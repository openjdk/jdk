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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/modules.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/instrumentation/jfrEventClassTransformer.hpp"
#include "jfr/jni/jfrJavaCall.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrOopTraceId.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/support/jfrThreadId.inline.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceOop.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/growableArray.hpp"
#include "classfile/vmSymbols.hpp"

#ifdef ASSERT
static void check_java_thread_state(JavaThread* t, JavaThreadState state) {
  assert(t != nullptr, "invariant");
  assert(t->is_Java_thread(), "invariant");
  assert(t->thread_state() == state, "invariant");
}

void JfrJavaSupport::check_java_thread_in_vm(JavaThread* t) {
  check_java_thread_state(t, _thread_in_vm);
}

void JfrJavaSupport::check_java_thread_in_native(JavaThread* t) {
  check_java_thread_state(t, _thread_in_native);
}

void JfrJavaSupport::check_java_thread_in_java(JavaThread* t) {
  check_java_thread_state(t, _thread_in_Java);
}

#endif

/*
 *  Handles and references
 */
jobject JfrJavaSupport::local_jni_handle(const oop obj, JavaThread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  return t->active_handles()->allocate_handle(t, obj);
}

jobject JfrJavaSupport::local_jni_handle(const jobject handle, JavaThread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  const oop obj = JNIHandles::resolve(handle);
  return obj == nullptr ? nullptr : local_jni_handle(obj, t);
}

void JfrJavaSupport::destroy_local_jni_handle(jobject handle) {
  JNIHandles::destroy_local(handle);
}

jobject JfrJavaSupport::global_jni_handle(const oop obj, JavaThread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  HandleMark hm(t);
  return JNIHandles::make_global(Handle(t, obj));
}

jobject JfrJavaSupport::global_jni_handle(const jobject handle, JavaThread* t) {
  const oop obj = JNIHandles::resolve(handle);
  return obj == nullptr ? nullptr : global_jni_handle(obj, t);
}

void JfrJavaSupport::destroy_global_jni_handle(jobject handle) {
  JNIHandles::destroy_global(handle);
}

jweak JfrJavaSupport::global_weak_jni_handle(const oop obj, JavaThread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  HandleMark hm(t);
  return JNIHandles::make_weak_global(Handle(t, obj));
}

jweak JfrJavaSupport::global_weak_jni_handle(const jobject handle, JavaThread* t) {
  const oop obj = JNIHandles::resolve(handle);
  return obj == nullptr ? nullptr : global_weak_jni_handle(obj, t);
}

void JfrJavaSupport::destroy_global_weak_jni_handle(jweak handle) {
  JNIHandles::destroy_weak_global(handle);
}

oop JfrJavaSupport::resolve_non_null(jobject obj) {
  return JNIHandles::resolve_non_null(obj);
}

oop JfrJavaSupport::resolve(jobject obj) {
  return JNIHandles::resolve(obj);
}

/*
 *  Method invocation
 */
void JfrJavaSupport::call_static(JfrJavaArguments* args, TRAPS) {
  JfrJavaCall::call_static(args, THREAD);
}

void JfrJavaSupport::call_special(JfrJavaArguments* args, TRAPS) {
  JfrJavaCall::call_special(args, THREAD);
}

void JfrJavaSupport::call_virtual(JfrJavaArguments* args, TRAPS) {
  JfrJavaCall::call_virtual(args, THREAD);
}

void JfrJavaSupport::notify_all(jobject object, TRAPS) {
  assert(object != nullptr, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  HandleMark hm(THREAD);
  Handle h_obj(THREAD, resolve_non_null(object));
  assert(h_obj.not_null(), "invariant");
  ObjectSynchronizer::jni_enter(h_obj, THREAD);
  ObjectSynchronizer::notifyall(h_obj, THREAD);
  ObjectSynchronizer::jni_exit(h_obj(), THREAD);
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
}

/*
 *  Object construction
 */
static void object_construction(JfrJavaArguments* args, JavaValue* result, InstanceKlass* klass, TRAPS) {
  assert(args != nullptr, "invariant");
  assert(result != nullptr, "invariant");
  assert(klass != nullptr, "invariant");
  assert(klass->is_initialized(), "invariant");

  HandleMark hm(THREAD);
  instanceOop obj = klass->allocate_instance(CHECK);
  instanceHandle h_obj(THREAD, obj);
  assert(h_obj.not_null(), "invariant");
  args->set_receiver(h_obj);
  result->set_type(T_VOID); // constructor result type
  JfrJavaSupport::call_special(args, CHECK);
  result->set_type(T_OBJECT); // set back to original result type
  result->set_oop(h_obj());
}

static void array_construction(JfrJavaArguments* args, JavaValue* result, InstanceKlass* klass, int array_length, TRAPS) {
  assert(args != nullptr, "invariant");
  assert(result != nullptr, "invariant");
  assert(klass != nullptr, "invariant");
  assert(klass->is_initialized(), "invariant");

  Klass* const ak = klass->array_klass(THREAD);
  ObjArrayKlass::cast(ak)->initialize(THREAD);
  HandleMark hm(THREAD);
  objArrayOop arr = ObjArrayKlass::cast(ak)->allocate(array_length, CHECK);
  result->set_oop(arr);
}

static void create_object(JfrJavaArguments* args, JavaValue* result, TRAPS) {
  assert(args != nullptr, "invariant");
  assert(result != nullptr, "invariant");
  assert(result->get_type() == T_OBJECT, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  InstanceKlass* const klass = static_cast<InstanceKlass*>(args->klass());
  klass->initialize(CHECK);

  const int array_length = args->array_length();

  if (array_length >= 0) {
    array_construction(args, result, klass, array_length, CHECK);
  } else {
    object_construction(args, result, klass, THREAD);
  }
}

static void handle_result(JavaValue* result, bool global_ref, JavaThread* t) {
  assert(result != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(t));
  const oop result_oop = result->get_oop();
  if (result_oop == nullptr) {
    return;
  }
  result->set_jobject(global_ref ?
                      JfrJavaSupport::global_jni_handle(result_oop, t) :
                      JfrJavaSupport::local_jni_handle(result_oop, t));
}

void JfrJavaSupport::new_object(JfrJavaArguments* args, TRAPS) {
  assert(args != nullptr, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  create_object(args, args->result(), THREAD);
}

void JfrJavaSupport::new_object_local_ref(JfrJavaArguments* args, TRAPS) {
  assert(args != nullptr, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue* const result = args->result();
  assert(result != nullptr, "invariant");
  create_object(args, result, CHECK);
  handle_result(result, false, THREAD);
}

void JfrJavaSupport::new_object_global_ref(JfrJavaArguments* args, TRAPS) {
  assert(args != nullptr, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue* const result = args->result();
  assert(result != nullptr, "invariant");
  create_object(args, result, CHECK);
  handle_result(result, true, THREAD);
}

jstring JfrJavaSupport::new_string(const char* c_str, TRAPS) {
  assert(c_str != nullptr, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  const oop result = java_lang_String::create_oop_from_str(c_str, THREAD);
  return (jstring)local_jni_handle(result, THREAD);
}

jobjectArray JfrJavaSupport::new_string_array(int length, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/String", "<init>", "()V", CHECK_NULL);
  args.set_array_length(length);
  new_object_local_ref(&args, THREAD);
  return (jobjectArray)args.result()->get_jobject();
}

jobject JfrJavaSupport::new_java_lang_Boolean(bool value, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/Boolean", "<init>", "(Z)V", CHECK_NULL);
  args.push_int(value ? (jint)JNI_TRUE : (jint)JNI_FALSE);
  new_object_local_ref(&args, THREAD);
  return args.result()->get_jobject();
}

jobject JfrJavaSupport::new_java_lang_Integer(jint value, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/Integer", "<init>", "(I)V", CHECK_NULL);
  args.push_int(value);
  new_object_local_ref(&args, THREAD);
  return args.result()->get_jobject();
}

jobject JfrJavaSupport::new_java_lang_Long(jlong value, TRAPS) {
  DEBUG_ONLY(check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/lang/Long", "<init>", "(J)V", CHECK_NULL);
  args.push_long(value);
  new_object_local_ref(&args, THREAD);
  return args.result()->get_jobject();
}

void JfrJavaSupport::set_array_element(jobjectArray arr, jobject element, int index, JavaThread* t) {
  assert(arr != nullptr, "invariant");
  DEBUG_ONLY(check_java_thread_in_vm(t));
  HandleMark hm(t);
  objArrayHandle a(t, (objArrayOop)resolve_non_null(arr));
  a->obj_at_put(index, resolve_non_null(element));
}

/*
 *  Field access
 */
static void write_int_field(const Handle& h_oop, fieldDescriptor* fd, jint value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != nullptr, "invariant");
  h_oop->int_field_put(fd->offset(), value);
}

static void write_float_field(const Handle& h_oop, fieldDescriptor* fd, jfloat value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != nullptr, "invariant");
  h_oop->float_field_put(fd->offset(), value);
}

static void write_double_field(const Handle& h_oop, fieldDescriptor* fd, jdouble value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != nullptr, "invariant");
  h_oop->double_field_put(fd->offset(), value);
}

static void write_long_field(const Handle& h_oop, fieldDescriptor* fd, jlong value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != nullptr, "invariant");
  h_oop->long_field_put(fd->offset(), value);
}

static void write_oop_field(const Handle& h_oop, fieldDescriptor* fd, const oop value) {
  assert(h_oop.not_null(), "invariant");
  assert(fd != nullptr, "invariant");
  h_oop->obj_field_put(fd->offset(), value);
}

static void write_specialized_field(JfrJavaArguments* args, const Handle& h_oop, fieldDescriptor* fd, bool static_field) {
  assert(args != nullptr, "invariant");
  assert(h_oop.not_null(), "invariant");
  assert(fd != nullptr, "invariant");
  assert(fd->offset() > 0, "invariant");
  assert(args->length() >= 1, "invariant");

  // attempt must set a real value
  assert(args->param(1).get_type() != T_VOID, "invariant");

  switch(fd->field_type()) {
    case T_BOOLEAN:
    case T_CHAR:
    case T_SHORT:
    case T_INT:
      write_int_field(h_oop, fd, args->param(1).get_jint());
      break;
    case T_FLOAT:
      write_float_field(h_oop, fd, args->param(1).get_jfloat());
      break;
    case T_DOUBLE:
      write_double_field(h_oop, fd, args->param(1).get_jdouble());
      break;
    case T_LONG:
      write_long_field(h_oop, fd, args->param(1).get_jlong());
      break;
    case T_OBJECT:
      write_oop_field(h_oop, fd, args->param(1).get_oop());
      break;
    case T_ADDRESS:
      write_oop_field(h_oop, fd, JfrJavaSupport::resolve_non_null(args->param(1).get_jobject()));
      break;
    default:
      ShouldNotReachHere();
  }
}

static void read_specialized_field(JavaValue* result, const Handle& h_oop, fieldDescriptor* fd) {
  assert(result != nullptr, "invariant");
  assert(h_oop.not_null(), "invariant");
  assert(fd != nullptr, "invariant");
  assert(fd->offset() > 0, "invariant");

  switch(fd->field_type()) {
    case T_BOOLEAN:
    case T_CHAR:
    case T_SHORT:
    case T_INT:
      result->set_jint(h_oop->int_field(fd->offset()));
      break;
    case T_FLOAT:
      result->set_jfloat(h_oop->float_field(fd->offset()));
      break;
    case T_DOUBLE:
      result->set_jdouble(h_oop->double_field(fd->offset()));
      break;
    case T_LONG:
      result->set_jlong(h_oop->long_field(fd->offset()));
      break;
    case T_OBJECT:
      result->set_oop(h_oop->obj_field(fd->offset()));
      break;
    default:
      ShouldNotReachHere();
  }
}

static bool find_field(const InstanceKlass* ik,
                       Symbol* name_symbol,
                       Symbol* signature_symbol,
                       fieldDescriptor* fd,
                       bool is_static = false,
                       bool allow_super = false) {
  assert(ik != nullptr, "invariant");
  if (allow_super || is_static) {
    return ik->find_field(name_symbol, signature_symbol, is_static, fd) != nullptr;
  }
  return ik->find_local_field(name_symbol, signature_symbol, fd);
}

static void lookup_field(JfrJavaArguments* args, const InstanceKlass* ik, fieldDescriptor* fd, bool static_field) {
  assert(args != nullptr, "invariant");
  assert(ik != nullptr, "invariant");
  assert(ik->is_initialized(), "invariant");
  assert(fd != nullptr, "invariant");
  find_field(ik, args->name(), args->signature(), fd, static_field, true);
}

static void read_field(JfrJavaArguments* args, JavaValue* result, Thread* thread) {
  assert(result != nullptr, "invariant");
  assert(result->get_oop() == nullptr, "result not initialized");
  const bool static_field = !args->has_receiver();
  fieldDescriptor fd;
  const InstanceKlass* const ik = static_cast<InstanceKlass*>(args->klass());
  lookup_field(args, ik, &fd, static_field);
  assert(fd.offset() > 0, "invariant");
  HandleMark hm(thread);
  Handle h_oop(static_field ? Handle(thread, ik->java_mirror()) : Handle(thread, args->receiver()));
  read_specialized_field(result, h_oop, &fd);
}

static void read_field(JfrJavaArguments* args, JavaValue* result, TRAPS) {
  assert(args != nullptr, "invariant");
  assert(result != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  result->set_oop(nullptr); // Initialize result in case klass initialize throws.
  InstanceKlass* const klass = static_cast<InstanceKlass*>(args->klass());
  klass->initialize(CHECK);
  read_field(args, result, static_cast<Thread*>(THREAD));
}

static void write_field(JfrJavaArguments* args, TRAPS) {
  assert(args != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  InstanceKlass* const klass = static_cast<InstanceKlass*>(args->klass());
  klass->initialize(CHECK);

  const bool static_field = !args->has_receiver();
  fieldDescriptor fd;
  lookup_field(args, klass, &fd, static_field);
  assert(fd.offset() > 0, "invariant");

  HandleMark hm(THREAD);
  Handle h_oop(static_field ? Handle(THREAD, klass->java_mirror()) : Handle(THREAD, args->receiver()));
  write_specialized_field(args, h_oop, &fd, static_field);
}

void JfrJavaSupport::set_field(JfrJavaArguments* args, TRAPS) {
  assert(args != nullptr, "invariant");
  write_field(args, THREAD);
}

void JfrJavaSupport::get_field(JfrJavaArguments* args, TRAPS) {
  assert(args != nullptr, "invariant");
  read_field(args, args->result(), THREAD);
}

static void get_field_ref(JfrJavaArguments* args, bool local_ref, TRAPS) {
  assert(args != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  JavaValue* const result = args->result();
  assert(result != nullptr, "invariant");
  assert(result->get_type() == T_OBJECT, "invariant");
  read_field(args, result, CHECK);
  const oop obj = result->get_oop();
  if (obj == nullptr) {
    return;
  }
  if (local_ref) {
    result->set_jobject(JfrJavaSupport::local_jni_handle(obj, THREAD));
  } else {
    result->set_jobject(JfrJavaSupport::global_jni_handle(obj, THREAD));
  }
}

void JfrJavaSupport::get_field_local_ref(JfrJavaArguments* args, TRAPS) {
  get_field_ref(args, true /* local_ref */, THREAD);
}

void JfrJavaSupport::get_field_global_ref(JfrJavaArguments* args, TRAPS) {
  get_field_ref(args, false /* local_ref */, THREAD);
}

/*
 *  Misc
 */
Klass* JfrJavaSupport::klass(const jobject handle) {
  const oop obj = resolve_non_null(handle);
  assert(obj != nullptr, "invariant");
  return obj->klass();
}

static char* allocate_string(bool c_heap, int length, Thread* thread) {
  return c_heap ? NEW_C_HEAP_ARRAY(char, length, mtTracing) :
                  NEW_RESOURCE_ARRAY_IN_THREAD(thread, char, length);
}

const char* JfrJavaSupport::c_str(oop string, Thread* thread, bool c_heap /* false */) {
  char* str = nullptr;
  const typeArrayOop value = java_lang_String::value(string);
  if (value != nullptr) {
    const int length = java_lang_String::utf8_length(string, value);
    str = allocate_string(c_heap, length + 1, thread);
    if (str == nullptr) {
      return nullptr;
    }
    java_lang_String::as_utf8_string(string, value, str, length + 1);
  }
  return str;
}

const char* JfrJavaSupport::c_str(jstring string, Thread* thread, bool c_heap /* false */) {
  return string != nullptr ? c_str(resolve_non_null(string), thread, c_heap) : nullptr;
}

static Symbol** allocate_symbol_array(bool c_heap, int length, Thread* thread) {
  return c_heap ?
           NEW_C_HEAP_ARRAY(Symbol*, length, mtTracing) :
           NEW_RESOURCE_ARRAY_IN_THREAD(thread, Symbol*, length);
}

Symbol** JfrJavaSupport::symbol_array(jobjectArray string_array, JavaThread* thread, intptr_t* result_array_size, bool c_heap /* false */) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));
  assert(string_array != nullptr, "invariant");
  assert(result_array_size != nullptr, "invariant");
  objArrayOop arrayOop = objArrayOop(resolve_non_null(string_array));
  const int length = arrayOop->length();
  *result_array_size = length;
  Symbol** result_array = allocate_symbol_array(c_heap, length, thread);
  assert(result_array != nullptr, "invariant");
  for (int i = 0; i < length; i++) {
    oop object = arrayOop->obj_at(i);
    Symbol* symbol = nullptr;
    if (object != nullptr) {
      const char* text = c_str(arrayOop->obj_at(i), thread, c_heap);
      symbol = SymbolTable::new_symbol(text);
    }
    result_array[i] = symbol;
  }
  return result_array;
}

/*
 *  Exceptions and errors
 */
static void create_and_throw(Symbol* name, const char* message, TRAPS) {
  assert(name != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(!HAS_PENDING_EXCEPTION, "invariant");
  THROW_MSG(name, message);
}

void JfrJavaSupport::throw_illegal_state_exception(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_IllegalStateException(), message, THREAD);
}

void JfrJavaSupport::throw_internal_error(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_InternalError(), message, THREAD);
}

void JfrJavaSupport::throw_illegal_argument_exception(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_IllegalArgumentException(), message, THREAD);
}

void JfrJavaSupport::throw_out_of_memory_error(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_OutOfMemoryError(), message, THREAD);
}

void JfrJavaSupport::throw_class_format_error(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_ClassFormatError(), message, THREAD);
}

void JfrJavaSupport::throw_runtime_exception(const char* message, TRAPS) {
  create_and_throw(vmSymbols::java_lang_RuntimeException(), message, THREAD);
}

void JfrJavaSupport::abort(jstring errorMsg, JavaThread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  ResourceMark rm(t);
  abort(c_str(errorMsg, t));
}

void JfrJavaSupport::abort(const char* error_msg, bool dump_core /* true */) {
  if (error_msg != nullptr) {
    log_error(jfr, system)("%s", error_msg);
  }
  log_error(jfr, system)("%s", "An irrecoverable error in Jfr. Shutting down VM...");
  vm_abort(dump_core);
}

JfrJavaSupport::CAUSE JfrJavaSupport::_cause = JfrJavaSupport::VM_ERROR;
void JfrJavaSupport::set_cause(jthrowable throwable, JavaThread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));

  HandleMark hm(t);
  Handle ex(t, JNIHandles::resolve_external_guard(throwable));

  if (ex.is_null()) {
    return;
  }

  if (ex->is_a(vmClasses::OutOfMemoryError_klass())) {
    _cause = OUT_OF_MEMORY;
    return;
  }
  if (ex->is_a(vmClasses::StackOverflowError_klass())) {
    _cause = STACK_OVERFLOW;
    return;
  }
  if (ex->is_a(vmClasses::Error_klass())) {
    _cause = VM_ERROR;
    return;
  }
  if (ex->is_a(vmClasses::RuntimeException_klass())) {
    _cause = RUNTIME_EXCEPTION;
    return;
  }
  if (ex->is_a(vmClasses::Exception_klass())) {
    _cause = UNKNOWN;
    return;
  }
}

void JfrJavaSupport::uncaught_exception(jthrowable throwable, JavaThread* t) {
  DEBUG_ONLY(check_java_thread_in_vm(t));
  assert(throwable != nullptr, "invariant");
  set_cause(throwable, t);
}

JfrJavaSupport::CAUSE JfrJavaSupport::cause() {
  return _cause;
}

const char* const JDK_JFR_MODULE_NAME = "jdk.jfr";
const char* const JDK_JFR_PACKAGE_NAME = "jdk/jfr";



void JfrJavaSupport::load_jdk_jfr_module(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  Handle h_module_name = java_lang_String::create_from_str(JDK_JFR_MODULE_NAME, CHECK);
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
    vmClasses::module_Modules_klass(),
    vmSymbols::loadModule_name(),
    vmSymbols::loadModule_signature(),
    h_module_name,
    CHECK
  );
}

static bool is_jdk_jfr_module_in_readability_graph() {
  // take one of the packages in the module to be located and query for its definition.
  TempNewSymbol pkg_sym = SymbolTable::new_symbol(JDK_JFR_PACKAGE_NAME);
  return Modules::is_package_defined(pkg_sym, Handle());
}

static void print_module_resolution_error(outputStream* stream) {
  assert(stream != nullptr, "invariant");
  stream->print_cr("Module %s not found.", JDK_JFR_MODULE_NAME);
  stream->print_cr("Flight Recorder can not be enabled.");
}

bool JfrJavaSupport::is_jdk_jfr_module_available() {
  return is_jdk_jfr_module_in_readability_graph();
}

bool JfrJavaSupport::is_jdk_jfr_module_available(outputStream* stream, TRAPS) {
  if (!JfrJavaSupport::is_jdk_jfr_module_available()) {
    if (stream != nullptr) {
      print_module_resolution_error(stream);
    }
    return false;
  }
  return true;
}

typedef JfrOopTraceId<ThreadIdAccess> AccessThreadTraceId;


static JavaThread* get_native(ThreadsListHandle& tlh, jobject thread) {
  JavaThread* native_thread = nullptr;
  (void)tlh.cv_internal_thread_to_JavaThread(thread, &native_thread, nullptr);
  return native_thread;
}

static bool is_virtual_thread(oop ref) {
  const Klass* const k = ref->klass();
  assert(k != nullptr, "invariant");
  return k->is_subclass_of(vmClasses::VirtualThread_klass());
}

jlong JfrJavaSupport::jfr_thread_id(JavaThread* jt, jobject thread) {
  assert(jt != nullptr, "invariant");
  oop ref = resolve(thread);
  if (ref == nullptr) {
    return 0;
  }
  const traceid tid = AccessThreadTraceId::id(ref);
  if (is_virtual_thread(ref)) {
    const u2 epoch = JfrTraceIdEpoch::epoch_generation();
    if (AccessThreadTraceId::epoch(ref) != epoch) {
      AccessThreadTraceId::set_epoch(ref, epoch);
      JfrCheckpointManager::write_checkpoint(jt, tid, ref);
    }
  }
  return static_cast<jlong>(tid);
}

void JfrJavaSupport::exclude(JavaThread* jt, oop ref, jobject thread) {
  if (ref != nullptr) {
    AccessThreadTraceId::exclude(ref);
    if (is_virtual_thread(ref)) {
      if (ref == jt->vthread()) {
        JfrThreadLocal::exclude_vthread(jt);
      }
      return;
    }
  }
  ThreadsListHandle tlh;
  jt = get_native(tlh, thread);
  if (jt != nullptr) {
    JfrThreadLocal::exclude_jvm_thread(jt);
  }
}

void JfrJavaSupport::include(JavaThread* jt, oop ref, jobject thread) {
  if (ref != nullptr) {
    AccessThreadTraceId::include(ref);
    if (is_virtual_thread(ref)) {
      if (ref == jt->vthread()) {
        JfrThreadLocal::include_vthread(jt);
      }
      return;
    }
  }
  ThreadsListHandle tlh;
  jt = get_native(tlh, thread);
  if (jt != nullptr) {
    JfrThreadLocal::include_jvm_thread(jt);
  }
}

void JfrJavaSupport::exclude(Thread* thread) {
  assert(thread != nullptr, "invariant");
  if (thread->is_Java_thread()) {
    JavaThread* const jt = JavaThread::cast(thread);
    exclude(jt, jt->threadObj(), nullptr);
    return;
  }
  JfrThreadLocal::exclude_jvm_thread(thread);
}

void JfrJavaSupport::include(Thread* thread) {
  assert(thread != nullptr, "invariant");
  if (thread->is_Java_thread()) {
    JavaThread* const jt = JavaThread::cast(thread);
    include(jt, jt->threadObj(), nullptr);
    return;
  }
  JfrThreadLocal::include_jvm_thread(thread);
}

void JfrJavaSupport::exclude(JavaThread* jt, jobject thread) {
  oop ref = resolve(thread);
  assert(ref != nullptr, "invariant");
  exclude(jt, ref, thread);
}

void JfrJavaSupport::include(JavaThread* jt, jobject thread) {
  oop ref = resolve(thread);
  assert(ref != nullptr, "invariant");
  include(jt, ref, thread);
}

bool JfrJavaSupport::is_excluded(jobject thread) {
  oop ref = resolve(thread);
  assert(ref != nullptr, "invariant");
  return AccessThreadTraceId::is_excluded(ref);
}

bool JfrJavaSupport::is_excluded(Thread* thread) {
  assert(thread != nullptr, "invariant");
  if (thread->is_Java_thread()) {
    JavaThread* const jt = JavaThread::cast(thread);
    oop ref = jt->threadObj();
    return ref != nullptr ? AccessThreadTraceId::is_excluded(ref) : false;
  }
  return JfrThreadLocal::is_jvm_thread_excluded(thread);
}

static const Klass* get_configuration_field_descriptor(const Handle& h_mirror, fieldDescriptor* descriptor, TRAPS) {
  assert(h_mirror.not_null(), "invariant");
  assert(descriptor != nullptr, "invariant");
  Klass* const k = java_lang_Class::as_Klass(h_mirror());
  assert(k->is_instance_klass(), "invariant");
  InstanceKlass* const ik = InstanceKlass::cast(k);
  if (ik->is_not_initialized()) {
    ik->initialize(CHECK_NULL);
  }
  assert(ik->is_being_initialized() || ik->is_initialized(), "invariant");
  const Klass* const typed_field_holder = ik->find_field(vmSymbols::eventConfiguration_name(),
                                                         vmSymbols::jdk_jfr_internal_event_EventConfiguration_signature(),
                                                         true,
                                                         descriptor);
  return typed_field_holder != nullptr ? typed_field_holder : ik->find_field(vmSymbols::eventConfiguration_name(),
                                                                          vmSymbols::object_signature(), // untyped
                                                                          true,
                                                                          descriptor);
}

jobject JfrJavaSupport::get_configuration(jobject clazz, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  HandleMark hm(THREAD);
  const Handle h_mirror(Handle(THREAD, JNIHandles::resolve(clazz)));
  assert(h_mirror.not_null(), "invariant");
  fieldDescriptor configuration_field_descriptor;
  const Klass* const field_holder = get_configuration_field_descriptor(h_mirror, &configuration_field_descriptor, THREAD);
  if (field_holder == nullptr) {
    // The only reason should be that klass initialization failed.
    return nullptr;
  }
  assert(java_lang_Class::as_Klass(h_mirror()) == field_holder, "invariant");
  oop configuration_oop = h_mirror->obj_field(configuration_field_descriptor.offset());
  return configuration_oop != nullptr ? JfrJavaSupport::local_jni_handle(configuration_oop, THREAD) : nullptr;
}

bool JfrJavaSupport::set_configuration(jobject clazz, jobject configuration, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  HandleMark hm(THREAD);
  const Handle h_mirror(Handle(THREAD, JNIHandles::resolve(clazz)));
  assert(h_mirror.not_null(), "invariant");
  fieldDescriptor configuration_field_descriptor;
  const Klass* const field_holder = get_configuration_field_descriptor(h_mirror, &configuration_field_descriptor, THREAD);
  if (field_holder == nullptr) {
    // The only reason should be that klass initialization failed.
    return false;
  }
  assert(java_lang_Class::as_Klass(h_mirror()) == field_holder, "invariant");
  const oop configuration_oop = JNIHandles::resolve(configuration);
  assert(configuration_oop != nullptr, "invariant");
  h_mirror->obj_field_put(configuration_field_descriptor.offset(), configuration_oop);
  return true;
}

bool JfrJavaSupport::is_instrumented(jobject clazz, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  const Klass* const k = java_lang_Class::as_Klass(resolve_non_null(clazz));
  assert(k->is_instance_klass(), "invariant");
  return JfrEventClassTransformer::is_instrumented(InstanceKlass::cast(k));
}

bool JfrJavaSupport::on_thread_start(Thread* t) {
  assert(t != nullptr, "invariant");
  assert(Thread::current() == t, "invariant");
  if (!t->is_Java_thread()) {
    return true;
  }
  JavaThread* const jt = JavaThread::cast(t);
  assert(!JfrThreadLocal::is_vthread(jt), "invariant");
  if (is_excluded(jt)) {
    JfrThreadLocal::exclude_jvm_thread(jt);
    return false;
  }
  return true;
}

bool JfrJavaSupport::compute_field_offset(int &dest_offset,
                                          Klass* klass,
                                          Symbol* name_symbol,
                                          Symbol* signature_symbol,
                                          bool is_static,
                                          bool allow_super) {
  fieldDescriptor fd;
  const InstanceKlass* const ik = InstanceKlass::cast(klass);
  if (!find_field(ik, name_symbol, signature_symbol, &fd, is_static, allow_super)) {
    return false;
  }
  dest_offset = fd.offset();
  return true;
}
