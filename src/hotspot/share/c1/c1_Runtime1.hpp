/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_C1_C1_RUNTIME1_HPP
#define SHARE_C1_C1_RUNTIME1_HPP

#include "c1/c1_FrameMap.hpp"
#include "code/stubs.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/allStatic.hpp"
#include "runtime/stubDeclarations.hpp"
#include "runtime/stubInfo.hpp"

class StubAssembler;

// The Runtime1 holds all assembly stubs and VM
// runtime routines needed by code code generated
// by the Compiler1.

class StubAssemblerCodeGenClosure: public Closure {
 public:
  virtual OopMapSet* generate_code(StubAssembler* sasm) = 0;
};

class Runtime1: public AllStatic {
  friend class ArrayCopyStub;
  friend class AOTCodeAddressTable;

public:
  // statistics
#ifndef PRODUCT
  static uint _generic_arraycopystub_cnt;
  static uint _arraycopy_slowcase_cnt;
  static uint _arraycopy_checkcast_cnt;
  static uint _arraycopy_checkcast_attempt_cnt;
  static uint _new_type_array_slowcase_cnt;
  static uint _new_object_array_slowcase_cnt;
  static uint _new_instance_slowcase_cnt;
  static uint _new_multi_array_slowcase_cnt;
  static uint _monitorenter_slowcase_cnt;
  static uint _monitorexit_slowcase_cnt;
  static uint _patch_code_slowcase_cnt;
  static uint _throw_range_check_exception_count;
  static uint _throw_index_exception_count;
  static uint _throw_div0_exception_count;
  static uint _throw_null_pointer_exception_count;
  static uint _throw_class_cast_exception_count;
  static uint _throw_incompatible_class_change_error_count;
  static uint _throw_count;
#endif

 private:
  static CodeBlob* _blobs[(int)StubInfo::C1_STUB_COUNT];

  // stub generation
 public:
  static CodeBlob*  generate_blob(BufferBlob* buffer_blob, StubId id, const char* name, bool expect_oop_map, StubAssemblerCodeGenClosure *cl);
  static bool       generate_blob_for(BufferBlob* blob, StubId id);
  static OopMapSet* generate_code_for(StubId id, StubAssembler* sasm);
 private:
  static OopMapSet* generate_exception_throw(StubAssembler* sasm, address target, bool has_argument);
  static OopMapSet* generate_handle_exception(StubId id, StubAssembler* sasm);
  static void       generate_unwind_exception(StubAssembler *sasm);
  static OopMapSet* generate_patching(StubAssembler* sasm, address target);

  static OopMapSet* generate_stub_call(StubAssembler* sasm, Register result, address entry,
                                       Register arg1 = noreg, Register arg2 = noreg, Register arg3 = noreg);

  // runtime entry points
  static void new_instance    (JavaThread* current, Klass* klass);
  static void new_type_array  (JavaThread* current, Klass* klass, jint length);
  static void new_object_array(JavaThread* current, Klass* klass, jint length);
  static void new_multi_array (JavaThread* current, Klass* klass, int rank, jint* dims);

  static address counter_overflow(JavaThread* current, int bci, Method* method);

  static void unimplemented_entry(JavaThread* current, StubId id);

  static address exception_handler_for_pc(JavaThread* current);

  static void throw_range_check_exception(JavaThread* current, int index, arrayOopDesc* a);
  static void throw_index_exception(JavaThread* current, int index);
  static void throw_div0_exception(JavaThread* current);
  static void throw_null_pointer_exception(JavaThread* current);
  static void throw_class_cast_exception(JavaThread* current, oopDesc* object);
  static void throw_incompatible_class_change_error(JavaThread* current);
  static void throw_array_store_exception(JavaThread* current, oopDesc* object);

  static void monitorenter(JavaThread* current, oopDesc* obj, BasicObjectLock* lock);
  static void monitorexit (JavaThread* current, BasicObjectLock* lock);

  static void deoptimize(JavaThread* current, jint trap_request);

  static int access_field_patching(JavaThread* current);
  static int move_klass_patching(JavaThread* current);
  static int move_mirror_patching(JavaThread* current);
  static int move_appendix_patching(JavaThread* current);

  static void patch_code(JavaThread* current, StubId stub_id);

 public:
  // initialization
  static bool initialize(BufferBlob* blob);
  static void initialize_pd();

  // return offset in words
  static uint runtime_blob_current_thread_offset(frame f);

  // stubs
  static CodeBlob* blob_for (StubId id);
  static address   entry_for(StubId id)          { return blob_for(id)->code_begin(); }
  static const char* name_for (StubId id);
  static const char* name_for_address(address entry);

  // platform might add runtime names.
  static const char* pd_name_for_address(address entry);

  // method tracing
  static void trace_block_entry(jint block_id);

#ifndef PRODUCT
  static address throw_count_address()               { return (address)&_throw_count;             }
  static address arraycopy_count_address(BasicType type);
#endif

  // directly accessible leaf routine
  static int  is_instance_of(oopDesc* mirror, oopDesc* obj);

  static void predicate_failed_trap(JavaThread* current);

  static void check_abort_on_vm_exception(oopDesc* ex);

  static void print_statistics()                 PRODUCT_RETURN;
};

#endif // SHARE_C1_C1_RUNTIME1_HPP
