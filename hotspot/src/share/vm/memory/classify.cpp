/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_classify.cpp.incl"


const char* ClassifyObjectClosure::object_type_name[number_object_types] = {
  "unknown",
  "instance",
  "instanceRef",
  "objArray",
  "symbol",
  "klass",
  "instanceKlass",
  "method",
  "constMethod",
  "methodData",
  "constantPool",
  "constantPoolCache",
  "typeArray",
  "compiledICHolder"
};


object_type ClassifyObjectClosure::classify_object(oop obj, bool count) {
  object_type type = unknown_type;

  Klass* k = obj->blueprint();

  if (k->as_klassOop() == SystemDictionary::Object_klass()) {
    tty->print_cr("Found the class!");
  }

  if (count) {
    k->set_alloc_count(k->alloc_count() + 1);
  }

  if (obj->is_instance()) {
    if (k->oop_is_instanceRef()) {
      type = instanceRef_type;
    } else {
      type = instance_type;
    }
  } else if (obj->is_typeArray()) {
    type = typeArray_type;
  } else if (obj->is_objArray()) {
    type = objArray_type;
  } else if (obj->is_symbol()) {
    type = symbol_type;
  } else if (obj->is_klass()) {
    Klass* k = ((klassOop)obj)->klass_part();
    if (k->oop_is_instance()) {
      type = instanceKlass_type;
    } else {
      type = klass_type;
    }
  } else if (obj->is_method()) {
    type = method_type;
  } else if (obj->is_constMethod()) {
    type = constMethod_type;
  } else if (obj->is_methodData()) {
    ShouldNotReachHere();
  } else if (obj->is_constantPool()) {
    type = constantPool_type;
  } else if (obj->is_constantPoolCache()) {
    type = constantPoolCache_type;
  } else if (obj->is_compiledICHolder()) {
    type = compiledICHolder_type;
  } else {
    ShouldNotReachHere();
  }

  assert(type != unknown_type, "found object of unknown type.");
  return type;
}


void ClassifyObjectClosure::reset() {
  for (int i = 0; i < number_object_types; ++i) {
    object_count[i] = 0;
    object_size[i] = 0;
  }
  total_object_count = 0;
  total_object_size = 0;
}


void ClassifyObjectClosure::do_object(oop obj) {
  int i = classify_object(obj, true);
  ++object_count[i];
  ++total_object_count;
  size_t size = obj->size() * HeapWordSize;
  object_size[i] += size;
  total_object_size += size;
}


size_t ClassifyObjectClosure::print() {
  int num_objects = 0;
  size_t size_objects = 0;
  for (int i = 0; i < number_object_types; ++i) {
    if (object_count[i] != 0) {
      tty->print_cr("%8d  %-22s  (%8d bytes, %5.2f bytes/object)",
                    object_count[i], object_type_name[i], object_size[i],
                    (float)object_size[i]/(float)object_count[i]);
    }
    num_objects += object_count[i];
    size_objects += object_size[i];
  }
  assert(num_objects == total_object_count, "Object count mismatch!");
  assert(size_objects == total_object_size, "Object size mismatch!");

  tty->print_cr(" Total:  %d objects, %d bytes", total_object_count,
                total_object_size);
  return total_object_size;
}


void ClassifyInstanceKlassClosure::do_object(oop obj) {
  int type = classify_object(obj, false);
  if (type == instanceKlass_type || type == klass_type) {
    Klass* k = ((klassOop)obj)->klass_part();
    if (k->alloc_count() > 0) {
      ResourceMark rm;
      const char *name;
      if (k->name() == NULL) {

        if (obj == Universe::klassKlassObj()) {
          name = "_klassKlassObj";
        } else if (obj == Universe::arrayKlassKlassObj()) {
          name = "_arrayKlassKlassObj";
        } else if (obj == Universe::objArrayKlassKlassObj()) {
          name = "_objArrayKlassKlassObj";
        } else if (obj == Universe::typeArrayKlassKlassObj()) {
          name = "_typeArrayKlassKlassObj";
        } else if (obj == Universe::instanceKlassKlassObj()) {
          name = "_instanceKlassKlassObj";
        } else if (obj == Universe::symbolKlassObj()) {
          name = "_symbolKlassObj";
        } else if (obj == Universe::methodKlassObj()) {
          name = "_methodKlassObj";
        } else if (obj == Universe::constMethodKlassObj()) {
          name = "_constMethodKlassObj";
        } else if (obj == Universe::constantPoolKlassObj()) {
          name = "_constantPoolKlassObj";
        } else if (obj == Universe::constantPoolCacheKlassObj()) {
          name = "_constantPoolCacheKlassObj";
        } else if (obj == Universe::compiledICHolderKlassObj()) {
          name = "_compiledICHolderKlassObj";
        } else if (obj == Universe::systemObjArrayKlassObj()) {
          name = "_systemObjArrayKlassObj";
        } else {
          name = "[unnamed]";
        }
      } else {
        name = k->external_name();
      }
      tty->print_cr("% 8d  instances of %s", k->alloc_count(), name);
    }
    total_instances += k->alloc_count();
  }
}


void ClassifyInstanceKlassClosure::print() {
  tty->print_cr(" Total instances:  %d.", total_instances);
}


void ClassifyInstanceKlassClosure::reset() {
  total_instances = 0;
}
