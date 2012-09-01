/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/space.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.inline2.hpp"
#include "runtime/aprofiler.hpp"


bool AllocationProfiler::_active = false;
GrowableArray<Klass*>* AllocationProfiler::_print_array = NULL;


class AllocProfClosure : public ObjectClosure {
 public:
  void do_object(oop obj) {
    Klass* k = obj->klass();
    k->set_alloc_count(k->alloc_count() + 1);
    k->set_alloc_size(k->alloc_size() + obj->size());
  }
};


void AllocationProfiler::iterate_since_last_gc() {
  if (is_active()) {
    AllocProfClosure blk;
    GenCollectedHeap* heap = GenCollectedHeap::heap();
    heap->object_iterate_since_last_GC(&blk);
  }
}


void AllocationProfiler::engage() {
  _active = true;
}


void AllocationProfiler::disengage() {
  _active = false;
}


void AllocationProfiler::add_class_to_array(Klass* k) {
  _print_array->append(k);
}


void AllocationProfiler::add_classes_to_array(Klass* k) {
  // Iterate over klass and all array klasses for klass
  k->with_array_klasses_do(&AllocationProfiler::add_class_to_array);
}


int AllocationProfiler::compare_classes(Klass** k1, Klass** k2) {
  // Sort by total allocation size
  return (*k2)->alloc_size() - (*k1)->alloc_size();
}


int AllocationProfiler::average(size_t alloc_size, int alloc_count) {
  return (int) ((double) (alloc_size * BytesPerWord) / MAX2(alloc_count, 1) + 0.5);
}


void AllocationProfiler::sort_and_print_array(size_t cutoff) {
  _print_array->sort(&AllocationProfiler::compare_classes);
  tty->print_cr("________________Size"
                "__Instances"
                "__Average"
                "__Class________________");
  size_t total_alloc_size = 0;
  int total_alloc_count = 0;
  for (int index = 0; index < _print_array->length(); index++) {
    Klass* k = _print_array->at(index);
    size_t alloc_size = k->alloc_size();
    if (alloc_size > cutoff) {
      int alloc_count = k->alloc_count();
#ifdef PRODUCT
      const char* name = k->external_name();
#else
      const char* name = k->internal_name();
#endif
      tty->print_cr("%20u %10u %8u  %s",
        alloc_size * BytesPerWord,
        alloc_count,
        average(alloc_size, alloc_count),
        name);
      total_alloc_size += alloc_size;
      total_alloc_count += alloc_count;
    }
    k->set_alloc_count(0);
    k->set_alloc_size(0);
  }
  tty->print_cr("%20u %10u %8u  --total--",
    total_alloc_size * BytesPerWord,
    total_alloc_count,
    average(total_alloc_size, total_alloc_count));
  tty->cr();
}


void AllocationProfiler::print(size_t cutoff) {
  ResourceMark rm;
  assert(!is_active(), "AllocationProfiler cannot be active while printing profile");

  tty->cr();
  tty->print_cr("Allocation profile (sizes in bytes, cutoff = %ld bytes):", cutoff * BytesPerWord);
  tty->cr();

  // Print regular instance klasses and basic type array klasses
  _print_array = new GrowableArray<Klass*>(SystemDictionary::number_of_classes()*2);
  SystemDictionary::classes_do(&add_classes_to_array);
  Universe::basic_type_classes_do(&add_classes_to_array);
  sort_and_print_array(cutoff);

  // This used to print metadata in the permgen but since there isn't a permgen
  // anymore, it is not yet implemented.
}
