/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/dictionary.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "memory/filemap.hpp"
#include "memory/gcLocker.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceShared.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/signature.hpp"
#include "runtime/vm_operations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/hashtable.inline.hpp"


int MetaspaceShared::_max_alignment = 0;
int MetaspaceShared::max_alignment()                   { return _max_alignment; }
void MetaspaceShared::set_max_alignment(int alignment) { _max_alignment = alignment; }

// Accessor functions to save shared space created for metadata, which has
// extra space allocated at the end for miscellaneous data and code.
ReservedSpace* MetaspaceShared::_shared_rs = NULL;
ReservedSpace* MetaspaceShared::shared_rs()            { return _shared_rs; }
void MetaspaceShared::set_shared_rs(ReservedSpace* rs) { _shared_rs = rs; }

// Read/write a data stream for restoring/preserving metadata pointers and
// miscellaneous data from/to the shared archive file.

void MetaspaceShared::serialize(SerializeClosure* soc) {
  int tag = 0;
  soc->do_tag(--tag);

  assert(!UseCompressedOops, "UseCompressedOops doesn't work with shared archive");
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
  if (k->oop_is_instance()) {
    // Add in the array classes too
    InstanceKlass* ik = InstanceKlass::cast(k);
    ik->array_klasses_do(collect_classes);
  }
}

static void remove_unshareable_in_classes() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    k->remove_unshareable_info();
  }
}

// Walk all methods in the class list and assign a fingerprint.
// so that this part of the ConstMethod* is read only.
static void calculate_fingerprints() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (k->oop_is_instance()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      for (int i = 0; i < ik->methods()->length(); i++) {
        ResourceMark rm;
        Method* m = ik->methods()->at(i);
        (new Fingerprinter(m))->fingerprint();
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
    // Note oop_is_instance() is a virtual call.  After patching vtables
    // all virtual calls on the dummy vtables will restore the original!
    if (obj->oop_is_instance()) {
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


// Populate the shared space.

class VM_PopulateDumpSharedSpace: public VM_Operation {
private:
  ClassLoaderData* _loader_data;
  GrowableArray<Klass*> *_class_promote_order;
  VirtualSpace _md_vs;
  VirtualSpace _mc_vs;

public:
  VM_PopulateDumpSharedSpace(ClassLoaderData* loader_data,
                             GrowableArray<Klass*> *class_promote_order) :
    _loader_data(loader_data) {

    // Split up and initialize the misc code and data spaces
    ReservedSpace* shared_rs = MetaspaceShared::shared_rs();
    int metadata_size = SharedReadOnlySize+SharedReadWriteSize;
    ReservedSpace shared_ro_rw = shared_rs->first_part(metadata_size);
    ReservedSpace misc_section = shared_rs->last_part(metadata_size);

    // Now split into misc sections.
    ReservedSpace md_rs   = misc_section.first_part(SharedMiscDataSize);
    ReservedSpace mc_rs   = misc_section.last_part(SharedMiscDataSize);
    _md_vs.initialize(md_rs, SharedMiscDataSize);
    _mc_vs.initialize(mc_rs, SharedMiscCodeSize);
    _class_promote_order = class_promote_order;
  }

  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit();   // outline because gdb sucks
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
  SystemDictionary::classes_do(collect_classes);

  tty->print_cr("Number of classes %d", _global_klass_objects->length());

  // Update all the fingerprints in the shared methods.
  tty->print("Calculating fingerprints ... ");
  calculate_fingerprints();
  tty->print_cr("done. ");

  // Remove all references outside the metadata
  tty->print("Removing unshareable information ... ");
  remove_unshareable_in_classes();
  tty->print_cr("done. ");

  // Set up the share data and shared code segments.
  char* md_low = _md_vs.low();
  char* md_top = md_low;
  char* md_end = _md_vs.high();
  char* mc_low = _mc_vs.low();
  char* mc_top = mc_low;
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

  // Copy the the symbol table, and the system dictionary to the shared
  // space in usable form.  Copy the hastable
  // buckets first [read-write], then copy the linked lists of entries
  // [read-only].

  SymbolTable::reverse(md_top);
  NOT_PRODUCT(SymbolTable::verify());
  SymbolTable::copy_buckets(&md_top, md_end);

  SystemDictionary::reverse();
  SystemDictionary::copy_buckets(&md_top, md_end);

  ClassLoader::verify();
  ClassLoader::copy_package_info_buckets(&md_top, md_end);
  ClassLoader::verify();

  SymbolTable::copy_table(&md_top, md_end);
  SystemDictionary::copy_table(&md_top, md_end);
  ClassLoader::verify();
  ClassLoader::copy_package_info_table(&md_top, md_end);
  ClassLoader::verify();

  // Write the other data to the output array.
  WriteClosure wc(md_top, md_end);
  MetaspaceShared::serialize(&wc);
  md_top = wc.get_top();

  // Print shared spaces all the time
  const char* fmt = "%s space: " PTR_FORMAT " out of " PTR_FORMAT " words allocated at " PTR_FORMAT ".";
  Metaspace* ro_space = _loader_data->ro_metaspace();
  Metaspace* rw_space = _loader_data->rw_metaspace();
  tty->print_cr(fmt, "ro", ro_space->used_words(Metaspace::NonClassType),
                ro_space->capacity_words(Metaspace::NonClassType),
                ro_space->bottom());
  tty->print_cr(fmt, "rw", rw_space->used_words(Metaspace::NonClassType),
                rw_space->capacity_words(Metaspace::NonClassType),
                rw_space->bottom());
  tty->print_cr(fmt, "md", md_top - md_low, md_end-md_low, md_low);
  tty->print_cr(fmt, "mc", mc_top - mc_low, mc_end-mc_low, mc_low);

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

  // Pass 1 - update file offsets in header.
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

  // Pass 2 - write data.
  mapinfo->open_for_write();
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
  mapinfo->close();

  memmove(vtbl_list, saved_vtbl, vtbl_list_size * sizeof(void*));
}

static void link_shared_classes(Klass* obj, TRAPS) {
  Klass* k = Klass::cast(obj);
  if (k->oop_is_instance()) {
    InstanceKlass* ik = (InstanceKlass*) k;
    // Link the class to cause the bytecodes to be rewritten and the
    // cpcache to be created.
    if (ik->init_state() < InstanceKlass::linked) {
      ik->link_class(THREAD);
      guarantee(!HAS_PENDING_EXCEPTION, "exception in class rewriting");
    }
  }
}


// Support for a simple checksum of the contents of the class list
// file to prevent trivial tampering. The algorithm matches that in
// the MakeClassList program used by the J2SE build process.
#define JSUM_SEED ((jlong)CONST64(0xcafebabebabecafe))
static jlong
jsum(jlong start, const char *buf, const int len)
{
    jlong h = start;
    char *p = (char *)buf, *e = p + len;
    while (p < e) {
        char c = *p++;
        if (c <= ' ') {
            /* Skip spaces and control characters */
            continue;
        }
        h = 31 * h + c;
    }
    return h;
}

// Preload classes from a list, populate the shared spaces and dump to a
// file.
void MetaspaceShared::preload_and_dump(TRAPS) {
  TraceTime timer("Dump Shared Spaces", TraceStartupTime);
  ResourceMark rm;

  // Lock out GC - is it necessary? I don't think we care.
  No_GC_Verifier no_gc;

  // Preload classes to be shared.
  // Should use some os:: method rather than fopen() here. aB.
  // Construct the path to the class list (in jre/lib)
  // Walk up two directories from the location of the VM and
  // optionally tack on "lib" (depending on platform)
  char class_list_path[JVM_MAXPATHLEN];
  os::jvm_path(class_list_path, sizeof(class_list_path));
  for (int i = 0; i < 3; i++) {
    char *end = strrchr(class_list_path, *os::file_separator());
    if (end != NULL) *end = '\0';
  }
  int class_list_path_len = (int)strlen(class_list_path);
  if (class_list_path_len >= 3) {
    if (strcmp(class_list_path + class_list_path_len - 3, "lib") != 0) {
      strcat(class_list_path, os::file_separator());
      strcat(class_list_path, "lib");
    }
  }
  strcat(class_list_path, os::file_separator());
  strcat(class_list_path, "classlist");

  FILE* file = fopen(class_list_path, "r");
  if (file != NULL) {
    jlong computed_jsum  = JSUM_SEED;
    jlong file_jsum      = 0;

    char class_name[256];
    int class_count = 0;
    GrowableArray<Klass*>* class_promote_order = new GrowableArray<Klass*>();

    // sun.io.Converters
    static const char obj_array_sig[] = "[[Ljava/lang/Object;";
    SymbolTable::new_permanent_symbol(obj_array_sig, THREAD);

    // java.util.HashMap
    static const char map_entry_array_sig[] = "[Ljava/util/Map$Entry;";
    SymbolTable::new_permanent_symbol(map_entry_array_sig, THREAD);

    tty->print("Loading classes to share ... ");
    while ((fgets(class_name, sizeof class_name, file)) != NULL) {
      if (*class_name == '#') {
        jint fsh, fsl;
        if (sscanf(class_name, "# %8x%8x\n", &fsh, &fsl) == 2) {
          file_jsum = ((jlong)(fsh) << 32) | (fsl & 0xffffffff);
        }

        continue;
      }
      // Remove trailing newline
      size_t name_len = strlen(class_name);
      class_name[name_len-1] = '\0';

      computed_jsum = jsum(computed_jsum, class_name, (const int)name_len - 1);

      // Got a class name - load it.
      TempNewSymbol class_name_symbol = SymbolTable::new_permanent_symbol(class_name, THREAD);
      guarantee(!HAS_PENDING_EXCEPTION, "Exception creating a symbol.");
      Klass* klass = SystemDictionary::resolve_or_null(class_name_symbol,
                                                         THREAD);
      guarantee(!HAS_PENDING_EXCEPTION, "Exception resolving a class.");
      if (klass != NULL) {
        if (PrintSharedSpaces && Verbose && WizardMode) {
          tty->print_cr("Shared spaces preloaded: %s", class_name);
        }


        InstanceKlass* ik = InstanceKlass::cast(klass);

        // Should be class load order as per -XX:+TraceClassLoadingPreorder
        class_promote_order->append(ik);

        // Link the class to cause the bytecodes to be rewritten and the
        // cpcache to be created. The linking is done as soon as classes
        // are loaded in order that the related data structures (klass and
        // cpCache) are located together.

        if (ik->init_state() < InstanceKlass::linked) {
          ik->link_class(THREAD);
          guarantee(!(HAS_PENDING_EXCEPTION), "exception in class rewriting");
        }

        // TODO: Resolve klasses in constant pool
        ik->constants()->resolve_class_constants(THREAD);

        class_count++;
      } else {
        if (PrintSharedSpaces && Verbose && WizardMode) {
          tty->cr();
          tty->print_cr(" Preload failed: %s", class_name);
        }
      }
      file_jsum = 0; // Checksum must be on last line of file
    }
    if (computed_jsum != file_jsum) {
      tty->cr();
      tty->print_cr("Preload failed: checksum of class list was incorrect.");
      exit(1);
    }

    tty->print_cr("done. ");

    if (PrintSharedSpaces) {
      tty->print_cr("Shared spaces: preloaded %d classes", class_count);
    }

    // Rewrite and unlink classes.
    tty->print("Rewriting and linking classes ... ");

    // Link any classes which got missed.  (It's not quite clear why
    // they got missed.)  This iteration would be unsafe if we weren't
    // single-threaded at this point; however we can't do it on the VM
    // thread because it requires object allocation.
    SystemDictionary::classes_do(link_shared_classes, CATCH);
    tty->print_cr("done. ");

    // Create and dump the shared spaces.   Everything so far is loaded
    // with the null class loader.
    ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
    VM_PopulateDumpSharedSpace op(loader_data, class_promote_order);
    VMThread::execute(&op);

  } else {
    char errmsg[JVM_MAXPATHLEN];
    os::lasterror(errmsg, JVM_MAXPATHLEN);
    tty->print_cr("Loading classlist failed: %s", errmsg);
    exit(1);
  }

  // Since various initialization steps have been undone by this process,
  // it is not reasonable to continue running a java process.
  exit(0);
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


// Save bounds of shared spaces mapped in.
static char* _ro_base = NULL;
static char* _rw_base = NULL;
static char* _md_base = NULL;
static char* _mc_base = NULL;

// Return true if given address is in the mapped shared space.
bool MetaspaceShared::is_in_shared_space(const void* p) {
  if (_ro_base == NULL || _rw_base == NULL) {
    return false;
  } else {
    return ((p > _ro_base && p < (_ro_base + SharedReadOnlySize)) ||
            (p > _rw_base && p < (_rw_base + SharedReadWriteSize)));
  }
}

void MetaspaceShared::print_shared_spaces() {
  gclog_or_tty->print_cr("Shared Spaces:");
  gclog_or_tty->print("  read-only " INTPTR_FORMAT "-" INTPTR_FORMAT,
    _ro_base, _ro_base + SharedReadOnlySize);
  gclog_or_tty->print("  read-write " INTPTR_FORMAT "-" INTPTR_FORMAT,
    _rw_base, _rw_base + SharedReadWriteSize);
  gclog_or_tty->cr();
  gclog_or_tty->print("  misc-data " INTPTR_FORMAT "-" INTPTR_FORMAT,
    _md_base, _md_base + SharedMiscDataSize);
  gclog_or_tty->print("  misc-code " INTPTR_FORMAT "-" INTPTR_FORMAT,
    _mc_base, _mc_base + SharedMiscCodeSize);
  gclog_or_tty->cr();
}


// Map shared spaces at requested addresses and return if succeeded.
// Need to keep the bounds of the ro and rw space for the Metaspace::contains
// call, or is_in_shared_space.
bool MetaspaceShared::map_shared_spaces(FileMapInfo* mapinfo) {
  size_t image_alignment = mapinfo->alignment();

  // Map in the shared memory and then map the regions on top of it
  ReservedSpace shared_rs = mapinfo->reserve_shared_memory();
  if (!shared_rs.is_reserved()) return false;

  // Split reserved memory into pieces (windows needs this)
  ReservedSpace ro_rs   = shared_rs.first_part(SharedReadOnlySize);
  ReservedSpace tmp_rs1 = shared_rs.last_part(SharedReadOnlySize);
  ReservedSpace rw_rs   = tmp_rs1.first_part(SharedReadWriteSize);
  ReservedSpace tmp_rs2 = tmp_rs1.last_part(SharedReadWriteSize);
  ReservedSpace md_rs   = tmp_rs2.first_part(SharedMiscDataSize);
  ReservedSpace mc_rs   = tmp_rs2.last_part(SharedMiscDataSize);

  // Map each shared region
  if ((_ro_base = mapinfo->map_region(ro)) != NULL &&
      (_rw_base = mapinfo->map_region(rw)) != NULL &&
      (_md_base = mapinfo->map_region(md)) != NULL &&
      (_mc_base = mapinfo->map_region(mc)) != NULL &&
      (image_alignment == (size_t)max_alignment())) {
    // Success (no need to do anything)
    return true;
  } else {
    // If there was a failure in mapping any of the spaces, unmap the ones
    // that succeeded
    if (_ro_base != NULL) mapinfo->unmap_region(ro);
    if (_rw_base != NULL) mapinfo->unmap_region(rw);
    if (_md_base != NULL) mapinfo->unmap_region(md);
    if (_mc_base != NULL) mapinfo->unmap_region(mc);
    // Release the entire mapped region
    shared_rs.release();
    // If -Xshare:on is specified, print out the error message and exit VM,
    // otherwise, set UseSharedSpaces to false and continue.
    if (RequireSharedSpaces) {
      vm_exit_during_initialization("Unable to use shared archive.", NULL);
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

  char* buffer = mapinfo->region_base(md);

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

  // Create the symbol table using the bucket array at this spot in the
  // misc data space.  Since the symbol table is often modified, this
  // region (of mapped pages) will be copy-on-write.

  int symbolTableLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  int number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  SymbolTable::create_table((HashtableBucket<mtSymbol>*)buffer, symbolTableLen,
                            number_of_entries);
  buffer += symbolTableLen;

  // Create the shared dictionary using the bucket array at this spot in
  // the misc data space.  Since the shared dictionary table is never
  // modified, this region (of mapped pages) will be (effectively, if
  // not explicitly) read-only.

  int sharedDictionaryLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  number_of_entries = *(intptr_t*)buffer;
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
  // list elements (HashtableEntry objects) for the symbol table, string
  // table, and shared dictionary.  The heap objects refered to by the
  // symbol table, string table, and shared dictionary are permanent and
  // unmovable.  Since new entries added to the string and symbol tables
  // are always added at the beginning of the linked lists, THESE LINKED
  // LIST ELEMENTS ARE READ-ONLY.

  int len = *(intptr_t*)buffer; // skip over symbol table entries
  buffer += sizeof(intptr_t);
  buffer += len;

  len = *(intptr_t*)buffer;     // skip over shared dictionary entries
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
