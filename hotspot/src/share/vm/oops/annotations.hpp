/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_ANNOTATIONS_HPP
#define SHARE_VM_OOPS_ANNOTATIONS_HPP

#include "oops/metadata.hpp"
#include "runtime/handles.hpp"
#include "utilities/array.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"


class ClassLoaderData;
class outputStream;

typedef Array<u1> AnnotationArray;

// Class to hold the various types of annotations. The only metadata that points
// to this is InstanceKlass.

class Annotations: public MetaspaceObj {

  // Annotations for this class, or null if none.
  AnnotationArray*             _class_annotations;
  // Annotation objects (byte arrays) for fields, or null if no annotations.
  // Indices correspond to entries (not indices) in fields array.
  Array<AnnotationArray*>*     _fields_annotations;
  // Annotation objects (byte arrays) for methods, or null if no annotations.
  // Index is the idnum, which is initially the same as the methods array index.
  Array<AnnotationArray*>*     _methods_annotations;
  // Annotation objects (byte arrays) for methods' parameters, or null if no
  // such annotations.
  // Index is the idnum, which is initially the same as the methods array index.
  Array<AnnotationArray*>*     _methods_parameter_annotations;
  // Annotation objects (byte arrays) for methods' default values, or null if no
  // such annotations.
  // Index is the idnum, which is initially the same as the methods array index.
  Array<AnnotationArray*>*     _methods_default_annotations;

  // Constructor where some some values are known to not be null
  Annotations(Array<AnnotationArray*>* fa, Array<AnnotationArray*>* ma,
              Array<AnnotationArray*>* mpa, Array<AnnotationArray*>* mda) :
                 _class_annotations(NULL),
                 _fields_annotations(fa),
                 _methods_annotations(ma),
                 _methods_parameter_annotations(mpa),
                 _methods_default_annotations(mda) {}

 public:
  // Allocate instance of this class
  static Annotations* allocate(ClassLoaderData* loader_data, TRAPS);
  static Annotations* allocate(ClassLoaderData* loader_data,
                               Array<AnnotationArray*>* fa,
                               Array<AnnotationArray*>* ma,
                               Array<AnnotationArray*>* mpa,
                               Array<AnnotationArray*>* mda, TRAPS);
  void deallocate_contents(ClassLoaderData* loader_data);
  DEBUG_ONLY(bool on_stack() { return false; })  // for template
  static int size()    { return sizeof(Annotations) / wordSize; }

  // Constructor to initialize to null
  Annotations() : _class_annotations(NULL), _fields_annotations(NULL),
                  _methods_annotations(NULL),
                  _methods_parameter_annotations(NULL),
                  _methods_default_annotations(NULL) {}

  AnnotationArray* class_annotations() const                       { return _class_annotations; }
  Array<AnnotationArray*>* fields_annotations() const              { return _fields_annotations; }
  Array<AnnotationArray*>* methods_annotations() const             { return _methods_annotations; }
  Array<AnnotationArray*>* methods_parameter_annotations() const   { return _methods_parameter_annotations; }
  Array<AnnotationArray*>* methods_default_annotations() const     { return _methods_default_annotations; }

  void set_class_annotations(AnnotationArray* md)                     { _class_annotations = md; }
  void set_fields_annotations(Array<AnnotationArray*>* md)            { _fields_annotations = md; }
  void set_methods_annotations(Array<AnnotationArray*>* md)           { _methods_annotations = md; }
  void set_methods_parameter_annotations(Array<AnnotationArray*>* md) { _methods_parameter_annotations = md; }
  void set_methods_default_annotations(Array<AnnotationArray*>* md)   { _methods_default_annotations = md; }

  // Redefine classes support
  AnnotationArray* get_method_annotations_of(int idnum)
                                                { return get_method_annotations_from(idnum, _methods_annotations); }

  AnnotationArray* get_method_parameter_annotations_of(int idnum)
                                                { return get_method_annotations_from(idnum, _methods_parameter_annotations); }
  AnnotationArray* get_method_default_annotations_of(int idnum)
                                                { return get_method_annotations_from(idnum, _methods_default_annotations); }


  void set_method_annotations_of(instanceKlassHandle ik,
                                 int idnum, AnnotationArray* anno, TRAPS) {
    set_methods_annotations_of(ik, idnum, anno, &_methods_annotations, THREAD);
  }

  void set_method_parameter_annotations_of(instanceKlassHandle ik,
                                 int idnum, AnnotationArray* anno, TRAPS) {
    set_methods_annotations_of(ik, idnum, anno, &_methods_parameter_annotations, THREAD);
  }

  void set_method_default_annotations_of(instanceKlassHandle ik,
                                 int idnum, AnnotationArray* anno, TRAPS) {
    set_methods_annotations_of(ik, idnum, anno, &_methods_default_annotations, THREAD);
  }

  // Turn metadata annotations into a Java heap object (oop)
  static typeArrayOop make_java_array(AnnotationArray* annotations, TRAPS);

  inline AnnotationArray* get_method_annotations_from(int idnum, Array<AnnotationArray*>* annos);
  void set_annotations(Array<AnnotationArray*>* md, Array<AnnotationArray*>** md_p)  { *md_p = md; }

 private:
  void set_methods_annotations_of(instanceKlassHandle ik,
                                  int idnum, AnnotationArray* anno,
                                  Array<AnnotationArray*>** md_p, TRAPS);

 public:
  const char* internal_name() const { return "{constant pool}"; }
#ifndef PRODUCT
  void print_on(outputStream* st) const;
#endif
  void print_value_on(outputStream* st) const;
};


// For method with idnum get the method's Annotations
inline AnnotationArray* Annotations::get_method_annotations_from(int idnum, Array<AnnotationArray*>* annos) {
  if (annos == NULL || annos->length() <= idnum) {
    return NULL;
  }
  return annos->at(idnum);
}
#endif // SHARE_VM_OOPS_ANNOTATIONS_HPP
