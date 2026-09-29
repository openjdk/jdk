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

#include "cds/cdsConfig.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/verificationType.hpp"
#include "classfile/verifier.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/handles.inline.hpp"

VerificationType VerificationType::from_tag(u1 tag) {
  switch (tag) {
    case ITEM_Top:     return bogus_type();
    case ITEM_Integer: return integer_type();
    case ITEM_Float:   return float_type();
    case ITEM_Double:  return double_type();
    case ITEM_Long:    return long_type();
    case ITEM_Null:    return null_type();
    default:
      ShouldNotReachHere();
      return bogus_type();
  }
}

// Potentially resolve the target class and from class, and check whether the from class is assignable
// to the target class. The current_klass is the class being verified - it could also be the target in some
// cases, and otherwise is needed to obtain the correct classloader for resolving the other classes.
bool VerificationType::resolve_and_check_assignability(InstanceKlass* current_klass, Symbol* target_name, Symbol* from_name,
                                                       bool from_field_is_protected, bool from_is_array,
                                                       bool from_is_object, bool* target_is_interface, TRAPS) {
  HandleMark hm(THREAD);
  Klass* target_klass;
  if (current_klass->is_hidden() && current_klass->name() == target_name) {
    target_klass = current_klass;
  } else {
    target_klass = SystemDictionary::resolve_or_fail(
      target_name, Handle(THREAD, current_klass->class_loader()), true, CHECK_false);
    if (log_is_enabled(Debug, class, resolve)) {
      Verifier::trace_class_resolution(target_klass, current_klass);
    }
  }

  bool is_intf = target_klass->is_interface();
  if (target_is_interface != nullptr) {
    *target_is_interface = is_intf;
  }

  if (is_intf && (!from_field_is_protected ||
      from_name != vmSymbols::java_lang_Object())) {
    // If we are not trying to access a protected field or method in
    // java.lang.Object then, for arrays, we only allow assignability
    // to interfaces java.lang.Cloneable and java.io.Serializable.
    // Otherwise, we treat interfaces as java.lang.Object.
    return !from_is_array ||
      target_klass == vmClasses::Cloneable_klass() ||
      target_klass == vmClasses::Serializable_klass();
  } else if (from_is_object) {
    Klass* from_klass;
    if (current_klass->is_hidden() && current_klass->name() == from_name) {
      from_klass = current_klass;
    } else {
      from_klass = SystemDictionary::resolve_or_fail(
        from_name, Handle(THREAD, current_klass->class_loader()), true, CHECK_false);
      if (log_is_enabled(Debug, class, resolve)) {
        Verifier::trace_class_resolution(from_klass, current_klass);
      }
    }
    return from_klass->is_subclass_of(target_klass);
  }

  return false;
}

bool VerificationType::is_reference_assignable_from(
    const VerificationType& from, ClassVerifier* context,
    bool from_field_is_protected, bool* this_is_interface, TRAPS) const {

  if (from.is_null()) {
    // null is assignable to any reference
    return true;
  } else if (is_null()) {
    return false;
  } else if (name() == from.name()) {
    return true;
  } else if (is_object()) {
    // We need check the class hierarchy to check assignability
    if (name() == vmSymbols::java_lang_Object()) {
      // any object or array is assignable to java.lang.Object
      return true;
    }

#if INCLUDE_CDS
    if (CDSConfig::is_dumping_archive()) {
      bool skip_assignability_check = false;
      SystemDictionaryShared::add_verification_constraint(context->current_class(),
              name(), from.name(), from_field_is_protected, from.is_array(),
              from.is_object(), &skip_assignability_check);
      if (skip_assignability_check) {
        // We are not able to resolve name() or from.name(). The actual assignability check
        // will be delayed until runtime.
        return true;
      }
    }
#endif
    return resolve_and_check_assignability(context->current_class(), name(), from.name(),
                                           from_field_is_protected, from.is_array(),
                                           from.is_object(), this_is_interface, THREAD);
  } else if (is_array() && from.is_array()) {
    VerificationType comp_this = get_component(context);
    VerificationType comp_from = from.get_component(context);
    if (!comp_this.is_bogus() && !comp_from.is_bogus()) {
      return comp_this.is_component_assignable_from(comp_from, context,
                                                    from_field_is_protected, THREAD);
    }
  }
  return false;
}

VerificationType VerificationType::get_component(ClassVerifier *context) const {
  assert(is_array() && name()->utf8_length() >= 2, "Must be a valid array");
  SignatureStream ss(name(), false);
  ss.skip_array_prefix(1);
  switch (ss.type()) {
    case T_BOOLEAN: return VerificationType(Boolean);
    case T_BYTE:    return VerificationType(Byte);
    case T_CHAR:    return VerificationType(Char);
    case T_SHORT:   return VerificationType(Short);
    case T_INT:     return VerificationType(Integer);
    case T_LONG:    return VerificationType(Long);
    case T_FLOAT:   return VerificationType(Float);
    case T_DOUBLE:  return VerificationType(Double);
    case T_ARRAY:
    case T_OBJECT: {
      guarantee(ss.is_reference(), "unchecked verifier input?");
      Symbol* component = ss.as_symbol();
      // Create another symbol to save as signature stream unreferences this symbol.
      Symbol* component_copy = context->create_temporary_symbol(component);
      assert(component_copy == component, "symbols don't match");
      return VerificationType::reference_type(component_copy);
   }
   default:
     // Met an invalid type signature, e.g. [X
     return VerificationType::bogus_type();
  }
}

void VerificationType::print_on(outputStream* st) const {
  switch (_u._data) {
    case Bogus:            st->print("top"); break;
    case Category1:        st->print("category1"); break;
    case Category2:        st->print("category2"); break;
    case Category2_2nd:    st->print("category2_2nd"); break;
    case Boolean:          st->print("boolean"); break;
    case Byte:             st->print("byte"); break;
    case Short:            st->print("short"); break;
    case Char:             st->print("char"); break;
    case Integer:          st->print("integer"); break;
    case Float:            st->print("float"); break;
    case Long:             st->print("long"); break;
    case Double:           st->print("double"); break;
    case Long_2nd:         st->print("long_2nd"); break;
    case Double_2nd:       st->print("double_2nd"); break;
    case Null:             st->print("null"); break;
    case ReferenceQuery:   st->print("reference type"); break;
    case Category1Query:   st->print("category1 type"); break;
    case Category2Query:   st->print("category2 type"); break;
    case Category2_2ndQuery: st->print("category2_2nd type"); break;
    default:
      if (is_uninitialized_this()) {
        st->print("uninitializedThis");
      } else if (is_uninitialized()) {
        st->print("uninitialized %d", bci());
      } else {
        if (name() != nullptr) {
          name()->print_value_on(st);
        } else {
          st->print_cr("null");
        }
      }
  }
}
