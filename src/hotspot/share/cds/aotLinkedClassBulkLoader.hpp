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

#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
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
// the AOTLinkedClassTable into their respective ClassLoaders. This happens very early
// in the JVM bootstrap stage, before any Java bytecode is executed.
//
// IMPLEMENTATION NOTES:
// We also proactively link all the classes in the AOTLinkedClassTable, and move
// the AOT-initialized classes to the "initialized" state. Due to limitations
// of the current JVM bootstrap sequence, link_or_init_javabase_classes() and
// link_or_init_non_javabase_classes() need to be called after some Java bytecodes are
// executed. Future RFEs will move these calls to earlier stages.
class AOTLinkedClassBulkLoader :  AllStatic {
  static void preload_classes_impl(TRAPS);
  static void preload_classes_in_table(Array<InstanceKlass*>* classes,
                                       const char* category_name, Handle loader, TRAPS);
  static void initiate_loading(JavaThread* current, const char* category, Handle initiating_loader, Array<InstanceKlass*>* classes);
  static void link_classes_impl(TRAPS);
  static void link_classes_in_table(Array<InstanceKlass*>* classes, TRAPS);
  static void init_non_javabase_classes_impl(TRAPS);
  static void init_classes_for_loader(Handle class_loader, Array<InstanceKlass*>* classes, TRAPS);
  static void replay_training_at_init(Array<InstanceKlass*>* classes, TRAPS) NOT_CDS_RETURN;

#ifdef ASSERT
  static void validate_module_of_preloaded_classes();
  static void validate_module_of_preloaded_classes_in_table(Array<InstanceKlass*>* classes,
                                                            const char* category_name, Handle loader);
  static void validate_module(Klass* k, const char* category_name, oop module_oop);
#endif

public:
  static void serialize(SerializeClosure* soc) NOT_CDS_RETURN;
  static void preload_classes(JavaThread* current) NOT_CDS_RETURN;
  static void link_classes(JavaThread* current) NOT_CDS_RETURN;
  static void init_javabase_classes(JavaThread* current) NOT_CDS_RETURN;
  static void init_non_javabase_classes(JavaThread* current) NOT_CDS_RETURN;
  static void exit_on_exception(JavaThread* current);

  static void replay_training_at_init_for_preloaded_classes(TRAPS) NOT_CDS_RETURN;
};

#endif // SHARE_CDS_AOTLINKEDCLASSBULKLOADER_HPP
