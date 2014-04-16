/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1OOPCLOSURES_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1OOPCLOSURES_HPP

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
  uint _worker_id;
public:
  G1ParClosureSuper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state);
  bool apply_to_weak_ref_discovered_field() { return true; }
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
  G1ParScanClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state, ReferenceProcessor* rp) :
    G1ParClosureSuper(g1, par_scan_state)
  {
    assert(_ref_processor == NULL, "sanity");
    _ref_processor = rp;
  }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)          { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)    { do_oop_nv(p); }
};

// Add back base class for metadata
class G1ParCopyHelper : public G1ParClosureSuper {
protected:
  Klass* _scanned_klass;
  ConcurrentMark* _cm;

  // Mark the object if it's not already marked. This is used to mark
  // objects pointed to by roots that are guaranteed not to move
  // during the GC (i.e., non-CSet objects). It is MT-safe.
  void mark_object(oop obj);

  // Mark the object if it's not already marked. This is used to mark
  // objects pointed to by roots that have been forwarded during a
  // GC. It is MT-safe.
  void mark_forwarded_object(oop from_obj, oop to_obj);
 public:
  G1ParCopyHelper(G1CollectedHeap* g1,  G1ParScanThreadState* par_scan_state);

  void set_scanned_klass(Klass* k) { _scanned_klass = k; }
  template <class T> void do_klass_barrier(T* p, oop new_obj);
};

template <G1Barrier barrier, bool do_mark_object>
class G1ParCopyClosure : public G1ParCopyHelper {
private:
  template <class T> void do_oop_work(T* p);

public:
  G1ParCopyClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state,
                   ReferenceProcessor* rp) :
      G1ParCopyHelper(g1, par_scan_state) {
    assert(_ref_processor == NULL, "sanity");
  }

  template <class T> void do_oop_nv(T* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

typedef G1ParCopyClosure<G1BarrierNone, false> G1ParScanExtRootClosure;
typedef G1ParCopyClosure<G1BarrierKlass, false> G1ParScanMetadataClosure;


typedef G1ParCopyClosure<G1BarrierNone, true> G1ParScanAndMarkExtRootClosure;
typedef G1ParCopyClosure<G1BarrierKlass, true> G1ParScanAndMarkMetadataClosure;

// We use a separate closure to handle references during evacuation
// failure processing.

typedef G1ParCopyClosure<G1BarrierEvac, false> G1ParScanHeapEvacFailureClosure;

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
class G1CMOopClosure : public ExtendedOopClosure {
private:
  G1CollectedHeap*   _g1h;
  ConcurrentMark*    _cm;
  CMTask*            _task;
public:
  G1CMOopClosure(G1CollectedHeap* g1h, ConcurrentMark* cm, CMTask* task);
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(      oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

// Closure to scan the root regions during concurrent marking
class G1RootRegionScanClosure : public ExtendedOopClosure {
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

class G1UpdateRSOrPushRefOopClosure: public ExtendedOopClosure {
  G1CollectedHeap* _g1;
  G1RemSet* _g1_rem_set;
  HeapRegion* _from;
  OopsInHeapRegionClosure* _push_ref_cl;
  bool _record_refs_into_cset;
  int _worker_i;

public:
  G1UpdateRSOrPushRefOopClosure(G1CollectedHeap* g1h,
                                G1RemSet* rs,
                                OopsInHeapRegionClosure* push_ref_cl,
                                bool record_refs_into_cset,
                                int worker_i = 0);

  void set_from(HeapRegion* from) {
    assert(from != NULL, "from region must be non-NULL");
    _from = from;
  }

  bool self_forwarded(oop obj) {
    bool result = (obj->is_forwarded() && (obj->forwardee()== obj));
    return result;
  }

  bool apply_to_weak_ref_discovered_field() { return true; }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1OOPCLOSURES_HPP
