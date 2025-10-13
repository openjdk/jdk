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

#include "classfile/classFileStream.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/jni/jfrUpcalls.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrJdkJfrEvent.hpp"
#include "jfr/support/methodtracer/jfrTracedMethod.hpp"
#include "jvm_io.h"
#include "logging/log.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/exceptions.hpp"

static Symbol* jvm_upcalls_class_sym = nullptr;
static Symbol* on_retransform_method_sym = nullptr;
static Symbol* on_retransform_signature_sym = nullptr;
static Symbol* bytes_for_eager_instrumentation_sym = nullptr;
static Symbol* bytes_for_eager_instrumentation_sig_sym = nullptr;
static Symbol* unhide_internal_types_sym = nullptr;
static Symbol* unhide_internal_types_sig_sym = nullptr;
static Symbol* on_method_trace_sym = nullptr;
static Symbol* on_method_trace_sig_sym = nullptr;
static Symbol* publish_method_timers_for_klass_sym = nullptr;
static Symbol* publish_method_timers_for_klass_sig_sym = nullptr;

static bool initialize(TRAPS) {
  static bool initialized = false;
  if (!initialized) {
    DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
    jvm_upcalls_class_sym = SymbolTable::new_permanent_symbol("jdk/jfr/internal/JVMUpcalls");
    on_retransform_method_sym = SymbolTable::new_permanent_symbol("onRetransform");
    on_retransform_signature_sym = SymbolTable::new_permanent_symbol("(JZZLjava/lang/Class;[B)[B");
    bytes_for_eager_instrumentation_sym = SymbolTable::new_permanent_symbol("bytesForEagerInstrumentation");
    bytes_for_eager_instrumentation_sig_sym = SymbolTable::new_permanent_symbol("(JZZLjava/lang/Class;[B)[B");
    unhide_internal_types_sym = SymbolTable::new_permanent_symbol("unhideInternalTypes");
    unhide_internal_types_sig_sym = SymbolTable::new_permanent_symbol("()V");
    on_method_trace_sym = SymbolTable::new_permanent_symbol("onMethodTrace");
    on_method_trace_sig_sym = SymbolTable::new_permanent_symbol("(Ljava/lang/Module;Ljava/lang/ClassLoader;Ljava/lang/String;[B[J[Ljava/lang/String;[Ljava/lang/String;[I)[B");
    publish_method_timers_for_klass_sym = SymbolTable::new_permanent_symbol("publishMethodTimersForClass");
    publish_method_timers_for_klass_sig_sym = SymbolTable::new_permanent_symbol("(J)V");
    initialized = publish_method_timers_for_klass_sig_sym != nullptr;
  }
  return initialized;
}

static typeArrayOop invoke(jlong trace_id,
                           jboolean force_instrumentation,
                           jboolean boot_class_loader,
                           jclass class_being_redefined,
                           jint class_data_len,
                           const unsigned char* class_data,
                           Symbol* method_sym,
                           Symbol* signature_sym,
                           jint& new_bytes_length,
                           TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  const Klass* klass = SystemDictionary::resolve_or_fail(jvm_upcalls_class_sym, true, CHECK_NULL);
  assert(klass != nullptr, "invariant");
  typeArrayOop old_byte_array = oopFactory::new_byteArray(class_data_len, CHECK_NULL);
  memcpy(old_byte_array->byte_at_addr(0), class_data, class_data_len);
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, klass, method_sym, signature_sym);
  args.push_long(trace_id);
  args.push_int(force_instrumentation);
  args.push_int(boot_class_loader);
  args.push_jobject(class_being_redefined);
  args.push_oop(old_byte_array);
  JfrJavaSupport::call_static(&args, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    ResourceMark rm(THREAD);
    log_error(jfr, system)("JfrUpcall failed for %s", method_sym->as_C_string());
    return nullptr;
  }
  // The result should be a [B
  const oop res = result.get_oop();
  assert(res != nullptr, "invariant");
  assert(res->is_typeArray(), "invariant");
  assert(TypeArrayKlass::cast(res->klass())->element_type() == T_BYTE, "invariant");
  const typeArrayOop new_byte_array = typeArrayOop(res);
  new_bytes_length = (jint)new_byte_array->length();
  return new_byte_array;
}

static const size_t ERROR_MSG_BUFFER_SIZE = 256;
static void log_error_and_throw_oom(jint new_bytes_length, TRAPS) {
  char error_buffer[ERROR_MSG_BUFFER_SIZE];
  jio_snprintf(error_buffer, ERROR_MSG_BUFFER_SIZE,
    "Thread local allocation (native) for %zu bytes failed in JfrUpcalls", (size_t)new_bytes_length);
  log_error(jfr, system)("%s", error_buffer);
  JfrJavaSupport::throw_out_of_memory_error(error_buffer, CHECK);
}

void JfrUpcalls::on_retransform(jlong trace_id,
                                jclass class_being_redefined,
                                jint class_data_len,
                                const unsigned char* class_data,
                                jint* new_class_data_len,
                                unsigned char** new_class_data,
                                TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(class_being_redefined != nullptr, "invariant");
  assert(class_data != nullptr, "invariant");
  assert(new_class_data_len != nullptr, "invariant");
  assert(new_class_data != nullptr, "invariant");
  if (!JdkJfrEvent::is_visible(class_being_redefined)) {
    return;
  }
  jint new_bytes_length = 0;
  initialize(THREAD);
  const typeArrayOop new_byte_array = invoke(trace_id,
                                             false,
                                             false, // not used
                                             class_being_redefined,
                                             class_data_len,
                                             class_data,
                                             on_retransform_method_sym,
                                             on_retransform_signature_sym,
                                             new_bytes_length,
                                             CHECK);
  assert(new_byte_array != nullptr, "invariant");
  assert(new_bytes_length > 0, "invariant");
  unsigned char* const new_bytes = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, unsigned char, new_bytes_length);
  if (new_bytes == nullptr) {
    log_error_and_throw_oom(new_bytes_length, THREAD); // unwinds
  }
  assert(new_bytes != nullptr, "invariant");
  memcpy(new_bytes, new_byte_array->byte_at_addr(0), (size_t)new_bytes_length);
  *new_class_data_len = new_bytes_length;
  *new_class_data = new_bytes;
}

void JfrUpcalls::new_bytes_eager_instrumentation(jlong trace_id,
                                                 jboolean force_instrumentation,
                                                 jboolean boot_class_loader,
                                                 jclass super,
                                                 jint class_data_len,
                                                 const unsigned char* class_data,
                                                 jint* new_class_data_len,
                                                 unsigned char** new_class_data,
                                                 TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(super != nullptr, "invariant");
  assert(class_data != nullptr, "invariant");
  assert(new_class_data_len != nullptr, "invariant");
  assert(new_class_data != nullptr, "invariant");
  jint new_bytes_length = 0;
  initialize(THREAD);
  const typeArrayOop new_byte_array = invoke(trace_id,
                                             force_instrumentation,
                                             boot_class_loader,
                                             super,
                                             class_data_len,
                                             class_data,
                                             bytes_for_eager_instrumentation_sym,
                                             bytes_for_eager_instrumentation_sig_sym,
                                             new_bytes_length,
                                             CHECK);
  assert(new_byte_array != nullptr, "invariant");
  assert(new_bytes_length > 0, "invariant");
  unsigned char* const new_bytes = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, unsigned char, new_bytes_length);
  if (new_bytes == nullptr) {
    log_error_and_throw_oom(new_bytes_length, THREAD); // this unwinds
  }
  assert(new_bytes != nullptr, "invariant");
  memcpy(new_bytes, new_byte_array->byte_at_addr(0), (size_t)new_bytes_length);
  *new_class_data_len = new_bytes_length;
  *new_class_data = new_bytes;
}

bool JfrUpcalls::unhide_internal_types(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  if (!initialize(THREAD)) {
    log_error(jfr, system)("JfrUpcall could not be initialized.");
    return false;
  }
  JavaValue result(T_VOID);
  const Klass* klass = SystemDictionary::resolve_or_fail(jvm_upcalls_class_sym, true, CHECK_false);
  assert(klass != nullptr, "invariant");
  JfrJavaArguments args(&result, klass, unhide_internal_types_sym, unhide_internal_types_sig_sym);
  JfrJavaSupport::call_static(&args, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    ResourceMark rm(THREAD);
    log_error(jfr, system)("JfrUpcall failed for %s", unhide_internal_types_sym->as_C_string());
    return false;
  }
  return true;
}

// Caller needs ResourceMark
ClassFileStream* JfrUpcalls::on_method_trace(InstanceKlass* ik, const ClassFileStream* stream, GrowableArray<JfrTracedMethod>* methods, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(stream != nullptr, "invariant");
  assert(methods != nullptr, "invariant");
  assert(methods->is_nonempty(), "invariant");
  initialize(THREAD);
  Klass* klass = SystemDictionary::resolve_or_fail(jvm_upcalls_class_sym, true, CHECK_NULL);
  assert(klass != nullptr, "invariant");

  HandleMark hm(THREAD);

  ModuleEntry* module_entry = ik->module();
  oop module = nullptr;
  if (module_entry != nullptr) {
    module = module_entry->module_oop();
  }
  instanceHandle module_handle(THREAD, (instanceOop)module);

  // ClassLoader
  oop class_loader = ik->class_loader();
  instanceHandle class_loader_handle(THREAD, (instanceOop)class_loader);

  // String class name
  Handle class_name_h = java_lang_String::create_from_symbol(ik->name(), CHECK_NULL);

  // new byte[]
  int size = stream->length();
  typeArrayOop bytecode_array = oopFactory::new_byteArray(size, CHECK_NULL);
  typeArrayHandle h_bytecode_array(THREAD, bytecode_array);

  // Copy ClassFileStream bytes to byte[]
  const jbyte* src = reinterpret_cast<const jbyte*>(stream->buffer());
  ArrayAccess<>::arraycopy_from_native(src, bytecode_array, typeArrayOopDesc::element_offset<jbyte>(0), size);

  int method_count = methods->length();

  // new long[method_count]
  typeArrayOop id_array = oopFactory::new_longArray(method_count, CHECK_NULL);
  typeArrayHandle h_id_array(THREAD, id_array);

  // new String[method_count]
  objArrayOop name_array = oopFactory::new_objArray(vmClasses::String_klass(), method_count, CHECK_NULL);
  objArrayHandle h_name_array(THREAD, name_array);

  // new String[method_count]
  objArrayOop signature_array = oopFactory::new_objArray(vmClasses::String_klass(), method_count, CHECK_NULL);
  objArrayHandle h_signature_array(THREAD, signature_array);

   // new int[method_count]
  typeArrayOop modification_array = oopFactory::new_intArray(method_count, CHECK_NULL);
  typeArrayHandle h_modification_array(THREAD, modification_array);

  // Fill in arrays
  for (int i = 0; i < method_count; i++) {
    JfrTracedMethod method = methods->at(i);
    h_id_array->long_at_put(i, method.id());
    Handle name = java_lang_String::create_from_symbol(method.name(), CHECK_NULL);
    h_name_array->obj_at_put(i, name());
    Handle signature = java_lang_String::create_from_symbol(method.signature(), CHECK_NULL);
    h_signature_array->obj_at_put(i, signature());
    h_modification_array->int_at_put(i, method.modification());
  }

  // Call JVMUpcalls::onMethodTrace
  JavaCallArguments args;
  JavaValue result(T_ARRAY);
  args.push_oop(module_handle);
  args.push_oop(class_loader_handle);
  args.push_oop(class_name_h);
  args.push_oop(h_bytecode_array);
  args.push_oop(h_id_array);
  args.push_oop(h_name_array);
  args.push_oop(h_signature_array);
  args.push_oop(h_modification_array);
  JavaCalls::call_static(&result, klass, on_method_trace_sym, on_method_trace_sig_sym, &args, CHECK_NULL);

  oop return_object = result.get_oop();
  if (return_object != nullptr) {
    assert(return_object->is_typeArray(), "invariant");
    assert(TypeArrayKlass::cast(return_object->klass())->element_type() == T_BYTE, "invariant");
    typeArrayOop byte_array = typeArrayOop(return_object);
    int length = byte_array->length();
    u1* buffer = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, u1, length);
    ArrayAccess<>::arraycopy_to_native<>(byte_array, typeArrayOopDesc::element_offset<jbyte>(0), buffer, length);
    return new ClassFileStream(buffer, length, stream->source(), stream->from_boot_loader_modules_image());
  }
  return nullptr;
}

void JfrUpcalls::publish_method_timers_for_klass(traceid klass_id, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  Klass* const klass = SystemDictionary::resolve_or_fail(jvm_upcalls_class_sym, true, CHECK);
  assert(klass != nullptr, "invariant");
  JavaCallArguments args;
  JavaValue result(T_VOID);
  args.push_long(static_cast<jlong>(klass_id));
  JavaCalls::call_static(&result, klass, publish_method_timers_for_klass_sym, publish_method_timers_for_klass_sig_sym, &args, CHECK);
}
