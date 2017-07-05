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

/* This file houses the common methods for VM ergonomics the platforms
 * are split into ergo_sparc and ergo_x86, and they could be split more
 * in the future if required. The following comments are not entirely
 * true after bifurcation of the platform specific files.
 */

/*
 * The following methods (down to ServerClassMachine()) answer
 * the question about whether a machine is a "server-class"
 * machine.  A server-class machine is loosely defined as one
 * with 2 or more processors and 2 gigabytes or more physical
 * memory.  The definition of a processor is a physical package,
 * not a hyperthreaded chip masquerading as a multi-processor.
 * The definition of memory is also somewhat fuzzy, since x86
 * machines seem not to report all the memory in their DIMMs, we
 * think because of memory mapping of graphics cards, etc.
 *
 * This code is somewhat more confused with #ifdef's than we'd
 * like because this file is used by both Solaris and Linux
 * platforms, and so needs to be parameterized for SPARC and
 * i586 hardware.  The other Linux platforms (amd64 and ia64)
 * don't even ask this question, because they only come with
 * server JVMs.
 */

#include "ergo.h"

/* Dispatch to the platform-specific definition of "server-class" */
jboolean
ServerClassMachine(void) {
  jboolean result;
  switch(GetErgoPolicy()) {
    case NEVER_SERVER_CLASS:
      return JNI_FALSE;
    case ALWAYS_SERVER_CLASS:
      return JNI_TRUE;
    default:
      result = ServerClassMachineImpl();
      JLI_TraceLauncher("ServerClassMachine: returns default value of %s\n",
           (result == JNI_TRUE ? "true" : "false"));
      return result;
  }
}


/* Compute physical memory by asking the OS */
uint64_t
physical_memory(void) {
  const uint64_t pages     = (uint64_t) sysconf(_SC_PHYS_PAGES);
  const uint64_t page_size = (uint64_t) sysconf(_SC_PAGESIZE);
  const uint64_t result    = pages * page_size;
# define UINT64_FORMAT "%" PRIu64

  JLI_TraceLauncher("pages: " UINT64_FORMAT
          "  page_size: " UINT64_FORMAT
          "  physical memory: " UINT64_FORMAT " (%.3fGB)\n",
           pages, page_size, result, result / (double) GB);
  return result;
}
