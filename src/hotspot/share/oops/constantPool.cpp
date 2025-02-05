/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotConstantPoolResolver.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapLoader.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/metadataOnStackMark.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "interpreter/bootstrapInfo.hpp"
#include "interpreter/linkResolver.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/array.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/cpCache.inline.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/perfData.hpp"
#include "runtime/signature.hpp"
#include "runtime/vframe.inline.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/copy.hpp"

ConstantPool* ConstantPool::allocate(ClassLoaderData* loader_data, int length, TRAPS) {
  Array<u1>* tags = MetadataFactory::new_array<u1>(loader_data, length, 0, CHECK_NULL);
  int size = ConstantPool::size(length);
  return new (loader_data, size, MetaspaceObj::ConstantPoolType, THREAD) ConstantPool(tags);
}

void ConstantPool::copy_fields(const ConstantPool* orig) {
  // Preserve dynamic constant information from the original pool
  if (orig->has_dynamic_constant()) {
    set_has_dynamic_constant();
  }

  set_major_version(orig->major_version());
  set_minor_version(orig->minor_version());

  set_source_file_name_index(orig->source_file_name_index());
  set_generic_signature_index(orig->generic_signature_index());
}

#ifdef ASSERT

// MetaspaceObj allocation invariant is calloc equivalent memory
// simple verification of this here (JVM_CONSTANT_Invalid == 0 )
static bool tag_array_is_zero_initialized(Array<u1>* tags) {
  assert(tags != nullptr, "invariant");
  const int length = tags->length();
  for (int index = 0; index < length; ++index) {
    if (JVM_CONSTANT_Invalid != tags->at(index)) {
      return false;
    }
  }
  return true;
}

#endif

ConstantPool::ConstantPool() {
  assert(CDSConfig::is_dumping_static_archive() || CDSConfig::is_using_archive(), "only for CDS");
}

ConstantPool::ConstantPool(Array<u1>* tags) :
  _tags(tags),
  _length(tags->length()) {

    assert(_tags != nullptr, "invariant");
    assert(tags->length() == _length, "invariant");
    assert(tag_array_is_zero_initialized(tags), "invariant");
    assert(0 == flags(), "invariant");
    assert(0 == version(), "invariant");
    assert(nullptr == _pool_holder, "invariant");
}

void ConstantPool::deallocate_contents(ClassLoaderData* loader_data) {
  if (cache() != nullptr) {
    MetadataFactory::free_metadata(loader_data, cache());
    set_cache(nullptr);
  }

  MetadataFactory::free_array<Klass*>(loader_data, resolved_klasses());
  set_resolved_klasses(nullptr);

  MetadataFactory::free_array<u4>(loader_data, bsm_attribute_offsets());
  MetadataFactory::free_array<u2>(loader_data, bsm_attribute_entries());
  set_bsm_attribute_offsets(nullptr);
  set_bsm_attribute_entries(nullptr);

  release_C_heap_structures();

  // free tag array
  MetadataFactory::free_array<u1>(loader_data, tags());
  set_tags(nullptr);
}

void ConstantPool::release_C_heap_structures() {
  // walk constant pool and decrement symbol reference counts
  unreference_symbols();
}

void ConstantPool::metaspace_pointers_do(MetaspaceClosure* it) {
  log_trace(cds)("Iter(ConstantPool): %p", this);

  it->push(&_tags, MetaspaceClosure::_writable);
  it->push(&_cache);
  it->push(&_pool_holder);
  it->push(&_bsm_attribute_offsets);
  it->push(&_bsm_attribute_entries);
  it->push(&_resolved_klasses, MetaspaceClosure::_writable);

  for (int i = 0; i < length(); i++) {
    // The only MSO's embedded in the CP entries are Symbols:
    //   JVM_CONSTANT_String
    //   JVM_CONSTANT_Utf8
    constantTag ctag = tag_at(i);
    if (ctag.is_string() || ctag.is_utf8()) {
      it->push(symbol_at_addr(i));
    }
  }
}

objArrayOop ConstantPool::resolved_references() const {
  return _cache->resolved_references();
}

// Called from outside constant pool resolution where a resolved_reference array
// may not be present.
objArrayOop ConstantPool::resolved_references_or_null() const {
  if (_cache == nullptr) {
    return nullptr;
  } else {
    return _cache->resolved_references();
  }
}

oop ConstantPool::resolved_reference_at(int index) const {
  oop result = resolved_references()->obj_at(index);
  assert(oopDesc::is_oop_or_null(result), "Must be oop");
  return result;
}

// Use a CAS for multithreaded access
oop ConstantPool::set_resolved_reference_at(int index, oop new_result) {
  assert(oopDesc::is_oop_or_null(new_result), "Must be oop");
  return resolved_references()->replace_if_null(index, new_result);
}

// Create resolved_references array and mapping array for original cp indexes
// The ldc bytecode was rewritten to have the resolved reference array index so need a way
// to map it back for resolving and some unlikely miscellaneous uses.
// The objects created by invokedynamic are appended to this list.
void ConstantPool::initialize_resolved_references(ClassLoaderData* loader_data,
                                                  const intStack& reference_map,
                                                  int constant_pool_map_length,
                                                  TRAPS) {
  // Initialized the resolved object cache.
  int map_length = reference_map.length();
  if (map_length > 0) {
    // Only need mapping back to constant pool entries.  The map isn't used for
    // invokedynamic resolved_reference entries.  For invokedynamic entries,
    // the constant pool cache index has the mapping back to both the constant
    // pool and to the resolved reference index.
    if (constant_pool_map_length > 0) {
      Array<u2>* om = MetadataFactory::new_array<u2>(loader_data, constant_pool_map_length, CHECK);

      for (int i = 0; i < constant_pool_map_length; i++) {
        int x = reference_map.at(i);
        assert(x == (int)(jushort) x, "klass index is too big");
        om->at_put(i, (jushort)x);
      }
      set_reference_map(om);
    }

    // Create Java array for holding resolved strings, methodHandles,
    // methodTypes, invokedynamic and invokehandle appendix objects, etc.
    objArrayOop stom = oopFactory::new_objArray(vmClasses::Object_klass(), map_length, CHECK);
    HandleMark hm(THREAD);
    Handle refs_handle (THREAD, stom);  // must handleize.
    set_resolved_references(loader_data->add_handle(refs_handle));

    // Create a "scratch" copy of the resolved references array to archive
    if (CDSConfig::is_dumping_heap()) {
      objArrayOop scratch_references = oopFactory::new_objArray(vmClasses::Object_klass(), map_length, CHECK);
      HeapShared::add_scratch_resolved_references(this, scratch_references);
    }
  }
}

void ConstantPool::allocate_resolved_klasses(ClassLoaderData* loader_data, int num_klasses, TRAPS) {
  // A ConstantPool can't possibly have 0xffff valid class entries,
  // because entry #0 must be CONSTANT_Invalid, and each class entry must refer to a UTF8
  // entry for the class's name. So at most we will have 0xfffe class entries.
  // This allows us to use 0xffff (ConstantPool::_temp_resolved_klass_index) to indicate
  // UnresolvedKlass entries that are temporarily created during class redefinition.
  assert(num_klasses < KlassReference::_temp_resolved_klass_index, "sanity");
  assert(resolved_klasses() == nullptr, "sanity");
  Array<Klass*>* rk = MetadataFactory::new_array<Klass*>(loader_data, num_klasses, CHECK);
  set_resolved_klasses(rk);
}

void ConstantPool::initialize_unresolved_klasses(ClassLoaderData* loader_data, TRAPS) {
  int len = length();
  int num_klasses = 0;
  for (int i = 1; i <len; i++) {
    switch (tag_at(i).value()) {
    case JVM_CONSTANT_ClassIndex:
      {
        const int class_index = klass_index_at(i);
        unresolved_klass_at_put(i, class_index, num_klasses++);
      }
      break;
#ifndef PRODUCT
    case JVM_CONSTANT_Class:
    case JVM_CONSTANT_UnresolvedClass:
    case JVM_CONSTANT_UnresolvedClassInError:
      // All of these should have been reverted back to ClassIndex before calling
      // this function.
      ShouldNotReachHere();
#endif
    }
  }
  allocate_resolved_klasses(loader_data, num_klasses, THREAD);
}

// Hidden class support:
void ConstantPool::klass_at_put(int class_index, Klass* k) {
  assert(k != nullptr, "must be valid klass");
  KlassReference kref(this, class_index);
  resolved_klass_release_at_put(kref.resolved_klass_index(), k);

  // The interpreter assumes when the tag is stored, the klass is resolved
  // and the Klass* non-null, so we need hardware store ordering here.
  release_tag_at_put(class_index, JVM_CONSTANT_Class);
}

#if INCLUDE_CDS_JAVA_HEAP
template <typename Function>
void ConstantPool::iterate_archivable_resolved_references(Function function) {
  objArrayOop rr = resolved_references();
  if (rr != nullptr && cache() != nullptr && CDSConfig::is_dumping_invokedynamic()) {
    Array<ResolvedIndyEntry>* indy_entries = cache()->resolved_indy_entries();
    if (indy_entries != nullptr) {
      for (int i = 0; i < indy_entries->length(); i++) {
        ResolvedIndyEntry *rie = indy_entries->adr_at(i);
        BSMAttributeEntry *bsme = rie->bsme(this);
        if (rie->is_resolved() && AOTConstantPoolResolver::is_resolution_deterministic(this, rie->constant_pool_index())) {
          int rr_index = rie->resolved_references_index();
          assert(resolved_reference_at(rr_index) != nullptr, "must exist");
          function(rr_index);

          // Save the BSM as well (sometimes the JIT looks up the BSM it for replay)
          int bsm_mh_cp_index = bsme->bootstrap_method_index();
          int bsm_rr_index = cp_to_object_index(bsm_mh_cp_index);
          assert(resolved_reference_at(bsm_rr_index) != nullptr, "must exist");
          function(bsm_rr_index);
        }
      }
    }

    Array<ResolvedMethodEntry>* method_entries = cache()->resolved_method_entries();
    if (method_entries != nullptr) {
      for (int i = 0; i < method_entries->length(); i++) {
        ResolvedMethodEntry* rme = method_entries->adr_at(i);
        if (rme->is_resolved(Bytecodes::_invokehandle) && rme->has_appendix() &&
            cache()->can_archive_resolved_method(this, rme)) {
          int rr_index = rme->resolved_references_index();
          assert(resolved_reference_at(rr_index) != nullptr, "must exist");
          function(rr_index);
        }
      }
    }
  }
}

// Returns the _resolved_reference array after removing unarchivable items from it.
// Returns null if this class is not supported, or _resolved_reference doesn't exist.
objArrayOop ConstantPool::prepare_resolved_references_for_archiving() {
  if (_cache == nullptr) {
    return nullptr; // nothing to do
  }

  InstanceKlass *ik = pool_holder();
  if (!SystemDictionaryShared::is_builtin_loader(ik->class_loader_data())) {
    // Archiving resolved references for classes from non-builtin loaders
    // is not yet supported.
    return nullptr;
  }

  objArrayOop rr = resolved_references();
  if (rr != nullptr) {
    ResourceMark rm;
    int rr_len = rr->length();
    GrowableArray<bool> keep_resolved_refs(rr_len, rr_len, false);

    iterate_archivable_resolved_references([&](int rr_index) {
      keep_resolved_refs.at_put(rr_index, true);
    });

    objArrayOop scratch_rr = HeapShared::scratch_resolved_references(this);
    Array<u2>* ref_map = reference_map();
    int ref_map_len = ref_map == nullptr ? 0 : ref_map->length();
    for (int i = 0; i < rr_len; i++) {
      oop obj = rr->obj_at(i);
      scratch_rr->obj_at_put(i, nullptr);
      if (obj != nullptr) {
        if (i < ref_map_len) {
          int index = object_to_cp_index(i);
          if (tag_at(index).is_string()) {
            assert(java_lang_String::is_instance(obj), "must be");
            if (!ArchiveHeapWriter::is_string_too_large_to_archive(obj)) {
              scratch_rr->obj_at_put(i, obj);
            }
            continue;
          }
        }

        if (keep_resolved_refs.at(i)) {
          scratch_rr->obj_at_put(i, obj);
        }
      }
    }
    return scratch_rr;
  }
  return rr;
}

void ConstantPool::add_dumped_interned_strings() {
  InstanceKlass* ik = pool_holder();
  if (!ik->is_linked()) {
    // resolved_references() doesn't exist yet, so we have no resolved CONSTANT_String entries. However,
    // some static final fields may have default values that were initialized when the class was parsed.
    // We need to enter those into the CDS archive strings table.
    for (JavaFieldStream fs(ik); !fs.done(); fs.next()) {
      if (fs.access_flags().is_static()) {
        fieldDescriptor& fd = fs.field_descriptor();
        if (fd.field_type() == T_OBJECT) {
          int offset = fd.offset();
          check_and_add_dumped_interned_string(ik->java_mirror()->obj_field(offset));
        }
      }
    }
  } else {
    objArrayOop rr = resolved_references();
    if (rr != nullptr) {
      int rr_len = rr->length();
      for (int i = 0; i < rr_len; i++) {
        check_and_add_dumped_interned_string(rr->obj_at(i));
      }
    }
  }
}

void ConstantPool::check_and_add_dumped_interned_string(oop obj) {
  if (obj != nullptr && java_lang_String::is_instance(obj) &&
      !ArchiveHeapWriter::is_string_too_large_to_archive(obj)) {
    HeapShared::add_to_dumped_interned_strings(obj);
  }
}

#endif

#if INCLUDE_CDS
// CDS support. Create a new resolved_references array.
void ConstantPool::restore_unshareable_info(TRAPS) {
  if (!_pool_holder->is_linked() && !_pool_holder->is_rewritten()) {
    return;
  }
  assert(is_constantPool(), "ensure C++ vtable is restored");
  assert(on_stack(), "should always be set for shared constant pools");
  assert(is_shared(), "should always be set for shared constant pools");
  if (is_for_method_handle_intrinsic()) {
    // See the same check in remove_unshareable_info() below.
    assert(cache() == nullptr, "must not have cpCache");
    return;
  }
  assert(_cache != nullptr, "constant pool _cache should not be null");

  // Only create the new resolved references array if it hasn't been attempted before
  if (resolved_references() != nullptr) return;

  if (vmClasses::Object_klass_loaded()) {
    ClassLoaderData* loader_data = pool_holder()->class_loader_data();
#if INCLUDE_CDS_JAVA_HEAP
    if (ArchiveHeapLoader::is_in_use() &&
        _cache->archived_references() != nullptr) {
      oop archived = _cache->archived_references();
      // Create handle for the archived resolved reference array object
      HandleMark hm(THREAD);
      Handle refs_handle(THREAD, archived);
      set_resolved_references(loader_data->add_handle(refs_handle));
      _cache->clear_archived_references();
    } else
#endif
    {
      // No mapped archived resolved reference array
      // Recreate the object array and add to ClassLoaderData.
      int map_length = resolved_reference_length();
      if (map_length > 0) {
        objArrayOop stom = oopFactory::new_objArray(vmClasses::Object_klass(), map_length, CHECK);
        HandleMark hm(THREAD);
        Handle refs_handle(THREAD, stom);  // must handleize.
        set_resolved_references(loader_data->add_handle(refs_handle));
      }
    }
  }
}

void ConstantPool::remove_unshareable_info() {
  // Shared ConstantPools are in the RO region, so the _flags cannot be modified.
  // The _on_stack flag is used to prevent ConstantPools from deallocation during
  // class redefinition. Since shared ConstantPools cannot be deallocated anyway,
  // we always set _on_stack to true to avoid having to change _flags during runtime.
  _flags |= (_on_stack | _is_shared);

  if (is_for_method_handle_intrinsic()) {
    // This CP was created by Method::make_method_handle_intrinsic() and has nothing
    // that need to be removed/restored. It has no cpCache since the intrinsic methods
    // don't have any bytecodes.
    assert(cache() == nullptr, "must not have cpCache");
    return;
  }

  // resolved_references(): remember its length. If it cannot be restored
  // from the archived heap objects at run time, we need to dynamically allocate it.
  if (cache() != nullptr) {
    set_resolved_reference_length(
        resolved_references() != nullptr ? resolved_references()->length() : 0);
    set_resolved_references(OopHandle());
  }
  remove_unshareable_entries();
}

static const char* get_type(Klass* k) {
  const char* type;
  Klass* src_k;
  if (ArchiveBuilder::is_active() && ArchiveBuilder::current()->is_in_buffer_space(k)) {
    src_k = ArchiveBuilder::current()->get_source_addr(k);
  } else {
    src_k = k;
  }

  if (src_k->is_objArray_klass()) {
    src_k = ObjArrayKlass::cast(src_k)->bottom_klass();
    assert(!src_k->is_objArray_klass(), "sanity");
  }

  if (src_k->is_typeArray_klass()) {
    type = "prim";
  } else {
    InstanceKlass* src_ik = InstanceKlass::cast(src_k);
    oop loader = src_ik->class_loader();
    if (loader == nullptr) {
      type = "boot";
    } else if (loader == SystemDictionary::java_platform_loader()) {
      type = "plat";
    } else if (loader == SystemDictionary::java_system_loader()) {
      type = "app";
    } else {
      type = "unreg";
    }
  }

  return type;
}

void ConstantPool::remove_unshareable_entries() {
  ResourceMark rm;
  log_info(cds, resolve)("Archiving CP entries for %s", pool_holder()->name()->as_C_string());
  for (int cp_index = 1; cp_index < length(); cp_index++) { // cp_index 0 is unused
    int cp_tag = tag_at(cp_index).value();
    switch (cp_tag) {
    case JVM_CONSTANT_UnresolvedClass:
      ArchiveBuilder::alloc_stats()->record_klass_cp_entry(false, false);
      break;
    case JVM_CONSTANT_UnresolvedClassInError:
      tag_at_put(cp_index, JVM_CONSTANT_UnresolvedClass);
      ArchiveBuilder::alloc_stats()->record_klass_cp_entry(false, true);
      break;
    case JVM_CONSTANT_MethodHandleInError:
      tag_at_put(cp_index, JVM_CONSTANT_MethodHandle);
      break;
    case JVM_CONSTANT_MethodTypeInError:
      tag_at_put(cp_index, JVM_CONSTANT_MethodType);
      break;
    case JVM_CONSTANT_DynamicInError:
      tag_at_put(cp_index, JVM_CONSTANT_Dynamic);
      break;
    case JVM_CONSTANT_Class:
      remove_resolved_klass_if_non_deterministic(cp_index);
      break;
    default:
      break;
    }
  }

  if (cache() != nullptr) {
    // cache() is null if this class is not yet linked.
    cache()->remove_unshareable_info();
  }
}

void ConstantPool::remove_resolved_klass_if_non_deterministic(int cp_index) {
  assert(ArchiveBuilder::current()->is_in_buffer_space(this), "must be");
  assert(tag_at(cp_index).is_klass(), "must be resolved");

  Klass* k = resolved_klass_at(cp_index);
  bool can_archive;

  if (k == nullptr) {
    // We'd come here if the referenced class has been excluded via
    // SystemDictionaryShared::is_excluded_class(). As a result, ArchiveBuilder
    // has cleared the resolved_klasses()->at(...) pointer to null. Thus, we
    // need to revert the tag to JVM_CONSTANT_UnresolvedClass.
    can_archive = false;
  } else {
    ConstantPool* src_cp = ArchiveBuilder::current()->get_source_addr(this);
    can_archive = AOTConstantPoolResolver::is_resolution_deterministic(src_cp, cp_index);
  }

  if (!can_archive) {
    KlassReference kref(this, cp_index);
    int resolved_klass_index = kref.resolved_klass_index();
    resolved_klasses()->at_put(resolved_klass_index, nullptr);
    tag_at_put(cp_index, JVM_CONSTANT_UnresolvedClass);
  }

  LogStreamHandle(Trace, cds, resolve) log;
  if (log.is_enabled()) {
    ResourceMark rm;
    log.print("%s klass  CP entry [%3d]: %s %s",
              (can_archive ? "archived" : "reverted"),
              cp_index, pool_holder()->name()->as_C_string(), get_type(pool_holder()));
    if (can_archive) {
      log.print(" => %s %s%s", k->name()->as_C_string(), get_type(k),
                (!k->is_instance_klass() || pool_holder()->is_subtype_of(k)) ? "" : " (not supertype)");
    } else {
      Symbol* name = klass_name_at(cp_index);
      log.print(" => %s", name->as_C_string());
    }
  }

  ArchiveBuilder::alloc_stats()->record_klass_cp_entry(can_archive, /*reverted=*/!can_archive);
}
#endif // INCLUDE_CDS

int ConstantPool::cp_to_object_index(int cp_index) {
  // this is harder don't do this so much.
  int i = reference_map()->find(checked_cast<u2>(cp_index));
  // We might not find the index for jsr292 call.
  return (i < 0) ? _no_index_sentinel : i;
}

void ConstantPool::string_at_put(int obj_index, oop str) {
  oop result = set_resolved_reference_at(obj_index, str);
  assert(result == nullptr || result == str, "Only set once or to the same string.");
}

void ConstantPool::trace_class_resolution(const constantPoolHandle& this_cp, Klass* k) {
  ResourceMark rm;
  int line_number = -1;
  const char * source_file = nullptr;
  if (JavaThread::current()->has_last_Java_frame()) {
    // try to identify the method which called this function.
    vframeStream vfst(JavaThread::current());
    if (!vfst.at_end()) {
      line_number = vfst.method()->line_number_from_bci(vfst.bci());
      Symbol* s = vfst.method()->method_holder()->source_file_name();
      if (s != nullptr) {
        source_file = s->as_C_string();
      }
    }
  }
  if (k != this_cp->pool_holder()) {
    // only print something if the classes are different
    if (source_file != nullptr) {
      log_debug(class, resolve)("%s %s %s:%d",
                 this_cp->pool_holder()->external_name(),
                 k->external_name(), source_file, line_number);
    } else {
      log_debug(class, resolve)("%s %s",
                 this_cp->pool_holder()->external_name(),
                 k->external_name());
    }
  }
}

Klass* ConstantPool::klass_at_impl(const constantPoolHandle& this_cp, int cp_index,
                                   TRAPS) {
  JavaThread* javaThread = THREAD;

  // It should be safe to rely on the tag here, since the tag is updated
  // *after* the resolved_klasses entry is updated.  Both tag and RK entry
  // are read and written with appropriate acquires and releases.
  KlassReference kref(this_cp, cp_index);

  // The tag must be JVM_CONSTANT_Class in order to read the correct value from
  // the unresolved_klasses() array.
  if (kref.is_resolved()) {
    Klass* klass = kref.resolved_klass(this_cp);
    // We always publish the Klass* before updating the tag.
    //FIXME: (8349405) This assert should be true.
    //assert(klass != nullptr, "pointer must be published before caller reads");
    if (klass != nullptr) {
      return klass;
    }
  }

  // This tag doesn't change back to unresolved class unless at a safepoint.
  if (this_cp->tag_at(cp_index).is_unresolved_klass_in_error()) {
    // The original attempt to resolve this constant pool entry failed so find the
    // class of the original error and throw another error of the same class
    // (JVMS 5.4.3).
    // If there is a detail message, pass that detail message to the error.
    // The JVMS does not strictly require us to duplicate the same detail message,
    // or any internal exception fields such as cause or stacktrace.  But since the
    // detail message is often a class name or other literal string, we will repeat it
    // if we can find it in the symbol table.
    throw_resolution_error(this_cp, cp_index, CHECK_NULL);
    ShouldNotReachHere();
  }

  HandleMark hm(THREAD);
  Handle mirror_handle;
  Symbol* name = kref.name(this_cp);
  Handle loader (THREAD, this_cp->pool_holder()->class_loader());

  Klass* k;
  {
    // Turn off the single stepping while doing class resolution
    JvmtiHideSingleStepping jhss(javaThread);
    k = SystemDictionary::resolve_or_fail(name, loader, true, THREAD);
  } //  JvmtiHideSingleStepping jhss(javaThread);

  if (!HAS_PENDING_EXCEPTION) {
    // preserve the resolved klass from unloading
    mirror_handle = Handle(THREAD, k->java_mirror());
    // Do access check for klasses
    verify_constant_pool_resolve(this_cp, k, THREAD);
  }

  // Failed to resolve class. We must record the errors so that subsequent attempts
  // to resolve this constant pool entry fail with the same error (JVMS 5.4.3).
  if (HAS_PENDING_EXCEPTION) {
    save_and_throw_exception(this_cp, cp_index, constantTag(JVM_CONSTANT_UnresolvedClass), CHECK_NULL);
    // If CHECK_NULL above doesn't return the exception, that means that
    // some other thread has beaten us and has resolved the class.
    // To preserve old behavior, we return the resolved class.
    // FIXME: (8349405) should probably be:  return kref.resolved_klass(this_cp);
    Klass* klass = this_cp->resolved_klass_at_acquire(kref.resolved_klass_index());
    assert(klass != nullptr, "must be resolved if exception was cleared");
    return klass;
  }

  // logging for class+resolve.
  if (log_is_enabled(Debug, class, resolve)){
    trace_class_resolution(this_cp, k);
  }

  // The releasing store publishes any pending writes into the Klass
  // object before the Klass pointer itself is published.
  // This is matched elsewhere by an acquiring load.
  this_cp->resolved_klass_release_at_put(kref.resolved_klass_index(), k);

  // The interpreter assumes when the tag is stored, the klass is resolved
  // and the Klass* stored in _resolved_klasses is non-null, so we need
  // hardware store ordering here.
  // We also need to CAS to not overwrite an error from a racing thread.

  jbyte old_tag = Atomic::cmpxchg((jbyte*)this_cp->tag_addr_at(cp_index),
                                  (jbyte)JVM_CONSTANT_UnresolvedClass,
                                  (jbyte)JVM_CONSTANT_Class);

  // We need to recheck exceptions from racing thread and return the same.
  if (old_tag == JVM_CONSTANT_UnresolvedClassInError) {
    // Remove klass.
    this_cp->resolved_klasses()->at_put(kref.resolved_klass_index(), nullptr);
    throw_resolution_error(this_cp, cp_index, CHECK_NULL);
  }

  return k;
}


// Does not update ConstantPool* - to avoid any exception throwing. Used
// by compiler and exception handling.  Also used to avoid classloads for
// instanceof operations. Returns null if the class has not been loaded or
// if the verification of constant pool failed
Klass* ConstantPool::klass_at_if_loaded(const constantPoolHandle& this_cp, int which) {
  KlassReference kref(this_cp, which);

  if (kref.tag().is_klass()) {
    Klass* k = kref.resolved_klass(this_cp);
    assert(k != nullptr, "should be resolved");
    return k;
  } else if (kref.tag().is_unresolved_klass_in_error()) {
    return nullptr;
  } else {
    Thread* current = Thread::current();
    HandleMark hm(current);
    Symbol* name = kref.name(this_cp);
    oop loader = this_cp->pool_holder()->class_loader();
    Handle h_loader (current, loader);
    Klass* k = SystemDictionary::find_instance_klass(current, name, h_loader);

    // Avoid constant pool verification at a safepoint, as it takes the Module_lock.
    if (k != nullptr && current->is_Java_thread()) {
      // Make sure that resolving is legal
      JavaThread* THREAD = JavaThread::cast(current); // For exception macros.
      ExceptionMark em(THREAD);
      // return null if verification fails
      verify_constant_pool_resolve(this_cp, k, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        return nullptr;
      }
      return k;
    } else {
      return k;
    }
  }
}

Method* ConstantPool::method_at_if_loaded(const constantPoolHandle& cpool,
                                                   int which) {
  if (cpool->cache() == nullptr)  return nullptr;  // nothing to load yet
  if (!(which >= 0 && which < cpool->resolved_method_entries_length())) {
    // FIXME: should be an assert
    log_debug(class, resolve)("bad operand %d in:", which); cpool->print();
    return nullptr;
  }
  return cpool->cache()->method_if_resolved(which);
}


bool ConstantPool::has_appendix_at_if_loaded(const constantPoolHandle& cpool, int which, Bytecodes::Code code) {
  if (cpool->cache() == nullptr)  return false;  // nothing to load yet
  if (code == Bytecodes::_invokedynamic) {
    return cpool->resolved_indy_entry_at(which)->has_appendix();
  } else {
    return cpool->resolved_method_entry_at(which)->has_appendix();
  }
}

oop ConstantPool::appendix_at_if_loaded(const constantPoolHandle& cpool, int which, Bytecodes::Code code) {
  if (cpool->cache() == nullptr)  return nullptr;  // nothing to load yet
  if (code == Bytecodes::_invokedynamic) {
    return cpool->resolved_reference_from_indy(which);
  } else {
    return cpool->cache()->appendix_if_resolved(which);
  }
}


bool ConstantPool::has_local_signature_at_if_loaded(const constantPoolHandle& cpool, int which, Bytecodes::Code code) {
  if (cpool->cache() == nullptr)  return false;  // nothing to load yet
  if (code == Bytecodes::_invokedynamic) {
    return cpool->resolved_indy_entry_at(which)->has_local_signature();
  } else {
    return cpool->resolved_method_entry_at(which)->has_local_signature();
  }
}

// Translate index, which could be CPCache index or Indy index, to a constant pool index
int ConstantPool::to_cp_index(int index, Bytecodes::Code code) const {
  assert(cache() != nullptr, "'index' is a rewritten index so this class must have been rewritten");
  switch(code) {
    case Bytecodes::_invokedynamic:
      {
        ResolvedIndyEntry* ie = cache()->resolved_indy_entry_at(index);
        int cp_index = ie->constant_pool_index();
        assert(tag_at(cp_index).has_bootstrap(), "index contains symbolic ref");
        return cp_index;
      }
    case Bytecodes::_getfield:
    case Bytecodes::_getstatic:
    case Bytecodes::_putfield:
    case Bytecodes::_putstatic:
      return resolved_field_entry_at(index)->constant_pool_index();
    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokehandle:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
    case Bytecodes::_invokevirtual:
    case Bytecodes::_fast_invokevfinal: // Bytecode interpreter uses this
      return resolved_method_entry_at(index)->constant_pool_index();
    default:
      fatal("Unexpected bytecode: %s", Bytecodes::name(code));
  }
}

bool ConstantPool::is_resolved(int index, Bytecodes::Code code) {
  assert(cache() != nullptr, "'index' is a rewritten index so this class must have been rewritten");
  switch(code) {
    case Bytecodes::_invokedynamic:
      return resolved_indy_entry_at(index)->is_resolved();

    case Bytecodes::_getfield:
    case Bytecodes::_getstatic:
    case Bytecodes::_putfield:
    case Bytecodes::_putstatic:
      return resolved_field_entry_at(index)->is_resolved(code);

    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokehandle:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
    case Bytecodes::_invokevirtual:
    case Bytecodes::_fast_invokevfinal: // Bytecode interpreter uses this
      return resolved_method_entry_at(index)->is_resolved(code);

    default:
      fatal("Unexpected bytecode: %s", Bytecodes::name(code));
  }
}

void ConstantPool::verify_constant_pool_resolve(const constantPoolHandle& this_cp, Klass* k, TRAPS) {
  if (!(k->is_instance_klass() || k->is_objArray_klass())) {
    return;  // short cut, typeArray klass is always accessible
  }
  Klass* holder = this_cp->pool_holder();
  LinkResolver::check_klass_accessibility(holder, k, CHECK);
}


char* ConstantPool::string_at_noresolve(int cp_index) {
  return unresolved_string_at(cp_index)->as_C_string();
}

void ConstantPool::resolve_string_constants_impl(const constantPoolHandle& this_cp, TRAPS) {
  for (int index = 1; index < this_cp->length(); index++) { // Index 0 is unused
    if (this_cp->tag_at(index).is_string()) {
      this_cp->string_at(index, CHECK);
    }
  }
}

static const char* exception_message(const constantPoolHandle& this_cp, int which, constantTag tag, oop pending_exception) {
  // Note: caller needs ResourceMark

  // Dig out the detailed message to reuse if possible
  const char* msg = java_lang_Throwable::message_as_utf8(pending_exception);
  if (msg != nullptr) {
    return msg;
  }

  Symbol* message = nullptr;
  // Return specific message for the tag
  switch (tag.value()) {
  case JVM_CONSTANT_UnresolvedClass:
    // return the class name in the error message
    message = this_cp->klass_name_at(which);
    break;
  case JVM_CONSTANT_MethodHandle:
    // return the method handle name in the error message
    message = MethodHandleReference(this_cp, which).name(this_cp);
    break;
  case JVM_CONSTANT_MethodType:
    // return the method type signature in the error message
    message = MethodTypeReference(this_cp, which).signature(this_cp);
    break;
  case JVM_CONSTANT_Dynamic:
    // return the name of the condy in the error message
    message = BootstrapReference(this_cp, which).name(this_cp);
    break;
  default:
    ShouldNotReachHere();
  }

  return message != nullptr ? message->as_C_string() : nullptr;
}

static void add_resolution_error(JavaThread* current, const constantPoolHandle& this_cp, int which,
                                 constantTag tag, oop pending_exception) {

  ResourceMark rm(current);
  Symbol* error = pending_exception->klass()->name();
  oop cause = java_lang_Throwable::cause(pending_exception);

  // Also dig out the exception cause, if present.
  Symbol* cause_sym = nullptr;
  const char* cause_msg = nullptr;
  if (cause != nullptr && cause != pending_exception) {
    cause_sym = cause->klass()->name();
    cause_msg = java_lang_Throwable::message_as_utf8(cause);
  }

  const char* message = exception_message(this_cp, which, tag, pending_exception);
  SystemDictionary::add_resolution_error(this_cp, which, error, message, cause_sym, cause_msg);
}


void ConstantPool::throw_resolution_error(const constantPoolHandle& this_cp, int which, TRAPS) {
  ResourceMark rm(THREAD);
  const char* message = nullptr;
  Symbol* cause = nullptr;
  const char* cause_msg = nullptr;
  Symbol* error = SystemDictionary::find_resolution_error(this_cp, which, &message, &cause, &cause_msg);
  assert(error != nullptr, "checking");

  CLEAR_PENDING_EXCEPTION;
  if (message != nullptr) {
    if (cause != nullptr) {
      Handle h_cause = Exceptions::new_exception(THREAD, cause, cause_msg);
      THROW_MSG_CAUSE(error, message, h_cause);
    } else {
      THROW_MSG(error, message);
    }
  } else {
    if (cause != nullptr) {
      Handle h_cause = Exceptions::new_exception(THREAD, cause, cause_msg);
      THROW_CAUSE(error, h_cause);
    } else {
      THROW(error);
    }
  }
}

// If resolution for Class, Dynamic constant, MethodHandle or MethodType fails, save the
// exception in the resolution error table, so that the same exception is thrown again.
void ConstantPool::save_and_throw_exception(const constantPoolHandle& this_cp, int cp_index,
                                            constantTag tag, TRAPS) {

  int error_tag = tag.error_value();

  if (!PENDING_EXCEPTION->
    is_a(vmClasses::LinkageError_klass())) {
    // Just throw the exception and don't prevent these classes from
    // being loaded due to virtual machine errors like StackOverflow
    // and OutOfMemoryError, etc, or if the thread was hit by stop()
    // Needs clarification to section 5.4.3 of the VM spec (see 6308271)
  } else if (this_cp->tag_at(cp_index).value() != error_tag) {
    add_resolution_error(THREAD, this_cp, cp_index, tag, PENDING_EXCEPTION);
    // CAS in the tag.  If a thread beat us to registering this error that's fine.
    // If another thread resolved the reference, this is a race condition. This
    // thread may have had a security manager or something temporary.
    // This doesn't deterministically get an error.   So why do we save this?
    // We save this because jvmti can add classes to the bootclass path after
    // this error, so it needs to get the same error if the error is first.
    jbyte old_tag = Atomic::cmpxchg((jbyte*)this_cp->tag_addr_at(cp_index),
                                    (jbyte)tag.value(),
                                    (jbyte)error_tag);
    if (old_tag != error_tag && old_tag != tag.value()) {
      // MethodHandles and MethodType doesn't change to resolved version.
      assert(this_cp->tag_at(cp_index).is_klass(), "Wrong tag value");
      // Forget the exception and use the resolved class.
      CLEAR_PENDING_EXCEPTION;
    }
  } else {
    // some other thread put this in error state
    throw_resolution_error(this_cp, cp_index, CHECK);
  }
}

constantTag ConstantPool::constant_tag_at(int cp_index) {
  constantTag tag = tag_at(cp_index);
  if (tag.is_dynamic_constant()) {
    BasicType bt = basic_type_for_constant_at(cp_index);
    return constantTag(constantTag::type2tag(bt));
  }
  return tag;
}

BasicType ConstantPool::basic_type_for_constant_at(int cp_index) {
  constantTag tag = tag_at(cp_index);
  if (tag.is_dynamic_constant_or_error()) {
    // have to look at the signature for this one
    BootstrapReference condy(this, cp_index);
    Symbol* constant_type = condy.signature(this);
    return Signature::basic_type(constant_type);
  }
  return tag.basic_type();
}

// Called to resolve constants in the constant pool and return an oop.
// Some constant pool entries cache their resolved oop. This is also
// called to create oops from constants to use in arguments for invokedynamic
oop ConstantPool::resolve_constant_at_impl(const constantPoolHandle& this_cp,
                                           int cp_index, int cache_index,
                                           bool* status_return, TRAPS) {
  oop result_oop = nullptr;

  if (cache_index == _possible_index_sentinel) {
    // It is possible that this constant is one which is cached in the objects.
    // We'll do a linear search.  This should be OK because this usage is rare.
    // FIXME: If bootstrap specifiers stress this code, consider putting in
    // a reverse index.  Binary search over a short array should do it.
    assert(cp_index > 0, "valid constant pool index");
    cache_index = this_cp->cp_to_object_index(cp_index);
  }
  assert(cache_index == _no_index_sentinel || cache_index >= 0, "");
  assert(cp_index == _no_index_sentinel || cp_index >= 0, "");

  if (cache_index >= 0) {
    result_oop = this_cp->resolved_reference_at(cache_index);
    if (result_oop != nullptr) {
      if (result_oop == Universe::the_null_sentinel()) {
        DEBUG_ONLY(int temp_index = (cp_index >= 0 ? cp_index : this_cp->object_to_cp_index(cache_index)));
        assert(this_cp->tag_at(temp_index).is_dynamic_constant(), "only condy uses the null sentinel");
        result_oop = nullptr;
      }
      if (status_return != nullptr)  (*status_return) = true;
      return result_oop;
      // That was easy...
    }
    cp_index = this_cp->object_to_cp_index(cache_index);
  }

  jvalue prim_value;  // temp used only in a few cases below

  constantTag tag = this_cp->tag_at(cp_index);

  if (status_return != nullptr) {
    // don't trigger resolution if the constant might need it
    switch (tag.value()) {
    case JVM_CONSTANT_Class:
    {
      KlassReference kref(this_cp, cp_index);
      if (this_cp->resolved_klasses()->at(kref.resolved_klass_index()) == nullptr) {
        //FIXME: (8349405) this path should not be taken
        (*status_return) = false;
        return nullptr;
      }
      // the klass is waiting in the CP; go get it
      break;
    }
    case JVM_CONSTANT_String:
    case JVM_CONSTANT_Integer:
    case JVM_CONSTANT_Float:
    case JVM_CONSTANT_Long:
    case JVM_CONSTANT_Double:
      // these guys trigger OOM at worst
      break;
    default:
      (*status_return) = false;
      return nullptr;
    }
    // from now on there is either success or an OOME
    (*status_return) = true;
  }

  switch (tag.value()) {

  case JVM_CONSTANT_UnresolvedClass:
  case JVM_CONSTANT_Class:
    {
      assert(cache_index == _no_index_sentinel, "should not have been set");
      Klass* resolved = klass_at_impl(this_cp, cp_index, CHECK_NULL);
      // ldc wants the java mirror.
      result_oop = resolved->java_mirror();
      break;
    }

  case JVM_CONSTANT_Dynamic:
    { PerfTraceTimedEvent timer(ClassLoader::perf_resolve_invokedynamic_time(),
                                ClassLoader::perf_resolve_invokedynamic_count());

      // Resolve the Dynamically-Computed constant to invoke the BSM in order to obtain the resulting oop.
      BootstrapInfo bootstrap_specifier(this_cp, cp_index);

      // The initial step in resolving an unresolved symbolic reference to a
      // dynamically-computed constant is to resolve the symbolic reference to a
      // method handle which will be the bootstrap method for the dynamically-computed
      // constant. If resolution of the java.lang.invoke.MethodHandle for the bootstrap
      // method fails, then a MethodHandleInError is stored at the corresponding
      // bootstrap method's CP index for the CONSTANT_MethodHandle_info. No need to
      // set a DynamicConstantInError here since any subsequent use of this
      // bootstrap method will encounter the resolution of MethodHandleInError.
      // Both the first, (resolution of the BSM and its static arguments), and the second tasks,
      // (invocation of the BSM), of JVMS Section 5.4.3.6 occur within invoke_bootstrap_method()
      // for the bootstrap_specifier created above.
      SystemDictionary::invoke_bootstrap_method(bootstrap_specifier, THREAD);
      Exceptions::wrap_dynamic_exception(/* is_indy */ false, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        // Resolution failure of the dynamically-computed constant, save_and_throw_exception
        // will check for a LinkageError and store a DynamicConstantInError.
        save_and_throw_exception(this_cp, cp_index, tag, CHECK_NULL);
      }
      result_oop = bootstrap_specifier.resolved_value()();
      BasicType type = Signature::basic_type(bootstrap_specifier.signature());
      if (!is_reference_type(type)) {
        // Make sure the primitive value is properly boxed.
        // This is a JDK responsibility.
        const char* fail = nullptr;
        if (result_oop == nullptr) {
          fail = "null result instead of box";
        } else if (!is_java_primitive(type)) {
          // FIXME: support value types via unboxing
          fail = "can only handle references and primitives";
        } else if (!java_lang_boxing_object::is_instance(result_oop, type)) {
          fail = "primitive is not properly boxed";
        }
        if (fail != nullptr) {
          // Since this exception is not a LinkageError, throw exception
          // but do not save a DynamicInError resolution result.
          // See section 5.4.3 of the VM spec.
          THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), fail);
        }
      }

      LogTarget(Debug, methodhandles, condy) lt_condy;
      if (lt_condy.is_enabled()) {
        LogStream ls(lt_condy);
        bootstrap_specifier.print_msg_on(&ls, "resolve_constant_at_impl");
      }
      break;
    }

  case JVM_CONSTANT_String:
    assert(cache_index != _no_index_sentinel, "should have been set");
    result_oop = string_at_impl(this_cp, cp_index, cache_index, CHECK_NULL);
    break;

  case JVM_CONSTANT_MethodHandle:
    { PerfTraceTimedEvent timer(ClassLoader::perf_resolve_method_handle_time(),
                                ClassLoader::perf_resolve_method_handle_count());

      MethodHandleReference mhref(this_cp, cp_index);
      int ref_kind       = mhref.ref_kind();
      int callee_index   = mhref.klass_index();
      Symbol*  name      = mhref.name(this_cp);
      Symbol*  signature = mhref.signature(this_cp);
      constantTag m_tag  = this_cp->tag_at(mhref.ref_index());
      { ResourceMark rm(THREAD);
        log_debug(class, resolve)("resolve JVM_CONSTANT_MethodHandle:%d [%d/%d/%d] %s.%s",
                              ref_kind, cp_index, mhref.ref_index(),
                              callee_index, name->as_C_string(), signature->as_C_string());
      }

      Klass* callee = klass_at_impl(this_cp, callee_index, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        save_and_throw_exception(this_cp, cp_index, tag, CHECK_NULL);
      }

      // Check constant pool method consistency
      if ((callee->is_interface() && m_tag.is_method()) ||
          (!callee->is_interface() && m_tag.is_interface_method())) {
        ResourceMark rm(THREAD);
        stringStream ss;
        ss.print("Inconsistent constant pool data in classfile for class %s. "
                 "Method '", callee->name()->as_C_string());
        signature->print_as_signature_external_return_type(&ss);
        ss.print(" %s(", name->as_C_string());
        signature->print_as_signature_external_parameters(&ss);
        ss.print(")' at index %d is %s and should be %s",
                 cp_index,
                 callee->is_interface() ? "CONSTANT_MethodRef" : "CONSTANT_InterfaceMethodRef",
                 callee->is_interface() ? "CONSTANT_InterfaceMethodRef" : "CONSTANT_MethodRef");
        // Names are all known to be < 64k so we know this formatted message is not excessively large.
        Exceptions::fthrow(THREAD_AND_LOCATION, vmSymbols::java_lang_IncompatibleClassChangeError(), "%s", ss.as_string());
        save_and_throw_exception(this_cp, cp_index, tag, CHECK_NULL);
      }

      Klass* klass = this_cp->pool_holder();
      HandleMark hm(THREAD);
      Handle value = SystemDictionary::link_method_handle_constant(klass, ref_kind,
                                                                   callee, name, signature,
                                                                   THREAD);
      if (HAS_PENDING_EXCEPTION) {
        save_and_throw_exception(this_cp, cp_index, tag, CHECK_NULL);
      }
      result_oop = value();
      break;
    }

  case JVM_CONSTANT_MethodType:
    { PerfTraceTimedEvent timer(ClassLoader::perf_resolve_method_type_time(),
                                ClassLoader::perf_resolve_method_type_count());

      MethodTypeReference mtref(this_cp, cp_index);
      Symbol*  signature = mtref.signature(this_cp);
      { ResourceMark rm(THREAD);
        log_debug(class, resolve)("resolve JVM_CONSTANT_MethodType [%d/%d] %s",
                              cp_index, mtref.signature_index(),
                              signature->as_C_string());
      }
      Klass* klass = this_cp->pool_holder();
      HandleMark hm(THREAD);
      Handle value = SystemDictionary::find_method_handle_type(signature, klass, THREAD);
      result_oop = value();
      if (HAS_PENDING_EXCEPTION) {
        save_and_throw_exception(this_cp, cp_index, tag, CHECK_NULL);
      }
      break;
    }

  case JVM_CONSTANT_Integer:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.i = this_cp->int_at(cp_index);
    result_oop = java_lang_boxing_object::create(T_INT, &prim_value, CHECK_NULL);
    break;

  case JVM_CONSTANT_Float:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.f = this_cp->float_at(cp_index);
    result_oop = java_lang_boxing_object::create(T_FLOAT, &prim_value, CHECK_NULL);
    break;

  case JVM_CONSTANT_Long:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.j = this_cp->long_at(cp_index);
    result_oop = java_lang_boxing_object::create(T_LONG, &prim_value, CHECK_NULL);
    break;

  case JVM_CONSTANT_Double:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.d = this_cp->double_at(cp_index);
    result_oop = java_lang_boxing_object::create(T_DOUBLE, &prim_value, CHECK_NULL);
    break;

  case JVM_CONSTANT_UnresolvedClassInError:
  case JVM_CONSTANT_DynamicInError:
  case JVM_CONSTANT_MethodHandleInError:
  case JVM_CONSTANT_MethodTypeInError:
    throw_resolution_error(this_cp, cp_index, CHECK_NULL);
    break;

  default:
    fatal("unexpected constant tag at CP %p[%d/%d] = %d", this_cp(), cp_index, cache_index, tag.value());
    break;
  }

  if (cache_index >= 0) {
    // Benign race condition:  resolved_references may already be filled in.
    // The important thing here is that all threads pick up the same result.
    // It doesn't matter which racing thread wins, as long as only one
    // result is used by all threads, and all future queries.
    oop new_result = (result_oop == nullptr ? Universe::the_null_sentinel() : result_oop);
    oop old_result = this_cp->set_resolved_reference_at(cache_index, new_result);
    if (old_result == nullptr) {
      return result_oop;  // was installed
    } else {
      // Return the winning thread's result.  This can be different than
      // the result here for MethodHandles.
      if (old_result == Universe::the_null_sentinel())
        old_result = nullptr;
      return old_result;
    }
  } else {
    assert(result_oop != Universe::the_null_sentinel(), "");
    return result_oop;
  }
}

oop ConstantPool::uncached_string_at(int cp_index, TRAPS) {
  Symbol* sym = unresolved_string_at(cp_index);
  oop str = StringTable::intern(sym, CHECK_(nullptr));
  assert(java_lang_String::is_instance(str), "must be string");
  return str;
}

void ConstantPool::copy_bootstrap_arguments_at_impl(const constantPoolHandle& this_cp,
                                                    int bsme_index,
                                                    int start_arg, int end_arg,
                                                    objArrayHandle info, int pos,
                                                    bool must_resolve, Handle if_not_available,
                                                    TRAPS) {
  int limit = pos + end_arg - start_arg;
  // check explicitly (do not assert) that bsms index is in range
  BSMAttributeEntry* bsme = nullptr;
  if (0 <= bsme_index &&
      bsme_index < this_cp->bsm_attribute_count()) {
    bsme = this_cp->bsm_attribute_entry(bsme_index);
  }
  // also check tag at cp_index, start..end in range,
  // info array non-null, pos..limit in [0..info.length]
  if (bsme == nullptr ||
      (0 > start_arg || start_arg > end_arg) ||
      (end_arg > bsme->argument_count()) ||
      (0 > pos       || pos > limit)         ||
      (info.is_null() || limit > info->length())) {
    // An index or something else went wrong; throw an error.
    // Since this is an internal API, we don't expect this,
    // so we don't bother to craft a nice message.
    THROW_MSG(vmSymbols::java_lang_LinkageError(), "bad BSM argument access");
  }
  // now we can loop safely
  int info_i = pos;
  for (int i = start_arg; i < end_arg; i++) {
    int arg_index = bsme->argument_index(i);
    oop arg_oop;
    if (must_resolve) {
      arg_oop = this_cp->resolve_possibly_cached_constant_at(arg_index, CHECK);
    } else {
      bool found_it = false;
      arg_oop = this_cp->find_cached_constant_at(arg_index, found_it, CHECK);
      if (!found_it)  arg_oop = if_not_available();
    }
    info->obj_at_put(info_i++, arg_oop);
  }
}

oop ConstantPool::string_at_impl(const constantPoolHandle& this_cp, int cp_index, int obj_index, TRAPS) {
  // If the string has already been interned, this entry will be non-null
  oop str = this_cp->resolved_reference_at(obj_index);
  assert(str != Universe::the_null_sentinel(), "");
  if (str != nullptr) return str;
  Symbol* sym = this_cp->unresolved_string_at(cp_index);
  str = StringTable::intern(sym, CHECK_(nullptr));
  this_cp->string_at_put(obj_index, str);
  assert(java_lang_String::is_instance(str), "must be string");
  return str;
}


bool ConstantPool::klass_name_at_matches(const InstanceKlass* k, int cp_index) {
  // Names are interned, so we can compare Symbol*s directly
  Symbol* cp_name = klass_name_at(cp_index);
  return (cp_name == k->name());
}


// Iterate over symbols and decrement ones which are Symbol*s
// This is done during GC.
// Only decrement the UTF8 symbols. Strings point to
// these symbols but didn't increment the reference count.
void ConstantPool::unreference_symbols() {
  for (int index = 1; index < length(); index++) { // Index 0 is unused
    constantTag tag = tag_at(index);
    if (tag.is_symbol()) {
      symbol_at(index)->decrement_refcount();
    }
  }
}


// Compare this constant pool's entry at index1 to the constant pool
// cp2's entry at index2.
bool ConstantPool::compare_entry_to(int index1, const constantPoolHandle& cp2,
       int index2) {

  // The error tags are equivalent to non-error tags when comparing
  jbyte t1 = tag_at(index1).non_error_value();
  jbyte t2 = cp2->tag_at(index2).non_error_value();

  // Some classes are pre-resolved (like Throwable) which may lead to
  // consider it as a different entry. We then revert them back temporarily
  // to ensure proper comparison.
  if (t1 == JVM_CONSTANT_Class) {
    t1 = JVM_CONSTANT_UnresolvedClass;
  }
  if (t2 == JVM_CONSTANT_Class) {
    t2 = JVM_CONSTANT_UnresolvedClass;
  }

  if (t1 != t2) {
    // Not the same entry type so there is nothing else to check. Note
    // that this style of checking will consider resolved/unresolved
    // class pairs as different.
    // From the ConstantPool* API point of view, this is correct
    // behavior. See VM_RedefineClasses::merge_constant_pools() to see how this
    // plays out in the context of ConstantPool* merging.
    return false;
  }

  switch (t1) {
  case JVM_CONSTANT_ClassIndex:
  {
    int recur1 = klass_index_at(index1);
    int recur2 = cp2->klass_index_at(index2);
    if (compare_entry_to(recur1, cp2, recur2)) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Double:
  {
    jdouble d1 = double_at(index1);
    jdouble d2 = cp2->double_at(index2);
    if (d1 == d2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Fieldref:
  case JVM_CONSTANT_InterfaceMethodref:
  case JVM_CONSTANT_Methodref:
  {
    FMReference ref1(this, index1);
    FMReference ref2(cp2,  index2);
    if (compare_entry_to(ref1.klass_index(), cp2, ref2.klass_index()) &&
        compare_entry_to(ref1.nt_index(),    cp2, ref2.nt_index())) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Float:
  {
    jfloat f1 = float_at(index1);
    jfloat f2 = cp2->float_at(index2);
    if (f1 == f2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Integer:
  {
    jint i1 = int_at(index1);
    jint i2 = cp2->int_at(index2);
    if (i1 == i2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Long:
  {
    jlong l1 = long_at(index1);
    jlong l2 = cp2->long_at(index2);
    if (l1 == l2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_NameAndType:
  {
    NTReference nt1(this, index1);
    NTReference nt2(cp2,  index2);
    if (compare_entry_to(nt1.name_index(),      cp2, nt2.name_index()) &&
        compare_entry_to(nt1.signature_index(), cp2, nt2.signature_index())) {
      return true;
    }
  } break;

  case JVM_CONSTANT_StringIndex:
  {
    int recur1 = string_index_at(index1);
    int recur2 = cp2->string_index_at(index2);
    if (compare_entry_to(recur1, cp2, recur2)) {
      return true;
    }
  } break;

  case JVM_CONSTANT_UnresolvedClass:
  {
    Symbol* k1 = klass_name_at(index1);
    Symbol* k2 = cp2->klass_name_at(index2);
    if (k1 == k2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_MethodType:
  {
    MethodTypeReference ref1(this, index1);
    MethodTypeReference ref2(cp2,  index2);
    int k1 = ref1.signature_index();
    int k2 = ref2.signature_index();
    if (compare_entry_to(k1, cp2, k2)) {
      return true;
    }
  } break;

  case JVM_CONSTANT_MethodHandle:
  {
    MethodHandleReference ref1(this, index1);
    MethodHandleReference ref2(cp2,  index2);
    if (ref1.ref_kind() == ref2.ref_kind() &&
        compare_entry_to(ref1.ref_index(), cp2, ref2.ref_index())) {
      return true;
    }
  } break;

  case JVM_CONSTANT_InvokeDynamic:
  case JVM_CONSTANT_Dynamic:
  {
    BootstrapReference ref1(this, index1);
    BootstrapReference ref2(cp2,  index2);
    if (compare_entry_to(ref1.nt_index(),  cp2, ref2.nt_index()) &&
        compare_bsme_to(ref1.bsme_index(), cp2, ref2.bsme_index())) {
      return true;
    }
  } break;

  case JVM_CONSTANT_String:
  {
    Symbol* s1 = unresolved_string_at(index1);
    Symbol* s2 = cp2->unresolved_string_at(index2);
    if (s1 == s2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Utf8:
  {
    Symbol* s1 = symbol_at(index1);
    Symbol* s2 = cp2->symbol_at(index2);
    if (s1 == s2) {
      return true;
    }
  } break;

  // Invalid is used as the tag for the second constant pool entry
  // occupied by JVM_CONSTANT_Double or JVM_CONSTANT_Long. It should
  // not be seen by itself.
  case JVM_CONSTANT_Invalid: // fall through

  default:
    ShouldNotReachHere();
    break;
  }

  return false;
} // end compare_entry_to()


// Resize the BSM attribute arrays with delta_len and delta_size.
// Used in RedefineClasses for CP merge.
void ConstantPool::resize_bsm_data(int delta_len, int delta_size, TRAPS) {
  Array<u4>* old_offs = bsm_attribute_offsets();
  Array<u2>* old_data = bsm_attribute_entries();
  const bool have_old = (bsm_attribute_count() != 0);

  int old_offs_len  = !have_old ? 0 : old_offs->length();
  int new_offs_len  = old_offs_len + delta_len;
  int min_offs_len  = (delta_len > 0) ? old_offs_len : new_offs_len;

  int old_data_len = !have_old ? 0 : old_data->length();
  int new_data_len = old_data_len + delta_size;
  int min_data_len = (delta_size > 0) ? old_data_len : new_data_len;

  ClassLoaderData* loader_data = pool_holder()->class_loader_data();
  Array<u4>* new_offs = MetadataFactory::new_array<u4>(loader_data, new_offs_len, CHECK);
  Array<u2>* new_data = MetadataFactory::new_array<u2>(loader_data, new_data_len, CHECK);

  // Copy the old array data.  We do not need to change any offsets.
  if (have_old) {
    guarantee(min_offs_len > 0 && min_data_len > 0,
              "must have something to copy %d/%d", min_offs_len, min_data_len);
    Copy::conjoint_memory_atomic(old_offs->adr_at(0),
                                 new_offs->adr_at(0),
                                 min_offs_len * sizeof(u4));
    Copy::conjoint_memory_atomic(old_data->adr_at(0),
                                 new_data->adr_at(0),
                                 min_data_len * sizeof(u2));
  }
  // Explicitly deallocate old bsm_data array.
  if (bsm_attribute_offsets() != nullptr) { // the safety check
    MetadataFactory::free_array<u4>(loader_data, bsm_attribute_offsets());
  }
  if (bsm_attribute_entries() != nullptr) { // the safety check
    MetadataFactory::free_array<u2>(loader_data, bsm_attribute_entries());
  }
  set_bsm_attribute_offsets(new_offs);
  set_bsm_attribute_entries(new_data);
} // end resize_bsm_data()


// Extend the BSM attribute arrays with the length and size of the ext_cp data.
// Used in RedefineClasses for CP merge.
void ConstantPool::extend_bsm_data(const constantPoolHandle& ext_cp, TRAPS) {
  int delta_len = ext_cp->bsm_attribute_count();
  if (delta_len == 0) {
    return; // nothing to do
  }
  int delta_size = ext_cp->bsm_attribute_entries()->length();

  assert(delta_len > 0 && delta_size > 0, "extended arrays must be bigger");

  // Note:  resize_bsm_data can handle bsm_attribute_entries()==nullptr
  resize_bsm_data(delta_len, delta_size, CHECK);
} // end extend_bsm_data()


// Shrink the BSM attribute arrays to a smaller number of entries.
// Used in RedefineClasses for CP merge.
void ConstantPool::shrink_bsm_data(int new_len, TRAPS) {
  int old_len = bsm_attribute_count();
  if (new_len == old_len) {
    return; // nothing to do
  }
  assert(new_len < old_len, "shrunken bsm_data array must be smaller");

  int delta_len    = new_len      - old_len;

  int old_data_len = bsm_attribute_entries()->length();     //length
  int new_data_len = 0;
  if (new_len > 0) {
    // This is tricky: we cannot trust any offset or data at new_len or beyond.
    // So, work forward from the last valid BSM entry.
    int last_bsme_offset = bsm_attribute_offsets()->at(new_len - 1);
    int last_bsme_header = sizeof(BSMAttributeEntry) / sizeof(u2);
    assert(last_bsme_header == 2, "bsm+argc");
    new_data_len = (last_bsme_offset + last_bsme_header +
                    bsm_attribute_entry(new_len - 1)->argument_count());
  }

  int delta_size   = new_data_len - old_data_len;

  resize_bsm_data(delta_len, delta_size, CHECK);
} // end shrink_bsm_data()


// Append the BSM attribute entries from one CP to the end of another.
void ConstantPool::copy_bsm_data(const constantPoolHandle& from_cp,
                                 const constantPoolHandle& to_cp,
                                 TRAPS) {
  // Append my offsets and data to the target's offset and data arrays.
  Array<u4>* from_offs = from_cp->bsm_attribute_offsets();
  Array<u2>* from_data = from_cp->bsm_attribute_entries();
  Array<u4>* to_offs   = to_cp->bsm_attribute_offsets();
  Array<u2>* to_data   = to_cp->bsm_attribute_entries();
  if (from_offs == nullptr || from_offs->length() == 0) {
    return;  // nothing to copy
  }

  const bool have_old = (to_offs != nullptr && to_offs->length() != 0);
  const int old_offs_len = !have_old ? 0 : to_offs->length();
  const int add_offs_len = from_offs->length();
  const int new_offs_len = old_offs_len + add_offs_len;
  const int old_data_len = !have_old ? 0 : to_data->length();
  const int add_data_len = from_data->length();
  const int new_data_len = old_data_len + add_data_len;

  // Note: even if old_len is zero, we can't just reuse from_cp's
  // arrays, because of deallocation issues.  Always make fresh data.
  ClassLoaderData* loader_data = to_cp->pool_holder()->class_loader_data();

  // Use the metaspace for the destination constant pool
  Array<u4>* new_offs = MetadataFactory::new_array<u4>(loader_data, new_offs_len, CHECK);
  Array<u2>* new_data = MetadataFactory::new_array<u2>(loader_data, new_data_len, CHECK);

  // first, recopy pre-existing parts of both dest arrays:
  int offs_fillp = 0, data_fillp = 0, offs_copied, data_copied;
  if (have_old) {
    Copy::conjoint_memory_atomic(to_offs->adr_at(0),
                                 new_offs->adr_at(offs_fillp),
                                 (offs_copied = old_offs_len) * sizeof(u4));
    Copy::conjoint_memory_atomic(to_data->adr_at(0),
                                 new_data->adr_at(data_fillp),
                                 (data_copied = old_data_len) * sizeof(u2));
    offs_fillp += offs_copied;
    data_fillp += data_copied;
  }

  // then, append new parts of both source arrays:
  Copy::conjoint_memory_atomic(from_offs->adr_at(0),
                               new_offs->adr_at(offs_fillp),
                               (offs_copied = add_offs_len) * sizeof(u4));
  Copy::conjoint_memory_atomic(from_data->adr_at(0),
                               new_data->adr_at(old_data_len),
                               (data_copied = add_data_len) * sizeof(u2));
  offs_fillp += offs_copied;
  data_fillp += data_copied;
  assert(offs_fillp == new_offs->length(), "");
  assert(data_fillp == new_data->length(), "");

  // Adjust indexes in the first part of the copied bsm_data array.
  for (int j = old_offs_len; j < new_offs_len; j++) {
    u4 old_offset = new_offs->at(j);
    u4 new_offset = old_offset + old_data_len;
    // every new entry is preceded by old_data_len extra u2's
    new_offs->at_put(j, new_offset);
  }

  // replace target bsm_data array with combined array
  to_cp->set_bsm_attribute_offsets(new_offs);
  to_cp->set_bsm_attribute_entries(new_data);
} // end copy_bsm_data()


// Copy this constant pool's entries at start_i to end_i (inclusive)
// to the constant pool to_cp's entries starting at to_i. A total of
// (end_i - start_i) + 1 entries are copied.
void ConstantPool::copy_cp_to_impl(const constantPoolHandle& from_cp, int start_i, int end_i,
       const constantPoolHandle& to_cp, int to_i, TRAPS) {


  int dest_cpi = to_i;  // leave original alone for debug purposes

  for (int src_cpi = start_i; src_cpi <= end_i; /* see loop bottom */ ) {
    copy_entry_to(from_cp, src_cpi, to_cp, dest_cpi);

    switch (from_cp->tag_at(src_cpi).value()) {
    case JVM_CONSTANT_Double:
    case JVM_CONSTANT_Long:
      // double and long take two constant pool entries
      src_cpi += 2;
      dest_cpi += 2;
      break;

    default:
      // all others take one constant pool entry
      src_cpi++;
      dest_cpi++;
      break;
    }
  }
  copy_bsm_data(from_cp, to_cp, CHECK);

} // end copy_cp_to_impl()


// Copy this constant pool's entry at from_i to the constant pool
// to_cp's entry at to_i.
void ConstantPool::copy_entry_to(const constantPoolHandle& from_cp, int from_i,
                                        const constantPoolHandle& to_cp, int to_i) {

  int tag = from_cp->tag_at(from_i).value();
  switch (tag) {
  case JVM_CONSTANT_ClassIndex:
  {
    jint ki = from_cp->klass_index_at(from_i);
    to_cp->klass_index_at_put(to_i, ki);
  } break;

  case JVM_CONSTANT_Double:
  {
    jdouble d = from_cp->double_at(from_i);
    to_cp->double_at_put(to_i, d);
    // double takes two constant pool entries so init second entry's tag
    to_cp->tag_at_put(to_i + 1, JVM_CONSTANT_Invalid);
  } break;

  case JVM_CONSTANT_Fieldref:
  {
    FMReference ref(from_cp, from_i);
    to_cp->field_at_put(to_i, ref.klass_index(), ref.nt_index());
  } break;

  case JVM_CONSTANT_Float:
  {
    jfloat f = from_cp->float_at(from_i);
    to_cp->float_at_put(to_i, f);
  } break;

  case JVM_CONSTANT_Integer:
  {
    jint i = from_cp->int_at(from_i);
    to_cp->int_at_put(to_i, i);
  } break;

  case JVM_CONSTANT_InterfaceMethodref:
  {
    FMReference ref(from_cp, from_i);
    to_cp->interface_method_at_put(to_i, ref.klass_index(), ref.nt_index());
  } break;

  case JVM_CONSTANT_Long:
  {
    jlong l = from_cp->long_at(from_i);
    to_cp->long_at_put(to_i, l);
    // long takes two constant pool entries so init second entry's tag
    to_cp->tag_at_put(to_i + 1, JVM_CONSTANT_Invalid);
  } break;

  case JVM_CONSTANT_Methodref:
  {
    FMReference ref(from_cp, from_i);
    to_cp->method_at_put(to_i, ref.klass_index(), ref.nt_index());
  } break;

  case JVM_CONSTANT_NameAndType:
  {
    NTReference ref(from_cp, from_i);
    to_cp->name_and_type_at_put(to_i, ref.name_index(), ref.signature_index());
  } break;

  case JVM_CONSTANT_StringIndex:
  {
    jint si = from_cp->string_index_at(from_i);
    to_cp->string_index_at_put(to_i, si);
  } break;

  case JVM_CONSTANT_Class:
  case JVM_CONSTANT_UnresolvedClass:
  case JVM_CONSTANT_UnresolvedClassInError:
  {
    // Revert to JVM_CONSTANT_ClassIndex
    KlassReference kref(from_cp, from_i);
    to_cp->klass_index_at_put(to_i, kref.name_index());
  } break;

  case JVM_CONSTANT_String:
  {
    Symbol* s = from_cp->unresolved_string_at(from_i);
    to_cp->unresolved_string_at_put(to_i, s);
  } break;

  case JVM_CONSTANT_Utf8:
  {
    Symbol* s = from_cp->symbol_at(from_i);
    // Need to increase refcount, the old one will be thrown away and deferenced
    s->increment_refcount();
    to_cp->symbol_at_put(to_i, s);
  } break;

  case JVM_CONSTANT_MethodType:
  case JVM_CONSTANT_MethodTypeInError:
  {
    MethodTypeReference ref(from_cp, from_i);
    to_cp->method_type_index_at_put(to_i, ref.signature_index());
  } break;

  case JVM_CONSTANT_MethodHandle:
  case JVM_CONSTANT_MethodHandleInError:
  {
    MethodHandleReference ref(from_cp, from_i);
    to_cp->method_handle_index_at_put(to_i, ref.ref_kind(), ref.ref_index());
  } break;

  case JVM_CONSTANT_Dynamic:
  case JVM_CONSTANT_DynamicInError:
  case JVM_CONSTANT_InvokeDynamic:
  {
    BootstrapReference ref(from_cp, from_i);
    int k1 = ref.bsme_index();
    k1 += to_cp->bsm_attribute_count();  // to_cp might already have BSMs
    if (ref.tag().is_invoke_dynamic()) {
      to_cp->invoke_dynamic_at_put(to_i, k1, ref.nt_index());
    } else {
      to_cp->dynamic_constant_at_put(to_i, k1, ref.nt_index());
    }
  } break;

  // Invalid is used as the tag for the second constant pool entry
  // occupied by JVM_CONSTANT_Double or JVM_CONSTANT_Long. It should
  // not be seen by itself.
  case JVM_CONSTANT_Invalid: // fall through

  default:
  {
    ShouldNotReachHere();
  } break;
  }
} // end copy_entry_to()

// Search constant pool search_cp for an entry that matches this
// constant pool's entry at pattern_i. Returns the index of a
// matching entry or zero (0) if there is no matching entry.
int ConstantPool::find_matching_entry(int pattern_i,
      const constantPoolHandle& search_cp) {

  // index zero (0) is not used
  for (int i = 1; i < search_cp->length(); i++) {
    bool found = compare_entry_to(pattern_i, search_cp, i);
    if (found) {
      return i;
    }
  }

  return 0;  // entry not found; return unused index zero (0)
} // end find_matching_entry()


// Compare this constant pool's BSM attribute entry at idx1 to the constant pool
// cp2's BSM attribute entry at idx2.
bool ConstantPool::compare_bsme_to(int idx1, const constantPoolHandle& cp2, int idx2) {
  BSMAttributeEntry* e1 = bsm_attribute_entry(idx1);
  BSMAttributeEntry* e2 = cp2->bsm_attribute_entry(idx2);
  int k1 = e1->bootstrap_method_index();
  int k2 = e2->bootstrap_method_index();
  bool match = compare_entry_to(k1, cp2, k2);

  if (!match) {
    return false;
  }
  int argc = e1->argument_count();
  if (argc == e2->argument_count()) {
    for (int j = 0; j < argc; j++) {
      k1 = e1->argument_index(j);
      k2 = e2->argument_index(j);
      match = compare_entry_to(k1, cp2, k2);
      if (!match) {
        return false;
      }
    }
    return true;           // got through loop; all elements equal
  }
  return false;
} // end compare_bsme_to()

// Search constant pool search_cp for a BSM attribute entry that matches
// this constant pool's BSM attribute entry at pattern_i index.
// Return the index of a entry, or (-1) if there was no match.
int ConstantPool::find_matching_bsme(int pattern_i,
                    const constantPoolHandle& search_cp, int search_len) {
  for (int i = 0; i < search_len; i++) {
    bool found = compare_bsme_to(pattern_i, search_cp, i);
    if (found) {
      return i;
    }
  }
  return -1;  // bootstrap specifier data not found; return unused index (-1)
} // end find_matching_bsme()


#ifndef PRODUCT

const char* ConstantPool::printable_name_at(int cp_index) {

  constantTag tag = tag_at(cp_index);

  if (tag.is_string()) {
    return string_at_noresolve(cp_index);
  } else if (tag.is_klass() || tag.is_unresolved_klass()) {
    return klass_name_at(cp_index)->as_C_string();
  } else if (tag.is_symbol()) {
    return symbol_at(cp_index)->as_C_string();
  }
  return "";
}

#endif // PRODUCT


// JVMTI GetConstantPool support

// For debugging of constant pool
const bool debug_cpool = false;

#define DBG(code) do { if (debug_cpool) { (code); } } while(0)

static void print_cpool_bytes(jint cnt, u1 *bytes) {
  const char* WARN_MSG = "Must not be such entry!";
  jint size = 0;
  u2   idx1, idx2;

  for (jint idx = 1; idx < cnt; idx++) {
    jint ent_size = 0;
    u1   tag  = *bytes++;
    size++;                       // count tag

    printf("const #%03d, tag: %02d ", idx, tag);
    switch(tag) {
      case JVM_CONSTANT_Invalid: {
        printf("Invalid");
        break;
      }
      case JVM_CONSTANT_Unicode: {
        printf("Unicode      %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_Utf8: {
        u2 len = Bytes::get_Java_u2(bytes);
        char str[128];
        if (len > 127) {
           len = 127;
        }
        strncpy(str, (char *) (bytes+2), len);
        str[len] = '\0';
        printf("Utf8          \"%s\"", str);
        ent_size = 2 + len;
        break;
      }
      case JVM_CONSTANT_Integer: {
        u4 val = Bytes::get_Java_u4(bytes);
        printf("int          %d", *(int *) &val);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Float: {
        u4 val = Bytes::get_Java_u4(bytes);
        printf("float        %5.3ff", *(float *) &val);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Long: {
        u8 val = Bytes::get_Java_u8(bytes);
        printf("long         " INT64_FORMAT, (int64_t) *(jlong *) &val);
        ent_size = 8;
        idx++; // Long takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Double: {
        u8 val = Bytes::get_Java_u8(bytes);
        printf("double       %5.3fd", *(jdouble *)&val);
        ent_size = 8;
        idx++; // Double takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Class: {
        idx1 = Bytes::get_Java_u2(bytes);
        printf("class        #%03d", idx1);
        ent_size = 2;
        break;
      }
      case JVM_CONSTANT_String: {
        idx1 = Bytes::get_Java_u2(bytes);
        printf("String       #%03d", idx1);
        ent_size = 2;
        break;
      }
      case JVM_CONSTANT_Fieldref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("Field        #%03d, #%03d", (int) idx1, (int) idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Methodref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("Method       #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_InterfaceMethodref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("InterfMethod #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("NameAndType  #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_ClassIndex: {
        printf("ClassIndex  %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_UnresolvedClass: {
        printf("UnresolvedClass: %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_UnresolvedClassInError: {
        printf("UnresolvedClassInErr: %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_StringIndex: {
        printf("StringIndex: %s", WARN_MSG);
        break;
      }
    }
    printf(";\n");
    bytes += ent_size;
    size  += ent_size;
  }
  printf("Cpool size: %d\n", size);
  fflush(nullptr);
  return;
} /* end print_cpool_bytes */


// Returns size of constant pool entry.
jint ConstantPool::cpool_entry_size(jint idx) {
  switch(tag_at(idx).value()) {
    case JVM_CONSTANT_Invalid:
    case JVM_CONSTANT_Unicode:
      return 1;

    case JVM_CONSTANT_Utf8:
      return 3 + symbol_at(idx)->utf8_length();

    case JVM_CONSTANT_Class:
    case JVM_CONSTANT_String:
    case JVM_CONSTANT_ClassIndex:
    case JVM_CONSTANT_UnresolvedClass:
    case JVM_CONSTANT_UnresolvedClassInError:
    case JVM_CONSTANT_StringIndex:
    case JVM_CONSTANT_MethodType:
    case JVM_CONSTANT_MethodTypeInError:
      return 3;

    case JVM_CONSTANT_MethodHandle:
    case JVM_CONSTANT_MethodHandleInError:
      return 4; //tag, ref_kind, ref_index

    case JVM_CONSTANT_Integer:
    case JVM_CONSTANT_Float:
    case JVM_CONSTANT_Fieldref:
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_InterfaceMethodref:
    case JVM_CONSTANT_NameAndType:
      return 5;

    case JVM_CONSTANT_Dynamic:
    case JVM_CONSTANT_DynamicInError:
    case JVM_CONSTANT_InvokeDynamic:
      // u1 tag, u2 bsm, u2 nt
      return 5;

    case JVM_CONSTANT_Long:
    case JVM_CONSTANT_Double:
      return 9;
  }
  assert(false, "cpool_entry_size: Invalid constant pool entry tag");
  return 1;
} /* end cpool_entry_size */


// SymbolHash is used to find a constant pool index from a string.
// This function fills in SymbolHashs, one for utf8s and one for
// class names, returns size of the cpool raw bytes.
jint ConstantPool::hash_entries_to(SymbolHash *symmap,
                                   SymbolHash *classmap) {
  jint size = 0;

  for (u2 idx = 1; idx < length(); idx++) {
    u2 tag = tag_at(idx).value();
    size += cpool_entry_size(idx);

    switch(tag) {
      case JVM_CONSTANT_Utf8: {
        Symbol* sym = symbol_at(idx);
        symmap->add_if_absent(sym, idx);
        DBG(printf("adding symbol entry %s = %d\n", sym->as_utf8(), idx));
        break;
      }
      case JVM_CONSTANT_Class:
      case JVM_CONSTANT_UnresolvedClass:
      case JVM_CONSTANT_UnresolvedClassInError: {
        Symbol* sym = klass_name_at(idx);
        classmap->add_if_absent(sym, idx);
        DBG(printf("adding class entry %s = %d\n", sym->as_utf8(), idx));
        break;
      }
      case JVM_CONSTANT_Long:
      case JVM_CONSTANT_Double: {
        idx++; // Both Long and Double take two cpool slots
        break;
      }
    }
  }
  return size;
} /* end hash_utf8_entries_to */


// Copy cpool bytes.
// Returns:
//    0, in case of OutOfMemoryError
//   -1, in case of internal error
//  > 0, count of the raw cpool bytes that have been copied
int ConstantPool::copy_cpool_bytes(int cpool_size,
                                   SymbolHash* tbl,
                                   unsigned char *bytes) {
  u2   idx1, idx2;
  jint size  = 0;
  jint cnt   = length();
  unsigned char *start_bytes = bytes;

  for (jint idx = 1; idx < cnt; idx++) {
    u1   tag      = tag_at(idx).value();
    jint ent_size = cpool_entry_size(idx);

    assert(size + ent_size <= cpool_size, "Size mismatch");

    *bytes = tag;
    DBG(printf("#%03hd tag=%03hd, ", (short)idx, (short)tag));
    switch(tag) {
      case JVM_CONSTANT_Invalid: {
        DBG(printf("JVM_CONSTANT_Invalid"));
        break;
      }
      case JVM_CONSTANT_Unicode: {
        assert(false, "Wrong constant pool tag: JVM_CONSTANT_Unicode");
        DBG(printf("JVM_CONSTANT_Unicode"));
        break;
      }
      case JVM_CONSTANT_Utf8: {
        Symbol* sym = symbol_at(idx);
        char*     str = sym->as_utf8();
        // Warning! It's crashing on x86 with len = sym->utf8_length()
        int       len = (int) strlen(str);
        Bytes::put_Java_u2((address) (bytes+1), (u2) len);
        for (int i = 0; i < len; i++) {
            bytes[3+i] = (u1) str[i];
        }
        DBG(printf("JVM_CONSTANT_Utf8: %s ", str));
        break;
      }
      case JVM_CONSTANT_Integer: {
        jint val = int_at(idx);
        Bytes::put_Java_u4((address) (bytes+1), *(u4*)&val);
        break;
      }
      case JVM_CONSTANT_Float: {
        jfloat val = float_at(idx);
        Bytes::put_Java_u4((address) (bytes+1), *(u4*)&val);
        break;
      }
      case JVM_CONSTANT_Long: {
        jlong val = long_at(idx);
        Bytes::put_Java_u8((address) (bytes+1), *(u8*)&val);
        idx++;             // Long takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Double: {
        jdouble val = double_at(idx);
        Bytes::put_Java_u8((address) (bytes+1), *(u8*)&val);
        idx++;             // Double takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Class:
      case JVM_CONSTANT_UnresolvedClass:
      case JVM_CONSTANT_UnresolvedClassInError: {
        *bytes = JVM_CONSTANT_Class;
        Symbol* sym = klass_name_at(idx);
        idx1 = tbl->symbol_to_value(sym);
        assert(idx1 != 0, "Have not found a hashtable entry");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_Class: idx=#%03hd, %s", idx1, sym->as_utf8()));
        break;
      }
      case JVM_CONSTANT_String: {
        *bytes = JVM_CONSTANT_String;
        Symbol* sym = unresolved_string_at(idx);
        idx1 = tbl->symbol_to_value(sym);
        assert(idx1 != 0, "Have not found a hashtable entry");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_String: idx=#%03hd, %s", idx1, sym->as_utf8()));
        break;
      }
      case JVM_CONSTANT_Fieldref:
      case JVM_CONSTANT_Methodref:
      case JVM_CONSTANT_InterfaceMethodref: {
        FMReference ref(this, idx);
        idx1 = ref.klass_index();
        idx2 = ref.nt_index();
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_Methodref: %hd %hd", idx1, idx2));
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        NTReference ref(this, idx);
        idx1 = ref.name_index();
        idx2 = ref.signature_index();
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_NameAndType: %hd %hd", idx1, idx2));
        break;
      }
      case JVM_CONSTANT_ClassIndex: {
        *bytes = JVM_CONSTANT_Class;
        idx1 = checked_cast<u2>(klass_index_at(idx));
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_ClassIndex: %hd", idx1));
        break;
      }
      case JVM_CONSTANT_StringIndex: {
        *bytes = JVM_CONSTANT_String;
        idx1 = checked_cast<u2>(string_index_at(idx));
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_StringIndex: %hd", idx1));
        break;
      }
      case JVM_CONSTANT_MethodHandle:
      case JVM_CONSTANT_MethodHandleInError: {
        *bytes = JVM_CONSTANT_MethodHandle;
        MethodHandleReference ref(this, idx);
        int kind = ref.ref_kind();
        idx1 = checked_cast<u2>(ref.ref_index());
        *(bytes+1) = (unsigned char) kind;
        Bytes::put_Java_u2((address) (bytes+2), idx1);
        DBG(printf("JVM_CONSTANT_MethodHandle: %d %hd", kind, idx1));
        break;
      }
      case JVM_CONSTANT_MethodType:
      case JVM_CONSTANT_MethodTypeInError: {
        *bytes = JVM_CONSTANT_MethodType;
        MethodTypeReference ref(this, idx);
        idx1 = checked_cast<u2>(ref.signature_index());
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_MethodType: %hd", idx1));
        break;
      }
      case JVM_CONSTANT_Dynamic:
      case JVM_CONSTANT_DynamicInError: {
        *bytes = JVM_CONSTANT_Dynamic;
        BootstrapReference ref(this, idx);
        idx1 = ref.bsme_index();
        idx2 = ref.nt_index();
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_Dynamic: %hd %hd", idx1, idx2));
        break;
      }
      case JVM_CONSTANT_InvokeDynamic: {
        *bytes = tag;
        BootstrapReference ref(this, idx);
        idx1 = ref.bsme_index();
        idx2 = ref.nt_index();
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_InvokeDynamic: %hd %hd", idx1, idx2));
        break;
      }
    }
    DBG(printf("\n"));
    bytes += ent_size;
    size  += ent_size;
  }
  assert(size == cpool_size, "Size mismatch");

  // Keep temporarily for debugging until it's stable.
  DBG(print_cpool_bytes(cnt, start_bytes));
  return (int)(bytes - start_bytes);
} /* end copy_cpool_bytes */

#undef DBG

bool ConstantPool::is_maybe_on_stack() const {
  // This method uses the similar logic as nmethod::is_maybe_on_stack()
  if (!Continuations::enabled()) {
    return false;
  }

  // If the condition below is true, it means that the nmethod was found to
  // be alive the previous completed marking cycle.
  return cache()->gc_epoch() >= CodeCache::previous_completed_gc_marking_cycle();
}

// For redefinition, if any methods found in loom stack chunks, the gc_epoch is
// recorded in their constant pool cache. The on_stack-ness of the constant pool controls whether
// memory for the method is reclaimed.
bool ConstantPool::on_stack() const {
  if ((_flags &_on_stack) != 0) {
    return true;
  }

  if (_cache == nullptr) {
    return false;
  }

  return is_maybe_on_stack();
}

void ConstantPool::set_on_stack(const bool value) {
  if (value) {
    // Only record if it's not already set.
    if (!on_stack()) {
      assert(!is_shared(), "should always be set for shared constant pools");
      _flags |= _on_stack;
      MetadataOnStackMark::record(this);
    }
  } else {
    // Clearing is done single-threadedly.
    if (!is_shared()) {
      _flags &= (u2)(~_on_stack);
    }
  }
}

// Printing

void ConstantPool::print_on(outputStream* st) const {
  assert(is_constantPool(), "must be constantPool");
  st->print_cr("%s", internal_name());
  if (flags() != 0) {
    st->print(" - flags: 0x%x", flags());
    if (has_preresolution()) st->print(" has_preresolution");
    if (on_stack()) st->print(" on_stack");
    st->cr();
  }
  if (pool_holder() != nullptr) {
    st->print_cr(" - holder: " PTR_FORMAT, p2i(pool_holder()));
  }
  st->print_cr(" - cache: " PTR_FORMAT, p2i(cache()));
  st->print_cr(" - resolved_references: " PTR_FORMAT, p2i(resolved_references_or_null()));
  st->print_cr(" - reference_map: " PTR_FORMAT, p2i(reference_map()));
  st->print_cr(" - resolved_klasses: " PTR_FORMAT, p2i(resolved_klasses()));
  st->print_cr(" - cp length: %d", length());

  for (int index = 1; index < length(); index++) {      // Index 0 is unused
    ((ConstantPool*)this)->print_entry_on(index, st);
    switch (tag_at(index).value()) {
      case JVM_CONSTANT_Long :
      case JVM_CONSTANT_Double :
        index++;   // Skip entry following eigth-byte constant
    }

  }
  st->cr();
}

// Print one constant pool entry
void ConstantPool::print_entry_on(const int cp_index, outputStream* st) {
  EXCEPTION_MARK;
  st->print(" - %3d : ", cp_index);
  tag_at(cp_index).print_on(st);
  st->print(" : ");
  switch (tag_at(cp_index).value()) {
    case JVM_CONSTANT_Class :
      { Klass* k = klass_at(cp_index, CATCH);
        guarantee(k != nullptr, "need klass");
        k->print_value_on(st);
        st->print(" {" PTR_FORMAT "}", p2i(k));
      }
      break;
    case JVM_CONSTANT_Fieldref :
    case JVM_CONSTANT_Methodref :
    case JVM_CONSTANT_InterfaceMethodref :
      {
        FMReference ref(this, cp_index);
        st->print("klass_index=%d name_and_type_index=%d",
                  ref.klass_index(), ref.nt_index());
      }
      break;
    case JVM_CONSTANT_String :
      unresolved_string_at(cp_index)->print_value_on(st);
      break;
    case JVM_CONSTANT_Integer :
      st->print("%d", int_at(cp_index));
      break;
    case JVM_CONSTANT_Float :
      st->print("%f", float_at(cp_index));
      break;
    case JVM_CONSTANT_Long :
      st->print_jlong(long_at(cp_index));
      break;
    case JVM_CONSTANT_Double :
      st->print("%lf", double_at(cp_index));
      break;
    case JVM_CONSTANT_NameAndType :
      {
        NTReference nt(this, cp_index);
        st->print("name_index=%d signature_index=%d",
                  nt.name_index(), nt.signature_index());
      }
      break;
    case JVM_CONSTANT_Utf8 :
      symbol_at(cp_index)->print_value_on(st);
      break;
    case JVM_CONSTANT_ClassIndex: {
        int name_index = *int_at_addr(cp_index);
        st->print("klass_index=%d ", name_index);
        symbol_at(name_index)->print_value_on(st);
      }
      break;
    case JVM_CONSTANT_UnresolvedClass :               // fall-through
    case JVM_CONSTANT_UnresolvedClassInError: {
        KlassReference kref(this, cp_index);
        symbol_at(kref.name_index())->print_value_on(st);
      }
      break;
    case JVM_CONSTANT_MethodHandle :
    case JVM_CONSTANT_MethodHandleInError :
      {
        MethodHandleReference ref(this, cp_index);
        st->print("ref_kind=%d ref_index=%d",
                  ref.ref_kind(), ref.ref_index());
      }
      break;
    case JVM_CONSTANT_MethodType :
    case JVM_CONSTANT_MethodTypeInError :
      {
        MethodTypeReference ref(this, cp_index);
        st->print("signature_index=%d", ref.signature_index());
      }
      break;
    case JVM_CONSTANT_Dynamic :
    case JVM_CONSTANT_DynamicInError :
    case JVM_CONSTANT_InvokeDynamic :
      {
        BootstrapReference ref(this, cp_index);
        BSMAttributeEntry* bsme = ref.bsme(this);
        st->print("bootstrap_method_index=%d name_and_type_index=%d",
                  ref.bsme_index(), ref.nt_index());
        int argc = bsme->argument_count();
        if (argc > 0) {
          for (int arg_i = 0; arg_i < argc; arg_i++) {
            int arg = bsme->argument_index(arg_i);
            st->print((arg_i == 0 ? " arguments={%d" : ", %d"), arg);
          }
          st->print("}");
        }
      }
      break;
    default:
      // print something, because this is for debugging
      st->print("? (tag=%d)", tag_at(cp_index).value());
      break;
  }
  st->cr();
}

void ConstantPool::print_value_on(outputStream* st) const {
  assert(is_constantPool(), "must be constantPool");
  st->print("constant pool [%d]", length());
  if (has_preresolution()) st->print("/preresolution");
  st->print("/bsms[%d]", bsm_attribute_count());
  print_address_on(st);
  if (pool_holder() != nullptr) {
    st->print(" for ");
    pool_holder()->print_value_on(st);
    bool extra = (pool_holder()->constants() != this);
    if (extra)  st->print(" (extra)");
  }
  if (cache() != nullptr) {
    st->print(" cache=" PTR_FORMAT, p2i(cache()));
  }
}

// Verification

void ConstantPool::verify_on(outputStream* st) {
  guarantee(is_constantPool(), "object must be constant pool");
  for (int i = 0; i< length();  i++) {
    constantTag tag = tag_at(i);
    if (tag.is_klass() || tag.is_unresolved_klass()) {
      guarantee(klass_name_at(i)->refcount() != 0, "should have nonzero reference count");
    } else if (tag.is_symbol()) {
      Symbol* entry = symbol_at(i);
      guarantee(entry->refcount() != 0, "should have nonzero reference count");
    } else if (tag.is_string()) {
      Symbol* entry = unresolved_string_at(i);
      guarantee(entry->refcount() != 0, "should have nonzero reference count");
    }
  }
  if (pool_holder() != nullptr) {
    // Note: pool_holder() can be null in temporary constant pools
    // used during constant pool merging
    guarantee(pool_holder()->is_klass(),    "should be klass");
  }
}
