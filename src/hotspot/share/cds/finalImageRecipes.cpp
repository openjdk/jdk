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

static FinalImageRecipes* _final_image_recipes = nullptr;

void* FinalImageRecipes::operator new(size_t size) throw() {
  return ArchiveBuilder::current()->ro_region_alloc(size);
}

void FinalImageRecipes::record_recipes_impl() {
  assert(CDSConfig::is_dumping_preimage_static_archive(), "must be");
  ResourceMark rm;
  GrowableArray<Klass*>* klasses = ArchiveBuilder::current()->klasses();

  // Record the indys that have been resolved in the training run. These indys will be
  // resolved during the final image assembly.

  GrowableArray<InstanceKlass*> tmp_indy_klasses;
  GrowableArray<Array<int>*> tmp_indy_cp_indices;
  int total_indys_to_resolve = 0;
  for (int i = 0; i < klasses->length(); i++) {
    Klass* k = klasses->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      GrowableArray<int> indices;

      if (ik->constants()->cache() != nullptr) {
        Array<ResolvedIndyEntry>* tmp_indy_entries = ik->constants()->cache()->resolved_indy_entries();
        if (tmp_indy_entries != nullptr) {
          for (int i = 0; i < tmp_indy_entries->length(); i++) {
            ResolvedIndyEntry* rie = tmp_indy_entries->adr_at(i);
            int cp_index = rie->constant_pool_index();
            if (rie->is_resolved()) {
              indices.append(cp_index);
            }
          }
        }
      }

      if (indices.length() > 0) {
        tmp_indy_klasses.append(ArchiveBuilder::current()->get_buffered_addr(ik));
        tmp_indy_cp_indices.append(ArchiveUtils::archive_array(&indices));
        total_indys_to_resolve += indices.length();
      }
    }
  }

  _all_klasses = ArchiveUtils::archive_array(klasses);
  ArchivePtrMarker::mark_pointer(&_all_klasses);

  assert(tmp_indy_klasses.length() == tmp_indy_cp_indices.length(), "must be");
  if (tmp_indy_klasses.length() > 0) {
    _indy_klasses = ArchiveUtils::archive_array(&tmp_indy_klasses);
    _indy_cp_indices = ArchiveUtils::archive_array(&tmp_indy_cp_indices);

    ArchivePtrMarker::mark_pointer(&_indy_klasses);
    ArchivePtrMarker::mark_pointer(&_indy_cp_indices);
  }
  log_info(cds)("%d indies in %d classes will be resolved in final CDS image", total_indys_to_resolve, tmp_indy_klasses.length());
}

void FinalImageRecipes::load_all_classes(TRAPS) {
  assert(CDSConfig::is_dumping_final_static_archive(), "sanity");
  Handle class_loader(THREAD, SystemDictionary::java_system_loader());
  for (int i = 0; i < _all_klasses->length(); i++) {
    Klass* k = _all_klasses->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      if (!ik->is_shared_unregistered_class() && !ik->is_hidden()) {
        Klass* actual = SystemDictionary::resolve_or_fail(ik->name(), class_loader, true, CHECK);
        if (actual != ik) {
          ResourceMark rm(THREAD);
          log_error(cds)("Unable to resolve class from CDS archive: %s", ik->external_name());
          log_error(cds)("Expected: " INTPTR_FORMAT ", actual: " INTPTR_FORMAT, p2i(ik), p2i(actual));
          log_error(cds)("Please check if your VM command-line is the same as in the training run");
          MetaspaceShared::unrecoverable_writing_error();
        }
        assert(ik->is_loaded(), "must be");
        ik->link_class(CHECK);
      }
    }
  }
}

void FinalImageRecipes::apply_recipes_for_invokedynamic(TRAPS) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");

  if (CDSConfig::is_dumping_invokedynamic() && _indy_klasses != nullptr) {
    assert(_indy_cp_indices != nullptr, "must be");
    for (int i = 0; i < _indy_klasses->length(); i++) {
      InstanceKlass* ik = _indy_klasses->at(i);
      ConstantPool* cp = ik->constants();
      Array<int>* cp_indices = _indy_cp_indices->at(i);
      GrowableArray<bool> preresolve_list(cp->length(), cp->length(), false);
      for (int j = 0; j < cp_indices->length(); j++) {
        preresolve_list.at_put(cp_indices->at(j), true);
      }
      AOTConstantPoolResolver::preresolve_indy_cp_entries(THREAD, ik, &preresolve_list);
    }
  }
}

void FinalImageRecipes::record_recipes() {
  _final_image_recipes = new FinalImageRecipes();
  _final_image_recipes->record_recipes_impl();
}

void FinalImageRecipes::apply_recipes(TRAPS) {
  assert(CDSConfig::is_dumping_final_static_archive(), "must be");
  if (_final_image_recipes != nullptr) {
    _final_image_recipes->apply_recipes_impl(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      log_error(cds)("%s: %s", PENDING_EXCEPTION->klass()->external_name(),
                     java_lang_String::as_utf8_string(java_lang_Throwable::message(PENDING_EXCEPTION)));
      log_error(cds)("Please check if your VM command-line is the same as in the training run");
      MetaspaceShared::unrecoverable_writing_error("Unexpected exception, use -Xlog:cds,exceptions=trace for detail");
    }
  }

  // Set it to null as we don't need to write this table into the final image.
  _final_image_recipes = nullptr;
}

void FinalImageRecipes::apply_recipes_impl(TRAPS) {
  load_all_classes(CHECK);
  apply_recipes_for_invokedynamic(CHECK);
}

void FinalImageRecipes::serialize(SerializeClosure* soc) {
  soc->do_ptr((void**)&_final_image_recipes);
}
