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
#include "oops/method.hpp"
#include "oops/symbol.hpp"

#include "jfr/recorder/stacktrace/jfrStackFilter.hpp"

JfrStackFilter::JfrStackFilter(Symbol** class_names, Symbol** method_names, size_t count)
  : _count(count),
    _class_names(class_names),
    _method_names(method_names) {
}

bool JfrStackFilter::match(const Method* method) {
  for (size_t i = 0; i < _count; i++) {
    Symbol* m = _method_names[i];
    if (m == nullptr || m == method->name()) {
      Symbol* c = _class_names[i];
      if (c == nullptr || c == method->klass_name()) {
        return true;
      }
    }
  }
  return false;
}

JfrStackFilter::~JfrStackFilter() {
  for (size_t i = 0; i < _count; i++) {
    Symbol* m = _method_names[i];
    if (m != nullptr) {
      m->decrement_refcount();
    }
    Symbol* s = _class_names[i];
    if (s != nullptr) {
      s->decrement_refcount();
    }
   }
  FREE_C_HEAP_ARRAY(Symbol*, _method_names);
  FREE_C_HEAP_ARRAY(Symbol*, _class_names);
}
