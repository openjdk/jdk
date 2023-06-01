class LoadSelfIntoField {
    // Test that PEA handles loading self into a field correctly.
    // This test is a smaller reproducer of Test6843752.
    static class Item {
        public Item    next;
        public Item    prev;
    }
    Item list = null;

    private Item test(boolean cond) {
        Item item = new Item();
        Item head = list;
        if (cond) {
            item.next = item;
            item.prev = item;
            list = item;
            // Confirm we correctly materialize setting object's field to self.
            assert item == item.next;
            assert item == item.prev;
        } else {
            item.next = head;
            item.prev = head.prev;
            head.prev.next = item;
            head.prev = item;
        }
        return item;
    }

    public static void main(String[] args)  {
        LoadSelfIntoField obj = new LoadSelfIntoField();
        long iterations = 0;
        while (iterations <= 20000) {
            obj.test(0 == (iterations & 0xf));
            iterations++;
        }
    }
}
