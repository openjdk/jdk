/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

// ciArrayKlass
//
// This class, and its subclasses represent klassOops in the
// HotSpot virtual machine whose Klass part is an arrayKlass.
class ciArrayKlass : public ciKlass {
private:
  jint _dimension;

protected:
  ciArrayKlass(KlassHandle h_k);
  ciArrayKlass(ciSymbol* name, int dimension, ciKlass* klass);

  arrayKlass* get_arrayKlass() {
    return (arrayKlass*)get_Klass();
  }

  const char* type_string() { return "ciArrayKlass"; }

public:
  jint dimension() { return _dimension; }
  ciType* element_type();       // JLS calls this the "component type"
  ciType* base_element_type();  // JLS calls this the "element type"
  bool is_leaf_type();          // No subtypes of this array type.

  ciInstance* component_mirror() {
    // This is a real field in arrayKlass, but we derive it from element_type.
    return element_type()->java_mirror();
  }

  // What kind of vmObject is this?
  bool is_array_klass() { return true; }
  bool is_java_klass()  { return true; }

  static ciArrayKlass* make(ciType* element_type);
};
