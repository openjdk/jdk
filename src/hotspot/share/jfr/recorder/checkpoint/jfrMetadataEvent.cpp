/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/jfrMetadataEvent.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.inline.hpp"

static jbyteArray _metadata_blob = NULL;
static Semaphore metadata_mutex_semaphore(1);

void JfrMetadataEvent::lock() {
  metadata_mutex_semaphore.wait();
}

void JfrMetadataEvent::unlock() {
  metadata_mutex_semaphore.signal();
}

static void write_metadata_blob(JfrChunkWriter& chunkwriter, jbyteArray metadata_blob) {
  if (metadata_blob != NULL) {
    const typeArrayOop arr = (typeArrayOop)JfrJavaSupport::resolve_non_null(metadata_blob);
    assert(arr != NULL, "invariant");
    const int length = arr->length();
    const Klass* const k = arr->klass();
    assert(k != NULL && k->is_array_klass(), "invariant");
    const TypeArrayKlass* const byte_arr_klass = TypeArrayKlass::cast(k);
    const jbyte* const data_address = arr->byte_at_addr(0);
    chunkwriter.write_unbuffered(data_address, length);
  }
}

// the semaphore is assumed to be locked  (was locked previous safepoint)
size_t JfrMetadataEvent::write(JfrChunkWriter& chunkwriter, jlong metadata_offset) {
  assert(chunkwriter.is_valid(), "invariant");
  assert(chunkwriter.current_offset() == metadata_offset, "invariant");
  // header
  chunkwriter.reserve(sizeof(u4));
  chunkwriter.write<u8>(EVENT_METADATA); // ID 0
  // time data
  chunkwriter.write(JfrTicks::now());
  chunkwriter.write((u8)0); // duration
  chunkwriter.write((u8)0); // metadata id
  write_metadata_blob(chunkwriter, _metadata_blob); // payload
  unlock(); // open up for java to provide updated metadata
  // fill in size of metadata descriptor event
  const jlong size_written = chunkwriter.current_offset() - metadata_offset;
  chunkwriter.write_padded_at_offset((u4)size_written, metadata_offset);
  return size_written;
}

void JfrMetadataEvent::update(jbyteArray metadata) {
  JavaThread* thread = (JavaThread*)Thread::current();
  assert(thread->is_Java_thread(), "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));
  lock();
  if (_metadata_blob != NULL) {
    JfrJavaSupport::destroy_global_jni_handle(_metadata_blob);
  }
  const oop new_desc_oop = JfrJavaSupport::resolve_non_null(metadata);
  _metadata_blob = new_desc_oop != NULL ? (jbyteArray)JfrJavaSupport::global_jni_handle(new_desc_oop, thread) : NULL;
  unlock();
}
