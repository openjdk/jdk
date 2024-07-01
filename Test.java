
// To verify that the implementation works, hardcode 'stress_trap' to true and run:
// java -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,Test::test* -Xbatch -XX:-TieredCompilation -XX:CompileCommand=print,Test::test* -XX:+TraceDeoptimization -Xbatch Test.java > log.tmp
// Check that the lock contains 8 times "DEOPT PACKING", i.e., all 8 methods hit the uncommon trap

public class Test {

    public static int other() {
        throw new RuntimeException("FAIL");
    }

    public static int test1(boolean b) {
        if (b) {
            return 0;
        } else {
            return other();
        }
    }

    public static int test2(boolean b) {
        if (!b) {
            return 0;
        } else {
            return other();
        }
    }

    public static int test3(boolean b) {
        if (b) {
            return other();
        } else {
            return 0;
        }
    }

    public static int test4(boolean b) {
        if (!b) {
            return other();
        } else {
            return 0;
        }
    }

    public static int test5(Object obj) {
        if (obj == null) {
            return 0;
        } else {
            return other();
        }
    }

    public static int test6(Object obj) {
        if (obj != null) {
            return 0;
        } else {
            return other();
        }
    }

    public static int test7(Object obj) {
        if (obj == null) {
            return other();
        } else {
            return 0;
        }
    }

    public static int test8(Object obj) {
        if (obj != null) {
            return other();
        } else {
            return 0;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100_000; ++i) {
            test1(true);
            test2(false);
            test3(false);
            test4(true);
            test5(null);
            test6(42);
            test7(42);
            test8(null);
        }
    }    
}

