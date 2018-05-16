/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/repository/jfrChunkSizeNotifier.hpp"

size_t JfrChunkSizeNotifier::_chunk_size_threshold = 0;

void JfrChunkSizeNotifier::set_chunk_size_threshold(size_t bytes) {
  _chunk_size_threshold = bytes;
}

size_t JfrChunkSizeNotifier::chunk_size_threshold() {
  return _chunk_size_threshold;
}

static jobject new_chunk_monitor = NULL;

// lazy install
static jobject get_new_chunk_monitor(Thread* thread) {
  static bool initialized = false;
  if (initialized) {
    assert(new_chunk_monitor != NULL, "invariant");
    return new_chunk_monitor;
  }
  assert(new_chunk_monitor == NULL, "invariant");
  // read static field
  HandleMark hm(thread);
  static const char klass[] = "jdk/jfr/internal/JVM";
  static const char field[] = "FILE_DELTA_CHANGE";
  static const char signature[] = "Ljava/lang/Object;";
  JavaValue result(T_OBJECT);
  JfrJavaArguments field_args(&result, klass, field, signature, thread);
  JfrJavaSupport::get_field_global_ref(&field_args, thread);
  new_chunk_monitor = result.get_jobject();
  initialized = new_chunk_monitor != NULL;
  return new_chunk_monitor;
}

void JfrChunkSizeNotifier::notify() {
  Thread* const thread = Thread::current();
  JfrJavaSupport::notify_all(get_new_chunk_monitor(thread), thread);
}

void JfrChunkSizeNotifier::release_monitor() {
  if (new_chunk_monitor != NULL) {
    JfrJavaSupport::destroy_global_jni_handle(new_chunk_monitor);
    new_chunk_monitor = NULL;
  }
}
