/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METADATAFACTORY_HPP
#define SHARE_MEMORY_METADATAFACTORY_HPP

#include "classfile/classLoaderData.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "oops/array.inline.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include <type_traits>

class MetadataFactory : AllStatic {
 public:
  template <typename T>
  static Array<T>* new_array(ClassLoaderData* loader_data, int length, TRAPS) {
    return new (loader_data, length, THREAD) Array<T>(length);
  }

  template <typename T>
  static Array<T>* new_array(ClassLoaderData* loader_data, int length, T value, TRAPS) {
    Array<T>* array = new_array<T>(loader_data, length, CHECK_NULL);
    for (int i = 0; i < length; i++) {
      array->at_put(i, value);
    }
    return array;
  }

  template <typename T>
  static void free_array(ClassLoaderData* loader_data, Array<T>* data) {
    if (data != nullptr) {
      assert(loader_data != nullptr, "shouldn't pass null");
      assert(!data->is_shared(), "cannot deallocate array in shared spaces");
      int size = data->size();
      loader_data->metaspace_non_null()->deallocate((MetaWord*)data, size, false);
    }
  }

  // Deallocation method for metadata
  template <class T>
  static void free_metadata(ClassLoaderData* loader_data, T* md) {
    if (md != nullptr) {
      assert(loader_data != nullptr, "shouldn't pass null");
      int size = md->size();
      // Call metadata's deallocate function which will deallocate fields and release_C_heap_structures
      assert(!md->on_stack(), "can't deallocate things on stack");
      assert(!md->is_shared(), "cannot deallocate if in shared spaces");
      md->deallocate_contents(loader_data);
      bool is_klass = md->is_klass();
      // Call the destructor. This is currently used for MethodData which has a member
      // that needs to be destructed to release resources. Most Metadata derived classes have noop
      // destructors and/or cleanup using deallocate_contents.
      // T is a potentially const or volatile qualified pointer. Remove any const
      // or volatile so we can call the destructor of the type T points to.
      using U = std::remove_cv_t<T>;
      md->~U();
      loader_data->metaspace_non_null()->deallocate((MetaWord*)md, size, is_klass);
    }
  }
};

#endif // SHARE_MEMORY_METADATAFACTORY_HPP
