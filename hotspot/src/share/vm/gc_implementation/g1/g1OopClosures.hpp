/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

class HeapRegion;
class G1CollectedHeap;
class G1RemSet;
class HRInto_G1RemSet;
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


class G1ScanAndBalanceClosure : public OopClosure {
  G1CollectedHeap* _g1;
  static int _nq;
public:
  G1ScanAndBalanceClosure(G1CollectedHeap* g1) : _g1(g1) { }
  inline  void do_oop_nv(oop* p);
  inline  void do_oop_nv(narrowOop* p) { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p)    { guarantee(false, "NYI"); }
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

class G1ParScanClosure : public G1ParClosureSuper {
public:
  G1ParScanClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
    G1ParClosureSuper(g1, par_scan_state) { }
  void do_oop_nv(oop* p);   // should be made inline
  inline  void do_oop_nv(narrowOop* p) { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p)          { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p)    { do_oop_nv(p); }
};

#define G1_PARTIAL_ARRAY_MASK 1

inline bool has_partial_array_mask(oop* ref) {
  return (intptr_t) ref & G1_PARTIAL_ARRAY_MASK;
}

inline oop* set_partial_array_mask(oop obj) {
  return (oop*) ((intptr_t) obj | G1_PARTIAL_ARRAY_MASK);
}

inline oop clear_partial_array_mask(oop* ref) {
  return oop((intptr_t) ref & ~G1_PARTIAL_ARRAY_MASK);
}

class G1ParScanPartialArrayClosure : public G1ParClosureSuper {
  G1ParScanClosure _scanner;
  template <class T> void process_array_chunk(oop obj, int start, int end);
public:
  G1ParScanPartialArrayClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
    G1ParClosureSuper(g1, par_scan_state), _scanner(g1, par_scan_state) { }
  void do_oop_nv(oop* p);
  void do_oop_nv(narrowOop* p)      { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};


class G1ParCopyHelper : public G1ParClosureSuper {
  G1ParScanClosure *_scanner;
protected:
  void mark_forwardee(oop* p);
  oop copy_to_survivor_space(oop obj);
public:
  G1ParCopyHelper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state,
                  G1ParScanClosure *scanner) :
    G1ParClosureSuper(g1, par_scan_state), _scanner(scanner) { }
};

template<bool do_gen_barrier, G1Barrier barrier,
         bool do_mark_forwardee, bool skip_cset_test>
class G1ParCopyClosure : public G1ParCopyHelper {
  G1ParScanClosure _scanner;
  void do_oop_work(oop* p);
  void do_oop_work(narrowOop* p) { guarantee(false, "NYI"); }
public:
  G1ParCopyClosure(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
    _scanner(g1, par_scan_state), G1ParCopyHelper(g1, par_scan_state, &_scanner) { }
  inline void do_oop_nv(oop* p) {
    do_oop_work(p);
    if (do_mark_forwardee)
      mark_forwardee(p);
  }
  inline void do_oop_nv(narrowOop* p) { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p)       { do_oop_nv(p); }
  virtual void do_oop(narrowOop* p) { do_oop_nv(p); }
};

typedef G1ParCopyClosure<false, G1BarrierNone, false, false> G1ParScanExtRootClosure;
typedef G1ParCopyClosure<true,  G1BarrierNone, false, false> G1ParScanPermClosure;
typedef G1ParCopyClosure<false, G1BarrierNone, true,  false> G1ParScanAndMarkExtRootClosure;
typedef G1ParCopyClosure<true,  G1BarrierNone, true,  false> G1ParScanAndMarkPermClosure;
typedef G1ParCopyClosure<false, G1BarrierRS,   false, false> G1ParScanHeapRSClosure;
typedef G1ParCopyClosure<false, G1BarrierRS,   true,  false> G1ParScanAndMarkHeapRSClosure;
// This is the only case when we set skip_cset_test. Basically, this
// closure is (should?) only be called directly while we're draining
// the overflow and task queues. In that case we know that the
// reference in question points into the collection set, otherwise we
// would not have pushed it on the queue.
typedef G1ParCopyClosure<false, G1BarrierEvac, false, true> G1ParScanHeapEvacClosure;
// We need a separate closure to handle references during evacuation
// failure processing, as it cannot asume that the reference already
 // points to the collection set (like G1ParScanHeapEvacClosure does).
typedef G1ParCopyClosure<false, G1BarrierEvac, false, false> G1ParScanHeapEvacFailureClosure;

class FilterIntoCSClosure: public OopClosure {
  G1CollectedHeap* _g1;
  OopClosure* _oc;
  DirtyCardToOopClosure* _dcto_cl;
public:
  FilterIntoCSClosure(  DirtyCardToOopClosure* dcto_cl,
                        G1CollectedHeap* g1, OopClosure* oc) :
    _dcto_cl(dcto_cl), _g1(g1), _oc(oc)
  {}
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p) { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p)   { guarantee(false, "NYI"); }
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
  inline  void do_oop_nv(oop* p);
  inline  void do_oop_nv(narrowOop* p) { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p)    { guarantee(false, "NYI"); }
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

  inline  void do_oop_nv(oop* p);
  inline  void do_oop_nv(narrowOop* p) { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p)    { guarantee(false, "NYI"); }
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
  inline  void do_oop_nv(oop* p);
  inline  void do_oop_nv(narrowOop* p) { guarantee(false, "NYI"); }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p)   { guarantee(false, "NYI"); }
  bool apply_to_weak_ref_discovered_field() { return true; }
  bool do_header() { return false; }
  int out_of_region() { return _out_of_region; }
};
