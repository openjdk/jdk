/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciField.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciInstanceKlass.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/vmClasses.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.inline.hpp"

// ciInstanceKlass
//
// This class represents a Klass* in the HotSpot virtual machine
// whose Klass part in an InstanceKlass.


// ------------------------------------------------------------------
// ciInstanceKlass::ciInstanceKlass
//
// Loaded instance klass.
ciInstanceKlass::ciInstanceKlass(Klass* k) :
  ciKlass(k)
{
  assert(get_Klass()->is_instance_klass(), "wrong type");
  assert(get_instanceKlass()->is_loaded(), "must be at least loaded");
  InstanceKlass* ik = get_instanceKlass();

  AccessFlags access_flags = ik->access_flags();
  _flags = ciFlags(access_flags);
  _has_finalizer = ik->has_finalizer();
  _has_subklass = flags().is_final() ? subklass_false : subklass_unknown;
  _init_state = ik->init_state();
  _has_nonstatic_fields = ik->has_nonstatic_fields();
  _has_nonstatic_concrete_methods = ik->has_nonstatic_concrete_methods();
  _is_hidden = ik->is_hidden();
  _is_record = ik->is_record();
  _nonstatic_fields = nullptr; // initialized lazily by compute_nonstatic_fields:
  _has_injected_fields = -1;
  _implementor = nullptr; // we will fill these lazily
  _transitive_interfaces = nullptr;

  // Ensure that the metadata wrapped by the ciMetadata is kept alive by GC.
  // This is primarily useful for metadata which is considered as weak roots
  // by the GC but need to be strong roots if reachable from a current compilation.
  // InstanceKlass are created for both weak and strong metadata.  Ensuring this metadata
  // alive covers the cases where there are weak roots without performance cost.
  oop holder = ik->klass_holder();
  if (ik->class_loader_data()->has_class_mirror_holder()) {
    // Though ciInstanceKlass records class loader oop, it's not enough to keep
    // non-strong hidden classes alive (loader == nullptr). Klass holder should
    // be used instead. It is enough to record a ciObject, since cached elements are never removed
    // during ciObjectFactory lifetime. ciObjectFactory itself is created for
    // every compilation and lives for the whole duration of the compilation.
    assert(holder != nullptr, "holder of hidden class is the mirror which is never null");
    (void)CURRENT_ENV->get_object(holder);
  }

  JavaThread *thread = JavaThread::current();
  if (ciObjectFactory::is_initialized()) {
    _loader = JNIHandles::make_local(thread, ik->class_loader());
    _is_shared = false;
  } else {
    Handle h_loader(thread, ik->class_loader());
    _loader = JNIHandles::make_global(h_loader);
    _is_shared = true;
  }

  _has_trusted_loader = compute_has_trusted_loader();

  // Lazy fields get filled in only upon request.
  _super  = nullptr;
  _java_mirror = nullptr;

  if (is_shared()) {
    if (k != vmClasses::Object_klass()) {
      super();
    }
    //compute_nonstatic_fields();  // done outside of constructor
  }

  _field_cache = nullptr;
}

// Version for unloaded classes:
ciInstanceKlass::ciInstanceKlass(ciSymbol* name,
                                 jobject loader)
  : ciKlass(name, T_OBJECT)
{
  assert(name->char_at(0) != JVM_SIGNATURE_ARRAY, "not an instance klass");
  _init_state = (InstanceKlass::ClassState)0;
  _has_nonstatic_fields = false;
  _nonstatic_fields = nullptr;
  _has_injected_fields = -1;
  _is_hidden = false;
  _is_record = false;
  _loader = loader;
  _is_shared = false;
  _super = nullptr;
  _java_mirror = nullptr;
  _field_cache = nullptr;
  _has_trusted_loader = compute_has_trusted_loader();
}



// ------------------------------------------------------------------
// ciInstanceKlass::compute_shared_is_initialized
void ciInstanceKlass::compute_shared_init_state() {
  GUARDED_VM_ENTRY(
    InstanceKlass* ik = get_instanceKlass();
    _init_state = ik->init_state();
  )
}

// ------------------------------------------------------------------
// ciInstanceKlass::compute_shared_has_subklass
bool ciInstanceKlass::compute_shared_has_subklass() {
  GUARDED_VM_ENTRY(
    InstanceKlass* ik = get_instanceKlass();
    _has_subklass = ik->subklass() != nullptr ? subklass_true : subklass_false;
    return _has_subklass == subklass_true;
  )
}

// ------------------------------------------------------------------
// ciInstanceKlass::loader
oop ciInstanceKlass::loader() {
  ASSERT_IN_VM;
  return JNIHandles::resolve(_loader);
}

// ------------------------------------------------------------------
// ciInstanceKlass::loader_handle
jobject ciInstanceKlass::loader_handle() {
  return _loader;
}

// ------------------------------------------------------------------
// ciInstanceKlass::field_cache
//
// Get the field cache associated with this klass.
ciConstantPoolCache* ciInstanceKlass::field_cache() {
  if (is_shared()) {
    return nullptr;
  }
  if (_field_cache == nullptr) {
    assert(!is_java_lang_Object(), "Object has no fields");
    Arena* arena = CURRENT_ENV->arena();
    _field_cache = new (arena) ciConstantPoolCache(arena, 5);
  }
  return _field_cache;
}

// ------------------------------------------------------------------
// ciInstanceKlass::get_canonical_holder
//
ciInstanceKlass* ciInstanceKlass::get_canonical_holder(int offset) {
  #ifdef ASSERT
  if (!(offset >= 0 && offset < layout_helper_size_in_bytes())) {
    tty->print("*** get_canonical_holder(%d) on ", offset);
    this->print();
    tty->print_cr(" ***");
  };
  assert(offset >= 0 && offset < layout_helper_size_in_bytes(), "offset must be tame");
  #endif

  if (offset < instanceOopDesc::base_offset_in_bytes()) {
    // All header offsets belong properly to java/lang/Object.
    return CURRENT_ENV->Object_klass();
  }

  ciInstanceKlass* self = this;
  assert(self->is_loaded(), "must be loaded to access field info");
  ciField* field = self->get_field_by_offset(offset, false);
  if (field != nullptr) {
    return field->holder();
  } else {
    for (;;) {
      assert(self->is_loaded(), "must be loaded to have size");
      ciInstanceKlass* super = self->super();
      if (super == nullptr ||
          super->nof_nonstatic_fields() == 0 ||
          super->layout_helper_size_in_bytes() <= offset) {
        return self;
      } else {
        self = super;  // return super->get_canonical_holder(offset)
      }
    }
  }
}

// ------------------------------------------------------------------
// ciInstanceKlass::is_java_lang_Object
//
// Is this klass java.lang.Object?
bool ciInstanceKlass::is_java_lang_Object() const {
  return equals(CURRENT_ENV->Object_klass());
}

// ------------------------------------------------------------------
// ciInstanceKlass::uses_default_loader
bool ciInstanceKlass::uses_default_loader() const {
  // Note:  We do not need to resolve the handle or enter the VM
  // in order to test null-ness.
  return _loader == nullptr;
}

// ------------------------------------------------------------------

/**
 * Return basic type of boxed value for box klass or T_OBJECT if not.
 */
BasicType ciInstanceKlass::box_klass_type() const {
  if (uses_default_loader() && is_loaded()) {
    return vmClasses::box_klass_type(get_Klass());
  } else {
    return T_OBJECT;
  }
}

/**
 * Is this boxing klass?
 */
bool ciInstanceKlass::is_box_klass() const {
  return is_java_primitive(box_klass_type());
}

/**
 *  Is this boxed value offset?
 */
bool ciInstanceKlass::is_boxed_value_offset(int offset) const {
  BasicType bt = box_klass_type();
  return is_java_primitive(bt) &&
         (offset == java_lang_boxing_object::value_offset(bt));
}

// ------------------------------------------------------------------
// ciInstanceKlass::is_in_package
//
// Is this klass in the given package?
bool ciInstanceKlass::is_in_package(const char* packagename, int len) {
  // To avoid class loader mischief, this test always rejects application classes.
  if (!uses_default_loader())
    return false;
  GUARDED_VM_ENTRY(
    return is_in_package_impl(packagename, len);
  )
}

bool ciInstanceKlass::is_in_package_impl(const char* packagename, int len) {
  ASSERT_IN_VM;

  // If packagename contains trailing '/' exclude it from the
  // prefix-test since we test for it explicitly.
  if (packagename[len - 1] == '/')
    len--;

  if (!name()->starts_with(packagename, len))
    return false;

  // Test if the class name is something like "java/lang".
  if ((len + 1) > name()->utf8_length())
    return false;

  // Test for trailing '/'
  if (name()->char_at(len) != '/')
    return false;

  // Make sure it's not actually in a subpackage:
  if (name()->index_of_at(len+1, "/", 1) >= 0)
    return false;

  return true;
}

// ------------------------------------------------------------------
// ciInstanceKlass::print_impl
//
// Implementation of the print method.
void ciInstanceKlass::print_impl(outputStream* st) {
  ciKlass::print_impl(st);
  GUARDED_VM_ENTRY(st->print(" loader=" INTPTR_FORMAT, p2i(loader()));)
  if (is_loaded()) {
    st->print(" initialized=%s finalized=%s subklass=%s size=%d flags=",
              bool_to_str(is_initialized()),
              bool_to_str(has_finalizer()),
              bool_to_str(has_subklass()),
              layout_helper());

    _flags.print_klass_flags();

    if (_super) {
      st->print(" super=");
      _super->print_name();
    }
    if (_java_mirror) {
      st->print(" mirror=PRESENT");
    }
  }
}

// ------------------------------------------------------------------
// ciInstanceKlass::super
//
// Get the superklass of this klass.
ciInstanceKlass* ciInstanceKlass::super() {
  assert(is_loaded(), "must be loaded");
  if (_super == nullptr && !is_java_lang_Object()) {
    GUARDED_VM_ENTRY(
      Klass* super_klass = get_instanceKlass()->super();
      _super = CURRENT_ENV->get_instance_klass(super_klass);
    )
  }
  return _super;
}

// ------------------------------------------------------------------
// ciInstanceKlass::java_mirror
//
// Get the instance of java.lang.Class corresponding to this klass.
// Cache it on this->_java_mirror.
ciInstance* ciInstanceKlass::java_mirror() {
  if (is_shared()) {
    return ciKlass::java_mirror();
  }
  if (_java_mirror == nullptr) {
    _java_mirror = ciKlass::java_mirror();
  }
  return _java_mirror;
}

// ------------------------------------------------------------------
// ciInstanceKlass::unique_concrete_subklass
ciInstanceKlass* ciInstanceKlass::unique_concrete_subklass() {
  if (!is_loaded())     return nullptr; // No change if class is not loaded
  if (!is_abstract())   return nullptr; // Only applies to abstract classes.
  if (!has_subklass())  return nullptr; // Must have at least one subklass.
  VM_ENTRY_MARK;
  InstanceKlass* ik = get_instanceKlass();
  Klass* up = ik->up_cast_abstract();
  assert(up->is_instance_klass(), "must be InstanceKlass");
  if (ik == up) {
    return nullptr;
  }
  return CURRENT_THREAD_ENV->get_instance_klass(up);
}

// ------------------------------------------------------------------
// ciInstanceKlass::has_finalizable_subclass
bool ciInstanceKlass::has_finalizable_subclass() {
  if (!is_loaded())     return true;
  VM_ENTRY_MARK;
  return Dependencies::find_finalizable_subclass(get_instanceKlass()) != nullptr;
}

// ------------------------------------------------------------------
// ciInstanceKlass::contains_field_offset
bool ciInstanceKlass::contains_field_offset(int offset) {
  VM_ENTRY_MARK;
  return get_instanceKlass()->contains_field_offset(offset);
}

// ------------------------------------------------------------------
// ciInstanceKlass::get_field_by_offset
ciField* ciInstanceKlass::get_field_by_offset(int field_offset, bool is_static) {
  if (!is_static) {
    for (int i = 0, len = nof_nonstatic_fields(); i < len; i++) {
      ciField* field = _nonstatic_fields->at(i);
      int  field_off = field->offset_in_bytes();
      if (field_off == field_offset)
        return field;
    }
    return nullptr;
  }
  VM_ENTRY_MARK;
  InstanceKlass* k = get_instanceKlass();
  fieldDescriptor fd;
  if (!k->find_field_from_offset(field_offset, is_static, &fd)) {
    return nullptr;
  }
  ciField* field = new (CURRENT_THREAD_ENV->arena()) ciField(&fd);
  return field;
}

// ------------------------------------------------------------------
// ciInstanceKlass::get_field_by_name
ciField* ciInstanceKlass::get_field_by_name(ciSymbol* name, ciSymbol* signature, bool is_static) {
  VM_ENTRY_MARK;
  InstanceKlass* k = get_instanceKlass();
  fieldDescriptor fd;
  Klass* def = k->find_field(name->get_symbol(), signature->get_symbol(), is_static, &fd);
  if (def == nullptr) {
    return nullptr;
  }
  ciField* field = new (CURRENT_THREAD_ENV->arena()) ciField(&fd);
  return field;
}


// ------------------------------------------------------------------
// ciInstanceKlass::compute_nonstatic_fields
int ciInstanceKlass::compute_nonstatic_fields() {
  assert(is_loaded(), "must be loaded");

  if (_nonstatic_fields != nullptr)
    return _nonstatic_fields->length();

  if (!has_nonstatic_fields()) {
    Arena* arena = CURRENT_ENV->arena();
    _nonstatic_fields = new (arena) GrowableArray<ciField*>(arena, 0, 0, nullptr);
    return 0;
  }
  assert(!is_java_lang_Object(), "bootstrap OK");

  ciInstanceKlass* super = this->super();
  GrowableArray<ciField*>* super_fields = nullptr;
  if (super != nullptr && super->has_nonstatic_fields()) {
    int super_flen   = super->nof_nonstatic_fields();
    super_fields = super->_nonstatic_fields;
    assert(super_flen == 0 || super_fields != nullptr, "first get nof_fields");
  }

  GrowableArray<ciField*>* fields = nullptr;
  GUARDED_VM_ENTRY({
      fields = compute_nonstatic_fields_impl(super_fields);
    });

  if (fields == nullptr) {
    // This can happen if this class (java.lang.Class) has invisible fields.
    if (super_fields != nullptr) {
      _nonstatic_fields = super_fields;
      return super_fields->length();
    } else {
      return 0;
    }
  }

  int flen = fields->length();

  _nonstatic_fields = fields;
  return flen;
}

GrowableArray<ciField*>*
ciInstanceKlass::compute_nonstatic_fields_impl(GrowableArray<ciField*>*
                                               super_fields) {
  ASSERT_IN_VM;
  Arena* arena = CURRENT_ENV->arena();
  int flen = 0;
  GrowableArray<ciField*>* fields = nullptr;
  InstanceKlass* k = get_instanceKlass();
  for (JavaFieldStream fs(k); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static())  continue;
    flen += 1;
  }

  // allocate the array:
  if (flen == 0) {
    return nullptr;  // return nothing if none are locally declared
  }
  if (super_fields != nullptr) {
    flen += super_fields->length();
  }
  fields = new (arena) GrowableArray<ciField*>(arena, flen, 0, nullptr);
  if (super_fields != nullptr) {
    fields->appendAll(super_fields);
  }

  for (JavaFieldStream fs(k); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static())  continue;
    fieldDescriptor& fd = fs.field_descriptor();
    ciField* field = new (arena) ciField(&fd);
    fields->append(field);
  }
  assert(fields->length() == flen, "sanity");
  return fields;
}

bool ciInstanceKlass::compute_injected_fields_helper() {
  ASSERT_IN_VM;
  InstanceKlass* k = get_instanceKlass();

  for (InternalFieldStream fs(k); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static())  continue;
    return true;
  }
  return false;
}

void ciInstanceKlass::compute_injected_fields() {
  assert(is_loaded(), "must be loaded");

  int has_injected_fields = 0;
  if (super() != nullptr && super()->has_injected_fields()) {
    has_injected_fields = 1;
  } else {
    GUARDED_VM_ENTRY({
        has_injected_fields = compute_injected_fields_helper() ? 1 : 0;
      });
  }
  // may be concurrently initialized for shared ciInstanceKlass objects
  assert(_has_injected_fields == -1 || _has_injected_fields == has_injected_fields, "broken concurrent initialization");
  _has_injected_fields = has_injected_fields;
}

bool ciInstanceKlass::has_object_fields() const {
  GUARDED_VM_ENTRY(
      return get_instanceKlass()->nonstatic_oop_map_size() > 0;
    );
}

bool ciInstanceKlass::compute_has_trusted_loader() {
  ASSERT_IN_VM;
  oop loader_oop = loader();
  if (loader_oop == nullptr) {
    return true; // bootstrap class loader
  }
  return java_lang_ClassLoader::is_trusted_loader(loader_oop);
}

bool ciInstanceKlass::has_class_initializer() {
  VM_ENTRY_MARK;
  return get_instanceKlass()->class_initializer() != nullptr;
}

// ------------------------------------------------------------------
// ciInstanceKlass::find_method
//
// Find a method in this klass.
ciMethod* ciInstanceKlass::find_method(ciSymbol* name, ciSymbol* signature) {
  VM_ENTRY_MARK;
  InstanceKlass* k = get_instanceKlass();
  Symbol* name_sym = name->get_symbol();
  Symbol* sig_sym= signature->get_symbol();

  Method* m = k->find_method(name_sym, sig_sym);
  if (m == nullptr)  return nullptr;

  return CURRENT_THREAD_ENV->get_method(m);
}

// ------------------------------------------------------------------
// ciInstanceKlass::is_leaf_type
bool ciInstanceKlass::is_leaf_type() {
  assert(is_loaded(), "must be loaded");
  if (is_shared()) {
    return is_final();  // approximately correct
  } else {
    return !has_subklass() && (nof_implementors() == 0);
  }
}

// ------------------------------------------------------------------
// ciInstanceKlass::implementor
//
// Report an implementor of this interface.
// Note that there are various races here, since my copy
// of _nof_implementors might be out of date with respect
// to results returned by InstanceKlass::implementor.
// This is OK, since any dependencies we decide to assert
// will be checked later under the Compile_lock.
ciInstanceKlass* ciInstanceKlass::implementor() {
  ciInstanceKlass* impl = _implementor;
  if (impl == nullptr) {
    if (is_shared()) {
      impl = this; // assume a well-known interface never has a unique implementor
    } else {
      // Go into the VM to fetch the implementor.
      VM_ENTRY_MARK;
      InstanceKlass* ik = get_instanceKlass();
      Klass* implk = ik->implementor();
      if (implk != nullptr) {
        if (implk == ik) {
          // More than one implementors. Use 'this' in this case.
          impl = this;
        } else {
          impl = CURRENT_THREAD_ENV->get_instance_klass(implk);
        }
      }
    }
    // Memoize this result.
    _implementor = impl;
  }
  return impl;
}

// Utility class for printing of the contents of the static fields for
// use by compilation replay.  It only prints out the information that
// could be consumed by the compiler, so for primitive types it prints
// out the actual value.  For Strings it's the actual string value.
// For array types it it's first level array size since that's the
// only value which statically unchangeable.  For all other reference
// types it simply prints out the dynamic type.

class StaticFinalFieldPrinter : public FieldClosure {
  outputStream* _out;
  const char*   _holder;
 public:
  StaticFinalFieldPrinter(outputStream* out, const char* holder) :
    _out(out),
    _holder(holder) {
  }
  void do_field(fieldDescriptor* fd) {
    if (fd->is_final() && !fd->has_initial_value()) {
      ResourceMark rm;
      oop mirror = fd->field_holder()->java_mirror();
      _out->print("staticfield %s %s %s ", _holder, fd->name()->as_quoted_ascii(), fd->signature()->as_quoted_ascii());
      BasicType field_type = fd->field_type();
      switch (field_type) {
        case T_BYTE:    _out->print_cr("%d", mirror->byte_field(fd->offset()));   break;
        case T_BOOLEAN: _out->print_cr("%d", mirror->bool_field(fd->offset()));   break;
        case T_SHORT:   _out->print_cr("%d", mirror->short_field(fd->offset()));  break;
        case T_CHAR:    _out->print_cr("%d", mirror->char_field(fd->offset()));   break;
        case T_INT:     _out->print_cr("%d", mirror->int_field(fd->offset()));    break;
        case T_LONG:    _out->print_cr(INT64_FORMAT, (int64_t)(mirror->long_field(fd->offset())));   break;
        case T_FLOAT: {
          float f = mirror->float_field(fd->offset());
          _out->print_cr("%d", *(int*)&f);
          break;
        }
        case T_DOUBLE: {
          double d = mirror->double_field(fd->offset());
          _out->print_cr(INT64_FORMAT, *(int64_t*)&d);
          break;
        }
        case T_ARRAY:  // fall-through
        case T_OBJECT: {
          oop value =  mirror->obj_field_acquire(fd->offset());
          if (value == nullptr) {
            if (field_type == T_ARRAY) {
              _out->print("%d", -1);
            }
            _out->cr();
          } else if (value->is_instance()) {
            assert(field_type == T_OBJECT, "");
            if (value->is_a(vmClasses::String_klass())) {
              const char* ascii_value = java_lang_String::as_quoted_ascii(value);
              _out->print_cr("\"%s\"", (ascii_value != nullptr) ? ascii_value : "");
            } else {
              const char* klass_name  = value->klass()->name()->as_quoted_ascii();
              _out->print_cr("%s", klass_name);
            }
          } else if (value->is_array()) {
            typeArrayOop ta = (typeArrayOop)value;
            _out->print("%d", ta->length());
            if (value->is_objArray()) {
              objArrayOop oa = (objArrayOop)value;
              const char* klass_name  = value->klass()->name()->as_quoted_ascii();
              _out->print(" %s", klass_name);
            }
            _out->cr();
          } else {
            ShouldNotReachHere();
          }
          break;
        }
        default:
          ShouldNotReachHere();
        }
    }
  }
};

const char *ciInstanceKlass::replay_name() const {
  return CURRENT_ENV->replay_name(get_instanceKlass());
}

void ciInstanceKlass::dump_replay_instanceKlass(outputStream* out, InstanceKlass* ik) {
  if (ik->is_hidden()) {
    const char *name = CURRENT_ENV->dyno_name(ik);
    if (name != nullptr) {
      out->print_cr("instanceKlass %s # %s", name, ik->name()->as_quoted_ascii());
    } else {
      out->print_cr("# instanceKlass %s", ik->name()->as_quoted_ascii());
    }
  } else {
    out->print_cr("instanceKlass %s", ik->name()->as_quoted_ascii());
  }
}

GrowableArray<ciInstanceKlass*>* ciInstanceKlass::transitive_interfaces() const{
  if (_transitive_interfaces == nullptr) {
    const_cast<ciInstanceKlass*>(this)->compute_transitive_interfaces();
  }
  return _transitive_interfaces;
}

void ciInstanceKlass::compute_transitive_interfaces() {
  GUARDED_VM_ENTRY(
          InstanceKlass* ik = get_instanceKlass();
          Array<InstanceKlass*>* interfaces = ik->transitive_interfaces();
          int orig_length = interfaces->length();
          Arena* arena = CURRENT_ENV->arena();
          int transitive_interfaces_len = orig_length + (is_interface() ? 1 : 0);
          GrowableArray<ciInstanceKlass*>* transitive_interfaces = new(arena)GrowableArray<ciInstanceKlass*>(arena, transitive_interfaces_len,
                                                                                                             0, nullptr);
          for (int i = 0; i < orig_length; i++) {
            transitive_interfaces->append(CURRENT_ENV->get_instance_klass(interfaces->at(i)));
          }
          if (is_interface()) {
            transitive_interfaces->append(this);
          }
          _transitive_interfaces = transitive_interfaces;
  );
}

void ciInstanceKlass::dump_replay_data(outputStream* out) {
  ResourceMark rm;

  InstanceKlass* ik = get_instanceKlass();
  ConstantPool*  cp = ik->constants();

  // Try to record related loaded classes
  Klass* sub = ik->subklass();
  while (sub != nullptr) {
    if (sub->is_instance_klass()) {
      InstanceKlass *isub = InstanceKlass::cast(sub);
      dump_replay_instanceKlass(out, isub);
    }
    sub = sub->next_sibling();
  }

  // Dump out the state of the constant pool tags.  During replay the
  // tags will be validated for things which shouldn't change and
  // classes will be resolved if the tags indicate that they were
  // resolved at compile time.
  const char *name = replay_name();
  out->print("ciInstanceKlass %s %d %d %d", name,
             is_linked(), is_initialized(), cp->length());
  for (int index = 1; index < cp->length(); index++) {
    out->print(" %d", cp->tags()->at(index));
  }
  out->cr();
  if (is_initialized()) {
    //  Dump out the static final fields in case the compilation relies
    //  on their value for correct replay.
    StaticFinalFieldPrinter sffp(out, name);
    ik->do_local_static_fields(&sffp);
  }
}

#ifdef ASSERT
bool ciInstanceKlass::debug_final_field_at(int offset) {
  GUARDED_VM_ENTRY(
    InstanceKlass* ik = get_instanceKlass();
    fieldDescriptor fd;
    if (ik->find_field_from_offset(offset, false, &fd)) {
      return fd.is_final();
    }
  );
  return false;
}

bool ciInstanceKlass::debug_stable_field_at(int offset) {
  GUARDED_VM_ENTRY(
    InstanceKlass* ik = get_instanceKlass();
    fieldDescriptor fd;
    if (ik->find_field_from_offset(offset, false, &fd)) {
      return fd.is_stable();
    }
  );
  return false;
}
#endif
