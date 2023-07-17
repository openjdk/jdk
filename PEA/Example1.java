class Example1 {
    private Object _cache;
    public void test(boolean cond) {
        Object x = new Object();

        if (cond) {
            _cache = x;
        }
    }

    public Object test2(boolean cond) {
        Object x = new Object();

        if (cond) {
            _cache = x;
        }

        return cond ? null: x;
    }

    public static void main(String[] args)  {
        Example1 kase = new Example1();

        for (int i = 0; i < 1_000_000; ++i) {
            kase.test(0 == (i& 0xf));
            kase.test2(0 == (i& 0xf));
        }
    }
}
