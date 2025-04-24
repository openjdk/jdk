// This is part of SLEEF, written by Naoki
// Shibata. http://shibatch.sourceforge.net

// The code in this file is distributed under the Creative Commons
// Attribution 4.0 International License.

#define PREC_TARGET 113

//

#if 0
#define N 15          // Degree of equation
#define S 150         // Number of samples for phase 1
#define L 0           // Number of high precision coefficients
#define P 0.37
#define MIN 0.0       // Min argument
#define MAX (M_PI/2)  // Max argument
#define PMUL 2        // The form of polynomial is y = x^(PADD+PMUL*0) + x^(PADD+PMUL*1) + ...
#define PADD 3
void TARGET(mpfr_t ret, mpfr_t a) { // The function to approximate
  mpfr_sin(ret, a, GMP_RNDN);
  mpfr_sub(ret, ret, a, GMP_RNDN); // ret = sin(a) - a
}
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#endif

#if 0
#define N 15
#define S 150
#define L 0
#define MIN 0.0
#define MAX (M_PI/2)
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
//#define FIXCOEF0 (-0.5)
#endif


#if 0 // for xsincospi4_u05
#define N 13
#define S 150
#define L 2
#define P 0.9
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
#define N 13
#define S 150
#define L 2
#define MIN 0.0
#define MAX 1.0
void TARGET(mpfr_t ret, mpfr_t a) { // cos(x) - 1
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

#if 0 // running
#define N 31
#define S 100
#define P 1.7
#define L 0
#define MIN 0.0
#define MAX (M_PI/4)
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_tan(ret, a, GMP_RNDN); }
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define FIXCOEF0 1.0
#endif

#if 0 // running
#define N 20
#define S 110
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
#define N 22
#define S 140
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

#if 0 // running
#define N 45
#define S 100
#define P 1.55
#define L 2
#define MIN 0.0
#define MAX 1.0
#define PMUL 2
#define PADD 1

void TARGET(mpfr_t ret, mpfr_t a) { mpfr_atan(ret, a, GMP_RNDN); }
void CFUNC(mpfr_t dst, mpfr_t src) { mpfr_set(dst, src, GMP_RNDN); }
#define FIXCOEF0 1.0
#endif
