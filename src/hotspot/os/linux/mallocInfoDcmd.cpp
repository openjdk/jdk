/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.inline.hpp"
#include "mallocInfoDcmd.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#include <malloc.h>

void MallocInfoDcmd::execute(DCmdSource source, TRAPS) {
#ifdef LINUX
  char* buf;
  size_t size;
  ALLOW_C_FUNCTION(::open_memstream, FILE* stream = ::open_memstream(&buf, &size);)
  if (!os::Linux::malloc_info(stream)) {
    ALLOW_C_FUNCTION(::fflush, fflush(stream);)
    _output->print_raw(buf);
  }
  ALLOW_C_FUNCTION(::fclose, ::fclose(stream);)
  ALLOW_C_FUNCTION(::free, ::free(buf);)
#else
    // Nothing.
#endif // LINUX
}
