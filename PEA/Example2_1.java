// -Xcomp -Xms16M -Xmx16M -XX:+AlwaysPreTouch -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:-UseOnStackReplacement -XX:CompileOnly='Example2.foo' -XX:CompileCommand=dontinline,Example2.blackhole
class Example2_1 {
    int value  = 0;

    public static Example2_1 foo(boolean cond) {
        var x = new Example2_1();
        
        if (cond) {
            x.value++;
        } else {
            x.value += 2;
        }
        return x;
    }

    public static int foo2(boolean cond) {
        var x = new Example2_1();
        
        if (cond) {
            x.value++;
        } else {
            x.value += 2;
        }
        return x.value;
    }


    public static void blackhole() {}
    
    public static void main(String[] args)  {
        long iterations = 0;
        try {
            while (true) {
                boolean cond = 0 == (iterations & 0xf);
                Example2_1 obj = foo(cond);
                int expected = cond ? 1 : 2; 
                if (obj.value != expected) {
                    throw new RuntimeException("wrong answer");
                }
                int val = foo2(cond);
                if (val != expected) {
                    throw new RuntimeException("wrong answer");
                }
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
