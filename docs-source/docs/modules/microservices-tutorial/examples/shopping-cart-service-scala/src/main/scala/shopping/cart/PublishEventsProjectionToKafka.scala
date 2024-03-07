package shopping.cart

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.query.Offset
import akka.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.SourceProvider
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.persistence.query.typed.EventEnvelope
import akka.projection.Projection
import akka.projection.r2dbc.scaladsl.R2dbcProjection
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

object PublishEventsProjectionToKafka {

  def init(system: ActorSystem[_]): Unit = {
    val sendProducer = createProducer(system)
    val topic =
      system.settings.config.getString("shopping-cart-service.kafka.topic")

    def sourceProvider(sliceRange: Range): SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
      EventSourcedProvider
        .eventsBySlices[ShoppingCart.Event](
          system,
          readJournalPluginId = R2dbcReadJournal.Identifier,
          "ShoppingCart",
          sliceRange.min,
          sliceRange.max)

    def projection(sliceRange: Range): Projection[EventEnvelope[ShoppingCart.Event]] = {
      val minSlice = sliceRange.min
      val maxSlice = sliceRange.max
      val projectionId = ProjectionId("PublishEventsProjection", s"carts-$minSlice-$maxSlice")

      R2dbcProjection
        .atLeastOnce(
          projectionId,
          settings = None,
          sourceProvider(sliceRange),
          handler = () =>
            new PublishEventsProjectionToKafkaHandler(
              system,
              s"carts-$minSlice-$maxSlice",
              topic,
              sendProducer))(system)

    }

    ShardedDaemonProcess(system).initWithContext(
      name = "PublishEventsProjection",
      initialNumberOfInstances = 4,
      behaviorFactory = { daemonContext =>
        val sliceRanges =
          EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier, daemonContext.totalProcesses)
        val sliceRange = sliceRanges(daemonContext.processNumber)
        ProjectionBehavior(projection(sliceRange))
      },
      ShardedDaemonProcessSettings(system),
      stopMessage = ProjectionBehavior.Stop)

  }

  private def createProducer(
                              system: ActorSystem[_]): SendProducer[String, Array[Byte]] = {
    val producerSettings =
      ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
    val sendProducer =
      SendProducer(producerSettings)(system)
    CoordinatedShutdown(system).addTask(
      CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
      "close-sendProducer") { () =>
      sendProducer.close()
    }
    sendProducer
  }

}
