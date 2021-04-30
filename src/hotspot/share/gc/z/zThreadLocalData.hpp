/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZTHREADLOCALDATA_HPP
#define SHARE_GC_Z_ZTHREADLOCALDATA_HPP

#include "gc/z/zMarkStack.hpp"
#include "gc/z/zCycleId.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zStoreBarrierBuffer.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/sizes.hpp"

class ZThreadLocalData {
private:
  uintptr_t              _address_load_good_mask;
  uintptr_t              _address_load_bad_mask;
  uintptr_t              _address_mark_bad_mask;
  uintptr_t              _address_store_good_mask;
  uintptr_t              _address_store_bad_mask;
  uintptr_t              _address_uncolor_mask;
  ZStoreBarrierBuffer*   _store_barrier_buffer;
  ZMarkThreadLocalStacks _mark_stacks[2];
  oop*                   _invisible_root;
  size_t                 _invisible_root_initialized;

  ZThreadLocalData() :
      _address_load_good_mask(0),
      _address_load_bad_mask(0),
      _address_mark_bad_mask(0),
      _address_store_good_mask(0),
      _address_store_bad_mask(0),
      _address_uncolor_mask(0),
      _store_barrier_buffer(new ZStoreBarrierBuffer()),
      _mark_stacks(),
      _invisible_root(NULL),
      _invisible_root_initialized(0) {}

  ~ZThreadLocalData() {
    delete _store_barrier_buffer;
  }

  static ZThreadLocalData* data(Thread* thread) {
    return thread->gc_data<ZThreadLocalData>();
  }

public:
  static void create(Thread* thread) {
    new (data(thread)) ZThreadLocalData();
  }

  static void destroy(Thread* thread) {
    data(thread)->~ZThreadLocalData();
  }

  static void set_address_load_bad_mask(Thread* thread, uintptr_t mask) {
    data(thread)->_address_load_bad_mask = mask;
  }

  static void set_address_mark_bad_mask(Thread* thread, uintptr_t mask) {
    data(thread)->_address_mark_bad_mask = mask;
  }

  static void set_address_store_bad_mask(Thread* thread, uintptr_t mask) {
    data(thread)->_address_store_bad_mask = mask;
  }

  static void set_address_load_good_mask(Thread* thread, uintptr_t mask) {
    data(thread)->_address_load_good_mask = mask;
  }

  static void set_address_store_good_mask(Thread* thread, uintptr_t mask) {
    data(thread)->_address_store_good_mask = mask;
  }

  static void set_address_uncolor_mask(Thread* thread, uintptr_t mask) {
    data(thread)->_address_uncolor_mask = mask;
  }

  static ZMarkThreadLocalStacks* mark_stacks(Thread* thread, ZCycleId cycle) {
    return &data(thread)->_mark_stacks[(int)cycle];
  }

  static ZStoreBarrierBuffer* store_barrier_buffer(Thread* thread) {
    return data(thread)->_store_barrier_buffer;
  }

  static void set_invisible_root(Thread* thread, oop* root, size_t initialized) {
    assert(data(thread)->_invisible_root == NULL, "Already set");
    data(thread)->_invisible_root = root;
    data(thread)->_invisible_root_initialized = initialized;
  }

  static void clear_invisible_root(Thread* thread) {
    assert(data(thread)->_invisible_root != NULL, "Should be set");
    data(thread)->_invisible_root = NULL;
    data(thread)->_invisible_root_initialized = 0;
  }

  static oop* invisible_root(Thread* thread) {
    return data(thread)->_invisible_root;
  }

  static size_t invisible_root_initialized(Thread* thread) {
    return data(thread)->_invisible_root_initialized;
  }

  static ByteSize address_load_bad_mask_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ZThreadLocalData, _address_load_bad_mask);
  }

  static ByteSize address_mark_bad_mask_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ZThreadLocalData, _address_mark_bad_mask);
  }

  static ByteSize address_store_bad_mask_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ZThreadLocalData, _address_store_bad_mask);
  }

  static ByteSize address_load_good_mask_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ZThreadLocalData, _address_load_good_mask);
  }

  static ByteSize address_store_good_mask_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ZThreadLocalData, _address_store_good_mask);
  }

  static ByteSize address_uncolor_mask_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ZThreadLocalData, _address_uncolor_mask);
  }

  static ByteSize nmethod_disarmed_offset() {
    return address_store_good_mask_offset() + in_ByteSize(ZAddressStoreGoodMaskLowOrderBitsOffset);
  }

  static ByteSize store_barrier_buffer_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ZThreadLocalData, _store_barrier_buffer);
  }
};

#endif // SHARE_GC_Z_ZTHREADLOCALDATA_HPP
