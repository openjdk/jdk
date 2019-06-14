/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP

#include "code/codeCache.hpp"
#include "gc/shared/oopStorageParState.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "gc/shared/weakProcessorPhaseTimes.hpp"
#include "gc/shared/workgroup.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"

class ShenandoahSerialRoot {
public:
  typedef void (*OopsDo)(OopClosure*);
private:
  volatile bool                             _claimed;
  const OopsDo                              _oops_do;
  const ShenandoahPhaseTimings::GCParPhases _phase;

public:
  ShenandoahSerialRoot(OopsDo oops_do, ShenandoahPhaseTimings::GCParPhases);
  void oops_do(OopClosure* cl, uint worker_id);
};

class ShenandoahSerialRoots {
private:
  ShenandoahSerialRoot  _universe_root;
  ShenandoahSerialRoot  _object_synchronizer_root;
  ShenandoahSerialRoot  _management_root;
  ShenandoahSerialRoot  _system_dictionary_root;
  ShenandoahSerialRoot  _jvmti_root;
public:
  ShenandoahSerialRoots();
  void oops_do(OopClosure* cl, uint worker_id);
};

class ShenandoahJNIHandleRoots : public ShenandoahSerialRoot {
public:
  ShenandoahJNIHandleRoots();
};

class ShenandoahThreadRoots {
private:
  const bool _is_par;
public:
  ShenandoahThreadRoots(bool is_par);
  ~ShenandoahThreadRoots();

  void oops_do(OopClosure* oops_cl, CodeBlobClosure* code_cl, uint worker_id);
  void threads_do(ThreadClosure* tc, uint worker_id);
};

class ShenandoahWeakRoots {
private:
  WeakProcessorPhaseTimes _process_timings;
  WeakProcessor::Task     _task;
public:
  ShenandoahWeakRoots(uint n_workers);
  ~ShenandoahWeakRoots();

  template <typename IsAlive, typename KeepAlive>
  void oops_do(IsAlive* is_alive, KeepAlive* keep_alive, uint worker_id);
};

class ShenandoahStringDedupRoots {
public:
  ShenandoahStringDedupRoots();
  ~ShenandoahStringDedupRoots();

  void oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id);
};

template <typename ITR>
class ShenandoahCodeCacheRoots {
private:
  ITR _coderoots_iterator;
public:
  ShenandoahCodeCacheRoots();
  ~ShenandoahCodeCacheRoots();

  void code_blobs_do(CodeBlobClosure* blob_cl, uint worker_id);
};

class ShenandoahClassLoaderDataRoots {
public:
  ShenandoahClassLoaderDataRoots();

  void clds_do(CLDClosure* strong_clds, CLDClosure* weak_clds, uint worker_id);
};

class ShenandoahRootProcessor : public StackObj {
private:
  ShenandoahHeap* const               _heap;
  const ShenandoahPhaseTimings::Phase _phase;
public:
  ShenandoahRootProcessor(ShenandoahPhaseTimings::Phase phase);
  ~ShenandoahRootProcessor();

  ShenandoahHeap* heap() const { return _heap; }
};

template <typename ITR>
class ShenandoahRootScanner : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots          _serial_roots;
  ShenandoahJNIHandleRoots       _jni_roots;
  ShenandoahClassLoaderDataRoots _cld_roots;
  ShenandoahThreadRoots          _thread_roots;
  ShenandoahCodeCacheRoots<ITR>  _code_roots;
public:
  ShenandoahRootScanner(uint n_workers, ShenandoahPhaseTimings::Phase phase);

  // Apply oops, clds and blobs to all strongly reachable roots in the system,
  // during class unloading cycle
  void strong_roots_do(uint worker_id, OopClosure* cl);
  void strong_roots_do(uint worker_id, OopClosure* oops, CLDClosure* clds, CodeBlobClosure* code, ThreadClosure* tc = NULL);

  // Apply oops, clds and blobs to all strongly reachable roots and weakly reachable
  // roots when class unloading is disabled during this cycle
  void roots_do(uint worker_id, OopClosure* cl);
  void roots_do(uint worker_id, OopClosure* oops, CLDClosure* clds, CodeBlobClosure* code, ThreadClosure* tc = NULL);
  // For heap object iteration
  void roots_do_unchecked(OopClosure* cl);
  void strong_roots_do_unchecked(OopClosure* cl);
};

typedef ShenandoahRootScanner<ShenandoahAllCodeRootsIterator> ShenandoahAllRootScanner;
typedef ShenandoahRootScanner<ShenandoahCsetCodeRootsIterator> ShenandoahCSetRootScanner;

// Evacuate all roots at a safepoint
class ShenandoahRootEvacuator : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots          _serial_roots;
  ShenandoahJNIHandleRoots       _jni_roots;
  ShenandoahClassLoaderDataRoots _cld_roots;
  ShenandoahThreadRoots          _thread_roots;
  ShenandoahWeakRoots            _weak_roots;
  ShenandoahStringDedupRoots     _dedup_roots;
  ShenandoahCodeCacheRoots<ShenandoahCsetCodeRootsIterator> _code_roots;

public:
  ShenandoahRootEvacuator(uint n_workers, ShenandoahPhaseTimings::Phase phase);

  void roots_do(uint worker_id, OopClosure* oops);
};

// Update all roots at a safepoint
class ShenandoahRootUpdater : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots          _serial_roots;
  ShenandoahJNIHandleRoots       _jni_roots;
  ShenandoahClassLoaderDataRoots _cld_roots;
  ShenandoahThreadRoots          _thread_roots;
  ShenandoahWeakRoots            _weak_roots;
  ShenandoahStringDedupRoots     _dedup_roots;
  ShenandoahCodeCacheRoots<ShenandoahCsetCodeRootsIterator> _code_roots;
  const bool                     _update_code_cache;

public:
  ShenandoahRootUpdater(uint n_workers, ShenandoahPhaseTimings::Phase phase, bool update_code_cache);

  template<typename IsAlive, typename KeepAlive>
  void roots_do(uint worker_id, IsAlive* is_alive, KeepAlive* keep_alive);
};

// Adjuster all roots at a safepoint during full gc
class ShenandoahRootAdjuster : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots          _serial_roots;
  ShenandoahJNIHandleRoots       _jni_roots;
  ShenandoahClassLoaderDataRoots _cld_roots;
  ShenandoahThreadRoots          _thread_roots;
  ShenandoahWeakRoots            _weak_roots;
  ShenandoahStringDedupRoots     _dedup_roots;
  ShenandoahCodeCacheRoots<ShenandoahAllCodeRootsIterator> _code_roots;

public:
  ShenandoahRootAdjuster(uint n_workers, ShenandoahPhaseTimings::Phase phase);

  void roots_do(uint worker_id, OopClosure* oops);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP
