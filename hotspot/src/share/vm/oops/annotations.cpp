/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "memory/heapInspection.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/oopFactory.hpp"
#include "oops/annotations.hpp"
#include "oops/instanceKlass.hpp"
#include "utilities/ostream.hpp"

// Allocate annotations in metadata area
Annotations* Annotations::allocate(ClassLoaderData* loader_data, TRAPS) {
  return new (loader_data, size(), true, THREAD) Annotations();
}

Annotations* Annotations::allocate(ClassLoaderData* loader_data,
                                   Array<AnnotationArray*>* fa,
                                   Array<AnnotationArray*>* ma,
                                   Array<AnnotationArray*>* mpa,
                                   Array<AnnotationArray*>* mda, TRAPS) {
  return new (loader_data, size(), true, THREAD) Annotations(fa, ma, mpa, mda);
}

// helper
static void free_contents(ClassLoaderData* loader_data, Array<AnnotationArray*>* p) {
  if (p != NULL) {
    for (int i = 0; i < p->length(); i++) {
      MetadataFactory::free_array<u1>(loader_data, p->at(i));
    }
    MetadataFactory::free_array<AnnotationArray*>(loader_data, p);
  }
}

void Annotations::deallocate_contents(ClassLoaderData* loader_data) {
  if (class_annotations() != NULL) {
    MetadataFactory::free_array<u1>(loader_data, class_annotations());
  }
  free_contents(loader_data, fields_annotations());
  free_contents(loader_data, methods_annotations());
  free_contents(loader_data, methods_parameter_annotations());
  free_contents(loader_data, methods_default_annotations());

  // Recursively deallocate optional Annotations linked through this one
  MetadataFactory::free_metadata(loader_data, type_annotations());
}

// Set the annotation at 'idnum' to 'anno'.
// We don't want to create or extend the array if 'anno' is NULL, since that is the
// default value.  However, if the array exists and is long enough, we must set NULL values.
void Annotations::set_methods_annotations_of(instanceKlassHandle ik,
                                             int idnum, AnnotationArray* anno,
                                             Array<AnnotationArray*>** md_p,
                                             TRAPS) {
  Array<AnnotationArray*>* md = *md_p;
  if (md != NULL && md->length() > idnum) {
    md->at_put(idnum, anno);
  } else if (anno != NULL) {
    // create the array
    int length = MAX2(idnum+1, (int)ik->idnum_allocated_count());
    md = MetadataFactory::new_array<AnnotationArray*>(ik->class_loader_data(), length, CHECK);
    if (*md_p != NULL) {
      // copy the existing entries
      for (int index = 0; index < (*md_p)->length(); index++) {
        md->at_put(index, (*md_p)->at(index));
      }
    }
    set_annotations(md, md_p);
    md->at_put(idnum, anno);
  } // if no array and idnum isn't included there is nothing to do
}

// Keep created annotations in a global growable array (should be hashtable)
// need to add, search, delete when class is unloaded.
// Does it need a lock?  yes.  This sucks.

// Copy annotations to JVM call or reflection to the java heap.
typeArrayOop Annotations::make_java_array(AnnotationArray* annotations, TRAPS) {
  if (annotations != NULL) {
    int length = annotations->length();
    typeArrayOop copy = oopFactory::new_byteArray(length, CHECK_NULL);
    for (int i = 0; i< length; i++) {
      copy->byte_at_put(i, annotations->at(i));
    }
    return copy;
  } else {
    return NULL;
  }
}


void Annotations::print_value_on(outputStream* st) const {
  st->print("Anotations(" INTPTR_FORMAT ")", this);
}

#if INCLUDE_SERVICES
// Size Statistics

julong Annotations::count_bytes(Array<AnnotationArray*>* p) {
  julong bytes = 0;
  if (p != NULL) {
    for (int i = 0; i < p->length(); i++) {
      bytes += KlassSizeStats::count_array(p->at(i));
    }
    bytes += KlassSizeStats::count_array(p);
  }
  return bytes;
}

void Annotations::collect_statistics(KlassSizeStats *sz) const {
  sz->_annotations_bytes = sz->count(this);
  sz->_class_annotations_bytes = sz->count(class_annotations());
  sz->_fields_annotations_bytes = count_bytes(fields_annotations());
  sz->_methods_annotations_bytes = count_bytes(methods_annotations());
  sz->_methods_parameter_annotations_bytes =
                          count_bytes(methods_parameter_annotations());
  sz->_methods_default_annotations_bytes =
                          count_bytes(methods_default_annotations());

  const Annotations* type_anno = type_annotations();
  if (type_anno != NULL) {
    sz->_type_annotations_bytes = sz->count(type_anno);
    sz->_type_annotations_bytes += sz->count(type_anno->class_annotations());
    sz->_type_annotations_bytes += count_bytes(type_anno->fields_annotations());
    sz->_type_annotations_bytes += count_bytes(type_anno->methods_annotations());
  }

  sz->_annotations_bytes +=
      sz->_class_annotations_bytes +
      sz->_fields_annotations_bytes +
      sz->_methods_annotations_bytes +
      sz->_methods_parameter_annotations_bytes +
      sz->_methods_default_annotations_bytes +
      sz->_type_annotations_bytes;

  sz->_ro_bytes += sz->_annotations_bytes;
}
#endif // INCLUDE_SERVICES

#define BULLET  " - "

#ifndef PRODUCT
void Annotations::print_on(outputStream* st) const {
  st->print(BULLET"class_annotations            "); class_annotations()->print_value_on(st);
  st->print(BULLET"fields_annotations           "); fields_annotations()->print_value_on(st);
  st->print(BULLET"methods_annotations          "); methods_annotations()->print_value_on(st);
  st->print(BULLET"methods_parameter_annotations"); methods_parameter_annotations()->print_value_on(st);
  st->print(BULLET"methods_default_annotations  "); methods_default_annotations()->print_value_on(st);
}
#endif // PRODUCT
