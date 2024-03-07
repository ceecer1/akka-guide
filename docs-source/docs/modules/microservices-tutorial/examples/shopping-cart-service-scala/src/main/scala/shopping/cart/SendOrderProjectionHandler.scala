package shopping.cart

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.Done
import akka.actor.typed.ActorSystem
import akka.persistence.query.typed.EventEnvelope
import akka.projection.r2dbc.scaladsl.R2dbcHandler
import akka.projection.r2dbc.scaladsl.R2dbcSession
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import org.slf4j.LoggerFactory
import shopping.order.proto.Item
import shopping.order.proto.OrderRequest
import shopping.order.proto.ShoppingOrderService

class SendOrderProjectionHandler(
                                  system: ActorSystem[_],
                                  orderService: ShoppingOrderService) // <1>
  extends R2dbcHandler[EventEnvelope[ShoppingCart.Event]] {
  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext =
    system.executionContext

  private val sharding = ClusterSharding(system)
  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  override def process(
                        session: R2dbcSession,
                        envelope: EventEnvelope[ShoppingCart.Event]): Future[Done] = {
    envelope.event match {
      case checkout: ShoppingCart.CheckedOut =>
        sendOrder(checkout)

      case _ =>
        // this projection is only interested in CheckedOut events
        Future.successful(Done)
    }

  }

  private def sendOrder(checkout: ShoppingCart.CheckedOut): Future[Done] = {
    val entityRef =
      sharding.entityRefFor(ShoppingCart.EntityKey, checkout.cartId)
    entityRef.ask(ShoppingCart.Get).flatMap { cart => // <2>
      val items =
        cart.items.iterator.map { case (itemId, quantity) =>
          Item(itemId, quantity)
        }.toList
      log.info(
        "Sending order of {} items for cart {}.",
        items.size,
        checkout.cartId)
      val orderReq = OrderRequest(checkout.cartId, items)
      orderService.order(orderReq).map(_ => Done) // <3>
    }
  }

}
