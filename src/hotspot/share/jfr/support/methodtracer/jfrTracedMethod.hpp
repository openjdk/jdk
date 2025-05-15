/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRTRACEDMETHOD_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRTRACEDMETHOD_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class InstanceKlass;
class Method;

//
// Method that has been filtered out for tracing, may or may not yet be
// instrumented.
//
class JfrTracedMethod {
 private:
  traceid       _id;
  const Symbol* _name;
  const Symbol* _signature;
  const Method* _method;
  int32_t       _modification;
  int32_t       _methods_array_index;

 public:
  JfrTracedMethod(const InstanceKlass* ik, const Method* method, int32_t modification, int32_t methods_array_index) :
      _id(method_id(ik, method)), _name(method->name()), _signature(method->signature()), _method(nullptr), _modification(modification), _methods_array_index(methods_array_index) {
    assert(_method == nullptr, "invariant");
  }

  JfrTracedMethod() :
      _id(0), _name(nullptr), _signature(nullptr), _method(nullptr), _modification(0), _methods_array_index(0) {
  }

  traceid id() const {
    return _id;
  }

  const Symbol* name() const {
    return _name;
  }

  const Symbol* signature() const {
    return _signature;
  }

  const Method* method() const {
    assert(_method != nullptr, "Traced method must be set before it can be used");
    return _method;
  }

  void set_method_from_klass(const InstanceKlass* ik) {
    Array<Method*>* ik_methods = ik->methods();
    assert(ik_methods != nullptr, "invariant");
    _method = ik_methods->at(_methods_array_index);
  }

  int32_t modification() const {
    return _modification;
  }

  static traceid method_id(const InstanceKlass* ik, const Method* m) {
    assert(ik != nullptr, "invariant");
    assert(m != nullptr, "invariant");
    return METHOD_ID(ik, m);
  }
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRTRACEDMETHOD_HPP
