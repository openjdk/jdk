/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_C1_MACROASSEMBLER_SPARC_HPP
#define CPU_SPARC_VM_C1_MACROASSEMBLER_SPARC_HPP

  void pd_init() { /* nothing to do */ }

 public:
   void try_allocate(
    Register obj,                      // result: pointer to object after successful allocation
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Register t2,                       // temp register
    Label&   slow_case                 // continuation point if fast allocation fails
  );

  void initialize_header(Register obj, Register klass, Register len, Register t1, Register t2);
  void initialize_body(Register base, Register index);

  // locking/unlocking
  void lock_object  (Register Rmark, Register Roop, Register Rbox, Register Rscratch, Label& slow_case);
  void unlock_object(Register Rmark, Register Roop, Register Rbox,                    Label& slow_case);

  void initialize_object(
    Register obj,                      // result: pointer to object after successful allocation
    Register klass,                    // object klass
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Register t2,                       // temp register
    bool is_tlab_allocated             // the object was allocated in a TLAB; relevant for the implementation of ZeroTLAB
  );

  // allocation of fixed-size objects
  // (can also be used to allocate fixed-size arrays, by setting
  // hdr_size correctly and storing the array length afterwards)
  void allocate_object(
    Register obj,                      // result: pointer to object after successful allocation
    Register t1,                       // temp register
    Register t2,                       // temp register
    Register t3,                       // temp register
    int      hdr_size,                 // object header size in words
    int      obj_size,                 // object size in words
    Register klass,                    // object klass
    Label&   slow_case                 // continuation point if fast allocation fails
  );

  enum {
    max_array_allocation_length = 0x01000000 // sparc friendly value, requires sethi only
  };

  // allocation of arrays
  void allocate_array(
    Register obj,                      // result: pointer to array after successful allocation
    Register len,                      // array length
    Register t1,                       // temp register
    Register t2,                       // temp register
    Register t3,                       // temp register
    int      hdr_size,                 // object header size in words
    int      elt_size,                 // element size in bytes
    Register klass,                    // object klass
    Label&   slow_case                 // continuation point if fast allocation fails
  );

  // invalidates registers in this window
  void invalidate_registers(bool iregisters, bool lregisters, bool oregisters,
                            Register preserve1 = noreg, Register preserve2 = noreg);

  // This platform only uses signal-based null checks. The Label is not needed.
  void null_check(Register r, Label *Lnull = NULL) { MacroAssembler::null_check(r); }

#endif // CPU_SPARC_VM_C1_MACROASSEMBLER_SPARC_HPP
