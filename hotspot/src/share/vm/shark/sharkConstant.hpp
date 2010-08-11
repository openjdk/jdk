/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 Red Hat, Inc.
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

class SharkConstant : public ResourceObj {
 public:
  static SharkConstant* for_ldc(ciBytecodeStream* iter);
  static SharkConstant* for_field(ciBytecodeStream* iter);

 private:
  SharkConstant(ciConstant constant, ciType* type);

 private:
  SharkValue* _value;
  ciObject*   _object;
  ciType*     _type;
  bool        _is_loaded;
  bool        _is_nonzero;
  bool        _is_two_word;

 public:
  bool is_loaded() const {
    return _is_loaded;
  }
  bool is_nonzero() const {
    assert(is_loaded(), "should be");
    return _is_nonzero;
  }
  bool is_two_word() const {
    assert(is_loaded(), "should be");
    return _is_two_word;
  }

 public:
  SharkValue* value(SharkBuilder* builder) {
    assert(is_loaded(), "should be");
    if (_value == NULL) {
      _value = SharkValue::create_generic(
        _type, builder->CreateInlineOop(_object), _is_nonzero);
    }
    return _value;
  }
};
