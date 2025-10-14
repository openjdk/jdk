/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/stackMapTable.hpp"
#include "classfile/verifier.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"

StackMapTable::StackMapTable(StackMapReader* reader, TRAPS) {
  _code_length = reader->code_length();
  _frame_count = reader->get_frame_count();
  if (_frame_count > 0) {
    _frame_array = new GrowableArray<StackMapFrame*>(_frame_count);
    while (!reader->at_end()) {
      StackMapFrame* frame = reader->next(CHECK_VERIFY(reader->prev_frame()->verifier()));
      if (frame != nullptr) {
        _frame_array->push(frame);
      }
    }
    reader->check_end(CHECK);
    // Correct frame count based on how many actual frames are generated
    _frame_count = _frame_array->length();
  }
}

void StackMapReader::check_offset(StackMapFrame* frame) {
  int offset = frame->offset();
  if (offset >= _code_length || _code_data[offset] == 0) {
    _verifier->verify_error(ErrorContext::bad_stackmap(0, frame),
                            "StackMapTable error: bad offset");
  }
}

void StackMapReader::check_size(TRAPS) {
  if (_frame_count < _parsed_frame_count) {
    StackMapStream::stackmap_format_error("wrong attribute size", THREAD);
  }
}

void StackMapReader::check_end(TRAPS) {
  assert(_stream->at_end(), "must be");
  if (_frame_count != _parsed_frame_count) {
    StackMapStream::stackmap_format_error("wrong attribute size", THREAD);
  }
}

// This method is only called by method in StackMapTable.
int StackMapTable::get_index_from_offset(int32_t offset) const {
  int i = 0;
  for (; i < _frame_count; i++) {
    if (_frame_array->at(i)->offset() == offset) {
      return i;
    }
  }
  return i;  // frame with offset doesn't exist in the array
}

bool StackMapTable::match_stackmap(
    StackMapFrame* frame, int32_t target,
    bool match, bool update, ErrorContext* ctx, TRAPS) const {
  int index = get_index_from_offset(target);
  return match_stackmap(frame, target, index, match, update, ctx, THREAD);
}

// Match and/or update current_frame to the frame in stackmap table with
// specified offset and frame index. Return true if the two frames match.
//
// The values of match and update are:                  _match__update
//
// checking a branch target:                             true   false
// checking an exception handler:                        true   false
// linear bytecode verification following an
// unconditional branch:                                 false  true
// linear bytecode verification not following an
// unconditional branch:                                 true   true
bool StackMapTable::match_stackmap(
    StackMapFrame* frame, int32_t target, int32_t frame_index,
    bool match, bool update, ErrorContext* ctx, TRAPS) const {
  if (frame_index < 0 || frame_index >= _frame_count) {
    *ctx = ErrorContext::missing_stackmap(frame->offset());
    frame->verifier()->verify_error(
        *ctx, "Expecting a stackmap frame at branch target %d", target);
    return false;
  }

  StackMapFrame* stackmap_frame = _frame_array->at(frame_index);
  bool result = true;
  if (match) {
    // Has direct control flow from last instruction, need to match the two
    // frames.
    result = frame->is_assignable_to(stackmap_frame,
        ctx, CHECK_VERIFY_(frame->verifier(), result));
  }
  if (update) {
    // Use the frame in stackmap table as current frame
    int lsize = stackmap_frame->locals_size();
    int ssize = stackmap_frame->stack_size();
    if (frame->locals_size() > lsize || frame->stack_size() > ssize) {
      // Make sure unused type array items are all _bogus_type.
      frame->reset();
    }
    frame->set_locals_size(lsize);
    frame->copy_locals(stackmap_frame);
    frame->set_stack_size(ssize);
    frame->copy_stack(stackmap_frame);
    frame->set_flags(stackmap_frame->flags());
  }
  return result;
}

void StackMapTable::check_jump_target(
    StackMapFrame* frame, int32_t target, TRAPS) const {
  ErrorContext ctx;
  bool match = match_stackmap(
    frame, target, true, false, &ctx, CHECK_VERIFY(frame->verifier()));
  if (!match || (target < 0 || target >= _code_length)) {
    frame->verifier()->verify_error(ctx,
        "Inconsistent stackmap frames at branch target %d", target);
  }
}

void StackMapTable::print_on(outputStream* str) const {
  str->print_cr("StackMapTable: frame_count = %d", _frame_count);
  str->print_cr("table = {");
  {
    StreamIndentor si(str, 2);
    for (int32_t i = 0; i < _frame_count; ++i) {
      _frame_array->at(i)->print_on(str);
    }
  }
  str->print_cr(" }");
}

StackMapReader::StackMapReader(ClassVerifier* v, StackMapStream* stream,
                               char* code_data, int32_t code_len,
                               StackMapFrame* init_frame,
                               u2 max_locals, u2 max_stack, TRAPS) :
                                  _verifier(v), _stream(stream), _code_data(code_data),
                                  _code_length(code_len), _parsed_frame_count(0),
                                  _prev_frame(init_frame), _max_locals(max_locals),
                                  _max_stack(max_stack), _first(true) {
  methodHandle m = v->method();
  if (m->has_stackmap_table()) {
    _cp = constantPoolHandle(THREAD, m->constants());
    _frame_count = _stream->get_u2(CHECK);
  } else {
    // There's no stackmap table present. Frame count and size are 0.
    _frame_count = 0;
  }
}

int32_t StackMapReader::chop(
    VerificationType* locals, int32_t length, int32_t chops) {
  if (locals == nullptr) return -1;
  int32_t pos = length - 1;
  for (int32_t i=0; i<chops; i++) {
    if (locals[pos].is_category2_2nd()) {
      pos -= 2;
    } else {
      pos --;
    }
    if (pos<0 && i<(chops-1)) return -1;
  }
  return pos+1;
}

#define CHECK_NT CHECK_(VerificationType::bogus_type())

VerificationType StackMapReader::parse_verification_type(u1* flags, TRAPS) {
  u1 tag = _stream->get_u1(CHECK_NT);
  if (tag < (u1)ITEM_UninitializedThis) {
    return VerificationType::from_tag(tag);
  }
  if (tag == ITEM_Object) {
    u2 class_index = _stream->get_u2(CHECK_NT);
    int nconstants = _cp->length();
    if ((class_index <= 0 || class_index >= nconstants) ||
        (!_cp->tag_at(class_index).is_klass() &&
         !_cp->tag_at(class_index).is_unresolved_klass())) {
      _stream->stackmap_format_error("bad class index", THREAD);
      return VerificationType::bogus_type();
    }
    return VerificationType::reference_type(_cp->klass_name_at(class_index));
  }
  if (tag == ITEM_UninitializedThis) {
    if (flags != nullptr) {
      *flags |= FLAG_THIS_UNINIT;
    }
    return VerificationType::uninitialized_this_type();
  }
  if (tag == ITEM_Uninitialized) {
    u2 offset = _stream->get_u2(CHECK_NT);
    if (offset >= _code_length ||
        _code_data[offset] != ClassVerifier::NEW_OFFSET) {
      _verifier->class_format_error(
        "StackMapTable format error: bad offset for Uninitialized");
      return VerificationType::bogus_type();
    }
    return VerificationType::uninitialized_type(offset);
  }
  _stream->stackmap_format_error("bad verification type", THREAD);
  return VerificationType::bogus_type();
}

StackMapFrame* StackMapReader::next(TRAPS) {
  _parsed_frame_count++;
  check_size(CHECK_NULL);
  StackMapFrame* frame = next_helper(CHECK_VERIFY_(_verifier, nullptr));
  if (frame != nullptr) {
    check_offset(frame);
    if (frame->verifier()->has_error()) {
      return nullptr;
    }
    _prev_frame = frame;
  }
  return frame;
}

StackMapFrame* StackMapReader::next_helper(TRAPS) {
  StackMapFrame* frame;
  int offset;
  VerificationType* locals = nullptr;
  u1 frame_type = _stream->get_u1(CHECK_NULL);
  if (frame_type <= SAME_FRAME_END) {
    // same_frame
    if (_first) {
      offset = frame_type;
      // Can't share the locals array since that is updated by the verifier.
      if (_prev_frame->locals_size() > 0) {
        locals = NEW_RESOURCE_ARRAY_IN_THREAD(
          THREAD, VerificationType, _prev_frame->locals_size());
      }
    } else {
      offset = _prev_frame->offset() + frame_type + 1;
      locals = _prev_frame->locals();
    }
    frame = new StackMapFrame(
      offset, _prev_frame->flags(), _prev_frame->locals_size(), 0,
      _max_locals, _max_stack, locals, nullptr, _verifier);
    if (_first && locals != nullptr) {
      frame->copy_locals(_prev_frame);
    }
    _first = false;
    return frame;
  }
  if (frame_type <= SAME_LOCALS_1_STACK_ITEM_FRAME_END) {
    // same_locals_1_stack_item_frame
    if (_first) {
      offset = frame_type - SAME_LOCALS_1_STACK_ITEM_FRAME_START;
      // Can't share the locals array since that is updated by the verifier.
      if (_prev_frame->locals_size() > 0) {
        locals = NEW_RESOURCE_ARRAY_IN_THREAD(
          THREAD, VerificationType, _prev_frame->locals_size());
      }
    } else {
      offset = _prev_frame->offset() + frame_type - (SAME_LOCALS_1_STACK_ITEM_FRAME_START - 1);
      locals = _prev_frame->locals();
    }
    VerificationType* stack = NEW_RESOURCE_ARRAY_IN_THREAD(
      THREAD, VerificationType, 2);
    u2 stack_size = 1;
    stack[0] = parse_verification_type(nullptr, CHECK_VERIFY_(_verifier, nullptr));
    if (stack[0].is_category2()) {
      stack[1] = stack[0].to_category2_2nd();
      stack_size = 2;
    }
    check_verification_type_array_size(
      stack_size, _max_stack, CHECK_VERIFY_(_verifier, nullptr));
    frame = new StackMapFrame(
      offset, _prev_frame->flags(), _prev_frame->locals_size(), stack_size,
      _max_locals, _max_stack, locals, stack, _verifier);
    if (_first && locals != nullptr) {
      frame->copy_locals(_prev_frame);
    }
    _first = false;
    return frame;
  }

  u2 offset_delta = _stream->get_u2(CHECK_NULL);

  if (frame_type < SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
    // reserved frame types
    _stream->stackmap_format_error(
      "reserved frame type", CHECK_VERIFY_(_verifier, nullptr));
  }

  if (frame_type == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
    // same_locals_1_stack_item_frame_extended
    if (_first) {
      offset = offset_delta;
      // Can't share the locals array since that is updated by the verifier.
      if (_prev_frame->locals_size() > 0) {
        locals = NEW_RESOURCE_ARRAY_IN_THREAD(
          THREAD, VerificationType, _prev_frame->locals_size());
      }
    } else {
      offset = _prev_frame->offset() + offset_delta + 1;
      locals = _prev_frame->locals();
    }
    VerificationType* stack = NEW_RESOURCE_ARRAY_IN_THREAD(
      THREAD, VerificationType, 2);
    u2 stack_size = 1;
    stack[0] = parse_verification_type(nullptr, CHECK_VERIFY_(_verifier, nullptr));
    if (stack[0].is_category2()) {
      stack[1] = stack[0].to_category2_2nd();
      stack_size = 2;
    }
    check_verification_type_array_size(
      stack_size, _max_stack, CHECK_VERIFY_(_verifier, nullptr));
    frame = new StackMapFrame(
      offset, _prev_frame->flags(), _prev_frame->locals_size(), stack_size,
      _max_locals, _max_stack, locals, stack, _verifier);
    if (_first && locals != nullptr) {
      frame->copy_locals(_prev_frame);
    }
    _first = false;
    return frame;
  }

  if (frame_type <= SAME_FRAME_EXTENDED) {
    // chop_frame or same_frame_extended
    locals = _prev_frame->locals();
    int length = _prev_frame->locals_size();
    int chops = SAME_FRAME_EXTENDED - frame_type;
    int new_length = length;
    u1 flags = _prev_frame->flags();
    assert(chops == 0 || (frame_type >= CHOP_FRAME_START && frame_type <= CHOP_FRAME_END), "should be");
    if (chops != 0) {
      new_length = chop(locals, length, chops);
      check_verification_type_array_size(
        new_length, _max_locals, CHECK_VERIFY_(_verifier, nullptr));
      // Recompute flags since uninitializedThis could have been chopped.
      flags = 0;
      for (int i=0; i<new_length; i++) {
        if (locals[i].is_uninitialized_this()) {
          flags |= FLAG_THIS_UNINIT;
          break;
        }
      }
    }
    if (_first) {
      offset = offset_delta;
      // Can't share the locals array since that is updated by the verifier.
      if (new_length > 0) {
        locals = NEW_RESOURCE_ARRAY_IN_THREAD(
          THREAD, VerificationType, new_length);
      } else {
        locals = nullptr;
      }
    } else {
      offset = _prev_frame->offset() + offset_delta + 1;
    }
    frame = new StackMapFrame(
      offset, flags, new_length, 0, _max_locals, _max_stack,
      locals, nullptr, _verifier);
    if (_first && locals != nullptr) {
      frame->copy_locals(_prev_frame);
    }
    _first = false;
    return frame;
  } else if (frame_type <= APPEND_FRAME_END) {
    // append_frame
    assert(frame_type >= APPEND_FRAME_START && frame_type <= APPEND_FRAME_END, "should be");
    int appends = frame_type - APPEND_FRAME_START + 1;
    int real_length = _prev_frame->locals_size();
    int new_length = real_length + appends*2;
    locals = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, VerificationType, new_length);
    VerificationType* pre_locals = _prev_frame->locals();
    for (int i = 0; i < _prev_frame->locals_size(); i++) {
      locals[i] = pre_locals[i];
    }
    u1 flags = _prev_frame->flags();
    for (int i = 0; i < appends; i++) {
      locals[real_length] = parse_verification_type(&flags, CHECK_NULL);
      if (locals[real_length].is_category2()) {
        locals[real_length + 1] = locals[real_length].to_category2_2nd();
        ++real_length;
      }
      ++real_length;
    }
    check_verification_type_array_size(
      real_length, _max_locals, CHECK_VERIFY_(_verifier, nullptr));
    if (_first) {
      offset = offset_delta;
    } else {
      offset = _prev_frame->offset() + offset_delta + 1;
    }
    frame = new StackMapFrame(
      offset, flags, real_length, 0, _max_locals,
      _max_stack, locals, nullptr, _verifier);
    _first = false;
    return frame;
  }
  if (frame_type == FULL_FRAME) {
    // full_frame
    u1 flags = 0;
    u2 locals_size = _stream->get_u2(CHECK_NULL);
    int real_locals_size = 0;
    if (locals_size > 0) {
      locals = NEW_RESOURCE_ARRAY_IN_THREAD(
        THREAD, VerificationType, locals_size*2);
    }
    for (int i = 0; i < locals_size; i++) {
      locals[real_locals_size] = parse_verification_type(&flags, CHECK_NULL);
      if (locals[real_locals_size].is_category2()) {
        locals[real_locals_size + 1] =
          locals[real_locals_size].to_category2_2nd();
        ++real_locals_size;
      }
      ++real_locals_size;
    }
    check_verification_type_array_size(
      real_locals_size, _max_locals, CHECK_VERIFY_(_verifier, nullptr));
    u2 stack_size = _stream->get_u2(CHECK_NULL);
    int real_stack_size = 0;
    VerificationType* stack = nullptr;
    if (stack_size > 0) {
      stack = NEW_RESOURCE_ARRAY_IN_THREAD(
        THREAD, VerificationType, stack_size*2);
    }
    for (int i = 0; i < stack_size; i++) {
      stack[real_stack_size] = parse_verification_type(nullptr, CHECK_NULL);
      if (stack[real_stack_size].is_category2()) {
        stack[real_stack_size + 1] = stack[real_stack_size].to_category2_2nd();
        ++real_stack_size;
      }
      ++real_stack_size;
    }
    check_verification_type_array_size(
      real_stack_size, _max_stack, CHECK_VERIFY_(_verifier, nullptr));
    if (_first) {
      offset = offset_delta;
    } else {
      offset = _prev_frame->offset() + offset_delta + 1;
    }
    frame = new StackMapFrame(
      offset, flags, real_locals_size, real_stack_size,
      _max_locals, _max_stack, locals, stack, _verifier);
    _first = false;
    return frame;
  }

  _stream->stackmap_format_error(
    "reserved frame type", CHECK_VERIFY_(_prev_frame->verifier(), nullptr));
  return nullptr;
}
