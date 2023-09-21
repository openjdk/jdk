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
#include "jfr/recorder/stacktrace/jfrStackFilterRegistry.hpp"
#include "logging/log.hpp"

static JfrStackFilterRegistry* _instance = nullptr;

JfrStackFilterRegistry* JfrStackFilterRegistry::create() {
  assert(_instance == nullptr, "invariant");
  _instance = new JfrStackFilterRegistry();
  return _instance;
}

JfrStackFilterRegistry* JfrStackFilterRegistry::instance() {
  return _instance;
}

void JfrStackFilterRegistry::destroy() {
   delete _instance;
   _instance = nullptr;
}

JfrStackFilterRegistry::JfrStackFilterRegistry()
  :_index(0), _free_list_index(0) {
  memset(_elements, 0, sizeof(_elements));
  memset(_free_list, 0, sizeof(_free_list));
}

size_t JfrStackFilterRegistry::add(JfrStackFilter* frame) {
  if (_free_list_index > 0) {
    size_t index = _free_list[_free_list_index - 1];
    _elements[index] = frame;
    _free_list_index--;
    return index;
  }
  if (_index >= SIZE - 1) {
    log_warning(jfr)("Maximum number of @StackFrame in use has been reached.");
    return SIZE_MAX;
  }
  _elements[_index] = frame;
  return _index++;
}

JfrStackFilter* JfrStackFilterRegistry::lookup(size_t id) {
  return _elements[id];
}

void JfrStackFilterRegistry::remove(size_t index) {
  delete _elements[index];
  if (_free_list_index < SIZE - 1) {
    _free_list[_free_list_index++] = index;
  }
}

JfrStackFilterRegistry::~JfrStackFilterRegistry() {
  for (size_t i = 0; i < SIZE; i++) {
    delete _elements[i];
  }
}
