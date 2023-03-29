import java.util.Random;
import java.util.concurrent.TimeUnit;

/*

/Users/tholenst/dev/jdk4/build/macosx-aarch64/jdk/bin/java -XX:-TieredCompilation -XX:CompileCommand=compileonly,Main::compute -XX:CompileCommand=compileonly,Main::computeFdLibm  -XX:CompileCommand=compileonly,Main::computeLog  -XX:CompileCommand=dontinline,java.lang.Double::* Main2.java

*/

public class Main {

    public static void main(String[] args) throws Exception {
        final Random random = new Random(42);
        double value = random.nextDouble();


        final long start = System.nanoTime();
        compute(value);
        final long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println(elapsed + " ms (Math.log)");


        final long start2 = System.nanoTime();
        computeFdLibm(value);
        final long elapsed2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start2);
        System.out.println(elapsed2 + " ms (FdLibm.Log)");

        double res1 = Math.log(value);
        double res2 = computeLog(value);

        System.out.println(res1);
        System.out.println(res2);
        assert res1 == res2;
    }


    public static void compute(double value) {
        for (int i = 0; i < 10_000_000; i++) {
            Math.log(value);
        }
    }

    public static void computeFdLibm(double value) {
        for (int i = 0; i < 10_000_000; i++) {
            //StrictMath.log(value);
            computeLog(value);
        }
    }



    /**
     * Return a double with its high-order bits of the second argument
     * and the low-order bits of the first argument..
     */
    private static double __HI2(double x, int high) {
        long transX = Double.doubleToRawLongBits(x);
        return Double.longBitsToDouble((transX & 0x0000_0000_FFFF_FFFFL) |
                                       ( ((long)high)) << 32 );
    }


    private static final double TWO54    = 0x1.0p54; // 1.80143985094819840000e+16

    private static final double
        ln2_hi = 0x1.62e42feep-1,       // 6.93147180369123816490e-01
        ln2_lo = 0x1.a39ef35793c76p-33, // 1.90821492927058770002e-10

        Lg1    = 0x1.5555555555593p-1,  // 6.666666666666735130e-01
        Lg2    = 0x1.999999997fa04p-2,  // 3.999999999940941908e-01
        Lg3    = 0x1.2492494229359p-2,  // 2.857142874366239149e-01
        Lg4    = 0x1.c71c51d8e78afp-3,  // 2.222219843214978396e-01
        Lg5    = 0x1.7466496cb03dep-3,  // 1.818357216161805012e-01
        Lg6    = 0x1.39a09d078c69fp-3,  // 1.531383769920937332e-01
        Lg7    = 0x1.2f112df3e5244p-3;  // 1.479819860511658591e-01

    private static final double zero = 0.0;

    static double computeLog(double x) {
        double hfsq, f, s, z, R, w, t1, t2, dk;
        int k, hx, i, j;
        /*unsigned*/ int lx;

        hx = (int)(Double.doubleToRawLongBits(x) >> 32); // high word of x
        lx = (int)Double.doubleToRawLongBits(x);  // low  word of x

        k=0;
        if (hx < 0x0010_0000) {                  // x < 2**-1022
            if (((hx & 0x7fff_ffff) | lx) == 0) { // log(+-0) = -inf
                return -TWO54/zero;
            }
            if (hx < 0) {                        // log(-#) = NaN
                return (x - x)/zero;
            }
            k -= 54;
            x *= TWO54;    // subnormal number, scale up x
            hx = (int)(Double.doubleToRawLongBits(x) >> 32); // high word of x
        }
        if (hx >= 0x7ff0_0000) {
            return x + x;
        }
        k += (hx >> 20) - 1023;
        hx &= 0x000f_ffff;
        i = (hx + 0x9_5f64) & 0x10_0000;
        x =__HI2(x, hx | (i ^ 0x3ff0_0000));  // normalize x or x/2
        k += (i >> 20);
        f = x - 1.0;
        if ((0x000f_ffff & (2 + hx)) < 3) {// |f| < 2**-20
            if (f == zero) {
                if (k == 0) {
                    return zero;
                } else {
                    dk = (double)k;
                    return dk*ln2_hi + dk*ln2_lo;
                }
            }
            R = f*f*(0.5 - 0.33333333333333333*f);
            if (k == 0) {
                return f - R;
            } else {
                dk = (double)k;
                return dk*ln2_hi - ((R - dk*ln2_lo) - f);
            }
        }
        s = f/(2.0 + f);
        dk = (double)k;
        z = s*s;
        i = hx - 0x6_147a;
        w = z*z;
        j = 0x6b851 - hx;
        t1= w*(Lg2 + w*(Lg4 + w*Lg6));
        t2= z*(Lg1 + w*(Lg3 + w*(Lg5 + w*Lg7)));
        i |= j;
        R = t2 + t1;
        if (i > 0) {
            hfsq = 0.5*f*f;
            if (k == 0) {
                return f-(hfsq - s*(hfsq + R));
            } else {
                return dk*ln2_hi - ((hfsq - (s*(hfsq + R) + dk*ln2_lo)) - f);
            }
        } else {
            if (k == 0) {
                return f - s*(f - R);
            } else {
                return dk*ln2_hi - ((s*(f - R) - dk*ln2_lo) - f);
            }
        }
    }



}
