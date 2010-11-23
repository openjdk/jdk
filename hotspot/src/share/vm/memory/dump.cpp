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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc_implementation/shared/spaceDecorator.hpp"
#include "memory/classify.hpp"
#include "memory/filemap.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/methodDataOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/signature.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "utilities/copy.hpp"


// Closure to set up the fingerprint field for all methods.

class FingerprintMethodsClosure: public ObjectClosure {
public:
  void do_object(oop obj) {
    if (obj->is_method()) {
      methodOop mobj = (methodOop)obj;
      ResourceMark rm;
      (new Fingerprinter(mobj))->fingerprint();
    }
  }
};



// Closure to set the hash value (String.hash field) in all of the
// String objects in the heap.  Setting the hash value is not required.
// However, setting the value in advance prevents the value from being
// written later, increasing the likelihood that the shared page contain
// the hash can be shared.
//
// NOTE THAT the algorithm in StringTable::hash_string() MUST MATCH the
// algorithm in java.lang.String.hashCode().

class StringHashCodeClosure: public OopClosure {
private:
  Thread* THREAD;
  int hash_offset;
public:
  StringHashCodeClosure(Thread* t) {
    THREAD = t;
    hash_offset = java_lang_String::hash_offset_in_bytes();
  }

  void do_oop(oop* p) {
    if (p != NULL) {
      oop obj = *p;
      if (obj->klass() == SystemDictionary::String_klass()) {

        int hash;
        typeArrayOop value = java_lang_String::value(obj);
        int length = java_lang_String::length(obj);
        if (length == 0) {
          hash = 0;
        } else {
          int offset = java_lang_String::offset(obj);
          jchar* s = value->char_at_addr(offset);
          hash = StringTable::hash_string(s, length);
        }
        obj->int_field_put(hash_offset, hash);
      }
    }
  }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};


// Remove data from objects which should not appear in the shared file
// (as it pertains only to the current JVM).

class RemoveUnshareableInfoClosure : public ObjectClosure {
public:
  void do_object(oop obj) {
    // Zap data from the objects which is pertains only to this JVM.  We
    // want that data recreated in new JVMs when the shared file is used.
    if (obj->is_method()) {
      ((methodOop)obj)->remove_unshareable_info();
    }
    else if (obj->is_klass()) {
      Klass::cast((klassOop)obj)->remove_unshareable_info();
    }

    // Don't save compiler related special oops (shouldn't be any yet).
    if (obj->is_methodData() || obj->is_compiledICHolder()) {
      ShouldNotReachHere();
    }
  }
};


static bool mark_object(oop obj) {
  if (obj != NULL &&
      !obj->is_shared() &&
      !obj->is_forwarded() &&
      !obj->is_gc_marked()) {
    obj->set_mark(markOopDesc::prototype()->set_marked());
    return true;
  }

  return false;
}

// Closure:  mark objects closure.

class MarkObjectsOopClosure : public OopClosure {
public:
  void do_oop(oop* p)       { mark_object(*p); }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};


class MarkObjectsSkippingKlassesOopClosure : public OopClosure {
public:
  void do_oop(oop* pobj) {
    oop obj = *pobj;
    if (obj != NULL &&
        !obj->is_klass()) {
      mark_object(obj);
    }
  }
  void do_oop(narrowOop* pobj) { ShouldNotReachHere(); }
};


static void mark_object_recursive_skipping_klasses(oop obj) {
  mark_object(obj);
  if (obj != NULL) {
    MarkObjectsSkippingKlassesOopClosure mark_all;
    obj->oop_iterate(&mark_all);
  }
}


// Closure:  mark common read-only objects, excluding symbols

class MarkCommonReadOnly : public ObjectClosure {
private:
  MarkObjectsOopClosure mark_all;
public:
  void do_object(oop obj) {

    // Mark all constMethod objects.

    if (obj->is_constMethod()) {
      mark_object(obj);
      mark_object(constMethodOop(obj)->stackmap_data());
      // Exception tables are needed by ci code during compilation.
      mark_object(constMethodOop(obj)->exception_table());
    }

    // Mark objects referenced by klass objects which are read-only.

    else if (obj->is_klass()) {
      Klass* k = Klass::cast((klassOop)obj);
      mark_object(k->secondary_supers());

      // The METHODS() OBJARRAYS CANNOT BE MADE READ-ONLY, even though
      // it is never modified. Otherwise, they will be pre-marked; the
      // GC marking phase will skip them; and by skipping them will fail
      // to mark the methods objects referenced by the array.

      if (obj->blueprint()->oop_is_instanceKlass()) {
        instanceKlass* ik = instanceKlass::cast((klassOop)obj);
        mark_object(ik->method_ordering());
        mark_object(ik->local_interfaces());
        mark_object(ik->transitive_interfaces());
        mark_object(ik->fields());

        mark_object(ik->class_annotations());

        mark_object_recursive_skipping_klasses(ik->fields_annotations());
        mark_object_recursive_skipping_klasses(ik->methods_annotations());
        mark_object_recursive_skipping_klasses(ik->methods_parameter_annotations());
        mark_object_recursive_skipping_klasses(ik->methods_default_annotations());

        typeArrayOop inner_classes = ik->inner_classes();
        if (inner_classes != NULL) {
          mark_object(inner_classes);
        }
      }
    }
  }
};


// Closure:  mark common symbols

class MarkCommonSymbols : public ObjectClosure {
private:
  MarkObjectsOopClosure mark_all;
public:
  void do_object(oop obj) {

    // Mark symbols refered to by method objects.

    if (obj->is_method()) {
      methodOop m = methodOop(obj);
      mark_object(m->name());
      mark_object(m->signature());
    }

    // Mark symbols referenced by klass objects which are read-only.

    else if (obj->is_klass()) {

      if (obj->blueprint()->oop_is_instanceKlass()) {
        instanceKlass* ik = instanceKlass::cast((klassOop)obj);
        mark_object(ik->name());
        mark_object(ik->generic_signature());
        mark_object(ik->source_file_name());
        mark_object(ik->source_debug_extension());

        typeArrayOop inner_classes = ik->inner_classes();
        if (inner_classes != NULL) {
          int length = inner_classes->length();
          for (int i = 0;
                   i < length;
                   i += instanceKlass::inner_class_next_offset) {
            int ioff = i + instanceKlass::inner_class_inner_name_offset;
            int index = inner_classes->ushort_at(ioff);
            if (index != 0) {
              mark_object(ik->constants()->symbol_at(index));
            }
          }
        }
        ik->field_names_and_sigs_iterate(&mark_all);
      }
    }

    // Mark symbols referenced by other constantpool entries.

    if (obj->is_constantPool()) {
      constantPoolOop(obj)->shared_symbols_iterate(&mark_all);
    }
  }
};


// Closure:  mark char arrays used by strings

class MarkStringValues : public ObjectClosure {
private:
  MarkObjectsOopClosure mark_all;
public:
  void do_object(oop obj) {

    // Character arrays referenced by String objects are read-only.

    if (java_lang_String::is_instance(obj)) {
      mark_object(java_lang_String::value(obj));
    }
  }
};


#ifdef DEBUG
// Closure:  Check for objects left in the heap which have not been moved.

class CheckRemainingObjects : public ObjectClosure {
private:
  int count;

public:
  CheckRemainingObjects() {
    count = 0;
  }

  void do_object(oop obj) {
    if (!obj->is_shared() &&
        !obj->is_forwarded()) {
      ++count;
      if (Verbose) {
        tty->print("Unreferenced object: ");
        obj->print_on(tty);
      }
    }
  }

  void status() {
    tty->print_cr("%d objects no longer referenced, not shared.", count);
  }
};
#endif


// Closure:  Mark remaining objects read-write, except Strings.

class MarkReadWriteObjects : public ObjectClosure {
private:
  MarkObjectsOopClosure mark_objects;
public:
  void do_object(oop obj) {

      // The METHODS() OBJARRAYS CANNOT BE MADE READ-ONLY, even though
      // it is never modified. Otherwise, they will be pre-marked; the
      // GC marking phase will skip them; and by skipping them will fail
      // to mark the methods objects referenced by the array.

    if (obj->is_klass()) {
      mark_object(obj);
      Klass* k = klassOop(obj)->klass_part();
      mark_object(k->java_mirror());
      if (obj->blueprint()->oop_is_instanceKlass()) {
        instanceKlass* ik = (instanceKlass*)k;
        mark_object(ik->methods());
        mark_object(ik->constants());
      }
      if (obj->blueprint()->oop_is_javaArray()) {
        arrayKlass* ak = (arrayKlass*)k;
        mark_object(ak->component_mirror());
      }
      return;
    }

    // Mark constantPool tags and the constantPoolCache.

    else if (obj->is_constantPool()) {
      constantPoolOop pool = constantPoolOop(obj);
      mark_object(pool->cache());
      pool->shared_tags_iterate(&mark_objects);
      return;
    }

    // Mark all method objects.

    if (obj->is_method()) {
      mark_object(obj);
    }
  }
};


// Closure:  Mark String objects read-write.

class MarkStringObjects : public ObjectClosure {
private:
  MarkObjectsOopClosure mark_objects;
public:
  void do_object(oop obj) {

    // Mark String objects referenced by constant pool entries.

    if (obj->is_constantPool()) {
      constantPoolOop pool = constantPoolOop(obj);
      pool->shared_strings_iterate(&mark_objects);
      return;
    }
  }
};


// Move objects matching specified type (ie. lock_bits) to the specified
// space.

class MoveMarkedObjects : public ObjectClosure {
private:
  OffsetTableContigSpace* _space;
  bool _read_only;

public:
  MoveMarkedObjects(OffsetTableContigSpace* space, bool read_only) {
    _space = space;
    _read_only = read_only;
  }

  void do_object(oop obj) {
    if (obj->is_shared()) {
      return;
    }
    if (obj->is_gc_marked() && obj->forwardee() == NULL) {
      int s = obj->size();
      oop sh_obj = (oop)_space->allocate(s);
      if (sh_obj == NULL) {
        if (_read_only) {
          warning("\nThe permanent generation read only space is not large "
                  "enough to \npreload requested classes.  Use "
                  "-XX:SharedReadOnlySize= to increase \nthe initial "
                  "size of the read only space.\n");
        } else {
          warning("\nThe permanent generation read write space is not large "
                  "enough to \npreload requested classes.  Use "
                  "-XX:SharedReadWriteSize= to increase \nthe initial "
                  "size of the read write space.\n");
        }
        exit(2);
      }
      if (PrintSharedSpaces && Verbose && WizardMode) {
        tty->print_cr("\nMoveMarkedObjects: " PTR_FORMAT " -> " PTR_FORMAT " %s", obj, sh_obj,
                      (_read_only ? "ro" : "rw"));
      }
      Copy::aligned_disjoint_words((HeapWord*)obj, (HeapWord*)sh_obj, s);
      obj->forward_to(sh_obj);
      if (_read_only) {
        // Readonly objects: set hash value to self pointer and make gc_marked.
        sh_obj->forward_to(sh_obj);
      } else {
        sh_obj->init_mark();
      }
    }
  }
};

static void mark_and_move(oop obj, MoveMarkedObjects* move) {
  if (mark_object(obj)) move->do_object(obj);
}

enum order_policy {
  OP_favor_startup = 0,
  OP_balanced = 1,
  OP_favor_runtime = 2
};

static void mark_and_move_for_policy(order_policy policy, oop obj, MoveMarkedObjects* move) {
  if (SharedOptimizeColdStartPolicy >= policy) mark_and_move(obj, move);
}

class MarkAndMoveOrderedReadOnly : public ObjectClosure {
private:
  MoveMarkedObjects *_move_ro;

public:
  MarkAndMoveOrderedReadOnly(MoveMarkedObjects *move_ro) : _move_ro(move_ro) {}

  void do_object(oop obj) {
    if (obj->is_klass() && obj->blueprint()->oop_is_instanceKlass()) {
      instanceKlass* ik = instanceKlass::cast((klassOop)obj);
      int i;

      mark_and_move_for_policy(OP_favor_startup, ik->name(), _move_ro);

      if (ik->super() != NULL) {
        do_object(ik->super());
      }

      objArrayOop interfaces = ik->local_interfaces();
      mark_and_move_for_policy(OP_favor_startup, interfaces, _move_ro);
      for(i = 0; i < interfaces->length(); i++) {
        klassOop k = klassOop(interfaces->obj_at(i));
        mark_and_move_for_policy(OP_favor_startup, k->klass_part()->name(), _move_ro);
        do_object(k);
      }

      objArrayOop methods = ik->methods();
      for(i = 0; i < methods->length(); i++) {
        methodOop m = methodOop(methods->obj_at(i));
        mark_and_move_for_policy(OP_favor_startup, m->constMethod(), _move_ro);
        mark_and_move_for_policy(OP_favor_runtime, m->constMethod()->exception_table(), _move_ro);
        mark_and_move_for_policy(OP_favor_runtime, m->constMethod()->stackmap_data(), _move_ro);

        // We don't move the name symbolOop here because it may invalidate
        // method ordering, which is dependent on the address of the name
        // symbolOop.  It will get promoted later with the other symbols.
        // Method name is rarely accessed during classloading anyway.
        // mark_and_move_for_policy(OP_balanced, m->name(), _move_ro);

        mark_and_move_for_policy(OP_favor_startup, m->signature(), _move_ro);
      }

      mark_and_move_for_policy(OP_favor_startup, ik->transitive_interfaces(), _move_ro);
      mark_and_move_for_policy(OP_favor_startup, ik->fields(), _move_ro);

      mark_and_move_for_policy(OP_favor_runtime, ik->secondary_supers(),  _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->method_ordering(),   _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->class_annotations(), _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->fields_annotations(), _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->methods_annotations(), _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->methods_parameter_annotations(), _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->methods_default_annotations(), _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->inner_classes(), _move_ro);
      mark_and_move_for_policy(OP_favor_runtime, ik->secondary_supers(), _move_ro);
    }
  }
};

class MarkAndMoveOrderedReadWrite: public ObjectClosure {
private:
  MoveMarkedObjects *_move_rw;

public:
  MarkAndMoveOrderedReadWrite(MoveMarkedObjects *move_rw) : _move_rw(move_rw) {}

  void do_object(oop obj) {
    if (obj->is_klass() && obj->blueprint()->oop_is_instanceKlass()) {
      instanceKlass* ik = instanceKlass::cast((klassOop)obj);
      int i;

      mark_and_move_for_policy(OP_favor_startup, ik->as_klassOop(), _move_rw);

      if (ik->super() != NULL) {
        do_object(ik->super());
      }

      objArrayOop interfaces = ik->local_interfaces();
      for(i = 0; i < interfaces->length(); i++) {
        klassOop k = klassOop(interfaces->obj_at(i));
        mark_and_move_for_policy(OP_favor_startup, k, _move_rw);
        do_object(k);
      }

      objArrayOop methods = ik->methods();
      mark_and_move_for_policy(OP_favor_startup, methods, _move_rw);
      for(i = 0; i < methods->length(); i++) {
        methodOop m = methodOop(methods->obj_at(i));
        mark_and_move_for_policy(OP_favor_startup, m, _move_rw);
        mark_and_move_for_policy(OP_favor_startup, ik->constants(), _move_rw);          // idempotent
        mark_and_move_for_policy(OP_balanced, ik->constants()->cache(), _move_rw); // idempotent
        mark_and_move_for_policy(OP_balanced, ik->constants()->tags(), _move_rw);  // idempotent
      }

      mark_and_move_for_policy(OP_favor_startup, ik->as_klassOop()->klass(), _move_rw);
      mark_and_move_for_policy(OP_favor_startup, ik->constants()->klass(), _move_rw);

      // Although Java mirrors are marked in MarkReadWriteObjects,
      // apparently they were never moved into shared spaces since
      // MoveMarkedObjects skips marked instance oops.  This may
      // be a bug in the original implementation or simply the vestige
      // of an abandoned experiment.  Nevertheless we leave a hint
      // here in case this capability is ever correctly implemented.
      //
      // mark_and_move_for_policy(OP_favor_runtime, ik->java_mirror(), _move_rw);
    }
  }

};

// Adjust references in oops to refer to shared spaces.

class ResolveForwardingClosure: public OopClosure {
public:
  void do_oop(oop* p) {
    oop obj = *p;
    if (!obj->is_shared()) {
      if (obj != NULL) {
        oop f = obj->forwardee();
        guarantee(f->is_shared(), "Oop doesn't refer to shared space.");
        *p = f;
      }
    }
  }
  void do_oop(narrowOop* pobj) { ShouldNotReachHere(); }
};


void sort_methods(instanceKlass* ik, TRAPS) {
  klassOop super = ik->super();
  if (super != NULL) {
    sort_methods(instanceKlass::cast(super), THREAD);
  }

  // The methods array must be ordered by symbolOop address. (See
  // classFileParser.cpp where methods in a class are originally
  // sorted.)  Since objects have just be reordered, this must be
  // corrected.
  methodOopDesc::sort_methods(ik->methods(),
                              ik->methods_annotations(),
                              ik->methods_parameter_annotations(),
                              ik->methods_default_annotations(),
                              true /* idempotent, slow */);

  // Itable indices are calculated based on methods array order
  // (see klassItable::compute_itable_index()).  Must reinitialize.
  // We assume that since checkconstraints is false, this method
  // cannot throw an exception.  An exception here would be
  // problematic since this is the VMThread, not a JavaThread.
  ik->itable()->initialize_itable(false, THREAD);
}

// Sort methods if the oop is an instanceKlass.

class SortMethodsClosure: public ObjectClosure {
private:
  Thread* _thread;

public:
  SortMethodsClosure(Thread* thread) : _thread(thread) {}

  void do_object(oop obj) {
    // instanceKlass objects need some adjustment.
    if (obj->blueprint()->oop_is_instanceKlass()) {
      instanceKlass* ik = instanceKlass::cast((klassOop)obj);

      sort_methods(ik, _thread);
    }
  }
};


// Adjust references in oops to refer to shared spaces.

class PatchOopsClosure: public ObjectClosure {
private:
  Thread* _thread;
  ResolveForwardingClosure resolve;

public:
  PatchOopsClosure(Thread* thread) : _thread(thread) {}

  void do_object(oop obj) {
    obj->oop_iterate_header(&resolve);
    obj->oop_iterate(&resolve);

    assert(obj->klass()->is_shared(), "Klass not pointing into shared space.");

    // If the object is a Java object or class which might (in the
    // future) contain a reference to a young gen object, add it to the
    // list.

    if (obj->is_klass() || obj->is_instance()) {
      if (obj->is_klass() ||
          obj->is_a(SystemDictionary::Class_klass()) ||
          obj->is_a(SystemDictionary::Throwable_klass())) {
        // Do nothing
      }
      else if (obj->is_a(SystemDictionary::String_klass())) {
        // immutable objects.
      } else {
        // someone added an object we hadn't accounted for.
        ShouldNotReachHere();
      }
    }
  }
};


// Empty the young and old generations.

class ClearSpaceClosure : public SpaceClosure {
public:
  void do_space(Space* s) {
    s->clear(SpaceDecorator::Mangle);
  }
};


// Closure for serializing initialization data out to a data area to be
// written to the shared file.

class WriteClosure : public SerializeOopClosure {
private:
  oop* top;
  char* end;

  void out_of_space() {
    warning("\nThe shared miscellaneous data space is not large "
            "enough to \npreload requested classes.  Use "
            "-XX:SharedMiscDataSize= to increase \nthe initial "
            "size of the miscellaneous data space.\n");
    exit(2);
  }


  inline void check_space() {
    if ((char*)top + sizeof(oop) > end) {
      out_of_space();
    }
  }


public:
  WriteClosure(char* md_top, char* md_end) {
    top = (oop*)md_top;
    end = md_end;
  }

  char* get_top() { return (char*)top; }

  void do_oop(oop* p) {
    check_space();
    oop obj = *p;
    assert(obj->is_oop_or_null(), "invalid oop");
    assert(obj == NULL || obj->is_shared(),
           "Oop in shared space not pointing into shared space.");
    *top = obj;
    ++top;
  }

  void do_oop(narrowOop* pobj) { ShouldNotReachHere(); }

  void do_int(int* p) {
    check_space();
    *top = (oop)(intptr_t)*p;
    ++top;
  }

  void do_size_t(size_t* p) {
    check_space();
    *top = (oop)(intptr_t)*p;
    ++top;
  }

  void do_ptr(void** p) {
    check_space();
    *top = (oop)*p;
    ++top;
  }

  void do_ptr(HeapWord** p) { do_ptr((void **) p); }

  void do_tag(int tag) {
    check_space();
    *top = (oop)(intptr_t)tag;
    ++top;
  }

  void do_region(u_char* start, size_t size) {
    if ((char*)top + size > end) {
      out_of_space();
    }
    assert((intptr_t)start % sizeof(oop) == 0, "bad alignment");
    assert(size % sizeof(oop) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      *top = *(oop*)start;
      ++top;
      start += sizeof(oop);
      size -= sizeof(oop);
    }
  }

  bool reading() const { return false; }
};


class ResolveConstantPoolsClosure : public ObjectClosure {
private:
  TRAPS;
public:
  ResolveConstantPoolsClosure(Thread *t) {
    __the_thread__ = t;
  }
  void do_object(oop obj) {
    if (obj->is_constantPool()) {
      constantPoolOop cpool = (constantPoolOop)obj;
      int unresolved = cpool->pre_resolve_shared_klasses(THREAD);
    }
  }
};


// Print a summary of the contents of the read/write spaces to help
// identify objects which might be able to be made read-only.  At this
// point, the objects have been written, and we can trash them as
// needed.

static void print_contents() {
  if (PrintSharedSpaces) {
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    CompactingPermGenGen* gen = (CompactingPermGenGen*)gch->perm_gen();

    // High level summary of the read-only space:

    ClassifyObjectClosure coc;
    tty->cr(); tty->print_cr("ReadOnly space:");
    gen->ro_space()->object_iterate(&coc);
    coc.print();

    // High level summary of the read-write space:

    coc.reset();
    tty->cr(); tty->print_cr("ReadWrite space:");
    gen->rw_space()->object_iterate(&coc);
    coc.print();

    // Reset counters

    ClearAllocCountClosure cacc;
    gen->ro_space()->object_iterate(&cacc);
    gen->rw_space()->object_iterate(&cacc);
    coc.reset();

    // Lower level summary of the read-only space:

    gen->ro_space()->object_iterate(&coc);
    tty->cr(); tty->print_cr("ReadOnly space:");
    ClassifyInstanceKlassClosure cikc;
    gen->rw_space()->object_iterate(&cikc);
    cikc.print();

    // Reset counters

    gen->ro_space()->object_iterate(&cacc);
    gen->rw_space()->object_iterate(&cacc);
    coc.reset();

    // Lower level summary of the read-write space:

    gen->rw_space()->object_iterate(&coc);
    cikc.reset();
    tty->cr();  tty->print_cr("ReadWrite space:");
    gen->rw_space()->object_iterate(&cikc);
    cikc.print();
  }
}


// Patch C++ vtable pointer in klass oops.

// Klass objects contain references to c++ vtables in the JVM library.
// Fix them to point to our constructed vtables.  However, don't iterate
// across the space while doing this, as that causes the vtables to be
// patched, undoing our useful work.  Instead, iterate to make a list,
// then use the list to do the fixing.
//
// Our constructed vtables:
// Dump time:
//  1. init_self_patching_vtbl_list: table of pointers to current virtual method addrs
//  2. generate_vtable_methods: create jump table, appended to above vtbl_list
//  3. PatchKlassVtables: for Klass list, patch the vtable entry to point to jump table
//     rather than to current vtbl
// Table layout: NOTE FIXED SIZE
//   1. vtbl pointers
//   2. #Klass X #virtual methods per Klass
//   1 entry for each, in the order:
//   Klass1:method1 entry, Klass1:method2 entry, ... Klass1:method<num_virtuals> entry
//   Klass2:method1 entry, Klass2:method2 entry, ... Klass2:method<num_virtuals> entry
//   ...
//   Klass<vtbl_list_size>:method1 entry, Klass<vtbl_list_size>:method2 entry,
//       ... Klass<vtbl_list_size>:method<num_virtuals> entry
//  Sample entry: (Sparc):
//   save(sp, -256, sp)
//   ba,pt common_code
//   mov XXX, %L0       %L0 gets: Klass index <<8 + method index (note: max method index 255)
//
// Restore time:
//   1. initialize_oops: reserve space for table
//   2. init_self_patching_vtbl_list: update pointers to NEW virtual method addrs in text
//
// Execution time:
//   First virtual method call for any object of these Klass types:
//   1. object->klass->klass_part
//   2. vtable entry for that klass_part points to the jump table entries
//   3. branches to common_code with %O0/klass_part, %L0: Klass index <<8 + method index
//   4. common_code:
//      Get address of new vtbl pointer for this Klass from updated table
//      Update new vtbl pointer in the Klass: future virtual calls go direct
//      Jump to method, using new vtbl pointer and method index

class PatchKlassVtables: public ObjectClosure {
private:
  void*         _vtbl_ptr;
  VirtualSpace* _md_vs;
  GrowableArray<klassOop>* _klass_objects;

public:

  PatchKlassVtables(void* vtbl_ptr, VirtualSpace* md_vs) {
    _vtbl_ptr = vtbl_ptr;
    _md_vs = md_vs;
    _klass_objects = new GrowableArray<klassOop>();
  }


  void do_object(oop obj) {
    if (obj->is_klass()) {
      _klass_objects->append(klassOop(obj));
    }
  }


  void patch(void** vtbl_list, int vtbl_list_size) {
    for (int i = 0; i < _klass_objects->length(); ++i) {
      klassOop obj = (klassOop)_klass_objects->at(i);
      Klass* k = obj->klass_part();
      void* v =  *(void**)k;

      int n;
      for (n = 0; n < vtbl_list_size; ++n) {
        *(void**)k = NULL;
        if (vtbl_list[n] == v) {
          *(void**)k = (void**)_vtbl_ptr +
                                 (n * CompactingPermGenGen::num_virtuals);
          break;
        }
      }
      guarantee(n < vtbl_list_size, "unable to find matching vtbl pointer");
    }
  }
};


// Populate the shared space.

class VM_PopulateDumpSharedSpace: public VM_Operation {
private:
  GrowableArray<oop> *_class_promote_order;
  OffsetTableContigSpace* _ro_space;
  OffsetTableContigSpace* _rw_space;
  VirtualSpace* _md_vs;
  VirtualSpace* _mc_vs;

public:
  VM_PopulateDumpSharedSpace(GrowableArray<oop> *class_promote_order,
                             OffsetTableContigSpace* ro_space,
                             OffsetTableContigSpace* rw_space,
                             VirtualSpace* md_vs, VirtualSpace* mc_vs) {
    _class_promote_order = class_promote_order;
    _ro_space = ro_space;
    _rw_space = rw_space;
    _md_vs = md_vs;
    _mc_vs = mc_vs;
  }

  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit() {
    Thread* THREAD = VMThread::vm_thread();
    NOT_PRODUCT(SystemDictionary::verify();)
    // The following guarantee is meant to ensure that no loader constraints
    // exist yet, since the constraints table is not shared.  This becomes
    // more important now that we don't re-initialize vtables/itables for
    // shared classes at runtime, where constraints were previously created.
    guarantee(SystemDictionary::constraints()->number_of_entries() == 0,
              "loader constraints are not saved");
    // Revisit and implement this if we prelink method handle call sites:
    guarantee(SystemDictionary::invoke_method_table() == NULL ||
              SystemDictionary::invoke_method_table()->number_of_entries() == 0,
              "invoke method table is not saved");
    GenCollectedHeap* gch = GenCollectedHeap::heap();

    // At this point, many classes have been loaded.

    // Update all the fingerprints in the shared methods.

    tty->print("Calculating fingerprints ... ");
    FingerprintMethodsClosure fpmc;
    gch->object_iterate(&fpmc);
    tty->print_cr("done. ");

    // Remove all references outside the heap.

    tty->print("Removing unshareable information ... ");
    RemoveUnshareableInfoClosure ruic;
    gch->object_iterate(&ruic);
    tty->print_cr("done. ");

    // Move the objects in three passes.

    MarkObjectsOopClosure mark_all;
    MarkCommonReadOnly mark_common_ro;
    MarkCommonSymbols mark_common_symbols;
    MarkStringValues mark_string_values;
    MarkReadWriteObjects mark_rw;
    MarkStringObjects mark_strings;
    MoveMarkedObjects move_ro(_ro_space, true);
    MoveMarkedObjects move_rw(_rw_space, false);

    // The SharedOptimizeColdStart VM option governs the new layout
    // algorithm for promoting classes into the shared archive.
    // The general idea is to minimize cold start time by laying
    // out the objects in the order they are accessed at startup time.
    // By doing this we are trying to eliminate out-of-order accesses
    // in the shared archive.  This benefits cold startup time by making
    // disk reads as sequential as possible during class loading and
    // bootstrapping activities.  There may also be a small secondary
    // effect of better "packing" of more commonly used data on a smaller
    // number of pages, although no direct benefit has been measured from
    // this effect.
    //
    // At the class level of granularity, the promotion order is dictated
    // by the classlist file whose generation is discussed elsewhere.
    //
    // At smaller granularity, optimal ordering was determined by an
    // offline analysis of object access order in the shared archive.
    // The dbx watchpoint facility, combined with SA post-processing,
    // was used to observe common access patterns primarily during
    // classloading.  This information was used to craft the promotion
    // order seen in the following closures.
    //
    // The observed access order is mostly governed by what happens
    // in SystemDictionary::load_shared_class().  NOTE WELL - care
    // should be taken when making changes to this method, because it
    // may invalidate assumptions made about access order!
    //
    // (Ideally, there would be a better way to manage changes to
    //  the access order.  Unfortunately a generic in-VM solution for
    //  dynamically observing access order and optimizing shared
    //  archive layout is pretty difficult.  We go with the static
    //  analysis because the code is fairly mature at this point
    //  and we're betting that the access order won't change much.)

    MarkAndMoveOrderedReadOnly  mark_and_move_ordered_ro(&move_ro);
    MarkAndMoveOrderedReadWrite mark_and_move_ordered_rw(&move_rw);

    // Phase 1a: move commonly used read-only objects to the read-only space.

    if (SharedOptimizeColdStart) {
      tty->print("Moving pre-ordered read-only objects to shared space at " PTR_FORMAT " ... ",
                 _ro_space->top());
      for (int i = 0; i < _class_promote_order->length(); i++) {
        oop obj = _class_promote_order->at(i);
        mark_and_move_ordered_ro.do_object(obj);
      }
      tty->print_cr("done. ");
    }

    tty->print("Moving read-only objects to shared space at " PTR_FORMAT " ... ",
               _ro_space->top());
    gch->object_iterate(&mark_common_ro);
    gch->object_iterate(&move_ro);
    tty->print_cr("done. ");

    // Phase 1b: move commonly used symbols to the read-only space.

    tty->print("Moving common symbols to shared space at " PTR_FORMAT " ... ",
               _ro_space->top());
    gch->object_iterate(&mark_common_symbols);
    gch->object_iterate(&move_ro);
    tty->print_cr("done. ");

    // Phase 1c: move remaining symbols to the read-only space
    // (e.g. String initializers).

    tty->print("Moving remaining symbols to shared space at " PTR_FORMAT " ... ",
               _ro_space->top());
    vmSymbols::oops_do(&mark_all, true);
    gch->object_iterate(&move_ro);
    tty->print_cr("done. ");

    // Phase 1d: move String character arrays to the read-only space.

    tty->print("Moving string char arrays to shared space at " PTR_FORMAT " ... ",
               _ro_space->top());
    gch->object_iterate(&mark_string_values);
    gch->object_iterate(&move_ro);
    tty->print_cr("done. ");

    // Phase 2: move all remaining symbols to the read-only space.  The
    // remaining symbols are assumed to be string initializers no longer
    // referenced.

    void* extra_symbols = _ro_space->top();
    tty->print("Moving additional symbols to shared space at " PTR_FORMAT " ... ",
               _ro_space->top());
    SymbolTable::oops_do(&mark_all);
    gch->object_iterate(&move_ro);
    tty->print_cr("done. ");
    tty->print_cr("Read-only space ends at " PTR_FORMAT ", %d bytes.",
                  _ro_space->top(), _ro_space->used());

    // Phase 3: move read-write objects to the read-write space, except
    // Strings.

    if (SharedOptimizeColdStart) {
      tty->print("Moving pre-ordered read-write objects to shared space at " PTR_FORMAT " ... ",
                 _rw_space->top());
      for (int i = 0; i < _class_promote_order->length(); i++) {
        oop obj = _class_promote_order->at(i);
        mark_and_move_ordered_rw.do_object(obj);
      }
      tty->print_cr("done. ");
    }
    tty->print("Moving read-write objects to shared space at " PTR_FORMAT " ... ",
               _rw_space->top());
    Universe::oops_do(&mark_all, true);
    SystemDictionary::oops_do(&mark_all);
    oop tmp = Universe::arithmetic_exception_instance();
    mark_object(java_lang_Throwable::message(tmp));
    gch->object_iterate(&mark_rw);
    gch->object_iterate(&move_rw);
    tty->print_cr("done. ");

    // Phase 4: move String objects to the read-write space.

    tty->print("Moving String objects to shared space at " PTR_FORMAT " ... ",
               _rw_space->top());
    StringTable::oops_do(&mark_all);
    gch->object_iterate(&mark_strings);
    gch->object_iterate(&move_rw);
    tty->print_cr("done. ");
    tty->print_cr("Read-write space ends at " PTR_FORMAT ", %d bytes.",
                  _rw_space->top(), _rw_space->used());

#ifdef DEBUG
    // Check: scan for objects which were not moved.

    CheckRemainingObjects check_objects;
    gch->object_iterate(&check_objects);
    check_objects.status();
#endif

    // Resolve forwarding in objects and saved C++ structures
    tty->print("Updating references to shared objects ... ");
    ResolveForwardingClosure resolve;
    Universe::oops_do(&resolve);
    SystemDictionary::oops_do(&resolve);
    StringTable::oops_do(&resolve);
    SymbolTable::oops_do(&resolve);
    vmSymbols::oops_do(&resolve);

    // Set up the share data and shared code segments.

    char* md_top = _md_vs->low();
    char* md_end = _md_vs->high();
    char* mc_top = _mc_vs->low();
    char* mc_end = _mc_vs->high();

    // Reserve space for the list of klassOops whose vtables are used
    // for patching others as needed.

    void** vtbl_list = (void**)md_top;
    int vtbl_list_size = CompactingPermGenGen::vtbl_list_size;
    Universe::init_self_patching_vtbl_list(vtbl_list, vtbl_list_size);

    md_top += vtbl_list_size * sizeof(void*);
    void* vtable = md_top;

    // Reserve space for a new dummy vtable for klass objects in the
    // heap.  Generate self-patching vtable entries.

    CompactingPermGenGen::generate_vtable_methods(vtbl_list,
                                                  &vtable,
                                                  &md_top, md_end,
                                                  &mc_top, mc_end);

    // Fix (forward) all of the references in these shared objects (which
    // are required to point ONLY to objects in the shared spaces).
    // Also, create a list of all objects which might later contain a
    // reference to a younger generation object.

    CompactingPermGenGen* gen = (CompactingPermGenGen*)gch->perm_gen();
    PatchOopsClosure patch(THREAD);
    gen->ro_space()->object_iterate(&patch);
    gen->rw_space()->object_iterate(&patch);

    // Previously method sorting was done concurrently with forwarding
    // pointer resolution in the shared spaces.  This imposed an ordering
    // restriction in that methods were required to be promoted/patched
    // before their holder classes.  (Because constant pool pointers in
    // methodKlasses are required to be resolved before their holder class
    // is visited for sorting, otherwise methods are sorted by incorrect,
    // pre-forwarding addresses.)
    //
    // Now, we reorder methods as a separate step after ALL forwarding
    // pointer resolution, so that methods can be promoted in any order
    // with respect to their holder classes.

    SortMethodsClosure sort(THREAD);
    gen->ro_space()->object_iterate(&sort);
    gen->rw_space()->object_iterate(&sort);
    tty->print_cr("done. ");
    tty->cr();

    // Reorder the system dictionary.  (Moving the symbols opps affects
    // how the hash table indices are calculated.)

    SystemDictionary::reorder_dictionary();

    // Empty the non-shared heap (because most of the objects were
    // copied out, and the remainder cannot be considered valid oops).

    ClearSpaceClosure csc;
    for (int i = 0; i < gch->n_gens(); ++i) {
      gch->get_gen(i)->space_iterate(&csc);
    }
    csc.do_space(gen->the_space());
    NOT_PRODUCT(SystemDictionary::verify();)

    // Copy the String table, the symbol table, and the system
    // dictionary to the shared space in usable form.  Copy the hastable
    // buckets first [read-write], then copy the linked lists of entries
    // [read-only].

    SymbolTable::reverse(extra_symbols);
    NOT_PRODUCT(SymbolTable::verify());
    SymbolTable::copy_buckets(&md_top, md_end);

    StringTable::reverse();
    NOT_PRODUCT(StringTable::verify());
    StringTable::copy_buckets(&md_top, md_end);

    SystemDictionary::reverse();
    SystemDictionary::copy_buckets(&md_top, md_end);

    ClassLoader::verify();
    ClassLoader::copy_package_info_buckets(&md_top, md_end);
    ClassLoader::verify();

    SymbolTable::copy_table(&md_top, md_end);
    StringTable::copy_table(&md_top, md_end);
    SystemDictionary::copy_table(&md_top, md_end);
    ClassLoader::verify();
    ClassLoader::copy_package_info_table(&md_top, md_end);
    ClassLoader::verify();

    // Print debug data.

    if (PrintSharedSpaces) {
      const char* fmt = "%s space: " PTR_FORMAT " out of " PTR_FORMAT " bytes allocated at " PTR_FORMAT ".";
      tty->print_cr(fmt, "ro", _ro_space->used(), _ro_space->capacity(),
                    _ro_space->bottom());
      tty->print_cr(fmt, "rw", _rw_space->used(), _rw_space->capacity(),
                    _rw_space->bottom());
    }

    // Write the oop data to the output array.

    WriteClosure wc(md_top, md_end);
    CompactingPermGenGen::serialize_oops(&wc);
    md_top = wc.get_top();

    // Update the vtable pointers in all of the Klass objects in the
    // heap. They should point to newly generated vtable.

    PatchKlassVtables pkvt(vtable, _md_vs);
    _rw_space->object_iterate(&pkvt);
    pkvt.patch(vtbl_list, vtbl_list_size);

    char* saved_vtbl = (char*)malloc(vtbl_list_size * sizeof(void*));
    memmove(saved_vtbl, vtbl_list, vtbl_list_size * sizeof(void*));
    memset(vtbl_list, 0, vtbl_list_size * sizeof(void*));

    // Create and write the archive file that maps the shared spaces.

    FileMapInfo* mapinfo = new FileMapInfo();
    mapinfo->populate_header(gch->gen_policy()->max_alignment());

    // Pass 1 - update file offsets in header.
    mapinfo->write_header();
    mapinfo->write_space(CompactingPermGenGen::ro, _ro_space, true);
    _ro_space->set_saved_mark();
    mapinfo->write_space(CompactingPermGenGen::rw, _rw_space, false);
    _rw_space->set_saved_mark();
    mapinfo->write_region(CompactingPermGenGen::md, _md_vs->low(),
                          pointer_delta(md_top, _md_vs->low(), sizeof(char)),
                          SharedMiscDataSize,
                          false, false);
    mapinfo->write_region(CompactingPermGenGen::mc, _mc_vs->low(),
                          pointer_delta(mc_top, _mc_vs->low(), sizeof(char)),
                          SharedMiscCodeSize,
                          true, true);

    // Pass 2 - write data.
    mapinfo->open_for_write();
    mapinfo->write_header();
    mapinfo->write_space(CompactingPermGenGen::ro, _ro_space, true);
    mapinfo->write_space(CompactingPermGenGen::rw, _rw_space, false);
    mapinfo->write_region(CompactingPermGenGen::md, _md_vs->low(),
                          pointer_delta(md_top, _md_vs->low(), sizeof(char)),
                          SharedMiscDataSize,
                          false, false);
    mapinfo->write_region(CompactingPermGenGen::mc, _mc_vs->low(),
                          pointer_delta(mc_top, _mc_vs->low(), sizeof(char)),
                          SharedMiscCodeSize,
                          true, true);
    mapinfo->close();

    // Summarize heap.
    memmove(vtbl_list, saved_vtbl, vtbl_list_size * sizeof(void*));
    print_contents();
  }
}; // class VM_PopulateDumpSharedSpace


// Populate the shared spaces and dump to a file.

jint CompactingPermGenGen::dump_shared(GrowableArray<oop>* class_promote_order, TRAPS) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();

  // Calculate hash values for all of the (interned) strings to avoid
  // writes to shared pages in the future.

  tty->print("Calculating hash values for String objects .. ");
  StringHashCodeClosure shcc(THREAD);
  StringTable::oops_do(&shcc);
  tty->print_cr("done. ");

  CompactingPermGenGen* gen = (CompactingPermGenGen*)gch->perm_gen();
  VM_PopulateDumpSharedSpace op(class_promote_order,
                                gen->ro_space(), gen->rw_space(),
                                gen->md_space(), gen->mc_space());
  VMThread::execute(&op);
  return JNI_OK;
}


class LinkClassesClosure : public ObjectClosure {
 private:
  Thread* THREAD;

 public:
  LinkClassesClosure(Thread* thread) : THREAD(thread) {}

  void do_object(oop obj) {
    if (obj->is_klass()) {
      Klass* k = Klass::cast((klassOop) obj);
      if (k->oop_is_instance()) {
        instanceKlass* ik = (instanceKlass*) k;
        // Link the class to cause the bytecodes to be rewritten and the
        // cpcache to be created.
        if (ik->get_init_state() < instanceKlass::linked) {
          ik->link_class(THREAD);
          guarantee(!HAS_PENDING_EXCEPTION, "exception in class rewriting");
        }

        // Create String objects from string initializer symbols.
        ik->constants()->resolve_string_constants(THREAD);
        guarantee(!HAS_PENDING_EXCEPTION, "exception resolving string constants");
      }
    }
  }
};


// Support for a simple checksum of the contents of the class list
// file to prevent trivial tampering. The algorithm matches that in
// the MakeClassList program used by the J2SE build process.
#define JSUM_SEED ((jlong)CONST64(0xcafebabebabecafe))
static jlong
jsum(jlong start, const char *buf, const int len)
{
    jlong h = start;
    char *p = (char *)buf, *e = p + len;
    while (p < e) {
        char c = *p++;
        if (c <= ' ') {
            /* Skip spaces and control characters */
            continue;
        }
        h = 31 * h + c;
    }
    return h;
}





// Preload classes from a list, populate the shared spaces and dump to a
// file.

void GenCollectedHeap::preload_and_dump(TRAPS) {
  TraceTime timer("Dump Shared Spaces", TraceStartupTime);
  ResourceMark rm;

  // Preload classes to be shared.
  // Should use some hpi:: method rather than fopen() here. aB.
  // Construct the path to the class list (in jre/lib)
  // Walk up two directories from the location of the VM and
  // optionally tack on "lib" (depending on platform)
  char class_list_path[JVM_MAXPATHLEN];
  os::jvm_path(class_list_path, sizeof(class_list_path));
  for (int i = 0; i < 3; i++) {
    char *end = strrchr(class_list_path, *os::file_separator());
    if (end != NULL) *end = '\0';
  }
  int class_list_path_len = (int)strlen(class_list_path);
  if (class_list_path_len >= 3) {
    if (strcmp(class_list_path + class_list_path_len - 3, "lib") != 0) {
      strcat(class_list_path, os::file_separator());
      strcat(class_list_path, "lib");
    }
  }
  strcat(class_list_path, os::file_separator());
  strcat(class_list_path, "classlist");

  FILE* file = fopen(class_list_path, "r");
  if (file != NULL) {
    jlong computed_jsum  = JSUM_SEED;
    jlong file_jsum      = 0;

    char class_name[256];
    int class_count = 0;
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    gch->_preloading_shared_classes = true;
    GrowableArray<oop>* class_promote_order = new GrowableArray<oop>();

    // Preload (and intern) strings which will be used later.

    StringTable::intern("main", THREAD);
    StringTable::intern("([Ljava/lang/String;)V", THREAD);
    StringTable::intern("Ljava/lang/Class;", THREAD);

    StringTable::intern("I", THREAD);   // Needed for StringBuffer persistence?
    StringTable::intern("Z", THREAD);   // Needed for StringBuffer persistence?

    // sun.io.Converters
    static const char obj_array_sig[] = "[[Ljava/lang/Object;";
    SymbolTable::lookup(obj_array_sig, (int)strlen(obj_array_sig), THREAD);

    // java.util.HashMap
    static const char map_entry_array_sig[] = "[Ljava/util/Map$Entry;";
    SymbolTable::lookup(map_entry_array_sig, (int)strlen(map_entry_array_sig),
                        THREAD);

    tty->print("Loading classes to share ... ");
    while ((fgets(class_name, sizeof class_name, file)) != NULL) {
      if (*class_name == '#') {
        jint fsh, fsl;
        if (sscanf(class_name, "# %8x%8x\n", &fsh, &fsl) == 2) {
          file_jsum = ((jlong)(fsh) << 32) | (fsl & 0xffffffff);
        }

        continue;
      }
      // Remove trailing newline
      size_t name_len = strlen(class_name);
      class_name[name_len-1] = '\0';

      computed_jsum = jsum(computed_jsum, class_name, (const int)name_len - 1);

      // Got a class name - load it.
      symbolHandle class_name_symbol = oopFactory::new_symbol(class_name,
                                                              THREAD);
      guarantee(!HAS_PENDING_EXCEPTION, "Exception creating a symbol.");
      klassOop klass = SystemDictionary::resolve_or_null(class_name_symbol,
                                                         THREAD);
      guarantee(!HAS_PENDING_EXCEPTION, "Exception resolving a class.");
      if (klass != NULL) {
        if (PrintSharedSpaces) {
          tty->print_cr("Shared spaces preloaded: %s", class_name);
        }


        instanceKlass* ik = instanceKlass::cast(klass);

        // Should be class load order as per -XX:+TraceClassLoadingPreorder
        class_promote_order->append(ik->as_klassOop());

        // Link the class to cause the bytecodes to be rewritten and the
        // cpcache to be created. The linking is done as soon as classes
        // are loaded in order that the related data structures (klass,
        // cpCache, Sting constants) are located together.

        if (ik->get_init_state() < instanceKlass::linked) {
          ik->link_class(THREAD);
          guarantee(!(HAS_PENDING_EXCEPTION), "exception in class rewriting");
        }

        // Create String objects from string initializer symbols.

        ik->constants()->resolve_string_constants(THREAD);

        class_count++;
      } else {
        if (PrintSharedSpaces) {
          tty->cr();
          tty->print_cr(" Preload failed: %s", class_name);
        }
      }
      file_jsum = 0; // Checksum must be on last line of file
    }
    if (computed_jsum != file_jsum) {
      tty->cr();
      tty->print_cr("Preload failed: checksum of class list was incorrect.");
      exit(1);
    }

    tty->print_cr("done. ");

    if (PrintSharedSpaces) {
      tty->print_cr("Shared spaces: preloaded %d classes", class_count);
    }

    // Rewrite and unlink classes.
    tty->print("Rewriting and unlinking classes ... ");
    // Make heap parsable
    ensure_parsability(false); // arg is actually don't care

    // Link any classes which got missed.  (It's not quite clear why
    // they got missed.)  This iteration would be unsafe if we weren't
    // single-threaded at this point; however we can't do it on the VM
    // thread because it requires object allocation.
    LinkClassesClosure lcc(Thread::current());
    object_iterate(&lcc);
    tty->print_cr("done. ");

    // Create and dump the shared spaces.
    jint err = CompactingPermGenGen::dump_shared(class_promote_order, THREAD);
    if (err != JNI_OK) {
      fatal("Dumping shared spaces failed.");
    }

  } else {
    char errmsg[JVM_MAXPATHLEN];
    hpi::lasterror(errmsg, JVM_MAXPATHLEN);
    tty->print_cr("Loading classlist failed: %s", errmsg);
    exit(1);
  }

  // Since various initialization steps have been undone by this process,
  // it is not reasonable to continue running a java process.
  exit(0);
}
