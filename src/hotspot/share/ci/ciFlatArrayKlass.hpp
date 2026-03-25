/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CI_CIFLATARRAYKLASS_HPP
#define SHARE_VM_CI_CIFLATARRAYKLASS_HPP

#include "ci/ciInlineKlass.hpp"
#include "ci/ciObjArrayKlass.hpp"
#include "oops/flatArrayKlass.hpp"

// ciFlatArrayKlass
//
// This class represents a Klass* in the HotSpot virtual machine
// whose Klass part is a FlatArrayKlass.
class ciFlatArrayKlass : public ciObjArrayKlass {
  CI_PACKAGE_ACCESS
  friend class ciEnv;

protected:
  ciFlatArrayKlass(Klass* k) : ciObjArrayKlass(k) {
    assert(k->is_flatArray_klass(), "wrong type");
  }

  const FlatArrayKlass* get_FlatArrayKlass() const {
    return FlatArrayKlass::cast(get_Klass());
  }

  virtual const char* type_string() override { return "ciFlatArrayKlass"; }

public:
  LayoutKind layout_kind() const { return get_FlatArrayKlass()->layout_kind(); }

  int log2_element_size() {
    return Klass::layout_helper_log2_element_size(layout_helper());
  }

  int element_byte_size() { return 1 << log2_element_size(); }

  // What kind of ciObject is this?
  virtual bool is_flat_array_klass() const override { return true; }

  virtual ciKlass* exact_klass() override {
    assert(element_klass()->as_inline_klass()->exact_klass() != nullptr, "must have exact klass");
    return this;
  }
};

#endif // SHARE_VM_CI_CIFLATARRAYKLASS_HPP
