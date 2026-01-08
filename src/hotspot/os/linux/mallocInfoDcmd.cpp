/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "mallocInfoDcmd.hpp"
#include "os_linux.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/permitForbiddenFunctions.hpp"

#include <malloc.h>

constexpr const char* malloc_info_unavailable = "Error: malloc_info(3) not available.";

void MallocInfoDcmd::execute(DCmdSource source, TRAPS) {
#ifdef __GLIBC__
  char* buf;
  size_t size;
  FILE* stream = ::open_memstream(&buf, &size);
  if (stream == nullptr) {
    _output->print_cr("Error: Could not call malloc_info(3)");
    return;
  }

  int err = os::Linux::malloc_info(stream);
  if (err == 0) {
    fflush(stream);
    _output->print_raw(buf);
    _output->cr();
  } else if (err == -1) {
    _output->print_cr("Error: %s", os::strerror(errno));
  } else if (err == -2) {
    _output->print_cr(malloc_info_unavailable);
  } else {
    ShouldNotReachHere();
  }
  ::fclose(stream);
  permit_forbidden_function::free(buf);
#else
  _output->print_cr(malloc_info_unavailable);
#endif // __GLIBC__
}
