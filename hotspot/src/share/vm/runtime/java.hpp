/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_JAVA_HPP
#define SHARE_VM_RUNTIME_JAVA_HPP

#include "runtime/os.hpp"

// Register function to be called by before_exit
extern "C" { void register_on_exit_function(void (*func)(void)) ;}

// Execute code before all handles are released and thread is killed; prologue to vm_exit
extern void before_exit(JavaThread * thread);

// Forced VM exit (i.e, internal error or JVM_Exit)
extern void vm_exit(int code);

// Wrapper for ::exit()
extern void vm_direct_exit(int code);

// Shutdown the VM but do not exit the process
extern void vm_shutdown();
// Shutdown the VM and abort the process
extern void vm_abort(bool dump_core=true);

// Trigger any necessary notification of the VM being shutdown
extern void notify_vm_shutdown();

// VM exit if error occurs during initialization of VM
extern void vm_exit_during_initialization(Handle exception);
extern void vm_exit_during_initialization(Symbol* exception_name, const char* message);
extern void vm_exit_during_initialization(const char* error, const char* message = NULL);
extern void vm_shutdown_during_initialization(const char* error, const char* message = NULL);

/**
 * Discovering the JDK_Version during initialization is tricky when the
 * running JDK is less than JDK6.  For JDK6 and greater, a "GetVersion"
 * function exists in libjava.so and we simply call it during the
 * 'initialize()' call to find the version.  For JDKs with version < 6, no
 * such call exists and we have to probe the JDK in order to determine
 * the exact version.  This probing cannot happen during late in
 * the VM initialization process so there's a period of time during
 * initialization when we don't know anything about the JDK version other than
 * that it less than version 6.  This is the "partially initialized" time,
 * when we can answer only certain version queries (such as, is the JDK
 * version greater than 5?  Answer: no).  Once the JDK probing occurs, we
 * know the version and are considered fully initialized.
 */
class JDK_Version VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
  friend class Universe;
  friend void JDK_Version_init();
 private:

  static JDK_Version _current;
  static const char* _runtime_name;
  static const char* _runtime_version;

  // In this class, we promote the minor version of release to be the
  // major version for releases >= 5 in anticipation of the JDK doing the
  // same thing.  For example, we represent "1.5.0" as major version 5 (we
  // drop the leading 1 and use 5 as the 'major').

  uint8_t _major;
  uint8_t _minor;
  uint8_t _micro;
  uint8_t _update;
  uint8_t _special;
  uint8_t _build;

  // If partially initialized, the above fields are invalid and we know
  // that we're less than major version 6.
  bool _partially_initialized;

  bool _thread_park_blocker;
  bool _pending_list_uses_discovered_field;
  bool _post_vm_init_hook_enabled;

  bool is_valid() const {
    return (_major != 0 || _partially_initialized);
  }

  // initializes or partially initializes the _current static field
  static void initialize();

  // Completes initialization for a pre-JDK6 version.
  static void fully_initialize(uint8_t major, uint8_t minor = 0,
                               uint8_t micro = 0, uint8_t update = 0);

 public:

  // Returns true if the the current version has only been partially initialized
  static bool is_partially_initialized() {
    return _current._partially_initialized;
  }

  JDK_Version() : _major(0), _minor(0), _micro(0), _update(0),
                  _special(0), _build(0), _partially_initialized(false),
                  _thread_park_blocker(false), _post_vm_init_hook_enabled(false),
                  _pending_list_uses_discovered_field(false) {}

  JDK_Version(uint8_t major, uint8_t minor = 0, uint8_t micro = 0,
              uint8_t update = 0, uint8_t special = 0, uint8_t build = 0,
              bool thread_park_blocker = false, bool post_vm_init_hook_enabled = false,
              bool pending_list_uses_discovered_field = false) :
      _major(major), _minor(minor), _micro(micro), _update(update),
      _special(special), _build(build), _partially_initialized(false),
      _thread_park_blocker(thread_park_blocker),
      _post_vm_init_hook_enabled(post_vm_init_hook_enabled),
      _pending_list_uses_discovered_field(pending_list_uses_discovered_field) {}

  // Returns the current running JDK version
  static JDK_Version current() { return _current; }

  // Factory methods for convenience
  static JDK_Version jdk(uint8_t m) {
    return JDK_Version(m);
  }

  static JDK_Version jdk_update(uint8_t major, uint8_t update_number) {
    return JDK_Version(major, 0, 0, update_number);
  }

  uint8_t major_version() const          { return _major; }
  uint8_t minor_version() const          { return _minor; }
  uint8_t micro_version() const          { return _micro; }
  uint8_t update_version() const         { return _update; }
  uint8_t special_update_version() const { return _special; }
  uint8_t build_number() const           { return _build; }

  bool supports_thread_park_blocker() const {
    return _thread_park_blocker;
  }
  bool post_vm_init_hook_enabled() const {
    return _post_vm_init_hook_enabled;
  }
  // For compatibility wrt pre-4965777 JDK's
  bool pending_list_uses_discovered_field() const {
    return _pending_list_uses_discovered_field;
  }

  // Performs a full ordering comparison using all fields (update, build, etc.)
  int compare(const JDK_Version& other) const;

  /**
   * Performs comparison using only the major version, returning negative
   * if the major version of 'this' is less than the parameter, 0 if it is
   * equal, and a positive value if it is greater.
   */
  int compare_major(int version) const {
    if (_partially_initialized) {
      if (version >= 6) {
        return -1;
      } else {
        assert(false, "Can't make this comparison during init time");
        return -1; // conservative
      }
    } else {
      return major_version() - version;
    }
  }

  void to_string(char* buffer, size_t buflen) const;

  static const char* runtime_name() {
    return _runtime_name;
  }
  static void set_runtime_name(const char* name) {
    _runtime_name = name;
  }

  static const char* runtime_version() {
    return _runtime_version;
  }
  static void set_runtime_version(const char* version) {
    _runtime_version = version;
  }

  // Convenience methods for queries on the current major/minor version
  static bool is_jdk12x_version() {
    return current().compare_major(2) == 0;
  }

  static bool is_jdk13x_version() {
    return current().compare_major(3) == 0;
  }

  static bool is_jdk14x_version() {
    return current().compare_major(4) == 0;
  }

  static bool is_jdk15x_version() {
    return current().compare_major(5) == 0;
  }

  static bool is_jdk16x_version() {
    return current().compare_major(6) == 0;
  }

  static bool is_jdk17x_version() {
    return current().compare_major(7) == 0;
  }

  static bool is_jdk18x_version() {
    return current().compare_major(8) == 0;
  }

  static bool is_gte_jdk13x_version() {
    return current().compare_major(3) >= 0;
  }

  static bool is_gte_jdk14x_version() {
    return current().compare_major(4) >= 0;
  }

  static bool is_gte_jdk15x_version() {
    return current().compare_major(5) >= 0;
  }

  static bool is_gte_jdk16x_version() {
    return current().compare_major(6) >= 0;
  }

  static bool is_gte_jdk17x_version() {
    return current().compare_major(7) >= 0;
  }

  static bool is_gte_jdk18x_version() {
    return current().compare_major(8) >= 0;
  }
};

#endif // SHARE_VM_RUNTIME_JAVA_HPP
