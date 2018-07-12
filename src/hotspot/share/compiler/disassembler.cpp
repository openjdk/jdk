/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciUtilities.hpp"
#include "classfile/javaClasses.hpp"
#include "code/codeCache.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include CPU_HEADER(depChecker)

void*       Disassembler::_library               = NULL;
bool        Disassembler::_tried_to_load_library = false;

// This routine is in the shared library:
Disassembler::decode_func_virtual Disassembler::_decode_instructions_virtual = NULL;
Disassembler::decode_func Disassembler::_decode_instructions = NULL;

static const char hsdis_library_name[] = "hsdis-" HOTSPOT_LIB_ARCH;
static const char decode_instructions_virtual_name[] = "decode_instructions_virtual";
static const char decode_instructions_name[] = "decode_instructions";
static bool use_new_version = true;
#define COMMENT_COLUMN  40 LP64_ONLY(+8) /*could be an option*/
#define BYTES_COMMENT   ";..."  /* funky byte display comment */

bool Disassembler::load_library() {
  if (_decode_instructions_virtual != NULL || _decode_instructions != NULL) {
    // Already succeeded.
    return true;
  }
  if (_tried_to_load_library) {
    // Do not try twice.
    // To force retry in debugger: assign _tried_to_load_library=0
    return false;
  }
  // Try to load it.
  char ebuf[1024];
  char buf[JVM_MAXPATHLEN];
  os::jvm_path(buf, sizeof(buf));
  int jvm_offset = -1;
  int lib_offset = -1;
#ifdef STATIC_BUILD
  char* p = strrchr(buf, '/');
  *p = '\0';
  strcat(p, "/lib/");
  lib_offset = jvm_offset = strlen(buf);
#else
  {
    // Match "jvm[^/]*" in jvm_path.
    const char* base = buf;
    const char* p = strrchr(buf, *os::file_separator());
    if (p != NULL) lib_offset = p - base + 1;
    p = strstr(p ? p : base, "jvm");
    if (p != NULL) jvm_offset = p - base;
  }
#endif
  // Find the disassembler shared library.
  // Search for several paths derived from libjvm, in this order:
  // 1. <home>/jre/lib/<arch>/<vm>/libhsdis-<arch>.so  (for compatibility)
  // 2. <home>/jre/lib/<arch>/<vm>/hsdis-<arch>.so
  // 3. <home>/jre/lib/<arch>/hsdis-<arch>.so
  // 4. hsdis-<arch>.so  (using LD_LIBRARY_PATH)
  if (jvm_offset >= 0) {
    // 1. <home>/jre/lib/<arch>/<vm>/libhsdis-<arch>.so
    strcpy(&buf[jvm_offset], hsdis_library_name);
    strcat(&buf[jvm_offset], os::dll_file_extension());
    _library = os::dll_load(buf, ebuf, sizeof ebuf);
    if (_library == NULL && lib_offset >= 0) {
      // 2. <home>/jre/lib/<arch>/<vm>/hsdis-<arch>.so
      strcpy(&buf[lib_offset], hsdis_library_name);
      strcat(&buf[lib_offset], os::dll_file_extension());
      _library = os::dll_load(buf, ebuf, sizeof ebuf);
    }
    if (_library == NULL && lib_offset > 0) {
      // 3. <home>/jre/lib/<arch>/hsdis-<arch>.so
      buf[lib_offset - 1] = '\0';
      const char* p = strrchr(buf, *os::file_separator());
      if (p != NULL) {
        lib_offset = p - buf + 1;
        strcpy(&buf[lib_offset], hsdis_library_name);
        strcat(&buf[lib_offset], os::dll_file_extension());
        _library = os::dll_load(buf, ebuf, sizeof ebuf);
      }
    }
  }
  if (_library == NULL) {
    // 4. hsdis-<arch>.so  (using LD_LIBRARY_PATH)
    strcpy(&buf[0], hsdis_library_name);
    strcat(&buf[0], os::dll_file_extension());
    _library = os::dll_load(buf, ebuf, sizeof ebuf);
  }
  if (_library != NULL) {
    _decode_instructions_virtual = CAST_TO_FN_PTR(Disassembler::decode_func_virtual,
                                          os::dll_lookup(_library, decode_instructions_virtual_name));
  }
  if (_decode_instructions_virtual == NULL && _library != NULL) {
    // could not spot in new version, try old version
    _decode_instructions = CAST_TO_FN_PTR(Disassembler::decode_func,
                                          os::dll_lookup(_library, decode_instructions_name));
    use_new_version = false;
  } else {
    use_new_version = true;
  }
  _tried_to_load_library = true;
  if (_decode_instructions_virtual == NULL && _decode_instructions == NULL) {
    tty->print_cr("Could not load %s; %s; %s", buf,
                  ((_library != NULL)
                   ? "entry point is missing"
                   : (WizardMode || PrintMiscellaneous)
                   ? (const char*)ebuf
                   : "library not loadable"),
                  "PrintAssembly is disabled");
    return false;
  }

  // Success.
  tty->print_cr("Loaded disassembler from %s", buf);
  return true;
}


class decode_env {
 private:
  nmethod*      _nm;
  CodeBlob*     _code;
  CodeStrings   _strings;
  outputStream* _output;
  address       _start, _end;
  ptrdiff_t     _offset;

  char          _option_buf[512];
  char          _print_raw;
  bool          _print_pc;
  bool          _print_bytes;
  address       _cur_insn;
  int           _bytes_per_line; // arch-specific formatting option

  static bool match(const char* event, const char* tag) {
    size_t taglen = strlen(tag);
    if (strncmp(event, tag, taglen) != 0)
      return false;
    char delim = event[taglen];
    return delim == '\0' || delim == ' ' || delim == '/' || delim == '=';
  }

  void collect_options(const char* p) {
    if (p == NULL || p[0] == '\0')  return;
    size_t opt_so_far = strlen(_option_buf);
    if (opt_so_far + 1 + strlen(p) + 1 > sizeof(_option_buf))  return;
    char* fillp = &_option_buf[opt_so_far];
    if (opt_so_far > 0) *fillp++ = ',';
    strcat(fillp, p);
    // replace white space by commas:
    char* q = fillp;
    while ((q = strpbrk(q, " \t\n")) != NULL)
      *q++ = ',';
    // Note that multiple PrintAssemblyOptions flags accumulate with \n,
    // which we want to be changed to a comma...
  }

  void print_insn_labels();
  void print_insn_bytes(address pc0, address pc);
  void print_address(address value);

 public:
  decode_env(CodeBlob* code, outputStream* output,
             CodeStrings c = CodeStrings(), ptrdiff_t offset = 0);

  address decode_instructions(address start, address end);

  void start_insn(address pc) {
    _cur_insn = pc;
    output()->bol();
    print_insn_labels();
  }

  void end_insn(address pc) {
    address pc0 = cur_insn();
    outputStream* st = output();
    if (_print_bytes && pc > pc0)
      print_insn_bytes(pc0, pc);
    if (_nm != NULL) {
      _nm->print_code_comment_on(st, COMMENT_COLUMN, pc0, pc);
      // this calls reloc_string_for which calls oop::print_value_on
    }
    // follow each complete insn by a nice newline
    st->cr();
  }

  address handle_event(const char* event, address arg);

  outputStream* output() { return _output; }
  address cur_insn() { return _cur_insn; }
  const char* options() { return _option_buf; }
};

decode_env::decode_env(CodeBlob* code, outputStream* output, CodeStrings c,
                       ptrdiff_t offset) {
  memset(this, 0, sizeof(*this)); // Beware, this zeroes bits of fields.
  _output = output ? output : tty;
  _code = code;
  if (code != NULL && code->is_nmethod())
    _nm = (nmethod*) code;
  _strings.copy(c);
  _offset = offset;

  // by default, output pc but not bytes:
  _print_pc       = true;
  _print_bytes    = false;
  _bytes_per_line = Disassembler::pd_instruction_alignment();

  // parse the global option string:
  collect_options(Disassembler::pd_cpu_opts());
  collect_options(PrintAssemblyOptions);

  if (strstr(options(), "hsdis-")) {
    if (strstr(options(), "hsdis-print-raw"))
      _print_raw = (strstr(options(), "xml") ? 2 : 1);
    if (strstr(options(), "hsdis-print-pc"))
      _print_pc = !_print_pc;
    if (strstr(options(), "hsdis-print-bytes"))
      _print_bytes = !_print_bytes;
  }
  if (strstr(options(), "help")) {
    tty->print_cr("PrintAssemblyOptions help:");
    tty->print_cr("  hsdis-print-raw       test plugin by requesting raw output");
    tty->print_cr("  hsdis-print-raw-xml   test plugin by requesting raw xml");
    tty->print_cr("  hsdis-print-pc        turn off PC printing (on by default)");
    tty->print_cr("  hsdis-print-bytes     turn on instruction byte output");
    tty->print_cr("combined options: %s", options());
  }
}

address decode_env::handle_event(const char* event, address arg) {
  if (match(event, "insn")) {
    start_insn(arg);
  } else if (match(event, "/insn")) {
    end_insn(arg);
  } else if (match(event, "addr")) {
    if (arg != NULL) {
      print_address(arg);
      return arg;
    }
  } else if (match(event, "mach")) {
    static char buffer[32] = { 0, };
    if (strcmp(buffer, (const char*)arg) != 0 ||
        strlen((const char*)arg) > sizeof(buffer) - 1) {
      // Only print this when the mach changes
      strncpy(buffer, (const char*)arg, sizeof(buffer) - 1);
      buffer[sizeof(buffer) - 1] = '\0';
      output()->print_cr("[Disassembling for mach='%s']", arg);
    }
  } else if (match(event, "format bytes-per-line")) {
    _bytes_per_line = (int) (intptr_t) arg;
  } else {
    // ignore unrecognized markup
  }
  return NULL;
}

// called by the disassembler to print out jump targets and data addresses
void decode_env::print_address(address adr) {
  outputStream* st = _output;

  if (adr == NULL) {
    st->print("NULL");
    return;
  }

  int small_num = (int)(intptr_t)adr;
  if ((intptr_t)adr == (intptr_t)small_num
      && -1 <= small_num && small_num <= 9) {
    st->print("%d", small_num);
    return;
  }

  if (Universe::is_fully_initialized()) {
    if (StubRoutines::contains(adr)) {
      StubCodeDesc* desc = StubCodeDesc::desc_for(adr);
      if (desc == NULL) {
        desc = StubCodeDesc::desc_for(adr + frame::pc_return_offset);
      }
      if (desc != NULL) {
        st->print("Stub::%s", desc->name());
        if (desc->begin() != adr) {
          st->print(INTX_FORMAT_W(+) " " PTR_FORMAT, adr - desc->begin(), p2i(adr));
        } else if (WizardMode) {
          st->print(" " PTR_FORMAT, p2i(adr));
        }
        return;
      }
      st->print("Stub::<unknown> " PTR_FORMAT, p2i(adr));
      return;
    }

    BarrierSet* bs = BarrierSet::barrier_set();
    if (bs->is_a(BarrierSet::CardTableBarrierSet) &&
        adr == ci_card_table_address_as<address>()) {
      st->print("word_map_base");
      if (WizardMode) st->print(" " INTPTR_FORMAT, p2i(adr));
      return;
    }
  }

  if (_nm == NULL) {
    // Don't do this for native methods, as the function name will be printed in
    // nmethod::reloc_string_for().
    ResourceMark rm;
    const int buflen = 1024;
    char* buf = NEW_RESOURCE_ARRAY(char, buflen);
    int offset;
    if (os::dll_address_to_function_name(adr, buf, buflen, &offset)) {
      st->print(PTR_FORMAT " = %s",  p2i(adr), buf);
      if (offset != 0) {
        st->print("+%d", offset);
      }
      return;
    }
  }

  // Fall through to a simple (hexadecimal) numeral.
  st->print(PTR_FORMAT, p2i(adr));
}

void decode_env::print_insn_labels() {
  address p = cur_insn();
  outputStream* st = output();
  CodeBlob* cb = _code;
  if (cb != NULL) {
    cb->print_block_comment(st, p);
  }
  _strings.print_block_comment(st, (intptr_t)(p - _start + _offset));
  if (_print_pc) {
    st->print("  " PTR_FORMAT ": ", p2i(p));
  }
}

void decode_env::print_insn_bytes(address pc, address pc_limit) {
  outputStream* st = output();
  size_t incr = 1;
  size_t perline = _bytes_per_line;
  if ((size_t) Disassembler::pd_instruction_alignment() >= sizeof(int)
      && !((uintptr_t)pc % sizeof(int))
      && !((uintptr_t)pc_limit % sizeof(int))) {
    incr = sizeof(int);
    if (perline % incr)  perline += incr - (perline % incr);
  }
  while (pc < pc_limit) {
    // tab to the desired column:
    st->move_to(COMMENT_COLUMN);
    address pc0 = pc;
    address pc1 = pc + perline;
    if (pc1 > pc_limit)  pc1 = pc_limit;
    for (; pc < pc1; pc += incr) {
      if (pc == pc0) {
        st->print(BYTES_COMMENT);
      } else if ((uint)(pc - pc0) % sizeof(int) == 0) {
        st->print(" ");         // put out a space on word boundaries
      }
      if (incr == sizeof(int)) {
        st->print("%08x", *(int*)pc);
      } else {
        st->print("%02x", (*pc)&0xFF);
      }
    }
    st->cr();
  }
}


static void* event_to_env(void* env_pv, const char* event, void* arg) {
  decode_env* env = (decode_env*) env_pv;
  return env->handle_event(event, (address) arg);
}

ATTRIBUTE_PRINTF(2, 3)
static int printf_to_env(void* env_pv, const char* format, ...) {
  decode_env* env = (decode_env*) env_pv;
  outputStream* st = env->output();
  size_t flen = strlen(format);
  const char* raw = NULL;
  if (flen == 0)  return 0;
  if (flen == 1 && format[0] == '\n') { st->bol(); return 1; }
  if (flen < 2 ||
      strchr(format, '%') == NULL) {
    raw = format;
  } else if (format[0] == '%' && format[1] == '%' &&
             strchr(format+2, '%') == NULL) {
    // happens a lot on machines with names like %foo
    flen--;
    raw = format+1;
  }
  if (raw != NULL) {
    st->print_raw(raw, (int) flen);
    return (int) flen;
  }
  va_list ap;
  va_start(ap, format);
  julong cnt0 = st->count();
  st->vprint(format, ap);
  julong cnt1 = st->count();
  va_end(ap);
  return (int)(cnt1 - cnt0);
}

address decode_env::decode_instructions(address start, address end) {
  _start = start; _end = end;

  assert(((((intptr_t)start | (intptr_t)end) % Disassembler::pd_instruction_alignment()) == 0), "misaligned insn addr");

  const int show_bytes = false; // for disassembler debugging

  //_version = Disassembler::pd_cpu_version();

  if (!Disassembler::can_decode()) {
    return NULL;
  }

  // decode a series of instructions and return the end of the last instruction

  if (_print_raw) {
    // Print whatever the library wants to print, w/o fancy callbacks.
    // This is mainly for debugging the library itself.
    FILE* out = stdout;
    FILE* xmlout = (_print_raw > 1 ? out : NULL);
    return use_new_version ?
      (address)
      (*Disassembler::_decode_instructions_virtual)((uintptr_t)start, (uintptr_t)end,
                                                    start, end - start,
                                                    NULL, (void*) xmlout,
                                                    NULL, (void*) out,
                                                    options(), 0/*nice new line*/)
      :
      (address)
      (*Disassembler::_decode_instructions)(start, end,
                                            NULL, (void*) xmlout,
                                            NULL, (void*) out,
                                            options());
  }

  return use_new_version ?
    (address)
    (*Disassembler::_decode_instructions_virtual)((uintptr_t)start, (uintptr_t)end,
                                                  start, end - start,
                                                  &event_to_env,  (void*) this,
                                                  &printf_to_env, (void*) this,
                                                  options(), 0/*nice new line*/)
    :
    (address)
    (*Disassembler::_decode_instructions)(start, end,
                                          &event_to_env,  (void*) this,
                                          &printf_to_env, (void*) this,
                                          options());
}


void Disassembler::decode(CodeBlob* cb, outputStream* st) {
  ttyLocker ttyl;
  if (!load_library())  return;
  if (cb->is_nmethod()) {
    decode((nmethod*)cb, st);
    return;
  }
  decode_env env(cb, st);
  env.output()->print_cr("----------------------------------------------------------------------");
  if (cb->is_aot()) {
    env.output()->print("A ");
    if (cb->is_compiled()) {
      CompiledMethod* cm = (CompiledMethod*)cb;
      env.output()->print("%d ",cm->compile_id());
      cm->method()->method_holder()->name()->print_symbol_on(env.output());
      env.output()->print(".");
      cm->method()->name()->print_symbol_on(env.output());
      cm->method()->signature()->print_symbol_on(env.output());
    } else {
      env.output()->print_cr("%s", cb->name());
    }
  } else {
    env.output()->print_cr("%s", cb->name());
  }
  env.output()->print_cr(" at  [" PTR_FORMAT ", " PTR_FORMAT "]  " JLONG_FORMAT " bytes", p2i(cb->code_begin()), p2i(cb->code_end()), ((jlong)(cb->code_end() - cb->code_begin())) * sizeof(unsigned char*));
  env.decode_instructions(cb->code_begin(), cb->code_end());
}

void Disassembler::decode(address start, address end, outputStream* st, CodeStrings c,
                          ptrdiff_t offset) {
  ttyLocker ttyl;
  if (!load_library())  return;
  decode_env env(CodeCache::find_blob_unsafe(start), st, c, offset);
  env.decode_instructions(start, end);
}

void Disassembler::decode(nmethod* nm, outputStream* st) {
  ttyLocker ttyl;
  if (!load_library())  return;
  decode_env env(nm, st);
  env.output()->print_cr("----------------------------------------------------------------------");

  unsigned char* p   = nm->code_begin();
  unsigned char* end = nm->code_end();

  nm->method()->method_holder()->name()->print_symbol_on(env.output());
  env.output()->print(".");
  nm->method()->name()->print_symbol_on(env.output());
  nm->method()->signature()->print_symbol_on(env.output());
#if INCLUDE_JVMCI
  {
    char buffer[O_BUFLEN];
    char* jvmciName = nm->jvmci_installed_code_name(buffer, O_BUFLEN);
    if (jvmciName != NULL) {
      env.output()->print(" (%s)", jvmciName);
    }
  }
#endif
  env.output()->print_cr("  [" PTR_FORMAT ", " PTR_FORMAT "]  " JLONG_FORMAT " bytes", p2i(p), p2i(end), ((jlong)(end - p)));

  // Print constant table.
  if (nm->consts_size() > 0) {
    nm->print_nmethod_labels(env.output(), nm->consts_begin());
    int offset = 0;
    for (address p = nm->consts_begin(); p < nm->consts_end(); p += 4, offset += 4) {
      if ((offset % 8) == 0) {
        env.output()->print_cr("  " PTR_FORMAT " (offset: %4d): " PTR32_FORMAT "   " PTR64_FORMAT, p2i(p), offset, *((int32_t*) p), *((int64_t*) p));
      } else {
        env.output()->print_cr("  " PTR_FORMAT " (offset: %4d): " PTR32_FORMAT,                    p2i(p), offset, *((int32_t*) p));
      }
    }
  }

  env.decode_instructions(p, end);
}
