/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_CLASSIFY_HPP
#define SHARE_VM_MEMORY_CLASSIFY_HPP

#include "oops/oop.inline.hpp"

typedef enum oop_type {
  unknown_type,
  instance_type,
  instanceRef_type,
  objArray_type,
  symbol_type,
  klass_type,
  instanceKlass_type,
  method_type,
  constMethod_type,
  methodData_type,
  constantPool_type,
  constantPoolCache_type,
  typeArray_type,
  compiledICHolder_type,
  number_object_types
} object_type;


// Classify objects by type and keep counts.
// Print the count and space taken for each type.


class ClassifyObjectClosure : public ObjectClosure {
private:

  static const char* object_type_name[number_object_types];

  int total_object_count;
  size_t total_object_size;
  int object_count[number_object_types];
  size_t object_size[number_object_types];

public:
  ClassifyObjectClosure() { reset(); }
  void reset();
  void do_object(oop obj);
  static object_type classify_object(oop obj, bool count);
  size_t print();
};


// Count objects using the alloc_count field in the object's klass
// object.

class ClassifyInstanceKlassClosure : public ClassifyObjectClosure {
private:
  int total_instances;
public:
  ClassifyInstanceKlassClosure() { reset(); }
  void reset();
  void print();
  void do_object(oop obj);
};


// Clear the alloc_count fields in all classes so that the count can be
// restarted.

class ClearAllocCountClosure : public ObjectClosure {
public:
  void do_object(oop obj) {
    if (obj->is_klass()) {
      Klass* k = Klass::cast((klassOop)obj);
      k->set_alloc_count(0);
    }
  }
};

#endif // SHARE_VM_MEMORY_CLASSIFY_HPP
