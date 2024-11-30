/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/stacktrace/jfrStackFilter.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"

JfrStackFilter::JfrStackFilter(Symbol** class_names, Symbol** method_names, size_t count)
  : _count(count),
    _class_names(class_names),
    _method_names(method_names) {
  assert(_class_names != nullptr, "invariant");
  assert(_method_names != nullptr, "invariant");
}

bool JfrStackFilter::match(const Method* method) const {
  assert(method != nullptr, "Invariant");
  const Symbol* const method_name = method->name();
  const Symbol* const klass_name = method->klass_name();
  for (size_t i = 0; i < _count; i++) {
    const Symbol* m = _method_names[i];
    if (m == nullptr || m == method_name) {
      const Symbol* c = _class_names[i];
      if (c == nullptr || c == klass_name) {
        return true;
      }
    }
  }
  return false;
}

JfrStackFilter::~JfrStackFilter() {
  for (size_t i = 0; i < _count; i++) {
    Symbol::maybe_decrement_refcount(_method_names[i]);
    Symbol::maybe_decrement_refcount(_class_names[i]);
  }
  FREE_C_HEAP_ARRAY(Symbol*, _method_names);
  FREE_C_HEAP_ARRAY(Symbol*, _class_names);
}
