#include "gc/shenandoah/shenandoahObjectCountClosure.hpp"
#include "runtime/mutexLocker.hpp"

void ShenandoahObjectCountClosure::merge_tables(KlassInfoTable* main_cit) {
  if (main_cit == nullptr || _cit == nullptr) {
    return;
  }
  
  MutexLocker x(TableMerge_lock, Mutex::_no_safepoint_check_flag);
  bool success = main_cit->merge(_cit);
  assert(success, "Failed to merge thread-local table");
}
