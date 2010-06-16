/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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

// ciArray
//
// This class represents an arrayOop in the HotSpot virtual
// machine.
class ciArray : public ciObject {
private:
  int _length;

protected:
  ciArray(    arrayHandle h_a) : ciObject(h_a), _length(h_a()->length()) {}
  ciArray( objArrayHandle h_a) : ciObject(h_a), _length(h_a()->length()) {}
  ciArray(typeArrayHandle h_a) : ciObject(h_a), _length(h_a()->length()) {}

  ciArray(ciKlass* klass, int len) : ciObject(klass), _length(len) {}

  arrayOop get_arrayOop() { return (arrayOop)get_oop(); }

  const char* type_string() { return "ciArray"; }

  void print_impl(outputStream* st);

public:
  int length() { return _length; }

  // What kind of ciObject is this?
  bool is_array()        { return true; }
  bool is_java_object()  { return true; }
};
