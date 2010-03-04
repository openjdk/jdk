/*
 * Copyright 1997-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_pcDesc.cpp.incl"

PcDesc::PcDesc(int pc_offset, int scope_decode_offset, int obj_decode_offset) {
  assert(sizeof(PcDescFlags) <= 4, "occupies more than a word");
  _pc_offset           = pc_offset;
  _scope_decode_offset = scope_decode_offset;
  _obj_decode_offset   = obj_decode_offset;
  _flags.word          = 0;
}

address PcDesc::real_pc(const nmethod* code) const {
  return code->instructions_begin() + pc_offset();
}

void PcDesc::print(nmethod* code) {
#ifndef PRODUCT
  ResourceMark rm;
  tty->print_cr("PcDesc(pc=0x%lx offset=%x):", real_pc(code), pc_offset());

  if (scope_decode_offset() == DebugInformationRecorder::serialized_null) {
    return;
  }

  for (ScopeDesc* sd = code->scope_desc_at(real_pc(code));
       sd != NULL;
       sd = sd->sender()) {
    tty->print("  ");
    sd->method()->print_short_name(tty);
    tty->print("  @%d", sd->bci());
    if (sd->should_reexecute())
      tty->print("  reexecute=true");
    tty->cr();
  }
#endif
}

bool PcDesc::verify(nmethod* code) {
  //Unimplemented();
  return true;
}
