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

#include "runtime/safefetch.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef SAFEFETCH_METHOD_SIGSETJMP

// For SafeFetch we need POSIX TLS and sigsetjmp/longjmp.
#include <setjmp.h>
#include <pthread.h>
static pthread_key_t g_jmpbuf_key;

struct InitTLSKey { InitTLSKey() { pthread_key_create(&g_jmpbuf_key, NULL); } };
static InitTLSKey g_init_tly_key;

// Handle safefetch, sigsetjmp style:
//
// If a safefetch jump had been established and the sig qualifies, we
// jump back to the established jump point (and hence out of signal handling).
//
// Note that this function will never return for safefetch faults. We just
// keep the prototype the same as other handle_safefetch() versions to keep
// caller sites simple.
bool handle_safefetch(int sig, address ignored1, void* ignored2) {
  if (sig == SIGSEGV || sig == SIGBUS) {
    // Retrieve jump buffer pointer from TLS. If not NULL, it means we set the
    // jump buffer and this is indeed a SafeFetch fault.
    // Note signal safety: pthread_getspecific is not safe for signal handler
    // usage, but in practice it works and we have done this in the JVM for many
    // years (via Thread::current_or_null_safe()).
    sigjmp_buf* const jb = (sigjmp_buf*) pthread_getspecific(g_jmpbuf_key);
    if (jb) {
      siglongjmp(*jb, 1);
    }
  }
  return false;
}

template <class T>
static bool _SafeFetchXX_internal(const T *adr, T* result) {

  T n = 0;

  // Set up a jump buffer. Anchor its pointer in TLS. Then read from the unsafe address.
  // If that address was invalid, we fault, and in the signal handler we will jump back
  // to the jump point.
  sigjmp_buf jb;
  if (sigsetjmp(jb, 1) != 0) {
    // We faulted. Reset TLS slot, then return.
    pthread_setspecific(g_jmpbuf_key, NULL);
    *result = 0;
    return false;
  }

  // Anchor jump buffer in TLS
  pthread_setspecific(g_jmpbuf_key, &jb);

  // unsafe access
  n = *adr;

  // Still here... All went well, adr was valid.
  // Reset TLS slot, then return result.
  pthread_setspecific(g_jmpbuf_key, NULL);
  *result = n;

  return true;

}

int SafeFetch32_impl(int *adr, int errValue) {
  int result;
  return _SafeFetchXX_internal<int>(adr, &result) ? result : errValue;
}

intptr_t SafeFetchN_impl(intptr_t *adr, intptr_t errValue) {
  intptr_t result;
  return _SafeFetchXX_internal<intptr_t>(adr, &result) ? result : errValue;
}

#endif // SAFEFETCH_METHOD_SIGSETJMP
