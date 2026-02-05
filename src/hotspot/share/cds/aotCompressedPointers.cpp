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

#include "cds/aotCompressedPointers.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cds_globals.hpp"
#include "utilities/debug.hpp"

#if 0
address AOTCompressedPointers::_encoding_base = nullptr;
address AOTCompressedPointers::_encoding_top  = nullptr;

void AOTCompressedPointers::set_encoding_range(address encoding_base, address encoding_top) {
  precond(pointer_delta(encoding_base, encoding_top, 1) < MaxMetadataOffsetBytes);
  _encoding_base = encoding_base;
  _encoding_top = encoding_top;
}
#endif

size_t AOTCompressedPointers::compute_byte_offset(address p) {
  if (AOTMetaspace::in_aot_cache(p)) {
    assert(CDSConfig::is_dumping_dynamic_archive(), "must be");
    return pointer_delta(p, reinterpret_cast<address>(SharedBaseAddress), 1);
  }
  ArchiveBuilder* builder = ArchiveBuilder::current();
  if (!builder->is_in_buffer_space(p)) {
    // p must be a "source" address
    p = builder->get_buffered_addr(p);
  }

  precond(builder->is_in_buffer_space(p));
  return pointer_delta(p, builder->buffer_bottom(), 1);
}
