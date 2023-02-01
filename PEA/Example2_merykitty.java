// -Xcomp -Xms16M -Xmx16M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:CompileOnly='Example2_merykitty.foo' -XX:CompileCommand=dontinline,Example2_merykitty.blackhole
class Example2_merykitty {
    private static Object _cache1;
    private static Object _cache2;

    public void foo(boolean cond1, boolean cond2) {
        Object x = new Object();
    
        blackhole();
    
        if (cond1) {
            _cache1 = x;
        }
    
        blackhole();
    
        if (cond2) {
            _cache2 = x;
        }
    }

    public static void blackhole() {}
    
    public static void main(String[] args)  {
        Example2_merykitty kase = new Example2_merykitty();
        long iterations = 0;
        try {
            while (true) {
                boolean cond = 0 == (iterations & 0xf);
                kase.foo(cond, cond);
                assert Example2_merykitty._cache1 == Example2_merykitty._cache2 :"check";
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
