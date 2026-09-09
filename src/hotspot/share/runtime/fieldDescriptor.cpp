/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/fieldStreams.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/valueKlass.inline.hpp"
#include "runtime/arguments.hpp"
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
  return is_final() && (is_static() || ik->is_hidden() || ik->is_record() || ik->is_value_klass()
                        || (ik->is_abstract() && !ik->is_identity_class() && !ik->is_interface()));
}

bool fieldDescriptor::is_mutable_static_final() const {
  InstanceKlass* ik = field_holder();
  // write protected fields (JLS 17.5.4)
  if (is_final() && is_static() && ik == vmClasses::System_klass() &&
      (offset() == java_lang_System::in_offset() || offset() == java_lang_System::out_offset() || offset() == java_lang_System::err_offset())) {
   return true;
  }
  return false;
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

ValueKlass* fieldDescriptor::flat_field_klass() {
  precond(is_flat());
  return field_holder()->get_value_type_field_klass(index());
}

bool fieldDescriptor::is_flat_field_marked_as_null(address obj, const ValuePayloadContext* vpc) {
  precond(is_flat());
  if (is_null_free_value_type()) {
    return false; // Cannot be marked as null.
  } else {
    return flat_field_klass()->is_payload_marked_as_null(obj + field_offset_in_obj(vpc));
  }
}

int fieldDescriptor::field_offset_in_obj(const ValuePayloadContext* vpc) const {
  if (vpc == nullptr) {
    return offset();
  } else {
    precond(vpc->klass() != nullptr);
    precond(vpc->offset_in_obj() != 0);
    precond(vpc->klass() == this->field_holder());

    // Compute the offset of the field represented by this fieldDescriptor from
    // the beginning of an heap oop.
    //
    // Using the example Point class from the comments above the declaration of
    // ValuePayloadContext, if we are looking at Point::y::value,
    //
    //     this->field_holder()    : InstanceKlass java/lang/Integer
    //     vpc->klass()            : ValueKlass java/lang/Integer (we are looking at a field in a flattened Integer)
    //     vpc->_offset_in_obj     : 12 (this flattened Integer starts at offset 12 of obj)
    //     this->name()            : "value" (the field that we are looking at. Note: it's NOT "y")
    //     this->field_type()      : T_INT
    //     this->offset()          : 8 (the offset of the "value" field in a regular Integer heap oop)
    //     vpc->klass()->payload_offset() : 8 (the first 8 bytes of a regular Integer heap oop are excluded from the flattened copy)
    //   =>
    //     field_offset_in_obj() : 12 + (8 - 8) == 12 (offset inside a Point object)
    int offset_in_value_payload = this->offset() - ValueKlass::cast(this->field_holder())->payload_offset();
    return vpc->offset_in_obj() + offset_in_value_payload;
  }
}

void fieldDescriptor::reinitialize(const InstanceKlass* ik, const FieldInfo& fieldinfo) {
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

void fieldDescriptor::print_access_flags(outputStream* st) const {
  AccessFlags flags = access_flags();
  if (flags.is_public   ()) st->print("public ");
  if (flags.is_private  ()) st->print("private ");
  if (flags.is_protected()) st->print("protected ");
  if (flags.is_static   ()) st->print("static ");
  if (flags.is_final    ()) st->print("final ");
  if (flags.is_volatile ()) st->print("volatile ");
  if (flags.is_transient()) st->print("transient ");
  if (flags.is_enum     ()) st->print("enum ");
  if (flags.is_synthetic()) st->print("synthetic ");
  if (Arguments::is_valhalla_enabled()) {
    if (flags.is_identity_class()) st->print("identity ");
    if (!flags.is_identity_class()) st->print("value "  );
  }
}

// Print information (such as type, name, offset) of this field.
void fieldDescriptor::print_on(outputStream* st, const ValuePayloadContext* vpc) const {
  print_access_flags(st);
  if (field_flags().is_injected()) st->print("injected ");
  bool flat = field_flags().is_flat();
  if (flat) st->print("flat ");
  name()->print_value_on(st);
  st->print(" (fields 0x%08x) ", field_flags().as_uint());
  signature()->print_value_on(st);
  st->print(" @%d ", (vpc == nullptr) ? offset() : field_offset_in_obj(vpc));
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
    st->print(" ");
  }
  if (flat) LayoutKindHelper::print_on(layout_kind(), st);
}

void fieldDescriptor::print() const { print_on(tty); }

void fieldDescriptor::print_on_for(outputStream* st, oop obj, int indent, const ValuePayloadContext* vpc) {
  BasicType ft = field_type();
  int field_offset_in_obj = this->field_offset_in_obj(vpc);
  print_on(st, vpc);
  st->print(" ");
  jint as_int = 0;
  switch (ft) {
    case T_BYTE:
      st->print("%d", obj->byte_field(field_offset_in_obj));
      break;
    case T_CHAR:
      {
        jchar c = obj->char_field(field_offset_in_obj);
        st->print("%c %d", isprint(c) ? c : ' ', c);
      }
      break;
    case T_DOUBLE:
      st->print("%lf", obj->double_field(field_offset_in_obj));
      break;
    case T_FLOAT:
      st->print("%f", obj->float_field(field_offset_in_obj));
      break;
    case T_INT:
      st->print("%d", obj->int_field(field_offset_in_obj));
      break;
    case T_LONG:
      st->print_jlong(obj->long_field(field_offset_in_obj));
      break;
    case T_SHORT:
      st->print("%d", obj->short_field(field_offset_in_obj));
      break;
    case T_BOOLEAN:
      st->print("%s", obj->bool_field(field_offset_in_obj) ? "true" : "false");
      break;
    case T_ARRAY:
    case T_OBJECT:
      if (is_flat()) {
        ValueKlass* vk = flat_field_klass();
        bool is_null = is_flat_field_marked_as_null(obj, vpc);

        if (!is_null_free_value_type()) {
          assert(has_null_marker(), "should have null marker");
          st->print("Flat value type field '%s':", vk->name()->as_C_string());
          precond(is_null == vk->is_payload_marked_as_null(obj, field_offset_in_obj));
          if (is_null) {
            st->print(" null");
          }
          st->cr();
        } else {
          precond(!is_null);
          st->print_cr("Flat value null-free type field '%s':", vk->name()->as_C_string());
        }

        if (!is_null) {
          // Print fields declared inside this flat field (which is a type of vk)
          ValuePayloadContext field_vpc{vk, field_offset_in_obj};
          FieldPrinter print_field(st, obj, indent + 1, &field_vpc);
          vk->do_nonstatic_fields(&print_field);
        }

        if (field_flags().has_null_marker()) {
          for (int i = 0; i < indent + 1; i++) st->print("  ");
          st->print_cr(" - [null_marker] @%d %s",
                    field_offset_in_obj + vk->null_marker_offset_in_payload(),
                    is_null ? "Field marked as null" : "Field marked as non-null");
        }
        return; // No need to print underlying representation again (already printed by FieldPrinter above)
      }
      // Not flat value type field, fall through
      if (obj->obj_field(field_offset_in_obj) != nullptr) {
        obj->obj_field(field_offset_in_obj)->print_value_on(st);
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
      st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(field_offset_in_obj));
    } else {
      st->print(" (" INT64_FORMAT_X_0 ")", (int64_t)obj->long_field(field_offset_in_obj));
    }
#else
    st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(field_offset_in_obj));
#endif
  } else { // Primitives
    switch (ft) {
      case T_LONG:    st->print(" (" INT64_FORMAT_X_0 ")", (int64_t)obj->long_field(field_offset_in_obj)); break;
      case T_DOUBLE:  st->print(" (" INT64_FORMAT_X_0 ")", (int64_t)obj->long_field(field_offset_in_obj)); break;
      case T_BYTE:    st->print(" (" INT8_FORMAT_X_0  ")", obj->byte_field(field_offset_in_obj));          break;
      case T_CHAR:    st->print(" (" INT16_FORMAT_X_0 ")", obj->char_field(field_offset_in_obj));          break;
      case T_FLOAT:   st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(field_offset_in_obj));           break;
      case T_INT:     st->print(" (" INT32_FORMAT_X_0 ")", obj->int_field(field_offset_in_obj));           break;
      case T_SHORT:   st->print(" (" INT16_FORMAT_X_0 ")", obj->short_field(field_offset_in_obj));         break;
      case T_BOOLEAN: st->print(" (" INT8_FORMAT_X_0  ")", obj->bool_field(field_offset_in_obj));          break;
    default:
      ShouldNotReachHere();
      break;
    }
  }
}

FieldPrinter::FieldPrinter(outputStream* st, oop obj, int indent, const ValuePayloadContext* vpc) :
  FieldClosure(), _obj(obj), _st(st), _indent(indent), _vpc(vpc) {
  if (obj == nullptr) {
    assert(vpc == nullptr, "flattening not supported for static fields");
  } else {
    if (vpc != nullptr) {
      assert(obj->klass() != vpc->klass(), "a value object cannot be flattened into itself");
    }
  }
}

void FieldPrinter::do_field(fieldDescriptor* fd) {
  for (int i = 0; i < _indent; i++) _st->print("  ");
  _st->print(" - ");
  if (_obj == nullptr) {
    precond(_vpc == nullptr);
    fd->print_on(_st);
    _st->cr();
  } else {
    fd->print_on_for(_st, _obj, _indent, _vpc);
    if (!fd->field_flags().is_flat()) _st->cr();
  }
}
