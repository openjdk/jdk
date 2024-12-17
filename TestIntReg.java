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

    public static int testTzcnt(int a, int b) {
        return  Integer.numberOfTrailingZeros(a);
    }

    public static int testLzcnt(int a, int b) {
        return  Integer.numberOfLeadingZeros(b);
    }

    public static int testPopcnt(int a, int b) {
        return  Integer.bitCount(a);
    }

    public static int testRor(int a, int b) {
        return  Integer.rotateRight(a, 8);
    }

    public static int testRol(int a, int b) {
        return  Integer.rotateLeft(b, 7); // getting mapped to Ror 25
    }


    public static int testShrVar(int a, int b) {
        return  a >>> (b >>> 28) ; // generates shrxl UseBMI2Instructions
    }

    public static int testShr(int a, int b) {
        return  a >>> 6 ;
    }

    public static int testSal(int a, int b) {
        return  a << 5 ;
    }

    public static int testSar(int a, int b) {
        return  b >> 4 ;
    }

    public static int testDec(int a, int b) {
        return  b - 1 ;
    }

    public static int testInc(int a, int b) {
        return  a + 1 ;
    }

    public static int testNeg(int a, int b) {
        return  -b;
    }

    public static int testXor(int a, int b) {
        return a ^ b;
    }

    public static int testOr(int a, int b) {
        return a | b;
    }

    public static int testAndImm1(int a, int b) {
        return a & 7;
    }

    public static int testAndImm2(int a, int b) {
        return 5 & b;
    }

    public static int testAnd(int a, int b) {
        return a & b;
    }

    public static int testCount(int a, int b) {
        return Integer.bitCount(a) + Integer.numberOfLeadingZeros(b) + Integer.numberOfTrailingZeros(a-b);
    }

    public static int testMul(int a, int b) {
        return a * b;
    }

    public static int testSub(int a, int b) {
        return a - b;
    }

    public static int testAdd(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            int factor = 10_000;
            int size = factor * iters;
            int[] a = new int[size];
            int[] b = new int[size];
            int[] c = new int[size];
            Random rand = new Random(0);
            for (int i = 0; i < a.length; i++) {
                a[i] = rand.nextInt();
                b[i] = rand.nextInt();
            }

            Method method = TestIntReg.class.getMethod("test" + args[1], int.class, int.class);

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
            System.out.println("------------- MAIN DONE ----------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}