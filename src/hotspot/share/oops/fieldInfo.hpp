/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "oops/typeArrayOop.hpp"
#include "utilities/unsigned5.hpp"
#include "utilities/vmEnums.hpp"

static constexpr u4 flag_mask(int pos) {
  return (u4)1 << pos;
}


// Helper class for access to the underlying Array<u1> used to
// store the compressed stream of FieldInfo
template<typename ARR, typename OFF>
struct ArrayHelper {
  uint8_t operator()(ARR a, OFF i) const { return a->at(i); };
  void operator()(ARR a, OFF i, uint8_t b) const { a->at_put(i,b); };
  // So, an expression ArrayWriterHelper() acts like these lambdas:
  // auto get = [&](ARR a, OFF i){ return a[i]; };
  // auto set = [&](ARR a, OFF i, uint8_t x){ a[i] = x; };
};

// This class represents the field information contained in the fields
// array of an InstanceKlass.  Currently it's laid on top an array of
// Java shorts but in the future it could simply be used as a real
// array type.  FieldInfo generally shouldn't be used directly.
// Fields should be queried either through InstanceKlass or through
// the various FieldStreams.
class FieldInfo {
  friend class fieldDescriptor;
  friend class JavaFieldStream;
  friend class ClassFileParser;
  friend class FieldInfoStream;
  friend class FieldStreamBase;
  friend class FieldInfoReader;
  friend class VMStructs;

 public:

  class FieldFlags {
    friend class VMStructs;
    friend class JVMCIVMStructs;

    // The ordering of this enum is totally internal.  More frequent
    // flags should come earlier than less frequent ones, because
    // earlier ones compress better.
    enum FieldFlagBitPosition {
      _ff_initialized,  // has ConstantValue initializer attribute
      _ff_injected,     // internal field injected by the JVM
      _ff_generic,      // has a generic signature
      _ff_stable,       // trust as stable b/c declared as @Stable
      _ff_contended,    // is contended, may have contention-group
    };

    // Some but not all of the flag bits signal the presence of an
    // additional 32-bit item in the field record.
    static const u4 _optional_item_bit_mask =
      flag_mask((int)_ff_initialized) |
      flag_mask((int)_ff_generic)     |
      flag_mask((int)_ff_contended);

    // boilerplate:
    u4 _flags;

    bool test_flag(FieldFlagBitPosition pos) const {
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
    u4 as_uint() const { return _flags; }
    bool has_any_optionals() const {
      return (_flags & _optional_item_bit_mask) != 0;
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

 private:
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
  FieldFlags _field_flags;      // VM defined flags (not JVM spec)
  u2 _initializer_index;        // index from ConstantValue attr (or 0)
  u2 _generic_signature_index;  // index from GenericSignature attr (or 0)
  u2 _contention_group;         // index from @Contended group item (or 0)

 public:

  FieldInfo() : _index(0),
                _name_index(0),
                _signature_index(0),
                _offset(0),
                _access_flags(AccessFlags(0)),
                _field_flags(FieldFlags(0)),
                _initializer_index(0),
                _generic_signature_index(0),
                _contention_group(0) { }

  FieldInfo(AccessFlags access_flags, u2 name_index, u2 signature_index, u2 initval_index, FieldInfo::FieldFlags fflags) :
            _index(0),
            _name_index(name_index),
            _signature_index(signature_index),
            _offset(0),
            _access_flags(access_flags),
            _field_flags(fflags),
            _initializer_index(initval_index),
            _generic_signature_index(0),
            _contention_group(0) {
              if (initval_index != 0) {
                _field_flags.update_initialized(true);
              }
            }

  u4 index() const                           { return _index; }
  void set_index(u4 index)                   { _index = index; }
  u2 name_index() const                      { return _name_index; }
  void set_name_index(u2 index)              { _name_index = index; }
  u2 signature_index() const                 { return _signature_index; }
  void set_signature_index(u2 index)         { _signature_index = index; }
  u4 offset() const                          { return _offset; }
  void set_offset(u4 offset)                 { _offset = offset; }
  AccessFlags access_flags() const           { return _access_flags; }
  FieldFlags field_flags() const             { return _field_flags; }
  FieldFlags* field_flags_addr()             { return &_field_flags; }
  u2 initializer_index() const               { return _initializer_index; }
  void set_initializer_index(u2 index)       { _initializer_index = index; }
  u2 generic_signature_index() const         { return _generic_signature_index; }
  void set_generic_signature_index(u2 index) { _generic_signature_index = index; }
  u2 contention_group() const                { return _contention_group; }

  bool is_contended() const {
    return _field_flags.is_contended();
  }

  u2 contended_group() const {
    assert(is_contended(), "");
    return _contention_group;
  }

  void set_contended_group(u2 group) {
    _field_flags.update_contended(true);
    _contention_group = group;
  }

  bool is_offset_set() const {
    return _offset != 0;
  }

  inline Symbol* name(ConstantPool* cp) const;

  inline Symbol* signature(ConstantPool* cp) const;

  inline Symbol* lookup_symbol(int symbol_index) const;

  void print(outputStream* os, ConstantPool* cp);
  void static print_from_growable_array(outputStream* os, GrowableArray<FieldInfo>* array, ConstantPool* cp);
};

class FieldInfoStream;

// Gadget for sizing and/or writing a stream of field records.
template<typename CON>
class Mapper {
  CON* _consumer;  // can be UNSIGNED5::Writer or UNSIGNED5::Sizer
  int _next_index;
public:
  Mapper(CON* consumer) : _consumer(consumer) { _next_index = 0; }
  int next_index() const { return _next_index; }
  void set_next_index(int next_index) { _next_index = next_index; }
  CON* consumer() const { return _consumer; }
  void map_field_info(const FieldInfo& fi);
};

// Gadget for decoding and reading the stream of field records.
class FieldInfoReader {
  UNSIGNED5::Reader<const u1*, int> _r;
  int _next_index;

public:
  FieldInfoReader(const Array<u1>* fi);

private:
  inline uint32_t next_uint() { return _r.next_uint(); }
  void skip(int n) { int s = _r.try_skip(n); assert(s == n,""); }

public:
  void read_field_counts(int* java_fields, int* injected_fields);
  int has_next() const { return _r.position() < _r.limit(); }
  int position() const { return _r.position(); }
  int next_index() const { return _next_index; }
  void read_name_and_signature(u2* name_index, u2* signature_index);
  void read_field_info(FieldInfo& fi);

  int search_table_lookup(const Array<u1>* search_table, const Symbol* name, const Symbol* signature, ConstantPool* cp, int java_fields);

  // skip a whole field record, both required and optional bits
  FieldInfoReader&  skip_field_info();

  // Skip to the nth field.  If the reader is freshly initialized to
  // the zero index, this will call skip_field_info() n times.
  FieldInfoReader& skip_to_field_info(int n);

  // for random access, if you know where to go up front:
  FieldInfoReader& set_position_and_next_index(int position, int next_index);
};

// The format of the stream, after decompression, is a series of
// integers organized like this:
//
//   FieldInfoStream := j=num_java_fields k=num_injected_fields Field[j+k] End
//   Field := name sig offset access flags Optionals(flags)
//   Optionals(i) := initval?[i&is_init]     // ConstantValue attr
//                   gsig?[i&is_generic]     // signature attr
//                   group?[i&is_contended]  // Contended anno (group)
//   End = 0
//
class FieldInfoStream : AllStatic {
  friend class fieldDescriptor;
  friend class JavaFieldStream;
  friend class FieldStreamBase;
  friend class ClassFileParser;
  friend class FieldInfoReader;
  friend class FieldInfoComparator;

 private:
  static int compare_name_and_sig(const Symbol* n1, const Symbol* s1, const Symbol* n2, const Symbol* s2);

 public:
  static int num_java_fields(const Array<u1>* fis);
  static int num_injected_java_fields(const Array<u1>* fis);
  static int num_total_fields(const Array<u1>* fis);

  static Array<u1>* create_FieldInfoStream(GrowableArray<FieldInfo>* fields, int java_fields, int injected_fields,
                                           ClassLoaderData* loader_data, TRAPS);
  static Array<u1>* create_search_table(ConstantPool* cp, const Array<u1>* fis, ClassLoaderData* loader_data, TRAPS);
  static GrowableArray<FieldInfo>* create_FieldInfoArray(const Array<u1>* fis, int* java_fields_count, int* injected_fields_count);
  static void print_from_fieldinfo_stream(Array<u1>* fis, outputStream* os, ConstantPool* cp);

  DEBUG_ONLY(static void validate_search_table(ConstantPool* cp, const Array<u1>* fis, const Array<u1>* search_table);)

  static void print_search_table(outputStream* st, ConstantPool* cp, const Array<u1>* fis, const Array<u1>* search_table);
};

class FieldStatus {
  enum FieldStatusBitPosition {
    _fs_access_watched,       // field access is watched by JVMTI
    _fs_modification_watched, // field modification is watched by JVMTI
    _initialized_final_update // (static) final field updated outside (class) initializer
  };

  // boilerplate:
  u1 _flags;
  static constexpr u1 flag_mask(FieldStatusBitPosition pos) { return (u1)1 << (int)pos; }
  bool test_flag(FieldStatusBitPosition pos) { return (_flags & flag_mask(pos)) != 0; }
  // this performs an atomic update on a live status byte!
  void update_flag(FieldStatusBitPosition pos, bool z);
  // out-of-line functions do a CAS-loop
  static void atomic_set_bits(u1& flags, u1 mask);
  static void atomic_clear_bits(u1& flags, u1 mask);

  public:
  FieldStatus() { _flags = 0; }
  FieldStatus(u1 flags) { _flags = flags; }
  u1 as_uint() { return _flags; }

  bool is_access_watched()        { return test_flag(_fs_access_watched); }
  bool is_modification_watched()  { return test_flag(_fs_modification_watched); }
  bool is_initialized_final_update() { return test_flag(_initialized_final_update); }

  void update_access_watched(bool z);
  void update_modification_watched(bool z);
  void update_initialized_final_update(bool z);
};

#endif // SHARE_OOPS_FIELDINFO_HPP
