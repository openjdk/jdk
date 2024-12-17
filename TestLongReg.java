import java.util.Arrays;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.*;

public class TestLongReg {

    public static long testKernel(long a, long b) {
        long ans;
        long k = Long.rotateLeft(-a, 5);
        long l = Long.rotateRight(-b, 6);
        long m = Long.rotateLeft(a, Math.abs(Long.bitCount(l) % 7));
        ans = (a ^ b) + Math.max((a >> Math.min(2, k)), ((b >>> Math.min(3, l)) << 2));
        return ans;
    }

    public static long testTzcnt(long a, long b) {
        return  Long.numberOfTrailingZeros(a);
    }

    public static long testLzcnt(long a, long b) {
        return  Long.numberOfLeadingZeros(b);
    }

    public static long testPopcnt(long a, long b) {
        return  Long.bitCount(a);
    }

    public static long testRor(long a, long b) {
        return  Long.rotateRight(a, 8); // TODO: mapped to Rorxq
    }

    public static long testRol(long a, long b) {
        return  Long.rotateLeft(b, 7); // TODO: getting mapped to Rorxq 25
    }


    public static long testShrVar(long a, long b) {
        return  a >>> (b >>> 28) ; // generates shrxl UseBMI2Instructions
    }

    public static long testShr(long a, long b) {
        return  a >>> 6 ;
    }

    public static long testSal(long a, long b) {
        return  a << 5 ;
    }

    public static long testSar(long a, long b) {
        return  b >> 4 ;
    }

    public static long testDec(long a, long b) {
        return  b - 1 ;
    }

    public static long testInc(long a, long b) {
        return  a + 1 ; // no ndd
    }

    public static long testNeg(long a, long b) {
        return  -b;
    }

    public static long testXor(long a, long b) {
        return a ^ b;
    }

    public static long testOr(long a, long b) {
        return a | b;
    }

    public static long testAndImm1(long a, long b) {
        return a & 9;
    }

    public static long testAndImm2(long x, long b) {
        return 11 & b;
    }

    public static long testAnd(long a, long b) {
        return a & b;
    }

    public static long testCount(long a, long b) {
        return Long.bitCount(a) + Long.numberOfLeadingZeros(b) + Long.numberOfTrailingZeros(a-b);
    }

    public static long testMul(long a, long b) {
        return a * b;
    }

    public static long testSub(long a, long b) {
        return a - b;
    }

    public static long testAdd(long a, long b) {
        return a + b;
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            int factor = 10_000;
            int size = factor * iters;
            long[] a = new long[size];
            long[] b = new long[size];
            long[] c = new long[size];
            Random rand = new Random(0);
            for (int i = 0; i < a.length; i++) {
                a[i] = rand.nextLong();
                b[i] = rand.nextLong();
            }

            Method method = TestLongReg.class.getMethod("test" + args[1], long.class, long.class);

            // warmup
            for (int i = 0; i < factor; i++) {
                //c[i] = testKernel(a[i], b[i]);
                c[i] = (long) method.invoke(null, a[i], b[i]);
            }
            System.out.println("Warmup done>");

            // main iter
            for (int i = 0; i < factor * iters; i++) {
                c[i] = (long) method.invoke(null, a[i], b[i]);
            }
            System.out.println("------------- MAIN DONE ----------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}