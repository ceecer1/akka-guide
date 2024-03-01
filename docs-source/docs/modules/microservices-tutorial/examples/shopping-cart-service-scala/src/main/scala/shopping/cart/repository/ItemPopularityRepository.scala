package shopping.cart.repository

import akka.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.Future

// tag::trait[]
trait ItemPopularityRepository {
  def update(session: R2dbcSession, itemId: String, delta: Int): Future[Long]
  def getItem(session: R2dbcSession, itemId: String): Future[Option[Long]]
}
// end::trait[]

// tag::impl[]
class ItemPopularityRepositoryImpl() extends ItemPopularityRepository {

  override def update(
      session: R2dbcSession,
      itemId: String,
      delta: Int): Future[Long] = {
    val stmt = session.createStatement(
      """
        |INSERT INTO item_popularity (itemid, count) VALUES ($itemId, $delta)
        | ON CONFLICT(itemid) DO UPDATE SET count = item_popularity.count + $delta
        |""".stripMargin)
      .bind(0, itemId)
      .bind(1, delta)
    session.updateOne(stmt)
  }

  override def getItem(
      session: R2dbcSession,
      itemId: String): Future[Option[Long]] = {

    val stmt = session.createStatement(
      """
        |SELECT count FROM item_popularity WHERE itemid = $itemId
        |""".stripMargin)
    session.selectOne(stmt)(row => row.get("count", classOf[Long]))
  }

}
// end::impl[]
