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
#include "runtime/safefetch_method.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef SAFEFETCH_METHOD_SIGSETJMP

// For SafeFetch we need POSIX tls and setjmp
// (Note: for some reason __thread does not work; needs investigation. For now
//  stick with Posix TLS)
#include <setjmp.h>
#include <pthread.h>
static pthread_key_t g_jmpbuf_key;

struct InitTLSKey { InitTLSKey() { pthread_key_create(&g_jmpbuf_key, NULL); } };
static InitTLSKey g_init_tly_key;

// return the currently active jump buffer for this thread
//  - if there is any, NULL otherwise. Called from
//    zero signal handlers.
static sigjmp_buf* get_jmp_buf_for_continuation() {
  return (sigjmp_buf*) pthread_getspecific(g_jmpbuf_key);
}

// Handle safefetch, sigsetjmp style. Only call from signal handler.
// If a safefetch jump had been established and the sig qualifies, we
// jump back to the established jump point (and hence out of signal handling).
void handle_safefetch(int sig) {
  if (sig == SIGSEGV || sig == SIGBUS) {
    sigjmp_buf* const jb = get_jmp_buf_for_continuation();
    if (jb) {
      siglongjmp(*jb, 1);
    }
  }
}

template <class T>
static bool _SafeFetchXX_internal(const T *adr, T* result) {

  T n = 0;
  // set up a jump buffer; anchor the pointer to the jump buffer in tls; then
  // do the pointer access. If pointer is invalid, we crash; in signal
  // handler, we retrieve pointer to jmp buffer from tls, and jump back.
  //
  // Note: the jump buffer itself - which can get pretty large depending on
  // the architecture - lives on the stack and that is fine, because we will
  // not rewind the stack: either we crash, in which case signal handler
  // frame is below us, or we don't crash, in which case it does not matter.
  sigjmp_buf jb;
  if (sigsetjmp(jb, 1)) {

    // we crashed. clean up tls and return default value.
    pthread_setspecific(g_jmpbuf_key, NULL);

  } else {
    // save jump location
    pthread_setspecific(g_jmpbuf_key, &jb);

    // unsafe access
    n = *adr;

    // We are still here. All went well. Reset jump location
    pthread_setspecific(g_jmpbuf_key, NULL);

    *result = n;
    return true;
  }

  return false;
}

int SafeFetch32(int *adr, int errValue) {
  int result;
  return _SafeFetchXX_internal<int>(adr, &result) ? result : errValue;
}

intptr_t SafeFetchN(intptr_t *adr, intptr_t errValue) {
  intptr_t result;
  return _SafeFetchXX_internal<intptr_t>(adr, &result) ? result : errValue;
}

#endif // SAFEFETCH_METHOD_SIGSETJMP

