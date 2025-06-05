#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdint.h>
#include <math.h>
#include <mpfr.h>
#include <quadmath.h>

#define N 8
#define B 8
#define NCOL (53-B)
#define NROW ((16385+(53-B)*N-106)/NCOL+1)

static double *rempitabqp = NULL;

void generateRempitabqp() {
  rempitabqp = calloc(16385-106+(53-B)*(N+1), sizeof(double));

  int orgprec = mpfr_get_default_prec();
  mpfr_set_default_prec(18000);

  mpfr_t pi, m, n, o;
  mpfr_inits(pi, m, n, o, NULL);
  mpfr_const_pi(pi, GMP_RNDN);

  mpfr_d_div(n, 0.5, pi, GMP_RNDN);

  for(int e=106;e<16385+(53-B)*N;e++) {
    mpfr_set(m, n, GMP_RNDN);

    mpfr_set_ui_2exp(o, 1, -(113 - e), GMP_RNDN);
    mpfr_mul(m, m, o, GMP_RNDN);

    mpfr_frac(m, m, GMP_RNDN);

    mpfr_set_ui_2exp(o, 1, (53-B), GMP_RNDN);
    mpfr_mul(m, m, o, GMP_RNDN);

    mpfr_trunc(m, m);

    mpfr_set_ui_2exp(o, 1, 7-(53-B), GMP_RNDN);
    mpfr_mul(m, m, o, GMP_RNDN);

    int col = (e - 106) % NCOL;
    int row = (e - 106) / NCOL;
    rempitabqp[col * NROW + row] = mpfr_get_d(m, GMP_RNDN);
  }

  mpfr_clears(pi, m, n, o, NULL);
  mpfr_set_default_prec(orgprec);
}


int main(int argc, char **argv) {
  generateRempitabqp();

  printf("NOEXPORT const double Sleef_rempitabqp[] = {\n  ");
  for(int i=0;i<16385-106+(53-B)*(N+1);i++) {
    printf("%.20g, ", rempitabqp[i]);
    if ((i & 3) == 3) printf("\n  ");
  }
  printf("\n};\n");
}
