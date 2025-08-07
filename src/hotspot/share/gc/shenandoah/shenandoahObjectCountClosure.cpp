#include "gc/shenandoah/shenandoahObjectCountClosure.hpp"
#include "runtime/mutex.hpp"

Mutex* ShenandoahObjectCountClosure::get_mutex() {
  static Mutex mutex(Mutex::nosafepoint, "ShenandoahObjectCountMerge");
  return &mutex;
}

void ShenandoahObjectCountClosure::merge_tables(KlassInfoTable* main_cit) {
  if (main_cit == nullptr || _cit == nullptr) {
    return;
  }
  
  Mutex* mutex = get_mutex();
  MutexLocker x(mutex, Mutex::_no_safepoint_check_flag);
  bool success = main_cit->merge(_cit);
  assert(success, "Failed to merge thread-local table");
}
