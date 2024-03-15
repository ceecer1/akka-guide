package shopping.cart;

public class ItemPopularity {

    // primary key
    private final String itemId;

    // optimistic locking
    private final Long version;

    private final long count;

    public ItemPopularity() {
        // null version means the entity is not on the DB
        this.version = null;
        this.itemId = "";
        this.count = 0;
    }

    public ItemPopularity(String itemId, long version, long count) {
        this.itemId = itemId;
        this.version = version;
        this.count = count;
    }

    public String getItemId() {
        return itemId;
    }

    public long getCount() {
        return count;
    }

    public long getVersion() {
        return version;
    }

    public ItemPopularity changeCount(long delta) {
        return new ItemPopularity(itemId, version, count + delta);
    }
}
