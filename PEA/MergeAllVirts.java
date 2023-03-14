class MergeAllVirts {
    int a;
    int b;
    int c = 100;

    public MergeAllVirts() {}

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
    public static void main(String[] args) {
        long iterations = 0;

        try {
            while (true) {
                boolean cond =  0 == (iterations & 0xf);
                int sum = MergeAllVirts.add(cond);
                check_result(cond, sum);

                MergeAllVirts.escaped(cond);
                check_result(cond, cached.sum());

                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
