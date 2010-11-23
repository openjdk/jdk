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
#include "prims/jvm.h"
#include "runtime/hpi.hpp"

extern "C" {
  static void unimplemented_panic(const char *fmt, ...) {
    // mitigate testing damage from bug 6626677
    warning("hpi::unimplemented_panic called");
  }

  static void unimplemented_monitorRegister(sys_mon_t *mid, char *info_str) {
    Unimplemented();
  }
}

static vm_calls_t callbacks = {
  jio_fprintf,
  unimplemented_panic,
  unimplemented_monitorRegister,

  NULL, // unused
  NULL, // unused
  NULL  // unused
};

GetInterfaceFunc        hpi::_get_interface = NULL;
HPI_FileInterface*      hpi::_file          = NULL;
HPI_SocketInterface*    hpi::_socket        = NULL;
HPI_LibraryInterface*   hpi::_library       = NULL;
HPI_SystemInterface*    hpi::_system        = NULL;

jint hpi::initialize()
{
  initialize_get_interface(&callbacks);
  if (_get_interface == NULL)
    return JNI_ERR;

  jint result;

  result = (*_get_interface)((void **)&_file, "File", 1);
  if (result != 0) {
    if (TraceHPI) tty->print_cr("Can't find HPI_FileInterface");
    return JNI_ERR;
  }


  result = (*_get_interface)((void **)&_library, "Library", 1);
  if (result != 0) {
    if (TraceHPI) tty->print_cr("Can't find HPI_LibraryInterface");
    return JNI_ERR;
  }

  result = (*_get_interface)((void **)&_system, "System", 1);
  if (result != 0) {
    if (TraceHPI) tty->print_cr("Can't find HPI_SystemInterface");
    return JNI_ERR;
  }

  return JNI_OK;
}

jint hpi::initialize_socket_library()
{
  if (_get_interface == NULL) {
    if (TraceHPI) {
      tty->print_cr("Fatal HPI error: reached initialize_socket_library with NULL _get_interface");
    }
    return JNI_ERR;
  }

  jint result;
  result = (*_get_interface)((void **)&_socket, "Socket", 1);
  if (result != 0) {
    if (TraceHPI) tty->print_cr("Can't find HPI_SocketInterface");
    return JNI_ERR;
  }

  return JNI_OK;
}
