/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_c1_CodeStubs_sparc.cpp.incl"

#define __ ce->masm()->

RangeCheckStub::RangeCheckStub(CodeEmitInfo* info, LIR_Opr index,
                               bool throw_index_out_of_bounds_exception)
  : _throw_index_out_of_bounds_exception(throw_index_out_of_bounds_exception)
  , _index(index)
{
  _info = new CodeEmitInfo(info);
}


void RangeCheckStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);

  if (_index->is_register()) {
    __ mov(_index->as_register(), G4);
  } else {
    __ set(_index->as_jint(), G4);
  }
  if (_throw_index_out_of_bounds_exception) {
    __ call(Runtime1::entry_for(Runtime1::throw_index_exception_id), relocInfo::runtime_call_type);
  } else {
    __ call(Runtime1::entry_for(Runtime1::throw_range_check_failed_id), relocInfo::runtime_call_type);
  }
  __ delayed()->nop();
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
#ifdef ASSERT
  __ should_not_reach_here();
#endif
}

#ifdef TIERED

void CounterOverflowStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);
  __ set(_bci, G4);
  __ call(Runtime1::entry_for(Runtime1::counter_overflow_id), relocInfo::runtime_call_type);
  __ delayed()->nop();
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);

  __ br(Assembler::always, true, Assembler::pt, _continuation);
  __ delayed()->nop();
}

#endif // TIERED

void DivByZeroStub::emit_code(LIR_Assembler* ce) {
  if (_offset != -1) {
    ce->compilation()->implicit_exception_table()->append(_offset, __ offset());
  }
  __ bind(_entry);
  __ call(Runtime1::entry_for(Runtime1::throw_div0_exception_id), relocInfo::runtime_call_type);
  __ delayed()->nop();
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
#ifdef ASSERT
  __ should_not_reach_here();
#endif
}


void ImplicitNullCheckStub::emit_code(LIR_Assembler* ce) {
  ce->compilation()->implicit_exception_table()->append(_offset, __ offset());
  __ bind(_entry);
  __ call(Runtime1::entry_for(Runtime1::throw_null_pointer_exception_id),
          relocInfo::runtime_call_type);
  __ delayed()->nop();
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
#ifdef ASSERT
  __ should_not_reach_here();
#endif
}


// Implementation of SimpleExceptionStub
// Note: %g1 and %g3 are already in use
void SimpleExceptionStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);
  __ call(Runtime1::entry_for(_stub), relocInfo::runtime_call_type);

  if (_obj->is_valid()) {
    __ delayed()->mov(_obj->as_register(), G4); // _obj contains the optional argument to the stub
  } else {
    __ delayed()->mov(G0, G4);
  }
  ce->add_call_info_here(_info);
#ifdef ASSERT
  __ should_not_reach_here();
#endif
}


// Implementation of ArrayStoreExceptionStub

ArrayStoreExceptionStub::ArrayStoreExceptionStub(CodeEmitInfo* info):
  _info(info) {
}


void ArrayStoreExceptionStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);
  __ call(Runtime1::entry_for(Runtime1::throw_array_store_exception_id), relocInfo::runtime_call_type);
  __ delayed()->nop();
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
#ifdef ASSERT
  __ should_not_reach_here();
#endif
}




// Implementation of NewInstanceStub

NewInstanceStub::NewInstanceStub(LIR_Opr klass_reg, LIR_Opr result, ciInstanceKlass* klass, CodeEmitInfo* info, Runtime1::StubID stub_id) {
  _result = result;
  _klass = klass;
  _klass_reg = klass_reg;
  _info = new CodeEmitInfo(info);
  assert(stub_id == Runtime1::new_instance_id                 ||
         stub_id == Runtime1::fast_new_instance_id            ||
         stub_id == Runtime1::fast_new_instance_init_check_id,
         "need new_instance id");
  _stub_id   = stub_id;
}


void NewInstanceStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);
  __ call(Runtime1::entry_for(_stub_id), relocInfo::runtime_call_type);
  __ delayed()->mov_or_nop(_klass_reg->as_register(), G5);
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
  __ br(Assembler::always, false, Assembler::pt, _continuation);
  __ delayed()->mov_or_nop(O0, _result->as_register());
}


// Implementation of NewTypeArrayStub
NewTypeArrayStub::NewTypeArrayStub(LIR_Opr klass_reg, LIR_Opr length, LIR_Opr result, CodeEmitInfo* info) {
  _klass_reg = klass_reg;
  _length = length;
  _result = result;
  _info = new CodeEmitInfo(info);
}


void NewTypeArrayStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);

  __ mov(_length->as_register(), G4);
  __ call(Runtime1::entry_for(Runtime1::new_type_array_id), relocInfo::runtime_call_type);
  __ delayed()->mov_or_nop(_klass_reg->as_register(), G5);
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
  __ br(Assembler::always, false, Assembler::pt, _continuation);
  __ delayed()->mov_or_nop(O0, _result->as_register());
}


// Implementation of NewObjectArrayStub

NewObjectArrayStub::NewObjectArrayStub(LIR_Opr klass_reg, LIR_Opr length, LIR_Opr result, CodeEmitInfo* info) {
  _klass_reg = klass_reg;
  _length = length;
  _result = result;
  _info = new CodeEmitInfo(info);
}


void NewObjectArrayStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);

  __ mov(_length->as_register(), G4);
  __ call(Runtime1::entry_for(Runtime1::new_object_array_id), relocInfo::runtime_call_type);
  __ delayed()->mov_or_nop(_klass_reg->as_register(), G5);
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
  __ br(Assembler::always, false, Assembler::pt, _continuation);
  __ delayed()->mov_or_nop(O0, _result->as_register());
}


// Implementation of MonitorAccessStubs
MonitorEnterStub::MonitorEnterStub(LIR_Opr obj_reg, LIR_Opr lock_reg, CodeEmitInfo* info)
  : MonitorAccessStub(obj_reg, lock_reg) {
  _info = new CodeEmitInfo(info);
}


void MonitorEnterStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);
  __ mov(_obj_reg->as_register(), G4);
  if (ce->compilation()->has_fpu_code()) {
    __ call(Runtime1::entry_for(Runtime1::monitorenter_id), relocInfo::runtime_call_type);
  } else {
    __ call(Runtime1::entry_for(Runtime1::monitorenter_nofpu_id), relocInfo::runtime_call_type);
  }
  __ delayed()->mov_or_nop(_lock_reg->as_register(), G5);
  ce->add_call_info_here(_info);
  ce->verify_oop_map(_info);
  __ br(Assembler::always, true, Assembler::pt, _continuation);
  __ delayed()->nop();
}


void MonitorExitStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);
  if (_compute_lock) {
    ce->monitor_address(_monitor_ix, _lock_reg);
  }
  if (ce->compilation()->has_fpu_code()) {
    __ call(Runtime1::entry_for(Runtime1::monitorexit_id), relocInfo::runtime_call_type);
  } else {
    __ call(Runtime1::entry_for(Runtime1::monitorexit_nofpu_id), relocInfo::runtime_call_type);
  }

  __ delayed()->mov_or_nop(_lock_reg->as_register(), G4);
  __ br(Assembler::always, true, Assembler::pt, _continuation);
  __ delayed()->nop();
}

// Implementation of patching:
// - Copy the code at given offset to an inlined buffer (first the bytes, then the number of bytes)
// - Replace original code with a call to the stub
// At Runtime:
// - call to stub, jump to runtime
// - in runtime: preserve all registers (especially objects, i.e., source and destination object)
// - in runtime: after initializing class, restore original code, reexecute instruction

int PatchingStub::_patch_info_offset = -NativeGeneralJump::instruction_size;

void PatchingStub::align_patch_site(MacroAssembler* ) {
  // patch sites on sparc are always properly aligned.
}

void PatchingStub::emit_code(LIR_Assembler* ce) {
  // copy original code here
  assert(NativeCall::instruction_size <= _bytes_to_copy && _bytes_to_copy <= 0xFF,
         "not enough room for call");
  assert((_bytes_to_copy & 0x3) == 0, "must copy a multiple of four bytes");

  Label call_patch;

  int being_initialized_entry = __ offset();

  if (_id == load_klass_id) {
    // produce a copy of the load klass instruction for use by the being initialized case
#ifdef ASSERT
    address start = __ pc();
#endif
    AddressLiteral addrlit(NULL, oop_Relocation::spec(_oop_index));
    __ patchable_set(addrlit, _obj);

#ifdef ASSERT
    for (int i = 0; i < _bytes_to_copy; i++) {
      address ptr = (address)(_pc_start + i);
      int a_byte = (*ptr) & 0xFF;
      assert(a_byte == *start++, "should be the same code");
    }
#endif
  } else {
    // make a copy the code which is going to be patched.
    for (int i = 0; i < _bytes_to_copy; i++) {
      address ptr = (address)(_pc_start + i);
      int a_byte = (*ptr) & 0xFF;
      __ a_byte (a_byte);
    }
  }

  address end_of_patch = __ pc();
  int bytes_to_skip = 0;
  if (_id == load_klass_id) {
    int offset = __ offset();
    if (CommentedAssembly) {
      __ block_comment(" being_initialized check");
    }

    // static field accesses have special semantics while the class
    // initializer is being run so we emit a test which can be used to
    // check that this code is being executed by the initializing
    // thread.
    assert(_obj != noreg, "must be a valid register");
    assert(_oop_index >= 0, "must have oop index");
    __ ld_ptr(_obj, instanceKlass::init_thread_offset_in_bytes() + sizeof(klassOopDesc), G3);
    __ cmp(G2_thread, G3);
    __ br(Assembler::notEqual, false, Assembler::pn, call_patch);
    __ delayed()->nop();

    // load_klass patches may execute the patched code before it's
    // copied back into place so we need to jump back into the main
    // code of the nmethod to continue execution.
    __ br(Assembler::always, false, Assembler::pt, _patch_site_continuation);
    __ delayed()->nop();

    // make sure this extra code gets skipped
    bytes_to_skip += __ offset() - offset;
  }

  // Now emit the patch record telling the runtime how to find the
  // pieces of the patch.  We only need 3 bytes but it has to be
  // aligned as an instruction so emit 4 bytes.
  int sizeof_patch_record = 4;
  bytes_to_skip += sizeof_patch_record;

  // emit the offsets needed to find the code to patch
  int being_initialized_entry_offset = __ offset() - being_initialized_entry + sizeof_patch_record;

  // Emit the patch record.  We need to emit a full word, so emit an extra empty byte
  __ a_byte(0);
  __ a_byte(being_initialized_entry_offset);
  __ a_byte(bytes_to_skip);
  __ a_byte(_bytes_to_copy);
  address patch_info_pc = __ pc();
  assert(patch_info_pc - end_of_patch == bytes_to_skip, "incorrect patch info");

  address entry = __ pc();
  NativeGeneralJump::insert_unconditional((address)_pc_start, entry);
  address target = NULL;
  switch (_id) {
    case access_field_id:  target = Runtime1::entry_for(Runtime1::access_field_patching_id); break;
    case load_klass_id:    target = Runtime1::entry_for(Runtime1::load_klass_patching_id); break;
    default: ShouldNotReachHere();
  }
  __ bind(call_patch);

  if (CommentedAssembly) {
    __ block_comment("patch entry point");
  }
  __ call(target, relocInfo::runtime_call_type);
  __ delayed()->nop();
  assert(_patch_info_offset == (patch_info_pc - __ pc()), "must not change");
  ce->add_call_info_here(_info);
  __ br(Assembler::always, false, Assembler::pt, _patch_site_entry);
  __ delayed()->nop();
  if (_id == load_klass_id) {
    CodeSection* cs = __ code_section();
    address pc = (address)_pc_start;
    RelocIterator iter(cs, pc, pc + 1);
    relocInfo::change_reloc_info_for_address(&iter, (address) pc, relocInfo::oop_type, relocInfo::none);

    pc = (address)(_pc_start + NativeMovConstReg::add_offset);
    RelocIterator iter2(cs, pc, pc+1);
    relocInfo::change_reloc_info_for_address(&iter2, (address) pc, relocInfo::oop_type, relocInfo::none);
  }

}

void ArrayCopyStub::emit_code(LIR_Assembler* ce) {
  //---------------slow case: call to native-----------------
  __ bind(_entry);
  __ mov(src()->as_register(),     O0);
  __ mov(src_pos()->as_register(), O1);
  __ mov(dst()->as_register(),     O2);
  __ mov(dst_pos()->as_register(), O3);
  __ mov(length()->as_register(),  O4);

  ce->emit_static_call_stub();

  __ call(SharedRuntime::get_resolve_static_call_stub(), relocInfo::static_call_type);
  __ delayed()->nop();
  ce->add_call_info_here(info());
  ce->verify_oop_map(info());

#ifndef PRODUCT
  __ set((intptr_t)&Runtime1::_arraycopy_slowcase_cnt, O0);
  __ ld(O0, 0, O1);
  __ inc(O1);
  __ st(O1, 0, O0);
#endif

  __ br(Assembler::always, false, Assembler::pt, _continuation);
  __ delayed()->nop();
}


///////////////////////////////////////////////////////////////////////////////////
#ifndef SERIALGC

void G1PreBarrierStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);

  assert(pre_val()->is_register(), "Precondition.");

  Register pre_val_reg = pre_val()->as_register();

  ce->mem2reg(addr(), pre_val(), T_OBJECT, patch_code(), info(), false);
  __ br_on_reg_cond(Assembler::rc_z, /*annul*/false, Assembler::pt,
                    pre_val_reg, _continuation);
  __ delayed()->nop();

  __ call(Runtime1::entry_for(Runtime1::Runtime1::g1_pre_barrier_slow_id));
  __ delayed()->mov(pre_val_reg, G4);
  __ br(Assembler::always, false, Assembler::pt, _continuation);
  __ delayed()->nop();

}

jbyte* G1PostBarrierStub::_byte_map_base = NULL;

jbyte* G1PostBarrierStub::byte_map_base_slow() {
  BarrierSet* bs = Universe::heap()->barrier_set();
  assert(bs->is_a(BarrierSet::G1SATBCTLogging),
         "Must be if we're using this.");
  return ((G1SATBCardTableModRefBS*)bs)->byte_map_base;
}

void G1PostBarrierStub::emit_code(LIR_Assembler* ce) {
  __ bind(_entry);

  assert(addr()->is_register(), "Precondition.");
  assert(new_val()->is_register(), "Precondition.");
  Register addr_reg = addr()->as_pointer_register();
  Register new_val_reg = new_val()->as_register();
  __ br_on_reg_cond(Assembler::rc_z, /*annul*/false, Assembler::pt,
                    new_val_reg, _continuation);
  __ delayed()->nop();

  __ call(Runtime1::entry_for(Runtime1::Runtime1::g1_post_barrier_slow_id));
  __ delayed()->mov(addr_reg, G4);
  __ br(Assembler::always, false, Assembler::pt, _continuation);
  __ delayed()->nop();
}

#endif // SERIALGC
///////////////////////////////////////////////////////////////////////////////////

#undef __
