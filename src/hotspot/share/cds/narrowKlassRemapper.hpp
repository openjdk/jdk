/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_NARROWKLASSREMAPPER_HPP
#define SHARE_CDS_NARROWKLASSREMAPPER_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class Klass;

// NarrowKlassRemapper remaps narrow Klass IDs from dump-time encoding to runtime encoding.
//
// Background:
// With UseCompactObjectHeaders, we have a 22-bit narrowKlass + up to 10-bit shift,
// which gives us a maximum encoding range of 4GB. When the CDS archive is created,
// the narrow Klass IDs are pre-computed using the dump-time encoding (base and shift).
//
// At runtime, if the archive is mapped at a different address, the narrow Klass
// encoding may be different. This class handles the remapping of narrow Klass IDs
// in archived heap objects and Klass prototype headers.
//
// Remapping is only needed when:
// 1. UseCompactObjectHeaders is enabled, AND
// 2. The dump-time narrow klass base/shift differs from runtime base/shift
//
class NarrowKlassRemapper : AllStatic {
  // Dump-time encoding parameters (from the archive header)
  static address _dump_base;
  static int     _dump_shift;

  // Runtime encoding parameters (from CompressedKlassPointers)
  static address _runtime_base;
  static int     _runtime_shift;

  // Relocation delta: runtime_klass_address = dump_klass_address + _relocation_delta
  static intx    _relocation_delta;

  // Whether remapping is needed and has been initialized
  static bool    _initialized;
  static bool    _needs_remapping;

public:
  // Initialize the remapper with dump-time and runtime encoding parameters.
  // relocation_delta = mapped_archive_base - requested_archive_base
  // Returns true if remapping is needed.
  static bool initialize(address dump_base, int dump_shift,
                         address runtime_base, int runtime_shift,
                         intx relocation_delta);

  // Returns true if narrow Klass ID remapping is needed.
  static bool needs_remapping() { return _needs_remapping; }

  // Returns true if the remapper has been initialized.
  static bool is_initialized() { return _initialized; }

  // Accessors for debugging
  static address dump_base() { return _dump_base; }
  static int dump_shift() { return _dump_shift; }
  static address runtime_base() { return _runtime_base; }
  static int runtime_shift() { return _runtime_shift; }
  static intx relocation_delta() { return _relocation_delta; }

  // Remap a narrow Klass ID from dump-time encoding to runtime encoding.
  // If remapping is not needed, returns the same value.
  static narrowKlass remap(narrowKlass dump_nk);

  // Returns true if the given narrow Klass ID appears to be a dump-time value
  // (i.e., hasn't been remapped yet). This is used to avoid double-remapping.
  static bool is_dump_time_value(narrowKlass nk);

  // Remap narrow Klass IDs in all archived Klass prototype headers.
  // This must be called after the archive is mapped and before any
  // archived objects are accessed.
  static void remap_archived_klass_headers(address archive_bottom);
};

#endif // SHARE_CDS_NARROWKLASSREMAPPER_HPP
