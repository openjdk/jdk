/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classListParser.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/sharedClassUtil.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/gcLocker.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/filemap.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceShared.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/hashtable.inline.hpp"

int MetaspaceShared::_max_alignment = 0;

ReservedSpace* MetaspaceShared::_shared_rs = NULL;

MetaspaceSharedStats MetaspaceShared::_stats;

bool MetaspaceShared::_link_classes_made_progress;
bool MetaspaceShared::_check_classes_made_progress;
bool MetaspaceShared::_has_error_classes;
bool MetaspaceShared::_archive_loading_failed = false;
SharedMiscRegion MetaspaceShared::_mc;
SharedMiscRegion MetaspaceShared::_md;

void SharedMiscRegion::initialize(ReservedSpace rs, size_t committed_byte_size,  SharedSpaceType space_type) {
  _vs.initialize(rs, committed_byte_size);
  _alloc_top = _vs.low();
  _space_type = space_type;
}

// NOT thread-safe, but this is called during dump time in single-threaded mode.
char* SharedMiscRegion::alloc(size_t num_bytes) {
  assert(DumpSharedSpaces, "dump time only");
  size_t alignment = sizeof(char*);
  num_bytes = align_size_up(num_bytes, alignment);
  _alloc_top = (char*)align_ptr_up(_alloc_top, alignment);
  if (_alloc_top + num_bytes > _vs.high()) {
    report_out_of_shared_space(_space_type);
  }

  char* p = _alloc_top;
  _alloc_top += num_bytes;

  memset(p, 0, num_bytes);
  return p;
}

void MetaspaceShared::initialize_shared_rs(ReservedSpace* rs) {
  assert(DumpSharedSpaces, "dump time only");
  _shared_rs = rs;

  // Split up and initialize the misc code and data spaces
  size_t metadata_size = SharedReadOnlySize + SharedReadWriteSize;
  ReservedSpace shared_ro_rw = _shared_rs->first_part(metadata_size);
  ReservedSpace misc_section = _shared_rs->last_part(metadata_size);

  // Now split into misc sections.
  ReservedSpace md_rs   = misc_section.first_part(SharedMiscDataSize);
  ReservedSpace mc_rs   = misc_section.last_part(SharedMiscDataSize);
  _md.initialize(md_rs, SharedMiscDataSize, SharedMiscData);
  _mc.initialize(mc_rs, SharedMiscCodeSize, SharedMiscData);
}

// Read/write a data stream for restoring/preserving metadata pointers and
// miscellaneous data from/to the shared archive file.

void MetaspaceShared::serialize(SerializeClosure* soc) {
  int tag = 0;
  soc->do_tag(--tag);

  // Verify the sizes of various metadata in the system.
  soc->do_tag(sizeof(Method));
  soc->do_tag(sizeof(ConstMethod));
  soc->do_tag(arrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(ConstantPool));
  soc->do_tag(sizeof(ConstantPoolCache));
  soc->do_tag(objArrayOopDesc::base_offset_in_bytes());
  soc->do_tag(typeArrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(Symbol));

  // Dump/restore miscellaneous metadata.
  Universe::serialize(soc, true);
  soc->do_tag(--tag);

  // Dump/restore references to commonly used names and signatures.
  vmSymbols::serialize(soc);
  soc->do_tag(--tag);

  soc->do_tag(666);
}


// CDS code for dumping shared archive.

// Global object for holding classes that have been loaded.  Since this
// is run at a safepoint just before exit, this is the entire set of classes.
static GrowableArray<Klass*>* _global_klass_objects;
static void collect_classes(Klass* k) {
  _global_klass_objects->append_if_missing(k);
  if (k->is_instance_klass()) {
    // Add in the array classes too
    InstanceKlass* ik = InstanceKlass::cast(k);
    ik->array_klasses_do(collect_classes);
  }
}

static void collect_classes2(Klass* k, ClassLoaderData* class_data) {
  collect_classes(k);
}

static void remove_unshareable_in_classes() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    k->remove_unshareable_info();
  }
}

static void rewrite_nofast_bytecode(Method* method) {
  RawBytecodeStream bcs(method);
  while (!bcs.is_last_bytecode()) {
    Bytecodes::Code opcode = bcs.raw_next();
    switch (opcode) {
    case Bytecodes::_getfield:      *bcs.bcp() = Bytecodes::_nofast_getfield;      break;
    case Bytecodes::_putfield:      *bcs.bcp() = Bytecodes::_nofast_putfield;      break;
    case Bytecodes::_aload_0:       *bcs.bcp() = Bytecodes::_nofast_aload_0;       break;
    case Bytecodes::_iload:         *bcs.bcp() = Bytecodes::_nofast_iload;         break;
    default: break;
    }
  }
}

// Walk all methods in the class list to ensure that they won't be modified at
// run time. This includes:
// [1] Rewrite all bytecodes as needed, so that the ConstMethod* will not be modified
//     at run time by RewriteBytecodes/RewriteFrequentPairs
// [2] Assign a fingerprint, so one doesn't need to be assigned at run-time.
static void rewrite_nofast_bytecodes_and_calculate_fingerprints() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      for (int i = 0; i < ik->methods()->length(); i++) {
        Method* m = ik->methods()->at(i);
        rewrite_nofast_bytecode(m);
        Fingerprinter fp(m);
        // The side effect of this call sets method's fingerprint field.
        fp.fingerprint();
      }
    }
  }
}

// Patch C++ vtable pointer in metadata.

// Klass and other metadata objects contain references to c++ vtables in the
// JVM library.
// Fix them to point to our constructed vtables.  However, don't iterate
// across the space while doing this, as that causes the vtables to be
// patched, undoing our useful work.  Instead, iterate to make a list,
// then use the list to do the fixing.
//
// Our constructed vtables:
// Dump time:
//  1. init_self_patching_vtbl_list: table of pointers to current virtual method addrs
//  2. generate_vtable_methods: create jump table, appended to above vtbl_list
//  3. patch_klass_vtables: for Klass list, patch the vtable entry in klass and
//     associated metadata to point to jump table rather than to current vtbl
// Table layout: NOTE FIXED SIZE
//   1. vtbl pointers
//   2. #Klass X #virtual methods per Klass
//   1 entry for each, in the order:
//   Klass1:method1 entry, Klass1:method2 entry, ... Klass1:method<num_virtuals> entry
//   Klass2:method1 entry, Klass2:method2 entry, ... Klass2:method<num_virtuals> entry
//   ...
//   Klass<vtbl_list_size>:method1 entry, Klass<vtbl_list_size>:method2 entry,
//       ... Klass<vtbl_list_size>:method<num_virtuals> entry
//  Sample entry: (Sparc):
//   save(sp, -256, sp)
//   ba,pt common_code
//   mov XXX, %L0       %L0 gets: Klass index <<8 + method index (note: max method index 255)
//
// Restore time:
//   1. initialize_shared_space: reserve space for table
//   2. init_self_patching_vtbl_list: update pointers to NEW virtual method addrs in text
//
// Execution time:
//   First virtual method call for any object of these metadata types:
//   1. object->klass
//   2. vtable entry for that klass points to the jump table entries
//   3. branches to common_code with %O0/klass, %L0: Klass index <<8 + method index
//   4. common_code:
//      Get address of new vtbl pointer for this Klass from updated table
//      Update new vtbl pointer in the Klass: future virtual calls go direct
//      Jump to method, using new vtbl pointer and method index


static void* find_matching_vtbl_ptr(void** vtbl_list, void* new_vtable_start, void* obj) {
  void* old_vtbl_ptr = *(void**)obj;
  for (int i = 0; i < MetaspaceShared::vtbl_list_size; i++) {
    if (vtbl_list[i] == old_vtbl_ptr) {
      return (void**)new_vtable_start + i * MetaspaceShared::num_virtuals;
    }
  }
  ShouldNotReachHere();
  return NULL;
}

// Assumes the vtable is in first slot in object.
static void patch_klass_vtables(void** vtbl_list, void* new_vtable_start) {
  int n = _global_klass_objects->length();
  for (int i = 0; i < n; i++) {
    Klass* obj = _global_klass_objects->at(i);
    // Note is_instance_klass() is a virtual call in debug.  After patching vtables
    // all virtual calls on the dummy vtables will restore the original!
    if (obj->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(obj);
      *(void**)ik = find_matching_vtbl_ptr(vtbl_list, new_vtable_start, ik);
      ConstantPool* cp = ik->constants();
      *(void**)cp = find_matching_vtbl_ptr(vtbl_list, new_vtable_start, cp);
      for (int j = 0; j < ik->methods()->length(); j++) {
        Method* m = ik->methods()->at(j);
        *(void**)m = find_matching_vtbl_ptr(vtbl_list, new_vtable_start, m);
      }
    } else {
      // Array klasses
      Klass* k = obj;
      *(void**)k = find_matching_vtbl_ptr(vtbl_list, new_vtable_start, k);
    }
  }
}

// Closure for serializing initialization data out to a data area to be
// written to the shared file.

class WriteClosure : public SerializeClosure {
private:
  intptr_t* top;
  char* end;

  inline void check_space() {
    if ((char*)top + sizeof(intptr_t) > end) {
      report_out_of_shared_space(SharedMiscData);
    }
  }

public:
  WriteClosure(char* md_top, char* md_end) {
    top = (intptr_t*)md_top;
    end = md_end;
  }

  char* get_top() { return (char*)top; }

  void do_ptr(void** p) {
    check_space();
    *top = (intptr_t)*p;
    ++top;
  }

  void do_tag(int tag) {
    check_space();
    *top = (intptr_t)tag;
    ++top;
  }

  void do_region(u_char* start, size_t size) {
    if ((char*)top + size > end) {
      report_out_of_shared_space(SharedMiscData);
    }
    assert((intptr_t)start % sizeof(intptr_t) == 0, "bad alignment");
    assert(size % sizeof(intptr_t) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      *top = *(intptr_t*)start;
      ++top;
      start += sizeof(intptr_t);
      size -= sizeof(intptr_t);
    }
  }

  bool reading() const { return false; }
};

// This is for dumping detailed statistics for the allocations
// in the shared spaces.
class DumpAllocClosure : public Metaspace::AllocRecordClosure {
public:

  // Here's poor man's enum inheritance
#define SHAREDSPACE_OBJ_TYPES_DO(f) \
  METASPACE_OBJ_TYPES_DO(f) \
  f(SymbolHashentry) \
  f(SymbolBucket) \
  f(Other)

#define SHAREDSPACE_OBJ_TYPE_DECLARE(name) name ## Type,
#define SHAREDSPACE_OBJ_TYPE_NAME_CASE(name) case name ## Type: return #name;

  enum Type {
    // Types are MetaspaceObj::ClassType, MetaspaceObj::SymbolType, etc
    SHAREDSPACE_OBJ_TYPES_DO(SHAREDSPACE_OBJ_TYPE_DECLARE)
    _number_of_types
  };

  static const char * type_name(Type type) {
    switch(type) {
    SHAREDSPACE_OBJ_TYPES_DO(SHAREDSPACE_OBJ_TYPE_NAME_CASE)
    default:
      ShouldNotReachHere();
      return NULL;
    }
  }

public:
  enum {
    RO = 0,
    RW = 1
  };

  int _counts[2][_number_of_types];
  int _bytes [2][_number_of_types];
  int _which;

  DumpAllocClosure() {
    memset(_counts, 0, sizeof(_counts));
    memset(_bytes,  0, sizeof(_bytes));
  };

  void iterate_metaspace(Metaspace* space, int which) {
    assert(which == RO || which == RW, "sanity");
    _which = which;
    space->iterate(this);
  }

  virtual void doit(address ptr, MetaspaceObj::Type type, int byte_size) {
    assert(int(type) >= 0 && type < MetaspaceObj::_number_of_types, "sanity");
    _counts[_which][type] ++;
    _bytes [_which][type] += byte_size;
  }

  void dump_stats(int ro_all, int rw_all, int md_all, int mc_all);
};

void DumpAllocClosure::dump_stats(int ro_all, int rw_all, int md_all, int mc_all) {
  rw_all += (md_all + mc_all); // md and mc are all mapped Read/Write
  int other_bytes = md_all + mc_all;

  // Calculate size of data that was not allocated by Metaspace::allocate()
  MetaspaceSharedStats *stats = MetaspaceShared::stats();

  // symbols
  _counts[RW][SymbolHashentryType] = stats->symbol.hashentry_count;
  _bytes [RW][SymbolHashentryType] = stats->symbol.hashentry_bytes;
  other_bytes -= stats->symbol.hashentry_bytes;

  _counts[RW][SymbolBucketType] = stats->symbol.bucket_count;
  _bytes [RW][SymbolBucketType] = stats->symbol.bucket_bytes;
  other_bytes -= stats->symbol.bucket_bytes;

  // TODO: count things like dictionary, vtable, etc
  _bytes[RW][OtherType] =  other_bytes;

  // prevent divide-by-zero
  if (ro_all < 1) {
    ro_all = 1;
  }
  if (rw_all < 1) {
    rw_all = 1;
  }

  int all_ro_count = 0;
  int all_ro_bytes = 0;
  int all_rw_count = 0;
  int all_rw_bytes = 0;

// To make fmt_stats be a syntactic constant (for format warnings), use #define.
#define fmt_stats "%-20s: %8d %10d %5.1f | %8d %10d %5.1f | %8d %10d %5.1f"
  const char *sep = "--------------------+---------------------------+---------------------------+--------------------------";
  const char *hdr = "                        ro_cnt   ro_bytes     % |   rw_cnt   rw_bytes     % |  all_cnt  all_bytes     %";

  tty->print_cr("Detailed metadata info (rw includes md and mc):");
  tty->print_cr("%s", hdr);
  tty->print_cr("%s", sep);
  for (int type = 0; type < int(_number_of_types); type ++) {
    const char *name = type_name((Type)type);
    int ro_count = _counts[RO][type];
    int ro_bytes = _bytes [RO][type];
    int rw_count = _counts[RW][type];
    int rw_bytes = _bytes [RW][type];
    int count = ro_count + rw_count;
    int bytes = ro_bytes + rw_bytes;

    double ro_perc = 100.0 * double(ro_bytes) / double(ro_all);
    double rw_perc = 100.0 * double(rw_bytes) / double(rw_all);
    double perc    = 100.0 * double(bytes)    / double(ro_all + rw_all);

    tty->print_cr(fmt_stats, name,
                  ro_count, ro_bytes, ro_perc,
                  rw_count, rw_bytes, rw_perc,
                  count, bytes, perc);

    all_ro_count += ro_count;
    all_ro_bytes += ro_bytes;
    all_rw_count += rw_count;
    all_rw_bytes += rw_bytes;
  }

  int all_count = all_ro_count + all_rw_count;
  int all_bytes = all_ro_bytes + all_rw_bytes;

  double all_ro_perc = 100.0 * double(all_ro_bytes) / double(ro_all);
  double all_rw_perc = 100.0 * double(all_rw_bytes) / double(rw_all);
  double all_perc    = 100.0 * double(all_bytes)    / double(ro_all + rw_all);

  tty->print_cr("%s", sep);
  tty->print_cr(fmt_stats, "Total",
                all_ro_count, all_ro_bytes, all_ro_perc,
                all_rw_count, all_rw_bytes, all_rw_perc,
                all_count, all_bytes, all_perc);

  assert(all_ro_bytes == ro_all, "everything should have been counted");
  assert(all_rw_bytes == rw_all, "everything should have been counted");
#undef fmt_stats
}

// Populate the shared space.

class VM_PopulateDumpSharedSpace: public VM_Operation {
private:
  ClassLoaderData* _loader_data;
  GrowableArray<Klass*> *_class_promote_order;
  VirtualSpace _md_vs;
  VirtualSpace _mc_vs;
  CompactHashtableWriter* _string_cht;
  GrowableArray<MemRegion> *_string_regions;

public:
  VM_PopulateDumpSharedSpace(ClassLoaderData* loader_data,
                             GrowableArray<Klass*> *class_promote_order) :
    _loader_data(loader_data) {
    _class_promote_order = class_promote_order;
  }

  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit();   // outline because gdb sucks

private:
  void handle_misc_data_space_failure(bool success) {
    if (!success) {
      report_out_of_shared_space(SharedMiscData);
    }
  }
}; // class VM_PopulateDumpSharedSpace

void VM_PopulateDumpSharedSpace::doit() {
  Thread* THREAD = VMThread::vm_thread();
  NOT_PRODUCT(SystemDictionary::verify();)
  // The following guarantee is meant to ensure that no loader constraints
  // exist yet, since the constraints table is not shared.  This becomes
  // more important now that we don't re-initialize vtables/itables for
  // shared classes at runtime, where constraints were previously created.
  guarantee(SystemDictionary::constraints()->number_of_entries() == 0,
            "loader constraints are not saved");
  guarantee(SystemDictionary::placeholders()->number_of_entries() == 0,
          "placeholders are not saved");
  // Revisit and implement this if we prelink method handle call sites:
  guarantee(SystemDictionary::invoke_method_table() == NULL ||
            SystemDictionary::invoke_method_table()->number_of_entries() == 0,
            "invoke method table is not saved");

  // At this point, many classes have been loaded.
  // Gather systemDictionary classes in a global array and do everything to
  // that so we don't have to walk the SystemDictionary again.
  _global_klass_objects = new GrowableArray<Klass*>(1000);
  Universe::basic_type_classes_do(collect_classes);

  // Need to call SystemDictionary::classes_do(void f(Klass*, ClassLoaderData*))
  // as we may have some classes with NULL ClassLoaderData* in the dictionary. Other
  // variants of SystemDictionary::classes_do will skip those classes.
  SystemDictionary::classes_do(collect_classes2);

  tty->print_cr("Number of classes %d", _global_klass_objects->length());
  {
    int num_type_array = 0, num_obj_array = 0, num_inst = 0;
    for (int i = 0; i < _global_klass_objects->length(); i++) {
      Klass* k = _global_klass_objects->at(i);
      if (k->is_instance_klass()) {
        num_inst ++;
      } else if (k->is_objArray_klass()) {
        num_obj_array ++;
      } else {
        assert(k->is_typeArray_klass(), "sanity");
        num_type_array ++;
      }
    }
    tty->print_cr("    instance classes   = %5d", num_inst);
    tty->print_cr("    obj array classes  = %5d", num_obj_array);
    tty->print_cr("    type array classes = %5d", num_type_array);
  }


  // Ensure the ConstMethods won't be modified at run-time
  tty->print("Updating ConstMethods ... ");
  rewrite_nofast_bytecodes_and_calculate_fingerprints();
  tty->print_cr("done. ");

  // Remove all references outside the metadata
  tty->print("Removing unshareable information ... ");
  remove_unshareable_in_classes();
  tty->print_cr("done. ");

  // Set up the share data and shared code segments.
  _md_vs = *MetaspaceShared::misc_data_region()->virtual_space();
  _mc_vs = *MetaspaceShared::misc_code_region()->virtual_space();
  char* md_low = _md_vs.low();
  char* md_top = MetaspaceShared::misc_data_region()->alloc_top();
  char* md_end = _md_vs.high();
  char* mc_low = _mc_vs.low();
  char* mc_top = MetaspaceShared::misc_code_region()->alloc_top();
  char* mc_end = _mc_vs.high();

  // Reserve space for the list of Klass*s whose vtables are used
  // for patching others as needed.

  void** vtbl_list = (void**)md_top;
  int vtbl_list_size = MetaspaceShared::vtbl_list_size;
  Universe::init_self_patching_vtbl_list(vtbl_list, vtbl_list_size);

  md_top += vtbl_list_size * sizeof(void*);
  void* vtable = md_top;

  // Reserve space for a new dummy vtable for klass objects in the
  // heap.  Generate self-patching vtable entries.

  MetaspaceShared::generate_vtable_methods(vtbl_list, &vtable,
                                     &md_top, md_end,
                                     &mc_top, mc_end);

  // Reorder the system dictionary.  (Moving the symbols affects
  // how the hash table indices are calculated.)
  // Not doing this either.

  SystemDictionary::reorder_dictionary();

  NOT_PRODUCT(SystemDictionary::verify();)

  // Copy the symbol table, string table, and the system dictionary to the shared
  // space in usable form.  Copy the hashtable
  // buckets first [read-write], then copy the linked lists of entries
  // [read-only].

  NOT_PRODUCT(SymbolTable::verify());
  handle_misc_data_space_failure(SymbolTable::copy_compact_table(&md_top, md_end));

  size_t ss_bytes = 0;
  char* ss_low;
  // The string space has maximum two regions. See FileMapInfo::write_string_regions() for details.
  _string_regions = new GrowableArray<MemRegion>(2);
  NOT_PRODUCT(StringTable::verify());
  handle_misc_data_space_failure(StringTable::copy_compact_table(&md_top, md_end, _string_regions,
                                                                 &ss_bytes));
  ss_low = _string_regions->is_empty() ? NULL : (char*)_string_regions->first().start();

  SystemDictionary::reverse();
  SystemDictionary::copy_buckets(&md_top, md_end);

  ClassLoader::verify();
  ClassLoader::copy_package_info_buckets(&md_top, md_end);
  ClassLoader::verify();

  SystemDictionary::copy_table(&md_top, md_end);
  ClassLoader::verify();
  ClassLoader::copy_package_info_table(&md_top, md_end);
  ClassLoader::verify();

  // Write the other data to the output array.
  WriteClosure wc(md_top, md_end);
  MetaspaceShared::serialize(&wc);
  md_top = wc.get_top();

  // Print shared spaces all the time
// To make fmt_space be a syntactic constant (for format warnings), use #define.
#define fmt_space "%s space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [%4.1f%% used] at " INTPTR_FORMAT
  Metaspace* ro_space = _loader_data->ro_metaspace();
  Metaspace* rw_space = _loader_data->rw_metaspace();

  // Allocated size of each space (may not be all occupied)
  const size_t ro_alloced = ro_space->capacity_bytes_slow(Metaspace::NonClassType);
  const size_t rw_alloced = rw_space->capacity_bytes_slow(Metaspace::NonClassType);
  const size_t md_alloced = md_end-md_low;
  const size_t mc_alloced = mc_end-mc_low;
  const size_t total_alloced = ro_alloced + rw_alloced + md_alloced + mc_alloced
                             + ss_bytes;

  // Occupied size of each space.
  const size_t ro_bytes = ro_space->used_bytes_slow(Metaspace::NonClassType);
  const size_t rw_bytes = rw_space->used_bytes_slow(Metaspace::NonClassType);
  const size_t md_bytes = size_t(md_top - md_low);
  const size_t mc_bytes = size_t(mc_top - mc_low);

  // Percent of total size
  const size_t total_bytes = ro_bytes + rw_bytes + md_bytes + mc_bytes + ss_bytes;
  const double ro_t_perc = ro_bytes / double(total_bytes) * 100.0;
  const double rw_t_perc = rw_bytes / double(total_bytes) * 100.0;
  const double md_t_perc = md_bytes / double(total_bytes) * 100.0;
  const double mc_t_perc = mc_bytes / double(total_bytes) * 100.0;
  const double ss_t_perc = ss_bytes / double(total_bytes) * 100.0;

  // Percent of fullness of each space
  const double ro_u_perc = ro_bytes / double(ro_alloced) * 100.0;
  const double rw_u_perc = rw_bytes / double(rw_alloced) * 100.0;
  const double md_u_perc = md_bytes / double(md_alloced) * 100.0;
  const double mc_u_perc = mc_bytes / double(mc_alloced) * 100.0;
  const double total_u_perc = total_bytes / double(total_alloced) * 100.0;

  tty->print_cr(fmt_space, "ro", ro_bytes, ro_t_perc, ro_alloced, ro_u_perc, p2i(ro_space->bottom()));
  tty->print_cr(fmt_space, "rw", rw_bytes, rw_t_perc, rw_alloced, rw_u_perc, p2i(rw_space->bottom()));
  tty->print_cr(fmt_space, "md", md_bytes, md_t_perc, md_alloced, md_u_perc, p2i(md_low));
  tty->print_cr(fmt_space, "mc", mc_bytes, mc_t_perc, mc_alloced, mc_u_perc, p2i(mc_low));
  tty->print_cr(fmt_space, "st", ss_bytes, ss_t_perc, ss_bytes,   100.0,     p2i(ss_low));
  tty->print_cr("total   : " SIZE_FORMAT_W(9) " [100.0%% of total] out of " SIZE_FORMAT_W(9) " bytes [%4.1f%% used]",
                 total_bytes, total_alloced, total_u_perc);

  // Update the vtable pointers in all of the Klass objects in the
  // heap. They should point to newly generated vtable.
  patch_klass_vtables(vtbl_list, vtable);

  // dunno what this is for.
  char* saved_vtbl = (char*)os::malloc(vtbl_list_size * sizeof(void*), mtClass);
  memmove(saved_vtbl, vtbl_list, vtbl_list_size * sizeof(void*));
  memset(vtbl_list, 0, vtbl_list_size * sizeof(void*));

  // Create and write the archive file that maps the shared spaces.

  FileMapInfo* mapinfo = new FileMapInfo();
  mapinfo->populate_header(MetaspaceShared::max_alignment());
  mapinfo->set_misc_data_patching_start((char*)vtbl_list);

  for (int pass=1; pass<=2; pass++) {
    if (pass == 1) {
      // The first pass doesn't actually write the data to disk. All it
      // does is to update the fields in the mapinfo->_header.
    } else {
      // After the first pass, the contents of mapinfo->_header are finalized,
      // so we can compute the header's CRC, and write the contents of the header
      // and the regions into disk.
      mapinfo->open_for_write();
      mapinfo->set_header_crc(mapinfo->compute_header_crc());
    }
    mapinfo->write_header();
    mapinfo->write_space(MetaspaceShared::ro, _loader_data->ro_metaspace(), true);
    mapinfo->write_space(MetaspaceShared::rw, _loader_data->rw_metaspace(), false);
    mapinfo->write_region(MetaspaceShared::md, _md_vs.low(),
                          pointer_delta(md_top, _md_vs.low(), sizeof(char)),
                          SharedMiscDataSize,
                          false, false);
    mapinfo->write_region(MetaspaceShared::mc, _mc_vs.low(),
                          pointer_delta(mc_top, _mc_vs.low(), sizeof(char)),
                          SharedMiscCodeSize,
                          true, true);
    mapinfo->write_string_regions(_string_regions);
  }

  mapinfo->close();

  memmove(vtbl_list, saved_vtbl, vtbl_list_size * sizeof(void*));
  os::free(saved_vtbl);

  if (PrintSharedSpaces) {
    DumpAllocClosure dac;
    dac.iterate_metaspace(_loader_data->ro_metaspace(), DumpAllocClosure::RO);
    dac.iterate_metaspace(_loader_data->rw_metaspace(), DumpAllocClosure::RW);

    dac.dump_stats(int(ro_bytes), int(rw_bytes), int(md_bytes), int(mc_bytes));
  }
#undef fmt_space
}


void MetaspaceShared::link_one_shared_class(Klass* obj, TRAPS) {
  Klass* k = obj;
  if (k->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(k);
    // Link the class to cause the bytecodes to be rewritten and the
    // cpcache to be created. Class verification is done according
    // to -Xverify setting.
    _link_classes_made_progress |= try_link_class(ik, THREAD);
    guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");
  }
}

void MetaspaceShared::check_one_shared_class(Klass* k) {
  if (k->is_instance_klass() && InstanceKlass::cast(k)->check_sharing_error_state()) {
    _check_classes_made_progress = true;
  }
}

void MetaspaceShared::link_and_cleanup_shared_classes(TRAPS) {
  // We need to iterate because verification may cause additional classes
  // to be loaded.
  do {
    _link_classes_made_progress = false;
    SystemDictionary::classes_do(link_one_shared_class, THREAD);
    guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");
  } while (_link_classes_made_progress);

  if (_has_error_classes) {
    // Mark all classes whose super class or interfaces failed verification.
    do {
      // Not completely sure if we need to do this iteratively. Anyway,
      // we should come here only if there are unverifiable classes, which
      // shouldn't happen in normal cases. So better safe than sorry.
      _check_classes_made_progress = false;
      SystemDictionary::classes_do(check_one_shared_class);
    } while (_check_classes_made_progress);

    if (IgnoreUnverifiableClassesDuringDump) {
      // This is useful when running JCK or SQE tests. You should not
      // enable this when running real apps.
      SystemDictionary::remove_classes_in_error_state();
    } else {
      tty->print_cr("Please remove the unverifiable classes from your class list and try again");
      exit(1);
    }
  }
}

void MetaspaceShared::prepare_for_dumping() {
  ClassLoader::initialize_shared_path();
  FileMapInfo::allocate_classpath_entry_table();
}

// Preload classes from a list, populate the shared spaces and dump to a
// file.
void MetaspaceShared::preload_and_dump(TRAPS) {
  TraceTime timer("Dump Shared Spaces", TraceStartupTime);
  ResourceMark rm;
  char class_list_path_str[JVM_MAXPATHLEN];

  tty->print_cr("Allocated shared space: " SIZE_FORMAT " bytes at " PTR_FORMAT,
                MetaspaceShared::shared_rs()->size(),
                p2i(MetaspaceShared::shared_rs()->base()));

  // Preload classes to be shared.
  // Should use some os:: method rather than fopen() here. aB.
  const char* class_list_path;
  if (SharedClassListFile == NULL) {
    // Construct the path to the class list (in jre/lib)
    // Walk up two directories from the location of the VM and
    // optionally tack on "lib" (depending on platform)
    os::jvm_path(class_list_path_str, sizeof(class_list_path_str));
    for (int i = 0; i < 3; i++) {
      char *end = strrchr(class_list_path_str, *os::file_separator());
      if (end != NULL) *end = '\0';
    }
    int class_list_path_len = (int)strlen(class_list_path_str);
    if (class_list_path_len >= 3) {
      if (strcmp(class_list_path_str + class_list_path_len - 3, "lib") != 0) {
        if (class_list_path_len < JVM_MAXPATHLEN - 4) {
          jio_snprintf(class_list_path_str + class_list_path_len,
                       sizeof(class_list_path_str) - class_list_path_len,
                       "%slib", os::file_separator());
          class_list_path_len += 4;
        }
      }
    }
    if (class_list_path_len < JVM_MAXPATHLEN - 10) {
      jio_snprintf(class_list_path_str + class_list_path_len,
                   sizeof(class_list_path_str) - class_list_path_len,
                   "%sclasslist", os::file_separator());
    }
    class_list_path = class_list_path_str;
  } else {
    class_list_path = SharedClassListFile;
  }

  int class_count = 0;
  GrowableArray<Klass*>* class_promote_order = new GrowableArray<Klass*>();

  // sun.io.Converters
  static const char obj_array_sig[] = "[[Ljava/lang/Object;";
  SymbolTable::new_permanent_symbol(obj_array_sig, THREAD);

  // java.util.HashMap
  static const char map_entry_array_sig[] = "[Ljava/util/Map$Entry;";
  SymbolTable::new_permanent_symbol(map_entry_array_sig, THREAD);

  // Need to allocate the op here:
  // op.misc_data_space_alloc() will be called during preload_and_dump().
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  VM_PopulateDumpSharedSpace op(loader_data, class_promote_order);

  tty->print_cr("Loading classes to share ...");
  _has_error_classes = false;
  class_count += preload_and_dump(class_list_path, class_promote_order,
                                  THREAD);
  if (ExtraSharedClassListFile) {
    class_count += preload_and_dump(ExtraSharedClassListFile, class_promote_order,
                                    THREAD);
  }
  tty->print_cr("Loading classes to share: done.");

  if (PrintSharedSpaces) {
    tty->print_cr("Shared spaces: preloaded %d classes", class_count);
  }

  // Rewrite and link classes
  tty->print_cr("Rewriting and linking classes ...");

  // Link any classes which got missed. This would happen if we have loaded classes that
  // were not explicitly specified in the classlist. E.g., if an interface implemented by class K
  // fails verification, all other interfaces that were not specified in the classlist but
  // are implemented by K are not verified.
  link_and_cleanup_shared_classes(CATCH);
  tty->print_cr("Rewriting and linking classes: done");

  VMThread::execute(&op);
  // Since various initialization steps have been undone by this process,
  // it is not reasonable to continue running a java process.
  exit(0);
}


int MetaspaceShared::preload_and_dump(const char* class_list_path,
                                      GrowableArray<Klass*>* class_promote_order,
                                      TRAPS) {
  ClassListParser parser(class_list_path);
  int class_count = 0;

    while (parser.parse_one_line()) {
      Klass* klass = ClassLoaderExt::load_one_class(&parser, THREAD);

      CLEAR_PENDING_EXCEPTION;
      if (klass != NULL) {
        if (PrintSharedSpaces && Verbose && WizardMode) {
          ResourceMark rm;
          tty->print_cr("Shared spaces preloaded: %s", klass->external_name());
        }

        InstanceKlass* ik = InstanceKlass::cast(klass);

        // Should be class load order as per -XX:+TraceClassLoadingPreorder
        class_promote_order->append(ik);

        // Link the class to cause the bytecodes to be rewritten and the
        // cpcache to be created. The linking is done as soon as classes
        // are loaded in order that the related data structures (klass and
        // cpCache) are located together.
        try_link_class(ik, THREAD);
        guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");

        class_count++;
      }
    }

  return class_count;
}

// Returns true if the class's status has changed
bool MetaspaceShared::try_link_class(InstanceKlass* ik, TRAPS) {
  assert(DumpSharedSpaces, "should only be called during dumping");
  if (ik->init_state() < InstanceKlass::linked) {
    bool saved = BytecodeVerificationLocal;
    if (!SharedClassUtil::is_shared_boot_class(ik)) {
      // The verification decision is based on BytecodeVerificationRemote
      // for non-system classes. Since we are using the NULL classloader
      // to load non-system classes during dumping, we need to temporarily
      // change BytecodeVerificationLocal to be the same as
      // BytecodeVerificationRemote. Note this can cause the parent system
      // classes also being verified. The extra overhead is acceptable during
      // dumping.
      BytecodeVerificationLocal = BytecodeVerificationRemote;
    }
    ik->link_class(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      ResourceMark rm;
      tty->print_cr("Preload Warning: Verification failed for %s",
                    ik->external_name());
      CLEAR_PENDING_EXCEPTION;
      ik->set_in_error_state();
      _has_error_classes = true;
    }
    BytecodeVerificationLocal = saved;
    return true;
  } else {
    return false;
  }
}

// Closure for serializing initialization data in from a data area
// (ptr_array) read from the shared file.

class ReadClosure : public SerializeClosure {
private:
  intptr_t** _ptr_array;

  inline intptr_t nextPtr() {
    return *(*_ptr_array)++;
  }

public:
  ReadClosure(intptr_t** ptr_array) { _ptr_array = ptr_array; }

  void do_ptr(void** p) {
    assert(*p == NULL, "initializing previous initialized pointer.");
    intptr_t obj = nextPtr();
    assert((intptr_t)obj >= 0 || (intptr_t)obj < -100,
           "hit tag while initializing ptrs.");
    *p = (void*)obj;
  }

  void do_tag(int tag) {
    int old_tag;
    old_tag = (int)(intptr_t)nextPtr();
    // do_int(&old_tag);
    assert(tag == old_tag, "old tag doesn't match");
    FileMapInfo::assert_mark(tag == old_tag);
  }

  void do_region(u_char* start, size_t size) {
    assert((intptr_t)start % sizeof(intptr_t) == 0, "bad alignment");
    assert(size % sizeof(intptr_t) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      *(intptr_t*)start = nextPtr();
      start += sizeof(intptr_t);
      size -= sizeof(intptr_t);
    }
  }

  bool reading() const { return true; }
};

// Return true if given address is in the mapped shared space.
bool MetaspaceShared::is_in_shared_space(const void* p) {
  return UseSharedSpaces && FileMapInfo::current_info()->is_in_shared_space(p);
}

bool MetaspaceShared::is_string_region(int idx) {
  return (idx >= MetaspaceShared::first_string &&
          idx < MetaspaceShared::first_string + MetaspaceShared::max_strings);
}

void MetaspaceShared::print_shared_spaces() {
  if (UseSharedSpaces) {
    FileMapInfo::current_info()->print_shared_spaces();
  }
}


// Map shared spaces at requested addresses and return if succeeded.
// Need to keep the bounds of the ro and rw space for the Metaspace::contains
// call, or is_in_shared_space.
bool MetaspaceShared::map_shared_spaces(FileMapInfo* mapinfo) {
  size_t image_alignment = mapinfo->alignment();

#ifndef _WINDOWS
  // Map in the shared memory and then map the regions on top of it.
  // On Windows, don't map the memory here because it will cause the
  // mappings of the regions to fail.
  ReservedSpace shared_rs = mapinfo->reserve_shared_memory();
  if (!shared_rs.is_reserved()) return false;
#endif

  assert(!DumpSharedSpaces, "Should not be called with DumpSharedSpaces");

  char* _ro_base = NULL;
  char* _rw_base = NULL;
  char* _md_base = NULL;
  char* _mc_base = NULL;

  // Map each shared region
  if ((_ro_base = mapinfo->map_region(ro)) != NULL &&
      mapinfo->verify_region_checksum(ro) &&
      (_rw_base = mapinfo->map_region(rw)) != NULL &&
      mapinfo->verify_region_checksum(rw) &&
      (_md_base = mapinfo->map_region(md)) != NULL &&
      mapinfo->verify_region_checksum(md) &&
      (_mc_base = mapinfo->map_region(mc)) != NULL &&
      mapinfo->verify_region_checksum(mc) &&
      (image_alignment == (size_t)max_alignment()) &&
      mapinfo->validate_classpath_entry_table()) {
    // Success (no need to do anything)
    return true;
  } else {
    // If there was a failure in mapping any of the spaces, unmap the ones
    // that succeeded
    if (_ro_base != NULL) mapinfo->unmap_region(ro);
    if (_rw_base != NULL) mapinfo->unmap_region(rw);
    if (_md_base != NULL) mapinfo->unmap_region(md);
    if (_mc_base != NULL) mapinfo->unmap_region(mc);
#ifndef _WINDOWS
    // Release the entire mapped region
    shared_rs.release();
#endif
    // If -Xshare:on is specified, print out the error message and exit VM,
    // otherwise, set UseSharedSpaces to false and continue.
    if (RequireSharedSpaces || PrintSharedArchiveAndExit) {
      vm_exit_during_initialization("Unable to use shared archive.", "Failed map_region for using -Xshare:on.");
    } else {
      FLAG_SET_DEFAULT(UseSharedSpaces, false);
    }
    return false;
  }
}

// Read the miscellaneous data from the shared file, and
// serialize it out to its various destinations.

void MetaspaceShared::initialize_shared_spaces() {
  FileMapInfo *mapinfo = FileMapInfo::current_info();
  char* buffer = mapinfo->misc_data_patching_start();

  // Skip over (reserve space for) a list of addresses of C++ vtables
  // for Klass objects.  They get filled in later.

  void** vtbl_list = (void**)buffer;
  buffer += MetaspaceShared::vtbl_list_size * sizeof(void*);
  Universe::init_self_patching_vtbl_list(vtbl_list, vtbl_list_size);

  // Skip over (reserve space for) dummy C++ vtables Klass objects.
  // They are used as is.

  intptr_t vtable_size = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  buffer += vtable_size;

  // Create the shared symbol table using the compact table at this spot in the
  // misc data space. (Todo: move this to read-only space. Currently
  // this is mapped copy-on-write but will never be written into).

  buffer = (char*)SymbolTable::init_shared_table(buffer);
  SymbolTable::create_table();

  // Create the shared string table using the compact table
  buffer = (char*)StringTable::init_shared_table(mapinfo, buffer);

  // Create the shared dictionary using the bucket array at this spot in
  // the misc data space.  Since the shared dictionary table is never
  // modified, this region (of mapped pages) will be (effectively, if
  // not explicitly) read-only.

  int sharedDictionaryLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  int number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  SystemDictionary::set_shared_dictionary((HashtableBucket<mtClass>*)buffer,
                                          sharedDictionaryLen,
                                          number_of_entries);
  buffer += sharedDictionaryLen;

  // Create the package info table using the bucket array at this spot in
  // the misc data space.  Since the package info table is never
  // modified, this region (of mapped pages) will be (effectively, if
  // not explicitly) read-only.

  int pkgInfoLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  ClassLoader::create_package_info_table((HashtableBucket<mtClass>*)buffer, pkgInfoLen,
                                         number_of_entries);
  buffer += pkgInfoLen;
  ClassLoader::verify();

  // The following data in the shared misc data region are the linked
  // list elements (HashtableEntry objects) for the shared dictionary
  // and package info table.

  int len = *(intptr_t*)buffer;     // skip over shared dictionary entries
  buffer += sizeof(intptr_t);
  buffer += len;

  len = *(intptr_t*)buffer;     // skip over package info table entries
  buffer += sizeof(intptr_t);
  buffer += len;

  len = *(intptr_t*)buffer;     // skip over package info table char[] arrays.
  buffer += sizeof(intptr_t);
  buffer += len;

  intptr_t* array = (intptr_t*)buffer;
  ReadClosure rc(&array);
  serialize(&rc);

  // Close the mapinfo file
  mapinfo->close();

  if (PrintSharedArchiveAndExit) {
    if (PrintSharedDictionary) {
      tty->print_cr("\nShared classes:\n");
      SystemDictionary::print_shared(false);
    }
    if (_archive_loading_failed) {
      tty->print_cr("archive is invalid");
      vm_exit(1);
    } else {
      tty->print_cr("archive is valid");
      vm_exit(0);
    }
  }
}

void MetaspaceShared::fixup_shared_string_regions() {
  FileMapInfo *mapinfo = FileMapInfo::current_info();
  mapinfo->fixup_string_regions();
}

// JVM/TI RedefineClasses() support:
bool MetaspaceShared::remap_shared_readonly_as_readwrite() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  if (UseSharedSpaces) {
    // remap the shared readonly space to shared readwrite, private
    FileMapInfo* mapinfo = FileMapInfo::current_info();
    if (!mapinfo->remap_shared_readonly_as_readwrite()) {
      return false;
    }
  }
  return true;
}

int MetaspaceShared::count_class(const char* classlist_file) {
  if (classlist_file == NULL) {
    return 0;
  }
  char class_name[256];
  int class_count = 0;
  FILE* file = fopen(classlist_file, "r");
  if (file != NULL) {
    while ((fgets(class_name, sizeof class_name, file)) != NULL) {
      if (*class_name == '#') { // comment
        continue;
      }
      class_count++;
    }
    fclose(file);
  } else {
    char errmsg[JVM_MAXPATHLEN];
    os::lasterror(errmsg, JVM_MAXPATHLEN);
    tty->print_cr("Loading classlist failed: %s", errmsg);
    exit(1);
  }

  return class_count;
}

// the sizes are good for typical large applications that have a lot of shared
// classes
void MetaspaceShared::estimate_regions_size() {
  int class_count = count_class(SharedClassListFile);
  class_count += count_class(ExtraSharedClassListFile);

  if (class_count > LargeThresholdClassCount) {
    if (class_count < HugeThresholdClassCount) {
      SET_ESTIMATED_SIZE(Large, ReadOnly);
      SET_ESTIMATED_SIZE(Large, ReadWrite);
      SET_ESTIMATED_SIZE(Large, MiscData);
      SET_ESTIMATED_SIZE(Large, MiscCode);
    } else {
      SET_ESTIMATED_SIZE(Huge,  ReadOnly);
      SET_ESTIMATED_SIZE(Huge,  ReadWrite);
      SET_ESTIMATED_SIZE(Huge,  MiscData);
      SET_ESTIMATED_SIZE(Huge,  MiscCode);
    }
  }
}
