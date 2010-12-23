/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/oop.inline.hpp"
#include "oops/symbolOop.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "prims/methodComparator.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/globalDefinitions.hpp"

BytecodeStream *MethodComparator::_s_old;
BytecodeStream *MethodComparator::_s_new;
constantPoolOop MethodComparator::_old_cp;
constantPoolOop MethodComparator::_new_cp;
BciMap *MethodComparator::_bci_map;
bool MethodComparator::_switchable_test;
GrowableArray<int> *MethodComparator::_fwd_jmps;

bool MethodComparator::methods_EMCP(methodOop old_method, methodOop new_method) {
  if (old_method->code_size() != new_method->code_size())
    return false;
  if (check_stack_and_locals_size(old_method, new_method) != 0) {
    // RC_TRACE macro has an embedded ResourceMark
    RC_TRACE(0x00800000, ("Methods %s non-comparable with diagnosis %d",
      old_method->name()->as_C_string(),
      check_stack_and_locals_size(old_method, new_method)));
    return false;
  }

  _old_cp = old_method->constants();
  _new_cp = new_method->constants();
  BytecodeStream s_old(old_method);
  BytecodeStream s_new(new_method);
  _s_old = &s_old;
  _s_new = &s_new;
  _switchable_test = false;
  Bytecodes::Code c_old, c_new;

  while ((c_old = s_old.next()) >= 0) {
    if ((c_new = s_new.next()) < 0 || c_old != c_new)
      return false;

    if (! args_same(c_old, c_new))
      return false;
  }
  return true;
}


bool MethodComparator::methods_switchable(methodOop old_method, methodOop new_method,
                                          BciMap &bci_map) {
  if (old_method->code_size() > new_method->code_size())
    // Something has definitely been deleted in the new method, compared to the old one.
    return false;

  if (! check_stack_and_locals_size(old_method, new_method))
    return false;

  _old_cp = old_method->constants();
  _new_cp = new_method->constants();
  BytecodeStream s_old(old_method);
  BytecodeStream s_new(new_method);
  _s_old = &s_old;
  _s_new = &s_new;
  _bci_map = &bci_map;
  _switchable_test = true;
  GrowableArray<int> fwd_jmps(16);
  _fwd_jmps = &fwd_jmps;
  Bytecodes::Code c_old, c_new;

  while ((c_old = s_old.next()) >= 0) {
    if ((c_new = s_new.next()) < 0)
      return false;
    if (! (c_old == c_new && args_same(c_old, c_new))) {
      int old_bci = s_old.bci();
      int new_st_bci = s_new.bci();
      bool found_match = false;
      do {
        c_new = s_new.next();
        if (c_new == c_old && args_same(c_old, c_new)) {
          found_match = true;
          break;
        }
      } while (c_new >= 0);
      if (! found_match)
        return false;
      int new_end_bci = s_new.bci();
      bci_map.store_fragment_location(old_bci, new_st_bci, new_end_bci);
    }
  }

  // Now we can test all forward jumps
  for (int i = 0; i < fwd_jmps.length() / 2; i++) {
    if (! bci_map.old_and_new_locations_same(fwd_jmps.at(i*2), fwd_jmps.at(i*2+1))) {
      RC_TRACE(0x00800000,
        ("Fwd jump miss: old dest = %d, calc new dest = %d, act new dest = %d",
        fwd_jmps.at(i*2), bci_map.new_bci_for_old(fwd_jmps.at(i*2)),
        fwd_jmps.at(i*2+1)));
      return false;
    }
  }

  return true;
}


bool MethodComparator::args_same(Bytecodes::Code c_old, Bytecodes::Code c_new) {
  // BytecodeStream returns the correct standard Java bytecodes for various "fast"
  // bytecode versions, so we don't have to bother about them here..
  switch (c_old) {
  case Bytecodes::_new            : // fall through
  case Bytecodes::_anewarray      : // fall through
  case Bytecodes::_multianewarray : // fall through
  case Bytecodes::_checkcast      : // fall through
  case Bytecodes::_instanceof     : {
    u2 cpi_old = _s_old->get_index_u2();
    u2 cpi_new = _s_new->get_index_u2();
    if ((_old_cp->klass_at_noresolve(cpi_old) != _new_cp->klass_at_noresolve(cpi_new)))
        return false;
    if (c_old == Bytecodes::_multianewarray &&
        *(jbyte*)(_s_old->bcp() + 3) != *(jbyte*)(_s_new->bcp() + 3))
      return false;
    break;
  }

  case Bytecodes::_getstatic       : // fall through
  case Bytecodes::_putstatic       : // fall through
  case Bytecodes::_getfield        : // fall through
  case Bytecodes::_putfield        : // fall through
  case Bytecodes::_invokevirtual   : // fall through
  case Bytecodes::_invokespecial   : // fall through
  case Bytecodes::_invokestatic    : // fall through
  case Bytecodes::_invokeinterface : {
    int cpci_old = _s_old->get_index_u2_cpcache();
    int cpci_new = _s_new->get_index_u2_cpcache();
    // Check if the names of classes, field/method names and signatures at these indexes
    // are the same. Indices which are really into constantpool cache (rather than constant
    // pool itself) are accepted by the constantpool query routines below.
    if ((_old_cp->klass_ref_at_noresolve(cpci_old) != _new_cp->klass_ref_at_noresolve(cpci_new)) ||
        (_old_cp->name_ref_at(cpci_old) != _new_cp->name_ref_at(cpci_new)) ||
        (_old_cp->signature_ref_at(cpci_old) != _new_cp->signature_ref_at(cpci_new)))
      return false;
    break;
  }
  case Bytecodes::_invokedynamic: {
    int cpci_old = _s_old->get_index_u4();
    int cpci_new = _s_new->get_index_u4();
    // Check if the names of classes, field/method names and signatures at these indexes
    // are the same. Indices which are really into constantpool cache (rather than constant
    // pool itself) are accepted by the constantpool query routines below.
    if ((_old_cp->name_ref_at(cpci_old) != _new_cp->name_ref_at(cpci_new)) ||
        (_old_cp->signature_ref_at(cpci_old) != _new_cp->signature_ref_at(cpci_new)))
      return false;
    int cpi_old = _old_cp->cache()->main_entry_at(cpci_old)->constant_pool_index();
    int cpi_new = _new_cp->cache()->main_entry_at(cpci_new)->constant_pool_index();
    int bsm_old = _old_cp->invoke_dynamic_bootstrap_method_ref_index_at(cpi_old);
    int bsm_new = _new_cp->invoke_dynamic_bootstrap_method_ref_index_at(cpi_new);
    if (!pool_constants_same(bsm_old, bsm_new))
      return false;
    int cnt_old = _old_cp->invoke_dynamic_argument_count_at(cpi_old);
    int cnt_new = _new_cp->invoke_dynamic_argument_count_at(cpi_new);
    if (cnt_old != cnt_new)
      return false;
    for (int arg_i = 0; arg_i < cnt_old; arg_i++) {
      int idx_old = _old_cp->invoke_dynamic_argument_index_at(cpi_old, arg_i);
      int idx_new = _new_cp->invoke_dynamic_argument_index_at(cpi_new, arg_i);
      if (!pool_constants_same(idx_old, idx_new))
        return false;
    }
    break;
  }

  case Bytecodes::_ldc   : // fall through
  case Bytecodes::_ldc_w : {
    Bytecode_loadconstant* ldc_old = Bytecode_loadconstant_at(_s_old->method(), _s_old->bci());
    Bytecode_loadconstant* ldc_new = Bytecode_loadconstant_at(_s_new->method(), _s_new->bci());
    int cpi_old = ldc_old->pool_index();
    int cpi_new = ldc_new->pool_index();
    if (!pool_constants_same(cpi_old, cpi_new))
      return false;
    break;
  }

  case Bytecodes::_ldc2_w : {
    u2 cpi_old = _s_old->get_index_u2();
    u2 cpi_new = _s_new->get_index_u2();
    constantTag tag_old = _old_cp->tag_at(cpi_old);
    constantTag tag_new = _new_cp->tag_at(cpi_new);
    if (tag_old.value() != tag_new.value())
      return false;
    if (tag_old.is_long()) {
      if (_old_cp->long_at(cpi_old) != _new_cp->long_at(cpi_new))
        return false;
    } else {
      // Use jlong_cast to compare the bits rather than numerical values.
      // This makes a difference for NaN constants.
      if (jlong_cast(_old_cp->double_at(cpi_old)) != jlong_cast(_new_cp->double_at(cpi_new)))
        return false;
    }
    break;
  }

  case Bytecodes::_bipush :
    if (_s_old->bcp()[1] != _s_new->bcp()[1])
      return false;
    break;

  case Bytecodes::_sipush    :
    if (_s_old->get_index_u2() != _s_new->get_index_u2())
      return false;
    break;

  case Bytecodes::_aload  : // fall through
  case Bytecodes::_astore : // fall through
  case Bytecodes::_dload  : // fall through
  case Bytecodes::_dstore : // fall through
  case Bytecodes::_fload  : // fall through
  case Bytecodes::_fstore : // fall through
  case Bytecodes::_iload  : // fall through
  case Bytecodes::_istore : // fall through
  case Bytecodes::_lload  : // fall through
  case Bytecodes::_lstore : // fall through
  case Bytecodes::_ret    :
    if (_s_old->is_wide() != _s_new->is_wide())
      return false;
    if (_s_old->get_index() != _s_new->get_index())
      return false;
    break;

  case Bytecodes::_goto      : // fall through
  case Bytecodes::_if_acmpeq : // fall through
  case Bytecodes::_if_acmpne : // fall through
  case Bytecodes::_if_icmpeq : // fall through
  case Bytecodes::_if_icmpne : // fall through
  case Bytecodes::_if_icmplt : // fall through
  case Bytecodes::_if_icmpge : // fall through
  case Bytecodes::_if_icmpgt : // fall through
  case Bytecodes::_if_icmple : // fall through
  case Bytecodes::_ifeq      : // fall through
  case Bytecodes::_ifne      : // fall through
  case Bytecodes::_iflt      : // fall through
  case Bytecodes::_ifge      : // fall through
  case Bytecodes::_ifgt      : // fall through
  case Bytecodes::_ifle      : // fall through
  case Bytecodes::_ifnonnull : // fall through
  case Bytecodes::_ifnull    : // fall through
  case Bytecodes::_jsr       : {
    int old_ofs = _s_old->bytecode()->get_offset_s2(c_old);
    int new_ofs = _s_new->bytecode()->get_offset_s2(c_new);
    if (_switchable_test) {
      int old_dest = _s_old->bci() + old_ofs;
      int new_dest = _s_new->bci() + new_ofs;
      if (old_ofs < 0 && new_ofs < 0) {
        if (! _bci_map->old_and_new_locations_same(old_dest, new_dest))
          return false;
      } else if (old_ofs > 0 && new_ofs > 0) {
        _fwd_jmps->append(old_dest);
        _fwd_jmps->append(new_dest);
      } else {
        return false;
      }
    } else {
      if (old_ofs != new_ofs)
        return false;
    }
    break;
  }

  case Bytecodes::_iinc :
    if (_s_old->is_wide() != _s_new->is_wide())
      return false;
    if (! _s_old->is_wide()) {
      // We could use get_index_u1 and get_constant_u1, but it's simpler to grab both bytes at once:
      if (Bytes::get_Java_u2(_s_old->bcp() + 1) != Bytes::get_Java_u2(_s_new->bcp() + 1))
        return false;
    } else {
      // We could use get_index_u2 and get_constant_u2, but it's simpler to grab all four bytes at once:
      if (Bytes::get_Java_u4(_s_old->bcp() + 1) != Bytes::get_Java_u4(_s_new->bcp() + 1))
        return false;
    }
    break;

  case Bytecodes::_goto_w : // fall through
  case Bytecodes::_jsr_w  : {
    int old_ofs = _s_old->bytecode()->get_offset_s4(c_old);
    int new_ofs = _s_new->bytecode()->get_offset_s4(c_new);
    if (_switchable_test) {
      int old_dest = _s_old->bci() + old_ofs;
      int new_dest = _s_new->bci() + new_ofs;
      if (old_ofs < 0 && new_ofs < 0) {
        if (! _bci_map->old_and_new_locations_same(old_dest, new_dest))
          return false;
      } else if (old_ofs > 0 && new_ofs > 0) {
        _fwd_jmps->append(old_dest);
        _fwd_jmps->append(new_dest);
      } else {
        return false;
      }
    } else {
      if (old_ofs != new_ofs)
        return false;
    }
    break;
  }

  case Bytecodes::_lookupswitch : // fall through
  case Bytecodes::_tableswitch  : {
    if (_switchable_test) {
      address aligned_bcp_old = (address) round_to((intptr_t)_s_old->bcp() + 1, jintSize);
      address aligned_bcp_new = (address) round_to((intptr_t)_s_new->bcp() + 1, jintSize);
      int default_old = (int) Bytes::get_Java_u4(aligned_bcp_old);
      int default_new = (int) Bytes::get_Java_u4(aligned_bcp_new);
      _fwd_jmps->append(_s_old->bci() + default_old);
      _fwd_jmps->append(_s_new->bci() + default_new);
      if (c_old == Bytecodes::_lookupswitch) {
        int npairs_old = (int) Bytes::get_Java_u4(aligned_bcp_old + jintSize);
        int npairs_new = (int) Bytes::get_Java_u4(aligned_bcp_new + jintSize);
        if (npairs_old != npairs_new)
          return false;
        for (int i = 0; i < npairs_old; i++) {
          int match_old = (int) Bytes::get_Java_u4(aligned_bcp_old + (2+2*i)*jintSize);
          int match_new = (int) Bytes::get_Java_u4(aligned_bcp_new + (2+2*i)*jintSize);
          if (match_old != match_new)
            return false;
          int ofs_old = (int) Bytes::get_Java_u4(aligned_bcp_old + (2+2*i+1)*jintSize);
          int ofs_new = (int) Bytes::get_Java_u4(aligned_bcp_new + (2+2*i+1)*jintSize);
          _fwd_jmps->append(_s_old->bci() + ofs_old);
          _fwd_jmps->append(_s_new->bci() + ofs_new);
        }
      } else if (c_old == Bytecodes::_tableswitch) {
        int lo_old = (int) Bytes::get_Java_u4(aligned_bcp_old + jintSize);
        int lo_new = (int) Bytes::get_Java_u4(aligned_bcp_new + jintSize);
        if (lo_old != lo_new)
          return false;
        int hi_old = (int) Bytes::get_Java_u4(aligned_bcp_old + 2*jintSize);
        int hi_new = (int) Bytes::get_Java_u4(aligned_bcp_new + 2*jintSize);
        if (hi_old != hi_new)
          return false;
        for (int i = 0; i < hi_old - lo_old + 1; i++) {
          int ofs_old = (int) Bytes::get_Java_u4(aligned_bcp_old + (3+i)*jintSize);
          int ofs_new = (int) Bytes::get_Java_u4(aligned_bcp_new + (3+i)*jintSize);
          _fwd_jmps->append(_s_old->bci() + ofs_old);
          _fwd_jmps->append(_s_new->bci() + ofs_new);
        }
      }
    } else { // !_switchable_test, can use fast rough compare
      int len_old = _s_old->instruction_size();
      int len_new = _s_new->instruction_size();
      if (len_old != len_new)
        return false;
      if (memcmp(_s_old->bcp(), _s_new->bcp(), len_old) != 0)
        return false;
    }
    break;
  }
  }

  return true;
}

bool MethodComparator::pool_constants_same(int cpi_old, int cpi_new) {
  constantTag tag_old = _old_cp->tag_at(cpi_old);
  constantTag tag_new = _new_cp->tag_at(cpi_new);
  if (tag_old.is_int() || tag_old.is_float()) {
    if (tag_old.value() != tag_new.value())
      return false;
    if (tag_old.is_int()) {
      if (_old_cp->int_at(cpi_old) != _new_cp->int_at(cpi_new))
        return false;
    } else {
      // Use jint_cast to compare the bits rather than numerical values.
      // This makes a difference for NaN constants.
      if (jint_cast(_old_cp->float_at(cpi_old)) != jint_cast(_new_cp->float_at(cpi_new)))
        return false;
    }
  } else if (tag_old.is_string() || tag_old.is_unresolved_string()) {
    if (! (tag_new.is_unresolved_string() || tag_new.is_string()))
      return false;
    if (strcmp(_old_cp->string_at_noresolve(cpi_old),
               _new_cp->string_at_noresolve(cpi_new)) != 0)
      return false;
  } else if (tag_old.is_klass() || tag_old.is_unresolved_klass()) {
    // tag_old should be klass - 4881222
    if (! (tag_new.is_unresolved_klass() || tag_new.is_klass()))
      return false;
    if (_old_cp->klass_at_noresolve(cpi_old) !=
        _new_cp->klass_at_noresolve(cpi_new))
      return false;
  } else if (tag_old.is_method_type() && tag_new.is_method_type()) {
    int mti_old = _old_cp->method_type_index_at(cpi_old);
    int mti_new = _new_cp->method_type_index_at(cpi_new);
    if ((_old_cp->symbol_at(mti_old) != _new_cp->symbol_at(mti_new)))
      return false;
  } else if (tag_old.is_method_handle() && tag_new.is_method_handle()) {
    if (_old_cp->method_handle_ref_kind_at(cpi_old) !=
        _new_cp->method_handle_ref_kind_at(cpi_new))
      return false;
    int mhi_old = _old_cp->method_handle_index_at(cpi_old);
    int mhi_new = _new_cp->method_handle_index_at(cpi_new);
    if ((_old_cp->uncached_klass_ref_at_noresolve(mhi_old) != _new_cp->uncached_klass_ref_at_noresolve(mhi_new)) ||
        (_old_cp->uncached_name_ref_at(mhi_old) != _new_cp->uncached_name_ref_at(mhi_new)) ||
        (_old_cp->uncached_signature_ref_at(mhi_old) != _new_cp->uncached_signature_ref_at(mhi_new)))
      return false;
  } else {
    return false;  // unknown tag
  }
  return true;
}


int MethodComparator::check_stack_and_locals_size(methodOop old_method, methodOop new_method) {
  if (old_method->max_stack() != new_method->max_stack()) {
    return 1;
  } else if (old_method->max_locals() != new_method->max_locals()) {
    return 2;
  } else if (old_method->size_of_parameters() != new_method->size_of_parameters()) {
    return 3;
  } else return 0;
}
