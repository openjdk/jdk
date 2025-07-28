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
#include "utilities/growableArray.hpp"

intx AOTMapLogger::_buffer_to_requested_delta;
intx  AOTMapLogger::_requested_to_mapped_metadata_delta;

class AOTMapLogger::RequestedMetadataAddr {
  address _raw_addr;

public:
  RequestedMetadataAddr(address raw_addr) : _raw_addr(raw_addr) {}

  address raw_addr() const { return _raw_addr; }

  Klass* to_real_klass() const {
    if (_raw_addr == nullptr) {
      return nullptr;
    }
    return (Klass*)(_raw_addr + _requested_to_mapped_metadata_delta);
  }
};

void AOTMapLogger::dumptime_log(ArchiveBuilder* builder, FileMapInfo* mapinfo,
                                ArchiveHeapInfo* heap_info,
                                char* bitmap, size_t bitmap_size_in_bytes) {
  _buffer_to_requested_delta =  ArchiveBuilder::current()->buffer_to_requested_delta();

  log_info(aot, map)("%s CDS archive map for %s", CDSConfig::is_dumping_static_archive() ? "Static" : "Dynamic", mapinfo->full_path());

  address header = address(mapinfo->header());
  address header_end = header + mapinfo->header()->header_size();
  log_region("header", header, header_end, nullptr);
  log_header(mapinfo);
  log_as_hex(header, header_end, nullptr);

  DumpRegion* rw_region = &builder->_rw_region;
  DumpRegion* ro_region = &builder->_ro_region;

  log_metaspace_region("rw region", rw_region, &builder->_rw_src_objs);
  log_metaspace_region("ro region", ro_region, &builder->_ro_src_objs);

  address bitmap_end = address(bitmap + bitmap_size_in_bytes);
  log_region("bitmap", address(bitmap), bitmap_end, nullptr);
  log_as_hex((address)bitmap, bitmap_end, nullptr);

#if INCLUDE_CDS_JAVA_HEAP
  if (heap_info->is_used()) {
    log_heap_region(heap_info);
  }
#endif

  log_info(aot, map)("[End of AOT cache map]");
};

// This class is used to find the location and type of all the
// archived metaspace objects.
class AOTMapLogger::GatherArchivedMetaspaceObjs : public UniqueMetaspaceClosure {
  GrowableArray<ArchivedObjInfo> _objs;

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
  GrowableArray<ArchivedObjInfo>* objs() { return &_objs; }

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
};

void AOTMapLogger::runtime_log(FileMapInfo* mapinfo) {
  _requested_to_mapped_metadata_delta = mapinfo->relocation_delta();

  address header = address(mapinfo->header());
  address header_end = header + mapinfo->header()->header_size();
  log_region("header", header, header_end, nullptr);
  log_header(mapinfo);
  log_as_hex(header, header_end, nullptr);

  runtime_log_metaspace_regions(mapinfo);

#if INCLUDE_CDS_JAVA_HEAP
  if (mapinfo->has_heap_region()) {
    runtime_log_heap_region(mapinfo);
  }
#endif
}

void AOTMapLogger::runtime_log_metaspace_regions(FileMapInfo* mapinfo) {
  FileMapRegion* rw = mapinfo->region_at(MetaspaceShared::rw);
  FileMapRegion* ro = mapinfo->region_at(MetaspaceShared::ro);

  address rw_base = address(rw->mapped_base());
  address rw_end  = address(rw->mapped_end());
  address ro_base = address(ro->mapped_base());
  address ro_end  = address(ro->mapped_end());

  ResourceMark rm;
  GatherArchivedMetaspaceObjs gatherer;
  GrowableArray<ArchivedObjInfo>* objs = nullptr;
  int first_ro_index = 0;

  if (log_is_enabled(Debug, aot, map)) {
    // The metaspace objects in the AOT cache are stored as a stream of bytes. For space
    // saving, we don't store a complete index that tells us where one object ends and
    // another object starts. There's also no type information.
    //
    // However, we can rebuild our index by iterating over all the objects using
    // MetaspaceClosure, starting from the dictionary of Klasses in SystemDictionaryShared.
    GrowableArray<Klass*> klasses;
    SystemDictionaryShared::get_all_archived_classes(mapinfo->is_static(), &klasses);
    for (int i = 0; i < klasses.length(); i++) {
      gatherer.push(klasses.adr_at(i));
    }
    gatherer.finish();

    // Divide the objects into two parts: RW and RO
    objs = gatherer.objs();
    int i = 0;
    for (; i < objs->length(); i++) {
      if (objs->at(i)._src_addr >= ro_base) {
        break;
      }
    }
    // i is now the index of the first object in the ro region. TODO: this doesn't work for dynamic archive yet ...
    first_ro_index = i;
  }

  log_region("rw", rw_base, rw_end, rw_base - _requested_to_mapped_metadata_delta);
  if (log_is_enabled(Debug, aot, map)) {
    log_metaspace_objects(rw_base, rw_end, objs, 0, first_ro_index);
  }

  log_region("ro", ro_base, ro_end, ro_base - _requested_to_mapped_metadata_delta);
  if (log_is_enabled(Debug, aot, map)) {
    log_metaspace_objects(ro_base, ro_end, objs, first_ro_index, objs->length());
  }
}

void AOTMapLogger::log_header(FileMapInfo* mapinfo) {
  LogStreamHandle(Info, aot, map) lsh;
  if (lsh.is_enabled()) {
    mapinfo->print(&lsh);
  }
}

// Log information about a region, whose address at dump time is [base .. top). At
// runtime, this region will be mapped to requested_base. requested_base is nullptr if this
// region will be mapped at os-selected addresses (such as the bitmap region), or will
// be accessed with os::read (the header).
//
// Note: across -Xshare:dump runs, base may be different, but requested_base should
// be the same as the archive contents should be deterministic.
void AOTMapLogger::log_region(const char* name, address base, address top, address requested_base) {
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

void AOTMapLogger::log_metaspace_region(const char* name, DumpRegion* region,
                                        const ArchiveBuilder::SourceObjList* src_objs) {
  address region_base = address(region->base());
  address region_top  = address(region->top());
  log_region(name, region_base, region_top, region_base + _buffer_to_requested_delta);
  if (log_is_enabled(Debug, aot, map)) {
    log_metaspace_objects(region, src_objs);
  }
}

#define _LOG_PREFIX PTR_FORMAT ": @@ %-17s %d"

void AOTMapLogger::log_metaspace_objects(DumpRegion* region, const ArchiveBuilder::SourceObjList* src_objs) {
  GrowableArray<ArchivedObjInfo> objs;

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

  log_metaspace_objects(address(region->base()), address(region->end()), &objs, 0, objs.length());
}

void AOTMapLogger::log_metaspace_objects(address region_base, address region_end, GrowableArray<ArchivedObjInfo>* objs, int start_idx, int end_idx) {
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

/*
  LogStreamHandle(Trace, aot, map) lsh;
  if (lsh.is_enabled()) {
    cp->print_on(&lsh);
  }
*/
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
void AOTMapLogger::log_heap_region(ArchiveHeapInfo* heap_info) {
  MemRegion r = heap_info->buffer_region();
  address start = address(r.start()); // start of the current oop inside the buffer
  address end = address(r.end());
  log_region("heap", start, end, ArchiveHeapWriter::buffered_addr_to_requested_addr(start));
  log_oops(heap_info, start, end);
}

void AOTMapLogger::log_oops(ArchiveHeapInfo* heap_info, address start, address end) {
  LogStreamHandle(Debug, aot, map) st;
  if (!st.is_enabled()) {
    return;
  }

  HeapRootSegments segments = heap_info->heap_root_segments();
  assert(segments.base_offset() == 0, "Sanity");

  for (size_t seg_idx = 0; seg_idx < segments.count(); seg_idx++) {
    address requested_start = ArchiveHeapWriter::buffered_addr_to_requested_addr(start);
    st.print_cr(PTR_FORMAT ": Heap roots segment [%d]",
                p2i(requested_start), segments.size_in_elems(seg_idx));
    start += segments.size_in_bytes(seg_idx);
  }
  log_heap_roots();

  while (start < end) {
    size_t byte_size;
    oop source_oop = ArchiveHeapWriter::buffered_addr_to_source_obj(start);
    address requested_start = ArchiveHeapWriter::buffered_addr_to_requested_addr(start);
    st.print(PTR_FORMAT ": @@ Object ", p2i(requested_start));

    if (source_oop != nullptr) {
      // This is a regular oop that got archived.
      // Don't print the requested addr again as we have just printed it at the beginning of the line.
      // Example:
      // 0x00000007ffd27938: @@ Object (0xfffa4f27) java.util.HashMap
      print_oop_info_cr(&st, source_oop, /*print_requested_addr=*/false);
      byte_size = source_oop->size() * BytesPerWord;
    } else if ((byte_size = ArchiveHeapWriter::get_filler_size_at(start)) > 0) {
      // We have a filler oop, which also does not exist in BufferOffsetToSourceObjectTable.
      // Example:
      // 0x00000007ffc3ffd8: @@ Object filler 40 bytes
      st.print_cr("filler %zu bytes", byte_size);
    } else {
      ShouldNotReachHere();
    }

    address oop_end = start + byte_size;
    log_as_hex(start, oop_end, requested_start, /*is_heap=*/true);

    if (source_oop != nullptr) {
      log_oop_details(heap_info, source_oop, /*buffered_addr=*/start);
    }
    start = oop_end;
  }
}

// ArchivedFieldPrinter is used to print the fields of archived objects. We can't
// use _source_obj->print_on(), because we want to print the oop fields
// in _source_obj with their requested addresses using print_oop_info_cr().
class AOTMapLogger::ArchivedFieldPrinter : public FieldClosure {
  ArchiveHeapInfo* _heap_info;
  outputStream* _st;
  oop _source_obj;
  address _buffered_addr;
public:
  ArchivedFieldPrinter(ArchiveHeapInfo* heap_info, outputStream* st, oop src_obj, address buffered_addr) :
    _heap_info(heap_info), _st(st), _source_obj(src_obj), _buffered_addr(buffered_addr) {}

  void do_field(fieldDescriptor* fd) {
    _st->print(" - ");
    BasicType ft = fd->field_type();
    switch (ft) {
    case T_ARRAY:
    case T_OBJECT:
      {
        fd->print_on(_st); // print just the name and offset
        oop obj = _source_obj->obj_field(fd->offset());
        if (java_lang_Class::is_instance(obj)) {
          obj = HeapShared::scratch_java_mirror(obj);
        }
        print_oop_info_cr(_st, obj);
      }
      break;
    default:
      if (ArchiveHeapWriter::is_marked_as_native_pointer(_heap_info, _source_obj, fd->offset())) {
        print_as_native_pointer(fd);
      } else {
        fd->print_on_for(_st, cast_to_oop(_buffered_addr)); // name, offset, value
        _st->cr();
      }
    }
  }

  void print_as_native_pointer(fieldDescriptor* fd) {
    LP64_ONLY(assert(fd->field_type() == T_LONG, "must be"));
    NOT_LP64 (assert(fd->field_type() == T_INT,  "must be"));

    // We have a field that looks like an integer, but it's actually a pointer to a MetaspaceObj.
    address source_native_ptr = (address)
        LP64_ONLY(_source_obj->long_field(fd->offset()))
        NOT_LP64( _source_obj->int_field (fd->offset()));
    ArchiveBuilder* builder = ArchiveBuilder::current();

    // The value of the native pointer at runtime.
    address requested_native_ptr = builder->to_requested(builder->get_buffered_addr(source_native_ptr));

    // The address of _source_obj at runtime
    oop requested_obj = ArchiveHeapWriter::source_obj_to_requested_obj(_source_obj);
    // The address of this field in the requested space
    assert(requested_obj != nullptr, "Attempting to load field from null oop");
    address requested_field_addr = cast_from_oop<address>(requested_obj) + fd->offset();

    fd->print_on(_st);
    _st->print_cr(PTR_FORMAT " (marked metadata pointer @" PTR_FORMAT " )",
                  p2i(requested_native_ptr), p2i(requested_field_addr));
  }
}; // ArchivedFieldPrinter

// Print the fields of instanceOops, or the elements of arrayOops
void AOTMapLogger::log_oop_details(ArchiveHeapInfo* heap_info, oop source_oop, address buffered_addr) {
  LogStreamHandle(Trace, aot, map, oops) st;
  if (st.is_enabled()) {
    Klass* source_klass = source_oop->klass();
    ArchiveBuilder* builder = ArchiveBuilder::current();
    Klass* requested_klass = builder->to_requested(builder->get_buffered_addr(source_klass));

    st.print(" - klass: ");
    source_klass->print_value_on(&st);
    st.print(" " PTR_FORMAT, p2i(requested_klass));
    st.cr();

    if (source_oop->is_typeArray()) {
      TypeArrayKlass::cast(source_klass)->oop_print_elements_on(typeArrayOop(source_oop), &st);
    } else if (source_oop->is_objArray()) {
      objArrayOop source_obj_array = objArrayOop(source_oop);
      for (int i = 0; i < source_obj_array->length(); i++) {
        st.print(" -%4d: ", i);
        oop obj = source_obj_array->obj_at(i);
        if (java_lang_Class::is_instance(obj)) {
          obj = HeapShared::scratch_java_mirror(obj);
        }
        print_oop_info_cr(&st, obj);
      }
    } else {
      st.print_cr(" - fields (%zu words):", source_oop->size());
      ArchivedFieldPrinter print_field(heap_info, &st, source_oop, buffered_addr);
      InstanceKlass::cast(source_klass)->print_nonstatic_fields(&print_field);

      if (java_lang_Class::is_instance(source_oop)) {
        oop scratch_mirror = source_oop;
        st.print(" - signature: ");
        print_class_signature_for_mirror(&st, scratch_mirror);
        st.cr();

        Klass* src_klass = java_lang_Class::as_Klass(scratch_mirror);
        if (src_klass != nullptr && src_klass->is_instance_klass()) {
          oop rr = HeapShared::scratch_resolved_references(InstanceKlass::cast(src_klass)->constants());
          st.print(" - archived_resolved_references: ");
          print_oop_info_cr(&st, rr);

          // We need to print the fields in the scratch_mirror, not the original mirror.
          // (if a class is not aot-initialized, static fields in its scratch mirror will be cleared).
          assert(scratch_mirror == HeapShared::scratch_java_mirror(src_klass->java_mirror()), "sanity");
          st.print_cr("- ---- static fields (%d):", java_lang_Class::static_oop_field_count(scratch_mirror));
          InstanceKlass::cast(src_klass)->do_local_static_fields(&print_field);
        }
      }
    }
  }
}

void AOTMapLogger::print_class_signature_for_mirror(outputStream* st, oop scratch_mirror) {
  assert(java_lang_Class::is_instance(scratch_mirror), "sanity");
  if (java_lang_Class::is_primitive(scratch_mirror)) {
    for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
      BasicType bt = (BasicType)i;
      if (!is_reference_type(bt) && scratch_mirror == HeapShared::scratch_java_mirror(bt)) {
        oop orig_mirror = Universe::java_mirror(bt);
        java_lang_Class::print_signature(orig_mirror, st);
        return;
      }
    }
    ShouldNotReachHere();
  }
  java_lang_Class::print_signature(scratch_mirror, st);
}

void AOTMapLogger::log_heap_roots() {
  LogStreamHandle(Trace, aot, map, oops) st;
  if (st.is_enabled()) {
    for (int i = 0; i < HeapShared::pending_roots()->length(); i++) {
      st.print("roots[%4d]: ", i);
      print_oop_info_cr(&st, HeapShared::pending_roots()->at(i));
    }
  }
}

// Example output:
// - The first number is the requested address (if print_requested_addr == true)
// - The second number is the narrowOop version of the requested address (if UseCompressedOops == true)
//     0x00000007ffc7e840 (0xfff8fd08) java.lang.Class Ljava/util/Array;
//     0x00000007ffc000f8 (0xfff8001f) [B length: 11
void AOTMapLogger::print_oop_info_cr(outputStream* st, oop source_oop, bool print_requested_addr) {
  if (source_oop == nullptr) {
    st->print_cr("null");
  } else {
    ResourceMark rm;
    oop requested_obj = ArchiveHeapWriter::source_obj_to_requested_obj(source_oop);
    if (print_requested_addr) {
      st->print(PTR_FORMAT " ", p2i(requested_obj));
    }
    if (UseCompressedOops) {
      st->print("(0x%08x) ", CompressedOops::narrow_oop_value(requested_obj));
    }
    if (source_oop->is_array()) {
      int array_len = arrayOop(source_oop)->length();
      st->print_cr("%s length: %d", source_oop->klass()->external_name(), array_len);
    } else {
      st->print("%s", source_oop->klass()->external_name());

      if (java_lang_String::is_instance(source_oop)) {
        st->print(" ");
        java_lang_String::print(source_oop, st);
      } else if (java_lang_Class::is_instance(source_oop)) {
        oop scratch_mirror = source_oop;

        st->print(" ");
        print_class_signature_for_mirror(st, scratch_mirror);

        Klass* src_klass = java_lang_Class::as_Klass(scratch_mirror);
        if (src_klass != nullptr && src_klass->is_instance_klass()) {
          InstanceKlass* buffered_klass =
            ArchiveBuilder::current()->get_buffered_addr(InstanceKlass::cast(src_klass));
          if (buffered_klass->has_aot_initialized_mirror()) {
            st->print(" (aot-inited)");
          }
        }
      }
      st->cr();
    }
  }
}

class FakeMirror;

class AOTMapLogger::FakeOop {
  static int _requested_shift;
  static intx _buffer_to_requested_delta;
  static address _buffer_base;
  static address _buffer_start;
  static address _buffer_end;

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

public:
  static void init(address requested_start, int requested_shift, address buffer_base, address buffer_start, address buffer_end) {
    _requested_shift = requested_shift;
    _buffer_to_requested_delta = requested_start - buffer_start;
    _buffer_base = buffer_base;
    _buffer_start = buffer_start;
    _buffer_end = buffer_end;
  }

  FakeOop(address buffer_addr) : _buffer_addr(buffer_addr) {
    if (_buffer_addr != nullptr) {
      assert_range(_buffer_addr);
    }
  }

  Klass* real_klass() {
    // TODO Dump time
    assert(UseCompressedClassPointers, "heap archiving requires UseCompressedClassPointers");
    return raw_oop()->klass();
  }

  RequestedMetadataAddr klass() {
    // TODO Dump time
    address rk = (address)real_klass();
    return RequestedMetadataAddr(rk - _requested_to_mapped_metadata_delta);
  }

  size_t size() {
    return raw_oop()->size_given_klass(real_klass());

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
    uint64_t pd = (uint64_t)(pointer_delta((void*)_buffer_addr, (void*)_buffer_base, 1));
    return checked_cast<uint32_t>(pd >> _requested_shift);
  }

  void print_string_on(outputStream* st);

  bool is_marked_as_native_pointer(int field_offset) {
    return false; // TODO
  }

  oop raw_oop() { return cast_to_oop(_buffer_addr); }

  FakeOop read_oop_at(narrowOop* addr) {
    narrowOop n = *addr;
    if (size_t(n) == 0) {
      return FakeOop(nullptr);
    } else {
      address value = _buffer_base + (size_t(n) << _requested_shift);
      return FakeOop(value);
    }
  }

  FakeOop obj_field(int field_offset) {
    if (UseCompressedOops) {
      narrowOop n = *raw_oop()->field_addr<narrowOop>(field_offset);
      if (size_t(n) == 0) {
        return FakeOop(nullptr);
      } else {
        address field_value = _buffer_base + (size_t(n) << _requested_shift);
        return FakeOop(field_value);
      }
    } else {
      return FakeOop(nullptr); // FIXME
    }
  }

  FakeMirror& as_mirror();
  FakeObjArray& as_obj_array();

};

class AOTMapLogger::FakeObjArray : public AOTMapLogger::FakeOop {
  objArrayOop raw_obj_array() {
    return (objArrayOop)raw_oop();
  }

public:
  int length() {
    return raw_obj_array()->length();
  }
  FakeOop obj_at(int i) {
    if (UseCompressedOops) {
      return read_oop_at(raw_obj_array()->obj_at_addr<narrowOop>(i));
    } else {
      return FakeOop(nullptr); // FIXME
    }
  }
};

class AOTMapLogger::FakeMirror : public AOTMapLogger::FakeOop {
public:
  void print_class_signature_on(outputStream* st);

  Klass* real_mirrored_klass() {
    RequestedMetadataAddr mirrored_klass = metadata_field(java_lang_Class::klass_offset());
    return mirrored_klass.to_real_klass();
  }
};

AOTMapLogger::FakeMirror& AOTMapLogger::FakeOop::as_mirror() {
  precond(real_klass() == vmClasses::Class_klass());
  return (FakeMirror&)*this;
}

AOTMapLogger::FakeObjArray& AOTMapLogger::FakeOop::as_obj_array() {
  precond(raw_oop()->is_objArray());
  return (FakeObjArray&)*this;
}

void AOTMapLogger::FakeOop::print_string_on(outputStream* st) {

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

class AOTMapLogger::ArchivedFieldPrinter2 : public FieldClosure {
  FakeOop _fake_oop;
  outputStream* _st;
public:
  ArchivedFieldPrinter2(FakeOop fake_oop, outputStream* st) : _fake_oop(fake_oop), _st(st) {}

  void do_field(fieldDescriptor* fd) {
    _st->print(" - ");
    BasicType ft = fd->field_type();
    switch (ft) {
    case T_ARRAY:
    case T_OBJECT:
      {
        fd->print_on(_st); // print just the name and offset
        FakeOop field_value = _fake_oop.obj_field(fd->offset());
        new_print_oop_info_cr(_st, field_value);
      }
      break;
    default:
      if (_fake_oop.is_marked_as_native_pointer(fd->offset())) {
        print_as_native_pointer(fd);
      } else {
        fd->print_on_for(_st, _fake_oop.raw_oop()); // name, offset, value
        _st->cr();
      }
    }
  }

  void print_as_native_pointer(fieldDescriptor* fd) {
#if 0
    LP64_ONLY(assert(fd->field_type() == T_LONG, "must be"));
    NOT_LP64 (assert(fd->field_type() == T_INT,  "must be"));

    // We have a field that looks like an integer, but it's actually a pointer to a MetaspaceObj.
    address source_native_ptr = (address)
        LP64_ONLY(_source_obj->long_field(fd->offset()))
        NOT_LP64( _source_obj->int_field (fd->offset()));
    ArchiveBuilder* builder = ArchiveBuilder::current();

    // The value of the native pointer at runtime.
    address requested_native_ptr = builder->to_requested(builder->get_buffered_addr(source_native_ptr));

    // The address of _source_obj at runtime
    oop requested_obj = ArchiveHeapWriter::source_obj_to_requested_obj(_source_obj);
    // The address of this field in the requested space
    assert(requested_obj != nullptr, "Attempting to load field from null oop");
    address requested_field_addr = cast_from_oop<address>(requested_obj) + fd->offset();

    fd->print_on(_st);
    _st->print_cr(PTR_FORMAT " (marked metadata pointer @" PTR_FORMAT " )",
                  p2i(requested_native_ptr), p2i(requested_field_addr));
#endif
  }
}; // ArchivedFieldPrinter2


int AOTMapLogger::FakeOop::_requested_shift;
intx AOTMapLogger::FakeOop::_buffer_to_requested_delta;
address AOTMapLogger::FakeOop::_buffer_base;
address AOTMapLogger::FakeOop::_buffer_start;
address AOTMapLogger::FakeOop::_buffer_end;

void AOTMapLogger::new_print_oop_info_cr(outputStream* st, FakeOop fake_oop, bool print_requested_addr) {
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
        fake_oop.print_string_on(st);
      } else if (real_klass == vmClasses::Class_klass()) {
        fake_oop.as_mirror().print_class_signature_on(st);
      }

      st->cr();
    }
  }
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
  }

  address requested_base = (address)mapinfo->narrow_oop_base();
  address requested_start = requested_base + r->mapping_offset();
  address buffer_base = buffer_start - r->mapping_offset();

  FakeOop::init(requested_start, mapinfo->narrow_oop_shift(), buffer_base, buffer_start, buffer_end);

  log_region("heap", buffer_start, buffer_end, requested_start);
  runtime_log_oops(buffer_start, buffer_end);
}

// Print the fields of instanceOops, or the elements of arrayOops
void AOTMapLogger::new_print_oop_details(FakeOop fake_oop, outputStream* st) {
  Klass* real_klass = fake_oop.real_klass();

  st->print(" - klass: ");
  real_klass->print_value_on(st);
  st->print(" " PTR_FORMAT, p2i(fake_oop.klass().raw_addr()));
  st->cr();

  if (real_klass->is_typeArray_klass()) {
    // TypeArrayKlass::cast(source_klass)->oop_print_elements_on(typeArrayOop(source_oop), &st);
  } else if (real_klass->is_objArray_klass()) {
    FakeObjArray fake_obj_array = fake_oop.as_obj_array();
    for (int i = 0; i < fake_obj_array.length(); i++) {
      st->print(" -%4d: ", i);
      FakeOop elm = fake_obj_array.obj_at(i);
      new_print_oop_info_cr(st, elm);
    }
  } else {
    st->print_cr(" - fields (%zu words):", fake_oop.size());

    ArchivedFieldPrinter2 print_field(fake_oop, st);
    InstanceKlass::cast(real_klass)->print_nonstatic_fields(&print_field);

    if (real_klass == vmClasses::Class_klass()) {
      FakeMirror fake_mirror = fake_oop.as_mirror();

      st->print(" - signature: ");
      fake_mirror.print_class_signature_on(st);
      st->cr();

      Klass* real_mirrored_klass = fake_mirror.real_mirrored_klass();
      if (real_mirrored_klass != nullptr && real_mirrored_klass->is_instance_klass()) {
        //oop rr = HeapShared::scratch_resolved_references(InstanceKlass::cast(src_klass)->constants());
        //st->print(" - archived_resolved_references: ");
        //print_oop_info_cr(&st, rr);

        st->print_cr("- ---- static fields (%d):", java_lang_Class::static_oop_field_count(fake_oop.raw_oop()));
        InstanceKlass::cast(real_mirrored_klass)->do_local_static_fields(&print_field);
      }
    }
  }
}

void AOTMapLogger::runtime_log_oops(address buffer_start, address buffer_end) {
  LogStreamHandle(Debug, aot, map) st;
  if (!st.is_enabled()) {
    return;
  }

  for (address fo = buffer_start; fo < buffer_end; ) {
    FakeOop fake_oop(fo);
    st.print(PTR_FORMAT ": @@ Object ", p2i(fake_oop.requested_addr()));
    new_print_oop_info_cr(&st, fake_oop);

    address next_fo = fo + fake_oop.size() * BytesPerWord;
    log_as_hex(fo, next_fo, fake_oop.requested_addr(), /*is_heap=*/true);

    LogStreamHandle(Trace, aot, map, oops) trace_st;
    if (trace_st.is_enabled()) {
      new_print_oop_details(fake_oop, &trace_st);
    }

    fo = next_fo;
  }
}

#endif // INCLUDE_CDS_JAVA_HEAP
