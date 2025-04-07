// This is part of SLEEF, written by Naoki
// Shibata. http://shibatch.sourceforge.net

// The code in this file is distributed under the Creative Commons
// Attribution 4.0 International License.

#define PREC_TARGET 53

#if 0
#define N 8           // Degree of equation
#define S 40          // Number of samples for phase 1
#define L 4           // Number of high precision coefficients
#define MIN 0.0       // Min argument
#define MAX (M_PI/4)  // Max argument
#define PMUL 2        // The form of polynomial is y = x^(PADD+PMUL*0) + x^(PADD+PMUL*1) + ...
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_sin(ret, a, GMP_RNDN); } // The function to approximate
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define FIXCOEF0 1.0  // Fix coef 0 to 1.0
#endif

#if 0
#define N 10
#define S 40
#define L 2
#define MIN 0.0
#define MAX (M_PI/4)

void TARGET(mpfr_t ret, mpfr_t a) { // cos(x) - 1
  mpfr_t x;
  mpfr_init(x);
  mpfr_cos(ret, a, GMP_RNDN);
  mpfr_set_ld(x, 1, GMP_RNDN);
  mpfr_sub(ret, ret, x, GMP_RNDN);
  mpfr_clear(x);
}
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }

#define PMUL 2
#define PADD 2
#define FIXCOEF0 (-0.5)
#endif


#if 0 // for xsincospi4_u05
#define S 40
#define N 8
#define L 2
#define MIN 0.0
#define MAX 1.0
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) {
  mpfr_t x, y;
  mpfr_inits(x, y, NULL);
  mpfr_const_pi(x, GMP_RNDN);
  mpfr_set_d(y, 1.0/4, GMP_RNDN);
  mpfr_mul(x, x, y, GMP_RNDN);
  mpfr_mul(x, x, a, GMP_RNDN);
  mpfr_sin(ret, x, GMP_RNDN);
  mpfr_clears(x, y, NULL);
}
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#endif

#if 0 // for xsincospi4_u05
#define N 8
#define S 40
#define L 2
#define MIN 0.0
#define MAX 1.0

void TARGET(mpfr_t ret, mpfr_t a) {
  mpfr_t x, y;
  mpfr_inits(x, y, NULL);
  mpfr_const_pi(x, GMP_RNDN);
  mpfr_set_d(y, 1.0/4, GMP_RNDN);
  mpfr_mul(x, x, y, GMP_RNDN);
  mpfr_mul(x, x, a, GMP_RNDN);
  mpfr_cos(ret, x, GMP_RNDN);
  mpfr_set_ld(x, 1, GMP_RNDN);
  mpfr_sub(ret, ret, x, GMP_RNDN);
  mpfr_clears(x, y, NULL);
}
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define PMUL 2
#define PADD 2
#endif


#if 0 // for xsincospi4
#define N 7
#define S 40
#define L 0
#define MIN 0.0
#define MAX 1.0
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) {
  mpfr_t x, y;
  mpfr_inits(x, y, NULL);
  mpfr_const_pi(x, GMP_RNDN);
  mpfr_set_d(y, 1.0/4, GMP_RNDN);
  mpfr_mul(x, x, y, GMP_RNDN);
  mpfr_mul(x, x, a, GMP_RNDN);
  mpfr_sin(ret, x, GMP_RNDN);
  mpfr_clears(x, y, NULL);
}
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#endif


#if 0
#define N 17
#define S 60
#define L 0
#define MIN 0.0
#define MAX (M_PI/4)
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_tan(ret, a, GMP_RNDN); }
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define FIXCOEF0 1.0
#endif

#if 0
#define N 11
#define S 35
#define L 2
#define MIN 1 //0.75
#define MAX 1.5
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_log(ret, a, GMP_RNDN); }
void CFUNC(mpfr_t frd, mpfr_t fra) {
  mpfr_t tmp, one;
  mpfr_inits(tmp, one, NULL);
  mpfr_set_d(one, 1, GMP_RNDN);
  mpfr_add(tmp, fra, one, GMP_RNDN);
  mpfr_sub(frd, fra, one, GMP_RNDN);
  mpfr_div(frd, frd, tmp, GMP_RNDN);
  mpfr_clears(tmp, one, NULL);
}
#define FIXCOEF0 2.0
#endif

#if 1
#define N 12
#define S 50
#define L 2
#define MIN -0.347
#define MAX 0.347 // 0.5 log 2
#define PMUL 1
#define PADD 0

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_exp(ret, a, GMP_RNDN); }
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define FIXCOEF0 1.0
#define FIXCOEF1 1.0
//#define FIXCOEF2 0.5
#endif

#if 0
#define N 21
#define S 100
#define L 1
#define P 1.1
#define MIN 0.0
#define MAX 1.0
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_atan(ret, a, GMP_RNDN); }
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define FIXCOEF0 1.0
#endif

#if 0
#define N 20
#define S 100
#define L 0
#define P 1.54
#define MIN 0.0
#define MAX 0.708
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_asin(ret, a, GMP_RNDN); }
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define FIXCOEF0 1.0
#endif
