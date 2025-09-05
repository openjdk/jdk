#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTCOUNTCLOSURE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTCOUNTCLOSURE_HPP

#include "memory/heapInspection.hpp"
#include "oops/access.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/mutex.hpp"

#if INCLUDE_JFR

class ShenandoahIsAliveClosure;

class ShenandoahObjectCountClosure {
private:
  KlassInfoTable* _cit;
  ShenandoahIsAliveClosure* _filter;

  template <class T>
  inline void do_oop_work(T* p) {
    assert(p != nullptr, "Object is null");
    T o = RawAccess<>::oop_load(p);
    assert(!CompressedOops::is_null(o), "CompressOops is null");
    oop obj = CompressedOops::decode_not_null(o);
    assert(_cit != nullptr, "KlassInfoTable is null");
    if (should_visit(obj)) {
      _cit->record_instance(obj);
    }
  }

public:
  ShenandoahObjectCountClosure(KlassInfoTable* cit, ShenandoahIsAliveClosure* is_alive) : _cit(cit), _filter(is_alive) {}
  // Record the object's instance in the KlassInfoTable
  inline void do_oop(narrowOop* o) { do_oop_work(o); }
  // Record the object's instance in the KlassInfoTable
  inline void do_oop(oop* o) { do_oop_work(o); }
  inline KlassInfoTable* get_table() { return _cit; }
<<<<<<< HEAD

  bool should_visit(oop o);

=======
  
  bool should_visit(oop o);
  
>>>>>>> 774f4e2b8592424f74d3fea4c509d1fc95a20c39
  // Merges the heap's KlassInfoTable with the thread's KlassInfoTable.
  // Clears the thread's table, so it won't be used again.
  void merge_table(KlassInfoTable* global_cit);
};

#endif // INCLUDE_JFR

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTCOUNTCLOSURE_HPP
