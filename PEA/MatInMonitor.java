class MatInMonitor {
    private Object _cache;
    private boolean _odd;

    public void test() {
        Object x = new Object();
        synchronized (x) {
            _cache = x;
        }
    }

    public void test2() {
        Object x = new Object();
        Object y = new Object();
        synchronized (x) {
        synchronized (y) {
            _cache = x; // steal the monitor of x, which is not the top of stack.
        }}
    }

    public void test3() {
        Object x = new Object();
        synchronized (x) {
            if (_odd) {
                _cache = x;
            }
        }
    }
    // c2 doesn't support this nesting form. GeneratePairingInfo regards it as 'unbalanced'.
    public void testNested() {
        Object x = new Object();
        synchronized (x) {
        synchronized (x) {
        synchronized (x) {
            _cache = x;
        }}}
    }

    synchronized void bar() {
        if (_odd) {
            _cache = this; // escaping
        }
    }

    synchronized void foo() { bar(); }

    // Perfectly fine in C2. it is also a nesting lock after inlining.
    // it passes GeneratePairingInfo becasue it is intra-procedural.
    public void testNested2() {
        synchronized(this) {
            foo();
        }
    }

    public static void main(String[] args)  {
        MatInMonitor kase = new MatInMonitor();

        for (int i = 0; i < 10_000; ++i) {
            kase._odd = 0 == (i& 0xf);
            kase.test();
            kase.test2();
            kase.test3();

            kase.testNested();
            kase.testNested2();
        }
    }
}
