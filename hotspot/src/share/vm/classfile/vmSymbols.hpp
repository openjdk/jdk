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

// The classes vmSymbols and vmSymbolHandles are a name spaces for fast lookup of
// symbols commonly used in the VM. The first class return a symbolOop, while the
// second class returns a SymbolHandle. The underlying data structure is shared
// between the two classes.
//
// Sample usage:
//
//   symbolOop obj       = vmSymbols::java_lang_Object()();
//   SymbolHandle handle = vmSymbolHandles::java_lang_Object();


// Useful sub-macros exported by this header file:

#define VM_SYMBOL_ENUM_NAME(name)    name##_enum
#define VM_INTRINSIC_IGNORE(id, class, name, sig, flags) /*ignored*/
#define VM_SYMBOL_IGNORE(id, name)                       /*ignored*/
#define VM_ALIAS_IGNORE(id, id2)                         /*ignored*/


// Mapping function names to values. New entries should be added below.

#define VM_SYMBOLS_DO(template, do_alias)                                                         \
  /* commonly used class names */                                                                 \
  template(java_lang_System,                          "java/lang/System")                         \
  template(java_lang_Object,                          "java/lang/Object")                         \
  template(java_lang_Class,                           "java/lang/Class")                          \
  template(java_lang_String,                          "java/lang/String")                         \
  template(java_lang_StringValue,                     "java/lang/StringValue")                    \
  template(java_lang_StringCache,                     "java/lang/StringValue$StringCache")        \
  template(java_lang_Thread,                          "java/lang/Thread")                         \
  template(java_lang_ThreadGroup,                     "java/lang/ThreadGroup")                    \
  template(java_lang_Cloneable,                       "java/lang/Cloneable")                      \
  template(java_lang_Throwable,                       "java/lang/Throwable")                      \
  template(java_lang_ClassLoader,                     "java/lang/ClassLoader")                    \
  template(java_lang_ClassLoader_NativeLibrary,       "java/lang/ClassLoader\x024NativeLibrary")  \
  template(java_lang_ThreadDeath,                     "java/lang/ThreadDeath")                    \
  template(java_lang_Boolean,                         "java/lang/Boolean")                        \
  template(java_lang_Character,                       "java/lang/Character")                      \
  template(java_lang_Character_CharacterCache,        "java/lang/Character$CharacterCache")       \
  template(java_lang_Float,                           "java/lang/Float")                          \
  template(java_lang_Double,                          "java/lang/Double")                         \
  template(java_lang_Byte,                            "java/lang/Byte")                           \
  template(java_lang_Byte_Cache,                      "java/lang/Byte$ByteCache")                 \
  template(java_lang_Short,                           "java/lang/Short")                          \
  template(java_lang_Short_ShortCache,                "java/lang/Short$ShortCache")               \
  template(java_lang_Integer,                         "java/lang/Integer")                        \
  template(java_lang_Integer_IntegerCache,            "java/lang/Integer$IntegerCache")           \
  template(java_lang_Long,                            "java/lang/Long")                           \
  template(java_lang_Long_LongCache,                  "java/lang/Long$LongCache")                 \
  template(java_lang_Shutdown,                        "java/lang/Shutdown")                       \
  template(java_lang_ref_Reference,                   "java/lang/ref/Reference")                  \
  template(java_lang_ref_SoftReference,               "java/lang/ref/SoftReference")              \
  template(java_lang_ref_WeakReference,               "java/lang/ref/WeakReference")              \
  template(java_lang_ref_FinalReference,              "java/lang/ref/FinalReference")             \
  template(java_lang_ref_PhantomReference,            "java/lang/ref/PhantomReference")           \
  template(java_lang_ref_Finalizer,                   "java/lang/ref/Finalizer")                  \
  template(java_lang_reflect_AccessibleObject,        "java/lang/reflect/AccessibleObject")       \
  template(java_lang_reflect_Method,                  "java/lang/reflect/Method")                 \
  template(java_lang_reflect_Constructor,             "java/lang/reflect/Constructor")            \
  template(java_lang_reflect_Field,                   "java/lang/reflect/Field")                  \
  template(java_lang_reflect_Array,                   "java/lang/reflect/Array")                  \
  template(java_lang_StringBuffer,                    "java/lang/StringBuffer")                   \
  template(java_lang_CharSequence,                    "java/lang/CharSequence")                   \
  template(java_security_AccessControlContext,        "java/security/AccessControlContext")       \
  template(java_security_ProtectionDomain,            "java/security/ProtectionDomain")           \
  template(java_io_OutputStream,                      "java/io/OutputStream")                     \
  template(java_io_Reader,                            "java/io/Reader")                           \
  template(java_io_BufferedReader,                    "java/io/BufferedReader")                   \
  template(java_io_FileInputStream,                   "java/io/FileInputStream")                  \
  template(java_io_ByteArrayInputStream,              "java/io/ByteArrayInputStream")             \
  template(java_io_Serializable,                      "java/io/Serializable")                     \
  template(java_util_Arrays,                          "java/util/Arrays")                         \
  template(java_util_Properties,                      "java/util/Properties")                     \
  template(java_util_Vector,                          "java/util/Vector")                         \
  template(java_util_AbstractList,                    "java/util/AbstractList")                   \
  template(java_util_Hashtable,                       "java/util/Hashtable")                      \
  template(java_util_HashMap,                         "java/util/HashMap")                        \
  template(java_lang_Compiler,                        "java/lang/Compiler")                       \
  template(sun_misc_Signal,                           "sun/misc/Signal")                          \
  template(java_lang_AssertionStatusDirectives,       "java/lang/AssertionStatusDirectives")      \
  template(sun_jkernel_DownloadManager,               "sun/jkernel/DownloadManager")              \
  template(getBootClassPathEntryForClass_name,        "getBootClassPathEntryForClass")            \
                                                                                                  \
  /* class file format tags */                                                                    \
  template(tag_source_file,                           "SourceFile")                               \
  template(tag_inner_classes,                         "InnerClasses")                             \
  template(tag_constant_value,                        "ConstantValue")                            \
  template(tag_code,                                  "Code")                                     \
  template(tag_exceptions,                            "Exceptions")                               \
  template(tag_line_number_table,                     "LineNumberTable")                          \
  template(tag_local_variable_table,                  "LocalVariableTable")                       \
  template(tag_local_variable_type_table,             "LocalVariableTypeTable")                   \
  template(tag_stack_map_table,                       "StackMapTable")                            \
  template(tag_synthetic,                             "Synthetic")                                \
  template(tag_deprecated,                            "Deprecated")                               \
  template(tag_source_debug_extension,                "SourceDebugExtension")                     \
  template(tag_signature,                             "Signature")                                \
  template(tag_runtime_visible_annotations,           "RuntimeVisibleAnnotations")                \
  template(tag_runtime_invisible_annotations,         "RuntimeInvisibleAnnotations")              \
  template(tag_runtime_visible_parameter_annotations, "RuntimeVisibleParameterAnnotations")       \
  template(tag_runtime_invisible_parameter_annotations,"RuntimeInvisibleParameterAnnotations")    \
  template(tag_annotation_default,                    "AnnotationDefault")                        \
  template(tag_enclosing_method,                      "EnclosingMethod")                          \
                                                                                                  \
  /* exception klasses: at least all exceptions thrown by the VM have entries here */             \
  template(java_lang_ArithmeticException,             "java/lang/ArithmeticException")            \
  template(java_lang_ArrayIndexOutOfBoundsException,  "java/lang/ArrayIndexOutOfBoundsException") \
  template(java_lang_ArrayStoreException,             "java/lang/ArrayStoreException")            \
  template(java_lang_ClassCastException,              "java/lang/ClassCastException")             \
  template(java_lang_ClassNotFoundException,          "java/lang/ClassNotFoundException")         \
  template(java_lang_CloneNotSupportedException,      "java/lang/CloneNotSupportedException")     \
  template(java_lang_IllegalAccessException,          "java/lang/IllegalAccessException")         \
  template(java_lang_IllegalArgumentException,        "java/lang/IllegalArgumentException")       \
  template(java_lang_IllegalMonitorStateException,    "java/lang/IllegalMonitorStateException")   \
  template(java_lang_IllegalThreadStateException,     "java/lang/IllegalThreadStateException")    \
  template(java_lang_IndexOutOfBoundsException,       "java/lang/IndexOutOfBoundsException")      \
  template(java_lang_InstantiationException,          "java/lang/InstantiationException")         \
  template(java_lang_InstantiationError,              "java/lang/InstantiationError")             \
  template(java_lang_InterruptedException,            "java/lang/InterruptedException")           \
  template(java_lang_LinkageError,                    "java/lang/LinkageError")                   \
  template(java_lang_NegativeArraySizeException,      "java/lang/NegativeArraySizeException")     \
  template(java_lang_NoSuchFieldException,            "java/lang/NoSuchFieldException")           \
  template(java_lang_NoSuchMethodException,           "java/lang/NoSuchMethodException")          \
  template(java_lang_NullPointerException,            "java/lang/NullPointerException")           \
  template(java_lang_StringIndexOutOfBoundsException, "java/lang/StringIndexOutOfBoundsException")\
  template(java_lang_InvalidClassException,           "java/lang/InvalidClassException")          \
  template(java_lang_reflect_InvocationTargetException, "java/lang/reflect/InvocationTargetException") \
  template(java_lang_Exception,                       "java/lang/Exception")                      \
  template(java_lang_RuntimeException,                "java/lang/RuntimeException")               \
  template(java_io_IOException,                       "java/io/IOException")                      \
  template(java_security_PrivilegedActionException,   "java/security/PrivilegedActionException")  \
                                                                                                  \
  /* error klasses: at least all errors thrown by the VM have entries here */                     \
  template(java_lang_AbstractMethodError,             "java/lang/AbstractMethodError")            \
  template(java_lang_ClassCircularityError,           "java/lang/ClassCircularityError")          \
  template(java_lang_ClassFormatError,                "java/lang/ClassFormatError")               \
  template(java_lang_UnsupportedClassVersionError,    "java/lang/UnsupportedClassVersionError")   \
  template(java_lang_Error,                           "java/lang/Error")                          \
  template(java_lang_ExceptionInInitializerError,     "java/lang/ExceptionInInitializerError")    \
  template(java_lang_IllegalAccessError,              "java/lang/IllegalAccessError")             \
  template(java_lang_IncompatibleClassChangeError,    "java/lang/IncompatibleClassChangeError")   \
  template(java_lang_InternalError,                   "java/lang/InternalError")                  \
  template(java_lang_NoClassDefFoundError,            "java/lang/NoClassDefFoundError")           \
  template(java_lang_NoSuchFieldError,                "java/lang/NoSuchFieldError")               \
  template(java_lang_NoSuchMethodError,               "java/lang/NoSuchMethodError")              \
  template(java_lang_OutOfMemoryError,                "java/lang/OutOfMemoryError")               \
  template(java_lang_UnsatisfiedLinkError,            "java/lang/UnsatisfiedLinkError")           \
  template(java_lang_VerifyError,                     "java/lang/VerifyError")                    \
  template(java_lang_SecurityException,               "java/lang/SecurityException")              \
  template(java_lang_VirtualMachineError,             "java/lang/VirtualMachineError")            \
  template(java_lang_StackOverflowError,              "java/lang/StackOverflowError")             \
  template(java_lang_StackTraceElement,               "java/lang/StackTraceElement")              \
  template(java_util_concurrent_locks_AbstractOwnableSynchronizer,   "java/util/concurrent/locks/AbstractOwnableSynchronizer") \
                                                                                                  \
  /* class symbols needed by intrinsics */                                                        \
  VM_INTRINSICS_DO(VM_INTRINSIC_IGNORE, template, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE) \
                                                                                                  \
  /* Support for reflection based on dynamic bytecode generation (JDK 1.4 and above) */           \
                                                                                                  \
  template(sun_reflect_FieldInfo,                     "sun/reflect/FieldInfo")                    \
  template(sun_reflect_MethodInfo,                    "sun/reflect/MethodInfo")                   \
  template(sun_reflect_MagicAccessorImpl,             "sun/reflect/MagicAccessorImpl")            \
  template(sun_reflect_MethodAccessorImpl,            "sun/reflect/MethodAccessorImpl")           \
  template(sun_reflect_ConstructorAccessorImpl,       "sun/reflect/ConstructorAccessorImpl")      \
  template(sun_reflect_SerializationConstructorAccessorImpl, "sun/reflect/SerializationConstructorAccessorImpl") \
  template(sun_reflect_DelegatingClassLoader,         "sun/reflect/DelegatingClassLoader")        \
  template(sun_reflect_Reflection,                    "sun/reflect/Reflection")                   \
  template(checkedExceptions_name,                    "checkedExceptions")                        \
  template(clazz_name,                                "clazz")                                    \
  template(exceptionTypes_name,                       "exceptionTypes")                           \
  template(modifiers_name,                            "modifiers")                                \
  template(newConstructor_name,                       "newConstructor")                           \
  template(newConstructor_signature,                  "(Lsun/reflect/MethodInfo;)Ljava/lang/reflect/Constructor;") \
  template(newField_name,                             "newField")                                 \
  template(newField_signature,                        "(Lsun/reflect/FieldInfo;)Ljava/lang/reflect/Field;") \
  template(newMethod_name,                            "newMethod")                                \
  template(newMethod_signature,                       "(Lsun/reflect/MethodInfo;)Ljava/lang/reflect/Method;") \
  template(invoke_name,                               "invoke")                                   \
  template(override_name,                             "override")                                 \
  template(parameterTypes_name,                       "parameterTypes")                           \
  template(returnType_name,                           "returnType")                               \
  template(signature_name,                            "signature")                                \
  template(slot_name,                                 "slot")                                     \
                                                                                                  \
  /* Support for annotations (JDK 1.5 and above) */                                               \
                                                                                                  \
  template(annotations_name,                          "annotations")                              \
  template(parameter_annotations_name,                "parameterAnnotations")                     \
  template(annotation_default_name,                   "annotationDefault")                        \
  template(sun_reflect_ConstantPool,                  "sun/reflect/ConstantPool")                 \
  template(constantPoolOop_name,                      "constantPoolOop")                          \
  template(sun_reflect_UnsafeStaticFieldAccessorImpl, "sun/reflect/UnsafeStaticFieldAccessorImpl")\
  template(base_name,                                 "base")                                     \
                                                                                                  \
  /* Support for JSR 292 & invokedynamic (JDK 1.7 and above) */                                   \
  template(java_dyn_MethodHandle,                     "java/dyn/MethodHandle")                    \
  template(java_dyn_MethodType,                       "java/dyn/MethodType")                      \
  template(java_dyn_WrongMethodTypeException,         "java/dyn/WrongMethodTypeException")        \
  template(java_dyn_MethodType_signature,             "Ljava/dyn/MethodType;")                    \
  template(java_dyn_MethodHandle_signature,           "Ljava/dyn/MethodHandle;")                  \
  /* internal classes known only to the JVM: */                                                   \
  template(java_dyn_MethodTypeForm,                   "java/dyn/MethodTypeForm")                  \
  template(java_dyn_MethodTypeForm_signature,         "Ljava/dyn/MethodTypeForm;")                \
  template(sun_dyn_MemberName,                        "sun/dyn/MemberName")                       \
  template(sun_dyn_MethodHandleImpl,                  "sun/dyn/MethodHandleImpl")                 \
  template(sun_dyn_AdapterMethodHandle,               "sun/dyn/AdapterMethodHandle")              \
  template(sun_dyn_BoundMethodHandle,                 "sun/dyn/BoundMethodHandle")                \
  template(sun_dyn_DirectMethodHandle,                "sun/dyn/DirectMethodHandle")               \
  template(makeImpl_name,                             "makeImpl") /*MethodType::makeImpl*/        \
  template(makeImpl_signature,    "(Ljava/lang/Class;[Ljava/lang/Class;ZZ)Ljava/dyn/MethodType;") \
  NOT_LP64(  do_alias(machine_word_signature,         int_signature)  )                           \
  LP64_ONLY( do_alias(machine_word_signature,         long_signature) )                           \
                                                                                                  \
  /* common method and field names */                                                             \
  template(object_initializer_name,                   "<init>")                                   \
  template(class_initializer_name,                    "<clinit>")                                 \
  template(println_name,                              "println")                                  \
  template(printStackTrace_name,                      "printStackTrace")                          \
  template(main_name,                                 "main")                                     \
  template(name_name,                                 "name")                                     \
  template(priority_name,                             "priority")                                 \
  template(stillborn_name,                            "stillborn")                                \
  template(group_name,                                "group")                                    \
  template(daemon_name,                               "daemon")                                   \
  template(eetop_name,                                "eetop")                                    \
  template(thread_status_name,                        "threadStatus")                             \
  template(run_method_name,                           "run")                                      \
  template(exit_method_name,                          "exit")                                     \
  template(add_method_name,                           "add")                                      \
  template(parent_name,                               "parent")                                   \
  template(threads_name,                              "threads")                                  \
  template(groups_name,                               "groups")                                   \
  template(maxPriority_name,                          "maxPriority")                              \
  template(destroyed_name,                            "destroyed")                                \
  template(vmAllowSuspension_name,                    "vmAllowSuspension")                        \
  template(nthreads_name,                             "nthreads")                                 \
  template(ngroups_name,                              "ngroups")                                  \
  template(shutdown_method_name,                      "shutdown")                                 \
  template(finalize_method_name,                      "finalize")                                 \
  template(reference_lock_name,                       "lock")                                     \
  template(reference_discovered_name,                 "discovered")                               \
  template(run_finalizers_on_exit_name,               "runFinalizersOnExit")                      \
  template(uncaughtException_name,                    "uncaughtException")                        \
  template(dispatchUncaughtException_name,            "dispatchUncaughtException")                \
  template(initializeSystemClass_name,                "initializeSystemClass")                    \
  template(loadClass_name,                            "loadClass")                                \
  template(loadClassInternal_name,                    "loadClassInternal")                        \
  template(get_name,                                  "get")                                      \
  template(put_name,                                  "put")                                      \
  template(type_name,                                 "type")                                     \
  template(findNative_name,                           "findNative")                               \
  template(deadChild_name,                            "deadChild")                                \
  template(addClass_name,                             "addClass")                                 \
  template(getFromClass_name,                         "getFromClass")                             \
  template(dispatch_name,                             "dispatch")                                 \
  template(getSystemClassLoader_name,                 "getSystemClassLoader")                     \
  template(fillInStackTrace_name,                     "fillInStackTrace")                         \
  template(getCause_name,                             "getCause")                                 \
  template(initCause_name,                            "initCause")                                \
  template(setProperty_name,                          "setProperty")                              \
  template(getProperty_name,                          "getProperty")                              \
  template(context_name,                              "context")                                  \
  template(privilegedContext_name,                    "privilegedContext")                        \
  template(contextClassLoader_name,                   "contextClassLoader")                       \
  template(inheritedAccessControlContext_name,        "inheritedAccessControlContext")            \
  template(isPrivileged_name,                         "isPrivileged")                             \
  template(wait_name,                                 "wait")                                     \
  template(checkPackageAccess_name,                   "checkPackageAccess")                       \
  template(stackSize_name,                            "stackSize")                                \
  template(thread_id_name,                            "tid")                                      \
  template(newInstance0_name,                         "newInstance0")                             \
  template(limit_name,                                "limit")                                    \
  template(forName_name,                              "forName")                                  \
  template(forName0_name,                             "forName0")                                 \
  template(isJavaIdentifierStart_name,                "isJavaIdentifierStart")                    \
  template(isJavaIdentifierPart_name,                 "isJavaIdentifierPart")                     \
  template(exclusive_owner_thread_name,               "exclusiveOwnerThread")                     \
  template(park_blocker_name,                         "parkBlocker")                              \
  template(park_event_name,                           "nativeParkEventPointer")                   \
  template(cache_field_name,                          "cache")                                    \
  template(value_name,                                "value")                                    \
  template(frontCacheEnabled_name,                    "frontCacheEnabled")                        \
  template(stringCacheEnabled_name,                   "stringCacheEnabled")                       \
  template(bitCount_name,                             "bitCount")                                 \
  template(profile_name,                              "profile")                                  \
  template(equals_name,                               "equals")                                   \
  template(toString_name,                             "toString")                                 \
  template(values_name,                               "values")                                   \
  template(receiver_name,                             "receiver")                                 \
  template(vmtarget_name,                             "vmtarget")                                 \
  template(vmentry_name,                              "vmentry")                                  \
  template(vmslots_name,                              "vmslots")                                  \
  template(vmindex_name,                              "vmindex")                                  \
  template(vmargslot_name,                            "vmargslot")                                \
  template(flags_name,                                "flags")                                    \
  template(argument_name,                             "argument")                                 \
  template(conversion_name,                           "conversion")                               \
  template(rtype_name,                                "rtype")                                    \
  template(ptypes_name,                               "ptypes")                                   \
  template(form_name,                                 "form")                                     \
  template(erasedType_name,                           "erasedType")                               \
                                                                                                  \
  /* non-intrinsic name/signature pairs: */                                                       \
  template(register_method_name,                      "register")                                 \
  do_alias(register_method_signature,         object_void_signature)                              \
                                                                                                  \
  /* name symbols needed by intrinsics */                                                         \
  VM_INTRINSICS_DO(VM_INTRINSIC_IGNORE, VM_SYMBOL_IGNORE, template, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE) \
                                                                                                  \
  /* common signatures names */                                                                   \
  template(void_method_signature,                     "()V")                                      \
  template(void_int_signature,                        "()I")                                      \
  template(void_long_signature,                       "()J")                                      \
  template(void_boolean_signature,                    "()Z")                                      \
  template(int_void_signature,                        "(I)V")                                     \
  template(int_int_signature,                         "(I)I")                                     \
  template(int_bool_signature,                        "(I)Z")                                     \
  template(float_int_signature,                       "(F)I")                                     \
  template(double_long_signature,                     "(D)J")                                     \
  template(double_double_signature,                   "(D)D")                                     \
  template(int_float_signature,                       "(I)F")                                     \
  template(long_int_signature,                        "(J)I")                                     \
  template(long_long_signature,                       "(J)J")                                     \
  template(long_double_signature,                     "(J)D")                                     \
  template(byte_signature,                            "B")                                        \
  template(char_signature,                            "C")                                        \
  template(double_signature,                          "D")                                        \
  template(float_signature,                           "F")                                        \
  template(int_signature,                             "I")                                        \
  template(long_signature,                            "J")                                        \
  template(short_signature,                           "S")                                        \
  template(bool_signature,                            "Z")                                        \
  template(void_signature,                            "V")                                        \
  template(byte_array_signature,                      "[B")                                       \
  template(char_array_signature,                      "[C")                                       \
  template(object_void_signature,                     "(Ljava/lang/Object;)V")                    \
  template(object_int_signature,                      "(Ljava/lang/Object;)I")                    \
  template(object_boolean_signature,                  "(Ljava/lang/Object;)Z")                    \
  template(string_void_signature,                     "(Ljava/lang/String;)V")                    \
  template(string_int_signature,                      "(Ljava/lang/String;)I")                    \
  template(throwable_void_signature,                  "(Ljava/lang/Throwable;)V")                 \
  template(void_throwable_signature,                  "()Ljava/lang/Throwable;")                  \
  template(throwable_throwable_signature,             "(Ljava/lang/Throwable;)Ljava/lang/Throwable;")             \
  template(class_void_signature,                      "(Ljava/lang/Class;)V")                     \
  template(class_int_signature,                       "(Ljava/lang/Class;)I")                     \
  template(class_boolean_signature,                   "(Ljava/lang/Class;)Z")                     \
  template(throwable_string_void_signature,           "(Ljava/lang/Throwable;Ljava/lang/String;)V")               \
  template(string_array_void_signature,               "([Ljava/lang/String;)V")                                   \
  template(string_array_string_array_void_signature,  "([Ljava/lang/String;[Ljava/lang/String;)V")                \
  template(thread_throwable_void_signature,           "(Ljava/lang/Thread;Ljava/lang/Throwable;)V")               \
  template(thread_void_signature,                     "(Ljava/lang/Thread;)V")                                    \
  template(threadgroup_runnable_void_signature,       "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;)V")           \
  template(threadgroup_string_void_signature,         "(Ljava/lang/ThreadGroup;Ljava/lang/String;)V")             \
  template(string_class_signature,                    "(Ljava/lang/String;)Ljava/lang/Class;")                    \
  template(object_object_object_signature,            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;") \
  template(string_string_string_signature,            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;") \
  template(string_string_signature,                   "(Ljava/lang/String;)Ljava/lang/String;")                   \
  template(classloader_string_long_signature,         "(Ljava/lang/ClassLoader;Ljava/lang/String;)J")             \
  template(byte_array_void_signature,                 "([B)V")                                                    \
  template(char_array_void_signature,                 "([C)V")                                                    \
  template(int_int_void_signature,                    "(II)V")                                                    \
  template(long_long_void_signature,                  "(JJ)V")                                                    \
  template(void_classloader_signature,                "()Ljava/lang/ClassLoader;")                                \
  template(void_object_signature,                     "()Ljava/lang/Object;")                                     \
  template(void_class_signature,                      "()Ljava/lang/Class;")                                      \
  template(void_string_signature,                     "()Ljava/lang/String;")                                      \
  template(object_array_object_object_signature,      "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")\
  template(exception_void_signature,                  "(Ljava/lang/Exception;)V")                                 \
  template(protectiondomain_signature,                "[Ljava/security/ProtectionDomain;")                        \
  template(accesscontrolcontext_signature,            "Ljava/security/AccessControlContext;")                     \
  template(class_protectiondomain_signature,          "(Ljava/lang/Class;Ljava/security/ProtectionDomain;)V")     \
  template(thread_signature,                          "Ljava/lang/Thread;")                                       \
  template(thread_array_signature,                    "[Ljava/lang/Thread;")                                      \
  template(threadgroup_signature,                     "Ljava/lang/ThreadGroup;")                                  \
  template(threadgroup_array_signature,               "[Ljava/lang/ThreadGroup;")                                 \
  template(class_array_signature,                     "[Ljava/lang/Class;")                                       \
  template(classloader_signature,                     "Ljava/lang/ClassLoader;")                                  \
  template(object_signature,                          "Ljava/lang/Object;")                                       \
  template(class_signature,                           "Ljava/lang/Class;")                                        \
  template(string_signature,                          "Ljava/lang/String;")                                       \
  template(reference_signature,                       "Ljava/lang/ref/Reference;")                                \
  template(concurrenthashmap_signature,               "Ljava/util/concurrent/ConcurrentHashMap;")                 \
  /* signature symbols needed by intrinsics */                                                                    \
  VM_INTRINSICS_DO(VM_INTRINSIC_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, template, VM_ALIAS_IGNORE)            \
                                                                                                                  \
  /* symbol aliases needed by intrinsics */                                                                       \
  VM_INTRINSICS_DO(VM_INTRINSIC_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, do_alias)           \
                                                                                                                  \
  /* returned by the C1 compiler in case there's not enough memory to allocate a new symbol*/                     \
  template(dummy_symbol_oop,                          "illegal symbol")                                           \
                                                                                                                  \
  /* used by ClassFormatError when class name is not known yet */                                                 \
  template(unknown_class_name,                        "<Unknown>")                                                \
                                                                                                                  \
  /* used to identify class loaders handling parallel class loading */                                            \
  template(parallelCapable_name,                      "parallelLockMap")                                          \
                                                                                                                  \
  /* JVM monitoring and management support */                                                                     \
  template(java_lang_StackTraceElement_array,          "[Ljava/lang/StackTraceElement;")                          \
  template(java_lang_management_ThreadState,           "java/lang/management/ThreadState")                        \
  template(java_lang_management_MemoryUsage,           "java/lang/management/MemoryUsage")                        \
  template(java_lang_management_ThreadInfo,            "java/lang/management/ThreadInfo")                         \
  template(sun_management_ManagementFactory,           "sun/management/ManagementFactory")                        \
  template(sun_management_Sensor,                      "sun/management/Sensor")                                   \
  template(sun_management_Agent,                       "sun/management/Agent")                                    \
  template(createMemoryPoolMBean_name,                 "createMemoryPoolMBean")                                   \
  template(createMemoryManagerMBean_name,              "createMemoryManagerMBean")                                \
  template(createGarbageCollectorMBean_name,           "createGarbageCollectorMBean")                             \
  template(createMemoryPoolMBean_signature,            "(Ljava/lang/String;ZJJ)Ljava/lang/management/MemoryPoolMBean;") \
  template(createMemoryManagerMBean_signature,         "(Ljava/lang/String;)Ljava/lang/management/MemoryManagerMBean;") \
  template(createGarbageCollectorMBean_signature,      "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/management/GarbageCollectorMBean;") \
  template(trigger_name,                               "trigger")                                                 \
  template(clear_name,                                 "clear")                                                   \
  template(trigger_method_signature,                   "(ILjava/lang/management/MemoryUsage;)V")                                                 \
  template(startAgent_name,                            "startAgent")                                              \
  template(java_lang_management_ThreadInfo_constructor_signature, "(Ljava/lang/Thread;ILjava/lang/Object;Ljava/lang/Thread;JJJJ[Ljava/lang/StackTraceElement;)V") \
  template(java_lang_management_ThreadInfo_with_locks_constructor_signature, "(Ljava/lang/Thread;ILjava/lang/Object;Ljava/lang/Thread;JJJJ[Ljava/lang/StackTraceElement;[Ljava/lang/Object;[I[Ljava/lang/Object;)V") \
  template(long_long_long_long_void_signature,         "(JJJJ)V")                                                 \
                                                                                                                  \
  template(java_lang_management_MemoryPoolMXBean,      "java/lang/management/MemoryPoolMXBean")                   \
  template(java_lang_management_MemoryManagerMXBean,   "java/lang/management/MemoryManagerMXBean")                \
  template(java_lang_management_GarbageCollectorMXBean,"java/lang/management/GarbageCollectorMXBean")             \
  template(createMemoryPool_name,                      "createMemoryPool")                                        \
  template(createMemoryManager_name,                   "createMemoryManager")                                     \
  template(createGarbageCollector_name,                "createGarbageCollector")                                  \
  template(createMemoryPool_signature,                 "(Ljava/lang/String;ZJJ)Ljava/lang/management/MemoryPoolMXBean;") \
  template(createMemoryManager_signature,              "(Ljava/lang/String;)Ljava/lang/management/MemoryManagerMXBean;") \
  template(createGarbageCollector_signature,           "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/management/GarbageCollectorMXBean;") \
  template(addThreadDumpForMonitors_name,              "addThreadDumpForMonitors")                                \
  template(addThreadDumpForSynchronizers_name,         "addThreadDumpForSynchronizers")                           \
  template(addThreadDumpForMonitors_signature,         "(Ljava/lang/management/ThreadInfo;[Ljava/lang/Object;[I)V") \
  template(addThreadDumpForSynchronizers_signature,    "(Ljava/lang/management/ThreadInfo;[Ljava/lang/Object;)V")   \
                                                                                                                  \
  /* JVMTI/java.lang.instrument support and VM Attach mechanism */                                                \
  template(sun_misc_VMSupport,                         "sun/misc/VMSupport")                                      \
  template(appendToClassPathForInstrumentation_name,   "appendToClassPathForInstrumentation")                     \
  do_alias(appendToClassPathForInstrumentation_signature, string_void_signature)                                  \
  template(serializePropertiesToByteArray_name,        "serializePropertiesToByteArray")                          \
  template(serializePropertiesToByteArray_signature,   "()[B")                                                    \
  template(serializeAgentPropertiesToByteArray_name,   "serializeAgentPropertiesToByteArray")                     \
  template(classRedefinedCount_name,                   "classRedefinedCount")                                     \
  /*end*/



// Here are all the intrinsics known to the runtime and the CI.
// Each intrinsic consists of a public enum name (like _hashCode),
// followed by a specification of its klass, name, and signature:
//    template(<id>,  <klass>,  <name>, <sig>, <FCODE>)
//
// If you add an intrinsic here, you must also define its name
// and signature as members of the VM symbols.  The VM symbols for
// the intrinsic name and signature may be defined above.
//
// Because the VM_SYMBOLS_DO macro makes reference to VM_INTRINSICS_DO,
// you can also define an intrinsic's name and/or signature locally to the
// intrinsic, if this makes sense.  (It often does make sense.)
//
// For example:
//    do_intrinsic(_foo,  java_lang_Object,  foo_name, foo_signature, F_xx)
//     do_name(     foo_name, "foo")
//     do_signature(foo_signature, "()F")
// klass      = vmSymbols::java_lang_Object()
// name       = vmSymbols::foo_name()
// signature  = vmSymbols::foo_signature()
//
// The name and/or signature might be a "well known" symbol
// like "equal" or "()I", in which case there will be no local
// re-definition of the symbol.
//
// The do_class, do_name, and do_signature calls are all used for the
// same purpose:  Define yet another VM symbol.  They could all be merged
// into a common 'do_symbol' call, but it seems useful to record our
// intentions here about kinds of symbols (class vs. name vs. signature).
//
// The F_xx is one of the Flags enum; see below.
//
// for Emacs: (let ((c-backslash-column 120) (c-backslash-max-column 120)) (c-backslash-region (point) (point-max) nil t))
#define VM_INTRINSICS_DO(do_intrinsic, do_class, do_name, do_signature, do_alias)                                       \
  do_intrinsic(_Object_init,              java_lang_Object, object_initializer_name, void_method_signature,      F_R)   \
  /*    (symbol object_initializer_name defined above) */                                                               \
                                                                                                                        \
  do_intrinsic(_hashCode,                 java_lang_Object,       hashCode_name, void_int_signature,             F_R)   \
   do_name(     hashCode_name,                                   "hashCode")                                            \
  do_intrinsic(_getClass,                 java_lang_Object,       getClass_name, void_class_signature,           F_R)   \
   do_name(     getClass_name,                                   "getClass")                                            \
  do_intrinsic(_clone,                    java_lang_Object,       clone_name, void_object_signature,             F_R)   \
   do_name(     clone_name,                                      "clone")                                               \
                                                                                                                        \
  /* Math & StrictMath intrinsics are defined in terms of just a few signatures: */                                     \
  do_class(java_lang_Math,                "java/lang/Math")                                                             \
  do_class(java_lang_StrictMath,          "java/lang/StrictMath")                                                       \
  do_signature(double2_double_signature,  "(DD)D")                                                                      \
  do_signature(int2_int_signature,        "(II)I")                                                                      \
                                                                                                                        \
  /* here are the math names, all together: */                                                                          \
  do_name(abs_name,"abs")       do_name(sin_name,"sin")         do_name(cos_name,"cos")                                 \
  do_name(tan_name,"tan")       do_name(atan2_name,"atan2")     do_name(sqrt_name,"sqrt")                               \
  do_name(log_name,"log")       do_name(log10_name,"log10")     do_name(pow_name,"pow")                                 \
  do_name(exp_name,"exp")       do_name(min_name,"min")         do_name(max_name,"max")                                 \
                                                                                                                        \
  do_intrinsic(_dabs,                     java_lang_Math,         abs_name,   double_double_signature,           F_S)   \
  do_intrinsic(_dsin,                     java_lang_Math,         sin_name,   double_double_signature,           F_S)   \
  do_intrinsic(_dcos,                     java_lang_Math,         cos_name,   double_double_signature,           F_S)   \
  do_intrinsic(_dtan,                     java_lang_Math,         tan_name,   double_double_signature,           F_S)   \
  do_intrinsic(_datan2,                   java_lang_Math,         atan2_name, double2_double_signature,          F_S)   \
  do_intrinsic(_dsqrt,                    java_lang_Math,         sqrt_name,  double_double_signature,           F_S)   \
  do_intrinsic(_dlog,                     java_lang_Math,         log_name,   double_double_signature,           F_S)   \
  do_intrinsic(_dlog10,                   java_lang_Math,         log10_name, double_double_signature,           F_S)   \
  do_intrinsic(_dpow,                     java_lang_Math,         pow_name,   double2_double_signature,          F_S)   \
  do_intrinsic(_dexp,                     java_lang_Math,         exp_name,   double_double_signature,           F_S)   \
  do_intrinsic(_min,                      java_lang_Math,         min_name,   int2_int_signature,                F_S)   \
  do_intrinsic(_max,                      java_lang_Math,         max_name,   int2_int_signature,                F_S)   \
                                                                                                                        \
  do_intrinsic(_floatToRawIntBits,        java_lang_Float,        floatToRawIntBits_name,   float_int_signature, F_S)   \
   do_name(     floatToRawIntBits_name,                          "floatToRawIntBits")                                   \
  do_intrinsic(_floatToIntBits,           java_lang_Float,        floatToIntBits_name,      float_int_signature, F_S)   \
   do_name(     floatToIntBits_name,                             "floatToIntBits")                                      \
  do_intrinsic(_intBitsToFloat,           java_lang_Float,        intBitsToFloat_name,      int_float_signature, F_S)   \
   do_name(     intBitsToFloat_name,                             "intBitsToFloat")                                      \
  do_intrinsic(_doubleToRawLongBits,      java_lang_Double,       doubleToRawLongBits_name, double_long_signature, F_S) \
   do_name(     doubleToRawLongBits_name,                        "doubleToRawLongBits")                                 \
  do_intrinsic(_doubleToLongBits,         java_lang_Double,       doubleToLongBits_name,    double_long_signature, F_S) \
   do_name(     doubleToLongBits_name,                           "doubleToLongBits")                                    \
  do_intrinsic(_longBitsToDouble,         java_lang_Double,       longBitsToDouble_name,    long_double_signature, F_S) \
   do_name(     longBitsToDouble_name,                           "longBitsToDouble")                                    \
                                                                                                                        \
  do_intrinsic(_bitCount_i,               java_lang_Integer,      bitCount_name,            int_int_signature,   F_S)   \
  do_intrinsic(_bitCount_l,               java_lang_Long,         bitCount_name,            long_int_signature,  F_S)   \
                                                                                                                        \
  do_intrinsic(_reverseBytes_i,           java_lang_Integer,      reverseBytes_name,        int_int_signature,   F_S)   \
   do_name(     reverseBytes_name,                               "reverseBytes")                                        \
  do_intrinsic(_reverseBytes_l,           java_lang_Long,         reverseBytes_name,        long_long_signature, F_S)   \
    /*  (symbol reverseBytes_name defined above) */                                                                     \
                                                                                                                        \
  do_intrinsic(_identityHashCode,         java_lang_System,       identityHashCode_name, object_int_signature,   F_S)   \
   do_name(     identityHashCode_name,                           "identityHashCode")                                    \
  do_intrinsic(_currentTimeMillis,        java_lang_System,       currentTimeMillis_name, void_long_signature,   F_S)   \
                                                                                                                        \
   do_name(     currentTimeMillis_name,                          "currentTimeMillis")                                   \
  do_intrinsic(_nanoTime,                 java_lang_System,       nanoTime_name,          void_long_signature,   F_S)   \
   do_name(     nanoTime_name,                                   "nanoTime")                                            \
                                                                                                                        \
  do_intrinsic(_arraycopy,                java_lang_System,       arraycopy_name, arraycopy_signature,           F_S)   \
   do_name(     arraycopy_name,                                  "arraycopy")                                           \
   do_signature(arraycopy_signature,                             "(Ljava/lang/Object;ILjava/lang/Object;II)V")          \
  do_intrinsic(_isInterrupted,            java_lang_Thread,       isInterrupted_name, isInterrupted_signature,   F_R)   \
   do_name(     isInterrupted_name,                              "isInterrupted")                                       \
   do_signature(isInterrupted_signature,                         "(Z)Z")                                                \
  do_intrinsic(_currentThread,            java_lang_Thread,       currentThread_name, currentThread_signature,   F_S)   \
   do_name(     currentThread_name,                              "currentThread")                                       \
   do_signature(currentThread_signature,                         "()Ljava/lang/Thread;")                                \
                                                                                                                        \
  /* reflective intrinsics, for java/lang/Class, etc. */                                                                \
  do_intrinsic(_isAssignableFrom,         java_lang_Class,        isAssignableFrom_name, class_boolean_signature, F_RN) \
   do_name(     isAssignableFrom_name,                           "isAssignableFrom")                                    \
  do_intrinsic(_isInstance,               java_lang_Class,        isInstance_name, object_boolean_signature,     F_RN)  \
   do_name(     isInstance_name,                                 "isInstance")                                          \
  do_intrinsic(_getModifiers,             java_lang_Class,        getModifiers_name, void_int_signature,         F_RN)  \
   do_name(     getModifiers_name,                               "getModifiers")                                        \
  do_intrinsic(_isInterface,              java_lang_Class,        isInterface_name, void_boolean_signature,      F_RN)  \
   do_name(     isInterface_name,                                "isInterface")                                         \
  do_intrinsic(_isArray,                  java_lang_Class,        isArray_name, void_boolean_signature,          F_RN)  \
   do_name(     isArray_name,                                    "isArray")                                             \
  do_intrinsic(_isPrimitive,              java_lang_Class,        isPrimitive_name, void_boolean_signature,      F_RN)  \
   do_name(     isPrimitive_name,                                "isPrimitive")                                         \
  do_intrinsic(_getSuperclass,            java_lang_Class,        getSuperclass_name, void_class_signature,      F_RN)  \
   do_name(     getSuperclass_name,                              "getSuperclass")                                       \
  do_intrinsic(_getComponentType,         java_lang_Class,        getComponentType_name, void_class_signature,   F_RN)  \
   do_name(     getComponentType_name,                           "getComponentType")                                    \
                                                                                                                        \
  do_intrinsic(_getClassAccessFlags,      sun_reflect_Reflection, getClassAccessFlags_name, class_int_signature, F_SN)  \
   do_name(     getClassAccessFlags_name,                        "getClassAccessFlags")                                 \
  do_intrinsic(_getLength,                java_lang_reflect_Array, getLength_name, object_int_signature,         F_SN)  \
   do_name(     getLength_name,                                   "getLength")                                          \
                                                                                                                        \
  do_intrinsic(_getCallerClass,           sun_reflect_Reflection, getCallerClass_name, getCallerClass_signature, F_SN)  \
   do_name(     getCallerClass_name,                             "getCallerClass")                                      \
   do_signature(getCallerClass_signature,                        "(I)Ljava/lang/Class;")                                \
                                                                                                                        \
  do_intrinsic(_newArray,                 java_lang_reflect_Array, newArray_name, newArray_signature,            F_SN)  \
   do_name(     newArray_name,                                    "newArray")                                           \
   do_signature(newArray_signature,                               "(Ljava/lang/Class;I)Ljava/lang/Object;")             \
                                                                                                                        \
  do_intrinsic(_copyOf,                   java_util_Arrays,       copyOf_name, copyOf_signature,                 F_S)   \
   do_name(     copyOf_name,                                     "copyOf")                                              \
   do_signature(copyOf_signature,             "([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;")             \
                                                                                                                        \
  do_intrinsic(_copyOfRange,              java_util_Arrays,       copyOfRange_name, copyOfRange_signature,       F_S)   \
   do_name(     copyOfRange_name,                                "copyOfRange")                                         \
   do_signature(copyOfRange_signature,        "([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;")            \
                                                                                                                        \
  do_intrinsic(_equalsC,                  java_util_Arrays,       equals_name,    equalsC_signature,             F_S)   \
   do_signature(equalsC_signature,                               "([C[C)Z")                                             \
                                                                                                                        \
  do_intrinsic(_invoke,                   java_lang_reflect_Method, invoke_name, object_array_object_object_signature, F_R) \
  /*   (symbols invoke_name and invoke_signature defined above) */                                                      \
                                                                                                                        \
  do_intrinsic(_compareTo,                java_lang_String,       compareTo_name, string_int_signature,          F_R)   \
   do_name(     compareTo_name,                                  "compareTo")                                           \
  do_intrinsic(_indexOf,                  java_lang_String,       indexOf_name, string_int_signature,            F_R)   \
   do_name(     indexOf_name,                                    "indexOf")                                             \
  do_intrinsic(_equals,                   java_lang_String,       equals_name, object_boolean_signature,         F_R)   \
                                                                                                                        \
  do_class(java_nio_Buffer,               "java/nio/Buffer")                                                            \
  do_intrinsic(_checkIndex,               java_nio_Buffer,        checkIndex_name, int_int_signature,            F_R)   \
   do_name(     checkIndex_name,                                 "checkIndex")                                          \
                                                                                                                        \
  do_class(sun_misc_AtomicLongCSImpl,     "sun/misc/AtomicLongCSImpl")                                                  \
  do_intrinsic(_get_AtomicLong,           sun_misc_AtomicLongCSImpl, get_name, void_long_signature,              F_R)   \
  /*   (symbols get_name and void_long_signature defined above) */                                                      \
                                                                                                                        \
  do_intrinsic(_attemptUpdate,            sun_misc_AtomicLongCSImpl, attemptUpdate_name, attemptUpdate_signature, F_R)  \
   do_name(     attemptUpdate_name,                                 "attemptUpdate")                                    \
   do_signature(attemptUpdate_signature,                            "(JJ)Z")                                            \
                                                                                                                        \
  do_intrinsic(_fillInStackTrace,         java_lang_Throwable, fillInStackTrace_name, void_throwable_signature,  F_RNY) \
                                                                                                                        \
  /* support for sun.misc.Unsafe */                                                                                     \
  do_class(sun_misc_Unsafe,               "sun/misc/Unsafe")                                                            \
                                                                                                                        \
  do_intrinsic(_allocateInstance,         sun_misc_Unsafe,        allocateInstance_name, allocateInstance_signature, F_RN) \
   do_name(     allocateInstance_name,                           "allocateInstance")                                    \
   do_signature(allocateInstance_signature,   "(Ljava/lang/Class;)Ljava/lang/Object;")                                  \
  do_intrinsic(_copyMemory,               sun_misc_Unsafe,        copyMemory_name, copyMemory_signature,         F_RN)  \
   do_name(     copyMemory_name,                                 "copyMemory")                                          \
   do_signature(copyMemory_signature,         "(Ljava/lang/Object;JLjava/lang/Object;JJ)V")                             \
  do_intrinsic(_park,                     sun_misc_Unsafe,        park_name, park_signature,                     F_RN)  \
   do_name(     park_name,                                       "park")                                                \
   do_signature(park_signature,                                  "(ZJ)V")                                               \
  do_intrinsic(_unpark,                   sun_misc_Unsafe,        unpark_name, unpark_signature,                 F_RN)  \
   do_name(     unpark_name,                                     "unpark")                                              \
   do_alias(    unpark_signature,                               /*(LObject;)V*/ object_void_signature)                  \
                                                                                                                        \
  /* unsafe memory references (there are a lot of them...) */                                                           \
  do_signature(getObject_signature,       "(Ljava/lang/Object;J)Ljava/lang/Object;")                                    \
  do_signature(putObject_signature,       "(Ljava/lang/Object;JLjava/lang/Object;)V")                                   \
  do_signature(getBoolean_signature,      "(Ljava/lang/Object;J)Z")                                                     \
  do_signature(putBoolean_signature,      "(Ljava/lang/Object;JZ)V")                                                    \
  do_signature(getByte_signature,         "(Ljava/lang/Object;J)B")                                                     \
  do_signature(putByte_signature,         "(Ljava/lang/Object;JB)V")                                                    \
  do_signature(getShort_signature,        "(Ljava/lang/Object;J)S")                                                     \
  do_signature(putShort_signature,        "(Ljava/lang/Object;JS)V")                                                    \
  do_signature(getChar_signature,         "(Ljava/lang/Object;J)C")                                                     \
  do_signature(putChar_signature,         "(Ljava/lang/Object;JC)V")                                                    \
  do_signature(getInt_signature,          "(Ljava/lang/Object;J)I")                                                     \
  do_signature(putInt_signature,          "(Ljava/lang/Object;JI)V")                                                    \
  do_signature(getLong_signature,         "(Ljava/lang/Object;J)J")                                                     \
  do_signature(putLong_signature,         "(Ljava/lang/Object;JJ)V")                                                    \
  do_signature(getFloat_signature,        "(Ljava/lang/Object;J)F")                                                     \
  do_signature(putFloat_signature,        "(Ljava/lang/Object;JF)V")                                                    \
  do_signature(getDouble_signature,       "(Ljava/lang/Object;J)D")                                                     \
  do_signature(putDouble_signature,       "(Ljava/lang/Object;JD)V")                                                    \
                                                                                                                        \
  do_name(getObject_name,"getObject")           do_name(putObject_name,"putObject")                                     \
  do_name(getBoolean_name,"getBoolean")         do_name(putBoolean_name,"putBoolean")                                   \
  do_name(getByte_name,"getByte")               do_name(putByte_name,"putByte")                                         \
  do_name(getShort_name,"getShort")             do_name(putShort_name,"putShort")                                       \
  do_name(getChar_name,"getChar")               do_name(putChar_name,"putChar")                                         \
  do_name(getInt_name,"getInt")                 do_name(putInt_name,"putInt")                                           \
  do_name(getLong_name,"getLong")               do_name(putLong_name,"putLong")                                         \
  do_name(getFloat_name,"getFloat")             do_name(putFloat_name,"putFloat")                                       \
  do_name(getDouble_name,"getDouble")           do_name(putDouble_name,"putDouble")                                     \
                                                                                                                        \
  do_intrinsic(_getObject,                sun_misc_Unsafe,        getObject_name, getObject_signature,           F_RN)  \
  do_intrinsic(_getBoolean,               sun_misc_Unsafe,        getBoolean_name, getBoolean_signature,         F_RN)  \
  do_intrinsic(_getByte,                  sun_misc_Unsafe,        getByte_name, getByte_signature,               F_RN)  \
  do_intrinsic(_getShort,                 sun_misc_Unsafe,        getShort_name, getShort_signature,             F_RN)  \
  do_intrinsic(_getChar,                  sun_misc_Unsafe,        getChar_name, getChar_signature,               F_RN)  \
  do_intrinsic(_getInt,                   sun_misc_Unsafe,        getInt_name, getInt_signature,                 F_RN)  \
  do_intrinsic(_getLong,                  sun_misc_Unsafe,        getLong_name, getLong_signature,               F_RN)  \
  do_intrinsic(_getFloat,                 sun_misc_Unsafe,        getFloat_name, getFloat_signature,             F_RN)  \
  do_intrinsic(_getDouble,                sun_misc_Unsafe,        getDouble_name, getDouble_signature,           F_RN)  \
  do_intrinsic(_putObject,                sun_misc_Unsafe,        putObject_name, putObject_signature,           F_RN)  \
  do_intrinsic(_putBoolean,               sun_misc_Unsafe,        putBoolean_name, putBoolean_signature,         F_RN)  \
  do_intrinsic(_putByte,                  sun_misc_Unsafe,        putByte_name, putByte_signature,               F_RN)  \
  do_intrinsic(_putShort,                 sun_misc_Unsafe,        putShort_name, putShort_signature,             F_RN)  \
  do_intrinsic(_putChar,                  sun_misc_Unsafe,        putChar_name, putChar_signature,               F_RN)  \
  do_intrinsic(_putInt,                   sun_misc_Unsafe,        putInt_name, putInt_signature,                 F_RN)  \
  do_intrinsic(_putLong,                  sun_misc_Unsafe,        putLong_name, putLong_signature,               F_RN)  \
  do_intrinsic(_putFloat,                 sun_misc_Unsafe,        putFloat_name, putFloat_signature,             F_RN)  \
  do_intrinsic(_putDouble,                sun_misc_Unsafe,        putDouble_name, putDouble_signature,           F_RN)  \
                                                                                                                        \
  do_name(getObjectVolatile_name,"getObjectVolatile")   do_name(putObjectVolatile_name,"putObjectVolatile")             \
  do_name(getBooleanVolatile_name,"getBooleanVolatile") do_name(putBooleanVolatile_name,"putBooleanVolatile")           \
  do_name(getByteVolatile_name,"getByteVolatile")       do_name(putByteVolatile_name,"putByteVolatile")                 \
  do_name(getShortVolatile_name,"getShortVolatile")     do_name(putShortVolatile_name,"putShortVolatile")               \
  do_name(getCharVolatile_name,"getCharVolatile")       do_name(putCharVolatile_name,"putCharVolatile")                 \
  do_name(getIntVolatile_name,"getIntVolatile")         do_name(putIntVolatile_name,"putIntVolatile")                   \
  do_name(getLongVolatile_name,"getLongVolatile")       do_name(putLongVolatile_name,"putLongVolatile")                 \
  do_name(getFloatVolatile_name,"getFloatVolatile")     do_name(putFloatVolatile_name,"putFloatVolatile")               \
  do_name(getDoubleVolatile_name,"getDoubleVolatile")   do_name(putDoubleVolatile_name,"putDoubleVolatile")             \
                                                                                                                        \
  do_intrinsic(_getObjectVolatile,        sun_misc_Unsafe,        getObjectVolatile_name, getObject_signature,   F_RN)  \
  do_intrinsic(_getBooleanVolatile,       sun_misc_Unsafe,        getBooleanVolatile_name, getBoolean_signature, F_RN)  \
  do_intrinsic(_getByteVolatile,          sun_misc_Unsafe,        getByteVolatile_name, getByte_signature,       F_RN)  \
  do_intrinsic(_getShortVolatile,         sun_misc_Unsafe,        getShortVolatile_name, getShort_signature,     F_RN)  \
  do_intrinsic(_getCharVolatile,          sun_misc_Unsafe,        getCharVolatile_name, getChar_signature,       F_RN)  \
  do_intrinsic(_getIntVolatile,           sun_misc_Unsafe,        getIntVolatile_name, getInt_signature,         F_RN)  \
  do_intrinsic(_getLongVolatile,          sun_misc_Unsafe,        getLongVolatile_name, getLong_signature,       F_RN)  \
  do_intrinsic(_getFloatVolatile,         sun_misc_Unsafe,        getFloatVolatile_name, getFloat_signature,     F_RN)  \
  do_intrinsic(_getDoubleVolatile,        sun_misc_Unsafe,        getDoubleVolatile_name, getDouble_signature,   F_RN)  \
  do_intrinsic(_putObjectVolatile,        sun_misc_Unsafe,        putObjectVolatile_name, putObject_signature,   F_RN)  \
  do_intrinsic(_putBooleanVolatile,       sun_misc_Unsafe,        putBooleanVolatile_name, putBoolean_signature, F_RN)  \
  do_intrinsic(_putByteVolatile,          sun_misc_Unsafe,        putByteVolatile_name, putByte_signature,       F_RN)  \
  do_intrinsic(_putShortVolatile,         sun_misc_Unsafe,        putShortVolatile_name, putShort_signature,     F_RN)  \
  do_intrinsic(_putCharVolatile,          sun_misc_Unsafe,        putCharVolatile_name, putChar_signature,       F_RN)  \
  do_intrinsic(_putIntVolatile,           sun_misc_Unsafe,        putIntVolatile_name, putInt_signature,         F_RN)  \
  do_intrinsic(_putLongVolatile,          sun_misc_Unsafe,        putLongVolatile_name, putLong_signature,       F_RN)  \
  do_intrinsic(_putFloatVolatile,         sun_misc_Unsafe,        putFloatVolatile_name, putFloat_signature,     F_RN)  \
  do_intrinsic(_putDoubleVolatile,        sun_misc_Unsafe,        putDoubleVolatile_name, putDouble_signature,   F_RN)  \
                                                                                                                        \
  /* %%% these are redundant except perhaps for getAddress, but Unsafe has native methods for them */                   \
  do_signature(getByte_raw_signature,     "(J)B")                                                                       \
  do_signature(putByte_raw_signature,     "(JB)V")                                                                      \
  do_signature(getShort_raw_signature,    "(J)S")                                                                       \
  do_signature(putShort_raw_signature,    "(JS)V")                                                                      \
  do_signature(getChar_raw_signature,     "(J)C")                                                                       \
  do_signature(putChar_raw_signature,     "(JC)V")                                                                      \
  do_signature(putInt_raw_signature,      "(JI)V")                                                                      \
      do_alias(getLong_raw_signature,    /*(J)J*/ long_long_signature)                                                  \
      do_alias(putLong_raw_signature,    /*(JJ)V*/ long_long_void_signature)                                            \
  do_signature(getFloat_raw_signature,    "(J)F")                                                                       \
  do_signature(putFloat_raw_signature,    "(JF)V")                                                                      \
      do_alias(getDouble_raw_signature,  /*(J)D*/ long_double_signature)                                                \
  do_signature(putDouble_raw_signature,   "(JD)V")                                                                      \
      do_alias(getAddress_raw_signature, /*(J)J*/ long_long_signature)                                                  \
      do_alias(putAddress_raw_signature, /*(JJ)V*/ long_long_void_signature)                                            \
                                                                                                                        \
   do_name(    getAddress_name,           "getAddress")                                                                 \
   do_name(    putAddress_name,           "putAddress")                                                                 \
                                                                                                                        \
  do_intrinsic(_getByte_raw,              sun_misc_Unsafe,        getByte_name, getByte_raw_signature,           F_RN)  \
  do_intrinsic(_getShort_raw,             sun_misc_Unsafe,        getShort_name, getShort_raw_signature,         F_RN)  \
  do_intrinsic(_getChar_raw,              sun_misc_Unsafe,        getChar_name, getChar_raw_signature,           F_RN)  \
  do_intrinsic(_getInt_raw,               sun_misc_Unsafe,        getInt_name, long_int_signature,               F_RN)  \
  do_intrinsic(_getLong_raw,              sun_misc_Unsafe,        getLong_name, getLong_raw_signature,           F_RN)  \
  do_intrinsic(_getFloat_raw,             sun_misc_Unsafe,        getFloat_name, getFloat_raw_signature,         F_RN)  \
  do_intrinsic(_getDouble_raw,            sun_misc_Unsafe,        getDouble_name, getDouble_raw_signature,       F_RN)  \
  do_intrinsic(_getAddress_raw,           sun_misc_Unsafe,        getAddress_name, getAddress_raw_signature,     F_RN)  \
  do_intrinsic(_putByte_raw,              sun_misc_Unsafe,        putByte_name, putByte_raw_signature,           F_RN)  \
  do_intrinsic(_putShort_raw,             sun_misc_Unsafe,        putShort_name, putShort_raw_signature,         F_RN)  \
  do_intrinsic(_putChar_raw,              sun_misc_Unsafe,        putChar_name, putChar_raw_signature,           F_RN)  \
  do_intrinsic(_putInt_raw,               sun_misc_Unsafe,        putInt_name, putInt_raw_signature,             F_RN)  \
  do_intrinsic(_putLong_raw,              sun_misc_Unsafe,        putLong_name, putLong_raw_signature,           F_RN)  \
  do_intrinsic(_putFloat_raw,             sun_misc_Unsafe,        putFloat_name, putFloat_raw_signature,         F_RN)  \
  do_intrinsic(_putDouble_raw,            sun_misc_Unsafe,        putDouble_name, putDouble_raw_signature,       F_RN)  \
  do_intrinsic(_putAddress_raw,           sun_misc_Unsafe,        putAddress_name, putAddress_raw_signature,     F_RN)  \
                                                                                                                        \
  do_intrinsic(_compareAndSwapObject,     sun_misc_Unsafe,        compareAndSwapObject_name, compareAndSwapObject_signature, F_RN) \
   do_name(     compareAndSwapObject_name,                       "compareAndSwapObject")                                \
   do_signature(compareAndSwapObject_signature,  "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z")          \
  do_intrinsic(_compareAndSwapLong,       sun_misc_Unsafe,        compareAndSwapLong_name, compareAndSwapLong_signature, F_RN) \
   do_name(     compareAndSwapLong_name,                         "compareAndSwapLong")                                  \
   do_signature(compareAndSwapLong_signature,                    "(Ljava/lang/Object;JJJ)Z")                            \
  do_intrinsic(_compareAndSwapInt,        sun_misc_Unsafe,        compareAndSwapInt_name, compareAndSwapInt_signature, F_RN) \
   do_name(     compareAndSwapInt_name,                          "compareAndSwapInt")                                   \
   do_signature(compareAndSwapInt_signature,                     "(Ljava/lang/Object;JII)Z")                            \
  do_intrinsic(_putOrderedObject,         sun_misc_Unsafe,        putOrderedObject_name, putOrderedObject_signature, F_RN) \
   do_name(     putOrderedObject_name,                           "putOrderedObject")                                    \
   do_alias(    putOrderedObject_signature,                     /*(LObject;JLObject;)V*/ putObject_signature)           \
  do_intrinsic(_putOrderedLong,           sun_misc_Unsafe,        putOrderedLong_name, putOrderedLong_signature, F_RN)  \
   do_name(     putOrderedLong_name,                             "putOrderedLong")                                      \
   do_alias(    putOrderedLong_signature,                       /*(Ljava/lang/Object;JJ)V*/ putLong_signature)          \
  do_intrinsic(_putOrderedInt,            sun_misc_Unsafe,        putOrderedInt_name, putOrderedInt_signature,   F_RN)  \
   do_name(     putOrderedInt_name,                              "putOrderedInt")                                       \
   do_alias(    putOrderedInt_signature,                        /*(Ljava/lang/Object;JI)V*/ putInt_signature)           \
                                                                                                                        \
  /* prefetch_signature is shared by all prefetch variants */                                                           \
  do_signature( prefetch_signature,        "(Ljava/lang/Object;J)V")                                                    \
                                                                                                                        \
  do_intrinsic(_prefetchRead,             sun_misc_Unsafe,        prefetchRead_name, prefetch_signature,         F_RN)  \
   do_name(     prefetchRead_name,                               "prefetchRead")                                        \
  do_intrinsic(_prefetchWrite,            sun_misc_Unsafe,        prefetchWrite_name, prefetch_signature,        F_RN)  \
   do_name(     prefetchWrite_name,                              "prefetchWrite")                                       \
  do_intrinsic(_prefetchReadStatic,       sun_misc_Unsafe,        prefetchReadStatic_name, prefetch_signature,   F_SN)  \
   do_name(     prefetchReadStatic_name,                         "prefetchReadStatic")                                  \
  do_intrinsic(_prefetchWriteStatic,      sun_misc_Unsafe,        prefetchWriteStatic_name, prefetch_signature,  F_SN)  \
   do_name(     prefetchWriteStatic_name,                        "prefetchWriteStatic")                                 \
    /*end*/



// Class vmSymbols

class vmSymbols: AllStatic {
 friend class vmSymbolHandles;
 friend class vmIntrinsics;
 public:
  // enum for figuring positions and size of array holding symbolOops
  enum SID {
    NO_SID = 0,

    #define VM_SYMBOL_ENUM(name, string) VM_SYMBOL_ENUM_NAME(name),
    VM_SYMBOLS_DO(VM_SYMBOL_ENUM, VM_ALIAS_IGNORE)
    #undef VM_SYMBOL_ENUM

    SID_LIMIT,

    #define VM_ALIAS_ENUM(name, def) VM_SYMBOL_ENUM_NAME(name) = VM_SYMBOL_ENUM_NAME(def),
    VM_SYMBOLS_DO(VM_SYMBOL_IGNORE, VM_ALIAS_ENUM)
    #undef VM_ALIAS_ENUM

    FIRST_SID = NO_SID + 1
  };
  enum {
    log2_SID_LIMIT = 10         // checked by an assert at start-up
  };

 private:
  // The symbol array
  static symbolOop _symbols[];

  // Field signatures indexed by BasicType.
  static symbolOop _type_signatures[T_VOID+1];

 public:
  // Initialization
  static void initialize(TRAPS);
  // Accessing
  #define VM_SYMBOL_DECLARE(name, ignore) \
    static symbolOop name() { return _symbols[VM_SYMBOL_ENUM_NAME(name)]; }
  VM_SYMBOLS_DO(VM_SYMBOL_DECLARE, VM_SYMBOL_DECLARE)
  #undef VM_SYMBOL_DECLARE

  // GC support
  static void oops_do(OopClosure* f, bool do_all = false);

  static symbolOop type_signature(BasicType t) {
    assert((uint)t < T_VOID+1, "range check");
    assert(_type_signatures[t] != NULL, "domain check");
    return _type_signatures[t];
  }
  // inverse of type_signature; returns T_OBJECT if s is not recognized
  static BasicType signature_type(symbolOop s);

  static symbolOop symbol_at(SID id) {
    assert(id >= FIRST_SID && id < SID_LIMIT, "oob");
    assert(_symbols[id] != NULL, "init");
    return _symbols[id];
  }

  // Returns symbol's SID if one is assigned, else NO_SID.
  static SID find_sid(symbolOop symbol);

#ifndef PRODUCT
  // No need for this in the product:
  static const char* name_for(SID sid);
#endif //PRODUCT
};


// Class vmSymbolHandles

class vmSymbolHandles: AllStatic {
  friend class vmIntrinsics;
  friend class ciObjectFactory;

 public:
  // Accessing
  #define VM_SYMBOL_HANDLE_DECLARE(name, ignore) \
    static symbolHandle name() { return symbol_handle_at(vmSymbols::VM_SYMBOL_ENUM_NAME(name)); }
  VM_SYMBOLS_DO(VM_SYMBOL_HANDLE_DECLARE, VM_SYMBOL_HANDLE_DECLARE)
  #undef VM_SYMBOL_HANDLE_DECLARE

  static symbolHandle symbol_handle_at(vmSymbols::SID id) {
    return symbolHandle(&vmSymbols::_symbols[(int)id], false);
  }

  static symbolHandle type_signature(BasicType t) {
    assert(vmSymbols::type_signature(t) != NULL, "domain check");
    return symbolHandle(&vmSymbols::_type_signatures[t], false);
  }
  // inverse of type_signature; returns T_OBJECT if s is not recognized
  static BasicType signature_type(symbolHandle s) {
    return vmSymbols::signature_type(s());
  }
};

// VM Intrinsic ID's uniquely identify some very special methods
class vmIntrinsics: AllStatic {
  friend class vmSymbols;
  friend class ciObjectFactory;

 public:
  // Accessing
  enum ID {
    _none = 0,                      // not an intrinsic (default answer)

    #define VM_INTRINSIC_ENUM(id, klass, name, sig, flags)  id,
    VM_INTRINSICS_DO(VM_INTRINSIC_ENUM,
                     VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE)
    #undef VM_INTRINSIC_ENUM

    ID_LIMIT,
    FIRST_ID = _none + 1
  };

  enum Flags {
    // AccessFlags syndromes relevant to intrinsics.
    F_none = 0,
    F_R,                        // !static        !synchronized (R="regular")
    F_S,                        //  static        !synchronized
    F_RN,                       // !static native !synchronized
    F_SN,                       //  static native !synchronized
    F_RNY                       // !static native  synchronized
  };

public:
  static ID ID_from(int raw_id) {
    assert(raw_id >= (int)_none && raw_id < (int)ID_LIMIT,
           "must be a valid intrinsic ID");
    return (ID)raw_id;
  }

  static const char* name_at(ID id);

  // Given a method's class, name, signature, and access flags, report its ID.
  static ID find_id(vmSymbols::SID holder,
                    vmSymbols::SID name,
                    vmSymbols::SID sig,
                    jshort flags);

  static void verify_method(ID actual_id, methodOop m) PRODUCT_RETURN;

  // No need for these in the product:
  static vmSymbols::SID     class_for(ID id);
  static vmSymbols::SID      name_for(ID id);
  static vmSymbols::SID signature_for(ID id);
  static Flags              flags_for(ID id);

  static const char* short_name_as_C_string(ID id, char* buf, int size);
};
