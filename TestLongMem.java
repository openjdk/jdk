import java.util.Arrays;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.*;

public class TestLongMem {

    static long[] a;
    static long[] b;
    static long[] c;
    static long[] d;

    public static long testTemp(long x, int i) {
        return 0;
    }

    public static long testCmovP(long x, int i) {
        long[] p = x > i ? a : null;
        long[] q = x == i ? b : p;
        d = p == q ? a : b;
        return d[0];
    }

    public static long testTzcnt(long x, int i) {
        return  Long.numberOfTrailingZeros(a[i]); //CV
    }

    public static long testLzcnt(long x, int i) {
        return  Long.numberOfLeadingZeros(b[i]); //CV
    }

    public static long testPopcnt(long x, int i) {
        return  Long.bitCount(a[i]); //CV
    }

    public static long testShrVar(int a, int b) {
        return  a >>> (b >>> 28) ; // generates shrxl UseBMI2Instructions
    }

    public static long testShr(long x, int i) {
        return  a[i] >>> 6 ; //CV
    }

    public static long testSal(long x, int i) {
        return  a[i] << 5 ; //CV
    }

    public static long testSar(long x, int i) {
        return  a[i] >> 63 ; //CV
    }

    public static long testDec(long x, int i) {
        return  b[i] - 1 ; //CV
    }

    public static long testInc(long x, int i) {
        return  a[i] + 1 ; //CV
    }

    public static long testNeg(long x, int i) {
        return  -b[i]; //CV
    }

    public static long testXorImm1(long x, int i) {
        return b[i] ^ 9; //CV
    }

    public static long testXorImm2(long x, int i) {
        return 11 ^ a[i]; //CV
    }

    public static long testXor2(long x, int i) {
        return b[i] ^ x; //CV
    }

    public static long testXor1(long x, int i) {
        return x ^ b[i]; //CV
    }

    public static long testOrImm1(long x, int i) {
        return b[i] | 9; //CV
    }

    public static long testOrImm2(long x, int i) {
        return 11 | a[i]; //CV
    }

    public static long testOr2(long x, int i) {
        return b[i] | x; //CV
    }

    public static long testOr1(long x, int i) {
        return x | b[i]; //CV
    }

    public static long testAndImm1(long x, int i) {
        return b[i] & 9; //CV
    }

    public static long testAndImm2(long x, int i) {
        return 11 & a[i]; //CV
    }

    public static long testAnd2(long x, int i) {
        return b[i] & x; //CV
    }

    public static long testAnd1(long x, int i) {
        return x & b[i]; //CV
    }

    public static long testMulImm2(long x, int i) {
        return a[i] * 5971; //CV
    }

    public static long testMulImm1(long x, int i) {
        return 487 * b[i]; //CV
    }

    public static long testMul2(long x, int i) {
        return b[i] * x; //CV
    }

    public static long testMul1(long x, int i) {
        return x * b[i]; //CV
    }

    public static long testSubImm2(long x, int i) {
        return a[i] - 99; //TODO: maps to eaddq(Reg, Memm, Imm)
    }

    public static long testSubImm1(long x, int i) {
        return 11 - b[i]; //TODO: maps to esubq(Reg, Reg, Mem)
    }

    public static long testSub2(long x, int i) {
        return a[i] - x; //CV
    }

    public static long testSub1(long x, int i) {
        return x - b[i]; //CV
    }

    public static long testAddImm2(long x, int i) {
        return -75 + b[i]; //CV
    }

    public static long testAddImm1(long x, int i) {
        return a[i] + 75; //CV
    }

    public static long testAdd2(long x, int i) {
        return b[i] + x; //CV
    }

    public static long testAdd1(long x, int i) {
        return x + b[i]; //CV
    }

    public static void init(int size, long a0, long b0) {
        a = new long[size];
        b = new long[size];
        c = new long[size];
        Random rand = new Random(0);
        for (int i = 0; i < a.length; i++) {
            a[i] = i == 0 ? a0 : rand.nextLong();
            b[i] = i == 0 ? b0 : rand.nextLong();
        }
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            Method method = TestLongMem.class.getMethod("test" + args[1], long.class, int.class);
            int factor = 10_000;
            int size = factor * iters;
            init(size, Long.parseLong(args[2]), Long.parseLong(args[3]));

            // warmup
            for (int i = 0; i < factor; i++) {
                c[i] = (long) method.invoke(null, a[i], i);
            }
            System.out.println("Warmup done>");

            // main iter
            for (int i = 0; i < factor * iters; i++) {
                c[i] = (long) method.invoke(null, a[i], i);
            }

            System.out.println("\n ------------- MAIN DONE: " + "test" + args[1] + "(" + a[0] + "," + b[0] + ") = " + c[0]);
            assert c[0] == Long.parseLong(args[4]) : "APX NDD test failed; expected = " + args[4];

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}