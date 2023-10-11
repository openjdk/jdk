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

class JavaThread;
class OopClosure;
class outputStream;

class LockStack {
  friend class VMStructs;
  JVMCI_ONLY(friend class JVMCIVMStructs;)
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

  // Return true if we have room to push onto this lock-stack, false otherwise.
  inline bool can_push() const;

  // Pushes an oop on this lock-stack.
  inline void push(oop o);

  // Pops an oop from this lock-stack.
  inline oop pop();

  // Removes an oop from an arbitrary location of this lock-stack.
  inline void remove(oop o);

  // Tests whether the oop is on this lock-stack.
  inline bool contains(oop o) const;

  // GC support
  inline void oops_do(OopClosure* cl);

  // Printing
  void print_on(outputStream* st);
};

#endif // SHARE_RUNTIME_LOCKSTACK_HPP
