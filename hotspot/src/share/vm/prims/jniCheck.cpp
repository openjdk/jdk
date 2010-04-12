/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_jniCheck.cpp.incl"


// Heap objects are allowed to be directly referenced only in VM code,
// not in native code.

#define ASSERT_OOPS_ALLOWED                                          \
    assert(JavaThread::current()->thread_state() == _thread_in_vm,   \
           "jniCheck examining oops in bad state.")


// Execute the given block of source code with the thread in VM state.
// To do this, transition from the NATIVE state to the VM state, execute
// the code, and transtition back.  The ThreadInVMfromNative constructor
// performs the transition to VM state, its destructor restores the
// NATIVE state.

#define IN_VM(source_code)   {                                         \
    {                                                                  \
      ThreadInVMfromNative __tiv(thr);                                 \
      source_code                                                      \
    }                                                                  \
  }


/*
 * DECLARATIONS
 */

static struct JNINativeInterface_ * unchecked_jni_NativeInterface;


/*
 * MACRO DEFINITIONS
 */

// All JNI checked functions here use JNI_ENTRY_CHECKED() instead of the
// QUICK_ENTRY or LEAF variants found in jni.cpp.  This allows handles
// to be created if a fatal error should occur.

// Check for thread not attached to VM;  need to catch this before
// assertions in the wrapper routines might fire

// Check for env being the one value appropriate for this thread.

#define JNI_ENTRY_CHECKED(result_type, header)                           \
extern "C" {                                                             \
  result_type JNICALL header {                                           \
    JavaThread* thr = (JavaThread*)ThreadLocalStorage::get_thread_slow();\
    if (thr == NULL || !thr->is_Java_thread()) {                         \
      tty->print_cr(fatal_using_jnienv_in_nonjava);                      \
      os::abort(true);                                                   \
    }                                                                    \
    JNIEnv* xenv = thr->jni_environment();                               \
    if (env != xenv) {                                                   \
      NativeReportJNIFatalError(thr, warn_wrong_jnienv);                 \
    }                                                                    \
    __ENTRY(result_type, header, thr)


#define UNCHECKED() (unchecked_jni_NativeInterface)

static const char * warn_wrong_jnienv = "Using JNIEnv in the wrong thread";
static const char * warn_bad_class_descriptor = "JNI FindClass received a bad class descriptor \"%s\".  A correct class descriptor " \
  "has no leading \"L\" or trailing \";\".  Incorrect descriptors will not be accepted in future releases.";
static const char * fatal_using_jnienv_in_nonjava = "FATAL ERROR in native method: Using JNIEnv in non-Java thread";
static const char * warn_other_function_in_critical = "Warning: Calling other JNI functions in the scope of " \
  "Get/ReleasePrimitiveArrayCritical or Get/ReleaseStringCritical";
static const char * fatal_bad_ref_to_jni = "Bad global or local ref passed to JNI";
static const char * fatal_received_null_class = "JNI received a null class";
static const char * fatal_class_not_a_class = "JNI received a class argument that is not a class";
static const char * fatal_class_not_a_throwable_class = "JNI Throw or ThrowNew received a class argument that is not a Throwable or Throwable subclass";
static const char * fatal_wrong_class_or_method = "Wrong object class or methodID passed to JNI call";
static const char * fatal_non_weak_method = "non-weak methodID passed to JNI call";
static const char * fatal_unknown_array_object = "Unknown array object passed to JNI array operations";
static const char * fatal_object_array_expected = "Object array expected but not received for JNI array operation";
static const char * fatal_non_array  = "Non-array passed to JNI array operations";
static const char * fatal_element_type_mismatch = "Array element type mismatch in JNI";
static const char * fatal_should_be_static = "Non-static field ID passed to JNI";
static const char * fatal_wrong_static_field = "Wrong static field ID passed to JNI";
static const char * fatal_static_field_not_found = "Static field not found in JNI get/set field operations";
static const char * fatal_static_field_mismatch = "Field type (static) mismatch in JNI get/set field operations";
static const char * fatal_should_be_nonstatic = "Static field ID passed to JNI";
static const char * fatal_null_object = "Null object passed to JNI";
static const char * fatal_wrong_field = "Wrong field ID passed to JNI";
static const char * fatal_instance_field_not_found = "Instance field not found in JNI get/set field operations";
static const char * fatal_instance_field_mismatch = "Field type (instance) mismatch in JNI get/set field operations";
static const char * fatal_non_string = "JNI string operation received a non-string";


// When in VM state:
static void ReportJNIWarning(JavaThread* thr, const char *msg) {
  tty->print_cr("WARNING in native method: %s", msg);
  thr->print_stack();
}

// When in NATIVE state:
static void NativeReportJNIFatalError(JavaThread* thr, const char *msg) {
  IN_VM(
    ReportJNIFatalError(thr, msg);
  )
}

static void NativeReportJNIWarning(JavaThread* thr, const char *msg) {
  IN_VM(
    ReportJNIWarning(thr, msg);
  )
}




/*
 * SUPPORT FUNCTIONS
 */

static inline void
functionEnterCritical(JavaThread* thr)
{
  if (thr->has_pending_exception()) {
    NativeReportJNIWarning(thr, "JNI call made with exception pending");
  }
}

static inline void
functionEnterCriticalExceptionAllowed(JavaThread* thr)
{
}

static inline void
functionEnter(JavaThread* thr)
{
  if (thr->in_critical()) {
    tty->print_cr(warn_other_function_in_critical);
  }
  if (thr->has_pending_exception()) {
    NativeReportJNIWarning(thr, "JNI call made with exception pending");
  }
}

static inline void
functionEnterExceptionAllowed(JavaThread* thr)
{
  if (thr->in_critical()) {
    tty->print_cr(warn_other_function_in_critical);
  }
}

static inline void
functionExit(JNIEnv *env)
{
  /* nothing to do at this time */
}

static inline void
checkStaticFieldID(JavaThread* thr, jfieldID fid, jclass cls, int ftype)
{
  fieldDescriptor fd;

  /* make sure it is a static field */
  if (!jfieldIDWorkaround::is_static_jfieldID(fid))
    ReportJNIFatalError(thr, fatal_should_be_static);

  /* validate the class being passed */
  ASSERT_OOPS_ALLOWED;
  klassOop k_oop = jniCheck::validate_class(thr, cls, false);

  /* check for proper subclass hierarchy */
  JNIid* id = jfieldIDWorkaround::from_static_jfieldID(fid);
  klassOop f_oop = id->holder();
  if (!instanceKlass::cast(k_oop)->is_subtype_of(f_oop))
    ReportJNIFatalError(thr, fatal_wrong_static_field);

  /* check for proper field type */
  if (!instanceKlass::cast(f_oop)->find_local_field_from_offset(
          id->offset(), true, &fd))
    ReportJNIFatalError(thr, fatal_static_field_not_found);
  if ((fd.field_type() != ftype) &&
      !(fd.field_type() == T_ARRAY && ftype == T_OBJECT)) {
    ReportJNIFatalError(thr, fatal_static_field_mismatch);
  }
}

static inline void
checkInstanceFieldID(JavaThread* thr, jfieldID fid, jobject obj, int ftype)
{
  fieldDescriptor fd;

  /* make sure it is an instance field */
  if (jfieldIDWorkaround::is_static_jfieldID(fid))
    ReportJNIFatalError(thr, fatal_should_be_nonstatic);

  /* validate the object being passed and then get its class */
  ASSERT_OOPS_ALLOWED;
  oop oopObj = jniCheck::validate_object(thr, obj);
  if (!oopObj) {
    ReportJNIFatalError(thr, fatal_null_object);
  }
  klassOop k_oop = oopObj->klass();

  if (!jfieldIDWorkaround::is_valid_jfieldID(k_oop, fid)) {
    ReportJNIFatalError(thr, fatal_wrong_field);
  }

  /* make sure the field exists */
  int offset = jfieldIDWorkaround::from_instance_jfieldID(k_oop, fid);
  if (!instanceKlass::cast(k_oop)->contains_field_offset(offset))
    ReportJNIFatalError(thr, fatal_wrong_field);

  /* check for proper field type */
  if (!instanceKlass::cast(k_oop)->find_field_from_offset(offset,
                                                              false, &fd))
    ReportJNIFatalError(thr, fatal_instance_field_not_found);

  if ((fd.field_type() != ftype) &&
      !(fd.field_type() == T_ARRAY && ftype == T_OBJECT)) {
    ReportJNIFatalError(thr, fatal_instance_field_mismatch);
  }
}

static inline void
checkString(JavaThread* thr, jstring js)
{
  ASSERT_OOPS_ALLOWED;
  oop s = jniCheck::validate_object(thr, js);
  if (!s || !java_lang_String::is_instance(s))
    ReportJNIFatalError(thr, fatal_non_string);
}

static inline void
checkArray(JavaThread* thr, jarray jArray, int elementType)
{
  ASSERT_OOPS_ALLOWED;
  arrayOop aOop;

  aOop = (arrayOop)jniCheck::validate_object(thr, jArray);
  if (aOop == NULL || !aOop->is_array())
    ReportJNIFatalError(thr, fatal_non_array);

  if (elementType != -1) {
    if (aOop->is_typeArray()) {
      BasicType array_type = typeArrayKlass::cast(aOop->klass())->element_type();
      if (array_type != elementType)
        ReportJNIFatalError(thr, fatal_element_type_mismatch);
      } else if (aOop->is_objArray()) {
        if ( T_OBJECT != elementType)
          ReportJNIFatalError(thr, fatal_object_array_expected);
      } else {
        ReportJNIFatalError(thr, fatal_unknown_array_object);
    }
  }
}


oop jniCheck::validate_handle(JavaThread* thr, jobject obj) {
  if (JNIHandles::is_frame_handle(thr, obj) ||
      JNIHandles::is_local_handle(thr, obj) ||
      JNIHandles::is_global_handle(obj) ||
      JNIHandles::is_weak_global_handle(obj)) {
    ASSERT_OOPS_ALLOWED;
    return JNIHandles::resolve_external_guard(obj);
  }
  ReportJNIFatalError(thr, fatal_bad_ref_to_jni);
  return NULL;
}


methodOop jniCheck::validate_jmethod_id(JavaThread* thr, jmethodID method_id) {
  ASSERT_OOPS_ALLOWED;
  // do the fast jmethodID check first
  methodOop moop = JNIHandles::checked_resolve_jmethod_id(method_id);
  if (moop == NULL) {
    ReportJNIFatalError(thr, fatal_wrong_class_or_method);
  }
  // jmethodIDs are supposed to be weak global handles, but that
  // can be expensive so check it last
  else if (!JNIHandles::is_weak_global_handle((jobject) method_id)) {
    ReportJNIFatalError(thr, fatal_non_weak_method);
  }
  return moop;
}


oop jniCheck::validate_object(JavaThread* thr, jobject obj) {
    if (!obj)
        return NULL;
    ASSERT_OOPS_ALLOWED;
    oop oopObj = jniCheck::validate_handle(thr, obj);
    if (!oopObj) {
      ReportJNIFatalError(thr, fatal_bad_ref_to_jni);
    }
    return oopObj;
}

// Warn if a class descriptor is in decorated form; class descriptors
// passed to JNI findClass should not be decorated unless they are
// array descriptors.
void jniCheck::validate_class_descriptor(JavaThread* thr, const char* name) {
  if (name == NULL) return;  // implementation accepts NULL so just return

  size_t len = strlen(name);

  if (len >= 2 &&
      name[0] == JVM_SIGNATURE_CLASS &&            // 'L'
      name[len-1] == JVM_SIGNATURE_ENDCLASS ) {    // ';'
    char msg[JVM_MAXPATHLEN];
    jio_snprintf(msg, JVM_MAXPATHLEN, warn_bad_class_descriptor, name);
    ReportJNIWarning(thr, msg);
  }
}

klassOop jniCheck::validate_class(JavaThread* thr, jclass clazz, bool allow_primitive) {
  ASSERT_OOPS_ALLOWED;
  oop mirror = jniCheck::validate_handle(thr, clazz);
  if (!mirror) {
    ReportJNIFatalError(thr, fatal_received_null_class);
  }

  if (mirror->klass() != SystemDictionary::Class_klass()) {
    ReportJNIFatalError(thr, fatal_class_not_a_class);
  }

  klassOop k = java_lang_Class::as_klassOop(mirror);
  // Make allowances for primitive classes ...
  if (!(k != NULL || allow_primitive && java_lang_Class::is_primitive(mirror))) {
    ReportJNIFatalError(thr, fatal_class_not_a_class);
  }
  return k;
}

void jniCheck::validate_throwable_klass(JavaThread* thr, klassOop klass) {
  ASSERT_OOPS_ALLOWED;
  assert(klass != NULL, "klass argument must have a value");

  if (!Klass::cast(klass)->oop_is_instance() ||
      !instanceKlass::cast(klass)->is_subclass_of(SystemDictionary::Throwable_klass())) {
    ReportJNIFatalError(thr, fatal_class_not_a_throwable_class);
  }
}

void jniCheck::validate_call_object(JavaThread* thr, jobject obj, jmethodID method_id) {
  /* validate the object being passed */
  ASSERT_OOPS_ALLOWED;
  jniCheck::validate_jmethod_id(thr, method_id);
  jniCheck::validate_object(thr, obj);
}

void jniCheck::validate_call_class(JavaThread* thr, jclass clazz, jmethodID method_id) {
  /* validate the class being passed */
  ASSERT_OOPS_ALLOWED;
  jniCheck::validate_jmethod_id(thr, method_id);
  jniCheck::validate_class(thr, clazz, false);
}


/*
 * IMPLEMENTATION OF FUNCTIONS IN CHECKED TABLE
 */

JNI_ENTRY_CHECKED(jclass,
  checked_jni_DefineClass(JNIEnv *env,
                          const char *name,
                          jobject loader,
                          const jbyte *buf,
                          jsize len))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_object(thr, loader);
    )
    jclass result = UNCHECKED()->DefineClass(env, name, loader, buf, len);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jclass,
  checked_jni_FindClass(JNIEnv *env,
                        const char *name))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class_descriptor(thr, name);
    )
    jclass result = UNCHECKED()->FindClass(env, name);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jmethodID,
  checked_jni_FromReflectedMethod(JNIEnv *env,
                                  jobject method))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_object(thr, method);
    )
    jmethodID result = UNCHECKED()->FromReflectedMethod(env, method);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jfieldID,
  checked_jni_FromReflectedField(JNIEnv *env,
                                 jobject field))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_object(thr, field);
    )
    jfieldID result = UNCHECKED()->FromReflectedField(env, field);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_ToReflectedMethod(JNIEnv *env,
                                jclass cls,
                                jmethodID methodID,
                                jboolean isStatic))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, cls, false);
      jniCheck::validate_jmethod_id(thr, methodID);
    )
    jobject result = UNCHECKED()->ToReflectedMethod(env, cls, methodID,
                                                    isStatic);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jclass,
  checked_jni_GetSuperclass(JNIEnv *env,
                            jclass sub))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, sub, true);
    )
    jclass result = UNCHECKED()->GetSuperclass(env, sub);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jboolean,
  checked_jni_IsAssignableFrom(JNIEnv *env,
                               jclass sub,
                               jclass sup))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, sub, true);
      jniCheck::validate_class(thr, sup, true);
    )
    jboolean result = UNCHECKED()->IsAssignableFrom(env, sub, sup);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_ToReflectedField(JNIEnv *env,
                               jclass cls,
                               jfieldID fieldID,
                               jboolean isStatic))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, cls, false);
    )
    jobject result = UNCHECKED()->ToReflectedField(env, cls, fieldID,
                                                   isStatic);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_Throw(JNIEnv *env,
                    jthrowable obj))
    functionEnter(thr);
    IN_VM(
      oop oopObj = jniCheck::validate_object(thr, obj);
      if (oopObj == NULL) {
        // Unchecked Throw tolerates a NULL obj, so just warn
        ReportJNIWarning(thr, "JNI Throw called with NULL throwable");
      } else {
        jniCheck::validate_throwable_klass(thr, oopObj->klass());
      }
    )
    jint result = UNCHECKED()->Throw(env, obj);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_ThrowNew(JNIEnv *env,
                       jclass clazz,
                       const char *msg))
    functionEnter(thr);
    IN_VM(
      klassOop k = jniCheck::validate_class(thr, clazz, false);
      assert(k != NULL, "validate_class shouldn't return NULL klassOop");
      jniCheck::validate_throwable_klass(thr, k);
    )
    jint result = UNCHECKED()->ThrowNew(env, clazz, msg);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jthrowable,
  checked_jni_ExceptionOccurred(JNIEnv *env))
    functionEnterExceptionAllowed(thr);
    jthrowable result = UNCHECKED()->ExceptionOccurred(env);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_ExceptionDescribe(JNIEnv *env))
    functionEnterExceptionAllowed(thr);
    UNCHECKED()->ExceptionDescribe(env);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_ExceptionClear(JNIEnv *env))
    functionEnterExceptionAllowed(thr);
    UNCHECKED()->ExceptionClear(env);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_FatalError(JNIEnv *env,
                         const char *msg))
    functionEnter(thr);
    UNCHECKED()->FatalError(env, msg);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_PushLocalFrame(JNIEnv *env,
                             jint capacity))
    functionEnterExceptionAllowed(thr);
    if (capacity < 0)
      NativeReportJNIFatalError(thr, "negative capacity");
    jint result = UNCHECKED()->PushLocalFrame(env, capacity);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_PopLocalFrame(JNIEnv *env,
                            jobject result))
    functionEnterExceptionAllowed(thr);
    jobject res = UNCHECKED()->PopLocalFrame(env, result);
    functionExit(env);
    return res;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_NewGlobalRef(JNIEnv *env,
                           jobject lobj))
    functionEnter(thr);
    IN_VM(
      if (lobj != NULL) {
        jniCheck::validate_handle(thr, lobj);
      }
    )
    jobject result = UNCHECKED()->NewGlobalRef(env,lobj);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_DeleteGlobalRef(JNIEnv *env,
                              jobject gref))
    functionEnterExceptionAllowed(thr);
    IN_VM(
      jniCheck::validate_object(thr, gref);
      if (gref && !JNIHandles::is_global_handle(gref)) {
        ReportJNIFatalError(thr,
            "Invalid global JNI handle passed to DeleteGlobalRef");
      }
    )
    UNCHECKED()->DeleteGlobalRef(env,gref);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_DeleteLocalRef(JNIEnv *env,
                             jobject obj))
    functionEnterExceptionAllowed(thr);
    IN_VM(
      jniCheck::validate_object(thr, obj);
      if (obj && !(JNIHandles::is_local_handle(thr, obj) ||
                   JNIHandles::is_frame_handle(thr, obj)))
        ReportJNIFatalError(thr,
            "Invalid local JNI handle passed to DeleteLocalRef");
    )
    UNCHECKED()->DeleteLocalRef(env, obj);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jboolean,
  checked_jni_IsSameObject(JNIEnv *env,
                           jobject obj1,
                           jobject obj2))
    functionEnterExceptionAllowed(thr);
    IN_VM(
      /* This JNI function can be used to compare weak global references
       * to NULL objects. If the handles are valid, but contain NULL,
       * then don't attempt to validate the object.
       */
      if (obj1 != NULL && jniCheck::validate_handle(thr, obj1) != NULL) {
        jniCheck::validate_object(thr, obj1);
      }
      if (obj2 != NULL && jniCheck::validate_handle(thr, obj2) != NULL) {
        jniCheck::validate_object(thr, obj2);
      }
    )
    jboolean result = UNCHECKED()->IsSameObject(env,obj1,obj2);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_NewLocalRef(JNIEnv *env,
                          jobject ref))
    functionEnter(thr);
    IN_VM(
      if (ref != NULL) {
        jniCheck::validate_handle(thr, ref);
      }
    )
    jobject result = UNCHECKED()->NewLocalRef(env, ref);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_EnsureLocalCapacity(JNIEnv *env,
                                  jint capacity))
    functionEnter(thr);
    if (capacity < 0) {
      NativeReportJNIFatalError(thr, "negative capacity");
    }
    jint result = UNCHECKED()->EnsureLocalCapacity(env, capacity);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_AllocObject(JNIEnv *env,
                          jclass clazz))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
    )
    jobject result = UNCHECKED()->AllocObject(env,clazz);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_NewObject(JNIEnv *env,
                        jclass clazz,
                        jmethodID methodID,
                        ...))
    functionEnter(thr);
    va_list args;
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
      jniCheck::validate_jmethod_id(thr, methodID);
    )
    va_start(args, methodID);
    jobject result = UNCHECKED()->NewObjectV(env,clazz,methodID,args);
    va_end(args);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_NewObjectV(JNIEnv *env,
                         jclass clazz,
                         jmethodID methodID,
                         va_list args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
      jniCheck::validate_jmethod_id(thr, methodID);
    )
    jobject result = UNCHECKED()->NewObjectV(env,clazz,methodID,args);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_NewObjectA(JNIEnv *env,
                         jclass clazz,
                         jmethodID methodID,
                         const jvalue *args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
      jniCheck::validate_jmethod_id(thr, methodID);
    )
    jobject result = UNCHECKED()->NewObjectA(env,clazz,methodID,args);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jclass,
  checked_jni_GetObjectClass(JNIEnv *env,
                             jobject obj))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_object(thr, obj);
    )
    jclass result = UNCHECKED()->GetObjectClass(env,obj);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jboolean,
  checked_jni_IsInstanceOf(JNIEnv *env,
                           jobject obj,
                           jclass clazz))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_object(thr, obj);
      jniCheck::validate_class(thr, clazz, true);
    )
    jboolean result = UNCHECKED()->IsInstanceOf(env,obj,clazz);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jmethodID,
  checked_jni_GetMethodID(JNIEnv *env,
                          jclass clazz,
                          const char *name,
                          const char *sig))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
    )
    jmethodID result = UNCHECKED()->GetMethodID(env,clazz,name,sig);
    functionExit(env);
    return result;
JNI_END

#define WRAPPER_CallMethod(ResultType, Result) \
JNI_ENTRY_CHECKED(ResultType,  \
  checked_jni_Call##Result##Method(JNIEnv *env, \
                                   jobject obj, \
                                   jmethodID methodID, \
                                   ...)) \
    functionEnter(thr); \
    va_list args; \
    IN_VM( \
      jniCheck::validate_call_object(thr, obj, methodID); \
    ) \
    va_start(args,methodID); \
    ResultType result =UNCHECKED()->Call##Result##MethodV(env, obj, methodID, \
                                                          args); \
    va_end(args); \
    functionExit(env); \
    return result; \
JNI_END \
\
JNI_ENTRY_CHECKED(ResultType,  \
  checked_jni_Call##Result##MethodV(JNIEnv *env, \
                                    jobject obj, \
                                    jmethodID methodID, \
                                    va_list args)) \
    functionEnter(thr); \
    IN_VM(\
      jniCheck::validate_call_object(thr, obj, methodID); \
    ) \
    ResultType result = UNCHECKED()->Call##Result##MethodV(env, obj, methodID,\
                                                           args); \
    functionExit(env); \
    return result; \
JNI_END \
\
JNI_ENTRY_CHECKED(ResultType,  \
  checked_jni_Call##Result##MethodA(JNIEnv *env, \
                                    jobject obj, \
                                    jmethodID methodID, \
                                    const jvalue * args)) \
    functionEnter(thr); \
    IN_VM( \
      jniCheck::validate_call_object(thr, obj, methodID); \
    ) \
    ResultType result = UNCHECKED()->Call##Result##MethodA(env, obj, methodID,\
                                                           args); \
    functionExit(env); \
    return result; \
JNI_END

WRAPPER_CallMethod(jobject,Object)
WRAPPER_CallMethod(jboolean,Boolean)
WRAPPER_CallMethod(jbyte,Byte)
WRAPPER_CallMethod(jshort,Short)
WRAPPER_CallMethod(jchar,Char)
WRAPPER_CallMethod(jint,Int)
WRAPPER_CallMethod(jlong,Long)
WRAPPER_CallMethod(jfloat,Float)
WRAPPER_CallMethod(jdouble,Double)

JNI_ENTRY_CHECKED(void,
  checked_jni_CallVoidMethod(JNIEnv *env, \
                             jobject obj, \
                             jmethodID methodID, \
                             ...))
    functionEnter(thr);
    va_list args;
    IN_VM(
      jniCheck::validate_call_object(thr, obj, methodID);
    )
    va_start(args,methodID);
    UNCHECKED()->CallVoidMethodV(env,obj,methodID,args);
    va_end(args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_CallVoidMethodV(JNIEnv *env,
                              jobject obj,
                              jmethodID methodID,
                              va_list args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_call_object(thr, obj, methodID);
    )
    UNCHECKED()->CallVoidMethodV(env,obj,methodID,args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_CallVoidMethodA(JNIEnv *env,
                              jobject obj,
                              jmethodID methodID,
                              const jvalue * args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_call_object(thr, obj, methodID);
    )
    UNCHECKED()->CallVoidMethodA(env,obj,methodID,args);
    functionExit(env);
JNI_END

#define WRAPPER_CallNonvirtualMethod(ResultType, Result) \
JNI_ENTRY_CHECKED(ResultType,  \
  checked_jni_CallNonvirtual##Result##Method(JNIEnv *env, \
                                             jobject obj, \
                                             jclass clazz, \
                                             jmethodID methodID, \
                                             ...)) \
    functionEnter(thr); \
    va_list args; \
    IN_VM( \
      jniCheck::validate_call_object(thr, obj, methodID); \
      jniCheck::validate_call_class(thr, clazz, methodID); \
    ) \
    va_start(args,methodID); \
    ResultType result = UNCHECKED()->CallNonvirtual##Result##MethodV(env, \
                                                                     obj, \
                                                                     clazz, \
                                                                     methodID,\
                                                                     args); \
    va_end(args); \
    functionExit(env); \
    return result; \
JNI_END \
\
JNI_ENTRY_CHECKED(ResultType,  \
  checked_jni_CallNonvirtual##Result##MethodV(JNIEnv *env, \
                                              jobject obj, \
                                              jclass clazz, \
                                              jmethodID methodID, \
                                              va_list args)) \
    functionEnter(thr); \
    IN_VM( \
      jniCheck::validate_call_object(thr, obj, methodID); \
      jniCheck::validate_call_class(thr, clazz, methodID); \
    ) \
    ResultType result = UNCHECKED()->CallNonvirtual##Result##MethodV(env, \
                                                                     obj, \
                                                                     clazz, \
                                                                     methodID,\
                                                                     args); \
    functionExit(env); \
    return result; \
JNI_END \
\
JNI_ENTRY_CHECKED(ResultType,  \
  checked_jni_CallNonvirtual##Result##MethodA(JNIEnv *env, \
                                              jobject obj, \
                                              jclass clazz, \
                                              jmethodID methodID, \
                                              const jvalue * args)) \
    functionEnter(thr); \
    IN_VM( \
      jniCheck::validate_call_object(thr, obj, methodID); \
      jniCheck::validate_call_class(thr, clazz, methodID); \
    ) \
    ResultType result = UNCHECKED()->CallNonvirtual##Result##MethodA(env, \
                                                                     obj, \
                                                                     clazz, \
                                                                     methodID,\
                                                                     args); \
    functionExit(env); \
    return result; \
JNI_END

WRAPPER_CallNonvirtualMethod(jobject,Object)
WRAPPER_CallNonvirtualMethod(jboolean,Boolean)
WRAPPER_CallNonvirtualMethod(jbyte,Byte)
WRAPPER_CallNonvirtualMethod(jshort,Short)
WRAPPER_CallNonvirtualMethod(jchar,Char)
WRAPPER_CallNonvirtualMethod(jint,Int)
WRAPPER_CallNonvirtualMethod(jlong,Long)
WRAPPER_CallNonvirtualMethod(jfloat,Float)
WRAPPER_CallNonvirtualMethod(jdouble,Double)

JNI_ENTRY_CHECKED(void,
  checked_jni_CallNonvirtualVoidMethod(JNIEnv *env,
                                       jobject obj,
                                       jclass clazz,
                                       jmethodID methodID,
                                       ...))
    functionEnter(thr);
    va_list args;
    IN_VM(
      jniCheck::validate_call_object(thr, obj, methodID);
      jniCheck::validate_call_class(thr, clazz, methodID);
    )
    va_start(args,methodID);
    UNCHECKED()->CallNonvirtualVoidMethodV(env,obj,clazz,methodID,args);
    va_end(args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_CallNonvirtualVoidMethodV(JNIEnv *env,
                                        jobject obj,
                                        jclass clazz,
                                        jmethodID methodID,
                                        va_list args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_call_object(thr, obj, methodID);
      jniCheck::validate_call_class(thr, clazz, methodID);
    )
    UNCHECKED()->CallNonvirtualVoidMethodV(env,obj,clazz,methodID,args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_CallNonvirtualVoidMethodA(JNIEnv *env,
                                        jobject obj,
                                        jclass clazz,
                                        jmethodID methodID,
                                        const jvalue * args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_call_object(thr, obj, methodID);
      jniCheck::validate_call_class(thr, clazz, methodID);
    )
    UNCHECKED()->CallNonvirtualVoidMethodA(env,obj,clazz,methodID,args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jfieldID,
  checked_jni_GetFieldID(JNIEnv *env,
                         jclass clazz,
                         const char *name,
                         const char *sig))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
    )
    jfieldID result = UNCHECKED()->GetFieldID(env,clazz,name,sig);
    functionExit(env);
    return result;
JNI_END

#define WRAPPER_GetField(ReturnType,Result,FieldType) \
JNI_ENTRY_CHECKED(ReturnType,  \
  checked_jni_Get##Result##Field(JNIEnv *env, \
                                 jobject obj, \
                                 jfieldID fieldID)) \
    functionEnter(thr); \
    IN_VM( \
      checkInstanceFieldID(thr, fieldID, obj, FieldType); \
    ) \
    ReturnType result = UNCHECKED()->Get##Result##Field(env,obj,fieldID); \
    functionExit(env); \
    return result; \
JNI_END

WRAPPER_GetField(jobject,  Object,  T_OBJECT)
WRAPPER_GetField(jboolean, Boolean, T_BOOLEAN)
WRAPPER_GetField(jbyte,    Byte,    T_BYTE)
WRAPPER_GetField(jshort,   Short,   T_SHORT)
WRAPPER_GetField(jchar,    Char,    T_CHAR)
WRAPPER_GetField(jint,     Int,     T_INT)
WRAPPER_GetField(jlong,    Long,    T_LONG)
WRAPPER_GetField(jfloat,   Float,   T_FLOAT)
WRAPPER_GetField(jdouble,  Double,  T_DOUBLE)

#define WRAPPER_SetField(ValueType,Result,FieldType) \
JNI_ENTRY_CHECKED(void,  \
  checked_jni_Set##Result##Field(JNIEnv *env, \
                                 jobject obj, \
                                 jfieldID fieldID, \
                                 ValueType val)) \
    functionEnter(thr); \
    IN_VM( \
      checkInstanceFieldID(thr, fieldID, obj, FieldType); \
    ) \
    UNCHECKED()->Set##Result##Field(env,obj,fieldID,val); \
    functionExit(env); \
JNI_END

WRAPPER_SetField(jobject,  Object,  T_OBJECT)
WRAPPER_SetField(jboolean, Boolean, T_BOOLEAN)
WRAPPER_SetField(jbyte,    Byte,    T_BYTE)
WRAPPER_SetField(jshort,   Short,   T_SHORT)
WRAPPER_SetField(jchar,    Char,    T_CHAR)
WRAPPER_SetField(jint,     Int,     T_INT)
WRAPPER_SetField(jlong,    Long,    T_LONG)
WRAPPER_SetField(jfloat,   Float,   T_FLOAT)
WRAPPER_SetField(jdouble,  Double,  T_DOUBLE)


JNI_ENTRY_CHECKED(jmethodID,
  checked_jni_GetStaticMethodID(JNIEnv *env,
                                jclass clazz,
                                const char *name,
                                const char *sig))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
    )
    jmethodID result = UNCHECKED()->GetStaticMethodID(env,clazz,name,sig);
    functionExit(env);
    return result;
JNI_END

#define WRAPPER_CallStaticMethod(ReturnType,Result) \
JNI_ENTRY_CHECKED(ReturnType,  \
  checked_jni_CallStatic##Result##Method(JNIEnv *env, \
                                         jclass clazz, \
                                         jmethodID methodID, \
                                         ...)) \
    functionEnter(thr); \
    va_list args; \
    IN_VM( \
      jniCheck::validate_jmethod_id(thr, methodID); \
      jniCheck::validate_class(thr, clazz, false); \
    ) \
    va_start(args,methodID); \
    ReturnType result = UNCHECKED()->CallStatic##Result##MethodV(env, \
                                                                 clazz, \
                                                                 methodID, \
                                                                 args); \
    va_end(args); \
    functionExit(env); \
    return result; \
JNI_END \
\
JNI_ENTRY_CHECKED(ReturnType,  \
  checked_jni_CallStatic##Result##MethodV(JNIEnv *env, \
                                          jclass clazz, \
                                          jmethodID methodID,\
                                          va_list args)) \
    functionEnter(thr); \
    IN_VM( \
      jniCheck::validate_jmethod_id(thr, methodID); \
      jniCheck::validate_class(thr, clazz, false); \
    ) \
    ReturnType result = UNCHECKED()->CallStatic##Result##MethodV(env, \
                                                                 clazz, \
                                                                 methodID, \
                                                                 args); \
    functionExit(env); \
    return result; \
JNI_END \
\
JNI_ENTRY_CHECKED(ReturnType,  \
  checked_jni_CallStatic##Result##MethodA(JNIEnv *env, \
                                          jclass clazz, \
                                          jmethodID methodID, \
                                          const jvalue *args)) \
    functionEnter(thr); \
    IN_VM( \
      jniCheck::validate_jmethod_id(thr, methodID); \
      jniCheck::validate_class(thr, clazz, false); \
    ) \
    ReturnType result = UNCHECKED()->CallStatic##Result##MethodA(env, \
                                                                 clazz, \
                                                                 methodID, \
                                                                 args); \
    functionExit(env); \
    return result; \
JNI_END

WRAPPER_CallStaticMethod(jobject,Object)
WRAPPER_CallStaticMethod(jboolean,Boolean)
WRAPPER_CallStaticMethod(jbyte,Byte)
WRAPPER_CallStaticMethod(jshort,Short)
WRAPPER_CallStaticMethod(jchar,Char)
WRAPPER_CallStaticMethod(jint,Int)
WRAPPER_CallStaticMethod(jlong,Long)
WRAPPER_CallStaticMethod(jfloat,Float)
WRAPPER_CallStaticMethod(jdouble,Double)

JNI_ENTRY_CHECKED(void,
  checked_jni_CallStaticVoidMethod(JNIEnv *env,
                                   jclass cls,
                                   jmethodID methodID,
                                   ...))
    functionEnter(thr);
    va_list args;
    IN_VM(
      jniCheck::validate_jmethod_id(thr, methodID);
      jniCheck::validate_class(thr, cls, false);
    )
    va_start(args,methodID);
    UNCHECKED()->CallStaticVoidMethodV(env,cls,methodID,args);
    va_end(args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_CallStaticVoidMethodV(JNIEnv *env,
                                    jclass cls,
                                    jmethodID methodID,
                                    va_list args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_jmethod_id(thr, methodID);
      jniCheck::validate_class(thr, cls, false);
    )
    UNCHECKED()->CallStaticVoidMethodV(env,cls,methodID,args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_CallStaticVoidMethodA(JNIEnv *env,
                                    jclass cls,
                                    jmethodID methodID,
                                    const jvalue * args))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_jmethod_id(thr, methodID);
      jniCheck::validate_class(thr, cls, false);
    )
    UNCHECKED()->CallStaticVoidMethodA(env,cls,methodID,args);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jfieldID,
  checked_jni_GetStaticFieldID(JNIEnv *env,
                               jclass clazz,
                               const char *name,
                               const char *sig))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_class(thr, clazz, false);
    )
    jfieldID result = UNCHECKED()->GetStaticFieldID(env,clazz,name,sig);
    functionExit(env);
    return result;
JNI_END

#define WRAPPER_GetStaticField(ReturnType,Result,FieldType) \
JNI_ENTRY_CHECKED(ReturnType,  \
  checked_jni_GetStatic##Result##Field(JNIEnv *env, \
                                       jclass clazz, \
                                       jfieldID fieldID)) \
    functionEnter(thr); \
    IN_VM( \
      jniCheck::validate_class(thr, clazz, false); \
      checkStaticFieldID(thr, fieldID, clazz, FieldType); \
    ) \
    ReturnType result = UNCHECKED()->GetStatic##Result##Field(env, \
                                                              clazz, \
                                                              fieldID); \
    functionExit(env); \
    return result; \
JNI_END

WRAPPER_GetStaticField(jobject,  Object,  T_OBJECT)
WRAPPER_GetStaticField(jboolean, Boolean, T_BOOLEAN)
WRAPPER_GetStaticField(jbyte,    Byte,    T_BYTE)
WRAPPER_GetStaticField(jshort,   Short,   T_SHORT)
WRAPPER_GetStaticField(jchar,    Char,    T_CHAR)
WRAPPER_GetStaticField(jint,     Int,     T_INT)
WRAPPER_GetStaticField(jlong,    Long,    T_LONG)
WRAPPER_GetStaticField(jfloat,   Float,   T_FLOAT)
WRAPPER_GetStaticField(jdouble,  Double,  T_DOUBLE)

#define WRAPPER_SetStaticField(ValueType,Result,FieldType) \
JNI_ENTRY_CHECKED(void,  \
  checked_jni_SetStatic##Result##Field(JNIEnv *env, \
                                       jclass clazz, \
                                       jfieldID fieldID, \
                                       ValueType value)) \
    functionEnter(thr); \
    IN_VM( \
      jniCheck::validate_class(thr, clazz, false); \
      checkStaticFieldID(thr, fieldID, clazz, FieldType); \
    ) \
    UNCHECKED()->SetStatic##Result##Field(env,clazz,fieldID,value); \
    functionExit(env); \
JNI_END

WRAPPER_SetStaticField(jobject,  Object,  T_OBJECT)
WRAPPER_SetStaticField(jboolean, Boolean, T_BOOLEAN)
WRAPPER_SetStaticField(jbyte,    Byte,    T_BYTE)
WRAPPER_SetStaticField(jshort,   Short,   T_SHORT)
WRAPPER_SetStaticField(jchar,    Char,    T_CHAR)
WRAPPER_SetStaticField(jint,     Int,     T_INT)
WRAPPER_SetStaticField(jlong,    Long,    T_LONG)
WRAPPER_SetStaticField(jfloat,   Float,   T_FLOAT)
WRAPPER_SetStaticField(jdouble,  Double,  T_DOUBLE)


JNI_ENTRY_CHECKED(jstring,
  checked_jni_NewString(JNIEnv *env,
                        const jchar *unicode,
                        jsize len))
    functionEnter(thr);
    jstring result = UNCHECKED()->NewString(env,unicode,len);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jsize,
  checked_jni_GetStringLength(JNIEnv *env,
                              jstring str))
    functionEnter(thr);
    IN_VM(
      checkString(thr, str);
    )
    jsize result = UNCHECKED()->GetStringLength(env,str);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(const jchar *,
  checked_jni_GetStringChars(JNIEnv *env,
                             jstring str,
                             jboolean *isCopy))
    functionEnter(thr);
    IN_VM(
      checkString(thr, str);
    )
    const jchar *result = UNCHECKED()->GetStringChars(env,str,isCopy);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_ReleaseStringChars(JNIEnv *env,
                                 jstring str,
                                 const jchar *chars))
    functionEnterExceptionAllowed(thr);
    IN_VM(
      checkString(thr, str);
    )
    /* cannot check validity of copy, unless every request is logged by
     * checking code.  Implementation of this check is deferred until a
     * subsequent release.
     */
    UNCHECKED()->ReleaseStringChars(env,str,chars);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jstring,
  checked_jni_NewStringUTF(JNIEnv *env,
                           const char *utf))
    functionEnter(thr);
    jstring result = UNCHECKED()->NewStringUTF(env,utf);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jsize,
  checked_jni_GetStringUTFLength(JNIEnv *env,
                                 jstring str))
    functionEnter(thr);
    IN_VM(
      checkString(thr, str);
    )
    jsize result = UNCHECKED()->GetStringUTFLength(env,str);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(const char *,
  checked_jni_GetStringUTFChars(JNIEnv *env,
                                jstring str,
                                jboolean *isCopy))
    functionEnter(thr);
    IN_VM(
      checkString(thr, str);
    )
    const char *result = UNCHECKED()->GetStringUTFChars(env,str,isCopy);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_ReleaseStringUTFChars(JNIEnv *env,
                                    jstring str,
                                    const char* chars))
    functionEnterExceptionAllowed(thr);
    IN_VM(
      checkString(thr, str);
    )
    /* cannot check validity of copy, unless every request is logged by
     * checking code.  Implementation of this check is deferred until a
     * subsequent release.
     */
    UNCHECKED()->ReleaseStringUTFChars(env,str,chars);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jsize,
  checked_jni_GetArrayLength(JNIEnv *env,
                             jarray array))
    functionEnter(thr);
    IN_VM(
      checkArray(thr, array, -1);
    )
    jsize result = UNCHECKED()->GetArrayLength(env,array);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobjectArray,
  checked_jni_NewObjectArray(JNIEnv *env,
                             jsize len,
                             jclass clazz,
                             jobject init))
    functionEnter(thr);
    jobjectArray result = UNCHECKED()->NewObjectArray(env,len,clazz,init);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_GetObjectArrayElement(JNIEnv *env,
                                    jobjectArray array,
                                    jsize index))
    functionEnter(thr);
    IN_VM(
      checkArray(thr, array, T_OBJECT);
    )
    jobject result = UNCHECKED()->GetObjectArrayElement(env,array,index);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_SetObjectArrayElement(JNIEnv *env,
                                    jobjectArray array,
                                    jsize index,
                                    jobject val))
    functionEnter(thr);
    IN_VM(
      checkArray(thr, array, T_OBJECT);
    )
    UNCHECKED()->SetObjectArrayElement(env,array,index,val);
    functionExit(env);
JNI_END

#define WRAPPER_NewScalarArray(Return, Result) \
JNI_ENTRY_CHECKED(Return, \
  checked_jni_New##Result##Array(JNIEnv *env, \
                                 jsize len)) \
    functionEnter(thr); \
    Return result = UNCHECKED()->New##Result##Array(env,len); \
    functionExit(env); \
    return (Return) result; \
JNI_END

WRAPPER_NewScalarArray(jbooleanArray, Boolean)
WRAPPER_NewScalarArray(jbyteArray, Byte)
WRAPPER_NewScalarArray(jshortArray, Short)
WRAPPER_NewScalarArray(jcharArray, Char)
WRAPPER_NewScalarArray(jintArray, Int)
WRAPPER_NewScalarArray(jlongArray, Long)
WRAPPER_NewScalarArray(jfloatArray, Float)
WRAPPER_NewScalarArray(jdoubleArray, Double)

#define WRAPPER_GetScalarArrayElements(ElementTag,ElementType,Result) \
JNI_ENTRY_CHECKED(ElementType *,  \
  checked_jni_Get##Result##ArrayElements(JNIEnv *env, \
                                         ElementType##Array array, \
                                         jboolean *isCopy)) \
    functionEnter(thr); \
    IN_VM( \
      checkArray(thr, array, ElementTag); \
    ) \
    ElementType *result = UNCHECKED()->Get##Result##ArrayElements(env, \
                                                                  array, \
                                                                  isCopy); \
    functionExit(env); \
    return result; \
JNI_END

WRAPPER_GetScalarArrayElements(T_BOOLEAN, jboolean, Boolean)
WRAPPER_GetScalarArrayElements(T_BYTE,    jbyte,    Byte)
WRAPPER_GetScalarArrayElements(T_SHORT,   jshort,   Short)
WRAPPER_GetScalarArrayElements(T_CHAR,    jchar,    Char)
WRAPPER_GetScalarArrayElements(T_INT,     jint,     Int)
WRAPPER_GetScalarArrayElements(T_LONG,    jlong,    Long)
WRAPPER_GetScalarArrayElements(T_FLOAT,   jfloat,   Float)
WRAPPER_GetScalarArrayElements(T_DOUBLE,  jdouble,  Double)

#define WRAPPER_ReleaseScalarArrayElements(ElementTag,ElementType,Result,Tag) \
JNI_ENTRY_CHECKED(void,  \
  checked_jni_Release##Result##ArrayElements(JNIEnv *env, \
                                             ElementType##Array array, \
                                             ElementType *elems, \
                                             jint mode)) \
    functionEnterExceptionAllowed(thr); \
    IN_VM( \
      checkArray(thr, array, ElementTag); \
      ASSERT_OOPS_ALLOWED; \
      typeArrayOop a = typeArrayOop(JNIHandles::resolve_non_null(array)); \
      /* cannot check validity of copy, unless every request is logged by
       * checking code.  Implementation of this check is deferred until a
       * subsequent release.
       */ \
    ) \
    UNCHECKED()->Release##Result##ArrayElements(env,array,elems,mode); \
    functionExit(env); \
JNI_END

WRAPPER_ReleaseScalarArrayElements(T_BOOLEAN,jboolean, Boolean, bool)
WRAPPER_ReleaseScalarArrayElements(T_BYTE,   jbyte,    Byte,    byte)
WRAPPER_ReleaseScalarArrayElements(T_SHORT,  jshort,   Short,   short)
WRAPPER_ReleaseScalarArrayElements(T_CHAR,   jchar,    Char,    char)
WRAPPER_ReleaseScalarArrayElements(T_INT,    jint,     Int,     int)
WRAPPER_ReleaseScalarArrayElements(T_LONG,   jlong,    Long,    long)
WRAPPER_ReleaseScalarArrayElements(T_FLOAT,  jfloat,   Float,   float)
WRAPPER_ReleaseScalarArrayElements(T_DOUBLE, jdouble,  Double,  double)

#define WRAPPER_GetScalarArrayRegion(ElementTag,ElementType,Result) \
JNI_ENTRY_CHECKED(void,  \
  checked_jni_Get##Result##ArrayRegion(JNIEnv *env, \
                                       ElementType##Array array, \
                                       jsize start, \
                                       jsize len, \
                                       ElementType *buf)) \
    functionEnter(thr); \
    IN_VM( \
      checkArray(thr, array, ElementTag); \
    ) \
    UNCHECKED()->Get##Result##ArrayRegion(env,array,start,len,buf); \
    functionExit(env); \
JNI_END

WRAPPER_GetScalarArrayRegion(T_BOOLEAN, jboolean, Boolean)
WRAPPER_GetScalarArrayRegion(T_BYTE,    jbyte,    Byte)
WRAPPER_GetScalarArrayRegion(T_SHORT,   jshort,   Short)
WRAPPER_GetScalarArrayRegion(T_CHAR,    jchar,    Char)
WRAPPER_GetScalarArrayRegion(T_INT,     jint,     Int)
WRAPPER_GetScalarArrayRegion(T_LONG,    jlong,    Long)
WRAPPER_GetScalarArrayRegion(T_FLOAT,   jfloat,   Float)
WRAPPER_GetScalarArrayRegion(T_DOUBLE,  jdouble,  Double)

#define WRAPPER_SetScalarArrayRegion(ElementTag,ElementType,Result) \
JNI_ENTRY_CHECKED(void,  \
  checked_jni_Set##Result##ArrayRegion(JNIEnv *env, \
                                       ElementType##Array array, \
                                       jsize start, \
                                       jsize len, \
                                       const ElementType *buf)) \
    functionEnter(thr); \
    IN_VM( \
      checkArray(thr, array, ElementTag); \
    ) \
    UNCHECKED()->Set##Result##ArrayRegion(env,array,start,len,buf); \
    functionExit(env); \
JNI_END

WRAPPER_SetScalarArrayRegion(T_BOOLEAN, jboolean, Boolean)
WRAPPER_SetScalarArrayRegion(T_BYTE,    jbyte,    Byte)
WRAPPER_SetScalarArrayRegion(T_SHORT,   jshort,   Short)
WRAPPER_SetScalarArrayRegion(T_CHAR,    jchar,    Char)
WRAPPER_SetScalarArrayRegion(T_INT,     jint,     Int)
WRAPPER_SetScalarArrayRegion(T_LONG,    jlong,    Long)
WRAPPER_SetScalarArrayRegion(T_FLOAT,   jfloat,   Float)
WRAPPER_SetScalarArrayRegion(T_DOUBLE,  jdouble,  Double)

JNI_ENTRY_CHECKED(jint,
  checked_jni_RegisterNatives(JNIEnv *env,
                              jclass clazz,
                              const JNINativeMethod *methods,
                              jint nMethods))
    functionEnter(thr);
    jint result = UNCHECKED()->RegisterNatives(env,clazz,methods,nMethods);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_UnregisterNatives(JNIEnv *env,
                                jclass clazz))
    functionEnter(thr);
    jint result = UNCHECKED()->UnregisterNatives(env,clazz);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_MonitorEnter(JNIEnv *env,
                           jobject obj))
    functionEnter(thr);
    IN_VM(
      jniCheck::validate_object(thr, obj);
    )
    jint result = UNCHECKED()->MonitorEnter(env,obj);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_MonitorExit(JNIEnv *env,
                          jobject obj))
    functionEnterExceptionAllowed(thr);
    IN_VM(
      jniCheck::validate_object(thr, obj);
    )
    jint result = UNCHECKED()->MonitorExit(env,obj);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jint,
  checked_jni_GetJavaVM(JNIEnv *env,
                        JavaVM **vm))
    functionEnter(thr);
    jint result = UNCHECKED()->GetJavaVM(env,vm);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_GetStringRegion(JNIEnv *env,
                              jstring str,
                              jsize start,
                              jsize len,
                              jchar *buf))
    functionEnter(thr);
    IN_VM(
      checkString(thr, str);
    )
    UNCHECKED()->GetStringRegion(env, str, start, len, buf);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_GetStringUTFRegion(JNIEnv *env,
                                 jstring str,
                                 jsize start,
                                 jsize len,
                                 char *buf))
    functionEnter(thr);
    IN_VM(
      checkString(thr, str);
    )
    UNCHECKED()->GetStringUTFRegion(env, str, start, len, buf);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(void *,
  checked_jni_GetPrimitiveArrayCritical(JNIEnv *env,
                                        jarray array,
                                        jboolean *isCopy))
    functionEnterCritical(thr);
    IN_VM(
      checkArray(thr, array, -1);
    )
    void *result = UNCHECKED()->GetPrimitiveArrayCritical(env, array, isCopy);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_ReleasePrimitiveArrayCritical(JNIEnv *env,
                                            jarray array,
                                            void *carray,
                                            jint mode))
    functionEnterCriticalExceptionAllowed(thr);
    IN_VM(
      checkArray(thr, array, -1);
    )
    /* The Hotspot JNI code does not use the parameters, so just check the
     * array parameter as a minor sanity check
     */
    UNCHECKED()->ReleasePrimitiveArrayCritical(env, array, carray, mode);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(const jchar*,
  checked_jni_GetStringCritical(JNIEnv *env,
                                jstring string,
                                jboolean *isCopy))
    functionEnterCritical(thr);
    IN_VM(
      checkString(thr, string);
    )
    const jchar *result = UNCHECKED()->GetStringCritical(env, string, isCopy);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_ReleaseStringCritical(JNIEnv *env,
                                    jstring str,
                                    const jchar *chars))
    functionEnterCriticalExceptionAllowed(thr);
    IN_VM(
      checkString(thr, str);
    )
    /* The Hotspot JNI code does not use the parameters, so just check the
     * string parameter as a minor sanity check
     */
    UNCHECKED()->ReleaseStringCritical(env, str, chars);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jweak,
  checked_jni_NewWeakGlobalRef(JNIEnv *env,
                               jobject obj))
    functionEnter(thr);
    IN_VM(
      if (obj != NULL) {
        jniCheck::validate_handle(thr, obj);
      }
    )
    jweak result = UNCHECKED()->NewWeakGlobalRef(env, obj);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void,
  checked_jni_DeleteWeakGlobalRef(JNIEnv *env,
                                  jweak ref))
    functionEnterExceptionAllowed(thr);
    UNCHECKED()->DeleteWeakGlobalRef(env, ref);
    functionExit(env);
JNI_END

JNI_ENTRY_CHECKED(jboolean,
  checked_jni_ExceptionCheck(JNIEnv *env))
    functionEnterExceptionAllowed(thr);
    jboolean result = UNCHECKED()->ExceptionCheck(env);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobject,
  checked_jni_NewDirectByteBuffer(JNIEnv *env,
                                  void *address,
                                  jlong capacity))
    functionEnter(thr);
    jobject result = UNCHECKED()->NewDirectByteBuffer(env, address, capacity);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(void *,
  checked_jni_GetDirectBufferAddress(JNIEnv *env,
                                     jobject buf))
    functionEnter(thr);
    void* result = UNCHECKED()->GetDirectBufferAddress(env, buf);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jlong,
  checked_jni_GetDirectBufferCapacity(JNIEnv *env,
                                      jobject buf))
    functionEnter(thr);
    jlong result = UNCHECKED()->GetDirectBufferCapacity(env, buf);
    functionExit(env);
    return result;
JNI_END

JNI_ENTRY_CHECKED(jobjectRefType,
  checked_jni_GetObjectRefType(JNIEnv *env,
                               jobject obj))
    functionEnter(thr);
    /* validate the object being passed */
    IN_VM(
      jniCheck::validate_object(thr, obj);
    )
    jobjectRefType result = UNCHECKED()->GetObjectRefType(env, obj);
    functionExit(env);
    return result;
JNI_END


JNI_ENTRY_CHECKED(jint,
  checked_jni_GetVersion(JNIEnv *env))
    functionEnter(thr);
    jint result = UNCHECKED()->GetVersion(env);
    functionExit(env);
    return result;
JNI_END



/*
 * Structure containing all checked jni functions
 */
struct JNINativeInterface_  checked_jni_NativeInterface = {
    NULL,
    NULL,
    NULL,

    NULL,

    checked_jni_GetVersion,

    checked_jni_DefineClass,
    checked_jni_FindClass,

    checked_jni_FromReflectedMethod,
    checked_jni_FromReflectedField,

    checked_jni_ToReflectedMethod,

    checked_jni_GetSuperclass,
    checked_jni_IsAssignableFrom,

    checked_jni_ToReflectedField,

    checked_jni_Throw,
    checked_jni_ThrowNew,
    checked_jni_ExceptionOccurred,
    checked_jni_ExceptionDescribe,
    checked_jni_ExceptionClear,
    checked_jni_FatalError,

    checked_jni_PushLocalFrame,
    checked_jni_PopLocalFrame,

    checked_jni_NewGlobalRef,
    checked_jni_DeleteGlobalRef,
    checked_jni_DeleteLocalRef,
    checked_jni_IsSameObject,

    checked_jni_NewLocalRef,
    checked_jni_EnsureLocalCapacity,

    checked_jni_AllocObject,
    checked_jni_NewObject,
    checked_jni_NewObjectV,
    checked_jni_NewObjectA,

    checked_jni_GetObjectClass,
    checked_jni_IsInstanceOf,

    checked_jni_GetMethodID,

    checked_jni_CallObjectMethod,
    checked_jni_CallObjectMethodV,
    checked_jni_CallObjectMethodA,
    checked_jni_CallBooleanMethod,
    checked_jni_CallBooleanMethodV,
    checked_jni_CallBooleanMethodA,
    checked_jni_CallByteMethod,
    checked_jni_CallByteMethodV,
    checked_jni_CallByteMethodA,
    checked_jni_CallCharMethod,
    checked_jni_CallCharMethodV,
    checked_jni_CallCharMethodA,
    checked_jni_CallShortMethod,
    checked_jni_CallShortMethodV,
    checked_jni_CallShortMethodA,
    checked_jni_CallIntMethod,
    checked_jni_CallIntMethodV,
    checked_jni_CallIntMethodA,
    checked_jni_CallLongMethod,
    checked_jni_CallLongMethodV,
    checked_jni_CallLongMethodA,
    checked_jni_CallFloatMethod,
    checked_jni_CallFloatMethodV,
    checked_jni_CallFloatMethodA,
    checked_jni_CallDoubleMethod,
    checked_jni_CallDoubleMethodV,
    checked_jni_CallDoubleMethodA,
    checked_jni_CallVoidMethod,
    checked_jni_CallVoidMethodV,
    checked_jni_CallVoidMethodA,

    checked_jni_CallNonvirtualObjectMethod,
    checked_jni_CallNonvirtualObjectMethodV,
    checked_jni_CallNonvirtualObjectMethodA,
    checked_jni_CallNonvirtualBooleanMethod,
    checked_jni_CallNonvirtualBooleanMethodV,
    checked_jni_CallNonvirtualBooleanMethodA,
    checked_jni_CallNonvirtualByteMethod,
    checked_jni_CallNonvirtualByteMethodV,
    checked_jni_CallNonvirtualByteMethodA,
    checked_jni_CallNonvirtualCharMethod,
    checked_jni_CallNonvirtualCharMethodV,
    checked_jni_CallNonvirtualCharMethodA,
    checked_jni_CallNonvirtualShortMethod,
    checked_jni_CallNonvirtualShortMethodV,
    checked_jni_CallNonvirtualShortMethodA,
    checked_jni_CallNonvirtualIntMethod,
    checked_jni_CallNonvirtualIntMethodV,
    checked_jni_CallNonvirtualIntMethodA,
    checked_jni_CallNonvirtualLongMethod,
    checked_jni_CallNonvirtualLongMethodV,
    checked_jni_CallNonvirtualLongMethodA,
    checked_jni_CallNonvirtualFloatMethod,
    checked_jni_CallNonvirtualFloatMethodV,
    checked_jni_CallNonvirtualFloatMethodA,
    checked_jni_CallNonvirtualDoubleMethod,
    checked_jni_CallNonvirtualDoubleMethodV,
    checked_jni_CallNonvirtualDoubleMethodA,
    checked_jni_CallNonvirtualVoidMethod,
    checked_jni_CallNonvirtualVoidMethodV,
    checked_jni_CallNonvirtualVoidMethodA,

    checked_jni_GetFieldID,

    checked_jni_GetObjectField,
    checked_jni_GetBooleanField,
    checked_jni_GetByteField,
    checked_jni_GetCharField,
    checked_jni_GetShortField,
    checked_jni_GetIntField,
    checked_jni_GetLongField,
    checked_jni_GetFloatField,
    checked_jni_GetDoubleField,

    checked_jni_SetObjectField,
    checked_jni_SetBooleanField,
    checked_jni_SetByteField,
    checked_jni_SetCharField,
    checked_jni_SetShortField,
    checked_jni_SetIntField,
    checked_jni_SetLongField,
    checked_jni_SetFloatField,
    checked_jni_SetDoubleField,

    checked_jni_GetStaticMethodID,

    checked_jni_CallStaticObjectMethod,
    checked_jni_CallStaticObjectMethodV,
    checked_jni_CallStaticObjectMethodA,
    checked_jni_CallStaticBooleanMethod,
    checked_jni_CallStaticBooleanMethodV,
    checked_jni_CallStaticBooleanMethodA,
    checked_jni_CallStaticByteMethod,
    checked_jni_CallStaticByteMethodV,
    checked_jni_CallStaticByteMethodA,
    checked_jni_CallStaticCharMethod,
    checked_jni_CallStaticCharMethodV,
    checked_jni_CallStaticCharMethodA,
    checked_jni_CallStaticShortMethod,
    checked_jni_CallStaticShortMethodV,
    checked_jni_CallStaticShortMethodA,
    checked_jni_CallStaticIntMethod,
    checked_jni_CallStaticIntMethodV,
    checked_jni_CallStaticIntMethodA,
    checked_jni_CallStaticLongMethod,
    checked_jni_CallStaticLongMethodV,
    checked_jni_CallStaticLongMethodA,
    checked_jni_CallStaticFloatMethod,
    checked_jni_CallStaticFloatMethodV,
    checked_jni_CallStaticFloatMethodA,
    checked_jni_CallStaticDoubleMethod,
    checked_jni_CallStaticDoubleMethodV,
    checked_jni_CallStaticDoubleMethodA,
    checked_jni_CallStaticVoidMethod,
    checked_jni_CallStaticVoidMethodV,
    checked_jni_CallStaticVoidMethodA,

    checked_jni_GetStaticFieldID,

    checked_jni_GetStaticObjectField,
    checked_jni_GetStaticBooleanField,
    checked_jni_GetStaticByteField,
    checked_jni_GetStaticCharField,
    checked_jni_GetStaticShortField,
    checked_jni_GetStaticIntField,
    checked_jni_GetStaticLongField,
    checked_jni_GetStaticFloatField,
    checked_jni_GetStaticDoubleField,

    checked_jni_SetStaticObjectField,
    checked_jni_SetStaticBooleanField,
    checked_jni_SetStaticByteField,
    checked_jni_SetStaticCharField,
    checked_jni_SetStaticShortField,
    checked_jni_SetStaticIntField,
    checked_jni_SetStaticLongField,
    checked_jni_SetStaticFloatField,
    checked_jni_SetStaticDoubleField,

    checked_jni_NewString,
    checked_jni_GetStringLength,
    checked_jni_GetStringChars,
    checked_jni_ReleaseStringChars,

    checked_jni_NewStringUTF,
    checked_jni_GetStringUTFLength,
    checked_jni_GetStringUTFChars,
    checked_jni_ReleaseStringUTFChars,

    checked_jni_GetArrayLength,

    checked_jni_NewObjectArray,
    checked_jni_GetObjectArrayElement,
    checked_jni_SetObjectArrayElement,

    checked_jni_NewBooleanArray,
    checked_jni_NewByteArray,
    checked_jni_NewCharArray,
    checked_jni_NewShortArray,
    checked_jni_NewIntArray,
    checked_jni_NewLongArray,
    checked_jni_NewFloatArray,
    checked_jni_NewDoubleArray,

    checked_jni_GetBooleanArrayElements,
    checked_jni_GetByteArrayElements,
    checked_jni_GetCharArrayElements,
    checked_jni_GetShortArrayElements,
    checked_jni_GetIntArrayElements,
    checked_jni_GetLongArrayElements,
    checked_jni_GetFloatArrayElements,
    checked_jni_GetDoubleArrayElements,

    checked_jni_ReleaseBooleanArrayElements,
    checked_jni_ReleaseByteArrayElements,
    checked_jni_ReleaseCharArrayElements,
    checked_jni_ReleaseShortArrayElements,
    checked_jni_ReleaseIntArrayElements,
    checked_jni_ReleaseLongArrayElements,
    checked_jni_ReleaseFloatArrayElements,
    checked_jni_ReleaseDoubleArrayElements,

    checked_jni_GetBooleanArrayRegion,
    checked_jni_GetByteArrayRegion,
    checked_jni_GetCharArrayRegion,
    checked_jni_GetShortArrayRegion,
    checked_jni_GetIntArrayRegion,
    checked_jni_GetLongArrayRegion,
    checked_jni_GetFloatArrayRegion,
    checked_jni_GetDoubleArrayRegion,

    checked_jni_SetBooleanArrayRegion,
    checked_jni_SetByteArrayRegion,
    checked_jni_SetCharArrayRegion,
    checked_jni_SetShortArrayRegion,
    checked_jni_SetIntArrayRegion,
    checked_jni_SetLongArrayRegion,
    checked_jni_SetFloatArrayRegion,
    checked_jni_SetDoubleArrayRegion,

    checked_jni_RegisterNatives,
    checked_jni_UnregisterNatives,

    checked_jni_MonitorEnter,
    checked_jni_MonitorExit,

    checked_jni_GetJavaVM,

    checked_jni_GetStringRegion,
    checked_jni_GetStringUTFRegion,

    checked_jni_GetPrimitiveArrayCritical,
    checked_jni_ReleasePrimitiveArrayCritical,

    checked_jni_GetStringCritical,
    checked_jni_ReleaseStringCritical,

    checked_jni_NewWeakGlobalRef,
    checked_jni_DeleteWeakGlobalRef,

    checked_jni_ExceptionCheck,

    checked_jni_NewDirectByteBuffer,
    checked_jni_GetDirectBufferAddress,
    checked_jni_GetDirectBufferCapacity,

    // New 1.6 Features

    checked_jni_GetObjectRefType
};


// Returns the function structure
struct JNINativeInterface_* jni_functions_check() {

  unchecked_jni_NativeInterface = jni_functions_nocheck();

  // make sure the last pointer in the checked table is not null, indicating
  // an addition to the JNINativeInterface_ structure without initializing
  // it in the checked table.
  debug_only(int *lastPtr = (int *)((char *)&checked_jni_NativeInterface + \
             sizeof(*unchecked_jni_NativeInterface) - sizeof(char *));)
  assert(*lastPtr != 0,
         "Mismatched JNINativeInterface tables, check for new entries");

  // with -verbose:jni this message will print
  if (PrintJNIResolving) {
    tty->print_cr("Checked JNI functions are being used to " \
                  "validate JNI usage");
  }

  return &checked_jni_NativeInterface;
}
