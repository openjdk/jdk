import java.util.*;

class Example3_2 {
    //public static Integer zero = Integer.valueOf(0);

    void bar(boolean cond, ArrayList<Integer> L) {
        if(cond) {
            L.add(0);
        }
    }

    public void foo(boolean cond) {
        var x = new ArrayList<Integer>();
        bar(cond, x);
        return;
    }

    public static void main(String[] args)  {
        var kase = new Example3_2();
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
