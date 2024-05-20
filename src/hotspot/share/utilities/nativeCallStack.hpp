/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_NATIVECALLSTACK_HPP
#define SHARE_UTILITIES_NATIVECALLSTACK_HPP

#include "memory/allocation.hpp"
#include "nmt/nmtCommon.hpp"
#include "utilities/ostream.hpp"

/*
 * This class represents a native call path (does not include Java frame)
 *
 * This class is developed in the context of native memory tracking, it can
 * be an useful tool for debugging purpose.
 *
 * For example, following code should print out native call path:
 *
 *   ....
 *   NativeCallStack here;
 *   here.print_on(tty);
 *   ....
 *
 * However, there are a couple of restrictions on this class. If the restrictions are
 * not strictly followed, it may break native memory tracking badly.
 *
 * 1. Number of stack frames to capture, is defined by native memory tracking.
 *    This number has impacts on how much memory to be used by native
 *    memory tracking.
 * 2. The class is strict stack object, no heap or virtual memory can be allocated
 *    from it.
 */
class MemTracker;

class NativeCallStack : public StackObj {
private:
  address       _stack[NMT_TrackingStackDepth];
  static const NativeCallStack _empty_stack;

public:

  enum class FakeMarker { its_fake };
#ifdef ASSERT
  static constexpr uintptr_t _fake_address = -2; // 0xFF...FE
  inline void assert_not_fake() const {
    assert(_stack[0] != (address)_fake_address, "Must not be a fake stack");
  }
#endif

  // This "fake" constructor is only used in the CALLER_PC and CURRENT_PC macros
  // when NMT is off or in summary mode. In these cases, it does not need a
  // callstack, and we can leave the constructed object uninitialized. That will
  // cause the constructor call to be optimized away (see JDK-8296437).
  explicit NativeCallStack(FakeMarker dummy) {
#ifdef ASSERT
    for (int i = 0; i < NMT_TrackingStackDepth; i++) {
      _stack[i] = (address)_fake_address;
    }
#endif
  }

  // Default ctor creates an empty stack.
  // (it may make sense to remove this altogether but its used in a few places).
  NativeCallStack() {
    memset(_stack, 0, sizeof(_stack));
  }

  explicit NativeCallStack(int toSkip);
  explicit NativeCallStack(address* pc, int frameCount);

  static inline const NativeCallStack& empty_stack() { return _empty_stack; }

  // if it is an empty stack
  inline bool is_empty() const {
    DEBUG_ONLY(assert_not_fake();)
    return _stack[0] == nullptr;
  }

  // number of stack frames captured
  int frames() const;

  inline int compare(const NativeCallStack& other) const {
    return memcmp(_stack, other._stack, sizeof(_stack));
  }

  inline bool equals(const NativeCallStack& other) const {
    return compare(other) == 0;
  }

  inline address get_frame(int index) const {
    assert(index >= 0 && index < NMT_TrackingStackDepth, "Index out of bound");
    return _stack[index];
  }

  // Helper; calculates a hash value over the stack frames in this stack
  unsigned int calculate_hash() const {
    DEBUG_ONLY(assert_not_fake();)
    uintptr_t hash = 0;
    for (int i = 0; i < NMT_TrackingStackDepth; i++) {
      hash += (uintptr_t)_stack[i];
    }
    return (unsigned int)hash;
  }

  void print_on(outputStream* out) const;
  void print_on(outputStream* out, int indent) const;
};

#define FAKE_CALLSTACK NativeCallStack(NativeCallStack::FakeMarker::its_fake)

#endif // SHARE_UTILITIES_NATIVECALLSTACK_HPP
