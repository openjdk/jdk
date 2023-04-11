class EscapeInInitializer {
    private final int foo;
    private final int bar;
    private static EscapeInInitializer cache;

    EscapeInInitializer() {
        this.foo = 1;   // put a final field, set_alloc_with_final(obj)
        cache = this;   // materialize this here.
        this.bar = 2;   // put a final field again, set_alloc_with_final(obj)
    }

    EscapeInInitializer(int i) {
        this.foo = 1;   // put a final field, set_alloc_with_final(obj)
        this.bar = the_answer(); // invocation, either inline or not line may trigger materialization.
    }

    EscapeInInitializer(boolean cond) {
        this.foo = 1;   // put a final field, set_alloc_with_final(obj)
        this.bar = 2;
        if  (cond) {
            cache = this;   // materialize this here.
        }
        // we need to merge 'this' here, and then emit the trailing barrier for the phi.
    }

    int the_answer() { // of life, the universe and everything...
        cache = this;  // materialize 'this' here
        return 42;
    }

    static void test(int i) {
        var kase = new EscapeInInitializer();
        var kase2 = new EscapeInInitializer(i);
        var kase3 = new EscapeInInitializer(0 == (i % 100));
    }

    public static void main(String[] args) {
        for (int i = 0; i < 30_000; ++i) {
            test(i);
        }
    }
}
