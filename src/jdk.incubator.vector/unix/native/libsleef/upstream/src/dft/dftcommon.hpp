//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <iostream>
#include <string>
#include <vector>
#include <climits>
#include <unordered_map>
#include <tuple>
#include <utility>
#include <mutex>
#include <functional>

using namespace std;

#include "dispatchparam.h"

#define MAGIC_FLOAT 0x31415926
#define MAGIC_DOUBLE 0x27182818
#define MAGIC2D_FLOAT 0x53589793
#define MAGIC2D_DOUBLE 0x28459045

#define MINSHIFTDP 1
#define MINSHIFTSP 1
#define MAXSHIFTDP 1
#define MAXSHIFTSP 1

#define CONFIG_STREAM 1
#define CONFIG_MT 2

#define SLEEF_MODE2_MT1D       (1 << 0)
#define SLEEF_MODE3_MT2D       (1 << 0)

#define PLANFILEID "SLEEFDFT1"
#define ENVVAR "SLEEFDFTPLAN"

#define SLEEF_MODE_MEASUREBITS (7 << 20)
#define SLEEF_MODE_INTERNAL_2D (1ULL << 40)

#define GETINT_VECWIDTH 100
#define GETINT_DFTPRIORITY 101

#define MAXLOG2LEN 32

#define INFINITY_ (1e+300 * 1e+300)

namespace sleef_internal {
  class Action {
  public:
    int config, level, N;

    Action(const Action& a) = default;

    Action(int config_, int level_, int N_) : config(config_), level(level_), N(N_) {}

    bool operator==(const Action& rhs) const {
      return config == rhs.config && level == rhs.level && N == rhs.N;
    }
    bool operator!=(const Action& rhs) const { return !(*this == rhs); }

    friend ostream& operator<<(ostream &os, const Action &ac) {
      return os << "[" << ac.config << ", " << ac.level << ", " << ac.N << "]";
    }
  };

  template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
  struct SleefDFTXX {
    int magic;
    const int baseTypeID;
    const real * const in;
    real * const out;
    const int nThread;
    const uint32_t log2len;
    const uint64_t mode;
    const int minshift;

    uint64_t mode2 = 0, mode3 = 0;

    //

    mutex mtx;

    real **tbl[MAXBUTWIDTH+1];
    real *rtCoef0, *rtCoef1;
    uint32_t **perm;

    unordered_map<thread::id, pair<real *, real *>> xn;

    int isa = 0;
    int planMode = 0;

    int vecwidth, log2vecwidth;

    bool executable[CONFIGMAX][MAXLOG2LEN][MAXLOG2LEN];
    vector<Action> bestPath;

    FILE *verboseFP = NULL;

    void (*(* const DFTF)[ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int);
    void (*(* const DFTB)[ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int);
    void (*(* const TBUTF)[ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int);
    void (*(* const TBUTB)[ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int);
    void (*(* const BUTF)[ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int);
    void (*(* const BUTB)[ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int);
    void (** const REALSUB0)(real *, const real *, const int, const real *, const real *);
    void (** const REALSUB1)(real *, const real *, const int, const real *, const real *, const int);
    void (*(* const TBUTFS)[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const real *, const int);
    void (*(* const TBUTBS)[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const real *, const int);

    SleefDFTXX(uint32_t n, const real *in, real *out, uint64_t mode, const char *baseTypeString, int BASETYPEID_, int MAGIC_, int minshift_,
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
               );

    ~SleefDFTXX();

    void dispatch(const int N, real *d, const real *s, const int level, const int config);
    void execute(const real *s0, real *d0, int MAGIC_, int MAGIC2D_);
    void freeTables();
    void generatePerm(const vector<Action> &);

    void measurementRun(real *d, const real *s, const vector<Action> &path, uint64_t niter);
    double measurePath(const vector<Action> &path, uint64_t minTime);
    void searchForBestPath(int nPaths);
    void searchForRandomPath();
    bool measure(bool randomize);

    vector<Action> parsePathStr(const char *);

    string planKeyString(string = "");
    bool loadMeasurementResults();
    void saveMeasurementResults();
    void setPath(const char *pathStr);
    string getPath();
  };

  template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
  struct SleefDFT2DXX {
    int magic;
    uint64_t mode, mode2, mode3;
    int baseTypeID;
    const real *in;
    real *out;

    //

    int32_t hlen, vlen;
    int32_t log2hlen, log2vlen;
    bool planMT;
    real *tBuf;

    SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> *instH, *instV;

    FILE *verboseFP = NULL;

    SleefDFT2DXX(uint32_t vlen, uint32_t hlen, const real *in, real *out, uint64_t mode, const char *baseTypeString,
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
                 );

    ~SleefDFT2DXX();

    void execute(const real *s0, real *d0, int MAGIC_, int MAGIC2D_);
    pair<uint64_t, uint64_t> measureTranspose();
    double measurePath(SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> *inst, bool mt,
                       const vector<Action> &path, uint32_t hlen, uint32_t vlen, uint64_t minTime);
    pair<vector<Action>, double> searchForBestPath(SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH> *inst, bool mt, uint32_t hlen, uint32_t vlen, int nPaths);

    string planKeyString(string = "");
    bool loadMeasurementResults();
    void saveMeasurementResults();
    void setPath(const char *pathStr);
    string getPath();
  };

  class PlanManager {
    string dftPlanFilePath;
    uint64_t planMode_ = SLEEF_PLAN_REFERTOENVVAR;

    string planID;
    tuple<unordered_map<string, unordered_map<string, string>>, string> thePlan;

  public:
    PlanManager();

    recursive_mutex mtx;

    uint64_t planMode() { return planMode_; }

    void setPlanFilePath(const char *path, const char *arch, uint64_t mode);
    void loadPlanFromFile();
    bool savePlanToFile(const string &fn);
    bool savePlanToFile();

    bool loadAndPutToFile(const string& key, const string& value);

    string get(const string& key);
    void put(const string& key, const string& value);
  };

  extern PlanManager planManager;
  extern FILE *defaultVerboseFP;

  void parallelFor(int64_t start_, int64_t end_, int64_t inc_, std::function<void(int64_t, int64_t, int64_t)> func_);
}

using namespace sleef_internal;

struct SleefDFT {
  uint32_t magic;
  union {
    SleefDFTXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP> *double_;
    SleefDFTXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP> *float_;
    SleefDFT2DXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP> *double2d_;
    SleefDFT2DXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP> *float2d_;
  };
};

template <>
struct std::hash<Action> {
  size_t operator()(const Action &a) const {
    size_t u = 0;
    u ^= a.config;
    u = (u << 7) | (u >> ((sizeof(u)*8)-7));
    u ^= a.level;
    u = (u << 7) | (u >> ((sizeof(u)*8)-7));
    u ^= a.N;
    return u;
  }
};
