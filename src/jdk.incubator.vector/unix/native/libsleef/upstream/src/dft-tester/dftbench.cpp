//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <cassert>
#include <cmath>
#include <iostream>
#include <complex>
#include <ctime>
#include <chrono>
#include <thread>
#include <memory>
#include <vector>

#include <fftw3.h>
#include <omp.h>

#include "sleef.h"
#include "sleefdft.h"

using namespace std;

#if BASETYPEID == 1
typedef double xreal;
#define FFTW_COMPLEX fftw_complex
#define FFTW_PLAN_WITH_NTHREADS fftw_plan_with_nthreads
#define FFTW_PLAN fftw_plan
#define FFTW_MALLOC fftw_malloc
#define FFTW_FREE fftw_free
#define FFTW_PLAN_DFT_1D fftw_plan_dft_1d
#define FFTW_PLAN_DFT_2D fftw_plan_dft_2d
#define FFTW_EXECUTE fftw_execute
#define FFTW_DESTROY_PLAN fftw_destroy_plan
#define FFTW_CLEANUP fftw_cleanup
#define SLEEFDFT_INIT1D SleefDFT_double_init1d
#define SLEEFDFT_INIT2D SleefDFT_double_init2d
#elif BASETYPEID == 2
typedef float xreal;
#define FFTW_COMPLEX fftwf_complex
#define FFTW_PLAN_WITH_NTHREADS fftwf_plan_with_nthreads
#define FFTW_PLAN fftwf_plan
#define FFTW_MALLOC fftwf_malloc
#define FFTW_FREE fftwf_free
#define FFTW_PLAN_DFT_1D fftwf_plan_dft_1d
#define FFTW_PLAN_DFT_2D fftwf_plan_dft_2d
#define FFTW_EXECUTE fftwf_execute
#define FFTW_DESTROY_PLAN fftwf_destroy_plan
#define FFTW_CLEANUP fftwf_cleanup
#define SLEEFDFT_INIT1D SleefDFT_float_init1d
#define SLEEFDFT_INIT2D SleefDFT_float_init2d
#else
#error BASETYPEID not set
#endif

static uint64_t timens() {
  return std::chrono::duration_cast<std::chrono::nanoseconds>
    (std::chrono::high_resolution_clock::now() - std::chrono::high_resolution_clock::from_time_t(0)).count();
}

template<typename cplx>
class FFTFramework {
public:
  virtual void execute() = 0;
  virtual cplx* getInPtr() = 0;
  virtual cplx* getOutPtr() = 0;
  virtual ~FFTFramework() {};

  int64_t niter(int64_t ns) {
    int64_t niter = 10, t0, t1;

    for(;;) {
      t0 = timens();
      for(int64_t i=0;i<niter;i++) execute();
      t1 = timens();
      if (t1 - t0 > 1000LL * 1000 * 10) break;
      niter *= 2;
    }

    return 1 + int64_t((double)niter * ns / (t1 - t0));
  }
};

template<typename cplx>
class FWSleefDFT : public FFTFramework<cplx> {
  const int n, m;
  cplx* in;
  cplx* out;
  SleefDFT *plan;

public:
  FWSleefDFT(int n_, int m_, bool forward, bool mt, bool check) : n(n_), m(m_) {
    SleefDFT_setDefaultVerboseFP(stderr);
    SleefDFT_setPlanFilePath(NULL, NULL, SLEEF_PLAN_RESET);
    in  = (cplx*)Sleef_malloc(sizeof(cplx) * n * m);
    out = (cplx*)Sleef_malloc(sizeof(cplx) * n * m);

    if (!in || !out) {
      cerr << "Sleef_malloc failed" << endl;
      exit(-1);
    }

    uint64_t mode = check ? SLEEF_MODE_ESTIMATE : SLEEF_MODE_MEASURE;
    mode |= forward ? SLEEF_MODE_FORWARD : SLEEF_MODE_BACKWARD;
    mode |= mt ? 0 : SLEEF_MODE_NO_MT;
    //mode |= SLEEF_MODE_VERBOSE;

    if (m == 1) {
      plan = SLEEFDFT_INIT1D(n, (xreal*)in, (xreal*)out, mode);
    } else {
      plan = SLEEFDFT_INIT2D(n, m, (xreal*)in, (xreal*)out, mode);
    }
  }

  string getPath() {
    vector<char> pathstr(1024);
    SleefDFT_getPath(plan, pathstr.data(), pathstr.size());
    return pathstr.data();
  }

  ~FWSleefDFT() {
    SleefDFT_dispose(plan);
    Sleef_free(out);
    Sleef_free(in);
  }

  cplx* getInPtr () { return in ; }
  cplx* getOutPtr() { return out; }

  void execute() { SleefDFT_execute(plan, NULL, NULL); }
};

template<typename cplx>
class FWFFTW3 : public FFTFramework<cplx> {
  const int n, m;
  cplx* in;
  cplx* out;
  FFTW_PLAN plan;

public:
  FWFFTW3(int n_, int m_, bool forward, bool mt, bool check) : n(n_), m(m_) {
    //FFTW_CLEANUP();
    FFTW_PLAN_WITH_NTHREADS(mt ? thread::hardware_concurrency() : 1);
    in  = (cplx*)FFTW_MALLOC(sizeof(FFTW_COMPLEX) * n * m);
    out = (cplx*)FFTW_MALLOC(sizeof(FFTW_COMPLEX) * n * m);
    unsigned flags = check ? FFTW_ESTIMATE : FFTW_MEASURE;
    if (m == 1) {
      plan = FFTW_PLAN_DFT_1D(n, (FFTW_COMPLEX*)in, (FFTW_COMPLEX*)out, forward ? FFTW_FORWARD : FFTW_BACKWARD, flags);
    } else {
      plan = FFTW_PLAN_DFT_2D(n, m, (FFTW_COMPLEX*)in, (FFTW_COMPLEX*)out, forward ? FFTW_FORWARD : FFTW_BACKWARD, flags);
    }
  }

  ~FWFFTW3() {
    FFTW_DESTROY_PLAN(plan);
    FFTW_FREE(out);
    FFTW_FREE(in);
  }

  cplx* getInPtr() { return in; }
  cplx* getOutPtr() { return out; }

  void execute() { FFTW_EXECUTE(plan); }
};

int main(int argc, char **argv) {
  if (argc == 1) {
    fprintf(stderr, "%s <log2n> <log2m> <measurement time in ms> <nrepeat>\n", argv[0]);
    exit(-1);
  }

  fftw_init_threads();

  double measureTimeMillis = 3000;
  if (argc >= 4) measureTimeMillis = atof(argv[3]);

  bool forward = true;

  int log2n = atoi(argv[1]);
  if (log2n < 0) {
    forward = false;
    log2n = -log2n;
  }

  const int n = 1 << log2n;

  const int log2m = argc >= 3 ? atoi(argv[2]) : 0;
  const int m = 1 << log2m;

  cerr << "n = " << n << ", m = " << m << ", " << (forward ? "forward" : "backward") << endl;

  const int nrepeat = argc >= 5 ? atoi(argv[4]) : 1;

  vector<double> mflops_sleefdftst, mflops_fftwst, mflops_sleefdftmt, mflops_fftwmt;

  vector<complex<xreal>> v(n * m);
  for(int i=0;i<n * m;i++) {
    v[i] = (2.0 * (rand() / (double)RAND_MAX) - 1) + (2.0 * (rand() / (double)RAND_MAX) - 1) * 1i;
  }

  {
    // Check if we are really computing the same values

    auto sleefdft = make_shared<FWSleefDFT<complex<xreal>>>(n, m, forward, true , true);
    auto fftw     = make_shared<FWFFTW3   <complex<xreal>>>(n, m, forward, false, true);

    complex<xreal> *in0  = sleefdft->getInPtr();
    complex<xreal> *out0 = sleefdft->getOutPtr();
    complex<xreal> *in1  = fftw->getInPtr();
    complex<xreal> *out1 = fftw->getOutPtr();

    for(int i=0;i<n * m;i++) in0[i] = in1[i] = v[i];

    sleefdft->execute();
    fftw    ->execute();

    for(int i=0;i<n * m;i++) {
      if (std::real(abs((out0[i] - out1[i]) * (out0[i] - out1[i]))) > 0.1) {
        cerr << "NG " << i << " : " << out0[i] << ", " << out1[i] << endl;
        exit(-1);
      }
    }

    cerr << "Check OK" << endl;
  }

  for(int nr = 0;nr < nrepeat;nr++) {
    cerr << endl;
#if BASETYPEID == 1
    cerr << "DP ";
#elif BASETYPEID == 2
    cerr << "SP ";
#endif
    cerr << "n = 2^" << log2n << " = " << n << ", m = 2^" << log2m << " = " << m << ", nr = " << nr << endl;

    //

    {
      cerr << "Planning SleefDFT ST ... ";
      int64_t ptm0 = timens();
      auto sleefdftst = make_shared<FWSleefDFT<complex<xreal>>>(n, m, forward, false, false);
      int64_t ptm1 = timens();
      cerr << ((ptm1 - ptm0) / 1000.0 / 1000.0) << "ms" << endl;

      cerr << sleefdftst->getPath() << endl;

      complex<xreal> *in0  = sleefdftst->getInPtr();
      for(int i=0;i<n * m;i++) in0[i] = v[i];

      auto niter = sleefdftst->niter(1000LL * 1000 * measureTimeMillis);

      cerr << "SleefDFT ST niter = " << niter << endl;

      for(int64_t i=0;i<niter/10;i++) sleefdftst->execute(); // warm up

      int64_t tm0 = timens();
      for(int64_t i=0;i<niter;i++) sleefdftst->execute();
      int64_t tm1 = timens();

      double mflops = 5 * n * log2n / ((tm1 - tm0) / (double(niter)*1000));
      if (m != 1) mflops *= m * log2m;

      fprintf(stderr, "%g Mflops\n", mflops);

      mflops_sleefdftst.push_back(mflops);
    }

    //

    {
      cerr << "Planning FFTW ST ... ";
      int64_t ptm0 = timens();
      auto fftwst = make_shared<FWFFTW3<complex<xreal>>>(n, m, forward, false, false);
      int64_t ptm1 = timens();
      cerr << ((ptm1 - ptm0) / 1000.0 / 1000.0) << "ms" << endl;

      complex<xreal> *in0  = fftwst->getInPtr();
      for(int i=0;i<n * m;i++) in0[i] = v[i];

      auto niter  = fftwst->niter(1000LL * 1000 * measureTimeMillis);

      cerr << "FFTW ST niter = " << niter << endl;

      for(int64_t i=0;i<niter/10;i++) fftwst->execute(); // warm up

      int64_t tm0 = timens();
      for(int64_t i=0;i<niter;i++) fftwst->execute();
      int64_t tm1 = timens();

      double mflops = 5 * n * log2n / ((tm1 - tm0) / (double(niter)*1000));
      if (m != 1) mflops *= m * log2m;

      fprintf(stderr, "%g Mflops\n", mflops);

      mflops_fftwst.push_back(mflops);
    }

    //

    {
      cerr << "Planning SleefDFT MT ... ";
      int64_t ptm0 = timens();
      auto sleefdftmt = make_shared<FWSleefDFT<complex<xreal>>>(n, m, forward, true, false);
      int64_t ptm1 = timens();
      cerr << ((ptm1 - ptm0) / 1000.0 / 1000.0) << "ms" << endl;

      cerr << sleefdftmt->getPath() << endl;

      complex<xreal> *in0  = sleefdftmt->getInPtr();
      for(int i=0;i<n * m;i++) in0[i] = v[i];

      auto niter = sleefdftmt->niter(1000LL * 1000 * measureTimeMillis);

      cerr << "SleefDFT MT niter = " << niter << endl;

      for(int64_t i=0;i<niter/10;i++) sleefdftmt->execute(); // warm up

      int64_t tm0 = timens();
      for(int64_t i=0;i<niter;i++) sleefdftmt->execute();
      int64_t tm1 = timens();

      double mflops = 5 * n * log2n / ((tm1 - tm0) / (double(niter)*1000));
      if (m != 1) mflops *= m * log2m;

      fprintf(stderr, "%g Mflops\n", mflops);

      mflops_sleefdftmt.push_back(mflops);
    }

    //

    {
      cerr << "Planning FFTW MT ... ";
      int64_t ptm0 = timens();
      auto fftwmt = make_shared<FWFFTW3<complex<xreal>>>(n, m, forward, true, false);
      int64_t ptm1 = timens();
      cerr << ((ptm1 - ptm0) / 1000.0 / 1000.0) << "ms" << endl;

      complex<xreal> *in0  = fftwmt->getInPtr();
      for(int i=0;i<n * m;i++) in0[i] = v[i];

      auto niter  = fftwmt->niter(1000LL * 1000 * measureTimeMillis);

      cerr << "FFTW MT niter = " << niter << endl;

      for(int64_t i=0;i<niter/10;i++) fftwmt->execute(); // warm up

      int64_t tm0 = timens();
      for(int64_t i=0;i<niter;i++) fftwmt->execute();
      int64_t tm1 = timens();

      double mflops = 5 * n * log2n / ((tm1 - tm0) / (double(niter)*1000));
      if (m != 1) mflops *= m * log2m;

      fprintf(stderr, "%g Mflops\n", mflops);

      mflops_fftwmt.push_back(mflops);
    }
  }

  cerr << endl;

  cout << log2n << ", " << log2m << ", ";

  {
    double f = 0;
    for(auto a : mflops_sleefdftst) {
      if (a > f) f = a;
    }
    cout << f << ", ";
  }

  {
    double f = 0;
    for(auto a : mflops_sleefdftmt) {
      if (a > f) f = a;
    }
    cout << f << ", ";
  }

  {
    double f = 0;
    for(auto a : mflops_fftwst) {
      if (a > f) f = a;
    }
    cout << f << ", ";
  }

  {
    double f = 0;
    for(auto a : mflops_fftwmt) {
      if (a > f) f = a;
    }
    cout << f << endl;
  }

  //

  exit(0);
}
