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

class ArchiveHeapInfo;
class DumpRegion;
class FileMapInfo;
class outputStream;

// Write detailed info to a mapfile to analyze contents of the archive.
// static dump:
//   java -Xshare:dump -Xlog:aot+map=trace:file=aot.map:none:filesize=0
// dynamic dump:
//   java -cp MyApp.jar -XX:ArchiveClassesAtExit=MyApp.jsa \
//        -Xlog:aot+map=trace:file=aot.map:none:filesize=0 MyApp
//
// We need to do some address translation because the buffers used at dump time may be mapped to
// a different location at runtime. At dump time, the buffers may be at arbitrary locations
// picked by the OS. At runtime, we try to map at a fixed location (SharedBaseAddress). For
// consistency, we log everything using runtime addresses.
class AOTMapLogger : AllStatic {
  struct ArchivedObjInfo {
    address _src_addr;
    address _buffered_addr;
    address _requested_addr;
    int _bytes;
    MetaspaceObj::Type _type;
  };

  class FakeOop;
  class FakeMirror;

  class GatherArchivedMetaspaceObjs;

  // Translate the buffers used by the RW/RO regions to their requested locations
  // at runtime.
  static intx _buffer_to_requested_delta;

  static void log_header(FileMapInfo* mapinfo);
  static void log_region(const char* name, address base, address top, address requested_base);
  static void log_metaspace_region(const char* name, DumpRegion* region,
                                   const ArchiveBuilder::SourceObjList* src_objs);
  static void log_metaspace_objects(DumpRegion* region, const ArchiveBuilder::SourceObjList* src_objs);
  static void log_metaspace_objects(address region_base, address region_end,
                                    GrowableArray<ArchivedObjInfo>* objs, int start_idx, int end_idx);

  static void log_constant_pool(ConstantPool* cp, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_constant_pool_cache(ConstantPoolCache* cpc, address requested_addr,
                                      const char* type_name, int bytes, Thread* current);
  static void log_const_method(ConstMethod* cm, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_klass(Klass* k, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_method(Method* m, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_symbol(Symbol* s, address requested_addr, const char* type_name, int bytes, Thread* current);

  static void log_as_hex(address base, address top, address requested_base, bool is_heap = false);

  static void runtime_log_metaspace_regions(FileMapInfo* mapinfo);

#if INCLUDE_CDS_JAVA_HEAP
  static void log_heap_region(ArchiveHeapInfo* heap_info);
  static void log_oops(ArchiveHeapInfo* heap_info, address start, address end);
  static void log_oop_details(ArchiveHeapInfo* heap_info, oop source_oop, address buffered_addr);
  static void print_class_signature_for_mirror(outputStream* st, oop scratch_mirror);
  static void log_heap_roots();
  static void print_oop_info_cr(outputStream* st, oop source_oop, bool print_requested_addr = true);
  static void new_print_oop_info_cr(outputStream* st, FakeOop fake_oop, bool print_requested_addr = true);

  static void runtime_log_heap_region(FileMapInfo* mapinfo);
  static void runtime_log_oops(address buf_start, address buf_end);
  class ArchivedFieldPrinter;
#endif

public:

  static void dumptime_log(ArchiveBuilder* builder, FileMapInfo* mapinfo,
                           ArchiveHeapInfo* heap_info,
                           char* bitmap, size_t bitmap_size_in_bytes);
  static void runtime_log(FileMapInfo* mapinfo);
};

#endif // SHARE_CDS_AOTMAPLOGGER_HPP
