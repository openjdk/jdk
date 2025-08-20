/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveUtils.inline.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/finalImageRecipes.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"

static FinalImageRecipes* _final_image_recipes = nullptr;

void* FinalImageRecipes::operator new(size_t size) throw() {
  return ArchiveBuilder::current()->ro_region_alloc(size);
}

void FinalImageRecipes::record_all_classes() {
  _all_klasses = ArchiveUtils::archive_array(ArchiveBuilder::current()->klasses());
  ArchivePtrMarker::mark_pointer(&_all_klasses);
}

void FinalImageRecipes::record_recipes_for_constantpool() {
  ResourceMark rm;

  // The recipes are recorded regardless of CDSConfig::is_dumping_{invokedynamic,dynamic_proxies,reflection_data}().
  // If some of these options are not enabled, the corresponding recipes will be
  // ignored during the final image assembly.

  GrowableArray<Array<int>*> tmp_cp_recipes;
  GrowableArray<int> tmp_cp_flags;

  GrowableArray<Klass*>* klasses = ArchiveBuilder::current()->klasses();
  for (int i = 0; i < klasses->length(); i++) {
    GrowableArray<int> cp_indices;
    int flags = 0;

    Klass* k = klasses->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      ConstantPool* cp = ik->constants();
      ConstantPoolCache* cp_cache = cp->cache();

      for (int cp_index = 1; cp_index < cp->length(); cp_index++) { // Index 0 is unused
        if (cp->tag_at(cp_index).value() == JVM_CONSTANT_Class) {
          Klass* k = cp->resolved_klass_at(cp_index);
          if (k->is_instance_klass()) {
            cp_indices.append(cp_index);
            flags |= HAS_CLASS;
          }
        }
      }

      if (cp_cache != nullptr) {
        Array<ResolvedFieldEntry>* field_entries = cp_cache->resolved_field_entries();
        if (field_entries != nullptr) {
          for (int i = 0; i < field_entries->length(); i++) {
            ResolvedFieldEntry* rfe = field_entries->adr_at(i);
            if (rfe->is_resolved(Bytecodes::_getfield) ||
                rfe->is_resolved(Bytecodes::_putfield)) {
              cp_indices.append(rfe->constant_pool_index());
              flags |= HAS_FIELD_AND_METHOD;
            }
          }
        }

        Array<ResolvedMethodEntry>* method_entries = cp_cache->resolved_method_entries();
        if (method_entries != nullptr) {
          for (int i = 0; i < method_entries->length(); i++) {
            ResolvedMethodEntry* rme = method_entries->adr_at(i);
            if (rme->is_resolved(Bytecodes::_invokevirtual) ||
                rme->is_resolved(Bytecodes::_invokespecial) ||
                rme->is_resolved(Bytecodes::_invokeinterface) ||
                rme->is_resolved(Bytecodes::_invokestatic) ||
                rme->is_resolved(Bytecodes::_invokehandle)) {
              cp_indices.append(rme->constant_pool_index());
              flags |= HAS_FIELD_AND_METHOD;
            }
          }
        }

        Array<ResolvedIndyEntry>* indy_entries = cp_cache->resolved_indy_entries();
        if (indy_entries != nullptr) {
          for (int i = 0; i < indy_entries->length(); i++) {
            ResolvedIndyEntry* rie = indy_entries->adr_at(i);
            int cp_index = rie->constant_pool_index();
            if (rie->is_resolved()) {
              cp_indices.append(cp_index);
              flags |= HAS_INDY;
            }
          }
        }
      }
    }

    if (cp_indices.length() > 0) {
      tmp_cp_recipes.append(ArchiveUtils::archive_array(&cp_indices));
    } else {
      tmp_cp_recipes.append(nullptr);
    }
    tmp_cp_flags.append(flags);
  }

  _cp_recipes = ArchiveUtils::archive_array(&tmp_cp_recipes);
  ArchivePtrMarker::mark_pointer(&_cp_recipes);

  _cp_flags = ArchiveUtils::archive_array(&tmp_cp_flags);
  ArchivePtrMarker::mark_pointer(&_cp_flags);
}

void FinalImageRecipes::apply_recipes_for_constantpool(JavaThread* current) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");

  for (int i = 0; i < _all_klasses->length(); i++) {
    Array<int>* cp_indices = _cp_recipes->at(i);
    int flags = _cp_flags->at(i);
    if (cp_indices != nullptr) {
      InstanceKlass* ik = InstanceKlass::cast(_all_klasses->at(i));
      if (ik->is_loaded()) {
        ResourceMark rm(current);
        ConstantPool* cp = ik->constants();
        GrowableArray<bool> preresolve_list(cp->length(), cp->length(), false);
        for (int j = 0; j < cp_indices->length(); j++) {
          preresolve_list.at_put(cp_indices->at(j), true);
        }
        if ((flags & HAS_CLASS) != 0) {
          AOTConstantPoolResolver::preresolve_class_cp_entries(current, ik, &preresolve_list);
        }
        if ((flags & HAS_FIELD_AND_METHOD) != 0) {
          AOTConstantPoolResolver::preresolve_field_and_method_cp_entries(current, ik, &preresolve_list);
        }
        if ((flags & HAS_INDY) != 0) {
          AOTConstantPoolResolver::preresolve_indy_cp_entries(current, ik, &preresolve_list);
        }
      }
    }
  }
}

void FinalImageRecipes::load_all_classes(TRAPS) {
  assert(CDSConfig::is_dumping_final_static_archive(), "sanity");
  Handle class_loader(THREAD, SystemDictionary::java_system_loader());
  for (int i = 0; i < _all_klasses->length(); i++) {
    Klass* k = _all_klasses->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      if (ik->defined_by_other_loaders()) {
        SystemDictionaryShared::init_dumptime_info(ik);
        SystemDictionaryShared::add_unregistered_class(THREAD, ik);
        SystemDictionaryShared::copy_unregistered_class_size_and_crc32(ik);
      } else if (!ik->is_hidden()) {
        Klass* actual = SystemDictionary::resolve_or_fail(ik->name(), class_loader, true, CHECK);
        if (actual != ik) {
          ResourceMark rm(THREAD);
          log_error(aot)("Unable to resolve class from CDS archive: %s", ik->external_name());
          log_error(aot)("Expected: " INTPTR_FORMAT ", actual: " INTPTR_FORMAT, p2i(ik), p2i(actual));
          log_error(aot)("Please check if your VM command-line is the same as in the training run");
          MetaspaceShared::unrecoverable_writing_error();
        }
        assert(ik->is_loaded(), "must be");
        ik->link_class(CHECK);
      }
    }
  }
}

void FinalImageRecipes::record_recipes() {
  assert(CDSConfig::is_dumping_preimage_static_archive(), "must be");
  _final_image_recipes = new FinalImageRecipes();
  _final_image_recipes->record_all_classes();
  _final_image_recipes->record_recipes_for_constantpool();
}

void FinalImageRecipes::apply_recipes(TRAPS) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");
  if (_final_image_recipes != nullptr) {
    _final_image_recipes->apply_recipes_impl(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      log_error(aot)("%s: %s", PENDING_EXCEPTION->klass()->external_name(),
                     java_lang_String::as_utf8_string(java_lang_Throwable::message(PENDING_EXCEPTION)));
      log_error(aot)("Please check if your VM command-line is the same as in the training run");
      MetaspaceShared::unrecoverable_writing_error("Unexpected exception, use -Xlog:aot,exceptions=trace for detail");
    }
  }

  // Set it to null as we don't need to write this table into the final image.
  _final_image_recipes = nullptr;
}

void FinalImageRecipes::apply_recipes_impl(TRAPS) {
  load_all_classes(CHECK);
  apply_recipes_for_constantpool(THREAD);
}

void FinalImageRecipes::serialize(SerializeClosure* soc) {
  soc->do_ptr((void**)&_final_image_recipes);
}
