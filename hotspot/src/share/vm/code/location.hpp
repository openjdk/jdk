/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

// A Location describes a concrete machine variable location
// (such as integer or floating point register or a stack-held
// variable). Used when generating debug-information for nmethods.
//
// Encoding:
//
// bits:
//  Where:  [15]
//  Type:   [14..12]
//  Offset: [11..0]

class Location VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 public:
  enum Where {
    on_stack,
    in_register
  };

  enum Type {
    normal,                     // Ints, floats, double halves
    oop,                        // Oop (please GC me!)
    int_in_long,                // Integer held in long register
    lng,                        // Long held in one register
    float_in_dbl,               // Float held in double register
    dbl,                        // Double held in one register
    addr,                       // JSR return address
    invalid                     // Invalid location
  };


 private:
  enum {
    OFFSET_MASK  = (jchar) 0x0FFF,
    OFFSET_SHIFT = 0,
    TYPE_MASK    = (jchar) 0x7000,
    TYPE_SHIFT   = 12,
    WHERE_MASK   = (jchar) 0x8000,
    WHERE_SHIFT  = 15
  };

  uint16_t _value;

  // Create a bit-packed Location
  Location(Where where_, Type type_, unsigned offset_) {
    set(where_, type_, offset_);
    assert( where () == where_ , "" );
    assert( type  () == type_  , "" );
    assert( offset() == offset_, "" );
  }

  inline void set(Where where_, Type type_, unsigned offset_) {
    _value = (uint16_t) ((where_  << WHERE_SHIFT) |
                         (type_   << TYPE_SHIFT)  |
                         ((offset_ << OFFSET_SHIFT) & OFFSET_MASK));
  }

 public:

  // Stack location Factory.  Offset is 4-byte aligned; remove low bits
  static Location new_stk_loc( Type t, int offset ) { return Location(on_stack,t,offset>>LogBytesPerInt); }
  // Register location Factory
  static Location new_reg_loc( Type t, VMReg reg ) { return Location(in_register, t, reg->value()); }
  // Default constructor
  Location() { set(on_stack,invalid,(unsigned) -1); }

  // Bit field accessors
  Where where()  const { return (Where)       ((_value & WHERE_MASK)  >> WHERE_SHIFT);}
  Type  type()   const { return (Type)        ((_value & TYPE_MASK)   >> TYPE_SHIFT); }
  unsigned offset() const { return (unsigned) ((_value & OFFSET_MASK) >> OFFSET_SHIFT); }

  // Accessors
  bool is_register() const    { return where() == in_register; }
  bool is_stack() const       { return where() == on_stack;    }

  int stack_offset() const    { assert(where() == on_stack,    "wrong Where"); return offset()<<LogBytesPerInt; }
  int register_number() const { assert(where() == in_register, "wrong Where"); return offset()   ; }

  VMReg reg() const { assert(where() == in_register, "wrong Where"); return VMRegImpl::as_VMReg(offset())   ; }

  // Printing
  void print_on(outputStream* st) const;

  // Serialization of debugging information
  Location(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // check
  static bool legal_offset_in_bytes(int offset_in_bytes);
};
