package shopping.cart

import java.util.concurrent.TimeoutException
import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.projection.r2dbc.scaladsl.R2dbcSession
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory
import shopping.cart.repository.ItemPopularityRepository

// tag::moreOperations[]
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply

// end::moreOperations[]

// tag::getItemPopularity[]
class ShoppingCartServiceImpl(
    system: ActorSystem[_],
    itemPopularityRepository: ItemPopularityRepository) // <1>
    extends proto.ShoppingCartService {

  // end::getItemPopularity[]
  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  // end::getItemPopularity[]
  override def addItem(in: proto.AddItemRequest): Future[proto.Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.AddItem(in.itemId, in.quantity, _))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def updateItem(in: proto.UpdateItemRequest): Future[proto.Cart] = {
    logger.info("updateItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)

    def command(replyTo: ActorRef[StatusReply[ShoppingCart.Summary]]) =
      if (in.quantity == 0)
        ShoppingCart.RemoveItem(in.itemId, replyTo)
      else
        ShoppingCart.AdjustItemQuantity(in.itemId, in.quantity, replyTo)

    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(command(_))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  // tag::checkoutAndGet[]
  override def checkout(in: proto.CheckoutRequest): Future[proto.Cart] = {
    logger.info("checkout {}", in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.Checkout(_))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def getCart(in: proto.GetCartRequest): Future[proto.Cart] = {
    logger.info("getCart {}", in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val response =
      entityRef.ask(ShoppingCart.Get).map { cart =>
        if (cart.items.isEmpty)
          throw new GrpcServiceException(
            Status.NOT_FOUND.withDescription(s"Cart ${in.cartId} not found"))
        else
          toProtoCart(cart)
      }
    convertError(response)
  }
  // end::checkoutAndGet[]

  // tag::toProtoCart[]
  private def toProtoCart(cart: ShoppingCart.Summary): proto.Cart = {
    proto.Cart(
      cart.items.iterator.map { case (itemId, quantity) =>
        proto.Item(itemId, quantity)
      }.toSeq,
      cart.checkedOut)
  }
  // end::toProtoCart[]

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }

  // tag::getItemPopularity[]
  override def getItemPopularity(in: proto.GetItemPopularityRequest)
      : Future[proto.GetItemPopularityResponse] = {
      R2dbcSession.withSession(system) { session =>
        itemPopularityRepository.getItem(session, in.itemId)
      }.map {
      case Some(count) =>
        proto.GetItemPopularityResponse(in.itemId, count)
      case None =>
        proto.GetItemPopularityResponse(in.itemId, 0L)
    }
  }
}
// end::getItemPopularity[]
