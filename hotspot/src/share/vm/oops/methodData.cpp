/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/heapInspection.hpp"
#include "oops/methodData.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/handles.inline.hpp"

// ==================================================================
// DataLayout
//
// Overlay for generic profiling data.

// Some types of data layouts need a length field.
bool DataLayout::needs_array_len(u1 tag) {
  return (tag == multi_branch_data_tag) || (tag == arg_info_data_tag);
}

// Perform generic initialization of the data.  More specific
// initialization occurs in overrides of ProfileData::post_initialize.
void DataLayout::initialize(u1 tag, u2 bci, int cell_count) {
  _header._bits = (intptr_t)0;
  _header._struct._tag = tag;
  _header._struct._bci = bci;
  for (int i = 0; i < cell_count; i++) {
    set_cell_at(i, (intptr_t)0);
  }
  if (needs_array_len(tag)) {
    set_cell_at(ArrayData::array_len_off_set, cell_count - 1); // -1 for header.
  }
}

void DataLayout::clean_weak_klass_links(BoolObjectClosure* cl) {
  ResourceMark m;
  data_in()->clean_weak_klass_links(cl);
}


// ==================================================================
// ProfileData
//
// A ProfileData object is created to refer to a section of profiling
// data in a structured way.

// Constructor for invalid ProfileData.
ProfileData::ProfileData() {
  _data = NULL;
}

#ifndef PRODUCT
void ProfileData::print_shared(outputStream* st, const char* name) {
  st->print("bci: %d", bci());
  st->fill_to(tab_width_one);
  st->print("%s", name);
  tab(st);
  int trap = trap_state();
  if (trap != 0) {
    char buf[100];
    st->print("trap(%s) ", Deoptimization::format_trap_state(buf, sizeof(buf), trap));
  }
  int flags = data()->flags();
  if (flags != 0)
    st->print("flags(%d) ", flags);
}

void ProfileData::tab(outputStream* st) {
  st->fill_to(tab_width_two);
}
#endif // !PRODUCT

// ==================================================================
// BitData
//
// A BitData corresponds to a one-bit flag.  This is used to indicate
// whether a checkcast bytecode has seen a null value.


#ifndef PRODUCT
void BitData::print_data_on(outputStream* st) {
  print_shared(st, "BitData");
}
#endif // !PRODUCT

// ==================================================================
// CounterData
//
// A CounterData corresponds to a simple counter.

#ifndef PRODUCT
void CounterData::print_data_on(outputStream* st) {
  print_shared(st, "CounterData");
  st->print_cr("count(%u)", count());
}
#endif // !PRODUCT

// ==================================================================
// JumpData
//
// A JumpData is used to access profiling information for a direct
// branch.  It is a counter, used for counting the number of branches,
// plus a data displacement, used for realigning the data pointer to
// the corresponding target bci.

void JumpData::post_initialize(BytecodeStream* stream, MethodData* mdo) {
  assert(stream->bci() == bci(), "wrong pos");
  int target;
  Bytecodes::Code c = stream->code();
  if (c == Bytecodes::_goto_w || c == Bytecodes::_jsr_w) {
    target = stream->dest_w();
  } else {
    target = stream->dest();
  }
  int my_di = mdo->dp_to_di(dp());
  int target_di = mdo->bci_to_di(target);
  int offset = target_di - my_di;
  set_displacement(offset);
}

#ifndef PRODUCT
void JumpData::print_data_on(outputStream* st) {
  print_shared(st, "JumpData");
  st->print_cr("taken(%u) displacement(%d)", taken(), displacement());
}
#endif // !PRODUCT

// ==================================================================
// ReceiverTypeData
//
// A ReceiverTypeData is used to access profiling information about a
// dynamic type check.  It consists of a counter which counts the total times
// that the check is reached, and a series of (Klass*, count) pairs
// which are used to store a type profile for the receiver of the check.

void ReceiverTypeData::clean_weak_klass_links(BoolObjectClosure* is_alive_cl) {
    for (uint row = 0; row < row_limit(); row++) {
    Klass* p = receiver(row);
    if (p != NULL && !p->is_loader_alive(is_alive_cl)) {
      clear_row(row);
    }
  }
}

#ifndef PRODUCT
void ReceiverTypeData::print_receiver_data_on(outputStream* st) {
  uint row;
  int entries = 0;
  for (row = 0; row < row_limit(); row++) {
    if (receiver(row) != NULL)  entries++;
  }
  st->print_cr("count(%u) entries(%u)", count(), entries);
  int total = count();
  for (row = 0; row < row_limit(); row++) {
    if (receiver(row) != NULL) {
      total += receiver_count(row);
    }
  }
  for (row = 0; row < row_limit(); row++) {
    if (receiver(row) != NULL) {
      tab(st);
      receiver(row)->print_value_on(st);
      st->print_cr("(%u %4.2f)", receiver_count(row), (float) receiver_count(row) / (float) total);
    }
  }
}
void ReceiverTypeData::print_data_on(outputStream* st) {
  print_shared(st, "ReceiverTypeData");
  print_receiver_data_on(st);
}
void VirtualCallData::print_data_on(outputStream* st) {
  print_shared(st, "VirtualCallData");
  print_receiver_data_on(st);
}
#endif // !PRODUCT

// ==================================================================
// RetData
//
// A RetData is used to access profiling information for a ret bytecode.
// It is composed of a count of the number of times that the ret has
// been executed, followed by a series of triples of the form
// (bci, count, di) which count the number of times that some bci was the
// target of the ret and cache a corresponding displacement.

void RetData::post_initialize(BytecodeStream* stream, MethodData* mdo) {
  for (uint row = 0; row < row_limit(); row++) {
    set_bci_displacement(row, -1);
    set_bci(row, no_bci);
  }
  // release so other threads see a consistent state.  bci is used as
  // a valid flag for bci_displacement.
  OrderAccess::release();
}

// This routine needs to atomically update the RetData structure, so the
// caller needs to hold the RetData_lock before it gets here.  Since taking
// the lock can block (and allow GC) and since RetData is a ProfileData is a
// wrapper around a derived oop, taking the lock in _this_ method will
// basically cause the 'this' pointer's _data field to contain junk after the
// lock.  We require the caller to take the lock before making the ProfileData
// structure.  Currently the only caller is InterpreterRuntime::update_mdp_for_ret
address RetData::fixup_ret(int return_bci, MethodData* h_mdo) {
  // First find the mdp which corresponds to the return bci.
  address mdp = h_mdo->bci_to_dp(return_bci);

  // Now check to see if any of the cache slots are open.
  for (uint row = 0; row < row_limit(); row++) {
    if (bci(row) == no_bci) {
      set_bci_displacement(row, mdp - dp());
      set_bci_count(row, DataLayout::counter_increment);
      // Barrier to ensure displacement is written before the bci; allows
      // the interpreter to read displacement without fear of race condition.
      release_set_bci(row, return_bci);
      break;
    }
  }
  return mdp;
}

#ifdef CC_INTERP
DataLayout* RetData::advance(MethodData *md, int bci) {
  return (DataLayout*) md->bci_to_dp(bci);
}
#endif // CC_INTERP

#ifndef PRODUCT
void RetData::print_data_on(outputStream* st) {
  print_shared(st, "RetData");
  uint row;
  int entries = 0;
  for (row = 0; row < row_limit(); row++) {
    if (bci(row) != no_bci)  entries++;
  }
  st->print_cr("count(%u) entries(%u)", count(), entries);
  for (row = 0; row < row_limit(); row++) {
    if (bci(row) != no_bci) {
      tab(st);
      st->print_cr("bci(%d: count(%u) displacement(%d))",
                   bci(row), bci_count(row), bci_displacement(row));
    }
  }
}
#endif // !PRODUCT

// ==================================================================
// BranchData
//
// A BranchData is used to access profiling data for a two-way branch.
// It consists of taken and not_taken counts as well as a data displacement
// for the taken case.

void BranchData::post_initialize(BytecodeStream* stream, MethodData* mdo) {
  assert(stream->bci() == bci(), "wrong pos");
  int target = stream->dest();
  int my_di = mdo->dp_to_di(dp());
  int target_di = mdo->bci_to_di(target);
  int offset = target_di - my_di;
  set_displacement(offset);
}

#ifndef PRODUCT
void BranchData::print_data_on(outputStream* st) {
  print_shared(st, "BranchData");
  st->print_cr("taken(%u) displacement(%d)",
               taken(), displacement());
  tab(st);
  st->print_cr("not taken(%u)", not_taken());
}
#endif

// ==================================================================
// MultiBranchData
//
// A MultiBranchData is used to access profiling information for
// a multi-way branch (*switch bytecodes).  It consists of a series
// of (count, displacement) pairs, which count the number of times each
// case was taken and specify the data displacment for each branch target.

int MultiBranchData::compute_cell_count(BytecodeStream* stream) {
  int cell_count = 0;
  if (stream->code() == Bytecodes::_tableswitch) {
    Bytecode_tableswitch sw(stream->method()(), stream->bcp());
    cell_count = 1 + per_case_cell_count * (1 + sw.length()); // 1 for default
  } else {
    Bytecode_lookupswitch sw(stream->method()(), stream->bcp());
    cell_count = 1 + per_case_cell_count * (sw.number_of_pairs() + 1); // 1 for default
  }
  return cell_count;
}

void MultiBranchData::post_initialize(BytecodeStream* stream,
                                      MethodData* mdo) {
  assert(stream->bci() == bci(), "wrong pos");
  int target;
  int my_di;
  int target_di;
  int offset;
  if (stream->code() == Bytecodes::_tableswitch) {
    Bytecode_tableswitch sw(stream->method()(), stream->bcp());
    int len = sw.length();
    assert(array_len() == per_case_cell_count * (len + 1), "wrong len");
    for (int count = 0; count < len; count++) {
      target = sw.dest_offset_at(count) + bci();
      my_di = mdo->dp_to_di(dp());
      target_di = mdo->bci_to_di(target);
      offset = target_di - my_di;
      set_displacement_at(count, offset);
    }
    target = sw.default_offset() + bci();
    my_di = mdo->dp_to_di(dp());
    target_di = mdo->bci_to_di(target);
    offset = target_di - my_di;
    set_default_displacement(offset);

  } else {
    Bytecode_lookupswitch sw(stream->method()(), stream->bcp());
    int npairs = sw.number_of_pairs();
    assert(array_len() == per_case_cell_count * (npairs + 1), "wrong len");
    for (int count = 0; count < npairs; count++) {
      LookupswitchPair pair = sw.pair_at(count);
      target = pair.offset() + bci();
      my_di = mdo->dp_to_di(dp());
      target_di = mdo->bci_to_di(target);
      offset = target_di - my_di;
      set_displacement_at(count, offset);
    }
    target = sw.default_offset() + bci();
    my_di = mdo->dp_to_di(dp());
    target_di = mdo->bci_to_di(target);
    offset = target_di - my_di;
    set_default_displacement(offset);
  }
}

#ifndef PRODUCT
void MultiBranchData::print_data_on(outputStream* st) {
  print_shared(st, "MultiBranchData");
  st->print_cr("default_count(%u) displacement(%d)",
               default_count(), default_displacement());
  int cases = number_of_cases();
  for (int i = 0; i < cases; i++) {
    tab(st);
    st->print_cr("count(%u) displacement(%d)",
                 count_at(i), displacement_at(i));
  }
}
#endif

#ifndef PRODUCT
void ArgInfoData::print_data_on(outputStream* st) {
  print_shared(st, "ArgInfoData");
  int nargs = number_of_args();
  for (int i = 0; i < nargs; i++) {
    st->print("  0x%x", arg_modified(i));
  }
  st->cr();
}

#endif
// ==================================================================
// MethodData*
//
// A MethodData* holds information which has been collected about
// a method.

MethodData* MethodData::allocate(ClassLoaderData* loader_data, methodHandle method, TRAPS) {
  int size = MethodData::compute_allocation_size_in_words(method);

  return new (loader_data, size, false, MetaspaceObj::MethodDataType, THREAD)
    MethodData(method(), size, CHECK_NULL);
}

int MethodData::bytecode_cell_count(Bytecodes::Code code) {
#if defined(COMPILER1) && !defined(COMPILER2)
  return no_profile_data;
#else
  switch (code) {
  case Bytecodes::_checkcast:
  case Bytecodes::_instanceof:
  case Bytecodes::_aastore:
    if (TypeProfileCasts) {
      return ReceiverTypeData::static_cell_count();
    } else {
      return BitData::static_cell_count();
    }
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokestatic:
    return CounterData::static_cell_count();
  case Bytecodes::_goto:
  case Bytecodes::_goto_w:
  case Bytecodes::_jsr:
  case Bytecodes::_jsr_w:
    return JumpData::static_cell_count();
  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokeinterface:
    return VirtualCallData::static_cell_count();
  case Bytecodes::_invokedynamic:
    return CounterData::static_cell_count();
  case Bytecodes::_ret:
    return RetData::static_cell_count();
  case Bytecodes::_ifeq:
  case Bytecodes::_ifne:
  case Bytecodes::_iflt:
  case Bytecodes::_ifge:
  case Bytecodes::_ifgt:
  case Bytecodes::_ifle:
  case Bytecodes::_if_icmpeq:
  case Bytecodes::_if_icmpne:
  case Bytecodes::_if_icmplt:
  case Bytecodes::_if_icmpge:
  case Bytecodes::_if_icmpgt:
  case Bytecodes::_if_icmple:
  case Bytecodes::_if_acmpeq:
  case Bytecodes::_if_acmpne:
  case Bytecodes::_ifnull:
  case Bytecodes::_ifnonnull:
    return BranchData::static_cell_count();
  case Bytecodes::_lookupswitch:
  case Bytecodes::_tableswitch:
    return variable_cell_count;
  }
  return no_profile_data;
#endif
}

// Compute the size of the profiling information corresponding to
// the current bytecode.
int MethodData::compute_data_size(BytecodeStream* stream) {
  int cell_count = bytecode_cell_count(stream->code());
  if (cell_count == no_profile_data) {
    return 0;
  }
  if (cell_count == variable_cell_count) {
    cell_count = MultiBranchData::compute_cell_count(stream);
  }
  // Note:  cell_count might be zero, meaning that there is just
  //        a DataLayout header, with no extra cells.
  assert(cell_count >= 0, "sanity");
  return DataLayout::compute_size_in_bytes(cell_count);
}

int MethodData::compute_extra_data_count(int data_size, int empty_bc_count) {
  if (ProfileTraps) {
    // Assume that up to 3% of BCIs with no MDP will need to allocate one.
    int extra_data_count = (uint)(empty_bc_count * 3) / 128 + 1;
    // If the method is large, let the extra BCIs grow numerous (to ~1%).
    int one_percent_of_data
      = (uint)data_size / (DataLayout::header_size_in_bytes()*128);
    if (extra_data_count < one_percent_of_data)
      extra_data_count = one_percent_of_data;
    if (extra_data_count > empty_bc_count)
      extra_data_count = empty_bc_count;  // no need for more
    return extra_data_count;
  } else {
    return 0;
  }
}

// Compute the size of the MethodData* necessary to store
// profiling information about a given method.  Size is in bytes.
int MethodData::compute_allocation_size_in_bytes(methodHandle method) {
  int data_size = 0;
  BytecodeStream stream(method);
  Bytecodes::Code c;
  int empty_bc_count = 0;  // number of bytecodes lacking data
  while ((c = stream.next()) >= 0) {
    int size_in_bytes = compute_data_size(&stream);
    data_size += size_in_bytes;
    if (size_in_bytes == 0)  empty_bc_count += 1;
  }
  int object_size = in_bytes(data_offset()) + data_size;

  // Add some extra DataLayout cells (at least one) to track stray traps.
  int extra_data_count = compute_extra_data_count(data_size, empty_bc_count);
  object_size += extra_data_count * DataLayout::compute_size_in_bytes(0);

  // Add a cell to record information about modified arguments.
  int arg_size = method->size_of_parameters();
  object_size += DataLayout::compute_size_in_bytes(arg_size+1);
  return object_size;
}

// Compute the size of the MethodData* necessary to store
// profiling information about a given method.  Size is in words
int MethodData::compute_allocation_size_in_words(methodHandle method) {
  int byte_size = compute_allocation_size_in_bytes(method);
  int word_size = align_size_up(byte_size, BytesPerWord) / BytesPerWord;
  return align_object_size(word_size);
}

// Initialize an individual data segment.  Returns the size of
// the segment in bytes.
int MethodData::initialize_data(BytecodeStream* stream,
                                       int data_index) {
#if defined(COMPILER1) && !defined(COMPILER2)
  return 0;
#else
  int cell_count = -1;
  int tag = DataLayout::no_tag;
  DataLayout* data_layout = data_layout_at(data_index);
  Bytecodes::Code c = stream->code();
  switch (c) {
  case Bytecodes::_checkcast:
  case Bytecodes::_instanceof:
  case Bytecodes::_aastore:
    if (TypeProfileCasts) {
      cell_count = ReceiverTypeData::static_cell_count();
      tag = DataLayout::receiver_type_data_tag;
    } else {
      cell_count = BitData::static_cell_count();
      tag = DataLayout::bit_data_tag;
    }
    break;
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokestatic:
    cell_count = CounterData::static_cell_count();
    tag = DataLayout::counter_data_tag;
    break;
  case Bytecodes::_goto:
  case Bytecodes::_goto_w:
  case Bytecodes::_jsr:
  case Bytecodes::_jsr_w:
    cell_count = JumpData::static_cell_count();
    tag = DataLayout::jump_data_tag;
    break;
  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokeinterface:
    cell_count = VirtualCallData::static_cell_count();
    tag = DataLayout::virtual_call_data_tag;
    break;
  case Bytecodes::_invokedynamic:
    // %%% should make a type profile for any invokedynamic that takes a ref argument
    cell_count = CounterData::static_cell_count();
    tag = DataLayout::counter_data_tag;
    break;
  case Bytecodes::_ret:
    cell_count = RetData::static_cell_count();
    tag = DataLayout::ret_data_tag;
    break;
  case Bytecodes::_ifeq:
  case Bytecodes::_ifne:
  case Bytecodes::_iflt:
  case Bytecodes::_ifge:
  case Bytecodes::_ifgt:
  case Bytecodes::_ifle:
  case Bytecodes::_if_icmpeq:
  case Bytecodes::_if_icmpne:
  case Bytecodes::_if_icmplt:
  case Bytecodes::_if_icmpge:
  case Bytecodes::_if_icmpgt:
  case Bytecodes::_if_icmple:
  case Bytecodes::_if_acmpeq:
  case Bytecodes::_if_acmpne:
  case Bytecodes::_ifnull:
  case Bytecodes::_ifnonnull:
    cell_count = BranchData::static_cell_count();
    tag = DataLayout::branch_data_tag;
    break;
  case Bytecodes::_lookupswitch:
  case Bytecodes::_tableswitch:
    cell_count = MultiBranchData::compute_cell_count(stream);
    tag = DataLayout::multi_branch_data_tag;
    break;
  }
  assert(tag == DataLayout::multi_branch_data_tag ||
         cell_count == bytecode_cell_count(c), "cell counts must agree");
  if (cell_count >= 0) {
    assert(tag != DataLayout::no_tag, "bad tag");
    assert(bytecode_has_profile(c), "agree w/ BHP");
    data_layout->initialize(tag, stream->bci(), cell_count);
    return DataLayout::compute_size_in_bytes(cell_count);
  } else {
    assert(!bytecode_has_profile(c), "agree w/ !BHP");
    return 0;
  }
#endif
}

// Get the data at an arbitrary (sort of) data index.
ProfileData* MethodData::data_at(int data_index) const {
  if (out_of_bounds(data_index)) {
    return NULL;
  }
  DataLayout* data_layout = data_layout_at(data_index);
  return data_layout->data_in();
}

ProfileData* DataLayout::data_in() {
  switch (tag()) {
  case DataLayout::no_tag:
  default:
    ShouldNotReachHere();
    return NULL;
  case DataLayout::bit_data_tag:
    return new BitData(this);
  case DataLayout::counter_data_tag:
    return new CounterData(this);
  case DataLayout::jump_data_tag:
    return new JumpData(this);
  case DataLayout::receiver_type_data_tag:
    return new ReceiverTypeData(this);
  case DataLayout::virtual_call_data_tag:
    return new VirtualCallData(this);
  case DataLayout::ret_data_tag:
    return new RetData(this);
  case DataLayout::branch_data_tag:
    return new BranchData(this);
  case DataLayout::multi_branch_data_tag:
    return new MultiBranchData(this);
  case DataLayout::arg_info_data_tag:
    return new ArgInfoData(this);
  };
}

// Iteration over data.
ProfileData* MethodData::next_data(ProfileData* current) const {
  int current_index = dp_to_di(current->dp());
  int next_index = current_index + current->size_in_bytes();
  ProfileData* next = data_at(next_index);
  return next;
}

// Give each of the data entries a chance to perform specific
// data initialization.
void MethodData::post_initialize(BytecodeStream* stream) {
  ResourceMark rm;
  ProfileData* data;
  for (data = first_data(); is_valid(data); data = next_data(data)) {
    stream->set_start(data->bci());
    stream->next();
    data->post_initialize(stream, this);
  }
}

// Initialize the MethodData* corresponding to a given method.
MethodData::MethodData(methodHandle method, int size, TRAPS) {
  No_Safepoint_Verifier no_safepoint;  // init function atomic wrt GC
  ResourceMark rm;
  // Set the method back-pointer.
  _method = method();

  init();
  set_creation_mileage(mileage_of(method()));

  // Go through the bytecodes and allocate and initialize the
  // corresponding data cells.
  int data_size = 0;
  int empty_bc_count = 0;  // number of bytecodes lacking data
  _data[0] = 0;  // apparently not set below.
  BytecodeStream stream(method);
  Bytecodes::Code c;
  while ((c = stream.next()) >= 0) {
    int size_in_bytes = initialize_data(&stream, data_size);
    data_size += size_in_bytes;
    if (size_in_bytes == 0)  empty_bc_count += 1;
  }
  _data_size = data_size;
  int object_size = in_bytes(data_offset()) + data_size;

  // Add some extra DataLayout cells (at least one) to track stray traps.
  int extra_data_count = compute_extra_data_count(data_size, empty_bc_count);
  int extra_size = extra_data_count * DataLayout::compute_size_in_bytes(0);

  // Add a cell to record information about modified arguments.
  // Set up _args_modified array after traps cells so that
  // the code for traps cells works.
  DataLayout *dp = data_layout_at(data_size + extra_size);

  int arg_size = method->size_of_parameters();
  dp->initialize(DataLayout::arg_info_data_tag, 0, arg_size+1);

  object_size += extra_size + DataLayout::compute_size_in_bytes(arg_size+1);

  // Set an initial hint. Don't use set_hint_di() because
  // first_di() may be out of bounds if data_size is 0.
  // In that situation, _hint_di is never used, but at
  // least well-defined.
  _hint_di = first_di();

  post_initialize(&stream);

  set_size(object_size);
}

void MethodData::init() {
  _invocation_counter.init();
  _backedge_counter.init();
  _invocation_counter_start = 0;
  _backedge_counter_start = 0;
  _num_loops = 0;
  _num_blocks = 0;
  _highest_comp_level = 0;
  _highest_osr_comp_level = 0;
  _would_profile = true;

  // Initialize flags and trap history.
  _nof_decompiles = 0;
  _nof_overflow_recompiles = 0;
  _nof_overflow_traps = 0;
  clear_escape_info();
  assert(sizeof(_trap_hist) % sizeof(HeapWord) == 0, "align");
  Copy::zero_to_words((HeapWord*) &_trap_hist,
                      sizeof(_trap_hist) / sizeof(HeapWord));
}

// Get a measure of how much mileage the method has on it.
int MethodData::mileage_of(Method* method) {
  int mileage = 0;
  if (TieredCompilation) {
    mileage = MAX2(method->invocation_count(), method->backedge_count());
  } else {
    int iic = method->interpreter_invocation_count();
    if (mileage < iic)  mileage = iic;
    MethodCounters* mcs = method->method_counters();
    if (mcs != NULL) {
      InvocationCounter* ic = mcs->invocation_counter();
      InvocationCounter* bc = mcs->backedge_counter();
      int icval = ic->count();
      if (ic->carry()) icval += CompileThreshold;
      if (mileage < icval)  mileage = icval;
      int bcval = bc->count();
      if (bc->carry()) bcval += CompileThreshold;
      if (mileage < bcval)  mileage = bcval;
    }
  }
  return mileage;
}

bool MethodData::is_mature() const {
  return CompilationPolicy::policy()->is_mature(_method);
}

// Translate a bci to its corresponding data index (di).
address MethodData::bci_to_dp(int bci) {
  ResourceMark rm;
  ProfileData* data = data_before(bci);
  ProfileData* prev = NULL;
  for ( ; is_valid(data); data = next_data(data)) {
    if (data->bci() >= bci) {
      if (data->bci() == bci)  set_hint_di(dp_to_di(data->dp()));
      else if (prev != NULL)   set_hint_di(dp_to_di(prev->dp()));
      return data->dp();
    }
    prev = data;
  }
  return (address)limit_data_position();
}

// Translate a bci to its corresponding data, or NULL.
ProfileData* MethodData::bci_to_data(int bci) {
  ProfileData* data = data_before(bci);
  for ( ; is_valid(data); data = next_data(data)) {
    if (data->bci() == bci) {
      set_hint_di(dp_to_di(data->dp()));
      return data;
    } else if (data->bci() > bci) {
      break;
    }
  }
  return bci_to_extra_data(bci, false);
}

// Translate a bci to its corresponding extra data, or NULL.
ProfileData* MethodData::bci_to_extra_data(int bci, bool create_if_missing) {
  DataLayout* dp    = extra_data_base();
  DataLayout* end   = extra_data_limit();
  DataLayout* avail = NULL;
  for (; dp < end; dp = next_extra(dp)) {
    // No need for "OrderAccess::load_acquire" ops,
    // since the data structure is monotonic.
    if (dp->tag() == DataLayout::no_tag)  break;
    if (dp->tag() == DataLayout::arg_info_data_tag) {
      dp = end; // ArgInfoData is at the end of extra data section.
      break;
    }
    if (dp->bci() == bci) {
      assert(dp->tag() == DataLayout::bit_data_tag, "sane");
      return new BitData(dp);
    }
  }
  if (create_if_missing && dp < end) {
    // Allocate this one.  There is no mutual exclusion,
    // so two threads could allocate different BCIs to the
    // same data layout.  This means these extra data
    // records, like most other MDO contents, must not be
    // trusted too much.
    DataLayout temp;
    temp.initialize(DataLayout::bit_data_tag, bci, 0);
    dp->release_set_header(temp.header());
    assert(dp->tag() == DataLayout::bit_data_tag, "sane");
    //NO: assert(dp->bci() == bci, "no concurrent allocation");
    return new BitData(dp);
  }
  return NULL;
}

ArgInfoData *MethodData::arg_info() {
  DataLayout* dp    = extra_data_base();
  DataLayout* end   = extra_data_limit();
  for (; dp < end; dp = next_extra(dp)) {
    if (dp->tag() == DataLayout::arg_info_data_tag)
      return new ArgInfoData(dp);
  }
  return NULL;
}

// Printing

#ifndef PRODUCT

void MethodData::print_on(outputStream* st) const {
  assert(is_methodData(), "should be method data");
  st->print("method data for ");
  method()->print_value_on(st);
  st->cr();
  print_data_on(st);
}

#endif //PRODUCT

void MethodData::print_value_on(outputStream* st) const {
  assert(is_methodData(), "should be method data");
  st->print("method data for ");
  method()->print_value_on(st);
}

#ifndef PRODUCT
void MethodData::print_data_on(outputStream* st) const {
  ResourceMark rm;
  ProfileData* data = first_data();
  for ( ; is_valid(data); data = next_data(data)) {
    st->print("%d", dp_to_di(data->dp()));
    st->fill_to(6);
    data->print_data_on(st);
  }
  st->print_cr("--- Extra data:");
  DataLayout* dp    = extra_data_base();
  DataLayout* end   = extra_data_limit();
  for (; dp < end; dp = next_extra(dp)) {
    // No need for "OrderAccess::load_acquire" ops,
    // since the data structure is monotonic.
    if (dp->tag() == DataLayout::no_tag)  continue;
    if (dp->tag() == DataLayout::bit_data_tag) {
      data = new BitData(dp);
    } else {
      assert(dp->tag() == DataLayout::arg_info_data_tag, "must be BitData or ArgInfo");
      data = new ArgInfoData(dp);
      dp = end; // ArgInfoData is at the end of extra data section.
    }
    st->print("%d", dp_to_di(data->dp()));
    st->fill_to(6);
    data->print_data_on(st);
  }
}
#endif

#if INCLUDE_SERVICES
// Size Statistics
void MethodData::collect_statistics(KlassSizeStats *sz) const {
  int n = sz->count(this);
  sz->_method_data_bytes += n;
  sz->_method_all_bytes += n;
  sz->_rw_bytes += n;
}
#endif // INCLUDE_SERVICES

// Verification

void MethodData::verify_on(outputStream* st) {
  guarantee(is_methodData(), "object must be method data");
  // guarantee(m->is_perm(), "should be in permspace");
  this->verify_data_on(st);
}

void MethodData::verify_data_on(outputStream* st) {
  NEEDS_CLEANUP;
  // not yet implemented.
}
