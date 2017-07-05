/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/nativeCallStack.hpp"

const NativeCallStack NativeCallStack::EMPTY_STACK(0, false);

NativeCallStack::NativeCallStack(int toSkip, bool fillStack) :
  _hash_value(0) {

#if !PLATFORM_NATIVE_STACK_WALKING_SUPPORTED
  fillStack = false;
#endif

  if (fillStack) {
    os::get_native_stack(_stack, NMT_TrackingStackDepth, toSkip);
  } else {
    for (int index = 0; index < NMT_TrackingStackDepth; index ++) {
      _stack[index] = NULL;
    }
  }
}

NativeCallStack::NativeCallStack(address* pc, int frameCount) {
  int frameToCopy = (frameCount < NMT_TrackingStackDepth) ?
    frameCount : NMT_TrackingStackDepth;
  int index;
  for (index = 0; index < frameToCopy; index ++) {
    _stack[index] = pc[index];
  }
  for (; index < NMT_TrackingStackDepth; index ++) {
    _stack[index] = NULL;
  }
  _hash_value = 0;
}

// number of stack frames captured
int NativeCallStack::frames() const {
  int index;
  for (index = 0; index < NMT_TrackingStackDepth; index ++) {
    if (_stack[index] == NULL) {
      break;
    }
  }
  return index;
}

// Hash code. Any better algorithm?
unsigned int NativeCallStack::hash() const {
  uintptr_t hash_val = _hash_value;
  if (hash_val == 0) {
    for (int index = 0; index < NMT_TrackingStackDepth; index++) {
      if (_stack[index] == NULL) break;
      hash_val += (uintptr_t)_stack[index];
    }

    NativeCallStack* p = const_cast<NativeCallStack*>(this);
    p->_hash_value = (unsigned int)(hash_val & 0xFFFFFFFF);
  }
  return _hash_value;
}

void NativeCallStack::print_on(outputStream* out) const {
  print_on(out, 0);
}

// Decode and print this call path
void NativeCallStack::print_on(outputStream* out, int indent) const {
  address pc;
  char    buf[1024];
  int     offset;
  if (is_empty()) {
    for (int index = 0; index < indent; index ++) out->print(" ");
#if PLATFORM_NATIVE_STACK_WALKING_SUPPORTED
    out->print("[BOOTSTRAP]");
#else
    out->print("[No stack]");
#endif
  } else {
    for (int frame = 0; frame < NMT_TrackingStackDepth; frame ++) {
      pc = get_frame(frame);
      if (pc == NULL) break;
      // Print indent
      for (int index = 0; index < indent; index ++) out->print(" ");
      if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
        out->print_cr("[" PTR_FORMAT "] %s+0x%x", p2i(pc), buf, offset);
      } else {
        out->print_cr("[" PTR_FORMAT "]", p2i(pc));
      }
    }
  }
}

