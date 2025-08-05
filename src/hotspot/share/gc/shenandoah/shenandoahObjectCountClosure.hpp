#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTCOUNTCLOSURE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTCOUNTCLOSURE_HPP

#include "memory/heapInspection.hpp"
#include "oops/access.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"


class ShenandoahObjectCountClosure {
private:
  static thread_local KlassInfoTable* _cit;

  template <class T>
  inline void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    oop obj = CompressedOops::decode_not_null(o);
    _cit->record_instance(obj);
  }

public:
  ShenandoahObjectCountClosure() { initialize_table(); }
  // Record the object's instance in the KlassInfoTable
  inline void do_oop(narrowOop* o) { do_oop_work(o); }
  // Record the object's instance in the KlassInfoTable
  inline void do_oop(oop* o) { do_oop_work(o); }
  inline KlassInfoTable* get_table() { return _cit; }

  void merge_tables(KlassInfoTable* main_cit);

private:
  void initialize_table();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTCOUNTCLOSURE_HPP
