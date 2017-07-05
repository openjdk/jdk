/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciField.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciInstanceKlass.hpp"
#include "ci/ciUtilities.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/fieldStreams.hpp"
#include "runtime/fieldDescriptor.hpp"

// ciInstanceKlass
//
// This class represents a Klass* in the HotSpot virtual machine
// whose Klass part in an InstanceKlass.

// ------------------------------------------------------------------
// ciInstanceKlass::ciInstanceKlass
//
// Loaded instance klass.
ciInstanceKlass::ciInstanceKlass(KlassHandle h_k) :
  ciKlass(h_k), _non_static_fields(NULL)
{
  assert(get_Klass()->oop_is_instance(), "wrong type");
  assert(get_instanceKlass()->is_loaded(), "must be at least loaded");
  InstanceKlass* ik = get_instanceKlass();

  AccessFlags access_flags = ik->access_flags();
  _flags = ciFlags(access_flags);
  _has_finalizer = access_flags.has_finalizer();
  _has_subklass = ik->subklass() != NULL;
  _init_state = ik->init_state();
  _nonstatic_field_size = ik->nonstatic_field_size();
  _has_nonstatic_fields = ik->has_nonstatic_fields();
  _nonstatic_fields = NULL; // initialized lazily by compute_nonstatic_fields:

  _implementor = NULL; // we will fill these lazily

  Thread *thread = Thread::current();
  if (ciObjectFactory::is_initialized()) {
    _loader = JNIHandles::make_local(thread, ik->class_loader());
    _protection_domain = JNIHandles::make_local(thread,
                                                ik->protection_domain());
    _is_shared = false;
  } else {
    Handle h_loader(thread, ik->class_loader());
    Handle h_protection_domain(thread, ik->protection_domain());
    _loader = JNIHandles::make_global(h_loader);
    _protection_domain = JNIHandles::make_global(h_protection_domain);
    _is_shared = true;
  }

  // Lazy fields get filled in only upon request.
  _super  = NULL;
  _java_mirror = NULL;

  if (is_shared()) {
    if (h_k() != SystemDictionary::Object_klass()) {
      super();
    }
    //compute_nonstatic_fields();  // done outside of constructor
  }

  _field_cache = NULL;
}

// Version for unloaded classes:
ciInstanceKlass::ciInstanceKlass(ciSymbol* name,
                                 jobject loader, jobject protection_domain)
  : ciKlass(name, T_OBJECT)
{
  assert(name->byte_at(0) != '[', "not an instance klass");
  _init_state = (InstanceKlass::ClassState)0;
  _nonstatic_field_size = -1;
  _has_nonstatic_fields = false;
  _nonstatic_fields = NULL;
  _loader = loader;
  _protection_domain = protection_domain;
  _is_shared = false;
  _super = NULL;
  _java_mirror = NULL;
  _field_cache = NULL;
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
    _has_subklass = ik->subklass() != NULL;
    return _has_subklass;
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
// ciInstanceKlass::protection_domain
oop ciInstanceKlass::protection_domain() {
  ASSERT_IN_VM;
  return JNIHandles::resolve(_protection_domain);
}

// ------------------------------------------------------------------
// ciInstanceKlass::protection_domain_handle
jobject ciInstanceKlass::protection_domain_handle() {
  return _protection_domain;
}

// ------------------------------------------------------------------
// ciInstanceKlass::field_cache
//
// Get the field cache associated with this klass.
ciConstantPoolCache* ciInstanceKlass::field_cache() {
  if (is_shared()) {
    return NULL;
  }
  if (_field_cache == NULL) {
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
  if (!(offset >= 0 && offset < layout_helper())) {
    tty->print("*** get_canonical_holder(%d) on ", offset);
    this->print();
    tty->print_cr(" ***");
  };
  assert(offset >= 0 && offset < layout_helper(), "offset must be tame");
  #endif

  if (offset < instanceOopDesc::base_offset_in_bytes()) {
    // All header offsets belong properly to java/lang/Object.
    return CURRENT_ENV->Object_klass();
  }

  ciInstanceKlass* self = this;
  for (;;) {
    assert(self->is_loaded(), "must be loaded to have size");
    ciInstanceKlass* super = self->super();
    if (super == NULL || super->nof_nonstatic_fields() == 0 ||
        !super->contains_field_offset(offset)) {
      return self;
    } else {
      self = super;  // return super->get_canonical_holder(offset)
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
  return _loader == NULL;
}

// ------------------------------------------------------------------

/**
 * Return basic type of boxed value for box klass or T_OBJECT if not.
 */
BasicType ciInstanceKlass::box_klass_type() const {
  if (uses_default_loader() && is_loaded()) {
    return SystemDictionary::box_klass_type(get_Klass());
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
         (offset == java_lang_boxing_object::value_offset_in_bytes(bt));
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
  if ((char) name()->byte_at(len) != '/')
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
  GUARDED_VM_ENTRY(st->print(" loader=0x%x", (address)loader());)
  if (is_loaded()) {
    st->print(" loaded=true initialized=%s finalized=%s subklass=%s size=%d flags=",
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
  } else {
    st->print(" loaded=false");
  }
}

// ------------------------------------------------------------------
// ciInstanceKlass::super
//
// Get the superklass of this klass.
ciInstanceKlass* ciInstanceKlass::super() {
  assert(is_loaded(), "must be loaded");
  if (_super == NULL && !is_java_lang_Object()) {
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
  if (_java_mirror == NULL) {
    _java_mirror = ciKlass::java_mirror();
  }
  return _java_mirror;
}

// ------------------------------------------------------------------
// ciInstanceKlass::unique_concrete_subklass
ciInstanceKlass* ciInstanceKlass::unique_concrete_subklass() {
  if (!is_loaded())     return NULL; // No change if class is not loaded
  if (!is_abstract())   return NULL; // Only applies to abstract classes.
  if (!has_subklass())  return NULL; // Must have at least one subklass.
  VM_ENTRY_MARK;
  InstanceKlass* ik = get_instanceKlass();
  Klass* up = ik->up_cast_abstract();
  assert(up->oop_is_instance(), "must be InstanceKlass");
  if (ik == up) {
    return NULL;
  }
  return CURRENT_THREAD_ENV->get_instance_klass(up);
}

// ------------------------------------------------------------------
// ciInstanceKlass::has_finalizable_subclass
bool ciInstanceKlass::has_finalizable_subclass() {
  if (!is_loaded())     return true;
  VM_ENTRY_MARK;
  return Dependencies::find_finalizable_subclass(get_instanceKlass()) != NULL;
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
      if (field_off > field_offset)
        break;
      // could do binary search or check bins, but probably not worth it
    }
    return NULL;
  }
  VM_ENTRY_MARK;
  InstanceKlass* k = get_instanceKlass();
  fieldDescriptor fd;
  if (!k->find_field_from_offset(field_offset, is_static, &fd)) {
    return NULL;
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
  if (def == NULL) {
    return NULL;
  }
  ciField* field = new (CURRENT_THREAD_ENV->arena()) ciField(&fd);
  return field;
}

// ------------------------------------------------------------------
// ciInstanceKlass::non_static_fields.

class NonStaticFieldFiller: public FieldClosure {
  GrowableArray<ciField*>* _arr;
  ciEnv* _curEnv;
public:
  NonStaticFieldFiller(ciEnv* curEnv, GrowableArray<ciField*>* arr) :
    _curEnv(curEnv), _arr(arr)
  {}
  void do_field(fieldDescriptor* fd) {
    ciField* field = new (_curEnv->arena()) ciField(fd);
    _arr->append(field);
  }
};

GrowableArray<ciField*>* ciInstanceKlass::non_static_fields() {
  if (_non_static_fields == NULL) {
    VM_ENTRY_MARK;
    ciEnv* curEnv = ciEnv::current();
    InstanceKlass* ik = get_instanceKlass();
    int max_n_fields = ik->java_fields_count();

    Arena* arena = curEnv->arena();
    _non_static_fields =
      new (arena) GrowableArray<ciField*>(arena, max_n_fields, 0, NULL);
    NonStaticFieldFiller filler(curEnv, _non_static_fields);
    ik->do_nonstatic_fields(&filler);
  }
  return _non_static_fields;
}

static int sort_field_by_offset(ciField** a, ciField** b) {
  return (*a)->offset_in_bytes() - (*b)->offset_in_bytes();
  // (no worries about 32-bit overflow...)
}

// ------------------------------------------------------------------
// ciInstanceKlass::compute_nonstatic_fields
int ciInstanceKlass::compute_nonstatic_fields() {
  assert(is_loaded(), "must be loaded");

  if (_nonstatic_fields != NULL)
    return _nonstatic_fields->length();

  if (!has_nonstatic_fields()) {
    Arena* arena = CURRENT_ENV->arena();
    _nonstatic_fields = new (arena) GrowableArray<ciField*>(arena, 0, 0, NULL);
    return 0;
  }
  assert(!is_java_lang_Object(), "bootstrap OK");

  // Size in bytes of my fields, including inherited fields.
  int fsize = nonstatic_field_size() * heapOopSize;

  ciInstanceKlass* super = this->super();
  GrowableArray<ciField*>* super_fields = NULL;
  if (super != NULL && super->has_nonstatic_fields()) {
    int super_fsize  = super->nonstatic_field_size() * heapOopSize;
    int super_flen   = super->nof_nonstatic_fields();
    super_fields = super->_nonstatic_fields;
    assert(super_flen == 0 || super_fields != NULL, "first get nof_fields");
    // See if I am no larger than my super; if so, I can use his fields.
    if (fsize == super_fsize) {
      _nonstatic_fields = super_fields;
      return super_fields->length();
    }
  }

  GrowableArray<ciField*>* fields = NULL;
  GUARDED_VM_ENTRY({
      fields = compute_nonstatic_fields_impl(super_fields);
    });

  if (fields == NULL) {
    // This can happen if this class (java.lang.Class) has invisible fields.
    _nonstatic_fields = super_fields;
    return super_fields->length();
  }

  int flen = fields->length();

  // Now sort them by offset, ascending.
  // (In principle, they could mix with superclass fields.)
  fields->sort(sort_field_by_offset);
  _nonstatic_fields = fields;
  return flen;
}

GrowableArray<ciField*>*
ciInstanceKlass::compute_nonstatic_fields_impl(GrowableArray<ciField*>*
                                               super_fields) {
  ASSERT_IN_VM;
  Arena* arena = CURRENT_ENV->arena();
  int flen = 0;
  GrowableArray<ciField*>* fields = NULL;
  InstanceKlass* k = get_instanceKlass();
  for (JavaFieldStream fs(k); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static())  continue;
    flen += 1;
  }

  // allocate the array:
  if (flen == 0) {
    return NULL;  // return nothing if none are locally declared
  }
  if (super_fields != NULL) {
    flen += super_fields->length();
  }
  fields = new (arena) GrowableArray<ciField*>(arena, flen, 0, NULL);
  if (super_fields != NULL) {
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
  if (m == NULL)  return NULL;

  return CURRENT_THREAD_ENV->get_method(m);
}

// ------------------------------------------------------------------
// ciInstanceKlass::is_leaf_type
bool ciInstanceKlass::is_leaf_type() {
  assert(is_loaded(), "must be loaded");
  if (is_shared()) {
    return is_final();  // approximately correct
  } else {
    return !_has_subklass && (nof_implementors() == 0);
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
  if (impl == NULL) {
    // Go into the VM to fetch the implementor.
    {
      VM_ENTRY_MARK;
      Klass* k = get_instanceKlass()->implementor();
      if (k != NULL) {
        if (k == get_instanceKlass()) {
          // More than one implementors. Use 'this' in this case.
          impl = this;
        } else {
          impl = CURRENT_THREAD_ENV->get_instance_klass(k);
        }
      }
    }
    // Memoize this result.
    if (!is_shared()) {
      _implementor = impl;
    }
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
      switch (fd->field_type()) {
        case T_BYTE:    _out->print_cr("%d", mirror->byte_field(fd->offset()));   break;
        case T_BOOLEAN: _out->print_cr("%d", mirror->bool_field(fd->offset()));   break;
        case T_SHORT:   _out->print_cr("%d", mirror->short_field(fd->offset()));  break;
        case T_CHAR:    _out->print_cr("%d", mirror->char_field(fd->offset()));   break;
        case T_INT:     _out->print_cr("%d", mirror->int_field(fd->offset()));    break;
        case T_LONG:    _out->print_cr(INT64_FORMAT, mirror->long_field(fd->offset()));   break;
        case T_FLOAT: {
          float f = mirror->float_field(fd->offset());
          _out->print_cr("%d", *(int*)&f);
          break;
        }
        case T_DOUBLE: {
          double d = mirror->double_field(fd->offset());
          _out->print_cr(INT64_FORMAT, *(jlong*)&d);
          break;
        }
        case T_ARRAY: {
          oop value =  mirror->obj_field_acquire(fd->offset());
          if (value == NULL) {
            _out->print_cr("null");
          } else {
            typeArrayOop ta = (typeArrayOop)value;
            _out->print("%d", ta->length());
            if (value->is_objArray()) {
              objArrayOop oa = (objArrayOop)value;
              const char* klass_name  = value->klass()->name()->as_quoted_ascii();
              _out->print(" %s", klass_name);
            }
            _out->cr();
          }
          break;
        }
        case T_OBJECT: {
          oop value =  mirror->obj_field_acquire(fd->offset());
          if (value == NULL) {
            _out->print_cr("null");
          } else if (value->is_instance()) {
            if (value->is_a(SystemDictionary::String_klass())) {
              _out->print("\"");
              _out->print_raw(java_lang_String::as_quoted_ascii(value));
              _out->print_cr("\"");
            } else {
              const char* klass_name  = value->klass()->name()->as_quoted_ascii();
              _out->print_cr(klass_name);
            }
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


void ciInstanceKlass::dump_replay_data(outputStream* out) {
  ResourceMark rm;

  InstanceKlass* ik = get_instanceKlass();
  ConstantPool*  cp = ik->constants();

  // Try to record related loaded classes
  Klass* sub = ik->subklass();
  while (sub != NULL) {
    if (sub->oop_is_instance()) {
      out->print_cr("instanceKlass %s", sub->name()->as_quoted_ascii());
    }
    sub = sub->next_sibling();
  }

  // Dump out the state of the constant pool tags.  During replay the
  // tags will be validated for things which shouldn't change and
  // classes will be resolved if the tags indicate that they were
  // resolved at compile time.
  out->print("ciInstanceKlass %s %d %d %d", ik->name()->as_quoted_ascii(),
             is_linked(), is_initialized(), cp->length());
  for (int index = 1; index < cp->length(); index++) {
    out->print(" %d", cp->tags()->at(index));
  }
  out->cr();
  if (is_initialized()) {
    //  Dump out the static final fields in case the compilation relies
    //  on their value for correct replay.
    StaticFinalFieldPrinter sffp(out, ik->name()->as_quoted_ascii());
    ik->do_local_static_fields(&sffp);
  }
}
