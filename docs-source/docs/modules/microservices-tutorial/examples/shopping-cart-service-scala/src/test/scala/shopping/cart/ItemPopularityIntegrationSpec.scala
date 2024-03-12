package shopping.cart

import scala.concurrent.Future
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import akka.cluster.MemberStatus
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.Cluster
import akka.cluster.typed.Join
import akka.projection.r2dbc.scaladsl.R2dbcSession
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpecLike
import shopping.cart.repository.ItemPopularityRepositoryImpl

object ItemPopularityIntegrationSpec {
  val config: Config =
    ConfigFactory.load("item-popularity-integration-test.conf")
}

class ItemPopularityIntegrationSpec
  extends ScalaTestWithActorTestKit(ItemPopularityIntegrationSpec.config)
    with TablesLifeCycle
    with AnyWordSpecLike
    with OptionValues {

  override def typedSystem: ActorSystem[_] = system

  private lazy val itemPopularityRepository =
    new ItemPopularityRepositoryImpl()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
  }

  "Item popularity projection" should {
    "init and join Cluster" in {
      Cluster(system).manager ! Join(Cluster(system).selfMember.address)

      // let the node join and become Up
      eventually {
        Cluster(system).selfMember.status should ===(MemberStatus.Up)
      }
    }

    "consume cart events and update popularity count" in {
      val sharding = ClusterSharding(system)
      val cartId1 = "cart1"
      val cartId2 = "cart2"
      val item1 = "item1"
      val item2 = "item2"

      sharding.init(Entity(typeKey = ShoppingCart.EntityKey) { entityContext =>
        ShoppingCart(entityContext.entityId)
      })

      val cart1 = sharding.entityRefFor(ShoppingCart.EntityKey, cartId1)
      val cart2 = sharding.entityRefFor(ShoppingCart.EntityKey, cartId2)

      val reply1: Future[ShoppingCart.Summary] =
        cart1.askWithStatus(ShoppingCart.AddItem(item1, 3, _))
      reply1.futureValue.items.values.sum should ===(3)

      eventually {
        R2dbcSession.withSession(typedSystem) { session =>
          itemPopularityRepository.getItem(session, item1).map {
            value => assert(value == 3l)
          }
        }
      }

      val reply2: Future[ShoppingCart.Summary] =
        cart1.askWithStatus(ShoppingCart.AddItem(item2, 5, _))
      reply2.futureValue.items.values.sum should ===(3 + 5)
      // another cart
      val reply3: Future[ShoppingCart.Summary] =
        cart2.askWithStatus(ShoppingCart.AddItem(item2, 4, _))
      reply3.futureValue.items.values.sum should ===(4)

      eventually {
        R2dbcSession.withSession(typedSystem) { session =>
          itemPopularityRepository.getItem(session, item2).map { value =>
            value should ===(5l + 4l)
          }
          itemPopularityRepository.getItem(session, item1).map { value =>
            value should ===(3l)
          }
        }
      }
    }

  }
}
