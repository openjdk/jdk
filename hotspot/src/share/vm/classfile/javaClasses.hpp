/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

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
  enum {
    hc_value_offset  = 0,
    hc_offset_offset = 1
    //hc_count_offset = 2  -- not a word-scaled offset
    //hc_hash_offset  = 3  -- not a word-scaled offset
  };

  static int value_offset;
  static int offset_offset;
  static int count_offset;
  static int hash_offset;

  static Handle basic_create(int length, bool tenured, TRAPS);
  static Handle basic_create_from_unicode(jchar* unicode, int length, bool tenured, TRAPS);

  static void set_value( oop string, typeArrayOop buffer) { string->obj_field_put(value_offset,  (oop)buffer); }
  static void set_offset(oop string, int offset)          { string->int_field_put(offset_offset, offset); }
  static void set_count( oop string, int count)           { string->int_field_put(count_offset,  count);  }

 public:
  // Instance creation
  static Handle create_from_unicode(jchar* unicode, int len, TRAPS);
  static Handle create_tenured_from_unicode(jchar* unicode, int len, TRAPS);
  static oop    create_oop_from_unicode(jchar* unicode, int len, TRAPS);
  static Handle create_from_str(const char* utf8_str, TRAPS);
  static oop    create_oop_from_str(const char* utf8_str, TRAPS);
  static Handle create_from_symbol(symbolHandle symbol, TRAPS);
  static Handle create_from_platform_dependent_str(const char* str, TRAPS);
  static Handle char_converter(Handle java_string, jchar from_char, jchar to_char, TRAPS);

  static int value_offset_in_bytes()  { return value_offset;  }
  static int count_offset_in_bytes()  { return count_offset;  }
  static int offset_offset_in_bytes() { return offset_offset; }
  static int hash_offset_in_bytes()   { return hash_offset;   }

  // Accessors
  static typeArrayOop value(oop java_string) {
    assert(is_instance(java_string), "must be java_string");
    return (typeArrayOop) java_string->obj_field(value_offset);
  }
  static int offset(oop java_string) {
    assert(is_instance(java_string), "must be java_string");
    return java_string->int_field(offset_offset);
  }
  static int length(oop java_string) {
    assert(is_instance(java_string), "must be java_string");
    return java_string->int_field(count_offset);
  }
  static int utf8_length(oop java_string);

  // String converters
  static char*  as_utf8_string(oop java_string);
  static char*  as_utf8_string(oop java_string, int start, int len);
  static char*  as_platform_dependent_str(Handle java_string, TRAPS);
  static jchar* as_unicode_string(oop java_string, int& length);

  static bool equals(oop java_string, jchar* chars, int len);

  // Conversion between '.' and '/' formats
  static Handle externalize_classname(Handle java_string, TRAPS) { return char_converter(java_string, '/', '.', THREAD); }
  static Handle internalize_classname(Handle java_string, TRAPS) { return char_converter(java_string, '.', '/', THREAD); }

  // Conversion
  static symbolHandle as_symbol(Handle java_string, TRAPS);
  static symbolOop as_symbol_or_null(oop java_string);

  // Testers
  static bool is_instance(oop obj) {
    return obj != NULL && obj->klass() == SystemDictionary::String_klass();
  }

  // Debugging
  static void print(Handle java_string, outputStream* st);
  friend class JavaClasses;
};


// Interface to java.lang.Class objects

class java_lang_Class : AllStatic {
   friend class VMStructs;
 private:
  // The fake offsets are added by the class loader when java.lang.Class is loaded

  enum {
    hc_klass_offset                = 0,
    hc_array_klass_offset          = 1,
    hc_resolved_constructor_offset = 2,
    hc_number_of_fake_oop_fields   = 3
  };

  static int klass_offset;
  static int resolved_constructor_offset;
  static int array_klass_offset;
  static int number_of_fake_oop_fields;

  static void compute_offsets();
  static bool offsets_computed;
  static int classRedefinedCount_offset;
  static int parallelCapable_offset;

 public:
  // Instance creation
  static oop  create_mirror(KlassHandle k, TRAPS);
  static oop  create_basic_type_mirror(const char* basic_type_name, BasicType type, TRAPS);
  // Conversion
  static klassOop as_klassOop(oop java_class);
  static BasicType as_BasicType(oop java_class, klassOop* reference_klass = NULL);
  static BasicType as_BasicType(oop java_class, KlassHandle* reference_klass) {
    klassOop refk_oop = NULL;
    BasicType result = as_BasicType(java_class, &refk_oop);
    (*reference_klass) = KlassHandle(refk_oop);
    return result;
  }
  static symbolOop as_signature(oop java_class, bool intern_if_not_found, TRAPS);
  static void print_signature(oop java_class, outputStream *st);
  // Testing
  static bool is_instance(oop obj) {
    return obj != NULL && obj->klass() == SystemDictionary::Class_klass();
  }
  static bool is_primitive(oop java_class);
  static BasicType primitive_type(oop java_class);
  static oop primitive_mirror(BasicType t);
  // JVM_NewInstance support
  static methodOop resolved_constructor(oop java_class);
  static void set_resolved_constructor(oop java_class, methodOop constructor);
  // JVM_NewArray support
  static klassOop array_klass(oop java_class);
  static void set_array_klass(oop java_class, klassOop klass);
  // compiler support for class operations
  static int klass_offset_in_bytes() { return klass_offset; }
  static int resolved_constructor_offset_in_bytes() { return resolved_constructor_offset; }
  static int array_klass_offset_in_bytes() { return array_klass_offset; }
  // Support for classRedefinedCount field
  static int classRedefinedCount(oop the_class_mirror);
  static void set_classRedefinedCount(oop the_class_mirror, int value);
  // Support for parallelCapable field
  static bool parallelCapable(oop the_class_mirror);
  // Debugging
  friend class JavaClasses;
  friend class instanceKlass;   // verification code accesses offsets
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
  // Trace constants
  enum {
    trace_methods_offset = 0,
    trace_bcis_offset    = 1,
    trace_next_offset    = 2,
    trace_size           = 3,
    trace_chunk_size     = 32
  };

  static int backtrace_offset;
  static int detailMessage_offset;
  static int cause_offset;
  static int stackTrace_offset;

  // Printing
  static char* print_stack_element_to_buffer(methodOop method, int bci);
  static void print_to_stream(Handle stream, const char* str);
  // StackTrace (programmatic access, new since 1.4)
  static void clear_stacktrace(oop throwable);
  // No stack trace available
  static const char* no_stack_trace_message();

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
  // Print stack trace stored in exception by call-back to Java
  // Note: this is no longer used in Merlin, but we still suppport
  // it for compatibility.
  static void print_stack_trace(oop throwable, oop print_stream);
  static void print_stack_element(Handle stream, methodOop method, int bci);
  static void print_stack_element(outputStream *st, methodOop method, int bci);
  static void print_stack_usage(Handle stream);

  // Allocate space for backtrace (created but stack trace not filled in)
  static void allocate_backtrace(Handle throwable, TRAPS);
  // Fill in current stack trace for throwable with preallocated backtrace (no GC)
  static void fill_in_stack_trace_of_preallocated_backtrace(Handle throwable);

  // Fill in current stack trace, can cause GC
  static void fill_in_stack_trace(Handle throwable, TRAPS);
  static void fill_in_stack_trace(Handle throwable);
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

  // Debugging
  friend class JavaClasses;
};

// Interface to sun.reflect.ConstantPool objects
class sun_reflect_ConstantPool {
 private:
  // Note that to reduce dependencies on the JDK we compute these
  // offsets at run-time.
  static int _cp_oop_offset;

  static void compute_offsets();

 public:
  // Allocation
  static Handle create(TRAPS);

  // Accessors
  static oop cp_oop(oop reflect);
  static void set_cp_oop(oop reflect, oop value);
  static int cp_oop_offset() {
    return _cp_oop_offset;
  }

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
    ref->obj_field_raw_put(referent_offset, value);
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
    ref->obj_field_raw_put(next_offset, value);
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
    ref->obj_field_raw_put(discovered_offset, value);
  }
  static HeapWord* discovered_addr(oop ref) {
    return ref->obj_field_addr<HeapWord>(discovered_offset);
  }
  // Accessors for statics
  static oop  pending_list_lock();
  static oop  pending_list();

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


// Interface to java.dyn.MethodHandle objects

class MethodHandleEntry;

class java_dyn_MethodHandle: AllStatic {
  friend class JavaClasses;

 private:
  static int _vmentry_offset;           // assembly code trampoline for MH
  static int _vmtarget_offset;          // class-specific target reference
  static int _type_offset;              // the MethodType of this MH
  static int _vmslots_offset;           // OPTIONAL hoisted type.form.vmslots

  static void compute_offsets();

 public:
  // Accessors
  static oop            type(oop mh);
  static void       set_type(oop mh, oop mtype);

  static oop            vmtarget(oop mh);
  static void       set_vmtarget(oop mh, oop target);

  static MethodHandleEntry* vmentry(oop mh);
  static void       set_vmentry(oop mh, MethodHandleEntry* data);

  static int            vmslots(oop mh);
  static void      init_vmslots(oop mh);
  static int    compute_vmslots(oop mh);

  // Testers
  static bool is_subclass(klassOop klass) {
    return Klass::cast(klass)->is_subclass_of(SystemDictionary::MethodHandle_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Accessors for code generation:
  static int type_offset_in_bytes()             { return _type_offset; }
  static int vmtarget_offset_in_bytes()         { return _vmtarget_offset; }
  static int vmentry_offset_in_bytes()          { return _vmentry_offset; }
  static int vmslots_offset_in_bytes()          { return _vmslots_offset; }
};

class sun_dyn_DirectMethodHandle: public java_dyn_MethodHandle {
  friend class JavaClasses;

 private:
  //         _vmtarget_offset;          // method   or class      or interface
  static int _vmindex_offset;           // negative or vtable idx or itable idx
  static void compute_offsets();

 public:
  // Accessors
  static int            vmindex(oop mh);
  static void       set_vmindex(oop mh, int index);

  // Testers
  static bool is_subclass(klassOop klass) {
    return Klass::cast(klass)->is_subclass_of(SystemDictionary::DirectMethodHandle_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Accessors for code generation:
  static int vmindex_offset_in_bytes()          { return _vmindex_offset; }
};

class sun_dyn_BoundMethodHandle: public java_dyn_MethodHandle {
  friend class JavaClasses;

 private:
  static int _argument_offset;          // argument value bound into this MH
  static int _vmargslot_offset;         // relevant argument slot (<= vmslots)
  static void compute_offsets();

public:
  static oop            argument(oop mh);
  static void       set_argument(oop mh, oop ref);

  static jint           vmargslot(oop mh);
  static void       set_vmargslot(oop mh, jint slot);

  // Testers
  static bool is_subclass(klassOop klass) {
    return Klass::cast(klass)->is_subclass_of(SystemDictionary::BoundMethodHandle_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  static int argument_offset_in_bytes()         { return _argument_offset; }
  static int vmargslot_offset_in_bytes()        { return _vmargslot_offset; }
};

class sun_dyn_AdapterMethodHandle: public sun_dyn_BoundMethodHandle {
  friend class JavaClasses;

 private:
  static int _conversion_offset;        // type of conversion to apply
  static void compute_offsets();

 public:
  static int            conversion(oop mh);
  static void       set_conversion(oop mh, int conv);

  // Testers
  static bool is_subclass(klassOop klass) {
    return Klass::cast(klass)->is_subclass_of(SystemDictionary::AdapterMethodHandle_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Relevant integer codes (keep these in synch. with MethodHandleNatives.Constants):
  enum {
    OP_RETYPE_ONLY   = 0x0, // no argument changes; straight retype
    OP_RETYPE_RAW    = 0x1, // straight retype, trusted (void->int, Object->T)
    OP_CHECK_CAST    = 0x2, // ref-to-ref conversion; requires a Class argument
    OP_PRIM_TO_PRIM  = 0x3, // converts from one primitive to another
    OP_REF_TO_PRIM   = 0x4, // unboxes a wrapper to produce a primitive
    OP_PRIM_TO_REF   = 0x5, // boxes a primitive into a wrapper (NYI)
    OP_SWAP_ARGS     = 0x6, // swap arguments (vminfo is 2nd arg)
    OP_ROT_ARGS      = 0x7, // rotate arguments (vminfo is displaced arg)
    OP_DUP_ARGS      = 0x8, // duplicates one or more arguments (at TOS)
    OP_DROP_ARGS     = 0x9, // remove one or more argument slots
    OP_COLLECT_ARGS  = 0xA, // combine one or more arguments into a varargs (NYI)
    OP_SPREAD_ARGS   = 0xB, // expand in place a varargs array (of known size)
    OP_FLYBY         = 0xC, // operate first on reified argument list (NYI)
    OP_RICOCHET      = 0xD, // run an adapter chain on the return value (NYI)
    CONV_OP_LIMIT    = 0xE, // limit of CONV_OP enumeration

    CONV_OP_MASK     = 0xF00, // this nybble contains the conversion op field
    CONV_VMINFO_MASK = 0x0FF, // LSB is reserved for JVM use
    CONV_VMINFO_SHIFT     =  0, // position of bits in CONV_VMINFO_MASK
    CONV_OP_SHIFT         =  8, // position of bits in CONV_OP_MASK
    CONV_DEST_TYPE_SHIFT  = 12, // byte 2 has the adapter BasicType (if needed)
    CONV_SRC_TYPE_SHIFT   = 16, // byte 2 has the source BasicType (if needed)
    CONV_STACK_MOVE_SHIFT = 20, // high 12 bits give signed SP change
    CONV_STACK_MOVE_MASK  = (1 << (32 - CONV_STACK_MOVE_SHIFT)) - 1
  };

  static int conversion_offset_in_bytes()       { return _conversion_offset; }
};


// Interface to sun.dyn.MemberName objects
// (These are a private interface for Java code to query the class hierarchy.)

class sun_dyn_MemberName: AllStatic {
  friend class JavaClasses;

 private:
  // From java.dyn.MemberName:
  //    private Class<?>   clazz;       // class in which the method is defined
  //    private String     name;        // may be null if not yet materialized
  //    private Object     type;        // may be null if not yet materialized
  //    private int        flags;       // modifier bits; see reflect.Modifier
  //    private Object     vmtarget;    // VM-specific target value
  //    private int        vmindex;     // method index within class or interface
  static int _clazz_offset;
  static int _name_offset;
  static int _type_offset;
  static int _flags_offset;
  static int _vmtarget_offset;
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

  static int            modifiers(oop mname) { return (u2) flags(mname); }
  static void       set_modifiers(oop mname, int mods)
                                { set_flags(mname, (flags(mname) &~ (u2)-1) | (u2)mods); }

  static oop            vmtarget(oop mname);
  static void       set_vmtarget(oop mname, oop target);

  static int            vmindex(oop mname);
  static void       set_vmindex(oop mname, int index);

  // Testers
  static bool is_subclass(klassOop klass) {
    return Klass::cast(klass)->is_subclass_of(SystemDictionary::MemberName_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Relevant integer codes (keep these in synch. with MethodHandleNatives.Constants):
  enum {
    MN_IS_METHOD           = 0x00010000, // method (not constructor)
    MN_IS_CONSTRUCTOR      = 0x00020000, // constructor
    MN_IS_FIELD            = 0x00040000, // field
    MN_IS_TYPE             = 0x00080000, // nested type
    MN_SEARCH_SUPERCLASSES = 0x00100000, // for MHN.getMembers
    MN_SEARCH_INTERFACES   = 0x00200000, // for MHN.getMembers
    VM_INDEX_UNINITIALIZED = -99
  };

  // Accessors for code generation:
  static int clazz_offset_in_bytes()            { return _clazz_offset; }
  static int type_offset_in_bytes()             { return _type_offset; }
  static int name_offset_in_bytes()             { return _name_offset; }
  static int flags_offset_in_bytes()            { return _flags_offset; }
  static int vmtarget_offset_in_bytes()         { return _vmtarget_offset; }
  static int vmindex_offset_in_bytes()          { return _vmindex_offset; }
};


// Interface to java.dyn.MethodType objects

class java_dyn_MethodType: AllStatic {
  friend class JavaClasses;

 private:
  static int _rtype_offset;
  static int _ptypes_offset;
  static int _form_offset;

  static void compute_offsets();

 public:
  // Accessors
  static oop            rtype(oop mt);
  static objArrayOop    ptypes(oop mt);
  static oop            form(oop mt);

  static oop            ptype(oop mt, int index);
  static int            ptype_count(oop mt);

  static symbolOop      as_signature(oop mt, bool intern_if_not_found, TRAPS);
  static void           print_signature(oop mt, outputStream* st);

  static bool is_instance(oop obj) {
    return obj != NULL && obj->klass() == SystemDictionary::MethodType_klass();
  }

  // Accessors for code generation:
  static int rtype_offset_in_bytes()            { return _rtype_offset; }
  static int ptypes_offset_in_bytes()           { return _ptypes_offset; }
  static int form_offset_in_bytes()             { return _form_offset; }
};

class java_dyn_MethodTypeForm: AllStatic {
  friend class JavaClasses;

 private:
  static int _vmslots_offset;           // number of argument slots needed
  static int _erasedType_offset;        // erasedType = canonical MethodType

  static void compute_offsets();

 public:
  // Accessors
  static int            vmslots(oop mtform);
  static oop            erasedType(oop mtform);

  // Accessors for code generation:
  static int vmslots_offset_in_bytes()          { return _vmslots_offset; }
  static int erasedType_offset_in_bytes()       { return _erasedType_offset; }
};


// Interface to java.dyn.CallSite objects

class java_dyn_CallSite: AllStatic {
  friend class JavaClasses;

private:
  static int _target_offset;
  static int _caller_method_offset;
  static int _caller_bci_offset;

  static void compute_offsets();

public:
  // Accessors
  static oop            target(oop site);
  static void       set_target(oop site, oop target);

  static oop            caller_method(oop site);
  static void       set_caller_method(oop site, oop ref);

  static jint           caller_bci(oop site);
  static void       set_caller_bci(oop site, jint bci);

  // Testers
  static bool is_subclass(klassOop klass) {
    return Klass::cast(klass)->is_subclass_of(SystemDictionary::CallSite_klass());
  }
  static bool is_instance(oop obj) {
    return obj != NULL && is_subclass(obj->klass());
  }

  // Accessors for code generation:
  static int target_offset_in_bytes()           { return _target_offset; }
  static int caller_method_offset_in_bytes()    { return _caller_method_offset; }
  static int caller_bci_offset_in_bytes()       { return _caller_bci_offset; }
};


// Interface to java.security.AccessControlContext objects

class java_security_AccessControlContext: AllStatic {
 private:
  // Note that for this class the layout changed between JDK1.2 and JDK1.3,
  // so we compute the offsets at startup rather than hard-wiring them.
  static int _context_offset;
  static int _privilegedContext_offset;
  static int _isPrivileged_offset;

  static void compute_offsets();
 public:
  static oop create(objArrayHandle context, bool isPrivileged, Handle privileged_context, TRAPS);

  // Debugging/initialization
  friend class JavaClasses;
};


// Interface to java.lang.ClassLoader objects

class java_lang_ClassLoader : AllStatic {
 private:
  enum {
   hc_parent_offset = 0
  };

  static int parent_offset;

 public:
  static oop parent(oop loader);

  static bool is_trusted_loader(oop loader);

  // Fix for 4474172
  static oop  non_reflection_class_loader(oop loader);

  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.System objects

class java_lang_System : AllStatic {
 private:
  enum {
   hc_static_in_offset  = 0,
   hc_static_out_offset = 1,
   hc_static_err_offset = 2
  };

  static int offset_of_static_fields;
  static int  static_in_offset;
  static int static_out_offset;
  static int static_err_offset;

  static void compute_offsets();

 public:
  static int  in_offset_in_bytes();
  static int out_offset_in_bytes();
  static int err_offset_in_bytes();

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
  static oop create(methodHandle m, int bci, TRAPS);

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

class sun_misc_AtomicLongCSImpl: AllStatic {
 private:
  static int _value_offset;

 public:
  static int  value_offset();
  static void compute_offsets();
};

class java_util_concurrent_locks_AbstractOwnableSynchronizer : AllStatic {
 private:
  static int  _owner_offset;
 public:
  static void initialize(TRAPS);
  static oop  get_owner_threadObj(oop obj);
};

// Interface to hard-coded offset checking

class JavaClasses : AllStatic {
 private:
  static bool check_offset(const char *klass_name, int offset, const char *field_name, const char* field_sig) PRODUCT_RETURN0;
  static bool check_static_offset(const char *klass_name, int hardcoded_offset, const char *field_name, const char* field_sig) PRODUCT_RETURN0;
  static bool check_constant(const char *klass_name, int constant, const char *field_name, const char* field_sig) PRODUCT_RETURN0;
 public:
  static void compute_hard_coded_offsets();
  static void compute_offsets();
  static void check_offsets() PRODUCT_RETURN;
};
