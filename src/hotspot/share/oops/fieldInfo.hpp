/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_FIELDINFO_HPP
#define SHARE_OOPS_FIELDINFO_HPP

#include "oops/constantPool.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayOop.hpp"
#include "utilities/unsigned5.hpp"
#include "utilities/vmEnums.hpp"

// This class represents the field information contained in
// InstanceKlass::_fields.  It is logically an array of FieldInfo
// records.  But it is stored as a compressed stream of 32-bit ints,
// to be traversed stream-wise.  Therefore, actual instances of this
// structure are present only temporarily, on the stack.
//
// To unpack or pack instances of FieldInfo, use FieldInfoStream,
// which is also in this file.
// 
class FieldInfo {
  friend class FieldInfoStream;
  friend class FieldStreamBase;

  // Each field record consists of the following items: index, name,
  // signature, offset, access flags, internal flags, initializer*,
  // generic signature*, contention group*.  The starred items are
  // usually zero, and usually don't take up any storage, because
  // their presence is gated by a bit in the internal flags.
  //
  // The index item is zero for the first field and goes up stepwise
  // from there.  The stream iterator computes that for you on the fly,
  // so it also doesn't take up any storage.  The index item is useful
  // for creating field-ids or indexing side tables (like field_state).

  // Internal field flags defined when a class file is loaded.  There
  // are different from AccessFlags, which are those not in the JVM
  // spec, and from FieldStatus flags, which are mutable.
 public:
  class FieldFlags {
    // The ordering of this enum is totally internal.  More frequent
    // flags should come earlier than less frequent ones, because
    // earlier ones compress better.
    enum FieldFlagBitPosition {
      _ff_initialized,  // has ConstantValue initializer attribute
      _ff_injected,     // internal field injected by the JVM
      _ff_generic,      // has a generic signature
      _ff_stable,       // trust as stable b/c declared as @Stable
      //_ff_unstable,   // do not trust as stable even though static-final
      _ff_contended,    // is contended, may have contention-group
    };

    // Some but not all of the flag bits signal the presence of an
    // additional 32-bit item in the field record.
    static const u4 _optional_item_bit_mask =
      flag_mask(_ff_initialized) |
      flag_mask(_ff_generic)     |
      flag_mask(_ff_contended);

    // boilerplate:
    u4 _flags;
    static constexpr u4 flag_mask(FieldFlagBitPosition pos) {
      return (u4)1 << (int)pos;
    }
    bool test_flag(FieldFlagBitPosition pos) {
      return (_flags & flag_mask(pos)) != 0;
    }
    void update_flag(FieldFlagBitPosition pos, bool z) {
      if (z)    _flags |=  flag_mask(pos);
      else      _flags &= ~flag_mask(pos);
    }

  public:
    FieldFlags(u4 flags) {
      _flags = flags;
    }
    u4 as_uint() { return _flags; }

    bool has_any_optionals() const {
      return (_flags & _optional_item_bit_mask) != 0;
    }

    // set internal flag bits if optionals are present
    FieldFlags set_optionals(const FieldInfo& fi) const {
      FieldFlags copy = *this;
      if (_initializer_index != 0) {
        copy.update_initialized(true);
      }
      if (_generic_signature_index != 0) {
        copy.update_generic(true);
      }
      if (_contention_group_index != 0) {
        copy.update_contended(true);
      }
      return copy;
    }

    bool is_initialized() const     { return test_flag(_ff_initialized); }
    bool is_injected() const        { return test_flag(_ff_injected); }
    bool is_generic() const         { return test_flag(_ff_generic); }
    bool is_stable() const          { return test_flag(_ff_stable); }
    bool is_contended() const       { return test_flag(_ff_contended); }

    void update_initialized(bool z) { update_flag(_ff_initialized, z); }
    void update_injected(bool z)    { update_flag(_ff_injected, z); }
    void update_generic(bool z)     { update_flag(_ff_generic, z); }
    void update_stable(bool z)      { update_flag(_ff_stable, z); }
    void update_contended(bool z)   { update_flag(_ff_contended, z); }
  };

  // Those few bits per field which are mutable (can change over time)
  // as opposed to immutable (defined when the declaring class file is
  // loaded).  These are stored in a narrow, optional array rooted in
  // the InstanceKlass structure.
 public:
  class FieldStatus {
    enum FieldStatusBitPosition {
      _fs_unstable,             // (final) field value may change unpredictably
      _fs_access_watched,       // field access is watched by JVMTI
      _fs_modification_watched, // field modification is watched by JVMTI
    };

    // boilerplate:
    u1 _flags;
    static constexpr u1 flag_mask(FieldStatusBitPosition pos) {
      return (u1)1 << (int)pos;
    }
    bool test_flag(FieldStatusBitPosition pos) {
      return (_flags & flag_mask(pos)) != 0;
    }
    // this performs an atomic update on a live status byte!
    void update_flag(FieldStatusBitPosition pos, bool z) {
      if (z)    atomic_set_bits(  _flags, flag_mask(pos));
      else      atomic_clear_bits(_flags, flag_mask(pos));
    }
    // out-of-line functions do a CAS-loop
    static void atomic_set_bits(u1& flags, u1 mask);
    static void atomic_clear_bits(u1& flags, u1 mask);

  public:
    FieldFlags(u1& flags) {
      _flags = flags;
    }
    u1 as_uint() { return _flags; }

    bool is_unstable()              { return test_flag(_fs_unstable); }
    bool is_access_watched()        { return test_flag(_fs_access_watched); }
    bool is_modification_watched()  { return test_flag(_fs_modification_watched); }

    void update_unstable(bool z)    { update_flag(_fs_unstable, z); }
    void update_access_watched(bool z) { update_flag(_fs_access_watched, z); }
    void update_modification_watched(bool z) { update_flag(_fs_modification_watched, z); }
  };

 public:    // %%% maybe make these private with accessors?
  // The following items are the unpacked bitwise information content
  // of a field record.  Per-field metadata extracted from the class
  // file are stored logically as a group of these items.  The
  // classfile parser produces these records in a temporary array, and
  // then compresses them into a FieldInfoStream.
  //
  u4 _index;                    // which field it is
  u2 _name_index;               // index in CP of name
  u2 _signature_index;          // index in CP of descriptor
  u4 _offset;                   // offset in object layout
  AccessFlags _access_flags;    // access flags (JVM spec)
  FieldFlags _internal_flags;   // internal flags (not JVM spec)
  u2 _initializer_index;        // index from ConstantValue attr (or 0)
  u2 _generic_signature_index;  // index from GenericSignature attr (or 0)
  u2 _contention_group_index;   // index from @Contended group item (or 0)


 public:
  // Caller sets an initial state, including the index of the field,
  // then modifies additional state (internal flags, optionals) as
  // needed.
  void initialize(u2 index,
                  u2 access_flags,
                  u2 name_index,
                  u2 signature_index) {
    _index = index;
    _name_index = name_index;
    _signature_index = signature_index;
    _access_flags = AccessFlags(access_flags);
    _internal_flags = FieldFlags(0);
    // set optionals to zero:
    _initializer_index = initval_index;
    _generic_signature_index = 0;
    _contention_group_index = 0;
  }

  int access_flags() const {
    return _access_flags.as_int();
  }
  u4 offset() const {
    return _offset;
  }

  bool is_contended() const {
    return _internal_flags.is_contended();
  }

  u2 contended_group() const {
    assert(is_contended(), "");
    return _contention_group_index;
 }

  bool is_offset_set() const {
    return _offset != 0;
  }

  Symbol* name(ConstantPool* cp) const {
    int index = _name_index;
    if (_internal_flags.is_injected()) {
      return lookup_symbol(index);
    }
    return cp->symbol_at(index);
  }

  Symbol* signature(ConstantPool* cp) const {
    int index = _signature_index;
    if (_internal_flags.is_injected()) {
      return lookup_symbol(index);
    }
    return cp->symbol_at(index);
  }

  Symbol* lookup_symbol(int symbol_index) const {
    assert(is_internal(), "only internal fields");
    return Symbol::vm_symbol_at(static_cast<vmSymbolID>(symbol_index));
  }
};

// The format of the stream, after decompression, is a series of
// integers organized like this:
//
//   FieldInfo := j=num_java_fields k=num_internal_fields Field*[j+k] End
//   Field := name sig offset access internal Optionals(internal)
//   Optionals(i) := initval?[i&is_init]     // ConstantValue attr
//                   gsig?[i&is_generic]     // signature attr
//                   group?[i&is_contended]  // Contended anno (group)
//   End = 0
//
class FieldInfoStream : public Array<u1> {
  // a FieldInfo is a repurposed Array<u1> with no additional fields

  friend class fieldDescriptor;
  friend class JavaFieldStream;
  friend class ClassFileParser;

  // Return num_java_fields from the header.  As the most frequently
  // used item it comes first.
  int num_java_fields() {
    assert(hf_java_fields == 0, "");
    return Reader(*this).next_uint();
  }
  Reader read_header(int& num_java_fields,
                     int& num_internal_fields) {
    Reader r(*this);
    num_java_fields = r.next_uint();
    num_internal_fields = r.next_uint();
    return r;
  }

  // Gadget for decoding and reading the stream of field records.
  class Reader {
    UNSIGNED5::Reader<u1*, int> _r;
    int _next_index;
    uint32_t uint() { return _r.next_uint(); }
    void skip(int n) { _r.skip(n); }
    Reader(const FieldInfoStream& fi)
      : _r(fi.data(), 0, fi.limit()) {
    }
    void skip_header() {
      const int jf_intf = 2;  // two items
      skip(jf_intf);
    }
  public:
    int position() { return _r.position(); }
    int next_index() { return _next_index; }
    // read the fixed items; optional items must be read next
    bool read_required_field_info(FieldInfo& fi) {
      fi._index = _next_index++;
      fi._name_index = next_uint();
      fi._signature_index = next_uint();
      fi._access_flags = AccessFlags(next_uint());
      fi._offset = next_uint();
      fi._internal_flags = FieldFlags(next_uint());
      return fi._internal_flags.has_any_optionals();
    }
    // based on the internal flags item just read, read any optional items
    bool read_optional_field_info(FieldInfo& fi) {
      FieldFlags internal_flags = fi._field_flags;
      if (!internal_flags.has_any_optionals()) {
        return false;  // tell caller there was nothing
      } else {
        if (internal_flags.is_initialized()) {
          fi._initializer_index = next_uint();
        }
        if (internal_flags.is_generic()) {
          fi._generic_signature_index = next_uint();
        }
        if (internal_flags.is_contended()) {
          fi._contention_group = next_uint();
        }
        return true;  // tell caller there was something
      }
    }
    // skip a whole field record, both required and optional bits
    Reader&  skip_field_info() {
      _next_index++;
      const int name_sig_af_off = 4;  // four items
      skip(af_name_sig_off);
      FieldFlags ff(next_uint());
      if (internal_flags.has_any_optionals()) {
        const int init_gen_cont = (ff.is_initialized() +
                                   ff.is_generic() +
                                   ff.is_contended());
        skip(init_gen_cont);  // up to three items
      }
      return *this;
    }

    // Skip to the nth field.  If the reader is freshly initialized to
    // the zero index, this will call skip_field_info() n times.
    Reader& skip_to_field_info(int n) {
      assert(n >= _next_index, "already past that index");
      const int count = n - _next_index;
      for (int i = 0; i < count; i++)  skip_field_info();
      assert(_next_index() == n, "");
      return *this;
    }

    Reader& rewind() {
      set_position_and_next_index(0, 0);
      skip_header();
    }

    // for random access, if you know where to go up front:
    Reader& set_position_and_next_index(int position, int next_index) {
      _r.set_position(position);
      _next_index = next_index;
      return *this;
    }
  };

  // Gadget for sizing and/or writing a stream of field records.
  template<typename CON>
  class Mapper {
    CON _consumer;  // can be UNSIGNED5::Writer or UNSIGNED5::Sizer
    int _next_index;
  public:
    Mapper(CON consumer) : _con(consumer) { _next_index = 0; }
    int next_index() { return _next_index; }
    void set_next_index(int next_index) {
      _next_index = next_index;
    }
    // visit the fixed items; optional items must be visited next
    bool map_required_field_info(const FieldInfo& fi) {
      _next_index++;  // pre-increment
      _con.accept_uint(fi._name_index);
      _con.accept_uint(fi._signature_index);
      _con.accept_uint(fi._access_flags.as_int());
      _con.accept_uint(fi._internal_flags.set_optionals(fi).as_int());
      // tell caller whether there were optional fields:
      return internal_flags.has_any_optionals();
    }
    // based on the internal flags item just written, visit any optional items
    void write_optional_field_info(FieldFlags internal_flags,
                                   int initializer_index,
                                   int generic_signature_index,
                                   int contention_group) {
      if (!internal_flags.has_any_optionals()) {
        assert(initializer_index == 0, "");
        assert(generic_signature_index == 0, "");
        assert(contention_group == 0, "");
      } else {
        if (internal_flags.is_initialized()) {
          _con.accept_uint(initializer_index);
        }
        if (internal_flags.is_generic()) {
          _con.accept_uint(generic_signature_index);
        }
        if (internal_flags.is_contended()) {
          _con.accept_uint(contention_group);
        }
      }
    }
  };
};

#endif // SHARE_OOPS_FIELDINFO_HPP
