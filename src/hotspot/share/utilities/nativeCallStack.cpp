/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/decoder.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/nativeCallStack.hpp"

const NativeCallStack NativeCallStack::_empty_stack; // Uses default ctor

NativeCallStack::NativeCallStack(int toSkip) {

  // We need to skip the NativeCallStack::NativeCallStack frame if a tail call is NOT used
  // to call os::get_native_stack. A tail call is used if _NMT_NOINLINE_ is not defined
  // (which means this is not a slowdebug build), and we are on 64-bit (except Windows).
  // This is not necessarily a rule, but what has been obvserved to date.
#if (defined(_NMT_NOINLINE_) || defined(_WINDOWS) || !defined(_LP64) || defined(PPC64) || (defined(BSD) && defined (__aarch64__)))
  // Not a tail call.
  toSkip++;
#if (defined(_NMT_NOINLINE_) && defined(BSD) && defined(_LP64))
  // Mac OS X slowdebug builds have this odd behavior where NativeCallStack::NativeCallStack
  // appears as two frames, so we need to skip an extra frame.
  toSkip++;
#endif // Special-case for BSD.
#endif // Not a tail call.
  os::get_native_stack(_stack, NMT_TrackingStackDepth, toSkip);
}

NativeCallStack::NativeCallStack(address* pc, int frameCount) {
  int frameToCopy = (frameCount < NMT_TrackingStackDepth) ?
    frameCount : NMT_TrackingStackDepth;
  int index;
  for (index = 0; index < frameToCopy; index ++) {
    _stack[index] = pc[index];
  }
  for (; index < NMT_TrackingStackDepth; index ++) {
    _stack[index] = nullptr;
  }
}

// number of stack frames captured
int NativeCallStack::frames() const {
  int index;
  for (index = 0; index < NMT_TrackingStackDepth; index ++) {
    if (_stack[index] == nullptr) {
      break;
    }
  }
  return index;
}

// Decode and print this call path
void NativeCallStack::print_on(outputStream* out) const {
  DEBUG_ONLY(assert_not_fake();)
  address pc;
  char    buf[1024];
  int     offset;
  if (is_empty()) {
    out->print("[BOOTSTRAP]");
  } else {
    for (int frame = 0; frame < NMT_TrackingStackDepth; frame ++) {
      pc = get_frame(frame);
      if (pc == nullptr) break;
      out->print("[" PTR_FORMAT "]", p2i(pc));
      // Print function and library; shorten library name to just its last component
      // for brevity, and omit it completely for libjvm.so
      bool function_printed = false;
      if (os::dll_address_to_function_name(pc, buf, sizeof(buf), &offset)) {
        out->print("%s+0x%x", buf, offset);
        function_printed = true;
      }
      if ((!function_printed || !os::address_is_in_vm(pc)) &&
          os::dll_address_to_library_name(pc, buf, sizeof(buf), &offset)) {
        const char* libname = strrchr(buf, os::file_separator()[0]);
        if (libname != nullptr) {
          libname++;
        } else {
          libname = buf;
        }
        out->print(" in %s", libname);
        if (!function_printed) {
          out->print("+0x%x", offset);
        }
      }

      // Note: we deliberately omit printing source information here. NativeCallStack::print_on()
      // can be called thousands of times as part of NMT detail reporting, and source printing
      // can slow down reporting by a factor of 5 or more depending on platform (see JDK-8296931).

      out->cr();
    }
  }
}
