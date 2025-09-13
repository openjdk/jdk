#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahObjectCountClosure.hpp"
#include "runtime/mutexLocker.hpp"

#if INCLUDE_JFR

void ShenandoahObjectCountClosure::merge_table(KlassInfoTable* global_cit) {
  assert(_cit != nullptr, "The thread-local KlassInfoTable is not initialized");
  assert(global_cit != nullptr, "Shenandoah KlassInfoTable is not initialized");
  MutexLocker x(ObjectCountMerge_lock, Mutex::_no_safepoint_check_flag);
  bool success = global_cit->merge(_cit);

  // Clear the _cit in the closure to ensure it won't be used again.
  _cit = nullptr;
  assert(success, "Failed to merge thread-local table");
}

bool ShenandoahObjectCountClosure::should_visit(oop o) {
  return _filter->do_object_b(o);
}

#endif // INCLUDE_JFR
