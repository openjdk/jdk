/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "runtime/jniHandles.hpp"
#include "classfile/symbolTable.hpp"
#include "memory/resourceArea.hpp"

// This function is similar to javaClasses.cpp, it computes the field offset of a (static or instance) field.
// It looks up the name and signature symbols without creating new ones, all the symbols of these classes need to be already loaded.

void compute_offset(int &dest_offset, Klass* klass, const char* name, const char* signature, bool static_field) {
  InstanceKlass* ik = InstanceKlass::cast(klass);
  Symbol* name_symbol = SymbolTable::probe(name, (int)strlen(name));
  Symbol* signature_symbol = SymbolTable::probe(signature, (int)strlen(signature));
  if (name_symbol == NULL || signature_symbol == NULL) {
#ifndef PRODUCT
    ik->print_on(tty);
#endif
    fatal("symbol with name %s and signature %s was not found in symbol table (klass=%s)", name, signature, klass->name()->as_C_string());
  }

  fieldDescriptor fd;
  if (!ik->find_field(name_symbol, signature_symbol, &fd)) {
    ResourceMark rm;
    fatal("Invalid layout of %s at %s", name_symbol->as_C_string(), ik->external_name());
  }
  guarantee(fd.is_static() == static_field, "static/instance mismatch");
  dest_offset = fd.offset();
  assert(dest_offset != 0, "must be valid offset");
}

// This piece of macro magic creates the contents of the jvmci_compute_offsets method that initializes the field indices of all the access classes.

#define START_CLASS(name) { Klass* k = SystemDictionary::name##_klass(); assert(k != NULL, "Could not find class " #name "");

#define END_CLASS }

#define FIELD(klass, name, signature, static_field) compute_offset(klass::_##name##_offset, k, #name, signature, static_field);
#define CHAR_FIELD(klass, name) FIELD(klass, name, "C", false)
#define INT_FIELD(klass, name) FIELD(klass, name, "I", false)
#define BOOLEAN_FIELD(klass, name) FIELD(klass, name, "Z", false)
#define LONG_FIELD(klass, name) FIELD(klass, name, "J", false)
#define FLOAT_FIELD(klass, name) FIELD(klass, name, "F", false)
#define OOP_FIELD(klass, name, signature) FIELD(klass, name, signature, false)
#define STATIC_OOP_FIELD(klass, name, signature) FIELD(klass, name, signature, true)
#define STATIC_INT_FIELD(klass, name) FIELD(klass, name, "I", true)
#define STATIC_BOOLEAN_FIELD(klass, name) FIELD(klass, name, "Z", true)


void JVMCIJavaClasses::compute_offsets() {
  COMPILER_CLASSES_DO(START_CLASS, END_CLASS, CHAR_FIELD, INT_FIELD, BOOLEAN_FIELD, LONG_FIELD, FLOAT_FIELD, OOP_FIELD, OOP_FIELD, OOP_FIELD, STATIC_OOP_FIELD, STATIC_OOP_FIELD, STATIC_INT_FIELD, STATIC_BOOLEAN_FIELD)
}

#define EMPTY0
#define EMPTY1(x)
#define EMPTY2(x,y)
#define FIELD2(klass, name) int klass::_##name##_offset = 0;
#define FIELD3(klass, name, sig) FIELD2(klass, name)

COMPILER_CLASSES_DO(EMPTY1, EMPTY0, FIELD2, FIELD2, FIELD2, FIELD2, FIELD2, FIELD3, FIELD3, FIELD3, FIELD3, FIELD3, FIELD2, FIELD2)





