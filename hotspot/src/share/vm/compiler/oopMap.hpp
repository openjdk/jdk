/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_COMPILER_OOPMAP_HPP
#define SHARE_VM_COMPILER_OOPMAP_HPP

#include "code/compressedStream.hpp"
#include "code/vmreg.hpp"
#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

// Interface for generating the frame map for compiled code.  A frame map
// describes for a specific pc whether each register and frame stack slot is:
//   Oop         - A GC root for current frame
//   Dead        - Dead; can be Zapped for debugging
//   CalleeXX    - Callee saved; also describes which caller register is saved
//   DerivedXX   - A derived oop; original oop is described.
//
// OopMapValue describes a single OopMap entry

class frame;
class RegisterMap;
class DerivedPointerEntry;

class OopMapValue: public StackObj {
  friend class VMStructs;
private:
  short _value;
  int value() const                                 { return _value; }
  void set_value(int value)                         { _value = value; }
  short _content_reg;

public:
  // Constants
  enum { type_bits                = 4,
         register_bits            = BitsPerShort - type_bits };

  enum { type_shift               = 0,
         register_shift           = type_bits };

  enum { type_mask                = right_n_bits(type_bits),
         type_mask_in_place       = type_mask << type_shift,
         register_mask            = right_n_bits(register_bits),
         register_mask_in_place   = register_mask << register_shift };

  enum oop_types {              // must fit in type_bits
         unused_value =0,       // powers of 2, for masking OopMapStream
         oop_value = 1,
         narrowoop_value = 2,
         callee_saved_value = 4,
         derived_oop_value= 8 };

  // Constructors
  OopMapValue () { set_value(0); set_content_reg(VMRegImpl::Bad()); }
  OopMapValue (VMReg reg, oop_types t) { set_reg_type(reg,t); }
  OopMapValue (VMReg reg, oop_types t, VMReg reg2) { set_reg_type(reg,t); set_content_reg(reg2); }
  OopMapValue (CompressedReadStream* stream) { read_from(stream); }

  // Archiving
  void write_on(CompressedWriteStream* stream) {
    stream->write_int(value());
    if(is_callee_saved() || is_derived_oop()) {
      stream->write_int(content_reg()->value());
    }
  }

  void read_from(CompressedReadStream* stream) {
    set_value(stream->read_int());
    if(is_callee_saved() || is_derived_oop()) {
      set_content_reg(VMRegImpl::as_VMReg(stream->read_int(), true));
    }
  }

  // Querying
  bool is_oop()               { return mask_bits(value(), type_mask_in_place) == oop_value; }
  bool is_narrowoop()           { return mask_bits(value(), type_mask_in_place) == narrowoop_value; }
  bool is_callee_saved()      { return mask_bits(value(), type_mask_in_place) == callee_saved_value; }
  bool is_derived_oop()       { return mask_bits(value(), type_mask_in_place) == derived_oop_value; }

  void set_oop()              { set_value((value() & register_mask_in_place) | oop_value); }
  void set_narrowoop()          { set_value((value() & register_mask_in_place) | narrowoop_value); }
  void set_callee_saved()     { set_value((value() & register_mask_in_place) | callee_saved_value); }
  void set_derived_oop()      { set_value((value() & register_mask_in_place) | derived_oop_value); }

  VMReg reg() const { return VMRegImpl::as_VMReg(mask_bits(value(), register_mask_in_place) >> register_shift); }
  oop_types type() const      { return (oop_types)mask_bits(value(), type_mask_in_place); }

  static bool legal_vm_reg_name(VMReg p) {
    return (p->value()  == (p->value() & register_mask));
  }

  void set_reg_type(VMReg p, oop_types t) {
    set_value((p->value() << register_shift) | t);
    assert(reg() == p, "sanity check" );
    assert(type() == t, "sanity check" );
  }


  VMReg content_reg() const       { return VMRegImpl::as_VMReg(_content_reg, true); }
  void set_content_reg(VMReg r)   { _content_reg = r->value(); }

  // Physical location queries
  bool is_register_loc()      { return reg()->is_reg(); }
  bool is_stack_loc()         { return reg()->is_stack(); }

  // Returns offset from sp.
  int stack_offset() {
    assert(is_stack_loc(), "must be stack location");
    return reg()->reg2stack();
  }

  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
};


class OopMap: public ResourceObj {
  friend class OopMapStream;
  friend class VMStructs;
 private:
  int  _pc_offset; // offset in the code that this OopMap corresponds to
  int  _omv_count; // number of OopMapValues in the stream
  CompressedWriteStream* _write_stream;

  debug_only( OopMapValue::oop_types* _locs_used; int _locs_length;)

  // Accessors
  int omv_count() const                       { return _omv_count; }
  void set_omv_count(int value)               { _omv_count = value; }
  void increment_count()                      { _omv_count++; }
  CompressedWriteStream* write_stream() const { return _write_stream; }
  void set_write_stream(CompressedWriteStream* value) { _write_stream = value; }

 private:
  enum DeepCopyToken { _deep_copy_token };
  OopMap(DeepCopyToken, OopMap* source);  // used only by deep_copy

 public:
  OopMap(int frame_size, int arg_count);

  // pc-offset handling
  int offset() const     { return _pc_offset; }
  void set_offset(int o) { _pc_offset = o; }
  int count() const { return _omv_count; }
  int data_size() const  { return write_stream()->position(); }
  address data() const { return write_stream()->buffer(); }

  // Check to avoid double insertion
  debug_only(OopMapValue::oop_types locs_used( int indx ) { return _locs_used[indx]; })

  // Construction
  // frame_size units are stack-slots (4 bytes) NOT intptr_t; we can name odd
  // slots to hold 4-byte values like ints and floats in the LP64 build.
  void set_oop  ( VMReg local);
  void set_value( VMReg local);
  void set_narrowoop(VMReg local);
  void set_dead ( VMReg local);
  void set_callee_saved( VMReg local, VMReg caller_machine_register );
  void set_derived_oop ( VMReg local, VMReg derived_from_local_register );
  void set_xxx(VMReg reg, OopMapValue::oop_types x, VMReg optional);

  int heap_size() const;
  void copy_data_to(address addr) const;
  OopMap* deep_copy();

  bool has_derived_pointer() const PRODUCT_RETURN0;

  bool legal_vm_reg_name(VMReg local) {
     return OopMapValue::legal_vm_reg_name(local);
  }

  // Printing
  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
  bool equals(const OopMap* other) const;
};


class OopMapSet : public ResourceObj {
  friend class VMStructs;
 private:
  int _om_count;
  int _om_size;
  OopMap** _om_data;

  int om_count() const              { return _om_count; }
  void set_om_count(int value)      { _om_count = value; }
  void increment_count()            { _om_count++; }
  int om_size() const               { return _om_size; }
  void set_om_size(int value)       { _om_size = value; }
  OopMap** om_data() const          { return _om_data; }
  void set_om_data(OopMap** value)  { _om_data = value; }
  void grow_om_data();
  void set(int index,OopMap* value) { assert((index == 0) || ((index > 0) && (index < om_size())),"bad index"); _om_data[index] = value; }

 public:
  OopMapSet();

  // returns the number of OopMaps in this OopMapSet
  int size() const            { return _om_count; }
  // returns the OopMap at a given index
  OopMap* at(int index) const { assert((index >= 0) && (index <= om_count()),"bad index"); return _om_data[index]; }

  // Collect OopMaps.
  void add_gc_map(int pc, OopMap* map);

  // Returns the only oop map. Used for reconstructing
  // Adapter frames during deoptimization
  OopMap* singular_oop_map();

  // returns OopMap in that is anchored to the pc
  OopMap* find_map_at_offset(int pc_offset) const;

  int heap_size() const;

  // Methods oops_do() and all_do() filter out NULL oops and
  // oop == Universe::narrow_oop_base() before passing oops
  // to closures.

  // Iterates through frame for a compiled method
  static void oops_do            (const frame* fr,
                                  const RegisterMap* reg_map, OopClosure* f);
  static void update_register_map(const frame* fr, RegisterMap *reg_map);

  // Iterates through frame for a compiled method for dead ones and values, too
  static void all_do(const frame* fr, const RegisterMap* reg_map,
                     OopClosure* oop_fn,
                     void derived_oop_fn(oop* base, oop* derived),
                     OopClosure* value_fn);

  // Printing
  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
};

class ImmutableOopMapBuilder;

class ImmutableOopMap {
  friend class OopMapStream;
  friend class VMStructs;
#ifdef ASSERT
  friend class ImmutableOopMapBuilder;
#endif
private:
  int _count; // contains the number of entries in this OopMap

  address data_addr() const { return (address) this + sizeof(ImmutableOopMap); }
public:
  ImmutableOopMap(const OopMap* oopmap);

  bool has_derived_pointer() const PRODUCT_RETURN0;
  int count() const { return _count; }
#ifdef ASSERT
  int nr_of_bytes() const; // this is an expensive operation, only used in debug builds
#endif

  // Printing
  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
};

class ImmutableOopMapSet;
class ImmutableOopMap;
class OopMapSet;

class ImmutableOopMapPair {
  friend class VMStructs;
private:
  int _pc_offset; // program counter offset from the beginning of the method
  int _oopmap_offset; // offset in the data in the ImmutableOopMapSet where the ImmutableOopMap is located
public:
  ImmutableOopMapPair(int pc_offset, int oopmap_offset) : _pc_offset(pc_offset), _oopmap_offset(oopmap_offset) {
    assert(pc_offset >= 0 && oopmap_offset >= 0, "check");
  }
  const ImmutableOopMap* get_from(const ImmutableOopMapSet* set) const;

  int pc_offset() const { return _pc_offset; }
  int oopmap_offset() const { return _oopmap_offset; }
};

class ImmutableOopMapSet {
  friend class VMStructs;
private:
  int _count; // nr of ImmutableOopMapPairs in the Set
  int _size; // nr of bytes including ImmutableOopMapSet itself

  address data() const { return (address) this + sizeof(*this) + sizeof(ImmutableOopMapPair) * _count; }

public:
  ImmutableOopMapSet(const OopMapSet* oopmap_set, int size) : _count(oopmap_set->size()), _size(size) {}

  ImmutableOopMap* oopmap_at_offset(int offset) const {
    assert(offset >= 0 && offset < _size, "must be within boundaries");
    address addr = data() + offset;
    return (ImmutableOopMap*) addr;
  }

  ImmutableOopMapPair* get_pairs() const { return (ImmutableOopMapPair*) ((address) this + sizeof(*this)); }

  static ImmutableOopMapSet* build_from(const OopMapSet* oopmap_set);

  const ImmutableOopMap* find_map_at_offset(int pc_offset) const;

  const ImmutableOopMapPair* pair_at(int index) const { assert(index >= 0 && index < _count, "check"); return &get_pairs()[index]; }

  int count() const { return _count; }
  int nr_of_bytes() const { return _size; }

  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
};

class OopMapStream : public StackObj {
 private:
  CompressedReadStream* _stream;
  int _mask;
  int _size;
  int _position;
  bool _valid_omv;
  OopMapValue _omv;
  void find_next();

 public:
  OopMapStream(OopMap* oop_map, int oop_types_mask = OopMapValue::type_mask_in_place);
  OopMapStream(const ImmutableOopMap* oop_map, int oop_types_mask = OopMapValue::type_mask_in_place);
  bool is_done()                        { if(!_valid_omv) { find_next(); } return !_valid_omv; }
  void next()                           { find_next(); }
  OopMapValue current()                 { return _omv; }
#ifdef ASSERT
  int stream_position() const           { return _stream->position(); }
#endif
};

class ImmutableOopMapBuilder {
private:
  class Mapping;

private:
  const OopMapSet* _set;
  const OopMap* _empty;
  const OopMap* _last;
  int _empty_offset;
  int _last_offset;
  int _offset;
  int _required;
  Mapping* _mapping;
  ImmutableOopMapSet* _new_set;

  /* Used for bookkeeping when building ImmutableOopMaps */
  class Mapping : public ResourceObj {
  public:
    enum kind_t { OOPMAP_UNKNOWN = 0, OOPMAP_NEW = 1, OOPMAP_EMPTY = 2, OOPMAP_DUPLICATE = 3 };

    kind_t _kind;
    int _offset;
    int _size;
    const OopMap* _map;
    const OopMap* _other;

    Mapping() : _kind(OOPMAP_UNKNOWN), _offset(-1), _size(-1), _map(NULL) {}

    void set(kind_t kind, int offset, int size, const OopMap* map = 0, const OopMap* other = 0) {
      _kind = kind;
      _offset = offset;
      _size = size;
      _map = map;
      _other = other;
    }
  };

public:
  ImmutableOopMapBuilder(const OopMapSet* set);

  int heap_size();
  ImmutableOopMapSet* build();
  ImmutableOopMapSet* generate_into(address buffer);
private:
  bool is_empty(const OopMap* map) const {
    return map->count() == 0;
  }

  bool is_last_duplicate(const OopMap* map) {
    if (_last != NULL && _last->count() > 0 && _last->equals(map)) {
      return true;
    }
    return false;
  }

#ifdef ASSERT
  void verify(address buffer, int size, const ImmutableOopMapSet* set);
#endif

  bool has_empty() const {
    return _empty_offset != -1;
  }

  int size_for(const OopMap* map) const;
  void fill_pair(ImmutableOopMapPair* pair, const OopMap* map, int offset, const ImmutableOopMapSet* set);
  int fill_map(ImmutableOopMapPair* pair, const OopMap* map, int offset, const ImmutableOopMapSet* set);
  void fill(ImmutableOopMapSet* set, int size);
};


// Derived pointer support. This table keeps track of all derived points on a
// stack.  It is cleared before each scavenge/GC.  During the traversal of all
// oops, it is filled in with references to all locations that contains a
// derived oop (assumed to be very few).  When the GC is complete, the derived
// pointers are updated based on their base pointers new value and an offset.
#if defined(COMPILER2) || INCLUDE_JVMCI
class DerivedPointerTable : public AllStatic {
  friend class VMStructs;
 private:
   static GrowableArray<DerivedPointerEntry*>* _list;
   static bool _active;                      // do not record pointers for verify pass etc.
 public:
  static void clear();                       // Called before scavenge/GC
  static void add(oop *derived, oop *base);  // Called during scavenge/GC
  static void update_pointers();             // Called after  scavenge/GC
  static bool is_empty()                     { return _list == NULL || _list->is_empty(); }
  static bool is_active()                    { return _active; }
  static void set_active(bool value)         { _active = value; }
};

// A utility class to temporarily "deactivate" the DerivedPointerTable.
// (Note: clients are responsible for any MT-safety issues)
class DerivedPointerTableDeactivate: public StackObj {
 private:
  bool _active;
 public:
  DerivedPointerTableDeactivate() {
    _active = DerivedPointerTable::is_active();
    if (_active) {
      DerivedPointerTable::set_active(false);
    }
  }

  ~DerivedPointerTableDeactivate() {
    assert(!DerivedPointerTable::is_active(),
           "Inconsistency: not MT-safe");
    if (_active) {
      DerivedPointerTable::set_active(true);
    }
  }
};
#endif // COMPILER2 || INCLUDE_JVMCI

#endif // SHARE_VM_COMPILER_OOPMAP_HPP
