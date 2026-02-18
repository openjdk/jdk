package java.lang;

import jdk.internal.vm.annotation.Stable;

import static java.lang.Double.doubleToRawLongBits;

final class OpenLibm {

    static final class Sincos {
        private Sincos() {throw new UnsupportedOperationException();}

        private static final double
                one =  1.00000000000000000000e+00,
                half =  5.00000000000000000000e-01;

        @Stable
        private static final double[]
                S = {-1.66666666666666324348e-01,
                8.33333333332248946124e-03,
                -1.98412698298579493134e-04,
                2.75573137070700676789e-06,
                -2.50507602534068634195e-08,
                1.58969099521155010221e-10};

        @Stable
        private static final double[]
                C = {4.16666666666666019037e-02,
                -1.38888888888741095749e-03,
                2.48015872894767294178e-05,
                -2.75573143513906633035e-07,
                2.08757232129817482790e-09,
                -1.13596475577881948265e-11};


        static Math.SinCosResult __kernel_sincos(double x, double y, int iy) {
            double k_s;
            double k_c;

            /* Inline calculation of sin/cos, as we can save
            some work, and we will always need to calculate
            both values, no matter the result of switch */
            final double z   = x*x;
            final double w   = z*z;

            /* cos-specific computation; equivalent to calling
             __kernel_cos(x,y) and storing in k_c*/
            double r   = z*(C[0]+z*(C[1]+z*C[2])) + w*w*(C[3]+z*(C[4]+z*C[5]));
            final double hz  = 0.5*z;
            double v   = one-hz;

            k_c = v + (((one-v)-hz) + (z*r-x*y));

            /* sin-specific computation; equivalent to calling
             __kernel_sin(x,y,1) and storing in k_s*/
            r   = S[1]+z*(S[2]+z*S[3]) + z*w*(S[4]+z*S[5]);
            v   = z*x;
            if(iy == 0) {
                k_s = x + v * (S[0] + z * r);
            } else {
                k_s = x - ((z * (half * y - v * r) - y) - v * S[0]);
            }
            return new Math.SinCosResult(k_s, k_c);
        }

        static Math.SinCosResult compute(double x) {
            double [] y = new double[2];
            double s, c;

            // High word of x.
            final long transducer = doubleToRawLongBits(x);
            int ix = (int)(transducer >> 32);

            ix &= 0x7fff_ffff;
            if(ix <= 0x3fe9_21fb) {
                /* Check for small x for sin and cos */
                if(ix < 0x3e46_a09e) {
                    /* Check for exact zero */
                    if( (int)x==0 ) {
                        s = x;
                        c = 1.0;
                        return new Math.SinCosResult(s, c);
                    }
                }
                /* Call kernel function with 0 extra */
                return __kernel_sincos(x,0.0,0);
            } else if( ix >= 0x7ff0_0000 ) {
                /* sincos(Inf or NaN) is NaN */
                s = x-x;
                c = x-x;
            } else { /*argument reduction needed*/
                /* Calculate remainer, then sub out to kernel */
                int n = FdLibm.RemPio2.__ieee754_rem_pio2(x, y);
                Math.SinCosResult k = __kernel_sincos(y[0], y[1], 1);
                double k_s = k.sin(), k_c = k.cos();
                /* Figure out permutation of sin/cos outputs to true outputs */
                switch(n&3) {
                    case 0:
                        c =  k_c;
                        s =  k_s;
                        break;
                    case 1:
                        c = -k_s;
                        s =  k_c;
                        break;
                    case 2:
                        c = -k_c;
                        s = -k_s;
                        break;
                    default:
                        c =  k_s;
                        s = -k_c;
                        break;
                }

            }
            return new Math.SinCosResult(s, c);
        }
    }
}
