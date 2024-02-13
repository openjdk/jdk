/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerEvent.hpp"
#include "compiler/disassembler.hpp"
#include "compiler/oopMap.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/linkResolver.hpp"
#include "interpreter/oopMapCache.hpp"
#include "jfr/jfrEvents.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/reflection.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframe_hp.hpp"
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

JVMCIKlassHandle::JVMCIKlassHandle(Thread* thread, Klass* klass) {
  _thread = thread;
  _klass = klass;
  if (klass != nullptr) {
    _holder = Handle(_thread, klass->klass_holder());
  }
}

JVMCIKlassHandle& JVMCIKlassHandle::operator=(Klass* klass) {
  _klass = klass;
  if (klass != nullptr) {
    _holder = Handle(_thread, klass->klass_holder());
  }
  return *this;
}

static void requireInHotSpot(const char* caller, JVMCI_TRAPS) {
  if (!JVMCIENV->is_hotspot()) {
    JVMCI_THROW_MSG(IllegalStateException, err_msg("Cannot call %s from JVMCI shared library", caller));
  }
}

class JVMCITraceMark : public StackObj {
  const char* _msg;
 public:
  JVMCITraceMark(const char* msg) {
    _msg = msg;
    JVMCI_event_2("Enter %s", _msg);
  }
  ~JVMCITraceMark() {
    JVMCI_event_2(" Exit %s", _msg);
  }
};

class JavaArgumentUnboxer : public SignatureIterator {
 protected:
  JavaCallArguments*  _jca;
  arrayOop _args;
  int _index;

  Handle next_arg(BasicType expectedType);

 public:
  JavaArgumentUnboxer(Symbol* signature,
                      JavaCallArguments* jca,
                      arrayOop args,
                      bool is_static)
    : SignatureIterator(signature)
  {
    this->_return_type = T_ILLEGAL;
    _jca = jca;
    _index = 0;
    _args = args;
    if (!is_static) {
      _jca->push_oop(next_arg(T_OBJECT));
    }
    do_parameters_on(this);
    assert(_index == args->length(), "arg count mismatch with signature");
  }

 private:
  friend class SignatureIterator;  // so do_parameters_on can call do_type
  void do_type(BasicType type) {
    if (is_reference_type(type)) {
      _jca->push_oop(next_arg(T_OBJECT));
      return;
    }
    Handle arg = next_arg(type);
    int box_offset = java_lang_boxing_object::value_offset(type);
    switch (type) {
    case T_BOOLEAN:     _jca->push_int(arg->bool_field(box_offset));    break;
    case T_CHAR:        _jca->push_int(arg->char_field(box_offset));    break;
    case T_SHORT:       _jca->push_int(arg->short_field(box_offset));   break;
    case T_BYTE:        _jca->push_int(arg->byte_field(box_offset));    break;
    case T_INT:         _jca->push_int(arg->int_field(box_offset));     break;
    case T_LONG:        _jca->push_long(arg->long_field(box_offset));   break;
    case T_FLOAT:       _jca->push_float(arg->float_field(box_offset));    break;
    case T_DOUBLE:      _jca->push_double(arg->double_field(box_offset));  break;
    default:            ShouldNotReachHere();
    }
  }
};

Handle JavaArgumentUnboxer::next_arg(BasicType expectedType) {
  assert(_index < _args->length(), "out of bounds");
  oop arg=((objArrayOop) (_args))->obj_at(_index++);
  assert(expectedType == T_OBJECT || java_lang_boxing_object::is_instance(arg, expectedType), "arg type mismatch");
  return Handle(Thread::current(), arg);
}

// Bring the JVMCI compiler thread into the VM state.
#define JVMCI_VM_ENTRY_MARK                                       \
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, thread));       \
  ThreadInVMfromNative __tiv(thread);                             \
  HandleMarkCleaner __hm(thread);                                 \
  JavaThread* THREAD = thread;                                    \
  debug_only(VMNativeEntryWrapper __vew;)

// Native method block that transitions current thread to '_thread_in_vm'.
// Note: CompilerThreadCanCallJava must precede JVMCIENV_FROM_JNI so that
// the translation of an uncaught exception in the JVMCIEnv does not make
// a Java call when __is_hotspot == false.
#define C2V_BLOCK(result_type, name, signature)            \
  JVMCI_VM_ENTRY_MARK;                                     \
  ResourceMark rm;                                         \
  bool __is_hotspot = env == thread->jni_environment();    \
  CompilerThreadCanCallJava ccj(thread, __is_hotspot);     \
  JVMCIENV_FROM_JNI(JVMCI::compilation_tick(thread), env); \

// Entry to native method implementation that transitions
// current thread to '_thread_in_vm'.
#define C2V_VMENTRY(result_type, name, signature)        \
  JNIEXPORT result_type JNICALL c2v_ ## name signature { \
  JavaThread* thread = JavaThread::current_or_null();    \
  if (thread == nullptr) {                               \
    env->ThrowNew(JNIJVMCI::InternalError::clazz(),      \
        err_msg("Cannot call into HotSpot from JVMCI shared library without attaching current thread")); \
    return;                                              \
  }                                                      \
  C2V_BLOCK(result_type, name, signature)                \
  JVMCITraceMark jtm("CompilerToVM::" #name);

#define C2V_VMENTRY_(result_type, name, signature, result) \
  JNIEXPORT result_type JNICALL c2v_ ## name signature { \
  JavaThread* thread = JavaThread::current_or_null();    \
  if (thread == nullptr) {                               \
    env->ThrowNew(JNIJVMCI::InternalError::clazz(),      \
        err_msg("Cannot call into HotSpot from JVMCI shared library without attaching current thread")); \
    return result;                                       \
  }                                                      \
  C2V_BLOCK(result_type, name, signature)                \
  JVMCITraceMark jtm("CompilerToVM::" #name);

#define C2V_VMENTRY_NULL(result_type, name, signature) C2V_VMENTRY_(result_type, name, signature, nullptr)
#define C2V_VMENTRY_0(result_type, name, signature) C2V_VMENTRY_(result_type, name, signature, 0)

// Entry to native method implementation that does not transition
// current thread to '_thread_in_vm'.
#define C2V_VMENTRY_PREFIX(result_type, name, signature) \
  JNIEXPORT result_type JNICALL c2v_ ## name signature { \
  JavaThread* thread = JavaThread::current_or_null();

#define C2V_END }

#define JNI_THROW(caller, name, msg) do {                                         \
    jint __throw_res = env->ThrowNew(JNIJVMCI::name::clazz(), msg);               \
    if (__throw_res != JNI_OK) {                                                  \
      JVMCI_event_1("Throwing " #name " in " caller " returned %d", __throw_res); \
    }                                                                             \
    return;                                                                       \
  } while (0);

#define JNI_THROW_(caller, name, msg, result) do {                                \
    jint __throw_res = env->ThrowNew(JNIJVMCI::name::clazz(), msg);               \
    if (__throw_res != JNI_OK) {                                                  \
      JVMCI_event_1("Throwing " #name " in " caller " returned %d", __throw_res); \
    }                                                                             \
    return result;                                                                \
  } while (0)

jobjectArray readConfiguration0(JNIEnv *env, JVMCI_TRAPS);

C2V_VMENTRY_NULL(jobjectArray, readConfiguration, (JNIEnv* env))
  jobjectArray config = readConfiguration0(env, JVMCI_CHECK_NULL);
  return config;
}

C2V_VMENTRY_NULL(jobject, getFlagValue, (JNIEnv* env, jobject c2vm, jobject name_handle))
#define RETURN_BOXED_LONG(value) jvalue p; p.j = (jlong) (value); JVMCIObject box = JVMCIENV->create_box(T_LONG, &p, JVMCI_CHECK_NULL); return box.as_jobject();
#define RETURN_BOXED_DOUBLE(value) jvalue p; p.d = (jdouble) (value); JVMCIObject box = JVMCIENV->create_box(T_DOUBLE, &p, JVMCI_CHECK_NULL); return box.as_jobject();
  JVMCIObject name = JVMCIENV->wrap(name_handle);
  if (name.is_null()) {
    JVMCI_THROW_NULL(NullPointerException);
  }
  const char* cstring = JVMCIENV->as_utf8_string(name);
  const JVMFlag* flag = JVMFlag::find_declared_flag(cstring);
  if (flag == nullptr) {
    return c2vm;
  }
  if (flag->is_bool()) {
    jvalue prim;
    prim.z = flag->get_bool();
    JVMCIObject box = JVMCIENV->create_box(T_BOOLEAN, &prim, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobject(box);
  } else if (flag->is_ccstr()) {
    JVMCIObject value = JVMCIENV->create_string(flag->get_ccstr(), JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobject(value);
  } else if (flag->is_intx()) {
    RETURN_BOXED_LONG(flag->get_intx());
  } else if (flag->is_int()) {
    RETURN_BOXED_LONG(flag->get_int());
  } else if (flag->is_uint()) {
    RETURN_BOXED_LONG(flag->get_uint());
  } else if (flag->is_uint64_t()) {
    RETURN_BOXED_LONG(flag->get_uint64_t());
  } else if (flag->is_size_t()) {
    RETURN_BOXED_LONG(flag->get_size_t());
  } else if (flag->is_uintx()) {
    RETURN_BOXED_LONG(flag->get_uintx());
  } else if (flag->is_double()) {
    RETURN_BOXED_DOUBLE(flag->get_double());
  } else {
    JVMCI_ERROR_NULL("VM flag %s has unsupported type %s", flag->name(), flag->type_string());
  }
#undef RETURN_BOXED_LONG
#undef RETURN_BOXED_DOUBLE
C2V_END

// Macros for argument pairs representing a wrapper object and its wrapped VM pointer
#define ARGUMENT_PAIR(name) jobject name ## _obj, jlong name ## _pointer
#define UNPACK_PAIR(type, name) ((type*) name ## _pointer)

C2V_VMENTRY_NULL(jbyteArray, getBytecode, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));

  int code_size = method->code_size();
  jbyte* reconstituted_code = NEW_RESOURCE_ARRAY(jbyte, code_size);

  guarantee(method->method_holder()->is_rewritten(), "Method's holder should be rewritten");
  // iterate over all bytecodes and replace non-Java bytecodes

  for (BytecodeStream s(method); s.next() != Bytecodes::_illegal; ) {
    Bytecodes::Code code = s.code();
    Bytecodes::Code raw_code = s.raw_code();
    int bci = s.bci();
    int len = s.instruction_size();

    // Restore original byte code.
    reconstituted_code[bci] =  (jbyte) (s.is_wide()? Bytecodes::_wide : code);
    if (len > 1) {
      memcpy(reconstituted_code + (bci + 1), s.bcp()+1, len-1);
    }

    if (len > 1) {
      // Restore the big-endian constant pool indexes.
      // Cf. Rewriter::scan_method
      switch (code) {
        case Bytecodes::_getstatic:
        case Bytecodes::_putstatic:
        case Bytecodes::_getfield:
        case Bytecodes::_putfield:
        case Bytecodes::_invokevirtual:
        case Bytecodes::_invokespecial:
        case Bytecodes::_invokestatic:
        case Bytecodes::_invokeinterface:
        case Bytecodes::_invokehandle: {
          int cp_index = Bytes::get_native_u2((address) reconstituted_code + (bci + 1));
          Bytes::put_Java_u2((address) reconstituted_code + (bci + 1), (u2) cp_index);
          break;
        }

        case Bytecodes::_invokedynamic: {
          int cp_index = Bytes::get_native_u4((address) reconstituted_code + (bci + 1));
          Bytes::put_Java_u4((address) reconstituted_code + (bci + 1), (u4) cp_index);
          break;
        }

        default:
          break;
      }

      // Not all ldc byte code are rewritten.
      switch (raw_code) {
        case Bytecodes::_fast_aldc: {
          int cpc_index = reconstituted_code[bci + 1] & 0xff;
          int cp_index = method->constants()->object_to_cp_index(cpc_index);
          assert(cp_index < method->constants()->length(), "sanity check");
          reconstituted_code[bci + 1] = (jbyte) cp_index;
          break;
        }

        case Bytecodes::_fast_aldc_w: {
          int cpc_index = Bytes::get_native_u2((address) reconstituted_code + (bci + 1));
          int cp_index = method->constants()->object_to_cp_index(cpc_index);
          assert(cp_index < method->constants()->length(), "sanity check");
          Bytes::put_Java_u2((address) reconstituted_code + (bci + 1), (u2) cp_index);
          break;
        }

        default:
          break;
      }
    }
  }

  JVMCIPrimitiveArray result = JVMCIENV->new_byteArray(code_size, JVMCI_CHECK_NULL);
  JVMCIENV->copy_bytes_from(reconstituted_code, result, 0, code_size);
  return JVMCIENV->get_jbyteArray(result);
C2V_END

C2V_VMENTRY_0(jint, getExceptionTableLength, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  Method* method = UNPACK_PAIR(Method, method);
  return method->exception_table_length();
C2V_END

C2V_VMENTRY_0(jlong, getExceptionTableStart, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  Method* method = UNPACK_PAIR(Method, method);
  if (method->exception_table_length() == 0) {
    return 0L;
  }
  return (jlong) (address) method->exception_table_start();
C2V_END

C2V_VMENTRY_NULL(jobject, asResolvedJavaMethod, (JNIEnv* env, jobject, jobject executable_handle))
  requireInHotSpot("asResolvedJavaMethod", JVMCI_CHECK_NULL);
  oop executable = JNIHandles::resolve(executable_handle);
  oop mirror = nullptr;
  int slot = 0;

  if (executable->klass() == vmClasses::reflect_Constructor_klass()) {
    mirror = java_lang_reflect_Constructor::clazz(executable);
    slot = java_lang_reflect_Constructor::slot(executable);
  } else {
    assert(executable->klass() == vmClasses::reflect_Method_klass(), "wrong type");
    mirror = java_lang_reflect_Method::clazz(executable);
    slot = java_lang_reflect_Method::slot(executable);
  }
  Klass* holder = java_lang_Class::as_Klass(mirror);
  methodHandle method (THREAD, InstanceKlass::cast(holder)->method_with_idnum(slot));
  JVMCIObject result = JVMCIENV->get_jvmci_method(method, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
}

C2V_VMENTRY_NULL(jobject, getResolvedJavaMethod, (JNIEnv* env, jobject, jobject base, jlong offset))
  Method* method = nullptr;
  JVMCIObject base_object = JVMCIENV->wrap(base);
  if (base_object.is_null()) {
    method = *((Method**)(offset));
  } else {
    Handle obj = JVMCIENV->asConstant(base_object, JVMCI_CHECK_NULL);
    if (obj->is_a(vmClasses::ResolvedMethodName_klass())) {
      method = (Method*) (intptr_t) obj->long_field(offset);
    } else {
      JVMCI_THROW_MSG_NULL(IllegalArgumentException, err_msg("Unexpected type: %s", obj->klass()->external_name()));
    }
  }
  if (method == nullptr) {
    JVMCI_THROW_MSG_NULL(IllegalArgumentException, err_msg("Unexpected type: %s", JVMCIENV->klass_name(base_object)));
  }
  assert (method->is_method(), "invalid read");
  JVMCIObject result = JVMCIENV->get_jvmci_method(methodHandle(THREAD, method), JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
}

C2V_VMENTRY_NULL(jobject, getConstantPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass_or_method), jboolean is_klass))
  ConstantPool* cp = nullptr;
  if (UNPACK_PAIR(address, klass_or_method) == 0) {
    JVMCI_THROW_NULL(NullPointerException);
  }
  if (!is_klass) {
    cp = (UNPACK_PAIR(Method, klass_or_method))->constMethod()->constants();
  } else {
    cp = InstanceKlass::cast(UNPACK_PAIR(Klass, klass_or_method))->constants();
  }

  JVMCIObject result = JVMCIENV->get_jvmci_constant_pool(constantPoolHandle(THREAD, cp), JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
}

C2V_VMENTRY_NULL(jobject, getResolvedJavaType0, (JNIEnv* env, jobject, jobject base, jlong offset, jboolean compressed))
  JVMCIObject base_object = JVMCIENV->wrap(base);
  if (base_object.is_null()) {
    JVMCI_THROW_MSG_NULL(NullPointerException, "base object is null");
  }

  const char* base_desc = nullptr;
  JVMCIKlassHandle klass(THREAD);
  if (offset == oopDesc::klass_offset_in_bytes()) {
    if (JVMCIENV->isa_HotSpotObjectConstantImpl(base_object)) {
      Handle base_oop = JVMCIENV->asConstant(base_object, JVMCI_CHECK_NULL);
      klass = base_oop->klass();
    } else {
      goto unexpected;
    }
  } else if (!compressed) {
    if (JVMCIENV->isa_HotSpotConstantPool(base_object)) {
      ConstantPool* cp = JVMCIENV->asConstantPool(base_object);
      if (offset == in_bytes(ConstantPool::pool_holder_offset())) {
        klass = cp->pool_holder();
      } else {
        base_desc = FormatBufferResource("[constant pool for %s]", cp->pool_holder()->signature_name());
        goto unexpected;
      }
    } else if (JVMCIENV->isa_HotSpotResolvedObjectTypeImpl(base_object)) {
      Klass* base_klass = JVMCIENV->asKlass(base_object);
      if (offset == in_bytes(Klass::subklass_offset())) {
        klass = base_klass->subklass();
      } else if (offset == in_bytes(Klass::super_offset())) {
        klass = base_klass->super();
      } else if (offset == in_bytes(Klass::next_sibling_offset())) {
        klass = base_klass->next_sibling();
      } else if (offset == in_bytes(ObjArrayKlass::element_klass_offset()) && base_klass->is_objArray_klass()) {
        klass = ObjArrayKlass::cast(base_klass)->element_klass();
      } else if (offset >= in_bytes(Klass::primary_supers_offset()) &&
                 offset < in_bytes(Klass::primary_supers_offset()) + (int) (sizeof(Klass*) * Klass::primary_super_limit()) &&
                 offset % sizeof(Klass*) == 0) {
        // Offset is within the primary supers array
        int index = (int) ((offset - in_bytes(Klass::primary_supers_offset())) / sizeof(Klass*));
        klass = base_klass->primary_super_of_depth(index);
      } else {
        base_desc = FormatBufferResource("[%s]", base_klass->signature_name());
        goto unexpected;
      }
    } else if (JVMCIENV->isa_HotSpotObjectConstantImpl(base_object)) {
      Handle base_oop = JVMCIENV->asConstant(base_object, JVMCI_CHECK_NULL);
      if (base_oop->is_a(vmClasses::Class_klass())) {
        if (offset == java_lang_Class::klass_offset()) {
          klass = java_lang_Class::as_Klass(base_oop());
        } else if (offset == java_lang_Class::array_klass_offset()) {
          klass = java_lang_Class::array_klass_acquire(base_oop());
        } else {
          base_desc = FormatBufferResource("[Class=%s]", java_lang_Class::as_Klass(base_oop())->signature_name());
          goto unexpected;
        }
      } else {
        if (!base_oop.is_null()) {
          base_desc = FormatBufferResource("[%s]", base_oop()->klass()->signature_name());
        }
        goto unexpected;
      }
    } else if (JVMCIENV->isa_HotSpotMethodData(base_object)) {
      jlong base_address = (intptr_t) JVMCIENV->asMethodData(base_object);
      klass = *((Klass**) (intptr_t) (base_address + offset));
      if (klass == nullptr || !klass->is_loader_alive()) {
        // Klasses in methodData might be concurrently unloading so return null in that case.
        return nullptr;
      }
    } else {
      goto unexpected;
    }
  } else {
    goto unexpected;
  }

  {
    if (klass == nullptr) {
      return nullptr;
    }
    JVMCIObject result = JVMCIENV->get_jvmci_type(klass, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobject(result);
  }

unexpected:
  JVMCI_THROW_MSG_NULL(IllegalArgumentException,
                       err_msg("Unexpected arguments: %s%s " JLONG_FORMAT " %s",
                               JVMCIENV->klass_name(base_object), base_desc == nullptr ? "" : base_desc,
                               offset, compressed ? "true" : "false"));
}

C2V_VMENTRY_NULL(jobject, findUniqueConcreteMethod, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass), ARGUMENT_PAIR(method)))
  methodHandle method (THREAD, UNPACK_PAIR(Method, method));
  InstanceKlass* holder = InstanceKlass::cast(UNPACK_PAIR(Klass, klass));
  if (holder->is_interface()) {
    JVMCI_THROW_MSG_NULL(InternalError, err_msg("Interface %s should be handled in Java code", holder->external_name()));
  }
  if (method->can_be_statically_bound()) {
    JVMCI_THROW_MSG_NULL(InternalError, err_msg("Effectively static method %s.%s should be handled in Java code", method->method_holder()->external_name(), method->external_name()));
  }

  methodHandle ucm;
  {
    MutexLocker locker(Compile_lock);
    ucm = methodHandle(THREAD, Dependencies::find_unique_concrete_method(holder, method()));
  }
  JVMCIObject result = JVMCIENV->get_jvmci_method(ucm, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, getImplementor, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (!klass->is_interface()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
        err_msg("Expected interface type, got %s", klass->external_name()));
  }
  InstanceKlass* iklass = InstanceKlass::cast(klass);
  JVMCIKlassHandle handle(THREAD, iklass->implementor());
  JVMCIObject implementor = JVMCIENV->get_jvmci_type(handle, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(implementor);
C2V_END

C2V_VMENTRY_0(jboolean, methodIsIgnoredBySecurityStackWalk,(JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  Method* method = UNPACK_PAIR(Method, method);
  return method->is_ignored_by_security_stack_walk();
C2V_END

C2V_VMENTRY_0(jboolean, isCompilable,(JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  Method* method = UNPACK_PAIR(Method, method);
  // Skip redefined methods
  if (method->is_old()) {
    return false;
  }
  return !method->is_not_compilable(CompLevel_full_optimization);
C2V_END

C2V_VMENTRY_0(jboolean, hasNeverInlineDirective,(JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  methodHandle method (THREAD, UNPACK_PAIR(Method, method));
  return !Inline || CompilerOracle::should_not_inline(method) || method->dont_inline();
C2V_END

C2V_VMENTRY_0(jboolean, shouldInlineMethod,(JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  methodHandle method (THREAD, UNPACK_PAIR(Method, method));
  return CompilerOracle::should_inline(method) || method->force_inline();
C2V_END

C2V_VMENTRY_NULL(jobject, lookupType, (JNIEnv* env, jobject, jstring jname, ARGUMENT_PAIR(accessing_klass), jint accessing_klass_loader, jboolean resolve))
  CompilerThreadCanCallJava canCallJava(thread, resolve); // Resolution requires Java calls
  JVMCIObject name = JVMCIENV->wrap(jname);
  const char* str = JVMCIENV->as_utf8_string(name);
  TempNewSymbol class_name = SymbolTable::new_symbol(str);

  if (class_name->utf8_length() <= 1) {
    JVMCI_THROW_MSG_0(InternalError, err_msg("Primitive type %s should be handled in Java code", str));
  }

#ifdef ASSERT
  const char* val = Arguments::PropertyList_get_value(Arguments::system_properties(), "test.jvmci.lookupTypeException");
  if (val != nullptr) {
    if (strstr(val, "<trace>") != nullptr) {
      tty->print_cr("CompilerToVM.lookupType: %s", str);
    } else if (strstr(str, val) != nullptr) {
      THROW_MSG_0(vmSymbols::java_lang_Exception(),
                  err_msg("lookupTypeException: %s", str));
    }
  }
#endif

  JVMCIKlassHandle resolved_klass(THREAD);
  Klass* accessing_klass = UNPACK_PAIR(Klass, accessing_klass);
  Handle class_loader;
  Handle protection_domain;
  if (accessing_klass != nullptr) {
    class_loader = Handle(THREAD, accessing_klass->class_loader());
    protection_domain = Handle(THREAD, accessing_klass->protection_domain());
  } else {
    switch (accessing_klass_loader) {
      case 0: break; // class_loader is already null, the boot loader
      case 1: class_loader = Handle(THREAD, SystemDictionary::java_platform_loader()); break;
      case 2: class_loader = Handle(THREAD, SystemDictionary::java_system_loader()); break;
      default:
        JVMCI_THROW_MSG_0(InternalError, err_msg("Illegal class loader value: %d", accessing_klass_loader));
    }
    JVMCIENV->runtime()->initialize(JVMCI_CHECK_NULL);
  }

  if (resolve) {
    resolved_klass = SystemDictionary::resolve_or_fail(class_name, class_loader, protection_domain, true, CHECK_NULL);
  } else {
    if (Signature::has_envelope(class_name)) {
      // This is a name from a signature.  Strip off the trimmings.
      // Call recursive to keep scope of strippedsym.
      TempNewSymbol strippedsym = Signature::strip_envelope(class_name);
      resolved_klass = SystemDictionary::find_instance_klass(THREAD, strippedsym,
                                                             class_loader,
                                                             protection_domain);
    } else if (Signature::is_array(class_name)) {
      SignatureStream ss(class_name, false);
      int ndim = ss.skip_array_prefix();
      if (ss.type() == T_OBJECT) {
        Symbol* strippedsym = ss.as_symbol();
        resolved_klass = SystemDictionary::find_instance_klass(THREAD, strippedsym,
                                                               class_loader,
                                                               protection_domain);
        if (!resolved_klass.is_null()) {
          resolved_klass = resolved_klass->array_klass(ndim, CHECK_NULL);
        }
      } else {
        resolved_klass = TypeArrayKlass::cast(Universe::typeArrayKlassObj(ss.type()))->array_klass(ndim, CHECK_NULL);
      }
    } else {
      resolved_klass = SystemDictionary::find_instance_klass(THREAD, class_name,
                                                             class_loader,
                                                             protection_domain);
    }
  }
  JVMCIObject result = JVMCIENV->get_jvmci_type(resolved_klass, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, getArrayType, (JNIEnv* env, jobject, jchar type_char, ARGUMENT_PAIR(klass)))
  JVMCIKlassHandle array_klass(THREAD);
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    BasicType type = JVMCIENV->typeCharToBasicType(type_char, JVMCI_CHECK_0);
    if (type == T_VOID) {
      return nullptr;
    }
    array_klass = Universe::typeArrayKlassObj(type);
    if (array_klass == nullptr) {
      JVMCI_THROW_MSG_NULL(InternalError, err_msg("No array klass for primitive type %s", type2name(type)));
    }
  } else {
    array_klass = klass->array_klass(CHECK_NULL);
  }
  JVMCIObject result = JVMCIENV->get_jvmci_type(array_klass, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, lookupClass, (JNIEnv* env, jobject, jclass mirror))
  requireInHotSpot("lookupClass", JVMCI_CHECK_NULL);
  if (mirror == nullptr) {
    return nullptr;
  }
  JVMCIKlassHandle klass(THREAD);
  klass = java_lang_Class::as_Klass(JNIHandles::resolve(mirror));
  if (klass == nullptr) {
    JVMCI_THROW_MSG_NULL(IllegalArgumentException, "Primitive classes are unsupported");
  }
  JVMCIObject result = JVMCIENV->get_jvmci_type(klass, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, lookupJClass, (JNIEnv* env, jobject, jlong jclass_value))
    if (jclass_value == 0L) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, "jclass must not be zero");
    }
    jclass mirror = reinterpret_cast<jclass>(jclass_value);
    // Since the jclass_value is passed as a jlong, we perform additional checks to prevent the caller from accidentally
    // sending a value that is not a JNI handle.
    if (JNIHandles::handle_type(thread, mirror) == JNIInvalidRefType) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, "jclass is not a valid JNI reference");
    }
    oop obj = JNIHandles::resolve(mirror);
    if (!java_lang_Class::is_instance(obj)) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, "jclass must be a reference to the Class object");
    }
    JVMCIKlassHandle klass(THREAD, java_lang_Class::as_Klass(obj));
    JVMCIObject result = JVMCIENV->get_jvmci_type(klass, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, getUncachedStringInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  constantTag tag = cp->tag_at(index);
  if (!tag.is_string()) {
    JVMCI_THROW_MSG_NULL(IllegalArgumentException, err_msg("Unexpected constant pool tag at index %d: %d", index, tag.value()));
  }
  oop obj = cp->uncached_string_at(index, CHECK_NULL);
  return JVMCIENV->get_jobject(JVMCIENV->get_object_constant(obj));
C2V_END

C2V_VMENTRY_NULL(jobject, lookupConstantInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint cp_index, bool resolve))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  oop obj;
  if (!resolve) {
    bool found_it;
    obj = cp->find_cached_constant_at(cp_index, found_it, CHECK_NULL);
    if (!found_it) {
      return nullptr;
    }
  } else {
    obj = cp->resolve_possibly_cached_constant_at(cp_index, CHECK_NULL);
  }
  constantTag tag = cp->tag_at(cp_index);
  if (tag.is_dynamic_constant()) {
    if (obj == nullptr) {
      return JVMCIENV->get_jobject(JVMCIENV->get_JavaConstant_NULL_POINTER());
    }
    BasicType bt = Signature::basic_type(cp->uncached_signature_ref_at(cp_index));
    if (!is_reference_type(bt)) {
      if (!is_java_primitive(bt)) {
        return JVMCIENV->get_jobject(JVMCIENV->get_JavaConstant_ILLEGAL());
      }

      // Convert standard box (e.g. java.lang.Integer) to JVMCI box (e.g. jdk.vm.ci.meta.PrimitiveConstant)
      jvalue value;
      jlong raw_value;
      jchar type_char;
      BasicType bt2 = java_lang_boxing_object::get_value(obj, &value);
      assert(bt2 == bt, "");
      switch (bt2) {
        case T_LONG:    type_char = 'J'; raw_value = value.j; break;
        case T_DOUBLE:  type_char = 'D'; raw_value = value.j; break;
        case T_FLOAT:   type_char = 'F'; raw_value = value.i; break;
        case T_INT:     type_char = 'I'; raw_value = value.i; break;
        case T_SHORT:   type_char = 'S'; raw_value = value.s; break;
        case T_BYTE:    type_char = 'B'; raw_value = value.b; break;
        case T_CHAR:    type_char = 'C'; raw_value = value.c; break;
        case T_BOOLEAN: type_char = 'Z'; raw_value = value.z; break;
        default:        return JVMCIENV->get_jobject(JVMCIENV->get_JavaConstant_ILLEGAL());
      }

      JVMCIObject result = JVMCIENV->call_JavaConstant_forPrimitive(type_char, raw_value, JVMCI_CHECK_NULL);
      return JVMCIENV->get_jobject(result);
    }
  }
  return JVMCIENV->get_jobject(JVMCIENV->get_object_constant(obj));
C2V_END

C2V_VMENTRY_NULL(jobjectArray, resolveBootstrapMethod, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  constantTag tag = cp->tag_at(index);
  bool is_indy = tag.is_invoke_dynamic();
  bool is_condy = tag.is_dynamic_constant();
  if (!(is_condy || is_indy)) {
    JVMCI_THROW_MSG_0(IllegalArgumentException, err_msg("Unexpected constant pool tag at index %d: %d", index, tag.value()));
  }
  // Get the indy entry based on CP index
  int indy_index = -1;
  if (is_indy) {
    for (int i = 0; i < cp->resolved_indy_entries_length(); i++) {
      if (cp->resolved_indy_entry_at(i)->constant_pool_index() == index) {
        indy_index = i;
      }
    }
  }
  // Resolve the bootstrap specifier, its name, type, and static arguments
  BootstrapInfo bootstrap_specifier(cp, index, indy_index);
  Handle bsm = bootstrap_specifier.resolve_bsm(CHECK_NULL);

  // call java.lang.invoke.MethodHandle::asFixedArity() -> MethodHandle
  // to get a DirectMethodHandle from which we can then extract a Method*
  JavaValue result(T_OBJECT);
  JavaCalls::call_virtual(&result,
                         bsm,
                         vmClasses::MethodHandle_klass(),
                         vmSymbols::asFixedArity_name(),
                         vmSymbols::asFixedArity_signature(),
                         CHECK_NULL);
  bsm = Handle(THREAD, result.get_oop());

  // Check assumption about getting a DirectMethodHandle
  if (!java_lang_invoke_DirectMethodHandle::is_instance(bsm())) {
    JVMCI_THROW_MSG_NULL(InternalError, err_msg("Unexpected MethodHandle subclass: %s", bsm->klass()->external_name()));
  }
  // Create return array describing the bootstrap method invocation (BSMI)
  JVMCIObjectArray bsmi = JVMCIENV->new_Object_array(4, JVMCI_CHECK_NULL);

  // Extract Method* and wrap it in a ResolvedJavaMethod
  Handle member = Handle(THREAD, java_lang_invoke_DirectMethodHandle::member(bsm()));
  JVMCIObject bsmi_method = JVMCIENV->get_jvmci_method(methodHandle(THREAD, java_lang_invoke_MemberName::vmtarget(member())), JVMCI_CHECK_NULL);
  JVMCIENV->put_object_at(bsmi, 0, bsmi_method);

  JVMCIObject bsmi_name = JVMCIENV->create_string(bootstrap_specifier.name(), JVMCI_CHECK_NULL);
  JVMCIENV->put_object_at(bsmi, 1, bsmi_name);

  Handle type_arg = bootstrap_specifier.type_arg();
  JVMCIObject bsmi_type = JVMCIENV->get_object_constant(type_arg());
  JVMCIENV->put_object_at(bsmi, 2, bsmi_type);

  Handle arg_values = bootstrap_specifier.arg_values();
  if (arg_values.not_null()) {
    if (!arg_values->is_array()) {
      JVMCIENV->put_object_at(bsmi, 3, JVMCIENV->get_object_constant(arg_values()));
    } else if (arg_values->is_objArray()) {
      objArrayHandle args_array = objArrayHandle(THREAD, (objArrayOop) arg_values());
      int len = args_array->length();
      JVMCIObjectArray arguments = JVMCIENV->new_JavaConstant_array(len, JVMCI_CHECK_NULL);
      JVMCIENV->put_object_at(bsmi, 3, arguments);
      for (int i = 0; i < len; i++) {
        oop x = args_array->obj_at(i);
        if (x != nullptr) {
          JVMCIENV->put_object_at(arguments, i, JVMCIENV->get_object_constant(x));
        } else {
          JVMCIENV->put_object_at(arguments, i, JVMCIENV->get_JavaConstant_NULL_POINTER());
        }
      }
    } else if (arg_values->is_typeArray()) {
      typeArrayHandle bsci = typeArrayHandle(THREAD, (typeArrayOop) arg_values());
      JVMCIPrimitiveArray arguments = JVMCIENV->new_intArray(bsci->length(), JVMCI_CHECK_NULL);
      JVMCIENV->put_object_at(bsmi, 3, arguments);
      for (int i = 0; i < bsci->length(); i++) {
        JVMCIENV->put_int_at(arguments, i, bsci->int_at(i));
      }
    }
  }
  return JVMCIENV->get_jobjectArray(bsmi);
C2V_END

C2V_VMENTRY_0(jint, bootstrapArgumentIndexAt, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint cpi, jint index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  return cp->bootstrap_argument_index_at(cpi, index);
C2V_END

C2V_VMENTRY_0(jint, lookupNameAndTypeRefIndexInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index, jint opcode))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  return cp->name_and_type_ref_index_at(index, (Bytecodes::Code)opcode);
C2V_END

C2V_VMENTRY_NULL(jobject, lookupNameInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint which, jint opcode))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  JVMCIObject sym = JVMCIENV->create_string(cp->name_ref_at(which, (Bytecodes::Code)opcode), JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(sym);
C2V_END

C2V_VMENTRY_NULL(jobject, lookupSignatureInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint which, jint opcode))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  JVMCIObject sym = JVMCIENV->create_string(cp->signature_ref_at(which, (Bytecodes::Code)opcode), JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(sym);
C2V_END

C2V_VMENTRY_0(jint, lookupKlassRefIndexInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index, jint opcode))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  return cp->klass_ref_index_at(index, (Bytecodes::Code)opcode);
C2V_END

C2V_VMENTRY_NULL(jobject, resolveTypeInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  Klass* klass = cp->klass_at(index, CHECK_NULL);
  JVMCIKlassHandle resolved_klass(THREAD, klass);
  if (resolved_klass->is_instance_klass()) {
    InstanceKlass::cast(resolved_klass())->link_class(CHECK_NULL);
    if (!InstanceKlass::cast(resolved_klass())->is_linked()) {
      // link_class() should not return here if there is an issue.
      JVMCI_THROW_MSG_NULL(InternalError, err_msg("Class %s must be linked", resolved_klass()->external_name()));
    }
  }
  JVMCIObject klassObject = JVMCIENV->get_jvmci_type(resolved_klass, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(klassObject);
C2V_END

C2V_VMENTRY_NULL(jobject, lookupKlassInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  Klass* loading_klass = cp->pool_holder();
  bool is_accessible = false;
  JVMCIKlassHandle klass(THREAD, JVMCIRuntime::get_klass_by_index(cp, index, is_accessible, loading_klass));
  Symbol* symbol = nullptr;
  if (klass.is_null()) {
    constantTag tag = cp->tag_at(index);
    if (tag.is_klass()) {
      // The klass has been inserted into the constant pool
      // very recently.
      klass = cp->resolved_klass_at(index);
    } else if (tag.is_symbol()) {
      symbol = cp->symbol_at(index);
    } else {
      assert(cp->tag_at(index).is_unresolved_klass(), "wrong tag");
      symbol = cp->klass_name_at(index);
    }
  }
  JVMCIObject result;
  if (!klass.is_null()) {
    result = JVMCIENV->get_jvmci_type(klass, JVMCI_CHECK_NULL);
  } else {
    result = JVMCIENV->create_string(symbol, JVMCI_CHECK_NULL);
  }
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, lookupAppendixInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint which))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  oop appendix_oop = ConstantPool::appendix_at_if_loaded(cp, which);
  return JVMCIENV->get_jobject(JVMCIENV->get_object_constant(appendix_oop));
C2V_END

C2V_VMENTRY_NULL(jobject, lookupMethodInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index, jbyte opcode, ARGUMENT_PAIR(caller)))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  methodHandle caller(THREAD, UNPACK_PAIR(Method, caller));
  InstanceKlass* pool_holder = cp->pool_holder();
  Bytecodes::Code bc = (Bytecodes::Code) (((int) opcode) & 0xFF);
  methodHandle method(THREAD, JVMCIRuntime::get_method_by_index(cp, index, bc, pool_holder));
  JFR_ONLY(if (method.not_null()) Jfr::on_resolution(caller(), method(), CHECK_NULL);)
  JVMCIObject result = JVMCIENV->get_jvmci_method(method, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, resolveFieldInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index, ARGUMENT_PAIR(method), jbyte opcode, jintArray info_handle))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  Bytecodes::Code code = (Bytecodes::Code)(((int) opcode) & 0xFF);
  fieldDescriptor fd;
  methodHandle mh(THREAD, UNPACK_PAIR(Method, method));

  Bytecodes::Code bc = (Bytecodes::Code) (((int) opcode) & 0xFF);
  int holder_index = cp->klass_ref_index_at(index, bc);
  if (!cp->tag_at(holder_index).is_klass() && !THREAD->can_call_java()) {
    // If the holder is not resolved in the constant pool and the current
    // thread cannot call Java, return null. This avoids a Java call
    // in LinkInfo to load the holder.
    Symbol* klass_name = cp->klass_ref_at_noresolve(index, bc);
    return nullptr;
  }

  LinkInfo link_info(cp, index, mh, code, CHECK_NULL);
  LinkResolver::resolve_field(fd, link_info, Bytecodes::java_code(code), false, CHECK_NULL);
  JVMCIPrimitiveArray info = JVMCIENV->wrap(info_handle);
  if (info.is_null() || JVMCIENV->get_length(info) != 4) {
    JVMCI_ERROR_NULL("info must not be null and have a length of 4");
  }
  JVMCIENV->put_int_at(info, 0, fd.access_flags().as_int());
  JVMCIENV->put_int_at(info, 1, fd.offset());
  JVMCIENV->put_int_at(info, 2, fd.index());
  JVMCIENV->put_int_at(info, 3, fd.field_flags().as_uint());
  JVMCIKlassHandle handle(THREAD, fd.field_holder());
  JVMCIObject field_holder = JVMCIENV->get_jvmci_type(handle, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(field_holder);
C2V_END

C2V_VMENTRY_0(jint, getVtableIndexForInterfaceMethod, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass), ARGUMENT_PAIR(method)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  InstanceKlass* holder = method->method_holder();
  if (klass->is_interface()) {
    JVMCI_THROW_MSG_0(InternalError, err_msg("Interface %s should be handled in Java code", klass->external_name()));
  }
  if (!holder->is_interface()) {
    JVMCI_THROW_MSG_0(InternalError, err_msg("Method %s is not held by an interface, this case should be handled in Java code", method->name_and_sig_as_C_string()));
  }
  if (!klass->is_instance_klass()) {
    JVMCI_THROW_MSG_0(InternalError, err_msg("Class %s must be instance klass", klass->external_name()));
  }
  if (!InstanceKlass::cast(klass)->is_linked()) {
    JVMCI_THROW_MSG_0(InternalError, err_msg("Class %s must be linked", klass->external_name()));
  }
  if (!klass->is_subtype_of(holder)) {
    JVMCI_THROW_MSG_0(InternalError, err_msg("Class %s does not implement interface %s", klass->external_name(), holder->external_name()));
  }
  return LinkResolver::vtable_index_of_interface_method(klass, method);
C2V_END

C2V_VMENTRY_NULL(jobject, resolveMethod, (JNIEnv* env, jobject, ARGUMENT_PAIR(receiver), ARGUMENT_PAIR(method), ARGUMENT_PAIR(caller)))
  Klass* recv_klass = UNPACK_PAIR(Klass, receiver);
  Klass* caller_klass = UNPACK_PAIR(Klass, caller);
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));

  Klass* resolved     = method->method_holder();
  Symbol* h_name      = method->name();
  Symbol* h_signature = method->signature();

  if (MethodHandles::is_signature_polymorphic_method(method())) {
      // Signature polymorphic methods are already resolved, JVMCI just returns null in this case.
      return nullptr;
  }

  if (method->name() == vmSymbols::clone_name() &&
      resolved == vmClasses::Object_klass() &&
      recv_klass->is_array_klass()) {
    // Resolution of the clone method on arrays always returns Object.clone even though that method
    // has protected access.  There's some trickery in the access checking to make this all work out
    // so it's necessary to pass in the array class as the resolved class to properly trigger this.
    // Otherwise it's impossible to resolve the array clone methods through JVMCI.  See
    // LinkResolver::check_method_accessability for the matching logic.
    resolved = recv_klass;
  }

  LinkInfo link_info(resolved, h_name, h_signature, caller_klass);
  Method* m = nullptr;
  // Only do exact lookup if receiver klass has been linked.  Otherwise,
  // the vtable has not been setup, and the LinkResolver will fail.
  if (recv_klass->is_array_klass() ||
      (InstanceKlass::cast(recv_klass)->is_linked() && !recv_klass->is_interface())) {
    if (resolved->is_interface()) {
      m = LinkResolver::resolve_interface_call_or_null(recv_klass, link_info);
    } else {
      m = LinkResolver::resolve_virtual_call_or_null(recv_klass, link_info);
    }
  }

  if (m == nullptr) {
    // Return null if there was a problem with lookup (uninitialized class, etc.)
    return nullptr;
  }

  JVMCIObject result = JVMCIENV->get_jvmci_method(methodHandle(THREAD, m), JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_0(jboolean, hasFinalizableSubclass,(JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  assert(klass != nullptr, "method must not be called for primitive types");
  if (!klass->is_instance_klass()) {
    return false;
  }
  InstanceKlass* iklass = InstanceKlass::cast(klass);
  return Dependencies::find_finalizable_subclass(iklass) != nullptr;
C2V_END

C2V_VMENTRY_NULL(jobject, getClassInitializer, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (!klass->is_instance_klass()) {
    return nullptr;
  }
  InstanceKlass* iklass = InstanceKlass::cast(klass);
  methodHandle clinit(THREAD, iklass->class_initializer());
  JVMCIObject result = JVMCIENV->get_jvmci_method(clinit, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_0(jlong, getMaxCallTargetOffset, (JNIEnv* env, jobject, jlong addr))
  address target_addr = (address) addr;
  if (target_addr != 0x0) {
    int64_t off_low = (int64_t)target_addr - ((int64_t)CodeCache::low_bound() + sizeof(int));
    int64_t off_high = (int64_t)target_addr - ((int64_t)CodeCache::high_bound() + sizeof(int));
    return MAX2(ABS(off_low), ABS(off_high));
  }
  return -1;
C2V_END

C2V_VMENTRY(void, setNotInlinableOrCompilable,(JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  method->set_is_not_c1_compilable();
  method->set_is_not_c2_compilable();
  method->set_dont_inline(true);
C2V_END

C2V_VMENTRY_0(jint, getInstallCodeFlags, (JNIEnv *env, jobject))
  int flags = 0;
#ifndef PRODUCT
  flags |= 0x0001; // VM will install block comments
  flags |= 0x0004; // Enable HotSpotJVMCIRuntime.Option.CodeSerializationTypeInfo if not explicitly set
#endif
  if (JvmtiExport::can_hotswap_or_post_breakpoint()) {
    // VM needs to track method dependencies
    flags |= 0x0002;
  }
  return flags;
C2V_END

C2V_VMENTRY_0(jint, installCode0, (JNIEnv *env, jobject,
    jlong compiled_code_buffer,
    jlong serialization_ns,
    bool with_type_info,
    jobject compiled_code,
    jobjectArray object_pool,
    jobject installed_code,
    jlong failed_speculations_address,
    jbyteArray speculations_obj))
  HandleMark hm(THREAD);
  JNIHandleMark jni_hm(thread);

  JVMCIObject compiled_code_handle = JVMCIENV->wrap(compiled_code);
  objArrayHandle object_pool_handle(thread, JVMCIENV->is_hotspot() ? (objArrayOop) JNIHandles::resolve(object_pool) : nullptr);

  CodeBlob* cb = nullptr;
  JVMCIObject installed_code_handle = JVMCIENV->wrap(installed_code);
  JVMCIPrimitiveArray speculations_handle = JVMCIENV->wrap(speculations_obj);

  int speculations_len = JVMCIENV->get_length(speculations_handle);
  char* speculations = NEW_RESOURCE_ARRAY(char, speculations_len);
  JVMCIENV->copy_bytes_to(speculations_handle, (jbyte*) speculations, 0, speculations_len);

  JVMCICompiler* compiler = JVMCICompiler::instance(true, CHECK_JNI_ERR);
  JVMCICompiler::CodeInstallStats* stats = compiler->code_install_stats(!thread->is_Compiler_thread());
  elapsedTimer *timer = stats->timer();
  timer->add_nanoseconds(serialization_ns);
  TraceTime install_time("installCode", timer);

  CodeInstaller installer(JVMCIENV);

  JVMCI::CodeInstallResult result = installer.install(compiler,
      compiled_code_buffer,
      with_type_info,
      compiled_code_handle,
      object_pool_handle,
      cb,
      installed_code_handle,
      (FailedSpeculation**)(address) failed_speculations_address,
      speculations,
      speculations_len,
      JVMCI_CHECK_0);

  if (PrintCodeCacheOnCompilation) {
    stringStream s;
    // Dump code cache into a buffer before locking the tty,
    {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      CodeCache::print_summary(&s, false);
    }
    ttyLocker ttyl;
    tty->print_raw_cr(s.freeze());
  }

  if (result != JVMCI::ok) {
    assert(cb == nullptr, "should be");
  } else {
    stats->on_install(cb);
    if (installed_code_handle.is_non_null()) {
      if (cb->is_nmethod()) {
        assert(JVMCIENV->isa_HotSpotNmethod(installed_code_handle), "wrong type");
        // Clear the link to an old nmethod first
        JVMCIObject nmethod_mirror = installed_code_handle;
        JVMCIENV->invalidate_nmethod_mirror(nmethod_mirror, true, JVMCI_CHECK_0);
      } else {
        assert(JVMCIENV->isa_InstalledCode(installed_code_handle), "wrong type");
      }
      // Initialize the link to the new code blob
      JVMCIENV->initialize_installed_code(installed_code_handle, cb, JVMCI_CHECK_0);
    }
  }
  return result;
C2V_END

C2V_VMENTRY(void, resetCompilationStatistics, (JNIEnv* env, jobject))
  JVMCICompiler* compiler = JVMCICompiler::instance(true, CHECK);
  CompilerStatistics* stats = compiler->stats();
  stats->_standard.reset();
  stats->_osr.reset();
C2V_END

C2V_VMENTRY_NULL(jobject, disassembleCodeBlob, (JNIEnv* env, jobject, jobject installedCode))
  HandleMark hm(THREAD);

  if (installedCode == nullptr) {
    JVMCI_THROW_MSG_NULL(NullPointerException, "installedCode is null");
  }

  JVMCIObject installedCodeObject = JVMCIENV->wrap(installedCode);
  CodeBlob* cb = JVMCIENV->get_code_blob(installedCodeObject);
  if (cb == nullptr) {
    return nullptr;
  }

  // We don't want the stringStream buffer to resize during disassembly as it
  // uses scoped resource memory. If a nested function called during disassembly uses
  // a ResourceMark and the buffer expands within the scope of the mark,
  // the buffer becomes garbage when that scope is exited. Experience shows that
  // the disassembled code is typically about 10x the code size so a fixed buffer
  // sized to 20x code size plus a fixed amount for header info should be sufficient.
  int bufferSize = cb->code_size() * 20 + 1024;
  char* buffer = NEW_RESOURCE_ARRAY(char, bufferSize);
  stringStream st(buffer, bufferSize);
  Disassembler::decode(cb, &st);
  if (st.size() <= 0) {
    return nullptr;
  }

  JVMCIObject result = JVMCIENV->create_string(st.as_string(), JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobject, getStackTraceElement, (JNIEnv* env, jobject, ARGUMENT_PAIR(method), int bci))
  HandleMark hm(THREAD);

  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  JVMCIObject element = JVMCIENV->new_StackTraceElement(method, bci, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(element);
C2V_END

C2V_VMENTRY_NULL(jobject, executeHotSpotNmethod, (JNIEnv* env, jobject, jobject args, jobject hs_nmethod))
  // The incoming arguments array would have to contain JavaConstants instead of regular objects
  // and the return value would have to be wrapped as a JavaConstant.
  requireInHotSpot("executeHotSpotNmethod", JVMCI_CHECK_NULL);

  HandleMark hm(THREAD);

  JVMCIObject nmethod_mirror = JVMCIENV->wrap(hs_nmethod);
  nmethod* nm = JVMCIENV->get_nmethod(nmethod_mirror);
  if (nm == nullptr || !nm->is_in_use()) {
    JVMCI_THROW_NULL(InvalidInstalledCodeException);
  }
  methodHandle mh(THREAD, nm->method());
  Symbol* signature = mh->signature();
  JavaCallArguments jca(mh->size_of_parameters());

  JavaArgumentUnboxer jap(signature, &jca, (arrayOop) JNIHandles::resolve(args), mh->is_static());
  JavaValue result(jap.return_type());
  jca.set_alternative_target(Handle(THREAD, JNIHandles::resolve(nmethod_mirror.as_jobject())));
  JavaCalls::call(&result, mh, &jca, CHECK_NULL);

  if (jap.return_type() == T_VOID) {
    return nullptr;
  } else if (is_reference_type(jap.return_type())) {
    return JNIHandles::make_local(THREAD, result.get_oop());
  } else {
    jvalue *value = (jvalue *) result.get_value_addr();
    // Narrow the value down if required (Important on big endian machines)
    switch (jap.return_type()) {
      case T_BOOLEAN:
       value->z = (jboolean) value->i;
       break;
      case T_BYTE:
       value->b = (jbyte) value->i;
       break;
      case T_CHAR:
       value->c = (jchar) value->i;
       break;
      case T_SHORT:
       value->s = (jshort) value->i;
       break;
      default:
        break;
    }
    JVMCIObject o = JVMCIENV->create_box(jap.return_type(), value, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobject(o);
  }
C2V_END

C2V_VMENTRY_NULL(jlongArray, getLineNumberTable, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  Method* method = UNPACK_PAIR(Method, method);
  if (!method->has_linenumber_table()) {
    return nullptr;
  }
  u2 num_entries = 0;
  CompressedLineNumberReadStream streamForSize(method->compressed_linenumber_table());
  while (streamForSize.read_pair()) {
    num_entries++;
  }

  CompressedLineNumberReadStream stream(method->compressed_linenumber_table());
  JVMCIPrimitiveArray result = JVMCIENV->new_longArray(2 * num_entries, JVMCI_CHECK_NULL);

  int i = 0;
  jlong value;
  while (stream.read_pair()) {
    value = ((jlong) stream.bci());
    JVMCIENV->put_long_at(result, i, value);
    value = ((jlong) stream.line());
    JVMCIENV->put_long_at(result, i + 1, value);
    i += 2;
  }

  return (jlongArray) JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_0(jlong, getLocalVariableTableStart, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  Method* method = UNPACK_PAIR(Method, method);
  if (!method->has_localvariable_table()) {
    return 0;
  }
  return (jlong) (address) method->localvariable_table_start();
C2V_END

C2V_VMENTRY_0(jint, getLocalVariableTableLength, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  Method* method = UNPACK_PAIR(Method, method);
  return method->localvariable_table_length();
C2V_END

static MethodData* get_profiling_method_data(const methodHandle& method, TRAPS) {
  MethodData* method_data = method->method_data();
  if (method_data == nullptr) {
    method->build_profiling_method_data(method, CHECK_NULL);
    method_data = method->method_data();
    if (method_data == nullptr) {
      THROW_MSG_NULL(vmSymbols::java_lang_OutOfMemoryError(), "cannot allocate MethodData")
    }
  }
  return method_data;
}

C2V_VMENTRY(void, reprofile, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  MethodCounters* mcs = method->method_counters();
  if (mcs != nullptr) {
    mcs->clear_counters();
  }
  NOT_PRODUCT(method->set_compiled_invocation_count(0));

  CompiledMethod* code = method->code();
  if (code != nullptr) {
    code->make_not_entrant();
  }

  MethodData* method_data = method->method_data();
  if (method_data == nullptr) {
    method_data = get_profiling_method_data(method, CHECK);
  } else {
    method_data->initialize();
  }
C2V_END


C2V_VMENTRY(void, invalidateHotSpotNmethod, (JNIEnv* env, jobject, jobject hs_nmethod, jboolean deoptimize))
  JVMCIObject nmethod_mirror = JVMCIENV->wrap(hs_nmethod);
  JVMCIENV->invalidate_nmethod_mirror(nmethod_mirror, deoptimize, JVMCI_CHECK);
C2V_END

C2V_VMENTRY_NULL(jlongArray, collectCounters, (JNIEnv* env, jobject))
  // Returns a zero length array if counters aren't enabled
  JVMCIPrimitiveArray array = JVMCIENV->new_longArray(JVMCICounterSize, JVMCI_CHECK_NULL);
  if (JVMCICounterSize > 0) {
    jlong* temp_array = NEW_RESOURCE_ARRAY(jlong, JVMCICounterSize);
    JavaThread::collect_counters(temp_array, JVMCICounterSize);
    JVMCIENV->copy_longs_from(temp_array, array, 0, JVMCICounterSize);
  }
  return (jlongArray) JVMCIENV->get_jobject(array);
C2V_END

C2V_VMENTRY_0(jint, getCountersSize, (JNIEnv* env, jobject))
  return (jint) JVMCICounterSize;
C2V_END

C2V_VMENTRY_0(jboolean, setCountersSize, (JNIEnv* env, jobject, jint new_size))
  return JavaThread::resize_all_jvmci_counters(new_size);
C2V_END

C2V_VMENTRY_0(jint, allocateCompileId, (JNIEnv* env, jobject, ARGUMENT_PAIR(method), int entry_bci))
  HandleMark hm(THREAD);
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  if (method.is_null()) {
    JVMCI_THROW_0(NullPointerException);
  }
  if (entry_bci >= method->code_size() || entry_bci < -1) {
    JVMCI_THROW_MSG_0(IllegalArgumentException, err_msg("Unexpected bci %d", entry_bci));
  }
  return CompileBroker::assign_compile_id_unlocked(THREAD, method, entry_bci);
C2V_END


C2V_VMENTRY_0(jboolean, isMature, (JNIEnv* env, jobject, jlong method_data_pointer))
  MethodData* mdo = (MethodData*) method_data_pointer;
  return mdo != nullptr && mdo->is_mature();
C2V_END

C2V_VMENTRY_0(jboolean, hasCompiledCodeForOSR, (JNIEnv* env, jobject, ARGUMENT_PAIR(method), int entry_bci, int comp_level))
  Method* method = UNPACK_PAIR(Method, method);
  return method->lookup_osr_nmethod_for(entry_bci, comp_level, true) != nullptr;
C2V_END

C2V_VMENTRY_NULL(jobject, getSymbol, (JNIEnv* env, jobject, jlong symbol))
  JVMCIObject sym = JVMCIENV->create_string((Symbol*)(address)symbol, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(sym);
C2V_END

C2V_VMENTRY_NULL(jobject, getSignatureName, (JNIEnv* env, jobject, jlong klass_pointer))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  JVMCIObject signature = JVMCIENV->create_string(klass->signature_name(), JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(signature);
C2V_END

/*
 * Used by matches() to convert a ResolvedJavaMethod[] to an array of Method*.
 */
GrowableArray<Method*>* init_resolved_methods(jobjectArray methods, JVMCIEnv* JVMCIENV) {
  objArrayOop methods_oop = (objArrayOop) JNIHandles::resolve(methods);
  GrowableArray<Method*>* resolved_methods = new GrowableArray<Method*>(methods_oop->length());
  for (int i = 0; i < methods_oop->length(); i++) {
    oop resolved = methods_oop->obj_at(i);
    Method* resolved_method = nullptr;
    if (resolved->klass() == HotSpotJVMCI::HotSpotResolvedJavaMethodImpl::klass()) {
      resolved_method = HotSpotJVMCI::asMethod(JVMCIENV, resolved);
    }
    resolved_methods->append(resolved_method);
  }
  return resolved_methods;
}

/*
 * Used by c2v_iterateFrames to check if `method` matches one of the ResolvedJavaMethods in the `methods` array.
 * The ResolvedJavaMethod[] array is converted to a Method* array that is then cached in the resolved_methods_ref in/out parameter.
 * In case of a match, the matching ResolvedJavaMethod is returned in matched_jvmci_method_ref.
 */
bool matches(jobjectArray methods, Method* method, GrowableArray<Method*>** resolved_methods_ref, Handle* matched_jvmci_method_ref, Thread* THREAD, JVMCIEnv* JVMCIENV) {
  GrowableArray<Method*>* resolved_methods = *resolved_methods_ref;
  if (resolved_methods == nullptr) {
    resolved_methods = init_resolved_methods(methods, JVMCIENV);
    *resolved_methods_ref = resolved_methods;
  }
  assert(method != nullptr, "method should not be null");
  assert(resolved_methods->length() == ((objArrayOop) JNIHandles::resolve(methods))->length(), "arrays must have the same length");
  for (int i = 0; i < resolved_methods->length(); i++) {
    Method* m = resolved_methods->at(i);
    if (m == method) {
      *matched_jvmci_method_ref = Handle(THREAD, ((objArrayOop) JNIHandles::resolve(methods))->obj_at(i));
      return true;
    }
  }
  return false;
}

/*
 * Resolves an interface call to a concrete method handle.
 */
methodHandle resolve_interface_call(Klass* spec_klass, Symbol* name, Symbol* signature, JavaCallArguments* args, TRAPS) {
  CallInfo callinfo;
  Handle receiver = args->receiver();
  Klass* recvrKlass = receiver.is_null() ? (Klass*)nullptr : receiver->klass();
  LinkInfo link_info(spec_klass, name, signature);
  LinkResolver::resolve_interface_call(
          callinfo, receiver, recvrKlass, link_info, true, CHECK_(methodHandle()));
  methodHandle method(THREAD, callinfo.selected_method());
  assert(method.not_null(), "should have thrown exception");
  return method;
}

/*
 * Used by c2v_iterateFrames to make a new vframeStream at the given compiled frame id (stack pointer) and vframe id.
 */
void resync_vframestream_to_compiled_frame(vframeStream& vfst, intptr_t* stack_pointer, int vframe_id, JavaThread* thread, TRAPS) {
  vfst = vframeStream(thread);
  while (vfst.frame_id() != stack_pointer && !vfst.at_end()) {
    vfst.next();
  }
  if (vfst.frame_id() != stack_pointer) {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "stack frame not found after deopt")
  }
  if (vfst.is_interpreted_frame()) {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "compiled stack frame expected")
  }
  while (vfst.vframe_id() != vframe_id) {
    if (vfst.at_end()) {
      THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "vframe not found after deopt")
    }
    vfst.next();
    assert(!vfst.is_interpreted_frame(), "Wrong frame type");
  }
}

/*
 * Used by c2v_iterateFrames. Returns an array of any unallocated scope objects or null if none.
 */
GrowableArray<ScopeValue*>* get_unallocated_objects_or_null(GrowableArray<ScopeValue*>* scope_objects) {
  GrowableArray<ScopeValue*>* unallocated = nullptr;
  for (int i = 0; i < scope_objects->length(); i++) {
    ObjectValue* sv = (ObjectValue*) scope_objects->at(i);
    if (sv->value().is_null()) {
      if (unallocated == nullptr) {
        unallocated = new GrowableArray<ScopeValue*>(scope_objects->length());
      }
      unallocated->append(sv);
    }
  }
  return unallocated;
}

C2V_VMENTRY_NULL(jobject, iterateFrames, (JNIEnv* env, jobject compilerToVM, jobjectArray initial_methods, jobjectArray match_methods, jint initialSkip, jobject visitor_handle))

  if (!thread->has_last_Java_frame()) {
    return nullptr;
  }
  Handle visitor(THREAD, JNIHandles::resolve_non_null(visitor_handle));

  requireInHotSpot("iterateFrames", JVMCI_CHECK_NULL);

  HotSpotJVMCI::HotSpotStackFrameReference::klass()->initialize(CHECK_NULL);

  vframeStream vfst(thread);
  jobjectArray methods = initial_methods;
  methodHandle visitor_method;
  GrowableArray<Method*>* resolved_methods = nullptr;

  while (!vfst.at_end()) { // frame loop
    bool realloc_called = false;
    intptr_t* frame_id = vfst.frame_id();

    // Previous compiledVFrame of this frame; use with at_scope() to reuse scope object pool.
    compiledVFrame* prev_cvf = nullptr;

    for (; !vfst.at_end() && vfst.frame_id() == frame_id; vfst.next()) { // vframe loop
      int frame_number = 0;
      Method *method = vfst.method();
      int bci = vfst.bci();

      Handle matched_jvmci_method;
      if (methods == nullptr || matches(methods, method, &resolved_methods, &matched_jvmci_method, THREAD, JVMCIENV)) {
        if (initialSkip > 0) {
          initialSkip--;
          continue;
        }
        javaVFrame* vf;
        if (prev_cvf != nullptr && prev_cvf->frame_pointer()->id() == frame_id) {
          assert(prev_cvf->is_compiled_frame(), "expected compiled Java frame");
          vf = prev_cvf->at_scope(vfst.decode_offset(), vfst.vframe_id());
        } else {
          vf = vfst.asJavaVFrame();
        }

        StackValueCollection* locals = nullptr;
        typeArrayHandle localIsVirtual_h;
        if (vf->is_compiled_frame()) {
          // compiled method frame
          compiledVFrame* cvf = compiledVFrame::cast(vf);

          ScopeDesc* scope = cvf->scope();
          // native wrappers do not have a scope
          if (scope != nullptr && scope->objects() != nullptr) {
            prev_cvf = cvf;

            GrowableArray<ScopeValue*>* objects = nullptr;
            if (!realloc_called) {
              objects = scope->objects();
            } else {
              // some object might already have been re-allocated, only reallocate the non-allocated ones
              objects = get_unallocated_objects_or_null(scope->objects());
            }

            if (objects != nullptr) {
              RegisterMap reg_map(vf->register_map());
              bool realloc_failures = Deoptimization::realloc_objects(thread, vf->frame_pointer(), &reg_map, objects, CHECK_NULL);
              Deoptimization::reassign_fields(vf->frame_pointer(), &reg_map, objects, realloc_failures, false);
              realloc_called = true;
            }

            GrowableArray<ScopeValue*>* local_values = scope->locals();
            for (int i = 0; i < local_values->length(); i++) {
              ScopeValue* value = local_values->at(i);
              assert(!value->is_object_merge(), "Should not be.");
              if (value->is_object()) {
                if (localIsVirtual_h.is_null()) {
                  typeArrayOop array_oop = oopFactory::new_boolArray(local_values->length(), CHECK_NULL);
                  localIsVirtual_h = typeArrayHandle(THREAD, array_oop);
                }
                localIsVirtual_h->bool_at_put(i, true);
              }
            }
          }

          locals = cvf->locals();
          frame_number = cvf->vframe_id();
        } else {
          // interpreted method frame
          interpretedVFrame* ivf = interpretedVFrame::cast(vf);

          locals = ivf->locals();
        }
        assert(bci == vf->bci(), "wrong bci");
        assert(method == vf->method(), "wrong method");

        Handle frame_reference = HotSpotJVMCI::HotSpotStackFrameReference::klass()->allocate_instance_handle(CHECK_NULL);
        HotSpotJVMCI::HotSpotStackFrameReference::set_bci(JVMCIENV, frame_reference(), bci);
        if (matched_jvmci_method.is_null()) {
          methodHandle mh(THREAD, method);
          JVMCIObject jvmci_method = JVMCIENV->get_jvmci_method(mh, JVMCI_CHECK_NULL);
          matched_jvmci_method = Handle(THREAD, JNIHandles::resolve(jvmci_method.as_jobject()));
        }
        HotSpotJVMCI::HotSpotStackFrameReference::set_method(JVMCIENV, frame_reference(), matched_jvmci_method());
        HotSpotJVMCI::HotSpotStackFrameReference::set_localIsVirtual(JVMCIENV, frame_reference(), localIsVirtual_h());

        HotSpotJVMCI::HotSpotStackFrameReference::set_compilerToVM(JVMCIENV, frame_reference(), JNIHandles::resolve(compilerToVM));
        HotSpotJVMCI::HotSpotStackFrameReference::set_stackPointer(JVMCIENV, frame_reference(), (jlong) frame_id);
        HotSpotJVMCI::HotSpotStackFrameReference::set_frameNumber(JVMCIENV, frame_reference(), frame_number);

        // initialize the locals array
        objArrayOop array_oop = oopFactory::new_objectArray(locals->size(), CHECK_NULL);
        objArrayHandle array(THREAD, array_oop);
        for (int i = 0; i < locals->size(); i++) {
          StackValue* var = locals->at(i);
          if (var->type() == T_OBJECT) {
            array->obj_at_put(i, locals->at(i)->get_obj()());
          }
        }
        HotSpotJVMCI::HotSpotStackFrameReference::set_locals(JVMCIENV, frame_reference(), array());
        HotSpotJVMCI::HotSpotStackFrameReference::set_objectsMaterialized(JVMCIENV, frame_reference(), JNI_FALSE);

        JavaValue result(T_OBJECT);
        JavaCallArguments args(visitor);
        if (visitor_method.is_null()) {
          visitor_method = resolve_interface_call(HotSpotJVMCI::InspectedFrameVisitor::klass(), vmSymbols::visitFrame_name(), vmSymbols::visitFrame_signature(), &args, CHECK_NULL);
        }

        args.push_oop(frame_reference);
        JavaCalls::call(&result, visitor_method, &args, CHECK_NULL);
        if (result.get_oop() != nullptr) {
          return JNIHandles::make_local(thread, result.get_oop());
        }
        if (methods == initial_methods) {
          methods = match_methods;
          if (resolved_methods != nullptr && JNIHandles::resolve(match_methods) != JNIHandles::resolve(initial_methods)) {
            resolved_methods = nullptr;
          }
        }
        assert(initialSkip == 0, "There should be no match before initialSkip == 0");
        if (HotSpotJVMCI::HotSpotStackFrameReference::objectsMaterialized(JVMCIENV, frame_reference()) == JNI_TRUE) {
          // the frame has been deoptimized, we need to re-synchronize the frame and vframe
          prev_cvf = nullptr;
          intptr_t* stack_pointer = (intptr_t*) HotSpotJVMCI::HotSpotStackFrameReference::stackPointer(JVMCIENV, frame_reference());
          resync_vframestream_to_compiled_frame(vfst, stack_pointer, frame_number, thread, CHECK_NULL);
        }
      }
    } // end of vframe loop
  } // end of frame loop

  // the end was reached without finding a matching method
  return nullptr;
C2V_END

C2V_VMENTRY_0(int, decodeIndyIndexToCPIndex, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint encoded_indy_index, jboolean resolve))
  if (!ConstantPool::is_invokedynamic_index(encoded_indy_index)) {
    JVMCI_THROW_MSG_0(IllegalStateException, err_msg("not an encoded indy index %d", encoded_indy_index));
  }

  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  CallInfo callInfo;
  int indy_index = cp->decode_invokedynamic_index(encoded_indy_index);
  if (resolve) {
    LinkResolver::resolve_invoke(callInfo, Handle(), cp, encoded_indy_index, Bytecodes::_invokedynamic, CHECK_0);
    cp->cache()->set_dynamic_call(callInfo, indy_index);
  }
  return cp->resolved_indy_entry_at(indy_index)->constant_pool_index();
C2V_END

C2V_VMENTRY_0(int, decodeFieldIndexToCPIndex, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint field_index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  if (field_index < 0 || field_index >= cp->resolved_field_entries_length()) {
    JVMCI_THROW_MSG_0(IllegalStateException, err_msg("invalid field index %d", field_index));
  }
  return cp->resolved_field_entry_at(field_index)->constant_pool_index();
C2V_END

C2V_VMENTRY_0(int, decodeMethodIndexToCPIndex, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint method_index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  if (method_index < 0 || method_index >= cp->resolved_method_entries_length()) {
    JVMCI_THROW_MSG_0(IllegalStateException, err_msg("invalid method index %d", method_index));
  }
  return cp->resolved_method_entry_at(method_index)->constant_pool_index();
C2V_END

C2V_VMENTRY(void, resolveInvokeHandleInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  Klass* holder = cp->klass_ref_at(index, Bytecodes::_invokehandle, CHECK);
  Symbol* name = cp->name_ref_at(index, Bytecodes::_invokehandle);
  if (MethodHandles::is_signature_polymorphic_name(holder, name)) {
    CallInfo callInfo;
    LinkResolver::resolve_invoke(callInfo, Handle(), cp, index, Bytecodes::_invokehandle, CHECK);
    cp->cache()->set_method_handle(index, callInfo);
  }
C2V_END

C2V_VMENTRY_0(jint, isResolvedInvokeHandleInPool, (JNIEnv* env, jobject, ARGUMENT_PAIR(cp), jint index))
  constantPoolHandle cp(THREAD, UNPACK_PAIR(ConstantPool, cp));
  ResolvedMethodEntry* entry = cp->cache()->resolved_method_entry_at(index);
  if (entry->is_resolved(Bytecodes::_invokehandle)) {
    // MethodHandle.invoke* --> LambdaForm?
    ResourceMark rm;

    LinkInfo link_info(cp, index, Bytecodes::_invokehandle, CATCH);

    Klass* resolved_klass = link_info.resolved_klass();

    Symbol* name_sym = cp->name_ref_at(index, Bytecodes::_invokehandle);

    vmassert(MethodHandles::is_method_handle_invoke_name(resolved_klass, name_sym), "!");
    vmassert(MethodHandles::is_signature_polymorphic_name(resolved_klass, name_sym), "!");

    methodHandle adapter_method(THREAD, entry->method());

    methodHandle resolved_method(adapter_method);

    // Can we treat it as a regular invokevirtual?
    if (resolved_method->method_holder() == resolved_klass && resolved_method->name() == name_sym) {
      vmassert(!resolved_method->is_static(),"!");
      vmassert(MethodHandles::is_signature_polymorphic_method(resolved_method()),"!");
      vmassert(!MethodHandles::is_signature_polymorphic_static(resolved_method->intrinsic_id()), "!");
      vmassert(cp->cache()->appendix_if_resolved(entry) == nullptr, "!");

      methodHandle m(THREAD, LinkResolver::linktime_resolve_virtual_method_or_null(link_info));
      vmassert(m == resolved_method, "!!");
      return -1;
    }

    return Bytecodes::_invokevirtual;
  }
  if (cp->is_invokedynamic_index(index)) {
    if (cp->resolved_indy_entry_at(cp->decode_invokedynamic_index(index))->is_resolved()) {
      return Bytecodes::_invokedynamic;
    }
  }
  return -1;
C2V_END


C2V_VMENTRY_NULL(jobject, getSignaturePolymorphicHolders, (JNIEnv* env, jobject))
  JVMCIObjectArray holders = JVMCIENV->new_String_array(2, JVMCI_CHECK_NULL);
  JVMCIObject mh = JVMCIENV->create_string("Ljava/lang/invoke/MethodHandle;", JVMCI_CHECK_NULL);
  JVMCIObject vh = JVMCIENV->create_string("Ljava/lang/invoke/VarHandle;", JVMCI_CHECK_NULL);
  JVMCIENV->put_object_at(holders, 0, mh);
  JVMCIENV->put_object_at(holders, 1, vh);
  return JVMCIENV->get_jobject(holders);
C2V_END

C2V_VMENTRY_0(jboolean, shouldDebugNonSafepoints, (JNIEnv* env, jobject))
  //see compute_recording_non_safepoints in debugInfroRec.cpp
  if (JvmtiExport::should_post_compiled_method_load() && FLAG_IS_DEFAULT(DebugNonSafepoints)) {
    return true;
  }
  return DebugNonSafepoints;
C2V_END

// public native void materializeVirtualObjects(HotSpotStackFrameReference stackFrame, boolean invalidate);
C2V_VMENTRY(void, materializeVirtualObjects, (JNIEnv* env, jobject, jobject _hs_frame, bool invalidate))
  JVMCIObject hs_frame = JVMCIENV->wrap(_hs_frame);
  if (hs_frame.is_null()) {
    JVMCI_THROW_MSG(NullPointerException, "stack frame is null");
  }

  requireInHotSpot("materializeVirtualObjects", JVMCI_CHECK);

  JVMCIENV->HotSpotStackFrameReference_initialize(JVMCI_CHECK);

  // look for the given stack frame
  StackFrameStream fst(thread, false /* update */, true /* process_frames */);
  intptr_t* stack_pointer = (intptr_t*) JVMCIENV->get_HotSpotStackFrameReference_stackPointer(hs_frame);
  while (fst.current()->id() != stack_pointer && !fst.is_done()) {
    fst.next();
  }
  if (fst.current()->id() != stack_pointer) {
    JVMCI_THROW_MSG(IllegalStateException, "stack frame not found");
  }

  if (invalidate) {
    if (!fst.current()->is_compiled_frame()) {
      JVMCI_THROW_MSG(IllegalStateException, "compiled stack frame expected");
    }
    assert(fst.current()->cb()->is_nmethod(), "nmethod expected");
    ((nmethod*) fst.current()->cb())->make_not_entrant();
  }
  Deoptimization::deoptimize(thread, *fst.current(), Deoptimization::Reason_none);
  // look for the frame again as it has been updated by deopt (pc, deopt state...)
  StackFrameStream fstAfterDeopt(thread, true /* update */, true /* process_frames */);
  while (fstAfterDeopt.current()->id() != stack_pointer && !fstAfterDeopt.is_done()) {
    fstAfterDeopt.next();
  }
  if (fstAfterDeopt.current()->id() != stack_pointer) {
    JVMCI_THROW_MSG(IllegalStateException, "stack frame not found after deopt");
  }

  vframe* vf = vframe::new_vframe(fstAfterDeopt.current(), fstAfterDeopt.register_map(), thread);
  if (!vf->is_compiled_frame()) {
    JVMCI_THROW_MSG(IllegalStateException, "compiled stack frame expected");
  }

  GrowableArray<compiledVFrame*>* virtualFrames = new GrowableArray<compiledVFrame*>(10);
  while (true) {
    assert(vf->is_compiled_frame(), "Wrong frame type");
    virtualFrames->push(compiledVFrame::cast(vf));
    if (vf->is_top()) {
      break;
    }
    vf = vf->sender();
  }

  int last_frame_number = JVMCIENV->get_HotSpotStackFrameReference_frameNumber(hs_frame);
  if (last_frame_number >= virtualFrames->length()) {
    JVMCI_THROW_MSG(IllegalStateException, "invalid frame number");
  }

  // Reallocate the non-escaping objects and restore their fields.
  assert (virtualFrames->at(last_frame_number)->scope() != nullptr,"invalid scope");
  GrowableArray<ScopeValue*>* objects = virtualFrames->at(last_frame_number)->scope()->objects();

  if (objects == nullptr) {
    // no objects to materialize
    return;
  }

  bool realloc_failures = Deoptimization::realloc_objects(thread, fstAfterDeopt.current(), fstAfterDeopt.register_map(), objects, CHECK);
  Deoptimization::reassign_fields(fstAfterDeopt.current(), fstAfterDeopt.register_map(), objects, realloc_failures, false);

  for (int frame_index = 0; frame_index < virtualFrames->length(); frame_index++) {
    compiledVFrame* cvf = virtualFrames->at(frame_index);

    GrowableArray<ScopeValue*>* scopedValues = cvf->scope()->locals();
    StackValueCollection* locals = cvf->locals();
    if (locals != nullptr) {
      for (int i2 = 0; i2 < locals->size(); i2++) {
        StackValue* var = locals->at(i2);
        assert(!scopedValues->at(i2)->is_object_merge(), "Should not be.");
        if (var->type() == T_OBJECT && scopedValues->at(i2)->is_object()) {
          jvalue val;
          val.l = cast_from_oop<jobject>(locals->at(i2)->get_obj()());
          cvf->update_local(T_OBJECT, i2, val);
        }
      }
    }

    GrowableArray<ScopeValue*>* scopeExpressions = cvf->scope()->expressions();
    StackValueCollection* expressions = cvf->expressions();
    if (expressions != nullptr) {
      for (int i2 = 0; i2 < expressions->size(); i2++) {
        StackValue* var = expressions->at(i2);
        assert(!scopeExpressions->at(i2)->is_object_merge(), "Should not be.");
        if (var->type() == T_OBJECT && scopeExpressions->at(i2)->is_object()) {
          jvalue val;
          val.l = cast_from_oop<jobject>(expressions->at(i2)->get_obj()());
          cvf->update_stack(T_OBJECT, i2, val);
        }
      }
    }

    GrowableArray<MonitorValue*>* scopeMonitors = cvf->scope()->monitors();
    GrowableArray<MonitorInfo*>* monitors = cvf->monitors();
    if (monitors != nullptr) {
      for (int i2 = 0; i2 < monitors->length(); i2++) {
        cvf->update_monitor(i2, monitors->at(i2));
      }
    }
  }

  // all locals are materialized by now
  JVMCIENV->set_HotSpotStackFrameReference_localIsVirtual(hs_frame, nullptr);
  // update the locals array
  JVMCIObjectArray array = JVMCIENV->get_HotSpotStackFrameReference_locals(hs_frame);
  StackValueCollection* locals = virtualFrames->at(last_frame_number)->locals();
  for (int i = 0; i < locals->size(); i++) {
    StackValue* var = locals->at(i);
    if (var->type() == T_OBJECT) {
      JVMCIENV->put_object_at(array, i, HotSpotJVMCI::wrap(locals->at(i)->get_obj()()));
    }
  }
  HotSpotJVMCI::HotSpotStackFrameReference::set_objectsMaterialized(JVMCIENV, hs_frame, JNI_TRUE);
C2V_END

// Use of tty does not require the current thread to be attached to the VM
// so no need for a full C2V_VMENTRY transition.
C2V_VMENTRY_PREFIX(void, writeDebugOutput, (JNIEnv* env, jobject, jlong buffer, jint length, bool flush))
  if (length <= 8) {
    tty->write((char*) &buffer, length);
  } else {
    tty->write((char*) buffer, length);
  }
  if (flush) {
    tty->flush();
  }
C2V_END

// Use of tty does not require the current thread to be attached to the VM
// so no need for a full C2V_VMENTRY transition.
C2V_VMENTRY_PREFIX(void, flushDebugOutput, (JNIEnv* env, jobject))
  tty->flush();
C2V_END

C2V_VMENTRY_0(jint, methodDataProfileDataSize, (JNIEnv* env, jobject, jlong method_data_pointer, jint position))
  MethodData* mdo = (MethodData*) method_data_pointer;
  ProfileData* profile_data = mdo->data_at(position);
  if (mdo->is_valid(profile_data)) {
    return profile_data->size_in_bytes();
  }
  // Java code should never directly access the extra data section
  JVMCI_THROW_MSG_0(IllegalArgumentException, err_msg("Invalid profile data position %d", position));
C2V_END

C2V_VMENTRY_0(jint, methodDataExceptionSeen, (JNIEnv* env, jobject, jlong method_data_pointer, jint bci))
  MethodData* mdo = (MethodData*) method_data_pointer;

  // Lock to read ProfileData, and ensure lock is not broken by a safepoint
  MutexLocker mu(mdo->extra_data_lock(), Mutex::_no_safepoint_check_flag);

  DataLayout* data    = mdo->extra_data_base();
  DataLayout* end   = mdo->args_data_limit();
  for (;; data = mdo->next_extra(data)) {
    assert(data < end, "moved past end of extra data");
    int tag = data->tag();
    switch(tag) {
      case DataLayout::bit_data_tag: {
        BitData* bit_data = (BitData*) data->data_in();
        if (bit_data->bci() == bci) {
          return bit_data->exception_seen() ? 1 : 0;
        }
        break;
      }
    case DataLayout::no_tag:
      // There is a free slot so return false since a BitData would have been allocated to record
      // true if it had been seen.
      return 0;
    case DataLayout::arg_info_data_tag:
      // The bci wasn't found and there are no free slots to record a trap for this location, so always
      // return unknown.
      return -1;
    }
  }
  ShouldNotReachHere();
  return -1;
C2V_END

C2V_VMENTRY_NULL(jobject, getInterfaces, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }

  if (!klass->is_instance_klass()) {
    JVMCI_THROW_MSG_0(InternalError, err_msg("Class %s must be instance klass", klass->external_name()));
  }
  InstanceKlass* iklass = InstanceKlass::cast(klass);

  // Regular instance klass, fill in all local interfaces
  int size = iklass->local_interfaces()->length();
  JVMCIObjectArray interfaces = JVMCIENV->new_HotSpotResolvedObjectTypeImpl_array(size, JVMCI_CHECK_NULL);
  for (int index = 0; index < size; index++) {
    JVMCIKlassHandle klass(THREAD);
    Klass* k = iklass->local_interfaces()->at(index);
    klass = k;
    JVMCIObject type = JVMCIENV->get_jvmci_type(klass, JVMCI_CHECK_NULL);
    JVMCIENV->put_object_at(interfaces, index, type);
  }
  return JVMCIENV->get_jobject(interfaces);
C2V_END

C2V_VMENTRY_NULL(jobject, getComponentType, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }

  if (!klass->is_array_klass()) {
    return nullptr;
  }
  oop mirror = klass->java_mirror();
  oop component_mirror = java_lang_Class::component_mirror(mirror);
  if (component_mirror == nullptr) {
    JVMCI_THROW_MSG_0(NullPointerException,
                    err_msg("Component mirror for array class %s is null", klass->external_name()))
  }

  Klass* component_klass = java_lang_Class::as_Klass(component_mirror);
  if (component_klass != nullptr) {
    JVMCIKlassHandle klass_handle(THREAD, component_klass);
    JVMCIObject result = JVMCIENV->get_jvmci_type(klass_handle, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobject(result);
  }
  BasicType type = java_lang_Class::primitive_type(component_mirror);
  JVMCIObject result = JVMCIENV->get_jvmci_primitive_type(type);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY(void, ensureInitialized, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW(NullPointerException);
  }
  if (klass->should_be_initialized()) {
    InstanceKlass* k = InstanceKlass::cast(klass);
    k->initialize(CHECK);
  }
C2V_END

C2V_VMENTRY(void, ensureLinked, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  CompilerThreadCanCallJava canCallJava(thread, true); // Linking requires Java calls
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW(NullPointerException);
  }
  if (klass->is_instance_klass()) {
    InstanceKlass* k = InstanceKlass::cast(klass);
    k->link_class(CHECK);
  }
C2V_END

C2V_VMENTRY_0(jint, interpreterFrameSize, (JNIEnv* env, jobject, jobject bytecode_frame_handle))
  if (bytecode_frame_handle == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }

  JVMCIObject top_bytecode_frame = JVMCIENV->wrap(bytecode_frame_handle);
  JVMCIObject bytecode_frame = top_bytecode_frame;
  int size = 0;
  int callee_parameters = 0;
  int callee_locals = 0;
  Method* method = JVMCIENV->asMethod(JVMCIENV->get_BytecodePosition_method(bytecode_frame));
  int extra_args = method->max_stack() - JVMCIENV->get_BytecodeFrame_numStack(bytecode_frame);

  while (bytecode_frame.is_non_null()) {
    int locks = JVMCIENV->get_BytecodeFrame_numLocks(bytecode_frame);
    int temps = JVMCIENV->get_BytecodeFrame_numStack(bytecode_frame);
    bool is_top_frame = (JVMCIENV->equals(bytecode_frame, top_bytecode_frame));
    Method* method = JVMCIENV->asMethod(JVMCIENV->get_BytecodePosition_method(bytecode_frame));

    int frame_size = BytesPerWord * Interpreter::size_activation(method->max_stack(),
                                                                 temps + callee_parameters,
                                                                 extra_args,
                                                                 locks,
                                                                 callee_parameters,
                                                                 callee_locals,
                                                                 is_top_frame);
    size += frame_size;

    callee_parameters = method->size_of_parameters();
    callee_locals = method->max_locals();
    extra_args = 0;
    bytecode_frame = JVMCIENV->get_BytecodePosition_caller(bytecode_frame);
  }
  return size + Deoptimization::last_frame_adjust(0, callee_locals) * BytesPerWord;
C2V_END

C2V_VMENTRY(void, compileToBytecode, (JNIEnv* env, jobject, jobject lambda_form_handle))
  Handle lambda_form = JVMCIENV->asConstant(JVMCIENV->wrap(lambda_form_handle), JVMCI_CHECK);
  if (lambda_form->is_a(vmClasses::LambdaForm_klass())) {
    TempNewSymbol compileToBytecode = SymbolTable::new_symbol("compileToBytecode");
    JavaValue result(T_VOID);
    JavaCalls::call_special(&result, lambda_form, vmClasses::LambdaForm_klass(), compileToBytecode, vmSymbols::void_method_signature(), CHECK);
  } else {
    JVMCI_THROW_MSG(IllegalArgumentException,
                    err_msg("Unexpected type: %s", lambda_form->klass()->external_name()))
  }
C2V_END

C2V_VMENTRY_0(jint, getIdentityHashCode, (JNIEnv* env, jobject, jobject object))
  Handle obj = JVMCIENV->asConstant(JVMCIENV->wrap(object), JVMCI_CHECK_0);
  return obj->identity_hash();
C2V_END

C2V_VMENTRY_0(jboolean, isInternedString, (JNIEnv* env, jobject, jobject object))
  Handle str = JVMCIENV->asConstant(JVMCIENV->wrap(object), JVMCI_CHECK_0);
  if (!java_lang_String::is_instance(str())) {
    return false;
  }
  int len;
  jchar* name = java_lang_String::as_unicode_string(str(), len, CHECK_false);
  return (StringTable::lookup(name, len) != nullptr);
C2V_END


C2V_VMENTRY_NULL(jobject, unboxPrimitive, (JNIEnv* env, jobject, jobject object))
  if (object == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Handle box = JVMCIENV->asConstant(JVMCIENV->wrap(object), JVMCI_CHECK_NULL);
  BasicType type = java_lang_boxing_object::basic_type(box());
  jvalue result;
  if (java_lang_boxing_object::get_value(box(), &result) == T_ILLEGAL) {
    return nullptr;
  }
  JVMCIObject boxResult = JVMCIENV->create_box(type, &result, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(boxResult);
C2V_END

C2V_VMENTRY_NULL(jobject, boxPrimitive, (JNIEnv* env, jobject, jobject object))
  if (object == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  JVMCIObject box = JVMCIENV->wrap(object);
  BasicType type = JVMCIENV->get_box_type(box);
  if (type == T_ILLEGAL) {
    return nullptr;
  }
  jvalue value = JVMCIENV->get_boxed_value(type, box);
  JavaValue box_result(T_OBJECT);
  JavaCallArguments jargs;
  Klass* box_klass = nullptr;
  Symbol* box_signature = nullptr;
#define BOX_CASE(bt, v, argtype, name)           \
  case bt: \
    jargs.push_##argtype(value.v); \
    box_klass = vmClasses::name##_klass(); \
    box_signature = vmSymbols::name##_valueOf_signature(); \
    break

  switch (type) {
    BOX_CASE(T_BOOLEAN, z, int, Boolean);
    BOX_CASE(T_BYTE, b, int, Byte);
    BOX_CASE(T_CHAR, c, int, Character);
    BOX_CASE(T_SHORT, s, int, Short);
    BOX_CASE(T_INT, i, int, Integer);
    BOX_CASE(T_LONG, j, long, Long);
    BOX_CASE(T_FLOAT, f, float, Float);
    BOX_CASE(T_DOUBLE, d, double, Double);
    default:
      ShouldNotReachHere();
  }
#undef BOX_CASE

  JavaCalls::call_static(&box_result,
                         box_klass,
                         vmSymbols::valueOf_name(),
                         box_signature, &jargs, CHECK_NULL);
  oop hotspot_box = box_result.get_oop();
  JVMCIObject result = JVMCIENV->get_object_constant(hotspot_box, false);
  return JVMCIENV->get_jobject(result);
C2V_END

C2V_VMENTRY_NULL(jobjectArray, getDeclaredConstructors, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  if (!klass->is_instance_klass()) {
    JVMCIObjectArray methods = JVMCIENV->new_ResolvedJavaMethod_array(0, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobjectArray(methods);
  }

  InstanceKlass* iklass = InstanceKlass::cast(klass);
  GrowableArray<Method*> constructors_array;
  for (int i = 0; i < iklass->methods()->length(); i++) {
    Method* m = iklass->methods()->at(i);
    if (m->is_initializer() && !m->is_static()) {
      constructors_array.append(m);
    }
  }
  JVMCIObjectArray methods = JVMCIENV->new_ResolvedJavaMethod_array(constructors_array.length(), JVMCI_CHECK_NULL);
  for (int i = 0; i < constructors_array.length(); i++) {
    methodHandle ctor(THREAD, constructors_array.at(i));
    JVMCIObject method = JVMCIENV->get_jvmci_method(ctor, JVMCI_CHECK_NULL);
    JVMCIENV->put_object_at(methods, i, method);
  }
  return JVMCIENV->get_jobjectArray(methods);
C2V_END

C2V_VMENTRY_NULL(jobjectArray, getDeclaredMethods, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  if (!klass->is_instance_klass()) {
    JVMCIObjectArray methods = JVMCIENV->new_ResolvedJavaMethod_array(0, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobjectArray(methods);
  }

  InstanceKlass* iklass = InstanceKlass::cast(klass);
  GrowableArray<Method*> methods_array;
  for (int i = 0; i < iklass->methods()->length(); i++) {
    Method* m = iklass->methods()->at(i);
    if (!m->is_initializer() && !m->is_overpass()) {
      methods_array.append(m);
    }
  }
  JVMCIObjectArray methods = JVMCIENV->new_ResolvedJavaMethod_array(methods_array.length(), JVMCI_CHECK_NULL);
  for (int i = 0; i < methods_array.length(); i++) {
    methodHandle mh(THREAD, methods_array.at(i));
    JVMCIObject method = JVMCIENV->get_jvmci_method(mh, JVMCI_CHECK_NULL);
    JVMCIENV->put_object_at(methods, i, method);
  }
  return JVMCIENV->get_jobjectArray(methods);
C2V_END

C2V_VMENTRY_NULL(jobjectArray, getDeclaredFieldsInfo, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  if (!klass->is_instance_klass()) {
    JVMCI_THROW_MSG_NULL(IllegalArgumentException, "not an InstanceKlass");
  }
  InstanceKlass* iklass = InstanceKlass::cast(klass);
  int java_fields, injected_fields;
  GrowableArray<FieldInfo>* fields = FieldInfoStream::create_FieldInfoArray(iklass->fieldinfo_stream(), &java_fields, &injected_fields);
  JVMCIObjectArray array = JVMCIENV->new_FieldInfo_array(fields->length(), JVMCIENV);
  for (int i = 0; i < fields->length(); i++) {
    JVMCIObject field_info = JVMCIENV->new_FieldInfo(fields->adr_at(i), JVMCI_CHECK_NULL);
    JVMCIENV->put_object_at(array, i, field_info);
  }
  return array.as_jobject();
C2V_END

static jobject read_field_value(Handle obj, long displacement, jchar type_char, bool is_static, Thread* THREAD, JVMCIEnv* JVMCIENV) {

  BasicType basic_type = JVMCIENV->typeCharToBasicType(type_char, JVMCI_CHECK_NULL);
  int basic_type_elemsize = type2aelembytes(basic_type);
  if (displacement < 0 || ((size_t) displacement + basic_type_elemsize > HeapWordSize * obj->size())) {
    // Reading outside of the object bounds
    JVMCI_THROW_MSG_NULL(IllegalArgumentException, "reading outside object bounds");
  }

  // Perform basic sanity checks on the read.  Primitive reads are permitted to read outside the
  // bounds of their fields but object reads must map exactly onto the underlying oop slot.
  bool aligned = (displacement % basic_type_elemsize) == 0;
  if (!aligned) {
    JVMCI_THROW_MSG_NULL(IllegalArgumentException, "read is unaligned");
  }
  if (basic_type == T_OBJECT) {
    if (obj->is_objArray()) {
      if (displacement < arrayOopDesc::base_offset_in_bytes(T_OBJECT)) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, "reading from array header");
      }
      if (displacement + heapOopSize > arrayOopDesc::base_offset_in_bytes(T_OBJECT) + arrayOop(obj())->length() * heapOopSize) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, "reading after last array element");
      }
      if (((displacement - arrayOopDesc::base_offset_in_bytes(T_OBJECT)) % heapOopSize) != 0) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, "misaligned object read from array");
      }
    } else if (obj->is_instance()) {
      InstanceKlass* klass = InstanceKlass::cast(is_static ? java_lang_Class::as_Klass(obj()) : obj->klass());
      fieldDescriptor fd;
      if (!klass->find_field_from_offset(displacement, is_static, &fd)) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, err_msg("Can't find field at displacement %d in object of type %s", (int) displacement, klass->external_name()));
      }
      if (fd.field_type() != T_OBJECT && fd.field_type() != T_ARRAY) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, err_msg("Field at displacement %d in object of type %s is %s but expected %s", (int) displacement,
                                                               klass->external_name(), type2name(fd.field_type()), type2name(basic_type)));
      }
    } else if (obj->is_typeArray()) {
      JVMCI_THROW_MSG_NULL(IllegalArgumentException, "Can't read objects from primitive array");
    } else {
      ShouldNotReachHere();
    }
  } else {
    if (obj->is_objArray()) {
      JVMCI_THROW_MSG_NULL(IllegalArgumentException, "Reading primitive from object array");
    } else if (obj->is_typeArray()) {
      if (displacement < arrayOopDesc::base_offset_in_bytes(ArrayKlass::cast(obj->klass())->element_type())) {
        JVMCI_THROW_MSG_NULL(IllegalArgumentException, "reading from array header");
      }
    }
  }

  jlong value = 0;

  // Treat all reads as volatile for simplicity as this function can be used
  // both for reading Java fields declared as volatile as well as for constant
  // folding Unsafe.get* methods with volatile semantics.

  switch (basic_type) {
    case T_BOOLEAN: value = HeapAccess<MO_SEQ_CST>::load(obj->field_addr<jboolean>(displacement)); break;
    case T_BYTE:    value = HeapAccess<MO_SEQ_CST>::load(obj->field_addr<jbyte>(displacement));    break;
    case T_SHORT:   value = HeapAccess<MO_SEQ_CST>::load(obj->field_addr<jshort>(displacement));   break;
    case T_CHAR:    value = HeapAccess<MO_SEQ_CST>::load(obj->field_addr<jchar>(displacement));    break;
    case T_FLOAT:
    case T_INT:     value = HeapAccess<MO_SEQ_CST>::load(obj->field_addr<jint>(displacement));     break;
    case T_DOUBLE:
    case T_LONG:    value = HeapAccess<MO_SEQ_CST>::load(obj->field_addr<jlong>(displacement));    break;

    case T_OBJECT: {
      if (displacement == java_lang_Class::component_mirror_offset() && java_lang_Class::is_instance(obj()) &&
          (java_lang_Class::as_Klass(obj()) == nullptr || !java_lang_Class::as_Klass(obj())->is_array_klass())) {
        // Class.componentType for non-array classes can transiently contain an int[] that's
        // used for locking so always return null to mimic Class.getComponentType()
        return JVMCIENV->get_jobject(JVMCIENV->get_JavaConstant_NULL_POINTER());
      }

      // Perform the read including any barriers required to make the reference strongly reachable
      // since it will be wrapped as a JavaConstant.
      oop value = obj->obj_field_access<MO_SEQ_CST | ON_UNKNOWN_OOP_REF>(displacement);

      if (value == nullptr) {
        return JVMCIENV->get_jobject(JVMCIENV->get_JavaConstant_NULL_POINTER());
      } else {
        if (value != nullptr && !oopDesc::is_oop(value)) {
          // Throw an exception to improve debuggability.  This check isn't totally reliable because
          // is_oop doesn't try to be completety safe but for most invalid values it provides a good
          // enough answer.  It possible to crash in the is_oop call but that just means the crash happens
          // closer to where things went wrong.
          JVMCI_THROW_MSG_NULL(InternalError, err_msg("Read bad oop " INTPTR_FORMAT " at offset " JLONG_FORMAT " in object " INTPTR_FORMAT " of type %s",
                                                      p2i(value), displacement, p2i(obj()), obj->klass()->external_name()));
        }

        JVMCIObject result = JVMCIENV->get_object_constant(value);
        return JVMCIENV->get_jobject(result);
      }
    }

    default:
      ShouldNotReachHere();
  }
  JVMCIObject result = JVMCIENV->call_JavaConstant_forPrimitive(type_char, value, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
}

C2V_VMENTRY_NULL(jobject, readStaticFieldValue, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass), long displacement, jchar type_char))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  Handle obj(THREAD, klass->java_mirror());
  return read_field_value(obj, displacement, type_char, true, THREAD, JVMCIENV);
C2V_END

C2V_VMENTRY_NULL(jobject, readFieldValue, (JNIEnv* env, jobject, jobject object, ARGUMENT_PAIR(expected_type), long displacement, jchar type_char))
  if (object == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }

  // asConstant will throw an NPE if a constant contains null
  Handle obj = JVMCIENV->asConstant(JVMCIENV->wrap(object), JVMCI_CHECK_NULL);

  Klass* expected_klass = UNPACK_PAIR(Klass, expected_type);
  if (expected_klass != nullptr) {
    InstanceKlass* expected_iklass = InstanceKlass::cast(expected_klass);
    if (!obj->is_a(expected_iklass)) {
      // Not of the expected type
      return nullptr;
    }
  }
  bool is_static = expected_klass == nullptr && java_lang_Class::is_instance(obj()) && displacement >= InstanceMirrorKlass::offset_of_static_fields();
  return read_field_value(obj, displacement, type_char, is_static, THREAD, JVMCIENV);
C2V_END

C2V_VMENTRY_0(jboolean, isInstance, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass), jobject object))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (object == nullptr || klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Handle obj = JVMCIENV->asConstant(JVMCIENV->wrap(object), JVMCI_CHECK_0);
  return obj->is_a(klass);
C2V_END

C2V_VMENTRY_0(jboolean, isAssignableFrom, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass), ARGUMENT_PAIR(subklass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  Klass* subklass = UNPACK_PAIR(Klass, subklass);
  if (klass == nullptr || subklass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  return subklass->is_subtype_of(klass);
C2V_END

C2V_VMENTRY_0(jboolean, isTrustedForIntrinsics, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  InstanceKlass* ik = InstanceKlass::cast(klass);
  if (ik->class_loader_data()->is_boot_class_loader_data() || ik->class_loader_data()->is_platform_class_loader_data()) {
    return true;
  }
  return false;
C2V_END

C2V_VMENTRY_NULL(jobject, asJavaType, (JNIEnv* env, jobject, jobject object))
  if (object == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Handle obj = JVMCIENV->asConstant(JVMCIENV->wrap(object), JVMCI_CHECK_NULL);
  if (java_lang_Class::is_instance(obj())) {
    if (java_lang_Class::is_primitive(obj())) {
      JVMCIObject type = JVMCIENV->get_jvmci_primitive_type(java_lang_Class::primitive_type(obj()));
      return JVMCIENV->get_jobject(type);
    }
    Klass* klass = java_lang_Class::as_Klass(obj());
    JVMCIKlassHandle klass_handle(THREAD);
    klass_handle = klass;
    JVMCIObject type = JVMCIENV->get_jvmci_type(klass_handle, JVMCI_CHECK_NULL);
    return JVMCIENV->get_jobject(type);
  }
  return nullptr;
C2V_END


C2V_VMENTRY_NULL(jobject, asString, (JNIEnv* env, jobject, jobject object))
  if (object == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Handle obj = JVMCIENV->asConstant(JVMCIENV->wrap(object), JVMCI_CHECK_NULL);
  const char* str = java_lang_String::as_utf8_string(obj());
  JVMCIObject result = JVMCIENV->create_string(str, JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(result);
C2V_END


C2V_VMENTRY_0(jboolean, equals, (JNIEnv* env, jobject, jobject x, jlong xHandle, jobject y, jlong yHandle))
  if (x == nullptr || y == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  return JVMCIENV->resolve_oop_handle(xHandle) == JVMCIENV->resolve_oop_handle(yHandle);
C2V_END

C2V_VMENTRY_NULL(jobject, getJavaMirror, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass)))
  Klass* klass = UNPACK_PAIR(Klass, klass);
  if (klass == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Handle mirror(THREAD, klass->java_mirror());
  JVMCIObject result = JVMCIENV->get_object_constant(mirror());
  return JVMCIENV->get_jobject(result);
C2V_END


C2V_VMENTRY_0(jint, getArrayLength, (JNIEnv* env, jobject, jobject x))
  if (x == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Handle xobj = JVMCIENV->asConstant(JVMCIENV->wrap(x), JVMCI_CHECK_0);
  if (xobj->klass()->is_array_klass()) {
    return arrayOop(xobj())->length();
  }
  return -1;
 C2V_END


C2V_VMENTRY_NULL(jobject, readArrayElement, (JNIEnv* env, jobject, jobject x, int index))
  if (x == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Handle xobj = JVMCIENV->asConstant(JVMCIENV->wrap(x), JVMCI_CHECK_NULL);
  if (xobj->klass()->is_array_klass()) {
    arrayOop array = arrayOop(xobj());
    BasicType element_type = ArrayKlass::cast(array->klass())->element_type();
    if (index < 0 || index >= array->length()) {
      return nullptr;
    }
    JVMCIObject result;

    if (element_type == T_OBJECT) {
      result = JVMCIENV->get_object_constant(objArrayOop(xobj())->obj_at(index));
      if (result.is_null()) {
        result = JVMCIENV->get_JavaConstant_NULL_POINTER();
      }
    } else {
      jvalue value;
      switch (element_type) {
        case T_DOUBLE:        value.d = typeArrayOop(xobj())->double_at(index);        break;
        case T_FLOAT:         value.f = typeArrayOop(xobj())->float_at(index);         break;
        case T_LONG:          value.j = typeArrayOop(xobj())->long_at(index);          break;
        case T_INT:           value.i = typeArrayOop(xobj())->int_at(index);            break;
        case T_SHORT:         value.s = typeArrayOop(xobj())->short_at(index);          break;
        case T_CHAR:          value.c = typeArrayOop(xobj())->char_at(index);           break;
        case T_BYTE:          value.b = typeArrayOop(xobj())->byte_at(index);           break;
        case T_BOOLEAN:       value.z = typeArrayOop(xobj())->byte_at(index) & 1;       break;
        default:              ShouldNotReachHere();
      }
      result = JVMCIENV->create_box(element_type, &value, JVMCI_CHECK_NULL);
    }
    assert(!result.is_null(), "must have a value");
    return JVMCIENV->get_jobject(result);
  }
  return nullptr;;
C2V_END


C2V_VMENTRY_0(jint, arrayBaseOffset, (JNIEnv* env, jobject, jchar type_char))
  BasicType type = JVMCIENV->typeCharToBasicType(type_char, JVMCI_CHECK_0);
  return arrayOopDesc::header_size(type) * HeapWordSize;
C2V_END

C2V_VMENTRY_0(jint, arrayIndexScale, (JNIEnv* env, jobject, jchar type_char))
  BasicType type = JVMCIENV->typeCharToBasicType(type_char, JVMCI_CHECK_0);
  return type2aelembytes(type);
C2V_END

C2V_VMENTRY(void, clearOopHandle, (JNIEnv* env, jobject, jlong oop_handle))
  if (oop_handle == 0L) {
    JVMCI_THROW(NullPointerException);
  }
  // Assert before nulling out, for better debugging.
  assert(JVMCIRuntime::is_oop_handle(oop_handle), "precondition");
  oop* oop_ptr = (oop*) oop_handle;
  NativeAccess<>::oop_store(oop_ptr, (oop) nullptr);
C2V_END

C2V_VMENTRY(void, releaseClearedOopHandles, (JNIEnv* env, jobject))
  JVMCIENV->runtime()->release_cleared_oop_handles();
C2V_END

static void requireJVMCINativeLibrary(JVMCI_TRAPS) {
  if (!UseJVMCINativeLibrary) {
    JVMCI_THROW_MSG(UnsupportedOperationException, "JVMCI shared library is not enabled (requires -XX:+UseJVMCINativeLibrary)");
  }
}

C2V_VMENTRY_NULL(jlongArray, registerNativeMethods, (JNIEnv* env, jobject, jclass mirror))
  requireJVMCINativeLibrary(JVMCI_CHECK_NULL);
  requireInHotSpot("registerNativeMethods", JVMCI_CHECK_NULL);
  char* sl_path;
  void* sl_handle;
  JVMCIRuntime* runtime;
  {
    // Ensure the JVMCI shared library runtime is initialized.
    PEER_JVMCIENV_FROM_THREAD(THREAD, false);
    PEER_JVMCIENV->check_init(JVMCI_CHECK_NULL);

    HandleMark hm(THREAD);
    runtime = JVMCI::compiler_runtime(thread);
    if (PEER_JVMCIENV->has_pending_exception()) {
      PEER_JVMCIENV->describe_pending_exception(tty);
    }
    sl_handle = JVMCI::get_shared_library(sl_path, false);
    if (sl_handle == nullptr) {
      JVMCI_THROW_MSG_0(InternalError, err_msg("Error initializing JVMCI runtime %d", runtime->id()));
    }
  }

  if (mirror == nullptr) {
    JVMCI_THROW_0(NullPointerException);
  }
  Klass* klass = java_lang_Class::as_Klass(JNIHandles::resolve(mirror));
  if (klass == nullptr || !klass->is_instance_klass()) {
    JVMCI_THROW_MSG_0(IllegalArgumentException, "clazz is for primitive type");
  }

  InstanceKlass* iklass = InstanceKlass::cast(klass);
  for (int i = 0; i < iklass->methods()->length(); i++) {
    methodHandle method(THREAD, iklass->methods()->at(i));
    if (method->is_native()) {

      // Compute argument size
      int args_size = 1                             // JNIEnv
                    + (method->is_static() ? 1 : 0) // class for static methods
                    + method->size_of_parameters(); // actual parameters

      // 1) Try JNI short style
      stringStream st;
      char* pure_name = NativeLookup::pure_jni_name(method);
      guarantee(pure_name != nullptr, "Illegal native method name encountered");
      os::print_jni_name_prefix_on(&st, args_size);
      st.print_raw(pure_name);
      os::print_jni_name_suffix_on(&st, args_size);
      char* jni_name = st.as_string();

      address entry = (address) os::dll_lookup(sl_handle, jni_name);
      if (entry == nullptr) {
        // 2) Try JNI long style
        st.reset();
        char* long_name = NativeLookup::long_jni_name(method);
        guarantee(long_name != nullptr, "Illegal native method name encountered");
        os::print_jni_name_prefix_on(&st, args_size);
        st.print_raw(pure_name);
        st.print_raw(long_name);
        os::print_jni_name_suffix_on(&st, args_size);
        char* jni_long_name = st.as_string();
        entry = (address) os::dll_lookup(sl_handle, jni_long_name);
        if (entry == nullptr) {
          JVMCI_THROW_MSG_0(UnsatisfiedLinkError, err_msg("%s [neither %s nor %s exist in %s]",
              method->name_and_sig_as_C_string(),
              jni_name, jni_long_name, sl_path));
        }
      }

      if (method->has_native_function() && entry != method->native_function()) {
        JVMCI_THROW_MSG_0(UnsatisfiedLinkError, err_msg("%s [cannot re-link from " PTR_FORMAT " to " PTR_FORMAT "]",
            method->name_and_sig_as_C_string(), p2i(method->native_function()), p2i(entry)));
      }
      method->set_native_function(entry, Method::native_bind_event_is_interesting);
      log_debug(jni, resolve)("[Dynamic-linking native method %s.%s ... JNI] @ " PTR_FORMAT,
                              method->method_holder()->external_name(),
                              method->name()->as_C_string(),
                              p2i((void*) entry));
    }
  }

  typeArrayOop info_oop = oopFactory::new_longArray(4, CHECK_0);
  jlongArray info = (jlongArray) JNIHandles::make_local(THREAD, info_oop);
  runtime->init_JavaVM_info(info, JVMCI_CHECK_0);
  return info;
C2V_END

C2V_VMENTRY_PREFIX(jboolean, isCurrentThreadAttached, (JNIEnv* env, jobject c2vm))
  if (thread == nullptr || thread->libjvmci_runtime() == nullptr) {
    // Called from unattached JVMCI shared library thread
    return false;
  }
  if (thread->jni_environment() == env) {
    C2V_BLOCK(jboolean, isCurrentThreadAttached, (JNIEnv* env, jobject))
    JVMCITraceMark jtm("isCurrentThreadAttached");
    requireJVMCINativeLibrary(JVMCI_CHECK_0);
    JVMCIRuntime* runtime = thread->libjvmci_runtime();
    if (runtime == nullptr || !runtime->has_shared_library_javavm()) {
      JVMCI_THROW_MSG_0(IllegalStateException, "Require JVMCI shared library JavaVM to be initialized in isCurrentThreadAttached");
    }
    JNIEnv* peerEnv;
    return runtime->GetEnv(thread, (void**) &peerEnv, JNI_VERSION_1_2) == JNI_OK;
  }
  return true;
C2V_END

C2V_VMENTRY_PREFIX(jlong, getCurrentJavaThread, (JNIEnv* env, jobject c2vm))
  if (thread == nullptr) {
    // Called from unattached JVMCI shared library thread
    return 0L;
  }
  return (jlong) p2i(thread);
C2V_END

// Attaches a thread started in a JVMCI shared library to a JavaThread and JVMCI runtime.
static void attachSharedLibraryThread(JNIEnv* env, jbyteArray name, jboolean as_daemon) {
  JavaVM* javaVM = nullptr;
  jint res = env->GetJavaVM(&javaVM);
  if (res != JNI_OK) {
    JNI_THROW("attachSharedLibraryThread", InternalError, err_msg("Error getting shared library JavaVM from shared library JNIEnv: %d", res));
  }
  extern struct JavaVM_ main_vm;
  JNIEnv* hotspotEnv;

  int name_len = env->GetArrayLength(name);
  char name_buf[64]; // Cannot use Resource heap as it requires a current thread
  int to_copy = MIN2(name_len, (int) sizeof(name_buf) - 1);
  env->GetByteArrayRegion(name, 0, to_copy, (jbyte*) name_buf);
  name_buf[to_copy] = '\0';
  JavaVMAttachArgs attach_args;
  attach_args.version = JNI_VERSION_1_2;
  attach_args.name = name_buf;
  attach_args.group = nullptr;
  res = as_daemon ? main_vm.AttachCurrentThreadAsDaemon((void**)&hotspotEnv, &attach_args) :
                    main_vm.AttachCurrentThread((void**)&hotspotEnv, &attach_args);
  if (res != JNI_OK) {
    JNI_THROW("attachSharedLibraryThread", InternalError, err_msg("Trying to attach thread returned %d", res));
  }
  JavaThread* thread = JavaThread::thread_from_jni_environment(hotspotEnv);
  const char* attach_error;
  {
    // Transition to VM
    JVMCI_VM_ENTRY_MARK
    attach_error = JVMCIRuntime::attach_shared_library_thread(thread, javaVM);
    // Transition back to Native
  }
  if (attach_error != nullptr) {
    JNI_THROW("attachCurrentThread", InternalError, attach_error);
  }
}

C2V_VMENTRY_PREFIX(jboolean, attachCurrentThread, (JNIEnv* env, jobject c2vm, jbyteArray name, jboolean as_daemon, jlongArray javaVM_info))
  if (thread == nullptr) {
    attachSharedLibraryThread(env, name, as_daemon);
    return true;
  }
  if (thread->jni_environment() == env) {
    // Called from HotSpot
    C2V_BLOCK(jboolean, attachCurrentThread, (JNIEnv* env, jobject, jboolean))
    JVMCITraceMark jtm("attachCurrentThread");
    requireJVMCINativeLibrary(JVMCI_CHECK_0);

    JVMCIRuntime* runtime = JVMCI::compiler_runtime(thread);
    JNIEnv* peerJNIEnv;
    if (runtime->has_shared_library_javavm()) {
      if (runtime->GetEnv(thread, (void**)&peerJNIEnv, JNI_VERSION_1_2) == JNI_OK) {
        // Already attached
        runtime->init_JavaVM_info(javaVM_info, JVMCI_CHECK_0);
        return false;
      }
    }

    {
      // Ensure the JVMCI shared library runtime is initialized.
      PEER_JVMCIENV_FROM_THREAD(THREAD, false);
      PEER_JVMCIENV->check_init(JVMCI_CHECK_0);

      HandleMark hm(thread);
      JVMCIObject receiver = runtime->get_HotSpotJVMCIRuntime(PEER_JVMCIENV);
      if (PEER_JVMCIENV->has_pending_exception()) {
        PEER_JVMCIENV->describe_pending_exception(tty);
      }
      char* sl_path;
      if (JVMCI::get_shared_library(sl_path, false) == nullptr) {
        JVMCI_THROW_MSG_0(InternalError, "Error initializing JVMCI runtime");
      }
    }

    JavaVMAttachArgs attach_args;
    attach_args.version = JNI_VERSION_1_2;
    attach_args.name = const_cast<char*>(thread->name());
    attach_args.group = nullptr;
    if (runtime->GetEnv(thread, (void**) &peerJNIEnv, JNI_VERSION_1_2) == JNI_OK) {
      return false;
    }
    jint res = as_daemon ? runtime->AttachCurrentThreadAsDaemon(thread, (void**) &peerJNIEnv, &attach_args) :
                           runtime->AttachCurrentThread(thread, (void**) &peerJNIEnv, &attach_args);

    if (res == JNI_OK) {
      guarantee(peerJNIEnv != nullptr, "must be");
      runtime->init_JavaVM_info(javaVM_info, JVMCI_CHECK_0);
      JVMCI_event_1("attached to JavaVM[%d] for JVMCI runtime %d", runtime->get_shared_library_javavm_id(), runtime->id());
      return true;
    }
    JVMCI_THROW_MSG_0(InternalError, err_msg("Error %d while attaching %s", res, attach_args.name));
  }
  // Called from JVMCI shared library
  return false;
C2V_END

C2V_VMENTRY_PREFIX(jboolean, detachCurrentThread, (JNIEnv* env, jobject c2vm, jboolean release))
  if (thread == nullptr) {
    // Called from unattached JVMCI shared library thread
    JNI_THROW_("detachCurrentThread", IllegalStateException, "Cannot detach non-attached thread", false);
  }
  if (thread->jni_environment() == env) {
    // Called from HotSpot
    C2V_BLOCK(void, detachCurrentThread, (JNIEnv* env, jobject))
    JVMCITraceMark jtm("detachCurrentThread");
    requireJVMCINativeLibrary(JVMCI_CHECK_0);
    requireInHotSpot("detachCurrentThread", JVMCI_CHECK_0);
    JVMCIRuntime* runtime = thread->libjvmci_runtime();
    if (runtime == nullptr || !runtime->has_shared_library_javavm()) {
      JVMCI_THROW_MSG_0(IllegalStateException, "Require JVMCI shared library JavaVM to be initialized in detachCurrentThread");
    }
    JNIEnv* peerEnv;

    if (runtime->GetEnv(thread, (void**) &peerEnv, JNI_VERSION_1_2) != JNI_OK) {
      JVMCI_THROW_MSG_0(IllegalStateException, err_msg("Cannot detach non-attached thread: %s", thread->name()));
    }
    jint res = runtime->DetachCurrentThread(thread);
    if (res != JNI_OK) {
      JVMCI_THROW_MSG_0(InternalError, err_msg("Error %d while attaching %s", res, thread->name()));
    }
    JVMCI_event_1("detached from JavaVM[%d] for JVMCI runtime %d",
        runtime->get_shared_library_javavm_id(), runtime->id());
    if (release) {
      return runtime->detach_thread(thread, "user thread detach");
    }
  } else {
    // Called from attached JVMCI shared library thread
    if (release) {
      JNI_THROW_("detachCurrentThread", InternalError, "JVMCI shared library thread cannot release JVMCI shared library JavaVM", false);
    }
    JVMCIRuntime* runtime = thread->libjvmci_runtime();
    if (runtime == nullptr) {
      JNI_THROW_("detachCurrentThread", InternalError, "JVMCI shared library thread should have a JVMCI runtime", false);
    }
    {
      // Transition to VM
      C2V_BLOCK(jboolean, detachCurrentThread, (JNIEnv* env, jobject))
      // Cannot destroy shared library JavaVM as we're about to return to it.
      runtime->detach_thread(thread, "shared library thread detach", false);
      JVMCI_event_1("detaching JVMCI shared library thread from HotSpot JavaVM");
      // Transition back to Native
    }
    extern struct JavaVM_ main_vm;
    jint res = main_vm.DetachCurrentThread();
    if (res != JNI_OK) {
      JNI_THROW_("detachCurrentThread", InternalError, "Cannot detach non-attached thread", false);
    }
  }
  return false;
C2V_END

C2V_VMENTRY_0(jlong, translate, (JNIEnv* env, jobject, jobject obj_handle, jboolean callPostTranslation))
  requireJVMCINativeLibrary(JVMCI_CHECK_0);
  if (obj_handle == nullptr) {
    return 0L;
  }
  PEER_JVMCIENV_FROM_THREAD(THREAD, !JVMCIENV->is_hotspot());
  CompilerThreadCanCallJava canCallJava(thread, PEER_JVMCIENV->is_hotspot());
  PEER_JVMCIENV->check_init(JVMCI_CHECK_0);

  JVMCIEnv* thisEnv = JVMCIENV;
  JVMCIObject obj = thisEnv->wrap(obj_handle);
  JVMCIObject result;
  if (thisEnv->isa_HotSpotResolvedJavaMethodImpl(obj)) {
    methodHandle method(THREAD, thisEnv->asMethod(obj));
    result = PEER_JVMCIENV->get_jvmci_method(method, JVMCI_CHECK_0);
  } else if (thisEnv->isa_HotSpotResolvedObjectTypeImpl(obj)) {
    Klass* klass = thisEnv->asKlass(obj);
    JVMCIKlassHandle klass_handle(THREAD);
    klass_handle = klass;
    result = PEER_JVMCIENV->get_jvmci_type(klass_handle, JVMCI_CHECK_0);
  } else if (thisEnv->isa_HotSpotResolvedPrimitiveType(obj)) {
    BasicType type = JVMCIENV->kindToBasicType(JVMCIENV->get_HotSpotResolvedPrimitiveType_kind(obj), JVMCI_CHECK_0);
    result = PEER_JVMCIENV->get_jvmci_primitive_type(type);
  } else if (thisEnv->isa_IndirectHotSpotObjectConstantImpl(obj) ||
             thisEnv->isa_DirectHotSpotObjectConstantImpl(obj)) {
    Handle constant = thisEnv->asConstant(obj, JVMCI_CHECK_0);
    result = PEER_JVMCIENV->get_object_constant(constant());
  } else if (thisEnv->isa_HotSpotNmethod(obj)) {
    if (PEER_JVMCIENV->is_hotspot()) {
      nmethod* nm = JVMCIENV->get_nmethod(obj);
      if (nm != nullptr) {
        JVMCINMethodData* data = nm->jvmci_nmethod_data();
        if (data != nullptr) {
          // Only the mirror in the HotSpot heap is accessible
          // through JVMCINMethodData
          oop nmethod_mirror = data->get_nmethod_mirror(nm, /* phantom_ref */ true);
          if (nmethod_mirror != nullptr) {
            result = HotSpotJVMCI::wrap(nmethod_mirror);
          }
        }
      }
    }

    if (result.is_null()) {
      JVMCIObject methodObject = thisEnv->get_HotSpotNmethod_method(obj);
      methodHandle mh(THREAD, thisEnv->asMethod(methodObject));
      jboolean isDefault = thisEnv->get_HotSpotNmethod_isDefault(obj);
      jlong compileIdSnapshot = thisEnv->get_HotSpotNmethod_compileIdSnapshot(obj);
      JVMCIObject name_string = thisEnv->get_InstalledCode_name(obj);
      const char* cstring = name_string.is_null() ? nullptr : thisEnv->as_utf8_string(name_string);
      // Create a new HotSpotNmethod instance in the peer runtime
      result = PEER_JVMCIENV->new_HotSpotNmethod(mh, cstring, isDefault, compileIdSnapshot, JVMCI_CHECK_0);
      nmethod* nm = JVMCIENV->get_nmethod(obj);
      if (result.is_null()) {
        // exception occurred (e.g. OOME) creating a new HotSpotNmethod
      } else if (nm == nullptr) {
        // nmethod must have been unloaded
      } else {
        // Link the new HotSpotNmethod to the nmethod
        PEER_JVMCIENV->initialize_installed_code(result, nm, JVMCI_CHECK_0);
        // Only non-default HotSpotNmethod instances in the HotSpot heap are tracked directly by the runtime.
        if (!isDefault && PEER_JVMCIENV->is_hotspot()) {
          JVMCINMethodData* data = nm->jvmci_nmethod_data();
          if (data == nullptr) {
            JVMCI_THROW_MSG_0(IllegalArgumentException, "Missing HotSpotNmethod data");
          }
          if (data->get_nmethod_mirror(nm, /* phantom_ref */ false) != nullptr) {
            JVMCI_THROW_MSG_0(IllegalArgumentException, "Cannot overwrite existing HotSpotNmethod mirror for nmethod");
          }
          oop nmethod_mirror = HotSpotJVMCI::resolve(result);
          data->set_nmethod_mirror(nm, nmethod_mirror);
        }
      }
    }
  } else {
    JVMCI_THROW_MSG_0(IllegalArgumentException,
                err_msg("Cannot translate object of type: %s", thisEnv->klass_name(obj)));
  }
  if (callPostTranslation) {
    PEER_JVMCIENV->call_HotSpotJVMCIRuntime_postTranslation(result, JVMCI_CHECK_0);
  }
  // Propagate any exception that occurred while creating the translated object
  if (PEER_JVMCIENV->transfer_pending_exception(thread, thisEnv)) {
    return 0L;
  }
  return (jlong) PEER_JVMCIENV->make_global(result).as_jobject();
C2V_END

C2V_VMENTRY_NULL(jobject, unhand, (JNIEnv* env, jobject, jlong obj_handle))
  requireJVMCINativeLibrary(JVMCI_CHECK_NULL);
  if (obj_handle == 0L) {
    return nullptr;
  }
  jobject global_handle = (jobject) obj_handle;
  JVMCIObject global_handle_obj = JVMCIENV->wrap(global_handle);
  jobject result = JVMCIENV->make_local(global_handle_obj).as_jobject();

  JVMCIENV->destroy_global(global_handle_obj);
  return result;
C2V_END

C2V_VMENTRY(void, updateHotSpotNmethod, (JNIEnv* env, jobject, jobject code_handle))
  JVMCIObject code = JVMCIENV->wrap(code_handle);
  // Execute this operation for the side effect of updating the InstalledCode state
  JVMCIENV->get_nmethod(code);
C2V_END

C2V_VMENTRY_NULL(jbyteArray, getCode, (JNIEnv* env, jobject, jobject code_handle))
  JVMCIObject code = JVMCIENV->wrap(code_handle);
  CodeBlob* cb = JVMCIENV->get_code_blob(code);
  if (cb == nullptr) {
    return nullptr;
  }
  // Make a resource copy of code before the allocation causes a safepoint
  int code_size = cb->code_size();
  jbyte* code_bytes = NEW_RESOURCE_ARRAY(jbyte, code_size);
  memcpy(code_bytes, (jbyte*) cb->code_begin(), code_size);

  JVMCIPrimitiveArray result = JVMCIENV->new_byteArray(code_size, JVMCI_CHECK_NULL);
  JVMCIENV->copy_bytes_from(code_bytes, result, 0, code_size);
  return JVMCIENV->get_jbyteArray(result);
C2V_END

C2V_VMENTRY_NULL(jobject, asReflectionExecutable, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  requireInHotSpot("asReflectionExecutable", JVMCI_CHECK_NULL);
  methodHandle m(THREAD, UNPACK_PAIR(Method, method));
  oop executable;
  if (m->is_initializer()) {
    if (m->is_static_initializer()) {
      JVMCI_THROW_MSG_NULL(IllegalArgumentException,
          "Cannot create java.lang.reflect.Method for class initializer");
    }
    executable = Reflection::new_constructor(m, CHECK_NULL);
  } else {
    executable = Reflection::new_method(m, false, CHECK_NULL);
  }
  return JNIHandles::make_local(THREAD, executable);
C2V_END

static InstanceKlass* check_field(Klass* klass, jint index, JVMCI_TRAPS) {
  if (!klass->is_instance_klass()) {
    JVMCI_THROW_MSG_NULL(IllegalArgumentException,
        err_msg("Expected non-primitive type, got %s", klass->external_name()));
  }
  InstanceKlass* iklass = InstanceKlass::cast(klass);
  if (index < 0 || index > iklass->total_fields_count()) {
    JVMCI_THROW_MSG_NULL(IllegalArgumentException,
        err_msg("Field index %d out of bounds for %s", index, klass->external_name()));
  }
  return iklass;
}

C2V_VMENTRY_NULL(jobject, asReflectionField, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass), jint index))
  requireInHotSpot("asReflectionField", JVMCI_CHECK_NULL);
  Klass* klass = UNPACK_PAIR(Klass, klass);
  InstanceKlass* iklass = check_field(klass, index, JVMCIENV);
  fieldDescriptor fd(iklass, index);
  oop reflected = Reflection::new_field(&fd, CHECK_NULL);
  return JNIHandles::make_local(THREAD, reflected);
C2V_END

static jbyteArray get_encoded_annotation_data(InstanceKlass* holder, AnnotationArray* annotations_array, bool for_class,
                                              jint filter_length, jlong filter_klass_pointers,
                                              JavaThread* THREAD, JVMCIEnv* JVMCIENV) {
  // Get a ConstantPool object for annotation parsing
  Handle jcp = reflect_ConstantPool::create(CHECK_NULL);
  reflect_ConstantPool::set_cp(jcp(), holder->constants());

  // load VMSupport
  Symbol* klass = vmSymbols::jdk_internal_vm_VMSupport();
  Klass* k = SystemDictionary::resolve_or_fail(klass, true, CHECK_NULL);

  InstanceKlass* vm_support = InstanceKlass::cast(k);
  if (vm_support->should_be_initialized()) {
    vm_support->initialize(CHECK_NULL);
  }

  typeArrayOop annotations_oop = Annotations::make_java_array(annotations_array, CHECK_NULL);
  typeArrayHandle annotations = typeArrayHandle(THREAD, annotations_oop);

  InstanceKlass** filter = filter_length == 1 ?
      (InstanceKlass**) &filter_klass_pointers:
      (InstanceKlass**) filter_klass_pointers;
  objArrayOop filter_oop = oopFactory::new_objArray(vmClasses::Class_klass(), filter_length, CHECK_NULL);
  objArrayHandle filter_classes(THREAD, filter_oop);
  for (int i = 0; i < filter_length; i++) {
    filter_classes->obj_at_put(i, filter[i]->java_mirror());
  }

  // invoke VMSupport.encodeAnnotations
  JavaValue result(T_OBJECT);
  JavaCallArguments args;
  args.push_oop(annotations);
  args.push_oop(Handle(THREAD, holder->java_mirror()));
  args.push_oop(jcp);
  args.push_int(for_class);
  args.push_oop(filter_classes);
  Symbol* signature = vmSymbols::encodeAnnotations_signature();
  JavaCalls::call_static(&result,
                         vm_support,
                         vmSymbols::encodeAnnotations_name(),
                         signature,
                         &args,
                         CHECK_NULL);

  oop res = result.get_oop();
  if (JVMCIENV->is_hotspot()) {
    return (jbyteArray) JNIHandles::make_local(THREAD, res);
  }

  typeArrayOop ba = typeArrayOop(res);
  int ba_len = ba->length();
  jbyte* ba_buf = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, jbyte, ba_len);
  if (ba_buf == nullptr) {
    JVMCI_THROW_MSG_NULL(InternalError,
              err_msg("could not allocate %d bytes", ba_len));

  }
  memcpy(ba_buf, ba->byte_at_addr(0), ba_len);
  JVMCIPrimitiveArray ba_dest = JVMCIENV->new_byteArray(ba_len, JVMCI_CHECK_NULL);
  JVMCIENV->copy_bytes_from(ba_buf, ba_dest, 0, ba_len);
  return JVMCIENV->get_jbyteArray(ba_dest);
}

C2V_VMENTRY_NULL(jbyteArray, getEncodedClassAnnotationData, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass),
                 jobject filter, jint filter_length, jlong filter_klass_pointers))
  CompilerThreadCanCallJava canCallJava(thread, true); // Requires Java support
  InstanceKlass* holder = InstanceKlass::cast(UNPACK_PAIR(Klass, klass));
  return get_encoded_annotation_data(holder, holder->class_annotations(), true, filter_length, filter_klass_pointers, THREAD, JVMCIENV);
C2V_END

C2V_VMENTRY_NULL(jbyteArray, getEncodedExecutableAnnotationData, (JNIEnv* env, jobject, ARGUMENT_PAIR(method),
                 jobject filter, jint filter_length, jlong filter_klass_pointers))
  CompilerThreadCanCallJava canCallJava(thread, true); // Requires Java support
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  return get_encoded_annotation_data(method->method_holder(), method->annotations(), false, filter_length, filter_klass_pointers, THREAD, JVMCIENV);
C2V_END

C2V_VMENTRY_NULL(jbyteArray, getEncodedFieldAnnotationData, (JNIEnv* env, jobject, ARGUMENT_PAIR(klass), jint index,
                 jobject filter, jint filter_length, jlong filter_klass_pointers))
  CompilerThreadCanCallJava canCallJava(thread, true); // Requires Java support
  InstanceKlass* holder = check_field(InstanceKlass::cast(UNPACK_PAIR(Klass, klass)), index, JVMCIENV);
  fieldDescriptor fd(holder, index);
  return get_encoded_annotation_data(holder, fd.annotations(), false, filter_length, filter_klass_pointers, THREAD, JVMCIENV);
C2V_END

C2V_VMENTRY_NULL(jobjectArray, getFailedSpeculations, (JNIEnv* env, jobject, jlong failed_speculations_address, jobjectArray current))
  FailedSpeculation* head = *((FailedSpeculation**)(address) failed_speculations_address);
  int result_length = 0;
  for (FailedSpeculation* fs = head; fs != nullptr; fs = fs->next()) {
    result_length++;
  }
  int current_length = 0;
  JVMCIObjectArray current_array = nullptr;
  if (current != nullptr) {
    current_array = JVMCIENV->wrap(current);
    current_length = JVMCIENV->get_length(current_array);
    if (current_length == result_length) {
      // No new failures
      return current;
    }
  }
  JVMCIObjectArray result = JVMCIENV->new_byte_array_array(result_length, JVMCI_CHECK_NULL);
  int result_index = 0;
  for (FailedSpeculation* fs = head; result_index < result_length; fs = fs->next()) {
    assert(fs != nullptr, "npe");
    JVMCIPrimitiveArray entry;
    if (result_index < current_length) {
      entry = (JVMCIPrimitiveArray) JVMCIENV->get_object_at(current_array, result_index);
    } else {
      entry = JVMCIENV->new_byteArray(fs->data_len(), JVMCI_CHECK_NULL);
      JVMCIENV->copy_bytes_from((jbyte*) fs->data(), entry, 0, fs->data_len());
    }
    JVMCIENV->put_object_at(result, result_index++, entry);
  }
  return JVMCIENV->get_jobjectArray(result);
C2V_END

C2V_VMENTRY_0(jlong, getFailedSpeculationsAddress, (JNIEnv* env, jobject, ARGUMENT_PAIR(method)))
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  MethodData* method_data = get_profiling_method_data(method, CHECK_0);
  return (jlong) method_data->get_failed_speculations_address();
C2V_END

C2V_VMENTRY(void, releaseFailedSpeculations, (JNIEnv* env, jobject, jlong failed_speculations_address))
  FailedSpeculation::free_failed_speculations((FailedSpeculation**)(address) failed_speculations_address);
C2V_END

C2V_VMENTRY_0(jboolean, addFailedSpeculation, (JNIEnv* env, jobject, jlong failed_speculations_address, jbyteArray speculation_obj))
  JVMCIPrimitiveArray speculation_handle = JVMCIENV->wrap(speculation_obj);
  int speculation_len = JVMCIENV->get_length(speculation_handle);
  char* speculation = NEW_RESOURCE_ARRAY(char, speculation_len);
  JVMCIENV->copy_bytes_to(speculation_handle, (jbyte*) speculation, 0, speculation_len);
  return FailedSpeculation::add_failed_speculation(nullptr, (FailedSpeculation**)(address) failed_speculations_address, (address) speculation, speculation_len);
C2V_END

C2V_VMENTRY(void, callSystemExit, (JNIEnv* env, jobject, jint status))
  if (!JVMCIENV->is_hotspot()) {
    // It's generally not safe to call Java code before the module system is initialized
    if (!Universe::is_module_initialized()) {
      JVMCI_event_1("callSystemExit(%d) before Universe::is_module_initialized() -> direct VM exit", status);
      vm_exit_during_initialization();
    }
  }
  CompilerThreadCanCallJava canCallJava(thread, true);
  JavaValue result(T_VOID);
  JavaCallArguments jargs(1);
  jargs.push_int(status);
  JavaCalls::call_static(&result,
                       vmClasses::System_klass(),
                       vmSymbols::exit_method_name(),
                       vmSymbols::int_void_signature(),
                       &jargs,
                       CHECK);
C2V_END

C2V_VMENTRY_0(jlong, ticksNow, (JNIEnv* env, jobject))
  return CompilerEvent::ticksNow();
C2V_END

C2V_VMENTRY_0(jint, registerCompilerPhase, (JNIEnv* env, jobject, jstring jphase_name))
#if INCLUDE_JFR
  JVMCIObject phase_name = JVMCIENV->wrap(jphase_name);
  const char *name = JVMCIENV->as_utf8_string(phase_name);
  return CompilerEvent::PhaseEvent::get_phase_id(name, true, true, true);
#else
  return -1;
#endif // !INCLUDE_JFR
C2V_END

C2V_VMENTRY(void, notifyCompilerPhaseEvent, (JNIEnv* env, jobject, jlong startTime, jint phase, jint compileId, jint level))
  EventCompilerPhase event(UNTIMED);
  if (event.should_commit()) {
    CompilerEvent::PhaseEvent::post(event, startTime, phase, compileId, level);
  }
C2V_END

C2V_VMENTRY(void, notifyCompilerInliningEvent, (JNIEnv* env, jobject, jint compileId, ARGUMENT_PAIR(caller), ARGUMENT_PAIR(callee), jboolean succeeded, jstring jmessage, jint bci))
  EventCompilerInlining event;
  if (event.should_commit()) {
    Method* caller = UNPACK_PAIR(Method, caller);
    Method* callee = UNPACK_PAIR(Method, callee);
    JVMCIObject message = JVMCIENV->wrap(jmessage);
    CompilerEvent::InlineEvent::post(event, compileId, caller, callee, succeeded, JVMCIENV->as_utf8_string(message), bci);
  }
C2V_END

C2V_VMENTRY(void, setThreadLocalObject, (JNIEnv* env, jobject, jint id, jobject value))
  requireInHotSpot("setThreadLocalObject", JVMCI_CHECK);
  if (id == 0) {
    thread->set_jvmci_reserved_oop0(JNIHandles::resolve(value));
    return;
  }
  THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
            err_msg("%d is not a valid thread local id", id));
C2V_END

C2V_VMENTRY_NULL(jobject, getThreadLocalObject, (JNIEnv* env, jobject, jint id))
  requireInHotSpot("getThreadLocalObject", JVMCI_CHECK_NULL);
  if (id == 0) {
    return JNIHandles::make_local(thread->get_jvmci_reserved_oop0());
  }
  THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
              err_msg("%d is not a valid thread local id", id));
C2V_END

C2V_VMENTRY(void, setThreadLocalLong, (JNIEnv* env, jobject, jint id, jlong value))
  requireInHotSpot("setThreadLocalLong", JVMCI_CHECK);
  if (id == 0) {
    thread->set_jvmci_reserved0(value);
  } else if (id == 1) {
    thread->set_jvmci_reserved1(value);
  } else {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              err_msg("%d is not a valid thread local id", id));
  }
C2V_END

C2V_VMENTRY_0(jlong, getThreadLocalLong, (JNIEnv* env, jobject, jint id))
  requireInHotSpot("getThreadLocalLong", JVMCI_CHECK_0);
  if (id == 0) {
    return thread->get_jvmci_reserved0();
  } else if (id == 1) {
    return thread->get_jvmci_reserved1();
  } else {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("%d is not a valid thread local id", id));
  }
C2V_END

C2V_VMENTRY(void, getOopMapAt, (JNIEnv* env, jobject, ARGUMENT_PAIR(method),
                 jint bci, jlongArray oop_map_handle))
  methodHandle method(THREAD, UNPACK_PAIR(Method, method));
  if (bci < 0 || bci >= method->code_size()) {
    JVMCI_THROW_MSG(IllegalArgumentException,
                err_msg("bci %d is out of bounds [0 .. %d)", bci, method->code_size()));
  }
  InterpreterOopMap mask;
  OopMapCache::compute_one_oop_map(method, bci, &mask);
  if (!mask.has_valid_mask()) {
    JVMCI_THROW_MSG(IllegalArgumentException, err_msg("bci %d is not valid", bci));
  }
  if (mask.number_of_entries() == 0) {
    return;
  }

  int nslots = method->max_locals() + method->max_stack();
  int nwords = ((nslots - 1) / 64) + 1;
  JVMCIPrimitiveArray oop_map = JVMCIENV->wrap(oop_map_handle);
  int oop_map_len = JVMCIENV->get_length(oop_map);
  if (nwords > oop_map_len) {
    JVMCI_THROW_MSG(IllegalArgumentException,
                err_msg("oop map too short: %d > %d", nwords, oop_map_len));
  }

  jlong* oop_map_buf = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, jlong, nwords);
  if (oop_map_buf == nullptr) {
    JVMCI_THROW_MSG(InternalError, err_msg("could not allocate %d longs", nwords));
  }
  for (int i = 0; i < nwords; i++) {
    oop_map_buf[i] = 0L;
  }

  BitMapView oop_map_view = BitMapView((BitMap::bm_word_t*) oop_map_buf, nwords * BitsPerLong);
  for (int i = 0; i < nslots; i++) {
    if (mask.is_oop(i)) {
      oop_map_view.set_bit(i);
    }
  }
  JVMCIENV->copy_longs_from((jlong*)oop_map_buf, oop_map, 0, nwords);
C2V_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &(c2v_ ## f))

#define STRING                  "Ljava/lang/String;"
#define OBJECT                  "Ljava/lang/Object;"
#define CLASS                   "Ljava/lang/Class;"
#define OBJECTCONSTANT          "Ljdk/vm/ci/hotspot/HotSpotObjectConstantImpl;"
#define EXECUTABLE              "Ljava/lang/reflect/Executable;"
#define STACK_TRACE_ELEMENT     "Ljava/lang/StackTraceElement;"
#define INSTALLED_CODE          "Ljdk/vm/ci/code/InstalledCode;"
#define BYTECODE_FRAME          "Ljdk/vm/ci/code/BytecodeFrame;"
#define JAVACONSTANT            "Ljdk/vm/ci/meta/JavaConstant;"
#define INSPECTED_FRAME_VISITOR "Ljdk/vm/ci/code/stack/InspectedFrameVisitor;"
#define RESOLVED_METHOD         "Ljdk/vm/ci/meta/ResolvedJavaMethod;"
#define FIELDINFO               "Ljdk/vm/ci/hotspot/HotSpotResolvedObjectTypeImpl$FieldInfo;"
#define HS_RESOLVED_TYPE        "Ljdk/vm/ci/hotspot/HotSpotResolvedJavaType;"
#define HS_INSTALLED_CODE       "Ljdk/vm/ci/hotspot/HotSpotInstalledCode;"
#define HS_NMETHOD              "Ljdk/vm/ci/hotspot/HotSpotNmethod;"
#define HS_COMPILED_CODE        "Ljdk/vm/ci/hotspot/HotSpotCompiledCode;"
#define HS_CONFIG               "Ljdk/vm/ci/hotspot/HotSpotVMConfig;"
#define HS_STACK_FRAME_REF      "Ljdk/vm/ci/hotspot/HotSpotStackFrameReference;"
#define HS_SPECULATION_LOG      "Ljdk/vm/ci/hotspot/HotSpotSpeculationLog;"
#define REFLECTION_EXECUTABLE   "Ljava/lang/reflect/Executable;"
#define REFLECTION_FIELD        "Ljava/lang/reflect/Field;"

// Types wrapping VM pointers. The ...2 macro is for a pair: (wrapper, pointer)
#define HS_METHOD               "Ljdk/vm/ci/hotspot/HotSpotResolvedJavaMethodImpl;"
#define HS_METHOD2              "Ljdk/vm/ci/hotspot/HotSpotResolvedJavaMethodImpl;J"
#define HS_KLASS                "Ljdk/vm/ci/hotspot/HotSpotResolvedObjectTypeImpl;"
#define HS_KLASS2               "Ljdk/vm/ci/hotspot/HotSpotResolvedObjectTypeImpl;J"
#define HS_CONSTANT_POOL        "Ljdk/vm/ci/hotspot/HotSpotConstantPool;"
#define HS_CONSTANT_POOL2       "Ljdk/vm/ci/hotspot/HotSpotConstantPool;J"

JNINativeMethod CompilerToVM::methods[] = {
  {CC "getBytecode",                                  CC "(" HS_METHOD2 ")[B",                                                              FN_PTR(getBytecode)},
  {CC "getExceptionTableStart",                       CC "(" HS_METHOD2 ")J",                                                               FN_PTR(getExceptionTableStart)},
  {CC "getExceptionTableLength",                      CC "(" HS_METHOD2 ")I",                                                               FN_PTR(getExceptionTableLength)},
  {CC "findUniqueConcreteMethod",                     CC "(" HS_KLASS2 HS_METHOD2 ")" HS_METHOD,                                            FN_PTR(findUniqueConcreteMethod)},
  {CC "getImplementor",                               CC "(" HS_KLASS2 ")" HS_KLASS,                                                        FN_PTR(getImplementor)},
  {CC "getStackTraceElement",                         CC "(" HS_METHOD2 "I)" STACK_TRACE_ELEMENT,                                           FN_PTR(getStackTraceElement)},
  {CC "methodIsIgnoredBySecurityStackWalk",           CC "(" HS_METHOD2 ")Z",                                                               FN_PTR(methodIsIgnoredBySecurityStackWalk)},
  {CC "setNotInlinableOrCompilable",                  CC "(" HS_METHOD2 ")V",                                                               FN_PTR(setNotInlinableOrCompilable)},
  {CC "isCompilable",                                 CC "(" HS_METHOD2 ")Z",                                                               FN_PTR(isCompilable)},
  {CC "hasNeverInlineDirective",                      CC "(" HS_METHOD2 ")Z",                                                               FN_PTR(hasNeverInlineDirective)},
  {CC "shouldInlineMethod",                           CC "(" HS_METHOD2 ")Z",                                                               FN_PTR(shouldInlineMethod)},
  {CC "lookupType",                                   CC "(" STRING HS_KLASS2 "IZ)" HS_RESOLVED_TYPE,                                       FN_PTR(lookupType)},
  {CC "lookupJClass",                                 CC "(J)" HS_RESOLVED_TYPE,                                                            FN_PTR(lookupJClass)},
  {CC "getArrayType",                                 CC "(C" HS_KLASS2 ")" HS_KLASS,                                                       FN_PTR(getArrayType)},
  {CC "lookupClass",                                  CC "(" CLASS ")" HS_RESOLVED_TYPE,                                                    FN_PTR(lookupClass)},
  {CC "lookupNameInPool",                             CC "(" HS_CONSTANT_POOL2 "II)" STRING,                                                FN_PTR(lookupNameInPool)},
  {CC "lookupNameAndTypeRefIndexInPool",              CC "(" HS_CONSTANT_POOL2 "II)I",                                                      FN_PTR(lookupNameAndTypeRefIndexInPool)},
  {CC "lookupSignatureInPool",                        CC "(" HS_CONSTANT_POOL2 "II)" STRING,                                                FN_PTR(lookupSignatureInPool)},
  {CC "lookupKlassRefIndexInPool",                    CC "(" HS_CONSTANT_POOL2 "II)I",                                                      FN_PTR(lookupKlassRefIndexInPool)},
  {CC "lookupKlassInPool",                            CC "(" HS_CONSTANT_POOL2 "I)Ljava/lang/Object;",                                      FN_PTR(lookupKlassInPool)},
  {CC "lookupAppendixInPool",                         CC "(" HS_CONSTANT_POOL2 "I)" OBJECTCONSTANT,                                         FN_PTR(lookupAppendixInPool)},
  {CC "lookupMethodInPool",                           CC "(" HS_CONSTANT_POOL2 "IB" HS_METHOD2 ")" HS_METHOD,                               FN_PTR(lookupMethodInPool)},
  {CC "lookupConstantInPool",                         CC "(" HS_CONSTANT_POOL2 "IZ)" JAVACONSTANT,                                          FN_PTR(lookupConstantInPool)},
  {CC "resolveBootstrapMethod",                       CC "(" HS_CONSTANT_POOL2 "I)[" OBJECT,                                                FN_PTR(resolveBootstrapMethod)},
  {CC "bootstrapArgumentIndexAt",                     CC "(" HS_CONSTANT_POOL2 "II)I",                                                      FN_PTR(bootstrapArgumentIndexAt)},
  {CC "getUncachedStringInPool",                      CC "(" HS_CONSTANT_POOL2 "I)" JAVACONSTANT,                                           FN_PTR(getUncachedStringInPool)},
  {CC "resolveTypeInPool",                            CC "(" HS_CONSTANT_POOL2 "I)" HS_KLASS,                                               FN_PTR(resolveTypeInPool)},
  {CC "resolveFieldInPool",                           CC "(" HS_CONSTANT_POOL2 "I" HS_METHOD2 "B[I)" HS_KLASS,                              FN_PTR(resolveFieldInPool)},
  {CC "decodeFieldIndexToCPIndex",                    CC "(" HS_CONSTANT_POOL2 "I)I",                                                       FN_PTR(decodeFieldIndexToCPIndex)},
  {CC "decodeMethodIndexToCPIndex",                   CC "(" HS_CONSTANT_POOL2 "I)I",                                                       FN_PTR(decodeMethodIndexToCPIndex)},
  {CC "decodeIndyIndexToCPIndex",                     CC "(" HS_CONSTANT_POOL2 "IZ)I",                                                      FN_PTR(decodeIndyIndexToCPIndex)},
  {CC "resolveInvokeHandleInPool",                    CC "(" HS_CONSTANT_POOL2 "I)V",                                                       FN_PTR(resolveInvokeHandleInPool)},
  {CC "isResolvedInvokeHandleInPool",                 CC "(" HS_CONSTANT_POOL2 "I)I",                                                       FN_PTR(isResolvedInvokeHandleInPool)},
  {CC "resolveMethod",                                CC "(" HS_KLASS2 HS_METHOD2 HS_KLASS2 ")" HS_METHOD,                                  FN_PTR(resolveMethod)},
  {CC "getSignaturePolymorphicHolders",               CC "()[" STRING,                                                                      FN_PTR(getSignaturePolymorphicHolders)},
  {CC "getVtableIndexForInterfaceMethod",             CC "(" HS_KLASS2 HS_METHOD2 ")I",                                                     FN_PTR(getVtableIndexForInterfaceMethod)},
  {CC "getClassInitializer",                          CC "(" HS_KLASS2 ")" HS_METHOD,                                                       FN_PTR(getClassInitializer)},
  {CC "hasFinalizableSubclass",                       CC "(" HS_KLASS2 ")Z",                                                                FN_PTR(hasFinalizableSubclass)},
  {CC "getMaxCallTargetOffset",                       CC "(J)J",                                                                            FN_PTR(getMaxCallTargetOffset)},
  {CC "asResolvedJavaMethod",                         CC "(" EXECUTABLE ")" HS_METHOD,                                                      FN_PTR(asResolvedJavaMethod)},
  {CC "getResolvedJavaMethod",                        CC "(" OBJECTCONSTANT "J)" HS_METHOD,                                                 FN_PTR(getResolvedJavaMethod)},
  {CC "getConstantPool",                              CC "(" OBJECT "JZ)" HS_CONSTANT_POOL,                                                 FN_PTR(getConstantPool)},
  {CC "getResolvedJavaType0",                         CC "(Ljava/lang/Object;JZ)" HS_KLASS,                                                 FN_PTR(getResolvedJavaType0)},
  {CC "readConfiguration",                            CC "()[" OBJECT,                                                                      FN_PTR(readConfiguration)},
  {CC "installCode0",                                 CC "(JJZ" HS_COMPILED_CODE "[" OBJECT INSTALLED_CODE "J[B)I",                         FN_PTR(installCode0)},
  {CC "getInstallCodeFlags",                          CC "()I",                                                                             FN_PTR(getInstallCodeFlags)},
  {CC "resetCompilationStatistics",                   CC "()V",                                                                             FN_PTR(resetCompilationStatistics)},
  {CC "disassembleCodeBlob",                          CC "(" INSTALLED_CODE ")" STRING,                                                     FN_PTR(disassembleCodeBlob)},
  {CC "executeHotSpotNmethod",                        CC "([" OBJECT HS_NMETHOD ")" OBJECT,                                                 FN_PTR(executeHotSpotNmethod)},
  {CC "getLineNumberTable",                           CC "(" HS_METHOD2 ")[J",                                                              FN_PTR(getLineNumberTable)},
  {CC "getLocalVariableTableStart",                   CC "(" HS_METHOD2 ")J",                                                               FN_PTR(getLocalVariableTableStart)},
  {CC "getLocalVariableTableLength",                  CC "(" HS_METHOD2 ")I",                                                               FN_PTR(getLocalVariableTableLength)},
  {CC "reprofile",                                    CC "(" HS_METHOD2 ")V",                                                               FN_PTR(reprofile)},
  {CC "invalidateHotSpotNmethod",                     CC "(" HS_NMETHOD "Z)V",                                                              FN_PTR(invalidateHotSpotNmethod)},
  {CC "collectCounters",                              CC "()[J",                                                                            FN_PTR(collectCounters)},
  {CC "getCountersSize",                              CC "()I",                                                                             FN_PTR(getCountersSize)},
  {CC "setCountersSize",                              CC "(I)Z",                                                                            FN_PTR(setCountersSize)},
  {CC "allocateCompileId",                            CC "(" HS_METHOD2 "I)I",                                                              FN_PTR(allocateCompileId)},
  {CC "isMature",                                     CC "(J)Z",                                                                            FN_PTR(isMature)},
  {CC "hasCompiledCodeForOSR",                        CC "(" HS_METHOD2 "II)Z",                                                             FN_PTR(hasCompiledCodeForOSR)},
  {CC "getSymbol",                                    CC "(J)" STRING,                                                                      FN_PTR(getSymbol)},
  {CC "getSignatureName",                             CC "(J)" STRING,                                                                      FN_PTR(getSignatureName)},
  {CC "iterateFrames",                                CC "([" RESOLVED_METHOD "[" RESOLVED_METHOD "I" INSPECTED_FRAME_VISITOR ")" OBJECT,   FN_PTR(iterateFrames)},
  {CC "materializeVirtualObjects",                    CC "(" HS_STACK_FRAME_REF "Z)V",                                                      FN_PTR(materializeVirtualObjects)},
  {CC "shouldDebugNonSafepoints",                     CC "()Z",                                                                             FN_PTR(shouldDebugNonSafepoints)},
  {CC "writeDebugOutput",                             CC "(JIZ)V",                                                                          FN_PTR(writeDebugOutput)},
  {CC "flushDebugOutput",                             CC "()V",                                                                             FN_PTR(flushDebugOutput)},
  {CC "methodDataProfileDataSize",                    CC "(JI)I",                                                                           FN_PTR(methodDataProfileDataSize)},
  {CC "methodDataExceptionSeen",                      CC "(JI)I",                                                                           FN_PTR(methodDataExceptionSeen)},
  {CC "interpreterFrameSize",                         CC "(" BYTECODE_FRAME ")I",                                                           FN_PTR(interpreterFrameSize)},
  {CC "compileToBytecode",                            CC "(" OBJECTCONSTANT ")V",                                                           FN_PTR(compileToBytecode)},
  {CC "getFlagValue",                                 CC "(" STRING ")" OBJECT,                                                             FN_PTR(getFlagValue)},
  {CC "getInterfaces",                                CC "(" HS_KLASS2 ")[" HS_KLASS,                                                       FN_PTR(getInterfaces)},
  {CC "getComponentType",                             CC "(" HS_KLASS2 ")" HS_RESOLVED_TYPE,                                                FN_PTR(getComponentType)},
  {CC "ensureInitialized",                            CC "(" HS_KLASS2 ")V",                                                                FN_PTR(ensureInitialized)},
  {CC "ensureLinked",                                 CC "(" HS_KLASS2 ")V",                                                                FN_PTR(ensureLinked)},
  {CC "getIdentityHashCode",                          CC "(" OBJECTCONSTANT ")I",                                                           FN_PTR(getIdentityHashCode)},
  {CC "isInternedString",                             CC "(" OBJECTCONSTANT ")Z",                                                           FN_PTR(isInternedString)},
  {CC "unboxPrimitive",                               CC "(" OBJECTCONSTANT ")" OBJECT,                                                     FN_PTR(unboxPrimitive)},
  {CC "boxPrimitive",                                 CC "(" OBJECT ")" OBJECTCONSTANT,                                                     FN_PTR(boxPrimitive)},
  {CC "getDeclaredConstructors",                      CC "(" HS_KLASS2 ")[" RESOLVED_METHOD,                                                FN_PTR(getDeclaredConstructors)},
  {CC "getDeclaredMethods",                           CC "(" HS_KLASS2 ")[" RESOLVED_METHOD,                                                FN_PTR(getDeclaredMethods)},
  {CC "getDeclaredFieldsInfo",                        CC "(" HS_KLASS2 ")[" FIELDINFO,                                                      FN_PTR(getDeclaredFieldsInfo)},
  {CC "readStaticFieldValue",                         CC "(" HS_KLASS2 "JC)" JAVACONSTANT,                                                  FN_PTR(readStaticFieldValue)},
  {CC "readFieldValue",                               CC "(" OBJECTCONSTANT HS_KLASS2 "JC)" JAVACONSTANT,                                   FN_PTR(readFieldValue)},
  {CC "isInstance",                                   CC "(" HS_KLASS2 OBJECTCONSTANT ")Z",                                                 FN_PTR(isInstance)},
  {CC "isAssignableFrom",                             CC "(" HS_KLASS2 HS_KLASS2 ")Z",                                                      FN_PTR(isAssignableFrom)},
  {CC "isTrustedForIntrinsics",                       CC "(" HS_KLASS2 ")Z",                                                                FN_PTR(isTrustedForIntrinsics)},
  {CC "asJavaType",                                   CC "(" OBJECTCONSTANT ")" HS_RESOLVED_TYPE,                                           FN_PTR(asJavaType)},
  {CC "asString",                                     CC "(" OBJECTCONSTANT ")" STRING,                                                     FN_PTR(asString)},
  {CC "equals",                                       CC "(" OBJECTCONSTANT "J" OBJECTCONSTANT "J)Z",                                       FN_PTR(equals)},
  {CC "getJavaMirror",                                CC "(" HS_KLASS2 ")" OBJECTCONSTANT,                                                  FN_PTR(getJavaMirror)},
  {CC "getArrayLength",                               CC "(" OBJECTCONSTANT ")I",                                                           FN_PTR(getArrayLength)},
  {CC "readArrayElement",                             CC "(" OBJECTCONSTANT "I)Ljava/lang/Object;",                                         FN_PTR(readArrayElement)},
  {CC "arrayBaseOffset",                              CC "(C)I",                                                                            FN_PTR(arrayBaseOffset)},
  {CC "arrayIndexScale",                              CC "(C)I",                                                                            FN_PTR(arrayIndexScale)},
  {CC "clearOopHandle",                               CC "(J)V",                                                                            FN_PTR(clearOopHandle)},
  {CC "releaseClearedOopHandles",                     CC "()V",                                                                             FN_PTR(releaseClearedOopHandles)},
  {CC "registerNativeMethods",                        CC "(" CLASS ")[J",                                                                   FN_PTR(registerNativeMethods)},
  {CC "isCurrentThreadAttached",                      CC "()Z",                                                                             FN_PTR(isCurrentThreadAttached)},
  {CC "getCurrentJavaThread",                         CC "()J",                                                                             FN_PTR(getCurrentJavaThread)},
  {CC "attachCurrentThread",                          CC "([BZ[J)Z",                                                                        FN_PTR(attachCurrentThread)},
  {CC "detachCurrentThread",                          CC "(Z)Z",                                                                            FN_PTR(detachCurrentThread)},
  {CC "translate",                                    CC "(" OBJECT "Z)J",                                                                  FN_PTR(translate)},
  {CC "unhand",                                       CC "(J)" OBJECT,                                                                      FN_PTR(unhand)},
  {CC "updateHotSpotNmethod",                         CC "(" HS_NMETHOD ")V",                                                               FN_PTR(updateHotSpotNmethod)},
  {CC "getCode",                                      CC "(" HS_INSTALLED_CODE ")[B",                                                       FN_PTR(getCode)},
  {CC "asReflectionExecutable",                       CC "(" HS_METHOD2 ")" REFLECTION_EXECUTABLE,                                          FN_PTR(asReflectionExecutable)},
  {CC "asReflectionField",                            CC "(" HS_KLASS2 "I)" REFLECTION_FIELD,                                               FN_PTR(asReflectionField)},
  {CC "getEncodedClassAnnotationData",                CC "(" HS_KLASS2 OBJECT "IJ)[B",                                                      FN_PTR(getEncodedClassAnnotationData)},
  {CC "getEncodedExecutableAnnotationData",           CC "(" HS_METHOD2 OBJECT "IJ)[B",                                                     FN_PTR(getEncodedExecutableAnnotationData)},
  {CC "getEncodedFieldAnnotationData",                CC "(" HS_KLASS2 "I" OBJECT "IJ)[B",                                                  FN_PTR(getEncodedFieldAnnotationData)},
  {CC "getFailedSpeculations",                        CC "(J[[B)[[B",                                                                       FN_PTR(getFailedSpeculations)},
  {CC "getFailedSpeculationsAddress",                 CC "(" HS_METHOD2 ")J",                                                               FN_PTR(getFailedSpeculationsAddress)},
  {CC "releaseFailedSpeculations",                    CC "(J)V",                                                                            FN_PTR(releaseFailedSpeculations)},
  {CC "addFailedSpeculation",                         CC "(J[B)Z",                                                                          FN_PTR(addFailedSpeculation)},
  {CC "callSystemExit",                               CC "(I)V",                                                                            FN_PTR(callSystemExit)},
  {CC "ticksNow",                                     CC "()J",                                                                             FN_PTR(ticksNow)},
  {CC "getThreadLocalObject",                         CC "(I)" OBJECT,                                                                      FN_PTR(getThreadLocalObject)},
  {CC "setThreadLocalObject",                         CC "(I" OBJECT ")V",                                                                  FN_PTR(setThreadLocalObject)},
  {CC "getThreadLocalLong",                           CC "(I)J",                                                                            FN_PTR(getThreadLocalLong)},
  {CC "setThreadLocalLong",                           CC "(IJ)V",                                                                           FN_PTR(setThreadLocalLong)},
  {CC "registerCompilerPhase",                        CC "(" STRING ")I",                                                                   FN_PTR(registerCompilerPhase)},
  {CC "notifyCompilerPhaseEvent",                     CC "(JIII)V",                                                                         FN_PTR(notifyCompilerPhaseEvent)},
  {CC "notifyCompilerInliningEvent",                  CC "(I" HS_METHOD2 HS_METHOD2 "ZLjava/lang/String;I)V",                               FN_PTR(notifyCompilerInliningEvent)},
  {CC "getOopMapAt",                                  CC "(" HS_METHOD2 "I[J)V",                                                            FN_PTR(getOopMapAt)},
};

int CompilerToVM::methods_count() {
  return sizeof(methods) / sizeof(JNINativeMethod);
}
