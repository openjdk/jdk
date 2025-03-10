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

#ifndef SHARE_CDS_FINALIMAGERECIPES_HPP
#define SHARE_CDS_FINALIMAGERECIPES_HPP

#include "oops/oopsHierarchy.hpp"
#include "utilities/exceptions.hpp"

class InstanceKlass;
class Klass;

template <typename T> class GrowableArray;
template <typename T> class Array;

// This class is used for transferring information from the AOTConfiguration file (aka the "preimage")
// to the JVM that creates the AOTCache (aka the "final image").
//   - The recipes are recorded when CDSConfig::is_dumping_preimage_static_archive() is true.
//   - The recipes are applied when CDSConfig::is_dumping_final_static_archive() is true.
// The following information are recorded:
//   - The list of all classes that are stored in the AOTConfiguration file.
//   - The list of all classes that require AOT resolution of invokedynamic call sites.
class FinalImageRecipes {
  // A list of all the archived classes from the preimage. We want to transfer all of these
  // into the final image.
  Array<Klass*>* _all_klasses;

  // The classes who have resolved at least one indy CP entry during the training run.
  // _indy_cp_indices[i] is a list of all resolved CP entries for _indy_klasses[i].
  Array<InstanceKlass*>* _indy_klasses;
  Array<Array<int>*>*    _indy_cp_indices;

  FinalImageRecipes() : _indy_klasses(nullptr), _indy_cp_indices(nullptr) {}

  void* operator new(size_t size) throw();

  // Called when dumping preimage
  void record_recipes_impl();

  // Called when dumping final image
  void apply_recipes_impl(TRAPS);
  void load_all_classes(TRAPS);
  void apply_recipes_for_invokedynamic(TRAPS);

public:
  static void serialize(SerializeClosure* soc);

  // Called when dumping preimage
  static void record_recipes();

  // Called when dumping final image
  static void apply_recipes(TRAPS);
};

#endif // SHARE_CDS_FINALIMAGERECIPES_HPP
