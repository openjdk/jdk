/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/storage/jfrStorage.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "memory/iterator.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/threads.hpp"

static int start_pos_offset = invalid_offset;
static int start_pos_address_offset = invalid_offset;
static int current_pos_offset = invalid_offset;
static int max_pos_offset = invalid_offset;
static int excluded_offset = invalid_offset;
static int thread_id_offset = invalid_offset;
static int valid_offset = invalid_offset;

static bool setup_event_writer_offsets(TRAPS) {
  const char class_name[] = "jdk/jfr/internal/event/EventWriter";
  Symbol* const k_sym = SymbolTable::new_symbol(class_name);
  assert(k_sym != nullptr, "invariant");
  Klass* klass = SystemDictionary::resolve_or_fail(k_sym, true, CHECK_false);
  assert(klass != nullptr, "invariant");

  const char start_pos_name[] = "startPosition";
  Symbol* const start_pos_sym = SymbolTable::new_symbol(start_pos_name);
  assert(start_pos_sym != nullptr, "invariant");
  assert(invalid_offset == start_pos_offset, "invariant");
  JfrJavaSupport::compute_field_offset(start_pos_offset, klass, start_pos_sym, vmSymbols::long_signature());
  assert(start_pos_offset != invalid_offset, "invariant");

  const char event_pos_name[] = "currentPosition";
  Symbol* const event_pos_sym = SymbolTable::new_symbol(event_pos_name);
  assert(event_pos_sym != nullptr, "invariant");
  assert(invalid_offset == current_pos_offset, "invariant");
  JfrJavaSupport::compute_field_offset(current_pos_offset, klass, event_pos_sym,vmSymbols::long_signature());
  assert(current_pos_offset != invalid_offset, "invariant");

  const char max_pos_name[] = "maxPosition";
  Symbol* const max_pos_sym = SymbolTable::new_symbol(max_pos_name);
  assert(max_pos_sym != nullptr, "invariant");
  assert(invalid_offset == max_pos_offset, "invariant");
  JfrJavaSupport::compute_field_offset(max_pos_offset, klass, max_pos_sym, vmSymbols::long_signature());
  assert(max_pos_offset != invalid_offset, "invariant");

  const char excluded_name[] = "excluded";
  Symbol* const excluded_sym = SymbolTable::new_symbol(excluded_name);
  assert(excluded_sym != nullptr, "invariant");
  assert(invalid_offset == excluded_offset, "invariant");
  JfrJavaSupport::compute_field_offset(excluded_offset, klass, excluded_sym, vmSymbols::bool_signature());
  assert(excluded_offset != invalid_offset, "invariant");

  const char threadID_name[] = "threadID";
  Symbol * const threadID_sym = SymbolTable::new_symbol(threadID_name);
  assert(threadID_sym != nullptr, "invariant");
  assert(invalid_offset == thread_id_offset, "invariant");
  JfrJavaSupport::compute_field_offset(thread_id_offset, klass, threadID_sym, vmSymbols::long_signature());
  assert(thread_id_offset != invalid_offset, "invariant");

  const char valid_name[] = "valid";
  Symbol* const valid_sym = SymbolTable::new_symbol(valid_name);
  assert (valid_sym != nullptr, "invariant");
  assert(invalid_offset == valid_offset, "invariant");
  JfrJavaSupport::compute_field_offset(valid_offset, klass, valid_sym, vmSymbols::bool_signature());
  assert(valid_offset != invalid_offset, "invariant");
  return true;
}

bool JfrJavaEventWriter::initialize() {
  static bool initialized = false;
  if (!initialized) {
    initialized = setup_event_writer_offsets(JavaThread::current());
  }
  return initialized;
}

void JfrJavaEventWriter::flush(jobject writer, jint used, jint requested, JavaThread* jt) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  assert(writer != nullptr, "invariant");
  JfrBuffer* const current = jt->jfr_thread_local()->java_buffer();
  assert(current != nullptr, "invariant");
  JfrBuffer* const buffer = JfrStorage::flush(current, used, requested, false, jt);
  assert(buffer != nullptr, "invariant");
  // "validity" is contextually defined here to mean
  // that some memory location was provided that is
  // large enough to accommodate the "requested size".
  const bool is_valid = buffer->free_size() >= (size_t)(used + requested);
  u1* const new_current_position = is_valid ? buffer->pos() + used : buffer->pos();
  assert(start_pos_offset != invalid_offset, "invariant");
  // can safepoint here
  ThreadInVMfromNative transition(jt);
  oop const w = JNIHandles::resolve_non_null(writer);
  assert(w != nullptr, "invariant");
  w->long_field_put(start_pos_offset, (jlong)buffer->pos());
  w->long_field_put(current_pos_offset, (jlong)new_current_position);
  // only update java writer if underlying memory changed
  if (buffer != current) {
    w->long_field_put(max_pos_offset, (jlong)buffer->end());
  }
  if (!is_valid) {
    // mark writer as invalid for this write attempt
    w->release_bool_field_put(valid_offset, JNI_FALSE);
  }
}

jlong JfrJavaEventWriter::commit(jlong next_position) {
  assert(next_position != 0, "invariant");
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  assert(tl->has_java_event_writer(), "invariant");
  assert(tl->has_java_buffer(), "invariant");
  JfrBuffer* const current = tl->java_buffer();
  assert(current != nullptr, "invariant");
  u1* const next = reinterpret_cast<u1*>(next_position);
  assert(next >= current->start(), "invariant");
  assert(next <= current->end(), "invariant");
  if (tl->is_notified()) {
    tl->clear_notification();
    return reinterpret_cast<jlong>(current->pos());
  }
  // set_pos() has release semantics
  current->set_pos(next);
  if (!current->lease()) {
    return next_position;
  }
  assert(current->lease(), "invariant");
  flush(tl->java_event_writer(), 0, 0, jt);
  return 0; // signals that the buffer lease was returned.
}

class JfrJavaEventWriterNotificationClosure : public ThreadClosure {
 public:
   void do_thread(Thread* t) {
     if (t->is_Java_thread()) {
       JfrJavaEventWriter::notify(JavaThread::cast(t));
     }
   }
};

void JfrJavaEventWriter::notify() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrJavaEventWriterNotificationClosure closure;
  Threads::threads_do(&closure);
}

static void set_excluded_field(traceid tid, const JavaThread* jt, bool state) {
  assert(jt != nullptr, "invariant");
  jobject event_writer_handle = jt->jfr_thread_local()->java_event_writer();
  if (event_writer_handle == nullptr) {
    return;
  }
  oop event_writer = JNIHandles::resolve_non_null(event_writer_handle);
  assert(event_writer != nullptr, "invariant");
  const jlong event_writer_tid = event_writer->long_field(thread_id_offset);
  if (event_writer_tid == static_cast<jlong>(tid)) {
    event_writer->bool_field_put(excluded_offset, state);
  }
}

void JfrJavaEventWriter::exclude(traceid tid, const JavaThread* jt) {
  set_excluded_field(tid, jt, true);
}

void JfrJavaEventWriter::include(traceid tid, const JavaThread* jt) {
  set_excluded_field(tid, jt, false);
}

void JfrJavaEventWriter::notify(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  if (jt->jfr_thread_local()->has_java_event_writer()) {
    JfrThreadLocal* const tl = jt->jfr_thread_local();
    assert(tl != nullptr, "invariant");
    oop event_writer = JNIHandles::resolve_non_null(tl->java_event_writer());
    assert(event_writer != nullptr, "invariant");
    const jlong start_pos = event_writer->long_field(start_pos_offset);
    if (event_writer->long_field(current_pos_offset) > start_pos) {
      tl->notify();
    }
  }
}

static jobject create_new_event_writer(JfrBuffer* buffer, JfrThreadLocal* tl, TRAPS) {
  assert(buffer != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  HandleMark hm(THREAD);
  static const char klass[] = "jdk/jfr/internal/event/EventWriter";
  static const char method[] = "<init>";
  static const char signature[] = "(JJJZZ)V";
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, klass, method, signature, CHECK_NULL);

  // parameters
  args.push_long((jlong)buffer->pos());
  args.push_long((jlong)buffer->end());
  args.push_long((jlong)JfrThreadLocal::thread_id(THREAD));
  args.push_int((jint)JNI_TRUE); // valid
  args.push_int(tl->is_excluded() ? (jint)JNI_TRUE : (jint)JNI_FALSE); // excluded
  JfrJavaSupport::new_object_global_ref(&args, CHECK_NULL);
  return result.get_jobject();
}

jobject JfrJavaEventWriter::event_writer(JavaThread* jt) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  jobject h_writer = tl->java_event_writer();
  if (h_writer != nullptr) {
    oop writer = JNIHandles::resolve_non_null(h_writer);
    assert(writer != nullptr, "invariant");
    const jlong event_writer_tid = writer->long_field(thread_id_offset);
    const jlong current_tid = static_cast<jlong>(JfrThreadLocal::thread_id(jt));
    if (event_writer_tid != current_tid) {
      const bool excluded = tl->is_excluded();
      writer->bool_field_put(excluded_offset, excluded);
      writer->long_field_put(thread_id_offset, current_tid);
    }
  }
  return h_writer;
}

jobject JfrJavaEventWriter::new_event_writer(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(event_writer(THREAD) == nullptr, "invariant");
  JfrThreadLocal* const tl = THREAD->jfr_thread_local();
  JfrBuffer* const buffer = tl->java_buffer();
  if (buffer == nullptr) {
    JfrJavaSupport::throw_out_of_memory_error("OOME for thread local buffer", THREAD);
    return nullptr;
  }
  jobject h_writer = create_new_event_writer(buffer, tl, CHECK_NULL);
  tl->set_java_event_writer(h_writer);
  assert(tl->has_java_event_writer(), "invariant");
  return h_writer;
}
