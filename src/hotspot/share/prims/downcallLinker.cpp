/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "downcallLinker.hpp"
#include "runtime/interfaceSupport.inline.hpp"

#include <cerrno>
#ifdef _WIN64
#include <Windows.h>
#include <Winsock2.h>
#endif

// We call this from _thread_in_native, right after a downcall
JVM_LEAF(void, DowncallLinker::capture_state(int32_t* value_ptr, int captured_state_mask))
  // keep in synch with jdk.internal.foreign.abi.CapturableState
  enum PreservableValues {
    NONE = 0,
    GET_LAST_ERROR = 1,
    WSA_GET_LAST_ERROR = 1 << 1,
    ERRNO = 1 << 2
  };
#ifdef _WIN64
  if (captured_state_mask & GET_LAST_ERROR) {
    *value_ptr = GetLastError();
  }
  value_ptr++;
  if (captured_state_mask & WSA_GET_LAST_ERROR) {
    *value_ptr = WSAGetLastError();
  }
  value_ptr++;
#endif
  if (captured_state_mask & ERRNO) {
    *value_ptr = errno;
  }
JVM_END

void DowncallLinker::StubGenerator::add_offsets_to_oops(GrowableArray<VMStorage>& java_regs, VMStorage tmp1, VMStorage tmp2) const {
  int reg_idx = 0;
  for (int sig_idx = 0; sig_idx < _num_args; sig_idx++) {
    if (_signature[sig_idx] == T_OBJECT) {
      assert(_signature[sig_idx + 1] == T_LONG, "expected offset after oop");
      VMStorage reg_oop = java_regs.at(reg_idx++);
      VMStorage reg_offset = java_regs.at(reg_idx++);
      sig_idx++; // skip offset
      pd_add_offset_to_oop(reg_oop, reg_offset, tmp1, tmp2);
    } else if (_signature[sig_idx] != T_VOID) {
      reg_idx++;
    }
  }
}
