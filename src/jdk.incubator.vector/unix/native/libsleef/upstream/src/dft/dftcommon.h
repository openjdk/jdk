//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#define CONFIGMAX 4
#define CONFIG_STREAM 1
#define CONFIG_MT 2

#define MAXLOG2LEN 32

typedef struct SleefDFT {
  uint32_t magic;
  uint64_t mode, mode2, mode3;
  int baseTypeID;
  const void *in;
  void *out;

  union {
    struct {
      uint32_t log2len;

      void **tbl[MAXBUTWIDTH+1];
      void *rtCoef0, *rtCoef1;
      uint32_t **perm;

      void **x0, **x1;

      int isa;
      int planMode;

      int vecwidth, log2vecwidth;
      int nThread;

      uint64_t tm[CONFIGMAX][(MAXBUTWIDTH+1)*32];
      uint64_t bestTime;
      int16_t bestPath[32], bestPathConfig[32], pathLen;
    };

    struct {
      int32_t hlen, vlen;
      int32_t log2hlen, log2vlen;
      uint64_t tmNoMT, tmMT;
      struct SleefDFT *instH, *instV;
      void *tBuf;
    };
  };
} SleefDFT;

#define SLEEF_MODE2_MT1D       (1 << 0)
#define SLEEF_MODE3_MT2D       (1 << 0)

#define PLANFILEID "SLEEFDFT0\n"
#define ENVVAR "SLEEFDFTPLAN"

#define SLEEF_MODE_MEASUREBITS (3 << 20)

void freeTables(SleefDFT *p);
uint32_t ilog2(uint32_t q);

//int PlanManager_loadMeasurementResultsB(SleefDFT *p);
//void PlanManager_saveMeasurementResultsB(SleefDFT *p, int butStat);
int PlanManager_loadMeasurementResultsT(SleefDFT *p);
void PlanManager_saveMeasurementResultsT(SleefDFT *p);
int PlanManager_loadMeasurementResultsP(SleefDFT *p, int pathCat);
void PlanManager_saveMeasurementResultsP(SleefDFT *p, int pathCat);

#define GETINT_VECWIDTH 100
#define GETINT_DFTPRIORITY 101
