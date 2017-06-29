/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1InCSetState.hpp"
#include "memory/iterator.hpp"
#include "oops/markOop.hpp"

class HeapRegion;
class G1CollectedHeap;
class G1RemSet;
class G1ConcurrentMark;
class DirtyCardToOopClosure;
class G1CMBitMap;
class G1ParScanThreadState;
class G1CMTask;
class ReferenceProcessor;

// A class that scans oops in a given heap region (much as OopsInGenClosure
// scans oops in a generation.)
class OopsInHeapRegionClosure: public ExtendedOopClosure {
protected:
  HeapRegion* _from;
public:
  void set_region(HeapRegion* from) { _from = from; }
};

class G1ScanClosureBase : public OopsInHeapRegionClosure {
protected:
  G1CollectedHeap* _g1;
  G1ParScanThreadState* _par_scan_state;

  G1ScanClosureBase(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state);
  ~G1ScanClosureBase() { }

  template <class T>
  inline void prefetch_and_push(T* p, oop const obj);

  template <class T>
  inline void handle_non_cset_obj_common(InCSetState const state, T* p, oop const obj);
public:
  // This closure needs special handling for InstanceRefKlass.
  virtual ReferenceIterationMode reference_iteration_mode() { return DO_DISCOVERED_AND_DISCOVERY; }
};

// Used during the Update RS phase to refine remaining cards in the DCQ during garbage collection.
class G1ScanObjsDuringUpdateRSClosure: public G1ScanClosureBase {
  uint _worker_i;
  bool _has_refs_into_cset;

public:
  G1ScanObjsDuringUpdateRSClosure(G1CollectedHeap* g1h,
                                  G1ParScanThreadState* pss,
                                  uint worker_i) :
    G1ScanClosureBase(g1h, pss), _has_refs_into_cset(false), _worker_i(worker_i) { }

  void reset_has_refs_into_cset() { _has_refs_into_cset = false; }
  bool has_refs_into_cset() const { return _has_refs_into_cset; }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
  virtual void do_oop(oop* p) { do_oop_nv(p); }
};

// Used during the Scan RS phase to scan cards from the remembered set during garbage collection.
class G1ScanObjsDuringScanRSClosure : public G1ScanClosureBase {
public:
  G1ScanObjsDuringScanRSClosure(G1CollectedHeap* g1,
                                G1ParScanThreadState* par_scan_state):
    G1ScanClosureBase(g1, par_scan_state) { }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)          { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)    { do_oop_nv(p); }
};

// This closure is applied to the fields of the objects that have just been copied during evacuation.
class G1ScanEvacuatedObjClosure : public G1ScanClosureBase {
public:
  G1ScanEvacuatedObjClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
    G1ScanClosureBase(g1, par_scan_state) { }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)          { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)    { do_oop_nv(p); }

  void set_ref_processor(ReferenceProcessor* rp) {
    set_ref_processor_internal(rp);
  }
};

// Add back base class for metadata
class G1ParCopyHelper : public OopClosure {
protected:
  G1CollectedHeap* _g1;
  G1ParScanThreadState* _par_scan_state;
  uint _worker_id;              // Cache value from par_scan_state.
  Klass* _scanned_klass;
  G1ConcurrentMark* _cm;

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
      G1ParCopyHelper(g1, par_scan_state) { }

  template <class T> void do_oop_work(T* p);
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
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

// Closure for iterating over object fields during concurrent marking
class G1CMOopClosure : public MetadataAwareOopClosure {
protected:
  G1ConcurrentMark*  _cm;
private:
  G1CollectedHeap*   _g1h;
  G1CMTask*          _task;
public:
  G1CMOopClosure(G1CollectedHeap* g1h, G1ConcurrentMark* cm, G1CMTask* task);
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(      oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

// Closure to scan the root regions during concurrent marking
class G1RootRegionScanClosure : public MetadataAwareOopClosure {
private:
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;
public:
  G1RootRegionScanClosure(G1CollectedHeap* g1h, G1ConcurrentMark* cm) :
    _g1h(g1h), _cm(cm) { }
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(      oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

class G1ConcurrentRefineOopClosure: public ExtendedOopClosure {
  G1CollectedHeap* _g1;
  uint _worker_i;

public:
  G1ConcurrentRefineOopClosure(G1CollectedHeap* g1h, uint worker_i) :
    _g1(g1h),
    _worker_i(worker_i) {
  }

  // This closure needs special handling for InstanceRefKlass.
  virtual ReferenceIterationMode reference_iteration_mode() { return DO_DISCOVERED_AND_DISCOVERY; }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
};

#endif // SHARE_VM_GC_G1_G1OOPCLOSURES_HPP
