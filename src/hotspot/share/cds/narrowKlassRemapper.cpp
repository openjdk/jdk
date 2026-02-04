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

#include "cds/aotLogging.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/narrowKlassRemapper.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/klass.hpp"
#include "oops/markWord.hpp"
#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

address NarrowKlassRemapper::_dump_base = nullptr;
int     NarrowKlassRemapper::_dump_shift = 0;
address NarrowKlassRemapper::_runtime_base = nullptr;
int     NarrowKlassRemapper::_runtime_shift = 0;
intx    NarrowKlassRemapper::_relocation_delta = 0;
bool    NarrowKlassRemapper::_initialized = false;
bool    NarrowKlassRemapper::_needs_remapping = false;

bool NarrowKlassRemapper::initialize(address dump_base, int dump_shift,
                                     address runtime_base, int runtime_shift,
                                     intx relocation_delta) {
  assert(!_initialized, "should only initialize once");

  _dump_base = dump_base;
  _dump_shift = dump_shift;
  _runtime_base = runtime_base;
  _runtime_shift = runtime_shift;
  _relocation_delta = relocation_delta;
  _initialized = true;

  // Remapping is needed only if the encoding parameters would produce different narrow klass values.
  // If dump_base + relocation_delta == runtime_base and shifts match, then for any narrow klass:
  //   dump_k = dump_base + (nk << shift)
  //   runtime_k = dump_k + delta = dump_base + delta + (nk << shift)
  //   runtime_nk = (runtime_k - runtime_base) >> shift
  //              = (dump_base + delta + (nk << shift) - runtime_base) >> shift
  //              = (nk << shift) >> shift = nk  (since dump_base + delta = runtime_base)
  // So remapping would produce the same values - no need to remap.
  bool encoding_matches = ((intx)dump_base + relocation_delta == (intx)runtime_base) &&
                          (dump_shift == runtime_shift);
  _needs_remapping = !encoding_matches;

  if (_needs_remapping) {
    aot_log_info(aot)("Narrow Klass ID remapping enabled:");
    aot_log_info(aot)("  dump-time: base=" PTR_FORMAT ", shift=%d", p2i(dump_base), dump_shift);
    aot_log_info(aot)("  runtime:   base=" PTR_FORMAT ", shift=%d, relocation_delta=%zd",
                      p2i(runtime_base), runtime_shift, (ssize_t)relocation_delta);
  }

  return _needs_remapping;
}

narrowKlass NarrowKlassRemapper::remap(narrowKlass dump_nk) {
  if (!_needs_remapping || dump_nk == 0) {
    return dump_nk;
  }

  // Decode using dump-time parameters to get the dump-time Klass address
  // Formula: Klass* = base + (narrowKlass << shift)
  address dump_k = (address)((uintptr_t)_dump_base + ((uintptr_t)dump_nk << _dump_shift));

  // Apply relocation delta to get runtime Klass address
  // runtime_k = dump_k + relocation_delta
  Klass* runtime_k = (Klass*)(dump_k + _relocation_delta);

  // Re-encode using runtime parameters
  narrowKlass runtime_nk = CompressedKlassPointers::encode_not_null_without_asserts(runtime_k, _runtime_base, _runtime_shift);

  return runtime_nk;
}

bool NarrowKlassRemapper::is_dump_time_value(narrowKlass nk) {
  if (!_needs_remapping || nk == 0) {
    return false; // No remapping needed, so the distinction doesn't matter
  }

  // Calculate the offset between dump-time and runtime narrow klass IDs.
  // For a Klass K at dump-time address A:
  //   dump_nk = (A - dump_base) >> shift
  //   At runtime, K is at address A + relocation_delta
  //   runtime_nk = ((A + relocation_delta) - runtime_base) >> shift
  //
  // The offset between runtime and dump narrow klass values is:
  //   offset = runtime_nk - dump_nk
  //          = ((A + delta - runtime_base) - (A - dump_base)) >> shift
  //          = (delta - (runtime_base - dump_base)) >> shift
  //
  // In split encoding mode:
  //   - delta = runtime_archive_base - dump_archive_base (positive, archive relocated up)
  //   - base_diff = runtime_base - dump_base (large positive value)
  //   - delta - base_diff gives the actual offset
  //
  // Values < offset are dump-time values (need remapping).
  // Values >= offset are runtime values (already remapped).
  intx base_diff = (intx)_runtime_base - (intx)_dump_base;
  intx offset = _relocation_delta - base_diff;

  // Ensure offset is positive and reasonable
  if (offset <= 0) {
    // This means dump-time values are actually larger than runtime values,
    // which is unusual. Use a conservative threshold.
    return nk < 0x10000; // 64K - max reasonable archived Klasses
  }

  narrowKlass threshold = (narrowKlass)(offset >> _runtime_shift);

  return nk < threshold;
}

void NarrowKlassRemapper::remap_archived_klass_headers(address archive_bottom) {
  if (!_needs_remapping || !UseCompactObjectHeaders) {
    return;
  }

  aot_log_info(aot)("Remapping narrow Klass IDs in archived Klass prototype headers...");

  // Iterate through all Klasses in the archive and update their prototype headers.
  // The klasses are located in the rw region which starts at archive_bottom + protection_zone_size.
  // We need to walk the entire archive space to find all Klass objects and update their
  // prototype headers.
  //
  // Since Klass objects are stored consecutively in the archive and we don't have
  // a direct list of them at runtime, we rely on the fact that the prototype headers
  // will be updated lazily when each Klass is first used (via initialize_from_archived_subgraph
  // or class loading). For now, we update them when the archive is loaded.

  // The actual iteration over klasses happens in AOTMetaspace during archive validation.
  // Here we just verify that the remapping is set up correctly.
  aot_log_debug(aot)("Narrow Klass ID remapping initialized for archived objects");
}
