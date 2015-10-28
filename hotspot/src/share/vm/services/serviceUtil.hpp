/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_SERVICEUTIL_HPP
#define SHARE_VM_SERVICES_SERVICEUTIL_HPP

#include "classfile/systemDictionary.hpp"
#include "oops/objArrayOop.hpp"

//
// Serviceability utility functions.
// (Shared by MM and JVMTI).
//
class ServiceUtil : public AllStatic {
 public:

  // Return true if oop represents an object that is "visible"
  // to the java world.
  static inline bool visible_oop(oop o) {
    // the sentinel for deleted handles isn't visible
    if (o == JNIHandles::deleted_handle()) {
      return false;
    }

    // instance
    if (o->is_instance()) {
      // instance objects are visible
      if (o->klass() != SystemDictionary::Class_klass()) {
        return true;
      }
      if (java_lang_Class::is_primitive(o)) {
        return true;
      }
      // java.lang.Classes are visible
      Klass* k = java_lang_Class::as_Klass(o);
      if (k->is_klass()) {
        // if it's a class for an object, an object array, or
        // primitive (type) array then it's visible.
        if (k->is_instance_klass()) {
          return true;
        }
        if (k->is_objArray_klass()) {
          return true;
        }
        if (k->is_typeArray_klass()) {
          return true;
        }
      }
      return false;
    }
    // object arrays are visible if they aren't system object arrays
    if (o->is_objArray()) {
        return true;
    }
    // type arrays are visible
    if (o->is_typeArray()) {
      return true;
    }
    // everything else (Method*s, ...) aren't visible
    return false;
  };   // end of visible_oop()

};

#endif // SHARE_VM_SERVICES_SERVICEUTIL_HPP
