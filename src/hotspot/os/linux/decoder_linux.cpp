/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/decoder_elf.hpp"

#include <cxxabi.h>

bool ElfDecoder::demangle(const char* symbol, char *buf, int buflen) {
  int   status;
  char* result;
  size_t size = (size_t)buflen;

#ifdef PPC64
  // On PPC64 ElfDecoder::decode() may return a dot (.) prefixed name
  // (see elfFuncDescTable.hpp for details)
  if (symbol && *symbol == '.') symbol += 1;
#endif

  // Don't pass buf to __cxa_demangle. In case of the 'buf' is too small,
  // __cxa_demangle will call system "realloc" for additional memory, which
  // may use different malloc/realloc mechanism that allocates 'buf'.
  if ((result = abi::__cxa_demangle(symbol, NULL, NULL, &status)) != NULL) {
    jio_snprintf(buf, buflen, "%s", result);
      // call c library's free
      ::free(result);
      return true;
  }
  return false;
}

