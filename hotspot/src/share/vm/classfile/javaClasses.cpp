/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_javaClasses.cpp.incl"

static bool find_field(instanceKlass* ik,
                       symbolOop name_symbol, symbolOop signature_symbol,
                       fieldDescriptor* fd,
                       bool allow_super = false) {
  if (allow_super)
    return ik->find_field(name_symbol, signature_symbol, fd) != NULL;
  else
    return ik->find_local_field(name_symbol, signature_symbol, fd);
}

// Helpful routine for computing field offsets at run time rather than hardcoding them
static void
compute_offset(int &dest_offset,
               klassOop klass_oop, symbolOop name_symbol, symbolOop signature_symbol,
               bool allow_super = false) {
  fieldDescriptor fd;
  instanceKlass* ik = instanceKlass::cast(klass_oop);
  if (!find_field(ik, name_symbol, signature_symbol, &fd, allow_super)) {
    ResourceMark rm;
    tty->print_cr("Invalid layout of %s at %s", ik->external_name(), name_symbol->as_C_string());
    fatal("Invalid layout of preloaded class");
  }
  dest_offset = fd.offset();
}

// Same as above but for "optional" offsets that might not be present in certain JDK versions
static void
compute_optional_offset(int& dest_offset,
                        klassOop klass_oop, symbolOop name_symbol, symbolOop signature_symbol,
                        bool allow_super = false) {
  fieldDescriptor fd;
  instanceKlass* ik = instanceKlass::cast(klass_oop);
  if (find_field(ik, name_symbol, signature_symbol, &fd, allow_super)) {
    dest_offset = fd.offset();
  }
}


Handle java_lang_String::basic_create(int length, bool tenured, TRAPS) {
  // Create the String object first, so there's a chance that the String
  // and the char array it points to end up in the same cache line.
  oop obj;
  if (tenured) {
    obj = instanceKlass::cast(SystemDictionary::String_klass())->allocate_permanent_instance(CHECK_NH);
  } else {
    obj = instanceKlass::cast(SystemDictionary::String_klass())->allocate_instance(CHECK_NH);
  }

  // Create the char array.  The String object must be handlized here
  // because GC can happen as a result of the allocation attempt.
  Handle h_obj(THREAD, obj);
  typeArrayOop buffer;
  if (tenured) {
    buffer = oopFactory::new_permanent_charArray(length, CHECK_NH);
  } else {
    buffer = oopFactory::new_charArray(length, CHECK_NH);
  }

  // Point the String at the char array
  obj = h_obj();
  set_value(obj, buffer);
  // No need to zero the offset, allocation zero'ed the entire String object
  assert(offset(obj) == 0, "initial String offset should be zero");
//set_offset(obj, 0);
  set_count(obj, length);

  return h_obj;
}

Handle java_lang_String::basic_create_from_unicode(jchar* unicode, int length, bool tenured, TRAPS) {
  Handle h_obj = basic_create(length, tenured, CHECK_NH);
  typeArrayOop buffer = value(h_obj());
  for (int index = 0; index < length; index++) {
    buffer->char_at_put(index, unicode[index]);
  }
  return h_obj;
}

Handle java_lang_String::create_from_unicode(jchar* unicode, int length, TRAPS) {
  return basic_create_from_unicode(unicode, length, false, CHECK_NH);
}

Handle java_lang_String::create_tenured_from_unicode(jchar* unicode, int length, TRAPS) {
  return basic_create_from_unicode(unicode, length, true, CHECK_NH);
}

oop java_lang_String::create_oop_from_unicode(jchar* unicode, int length, TRAPS) {
  Handle h_obj = basic_create_from_unicode(unicode, length, false, CHECK_0);
  return h_obj();
}

Handle java_lang_String::create_from_str(const char* utf8_str, TRAPS) {
  if (utf8_str == NULL) {
    return Handle();
  }
  int length = UTF8::unicode_length(utf8_str);
  Handle h_obj = basic_create(length, false, CHECK_NH);
  if (length > 0) {
    UTF8::convert_to_unicode(utf8_str, value(h_obj())->char_at_addr(0), length);
  }
  return h_obj;
}

oop java_lang_String::create_oop_from_str(const char* utf8_str, TRAPS) {
  Handle h_obj = create_from_str(utf8_str, CHECK_0);
  return h_obj();
}

Handle java_lang_String::create_from_symbol(symbolHandle symbol, TRAPS) {
  int length = UTF8::unicode_length((char*)symbol->bytes(), symbol->utf8_length());
  Handle h_obj = basic_create(length, false, CHECK_NH);
  if (length > 0) {
    UTF8::convert_to_unicode((char*)symbol->bytes(), value(h_obj())->char_at_addr(0), length);
  }
  return h_obj;
}

// Converts a C string to a Java String based on current encoding
Handle java_lang_String::create_from_platform_dependent_str(const char* str, TRAPS) {
  assert(str != NULL, "bad arguments");

  typedef jstring (*to_java_string_fn_t)(JNIEnv*, const char *);
  static to_java_string_fn_t _to_java_string_fn = NULL;

  if (_to_java_string_fn == NULL) {
    void *lib_handle = os::native_java_library();
    _to_java_string_fn = CAST_TO_FN_PTR(to_java_string_fn_t, hpi::dll_lookup(lib_handle, "NewStringPlatform"));
    if (_to_java_string_fn == NULL) {
      fatal("NewStringPlatform missing");
    }
  }

  jstring js = NULL;
  { JavaThread* thread = (JavaThread*)THREAD;
    assert(thread->is_Java_thread(), "must be java thread");
    HandleMark hm(thread);
    ThreadToNativeFromVM ttn(thread);
    js = (_to_java_string_fn)(thread->jni_environment(), str);
  }
  return Handle(THREAD, JNIHandles::resolve(js));
}

// Converts a Java String to a native C string that can be used for
// native OS calls.
char* java_lang_String::as_platform_dependent_str(Handle java_string, TRAPS) {

  typedef char* (*to_platform_string_fn_t)(JNIEnv*, jstring, bool*);
  static to_platform_string_fn_t _to_platform_string_fn = NULL;

  if (_to_platform_string_fn == NULL) {
    void *lib_handle = os::native_java_library();
    _to_platform_string_fn = CAST_TO_FN_PTR(to_platform_string_fn_t, hpi::dll_lookup(lib_handle, "GetStringPlatformChars"));
    if (_to_platform_string_fn == NULL) {
      fatal("GetStringPlatformChars missing");
    }
  }

  char *native_platform_string;
  { JavaThread* thread = (JavaThread*)THREAD;
    assert(thread->is_Java_thread(), "must be java thread");
    JNIEnv *env = thread->jni_environment();
    jstring js = (jstring) JNIHandles::make_local(env, java_string());
    bool is_copy;
    HandleMark hm(thread);
    ThreadToNativeFromVM ttn(thread);
    native_platform_string = (_to_platform_string_fn)(env, js, &is_copy);
    assert(is_copy == JNI_TRUE, "is_copy value changed");
    JNIHandles::destroy_local(js);
  }
  return native_platform_string;
}

Handle java_lang_String::char_converter(Handle java_string, jchar from_char, jchar to_char, TRAPS) {
  oop          obj    = java_string();
  // Typical usage is to convert all '/' to '.' in string.
  typeArrayOop value  = java_lang_String::value(obj);
  int          offset = java_lang_String::offset(obj);
  int          length = java_lang_String::length(obj);

  // First check if any from_char exist
  int index; // Declared outside, used later
  for (index = 0; index < length; index++) {
    if (value->char_at(index + offset) == from_char) {
      break;
    }
  }
  if (index == length) {
    // No from_char, so do not copy.
    return java_string;
  }

  // Create new UNICODE buffer. Must handlize value because GC
  // may happen during String and char array creation.
  typeArrayHandle h_value(THREAD, value);
  Handle string = basic_create(length, false, CHECK_NH);

  typeArrayOop from_buffer = h_value();
  typeArrayOop to_buffer   = java_lang_String::value(string());

  // Copy contents
  for (index = 0; index < length; index++) {
    jchar c = from_buffer->char_at(index + offset);
    if (c == from_char) {
      c = to_char;
    }
    to_buffer->char_at_put(index, c);
  }
  return string;
}

jchar* java_lang_String::as_unicode_string(oop java_string, int& length) {
  typeArrayOop value  = java_lang_String::value(java_string);
  int          offset = java_lang_String::offset(java_string);
               length = java_lang_String::length(java_string);

  jchar* result = NEW_RESOURCE_ARRAY(jchar, length);
  for (int index = 0; index < length; index++) {
    result[index] = value->char_at(index + offset);
  }
  return result;
}

symbolHandle java_lang_String::as_symbol(Handle java_string, TRAPS) {
  oop          obj    = java_string();
  typeArrayOop value  = java_lang_String::value(obj);
  int          offset = java_lang_String::offset(obj);
  int          length = java_lang_String::length(obj);
  jchar* base = (length == 0) ? NULL : value->char_at_addr(offset);
  symbolOop sym = SymbolTable::lookup_unicode(base, length, THREAD);
  return symbolHandle(THREAD, sym);
}

symbolOop java_lang_String::as_symbol_or_null(oop java_string) {
  typeArrayOop value  = java_lang_String::value(java_string);
  int          offset = java_lang_String::offset(java_string);
  int          length = java_lang_String::length(java_string);
  jchar* base = (length == 0) ? NULL : value->char_at_addr(offset);
  return SymbolTable::probe_unicode(base, length);
}


int java_lang_String::utf8_length(oop java_string) {
  typeArrayOop value  = java_lang_String::value(java_string);
  int          offset = java_lang_String::offset(java_string);
  int          length = java_lang_String::length(java_string);
  jchar* position = (length == 0) ? NULL : value->char_at_addr(offset);
  return UNICODE::utf8_length(position, length);
}

char* java_lang_String::as_utf8_string(oop java_string) {
  typeArrayOop value  = java_lang_String::value(java_string);
  int          offset = java_lang_String::offset(java_string);
  int          length = java_lang_String::length(java_string);
  jchar* position = (length == 0) ? NULL : value->char_at_addr(offset);
  return UNICODE::as_utf8(position, length);
}

char* java_lang_String::as_utf8_string(oop java_string, int start, int len) {
  typeArrayOop value  = java_lang_String::value(java_string);
  int          offset = java_lang_String::offset(java_string);
  int          length = java_lang_String::length(java_string);
  assert(start + len <= length, "just checking");
  jchar* position = value->char_at_addr(offset + start);
  return UNICODE::as_utf8(position, len);
}

bool java_lang_String::equals(oop java_string, jchar* chars, int len) {
  assert(SharedSkipVerify ||
         java_string->klass() == SystemDictionary::String_klass(),
         "must be java_string");
  typeArrayOop value  = java_lang_String::value(java_string);
  int          offset = java_lang_String::offset(java_string);
  int          length = java_lang_String::length(java_string);
  if (length != len) {
    return false;
  }
  for (int i = 0; i < len; i++) {
    if (value->char_at(i + offset) != chars[i]) {
      return false;
    }
  }
  return true;
}

void java_lang_String::print(Handle java_string, outputStream* st) {
  oop          obj    = java_string();
  assert(obj->klass() == SystemDictionary::String_klass(), "must be java_string");
  typeArrayOop value  = java_lang_String::value(obj);
  int          offset = java_lang_String::offset(obj);
  int          length = java_lang_String::length(obj);

  int end = MIN2(length, 100);
  if (value == NULL) {
    // This can happen if, e.g., printing a String
    // object before its initializer has been called
    st->print_cr("NULL");
  } else {
    st->print("\"");
    for (int index = 0; index < length; index++) {
      st->print("%c", value->char_at(index + offset));
    }
    st->print("\"");
  }
}


oop java_lang_Class::create_mirror(KlassHandle k, TRAPS) {
  assert(k->java_mirror() == NULL, "should only assign mirror once");
  // Use this moment of initialization to cache modifier_flags also,
  // to support Class.getModifiers().  Instance classes recalculate
  // the cached flags after the class file is parsed, but before the
  // class is put into the system dictionary.
  int computed_modifiers = k->compute_modifier_flags(CHECK_0);
  k->set_modifier_flags(computed_modifiers);
  if (SystemDictionary::Class_klass_loaded()) {
    // Allocate mirror (java.lang.Class instance)
    Handle mirror = instanceKlass::cast(SystemDictionary::Class_klass())->allocate_permanent_instance(CHECK_0);
    // Setup indirections
    mirror->obj_field_put(klass_offset,  k());
    k->set_java_mirror(mirror());
    // It might also have a component mirror.  This mirror must already exist.
    if (k->oop_is_javaArray()) {
      Handle comp_mirror;
      if (k->oop_is_typeArray()) {
        BasicType type = typeArrayKlass::cast(k->as_klassOop())->element_type();
        comp_mirror = Universe::java_mirror(type);
        assert(comp_mirror.not_null(), "must have primitive mirror");
      } else if (k->oop_is_objArray()) {
        klassOop element_klass = objArrayKlass::cast(k->as_klassOop())->element_klass();
        if (element_klass != NULL
            && (Klass::cast(element_klass)->oop_is_instance() ||
                Klass::cast(element_klass)->oop_is_javaArray())) {
          comp_mirror = Klass::cast(element_klass)->java_mirror();
          assert(comp_mirror.not_null(), "must have element mirror");
        }
        // else some object array internal to the VM, like systemObjArrayKlassObj
      }
      if (comp_mirror.not_null()) {
        // Two-way link between the array klass and its component mirror:
        arrayKlass::cast(k->as_klassOop())->set_component_mirror(comp_mirror());
        set_array_klass(comp_mirror(), k->as_klassOop());
      }
    }
    return mirror();
  } else {
    return NULL;
  }
}


oop java_lang_Class::create_basic_type_mirror(const char* basic_type_name, BasicType type, TRAPS) {
  // This should be improved by adding a field at the Java level or by
  // introducing a new VM klass (see comment in ClassFileParser)
  oop java_class = instanceKlass::cast(SystemDictionary::Class_klass())->allocate_permanent_instance(CHECK_0);
  if (type != T_VOID) {
    klassOop aklass = Universe::typeArrayKlassObj(type);
    assert(aklass != NULL, "correct bootstrap");
    set_array_klass(java_class, aklass);
  }
  return java_class;
}


klassOop java_lang_Class::as_klassOop(oop java_class) {
  //%note memory_2
  klassOop k = klassOop(java_class->obj_field(klass_offset));
  assert(k == NULL || k->is_klass(), "type check");
  return k;
}


void java_lang_Class::print_signature(oop java_class, outputStream* st) {
  assert(java_lang_Class::is_instance(java_class), "must be a Class object");
  symbolOop name = NULL;
  bool is_instance = false;
  if (is_primitive(java_class)) {
    name = vmSymbols::type_signature(primitive_type(java_class));
  } else {
    klassOop k = as_klassOop(java_class);
    is_instance = Klass::cast(k)->oop_is_instance();
    name = Klass::cast(k)->name();
  }
  if (name == NULL) {
    st->print("<null>");
    return;
  }
  if (is_instance)  st->print("L");
  st->write((char*) name->base(), (int) name->utf8_length());
  if (is_instance)  st->print(";");
}

symbolOop java_lang_Class::as_signature(oop java_class, bool intern_if_not_found, TRAPS) {
  assert(java_lang_Class::is_instance(java_class), "must be a Class object");
  symbolOop name = NULL;
  if (is_primitive(java_class)) {
    return vmSymbols::type_signature(primitive_type(java_class));
  } else {
    klassOop k = as_klassOop(java_class);
    if (!Klass::cast(k)->oop_is_instance()) {
      return Klass::cast(k)->name();
    } else {
      ResourceMark rm;
      const char* sigstr = Klass::cast(k)->signature_name();
      int         siglen = (int) strlen(sigstr);
      if (!intern_if_not_found)
        return SymbolTable::probe(sigstr, siglen);
      else
        return oopFactory::new_symbol(sigstr, siglen, THREAD);
    }
  }
}


klassOop java_lang_Class::array_klass(oop java_class) {
  klassOop k = klassOop(java_class->obj_field(array_klass_offset));
  assert(k == NULL || k->is_klass() && Klass::cast(k)->oop_is_javaArray(), "should be array klass");
  return k;
}


void java_lang_Class::set_array_klass(oop java_class, klassOop klass) {
  assert(klass->is_klass() && Klass::cast(klass)->oop_is_javaArray(), "should be array klass");
  java_class->obj_field_put(array_klass_offset, klass);
}


methodOop java_lang_Class::resolved_constructor(oop java_class) {
  oop constructor = java_class->obj_field(resolved_constructor_offset);
  assert(constructor == NULL || constructor->is_method(), "should be method");
  return methodOop(constructor);
}


void java_lang_Class::set_resolved_constructor(oop java_class, methodOop constructor) {
  assert(constructor->is_method(), "should be method");
  java_class->obj_field_put(resolved_constructor_offset, constructor);
}


bool java_lang_Class::is_primitive(oop java_class) {
  // should assert:
  //assert(java_lang_Class::is_instance(java_class), "must be a Class object");
  klassOop k = klassOop(java_class->obj_field(klass_offset));
  return k == NULL;
}


BasicType java_lang_Class::primitive_type(oop java_class) {
  assert(java_lang_Class::is_primitive(java_class), "just checking");
  klassOop ak = klassOop(java_class->obj_field(array_klass_offset));
  BasicType type = T_VOID;
  if (ak != NULL) {
    // Note: create_basic_type_mirror above initializes ak to a non-null value.
    type = arrayKlass::cast(ak)->element_type();
  } else {
    assert(java_class == Universe::void_mirror(), "only valid non-array primitive");
  }
  assert(Universe::java_mirror(type) == java_class, "must be consistent");
  return type;
}

BasicType java_lang_Class::as_BasicType(oop java_class, klassOop* reference_klass) {
  assert(java_lang_Class::is_instance(java_class), "must be a Class object");
  if (is_primitive(java_class)) {
    if (reference_klass != NULL)
      (*reference_klass) = NULL;
    return primitive_type(java_class);
  } else {
    if (reference_klass != NULL)
      (*reference_klass) = as_klassOop(java_class);
    return T_OBJECT;
  }
}


oop java_lang_Class::primitive_mirror(BasicType t) {
  oop mirror = Universe::java_mirror(t);
  assert(mirror != NULL && mirror->is_a(SystemDictionary::Class_klass()), "must be a Class");
  assert(java_lang_Class::is_primitive(mirror), "must be primitive");
  return mirror;
}

bool java_lang_Class::offsets_computed = false;
int  java_lang_Class::classRedefinedCount_offset = -1;
int  java_lang_Class::parallelCapable_offset = -1;

void java_lang_Class::compute_offsets() {
  assert(!offsets_computed, "offsets should be initialized only once");
  offsets_computed = true;

  klassOop k = SystemDictionary::Class_klass();
  // The classRedefinedCount field is only present starting in 1.5,
  // so don't go fatal.
  compute_optional_offset(classRedefinedCount_offset,
    k, vmSymbols::classRedefinedCount_name(), vmSymbols::int_signature());

  // The field indicating parallelCapable (parallelLockMap) is only present starting in 7,
  klassOop k1 = SystemDictionary::ClassLoader_klass();
  compute_optional_offset(parallelCapable_offset,
    k1, vmSymbols::parallelCapable_name(), vmSymbols::concurrenthashmap_signature());
}

// For class loader classes, parallelCapable defined
// based on non-null field
// Written to by java.lang.ClassLoader, vm only reads this field, doesn't set it
bool java_lang_Class::parallelCapable(oop class_loader) {
  if (!JDK_Version::is_gte_jdk17x_version()
     || parallelCapable_offset == -1) {
     // Default for backward compatibility is false
     return false;
  }
  return (class_loader->obj_field(parallelCapable_offset) != NULL);
}

int java_lang_Class::classRedefinedCount(oop the_class_mirror) {
  if (!JDK_Version::is_gte_jdk15x_version()
      || classRedefinedCount_offset == -1) {
    // The classRedefinedCount field is only present starting in 1.5.
    // If we don't have an offset for it then just return -1 as a marker.
    return -1;
  }

  return the_class_mirror->int_field(classRedefinedCount_offset);
}

void java_lang_Class::set_classRedefinedCount(oop the_class_mirror, int value) {
  if (!JDK_Version::is_gte_jdk15x_version()
      || classRedefinedCount_offset == -1) {
    // The classRedefinedCount field is only present starting in 1.5.
    // If we don't have an offset for it then nothing to set.
    return;
  }

  the_class_mirror->int_field_put(classRedefinedCount_offset, value);
}


// Note: JDK1.1 and before had a privateInfo_offset field which was used for the
//       platform thread structure, and a eetop offset which was used for thread
//       local storage (and unused by the HotSpot VM). In JDK1.2 the two structures
//       merged, so in the HotSpot VM we just use the eetop field for the thread
//       instead of the privateInfo_offset.
//
// Note: The stackSize field is only present starting in 1.4.

int java_lang_Thread::_name_offset = 0;
int java_lang_Thread::_group_offset = 0;
int java_lang_Thread::_contextClassLoader_offset = 0;
int java_lang_Thread::_inheritedAccessControlContext_offset = 0;
int java_lang_Thread::_priority_offset = 0;
int java_lang_Thread::_eetop_offset = 0;
int java_lang_Thread::_daemon_offset = 0;
int java_lang_Thread::_stillborn_offset = 0;
int java_lang_Thread::_stackSize_offset = 0;
int java_lang_Thread::_tid_offset = 0;
int java_lang_Thread::_thread_status_offset = 0;
int java_lang_Thread::_park_blocker_offset = 0;
int java_lang_Thread::_park_event_offset = 0 ;


void java_lang_Thread::compute_offsets() {
  assert(_group_offset == 0, "offsets should be initialized only once");

  klassOop k = SystemDictionary::Thread_klass();
  compute_offset(_name_offset,      k, vmSymbols::name_name(),      vmSymbols::char_array_signature());
  compute_offset(_group_offset,     k, vmSymbols::group_name(),     vmSymbols::threadgroup_signature());
  compute_offset(_contextClassLoader_offset, k, vmSymbols::contextClassLoader_name(), vmSymbols::classloader_signature());
  compute_offset(_inheritedAccessControlContext_offset, k, vmSymbols::inheritedAccessControlContext_name(), vmSymbols::accesscontrolcontext_signature());
  compute_offset(_priority_offset,  k, vmSymbols::priority_name(),  vmSymbols::int_signature());
  compute_offset(_daemon_offset,    k, vmSymbols::daemon_name(),    vmSymbols::bool_signature());
  compute_offset(_eetop_offset,     k, vmSymbols::eetop_name(),     vmSymbols::long_signature());
  compute_offset(_stillborn_offset, k, vmSymbols::stillborn_name(), vmSymbols::bool_signature());
  // The stackSize field is only present starting in 1.4, so don't go fatal.
  compute_optional_offset(_stackSize_offset, k, vmSymbols::stackSize_name(), vmSymbols::long_signature());
  // The tid and thread_status fields are only present starting in 1.5, so don't go fatal.
  compute_optional_offset(_tid_offset, k, vmSymbols::thread_id_name(), vmSymbols::long_signature());
  compute_optional_offset(_thread_status_offset, k, vmSymbols::thread_status_name(), vmSymbols::int_signature());
  // The parkBlocker field is only present starting in 1.6, so don't go fatal.
  compute_optional_offset(_park_blocker_offset, k, vmSymbols::park_blocker_name(), vmSymbols::object_signature());
  compute_optional_offset(_park_event_offset, k, vmSymbols::park_event_name(),
 vmSymbols::long_signature());
}


JavaThread* java_lang_Thread::thread(oop java_thread) {
  return (JavaThread*)java_thread->address_field(_eetop_offset);
}


void java_lang_Thread::set_thread(oop java_thread, JavaThread* thread) {
  java_thread->address_field_put(_eetop_offset, (address)thread);
}


typeArrayOop java_lang_Thread::name(oop java_thread) {
  oop name = java_thread->obj_field(_name_offset);
  assert(name == NULL || (name->is_typeArray() && typeArrayKlass::cast(name->klass())->element_type() == T_CHAR), "just checking");
  return typeArrayOop(name);
}


void java_lang_Thread::set_name(oop java_thread, typeArrayOop name) {
  assert(java_thread->obj_field(_name_offset) == NULL, "name should be NULL");
  java_thread->obj_field_put(_name_offset, name);
}


ThreadPriority java_lang_Thread::priority(oop java_thread) {
  return (ThreadPriority)java_thread->int_field(_priority_offset);
}


void java_lang_Thread::set_priority(oop java_thread, ThreadPriority priority) {
  java_thread->int_field_put(_priority_offset, priority);
}


oop java_lang_Thread::threadGroup(oop java_thread) {
  return java_thread->obj_field(_group_offset);
}


bool java_lang_Thread::is_stillborn(oop java_thread) {
  return java_thread->bool_field(_stillborn_offset) != 0;
}


// We never have reason to turn the stillborn bit off
void java_lang_Thread::set_stillborn(oop java_thread) {
  java_thread->bool_field_put(_stillborn_offset, true);
}


bool java_lang_Thread::is_alive(oop java_thread) {
  JavaThread* thr = java_lang_Thread::thread(java_thread);
  return (thr != NULL);
}


bool java_lang_Thread::is_daemon(oop java_thread) {
  return java_thread->bool_field(_daemon_offset) != 0;
}


void java_lang_Thread::set_daemon(oop java_thread) {
  java_thread->bool_field_put(_daemon_offset, true);
}

oop java_lang_Thread::context_class_loader(oop java_thread) {
  return java_thread->obj_field(_contextClassLoader_offset);
}

oop java_lang_Thread::inherited_access_control_context(oop java_thread) {
  return java_thread->obj_field(_inheritedAccessControlContext_offset);
}


jlong java_lang_Thread::stackSize(oop java_thread) {
  // The stackSize field is only present starting in 1.4
  if (_stackSize_offset > 0) {
    assert(JDK_Version::is_gte_jdk14x_version(), "sanity check");
    return java_thread->long_field(_stackSize_offset);
  } else {
    return 0;
  }
}

// Write the thread status value to threadStatus field in java.lang.Thread java class.
void java_lang_Thread::set_thread_status(oop java_thread,
                                         java_lang_Thread::ThreadStatus status) {
  assert(JavaThread::current()->thread_state() == _thread_in_vm, "Java Thread is not running in vm");
  // The threadStatus is only present starting in 1.5
  if (_thread_status_offset > 0) {
    java_thread->int_field_put(_thread_status_offset, status);
  }
}

// Read thread status value from threadStatus field in java.lang.Thread java class.
java_lang_Thread::ThreadStatus java_lang_Thread::get_thread_status(oop java_thread) {
  assert(Thread::current()->is_VM_thread() ||
         JavaThread::current()->thread_state() == _thread_in_vm,
         "Java Thread is not running in vm");
  // The threadStatus is only present starting in 1.5
  if (_thread_status_offset > 0) {
    return (java_lang_Thread::ThreadStatus)java_thread->int_field(_thread_status_offset);
  } else {
    // All we can easily figure out is if it is alive, but that is
    // enough info for a valid unknown status.
    // These aren't restricted to valid set ThreadStatus values, so
    // use JVMTI values and cast.
    JavaThread* thr = java_lang_Thread::thread(java_thread);
    if (thr == NULL) {
      // the thread hasn't run yet or is in the process of exiting
      return NEW;
    }
    return (java_lang_Thread::ThreadStatus)JVMTI_THREAD_STATE_ALIVE;
  }
}


jlong java_lang_Thread::thread_id(oop java_thread) {
  // The thread ID field is only present starting in 1.5
  if (_tid_offset > 0) {
    return java_thread->long_field(_tid_offset);
  } else {
    return 0;
  }
}

oop java_lang_Thread::park_blocker(oop java_thread) {
  assert(JDK_Version::current().supports_thread_park_blocker() &&
         _park_blocker_offset != 0, "Must support parkBlocker field");

  if (_park_blocker_offset > 0) {
    return java_thread->obj_field(_park_blocker_offset);
  }

  return NULL;
}

jlong java_lang_Thread::park_event(oop java_thread) {
  if (_park_event_offset > 0) {
    return java_thread->long_field(_park_event_offset);
  }
  return 0;
}

bool java_lang_Thread::set_park_event(oop java_thread, jlong ptr) {
  if (_park_event_offset > 0) {
    java_thread->long_field_put(_park_event_offset, ptr);
    return true;
  }
  return false;
}


const char* java_lang_Thread::thread_status_name(oop java_thread) {
  assert(JDK_Version::is_gte_jdk15x_version() && _thread_status_offset != 0, "Must have thread status");
  ThreadStatus status = (java_lang_Thread::ThreadStatus)java_thread->int_field(_thread_status_offset);
  switch (status) {
    case NEW                      : return "NEW";
    case RUNNABLE                 : return "RUNNABLE";
    case SLEEPING                 : return "TIMED_WAITING (sleeping)";
    case IN_OBJECT_WAIT           : return "WAITING (on object monitor)";
    case IN_OBJECT_WAIT_TIMED     : return "TIMED_WAITING (on object monitor)";
    case PARKED                   : return "WAITING (parking)";
    case PARKED_TIMED             : return "TIMED_WAITING (parking)";
    case BLOCKED_ON_MONITOR_ENTER : return "BLOCKED (on object monitor)";
    case TERMINATED               : return "TERMINATED";
    default                       : return "UNKNOWN";
  };
}
int java_lang_ThreadGroup::_parent_offset = 0;
int java_lang_ThreadGroup::_name_offset = 0;
int java_lang_ThreadGroup::_threads_offset = 0;
int java_lang_ThreadGroup::_groups_offset = 0;
int java_lang_ThreadGroup::_maxPriority_offset = 0;
int java_lang_ThreadGroup::_destroyed_offset = 0;
int java_lang_ThreadGroup::_daemon_offset = 0;
int java_lang_ThreadGroup::_vmAllowSuspension_offset = 0;
int java_lang_ThreadGroup::_nthreads_offset = 0;
int java_lang_ThreadGroup::_ngroups_offset = 0;

oop  java_lang_ThreadGroup::parent(oop java_thread_group) {
  assert(java_thread_group->is_oop(), "thread group must be oop");
  return java_thread_group->obj_field(_parent_offset);
}

// ("name as oop" accessor is not necessary)

typeArrayOop java_lang_ThreadGroup::name(oop java_thread_group) {
  oop name = java_thread_group->obj_field(_name_offset);
  // ThreadGroup.name can be null
  return name == NULL ? (typeArrayOop)NULL : java_lang_String::value(name);
}

int java_lang_ThreadGroup::nthreads(oop java_thread_group) {
  assert(java_thread_group->is_oop(), "thread group must be oop");
  return java_thread_group->int_field(_nthreads_offset);
}

objArrayOop java_lang_ThreadGroup::threads(oop java_thread_group) {
  oop threads = java_thread_group->obj_field(_threads_offset);
  assert(threads != NULL, "threadgroups should have threads");
  assert(threads->is_objArray(), "just checking"); // Todo: Add better type checking code
  return objArrayOop(threads);
}

int java_lang_ThreadGroup::ngroups(oop java_thread_group) {
  assert(java_thread_group->is_oop(), "thread group must be oop");
  return java_thread_group->int_field(_ngroups_offset);
}

objArrayOop java_lang_ThreadGroup::groups(oop java_thread_group) {
  oop groups = java_thread_group->obj_field(_groups_offset);
  assert(groups == NULL || groups->is_objArray(), "just checking"); // Todo: Add better type checking code
  return objArrayOop(groups);
}

ThreadPriority java_lang_ThreadGroup::maxPriority(oop java_thread_group) {
  assert(java_thread_group->is_oop(), "thread group must be oop");
  return (ThreadPriority) java_thread_group->int_field(_maxPriority_offset);
}

bool java_lang_ThreadGroup::is_destroyed(oop java_thread_group) {
  assert(java_thread_group->is_oop(), "thread group must be oop");
  return java_thread_group->bool_field(_destroyed_offset) != 0;
}

bool java_lang_ThreadGroup::is_daemon(oop java_thread_group) {
  assert(java_thread_group->is_oop(), "thread group must be oop");
  return java_thread_group->bool_field(_daemon_offset) != 0;
}

bool java_lang_ThreadGroup::is_vmAllowSuspension(oop java_thread_group) {
  assert(java_thread_group->is_oop(), "thread group must be oop");
  return java_thread_group->bool_field(_vmAllowSuspension_offset) != 0;
}

void java_lang_ThreadGroup::compute_offsets() {
  assert(_parent_offset == 0, "offsets should be initialized only once");

  klassOop k = SystemDictionary::ThreadGroup_klass();

  compute_offset(_parent_offset,      k, vmSymbols::parent_name(),      vmSymbols::threadgroup_signature());
  compute_offset(_name_offset,        k, vmSymbols::name_name(),        vmSymbols::string_signature());
  compute_offset(_threads_offset,     k, vmSymbols::threads_name(),     vmSymbols::thread_array_signature());
  compute_offset(_groups_offset,      k, vmSymbols::groups_name(),      vmSymbols::threadgroup_array_signature());
  compute_offset(_maxPriority_offset, k, vmSymbols::maxPriority_name(), vmSymbols::int_signature());
  compute_offset(_destroyed_offset,   k, vmSymbols::destroyed_name(),   vmSymbols::bool_signature());
  compute_offset(_daemon_offset,      k, vmSymbols::daemon_name(),      vmSymbols::bool_signature());
  compute_offset(_vmAllowSuspension_offset, k, vmSymbols::vmAllowSuspension_name(), vmSymbols::bool_signature());
  compute_offset(_nthreads_offset,    k, vmSymbols::nthreads_name(),    vmSymbols::int_signature());
  compute_offset(_ngroups_offset,     k, vmSymbols::ngroups_name(),     vmSymbols::int_signature());
}

oop java_lang_Throwable::backtrace(oop throwable) {
  return throwable->obj_field_acquire(backtrace_offset);
}


void java_lang_Throwable::set_backtrace(oop throwable, oop value) {
  throwable->release_obj_field_put(backtrace_offset, value);
}


oop java_lang_Throwable::message(oop throwable) {
  return throwable->obj_field(detailMessage_offset);
}


oop java_lang_Throwable::message(Handle throwable) {
  return throwable->obj_field(detailMessage_offset);
}


void java_lang_Throwable::set_message(oop throwable, oop value) {
  throwable->obj_field_put(detailMessage_offset, value);
}


void java_lang_Throwable::clear_stacktrace(oop throwable) {
  assert(JDK_Version::is_gte_jdk14x_version(), "should only be called in >= 1.4");
  throwable->obj_field_put(stackTrace_offset, NULL);
}


void java_lang_Throwable::print(oop throwable, outputStream* st) {
  ResourceMark rm;
  klassOop k = throwable->klass();
  assert(k != NULL, "just checking");
  st->print("%s", instanceKlass::cast(k)->external_name());
  oop msg = message(throwable);
  if (msg != NULL) {
    st->print(": %s", java_lang_String::as_utf8_string(msg));
  }
}


void java_lang_Throwable::print(Handle throwable, outputStream* st) {
  ResourceMark rm;
  klassOop k = throwable->klass();
  assert(k != NULL, "just checking");
  st->print("%s", instanceKlass::cast(k)->external_name());
  oop msg = message(throwable);
  if (msg != NULL) {
    st->print(": %s", java_lang_String::as_utf8_string(msg));
  }
}

// Print stack trace element to resource allocated buffer
char* java_lang_Throwable::print_stack_element_to_buffer(methodOop method, int bci) {
  // Get strings and string lengths
  instanceKlass* klass = instanceKlass::cast(method->method_holder());
  const char* klass_name  = klass->external_name();
  int buf_len = (int)strlen(klass_name);
  char* source_file_name;
  if (klass->source_file_name() == NULL) {
    source_file_name = NULL;
  } else {
    source_file_name = klass->source_file_name()->as_C_string();
    buf_len += (int)strlen(source_file_name);
  }
  char* method_name = method->name()->as_C_string();
  buf_len += (int)strlen(method_name);

  // Allocate temporary buffer with extra space for formatting and line number
  char* buf = NEW_RESOURCE_ARRAY(char, buf_len + 64);

  // Print stack trace line in buffer
  sprintf(buf, "\tat %s.%s", klass_name, method_name);
  if (method->is_native()) {
    strcat(buf, "(Native Method)");
  } else {
    int line_number = method->line_number_from_bci(bci);
    if (source_file_name != NULL && (line_number != -1)) {
      // Sourcename and linenumber
      sprintf(buf + (int)strlen(buf), "(%s:%d)", source_file_name, line_number);
    } else if (source_file_name != NULL) {
      // Just sourcename
      sprintf(buf + (int)strlen(buf), "(%s)", source_file_name);
    } else {
      // Neither soucename and linenumber
      sprintf(buf + (int)strlen(buf), "(Unknown Source)");
    }
    nmethod* nm = method->code();
    if (WizardMode && nm != NULL) {
      sprintf(buf + (int)strlen(buf), "(nmethod " PTR_FORMAT ")", (intptr_t)nm);
    }
  }

  return buf;
}


void java_lang_Throwable::print_stack_element(Handle stream, methodOop method, int bci) {
  ResourceMark rm;
  char* buf = print_stack_element_to_buffer(method, bci);
  print_to_stream(stream, buf);
}

void java_lang_Throwable::print_stack_element(outputStream *st, methodOop method, int bci) {
  ResourceMark rm;
  char* buf = print_stack_element_to_buffer(method, bci);
  st->print_cr("%s", buf);
}

void java_lang_Throwable::print_to_stream(Handle stream, const char* str) {
  if (stream.is_null()) {
    tty->print_cr("%s", str);
  } else {
    EXCEPTION_MARK;
    JavaValue result(T_VOID);
    Handle arg (THREAD, oopFactory::new_charArray(str, THREAD));
    if (!HAS_PENDING_EXCEPTION) {
      JavaCalls::call_virtual(&result,
                              stream,
                              KlassHandle(THREAD, stream->klass()),
                              vmSymbolHandles::println_name(),
                              vmSymbolHandles::char_array_void_signature(),
                              arg,
                              THREAD);
    }
    // Ignore any exceptions. we are in the middle of exception handling. Same as classic VM.
    if (HAS_PENDING_EXCEPTION) CLEAR_PENDING_EXCEPTION;
  }

}


const char* java_lang_Throwable::no_stack_trace_message() {
  return "\t<<no stack trace available>>";
}


// Currently used only for exceptions occurring during startup
void java_lang_Throwable::print_stack_trace(oop throwable, outputStream* st) {
  Thread *THREAD = Thread::current();
  Handle h_throwable(THREAD, throwable);
  while (h_throwable.not_null()) {
    objArrayHandle result (THREAD, objArrayOop(backtrace(h_throwable())));
    if (result.is_null()) {
      st->print_cr(no_stack_trace_message());
      return;
    }

    while (result.not_null()) {
      objArrayHandle methods (THREAD,
                              objArrayOop(result->obj_at(trace_methods_offset)));
      typeArrayHandle bcis (THREAD,
                            typeArrayOop(result->obj_at(trace_bcis_offset)));

      if (methods.is_null() || bcis.is_null()) {
        st->print_cr(no_stack_trace_message());
        return;
      }

      int length = methods()->length();
      for (int index = 0; index < length; index++) {
        methodOop method = methodOop(methods()->obj_at(index));
        if (method == NULL) goto handle_cause;
        int bci = bcis->ushort_at(index);
        print_stack_element(st, method, bci);
      }
      result = objArrayHandle(THREAD, objArrayOop(result->obj_at(trace_next_offset)));
    }
  handle_cause:
    {
      EXCEPTION_MARK;
      JavaValue result(T_OBJECT);
      JavaCalls::call_virtual(&result,
                              h_throwable,
                              KlassHandle(THREAD, h_throwable->klass()),
                              vmSymbolHandles::getCause_name(),
                              vmSymbolHandles::void_throwable_signature(),
                              THREAD);
      // Ignore any exceptions. we are in the middle of exception handling. Same as classic VM.
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        h_throwable = Handle();
      } else {
        h_throwable = Handle(THREAD, (oop) result.get_jobject());
        if (h_throwable.not_null()) {
          st->print("Caused by: ");
          print(h_throwable, st);
          st->cr();
        }
      }
    }
  }
}


void java_lang_Throwable::print_stack_trace(oop throwable, oop print_stream) {
  // Note: this is no longer used in Merlin, but we support it for compatibility.
  Thread *thread = Thread::current();
  Handle stream(thread, print_stream);
  objArrayHandle result (thread, objArrayOop(backtrace(throwable)));
  if (result.is_null()) {
    print_to_stream(stream, no_stack_trace_message());
    return;
  }

  while (result.not_null()) {
    objArrayHandle methods (thread,
                            objArrayOop(result->obj_at(trace_methods_offset)));
    typeArrayHandle bcis (thread,
                          typeArrayOop(result->obj_at(trace_bcis_offset)));

    if (methods.is_null() || bcis.is_null()) {
      print_to_stream(stream, no_stack_trace_message());
      return;
    }

    int length = methods()->length();
    for (int index = 0; index < length; index++) {
      methodOop method = methodOop(methods()->obj_at(index));
      if (method == NULL) return;
      int bci = bcis->ushort_at(index);
      print_stack_element(stream, method, bci);
    }
    result = objArrayHandle(thread, objArrayOop(result->obj_at(trace_next_offset)));
  }
}

// This class provides a simple wrapper over the internal structure of
// exception backtrace to insulate users of the backtrace from needing
// to know what it looks like.
class BacktraceBuilder: public StackObj {
 private:
  Handle          _backtrace;
  objArrayOop     _head;
  objArrayOop     _methods;
  typeArrayOop    _bcis;
  int             _index;
  bool            _dirty;
  No_Safepoint_Verifier _nsv;

 public:

  enum {
    trace_methods_offset = java_lang_Throwable::trace_methods_offset,
    trace_bcis_offset    = java_lang_Throwable::trace_bcis_offset,
    trace_next_offset    = java_lang_Throwable::trace_next_offset,
    trace_size           = java_lang_Throwable::trace_size,
    trace_chunk_size     = java_lang_Throwable::trace_chunk_size
  };

  // constructor for new backtrace
  BacktraceBuilder(TRAPS): _methods(NULL), _bcis(NULL), _head(NULL), _dirty(false) {
    expand(CHECK);
    _backtrace = _head;
    _index = 0;
  }

  void flush() {
    // The following appears to have been an optimization to save from
    // doing a barrier for each individual store into the _methods array,
    // but rather to do it for the entire array after the series of writes.
    // That optimization seems to have been lost when compressed oops was
    // implemented. However, the extra card-marks below was left in place,
    // but is now redundant because the individual stores into the
    // _methods array already execute the barrier code. CR 6918185 has
    // been filed so the original code may be restored by deferring the
    // barriers until after the entire sequence of stores, thus re-enabling
    // the intent of the original optimization. In the meantime the redundant
    // card mark below is now disabled.
    if (_dirty && _methods != NULL) {
#if 0
      BarrierSet* bs = Universe::heap()->barrier_set();
      assert(bs->has_write_ref_array_opt(), "Barrier set must have ref array opt");
      bs->write_ref_array((HeapWord*)_methods->base(), _methods->length());
#endif
      _dirty = false;
    }
  }

  void expand(TRAPS) {
    flush();

    objArrayHandle old_head(THREAD, _head);
    Pause_No_Safepoint_Verifier pnsv(&_nsv);

    objArrayOop head = oopFactory::new_objectArray(trace_size, CHECK);
    objArrayHandle new_head(THREAD, head);

    objArrayOop methods = oopFactory::new_objectArray(trace_chunk_size, CHECK);
    objArrayHandle new_methods(THREAD, methods);

    typeArrayOop bcis = oopFactory::new_shortArray(trace_chunk_size, CHECK);
    typeArrayHandle new_bcis(THREAD, bcis);

    if (!old_head.is_null()) {
      old_head->obj_at_put(trace_next_offset, new_head());
    }
    new_head->obj_at_put(trace_methods_offset, new_methods());
    new_head->obj_at_put(trace_bcis_offset, new_bcis());

    _head    = new_head();
    _methods = new_methods();
    _bcis    = new_bcis();
    _index = 0;
  }

  oop backtrace() {
    flush();
    return _backtrace();
  }

  inline void push(methodOop method, short bci, TRAPS) {
    if (_index >= trace_chunk_size) {
      methodHandle mhandle(THREAD, method);
      expand(CHECK);
      method = mhandle();
    }

    _methods->obj_at_put(_index, method);
    _bcis->ushort_at_put(_index, bci);
    _index++;
    _dirty = true;
  }

  methodOop current_method() {
    assert(_index >= 0 && _index < trace_chunk_size, "out of range");
    return methodOop(_methods->obj_at(_index));
  }

  jushort current_bci() {
    assert(_index >= 0 && _index < trace_chunk_size, "out of range");
    return _bcis->ushort_at(_index);
  }
};


void java_lang_Throwable::fill_in_stack_trace(Handle throwable, TRAPS) {
  if (!StackTraceInThrowable) return;
  ResourceMark rm(THREAD);

  // Start out by clearing the backtrace for this object, in case the VM
  // runs out of memory while allocating the stack trace
  set_backtrace(throwable(), NULL);
  if (JDK_Version::is_gte_jdk14x_version()) {
    // New since 1.4, clear lazily constructed Java level stacktrace if
    // refilling occurs
    clear_stacktrace(throwable());
  }

  int max_depth = MaxJavaStackTraceDepth;
  JavaThread* thread = (JavaThread*)THREAD;
  BacktraceBuilder bt(CHECK);

  // Instead of using vframe directly, this version of fill_in_stack_trace
  // basically handles everything by hand. This significantly improved the
  // speed of this method call up to 28.5% on Solaris sparc. 27.1% on Windows.
  // See bug 6333838 for  more details.
  // The "ASSERT" here is to verify this method generates the exactly same stack
  // trace as utilizing vframe.
#ifdef ASSERT
  vframeStream st(thread);
  methodHandle st_method(THREAD, st.method());
#endif
  int total_count = 0;
  RegisterMap map(thread, false);
  int decode_offset = 0;
  nmethod* nm = NULL;
  bool skip_fillInStackTrace_check = false;
  bool skip_throwableInit_check = false;

  for (frame fr = thread->last_frame(); max_depth != total_count;) {
    methodOop method = NULL;
    int bci = 0;

    // Compiled java method case.
    if (decode_offset != 0) {
      DebugInfoReadStream stream(nm, decode_offset);
      decode_offset = stream.read_int();
      method = (methodOop)nm->oop_at(stream.read_int());
      bci = stream.read_bci();
    } else {
      if (fr.is_first_frame()) break;
      address pc = fr.pc();
      if (fr.is_interpreted_frame()) {
        intptr_t bcx = fr.interpreter_frame_bcx();
        method = fr.interpreter_frame_method();
        bci =  fr.is_bci(bcx) ? bcx : method->bci_from((address)bcx);
        fr = fr.sender(&map);
      } else {
        CodeBlob* cb = fr.cb();
        // HMMM QQQ might be nice to have frame return nm as NULL if cb is non-NULL
        // but non nmethod
        fr = fr.sender(&map);
        if (cb == NULL || !cb->is_nmethod()) {
          continue;
        }
        nm = (nmethod*)cb;
        if (nm->method()->is_native()) {
          method = nm->method();
          bci = 0;
        } else {
          PcDesc* pd = nm->pc_desc_at(pc);
          decode_offset = pd->scope_decode_offset();
          // if decode_offset is not equal to 0, it will execute the
          // "compiled java method case" at the beginning of the loop.
          continue;
        }
      }
    }
#ifdef ASSERT
  assert(st_method() == method && st.bci() == bci,
         "Wrong stack trace");
  st.next();
  // vframeStream::method isn't GC-safe so store off a copy
  // of the methodOop in case we GC.
  if (!st.at_end()) {
    st_method = st.method();
  }
#endif
    if (!skip_fillInStackTrace_check) {
      // check "fillInStackTrace" only once, so we negate the flag
      // after the first time check.
      skip_fillInStackTrace_check = true;
      if (method->name() == vmSymbols::fillInStackTrace_name()) {
        continue;
      }
    }
    // skip <init> methods of the exceptions klass. If there is <init> methods
    // that belongs to a superclass of the exception  we are going to skipping
    // them in stack trace. This is simlar to classic VM.
    if (!skip_throwableInit_check) {
      if (method->name() == vmSymbols::object_initializer_name() &&
          throwable->is_a(method->method_holder())) {
        continue;
      } else {
        // if no "Throwable.init()" method found, we stop checking it next time.
        skip_throwableInit_check = true;
      }
    }
    bt.push(method, bci, CHECK);
    total_count++;
  }

  // Put completed stack trace into throwable object
  set_backtrace(throwable(), bt.backtrace());
}

void java_lang_Throwable::fill_in_stack_trace(Handle throwable) {
  // No-op if stack trace is disabled
  if (!StackTraceInThrowable) {
    return;
  }

  // Disable stack traces for some preallocated out of memory errors
  if (!Universe::should_fill_in_stack_trace(throwable)) {
    return;
  }

  PRESERVE_EXCEPTION_MARK;

  JavaThread* thread = JavaThread::active();
  fill_in_stack_trace(throwable, thread);
  // ignore exceptions thrown during stack trace filling
  CLEAR_PENDING_EXCEPTION;
}

void java_lang_Throwable::allocate_backtrace(Handle throwable, TRAPS) {
  // Allocate stack trace - backtrace is created but not filled in

  // No-op if stack trace is disabled
  if (!StackTraceInThrowable) return;

  objArrayOop h_oop = oopFactory::new_objectArray(trace_size, CHECK);
  objArrayHandle backtrace  (THREAD, h_oop);
  objArrayOop m_oop = oopFactory::new_objectArray(trace_chunk_size, CHECK);
  objArrayHandle methods (THREAD, m_oop);
  typeArrayOop b = oopFactory::new_shortArray(trace_chunk_size, CHECK);
  typeArrayHandle bcis(THREAD, b);

  // backtrace has space for one chunk (next is NULL)
  backtrace->obj_at_put(trace_methods_offset, methods());
  backtrace->obj_at_put(trace_bcis_offset, bcis());
  set_backtrace(throwable(), backtrace());
}


void java_lang_Throwable::fill_in_stack_trace_of_preallocated_backtrace(Handle throwable) {
  // Fill in stack trace into preallocated backtrace (no GC)

  // No-op if stack trace is disabled
  if (!StackTraceInThrowable) return;

  assert(throwable->is_a(SystemDictionary::Throwable_klass()), "sanity check");

  oop backtrace = java_lang_Throwable::backtrace(throwable());
  assert(backtrace != NULL, "backtrace not preallocated");

  oop m = objArrayOop(backtrace)->obj_at(trace_methods_offset);
  objArrayOop methods = objArrayOop(m);
  assert(methods != NULL && methods->length() > 0, "method array not preallocated");

  oop b = objArrayOop(backtrace)->obj_at(trace_bcis_offset);
  typeArrayOop bcis = typeArrayOop(b);
  assert(bcis != NULL, "bci array not preallocated");

  assert(methods->length() == bcis->length(), "method and bci arrays should match");

  JavaThread* thread = JavaThread::current();
  ResourceMark rm(thread);
  vframeStream st(thread);

  // Unlike fill_in_stack_trace we do not skip fillInStackTrace or throwable init
  // methods as preallocated errors aren't created by "java" code.

  // fill in as much stack trace as possible
  int max_chunks = MIN2(methods->length(), (int)MaxJavaStackTraceDepth);
  int chunk_count = 0;

  for (;!st.at_end(); st.next()) {
    // add element
    bcis->ushort_at_put(chunk_count, st.bci());
    methods->obj_at_put(chunk_count, st.method());

    chunk_count++;

    // Bail-out for deep stacks
    if (chunk_count >= max_chunks) break;
  }
}


int java_lang_Throwable::get_stack_trace_depth(oop throwable, TRAPS) {
  if (throwable == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  objArrayOop chunk = objArrayOop(backtrace(throwable));
  int depth = 0;
  if (chunk != NULL) {
    // Iterate over chunks and count full ones
    while (true) {
      objArrayOop next = objArrayOop(chunk->obj_at(trace_next_offset));
      if (next == NULL) break;
      depth += trace_chunk_size;
      chunk = next;
    }
    assert(chunk != NULL && chunk->obj_at(trace_next_offset) == NULL, "sanity check");
    // Count element in remaining partial chunk
    objArrayOop methods = objArrayOop(chunk->obj_at(trace_methods_offset));
    typeArrayOop bcis = typeArrayOop(chunk->obj_at(trace_bcis_offset));
    assert(methods != NULL && bcis != NULL, "sanity check");
    for (int i = 0; i < methods->length(); i++) {
      if (methods->obj_at(i) == NULL) break;
      depth++;
    }
  }
  return depth;
}


oop java_lang_Throwable::get_stack_trace_element(oop throwable, int index, TRAPS) {
  if (throwable == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  if (index < 0) {
    THROW_(vmSymbols::java_lang_IndexOutOfBoundsException(), NULL);
  }
  // Compute how many chunks to skip and index into actual chunk
  objArrayOop chunk = objArrayOop(backtrace(throwable));
  int skip_chunks = index / trace_chunk_size;
  int chunk_index = index % trace_chunk_size;
  while (chunk != NULL && skip_chunks > 0) {
    chunk = objArrayOop(chunk->obj_at(trace_next_offset));
        skip_chunks--;
  }
  if (chunk == NULL) {
    THROW_(vmSymbols::java_lang_IndexOutOfBoundsException(), NULL);
  }
  // Get method,bci from chunk
  objArrayOop methods = objArrayOop(chunk->obj_at(trace_methods_offset));
  typeArrayOop bcis = typeArrayOop(chunk->obj_at(trace_bcis_offset));
  assert(methods != NULL && bcis != NULL, "sanity check");
  methodHandle method(THREAD, methodOop(methods->obj_at(chunk_index)));
  int bci = bcis->ushort_at(chunk_index);
  // Chunk can be partial full
  if (method.is_null()) {
    THROW_(vmSymbols::java_lang_IndexOutOfBoundsException(), NULL);
  }

  oop element = java_lang_StackTraceElement::create(method, bci, CHECK_0);
  return element;
}

oop java_lang_StackTraceElement::create(methodHandle method, int bci, TRAPS) {
  // SystemDictionary::stackTraceElement_klass() will be null for pre-1.4 JDKs
  assert(JDK_Version::is_gte_jdk14x_version(), "should only be called in >= 1.4");

  // Allocate java.lang.StackTraceElement instance
  klassOop k = SystemDictionary::StackTraceElement_klass();
  assert(k != NULL, "must be loaded in 1.4+");
  instanceKlassHandle ik (THREAD, k);
  if (ik->should_be_initialized()) {
    ik->initialize(CHECK_0);
  }

  Handle element = ik->allocate_instance_handle(CHECK_0);
  // Fill in class name
  ResourceMark rm(THREAD);
  const char* str = instanceKlass::cast(method->method_holder())->external_name();
  oop classname = StringTable::intern((char*) str, CHECK_0);
  java_lang_StackTraceElement::set_declaringClass(element(), classname);
  // Fill in method name
  oop methodname = StringTable::intern(method->name(), CHECK_0);
  java_lang_StackTraceElement::set_methodName(element(), methodname);
  // Fill in source file name
  symbolOop source = instanceKlass::cast(method->method_holder())->source_file_name();
  oop filename = StringTable::intern(source, CHECK_0);
  java_lang_StackTraceElement::set_fileName(element(), filename);
  // File in source line number
  int line_number;
  if (method->is_native()) {
    // Negative value different from -1 below, enabling Java code in
    // class java.lang.StackTraceElement to distinguish "native" from
    // "no LineNumberTable".
    line_number = -2;
  } else {
    // Returns -1 if no LineNumberTable, and otherwise actual line number
    line_number = method->line_number_from_bci(bci);
  }
  java_lang_StackTraceElement::set_lineNumber(element(), line_number);

  return element();
}


void java_lang_reflect_AccessibleObject::compute_offsets() {
  klassOop k = SystemDictionary::reflect_AccessibleObject_klass();
  compute_offset(override_offset, k, vmSymbols::override_name(), vmSymbols::bool_signature());
}

jboolean java_lang_reflect_AccessibleObject::override(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return (jboolean) reflect->bool_field(override_offset);
}

void java_lang_reflect_AccessibleObject::set_override(oop reflect, jboolean value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  reflect->bool_field_put(override_offset, (int) value);
}

void java_lang_reflect_Method::compute_offsets() {
  klassOop k = SystemDictionary::reflect_Method_klass();
  compute_offset(clazz_offset,          k, vmSymbols::clazz_name(),          vmSymbols::class_signature());
  compute_offset(name_offset,           k, vmSymbols::name_name(),           vmSymbols::string_signature());
  compute_offset(returnType_offset,     k, vmSymbols::returnType_name(),     vmSymbols::class_signature());
  compute_offset(parameterTypes_offset, k, vmSymbols::parameterTypes_name(), vmSymbols::class_array_signature());
  compute_offset(exceptionTypes_offset, k, vmSymbols::exceptionTypes_name(), vmSymbols::class_array_signature());
  compute_offset(slot_offset,           k, vmSymbols::slot_name(),           vmSymbols::int_signature());
  compute_offset(modifiers_offset,      k, vmSymbols::modifiers_name(),      vmSymbols::int_signature());
  // The generic signature and annotations fields are only present in 1.5
  signature_offset = -1;
  annotations_offset = -1;
  parameter_annotations_offset = -1;
  annotation_default_offset = -1;
  compute_optional_offset(signature_offset,             k, vmSymbols::signature_name(),             vmSymbols::string_signature());
  compute_optional_offset(annotations_offset,           k, vmSymbols::annotations_name(),           vmSymbols::byte_array_signature());
  compute_optional_offset(parameter_annotations_offset, k, vmSymbols::parameter_annotations_name(), vmSymbols::byte_array_signature());
  compute_optional_offset(annotation_default_offset,    k, vmSymbols::annotation_default_name(),    vmSymbols::byte_array_signature());
}

Handle java_lang_reflect_Method::create(TRAPS) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  klassOop klass = SystemDictionary::reflect_Method_klass();
  // This class is eagerly initialized during VM initialization, since we keep a refence
  // to one of the methods
  assert(instanceKlass::cast(klass)->is_initialized(), "must be initialized");
  return instanceKlass::cast(klass)->allocate_instance_handle(CHECK_NH);
}

oop java_lang_reflect_Method::clazz(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return reflect->obj_field(clazz_offset);
}

void java_lang_reflect_Method::set_clazz(oop reflect, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
   reflect->obj_field_put(clazz_offset, value);
}

int java_lang_reflect_Method::slot(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return reflect->int_field(slot_offset);
}

void java_lang_reflect_Method::set_slot(oop reflect, int value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  reflect->int_field_put(slot_offset, value);
}

oop java_lang_reflect_Method::name(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return method->obj_field(name_offset);
}

void java_lang_reflect_Method::set_name(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  method->obj_field_put(name_offset, value);
}

oop java_lang_reflect_Method::return_type(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return method->obj_field(returnType_offset);
}

void java_lang_reflect_Method::set_return_type(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  method->obj_field_put(returnType_offset, value);
}

oop java_lang_reflect_Method::parameter_types(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return method->obj_field(parameterTypes_offset);
}

void java_lang_reflect_Method::set_parameter_types(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  method->obj_field_put(parameterTypes_offset, value);
}

oop java_lang_reflect_Method::exception_types(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return method->obj_field(exceptionTypes_offset);
}

void java_lang_reflect_Method::set_exception_types(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  method->obj_field_put(exceptionTypes_offset, value);
}

int java_lang_reflect_Method::modifiers(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return method->int_field(modifiers_offset);
}

void java_lang_reflect_Method::set_modifiers(oop method, int value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  method->int_field_put(modifiers_offset, value);
}

bool java_lang_reflect_Method::has_signature_field() {
  return (signature_offset >= 0);
}

oop java_lang_reflect_Method::signature(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_signature_field(), "signature field must be present");
  return method->obj_field(signature_offset);
}

void java_lang_reflect_Method::set_signature(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_signature_field(), "signature field must be present");
  method->obj_field_put(signature_offset, value);
}

bool java_lang_reflect_Method::has_annotations_field() {
  return (annotations_offset >= 0);
}

oop java_lang_reflect_Method::annotations(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotations_field(), "annotations field must be present");
  return method->obj_field(annotations_offset);
}

void java_lang_reflect_Method::set_annotations(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotations_field(), "annotations field must be present");
  method->obj_field_put(annotations_offset, value);
}

bool java_lang_reflect_Method::has_parameter_annotations_field() {
  return (parameter_annotations_offset >= 0);
}

oop java_lang_reflect_Method::parameter_annotations(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_parameter_annotations_field(), "parameter annotations field must be present");
  return method->obj_field(parameter_annotations_offset);
}

void java_lang_reflect_Method::set_parameter_annotations(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_parameter_annotations_field(), "parameter annotations field must be present");
  method->obj_field_put(parameter_annotations_offset, value);
}

bool java_lang_reflect_Method::has_annotation_default_field() {
  return (annotation_default_offset >= 0);
}

oop java_lang_reflect_Method::annotation_default(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotation_default_field(), "annotation default field must be present");
  return method->obj_field(annotation_default_offset);
}

void java_lang_reflect_Method::set_annotation_default(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotation_default_field(), "annotation default field must be present");
  method->obj_field_put(annotation_default_offset, value);
}

void java_lang_reflect_Constructor::compute_offsets() {
  klassOop k = SystemDictionary::reflect_Constructor_klass();
  compute_offset(clazz_offset,          k, vmSymbols::clazz_name(),          vmSymbols::class_signature());
  compute_offset(parameterTypes_offset, k, vmSymbols::parameterTypes_name(), vmSymbols::class_array_signature());
  compute_offset(exceptionTypes_offset, k, vmSymbols::exceptionTypes_name(), vmSymbols::class_array_signature());
  compute_offset(slot_offset,           k, vmSymbols::slot_name(),           vmSymbols::int_signature());
  compute_offset(modifiers_offset,      k, vmSymbols::modifiers_name(),      vmSymbols::int_signature());
  // The generic signature and annotations fields are only present in 1.5
  signature_offset = -1;
  annotations_offset = -1;
  parameter_annotations_offset = -1;
  compute_optional_offset(signature_offset,             k, vmSymbols::signature_name(),             vmSymbols::string_signature());
  compute_optional_offset(annotations_offset,           k, vmSymbols::annotations_name(),           vmSymbols::byte_array_signature());
  compute_optional_offset(parameter_annotations_offset, k, vmSymbols::parameter_annotations_name(), vmSymbols::byte_array_signature());
}

Handle java_lang_reflect_Constructor::create(TRAPS) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  symbolHandle name = vmSymbolHandles::java_lang_reflect_Constructor();
  klassOop k = SystemDictionary::resolve_or_fail(name, true, CHECK_NH);
  instanceKlassHandle klass (THREAD, k);
  // Ensure it is initialized
  klass->initialize(CHECK_NH);
  return klass->allocate_instance_handle(CHECK_NH);
}

oop java_lang_reflect_Constructor::clazz(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return reflect->obj_field(clazz_offset);
}

void java_lang_reflect_Constructor::set_clazz(oop reflect, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
   reflect->obj_field_put(clazz_offset, value);
}

oop java_lang_reflect_Constructor::parameter_types(oop constructor) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return constructor->obj_field(parameterTypes_offset);
}

void java_lang_reflect_Constructor::set_parameter_types(oop constructor, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  constructor->obj_field_put(parameterTypes_offset, value);
}

oop java_lang_reflect_Constructor::exception_types(oop constructor) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return constructor->obj_field(exceptionTypes_offset);
}

void java_lang_reflect_Constructor::set_exception_types(oop constructor, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  constructor->obj_field_put(exceptionTypes_offset, value);
}

int java_lang_reflect_Constructor::slot(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return reflect->int_field(slot_offset);
}

void java_lang_reflect_Constructor::set_slot(oop reflect, int value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  reflect->int_field_put(slot_offset, value);
}

int java_lang_reflect_Constructor::modifiers(oop constructor) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return constructor->int_field(modifiers_offset);
}

void java_lang_reflect_Constructor::set_modifiers(oop constructor, int value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  constructor->int_field_put(modifiers_offset, value);
}

bool java_lang_reflect_Constructor::has_signature_field() {
  return (signature_offset >= 0);
}

oop java_lang_reflect_Constructor::signature(oop constructor) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_signature_field(), "signature field must be present");
  return constructor->obj_field(signature_offset);
}

void java_lang_reflect_Constructor::set_signature(oop constructor, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_signature_field(), "signature field must be present");
  constructor->obj_field_put(signature_offset, value);
}

bool java_lang_reflect_Constructor::has_annotations_field() {
  return (annotations_offset >= 0);
}

oop java_lang_reflect_Constructor::annotations(oop constructor) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotations_field(), "annotations field must be present");
  return constructor->obj_field(annotations_offset);
}

void java_lang_reflect_Constructor::set_annotations(oop constructor, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotations_field(), "annotations field must be present");
  constructor->obj_field_put(annotations_offset, value);
}

bool java_lang_reflect_Constructor::has_parameter_annotations_field() {
  return (parameter_annotations_offset >= 0);
}

oop java_lang_reflect_Constructor::parameter_annotations(oop method) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_parameter_annotations_field(), "parameter annotations field must be present");
  return method->obj_field(parameter_annotations_offset);
}

void java_lang_reflect_Constructor::set_parameter_annotations(oop method, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_parameter_annotations_field(), "parameter annotations field must be present");
  method->obj_field_put(parameter_annotations_offset, value);
}

void java_lang_reflect_Field::compute_offsets() {
  klassOop k = SystemDictionary::reflect_Field_klass();
  compute_offset(clazz_offset,     k, vmSymbols::clazz_name(),     vmSymbols::class_signature());
  compute_offset(name_offset,      k, vmSymbols::name_name(),      vmSymbols::string_signature());
  compute_offset(type_offset,      k, vmSymbols::type_name(),      vmSymbols::class_signature());
  compute_offset(slot_offset,      k, vmSymbols::slot_name(),      vmSymbols::int_signature());
  compute_offset(modifiers_offset, k, vmSymbols::modifiers_name(), vmSymbols::int_signature());
  // The generic signature and annotations fields are only present in 1.5
  signature_offset = -1;
  annotations_offset = -1;
  compute_optional_offset(signature_offset, k, vmSymbols::signature_name(), vmSymbols::string_signature());
  compute_optional_offset(annotations_offset,  k, vmSymbols::annotations_name(),  vmSymbols::byte_array_signature());
}

Handle java_lang_reflect_Field::create(TRAPS) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  symbolHandle name = vmSymbolHandles::java_lang_reflect_Field();
  klassOop k = SystemDictionary::resolve_or_fail(name, true, CHECK_NH);
  instanceKlassHandle klass (THREAD, k);
  // Ensure it is initialized
  klass->initialize(CHECK_NH);
  return klass->allocate_instance_handle(CHECK_NH);
}

oop java_lang_reflect_Field::clazz(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return reflect->obj_field(clazz_offset);
}

void java_lang_reflect_Field::set_clazz(oop reflect, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
   reflect->obj_field_put(clazz_offset, value);
}

oop java_lang_reflect_Field::name(oop field) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return field->obj_field(name_offset);
}

void java_lang_reflect_Field::set_name(oop field, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  field->obj_field_put(name_offset, value);
}

oop java_lang_reflect_Field::type(oop field) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return field->obj_field(type_offset);
}

void java_lang_reflect_Field::set_type(oop field, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  field->obj_field_put(type_offset, value);
}

int java_lang_reflect_Field::slot(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return reflect->int_field(slot_offset);
}

void java_lang_reflect_Field::set_slot(oop reflect, int value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  reflect->int_field_put(slot_offset, value);
}

int java_lang_reflect_Field::modifiers(oop field) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return field->int_field(modifiers_offset);
}

void java_lang_reflect_Field::set_modifiers(oop field, int value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  field->int_field_put(modifiers_offset, value);
}

bool java_lang_reflect_Field::has_signature_field() {
  return (signature_offset >= 0);
}

oop java_lang_reflect_Field::signature(oop field) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_signature_field(), "signature field must be present");
  return field->obj_field(signature_offset);
}

void java_lang_reflect_Field::set_signature(oop field, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_signature_field(), "signature field must be present");
  field->obj_field_put(signature_offset, value);
}

bool java_lang_reflect_Field::has_annotations_field() {
  return (annotations_offset >= 0);
}

oop java_lang_reflect_Field::annotations(oop field) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotations_field(), "annotations field must be present");
  return field->obj_field(annotations_offset);
}

void java_lang_reflect_Field::set_annotations(oop field, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  assert(has_annotations_field(), "annotations field must be present");
  field->obj_field_put(annotations_offset, value);
}


void sun_reflect_ConstantPool::compute_offsets() {
  klassOop k = SystemDictionary::reflect_ConstantPool_klass();
  // This null test can be removed post beta
  if (k != NULL) {
    compute_offset(_cp_oop_offset, k, vmSymbols::constantPoolOop_name(), vmSymbols::object_signature());
  }
}


Handle sun_reflect_ConstantPool::create(TRAPS) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  klassOop k = SystemDictionary::reflect_ConstantPool_klass();
  instanceKlassHandle klass (THREAD, k);
  // Ensure it is initialized
  klass->initialize(CHECK_NH);
  return klass->allocate_instance_handle(CHECK_NH);
}


oop sun_reflect_ConstantPool::cp_oop(oop reflect) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  return reflect->obj_field(_cp_oop_offset);
}


void sun_reflect_ConstantPool::set_cp_oop(oop reflect, oop value) {
  assert(Universe::is_fully_initialized(), "Need to find another solution to the reflection problem");
  reflect->obj_field_put(_cp_oop_offset, value);
}

void sun_reflect_UnsafeStaticFieldAccessorImpl::compute_offsets() {
  klassOop k = SystemDictionary::reflect_UnsafeStaticFieldAccessorImpl_klass();
  // This null test can be removed post beta
  if (k != NULL) {
    compute_offset(_base_offset, k,
                   vmSymbols::base_name(), vmSymbols::object_signature());
  }
}

oop java_lang_boxing_object::initialize_and_allocate(BasicType type, TRAPS) {
  klassOop k = SystemDictionary::box_klass(type);
  if (k == NULL)  return NULL;
  instanceKlassHandle h (THREAD, k);
  if (!h->is_initialized())  h->initialize(CHECK_0);
  return h->allocate_instance(THREAD);
}


oop java_lang_boxing_object::create(BasicType type, jvalue* value, TRAPS) {
  oop box = initialize_and_allocate(type, CHECK_0);
  if (box == NULL)  return NULL;
  switch (type) {
    case T_BOOLEAN:
      box->bool_field_put(value_offset, value->z);
      break;
    case T_CHAR:
      box->char_field_put(value_offset, value->c);
      break;
    case T_FLOAT:
      box->float_field_put(value_offset, value->f);
      break;
    case T_DOUBLE:
      box->double_field_put(long_value_offset, value->d);
      break;
    case T_BYTE:
      box->byte_field_put(value_offset, value->b);
      break;
    case T_SHORT:
      box->short_field_put(value_offset, value->s);
      break;
    case T_INT:
      box->int_field_put(value_offset, value->i);
      break;
    case T_LONG:
      box->long_field_put(long_value_offset, value->j);
      break;
    default:
      return NULL;
  }
  return box;
}


BasicType java_lang_boxing_object::basic_type(oop box) {
  if (box == NULL)  return T_ILLEGAL;
  BasicType type = SystemDictionary::box_klass_type(box->klass());
  if (type == T_OBJECT)         // 'unknown' value returned by SD::bkt
    return T_ILLEGAL;
  return type;
}


BasicType java_lang_boxing_object::get_value(oop box, jvalue* value) {
  BasicType type = SystemDictionary::box_klass_type(box->klass());
  switch (type) {
  case T_BOOLEAN:
    value->z = box->bool_field(value_offset);
    break;
  case T_CHAR:
    value->c = box->char_field(value_offset);
    break;
  case T_FLOAT:
    value->f = box->float_field(value_offset);
    break;
  case T_DOUBLE:
    value->d = box->double_field(long_value_offset);
    break;
  case T_BYTE:
    value->b = box->byte_field(value_offset);
    break;
  case T_SHORT:
    value->s = box->short_field(value_offset);
    break;
  case T_INT:
    value->i = box->int_field(value_offset);
    break;
  case T_LONG:
    value->j = box->long_field(long_value_offset);
    break;
  default:
    return T_ILLEGAL;
  } // end switch
  return type;
}


BasicType java_lang_boxing_object::set_value(oop box, jvalue* value) {
  BasicType type = SystemDictionary::box_klass_type(box->klass());
  switch (type) {
  case T_BOOLEAN:
    box->bool_field_put(value_offset, value->z);
    break;
  case T_CHAR:
    box->char_field_put(value_offset, value->c);
    break;
  case T_FLOAT:
    box->float_field_put(value_offset, value->f);
    break;
  case T_DOUBLE:
    box->double_field_put(long_value_offset, value->d);
    break;
  case T_BYTE:
    box->byte_field_put(value_offset, value->b);
    break;
  case T_SHORT:
    box->short_field_put(value_offset, value->s);
    break;
  case T_INT:
    box->int_field_put(value_offset, value->i);
    break;
  case T_LONG:
    box->long_field_put(long_value_offset, value->j);
    break;
  default:
    return T_ILLEGAL;
  } // end switch
  return type;
}


void java_lang_boxing_object::print(BasicType type, jvalue* value, outputStream* st) {
  switch (type) {
  case T_BOOLEAN:   st->print("%s", value->z ? "true" : "false");   break;
  case T_CHAR:      st->print("%d", value->c);                      break;
  case T_BYTE:      st->print("%d", value->b);                      break;
  case T_SHORT:     st->print("%d", value->s);                      break;
  case T_INT:       st->print("%d", value->i);                      break;
  case T_LONG:      st->print(INT64_FORMAT, value->j);              break;
  case T_FLOAT:     st->print("%f", value->f);                      break;
  case T_DOUBLE:    st->print("%lf", value->d);                     break;
  default:          st->print("type %d?", type);                    break;
  }
}


// Support for java_lang_ref_Reference
oop java_lang_ref_Reference::pending_list_lock() {
  instanceKlass* ik = instanceKlass::cast(SystemDictionary::Reference_klass());
  char *addr = (((char *)ik->start_of_static_fields()) + static_lock_offset);
  if (UseCompressedOops) {
    return oopDesc::load_decode_heap_oop((narrowOop *)addr);
  } else {
    return oopDesc::load_decode_heap_oop((oop*)addr);
  }
}

HeapWord *java_lang_ref_Reference::pending_list_addr() {
  instanceKlass* ik = instanceKlass::cast(SystemDictionary::Reference_klass());
  char *addr = (((char *)ik->start_of_static_fields()) + static_pending_offset);
  // XXX This might not be HeapWord aligned, almost rather be char *.
  return (HeapWord*)addr;
}

oop java_lang_ref_Reference::pending_list() {
  char *addr = (char *)pending_list_addr();
  if (UseCompressedOops) {
    return oopDesc::load_decode_heap_oop((narrowOop *)addr);
  } else {
    return oopDesc::load_decode_heap_oop((oop*)addr);
  }
}


// Support for java_lang_ref_SoftReference

jlong java_lang_ref_SoftReference::timestamp(oop ref) {
  return ref->long_field(timestamp_offset);
}

jlong java_lang_ref_SoftReference::clock() {
  instanceKlass* ik = instanceKlass::cast(SystemDictionary::SoftReference_klass());
  int offset = ik->offset_of_static_fields() + static_clock_offset;

  return SystemDictionary::SoftReference_klass()->long_field(offset);
}

void java_lang_ref_SoftReference::set_clock(jlong value) {
  instanceKlass* ik = instanceKlass::cast(SystemDictionary::SoftReference_klass());
  int offset = ik->offset_of_static_fields() + static_clock_offset;

  SystemDictionary::SoftReference_klass()->long_field_put(offset, value);
}


// Support for java_dyn_MethodHandle

int java_dyn_MethodHandle::_type_offset;
int java_dyn_MethodHandle::_vmtarget_offset;
int java_dyn_MethodHandle::_vmentry_offset;
int java_dyn_MethodHandle::_vmslots_offset;

int sun_dyn_MemberName::_clazz_offset;
int sun_dyn_MemberName::_name_offset;
int sun_dyn_MemberName::_type_offset;
int sun_dyn_MemberName::_flags_offset;
int sun_dyn_MemberName::_vmtarget_offset;
int sun_dyn_MemberName::_vmindex_offset;

int sun_dyn_DirectMethodHandle::_vmindex_offset;

int sun_dyn_BoundMethodHandle::_argument_offset;
int sun_dyn_BoundMethodHandle::_vmargslot_offset;

int sun_dyn_AdapterMethodHandle::_conversion_offset;

void java_dyn_MethodHandle::compute_offsets() {
  klassOop k = SystemDictionary::MethodHandle_klass();
  if (k != NULL && EnableMethodHandles) {
    compute_offset(_type_offset,      k, vmSymbols::type_name(),      vmSymbols::java_dyn_MethodType_signature(), true);
    compute_offset(_vmtarget_offset,  k, vmSymbols::vmtarget_name(),  vmSymbols::object_signature(), true);
    compute_offset(_vmentry_offset,   k, vmSymbols::vmentry_name(),   vmSymbols::machine_word_signature(), true);

    // Note:  MH.vmslots (if it is present) is a hoisted copy of MH.type.form.vmslots.
    // It is optional pending experiments to keep or toss.
    compute_optional_offset(_vmslots_offset, k, vmSymbols::vmslots_name(), vmSymbols::int_signature(), true);
  }
}

void sun_dyn_MemberName::compute_offsets() {
  klassOop k = SystemDictionary::MemberName_klass();
  if (k != NULL && EnableMethodHandles) {
    compute_offset(_clazz_offset,     k, vmSymbols::clazz_name(),     vmSymbols::class_signature());
    compute_offset(_name_offset,      k, vmSymbols::name_name(),      vmSymbols::string_signature());
    compute_offset(_type_offset,      k, vmSymbols::type_name(),      vmSymbols::object_signature());
    compute_offset(_flags_offset,     k, vmSymbols::flags_name(),     vmSymbols::int_signature());
    compute_offset(_vmtarget_offset,  k, vmSymbols::vmtarget_name(),  vmSymbols::object_signature());
    compute_offset(_vmindex_offset,   k, vmSymbols::vmindex_name(),   vmSymbols::int_signature());
  }
}

void sun_dyn_DirectMethodHandle::compute_offsets() {
  klassOop k = SystemDictionary::DirectMethodHandle_klass();
  if (k != NULL && EnableMethodHandles) {
    compute_offset(_vmindex_offset,   k, vmSymbols::vmindex_name(),   vmSymbols::int_signature(),    true);
  }
}

void sun_dyn_BoundMethodHandle::compute_offsets() {
  klassOop k = SystemDictionary::BoundMethodHandle_klass();
  if (k != NULL && EnableMethodHandles) {
    compute_offset(_vmargslot_offset, k, vmSymbols::vmargslot_name(), vmSymbols::int_signature(),    true);
    compute_offset(_argument_offset,  k, vmSymbols::argument_name(),  vmSymbols::object_signature(), true);
  }
}

void sun_dyn_AdapterMethodHandle::compute_offsets() {
  klassOop k = SystemDictionary::AdapterMethodHandle_klass();
  if (k != NULL && EnableMethodHandles) {
    compute_offset(_conversion_offset, k, vmSymbols::conversion_name(), vmSymbols::int_signature(), true);
  }
}

oop java_dyn_MethodHandle::type(oop mh) {
  return mh->obj_field(_type_offset);
}

void java_dyn_MethodHandle::set_type(oop mh, oop mtype) {
  mh->obj_field_put(_type_offset, mtype);
}

int java_dyn_MethodHandle::vmslots(oop mh) {
  int vmslots_offset = _vmslots_offset;
  if (vmslots_offset != 0) {
#ifdef ASSERT
    int x = mh->int_field(vmslots_offset);
    int y = compute_vmslots(mh);
    assert(x == y, "correct hoisted value");
#endif
    return mh->int_field(vmslots_offset);
  } else {
    return compute_vmslots(mh);
  }
}

// if MH.vmslots exists, hoist into it the value of type.form.vmslots
void java_dyn_MethodHandle::init_vmslots(oop mh) {
  int vmslots_offset = _vmslots_offset;
  if (vmslots_offset != 0) {
    mh->int_field_put(vmslots_offset, compute_vmslots(mh));
  }
}

// fetch type.form.vmslots, which is the number of JVM stack slots
// required to carry the arguments of this MH
int java_dyn_MethodHandle::compute_vmslots(oop mh) {
  oop mtype = type(mh);
  if (mtype == NULL)  return 0;  // Java code would get NPE
  oop form = java_dyn_MethodType::form(mtype);
  if (form == NULL)   return 0;  // Java code would get NPE
  return java_dyn_MethodTypeForm::vmslots(form);
}

// fetch the low-level entry point for this mh
MethodHandleEntry* java_dyn_MethodHandle::vmentry(oop mh) {
  return (MethodHandleEntry*) mh->address_field(_vmentry_offset);
}

void java_dyn_MethodHandle::set_vmentry(oop mh, MethodHandleEntry* me) {
  assert(_vmentry_offset != 0, "must be present");

  // This is always the final step that initializes a valid method handle:
  mh->release_address_field_put(_vmentry_offset, (address) me);

  // There should be enough memory barriers on exit from native methods
  // to ensure that the MH is fully initialized to all threads before
  // Java code can publish it in global data structures.
  // But just in case, we use release_address_field_put.
}

/// MemberName accessors

oop sun_dyn_MemberName::clazz(oop mname) {
  assert(is_instance(mname), "wrong type");
  return mname->obj_field(_clazz_offset);
}

void sun_dyn_MemberName::set_clazz(oop mname, oop clazz) {
  assert(is_instance(mname), "wrong type");
  mname->obj_field_put(_clazz_offset, clazz);
}

oop sun_dyn_MemberName::name(oop mname) {
  assert(is_instance(mname), "wrong type");
  return mname->obj_field(_name_offset);
}

void sun_dyn_MemberName::set_name(oop mname, oop name) {
  assert(is_instance(mname), "wrong type");
  mname->obj_field_put(_name_offset, name);
}

oop sun_dyn_MemberName::type(oop mname) {
  assert(is_instance(mname), "wrong type");
  return mname->obj_field(_type_offset);
}

void sun_dyn_MemberName::set_type(oop mname, oop type) {
  assert(is_instance(mname), "wrong type");
  mname->obj_field_put(_type_offset, type);
}

int sun_dyn_MemberName::flags(oop mname) {
  assert(is_instance(mname), "wrong type");
  return mname->int_field(_flags_offset);
}

void sun_dyn_MemberName::set_flags(oop mname, int flags) {
  assert(is_instance(mname), "wrong type");
  mname->int_field_put(_flags_offset, flags);
}

oop sun_dyn_MemberName::vmtarget(oop mname) {
  assert(is_instance(mname), "wrong type");
  return mname->obj_field(_vmtarget_offset);
}

void sun_dyn_MemberName::set_vmtarget(oop mname, oop ref) {
  assert(is_instance(mname), "wrong type");
  mname->obj_field_put(_vmtarget_offset, ref);
}

int sun_dyn_MemberName::vmindex(oop mname) {
  assert(is_instance(mname), "wrong type");
  return mname->int_field(_vmindex_offset);
}

void sun_dyn_MemberName::set_vmindex(oop mname, int index) {
  assert(is_instance(mname), "wrong type");
  mname->int_field_put(_vmindex_offset, index);
}

oop java_dyn_MethodHandle::vmtarget(oop mh) {
  assert(is_instance(mh), "MH only");
  return mh->obj_field(_vmtarget_offset);
}

void java_dyn_MethodHandle::set_vmtarget(oop mh, oop ref) {
  assert(is_instance(mh), "MH only");
  mh->obj_field_put(_vmtarget_offset, ref);
}

int sun_dyn_DirectMethodHandle::vmindex(oop mh) {
  assert(is_instance(mh), "DMH only");
  return mh->int_field(_vmindex_offset);
}

void sun_dyn_DirectMethodHandle::set_vmindex(oop mh, int index) {
  assert(is_instance(mh), "DMH only");
  mh->int_field_put(_vmindex_offset, index);
}

int sun_dyn_BoundMethodHandle::vmargslot(oop mh) {
  assert(is_instance(mh), "BMH only");
  return mh->int_field(_vmargslot_offset);
}

oop sun_dyn_BoundMethodHandle::argument(oop mh) {
  assert(is_instance(mh), "BMH only");
  return mh->obj_field(_argument_offset);
}

int sun_dyn_AdapterMethodHandle::conversion(oop mh) {
  assert(is_instance(mh), "AMH only");
  return mh->int_field(_conversion_offset);
}

void sun_dyn_AdapterMethodHandle::set_conversion(oop mh, int conv) {
  assert(is_instance(mh), "AMH only");
  mh->int_field_put(_conversion_offset, conv);
}


// Support for java_dyn_MethodType

int java_dyn_MethodType::_rtype_offset;
int java_dyn_MethodType::_ptypes_offset;
int java_dyn_MethodType::_form_offset;

void java_dyn_MethodType::compute_offsets() {
  klassOop k = SystemDictionary::MethodType_klass();
  if (k != NULL) {
    compute_offset(_rtype_offset,  k, vmSymbols::rtype_name(),  vmSymbols::class_signature());
    compute_offset(_ptypes_offset, k, vmSymbols::ptypes_name(), vmSymbols::class_array_signature());
    compute_offset(_form_offset,   k, vmSymbols::form_name(),   vmSymbols::java_dyn_MethodTypeForm_signature());
  }
}

void java_dyn_MethodType::print_signature(oop mt, outputStream* st) {
  st->print("(");
  objArrayOop pts = ptypes(mt);
  for (int i = 0, limit = pts->length(); i < limit; i++) {
    java_lang_Class::print_signature(pts->obj_at(i), st);
  }
  st->print(")");
  java_lang_Class::print_signature(rtype(mt), st);
}

symbolOop java_dyn_MethodType::as_signature(oop mt, bool intern_if_not_found, TRAPS) {
  ResourceMark rm;
  stringStream buffer(128);
  print_signature(mt, &buffer);
  const char* sigstr =       buffer.base();
  int         siglen = (int) buffer.size();
  if (!intern_if_not_found)
    return SymbolTable::probe(sigstr, siglen);
  else
    return oopFactory::new_symbol(sigstr, siglen, THREAD);
}

oop java_dyn_MethodType::rtype(oop mt) {
  assert(is_instance(mt), "must be a MethodType");
  return mt->obj_field(_rtype_offset);
}

objArrayOop java_dyn_MethodType::ptypes(oop mt) {
  assert(is_instance(mt), "must be a MethodType");
  return (objArrayOop) mt->obj_field(_ptypes_offset);
}

oop java_dyn_MethodType::form(oop mt) {
  assert(is_instance(mt), "must be a MethodType");
  return mt->obj_field(_form_offset);
}

oop java_dyn_MethodType::ptype(oop mt, int idx) {
  return ptypes(mt)->obj_at(idx);
}

int java_dyn_MethodType::ptype_count(oop mt) {
  return ptypes(mt)->length();
}



// Support for java_dyn_MethodTypeForm

int java_dyn_MethodTypeForm::_vmslots_offset;
int java_dyn_MethodTypeForm::_erasedType_offset;
int java_dyn_MethodTypeForm::_genericInvoker_offset;

void java_dyn_MethodTypeForm::compute_offsets() {
  klassOop k = SystemDictionary::MethodTypeForm_klass();
  if (k != NULL) {
    compute_optional_offset(_vmslots_offset,    k, vmSymbols::vmslots_name(),    vmSymbols::int_signature(), true);
    compute_optional_offset(_erasedType_offset, k, vmSymbols::erasedType_name(), vmSymbols::java_dyn_MethodType_signature(), true);
    compute_optional_offset(_genericInvoker_offset, k, vmSymbols::genericInvoker_name(), vmSymbols::java_dyn_MethodHandle_signature(), true);
    if (_genericInvoker_offset == 0)  _genericInvoker_offset = -1;  // set to explicit "empty" value
  }
}

int java_dyn_MethodTypeForm::vmslots(oop mtform) {
  assert(mtform->klass() == SystemDictionary::MethodTypeForm_klass(), "MTForm only");
  return mtform->int_field(_vmslots_offset);
}

oop java_dyn_MethodTypeForm::erasedType(oop mtform) {
  assert(mtform->klass() == SystemDictionary::MethodTypeForm_klass(), "MTForm only");
  return mtform->obj_field(_erasedType_offset);
}

oop java_dyn_MethodTypeForm::genericInvoker(oop mtform) {
  assert(mtform->klass() == SystemDictionary::MethodTypeForm_klass(), "MTForm only");
  return mtform->obj_field(_genericInvoker_offset);
}


// Support for java_dyn_CallSite

int java_dyn_CallSite::_target_offset;
int java_dyn_CallSite::_caller_method_offset;
int java_dyn_CallSite::_caller_bci_offset;

void java_dyn_CallSite::compute_offsets() {
  if (!EnableInvokeDynamic)  return;
  klassOop k = SystemDictionary::CallSite_klass();
  if (k != NULL) {
    compute_offset(_target_offset, k, vmSymbols::target_name(), vmSymbols::java_dyn_MethodHandle_signature());
    compute_offset(_caller_method_offset, k, vmSymbols::vmmethod_name(), vmSymbols::sun_dyn_MemberName_signature());
    compute_offset(_caller_bci_offset, k, vmSymbols::vmindex_name(), vmSymbols::int_signature());
  }
}

oop java_dyn_CallSite::target(oop site) {
  return site->obj_field(_target_offset);
}

void java_dyn_CallSite::set_target(oop site, oop target) {
  site->obj_field_put(_target_offset, target);
}

oop java_dyn_CallSite::caller_method(oop site) {
  return site->obj_field(_caller_method_offset);
}

void java_dyn_CallSite::set_caller_method(oop site, oop ref) {
  site->obj_field_put(_caller_method_offset, ref);
}

jint java_dyn_CallSite::caller_bci(oop site) {
  return site->int_field(_caller_bci_offset);
}

void java_dyn_CallSite::set_caller_bci(oop site, jint bci) {
  site->int_field_put(_caller_bci_offset, bci);
}


// Support for java_security_AccessControlContext

int java_security_AccessControlContext::_context_offset = 0;
int java_security_AccessControlContext::_privilegedContext_offset = 0;
int java_security_AccessControlContext::_isPrivileged_offset = 0;

void java_security_AccessControlContext::compute_offsets() {
  assert(_isPrivileged_offset == 0, "offsets should be initialized only once");
  fieldDescriptor fd;
  instanceKlass* ik = instanceKlass::cast(SystemDictionary::AccessControlContext_klass());

  if (!ik->find_local_field(vmSymbols::context_name(), vmSymbols::protectiondomain_signature(), &fd)) {
    fatal("Invalid layout of java.security.AccessControlContext");
  }
  _context_offset = fd.offset();

  if (!ik->find_local_field(vmSymbols::privilegedContext_name(), vmSymbols::accesscontrolcontext_signature(), &fd)) {
    fatal("Invalid layout of java.security.AccessControlContext");
  }
  _privilegedContext_offset = fd.offset();

  if (!ik->find_local_field(vmSymbols::isPrivileged_name(), vmSymbols::bool_signature(), &fd)) {
    fatal("Invalid layout of java.security.AccessControlContext");
  }
  _isPrivileged_offset = fd.offset();
}


oop java_security_AccessControlContext::create(objArrayHandle context, bool isPrivileged, Handle privileged_context, TRAPS) {
  assert(_isPrivileged_offset != 0, "offsets should have been initialized");
  // Ensure klass is initialized
  instanceKlass::cast(SystemDictionary::AccessControlContext_klass())->initialize(CHECK_0);
  // Allocate result
  oop result = instanceKlass::cast(SystemDictionary::AccessControlContext_klass())->allocate_instance(CHECK_0);
  // Fill in values
  result->obj_field_put(_context_offset, context());
  result->obj_field_put(_privilegedContext_offset, privileged_context());
  result->bool_field_put(_isPrivileged_offset, isPrivileged);
  return result;
}


// Support for java_lang_ClassLoader

oop java_lang_ClassLoader::parent(oop loader) {
  assert(loader->is_oop(), "loader must be oop");
  return loader->obj_field(parent_offset);
}


bool java_lang_ClassLoader::is_trusted_loader(oop loader) {
  // Fix for 4474172; see evaluation for more details
  loader = non_reflection_class_loader(loader);

  oop cl = SystemDictionary::java_system_loader();
  while(cl != NULL) {
    if (cl == loader) return true;
    cl = parent(cl);
  }
  return false;
}

oop java_lang_ClassLoader::non_reflection_class_loader(oop loader) {
  if (loader != NULL) {
    // See whether this is one of the class loaders associated with
    // the generated bytecodes for reflection, and if so, "magically"
    // delegate to its parent to prevent class loading from occurring
    // in places where applications using reflection didn't expect it.
    klassOop delegating_cl_class = SystemDictionary::reflect_DelegatingClassLoader_klass();
    // This might be null in non-1.4 JDKs
    if (delegating_cl_class != NULL && loader->is_a(delegating_cl_class)) {
      return parent(loader);
    }
  }
  return loader;
}


// Support for java_lang_System

void java_lang_System::compute_offsets() {
  assert(offset_of_static_fields == 0, "offsets should be initialized only once");

  instanceKlass* ik = instanceKlass::cast(SystemDictionary::System_klass());
  offset_of_static_fields = ik->offset_of_static_fields();
}

int java_lang_System::in_offset_in_bytes() {
  return (offset_of_static_fields + static_in_offset);
}


int java_lang_System::out_offset_in_bytes() {
  return (offset_of_static_fields + static_out_offset);
}


int java_lang_System::err_offset_in_bytes() {
  return (offset_of_static_fields + static_err_offset);
}



int java_lang_String::value_offset;
int java_lang_String::offset_offset;
int java_lang_String::count_offset;
int java_lang_String::hash_offset;
int java_lang_Class::klass_offset;
int java_lang_Class::array_klass_offset;
int java_lang_Class::resolved_constructor_offset;
int java_lang_Class::number_of_fake_oop_fields;
int java_lang_Throwable::backtrace_offset;
int java_lang_Throwable::detailMessage_offset;
int java_lang_Throwable::cause_offset;
int java_lang_Throwable::stackTrace_offset;
int java_lang_reflect_AccessibleObject::override_offset;
int java_lang_reflect_Method::clazz_offset;
int java_lang_reflect_Method::name_offset;
int java_lang_reflect_Method::returnType_offset;
int java_lang_reflect_Method::parameterTypes_offset;
int java_lang_reflect_Method::exceptionTypes_offset;
int java_lang_reflect_Method::slot_offset;
int java_lang_reflect_Method::modifiers_offset;
int java_lang_reflect_Method::signature_offset;
int java_lang_reflect_Method::annotations_offset;
int java_lang_reflect_Method::parameter_annotations_offset;
int java_lang_reflect_Method::annotation_default_offset;
int java_lang_reflect_Constructor::clazz_offset;
int java_lang_reflect_Constructor::parameterTypes_offset;
int java_lang_reflect_Constructor::exceptionTypes_offset;
int java_lang_reflect_Constructor::slot_offset;
int java_lang_reflect_Constructor::modifiers_offset;
int java_lang_reflect_Constructor::signature_offset;
int java_lang_reflect_Constructor::annotations_offset;
int java_lang_reflect_Constructor::parameter_annotations_offset;
int java_lang_reflect_Field::clazz_offset;
int java_lang_reflect_Field::name_offset;
int java_lang_reflect_Field::type_offset;
int java_lang_reflect_Field::slot_offset;
int java_lang_reflect_Field::modifiers_offset;
int java_lang_reflect_Field::signature_offset;
int java_lang_reflect_Field::annotations_offset;
int java_lang_boxing_object::value_offset;
int java_lang_boxing_object::long_value_offset;
int java_lang_ref_Reference::referent_offset;
int java_lang_ref_Reference::queue_offset;
int java_lang_ref_Reference::next_offset;
int java_lang_ref_Reference::discovered_offset;
int java_lang_ref_Reference::static_lock_offset;
int java_lang_ref_Reference::static_pending_offset;
int java_lang_ref_Reference::number_of_fake_oop_fields;
int java_lang_ref_SoftReference::timestamp_offset;
int java_lang_ref_SoftReference::static_clock_offset;
int java_lang_ClassLoader::parent_offset;
int java_lang_System::offset_of_static_fields;
int java_lang_System::static_in_offset;
int java_lang_System::static_out_offset;
int java_lang_System::static_err_offset;
int java_lang_StackTraceElement::declaringClass_offset;
int java_lang_StackTraceElement::methodName_offset;
int java_lang_StackTraceElement::fileName_offset;
int java_lang_StackTraceElement::lineNumber_offset;
int java_lang_AssertionStatusDirectives::classes_offset;
int java_lang_AssertionStatusDirectives::classEnabled_offset;
int java_lang_AssertionStatusDirectives::packages_offset;
int java_lang_AssertionStatusDirectives::packageEnabled_offset;
int java_lang_AssertionStatusDirectives::deflt_offset;
int java_nio_Buffer::_limit_offset;
int sun_misc_AtomicLongCSImpl::_value_offset;
int java_util_concurrent_locks_AbstractOwnableSynchronizer::_owner_offset = 0;
int sun_reflect_ConstantPool::_cp_oop_offset;
int sun_reflect_UnsafeStaticFieldAccessorImpl::_base_offset;


// Support for java_lang_StackTraceElement

void java_lang_StackTraceElement::set_fileName(oop element, oop value) {
  element->obj_field_put(fileName_offset, value);
}

void java_lang_StackTraceElement::set_declaringClass(oop element, oop value) {
  element->obj_field_put(declaringClass_offset, value);
}

void java_lang_StackTraceElement::set_methodName(oop element, oop value) {
  element->obj_field_put(methodName_offset, value);
}

void java_lang_StackTraceElement::set_lineNumber(oop element, int value) {
  element->int_field_put(lineNumber_offset, value);
}


// Support for java Assertions - java_lang_AssertionStatusDirectives.

void java_lang_AssertionStatusDirectives::set_classes(oop o, oop val) {
  o->obj_field_put(classes_offset, val);
}

void java_lang_AssertionStatusDirectives::set_classEnabled(oop o, oop val) {
  o->obj_field_put(classEnabled_offset, val);
}

void java_lang_AssertionStatusDirectives::set_packages(oop o, oop val) {
  o->obj_field_put(packages_offset, val);
}

void java_lang_AssertionStatusDirectives::set_packageEnabled(oop o, oop val) {
  o->obj_field_put(packageEnabled_offset, val);
}

void java_lang_AssertionStatusDirectives::set_deflt(oop o, bool val) {
  o->bool_field_put(deflt_offset, val);
}


// Support for intrinsification of java.nio.Buffer.checkIndex
int java_nio_Buffer::limit_offset() {
  return _limit_offset;
}


void java_nio_Buffer::compute_offsets() {
  klassOop k = SystemDictionary::java_nio_Buffer_klass();
  assert(k != NULL, "must be loaded in 1.4+");
  compute_offset(_limit_offset, k, vmSymbols::limit_name(), vmSymbols::int_signature());
}

// Support for intrinsification of sun.misc.AtomicLongCSImpl.attemptUpdate
int sun_misc_AtomicLongCSImpl::value_offset() {
  assert(SystemDictionary::sun_misc_AtomicLongCSImpl_klass() != NULL, "can't call this");
  return _value_offset;
}


void sun_misc_AtomicLongCSImpl::compute_offsets() {
  klassOop k = SystemDictionary::sun_misc_AtomicLongCSImpl_klass();
  // If this class is not present, its value field offset won't be referenced.
  if (k != NULL) {
    compute_offset(_value_offset, k, vmSymbols::value_name(), vmSymbols::long_signature());
  }
}

void java_util_concurrent_locks_AbstractOwnableSynchronizer::initialize(TRAPS) {
  if (_owner_offset != 0) return;

  assert(JDK_Version::is_gte_jdk16x_version(), "Must be JDK 1.6 or later");
  SystemDictionary::load_abstract_ownable_synchronizer_klass(CHECK);
  klassOop k = SystemDictionary::abstract_ownable_synchronizer_klass();
  compute_offset(_owner_offset, k,
                 vmSymbols::exclusive_owner_thread_name(), vmSymbols::thread_signature());
}

oop java_util_concurrent_locks_AbstractOwnableSynchronizer::get_owner_threadObj(oop obj) {
  assert(_owner_offset != 0, "Must be initialized");
  return obj->obj_field(_owner_offset);
}

// Compute hard-coded offsets
// Invoked before SystemDictionary::initialize, so pre-loaded classes
// are not available to determine the offset_of_static_fields.
void JavaClasses::compute_hard_coded_offsets() {
  const int x = heapOopSize;
  const int header = instanceOopDesc::base_offset_in_bytes();

  // Do the String Class
  java_lang_String::value_offset  = java_lang_String::hc_value_offset  * x + header;
  java_lang_String::offset_offset = java_lang_String::hc_offset_offset * x + header;
  java_lang_String::count_offset  = java_lang_String::offset_offset + sizeof (jint);
  java_lang_String::hash_offset   = java_lang_String::count_offset + sizeof (jint);

  // Do the Class Class
  java_lang_Class::klass_offset = java_lang_Class::hc_klass_offset * x + header;
  java_lang_Class::array_klass_offset = java_lang_Class::hc_array_klass_offset * x + header;
  java_lang_Class::resolved_constructor_offset = java_lang_Class::hc_resolved_constructor_offset * x + header;

  // This is NOT an offset
  java_lang_Class::number_of_fake_oop_fields = java_lang_Class::hc_number_of_fake_oop_fields;

  // Throwable Class
  java_lang_Throwable::backtrace_offset  = java_lang_Throwable::hc_backtrace_offset  * x + header;
  java_lang_Throwable::detailMessage_offset = java_lang_Throwable::hc_detailMessage_offset * x + header;
  java_lang_Throwable::cause_offset      = java_lang_Throwable::hc_cause_offset      * x + header;
  java_lang_Throwable::stackTrace_offset = java_lang_Throwable::hc_stackTrace_offset * x + header;

  // java_lang_boxing_object
  java_lang_boxing_object::value_offset = java_lang_boxing_object::hc_value_offset + header;
  java_lang_boxing_object::long_value_offset = align_size_up((java_lang_boxing_object::hc_value_offset + header), BytesPerLong);

  // java_lang_ref_Reference:
  java_lang_ref_Reference::referent_offset = java_lang_ref_Reference::hc_referent_offset * x + header;
  java_lang_ref_Reference::queue_offset = java_lang_ref_Reference::hc_queue_offset * x + header;
  java_lang_ref_Reference::next_offset  = java_lang_ref_Reference::hc_next_offset * x + header;
  java_lang_ref_Reference::discovered_offset  = java_lang_ref_Reference::hc_discovered_offset * x + header;
  java_lang_ref_Reference::static_lock_offset = java_lang_ref_Reference::hc_static_lock_offset *  x;
  java_lang_ref_Reference::static_pending_offset = java_lang_ref_Reference::hc_static_pending_offset * x;
  // Artificial fields for java_lang_ref_Reference
  // The first field is for the discovered field added in 1.4
  java_lang_ref_Reference::number_of_fake_oop_fields = 1;

  // java_lang_ref_SoftReference Class
  java_lang_ref_SoftReference::timestamp_offset = align_size_up((java_lang_ref_SoftReference::hc_timestamp_offset * x + header), BytesPerLong);
  // Don't multiply static fields because they are always in wordSize units
  java_lang_ref_SoftReference::static_clock_offset = java_lang_ref_SoftReference::hc_static_clock_offset * x;

  // java_lang_ClassLoader
  java_lang_ClassLoader::parent_offset = java_lang_ClassLoader::hc_parent_offset * x + header;

  // java_lang_System
  java_lang_System::static_in_offset  = java_lang_System::hc_static_in_offset  * x;
  java_lang_System::static_out_offset = java_lang_System::hc_static_out_offset * x;
  java_lang_System::static_err_offset = java_lang_System::hc_static_err_offset * x;

  // java_lang_StackTraceElement
  java_lang_StackTraceElement::declaringClass_offset = java_lang_StackTraceElement::hc_declaringClass_offset  * x + header;
  java_lang_StackTraceElement::methodName_offset = java_lang_StackTraceElement::hc_methodName_offset * x + header;
  java_lang_StackTraceElement::fileName_offset   = java_lang_StackTraceElement::hc_fileName_offset   * x + header;
  java_lang_StackTraceElement::lineNumber_offset = java_lang_StackTraceElement::hc_lineNumber_offset * x + header;
  java_lang_AssertionStatusDirectives::classes_offset = java_lang_AssertionStatusDirectives::hc_classes_offset * x + header;
  java_lang_AssertionStatusDirectives::classEnabled_offset = java_lang_AssertionStatusDirectives::hc_classEnabled_offset * x + header;
  java_lang_AssertionStatusDirectives::packages_offset = java_lang_AssertionStatusDirectives::hc_packages_offset * x + header;
  java_lang_AssertionStatusDirectives::packageEnabled_offset = java_lang_AssertionStatusDirectives::hc_packageEnabled_offset * x + header;
  java_lang_AssertionStatusDirectives::deflt_offset = java_lang_AssertionStatusDirectives::hc_deflt_offset * x + header;

}


// Compute non-hard-coded field offsets of all the classes in this file
void JavaClasses::compute_offsets() {

  java_lang_Class::compute_offsets();
  java_lang_System::compute_offsets();
  java_lang_Thread::compute_offsets();
  java_lang_ThreadGroup::compute_offsets();
  if (EnableMethodHandles) {
    java_dyn_MethodHandle::compute_offsets();
    sun_dyn_MemberName::compute_offsets();
    sun_dyn_DirectMethodHandle::compute_offsets();
    sun_dyn_BoundMethodHandle::compute_offsets();
    sun_dyn_AdapterMethodHandle::compute_offsets();
    java_dyn_MethodType::compute_offsets();
    java_dyn_MethodTypeForm::compute_offsets();
  }
  if (EnableInvokeDynamic) {
    java_dyn_CallSite::compute_offsets();
  }
  java_security_AccessControlContext::compute_offsets();
  // Initialize reflection classes. The layouts of these classes
  // changed with the new reflection implementation in JDK 1.4, and
  // since the Universe doesn't know what JDK version it is until this
  // point we defer computation of these offsets until now.
  java_lang_reflect_AccessibleObject::compute_offsets();
  java_lang_reflect_Method::compute_offsets();
  java_lang_reflect_Constructor::compute_offsets();
  java_lang_reflect_Field::compute_offsets();
  if (JDK_Version::is_gte_jdk14x_version()) {
    java_nio_Buffer::compute_offsets();
  }
  if (JDK_Version::is_gte_jdk15x_version()) {
    sun_reflect_ConstantPool::compute_offsets();
    sun_reflect_UnsafeStaticFieldAccessorImpl::compute_offsets();
  }
  sun_misc_AtomicLongCSImpl::compute_offsets();

  // generated interpreter code wants to know about the offsets we just computed:
  AbstractAssembler::update_delayed_values();
}

#ifndef PRODUCT

// These functions exist to assert the validity of hard-coded field offsets to guard
// against changes in the class files

bool JavaClasses::check_offset(const char *klass_name, int hardcoded_offset, const char *field_name, const char* field_sig) {
  EXCEPTION_MARK;
  fieldDescriptor fd;
  symbolHandle klass_sym = oopFactory::new_symbol_handle(klass_name, CATCH);
  klassOop k = SystemDictionary::resolve_or_fail(klass_sym, true, CATCH);
  instanceKlassHandle h_klass (THREAD, k);
  //instanceKlassHandle h_klass(klass);
  symbolHandle f_name = oopFactory::new_symbol_handle(field_name, CATCH);
  symbolHandle f_sig  = oopFactory::new_symbol_handle(field_sig, CATCH);
  if (!h_klass->find_local_field(f_name(), f_sig(), &fd)) {
    tty->print_cr("Nonstatic field %s.%s not found", klass_name, field_name);
    return false;
  }
  if (fd.is_static()) {
    tty->print_cr("Nonstatic field %s.%s appears to be static", klass_name, field_name);
    return false;
  }
  if (fd.offset() == hardcoded_offset ) {
    return true;
  } else {
    tty->print_cr("Offset of nonstatic field %s.%s is hardcoded as %d but should really be %d.",
                  klass_name, field_name, hardcoded_offset, fd.offset());
    return false;
  }
}


bool JavaClasses::check_static_offset(const char *klass_name, int hardcoded_offset, const char *field_name, const char* field_sig) {
  EXCEPTION_MARK;
  fieldDescriptor fd;
  symbolHandle klass_sym = oopFactory::new_symbol_handle(klass_name, CATCH);
  klassOop k = SystemDictionary::resolve_or_fail(klass_sym, true, CATCH);
  instanceKlassHandle h_klass (THREAD, k);
  symbolHandle f_name = oopFactory::new_symbol_handle(field_name, CATCH);
  symbolHandle f_sig  = oopFactory::new_symbol_handle(field_sig, CATCH);
  if (!h_klass->find_local_field(f_name(), f_sig(), &fd)) {
    tty->print_cr("Static field %s.%s not found", klass_name, field_name);
    return false;
  }
  if (!fd.is_static()) {
    tty->print_cr("Static field %s.%s appears to be nonstatic", klass_name, field_name);
    return false;
  }
  if (fd.offset() == hardcoded_offset + h_klass->offset_of_static_fields()) {
    return true;
  } else {
    tty->print_cr("Offset of static field %s.%s is hardcoded as %d but should really be %d.", klass_name, field_name, hardcoded_offset, fd.offset() - h_klass->offset_of_static_fields());
    return false;
  }
}


bool JavaClasses::check_constant(const char *klass_name, int hardcoded_constant, const char *field_name, const char* field_sig) {
  EXCEPTION_MARK;
  fieldDescriptor fd;
  symbolHandle klass_sym = oopFactory::new_symbol_handle(klass_name, CATCH);
  klassOop k = SystemDictionary::resolve_or_fail(klass_sym, true, CATCH);
  instanceKlassHandle h_klass (THREAD, k);
  symbolHandle f_name = oopFactory::new_symbol_handle(field_name, CATCH);
  symbolHandle f_sig  = oopFactory::new_symbol_handle(field_sig, CATCH);
  if (!h_klass->find_local_field(f_name(), f_sig(), &fd)) {
    tty->print_cr("Static field %s.%s not found", klass_name, field_name);
    return false;
  }
  if (!fd.is_static() || !fd.has_initial_value()) {
    tty->print_cr("Static field %s.%s appears to be non-constant", klass_name, field_name);
    return false;
  }
  if (!fd.initial_value_tag().is_int()) {
    tty->print_cr("Static field %s.%s is not an int", klass_name, field_name);
    return false;
  }
  jint field_value = fd.int_initial_value();
  if (field_value == hardcoded_constant) {
    return true;
  } else {
    tty->print_cr("Constant value of static field %s.%s is hardcoded as %d but should really be %d.", klass_name, field_name, hardcoded_constant, field_value);
    return false;
  }
}


// Check the hard-coded field offsets of all the classes in this file

void JavaClasses::check_offsets() {
  bool valid = true;

#define CHECK_OFFSET(klass_name, cpp_klass_name, field_name, field_sig) \
  valid &= check_offset(klass_name, cpp_klass_name :: field_name ## _offset, #field_name, field_sig)

#define CHECK_LONG_OFFSET(klass_name, cpp_klass_name, field_name, field_sig) \
  valid &= check_offset(klass_name, cpp_klass_name :: long_ ## field_name ## _offset, #field_name, field_sig)

#define CHECK_STATIC_OFFSET(klass_name, cpp_klass_name, field_name, field_sig) \
  valid &= check_static_offset(klass_name, cpp_klass_name :: static_ ## field_name ## _offset, #field_name, field_sig)

#define CHECK_CONSTANT(klass_name, cpp_klass_name, field_name, field_sig) \
  valid &= check_constant(klass_name, cpp_klass_name :: field_name, #field_name, field_sig)

  // java.lang.String

  CHECK_OFFSET("java/lang/String", java_lang_String, value, "[C");
  CHECK_OFFSET("java/lang/String", java_lang_String, offset, "I");
  CHECK_OFFSET("java/lang/String", java_lang_String, count, "I");
  CHECK_OFFSET("java/lang/String", java_lang_String, hash, "I");

  // java.lang.Class

  // Fake fields
  // CHECK_OFFSET("java/lang/Class", java_lang_Class, klass); // %%% this needs to be checked
  // CHECK_OFFSET("java/lang/Class", java_lang_Class, array_klass); // %%% this needs to be checked
  // CHECK_OFFSET("java/lang/Class", java_lang_Class, resolved_constructor); // %%% this needs to be checked

  // java.lang.Throwable

  CHECK_OFFSET("java/lang/Throwable", java_lang_Throwable, backtrace, "Ljava/lang/Object;");
  CHECK_OFFSET("java/lang/Throwable", java_lang_Throwable, detailMessage, "Ljava/lang/String;");
  CHECK_OFFSET("java/lang/Throwable", java_lang_Throwable, cause, "Ljava/lang/Throwable;");
  CHECK_OFFSET("java/lang/Throwable", java_lang_Throwable, stackTrace, "[Ljava/lang/StackTraceElement;");

  // Boxed primitive objects (java_lang_boxing_object)

  CHECK_OFFSET("java/lang/Boolean",   java_lang_boxing_object, value, "Z");
  CHECK_OFFSET("java/lang/Character", java_lang_boxing_object, value, "C");
  CHECK_OFFSET("java/lang/Float",     java_lang_boxing_object, value, "F");
  CHECK_LONG_OFFSET("java/lang/Double", java_lang_boxing_object, value, "D");
  CHECK_OFFSET("java/lang/Byte",      java_lang_boxing_object, value, "B");
  CHECK_OFFSET("java/lang/Short",     java_lang_boxing_object, value, "S");
  CHECK_OFFSET("java/lang/Integer",   java_lang_boxing_object, value, "I");
  CHECK_LONG_OFFSET("java/lang/Long", java_lang_boxing_object, value, "J");

  // java.lang.ClassLoader

  CHECK_OFFSET("java/lang/ClassLoader", java_lang_ClassLoader, parent,      "Ljava/lang/ClassLoader;");

  // java.lang.System

  CHECK_STATIC_OFFSET("java/lang/System", java_lang_System,  in, "Ljava/io/InputStream;");
  CHECK_STATIC_OFFSET("java/lang/System", java_lang_System, out, "Ljava/io/PrintStream;");
  CHECK_STATIC_OFFSET("java/lang/System", java_lang_System, err, "Ljava/io/PrintStream;");

  // java.lang.StackTraceElement

  CHECK_OFFSET("java/lang/StackTraceElement", java_lang_StackTraceElement, declaringClass, "Ljava/lang/String;");
  CHECK_OFFSET("java/lang/StackTraceElement", java_lang_StackTraceElement, methodName, "Ljava/lang/String;");
  CHECK_OFFSET("java/lang/StackTraceElement", java_lang_StackTraceElement,   fileName, "Ljava/lang/String;");
  CHECK_OFFSET("java/lang/StackTraceElement", java_lang_StackTraceElement, lineNumber, "I");

  // java.lang.ref.Reference

  CHECK_OFFSET("java/lang/ref/Reference", java_lang_ref_Reference, referent, "Ljava/lang/Object;");
  CHECK_OFFSET("java/lang/ref/Reference", java_lang_ref_Reference, queue, "Ljava/lang/ref/ReferenceQueue;");
  CHECK_OFFSET("java/lang/ref/Reference", java_lang_ref_Reference, next, "Ljava/lang/ref/Reference;");
  // Fake field
  //CHECK_OFFSET("java/lang/ref/Reference", java_lang_ref_Reference, discovered, "Ljava/lang/ref/Reference;");
  CHECK_STATIC_OFFSET("java/lang/ref/Reference", java_lang_ref_Reference, lock, "Ljava/lang/ref/Reference$Lock;");
  CHECK_STATIC_OFFSET("java/lang/ref/Reference", java_lang_ref_Reference, pending, "Ljava/lang/ref/Reference;");

  // java.lang.ref.SoftReference

  CHECK_OFFSET("java/lang/ref/SoftReference", java_lang_ref_SoftReference, timestamp, "J");
  CHECK_STATIC_OFFSET("java/lang/ref/SoftReference", java_lang_ref_SoftReference, clock, "J");

  // java.lang.AssertionStatusDirectives
  //
  // The CheckAssertionStatusDirectives boolean can be removed from here and
  // globals.hpp after the AssertionStatusDirectives class has been integrated
  // into merlin "for some time."  Without it, the vm will fail with early
  // merlin builds.

  if (CheckAssertionStatusDirectives && JDK_Version::is_gte_jdk14x_version()) {
    const char* nm = "java/lang/AssertionStatusDirectives";
    const char* sig = "[Ljava/lang/String;";
    CHECK_OFFSET(nm, java_lang_AssertionStatusDirectives, classes, sig);
    CHECK_OFFSET(nm, java_lang_AssertionStatusDirectives, classEnabled, "[Z");
    CHECK_OFFSET(nm, java_lang_AssertionStatusDirectives, packages, sig);
    CHECK_OFFSET(nm, java_lang_AssertionStatusDirectives, packageEnabled, "[Z");
    CHECK_OFFSET(nm, java_lang_AssertionStatusDirectives, deflt, "Z");
  }

  if (!valid) vm_exit_during_initialization("Hard-coded field offset verification failed");
}

#endif // PRODUCT

void javaClasses_init() {
  JavaClasses::compute_offsets();
  JavaClasses::check_offsets();
  FilteredFieldsMap::initialize();  // must be done after computing offsets.
}
