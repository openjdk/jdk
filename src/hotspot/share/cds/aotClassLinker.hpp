/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTCLASSLINKER_HPP
#define SHARE_CDS_AOTCLASSLINKER_HPP

#include "interpreter/bytecodes.hpp"
#include "oops/oopsHierarchy.hpp"
#include "memory/allStatic.hpp"
#include "memory/allocation.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

class AOTLinkedClassTable;
class InstanceKlass;
class SerializeClosure;
template <typename T> class Array;


// AOTClassLinker is used during the AOTCache Assembly Phase.
// It links eligible classes before they are written into the AOTCache
//
// The classes linked by AOTClassLinker are recorded in an AOTLinkedClassTable,
// which is also written into the AOTCache.
//
// AOTClassLinker is enabled by the -XX:+AOTClassLinking option. If this option
// is disabled, no AOTLinkedClassTable will be included in the AOTCache.
//
// For each class C in the AOTLinkedClassTable, the following properties for C
// are assigned by AOTClassLinker and cannot be changed thereafter.
//     - The CodeSource for C
//     - The bytecodes in C
//     - The supertypes of C
//     - The ClassLoader, Package and Module of C
//     - The visibility of C
//
// During an Production Run, the JVM can use an AOTCache with an AOTLinkedClassTable
// only if it's guaranteed to produce the same results for the above set of properties
// for each class C in the AOTLinkedClassTable.
//
// For example,
//     - C may be loaded from a different CodeSource when the CLASSPATH is changed.
//     - Some JVMTI agent may allow the bytecodes of C to be modified.
//     - C may be made invisible by module options such as --add-modules
// In such situations, the JVM will refuse to load the AOTCache.
//
class AOTClassLinker :  AllStatic {
  using ClassesTable = ResourceHashtable<InstanceKlass*, bool, 15889, AnyObj::C_HEAP, mtClassShared>;

  // Classes loaded inside vmClasses::resolve_all()
  static ClassesTable* _vm_classes;

  // Classes that should be automatically loaded into system dictionary at VM start-up
  static ClassesTable* _candidates;

  // Sorted list such that super types come first.
  static GrowableArrayCHeap<InstanceKlass*, mtClassShared>* _sorted_candidates;

  static bool is_initialized(); // for debugging

  static void add_vm_class(InstanceKlass* ik);
  static void add_candidate(InstanceKlass* ik);

  static Array<InstanceKlass*>* write_classes(oop class_loader, bool is_javabase);
  static int num_initiated_classes(oop loader1, oop loader2);

public:
  static void initialize();
  static void add_candidates();
  static void write_to_archive();
  static void dispose();

  // Is this class resolved as part of vmClasses::resolve_all()?
  static bool is_vm_class(InstanceKlass* ik);

  // When CDS is enabled, is ik guatanteed to be loaded at deployment time (and
  // cannot be replaced by JVMTI, etc)?
  // This is a necessary (not but sufficient) condition for keeping a direct pointer
  // to ik in precomputed data (such as ConstantPool entries in archived classes,
  // or in AOT-compiled code).
  static bool is_candidate(InstanceKlass* ik);

  // Request that ik to be added to the candidates table. This will return succeed only if
  // ik is allowed to be aot-loaded.
  static bool try_add_candidate(InstanceKlass* ik);

  static int num_app_initiated_classes();
  static int num_platform_initiated_classes();
};

#endif // SHARE_CDS_AOTCLASSLINKER_HPP
