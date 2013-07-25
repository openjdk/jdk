/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_JAVACLASSES_HPP
#define SHARE_VM_CLASSFILE_JAVACLASSES_HPP

#include "classfile/systemDictionary.hpp"
#include "jvmtifiles/jvmti.h"
#include "oops/oop.hpp"
#include "runtime/os.hpp"
#include "utilities/utf8.hpp"

// Interface for manipulating the basic Java classes.
//
// All dependencies on layout of actual Java classes should be kept here.
// If the layout of any of the classes above changes the offsets must be adjusted.
//
// For most classes we hardwire the offsets for performance reasons. In certain
// cases (e.g. java.security.AccessControlContext) we compute the offsets at
// startup since the layout here differs between JDK1.2 and JDK1.3.
//
// Note that fields (static and non-static) are arranged with oops before non-oops
// on a per class basis. The offsets below have to reflect this ordering.
//
// When editing the layouts please update the check_offset verification code
// correspondingly. The names in the enums must be identical to the actual field
// names in order for the verification code to work.


// Interface to java.lang.String objects

class java_lang_String : AllStatic {
 private:
  static int value_offset;
  static int offset_offset;
  static int count_offset;
  static int hash_offset;

  static bool initialized;

  static Handle basic_create(int length, TRAPS);

  static void set_value( oop string, typeArrayOop buffer) {
    assert(initialized, "Must be initialized");
    string->obj_field_put(value_offset,  (oop)buffer);
  }
  static void set_offset(oop string, int offset) {
    assert(initialized, "Must be initialized");
    if (offset_offset > 0) {
      string->int_field_put(offset_offset, offset);
    }
  }
  static void set_count( oop string, int count) {
    assert(initialized, "Must be initialized");
    if (count_offset > 0) {
      string->int_field_put(count_offset,  count);
    }
  }

 public:
  static void compute_offsets();

  // Instance creation
  static Handle create_from_unicode(jchar* unicode, int len, TRAPS);
  static oop    create_oop_from_unicode(jchar* unicode, int len, TRAPS);
  static Handle create_from_str(const char* utf8_str, TRAPS);
  static oop    create_oop_from_str(const char* utf8_str, TRAPS);
  static Handle create_from_symbol(Symbol* symbol, TRAPS);
  static Handle create_from_platform_dependent_str(const char* str, TRAPS);
  static Handle char_converter(Handle java_string, jchar from_char, jchar to_char, TRAPS);

  static bool has_offset_field()  {
    assert(initialized, "Must be initialized");
    return (offset_offset > 0);
  }

  static bool has_count_field()  {
    assert(initialized, "Must be initialized");
    return (count_offset > 0);
  }

  static bool has_hash_field()  {
    assert(initialized, "Must be initialized");
    return (hash_offset > 0);
  }

  static int value_offset_in_bytes()  {
    assert(initialized && (value_offset > 0), "Must be initialized");
    return value_offset;
  }
  static int count_offset_in_bytes()  {
    assert(initialized && (count_offset > 0), "Must be initialized");
    return count_offset;
  }
  static int offset_offset_in_bytes() {
    assert(initialized && (offset_offset > 0), "Must be initialized");
    return offset_offset;
  }
  static int hash_offset_in_bytes()   {
    assert(initialized && (hash_offset > 0), "Must be initialized");
    return hash_offset;
  }

  // Accessors
  static typeArrayOop value(oop java_string) {
    assert(initialized && (value_offset > 0), "Must be initialized");
    assert(is_instance(java_string), "must be java_string");
    return (typeArrayOop) java_string->obj_field(value_offset);
  }
  static int offset(oop java_string) {
    assert(initialized, "Must be initialized");
    assert(is_instance(java_string), "must be java_string");
    if (offset_offset > 0) {
      return java_string->int_field(offset_offset);
    } else {
      return 0;
    }
  }
  static int length(oop java_string) {
    assert(initialized, "Must be initialized");
    assert(is_instance(java_string), "must be java_string");
    if (count_offset > 0) {
      return java_string->int_field(count_offset);
    } else {
      return ((typeArrayOop)java_string->obj_field(value_offset))->length();
    }
  }
  static int utf8_length(oop java_string);

  // String converters
  static char*  as_utf8_string(oop java_string);
  static char*  as_utf8_string(oop java_string, char* buf, int buflen);
  static char*  as_utf8_string(oop java_string, int start, int len);
  static char*  as_platform_dependent_str(Handle java_string, TRAPS);
  static jchar* as_unicode_string(oop java_string, int& length, TRAPS);
  // produce an ascii string with all other values quoted using \u####
  static char*  as_quoted_ascii(oop java_string);

  // Compute the hash value for a java.lang.String object which would
  // contain the characters passed in.
  //
  // As the hash value used by the String object itself, in
  // String.hashCode().  This value is normally calculated in Java code
  // in the String.hashCode method(), but is precomputed for String
  // objects in the shared archive file.
  // hash P(31) from Kernighan & Ritchie
  //
  // For this reason, THIS ALGORITHM MUST MATCH String.hashCode().
  template <typename T> static unsigned int hash_code(T* s, int len) {
    unsigned int h = 0;
    while (len-- > 0) {
      h = 31*h + (unsigned int) *s;
      s++;
    }
    return h;
  }
  static unsigned int hash_code(oop java_string);

  // This is the string hash code used by the StringTable, which may be
  // the same as String.hashCode or an alternate hash code.
  static unsigned int hash_string(oop java_string);

  static bool equals(oop java_string, jchar* chars, int len);

  // Conversion between '.' and '/' formats
  static Handle externalize_classname(Handle java_string, TRAPS) { return char_converter(java_string, '/', '.', THREAD); }
  static Handle internalize_classname(Handle java_string, TRAPS) { return char_converter(java_string, '.', '/', THREAD); }

  // Conversion
  static Symbol* as_symbol(Handle java_string, TRAPS);
  static Symbol* as_symbol_or_null(oop java_string);

  // Testers
  static bool is_instance(oop obj) {
    return obj != NULL && obj->klass() == SystemDictionary::String_klass();
  }

  // Debugging
  static void print(Handle java_string, outputStream* st);
  friend class JavaClasses;
};


// Interface to java.lang.Class objects

#define CLASS_INJECTED_FIELDS(macro)                                       \
  macro(java_lang_Class, klass,                  intptr_signature,  false) \
  macro(java_lang_Class, array_klass,            intptr_signature,  false) \
  macro(java_lang_Class, oop_size,               int_signature,     false) \
  macro(java_lang_Class, static_oop_field_count, int_signature,     false) \
  macro(java_lang_Class, protection_domain,      object_signature,  false) \
  macro(java_lang_Class, init_lock,              object_signature,  false) \
  macro(java_lang_Class, signers,                object_signature,  false)

class java_lang_Class : AllStatic {
  friend class VMStructs;

 private:
  // The fake offsets are added by the class loader when java.lang.Class is loaded

  static int _klass_offset;
  static int _array_klass_offset;

  static int _oop_size_offset;
  static int _static_oop_field_count_offset;

  static int _protection_domain_offset;
  static int _init_lock_offset;
  static int _signers_offset;

  static bool offsets_computed;
  static int classRedefinedCount_offset;
  static GrowableArray<Klass*>* _fixup_mirror_list;

  static void set_init_lock(oop java_class, oop init_lock);
 public:
  static void compute_offsets();

  // Instance creation
  static oop  create_mirror(KlassHandle k, Handle protection_domain, TRAPS);
  static void fixup_mirror(KlassHandle k, TRAPS);
  static oop  create_basic_type_mirror(const char* basic_type_name, BasicType type, TRAPS);
  // Conversion
  static Klass* as_Klass(oop java_class);
  static void set_klass(oop java_class, Klass* klass);
  static BasicType as_BasicType(oop java_class, Klass** reference_klass = NULL);
  static BasicType as_BasicType(oop java_class, KlassHandle* reference_klass) {
    Klass* refk_oop = NULL;
    BasicType result = as_BasicType(java_class, &refk_oop);
    (*reference_klass) = KlassHandle(refk_oop);
    return result;
  }
  static Symbol* as_signature(oop java_class, bool intern_if_not_found, TRAPS);
  static void print_signature(oop java_class, outputStream *st);
  // Testing
  static bool is_instance(oop obj) {
    return obj != NULL && obj->klass() == SystemDictionary::Class_klass();
  }
  static bool is_primitive(oop java_class);
  static BasicType primitive_type(oop java_class);
  static oop primitive_mirror(BasicType t);
  // JVM_NewArray support
  static Klass* array_klass(oop java_class);
  static void set_array_klass(oop java_class, Klass* klass);
  // compiler support for class operations
  static int klass_offset_in_bytes()                { return _klass_offset; }
  static int array_klass_offset_in_bytes()          { return _array_klass_offset; }
  // Support for classRedefinedCount field
  static int classRedefinedCount(oop the_class_mirror);
  static void set_classRedefinedCount(oop the_class_mirror, int value);

  // Support for embedded per-class oops
  static oop  protection_domain(oop java_class);
  static void set_protection_domain(oop java_class, oop protection_domain);
  static oop  init_lock(oop java_class);
  static objArrayOop  signers(oop java_class);
  static void set_signers(oop java_class, objArrayOop signers);

  static int oop_size(oop java_class);
  static void set_oop_size(oop java_class, int size);
  static int static_oop_field_count(oop java_class);
  static void set_static_oop_field_count(oop java_class, int size);

  static GrowableArray<Klass*>* fixup_mirror_list() {
    return _fixup_mirror_list;
  }
  static void set_fixup_mirror_list(GrowableArray<Klass*>* v) {
    _fixup_mirror_list = v;
  }
  // Debugging
  friend class JavaClasses;
  friend class InstanceKlass;   // verification code accesses offsets
  friend class ClassFileParser; // access to number_of_fake_fields
};

// Interface to java.lang.Thread objects

class java_lang_Thread : AllStatic {
 private:
  // Note that for this class the layout changed between JDK1.2 and JDK1.3,
  // so we compute the offsets at startup rather than hard-wiring them.
  static int _name_offset;
  static int _group_offset;
  static int _contextClassLoader_offset;
  static int _inheritedAccessControlContext_offset;
  static int _priority_offset;
  static int _eetop_offset;
  static int _daemon_offset;
  static int _stillborn_offset;
  static int _stackSize_offset;
  static int _tid_offset;
  static int _thread_status_offset;
  static int _park_blocker_offset;
  static int _park_event_offset ;

  static void compute_offsets();

 public:
  // Instance creation
  static oop create();
  // Returns the JavaThread associated with the thread obj
  static JavaThread* thread(oop java_thread);
  // Set JavaThread for instance
  static void set_thread(oop java_thread, JavaThread* thread);
  // Name
  static typeArrayOop name(oop java_thread);
  static void set_name(oop java_thread, typeArrayOop name);
  // Priority
  static ThreadPriority priority(oop java_thread);
  static void set_priority(oop java_thread, ThreadPriority priority);
  // Thread group
  static oop  threadGroup(oop java_thread);
  // Stillborn
  static bool is_stillborn(oop java_thread);
  static void set_stillborn(oop java_thread);
  // Alive (NOTE: this is not really a field, but provides the correct
  // definition without doing a Java call)
  static bool is_alive(oop java_thread);
  // Daemon
  static bool is_daemon(oop java_thread);
  static void set_daemon(oop java_thread);
  // Context ClassLoader
  static oop context_class_loader(oop java_thread);
  // Control context
  static oop inherited_access_control_context(oop java_thread);
  // Stack size hint
  static jlong stackSize(oop java_thread);
  // Thread ID
  static jlong thread_id(oop java_thread);

  // Blocker object responsible for thread parking
  static oop park_blocker(oop java_thread);

  // Pointer to type-stable park handler, encoded as jlong.
  // Should be set when apparently null
  // For details, see unsafe.cpp Unsafe_Unpark
  static jlong park_event(oop java_thread);
  static bool set_park_event(oop java_thread, jlong ptr);

  // Java Thread Status for JVMTI and M&M use.
  // This thread status info is saved in threadStatus field of
  // java.lang.Thread java class.
  enum ThreadStatus {
    NEW                      = 0,
    RUNNABLE                 = JVMTI_THREAD_STATE_ALIVE +          // runnable / running
                               JVMTI_THREAD_STATE_RUNNABLE,
    SLEEPING                 = JVMTI_THREAD_STATE_ALIVE +          // Thread.sleep()
                               JVMTI_THREAD_STATE_WAITING +
                               JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT +
                               JVMTI_THREAD_STATE_SLEEPING,
    IN_OBJECT_WAIT           = JVMTI_THREAD_STATE_ALIVE +          // Object.wait()
                               JVMTI_THREAD_STATE_WAITING +
                               JVMTI_THREAD_STATE_WAITING_INDEFINITELY +
                               JVMTI_THREAD_STATE_IN_OBJECT_WAIT,
    IN_OBJECT_WAIT_TIMED     = JVMTI_THREAD_STATE_ALIVE +          // Object.wait(long)
                               JVMTI_THREAD_STATE_WAITING +
                               JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT +
                               JVMTI_THREAD_STATE_IN_OBJECT_WAIT,
    PARKED                   = JVMTI_THREAD_STATE_ALIVE +          // LockSupport.park()
                               JVMTI_THREAD_STATE_WAITING +
                               JVMTI_THREAD_STATE_WAITING_INDEFINITELY +
                               JVMTI_THREAD_STATE_PARKED,
    PARKED_TIMED             = JVMTI_THREAD_STATE_ALIVE +          // LockSupport.park(long)
                               JVMTI_THREAD_STATE_WAITING +
                               JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT +
                               JVMTI_THREAD_STATE_PARKED,
    BLOCKED_ON_MONITOR_ENTER = JVMTI_THREAD_STATE_ALIVE +          // (re-)entering a synchronization block
                               JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER,
    TERMINATED               = JVMTI_THREAD_STATE_TERMINATED
  };
  // Write thread status info to threadStatus field of java.lang.Thread.
  static void set_thread_status(oop java_thread_oop, ThreadStatus status);
  // Read thread status info from threadStatus field of java.lang.Thread.
  static ThreadStatus get_thread_status(oop java_thread_oop);

  static const char*  thread_status_name(oop java_thread_oop);

  // Debugging
  friend class JavaClasses;
};

// Interface to java.lang.ThreadGroup objects

class java_lang_ThreadGroup : AllStatic {
 private:
  static int _parent_offset;
  static int _name_offset;
  static int _threads_offset;
  static int _groups_offset;
  static int _maxPriority_offset;
  static int _destroyed_offset;
  static int _daemon_offset;
  static int _vmAllowSuspension_offset;
  static int _nthreads_offset;
  static int _ngroups_offset;

  static void compute_offsets();

 public:
  // parent ThreadGroup
  static oop  parent(oop java_thread_group);
  // name
  static typeArrayOop name(oop java_thread_group);
  // ("name as oop" accessor is not necessary)
  // Number of threads in group
  static int nthreads(oop java_thread_group);
  // threads
  static objArrayOop threads(oop java_thread_group);
  // Number of threads in group
  static int ngroups(oop java_thread_group);
  // groups
  static objArrayOop groups(oop java_thread_group);
  // maxPriority in group
  static ThreadPriority maxPriority(oop java_thread_group);
  // Destroyed
  static bool is_destroyed(oop java_thread_group);
  // Daemon
  static bool is_daemon(oop java_thread_group);
  // vmAllowSuspension
  static bool is_vmAllowSuspension(oop java_thread_group);
  // Debugging
  friend class JavaClasses;
};



// Interface to java.lang.Throwable objects

class java_lang_Throwable: AllStatic {
  friend class BacktraceBuilder;

 private:
  // Offsets
  enum {
    hc_backtrace_offset     =  0,
    hc_detailMessage_offset =  1,
    hc_cause_offset         =  2,  // New since 1.4
    hc_stackTrace_offset    =  3   // New since 1.4
  };
  enum {
      hc_static_unassigned_stacktrace_offset = 0  // New since 1.7
  };
  // Trace constants
  enum {
    trace_methods_offset = 0,
    trace_bcis_offset    = 1,
    trace_mirrors_offset = 2,
    trace_next_offset    = 3,
    trace_size           = 4,
    trace_chunk_size     = 32
  };

  static int backtrace_offset;
  static int detailMessage_offset;
  static int cause_offset;
  static int stackTrace_offset;
  static int static_unassigned_stacktrace_offset;

  // Printing
  static char* print_stack_element_to_buffer(Handle mirror, int method, int version, int bci);
  // StackTrace (programmatic access, new since 1.4)
  static void clear_stacktrace(oop throwable);
  // No stack trace available
  static const char* no_stack_trace_message();
  // Stacktrace (post JDK 1.7.0 to allow immutability protocol to be followed)
  static void set_stacktrace(oop throwable, oop st_element_array);
  static oop unassigned_stacktrace();

 public:
  // Backtrace
  static oop backtrace(oop throwable);
  static void set_backtrace(oop throwable, oop value);
  // Needed by JVMTI to filter out this internal field.
  static int get_backtrace_offset() { return backtrace_offset;}
  static int get_detailMessage_offset() { return detailMessage_offset;}
  // Message
  static oop message(oop throwable);
  static oop message(Handle throwable);
  static void set_message(oop throwable, oop value);
  static void print_stack_element(outputStream *st, Handle mirror, int method,
                                  int version, int bci);
  static void print_stack_element(outputStream *st, methodHandle method, int bci);
  static void print_stack_usage(Handle stream);

  // Allocate space for backtrace (created but stack trace not filled in)
  static void allocate_backtrace(Handle throwable, TRAPS);
  // Fill in current stack trace for throwable with preallocated backtrace (no GC)
  static void fill_in_stack_trace_of_preallocated_backtrace(Handle throwable);
  // Fill in current stack trace, can cause GC
  static void fill_in_stack_trace(Handle throwable, methodHandle method, TRAPS);
  static void fill_in_stack_trace(Handle throwable, methodHandle method = methodHandle());
  // Programmatic access to stack trace
  static oop  get_stack_trace_element(oop throwable, int index, TRAPS);
  static int  get_stack_trace_depth(oop throwable, TRAPS);
  // Printing
  static void print(oop throwable, outputStream* st);
  static void print(Handle throwable, outputStream* st);
  static void print_stack_trace(oop throwable, outputStream* st);
  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.reflect.AccessibleObject objects

class java_lang_reflect_AccessibleObject: AllStatic {
 private:
  // Note that to reduce dependencies on the JDK we compute these
  // offsets at run-time.
  static int override_offset;

  static void compute_offsets();

 public:
  // Accessors
  static jboolean override(oop reflect);
  static void set_override(oop reflect, jboolean value);

  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.reflect.Method objects

class java_lang_reflect_Method : public java_lang_reflect_AccessibleObject {
 private:
  // Note that to reduce dependencies on the JDK we compute these
  // offsets at run-time.
  static int clazz_offset;
  static int name_offset;
  static int returnType_offset;
  static int parameterTypes_offset;
  static int exceptionTypes_offset;
  static int slot_offset;
  static int modifiers_offset;
  static int signature_offset;
  static int annotations_offset;
  static int parameter_annotations_offset;
  static int annotation_default_offset;
  static int type_annotations_offset;

  static void compute_offsets();

 public:
  // Allocation
  static Handle create(TRAPS);

  // Accessors
  static oop clazz(oop reflect);
  static void set_clazz(oop reflect, oop value);

  static oop name(oop method);
  static void set_name(oop method, oop value);

  static oop return_type(oop method);
  static void set_return_type(oop method, oop value);

  static oop parameter_types(oop method);
  static void set_parameter_types(oop method, oop value);

  static oop exception_types(oop method);
  static void set_exception_types(oop method, oop value);

  static int slot(oop reflect);
  static void set_slot(oop reflect, int value);

  static int modifiers(oop method);
  static void set_modifiers(oop method, int value);

  static bool has_signature_field();
  static oop signature(oop method);
  static void set_signature(oop method, oop value);

  static bool has_annotations_field();
  static oop annotations(oop method);
  static void set_annotations(oop method, oop value);

  static bool has_parameter_annotations_field();
  static oop parameter_annotations(oop method);
  static void set_parameter_annotations(oop method, oop value);

  static bool has_annotation_default_field();
  static oop annotation_default(oop method);
  static void set_annotation_default(oop method, oop value);

  static bool has_type_annotations_field();
  static oop type_annotations(oop method);
  static void set_type_annotations(oop method, oop value);

  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.reflect.Constructor objects

class java_lang_reflect_Constructor : public java_lang_reflect_AccessibleObject {
 private:
  // Note that to reduce dependencies on the JDK we compute these
  // offsets at run-time.
  static int clazz_offset;
  static int parameterTypes_offset;
  static int exceptionTypes_offset;
  static int slot_offset;
  static int modifiers_offset;
  static int signature_offset;
  static int annotations_offset;
  static int parameter_annotations_offset;
  static int type_annotations_offset;

  static void compute_offsets();

 public:
  // Allocation
  static Handle create(TRAPS);

  // Accessors
  static oop clazz(oop reflect);
  static void set_clazz(oop reflect, oop value);

  static oop parameter_types(oop constructor);
  static void set_parameter_types(oop constructor, oop value);

  static oop exception_types(oop constructor);
  static void set_exception_types(oop constructor, oop value);

  static int slot(oop reflect);
  static void set_slot(oop reflect, int value);

  static int modifiers(oop constructor);
  static void set_modifiers(oop constructor, int value);

  static bool has_signature_field();
  static oop signature(oop constructor);
  static void set_signature(oop constructor, oop value);

  static bool has_annotations_field();
  static oop annotations(oop constructor);
  static void set_annotations(oop constructor, oop value);

  static bool has_parameter_annotations_field();
  static oop parameter_annotations(oop method);
  static void set_parameter_annotations(oop method, oop value);

  static bool has_type_annotations_field();
  static oop type_annotations(oop constructor);
  static void set_type_annotations(oop constructor, oop value);

  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.reflect.Field objects

class java_lang_reflect_Field : public java_lang_reflect_AccessibleObject {
 private:
  // Note that to reduce dependencies on the JDK we compute these
  // offsets at run-time.
  static int clazz_offset;
  static int name_offset;
  static int type_offset;
  static int slot_offset;
  static int modifiers_offset;
  static int signature_offset;
  static int annotations_offset;
  static int type_annotations_offset;

  static void compute_offsets();

 public:
  // Allocation
  static Handle create(TRAPS);

  // Accessors
  static oop clazz(oop reflect);
  static void set_clazz(oop reflect, oop value);

  static oop name(oop field);
  static void set_name(oop field, oop value);

  static oop type(oop field);
  static void set_type(oop field, oop value);

  static int slot(oop reflect);
  static void set_slot(oop reflect, int value);

  static int modifiers(oop field);
  static void set_modifiers(oop field, int value);

  static bool has_signature_field();
  static oop signature(oop constructor);
  static void set_signature(oop constructor, oop value);

  static bool has_annotations_field();
  static oop annotations(oop constructor);
  static void set_annotations(oop constructor, oop value);

  static bool has_parameter_annotations_field();
  static oop parameter_annotations(oop method);
  static void set_parameter_annotations(oop method, oop value);

  static bool has_annotation_default_field();
  static oop annotation_default(oop method);
  static void set_annotation_default(oop method, oop value);

  static bool has_type_annotations_field();
  static oop type_annotations(oop field);
  static void set_type_annotations(oop field, oop value);

  // Debugging
  friend class JavaClasses;
};

class java_lang_reflect_Parameter {
 private:
  // Note that to reduce dependencies on the JDK we compute these
  // offsets at run-time.
  static int name_offset;
  static int modifiers_offset;
  static int index_offset;
  static int executable_offset;

  static void compute_offsets();

 public:
  // Allocation
  static Handle create(TRAPS);

  // Accessors
  static oop name(oop field);
  static void set_name(oop field, oop value);

  static int index(oop reflect);
  static void set_index(oop reflect, int value);

  static int modifiers(oop reflect);
  static void set_modifiers(oop reflect, int value);

  static oop executable(oop constructor);
  static void set_executable(oop constructor, oop value);

  friend class JavaClasses;
};

// Interface to sun.reflect.ConstantPool objects
class sun_reflect_ConstantPool {
 private:
  // Note that to reduce dependencies on the JDK we compute these
  // offsets at run-time.
  static int _oop_offset;

  static void compute_offsets();

 public:
  // Allocation
  static Handle create(TRAPS);

  // Accessors
  static void set_cp(oop reflect, ConstantPool* value);
  static int oop_offset() {
    return _oop_offset;
  }

  static ConstantPool* get_cp(oop reflect);

  // Debugging
  friend class JavaClasses;
};

// Interface to sun.reflect.UnsafeStaticFieldAccessorImpl objects
class sun_reflect_UnsafeStaticFieldAccessorImpl {
 private:
  static int _base_offset;
  static void compute_offsets();

 public:
  static int base_offset() {
    return _base_offset;
  }

  // Debugging
  friend class JavaClasses;
};

// Interface to java.lang primitive type boxing objects:
//  - java.lang.Boolean
//  - java.lang.Character
//  - java.lang.Float
//  - java.lang.Double
//  - java.lang.Byte
//  - java.lang.Short
//  - java.lang.Integer
//  - java.lang.Long

// This could be separated out into 8 individual classes.

class java_lang_boxing_object: AllStatic {
 private:
  enum {
   hc_value_offset = 0
  };
  static int value_offset;
  static int long_value_offset;

  static oop initialize_and_allocate(BasicType type, TRAPS);
 public:
  // Allocation. Returns a boxed value, or NULL for invalid type.
  static oop create(BasicType type, jvalue* value, TRAPS);
  // Accessors. Returns the basic type being boxed, or T_ILLEGAL for invalid oop.
  static BasicType get_value(oop box, jvalue* value);
  static BasicType set_value(oop box, jvalue* value);
  static BasicType basic_type(oop box);
  static bool is_instance(oop box)                 { return basic_type(box) != T_ILLEGAL; }
  static bool is_instance(oop box, BasicType type) { return basic_type(box) == type; }
  static void print(oop box, outputStream* st)     { jvalue value;  print(get_value(box, &value), &value, st); }
  static void print(BasicType type, jvalue* value, outputStream* st);

  static int value_offset_in_bytes(BasicType type) {
    return ( type == T_LONG || type == T_DOUBLE ) ? long_value_offset :
                                                    value_offset;
  }

  // Debugging
  friend class JavaClasses;
};



// Interface to java.lang.ref.Reference objects

class java_lang_ref_Reference: AllStatic {
 public:
  enum {
   hc_referent_offset   = 0,
   hc_queue_offset      = 1,
   hc_next_offset       = 2,
   hc_discovered_offset = 3  // Is not last, see SoftRefs.
  };
  enum {
   hc_static_lock_offset    = 0,
   hc_static_pending_offset = 1
  };

  static int referent_offset;
  static int queue_offset;
  static int next_offset;
  static int discovered_offset;
  static int static_lock_offset;
  static int static_pending_offset;
  static int number_of_fake_oop_fields;

  // Accessors
  static oop referent(oop ref) {
    return ref->obj_field(referent_offset);
  }
  static void set_referent(oop ref, oop value) {
    ref->obj_field_put(referent_offset, value);
  }
  static void set_referent_raw(oop ref, oop value) {
    ref->obj_field_put_raw(referent_offset, value);
  }
  static HeapWord* referent_addr(oop ref) {
    return ref->obj_field_addr<HeapWord>(referent_offset);
  }
  static oop next(oop ref) {
    return ref->obj_field(next_offset);
  }
  static void set_next(oop ref, oop value) {
    ref->obj_field_put(next_offset, value);
  }
  static void set_next_raw(oop ref, oop value) {
    ref->obj_field_put_raw(next_offset, value);
  }
  static HeapWord* next_addr(oop ref) {
    return ref->obj_field_addr<HeapWord>(next_offset);
  }
  static oop discovered(oop ref) {
    return ref->obj_field(discovered_offset);
  }
  static void set_discovered(oop ref, oop value) {
    ref->obj_field_put(discovered_offset, value);
  }
  static void set_discovered_raw(oop ref, oop value) {
    ref->obj_field_put_raw(discovered_offset, value);
  }
  static HeapWord* discovered_addr(oop ref) {
    return ref->obj_field_addr<HeapWord>(discovered_offset);
  }
  // Accessors for statics
  static oop  pending_list_lock();
  static oop  pending_list();

  static HeapWord*  pending_list_lock_addr();
  static HeapWord*  pending_list_addr();
};


// Interface to java.lang.ref.SoftReference objects

class java_lang_ref_SoftReference: public java_lang_ref_Reference {
 public:
  enum {
   // The timestamp is a long field and may need to be adjusted for alignment.
   hc_timestamp_offset  = hc_discovered_offset + 1
  };
  enum {
   hc_static_clock_offset = 0
  };

  static int timestamp_offset;
  static int static_clock_offset;

  // Accessors
  static jlong timestamp(oop ref);

  // Accessors for statics
  static jlong clock();
  static void set_clock(jlong value);
};


// Interface to java.lang.invoke.MethodHandle objects

class MethodHandleEntry;

class java_lang_invoke_MethodHandle: AllStatic {
  friend class JavaClasses;

 private:
  static int _type_offset;               // the MethodType of this MH
  static int _form_offset;               // the LambdaForm of this MH

  static void compute_offsets();

 public:
  // Accessors
  static oop            type(oop mh);
  static void       set_type(oop mh, oop mtype);

  static oop            form(oop mh);
  static void       set_form(oop mh, oop lform);

  // Testers
  static bool is_subclass(Klass* klass) {
    return klass->is_subclass_of(SystemDictionary::MethodHandle_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Accessors for code generation:
  static int type_offset_in_bytes()             { return _type_offset; }
  static int form_offset_in_bytes()             { return _form_offset; }
};

// Interface to java.lang.invoke.LambdaForm objects
// (These are a private interface for managing adapter code generation.)

class java_lang_invoke_LambdaForm: AllStatic {
  friend class JavaClasses;

 private:
  static int _vmentry_offset;  // type is MemberName

  static void compute_offsets();

 public:
  // Accessors
  static oop            vmentry(oop lform);
  static void       set_vmentry(oop lform, oop invoker);

  // Testers
  static bool is_subclass(Klass* klass) {
    return SystemDictionary::LambdaForm_klass() != NULL &&
      klass->is_subclass_of(SystemDictionary::LambdaForm_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Accessors for code generation:
  static int vmentry_offset_in_bytes()          { return _vmentry_offset; }
};


// Interface to java.lang.invoke.MemberName objects
// (These are a private interface for Java code to query the class hierarchy.)

#define MEMBERNAME_INJECTED_FIELDS(macro)                               \
  macro(java_lang_invoke_MemberName, vmloader, object_signature, false) \
  macro(java_lang_invoke_MemberName, vmindex,  intptr_signature, false) \
  macro(java_lang_invoke_MemberName, vmtarget, intptr_signature, false)

class java_lang_invoke_MemberName: AllStatic {
  friend class JavaClasses;

 private:
  // From java.lang.invoke.MemberName:
  //    private Class<?>   clazz;       // class in which the method is defined
  //    private String     name;        // may be null if not yet materialized
  //    private Object     type;        // may be null if not yet materialized
  //    private int        flags;       // modifier bits; see reflect.Modifier
  //    private intptr     vmtarget;    // VM-specific target value
  //    private intptr_t   vmindex;     // member index within class or interface
  static int _clazz_offset;
  static int _name_offset;
  static int _type_offset;
  static int _flags_offset;
  static int _vmtarget_offset;
  static int _vmloader_offset;
  static int _vmindex_offset;

  static void compute_offsets();

 public:
  // Accessors
  static oop            clazz(oop mname);
  static void       set_clazz(oop mname, oop clazz);

  static oop            type(oop mname);
  static void       set_type(oop mname, oop type);

  static oop            name(oop mname);
  static void       set_name(oop mname, oop name);

  static int            flags(oop mname);
  static void       set_flags(oop mname, int flags);

  static Metadata*      vmtarget(oop mname);
  static void       set_vmtarget(oop mname, Metadata* target);
#if INCLUDE_JVMTI
  static void       adjust_vmtarget(oop mname, Metadata* target);
#endif // INCLUDE_JVMTI

  static intptr_t       vmindex(oop mname);
  static void       set_vmindex(oop mname, intptr_t index);

  // Testers
  static bool is_subclass(Klass* klass) {
    return klass->is_subclass_of(SystemDictionary::MemberName_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Relevant integer codes (keep these in synch. with MethodHandleNatives.Constants):
  enum {
    MN_IS_METHOD            = 0x00010000, // method (not constructor)
    MN_IS_CONSTRUCTOR       = 0x00020000, // constructor
    MN_IS_FIELD             = 0x00040000, // field
    MN_IS_TYPE              = 0x00080000, // nested type
    MN_CALLER_SENSITIVE     = 0x00100000, // @CallerSensitive annotation detected
    MN_REFERENCE_KIND_SHIFT = 24, // refKind
    MN_REFERENCE_KIND_MASK  = 0x0F000000 >> MN_REFERENCE_KIND_SHIFT,
    // The SEARCH_* bits are not for MN.flags but for the matchFlags argument of MHN.getMembers:
    MN_SEARCH_SUPERCLASSES  = 0x00100000, // walk super classes
    MN_SEARCH_INTERFACES    = 0x00200000  // walk implemented interfaces
  };

  // Accessors for code generation:
  static int clazz_offset_in_bytes()            { return _clazz_offset; }
  static int type_offset_in_bytes()             { return _type_offset; }
  static int name_offset_in_bytes()             { return _name_offset; }
  static int flags_offset_in_bytes()            { return _flags_offset; }
  static int vmtarget_offset_in_bytes()         { return _vmtarget_offset; }
  static int vmindex_offset_in_bytes()          { return _vmindex_offset; }
};


// Interface to java.lang.invoke.MethodType objects

class java_lang_invoke_MethodType: AllStatic {
  friend class JavaClasses;

 private:
  static int _rtype_offset;
  static int _ptypes_offset;

  static void compute_offsets();

 public:
  // Accessors
  static oop            rtype(oop mt);
  static objArrayOop    ptypes(oop mt);

  static oop            ptype(oop mt, int index);
  static int            ptype_count(oop mt);

  static int            ptype_slot_count(oop mt);  // extra counts for long/double
  static int            rtype_slot_count(oop mt);  // extra counts for long/double

  static Symbol*        as_signature(oop mt, bool intern_if_not_found, TRAPS);
  static void           print_signature(oop mt, outputStream* st);

  static bool is_instance(oop obj) {
    return obj != NULL && obj->klass() == SystemDictionary::MethodType_klass();
  }

  static bool equals(oop mt1, oop mt2);

  // Accessors for code generation:
  static int rtype_offset_in_bytes()            { return _rtype_offset; }
  static int ptypes_offset_in_bytes()           { return _ptypes_offset; }
};


// Interface to java.lang.invoke.CallSite objects

class java_lang_invoke_CallSite: AllStatic {
  friend class JavaClasses;

private:
  static int _target_offset;

  static void compute_offsets();

public:
  // Accessors
  static oop              target(         oop site)             { return site->obj_field(             _target_offset);         }
  static void         set_target(         oop site, oop target) {        site->obj_field_put(         _target_offset, target); }

  static volatile oop     target_volatile(oop site)             { return site->obj_field_volatile(    _target_offset);         }
  static void         set_target_volatile(oop site, oop target) {        site->obj_field_put_volatile(_target_offset, target); }

  // Testers
  static bool is_subclass(Klass* klass) {
    return klass->is_subclass_of(SystemDictionary::CallSite_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Accessors for code generation:
  static int target_offset_in_bytes()           { return _target_offset; }
};


// Interface to java.security.AccessControlContext objects

class java_security_AccessControlContext: AllStatic {
 private:
  // Note that for this class the layout changed between JDK1.2 and JDK1.3,
  // so we compute the offsets at startup rather than hard-wiring them.
  static int _context_offset;
  static int _privilegedContext_offset;
  static int _isPrivileged_offset;
  static int _isAuthorized_offset;

  static void compute_offsets();
 public:
  static oop create(objArrayHandle context, bool isPrivileged, Handle privileged_context, TRAPS);

  static bool is_authorized(Handle context);

  // Debugging/initialization
  friend class JavaClasses;
};


// Interface to java.lang.ClassLoader objects

#define CLASSLOADER_INJECTED_FIELDS(macro)                            \
  macro(java_lang_ClassLoader, loader_data,  intptr_signature, false)

class java_lang_ClassLoader : AllStatic {
 private:
  // The fake offsets are added by the class loader when java.lang.Class is loaded
  enum {
   hc_parent_offset = 0
  };
  static int _loader_data_offset;
  static bool offsets_computed;
  static int parent_offset;
  static int parallelCapable_offset;

 public:
  static void compute_offsets();

  static ClassLoaderData** loader_data_addr(oop loader);
  static ClassLoaderData* loader_data(oop loader);

  static oop parent(oop loader);
  static bool isAncestor(oop loader, oop cl);

  // Support for parallelCapable field
  static bool parallelCapable(oop the_class_mirror);

  static bool is_trusted_loader(oop loader);

  // Fix for 4474172
  static oop  non_reflection_class_loader(oop loader);

  // Testers
  static bool is_subclass(Klass* klass) {
    return klass->is_subclass_of(SystemDictionary::ClassLoader_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Debugging
  friend class JavaClasses;
  friend class ClassFileParser; // access to number_of_fake_fields
};


// Interface to java.lang.System objects

class java_lang_System : AllStatic {
 private:
  enum {
   hc_static_in_offset  = 0,
   hc_static_out_offset = 1,
   hc_static_err_offset = 2,
   hc_static_security_offset = 3
  };

  static int  static_in_offset;
  static int static_out_offset;
  static int static_err_offset;
  static int static_security_offset;

 public:
  static int  in_offset_in_bytes();
  static int out_offset_in_bytes();
  static int err_offset_in_bytes();

  static bool has_security_manager();

  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.StackTraceElement objects

class java_lang_StackTraceElement: AllStatic {
 private:
  enum {
    hc_declaringClass_offset  = 0,
    hc_methodName_offset = 1,
    hc_fileName_offset   = 2,
    hc_lineNumber_offset = 3
  };

  static int declaringClass_offset;
  static int methodName_offset;
  static int fileName_offset;
  static int lineNumber_offset;

 public:
  // Setters
  static void set_declaringClass(oop element, oop value);
  static void set_methodName(oop element, oop value);
  static void set_fileName(oop element, oop value);
  static void set_lineNumber(oop element, int value);

  // Create an instance of StackTraceElement
  static oop create(Handle mirror, int method, int version, int bci, TRAPS);
  static oop create(methodHandle method, int bci, TRAPS);

  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.AssertionStatusDirectives objects

class java_lang_AssertionStatusDirectives: AllStatic {
 private:
  enum {
    hc_classes_offset,
    hc_classEnabled_offset,
    hc_packages_offset,
    hc_packageEnabled_offset,
    hc_deflt_offset
  };

  static int classes_offset;
  static int classEnabled_offset;
  static int packages_offset;
  static int packageEnabled_offset;
  static int deflt_offset;

 public:
  // Setters
  static void set_classes(oop obj, oop val);
  static void set_classEnabled(oop obj, oop val);
  static void set_packages(oop obj, oop val);
  static void set_packageEnabled(oop obj, oop val);
  static void set_deflt(oop obj, bool val);
  // Debugging
  friend class JavaClasses;
};


class java_nio_Buffer: AllStatic {
 private:
  static int _limit_offset;

 public:
  static int  limit_offset();
  static void compute_offsets();
};

class java_util_concurrent_locks_AbstractOwnableSynchronizer : AllStatic {
 private:
  static int  _owner_offset;
 public:
  static void initialize(TRAPS);
  static oop  get_owner_threadObj(oop obj);
};

// Use to declare fields that need to be injected into Java classes
// for the JVM to use.  The name_index and signature_index are
// declared in vmSymbols.  The may_be_java flag is used to declare
// fields that might already exist in Java but should be injected if
// they don't.  Otherwise the field is unconditionally injected and
// the JVM uses the injected one.  This is to ensure that name
// collisions don't occur.  In general may_be_java should be false
// unless there's a good reason.

class InjectedField {
 public:
  const SystemDictionary::WKID klass_id;
  const vmSymbols::SID name_index;
  const vmSymbols::SID signature_index;
  const bool           may_be_java;


  Klass* klass() const    { return SystemDictionary::well_known_klass(klass_id); }
  Symbol* name() const      { return lookup_symbol(name_index); }
  Symbol* signature() const { return lookup_symbol(signature_index); }

  int compute_offset();

  // Find the Symbol for this index
  static Symbol* lookup_symbol(int symbol_index) {
    return vmSymbols::symbol_at((vmSymbols::SID)symbol_index);
  }
};

#define DECLARE_INJECTED_FIELD_ENUM(klass, name, signature, may_be_java) \
  klass##_##name##_enum,

#define ALL_INJECTED_FIELDS(macro)          \
  CLASS_INJECTED_FIELDS(macro)              \
  CLASSLOADER_INJECTED_FIELDS(macro)        \
  MEMBERNAME_INJECTED_FIELDS(macro)

// Interface to hard-coded offset checking

class JavaClasses : AllStatic {
 private:

  static InjectedField _injected_fields[];

  static bool check_offset(const char *klass_name, int offset, const char *field_name, const char* field_sig) PRODUCT_RETURN0;
  static bool check_static_offset(const char *klass_name, int hardcoded_offset, const char *field_name, const char* field_sig) PRODUCT_RETURN0;
  static bool check_constant(const char *klass_name, int constant, const char *field_name, const char* field_sig) PRODUCT_RETURN0;

 public:
  enum InjectedFieldID {
    ALL_INJECTED_FIELDS(DECLARE_INJECTED_FIELD_ENUM)
    MAX_enum
  };

  static int compute_injected_offset(InjectedFieldID id);

  static void compute_hard_coded_offsets();
  static void compute_offsets();
  static void check_offsets() PRODUCT_RETURN;

  static InjectedField* get_injected(Symbol* class_name, int* field_count);
};

#undef DECLARE_INJECTED_FIELD_ENUM

#endif // SHARE_VM_CLASSFILE_JAVACLASSES_HPP
