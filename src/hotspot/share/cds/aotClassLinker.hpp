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
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

class AOTLinkedClassTable;
class InstanceKlass;
class SerializeClosure;
template <typename T> class Array;
enum class AOTLinkedClassCategory : int;

// AOTClassLinker is used during the AOTCache Assembly Phase.
// It links eligible classes before they are written into the AOTCache
//
// The classes linked by AOTClassLinker are recorded in an AOTLinkedClassTable,
// which is also written into the AOTCache.
//
// AOTClassLinker is enabled by the -XX:+AOTClassLinking option. If this option
// is disabled, an empty AOTLinkedClassTable will be included in the AOTCache.
//
// For each class C in the AOTLinkedClassTable, the following properties for C
// are assigned by AOTClassLinker and cannot be changed thereafter.
//     - The CodeSource for C
//     - The bytecodes in C
//     - The supertypes of C
//     - The ClassLoader, Package and Module of C
//     - The visibility of C
//
// During a production run, the JVM can use an AOTCache with an AOTLinkedClassTable
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
  static const int TABLE_SIZE = 15889; // prime number
  using ClassesTable = ResourceHashtable<InstanceKlass*, bool, TABLE_SIZE, AnyObj::C_HEAP, mtClassShared>;

  // Classes loaded inside vmClasses::resolve_all()
  static ClassesTable* _vm_classes;

  // Classes that should be automatically loaded into system dictionary at VM start-up
  static ClassesTable* _candidates;

  // Sorted list such that super types come first.
  static GrowableArrayCHeap<InstanceKlass*, mtClassShared>* _sorted_candidates;

  DEBUG_ONLY(static bool is_initialized());

  static void add_vm_class(InstanceKlass* ik);
  static void add_new_candidate(InstanceKlass* ik);

  static Array<InstanceKlass*>* write_classes(oop class_loader, bool is_javabase);
  static int count_public_classes(oop loader);

public:
  static void initialize();
  static void add_candidates();
  static void write_to_archive();
  static void dispose();

  // Is this class resolved as part of vmClasses::resolve_all()?
  static bool is_vm_class(InstanceKlass* ik);

  // When CDS is enabled, is ik guaranteed to be linked at deployment time (and
  // cannot be replaced by JVMTI, etc)?
  // This is a necessary (but not sufficient) condition for keeping a direct pointer
  // to ik in AOT-computed data (such as ConstantPool entries in archived classes,
  // or in AOT-compiled code).
  static bool is_candidate(InstanceKlass* ik);

  // Request that ik be added to the candidates table. This will return true only if
  // ik is allowed to be aot-linked.
  static bool try_add_candidate(InstanceKlass* ik);

  static int num_app_initiated_classes();
  static int num_platform_initiated_classes();

  // Used in logging: "boot1", "boot2", "plat", "app" and "unreg";
  static const char* class_category_name(AOTLinkedClassCategory category);
  static const char* class_category_name(Klass* k);
};

// AOT-linked classes are divided into different categories and are loaded
// in two phases during the production run.
enum class AOTLinkedClassCategory : int {
  BOOT1,       // Only java.base classes are loaded in the 1st phase
  BOOT2,       // All boot classes that not in java.base are loaded in the 2nd phase
  PLATFORM,    // Classes for platform loader, loaded in the 2nd phase
  APP,         // Classes for the app loader, loaded in the 2nd phase
  UNREGISTERED // classes loaded outside of the boot/platform/app loaders; currently not supported by AOTClassLinker
};

#endif // SHARE_CDS_AOTCLASSLINKER_HPP
