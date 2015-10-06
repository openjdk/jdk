/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "asm/codeBuffer.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/icache.hpp"
#include "runtime/os.hpp"


// Implementation of AbstractAssembler
//
// The AbstractAssembler is generating code into a CodeBuffer. To make code generation faster,
// the assembler keeps a copy of the code buffers boundaries & modifies them when
// emitting bytes rather than using the code buffers accessor functions all the time.
// The code buffer is updated via set_code_end(...) after emitting a whole instruction.

AbstractAssembler::AbstractAssembler(CodeBuffer* code) {
  if (code == NULL)  return;
  CodeSection* cs = code->insts();
  cs->clear_mark();   // new assembler kills old mark
  if (cs->start() == NULL)  {
    vm_exit_out_of_memory(0, OOM_MMAP_ERROR, "CodeCache: no room for %s", code->name());
  }
  _code_section = cs;
  _oop_recorder= code->oop_recorder();
  DEBUG_ONLY( _short_branch_delta = 0; )
}

void AbstractAssembler::set_code_section(CodeSection* cs) {
  assert(cs->outer() == code_section()->outer(), "sanity");
  assert(cs->is_allocated(), "need to pre-allocate this section");
  cs->clear_mark();  // new assembly into this section kills old mark
  _code_section = cs;
}

// Inform CodeBuffer that incoming code and relocation will be for stubs
address AbstractAssembler::start_a_stub(int required_space) {
  CodeBuffer*  cb = code();
  CodeSection* cs = cb->stubs();
  assert(_code_section == cb->insts(), "not in insts?");
  if (cs->maybe_expand_to_ensure_remaining(required_space)
      && cb->blob() == NULL) {
    return NULL;
  }
  set_code_section(cs);
  return pc();
}

// Inform CodeBuffer that incoming code and relocation will be code
// Should not be called if start_a_stub() returned NULL
void AbstractAssembler::end_a_stub() {
  assert(_code_section == code()->stubs(), "not in stubs?");
  set_code_section(code()->insts());
}

// Inform CodeBuffer that incoming code and relocation will be for stubs
address AbstractAssembler::start_a_const(int required_space, int required_align) {
  CodeBuffer*  cb = code();
  CodeSection* cs = cb->consts();
  assert(_code_section == cb->insts() || _code_section == cb->stubs(), "not in insts/stubs?");
  address end = cs->end();
  int pad = -(intptr_t)end & (required_align-1);
  if (cs->maybe_expand_to_ensure_remaining(pad + required_space)) {
    if (cb->blob() == NULL)  return NULL;
    end = cs->end();  // refresh pointer
  }
  if (pad > 0) {
    while (--pad >= 0) { *end++ = 0; }
    cs->set_end(end);
  }
  set_code_section(cs);
  return end;
}

// Inform CodeBuffer that incoming code and relocation will be code
// in section cs (insts or stubs).
void AbstractAssembler::end_a_const(CodeSection* cs) {
  assert(_code_section == code()->consts(), "not in consts?");
  set_code_section(cs);
}

void AbstractAssembler::flush() {
  ICache::invalidate_range(addr_at(0), offset());
}

void AbstractAssembler::bind(Label& L) {
  if (L.is_bound()) {
    // Assembler can bind a label more than once to the same place.
    guarantee(L.loc() == locator(), "attempt to redefine label");
    return;
  }
  L.bind_loc(locator());
  L.patch_instructions((MacroAssembler*)this);
}

void AbstractAssembler::generate_stack_overflow_check(int frame_size_in_bytes) {
  if (UseStackBanging) {
    // Each code entry causes one stack bang n pages down the stack where n
    // is configurable by StackShadowPages.  The setting depends on the maximum
    // depth of VM call stack or native before going back into java code,
    // since only java code can raise a stack overflow exception using the
    // stack banging mechanism.  The VM and native code does not detect stack
    // overflow.
    // The code in JavaCalls::call() checks that there is at least n pages
    // available, so all entry code needs to do is bang once for the end of
    // this shadow zone.
    // The entry code may need to bang additional pages if the framesize
    // is greater than a page.

    const int page_size = os::vm_page_size();
    int bang_end = StackShadowPages * page_size;

    // This is how far the previous frame's stack banging extended.
    const int bang_end_safe = bang_end;

    if (frame_size_in_bytes > page_size) {
      bang_end += frame_size_in_bytes;
    }

    int bang_offset = bang_end_safe;
    while (bang_offset <= bang_end) {
      // Need at least one stack bang at end of shadow zone.
      bang_stack_with_offset(bang_offset);
      bang_offset += page_size;
    }
  } // end (UseStackBanging)
}

void Label::add_patch_at(CodeBuffer* cb, int branch_loc) {
  assert(_loc == -1, "Label is unbound");
  if (_patch_index < PatchCacheSize) {
    _patches[_patch_index] = branch_loc;
  } else {
    if (_patch_overflow == NULL) {
      _patch_overflow = cb->create_patch_overflow();
    }
    _patch_overflow->push(branch_loc);
  }
  ++_patch_index;
}

void Label::patch_instructions(MacroAssembler* masm) {
  assert(is_bound(), "Label is bound");
  CodeBuffer* cb = masm->code();
  int target_sect = CodeBuffer::locator_sect(loc());
  address target = cb->locator_address(loc());
  while (_patch_index > 0) {
    --_patch_index;
    int branch_loc;
    if (_patch_index >= PatchCacheSize) {
      branch_loc = _patch_overflow->pop();
    } else {
      branch_loc = _patches[_patch_index];
    }
    int branch_sect = CodeBuffer::locator_sect(branch_loc);
    address branch = cb->locator_address(branch_loc);
    if (branch_sect == CodeBuffer::SECT_CONSTS) {
      // The thing to patch is a constant word.
      *(address*)branch = target;
      continue;
    }

#ifdef ASSERT
    // Cross-section branches only work if the
    // intermediate section boundaries are frozen.
    if (target_sect != branch_sect) {
      for (int n = MIN2(target_sect, branch_sect),
               nlimit = (target_sect + branch_sect) - n;
           n < nlimit; n++) {
        CodeSection* cs = cb->code_section(n);
        assert(cs->is_frozen(), "cross-section branch needs stable offsets");
      }
    }
#endif //ASSERT

    // Push the target offset into the branch instruction.
    masm->pd_patch_instruction(branch, target);
  }
}

struct DelayedConstant {
  typedef void (*value_fn_t)();
  BasicType type;
  intptr_t value;
  value_fn_t value_fn;
  // This limit of 20 is generous for initial uses.
  // The limit needs to be large enough to store the field offsets
  // into classes which do not have statically fixed layouts.
  // (Initial use is for method handle object offsets.)
  // Look for uses of "delayed_value" in the source code
  // and make sure this number is generous enough to handle all of them.
  enum { DC_LIMIT = 20 };
  static DelayedConstant delayed_constants[DC_LIMIT];
  static DelayedConstant* add(BasicType type, value_fn_t value_fn);
  bool match(BasicType t, value_fn_t cfn) {
    return type == t && value_fn == cfn;
  }
  static void update_all();
};

DelayedConstant DelayedConstant::delayed_constants[DC_LIMIT];
// Default C structure initialization rules have the following effect here:
// = { { (BasicType)0, (intptr_t)NULL }, ... };

DelayedConstant* DelayedConstant::add(BasicType type,
                                      DelayedConstant::value_fn_t cfn) {
  for (int i = 0; i < DC_LIMIT; i++) {
    DelayedConstant* dcon = &delayed_constants[i];
    if (dcon->match(type, cfn))
      return dcon;
    if (dcon->value_fn == NULL) {
      // (cmpxchg not because this is multi-threaded but because I'm paranoid)
      if (Atomic::cmpxchg_ptr(CAST_FROM_FN_PTR(void*, cfn), &dcon->value_fn, NULL) == NULL) {
        dcon->type = type;
        return dcon;
      }
    }
  }
  // If this assert is hit (in pre-integration testing!) then re-evaluate
  // the comment on the definition of DC_LIMIT.
  guarantee(false, "too many delayed constants");
  return NULL;
}

void DelayedConstant::update_all() {
  for (int i = 0; i < DC_LIMIT; i++) {
    DelayedConstant* dcon = &delayed_constants[i];
    if (dcon->value_fn != NULL && dcon->value == 0) {
      typedef int     (*int_fn_t)();
      typedef address (*address_fn_t)();
      switch (dcon->type) {
      case T_INT:     dcon->value = (intptr_t) ((int_fn_t)    dcon->value_fn)(); break;
      case T_ADDRESS: dcon->value = (intptr_t) ((address_fn_t)dcon->value_fn)(); break;
      }
    }
  }
}

RegisterOrConstant AbstractAssembler::delayed_value(int(*value_fn)(), Register tmp, int offset) {
  intptr_t val = (intptr_t) (*value_fn)();
  if (val != 0)  return val + offset;
  return delayed_value_impl(delayed_value_addr(value_fn), tmp, offset);
}
RegisterOrConstant AbstractAssembler::delayed_value(address(*value_fn)(), Register tmp, int offset) {
  intptr_t val = (intptr_t) (*value_fn)();
  if (val != 0)  return val + offset;
  return delayed_value_impl(delayed_value_addr(value_fn), tmp, offset);
}
intptr_t* AbstractAssembler::delayed_value_addr(int(*value_fn)()) {
  DelayedConstant* dcon = DelayedConstant::add(T_INT, (DelayedConstant::value_fn_t) value_fn);
  return &dcon->value;
}
intptr_t* AbstractAssembler::delayed_value_addr(address(*value_fn)()) {
  DelayedConstant* dcon = DelayedConstant::add(T_ADDRESS, (DelayedConstant::value_fn_t) value_fn);
  return &dcon->value;
}
void AbstractAssembler::update_delayed_values() {
  DelayedConstant::update_all();
}

void AbstractAssembler::block_comment(const char* comment) {
  if (sect() == CodeBuffer::SECT_INSTS) {
    code_section()->outer()->block_comment(offset(), comment);
  }
}

const char* AbstractAssembler::code_string(const char* str) {
  if (sect() == CodeBuffer::SECT_INSTS || sect() == CodeBuffer::SECT_STUBS) {
    return code_section()->outer()->code_string(str);
  }
  return NULL;
}

bool MacroAssembler::needs_explicit_null_check(intptr_t offset) {
  // Exception handler checks the nmethod's implicit null checks table
  // only when this method returns false.
#ifdef _LP64
  if (UseCompressedOops && Universe::narrow_oop_base() != NULL) {
    assert (Universe::heap() != NULL, "java heap should be initialized");
    // The first page after heap_base is unmapped and
    // the 'offset' is equal to [heap_base + offset] for
    // narrow oop implicit null checks.
    uintptr_t base = (uintptr_t)Universe::narrow_oop_base();
    if ((uintptr_t)offset >= base) {
      // Normalize offset for the next check.
      offset = (intptr_t)(pointer_delta((void*)offset, (void*)base, 1));
    }
  }
#endif
  return offset < 0 || os::vm_page_size() <= offset;
}
