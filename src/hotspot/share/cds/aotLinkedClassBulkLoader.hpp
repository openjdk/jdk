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

#ifndef SHARE_CDS_AOTLINKEDCLASSBULKLOADER_HPP
#define SHARE_CDS_AOTLINKEDCLASSBULKLOADER_HPP

#include "memory/allStatic.hpp"
#include "memory/allocation.hpp"
#include "runtime/handles.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"

class AOTLinkedClassTable;
class ClassLoaderData;
class InstanceKlass;
class SerializeClosure;
template <typename T> class Array;
enum class AOTLinkedClassCategory : int;

// During a Production Run, the AOTLinkedClassBulkLoader loads all classes from
// a AOTLinkedClassTable into their respective ClassLoaders. This happens very early
// in the JVM bootstrap stage, before any application code is executed.
//
class AOTLinkedClassBulkLoader :  AllStatic {
  static bool _boot2_completed;
  static bool _platform_completed;
  static bool _app_completed;
  static bool _all_completed;
  static void load_classes_in_loader(JavaThread* current, AOTLinkedClassCategory class_category, oop class_loader_oop);
  static void load_classes_in_loader_impl(AOTLinkedClassCategory class_category, oop class_loader_oop, TRAPS);
  static void load_table(AOTLinkedClassTable* table, AOTLinkedClassCategory class_category, Handle loader, TRAPS);
  static void initiate_loading(JavaThread* current, const char* category, Handle initiating_loader, Array<InstanceKlass*>* classes);
  static void load_classes_impl(AOTLinkedClassCategory class_category, Array<InstanceKlass*>* classes,
                                const char* category_name, Handle loader, TRAPS);
  static void load_hidden_class(ClassLoaderData* loader_data, InstanceKlass* ik, TRAPS);
  static void init_required_classes_for_loader(Handle class_loader, Array<InstanceKlass*>* classes, TRAPS);
  static void replay_training_at_init(Array<InstanceKlass*>* classes, TRAPS) NOT_CDS_RETURN;
public:
  static void serialize(SerializeClosure* soc, bool is_static_archive) NOT_CDS_RETURN;

  static void load_javabase_classes(JavaThread* current) NOT_CDS_RETURN;
  static void load_non_javabase_classes(JavaThread* current) NOT_CDS_RETURN;
  static void finish_loading_javabase_classes(TRAPS) NOT_CDS_RETURN;
  static void exit_on_exception(JavaThread* current);

  static void replay_training_at_init_for_preloaded_classes(TRAPS) NOT_CDS_RETURN;
  static bool class_preloading_finished();
  static bool is_pending_aot_linked_class(Klass* k) NOT_CDS_RETURN_(false);
};

#endif // SHARE_CDS_AOTLINKEDCLASSBULKLOADER_HPP
