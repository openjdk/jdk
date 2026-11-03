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
#include "prims/jvmtiResolver.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/threads.hpp"
#include "utilities/growableArray.hpp"

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

class JvmtiResolverBufferRAII : StackObj {
 private:
  char* _buffer;
  NONCOPYABLE(JvmtiResolverBufferRAII);
 public:
  JvmtiResolverBufferRAII(char* buffer) : _buffer(buffer) {}
  ~JvmtiResolverBufferRAII() { deallocate(_buffer); }
  char* buffer() { return _buffer; }
  const char* buffer() const { return _buffer; }
  bool is_notnull() const { return _buffer != nullptr; }
  void reset(char* buffer) {
    assert(buffer != _buffer, "invariant");
    deallocate(_buffer);
    _buffer = buffer;
  }
};

static char* normalize(const char* agent_name, bool is_absolute_path) {
  assert(agent_name != nullptr, "invariant");
  size_t length = strlen(agent_name);
  if (is_absolute_path) {
    const char* separator = strrchr(agent_name, *os::file_separator());
    if (separator != nullptr) {
      agent_name = separator + 1;
    }
#ifdef _WINDOWS
    else {
      // Need to check for drive prefix e.g. C:L.dll
      const char* colon = strchr(agent_name, ':');
      if (colon != nullptr) {
        agent_name = colon + 1;
      }
    }
#endif
    const size_t prefix_length = strlen(JNI_LIB_PREFIX);
    const size_t suffix_length = strlen(JNI_LIB_SUFFIX);
    length = strlen(agent_name);
    if (length <= prefix_length + suffix_length) {
      return nullptr;
    }
    agent_name += prefix_length;
    length -= prefix_length + suffix_length;
  }
  char* const result = NEW_C_HEAP_ARRAY(char, length + 1, mtServiceability);
  memcpy(result, agent_name, length);
  result[length] = '\0';
  return result;
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

JvmtiResolver::JvmtiResolver(const void* handle, const char* static_name) :
  _handle(handle),
  _static_name(static_name != nullptr ? os::strdup_check_oom(static_name, mtServiceability) : nullptr),
  _native_lookup_registered(false),
  _unload_registered(false) {
  assert(handle != nullptr, "invariant");
  DEBUG_ONLY(assert_exclusivity();)
}

bool JvmtiResolver::is_static() const {
  return _static_name != nullptr;
}

void* JvmtiResolver::resolve(const char* name) const {
  assert(name != nullptr, "invariant");
  return os::dll_lookup(const_cast<void*>(_handle), name);
}

void* JvmtiResolver::resolve_entry_point(const char* entry_point_name) const {
  assert(entry_point_name != nullptr, "invariant");
  if (!is_static()) {
    return resolve(entry_point_name);
  }
  JvmtiResolverBufferRAII static_entry_point_name(build_static_agent_function_name(entry_point_name, _static_name));
  return resolve(static_entry_point_name.buffer());
}

bool JvmtiResolver::has_static_entry_point(const char* agent_name,
                                           bool is_absolute_path,
                                           const char* entry_point_name) {
  assert(agent_name != nullptr, "invariant");
  assert(entry_point_name != nullptr, "invariant");
  JvmtiResolverBufferRAII name(normalize(agent_name, is_absolute_path));
  if (!name.is_notnull()) {
    return false;
  }
  name.reset(build_static_agent_function_name(entry_point_name, name.buffer()));
  assert(name.is_notnull(), "invariant");
  return os::dll_lookup(os::get_default_process_handle(), name.buffer()) != nullptr;
}

using JvmtiResolverList = GrowableArrayCHeap<JvmtiResolver*, mtServiceability>;
static JvmtiResolverList* _resolvers = nullptr;

bool JvmtiResolver::match(const void* handle, const char* static_name) const {
  if (static_name != nullptr) {
    // static agents disambiguate on name
    return _static_name != nullptr && strcmp(static_name, _static_name) == 0;
  }
  return _static_name == nullptr && handle == _handle;
}

int JvmtiResolver::lookup(const void* handle, const char* static_name) {
  DEBUG_ONLY(assert_exclusivity();)
  assert(_resolvers != nullptr, "invariant");
  for (int i = 0; i < _resolvers->length(); ++i) {
    const JvmtiResolver* const resolver = _resolvers->at(i);
    if (resolver->match(handle, static_name)) {
      return i;
    }
  }
  return -1;
}

JvmtiResolver* JvmtiResolver::intern(const void* handle,
                                     const char* agent_name,
                                     bool is_absolute_path,
                                     bool is_static,
                                     bool* inserted) {
  assert(handle != nullptr, "invariant");
  JvmtiResolverBufferRAII static_name(is_static ? normalize(agent_name, is_absolute_path) : nullptr);
  assert(!is_static || static_name.is_notnull(), "invariant");
  // Protect lazy initialization, lookup, and insertion as one operation.
  ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
  if (_resolvers == nullptr) {
    _resolvers = new JvmtiResolverList(16);
  }
  const int location = lookup(handle, static_name.buffer());
  if (inserted != nullptr) {
    *inserted = location == -1;
  }
  if (location != -1) {
    return _resolvers->at(location);
  }
  JvmtiResolver* const resolver = new JvmtiResolver(handle, static_name.buffer());
  _resolvers->append(resolver);
  return resolver;
}

bool JvmtiResolver::shares_handle(const JvmtiResolver* rhs) const {
  assert(rhs != nullptr, "invariant");
  return rhs->_handle == _handle;
}

bool JvmtiResolver::is_registered_for_native_lookup() const {
  DEBUG_ONLY(assert_exclusivity();)
  return _native_lookup_registered;
}

void JvmtiResolver::register_for_native_resolution() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(_unload_registered, "invariant");
  assert(_resolvers != nullptr, "invariant");
  if (!_native_lookup_registered) {
    const int location = _resolvers->find(this);
    assert(location != -1, "resolver must have been added on intern");
    // This move preserves the native lookup order.
    _resolvers->remove_at(location);
    _resolvers->append(this);
    _native_lookup_registered = true;
  }
}

using JvmtiConstResolverList = GrowableArrayCHeap<const JvmtiResolver*, mtServiceability>;

class JvmtiNativeLookupIterator : StackObj {
  NONCOPYABLE(JvmtiNativeLookupIterator);
  JvmtiConstResolverList _snapshot;
  mutable int _next;

  // Static agent resolvers have distinct entry points but share a handle.
  // Therefore, for native lookup, one static resolver representative suffices.
  bool not_contains(const JvmtiResolver* resolver) const {
    for (int i = 0; i < _snapshot.length(); ++i) {
      if (_snapshot.at(i)->shares_handle(resolver)) {
        return false;
      }
    }
    return true;
  }

 public:
  JvmtiNativeLookupIterator() : _next(0) {
    ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
    if (_resolvers != nullptr) {
      for (int i = 0; i < _resolvers->length(); ++i) {
        const JvmtiResolver* const resolver = _resolvers->at(i);
        if (resolver->is_registered_for_native_lookup() && not_contains(resolver)) {
          _snapshot.append(resolver);
        }
      }
    }
  }

  bool has_next() const {
    return _next < _snapshot.length();
  }

  const JvmtiResolver* next() const {
    assert(has_next(), "invariant");
    return _snapshot.at(_next++);
  }
};

address JvmtiResolver::lookup_native_entry(const char* name) {
  assert(name != nullptr, "invariant");
  const JvmtiNativeLookupIterator iter;
  while (iter.has_next()) {
    address const entry = (address)iter.next()->resolve(name);
    if (entry != nullptr) {
      return entry;
    }
  }
  return nullptr;
}

bool JvmtiResolver::is_registered_for_unload() const {
  DEBUG_ONLY(assert_exclusivity();)
  return _unload_registered;
}

void JvmtiResolver::register_for_unload() {
  ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
  if (!_unload_registered) {
    _unload_registered = true;
  }
}

using JvmtiVoidList = GrowableArrayCHeap<void*, mtServiceability>;

static void collect_unload_resolvers(JvmtiVoidList* snapshot) {
  assert(snapshot != nullptr, "invariant");
  DEBUG_ONLY(assert_exclusivity();)
  assert(_resolvers != nullptr, "invariant");
  for (int i = 0; i < _resolvers->length(); ++i) {
    JvmtiResolver* const resolver = _resolvers->at(i);
    if (resolver->is_registered_for_unload()) {
      snapshot->append(resolver);
    }
  }
}

// Resolve all resolvers in the snapshot, in-place, to
// their corresponding Agent_OnUnload entry points.
static void resolve_unload_entry_points(JvmtiVoidList* snapshot) {
  assert(snapshot != nullptr, "invariant");
  assert(snapshot->is_nonempty(), "invariant");
  for (int i = 0; i < snapshot->length(); ++i) {
    const JvmtiResolver* resolver = static_cast<const JvmtiResolver*>(snapshot->at(i));
    assert(resolver != nullptr, "invariant");
    // JvmtiResolver -> Agent_OnUnload entry point
    snapshot->at_put(i, resolver->resolve_entry_point("Agent_OnUnload"));
  }
}

JvmtiVoidList* JvmtiResolver::Agent_OnUnload_entry_points(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  JvmtiVoidList* snapshot;

  // Take a snapshot of all resolvers registered for unload, if any.
  {
    ConditionalMutexLocker lock(jt, JvmtiThreadState_lock, is_not_vm_bootstrap());
    if (_resolvers == nullptr || _resolvers->is_empty()) {
      return nullptr;
    }
    snapshot = new GrowableArrayCHeap<void*, mtServiceability>(_resolvers->length());
    collect_unload_resolvers(snapshot);
  }

  assert(snapshot != nullptr, "invariant");
  if (snapshot->is_nonempty()) {
    resolve_unload_entry_points(snapshot);
  }
  return snapshot;
}
