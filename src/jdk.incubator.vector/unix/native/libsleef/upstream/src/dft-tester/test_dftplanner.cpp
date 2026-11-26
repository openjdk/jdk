//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <iostream>
#include <vector>
#include <string>

#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <cmath>

#include "sleef.h"
#include "sleefdft.h"

using namespace std;

vector<string> doTransform(int mode) {
  SleefDFT *p;
  vector<string> v;
  vector<char> s(1024);

  double *din  = (double *)Sleef_malloc(2048*64*2 * sizeof(double));
  double *dout = (double *)Sleef_malloc(2048*64*2 * sizeof(double));

  float *fin  = (float *)Sleef_malloc(2048*64*2 * sizeof(double));
  float *fout = (float *)Sleef_malloc(2048*64*2 * sizeof(double));

  //

  p = SleefDFT_double_init1d(1024, din, dout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("1d double 1024 : " + string(s.data()));
  SleefDFT_dispose(p);

  p = SleefDFT_double_init1d(512, din, dout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("1d double 512 : " + string(s.data()));
  SleefDFT_dispose(p);

  p = SleefDFT_float_init1d(1024, fin, fout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("1d float 1024 : " + string(s.data()));
  SleefDFT_dispose(p);

  p = SleefDFT_float_init1d(512, fin, fout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("1d float 512 : " + string(s.data()));
  SleefDFT_dispose(p);

  p = SleefDFT_double_init2d(2048, 64, din, dout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("2d double 2048x64 : " + string(s.data()));
  SleefDFT_dispose(p);

  p = SleefDFT_double_init2d(128, 128, din, dout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("2d double 128x128 : " + string(s.data()));
  SleefDFT_dispose(p);

  p = SleefDFT_float_init2d(2048, 64, fin, fout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("2d float 2048x64 : " + string(s.data()));
  SleefDFT_dispose(p);

  p = SleefDFT_float_init2d(128, 128, fin, fout, mode);
  SleefDFT_getPath(p, s.data(), s.size());
  v.push_back("2d float 128x128 : " + string(s.data()));
  SleefDFT_dispose(p);

  Sleef_free(din);
  Sleef_free(dout);
  Sleef_free(fin);
  Sleef_free(fout);

  return v;
}

void compare(vector<string> &runa, vector<string> &runb) {
  if (runa.size() != runb.size()) {
    cerr << "Lengths do not match" << endl;
    exit(-1);
  }
  for(size_t i=0;i<runa.size();i++) {
    if (runa[i] != runb[i]) {
      cerr << "Paths do not match" << endl;
      cerr << runa[i] << endl;
      cerr << runb[i] << endl;
      exit(-1);
    }
  }
}

int main(int argc, char **argv) {
  if (argc < 3) exit(-1);

  string fn1 = argv[1], fn2 = argv[2];

#ifdef MEASURE
#ifdef MULTITHREAD
  int mode = SLEEF_MODE_MEASURE | SLEEF_MODE_VERBOSE;
#else
  int mode = SLEEF_MODE_MEASURE | SLEEF_MODE_VERBOSE | SLEEF_MODE_NO_MT;
#endif
#else
#ifdef MULTITHREAD
  int mode = SLEEF_MODE_ESTIMATE | SLEEF_MODE_VERBOSE;
#else
  int mode = SLEEF_MODE_ESTIMATE | SLEEF_MODE_VERBOSE | SLEEF_MODE_NO_MT;
#endif
#endif

  int planMode = argc == 1 ? 0 : SLEEF_PLAN_AUTOMATIC;

  //

  cerr << "Run 0" << endl;

  SleefDFT_setPlanFilePath(fn1.c_str(), NULL, planMode);

  auto run0 = doTransform(mode);

  cerr << endl << "Run 1" << endl;

  SleefDFT_setPlanFilePath(NULL, NULL, SLEEF_PLAN_RESET);
  SleefDFT_setPlanFilePath(fn2.c_str(), NULL, planMode);

  auto run1 = doTransform(mode);

  cerr << endl << "Run 2" << endl;

  SleefDFT_setPlanFilePath(fn1.c_str(), NULL, planMode);

  auto run2 = doTransform(mode);

  compare(run0, run2);

#ifdef MEASURE
  SleefDFT_savePlan("manual.plan");
#endif

  cerr << endl << "Run 3" << endl;

  SleefDFT_setPlanFilePath(NULL, NULL, SLEEF_PLAN_RESET);
  SleefDFT_setPlanFilePath(fn2.c_str(), NULL, planMode);

  auto run3 = doTransform(mode);

  compare(run1, run3);

#ifdef MEASURE
  cerr << endl << "Run 4" << endl;

  SleefDFT_setPlanFilePath(NULL, NULL, SLEEF_PLAN_RESET);
  SleefDFT_setPlanFilePath("manual.plan", NULL, planMode);

  auto run4 = doTransform(mode);

  compare(run0, run4);
#endif

  cerr << "OK" << endl;

  exit(0);
}
