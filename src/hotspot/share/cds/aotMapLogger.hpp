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

#ifndef SHARE_CDS_AOTMAPLOGGER_HPP
#define SHARE_CDS_AOTMAPLOGGER_HPP

#include "cds/archiveBuilder.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

class ArchiveHeapInfo;
class DumpRegion;
class FileMapInfo;
class outputStream;

// Write detailed info to a mapfile to analyze contents of the archive.
// static dump:
//   java -Xshare:dump -Xlog:aot+map=trace,aot+map+oops=trace:file=aot.map:none:filesize=0
// dynamic dump:
//   java -cp MyApp.jar -XX:ArchiveClassesAtExit=MyApp.jsa \
//        -Xlog:aot+map=trace:file=aot.map:none:filesize=0 MyApp
class AOTMapLogger : AllStatic {
  struct ArchivedObjInfo {
    address _src_addr;
    address _buffered_addr;
    address _requested_addr;
    int _bytes;
    MetaspaceObj::Type _type;
  };

  // FakeOop and subtypes
  class FakeOop;
  class   FakeMirror;
  class   FakeObjArray;
  class   FakeString;
  class   FakeTypeArray;

  static intx _requested_to_mapped_metadata_delta;
  static bool _is_logging_at_bootstrap;
  static bool _is_logging_mapped_aot_cache;
  static int _num_root_segments;
  static int _num_obj_array_logged;
  static GrowableArrayCHeap<FakeOop, mtClass>* _roots;
  static ArchiveHeapInfo* _dumptime_heap_info;

  class RequestedMetadataAddr;

  class GatherArchivedMetaspaceObjs;

  // Translate the buffers used by the RW/RO regions to their requested locations
  // at runtime.
  static intx _buffer_to_requested_delta;

  static void dumptime_log_metaspace_region(const char* name, DumpRegion* region,
                                            const ArchiveBuilder::SourceObjList* src_objs);
  static void runtime_log_metaspace_regions(FileMapInfo* mapinfo);

  // Common code for dumptime/runtime
  static void log_header(FileMapInfo* mapinfo);
  static void log_region(const char* name, address base, address top, address requested_base);
  static void log_metaspace_objects_impl(address region_base, address region_end,
                                         GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs, int start_idx, int end_idx);
  static void log_as_hex(address base, address top, address requested_base, bool is_heap = false);

  // Metaspace object: type-specific logging
  static void log_constant_pool(ConstantPool* cp, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_constant_pool_cache(ConstantPoolCache* cpc, address requested_addr,
                                      const char* type_name, int bytes, Thread* current);
  static void log_const_method(ConstMethod* cm, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_klass(Klass* k, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_method(Method* m, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_symbol(Symbol* s, address requested_addr, const char* type_name, int bytes, Thread* current);


#if INCLUDE_CDS_JAVA_HEAP
  static void dumptime_log_heap_region(ArchiveHeapInfo* heap_info);
  static void runtime_log_heap_region(FileMapInfo* mapinfo);

  static void print_oop_info_cr(outputStream* st, FakeOop fake_oop, bool print_requested_addr = true);
  static void print_oop_details(FakeOop fake_oop, outputStream* st);
  static void log_oops(address buf_start, address buf_end);
  class ArchivedFieldPrinter; // to be replaced by ArchivedFieldPrinter2
#endif

  static bool is_logging_mapped_aot_cache() { return _is_logging_mapped_aot_cache; }

  // Functions like ConstantPool::print_on() won't work in the assembly phase
  // - The C++ vtables for the buffered objects are not initialized
  // - Pointers such as ConstantPool::_tags are "requested addresses" that do not point
  //   to actual memory used by the current JVM.
  static bool is_logging_metadata_details() { return is_logging_mapped_aot_cache(); }
public:
  static void ergo_initialize();
  static bool is_logging_at_bootstrap() { return _is_logging_at_bootstrap; }

  static void dumptime_log(ArchiveBuilder* builder, FileMapInfo* mapinfo,
                           ArchiveHeapInfo* heap_info,
                           char* bitmap, size_t bitmap_size_in_bytes);
  static void runtime_log(FileMapInfo* mapinfo);
};

#endif // SHARE_CDS_AOTMAPLOGGER_HPP
