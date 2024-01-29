/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/method.hpp"
#include "oops/resolvedMethodEntry.hpp"

bool ResolvedMethodEntry::check_no_old_or_obsolete_entry() {
  // return false if m refers to a non-deleted old or obsolete method
  if (_method != nullptr) {
    assert(_method->is_valid() && _method->is_method(), "m is a valid method");
    return !_method->is_old() && !_method->is_obsolete(); // old is always set for old and obsolete
  } else {
    return true;
  }
}

void ResolvedMethodEntry::reset_entry() {
  if (has_resolved_references_index()) {
    u2 saved_resolved_references_index = _entry_specific._resolved_references_index;
    u2 saved_cpool_index = _cpool_index;
    memset(this, 0, sizeof(*this));
    set_resolved_references_index(saved_resolved_references_index);
    _cpool_index = saved_cpool_index;
  } else {
    u2 saved_cpool_index = _cpool_index;
    memset(this, 0, sizeof(*this));
    _cpool_index = saved_cpool_index;
  }
}

void ResolvedMethodEntry::remove_unshareable_info() {
  reset_entry();
}

void ResolvedMethodEntry::print_on(outputStream* st) const {
  st->print_cr("Method Entry:");

  if (method() != nullptr) {
    st->print_cr(" - Method: " INTPTR_FORMAT " %s", p2i(method()), method()->external_name());
  } else {
    st->print_cr("- Method: null");
  }
  // Some fields are mutually exclusive and are only used by certain invoke codes
  if (bytecode1() == Bytecodes::_invokeinterface && interface_klass() != nullptr) {
    st->print_cr(" - Klass: " INTPTR_FORMAT " %s", p2i(interface_klass()), interface_klass()->external_name());
  } else {
    st->print_cr("- Klass: null");
  }
  if (bytecode1() == Bytecodes::_invokehandle) {
    st->print_cr(" - Resolved References Index: %d", resolved_references_index());
  } else {
    st->print_cr(" - Resolved References Index: none");
  }
  if (bytecode2() == Bytecodes::_invokevirtual) {
#ifdef ASSERT
    if (_has_table_index) {
      st->print_cr(" - Table Index: %d", table_index());
    }
#else
    st->print_cr(" - Table Index: %d", table_index());
#endif
  } else {
    st->print_cr(" - Table Index: none");
  }
  st->print_cr(" - CP Index: %d", constant_pool_index());
  st->print_cr(" - TOS: %s", type2name(as_BasicType((TosState)tos_state())));
  st->print_cr(" - Number of Parameters: %d", number_of_parameters());
  st->print_cr(" - Is Virtual Final: %d", is_vfinal());
  st->print_cr(" - Is Final: %d", is_final());
  st->print_cr(" - Is Forced Virtual: %d", is_forced_virtual());
  st->print_cr(" - Has Appendix: %d", has_appendix());
  st->print_cr(" - Has Local Signature: %d", has_local_signature());
  st->print_cr(" - Bytecode 1: %s", Bytecodes::name((Bytecodes::Code)bytecode1()));
  st->print_cr(" - Bytecode 2: %s", Bytecodes::name((Bytecodes::Code)bytecode2()));
}
