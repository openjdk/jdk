/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

/* hsdis.cpp -- dump a range of addresses as native instructions
   This implements the plugin protocol required by the
   HotSpot PrintAssembly option.
*/

#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <inttypes.h>
#include <string.h>

#include <llvm-c/Disassembler.h>
#include <llvm-c/DisassemblerTypes.h>
#include <llvm-c/Target.h>
#include <llvm-c/TargetMachine.h>

#include "hsdis.h"

/* short names for stuff in hsdis.h */
typedef decode_instructions_event_callback_ftype  event_callback_t;
typedef decode_instructions_printf_callback_ftype printf_callback_t;

class hsdis_backend_base {
 protected:
  uintptr_t         _start_va;
  uintptr_t         _end_va;
  unsigned char*    _buffer;
  uintptr_t         _length;
  event_callback_t  _event_callback;
  void*             _event_stream;
  printf_callback_t _printf_callback;
  void*             _printf_stream;
  int               _do_newline;

  bool              _losing;
  const char*       _arch_name;

  virtual void print_help(const char* msg, const char* arg) = 0;
  virtual void print_insns_config() = 0;
  virtual size_t decode_instruction(uintptr_t p, uintptr_t start, uintptr_t end) = 0;
  virtual const char* format_insn_close(const char* close, char* buf, size_t bufsize) = 0;

 private:
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
      fprintf(fp, "<" NS_PFX);
      fprintf(fp, event, arg);
      fprintf(fp, ">");
    } else {
      ++event;                    /* skip slash */
      const char* argp = strchr(event, ' ');
      if (argp == NULL) {
        /* no arguments; just issue the closing tag */
        fprintf(fp, "</" NS_PFX "%s>", event);
      } else {
        /* split out the closing attributes as <dis:foo_done attr='val'/> */
        size_t event_prefix =(argp - event);
        fprintf(fp, "<" NS_PFX "%.*s_done", (int) event_prefix, event);
        fprintf(fp, argp, arg);
        fprintf(fp, "/></" NS_PFX "%.*s>", (int) event_prefix, event);
      }
    }
#undef NS_PFX
    return NULL;
  }

protected:
  hsdis_backend_base(uintptr_t start_va, uintptr_t end_va,
                     unsigned char* buffer, uintptr_t length,
                     event_callback_t  event_callback,  void* event_stream,
                     printf_callback_t printf_callback, void* printf_stream,
                     int do_newline) :
      _start_va(start_va), _end_va(end_va),
      _buffer(buffer), _length(length),
      _event_callback(event_callback), _event_stream(event_stream),
      _printf_callback(printf_callback), _printf_stream(printf_stream),
      _do_newline(do_newline),
      _losing(false), _arch_name(NULL)
  {
    /* Make reasonable defaults for null callbacks.
      A non-null stream for a null callback is assumed to be a FILE* for output.
      Events are rendered as XML.
    */
    if (_printf_callback == NULL) {
      int (*fprintf_callback)(FILE*, const char*, ...) = &fprintf;
      FILE* fprintf_stream = stdout;
      _printf_callback = (printf_callback_t) fprintf_callback;
      if (_printf_stream == NULL)
        _printf_stream   = (void*)           fprintf_stream;
    }
    if (_event_callback == NULL) {
      if (_event_stream == NULL)
        _event_callback = (event_callback_t)&null_event_callback;
      else
        _event_callback = (event_callback_t)&xml_event_callback;
    }
  }

 public:
  void* decode() {
    uintptr_t start = _start_va;
    uintptr_t end   = _end_va;
    uintptr_t p     = start;

    (*_event_callback)(_event_stream, "insns", (void*)start);

    print_insns_config();

    while (p < end && !_losing) {
      (*_event_callback)(_event_stream, "insn", (void*) p);

      size_t size = decode_instruction(p, start, end);
      if (size > 0)  p += size;
      else           _losing = true;

      if (!_losing) {
        char buf[128];
        const char* insn_close = format_insn_close("/insn", buf, sizeof(buf));
        (*_event_callback)(_event_stream, insn_close, (void*) p);

        if (_do_newline) {
          /* follow each complete insn by a nice newline */
          (*_printf_callback)(_printf_stream, "\n");
        }
      }
    }

    if (_losing) (*_event_callback)(_event_stream, "/insns", (void*) p);
    return (void*) p;
  }
};


class hsdis_backend : public hsdis_backend_base {
 private:
  LLVMDisasmContextRef      _dcontext;
  char                      _target_triple[128];

  void parse_caller_options(const char* options) {
    memset(&_target_triple, 0, sizeof(_target_triple));
    const char* p;
    for (p = options; p != NULL; ) {
      const char* q = strchr(p, ',');
      size_t plen = (q == NULL) ? strlen(p) : ((q++) - p);
      if (plen == 4 && strncmp(p, "help", plen) == 0) {
        print_help(NULL, NULL);
      } else if (plen > 6 && strncmp(p, "hsdis-", 6) == 0) {
        // do not pass these to the next level
      } else if (plen >= 14 && strncmp(p, "target_triple=", 14) == 0) {
        char*  target_triple = _target_triple;
        size_t target_triple_size   = sizeof(_target_triple);
        target_triple_size -= 1;           /*leave room for the null*/
        if (plen > target_triple_size)  plen = target_triple_size;
        strncpy(target_triple, p, plen);
        target_triple[plen] = '\0';
      }
      p = q;
    }
  }

  const char* native_target_triple() {
    return LLVM_DEFAULT_TRIPLET;
  }

 public:
  hsdis_backend(uintptr_t start_va, uintptr_t end_va,
                unsigned char* buffer, uintptr_t length,
                event_callback_t  event_callback,  void* event_stream,
                printf_callback_t printf_callback, void* printf_stream,
                const char* options, int newline)
    : hsdis_backend_base(start_va, end_va,
                         buffer, length,
                         event_callback, event_stream,
                         printf_callback, printf_stream,
                         newline),
      _dcontext(NULL) {
    /* Look into _options for anything interesting. */
    if (options != NULL)
      parse_caller_options(options);

    /* Discover which architecture we are going to disassemble. */
    _arch_name = &_target_triple[0];
    if (_arch_name[0] == '\0')
      _arch_name = native_target_triple();

    if (LLVMInitializeNativeTarget() != 0) {
      static bool complained = false;
      if (!complained)
        (*_printf_callback)(_printf_stream, "failed to initialize LLVM native target\n");
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }
    if (LLVMInitializeNativeAsmPrinter() != 0) {
      static bool complained = false;
      if (!complained)
        (*_printf_callback)(_printf_stream, "failed to initialize LLVM native asm printer\n");
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }
    if (LLVMInitializeNativeDisassembler() != 0) {
      static bool complained = false;
      if (!complained)
        (*_printf_callback)(_printf_stream, "failed to initialize LLVM native disassembler\n");
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }
    if ((_dcontext = LLVMCreateDisasm(_arch_name, NULL, 0, NULL, NULL)) == NULL) {
      static bool complained = false;
      const char* bad = _arch_name;
      if (bad == &_target_triple[0])
        print_help("bad target_triple=%s", bad);
      else if (!complained)
        print_help("bad native target_triple=%s; please port hsdis to this platform", bad);
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }

    LLVMSetDisasmOptions(_dcontext, LLVMDisassembler_Option_PrintImmHex);
  }

  ~hsdis_backend() {
    if (_dcontext != NULL) {
      LLVMDisasmDispose(_dcontext);
    }
  }

 protected:
  virtual void print_help(const char* msg, const char* arg) {
    if (msg != NULL) {
      (*_printf_callback)(_printf_stream, "hsdis: ");
      (*_printf_callback)(_printf_stream, msg, arg);
      (*_printf_callback)(_printf_stream, "\n");
    }
    (*_printf_callback)(_printf_stream, "hsdis output options:\n");
    (*_printf_callback)(_printf_stream, "  target_triple=<triple> select disassembly target\n");
    (*_printf_callback)(_printf_stream, "  help          print this message\n");
  }

  virtual void print_insns_config() {
    (*_event_callback)(_event_stream, "target_triple name='%s'",
                      (void*) _arch_name);
  }

  virtual size_t decode_instruction(uintptr_t p, uintptr_t start, uintptr_t end) {
    char buf[128];
    size_t size = LLVMDisasmInstruction(_dcontext, (uint8_t*)p, (uint64_t)(end - start), (uint64_t)p, buf, sizeof(buf));
    if (size > 0) {
      (*_printf_callback)(_printf_stream, "%s", buf);
    } else {
      // LLVM encountered an unknown instruction
      if (end - start >= 4) {
        // Print the following word and skip past it
        snprintf(buf, sizeof(buf), "\t.inst\t#0x%08x ; undefined", *(uint32_t*)p);
        size = 4;
      } else {
        snprintf(buf, sizeof(buf), "\t<invalid instruction, aborting hsdis>");
      }
    }
    return size;
  }

  virtual const char* format_insn_close(const char* close, char* buf, size_t bufsize) {
    return close;
  }
};


JNIEXPORT
void* decode_instructions_virtual(uintptr_t start_va, uintptr_t end_va,
                            unsigned char* buffer, uintptr_t length,
                            event_callback_t  event_callback_arg,  void* event_stream_arg,
                            printf_callback_t printf_callback_arg, void* printf_stream_arg,
                            const char* options, int newline) {
  return hsdis_backend(start_va, end_va,
                       buffer, length,
                       event_callback_arg, event_stream_arg,
                       printf_callback_arg, printf_stream_arg,
                       options, newline == 0 ? false : true)
          .decode();
}

/* This is the compatability interface for older version of hotspot */
JNIEXPORT
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
