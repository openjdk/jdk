import java.util.Arrays;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.*;

public class TestIntMem {

    static int[] a;
    static int[] b;
    static int[] c;

    public static int testKernel(int a, int b) {
        int ans;
        int k = Integer.rotateLeft(-a, 5);
        int l = Integer.rotateRight(-b, 6);
        int m = Integer.rotateLeft(a, Math.abs(Integer.bitCount(l) % 7));
        ans = (a ^ b) + Math.max((a >> Math.min(2, k)), ((b >>> Math.min(3, l)) << 2));
        return ans;
    }

    public static int testTzcnt(int x, int i) {
        return  Integer.numberOfTrailingZeros(a[i]);
    }

    public static int testLzcnt(int x, int i) {
        return  Integer.numberOfLeadingZeros(b[i]);
    }

    public static int testPopcnt(int x, int i) {
        return  Integer.bitCount(a[i]);
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

    public static int testOrImm1(int x, int i) {
        return b[i] | 9;
    }

    public static int testOrImm2(int x, int i) {
        return 11 | a[i];
    }

    public static int testOr1(int x, int i) {
        return b[i] | x;
    }

    public static int testOr2(int x, int i) {
        return x | b[i];
    }

    public static int testAndImm1(int x, int i) {
        return b[i] & 9;
    }

    public static int testAndImm2(int x, int i) {
        return 11 & a[i];
    }

    public static int testAnd1(int x, int i) {
        return b[i] & x;
    }

    public static int testAnd2(int x, int i) {
        return x & b[i];
    }

    public static int testMul2(int x, int i) {
        return b[i] * x;
    }

    public static int testMul1(int x, int i) {
        return x * b[i];
    }

    public static  int testSub2(int x, int i) {
        return b[i] - x;
    }

    public static int testSub1(int x, int i) {
        return x - b[i];
    }

    public static int testAdd2(int x, int i) {
        return b[i] + x;
    }

    public static int testAdd1(int x, int i) {
        return x + b[i];
    }

    public static void init(int size) {
        a = new int[size];
        b = new int[size];
        c = new int[size];
        Random rand = new Random(0);
        for (int i = 0; i < a.length; i++) {
            a[i] = rand.nextInt();
            b[i] = rand.nextInt();
        }
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            int factor = 10_000;
            int size = factor * iters;
            init(size);

            Method method = TestIntMem.class.getMethod("test" + args[1], int.class, int.class);

            // warmup
            for (int i = 0; i < factor; i++) {
                c[i] = (int) method.invoke(null, a[i], i);
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