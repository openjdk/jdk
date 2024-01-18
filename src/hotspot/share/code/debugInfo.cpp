/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/debugInfo.hpp"
#include "code/debugInfoRec.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/stackValue.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.inline.hpp"

// Constructors

static const UNSIGNED5::Statistics::Kind DI = UNSIGNED5::Statistics::DI;

DebugInfoWriteStream::DebugInfoWriteStream(DebugInformationRecorder* recorder, int initial_size)
  : CompressedIntWriteStream(nullptr, initial_size,
                             UNSIGNED5::Statistics::zero_suppress_setting(DI))
{
  _recorder = recorder;
  _pending_tag = -1;
}

DebugInfoReadStream::DebugInfoReadStream(const CompiledMethod* code, int offset,
                                         GrowableArray<ScopeValue*>* obj_pool)
  : CompressedIntReadStream(code->scopes_data_begin(), 0,
                            UNSIGNED5::Statistics::zero_suppress_setting(DI))
{
  _code = code;
  _obj_pool = obj_pool;
  DEBUG_ONLY(_have_second_part = false);
  _second_part = 0;  // tidy
  reset_at(offset);
}
// Serializing oops

juint DebugInfoWriteStream::encode_handle(jobject h) {
  return recorder()->oop_recorder()->find_index(h);
}

void DebugInfoWriteStream::write_metadata(Metadata* h) {
  write_int(recorder()->oop_recorder()->find_index(h));
}

oop DebugInfoReadStream::decode_oop(juint oop_id) {
  nmethod* nm = const_cast<CompiledMethod*>(code())->as_nmethod_or_null();
  oop o;
  if (nm != nullptr) {
    // Despite these oops being found inside nmethods that are on-stack,
    // they are not kept alive by all GCs (e.g. G1 and Shenandoah).
    o = nm->oop_at_phantom(oop_id);
  } else {
    o = code()->oop_at(oop_id);
  }
  assert(oopDesc::is_oop_or_null(o), "oop only");
  return o;
}

ScopeValue* DebugInfoReadStream::read_object_value(bool is_auto_box) {
  int id = read_post_tag();
#ifdef ASSERT
  assert(_obj_pool != nullptr, "object pool does not exist");
  for (int i = _obj_pool->length() - 1; i >= 0; i--) {
    assert(_obj_pool->at(i)->as_ObjectValue()->id() != id, "should not be read twice");
  }
#endif
  ObjectValue* result = is_auto_box ? new AutoBoxObjectValue(id) : new ObjectValue(id);
  // Cache the object since an object field could reference it.
  _obj_pool->push(result);
  result->read_object(this);
  return result;
}

ScopeValue* DebugInfoReadStream::read_object_merge_value() {
  int id = read_post_tag();
#ifdef ASSERT
  assert(_obj_pool != nullptr, "object pool does not exist");
  for (int i = _obj_pool->length() - 1; i >= 0; i--) {
    assert(_obj_pool->at(i)->as_ObjectValue()->id() != id, "should not be read twice");
  }
#endif
  ObjectMergeValue* result = new ObjectMergeValue(id);
  _obj_pool->push(result);
  result->read_object(this);
  return result;
}

ScopeValue* DebugInfoReadStream::get_cached_object() {
  int id = read_post_tag();
  assert(_obj_pool != nullptr, "object pool does not exist");
  for (int i = _obj_pool->length() - 1; i >= 0; i--) {
    ObjectValue* ov = _obj_pool->at(i)->as_ObjectValue();
    if (ov->id() == id) {
      return ov;
    }
  }
  ShouldNotReachHere();
  return nullptr;
}

// Serializing scope values

enum { LOCATION_CODE = 0, CONSTANT_INT_CODE = 1,  CONSTANT_OOP_CODE = 2,
                          CONSTANT_LONG_CODE = 3, CONSTANT_DOUBLE_CODE = 4,
                          OBJECT_CODE = 5,        OBJECT_ID_CODE = 6,
                          AUTO_BOX_OBJECT_CODE = 7, MARKER_CODE = 8,
                          OBJECT_MERGE_CODE = 9,
                          DI_CODE_LIMIT = 10
};
// Note:  The LOCATION code is by far the most frequent.
// For best compression it must be assigned the zero encoding (0).
// The INT and OOP codes are next most common.  Assign them next.
// The others occur at fractions of a percent.
// If we ever create another common code, put it near the start of this order.

static size_t di_code_counts[DI_CODE_LIMIT];
// cross-call from UNSIGNED5::Statistics::print_on
size_t* report_di_code_counts(int& length) {
  length = DI_CODE_LIMIT;
  return di_code_counts;
  // Typical counts for -Xcomp hello-world:
  //   591180 9788 12696 143 0 134 114 0 0 0
  // The first five are LOCATION, INT, OOP, LONG, DOUBLE.
  // Two bits is plenty for the tag component in write_uint_pair.
  // The rare codes will require one extra byte in the pair encoding.
}

void DebugInfoWriteStream::write_tag_and_post(int tag, juint post) {
  assert(tag >= 0 && tag < DI_CODE_LIMIT, "");
  di_code_counts[tag]++;
  if (UNSIGNED5::Statistics::compression_enabled(DI)) {
    write_int_pair(UNSIGNED5::Statistics::int_pair_setting(DI), tag, post);
  } else {
    write_int(tag);
    write_int(post);
  }
}

int DebugInfoReadStream::read_tag() {
  if (!UNSIGNED5::Statistics::compression_enabled(DI)) {
    return read_int();
  }
  assert(!hsp(), "");
  DEBUG_ONLY(_have_second_part = true);
  juint& y = _second_part;
  return read_int_pair(UNSIGNED5::Statistics::int_pair_setting(DI), &y);
}

juint DebugInfoReadStream::read_post_tag() {
  if (!UNSIGNED5::Statistics::compression_enabled(DI)) {
    return read_int();
  }
  assert(hsp(), "");
  DEBUG_ONLY(_have_second_part = false);
  return _second_part;
}

ScopeValue* ScopeValue::read_from(DebugInfoReadStream* stream) {
  ScopeValue* result = nullptr;
  int tag = stream->read_tag();
  switch (tag) {
   case LOCATION_CODE:        result = new LocationValue(stream);                        break;
   case CONSTANT_INT_CODE:    result = new ConstantIntValue(stream);                     break;
   case CONSTANT_OOP_CODE:    result = new ConstantOopReadValue(stream);                 break;
   case CONSTANT_LONG_CODE:   result = new ConstantLongValue(stream);                    break;
   case CONSTANT_DOUBLE_CODE: result = new ConstantDoubleValue(stream);                  break;
   case OBJECT_CODE:          result = stream->read_object_value(false /*is_auto_box*/); break;
   case AUTO_BOX_OBJECT_CODE: result = stream->read_object_value(true /*is_auto_box*/);  break;
   case OBJECT_MERGE_CODE:    result = stream->read_object_merge_value();                break;
   case OBJECT_ID_CODE:       result = stream->get_cached_object();                      break;
   case MARKER_CODE:          result = new MarkerValue(stream);                          break;
   default: assert(false, "bad tag %d", tag);
  }
  return result;
}

// LocationValue

LocationValue::LocationValue(DebugInfoReadStream* stream) {
  _location = Location(true, stream);
}

void LocationValue::write_on(DebugInfoWriteStream* stream) {
  stream->write_tag(LOCATION_CODE);
  location().write_on(true, stream);
}

void LocationValue::print_on(outputStream* st) const {
  st->print("loc:");
  location().print_on(st);
}

// MarkerValue

MarkerValue::MarkerValue(DebugInfoReadStream* stream) {
  int discard = (!UNSIGNED5::Statistics::compression_enabled(DI)) ? 0 : stream->read_post_tag();
  assert(discard == 0, "");
}

void MarkerValue::write_on(DebugInfoWriteStream* stream) {
  if (!UNSIGNED5::Statistics::compression_enabled(DI)) { stream->write_int(MARKER_CODE); return; }
  stream->write_tag_and_post(MARKER_CODE, 0);
  // discarded zero pairs up with the MARKER_CODE; no extra overhead
}

void MarkerValue::print_on(outputStream* st) const {
  st->print("marker");
}

// ObjectValue

void ObjectValue::set_value(oop value) {
  _value = Handle(Thread::current(), value);
}

void ObjectValue::read_object(DebugInfoReadStream* stream) {
  _is_root = stream->read_bool();
  _klass = read_from(stream);
  assert(_klass->is_constant_oop(), "should be constant java mirror oop");
  int length = stream->read_int();
  for (int i = 0; i < length; i++) {
    ScopeValue* val = read_from(stream);
    _field_values.append(val);
  }
}

void ObjectValue::write_on(DebugInfoWriteStream* stream) {
  if (is_visited()) {
    stream->write_tag_and_post(OBJECT_ID_CODE, _id);
  } else {
    set_visited(true);
    int tag = is_auto_box() ? AUTO_BOX_OBJECT_CODE : OBJECT_CODE;
    stream->write_tag_and_post(tag, _id);
    stream->write_bool(_is_root);
    _klass->write_on(stream);
    int length = _field_values.length();
    stream->write_int(length);
    for (int i = 0; i < length; i++) {
      _field_values.at(i)->write_on(stream);
    }
  }
}

void ObjectValue::print_on(outputStream* st) const {
  st->print("%s[%d]", is_auto_box() ? "box_obj" : is_object_merge() ? "merge_obj" : "obj", _id);
}

void ObjectValue::print_fields_on(outputStream* st) const {
#ifndef PRODUCT
  if (is_object_merge()) {
    ObjectMergeValue* omv = (ObjectMergeValue*)this;
    st->print("selector=\"");
    omv->selector()->print_on(st);
    st->print("\"");
    ScopeValue* merge_pointer = omv->merge_pointer();
    if (!(merge_pointer->is_object() && merge_pointer->as_ObjectValue()->value()() == nullptr) &&
        !(merge_pointer->is_constant_oop() && merge_pointer->as_ConstantOopReadValue()->value()() == nullptr)) {
      st->print(", merge_pointer=\"");
      merge_pointer->print_on(st);
      st->print("\"");
    }
    GrowableArray<ScopeValue*>* possible_objects = omv->possible_objects();
    st->print(", candidate_objs=[%d", possible_objects->at(0)->as_ObjectValue()->id());
    int ncandidates = possible_objects->length();
    for (int i = 1; i < ncandidates; i++) {
      st->print(", %d", possible_objects->at(i)->as_ObjectValue()->id());
    }
    st->print("]");
  } else {
    st->print("\n        Fields: ");
    if (_field_values.length() > 0) {
      _field_values.at(0)->print_on(st);
    }
    for (int i = 1; i < _field_values.length(); i++) {
      st->print(", ");
      _field_values.at(i)->print_on(st);
    }
  }
#endif
}


// ObjectMergeValue

// Returns the ObjectValue that should be used for the local that this
// ObjectMergeValue represents. ObjectMergeValue represents allocation
// merges in C2. This method will select which path the allocation merge
// took during execution of the Trap that triggered the rematerialization
// of the object.
ObjectValue* ObjectMergeValue::select(frame& fr, RegisterMap& reg_map) {
  StackValue* sv_selector = StackValue::create_stack_value(&fr, &reg_map, _selector);
  jint selector = sv_selector->get_jint();

  // If the selector is '-1' it means that execution followed the path
  // where no scalar replacement happened.
  // Otherwise, it is the index in _possible_objects array that holds
  // the description of the scalar replaced object.
  if (selector == -1) {
    StackValue* sv_merge_pointer = StackValue::create_stack_value(&fr, &reg_map, _merge_pointer);
    _selected = new ObjectValue(id());

    // Retrieve the pointer to the real object and use it as if we had
    // allocated it during the deoptimization
    _selected->set_value(sv_merge_pointer->get_obj()());

    // No need to rematerialize
    return nullptr;
  } else {
    assert(selector < _possible_objects.length(), "sanity");
    _selected = (ObjectValue*) _possible_objects.at(selector);
    return _selected;
  }
}

void ObjectMergeValue::read_object(DebugInfoReadStream* stream) {
  _selector = read_from(stream);
  _merge_pointer = read_from(stream);
  int ncandidates = stream->read_int();
  for (int i = 0; i < ncandidates; i++) {
    ScopeValue* result = read_from(stream);
    assert(result->is_object(), "Candidate is not an object!");
    ObjectValue* obj = result->as_ObjectValue();
    _possible_objects.append(obj);
  }
}

void ObjectMergeValue::write_on(DebugInfoWriteStream* stream) {
  if (is_visited()) {
    stream->write_tag_and_post(OBJECT_ID_CODE, _id);
  } else {
    set_visited(true);
    stream->write_tag_and_post(OBJECT_MERGE_CODE, _id);
    _selector->write_on(stream);
    _merge_pointer->write_on(stream);
    int ncandidates = _possible_objects.length();
    stream->write_int(ncandidates);
    for (int i = 0; i < ncandidates; i++) {
      _possible_objects.at(i)->as_ObjectValue()->write_on(stream);
    }
  }
}

// ConstantIntValue

ConstantIntValue::ConstantIntValue(DebugInfoReadStream* stream) {
  juint x = stream->read_post_tag();
  _value = UNSIGNED5::decode_sign(x);
}

void ConstantIntValue::write_on(DebugInfoWriteStream* stream) {
  juint y = UNSIGNED5::encode_sign(value());
  stream->write_tag_and_post(CONSTANT_INT_CODE, y);
}

void ConstantIntValue::print_on(outputStream* st) const {
  st->print("I:%d", value());
}

// ConstantLongValue

ConstantLongValue::ConstantLongValue(DebugInfoReadStream* stream) {
  juint x = stream->read_post_tag();
  juint y = stream->read_int();
  _value = CompressedStream::decode_long(x, y);
}

void ConstantLongValue::write_on(DebugInfoWriteStream* stream) {
  juint x, y;
  CompressedStream::encode_long(value(), x, y);
  stream->write_tag_and_post(CONSTANT_LONG_CODE, x);
  stream->write_int(y);
}

void ConstantLongValue::print_on(outputStream* st) const {
  st->print("J:" JLONG_FORMAT, value());
}

// ConstantDoubleValue

ConstantDoubleValue::ConstantDoubleValue(DebugInfoReadStream* stream) {
  juint x = stream->read_post_tag();
  juint y = stream->read_int();
  _value = CompressedStream::decode_double(x, y);
}

void ConstantDoubleValue::write_on(DebugInfoWriteStream* stream) {
  juint x, y;
  CompressedStream::encode_long(value(), x, y);
  stream->write_tag_and_post(CONSTANT_DOUBLE_CODE, x);
  stream->write_int(y);
}

void ConstantDoubleValue::print_on(outputStream* st) const {
  st->print("D:%f", value());
}

// ConstantOopWriteValue

void ConstantOopWriteValue::write_on(DebugInfoWriteStream* stream) {
#ifdef ASSERT
  {
    // cannot use ThreadInVMfromNative here since in case of JVMCI compiler,
    // thread is already in VM state.
    ThreadInVMfromUnknown tiv;
    assert(JNIHandles::resolve(value()) == nullptr ||
           Universe::heap()->is_in(JNIHandles::resolve(value())),
           "Should be in heap");
 }
#endif
  stream->write_tag_and_post(CONSTANT_OOP_CODE, stream->encode_handle(value()));
}

void ConstantOopWriteValue::print_on(outputStream* st) const {
  st->print("oop:");
  if (value() != nullptr) {
  // using ThreadInVMfromUnknown here since in case of JVMCI compiler,
  // thread is already in VM state.
  ThreadInVMfromUnknown tiv;
  JNIHandles::resolve(value())->print_value_on(st);
  } else {
    st->print("nullptr");
  }
}


// ConstantOopReadValue

ConstantOopReadValue::ConstantOopReadValue(DebugInfoReadStream* stream) {
  juint x = stream->read_post_tag();
  _value = Handle(Thread::current(), stream->decode_oop(x));
  assert(_value() == nullptr ||
         Universe::heap()->is_in(_value()), "Should be in heap");
}

void ConstantOopReadValue::write_on(DebugInfoWriteStream* stream) {
  ShouldNotReachHere();
}

void ConstantOopReadValue::print_on(outputStream* st) const {
  st->print("oop:");
  if (value()() != nullptr) {
    value()()->print_value_on(st);
  } else {
    st->print("nullptr");
  }
}


// MonitorValue

MonitorValue::MonitorValue(ScopeValue* owner, Location basic_lock, bool eliminated) {
  _owner       = owner;
  _basic_lock  = basic_lock;
  _eliminated  = eliminated;
}

MonitorValue::MonitorValue(DebugInfoReadStream* stream) {
  _basic_lock  = Location(false, stream);
  _owner       = ScopeValue::read_from(stream);
  _eliminated  = (stream->read_bool() != 0);
}

void MonitorValue::write_on(DebugInfoWriteStream* stream) {
  _basic_lock.write_on(false, stream);
  _owner->write_on(stream);
  stream->write_bool(_eliminated);
}

void MonitorValue::print_on(outputStream* st) const {
  st->print("monitor{");
  owner()->print_on(st);
  st->print(",");
  basic_lock().print_on(st);
  st->print("}");
  if (_eliminated) {
    st->print(" (eliminated)");
  }
}
