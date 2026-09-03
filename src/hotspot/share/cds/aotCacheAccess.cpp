/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "memory/virtualspace.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"

bool AOTCacheAccess::can_generate_aot_code(address addr) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");
  return ArchiveBuilder::is_active() && ArchiveBuilder::current()->has_been_archived(addr);
}

bool AOTCacheAccess::can_generate_aot_code_for(InstanceKlass* ik) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");
  if (!ArchiveBuilder::is_active()) {
    return false;
  }
  ArchiveBuilder* builder = ArchiveBuilder::current();
  if (!builder->has_been_archived((address)ik)) {
    return false;
  }
  if (ik->defined_by_other_loaders()) {
    return false;
  }
  return true;
}

Klass* AOTCacheAccess::try_narrow_ptr_to_klass(narrowPtr narrowp) {
  Metadata* metadata = try_narrow_ptr_to_metadata(narrowp, sizeof(Klass));
  if (metadata == nullptr) {
    return nullptr;
  }
  Klass* k = (Klass*)metadata;
  return Klass::is_valid_aot_klass(k) ? k : nullptr;
}

Method* AOTCacheAccess::try_narrow_ptr_to_method(narrowPtr narrowp) {
  Metadata* metadata = try_narrow_ptr_to_metadata(narrowp, sizeof(Method));
  if (metadata == nullptr) {
    return nullptr;
  }
  Method* m = (Method*)metadata;
  return Method::is_valid_method(m) ? m : nullptr;
}

Metadata* AOTCacheAccess::try_narrow_ptr_to_metadata(narrowPtr narrowp, size_t size) {
  if (narrowp == AOTCompressedPointers::null()) {
    return nullptr;
  }
  uintptr_t base = SharedBaseAddress;
  uintptr_t low_bound  = p2u(MetaspaceObj::aot_metaspace_base());
  uintptr_t high_bound = p2u(MetaspaceObj::aot_metaspace_top());
  if (base > low_bound || low_bound > high_bound) { // paranoid check
    return nullptr;
  }
  size_t low_offset  = low_bound  - base;
  size_t high_offset = high_bound - base;

  size_t offset = AOTCompressedPointers::get_byte_offset(narrowp);
  if (offset == 0 || offset > AOTCompressedPointers::MaxMetadataOffsetBytes ||
      offset < low_offset || offset >= high_offset ||
      size > (high_offset - offset)) {
    return nullptr;
  }

  Metadata* meta = reinterpret_cast<Metadata*>(base + offset);
  assert(Metaspace::in_aot_cache(meta), "must be");
  return meta;
}

#if INCLUDE_CDS_JAVA_HEAP
int AOTCacheAccess::get_archived_object_permanent_index(oop obj) {
  return HeapShared::get_root_index(obj); // -1 if obj is not a root.
}

oop AOTCacheAccess::get_archived_object(int permanent_index) {
  oop o = HeapShared::get_root(permanent_index);
  assert(oopDesc::is_oop_or_null(o), "sanity");
  return o;
}

#endif // INCLUDE_CDS_JAVA_HEAP

void* AOTCacheAccess::allocate_aot_code_region(size_t size) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");
  return (void*)ArchiveBuilder::ac_region_alloc(size);
}

static size_t _aot_code_region_size = 0;

size_t AOTCacheAccess::get_aot_code_region_size() {
  return _aot_code_region_size;
}

void AOTCacheAccess::set_aot_code_region_size(size_t sz) {
  _aot_code_region_size = sz;
}

bool AOTCacheAccess::map_aot_code_region(ReservedSpace rs) {
  FileMapInfo* static_mapinfo = FileMapInfo::current_info();
  assert(UseSharedSpaces && static_mapinfo != nullptr, "must be");
  return static_mapinfo->map_aot_code_region(rs);
}

void AOTCacheAccess::unmap_aot_code_region() {
  FileMapInfo* static_mapinfo = FileMapInfo::current_info();
  assert(UseSharedSpaces && static_mapinfo != nullptr, "must be");
  return static_mapinfo->unmap_aot_code_region();
}

bool AOTCacheAccess::is_aot_code_region_empty() {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");
  return ArchiveBuilder::current()->ac_region()->is_empty();
}

void AOTCacheAccess::set_pointer(address* ptr, address value) {
  ArchiveBuilder* builder = ArchiveBuilder::current();
  if (value != nullptr && !builder->is_in_buffer_space(value)) {
    value = builder->get_buffered_addr(value);
  }
  *ptr = value;
  ArchivePtrMarker::mark_pointer(ptr);
}
