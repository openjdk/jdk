public class UestBug2 {
    static int c = 0;
    static int[] arr = {};

    static void op2Test(int a, int b) {
        // Create implicit edges during dom calculation to exception handler
        if (a < 0) {
            b = 0;
        }
        // Create two branches into next loop header block
        try {
            int l = arr.length;
            for (int i = 0; i < l; i++) {
                int d = arr[i] + arr[i];
            }
        }
        // Exception handler as predecessor of the next loop header block
        catch (ArithmeticException e) {}

        // op2(x, y) as candidate for hoisting: operands are loop invariant
        while (a + b < b) {}
    }

    static void arrayLengthTest() {
        float [] newArr = new float[c];

        try {
            for (float f : newArr) {}
        }
        catch (ArrayIndexOutOfBoundsException e) {}

        while (54321 < newArr.length) {
            newArr[c] = 123.45f;
        }
    }

    static void negateTest(int a) {
        if (a <= 111) {
            a = -111;
        }

        int f = -3;
        try {
            int l = arr.length;
            f--;
        }
        catch (NegativeArraySizeException e) {}

        while (-a < f) {
            f--;
        }
    }

    static void convertTest(int a) {
        if (c == 1000) {
            a = -1;
        }

        long tgt = 4000;

        try {
            String s = String.valueOf(c);
        }
        catch (NumberFormatException e) {}

        while ((long)a != tgt) {
            tgt++;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            op2Test(12, 34);
            arrayLengthTest();
            negateTest(778);
            convertTest(4812);
        }
    }
}
