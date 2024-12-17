import java.util.Arrays;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.*;

public class TestLongMem {

    static long[] a;
    static long[] b;
    static long[] c;

    public static long testKernel(int a, int b) {
        long ans;
        long k = Long.rotateLeft(-a, 5);
        long l = Long.rotateRight(-b, 6);
        long m = Long.rotateLeft(a, Math.abs(Long.bitCount(l) % 7));
        ans = (a ^ b) + Math.max((a >> Math.min(2, k)), ((b >>> Math.min(3, l)) << 2));
        return ans;
    }

    public static long testTzcnt(long x, int i) {
        return  Long.numberOfTrailingZeros(a[i]);
    }

    public static long testLzcnt(long x, int i) {
        return  Long.numberOfLeadingZeros(b[i]);
    }

    public static long testPopcnt(long x, int i) {
        return  Long.bitCount(a[i]);
    }

    public static long testRor(int a, int b) {
        return  Long.rotateRight(a, 8);
    }

    public static long testRol(int a, int b) {
        return  Long.rotateLeft(b, 7); // getting mapped to Ror 25
    }


    public static long testShrVar(int a, int b) {
        return  a >>> (b >>> 28) ; // generates shrxl UseBMI2Instructions
    }

    public static long testShr(int a, int b) {
        return  a >>> 6 ;
    }

    public static long testSal(int a, int b) {
        return  a << 5 ;
    }

    public static long testSar(int a, int b) {
        return  b >> 4 ;
    }

    public static long testDec(int a, int b) {
        return  b - 1 ;
    }

    public static long testInc(int a, int b) {
        return  a + 1 ;
    }

    public static long testNeg(int a, int b) {
        return  -b;
    }

    public static long testXor(int a, int b) {
        return a ^ b;
    }

    public static long testOrImm1(long x, int i) {
        return b[i] | 9;
    }

    public static long testOrImm2(long x, int i) {
        return 11 | a[i];
    }

    public static long testOr2(long x, int i) {
        return b[i] | x;
    }

    public static long testOr1(long x, int i) {
        return x | b[i];
    }

    public static long testAndImm1(long x, int i) {
        return b[i] & 9;
    }

    public static long testAndImm2(long x, int i) {
        return 11 & a[i];
    }

    public static long testAnd2(long x, int i) {
        return b[i] & x;
    }

    public static long testAnd1(long x, int i) {
        return x & b[i];
    }

    public static long testCount(int a, int b) {
        return Long.bitCount(a) + Long.numberOfLeadingZeros(b) + Long.numberOfTrailingZeros(a-b);
    }

    public static long testMul2(long x, int i) {
        return b[i] * x;
    }

    public static long testMul1(long x, int i) {
        return x * b[i];
    }

    public static long testSub2(long x, int i) {
        return b[i] - x;
    }

    public static long testSub1(long x, int i) {
        return x - b[i];
    }

    public static long testAdd2(long x, int i) {
        return b[i] + x; // map to RRM
    }

    public static long testAdd1(long x, int i) {
        return x + b[i];
    }

    public static void init(int size) {
        a = new long[size];
        b = new long[size];
        c = new long[size];
        Random rand = new Random(0);
        for (int i = 0; i < a.length; i++) {
            a[i] = rand.nextLong();
            b[i] = rand.nextLong();
        }
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            int factor = 10_000;
            int size = factor * iters;
            init(size);

            Method method = TestLongMem.class.getMethod("test" + args[1], long.class, int.class);

            // warmup
            for (int i = 0; i < factor; i++) {
                c[i] = (long) method.invoke(null, a[i], i);
            }
            System.out.println("Warmup done>");

            // main iter
            for (int i = 0; i < factor * iters; i++) {
                c[i] = (int) method.invoke(null, a[i], i);
            }
            System.out.println("------------- MAIN DONE ----------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}