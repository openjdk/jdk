/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

class JavaThread;
class OopClosure;
class outputStream;

class LockStack {
  friend class LockStackTest;
  friend class VMStructs;
  JVMCI_ONLY(friend class JVMCIVMStructs;)
public:
  static const int CAPACITY = 8;
private:

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
  // The _bad_oop_sentinel acts as a sentinel value to elide underflow checks in generated code.
  // The correct layout is statically asserted in the constructor.
  const uintptr_t _bad_oop_sentinel = badOopVal;
  oop _base[CAPACITY];

  // Get the owning thread of this lock-stack.
  inline JavaThread* get_thread() const;

  // Tests if the calling thread is the thread that owns this lock-stack.
  bool is_owning_thread() const;

  // Verifies consistency of the lock-stack.
  void verify(const char* msg) const PRODUCT_RETURN;

  // Given an offset (in bytes) calculate the index into the lock-stack.
  static inline int to_index(uint32_t offset);

public:
  static ByteSize top_offset()  { return byte_offset_of(LockStack, _top); }
  static ByteSize base_offset() { return byte_offset_of(LockStack, _base); }

  LockStack(JavaThread* jt);

  // The boundary indicies of the lock-stack.
  static uint32_t start_offset();
  static uint32_t end_offset();

  // Returns true if the lock-stack is full. False otherwise.
  inline bool is_full() const;

  // Pushes an oop on this lock-stack.
  inline void push(oop o);

  // Get the oldest oop from this lock-stack.
  // Precondition: This lock-stack must not be empty.
  inline oop bottom() const;

  // Is the lock-stack empty.
  inline bool is_empty() const;

  // Check if object is recursive.
  // Precondition: This lock-stack must contain the oop.
  inline bool is_recursive(oop o) const;

  // Try recursive enter.
  // Precondition: This lock-stack must not be full.
  inline bool try_recursive_enter(oop o);

  // Try recursive exit.
  // Precondition: This lock-stack must contain the oop.
  inline bool try_recursive_exit(oop o);

  // Removes an oop from an arbitrary location of this lock-stack.
  // Precondition: This lock-stack must contain the oop.
  // Returns the number of oops removed.
  inline size_t remove(oop o);

  // Tests whether the oop is on this lock-stack.
  inline bool contains(oop o) const;

  // GC support
  inline void oops_do(OopClosure* cl);

  // Printing
  void print_on(outputStream* st);
};

#endif // SHARE_RUNTIME_LOCKSTACK_HPP
