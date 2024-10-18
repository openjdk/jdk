//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <signal.h>
#include <setjmp.h>

#include <math.h>

#include "sleef.h"

#include "misc.h"
#include "common.h"
#include "arraymap.h"
#include "dftcommon.h"

#ifdef _OPENMP
#include <omp.h>
#endif

#if BASETYPEID == 1
typedef double real;
typedef Sleef_double2 sc_t;
#define BASETYPESTRING "double"
#define MAGIC 0x27182818
#define MAGIC2D 0x17320508
#define INIT SleefDFT_double_init1d
#define EXECUTE SleefDFT_double_execute
#define INIT2D SleefDFT_double_init2d
#define CTBL ctbl_double
#define REALSUB0 realSub0_double
#define REALSUB1 realSub1_double
#define GETINT getInt_double
#define GETPTR getPtr_double
#define DFTF dftf_double
#define DFTB dftb_double
#define TBUTF tbutf_double
#define TBUTB tbutb_double
#define BUTF butf_double
#define BUTB butb_double
#define SINCOSPI Sleef_sincospi_u05
#include "dispatchdp.h"
#elif BASETYPEID == 2
typedef float real;
typedef Sleef_float2 sc_t;
#define BASETYPESTRING "float"
#define MAGIC 0x31415926
#define MAGIC2D 0x22360679
#define INIT SleefDFT_float_init1d
#define EXECUTE SleefDFT_float_execute
#define INIT2D SleefDFT_float_init2d
#define CTBL ctbl_float
#define REALSUB0 realSub0_float
#define REALSUB1 realSub1_float
#define GETINT getInt_float
#define GETPTR getPtr_float
#define DFTF dftf_float
#define DFTB dftb_float
#define TBUTF tbutf_float
#define TBUTB tbutb_float
#define BUTF butf_float
#define BUTB butb_float
#define SINCOSPI Sleef_sincospif_u05
#include "dispatchsp.h"
#else
#error No BASETYPEID specified
#endif

#define IMPORT_IS_EXPORT
#include "sleefdft.h"

//

real CTBL[] = {
  0.7071067811865475243818940365159164684883L, -0.7071067811865475243818940365159164684883L,
  0.9238795325112867561014214079495587839119L, -0.382683432365089771723257530688933059082L,
  0.382683432365089771723257530688933059082L, -0.9238795325112867561014214079495587839119L,
#if MAXBUTWIDTH >= 5
  0.9807852804032304491190993878113602022495L, -0.1950903220161282678433729148581576851029L,
  0.5555702330196022247573058028269343822103L, -0.8314696123025452370808655033762590846891L,
  0.8314696123025452370808655033762590846891L, -0.5555702330196022247573058028269343822103L,
  0.1950903220161282678433729148581576851029L, -0.9807852804032304491190993878113602022495L,
#endif
#if MAXBUTWIDTH >= 6
  0.9951847266721968862310254699821143731242L, -0.09801714032956060199569840382660679267701L,
  0.6343932841636454982026105398063009488396L, -0.7730104533627369607965383602188325085081L,
  0.881921264348355029715105513066220055407L, -0.4713967368259976485449225247492677226546L,
  0.2902846772544623676448431737195932100803L, -0.9569403357322088649310892760624369657307L,
  0.9569403357322088649310892760624369657307L, -0.2902846772544623676448431737195932100803L,
  0.4713967368259976485449225247492677226546L, -0.881921264348355029715105513066220055407L,
  0.7730104533627369607965383602188325085081L, -0.6343932841636454982026105398063009488396L,
  0.09801714032956060199569840382660679267701L, -0.9951847266721968862310254699821143731242L,
#endif
#if MAXBUTWIDTH >= 7
  0.9987954562051723927007702841240899260811L, -0.04906767432741801425355085940205324135377L,
  0.6715589548470184006194634573905233310143L, -0.7409511253549590911932944126139233276263L,
  0.9039892931234433315823215138173907234886L, -0.427555093430282094315230886905077056781L,
  0.336889853392220050702686798271834334173L, -0.9415440651830207783906830087961026265475L,
  0.9700312531945439926159106824865574481009L, -0.2429801799032638899447731489766866275204L,
  0.5141027441932217266072797923204262815489L, -0.8577286100002720698929313536407192941624L,
  0.8032075314806449097991200569701675249235L, -0.5956993044924333434615715265891822127742L,
  0.1467304744553617516588479505190711904561L, -0.9891765099647809734561415551112872890371L,
  0.9891765099647809734561415551112872890371L, -0.1467304744553617516588479505190711904561L,
  0.5956993044924333434615715265891822127742L, -0.8032075314806449097991200569701675249235L,
  0.8577286100002720698929313536407192941624L, -0.5141027441932217266072797923204262815489L,
  0.2429801799032638899447731489766866275204L, -0.9700312531945439926159106824865574481009L,
  0.9415440651830207783906830087961026265475L, -0.336889853392220050702686798271834334173L,
  0.427555093430282094315230886905077056781L, -0.9039892931234433315823215138173907234886L,
  0.7409511253549590911932944126139233276263L, -0.6715589548470184006194634573905233310143L,
  0.04906767432741801425355085940205324135377L, -0.9987954562051723927007702841240899260811L,
#endif
};

#ifndef ENABLE_STREAM
#error ENABLE_STREAM not defined
#endif

static const int constK[] = { 0, 2, 6, 14, 38, 94, 230, 542, 1254 };

extern const char *configStr[];

extern int planFilePathSet;

// Utility functions

#if defined(_MSC_VER) || defined(__MINGW32__) || defined(__MINGW64__)
static jmp_buf sigjmp;
#define SETJMP(x) setjmp(x)
#define LONGJMP longjmp
#else
static sigjmp_buf sigjmp;
#define SETJMP(x) sigsetjmp(x, 1)
#define LONGJMP siglongjmp
#endif

static void sighandler(int signum) { LONGJMP(sigjmp, 1); }

static int checkISAAvailability(int isa) {
  signal(SIGILL, sighandler);

  if (SETJMP(sigjmp) == 0) {
    int ret = GETINT[isa] != NULL && (*GETINT[isa])(BASETYPEID);
    signal(SIGILL, SIG_DFL);
    return ret;
  }

  signal(SIGILL, SIG_DFL);
  return 0;
}

#ifdef _OPENMP
static int omp_thread_count() {
  int n = 0;
#pragma omp parallel reduction(+:n)
  n += 1;
  return n;
}
#endif

static void startAllThreads(const int nth) {
#ifdef _OPENMP
  volatile int8_t *state = calloc(nth, 1);
  int th=0;
#pragma omp parallel for
  for(th=0;th<nth;th++) {
    state[th] = 1;
    for(;;) {
      int i;
      for(i=0;i<nth;i++) if (state[i] == 0) break;
      if (i == nth) break;
    }
  }
  free((void *)state);
#endif
}

// Dispatcher

static void dispatch(SleefDFT *p, const int N, real *d, const real *s, const int level, const int config) {
  const int K = constK[N], log2len = p->log2len;
  if (level == N) {
    if ((p->mode & SLEEF_MODE_BACKWARD) == 0) {
      void (*func)(real *, const real *, const int) = DFTF[config][p->isa][N];
      (*func)(d, s, log2len-N);
    } else {
      void (*func)(real *, const real *, const int) = DFTB[config][p->isa][N];
      (*func)(d, s, log2len-N);
    }
  } else if (level == log2len) {
    assert(p->vecwidth <= (1 << N));
    if ((p->mode & SLEEF_MODE_BACKWARD) == 0) {
      void (*func)(real *, uint32_t *, const real *, const int, const real *, const int) = TBUTF[config][p->isa][N];
      (*func)(d, p->perm[level], s, log2len-N, p->tbl[N][level], K);
    } else {
      void (*func)(real *, uint32_t *, const real *, const int, const real *, const int) = TBUTB[config][p->isa][N];
      (*func)(d, p->perm[level], s, log2len-N, p->tbl[N][level], K);
    }
  } else {
    if ((p->mode & SLEEF_MODE_BACKWARD) == 0) {
      void (*func)(real *, uint32_t *, const int, const real *, const int, const real *, const int) = BUTF[config][p->isa][N];
      (*func)(d, p->perm[level], log2len-level, s, log2len-N, p->tbl[N][level], K);
    } else {
      void (*func)(real *, uint32_t *, const int, const real *, const int, const real *, const int) = BUTB[config][p->isa][N];
      (*func)(d, p->perm[level], log2len-level, s, log2len-N, p->tbl[N][level], K);
    }
  }
}

// Transposer

#if defined(__GNUC__) && __GNUC__ < 5
// This is another workaround of a bug in gcc-4
#define LOG2BS 3
#else
#define LOG2BS 4
#endif

#define BS (1 << LOG2BS)
#define TRANSPOSE_BLOCK(y2) do {                                        \
    for(int x2=y2+1;x2<BS;x2++) {                                       \
      element_t r = *(element_t *)&row[y2].r[x2*2+0];                   \
      *(element_t *)&row[y2].r[x2*2+0] = *(element_t *)&row[x2].r[y2*2+0]; \
      *(element_t *)&row[x2].r[y2*2+0] = r;                             \
    }} while(0)

static void transpose(real *RESTRICT ALIGNED(256) d, real *RESTRICT ALIGNED(256) s, const int log2n, const int log2m) {
  if (log2n < LOG2BS || log2m < LOG2BS) {
    for(int y=0;y<(1 << log2n);y++) {
      for(int x=0;x<(1 << log2m);x++) {
        real r0 = s[((y << log2m)+x)*2+0];
        real r1 = s[((y << log2m)+x)*2+1];
        d[((x << log2n)+y)*2+0] = r0;
        d[((x << log2n)+y)*2+1] = r1;
      }
    }
  } else {
#if defined(__GNUC__) && !defined(__clang__)
    typedef struct { real __attribute__((vector_size(sizeof(real)*BS*2))) r; } row_t;
    typedef struct { real __attribute__((vector_size(sizeof(real)*2))) r; } element_t;
#else
    typedef struct { real r[BS*2]; } row_t;
    typedef struct { real r0, r1; } element_t;
#endif
    for(int y=0;y<(1 << log2n);y+=BS) {
      for(int x=0;x<(1 << log2m);x+=BS) {
        row_t row[BS];
        for(int y2=0;y2<BS;y2++) {
          row[y2] = *(row_t *)&s[(((y+y2) << log2m)+x)*2];
        }

#if LOG2BS == 4
        TRANSPOSE_BLOCK( 0); TRANSPOSE_BLOCK( 1);
        TRANSPOSE_BLOCK( 2); TRANSPOSE_BLOCK( 3);
        TRANSPOSE_BLOCK( 4); TRANSPOSE_BLOCK( 5);
        TRANSPOSE_BLOCK( 6); TRANSPOSE_BLOCK( 7);
        TRANSPOSE_BLOCK( 8); TRANSPOSE_BLOCK( 9);
        TRANSPOSE_BLOCK(10); TRANSPOSE_BLOCK(11);
        TRANSPOSE_BLOCK(12); TRANSPOSE_BLOCK(13);
        TRANSPOSE_BLOCK(14); TRANSPOSE_BLOCK(15);
#else
        for(int y2=0;y2<BS;y2++) {
          for(int x2=y2+1;x2<BS;x2++) {
            element_t r = *(element_t *)&row[y2].r[x2*2+0];
            *(element_t *)&row[y2].r[x2*2+0] = *(element_t *)&row[x2].r[y2*2+0];
            *(element_t *)&row[x2].r[y2*2+0] = r;
          }
        }
#endif
        for(int y2=0;y2<BS;y2++) {
          *(row_t *)&d[(((x+y2) << log2n)+y)*2] = row[y2];
        }
      }
    }
  }
}

#ifdef _OPENMP
static void transposeMT(real *RESTRICT ALIGNED(256) d, real *RESTRICT ALIGNED(256) s, int log2n, int log2m) {
  if (log2n < LOG2BS || log2m < LOG2BS) {
    for(int y=0;y<(1 << log2n);y++) {
      for(int x=0;x<(1 << log2m);x++) {
        real r0 = s[((y << log2m)+x)*2+0];
        real r1 = s[((y << log2m)+x)*2+1];
        d[((x << log2n)+y)*2+0] = r0;
        d[((x << log2n)+y)*2+1] = r1;
      }
    }
  } else {
#if defined(__GNUC__) && !defined(__clang__)
    typedef struct { real __attribute__((vector_size(sizeof(real)*BS*2))) r; } row_t;
    typedef struct { real __attribute__((vector_size(sizeof(real)*2))) r; } element_t;
#else
    typedef struct { real r[BS*2]; } row_t;
    typedef struct { real r0, r1; } element_t;
#endif
    int y=0;
#pragma omp parallel for
    for(y=0;y<(1 << log2n);y+=BS) {
      for(int x=0;x<(1 << log2m);x+=BS) {
        row_t row[BS];
        for(int y2=0;y2<BS;y2++) {
          row[y2] = *(row_t *)&s[(((y+y2) << log2m)+x)*2];
        }

#if LOG2BS == 4
        TRANSPOSE_BLOCK( 0); TRANSPOSE_BLOCK( 1);
        TRANSPOSE_BLOCK( 2); TRANSPOSE_BLOCK( 3);
        TRANSPOSE_BLOCK( 4); TRANSPOSE_BLOCK( 5);
        TRANSPOSE_BLOCK( 6); TRANSPOSE_BLOCK( 7);
        TRANSPOSE_BLOCK( 8); TRANSPOSE_BLOCK( 9);
        TRANSPOSE_BLOCK(10); TRANSPOSE_BLOCK(11);
        TRANSPOSE_BLOCK(12); TRANSPOSE_BLOCK(13);
        TRANSPOSE_BLOCK(14); TRANSPOSE_BLOCK(15);
#else
        for(int y2=0;y2<BS;y2++) {
          for(int x2=y2+1;x2<BS;x2++) {
            element_t r = *(element_t *)&row[y2].r[x2*2+0];
            *(element_t *)&row[y2].r[x2*2+0] = *(element_t *)&row[x2].r[y2*2+0];
            *(element_t *)&row[x2].r[y2*2+0] = r;
          }
        }
#endif
        for(int y2=0;y2<BS;y2++) {
          *(row_t *)&d[(((x+y2) << log2n)+y)*2] = row[y2];
        }
      }
    }
  }
}
#endif // #ifdef _OPENMP

// Table generator

static sc_t r2coefsc(int i, int log2len, int level) {
  return SINCOSPI((i & ((-1 << (log2len - level)) & ~(-1 << log2len))) * ((real)1.0/(1 << (log2len-1))));
}

static sc_t srcoefsc(int i, int log2len, int level) {
  return SINCOSPI(((3*(i & (-1 << (log2len - level)))) & ~(-1 << log2len)) * ((real)1.0/(1 << (log2len-1))));
}

static int makeTableRecurse(real *x, int *p, const int log2len, const int levelorg, const int levelinc, const int sign, const int top, const int bot, const int N, int cnt) {
  if (levelinc >= N-1) return cnt;
  const int level = levelorg - levelinc;
  if (bot - top > 4) {
    const int bl = 1 << (N - levelinc);
    const int w = bl/4;
    for(int j=0;j<(bot-top)/bl;j++) {
      for(int i=0;i<w;i++) {
        int a = sign*(p[(levelinc << N) + top+bl*j+i] & (-1 << (log2len - level)));
        sc_t sc;
        sc = r2coefsc(a, log2len, level);
        x[cnt++] = -sc.x; x[cnt++] = -sc.y;
        sc = srcoefsc(a, log2len, level);
        x[cnt++] = -sc.x; x[cnt++] = -sc.y;
      }
      cnt = makeTableRecurse(x, p, log2len, levelorg, levelinc+1, sign, top+bl*j       , top+bl*j + bl/2, N, cnt);
      cnt = makeTableRecurse(x, p, log2len, levelorg, levelinc+2, sign, top+bl*j + bl/2, top+bl*j + bl  , N, cnt);
    }
  } else if (bot - top == 4) {
    int a = sign*(p[(levelinc << N) + top] & (-1 << (log2len - level)));
    sc_t sc;
    sc = r2coefsc(a, log2len, level);
    x[cnt++] = -sc.x; x[cnt++] = -sc.y;
    sc = srcoefsc(a, log2len, level);
    x[cnt++] = -sc.x; x[cnt++] = -sc.y;
  }

  return cnt;
}

static uint32_t perm(int nbits, uint32_t k, int s, int d) {
  s = MIN(MAX(s, 0), nbits);
  d = MIN(MAX(d, 0), nbits);
  uint32_t r;
  r = (((k & 0xaaaaaaaa) >> 1) | ((k & 0x55555555) << 1));
  r = (((r & 0xcccccccc) >> 2) | ((r & 0x33333333) << 2));
  r = (((r & 0xf0f0f0f0) >> 4) | ((r & 0x0f0f0f0f) << 4));
  r = (((r & 0xff00ff00) >> 8) | ((r & 0x00ff00ff) << 8));
  r = ((r >> 16) | (r << 16)) >> (32-nbits);

  return (((r << s) | (k & ~(-1 << s))) & ~(-1 << d)) |
    ((((k >> s) | (r & (-1 << (nbits-s)))) << d) & ~(-1 << nbits));
}

static real **makeTable(int sign, int vecwidth, int log2len, const int N, const int K) {
  if (log2len < N) return NULL;

  int *p = (int *)malloc(sizeof(int)*((N+1)<<N));

  real **tbl = (real **)calloc(sizeof(real *), (log2len+1));

  for(int level=N;level<=log2len;level++) {
    if (level == log2len && (1 << (log2len-N)) < vecwidth) { tbl[level] = NULL; continue; }

    int tblOffset = 0;
    tbl[level] = (real *)Sleef_malloc(sizeof(real) * (K << (level-N)));

    for(int i0=0;i0 < (1 << (log2len-N));i0+=(1 << (log2len - level))) {
      for(int j=0;j<N+1;j++) {
        for(int i=0;i<(1 << N);i++) {
          p[(j << N) + i] = perm(log2len, i0 + (i << (log2len-N)), log2len-level, log2len-(level-j));
        }
      }

      int a = -sign*(p[((N-1) << N) + 0] & (-1 << (log2len - level)));
      sc_t sc = r2coefsc(a, log2len, level-N+1);
      tbl[level][tblOffset++] = sc.y; tbl[level][tblOffset++] = sc.x;

      tblOffset = makeTableRecurse(tbl[level], p, log2len, level, 0, sign, 0, 1 << N, N, tblOffset);
    }

    if (level == log2len) {
      real *atbl = (real *)Sleef_malloc(sizeof(real)*(K << (log2len-N))*2);
      tblOffset = 0;
      while(tblOffset < (K << (log2len-N))) {
        for(int k=0;k < K;k++) {
          for(int v = 0;v < vecwidth;v++) {
            assert((tblOffset + k * vecwidth + v)*2 + 1 < (K << (log2len-N))*2);
            atbl[(tblOffset + k * vecwidth + v)*2 + 0] = tbl[log2len][tblOffset + v * K + k];
            atbl[(tblOffset + k * vecwidth + v)*2 + 1] = tbl[log2len][tblOffset + v * K + k];
          }
        }
        tblOffset += K * vecwidth;
      }
      Sleef_free(tbl[log2len]);
      tbl[log2len] = atbl;
    }
  }

  free(p);

  return tbl;
}

// Random planner (for debugging)

static int searchForRandomPathRecurse(SleefDFT *p, int level, int *path, int *pathConfig, uint64_t tm, int nTrial) {
  if (level == 0) {
    p->bestTime = tm;
    for(uint32_t j = 0;j < p->log2len+1;j++) {
      p->bestPathConfig[j] = pathConfig[j];
      p->bestPath[j] = path[j];
    }
    return nTrial;
  }

  if (level < 1) return nTrial-1;

  for(int i=0;i<10;i++) {
    int N;

    do {
      N = 1 + rand() % MAXBUTWIDTH;
    } while(p->tm[0][level*(MAXBUTWIDTH+1)+N] >= 1ULL << 60);

    if (p->vecwidth > (1 << N) || N == p->log2len) continue;

    path[level] = N;
    for(;;) {
      pathConfig[level] = rand() % CONFIGMAX;
#if ENABLE_STREAM == 0
      pathConfig[level] &= ~1;
#endif
      if ((p->mode2 & SLEEF_MODE2_MT1D) == 0 && (pathConfig[level] & CONFIG_MT) != 0) continue;
      break;
    }
    for(int j = level-1;j >= 0;j--) path[j] = 0;
    nTrial = searchForRandomPathRecurse(p, level - N, path, pathConfig, 0, nTrial);
    if (nTrial <= 0) break;
    if (p->bestTime < 1ULL << 60) break;
  }

  return nTrial - 1;
}

// Planner

#define NSHORTESTPATHS 15
#define MAXPATHLEN (MAXLOG2LEN+1)
#define POSMAX (CONFIGMAX * MAXLOG2LEN * (MAXBUTWIDTH+1))

static int cln2pos(int config, int level, int N) { return (config * MAXLOG2LEN + level) * MAXBUTWIDTH + N; }
static int pos2config(int pos) { return pos == -1 ? -1 : ((pos - 1) / (MAXBUTWIDTH * MAXLOG2LEN)); }
static int pos2level(int pos) { return pos == -1 ? -1 : (((pos - 1) / MAXBUTWIDTH) % MAXLOG2LEN); }
static int pos2N(int pos) { return pos == -1 ? -1 : ((pos - 1) % MAXBUTWIDTH + 1); }

typedef struct {
  SleefDFT *p;

  int countu[POSMAX];
  int path[NSHORTESTPATHS][MAXPATHLEN];
  int pathLen[NSHORTESTPATHS];
  uint64_t cost[NSHORTESTPATHS];
  int nPaths;

  int *heap;
  int *heapLen;
  uint64_t *heapCost;
  int heapSize, nPathsInHeap;
} ks_t;

static ks_t *ksInit(SleefDFT *p) {
  ks_t *q = calloc(1, sizeof(ks_t));
  q->p = p;
  q->heapSize = 10;
  q->heap = calloc(q->heapSize, sizeof(int)*MAXPATHLEN);
  q->heapCost = calloc(q->heapSize, sizeof(uint64_t));
  q->heapLen = calloc(q->heapSize, sizeof(int));
  return q;
}

static void ksDispose(ks_t *q) {
  free(q->heapCost);
  free(q->heapLen);
  free(q->heap);
  free(q);
}

// returns the number of paths in the heap
static int ksSize(ks_t *q) { return q->nPathsInHeap; }

// adds a path to the heap
static void ksAddPath(ks_t *q, int *path, int pathLen, uint64_t cost) {
  assert(pathLen <= MAXPATHLEN);

  if (q->nPathsInHeap == q->heapSize) {
    q->heapSize *= 2;
    q->heap = realloc(q->heap, q->heapSize * sizeof(int)*MAXPATHLEN);
    q->heapCost = realloc(q->heapCost, q->heapSize * sizeof(uint64_t));
    q->heapLen = realloc(q->heapLen, q->heapSize * sizeof(int));
  }

  for(int i=0;i<pathLen;i++) q->heap[q->nPathsInHeap * MAXPATHLEN + i] = path[i];
  q->heapLen[q->nPathsInHeap] = pathLen;
  q->heapCost[q->nPathsInHeap] = cost;
  q->nPathsInHeap++;
}

// returns the cost of n-th paths in the heap
static uint64_t ksCost(ks_t *q, int n) {
  assert(0 <= n && n < q->nPathsInHeap);
  return q->heapCost[n];
}

// copies the n-th paths in the heap to path, returns its length
static int ksGetPath(ks_t *q, int *path, int n) {
  assert(0 <= n && n < q->nPathsInHeap);
  int len = q->heapLen[n];
  for(int i=0;i<len;i++) path[i] = q->heap[n * MAXPATHLEN + i];
  return len;
}

// removes the n-th paths in the heap
static void ksRemove(ks_t *q, int n) {
  assert(0 <= n && n < q->nPathsInHeap);

  for(int i=n;i<q->nPathsInHeap-1;i++) {
    int len = q->heapLen[i+1];
    assert(len < MAXPATHLEN);
    for(int j=0;j<len;j++) q->heap[i * MAXPATHLEN + j] = q->heap[(i+1) * MAXPATHLEN + j];
    q->heapLen[i] = q->heapLen[i+1];
    q->heapCost[i] = q->heapCost[i+1];
  }
  q->nPathsInHeap--;
}

// returns the countu value at pos
static int ksCountu(ks_t *q, int pos) {
  assert(0 <= pos && pos < POSMAX);
  return q->countu[pos];
}

// set the countu value at pos to n
static void ksSetCountu(ks_t *q, int pos, int n) {
  assert(0 <= pos && pos < POSMAX);
  q->countu[pos] = n;
}

// adds a path as one of the best k paths, returns the number best paths
static int ksAddBestPath(ks_t *q, int *path, int pathLen, uint64_t cost) {
  assert(pathLen <= MAXPATHLEN);
  assert(q->nPaths < NSHORTESTPATHS);
  for(int i=0;i<pathLen;i++) q->path[q->nPaths][i] = path[i];
  q->pathLen[q->nPaths] = pathLen;
  q->cost[q->nPaths] = cost;
  q->nPaths++;
  return q->nPaths;
}

// returns if pos is a destination
static int ksIsDest(ks_t *q, int pos) { return pos2level(pos) == 0; }

// returns n-th adjacent nodes at pos.
static int ksAdjacent(ks_t *q, int pos, int n) {
  if (pos != -1 && pos2level(pos) == 0) return -1;

  int NMAX = MIN(MIN(q->p->log2len, MAXBUTWIDTH+1), q->p->log2len - q->p->log2vecwidth + 1);

  if (pos == -1) {
    int N = n / 2 + MAX(q->p->log2vecwidth, 1);
    if (N >= NMAX) return -1;
    return cln2pos((n & 1) * CONFIG_MT, q->p->log2len, N);
  }

  int config = (pos2config(pos) & CONFIG_MT);
  int N = n + 1;
  int level = pos2level(pos) - pos2N(pos);

  if (level < 0 || N >= NMAX) return -1;
  if (level == 0) return n == 0 ? cln2pos(0, 0, 0) : -1;

  return cln2pos(config, level, N);
}

static uint64_t ksAdjacentCost(ks_t *q, int pos, int n) {
  int nxpos = ksAdjacent(q, pos, n);
  if (nxpos == -1) return 0;
  int config = pos2config(nxpos), level = pos2level(nxpos), N = pos2N(nxpos);
  uint64_t ret0 = q->p->tm[config | 0][level*(MAXBUTWIDTH+1) + N];
  uint64_t ret1 = q->p->tm[config | 1][level*(MAXBUTWIDTH+1) + N];
  return MIN(ret0, ret1);
}

static void searchForBestPath(SleefDFT *p) {
  ks_t *q = ksInit(p);

  for(int i=0;;i++) {
    int v = ksAdjacent(q, -1, i);
    if (v == -1) break;
    uint64_t c = ksAdjacentCost(q, -1, i);
    int path[1] = { v };
    ksAddPath(q, path, 1, c);
  }

  while(ksSize(q) != 0) {
    uint64_t bestCost = 1ULL << 60;
    int bestPathNum = -1;

    for(int i=0;i<ksSize(q);i++) {
      if (ksCost(q, i) < bestCost) {
        bestCost = ksCost(q, i);
        bestPathNum = i;
      }
    }
    if (bestPathNum == -1) break;

    int path[MAXPATHLEN];
    int pathLen = ksGetPath(q, path, bestPathNum);
    uint64_t cost = ksCost(q, bestPathNum);
    ksRemove(q, bestPathNum);

    int lastPos = path[pathLen-1];
    if (ksCountu(q, lastPos) >= NSHORTESTPATHS) continue;
    ksSetCountu(q, lastPos, ksCountu(q, lastPos)+1);

    if (ksIsDest(q, lastPos)) {
      if (ksAddBestPath(q, path, pathLen, cost) >= NSHORTESTPATHS) break;
      continue;
    }

    for(int i=0;;i++) {
      int v = ksAdjacent(q, lastPos, i);
      if (v == -1) break;
      assert(0 <= pos2N(v) && pos2N(v) <= q->p->log2len);
      uint64_t c = ksAdjacentCost(q, lastPos, i);
      path[pathLen] = v;
      ksAddPath(q, path, pathLen+1, cost + c);
    }
  }

  for(int j = p->log2len;j >= 0;j--) p->bestPath[j] = 0;

  if (((p->mode & SLEEF_MODE_MEASURE) != 0 || (planFilePathSet && (p->mode & SLEEF_MODE_MEASUREBITS) == 0))) {
    uint64_t besttm = 1ULL << 62;
    int bestPath = -1;
    const int niter =  1 + 5000000 / ((1 << p->log2len) + 1);

    real *s2 = NULL, *d2 = NULL;
    const real *s = p->in  == NULL ? (s2 = (real *)memset(Sleef_malloc((2 << p->log2len) * sizeof(real)), 0, sizeof(real) * (2 << p->log2len))) : p->in;
    real       *d = p->out == NULL ? (d2 = (real *)memset(Sleef_malloc((2 << p->log2len) * sizeof(real)), 0, sizeof(real) * (2 << p->log2len))) : p->out;

#ifdef _OPENMP
    const int tn = omp_get_thread_num();
#else
    const int tn = 0;
#endif

    real *t[] = { p->x1[tn], p->x0[tn], d };

    for(int mt=0;mt<2;mt++) {
      for(int i=q->nPaths-1;i>=0;i--) {
        if (((pos2config(q->path[i][0]) & CONFIG_MT) != 0) != mt) continue;

        if ((p->mode & SLEEF_MODE_VERBOSE) != 0) {
          for(int j=0;j<q->pathLen[i];j++) {
            int N = pos2N(q->path[i][j]);
            int level = pos2level(q->path[i][j]);
            int config = pos2config(q->path[i][j]) & ~1;
            uint64_t t0 = q->p->tm[config | 0][level*(MAXBUTWIDTH+1) + N];
            uint64_t t1 = q->p->tm[config | 1][level*(MAXBUTWIDTH+1) + N];
            config = t0 < t1 ? config : (config | 1);

            if (N != 0) printf("%d(%s) ", N, configStr[config]);
          }
        }

        if (mt) startAllThreads(p->nThread);

        uint64_t tm0 = Sleef_currentTimeMicros();
        for(int k=0;k<niter;k++) {
          int nb = 0;
          const real *lb = s;
          if ((p->pathLen & 1) == 1) nb = -1;
          for(int level = p->log2len, j=0;level >= 1;j++) {
            assert(pos2level(q->path[i][j]) == level);
            int N = pos2N(q->path[i][j]);
            int config = pos2config(q->path[i][j]) & ~1;
            uint64_t t0 = q->p->tm[config | 0][level*(MAXBUTWIDTH+1) + N];
            uint64_t t1 = q->p->tm[config | 1][level*(MAXBUTWIDTH+1) + N];
            config = t0 < t1 ? config : (config | 1);
            dispatch(p, N, t[nb+1], lb, level, config);
            level -= N;
            lb = t[nb+1];
            nb = (nb + 1) & 1;
          }
        }
        uint64_t tm1 = Sleef_currentTimeMicros();
        for(int k=0;k<niter;k++) {
          int nb = 0;
          const real *lb = s;
          if ((p->pathLen & 1) == 1) nb = -1;
          for(int level = p->log2len, j=0;level >= 1;j++) {
            assert(pos2level(q->path[i][j]) == level);
            int N = pos2N(q->path[i][j]);
            int config = pos2config(q->path[i][j]) & ~1;
            uint64_t t0 = q->p->tm[config | 0][level*(MAXBUTWIDTH+1) + N];
            uint64_t t1 = q->p->tm[config | 1][level*(MAXBUTWIDTH+1) + N];
            config = t0 < t1 ? config : (config | 1);
            dispatch(p, N, t[nb+1], lb, level, config);
            level -= N;
            lb = t[nb+1];
            nb = (nb + 1) & 1;
          }
        }
        uint64_t tm2 = Sleef_currentTimeMicros();

        if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf(" : %lld %lld\n", (long long int)(tm1 - tm0), (long long int)(tm2 - tm1));
        if ((tm1 - tm0) < besttm) {
          bestPath = i;
          besttm = tm1 - tm0;
        }
        if ((tm2 - tm1) < besttm) {
          bestPath = i;
          besttm = tm2 - tm1;
        }
      }
    }

    for(int level = p->log2len, j=0;level >= 1;j++) {
      assert(pos2level(q->path[bestPath][j]) == level);
      int N = pos2N(q->path[bestPath][j]);

      int config = pos2config(q->path[bestPath][j]) & ~1;
      uint64_t t0 = q->p->tm[config | 0][level*(MAXBUTWIDTH+1) + N];
      uint64_t t1 = q->p->tm[config | 1][level*(MAXBUTWIDTH+1) + N];
      config = t0 < t1 ? config : (config | 1);

      p->bestPath[level] = N;
      p->bestPathConfig[level] = config;
      level -= N;
    }

    if (d2 != NULL) Sleef_free(d2);
    if (s2 != NULL) Sleef_free(s2);
  } else {
    for(int level = p->log2len, j=0;level >= 1;j++) {
      int bestPath = 0;
      assert(pos2level(q->path[bestPath][j]) == level);
      int N = pos2N(q->path[bestPath][j]);
      int config = pos2config(q->path[bestPath][j]);
      p->bestPath[level] = N;
      p->bestPathConfig[level] = config;
      level -= N;
    }
  }

  ksDispose(q);
}

//

static uint64_t estimate(int log2len, int level, int N, int config) {
  uint64_t ret = N * 1000 + ABS(N-3) * 1000;
  if (log2len >= 14 && (config & CONFIG_MT) != 0) ret /= 2;
  return ret;
}

static void measureBut(SleefDFT *p) {
  if (p->x0 == NULL) return;

  //

#ifdef _OPENMP
  const int tn = omp_get_thread_num();
#else
  const int tn = 0;
#endif

  real *s = (real *)memset(p->x0[tn], 0, sizeof(real) * (2 << p->log2len));
  real *d = (real *)memset(p->x1[tn], 0, sizeof(real) * (2 << p->log2len));

  const int niter =  1 + 100000 / ((1 << p->log2len) + 1);

#define MEASURE_REPEAT 4

  for(int rep=1;rep<=MEASURE_REPEAT;rep++) {
    for(int config=0;config<CONFIGMAX;config++) {
#if ENABLE_STREAM == 0
      if ((config & 1) != 0) continue;
#endif
      if ((p->mode2 & SLEEF_MODE2_MT1D) == 0 && (config & CONFIG_MT) != 0) continue;
      for(uint32_t level = p->log2len;level >= 1;level--) {
        for(uint32_t N=1;N<=MAXBUTWIDTH;N++) {
          if (level < N || p->log2len <= N) continue;
          if (level == N) {
            if ((int)p->log2len - (int)level < p->log2vecwidth) continue;

            uint64_t tm = Sleef_currentTimeMicros();
            for(int i=0;i<niter*2;i++) {
              dispatch(p, N, d, s, level, config);
            }
            tm = Sleef_currentTimeMicros() - tm + 1;
            p->tm[config][level*(MAXBUTWIDTH+1)+N] = MIN(p->tm[config][level*(MAXBUTWIDTH+1)+N], tm);
          } else if (level == p->log2len) {
            if (p->tbl[N] == NULL || p->tbl[N][level] == NULL) continue;
            if (p->vecwidth > (1 << N)) continue;
            if ((config & CONFIG_MT) != 0) {
              int i1=0;
#ifdef _OPENMP
#pragma omp parallel for
#endif
              for(i1=0;i1 < (1 << (p->log2len-N-p->log2vecwidth));i1++) {
                int i0 = i1 << p->log2vecwidth;
                p->perm[level][i1] = 2*perm(p->log2len, i0, p->log2len-level, p->log2len-(level-N));
              }
            } else {
              for(int i0=0, i1=0;i0 < (1 << (p->log2len-N));i0+=p->vecwidth, i1++) {
                p->perm[level][i1] = 2*perm(p->log2len, i0, p->log2len-level, p->log2len-(level-N));
              }
            }

            uint64_t tm = Sleef_currentTimeMicros();
            for(int i=0;i<niter;i++) {
              dispatch(p, N, d, s, level, config);
              dispatch(p, N, s, d, level, config);
            }
            tm = Sleef_currentTimeMicros() - tm + 1;
            p->tm[config][level*(MAXBUTWIDTH+1)+N] = MIN(p->tm[config][level*(MAXBUTWIDTH+1)+N], tm);
          } else {
            if (p->tbl[N] == NULL || p->tbl[N][level] == NULL) continue;
            if (p->vecwidth > 2 && p->log2len <= N+2) continue;
            if ((int)p->log2len - (int)level < p->log2vecwidth) continue;
            if ((config & CONFIG_MT) != 0) {
              int i1=0;
#ifdef _OPENMP
#pragma omp parallel for
#endif
              for(i1=0;i1 < (1 << (p->log2len-N-p->log2vecwidth));i1++) {
                int i0 = i1 << p->log2vecwidth;
                p->perm[level][i1] = 2*perm(p->log2len, i0, p->log2len-level, p->log2len-(level-N));
              }
            } else {
              for(int i0=0, i1=0;i0 < (1 << (p->log2len-N));i0+=p->vecwidth, i1++) {
                p->perm[level][i1] = 2*perm(p->log2len, i0, p->log2len-level, p->log2len-(level-N));
              }
            }

            uint64_t tm = Sleef_currentTimeMicros();
            for(int i=0;i<niter;i++) {
              dispatch(p, N, d, s, level, config);
              dispatch(p, N, s, d, level, config);
            }
            tm = Sleef_currentTimeMicros() - tm + 1;
            p->tm[config][level*(MAXBUTWIDTH+1)+N] = MIN(p->tm[config][level*(MAXBUTWIDTH+1)+N], tm);
          }
        }
      }
    }
  }

  if ((p->mode & SLEEF_MODE_VERBOSE) != 0) {
    for(uint32_t level = p->log2len;level >= 1;level--) {
      for(uint32_t N=1;N<=MAXBUTWIDTH;N++) {
        if (level < N || p->log2len <= N) continue;
        if (level == N) {
          if ((int)p->log2len - (int)level < p->log2vecwidth) continue;
          printf("bot %d, %d, %d, ", p->log2len, level, N);
          for(int config=0;config<CONFIGMAX;config++) {
            if (p->tm[config][level*(MAXBUTWIDTH+1)+N] == 1ULL << 60) {
              printf("N/A, ");
            } else {
              printf("%lld, ", (long long int)p->tm[config][level*(MAXBUTWIDTH+1)+N]);
            }
          }
          printf("\n");
        } else if (level == p->log2len) {
          if (p->tbl[N] == NULL || p->tbl[N][level] == NULL) continue;
          if (p->vecwidth > (1 << N)) continue;
          printf("top %d, %d, %d, ", p->log2len, level, N);
          for(int config=0;config<CONFIGMAX;config++) {
            if (p->tm[config][level*(MAXBUTWIDTH+1)+N] == 1ULL << 60) {
              printf("N/A, ");
            } else {
              printf("%lld, ", (long long int)p->tm[config][level*(MAXBUTWIDTH+1)+N]);
            }
          }
          printf("\n");
        } else {
          if (p->tbl[N] == NULL || p->tbl[N][level] == NULL) continue;
          if (p->vecwidth > 2 && p->log2len <= N+2) continue;
          if ((int)p->log2len - (int)level < p->log2vecwidth) continue;
          printf("mid %d, %d, %d, ", p->log2len, level, N);
          for(int config=0;config<CONFIGMAX;config++) {
            if (p->tm[config][level*(MAXBUTWIDTH+1)+N] == 1ULL << 60) {
              printf("N/A, ");
            } else {
              printf("%lld, ", (long long int)p->tm[config][level*(MAXBUTWIDTH+1)+N]);
            }
          }
          printf("\n");
        }
      }
    }
  }
}

static void estimateBut(SleefDFT *p) {
  for(uint32_t level = p->log2len;level >= 1;level--) {
    for(uint32_t N=1;N<=MAXBUTWIDTH;N++) {
      if (level < N || p->log2len <= N) continue;
      if (level == N) {
        if ((int)p->log2len - (int)level < p->log2vecwidth) continue;
        for(int config=0;config<CONFIGMAX;config++) {
#if ENABLE_STREAM == 0
          if ((config & 1) != 0) continue;
#endif
          p->tm[config][level*(MAXBUTWIDTH+1)+N] = estimate(p->log2len, level, N, config);
        }
      } else if (level == p->log2len) {
        if (p->tbl[N] == NULL || p->tbl[N][level] == NULL) continue;
        if (p->vecwidth > (1 << N)) continue;
        for(int config=0;config<CONFIGMAX;config++) {
#if ENABLE_STREAM == 0
          if ((config & 1) != 0) continue;
#endif
          p->tm[config][level*(MAXBUTWIDTH+1)+N] = estimate(p->log2len, level, N, config);
        }
      } else {
        if (p->tbl[N] == NULL || p->tbl[N][level] == NULL) continue;
        if (p->vecwidth > 2 && p->log2len <= N+2) continue;
        if ((int)p->log2len - (int)level < p->log2vecwidth) continue;
        for(int config=0;config<CONFIGMAX;config++) {
#if ENABLE_STREAM == 0
          if ((config & 1) != 0) continue;
#endif
          p->tm[config][level*(MAXBUTWIDTH+1)+N] = estimate(p->log2len, level, N, config);
        }
      }
    }
  }
}

static int measure(SleefDFT *p, int randomize) {
  if (p->log2len == 1) {
    p->bestTime = 1ULL << 60;

    p->pathLen = 1;
    p->bestPath[1] = 1;

    return 1;
  }

  if (PlanManager_loadMeasurementResultsP(p, (p->mode & SLEEF_MODE_NO_MT) != 0 ? 1 : 0)) {
    if ((p->mode & SLEEF_MODE_VERBOSE) != 0) {
      printf("Path(loaded) : ");
      for(int j = p->log2len;j >= 0;j--) if (p->bestPath[j] != 0) printf("%d(%s) ", p->bestPath[j], configStr[p->bestPathConfig[j]]);
      printf("\n");
    }

    return 1;
  }

  int toBeSaved = 0;

  for(uint32_t level = p->log2len;level >= 1;level--) {
    for(uint32_t N=1;N<=MAXBUTWIDTH;N++) {
      for(int config=0;config<CONFIGMAX;config++) {
        p->tm[config][level*(MAXBUTWIDTH+1)+N] = 1ULL << 60;
      }
    }
  }

  if (((p->mode & SLEEF_MODE_MEASURE) != 0 || (planFilePathSet && (p->mode & SLEEF_MODE_MEASUREBITS) == 0)) && !randomize) {
    measureBut(p);
    toBeSaved = 1;
  } else {
    estimateBut(p);
  }

  int executable = 0;
  for(int i=1;i<=MAXBUTWIDTH && !executable;i++) {
    if (p->tm[0][p->log2len*(MAXBUTWIDTH+1)+i] < (1ULL << 60)) executable = 1;
  }

  if (!executable) return 0;

  p->bestTime = 1ULL << 60;

  p->bestPath[p->log2len] = 0;

  if (!randomize) {
    searchForBestPath(p);
  } else {
    int path[MAXLOG2LEN+1];
    int pathConfig[MAXLOG2LEN+1];
    for(int j = p->log2len;j >= 0;j--) path[j] = pathConfig[j] = 0;

    int nTrial = 100000;
    do {
      nTrial = searchForRandomPathRecurse(p, p->log2len, path, pathConfig, 0, nTrial);
    } while(p->bestTime == 1ULL << 60 && nTrial >= 0);
  }

  if (p->bestPath[p->log2len] == 0) return 0;

  p->pathLen = 0;
  for(int j = p->log2len;j >= 0;j--) if (p->bestPath[j] != 0) p->pathLen++;

  if ((p->mode & SLEEF_MODE_VERBOSE) != 0) {
    printf("Path");
    if (randomize) printf("(random) :");
    else if (toBeSaved) printf("(measured) :");
    else printf("(estimated) :");

    for(int j = p->log2len;j >= 0;j--) if (p->bestPath[j] != 0) printf("%d(%s) ", p->bestPath[j], configStr[p->bestPathConfig[j]]);
    printf("\n");
  }

  if (toBeSaved) {
    PlanManager_saveMeasurementResultsP(p, (p->mode & SLEEF_MODE_NO_MT) != 0 ? 1 : 0);
  }

  return 1;
}

static void measureTranspose(SleefDFT *p) {
  if (PlanManager_loadMeasurementResultsT(p)) {
    if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("transpose NoMT(loaded): %lld\n", (long long int)p->tmNoMT);
    if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("transpose   MT(loaded): %lld\n", (long long int)p->tmMT);
    return;
  }

  if ((p->mode & SLEEF_MODE_MEASURE) == 0 && (!planFilePathSet || (p->mode & SLEEF_MODE_MEASUREBITS) != 0)) {
    if (p->log2hlen + p->log2vlen >= 14) {
      p->tmNoMT = 20;
      p->tmMT = 10;
      if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("transpose : selected MT(estimated)\n");
    } else {
      p->tmNoMT = 10;
      p->tmMT = 20;
      if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("transpose : selected NoMT(estimated)\n");
    }
    return;
  }

  real *tBuf2 = (real *)Sleef_malloc(sizeof(real)*2*p->hlen*p->vlen);

  const int niter =  1 + 5000000 / (p->hlen * p->vlen + 1);
  uint64_t tm;

  tm = Sleef_currentTimeMicros();
  for(int i=0;i<niter;i++) {
    transpose(tBuf2, p->tBuf, p->log2hlen, p->log2vlen);
    transpose(tBuf2, p->tBuf, p->log2vlen, p->log2hlen);
  }
  p->tmNoMT = Sleef_currentTimeMicros() - tm + 1;

  if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("transpose NoMT(measured): %lld\n", (long long int)p->tmNoMT);

#ifdef _OPENMP
  tm = Sleef_currentTimeMicros();
  for(int i=0;i<niter;i++) {
    transposeMT(tBuf2, p->tBuf, p->log2hlen, p->log2vlen);
    transposeMT(tBuf2, p->tBuf, p->log2vlen, p->log2hlen);
  }
  p->tmMT = Sleef_currentTimeMicros() - tm + 1;

  if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("transpose   MT(measured): %lld\n", (long long int)p->tmMT);
#else
  p->tmMT = p->tmNoMT*2;
#endif

  Sleef_free(tBuf2);

  PlanManager_saveMeasurementResultsT(p);
}

// Implementation of SleefDFT_*_init1d

EXPORT SleefDFT *INIT(uint32_t n, const real *in, real *out, uint64_t mode) {
  SleefDFT *p = (SleefDFT *)calloc(1, sizeof(SleefDFT));
  p->magic = MAGIC;
  p->baseTypeID = BASETYPEID;
  p->in = (const void *)in;
  p->out = (void *)out;

  // Mode

  p->mode = mode;

  if ((p->mode & SLEEF_MODE_NO_MT) == 0) {
    p->mode2 |= SLEEF_MODE2_MT1D;
  }

  if ((mode & SLEEF_MODE_REAL) != 0) n /= 2;
  p->log2len = ilog2(n);

  if (p->log2len <= 1) return p;

  if ((mode & SLEEF_MODE_ALT) != 0) p->mode = mode = mode ^ SLEEF_MODE_BACKWARD;

#ifdef _OPENMP
  p->nThread = omp_thread_count();
#else
  p->nThread = 1;
  p->mode2 &= ~SLEEF_MODE2_MT1D;
#endif

  // ISA availability

  int bestPriority = -1;
  p->isa = -1;

  for(int i=0;i<ISAMAX;i++) {
    if (checkISAAvailability(i) && bestPriority < (*GETINT[i])(GETINT_DFTPRIORITY) && n >= (uint32_t)((*GETINT[i])(GETINT_VECWIDTH) * (*GETINT[i])(GETINT_VECWIDTH))) {
      bestPriority = (*GETINT[i])(GETINT_DFTPRIORITY);
      p->isa = i;
    }
  }

  if (p->isa == -1) {
    if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("ISA not available\n");
    p->magic = 0;
    free(p);
    return NULL;
  }

  // Tables

  p->perm = (uint32_t **)calloc(sizeof(uint32_t *), p->log2len+1);
  for(int level = p->log2len;level >= 1;level--) {
    p->perm[level] = (uint32_t *)Sleef_malloc(sizeof(uint32_t) * ((1 << p->log2len) + 8));
  }

  p->x0 = malloc(sizeof(real *) * p->nThread);
  p->x1 = malloc(sizeof(real *) * p->nThread);

  for(int i=0;i<p->nThread;i++) {
    p->x0[i] = (real *)Sleef_malloc(sizeof(real) * 2 * n);
    p->x1[i] = (real *)Sleef_malloc(sizeof(real) * 2 * n);
  }

  if ((mode & SLEEF_MODE_REAL) != 0) {
    p->rtCoef0 = (real *)Sleef_malloc(sizeof(real) * n);
    p->rtCoef1 = (real *)Sleef_malloc(sizeof(real) * n);

    if ((mode & SLEEF_MODE_BACKWARD) == 0) {
      for(uint32_t i=0;i<n/2;i++) {
        sc_t sc = SINCOSPI(i*((real)-1.0/n));
        ((real *)p->rtCoef0)[i*2+0] = ((real *)p->rtCoef0)[i*2+1] = (real)0.5 - (real)0.5 * sc.x;
        ((real *)p->rtCoef1)[i*2+0] = ((real *)p->rtCoef1)[i*2+1] = (real)0.5*sc.y;
      }
    } else {
      for(uint32_t i=0;i<n/2;i++) {
        sc_t sc = SINCOSPI(i*((real)-1.0/n));
        ((real *)p->rtCoef0)[i*2+0] = ((real *)p->rtCoef0)[i*2+1] = (real)0.5 + (real)0.5 * sc.x;
        ((real *)p->rtCoef1)[i*2+0] = ((real *)p->rtCoef1)[i*2+1] = (real)0.5*sc.y;
      }
    }
  }

  // Measure

  int sign = (mode & SLEEF_MODE_BACKWARD) != 0 ? -1 : 1;

  p->vecwidth = (*GETINT[p->isa])(GETINT_VECWIDTH);
  p->log2vecwidth = ilog2(p->vecwidth);

  for(int i=1;i<=MAXBUTWIDTH;i++) {
    ((real ***)p->tbl)[i] = makeTable(sign, p->vecwidth, p->log2len, i, constK[i]);
  }

  if (!measure(p, (mode & SLEEF_MODE_DEBUG))) {
    // Fall back to the first ISA
    freeTables(p);
    p->isa = 0;

    p->vecwidth = (*GETINT[p->isa])(GETINT_VECWIDTH);
    p->log2vecwidth = ilog2(p->vecwidth);

    for(int i=1;i<=MAXBUTWIDTH;i++) {
      ((real ***)p->tbl)[i] = makeTable(sign, p->vecwidth, p->log2len, i, constK[i]);
    }

    for(int level = p->log2len;level >= 1;) {
      int N = ABS(p->bestPath[level]);
      if (level == N) { level -= N; continue; }

      int i1 = 0;
      for(int i0=0;i0 < (1 << (p->log2len-N));i0+=p->vecwidth, i1++) {
        p->perm[level][i1] = 2*perm(p->log2len, i0, p->log2len-level, p->log2len-(level-N));
      }
      for(;i1 < (1 << p->log2len) + 8;i1++) p->perm[level][i1] = 0;

      level -= N;
    }

    if (!measure(p, (mode & SLEEF_MODE_DEBUG))) {
      if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("Suitable ISA not found. This should not happen.\n");
      return NULL;
    }
  }

  for(int level = p->log2len;level >= 1;) {
    int N = ABS(p->bestPath[level]);
    if (level == N) { level -= N; continue; }

    int i1 = 0;
    for(int i0=0;i0 < (1 << (p->log2len-N));i0+=p->vecwidth, i1++) {
      p->perm[level][i1] = 2*perm(p->log2len, i0, p->log2len-level, p->log2len-(level-N));
    }
    for(;i1 < (1 << p->log2len) + 8;i1++) p->perm[level][i1] = 0;

    level -= N;
  }

  if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("ISA : %s %d bit %s\n", (char *)(*GETPTR[p->isa])(0), (int)(GETINT[p->isa](GETINT_VECWIDTH) * sizeof(real) * 16), BASETYPESTRING);

  return p;
}

// Implementation of SleefDFT_*_init2d

EXPORT SleefDFT *INIT2D(uint32_t vlen, uint32_t hlen, const real *in, real *out, uint64_t mode) {
  SleefDFT *p = (SleefDFT *)calloc(1, sizeof(SleefDFT));
  p->magic = MAGIC2D;
  p->mode = mode;
  p->baseTypeID = BASETYPEID;
  p->in = in;
  p->out = out;
  p->hlen = hlen;
  p->log2hlen = ilog2(hlen);
  p->vlen = vlen;
  p->log2vlen = ilog2(vlen);

  uint64_t mode1D = mode;
  mode1D |= SLEEF_MODE_NO_MT;

  if ((mode & SLEEF_MODE_NO_MT) == 0) p->mode3 |= SLEEF_MODE3_MT2D;

  p->instH = p->instV = INIT(hlen, NULL, NULL, mode1D);
  if (hlen != vlen) p->instV = INIT(vlen, NULL, NULL, mode1D);

  p->tBuf = (void *)Sleef_malloc(sizeof(real)*2*hlen*vlen);

  measureTranspose(p);

  return p;
}

// Implementation of SleefDFT_*_execute

EXPORT void EXECUTE(SleefDFT *p, const real *s0, real *d0) {
  assert(p != NULL && (p->magic == MAGIC || p->magic == MAGIC2D));

  const real *s = s0 == NULL ? p->in : s0;
  real *d = d0 == NULL ? p->out : d0;

  if (p->magic == MAGIC2D) {
  // S -> T -> D -> T -> D

    real *tBuf = (real *)(p->tBuf);

#ifdef _OPENMP
    if ((p->mode3 & SLEEF_MODE3_MT2D) != 0 &&
        (((p->mode & SLEEF_MODE_DEBUG) == 0 && p->tmMT < p->tmNoMT) ||
         ((p->mode & SLEEF_MODE_DEBUG) != 0 && (rand() & 1))))
      {
        int y=0;
#pragma omp parallel for
        for(y=0;y<p->vlen;y++) {
          EXECUTE(p->instH, &s[p->hlen*2*y], &tBuf[p->hlen*2*y]);
        }

        transposeMT(d, tBuf, p->log2vlen, p->log2hlen);

#pragma omp parallel for
        for(y=0;y<p->hlen;y++) {
          EXECUTE(p->instV, &d[p->vlen*2*y], &tBuf[p->vlen*2*y]);
        }

        transposeMT(d, tBuf, p->log2hlen, p->log2vlen);
      } else
#endif
      {
        for(int y=0;y<p->vlen;y++) {
          EXECUTE(p->instH, &s[p->hlen*2*y], &tBuf[p->hlen*2*y]);
        }

        transpose(d, tBuf, p->log2vlen, p->log2hlen);

        for(int y=0;y<p->hlen;y++) {
          EXECUTE(p->instV, &d[p->vlen*2*y], &tBuf[p->vlen*2*y]);
        }

        transpose(d, tBuf, p->log2hlen, p->log2vlen);
      }

    return;
  }

  if (p->log2len <= 1) {
    if ((p->mode & SLEEF_MODE_REAL) == 0) {
      real r0 = s[0] + s[2];
      real r1 = s[1] + s[3];
      real r2 = s[0] - s[2];
      real r3 = s[1] - s[3];
      d[0] = r0; d[1] = r1; d[2] = r2; d[3] = r3;
    } else {
      if ((p->mode & SLEEF_MODE_ALT) == 0) {
        if (p->log2len == 1) {
          if ((p->mode & SLEEF_MODE_BACKWARD) == 0) {
            real r0 = s[0] + s[2] + (s[1] + s[3]);
            real r1 = s[0] + s[2] - (s[1] + s[3]);
            real r2 = s[0] - s[2];
            real r3 = s[3] - s[1];
            d[0] = r0; d[1] = 0; d[2] = r2; d[3] = r3; d[4] = r1; d[5] = 0;
          } else {
            real r0 = (s[0] + s[4])*(real)0.5 + s[2];
            real r1 = (s[0] - s[4])*(real)0.5 - s[3];
            real r2 = (s[0] + s[4])*(real)0.5 - s[2];
            real r3 = (s[0] - s[4])*(real)0.5 + s[3];
            d[0] = r0*2; d[1] = r1*2; d[2] = r2*2; d[3] = r3*2;
          }
        } else {
          if ((p->mode & SLEEF_MODE_BACKWARD) == 0) {
            real r0 = s[0] + s[1];
            real r1 = s[0] - s[1];
            d[0] = r0; d[1] = 0; d[2] = r1; d[3] = 0;
          } else {
            real r0 = s[0] + s[2];
            real r1 = s[0] - s[2];
            d[0] = r0; d[1] = r1;
          }
        }
      } else {
        if (p->log2len == 1) {
          if ((p->mode & SLEEF_MODE_BACKWARD) == 0) {
            real r0 = s[0] + s[2] + (s[1] + s[3]);
            real r1 = s[0] + s[2] - (s[1] + s[3]);
            real r2 = s[0] - s[2];
            real r3 = s[1] - s[3];
            d[0] = r0; d[1] = r1; d[2] = r2; d[3] = r3;
          } else {
            real r0 = (s[0] + s[1])*(real)0.5 + s[2];
            real r1 = (s[0] - s[1])*(real)0.5 + s[3];
            real r2 = (s[0] + s[1])*(real)0.5 - s[2];
            real r3 = (s[0] - s[1])*(real)0.5 - s[3];
            d[0] = r0; d[1] = r1; d[2] = r2; d[3] = r3;
          }
        } else {
          real c = ((p->mode & SLEEF_MODE_BACKWARD) != 0) ? (real)0.5 : (real)1.0;
          real r0 = s[0] + s[1];
          real r1 = s[0] - s[1];
          d[0] = r0 * c; d[1] = r1 * c;
        }
      }
    }
    return;
  }

  //

#ifdef _OPENMP
  const int tn = omp_get_thread_num();
  real *t[] = { p->x1[tn], p->x0[tn], d };
#else
  real *t[] = { p->x1[0], p->x0[0], d };
#endif

  const real *lb = s;
  int nb = 0;

  if ((p->mode & SLEEF_MODE_REAL) != 0 && (p->pathLen & 1) == 0 &&
      ((p->mode & SLEEF_MODE_BACKWARD) != 0) != ((p->mode & SLEEF_MODE_ALT) != 0)) nb = -1;
  if ((p->mode & SLEEF_MODE_REAL) == 0 && (p->pathLen & 1) == 1) nb = -1;

  if ((p->mode & SLEEF_MODE_REAL) != 0 &&
      ((p->mode & SLEEF_MODE_BACKWARD) != 0) != ((p->mode & SLEEF_MODE_ALT) != 0)) {
    (*REALSUB1[p->isa])(t[nb+1], s, p->log2len, p->rtCoef0, p->rtCoef1, (p->mode & SLEEF_MODE_ALT) == 0);
    if ((p-> mode & SLEEF_MODE_ALT) == 0) t[nb+1][(1 << p->log2len)+1] = -s[(1 << p->log2len)+1] * 2;
    lb = t[nb+1];
    nb = (nb + 1) & 1;
  }

  for(int level = p->log2len;level >= 1;) {
    int N = ABS(p->bestPath[level]), config = p->bestPathConfig[level];
    dispatch(p, N, t[nb+1], lb, level, config);
    level -= N;
    lb = t[nb+1];
    nb = (nb + 1) & 1;
  }

  if ((p->mode & SLEEF_MODE_REAL) != 0 &&
      ((p->mode & SLEEF_MODE_BACKWARD) == 0) != ((p->mode & SLEEF_MODE_ALT) != 0)) {
    (*REALSUB0[p->isa])(d, lb, p->log2len, p->rtCoef0, p->rtCoef1);
    if ((p->mode & SLEEF_MODE_ALT) == 0) {
      d[(1 << p->log2len)+1] = -d[(1 << p->log2len)+1];
      d[(2 << p->log2len)+0] =  d[1];
      d[(2 << p->log2len)+1] =  0;
      d[1] = 0;
    }
  }
}
