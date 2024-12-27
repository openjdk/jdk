import java.util.Arrays;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.*;

public class TestIntReg {

    public static int testKernel(int a, int b) {
        int ans;
        int k = Integer.rotateLeft(-a, 5);
        int l = Integer.rotateRight(-b, 6);
        int m = Integer.rotateLeft(a, Math.abs(Integer.bitCount(l) % 7));
        ans = (a ^ b) + Math.max((a >> Math.min(2, k)), ((b >>> Math.min(3, l)) << 2));
        return ans;
    }

    public static int testCmovMin(int a, int b) {
        return Math.min(a, b); // correctness verified
    }

    public static int testCmovMax(int a, int b) {
        return Math.max(a, b); // correctness verified
    }

    public static int testCmov(int a, int b) {
        return a > 5 ? b : a; // correctness verified
    }

    public static int testCmovU(int a, int b) {
        return Integer.compareUnsigned(a, b) > 0 ? b : a; // correctness verified
    }

    public static int testTzcnt(int a, int b) {
        return  Integer.numberOfTrailingZeros(a); // correctness verified
    }

    public static int testLzcnt(int a, int b) {
        return  Integer.numberOfLeadingZeros(b); // correctness verified
    }

    public static int testPopcnt(int a, int b) {
        return  Integer.bitCount(a); // correctness verified
    }

    public static int testRor(int a, int b) {
        return  Integer.rotateRight(a, 8); // correctness verified
    }

    public static int testRol(int a, int b) {
        // TODO: getting mapped to erorl 25
        return  Integer.rotateLeft(a, 7); // correctness verified
    }

    public static int testShrVar(int a, int b) {
        return  a >>> (b >>> 28) ; // TODO: generates shrxl UseBMI2Instructions
    }

    public static int testShr(int a, int b) {
        return  a >>> 6 ; // correctness verified
    }

    public static int testSal(int a, int b) {
        return  a << 5 ; // correctness verified
    }

    public static int testSar(int a, int b) {
        return  a >> 4 ; // correctness verified
    }

    public static int testDec(int a, int b) {
        return  b - 1 ; // correctness verified
    }

    public static int testInc(int a, int b) {
        return  a + 1 ; // correctness verified
    }

    public static int testNeg(int a, int b) {
        return  -b; // correctness verified
    }

    public static int testXorM1(int a, int b) {
        // generates enotl
        return a ^ -1; // correctness verified
    }

    public static int testXorImm1(int a, int b) {
        return a ^ 7; // correctness verified
    }

    public static int testXorImm2(int a, int b) {
        return 5 ^ b; // correctness verified
    }

    public static int testXor(int a, int b) {
        return a ^ b; // correctness verified
    }

    public static int testOrImm1(int a, int b) {
        return a | 7; // correctness verified
    }

    public static int testOrImm2(int a, int b) {
        return 5 | b; // correctness verified
    }

    public static int testOr(int a, int b) {
        return a | b; // correctness verified
    }

    public static int testAndImm1(int a, int b) {
        return a & 7; // correctness verified
    }

    public static int testAndImm2(int a, int b) {
        return 5 & b; //correctness verified
    }

    public static int testAnd(int a, int b) {
        return a & b; //correctness verified
    }

    public static int testMulImm2(int a, int b) {
        return 8600 * b; //correctness verified
    }

    public static int testMulImm1(int a, int b) {
        return a * 7900; //correctness verified
    }

    public static int testMul(int a, int b) {
        return a * b; //correctness verified
    }

    public static int testSubImm2(int a, int b) {
        return 1557280266 - b; // TODO: maps to esubq(Reg, Reg, Reg)
    }

    public static int testSubImm1(int a, int b) {
        return a - -1557280266; // TODO: maps to leal
    }

    public static int testSub(int a, int b) {
        return a - b; //correctness verified
    }

    public static int testAddImm2(int a, int b) {
        return -27 + b; // TODO: maps to leal
    }

    public static int testAddImm1(int a, int b) {
        return a + 27; // TODO: SDE gives illegal instruction error TODO:
    }

    public static int testAdd(int a, int b) {
        return a + b; //correctness verified
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            Method method = TestIntReg.class.getMethod("test" + args[1], int.class, int.class);

            int factor = 10_000;
            int size = factor * iters;
            int[] a = new int[size];
            int[] b = new int[size];
            int[] c = new int[size];
            Random rand = new Random(0);
            for (int i = 0; i < a.length; i++) {
                a[i] = i == 0 ? Integer.parseInt(args[2]) : rand.nextInt();
                b[i] = i == 0 ? Integer.parseInt(args[3]) : rand.nextInt();
            }

            // warmup
            for (int i = 0; i < factor; i++) {
                //c[i] = testKernel(a[i], b[i]);
                c[i] = (int) method.invoke(null, a[i], b[i]);
            }
            System.out.println("Warmup done>");

            // main iter
            for (int i = 0; i < factor * iters; i++) {
                c[i] = (int) method.invoke(null, a[i], b[i]);
            }

            System.out.println("\n ------------- MAIN DONE: " + "test" + args[1] + "(" + a[0] + "," + b[0] + ") = " + c[0]);
            assert c[0] == Integer.parseInt(args[4]) : "APX NDD test failed; expected = " + args[4];

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}