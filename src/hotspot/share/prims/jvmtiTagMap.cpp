/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/arrayOop.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiEventController.hpp"
#include "prims/jvmtiEventController.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "prims/jvmtiTagMapTable.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/continuationWrapper.inline.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/objectBitSet.inline.hpp"
#include "utilities/macros.hpp"

typedef ObjectBitSet<mtServiceability> JVMTIBitSet;

bool JvmtiTagMap::_has_object_free_events = false;

// create a JvmtiTagMap
JvmtiTagMap::JvmtiTagMap(JvmtiEnv* env) :
  _env(env),
  _lock(Mutex::nosafepoint, "JvmtiTagMap_lock"),
  _needs_cleaning(false),
  _posting_events(false) {

  assert(JvmtiThreadState_lock->is_locked(), "sanity check");
  assert(((JvmtiEnvBase *)env)->tag_map() == nullptr, "tag map already exists for environment");

  _hashmap = new JvmtiTagMapTable();

  // finally add us to the environment
  ((JvmtiEnvBase *)env)->release_set_tag_map(this);
}

// destroy a JvmtiTagMap
JvmtiTagMap::~JvmtiTagMap() {

  // no lock acquired as we assume the enclosing environment is
  // also being destroyed.
  ((JvmtiEnvBase *)_env)->set_tag_map(nullptr);

  // finally destroy the hashmap
  delete _hashmap;
  _hashmap = nullptr;
}

// Called by env_dispose() to reclaim memory before deallocation.
// Remove all the entries but keep the empty table intact.
// This needs the table lock.
void JvmtiTagMap::clear() {
  MutexLocker ml(lock(), Mutex::_no_safepoint_check_flag);
  _hashmap->clear();
}

// returns the tag map for the given environments. If the tag map
// doesn't exist then it is created.
JvmtiTagMap* JvmtiTagMap::tag_map_for(JvmtiEnv* env) {
  JvmtiTagMap* tag_map = ((JvmtiEnvBase*)env)->tag_map_acquire();
  if (tag_map == nullptr) {
    MutexLocker mu(JvmtiThreadState_lock);
    tag_map = ((JvmtiEnvBase*)env)->tag_map();
    if (tag_map == nullptr) {
      tag_map = new JvmtiTagMap(env);
    }
  } else {
    DEBUG_ONLY(JavaThread::current()->check_possible_safepoint());
  }
  return tag_map;
}

// iterate over all entries in the tag map.
void JvmtiTagMap::entry_iterate(JvmtiTagMapKeyClosure* closure) {
  hashmap()->entry_iterate(closure);
}

// returns true if the hashmaps are empty
bool JvmtiTagMap::is_empty() {
  assert(SafepointSynchronize::is_at_safepoint() || is_locked(), "checking");
  return hashmap()->is_empty();
}

// This checks for posting before operations that use
// this tagmap table.
void JvmtiTagMap::check_hashmap(GrowableArray<jlong>* objects) {
  assert(is_locked(), "checking");

  if (is_empty()) { return; }

  if (_needs_cleaning &&
      objects != nullptr &&
      env()->is_enabled(JVMTI_EVENT_OBJECT_FREE)) {
    remove_dead_entries_locked(objects);
  }
}

// This checks for posting and is called from the heap walks.
void JvmtiTagMap::check_hashmaps_for_heapwalk(GrowableArray<jlong>* objects) {
  assert(SafepointSynchronize::is_at_safepoint(), "called from safepoints");

  // Verify that the tag map tables are valid and unconditionally post events
  // that are expected to be posted before gc_notification.
  JvmtiEnvIterator it;
  for (JvmtiEnv* env = it.first(); env != nullptr; env = it.next(env)) {
    JvmtiTagMap* tag_map = env->tag_map_acquire();
    if (tag_map != nullptr) {
      // The ZDriver may be walking the hashmaps concurrently so this lock is needed.
      MutexLocker ml(tag_map->lock(), Mutex::_no_safepoint_check_flag);
      tag_map->check_hashmap(objects);
    }
  }
}

// Return the tag value for an object, or 0 if the object is
// not tagged
//
static inline jlong tag_for(JvmtiTagMap* tag_map, oop o) {
  return tag_map->hashmap()->find(o);
}

// A CallbackWrapper is a support class for querying and tagging an object
// around a callback to a profiler. The constructor does pre-callback
// work to get the tag value, klass tag value, ... and the destructor
// does the post-callback work of tagging or untagging the object.
//
// {
//   CallbackWrapper wrapper(tag_map, o);
//
//   (*callback)(wrapper.klass_tag(), wrapper.obj_size(), wrapper.obj_tag_p(), ...)
//
// }
// wrapper goes out of scope here which results in the destructor
// checking to see if the object has been tagged, untagged, or the
// tag value has changed.
//
class CallbackWrapper : public StackObj {
 private:
  JvmtiTagMap* _tag_map;
  JvmtiTagMapTable* _hashmap;
  oop _o;
  jlong _obj_size;
  jlong _obj_tag;
  jlong _klass_tag;

 protected:
  JvmtiTagMap* tag_map() const { return _tag_map; }

  // invoked post-callback to tag, untag, or update the tag of an object
  void inline post_callback_tag_update(oop o, JvmtiTagMapTable* hashmap,
                                       jlong obj_tag);
 public:
  CallbackWrapper(JvmtiTagMap* tag_map, oop o) {
    assert(Thread::current()->is_VM_thread() || tag_map->is_locked(),
           "MT unsafe or must be VM thread");

    // object to tag
    _o = o;

    // object size
    _obj_size = (jlong)_o->size() * wordSize;

    // record the context
    _tag_map = tag_map;
    _hashmap = tag_map->hashmap();

    // get object tag
    _obj_tag = _hashmap->find(_o);

    // get the class and the class's tag value
    assert(vmClasses::Class_klass()->is_mirror_instance_klass(), "Is not?");

    _klass_tag = tag_for(tag_map, _o->klass()->java_mirror());
  }

  ~CallbackWrapper() {
    post_callback_tag_update(_o, _hashmap, _obj_tag);
  }

  inline jlong* obj_tag_p()                     { return &_obj_tag; }
  inline jlong obj_size() const                 { return _obj_size; }
  inline jlong obj_tag() const                  { return _obj_tag; }
  inline jlong klass_tag() const                { return _klass_tag; }
};

// callback post-callback to tag, untag, or update the tag of an object
void inline CallbackWrapper::post_callback_tag_update(oop o,
                                                      JvmtiTagMapTable* hashmap,
                                                      jlong obj_tag) {
  if (obj_tag == 0) {
    // callback has untagged the object, remove the entry if present
    hashmap->remove(o);
  } else {
    // object was previously tagged or not present - the callback may have
    // changed the tag value
    assert(Thread::current()->is_VM_thread(), "must be VMThread");
    hashmap->add(o, obj_tag);
  }
}

// An extended CallbackWrapper used when reporting an object reference
// to the agent.
//
// {
//   TwoOopCallbackWrapper wrapper(tag_map, referrer, o);
//
//   (*callback)(wrapper.klass_tag(),
//               wrapper.obj_size(),
//               wrapper.obj_tag_p()
//               wrapper.referrer_tag_p(), ...)
//
// }
// wrapper goes out of scope here which results in the destructor
// checking to see if the referrer object has been tagged, untagged,
// or the tag value has changed.
//
class TwoOopCallbackWrapper : public CallbackWrapper {
 private:
  bool _is_reference_to_self;
  JvmtiTagMapTable* _referrer_hashmap;
  oop _referrer;
  jlong _referrer_obj_tag;
  jlong _referrer_klass_tag;
  jlong* _referrer_tag_p;

  bool is_reference_to_self() const             { return _is_reference_to_self; }

 public:
  TwoOopCallbackWrapper(JvmtiTagMap* tag_map, oop referrer, oop o) :
    CallbackWrapper(tag_map, o)
  {
    // self reference needs to be handled in a special way
    _is_reference_to_self = (referrer == o);

    if (_is_reference_to_self) {
      _referrer_klass_tag = klass_tag();
      _referrer_tag_p = obj_tag_p();
    } else {
      _referrer = referrer;
      // record the context
      _referrer_hashmap = tag_map->hashmap();

      // get object tag
      _referrer_obj_tag = _referrer_hashmap->find(_referrer);

      _referrer_tag_p = &_referrer_obj_tag;

      // get referrer class tag.
      _referrer_klass_tag = tag_for(tag_map, _referrer->klass()->java_mirror());
    }
  }

  ~TwoOopCallbackWrapper() {
    if (!is_reference_to_self()) {
      post_callback_tag_update(_referrer,
                               _referrer_hashmap,
                               _referrer_obj_tag);
    }
  }

  // address of referrer tag
  // (for a self reference this will return the same thing as obj_tag_p())
  inline jlong* referrer_tag_p() { return _referrer_tag_p; }

  // referrer's class tag
  inline jlong referrer_klass_tag() { return _referrer_klass_tag; }
};

// tag an object
//
// This function is performance critical. If many threads attempt to tag objects
// around the same time then it's possible that the Mutex associated with the
// tag map will be a hot lock.
void JvmtiTagMap::set_tag(jobject object, jlong tag) {
  MutexLocker ml(lock(), Mutex::_no_safepoint_check_flag);

  // SetTag should not post events because the JavaThread has to
  // transition to native for the callback and this cannot stop for
  // safepoints with the hashmap lock held.
  check_hashmap(nullptr);  /* don't collect dead objects */

  // resolve the object
  oop o = JNIHandles::resolve_non_null(object);

  // see if the object is already tagged
  JvmtiTagMapTable* hashmap = _hashmap;

  if (tag == 0) {
    // remove the entry if present
    hashmap->remove(o);
  } else {
    // if the object is already tagged or not present then we add/update
    // the tag
    hashmap->add(o, tag);
  }
}

// get the tag for an object
jlong JvmtiTagMap::get_tag(jobject object) {
  MutexLocker ml(lock(), Mutex::_no_safepoint_check_flag);

  // GetTag should not post events because the JavaThread has to
  // transition to native for the callback and this cannot stop for
  // safepoints with the hashmap lock held.
  check_hashmap(nullptr); /* don't collect dead objects */

  // resolve the object
  oop o = JNIHandles::resolve_non_null(object);

  return tag_for(this, o);
}


// Helper class used to describe the static or instance fields of a class.
// For each field it holds the field index (as defined by the JVMTI specification),
// the field type, and the offset.

class ClassFieldDescriptor: public CHeapObj<mtInternal> {
 private:
  int _field_index;
  int _field_offset;
  char _field_type;
 public:
  ClassFieldDescriptor(int index, char type, int offset) :
    _field_index(index), _field_offset(offset), _field_type(type) {
  }
  int field_index()  const  { return _field_index; }
  char field_type()  const  { return _field_type; }
  int field_offset() const  { return _field_offset; }
};

class ClassFieldMap: public CHeapObj<mtInternal> {
 private:
  enum {
    initial_field_count = 5
  };

  // list of field descriptors
  GrowableArray<ClassFieldDescriptor*>* _fields;

  // constructor
  ClassFieldMap();

  // calculates number of fields in all interfaces
  static int interfaces_field_count(InstanceKlass* ik);

  // add a field
  void add(int index, char type, int offset);

 public:
  ~ClassFieldMap();

  // access
  int field_count()                     { return _fields->length(); }
  ClassFieldDescriptor* field_at(int i) { return _fields->at(i); }

  // functions to create maps of static or instance fields
  static ClassFieldMap* create_map_of_static_fields(Klass* k);
  static ClassFieldMap* create_map_of_instance_fields(oop obj);
};

ClassFieldMap::ClassFieldMap() {
  _fields = new (mtServiceability)
    GrowableArray<ClassFieldDescriptor*>(initial_field_count, mtServiceability);
}

ClassFieldMap::~ClassFieldMap() {
  for (int i=0; i<_fields->length(); i++) {
    delete _fields->at(i);
  }
  delete _fields;
}

int ClassFieldMap::interfaces_field_count(InstanceKlass* ik) {
  const Array<InstanceKlass*>* interfaces = ik->transitive_interfaces();
  int count = 0;
  for (int i = 0; i < interfaces->length(); i++) {
    FilteredJavaFieldStream fld(interfaces->at(i));
    count += fld.field_count();
  }
  return count;
}

void ClassFieldMap::add(int index, char type, int offset) {
  ClassFieldDescriptor* field = new ClassFieldDescriptor(index, type, offset);
  _fields->append(field);
}

// Returns a heap allocated ClassFieldMap to describe the static fields
// of the given class.
ClassFieldMap* ClassFieldMap::create_map_of_static_fields(Klass* k) {
  InstanceKlass* ik = InstanceKlass::cast(k);

  // create the field map
  ClassFieldMap* field_map = new ClassFieldMap();

  // Static fields of interfaces and superclasses are reported as references from the interfaces/superclasses.
  // Need to calculate start index of this class fields: number of fields in all interfaces and superclasses.
  int index = interfaces_field_count(ik);
  for (InstanceKlass* super_klass = ik->java_super(); super_klass != nullptr; super_klass = super_klass->java_super()) {
    FilteredJavaFieldStream super_fld(super_klass);
    index += super_fld.field_count();
  }

  for (FilteredJavaFieldStream fld(ik); !fld.done(); fld.next(), index++) {
    // ignore instance fields
    if (!fld.access_flags().is_static()) {
      continue;
    }
    field_map->add(index, fld.signature()->char_at(0), fld.offset());
  }

  return field_map;
}

// Returns a heap allocated ClassFieldMap to describe the instance fields
// of the given class. All instance fields are included (this means public
// and private fields declared in superclasses too).
ClassFieldMap* ClassFieldMap::create_map_of_instance_fields(oop obj) {
  InstanceKlass* ik = InstanceKlass::cast(obj->klass());

  // create the field map
  ClassFieldMap* field_map = new ClassFieldMap();

  // fields of the superclasses are reported first, so need to know total field number to calculate field indices
  int total_field_number = interfaces_field_count(ik);
  for (InstanceKlass* klass = ik; klass != nullptr; klass = klass->java_super()) {
    FilteredJavaFieldStream fld(klass);
    total_field_number += fld.field_count();
  }

  for (InstanceKlass* klass = ik; klass != nullptr; klass = klass->java_super()) {
    FilteredJavaFieldStream fld(klass);
    int start_index = total_field_number - fld.field_count();
    for (int index = 0; !fld.done(); fld.next(), index++) {
      // ignore static fields
      if (fld.access_flags().is_static()) {
        continue;
      }
      field_map->add(start_index + index, fld.signature()->char_at(0), fld.offset());
    }
    // update total_field_number for superclass (decrease by the field count in the current class)
    total_field_number = start_index;
  }

  return field_map;
}

// Helper class used to cache a ClassFileMap for the instance fields of
// a cache. A JvmtiCachedClassFieldMap can be cached by an InstanceKlass during
// heap iteration and avoid creating a field map for each object in the heap
// (only need to create the map when the first instance of a class is encountered).
//
class JvmtiCachedClassFieldMap : public CHeapObj<mtInternal> {
 private:
  enum {
     initial_class_count = 200
  };
  ClassFieldMap* _field_map;

  ClassFieldMap* field_map() const { return _field_map; }

  JvmtiCachedClassFieldMap(ClassFieldMap* field_map);
  ~JvmtiCachedClassFieldMap();

  static GrowableArray<InstanceKlass*>* _class_list;
  static void add_to_class_list(InstanceKlass* ik);

 public:
  // returns the field map for a given object (returning map cached
  // by InstanceKlass if possible
  static ClassFieldMap* get_map_of_instance_fields(oop obj);

  // removes the field map from all instanceKlasses - should be
  // called before VM operation completes
  static void clear_cache();

  // returns the number of ClassFieldMap cached by instanceKlasses
  static int cached_field_map_count();
};

GrowableArray<InstanceKlass*>* JvmtiCachedClassFieldMap::_class_list;

JvmtiCachedClassFieldMap::JvmtiCachedClassFieldMap(ClassFieldMap* field_map) {
  _field_map = field_map;
}

JvmtiCachedClassFieldMap::~JvmtiCachedClassFieldMap() {
  if (_field_map != nullptr) {
    delete _field_map;
  }
}

// Marker class to ensure that the class file map cache is only used in a defined
// scope.
class ClassFieldMapCacheMark : public StackObj {
 private:
   static bool _is_active;
 public:
   ClassFieldMapCacheMark() {
     assert(Thread::current()->is_VM_thread(), "must be VMThread");
     assert(JvmtiCachedClassFieldMap::cached_field_map_count() == 0, "cache not empty");
     assert(!_is_active, "ClassFieldMapCacheMark cannot be nested");
     _is_active = true;
   }
   ~ClassFieldMapCacheMark() {
     JvmtiCachedClassFieldMap::clear_cache();
     _is_active = false;
   }
   static bool is_active() { return _is_active; }
};

bool ClassFieldMapCacheMark::_is_active;

// record that the given InstanceKlass is caching a field map
void JvmtiCachedClassFieldMap::add_to_class_list(InstanceKlass* ik) {
  if (_class_list == nullptr) {
    _class_list = new (mtServiceability)
      GrowableArray<InstanceKlass*>(initial_class_count, mtServiceability);
  }
  _class_list->push(ik);
}

// returns the instance field map for the given object
// (returns field map cached by the InstanceKlass if possible)
ClassFieldMap* JvmtiCachedClassFieldMap::get_map_of_instance_fields(oop obj) {
  assert(Thread::current()->is_VM_thread(), "must be VMThread");
  assert(ClassFieldMapCacheMark::is_active(), "ClassFieldMapCacheMark not active");

  Klass* k = obj->klass();
  InstanceKlass* ik = InstanceKlass::cast(k);

  // return cached map if possible
  JvmtiCachedClassFieldMap* cached_map = ik->jvmti_cached_class_field_map();
  if (cached_map != nullptr) {
    assert(cached_map->field_map() != nullptr, "missing field list");
    return cached_map->field_map();
  } else {
    ClassFieldMap* field_map = ClassFieldMap::create_map_of_instance_fields(obj);
    cached_map = new JvmtiCachedClassFieldMap(field_map);
    ik->set_jvmti_cached_class_field_map(cached_map);
    add_to_class_list(ik);
    return field_map;
  }
}

// remove the fields maps cached from all instanceKlasses
void JvmtiCachedClassFieldMap::clear_cache() {
  assert(Thread::current()->is_VM_thread(), "must be VMThread");
  if (_class_list != nullptr) {
    for (int i = 0; i < _class_list->length(); i++) {
      InstanceKlass* ik = _class_list->at(i);
      JvmtiCachedClassFieldMap* cached_map = ik->jvmti_cached_class_field_map();
      assert(cached_map != nullptr, "should not be null");
      ik->set_jvmti_cached_class_field_map(nullptr);
      delete cached_map;  // deletes the encapsulated field map
    }
    delete _class_list;
    _class_list = nullptr;
  }
}

// returns the number of ClassFieldMap cached by instanceKlasses
int JvmtiCachedClassFieldMap::cached_field_map_count() {
  return (_class_list == nullptr) ? 0 : _class_list->length();
}

// helper function to indicate if an object is filtered by its tag or class tag
static inline bool is_filtered_by_heap_filter(jlong obj_tag,
                                              jlong klass_tag,
                                              int heap_filter) {
  // apply the heap filter
  if (obj_tag != 0) {
    // filter out tagged objects
    if (heap_filter & JVMTI_HEAP_FILTER_TAGGED) return true;
  } else {
    // filter out untagged objects
    if (heap_filter & JVMTI_HEAP_FILTER_UNTAGGED) return true;
  }
  if (klass_tag != 0) {
    // filter out objects with tagged classes
    if (heap_filter & JVMTI_HEAP_FILTER_CLASS_TAGGED) return true;
  } else {
    // filter out objects with untagged classes.
    if (heap_filter & JVMTI_HEAP_FILTER_CLASS_UNTAGGED) return true;
  }
  return false;
}

// helper function to indicate if an object is filtered by a klass filter
static inline bool is_filtered_by_klass_filter(oop obj, Klass* klass_filter) {
  if (klass_filter != nullptr) {
    if (obj->klass() != klass_filter) {
      return true;
    }
  }
  return false;
}

// helper function to tell if a field is a primitive field or not
static inline bool is_primitive_field_type(char type) {
  return (type != JVM_SIGNATURE_CLASS && type != JVM_SIGNATURE_ARRAY);
}

// helper function to copy the value from location addr to jvalue.
static inline void copy_to_jvalue(jvalue *v, address addr, jvmtiPrimitiveType value_type) {
  switch (value_type) {
    case JVMTI_PRIMITIVE_TYPE_BOOLEAN : { v->z = *(jboolean*)addr; break; }
    case JVMTI_PRIMITIVE_TYPE_BYTE    : { v->b = *(jbyte*)addr;    break; }
    case JVMTI_PRIMITIVE_TYPE_CHAR    : { v->c = *(jchar*)addr;    break; }
    case JVMTI_PRIMITIVE_TYPE_SHORT   : { v->s = *(jshort*)addr;   break; }
    case JVMTI_PRIMITIVE_TYPE_INT     : { v->i = *(jint*)addr;     break; }
    case JVMTI_PRIMITIVE_TYPE_LONG    : { v->j = *(jlong*)addr;    break; }
    case JVMTI_PRIMITIVE_TYPE_FLOAT   : { v->f = *(jfloat*)addr;   break; }
    case JVMTI_PRIMITIVE_TYPE_DOUBLE  : { v->d = *(jdouble*)addr;  break; }
    default: ShouldNotReachHere();
  }
}

// helper function to invoke string primitive value callback
// returns visit control flags
static jint invoke_string_value_callback(jvmtiStringPrimitiveValueCallback cb,
                                         CallbackWrapper* wrapper,
                                         oop str,
                                         void* user_data)
{
  assert(str->klass() == vmClasses::String_klass(), "not a string");

  typeArrayOop s_value = java_lang_String::value(str);

  // JDK-6584008: the value field may be null if a String instance is
  // partially constructed.
  if (s_value == nullptr) {
    return 0;
  }
  // get the string value and length
  // (string value may be offset from the base)
  int s_len = java_lang_String::length(str);
  bool is_latin1 = java_lang_String::is_latin1(str);
  jchar* value;
  if (s_len > 0) {
    if (!is_latin1) {
      value = s_value->char_at_addr(0);
    } else {
      // Inflate latin1 encoded string to UTF16
      jchar* buf = NEW_C_HEAP_ARRAY(jchar, s_len, mtInternal);
      for (int i = 0; i < s_len; i++) {
        buf[i] = ((jchar) s_value->byte_at(i)) & 0xff;
      }
      value = &buf[0];
    }
  } else {
    // Don't use char_at_addr(0) if length is 0
    value = (jchar*) s_value->base(T_CHAR);
  }

  // invoke the callback
  jint res = (*cb)(wrapper->klass_tag(),
                   wrapper->obj_size(),
                   wrapper->obj_tag_p(),
                   value,
                   (jint)s_len,
                   user_data);

  if (is_latin1 && s_len > 0) {
    FREE_C_HEAP_ARRAY(jchar, value);
  }
  return res;
}

// helper function to invoke string primitive value callback
// returns visit control flags
static jint invoke_array_primitive_value_callback(jvmtiArrayPrimitiveValueCallback cb,
                                                  CallbackWrapper* wrapper,
                                                  oop obj,
                                                  void* user_data)
{
  assert(obj->is_typeArray(), "not a primitive array");

  // get base address of first element
  typeArrayOop array = typeArrayOop(obj);
  BasicType type = TypeArrayKlass::cast(array->klass())->element_type();
  void* elements = array->base(type);

  // jvmtiPrimitiveType is defined so this mapping is always correct
  jvmtiPrimitiveType elem_type = (jvmtiPrimitiveType)type2char(type);

  return (*cb)(wrapper->klass_tag(),
               wrapper->obj_size(),
               wrapper->obj_tag_p(),
               (jint)array->length(),
               elem_type,
               elements,
               user_data);
}

// helper function to invoke the primitive field callback for all static fields
// of a given class
static jint invoke_primitive_field_callback_for_static_fields
  (CallbackWrapper* wrapper,
   oop obj,
   jvmtiPrimitiveFieldCallback cb,
   void* user_data)
{
  // for static fields only the index will be set
  static jvmtiHeapReferenceInfo reference_info = { 0 };

  assert(obj->klass() == vmClasses::Class_klass(), "not a class");
  if (java_lang_Class::is_primitive(obj)) {
    return 0;
  }
  Klass* klass = java_lang_Class::as_Klass(obj);

  // ignore classes for object and type arrays
  if (!klass->is_instance_klass()) {
    return 0;
  }

  // ignore classes which aren't linked yet
  InstanceKlass* ik = InstanceKlass::cast(klass);
  if (!ik->is_linked()) {
    return 0;
  }

  // get the field map
  ClassFieldMap* field_map = ClassFieldMap::create_map_of_static_fields(klass);

  // invoke the callback for each static primitive field
  for (int i=0; i<field_map->field_count(); i++) {
    ClassFieldDescriptor* field = field_map->field_at(i);

    // ignore non-primitive fields
    char type = field->field_type();
    if (!is_primitive_field_type(type)) {
      continue;
    }
    // one-to-one mapping
    jvmtiPrimitiveType value_type = (jvmtiPrimitiveType)type;

    // get offset and field value
    int offset = field->field_offset();
    address addr = cast_from_oop<address>(klass->java_mirror()) + offset;
    jvalue value;
    copy_to_jvalue(&value, addr, value_type);

    // field index
    reference_info.field.index = field->field_index();

    // invoke the callback
    jint res = (*cb)(JVMTI_HEAP_REFERENCE_STATIC_FIELD,
                     &reference_info,
                     wrapper->klass_tag(),
                     wrapper->obj_tag_p(),
                     value,
                     value_type,
                     user_data);
    if (res & JVMTI_VISIT_ABORT) {
      delete field_map;
      return res;
    }
  }

  delete field_map;
  return 0;
}

// helper function to invoke the primitive field callback for all instance fields
// of a given object
static jint invoke_primitive_field_callback_for_instance_fields(
  CallbackWrapper* wrapper,
  oop obj,
  jvmtiPrimitiveFieldCallback cb,
  void* user_data)
{
  // for instance fields only the index will be set
  static jvmtiHeapReferenceInfo reference_info = { 0 };

  // get the map of the instance fields
  ClassFieldMap* fields = JvmtiCachedClassFieldMap::get_map_of_instance_fields(obj);

  // invoke the callback for each instance primitive field
  for (int i=0; i<fields->field_count(); i++) {
    ClassFieldDescriptor* field = fields->field_at(i);

    // ignore non-primitive fields
    char type = field->field_type();
    if (!is_primitive_field_type(type)) {
      continue;
    }
    // one-to-one mapping
    jvmtiPrimitiveType value_type = (jvmtiPrimitiveType)type;

    // get offset and field value
    int offset = field->field_offset();
    address addr = cast_from_oop<address>(obj) + offset;
    jvalue value;
    copy_to_jvalue(&value, addr, value_type);

    // field index
    reference_info.field.index = field->field_index();

    // invoke the callback
    jint res = (*cb)(JVMTI_HEAP_REFERENCE_FIELD,
                     &reference_info,
                     wrapper->klass_tag(),
                     wrapper->obj_tag_p(),
                     value,
                     value_type,
                     user_data);
    if (res & JVMTI_VISIT_ABORT) {
      return res;
    }
  }
  return 0;
}


// VM operation to iterate over all objects in the heap (both reachable
// and unreachable)
class VM_HeapIterateOperation: public VM_Operation {
 private:
  ObjectClosure* _blk;
  GrowableArray<jlong>* const _dead_objects;
 public:
  VM_HeapIterateOperation(ObjectClosure* blk, GrowableArray<jlong>* objects) :
    _blk(blk), _dead_objects(objects) { }

  VMOp_Type type() const { return VMOp_HeapIterateOperation; }
  void doit() {
    // allows class files maps to be cached during iteration
    ClassFieldMapCacheMark cm;

    JvmtiTagMap::check_hashmaps_for_heapwalk(_dead_objects);

    // make sure that heap is parsable (fills TLABs with filler objects)
    Universe::heap()->ensure_parsability(false);  // no need to retire TLABs

    // Verify heap before iteration - if the heap gets corrupted then
    // JVMTI's IterateOverHeap will crash.
    if (VerifyBeforeIteration) {
      Universe::verify();
    }

    // do the iteration
    Universe::heap()->object_iterate(_blk);
  }
};


// An ObjectClosure used to support the deprecated IterateOverHeap and
// IterateOverInstancesOfClass functions
class IterateOverHeapObjectClosure: public ObjectClosure {
 private:
  JvmtiTagMap* _tag_map;
  Klass* _klass;
  jvmtiHeapObjectFilter _object_filter;
  jvmtiHeapObjectCallback _heap_object_callback;
  const void* _user_data;

  // accessors
  JvmtiTagMap* tag_map() const                    { return _tag_map; }
  jvmtiHeapObjectFilter object_filter() const     { return _object_filter; }
  jvmtiHeapObjectCallback object_callback() const { return _heap_object_callback; }
  Klass* klass() const                            { return _klass; }
  const void* user_data() const                   { return _user_data; }

  // indicates if iteration has been aborted
  bool _iteration_aborted;
  bool is_iteration_aborted() const               { return _iteration_aborted; }
  void set_iteration_aborted(bool aborted)        { _iteration_aborted = aborted; }

 public:
  IterateOverHeapObjectClosure(JvmtiTagMap* tag_map,
                               Klass* klass,
                               jvmtiHeapObjectFilter object_filter,
                               jvmtiHeapObjectCallback heap_object_callback,
                               const void* user_data) :
    _tag_map(tag_map),
    _klass(klass),
    _object_filter(object_filter),
    _heap_object_callback(heap_object_callback),
    _user_data(user_data),
    _iteration_aborted(false)
  {
  }

  void do_object(oop o);
};

// invoked for each object in the heap
void IterateOverHeapObjectClosure::do_object(oop o) {
  assert(o != nullptr, "Heap iteration should never produce null!");
  // check if iteration has been halted
  if (is_iteration_aborted()) return;

  // instanceof check when filtering by klass
  if (klass() != nullptr && !o->is_a(klass())) {
    return;
  }

  // skip if object is a dormant shared object whose mirror hasn't been loaded
  if (o->klass()->java_mirror() == nullptr) {
    log_debug(aot, heap)("skipped dormant archived object " INTPTR_FORMAT " (%s)", p2i(o),
                         o->klass()->external_name());
    return;
  }

  // prepare for the calllback
  CallbackWrapper wrapper(tag_map(), o);

  // if the object is tagged and we're only interested in untagged objects
  // then don't invoke the callback. Similarly, if the object is untagged
  // and we're only interested in tagged objects we skip the callback.
  if (wrapper.obj_tag() != 0) {
    if (object_filter() == JVMTI_HEAP_OBJECT_UNTAGGED) return;
  } else {
    if (object_filter() == JVMTI_HEAP_OBJECT_TAGGED) return;
  }

  // invoke the agent's callback
  jvmtiIterationControl control = (*object_callback())(wrapper.klass_tag(),
                                                       wrapper.obj_size(),
                                                       wrapper.obj_tag_p(),
                                                       (void*)user_data());
  if (control == JVMTI_ITERATION_ABORT) {
    set_iteration_aborted(true);
  }
}

// An ObjectClosure used to support the IterateThroughHeap function
class IterateThroughHeapObjectClosure: public ObjectClosure {
 private:
  JvmtiTagMap* _tag_map;
  Klass* _klass;
  int _heap_filter;
  const jvmtiHeapCallbacks* _callbacks;
  const void* _user_data;

  // accessor functions
  JvmtiTagMap* tag_map() const                     { return _tag_map; }
  int heap_filter() const                          { return _heap_filter; }
  const jvmtiHeapCallbacks* callbacks() const      { return _callbacks; }
  Klass* klass() const                             { return _klass; }
  const void* user_data() const                    { return _user_data; }

  // indicates if the iteration has been aborted
  bool _iteration_aborted;
  bool is_iteration_aborted() const                { return _iteration_aborted; }

  // used to check the visit control flags. If the abort flag is set
  // then we set the iteration aborted flag so that the iteration completes
  // without processing any further objects
  bool check_flags_for_abort(jint flags) {
    bool is_abort = (flags & JVMTI_VISIT_ABORT) != 0;
    if (is_abort) {
      _iteration_aborted = true;
    }
    return is_abort;
  }

 public:
  IterateThroughHeapObjectClosure(JvmtiTagMap* tag_map,
                                  Klass* klass,
                                  int heap_filter,
                                  const jvmtiHeapCallbacks* heap_callbacks,
                                  const void* user_data) :
    _tag_map(tag_map),
    _klass(klass),
    _heap_filter(heap_filter),
    _callbacks(heap_callbacks),
    _user_data(user_data),
    _iteration_aborted(false)
  {
  }

  void do_object(oop o);
};

// invoked for each object in the heap
void IterateThroughHeapObjectClosure::do_object(oop obj) {
  assert(obj != nullptr, "Heap iteration should never produce null!");
  // check if iteration has been halted
  if (is_iteration_aborted()) return;

  // apply class filter
  if (is_filtered_by_klass_filter(obj, klass())) return;

  // skip if object is a dormant shared object whose mirror hasn't been loaded
  if (obj->klass()->java_mirror() == nullptr) {
    log_debug(aot, heap)("skipped dormant archived object " INTPTR_FORMAT " (%s)", p2i(obj),
                         obj->klass()->external_name());
    return;
  }

  // prepare for callback
  CallbackWrapper wrapper(tag_map(), obj);

  // check if filtered by the heap filter
  if (is_filtered_by_heap_filter(wrapper.obj_tag(), wrapper.klass_tag(), heap_filter())) {
    return;
  }

  // for arrays we need the length, otherwise -1
  bool is_array = obj->is_array();
  int len = is_array ? arrayOop(obj)->length() : -1;

  // invoke the object callback (if callback is provided)
  if (callbacks()->heap_iteration_callback != nullptr) {
    jvmtiHeapIterationCallback cb = callbacks()->heap_iteration_callback;
    jint res = (*cb)(wrapper.klass_tag(),
                     wrapper.obj_size(),
                     wrapper.obj_tag_p(),
                     (jint)len,
                     (void*)user_data());
    if (check_flags_for_abort(res)) return;
  }

  // for objects and classes we report primitive fields if callback provided
  if (callbacks()->primitive_field_callback != nullptr && obj->is_instance()) {
    jint res;
    jvmtiPrimitiveFieldCallback cb = callbacks()->primitive_field_callback;
    if (obj->klass() == vmClasses::Class_klass()) {
      res = invoke_primitive_field_callback_for_static_fields(&wrapper,
                                                                    obj,
                                                                    cb,
                                                                    (void*)user_data());
    } else {
      res = invoke_primitive_field_callback_for_instance_fields(&wrapper,
                                                                      obj,
                                                                      cb,
                                                                      (void*)user_data());
    }
    if (check_flags_for_abort(res)) return;
  }

  // string callback
  if (!is_array &&
      callbacks()->string_primitive_value_callback != nullptr &&
      obj->klass() == vmClasses::String_klass()) {
    jint res = invoke_string_value_callback(
                callbacks()->string_primitive_value_callback,
                &wrapper,
                obj,
                (void*)user_data() );
    if (check_flags_for_abort(res)) return;
  }

  // array callback
  if (is_array &&
      callbacks()->array_primitive_value_callback != nullptr &&
      obj->is_typeArray()) {
    jint res = invoke_array_primitive_value_callback(
               callbacks()->array_primitive_value_callback,
               &wrapper,
               obj,
               (void*)user_data() );
    if (check_flags_for_abort(res)) return;
  }
};


// Deprecated function to iterate over all objects in the heap
void JvmtiTagMap::iterate_over_heap(jvmtiHeapObjectFilter object_filter,
                                    Klass* klass,
                                    jvmtiHeapObjectCallback heap_object_callback,
                                    const void* user_data)
{
  // EA based optimizations on tagged objects are already reverted.
  EscapeBarrier eb(object_filter == JVMTI_HEAP_OBJECT_UNTAGGED ||
                   object_filter == JVMTI_HEAP_OBJECT_EITHER,
                   JavaThread::current());
  eb.deoptimize_objects_all_threads();
  Arena dead_object_arena(mtServiceability);
  GrowableArray <jlong> dead_objects(&dead_object_arena, 10, 0, 0);
  {
    MutexLocker ml(Heap_lock);
    IterateOverHeapObjectClosure blk(this,
                                     klass,
                                     object_filter,
                                     heap_object_callback,
                                     user_data);
    VM_HeapIterateOperation op(&blk, &dead_objects);
    VMThread::execute(&op);
  }
  // Post events outside of Heap_lock
  post_dead_objects(&dead_objects);
}


// Iterates over all objects in the heap
void JvmtiTagMap::iterate_through_heap(jint heap_filter,
                                       Klass* klass,
                                       const jvmtiHeapCallbacks* callbacks,
                                       const void* user_data)
{
  // EA based optimizations on tagged objects are already reverted.
  EscapeBarrier eb(!(heap_filter & JVMTI_HEAP_FILTER_UNTAGGED), JavaThread::current());
  eb.deoptimize_objects_all_threads();

  Arena dead_object_arena(mtServiceability);
  GrowableArray<jlong> dead_objects(&dead_object_arena, 10, 0, 0);
  {
    MutexLocker ml(Heap_lock);
    IterateThroughHeapObjectClosure blk(this,
                                        klass,
                                        heap_filter,
                                        callbacks,
                                        user_data);
    VM_HeapIterateOperation op(&blk, &dead_objects);
    VMThread::execute(&op);
  }
  // Post events outside of Heap_lock
  post_dead_objects(&dead_objects);
}

void JvmtiTagMap::remove_dead_entries_locked(GrowableArray<jlong>* objects) {
  assert(is_locked(), "precondition");
  if (_needs_cleaning) {
    // Recheck whether to post object free events under the lock.
    if (!env()->is_enabled(JVMTI_EVENT_OBJECT_FREE)) {
      objects = nullptr;
    }
    log_info(jvmti, table)("TagMap table needs cleaning%s",
                           ((objects != nullptr) ? " and posting" : ""));
    hashmap()->remove_dead_entries(objects);
    _needs_cleaning = false;
  }
}

void JvmtiTagMap::remove_dead_entries(GrowableArray<jlong>* objects) {
  MutexLocker ml(lock(), Mutex::_no_safepoint_check_flag);
  remove_dead_entries_locked(objects);
}

void JvmtiTagMap::post_dead_objects(GrowableArray<jlong>* const objects) {
  assert(Thread::current()->is_Java_thread(), "Must post from JavaThread");
  if (objects != nullptr && objects->length() > 0) {
    JvmtiExport::post_object_free(env(), objects);
    log_info(jvmti, table)("%d free object posted", objects->length());
  }
}

void JvmtiTagMap::remove_and_post_dead_objects() {
  ResourceMark rm;
  GrowableArray<jlong> objects;
  remove_dead_entries(&objects);
  post_dead_objects(&objects);
}

void JvmtiTagMap::flush_object_free_events() {
  assert_not_at_safepoint();
  if (env()->is_enabled(JVMTI_EVENT_OBJECT_FREE)) {
    {
      MonitorLocker ml(lock(), Mutex::_no_safepoint_check_flag);
      // If another thread is posting events, let it finish
      while (_posting_events) {
        ml.wait();
      }

      if (!_needs_cleaning || is_empty()) {
        _needs_cleaning = false;
        return;
      }
      _posting_events = true;
    } // Drop the lock so we can do the cleaning on the VM thread.
    // Needs both cleaning and event posting (up to some other thread
    // getting there first after we dropped the lock).
    remove_and_post_dead_objects();
    {
      MonitorLocker ml(lock(), Mutex::_no_safepoint_check_flag);
      _posting_events = false;
      ml.notify_all();
    }
  } else {
    remove_dead_entries(nullptr);
  }
}

// support class for get_objects_with_tags

class TagObjectCollector : public JvmtiTagMapKeyClosure {
 private:
  JvmtiEnv* _env;
  JavaThread* _thread;
  jlong* _tags;
  jint _tag_count;
  bool _some_dead_found;

  GrowableArray<jobject>* _object_results;  // collected objects (JNI weak refs)
  GrowableArray<uint64_t>* _tag_results;    // collected tags

 public:
  TagObjectCollector(JvmtiEnv* env, const jlong* tags, jint tag_count) :
    _env(env),
    _thread(JavaThread::current()),
    _tags((jlong*)tags),
    _tag_count(tag_count),
    _some_dead_found(false),
    _object_results(new (mtServiceability) GrowableArray<jobject>(1, mtServiceability)),
    _tag_results(new (mtServiceability) GrowableArray<uint64_t>(1, mtServiceability)) { }

  ~TagObjectCollector() {
    delete _object_results;
    delete _tag_results;
  }

  bool some_dead_found() const { return _some_dead_found; }

  // for each tagged object check if the tag value matches
  // - if it matches then we create a JNI local reference to the object
  // and record the reference and tag value.
  // Always return true so the iteration continues.
  bool do_entry(JvmtiTagMapKey& key, jlong& value) {
    for (int i = 0; i < _tag_count; i++) {
      if (_tags[i] == value) {
        // The reference in this tag map could be the only (implicitly weak)
        // reference to that object. If we hand it out, we need to keep it live wrt
        // SATB marking similar to other j.l.ref.Reference referents. This is
        // achieved by using a phantom load in the object() accessor.
        oop o = key.object();
        if (o == nullptr) {
          _some_dead_found = true;
          // skip this whole entry
          return true;
        }
        assert(o != nullptr && Universe::heap()->is_in(o), "sanity check");
        jobject ref = JNIHandles::make_local(_thread, o);
        _object_results->append(ref);
        _tag_results->append(value);
      }
    }
    return true;
  }

  // return the results from the collection
  //
  jvmtiError result(jint* count_ptr, jobject** object_result_ptr, jlong** tag_result_ptr) {
    jvmtiError error;
    int count = _object_results->length();
    assert(count >= 0, "sanity check");

    // if object_result_ptr is not null then allocate the result and copy
    // in the object references.
    if (object_result_ptr != nullptr) {
      error = _env->Allocate(count * sizeof(jobject), (unsigned char**)object_result_ptr);
      if (error != JVMTI_ERROR_NONE) {
        return error;
      }
      for (int i=0; i<count; i++) {
        (*object_result_ptr)[i] = _object_results->at(i);
      }
    }

    // if tag_result_ptr is not null then allocate the result and copy
    // in the tag values.
    if (tag_result_ptr != nullptr) {
      error = _env->Allocate(count * sizeof(jlong), (unsigned char**)tag_result_ptr);
      if (error != JVMTI_ERROR_NONE) {
        if (object_result_ptr != nullptr) {
          _env->Deallocate((unsigned char*)object_result_ptr);
        }
        return error;
      }
      for (int i=0; i<count; i++) {
        (*tag_result_ptr)[i] = (jlong)_tag_results->at(i);
      }
    }

    *count_ptr = count;
    return JVMTI_ERROR_NONE;
  }
};

// return the list of objects with the specified tags
jvmtiError JvmtiTagMap::get_objects_with_tags(const jlong* tags,
  jint count, jint* count_ptr, jobject** object_result_ptr, jlong** tag_result_ptr) {

  TagObjectCollector collector(env(), tags, count);
  {
    // iterate over all tagged objects
    MutexLocker ml(lock(), Mutex::_no_safepoint_check_flag);
    // Can't post ObjectFree events here from a JavaThread, so this
    // will race with the gc_notification thread in the tiny
    // window where the object is not marked but hasn't been notified that
    // it is collected yet.
    entry_iterate(&collector);
  }
  return collector.result(count_ptr, object_result_ptr, tag_result_ptr);
}

// helper to map a jvmtiHeapReferenceKind to an old style jvmtiHeapRootKind
// (not performance critical as only used for roots)
static jvmtiHeapRootKind toJvmtiHeapRootKind(jvmtiHeapReferenceKind kind) {
  switch (kind) {
    case JVMTI_HEAP_REFERENCE_JNI_GLOBAL:   return JVMTI_HEAP_ROOT_JNI_GLOBAL;
    case JVMTI_HEAP_REFERENCE_SYSTEM_CLASS: return JVMTI_HEAP_ROOT_SYSTEM_CLASS;
    case JVMTI_HEAP_REFERENCE_STACK_LOCAL:  return JVMTI_HEAP_ROOT_STACK_LOCAL;
    case JVMTI_HEAP_REFERENCE_JNI_LOCAL:    return JVMTI_HEAP_ROOT_JNI_LOCAL;
    case JVMTI_HEAP_REFERENCE_THREAD:       return JVMTI_HEAP_ROOT_THREAD;
    case JVMTI_HEAP_REFERENCE_OTHER:        return JVMTI_HEAP_ROOT_OTHER;
    default: ShouldNotReachHere();          return JVMTI_HEAP_ROOT_OTHER;
  }
}

// Base class for all heap walk contexts. The base class maintains a flag
// to indicate if the context is valid or not.
class HeapWalkContext {
 private:
  bool _valid;
 public:
  HeapWalkContext(bool valid)                   { _valid = valid; }
  void invalidate()                             { _valid = false; }
  bool is_valid() const                         { return _valid; }
};

// A basic heap walk context for the deprecated heap walking functions.
// The context for a basic heap walk are the callbacks and fields used by
// the referrer caching scheme.
class BasicHeapWalkContext: public HeapWalkContext {
 private:
  jvmtiHeapRootCallback _heap_root_callback;
  jvmtiStackReferenceCallback _stack_ref_callback;
  jvmtiObjectReferenceCallback _object_ref_callback;

  // used for caching
  oop _last_referrer;
  jlong _last_referrer_tag;

 public:
  BasicHeapWalkContext() : HeapWalkContext(false) { }

  BasicHeapWalkContext(jvmtiHeapRootCallback heap_root_callback,
                       jvmtiStackReferenceCallback stack_ref_callback,
                       jvmtiObjectReferenceCallback object_ref_callback) :
    HeapWalkContext(true),
    _heap_root_callback(heap_root_callback),
    _stack_ref_callback(stack_ref_callback),
    _object_ref_callback(object_ref_callback),
    _last_referrer(nullptr),
    _last_referrer_tag(0) {
  }

  // accessors
  jvmtiHeapRootCallback heap_root_callback() const         { return _heap_root_callback; }
  jvmtiStackReferenceCallback stack_ref_callback() const   { return _stack_ref_callback; }
  jvmtiObjectReferenceCallback object_ref_callback() const { return _object_ref_callback;  }

  oop last_referrer() const               { return _last_referrer; }
  void set_last_referrer(oop referrer)    { _last_referrer = referrer; }
  jlong last_referrer_tag() const         { return _last_referrer_tag; }
  void set_last_referrer_tag(jlong value) { _last_referrer_tag = value; }
};

// The advanced heap walk context for the FollowReferences functions.
// The context is the callbacks, and the fields used for filtering.
class AdvancedHeapWalkContext: public HeapWalkContext {
 private:
  jint _heap_filter;
  Klass* _klass_filter;
  const jvmtiHeapCallbacks* _heap_callbacks;

 public:
  AdvancedHeapWalkContext() : HeapWalkContext(false) { }

  AdvancedHeapWalkContext(jint heap_filter,
                           Klass* klass_filter,
                           const jvmtiHeapCallbacks* heap_callbacks) :
    HeapWalkContext(true),
    _heap_filter(heap_filter),
    _klass_filter(klass_filter),
    _heap_callbacks(heap_callbacks) {
  }

  // accessors
  jint heap_filter() const         { return _heap_filter; }
  Klass* klass_filter() const      { return _klass_filter; }

  jvmtiHeapReferenceCallback heap_reference_callback() const {
    return _heap_callbacks->heap_reference_callback;
  };
  jvmtiPrimitiveFieldCallback primitive_field_callback() const {
    return _heap_callbacks->primitive_field_callback;
  }
  jvmtiArrayPrimitiveValueCallback array_primitive_value_callback() const {
    return _heap_callbacks->array_primitive_value_callback;
  }
  jvmtiStringPrimitiveValueCallback string_primitive_value_callback() const {
    return _heap_callbacks->string_primitive_value_callback;
  }
};

// The CallbackInvoker is a class with static functions that the heap walk can call
// into to invoke callbacks. It works in one of two modes. The "basic" mode is
// used for the deprecated IterateOverReachableObjects functions. The "advanced"
// mode is for the newer FollowReferences function which supports a lot of
// additional callbacks.
class CallbackInvoker : AllStatic {
 private:
  // heap walk styles
  enum { basic, advanced };
  static int _heap_walk_type;
  static bool is_basic_heap_walk()           { return _heap_walk_type == basic; }
  static bool is_advanced_heap_walk()        { return _heap_walk_type == advanced; }

  // context for basic style heap walk
  static BasicHeapWalkContext _basic_context;
  static BasicHeapWalkContext* basic_context() {
    assert(_basic_context.is_valid(), "invalid");
    return &_basic_context;
  }

  // context for advanced style heap walk
  static AdvancedHeapWalkContext _advanced_context;
  static AdvancedHeapWalkContext* advanced_context() {
    assert(_advanced_context.is_valid(), "invalid");
    return &_advanced_context;
  }

  // context needed for all heap walks
  static JvmtiTagMap* _tag_map;
  static const void* _user_data;
  static GrowableArray<oop>* _visit_stack;
  static JVMTIBitSet* _bitset;

  // accessors
  static JvmtiTagMap* tag_map()                        { return _tag_map; }
  static const void* user_data()                       { return _user_data; }
  static GrowableArray<oop>* visit_stack()             { return _visit_stack; }

  // if the object hasn't been visited then push it onto the visit stack
  // so that it will be visited later
  static inline bool check_for_visit(oop obj) {
    if (!_bitset->is_marked(obj)) visit_stack()->push(obj);
    return true;
  }

  // invoke basic style callbacks
  static inline bool invoke_basic_heap_root_callback
    (jvmtiHeapRootKind root_kind, oop obj);
  static inline bool invoke_basic_stack_ref_callback
    (jvmtiHeapRootKind root_kind, jlong thread_tag, jint depth, jmethodID method,
     int slot, oop obj);
  static inline bool invoke_basic_object_reference_callback
    (jvmtiObjectReferenceKind ref_kind, oop referrer, oop referree, jint index);

  // invoke advanced style callbacks
  static inline bool invoke_advanced_heap_root_callback
    (jvmtiHeapReferenceKind ref_kind, oop obj);
  static inline bool invoke_advanced_stack_ref_callback
    (jvmtiHeapReferenceKind ref_kind, jlong thread_tag, jlong tid, int depth,
     jmethodID method, jlocation bci, jint slot, oop obj);
  static inline bool invoke_advanced_object_reference_callback
    (jvmtiHeapReferenceKind ref_kind, oop referrer, oop referree, jint index);

  // used to report the value of primitive fields
  static inline bool report_primitive_field
    (jvmtiHeapReferenceKind ref_kind, oop obj, jint index, address addr, char type);

 public:
  // initialize for basic mode
  static void initialize_for_basic_heap_walk(JvmtiTagMap* tag_map,
                                             GrowableArray<oop>* visit_stack,
                                             const void* user_data,
                                             BasicHeapWalkContext context,
                                             JVMTIBitSet* bitset);

  // initialize for advanced mode
  static void initialize_for_advanced_heap_walk(JvmtiTagMap* tag_map,
                                                GrowableArray<oop>* visit_stack,
                                                const void* user_data,
                                                AdvancedHeapWalkContext context,
                                                JVMTIBitSet* bitset);

   // functions to report roots
  static inline bool report_simple_root(jvmtiHeapReferenceKind kind, oop o);
  static inline bool report_jni_local_root(jlong thread_tag, jlong tid, jint depth,
    jmethodID m, oop o);
  static inline bool report_stack_ref_root(jlong thread_tag, jlong tid, jint depth,
    jmethodID method, jlocation bci, jint slot, oop o);

  // functions to report references
  static inline bool report_array_element_reference(oop referrer, oop referree, jint index);
  static inline bool report_class_reference(oop referrer, oop referree);
  static inline bool report_class_loader_reference(oop referrer, oop referree);
  static inline bool report_signers_reference(oop referrer, oop referree);
  static inline bool report_protection_domain_reference(oop referrer, oop referree);
  static inline bool report_superclass_reference(oop referrer, oop referree);
  static inline bool report_interface_reference(oop referrer, oop referree);
  static inline bool report_static_field_reference(oop referrer, oop referree, jint slot);
  static inline bool report_field_reference(oop referrer, oop referree, jint slot);
  static inline bool report_constant_pool_reference(oop referrer, oop referree, jint index);
  static inline bool report_primitive_array_values(oop array);
  static inline bool report_string_value(oop str);
  static inline bool report_primitive_instance_field(oop o, jint index, address value, char type);
  static inline bool report_primitive_static_field(oop o, jint index, address value, char type);
};

// statics
int CallbackInvoker::_heap_walk_type;
BasicHeapWalkContext CallbackInvoker::_basic_context;
AdvancedHeapWalkContext CallbackInvoker::_advanced_context;
JvmtiTagMap* CallbackInvoker::_tag_map;
const void* CallbackInvoker::_user_data;
GrowableArray<oop>* CallbackInvoker::_visit_stack;
JVMTIBitSet* CallbackInvoker::_bitset;

// initialize for basic heap walk (IterateOverReachableObjects et al)
void CallbackInvoker::initialize_for_basic_heap_walk(JvmtiTagMap* tag_map,
                                                     GrowableArray<oop>* visit_stack,
                                                     const void* user_data,
                                                     BasicHeapWalkContext context,
                                                     JVMTIBitSet* bitset) {
  _tag_map = tag_map;
  _visit_stack = visit_stack;
  _user_data = user_data;
  _basic_context = context;
  _advanced_context.invalidate();       // will trigger assertion if used
  _heap_walk_type = basic;
  _bitset = bitset;
}

// initialize for advanced heap walk (FollowReferences)
void CallbackInvoker::initialize_for_advanced_heap_walk(JvmtiTagMap* tag_map,
                                                        GrowableArray<oop>* visit_stack,
                                                        const void* user_data,
                                                        AdvancedHeapWalkContext context,
                                                        JVMTIBitSet* bitset) {
  _tag_map = tag_map;
  _visit_stack = visit_stack;
  _user_data = user_data;
  _advanced_context = context;
  _basic_context.invalidate();      // will trigger assertion if used
  _heap_walk_type = advanced;
  _bitset = bitset;
}


// invoke basic style heap root callback
inline bool CallbackInvoker::invoke_basic_heap_root_callback(jvmtiHeapRootKind root_kind, oop obj) {
  // if we heap roots should be reported
  jvmtiHeapRootCallback cb = basic_context()->heap_root_callback();
  if (cb == nullptr) {
    return check_for_visit(obj);
  }

  CallbackWrapper wrapper(tag_map(), obj);
  jvmtiIterationControl control = (*cb)(root_kind,
                                        wrapper.klass_tag(),
                                        wrapper.obj_size(),
                                        wrapper.obj_tag_p(),
                                        (void*)user_data());
  // push root to visit stack when following references
  if (control == JVMTI_ITERATION_CONTINUE &&
      basic_context()->object_ref_callback() != nullptr) {
    visit_stack()->push(obj);
  }
  return control != JVMTI_ITERATION_ABORT;
}

// invoke basic style stack ref callback
inline bool CallbackInvoker::invoke_basic_stack_ref_callback(jvmtiHeapRootKind root_kind,
                                                             jlong thread_tag,
                                                             jint depth,
                                                             jmethodID method,
                                                             int slot,
                                                             oop obj) {
  // if we stack refs should be reported
  jvmtiStackReferenceCallback cb = basic_context()->stack_ref_callback();
  if (cb == nullptr) {
    return check_for_visit(obj);
  }

  CallbackWrapper wrapper(tag_map(), obj);
  jvmtiIterationControl control = (*cb)(root_kind,
                                        wrapper.klass_tag(),
                                        wrapper.obj_size(),
                                        wrapper.obj_tag_p(),
                                        thread_tag,
                                        depth,
                                        method,
                                        slot,
                                        (void*)user_data());
  // push root to visit stack when following references
  if (control == JVMTI_ITERATION_CONTINUE &&
      basic_context()->object_ref_callback() != nullptr) {
    visit_stack()->push(obj);
  }
  return control != JVMTI_ITERATION_ABORT;
}

// invoke basic style object reference callback
inline bool CallbackInvoker::invoke_basic_object_reference_callback(jvmtiObjectReferenceKind ref_kind,
                                                                    oop referrer,
                                                                    oop referree,
                                                                    jint index) {

  BasicHeapWalkContext* context = basic_context();

  // callback requires the referrer's tag. If it's the same referrer
  // as the last call then we use the cached value.
  jlong referrer_tag;
  if (referrer == context->last_referrer()) {
    referrer_tag = context->last_referrer_tag();
  } else {
    referrer_tag = tag_for(tag_map(), referrer);
  }

  // do the callback
  CallbackWrapper wrapper(tag_map(), referree);
  jvmtiObjectReferenceCallback cb = context->object_ref_callback();
  jvmtiIterationControl control = (*cb)(ref_kind,
                                        wrapper.klass_tag(),
                                        wrapper.obj_size(),
                                        wrapper.obj_tag_p(),
                                        referrer_tag,
                                        index,
                                        (void*)user_data());

  // record referrer and referrer tag. For self-references record the
  // tag value from the callback as this might differ from referrer_tag.
  context->set_last_referrer(referrer);
  if (referrer == referree) {
    context->set_last_referrer_tag(*wrapper.obj_tag_p());
  } else {
    context->set_last_referrer_tag(referrer_tag);
  }

  if (control == JVMTI_ITERATION_CONTINUE) {
    return check_for_visit(referree);
  } else {
    return control != JVMTI_ITERATION_ABORT;
  }
}

// invoke advanced style heap root callback
inline bool CallbackInvoker::invoke_advanced_heap_root_callback(jvmtiHeapReferenceKind ref_kind,
                                                                oop obj) {
  AdvancedHeapWalkContext* context = advanced_context();

  // check that callback is provided
  jvmtiHeapReferenceCallback cb = context->heap_reference_callback();
  if (cb == nullptr) {
    return check_for_visit(obj);
  }

  // apply class filter
  if (is_filtered_by_klass_filter(obj, context->klass_filter())) {
    return check_for_visit(obj);
  }

  // setup the callback wrapper
  CallbackWrapper wrapper(tag_map(), obj);

  // apply tag filter
  if (is_filtered_by_heap_filter(wrapper.obj_tag(),
                                 wrapper.klass_tag(),
                                 context->heap_filter())) {
    return check_for_visit(obj);
  }

  // for arrays we need the length, otherwise -1
  jint len = (jint)(obj->is_array() ? arrayOop(obj)->length() : -1);

  // invoke the callback
  jint res  = (*cb)(ref_kind,
                    nullptr, // referrer info
                    wrapper.klass_tag(),
                    0,    // referrer_class_tag is 0 for heap root
                    wrapper.obj_size(),
                    wrapper.obj_tag_p(),
                    nullptr, // referrer_tag_p
                    len,
                    (void*)user_data());
  if (res & JVMTI_VISIT_ABORT) {
    return false;// referrer class tag
  }
  if (res & JVMTI_VISIT_OBJECTS) {
    check_for_visit(obj);
  }
  return true;
}

// report a reference from a thread stack to an object
inline bool CallbackInvoker::invoke_advanced_stack_ref_callback(jvmtiHeapReferenceKind ref_kind,
                                                                jlong thread_tag,
                                                                jlong tid,
                                                                int depth,
                                                                jmethodID method,
                                                                jlocation bci,
                                                                jint slot,
                                                                oop obj) {
  AdvancedHeapWalkContext* context = advanced_context();

  // check that callback is provider
  jvmtiHeapReferenceCallback cb = context->heap_reference_callback();
  if (cb == nullptr) {
    return check_for_visit(obj);
  }

  // apply class filter
  if (is_filtered_by_klass_filter(obj, context->klass_filter())) {
    return check_for_visit(obj);
  }

  // setup the callback wrapper
  CallbackWrapper wrapper(tag_map(), obj);

  // apply tag filter
  if (is_filtered_by_heap_filter(wrapper.obj_tag(),
                                 wrapper.klass_tag(),
                                 context->heap_filter())) {
    return check_for_visit(obj);
  }

  // setup the referrer info
  jvmtiHeapReferenceInfo reference_info;
  reference_info.stack_local.thread_tag = thread_tag;
  reference_info.stack_local.thread_id = tid;
  reference_info.stack_local.depth = depth;
  reference_info.stack_local.method = method;
  reference_info.stack_local.location = bci;
  reference_info.stack_local.slot = slot;

  // for arrays we need the length, otherwise -1
  jint len = (jint)(obj->is_array() ? arrayOop(obj)->length() : -1);

  // call into the agent
  int res = (*cb)(ref_kind,
                  &reference_info,
                  wrapper.klass_tag(),
                  0,    // referrer_class_tag is 0 for heap root (stack)
                  wrapper.obj_size(),
                  wrapper.obj_tag_p(),
                  nullptr, // referrer_tag is 0 for root
                  len,
                  (void*)user_data());

  if (res & JVMTI_VISIT_ABORT) {
    return false;
  }
  if (res & JVMTI_VISIT_OBJECTS) {
    check_for_visit(obj);
  }
  return true;
}

// This mask is used to pass reference_info to a jvmtiHeapReferenceCallback
// only for ref_kinds defined by the JVM TI spec. Otherwise, null is passed.
#define REF_INFO_MASK  ((1 << JVMTI_HEAP_REFERENCE_FIELD)         \
                      | (1 << JVMTI_HEAP_REFERENCE_STATIC_FIELD)  \
                      | (1 << JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT) \
                      | (1 << JVMTI_HEAP_REFERENCE_CONSTANT_POOL) \
                      | (1 << JVMTI_HEAP_REFERENCE_STACK_LOCAL)   \
                      | (1 << JVMTI_HEAP_REFERENCE_JNI_LOCAL))

// invoke the object reference callback to report a reference
inline bool CallbackInvoker::invoke_advanced_object_reference_callback(jvmtiHeapReferenceKind ref_kind,
                                                                       oop referrer,
                                                                       oop obj,
                                                                       jint index)
{
  // field index is only valid field in reference_info
  static jvmtiHeapReferenceInfo reference_info = { 0 };

  AdvancedHeapWalkContext* context = advanced_context();

  // check that callback is provider
  jvmtiHeapReferenceCallback cb = context->heap_reference_callback();
  if (cb == nullptr) {
    return check_for_visit(obj);
  }

  // apply class filter
  if (is_filtered_by_klass_filter(obj, context->klass_filter())) {
    return check_for_visit(obj);
  }

  // setup the callback wrapper
  TwoOopCallbackWrapper wrapper(tag_map(), referrer, obj);

  // apply tag filter
  if (is_filtered_by_heap_filter(wrapper.obj_tag(),
                                 wrapper.klass_tag(),
                                 context->heap_filter())) {
    return check_for_visit(obj);
  }

  // field index is only valid field in reference_info
  reference_info.field.index = index;

  // for arrays we need the length, otherwise -1
  jint len = (jint)(obj->is_array() ? arrayOop(obj)->length() : -1);

  // invoke the callback
  int res = (*cb)(ref_kind,
                  (REF_INFO_MASK & (1 << ref_kind)) ? &reference_info : nullptr,
                  wrapper.klass_tag(),
                  wrapper.referrer_klass_tag(),
                  wrapper.obj_size(),
                  wrapper.obj_tag_p(),
                  wrapper.referrer_tag_p(),
                  len,
                  (void*)user_data());

  if (res & JVMTI_VISIT_ABORT) {
    return false;
  }
  if (res & JVMTI_VISIT_OBJECTS) {
    check_for_visit(obj);
  }
  return true;
}

// report a "simple root"
inline bool CallbackInvoker::report_simple_root(jvmtiHeapReferenceKind kind, oop obj) {
  assert(kind != JVMTI_HEAP_REFERENCE_STACK_LOCAL &&
         kind != JVMTI_HEAP_REFERENCE_JNI_LOCAL, "not a simple root");

  if (is_basic_heap_walk()) {
    // map to old style root kind
    jvmtiHeapRootKind root_kind = toJvmtiHeapRootKind(kind);
    return invoke_basic_heap_root_callback(root_kind, obj);
  } else {
    assert(is_advanced_heap_walk(), "wrong heap walk type");
    return invoke_advanced_heap_root_callback(kind, obj);
  }
}


// invoke the primitive array values
inline bool CallbackInvoker::report_primitive_array_values(oop obj) {
  assert(obj->is_typeArray(), "not a primitive array");

  AdvancedHeapWalkContext* context = advanced_context();
  assert(context->array_primitive_value_callback() != nullptr, "no callback");

  // apply class filter
  if (is_filtered_by_klass_filter(obj, context->klass_filter())) {
    return true;
  }

  CallbackWrapper wrapper(tag_map(), obj);

  // apply tag filter
  if (is_filtered_by_heap_filter(wrapper.obj_tag(),
                                 wrapper.klass_tag(),
                                 context->heap_filter())) {
    return true;
  }

  // invoke the callback
  int res = invoke_array_primitive_value_callback(context->array_primitive_value_callback(),
                                                  &wrapper,
                                                  obj,
                                                  (void*)user_data());
  return (!(res & JVMTI_VISIT_ABORT));
}

// invoke the string value callback
inline bool CallbackInvoker::report_string_value(oop str) {
  assert(str->klass() == vmClasses::String_klass(), "not a string");

  AdvancedHeapWalkContext* context = advanced_context();
  assert(context->string_primitive_value_callback() != nullptr, "no callback");

  // apply class filter
  if (is_filtered_by_klass_filter(str, context->klass_filter())) {
    return true;
  }

  CallbackWrapper wrapper(tag_map(), str);

  // apply tag filter
  if (is_filtered_by_heap_filter(wrapper.obj_tag(),
                                 wrapper.klass_tag(),
                                 context->heap_filter())) {
    return true;
  }

  // invoke the callback
  int res = invoke_string_value_callback(context->string_primitive_value_callback(),
                                         &wrapper,
                                         str,
                                         (void*)user_data());
  return (!(res & JVMTI_VISIT_ABORT));
}

// invoke the primitive field callback
inline bool CallbackInvoker::report_primitive_field(jvmtiHeapReferenceKind ref_kind,
                                                    oop obj,
                                                    jint index,
                                                    address addr,
                                                    char type)
{
  // for primitive fields only the index will be set
  static jvmtiHeapReferenceInfo reference_info = { 0 };

  AdvancedHeapWalkContext* context = advanced_context();
  assert(context->primitive_field_callback() != nullptr, "no callback");

  // apply class filter
  if (is_filtered_by_klass_filter(obj, context->klass_filter())) {
    return true;
  }

  CallbackWrapper wrapper(tag_map(), obj);

  // apply tag filter
  if (is_filtered_by_heap_filter(wrapper.obj_tag(),
                                 wrapper.klass_tag(),
                                 context->heap_filter())) {
    return true;
  }

  // the field index in the referrer
  reference_info.field.index = index;

  // map the type
  jvmtiPrimitiveType value_type = (jvmtiPrimitiveType)type;

  // setup the jvalue
  jvalue value;
  copy_to_jvalue(&value, addr, value_type);

  jvmtiPrimitiveFieldCallback cb = context->primitive_field_callback();
  int res = (*cb)(ref_kind,
                  &reference_info,
                  wrapper.klass_tag(),
                  wrapper.obj_tag_p(),
                  value,
                  value_type,
                  (void*)user_data());
  return (!(res & JVMTI_VISIT_ABORT));
}


// instance field
inline bool CallbackInvoker::report_primitive_instance_field(oop obj,
                                                             jint index,
                                                             address value,
                                                             char type) {
  return report_primitive_field(JVMTI_HEAP_REFERENCE_FIELD,
                                obj,
                                index,
                                value,
                                type);
}

// static field
inline bool CallbackInvoker::report_primitive_static_field(oop obj,
                                                           jint index,
                                                           address value,
                                                           char type) {
  return report_primitive_field(JVMTI_HEAP_REFERENCE_STATIC_FIELD,
                                obj,
                                index,
                                value,
                                type);
}

// report a JNI local (root object) to the profiler
inline bool CallbackInvoker::report_jni_local_root(jlong thread_tag, jlong tid, jint depth, jmethodID m, oop obj) {
  if (is_basic_heap_walk()) {
    return invoke_basic_stack_ref_callback(JVMTI_HEAP_ROOT_JNI_LOCAL,
                                           thread_tag,
                                           depth,
                                           m,
                                           -1,
                                           obj);
  } else {
    return invoke_advanced_stack_ref_callback(JVMTI_HEAP_REFERENCE_JNI_LOCAL,
                                              thread_tag, tid,
                                              depth,
                                              m,
                                              (jlocation)-1,
                                              -1,
                                              obj);
  }
}


// report a local (stack reference, root object)
inline bool CallbackInvoker::report_stack_ref_root(jlong thread_tag,
                                                   jlong tid,
                                                   jint depth,
                                                   jmethodID method,
                                                   jlocation bci,
                                                   jint slot,
                                                   oop obj) {
  if (is_basic_heap_walk()) {
    return invoke_basic_stack_ref_callback(JVMTI_HEAP_ROOT_STACK_LOCAL,
                                           thread_tag,
                                           depth,
                                           method,
                                           slot,
                                           obj);
  } else {
    return invoke_advanced_stack_ref_callback(JVMTI_HEAP_REFERENCE_STACK_LOCAL,
                                              thread_tag,
                                              tid,
                                              depth,
                                              method,
                                              bci,
                                              slot,
                                              obj);
  }
}

// report an object referencing a class.
inline bool CallbackInvoker::report_class_reference(oop referrer, oop referree) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_CLASS, referrer, referree, -1);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_CLASS, referrer, referree, -1);
  }
}

// report a class referencing its class loader.
inline bool CallbackInvoker::report_class_loader_reference(oop referrer, oop referree) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_CLASS_LOADER, referrer, referree, -1);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_CLASS_LOADER, referrer, referree, -1);
  }
}

// report a class referencing its signers.
inline bool CallbackInvoker::report_signers_reference(oop referrer, oop referree) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_SIGNERS, referrer, referree, -1);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_SIGNERS, referrer, referree, -1);
  }
}

// report a class referencing its protection domain..
inline bool CallbackInvoker::report_protection_domain_reference(oop referrer, oop referree) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_PROTECTION_DOMAIN, referrer, referree, -1);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_PROTECTION_DOMAIN, referrer, referree, -1);
  }
}

// report a class referencing its superclass.
inline bool CallbackInvoker::report_superclass_reference(oop referrer, oop referree) {
  if (is_basic_heap_walk()) {
    // Send this to be consistent with past implementation
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_CLASS, referrer, referree, -1);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_SUPERCLASS, referrer, referree, -1);
  }
}

// report a class referencing one of its interfaces.
inline bool CallbackInvoker::report_interface_reference(oop referrer, oop referree) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_INTERFACE, referrer, referree, -1);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_INTERFACE, referrer, referree, -1);
  }
}

// report a class referencing one of its static fields.
inline bool CallbackInvoker::report_static_field_reference(oop referrer, oop referree, jint slot) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_STATIC_FIELD, referrer, referree, slot);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_STATIC_FIELD, referrer, referree, slot);
  }
}

// report an array referencing an element object
inline bool CallbackInvoker::report_array_element_reference(oop referrer, oop referree, jint index) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_ARRAY_ELEMENT, referrer, referree, index);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT, referrer, referree, index);
  }
}

// report an object referencing an instance field object
inline bool CallbackInvoker::report_field_reference(oop referrer, oop referree, jint slot) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_FIELD, referrer, referree, slot);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_FIELD, referrer, referree, slot);
  }
}

// report an array referencing an element object
inline bool CallbackInvoker::report_constant_pool_reference(oop referrer, oop referree, jint index) {
  if (is_basic_heap_walk()) {
    return invoke_basic_object_reference_callback(JVMTI_REFERENCE_CONSTANT_POOL, referrer, referree, index);
  } else {
    return invoke_advanced_object_reference_callback(JVMTI_HEAP_REFERENCE_CONSTANT_POOL, referrer, referree, index);
  }
}

// A supporting closure used to process simple roots
class SimpleRootsClosure : public OopClosure {
 private:
  jvmtiHeapReferenceKind _kind;
  bool _continue;

  jvmtiHeapReferenceKind root_kind()    { return _kind; }

 public:
  void set_kind(jvmtiHeapReferenceKind kind) {
    _kind = kind;
    _continue = true;
  }

  inline bool stopped() {
    return !_continue;
  }

  void do_oop(oop* obj_p) {
    // iteration has terminated
    if (stopped()) {
      return;
    }

    oop o = NativeAccess<AS_NO_KEEPALIVE>::oop_load(obj_p);
    // ignore null
    if (o == nullptr) {
      return;
    }

    assert(Universe::heap()->is_in(o), "should be impossible");

    jvmtiHeapReferenceKind kind = root_kind();

    // invoke the callback
    _continue = CallbackInvoker::report_simple_root(kind, o);

  }
  virtual void do_oop(narrowOop* obj_p) { ShouldNotReachHere(); }
};

// A supporting closure used to process JNI locals
class JNILocalRootsClosure : public OopClosure {
 private:
  jlong _thread_tag;
  jlong _tid;
  jint _depth;
  jmethodID _method;
  bool _continue;
 public:
  void set_context(jlong thread_tag, jlong tid, jint depth, jmethodID method) {
    _thread_tag = thread_tag;
    _tid = tid;
    _depth = depth;
    _method = method;
    _continue = true;
  }

  inline bool stopped() {
    return !_continue;
  }

  void do_oop(oop* obj_p) {
    // iteration has terminated
    if (stopped()) {
      return;
    }

    oop o = *obj_p;
    // ignore null
    if (o == nullptr) {
      return;
    }

    // invoke the callback
    _continue = CallbackInvoker::report_jni_local_root(_thread_tag, _tid, _depth, _method, o);
  }
  virtual void do_oop(narrowOop* obj_p) { ShouldNotReachHere(); }
};

// Helper class to collect/report stack references.
class StackRefCollector {
private:
  JvmtiTagMap* _tag_map;
  JNILocalRootsClosure* _blk;
  // java_thread is needed only to report JNI local on top native frame;
  // I.e. it's required only for platform/carrier threads or mounted virtual threads.
  JavaThread* _java_thread;

  oop _threadObj;
  jlong _thread_tag;
  jlong _tid;

  bool _is_top_frame;
  int _depth;
  frame* _last_entry_frame;

  bool report_java_stack_refs(StackValueCollection* values, jmethodID method, jlocation bci, jint slot_offset);
  bool report_native_stack_refs(jmethodID method);

public:
  StackRefCollector(JvmtiTagMap* tag_map, JNILocalRootsClosure* blk, JavaThread* java_thread)
    : _tag_map(tag_map), _blk(blk), _java_thread(java_thread),
      _threadObj(nullptr), _thread_tag(0), _tid(0),
      _is_top_frame(true), _depth(0), _last_entry_frame(nullptr)
  {
  }

  bool set_thread(oop o);
  // Sets the thread and reports the reference to it with the specified kind.
  bool set_thread(jvmtiHeapReferenceKind kind, oop o);

  bool do_frame(vframe* vf);
  // Handles frames until vf->sender() is null.
  bool process_frames(vframe* vf);
};

bool StackRefCollector::set_thread(oop o) {
  _threadObj = o;
  _thread_tag = tag_for(_tag_map, _threadObj);
  _tid = java_lang_Thread::thread_id(_threadObj);

  _is_top_frame = true;
  _depth = 0;
  _last_entry_frame = nullptr;

  return true;
}

bool StackRefCollector::set_thread(jvmtiHeapReferenceKind kind, oop o) {
  return set_thread(o)
         && CallbackInvoker::report_simple_root(kind, _threadObj);
}

bool StackRefCollector::report_java_stack_refs(StackValueCollection* values, jmethodID method, jlocation bci, jint slot_offset) {
  for (int index = 0; index < values->size(); index++) {
    if (values->at(index)->type() == T_OBJECT) {
      oop obj = values->obj_at(index)();
      if (obj == nullptr) {
        continue;
      }
      // stack reference
      if (!CallbackInvoker::report_stack_ref_root(_thread_tag, _tid, _depth, method,
                                                  bci, slot_offset + index, obj)) {
        return false;
      }
    }
  }
  return true;
}

bool StackRefCollector::report_native_stack_refs(jmethodID method) {
  _blk->set_context(_thread_tag, _tid, _depth, method);
  if (_is_top_frame) {
    // JNI locals for the top frame.
    if (_java_thread != nullptr) {
      _java_thread->active_handles()->oops_do(_blk);
      if (_blk->stopped()) {
        return false;
      }
    }
  } else {
    if (_last_entry_frame != nullptr) {
      // JNI locals for the entry frame.
      assert(_last_entry_frame->is_entry_frame(), "checking");
      _last_entry_frame->entry_frame_call_wrapper()->handles()->oops_do(_blk);
      if (_blk->stopped()) {
        return false;
      }
    }
  }
  return true;
}

bool StackRefCollector::do_frame(vframe* vf) {
  if (vf->is_java_frame()) {
    // java frame (interpreted, compiled, ...)
    javaVFrame* jvf = javaVFrame::cast(vf);

    jmethodID method = jvf->method()->jmethod_id();

    if (!(jvf->method()->is_native())) {
      jlocation bci = (jlocation)jvf->bci();
      StackValueCollection* locals = jvf->locals();
      if (!report_java_stack_refs(locals, method, bci, 0)) {
        return false;
      }
      if (!report_java_stack_refs(jvf->expressions(), method, bci, locals->size())) {
        return false;
      }

      // Follow oops from compiled nmethod.
      if (jvf->cb() != nullptr && jvf->cb()->is_nmethod()) {
        _blk->set_context(_thread_tag, _tid, _depth, method);
        // Need to apply load barriers for unmounted vthreads.
        nmethod* nm = jvf->cb()->as_nmethod();
        nm->run_nmethod_entry_barrier();
        nm->oops_do(_blk);
        if (_blk->stopped()) {
          return false;
        }
      }
    } else {
      // native frame
      if (!report_native_stack_refs(method)) {
        return false;
      }
    }
    _last_entry_frame = nullptr;
    _depth++;
  } else {
    // externalVFrame - for an entry frame then we report the JNI locals
    // when we find the corresponding javaVFrame
    frame* fr = vf->frame_pointer();
    assert(fr != nullptr, "sanity check");
    if (fr->is_entry_frame()) {
      _last_entry_frame = fr;
    }
  }

  _is_top_frame = false;

  return true;
}

bool StackRefCollector::process_frames(vframe* vf) {
  while (vf != nullptr) {
    if (!do_frame(vf)) {
      return false;
    }
    vf = vf->sender();
  }
  return true;
}


// A VM operation to iterate over objects that are reachable from
// a set of roots or an initial object.
//
// For VM_HeapWalkOperation the set of roots used is :-
//
// - All JNI global references
// - All inflated monitors
// - All classes loaded by the boot class loader (or all classes
//     in the event that class unloading is disabled)
// - All java threads
// - For each java thread then all locals and JNI local references
//      on the thread's execution stack
// - All visible/explainable objects from Universes::oops_do
//
class VM_HeapWalkOperation: public VM_Operation {
 private:
  enum {
    initial_visit_stack_size = 4000
  };

  bool _is_advanced_heap_walk;                      // indicates FollowReferences
  JvmtiTagMap* _tag_map;
  Handle _initial_object;
  GrowableArray<oop>* _visit_stack;                 // the visit stack

  JVMTIBitSet _bitset;

  // Dead object tags in JvmtiTagMap
  GrowableArray<jlong>* _dead_objects;

  bool _following_object_refs;                      // are we following object references

  bool _reporting_primitive_fields;                 // optional reporting
  bool _reporting_primitive_array_values;
  bool _reporting_string_values;

  GrowableArray<oop>* create_visit_stack() {
    return new (mtServiceability) GrowableArray<oop>(initial_visit_stack_size, mtServiceability);
  }

  // accessors
  bool is_advanced_heap_walk() const               { return _is_advanced_heap_walk; }
  JvmtiTagMap* tag_map() const                     { return _tag_map; }
  Handle initial_object() const                    { return _initial_object; }

  bool is_following_references() const             { return _following_object_refs; }

  bool is_reporting_primitive_fields()  const      { return _reporting_primitive_fields; }
  bool is_reporting_primitive_array_values() const { return _reporting_primitive_array_values; }
  bool is_reporting_string_values() const          { return _reporting_string_values; }

  GrowableArray<oop>* visit_stack() const          { return _visit_stack; }

  // iterate over the various object types
  inline bool iterate_over_array(oop o);
  inline bool iterate_over_type_array(oop o);
  inline bool iterate_over_class(oop o);
  inline bool iterate_over_object(oop o);

  // root collection
  inline bool collect_simple_roots();
  inline bool collect_stack_roots();
  inline bool collect_stack_refs(JavaThread* java_thread, JNILocalRootsClosure* blk);
  inline bool collect_vthread_stack_refs(oop vt);

  // visit an object
  inline bool visit(oop o);

 public:
  VM_HeapWalkOperation(JvmtiTagMap* tag_map,
                       Handle initial_object,
                       BasicHeapWalkContext callbacks,
                       const void* user_data,
                       GrowableArray<jlong>* objects);

  VM_HeapWalkOperation(JvmtiTagMap* tag_map,
                       Handle initial_object,
                       AdvancedHeapWalkContext callbacks,
                       const void* user_data,
                       GrowableArray<jlong>* objects);

  ~VM_HeapWalkOperation();

  VMOp_Type type() const { return VMOp_HeapWalkOperation; }
  void doit();
};


VM_HeapWalkOperation::VM_HeapWalkOperation(JvmtiTagMap* tag_map,
                                           Handle initial_object,
                                           BasicHeapWalkContext callbacks,
                                           const void* user_data,
                                           GrowableArray<jlong>* objects) {
  _is_advanced_heap_walk = false;
  _tag_map = tag_map;
  _initial_object = initial_object;
  _following_object_refs = (callbacks.object_ref_callback() != nullptr);
  _reporting_primitive_fields = false;
  _reporting_primitive_array_values = false;
  _reporting_string_values = false;
  _visit_stack = create_visit_stack();
  _dead_objects = objects;

  CallbackInvoker::initialize_for_basic_heap_walk(tag_map, _visit_stack, user_data, callbacks, &_bitset);
}

VM_HeapWalkOperation::VM_HeapWalkOperation(JvmtiTagMap* tag_map,
                                           Handle initial_object,
                                           AdvancedHeapWalkContext callbacks,
                                           const void* user_data,
                                           GrowableArray<jlong>* objects) {
  _is_advanced_heap_walk = true;
  _tag_map = tag_map;
  _initial_object = initial_object;
  _following_object_refs = true;
  _reporting_primitive_fields = (callbacks.primitive_field_callback() != nullptr);;
  _reporting_primitive_array_values = (callbacks.array_primitive_value_callback() != nullptr);;
  _reporting_string_values = (callbacks.string_primitive_value_callback() != nullptr);;
  _visit_stack = create_visit_stack();
  _dead_objects = objects;
  CallbackInvoker::initialize_for_advanced_heap_walk(tag_map, _visit_stack, user_data, callbacks, &_bitset);
}

VM_HeapWalkOperation::~VM_HeapWalkOperation() {
  if (_following_object_refs) {
    assert(_visit_stack != nullptr, "checking");
    delete _visit_stack;
    _visit_stack = nullptr;
  }
}

// an array references its class and has a reference to
// each element in the array
inline bool VM_HeapWalkOperation::iterate_over_array(oop o) {
  objArrayOop array = objArrayOop(o);

  // array reference to its class
  oop mirror = ObjArrayKlass::cast(array->klass())->java_mirror();
  if (!CallbackInvoker::report_class_reference(o, mirror)) {
    return false;
  }

  // iterate over the array and report each reference to a
  // non-null element
  for (int index=0; index<array->length(); index++) {
    oop elem = array->obj_at(index);
    if (elem == nullptr) {
      continue;
    }

    // report the array reference o[index] = elem
    if (!CallbackInvoker::report_array_element_reference(o, elem, index)) {
      return false;
    }
  }
  return true;
}

// a type array references its class
inline bool VM_HeapWalkOperation::iterate_over_type_array(oop o) {
  Klass* k = o->klass();
  oop mirror = k->java_mirror();
  if (!CallbackInvoker::report_class_reference(o, mirror)) {
    return false;
  }

  // report the array contents if required
  if (is_reporting_primitive_array_values()) {
    if (!CallbackInvoker::report_primitive_array_values(o)) {
      return false;
    }
  }
  return true;
}

#ifdef ASSERT
// verify that a static oop field is in range
static inline bool verify_static_oop(InstanceKlass* ik,
                                     oop mirror, int offset) {
  address obj_p = cast_from_oop<address>(mirror) + offset;
  address start = (address)InstanceMirrorKlass::start_of_static_fields(mirror);
  address end = start + (java_lang_Class::static_oop_field_count(mirror) * heapOopSize);
  assert(end >= start, "sanity check");

  if (obj_p >= start && obj_p < end) {
    return true;
  } else {
    return false;
  }
}
#endif // #ifdef ASSERT

// a class references its super class, interfaces, class loader, ...
// and finally its static fields
inline bool VM_HeapWalkOperation::iterate_over_class(oop java_class) {
  int i;
  Klass* klass = java_lang_Class::as_Klass(java_class);

  if (klass->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(klass);

    // Ignore the class if it hasn't been initialized yet
    if (!ik->is_linked()) {
      return true;
    }

    // get the java mirror
    oop mirror = klass->java_mirror();

    // super (only if something more interesting than java.lang.Object)
    InstanceKlass* java_super = ik->java_super();
    if (java_super != nullptr && java_super != vmClasses::Object_klass()) {
      oop super = java_super->java_mirror();
      if (!CallbackInvoker::report_superclass_reference(mirror, super)) {
        return false;
      }
    }

    // class loader
    oop cl = ik->class_loader();
    if (cl != nullptr) {
      if (!CallbackInvoker::report_class_loader_reference(mirror, cl)) {
        return false;
      }
    }

    // protection domain
    oop pd = ik->protection_domain();
    if (pd != nullptr) {
      if (!CallbackInvoker::report_protection_domain_reference(mirror, pd)) {
        return false;
      }
    }

    // signers
    oop signers = ik->signers();
    if (signers != nullptr) {
      if (!CallbackInvoker::report_signers_reference(mirror, signers)) {
        return false;
      }
    }

    // references from the constant pool
    {
      ConstantPool* pool = ik->constants();
      for (int i = 1; i < pool->length(); i++) {
        constantTag tag = pool->tag_at(i).value();
        if (tag.is_string() || tag.is_klass() || tag.is_unresolved_klass()) {
          oop entry;
          if (tag.is_string()) {
            entry = pool->resolved_string_at(i);
            // If the entry is non-null it is resolved.
            if (entry == nullptr) {
              continue;
            }
          } else if (tag.is_klass()) {
            entry = pool->resolved_klass_at(i)->java_mirror();
          } else {
            // Code generated by JIT compilers might not resolve constant
            // pool entries.  Treat them as resolved if they are loaded.
            assert(tag.is_unresolved_klass(), "must be");
            constantPoolHandle cp(Thread::current(), pool);
            Klass* klass = ConstantPool::klass_at_if_loaded(cp, i);
            if (klass == nullptr) {
              continue;
            }
            entry = klass->java_mirror();
          }
          if (!CallbackInvoker::report_constant_pool_reference(mirror, entry, (jint)i)) {
            return false;
          }
        }
      }
    }

    // interfaces
    // (These will already have been reported as references from the constant pool
    //  but are specified by IterateOverReachableObjects and must be reported).
    Array<InstanceKlass*>* interfaces = ik->local_interfaces();
    for (i = 0; i < interfaces->length(); i++) {
      oop interf = interfaces->at(i)->java_mirror();
      if (interf == nullptr) {
        continue;
      }
      if (!CallbackInvoker::report_interface_reference(mirror, interf)) {
        return false;
      }
    }

    // iterate over the static fields

    ClassFieldMap* field_map = ClassFieldMap::create_map_of_static_fields(klass);
    for (i=0; i<field_map->field_count(); i++) {
      ClassFieldDescriptor* field = field_map->field_at(i);
      char type = field->field_type();
      if (!is_primitive_field_type(type)) {
        oop fld_o = mirror->obj_field(field->field_offset());
        assert(verify_static_oop(ik, mirror, field->field_offset()), "sanity check");
        if (fld_o != nullptr) {
          int slot = field->field_index();
          if (!CallbackInvoker::report_static_field_reference(mirror, fld_o, slot)) {
            delete field_map;
            return false;
          }
        }
      } else {
         if (is_reporting_primitive_fields()) {
           address addr = cast_from_oop<address>(mirror) + field->field_offset();
           int slot = field->field_index();
           if (!CallbackInvoker::report_primitive_static_field(mirror, slot, addr, type)) {
             delete field_map;
             return false;
          }
        }
      }
    }
    delete field_map;

    return true;
  }

  return true;
}

// an object references a class and its instance fields
// (static fields are ignored here as we report these as
// references from the class).
inline bool VM_HeapWalkOperation::iterate_over_object(oop o) {
  // reference to the class
  if (!CallbackInvoker::report_class_reference(o, o->klass()->java_mirror())) {
    return false;
  }

  // iterate over instance fields
  ClassFieldMap* field_map = JvmtiCachedClassFieldMap::get_map_of_instance_fields(o);
  for (int i=0; i<field_map->field_count(); i++) {
    ClassFieldDescriptor* field = field_map->field_at(i);
    char type = field->field_type();
    if (!is_primitive_field_type(type)) {
      oop fld_o = o->obj_field_access<AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF>(field->field_offset());
      // ignore any objects that aren't visible to profiler
      if (fld_o != nullptr) {
        assert(Universe::heap()->is_in(fld_o), "unsafe code should not "
               "have references to Klass* anymore");
        int slot = field->field_index();
        if (!CallbackInvoker::report_field_reference(o, fld_o, slot)) {
          return false;
        }
      }
    } else {
      if (is_reporting_primitive_fields()) {
        // primitive instance field
        address addr = cast_from_oop<address>(o) + field->field_offset();
        int slot = field->field_index();
        if (!CallbackInvoker::report_primitive_instance_field(o, slot, addr, type)) {
          return false;
        }
      }
    }
  }

  // if the object is a java.lang.String
  if (is_reporting_string_values() &&
      o->klass() == vmClasses::String_klass()) {
    if (!CallbackInvoker::report_string_value(o)) {
      return false;
    }
  }
  return true;
}


// Collects all simple (non-stack) roots except for threads;
// threads are handled in collect_stack_roots() as an optimization.
// if there's a heap root callback provided then the callback is
// invoked for each simple root.
// if an object reference callback is provided then all simple
// roots are pushed onto the marking stack so that they can be
// processed later
//
inline bool VM_HeapWalkOperation::collect_simple_roots() {
  SimpleRootsClosure blk;

  // JNI globals
  blk.set_kind(JVMTI_HEAP_REFERENCE_JNI_GLOBAL);
  JNIHandles::oops_do(&blk);
  if (blk.stopped()) {
    return false;
  }

  // Preloaded classes and loader from the system dictionary
  blk.set_kind(JVMTI_HEAP_REFERENCE_SYSTEM_CLASS);
  CLDToOopClosure cld_closure(&blk, ClassLoaderData::_claim_none);
  ClassLoaderDataGraph::always_strong_cld_do(&cld_closure);
  if (blk.stopped()) {
    return false;
  }

  // threads are now handled in collect_stack_roots()

  // Other kinds of roots maintained by HotSpot
  // Many of these won't be visible but others (such as instances of important
  // exceptions) will be visible.
  blk.set_kind(JVMTI_HEAP_REFERENCE_OTHER);
  Universe::vm_global()->oops_do(&blk);
  if (blk.stopped()) {
    return false;
  }

  return true;
}

// Reports the thread as JVMTI_HEAP_REFERENCE_THREAD,
// walks the stack of the thread, finds all references (locals
// and JNI calls) and reports these as stack references.
inline bool VM_HeapWalkOperation::collect_stack_refs(JavaThread* java_thread,
                                                     JNILocalRootsClosure* blk)
{
  oop threadObj = java_thread->threadObj();
  oop mounted_vt = java_thread->is_vthread_mounted() ? java_thread->vthread() : nullptr;
  if (mounted_vt != nullptr && !JvmtiEnvBase::is_vthread_alive(mounted_vt)) {
    mounted_vt = nullptr;
  }
  assert(threadObj != nullptr, "sanity check");

  StackRefCollector stack_collector(tag_map(), blk, java_thread);

  if (!java_thread->has_last_Java_frame()) {
    if (!stack_collector.set_thread(JVMTI_HEAP_REFERENCE_THREAD, threadObj)) {
      return false;
    }
    // no last java frame but there may be JNI locals
    blk->set_context(tag_for(_tag_map, threadObj), java_lang_Thread::thread_id(threadObj), 0, (jmethodID)nullptr);
    java_thread->active_handles()->oops_do(blk);
    return !blk->stopped();
  }
  // vframes are resource allocated
  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);

  RegisterMap reg_map(java_thread,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::include);

  // first handle mounted vthread (if any)
  if (mounted_vt != nullptr) {
    frame f = java_thread->last_frame();
    vframe* vf = vframe::new_vframe(&f, &reg_map, java_thread);
    // report virtual thread as JVMTI_HEAP_REFERENCE_OTHER
    if (!stack_collector.set_thread(JVMTI_HEAP_REFERENCE_OTHER, mounted_vt)) {
      return false;
    }
    // split virtual thread and carrier thread stacks by vthread entry ("enterSpecial") frame,
    // consider vthread entry frame as the last vthread stack frame
    while (vf != nullptr) {
      if (!stack_collector.do_frame(vf)) {
        return false;
      }
      if (vf->is_vthread_entry()) {
        break;
      }
      vf = vf->sender();
    }
  }
  // Platform or carrier thread.
  vframe* vf = JvmtiEnvBase::get_cthread_last_java_vframe(java_thread, &reg_map);
  if (!stack_collector.set_thread(JVMTI_HEAP_REFERENCE_THREAD, threadObj)) {
    return false;
  }
  return stack_collector.process_frames(vf);
}


// Collects the simple roots for all threads and collects all
// stack roots - for each thread it walks the execution
// stack to find all references and local JNI refs.
inline bool VM_HeapWalkOperation::collect_stack_roots() {
  JNILocalRootsClosure blk;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thread = jtiwh.next(); ) {
    oop threadObj = thread->threadObj();
    if (threadObj != nullptr && !thread->is_exiting() && !thread->is_hidden_from_external_view()) {
      if (!collect_stack_refs(thread, &blk)) {
        return false;
      }
    }
  }
  return true;
}

// Reports stack references for the unmounted virtual thread.
inline bool VM_HeapWalkOperation::collect_vthread_stack_refs(oop vt) {
  if (!JvmtiEnvBase::is_vthread_alive(vt)) {
    return true;
  }
  ContinuationWrapper cont(java_lang_VirtualThread::continuation(vt));
  if (cont.is_empty()) {
    return true;
  }
  assert(!cont.is_mounted(), "sanity check");

  stackChunkOop chunk = cont.last_nonempty_chunk();
  if (chunk == nullptr || chunk->is_empty()) {
    return true;
  }

  // vframes are resource allocated
  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);

  RegisterMap reg_map(cont.continuation(), RegisterMap::UpdateMap::include);

  JNILocalRootsClosure blk;
  // JavaThread is not required for unmounted virtual threads
  StackRefCollector stack_collector(tag_map(), &blk, nullptr);
  // reference to the vthread is already reported
  if (!stack_collector.set_thread(vt)) {
    return false;
  }

  frame fr = chunk->top_frame(&reg_map);
  vframe* vf = vframe::new_vframe(&fr, &reg_map, nullptr);
  return stack_collector.process_frames(vf);
}

// visit an object
// first mark the object as visited
// second get all the outbound references from this object (in other words, all
// the objects referenced by this object).
//
bool VM_HeapWalkOperation::visit(oop o) {
  // mark object as visited
  assert(!_bitset.is_marked(o), "can't visit same object more than once");
  _bitset.mark_obj(o);

  // instance
  if (o->is_instance()) {
    if (o->klass() == vmClasses::Class_klass()) {
      if (!java_lang_Class::is_primitive(o)) {
        // a java.lang.Class
        return iterate_over_class(o);
      }
    } else {
      // we report stack references only when initial object is not specified
      // (in the case we start from heap roots which include platform thread stack references)
      if (initial_object().is_null() && java_lang_VirtualThread::is_subclass(o->klass())) {
        if (!collect_vthread_stack_refs(o)) {
          return false;
        }
      }
      return iterate_over_object(o);
    }
  }

  // object array
  if (o->is_objArray()) {
    return iterate_over_array(o);
  }

  // type array
  if (o->is_typeArray()) {
    return iterate_over_type_array(o);
  }

  return true;
}

void VM_HeapWalkOperation::doit() {
  ResourceMark rm;
  ClassFieldMapCacheMark cm;

  JvmtiTagMap::check_hashmaps_for_heapwalk(_dead_objects);

  assert(visit_stack()->is_empty(), "visit stack must be empty");

  // the heap walk starts with an initial object or the heap roots
  if (initial_object().is_null()) {
    // can result in a big performance boost for an agent that is
    // focused on analyzing references in the thread stacks.
    if (!collect_stack_roots()) return;

    if (!collect_simple_roots()) return;
  } else {
    visit_stack()->push(initial_object()());
  }

  // object references required
  if (is_following_references()) {

    // visit each object until all reachable objects have been
    // visited or the callback asked to terminate the iteration.
    while (!visit_stack()->is_empty()) {
      oop o = visit_stack()->pop();
      if (!_bitset.is_marked(o)) {
        if (!visit(o)) {
          break;
        }
      }
    }
  }
}

// iterate over all objects that are reachable from a set of roots
void JvmtiTagMap::iterate_over_reachable_objects(jvmtiHeapRootCallback heap_root_callback,
                                                 jvmtiStackReferenceCallback stack_ref_callback,
                                                 jvmtiObjectReferenceCallback object_ref_callback,
                                                 const void* user_data) {
  // VTMS transitions must be disabled before the EscapeBarrier.
  JvmtiVTMSTransitionDisabler disabler;

  JavaThread* jt = JavaThread::current();
  EscapeBarrier eb(true, jt);
  eb.deoptimize_objects_all_threads();
  Arena dead_object_arena(mtServiceability);
  GrowableArray<jlong> dead_objects(&dead_object_arena, 10, 0, 0);

  {
    MutexLocker ml(Heap_lock);
    BasicHeapWalkContext context(heap_root_callback, stack_ref_callback, object_ref_callback);
    VM_HeapWalkOperation op(this, Handle(), context, user_data, &dead_objects);
    VMThread::execute(&op);
  }
  // Post events outside of Heap_lock
  post_dead_objects(&dead_objects);
}

// iterate over all objects that are reachable from a given object
void JvmtiTagMap::iterate_over_objects_reachable_from_object(jobject object,
                                                             jvmtiObjectReferenceCallback object_ref_callback,
                                                             const void* user_data) {
  oop obj = JNIHandles::resolve(object);
  Handle initial_object(Thread::current(), obj);

  Arena dead_object_arena(mtServiceability);
  GrowableArray<jlong> dead_objects(&dead_object_arena, 10, 0, 0);

  JvmtiVTMSTransitionDisabler disabler;

  {
    MutexLocker ml(Heap_lock);
    BasicHeapWalkContext context(nullptr, nullptr, object_ref_callback);
    VM_HeapWalkOperation op(this, initial_object, context, user_data, &dead_objects);
    VMThread::execute(&op);
  }
  // Post events outside of Heap_lock
  post_dead_objects(&dead_objects);
}

// follow references from an initial object or the GC roots
void JvmtiTagMap::follow_references(jint heap_filter,
                                    Klass* klass,
                                    jobject object,
                                    const jvmtiHeapCallbacks* callbacks,
                                    const void* user_data)
{
  // VTMS transitions must be disabled before the EscapeBarrier.
  JvmtiVTMSTransitionDisabler disabler;

  oop obj = JNIHandles::resolve(object);
  JavaThread* jt = JavaThread::current();
  Handle initial_object(jt, obj);
  // EA based optimizations that are tagged or reachable from initial_object are already reverted.
  EscapeBarrier eb(initial_object.is_null() &&
                   !(heap_filter & JVMTI_HEAP_FILTER_UNTAGGED),
                   jt);
  eb.deoptimize_objects_all_threads();

  Arena dead_object_arena(mtServiceability);
  GrowableArray<jlong> dead_objects(&dead_object_arena, 10, 0, 0);

  {
    MutexLocker ml(Heap_lock);
    AdvancedHeapWalkContext context(heap_filter, klass, callbacks);
    VM_HeapWalkOperation op(this, initial_object, context, user_data, &dead_objects);
    VMThread::execute(&op);
  }
  // Post events outside of Heap_lock
  post_dead_objects(&dead_objects);
}

// Verify gc_notification follows set_needs_cleaning.
DEBUG_ONLY(static bool notified_needs_cleaning = false;)

void JvmtiTagMap::set_needs_cleaning() {
  assert(SafepointSynchronize::is_at_safepoint(), "called in gc pause");
  assert(Thread::current()->is_VM_thread(), "should be the VM thread");
  // Can't assert !notified_needs_cleaning; a partial GC might be upgraded
  // to a full GC and do this twice without intervening gc_notification.
  DEBUG_ONLY(notified_needs_cleaning = true;)

  JvmtiEnvIterator it;
  for (JvmtiEnv* env = it.first(); env != nullptr; env = it.next(env)) {
    JvmtiTagMap* tag_map = env->tag_map_acquire();
    if (tag_map != nullptr) {
      tag_map->_needs_cleaning = !tag_map->is_empty();
    }
  }
}

void JvmtiTagMap::gc_notification(size_t num_dead_entries) {
  assert(notified_needs_cleaning, "missing GC notification");
  DEBUG_ONLY(notified_needs_cleaning = false;)

  // Notify ServiceThread if there's work to do.
  {
    MonitorLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
    _has_object_free_events = (num_dead_entries != 0);
    if (_has_object_free_events) ml.notify_all();
  }

  // If no dead entries then cancel cleaning requests.
  if (num_dead_entries == 0) {
    JvmtiEnvIterator it;
    for (JvmtiEnv* env = it.first(); env != nullptr; env = it.next(env)) {
      JvmtiTagMap* tag_map = env->tag_map_acquire();
      if (tag_map != nullptr) {
        MutexLocker ml (tag_map->lock(), Mutex::_no_safepoint_check_flag);
        tag_map->_needs_cleaning = false;
      }
    }
  }
}

// Used by ServiceThread to discover there is work to do.
bool JvmtiTagMap::has_object_free_events_and_reset() {
  assert_lock_strong(Service_lock);
  bool result = _has_object_free_events;
  _has_object_free_events = false;
  return result;
}

// Used by ServiceThread to clean up tagmaps.
void JvmtiTagMap::flush_all_object_free_events() {
  JavaThread* thread = JavaThread::current();
  JvmtiEnvIterator it;
  for (JvmtiEnv* env = it.first(); env != nullptr; env = it.next(env)) {
    JvmtiTagMap* tag_map = env->tag_map_acquire();
    if (tag_map != nullptr) {
      tag_map->flush_object_free_events();
      ThreadBlockInVM tbiv(thread); // Be safepoint-polite while looping.
    }
  }
}
