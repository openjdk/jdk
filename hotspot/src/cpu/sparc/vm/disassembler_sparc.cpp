/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_disassembler_sparc.cpp.incl"

#ifndef PRODUCT

#define SPARC_VERSION (VM_Version::v9_instructions_work()?              \
                        (VM_Version::v8_instructions_work()? "" : "9") : "8")

// This routine is in the shared library:
typedef unsigned char* print_insn_sparc_t(unsigned char* start, DisassemblerEnv* env,
                                          const char* sparc_version);

void*    Disassembler::_library          = NULL;
dll_func Disassembler::_print_insn_sparc = NULL;

bool Disassembler::load_library() {
  if (_library == NULL) {
    char buf[1024];
    char ebuf[1024];
    sprintf(buf, "disassembler%s", os::dll_file_extension());
    _library = hpi::dll_load(buf, ebuf, sizeof ebuf);
    if (_library != NULL) {
      tty->print_cr("Loaded disassembler");
      _print_insn_sparc = CAST_TO_FN_PTR(dll_func, hpi::dll_lookup(_library, "print_insn_sparc"));
    }
  }
  return (_library != NULL) && (_print_insn_sparc != NULL);
}


class sparc_env : public DisassemblerEnv {
 private:
  nmethod*      code;
  outputStream* output;
  const char*   version;

  static void print_address(address value, outputStream* st);

 public:
  sparc_env(nmethod* rcode, outputStream* routput) {
    code    = rcode;
    output  = routput;
    version = SPARC_VERSION;
  }
  const char* sparc_version() { return version; }
  void print_label(intptr_t value);
  void print_raw(char* str) { output->print_raw(str); }
  void print(char* format, ...);
  char* string_for_offset(intptr_t value);
  char* string_for_constant(unsigned char* pc, intptr_t value, int is_decimal);
};


void sparc_env::print_address(address adr, outputStream* st) {
  if (!Universe::is_fully_initialized()) {
    st->print(INTPTR_FORMAT, (intptr_t)adr);
    return;
  }
  if (StubRoutines::contains(adr)) {
    StubCodeDesc *desc = StubCodeDesc::desc_for(adr);
    if (desc == NULL)
      desc = StubCodeDesc::desc_for(adr + frame::pc_return_offset);
    if (desc == NULL)
      st->print("Unknown stub at " INTPTR_FORMAT, adr);
    else {
      st->print("Stub::%s", desc->name());
      if (desc->begin() != adr)
        st->print("%+d 0x%p",adr - desc->begin(), adr);
      else if (WizardMode) st->print(" " INTPTR_FORMAT, adr);
    }
  } else {
    BarrierSet* bs = Universe::heap()->barrier_set();
    if (bs->kind() == BarrierSet::CardTableModRef &&
        adr == (address)((CardTableModRefBS*)(bs))->byte_map_base) {
      st->print("word_map_base");
      if (WizardMode) st->print(" " INTPTR_FORMAT, (intptr_t)adr);
    } else {
      st->print(INTPTR_FORMAT, (intptr_t)adr);
    }
  }
}


// called by the disassembler to print out jump addresses
void sparc_env::print_label(intptr_t value) {
  print_address((address) value, output);
}

void sparc_env::print(char* format, ...) {
  va_list ap;
  va_start(ap, format);
  output->vprint(format, ap);
  va_end(ap);
}

char* sparc_env::string_for_offset(intptr_t value) {
  stringStream st;
  print_address((address) value, &st);
  return st.as_string();
}

char* sparc_env::string_for_constant(unsigned char* pc, intptr_t value, int is_decimal) {
  stringStream st;
  oop obj;
  if (code && (obj = code->embeddedOop_at(pc)) != NULL) {
    obj->print_value_on(&st);
  } else
  {
    print_address((address) value, &st);
  }
  return st.as_string();
}


address Disassembler::decode_instruction(address start, DisassemblerEnv* env) {
  const char* version = ((sparc_env*)env)->sparc_version();
  return ((print_insn_sparc_t*) _print_insn_sparc)(start, env, version);
}


const int show_bytes = false; // for disassembler debugging


void Disassembler::decode(CodeBlob* cb, outputStream* st) {
  st = st ? st : tty;
  st->print_cr("Decoding CodeBlob " INTPTR_FORMAT, cb);
  decode(cb->instructions_begin(), cb->instructions_end(), st);
}


void Disassembler::decode(u_char* begin, u_char* end, outputStream* st) {
  assert ((((intptr_t)begin | (intptr_t)end) % sizeof(int) == 0), "misaligned insn addr");
  st = st ? st : tty;
  if (!load_library()) {
    st->print_cr("Could not load disassembler");
    return;
  }
  sparc_env env(NULL, st);
  unsigned char*  p = (unsigned char*) begin;
  CodeBlob* cb = CodeCache::find_blob_unsafe(begin);
  while (p < (unsigned char*) end && p) {
    if (cb != NULL) {
      cb->print_block_comment(st, (intptr_t)(p - cb->instructions_begin()));
    }

    unsigned char* p0 = p;
    st->print(INTPTR_FORMAT ": ", p);
    p = decode_instruction(p, &env);
    if (show_bytes && p) {
      st->print("\t\t\t");
      while (p0 < p) { st->print("%08lx ", *(int*)p0); p0 += sizeof(int); }
    }
    st->cr();
  }
}


void Disassembler::decode(nmethod* nm, outputStream* st) {
  st = st ? st : tty;

  st->print_cr("Decoding compiled method " INTPTR_FORMAT ":", nm);
  st->print("Code:");
  st->cr();

  if (!load_library()) {
    st->print_cr("Could not load disassembler");
    return;
  }
  sparc_env env(nm, st);
  unsigned char* p   = nm->instructions_begin();
  unsigned char* end = nm->instructions_end();
  assert ((((intptr_t)p | (intptr_t)end) % sizeof(int) == 0), "misaligned insn addr");

  unsigned char *p1 = p;
  int total_bucket_count = 0;
  while (p1 < end && p1) {
    unsigned char *p0 = p1;
    ++p1;
    address bucket_pc = FlatProfiler::bucket_start_for(p1);
    if (bucket_pc != NULL && bucket_pc > p0 && bucket_pc <= p1)
      total_bucket_count += FlatProfiler::bucket_count_for(p0);
  }

  while (p < end && p) {
    if (p == nm->entry_point())                     st->print_cr("[Entry Point]");
    if (p == nm->verified_entry_point())            st->print_cr("[Verified Entry Point]");
    if (p == nm->exception_begin())                 st->print_cr("[Exception Handler]");
    if (p == nm->stub_begin())                      st->print_cr("[Stub Code]");
    if (p == nm->consts_begin())                    st->print_cr("[Constants]");
    nm->print_block_comment(st, (intptr_t)(p - nm->instructions_begin()));
    unsigned char* p0 = p;
    st->print("  " INTPTR_FORMAT ": ", p);
    p = decode_instruction(p, &env);
    nm->print_code_comment_on(st, 40, p0, p);
    st->cr();
    // Output pc bucket ticks if we have any
    address bucket_pc = FlatProfiler::bucket_start_for(p);
    if (bucket_pc != NULL && bucket_pc > p0 && bucket_pc <= p) {
      int bucket_count = FlatProfiler::bucket_count_for(p0);
      tty->print_cr("%3.1f%% [%d]", bucket_count*100.0/total_bucket_count, bucket_count);
      tty->cr();
    }
  }
}

#endif // PRODUCT
