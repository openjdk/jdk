/*
 * Copyright (c) 2015, 2022, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "memory/iterator.hpp"
#include "runtime/javaThread.hpp"

enum StringDedupMode {
  NO_DEDUP,      // Do not do anything for String deduplication
  ENQUEUE_DEDUP, // Enqueue candidate Strings for deduplication, if meet age threshold
  ALWAYS_DEDUP   // Enqueue Strings for deduplication
};

class ShenandoahMarkRefsSuperClosure : public MetadataVisitingOopIterateClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahObjToScanQueue* _old_queue;
  ShenandoahMarkingContext* const _mark_context;
  bool _weak;

protected:
  template <class T, GenerationMode GENERATION>
  void work(T *p);

public:
  ShenandoahMarkRefsSuperClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp,  ShenandoahObjToScanQueue* old_queue = nullptr);

  bool is_weak() const {
    return _weak;
  }

  void set_weak(bool weak) {
    _weak = weak;
  }

  virtual void do_nmethod(nmethod* nm) {
    assert(!is_weak(), "Can't handle weak marking of nmethods");
    nm->run_nmethod_entry_barrier();
  }
};

class ShenandoahMarkUpdateRefsSuperClosure : public ShenandoahMarkRefsSuperClosure {
protected:
  ShenandoahHeap* const _heap;

  template <class T, GenerationMode GENERATION>
  inline void work(T* p);

public:
  ShenandoahMarkUpdateRefsSuperClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp, ShenandoahObjToScanQueue* old = nullptr) :
    ShenandoahMarkRefsSuperClosure(q, rp, old),
    _heap(ShenandoahHeap::heap()) {
    assert(_heap->is_stw_gc_in_progress(), "Can only be used for STW GC");
  };
};

template <GenerationMode GENERATION>
class ShenandoahMarkUpdateRefsClosure : public ShenandoahMarkUpdateRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION>(p); }

public:
  ShenandoahMarkUpdateRefsClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp, ShenandoahObjToScanQueue* old = nullptr) :
    ShenandoahMarkUpdateRefsSuperClosure(q, rp, old) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
};

template <GenerationMode GENERATION>
class ShenandoahMarkRefsClosure : public ShenandoahMarkRefsSuperClosure {
private:
  template <class T>
  inline void do_oop_work(T* p)     { work<T, GENERATION>(p); }

public:
  ShenandoahMarkRefsClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp, ShenandoahObjToScanQueue* old = nullptr) :
    ShenandoahMarkRefsSuperClosure(q, rp, old) {};

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahUpdateRefsSuperClosure : public ShenandoahOopClosureBase {
protected:
  ShenandoahHeap* _heap;

public:
  ShenandoahUpdateRefsSuperClosure() :  _heap(ShenandoahHeap::heap()) {}
};

class ShenandoahSTWUpdateRefsClosure : public ShenandoahUpdateRefsSuperClosure {
private:
  template<class T>
  inline void work(T* p);

public:
  ShenandoahSTWUpdateRefsClosure() : ShenandoahUpdateRefsSuperClosure() {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must only be used at safepoints");
  }

  virtual void do_oop(narrowOop* p) { work(p); }
  virtual void do_oop(oop* p)       { work(p); }
};

class ShenandoahConcUpdateRefsClosure : public ShenandoahUpdateRefsSuperClosure {
private:
  template<class T>
  inline void work(T* p);

public:
  ShenandoahConcUpdateRefsClosure() : ShenandoahUpdateRefsSuperClosure() {}

  virtual void do_oop(narrowOop* p) { work(p); }
  virtual void do_oop(oop* p)       { work(p); }
};

class ShenandoahVerifyRemSetClosure : public BasicOopIterateClosure {
  protected:
  bool _init_mark;
  ShenandoahHeap* _heap;
  RememberedScanner* _scanner;

  public:
// Argument distinguishes between initial mark or start of update refs verification.
  ShenandoahVerifyRemSetClosure(bool init_mark) :
      _init_mark(init_mark),
      _heap(ShenandoahHeap::heap()),
      _scanner(_heap->card_scan()) {  }
  template<class T>
  inline void work(T* p);

  virtual void do_oop(narrowOop* p) { work(p); }
  virtual void do_oop(oop* p) { work(p); }
};

class ShenandoahSetRememberedCardsToDirtyClosure : public BasicOopIterateClosure {

protected:
  ShenandoahHeap* _heap;
  RememberedScanner* _scanner;

public:

  ShenandoahSetRememberedCardsToDirtyClosure() :
      _heap(ShenandoahHeap::heap()),
      _scanner(_heap->card_scan()) {  }

  template<class T>
  inline void work(T* p);

  virtual void do_oop(narrowOop* p) { work(p); }
  virtual void do_oop(oop* p) { work(p); }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHOOPCLOSURES_HPP
