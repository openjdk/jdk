/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

class HeapRegion;
class G1CollectedHeap;
class G1RemSet;
class ConcurrentMark;
class DirtyCardToOopClosure;
class CMBitMap;
class CMMarkStack;
class G1ParScanThreadState;

// A class that scans oops in a given heap region (much as OopsInGenClosure
// scans oops in a generation.)
class OopsInHeapRegionClosure: public OopsInGenClosure {
protected:
  HeapRegion* _from;
public:
  virtual void set_region(HeapRegion* from) { _from = from; }
};

class G1ParClosureSuper : public OopsInHeapRegionClosure {
protected:
  G1CollectedHeap* _g1;
  G1RemSet* _g1_rem;
  ConcurrentMark* _cm;
  G1ParScanThreadState* _par_scan_state;
public:
  G1ParClosureSuper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state);
  bool apply_to_weak_ref_discovered_field() { return true; }
};

class G1ParPushHeapRSClosure : public G1ParClosureSuper {
public:
  G1ParPushHeapRSClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
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
};

#define G1_PARTIAL_ARRAY_MASK 0x2

template <class T> inline bool has_partial_array_mask(T* ref) {
  return ((uintptr_t)ref & G1_PARTIAL_ARRAY_MASK) == G1_PARTIAL_ARRAY_MASK;
}

template <class T> inline T* set_partial_array_mask(T obj) {
  assert(((uintptr_t)obj & G1_PARTIAL_ARRAY_MASK) == 0, "Information loss!");
  return (T*) ((uintptr_t)obj | G1_PARTIAL_ARRAY_MASK);
}

template <class T> inline oop clear_partial_array_mask(T* ref) {
  return oop((intptr_t)ref & ~G1_PARTIAL_ARRAY_MASK);
}

class G1ParScanPartialArrayClosure : public G1ParClosureSuper {
  G1ParScanClosure _scanner;
public:
  G1ParScanPartialArrayClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
    G1ParClosureSuper(g1, par_scan_state), _scanner(g1, par_scan_state) { }
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};


class G1ParCopyHelper : public G1ParClosureSuper {
  G1ParScanClosure *_scanner;
protected:
  template <class T> void mark_forwardee(T* p);
  oop copy_to_survivor_space(oop obj);
public:
  G1ParCopyHelper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state,
                  G1ParScanClosure *scanner) :
    G1ParClosureSuper(g1, par_scan_state), _scanner(scanner) { }
};

template<bool do_gen_barrier, G1Barrier barrier,
         bool do_mark_forwardee>
class G1ParCopyClosure : public G1ParCopyHelper {
  G1ParScanClosure _scanner;
  template <class T> void do_oop_work(T* p);
public:
  G1ParCopyClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
    _scanner(g1, par_scan_state), G1ParCopyHelper(g1, par_scan_state, &_scanner) { }
  template <class T> void do_oop_nv(T* p) {
    do_oop_work(p);
    if (do_mark_forwardee)
      mark_forwardee(p);
  }
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

typedef G1ParCopyClosure<false, G1BarrierNone, false> G1ParScanExtRootClosure;
typedef G1ParCopyClosure<true,  G1BarrierNone, false> G1ParScanPermClosure;
typedef G1ParCopyClosure<false, G1BarrierRS,   false> G1ParScanHeapRSClosure;
typedef G1ParCopyClosure<false, G1BarrierNone, true> G1ParScanAndMarkExtRootClosure;
typedef G1ParCopyClosure<true,  G1BarrierNone, true> G1ParScanAndMarkPermClosure;
typedef G1ParCopyClosure<false, G1BarrierRS,   true> G1ParScanAndMarkHeapRSClosure;

// This is the only case when we set skip_cset_test. Basically, this
// closure is (should?) only be called directly while we're draining
// the overflow and task queues. In that case we know that the
// reference in question points into the collection set, otherwise we
// would not have pushed it on the queue. The following is defined in
// g1_specialized_oop_closures.hpp.
// typedef G1ParCopyClosure<false, G1BarrierEvac, false, true> G1ParScanHeapEvacClosure;
// We need a separate closure to handle references during evacuation
// failure processing, as we cannot asume that the reference already
// points into the collection set (like G1ParScanHeapEvacClosure does).
typedef G1ParCopyClosure<false, G1BarrierEvac, false> G1ParScanHeapEvacFailureClosure;

class FilterIntoCSClosure: public OopClosure {
  G1CollectedHeap* _g1;
  OopClosure* _oc;
  DirtyCardToOopClosure* _dcto_cl;
public:
  FilterIntoCSClosure(  DirtyCardToOopClosure* dcto_cl,
                        G1CollectedHeap* g1, OopClosure* oc) :
    _dcto_cl(dcto_cl), _g1(g1), _oc(oc)
  {}
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p)        { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)  { do_oop_nv(p); }
  bool apply_to_weak_ref_discovered_field() { return true; }
  bool do_header() { return false; }
};

class FilterInHeapRegionAndIntoCSClosure : public OopsInHeapRegionClosure {
  G1CollectedHeap* _g1;
  OopsInHeapRegionClosure* _oc;
public:
  FilterInHeapRegionAndIntoCSClosure(G1CollectedHeap* g1,
                                     OopsInHeapRegionClosure* oc) :
    _g1(g1), _oc(oc)
  {}
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
  bool apply_to_weak_ref_discovered_field() { return true; }
  bool do_header() { return false; }
  void set_region(HeapRegion* from) {
    _oc->set_region(from);
  }
};

class FilterAndMarkInHeapRegionAndIntoCSClosure : public OopsInHeapRegionClosure {
  G1CollectedHeap* _g1;
  ConcurrentMark* _cm;
  OopsInHeapRegionClosure* _oc;
public:
  FilterAndMarkInHeapRegionAndIntoCSClosure(G1CollectedHeap* g1,
                                            OopsInHeapRegionClosure* oc,
                                            ConcurrentMark* cm)
  : _g1(g1), _oc(oc), _cm(cm) { }

  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
  bool apply_to_weak_ref_discovered_field() { return true; }
  bool do_header() { return false; }
  void set_region(HeapRegion* from) {
    _oc->set_region(from);
  }
};

class FilterOutOfRegionClosure: public OopClosure {
  HeapWord* _r_bottom;
  HeapWord* _r_end;
  OopClosure* _oc;
  int _out_of_region;
public:
  FilterOutOfRegionClosure(HeapRegion* r, OopClosure* oc);
  template <class T> void do_oop_nv(T* p);
  virtual void do_oop(oop* p) { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
  bool apply_to_weak_ref_discovered_field() { return true; }
  bool do_header() { return false; }
  int out_of_region() { return _out_of_region; }
};
