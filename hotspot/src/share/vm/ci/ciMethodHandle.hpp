/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CI_CIMETHODHANDLE_HPP
#define SHARE_VM_CI_CIMETHODHANDLE_HPP

#include "ci/ciInstance.hpp"
#include "prims/methodHandles.hpp"

// ciMethodHandle
//
// The class represents a java.dyn.MethodHandle object.
class ciMethodHandle : public ciInstance {
private:
  ciMethod* _callee;

  // Return an adapter for this MethodHandle.
  ciMethod* get_adapter(bool is_invokedynamic) const;

protected:
  void print_impl(outputStream* st);

public:
  ciMethodHandle(instanceHandle h_i) : ciInstance(h_i) {};

  // What kind of ciObject is this?
  bool is_method_handle() const { return true; }

  ciMethod* callee() const { return _callee; }
  void  set_callee(ciMethod* m) { _callee = m; }

  // Return an adapter for a MethodHandle call.
  ciMethod* get_method_handle_adapter() const {
    return get_adapter(false);
  }

  // Return an adapter for an invokedynamic call.
  ciMethod* get_invokedynamic_adapter() const {
    return get_adapter(true);
  }
};

#endif // SHARE_VM_CI_CIMETHODHANDLE_HPP
