/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "memory/resourceArea.hpp"
#include "oops/annotations.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/signature.hpp"

Symbol* fieldDescriptor::generic_signature() const {
  if (!has_generic_signature()) {
    return nullptr;
  }
  return _cp->symbol_at(_fieldinfo.generic_signature_index());
}

bool fieldDescriptor::is_trusted_final() const {
  InstanceKlass* ik = field_holder();
  return is_final() && (is_static() || ik->is_hidden() || ik->is_record());
}

AnnotationArray* fieldDescriptor::annotations() const {
  InstanceKlass* ik = field_holder();
  Array<AnnotationArray*>* md = ik->fields_annotations();
  if (md == nullptr)
    return nullptr;
  return md->at(index());
}

AnnotationArray* fieldDescriptor::type_annotations() const {
  InstanceKlass* ik = field_holder();
  Array<AnnotationArray*>* type_annos = ik->fields_type_annotations();
  if (type_annos == nullptr)
    return nullptr;
  return type_annos->at(index());
}

constantTag fieldDescriptor::initial_value_tag() const {
  return constants()->tag_at(initial_value_index());
}

jint fieldDescriptor::int_initial_value() const {
  return constants()->int_at(initial_value_index());
}

jlong fieldDescriptor::long_initial_value() const {
  return constants()->long_at(initial_value_index());
}

jfloat fieldDescriptor::float_initial_value() const {
  return constants()->float_at(initial_value_index());
}

jdouble fieldDescriptor::double_initial_value() const {
  return constants()->double_at(initial_value_index());
}

oop fieldDescriptor::string_initial_value(TRAPS) const {
  return constants()->uncached_string_at(initial_value_index(), THREAD);
}

void fieldDescriptor::reinitialize(InstanceKlass* ik, const FieldInfo& fieldinfo) {
  if (_cp.is_null() || field_holder() != ik) {
    _cp = constantPoolHandle(Thread::current(), ik->constants());
    // _cp should now reference ik's constant pool; i.e., ik is now field_holder.
    // If the class is a scratch class, the constant pool points to the original class,
    // but that's ok because of constant pool merging.
    assert(field_holder() == ik || ik->is_scratch_class(), "must be already initialized to this class");
  }
  _fieldinfo = fieldinfo;
  guarantee(_fieldinfo.name_index() != 0 && _fieldinfo.signature_index() != 0, "bad constant pool index for fieldDescriptor");
}

void fieldDescriptor::print_on(outputStream* st) const {
  access_flags().print_on(st);
  if (field_flags().is_injected()) st->print("injected ");
  name()->print_value_on(st);
  st->print(" ");
  signature()->print_value_on(st);
  st->print(" @%d ", offset());
  if (WizardMode && has_initial_value()) {
    st->print("(initval ");
    constantTag t = initial_value_tag();
    if (t.is_int()) {
      st->print("int %d)", int_initial_value());
    } else if (t.is_long()){
      st->print_jlong(long_initial_value());
    } else if (t.is_float()){
      st->print("float %f)", float_initial_value());
    } else if (t.is_double()){
      st->print("double %lf)", double_initial_value());
    }
  }
}

void fieldDescriptor::print() const { print_on(tty); }

void fieldDescriptor::print_on_for(outputStream* st, oop obj) {
  print_on(st);
  st->print(" ");

  BasicType ft = field_type();
  switch (ft) {
    case T_BYTE:
      st->print("%d", obj->byte_field(offset()));
      break;
    case T_CHAR:
      {
        jchar c = obj->char_field(offset());
        st->print("%c %d", isprint(c) ? c : ' ', c);
      }
      break;
    case T_DOUBLE:
      st->print("%lf", obj->double_field(offset()));
      break;
    case T_FLOAT:
      st->print("%f", obj->float_field(offset()));
      break;
    case T_INT:
      st->print("%d", obj->int_field(offset()));
      break;
    case T_LONG:
      st->print_jlong(obj->long_field(offset()));
      break;
    case T_SHORT:
      st->print("%d", obj->short_field(offset()));
      break;
    case T_BOOLEAN:
      st->print("%s", obj->bool_field(offset()) ? "true" : "false");
      break;
    case T_ARRAY:
      if (obj->obj_field(offset()) != nullptr) {
        obj->obj_field(offset())->print_value_on(st);
      } else {
        st->print("null");
      }
      break;
    case T_OBJECT:
      if (obj->obj_field(offset()) != nullptr) {
        obj->obj_field(offset())->print_value_on(st);
      } else {
        st->print("null");
      }
      break;
    default:
      ShouldNotReachHere();
      break;
  }

  // Print a hint as to the underlying integer representation.
  if (is_reference_type(ft)) {
#ifdef _LP64
    if (UseCompressedOops) {
      st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(offset()));
    } else {
      st->print(" (" INT64_FORMAT_X_0 ")", (int64_t)obj->long_field(offset()));
    }
#else
    st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(offset()));
#endif
  } else { // Primitives
    switch (ft) {
      case T_LONG:    st->print(" (" INT64_FORMAT_X_0 ")", (int64_t)obj->long_field(offset())); break;
      case T_DOUBLE:  st->print(" (" INT64_FORMAT_X_0 ")", (int64_t)obj->long_field(offset())); break;
      case T_BYTE:    st->print(" (" INT8_FORMAT_X_0  ")", obj->byte_field(offset()));          break;
      case T_CHAR:    st->print(" (" INT16_FORMAT_X_0 ")", obj->char_field(offset()));          break;
      case T_FLOAT:   st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(offset()));           break;
      case T_INT:     st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(offset()));           break;
      case T_SHORT:   st->print(" (" INT16_FORMAT_X_0 ")", obj->short_field(offset()));         break;
      case T_BOOLEAN: st->print(" (" INT8_FORMAT_X_0  ")", obj->bool_field(offset()));          break;
    default:
      ShouldNotReachHere();
      break;
    }
  }
}
