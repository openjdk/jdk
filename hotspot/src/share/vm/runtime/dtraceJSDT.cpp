/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_dtraceJSDT.cpp.incl"

#ifdef HAVE_DTRACE_H

jlong DTraceJSDT::activate(
    jint version, jstring module_name, jint providers_count,
    JVM_DTraceProvider* providers, TRAPS) {

  size_t count = 0;
  RegisteredProbes* probes = NULL;

  if (!is_supported()) {
    return 0;
  }

  assert(module_name != NULL, "valid module name");
  assert(providers != NULL, "valid provider array");

  for (int i = 0; i < providers_count; ++i) {
    count += providers[i].probe_count;
  }
  probes = new RegisteredProbes(count);
  count = 0;

  for (int i = 0; i < providers_count; ++i) {
    assert(providers[i].name != NULL, "valid provider name");
    assert(providers[i].probe_count == 0 || providers[i].probes != NULL,
           "valid probe count");
    for (int j = 0; j < providers[i].probe_count; ++j) {
      JVM_DTraceProbe* probe = &(providers[i].probes[j]);
      assert(probe != NULL, "valid probe");
      assert(probe->method != NULL, "valid method");
      assert(probe->name != NULL, "valid probe name");
      assert(probe->function != NULL, "valid probe function spec");
      methodHandle h_method =
        methodHandle(THREAD, JNIHandles::resolve_jmethod_id(probe->method));
      nmethod* nm = AdapterHandlerLibrary::create_dtrace_nmethod(h_method);
      if (nm == NULL) {
        delete probes;
        THROW_MSG_0(vmSymbols::java_lang_RuntimeException(),
          "Unable to register DTrace probes (CodeCache: no room for DTrace nmethods).");
      }
      h_method()->set_not_compilable(CompLevel_highest_tier);
      h_method()->set_code(h_method, nm);
      probes->nmethod_at_put(count++, nm);
    }
  }

  int handle = pd_activate((void*)probes,
    module_name, providers_count, providers);
  if (handle <= 0) {
    delete probes;
    THROW_MSG_0(vmSymbols::java_lang_RuntimeException(),
      "Unable to register DTrace probes (internal error).");
  }
  probes->set_helper_handle(handle);
  return RegisteredProbes::toOpaqueProbes(probes);
}

jboolean DTraceJSDT::is_probe_enabled(jmethodID method) {
  methodOop m = JNIHandles::resolve_jmethod_id(method);
  return nativeInstruction_at(m->code()->trap_address())->is_dtrace_trap();
}

void DTraceJSDT::dispose(OpaqueProbes probes) {
  RegisteredProbes* p = RegisteredProbes::toRegisteredProbes(probes);
  if (probes != -1 && p != NULL) {
    pd_dispose(p->helper_handle());
    delete p;
  }
}

jboolean DTraceJSDT::is_supported() {
  return pd_is_supported();
}

#else // HAVE_DTRACE_H

jlong DTraceJSDT::activate(
    jint version, jstring module_name, jint providers_count,
    JVM_DTraceProvider* providers, TRAPS) {
  return 0;
}

jboolean DTraceJSDT::is_probe_enabled(jmethodID method) {
  return false;
}

void DTraceJSDT::dispose(OpaqueProbes probes) {
  return;
}

jboolean DTraceJSDT::is_supported() {
  return false;
}

#endif // ndef HAVE_DTRACE_H
