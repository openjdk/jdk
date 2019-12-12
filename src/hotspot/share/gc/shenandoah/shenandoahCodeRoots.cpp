/*
 * Copyright (c) 2017, 2019, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahEvacOOMHandler.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahNMethod.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.hpp"

ShenandoahParallelCodeCacheIterator::ShenandoahParallelCodeCacheIterator(const GrowableArray<CodeHeap*>* heaps) {
  _length = heaps->length();
  _iters = NEW_C_HEAP_ARRAY(ShenandoahParallelCodeHeapIterator, _length, mtGC);
  for (int h = 0; h < _length; h++) {
    _iters[h] = ShenandoahParallelCodeHeapIterator(heaps->at(h));
  }
}

ShenandoahParallelCodeCacheIterator::~ShenandoahParallelCodeCacheIterator() {
  FREE_C_HEAP_ARRAY(ParallelCodeHeapIterator, _iters);
}

void ShenandoahParallelCodeCacheIterator::parallel_blobs_do(CodeBlobClosure* f) {
  for (int c = 0; c < _length; c++) {
    _iters[c].parallel_blobs_do(f);
  }
}

ShenandoahParallelCodeHeapIterator::ShenandoahParallelCodeHeapIterator(CodeHeap* heap) :
        _heap(heap), _claimed_idx(0), _finished(false) {
}

void ShenandoahParallelCodeHeapIterator::parallel_blobs_do(CodeBlobClosure* f) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");

  /*
   * Parallel code heap walk.
   *
   * This code makes all threads scan all code heaps, but only one thread would execute the
   * closure on given blob. This is achieved by recording the "claimed" blocks: if a thread
   * had claimed the block, it can process all blobs in it. Others have to fast-forward to
   * next attempt without processing.
   *
   * Late threads would return immediately if iterator is finished.
   */

  if (_finished) {
    return;
  }

  int stride = 256; // educated guess
  int stride_mask = stride - 1;
  assert (is_power_of_2(stride), "sanity");

  int count = 0;
  bool process_block = true;

  for (CodeBlob *cb = CodeCache::first_blob(_heap); cb != NULL; cb = CodeCache::next_blob(_heap, cb)) {
    int current = count++;
    if ((current & stride_mask) == 0) {
      process_block = (current >= _claimed_idx) &&
                      (Atomic::cmpxchg(&_claimed_idx, current, current + stride) == current);
    }
    if (process_block) {
      if (cb->is_alive()) {
        f->do_code_blob(cb);
#ifdef ASSERT
        if (cb->is_nmethod())
          Universe::heap()->verify_nmethod((nmethod*)cb);
#endif
      }
    }
  }

  _finished = true;
}

ShenandoahNMethodTable* ShenandoahCodeRoots::_nmethod_table;
int ShenandoahCodeRoots::_disarmed_value = 1;

void ShenandoahCodeRoots::initialize() {
  _nmethod_table = new ShenandoahNMethodTable();
}

void ShenandoahCodeRoots::register_nmethod(nmethod* nm) {
  switch (ShenandoahCodeRootsStyle) {
    case 0:
    case 1:
      break;
    case 2: {
      assert_locked_or_safepoint(CodeCache_lock);
      _nmethod_table->register_nmethod(nm);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahCodeRoots::unregister_nmethod(nmethod* nm) {
  switch (ShenandoahCodeRootsStyle) {
    case 0:
    case 1: {
      break;
    }
    case 2: {
      assert_locked_or_safepoint(CodeCache_lock);
      _nmethod_table->unregister_nmethod(nm);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahCodeRoots::flush_nmethod(nmethod* nm) {
  switch (ShenandoahCodeRootsStyle) {
    case 0:
    case 1: {
      break;
    }
    case 2: {
      assert_locked_or_safepoint(CodeCache_lock);
      _nmethod_table->flush_nmethod(nm);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahCodeRoots::arm_nmethods() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  _disarmed_value ++;
  // 0 is reserved for new nmethod
  if (_disarmed_value == 0) {
    _disarmed_value = 1;
  }

  JavaThreadIteratorWithHandle jtiwh;
  for (JavaThread *thr = jtiwh.next(); thr != NULL; thr = jtiwh.next()) {
    ShenandoahThreadLocalData::set_disarmed_value(thr, _disarmed_value);
  }
}

class ShenandoahNMethodUnlinkClosure : public NMethodClosure {
private:
  bool            _unloading_occurred;
  volatile bool   _failed;
  ShenandoahHeap* _heap;

  void set_failed() {
    Atomic::store(&_failed, true);
  }

   void unlink(nmethod* nm) {
     // Unlinking of the dependencies must happen before the
     // handshake separating unlink and purge.
     nm->flush_dependencies(false /* delete_immediately */);

     // unlink_from_method will take the CompiledMethod_lock.
     // In this case we don't strictly need it when unlinking nmethods from
     // the Method, because it is only concurrently unlinked by
     // the entry barrier, which acquires the per nmethod lock.
     nm->unlink_from_method();

     if (nm->is_osr_method()) {
       // Invalidate the osr nmethod only once
       nm->invalidate_osr_method();
     }
   }
public:
  ShenandoahNMethodUnlinkClosure(bool unloading_occurred) :
      _unloading_occurred(unloading_occurred),
      _failed(false),
      _heap(ShenandoahHeap::heap()) {}

  virtual void do_nmethod(nmethod* nm) {
    if (failed()) {
      return;
    }

    ShenandoahNMethod* nm_data = ShenandoahNMethod::gc_data(nm);
    assert(!nm_data->is_unregistered(), "Should not see unregistered entry");

    if (!nm->is_alive()) {
      return;
    }

    if (nm->is_unloading()) {
      ShenandoahReentrantLocker locker(nm_data->lock());
      ShenandoahEvacOOMScope evac_scope;
      unlink(nm);
      return;
    }

    ShenandoahReentrantLocker locker(nm_data->lock());

    // Heal oops and disarm
    ShenandoahEvacOOMScope evac_scope;
    if (_heap->is_evacuation_in_progress()) {
      ShenandoahNMethod::heal_nmethod(nm);
    }
    ShenandoahNMethod::disarm_nmethod(nm);

    // Clear compiled ICs and exception caches
    if (!nm->unload_nmethod_caches(_unloading_occurred)) {
      set_failed();
    }
  }

  bool failed() const {
    return Atomic::load(&_failed);
  }
};

class ShenandoahUnlinkTask : public AbstractGangTask {
private:
  ShenandoahNMethodUnlinkClosure      _cl;
  ICRefillVerifier*                   _verifier;
  ShenandoahConcurrentNMethodIterator _iterator;

public:
  ShenandoahUnlinkTask(bool unloading_occurred, ICRefillVerifier* verifier) :
    AbstractGangTask("ShenandoahNMethodUnlinkTask"),
    _cl(unloading_occurred),
    _verifier(verifier),
    _iterator(ShenandoahCodeRoots::table()) {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    _iterator.nmethods_do_begin();
  }

  ~ShenandoahUnlinkTask() {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    _iterator.nmethods_do_end();
  }

  virtual void work(uint worker_id) {
    ICRefillVerifierMark mark(_verifier);
    _iterator.nmethods_do(&_cl);
  }

  bool success() const {
    return !_cl.failed();
  }
};

void ShenandoahCodeRoots::unlink(WorkGang* workers, bool unloading_occurred) {
  assert(ShenandoahConcurrentRoots::should_do_concurrent_class_unloading(),
         "Only when running concurrent class unloading");

  for (;;) {
    ICRefillVerifier verifier;

    {
      ShenandoahUnlinkTask task(unloading_occurred, &verifier);
      workers->run_task(&task);
      if (task.success()) {
        return;
      }
    }

    // Cleaning failed because we ran out of transitional IC stubs,
    // so we have to refill and try again. Refilling requires taking
    // a safepoint, so we temporarily leave the suspendible thread set.
    SuspendibleThreadSetLeaver sts;
    InlineCacheBuffer::refill_ic_stubs();
  }
}

class ShenandoahNMethodPurgeClosure : public NMethodClosure {
public:
  virtual void do_nmethod(nmethod* nm) {
    if (nm->is_alive() && nm->is_unloading()) {
      nm->make_unloaded();
    }
  }
};

class ShenandoahNMethodPurgeTask : public AbstractGangTask {
private:
  ShenandoahNMethodPurgeClosure       _cl;
  ShenandoahConcurrentNMethodIterator _iterator;

public:
  ShenandoahNMethodPurgeTask() :
    AbstractGangTask("ShenandoahNMethodPurgeTask"),
    _cl(),
    _iterator(ShenandoahCodeRoots::table()) {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    _iterator.nmethods_do_begin();
  }

  ~ShenandoahNMethodPurgeTask() {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    _iterator.nmethods_do_end();
  }

  virtual void work(uint worker_id) {
    _iterator.nmethods_do(&_cl);
  }
};

void ShenandoahCodeRoots::purge(WorkGang* workers) {
  assert(ShenandoahConcurrentRoots::should_do_concurrent_class_unloading(),
         "Only when running concurrent class unloading");

  ShenandoahNMethodPurgeTask task;
  workers->run_task(&task);
}

ShenandoahCodeRootsIterator::ShenandoahCodeRootsIterator() :
        _par_iterator(CodeCache::heaps()),
        _table_snapshot(NULL) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");
  assert(!Thread::current()->is_Worker_thread(), "Should not be acquired by workers");
  switch (ShenandoahCodeRootsStyle) {
    case 0:
    case 1: {
      // No need to do anything here
      break;
    }
    case 2: {
      CodeCache_lock->lock_without_safepoint_check();
      _table_snapshot = ShenandoahCodeRoots::table()->snapshot_for_iteration();
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

ShenandoahCodeRootsIterator::~ShenandoahCodeRootsIterator() {
  switch (ShenandoahCodeRootsStyle) {
    case 0:
    case 1: {
      // No need to do anything here
      break;
    }
    case 2: {
      ShenandoahCodeRoots::table()->finish_iteration(_table_snapshot);
      _table_snapshot = NULL;
      CodeCache_lock->unlock();
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

template<bool CSET_FILTER>
void ShenandoahCodeRootsIterator::dispatch_parallel_blobs_do(CodeBlobClosure *f) {
  switch (ShenandoahCodeRootsStyle) {
    case 0: {
      if (_seq_claimed.try_set()) {
        CodeCache::blobs_do(f);
      }
      break;
    }
    case 1: {
      _par_iterator.parallel_blobs_do(f);
      break;
    }
    case 2: {
      ShenandoahCodeRootsIterator::fast_parallel_blobs_do<CSET_FILTER>(f);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahAllCodeRootsIterator::possibly_parallel_blobs_do(CodeBlobClosure *f) {
  ShenandoahCodeRootsIterator::dispatch_parallel_blobs_do<false>(f);
}

void ShenandoahCsetCodeRootsIterator::possibly_parallel_blobs_do(CodeBlobClosure *f) {
  ShenandoahCodeRootsIterator::dispatch_parallel_blobs_do<true>(f);
}

template <bool CSET_FILTER>
void ShenandoahCodeRootsIterator::fast_parallel_blobs_do(CodeBlobClosure *f) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");
  assert(_table_snapshot != NULL, "Sanity");
  _table_snapshot->parallel_blobs_do<CSET_FILTER>(f);
}

