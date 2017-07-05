/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

class RegisteredProbes;
typedef jlong OpaqueProbes;

class DTraceJSDT : AllStatic {
 private:

  static int pd_activate(void* moduleBaseAddress, jstring module,
      jint providers_count, JVM_DTraceProvider* providers);
  static void pd_dispose(int handle);
  static jboolean pd_is_supported();

 public:

  static OpaqueProbes activate(
      jint version, jstring module_name, jint providers_count,
      JVM_DTraceProvider* providers, TRAPS);
  static jboolean is_probe_enabled(jmethodID method);
  static void dispose(OpaqueProbes handle);
  static jboolean is_supported();
};

class RegisteredProbes : public CHeapObj {
 private:
  nmethod** _nmethods;      // all the probe methods
  size_t    _count;         // number of probe methods
  int       _helper_handle; // DTrace-assigned identifier

 public:
  RegisteredProbes(size_t count) {
    _count = count;
    _nmethods = NEW_C_HEAP_ARRAY(nmethod*, count);
  }

  ~RegisteredProbes() {
    for (size_t i = 0; i < _count; ++i) {
      // Let the sweeper reclaim it
      _nmethods[i]->make_not_entrant();
      _nmethods[i]->method()->clear_code();
    }
    FREE_C_HEAP_ARRAY(nmethod*, _nmethods);
    _nmethods = NULL;
    _count = 0;
  }

  static RegisteredProbes* toRegisteredProbes(OpaqueProbes p) {
    return (RegisteredProbes*)(intptr_t)p;
  }

  static OpaqueProbes toOpaqueProbes(RegisteredProbes* p) {
    return (OpaqueProbes)(intptr_t)p;
  }

  void set_helper_handle(int handle) { _helper_handle = handle; }
  int helper_handle() const { return _helper_handle; }

  nmethod* nmethod_at(size_t i) {
    assert(i >= 0 && i < _count, "bad nmethod index");
    return _nmethods[i];
  }

  void nmethod_at_put(size_t i, nmethod* nm) {
    assert(i >= 0 && i < _count, "bad nmethod index");
    _nmethods[i] = nm;
  }
};
