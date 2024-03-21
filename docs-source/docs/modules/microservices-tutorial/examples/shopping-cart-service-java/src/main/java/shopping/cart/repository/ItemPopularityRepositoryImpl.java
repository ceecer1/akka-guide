package shopping.cart.repository;

import akka.projection.r2dbc.javadsl.R2dbcSession;
import io.r2dbc.spi.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shopping.cart.ItemPopularity;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class ItemPopularityRepositoryImpl implements ItemPopularityRepository {

    @Override
    public CompletionStage<Long> saveOrUpdate(R2dbcSession session, ItemPopularity itemPopularity) {
        String upsertStatement = String.format("INSERT INTO item_popularity (itemid, count) VALUES ('%s', %d) " +
                        "ON CONFLICT(itemid) DO UPDATE SET count = item_popularity.count + %d", itemPopularity.getItemId(),
                itemPopularity.getCount(), itemPopularity.getCount());
        return session.updateOne(session.createStatement(upsertStatement));
    }

    @Override
    public CompletionStage<Optional<ItemPopularity>> findById(R2dbcSession session, String id) {
        String selectStmt = String.format("SELECT itemid, count FROM item_popularity WHERE itemid = '%s'", id);
        Statement statement = session.createStatement(selectStmt);

        return session.selectOne(statement, row ->
                new ItemPopularity(
                        row.get("itemid", String.class),
                        row.get("count", Long.class))

        );

    }

    @Override
    public CompletionStage<Optional<Long>> getCount(R2dbcSession session, String id) {
        String selectStmt = String.format("SELECT count FROM item_popularity WHERE itemid = '%s'", id);
        Statement statement = session.createStatement(selectStmt);

        return session.selectOne(statement, row ->
                row.get("count", Long.class));
    }
}
