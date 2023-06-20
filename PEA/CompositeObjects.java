class CompositeObjects {
    static class Node {
       public Node _next;
       public Node() {}
    }

    static Object _global;

    public static void test() {
        var a = new Node();
        var b = new Node();

        a._next = b; b._next = a;
        blackhole(a); // materialize a here.
    }
    public static void blackhole(Object o) {
        _global = o;
    }
    public static void main(String[] args ) {
        for (int i=0; i < 100_000; ++i) {
            test();
        }
    }
}
