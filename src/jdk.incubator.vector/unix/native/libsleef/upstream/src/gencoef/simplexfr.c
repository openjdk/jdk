 // The original code for simplex algorithm is taken from Haruhiko Okumura's book.
// https://oku.edu.mie-u.ac.jp/~okumura/algo/
// The code is distributed under the Creative Commons Attribution 4.0 International License.
// https://creativecommons.org/licenses/by/4.0/

// The code is modified by Naoki Shibata to process arbitrary precision numbers.

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <float.h>
#include <time.h>
#include <mpfr.h>

#define PREC 4096
#define EPS 1e-50

#define OK 0
#define MAXIMIZABLE_TO_INFINITY 1
#define NOT_FEASIBLE 2
#define ERROR (-1)

#define NOP (-1)
#define EQU (0)
#define LEQ 1
#define GEQ 2

static int m, n, n1, n2, n3, jmax;
static int *col, *row, *nonzero_row, *inequality;
static mpfr_t **a, *c, **q, *pivotcolumn;

static mpfr_t zero, one, eps, minuseps, large;

void mpfr_zinit(mpfr_t m) {
  mpfr_init(m);
  mpfr_set_d(m, 0, GMP_RNDN);
}

static void init(int n0, int m0) {
  int i, j;

  m = m0; n = n0;

  mpfr_init(zero); mpfr_set_d(zero, 0, GMP_RNDN);
  mpfr_init(one); mpfr_set_d(one, 1, GMP_RNDN);

  mpfr_init(eps);
  mpfr_set_d(eps, EPS, GMP_RNDN);

  mpfr_init(minuseps);
  mpfr_set_d(minuseps, -EPS, GMP_RNDN);

  mpfr_init(large);
  mpfr_set_d(large, 1.0 / EPS, GMP_RNDN);

  a = malloc(sizeof(mpfr_t *) * (m + 1));
  for(i=0;i < m+1;i++) {
    a[i] = malloc(sizeof(mpfr_t) * (n + 1));
    for(j=0;j < (n+1);j++) {
      mpfr_zinit(a[i][j]);
    }
  }

  q = malloc(sizeof(mpfr_t *) * (m + 1));
  for(i=0;i < m+1;i++) {
    q[i] = malloc(sizeof(mpfr_t) * (m + 1));
    for(j=0;j < m+1;j++) {
      mpfr_zinit(q[i][j]);
    }
  }

  c = malloc(sizeof(mpfr_t) * (n + 1));
  for(j=0;j < (n+1);j++) {
    mpfr_zinit(c[j]);
  }

  pivotcolumn = malloc(sizeof(mpfr_t) * (m + 1));
  for(j=0;j < (m+1);j++) {
    mpfr_zinit(pivotcolumn[j]);
  }

  col = calloc(m+1, sizeof(int));
  row = calloc(n+2*m+1, sizeof(int));
  nonzero_row = calloc(n+2*m+1, sizeof(int));
  inequality = calloc(m+1, sizeof(int));
}

static void dispose() {
  mpfr_clears(zero, one, eps, minuseps, large, (mpfr_ptr)0);

  int i, j;

  for(i=0;i < m+1;i++) {
    for(j=0;j < m+1;j++) {
      mpfr_clear(q[i][j]);
    }
    free(q[i]);
  }
  free(q);

  for(i=0;i < m+1;i++) {
    for(j=0;j < n+1;j++) {
      mpfr_clear(a[i][j]);
    }
    free(a[i]);
  }
  free(a);

  for(j=0;j < n+1;j++) {
    mpfr_clear(c[j]);
  }
  free(c);

  for(j=0;j < m+1;j++) {
    mpfr_clear(pivotcolumn[j]);
  }
  free(pivotcolumn);

  free(col);
  free(row);
  free(nonzero_row);
  free(inequality);
}

static void prepare() {
  int i;

  n1 = n;
  for (i = 1; i <= m; i++)
    if (inequality[i] == GEQ) {
      n1++;  nonzero_row[n1] = i;
    }
  n2 = n1;
  for (i = 1; i <= m; i++)
    if (inequality[i] == LEQ) {
      n2++;  col[i] = n2;
      nonzero_row[n2] = row[n2] = i;
    }
  n3 = n2;
  for (i = 1; i <= m; i++)
    if (inequality[i] != LEQ) {
      n3++;  col[i] = n3;
      nonzero_row[n3] = row[n3] = i;
    }

  for (i = 0; i <= m; i++) {
    mpfr_set_d(q[i][i], 1, GMP_RNDN);
  }
}

static void tableau(mpfr_t ret, int i, int j) {
  int k;

  if (col[i] < 0) { mpfr_set_d(ret, 0, GMP_RNDN); return; }

  if (j <= n) {
    mpfr_t s;
    mpfr_zinit(s);
    mpfr_set_d(s, 0, GMP_RNDN);

    mpfr_t *tab = malloc(sizeof(mpfr_t) * (m + 1));
    mpfr_ptr *ptab = malloc(sizeof(mpfr_ptr) * (m + 1));
    for (k = 0; k <= m; k++) {
      mpfr_zinit(tab[k]);
      ptab[k] = (mpfr_ptr)&tab[k];
      mpfr_mul(tab[k], q[i][k], a[k][j], GMP_RNDN);
    }
    mpfr_sum(s, ptab, m+1, GMP_RNDN);
    for (k = 0; k <= m; k++) {
      mpfr_clear(tab[k]);
    }
    free(ptab);
    free(tab);

    mpfr_set(ret, s, GMP_RNDN);
    mpfr_clear(s);
    return;
  }

  mpfr_set(ret, q[i][nonzero_row[j]], GMP_RNDN);

  if (j <= n1) { mpfr_neg(ret, ret, GMP_RNDN); return; }
  if (j <= n2 || i != 0) return;

  mpfr_add(ret, ret, one, GMP_RNDN);
  return;
}

static void pivot(int ipivot, int jpivot) {
  int i, j;
  mpfr_t u;

  mpfr_zinit(u);

  mpfr_set(u, pivotcolumn[ipivot], GMP_RNDN);

  for (j = 1; j <= m; j++) {
    mpfr_div(q[ipivot][j], q[ipivot][j], u, GMP_RNDN);
  }

  for (i = 0; i <= m; i++)
    if (i != ipivot) {
      mpfr_set(u, pivotcolumn[i], GMP_RNDN);

      for (j = 1; j <= m; j++) {
        mpfr_fms(q[i][j], q[ipivot][j], u, q[i][j], GMP_RNDN);
        mpfr_neg(q[i][j], q[i][j], GMP_RNDN);
      }
    }

  row[col[ipivot]] = 0;

  col[ipivot] = jpivot;  row[jpivot] = ipivot;

  mpfr_clear(u);
}

static int minimize() {
  int i, ipivot, jpivot;
  mpfr_t t, u;
  mpfr_inits(t, u, (mpfr_ptr)0);

  for (;;) {
    for (jpivot = 1; jpivot <= jmax; jpivot++) {
      if (row[jpivot] == 0) {
        tableau(pivotcolumn[0], 0, jpivot);
        if (mpfr_cmp(pivotcolumn[0], minuseps) < 0) break;
      }
    }
    if (jpivot > jmax) {
      mpfr_clears(t, u, (mpfr_ptr)0);
      return 1;
    }

    mpfr_set(u, large, GMP_RNDN);
    ipivot = 0;
    for (i = 1; i <= m; i++) {
      tableau(pivotcolumn[i], i, jpivot);
      if (mpfr_cmp(pivotcolumn[i], eps) > 0) {
        tableau(t, i, 0);
        mpfr_div(t, t, pivotcolumn[i], GMP_RNDN);
        if (mpfr_cmp(t, u) < 0) { ipivot = i; mpfr_set(u, t, GMP_RNDN); }
      }
    }
    if (ipivot == 0) {
      mpfr_clears(t, u, (mpfr_ptr)0);
      return 0; // the objective function can be minimized to -infinite
    }
    pivot(ipivot, jpivot);
  }
}

static int phase1() {
  int i, j;
  mpfr_t u;
  mpfr_zinit(u);

  jmax = n3;
  for (i = 0; i <= m; i++) {
    if (col[i] > n2) mpfr_set_d(q[0][i], -1, GMP_RNDN);
  }

  minimize();

  tableau(u, 0, 0);
  if (mpfr_cmp(u, minuseps) < 0) {
    mpfr_clear(u);
    return 0;
  }
  for (i = 1; i <= m; i++) {
    if (col[i] > n2) {
      col[i] = -1;
    }
  }
  mpfr_set_d(q[0][0], 1, GMP_RNDN);
  for (j = 1; j <= m; j++) mpfr_set_d(q[0][j], 0, GMP_RNDN);
  for (i = 1; i <= m; i++) {
    if ((j = col[i]) > 0 && j <= n && mpfr_cmp_d(c[j], 0) != 0) {
      mpfr_set(u, c[j], GMP_RNDN);
      for (j = 1; j <= m; j++) {
        mpfr_fms(q[0][j], q[i][j], u, q[0][j], GMP_RNDN);
        mpfr_neg(q[0][j], q[0][j], GMP_RNDN);
      }
    }

  }

  mpfr_clear(u);
  return 1;
}

static int phase2() {
  int j;
  jmax = n2;
  for (j = 0; j <= n; j++) {
    mpfr_set(a[0][j], c[j], GMP_RNDN);
  }

  return minimize();
}

int solve_fr(mpfr_t *result, int n0, int m0, mpfr_t **a0, int *ineq0, mpfr_t *c0) {
  int i,j;

  m = m0;   // number of inequations
  n = n0+1; // number of variables

  init(n, m);

  mpfr_t csum;
  mpfr_zinit(csum);

  for(j=0;j<n0+1;j++) {
    mpfr_set(c[j], c0[j], GMP_RNDN);
  }

  for(j=1;j<n0+1;j++) {
    mpfr_add(csum, csum, c0[j], GMP_RNDN);
  }

  mpfr_set(c[n], csum, GMP_RNDN);
  mpfr_neg(c[n], c[n], GMP_RNDN);

  for(i=0;i<m;i++) {
    mpfr_set_d(csum, 0, GMP_RNDN);

    for(j=0;j<n0+1;j++) mpfr_set(a[i+1][j], a0[i][j], GMP_RNDN);
    mpfr_neg(a[i+1][0], a[i+1][0], GMP_RNDN);

    for(j=1;j<n0+1;j++) {
      mpfr_add(csum, csum, a0[i][j], GMP_RNDN);
    }

    mpfr_set(a[i+1][n], csum, GMP_RNDN);
    mpfr_neg(a[i+1][n], a[i+1][n], GMP_RNDN);
    inequality[i+1] = ineq0[i];

    if (mpfr_cmp_d(a[i+1][0], 0) < 0) {
      if      (inequality[i+1] == GEQ) inequality[i+1] = LEQ;
      else if (inequality[i+1] == LEQ) inequality[i+1] = GEQ;
      for (j = 0; j <= n; j++) mpfr_neg(a[i+1][j], a[i+1][j], GMP_RNDN);
    } else if (mpfr_cmp_d(a[i+1][0], 0) == 0 && inequality[i+1] == GEQ) {
      inequality[i+1] = LEQ;
      for (j = 1; j <= n; j++) mpfr_neg(a[i+1][j], a[i+1][j], GMP_RNDN);
    }
  }

  int p1r = 1;

  prepare();
  if (n3 != n2) p1r = phase1();

  if (!p1r) {
    dispose();
    return NOT_FEASIBLE;
  }

  int b = phase2();

  mpfr_t *s = calloc(sizeof(mpfr_t), n);
  for(j=0;j<n;j++) {
    mpfr_zinit(s[j]);
  }

  for (j = 1; j < n; j++) {
    if ((i = row[j]) != 0) {
      tableau(s[j], i, 0);
    }
  }

  mpfr_t cs;
  mpfr_zinit(cs);
  if (row[n] != 0) tableau(cs, row[n], 0);

  for (j = 1; j < n; j++) {
    mpfr_sub(s[j], s[j], cs, GMP_RNDN);
  }

  for(j=0;j<n;j++) {
    mpfr_set(result[j], s[j], GMP_RNDN);
  }

  mpfr_clear(cs);

  for(j=0;j<n;j++) mpfr_clear(s[j]);
  free(s);

  dispose();

  return b ? OK : MAXIMIZABLE_TO_INFINITY;
}

void regressMinRelError_fr(int n, int m, mpfr_t **x, mpfr_t *result) {
  int m0 = n * 3, n0 = m + 2 * n, i, j;
  mpfr_t **a0, *c0, *result0;
  int in0[m0];

  a0 = malloc(sizeof(mpfr_t *) * m0);
  for(i=0;i<m0;i++) {
    a0[i] = calloc(n0+1, sizeof(mpfr_t));
    for(j=0;j<n0+1;j++) mpfr_zinit(a0[i][j]);
  }

  c0 = calloc(n0+1, sizeof(mpfr_t));
  result0 = calloc(n0+1, sizeof(mpfr_t));

  for(j=0;j<n0+1;j++) {
    mpfr_zinit(c0[j]);
    mpfr_zinit(result0[j]);
  }

  for(i=0;i<n;i++) {
    long double ld = mpfr_get_ld(x[m][i], GMP_RNDN);
    if (ld < DBL_MIN) ld = 1;

#if 1
    mpfr_set_ld(c0[m+i  +1], 1.0/fabsl(ld), GMP_RNDN);
    mpfr_set_ld(c0[m+n+i+1], 1.0/fabsl(ld), GMP_RNDN);
#else
    int e;
    frexpl(ld, &e);
    ld = 1.0 / ldexpl(1.0, e);
    mpfr_set_ld(c0[m+i  +1], ld, GMP_RNDN);
    mpfr_set_ld(c0[m+n+i+1], ld, GMP_RNDN);
#endif

    mpfr_set_d(a0[i*3+0][m+i+1], 1, GMP_RNDN);
    in0[i*3+0] = GEQ;

    mpfr_set_d(a0[i*3+1][m+n+i+1], 1, GMP_RNDN);
    in0[i*3+1] = GEQ;

    for(j=0;j<m;j++) {
      mpfr_set(a0[i*3+2][j+1], x[j][i], GMP_RNDN);
    }

    mpfr_set_d(a0[i*3+2][m+i+1], 1, GMP_RNDN);
    mpfr_set_d(a0[i*3+2][m+n+i+1], -1, GMP_RNDN);
    in0[i*3+2] = EQU;
    mpfr_set(a0[i*3+2][0], x[m][i], GMP_RNDN);
    mpfr_neg(a0[i*3+2][0], a0[i*3+2][0], GMP_RNDN);
  }

  int status = solve_fr(result0, n0, m0, a0, in0, c0);

  if (status == NOT_FEASIBLE) {
    printf("not feasible\n");
  } else {
    if (status == MAXIMIZABLE_TO_INFINITY) printf("maximizable to inf\n");
  }

  for(i=0;i<m;i++) {
    mpfr_set(result[i], result0[i+1], GMP_RNDN);
  }

  free(result0);
  free(c0);
}
