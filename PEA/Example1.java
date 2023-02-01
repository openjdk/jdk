// -Xcomp -Xms16M -Xmx16M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:CompileOnly='Example1.ivanov' -XX:CompileCommand=dontinline,Example1.blackhole
class Example1 {
    private Object _cache;
    public void foo(boolean cond) {
        Object x = new Object();

        if (cond) {
            _cache = x;
        }
    }

    // Ivanov suggest to make this happen first.
    // we don't need to create JVMState for the cloning Allocate.
    public void ivanov(boolean cond) {
        Object x = new Object();

        if (cond) {
            blackhole(x);
        }
    }

    static void blackhole(Object x) {}

    public void test1(boolean cond) {
        foo(cond);
        //ivanov(cond);
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
                kase.test1(0 == (iterations & 0xf));
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
