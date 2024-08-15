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

// no precompiled headers
#ifdef COMPILER1
#include "c1/c1_Compiler.hpp"
#endif
#include "ci/ciUtilities.hpp"
#include "compiler/compiler_globals.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/tlab_globals.hpp"
#if INCLUDE_ZGC
#include "gc/x/xBarrierSetRuntime.hpp"
#include "gc/x/xThreadLocalData.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "gc/z/zThreadLocalData.hpp"
#endif
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/vmStructs_jvmci.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "oops/klass.inline.hpp"
#include "prims/jvmtiExport.hpp"
#ifdef COMPILER2
#include "opto/c2compiler.hpp"
#endif
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/resourceHash.hpp"


int CompilerToVM::Data::Klass_vtable_start_offset;
int CompilerToVM::Data::Klass_vtable_length_offset;

int CompilerToVM::Data::Method_extra_stack_entries;

address CompilerToVM::Data::SharedRuntime_ic_miss_stub;
address CompilerToVM::Data::SharedRuntime_handle_wrong_method_stub;
address CompilerToVM::Data::SharedRuntime_deopt_blob_unpack;
address CompilerToVM::Data::SharedRuntime_deopt_blob_unpack_with_exception_in_tls;
address CompilerToVM::Data::SharedRuntime_deopt_blob_uncommon_trap;
address CompilerToVM::Data::SharedRuntime_polling_page_return_handler;

address CompilerToVM::Data::nmethod_entry_barrier;
int CompilerToVM::Data::thread_disarmed_guard_value_offset;
int CompilerToVM::Data::thread_address_bad_mask_offset;

address CompilerToVM::Data::ZBarrierSetRuntime_load_barrier_on_oop_field_preloaded;
address CompilerToVM::Data::ZBarrierSetRuntime_load_barrier_on_weak_oop_field_preloaded;
address CompilerToVM::Data::ZBarrierSetRuntime_load_barrier_on_phantom_oop_field_preloaded;
address CompilerToVM::Data::ZBarrierSetRuntime_weak_load_barrier_on_oop_field_preloaded;
address CompilerToVM::Data::ZBarrierSetRuntime_weak_load_barrier_on_weak_oop_field_preloaded;
address CompilerToVM::Data::ZBarrierSetRuntime_weak_load_barrier_on_phantom_oop_field_preloaded;
address CompilerToVM::Data::ZBarrierSetRuntime_load_barrier_on_oop_array;
address CompilerToVM::Data::ZBarrierSetRuntime_clone;

address CompilerToVM::Data::ZPointerVectorLoadBadMask_address;
address CompilerToVM::Data::ZPointerVectorStoreBadMask_address;
address CompilerToVM::Data::ZPointerVectorStoreGoodMask_address;

bool CompilerToVM::Data::continuations_enabled;

#ifdef AARCH64
int CompilerToVM::Data::BarrierSetAssembler_nmethod_patching_type;
address CompilerToVM::Data::BarrierSetAssembler_patching_epoch_addr;
#endif

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

#ifdef X86
int CompilerToVM::Data::L1_line_size;
#endif

size_t CompilerToVM::Data::vm_page_size;

int CompilerToVM::Data::sizeof_vtableEntry = sizeof(vtableEntry);
int CompilerToVM::Data::sizeof_ExceptionTableElement = sizeof(ExceptionTableElement);
int CompilerToVM::Data::sizeof_LocalVariableTableElement = sizeof(LocalVariableTableElement);
int CompilerToVM::Data::sizeof_ConstantPool = sizeof(ConstantPool);
int CompilerToVM::Data::sizeof_narrowKlass = sizeof(narrowKlass);
int CompilerToVM::Data::sizeof_arrayOopDesc = sizeof(arrayOopDesc);
int CompilerToVM::Data::sizeof_BasicLock = sizeof(BasicLock);
#if INCLUDE_ZGC
int CompilerToVM::Data::sizeof_ZStoreBarrierEntry = sizeof(ZStoreBarrierEntry);
#endif

address CompilerToVM::Data::dsin;
address CompilerToVM::Data::dcos;
address CompilerToVM::Data::dtan;
address CompilerToVM::Data::dexp;
address CompilerToVM::Data::dlog;
address CompilerToVM::Data::dlog10;
address CompilerToVM::Data::dpow;

address CompilerToVM::Data::symbol_init;
address CompilerToVM::Data::symbol_clinit;

int CompilerToVM::Data::data_section_item_alignment;

JVMTI_ONLY( int* CompilerToVM::Data::_should_notify_object_alloc; )

void CompilerToVM::Data::initialize(JVMCI_TRAPS) {
  Klass_vtable_start_offset = in_bytes(Klass::vtable_start_offset());
  Klass_vtable_length_offset = in_bytes(Klass::vtable_length_offset());

  Method_extra_stack_entries = Method::extra_stack_entries();

  SharedRuntime_ic_miss_stub = SharedRuntime::get_ic_miss_stub();
  SharedRuntime_handle_wrong_method_stub = SharedRuntime::get_handle_wrong_method_stub();
  SharedRuntime_deopt_blob_unpack = SharedRuntime::deopt_blob()->unpack();
  SharedRuntime_deopt_blob_unpack_with_exception_in_tls = SharedRuntime::deopt_blob()->unpack_with_exception_in_tls();
  SharedRuntime_deopt_blob_uncommon_trap = SharedRuntime::deopt_blob()->uncommon_trap();
  SharedRuntime_polling_page_return_handler = SharedRuntime::polling_page_return_handler_blob()->entry_point();

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm != nullptr) {
    thread_disarmed_guard_value_offset = in_bytes(bs_nm->thread_disarmed_guard_value_offset());
    nmethod_entry_barrier = StubRoutines::method_entry_barrier();
    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
    AARCH64_ONLY(BarrierSetAssembler_nmethod_patching_type = (int) bs_asm->nmethod_patching_type());
    AARCH64_ONLY(BarrierSetAssembler_patching_epoch_addr = bs_asm->patching_epoch_addr());
  }

#if INCLUDE_ZGC
  if (UseZGC) {
    if (ZGenerational) {
      ZPointerVectorLoadBadMask_address   = (address) &ZPointerVectorLoadBadMask;
      ZPointerVectorStoreBadMask_address  = (address) &ZPointerVectorStoreBadMask;
      ZPointerVectorStoreGoodMask_address = (address) &ZPointerVectorStoreGoodMask;
    } else {
      thread_address_bad_mask_offset = in_bytes(XThreadLocalData::address_bad_mask_offset());
      // Initialize the old names for compatibility.  The proper XBarrierSetRuntime names are
      // exported as addresses in vmStructs_jvmci.cpp as are the new ZBarrierSetRuntime names.
      ZBarrierSetRuntime_load_barrier_on_oop_field_preloaded              = XBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr();
      ZBarrierSetRuntime_load_barrier_on_weak_oop_field_preloaded         = XBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded_addr();
      ZBarrierSetRuntime_load_barrier_on_phantom_oop_field_preloaded      = XBarrierSetRuntime::load_barrier_on_phantom_oop_field_preloaded_addr();
      ZBarrierSetRuntime_weak_load_barrier_on_oop_field_preloaded         = XBarrierSetRuntime::weak_load_barrier_on_oop_field_preloaded_addr();
      ZBarrierSetRuntime_weak_load_barrier_on_weak_oop_field_preloaded    = XBarrierSetRuntime::weak_load_barrier_on_weak_oop_field_preloaded_addr();
      ZBarrierSetRuntime_weak_load_barrier_on_phantom_oop_field_preloaded = XBarrierSetRuntime::weak_load_barrier_on_phantom_oop_field_preloaded_addr();
      ZBarrierSetRuntime_load_barrier_on_oop_array                        = XBarrierSetRuntime::load_barrier_on_oop_array_addr();
      ZBarrierSetRuntime_clone                                            = XBarrierSetRuntime::clone_addr();
    }
  }
#endif

  continuations_enabled = Continuations::enabled();

  ThreadLocalAllocBuffer_alignment_reserve = ThreadLocalAllocBuffer::alignment_reserve();

  Universe_collectedHeap = Universe::heap();
  Universe_base_vtable_size = Universe::base_vtable_size();
  if (UseCompressedOops) {
    Universe_narrow_oop_base = CompressedOops::base();
    Universe_narrow_oop_shift = CompressedOops::shift();
  } else {
    Universe_narrow_oop_base = nullptr;
    Universe_narrow_oop_shift = 0;
  }
  if (UseCompressedClassPointers) {
    Universe_narrow_klass_base = CompressedKlassPointers::base();
    Universe_narrow_klass_shift = CompressedKlassPointers::shift();
  } else {
    Universe_narrow_klass_base = nullptr;
    Universe_narrow_klass_shift = 0;
  }
  Universe_non_oop_bits = Universe::non_oop_word();
  Universe_verify_oop_mask = Universe::verify_oop_mask();
  Universe_verify_oop_bits = Universe::verify_oop_bits();

  _supports_inline_contig_alloc = false;
  _heap_end_addr = (HeapWord**) -1;
  _heap_top_addr = (HeapWord* volatile*) -1;

  _max_oop_map_stack_offset = (OopMapValue::register_mask - VMRegImpl::stack2reg(0)->value()) * VMRegImpl::stack_slot_size;
  int max_oop_map_stack_index = _max_oop_map_stack_offset / VMRegImpl::stack_slot_size;
  assert(OopMapValue::legal_vm_reg_name(VMRegImpl::stack2reg(max_oop_map_stack_index)), "should be valid");
  assert(!OopMapValue::legal_vm_reg_name(VMRegImpl::stack2reg(max_oop_map_stack_index + 1)), "should be invalid");

  symbol_init = (address) vmSymbols::object_initializer_name();
  symbol_clinit = (address) vmSymbols::class_initializer_name();

  _fields_annotations_base_offset = Array<AnnotationArray*>::base_offset_in_bytes();

  data_section_item_alignment = relocInfo::addr_unit();

  JVMTI_ONLY( _should_notify_object_alloc = &JvmtiExport::_should_notify_object_alloc; )

  BarrierSet* bs = BarrierSet::barrier_set();
  if (bs->is_a(BarrierSet::CardTableBarrierSet)) {
    CardTable::CardValue* base = ci_card_table_address();
    assert(base != nullptr, "unexpected byte_map_base");
    cardtable_start_address = base;
    cardtable_shift = CardTable::card_shift();
  } else {
    // No card mark barriers
    cardtable_start_address = nullptr;
    cardtable_shift = 0;
  }

#ifdef X86
  L1_line_size = VM_Version::L1_line_size();
#endif

  vm_page_size = os::vm_page_size();

#define SET_TRIGFUNC(name)                                      \
  if (StubRoutines::name() != nullptr) {                        \
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

static jboolean is_c1_supported(vmIntrinsics::ID id){
    jboolean supported = false;
#ifdef COMPILER1
    supported = (jboolean) Compiler::is_intrinsic_supported(id);
#endif
    return supported;
}

static jboolean is_c2_supported(vmIntrinsics::ID id){
    jboolean supported = false;
#ifdef COMPILER2
    supported = (jboolean) C2Compiler::is_intrinsic_supported(id);
#endif
    return supported;
}

JVMCIObjectArray CompilerToVM::initialize_intrinsics(JVMCI_TRAPS) {
  int len = vmIntrinsics::number_of_intrinsics() - 1; // Exclude vmIntrinsics::_none, which is 0
  JVMCIObjectArray vmIntrinsics = JVMCIENV->new_VMIntrinsicMethod_array(len, JVMCI_CHECK_NULL);
  int index = 0;
  vmSymbolID kls_sid = vmSymbolID::NO_SID;
  JVMCIObject kls_str;
#define VM_SYMBOL_TO_STRING(s) \
  JVMCIENV->create_string(vmSymbols::symbol_at(VM_SYMBOL_ENUM_NAME(s)), JVMCI_CHECK_NULL)
#define VM_INTRINSIC_INFO(id, kls, name, sig, ignore_fcode) {            \
    vmSymbolID sid = VM_SYMBOL_ENUM_NAME(kls);                           \
    if (kls_sid != sid) {                                                \
      kls_str = VM_SYMBOL_TO_STRING(kls);                                \
      kls_sid = sid;                                                     \
    }                                                                    \
    JVMCIObject name_str = VM_SYMBOL_TO_STRING(name);                    \
    JVMCIObject sig_str = VM_SYMBOL_TO_STRING(sig);                      \
    JVMCIObject vmIntrinsicMethod = JVMCIENV->new_VMIntrinsicMethod(kls_str, name_str, sig_str, (jint) vmIntrinsics::id, \
                                    (jboolean) vmIntrinsics::is_intrinsic_available(vmIntrinsics::id),                   \
                                    is_c1_supported(vmIntrinsics::id),                       \
                                    is_c2_supported(vmIntrinsics::id), JVMCI_CHECK_NULL);    \
    JVMCIENV->put_object_at(vmIntrinsics, index++, vmIntrinsicMethod);   \
  }

  // VM_INTRINSICS_DO does *not* iterate over vmIntrinsics::_none
  VM_INTRINSICS_DO(VM_INTRINSIC_INFO, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE)
#undef VM_SYMBOL_TO_STRING
#undef VM_INTRINSIC_INFO

  assert(index == len, "must be");
  return vmIntrinsics;
}

#define PREDEFINED_CONFIG_FLAGS(do_bool_flag, do_int_flag, do_intx_flag, do_uintx_flag) \
  do_int_flag(AllocateInstancePrefetchLines)                               \
  do_int_flag(AllocatePrefetchDistance)                                    \
  do_intx_flag(AllocatePrefetchInstr)                                      \
  do_int_flag(AllocatePrefetchLines)                                       \
  do_int_flag(AllocatePrefetchStepSize)                                    \
  do_int_flag(AllocatePrefetchStyle)                                       \
  do_intx_flag(BciProfileWidth)                                            \
  do_bool_flag(BootstrapJVMCI)                                             \
  do_bool_flag(CITime)                                                     \
  do_bool_flag(CITimeEach)                                                 \
  do_uintx_flag(CodeCacheSegmentSize)                                      \
  do_intx_flag(CodeEntryAlignment)                                         \
  do_int_flag(ContendedPaddingWidth)                                       \
  do_bool_flag(DontCompileHugeMethods)                                     \
  do_bool_flag(EagerJVMCI)                                                 \
  do_bool_flag(EnableContended)                                            \
  do_bool_flag(FoldStableValues)                                           \
  do_bool_flag(ForceUnreachable)                                           \
  do_intx_flag(HugeMethodLimit)                                            \
  do_bool_flag(Inline)                                                     \
  do_intx_flag(JVMCICounterSize)                                           \
  do_bool_flag(JVMCIPrintProperties)                                       \
  do_int_flag(ObjectAlignmentInBytes)                                      \
  do_bool_flag(PrintInlining)                                              \
  do_bool_flag(ReduceInitialCardMarks)                                     \
  do_bool_flag(RestrictContended)                                          \
  do_intx_flag(StackReservedPages)                                         \
  do_intx_flag(StackShadowPages)                                           \
  do_uintx_flag(TLABWasteIncrement)                                        \
  do_intx_flag(TypeProfileWidth)                                           \
  do_bool_flag(UseAESIntrinsics)                                           \
  X86_ONLY(do_int_flag(UseAVX))                                            \
  do_bool_flag(UseCRC32Intrinsics)                                         \
  do_bool_flag(UseAdler32Intrinsics)                                       \
  do_bool_flag(UseCompressedClassPointers)                                 \
  do_bool_flag(UseCompressedOops)                                          \
  X86_ONLY(do_bool_flag(UseCountLeadingZerosInstruction))                  \
  X86_ONLY(do_bool_flag(UseCountTrailingZerosInstruction))                 \
  do_bool_flag(UseG1GC)                                                    \
  do_bool_flag(UseParallelGC)                                              \
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
  X86_ONLY(do_int_flag(UseSSE))                                            \
  COMPILER2_PRESENT(do_bool_flag(UseSquareToLenIntrinsic))                 \
  do_bool_flag(UseTLAB)                                                    \
  do_bool_flag(VerifyOops)                                                 \

#define BOXED_BOOLEAN(name, value) name = ((jboolean)(value) ? boxedTrue : boxedFalse)
#define BOXED_DOUBLE(name, value) do { jvalue p; p.d = (jdouble) (value); name = JVMCIENV->create_box(T_DOUBLE, &p, JVMCI_CHECK_NULL);} while(0)
#define BOXED_LONG(name, value) \
  do { \
    jvalue p; p.j = (jlong) (value); \
    JVMCIObject* e = longs.get(p.j); \
    if (e == nullptr) { \
      JVMCIObject h = JVMCIENV->create_box(T_LONG, &p, JVMCI_CHECK_NULL); \
      longs.put(p.j, h); \
      name = h; \
    } else { \
      name = (*e); \
    } \
  } while (0)

#define CSTRING_TO_JSTRING(name, value) \
  JVMCIObject name; \
  do { \
    if (value != nullptr) { \
      JVMCIObject* e = strings.get(value); \
      if (e == nullptr) { \
        JVMCIObject h = JVMCIENV->create_string(value, JVMCI_CHECK_NULL); \
        strings.put(value, h); \
        name = h; \
      } else { \
        name = (*e); \
      } \
    } \
  } while (0)

jobjectArray readConfiguration0(JNIEnv *env, JVMCI_TRAPS) {
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  ResourceHashtable<jlong, JVMCIObject> longs;
  ResourceHashtable<const char*, JVMCIObject,
                    256, AnyObj::RESOURCE_AREA, mtInternal,
                    &CompilerToVM::cstring_hash, &CompilerToVM::cstring_equals> strings;

  jvalue prim;
  prim.z = true;  JVMCIObject boxedTrue =  JVMCIENV->create_box(T_BOOLEAN, &prim, JVMCI_CHECK_NULL);
  prim.z = false; JVMCIObject boxedFalse = JVMCIENV->create_box(T_BOOLEAN, &prim, JVMCI_CHECK_NULL);

  CompilerToVM::Data::initialize(JVMCI_CHECK_NULL);

  JVMCIENV->VMField_initialize(JVMCI_CHECK_NULL);
  JVMCIENV->VMFlag_initialize(JVMCI_CHECK_NULL);
  JVMCIENV->VMIntrinsicMethod_initialize(JVMCI_CHECK_NULL);

  int len = JVMCIVMStructs::localHotSpotVMStructs_count();
  JVMCIObjectArray vmFields = JVMCIENV->new_VMField_array(len, JVMCI_CHECK_NULL);
  for (int i = 0; i < len ; i++) {
    VMStructEntry vmField = JVMCIVMStructs::localHotSpotVMStructs[i];
    const size_t name_buf_size = strlen(vmField.typeName) + strlen(vmField.fieldName) + 2 + 1 /* "::" */;
    char* name_buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, name_buf_size);
    os::snprintf_checked(name_buf, name_buf_size, "%s::%s", vmField.typeName, vmField.fieldName);
    CSTRING_TO_JSTRING(name, name_buf);
    CSTRING_TO_JSTRING(type, vmField.typeString);
    JVMCIObject box;
    if (vmField.isStatic && vmField.typeString != nullptr) {
      if (strcmp(vmField.typeString, "bool") == 0) {
        BOXED_BOOLEAN(box, *(jbyte*) vmField.address);
        assert(box.is_non_null(), "must have a box");
      } else if (strcmp(vmField.typeString, "int") == 0 ||
                 strcmp(vmField.typeString, "jint") == 0 ||
                 strcmp(vmField.typeString, "uint") == 0 ||
                 strcmp(vmField.typeString, "uint32_t") == 0) {
        BOXED_LONG(box, *(jint*) vmField.address);
        assert(box.is_non_null(), "must have a box");
      } else if (strcmp(vmField.typeString, "uint64_t") == 0) {
        BOXED_LONG(box, *(uint64_t*) vmField.address);
        assert(box.is_non_null(), "must have a box");
      } else if (strcmp(vmField.typeString, "address") == 0 ||
                 strcmp(vmField.typeString, "intptr_t") == 0 ||
                 strcmp(vmField.typeString, "uintptr_t") == 0 ||
                 strcmp(vmField.typeString, "OopHandle") == 0 ||
                 strcmp(vmField.typeString, "size_t") == 0 ||
                 // All foo* types are addresses.
                 vmField.typeString[strlen(vmField.typeString) - 1] == '*') {
        BOXED_LONG(box, *((address*) vmField.address));
        assert(box.is_non_null(), "must have a box");
      } else {
        JVMCI_ERROR_NULL("VM field %s has unsupported type %s", name_buf, vmField.typeString);
      }
    }
    JVMCIObject vmFieldObj = JVMCIENV->new_VMField(name, type, vmField.offset, (jlong) vmField.address, box, JVMCI_CHECK_NULL);
    JVMCIENV->put_object_at(vmFields, i, vmFieldObj);
  }

  int ints_len = JVMCIVMStructs::localHotSpotVMIntConstants_count();
  int longs_len = JVMCIVMStructs::localHotSpotVMLongConstants_count();
  len = ints_len + longs_len;
  JVMCIObjectArray vmConstants = JVMCIENV->new_Object_array(len * 2, JVMCI_CHECK_NULL);
  int insert = 0;
  for (int i = 0; i < ints_len ; i++) {
    VMIntConstantEntry c = JVMCIVMStructs::localHotSpotVMIntConstants[i];
    CSTRING_TO_JSTRING(name, c.name);
    JVMCIObject value;
    BOXED_LONG(value, c.value);
    JVMCIENV->put_object_at(vmConstants, insert++, name);
    JVMCIENV->put_object_at(vmConstants, insert++, value);
  }
  for (int i = 0; i < longs_len ; i++) {
    VMLongConstantEntry c = JVMCIVMStructs::localHotSpotVMLongConstants[i];
    CSTRING_TO_JSTRING(name, c.name);
    JVMCIObject value;
    BOXED_LONG(value, c.value);
    JVMCIENV->put_object_at(vmConstants, insert++, name);
    JVMCIENV->put_object_at(vmConstants, insert++, value);
  }
  assert(insert == len * 2, "must be");

  len = JVMCIVMStructs::localHotSpotVMAddresses_count();
  JVMCIObjectArray vmAddresses = JVMCIENV->new_Object_array(len * 2, JVMCI_CHECK_NULL);
  for (int i = 0; i < len ; i++) {
    VMAddressEntry a = JVMCIVMStructs::localHotSpotVMAddresses[i];
    CSTRING_TO_JSTRING(name, a.name);
    JVMCIObject value;
    BOXED_LONG(value, a.value);
    JVMCIENV->put_object_at(vmAddresses, i * 2, name);
    JVMCIENV->put_object_at(vmAddresses, i * 2 + 1, value);
  }

#define COUNT_FLAG(ignore) +1
#ifdef ASSERT
#define CHECK_FLAG(type, name) { \
  const JVMFlag* flag = JVMFlag::find_declared_flag(#name); \
  assert(flag != nullptr, "No such flag named " #name); \
  assert(flag->is_##type(), "JVMFlag " #name " is not of type " #type); \
}
#else
#define CHECK_FLAG(type, name)
#endif

#define ADD_FLAG(type, name, convert) {                                                \
  CHECK_FLAG(type, name)                                                               \
  CSTRING_TO_JSTRING(fname, #name);                                                    \
  CSTRING_TO_JSTRING(ftype, #type);                                                    \
  convert(value, name);                                                                \
  JVMCIObject vmFlagObj = JVMCIENV->new_VMFlag(fname, ftype, value, JVMCI_CHECK_NULL); \
  JVMCIENV->put_object_at(vmFlags, i++, vmFlagObj);                                    \
}
#define ADD_BOOL_FLAG(name)  ADD_FLAG(bool, name, BOXED_BOOLEAN)
#define ADD_INT_FLAG(name)   ADD_FLAG(int, name, BOXED_LONG)
#define ADD_INTX_FLAG(name)  ADD_FLAG(intx, name, BOXED_LONG)
#define ADD_UINTX_FLAG(name) ADD_FLAG(uintx, name, BOXED_LONG)

  len = 0 + PREDEFINED_CONFIG_FLAGS(COUNT_FLAG, COUNT_FLAG, COUNT_FLAG, COUNT_FLAG);
  JVMCIObjectArray vmFlags = JVMCIENV->new_VMFlag_array(len, JVMCI_CHECK_NULL);
  int i = 0;
  JVMCIObject value;
  PREDEFINED_CONFIG_FLAGS(ADD_BOOL_FLAG, ADD_INT_FLAG, ADD_INTX_FLAG, ADD_UINTX_FLAG)

  JVMCIObjectArray vmIntrinsics = CompilerToVM::initialize_intrinsics(JVMCI_CHECK_NULL);

  JVMCIObjectArray data = JVMCIENV->new_Object_array(5, JVMCI_CHECK_NULL);
  JVMCIENV->put_object_at(data, 0, vmFields);
  JVMCIENV->put_object_at(data, 1, vmConstants);
  JVMCIENV->put_object_at(data, 2, vmAddresses);
  JVMCIENV->put_object_at(data, 3, vmFlags);
  JVMCIENV->put_object_at(data, 4, vmIntrinsics);

  return JVMCIENV->get_jobjectArray(data);
}
