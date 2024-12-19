import java.util.Arrays;
import java.util.Random;
import java.lang.reflect.Method;
import java.util.*;

public class TestIntMem {

    static int[] a;
    static int[] b;
    static int[] c;
    static int[] d;
    static int u = 0;
    static int v = 0;
    static int w;

    public static int testKernel(int a, int b) {
        int ans;
        int k = Integer.rotateLeft(-a, 5);
        int l = Integer.rotateRight(-b, 6);
        int m = Integer.rotateLeft(a, Math.abs(Integer.bitCount(l) % 7));
        ans = (a ^ b) + Math.max((a >> Math.min(2, k)), ((b >>> Math.min(3, l)) << 2));
        return ans;
    }

    public static int testCmov(int x, int i) {
        //TODO: find kernel
        return a != null ? (x > i ? i : a[i]): b[i];
    }

    public static int testCmovU(int x, int i) {
        //TODO: find kernel
        return 0;
    }

    public static int testCmovP(int x, int i) {
        // TODO: generates ecmovq (need a way to produce compressed ptr)
        int[] p = x > i ? a : null;
        int[] q = x == i ? b : p;
        d = p == q ? a : b;
        return d[0];
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

    public static int testDec(int x, int i) {
        return  b[i] - 1 ;
    }

    public static int testInc(int x, int i) {
        return  a[i] + 1 ;
    }

    public static int testNeg(int x, int i) {
        return  -b[i];
    }

    public static int testXorImm1(int x, int i) {
        return b[i] ^ 9;
    }

    public static int testXorImm2(int x, int i) {
        return 11 ^ a[i];
    }

    public static int testXor1(int x, int i) {
        return b[i] ^ x;
    }

    public static int testXor2(int x, int i) {
        return x ^ b[i];
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