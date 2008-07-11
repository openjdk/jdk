/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

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
extern void vm_exit_during_initialization(symbolHandle exception_name, const char* message);
extern void vm_exit_during_initialization(const char* error, const char* message = NULL);
extern void vm_shutdown_during_initialization(const char* error, const char* message = NULL);

class JDK_Version : AllStatic {
  friend class VMStructs;
 private:
  static jdk_version_info _version_info;
  static bool             _pre_jdk16_version;
  static int              _jdk_version;  // JDK version number representing the release
                                         //  i.e. n in 1.n.x (= jdk_minor_version())

 public:
  static void initialize();
  static int  jdk_major_version() { return JDK_VERSION_MAJOR(_version_info.jdk_version); }
  static int  jdk_minor_version() { return JDK_VERSION_MINOR(_version_info.jdk_version); }
  static int  jdk_micro_version() { return JDK_VERSION_MICRO(_version_info.jdk_version); }
  static int  jdk_build_number()  { return JDK_VERSION_BUILD(_version_info.jdk_version); }

  static bool is_pre_jdk16_version()        { return _pre_jdk16_version; }
  static bool is_jdk12x_version()           { assert(is_jdk_version_initialized(), "must have been initialized"); return _jdk_version == 2; }
  static bool is_jdk13x_version()           { assert(is_jdk_version_initialized(), "must have been initialized"); return _jdk_version == 3; }
  static bool is_jdk14x_version()           { assert(is_jdk_version_initialized(), "must have been initialized"); return _jdk_version == 4; }
  static bool is_jdk15x_version()           { assert(is_jdk_version_initialized(), "must have been initialized"); return _jdk_version == 5; }

  static bool is_jdk16x_version() {
    if (is_jdk_version_initialized()) {
      return _jdk_version == 6;
    } else {
      assert(is_pre_jdk16_version(), "must have been initialized");
      return false;
    }
  }

  static bool is_jdk17x_version() {
    if (is_jdk_version_initialized()) {
      return _jdk_version == 7;
    } else {
      assert(is_pre_jdk16_version(), "must have been initialized");
      return false;
    }
  }

  static bool supports_thread_park_blocker() { return _version_info.thread_park_blocker; }

  static bool is_gte_jdk14x_version() {
    // Keep the semantics of this that the version number is >= 1.4
    assert(is_jdk_version_initialized(), "Not initialized");
    return _jdk_version >= 4;
  }
  static bool is_gte_jdk15x_version() {
    // Keep the semantics of this that the version number is >= 1.5
    assert(is_jdk_version_initialized(), "Not initialized");
    return _jdk_version >= 5;
  }
  static bool is_gte_jdk16x_version() {
    // Keep the semantics of this that the version number is >= 1.6
    if (is_jdk_version_initialized()) {
      return _jdk_version >= 6;
    } else {
      assert(is_pre_jdk16_version(), "Not initialized");
      return false;
    }
  }

  static bool is_gte_jdk17x_version() {
    // Keep the semantics of this that the version number is >= 1.7
    if (is_jdk_version_initialized()) {
      return _jdk_version >= 7;
    } else {
      assert(is_pre_jdk16_version(), "Not initialized");
      return false;
    }
  }

  static bool is_jdk_version_initialized() {
    return _jdk_version > 0;
  }

  // These methods are defined to deal with pre JDK 1.6 versions
  static void set_jdk12x_version() {
    assert(_pre_jdk16_version && !is_jdk_version_initialized(), "must not initialize");
    _jdk_version = 2;
    _version_info.jdk_version = (1 << 24) | (2 << 16);
  }
  static void set_jdk13x_version() {
    assert(_pre_jdk16_version && !is_jdk_version_initialized(), "must not initialize");
    _jdk_version = 3;
    _version_info.jdk_version = (1 << 24) | (3 << 16);
  }
  static void set_jdk14x_version() {
    assert(_pre_jdk16_version && !is_jdk_version_initialized(), "must not initialize");
    _jdk_version = 4;
    _version_info.jdk_version = (1 << 24) | (4 << 16);
  }
  static void set_jdk15x_version() {
    assert(_pre_jdk16_version && !is_jdk_version_initialized(), "must not initialize");
    _jdk_version = 5;
    _version_info.jdk_version = (1 << 24) | (5 << 16);
  }
};
