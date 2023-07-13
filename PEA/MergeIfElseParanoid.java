class MergeIfElseParanoid {
    static class Node {
       public Node _next;
       public Node() {}
    }

    static Object _global;
    static Object _global2;

    public static void test(boolean cond) {
        Object foo = new Object();
        if (cond) {
            _global = foo;
        }
        // When `cond == true`, we materialize foo a second time. We cannot
        // eliminate the original allocation because we need for when
        // `cond == false`.
        //
        // If we have passive materialization, we would materialize foo in the
        // predecessor, and the original object would be dead.
        _global2 = foo;
    }

    public static void main(String[] args ) {
        for (int i=0; i < 100_000; ++i) {
            test(0 == (i& 0xf));
        }
    }
}
