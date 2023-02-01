// java -Xms16M -Xmx16M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:-UseTLAB -XX:CompileOnly='Example3_1.foo' -XX:+DoPartialEscapeAnalysis Example3_1
import java.util.ArrayList; 

class Example3_1 {
    public ArrayList<Integer> _cache;
    public void foo(boolean cond) {
        ArrayList<Integer> x = new ArrayList<Integer>();

        if (cond) {
            _cache = x;
        }
    }

    public void test1(boolean cond) {
        foo(cond);
    }

    public static void main(String[] args)  {
        Example3_1 kase = new Example3_1();
        // Epsilon Test:
        // By setting the maximal heap and use EpsilonGC, let's see how long and how many iterations the program can sustain.
        // if PEA manages to reduce allocation rate, we expect the program to stay longer.
        // Roman commented it with a resonable doubt: "or your code slow down the program..."
        // That's why I suggest to observe iterations. It turns out not trivial because inner OOME will implode hotspot. We don't have a chance to execute the final statement...
        long iterations = 0;
        try {
            while (true) {
                kase.test1(0 == (iterations & 0xf));
                if (kase._cache != null) {
                    kase._cache.add(1);
                }
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
