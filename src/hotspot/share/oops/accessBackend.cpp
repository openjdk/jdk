/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "accessBackend.inline.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/vmError.hpp"

namespace AccessInternal {
// These forward copying calls to Copy without exposing the Copy type in headers unnecessarily

  void arraycopy_arrayof_conjoint_oops(void* src, void* dst, size_t length) {
    Copy::arrayof_conjoint_oops(reinterpret_cast<HeapWord*>(src),
                                reinterpret_cast<HeapWord*>(dst), length);
  }

  void arraycopy_conjoint_oops(oop* src, oop* dst, size_t length) {
    Copy::conjoint_oops_atomic(src, dst, length);
  }

  void arraycopy_conjoint_oops(narrowOop* src, narrowOop* dst, size_t length) {
    Copy::conjoint_oops_atomic(src, dst, length);
  }

  void arraycopy_disjoint_words(void* src, void* dst, size_t length) {
    Copy::disjoint_words(reinterpret_cast<HeapWord*>(src),
                         reinterpret_cast<HeapWord*>(dst), length);
  }

  void arraycopy_disjoint_words_atomic(void* src, void* dst, size_t length) {
    Copy::disjoint_words_atomic(reinterpret_cast<HeapWord*>(src),
                                reinterpret_cast<HeapWord*>(dst), length);
  }

  template<>
  void arraycopy_conjoint<jboolean>(jboolean* src, jboolean* dst, size_t length) {
    Copy::conjoint_jbytes(reinterpret_cast<jbyte*>(src), reinterpret_cast<jbyte*>(dst), length);
  }

  template<>
  void arraycopy_conjoint<jbyte>(jbyte* src, jbyte* dst, size_t length) {
    Copy::conjoint_jbytes(src, dst, length);
  }

  template<>
  void arraycopy_conjoint<jchar>(jchar* src, jchar* dst, size_t length) {
    Copy::conjoint_jshorts_atomic(reinterpret_cast<jshort*>(src), reinterpret_cast<jshort*>(dst), length);
  }

  template<>
  void arraycopy_conjoint<jshort>(jshort* src, jshort* dst, size_t length) {
    Copy::conjoint_jshorts_atomic(src, dst, length);
  }

  template<>
  void arraycopy_conjoint<jint>(jint* src, jint* dst, size_t length) {
    Copy::conjoint_jints_atomic(src, dst, length);
  }

  template<>
  void arraycopy_conjoint<jfloat>(jfloat* src, jfloat* dst, size_t length) {
    Copy::conjoint_jints_atomic(reinterpret_cast<jint*>(src), reinterpret_cast<jint*>(dst), length);
  }

  template<>
  void arraycopy_conjoint<jlong>(jlong* src, jlong* dst, size_t length) {
    Copy::conjoint_jlongs_atomic(src, dst, length);
  }

  template<>
  void arraycopy_conjoint<jdouble>(jdouble* src, jdouble* dst, size_t length) {
    Copy::conjoint_jlongs_atomic(reinterpret_cast<jlong*>(src), reinterpret_cast<jlong*>(dst), length);
  }

  template<>
  void arraycopy_arrayof_conjoint<jbyte>(jbyte* src, jbyte* dst, size_t length) {
    Copy::arrayof_conjoint_jbytes(reinterpret_cast<HeapWord*>(src),
                                  reinterpret_cast<HeapWord*>(dst),
                                  length);
  }

  template<>
  void arraycopy_arrayof_conjoint<jshort>(jshort* src, jshort* dst, size_t length) {
    Copy::arrayof_conjoint_jshorts(reinterpret_cast<HeapWord*>(src),
                                   reinterpret_cast<HeapWord*>(dst),
                                   length);
  }

  template<>
  void arraycopy_arrayof_conjoint<jint>(jint* src, jint* dst, size_t length) {
    Copy::arrayof_conjoint_jints(reinterpret_cast<HeapWord*>(src),
                                 reinterpret_cast<HeapWord*>(dst),
                                 length);
  }

  template<>
  void arraycopy_arrayof_conjoint<jlong>(jlong* src, jlong* dst, size_t length) {
    Copy::arrayof_conjoint_jlongs(reinterpret_cast<HeapWord*>(src),
                                  reinterpret_cast<HeapWord*>(dst),
                                  length);
  }

  template<>
  void arraycopy_conjoint<void>(void* src, void* dst, size_t length) {
    Copy::conjoint_jbytes(reinterpret_cast<jbyte*>(src),
                          reinterpret_cast<jbyte*>(dst),
                          length);
  }

  template<>
  void arraycopy_conjoint_atomic<jbyte>(jbyte* src, jbyte* dst, size_t length) {
    Copy::conjoint_jbytes_atomic(src, dst, length);
  }

  template<>
  void arraycopy_conjoint_atomic<jshort>(jshort* src, jshort* dst, size_t length) {
    Copy::conjoint_jshorts_atomic(src, dst, length);
  }

  template<>
  void arraycopy_conjoint_atomic<jint>(jint* src, jint* dst, size_t length) {
    Copy::conjoint_jints_atomic(src, dst, length);
  }

  template<>
  void arraycopy_conjoint_atomic<jlong>(jlong* src, jlong* dst, size_t length) {
    Copy::conjoint_jlongs_atomic(src, dst, length);
  }

  template<>
  void arraycopy_conjoint_atomic<void>(void* src, void* dst, size_t length) {
    Copy::conjoint_memory_atomic(src, dst, length);
  }

#ifdef ASSERT
  void check_access_thread_state() {
    if (VMError::is_error_reported() || DebuggingContext::is_enabled()) {
      return;
    }

    Thread* thread = Thread::current();
    if (!thread->is_Java_thread()) {
      return;
    }

    JavaThread* java_thread = JavaThread::cast(thread);
    JavaThreadState state = java_thread->thread_state();
    assert(state == _thread_in_vm || state == _thread_in_Java || state == _thread_new,
           "Wrong thread state for accesses: %d", (int)state);
  }
#endif
}
