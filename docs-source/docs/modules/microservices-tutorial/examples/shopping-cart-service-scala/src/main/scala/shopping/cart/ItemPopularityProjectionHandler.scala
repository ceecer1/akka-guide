// tag::handler[]
package shopping.cart

import akka.Done
import akka.actor.typed.ActorSystem
import akka.persistence.query.typed.EventEnvelope
import akka.projection.r2dbc.scaladsl.R2dbcHandler
import akka.projection.r2dbc.scaladsl.R2dbcSession
import org.slf4j.LoggerFactory
import shopping.cart.repository.ItemPopularityRepository

import scala.concurrent.Future

class ItemPopularityProjectionHandler(
    slice: String,
    system: ActorSystem[_],
    repo: ItemPopularityRepository)
    extends R2dbcHandler[EventEnvelope[ShoppingCart.Event]]() { // <1>

  private val logger = LoggerFactory.getLogger(getClass)
  import system.executionContext

  override def process(session: R2dbcSession, envelope: EventEnvelope[ShoppingCart.Event]): Future[Done] = { // <2>
    envelope.event match { // <3>
      case ShoppingCart.ItemAdded(_, itemId, quantity) =>
        repo.update(session, itemId, quantity).flatMap(_ => logItemCount(session, itemId))

      case ShoppingCart.ItemQuantityAdjusted(
            _,
            itemId,
            newQuantity,
            oldQuantity) =>
        repo.update(session, itemId, newQuantity - oldQuantity).flatMap(_ => logItemCount(session, itemId))

      case ShoppingCart.ItemRemoved(_, itemId, oldQuantity) =>
        repo.update(session, itemId, 0 - oldQuantity).flatMap(_ => logItemCount(session, itemId))

      case _: ShoppingCart.CheckedOut => Future.successful(Done)
    }
  }

  private def logItemCount(
      session: R2dbcSession,
      itemId: String): Future[Done] = {

    def log(count: Long): Unit =
      logger.info(
        "ItemPopularityProjectionHandler({}) item popularity for '{}': [{}]",
        slice,
        itemId,
        count)

    repo.getItem(session, itemId).map {
      case Some(l) =>
        log(l)
        Done
      case None =>
        throw new Exception("Something wrong during querying")
    }

  }

}
// end::handler[]
