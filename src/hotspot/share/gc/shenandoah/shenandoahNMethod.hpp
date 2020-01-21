/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHNMETHOD_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHNMETHOD_HPP

#include "code/nmethod.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

// ShenandoahNMethod tuple records the internal locations of oop slots within reclocation stream in
// the nmethod. This allows us to quickly scan the oops without doing the nmethod-internal scans,
// that sometimes involves parsing the machine code. Note it does not record the oops themselves,
// because it would then require handling these tuples as the new class of roots.
class ShenandoahNMethod : public CHeapObj<mtGC> {
private:
  nmethod* const          _nm;
  oop**                   _oops;
  int                     _oops_count;
  bool                    _has_non_immed_oops;
  bool                    _unregistered;
  ShenandoahReentrantLock _lock;

public:
  ShenandoahNMethod(nmethod *nm, GrowableArray<oop*>& oops, bool has_non_immed_oops);
  ~ShenandoahNMethod();

  inline nmethod* nm() const;
  inline ShenandoahReentrantLock* lock();
  void oops_do(OopClosure* oops, bool fix_relocations = false);
  // Update oops when the nmethod is re-registered
  void update();

  bool has_cset_oops(ShenandoahHeap* heap);

  inline int oop_count() const;
  inline bool has_oops() const;

  inline void mark_unregistered();
  inline bool is_unregistered() const;

  static ShenandoahNMethod* for_nmethod(nmethod* nm);
  static inline ShenandoahReentrantLock* lock_for_nmethod(nmethod* nm);

  static void heal_nmethod(nmethod* nm);
  static inline void disarm_nmethod(nmethod* nm);

  static inline ShenandoahNMethod* gc_data(nmethod* nm);
  static inline void attach_gc_data(nmethod* nm, ShenandoahNMethod* gc_data);

  void assert_alive_and_correct() NOT_DEBUG_RETURN;
  void assert_same_oops(bool allow_dead = false) NOT_DEBUG_RETURN;
  static void assert_no_oops(nmethod* nm, bool allow_dea = false) NOT_DEBUG_RETURN;

private:
  bool has_non_immed_oops() const { return _has_non_immed_oops; }
  static void detect_reloc_oops(nmethod* nm, GrowableArray<oop*>& oops, bool& _has_non_immed_oops);
};

class ShenandoahNMethodTable;

// An opaque snapshot of current nmethod table for iteration
class ShenandoahNMethodTableSnapshot : public CHeapObj<mtGC> {
  friend class ShenandoahNMethodTable;
private:
  ShenandoahHeap* const       _heap;
  ShenandoahNMethodTable*     _table;
  ShenandoahNMethod** const   _array;
  const int                   _length;

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile size_t));
  volatile size_t       _claimed;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

public:
  ShenandoahNMethodTableSnapshot(ShenandoahNMethodTable* table);

  template<bool CSET_FILTER>
  void parallel_blobs_do(CodeBlobClosure *f);

  void concurrent_nmethods_do(NMethodClosure* cl);
};

class ShenandoahNMethodTable : public CHeapObj<mtGC> {
  friend class ShenandoahNMethodTableSnapshot;
private:
  enum {
    minSize = 1024
  };

  ShenandoahHeap* const _heap;
  ShenandoahNMethod**   _array;
  int                   _size;
  int                   _index;
  ShenandoahLock        _lock;
  bool                  _iteration_in_progress;

public:
  ShenandoahNMethodTable();
  ~ShenandoahNMethodTable();

  void register_nmethod(nmethod* nm);
  void unregister_nmethod(nmethod* nm);
  void flush_nmethod(nmethod* nm);

  bool contain(nmethod* nm) const;
  int length() const { return _index; }

  // Table iteration support
  ShenandoahNMethodTableSnapshot* snapshot_for_iteration();
  void finish_iteration(ShenandoahNMethodTableSnapshot* snapshot);

  void assert_nmethods_alive_and_correct() NOT_DEBUG_RETURN;
private:
  // Rebuild table and replace current one
  void rebuild(int size);

  bool is_full() const {
    assert(_index <= _size, "Sanity");
    return _index == _size;
  }

  ShenandoahNMethod* at(int index) const;
  int  index_of(nmethod* nm) const;
  void remove(int index);
  void append(ShenandoahNMethod* snm);

  inline bool iteration_in_progress() const;
  void wait_until_concurrent_iteration_done();

  // Logging support
  void log_register_nmethod(nmethod* nm);
  void log_unregister_nmethod(nmethod* nm);
  void log_flush_nmethod(nmethod* nm);
};

class ShenandoahConcurrentNMethodIterator {
private:
  ShenandoahNMethodTable*         const _table;
  ShenandoahNMethodTableSnapshot*       _table_snapshot;

public:
  ShenandoahConcurrentNMethodIterator(ShenandoahNMethodTable* table);

  void nmethods_do_begin();
  void nmethods_do(NMethodClosure* cl);
  void nmethods_do_end();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHNMETHOD_HPP
