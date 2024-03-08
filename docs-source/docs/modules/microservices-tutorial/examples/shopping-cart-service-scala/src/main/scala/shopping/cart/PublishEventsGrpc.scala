package shopping.cart

//#eventProducerService
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.persistence.query.typed
import akka.persistence.query.typed.EventEnvelope
import akka.persistence.typed.PersistenceId
import akka.projection.grpc.producer.EventProducerSettings
import akka.projection.grpc.producer.scaladsl.EventProducer
import akka.projection.grpc.producer.scaladsl.EventProducer.Transformation

import scala.concurrent.Future

object PublishEventsGrpc {

  def eventProducerService(system: ActorSystem[_])
  : PartialFunction[HttpRequest, Future[HttpResponse]] = {
    val transformation = Transformation.identity
      .registerAsyncEnvelopeMapper[ShoppingCart.ItemAdded, proto.ItemAdded](envelope =>
        Future.successful(Some(transformItemUpdated(envelope))))
      .registerAsyncEnvelopeMapper[ShoppingCart.ItemQuantityAdjusted, proto.ItemQuantityAdjusted](envelope =>
        Future.successful(Some(transformItemAdjusted(envelope))))
      .registerAsyncEnvelopeMapper[ShoppingCart.ItemRemoved, proto.ItemRemoved](envelope =>
        Future.successful(Some(transformItemRemoved(envelope))))
      .registerAsyncEnvelopeMapper[ShoppingCart.CheckedOut, proto.CheckedOut](envelope =>
        Future.successful(Some(transformCheckedOut(envelope))))

    //#withProducerFilter
    val eventProducerSource = EventProducer
      .EventProducerSource(
        "ShoppingCart",
        "cart",
        transformation,
        EventProducerSettings(system))
      //#eventProducerService
      .withProducerFilter[ShoppingCart.Event] { envelope =>
        val tags = envelope.tags
        tags.contains(ShoppingCart.MediumQuantityTag) ||
          tags.contains(ShoppingCart.LargeQuantityTag)
      }
    //#eventProducerService
    //#withProducerFilter

    EventProducer.grpcServiceHandler(eventProducerSource)(system)
  }
  //#eventProducerService

  //#transformItemUpdated
  private def transformItemUpdated(
                            envelope: EventEnvelope[ShoppingCart.ItemAdded]): proto.ItemAdded = {
    val event = envelope.event
    proto.ItemAdded(
      cartId = PersistenceId.extractEntityId(envelope.persistenceId),
      itemId = event.itemId,
      quantity = event.quantity)
  }
  //#transformItemUpdated

  //#transformItemAdjusted
  private def transformItemAdjusted(
                                    envelope: EventEnvelope[ShoppingCart.ItemQuantityAdjusted]): proto.ItemQuantityAdjusted = {
    val event = envelope.event
    proto.ItemQuantityAdjusted(
      cartId = PersistenceId.extractEntityId(envelope.persistenceId),
      itemId = event.itemId,
      quantity = event.newQuantity)
  }
  //#transformItemAdjusted

  //#transformItemRemoved
  private def transformItemRemoved(
                                    envelope: EventEnvelope[ShoppingCart.ItemRemoved]): proto.ItemRemoved = {
    val event = envelope.event
    proto.ItemRemoved(
      cartId = PersistenceId.extractEntityId(envelope.persistenceId),
      itemId = event.itemId)
  }
  //#transformItemRemoved

  private def transformCheckedOut(envelope: typed.EventEnvelope[ShoppingCart.CheckedOut]): proto.CheckedOut =
    proto.CheckedOut(PersistenceId.extractEntityId(envelope.persistenceId))

  //#eventProducerService
}
//#eventProducerService