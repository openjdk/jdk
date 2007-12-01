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
# include "incls/_disassembler_x86.cpp.incl"

#ifndef PRODUCT

void*    Disassembler::_library            = NULL;
Disassembler::decode_func Disassembler::_decode_instruction = NULL;

bool Disassembler::load_library() {
  if (_library == NULL) {
    char buf[1024];
    char ebuf[1024];
    sprintf(buf, "disassembler%s", os::dll_file_extension());
    _library = hpi::dll_load(buf, ebuf, sizeof ebuf);
    if (_library != NULL) {
      tty->print_cr("Loaded disassembler");
      _decode_instruction = CAST_TO_FN_PTR(Disassembler::decode_func, hpi::dll_lookup(_library, "decode_instruction"));
    }
  }
  return (_library != NULL) && (_decode_instruction != NULL);
}

class x86_env : public DisassemblerEnv {
 private:
  nmethod*      code;
  outputStream* output;
 public:
  x86_env(nmethod* rcode, outputStream* routput) {
    code   = rcode;
    output = routput;
  }
  void print_label(intptr_t value);
  void print_raw(char* str) { output->print_raw(str); }
  void print(char* format, ...);
  char* string_for_offset(intptr_t value);
  char* string_for_constant(unsigned char* pc, intptr_t value, int is_decimal);
};


void x86_env::print_label(intptr_t value) {
  if (!Universe::is_fully_initialized()) {
    output->print(INTPTR_FORMAT, value);
    return;
  }
  address adr = (address) value;
  if (StubRoutines::contains(adr)) {
    StubCodeDesc* desc = StubCodeDesc::desc_for(adr);
    const char * desc_name = "unknown stub";
    if (desc != NULL) {
      desc_name = desc->name();
    }
    output->print("Stub::%s", desc_name);
    if (WizardMode) output->print(" " INTPTR_FORMAT, value);
  } else {
    output->print(INTPTR_FORMAT, value);
  }
}

void x86_env::print(char* format, ...) {
  va_list ap;
  va_start(ap, format);
  output->vprint(format, ap);
  va_end(ap);
}

char* x86_env::string_for_offset(intptr_t value) {
  stringStream st;
  if (!Universe::is_fully_initialized()) {
    st.print(INTX_FORMAT, value);
    return st.as_string();
  }
  BarrierSet* bs = Universe::heap()->barrier_set();
  BarrierSet::Name bsn = bs->kind();
  if (bs->kind() == BarrierSet::CardTableModRef &&
      (jbyte*) value == ((CardTableModRefBS*)(bs))->byte_map_base) {
    st.print("word_map_base");
  } else {
    st.print(INTX_FORMAT, value);
  }
  return st.as_string();
}

char* x86_env::string_for_constant(unsigned char* pc, intptr_t value, int is_decimal) {
  stringStream st;
  oop obj = NULL;
  if (code && ((obj = code->embeddedOop_at(pc)) != NULL)) {
    obj->print_value_on(&st);
  } else {
    if (is_decimal == 1) {
      st.print(INTX_FORMAT, value);
    } else {
      st.print(INTPTR_FORMAT, value);
    }
  }
  return st.as_string();
}



address Disassembler::decode_instruction(address start, DisassemblerEnv* env) {
  return ((decode_func) _decode_instruction)(start, env);
}


void Disassembler::decode(CodeBlob* cb, outputStream* st) {
  st = st ? st : tty;
  st->print_cr("Decoding CodeBlob " INTPTR_FORMAT, cb);
  decode(cb->instructions_begin(), cb->instructions_end(), st);
}


void Disassembler::decode(u_char* begin, u_char* end, outputStream* st) {
  st = st ? st : tty;

  const int show_bytes = false; // for disassembler debugging

  if (!load_library()) {
    st->print_cr("Could not load disassembler");
    return;
  }

  x86_env env(NULL, st);
  unsigned char*  p = (unsigned char*) begin;
  CodeBlob* cb = CodeCache::find_blob_unsafe(begin);
  while (p < (unsigned char*) end) {
    if (cb != NULL) {
      cb->print_block_comment(st, (intptr_t)(p - cb->instructions_begin()));
    }

    unsigned char* p0 = p;
    st->print("  " INTPTR_FORMAT ": ", p);
    p = decode_instruction(p, &env);
    if (show_bytes) {
      st->print("\t\t\t");
      while (p0 < p) st->print("%x ", *p0++);
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
  x86_env env(nm, st);
  unsigned char* p = nm->instructions_begin();
  unsigned char* end = nm->instructions_end();
  while (p < end) {
    if (p == nm->entry_point())             st->print_cr("[Entry Point]");
    if (p == nm->verified_entry_point())    st->print_cr("[Verified Entry Point]");
    if (p == nm->exception_begin())         st->print_cr("[Exception Handler]");
    if (p == nm->stub_begin())              st->print_cr("[Stub Code]");
    if (p == nm->consts_begin())            st->print_cr("[Constants]");
    nm->print_block_comment(st, (intptr_t)(p - nm->instructions_begin()));
    unsigned char* p0 = p;
    st->print("  " INTPTR_FORMAT ": ", p);
    p = decode_instruction(p, &env);
    nm->print_code_comment_on(st, 40, p0, p);
    st->cr();
    // Output pc bucket ticks if we have any
    address bucket_pc = FlatProfiler::bucket_start_for(p);
    if (bucket_pc != NULL && bucket_pc > p0 && bucket_pc <= p) {
      int bucket_count = FlatProfiler::bucket_count_for(bucket_pc);
      tty->print_cr("[%d]", bucket_count);
    }
  }
}

#endif // PRODUCT
