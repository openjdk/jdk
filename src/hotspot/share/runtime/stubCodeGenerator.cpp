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

#include "asm/macroAssembler.inline.hpp"
#include "code/aotCodeCache.hpp"
#include "code/codeCache.hpp"
#include "compiler/disassembler.hpp"
#include "oops/oop.inline.hpp"
#include "prims/forte.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"


// Implementation of StubCodeDesc

StubCodeDesc* StubCodeDesc::_list = nullptr;
bool          StubCodeDesc::_frozen = false;

StubCodeDesc* StubCodeDesc::desc_for(address pc) {
  StubCodeDesc* p = _list;
  while (p != nullptr && !p->contains(pc)) {
    p = p->_next;
  }
  return p;
}

void StubCodeDesc::freeze() {
  assert(!_frozen, "repeated freeze operation");
  _frozen = true;
}

void StubCodeDesc::unfreeze() {
  assert(_frozen, "repeated unfreeze operation");
  _frozen = false;
}

void StubCodeDesc::print_on(outputStream* st) const {
  st->print("%s", group());
  st->print("::");
  st->print("%s", name());
  st->print(" [" INTPTR_FORMAT ", " INTPTR_FORMAT "] (%d bytes)", p2i(begin()), p2i(end()), size_in_bytes());
}

void StubCodeDesc::print() const { print_on(tty); }

// Implementation of StubCodeGenerator

StubCodeGenerator::StubCodeGenerator(CodeBuffer* code, bool print_code) {
  _masm = new MacroAssembler(code);
  _blob_id = BlobId::NO_BLOBID;
  _stub_data = nullptr;
  _print_code = PrintStubCode || print_code;
}

StubCodeGenerator::StubCodeGenerator(CodeBuffer* code, BlobId blob_id, AOTStubData* stub_data, bool print_code) {
  assert(StubInfo::is_stubgen(blob_id),
         "not a stubgen blob %s", StubInfo::name(blob_id));
  _masm = new MacroAssembler(code);
  _blob_id = blob_id;
  _stub_data = stub_data;
  _print_code = PrintStubCode || print_code;
}

StubCodeGenerator::~StubCodeGenerator() {
#ifndef PRODUCT
  CodeBuffer* cbuf = _masm->code();
  CodeBlob*   blob = CodeCache::find_blob(cbuf->insts()->start());
  if (blob != nullptr) {
    blob->use_remarks(cbuf->asm_remarks());
    blob->use_strings(cbuf->dbg_strings());
  }
#endif
}

void StubCodeGenerator::setup_code_desc(const char* name, address start, address end, bool loaded_from_cache) {
  StubCodeDesc* cdesc = new StubCodeDesc("StubRoutines", name, start, end);
  cdesc->set_disp(uint(start - _masm->code_section()->outer()->insts_begin()));
  if (loaded_from_cache) {
    cdesc->set_loaded_from_cache();
  }
  print_stub_code_desc(cdesc);
  // copied from ~StubCodeMark()
  Forte::register_stub(cdesc->name(), cdesc->begin(), cdesc->end());
  if (JvmtiExport::should_post_dynamic_code_generated()) {
    JvmtiExport::post_dynamic_code_generated(cdesc->name(), cdesc->begin(), cdesc->end());
  }
}

// Helper used to restore ranges and handler addresses restored from
// AOT cache. Expects entries to contain 3 * count addresses beginning
// at offset begin which identify start of range, end of range and
// address of handler pc. start and end of range may not be null.
// handler pc may be null in which case it defaults to the
// default_handler.

void StubCodeGenerator::register_unsafe_access_handlers(GrowableArray<address> &entries, int begin, int count) {
  for (int i = 0; i < count; i++) {
    int offset = begin + 3 * i;
    address start = entries.at(offset);
    address end = entries.at(offset + 1);
    address handler = entries.at(offset + 2);
    assert(start != nullptr, "sanity");
    assert(end != nullptr, "sanity");
    if (handler == nullptr) {
      assert(UnsafeMemoryAccess::common_exit_stub_pc() != nullptr,
             "default unsafe handler must be set before registering unsafe rgeionwiht no handler!");
      handler = UnsafeMemoryAccess::common_exit_stub_pc();
    }
    UnsafeMemoryAccess::add_to_table(start, end, handler);
  }
}

// Helper used to retrieve ranges and handler addresses registered
// during generation of the stub which spans [start, end) in order to
// allow them to be saved to an AOT cache.
void StubCodeGenerator::retrieve_unsafe_access_handlers(address start, address end, GrowableArray<address> &entries) {
  UnsafeMemoryAccess::collect_entries(start, end, entries);
}

void StubCodeGenerator::stub_prolog(StubCodeDesc* cdesc) {
  // default implementation - do nothing
}

void StubCodeGenerator::stub_epilog(StubCodeDesc* cdesc) {
  print_stub_code_desc(cdesc);
}

void StubCodeGenerator::print_stub_code_desc(StubCodeDesc* cdesc) {
  LogTarget(Debug, stubs) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    cdesc->print_on(&ls);
    ls.cr();
  }

  if (_print_code) {
#ifndef PRODUCT
    // Find the assembly code remarks in the outer CodeBuffer.
    AsmRemarks* remarks = &_masm->code_section()->outer()->asm_remarks();
#endif
    ttyLocker ttyl;
    tty->print_cr("- - - [BEGIN] - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
    cdesc->print_on(tty);
    tty->cr();
    Disassembler::decode(cdesc->begin(), cdesc->end(), tty
                         NOT_PRODUCT(COMMA remarks COMMA cdesc->disp()));
    tty->print_cr("- - - [END] - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
    tty->cr();
  }
}

address StubCodeGenerator::load_archive_data(StubId stub_id, GrowableArray<address> *entries, GrowableArray<address>* extras) {
  // punt to stub data if it exists and is not for dumping
  if (_stub_data == nullptr || _stub_data->is_dumping()) {
    return nullptr;
  }
  // punt to stub data
  address start, end;
  start = _stub_data->load_archive_data(stub_id, end, entries, extras);

  if (start != nullptr) {
    setup_code_desc(StubInfo::name(stub_id), start, end, true);
  }

  return start;
}

void StubCodeGenerator::store_archive_data(StubId stub_id, address start, address end, GrowableArray<address>* entries, GrowableArray<address>* extras) {
  // punt to stub data if we have any
  if (_stub_data != nullptr) {
    _stub_data->store_archive_data(stub_id, start, end, entries, extras);
  }
}

void StubCodeGenerator::print_statistics_on(outputStream* st) {
  st->print_cr("StubRoutines Stubs:");
  st->print_cr("  Initial stubs:         %d", StubInfo::stub_count(BlobId::stubgen_initial_id));
  st->print_cr("  Continuation stubs:    %d", StubInfo::stub_count(BlobId::stubgen_continuation_id));
  st->print_cr("  Compiler stubs:        %d", StubInfo::stub_count(BlobId::stubgen_compiler_id));
  st->print_cr("  Final stubs:           %d", StubInfo::stub_count(BlobId::stubgen_final_id));

  int emitted = 0;
  int loaded_from_cache = 0;

  StubCodeDesc* scd = StubCodeDesc::first();
  while (scd != nullptr) {
    if (!strcmp(scd->group(), "StubRoutines")) {
      emitted += 1;
      if (scd->loaded_from_cache()) {
        loaded_from_cache += 1;
      }
    }
    scd = StubCodeDesc::next(scd);
  }
  st->print_cr("Total stubroutines stubs emitted: %d (generated=%d, loaded from cache=%d)", emitted, emitted - loaded_from_cache, loaded_from_cache);
}

#ifdef ASSERT
void StubCodeGenerator::verify_stub(StubId stub_id) {
  assert(StubRoutines::stub_to_blob(stub_id) == blob_id(), "wrong blob %s for generation of stub %s", StubRoutines::get_blob_name(blob_id()), StubRoutines::get_stub_name(stub_id));
}
#endif

// Implementation of CodeMark

StubCodeMark::StubCodeMark(StubCodeGenerator* cgen, const char* group, const char* name) {
  _cgen  = cgen;
  _cdesc = new StubCodeDesc(group, name, _cgen->assembler()->pc());
  _cgen->stub_prolog(_cdesc);
  // define the stub's beginning (= entry point) to be after the prolog:
  _cdesc->set_begin(_cgen->assembler()->pc());
}

StubCodeMark::StubCodeMark(StubCodeGenerator* cgen, StubId stub_id) : StubCodeMark(cgen, "StubRoutines", StubRoutines::get_stub_name(stub_id)) {
#ifdef ASSERT
  cgen->verify_stub(stub_id);
#endif
}

StubCodeMark::~StubCodeMark() {
  _cgen->assembler()->flush();
  _cdesc->set_end(_cgen->assembler()->pc());
  assert(StubCodeDesc::_list == _cdesc, "expected order on list");
#ifndef PRODUCT
  address base = _cgen->assembler()->code_section()->outer()->insts_begin();
  address head = _cdesc->begin();
  _cdesc->set_disp(uint(head - base));
#endif
  _cgen->stub_epilog(_cdesc);
  Forte::register_stub(_cdesc->name(), _cdesc->begin(), _cdesc->end());

  if (JvmtiExport::should_post_dynamic_code_generated()) {
    JvmtiExport::post_dynamic_code_generated(_cdesc->name(), _cdesc->begin(), _cdesc->end());
  }
}
