/*
 * Copyright (c) 2015, 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP

#include "code/codeCache.hpp"
#include "gc/shared/oopStorageParState.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "memory/iterator.hpp"

class ShenandoahSerialRoot {
public:
  typedef void (*OopsDo)(OopClosure*);
private:
  ShenandoahSharedFlag                      _claimed;
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

class ShenandoahWeakSerialRoot {
  typedef void (*WeakOopsDo)(BoolObjectClosure*, OopClosure*);
private:
  ShenandoahSharedFlag                      _claimed;
  const WeakOopsDo                          _weak_oops_do;
  const ShenandoahPhaseTimings::GCParPhases _phase;

public:
  ShenandoahWeakSerialRoot(WeakOopsDo oops_do, ShenandoahPhaseTimings::GCParPhases);
  void weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id);
};

#if INCLUDE_JVMTI
class ShenandoahJVMTIWeakRoot : public ShenandoahWeakSerialRoot {
public:
  ShenandoahJVMTIWeakRoot();
};
#endif // INCLUDE_JVMTI

#if INCLUDE_JFR
class ShenandoahJFRWeakRoot : public ShenandoahWeakSerialRoot {
public:
  ShenandoahJFRWeakRoot();
};
#endif // INCLUDE_JFR

class ShenandoahSerialWeakRoots {
private:
  JVMTI_ONLY(ShenandoahJVMTIWeakRoot _jvmti_weak_roots;)
  JFR_ONLY(ShenandoahJFRWeakRoot     _jfr_weak_roots;)
public:
  void weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id);
  void weak_oops_do(OopClosure* cl, uint worker_id);
};

template <bool CONCURRENT>
class ShenandoahVMRoot {
private:
  OopStorage::ParState<CONCURRENT, false /* is_const */> _itr;
  const ShenandoahPhaseTimings::GCParPhases _phase;
public:
  ShenandoahVMRoot(OopStorage* storage, ShenandoahPhaseTimings::GCParPhases phase);

  template <typename Closure>
  void oops_do(Closure* cl, uint worker_id);
};

template <bool CONCURRENT>
class ShenandoahWeakRoot : public ShenandoahVMRoot<CONCURRENT> {
public:
  ShenandoahWeakRoot(OopStorage* storage, ShenandoahPhaseTimings::GCParPhases phase);
};

template <>
class ShenandoahWeakRoot<false /*concurrent*/> {
private:
  OopStorage::ParState<false /*concurrent*/, false /*is_const*/> _itr;
  const ShenandoahPhaseTimings::GCParPhases _phase;

public:
  ShenandoahWeakRoot(OopStorage* storage, ShenandoahPhaseTimings::GCParPhases phase);

  template <typename IsAliveClosure, typename KeepAliveClosure>
  void weak_oops_do(IsAliveClosure* is_alive, KeepAliveClosure* keep_alive, uint worker_id);
};

template <bool CONCURRENT>
class ShenandoahWeakRoots {
private:
  ShenandoahWeakRoot<CONCURRENT>  _jni_roots;
  ShenandoahWeakRoot<CONCURRENT>  _string_table_roots;
  ShenandoahWeakRoot<CONCURRENT>  _resolved_method_table_roots;
  ShenandoahWeakRoot<CONCURRENT>  _vm_roots;

public:
  ShenandoahWeakRoots();

  template <typename Closure>
  void oops_do(Closure* cl, uint worker_id = 0);
};

template <>
class ShenandoahWeakRoots<false /*concurrent */> {
private:
  ShenandoahWeakRoot<false /*concurrent*/>  _jni_roots;
  ShenandoahWeakRoot<false /*concurrent*/>  _string_table_roots;
  ShenandoahWeakRoot<false /*concurrent*/>  _resolved_method_table_roots;
  ShenandoahWeakRoot<false /*concurrent*/>  _vm_roots;
public:
  ShenandoahWeakRoots();

  template <typename Closure>
  void oops_do(Closure* cl, uint worker_id = 0);

  template <typename IsAliveClosure, typename KeepAliveClosure>
  void weak_oops_do(IsAliveClosure* is_alive, KeepAliveClosure* keep_alive, uint worker_id);
};

template <bool CONCURRENT>
class ShenandoahVMRoots {
private:
  ShenandoahVMRoot<CONCURRENT>    _jni_handle_roots;
  ShenandoahVMRoot<CONCURRENT>    _vm_global_roots;

public:
  ShenandoahVMRoots();

  template <typename T>
  void oops_do(T* cl, uint worker_id = 0);
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

class ShenandoahStringDedupRoots {
public:
  ShenandoahStringDedupRoots();
  ~ShenandoahStringDedupRoots();

  void oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id);
};

class ShenandoahConcurrentStringDedupRoots {
public:
  ShenandoahConcurrentStringDedupRoots();
  ~ShenandoahConcurrentStringDedupRoots();

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

template <bool CONCURRENT, bool SINGLE_THREADED>
class ShenandoahClassLoaderDataRoots {
public:
  ShenandoahClassLoaderDataRoots();
  ~ShenandoahClassLoaderDataRoots();

  void always_strong_cld_do(CLDClosure* clds, uint worker_id = 0);
  void cld_do(CLDClosure* clds, uint worker_id = 0);
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
  ShenandoahSerialRoots                                     _serial_roots;
  ShenandoahThreadRoots                                     _thread_roots;
  ShenandoahCodeCacheRoots<ITR>                             _code_roots;
  ShenandoahVMRoots<false /*concurrent*/ >                  _vm_roots;
  ShenandoahStringDedupRoots                                _dedup_roots;
  ShenandoahClassLoaderDataRoots<false /*concurrent*/, false /*single threaded*/>
                                                            _cld_roots;
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
};

typedef ShenandoahRootScanner<ShenandoahAllCodeRootsIterator> ShenandoahAllRootScanner;
typedef ShenandoahRootScanner<ShenandoahCsetCodeRootsIterator> ShenandoahCSetRootScanner;

// This scanner is only for SH::object_iteration() and only supports single-threaded
// root scanning
class ShenandoahHeapIterationRootScanner : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots                                    _serial_roots;
  ShenandoahThreadRoots                                    _thread_roots;
  ShenandoahVMRoots<false /*concurrent*/>                  _vm_roots;
  ShenandoahClassLoaderDataRoots<false /*concurrent*/, true /*single threaded*/>
                                                           _cld_roots;
  ShenandoahSerialWeakRoots                                _serial_weak_roots;
  ShenandoahWeakRoots<false /*concurrent*/>                _weak_roots;
  ShenandoahConcurrentStringDedupRoots                     _dedup_roots;
  ShenandoahCodeCacheRoots<ShenandoahAllCodeRootsIterator> _code_roots;

public:
  ShenandoahHeapIterationRootScanner();

  void roots_do(OopClosure* cl);
  void strong_roots_do(OopClosure* cl);
};

// Evacuate all roots at a safepoint
class ShenandoahRootEvacuator : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots                                     _serial_roots;
  ShenandoahVMRoots<false /*concurrent*/>                   _vm_roots;
  ShenandoahClassLoaderDataRoots<false /*concurrent*/, false /*single threaded*/>
                                                            _cld_roots;
  ShenandoahThreadRoots                                     _thread_roots;
  ShenandoahSerialWeakRoots                                 _serial_weak_roots;
  ShenandoahWeakRoots<false /*concurrent*/>                 _weak_roots;
  ShenandoahStringDedupRoots                                _dedup_roots;
  ShenandoahCodeCacheRoots<ShenandoahAllCodeRootsIterator>  _code_roots;
  bool                                                      _include_concurrent_roots;
  bool                                                      _include_concurrent_code_roots;
public:
  ShenandoahRootEvacuator(uint n_workers, ShenandoahPhaseTimings::Phase phase,
                          bool include_concurrent_roots, bool _include_concurrent_code_roots);

  void roots_do(uint worker_id, OopClosure* oops);
};

// Update all roots at a safepoint
class ShenandoahRootUpdater : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots                                     _serial_roots;
  ShenandoahVMRoots<false /*concurrent*/>                   _vm_roots;
  ShenandoahClassLoaderDataRoots<false /*concurrent*/, false /*single threaded*/>
                                                            _cld_roots;
  ShenandoahThreadRoots                                     _thread_roots;
  ShenandoahSerialWeakRoots                                 _serial_weak_roots;
  ShenandoahWeakRoots<false /*concurrent*/>                 _weak_roots;
  ShenandoahStringDedupRoots                                _dedup_roots;
  ShenandoahCodeCacheRoots<ShenandoahAllCodeRootsIterator>  _code_roots;

public:
  ShenandoahRootUpdater(uint n_workers, ShenandoahPhaseTimings::Phase phase);

  template<typename IsAlive, typename KeepAlive>
  void roots_do(uint worker_id, IsAlive* is_alive, KeepAlive* keep_alive);
};

// Adjuster all roots at a safepoint during full gc
class ShenandoahRootAdjuster : public ShenandoahRootProcessor {
private:
  ShenandoahSerialRoots                                     _serial_roots;
  ShenandoahVMRoots<false /*concurrent*/>                   _vm_roots;
  ShenandoahClassLoaderDataRoots<false /*concurrent*/, false /*single threaded*/>
                                                            _cld_roots;
  ShenandoahThreadRoots                                     _thread_roots;
  ShenandoahSerialWeakRoots                                 _serial_weak_roots;
  ShenandoahWeakRoots<false /*concurrent*/>                 _weak_roots;
  ShenandoahStringDedupRoots                                _dedup_roots;
  ShenandoahCodeCacheRoots<ShenandoahAllCodeRootsIterator>  _code_roots;

public:
  ShenandoahRootAdjuster(uint n_workers, ShenandoahPhaseTimings::Phase phase);

  void roots_do(uint worker_id, OopClosure* oops);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_HPP
