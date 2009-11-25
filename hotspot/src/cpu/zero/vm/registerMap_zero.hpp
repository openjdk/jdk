/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

  // machine-dependent implemention for register maps
  friend class frame;

 private:
  // This is the hook for finding a register in an "well-known" location,
  // such as a register block of a predetermined format.
  // Since there is none, we just return NULL.
  // See registerMap_sparc.hpp for an example of grabbing registers
  // from register save areas of a standard layout.
  address pd_location(VMReg reg) const { return NULL; }

  // no PD state to clear or copy:
  void pd_clear() {}
  void pd_initialize() {}
  void pd_initialize_from(const RegisterMap* map) {}
