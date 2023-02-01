// -Xcomp -Xms16M -Xmx16M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:CompileOnly='Example2.foo' -XX:CompileCommand=dontinline,Example2.blackhole
class Example2 {
    private Object _cache;
    public Object foo(boolean cond) {
        Object x = new Object();
        
        blackhole();

        if (cond) {
            _cache = x;
        }
        return x;
    }

    public static void blackhole() {}
    
    public static void main(String[] args)  {
        Example2 kase = new Example2();
        // Epsilon Test:
        // By setting the maximal heap and use EpsilonGC, let's see how long and how many iterations the program can sustain.
        // if PEA manages to reduce allocation rate, we expect the program to stay longer.
        // Roman commented it with a resonable doubt: "or your code slow down the program..."
        // That's why I suggest to observe iterations. It turns out not trivial because inner OOME will implode hotspot. We don't have a chance to execute the final statement...
        long iterations = 0;
        try {
            while (true) {
                kase.foo(0 == (iterations & 0xf));
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
