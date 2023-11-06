/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_CONTINUATIONHELPER_INLINE_HPP
#define SHARE_VM_RUNTIME_CONTINUATIONHELPER_INLINE_HPP

#include "runtime/continuationHelper.hpp"

#include "code/scopeDesc.hpp"
#include "compiler/oopMap.hpp"
#include "compiler/oopMap.inline.hpp"
#include "interpreter/oopMapCache.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/stackValue.hpp"
#include "utilities/macros.hpp"

#include CPU_HEADER_INLINE(continuationHelper)

#ifndef CPU_OVERRIDES_RETURN_ADDRESS_ACCESSORS
inline address ContinuationHelper::return_address_at(intptr_t* sp) {
  return *(address*)sp;
}

inline void ContinuationHelper::patch_return_address_at(intptr_t* sp,
                                                        address pc) {
  *(address*)sp = pc;
}
#endif // !CPU_OVERRIDES_RETURN_ADDRESS_ACCESSORS

inline bool ContinuationHelper::NonInterpretedUnknownFrame::is_instance(const frame& f) {
  return !f.is_interpreted_frame();
}

inline bool ContinuationHelper::Frame::is_stub(CodeBlob* cb) {
  return cb != nullptr && (cb->is_safepoint_stub() || cb->is_runtime_stub());
}

inline Method* ContinuationHelper::Frame::frame_method(const frame& f) {
  return f.is_interpreted_frame() ? f.interpreter_frame_method() : f.cb()->as_compiled_method()->method();
}

inline address ContinuationHelper::Frame::return_pc(const frame& f) {
  return return_address_at((intptr_t *)return_pc_address(f));
}

#ifdef ASSERT
inline intptr_t* ContinuationHelper::Frame::frame_top(const frame &f) {
  if (f.is_interpreted_frame()) {
    ResourceMark rm;
    InterpreterOopMap mask;
    f.interpreted_frame_oop_map(&mask);
    return InterpretedFrame::frame_top(f, &mask);
  } else {
    return CompiledFrame::frame_top(f);
  }
}

inline bool ContinuationHelper::Frame::is_deopt_return(address pc, const frame& sender) {
  if (sender.is_interpreted_frame()) return false;

  CompiledMethod* cm = sender.cb()->as_compiled_method();
  return cm->is_deopt_pc(pc);
}

#endif

inline bool ContinuationHelper::InterpretedFrame::is_instance(const frame& f) {
  return f.is_interpreted_frame();
}

inline address ContinuationHelper::InterpretedFrame::return_pc(const frame& f) {
  return return_address_at((intptr_t *)return_pc_address(f));
}

inline int ContinuationHelper::InterpretedFrame::size(const frame&f) {
  return pointer_delta_as_int(InterpretedFrame::frame_bottom(f), InterpretedFrame::frame_top(f));
}

inline int ContinuationHelper::InterpretedFrame::stack_argsize(const frame& f) {
  return f.interpreter_frame_method()->size_of_parameters();
}

inline int ContinuationHelper::InterpretedFrame::expression_stack_size(const frame &f, InterpreterOopMap* mask) {
  int size = mask->expression_stack_size();
  assert(size <= f.interpreter_frame_expression_stack_size(), "size1: %d size2: %d", size, f.interpreter_frame_expression_stack_size());
  return size;
}

#ifdef ASSERT
inline bool ContinuationHelper::InterpretedFrame::is_owning_locks(const frame& f) {
  assert(f.interpreter_frame_monitor_end() <= f.interpreter_frame_monitor_begin(), "must be");
  if (f.interpreter_frame_monitor_end() == f.interpreter_frame_monitor_begin()) {
    return false;
  }

  for (BasicObjectLock* current = f.previous_monitor_in_interpreter_frame(f.interpreter_frame_monitor_begin());
        current >= f.interpreter_frame_monitor_end();
        current = f.previous_monitor_in_interpreter_frame(current)) {

      oop obj = current->obj();
      if (obj != nullptr) {
        return true;
      }
  }
  return false;
}
#endif

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f) { // inclusive; this will be copied with the frame
  return f.unextended_sp();
}

inline intptr_t* ContinuationHelper::NonInterpretedFrame::frame_top(const frame& f, int callee_argsize, bool callee_interpreted) {
  return f.unextended_sp() + (callee_interpreted ? 0 : callee_argsize);
}

inline intptr_t* ContinuationHelper::NonInterpretedFrame::frame_top(const frame& f) { // inclusive; this will be copied with the frame
  return f.unextended_sp();
}

inline intptr_t* ContinuationHelper::NonInterpretedFrame::frame_bottom(const frame& f) { // exclusive; this will not be copied with the frame
  return f.unextended_sp() + f.cb()->frame_size();
}

inline int ContinuationHelper::NonInterpretedFrame::size(const frame& f) {
  assert(!f.is_interpreted_frame(), "");
  return f.cb()->frame_size();
}

inline int ContinuationHelper::NonInterpretedFrame::stack_argsize(const frame& f) {
  return f.compiled_frame_stack_argsize();
}

inline bool ContinuationHelper::CompiledFrame::is_instance(const frame& f) {
  return f.is_compiled_frame();
}

#ifdef ASSERT
template<typename RegisterMapT>
bool ContinuationHelper::CompiledFrame::is_owning_locks(JavaThread* thread, RegisterMapT* map, const frame& f) {
  assert(!f.is_interpreted_frame(), "");
  assert(CompiledFrame::is_instance(f), "");

  CompiledMethod* cm = f.cb()->as_compiled_method();
  assert(!cm->is_compiled() || !cm->as_compiled_method()->is_native_method(), ""); // See compiledVFrame::compiledVFrame(...) in vframe_hp.cpp

  if (!cm->has_monitors()) {
    return false;
  }

  frame::update_map_with_saved_link(map, Frame::callee_link_address(f)); // the monitor object could be stored in the link register
  ResourceMark rm;
  for (ScopeDesc* scope = cm->scope_desc_at(f.pc()); scope != nullptr; scope = scope->sender()) {
    GrowableArray<MonitorValue*>* mons = scope->monitors();
    if (mons == nullptr || mons->is_empty()) {
      continue;
    }

    for (int index = (mons->length()-1); index >= 0; index--) { // see compiledVFrame::monitors()
      MonitorValue* mon = mons->at(index);
      if (mon->eliminated()) {
        continue; // we ignore scalar-replaced monitors
      }
      ScopeValue* ov = mon->owner();
      StackValue* owner_sv = StackValue::create_stack_value(&f, map, ov); // it is an oop
      oop owner = owner_sv->get_obj()();
      if (owner != nullptr) {
        //assert(cm->has_monitors(), "");
        return true;
      }
    }
  }
  return false;
}
#endif

inline bool ContinuationHelper::StubFrame::is_instance(const frame& f) {
  return !f.is_interpreted_frame() && is_stub(f.cb());
}

#endif // SHARE_VM_RUNTIME_CONTINUATIONHELPER_INLINE_HPP
