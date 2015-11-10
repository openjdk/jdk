/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/scopeDesc.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/oopFactory.hpp"
#include "oops/generateOopMap.hpp"
#include "oops/fieldStreams.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/javaCalls.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/disassembler.hpp"
#include "compiler/oopMap.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "gc/g1/heapRegion.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vframe_hp.hpp"
#include "runtime/vmStructs.hpp"


// Entry to native method implementation that transitions current thread to '_thread_in_vm'.
#define C2V_VMENTRY(result_type, name, signature) \
  JNIEXPORT result_type JNICALL c2v_ ## name signature { \
  TRACE_jvmci_1("CompilerToVM::" #name); \
  TRACE_CALL(result_type, jvmci_ ## name signature) \
  JVMCI_VM_ENTRY_MARK; \

#define C2V_END }

oop CompilerToVM::get_jvmci_method(const methodHandle& method, TRAPS) {
  if (method() != NULL) {
    JavaValue result(T_OBJECT);
    JavaCallArguments args;
    args.push_long((jlong) (address) method());
    JavaCalls::call_static(&result, SystemDictionary::HotSpotResolvedJavaMethodImpl_klass(), vmSymbols::fromMetaspace_name(), vmSymbols::method_fromMetaspace_signature(), &args, CHECK_NULL);

    return (oop)result.get_jobject();
  }
  return NULL;
}

oop CompilerToVM::get_jvmci_type(KlassHandle klass, TRAPS) {
  if (klass() != NULL) {
    JavaValue result(T_OBJECT);
    JavaCallArguments args;
    args.push_oop(klass->java_mirror());
    JavaCalls::call_static(&result, SystemDictionary::HotSpotResolvedObjectTypeImpl_klass(), vmSymbols::fromMetaspace_name(), vmSymbols::klass_fromMetaspace_signature(), &args, CHECK_NULL);

    return (oop)result.get_jobject();
  }
  return NULL;
}

void CompilerToVM::invalidate_installed_code(Handle installedCode, TRAPS) {
  if (installedCode() == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }
  jlong nativeMethod = InstalledCode::address(installedCode);
  nmethod* nm = (nmethod*)nativeMethod;
  assert(nm == NULL || nm->jvmci_installed_code() == installedCode(), "sanity check");
  if (nm != NULL && nm->is_alive()) {
    // The nmethod state machinery maintains the link between the
    // HotSpotInstalledCode and nmethod* so as long as the nmethod appears to be
    // alive assume there is work to do and deoptimize the nmethod.
    nm->mark_for_deoptimization();
    VM_Deoptimize op;
    VMThread::execute(&op);
  }
  InstalledCode::set_address(installedCode, 0);
}

extern "C" {
extern VMStructEntry* gHotSpotVMStructs;
extern uint64_t gHotSpotVMStructEntryTypeNameOffset;
extern uint64_t gHotSpotVMStructEntryFieldNameOffset;
extern uint64_t gHotSpotVMStructEntryTypeStringOffset;
extern uint64_t gHotSpotVMStructEntryIsStaticOffset;
extern uint64_t gHotSpotVMStructEntryOffsetOffset;
extern uint64_t gHotSpotVMStructEntryAddressOffset;
extern uint64_t gHotSpotVMStructEntryArrayStride;

extern VMTypeEntry* gHotSpotVMTypes;
extern uint64_t gHotSpotVMTypeEntryTypeNameOffset;
extern uint64_t gHotSpotVMTypeEntrySuperclassNameOffset;
extern uint64_t gHotSpotVMTypeEntryIsOopTypeOffset;
extern uint64_t gHotSpotVMTypeEntryIsIntegerTypeOffset;
extern uint64_t gHotSpotVMTypeEntryIsUnsignedOffset;
extern uint64_t gHotSpotVMTypeEntrySizeOffset;
extern uint64_t gHotSpotVMTypeEntryArrayStride;

extern VMIntConstantEntry* gHotSpotVMIntConstants;
extern uint64_t gHotSpotVMIntConstantEntryNameOffset;
extern uint64_t gHotSpotVMIntConstantEntryValueOffset;
extern uint64_t gHotSpotVMIntConstantEntryArrayStride;

extern VMLongConstantEntry* gHotSpotVMLongConstants;
extern uint64_t gHotSpotVMLongConstantEntryNameOffset;
extern uint64_t gHotSpotVMLongConstantEntryValueOffset;
extern uint64_t gHotSpotVMLongConstantEntryArrayStride;

extern VMAddressEntry* gHotSpotVMAddresses;
extern uint64_t gHotSpotVMAddressEntryNameOffset;
extern uint64_t gHotSpotVMAddressEntryValueOffset;
extern uint64_t gHotSpotVMAddressEntryArrayStride;
}

// FIXME This is only temporary until the GC code is changed.
bool       CompilerToVM::_supports_inline_contig_alloc;
HeapWord** CompilerToVM::_heap_end_addr;
HeapWord** CompilerToVM::_heap_top_addr;

/**
 * We put all gHotSpotVM values in an array so we can read them easily from Java.
 */
static uintptr_t ciHotSpotVMData[28];

C2V_VMENTRY(jlong, initializeConfiguration, (JNIEnv *env, jobject))
  ciHotSpotVMData[0] = (uintptr_t) gHotSpotVMStructs;
  ciHotSpotVMData[1] = gHotSpotVMStructEntryTypeNameOffset;
  ciHotSpotVMData[2] = gHotSpotVMStructEntryFieldNameOffset;
  ciHotSpotVMData[3] = gHotSpotVMStructEntryTypeStringOffset;
  ciHotSpotVMData[4] = gHotSpotVMStructEntryIsStaticOffset;
  ciHotSpotVMData[5] = gHotSpotVMStructEntryOffsetOffset;
  ciHotSpotVMData[6] = gHotSpotVMStructEntryAddressOffset;
  ciHotSpotVMData[7] = gHotSpotVMStructEntryArrayStride;

  ciHotSpotVMData[8] = (uintptr_t) gHotSpotVMTypes;
  ciHotSpotVMData[9] = gHotSpotVMTypeEntryTypeNameOffset;
  ciHotSpotVMData[10] = gHotSpotVMTypeEntrySuperclassNameOffset;
  ciHotSpotVMData[11] = gHotSpotVMTypeEntryIsOopTypeOffset;
  ciHotSpotVMData[12] = gHotSpotVMTypeEntryIsIntegerTypeOffset;
  ciHotSpotVMData[13] = gHotSpotVMTypeEntryIsUnsignedOffset;
  ciHotSpotVMData[14] = gHotSpotVMTypeEntrySizeOffset;
  ciHotSpotVMData[15] = gHotSpotVMTypeEntryArrayStride;

  ciHotSpotVMData[16] = (uintptr_t) gHotSpotVMIntConstants;
  ciHotSpotVMData[17] = gHotSpotVMIntConstantEntryNameOffset;
  ciHotSpotVMData[18] = gHotSpotVMIntConstantEntryValueOffset;
  ciHotSpotVMData[19] = gHotSpotVMIntConstantEntryArrayStride;

  ciHotSpotVMData[20] = (uintptr_t) gHotSpotVMLongConstants;
  ciHotSpotVMData[21] = gHotSpotVMLongConstantEntryNameOffset;
  ciHotSpotVMData[22] = gHotSpotVMLongConstantEntryValueOffset;
  ciHotSpotVMData[23] = gHotSpotVMLongConstantEntryArrayStride;

  ciHotSpotVMData[24] = (uintptr_t) gHotSpotVMAddresses;
  ciHotSpotVMData[25] = gHotSpotVMAddressEntryNameOffset;
  ciHotSpotVMData[26] = gHotSpotVMAddressEntryValueOffset;
  ciHotSpotVMData[27] = gHotSpotVMAddressEntryArrayStride;

  // FIXME This is only temporary until the GC code is changed.
  CompilerToVM::_supports_inline_contig_alloc = Universe::heap()->supports_inline_contig_alloc();
  CompilerToVM::_heap_end_addr = CompilerToVM::_supports_inline_contig_alloc ? Universe::heap()->end_addr() : (HeapWord**) -1;
  CompilerToVM::_heap_top_addr = CompilerToVM::_supports_inline_contig_alloc ? Universe::heap()->top_addr() : (HeapWord**) -1;

  return (jlong) (address) &ciHotSpotVMData;
C2V_END

C2V_VMENTRY(jbyteArray, getBytecode, (JNIEnv *, jobject, jobject jvmci_method))
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  ResourceMark rm;

  int code_size = method->code_size();
  typeArrayOop reconstituted_code = oopFactory::new_byteArray(code_size, CHECK_NULL);

  guarantee(method->method_holder()->is_rewritten(), "Method's holder should be rewritten");
  // iterate over all bytecodes and replace non-Java bytecodes

  for (BytecodeStream s(method); s.next() != Bytecodes::_illegal; ) {
    Bytecodes::Code code = s.code();
    Bytecodes::Code raw_code = s.raw_code();
    int bci = s.bci();
    int len = s.instruction_size();

    // Restore original byte code.
    reconstituted_code->byte_at_put(bci, (jbyte) (s.is_wide()? Bytecodes::_wide : code));
    if (len > 1) {
      memcpy(reconstituted_code->byte_at_addr(bci + 1), s.bcp()+1, len-1);
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
          int cp_index = Bytes::get_native_u2((address) reconstituted_code->byte_at_addr(bci + 1));
          Bytes::put_Java_u2((address) reconstituted_code->byte_at_addr(bci + 1), (u2) cp_index);
          break;
        }

        case Bytecodes::_invokedynamic:
          int cp_index = Bytes::get_native_u4((address) reconstituted_code->byte_at_addr(bci + 1));
          Bytes::put_Java_u4((address) reconstituted_code->byte_at_addr(bci + 1), (u4) cp_index);
          break;
      }

      // Not all ldc byte code are rewritten.
      switch (raw_code) {
        case Bytecodes::_fast_aldc: {
          int cpc_index = reconstituted_code->byte_at(bci + 1) & 0xff;
          int cp_index = method->constants()->object_to_cp_index(cpc_index);
          assert(cp_index < method->constants()->length(), "sanity check");
          reconstituted_code->byte_at_put(bci + 1, (jbyte) cp_index);
          break;
        }

        case Bytecodes::_fast_aldc_w: {
          int cpc_index = Bytes::get_native_u2((address) reconstituted_code->byte_at_addr(bci + 1));
          int cp_index = method->constants()->object_to_cp_index(cpc_index);
          assert(cp_index < method->constants()->length(), "sanity check");
          Bytes::put_Java_u2((address) reconstituted_code->byte_at_addr(bci + 1), (u2) cp_index);
          break;
        }
      }
    }
  }

  return (jbyteArray) JNIHandles::make_local(THREAD, reconstituted_code);
C2V_END

C2V_VMENTRY(jint, getExceptionTableLength, (JNIEnv *, jobject, jobject jvmci_method))
  ResourceMark rm;
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  return method->exception_table_length();
C2V_END

C2V_VMENTRY(jlong, getExceptionTableStart, (JNIEnv *, jobject, jobject jvmci_method))
  ResourceMark rm;
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  if (method->exception_table_length() == 0) {
    return 0L;
  }
  return (jlong) (address) method->exception_table_start();
C2V_END

C2V_VMENTRY(jobject, getResolvedJavaMethodAtSlot, (JNIEnv *, jobject, jclass holder_handle, jint slot))
  oop java_class = JNIHandles::resolve(holder_handle);
  Klass* holder = java_lang_Class::as_Klass(java_class);
  methodHandle method = InstanceKlass::cast(holder)->method_with_idnum(slot);
  oop result = CompilerToVM::get_jvmci_method(method, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
}

C2V_VMENTRY(jobject, getResolvedJavaMethod, (JNIEnv *, jobject, jobject base, jlong offset))
  methodHandle method;
  oop base_object = JNIHandles::resolve(base);
  if (base_object == NULL) {
    method = *((Method**)(offset));
  } else if (base_object->is_a(SystemDictionary::MemberName_klass())) {
    method = (Method*) (intptr_t) base_object->long_field(offset);
  } else if (base_object->is_a(SystemDictionary::HotSpotResolvedJavaMethodImpl_klass())) {
    method = *((Method**)(HotSpotResolvedJavaMethodImpl::metaspaceMethod(base_object) + offset));
  } else {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Unexpected type: %s", base_object->klass()->external_name()));
  }
  assert (method.is_null() || method->is_method(), "invalid read");
  oop result = CompilerToVM::get_jvmci_method(method, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
}

C2V_VMENTRY(jobject, getConstantPool, (JNIEnv *, jobject, jobject base, jlong offset))
  constantPoolHandle cp;
  oop base_object = JNIHandles::resolve(base);
  jlong base_address = 0;
  if (base_object != NULL) {
    if (base_object->is_a(SystemDictionary::HotSpotResolvedJavaMethodImpl_klass())) {
      base_address = HotSpotResolvedJavaMethodImpl::metaspaceMethod(base_object);
    } else if (base_object->is_a(SystemDictionary::HotSpotConstantPool_klass())) {
      base_address = HotSpotConstantPool::metaspaceConstantPool(base_object);
    } else if (base_object->is_a(SystemDictionary::HotSpotResolvedObjectTypeImpl_klass())) {
      base_address = (jlong) CompilerToVM::asKlass(base_object);
    } else {
      THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                  err_msg("Unexpected type: %s", base_object->klass()->external_name()));
    }
  }
  cp = *((ConstantPool**) (intptr_t) (base_address + offset));
  if (!cp.is_null()) {
    JavaValue method_result(T_OBJECT);
    JavaCallArguments args;
    args.push_long((jlong) (address) cp());
    JavaCalls::call_static(&method_result, SystemDictionary::HotSpotConstantPool_klass(), vmSymbols::fromMetaspace_name(), vmSymbols::constantPool_fromMetaspace_signature(), &args, CHECK_NULL);
    return JNIHandles::make_local(THREAD, (oop)method_result.get_jobject());
  }
  return NULL;
}

C2V_VMENTRY(jobject, getResolvedJavaType, (JNIEnv *, jobject, jobject base, jlong offset, jboolean compressed))
  KlassHandle klass;
  oop base_object = JNIHandles::resolve(base);
  jlong base_address = 0;
  if (base_object != NULL && offset == oopDesc::klass_offset_in_bytes()) {
    klass = base_object->klass();
  } else if (!compressed) {
    if (base_object != NULL) {
      if (base_object->is_a(SystemDictionary::HotSpotResolvedJavaMethodImpl_klass())) {
        base_address = HotSpotResolvedJavaMethodImpl::metaspaceMethod(base_object);
      } else if (base_object->is_a(SystemDictionary::HotSpotConstantPool_klass())) {
        base_address = HotSpotConstantPool::metaspaceConstantPool(base_object);
      } else if (base_object->is_a(SystemDictionary::HotSpotResolvedObjectTypeImpl_klass())) {
        base_address = (jlong) CompilerToVM::asKlass(base_object);
      } else if (base_object->is_a(SystemDictionary::Class_klass())) {
        base_address = (jlong) (address) base_object;
      } else {
        THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                    err_msg("Unexpected arguments: %s " JLONG_FORMAT " %s", base_object->klass()->external_name(), offset, compressed ? "true" : "false"));
      }
    }
    klass = *((Klass**) (intptr_t) (base_address + offset));
  } else {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Unexpected arguments: %s " JLONG_FORMAT " %s", base_object->klass()->external_name(), offset, compressed ? "true" : "false"));
  }
  assert (klass.is_null() || klass->is_klass(), "invalid read");
  oop result = CompilerToVM::get_jvmci_type(klass, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
}

C2V_VMENTRY(jobject, findUniqueConcreteMethod, (JNIEnv *, jobject, jobject jvmci_type, jobject jvmci_method))
  ResourceMark rm;
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  KlassHandle holder = CompilerToVM::asKlass(jvmci_type);
  if (holder->is_interface()) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), err_msg("Interface %s should be handled in Java code", holder->external_name()));
  }

  methodHandle ucm;
  {
    MutexLocker locker(Compile_lock);
    ucm = Dependencies::find_unique_concrete_method(holder(), method());
  }
  oop result = CompilerToVM::get_jvmci_method(ucm, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
C2V_END

C2V_VMENTRY(jobject, getImplementor, (JNIEnv *, jobject, jobject jvmci_type))
  InstanceKlass* klass = (InstanceKlass*) CompilerToVM::asKlass(jvmci_type);
  oop implementor = CompilerToVM::get_jvmci_type(klass->implementor(), CHECK_NULL);
  return JNIHandles::make_local(THREAD, implementor);
C2V_END

C2V_VMENTRY(jboolean, methodIsIgnoredBySecurityStackWalk,(JNIEnv *, jobject, jobject jvmci_method))
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  return method->is_ignored_by_security_stack_walk();
C2V_END

C2V_VMENTRY(jboolean, canInlineMethod,(JNIEnv *, jobject, jobject jvmci_method))
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  return !method->is_not_compilable() && !CompilerOracle::should_not_inline(method) && !method->dont_inline();
C2V_END

C2V_VMENTRY(jboolean, shouldInlineMethod,(JNIEnv *, jobject, jobject jvmci_method))
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  return CompilerOracle::should_inline(method) || method->force_inline();
C2V_END

C2V_VMENTRY(jobject, lookupType, (JNIEnv*, jobject, jstring jname, jclass accessing_class, jboolean resolve))
  ResourceMark rm;
  Handle name = JNIHandles::resolve(jname);
  Symbol* class_name = java_lang_String::as_symbol(name, CHECK_0);
  if (java_lang_String::length(name()) <= 1) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), err_msg("Primitive type %s should be handled in Java code", class_name->as_C_string()));
  }

  Klass* resolved_klass = NULL;
  Handle class_loader;
  Handle protection_domain;
  if (JNIHandles::resolve(accessing_class) == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  Klass* accessing_klass = java_lang_Class::as_Klass(JNIHandles::resolve(accessing_class));
  class_loader = accessing_klass->class_loader();
  protection_domain = accessing_klass->protection_domain();

  if (resolve) {
    resolved_klass = SystemDictionary::resolve_or_null(class_name, class_loader, protection_domain, CHECK_0);
  } else {
    if (class_name->byte_at(0) == 'L' &&
      class_name->byte_at(class_name->utf8_length()-1) == ';') {
      // This is a name from a signature.  Strip off the trimmings.
      // Call recursive to keep scope of strippedsym.
      TempNewSymbol strippedsym = SymbolTable::new_symbol(class_name->as_utf8()+1,
                                                          class_name->utf8_length()-2,
                                                          CHECK_0);
      resolved_klass = SystemDictionary::find(strippedsym, class_loader, protection_domain, CHECK_0);
    } else if (FieldType::is_array(class_name)) {
      FieldArrayInfo fd;
      // dimension and object_key in FieldArrayInfo are assigned as a side-effect
      // of this call
      BasicType t = FieldType::get_array_info(class_name, fd, CHECK_0);
      if (t == T_OBJECT) {
        TempNewSymbol strippedsym = SymbolTable::new_symbol(class_name->as_utf8()+1+fd.dimension(),
                                                            class_name->utf8_length()-2-fd.dimension(),
                                                            CHECK_0);
        // naked oop "k" is OK here -- we assign back into it
        resolved_klass = SystemDictionary::find(strippedsym,
                                                             class_loader,
                                                             protection_domain,
                                                             CHECK_0);
        if (resolved_klass != NULL) {
          resolved_klass = resolved_klass->array_klass(fd.dimension(), CHECK_0);
        }
      } else {
        resolved_klass = Universe::typeArrayKlassObj(t);
        resolved_klass = TypeArrayKlass::cast(resolved_klass)->array_klass(fd.dimension(), CHECK_0);
      }
    }
  }
  Handle result = CompilerToVM::get_jvmci_type(resolved_klass, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result());
C2V_END

C2V_VMENTRY(jobject, resolveConstantInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  oop result = cp->resolve_constant_at(index, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
C2V_END

C2V_VMENTRY(jobject, resolvePossiblyCachedConstantInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  oop result = cp->resolve_possibly_cached_constant_at(index, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
C2V_END

C2V_VMENTRY(jint, lookupNameAndTypeRefIndexInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  return cp->name_and_type_ref_index_at(index);
C2V_END

C2V_VMENTRY(jobject, lookupNameInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint which))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  Handle sym = java_lang_String::create_from_symbol(cp->name_ref_at(which), CHECK_NULL);
  return JNIHandles::make_local(THREAD, sym());
C2V_END

C2V_VMENTRY(jobject, lookupSignatureInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint which))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  Handle sym = java_lang_String::create_from_symbol(cp->signature_ref_at(which), CHECK_NULL);
  return JNIHandles::make_local(THREAD, sym());
C2V_END

C2V_VMENTRY(jint, lookupKlassRefIndexInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  return cp->klass_ref_index_at(index);
C2V_END

C2V_VMENTRY(jobject, resolveTypeInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  Klass* resolved_klass = cp->klass_at(index, CHECK_NULL);
  Handle klass = CompilerToVM::get_jvmci_type(resolved_klass, CHECK_NULL);
  return JNIHandles::make_local(THREAD, klass());
C2V_END

C2V_VMENTRY(jobject, lookupKlassInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index, jbyte opcode))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  KlassHandle loading_klass(cp->pool_holder());
  bool is_accessible = false;
  KlassHandle klass = JVMCIEnv::get_klass_by_index(cp, index, is_accessible, loading_klass);
  Symbol* symbol = NULL;
  if (klass.is_null()) {
    symbol = cp->klass_name_at(index);
  }
  Handle result;
  if (!klass.is_null()) {
    result = CompilerToVM::get_jvmci_type(klass, CHECK_NULL);
  } else {
    result = java_lang_String::create_from_symbol(symbol, CHECK_NULL);
  }
  return JNIHandles::make_local(THREAD, result());
C2V_END

C2V_VMENTRY(jobject, lookupAppendixInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  oop appendix_oop = ConstantPool::appendix_at_if_loaded(cp, index);
  return JNIHandles::make_local(THREAD, appendix_oop);
C2V_END

C2V_VMENTRY(jobject, lookupMethodInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index, jbyte opcode))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  instanceKlassHandle pool_holder(cp->pool_holder());
  Bytecodes::Code bc = (Bytecodes::Code) (((int) opcode) & 0xFF);
  methodHandle method = JVMCIEnv::get_method_by_index(cp, index, bc, pool_holder);
  oop result = CompilerToVM::get_jvmci_method(method, CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
C2V_END

C2V_VMENTRY(jint, constantPoolRemapInstructionOperandFromCache, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  return cp->remap_instruction_operand_from_cache(index);
C2V_END

C2V_VMENTRY(jobject, resolveFieldInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index, jbyte opcode, jlongArray info_handle))
  ResourceMark rm;
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  Bytecodes::Code code = (Bytecodes::Code)(((int) opcode) & 0xFF);
  fieldDescriptor fd;
  LinkInfo link_info(cp, index, CHECK_0);
  LinkResolver::resolve_field(fd, link_info, Bytecodes::java_code(code), false, CHECK_0);
  typeArrayOop info = (typeArrayOop) JNIHandles::resolve(info_handle);
  assert(info != NULL && info->length() == 2, "must be");
  info->long_at_put(0, (jlong) fd.access_flags().as_int());
  info->long_at_put(1, (jlong) fd.offset());
  oop field_holder = CompilerToVM::get_jvmci_type(fd.field_holder(), CHECK_NULL);
  return JNIHandles::make_local(THREAD, field_holder);
C2V_END

C2V_VMENTRY(jint, getVtableIndexForInterfaceMethod, (JNIEnv *, jobject, jobject jvmci_type, jobject jvmci_method))
  ResourceMark rm;
  Klass* klass = CompilerToVM::asKlass(jvmci_type);
  Method* method = CompilerToVM::asMethod(jvmci_method);
  if (klass->is_interface()) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), err_msg("Interface %s should be handled in Java code", klass->external_name()));
  }
  if (!method->method_holder()->is_interface()) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), err_msg("Method %s is not held by an interface, this case should be handled in Java code", method->name_and_sig_as_C_string()));
  }
  if (!InstanceKlass::cast(klass)->is_linked()) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), err_msg("Class %s must be linked", klass->external_name()));
  }
  return LinkResolver::vtable_index_of_interface_method(klass, method);
C2V_END

C2V_VMENTRY(jobject, resolveMethod, (JNIEnv *, jobject, jobject receiver_jvmci_type, jobject jvmci_method, jobject caller_jvmci_type))
  Klass* recv_klass = CompilerToVM::asKlass(receiver_jvmci_type);
  Klass* caller_klass = CompilerToVM::asKlass(caller_jvmci_type);
  Method* method = CompilerToVM::asMethod(jvmci_method);

  if (recv_klass->oop_is_array() || (InstanceKlass::cast(recv_klass)->is_linked())) {
    Klass* holder_klass = method->method_holder();
    Symbol* method_name = method->name();
    Symbol* method_signature = method->signature();

    if (holder_klass->is_interface()) {
      // do link-time resolution to check all access rules.
      LinkInfo link_info(holder_klass, method_name, method_signature, caller_klass, true);
      methodHandle resolved_method = LinkResolver::linktime_resolve_interface_method_or_null(link_info);
      if (resolved_method.is_null() || resolved_method->is_private()) {
        return NULL;
      }
      assert(recv_klass->is_subtype_of(holder_klass), "");
      // do actual lookup
      methodHandle sel_method = LinkResolver::lookup_instance_method_in_klasses(recv_klass, resolved_method->name(), resolved_method->signature(), CHECK_AND_CLEAR_0);
      oop result = CompilerToVM::get_jvmci_method(sel_method, CHECK_NULL);
      return JNIHandles::make_local(THREAD, result);
    } else {
      // do link-time resolution to check all access rules.
      LinkInfo link_info(holder_klass, method_name, method_signature, caller_klass, true);
      methodHandle resolved_method = LinkResolver::linktime_resolve_virtual_method_or_null(link_info);
      if (resolved_method.is_null()) {
        return NULL;
      }
      // do actual lookup (see LinkResolver::runtime_resolve_virtual_method)
      int vtable_index = Method::invalid_vtable_index;
      Method* selected_method;

      if (resolved_method->method_holder()->is_interface()) { // miranda method
        vtable_index = LinkResolver::vtable_index_of_interface_method(holder_klass, resolved_method);
        assert(vtable_index >= 0 , "we should have valid vtable index at this point");

        InstanceKlass* inst = InstanceKlass::cast(recv_klass);
        selected_method = inst->method_at_vtable(vtable_index);
      } else {
        // at this point we are sure that resolved_method is virtual and not
        // a miranda method; therefore, it must have a valid vtable index.
        assert(!resolved_method->has_itable_index(), "");
        vtable_index = resolved_method->vtable_index();
        // We could get a negative vtable_index for final methods,
        // because as an optimization they are they are never put in the vtable,
        // unless they override an existing method.
        // If we do get a negative, it means the resolved method is the the selected
        // method, and it can never be changed by an override.
        if (vtable_index == Method::nonvirtual_vtable_index) {
          assert(resolved_method->can_be_statically_bound(), "cannot override this method");
          selected_method = resolved_method();
        } else {
          // recv_klass might be an arrayKlassOop but all vtables start at
          // the same place. The cast is to avoid virtual call and assertion.
          InstanceKlass* inst = (InstanceKlass*)recv_klass;
          selected_method = inst->method_at_vtable(vtable_index);
        }
      }
      oop result = CompilerToVM::get_jvmci_method(selected_method, CHECK_NULL);
      return JNIHandles::make_local(THREAD, result);
    }
  }
  return NULL;
C2V_END

C2V_VMENTRY(jboolean, hasFinalizableSubclass,(JNIEnv *, jobject, jobject jvmci_type))
  Klass* klass = CompilerToVM::asKlass(jvmci_type);
  assert(klass != NULL, "method must not be called for primitive types");
  return Dependencies::find_finalizable_subclass(klass) != NULL;
C2V_END

C2V_VMENTRY(jobject, getClassInitializer, (JNIEnv *, jobject, jobject jvmci_type))
  InstanceKlass* klass = (InstanceKlass*) CompilerToVM::asKlass(jvmci_type);
  oop result = CompilerToVM::get_jvmci_method(klass->class_initializer(), CHECK_NULL);
  return JNIHandles::make_local(THREAD, result);
C2V_END

C2V_VMENTRY(jlong, getMaxCallTargetOffset, (JNIEnv*, jobject, jlong addr))
  address target_addr = (address) addr;
  if (target_addr != 0x0) {
    int64_t off_low = (int64_t)target_addr - ((int64_t)CodeCache::low_bound() + sizeof(int));
    int64_t off_high = (int64_t)target_addr - ((int64_t)CodeCache::high_bound() + sizeof(int));
    return MAX2(ABS(off_low), ABS(off_high));
  }
  return -1;
C2V_END

C2V_VMENTRY(void, doNotInlineOrCompile,(JNIEnv *, jobject,  jobject jvmci_method))
  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  method->set_not_c1_compilable();
  method->set_not_c2_compilable();
  method->set_dont_inline(true);
C2V_END

C2V_VMENTRY(jint, installCode, (JNIEnv *jniEnv, jobject, jobject target, jobject compiled_code, jobject installed_code, jobject speculation_log))
  ResourceMark rm;
  HandleMark hm;
  Handle target_handle = JNIHandles::resolve(target);
  Handle compiled_code_handle = JNIHandles::resolve(compiled_code);
  CodeBlob* cb = NULL;
  Handle installed_code_handle = JNIHandles::resolve(installed_code);
  Handle speculation_log_handle = JNIHandles::resolve(speculation_log);

  JVMCICompiler* compiler = JVMCICompiler::instance(CHECK_JNI_ERR);

  TraceTime install_time("installCode", JVMCICompiler::codeInstallTimer());
  CodeInstaller installer;
  JVMCIEnv::CodeInstallResult result = installer.install(compiler, target_handle, compiled_code_handle, cb, installed_code_handle, speculation_log_handle, CHECK_0);

  if (PrintCodeCacheOnCompilation) {
    stringStream s;
    // Dump code cache  into a buffer before locking the tty,
    {
      MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      CodeCache::print_summary(&s, false);
    }
    ttyLocker ttyl;
    tty->print_raw_cr(s.as_string());
  }

  if (result != JVMCIEnv::ok) {
    assert(cb == NULL, "should be");
  } else {
    if (!installed_code_handle.is_null()) {
      assert(installed_code_handle->is_a(InstalledCode::klass()), "wrong type");
      CompilerToVM::invalidate_installed_code(installed_code_handle, CHECK_0);
      InstalledCode::set_address(installed_code_handle, (jlong) cb);
      if (cb->is_nmethod()) {
        InstalledCode::set_entryPoint(installed_code_handle, (jlong) cb->as_nmethod_or_null()->verified_entry_point());
      } else {
        InstalledCode::set_entryPoint(installed_code_handle, (jlong) cb->code_begin());
      }
      if (installed_code_handle->is_a(HotSpotInstalledCode::klass())) {
        HotSpotInstalledCode::set_size(installed_code_handle, cb->size());
        HotSpotInstalledCode::set_codeStart(installed_code_handle, (jlong) cb->code_begin());
        HotSpotInstalledCode::set_codeSize(installed_code_handle, cb->code_size());
      }
      nmethod* nm = cb->as_nmethod_or_null();
      if (nm != NULL && installed_code_handle->is_scavengable()) {
        assert(nm->detect_scavenge_root_oops(), "nm should be scavengable if installed_code is scavengable");
        if (!UseG1GC) {
          assert(nm->on_scavenge_root_list(), "nm should be on scavengable list");
        }
      }
    }
  }
  return result;
C2V_END

C2V_VMENTRY(jint, getMetadata, (JNIEnv *jniEnv, jobject, jobject target, jobject compiled_code, jobject metadata))
  ResourceMark rm;
  HandleMark hm;

  Handle target_handle = JNIHandles::resolve(target);
  Handle compiled_code_handle = JNIHandles::resolve(compiled_code);
  Handle metadata_handle = JNIHandles::resolve(metadata);

  HotSpotOopMap::klass()->initialize(thread);

  CodeMetadata code_metadata;
  CodeBlob *cb = NULL;
  CodeInstaller installer;

  JVMCIEnv::CodeInstallResult result = installer.gather_metadata(target_handle, compiled_code_handle, code_metadata, CHECK_0); //cb, pc_descs, nr_pc_descs, scopes_descs, scopes_size, reloc_buffer);
  if (result != JVMCIEnv::ok) {
    return result;
  }

  if (code_metadata.get_nr_pc_desc() > 0) {
    typeArrayHandle pcArrayOop = oopFactory::new_byteArray(sizeof(PcDesc) * code_metadata.get_nr_pc_desc(), CHECK_(JVMCIEnv::cache_full));
    memcpy(pcArrayOop->byte_at_addr(0), code_metadata.get_pc_desc(), sizeof(PcDesc) * code_metadata.get_nr_pc_desc());
    HotSpotMetaData::set_pcDescBytes(metadata_handle, pcArrayOop());
  }

  if (code_metadata.get_scopes_size() > 0) {
    typeArrayHandle scopesArrayOop = oopFactory::new_byteArray(code_metadata.get_scopes_size(), CHECK_(JVMCIEnv::cache_full));
    memcpy(scopesArrayOop->byte_at_addr(0), code_metadata.get_scopes_desc(), code_metadata.get_scopes_size());
    HotSpotMetaData::set_scopesDescBytes(metadata_handle, scopesArrayOop());
  }

  RelocBuffer* reloc_buffer = code_metadata.get_reloc_buffer();
  typeArrayHandle relocArrayOop = oopFactory::new_byteArray((int) reloc_buffer->size(), CHECK_(JVMCIEnv::cache_full));
  if (reloc_buffer->size() > 0) {
    memcpy(relocArrayOop->byte_at_addr(0), reloc_buffer->begin(), reloc_buffer->size());
  }
  HotSpotMetaData::set_relocBytes(metadata_handle, relocArrayOop());

  const OopMapSet* oopMapSet = installer.oopMapSet();
  {
    ResourceMark mark;
    ImmutableOopMapBuilder builder(oopMapSet);
    int oopmap_size = builder.heap_size();
    typeArrayHandle oopMapArrayHandle = oopFactory::new_byteArray(oopmap_size, CHECK_(JVMCIEnv::cache_full));
    builder.generate_into((address) oopMapArrayHandle->byte_at_addr(0));
    HotSpotMetaData::set_oopMaps(metadata_handle, oopMapArrayHandle());
  }

  HotSpotMetaData::set_metadata(metadata_handle, NULL);

  ExceptionHandlerTable* handler = code_metadata.get_exception_table();
  int table_size = handler->size_in_bytes();
  typeArrayHandle exceptionArrayOop = oopFactory::new_byteArray(table_size, CHECK_(JVMCIEnv::cache_full));

  if (table_size > 0) {
    handler->copy_bytes_to((address) exceptionArrayOop->byte_at_addr(0));
  }
  HotSpotMetaData::set_exceptionBytes(metadata_handle, exceptionArrayOop());

  return result;
C2V_END

C2V_VMENTRY(void, notifyCompilationStatistics, (JNIEnv *jniEnv, jobject, jint id, jobject hotspot_method, jboolean osr, jint processedBytecodes, jlong time, jlong timeUnitsPerSecond, jobject installed_code))
  JVMCICompiler* compiler = JVMCICompiler::instance(CHECK);
  CompilerStatistics* stats = compiler->stats();

  elapsedTimer timer = elapsedTimer(time, timeUnitsPerSecond);
  if (osr) {
    stats->_osr.update(timer, processedBytecodes);
  } else {
    stats->_standard.update(timer, processedBytecodes);
  }
  Handle installed_code_handle = JNIHandles::resolve(installed_code);
  if (installed_code_handle->is_a(HotSpotInstalledCode::klass())) {
    stats->_nmethods_size += HotSpotInstalledCode::size(installed_code_handle);
    stats->_nmethods_code_size += HotSpotInstalledCode::codeSize(installed_code_handle);
  }

  if (CITimeEach) {
    methodHandle method = CompilerToVM::asMethod(hotspot_method);
    float bytes_per_sec = 1.0 * processedBytecodes / timer.seconds();
    tty->print_cr("%3d   seconds: %f bytes/sec: %f (bytes %d)",
                  id, timer.seconds(), bytes_per_sec, processedBytecodes);
  }
C2V_END

C2V_VMENTRY(void, resetCompilationStatistics, (JNIEnv *jniEnv, jobject))
  JVMCICompiler* compiler = JVMCICompiler::instance(CHECK);
  CompilerStatistics* stats = compiler->stats();
  stats->_standard.reset();
  stats->_osr.reset();
C2V_END

C2V_VMENTRY(jobject, disassembleCodeBlob, (JNIEnv *jniEnv, jobject, jobject installedCode))
  ResourceMark rm;
  HandleMark hm;

  if (installedCode == NULL) {
    THROW_MSG_NULL(vmSymbols::java_lang_NullPointerException(), "installedCode is null");
  }

  jlong codeBlob = InstalledCode::address(installedCode);
  if (codeBlob == 0L) {
    return NULL;
  }

  CodeBlob* cb = (CodeBlob*) (address) codeBlob;
  if (cb == NULL) {
    return NULL;
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
  if (cb->is_nmethod()) {
    nmethod* nm = (nmethod*) cb;
    if (!nm->is_alive()) {
      return NULL;
    }
    Disassembler::decode(nm, &st);
  } else {
    Disassembler::decode(cb, &st);
  }
  if (st.size() <= 0) {
    return NULL;
  }

  Handle result = java_lang_String::create_from_platform_dependent_str(st.as_string(), CHECK_NULL);
  return JNIHandles::make_local(THREAD, result());
C2V_END

C2V_VMENTRY(jobject, getStackTraceElement, (JNIEnv*, jobject, jobject jvmci_method, int bci))
  ResourceMark rm;
  HandleMark hm;

  methodHandle method = CompilerToVM::asMethod(jvmci_method);
  oop element = java_lang_StackTraceElement::create(method, bci, CHECK_NULL);
  return JNIHandles::make_local(THREAD, element);
C2V_END

C2V_VMENTRY(jobject, executeInstalledCode, (JNIEnv*, jobject, jobject args, jobject hotspotInstalledCode))
  ResourceMark rm;
  HandleMark hm;

  jlong nmethodValue = InstalledCode::address(hotspotInstalledCode);
  if (nmethodValue == 0L) {
    THROW_NULL(vmSymbols::jdk_vm_ci_code_InvalidInstalledCodeException());
  }
  nmethod* nm = (nmethod*) (address) nmethodValue;
  methodHandle mh = nm->method();
  Symbol* signature = mh->signature();
  JavaCallArguments jca(mh->size_of_parameters());

  JavaArgumentUnboxer jap(signature, &jca, (arrayOop) JNIHandles::resolve(args), mh->is_static());
  JavaValue result(jap.get_ret_type());
  jca.set_alternative_target(nm);
  JavaCalls::call(&result, mh, &jca, CHECK_NULL);

  if (jap.get_ret_type() == T_VOID) {
    return NULL;
  } else if (jap.get_ret_type() == T_OBJECT || jap.get_ret_type() == T_ARRAY) {
    return JNIHandles::make_local(THREAD, (oop) result.get_jobject());
  } else {
    jvalue *value = (jvalue *) result.get_value_addr();
    // Narrow the value down if required (Important on big endian machines)
    switch (jap.get_ret_type()) {
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
     }
    oop o = java_lang_boxing_object::create(jap.get_ret_type(), value, CHECK_NULL);
    return JNIHandles::make_local(THREAD, o);
  }
C2V_END

C2V_VMENTRY(jlongArray, getLineNumberTable, (JNIEnv *, jobject, jobject jvmci_method))
  Method* method = CompilerToVM::asMethod(jvmci_method);
  if (!method->has_linenumber_table()) {
    return NULL;
  }
  u2 num_entries = 0;
  CompressedLineNumberReadStream streamForSize(method->compressed_linenumber_table());
  while (streamForSize.read_pair()) {
    num_entries++;
  }

  CompressedLineNumberReadStream stream(method->compressed_linenumber_table());
  typeArrayOop result = oopFactory::new_longArray(2 * num_entries, CHECK_NULL);

  int i = 0;
  jlong value;
  while (stream.read_pair()) {
    value = ((long) stream.bci());
    result->long_at_put(i, value);
    value = ((long) stream.line());
    result->long_at_put(i + 1, value);
    i += 2;
  }

  return (jlongArray) JNIHandles::make_local(THREAD, result);
C2V_END

C2V_VMENTRY(jlong, getLocalVariableTableStart, (JNIEnv *, jobject, jobject jvmci_method))
  ResourceMark rm;
  Method* method = CompilerToVM::asMethod(jvmci_method);
  if (!method->has_localvariable_table()) {
    return 0;
  }
  return (jlong) (address) method->localvariable_table_start();
C2V_END

C2V_VMENTRY(jint, getLocalVariableTableLength, (JNIEnv *, jobject, jobject jvmci_method))
  ResourceMark rm;
  Method* method = CompilerToVM::asMethod(jvmci_method);
  return method->localvariable_table_length();
C2V_END

C2V_VMENTRY(void, reprofile, (JNIEnv*, jobject, jobject jvmci_method))
  Method* method = CompilerToVM::asMethod(jvmci_method);
  MethodCounters* mcs = method->method_counters();
  if (mcs != NULL) {
    mcs->clear_counters();
  }
  NOT_PRODUCT(method->set_compiled_invocation_count(0));

  nmethod* code = method->code();
  if (code != NULL) {
    code->make_not_entrant();
  }

  MethodData* method_data = method->method_data();
  if (method_data == NULL) {
    ClassLoaderData* loader_data = method->method_holder()->class_loader_data();
    method_data = MethodData::allocate(loader_data, method, CHECK);
    method->set_method_data(method_data);
  } else {
    method_data->initialize();
  }
C2V_END


C2V_VMENTRY(void, invalidateInstalledCode, (JNIEnv*, jobject, jobject installed_code))
  Handle installed_code_handle = JNIHandles::resolve(installed_code);
  CompilerToVM::invalidate_installed_code(installed_code_handle, CHECK);
C2V_END

C2V_VMENTRY(jobject, readUncompressedOop, (JNIEnv*, jobject, jlong addr))
  oop ret = oopDesc::load_decode_heap_oop((oop*)(address)addr);
  return JNIHandles::make_local(THREAD, ret);
C2V_END

C2V_VMENTRY(jlongArray, collectCounters, (JNIEnv*, jobject))
  typeArrayOop arrayOop = oopFactory::new_longArray(JVMCICounterSize, CHECK_NULL);
  JavaThread::collect_counters(arrayOop);
  return (jlongArray) JNIHandles::make_local(THREAD, arrayOop);
C2V_END

C2V_VMENTRY(int, allocateCompileId, (JNIEnv*, jobject, jobject jvmci_method, int entry_bci))
  HandleMark hm;
  ResourceMark rm;
  if (JNIHandles::resolve(jvmci_method) == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  Method* method = CompilerToVM::asMethod(jvmci_method);
  if (entry_bci >= method->code_size() || entry_bci < -1) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), err_msg("Unexpected bci %d", entry_bci));
  }
  return CompileBroker::assign_compile_id_unlocked(THREAD, method, entry_bci);
C2V_END


C2V_VMENTRY(jboolean, isMature, (JNIEnv*, jobject, jlong metaspace_method_data))
  MethodData* mdo = CompilerToVM::asMethodData(metaspace_method_data);
  return mdo != NULL && mdo->is_mature();
C2V_END

C2V_VMENTRY(jboolean, hasCompiledCodeForOSR, (JNIEnv*, jobject, jobject jvmci_method, int entry_bci, int comp_level))
  Method* method = CompilerToVM::asMethod(jvmci_method);
  return method->lookup_osr_nmethod_for(entry_bci, comp_level, true) != NULL;
C2V_END

C2V_VMENTRY(jobject, getSymbol, (JNIEnv*, jobject, jlong symbol))
  Handle sym = java_lang_String::create_from_symbol((Symbol*)(address)symbol, CHECK_NULL);
  return JNIHandles::make_local(THREAD, sym());
C2V_END

bool matches(jobjectArray methods, Method* method) {
  objArrayOop methods_oop = (objArrayOop) JNIHandles::resolve(methods);

  for (int i = 0; i < methods_oop->length(); i++) {
    oop resolved = methods_oop->obj_at(i);
    if (resolved->is_a(HotSpotResolvedJavaMethodImpl::klass()) && CompilerToVM::asMethod(resolved) == method) {
      return true;
    }
  }
  return false;
}

C2V_VMENTRY(jobject, getNextStackFrame, (JNIEnv*, jobject compilerToVM, jobject hs_frame, jobjectArray methods, jint initialSkip))
  ResourceMark rm;

  if (!thread->has_last_Java_frame()) return NULL;
  Handle result = HotSpotStackFrameReference::klass()->allocate_instance(thread);
  HotSpotStackFrameReference::klass()->initialize(thread);

  StackFrameStream fst(thread);
  if (hs_frame != NULL) {
    // look for the correct stack frame if one is given
    intptr_t* stack_pointer = (intptr_t*) HotSpotStackFrameReference::stackPointer(hs_frame);
    while (fst.current()->sp() != stack_pointer && !fst.is_done()) {
      fst.next();
    }
    if (fst.current()->sp() != stack_pointer) {
      THROW_MSG_NULL(vmSymbols::java_lang_IllegalStateException(), "stack frame not found")
    }
  }

  int frame_number = 0;
  vframe* vf = vframe::new_vframe(fst.current(), fst.register_map(), thread);
  if (hs_frame != NULL) {
    // look for the correct vframe within the stack frame if one is given
    int last_frame_number = HotSpotStackFrameReference::frameNumber(hs_frame);
    while (frame_number < last_frame_number) {
      if (vf->is_top()) {
        THROW_MSG_NULL(vmSymbols::java_lang_IllegalStateException(), "invalid frame number")
      }
      vf = vf->sender();
      frame_number ++;
    }
    // move one frame forward
    if (vf->is_top()) {
      if (fst.is_done()) {
        return NULL;
      }
      fst.next();
      vf = vframe::new_vframe(fst.current(), fst.register_map(), thread);
      frame_number = 0;
    } else {
      vf = vf->sender();
      frame_number++;
    }
  }

  while (true) {
    // look for the given method
    while (true) {
      StackValueCollection* locals = NULL;
      if (vf->is_compiled_frame()) {
        // compiled method frame
        compiledVFrame* cvf = compiledVFrame::cast(vf);
        if (methods == NULL || matches(methods, cvf->method())) {
          if (initialSkip > 0) {
            initialSkip --;
          } else {
            ScopeDesc* scope = cvf->scope();
            // native wrapper do not have a scope
            if (scope != NULL && scope->objects() != NULL) {
              bool realloc_failures = Deoptimization::realloc_objects(thread, fst.current(), scope->objects(), THREAD);
              Deoptimization::reassign_fields(fst.current(), fst.register_map(), scope->objects(), realloc_failures, false);

              GrowableArray<ScopeValue*>* local_values = scope->locals();
              typeArrayHandle array = oopFactory::new_boolArray(local_values->length(), thread);
              for (int i = 0; i < local_values->length(); i++) {
                ScopeValue* value = local_values->at(i);
                if (value->is_object()) {
                  array->bool_at_put(i, true);
                }
              }
              HotSpotStackFrameReference::set_localIsVirtual(result, array());
            } else {
              HotSpotStackFrameReference::set_localIsVirtual(result, NULL);
            }

            locals = cvf->locals();
            HotSpotStackFrameReference::set_bci(result, cvf->bci());
            oop method = CompilerToVM::get_jvmci_method(cvf->method(), CHECK_NULL);
            HotSpotStackFrameReference::set_method(result, method);
          }
        }
      } else if (vf->is_interpreted_frame()) {
        // interpreted method frame
        interpretedVFrame* ivf = interpretedVFrame::cast(vf);
        if (methods == NULL || matches(methods, ivf->method())) {
          if (initialSkip > 0) {
            initialSkip --;
          } else {
            locals = ivf->locals();
            HotSpotStackFrameReference::set_bci(result, ivf->bci());
            oop method = CompilerToVM::get_jvmci_method(ivf->method(), CHECK_NULL);
            HotSpotStackFrameReference::set_method(result, method);
            HotSpotStackFrameReference::set_localIsVirtual(result, NULL);
          }
        }
      }

      // locals != NULL means that we found a matching frame and result is already partially initialized
      if (locals != NULL) {
        HotSpotStackFrameReference::set_compilerToVM(result, JNIHandles::resolve(compilerToVM));
        HotSpotStackFrameReference::set_stackPointer(result, (jlong) fst.current()->sp());
        HotSpotStackFrameReference::set_frameNumber(result, frame_number);

        // initialize the locals array
        objArrayHandle array = oopFactory::new_objectArray(locals->size(), thread);
        for (int i = 0; i < locals->size(); i++) {
          StackValue* var = locals->at(i);
          if (var->type() == T_OBJECT) {
            array->obj_at_put(i, locals->at(i)->get_obj()());
          }
        }
        HotSpotStackFrameReference::set_locals(result, array());

        return JNIHandles::make_local(thread, result());
      }

      if (vf->is_top()) {
        break;
      }
      frame_number++;
      vf = vf->sender();
    } // end of vframe loop

    if (fst.is_done()) {
      break;
    }
    fst.next();
    vf = vframe::new_vframe(fst.current(), fst.register_map(), thread);
    frame_number = 0;
  } // end of frame loop

  // the end was reached without finding a matching method
  return NULL;
C2V_END

C2V_VMENTRY(void, resolveInvokeDynamicInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  CallInfo callInfo;
  LinkResolver::resolve_invoke(callInfo, Handle(), cp, index, Bytecodes::_invokedynamic, CHECK);
  ConstantPoolCacheEntry* cp_cache_entry = cp->invokedynamic_cp_cache_entry_at(index);
  cp_cache_entry->set_dynamic_call(cp, callInfo);
C2V_END

C2V_VMENTRY(void, resolveInvokeHandleInPool, (JNIEnv*, jobject, jobject jvmci_constant_pool, jint index))
  constantPoolHandle cp = CompilerToVM::asConstantPool(jvmci_constant_pool);
  CallInfo callInfo;
  LinkResolver::resolve_invoke(callInfo, Handle(), cp, index, Bytecodes::_invokehandle, CHECK);
  ConstantPoolCacheEntry* cp_cache_entry = cp_cache_entry = cp->cache()->entry_at(cp->decode_cpcache_index(index));
  cp_cache_entry->set_method_handle(cp, callInfo);
C2V_END

C2V_VMENTRY(jboolean, shouldDebugNonSafepoints, (JNIEnv*, jobject))
  //see compute_recording_non_safepoints in debugInfroRec.cpp
  if (JvmtiExport::should_post_compiled_method_load() && FLAG_IS_DEFAULT(DebugNonSafepoints)) {
    return true;
  }
  return DebugNonSafepoints;
C2V_END

// public native void materializeVirtualObjects(HotSpotStackFrameReference stackFrame, boolean invalidate);
C2V_VMENTRY(void, materializeVirtualObjects, (JNIEnv*, jobject, jobject hs_frame, bool invalidate))
  ResourceMark rm;

  if (hs_frame == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "stack frame is null")
  }

  HotSpotStackFrameReference::klass()->initialize(thread);

  // look for the given stack frame
  StackFrameStream fst(thread);
  intptr_t* stack_pointer = (intptr_t*) HotSpotStackFrameReference::stackPointer(hs_frame);
  while (fst.current()->sp() != stack_pointer && !fst.is_done()) {
    fst.next();
  }
  if (fst.current()->sp() != stack_pointer) {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "stack frame not found")
  }

  if (invalidate) {
    if (!fst.current()->is_compiled_frame()) {
      THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "compiled stack frame expected")
    }
    assert(fst.current()->cb()->is_nmethod(), "nmethod expected");
    ((nmethod*) fst.current()->cb())->make_not_entrant();
  }
  Deoptimization::deoptimize(thread, *fst.current(), fst.register_map(), Deoptimization::Reason_none);
  // look for the frame again as it has been updated by deopt (pc, deopt state...)
  StackFrameStream fstAfterDeopt(thread);
  while (fstAfterDeopt.current()->sp() != stack_pointer && !fstAfterDeopt.is_done()) {
    fstAfterDeopt.next();
  }
  if (fstAfterDeopt.current()->sp() != stack_pointer) {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "stack frame not found after deopt")
  }

  vframe* vf = vframe::new_vframe(fstAfterDeopt.current(), fstAfterDeopt.register_map(), thread);
  if (!vf->is_compiled_frame()) {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "compiled stack frame expected")
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

  int last_frame_number = HotSpotStackFrameReference::frameNumber(hs_frame);
  if (last_frame_number >= virtualFrames->length()) {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "invalid frame number")
  }

  // Reallocate the non-escaping objects and restore their fields.
  assert (virtualFrames->at(last_frame_number)->scope() != NULL,"invalid scope");
  GrowableArray<ScopeValue*>* objects = virtualFrames->at(last_frame_number)->scope()->objects();

  if (objects == NULL) {
    // no objects to materialize
    return;
  }

  bool realloc_failures = Deoptimization::realloc_objects(thread, fstAfterDeopt.current(), objects, THREAD);
  Deoptimization::reassign_fields(fstAfterDeopt.current(), fstAfterDeopt.register_map(), objects, realloc_failures, false);

  for (int frame_index = 0; frame_index < virtualFrames->length(); frame_index++) {
    compiledVFrame* cvf = virtualFrames->at(frame_index);

    GrowableArray<ScopeValue*>* scopeLocals = cvf->scope()->locals();
    StackValueCollection* locals = cvf->locals();

    if (locals != NULL) {
      for (int i2 = 0; i2 < locals->size(); i2++) {
        StackValue* var = locals->at(i2);
        if (var->type() == T_OBJECT && scopeLocals->at(i2)->is_object()) {
          jvalue val;
          val.l = (jobject) locals->at(i2)->get_obj()();
          cvf->update_local(T_OBJECT, i2, val);
        }
      }
    }
  }

  // all locals are materialized by now
  HotSpotStackFrameReference::set_localIsVirtual(hs_frame, NULL);

  // update the locals array
  objArrayHandle array = HotSpotStackFrameReference::locals(hs_frame);
  StackValueCollection* locals = virtualFrames->at(last_frame_number)->locals();
  for (int i = 0; i < locals->size(); i++) {
    StackValue* var = locals->at(i);
    if (var->type() == T_OBJECT) {
      array->obj_at_put(i, locals->at(i)->get_obj()());
    }
  }
C2V_END

C2V_VMENTRY(void, writeDebugOutput, (JNIEnv*, jobject, jbyteArray bytes, jint offset, jint length))
  if (bytes == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }
  typeArrayOop array = (typeArrayOop) JNIHandles::resolve(bytes);

  // Check if offset and length are non negative.
  if (offset < 0 || length < 0) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }
  // Check if the range is valid.
  if ((((unsigned int) length + (unsigned int) offset) > (unsigned int) array->length())) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }
  while (length > 0) {
    jbyte* start = array->byte_at_addr(offset);
    tty->write((char*) start, MIN2(length, O_BUFLEN));
    length -= O_BUFLEN;
    offset += O_BUFLEN;
  }
C2V_END

C2V_VMENTRY(void, flushDebugOutput, (JNIEnv*, jobject))
  tty->flush();
C2V_END

C2V_VMENTRY(int, methodDataProfileDataSize, (JNIEnv*, jobject, jlong metaspace_method_data, jint position))
  ResourceMark rm;
  MethodData* mdo = CompilerToVM::asMethodData(metaspace_method_data);
  ProfileData* profile_data = mdo->data_at(position);
  if (mdo->is_valid(profile_data)) {
    return profile_data->size_in_bytes();
  }
  DataLayout* data    = mdo->extra_data_base();
  DataLayout* end   = mdo->extra_data_limit();
  for (;; data = mdo->next_extra(data)) {
    assert(data < end, "moved past end of extra data");
    profile_data = data->data_in();
    if (mdo->dp_to_di(profile_data->dp()) == position) {
      return profile_data->size_in_bytes();
    }
  }
  THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), err_msg("Invalid profile data position %d", position));
C2V_END


#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &(c2v_ ## f))

#define STRING                "Ljava/lang/String;"
#define OBJECT                "Ljava/lang/Object;"
#define CLASS                 "Ljava/lang/Class;"
#define STACK_TRACE_ELEMENT   "Ljava/lang/StackTraceElement;"
#define INSTALLED_CODE        "Ljdk/vm/ci/code/InstalledCode;"
#define TARGET_DESCRIPTION    "Ljdk/vm/ci/code/TargetDescription;"
#define RESOLVED_METHOD       "Ljdk/vm/ci/meta/ResolvedJavaMethod;"
#define HS_RESOLVED_METHOD    "Ljdk/vm/ci/hotspot/HotSpotResolvedJavaMethodImpl;"
#define HS_RESOLVED_KLASS     "Ljdk/vm/ci/hotspot/HotSpotResolvedObjectTypeImpl;"
#define HS_CONSTANT_POOL      "Ljdk/vm/ci/hotspot/HotSpotConstantPool;"
#define HS_COMPILED_CODE      "Ljdk/vm/ci/hotspot/HotSpotCompiledCode;"
#define HS_CONFIG             "Ljdk/vm/ci/hotspot/HotSpotVMConfig;"
#define HS_METADATA           "Ljdk/vm/ci/hotspot/HotSpotMetaData;"
#define HS_STACK_FRAME_REF    "Ljdk/vm/ci/hotspot/HotSpotStackFrameReference;"
#define HS_SPECULATION_LOG    "Ljdk/vm/ci/hotspot/HotSpotSpeculationLog;"
#define METASPACE_METHOD_DATA "J"

JNINativeMethod CompilerToVM::methods[] = {
  {CC"getBytecode",                                  CC"("HS_RESOLVED_METHOD")[B",                                                     FN_PTR(getBytecode)},
  {CC"getExceptionTableStart",                       CC"("HS_RESOLVED_METHOD")J",                                                      FN_PTR(getExceptionTableStart)},
  {CC"getExceptionTableLength",                      CC"("HS_RESOLVED_METHOD")I",                                                      FN_PTR(getExceptionTableLength)},
  {CC"findUniqueConcreteMethod",                     CC"("HS_RESOLVED_KLASS HS_RESOLVED_METHOD")"HS_RESOLVED_METHOD,                   FN_PTR(findUniqueConcreteMethod)},
  {CC"getImplementor",                               CC"("HS_RESOLVED_KLASS")"HS_RESOLVED_KLASS,                                       FN_PTR(getImplementor)},
  {CC"getStackTraceElement",                         CC"("HS_RESOLVED_METHOD"I)"STACK_TRACE_ELEMENT,                                   FN_PTR(getStackTraceElement)},
  {CC"methodIsIgnoredBySecurityStackWalk",           CC"("HS_RESOLVED_METHOD")Z",                                                      FN_PTR(methodIsIgnoredBySecurityStackWalk)},
  {CC"doNotInlineOrCompile",                         CC"("HS_RESOLVED_METHOD")V",                                                      FN_PTR(doNotInlineOrCompile)},
  {CC"canInlineMethod",                              CC"("HS_RESOLVED_METHOD")Z",                                                      FN_PTR(canInlineMethod)},
  {CC"shouldInlineMethod",                           CC"("HS_RESOLVED_METHOD")Z",                                                      FN_PTR(shouldInlineMethod)},
  {CC"lookupType",                                   CC"("STRING CLASS"Z)"HS_RESOLVED_KLASS,                                           FN_PTR(lookupType)},
  {CC"lookupNameInPool",                             CC"("HS_CONSTANT_POOL"I)"STRING,                                                  FN_PTR(lookupNameInPool)},
  {CC"lookupNameAndTypeRefIndexInPool",              CC"("HS_CONSTANT_POOL"I)I",                                                       FN_PTR(lookupNameAndTypeRefIndexInPool)},
  {CC"lookupSignatureInPool",                        CC"("HS_CONSTANT_POOL"I)"STRING,                                                  FN_PTR(lookupSignatureInPool)},
  {CC"lookupKlassRefIndexInPool",                    CC"("HS_CONSTANT_POOL"I)I",                                                       FN_PTR(lookupKlassRefIndexInPool)},
  {CC"lookupKlassInPool",                            CC"("HS_CONSTANT_POOL"I)Ljava/lang/Object;",                                      FN_PTR(lookupKlassInPool)},
  {CC"lookupAppendixInPool",                         CC"("HS_CONSTANT_POOL"I)"OBJECT,                                                  FN_PTR(lookupAppendixInPool)},
  {CC"lookupMethodInPool",                           CC"("HS_CONSTANT_POOL"IB)"HS_RESOLVED_METHOD,                                     FN_PTR(lookupMethodInPool)},
  {CC"constantPoolRemapInstructionOperandFromCache", CC"("HS_CONSTANT_POOL"I)I",                                                       FN_PTR(constantPoolRemapInstructionOperandFromCache)},
  {CC"resolveConstantInPool",                        CC"("HS_CONSTANT_POOL"I)"OBJECT,                                                  FN_PTR(resolveConstantInPool)},
  {CC"resolvePossiblyCachedConstantInPool",          CC"("HS_CONSTANT_POOL"I)"OBJECT,                                                  FN_PTR(resolvePossiblyCachedConstantInPool)},
  {CC"resolveTypeInPool",                            CC"("HS_CONSTANT_POOL"I)"HS_RESOLVED_KLASS,                                       FN_PTR(resolveTypeInPool)},
  {CC"resolveFieldInPool",                           CC"("HS_CONSTANT_POOL"IB[J)"HS_RESOLVED_KLASS,                                    FN_PTR(resolveFieldInPool)},
  {CC"resolveInvokeDynamicInPool",                   CC"("HS_CONSTANT_POOL"I)V",                                                       FN_PTR(resolveInvokeDynamicInPool)},
  {CC"resolveInvokeHandleInPool",                    CC"("HS_CONSTANT_POOL"I)V",                                                       FN_PTR(resolveInvokeHandleInPool)},
  {CC"resolveMethod",                                CC"("HS_RESOLVED_KLASS HS_RESOLVED_METHOD HS_RESOLVED_KLASS")"HS_RESOLVED_METHOD, FN_PTR(resolveMethod)},
  {CC"getVtableIndexForInterfaceMethod",             CC"("HS_RESOLVED_KLASS HS_RESOLVED_METHOD")I",                                    FN_PTR(getVtableIndexForInterfaceMethod)},
  {CC"getClassInitializer",                          CC"("HS_RESOLVED_KLASS")"HS_RESOLVED_METHOD,                                      FN_PTR(getClassInitializer)},
  {CC"hasFinalizableSubclass",                       CC"("HS_RESOLVED_KLASS")Z",                                                       FN_PTR(hasFinalizableSubclass)},
  {CC"getMaxCallTargetOffset",                       CC"(J)J",                                                                         FN_PTR(getMaxCallTargetOffset)},
  {CC"getResolvedJavaMethodAtSlot",                  CC"("CLASS"I)"HS_RESOLVED_METHOD,                                                 FN_PTR(getResolvedJavaMethodAtSlot)},
  {CC"getResolvedJavaMethod",                        CC"(Ljava/lang/Object;J)"HS_RESOLVED_METHOD,                                      FN_PTR(getResolvedJavaMethod)},
  {CC"getConstantPool",                              CC"(Ljava/lang/Object;J)"HS_CONSTANT_POOL,                                        FN_PTR(getConstantPool)},
  {CC"getResolvedJavaType",                          CC"(Ljava/lang/Object;JZ)"HS_RESOLVED_KLASS,                                      FN_PTR(getResolvedJavaType)},
  {CC"initializeConfiguration",                      CC"("HS_CONFIG")J",                                                               FN_PTR(initializeConfiguration)},
  {CC"installCode",                                  CC"("TARGET_DESCRIPTION HS_COMPILED_CODE INSTALLED_CODE HS_SPECULATION_LOG")I",   FN_PTR(installCode)},
  {CC"getMetadata",                                  CC"("TARGET_DESCRIPTION HS_COMPILED_CODE HS_METADATA")I",                         FN_PTR(getMetadata)},
  {CC"notifyCompilationStatistics",                  CC"(I"HS_RESOLVED_METHOD"ZIJJ"INSTALLED_CODE")V",                                 FN_PTR(notifyCompilationStatistics)},
  {CC"resetCompilationStatistics",                   CC"()V",                                                                          FN_PTR(resetCompilationStatistics)},
  {CC"disassembleCodeBlob",                          CC"("INSTALLED_CODE")"STRING,                                                     FN_PTR(disassembleCodeBlob)},
  {CC"executeInstalledCode",                         CC"(["OBJECT INSTALLED_CODE")"OBJECT,                                             FN_PTR(executeInstalledCode)},
  {CC"getLineNumberTable",                           CC"("HS_RESOLVED_METHOD")[J",                                                     FN_PTR(getLineNumberTable)},
  {CC"getLocalVariableTableStart",                   CC"("HS_RESOLVED_METHOD")J",                                                      FN_PTR(getLocalVariableTableStart)},
  {CC"getLocalVariableTableLength",                  CC"("HS_RESOLVED_METHOD")I",                                                      FN_PTR(getLocalVariableTableLength)},
  {CC"reprofile",                                    CC"("HS_RESOLVED_METHOD")V",                                                      FN_PTR(reprofile)},
  {CC"invalidateInstalledCode",                      CC"("INSTALLED_CODE")V",                                                          FN_PTR(invalidateInstalledCode)},
  {CC"readUncompressedOop",                          CC"(J)"OBJECT,                                                                    FN_PTR(readUncompressedOop)},
  {CC"collectCounters",                              CC"()[J",                                                                         FN_PTR(collectCounters)},
  {CC"allocateCompileId",                            CC"("HS_RESOLVED_METHOD"I)I",                                                     FN_PTR(allocateCompileId)},
  {CC"isMature",                                     CC"("METASPACE_METHOD_DATA")Z",                                                   FN_PTR(isMature)},
  {CC"hasCompiledCodeForOSR",                        CC"("HS_RESOLVED_METHOD"II)Z",                                                    FN_PTR(hasCompiledCodeForOSR)},
  {CC"getSymbol",                                    CC"(J)"STRING,                                                                    FN_PTR(getSymbol)},
  {CC"getNextStackFrame",                            CC"("HS_STACK_FRAME_REF "["RESOLVED_METHOD"I)"HS_STACK_FRAME_REF,                 FN_PTR(getNextStackFrame)},
  {CC"materializeVirtualObjects",                    CC"("HS_STACK_FRAME_REF"Z)V",                                                     FN_PTR(materializeVirtualObjects)},
  {CC"shouldDebugNonSafepoints",                     CC"()Z",                                                                          FN_PTR(shouldDebugNonSafepoints)},
  {CC"writeDebugOutput",                             CC"([BII)V",                                                                      FN_PTR(writeDebugOutput)},
  {CC"flushDebugOutput",                             CC"()V",                                                                          FN_PTR(flushDebugOutput)},
  {CC"methodDataProfileDataSize",                    CC"(JI)I",                                                                        FN_PTR(methodDataProfileDataSize)},
};

int CompilerToVM::methods_count() {
  return sizeof(methods) / sizeof(JNINativeMethod);
}

