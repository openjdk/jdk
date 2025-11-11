// This is part of SLEEF, written by Naoki Shibata. http://shibatch.sourceforge.net

// Since the original code for simplex algorithm is developed by Haruhiko Okumura and
// the code is distributed under the Creative Commons Attribution 4.0 International License,
// the contents under this directory are also distributed under the same license.

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <float.h>
#include <time.h>
#include <mpfr.h>

//#include "sp.h"
#include "dp.h"
//#include "ld.h"
//#include "qp.h"

#undef VERBOSE

#define PREC 4096

#define EPS 1e-50

#define PREC2 (PREC_TARGET*4)

#ifndef P
#define P 1
#endif

#ifndef Q
#define Q 10000
#endif

void mpfr_zinit(mpfr_t m);
void regressMinRelError_fr(int n, int m, mpfr_t **x, mpfr_t *result);

char *mpfrToStr(mpfr_t m) {
  mpfr_t fra;
  mpfr_init2(fra, mpfr_get_prec(m));

  mpfr_abs(fra, m, GMP_RNDN);
  mpfr_exp_t e;
  char *s = mpfr_get_str(NULL, &e, 10, 0, fra, GMP_RNDN);

  char *ret = malloc(strlen(s) + 20);

  if (mpfr_sgn(m) == -1) ret[0] = '-'; else ret[0] = '+';
  ret[1] = '0';
  ret[2] = '.';

  strcpy(&ret[3], s);
  mpfr_free_str(s);

  char estr[10];
  sprintf(estr, "e%+d", (int)e);
  strcat(ret, estr);

  mpfr_clears(fra, NULL);
  return ret;
}

double countULP(mpfr_t d, mpfr_t c) {
  mpfr_t fry, frw;
  mpfr_inits(fry, frw, NULL);

  double c2 = mpfr_get_d(c, GMP_RNDN);
  if (c2 == 0 && mpfr_cmp_d(d, 0) != 0) return 10000;

  long e;
  mpfr_get_d_2exp(&e, c, GMP_RNDN);
  mpfr_set_ui_2exp(frw, 1, e-PREC_TARGET, GMP_RNDN);

  mpfr_sub(fry, d, c, GMP_RNDN);
  mpfr_div(fry, fry, frw, GMP_RNDN);
  double u = fabs(mpfr_get_d(fry, GMP_RNDN));

  mpfr_clears(fry, frw, NULL);

  return u;
}

void func(mpfr_t s, mpfr_t x, mpfr_t *coef, int n) {
  mpfr_set_prec(s, PREC_TARGET);
  mpfr_set(s, coef[n-1], GMP_RNDN);

  for(int i=n-1;i>0;i--) {
    if (i == L-1) {
      mpfr_t t;
      mpfr_init2(t, PREC2);
      mpfr_set(t, s, GMP_RNDN);
      mpfr_set_prec(s, PREC2);
      mpfr_set(s, t, GMP_RNDN);
      mpfr_clear(t);
    }
    mpfr_mul(s, s, x, GMP_RNDN);
    mpfr_add(s, s, coef[i-1], GMP_RNDN);
  }
}

int main(int argc, char **argv)
{
  int i, j;
  int n, m;
  double p;

  mpfr_set_default_prec(PREC);

#if 0
  {
    mpfr_t a, b;
    mpfr_inits(a, b, NULL);

    float x = M_PI;
    mpfr_set_d(a, x, GMP_RNDN);
    x = nexttowardf(x, 100);
    x = nexttowardf(x, 100);
    x = nexttowardf(x, 100);
    mpfr_set_d(b, x, GMP_RNDN);

    printf("%g\n", countULP(b, a));
    mpfr_clears(a, b, NULL);
    exit(0);
  }
#endif

#if 0
  {
    mpfr_t a, b;
    mpfr_inits(a, b, NULL);

    double x = M_PI;
    mpfr_set_d(a, x, GMP_RNDN);
    x = nexttoward(x, 100);
    x = nexttoward(x, 100);
    x = nexttoward(x, 100);
    mpfr_set_d(b, x, GMP_RNDN);

    printf("%g\n", countULP(b, a));
    mpfr_clears(a, b, NULL);
    exit(0);
  }
#endif

#if 0
  {
    mpfr_t a, b;
    mpfr_inits(a, b, NULL);

    long double x = M_PI;
    mpfr_set_ld(a, x, GMP_RNDN);
    x = nexttowardl(x, 100);
    x = nexttowardl(x, 100);
    x = nexttowardl(x, 100);
    mpfr_set_ld(b, x, GMP_RNDN);

    printf("%g\n", countULP(b, a));
    mpfr_clears(a, b, NULL);
    exit(0);
  }
#endif

#if 0
  {
    mpfr_t a, b;
    mpfr_inits(a, b, NULL);

    __float128 x = M_PI;
    mpfr_set_f128(a, x, GMP_RNDN);
    x = nextafterq(x, 100);
    x = nextafterq(x, 100);
    x = nextafterq(x, 100);
    mpfr_set_f128(b, x, GMP_RNDN);

    printf("%g\n", countULP(b, a));
    mpfr_clears(a, b, NULL);
    exit(0);
  }
#endif

  m = N+1;
  n = argc >= 2 ? atoi(argv[1]) : S;
  p = argc >= 3 ? atof(argv[2]) : P;

  mpfr_t **x, *result;  // x[m][n], result[m]

  x = calloc(sizeof(mpfr_t *), m);
  result = calloc(sizeof(mpfr_t), m);
  for(i=0;i<m;i++) {
    x[i] = calloc(sizeof(mpfr_t), n);
    for(j=0;j<n;j++) mpfr_zinit(x[i][j]);
    mpfr_zinit(result[i]);
  }

  mpfr_t fra, frb, frc, frd, fre;

  mpfr_zinit(fra);
  mpfr_zinit(frb);
  mpfr_zinit(frc);
  mpfr_zinit(frd);
  mpfr_zinit(fre);

  for(i=0;i<n;i++) {
    double b = 1.0 - pow((double)i / (n-1), p);
    double a = ((double)MAX - MIN) * b + MIN;
    mpfr_set_d(fra, a, GMP_RNDN);
    CFUNC(frd, fra);

    for(j=0;j<m-1;j++) {
      mpfr_set_d(frb, (double)j*PMUL+PADD, GMP_RNDN);
      mpfr_pow(x[j][i], frd, frb, GMP_RNDN);
      //printf("%g ", mpfr_get_d(x[j][i], GMP_RNDN));
    }

    TARGET(x[m-1][i], fra);
    //printf(" : %g\n", mpfr_get_d(x[m-1][i], GMP_RNDN));
  }

  for(i=0;i<m-1;i++) mpfr_set_d(result[i], 0, GMP_RNDN);

  regressMinRelError_fr(n, m-1, x, result);

  for(i=m-2;i>=0;i--) {
    mpfr_set_prec(fra, PREC_TARGET+4);
    mpfr_set(fra, result[i], GMP_RNDN);

    char *s;
    printf("%s, \n", s = mpfrToStr(fra));
    free(s);
  }
  printf("\n");

  mpfr_set_prec(fra, PREC);

  double emax = 0;

  for(i=0;i<=n*10;i++) {
    double a = i * (double)(MAX - MIN) / (n*10.0) + MIN;
    mpfr_set_d(fra, a, GMP_RNDN);

    CFUNC(frd, fra);

    mpfr_set_d(frb, 0, GMP_RNDN);

    for(j=m-1;j>=0;j--) {
      mpfr_set_d(frc, (double)j*PMUL+PADD, GMP_RNDN);
      mpfr_pow(frc, frd, frc, GMP_RNDN);
      mpfr_mul(frc, frc, result[j], GMP_RNDN);
      mpfr_add(frb, frb, frc, GMP_RNDN);
    }

    TARGET(frc, fra);
    double u = countULP(frb, frc);

    if (u > emax) emax = u;
  }

  printf("Phase 1 : Max error = %g ULP\n\n", emax);
  fflush(stdout);

  //

  mpfr_t bestcoef[N], curcoef[N];

  for(i=0;i<N;i++) {
    mpfr_init2(bestcoef[i], i >= L ? PREC_TARGET : PREC2);
    mpfr_set(bestcoef[i], result[i], GMP_RNDN);

    mpfr_init2(curcoef[i], i >= L ? PREC_TARGET : PREC2);
    mpfr_set(curcoef[i], result[i], GMP_RNDN);
  }

  srandom(time(NULL));

  mpfr_set_default_prec(PREC2);

  static mpfr_t a[Q], v[Q], am[Q], aa[Q];

  for(i=0;i<Q;i++) {
    mpfr_inits(a[i], v[i], am[i], aa[i], NULL);

    mpfr_set_d(fra, ((double)MAX - (double)MIN) * i / (double)(Q-1) + (double)MIN, GMP_RNDN);

    TARGET(v[i], fra);
    CFUNC(a[i], fra);
    mpfr_set_d(frb, PMUL, GMP_RNDN);
    mpfr_pow(am[i], a[i], frb, GMP_RNDN);
    mpfr_set_d(frb, PADD, GMP_RNDN);
    mpfr_pow(aa[i], a[i], frb, GMP_RNDN);
    mpfr_clears(a[i], v[i], am[i], aa[i], NULL);
  }

  double best = 1e+100, bestsum = 1e+100, bestworstx;

  for(int k=0;k<10000;k++) {
    double emax = 0, esum = 0, worstx = 0;

#ifdef FIXCOEF0
    mpfr_set_d(curcoef[0], FIXCOEF0, GMP_RNDN);
#endif

#ifdef FIXCOEF1
    mpfr_set_d(curcoef[1], FIXCOEF1, GMP_RNDN);
#endif

#ifdef FIXCOEF2
    mpfr_set_d(curcoef[2], FIXCOEF2, GMP_RNDN);
#endif

    for(i=0;i<Q;i++) {
      if (mpfr_cmp_d(v[i], 0) == 0) continue;

      mpfr_set_d(frb, 0, GMP_RNDN);
      for(j=N-1;j>=0;j--) {
        mpfr_set_d(frc, (double)j*PMUL+PADD, GMP_RNDN);
        mpfr_pow(frc, a[i], frc, GMP_RNDN);
        mpfr_mul(frc, frc, curcoef[j], GMP_RNDN);
        mpfr_add(frb, frb, frc, GMP_RNDN);
      }

      double e = countULP(frb, v[i]);

      //printf("c = %.20g, t = %.20g, ulp = %g\n", mpfr_get_d(v[i], GMP_RNDN), mpfr_get_d(frb, GMP_RNDN), e);

      if (!isfinite(e)) continue;
      if (e > emax) { emax = e; worstx = mpfr_get_d(a[i], GMP_RNDN); }
      esum += e;
    }
    mpfr_set_prec(frb, PREC);

    //printf("emax = %g\n", emax);

    if (emax < best || (emax == best && esum < bestsum)) {
      for(i=0;i<N;i++) {
        mpfr_set(bestcoef[i], curcoef[i], GMP_RNDN);
      }
      if (best == 1e+100 || k > 10) printf("Max error = %g ULP, Sum error = %g (Max error at %g)\n", emax, esum, worstx);
      if ((best - emax) / best > 0.0001) k = 0;
      best = emax;
      bestsum = esum;
      bestworstx = worstx;
    }

    for(i=0;i<N;i++) {
      mpfr_set(curcoef[i], bestcoef[i], GMP_RNDN);
    }

    for(i=0;i<N;i++) {
      static int tab[] = {0, 0, 0, 0, 0, 0, 1, -1};
      //static int tab[] = {0, 0, 0, 0, 2, -2, 1, -1};
      int r = tab[random() & 7];
      if (r > 0) {
        for(int j=0;j<r;j++) mpfr_nextabove(curcoef[i]);
      } else if (r < 0) {
        for(int j=0;j>r;j--) mpfr_nextbelow(curcoef[i]);
      }
    }
  }

  printf("\n");

  for(i=N-1;i>=0;i--) {
    mpfr_set_prec(fra, i >= L ? PREC_TARGET+4 : PREC2);
    mpfr_set(fra, bestcoef[i], GMP_RNDN);

    char *s;
    printf("%s, \n", s = mpfrToStr(fra));
    free(s);
  }
  printf("\nPhase 2 : max error = %g ULP at %g\n", best, bestworstx);

  exit(0);
}
