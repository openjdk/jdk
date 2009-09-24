/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/////////////////////////////////////////////////////////////////
// Closures used by ConcurrentMarkSweepGeneration's collector
/////////////////////////////////////////////////////////////////
class ConcurrentMarkSweepGeneration;
class CMSBitMap;
class CMSMarkStack;
class CMSCollector;
class MarkFromRootsClosure;
class Par_MarkFromRootsClosure;

// Decode the oop and call do_oop on it.
#define DO_OOP_WORK_DEFN \
  void do_oop(oop obj);                                   \
  template <class T> inline void do_oop_work(T* p) {      \
    T heap_oop = oopDesc::load_heap_oop(p);               \
    if (!oopDesc::is_null(heap_oop)) {                    \
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);       \
      do_oop(obj);                                        \
    }                                                     \
  }

class MarkRefsIntoClosure: public OopsInGenClosure {
 private:
  const MemRegion _span;
  CMSBitMap*      _bitMap;
 protected:
  DO_OOP_WORK_DEFN
 public:
  MarkRefsIntoClosure(MemRegion span, CMSBitMap* bitMap);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { MarkRefsIntoClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { MarkRefsIntoClosure::do_oop_work(p); }
  bool do_header() { return true; }
  Prefetch::style prefetch_style() {
    return Prefetch::do_read;
  }
};

// A variant of the above used in certain kinds of CMS
// marking verification.
class MarkRefsIntoVerifyClosure: public OopsInGenClosure {
 private:
  const MemRegion _span;
  CMSBitMap*      _verification_bm;
  CMSBitMap*      _cms_bm;
 protected:
  DO_OOP_WORK_DEFN
 public:
  MarkRefsIntoVerifyClosure(MemRegion span, CMSBitMap* verification_bm,
                            CMSBitMap* cms_bm);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { MarkRefsIntoVerifyClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { MarkRefsIntoVerifyClosure::do_oop_work(p); }
  bool do_header() { return true; }
  Prefetch::style prefetch_style() {
    return Prefetch::do_read;
  }
};

// KlassRememberingOopClosure is used when marking of the permanent generation
// is being done.  It adds fields to support revisiting of klasses
// for class unloading.  _should_remember_klasses should be set to
// indicate if klasses should be remembered.  Currently that is whenever
// CMS class unloading is turned on.  The _revisit_stack is used
// to save the klasses for later processing.
class KlassRememberingOopClosure : public OopClosure {
 protected:
  CMSCollector* _collector;
  CMSMarkStack* _revisit_stack;
  bool const    _should_remember_klasses;
 public:
  void check_remember_klasses() const PRODUCT_RETURN;
  virtual const bool should_remember_klasses() const {
    check_remember_klasses();
    return _should_remember_klasses;
  }
  virtual void remember_klass(Klass* k);

  KlassRememberingOopClosure(CMSCollector* collector,
                             ReferenceProcessor* rp,
                             CMSMarkStack* revisit_stack);
};

// Similar to KlassRememberingOopClosure for use when multiple
// GC threads will execute the closure.

class Par_KlassRememberingOopClosure : public KlassRememberingOopClosure {
 public:
  Par_KlassRememberingOopClosure(CMSCollector* collector,
                                 ReferenceProcessor* rp,
                                 CMSMarkStack* revisit_stack):
    KlassRememberingOopClosure(collector, rp, revisit_stack) {}
  virtual void remember_klass(Klass* k);
};

// The non-parallel version (the parallel version appears further below).
class PushAndMarkClosure: public KlassRememberingOopClosure {
 private:
  MemRegion     _span;
  CMSBitMap*    _bit_map;
  CMSBitMap*    _mod_union_table;
  CMSMarkStack* _mark_stack;
  bool          _concurrent_precleaning;
 protected:
  DO_OOP_WORK_DEFN
 public:
  PushAndMarkClosure(CMSCollector* collector,
                     MemRegion span,
                     ReferenceProcessor* rp,
                     CMSBitMap* bit_map,
                     CMSBitMap* mod_union_table,
                     CMSMarkStack* mark_stack,
                     CMSMarkStack* revisit_stack,
                     bool concurrent_precleaning);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { PushAndMarkClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { PushAndMarkClosure::do_oop_work(p); }
  bool do_header() { return true; }
  Prefetch::style prefetch_style() {
    return Prefetch::do_read;
  }
  // In support of class unloading
  virtual const bool should_remember_mdo() const {
    return false;
    // return _should_remember_klasses;
  }
  virtual void remember_mdo(DataLayout* v);
};

// In the parallel case, the revisit stack, the bit map and the
// reference processor are currently all shared. Access to
// these shared mutable structures must use appropriate
// synchronization (for instance, via CAS). The marking stack
// used in the non-parallel case above is here replaced with
// an OopTaskQueue structure to allow efficient work stealing.
class Par_PushAndMarkClosure: public Par_KlassRememberingOopClosure {
 private:
  MemRegion     _span;
  CMSBitMap*    _bit_map;
  OopTaskQueue* _work_queue;
 protected:
  DO_OOP_WORK_DEFN
 public:
  Par_PushAndMarkClosure(CMSCollector* collector,
                         MemRegion span,
                         ReferenceProcessor* rp,
                         CMSBitMap* bit_map,
                         OopTaskQueue* work_queue,
                         CMSMarkStack* revisit_stack);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { Par_PushAndMarkClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { Par_PushAndMarkClosure::do_oop_work(p); }
  bool do_header() { return true; }
  Prefetch::style prefetch_style() {
    return Prefetch::do_read;
  }
  // In support of class unloading
  virtual const bool should_remember_mdo() const {
    return false;
    // return _should_remember_klasses;
  }
  virtual void remember_mdo(DataLayout* v);
};

// The non-parallel version (the parallel version appears further below).
class MarkRefsIntoAndScanClosure: public OopsInGenClosure {
 private:
  MemRegion          _span;
  CMSBitMap*         _bit_map;
  CMSMarkStack*      _mark_stack;
  PushAndMarkClosure _pushAndMarkClosure;
  CMSCollector*      _collector;
  Mutex*             _freelistLock;
  bool               _yield;
  // Whether closure is being used for concurrent precleaning
  bool               _concurrent_precleaning;
 protected:
  DO_OOP_WORK_DEFN
 public:
  MarkRefsIntoAndScanClosure(MemRegion span,
                             ReferenceProcessor* rp,
                             CMSBitMap* bit_map,
                             CMSBitMap* mod_union_table,
                             CMSMarkStack* mark_stack,
                             CMSMarkStack* revisit_stack,
                             CMSCollector* collector,
                             bool should_yield,
                             bool concurrent_precleaning);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { MarkRefsIntoAndScanClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { MarkRefsIntoAndScanClosure::do_oop_work(p); }
  bool do_header() { return true; }
  Prefetch::style prefetch_style() {
    return Prefetch::do_read;
  }
  void set_freelistLock(Mutex* m) {
    _freelistLock = m;
  }
  virtual const bool should_remember_klasses() const {
    return _pushAndMarkClosure.should_remember_klasses();
  }
  virtual void remember_klass(Klass* k) {
    _pushAndMarkClosure.remember_klass(k);
  }

 private:
  inline void do_yield_check();
  void do_yield_work();
  bool take_from_overflow_list();
};

// Tn this, the parallel avatar of MarkRefsIntoAndScanClosure, the revisit
// stack and the bitMap are shared, so access needs to be suitably
// sycnhronized. An OopTaskQueue structure, supporting efficient
// workstealing, replaces a CMSMarkStack for storing grey objects.
class Par_MarkRefsIntoAndScanClosure: public OopsInGenClosure {
 private:
  MemRegion              _span;
  CMSBitMap*             _bit_map;
  OopTaskQueue*          _work_queue;
  const uint             _low_water_mark;
  Par_PushAndMarkClosure _par_pushAndMarkClosure;
 protected:
  DO_OOP_WORK_DEFN
 public:
  Par_MarkRefsIntoAndScanClosure(CMSCollector* collector,
                                 MemRegion span,
                                 ReferenceProcessor* rp,
                                 CMSBitMap* bit_map,
                                 OopTaskQueue* work_queue,
                                 CMSMarkStack*  revisit_stack);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { Par_MarkRefsIntoAndScanClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { Par_MarkRefsIntoAndScanClosure::do_oop_work(p); }
  bool do_header() { return true; }
  // When ScanMarkedObjectsAgainClosure is used,
  // it passes [Par_]MarkRefsIntoAndScanClosure to oop_oop_iterate(),
  // and this delegation is used.
  virtual const bool should_remember_klasses() const {
    return _par_pushAndMarkClosure.should_remember_klasses();
  }
  // See comment on should_remember_klasses() above.
  virtual void remember_klass(Klass* k) {
    _par_pushAndMarkClosure.remember_klass(k);
  }
  Prefetch::style prefetch_style() {
    return Prefetch::do_read;
  }
  void trim_queue(uint size);
};

// This closure is used during the concurrent marking phase
// following the first checkpoint. Its use is buried in
// the closure MarkFromRootsClosure.
class PushOrMarkClosure: public KlassRememberingOopClosure {
 private:
  MemRegion       _span;
  CMSBitMap*      _bitMap;
  CMSMarkStack*   _markStack;
  HeapWord* const _finger;
  MarkFromRootsClosure* const
                  _parent;
 protected:
  DO_OOP_WORK_DEFN
 public:
  PushOrMarkClosure(CMSCollector* cms_collector,
                    MemRegion span,
                    CMSBitMap* bitMap,
                    CMSMarkStack* markStack,
                    CMSMarkStack* revisitStack,
                    HeapWord* finger,
                    MarkFromRootsClosure* parent);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { PushOrMarkClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { PushOrMarkClosure::do_oop_work(p); }
  // In support of class unloading
  virtual const bool should_remember_mdo() const {
    return false;
    // return _should_remember_klasses;
  }
  virtual void remember_mdo(DataLayout* v);

  // Deal with a stack overflow condition
  void handle_stack_overflow(HeapWord* lost);
 private:
  inline void do_yield_check();
};

// A parallel (MT) version of the above.
// This closure is used during the concurrent marking phase
// following the first checkpoint. Its use is buried in
// the closure Par_MarkFromRootsClosure.
class Par_PushOrMarkClosure: public Par_KlassRememberingOopClosure {
 private:
  MemRegion        _whole_span;
  MemRegion        _span;        // local chunk
  CMSBitMap*       _bit_map;
  OopTaskQueue*    _work_queue;
  CMSMarkStack*    _overflow_stack;
  HeapWord*  const _finger;
  HeapWord** const _global_finger_addr;
  Par_MarkFromRootsClosure* const
                   _parent;
 protected:
  DO_OOP_WORK_DEFN
 public:
  Par_PushOrMarkClosure(CMSCollector* cms_collector,
                        MemRegion span,
                        CMSBitMap* bit_map,
                        OopTaskQueue* work_queue,
                        CMSMarkStack* mark_stack,
                        CMSMarkStack* revisit_stack,
                        HeapWord* finger,
                        HeapWord** global_finger_addr,
                        Par_MarkFromRootsClosure* parent);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { Par_PushOrMarkClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { Par_PushOrMarkClosure::do_oop_work(p); }
  // In support of class unloading
  virtual const bool should_remember_mdo() const {
    return false;
    // return _should_remember_klasses;
  }
  virtual void remember_mdo(DataLayout* v);

  // Deal with a stack overflow condition
  void handle_stack_overflow(HeapWord* lost);
 private:
  inline void do_yield_check();
};

// For objects in CMS generation, this closure marks
// given objects (transitively) as being reachable/live.
// This is currently used during the (weak) reference object
// processing phase of the CMS final checkpoint step, as
// well as during the concurrent precleaning of the discovered
// reference lists.
class CMSKeepAliveClosure: public KlassRememberingOopClosure {
 private:
  const MemRegion _span;
  CMSMarkStack* _mark_stack;
  CMSBitMap*    _bit_map;
  bool          _concurrent_precleaning;
 protected:
  DO_OOP_WORK_DEFN
 public:
  CMSKeepAliveClosure(CMSCollector* collector, MemRegion span,
                      CMSBitMap* bit_map, CMSMarkStack* mark_stack,
                      CMSMarkStack* revisit_stack, bool cpc);
  bool    concurrent_precleaning() const { return _concurrent_precleaning; }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { CMSKeepAliveClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { CMSKeepAliveClosure::do_oop_work(p); }
};

class CMSInnerParMarkAndPushClosure: public Par_KlassRememberingOopClosure {
 private:
  MemRegion     _span;
  OopTaskQueue* _work_queue;
  CMSBitMap*    _bit_map;
 protected:
  DO_OOP_WORK_DEFN
 public:
  CMSInnerParMarkAndPushClosure(CMSCollector* collector,
                                MemRegion span, CMSBitMap* bit_map,
                                CMSMarkStack* revisit_stack,
                                OopTaskQueue* work_queue);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { CMSInnerParMarkAndPushClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { CMSInnerParMarkAndPushClosure::do_oop_work(p); }
};

// A parallel (MT) version of the above, used when
// reference processing is parallel; the only difference
// is in the do_oop method.
class CMSParKeepAliveClosure: public Par_KlassRememberingOopClosure {
 private:
  MemRegion     _span;
  OopTaskQueue* _work_queue;
  CMSBitMap*    _bit_map;
  CMSInnerParMarkAndPushClosure
                _mark_and_push;
  const uint    _low_water_mark;
  void trim_queue(uint max);
 protected:
  DO_OOP_WORK_DEFN
 public:
  CMSParKeepAliveClosure(CMSCollector* collector, MemRegion span,
                         CMSBitMap* bit_map, CMSMarkStack* revisit_stack,
                         OopTaskQueue* work_queue);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { CMSParKeepAliveClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { CMSParKeepAliveClosure::do_oop_work(p); }
};
