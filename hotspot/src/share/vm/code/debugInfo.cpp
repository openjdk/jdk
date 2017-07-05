/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/handles.inline.hpp"

// Comstructors

DebugInfoWriteStream::DebugInfoWriteStream(DebugInformationRecorder* recorder, int initial_size)
: CompressedWriteStream(initial_size) {
  _recorder = recorder;
}

// Serializing oops

void DebugInfoWriteStream::write_handle(jobject h) {
  write_int(recorder()->oop_recorder()->find_index(h));
}

void DebugInfoWriteStream::write_metadata(Metadata* h) {
  write_int(recorder()->oop_recorder()->find_index(h));
}

ScopeValue* DebugInfoReadStream::read_object_value() {
  int id = read_int();
#ifdef ASSERT
  assert(_obj_pool != NULL, "object pool does not exist");
  for (int i = _obj_pool->length() - 1; i >= 0; i--) {
    assert(((ObjectValue*) _obj_pool->at(i))->id() != id, "should not be read twice");
  }
#endif
  ObjectValue* result = new ObjectValue(id);
  // Cache the object since an object field could reference it.
  _obj_pool->push(result);
  result->read_object(this);
  return result;
}

ScopeValue* DebugInfoReadStream::get_cached_object() {
  int id = read_int();
  assert(_obj_pool != NULL, "object pool does not exist");
  for (int i = _obj_pool->length() - 1; i >= 0; i--) {
    ObjectValue* ov = (ObjectValue*) _obj_pool->at(i);
    if (ov->id() == id) {
      return ov;
    }
  }
  ShouldNotReachHere();
  return NULL;
}

// Serializing scope values

enum { LOCATION_CODE = 0, CONSTANT_INT_CODE = 1,  CONSTANT_OOP_CODE = 2,
                          CONSTANT_LONG_CODE = 3, CONSTANT_DOUBLE_CODE = 4,
                          OBJECT_CODE = 5,        OBJECT_ID_CODE = 6 };

ScopeValue* ScopeValue::read_from(DebugInfoReadStream* stream) {
  ScopeValue* result = NULL;
  switch(stream->read_int()) {
   case LOCATION_CODE:        result = new LocationValue(stream);        break;
   case CONSTANT_INT_CODE:    result = new ConstantIntValue(stream);     break;
   case CONSTANT_OOP_CODE:    result = new ConstantOopReadValue(stream); break;
   case CONSTANT_LONG_CODE:   result = new ConstantLongValue(stream);    break;
   case CONSTANT_DOUBLE_CODE: result = new ConstantDoubleValue(stream);  break;
   case OBJECT_CODE:          result = stream->read_object_value();      break;
   case OBJECT_ID_CODE:       result = stream->get_cached_object();      break;
   default: ShouldNotReachHere();
  }
  return result;
}

// LocationValue

LocationValue::LocationValue(DebugInfoReadStream* stream) {
  _location = Location(stream);
}

void LocationValue::write_on(DebugInfoWriteStream* stream) {
  stream->write_int(LOCATION_CODE);
  location().write_on(stream);
}

void LocationValue::print_on(outputStream* st) const {
  location().print_on(st);
}

// ObjectValue

void ObjectValue::read_object(DebugInfoReadStream* stream) {
  _klass = read_from(stream);
  assert(_klass->is_constant_oop(), "should be constant java mirror oop");
  int length = stream->read_int();
  for (int i = 0; i < length; i++) {
    ScopeValue* val = read_from(stream);
    _field_values.append(val);
  }
}

void ObjectValue::write_on(DebugInfoWriteStream* stream) {
  if (_visited) {
    stream->write_int(OBJECT_ID_CODE);
    stream->write_int(_id);
  } else {
    _visited = true;
    stream->write_int(OBJECT_CODE);
    stream->write_int(_id);
    _klass->write_on(stream);
    int length = _field_values.length();
    stream->write_int(length);
    for (int i = 0; i < length; i++) {
      _field_values.at(i)->write_on(stream);
    }
  }
}

void ObjectValue::print_on(outputStream* st) const {
  st->print("obj[%d]", _id);
}

void ObjectValue::print_fields_on(outputStream* st) const {
#ifndef PRODUCT
  if (_field_values.length() > 0) {
    _field_values.at(0)->print_on(st);
  }
  for (int i = 1; i < _field_values.length(); i++) {
    st->print(", ");
    _field_values.at(i)->print_on(st);
  }
#endif
}

// ConstantIntValue

ConstantIntValue::ConstantIntValue(DebugInfoReadStream* stream) {
  _value = stream->read_signed_int();
}

void ConstantIntValue::write_on(DebugInfoWriteStream* stream) {
  stream->write_int(CONSTANT_INT_CODE);
  stream->write_signed_int(value());
}

void ConstantIntValue::print_on(outputStream* st) const {
  st->print("%d", value());
}

// ConstantLongValue

ConstantLongValue::ConstantLongValue(DebugInfoReadStream* stream) {
  _value = stream->read_long();
}

void ConstantLongValue::write_on(DebugInfoWriteStream* stream) {
  stream->write_int(CONSTANT_LONG_CODE);
  stream->write_long(value());
}

void ConstantLongValue::print_on(outputStream* st) const {
  st->print(INT64_FORMAT, value());
}

// ConstantDoubleValue

ConstantDoubleValue::ConstantDoubleValue(DebugInfoReadStream* stream) {
  _value = stream->read_double();
}

void ConstantDoubleValue::write_on(DebugInfoWriteStream* stream) {
  stream->write_int(CONSTANT_DOUBLE_CODE);
  stream->write_double(value());
}

void ConstantDoubleValue::print_on(outputStream* st) const {
  st->print("%f", value());
}

// ConstantOopWriteValue

void ConstantOopWriteValue::write_on(DebugInfoWriteStream* stream) {
  assert(JNIHandles::resolve(value()) == NULL ||
         Universe::heap()->is_in_reserved(JNIHandles::resolve(value())),
         "Should be in heap");
  stream->write_int(CONSTANT_OOP_CODE);
  stream->write_handle(value());
}

void ConstantOopWriteValue::print_on(outputStream* st) const {
  JNIHandles::resolve(value())->print_value_on(st);
}


// ConstantOopReadValue

ConstantOopReadValue::ConstantOopReadValue(DebugInfoReadStream* stream) {
  _value = Handle(stream->read_oop());
  assert(_value() == NULL ||
         Universe::heap()->is_in_reserved(_value()), "Should be in heap");
}

void ConstantOopReadValue::write_on(DebugInfoWriteStream* stream) {
  ShouldNotReachHere();
}

void ConstantOopReadValue::print_on(outputStream* st) const {
  value()()->print_value_on(st);
}


// MonitorValue

MonitorValue::MonitorValue(ScopeValue* owner, Location basic_lock, bool eliminated) {
  _owner       = owner;
  _basic_lock  = basic_lock;
  _eliminated  = eliminated;
}

MonitorValue::MonitorValue(DebugInfoReadStream* stream) {
  _basic_lock  = Location(stream);
  _owner       = ScopeValue::read_from(stream);
  _eliminated  = (stream->read_bool() != 0);
}

void MonitorValue::write_on(DebugInfoWriteStream* stream) {
  _basic_lock.write_on(stream);
  _owner->write_on(stream);
  stream->write_bool(_eliminated);
}

#ifndef PRODUCT
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
#endif
