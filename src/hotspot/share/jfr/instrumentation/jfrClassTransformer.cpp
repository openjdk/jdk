/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoadInfo.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "jfr/instrumentation/jfrClassTransformer.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

static void log_pending_exception(oop throwable) {
  assert(throwable != nullptr, "invariant");
  oop msg = java_lang_Throwable::message(throwable);
  if (msg != nullptr) {
    char* text = java_lang_String::as_utf8_string(msg);
    if (text != nullptr) {
      log_error(jfr, system) ("%s", text);
    }
  }
}

// On initial class load.
void JfrClassTransformer::cache_class_file_data(InstanceKlass* new_ik, const ClassFileStream* new_stream, const JavaThread* thread) {
  assert(new_ik != nullptr, "invariant");
  assert(new_stream != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  assert(!thread->has_pending_exception(), "invariant");
  if (!JfrOptionSet::allow_retransforms()) {
    return;
  }
  const jint stream_len = new_stream->length();
  JvmtiCachedClassFileData* p =
    (JvmtiCachedClassFileData*)NEW_C_HEAP_ARRAY_RETURN_NULL(u1, offset_of(JvmtiCachedClassFileData, data) + stream_len, mtInternal);
  if (p == nullptr) {
    log_error(jfr, system)("Allocation using C_HEAP_ARRAY for %zu bytes failed in JfrEventClassTransformer::cache_class_file_data",
      static_cast<size_t>(offset_of(JvmtiCachedClassFileData, data) + stream_len));
    return;
  }
  p->length = stream_len;
  memcpy(p->data, new_stream->buffer(), stream_len);
  new_ik->set_cached_class_file(p);
}

InstanceKlass* JfrClassTransformer::create_instance_klass(InstanceKlass*& ik, ClassFileStream* stream, bool is_initial_load, JavaThread* thread) {
  if (stream == nullptr) {
    if (is_initial_load) {
      log_error(jfr, system)("JfrClassTransformer: unable to create ClassFileStream for %s", ik->external_name());
    }
    return nullptr;
  }
  InstanceKlass* const new_ik = create_new_instance_klass(ik, stream, thread);
  if (new_ik == nullptr) {
    if (is_initial_load) {
      log_error(jfr, system)("JfrClassTransformer: unable to create InstanceKlass for %s", ik->external_name());
    }
  }
  return new_ik;
}

void JfrClassTransformer::copy_traceid(const InstanceKlass* ik, const InstanceKlass* new_ik) {
  assert(ik != nullptr, "invariant");
  assert(new_ik != nullptr, "invariant");
  new_ik->set_trace_id(ik->trace_id());
  assert(TRACE_ID(ik) == TRACE_ID(new_ik), "invariant");
}

InstanceKlass* JfrClassTransformer::create_new_instance_klass(InstanceKlass* ik, ClassFileStream* stream, TRAPS) {
  assert(stream != nullptr, "invariant");
  ResourceMark rm(THREAD);
  ClassLoaderData* const cld = ik->class_loader_data();
  Handle pd(THREAD, ik->protection_domain());
  Symbol* const class_name = ik->name();
  ClassLoadInfo cl_info(pd);
  ClassFileParser new_parser(stream,
                             class_name,
                             cld,
                             &cl_info,
                             ClassFileParser::INTERNAL, // internal visibility
                             THREAD);
  if (HAS_PENDING_EXCEPTION) {
    log_pending_exception(PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    return nullptr;
  }
  const ClassInstanceInfo* cl_inst_info = cl_info.class_hidden_info_ptr();
  InstanceKlass* const new_ik = new_parser.create_instance_klass(false, *cl_inst_info, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    log_pending_exception(PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    return nullptr;
  }
  assert(new_ik != nullptr, "invariant");
  assert(new_ik->name() != nullptr, "invariant");
  assert(ik->name() == new_ik->name(), "invariant");
  return new_ik;
}

// Redefining / retransforming?
const InstanceKlass* JfrClassTransformer::find_existing_klass(const InstanceKlass* ik, JavaThread* thread) {
  assert(ik != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  JvmtiThreadState* const state = thread->jvmti_thread_state();
  return state != nullptr ? klass_being_redefined(ik, state) : nullptr;
}

const InstanceKlass* JfrClassTransformer::klass_being_redefined(const InstanceKlass* ik, JvmtiThreadState* state) {
  assert(ik != nullptr, "invariant");
  assert(state != nullptr, "invariant");
  const GrowableArray<Klass*>* const redef_klasses = state->get_classes_being_redefined();
  if (redef_klasses == nullptr || redef_klasses->is_empty()) {
    return nullptr;
  }
  for (int i = 0; i < redef_klasses->length(); ++i) {
    const Klass* const existing_klass = redef_klasses->at(i);
    assert(existing_klass != nullptr, "invariant");
    assert(existing_klass->is_instance_klass(), "invariant");
    if (ik->name() == existing_klass->name() && ik->class_loader_data() == existing_klass->class_loader_data()) {
      // 'ik' is a scratch klass. Return the klass being redefined.
      return InstanceKlass::cast(existing_klass);
    }
  }
  return nullptr;
}

// On redefine / retransform, in case an agent modified the class, the original bytes are cached onto the scratch klass.
void JfrClassTransformer::transfer_cached_class_file_data(InstanceKlass* ik, InstanceKlass* new_ik, const ClassFileParser& parser, JavaThread* thread) {
  assert(ik != nullptr, "invariant");
  assert(new_ik != nullptr, "invariant");
  JvmtiCachedClassFileData* const p = ik->get_cached_class_file();
  if (p != nullptr) {
    new_ik->set_cached_class_file(p);
    ik->set_cached_class_file(nullptr);
    return;
  }
  // No cached classfile indicates that no agent modified the klass.
  // This means that the parser is holding the original bytes. Hence, we cache it onto the scratch klass.
  const ClassFileStream* const stream = parser.clone_stream();
  cache_class_file_data(new_ik, stream, thread);
}

void JfrClassTransformer::rewrite_klass_pointer(InstanceKlass*& ik, InstanceKlass* new_ik, ClassFileParser& parser, const JavaThread* thread) {
  assert(ik != nullptr, "invariant");
  assert(new_ik != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  assert(TRACE_ID(ik) == TRACE_ID(new_ik), "invariant");
  assert(!thread->has_pending_exception(), "invariant");
  // Assign original InstanceKlass* back onto "its" parser object for proper destruction.
  parser.set_klass_to_deallocate(ik);
  // Finally rewrite the original pointer to the newly created InstanceKlass.
  ik = new_ik;
}

