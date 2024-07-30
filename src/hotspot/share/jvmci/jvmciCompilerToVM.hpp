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

#ifndef SHARE_JVMCI_JVMCICOMPILERTOVM_HPP
#define SHARE_JVMCI_JVMCICOMPILERTOVM_HPP

#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/cardTable.hpp"
#include "jvmci/jvmciExceptions.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/signature.hpp"

class CollectedHeap;
class JVMCIObjectArray;

class CompilerToVM {
 public:
  class Data {
    friend class JVMCIVMStructs;

   private:
    static int Klass_vtable_start_offset;
    static int Klass_vtable_length_offset;

    static int Method_extra_stack_entries;

    static address SharedRuntime_ic_miss_stub;
    static address SharedRuntime_handle_wrong_method_stub;
    static address SharedRuntime_deopt_blob_unpack;
    static address SharedRuntime_deopt_blob_unpack_with_exception_in_tls;
    static address SharedRuntime_deopt_blob_uncommon_trap;
    static address SharedRuntime_polling_page_return_handler;

    static address nmethod_entry_barrier;
    static int thread_disarmed_guard_value_offset;
    static int thread_address_bad_mask_offset;
#ifdef AARCH64
    static int BarrierSetAssembler_nmethod_patching_type;
    static address BarrierSetAssembler_patching_epoch_addr;
#endif

    static address ZBarrierSetRuntime_load_barrier_on_oop_field_preloaded;
    static address ZBarrierSetRuntime_load_barrier_on_weak_oop_field_preloaded;
    static address ZBarrierSetRuntime_load_barrier_on_phantom_oop_field_preloaded;
    static address ZBarrierSetRuntime_weak_load_barrier_on_oop_field_preloaded;
    static address ZBarrierSetRuntime_weak_load_barrier_on_weak_oop_field_preloaded;
    static address ZBarrierSetRuntime_weak_load_barrier_on_phantom_oop_field_preloaded;
    static address ZBarrierSetRuntime_load_barrier_on_oop_array;
    static address ZBarrierSetRuntime_clone;

    static address ZPointerVectorLoadBadMask_address;
    static address ZPointerVectorStoreBadMask_address;
    static address ZPointerVectorStoreGoodMask_address;

    static bool continuations_enabled;

    static size_t ThreadLocalAllocBuffer_alignment_reserve;

    static CollectedHeap* Universe_collectedHeap;
    static int Universe_base_vtable_size;
    static address Universe_narrow_oop_base;
    static int Universe_narrow_oop_shift;
    static address Universe_narrow_klass_base;
    static int Universe_narrow_klass_shift;
    static uintptr_t Universe_verify_oop_mask;
    static uintptr_t Universe_verify_oop_bits;
    static void* Universe_non_oop_bits;

    static bool _supports_inline_contig_alloc;
    static HeapWord** _heap_end_addr;
    static HeapWord* volatile* _heap_top_addr;
    static int _max_oop_map_stack_offset;
    static int _fields_annotations_base_offset;

    static CardTable::CardValue* cardtable_start_address;
    static int cardtable_shift;

    static size_t vm_page_size;

    static int sizeof_vtableEntry;
    static int sizeof_ExceptionTableElement;
    static int sizeof_LocalVariableTableElement;
    static int sizeof_ConstantPool;
    static int sizeof_narrowKlass;
    static int sizeof_arrayOopDesc;
    static int sizeof_BasicLock;
#if INCLUDE_ZGC
    static int sizeof_ZStoreBarrierEntry;
#endif

#ifdef X86
    static int L1_line_size;
#endif

    static address dsin;
    static address dcos;
    static address dtan;
    static address dexp;
    static address dlog;
    static address dlog10;
    static address dpow;

    static address symbol_init;
    static address symbol_clinit;

    // Minimum alignment of an offset into CodeBuffer::SECT_CONSTS
    static int data_section_item_alignment;

#if INCLUDE_JVMTI
    /*
     * Pointer to JvmtiExport::_should_notify_object_alloc.
     * Exposed as an int* instead of an address so the
     * underlying type is part of the JVMCIVMStructs definition.
     */
    static int* _should_notify_object_alloc;
#endif

   public:
     static void initialize(JVMCI_TRAPS);

    static int max_oop_map_stack_offset() {
      assert(_max_oop_map_stack_offset > 0, "must be initialized");
      return Data::_max_oop_map_stack_offset;
    }

    static int get_data_section_item_alignment() {
      return data_section_item_alignment;
    }
  };

  static bool cstring_equals(const char* const& s0, const char* const& s1) {
    return strcmp(s0, s1) == 0;
  }

  static unsigned cstring_hash(const char* const& s) {
    unsigned h = 0;
    const char* p = s;
    while (*p != '\0') {
      h = 31 * h + *p;
      p++;
    }
    return h;
  }

  static JNINativeMethod methods[];
  static JNINativeMethod jni_methods[];

  static JVMCIObjectArray initialize_intrinsics(JVMCI_TRAPS);
 public:
  static int methods_count();

};
#endif // SHARE_JVMCI_JVMCICOMPILERTOVM_HPP
