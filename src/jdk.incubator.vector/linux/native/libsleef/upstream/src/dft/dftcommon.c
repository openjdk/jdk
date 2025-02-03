//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <ctype.h>
#include <inttypes.h>
#include <assert.h>

#include <math.h>

#ifdef _OPENMP
#include <omp.h>
#endif

#include "misc.h"
#include "sleef.h"

#define IMPORT_IS_EXPORT
#include "sleefdft.h"
#include "dispatchparam.h"
#include "dftcommon.h"
#include "common.h"
#include "arraymap.h"

#define MAGIC_FLOAT 0x31415926
#define MAGIC_DOUBLE 0x27182818

#define MAGIC2D_FLOAT 0x22360679
#define MAGIC2D_DOUBLE 0x17320508

const char *configStr[] = { "ST", "ST stream", "MT", "MT stream" };

static int parsePathStr(char *p, int *path, int *config, int pathLenMax, int log2len) {
  int pathLen = 0, l2l = 0;

  for(;;) {
    while(*p == ' ') p++;
    if (*p == '\0') break;
    if (!isdigit((int)*p)) return -1;

    pathLen++;
    if (pathLen >= pathLenMax) return -2;

    int n = 0;
    while(isdigit((int)*p)) n = n * 10 + *p++ - '0';

    if (n > MAXBUTWIDTH) return -6;
    path[pathLen-1] = n;
    l2l += n;
    config[pathLen-1] = 0;

    if (*p != '(') continue;

    int c;
    for(c=3;c>=0;c--) if (strncmp(p+1, configStr[c], strlen(configStr[c])) == 0) break;
    if (c == -1) return -3;
    p += strlen(configStr[c]) + 1;
    if (*p != ')') return -4;
    p++;

    config[pathLen-1] = c;
  }

  if (l2l != log2len) return -5;

  return pathLen;
}

EXPORT void SleefDFT_setPath(SleefDFT *p, char *pathStr) {
  assert(p != NULL && (p->magic == MAGIC_FLOAT || p->magic == MAGIC_DOUBLE));

  int path[32], config[32];
  int pathLen = parsePathStr(pathStr, path, config, 31, p->log2len);

  if (pathLen < 0) {
    if ((p->mode & SLEEF_MODE_VERBOSE) != 0) printf("Error %d in parsing path string : %s\n", pathLen, pathStr);
    return;
  }

  for(uint32_t j = 0;j <= p->log2len;j++) p->bestPath[j] = 0;

  for(int level = p->log2len, j=0;level > 0 && j < pathLen;) {
    p->bestPath[level] = path[j];
    p->bestPathConfig[level] = config[j];
    level -= path[j];
    j++;
  }

  p->pathLen = 0;
  for(int j = p->log2len;j >= 0;j--) if (p->bestPath[j] != 0) p->pathLen++;

  if ((p->mode & SLEEF_MODE_VERBOSE) != 0) {
    printf("Set path : ");
    for(int j = p->log2len;j >= 0;j--) if (p->bestPath[j] != 0) printf("%d(%s) ", p->bestPath[j], configStr[p->bestPathConfig[j]]);
    printf("\n");
  }
}

void freeTables(SleefDFT *p) {
  for(int N=1;N<=MAXBUTWIDTH;N++) {
    for(uint32_t level=N;level<=p->log2len;level++) {
      Sleef_free(p->tbl[N][level]);
    }
    free(p->tbl[N]);
    p->tbl[N] = NULL;
  }
}

EXPORT void SleefDFT_dispose(SleefDFT *p) {
  if (p != NULL && (p->magic == MAGIC2D_FLOAT || p->magic == MAGIC2D_DOUBLE)) {
    Sleef_free(p->tBuf);
    SleefDFT_dispose(p->instH);
    if (p->hlen != p->vlen) SleefDFT_dispose(p->instV);

    p->magic = 0;
    free(p);
    return;
  }

  assert(p != NULL && (p->magic == MAGIC_FLOAT || p->magic == MAGIC_DOUBLE));

  if (p->log2len <= 1) {
    p->magic = 0;
    free(p);
    return;
  }

  if ((p->mode & SLEEF_MODE_REAL) != 0) {
    Sleef_free(p->rtCoef1);
    Sleef_free(p->rtCoef0);
    p->rtCoef0 = p->rtCoef1 = NULL;
  }

  for(int level = p->log2len;level >= 1;level--) {
    Sleef_free(p->perm[level]);
  }
  free(p->perm);
  p->perm = NULL;

  freeTables(p);

  p->magic = 0;
  free(p);
}

uint32_t ilog2(uint32_t q) {
  static const uint32_t tab[] = {0,1,2,2,3,3,3,3,4,4,4,4,4,4,4,4};
  uint32_t r = 0,qq;

  if (q & 0xffff0000) r = 16;

  q >>= r;
  qq = q | (q >> 1);
  qq |= (qq >> 2);
  qq = ((qq & 0x10) >> 4) | ((qq & 0x100) >> 7) | ((qq & 0x1000) >> 10);

  return r + tab[qq] * 4 + tab[q >> (tab[qq] * 4)] - 1;
}

//

char *dftPlanFilePath = NULL;
char *archID = NULL;
uint64_t planMode = SLEEF_PLAN_REFERTOENVVAR;
ArrayMap *planMap = NULL;
int planFilePathSet = 0, planFileLoaded = 0;
#ifdef _OPENMP
omp_lock_t planMapLock;
int planMapLockInitialized = 0;
#endif

static void initPlanMapLock() {
#ifdef _OPENMP
#pragma omp critical
  {
    if (!planMapLockInitialized) {
      planMapLockInitialized = 1;
      omp_init_lock(&planMapLock);
    }
  }
#endif
}

static void planMap_clear() {
  if (planMap != NULL) ArrayMap_dispose(planMap);
  planMap = NULL;
}

EXPORT void SleefDFT_setPlanFilePath(const char *path, const char *arch, uint64_t mode) {
  initPlanMapLock();

  if ((mode & SLEEF_PLAN_RESET) != 0) {
    planMap_clear();
    planFileLoaded = 0;
    planFilePathSet = 0;
  }

  if (dftPlanFilePath != NULL) free(dftPlanFilePath);
  if (path != NULL) {
    dftPlanFilePath = malloc(strlen(path)+10);
    strcpy(dftPlanFilePath, path);
  } else {
    dftPlanFilePath = NULL;
  }

  if (archID != NULL) free(archID);
  if (arch == NULL) arch = Sleef_getCpuIdString();
  archID = malloc(strlen(arch)+10);
  strcpy(archID, arch);

  planMode = mode;
  planFilePathSet = 1;
}

static void loadPlanFromFile() {
  if (planFilePathSet == 0 && (planMode & SLEEF_PLAN_REFERTOENVVAR) != 0) {
    char *s = getenv(ENVVAR);
    if (s != NULL) SleefDFT_setPlanFilePath(s, NULL, planMode);
  }

  if (planMap != NULL) ArrayMap_dispose(planMap);

  if (dftPlanFilePath != NULL && (planMode & SLEEF_PLAN_RESET) == 0) {
    planMap = ArrayMap_load(dftPlanFilePath, archID, PLANFILEID, (planMode & SLEEF_PLAN_NOLOCK) == 0);
  }

  if (planMap == NULL) planMap = initArrayMap();

  planFileLoaded = 1;
}

static void savePlanToFile() {
  assert(planFileLoaded);
  if ((planMode & SLEEF_PLAN_READONLY) == 0 && dftPlanFilePath != NULL) {
    ArrayMap_save(planMap, dftPlanFilePath, archID, PLANFILEID);
  }
}

#define CATBIT 8
#define BASETYPEIDBIT 2
#define LOG2LENBIT 8
#define DIRBIT 1

#define BUTSTATBIT 16

static uint64_t keyButStat(int baseTypeID, int log2len, int dir, int butStat) {
  dir = (dir & SLEEF_MODE_BACKWARD) == 0;
  int cat = 0;
  uint64_t k = 0;
  k = (k << BUTSTATBIT) | (butStat & ~(~(uint64_t)0 << BUTSTATBIT));
  k = (k << LOG2LENBIT) | (log2len & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << DIRBIT) | (dir & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << BASETYPEIDBIT) | (baseTypeID & ~(~(uint64_t)0 << BASETYPEIDBIT));
  k = (k << CATBIT) | (cat & ~(~(uint64_t)0 << CATBIT));
  return k;
}

#define LEVELBIT LOG2LENBIT
#define BUTCONFIGBIT 8
#define TRANSCONFIGBIT 8

static uint64_t keyTrans(int baseTypeID, int hlen, int vlen, int transConfig) {
  int max = MAX(hlen, vlen), min = MIN(hlen, vlen);
  int cat = 2;
  uint64_t k = 0;
  k = (k << TRANSCONFIGBIT) | (transConfig & ~(~(uint64_t)0 << TRANSCONFIGBIT));
  k = (k << LOG2LENBIT) | (max & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << LOG2LENBIT) | (min & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << BASETYPEIDBIT) | (baseTypeID & ~(~(uint64_t)0 << BASETYPEIDBIT));
  k = (k << CATBIT) | (cat & ~(~(uint64_t)0 << CATBIT));
  return k;
}

static uint64_t keyPath(int baseTypeID, int log2len, int dir, int level, int config) {
  dir = (dir & SLEEF_MODE_BACKWARD) == 0;
  int cat = 3;
  uint64_t k = 0;
  k = (k << BUTCONFIGBIT) | (config & ~(~(uint64_t)0 << BUTCONFIGBIT));
  k = (k << LEVELBIT) | (level & ~(~(uint64_t)0 << LEVELBIT));
  k = (k << LOG2LENBIT) | (log2len & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << DIRBIT) | (dir & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << BASETYPEIDBIT) | (baseTypeID & ~(~(uint64_t)0 << BASETYPEIDBIT));
  k = (k << CATBIT) | (cat & ~(~(uint64_t)0 << CATBIT));
  return k;
}

static uint64_t keyPathConfig(int baseTypeID, int log2len, int dir, int level, int config) {
  dir = (dir & SLEEF_MODE_BACKWARD) == 0;
  int cat = 4;
  uint64_t k = 0;
  k = (k << BUTCONFIGBIT) | (config & ~(~(uint64_t)0 << BUTCONFIGBIT));
  k = (k << LEVELBIT) | (level & ~(~(uint64_t)0 << LEVELBIT));
  k = (k << LOG2LENBIT) | (log2len & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << DIRBIT) | (dir & ~(~(uint64_t)0 << LOG2LENBIT));
  k = (k << BASETYPEIDBIT) | (baseTypeID & ~(~(uint64_t)0 << BASETYPEIDBIT));
  k = (k << CATBIT) | (cat & ~(~(uint64_t)0 << CATBIT));
  return k;
}

static uint64_t planMap_getU64(uint64_t key) {
  char *s = ArrayMap_get(planMap, key);
  if (s == NULL) return 0;
  uint64_t ret;
  if (sscanf(s, "%" SCNx64, &ret) != 1) return 0;
  return ret;
}

static void planMap_putU64(uint64_t key, uint64_t value) {
  char *s = malloc(100);
  sprintf(s, "%" PRIx64, value);
  s = ArrayMap_put(planMap, key, s);
  if (s != NULL) free(s);
}

int PlanManager_loadMeasurementResultsP(SleefDFT *p, int pathCat) {
  assert(p != NULL && (p->magic == MAGIC_FLOAT || p->magic == MAGIC_DOUBLE));

  initPlanMapLock();

#ifdef _OPENMP
  omp_set_lock(&planMapLock);
#endif
  if (!planFileLoaded) loadPlanFromFile();

  int stat = planMap_getU64(keyButStat(p->baseTypeID, p->log2len, p->mode, pathCat+10));
  if (stat == 0) {
#ifdef _OPENMP
    omp_unset_lock(&planMapLock);
#endif
    return 0;
  }

  int ret = 1;

  for(int j = p->log2len;j >= 0;j--) {
    p->bestPath[j] = planMap_getU64(keyPath(p->baseTypeID, p->log2len, p->mode, j, pathCat));
    p->bestPathConfig[j] = planMap_getU64(keyPathConfig(p->baseTypeID, p->log2len, p->mode, j, pathCat));
    if (p->bestPath[j] > MAXBUTWIDTH) ret = 0;
  }

  p->pathLen = 0;
  for(int j = p->log2len;j >= 0;j--) if (p->bestPath[j] != 0) p->pathLen++;

#ifdef _OPENMP
  omp_unset_lock(&planMapLock);
#endif
  return ret;
}

void PlanManager_saveMeasurementResultsP(SleefDFT *p, int pathCat) {
  assert(p != NULL && (p->magic == MAGIC_FLOAT || p->magic == MAGIC_DOUBLE));

  initPlanMapLock();

#ifdef _OPENMP
  omp_set_lock(&planMapLock);
#endif
  if (!planFileLoaded) loadPlanFromFile();

  if (planMap_getU64(keyButStat(p->baseTypeID, p->log2len, p->mode, pathCat+10)) != 0) {
#ifdef _OPENMP
    omp_unset_lock(&planMapLock);
#endif
    return;
  }

  for(int j = p->log2len;j >= 0;j--) {
    planMap_putU64(keyPath(p->baseTypeID, p->log2len, p->mode, j, pathCat), p->bestPath[j]);
    planMap_putU64(keyPathConfig(p->baseTypeID, p->log2len, p->mode, j, pathCat), p->bestPathConfig[j]);
  }

  planMap_putU64(keyButStat(p->baseTypeID, p->log2len, p->mode, pathCat+10), 1);

  if ((planMode & SLEEF_PLAN_READONLY) == 0) savePlanToFile();

#ifdef _OPENMP
  omp_unset_lock(&planMapLock);
#endif
}

int PlanManager_loadMeasurementResultsT(SleefDFT *p) {
  assert(p != NULL && (p->magic == MAGIC2D_FLOAT || p->magic == MAGIC2D_DOUBLE));

  initPlanMapLock();

#ifdef _OPENMP
  omp_set_lock(&planMapLock);
#endif
  if (!planFileLoaded) loadPlanFromFile();

  p->tmNoMT = planMap_getU64(keyTrans(p->baseTypeID, p->log2hlen, p->log2vlen, 0));
  p->tmMT   = planMap_getU64(keyTrans(p->baseTypeID, p->log2hlen, p->log2vlen, 1));

#ifdef _OPENMP
  omp_unset_lock(&planMapLock);
#endif
  return p->tmNoMT != 0;
}

void PlanManager_saveMeasurementResultsT(SleefDFT *p) {
  assert(p != NULL && (p->magic == MAGIC2D_FLOAT || p->magic == MAGIC2D_DOUBLE));

  initPlanMapLock();

#ifdef _OPENMP
  omp_set_lock(&planMapLock);
#endif
  if (!planFileLoaded) loadPlanFromFile();

  planMap_putU64(keyTrans(p->baseTypeID, p->log2hlen, p->log2vlen, 0), p->tmNoMT);
  planMap_putU64(keyTrans(p->baseTypeID, p->log2hlen, p->log2vlen, 1), p->tmMT  );

  if ((planMode & SLEEF_PLAN_READONLY) == 0) savePlanToFile();

#ifdef _OPENMP
  omp_unset_lock(&planMapLock);
#endif
}
