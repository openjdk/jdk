/*
 * Copyright (c) 2020 Microsoft Corporation. All rights reserved.
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

/* hsdis.c -- dump a range of addresses as native instructions
   This implements the plugin protocol required by the
   HotSpot PrintAssembly option.
*/

#include <stdio.h>
#include <errno.h>
#include <inttypes.h>
#include <string.h>

#include <llvm-c/Disassembler.h>
#include <llvm-c/DisassemblerTypes.h>
#include <llvm-c/Target.h>
#include <llvm-c/TargetMachine.h>

#include "hsdis.h"

#ifndef bool
#define bool int
#define true 1
#define false 0
#endif /*bool*/

/* short names for stuff in hsdis.h */
typedef decode_instructions_event_callback_ftype  event_callback_t;
typedef decode_instructions_printf_callback_ftype printf_callback_t;

/* disassemble_info.application_data object */
struct hsdis_app_data {
  /* virtual address of data */
  uintptr_t start_va, end_va;
  /* the instructions to be decoded */
  unsigned char* buffer;
  uintptr_t length;
  event_callback_t  event_callback;  void* event_stream;
  printf_callback_t printf_callback; void* printf_stream;
  bool losing;
  bool do_newline;

  /* the architecture being disassembled */
  const char* arch_name;

  /* the disassembler we are going to use: */
  LLVMDisasmContextRef dcontext; /* the actual struct! */

  char target_triple_option[128];
};

static void* decode(struct hsdis_app_data* app_data, const char* options);

#define DECL_EVENT_CALLBACK(app_data) \
  event_callback_t  event_callback = (app_data)->event_callback; \
  void*             event_stream   = (app_data)->event_stream

#define DECL_PRINTF_CALLBACK(app_data) \
  printf_callback_t  printf_callback = (app_data)->printf_callback; \
  void*              printf_stream   = (app_data)->printf_stream


static void print_help(struct hsdis_app_data* app_data,
                       const char* msg, const char* arg);
static void setup_app_data(struct hsdis_app_data* app_data,
                           const char* options);

void* decode_instructions_virtual(uintptr_t start_va, uintptr_t end_va,
                            unsigned char* buffer, uintptr_t length,
                            event_callback_t  event_callback_arg,  void* event_stream_arg,
                            printf_callback_t printf_callback_arg, void* printf_stream_arg,
                            const char* options, int newline) {
  struct hsdis_app_data app_data;
  memset(&app_data, 0, sizeof(app_data));
  app_data.start_va    = start_va;
  app_data.end_va      = end_va;
  app_data.buffer = buffer;
  app_data.length = length;
  app_data.event_callback  = event_callback_arg;
  app_data.event_stream    = event_stream_arg;
  app_data.printf_callback = printf_callback_arg;
  app_data.printf_stream   = printf_stream_arg;
  app_data.do_newline = newline == 0 ? false : true;

  return decode(&app_data, options);
}

/* This is the compatability interface for older version of hotspot */
void* decode_instructions(void* start_pv, void* end_pv,
                    event_callback_t  event_callback_arg,  void* event_stream_arg,
                    printf_callback_t printf_callback_arg, void* printf_stream_arg,
                    const char* options) {
  return decode_instructions_virtual((uintptr_t)start_pv,
                                     (uintptr_t)end_pv,
                                     (unsigned char*)start_pv,
                                     (uintptr_t)end_pv - (uintptr_t)start_pv,
                                     event_callback_arg,
                                     event_stream_arg,
                                     printf_callback_arg,
                                     printf_stream_arg,
                                     options, false);
}

static void* decode(struct hsdis_app_data* app_data, const char* options) {
  setup_app_data(app_data, options);
  char buf[128];

  {
    /* now reload everything from app_data: */
    DECL_EVENT_CALLBACK(app_data);
    DECL_PRINTF_CALLBACK(app_data);
    uintptr_t start = app_data->start_va;
    uintptr_t end   = app_data->end_va;
    uintptr_t p     = start;

    (*event_callback)(event_stream, "insns", (void*)start);

    (*event_callback)(event_stream, "target_triple name='%s'",
                      (void*) app_data->arch_name);

    while (p < end && !app_data->losing) {
      (*event_callback)(event_stream, "insn", (void*) p);

      size_t size = LLVMDisasmInstruction(app_data->dcontext, (uint8_t*)p, (uint64_t)(end - start), (uint64_t)p, buf, sizeof(buf));

      if (size > 0)  { app_data->printf_callback(app_data->printf_stream, "%s", buf); p += size; }
      else           app_data->losing = true;

      if (!app_data->losing) {
        (*event_callback)(event_stream, "/insn", (void*) p);

        if (app_data->do_newline) {
          /* follow each complete insn by a nice newline */
          (*printf_callback)(printf_stream, "\n");
        }
      }
    }

    if (app_data->losing) (*event_callback)(event_stream, "/insns", (void*) p);
    LLVMDisasmDispose(app_data->dcontext);
    return (void*) p;
  }
}

/* configuration */

static void set_optional_callbacks(struct hsdis_app_data* app_data);
static void parse_caller_options(struct hsdis_app_data* app_data,
                                 const char* caller_options);
static const char* native_target_triple();

static void setup_app_data(struct hsdis_app_data* app_data,
                           const char* caller_options) {
  /* Make reasonable defaults for null callbacks.
     A non-null stream for a null callback is assumed to be a FILE* for output.
     Events are rendered as XML.
  */
  set_optional_callbacks(app_data);

  /* Look into caller_options for anything interesting. */
  if (caller_options != NULL)
    parse_caller_options(app_data, caller_options);

  /* Discover which architecture we are going to disassemble. */
  app_data->arch_name = &app_data->target_triple_option[0];
  if (app_data->arch_name[0] == '\0')
    app_data->arch_name = native_target_triple();

  if (LLVMInitializeNativeTarget() != 0) {
    fprintf(stderr, "failed to initialize LLVM native target\n");
  }
  if (LLVMInitializeNativeAsmPrinter() != 0) {
    fprintf(stderr, "failed to initialize LLVM native asm printer\n");
  }
  if (LLVMInitializeNativeDisassembler() != 0) {
    fprintf(stderr, "failed to initialize LLVM native disassembler\n");
  }

  if ((app_data->dcontext = LLVMCreateDisasm(app_data->arch_name, NULL, 0, NULL, NULL)) == NULL) {
    const char* bad = app_data->arch_name;
    static bool complained;
    if (bad == &app_data->target_triple_option[0])
      print_help(app_data, "bad target_triple=%s", bad);
    else if (!complained)
      print_help(app_data, "bad native target_triple=%s; please port hsdis to this platform", bad);
    complained = true;
    /* must bail out */
    app_data->losing = true;
    return;
  }

  LLVMSetDisasmOptions(app_data->dcontext, LLVMDisassembler_Option_PrintImmHex | LLVMDisassembler_Option_AsmPrinterVariant);
}


/* ignore all events, return a null */
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
    const char* argp = strchr(event, ' ');
    if (argp == NULL) {
      /* no arguments; just issue the closing tag */
      fprintf(fp, "</"NS_PFX"%s>", event);
    } else {
      /* split out the closing attributes as <dis:foo_done attr='val'/> */
      int event_prefix = (argp - event);
      fprintf(fp, "<"NS_PFX"%.*s_done", event_prefix, event);
      fprintf(fp, argp, arg);
      fprintf(fp, "/></"NS_PFX"%.*s>", event_prefix, event);
    }
  }
  return NULL;
}

static void set_optional_callbacks(struct hsdis_app_data* app_data) {
  if (app_data->printf_callback == NULL) {
    int (*fprintf_callback)(FILE*, const char*, ...) = &fprintf;
    FILE* fprintf_stream = stdout;
    app_data->printf_callback = (printf_callback_t) fprintf_callback;
    if (app_data->printf_stream == NULL)
      app_data->printf_stream   = (void*)           fprintf_stream;
  }
  if (app_data->event_callback == NULL) {
    if (app_data->event_stream == NULL)
      app_data->event_callback = &null_event_callback;
    else
      app_data->event_callback = &xml_event_callback;
  }

}

static void parse_caller_options(struct hsdis_app_data* app_data, const char* caller_options) {
  const char* p;
  for (p = caller_options; p != NULL; ) {
    const char* q = strchr(p, ',');
    size_t plen = (q == NULL) ? strlen(p) : ((q++) - p);
    if (plen == 4 && strncmp(p, "help", plen) == 0) {
      print_help(app_data, NULL, NULL);
    } else if (plen >= 14 && strncmp(p, "target_triple=", 14) == 0) {
      char*  target_triple_option = app_data->target_triple_option;
      size_t mach_size   = sizeof(app_data->target_triple_option);
      mach_size -= 1;           /*leave room for the null*/
      if (plen > mach_size)  plen = mach_size;
      strncpy(target_triple_option, p, plen);
      target_triple_option[plen] = '\0';
    } else if (plen > 6 && strncmp(p, "hsdis-", 6) == 0) {
      // do not pass these to the next level
    }
    p = q;
  }
}

static void print_help(struct hsdis_app_data* app_data,
                       const char* msg, const char* arg) {
  DECL_PRINTF_CALLBACK(app_data);
  if (msg != NULL) {
    (*printf_callback)(printf_stream, "hsdis: ");
    (*printf_callback)(printf_stream, msg, arg);
    (*printf_callback)(printf_stream, "\n");
  }
  (*printf_callback)(printf_stream, "hsdis output options:\n");
  (*printf_callback)(printf_stream, "  target_triple=<target> select disassembly target triple\n");
  (*printf_callback)(printf_stream, "  help          print this message\n");
}

static const char* native_target_triple() {
#if defined(__APPLE__) && defined(__aarch64__)
  return "aarch64-apple-darwin";
#elif defined(_WIN32) && defined(_M_ARM64)
  return "aarch64-pc-windows-msvc";
#else
#error "unknown platform"
#endif
}
