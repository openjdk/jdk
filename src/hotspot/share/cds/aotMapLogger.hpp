/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspaceClosureType.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

class ArchiveMappedHeapInfo;
class ArchiveStreamedHeapInfo;
class CompileTrainingData;
class DumpRegion;
class FileMapInfo;
class GrowableArrayBase;
class KlassTrainingData;
class MethodCounters;
class MethodTrainingData;
class ModuleEntry;
class outputStream;
class PackageEntry;

// Write detailed info to a mapfile to analyze contents of the AOT cache/CDS archive.
// -Xlog:aot+map* can be used both when creating an AOT cache, or when using an AOT cache.
//
// Creating cache:
//     java -XX:AOTCacheOutput=app.aot -Xlog:aot+map*=trace -cp app.jar App
//
// Using cache:
//     java -XX:AOTCache=app.aot -Xlog:aot+map*=trace -cp app.jar App
//
// You can also print the map of a cache without executing the application by using the
// --version flag:
//     java -XX:AOTCache=app.aot -Xlog:aot+map*=trace --version
//
// Because the output can be large, it's best to save it to a file
//     java -XX:AOTCache=app.aot -Xlog:aot+map*=trace:file=aot.map:none:filesize=0 --version
class AOTMapLogger : AllStatic {
  struct ArchivedObjInfo {
    address _src_addr;
    address _buffered_addr;
    address _requested_addr;
    int _bytes;
    MetaspaceClosureType _type;
  };

public:
  // FakeOop and subtypes
  class FakeOop;
  class   FakeMirror;
  class   FakeObjArray;
  class   FakeString;
  class   FakeTypeArray;

#if INCLUDE_CDS_JAVA_HEAP
  struct OopData {
    address _buffered_addr;
    address _requested_addr;
    intptr_t _target_location;
    uint32_t _narrow_location;
    oopDesc* _raw_oop;
    Klass* _klass;
    size_t _size;
    bool _is_root_segment;
  };

  class OopDataIterator : public CHeapObj<mtClassShared> {
  protected:
    OopData null_data() {
      return { nullptr,
               nullptr,
               0,
               0,
               nullptr,
               nullptr,
               0,
               false };
    }

  public:
    virtual bool has_next() = 0;
    virtual OopData next() = 0;
    virtual OopData obj_at(narrowOop* p) = 0;
    virtual OopData obj_at(oop* p) = 0;
    virtual GrowableArrayCHeap<OopData, mtClass>* roots() = 0;
    virtual ~OopDataIterator() {}
  };
#endif

private:
  class RequestedMetadataAddr;
  class RuntimeGatherArchivedMetaspaceObjs;

  static bool _is_logging_at_bootstrap;
  static bool _is_runtime_logging;
  static GrowableArrayCHeap<FakeOop, mtClass>* _roots;

  static intx _buffer_to_requested_delta;
  static intx _requested_to_mapped_metadata_delta;

  static void runtime_log(FileMapInfo* mapinfo, GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs);
  static void runtime_log_metaspace_regions(FileMapInfo* mapinfo, GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs);
  static void dumptime_log_metaspace_region(const char* name, DumpRegion* region,
                                            const ArchiveBuilder::SourceObjList* src_objs);

  // Common code for dumptime/runtime
  static void log_file_header(FileMapInfo* mapinfo);
  static void log_region_range(const char* name, address base, address top, address requested_base);
  static void log_metaspace_objects_impl(address region_base, address region_end,
                                         GrowableArrayCHeap<ArchivedObjInfo, mtClass>* objs, int start_idx, int end_idx);
  static void log_as_hex(address base, address top, address requested_base, bool is_heap = false);

  // Metaspace object: type-specific logging
  static void log_constant_pool(ConstantPool* cp, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_constant_pool_cache(ConstantPoolCache* cpc, address requested_addr,
                                      const char* type_name, int bytes, Thread* current);
  static void log_const_method(ConstMethod* cm, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_method_counters(MethodCounters* mc, address requested_addr, const char* type_name, int bytes,
  Thread* current);
  static void log_method_data(MethodData* md, address requested_addr, const char* type_name, int bytes,
  Thread* current);
  static void log_klass(Klass* k, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_method(Method* m, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_module_entry(ModuleEntry* mod, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_package_entry(PackageEntry* pkg, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_growable_array(GrowableArrayBase* arr, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_symbol(Symbol* s, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_klass_training_data(KlassTrainingData* ktd, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_method_training_data(MethodTrainingData* mtd, address requested_addr, const char* type_name, int bytes, Thread* current);
  static void log_compile_training_data(CompileTrainingData* ctd, address requested_addr, const char* type_name, int bytes, Thread* current);


#if INCLUDE_CDS_JAVA_HEAP
  static void dumptime_log_mapped_heap_region(ArchiveMappedHeapInfo* mapped_heap_info);
  static void dumptime_log_streamed_heap_region(ArchiveStreamedHeapInfo* streamed_heap_info);
  static void runtime_log_heap_region(FileMapInfo* mapinfo);

  static void print_oop_info_cr(outputStream* st, FakeOop fake_oop, bool print_location = true);
  static void print_oop_details(FakeOop fake_oop, outputStream* st);
  static void log_mapped_oops(address buf_start, address buf_end);
  static void log_archived_objects(OopDataIterator* iter);
  class ArchivedFieldPrinter; // to be replaced by ArchivedFieldPrinter2
#endif

public:
  static void ergo_initialize();
  static bool is_logging_at_bootstrap() { return _is_logging_at_bootstrap; }

  static void dumptime_log(ArchiveBuilder* builder, FileMapInfo* mapinfo,
                           ArchiveMappedHeapInfo* mapped_heap_info, ArchiveStreamedHeapInfo* streamed_heap_info,
                           char* bitmap, size_t bitmap_size_in_bytes);
  static void runtime_log(FileMapInfo* static_mapinfo, FileMapInfo* dynamic_mapinfo);
};

#endif // SHARE_CDS_AOTMAPLOGGER_HPP
