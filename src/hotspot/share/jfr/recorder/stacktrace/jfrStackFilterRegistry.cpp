/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/stacktrace/jfrStackFilter.hpp"
#include "jfr/recorder/stacktrace/jfrStackFilterRegistry.hpp"
#include "logging/log.hpp"

static const intptr_t STACK_FILTER_ELEMENTS_SIZE = 4096;
static const intptr_t STACK_FILTER_ERROR_CODE = -1;
static const JfrStackFilter* _elements[STACK_FILTER_ELEMENTS_SIZE];
static intptr_t _free_list[STACK_FILTER_ELEMENTS_SIZE];
static intptr_t _index = 0;
static intptr_t _free_list_index = 0;

int64_t JfrStackFilterRegistry::add(jobjectArray classes, jobjectArray methods, JavaThread* jt) {
  intptr_t c_size = 0;
  Symbol** class_names = JfrJavaSupport::symbol_array(classes, jt, &c_size, true);
  assert(class_names != nullptr, "invariant");
  intptr_t m_size = 0;
  Symbol** method_names = JfrJavaSupport::symbol_array(methods, jt, &m_size, true);
  assert(method_names != nullptr, "invariant");
  if (c_size != m_size) {
    FREE_C_HEAP_ARRAY(Symbol*, class_names);
    FREE_C_HEAP_ARRAY(Symbol*, method_names);
    JfrJavaSupport::throw_internal_error("Method array size doesn't match class array size", jt);
    return STACK_FILTER_ERROR_CODE;
  }
  assert(c_size >= 0, "invariant");
  const JfrStackFilter* filter = new JfrStackFilter(class_names, method_names, static_cast<size_t>(c_size));
  return JfrStackFilterRegistry::add(filter);
}

#ifdef ASSERT
static bool range_check(int64_t idx) {
  return idx < STACK_FILTER_ELEMENTS_SIZE && idx >= 0;
}
#endif

int64_t JfrStackFilterRegistry::add(const JfrStackFilter* filter) {
  if (_free_list_index > 0) {
    assert(range_check(_free_list_index), "invariant");
    const intptr_t free_index = _free_list[_free_list_index - 1];
    _elements[free_index] = filter;
    _free_list_index--;
    return free_index;
  }
  if (_index >= STACK_FILTER_ELEMENTS_SIZE - 1) {
    log_warning(jfr)("Maximum number of @StackFrame in use has been reached.");
    return STACK_FILTER_ERROR_CODE;
  }
  assert(range_check(_index), "invariant");
  _elements[_index] = filter;
  return _index++;
}

const JfrStackFilter* JfrStackFilterRegistry::lookup(int64_t id) {
  assert(id >= 0, "invariant");
  assert(range_check(id), "invariant");
  return _elements[id];
}

void JfrStackFilterRegistry::remove(int64_t index) {
  assert(range_check(index), "invariant");
  delete _elements[index];
  if (_free_list_index < STACK_FILTER_ELEMENTS_SIZE - 1) {
    assert(range_check(_free_list_index), "invariant");
    _free_list[_free_list_index++] = index;
  }
}
