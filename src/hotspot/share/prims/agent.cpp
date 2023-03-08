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
 */

#include "precompiled.hpp"
#include "prims/agent.hpp"

#include "jvm_io.h"

static void free_string(const char* str) {
  assert(str != nullptr, "invariant");
  FreeHeap(const_cast<char*>(str));
}

static const char* allocate_copy(const char* str) {
  if (str == nullptr) {
    return nullptr;
  }
  const size_t length = strlen(str);
  char* copy = AllocateHeap(length + 1, mtInternal);
  strncpy(copy, str, length + 1);
  assert(strncmp(copy, str, length + 1) == 0, "invariant");
  return copy;
}

// returns the lhs before '=', parsed_options output param gets the rhs.
static const char* split_options_and_allocate_copy(const char* options, const char** parsed_options) {
  assert(options != nullptr, "invariant");
  assert(parsed_options != nullptr, "invariant");
  const char* const equal_sign = strchr(options, '=');
  const size_t length = strlen(options);
  size_t name_length = length;
  if (equal_sign != nullptr) {
    name_length = equal_sign - options;
    const size_t options_length = length - name_length - 1;
    *parsed_options = allocate_copy(equal_sign + 1);
  } else {
    *parsed_options = nullptr;
    name_length = length;
  }
  char* const name = AllocateHeap(name_length + 1, mtInternal);
  jio_snprintf(name, name_length + 1, "%s", options);
  assert(strncmp(name, options, name_length) == 0, "invariant");
  return name;
}

Agent::Agent(const char* name, const char* options, bool is_absolute_path) :
  _init(),
  _init_time(),
  _next(nullptr),
  _name(allocate_copy(name)),
  _options(allocate_copy(options)),
  _os_lib(nullptr),
  _os_lib_path(nullptr),
  _jplis(nullptr),
  _valid(false),
  _is_absolute_path(is_absolute_path),
  _is_static_lib(false),
  _is_dynamic(false),
  _is_instrument_lib(strcmp(name, "instrument") == 0),
  _is_xrun(false) {}

Agent* Agent::next() const {
  return _next;
}

const char* Agent::name() const {
  return _name;
}

const char* Agent::options() const {
  return _options;
}

bool Agent::is_absolute_path() const {
  return _is_absolute_path;
}

void* Agent::os_lib() const {
  return _os_lib;
}

void Agent::set_os_lib(void* os_lib) {
  _os_lib = os_lib;
}

void Agent::set_os_lib_path(const char* path) {
  assert(path != nullptr, "invariant");
  assert(_os_lib_path == nullptr, "invariant");
  _os_lib_path = allocate_copy(path);
}

const char* Agent::os_lib_path() const {
  return _os_lib_path;
}

bool Agent::is_static_lib() const {
  return _is_static_lib;
}

void Agent::set_static_lib() {
  _is_static_lib = true;
}

bool Agent::is_dynamic() const {
  return _is_dynamic;
}

bool Agent:: is_instrument_lib() const {
  return _is_instrument_lib;
}

bool Agent::is_valid() const {
  return _valid;
}

void Agent::set_valid() {
  _valid = true;
}

const Ticks& Agent::initialization() const {
  return _init;
}

const Tickspan& Agent::initialization_time() const {
  return _init_time;
}

void Agent::set_jplis(const void* jplis) {
  assert(jplis != nullptr, "invaiant");
  assert(is_instrument_lib(), "invariant");
  assert(_jplis == nullptr, "invariant");
  if (_options != nullptr) {
    // For JPLIS agents, update with the java name and options.
    free_string(_name);
    const char* options = _options;
    _name = split_options_and_allocate_copy(options, &_options);
    free_string(options);
  }
  _jplis = jplis;
}

bool Agent::is_jplis() const {
  return _jplis != nullptr;
}

bool Agent::is_jplis(const void* jplis) const {
  assert(jplis != nullptr, "invariant");
  assert(is_instrument_lib(), "invariant");
  return jplis == _jplis;
}

bool Agent::is_timestamped() const {
  return _init.value() != 0;
}

void Agent::timestamp() {
  assert(_init.value() == 0, "invariant");
  _init = Ticks::now();
}

void Agent::initialization_begin() {
  timestamp();
}

void Agent::initialization_end() {
  assert(is_timestamped(), "invariant");
  assert(_init_time.value() == 0, "invariant");
  _init_time = Ticks::now() - initialization();
}
