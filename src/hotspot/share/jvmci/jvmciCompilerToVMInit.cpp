/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

// no precompiled headers
#include "ci/ciUtilities.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTable.hpp"
#include "memory/oopFactory.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/vmStructs_jvmci.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/resourceHash.hpp"


int CompilerToVM::Data::Klass_vtable_start_offset;
int CompilerToVM::Data::Klass_vtable_length_offset;

int CompilerToVM::Data::Method_extra_stack_entries;

address CompilerToVM::Data::SharedRuntime_ic_miss_stub;
address CompilerToVM::Data::SharedRuntime_handle_wrong_method_stub;
address CompilerToVM::Data::SharedRuntime_deopt_blob_unpack;
address CompilerToVM::Data::SharedRuntime_deopt_blob_uncommon_trap;

size_t CompilerToVM::Data::ThreadLocalAllocBuffer_alignment_reserve;

CollectedHeap* CompilerToVM::Data::Universe_collectedHeap;
int CompilerToVM::Data::Universe_base_vtable_size;
address CompilerToVM::Data::Universe_narrow_oop_base;
int CompilerToVM::Data::Universe_narrow_oop_shift;
address CompilerToVM::Data::Universe_narrow_klass_base;
int CompilerToVM::Data::Universe_narrow_klass_shift;
void* CompilerToVM::Data::Universe_non_oop_bits;
uintptr_t CompilerToVM::Data::Universe_verify_oop_mask;
uintptr_t CompilerToVM::Data::Universe_verify_oop_bits;

bool       CompilerToVM::Data::_supports_inline_contig_alloc;
HeapWord** CompilerToVM::Data::_heap_end_addr;
HeapWord* volatile* CompilerToVM::Data::_heap_top_addr;
int CompilerToVM::Data::_max_oop_map_stack_offset;
int CompilerToVM::Data::_fields_annotations_base_offset;

CardTable::CardValue* CompilerToVM::Data::cardtable_start_address;
int CompilerToVM::Data::cardtable_shift;

int CompilerToVM::Data::vm_page_size;

int CompilerToVM::Data::sizeof_vtableEntry = sizeof(vtableEntry);
int CompilerToVM::Data::sizeof_ExceptionTableElement = sizeof(ExceptionTableElement);
int CompilerToVM::Data::sizeof_LocalVariableTableElement = sizeof(LocalVariableTableElement);
int CompilerToVM::Data::sizeof_ConstantPool = sizeof(ConstantPool);
int CompilerToVM::Data::sizeof_narrowKlass = sizeof(narrowKlass);
int CompilerToVM::Data::sizeof_arrayOopDesc = sizeof(arrayOopDesc);
int CompilerToVM::Data::sizeof_BasicLock = sizeof(BasicLock);

address CompilerToVM::Data::dsin;
address CompilerToVM::Data::dcos;
address CompilerToVM::Data::dtan;
address CompilerToVM::Data::dexp;
address CompilerToVM::Data::dlog;
address CompilerToVM::Data::dlog10;
address CompilerToVM::Data::dpow;

address CompilerToVM::Data::symbol_init;
address CompilerToVM::Data::symbol_clinit;

void CompilerToVM::Data::initialize(TRAPS) {
  Klass_vtable_start_offset = in_bytes(Klass::vtable_start_offset());
  Klass_vtable_length_offset = in_bytes(Klass::vtable_length_offset());

  Method_extra_stack_entries = Method::extra_stack_entries();

  SharedRuntime_ic_miss_stub = SharedRuntime::get_ic_miss_stub();
  SharedRuntime_handle_wrong_method_stub = SharedRuntime::get_handle_wrong_method_stub();
  SharedRuntime_deopt_blob_unpack = SharedRuntime::deopt_blob()->unpack();
  SharedRuntime_deopt_blob_uncommon_trap = SharedRuntime::deopt_blob()->uncommon_trap();

  ThreadLocalAllocBuffer_alignment_reserve = ThreadLocalAllocBuffer::alignment_reserve();

  Universe_collectedHeap = Universe::heap();
  Universe_base_vtable_size = Universe::base_vtable_size();
  Universe_narrow_oop_base = Universe::narrow_oop_base();
  Universe_narrow_oop_shift = Universe::narrow_oop_shift();
  Universe_narrow_klass_base = Universe::narrow_klass_base();
  Universe_narrow_klass_shift = Universe::narrow_klass_shift();
  Universe_non_oop_bits = Universe::non_oop_word();
  Universe_verify_oop_mask = Universe::verify_oop_mask();
  Universe_verify_oop_bits = Universe::verify_oop_bits();

  _supports_inline_contig_alloc = Universe::heap()->supports_inline_contig_alloc();
  _heap_end_addr = _supports_inline_contig_alloc ? Universe::heap()->end_addr() : (HeapWord**) -1;
  _heap_top_addr = _supports_inline_contig_alloc ? Universe::heap()->top_addr() : (HeapWord* volatile*) -1;

  _max_oop_map_stack_offset = (OopMapValue::register_mask - VMRegImpl::stack2reg(0)->value()) * VMRegImpl::stack_slot_size;
  int max_oop_map_stack_index = _max_oop_map_stack_offset / VMRegImpl::stack_slot_size;
  assert(OopMapValue::legal_vm_reg_name(VMRegImpl::stack2reg(max_oop_map_stack_index)), "should be valid");
  assert(!OopMapValue::legal_vm_reg_name(VMRegImpl::stack2reg(max_oop_map_stack_index + 1)), "should be invalid");

  symbol_init = (address) vmSymbols::object_initializer_name();
  symbol_clinit = (address) vmSymbols::class_initializer_name();

  _fields_annotations_base_offset = Array<AnnotationArray*>::base_offset_in_bytes();

  BarrierSet* bs = BarrierSet::barrier_set();
  if (bs->is_a(BarrierSet::CardTableBarrierSet)) {
    CardTable::CardValue* base = ci_card_table_address();
    assert(base != NULL, "unexpected byte_map_base");
    cardtable_start_address = base;
    cardtable_shift = CardTable::card_shift;
  } else {
    // No card mark barriers
    cardtable_start_address = 0;
    cardtable_shift = 0;
  }

  vm_page_size = os::vm_page_size();

#define SET_TRIGFUNC(name)                                      \
  if (StubRoutines::name() != NULL) {                           \
    name = StubRoutines::name();                                \
  } else {                                                      \
    name = CAST_FROM_FN_PTR(address, SharedRuntime::name);      \
  }

  SET_TRIGFUNC(dsin);
  SET_TRIGFUNC(dcos);
  SET_TRIGFUNC(dtan);
  SET_TRIGFUNC(dexp);
  SET_TRIGFUNC(dlog10);
  SET_TRIGFUNC(dlog);
  SET_TRIGFUNC(dpow);

#undef SET_TRIGFUNC
}

objArrayHandle CompilerToVM::initialize_intrinsics(TRAPS) {
  objArrayHandle vmIntrinsics = oopFactory::new_objArray_handle(VMIntrinsicMethod::klass(), (vmIntrinsics::ID_LIMIT - 1), CHECK_(objArrayHandle()));
  int index = 0;
  // The intrinsics for a class are usually adjacent to each other.
  // When they are, the string for the class name can be reused.
  vmSymbols::SID kls_sid = vmSymbols::NO_SID;
  Handle kls_str;
#define VM_SYMBOL_TO_STRING(s) \
  java_lang_String::create_from_symbol(vmSymbols::symbol_at(vmSymbols::VM_SYMBOL_ENUM_NAME(s)), CHECK_(objArrayHandle()))
#define VM_INTRINSIC_INFO(id, kls, name, sig, ignore_fcode) {             \
    instanceHandle vmIntrinsicMethod = InstanceKlass::cast(VMIntrinsicMethod::klass())->allocate_instance_handle(CHECK_(objArrayHandle())); \
    vmSymbols::SID sid = vmSymbols::VM_SYMBOL_ENUM_NAME(kls);             \
    if (kls_sid != sid) {                                                 \
      kls_str = VM_SYMBOL_TO_STRING(kls);                                 \
      kls_sid = sid;                                                      \
    }                                                                     \
    Handle name_str = VM_SYMBOL_TO_STRING(name);                          \
    Handle sig_str = VM_SYMBOL_TO_STRING(sig);                            \
    VMIntrinsicMethod::set_declaringClass(vmIntrinsicMethod, kls_str());  \
    VMIntrinsicMethod::set_name(vmIntrinsicMethod, name_str());           \
    VMIntrinsicMethod::set_descriptor(vmIntrinsicMethod, sig_str());      \
    VMIntrinsicMethod::set_id(vmIntrinsicMethod, vmIntrinsics::id);       \
      vmIntrinsics->obj_at_put(index++, vmIntrinsicMethod());             \
  }

  VM_INTRINSICS_DO(VM_INTRINSIC_INFO, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE)
#undef VM_SYMBOL_TO_STRING
#undef VM_INTRINSIC_INFO
  assert(index == vmIntrinsics::ID_LIMIT - 1, "must be");

  return vmIntrinsics;
}

/**
 * The set of VM flags known to be used.
 */
#define PREDEFINED_CONFIG_FLAGS(do_bool_flag, do_intx_flag, do_uintx_flag) \
  do_intx_flag(AllocateInstancePrefetchLines)                              \
  do_intx_flag(AllocatePrefetchDistance)                                   \
  do_intx_flag(AllocatePrefetchInstr)                                      \
  do_intx_flag(AllocatePrefetchLines)                                      \
  do_intx_flag(AllocatePrefetchStepSize)                                   \
  do_intx_flag(AllocatePrefetchStyle)                                      \
  do_intx_flag(BciProfileWidth)                                            \
  do_bool_flag(BootstrapJVMCI)                                             \
  do_bool_flag(CITime)                                                     \
  do_bool_flag(CITimeEach)                                                 \
  do_uintx_flag(CodeCacheSegmentSize)                                      \
  do_intx_flag(CodeEntryAlignment)                                         \
  do_bool_flag(CompactFields)                                              \
  do_intx_flag(ContendedPaddingWidth)                                      \
  do_bool_flag(DontCompileHugeMethods)                                     \
  do_bool_flag(EagerJVMCI)                                                 \
  do_bool_flag(EnableContended)                                            \
  do_intx_flag(FieldsAllocationStyle)                                      \
  do_bool_flag(FoldStableValues)                                           \
  do_bool_flag(ForceUnreachable)                                           \
  do_intx_flag(HugeMethodLimit)                                            \
  do_bool_flag(Inline)                                                     \
  do_intx_flag(JVMCICounterSize)                                           \
  do_bool_flag(JVMCIPrintProperties)                                       \
  do_bool_flag(JVMCIUseFastLocking)                                        \
  do_intx_flag(MethodProfileWidth)                                         \
  do_intx_flag(ObjectAlignmentInBytes)                                     \
  do_bool_flag(PrintInlining)                                              \
  do_bool_flag(ReduceInitialCardMarks)                                     \
  do_bool_flag(RestrictContended)                                          \
  do_intx_flag(StackReservedPages)                                         \
  do_intx_flag(StackShadowPages)                                           \
  do_bool_flag(TLABStats)                                                  \
  do_uintx_flag(TLABWasteIncrement)                                        \
  do_intx_flag(TypeProfileWidth)                                           \
  do_bool_flag(UseAESIntrinsics)                                           \
  X86_ONLY(do_intx_flag(UseAVX))                                           \
  do_bool_flag(UseBiasedLocking)                                           \
  do_bool_flag(UseCRC32Intrinsics)                                         \
  do_bool_flag(UseCompressedClassPointers)                                 \
  do_bool_flag(UseCompressedOops)                                          \
  X86_ONLY(do_bool_flag(UseCountLeadingZerosInstruction))                  \
  X86_ONLY(do_bool_flag(UseCountTrailingZerosInstruction))                 \
  do_bool_flag(UseConcMarkSweepGC)                                         \
  do_bool_flag(UseG1GC)                                                    \
  do_bool_flag(UseParallelGC)                                              \
  do_bool_flag(UseParallelOldGC)                                           \
  do_bool_flag(UseSerialGC)                                                \
  do_bool_flag(UseZGC)                                                     \
  do_bool_flag(UseEpsilonGC)                                               \
  COMPILER2_PRESENT(do_bool_flag(UseMontgomeryMultiplyIntrinsic))          \
  COMPILER2_PRESENT(do_bool_flag(UseMontgomerySquareIntrinsic))            \
  COMPILER2_PRESENT(do_bool_flag(UseMulAddIntrinsic))                      \
  COMPILER2_PRESENT(do_bool_flag(UseMultiplyToLenIntrinsic))               \
  do_bool_flag(UsePopCountInstruction)                                     \
  do_bool_flag(UseSHA1Intrinsics)                                          \
  do_bool_flag(UseSHA256Intrinsics)                                        \
  do_bool_flag(UseSHA512Intrinsics)                                        \
  do_intx_flag(UseSSE)                                                     \
  COMPILER2_PRESENT(do_bool_flag(UseSquareToLenIntrinsic))                 \
  do_bool_flag(UseStackBanging)                                            \
  do_bool_flag(UseTLAB)                                                    \
  do_bool_flag(VerifyOops)                                                 \

#define BOXED_BOOLEAN(name, value) oop name = ((jboolean)(value) ? boxedTrue() : boxedFalse())
#define BOXED_DOUBLE(name, value) oop name; do { jvalue p; p.d = (jdouble) (value); name = java_lang_boxing_object::create(T_DOUBLE, &p, CHECK_NULL);} while(0)
#define BOXED_LONG(name, value) \
  oop name; \
  do { \
    jvalue p; p.j = (jlong) (value); \
    Handle* e = longs.get(p.j); \
    if (e == NULL) { \
      oop o = java_lang_boxing_object::create(T_LONG, &p, CHECK_NULL); \
      Handle h(THREAD, o); \
      longs.put(p.j, h); \
      name = h(); \
    } else { \
      name = (*e)(); \
    } \
  } while (0)

#define CSTRING_TO_JSTRING(name, value) \
  Handle name; \
  do { \
    if (value != NULL) { \
      Handle* e = strings.get(value); \
      if (e == NULL) { \
        Handle h = java_lang_String::create_from_str(value, CHECK_NULL); \
        strings.put(value, h); \
        name = h; \
      } else { \
        name = (*e); \
      } \
    } \
  } while (0)

jobjectArray readConfiguration0(JNIEnv *env, TRAPS) {
  ResourceMark rm;
  HandleMark hm;

  // Used to canonicalize Long and String values.
  ResourceHashtable<jlong, Handle> longs;
  ResourceHashtable<const char*, Handle, &CompilerToVM::cstring_hash, &CompilerToVM::cstring_equals> strings;

  jvalue prim;
  prim.z = true;  oop boxedTrueOop =  java_lang_boxing_object::create(T_BOOLEAN, &prim, CHECK_NULL);
  Handle boxedTrue(THREAD, boxedTrueOop);
  prim.z = false; oop boxedFalseOop = java_lang_boxing_object::create(T_BOOLEAN, &prim, CHECK_NULL);
  Handle boxedFalse(THREAD, boxedFalseOop);

  CompilerToVM::Data::initialize(CHECK_NULL);

  VMField::klass()->initialize(CHECK_NULL);
  VMFlag::klass()->initialize(CHECK_NULL);
  VMIntrinsicMethod::klass()->initialize(CHECK_NULL);

  int len = JVMCIVMStructs::localHotSpotVMStructs_count();
  objArrayHandle vmFields = oopFactory::new_objArray_handle(VMField::klass(), len, CHECK_NULL);
  for (int i = 0; i < len ; i++) {
    VMStructEntry vmField = JVMCIVMStructs::localHotSpotVMStructs[i];
    instanceHandle vmFieldObj = InstanceKlass::cast(VMField::klass())->allocate_instance_handle(CHECK_NULL);
    size_t name_buf_len = strlen(vmField.typeName) + strlen(vmField.fieldName) + 2 /* "::" */;
    char* name_buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, name_buf_len + 1);
    sprintf(name_buf, "%s::%s", vmField.typeName, vmField.fieldName);
    CSTRING_TO_JSTRING(name, name_buf);
    CSTRING_TO_JSTRING(type, vmField.typeString);
    VMField::set_name(vmFieldObj, name());
    VMField::set_type(vmFieldObj, type());
    VMField::set_offset(vmFieldObj, vmField.offset);
    VMField::set_address(vmFieldObj, (jlong) vmField.address);
    if (vmField.isStatic && vmField.typeString != NULL) {
      if (strcmp(vmField.typeString, "bool") == 0) {
        BOXED_BOOLEAN(box, *(jbyte*) vmField.address);
        VMField::set_value(vmFieldObj, box);
      } else if (strcmp(vmField.typeString, "int") == 0 ||
                 strcmp(vmField.typeString, "jint") == 0) {
        BOXED_LONG(box, *(jint*) vmField.address);
        VMField::set_value(vmFieldObj, box);
      } else if (strcmp(vmField.typeString, "uint64_t") == 0) {
        BOXED_LONG(box, *(uint64_t*) vmField.address);
        VMField::set_value(vmFieldObj, box);
      } else if (strcmp(vmField.typeString, "address") == 0 ||
                 strcmp(vmField.typeString, "intptr_t") == 0 ||
                 strcmp(vmField.typeString, "uintptr_t") == 0 ||
                 strcmp(vmField.typeString, "OopHandle") == 0 ||
                 strcmp(vmField.typeString, "size_t") == 0 ||
                 // All foo* types are addresses.
                 vmField.typeString[strlen(vmField.typeString) - 1] == '*') {
        BOXED_LONG(box, *((address*) vmField.address));
        VMField::set_value(vmFieldObj, box);
      } else {
        JVMCI_ERROR_NULL("VM field %s has unsupported type %s", name_buf, vmField.typeString);
      }
    }
    vmFields->obj_at_put(i, vmFieldObj());
  }

  int ints_len = JVMCIVMStructs::localHotSpotVMIntConstants_count();
  int longs_len = JVMCIVMStructs::localHotSpotVMLongConstants_count();
  len = ints_len + longs_len;
  objArrayHandle vmConstants = oopFactory::new_objArray_handle(SystemDictionary::Object_klass(), len * 2, CHECK_NULL);
  int insert = 0;
  for (int i = 0; i < ints_len ; i++) {
    VMIntConstantEntry c = JVMCIVMStructs::localHotSpotVMIntConstants[i];
    CSTRING_TO_JSTRING(name, c.name);
    BOXED_LONG(value, c.value);
    vmConstants->obj_at_put(insert++, name());
    vmConstants->obj_at_put(insert++, value);
  }
  for (int i = 0; i < longs_len ; i++) {
    VMLongConstantEntry c = JVMCIVMStructs::localHotSpotVMLongConstants[i];
    CSTRING_TO_JSTRING(name, c.name);
    BOXED_LONG(value, c.value);
    vmConstants->obj_at_put(insert++, name());
    vmConstants->obj_at_put(insert++, value);
  }
  assert(insert == len * 2, "must be");

  len = JVMCIVMStructs::localHotSpotVMAddresses_count();
  objArrayHandle vmAddresses = oopFactory::new_objArray_handle(SystemDictionary::Object_klass(), len * 2, CHECK_NULL);
  for (int i = 0; i < len ; i++) {
    VMAddressEntry a = JVMCIVMStructs::localHotSpotVMAddresses[i];
    CSTRING_TO_JSTRING(name, a.name);
    BOXED_LONG(value, a.value);
    vmAddresses->obj_at_put(i * 2, name());
    vmAddresses->obj_at_put(i * 2 + 1, value);
  }

#define COUNT_FLAG(ignore) +1
#ifdef ASSERT
#define CHECK_FLAG(type, name) { \
  JVMFlag* flag = JVMFlag::find_flag(#name, strlen(#name), /*allow_locked*/ true, /* return_flag */ true); \
  assert(flag != NULL, "No such flag named " #name); \
  assert(flag->is_##type(), "JVMFlag " #name " is not of type " #type); \
}
#else
#define CHECK_FLAG(type, name)
#endif

#define ADD_FLAG(type, name, convert) { \
  CHECK_FLAG(type, name) \
  instanceHandle vmFlagObj = InstanceKlass::cast(VMFlag::klass())->allocate_instance_handle(CHECK_NULL); \
  CSTRING_TO_JSTRING(fname, #name); \
  CSTRING_TO_JSTRING(ftype, #type); \
  VMFlag::set_name(vmFlagObj, fname()); \
  VMFlag::set_type(vmFlagObj, ftype()); \
  convert(value, name); \
  VMFlag::set_value(vmFlagObj, value); \
  vmFlags->obj_at_put(i++, vmFlagObj()); \
}
#define ADD_BOOL_FLAG(name)  ADD_FLAG(bool, name, BOXED_BOOLEAN)
#define ADD_INTX_FLAG(name)  ADD_FLAG(intx, name, BOXED_LONG)
#define ADD_UINTX_FLAG(name) ADD_FLAG(uintx, name, BOXED_LONG)

  len = 0 + PREDEFINED_CONFIG_FLAGS(COUNT_FLAG, COUNT_FLAG, COUNT_FLAG);
  objArrayHandle vmFlags = oopFactory::new_objArray_handle(VMFlag::klass(), len, CHECK_NULL);
  int i = 0;
  PREDEFINED_CONFIG_FLAGS(ADD_BOOL_FLAG, ADD_INTX_FLAG, ADD_UINTX_FLAG)

  objArrayHandle vmIntrinsics = CompilerToVM::initialize_intrinsics(CHECK_NULL);

  objArrayOop data = oopFactory::new_objArray(SystemDictionary::Object_klass(), 5, CHECK_NULL);
  data->obj_at_put(0, vmFields());
  data->obj_at_put(1, vmConstants());
  data->obj_at_put(2, vmAddresses());
  data->obj_at_put(3, vmFlags());
  data->obj_at_put(4, vmIntrinsics());

  return (jobjectArray) JNIHandles::make_local(THREAD, data);
#undef COUNT_FLAG
#undef ADD_FLAG
#undef ADD_BOOL_FLAG
#undef ADD_INTX_FLAG
#undef ADD_UINTX_FLAG
#undef CHECK_FLAG
}
