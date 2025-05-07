/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotCacheAccess.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/stringTable.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "memory/virtualspace.hpp"
#include "oops/instanceKlass.hpp"

void* AOTCacheAccess::allocate_aot_code_region(size_t size) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");
  return (void*)ArchiveBuilder::ac_region_alloc(size);
}

size_t AOTCacheAccess::get_aot_code_region_size() {
  assert(CDSConfig::is_using_archive(), "must be");
  FileMapInfo* mapinfo = FileMapInfo::current_info();
  assert(mapinfo != nullptr, "must be");
  return mapinfo->region_at(MetaspaceShared::ac)->used_aligned();
}

bool AOTCacheAccess::map_aot_code_region(ReservedSpace rs) {
  FileMapInfo* static_mapinfo = FileMapInfo::current_info();
  assert(UseSharedSpaces && static_mapinfo != nullptr, "must be");
  return static_mapinfo->map_aot_code_region(rs);
}
