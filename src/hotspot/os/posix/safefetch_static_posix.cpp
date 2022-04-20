/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/safefetch.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef SAFEFETCH_METHOD_STATIC_ASSEMBLY

// SafeFetch handling, static assembly style:
//
// SafeFetch32 and SafeFetchN are implemented via static assembly
// and live in os_cpu/xx_xx/safefetch_xx_xx.S

extern "C" char _SafeFetch32_continuation[] __attribute__ ((visibility ("hidden")));
extern "C" char _SafeFetch32_fault[] __attribute__ ((visibility ("hidden")));

#ifdef _LP64
extern "C" char _SafeFetchN_continuation[] __attribute__ ((visibility ("hidden")));
extern "C" char _SafeFetchN_fault[] __attribute__ ((visibility ("hidden")));
#endif // _LP64

bool handle_safefetch(int sig, address pc, void* context) {
  ucontext_t* uc = (ucontext_t*)context;
  if ((sig == SIGSEGV || sig == SIGBUS) && uc != NULL) {
    address pc = os::Posix::ucontext_get_pc(uc);
    if (pc == (address)_SafeFetch32_fault) {
      os::Posix::ucontext_set_pc(uc, (address)_SafeFetch32_continuation);
      return true;
    }
#ifdef _LP64
    if (pc == (address)_SafeFetchN_fault) {
      os::Posix::ucontext_set_pc(uc, (address)_SafeFetchN_continuation);
      return true;
    }
#endif
  }
  return false;
}

#endif // SAFEFETCH_METHOD_STATIC_ASSEMBLY
