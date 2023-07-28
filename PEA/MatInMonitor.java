class MatInMonitor {
    private Object _cache;
    public void test() {
        Object x = new Object();
        synchronized (x) {
            _cache = x;
        }
    }

    public static void main(String[] args)  {
        MatInMonitor kase = new MatInMonitor();

        for (int i = 0; i < 2; ++i) {
            kase.test();
        }
    }
}
