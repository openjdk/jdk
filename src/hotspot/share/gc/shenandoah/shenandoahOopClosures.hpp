/*
 * Copyright (c) 2015, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHOOPCLOSURES_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHOOPCLOSURES_HPP

#include "gc/shared/referenceProcessor.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahStrDedupQueue.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "memory/iterator.hpp"
#include "runtime/thread.hpp"

enum UpdateRefsMode {
  NONE,       // No reference updating
  RESOLVE,    // Only a read-barrier (no reference updating)
  SIMPLE,     // Reference updating using simple store
  CONCURRENT  // Reference updating using CAS
};

enum StringDedupMode {
  NO_DEDUP,      // Do not do anything for String deduplication
  ENQUEUE_DEDUP, // Enqueue candidate Strings for deduplication
};

class ShenandoahMarkRefsSuperClosure : public MetadataVisitingOopIterateClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _mark_context;

protected:
  template <class T, UpdateRefsMode UPDATE_MODE, StringDedupMode STRING_DEDUP>
  void work(T *p);

public:
  ShenandoahMarkRefsSuperClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp);
};

class ShenandoahMarkUpdateRefsClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, CONCURRENT, NO_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
          ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

class ShenandoahMarkUpdateRefsDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, CONCURRENT, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsDedupClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
          ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

class ShenandoahMarkUpdateRefsMetadataClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, CONCURRENT, NO_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsMetadataClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

class ShenandoahMarkUpdateRefsMetadataDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, CONCURRENT, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsMetadataDedupClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
  ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

class ShenandoahMarkRefsClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, NONE, NO_DEDUP>(p); }

public:
  ShenandoahMarkRefsClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

class ShenandoahMarkRefsDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, NONE, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkRefsDedupClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

class ShenandoahMarkResolveRefsClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, RESOLVE, NO_DEDUP>(p); }

public:
  ShenandoahMarkResolveRefsClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

class ShenandoahMarkRefsMetadataClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, NONE, NO_DEDUP>(p); }

public:
  ShenandoahMarkRefsMetadataClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

class ShenandoahMarkRefsMetadataDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, NONE, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkRefsMetadataDedupClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

class ShenandoahUpdateHeapRefsSuperClosure : public BasicOopIterateClosure {
private:
  ShenandoahHeap* _heap;
public:
  ShenandoahUpdateHeapRefsSuperClosure() :
    _heap(ShenandoahHeap::heap()) {}

  template <class T>
  void work(T *p);
};

class ShenandoahUpdateHeapRefsClosure : public ShenandoahUpdateHeapRefsSuperClosure {
private:
  template <class T>
  inline  void do_oop_work(T* p)    { work<T>(p); }

public:
  ShenandoahUpdateHeapRefsClosure() : ShenandoahUpdateHeapRefsSuperClosure() {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahTraversalSuperClosure : public MetadataVisitingOopIterateClosure {
private:
  ShenandoahTraversalGC* const _traversal_gc;
  Thread* const _thread;
  ShenandoahObjToScanQueue* const _queue;
  ShenandoahMarkingContext* const _mark_context;
protected:
  ShenandoahTraversalSuperClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    MetadataVisitingOopIterateClosure(rp),
    _traversal_gc(ShenandoahHeap::heap()->traversal_gc()),
    _thread(Thread::current()),
    _queue(q),
    _mark_context(ShenandoahHeap::heap()->marking_context()) {
  }

  template <class T, bool STRING_DEDUP, bool DEGEN>
  void work(T* p);

};

class ShenandoahTraversalClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, false, false>(p); }

public:
  ShenandoahTraversalClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return false; }
};

class ShenandoahTraversalMetadataClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, false, false>(p); }

public:
  ShenandoahTraversalMetadataClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return true; }
};

class ShenandoahTraversalDedupClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, true, false>(p); }

public:
  ShenandoahTraversalDedupClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return false; }
};

class ShenandoahTraversalMetadataDedupClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, true, false>(p); }

public:
  ShenandoahTraversalMetadataDedupClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return true; }
};

class ShenandoahTraversalDegenClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, false, true>(p); }

public:
  ShenandoahTraversalDegenClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return false; }
};

class ShenandoahTraversalMetadataDegenClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, false, true>(p); }

public:
  ShenandoahTraversalMetadataDegenClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return true; }
};

class ShenandoahTraversalDedupDegenClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, true, true>(p); }

public:
  ShenandoahTraversalDedupDegenClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return false; }
};

class ShenandoahTraversalMetadataDedupDegenClosure : public ShenandoahTraversalSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, true, true>(p); }

public:
  ShenandoahTraversalMetadataDedupDegenClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
    ShenandoahTraversalSuperClosure(q, rp) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  virtual bool do_metadata()        { return true; }
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHOOPCLOSURES_HPP
