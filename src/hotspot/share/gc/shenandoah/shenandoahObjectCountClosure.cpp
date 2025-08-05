#include "gc/shenandoah/shenandoahObjectCountClosure.hpp"

thread_local KlassInfoTable* ShenandoahObjectCountClosure::_cit = nullptr;

// Change the return type to be a boolean
void ShenandoahObjectCountClosure::initialize_table() {
  if (_cit == nullptr) {
    static thread_local KlassInfoTable temp_table(false);
    _cit = &temp_table;
  }
}

void ShenandoahObjectCountClosure::merge_tables(KlassInfoTable* main_cit) {
  if (main_cit == nullptr || _cit == nullptr) {
    return;
  }

  bool success = main_cit->merge(_cit);
  assert(success, "Failed to merge thread-local table");
}