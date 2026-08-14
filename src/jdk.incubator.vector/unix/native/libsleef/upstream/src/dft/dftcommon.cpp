//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <cctype>
#include <cinttypes>
#include <cassert>
#include <cmath>

#include <omp.h>
#include <vector>

#include "compat.h"
#include "misc.h"
#include "sleef.h"

#define IMPORT_IS_EXPORT
#include "sleefdft.h"
#include "dftcommon.hpp"
#include "common.h"
#include "serializer.hpp"

const char *configStr[] = { "ST", "ST stream", "MT", "MT stream" };

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
vector<Action> SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::parsePathStr(const char *p) {
  vector<Action> v;

  int level = log2len;
  for(;;) {
    while(isspace((int)*p)) p++;
    if (*p == '\0') break;
    if (!isdigit((int)*p)) throw(runtime_error("Unexpected character"));

    int N = 0;
    while(isdigit((int)*p)) N = N * 10 + *p++ - '0';

    if (N > MAXBUTWIDTHALL) throw(runtime_error("N too large"));
    if (N > level) throw(runtime_error("N larger than level"));

    int config = 0;
    if (*p == '(') {
      p++;

      for(config=3;config>=0;config--) {
        if (strncmp(p, configStr[config], strlen(configStr[config])) == 0) break;
      }
      if (config == -1) throw(runtime_error("Unknown config"));
      p += strlen(configStr[config]);
      if (*p++ != ')') throw(runtime_error("No ')' after config"));
    }

    v.push_back(Action(config, level, N));
    level -= N;
  }

  if (level != 0) throw(runtime_error("Sum of N less than level"));

  return v;
}

static string to_string(vector<Action> v) {
  string s = "";
  for(auto e : v) {
    string c = "? " + to_string(e.config);
    if (0 <= e.config && e.config < 4) c = configStr[e.config];
    s += to_string(e.N) + "(" + c + ") ";
  }
  return s;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::setPath(const char *pathStr) {
  assert(magic == MAGIC_FLOAT || magic == MAGIC_DOUBLE);

  try {
    bestPath = parsePathStr(pathStr);

    if ((mode & SLEEF_MODE_VERBOSE) != 0) fprintf(verboseFP, "Set path : %s\n", to_string(bestPath).c_str());
  } catch(exception &ex) {
    if ((mode & SLEEF_MODE_VERBOSE) != 0) fprintf(verboseFP, "Parse error : %s\n", ex.what());
  }
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::setPath(const char *pathStr) {
  assert(magic == MAGIC2D_FLOAT || magic == MAGIC2D_DOUBLE);
  int planMT_ = 0;
  if (sscanf(pathStr, "%d", &planMT_) != 1) return;
  planMT = planMT_;

  string pathH = pathStr;
  size_t cpos = pathH.find_first_of(':');
  if (cpos == string::npos) return;
  pathH = pathH.substr(cpos + 1);

  cpos = pathH.find_first_of(',');
  if (cpos == string::npos) return;
  string pathV = pathH.substr(cpos+1);
  pathH = pathH.substr(0, cpos);

  instH->setPath(pathH.c_str());
  instV->setPath(pathV.c_str());
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
string SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::getPath() {
  assert(magic == MAGIC_FLOAT || magic == MAGIC_DOUBLE);
  return to_string(bestPath);
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
string SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::getPath() {
  assert(magic == MAGIC2D_FLOAT || magic == MAGIC2D_DOUBLE);
  return to_string((int)planMT) + ":" +
    instH->getPath() + "," + instV->getPath();
}

EXPORT void SleefDFT_setPath(SleefDFT *p, char *pathStr) {
  assert(p != NULL);
  switch(p->magic) {
  case MAGIC_DOUBLE:
    p->double_->setPath(pathStr);
    break;
  case MAGIC_FLOAT:
    p->float_->setPath(pathStr);
    break;
  case MAGIC2D_DOUBLE:
    p->double2d_->setPath(pathStr);
    break;
  case MAGIC2D_FLOAT:
    p->float2d_->setPath(pathStr);
    break;
  default: abort();
  }
}

EXPORT int SleefDFT_getPath(SleefDFT *p, char *pathStr, int pathStrSize) {
  assert(p != NULL);

  string str;
  switch(p->magic) {
  case MAGIC_DOUBLE:
    str = p->double_->getPath();
    break;
  case MAGIC_FLOAT:
    str = p->float_->getPath();
    break;
  case MAGIC2D_DOUBLE:
    str = p->double2d_->getPath();
    break;
  case MAGIC2D_FLOAT:
    str = p->float2d_->getPath();
    break;
  default: abort();
  }

  strncpy(pathStr, str.c_str(), pathStrSize);

  return pathStrSize == 0 ? 0 : strlen(pathStr);
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::freeTables() {
  for(int N=1;N<=MAXBUTWIDTH;N++) {
    for(uint32_t level=N;level<=log2len;level++) {
      Sleef_free(tbl[N][level]);
      tbl[N][level] = nullptr;
    }
    free(tbl[N]);
    tbl[N] = NULL;
  }

  for(int i=0;i<nThread;i++) {
    Sleef_free(x1[i]);
    x1[i] = nullptr;
    Sleef_free(x0[i]);
    x0[i] = nullptr;
  }

  free(x1);
  x1 = nullptr;
  free(x0);
  x0 = nullptr;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::~SleefDFTXX() {
  assert(magic == MAGIC_FLOAT || magic == MAGIC_DOUBLE);

  if (log2len <= 1) {
    magic = 0;
    return;
  }

  if ((mode & SLEEF_MODE_REAL) != 0) {
    Sleef_free(rtCoef1);
    rtCoef1 = nullptr;
    Sleef_free(rtCoef0);
    rtCoef0 = nullptr;
  }

  for(int level = log2len;level >= 1;level--) {
    Sleef_free(perm[level]);
    perm[level] = nullptr;
  }
  free(perm);
  perm = NULL;

  freeTables();

  magic = 0;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::~SleefDFT2DXX() {
  assert(magic == MAGIC2D_FLOAT || magic == MAGIC2D_DOUBLE);

  Sleef_free(tBuf);
  tBuf = nullptr;
  delete instH;
  instH = nullptr;
  if (hlen != vlen) {
    delete instV;
    instV = nullptr;
  }

  magic = 0;
}

EXPORT void SleefDFT_dispose(SleefDFT *p) {
  assert(p != NULL);
  switch(p->magic) {
  case MAGIC_DOUBLE:
    delete p->double_;
    p->magic = 0;
    p->double_ = nullptr;
    free(p);
    break;
  case MAGIC2D_DOUBLE:
    delete p->double2d_;
    p->magic = 0;
    p->double_ = nullptr;
    free(p);
    break;
  case MAGIC_FLOAT:
    delete p->float_;
    p->magic = 0;
    p->float_ = nullptr;
    free(p);
    break;
  case MAGIC2D_FLOAT:
    delete p->float2d_;
    p->magic = 0;
    p->float_ = nullptr;
    free(p);
    break;
  default: abort();
  }
}

// PlanManager

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
string SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::planKeyString(string suffix) {
  string s;
  s += baseTypeID == 1 ? "D" : "S";
  s += (mode & SLEEF_MODE_REAL) ? "r" : "c";
  s += (mode & SLEEF_MODE_BACKWARD) ? "b" : "f";
  s += (mode & SLEEF_MODE_ALT) ? "o" : "w";
  s += (mode & SLEEF_MODE_NO_MT) ? "s" : "m";
  s += to_string(log2len) + "," + "0";
  if (suffix != "") s += ":" + suffix;
  return s;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
string SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::planKeyString(string suffix) {
  string s;
  s += baseTypeID == 1 ? "D" : "S";
  s += (mode & SLEEF_MODE_REAL) ? "r" : "c";
  s += (mode & SLEEF_MODE_BACKWARD) ? "b" : "f";
  s += (mode & SLEEF_MODE_ALT) ? "o" : "w";
  s += (mode & SLEEF_MODE_NO_MT) ? "s" : "m";
  s += to_string(log2hlen) + "," + to_string(log2vlen);
  if (suffix != "") s += ":" + suffix;
  return s;
}

static string getPlanIdPrefix() {
  string s;

#ifdef ENABLE_STREAM
  s += "s";
#else
  s += "n";
#endif
  s += to_string(CONFIGMAX) + ",";
  s += to_string(ISAMAX) + ",";
  s += to_string(MAXBUTWIDTHDP) + ",";
  s += to_string(MAXBUTWIDTHSP) + ",";
  s += to_string(MINSHIFTDP) + ",";
  s += to_string(MAXSHIFTDP) + ",";
  s += to_string(MINSHIFTSP) + ",";
  s += to_string(MAXSHIFTSP) + ":";

  return s;
}

PlanManager::PlanManager() {
  planID = getPlanIdPrefix() + Sleef_getCpuIdString();
}

void PlanManager::setPlanFilePath(const char *path, const char *arch, uint64_t mode) {
  planMode_ = mode;

  dftPlanFilePath = "";
  if (path != NULL) dftPlanFilePath = path;

  planID = Sleef_getCpuIdString();
  if (arch != NULL) planID = arch;
  planID = getPlanIdPrefix() + planID;

  if ((mode & SLEEF_PLAN_RESET) != 0) std::get<0>(thePlan)[planID].clear();
}

void PlanManager::loadPlanFromFile() {
  if ((planMode_ & SLEEF_PLAN_REFERTOENVVAR) != 0) {
    char *s = std::getenv(ENVVAR);
    if (s != NULL) SleefDFT_setPlanFilePath(s, NULL, planMode_);
  }

  if (dftPlanFilePath != "") {
    FILE *fp = fopen(dftPlanFilePath.c_str(), "rb");
    if (fp) {
      if (!(planMode_ & SLEEF_PLAN_NOLOCK)) FLOCK(fp);
      FileDeserializer d(fp);
      tuple<unordered_map<string, unordered_map<string, string>>, string> plan;
      try {
        d >> plan;
      } catch(exception &ex) {}
      if (!(planMode_ & SLEEF_PLAN_NOLOCK)) FUNLOCK(fp);
      fclose(fp);
      if (std::get<1>(plan) == PLANFILEID) thePlan = plan;
    }
  }
}

bool PlanManager::savePlanToFile(const string &fn) {
  if (fn != "") {
    FILE *fp = fopen(fn.c_str(), "wb");
    if (fp) {
      FLOCK(fp);
      FileSerializer s(fp);
      std::get<1>(thePlan) = PLANFILEID;
      s << thePlan;
      FUNLOCK(fp);
      fclose(fp);
      return true;
    }
  }
  return false;
}

bool PlanManager::savePlanToFile() {
  if ((planMode_ & SLEEF_PLAN_READONLY) != 0) return false;
  return savePlanToFile(dftPlanFilePath);
}

bool PlanManager::loadAndPutToFile(const string& key, const string& value) {
  if ((planMode_ & SLEEF_PLAN_REFERTOENVVAR) != 0) {
    char *s = std::getenv(ENVVAR);
    if (s != NULL) SleefDFT_setPlanFilePath(s, NULL, planMode_);
  }

  if (dftPlanFilePath != "") {
    FILE *fp = fopen(dftPlanFilePath.c_str(), "r+b");
    if (!fp) fp = fopen(dftPlanFilePath.c_str(), "w+b");
    if (fp) {
      if (!(planMode_ & SLEEF_PLAN_NOLOCK)) FLOCK(fp);
      fseek(fp, 0, SEEK_END);
      if (ftell(fp) != 0) {
        fseek(fp, 0, SEEK_SET);
        FileDeserializer d(fp);
        tuple<unordered_map<string, unordered_map<string, string>>, string> plan;
         try {
          d >> plan;
        } catch(exception &ex) {}
        if (std::get<1>(plan) == PLANFILEID) thePlan = plan;
      }

      std::get<0>(thePlan)[planID][key] = value;
      std::get<1>(thePlan) = PLANFILEID;
      fseek(fp, 0, SEEK_SET);
      FileSerializer s(fp);
      s << thePlan;
      if (!(planMode_ & SLEEF_PLAN_NOLOCK)) FUNLOCK(fp);
      fclose(fp);
      return true;
    }
  }

  return false;
}

EXPORT void SleefDFT_setPlanFilePath(const char *path, const char *arch, uint64_t mode) {
  planManager.setPlanFilePath(path, arch, mode);
}

EXPORT int SleefDFT_savePlan(const char *pathStr) {
  return (int)planManager.savePlanToFile(pathStr);
}

string PlanManager::get(const string& key) {
  if (std::get<0>(thePlan)[planID].count(key) == 0) return "";

  return std::get<0>(thePlan)[planID].at(key);
}

void PlanManager::put(const string& key, const string& value) {
  std::get<0>(thePlan)[planID][key] = value;
}

//

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::saveMeasurementResults() {
  assert(magic == MAGIC_FLOAT || magic == MAGIC_DOUBLE);

  unique_lock<recursive_mutex> lock(planManager.mtx);

  if ((planManager.planMode() & SLEEF_PLAN_AUTOMATIC) != 0) {
    if (planManager.loadAndPutToFile(planKeyString(), getPath()) && (mode & SLEEF_MODE_VERBOSE) != 0) {
      fprintf(verboseFP, "Saving plan to file\n");
    }
  } else {
    planManager.put(planKeyString(), getPath());
  }
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
void SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::saveMeasurementResults() {
  assert(magic == MAGIC2D_FLOAT || magic == MAGIC2D_DOUBLE);

  unique_lock<recursive_mutex> lock(planManager.mtx);

  if ((planManager.planMode() & SLEEF_PLAN_AUTOMATIC) != 0) {
    if (planManager.loadAndPutToFile(planKeyString(), getPath()) && (mode & SLEEF_MODE_VERBOSE) != 0) {
      fprintf(verboseFP, "Saving plan to file\n");
    }
  } else {
    planManager.put(planKeyString(), getPath());
  }
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
bool SleefDFTXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::loadMeasurementResults() {
  assert(magic == MAGIC_FLOAT || magic == MAGIC_DOUBLE);

  unique_lock<recursive_mutex> lock(planManager.mtx);

  planManager.loadPlanFromFile();

  string path = planManager.get(planKeyString());
  if (path == "") return false;

  setPath(path.c_str());

  return true;
}

template<typename real, typename real2, int MAXSHIFT, int MAXBUTWIDTH>
bool SleefDFT2DXX<real, real2, MAXSHIFT, MAXBUTWIDTH>::loadMeasurementResults() {
  assert(magic == MAGIC2D_FLOAT || magic == MAGIC2D_DOUBLE);

  unique_lock<recursive_mutex> lock(planManager.mtx);

  planManager.loadPlanFromFile();

  string path = planManager.get(planKeyString());
  if (path == "") return false;

  setPath(path.c_str());

  return true;
}

// Instantiation

template void SleefDFTXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>::freeTables();
template void SleefDFTXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>::freeTables();
template SleefDFTXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>::~SleefDFTXX();
template SleefDFTXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>::~SleefDFTXX();
template SleefDFT2DXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>::~SleefDFT2DXX();
template SleefDFT2DXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>::~SleefDFT2DXX();

template bool SleefDFTXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>::loadMeasurementResults();
template bool SleefDFTXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>::loadMeasurementResults();
template void SleefDFTXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>::saveMeasurementResults();
template void SleefDFTXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>::saveMeasurementResults();
template bool SleefDFT2DXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>::loadMeasurementResults();
template bool SleefDFT2DXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>::loadMeasurementResults();
template void SleefDFT2DXX<double, Sleef_double2, MAXSHIFTDP, MAXBUTWIDTHDP>::saveMeasurementResults();
template void SleefDFT2DXX<float, Sleef_float2, MAXSHIFTSP, MAXBUTWIDTHSP>::saveMeasurementResults();

PlanManager planManager;

FILE *defaultVerboseFP = stdout;

EXPORT void SleefDFT_setDefaultVerboseFP(FILE *fp) {
  defaultVerboseFP = fp;
}
