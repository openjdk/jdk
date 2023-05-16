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

#ifndef SHARE_GC_X_XTHREADLOCALDATA_HPP
#define SHARE_GC_X_XTHREADLOCALDATA_HPP

#include "gc/x/xMarkStack.hpp"
#include "gc/x/xGlobals.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/sizes.hpp"

class XThreadLocalData {
private:
  uintptr_t              _address_bad_mask;
  XMarkThreadLocalStacks _stacks;
  oop*                   _invisible_root;

  XThreadLocalData() :
      _address_bad_mask(0),
      _stacks(),
      _invisible_root(NULL) {}

  static XThreadLocalData* data(Thread* thread) {
    return thread->gc_data<XThreadLocalData>();
  }

public:
  static void create(Thread* thread) {
    new (data(thread)) XThreadLocalData();
  }

  static void destroy(Thread* thread) {
    data(thread)->~XThreadLocalData();
  }

  static void set_address_bad_mask(Thread* thread, uintptr_t mask) {
    data(thread)->_address_bad_mask = mask;
  }

  static XMarkThreadLocalStacks* stacks(Thread* thread) {
    return &data(thread)->_stacks;
  }

  static void set_invisible_root(Thread* thread, oop* root) {
    assert(data(thread)->_invisible_root == NULL, "Already set");
    data(thread)->_invisible_root = root;
  }

  static void clear_invisible_root(Thread* thread) {
    assert(data(thread)->_invisible_root != NULL, "Should be set");
    data(thread)->_invisible_root = NULL;
  }

  template <typename T>
  static void do_invisible_root(Thread* thread, T f) {
    if (data(thread)->_invisible_root != NULL) {
      f(data(thread)->_invisible_root);
    }
  }

  static ByteSize address_bad_mask_offset() {
    return Thread::gc_data_offset() + byte_offset_of(XThreadLocalData, _address_bad_mask);
  }

  static ByteSize nmethod_disarmed_offset() {
    return address_bad_mask_offset() + in_ByteSize(XAddressBadMaskHighOrderBitsOffset);
  }
};

#endif // SHARE_GC_X_XTHREADLOCALDATA_HPP
