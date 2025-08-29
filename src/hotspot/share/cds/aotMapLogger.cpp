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

#include "cds/aotMapLogger.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/filemap.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/growableArray.hpp"

bool AOTMapLogger::_is_logging_at_bootstrap;
bool AOTMapLogger::_is_runtime_logging;
intx AOTMapLogger::_buffer_to_requested_delta;
intx AOTMapLogger::_requested_to_mapped_metadata_delta;
size_t AOTMapLogger::_num_root_segments;
size_t AOTMapLogger::_num_obj_arrays_logged;
GrowableArrayCHeap<AOTMapLogger::FakeOop, mtClass>* AOTMapLogger::_roots;
ArchiveHeapInfo* AOTMapLogger::_dumptime_heap_info;

class AOTMapLogger::RequestedMetadataAddr {
  address _raw_addr;

public:
  RequestedMetadataAddr(address raw_addr) : _raw_addr(raw_addr) {}

  address raw_addr() const { return _raw_addr; }

  Klass* to_real_klass() const {
    if (_raw_addr == nullptr) {
      return nullptr;
    }

    if (_is_runtime_logging) {
      return (Klass*)(_raw_addr + _requested_to_mapped_metadata_delta);
    } else {
      ArchiveBuilder* builder = ArchiveBuilder::current();
      address buffered_addr = builder->requested_to_buffered(_raw_addr);
      address klass = builder->get_source_addr(buffered_addr);
      return (Klass*)klass;
    }
  }
}; // AOTMapLogger::RequestedMetadataAddr

void AOTMapLogger::ergo_initialize() {
  if (!CDSConfig::is_dumping_archive() && CDSConfig::is_using_archive() && log_is_enabled(Info, aot, map)) {
    _is_logging_at_bootstrap = true;
    if (FLAG_IS_DEFAULT(ArchiveRelocationMode)) {
      FLAG_SET_ERGO(ArchiveRelocationMode, 0);
    } else if (ArchiveRelocationMode != 0) {
      log_warning(aot, map)("Addresses in the AOT map may be incorrect for -XX:ArchiveRelocationMode=%d.", ArchiveRelocationMode);
    }
  }
}

void AOTMapLogger::dumptime_log(ArchiveBuilder* builder, FileMapInfo* mapinfo,
                                ArchiveHeapInfo* heap_info,
                                char* bitmap, size_t bitmap_size_in_bytes) {
  _is_runtime_logging = false;
  _buffer_to_requested_delta =  ArchiveBuilder::current()->buffer_to_requested_delta();
  _num_root_segments = mapinfo->heap_root_segments().count();
  _dumptime_heap_info = heap_info;

  log_file_header(mapinfo);

  DumpRegion* rw_region = &builder->_rw_region;
  DumpRegion* ro_region = &builder->_ro_region;

  dumptime_log_metaspace_region("rw region", rw_region, &builder->_rw_src_objs);
  dumptime_log_metaspace_region("ro region", ro_region, &builder->_ro_src_objs);

  address bitmap_end = address(bitmap + bitmap_size_in_bytes);
  log_region_range("bitmap", address(bitmap), bitmap_end, nullptr);
  log_as_hex((address)bitmap, bitmap_end, nullptr);

#if INCLUDE_CDS_JAVA_HEAP
  if (heap_info->is_used()) {
    dumptime_log_heap_region(heap_info);
  }
#endif

  log_info(aot, map)("[End of AOT cache map]");
}

// This class is used to find the location and type of all the
// archived metaspace objects.
class AOTMapLogger::RuntimeGatherArchivedMetaspaceObjs : public UniqueMetaspaceClosure {
  GrowableArrayCHeap<ArchivedObjInfo, mtClass> _objs;

  static int compare_objs_by_addr(ArchivedObjInfo* a, ArchivedObjInfo* b) {
    intx diff = a->_src_addr - b->_src_addr;
    if (diff < 0) {
      return -1;
    } else if (diff == 0) {
      return 0;
    } else {
      return 1;
    }
  }

public:
  GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs() { return &_objs; }

  virtual bool do_unique_ref(Ref* ref, bool read_only) {
    ArchivedObjInfo info;
    info._src_addr = ref->obj();
    info._buffered_addr = ref->obj();
    info._requested_addr = ref->obj();
    info._bytes = ref->size() * BytesPerWord;
    info._type = ref->msotype();
    _objs.append(info);

    return true; // keep iterating
  }

  void finish() {
    UniqueMetaspaceClosure::finish();
    _objs.sort(compare_objs_by_addr);
  }
}; // AOTMapLogger::RuntimeGatherArchivedMetaspaceObjs

void AOTMapLogger::runtime_log(FileMapInfo* static_mapinfo, FileMapInfo* dynamic_mapinfo) {
  _is_runtime_logging = true;
  _requested_to_mapped_metadata_delta = static_mapinfo->relocation_delta();

  ResourceMark rm;
  RuntimeGatherArchivedMetaspaceObjs gatherer;

  if (log_is_enabled(Debug, aot, map)) {
    // The metaspace objects in the AOT cache are stored as a stream of bytes. For space
    // saving, we don't store a complete index that tells us where one object ends and
    // another object starts. There's also no type information.
    //
    // However, we can rebuild our index by iterating over all the objects using
    // MetaspaceClosure, starting from the dictionary of Klasses in SystemDictionaryShared.
    GrowableArray<Klass*> klasses;
    SystemDictionaryShared::get_all_archived_classes(/*is_static*/true, &klasses);
    if (dynamic_mapinfo != nullptr) {
      SystemDictionaryShared::get_all_archived_classes(/*is_static*/false, &klasses);
    }

    for (int i = 0; i < klasses.length(); i++) {
      gatherer.push(klasses.adr_at(i));
    }
    gatherer.finish();
  }

  runtime_log(static_mapinfo, gatherer.objs());
  if (dynamic_mapinfo != nullptr) {
    runtime_log(dynamic_mapinfo, gatherer.objs());
  }
}

void AOTMapLogger::runtime_log(FileMapInfo* mapinfo, GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs) {
  log_file_header(mapinfo);

  runtime_log_metaspace_regions(mapinfo, objs);

#if INCLUDE_CDS_JAVA_HEAP
  if (mapinfo->has_heap_region() && CDSConfig::is_loading_heap()) {
    _num_root_segments = mapinfo->heap_root_segments().count();
    runtime_log_heap_region(mapinfo);
  }
#endif

  log_info(aot, map)("[End of map]");
}

void AOTMapLogger::dumptime_log_metaspace_region(const char* name, DumpRegion* region,
                                                 const ArchiveBuilder::SourceObjList* src_objs) {
  address region_base = address(region->base());
  address region_top  = address(region->top());
  log_region_range(name, region_base, region_top, region_base + _buffer_to_requested_delta);
  if (log_is_enabled(Debug, aot, map)) {
    GrowableArrayCHeap<ArchivedObjInfo, mtClass> objs;
    for (int i = 0; i < src_objs->objs()->length(); i++) {
      ArchiveBuilder::SourceObjInfo* src_info = src_objs->at(i);
      ArchivedObjInfo info;
      info._src_addr = src_info->source_addr();
      info._buffered_addr = src_info->buffered_addr();
      info._requested_addr = info._buffered_addr + _buffer_to_requested_delta;
      info._bytes = src_info->size_in_bytes();
      info._type = src_info->msotype();
      objs.append(info);
    }

    log_metaspace_objects_impl(address(region->base()), address(region->end()), &objs, 0, objs.length());
  }
}

void AOTMapLogger::runtime_log_metaspace_regions(FileMapInfo* mapinfo, GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs) {
  FileMapRegion* rw = mapinfo->region_at(MetaspaceShared::rw);
  FileMapRegion* ro = mapinfo->region_at(MetaspaceShared::ro);

  address rw_base = address(rw->mapped_base());
  address rw_end  = address(rw->mapped_end());
  address ro_base = address(ro->mapped_base());
  address ro_end  = address(ro->mapped_end());

  int first_rw_index = -1;
  int first_ro_index = -1;
  int last_ro_index = -1;

  if (log_is_enabled(Debug, aot, map)) {
    int i = 0;
    for (; i < objs->length(); i++) {
      address p = objs->at(i)._src_addr;
      if (p < rw_base) {
        // We are printing the dynamic archive but found an object in the static archive
        precond(!mapinfo->is_static());
        continue;
      }
      if (first_rw_index < 0) {
        first_rw_index = i;
        continue;
      }
      if (p < ro_base) {
        continue;
      }
      if (first_ro_index < 0) {
        first_ro_index = i;
        continue;
      }
      if (p < ro_end) {
        continue;
      } else {
        last_ro_index = i;
        break;
      }
    }
  }

  if (last_ro_index < 0) {
    last_ro_index = objs->length();
  }

  log_region_range("rw", rw_base, rw_end, rw_base - _requested_to_mapped_metadata_delta);
  if (log_is_enabled(Debug, aot, map)) {
    log_metaspace_objects_impl(rw_base, rw_end, objs, first_rw_index, first_ro_index);
  }

  log_region_range("ro", ro_base, ro_end, ro_base - _requested_to_mapped_metadata_delta);
  if (log_is_enabled(Debug, aot, map)) {
    log_metaspace_objects_impl(ro_base, ro_end, objs, first_ro_index, last_ro_index);
  }
}

void AOTMapLogger::log_file_header(FileMapInfo* mapinfo) {
  const char* type;
  if (mapinfo->is_static()) {
    if (CDSConfig::new_aot_flags_used()) {
      type = "AOT cache";
    } else {
      type = "Static CDS archive";
    }
  } else {
    type = "Dynamic CDS archive";
  }

  log_info(aot, map)("%s map for %s", type, mapinfo->full_path());

  address header = address(mapinfo->header());
  address header_end = header + mapinfo->header()->header_size();

  log_region_range("header", header, header_end, nullptr);
  LogStreamHandle(Info, aot, map) lsh;
  mapinfo->print(&lsh);
  log_as_hex(header, header_end, nullptr);
}

// Log information about a region, whose address at dump time is [base .. top). At
// runtime, this region will be mapped to requested_base. requested_base is nullptr if this
// region will be mapped at os-selected addresses (such as the bitmap region), or will
// be accessed with os::read (the header).
void AOTMapLogger::log_region_range(const char* name, address base, address top, address requested_base) {
  size_t size = top - base;
  base = requested_base;
  if (requested_base == nullptr) {
    top = (address)size;
  } else {
    top = requested_base + size;
  }
  log_info(aot, map)("[%-18s " PTR_FORMAT " - " PTR_FORMAT " %9zu bytes]",
                     name, p2i(base), p2i(top), size);
}

#define _LOG_PREFIX PTR_FORMAT ": @@ %-17s %d"

void AOTMapLogger::log_metaspace_objects_impl(address region_base, address region_end, GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs,
                                              int start_idx, int end_idx) {
  address last_obj_base = region_base;
  address last_obj_end  = region_base;
  Thread* current = Thread::current();

  for (int i = start_idx; i < end_idx; i++) {
    ArchivedObjInfo& info = objs->at(i);
    address src = info._src_addr;
    address buffered_addr = info._buffered_addr;
    address requested_addr = info._requested_addr;
    int bytes = info._bytes;
    MetaspaceObj::Type type = info._type;
    const char* type_name = MetaspaceObj::type_name(type);

    log_as_hex(last_obj_base, buffered_addr, last_obj_base + _buffer_to_requested_delta);

    switch (type) {
    case MetaspaceObj::ClassType:
      log_klass((Klass*)src, requested_addr, type_name, bytes, current);
      break;
    case MetaspaceObj::ConstantPoolType:
      log_constant_pool((ConstantPool*)src, requested_addr, type_name, bytes, current);
      break;
    case MetaspaceObj::ConstantPoolCacheType:
      log_constant_pool_cache((ConstantPoolCache*)src, requested_addr, type_name, bytes, current);
      break;
    case MetaspaceObj::ConstMethodType:
      log_const_method((ConstMethod*)src, requested_addr, type_name, bytes, current);
      break;
    case MetaspaceObj::MethodType:
      log_method((Method*)src, requested_addr, type_name, bytes, current);
      break;
    case MetaspaceObj::SymbolType:
      log_symbol((Symbol*)src, requested_addr, type_name, bytes, current);
      break;
    default:
      log_debug(aot, map)(_LOG_PREFIX, p2i(requested_addr), type_name, bytes);
      break;
    }

    last_obj_base = buffered_addr;
    last_obj_end  = buffered_addr + bytes;
  }

  log_as_hex(last_obj_base, last_obj_end, last_obj_base + _buffer_to_requested_delta);
  if (last_obj_end < region_end) {
    log_debug(aot, map)(PTR_FORMAT ": @@ Misc data %zu bytes",
                        p2i(last_obj_end + _buffer_to_requested_delta),
                        size_t(region_end - last_obj_end));
    log_as_hex(last_obj_end, region_end, last_obj_end + _buffer_to_requested_delta);
  }
}

void AOTMapLogger::log_constant_pool(ConstantPool* cp, address requested_addr,
                                     const char* type_name, int bytes, Thread* current) {
  ResourceMark rm(current);
  log_debug(aot, map)(_LOG_PREFIX " %s", p2i(requested_addr), type_name, bytes,
                      cp->pool_holder()->external_name());
}

void AOTMapLogger::log_constant_pool_cache(ConstantPoolCache* cpc, address requested_addr,
                                           const char* type_name, int bytes, Thread* current) {
  ResourceMark rm(current);
  log_debug(aot, map)(_LOG_PREFIX " %s", p2i(requested_addr), type_name, bytes,
                      cpc->constant_pool()->pool_holder()->external_name());
}

void AOTMapLogger::log_const_method(ConstMethod* cm, address requested_addr, const char* type_name,
                                    int bytes, Thread* current) {
  ResourceMark rm(current);
  log_debug(aot, map)(_LOG_PREFIX " %s", p2i(requested_addr), type_name, bytes,  cm->method()->external_name());
}

void AOTMapLogger::log_klass(Klass* k, address requested_addr, const char* type_name,
                             int bytes, Thread* current) {
  ResourceMark rm(current);
  log_debug(aot, map)(_LOG_PREFIX " %s", p2i(requested_addr), type_name, bytes, k->external_name());
}

void AOTMapLogger::log_method(Method* m, address requested_addr, const char* type_name,
                              int bytes, Thread* current) {
  ResourceMark rm(current);
  log_debug(aot, map)(_LOG_PREFIX " %s", p2i(requested_addr), type_name, bytes,  m->external_name());
}

void AOTMapLogger::log_symbol(Symbol* s, address requested_addr, const char* type_name,
                              int bytes, Thread* current) {
  ResourceMark rm(current);
  log_debug(aot, map)(_LOG_PREFIX " %s", p2i(requested_addr), type_name, bytes,
                      s->as_quoted_ascii());
}

#undef _LOG_PREFIX

// Log all the data [base...top). Pretend that the base address
// will be mapped to requested_base at run-time.
void AOTMapLogger::log_as_hex(address base, address top, address requested_base, bool is_heap) {
  assert(top >= base, "must be");

  LogStreamHandle(Trace, aot, map) lsh;
  if (lsh.is_enabled()) {
    int unitsize = sizeof(address);
    if (is_heap && UseCompressedOops) {
      // This makes the compressed oop pointers easier to read, but
      // longs and doubles will be split into two words.
      unitsize = sizeof(narrowOop);
    }
    os::print_hex_dump(&lsh, base, top, unitsize, /* print_ascii=*/true, /* bytes_per_line=*/32, requested_base);
  }
}

#if INCLUDE_CDS_JAVA_HEAP
// FakeOop (and subclasses FakeMirror, FakeString, FakeObjArray, FakeTypeArray) are used to traverse
// and print the (image of) heap objects stored in the AOT cache. These objects are different than regular oops:
// - They do not reside inside the range of the heap.
// - For +UseCompressedOops: pointers may use a different narrowOop encoding: see FakeOop::read_oop_at(narrowOop*)
// - For -UseCompressedOops: pointers are not direct: see FakeOop::read_oop_at(oop*)
//
// Hence, in general, we cannot use regular oop API (such as oopDesc::obj_field()) on these objects. There
// are a few rare case where regular oop API work, but these are all guarded with the raw_oop() method and
// should be used with care.
class AOTMapLogger::FakeOop {
  static int _requested_shift;
  static intx _buffer_to_requested_delta;
  static address _buffer_start;
  static address _buffer_end;
  static uint64_t _buffer_start_narrow_oop; // The encoded narrow oop for the objects at _buffer_start

  address _buffer_addr;

  static void assert_range(address buffer_addr) {
    assert(_buffer_start <= buffer_addr && buffer_addr < _buffer_end, "range check");
  }

  address* field_addr(int field_offset) {
    return (address*)(_buffer_addr + field_offset);
  }

protected:
  RequestedMetadataAddr metadata_field(int field_offset) {
    return RequestedMetadataAddr(*(address*)(field_addr(field_offset)));
  }

  // Return an "oop" pointer so we can use APIs that accept regular oops. This
  // must be used with care, as only a limited number of APIs can work with oops that
  // live outside of the range of the heap.
  oop raw_oop() { return cast_to_oop(_buffer_addr); }

public:
  static void init_globals(address requested_base, address requested_start, int requested_shift,
                           address buffer_start, address buffer_end) {
    _requested_shift = requested_shift;
    _buffer_to_requested_delta = requested_start - buffer_start;
    _buffer_start = buffer_start;
    _buffer_end = buffer_end;

    precond(requested_start >= requested_base);
    if (UseCompressedOops) {
      _buffer_start_narrow_oop = (uint64_t)(pointer_delta(requested_start, requested_base, 1)) >> _requested_shift;
      assert(_buffer_start_narrow_oop < 0xffffffff, "sanity");
    } else {
      _buffer_start_narrow_oop = 0xdeadbeed;
    }
  }

  FakeOop() : _buffer_addr(nullptr) {}

  FakeOop(address buffer_addr) : _buffer_addr(buffer_addr) {
    if (_buffer_addr != nullptr) {
      assert_range(_buffer_addr);
    }
  }

  FakeMirror& as_mirror();
  FakeObjArray& as_obj_array();
  FakeString& as_string();
  FakeTypeArray& as_type_array();

  RequestedMetadataAddr klass() {
    address rk = (address)real_klass();
    if (_is_runtime_logging) {
      return RequestedMetadataAddr(rk - _requested_to_mapped_metadata_delta);
    } else {
      ArchiveBuilder* builder = ArchiveBuilder::current();
      return builder->to_requested(builder->get_buffered_addr(rk));
    }
  }

  Klass* real_klass() {
    assert(UseCompressedClassPointers, "heap archiving requires UseCompressedClassPointers");
    if (_is_runtime_logging) {
      return raw_oop()->klass();
    } else {
      return ArchiveHeapWriter::real_klass_of_buffered_oop(_buffer_addr);
    }
  }

  // in heap words
  size_t size() {
    if (_is_runtime_logging) {
      return raw_oop()->size_given_klass(real_klass());
    } else {
      return ArchiveHeapWriter::size_of_buffered_oop(_buffer_addr);
    }
  }

  bool is_array() { return real_klass()->is_array_klass(); }
  bool is_null() { return _buffer_addr == nullptr; }

  int array_length() {
    precond(is_array());
    return arrayOop(raw_oop())->length();
  }

  address requested_addr() {
    return _buffer_addr + _buffer_to_requested_delta;
  }

  uint32_t as_narrow_oop_value() {
    precond(UseCompressedOops);
    if (_buffer_addr == nullptr) {
      return 0;
    }
    uint64_t pd = (uint64_t)(pointer_delta(_buffer_addr, _buffer_start, 1));
    return checked_cast<uint32_t>(_buffer_start_narrow_oop + (pd >> _requested_shift));
  }

  FakeOop read_oop_at(narrowOop* addr) { // +UseCompressedOops
    uint64_t n = (uint64_t)(*addr);
    if (n == 0) {
      return FakeOop(nullptr);
    } else {
      precond(n >= _buffer_start_narrow_oop);
      address value = _buffer_start + ((n - _buffer_start_narrow_oop) << _requested_shift);
      return FakeOop(value);
    }
  }

  FakeOop read_oop_at(oop* addr) { // -UseCompressedOops
    address requested_value = cast_from_oop<address>(*addr);
    if (requested_value == nullptr) {
      return FakeOop(nullptr);
    } else {
      return FakeOop(requested_value - _buffer_to_requested_delta);
    }
  }

  FakeOop obj_field(int field_offset) {
    if (UseCompressedOops) {
      return read_oop_at(raw_oop()->field_addr<narrowOop>(field_offset));
    } else {
      return read_oop_at(raw_oop()->field_addr<oop>(field_offset));
    }
  }

  void print_non_oop_field(outputStream* st, fieldDescriptor* fd) {
    // fd->print_on_for() works for non-oop fields in fake oops
    precond(fd->field_type() != T_ARRAY && fd->field_type() != T_OBJECT);
    fd->print_on_for(st, raw_oop());
  }
}; // AOTMapLogger::FakeOop

class AOTMapLogger::FakeMirror : public AOTMapLogger::FakeOop {
public:
  void print_class_signature_on(outputStream* st);

  Klass* real_mirrored_klass() {
    RequestedMetadataAddr mirrored_klass = metadata_field(java_lang_Class::klass_offset());
    return mirrored_klass.to_real_klass();
  }

  int static_oop_field_count() {
    return java_lang_Class::static_oop_field_count(raw_oop());
  }
}; // AOTMapLogger::FakeMirror

class AOTMapLogger::FakeObjArray : public AOTMapLogger::FakeOop {
  objArrayOop raw_objArrayOop() {
    return (objArrayOop)raw_oop();
  }

public:
  int length() {
    return raw_objArrayOop()->length();
  }
  FakeOop obj_at(int i) {
    if (UseCompressedOops) {
      return read_oop_at(raw_objArrayOop()->obj_at_addr<narrowOop>(i));
    } else {
      return read_oop_at(raw_objArrayOop()->obj_at_addr<oop>(i));
    }
  }
}; // AOTMapLogger::FakeObjArray

class AOTMapLogger::FakeString : public AOTMapLogger::FakeOop {
public:
  bool is_latin1() {
    jbyte coder = raw_oop()->byte_field(java_lang_String::coder_offset());
    assert(CompactStrings || coder == java_lang_String::CODER_UTF16, "Must be UTF16 without CompactStrings");
    return coder == java_lang_String::CODER_LATIN1;
  }

  FakeTypeArray value();

  int length();
  void print_on(outputStream* st, int max_length = MaxStringPrintSize);
}; // AOTMapLogger::FakeString

class AOTMapLogger::FakeTypeArray : public AOTMapLogger::FakeOop {
  typeArrayOop raw_typeArrayOop() {
    return (typeArrayOop)raw_oop();
  }

public:
  void print_elements_on(outputStream* st) {
    TypeArrayKlass::cast(real_klass())->oop_print_elements_on(raw_typeArrayOop(), st);
  }

  int length() { return raw_typeArrayOop()->length(); }
  jbyte byte_at(int i) { return raw_typeArrayOop()->byte_at(i); }
  jchar char_at(int i) { return raw_typeArrayOop()->char_at(i); }
}; // AOTMapLogger::FakeTypeArray

AOTMapLogger::FakeMirror& AOTMapLogger::FakeOop::as_mirror() {
  precond(real_klass() == vmClasses::Class_klass());
  return (FakeMirror&)*this;
}

AOTMapLogger::FakeObjArray& AOTMapLogger::FakeOop::as_obj_array() {
  precond(real_klass()->is_objArray_klass());
  return (FakeObjArray&)*this;
}

AOTMapLogger::FakeTypeArray& AOTMapLogger::FakeOop::as_type_array() {
  precond(real_klass()->is_typeArray_klass());
  return (FakeTypeArray&)*this;
}

AOTMapLogger::FakeString& AOTMapLogger::FakeOop::as_string() {
  precond(real_klass() == vmClasses::String_klass());
  return (FakeString&)*this;
}

void AOTMapLogger::FakeMirror::print_class_signature_on(outputStream* st) {
  ResourceMark rm;
  RequestedMetadataAddr requested_klass = metadata_field(java_lang_Class::klass_offset());
  Klass* real_klass = requested_klass.to_real_klass();

  if (real_klass == nullptr) {
    // This is a primitive mirror (Java expressions of int.class, long.class, void.class, etc);
    RequestedMetadataAddr requested_array_klass = metadata_field(java_lang_Class::array_klass_offset());
    Klass* real_array_klass = requested_array_klass.to_real_klass();
    if (real_array_klass == nullptr) {
      st->print(" V"); // The special mirror for void.class that doesn't have any representation in C++
    } else {
      precond(real_array_klass->is_typeArray_klass());
      st->print(" %c", real_array_klass->name()->char_at(1));
    }
  } else {
    const char* class_name = real_klass->name()->as_C_string();
    if (real_klass->is_instance_klass()) {
      st->print(" L%s;", class_name);
    } else {
      st->print(" %s", class_name);
    }
    if (real_klass->has_aot_initialized_mirror()) {
      st->print(" (aot-inited)");
    }
  }
}

AOTMapLogger::FakeTypeArray AOTMapLogger::FakeString::value() {
  return obj_field(java_lang_String::value_offset()).as_type_array();
}

int AOTMapLogger::FakeString::length() {
  FakeTypeArray v = value();
  if (v.is_null()) {
    return 0;
  }
  int arr_length = v.length();
  if (!is_latin1()) {
    assert((arr_length & 1) == 0, "should be even for UTF16 string");
    arr_length >>= 1; // convert number of bytes to number of elements
  }
  return arr_length;
}

void AOTMapLogger::FakeString::print_on(outputStream* st, int max_length) {
  FakeTypeArray v = value();
  int length = this->length();
  bool is_latin1 = this->is_latin1();
  bool abridge = length > max_length;

  st->print("\"");
  for (int index = 0; index < length; index++) {
    // If we need to abridge and we've printed half the allowed characters
    // then jump to the tail of the string.
    if (abridge && index >= max_length / 2) {
      st->print(" ... (%d characters ommitted) ... ", length - 2 * (max_length / 2));
      index = length - (max_length / 2);
      abridge = false; // only do this once
    }
    jchar c = (!is_latin1) ?  v.char_at(index) :
                             ((jchar) v.byte_at(index)) & 0xff;
    if (c < ' ') {
      st->print("\\x%02X", c); // print control characters e.g. \x0A
    } else {
      st->print("%c", c);
    }
  }
  st->print("\"");

  if (length > max_length) {
    st->print(" (abridged) ");
  }
}

class AOTMapLogger::ArchivedFieldPrinter : public FieldClosure {
  FakeOop _fake_oop;
  outputStream* _st;
public:
  ArchivedFieldPrinter(FakeOop fake_oop, outputStream* st) : _fake_oop(fake_oop), _st(st) {}

  void do_field(fieldDescriptor* fd) {
    _st->print(" - ");
    BasicType ft = fd->field_type();
    switch (ft) {
    case T_ARRAY:
    case T_OBJECT:
      {
        fd->print_on(_st); // print just the name and offset
        FakeOop field_value = _fake_oop.obj_field(fd->offset());
        print_oop_info_cr(_st, field_value);
      }
      break;
    default:
      _fake_oop.print_non_oop_field(_st, fd); // name, offset, value
      _st->cr();
    }
  }
}; // AOTMapLogger::ArchivedFieldPrinter

int AOTMapLogger::FakeOop::_requested_shift;
intx AOTMapLogger::FakeOop::_buffer_to_requested_delta;
address AOTMapLogger::FakeOop::_buffer_start;
address AOTMapLogger::FakeOop::_buffer_end;
uint64_t AOTMapLogger::FakeOop::_buffer_start_narrow_oop;

void AOTMapLogger::dumptime_log_heap_region(ArchiveHeapInfo* heap_info) {
  MemRegion r = heap_info->buffer_region();
  address buffer_start = address(r.start()); // start of the current oop inside the buffer
  address buffer_end = address(r.end());

  address requested_base = UseCompressedOops ? (address)CompressedOops::base() : (address)ArchiveHeapWriter::NOCOOPS_REQUESTED_BASE;
  address requested_start = UseCompressedOops ? ArchiveHeapWriter::buffered_addr_to_requested_addr(buffer_start) : requested_base;
  int requested_shift =  CompressedOops::shift();

  FakeOop::init_globals(requested_base, requested_start, requested_shift, buffer_start, buffer_end);

  log_region_range("heap", buffer_start, buffer_end, requested_start);
  log_oops(buffer_start, buffer_end);
}

void AOTMapLogger::runtime_log_heap_region(FileMapInfo* mapinfo) {
  ResourceMark rm;
  int heap_region_index = MetaspaceShared::hp;
  FileMapRegion* r = mapinfo->region_at(heap_region_index);
  size_t alignment = ObjectAlignmentInBytes;

  // Allocate a buffer and read the image of the archived heap region. This buffer is outside
  // of the real Java heap, so we must use FakeOop to access the contents of the archived heap objects.
  char* buffer = resource_allocate_bytes(r->used() + alignment);
  address buffer_start = (address)align_up(buffer, alignment);
  address buffer_end = buffer_start + r->used();
  if (!mapinfo->read_region(heap_region_index, (char*)buffer_start, r->used(), /* do_commit = */ false)) {
    log_error(aot)("Cannot read heap region; AOT map logging of heap objects failed");
    return;
  }

  address requested_base = UseCompressedOops ? (address)mapinfo->narrow_oop_base() : mapinfo->heap_region_requested_address();
  address requested_start = requested_base + r->mapping_offset();
  int requested_shift = mapinfo->narrow_oop_shift();

  FakeOop::init_globals(requested_base, requested_start, requested_shift, buffer_start, buffer_end);

  log_region_range("heap", buffer_start, buffer_end, requested_start);
  log_oops(buffer_start, buffer_end);
}

void AOTMapLogger::log_oops(address buffer_start, address buffer_end) {
  LogStreamHandle(Debug, aot, map) st;
  if (!st.is_enabled()) {
    return;
  }

  _roots = new GrowableArrayCHeap<FakeOop, mtClass>();
  _num_obj_arrays_logged = 0;

  for (address fop = buffer_start; fop < buffer_end; ) {
    FakeOop fake_oop(fop);
    st.print(PTR_FORMAT ": @@ Object ", p2i(fake_oop.requested_addr()));
    print_oop_info_cr(&st, fake_oop, /*print_requested_addr=*/false);

    LogStreamHandle(Trace, aot, map, oops) trace_st;
    if (trace_st.is_enabled()) {
      print_oop_details(fake_oop, &trace_st);
    }

    address next_fop = fop + fake_oop.size() * BytesPerWord;
    log_as_hex(fop, next_fop, fake_oop.requested_addr(), /*is_heap=*/true);

    fop = next_fop;
  }

  delete _roots;
}

void AOTMapLogger::print_oop_info_cr(outputStream* st, FakeOop fake_oop, bool print_requested_addr) {
  if (fake_oop.is_null()) {
    st->print_cr("null");
  } else {
    ResourceMark rm;
    Klass* real_klass = fake_oop.real_klass();
    address requested_addr = fake_oop.requested_addr();
    if (print_requested_addr) {
      st->print(PTR_FORMAT " ", p2i(requested_addr));
    }
    if (UseCompressedOops) {
      st->print("(0x%08x) ", fake_oop.as_narrow_oop_value());
    }
    if (fake_oop.is_array()) {
      int array_len = fake_oop.array_length();
      st->print_cr("%s length: %d", real_klass->external_name(), array_len);
    } else {
      st->print("%s", real_klass->external_name());

      if (real_klass == vmClasses::String_klass()) {
        st->print(" ");
        fake_oop.as_string().print_on(st);
      } else if (real_klass == vmClasses::Class_klass()) {
        fake_oop.as_mirror().print_class_signature_on(st);
      }

      st->cr();
    }
  }
}

// Print the fields of instanceOops, or the elements of arrayOops
void AOTMapLogger::print_oop_details(FakeOop fake_oop, outputStream* st) {
  Klass* real_klass = fake_oop.real_klass();

  st->print(" - klass: ");
  real_klass->print_value_on(st);
  st->print(" " PTR_FORMAT, p2i(fake_oop.klass().raw_addr()));
  st->cr();

  if (real_klass->is_typeArray_klass()) {
    fake_oop.as_type_array().print_elements_on(st);
  } else if (real_klass->is_objArray_klass()) {
    FakeObjArray fake_obj_array = fake_oop.as_obj_array();
    bool is_logging_root_segment = _num_obj_arrays_logged < _num_root_segments;

    for (int i = 0; i < fake_obj_array.length(); i++) {
      FakeOop elm = fake_obj_array.obj_at(i);
      if (is_logging_root_segment) {
        st->print(" root[%4d]: ", _roots->length());
        _roots->append(elm);
      } else {
        st->print(" -%4d: ", i);
      }
      print_oop_info_cr(st, elm);
    }
    _num_obj_arrays_logged ++;
  } else {
    st->print_cr(" - fields (%zu words):", fake_oop.size());

    ArchivedFieldPrinter print_field(fake_oop, st);
    InstanceKlass::cast(real_klass)->print_nonstatic_fields(&print_field);

    if (real_klass == vmClasses::Class_klass()) {
      FakeMirror fake_mirror = fake_oop.as_mirror();

      st->print(" - signature: ");
      fake_mirror.print_class_signature_on(st);
      st->cr();

      Klass* real_mirrored_klass = fake_mirror.real_mirrored_klass();
      if (real_mirrored_klass != nullptr && real_mirrored_klass->is_instance_klass()) {
        InstanceKlass* real_mirrored_ik = InstanceKlass::cast(real_mirrored_klass);

        ConstantPoolCache* cp_cache = real_mirrored_ik->constants()->cache();
        if (!_is_runtime_logging) {
          cp_cache = ArchiveBuilder::current()->get_buffered_addr(cp_cache);
        }
        int rr_root_index = cp_cache->archived_references_index();
        st->print(" - resolved_references: ");
        if (rr_root_index >= 0) {
          FakeOop resolved_references = _roots->at(rr_root_index);
          print_oop_info_cr(st, resolved_references);
        } else {
          st->print("null");
        }

        st->print_cr("- ---- static fields (%d):", fake_mirror.static_oop_field_count());
        real_mirrored_ik->do_local_static_fields(&print_field);
      }
    }
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP
