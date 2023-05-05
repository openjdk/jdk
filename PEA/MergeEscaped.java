class MergeEscaped {
    int a;
    int b;
    int c = 100;

    public MergeEscaped() {}
    public MergeEscaped(boolean cond) {
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

    public static MergeEscaped cached;

    void blackhole() {} // not inline this.
    static void blackhole(Object obj) {} // not inline this.

    public static MergeEscaped escaped(boolean cond1) {
        MergeEscaped obj = new MergeEscaped();

        if (cond1) {
            blackhole(obj);
            obj.a = 20;
        } else {
            obj.b = 30;
            blackhole(obj);
        }
        obj.c = 100;
        return obj;
    }

    static void check_result(boolean cond1, int sum) {
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
    public static MergeEscaped escaped2(boolean cond1) {
        MergeEscaped obj = new MergeEscaped();

        for (int i = 0; i < 100; ++i) {
            if (cond1) {
                blackhole(obj);
                obj.a = 20;
            } else {
                obj.b = 30;
            }
        }
        return obj;
    }

    public static MergeEscaped escaped3(boolean cond1, boolean cond2) {
        MergeEscaped obj = new MergeEscaped();
        if (cond1) {
            obj.a = 10; // V
        } else {
            if (cond2) {
                blackhole(obj); // E1
                obj.a = 20;
            } else {
                blackhole(obj); // E2
                obj.b = 30;
            }
            obj.c = 300;        // PHI(E1, E2),
        }                       // PHI'(V, PHI)
        return obj;
    }

    static void check_result3(boolean cond1, boolean cond2, int sum) {
        boolean okay = true;

        if (cond1) {
            okay = sum == 110;
        } else {
            if (cond2) {
                okay = sum == 320;
            } else {
                okay = sum == 330;
            }
        }

        if (!okay) {
            throw new RuntimeException("wrong answer: " + cond1 + " " +  cond2 + " " + sum);
        }
    }

    private static void test4(MergeEscaped obj, int cond) {
        if (cond == 0) {
            obj.a = 1;
        } else if (cond == 1) {
            obj.a = 2;
        } else {
            blackhole(obj);
        }
    }

    public static MergeEscaped escaped4(boolean cond1, boolean cond2) {
        MergeEscaped obj = new MergeEscaped();

        int cond = 0;
        if (cond1) {
            cond = 2;  // cond1 << 1
        }
        if (cond2) {
            cond = cond + 1;
        }

        test4(obj,  cond);
        blackhole(obj);
        return obj;
    }

    public static void main(String[] args) {
        long iterations = 0;

        try {
            while (true) {
                boolean cond = 0 == (iterations & 0xf);
                boolean cond2 = 7 == (iterations& 0xf);

                int sum = MergeEscaped.escaped(cond).sum();
                check_result(cond, sum);

                sum = MergeEscaped.escaped2(cond).sum();
                check_result(cond, sum);

                sum = MergeEscaped.escaped3(cond, cond2).sum();
                check_result3(cond, cond2, sum);

                escaped4(cond, cond2);
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
