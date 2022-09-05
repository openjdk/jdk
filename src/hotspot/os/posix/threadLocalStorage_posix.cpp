/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/threadLocalStorage.hpp"
#include "utilities/debug.hpp"
#include <pthread.h>

static pthread_key_t _thread_key;
static bool _initialized = false;

// Restore the thread pointer if the destructor is called. This is in case
// someone from JNI code sets up a destructor with pthread_key_create to run
// detachCurrentThread on thread death. Unless we restore the thread pointer we
// will hang or crash. When detachCurrentThread is called the key will be set
// to null and we will not be called again. If detachCurrentThread is never
// called we could loop forever depending on the pthread implementation.
extern "C" void restore_thread_pointer(void* p) {
  ThreadLocalStorage::set_thread((Thread*) p);
}

// We initialize Library-based TLS at C++ dynamic initialization time (when
// the libjvm.so is loaded).
// Note however that we cannot rely on initialization order, and we may be
// used even earlier than our initialization runs when called by other
// initialization code (e.g. UL). Therefore we also initialize on demand
// in ThreadLocalStorage::thread().

static void initialize_if_needed() {
  // Notes:
  // - we fatal out if this fails, even in release, since continuing would
  //   mean we use pthread_key_set/getspecific with an uninitialized key
  //   which is UB
  // - pthread_key_create *returns* the error code, it does not set errno
  if (!_initialized) {
    int rslt = pthread_key_create(&_thread_key, restore_thread_pointer);
    if (rslt != 0) {
      fatal("TLS initialization failed (pthread_key_create error %d)", rslt);
    }
    _initialized = true;
  }
}

struct InitTLS { InitTLS() { initialize_if_needed(); }};
static InitTLS _the_initializer;

bool ThreadLocalStorage::is_initialized() {
  return _initialized;
}

Thread* ThreadLocalStorage::thread() {
  initialize_if_needed();
  return (Thread*) pthread_getspecific(_thread_key); // may be NULL
}

void ThreadLocalStorage::set_thread(Thread* current) {
  initialize_if_needed();
  int rslt = pthread_setspecific(_thread_key, current);
  // pthread_setspecific *returns* error code, does not set errno
  assert(rslt == 0, "pthread_setspecific error %d", rslt);
}
