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

    public static long testCmovMin(long a, long b) {
        return Math.min(a, b);
    }

    public static long testCmovMax(long a, long b) {
        return Math.max(a, b); //CV
    }

    public static long testCmov(long a, long b) {
        return a < 5 ? b : a; //CV
    }

    public static long testCmovU(long a, long b) {
        return Long.compareUnsigned(a, b) < 0 ? b : a; //CV
    }

    public static long testTzcnt(long a, long b) {
        return  Long.numberOfTrailingZeros(a); //CV
    }

    public static long testLzcnt(long a, long b) {
        return  Long.numberOfLeadingZeros(a); //CV
    }

    public static long testPopcnt(long a, long b) {
        return  Long.bitCount(a); //CV
    }

    public static long testRor(long a, long b) {
        return  Long.rotateRight(a, 8); // TODO: maps to rorxq
    }

    public static long testRol(long a, long b) {
        return  Long.rotateLeft(a, 7); // TODO: maps to rorxq 25
    }

    public static long testShrVar(long a, long b) {
        return  a >>> (b >>> 28) ; // generates shrxl UseBMI2Instructions
    }

    public static long testShr(long a, long b) {
        return  a >>> 6 ; //CV
    }

    public static long testSal(long a, long b) {
        return  a << 5 ; //CV
    }

    public static long testSar(long a, long b) {
        return  b >> 4 ; //CV
    }

    public static long testDec(long a, long b) {
        return  b - 1 ; //CV
    }

    public static long testInc(long a, long b) {
        return  a + 1 ; // TODO: maps to eaddq(Reg, Reg, Imm#1)
    }

    public static long testNeg(long a, long b) {
        return  -b; //CV
    }

    public static long testXorImm1(long a, long b) {
        return a ^ 9; //CV
    }

    public static long testXorImm2(long a, long b) {
        return 11 ^ b; //CV
    }

    public static long testXorM1(long a, long b) {
        return -1 ^ b ; //CV
    }

    public static long testXor(long a, long b) {
        return a ^ b; //CV
    }

    public static long testOrImm1(long a, long b) {
        return a | 9; //CV
    }

    public static long testOrImm2(long a, long b) {
        return 11 | b; //CV
    }

    public static long testOr(long a, long b) {
        return a | b; //CV
    }

    public static long testAndImm1(long a, long b) {
        return a & 9; //CV
    }

    public static long testAndImm2(long a, long b) {
        return 11 & b; //CV
    }

    public static long testAnd(long a, long b) {
        return a & b; //CV
    }

    public static long testMulImm2(long a, long b) {
        return a * 775; //CV
    }

    public static long testMulImm1(long a, long b) {
        return 896 * b; //CV
    }

    public static long testMul(long a, long b) {
        return a * b; //CV
    }

    public static long testSubImm2(long a, long b) {
        return 1557280266 - b; // TODO: maps to esubq(Reg, Reg, Reg)
    }

    public static long testSubImm1(long a, long b) {
        return a - 1557280266; // TODO: maps to eaddq(Reg, Reg, Imm)
    }

    public static long testSub(long a, long b) {
        return a - b; //CV
    }

    public static long testAddImm2(long a, long b) {
        return 9 + b; //CV
    }

    public static long testAddImm1(long a, long b) {
        return a + 7; //CV
    }

    public static long testAdd(long a, long b) {
        return a + b; //CV
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            Method method = TestLongReg.class.getMethod("test" + args[1], long.class, long.class);

            int factor = 10_000;
            int size = factor * iters;
            long[] a = new long[size];
            long[] b = new long[size];
            long[] c = new long[size];
            Random rand = new Random(0);
            for (int i = 0; i < a.length; i++) {
                a[i] = i == 0 ? Long.parseLong(args[2]) : rand.nextInt();
                b[i] = i == 0 ? Long.parseLong(args[3]) : rand.nextInt();
            }

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

            System.out.println("\n ------------- MAIN DONE: " + "test" + args[1] + "(" + a[0] + "," + b[0] + ") = " + c[0]);
            assert c[0] == Long.parseLong(args[4]) : "APX NDD test failed; expected = " + args[4];

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}