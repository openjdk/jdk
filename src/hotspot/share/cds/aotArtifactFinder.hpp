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

#ifndef SHARE_CDS_AOTARTIFACTFINDER_HPP
#define SHARE_CDS_AOTARTIFACTFINDER_HPP

#include "memory/allStatic.hpp"
#include "utilities/exceptions.hpp"

class ArrayKlass;
class InstanceKlass;
class MetaspaceClosure;
class TypeArrayKlass;

// AOTArtifactFinder finds (the roots of) all artifacts that should be included in the AOT cache. These include:
//   [1] C++ Klasses
//   [2] Java heap objects
// It also decides what Klasses must be cached in aot-initialized state.
//
// ArchiveBuilder uses [1] as roots to scan for all MetaspaceObjs that need to be cached.
// ArchiveHeapWriter uses [2] to create an image of the archived heap.
//
// [1] is stored in _all_cached_classes in aotArtifactFinder.cpp.
// [2] is stored in HeapShared::archived_object_cache().
//
// Although many Klasses and heap objects are created in the assembly phase, we only store a subset of them into
// the AOT cache. For example:
//     - Klasses that fail verification are excluded
//     - Many Klasses are stored in non-initialized state, so any initialized static fields in their
//       java mirrors must be cleared.
//     - To conserve space, we exclude any hidden classes that are not referenced.
//
// The discovery of [1] and [2] is interdependent, and is done inside AOTArtifactFinder::find()
//     - We first add a set of roots that must be included in the AOT cache
//       - mirrors of primitive classes (e.g., int.class in Java source code).
//       - primitive array classes
//       - non hidden classes
//       - registered lambda proxy classes
//    - Whenever a class is added, we scan its constant pool. This will discover references
//      to hidden classes. All such hidden classes are added.
//    - As heap objects (**Note2) and classes are discovered, we find out what classes must
//      be AOT-initialized:
//       - If we discover at least one instance of class X, then class X is AOT-initialized (** Note1).
//       - If AOTClassInitializer::can_archive_initialized_mirror(X) is true, then X is AOT-initialized.
//         This function checks for the @jdk.internal.vm.annotation.AOTSafeClassInitializer annotation.
//    - For each AOT-initialized class, we scan all the static fields in its java mirror. This will in
//      turn discover more Klasses and java heap objects.
//    - The scanning continues until we reach a steady state.
//
// Note1: See TODO comments in HeapShared::archive_object() for exceptions to this rule.
//
// Note2: The scanning of Java objects is done in heapShared.cpp. Please see calls into the HeapShared class
//        from AOTArtifactFinder.

class AOTArtifactFinder : AllStatic {
  static void start_scanning_for_oops();
  static void end_scanning_for_oops();
  static void scan_oops_in_instance_class(InstanceKlass* ik);
  static void scan_oops_in_array_class(ArrayKlass* ak);
  static void add_cached_type_array_class(TypeArrayKlass* tak);
  static void add_cached_instance_class(InstanceKlass* ik);
  static void append_to_all_cached_classes(Klass* k);
public:
  static void initialize();
  static void find_artifacts();
  static void add_cached_class(Klass* k);
  static void add_aot_inited_class(InstanceKlass* ik);
  static void all_cached_classes_do(MetaspaceClosure* it);
  static void dispose();
};

#endif // SHARE_CDS_AOTARTIFACTFINDER_HPP
