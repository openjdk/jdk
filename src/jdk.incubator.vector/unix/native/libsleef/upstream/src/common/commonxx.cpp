#if defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSC_VER)
#include <windows.h>
#endif

#include <mutex>
#include <chrono>
#include "misc.h"
#include "compat.h"

using namespace std;

extern "C" {
EXPORT uint64_t Sleef_currentTimeMicros();
NOEXPORT int Sleef_internal_cpuSupportsExt(void (*tryExt)(), int *cache);
}

EXPORT uint64_t Sleef_currentTimeMicros() {
  return chrono::duration_cast<chrono::microseconds>
    (chrono::system_clock::now() - chrono::system_clock::from_time_t(0)).count();
}

//

static void sighandler(int signum) { LONGJMP(sigjmp, 1); }

NOEXPORT int Sleef_internal_cpuSupportsExt(void (*tryExt)(), int *cache) {
  if (*cache != -1) return *cache;

  static mutex mtx;

  unique_lock<mutex> lock(mtx);

  typedef void (*sighandler_t)(int);
  sighandler_t org = signal(SIGILL, sighandler);

  if (SETJMP(sigjmp) == 0) {
    (*tryExt)();
    *cache = 1;
  } else {
    *cache = 0;
  }

  signal(SIGILL, org);
  return *cache;
}
