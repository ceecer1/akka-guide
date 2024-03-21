package shopping.cart;

public class ItemPopularity {

    // primary key
    private final String itemId;

    private final long count;

    public ItemPopularity() {
        // null version means the entity is not on the DB
        this.itemId = "";
        this.count = 0;
    }

    public ItemPopularity(String itemId, long count) {
        this.itemId = itemId;
        this.count = count;
    }

    public String getItemId() {
        return itemId;
    }

    public long getCount() {
        return count;
    }

    public ItemPopularity changeCount(long delta) {
        return new ItemPopularity(itemId, count + delta);
    }
}
