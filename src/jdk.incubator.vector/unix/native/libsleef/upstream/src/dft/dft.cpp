//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <memory>
#include <unordered_map>
#include <sstream>
#include <chrono>
#include <thread>

#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <cassert>
#include <cmath>

#ifndef SLEEF_ENABLE_PARALLELFOR
#include <omp.h>
#endif

#include "compat.h"

#include "sleef.h"
#define IMPORT_IS_EXPORT
#include "sleefdft.h"

#include "misc.h"
#include "common.h"

#include "dftcommon.hpp"
#include "dispatchdp.hpp"
#include "dispatchsp.hpp"

using namespace std;

//

#ifndef ENABLE_STREAM
#error ENABLE_STREAM not defined
#endif

static const int constK[] = { 0, 2, 6, 14, 38, 94, 230, 542, 1254 };

extern const char *configStr[];

static void sighandler(int signum) { LONGJMP(sigjmp, 1); }

static int checkISAAvailability(int isa, int (*GETINT_[16])(int), int BASETYPEID_) {
  static mutex mtx;

  unique_lock<mutex> lock(mtx);

  signal(SIGILL, sighandler);

  if (SETJMP(sigjmp) == 0) {
    int ret = GETINT_[isa] != NULL && (*GETINT_[isa])(BASETYPEID_);
    signal(SIGILL, SIG_DFL);
    return ret;
  }

  signal(SIGILL, SIG_DFL);
  return 0;
}

static void startAllThreads(const int nth) {
#ifndef SLEEF_ENABLE_PARALLELFOR
  volatile int8_t *state = (int8_t *)calloc(nth, 1);
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

static uint32_t ilog2(uint32_t q) {
  static const uint32_t tab[] = {0,1,2,2,3,3,3,3,4,4,4,4,4,4,4,4};
  uint32_t r = 0,qq;

  if (q & 0xffff0000) r = 16;

  q >>= r;
  qq = q | (q >> 1);
  qq |= (qq >> 2);
  qq = ((qq & 0x10) >> 4) | ((qq & 0x100) >> 7) | ((qq & 0x1000) >> 10);

  return r + tab[qq] * 4 + tab[q >> (tab[qq] * 4)] - 1;
}

static uint32_t uperm(int nbits, uint32_t k, int s, int d) {
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

static void showPath(ostream &os, const string &mes, const vector<Action>& path) {
  os << mes;
  for(auto e : path) os << e << " ";
  os << endl;
}

static void showPath(FILE *fp, const string &mes, const vector<Action>& path) {
  ostringstream s;
  showPath(s, mes, path);
  fputs(s.str().c_str(), fp);
}

// Dispatcher

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::dispatch(const int N, real *d, const real *s, const int level, const int config) {
  const int K = constK[N];
  if (level == N) {
    // Last
    if ((mode & SLEEF_MODE_BACKWARD) == 0) {
      void (*func)(real *, const real *, const int) = DFTF[config][isa][N];
      (*func)(d, s, log2len-N);
    } else {
      void (*func)(real *, const real *, const int) = DFTB[config][isa][N];
      (*func)(d, s, log2len-N);
    }
  } else if (level == (int)log2len) {
    // First
    assert(vecwidth <= (1 << N));
    const int shift = log2len-N - log2vecwidth;
    if ((mode & SLEEF_MODE_BACKWARD) == 0) {
      if (minshift <= shift && shift < MAXSHIFT) {
        void (*func)(real *, uint32_t *, const real *, const real *, const int) = TBUTFS[shift][config][isa][N];
        (*func)(d, perm[level], s, tbl[N][level], K);
      } else {
        void (*func)(real *, uint32_t *, const real *, const int, const real *, const int) = TBUTF[config][isa][N];
        (*func)(d, perm[level], s, log2len-N, tbl[N][level], K);
      }
    } else {
      if (minshift <= shift && shift < MAXSHIFT) {
        void (*func)(real *, uint32_t *, const real *, const real *, const int) = TBUTBS[shift][config][isa][N];
        (*func)(d, perm[level], s, tbl[N][level], K);
      } else {
        void (*func)(real *, uint32_t *, const real *, const int, const real *, const int) = TBUTB[config][isa][N];
        (*func)(d, perm[level], s, log2len-N, tbl[N][level], K);
      }
    }
  } else {
    if ((mode & SLEEF_MODE_BACKWARD) == 0) {
      void (*func)(real *, uint32_t *, const int, const real *, const int, const real *, const int) = BUTF[config][isa][N];
      (*func)(d, perm[level], log2len-level, s, log2len-N, tbl[N][level], K);
    } else {
      void (*func)(real *, uint32_t *, const int, const real *, const int, const real *, const int) = BUTB[config][isa][N];
      (*func)(d, perm[level], log2len-level, s, log2len-N, tbl[N][level], K);
    }
  }
}

// Transposer

#define LOG2BS 4
#define BS (1 << LOG2BS)

#define TRANSPOSE_BLOCK(y2) do {                                        \
    for(int x2=y2+1;x2<BS;x2++) {                                        \
      element_t r = *(element_t *)&row[y2].r[x2*2+0];                        \
      *(element_t *)&row[y2].r[x2*2+0] = *(element_t *)&row[x2].r[y2*2+0]; \
      *(element_t *)&row[x2].r[y2*2+0] = r;                                \
    }} while(0)

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
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

        TRANSPOSE_BLOCK( 0); TRANSPOSE_BLOCK( 1);
        TRANSPOSE_BLOCK( 2); TRANSPOSE_BLOCK( 3);
        TRANSPOSE_BLOCK( 4); TRANSPOSE_BLOCK( 5);
        TRANSPOSE_BLOCK( 6); TRANSPOSE_BLOCK( 7);
        TRANSPOSE_BLOCK( 8); TRANSPOSE_BLOCK( 9);
        TRANSPOSE_BLOCK(10); TRANSPOSE_BLOCK(11);
        TRANSPOSE_BLOCK(12); TRANSPOSE_BLOCK(13);
        TRANSPOSE_BLOCK(14); TRANSPOSE_BLOCK(15);

        for(int y2=0;y2<BS;y2++) {
          *(row_t *)&d[(((x+y2) << log2n)+y)*2] = row[y2];
        }
      }
    }
  }
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
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

#ifndef SLEEF_ENABLE_PARALLELFOR
    int y=0;
#pragma omp parallel for
    for(y=0;y<(1 << log2n);y+=BS) {
#else
    parallelFor(0, (1 << log2n), BS, [&](int64_t start, int64_t end, int64_t inc) {
    for(int y=start;y<end;y+=inc) {
#endif
      for(int x=0;x<(1 << log2m);x+=BS) {
        row_t row[BS];
        for(int y2=0;y2<BS;y2++) {
          row[y2] = *(row_t *)&s[(((y+y2) << log2m)+x)*2];
        }

        TRANSPOSE_BLOCK( 0); TRANSPOSE_BLOCK( 1);
        TRANSPOSE_BLOCK( 2); TRANSPOSE_BLOCK( 3);
        TRANSPOSE_BLOCK( 4); TRANSPOSE_BLOCK( 5);
        TRANSPOSE_BLOCK( 6); TRANSPOSE_BLOCK( 7);
        TRANSPOSE_BLOCK( 8); TRANSPOSE_BLOCK( 9);
        TRANSPOSE_BLOCK(10); TRANSPOSE_BLOCK(11);
        TRANSPOSE_BLOCK(12); TRANSPOSE_BLOCK(13);
        TRANSPOSE_BLOCK(14); TRANSPOSE_BLOCK(15);

        for(int y2=0;y2<BS;y2++) {
          *(row_t *)&d[(((x+y2) << log2n)+y)*2] = row[y2];
        }
      }
    }
#ifdef SLEEF_ENABLE_PARALLELFOR
    });
#endif
  }
}

// Table generator

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
static real2 r2coefsc(int i, int log2len, int level, real2 (*SINCOSPI_)(real)) {
  return (*SINCOSPI_)((i & ((-1 << (log2len - level)) & ~(-1 << log2len))) * ((real)1.0/(1 << (log2len-1))));
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
static real2 srcoefsc(int i, int log2len, int level, real2 (*SINCOSPI_)(real)) {
  return (*SINCOSPI_)(((3*(i & (-1 << (log2len - level)))) & ~(-1 << log2len)) * ((real)1.0/(1 << (log2len-1))));
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
static int makeTableRecurse(real *x, int *p, const int log2len, const int levelorg, const int levelinc, const int sign, const int top, const int bot, const int N, int cnt, real2 (*SINCOSPI_)(real)) {
  if (levelinc >= N-1) return cnt;
  const int level = levelorg - levelinc;
  if (bot - top > 4) {
    const int bl = 1 << (N - levelinc);
    const int w = bl/4;
    for(int j=0;j<(bot-top)/bl;j++) {
      for(int i=0;i<w;i++) {
        int a = sign*(p[(levelinc << N) + top+bl*j+i] & (-1 << (log2len - level)));
        real2 sc;
        sc = r2coefsc<real, real2, MAXSHIFT, MAXBUTWIDTH>(a, log2len, level, SINCOSPI_);
        x[cnt++] = -sc.x; x[cnt++] = -sc.y;
        sc = srcoefsc<real, real2, MAXSHIFT, MAXBUTWIDTH>(a, log2len, level, SINCOSPI_);
        x[cnt++] = -sc.x; x[cnt++] = -sc.y;
      }
      cnt = makeTableRecurse<real, real2, MAXSHIFT, MAXBUTWIDTH>(x, p, log2len, levelorg, levelinc+1, sign, top+bl*j       , top+bl*j + bl/2, N, cnt, SINCOSPI_);
      cnt = makeTableRecurse<real, real2, MAXSHIFT, MAXBUTWIDTH>(x, p, log2len, levelorg, levelinc+2, sign, top+bl*j + bl/2, top+bl*j + bl  , N, cnt, SINCOSPI_);
    }
  } else if (bot - top == 4) {
    int a = sign*(p[(levelinc << N) + top] & (-1 << (log2len - level)));
    real2 sc;
    sc = r2coefsc<real, real2, MAXSHIFT, MAXBUTWIDTH>(a, log2len, level, SINCOSPI_);
    x[cnt++] = -sc.x; x[cnt++] = -sc.y;
    sc = srcoefsc<real, real2, MAXSHIFT, MAXBUTWIDTH>(a, log2len, level, SINCOSPI_);
    x[cnt++] = -sc.x; x[cnt++] = -sc.y;
  }

  return cnt;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
static real **makeTable(int sign, int vecwidth, int log2len, const int N, const int K, real2 (*SINCOSPI_)(real)) {
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
          p[(j << N) + i] = uperm(log2len, i0 + (i << (log2len-N)), log2len-level, log2len-(level-j));
        }
      }

      int a = -sign*(p[((N-1) << N) + 0] & (-1 << (log2len - level)));
      real2 sc = r2coefsc<real, real2, MAXSHIFT, MAXBUTWIDTH>(a, log2len, level-N+1, SINCOSPI_);
      tbl[level][tblOffset++] = sc.y; tbl[level][tblOffset++] = sc.x;

      tblOffset = makeTableRecurse<real, real2, MAXSHIFT, MAXBUTWIDTH>(tbl[level], p, log2len, level, 0, sign, 0, 1 << N, N, tblOffset, SINCOSPI_);
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

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::generatePerm(const vector<Action> &path) {
  for(unsigned i=0;i<path.size();i++) {
    int level = path[i].level;
    if (level == 0) break;
    int N = path[i].N;

    int i1 = 0;
    for(int i0=0;i0 < (1 << (log2len-N));i0+=vecwidth, i1++) {
      perm[level][i1] = 2*uperm(log2len, i0, log2len-level, log2len-(level-N));
    }
    for(;i1 < (1 << log2len) + 8;i1++) perm[level][i1] = 0;
  }
}

// Planner

template<typename T>
class KShortest {
  vector<vector<T>> heap;
  vector<double> heapCost;
  unordered_map<T, size_t> reached;

  /** Remove the n-th path in the heap */
  void remove(unsigned n) {
    assert(n < heap.size());
    heap.erase(heap.begin() + n);
    heapCost.erase(heapCost.begin() + n);
    assert(heap.size() == heapCost.size());
  }

public:
  size_t limit = 0;

  virtual ~KShortest() {}

  /** Add a path to the heap */
  size_t addPath(vector<T> &p, double cost) {
    heap.push_back(p);
    heapCost.push_back(cost);
    assert(heap.size() == heapCost.size());
    if (p.size()) reached[p[p.size()-1]]++;
    return heap.size();
  }

  void showHeap(ostream &os) const {
    os << "Heap :" << endl;
    int i = 0;
    for(auto a : heap) {
      os << i << " : ";
      for(auto e : a) os << e << " ";
      os << ": " << heapCost[i] << endl;
      i++;
    }
    os << endl;
  }

  /** Return the n-th path in the heap */
  vector<T> getPath(unsigned n) const {
    assert(n < heap.size());
    return heap[n];
  }

  /** Return if pos is a destination */
  virtual bool isDestination(const T& pos) = 0;

  /** Return next nodes after the path */
  virtual vector<T> next(const vector<T>& path) = 0;

  /** Return the cost to travel the path */
  virtual double cost(const vector<T>& path) = 0;

  /** Compute and return the next-best path */
  vector<T> execute() {
    for(;;) {
#ifdef DEBUG
      showHeap(cout);
#endif

      double bestCost = INFINITY_;
      unsigned bestNum = UINT_MAX;

      for(unsigned i=0;i<heap.size();i++) {
        double c = heapCost[i];
        if (c < bestCost) {
          bestCost = c;
          bestNum = (int)i;
        }
      }

      if (bestNum == UINT_MAX) return vector<T>();

      vector<T> best = getPath(bestNum);

      remove(bestNum);

      if (isDestination(best[best.size()-1])) return best;

      auto adj = next(best);

      for(auto a : adj) {
        if (limit != 0 && reached[a] >= limit) continue;
        vector<T> p(best);
        p.push_back(a);
        addPath(p, cost(p));
      }
    }
  }
};

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::measurementRun(real *d, const real *s, const vector<Action> &path, uint64_t niter) {
  auto tid = this_thread::get_id();
  real *t[] = { nullptr, nullptr, d };
  {
    unique_lock lock(mtx);
    if (xn.count(tid) != 0) {
      auto e = xn[tid];
      t[0] = e.first;
      t[1] = e.second;
    } else {
      t[0] = (real *)Sleef_malloc(sizeof(real) * 2 * (1L << log2len));
      t[1] = (real *)Sleef_malloc(sizeof(real) * 2 * (1L << log2len));
      xn[tid] = pair<real *, real *>{ t[0], t[1] };
    }
  }

  for(uint64_t i=0;i<niter;i++) {
    const real *lb = s;
    int nb = 0;

    if ((mode & SLEEF_MODE_REAL) != 0 && (path.size() & 1) == 0 &&
        ((mode & SLEEF_MODE_BACKWARD) != 0) != ((mode & SLEEF_MODE_ALT) != 0)) nb = -1;
    if ((mode & SLEEF_MODE_REAL) == 0 && (path.size() & 1) == 1) nb = -1;

    if ((mode & SLEEF_MODE_REAL) != 0 &&
        ((mode & SLEEF_MODE_BACKWARD) != 0) != ((mode & SLEEF_MODE_ALT) != 0)) {
      (*REALSUB1[isa])(t[nb+1], s, log2len, rtCoef0, rtCoef1, (mode & SLEEF_MODE_ALT) == 0);
      if (( mode & SLEEF_MODE_ALT) == 0) t[nb+1][(1 << log2len)+1] = -s[(1 << log2len)+1] * 2;
      lb = t[nb+1];
      nb = (nb + 1) & 1;
    }

    int level = log2len;
    for(unsigned j=0;j<path.size();j++) {
      if (path[j].level == 0) {
        assert(j == path.size()-1);
        break;
      }
      int N = path[j].N, config = path[j].config;
      dispatch(N, t[nb+1], lb, level, config);
      level -= N;
      lb = t[nb+1];
      nb = (nb + 1) & 1;
    }

    if (path[path.size()-1].level != 0) continue;

    if ((mode & SLEEF_MODE_REAL) != 0 &&
        ((mode & SLEEF_MODE_BACKWARD) == 0) != ((mode & SLEEF_MODE_ALT) != 0)) {
      (*REALSUB0[isa])(d, lb, log2len, rtCoef0, rtCoef1);
      if ((mode & SLEEF_MODE_ALT) == 0) {
        d[(1 << log2len)+1] = -d[(1 << log2len)+1];
        d[(2 << log2len)+0] =  d[1];
        d[(2 << log2len)+1] =  0;
        d[1] = 0;
      }
    }
  }
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
double SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::measurePath(const vector<Action> &path, uint64_t minTime) {
  real *s2 = NULL, *d2 = NULL;
  const real *s = in  == NULL ? (s2 = (real *)memset(Sleef_malloc((2 << log2len) * sizeof(real)), 0, sizeof(real) * (2 << log2len))) : in;
  real       *d = out == NULL ? (d2 = (real *)memset(Sleef_malloc((2 << log2len) * sizeof(real)), 0, sizeof(real) * (2 << log2len))) : out;

  generatePerm(path);

  uint64_t tm = UINT64_MAX, niter = 1;

  if ((path[0].config & CONFIG_MT) != 0) startAllThreads(nThread);

  for(;;) {
    auto tm0 = chrono::high_resolution_clock::now();

    measurementRun(d, s, path, niter);

    auto tm1 = chrono::high_resolution_clock::now();

    tm = chrono::duration_cast<std::chrono::nanoseconds>(tm1 - tm0).count();

    if (tm >= minTime)  break;

    niter *= 2;
  }

  {
    auto tm0 = chrono::high_resolution_clock::now();

    measurementRun(d, s, path, niter);

    auto tm1 = chrono::high_resolution_clock::now();

    uint64_t tm2 = chrono::duration_cast<std::chrono::nanoseconds>(tm1 - tm0).count();
    if (tm2 < tm) tm = tm2;
  }

  if (d2 != NULL) Sleef_free(d2);
  if (s2 != NULL) Sleef_free(s2);

  return double(tm) / niter;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
double SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::measurePath(SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> *inst, bool mt,
                                                                     const vector<Action> &path, uint32_t hlen, uint32_t vlen, uint64_t minTime) {
  real *s2 = NULL;
  const size_t z = (2 << (log2hlen + log2vlen)) * sizeof(real);
  const real *s = in  == NULL ? (s2 = (real *)memset(Sleef_malloc(z), 0, z)) : in;
  double scale = 1;

  if (mt) {
    if ((int)vlen > inst->nThread * 2) {
      scale = vlen / (inst->nThread * 2);
      vlen = inst->nThread * 2;
    }
  } else {
    if (vlen > 2) {
      scale = vlen / 2;
      vlen = 2;
    }
  }

  inst->generatePerm(path);

  uint64_t tm = UINT64_MAX, niter = 1;

  if (mt) startAllThreads(inst->nThread);

  for(;;) {
    auto tm0 = chrono::high_resolution_clock::now();

    if (mt) {
#ifndef SLEEF_ENABLE_PARALLELFOR
      int y=0;
#pragma omp parallel for
      for(y=0;y<(int)vlen;y++) {
        inst->measurementRun(&tBuf[hlen*2*y], &s[hlen*2*y], path, niter);
      }
#else
      parallelFor(0, (int)vlen, 1, [&](int64_t start, int64_t end, int64_t inc) {
        for(int y=start;y<end;y+=inc) {
          inst->measurementRun(&tBuf[hlen*2*y], &s[hlen*2*y], path, niter);
        }
      });
#endif
    } else {
      for(int y=0;y<(int)vlen;y++) {
        inst->measurementRun(&tBuf[hlen*2*y], &s[hlen*2*y], path, niter);
      }
    }

    auto tm1 = chrono::high_resolution_clock::now();

    tm = chrono::duration_cast<std::chrono::nanoseconds>(tm1 - tm0).count();

    if (tm >= minTime)  break;

    niter *= 2;
  }

  {
    auto tm0 = chrono::high_resolution_clock::now();

    if (mt) {
#ifndef SLEEF_ENABLE_PARALLELFOR
      int y=0;
#pragma omp parallel for
      for(y=0;y<(int)vlen;y++) {
        inst->measurementRun(&tBuf[hlen*2*y], &s[hlen*2*y], path, niter);
      }
#else
      parallelFor(0, (int)vlen, 1, [&](int64_t start, int64_t end, int64_t inc) {
        for(int y=start;y<end;y+=inc) {
          inst->measurementRun(&tBuf[hlen*2*y], &s[hlen*2*y], path, niter);
        }
      });
#endif
    } else {
      for(int y=0;y<(int)vlen;y++) {
        inst->measurementRun(&tBuf[hlen*2*y], &s[hlen*2*y], path, niter);
      }
    }

    auto tm1 = chrono::high_resolution_clock::now();

    uint64_t tm2 = chrono::duration_cast<std::chrono::nanoseconds>(tm1 - tm0).count();
    if (tm2 < tm) tm = tm2;
  }

  if (s2 != NULL) Sleef_free(s2);

  return double(tm) * scale / niter;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
class QuickFinder : public KShortest<Action> {
  SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> &inst;

public:
  QuickFinder(SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> &inst_, const vector<Action> &startPoints, size_t limit_) :
    inst(inst_) {
    limit = limit_;
    for(auto a : startPoints) {
      vector<Action> v { a };
      addPath(v, cost(v));
    }
  }

  ~QuickFinder() {}

  virtual bool isDestination(const Action& pos) {
    return pos.level == pos.N;
  }

  virtual vector<Action> next(const vector<Action>& path) {
    const int NMAX = MIN(MIN(inst.log2len, MAXBUTWIDTH+1), inst.log2len - inst.log2vecwidth + 1);

    vector<Action> v;

    Action last = path[path.size()-1];

    int level = last.level - last.N;

    assert(level > 0);

    for(int config = 0;config < CONFIGMAX;config++) {
      if ((config & CONFIG_MT) != (last.config & CONFIG_MT)) continue;

      for(int N=1;N<NMAX && N <= level;N++) {
        if (!inst.executable[config][level][N]) continue;
        Action a(config, level, N);
        v.push_back(a);
      }
    }

    return v;
  }

  virtual double cost(const vector<Action>& path) {
    return inst.measurePath(path, 100000);
  }
};

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
class QuickFinder2 : public KShortest<Action> {
  SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH> &inst2d;
  SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> *inst1d;
  const bool mt;
  const uint32_t hlen, vlen;

public:
  QuickFinder2(SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH> &inst2d_,
               SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> *inst1d_, bool mt_,
               const vector<Action> &startPoints, uint32_t hlen_, uint32_t vlen_, size_t limit_) :
    inst2d(inst2d_), inst1d(inst1d_), mt(mt_), hlen(hlen_), vlen(vlen_) {
    limit = limit_;
    for(auto a : startPoints) {
      vector<Action> v { a };
      addPath(v, cost(v));
    }
  }

  ~QuickFinder2() {}

  virtual bool isDestination(const Action& pos) {
    return pos.level == pos.N;
  }

  virtual vector<Action> next(const vector<Action>& path) {
    const int NMAX = MIN(MIN(inst1d->log2len, MAXBUTWIDTH+1), inst1d->log2len - inst1d->log2vecwidth + 1);

    vector<Action> v;

    Action last = path[path.size()-1];

    int level = last.level - last.N;

    assert(level > 0);

    for(int config = 0;config < CONFIGMAX;config++) {
      if ((config & CONFIG_MT) != (last.config & CONFIG_MT)) continue;

      for(int N=1;N<NMAX && N <= level;N++) {
        if (!inst1d->executable[config][level][N]) continue;
        Action a(config, level, N);
        v.push_back(a);
      }
    }

    return v;
  }

  virtual double cost(const vector<Action>& path) {
    return inst2d.measurePath(inst1d, mt, path, hlen, vlen, 100000);
  }
};

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
class PathEstimator : public KShortest<Action> {
  SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> &inst;

public:
  PathEstimator(SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> &inst_, const vector<Action> &startPoints) :
    inst(inst_) {
    limit = 1;
    for(auto a : startPoints) {
      vector<Action> v { a };
      addPath(v, cost(v));
    }
  }

  ~PathEstimator() {}

  virtual bool isDestination(const Action& pos) {
    return pos.level == pos.N;
  }

  virtual vector<Action> next(const vector<Action>& path) {
    const int NMAX = MIN(MIN(inst.log2len, MAXBUTWIDTH+1), inst.log2len - inst.log2vecwidth + 1);

    vector<Action> v;

    Action last = path[path.size()-1];

    if (last.level == 0) return v;

    int level = last.level - last.N;

    assert(level > 0);

    for(int config = 0;config < CONFIGMAX;config++) {
      if ((config & CONFIG_MT) != (last.config & CONFIG_MT)) continue;

      for(int N=1;N<NMAX && N <= level;N++) {
        if (!inst.executable[config][level][N]) continue;
        v.push_back(Action(config, level, N));
      }
    }

    return v;
  }

  static uint64_t estimate(int log2len, int config, int level, int N) {
    uint64_t ret = N * 1000 + ABS(N-3) * 1000;
    if (log2len >= 14 && (config & CONFIG_MT) != 0) ret /= 2;
    return ret;
  }

  virtual double cost(const vector<Action>& path) {
    uint64_t t = 0;
    for(auto a : path) {
      if (!inst.executable[a.config][a.level][a.N]) return INFINITY_;
      t += estimate(inst.log2len, a.config, a.level, a.N);
    }
    return t;
  }
};

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::searchForBestPath(int nPaths) {
  const int NMAX = MIN(MIN(log2len, MAXBUTWIDTH+1), log2len - log2vecwidth + 1);

  vector<Action> sp;

  for(int config = 0;config < CONFIGMAX;config++) {
    for(int N=1;N<NMAX;N++) {
      if (!executable[config][log2len][N]) continue;
      sp.push_back(Action(config, log2len, N));
    }
  }

  if (nPaths == 0) {
    auto pf = make_shared<PathEstimator<real, real2, MAXSHIFT, MAXBUTWIDTH>>(*this, sp);
    bestPath = pf->execute();
    return;
  }

  auto pf = make_shared<QuickFinder<real, real2, MAXSHIFT, MAXBUTWIDTH>>(*this, sp, 1);

  double bestTime = INFINITY_;

  for(int i=0;i<nPaths;i++) {
    auto p = pf->execute();

    if (p.size() == 0) break;

    double tm = measurePath(p, 1000000);

    if (tm < bestTime) {
      bestPath = p;
      bestTime = tm;
    }
  }
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::searchForRandomPath() {
  const int NMAX = MIN(MIN(log2len, MAXBUTWIDTH+1), log2len - log2vecwidth + 1);

  vector<Action> path;

  int level = log2len;
  while(level > 0) {
    int config = 0;
    int N = rand() % MIN(level, NMAX-1) + 1;
    if (!executable[config][level][N]) continue;

    path.push_back(Action(config, level, N));
    level -= N;
  }

  bestPath = path;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
pair<vector<Action>, double> SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::searchForBestPath(SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> *inst,
                                                                                                 bool mt, uint32_t hlen, uint32_t vlen, int nPaths) {
  assert(nPaths != 0);

  const int NMAX = MIN(MIN(inst->log2len, MAXBUTWIDTH+1), inst->log2len - inst->log2vecwidth + 1);

  vector<Action> sp;

  for(int config = 0;config < CONFIGMAX;config++) {
    for(int N=1;N<NMAX;N++) {
      if (!inst->executable[config][inst->log2len][N]) continue;
      sp.push_back(Action(config, inst->log2len, N));
    }
  }

  auto qf2 = QuickFinder2<real, real2, MAXSHIFT, MAXBUTWIDTH>(*this, inst, mt, sp, hlen, vlen, 1);

  vector<Action> bestPath;
  double bestTime = INFINITY_;

  for(int i=0;i<nPaths;i++) {
    auto p = qf2.execute();

    if (p.size() == 0) break;

    double tm = measurePath(inst, mt, p, hlen, vlen, 1000000);

    if (tm < bestTime) {
      bestPath = p;
      bestTime = tm;
    }
  }

  return pair<vector<Action>, double>(bestPath, bestTime);
}

//

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
bool SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::measure(bool randomize) {
  if (log2len == 1) {
    bestPath.clear();
    bestPath.push_back(Action(0, 1, 1));

    return true;
  }

  for(int config=0;config<CONFIGMAX;config++) {
    for(uint32_t level = log2len;level >= 1;level--) {
      for(uint32_t N=1;N<=MAXBUTWIDTH;N++) {
        executable[config][level][N] = false;
      }
    }
  }

  for(int config=0;config<CONFIGMAX;config++) {
#if ENABLE_STREAM == 0
    if ((config & CONFIG_STREAM) != 0) continue;
#endif
    if ((mode2 & SLEEF_MODE2_MT1D) == 0 && (config & CONFIG_MT) != 0) continue;
    for(uint32_t level = log2len;level >= 1;level--) {
      for(uint32_t N=1;N<=MAXBUTWIDTH;N++) {
        if (level < N || log2len <= N) continue;
        if (level == N) {
          executable[config][level][N] = true;
        } else if (level == log2len) {
          if (tbl[N] == NULL || tbl[N][level] == NULL) continue;
          if (vecwidth > (1 << N)) continue;
          executable[config][level][N] = true;
        } else {
          if (tbl[N] == NULL || tbl[N][level] == NULL) continue;
          if (vecwidth > 2 && log2len <= N+2) continue;
          if ((int)log2len - (int)level < log2vecwidth) continue;
          executable[config][level][N] = true;
        }
      }
    }
  }

  //

  {
    bool executable_ = false;
    for(int i=1;i<=MAXBUTWIDTH && !executable_;i++) {
      if (executable[0][log2len][i]) executable_ = true;
    }

    if (!executable_) return false;
  }

  if (!randomize) {
    searchForBestPath((mode & SLEEF_MODE_MEASURE) != 0 ? 32 : 0);
    if ((mode & SLEEF_MODE_VERBOSE) != 0) {
      if ((mode & SLEEF_MODE_MEASURE) != 0) {
        showPath(verboseFP, "Measure : ", bestPath);
      } else if ((mode & SLEEF_MODE_INTERNAL_2D) == 0) {
        showPath(verboseFP, "Estimate : ", bestPath);
      }
    }

    if ((mode & SLEEF_MODE_MEASURE) != 0) saveMeasurementResults();
  } else {
    searchForRandomPath();
    if ((mode & SLEEF_MODE_VERBOSE) != 0) {
      showPath(verboseFP, "Random path : ", bestPath);
    }
  }

  return true;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
pair<uint64_t, uint64_t> SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::measureTranspose() {
  uint64_t tmMT, tmNoMT;

  real *tBuf2 = (real *)Sleef_malloc(sizeof(real)*2*hlen*vlen);

  const int niter =  1 + 5000000 / (hlen * vlen + 1);

  auto tm0 = chrono::high_resolution_clock::now();
  for(int i=0;i<niter;i++) {
    transpose<real, real2, MAXSHIFT, MAXBUTWIDTH>(tBuf2, tBuf, log2hlen, log2vlen);
    transpose<real, real2, MAXSHIFT, MAXBUTWIDTH>(tBuf2, tBuf, log2vlen, log2hlen);
  }
  auto tm1 = chrono::high_resolution_clock::now();
  tmNoMT = chrono::duration_cast<std::chrono::nanoseconds>(tm1 - tm0).count();

  if ((mode & SLEEF_MODE_VERBOSE) != 0) fprintf(verboseFP, "transpose NoMT(measured): %lld\n", (long long int)tmNoMT);

  tm0 = chrono::high_resolution_clock::now();
  for(int i=0;i<niter;i++) {
    transposeMT<real, real2, MAXSHIFT, MAXBUTWIDTH>(tBuf2, tBuf, log2hlen, log2vlen);
    transposeMT<real, real2, MAXSHIFT, MAXBUTWIDTH>(tBuf2, tBuf, log2vlen, log2hlen);
  }
  tm1 = chrono::high_resolution_clock::now();
  tmMT = chrono::duration_cast<std::chrono::nanoseconds>(tm1 - tm0).count();

  if ((mode & SLEEF_MODE_VERBOSE) != 0) fprintf(verboseFP, "transpose   MT(measured): %lld\n", (long long int)tmMT);

  Sleef_free(tBuf2);

  tmMT /= niter;
  tmNoMT /= niter;

  return pair<uint64_t, uint64_t>(tmMT, tmNoMT);
}

// Implementation of SleefDFT_*_init1d

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::SleefDFTXX(uint32_t n, const real *in_, real *out_, uint64_t mode_, const char *baseTypeString,
    int BASETYPEID_, int MAGIC_, int minshift_,
    int (*GETINT_[16])(int), const void *(*GETPTR_[16])(int), real2 (*SINCOSPI_)(real),
    void (*DFTF_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int),
    void (*DFTB_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int),
    void (*TBUTF_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int),
    void (*TBUTB_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int),
    void (*BUTF_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int),
    void (*BUTB_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int),
    void (*REALSUB0_[ISAMAX])(real *, const real *, const int, const real *, const real *),
    void (*REALSUB1_[ISAMAX])(real *, const real *, const int, const real *, const real *, const int),
    void (*TBUTFS_[MAXSHIFT][CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const real *, const int),
    void (*TBUTBS_[MAXSHIFT][CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const real *, const int)
  ) :
  magic(MAGIC_), baseTypeID(BASETYPEID_), in(in_), out(out_), nThread(thread::hardware_concurrency()),
  log2len((mode_ & SLEEF_MODE_REAL) ? ilog2(n)-1 : ilog2(n)),
  mode(((mode_ & SLEEF_MODE_ALT) && log2len > 1) ? mode_ ^ SLEEF_MODE_BACKWARD : mode_),
  minshift(minshift_),
  DFTF(DFTF_), DFTB(DFTB_), TBUTF(TBUTF_), TBUTB(TBUTB_), BUTF(BUTF_), BUTB(BUTB_), REALSUB0(REALSUB0_), REALSUB1(REALSUB1_),
  TBUTFS(TBUTFS_), TBUTBS(TBUTBS_) {

  verboseFP = defaultVerboseFP;

  // Mode

  if ((mode & SLEEF_MODE_REAL) != 0) n /= 2;

  if ((mode & SLEEF_MODE_NO_MT) == 0) mode2 |= SLEEF_MODE2_MT1D;

  if (log2len <= 1) return;

  // ISA availability

  int bestPriority = -1;
  isa = -1;

  for(int i=0;i<ISAMAX;i++) {
    if (checkISAAvailability(i, GETINT_, BASETYPEID_) && bestPriority < (*GETINT_[i])(GETINT_DFTPRIORITY) && n >= (uint32_t)((*GETINT_[i])(GETINT_VECWIDTH) * (*GETINT_[i])(GETINT_VECWIDTH))) {
      bestPriority = (*GETINT_[i])(GETINT_DFTPRIORITY);
      isa = i;
    }
  }

  if (isa == -1) {
    if ((mode & SLEEF_MODE_VERBOSE) != 0) fprintf(verboseFP, "ISA not available\n");
    magic = 0;
    return;
  }

  // Generate tables

  perm = (uint32_t **)calloc(sizeof(uint32_t *), log2len+1);
  for(int level = log2len;level >= 1;level--) {
    perm[level] = (uint32_t *)Sleef_malloc(sizeof(uint32_t) * ((1 << log2len) + 8));
  }

  if ((mode & SLEEF_MODE_REAL) != 0) {
    rtCoef0 = (real *)Sleef_malloc(sizeof(real) * n);
    rtCoef1 = (real *)Sleef_malloc(sizeof(real) * n);

    if ((mode & SLEEF_MODE_BACKWARD) == 0) {
      for(uint32_t i=0;i<n/2;i++) {
        real2 sc = SINCOSPI_(i*((real)-1.0/n));
        rtCoef0[i*2+0] = rtCoef0[i*2+1] = (real)0.5 - (real)0.5 * sc.x;
        rtCoef1[i*2+0] = rtCoef1[i*2+1] = (real)0.5*sc.y;
      }
    } else {
      for(uint32_t i=0;i<n/2;i++) {
        real2 sc = SINCOSPI_(i*((real)-1.0/n));
        rtCoef0[i*2+0] = rtCoef0[i*2+1] = (real)0.5 + (real)0.5 * sc.x;
        rtCoef1[i*2+0] = rtCoef1[i*2+1] = (real)0.5*sc.y;
      }
    }
  }

  //

  int sign = (mode & SLEEF_MODE_BACKWARD) != 0 ? -1 : 1;

  vecwidth = (*GETINT_[isa])(GETINT_VECWIDTH);
  log2vecwidth = ilog2(vecwidth);

  for(int i=1;i<=MAXBUTWIDTH;i++) {
    tbl[i] = makeTable<real, real2, MAXSHIFT, MAXBUTWIDTH>(sign, vecwidth, log2len, i, constK[i], SINCOSPI_);
  }

  if (loadMeasurementResults()) {
    if ((mode & SLEEF_MODE_VERBOSE) != 0) {
      showPath(verboseFP, "Loaded : ", bestPath);
    }
  } else if (!measure(mode & SLEEF_MODE_DEBUG)) {
    // Fall back to the first ISA
    freeTables();
    isa = 0;

    vecwidth = (*GETINT_[isa])(GETINT_VECWIDTH);
    log2vecwidth = ilog2(vecwidth);

    for(int i=1;i<=MAXBUTWIDTH;i++) {
      tbl[i] = makeTable<real, real2, MAXSHIFT, MAXBUTWIDTH>(sign, vecwidth, log2len, i, constK[i], SINCOSPI_);
    }

    generatePerm(bestPath);

    if (!measure(mode & SLEEF_MODE_DEBUG)) {
      if ((mode & SLEEF_MODE_VERBOSE) != 0) fprintf(verboseFP, "Suitable ISA not found. This should not happen.\n");
      abort();
    }
  }

  generatePerm(bestPath);

  if ((mode & SLEEF_MODE_VERBOSE) != 0) fprintf(verboseFP, "ISA : %s %d bit %s\n", (char *)(*GETPTR_[isa])(0), (int)(GETINT_[isa](GETINT_VECWIDTH) * sizeof(real) * 16), baseTypeString);
}

// Implementation of SleefDFT_*_init2d

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::SleefDFT2DXX(uint32_t vlen_, uint32_t hlen_, const real *in_, real *out_, uint64_t mode_, const char *baseTypeString,
    int BASETYPEID_, int MAGIC_, int MAGIC2D_, int minshift_,
    int (*GETINT_[16])(int), const void *(*GETPTR_[16])(int), real2 (*SINCOSPI_)(real),
    void (*DFTF_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int),
    void (*DFTB_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int),
    void (*TBUTF_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int),
    void (*TBUTB_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int),
    void (*BUTF_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int),
    void (*BUTB_[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int),
    void (*REALSUB0_[ISAMAX])(real *, const real *, const int, const real *, const real *),
    void (*REALSUB1_[ISAMAX])(real *, const real *, const int, const real *, const real *, const int),
    void (*TBUTFS_[MAXSHIFT][CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const real *, const int),
    void (*TBUTBS_[MAXSHIFT][CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const real *, const int)
  ) {
  magic = MAGIC2D_;
  baseTypeID = BASETYPEID_;
  in = in_;
  out = out_;
  hlen = hlen_;
  log2hlen = ilog2(hlen_);
  vlen = vlen_;
  log2vlen = ilog2(vlen_);
  mode = mode_ | SLEEF_MODE_INTERNAL_2D;

  mode2 = 0;
  mode3 = 0;

  planMT = false;

  verboseFP = stdout;

  uint64_t mode1D = (mode & ~SLEEF_MODE_MEASUREBITS) | SLEEF_MODE_ESTIMATE | SLEEF_MODE_NO_MT;

  if ((mode & SLEEF_MODE_NO_MT) == 0) mode3 |= SLEEF_MODE3_MT2D;

  instH = instV = new SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>(hlen, NULL, NULL, mode1D, baseTypeString,
                                                                     BASETYPEID_, MAGIC_, minshift_,
                                                                     GETINT_, GETPTR_, SINCOSPI_,
                                                                     DFTF_, DFTB_, TBUTF_, TBUTB_, BUTF_, BUTB_,
                                                                     REALSUB0_, REALSUB1_, TBUTFS_, TBUTBS_);
  if (hlen != vlen) instV = new SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>(vlen, NULL, NULL, mode1D, baseTypeString,
                                                                               BASETYPEID_, MAGIC_, minshift_,
                                                                               GETINT_, GETPTR_, SINCOSPI_,
                                                                               DFTF_, DFTB_, TBUTF_, TBUTB_, BUTF_, BUTB_,
                                                                               REALSUB0_, REALSUB1_, TBUTFS_, TBUTBS_);

  tBuf = (real *)Sleef_malloc(sizeof(real)*2*hlen*vlen);

  if (!loadMeasurementResults()) {
    if ((mode & SLEEF_MODE_MEASURE) != 0) {
      uint64_t tmMT, tmNoMT;
      auto a = measureTranspose();
      tmMT = a.first;
      tmNoMT = a.second;
      planMT = tmMT < tmNoMT;

      pair<vector<Action>, double> noMT_H, MT_H, noMT_V, MT_V;

      const bool mt = (mode & SLEEF_MODE_NO_MT) == 0;

      if (instH == instV) {
        noMT_H  = searchForBestPath(instH, false, hlen, vlen, 8);
        tmNoMT += noMT_H.second * 2;

        if (mt) {
          MT_H  = searchForBestPath(instH, true, hlen, vlen, 8);
          tmMT += MT_H.second * 2;
        }
      } else {
        noMT_H  = searchForBestPath(instH, false, hlen, vlen, 8);
        noMT_V  = searchForBestPath(instV, false, vlen, hlen, 8);
        tmNoMT += noMT_H.second + noMT_V.second;

        if (mt) {
          MT_H  = searchForBestPath(instH, true, hlen, vlen, 8);
          MT_V  = searchForBestPath(instV, true, vlen, hlen, 8);
          tmMT += MT_H.second + MT_V.second;
        }
      }

      if (!mt) tmMT = ULLONG_MAX;

      if (tmMT < tmNoMT) {
        planMT = true;
        instH->bestPath = MT_H.first;
        if (instH != instV) instV->bestPath = MT_V.first;
      } else {
        planMT = false;
        instH->bestPath = noMT_H.first;
        if (instH != instV) instV->bestPath = noMT_V.first;
      }

      saveMeasurementResults();
    } else {
      planMT = log2hlen + log2vlen >= 14;
      // When the paths are to be estimated, the paths set in the constructors are used
    }
  }

  instH->generatePerm(instH->bestPath);
  if (instH != instV) instV->generatePerm(instV->bestPath);
}

// Implementation of SleefDFT_*_execute

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::execute(const real *s0, real *d0, int MAGIC_, int MAGIC2D_) {
  assert(magic == MAGIC_);

  const real *s = s0 == NULL ? in : s0;
  real *d = d0 == NULL ? out : d0;

  if (log2len <= 1) {
    if ((mode & SLEEF_MODE_REAL) == 0) {
      real r0 = s[0] + s[2];
      real r1 = s[1] + s[3];
      real r2 = s[0] - s[2];
      real r3 = s[1] - s[3];
      d[0] = r0; d[1] = r1; d[2] = r2; d[3] = r3;
    } else {
      if ((mode & SLEEF_MODE_ALT) == 0) {
        if (log2len == 1) {
          if ((mode & SLEEF_MODE_BACKWARD) == 0) {
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
          if ((mode & SLEEF_MODE_BACKWARD) == 0) {
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
        if (log2len == 1) {
          if ((mode & SLEEF_MODE_BACKWARD) == 0) {
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
          real c = ((mode & SLEEF_MODE_BACKWARD) != 0) ? (real)0.5 : (real)1.0;
          real r0 = s[0] + s[1];
          real r1 = s[0] - s[1];
          d[0] = r0 * c; d[1] = r1 * c;
        }
      }
    }
    return;
  }

  //

  auto tid = this_thread::get_id();
  real *t[] = { nullptr, nullptr, d };
  {
    unique_lock lock(mtx);
    if (xn.count(tid) != 0) {
      auto e = xn[tid];
      t[0] = e.first;
      t[1] = e.second;
    } else {
      t[0] = (real *)Sleef_malloc(sizeof(real) * 2 * (1L << log2len));
      t[1] = (real *)Sleef_malloc(sizeof(real) * 2 * (1L << log2len));
      xn[tid] = pair<real *, real *>{ t[0], t[1] };
    }
  }

  const real *lb = s;
  int nb = 0;

  if ((mode & SLEEF_MODE_REAL) != 0 && (bestPath.size() & 1) == 0 &&
      ((mode & SLEEF_MODE_BACKWARD) != 0) != ((mode & SLEEF_MODE_ALT) != 0)) nb = -1;
  if ((mode & SLEEF_MODE_REAL) == 0 && (bestPath.size() & 1) == 1) nb = -1;

  if ((mode & SLEEF_MODE_REAL) != 0 &&
      ((mode & SLEEF_MODE_BACKWARD) != 0) != ((mode & SLEEF_MODE_ALT) != 0)) {
    (*REALSUB1[isa])(t[nb+1], s, log2len, rtCoef0, rtCoef1, (mode & SLEEF_MODE_ALT) == 0);
    if (( mode & SLEEF_MODE_ALT) == 0) t[nb+1][(1 << log2len)+1] = -s[(1 << log2len)+1] * 2;
    lb = t[nb+1];
    nb = (nb + 1) & 1;
  }

  int level = log2len;
  for(unsigned j=0;j<bestPath.size();j++) {
    if (bestPath[j].level == 0) {
      assert(j == bestPath.size()-1);
      break;
    }
    int N = bestPath[j].N, config = bestPath[j].config;
    dispatch(N, t[nb+1], lb, level, config);
    level -= N;
    lb = t[nb+1];
    nb = (nb + 1) & 1;
  }

  if ((mode & SLEEF_MODE_REAL) != 0 &&
      ((mode & SLEEF_MODE_BACKWARD) == 0) != ((mode & SLEEF_MODE_ALT) != 0)) {
    (*REALSUB0[isa])(d, lb, log2len, rtCoef0, rtCoef1);
    if ((mode & SLEEF_MODE_ALT) == 0) {
      d[(1 << log2len)+1] = -d[(1 << log2len)+1];
      d[(2 << log2len)+0] =  d[1];
      d[(2 << log2len)+1] =  0;
      d[1] = 0;
    }
  }
}

//

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::execute(const real *s0, real *d0, int MAGIC_, int MAGIC2D_) {
  assert(magic == MAGIC2D_);

  const real *s = s0 == NULL ? in : s0;
  real *d = d0 == NULL ? out : d0;

  // S -> T -> D -> T -> D

  if ((mode3 & SLEEF_MODE3_MT2D) != 0 &&
      (((mode & SLEEF_MODE_DEBUG) == 0 && planMT) ||
       ((mode & SLEEF_MODE_DEBUG) != 0 && (rand() & 1)))) {
#ifndef SLEEF_ENABLE_PARALLELFOR
    int y=0;
#pragma omp parallel for
    for(y=0;y<vlen;y++) {
      instH->execute(&s[hlen*2*y], &tBuf[hlen*2*y], MAGIC_, MAGIC2D_);
    }
#else
    parallelFor(0, vlen, 1, [&](int64_t start, int64_t end, int64_t inc) {
      for(int y=start;y<end;y+=inc) {
        instH->execute(&s[hlen*2*y], &tBuf[hlen*2*y], MAGIC_, MAGIC2D_);
      }
    });
#endif

    transposeMT<real, real2, MAXSHIFT, MAXBUTWIDTH>(d, tBuf, log2vlen, log2hlen);

#ifndef SLEEF_ENABLE_PARALLELFOR
#pragma omp parallel for
    for(y=0;y<hlen;y++) {
      instV->execute(&d[vlen*2*y], &tBuf[vlen*2*y], MAGIC_, MAGIC2D_);
    }
#else
    parallelFor(0, hlen, 1, [&](int64_t start, int64_t end, int64_t inc) {
      for(int y=start;y<end;y+=inc) {
        instV->execute(&d[vlen*2*y], &tBuf[vlen*2*y], MAGIC_, MAGIC2D_);
      }
    });
#endif

    transposeMT<real, real2, MAXSHIFT, MAXBUTWIDTH>(d, tBuf, log2hlen, log2vlen);
  } else {
    for(int y=0;y<vlen;y++) {
      instH->execute(&s[hlen*2*y], &tBuf[hlen*2*y], MAGIC_, MAGIC2D_);
    }

    transpose<real, real2, MAXSHIFT, MAXBUTWIDTH>(d, tBuf, log2vlen, log2hlen);

    for(int y=0;y<hlen;y++) {
      instV->execute(&d[vlen*2*y], &tBuf[vlen*2*y], MAGIC_, MAGIC2D_);
    }

    transpose<real, real2, MAXSHIFT, MAXBUTWIDTH>(d, tBuf, log2hlen, log2vlen);
  }
}

//

EXPORT SleefDFT *SleefDFT_double_init1d(uint32_t n, const double *in, double *out, uint64_t mode) {
  SleefDFT *p = (SleefDFT *)calloc(1, sizeof(SleefDFT));
  p->double_ = new SleefDFTXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>(n, in, out, mode, "double",
    1, MAGIC_DOUBLE, MINSHIFTDP, getInt_double, getPtr_double, Sleef_sincospi_u05,
    dftf_double, dftb_double, tbutf_double, tbutb_double, butf_double, butb_double,
    realSub0_double, realSub1_double, tbutfs_double, tbutbs_double
  );
  p->magic = p->double_->magic;
  return p;
}

EXPORT SleefDFT *SleefDFT_double_init2d(uint32_t vlen, uint32_t hlen, const double *in, double *out, uint64_t mode) {
  SleefDFT *p = (SleefDFT *)calloc(1, sizeof(SleefDFT));
  p->double2d_ = new SleefDFT2DXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>(vlen, hlen, in, out, mode, "double",
    1, MAGIC_DOUBLE, MAGIC2D_DOUBLE, MINSHIFTDP, getInt_double, getPtr_double, Sleef_sincospi_u05,
    dftf_double, dftb_double, tbutf_double, tbutb_double, butf_double, butb_double,
    realSub0_double, realSub1_double, tbutfs_double, tbutbs_double
  );
  p->magic = p->double2d_->magic;
  return p;
}

EXPORT void SleefDFT_double_execute(SleefDFT *p, const double *s0, double *d0) {
  switch(p->magic) {
  case MAGIC_DOUBLE:
    p->double_->execute(s0, d0, MAGIC_DOUBLE, MAGIC2D_DOUBLE);
    break;
  case MAGIC2D_DOUBLE:
    p->double2d_->execute(s0, d0, MAGIC_DOUBLE, MAGIC2D_DOUBLE);
    break;
  default:
    abort();
  }
}

EXPORT SleefDFT *SleefDFT_float_init1d(uint32_t n, const float *in, float *out, uint64_t mode) {
  SleefDFT *p = (SleefDFT *)calloc(1, sizeof(SleefDFT));
  p->float_ = new SleefDFTXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>(n, in, out, mode, "float",
    2, MAGIC_FLOAT, MINSHIFTSP, getInt_float, getPtr_float, Sleef_sincospif_u05,
    dftf_float, dftb_float, tbutf_float, tbutb_float, butf_float, butb_float,
    realSub0_float, realSub1_float, tbutfs_float, tbutbs_float
  );
  p->magic = p->float_->magic;
  return p;
}

EXPORT SleefDFT *SleefDFT_float_init2d(uint32_t vlen, uint32_t hlen, const float *in, float *out, uint64_t mode) {
  SleefDFT *p = (SleefDFT *)calloc(1, sizeof(SleefDFT));
  p->float2d_ = new SleefDFT2DXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>(vlen, hlen, in, out, mode, "float",
    2, MAGIC_FLOAT, MAGIC2D_FLOAT, MINSHIFTSP, getInt_float, getPtr_float, Sleef_sincospif_u05,
    dftf_float, dftb_float, tbutf_float, tbutb_float, butf_float, butb_float,
    realSub0_float, realSub1_float, tbutfs_float, tbutbs_float
  );
  p->magic = p->float2d_->magic;
  return p;
}

EXPORT void SleefDFT_float_execute(SleefDFT *p, const float *s0, float *d0) {
  switch(p->magic) {
  case MAGIC_FLOAT:
    p->float_->execute(s0, d0, MAGIC_FLOAT, MAGIC2D_FLOAT);
    break;
  case MAGIC2D_FLOAT:
    p->float2d_->execute(s0, d0, MAGIC_FLOAT, MAGIC2D_FLOAT);
    break;
  default:
    abort();
  }
}

EXPORT void SleefDFT_execute(SleefDFT *p, const void *s0, void *d0) {
  switch(p->magic) {
  case MAGIC_DOUBLE:
    p->double_->execute((const double *)s0, (double *)d0, MAGIC_DOUBLE, MAGIC2D_DOUBLE);
    break;
  case MAGIC2D_DOUBLE:
    p->double2d_->execute((const double *)s0, (double *)d0, MAGIC_DOUBLE, MAGIC2D_DOUBLE);
    break;
  case MAGIC_FLOAT:
    p->float_->execute((const float *)s0, (float *)d0, MAGIC_FLOAT, MAGIC2D_FLOAT);
    break;
  case MAGIC2D_FLOAT:
    p->float2d_->execute((const float *)s0, (float *)d0, MAGIC_FLOAT, MAGIC2D_FLOAT);
    break;
  default:
    abort();
  }
}
