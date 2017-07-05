/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009 Red Hat, Inc.
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
#include "ci/ciMethod.hpp"
#include "code/debugInfoRec.hpp"
#include "shark/llvmValue.hpp"
#include "shark/sharkBuilder.hpp"
#include "shark/sharkCacheDecache.hpp"
#include "shark/sharkFunction.hpp"
#include "shark/sharkState.hpp"

using namespace llvm;

void SharkDecacher::start_frame() {
  // Start recording the debug information
  _pc_offset = code_buffer()->create_unique_offset();
  _oopmap = new OopMap(
    oopmap_slot_munge(stack()->oopmap_frame_size()),
    oopmap_slot_munge(arg_size()));
  debug_info()->add_safepoint(pc_offset(), oopmap());
}

void SharkDecacher::start_stack(int stack_depth) {
  // Create the array we'll record our stack slots in
  _exparray = new GrowableArray<ScopeValue*>(stack_depth);

  // Set the stack pointer
  stack()->CreateStoreStackPointer(
    builder()->CreatePtrToInt(
      stack()->slot_addr(
        stack()->stack_slots_offset() + max_stack() - stack_depth),
      SharkType::intptr_type()));
}

void SharkDecacher::process_stack_slot(int          index,
                                       SharkValue** addr,
                                       int          offset) {
  SharkValue *value = *addr;

  // Write the value to the frame if necessary
  if (stack_slot_needs_write(index, value)) {
    write_value_to_frame(
      SharkType::to_stackType(value->basic_type()),
      value->generic_value(),
      adjusted_offset(value, offset));
  }

  // Record the value in the oopmap if necessary
  if (stack_slot_needs_oopmap(index, value)) {
    oopmap()->set_oop(slot2reg(offset));
  }

  // Record the value in the debuginfo if necessary
  if (stack_slot_needs_debuginfo(index, value)) {
    exparray()->append(slot2lv(offset, stack_location_type(index, addr)));
  }
}

void SharkDecacher::start_monitors(int num_monitors) {
  // Create the array we'll record our monitors in
  _monarray = new GrowableArray<MonitorValue*>(num_monitors);
}

void SharkDecacher::process_monitor(int index, int box_offset, int obj_offset) {
  oopmap()->set_oop(slot2reg(obj_offset));

  monarray()->append(new MonitorValue(
    slot2lv (obj_offset, Location::oop),
    slot2loc(box_offset, Location::normal)));
}

void SharkDecacher::process_oop_tmp_slot(Value** value, int offset) {
  // Decache the temporary oop slot
  if (*value) {
    write_value_to_frame(
      SharkType::oop_type(),
      *value,
      offset);

    oopmap()->set_oop(slot2reg(offset));
  }
}

void SharkDecacher::process_method_slot(Value** value, int offset) {
  // Decache the method pointer
  write_value_to_frame(
    SharkType::Method_type(),
    *value,
    offset);

}

void SharkDecacher::process_pc_slot(int offset) {
  // Record the PC
  builder()->CreateStore(
    builder()->code_buffer_address(pc_offset()),
    stack()->slot_addr(offset));
}

void SharkDecacher::start_locals() {
  // Create the array we'll record our local variables in
  _locarray = new GrowableArray<ScopeValue*>(max_locals());}

void SharkDecacher::process_local_slot(int          index,
                                       SharkValue** addr,
                                       int          offset) {
  SharkValue *value = *addr;

  // Write the value to the frame if necessary
  if (local_slot_needs_write(index, value)) {
    write_value_to_frame(
      SharkType::to_stackType(value->basic_type()),
      value->generic_value(),
      adjusted_offset(value, offset));
  }

  // Record the value in the oopmap if necessary
  if (local_slot_needs_oopmap(index, value)) {
    oopmap()->set_oop(slot2reg(offset));
  }

  // Record the value in the debuginfo if necessary
  if (local_slot_needs_debuginfo(index, value)) {
    locarray()->append(slot2lv(offset, local_location_type(index, addr)));
  }
}

void SharkDecacher::end_frame() {
  // Record the scope
  methodHandle null_mh;
  debug_info()->describe_scope(
    pc_offset(),
    null_mh,
    target(),
    bci(),
    true,
    false,
    false,
    debug_info()->create_scope_values(locarray()),
    debug_info()->create_scope_values(exparray()),
    debug_info()->create_monitor_values(monarray()));

  // Finish recording the debug information
  debug_info()->end_safepoint(pc_offset());
}

void SharkCacher::process_stack_slot(int          index,
                                     SharkValue** addr,
                                     int          offset) {
  SharkValue *value = *addr;

  // Read the value from the frame if necessary
  if (stack_slot_needs_read(index, value)) {
    *addr = SharkValue::create_generic(
      value->type(),
      read_value_from_frame(
        SharkType::to_stackType(value->basic_type()),
        adjusted_offset(value, offset)),
      value->zero_checked());
  }
}

void SharkOSREntryCacher::process_monitor(int index,
                                          int box_offset,
                                          int obj_offset) {
  // Copy the monitor from the OSR buffer to the frame
  int src_offset = max_locals() + index * 2;
  builder()->CreateStore(
    builder()->CreateLoad(
      CreateAddressOfOSRBufEntry(src_offset, SharkType::intptr_type())),
    stack()->slot_addr(box_offset, SharkType::intptr_type()));
  builder()->CreateStore(
    builder()->CreateLoad(
      CreateAddressOfOSRBufEntry(src_offset + 1, SharkType::oop_type())),
    stack()->slot_addr(obj_offset, SharkType::oop_type()));
}

void SharkCacher::process_oop_tmp_slot(Value** value, int offset) {
  // Cache the temporary oop
  if (*value)
    *value = read_value_from_frame(SharkType::oop_type(), offset);
}

void SharkCacher::process_method_slot(Value** value, int offset) {
  // Cache the method pointer
  *value = read_value_from_frame(SharkType::Method_type(), offset);
}

void SharkFunctionEntryCacher::process_method_slot(Value** value, int offset) {
  // "Cache" the method pointer
  *value = method();
}

void SharkCacher::process_local_slot(int          index,
                                     SharkValue** addr,
                                     int          offset) {
  SharkValue *value = *addr;

  // Read the value from the frame if necessary
  if (local_slot_needs_read(index, value)) {
    *addr = SharkValue::create_generic(
      value->type(),
      read_value_from_frame(
        SharkType::to_stackType(value->basic_type()),
        adjusted_offset(value, offset)),
      value->zero_checked());
  }
}

Value* SharkOSREntryCacher::CreateAddressOfOSRBufEntry(int         offset,
                                                       Type* type) {
  Value *result = builder()->CreateStructGEP(osr_buf(), offset);
  if (type != SharkType::intptr_type())
    result = builder()->CreateBitCast(result, PointerType::getUnqual(type));
  return result;
}

void SharkOSREntryCacher::process_local_slot(int          index,
                                             SharkValue** addr,
                                             int          offset) {
  SharkValue *value = *addr;

  // Read the value from the OSR buffer if necessary
  if (local_slot_needs_read(index, value)) {
    *addr = SharkValue::create_generic(
      value->type(),
      builder()->CreateLoad(
        CreateAddressOfOSRBufEntry(
          adjusted_offset(value, max_locals() - 1 - index),
          SharkType::to_stackType(value->basic_type()))),
      value->zero_checked());
  }
}

void SharkDecacher::write_value_to_frame(Type* type,
                                         Value*      value,
                                         int         offset) {
  builder()->CreateStore(value, stack()->slot_addr(offset, type));
}

Value* SharkCacher::read_value_from_frame(Type* type, int offset) {
  return builder()->CreateLoad(stack()->slot_addr(offset, type));
}
