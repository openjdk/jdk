/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

// macOS decodes Mach-O and brings its own MachODecoder; ElfDecoder is
// compiled on the other BSDs, where nothing supplied demangle().  This is
// the same implementation os/linux uses.
#ifndef __APPLE__

#include "jvm.h"
#include "utilities/decoder_elf.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/permitForbiddenFunctions.hpp"

#include <cxxabi.h>

bool ElfDecoder::demangle(const char* symbol, char *buf, int buflen) {
  int   status;
  char* result;

#ifdef PPC64
  // On PPC64 ElfDecoder::decode() may return a dot (.) prefixed name
  // (see elfFuncDescTable.hpp for details)
  if (symbol && *symbol == '.') symbol += 1;
#endif

  // Only an Itanium mangled name starts _Z, and only such a name may be
  // handed to the demangler.  It is not obliged to reject anything else, and
  // FreeBSD's does not: it reads thread_start as a template-id and answers
  // "operator->", and every other C symbol in a native stack comes back as
  // some plausible-looking type.  A whole hs_err stack of them reads as
  //
  //   V  [libjvm.so+0xd48ddd]  char+0xcd
  //   C  [libnativeStack.so+0x1cbe]  unsigned short+0x3e
  //   C  [libthr.so.3+0x10e41]  operator->+0x951
  //
  // where the last of those is thread_start.  The name a C function was
  // given is already the name to print, so leave it alone.
  if (symbol == nullptr || symbol[0] != '_' || symbol[1] != 'Z') {
    return false;
  }

  // Don't pass buf to __cxa_demangle. In case of the 'buf' is too small,
  // __cxa_demangle will call system "realloc" for additional memory, which
  // may use different malloc/realloc mechanism that allocates 'buf'.
  if ((result = abi::__cxa_demangle(symbol, nullptr, nullptr, &status)) != nullptr) {
    jio_snprintf(buf, buflen, "%s", result);
    // call c library's free
    permit_forbidden_function::free(result);
    return true;
  }
  return false;
}

#endif // !__APPLE__
