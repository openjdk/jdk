/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm.h"
#include "logging/log.hpp"
#include "prims/jvmtiAgentLibrary.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/threads.hpp"

static inline bool is_vm_bootstrap() {
  return Threads::number_of_threads() == 0;
}

static inline bool is_not_vm_bootstrap() {
  return !is_vm_bootstrap();
}

#ifdef ASSERT
static void assert_exclusivity() {
  assert(is_vm_bootstrap() || JvmtiThreadState_lock->owned_by_self(), "invariant");
}
#endif

static inline void deallocate(char* buffer) {
  FREE_C_HEAP_ARRAY(buffer);
}

class JvmtiLibraryBuffer : StackObj {
 private:
  char* _buffer;
  NONCOPYABLE(JvmtiLibraryBuffer);
 public:
  JvmtiLibraryBuffer() : _buffer(NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtServiceability)) {}
  JvmtiLibraryBuffer(char* buffer) : _buffer(buffer) {}
  ~JvmtiLibraryBuffer() { deallocate(_buffer); }
  char* buffer() { return _buffer; }
  const char* buffer() const { return _buffer; }
  bool is_notnull() const { return _buffer != nullptr; }
  void reset(char* buffer) {
    assert(buffer != _buffer, "invariant");
    deallocate(_buffer);
    _buffer = buffer;
  }
};

JvmtiAgentLibrary::JvmtiAgentLibrary(const void* handle, const char* static_name) :
  _handle(handle),
  _static_name(static_name != nullptr ? os::strdup_check_oom(static_name, mtServiceability) : nullptr),
  _path(nullptr),
  _native_lookup_registered(false),
  _unload_registered(false) {
  assert(handle != nullptr, "invariant");
  DEBUG_ONLY(assert_exclusivity();)
}

bool JvmtiAgentLibrary::is_static() const {
  return _static_name != nullptr;
}

bool JvmtiAgentLibrary::match(const void* handle, const char* static_name) const {
  if (static_name != nullptr) {
    // static agents disambiguate on name
    return _static_name != nullptr && strcmp(static_name, _static_name) == 0;
  }
  return _static_name == nullptr && handle == _handle;
}

bool JvmtiAgentLibrary::shares_handle(const JvmtiAgentLibrary* rhs) const {
  assert(rhs != nullptr, "invariant");
  return rhs->_handle == _handle;
}

static char* build_static_agent_function_name(const char* symbol, const char* normalized_name) {
  assert(symbol != nullptr, "invariant");
  assert(normalized_name != nullptr, "invariant");
  const size_t symbol_length = strlen(symbol);
  const size_t name_length = strlen(normalized_name);
  const size_t length = symbol_length + 1 + name_length + 1;
  char* const result = NEW_C_HEAP_ARRAY(char, length, mtServiceability);
  memcpy(result, symbol, symbol_length);
  result[symbol_length] = '_';
  memcpy(result + symbol_length + 1, normalized_name, name_length + 1);
  return result;
}

void* JvmtiAgentLibrary::resolve(const char* name) const {
  assert(name != nullptr, "invariant");
  return os::dll_lookup(const_cast<void*>(_handle), name);
}

void* JvmtiAgentLibrary::resolve_entry_point(const char* entry_point_name) const {
  assert(entry_point_name != nullptr, "invariant");
  if (!is_static()) {
    return resolve(entry_point_name);
  }
  JvmtiLibraryBuffer static_entry_point_name(build_static_agent_function_name(entry_point_name, _static_name));
  return resolve(static_entry_point_name.buffer());
}

static const char* library_path_error_msg = "Unable to resolve loaded agent library path";

const char* JvmtiAgentLibrary::path() const {
  return AtomicAccess::load_acquire(&_path);
}

bool JvmtiAgentLibrary::initialize_path(void* entry_point) {
  assert(entry_point != nullptr, "invariant");
  if (path() != nullptr) {
    return true;
  }
  address pc = static_cast<address>(entry_point);
#ifdef HAVE_FUNCTION_DESCRIPTORS
  pc = static_cast<address>(os::resolve_function_descriptor(pc));
#endif
  JvmtiLibraryBuffer buffer;
  if (pc == nullptr || !os::dll_address_to_library_name(pc, buffer.buffer(), JVM_MAXPATHLEN, nullptr) || buffer.buffer()[0] == '\0') {
    log_error(jvmti)("%s", library_path_error_msg);
    return false;
  }
  ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
  if (path() == nullptr) {
    const char* const resolved_path = os::strdup_check_oom(buffer.buffer(), mtServiceability);
    AtomicAccess::release_store(&_path, resolved_path);
  }
  return true;
}
