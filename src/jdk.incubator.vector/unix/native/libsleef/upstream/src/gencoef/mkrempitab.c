#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <mpfr.h>

static int64_t doubleToRawLongBits(double d) {
  union {
    double f;
    int64_t i;
  } tmp;
  tmp.f = d;
  return tmp.i;
}

static double longBitsToDouble(int64_t i) {
  union {
    double f;
    int64_t i;
  } tmp;
  tmp.i = i;
  return tmp.f;
}

static double removelsb(double d) {
  return longBitsToDouble(doubleToRawLongBits(d) & 0xfffffffffffffffeLL);
}

static int32_t floatToRawIntBits(float d) {
  union {
    float f;
    int32_t i;
  } tmp;
  tmp.f = d;
  return tmp.i;
}

static float intBitsToFloat(int32_t i) {
  union {
    float f;
    int32_t i;
  } tmp;
  tmp.i = i;
  return tmp.f;
}

static float removelsbf(float x) {
  return intBitsToFloat(0xfffffffc & floatToRawIntBits(x));
}

int main(int argc, char **argv) {
  mpfr_set_default_prec(2048);
  mpfr_t pi, rpi, xrpi, x, y, z, r;
  mpfr_inits(pi, rpi, xrpi, x, y, z, r, NULL);
  mpfr_const_pi(pi, GMP_RNDN);
  mpfr_set_d(x, 0.5, GMP_RNDN);
  mpfr_div(rpi, x, pi, GMP_RNDN);

  printf("NOEXPORT ALIGNED(64) const double rempitabdp[] = {\n");
  for(int i=55;i<1024;i++) {
    int M = i > 700 ? -64 : 0;
    int ex = i - 53;
    if (ex < -52) ex = -52;
    mpfr_set_d(x, ldexp(1, ex), GMP_RNDN);
    mpfr_mul(y, x, rpi, GMP_RNDN);
    mpfr_frac(xrpi, y, GMP_RNDN);
    mpfr_div(xrpi, xrpi, x, GMP_RNDN);

    mpfr_set_exp(xrpi, mpfr_get_exp(xrpi) - M);

    mpfr_set(x, xrpi, GMP_RNDN);

    double rpi0 = removelsb(mpfr_get_d(x, GMP_RNDN));
    mpfr_set_d(y, rpi0, GMP_RNDN);
    mpfr_sub(x, x, y, GMP_RNDN);

    double rpi1 = removelsb(mpfr_get_d(x, GMP_RNDN));
    mpfr_set_d(y, rpi1, GMP_RNDN);
    mpfr_sub(x, x, y, GMP_RNDN);

    double rpi2 = removelsb(mpfr_get_d(x, GMP_RNDN));
    mpfr_set_d(y, rpi2, GMP_RNDN);
    mpfr_sub(x, x, y, GMP_RNDN);

    double rpi3 = mpfr_get_d(x, GMP_RNDN);

    printf("  %.20g, %.20g, %.20g, %.20g,\n", rpi0, rpi1, rpi2, rpi3);
  }
  printf("};\n\n");

  printf("NOEXPORT ALIGNED(64) const float rempitabsp[] = {\n");
  for(int i=25;i<128;i++) {
    int M = i > 90 ? -64 : 0;
    int ex = i - 23;
    mpfr_set_d(x, ldexp(1, ex), GMP_RNDN);
    mpfr_mul(y, x, rpi, GMP_RNDN);
    mpfr_frac(xrpi, y, GMP_RNDN);
    mpfr_div(xrpi, xrpi, x, GMP_RNDN);

    mpfr_set_exp(xrpi, mpfr_get_exp(xrpi) - M);

    mpfr_set(x, xrpi, GMP_RNDN);

    float rpi20 = removelsbf(mpfr_get_d(x, GMP_RNDN));
    mpfr_set_d(y, rpi20, GMP_RNDN);
    mpfr_sub(x, x, y, GMP_RNDN);

    float rpi21 = removelsbf(mpfr_get_d(x, GMP_RNDN));
    mpfr_set_d(y, rpi21, GMP_RNDN);
    mpfr_sub(x, x, y, GMP_RNDN);

    float rpi22 = removelsbf(mpfr_get_d(x, GMP_RNDN));
    mpfr_set_d(y, rpi22, GMP_RNDN);
    mpfr_sub(x, x, y, GMP_RNDN);

    float rpi23 = mpfr_get_d(x, GMP_RNDN);

    printf("  %.10g, %.10g, %.10g, %.10g,\n", rpi20, rpi21, rpi22, rpi23);
  }
  printf("};\n");
}
