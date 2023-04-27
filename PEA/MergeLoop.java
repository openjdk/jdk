class MergeLoop {
    static void blackhole(Object obj) {}
    static void test(int val) {
        Object obj1 = new Object();
        Object obj2 = new Object();

        for (int i = 0; i < val; ++i)  {
            // to prevent phi from being elided, we need to ensure obj is not loop invariant!
            // the following shuffle just to fool ciTypeFlow

            // swap
            Object t = obj1;
            obj1 = obj2;
            obj2 = t;
            // swap again
            t = obj1;
            obj1 = obj2;
            obj2 = t;

            blackhole(obj1); // escape here.
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 2_000_000; ++i) {
            test(i);
        }
    }
}
