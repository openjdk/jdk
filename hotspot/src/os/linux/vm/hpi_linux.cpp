/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include <sys/param.h>
# include <dlfcn.h>

typedef jint (JNICALL *init_t)(GetInterfaceFunc *, void *);

void hpi::initialize_get_interface(vm_calls_t *callbacks) {
    char buf[JVM_MAXPATHLEN];
    void *hpi_handle;
    GetInterfaceFunc& getintf = _get_interface;
    jint (JNICALL * DLL_Initialize)(GetInterfaceFunc *, void *);

    if (HPILibPath && HPILibPath[0]) {
      strncpy(buf, HPILibPath, JVM_MAXPATHLEN - 1);
      buf[JVM_MAXPATHLEN - 1] = '\0';
    } else {
      const char *thread_type = "native_threads";

      os::jvm_path(buf, JVM_MAXPATHLEN);

#ifdef PRODUCT
      const char * hpi_lib = "/libhpi.so";
#else
      char * ptr = strrchr(buf, '/');
      assert(strstr(ptr, "/libjvm") == ptr, "invalid library name");
      const char * hpi_lib = strstr(ptr, "_g") ? "/libhpi_g.so" : "/libhpi.so";
#endif

      *(strrchr(buf, '/')) = '\0';  /* get rid of /libjvm.so */
      char* p = strrchr(buf, '/');
      if (p != NULL) p[1] = '\0';   /* get rid of hotspot    */
      strcat(buf, thread_type);
      strcat(buf, hpi_lib);
    }

    if (TraceHPI) tty->print_cr("Loading HPI %s ", buf);
#ifdef SPARC
    // On 64-bit Ubuntu Sparc RTLD_NOW leads to unresolved deps in libpthread.so
#   define OPEN_MODE RTLD_LAZY
#else
    // We use RTLD_NOW because of bug 4032715
#   define OPEN_MODE RTLD_NOW
#endif
    hpi_handle = dlopen(buf, OPEN_MODE);
#undef OPEN_MODE

    if (hpi_handle == NULL) {
        if (TraceHPI) tty->print_cr("HPI dlopen failed: %s", dlerror());
        return;
    }
    DLL_Initialize = CAST_TO_FN_PTR(jint (JNICALL *)(GetInterfaceFunc *, void *),
                                    dlsym(hpi_handle, "DLL_Initialize"));
    if (TraceHPI && DLL_Initialize == NULL) tty->print_cr("HPI dlsym of DLL_Initialize failed: %s", dlerror());
    if (DLL_Initialize == NULL ||
        (*DLL_Initialize)(&getintf, callbacks) < 0) {
        if (TraceHPI) tty->print_cr("HPI DLL_Initialize failed");
        return;
    }
    if (TraceHPI)  tty->print_cr("HPI loaded successfully");
}
