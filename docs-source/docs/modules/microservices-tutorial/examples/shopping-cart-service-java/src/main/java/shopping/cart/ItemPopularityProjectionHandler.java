// tag::handler[]
package shopping.cart;

import akka.Done;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.r2dbc.javadsl.R2dbcHandler;
import akka.projection.r2dbc.javadsl.R2dbcSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shopping.cart.repository.ItemPopularityRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ItemPopularityProjectionHandler
        extends R2dbcHandler<EventEnvelope<ShoppingCart.Event>> { // <1>
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String slice;
    private final ItemPopularityRepository repo;

    public ItemPopularityProjectionHandler(String slice, ItemPopularityRepository repo) {
        this.slice = slice;
        this.repo = repo;
    }

    private CompletionStage<ItemPopularity> findOrNew(R2dbcSession session, String itemId) {
        return repo.findById(session, itemId).thenApply(ip -> ip.orElseGet(() -> new ItemPopularity(itemId, 0)));
    }

    @Override
    public CompletionStage<Done> process(
            R2dbcSession session, EventEnvelope<ShoppingCart.Event> envelope) { // <2>
        ShoppingCart.Event event = envelope.event();

        if (event instanceof ShoppingCart.ItemAdded) { // <3>
            ShoppingCart.ItemAdded added = (ShoppingCart.ItemAdded) event;
            String itemId = added.itemId;

            CompletionStage<ItemPopularity> existingItemPop = findOrNew(session, itemId);
            CompletionStage<ItemPopularity> updatedItemPop = existingItemPop.thenApplyAsync(itemPop -> itemPop.changeCount(added.quantity));
            CompletionStage<Long> updated = updatedItemPop.thenComposeAsync(itm -> repo.saveOrUpdate(session, itm));
            return updated.thenApplyAsync(rows -> {
                logCount(itemId, rows);
                return Done.getInstance();
            });
            // end::handler[]
        } else if (event instanceof ShoppingCart.ItemQuantityAdjusted) {
            ShoppingCart.ItemQuantityAdjusted adjusted = (ShoppingCart.ItemQuantityAdjusted) event;
            String itemId = adjusted.itemId;

            CompletionStage<ItemPopularity> existingItemPop = findOrNew(session, itemId);
            CompletionStage<ItemPopularity> updatedItemPop = existingItemPop.thenApplyAsync(itemPop ->
                    itemPop.changeCount(adjusted.newQuantity - adjusted.oldQuantity));
            CompletionStage<Long> updated = updatedItemPop.thenComposeAsync(itm -> repo.saveOrUpdate(session, itm));

            return updated.thenApplyAsync(rows -> {
                logCount(itemId, rows);
                return Done.getInstance();
            });

        } else if (event instanceof ShoppingCart.ItemRemoved) {
            ShoppingCart.ItemRemoved removed = (ShoppingCart.ItemRemoved) event;
            String itemId = removed.itemId;

            CompletionStage<ItemPopularity> existingItemPop = findOrNew(session, itemId);
            CompletionStage<ItemPopularity> updatedItemPop = existingItemPop.thenApplyAsync(itemPop -> itemPop.changeCount(-removed.oldQuantity));
            CompletionStage<Long> updated = updatedItemPop.thenComposeAsync(itm -> repo.saveOrUpdate(session, itm));
            return updated.thenApplyAsync(rows -> {
                logCount(itemId, rows);
                return Done.getInstance();
            });
            // tag::handler[]
        } else {
            // skip all other events, such as `CheckedOut`
          return CompletableFuture.completedFuture(Done.getInstance());
        }
    }

    private CompletionStage<Done> getCount(R2dbcSession session, String itemId) {
        return repo.getCount(session, itemId).thenApply(optLong -> {
            optLong.ifPresent(aLong -> logCount(itemId, aLong));
            return Done.getInstance();
        });

    }

    private void logCount(String itemId, Long count) {
        logger.info(
                "ItemPopularityProjectionHandler({}) item popularity for '{}': [{}]",
                this.slice,
                itemId,
                count);
    }
}
// end::handler[]
