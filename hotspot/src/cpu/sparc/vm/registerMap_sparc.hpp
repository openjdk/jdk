/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_REGISTERMAP_SPARC_HPP
#define CPU_SPARC_VM_REGISTERMAP_SPARC_HPP

// machine-dependent implemention for register maps
  friend class frame;
  friend class MethodHandles;

 private:
  intptr_t* _window;         // register window save area (for L and I regs)
  intptr_t* _younger_window; // previous save area (for O regs, if needed)

  address pd_location(VMReg reg) const;
  void pd_clear();
  void pd_initialize_from(const RegisterMap* map) {
    _window         = map->_window;
    _younger_window = map->_younger_window;
    _location_valid[0] = 0;  // avoid the shift_individual_registers game
  }
  void pd_initialize() {
    _window = NULL;
    _younger_window = NULL;
    _location_valid[0] = 0;  // avoid the shift_individual_registers game
  }
  void shift_window(intptr_t* sp, intptr_t* younger_sp) {
    _window         = sp;
    _younger_window = younger_sp;
    // Throw away locations for %i, %o, and %l registers:
    // But do not throw away %g register locs.
    if (_location_valid[0] != 0)  shift_individual_registers();
  }
  void shift_individual_registers();
  // When popping out of compiled frames, we make all IRegs disappear.
  void make_integer_regs_unsaved() { _location_valid[0] = 0; }

#endif // CPU_SPARC_VM_REGISTERMAP_SPARC_HPP
