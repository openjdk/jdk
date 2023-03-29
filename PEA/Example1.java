class Example1 {
    private Object _cache;
    public void test(boolean cond) {
        Object x = new Object();

        if (cond) {
            _cache = x;
        }
    }

    public static void main(String[] args)  {
        Example1 kase = new Example1();
        // Epsilon Test:
        // By setting the maximal heap and use EpsilonGC, let's see how long and how many iterations the program can sustain.
        // if PEA manages to reduce allocation rate, we expect the program to stay longer.
        // Roman commented it with a resonable doubt: "or your code slow down the program..."
        // That's why I suggest to observe iterations. It turns out not trivial because inner OOME will implode hotspot. We don't have a chance to execute the final statement...
        long iterations = 0;
        try {
            while (true) {
                kase.test(0 == (iterations & 0xf));
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
