/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_FIELDINFO_INLINE_HPP
#define SHARE_OOPS_FIELDINFO_INLINE_HPP

#include "oops/fieldInfo.hpp"

#include "memory/metadataFactory.hpp"
#include "oops/constantPool.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.hpp"
#include "utilities/checkedCast.hpp"

inline Symbol* FieldInfo::name(ConstantPool* cp) const {
  int index = _name_index;
  if (_field_flags.is_injected()) {
    return lookup_symbol(index);
  }
  return cp->symbol_at(index);
}

inline Symbol* FieldInfo::signature(ConstantPool* cp) const {
  int index = _signature_index;
  if (_field_flags.is_injected()) {
    return lookup_symbol(index);
  }
  return cp->symbol_at(index);
}

inline Symbol* FieldInfo::lookup_symbol(int symbol_index) const {
  assert(_field_flags.is_injected(), "only injected fields");
  return Symbol::vm_symbol_at(static_cast<vmSymbolID>(symbol_index));
}

inline int FieldInfoStream::num_injected_java_fields(const Array<u1>* fis) {
  FieldInfoReader fir(fis);
  int java_fields_count;
  int injected_fields_count;
  fir.read_field_counts(&java_fields_count, &injected_fields_count);
  return injected_fields_count;
}

inline int FieldInfoStream::num_total_fields(const Array<u1>* fis) {
  FieldInfoReader fir(fis);
  int java_fields_count;
  int injected_fields_count;
  fir.read_field_counts(&java_fields_count, &injected_fields_count);
  return java_fields_count + injected_fields_count;
}

inline int FieldInfoStream::num_java_fields(const Array<u1>* fis) {
  FieldInfoReader fir(fis);
  int java_fields_count;
  int injected_fields_count;
  fir.read_field_counts(&java_fields_count, &injected_fields_count);
  return java_fields_count;
}

template<typename CON>
inline void Mapper<CON>::map_field_info(const FieldInfo& fi) {
  _next_index++;  // pre-increment
  _consumer->accept_uint(fi.name_index());
  _consumer->accept_uint(fi.signature_index());
  _consumer->accept_uint(fi.offset());
  _consumer->accept_uint(fi.access_flags().as_field_flags());
  _consumer->accept_uint(fi.field_flags().as_uint());
  if(fi.field_flags().has_any_optionals()) {
    if (fi.field_flags().is_initialized()) {
      _consumer->accept_uint(fi.initializer_index());
    }
    if (fi.field_flags().is_generic()) {
      _consumer->accept_uint(fi.generic_signature_index());
    }
    if (fi.field_flags().is_contended()) {
      _consumer->accept_uint(fi.contention_group());
    }
  } else {
    assert(fi.initializer_index() == 0, "");
    assert(fi.generic_signature_index() == 0, "");
    assert(fi.contention_group() == 0, "");
  }
}


inline FieldInfoReader::FieldInfoReader(const Array<u1>* fi)
  : _r(fi->data(), fi->length()),
    _next_index(0) { }

inline void FieldInfoReader::read_field_counts(int* java_fields, int* injected_fields) {
  *java_fields = next_uint();
  *injected_fields = next_uint();
}

inline void FieldInfoReader::read_name_and_signature(u2* name_index, u2* signature_index) {
  *name_index = checked_cast<u2>(next_uint());
  *signature_index = checked_cast<u2>(next_uint());
}

inline void FieldInfoReader::read_field_info(FieldInfo& fi) {
  fi._index = _next_index++;
  read_name_and_signature(&fi._name_index, &fi._signature_index);
  fi._offset = next_uint();
  fi._access_flags = AccessFlags(checked_cast<u2>(next_uint()));
  fi._field_flags = FieldInfo::FieldFlags(next_uint());
  if (fi._field_flags.is_initialized()) {
    fi._initializer_index = checked_cast<u2>(next_uint());
  } else {
    fi._initializer_index = 0;
  }
  if (fi._field_flags.is_generic()) {
    fi._generic_signature_index = checked_cast<u2>(next_uint());
  } else {
    fi._generic_signature_index = 0;
  }
  if (fi._field_flags.is_contended()) {
    fi._contention_group = checked_cast<u2>(next_uint());
  } else {
    fi._contention_group = 0;
  }
}

inline FieldInfoReader&  FieldInfoReader::skip_field_info() {
  _next_index++;
  const int name_sig_af_off = 4;  // four items
  skip(name_sig_af_off);
  FieldInfo::FieldFlags ff(next_uint());
  if (ff.has_any_optionals()) {
    const int init_gen_cont = (ff.is_initialized() +
                                ff.is_generic() +
                                ff.is_contended());
    skip(init_gen_cont);  // up to three items
  }
  return *this;
}

// Skip to the nth field.  If the reader is freshly initialized to
// the zero index, this will call skip_field_info() n times.
inline FieldInfoReader& FieldInfoReader::skip_to_field_info(int n) {
  assert(n >= _next_index, "already past that index");
  const int count = n - _next_index;
  for (int i = 0; i < count; i++)  skip_field_info();
  assert(_next_index == n, "");
  return *this;
}

// for random access, if you know where to go up front:
inline FieldInfoReader& FieldInfoReader::set_position_and_next_index(int position, int next_index) {
  _r.set_position(position);
  _next_index = next_index;
  return *this;
}

inline void FieldStatus::atomic_set_bits(u1& flags, u1 mask) {
  Atomic::fetch_then_or(&flags, mask);
}

inline void FieldStatus::atomic_clear_bits(u1& flags, u1 mask) {
  Atomic::fetch_then_and(&flags, (u1)(~mask));
}

inline void FieldStatus::update_flag(FieldStatusBitPosition pos, bool z) {
  if (z) atomic_set_bits(_flags, flag_mask(pos));
  else atomic_clear_bits(_flags, flag_mask(pos));
}

inline void FieldStatus::update_access_watched(bool z) { update_flag(_fs_access_watched, z); }
inline void FieldStatus::update_modification_watched(bool z) { update_flag(_fs_modification_watched, z); }
inline void FieldStatus::update_initialized_final_update(bool z) { update_flag(_initialized_final_update, z); }

#endif // SHARE_OOPS_FIELDINFO_INLINE_HPP
