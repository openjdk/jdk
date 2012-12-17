/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.hpp"

inline ClassLoaderData* ClassLoaderData::class_loader_data(oop loader) {
  if (loader == NULL) return ClassLoaderData::the_null_class_loader_data();
  ClassLoaderData* loader_data = java_lang_ClassLoader::loader_data(loader);
  assert(loader_data != NULL, "Must be");
  return loader_data;
}


inline ClassLoaderData *ClassLoaderDataGraph::find_or_create(Handle loader, TRAPS) {
  assert(loader() != NULL,"Must be a class loader");
  // Gets the class loader data out of the java/lang/ClassLoader object, if non-null
  // it's already in the loader_data, so no need to add
  ClassLoaderData** loader_data_addr = java_lang_ClassLoader::loader_data_addr(loader());
  ClassLoaderData* loader_data_id = *loader_data_addr;
  if (loader_data_id) {
     return loader_data_id;
  }
  return ClassLoaderDataGraph::add(loader_data_addr, loader, THREAD);
}
