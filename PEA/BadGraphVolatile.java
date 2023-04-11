class BadGraphVolatile {
    // use volatile to force c2 to load it again
    volatile int value;

    BadGraphVolatile(int value) {
        this.value = value;
    }

    void blackhole(int field) {
        assert field  == 42 :" wrong answer";
    }

    public static void foo(int value) {
        var x = new BadGraphVolatile(value);
        x.blackhole(x.value);
        return;
    }

    public static void main(String[] args)  {
        for (int i = 0; i < 30_000; ++i) {
            foo(42);
        }
    }
}
