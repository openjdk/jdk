/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
#include "ergo.h"


/* Methods for solaris-sparc and linux-sparc: these are easy. */

/* Ask the OS how many processors there are. */
static unsigned long
physical_processors(void) {
  const unsigned long sys_processors = sysconf(_SC_NPROCESSORS_CONF);

  JLI_TraceLauncher("sysconf(_SC_NPROCESSORS_CONF): %lu\n", sys_processors);
  return sys_processors;
}

/* The sparc version of the "server-class" predicate. */
jboolean
ServerClassMachineImpl(void) {
  jboolean            result            = JNI_FALSE;
  /* How big is a server class machine? */
  const unsigned long server_processors = 2UL;
  const uint64_t      server_memory     = 2UL * GB;
  const uint64_t      actual_memory     = physical_memory();

  /* Is this a server class machine? */
  if (actual_memory >= server_memory) {
    const unsigned long actual_processors = physical_processors();
    if (actual_processors >= server_processors) {
      result = JNI_TRUE;
    }
  }
  JLI_TraceLauncher("unix_" LIBARCHNAME "_ServerClassMachine: %s\n",
           (result == JNI_TRUE ? "JNI_TRUE" : "JNI_FALSE"));
  return result;
}
