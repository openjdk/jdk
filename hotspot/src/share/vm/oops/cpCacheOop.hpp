/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// A ConstantPoolCacheEntry describes an individual entry of the constant
// pool cache. There's 2 principal kinds of entries: field entries for in-
// stance & static field access, and method entries for invokes. Some of
// the entry layout is shared and looks as follows:
//
// bit number |31                0|
// bit length |-8--|-8--|---16----|
// --------------------------------
// _indices   [ b2 | b1 |  index  ]
// _f1        [  entry specific   ]
// _f2        [  entry specific   ]
// _flags     [t|f|vf|v|m|h|unused|field_index] (for field entries)
// bit length |4|1|1 |1|1|0|---7--|----16-----]
// _flags     [t|f|vf|v|m|h|unused|eidx|psze] (for method entries)
// bit length |4|1|1 |1|1|1|---7--|-8--|-8--]

// --------------------------------
//
// with:
// index  = original constant pool index
// b1     = bytecode 1
// b2     = bytecode 2
// psze   = parameters size (method entries only)
// eidx   = interpreter entry index (method entries only)
// field_index = index into field information in holder instanceKlass
//          The index max is 0xffff (max number of fields in constant pool)
//          and is multiplied by (instanceKlass::next_offset) when accessing.
// t      = TosState (see below)
// f      = field is marked final (see below)
// vf     = virtual, final (method entries only : is_vfinal())
// v      = field is volatile (see below)
// m      = invokeinterface used for method in class Object (see below)
// h      = RedefineClasses/Hotswap bit (see below)
//
// The flags after TosState have the following interpretation:
// bit 27: f flag  true if field is marked final
// bit 26: vf flag true if virtual final method
// bit 25: v flag true if field is volatile (only for fields)
// bit 24: m flag true if invokeinterface used for method in class Object
// bit 23: 0 for fields, 1 for methods
//
// The flags 31, 30, 29, 28 together build a 4 bit number 0 to 8 with the
// following mapping to the TosState states:
//
// btos: 0
// ctos: 1
// stos: 2
// itos: 3
// ltos: 4
// ftos: 5
// dtos: 6
// atos: 7
// vtos: 8
//
// Entry specific: field entries:
// _indices = get (b1 section) and put (b2 section) bytecodes, original constant pool index
// _f1      = field holder
// _f2      = field offset in words
// _flags   = field type information, original field index in field holder
//            (field_index section)
//
// Entry specific: method entries:
// _indices = invoke code for f1 (b1 section), invoke code for f2 (b2 section),
//            original constant pool index
// _f1      = method for all but virtual calls, unused by virtual calls
//            (note: for interface calls, which are essentially virtual,
//             contains klassOop for the corresponding interface.
//            for invokedynamic, f1 contains the CallSite object for the invocation
// _f2      = method/vtable index for virtual calls only, unused by all other
//            calls.  The vf flag indicates this is a method pointer not an
//            index.
// _flags   = field type info (f section),
//            virtual final entry (vf),
//            interpreter entry index (eidx section),
//            parameter size (psze section)
//
// Note: invokevirtual & invokespecial bytecodes can share the same constant
//       pool entry and thus the same constant pool cache entry. All invoke
//       bytecodes but invokevirtual use only _f1 and the corresponding b1
//       bytecode, while invokevirtual uses only _f2 and the corresponding
//       b2 bytecode.  The value of _flags is shared for both types of entries.
//
// The fields are volatile so that they are stored in the order written in the
// source code.  The _indices field with the bytecode must be written last.

class ConstantPoolCacheEntry VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
  friend class constantPoolCacheKlass;

 private:
  volatile intx     _indices;  // constant pool index & rewrite bytecodes
  volatile oop      _f1;       // entry specific oop field
  volatile intx     _f2;       // entry specific int/oop field
  volatile intx     _flags;    // flags


#ifdef ASSERT
  bool same_methodOop(oop cur_f1, oop f1);
#endif

  void set_bytecode_1(Bytecodes::Code code);
  void set_bytecode_2(Bytecodes::Code code);
  void set_f1(oop f1)                            {
    oop existing_f1 = _f1; // read once
    assert(existing_f1 == NULL || existing_f1 == f1, "illegal field change");
    oop_store(&_f1, f1);
  }
  void set_f2(intx f2)                           { assert(_f2 == 0    || _f2 == f2, "illegal field change"); _f2 = f2; }
  int as_flags(TosState state, bool is_final, bool is_vfinal, bool is_volatile,
               bool is_method_interface, bool is_method);
  void set_flags(intx flags)                     { _flags = flags; }

 public:
  // specific bit values in flag field
  // Note: the interpreter knows this layout!
  enum FlagBitValues {
    hotSwapBit    = 23,
    methodInterface = 24,
    volatileField = 25,
    vfinalMethod  = 26,
    finalField    = 27
  };

  enum { field_index_mask = 0xFFFF };

  // start of type bits in flags
  // Note: the interpreter knows this layout!
  enum FlagValues {
    tosBits      = 28
  };

  // Initialization
  void initialize_entry(int original_index);     // initialize primary entry
  void initialize_secondary_entry(int main_index); // initialize secondary entry

  void set_field(                                // sets entry to resolved field state
    Bytecodes::Code get_code,                    // the bytecode used for reading the field
    Bytecodes::Code put_code,                    // the bytecode used for writing the field
    KlassHandle     field_holder,                // the object/klass holding the field
    int             orig_field_index,            // the original field index in the field holder
    int             field_offset,                // the field offset in words in the field holder
    TosState        field_type,                  // the (machine) field type
    bool            is_final,                     // the field is final
    bool            is_volatile                  // the field is volatile
  );

  void set_method(                               // sets entry to resolved method entry
    Bytecodes::Code invoke_code,                 // the bytecode used for invoking the method
    methodHandle    method,                      // the method/prototype if any (NULL, otherwise)
    int             vtable_index                 // the vtable index if any, else negative
  );

  void set_interface_call(
    methodHandle method,                         // Resolved method
    int index                                    // Method index into interface
  );

  void set_dynamic_call(
    Handle call_site,                            // Resolved java.dyn.CallSite (f1)
    methodHandle signature_invoker               // determines signature information
  );

  void set_parameter_size(int value) {
    assert(parameter_size() == 0 || parameter_size() == value,
           "size must not change");
    // Setting the parameter size by itself is only safe if the
    // current value of _flags is 0, otherwise another thread may have
    // updated it and we don't want to overwrite that value.  Don't
    // bother trying to update it once it's nonzero but always make
    // sure that the final parameter size agrees with what was passed.
    if (_flags == 0) {
      Atomic::cmpxchg_ptr((value & 0xFF), &_flags, 0);
    }
    guarantee(parameter_size() == value, "size must not change");
  }

  // Which bytecode number (1 or 2) in the index field is valid for this bytecode?
  // Returns -1 if neither is valid.
  static int bytecode_number(Bytecodes::Code code) {
    switch (code) {
      case Bytecodes::_getstatic       :    // fall through
      case Bytecodes::_getfield        :    // fall through
      case Bytecodes::_invokespecial   :    // fall through
      case Bytecodes::_invokestatic    :    // fall through
      case Bytecodes::_invokeinterface : return 1;
      case Bytecodes::_putstatic       :    // fall through
      case Bytecodes::_putfield        :    // fall through
      case Bytecodes::_invokevirtual   : return 2;
      default                          : break;
    }
    return -1;
  }

  // Has this bytecode been resolved? Only valid for invokes and get/put field/static.
  bool is_resolved(Bytecodes::Code code) const {
    switch (bytecode_number(code)) {
      case 1:  return (bytecode_1() == code);
      case 2:  return (bytecode_2() == code);
    }
    return false;      // default: not resolved
  }

  // Accessors
  bool is_secondary_entry() const                { return (_indices & 0xFFFF) == 0; }
  int constant_pool_index() const                { assert((_indices & 0xFFFF) != 0, "must be main entry");
                                                   return (_indices & 0xFFFF); }
  int main_entry_index() const                   { assert((_indices & 0xFFFF) == 0, "must be secondary entry");
                                                   return ((uintx)_indices >> 16); }
  Bytecodes::Code bytecode_1() const             { return Bytecodes::cast((_indices >> 16) & 0xFF); }
  Bytecodes::Code bytecode_2() const             { return Bytecodes::cast((_indices >> 24) & 0xFF); }
  volatile oop  f1() const                       { return _f1; }
  intx f2() const                                { return _f2; }
  int  field_index() const;
  int  parameter_size() const                    { return _flags & 0xFF; }
  bool is_vfinal() const                         { return ((_flags & (1 << vfinalMethod)) == (1 << vfinalMethod)); }
  bool is_volatile() const                       { return ((_flags & (1 << volatileField)) == (1 << volatileField)); }
  bool is_methodInterface() const                { return ((_flags & (1 << methodInterface)) == (1 << methodInterface)); }
  bool is_byte() const                           { return (((uintx) _flags >> tosBits) == btos); }
  bool is_char() const                           { return (((uintx) _flags >> tosBits) == ctos); }
  bool is_short() const                          { return (((uintx) _flags >> tosBits) == stos); }
  bool is_int() const                            { return (((uintx) _flags >> tosBits) == itos); }
  bool is_long() const                           { return (((uintx) _flags >> tosBits) == ltos); }
  bool is_float() const                          { return (((uintx) _flags >> tosBits) == ftos); }
  bool is_double() const                         { return (((uintx) _flags >> tosBits) == dtos); }
  bool is_object() const                         { return (((uintx) _flags >> tosBits) == atos); }
  TosState flag_state() const                    { assert( ( (_flags >> tosBits) & 0x0F ) < number_of_states, "Invalid state in as_flags");
                                                   return (TosState)((_flags >> tosBits) & 0x0F); }

  // Code generation support
  static WordSize size()                         { return in_WordSize(sizeof(ConstantPoolCacheEntry) / HeapWordSize); }
  static ByteSize size_in_bytes()                { return in_ByteSize(sizeof(ConstantPoolCacheEntry)); }
  static ByteSize indices_offset()               { return byte_offset_of(ConstantPoolCacheEntry, _indices); }
  static ByteSize f1_offset()                    { return byte_offset_of(ConstantPoolCacheEntry, _f1); }
  static ByteSize f2_offset()                    { return byte_offset_of(ConstantPoolCacheEntry, _f2); }
  static ByteSize flags_offset()                 { return byte_offset_of(ConstantPoolCacheEntry, _flags); }

  // GC Support
  void oops_do(void f(oop*));
  void oop_iterate(OopClosure* blk);
  void oop_iterate_m(OopClosure* blk, MemRegion mr);
  void follow_contents();
  void adjust_pointers();

#ifndef SERIALGC
  // Parallel Old
  void follow_contents(ParCompactionManager* cm);
#endif // SERIALGC

  void update_pointers();
  void update_pointers(HeapWord* beg_addr, HeapWord* end_addr);

  // RedefineClasses() API support:
  // If this constantPoolCacheEntry refers to old_method then update it
  // to refer to new_method.
  // trace_name_printed is set to true if the current call has
  // printed the klass name so that other routines in the adjust_*
  // group don't print the klass name.
  bool adjust_method_entry(methodOop old_method, methodOop new_method,
         bool * trace_name_printed);
  bool is_interesting_method_entry(klassOop k);
  bool is_field_entry() const                    { return (_flags & (1 << hotSwapBit)) == 0; }
  bool is_method_entry() const                   { return (_flags & (1 << hotSwapBit)) != 0; }

  // Debugging & Printing
  void print (outputStream* st, int index) const;
  void verify(outputStream* st) const;

  static void verify_tosBits() {
    assert(tosBits == 28, "interpreter now assumes tosBits is 28");
  }
};


// A constant pool cache is a runtime data structure set aside to a constant pool. The cache
// holds interpreter runtime information for all field access and invoke bytecodes. The cache
// is created and initialized before a class is actively used (i.e., initialized), the indivi-
// dual cache entries are filled at resolution (i.e., "link") time (see also: rewriter.*).

class constantPoolCacheOopDesc: public oopDesc {
  friend class VMStructs;
 private:
  int             _length;
  constantPoolOop _constant_pool;                // the corresponding constant pool
  // If true, safe for concurrent GC processing,
  // Set unconditionally in constantPoolCacheKlass::allocate()
  volatile bool        _is_conc_safe;

  // Sizing
  debug_only(friend class ClassVerifier;)
  int length() const                             { return _length; }
  void set_length(int length)                    { _length = length; }

  static int header_size()                       { return sizeof(constantPoolCacheOopDesc) / HeapWordSize; }
  static int object_size(int length)             { return align_object_size(header_size() + length * in_words(ConstantPoolCacheEntry::size())); }
  int object_size()                              { return object_size(length()); }

  // Helpers
  constantPoolOop*        constant_pool_addr()   { return &_constant_pool; }
  ConstantPoolCacheEntry* base() const           { return (ConstantPoolCacheEntry*)((address)this + in_bytes(base_offset())); }

  friend class constantPoolCacheKlass;
  friend class ConstantPoolCacheEntry;

 public:
  // Initialization
  void initialize(intArray& inverse_index_map);

  // Secondary indexes.
  // They must look completely different from normal indexes.
  // The main reason is that byte swapping is sometimes done on normal indexes.
  // Also, some of the CP accessors do different things for secondary indexes.
  // Finally, it is helpful for debugging to tell the two apart.
  static bool is_secondary_index(int i) { return (i < 0); }
  static int  decode_secondary_index(int i) { assert(is_secondary_index(i),  ""); return ~i; }
  static int  encode_secondary_index(int i) { assert(!is_secondary_index(i), ""); return ~i; }

  // Accessors
  void set_constant_pool(constantPoolOop pool)   { oop_store_without_check((oop*)&_constant_pool, (oop)pool); }
  constantPoolOop constant_pool() const          { return _constant_pool; }
  // Fetches the entry at the given index.
  // The entry may be either primary or secondary.
  // In either case the index must not be encoded or byte-swapped in any way.
  ConstantPoolCacheEntry* entry_at(int i) const {
    assert(0 <= i && i < length(), "index out of bounds");
    return base() + i;
  }
  // Fetches the secondary entry referred to by index.
  // The index may be a secondary index, and must not be byte-swapped.
  ConstantPoolCacheEntry* secondary_entry_at(int i) const {
    int raw_index = i;
    if (is_secondary_index(i)) {  // correct these on the fly
      raw_index = decode_secondary_index(i);
    }
    assert(entry_at(raw_index)->is_secondary_entry(), "not a secondary entry");
    return entry_at(raw_index);
  }
  // Given a primary or secondary index, fetch the corresponding primary entry.
  // Indirect through the secondary entry, if the index is encoded as a secondary index.
  // The index must not be byte-swapped.
  ConstantPoolCacheEntry* main_entry_at(int i) const {
    int primary_index = i;
    if (is_secondary_index(i)) {
      // run through an extra level of indirection:
      int raw_index = decode_secondary_index(i);
      primary_index = entry_at(raw_index)->main_entry_index();
    }
    assert(!entry_at(primary_index)->is_secondary_entry(), "only one level of indirection");
    return entry_at(primary_index);
  }

  // GC support
  // If the _length field has not been set, the size of the
  // constantPoolCache cannot be correctly calculated.
  bool is_conc_safe()                            { return _is_conc_safe; }
  void set_is_conc_safe(bool v)                  { _is_conc_safe = v; }

  // Code generation
  static ByteSize base_offset()                  { return in_ByteSize(sizeof(constantPoolCacheOopDesc)); }
  static ByteSize entry_offset(int raw_index) {
    int index = raw_index;
    if (is_secondary_index(raw_index))
      index = decode_secondary_index(raw_index);
    return (base_offset() + ConstantPoolCacheEntry::size_in_bytes() * index);
  }

  // RedefineClasses() API support:
  // If any entry of this constantPoolCache points to any of
  // old_methods, replace it with the corresponding new_method.
  // trace_name_printed is set to true if the current call has
  // printed the klass name so that other routines in the adjust_*
  // group don't print the klass name.
  void adjust_method_entries(methodOop* old_methods, methodOop* new_methods,
                             int methods_length, bool * trace_name_printed);
};
