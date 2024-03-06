// tag::projection[]
package shopping.cart

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.query.Offset
import akka.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.SourceProvider
import akka.projection.{ProjectionBehavior, ProjectionId}
import shopping.cart.repository.ItemPopularityRepository
import akka.persistence.query.typed.EventEnvelope
import akka.projection.Projection
import akka.projection.r2dbc.scaladsl.R2dbcProjection

object ItemPopularityProjection {
  // tag::howto-read-side-without-role[]
  def init(
            system: ActorSystem[_],
            repository: ItemPopularityRepository): Unit = {

    implicit val sys = system

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
      val projectionId = ProjectionId("ShoppingCarts", s"carts-$minSlice-$maxSlice")

      R2dbcProjection
        .exactlyOnce(
          projectionId,
          settings = None,
          sourceProvider(sliceRange),
          handler = () =>
            new ItemPopularityProjectionHandler(s"carts-$minSlice-$maxSlice",
              system,
              repository))
    }

    ShardedDaemonProcess(system).initWithContext(
      name = "ShoppingCartProjection",
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
}
// end::projection[]
