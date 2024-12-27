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
        return  Integer.numberOfTrailingZeros(b[i]); //correctness verified
    }

    public static int testLzcnt(int x, int i) {
        return  Integer.numberOfLeadingZeros(b[i]); //correctness verified
    }

    public static int testPopcnt(int x, int i) {
        return  Integer.bitCount(b[i]); //correctness verified
    }

    public static int testShr(int x, int i) {
        return  a[i] >>> 6 ; //correctness verified
    }

    public static int testSal(int x, int i) {
        return  a[i] << 5 ; //correctness verified
    }

    public static int testSar(int x, int i) {
        return  a[i] >> 4 ; //correctness verified
    }

    public static int testDec(int x, int i) {
        return  b[i] - 1 ; //correctness verified
    }

    public static int testInc(int x, int i) {
        return  a[i] + 1 ; //correctness verified
    }

    public static int testNeg(int x, int i) {
        return  -b[i]; //correctness verified
    }

    public static int testXorImm1(int x, int i) {
        return a[i] ^ 9; //correctness verified
    }

    public static int testXorImm2(int x, int i) {
        return 11 ^ b[i]; //correctness verified
    }

    public static int testXor1(int x, int i) {
        return b[i] ^ x; //correctness verified
    }

    public static int testXor2(int x, int i) {
        return x ^ b[i]; //correctness verified
    }

    public static int testOrImm1(int x, int i) {
        return a[i] | 9; //correctness verified
    }

    public static int testOrImm2(int x, int i) {
        return 11 | b[i]; //correctness verified
    }

    public static int testOr1(int x, int i) {
        return b[i] | x; //correctness verified
    }

    public static int testOr2(int x, int i) {
        return x | b[i]; //correctness verified
    }

    public static int testAndImm1(int x, int i) {
        return a[i] & 9; //correctness verified
    }

    public static int testAndImm2(int x, int i) {
        return 11 & b[i]; //correctness verified
    }

    public static int testAnd1(int x, int i) {
        return b[i] & x; //correctness verified
    }

    public static int testAnd2(int x, int i) {
        return x & b[i];  //correctness verified
    }

    public static int testMulImm1(int x, int i) {
        return a[i] * 5971; //correctness verified
    }

    public static int testMulImm2(int x, int i) {
        return 487 * b[i]; //correctness verified
    }

    public static int testMul2(int x, int i) {
        return b[i] * x; //correctness verified
    }

    public static int testMul1(int x, int i) {
        return x * b[i]; //correctness verified
    }

    public static  int testSubImm2(int x, int i) {
        return 97 - b[i]; //TODO: maps to esubl(Reg, Reg, Mem)
    }

    public static  int testSubImm1(int x, int i) {
        return a[i] - 47; //TODO: maps to eaddl(Reg, Mem, Imm)
    }

    public static  int testSub2(int x, int i) {
        return b[i] - x; //correctness verified
    }

    public static int testSub1(int x, int i) {
        return x - b[i]; //correctness verified
    }

    public static int testAddImm2(int x, int i) {
        return 19 + b[i]; //correctness verified
    }

    public static int testAddImm1(int x, int i) {
        return a[i] + 27; //correctness verified
    }

    public static int testAdd2(int x, int i) {
        return b[i] + x; //correctness verified
    }

    public static int testAdd1(int x, int i) {
        return x + b[i]; //correctness verified
    }

    public static void init(int size, int a0, int b0) {
        a = new int[size];
        b = new int[size];
        c = new int[size];
        Random rand = new Random(0);
        for (int i = 0; i < a.length; i++) {
            a[i] = i == 0 ? a0 : rand.nextInt();
            b[i] = i == 0 ? b0 : rand.nextInt();
        }
    }

    public static void main(String[] args) {

        try {

            int iters = Integer.parseInt(args[0]);
            Method method = TestIntMem.class.getMethod("test" + args[1], int.class, int.class);

            int factor = 10_000;
            int size = factor * iters;
            init(size, Integer.parseInt(args[2]), Integer.parseInt(args[3]));

            // warmup
            for (int i = 0; i < size; i++) {
                c[i] = (int) method.invoke(null, a[i], i);
            }
            System.out.println("Warmup done>");

            // main iter
            for (int i = 0; i < size; i++) {
                c[i] = (int) method.invoke(null, a[i], i);
            }

            System.out.println("\n ------------- MAIN DONE: " + "test" + args[1] + "(" + a[0] + "," + b[0] + ") = " + c[0]);
            assert c[0] == Integer.parseInt(args[4]) : "APX NDD test failed; expected = " + args[4];


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}