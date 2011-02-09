/*
* Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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

#include "prims/jvm.h"
#include "runtime/os.hpp"
#include "utilities/vmError.hpp"

#include <unistd.h>
#include <sys/resource.h>

// Check core dump limit and report possible place where core can be found
void os::check_or_create_dump(void* exceptionRecord, void* contextRecord, char* buffer, size_t bufferSize) {
  struct rlimit rlim;
  static char cwd[O_BUFLEN];
  bool success;

  get_current_directory(cwd, sizeof(cwd));

  if (getrlimit(RLIMIT_CORE, &rlim) != 0) {
    jio_snprintf(buffer, bufferSize, "%s/core or core.%d (may not exist)", cwd, current_process_id());
    success = true;
  } else {
    switch(rlim.rlim_cur) {
      case RLIM_INFINITY:
        jio_snprintf(buffer, bufferSize, "%s/core or core.%d", cwd, current_process_id());
        success = true;
        break;
      case 0:
        jio_snprintf(buffer, bufferSize, "Core dumps have been disabled. To enable core dumping, try \"ulimit -c unlimited\" before starting Java again");
        success = false;
        break;
      default:
        jio_snprintf(buffer, bufferSize, "%s/core or core.%d (max size %lu kB). To ensure a full core dump, try \"ulimit -c unlimited\" before starting Java again", cwd, current_process_id(), (unsigned long)(rlim.rlim_cur >> 10));
        success = true;
        break;
    }
  }
  VMError::report_coredump_status(buffer, success);
}

