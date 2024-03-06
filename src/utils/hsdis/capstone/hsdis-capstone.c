/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>

/* Undefine macro to avoid generating invalid C code.
   Capstone refactored cs_detail for AArch64 architecture
   from `cs_arm64 arm64` to `cs_aarch64 aarch64`
   and that causes invalid macro expansion.
*/
#undef aarch64
#include <capstone.h>

#include "hsdis.h"

/* short names for stuff in hsdis.h */
typedef decode_instructions_event_callback_ftype  event_callback_t;
typedef decode_instructions_printf_callback_ftype printf_callback_t;

#define print(...) (*printf_callback) (printf_stream, __VA_ARGS__)

static void* null_event_callback(void* ignore_stream, const char* ignore_event, void* arg) {
  return NULL;
}

/* print all events as XML markup */
static void* xml_event_callback(void* stream, const char* event, void* arg) {
  FILE* fp = (FILE*) stream;
#define NS_PFX "dis:"
  if (event[0] != '/') {
    /* issue the tag, with or without a formatted argument */
    fprintf(fp, "<"NS_PFX);
    fprintf(fp, event, arg);
    fprintf(fp, ">");
  } else {
    ++event;                    /* skip slash */
    fprintf(fp, "</"NS_PFX"%s>", event);
  }
  return NULL;
}

static const char* INTEL_SYNTAX_OP = "intel";

typedef struct {
  bool intel_syntax;
} Options;

static Options parse_options(const char* options, printf_callback_t printf_callback, void* printf_stream) {
  Options ops;
  // initialize with defaults
  ops.intel_syntax = false;

  const char* cursor = options;
  while (*cursor != '\0') {
    if (*cursor == ',') {
      cursor++;
    }
    if (strncmp(cursor, INTEL_SYNTAX_OP, strlen(INTEL_SYNTAX_OP)) == 0) {
      cursor += strlen(INTEL_SYNTAX_OP);
      ops.intel_syntax = true;
    } else {
      const char* end = strchr(cursor, ',');
      if (end == NULL) {
        end = strchr(cursor, '\0');
      }
      print("Unknown PrintAssembly option: %.*s\n", (int) (end - cursor), cursor);
      cursor = end;
    }
  }

  return ops;
}

#ifdef _WIN32
__declspec(dllexport)
#endif
void* decode_instructions_virtual(uintptr_t start_va, uintptr_t end_va,
                                  unsigned char* buffer, uintptr_t length,
                                  event_callback_t event_callback,
                                  void* event_stream,
                                  printf_callback_t printf_callback,
                                  void* printf_stream,
                                  const char* options,
                                  int newline /* bool value for nice new line */) {
  csh cs_handle;

  if (printf_callback == NULL) {
    int (*fprintf_callback)(FILE*, const char*, ...) = &fprintf;
    FILE* fprintf_stream = stdout;
    printf_callback = (printf_callback_t) fprintf_callback;
    if (printf_stream == NULL)
      printf_stream   = (void*)           fprintf_stream;
  }
  if (event_callback == NULL) {
    if (event_stream == NULL)
      event_callback = &null_event_callback;
    else
      event_callback = &xml_event_callback;
  }


  if (cs_open(CAPSTONE_ARCH, CAPSTONE_MODE, &cs_handle) != CS_ERR_OK) {
    print("Could not open cs_handle");
    return NULL;
  }

  Options ops = parse_options(options, printf_callback, printf_stream);
  cs_option(cs_handle, CS_OPT_SYNTAX, ops.intel_syntax ? CS_OPT_SYNTAX_INTEL : CS_OPT_SYNTAX_ATT);

  // Turn on SKIPDATA mode to skip broken instructions. HotSpot often
  // has embedded data in method bodies, and we need disassembly to
  // continue when such non-instructions are not recognized.
  cs_option(cs_handle, CS_OPT_SKIPDATA, CS_OPT_ON);

  cs_insn *insn;
  size_t count = cs_disasm(cs_handle, buffer, length, (uintptr_t) buffer, 0 , &insn);
  if (count) {
    for (unsigned int j = 0; j < count; j++) {
      (*event_callback)(event_stream, "insn", (void*) insn[j].address);
      print("%s\t\t%s", insn[j].mnemonic, insn[j].op_str);
      (*event_callback)(event_stream, "/insn", (void*) (insn[j].address + insn[j].size));
      if (newline) {
        /* follow each complete insn by a nice newline */
        print("\n");
      }
    }
    cs_free(insn, count);
  }

  cs_close(&cs_handle);

  return NULL;
}
