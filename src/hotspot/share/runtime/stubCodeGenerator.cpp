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

#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
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
  _print_code = PrintStubCode || print_code;
}

StubCodeGenerator::StubCodeGenerator(CodeBuffer* code, BlobId blob_id, bool print_code) {
  assert(StubInfo::is_stubgen(blob_id),
         "not a stubgen blob %s", StubInfo::name(blob_id));
  _masm = new MacroAssembler(code);
  _blob_id = blob_id;
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

void StubCodeGenerator::stub_prolog(StubCodeDesc* cdesc) {
  // default implementation - do nothing
}

void StubCodeGenerator::stub_epilog(StubCodeDesc* cdesc) {
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
