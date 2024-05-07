/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "code/debugInfoRec.hpp"
#include "code/pcDesc.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/compiler_globals.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"

ScopeDesc::ScopeDesc(const nmethod* code, PcDesc* pd, bool ignore_objects) {
  int obj_decode_offset = ignore_objects ? DebugInformationRecorder::serialized_null : pd->obj_decode_offset();
  _code          = code;
  _decode_offset = pd->scope_decode_offset();
  _objects       = decode_object_values(obj_decode_offset);
  _reexecute     = pd->should_reexecute();
  _rethrow_exception = pd->rethrow_exception();
  _return_oop    = pd->return_oop();
  _has_ea_local_in_scope = ignore_objects ? false : pd->has_ea_local_in_scope();
  _arg_escape    = ignore_objects ? false : pd->arg_escape();
  decode_body();
}


void ScopeDesc::initialize(const ScopeDesc* parent, int decode_offset) {
  _code          = parent->_code;
  _decode_offset = decode_offset;
  _objects       = parent->_objects;
  _reexecute     = false; //reexecute only applies to the first scope
  _rethrow_exception = false;
  _return_oop    = false;
  _has_ea_local_in_scope = parent->has_ea_local_in_scope();
  _arg_escape    = false;
  decode_body();
}

ScopeDesc::ScopeDesc(const ScopeDesc* parent) {
  initialize(parent, parent->_sender_decode_offset);
}

ScopeDesc::ScopeDesc(const ScopeDesc* parent, int decode_offset) {
  initialize(parent, decode_offset);
}


void ScopeDesc::decode_body() {
  if (decode_offset() == DebugInformationRecorder::serialized_null) {
    // This is a sentinel record, which is only relevant to
    // approximate queries.  Decode a reasonable frame.
    _sender_decode_offset = DebugInformationRecorder::serialized_null;
    _method = _code->method();
    _bci = InvocationEntryBci;
    _locals_decode_offset = DebugInformationRecorder::serialized_null;
    _expressions_decode_offset = DebugInformationRecorder::serialized_null;
    _monitors_decode_offset = DebugInformationRecorder::serialized_null;
  } else {
    // decode header
    DebugInfoReadStream* stream  = stream_at(decode_offset());

    _sender_decode_offset = stream->read_int();
    _method = stream->read_method();
    _bci    = stream->read_bci();

    // decode offsets for body and sender
    _locals_decode_offset      = stream->read_int();
    _expressions_decode_offset = stream->read_int();
    _monitors_decode_offset    = stream->read_int();
  }
}


GrowableArray<ScopeValue*>* ScopeDesc::decode_scope_values(int decode_offset) {
  if (decode_offset == DebugInformationRecorder::serialized_null) return nullptr;
  DebugInfoReadStream* stream = stream_at(decode_offset);
  int length = stream->read_int();
  GrowableArray<ScopeValue*>* result = new GrowableArray<ScopeValue*> (length);
  for (int index = 0; index < length; index++) {
    result->push(ScopeValue::read_from(stream));
  }
  return result;
}

GrowableArray<ScopeValue*>* ScopeDesc::decode_object_values(int decode_offset) {
  if (decode_offset == DebugInformationRecorder::serialized_null) return nullptr;
  GrowableArray<ScopeValue*>* result = new GrowableArray<ScopeValue*>();
  DebugInfoReadStream* stream = new DebugInfoReadStream(_code, decode_offset, result);
  int length = stream->read_int();
  for (int index = 0; index < length; index++) {
    // Objects values are pushed to 'result' array during read so that
    // object's fields could reference it (OBJECT_ID_CODE).
    (void)ScopeValue::read_from(stream);
  }
  return result;
}


GrowableArray<MonitorValue*>* ScopeDesc::decode_monitor_values(int decode_offset) {
  if (decode_offset == DebugInformationRecorder::serialized_null) return nullptr;
  DebugInfoReadStream* stream  = stream_at(decode_offset);
  int length = stream->read_int();
  GrowableArray<MonitorValue*>* result = new GrowableArray<MonitorValue*> (length);
  for (int index = 0; index < length; index++) {
    result->push(new MonitorValue(stream));
  }
  return result;
}

GrowableArray<ScopeValue*>* ScopeDesc::objects_to_rematerialize(frame& frm, RegisterMap& map) {
  if (_objects == nullptr) {
    return nullptr;
  }

  GrowableArray<ScopeValue*>* result = new GrowableArray<ScopeValue*>();
  for (int i = 0; i < _objects->length(); i++) {
    assert(_objects->at(i)->is_object(), "invalid debug information");
    ObjectValue* sv = _objects->at(i)->as_ObjectValue();

    // If the object is not referenced in current JVM state, then it's only
    // a candidate in an ObjectMergeValue, we don't need to rematerialize it
    // unless when/if it's returned by 'select()' below.
    if (!sv->is_root()) {
      continue;
    }

    if (sv->is_object_merge()) {
      sv = sv->as_ObjectMergeValue()->select(frm, map);
      // 'select(...)' may return an ObjectValue that actually represents a
      // non-scalar replaced object participating in a merge.
      if (!sv->is_scalar_replaced()) {
        continue;
      }
    }

    result->append_if_missing(sv);
  }

  return result;
}

DebugInfoReadStream* ScopeDesc::stream_at(int decode_offset) const {
  return new DebugInfoReadStream(_code, decode_offset, _objects);
}

GrowableArray<ScopeValue*>* ScopeDesc::locals() {
  return decode_scope_values(_locals_decode_offset);
}

GrowableArray<ScopeValue*>* ScopeDesc::expressions() {
  return decode_scope_values(_expressions_decode_offset);
}

GrowableArray<MonitorValue*>* ScopeDesc::monitors() {
  return decode_monitor_values(_monitors_decode_offset);
}

GrowableArray<ScopeValue*>* ScopeDesc::objects() {
  return _objects;
}

bool ScopeDesc::is_top() const {
 return _sender_decode_offset == DebugInformationRecorder::serialized_null;
}

ScopeDesc* ScopeDesc::sender() const {
  if (is_top()) return nullptr;
  return new ScopeDesc(this);
}


#ifndef PRODUCT

void ScopeDesc::print_value_on(outputStream* st) const {
  st->print("  ");
  method()->print_short_name(st);
  int lineno = method()->line_number_from_bci(bci());
  if (lineno != -1) {
    st->print("@%d (line %d)", bci(), lineno);
  } else {
    st->print("@%d", bci());
  }
  if (should_reexecute()) {
    st->print("  reexecute=true");
  }
  st->cr();
}

void ScopeDesc::print_on(outputStream* st) const {
  print_on(st, nullptr);
}

void ScopeDesc::print_on(outputStream* st, PcDesc* pd) const {
  // header
  if (pd != nullptr) {
    st->print_cr("ScopeDesc(pc=" PTR_FORMAT " offset=%x):", p2i(pd->real_pc(_code)), pd->pc_offset());
  }

  print_value_on(st);
  // decode offsets
  if (WizardMode) {
    st->print("ScopeDesc[%d]@" PTR_FORMAT " ", _decode_offset, p2i(_code->content_begin()));
    st->print_cr(" offset:     %d",    _decode_offset);
    st->print_cr(" bci:        %d",    bci());
    st->print_cr(" reexecute:  %s",    should_reexecute() ? "true" : "false");
    st->print_cr(" locals:     %d",    _locals_decode_offset);
    st->print_cr(" stack:      %d",    _expressions_decode_offset);
    st->print_cr(" monitor:    %d",    _monitors_decode_offset);
    st->print_cr(" sender:     %d",    _sender_decode_offset);
  }
  // locals
  { GrowableArray<ScopeValue*>* l = ((ScopeDesc*) this)->locals();
    if (l != nullptr) {
      st->print_cr("   Locals");
      for (int index = 0; index < l->length(); index++) {
        st->print("    - l%d: ", index);
        l->at(index)->print_on(st);
        st->cr();
      }
    }
  }
  // expressions
  { GrowableArray<ScopeValue*>* l = ((ScopeDesc*) this)->expressions();
    if (l != nullptr) {
      st->print_cr("   Expression stack");
      for (int index = 0; index < l->length(); index++) {
        st->print("    - @%d: ", index);
        l->at(index)->print_on(st);
        st->cr();
      }
    }
  }
  // monitors
  { GrowableArray<MonitorValue*>* l = ((ScopeDesc*) this)->monitors();
    if (l != nullptr) {
      st->print_cr("   Monitor stack");
      for (int index = 0; index < l->length(); index++) {
        st->print("    - @%d: ", index);
        l->at(index)->print_on(st);
        st->cr();
      }
    }
  }

#if COMPILER2_OR_JVMCI
  if (NOT_JVMCI(DoEscapeAnalysis &&) is_top() && _objects != nullptr) {
    st->print_cr("   Objects");
    for (int i = 0; i < _objects->length(); i++) {
      ObjectValue* sv = (ObjectValue*) _objects->at(i);
      st->print("    - %d: %c ", i, sv->is_root() ? 'R' : ' ');
      sv->print_on(st);
      st->print(", ");
      if (!sv->is_object_merge()) {
        st->print("%s", java_lang_Class::as_Klass(sv->klass()->as_ConstantOopReadValue()->value()())->external_name());
      }
      sv->print_fields_on(st);
      st->cr();
    }
  }
#endif // COMPILER2_OR_JVMCI
}

#endif

void ScopeDesc::verify() {
  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);
  guarantee(method()->is_method(), "type check");

  // check if we have any illegal elements on the expression stack
  { GrowableArray<ScopeValue*>* l = expressions();
    if (l != nullptr) {
      for (int index = 0; index < l->length(); index++) {
       //guarantee(!l->at(index)->is_illegal(), "expression element cannot be illegal");
      }
    }
  }
}
