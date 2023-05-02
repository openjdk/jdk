/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_RUNTIME_LOCKSTACK_HPP
#define SHARE_RUNTIME_LOCKSTACK_HPP

#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/sizes.hpp"

class Thread;
class OopClosure;

class LockStack {
  friend class VMStructs;
private:
  static const int CAPACITY = 8;

  // TODO: It would be very useful if JavaThread::lock_stack_offset() and friends were constexpr,
  // but this is currently not the case because we're using offset_of() which is non-constexpr,
  // GCC would warn about non-standard-layout types if we were using offsetof() (which *is* constexpr).
  static const int lock_stack_offset;
  static const int lock_stack_top_offset;
  static const int lock_stack_base_offset;

  // The offset of the next element, in bytes, relative to the JavaThread structure.
  // We do this instead of a simple index into the array because this allows for
  // efficient addressing in generated code.
  uint32_t _top;
  oop _base[CAPACITY];

  inline JavaThread* get_thread() const;

  bool is_self() const;
  void verify(const char* msg) const PRODUCT_RETURN;

  static inline int to_index(uint32_t offset);

public:
  static ByteSize top_offset()  { return byte_offset_of(LockStack, _top); }
  static ByteSize base_offset() { return byte_offset_of(LockStack, _base); }

  LockStack(JavaThread* jt);

  static uint32_t start_offset();
  static uint32_t end_offset();
  inline bool can_push() const;
  inline void push(oop o);
  inline oop pop();
  inline void remove(oop o);

  inline bool contains(oop o) const;

  // GC support
  inline void oops_do(OopClosure* cl);

};

#endif // SHARE_RUNTIME_LOCKSTACK_HPP
