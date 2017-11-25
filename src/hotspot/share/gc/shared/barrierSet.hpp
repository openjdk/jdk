/*
 * Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_BARRIERSET_HPP
#define SHARE_VM_GC_SHARED_BARRIERSET_HPP

#include "gc/shared/barrierSetConfig.hpp"
#include "memory/memRegion.hpp"
#include "oops/access.hpp"
#include "oops/accessBackend.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/fakeRttiSupport.hpp"

// This class provides the interface between a barrier implementation and
// the rest of the system.

class BarrierSet: public CHeapObj<mtGC> {
  friend class VMStructs;

  static BarrierSet* _bs;

public:
  enum Name {
#define BARRIER_SET_DECLARE_BS_ENUM(bs_name) bs_name ,
    FOR_EACH_BARRIER_SET_DO(BARRIER_SET_DECLARE_BS_ENUM)
#undef BARRIER_SET_DECLARE_BS_ENUM
    UnknownBS
  };

  static BarrierSet* barrier_set() { return _bs; }

protected:
  // Fake RTTI support.  For a derived class T to participate
  // - T must have a corresponding Name entry.
  // - GetName<T> must be specialized to return the corresponding Name
  //   entry.
  // - If T is a base class, the constructor must have a FakeRtti
  //   parameter and pass it up to its base class, with the tag set
  //   augmented with the corresponding Name entry.
  // - If T is a concrete class, the constructor must create a
  //   FakeRtti object whose tag set includes the corresponding Name
  //   entry, and pass it up to its base class.
  typedef FakeRttiSupport<BarrierSet, Name> FakeRtti;

private:
  FakeRtti _fake_rtti;

public:
  // Metafunction mapping a class derived from BarrierSet to the
  // corresponding Name enum tag.
  template<typename T> struct GetName;

  // Metafunction mapping a Name enum type to the corresponding
  // lass derived from BarrierSet.
  template<BarrierSet::Name T> struct GetType;

  // Note: This is not presently the Name corresponding to the
  // concrete class of this object.
  BarrierSet::Name kind() const { return _fake_rtti.concrete_tag(); }

  // Test whether this object is of the type corresponding to bsn.
  bool is_a(BarrierSet::Name bsn) const { return _fake_rtti.has_tag(bsn); }

  // End of fake RTTI support.

protected:
  BarrierSet(const FakeRtti& fake_rtti) : _fake_rtti(fake_rtti) { }
  ~BarrierSet() { }

public:
  // Operations on arrays, or general regions (e.g., for "clone") may be
  // optimized by some barriers.

  // Below length is the # array elements being written
  virtual void write_ref_array_pre(oop* dst, int length,
                                   bool dest_uninitialized = false) {}
  virtual void write_ref_array_pre(narrowOop* dst, int length,
                                   bool dest_uninitialized = false) {}
  // Below count is the # array elements being written, starting
  // at the address "start", which may not necessarily be HeapWord-aligned
  inline void write_ref_array(HeapWord* start, size_t count);

  // Static versions, suitable for calling from generated code;
  // count is # array elements being written, starting with "start",
  // which may not necessarily be HeapWord-aligned.
  static void static_write_ref_array_pre(HeapWord* start, size_t count);
  static void static_write_ref_array_post(HeapWord* start, size_t count);

protected:
  virtual void write_ref_array_work(MemRegion mr) = 0;

public:
  // (For efficiency reasons, this operation is specialized for certain
  // barrier types.  Semantically, it should be thought of as a call to the
  // virtual "_work" function below, which must implement the barrier.)
  void write_region(MemRegion mr);

protected:
  virtual void write_region_work(MemRegion mr) = 0;

public:
  // Inform the BarrierSet that the the covered heap region that starts
  // with "base" has been changed to have the given size (possibly from 0,
  // for initialization.)
  virtual void resize_covered_region(MemRegion new_region) = 0;

  // If the barrier set imposes any alignment restrictions on boundaries
  // within the heap, this function tells whether they are met.
  virtual bool is_aligned(HeapWord* addr) = 0;

  // Print a description of the memory for the barrier set
  virtual void print_on(outputStream* st) const = 0;

  static void set_bs(BarrierSet* bs) { _bs = bs; }

  // The AccessBarrier of a BarrierSet subclass is called by the Access API
  // (cf. oops/access.hpp) to perform decorated accesses. GC implementations
  // may override these default access operations by declaring an
  // AccessBarrier class in its BarrierSet. Its accessors will then be
  // automatically resolved at runtime.
  //
  // In order to register a new FooBarrierSet::AccessBarrier with the Access API,
  // the following steps should be taken:
  // 1) Provide an enum "name" for the BarrierSet in barrierSetConfig.hpp
  // 2) Make sure the barrier set headers are included from barrierSetConfig.inline.hpp
  // 3) Provide specializations for BarrierSet::GetName and BarrierSet::GetType.
  template <DecoratorSet decorators, typename BarrierSetT>
  class AccessBarrier: protected RawAccessBarrier<decorators> {
  protected:
    typedef RawAccessBarrier<decorators> Raw;
    typedef typename BarrierSetT::template AccessBarrier<decorators> CRTPAccessBarrier;

  public:
    // Primitive heap accesses. These accessors get resolved when
    // IN_HEAP is set (e.g. when using the HeapAccess API), it is
    // not an oop_* overload, and the barrier strength is AS_NORMAL.
    template <typename T>
    static T load_in_heap(T* addr) {
      return Raw::template load<T>(addr);
    }

    template <typename T>
    static T load_in_heap_at(oop base, ptrdiff_t offset) {
      return Raw::template load_at<T>(base, offset);
    }

    template <typename T>
    static void store_in_heap(T* addr, T value) {
      Raw::store(addr, value);
    }

    template <typename T>
    static void store_in_heap_at(oop base, ptrdiff_t offset, T value) {
      Raw::store_at(base, offset, value);
    }

    template <typename T>
    static T atomic_cmpxchg_in_heap(T new_value, T* addr, T compare_value) {
      return Raw::atomic_cmpxchg(new_value, addr, compare_value);
    }

    template <typename T>
    static T atomic_cmpxchg_in_heap_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
      return Raw::oop_atomic_cmpxchg_at(new_value, base, offset, compare_value);
    }

    template <typename T>
    static T atomic_xchg_in_heap(T new_value, T* addr) {
      return Raw::atomic_xchg(new_value, addr);
    }

    template <typename T>
    static T atomic_xchg_in_heap_at(T new_value, oop base, ptrdiff_t offset) {
      return Raw::atomic_xchg_at(new_value, base, offset);
    }

    template <typename T>
    static bool arraycopy_in_heap(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length) {
      return Raw::arraycopy(src_obj, dst_obj, src, dst, length);
    }

    // Heap oop accesses. These accessors get resolved when
    // IN_HEAP is set (e.g. when using the HeapAccess API), it is
    // an oop_* overload, and the barrier strength is AS_NORMAL.
    template <typename T>
    static oop oop_load_in_heap(T* addr) {
      return Raw::template oop_load<oop>(addr);
    }

    static oop oop_load_in_heap_at(oop base, ptrdiff_t offset) {
      return Raw::template oop_load_at<oop>(base, offset);
    }

    template <typename T>
    static void oop_store_in_heap(T* addr, oop value) {
      Raw::oop_store(addr, value);
    }

    static void oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
      Raw::oop_store_at(base, offset, value);
    }

    template <typename T>
    static oop oop_atomic_cmpxchg_in_heap(oop new_value, T* addr, oop compare_value) {
      return Raw::oop_atomic_cmpxchg(new_value, addr, compare_value);
    }

    static oop oop_atomic_cmpxchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset, oop compare_value) {
      return Raw::oop_atomic_cmpxchg_at(new_value, base, offset, compare_value);
    }

    template <typename T>
    static oop oop_atomic_xchg_in_heap(oop new_value, T* addr) {
      return Raw::oop_atomic_xchg(new_value, addr);
    }

    static oop oop_atomic_xchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset) {
      return Raw::oop_atomic_xchg_at(new_value, base, offset);
    }

    template <typename T>
    static bool oop_arraycopy_in_heap(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length) {
      return Raw::oop_arraycopy(src_obj, dst_obj, src, dst, length);
    }

    // Off-heap oop accesses. These accessors get resolved when
    // IN_HEAP is not set (e.g. when using the RootAccess API), it is
    // an oop* overload, and the barrier strength is AS_NORMAL.
    template <typename T>
    static oop oop_load_not_in_heap(T* addr) {
      return Raw::template oop_load<oop>(addr);
    }

    template <typename T>
    static void oop_store_not_in_heap(T* addr, oop value) {
      Raw::oop_store(addr, value);
    }

    template <typename T>
    static oop oop_atomic_cmpxchg_not_in_heap(oop new_value, T* addr, oop compare_value) {
      return Raw::oop_atomic_cmpxchg(new_value, addr, compare_value);
    }

    template <typename T>
    static oop oop_atomic_xchg_not_in_heap(oop new_value, T* addr) {
      return Raw::oop_atomic_xchg(new_value, addr);
    }

    // Clone barrier support
    static void clone_in_heap(oop src, oop dst, size_t size) {
      Raw::clone(src, dst, size);
    }
  };
};

template<typename T>
inline T* barrier_set_cast(BarrierSet* bs) {
  assert(bs->is_a(BarrierSet::GetName<T>::value), "wrong type of barrier set");
  return static_cast<T*>(bs);
}

#endif // SHARE_VM_GC_SHARED_BARRIERSET_HPP
