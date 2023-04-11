import java.io.*;

class MergeAllVirts {
    int a;
    int b;
    int c = 100;

    public MergeAllVirts() {}
    public MergeAllVirts(boolean cond) {
        if (cond) {
            this.a = 1;
            this.b = 20;
            this.c = 300;
            blackhole(); // kill locals, so LV0 = this is dead here.
        } else {
            this.a = 100;
            this.b = 50;
            this.c = 7;
            blackhole(); // kill locals.
        }
        // merge 2 predecessors, but LV0 is not live here.
        // we need to merge allocation states, or wrong current allocation state is wrong.
    }

    public int sum() {
        return a + b + c;
    }

    public static int add(boolean cond) {
        MergeAllVirts obj = new MergeAllVirts();
        if (cond) {
            obj.a = 1;
        } else {
            obj.b = 2;
        }
        //obj3 = phi(obj1, obj2);
        return obj.sum();
    }

    public static MergeAllVirts cached;
    public static void escaped(boolean cond) {
        MergeAllVirts obj = new MergeAllVirts();

        if (cond) {
            obj.a = 1;
        } else {
            obj.b = 2;
        }
        // return obj;  we don't materialize at exit. if it's virtual, keep it virtual.
        cached = obj; // materialize here.
    }

    static void check_result(boolean cond, int sum) {
        if ((cond && sum != 101) || (!cond && sum != 102)) {
            throw new RuntimeException("wrong answer: " + sum);
        }
    }
    static void blackhole() {} // not inline this.
    static void blackhole(MergeAllVirts obj) {} // not inline this.
    static void blackhole(File file) throws IOException {}
    public static MergeAllVirts escaped2(boolean cond1, boolean cond2) {
        MergeAllVirts obj = new MergeAllVirts();

        if (cond1) {
            obj.a = 20;
        } else {
            obj.b = 30;
        }
        if (cond2) {
            blackhole(obj); // materialize obj here.
        }
        return obj;
    }

    static void check_result2(boolean cond1, int sum) {
        boolean okay = true;

        if (cond1) {
            okay = sum == 120;
        } else {
            okay = sum == 130;
        }

        if (!okay) {
            throw new RuntimeException("wrong answer: " + cond1 + " " + sum);
        }
    }

    public static MergeAllVirts escaped3(boolean cond1, boolean cond2) {
        MergeAllVirts obj = new MergeAllVirts();

        if (cond1) {
            obj.a = 20;
        } else {
            obj.b = 30;
        }
        if (cond2) {
            blackhole(obj); // materialize obj here.
            obj.a = 300;
        }
        return obj;
    }

    static void check_result3(boolean cond1, boolean cond2, int sum) {
        boolean okay = true;

        if (cond1) {
            if (cond2)
                okay = sum == 400; // 300 + 0 + 100
            else
                okay = sum == 120;
        } else {
            if (cond2)
                okay = sum == 430; // 300 + 30 + 100
            else
                okay = sum == 130;
        }

        if (!okay) {
            throw new RuntimeException("wrong answer: " + cond1 + " "  + cond2 + " " + sum);
        }
    }
    public static void escaped4(boolean cond) {
        MergeAllVirts obj = new MergeAllVirts(cond);

        // return obj;  we don't materialize at exit. if it's virtual, keep it virtual.
        cached = obj; // materialize here.
    }

    static void check_result4(boolean cond, int sum) {
        boolean okay;

        if (cond) {
            okay = sum == 321;
        } else {
            okay = sum == 157;
        }

        if (!okay) {
            throw new RuntimeException("wrong answer: " + cond + " "  + sum);
        }

    }
    private static void fail(String msg, Object... args) {
        throw new RuntimeException(msg);
    }

    // JVM-1880: merge the same object but they have different aliases in its predecessors.
    // one common reason is exception handler. C2 parse forces to generate a phi for them.
    // inspired by sun.net.sdp.SdpProvider::<init>
    public static Object merge_2virts(File file) {
        MergeAllVirts obj = new MergeAllVirts();

        try {
            blackhole(file);
        } catch(IOException e) {
            // create a phi node : n = phi(region, _, obj)
            fail("Error reading %s: %s", file, e.getMessage());
        }
        // merge here: phi(r, m, n) and n is phi(region, _, obj)
        // same object but different aliases!
        return obj;
    }

    public static void main(String[] args) {
        long iterations = 0;
        File file = new File("hello");
        try {
            while (true) {
                boolean cond = 0 == (iterations & 0xf);
                int sum = MergeAllVirts.add(cond);
                check_result(cond, sum);

                MergeAllVirts.escaped(cond);
                check_result(cond, cached.sum());

                boolean cond2 = 0 == ((iterations + 3) & 0xf);
                sum = MergeAllVirts.escaped2(cond, cond2).sum();
                check_result2(cond, sum);

                sum = MergeAllVirts.escaped3(cond, cond2).sum();
                check_result3(cond, cond2, sum);

                MergeAllVirts.escaped4(cond);
                check_result4(cond, cached.sum());

                MergeAllVirts.merge_2virts(file);
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
