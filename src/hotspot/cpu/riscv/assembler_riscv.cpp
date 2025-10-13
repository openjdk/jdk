/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#include <stdio.h>
#include <sys/types.h>

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/sharedRuntime.hpp"

int AbstractAssembler::code_fill_byte() {
  return 0;
}

#ifdef ASSERT

void Address::assert_is_literal() const {
  assert(_mode == literal, "addressing mode is non-literal: %d", _mode);
}

void Address::assert_is_nonliteral() const {
  assert(_mode != literal, "unexpected literal addressing mode");
  assert(_mode != no_mode, "unexpected no_mode addressing mode");
}

#endif // ASSERT

static RelocationHolder address_relocation(address target, relocInfo::relocType rtype) {
  switch (rtype) {
    case relocInfo::oop_type:
    case relocInfo::metadata_type:
      // Oops are a special case. Normally they would be their own section
      // but in cases like icBuffer they are literals in the code stream that
      // we don't have a section for. We use none so that we get a literal address
      // which is always patchable.
      return RelocationHolder::none;
    case relocInfo::external_word_type:
      return external_word_Relocation::spec(target);
    case relocInfo::internal_word_type:
      return internal_word_Relocation::spec(target);
    case relocInfo::opt_virtual_call_type:
      return opt_virtual_call_Relocation::spec();
    case relocInfo::static_call_type:
      return static_call_Relocation::spec();
    case relocInfo::runtime_call_type:
      return runtime_call_Relocation::spec();
    case relocInfo::poll_type:
    case relocInfo::poll_return_type:
      return Relocation::spec_simple(rtype);
    case relocInfo::none:
      return RelocationHolder::none;
    default:
      ShouldNotReachHere();
      return RelocationHolder::none;
  }
}

Address::Address(address target, relocInfo::relocType rtype) :
  _mode(literal),
  _literal(target, address_relocation(target, rtype))
{}
