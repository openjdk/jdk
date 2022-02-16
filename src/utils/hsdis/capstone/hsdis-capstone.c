/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to
 * any person obtaining a copy of this software, associated documentation
 * and/or data (collectively the "Software"), free of charge and under any
 * and all copyright rights in the Software, and any and all patent rights
 * owned or freely licensable by each licensor hereunder covering either (i)
 * the unmodified Software as contributed to or provided by such licensor,
 * or (ii) the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file
 * if one is included with the Software (each a "Larger Work" to which the
 * Software is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy,
 * create derivative works of, display, perform, and distribute the Software
 * and make, use, sell, offer for sale, import, export, have made, and have
 * sold the Software and the Larger Work(s), and to sublicense the foregoing
 * rights on either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or
 * at a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

/* hsdis.c -- dump a range of addresses as native instructions
   This implements the plugin protocol required by the
   HotSpot PrintAssembly option.
*/

#include <inttypes.h>

#include <capstone.h>

#include "hsdis.h"

/* short names for stuff in hsdis.h */
typedef decode_instructions_event_callback_ftype  event_callback_t;
typedef decode_instructions_printf_callback_ftype printf_callback_t;

#define print(...) (*printf_callback) (printf_stream, __VA_ARGS__)

#ifdef _WIN32
__declspec(dllexport)
#endif
void* decode_instructions_virtual(uintptr_t start_va, uintptr_t end_va,
                                  unsigned char* buffer, uintptr_t length,
                                  void* (*event_callback)(void*, const char*, void*),
                                  void* event_stream,
                                  int (*printf_callback)(void*, const char*, ...),
                                  void* printf_stream,
                                  const char* options,
                                  int newline /* bool value for nice new line */) {
  csh cs_handle;

  if (cs_open(CAPSTONE_ARCH, CAPSTONE_MODE, &cs_handle) != CS_ERR_OK) {
    print("Could not open cs_handle");
    return NULL;
  }

  // TODO: Support intel syntax
  cs_option(cs_handle, CS_OPT_SYNTAX, CS_OPT_SYNTAX_ATT);

  cs_insn *insn;
  size_t count = cs_disasm(cs_handle, buffer, length, (uintptr_t) buffer, 0 , &insn);
  if (count) {
    for (unsigned int j = 0; j < count; j++) {
      print("  0x%" PRIx64 ":\t%s\t\t%s\n", insn[j].address, insn[j].mnemonic, insn[j].op_str);
    }
    cs_free(insn, count);
  }

  cs_close(&cs_handle);

  return NULL;
}
