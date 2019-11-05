/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "runtime/fieldType.hpp"
#include "runtime/signature.hpp"

BasicType FieldType::basic_type(Symbol* signature) {
  return char2type(signature->char_at(0));
}

// Check if it is a valid array signature
bool FieldType::is_valid_array_signature(Symbol* sig) {
  assert(sig->utf8_length() > 1, "this should already have been checked");
  assert(sig->char_at(0) == JVM_SIGNATURE_ARRAY, "this should already have been checked");
  // The first character is already checked
  int i = 1;
  int len = sig->utf8_length();
  // First skip all '['s
  while(i < len - 1 && sig->char_at(i) == JVM_SIGNATURE_ARRAY) i++;

  // Check type
  switch(sig->char_at(i)) {
    case JVM_SIGNATURE_BYTE:
    case JVM_SIGNATURE_CHAR:
    case JVM_SIGNATURE_DOUBLE:
    case JVM_SIGNATURE_FLOAT:
    case JVM_SIGNATURE_INT:
    case JVM_SIGNATURE_LONG:
    case JVM_SIGNATURE_SHORT:
    case JVM_SIGNATURE_BOOLEAN:
      // If it is an array, the type is the last character
      return (i + 1 == len);
    case JVM_SIGNATURE_CLASS:
      // If it is an object, the last character must be a ';'
      return sig->char_at(len - 1) == JVM_SIGNATURE_ENDCLASS;
  }

  return false;
}


BasicType FieldType::get_array_info(Symbol* signature, FieldArrayInfo& fd, TRAPS) {
  assert(basic_type(signature) == T_ARRAY, "must be array");
  int index = 1;
  int dim   = 1;
  while (signature->char_at(index) == JVM_SIGNATURE_ARRAY) {
    index++;
    dim++;
  }
  ResourceMark rm;
  char *element = signature->as_C_string() + index;
  BasicType element_type = char2type(element[0]);
  if (element_type == T_OBJECT) {
    int len = (int)strlen(element);
    assert(element[len-1] == JVM_SIGNATURE_ENDCLASS, "last char should be a semicolon");
    element[len-1] = '\0';        // chop off semicolon
    fd._object_key = SymbolTable::new_symbol(element + 1);
  }
  // Pass dimension back to caller
  fd._dimension = dim;
  return element_type;
}
