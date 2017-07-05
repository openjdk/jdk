/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_ciMethodData.cpp.incl"

// ciMethodData

// ------------------------------------------------------------------
// ciMethodData::ciMethodData
//
ciMethodData::ciMethodData(methodDataHandle h_md) : ciObject(h_md) {
  assert(h_md() != NULL, "no null method data");
  Copy::zero_to_words((HeapWord*) &_orig, sizeof(_orig) / sizeof(HeapWord));
  _data = NULL;
  _data_size = 0;
  _extra_data_size = 0;
  _current_mileage = 0;
  _invocation_counter = 0;
  _backedge_counter = 0;
  _state = empty_state;
  _saw_free_extra_data = false;
  // Set an initial hint. Don't use set_hint_di() because
  // first_di() may be out of bounds if data_size is 0.
  _hint_di = first_di();
  // Initialize the escape information (to "don't know.");
  _eflags = _arg_local = _arg_stack = _arg_returned = 0;
}

// ------------------------------------------------------------------
// ciMethodData::ciMethodData
//
// No methodDataOop.
ciMethodData::ciMethodData() : ciObject() {
  Copy::zero_to_words((HeapWord*) &_orig, sizeof(_orig) / sizeof(HeapWord));
  _data = NULL;
  _data_size = 0;
  _extra_data_size = 0;
  _current_mileage = 0;
  _invocation_counter = 0;
  _backedge_counter = 0;
  _state = empty_state;
  _saw_free_extra_data = false;
  // Set an initial hint. Don't use set_hint_di() because
  // first_di() may be out of bounds if data_size is 0.
  _hint_di = first_di();
  // Initialize the escape information (to "don't know.");
  _eflags = _arg_local = _arg_stack = _arg_returned = 0;
}

void ciMethodData::load_data() {
  methodDataOop mdo = get_methodDataOop();
  if (mdo == NULL) return;

  // To do: don't copy the data if it is not "ripe" -- require a minimum #
  // of invocations.

  // Snapshot the data -- actually, take an approximate snapshot of
  // the data.  Any concurrently executing threads may be changing the
  // data as we copy it.
  int skip_header = oopDesc::header_size();
  Copy::disjoint_words((HeapWord*) mdo              + skip_header,
                       (HeapWord*) &_orig           + skip_header,
                       sizeof(_orig) / HeapWordSize - skip_header);
  DEBUG_ONLY(*_orig.adr_method() = NULL);  // no dangling oops, please
  Arena* arena = CURRENT_ENV->arena();
  _data_size = mdo->data_size();
  _extra_data_size = mdo->extra_data_size();
  int total_size = _data_size + _extra_data_size;
  _data = (intptr_t *) arena->Amalloc(total_size);
  Copy::disjoint_words((HeapWord*) mdo->data_base(), (HeapWord*) _data, total_size / HeapWordSize);

  // Traverse the profile data, translating any oops into their
  // ci equivalents.
  ResourceMark rm;
  ciProfileData* ci_data = first_data();
  ProfileData* data = mdo->first_data();
  while (is_valid(ci_data)) {
    ci_data->translate_from(data);
    ci_data = next_data(ci_data);
    data = mdo->next_data(data);
  }
  // Note:  Extra data are all BitData, and do not need translation.
  _current_mileage = methodDataOopDesc::mileage_of(mdo->method());
  _invocation_counter = mdo->invocation_count();
  _backedge_counter = mdo->backedge_count();
  _state = mdo->is_mature()? mature_state: immature_state;

  _eflags = mdo->eflags();
  _arg_local = mdo->arg_local();
  _arg_stack = mdo->arg_stack();
  _arg_returned  = mdo->arg_returned();
}

void ciReceiverTypeData::translate_receiver_data_from(ProfileData* data) {
  for (uint row = 0; row < row_limit(); row++) {
    klassOop k = data->as_ReceiverTypeData()->receiver(row);
    if (k != NULL) {
      ciKlass* klass = CURRENT_ENV->get_object(k)->as_klass();
      set_receiver(row, klass);
    }
  }
}


// Get the data at an arbitrary (sort of) data index.
ciProfileData* ciMethodData::data_at(int data_index) {
  if (out_of_bounds(data_index)) {
    return NULL;
  }
  DataLayout* data_layout = data_layout_at(data_index);

  switch (data_layout->tag()) {
  case DataLayout::no_tag:
  default:
    ShouldNotReachHere();
    return NULL;
  case DataLayout::bit_data_tag:
    return new ciBitData(data_layout);
  case DataLayout::counter_data_tag:
    return new ciCounterData(data_layout);
  case DataLayout::jump_data_tag:
    return new ciJumpData(data_layout);
  case DataLayout::receiver_type_data_tag:
    return new ciReceiverTypeData(data_layout);
  case DataLayout::virtual_call_data_tag:
    return new ciVirtualCallData(data_layout);
  case DataLayout::ret_data_tag:
    return new ciRetData(data_layout);
  case DataLayout::branch_data_tag:
    return new ciBranchData(data_layout);
  case DataLayout::multi_branch_data_tag:
    return new ciMultiBranchData(data_layout);
  case DataLayout::arg_info_data_tag:
    return new ciArgInfoData(data_layout);
  };
}

// Iteration over data.
ciProfileData* ciMethodData::next_data(ciProfileData* current) {
  int current_index = dp_to_di(current->dp());
  int next_index = current_index + current->size_in_bytes();
  ciProfileData* next = data_at(next_index);
  return next;
}

// Translate a bci to its corresponding data, or NULL.
ciProfileData* ciMethodData::bci_to_data(int bci) {
  ciProfileData* data = data_before(bci);
  for ( ; is_valid(data); data = next_data(data)) {
    if (data->bci() == bci) {
      set_hint_di(dp_to_di(data->dp()));
      return data;
    } else if (data->bci() > bci) {
      break;
    }
  }
  // bci_to_extra_data(bci) ...
  DataLayout* dp  = data_layout_at(data_size());
  DataLayout* end = data_layout_at(data_size() + extra_data_size());
  for (; dp < end; dp = methodDataOopDesc::next_extra(dp)) {
    if (dp->tag() == DataLayout::no_tag) {
      _saw_free_extra_data = true;  // observed an empty slot (common case)
      return NULL;
    }
    if (dp->tag() == DataLayout::arg_info_data_tag) {
      break; // ArgInfoData is at the end of extra data section.
    }
    if (dp->bci() == bci) {
      assert(dp->tag() == DataLayout::bit_data_tag, "sane");
      return new ciBitData(dp);
    }
  }
  return NULL;
}

// Conservatively decode the trap_state of a ciProfileData.
int ciMethodData::has_trap_at(ciProfileData* data, int reason) {
  typedef Deoptimization::DeoptReason DR_t;
  int per_bc_reason
    = Deoptimization::reason_recorded_per_bytecode_if_any((DR_t) reason);
  if (trap_count(reason) == 0) {
    // Impossible for this trap to have occurred, regardless of trap_state.
    // Note:  This happens if the MDO is empty.
    return 0;
  } else if (per_bc_reason == Deoptimization::Reason_none) {
    // We cannot conclude anything; a trap happened somewhere, maybe here.
    return -1;
  } else if (data == NULL) {
    // No profile here, not even an extra_data record allocated on the fly.
    // If there are empty extra_data records, and there had been a trap,
    // there would have been a non-null data pointer.  If there are no
    // free extra_data records, we must return a conservative -1.
    if (_saw_free_extra_data)
      return 0;                 // Q.E.D.
    else
      return -1;                // bail with a conservative answer
  } else {
    return Deoptimization::trap_state_has_reason(data->trap_state(), per_bc_reason);
  }
}

int ciMethodData::trap_recompiled_at(ciProfileData* data) {
  if (data == NULL) {
    return (_saw_free_extra_data? 0: -1);  // (see previous method)
  } else {
    return Deoptimization::trap_state_is_recompiled(data->trap_state())? 1: 0;
  }
}

void ciMethodData::clear_escape_info() {
  VM_ENTRY_MARK;
  methodDataOop mdo = get_methodDataOop();
  if (mdo != NULL) {
    mdo->clear_escape_info();
    ArgInfoData *aid = arg_info();
    int arg_count = (aid == NULL) ? 0 : aid->number_of_args();
    for (int i = 0; i < arg_count; i++) {
      set_arg_modified(i, 0);
    }
  }
  _eflags = _arg_local = _arg_stack = _arg_returned = 0;
}

// copy our escape info to the methodDataOop if it exists
void ciMethodData::update_escape_info() {
  VM_ENTRY_MARK;
  methodDataOop mdo = get_methodDataOop();
  if ( mdo != NULL) {
    mdo->set_eflags(_eflags);
    mdo->set_arg_local(_arg_local);
    mdo->set_arg_stack(_arg_stack);
    mdo->set_arg_returned(_arg_returned);
    int arg_count = mdo->method()->size_of_parameters();
    for (int i = 0; i < arg_count; i++) {
      mdo->set_arg_modified(i, arg_modified(i));
    }
  }
}

void ciMethodData::set_compilation_stats(short loops, short blocks) {
  VM_ENTRY_MARK;
  methodDataOop mdo = get_methodDataOop();
  if (mdo != NULL) {
    mdo->set_num_loops(loops);
    mdo->set_num_blocks(blocks);
  }
}

void ciMethodData::set_would_profile(bool p) {
  VM_ENTRY_MARK;
  methodDataOop mdo = get_methodDataOop();
  if (mdo != NULL) {
    mdo->set_would_profile(p);
  }
}

bool ciMethodData::has_escape_info() {
  return eflag_set(methodDataOopDesc::estimated);
}

void ciMethodData::set_eflag(methodDataOopDesc::EscapeFlag f) {
  set_bits(_eflags, f);
}

void ciMethodData::clear_eflag(methodDataOopDesc::EscapeFlag f) {
  clear_bits(_eflags, f);
}

bool ciMethodData::eflag_set(methodDataOopDesc::EscapeFlag f) const {
  return mask_bits(_eflags, f) != 0;
}

void ciMethodData::set_arg_local(int i) {
  set_nth_bit(_arg_local, i);
}

void ciMethodData::set_arg_stack(int i) {
  set_nth_bit(_arg_stack, i);
}

void ciMethodData::set_arg_returned(int i) {
  set_nth_bit(_arg_returned, i);
}

void ciMethodData::set_arg_modified(int arg, uint val) {
  ArgInfoData *aid = arg_info();
  if (aid == NULL)
    return;
  assert(arg >= 0 && arg < aid->number_of_args(), "valid argument number");
  aid->set_arg_modified(arg, val);
}

bool ciMethodData::is_arg_local(int i) const {
  return is_set_nth_bit(_arg_local, i);
}

bool ciMethodData::is_arg_stack(int i) const {
  return is_set_nth_bit(_arg_stack, i);
}

bool ciMethodData::is_arg_returned(int i) const {
  return is_set_nth_bit(_arg_returned, i);
}

uint ciMethodData::arg_modified(int arg) const {
  ArgInfoData *aid = arg_info();
  if (aid == NULL)
    return 0;
  assert(arg >= 0 && arg < aid->number_of_args(), "valid argument number");
  return aid->arg_modified(arg);
}

ByteSize ciMethodData::offset_of_slot(ciProfileData* data, ByteSize slot_offset_in_data) {
  // Get offset within methodDataOop of the data array
  ByteSize data_offset = methodDataOopDesc::data_offset();

  // Get cell offset of the ProfileData within data array
  int cell_offset = dp_to_di(data->dp());

  // Add in counter_offset, the # of bytes into the ProfileData of counter or flag
  int offset = in_bytes(data_offset) + cell_offset + in_bytes(slot_offset_in_data);

  return in_ByteSize(offset);
}

ciArgInfoData *ciMethodData::arg_info() const {
  // Should be last, have to skip all traps.
  DataLayout* dp  = data_layout_at(data_size());
  DataLayout* end = data_layout_at(data_size() + extra_data_size());
  for (; dp < end; dp = methodDataOopDesc::next_extra(dp)) {
    if (dp->tag() == DataLayout::arg_info_data_tag)
      return new ciArgInfoData(dp);
  }
  return NULL;
}


// Implementation of the print method.
void ciMethodData::print_impl(outputStream* st) {
  ciObject::print_impl(st);
}

#ifndef PRODUCT
void ciMethodData::print() {
  print_data_on(tty);
}

void ciMethodData::print_data_on(outputStream* st) {
  ResourceMark rm;
  ciProfileData* data;
  for (data = first_data(); is_valid(data); data = next_data(data)) {
    st->print("%d", dp_to_di(data->dp()));
    st->fill_to(6);
    data->print_data_on(st);
  }
  st->print_cr("--- Extra data:");
  DataLayout* dp  = data_layout_at(data_size());
  DataLayout* end = data_layout_at(data_size() + extra_data_size());
  for (; dp < end; dp = methodDataOopDesc::next_extra(dp)) {
    if (dp->tag() == DataLayout::no_tag)  continue;
    if (dp->tag() == DataLayout::bit_data_tag) {
      data = new BitData(dp);
    } else {
      assert(dp->tag() == DataLayout::arg_info_data_tag, "must be BitData or ArgInfo");
      data = new ciArgInfoData(dp);
      dp = end; // ArgInfoData is at the end of extra data section.
    }
    st->print("%d", dp_to_di(data->dp()));
    st->fill_to(6);
    data->print_data_on(st);
  }
}

void ciReceiverTypeData::print_receiver_data_on(outputStream* st) {
  uint row;
  int entries = 0;
  for (row = 0; row < row_limit(); row++) {
    if (receiver(row) != NULL)  entries++;
  }
  st->print_cr("count(%u) entries(%u)", count(), entries);
  for (row = 0; row < row_limit(); row++) {
    if (receiver(row) != NULL) {
      tab(st);
      receiver(row)->print_name_on(st);
      st->print_cr("(%u)", receiver_count(row));
    }
  }
}

void ciReceiverTypeData::print_data_on(outputStream* st) {
  print_shared(st, "ciReceiverTypeData");
  print_receiver_data_on(st);
}

void ciVirtualCallData::print_data_on(outputStream* st) {
  print_shared(st, "ciVirtualCallData");
  rtd_super()->print_receiver_data_on(st);
}
#endif
