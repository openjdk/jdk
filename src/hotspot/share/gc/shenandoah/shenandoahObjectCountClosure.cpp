#include "gc/shenandoah/shenandoahObjectCountClosure.hpp"
#include "runtime/mutex.hpp"

static Mutex* get_merge_mutex() {
  static Mutex* _merge_mutex = nullptr;
  if (_merge_mutex == nullptr) {
    _merge_mutex = new Mutex(Mutex::safepoint, "ShenandoahObjectCountMerge");
  }
  return _merge_mutex;
}

void ShenandoahObjectCountClosure::merge_tables(KlassInfoTable* main_cit) {
  if (main_cit == nullptr || _cit == nullptr) {
    return;
  }
  
  MutexLocker ml(get_merge_mutex());
  bool success = main_cit->merge(_cit);
  assert(success, "Failed to merge thread-local table");
}
