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
#include "prims/jvmtiAgent.hpp"
#include "prims/jvmtiAgentLibrary.hpp"
#include "prims/jvmtiAgentLibraryManager.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/threads.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

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

using JvmtiAgentLibraryList = GrowableArrayCHeap<JvmtiAgentLibrary*, mtServiceability>;
static JvmtiAgentLibraryList* _libraries = nullptr;

int JvmtiAgentLibraryManager::lookup(const void* handle, const char* static_name) {
  DEBUG_ONLY(assert_exclusivity();)
  assert(_libraries != nullptr, "invariant");
  for (int i = 0; i < _libraries->length(); ++i) {
    const JvmtiAgentLibrary* const library = _libraries->at(i);
    if (library->match(handle, static_name)) {
      return i;
    }
  }
  return -1;
}

JvmtiAgentLibrary* JvmtiAgentLibraryManager::intern(const void* handle,
                                                    const char* static_name,
                                                    bool* inserted) {
  assert(handle != nullptr, "invariant");
  ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
  if (_libraries == nullptr) {
    _libraries = new JvmtiAgentLibraryList(16);
  }
  const int location = lookup(handle, static_name);
  if (inserted != nullptr) {
    *inserted = location == -1;
  }
  if (location != -1) {
    return _libraries->at(location);
  }
  JvmtiAgentLibrary* const library = new JvmtiAgentLibrary(handle, static_name);
  _libraries->append(library);
  return library;
}

static char* normalize_static_name(const char* agent_name, bool is_absolute_path) {
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

static inline void deallocate(char* buffer) {
  FREE_C_HEAP_ARRAY(buffer);
}

static bool has_static_entry_point(const char* agent_name, bool is_absolute_path, const char* entry_point_name) {
  assert(agent_name != nullptr, "invariant");
  assert(entry_point_name != nullptr, "invariant");
  char* const normalized_name = normalize_static_name(agent_name, is_absolute_path);
  if (normalized_name == nullptr) {
    return false;
  }
  stringStream name;
  name.print_raw(entry_point_name);
  name.put('_');
  name.print_raw(normalized_name);
  deallocate(normalized_name);
  return os::dll_lookup(os::get_default_process_handle(), name.base()) != nullptr;
}

static bool is_static_library(const char* name, bool is_absolute_path, const char* startup_symbol) {
  bool result = has_static_entry_point(name, is_absolute_path, startup_symbol);
  if (!result && strcmp(startup_symbol, "JVM_OnLoad") == 0) {
    result = has_static_entry_point(name, is_absolute_path, "Agent_OnLoad");
  }
  return result;
}

static bool resolve_to_platform_specific_absolute_path_in_std_dll_dir(const char* os_lib_name, char* buffer) {
  assert(os_lib_name != nullptr, "invariant");
  assert(buffer != nullptr, "invariant");
  return os::dll_locate_lib(buffer, JVM_MAXPATHLEN, Arguments::get_dll_dir(), os_lib_name);
}

static bool build_platform_specific_library_name(const char* os_lib_name, char* buffer) {
  assert(os_lib_name != nullptr, "invariant");
  assert(buffer != nullptr, "invariant");
  return os::dll_build_name(buffer, JVM_MAXPATHLEN, os_lib_name);
}

static char* prepare_library_load_name(const char* name, bool is_absolute_path, bool* in_std_dll_dir) {
  assert(name != nullptr, "invariant");
  if (in_std_dll_dir != nullptr) {
    *in_std_dll_dir = false;
  }
  char* const buffer = NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtServiceability);
  if (is_absolute_path) {
    const size_t length = strlen(name);
    if (length >= JVM_MAXPATHLEN) {
      log_error(jvmti)("Agent library path is too long: %zu bytes (maximum %zu bytes)",
                       length, static_cast<size_t>(JVM_MAXPATHLEN - 1));
      deallocate(buffer);
      return nullptr;
    }
    memcpy(buffer, name, length + 1);
    return buffer;
  }
  if (resolve_to_platform_specific_absolute_path_in_std_dll_dir(name, buffer)) {
    if (in_std_dll_dir != nullptr) {
      *in_std_dll_dir = true;
    }
    return buffer;
  }
  if (build_platform_specific_library_name(name, buffer)) {
    return buffer;
  }
  deallocate(buffer);
  return nullptr;
}

static const char* not_found_error_msg = "Could not find agent library ";
static const char* missing_module_error_msg = "\nModule java.instrument may be missing from runtime image.";

static void exit_on_load_error(const char* name, const char* detail, const char* error = "") {
  const char* const message = strcmp(name, "instrument") == 0 ? missing_module_error_msg : "";
  const size_t len = strlen(not_found_error_msg) + strlen(name) + strlen(detail) + strlen(error) + strlen(message) + 1;
  char* const buf = NEW_C_HEAP_ARRAY(char, len, mtServiceability);
  jio_snprintf(buf, len, "%s%s%s%s%s", not_found_error_msg, name, detail, error, message);
  vm_exit_during_initialization(buf, nullptr);
}

static void on_load_error(const char* name, bool is_absolute_path, bool vm_exit_on_error,
                          const char* error, outputStream* st = nullptr) {
  if (vm_exit_on_error) {
    const char* const detail = is_absolute_path ?
      " in absolute path, with error: " : " on the library path, with error: ";
    exit_on_load_error(name, detail, error);
  }
  st->print_cr("%s was not loaded.", name);
  if (*error != '\0') {
    st->print_cr("%s", error);
  }
}

static const void* load_library(const char* name, bool is_absolute_path, bool is_static, bool vm_exit_on_error, outputStream* st) {
  assert(name != nullptr, "invariant");
  assert(vm_exit_on_error || st != nullptr, "invariant");
  if (is_static) {
    return os::get_default_process_handle();
  }
  bool in_std_dll_dir = false;
  char* const path = prepare_library_load_name(name, is_absolute_path, &in_std_dll_dir);
  if (path == nullptr) {
    if (vm_exit_on_error) {
      exit_on_load_error(name, ", unable to resolve library path");
    }
    st->print_cr("Unable to resolve agent library path: path is invalid or too long.");
    return nullptr;
  }
  char ebuf[1024] = {0};
  const void* handle = os::dll_load(path, &ebuf[0], sizeof ebuf);
  if (handle == nullptr && in_std_dll_dir) {
    // On failure loading a file from the JDK directory,
    // retain the normal library-search fallback for relative names.
    if (build_platform_specific_library_name(name, path)) {
      handle = os::dll_load(path, &ebuf[0], sizeof ebuf);
    }
  }
  deallocate(path);
  if (handle == nullptr) {
    on_load_error(name, is_absolute_path, vm_exit_on_error, &ebuf[0], st);
    return nullptr;
  }
  return handle;
}

JvmtiAgentLibrary* JvmtiAgentLibraryManager::load(const JvmtiAgent* agent,
                                                  bool vm_exit_on_error,
                                                  outputStream* st,
                                                  bool* initial_load) {
  assert(agent != nullptr, "invariant");
  const char* const name = agent->name();
  const bool is_absolute_path = agent->is_absolute_path();
  const char* const startup_symbol = agent->is_dynamic() ? "Agent_OnAttach" : agent->is_xrun() ? "JVM_OnLoad" : "Agent_OnLoad";
  assert(name != nullptr, "invariant");
  assert(startup_symbol != nullptr, "invariant");
  const bool is_static = is_static_library(name, is_absolute_path, startup_symbol);
  const void* const handle = load_library(name, is_absolute_path, is_static, vm_exit_on_error, st);
  if (handle == nullptr) {
    return nullptr;
  }
  char* const static_name = is_static ? normalize_static_name(name, is_absolute_path) : nullptr;
  assert(!is_static || static_name != nullptr, "invariant");
  JvmtiAgentLibrary* const library = intern(handle, static_name, initial_load);
  FREE_C_HEAP_ARRAY(static_name);
  return library;
}

void JvmtiAgentLibraryManager::register_for_native_lookup(JvmtiAgentLibrary* library) {
  assert(library != nullptr, "invariant");
  DEBUG_ONLY(assert_exclusivity();)
  assert(library->_unload_registered, "invariant");
  assert(_libraries != nullptr, "invariant");
  if (!library->_native_lookup_registered) {
    const int location = _libraries->find(library);
    assert(location != -1, "library must have been added on intern");
    // This move preserves the native lookup order.
    _libraries->remove_at(location);
    _libraries->append(library);
    library->_native_lookup_registered = true;
  }
}

using JvmtiConstLibraryList = GrowableArrayCHeap<const JvmtiAgentLibrary*, mtServiceability>;

address JvmtiAgentLibraryManager::lookup_native_entry(const char* name) {
  assert(name != nullptr, "invariant");
  JvmtiConstLibraryList snapshot;

  // Static agent libraries have distinct entry points but share a handle.
  // Therefore, for native lookup, one static library representative suffices.
  const auto contains_handle = [&snapshot](const JvmtiAgentLibrary* library) {
    for (int index = 0; index < snapshot.length(); ++index) {
      if (snapshot.at(index)->shares_handle(library)) {
        return true;
      }
    }
    return false;
  };

  {
    ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
    if (_libraries != nullptr) {
      for (int index = 0; index < _libraries->length(); ++index) {
        const JvmtiAgentLibrary* const library = _libraries->at(index);
        if (library->_native_lookup_registered && !contains_handle(library)) {
          snapshot.append(library);
        }
      }
    }
  }

  for (int index = 0; index < snapshot.length(); ++index) {
    address const entry = (address)snapshot.at(index)->resolve(name);
    if (entry != nullptr) {
      return entry;
    }
  }
  return nullptr;
}

void JvmtiAgentLibraryManager::register_for_unload_notification(JvmtiAgentLibrary* library) {
  assert(library != nullptr, "invariant");
  ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
  if (!library->_unload_registered) {
    library->_unload_registered = true;
  }
}

using JvmtiVoidList = GrowableArrayCHeap<void*, mtServiceability>;

// Resolve all libraries in the snapshot, in-place, to
// their corresponding Agent_OnUnload entry points.
static void resolve_unload_entry_points(JvmtiVoidList* snapshot) {
  assert(snapshot != nullptr, "invariant");
  assert(snapshot->is_nonempty(), "invariant");
  for (int i = 0; i < snapshot->length(); ++i) {
    const JvmtiAgentLibrary* library = static_cast<const JvmtiAgentLibrary*>(snapshot->at(i));
    assert(library != nullptr, "invariant");
    // JvmtiAgentLibrary -> Agent_OnUnload entry point
    snapshot->at_put(i, library->resolve_entry_point("Agent_OnUnload"));
  }
}

JvmtiVoidList* JvmtiAgentLibraryManager::Agent_OnUnload_entry_points(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  JvmtiVoidList* snapshot;

  // Take a snapshot of all libraries registered for unload, if any.
  {
    ConditionalMutexLocker lock(jt, JvmtiThreadState_lock, is_not_vm_bootstrap());
    if (_libraries == nullptr || _libraries->is_empty()) {
      return nullptr;
    }
    snapshot = new GrowableArrayCHeap<void*, mtServiceability>(_libraries->length());
    for (int index = 0; index < _libraries->length(); ++index) {
      JvmtiAgentLibrary* const library = _libraries->at(index);
      if (library->_unload_registered) {
        snapshot->append(library);
      }
    }
  }

  assert(snapshot != nullptr, "invariant");
  if (snapshot->is_nonempty()) {
    resolve_unload_entry_points(snapshot);
  }
  return snapshot;
}
