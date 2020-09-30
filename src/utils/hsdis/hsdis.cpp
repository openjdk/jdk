/*
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
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

#define STRING2(x) #x
#define STRING(x) STRING2(x)

#ifndef LLVM
#include <config.h> /* required by bfd.h */
#endif
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <inttypes.h>
#include <string.h>

#ifndef LLVM
#include <libiberty.h>
#include <bfd.h>
#include <bfdver.h>
#include <dis-asm.h>
#else
#include <llvm-c/Disassembler.h>
#include <llvm-c/DisassemblerTypes.h>
#include <llvm-c/Target.h>
#include <llvm-c/TargetMachine.h>
#endif

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
        int event_prefix = (argp - event);
        fprintf(fp, "<" NS_PFX "%.*s_done", event_prefix, event);
        fprintf(fp, argp, arg);
        fprintf(fp, "/></" NS_PFX "%.*s>", event_prefix, event);
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

#ifndef LLVM

class hsdis_backend : public hsdis_backend_base {
 private:
  disassembler_ftype        _dfn;
  struct disassemble_info   _dinfo;
  const bfd_arch_info_type* _arch_info;
  char                      _mach_option[64];
  char                      _insn_options[256];

  void parse_caller_options(const char* options) {
    memset(&_mach_option, 0, sizeof(_mach_option));
    memset(&_insn_options, 0, sizeof(_insn_options));
    char* iop_base = _insn_options;
    char* iop_limit = iop_base + sizeof(_insn_options) - 1;
    char* iop = iop_base;
    const char* p;
    for (p = options; p != NULL; ) {
      const char* q = strchr(p, ',');
      size_t plen = (q == NULL) ? strlen(p) : ((q++) - p);
      if (plen == 4 && strncmp(p, "help", plen) == 0) {
        print_help(NULL, NULL);
      } else if (plen > 6 && strncmp(p, "hsdis-", 6) == 0) {
        // do not pass these to the next level
      } else if (plen >= 5 && strncmp(p, "mach=", 5) == 0) {
        char*  mach_option = _mach_option;
        size_t mach_size   = sizeof(_mach_option);
        mach_size -= 1;           /*leave room for the null*/
        if (plen > mach_size)  plen = mach_size;
        strncpy(mach_option, p, plen);
        mach_option[plen] = '\0';
      } else {
        /* just copy it; {i386,sparc}-dis.c might like to see it  */
        if (iop > iop_base && iop < iop_limit)  (*iop++) = ',';
        if (iop + plen > iop_limit)
          plen = iop_limit - iop;
        strncpy(iop, p, plen);
        iop += plen;
      }
      p = q;
    }
    *iop = '\0';
  }

  /* configuration */
  const char* native_arch_name() {
    const char* res = NULL;
#ifdef LIBARCH_i386
    res = "i386";
#endif
#ifdef LIBARCH_amd64
    res = "i386:x86-64";
#endif
#if  defined(LIBARCH_ppc64) || defined(LIBARCH_ppc64le)
    res = "powerpc:common64";
#endif
#ifdef LIBARCH_arm
    res = "arm";
#endif
#ifdef LIBARCH_aarch64
    res = "aarch64";
#endif
#ifdef LIBARCH_s390x
    res = "s390:64-bit";
#endif
    if (res == NULL)
      res = "architecture not set in Makefile!";
    return res;
  }

  enum bfd_endian native_endian() {
    int32_t endian_test = 'x';
    if (*(const char*) &endian_test == 'x')
      return BFD_ENDIAN_LITTLE;
    else
      return BFD_ENDIAN_BIG;
  }

  const bfd_arch_info_type* find_arch_info(const char* arch_name) {
    const bfd_arch_info_type* arch_info = bfd_scan_arch(arch_name);
    if (arch_info == NULL) {
      extern const bfd_arch_info_type bfd_default_arch_struct;
      arch_info = &bfd_default_arch_struct;
    }
    return arch_info;
  }

  bfd* get_native_bfd(const bfd_arch_info_type* arch_info,
                      /* to avoid malloc: */
                      bfd* empty_bfd, bfd_target* empty_xvec) {
    memset(empty_bfd,  0, sizeof(*empty_bfd));
    memset(empty_xvec, 0, sizeof(*empty_xvec));
    empty_xvec->flavour = bfd_target_unknown_flavour;
    empty_xvec->byteorder = native_endian();
    empty_bfd->xvec = empty_xvec;
    empty_bfd->arch_info = arch_info;
    return empty_bfd;
  }

  void init_disassemble_info_from_bfd(struct disassemble_info* dinfo,
                                      void *stream,
                                      fprintf_ftype fprintf_func,
                                      bfd* abfd,
                                      char* disassembler_options) {
    init_disassemble_info(dinfo, stream, fprintf_func);

    dinfo->flavour = bfd_get_flavour(abfd);
    dinfo->arch = bfd_get_arch(abfd);
    dinfo->mach = bfd_get_mach(abfd);
    dinfo->disassembler_options = disassembler_options;
#if BFD_VERSION >= 234000000
    /* bfd_octets_per_byte() has 2 args since binutils 2.34 */
    dinfo->octets_per_byte = bfd_octets_per_byte (abfd, NULL);
#else
    dinfo->octets_per_byte = bfd_octets_per_byte (abfd);
#endif
    dinfo->skip_zeroes = sizeof(void*) * 2;
    dinfo->skip_zeroes_at_end = sizeof(void*)-1;
    dinfo->disassembler_needs_relocs = FALSE;

    if (bfd_big_endian(abfd))
      dinfo->display_endian = dinfo->endian = BFD_ENDIAN_BIG;
    else if (bfd_little_endian(abfd))
      dinfo->display_endian = dinfo->endian = BFD_ENDIAN_LITTLE;
    else
      dinfo->endian = native_endian();

    disassemble_init_for_target(dinfo);
  }

  /* low-level bfd and arch stuff that binutils doesn't do for us */

  static int read_zero_data_only(bfd_vma ignore_p,
                                 bfd_byte* myaddr, unsigned int length,
                                 struct disassemble_info *ignore_info) {
    memset(myaddr, 0, length);
    return 0;
  }
  static int print_to_dev_null(void* ignore_stream, const char* ignore_format, ...) {
    return 0;
  }

  /* Prime the pump by running the selected disassembler on a null input.
   * This forces the machine-specific disassembler to divulge invariant
   * information like bytes_per_line. */
  void parse_fake_insn(disassembler_ftype dfn,
                       struct disassemble_info* dinfo) {
    typedef int (*read_memory_ftype)
      (bfd_vma memaddr, bfd_byte *myaddr, unsigned int length,
      struct disassemble_info *info);
    read_memory_ftype read_memory_func = dinfo->read_memory_func;
    fprintf_ftype     fprintf_func     = dinfo->fprintf_func;

    dinfo->read_memory_func = &read_zero_data_only;
    dinfo->fprintf_func     = &print_to_dev_null;
    (*dfn)(0, dinfo);

    /* put it back */
    dinfo->read_memory_func = read_memory_func;
    dinfo->fprintf_func     = fprintf_func;
  }


  static int hsdis_read_memory_func(bfd_vma memaddr,
                                    bfd_byte* myaddr,
                                    unsigned int length,
                                    struct disassemble_info* dinfo) {
    hsdis_backend* self = static_cast<hsdis_backend*>(dinfo->application_data);
    /* convert the virtual address memaddr into an address within memory buffer */
    uintptr_t offset = ((uintptr_t) memaddr) - self->_start_va;
    if (offset + length > self->_length) {
      /* read is out of bounds */
      return EIO;
    } else {
      memcpy(myaddr, (bfd_byte*) (self->_buffer + offset), length);
      return 0;
    }
  }

  static void hsdis_print_address_func(bfd_vma vma,
                                       struct disassemble_info* dinfo) {
    hsdis_backend* self = static_cast<hsdis_backend*>(dinfo->application_data);
    /* the actual value to print: */
    void* addr_value = (void*) (uintptr_t) vma;

    /* issue the event: */
    void* result =
      (*self->_event_callback)(self->_event_stream, "addr/", addr_value);
    if (result == NULL) {
      /* event declined */
      generic_print_address(vma, dinfo);
    }
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
                         newline) {
    /* Look into _options for anything interesting. */
    if (options != NULL)
      parse_caller_options(options);

    /* Discover which architecture we are going to disassemble. */
    _arch_name = &_mach_option[0];
    if (_arch_name[0] == '\0')
      _arch_name = native_arch_name();
    _arch_info = find_arch_info(_arch_name);

    /* Make a fake bfd to hold the arch. and byteorder info. */
    struct {
      bfd_target empty_xvec;
      bfd        empty_bfd;
    } buf;
    bfd* native_bfd = get_native_bfd(_arch_info,
                                    /* to avoid malloc: */
                                    &buf.empty_bfd, &buf.empty_xvec);
    memset(&_dinfo, 0, sizeof(_dinfo));
    init_disassemble_info_from_bfd(&_dinfo,
                                  _printf_stream,
                                  _printf_callback,
                                  native_bfd,
                                  /* On PowerPC we get warnings, if we pass empty options */
                                  (options == NULL) ? NULL : _insn_options);

    /* Finish linking together the various callback blocks. */
    _dinfo.application_data = (void*) this;
    _dfn = disassembler(bfd_get_arch(native_bfd),
                                bfd_big_endian(native_bfd),
                                bfd_get_mach(native_bfd),
                                native_bfd);
    _dinfo.print_address_func = hsdis_print_address_func;
    _dinfo.read_memory_func = hsdis_read_memory_func;

    if (_dfn == NULL) {
      const char* bad = _arch_name;
      static bool complained;
      if (bad == &_mach_option[0])
        print_help("bad mach=%s", bad);
      else if (!complained)
        print_help("bad native mach=%s; please port hsdis to this platform", bad);
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }

    parse_fake_insn(_dfn, &_dinfo);
  }

  ~hsdis_backend() {
    // do nothing
  }

 protected:
  virtual void print_help(const char* msg, const char* arg) {
    if (msg != NULL) {
      (*_printf_callback)(_printf_stream, "hsdis: ");
      (*_printf_callback)(_printf_stream, msg, arg);
      (*_printf_callback)(_printf_stream, "\n");
    }
    (*_printf_callback)(_printf_stream, "hsdis output options:\n");
    if (_printf_callback == (printf_callback_t) &fprintf)
      disassembler_usage((FILE*) _printf_stream);
    else
      disassembler_usage(stderr); /* better than nothing */
    (*_printf_callback)(_printf_stream, "  mach=<arch>   select disassembly mode\n");
#if defined(LIBARCH_i386) || defined(LIBARCH_amd64)
    (*_printf_callback)(_printf_stream, "  mach=i386     select 32-bit mode\n");
    (*_printf_callback)(_printf_stream, "  mach=x86-64   select 64-bit mode\n");
    (*_printf_callback)(_printf_stream, "  suffix        always print instruction suffix\n");
#endif
    (*_printf_callback)(_printf_stream, "  help          print this message\n");
  }

  virtual void print_insns_config() {
    (*_event_callback)(_event_stream, "mach name='%s'",
                      (void*) _arch_info->printable_name);
    if (_dinfo.bytes_per_line != 0) {
      (*_event_callback)(_event_stream, "format bytes-per-line='%p'/",
                        (void*)(intptr_t) _dinfo.bytes_per_line);
    }
  }

  virtual size_t decode_instruction(uintptr_t p, uintptr_t start, uintptr_t end) {
    /* reset certain state, so we can read it with confidence */
    _dinfo.insn_info_valid    = 0;
    _dinfo.branch_delay_insns = 0;
    _dinfo.data_size          = 0;
    _dinfo.insn_type          = (dis_insn_type)0;

    return (*_dfn)((bfd_vma) p, &_dinfo);
  }

  virtual const char* format_insn_close(const char* close, char* buf, size_t bufsize) {
    if (!_dinfo.insn_info_valid)
      return close;
    enum dis_insn_type itype = _dinfo.insn_type;
    int dsize = _dinfo.data_size, delays = _dinfo.branch_delay_insns;
    if ((itype == dis_nonbranch && (dsize | delays) == 0)
        || (strlen(close) + 3*20 > bufsize))
      return close;

    const char* type = "unknown";
    switch (itype) {
    case dis_nonbranch:   type = NULL;         break;
    case dis_branch:      type = "branch";     break;
    case dis_condbranch:  type = "condbranch"; break;
    case dis_jsr:         type = "jsr";        break;
    case dis_condjsr:     type = "condjsr";    break;
    case dis_dref:        type = "dref";       break;
    case dis_dref2:       type = "dref2";      break;
    case dis_noninsn:     type = "noninsn";    break;
    }

    strcpy(buf, close);
    char* p = buf;
    if (type)    sprintf(p += strlen(p), " type='%s'", type);
    if (dsize)   sprintf(p += strlen(p), " dsize='%d'", dsize);
    if (delays)  sprintf(p += strlen(p), " delay='%d'", delays);
    return buf;
  }
};

#else

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
#if defined(LIBOS_Linux) && defined(LIBARCH_aarch64)
    return "aarch64-pc-linux-gnu";

#elif defined(LIBOS_Linux) && defined(LIBARCH_amd64)
    return "x86_64-pc-linux-gnu";

#elif defined(LIBOS_Darwin) && defined(LIBARCH_aarch64)
    return "aarch64-apple-darwin";

#elif defined(LIBOS_Darwin) && defined(LIBARCH_x86_64)
    return "x86_64-apple-darwin";

#elif defined(LIBOS_Windows) && defined(LIBARCH_aarch64)
    return "aarch64-pc-windows-msvc";

#elif defined(LIBOS_Windows) && defined(LIBARCH_amd64)
    return "x86_64-pc-windows-msvc";

#else
    #error "unknown platform"
#endif
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

    static bool complained = false;

    if (LLVMInitializeNativeTarget() != 0) {
      if (!complained)
        (*_printf_callback)(_printf_stream, "failed to initialize LLVM native target\n");
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }
    if (LLVMInitializeNativeAsmPrinter() != 0) {
      if (!complained)
        (*_printf_callback)(_printf_stream, "failed to initialize LLVM native asm printer\n");
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }
    if (LLVMInitializeNativeDisassembler() != 0) {
      if (!complained)
        (*_printf_callback)(_printf_stream, "failed to initialize LLVM native disassembler\n");
      complained = true;
      /* must bail out */
      _losing = true;
      return;
    }
    if ((_dcontext = LLVMCreateDisasm(_arch_name, NULL, 0, NULL, NULL)) == NULL) {
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

    LLVMSetDisasmOptions(_dcontext, LLVMDisassembler_Option_PrintImmHex | LLVMDisassembler_Option_AsmPrinterVariant);
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
      _printf_callback(_printf_stream, "%s", buf);
    }
    return size;
  }

  virtual const char* format_insn_close(const char* close, char* buf, size_t bufsize) {
    return close;
  }
};

#endif

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
