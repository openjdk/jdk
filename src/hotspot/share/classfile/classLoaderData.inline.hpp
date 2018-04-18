/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_CLASSLOADERDATA_INLINE_HPP
#define SHARE_VM_CLASSFILE_CLASSLOADERDATA_INLINE_HPP

#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/weakHandle.inline.hpp"

inline oop ClassLoaderData::class_loader() const {
  assert(!_unloading, "This oop is not available to unloading class loader data");
  assert(_holder.is_null() || _holder.peek() != NULL , "This class loader data holder must be alive");
  return _class_loader.resolve();
}

inline bool ClassLoaderData::is_boot_class_loader_data() const {
    return class_loader() == NULL;
  }

inline ClassLoaderData* ClassLoaderData::class_loader_data_or_null(oop loader) {
  if (loader == NULL) {
    return ClassLoaderData::the_null_class_loader_data();
  }
  return java_lang_ClassLoader::loader_data(loader);
}

inline ClassLoaderData* ClassLoaderData::class_loader_data(oop loader) {
  ClassLoaderData* loader_data = class_loader_data_or_null(loader);
  assert(loader_data != NULL, "Must be");
  return loader_data;
}


inline ClassLoaderData *ClassLoaderDataGraph::find_or_create(Handle loader) {
  guarantee(loader() != NULL && oopDesc::is_oop(loader()), "Loader must be oop");
  // Gets the class loader data out of the java/lang/ClassLoader object, if non-null
  // it's already in the loader_data, so no need to add
  ClassLoaderData* loader_data= java_lang_ClassLoader::loader_data(loader());
  if (loader_data) {
     return loader_data;
  }
  return ClassLoaderDataGraph::add(loader, false);
}

size_t ClassLoaderDataGraph::num_instance_classes() {
  return _num_instance_classes;
}

size_t ClassLoaderDataGraph::num_array_classes() {
  return _num_array_classes;
}

void ClassLoaderDataGraph::inc_instance_classes(size_t count) {
  Atomic::add(count, &_num_instance_classes);
}

void ClassLoaderDataGraph::dec_instance_classes(size_t count) {
  assert(count <= _num_instance_classes, "Sanity");
  Atomic::sub(count, &_num_instance_classes);
}

void ClassLoaderDataGraph::inc_array_classes(size_t count) {
  Atomic::add(count, &_num_array_classes);
}

void ClassLoaderDataGraph::dec_array_classes(size_t count) {
  assert(count <= _num_array_classes, "Sanity");
  Atomic::sub(count, &_num_array_classes);
}

#endif // SHARE_VM_CLASSFILE_CLASSLOADERDATA_INLINE_HPP
