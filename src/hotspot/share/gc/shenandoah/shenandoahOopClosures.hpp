/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHOOPCLOSURES_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHOOPCLOSURES_HPP

#include "gc/shared/referenceProcessor.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahStrDedupQueue.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "memory/iterator.hpp"
#include "runtime/thread.hpp"

enum GenerationMode {
  YOUNG,
  GLOBAL
};

enum UpdateRefsMode {
  NONE,       // No reference updating
  SIMPLE,     // Reference updating using simple store
  CONCURRENT  // Reference updating using CAS
};

enum StringDedupMode {
  NO_DEDUP,      // Do not do anything for String deduplication
  ENQUEUE_DEDUP  // Enqueue candidate Strings for deduplication
};

class ShenandoahMarkRefsSuperClosure : public MetadataVisitingOopIterateClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _mark_context;
  bool _weak;

protected:
  template <class T, GenerationMode GENERATION, UpdateRefsMode UPDATE_MODE, StringDedupMode STRING_DEDUP>
  void work(T *p);

public:
  ShenandoahMarkRefsSuperClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp);

  bool is_weak() const {
    return _weak;
  }

  void set_weak(bool weak) {
    _weak = weak;
  }
};

template <GenerationMode GENERATION>
class ShenandoahMarkUpdateRefsClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, CONCURRENT, NO_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
          ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

template <GenerationMode GENERATION>
class ShenandoahMarkUpdateRefsDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, CONCURRENT, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsDedupClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
          ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

template <GenerationMode GENERATION>
class ShenandoahMarkUpdateRefsMetadataClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, CONCURRENT, NO_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsMetadataClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

template <GenerationMode GENERATION>
class ShenandoahMarkUpdateRefsMetadataDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, CONCURRENT, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkUpdateRefsMetadataDedupClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
  ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

template <GenerationMode GENERATION>
class ShenandoahMarkRefsClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, NONE, NO_DEDUP>(p); }

public:
  ShenandoahMarkRefsClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

template <GenerationMode GENERATION>
class ShenandoahMarkRefsDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, NONE, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkRefsDedupClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return false; }
};

template <GenerationMode GENERATION>
class ShenandoahMarkRefsMetadataClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, NONE, NO_DEDUP>(p); }

public:
  ShenandoahMarkRefsMetadataClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

template <GenerationMode GENERATION>
class ShenandoahMarkRefsMetadataDedupClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION, NONE, ENQUEUE_DEDUP>(p); }

public:
  ShenandoahMarkRefsMetadataDedupClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
    ShenandoahMarkRefsSuperClosure(q, rp) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual bool do_metadata()        { return true; }
};

class ShenandoahUpdateHeapRefsClosure : public BasicOopIterateClosure {
private:
  ShenandoahHeap* _heap;

  template <class T>
  void do_oop_work(T* p);

public:
  ShenandoahUpdateHeapRefsClosure() :
    _heap(ShenandoahHeap::heap()) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHOOPCLOSURES_HPP
