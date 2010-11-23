/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/oop.inline.hpp"
#include "runtime/hpi.hpp"
#include "runtime/os.hpp"

typedef jint (JNICALL *init_t)(GetInterfaceFunc *, void *);

void hpi::initialize_get_interface(vm_calls_t *callbacks)
{
  // Build name of HPI.
  char lib_name[JVM_MAXPATHLEN];

  if (HPILibPath && HPILibPath[0]) {
    strncpy(lib_name, HPILibPath, JVM_MAXPATHLEN - 1);
    lib_name[JVM_MAXPATHLEN - 1] = '\0';
  } else {
    os::jvm_path(lib_name, sizeof lib_name);

#ifdef PRODUCT
    const char *hpi_lib = "\\hpi.dll";
#else
    char *ptr = strrchr(lib_name, '\\');
    //  On Win98 GetModuleFileName() returns the path in the upper case.
    assert(_strnicmp(ptr, "\\jvm",4) == 0, "invalid library name");
    const char *hpi_lib = (_strnicmp(ptr, "\\jvm_g",6) == 0) ? "\\hpi_g.dll" : "\\hpi.dll";
#endif

    *(::strrchr(lib_name, '\\')) = '\0';  /* get rid of "\\jvm.dll" */
    char *p = ::strrchr(lib_name, '\\');
    if (p != NULL) *p = '\0';             /* get rid of "\\hotspot" */
    strcat(lib_name, hpi_lib);
  }

  // Load it.
  if (TraceHPI) tty->print_cr("Loading HPI %s ", lib_name);
  HINSTANCE lib_handle = LoadLibrary(lib_name);
  if (lib_handle == NULL) {
    if (TraceHPI) tty->print_cr("LoadLibrary failed, code = %d", GetLastError());
    return;
  }

  // Find hpi initializer.
  init_t initer = (init_t)GetProcAddress(lib_handle, "DLL_Initialize");
  if (initer == NULL) {
    if (TraceHPI) tty->print_cr("GetProcAddress failed, errcode = %d", GetLastError());
    return;
  }

  // Call initializer.
  jint init_result = (*initer)(&_get_interface, callbacks);
  if (init_result < 0) {
    if (TraceHPI) tty->print_cr("DLL_Initialize failed, returned %ld", init_result);
    return;
  }

  if (TraceHPI) tty->print_cr("success");
  return;
}
