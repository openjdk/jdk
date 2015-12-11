/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_VM_GC_G1_G1OOPCLOSURES_HPP
#define SHARE_VM_GC_G1_G1OOPCLOSURES_HPP

#include "memory/iterator.hpp"
#include "oops/markOop.hpp"

class HeapRegion;
class G1CollectedHeap;
class G1RemSet;
class ConcurrentMark;
class DirtyCardToOopClosure;
class CMBitMap;
class CMMarkStack;
class G1ParScanThreadState;
class CMTask;
class ReferenceProcessor;

// A class that scans oops in a given heap region (much as OopsInGenClosure
// scans oops in a generation.)
class OopsInHeapRegionClosure: public ExtendedOopClosure {
protected:
  HeapRegion* _from;
public:
  void set_region(HeapRegion* from) { _from = from; }
};

class G1ParClosureSuper : public OopsInHeapRegionClosure {
protected:
  G1CollectedHeap* _g1;
  G1ParScanThreadState* _par_scan_state;

  G1ParClosureSuper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state);
  ~G1ParClosureSuper() { }

public:
  virtual bool apply_to_weak_ref_discovered_field() { return true; }
};

class G1ParPushHeapRSClosure : public G1ParClosureSuper {
public:
  G1ParPushHeapRSClosure(G1CollectedHeap* g1,
                         G1ParScanThreadState* par_scan_state):
    G1ParClosureSuper(g1, par_scan_state) { }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)          { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)    { do_oop_nv(p); }
};

class G1ParScanClosure : public G1ParClosureSuper {
public:
  G1ParScanClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
    G1ParClosureSuper(g1, par_scan_state) { }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)          { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)    { do_oop_nv(p); }

  void set_ref_processor(ReferenceProcessor* rp) {
    set_ref_processor_internal(rp);
  }
};

// Add back base class for metadata
class G1ParCopyHelper : public G1ParClosureSuper {
protected:
  uint _worker_id;              // Cache value from par_scan_state.
  Klass* _scanned_klass;
  ConcurrentMark* _cm;

  // Mark the object if it's not already marked. This is used to mark
  // objects pointed to by roots that are guaranteed not to move
  // during the GC (i.e., non-CSet objects). It is MT-safe.
  inline void mark_object(oop obj);

  // Mark the object if it's not already marked. This is used to mark
  // objects pointed to by roots that have been forwarded during a
  // GC. It is MT-safe.
  inline void mark_forwarded_object(oop from_obj, oop to_obj);

  G1ParCopyHelper(G1CollectedHeap* g1,  G1ParScanThreadState* par_scan_state);
  ~G1ParCopyHelper() { }

 public:
  void set_scanned_klass(Klass* k) { _scanned_klass = k; }
  template <class T> inline void do_klass_barrier(T* p, oop new_obj);
};

enum G1Barrier {
  G1BarrierNone,
  G1BarrierKlass
};

enum G1Mark {
  G1MarkNone,
  G1MarkFromRoot,
  G1MarkPromotedFromRoot
};

template <G1Barrier barrier, G1Mark do_mark_object, bool use_ext>
class G1ParCopyClosure : public G1ParCopyHelper {
public:
  G1ParCopyClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
      G1ParCopyHelper(g1, par_scan_state) {
    assert(ref_processor() == NULL, "sanity");
  }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

class G1KlassScanClosure : public KlassClosure {
 G1ParCopyHelper* _closure;
 bool             _process_only_dirty;
 int              _count;
 public:
  G1KlassScanClosure(G1ParCopyHelper* closure, bool process_only_dirty)
      : _process_only_dirty(process_only_dirty), _closure(closure), _count(0) {}
  void do_klass(Klass* klass);
};

class FilterIntoCSClosure: public ExtendedOopClosure {
  G1CollectedHeap* _g1;
  OopClosure* _oc;
  DirtyCardToOopClosure* _dcto_cl;
public:
  FilterIntoCSClosure(  DirtyCardToOopClosure* dcto_cl,
                        G1CollectedHeap* g1,
                        OopClosure* oc) :
    _dcto_cl(dcto_cl), _g1(g1), _oc(oc) { }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)        { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)  { do_oop_nv(p); }
  bool apply_to_weak_ref_discovered_field() { return true; }
};

class FilterOutOfRegionClosure: public ExtendedOopClosure {
  HeapWord* _r_bottom;
  HeapWord* _r_end;
  OopClosure* _oc;
public:
  FilterOutOfRegionClosure(HeapRegion* r, OopClosure* oc);
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
  bool apply_to_weak_ref_discovered_field() { return true; }
};

// Closure for iterating over object fields during concurrent marking
class G1CMOopClosure : public MetadataAwareOopClosure {
protected:
  ConcurrentMark*    _cm;
private:
  G1CollectedHeap*   _g1h;
  CMTask*            _task;
public:
  G1CMOopClosure(G1CollectedHeap* g1h, ConcurrentMark* cm, CMTask* task);
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(      oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

// Closure to scan the root regions during concurrent marking
class G1RootRegionScanClosure : public MetadataAwareOopClosure {
private:
  G1CollectedHeap* _g1h;
  ConcurrentMark*  _cm;
  uint _worker_id;
public:
  G1RootRegionScanClosure(G1CollectedHeap* g1h, ConcurrentMark* cm,
                          uint worker_id) :
    _g1h(g1h), _cm(cm), _worker_id(worker_id) { }
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(      oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

// Closure that applies the given two closures in sequence.
// Used by the RSet refinement code (when updating RSets
// during an evacuation pause) to record cards containing
// pointers into the collection set.

class G1Mux2Closure : public ExtendedOopClosure {
  OopClosure* _c1;
  OopClosure* _c2;
public:
  G1Mux2Closure(OopClosure *c1, OopClosure *c2);
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)        { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)  { do_oop_nv(p); }
};

// A closure that returns true if it is actually applied
// to a reference

class G1TriggerClosure : public ExtendedOopClosure {
  bool _triggered;
public:
  G1TriggerClosure();
  bool triggered() const { return _triggered; }
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)        { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)  { do_oop_nv(p); }
};

// A closure which uses a triggering closure to determine
// whether to apply an oop closure.

class G1InvokeIfNotTriggeredClosure: public ExtendedOopClosure {
  G1TriggerClosure* _trigger_cl;
  OopClosure* _oop_cl;
public:
  G1InvokeIfNotTriggeredClosure(G1TriggerClosure* t, OopClosure* oc);
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)        { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)  { do_oop_nv(p); }
};

class G1UpdateRSOrPushRefOopClosure: public OopClosure {
  G1CollectedHeap* _g1;
  G1RemSet* _g1_rem_set;
  HeapRegion* _from;
  G1ParPushHeapRSClosure* _push_ref_cl;
  bool _record_refs_into_cset;
  uint _worker_i;

public:
  G1UpdateRSOrPushRefOopClosure(G1CollectedHeap* g1h,
                                G1RemSet* rs,
                                G1ParPushHeapRSClosure* push_ref_cl,
                                bool record_refs_into_cset,
                                uint worker_i = 0);

  void set_from(HeapRegion* from) {
    assert(from != NULL, "from region must be non-NULL");
    _from = from;
  }

  bool self_forwarded(oop obj) {
    markOop m = obj->mark();
    bool result = (m->is_marked() && ((oop)m->decode_pointer() == obj));
    return result;
  }

  template <class T> void do_oop_work(T* p);
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
};

#endif // SHARE_VM_GC_G1_G1OOPCLOSURES_HPP
