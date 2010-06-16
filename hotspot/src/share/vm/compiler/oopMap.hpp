/*
 * Copyright (c) 1998, 2009, Oracle and/or its affiliates. All rights reserved.
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

// Interface for generating the frame map for compiled code.  A frame map
// describes for a specific pc whether each register and frame stack slot is:
//   Oop         - A GC root for current frame
//   Value       - Live non-oop, non-float value: int, either half of double
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
  enum { type_bits                = 5,
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
         value_value = 2,
         narrowoop_value = 4,
         callee_saved_value = 8,
         derived_oop_value= 16 };

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
  bool is_value()             { return mask_bits(value(), type_mask_in_place) == value_value; }
  bool is_narrowoop()           { return mask_bits(value(), type_mask_in_place) == narrowoop_value; }
  bool is_callee_saved()      { return mask_bits(value(), type_mask_in_place) == callee_saved_value; }
  bool is_derived_oop()       { return mask_bits(value(), type_mask_in_place) == derived_oop_value; }

  void set_oop()              { set_value((value() & register_mask_in_place) | oop_value); }
  void set_value()            { set_value((value() & register_mask_in_place) | value_value); }
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
  int  _pc_offset;
  int  _omv_count;
  int  _omv_data_size;
  unsigned char* _omv_data;
  CompressedWriteStream* _write_stream;

  debug_only( OopMapValue::oop_types* _locs_used; int _locs_length;)

  // Accessors
  unsigned char* omv_data() const             { return _omv_data; }
  void set_omv_data(unsigned char* value)     { _omv_data = value; }
  int omv_data_size() const                   { return _omv_data_size; }
  void set_omv_data_size(int value)           { _omv_data_size = value; }
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
  void copy_to(address addr);
  OopMap* deep_copy();

  bool has_derived_pointer() const PRODUCT_RETURN0;

  bool legal_vm_reg_name(VMReg local) {
     return OopMapValue::legal_vm_reg_name(local);
  }

  // Printing
  void print_on(outputStream* st) const;
  void print() const { print_on(tty); }
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
  void copy_to(address addr);

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
  OopMapStream(OopMap* oop_map);
  OopMapStream(OopMap* oop_map, int oop_types_mask);
  bool is_done()                        { if(!_valid_omv) { find_next(); } return !_valid_omv; }
  void next()                           { find_next(); }
  OopMapValue current()                 { return _omv; }
};


// Derived pointer support. This table keeps track of all derived points on a
// stack.  It is cleared before each scavenge/GC.  During the traversal of all
// oops, it is filled in with references to all locations that contains a
// derived oop (assumed to be very few).  When the GC is complete, the derived
// pointers are updated based on their base pointers new value and an offset.
#ifdef COMPILER2
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
#endif // COMPILER2
