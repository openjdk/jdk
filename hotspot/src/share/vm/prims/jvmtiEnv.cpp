/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderExt.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/interpreter.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jniCheck.hpp"
#include "prims/jvm_misc.hpp"
#include "prims/jvmtiAgentThread.hpp"
#include "prims/jvmtiClassFileReconstituter.hpp"
#include "prims/jvmtiCodeBlobEvents.hpp"
#include "prims/jvmtiExtensions.hpp"
#include "prims/jvmtiGetLoadedClasses.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/jvmtiManageCapabilities.hpp"
#include "prims/jvmtiRawMonitor.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "prims/jvmtiThreadState.inline.hpp"
#include "prims/jvmtiUtil.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jfieldIDWorkaround.hpp"
#include "runtime/osThread.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/signature.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmThread.hpp"
#include "services/threadService.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/preserveException.hpp"


#define FIXLATER 0 // REMOVE this when completed.

 // FIXLATER: hook into JvmtiTrace
#define TraceJVMTICalls false

JvmtiEnv::JvmtiEnv(jint version) : JvmtiEnvBase(version) {
}

JvmtiEnv::~JvmtiEnv() {
}

JvmtiEnv*
JvmtiEnv::create_a_jvmti(jint version) {
  return new JvmtiEnv(version);
}

// VM operation class to copy jni function table at safepoint.
// More than one java threads or jvmti agents may be reading/
// modifying jni function tables. To reduce the risk of bad
// interaction b/w these threads it is copied at safepoint.
class VM_JNIFunctionTableCopier : public VM_Operation {
 private:
  const struct JNINativeInterface_ *_function_table;
 public:
  VM_JNIFunctionTableCopier(const struct JNINativeInterface_ *func_tbl) {
    _function_table = func_tbl;
  };

  VMOp_Type type() const { return VMOp_JNIFunctionTableCopier; }
  void doit() {
    copy_jni_function_table(_function_table);
  };
};

//
// Do not change the "prefix" marker below, everything above it is copied
// unchanged into the filled stub, everything below is controlled by the
// stub filler (only method bodies are carried forward, and then only for
// functionality still in the spec).
//
// end file prefix

  //
  // Memory Management functions
  //

// mem_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::Allocate(jlong size, unsigned char** mem_ptr) {
  return allocate(size, mem_ptr);
} /* end Allocate */


// mem - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::Deallocate(unsigned char* mem) {
  return deallocate(mem);
} /* end Deallocate */

// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::SetThreadLocalStorage(JavaThread* java_thread, const void* data) {
  JvmtiThreadState* state = java_thread->jvmti_thread_state();
  if (state == NULL) {
    if (data == NULL) {
      // leaving state unset same as data set to NULL
      return JVMTI_ERROR_NONE;
    }
    // otherwise, create the state
    state = JvmtiThreadState::state_for(java_thread);
    if (state == NULL) {
      return JVMTI_ERROR_THREAD_NOT_ALIVE;
    }
  }
  state->env_thread_state(this)->set_agent_thread_local_storage_data((void*)data);
  return JVMTI_ERROR_NONE;
} /* end SetThreadLocalStorage */


// Threads_lock NOT held
// thread - NOT pre-checked
// data_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadLocalStorage(jthread thread, void** data_ptr) {
  JavaThread* current_thread = JavaThread::current();
  if (thread == NULL) {
    JvmtiThreadState* state = current_thread->jvmti_thread_state();
    *data_ptr = (state == NULL) ? NULL :
      state->env_thread_state(this)->get_agent_thread_local_storage_data();
  } else {

    // jvmti_GetThreadLocalStorage is "in native" and doesn't transition
    // the thread to _thread_in_vm. However, when the TLS for a thread
    // other than the current thread is required we need to transition
    // from native so as to resolve the jthread.

    ThreadInVMfromNative __tiv(current_thread);
    VM_ENTRY_BASE(jvmtiError, JvmtiEnv::GetThreadLocalStorage , current_thread)
    debug_only(VMNativeEntryWrapper __vew;)

    oop thread_oop = JNIHandles::resolve_external_guard(thread);
    if (thread_oop == NULL) {
      return JVMTI_ERROR_INVALID_THREAD;
    }
    if (!thread_oop->is_a(SystemDictionary::Thread_klass())) {
      return JVMTI_ERROR_INVALID_THREAD;
    }
    JavaThread* java_thread = java_lang_Thread::thread(thread_oop);
    if (java_thread == NULL) {
      return JVMTI_ERROR_THREAD_NOT_ALIVE;
    }
    JvmtiThreadState* state = java_thread->jvmti_thread_state();
    *data_ptr = (state == NULL) ? NULL :
      state->env_thread_state(this)->get_agent_thread_local_storage_data();
  }
  return JVMTI_ERROR_NONE;
} /* end GetThreadLocalStorage */

  //
  // Module functions
  //

// module_count_ptr - pre-checked for NULL
// modules_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetAllModules(jint* module_count_ptr, jobject** modules_ptr) {
    JvmtiModuleClosure jmc;

    return jmc.get_all_modules(this, module_count_ptr, modules_ptr);
} /* end GetAllModules */


  //
  // Class functions
  //

// class_count_ptr - pre-checked for NULL
// classes_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLoadedClasses(jint* class_count_ptr, jclass** classes_ptr) {
  return JvmtiGetLoadedClasses::getLoadedClasses(this, class_count_ptr, classes_ptr);
} /* end GetLoadedClasses */


// initiating_loader - NULL is a valid value, must be checked
// class_count_ptr - pre-checked for NULL
// classes_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetClassLoaderClasses(jobject initiating_loader, jint* class_count_ptr, jclass** classes_ptr) {
  return JvmtiGetLoadedClasses::getClassLoaderClasses(this, initiating_loader,
                                                  class_count_ptr, classes_ptr);
} /* end GetClassLoaderClasses */

// k_mirror - may be primitive, this must be checked
// is_modifiable_class_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::IsModifiableClass(oop k_mirror, jboolean* is_modifiable_class_ptr) {
  *is_modifiable_class_ptr = VM_RedefineClasses::is_modifiable_class(k_mirror)?
                                                       JNI_TRUE : JNI_FALSE;
  return JVMTI_ERROR_NONE;
} /* end IsModifiableClass */

// class_count - pre-checked to be greater than or equal to 0
// classes - pre-checked for NULL
jvmtiError
JvmtiEnv::RetransformClasses(jint class_count, const jclass* classes) {
//TODO: add locking

  int index;
  JavaThread* current_thread = JavaThread::current();
  ResourceMark rm(current_thread);

  jvmtiClassDefinition* class_definitions =
                            NEW_RESOURCE_ARRAY(jvmtiClassDefinition, class_count);
  NULL_CHECK(class_definitions, JVMTI_ERROR_OUT_OF_MEMORY);

  for (index = 0; index < class_count; index++) {
    HandleMark hm(current_thread);

    jclass jcls = classes[index];
    oop k_mirror = JNIHandles::resolve_external_guard(jcls);
    if (k_mirror == NULL) {
      return JVMTI_ERROR_INVALID_CLASS;
    }
    if (!k_mirror->is_a(SystemDictionary::Class_klass())) {
      return JVMTI_ERROR_INVALID_CLASS;
    }

    if (java_lang_Class::is_primitive(k_mirror)) {
      return JVMTI_ERROR_UNMODIFIABLE_CLASS;
    }

    Klass* k_oop = java_lang_Class::as_Klass(k_mirror);
    KlassHandle klass(current_thread, k_oop);

    jint status = klass->jvmti_class_status();
    if (status & (JVMTI_CLASS_STATUS_ERROR)) {
      return JVMTI_ERROR_INVALID_CLASS;
    }
    if (status & (JVMTI_CLASS_STATUS_ARRAY)) {
      return JVMTI_ERROR_UNMODIFIABLE_CLASS;
    }

    instanceKlassHandle ikh(current_thread, k_oop);
    if (ikh->get_cached_class_file_bytes() == NULL) {
      // Not cached, we need to reconstitute the class file from the
      // VM representation. We don't attach the reconstituted class
      // bytes to the InstanceKlass here because they have not been
      // validated and we're not at a safepoint.
      JvmtiClassFileReconstituter reconstituter(ikh);
      if (reconstituter.get_error() != JVMTI_ERROR_NONE) {
        return reconstituter.get_error();
      }

      class_definitions[index].class_byte_count = (jint)reconstituter.class_file_size();
      class_definitions[index].class_bytes      = (unsigned char*)
                                                       reconstituter.class_file_bytes();
    } else {
      // it is cached, get it from the cache
      class_definitions[index].class_byte_count = ikh->get_cached_class_file_len();
      class_definitions[index].class_bytes      = ikh->get_cached_class_file_bytes();
    }
    class_definitions[index].klass              = jcls;
  }
  VM_RedefineClasses op(class_count, class_definitions, jvmti_class_load_kind_retransform);
  VMThread::execute(&op);
  return (op.check_error());
} /* end RetransformClasses */


// class_count - pre-checked to be greater than or equal to 0
// class_definitions - pre-checked for NULL
jvmtiError
JvmtiEnv::RedefineClasses(jint class_count, const jvmtiClassDefinition* class_definitions) {
//TODO: add locking
  VM_RedefineClasses op(class_count, class_definitions, jvmti_class_load_kind_redefine);
  VMThread::execute(&op);
  return (op.check_error());
} /* end RedefineClasses */


  //
  // Object functions
  //

// size_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetObjectSize(jobject object, jlong* size_ptr) {
  oop mirror = JNIHandles::resolve_external_guard(object);
  NULL_CHECK(mirror, JVMTI_ERROR_INVALID_OBJECT);

  if (mirror->klass() == SystemDictionary::Class_klass() &&
      !java_lang_Class::is_primitive(mirror)) {
    Klass* k = java_lang_Class::as_Klass(mirror);
    assert(k != NULL, "class for non-primitive mirror must exist");
    *size_ptr = (jlong)k->size() * wordSize;
  } else {
    *size_ptr = (jlong)mirror->size() * wordSize;
    }
  return JVMTI_ERROR_NONE;
} /* end GetObjectSize */

  //
  // Method functions
  //

// prefix - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::SetNativeMethodPrefix(const char* prefix) {
  return prefix == NULL?
              SetNativeMethodPrefixes(0, NULL) :
              SetNativeMethodPrefixes(1, (char**)&prefix);
} /* end SetNativeMethodPrefix */


// prefix_count - pre-checked to be greater than or equal to 0
// prefixes - pre-checked for NULL
jvmtiError
JvmtiEnv::SetNativeMethodPrefixes(jint prefix_count, char** prefixes) {
  // Have to grab JVMTI thread state lock to be sure that some thread
  // isn't accessing the prefixes at the same time we are setting them.
  // No locks during VM bring-up.
  if (Threads::number_of_threads() == 0) {
    return set_native_method_prefixes(prefix_count, prefixes);
  } else {
    MutexLocker mu(JvmtiThreadState_lock);
    return set_native_method_prefixes(prefix_count, prefixes);
  }
} /* end SetNativeMethodPrefixes */

  //
  // Event Management functions
  //

// callbacks - NULL is a valid value, must be checked
// size_of_callbacks - pre-checked to be greater than or equal to 0
jvmtiError
JvmtiEnv::SetEventCallbacks(const jvmtiEventCallbacks* callbacks, jint size_of_callbacks) {
  JvmtiEventController::set_event_callbacks(this, callbacks, size_of_callbacks);
  return JVMTI_ERROR_NONE;
} /* end SetEventCallbacks */


// event_thread - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::SetEventNotificationMode(jvmtiEventMode mode, jvmtiEvent event_type, jthread event_thread,   ...) {
  JavaThread* java_thread = NULL;
  if (event_thread != NULL) {
    oop thread_oop = JNIHandles::resolve_external_guard(event_thread);
    if (thread_oop == NULL) {
      return JVMTI_ERROR_INVALID_THREAD;
    }
    if (!thread_oop->is_a(SystemDictionary::Thread_klass())) {
      return JVMTI_ERROR_INVALID_THREAD;
    }
    java_thread = java_lang_Thread::thread(thread_oop);
    if (java_thread == NULL) {
      return JVMTI_ERROR_THREAD_NOT_ALIVE;
    }
  }

  // event_type must be valid
  if (!JvmtiEventController::is_valid_event_type(event_type)) {
    return JVMTI_ERROR_INVALID_EVENT_TYPE;
  }

  // global events cannot be controlled at thread level.
  if (java_thread != NULL && JvmtiEventController::is_global_event(event_type)) {
    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
  }

  bool enabled = (mode == JVMTI_ENABLE);

  // assure that needed capabilities are present
  if (enabled && !JvmtiUtil::has_event_capability(event_type, get_capabilities())) {
    return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
  }

  if (event_type == JVMTI_EVENT_CLASS_FILE_LOAD_HOOK && enabled) {
    record_class_file_load_hook_enabled();
  }
  JvmtiEventController::set_user_enabled(this, java_thread, event_type, enabled);

  return JVMTI_ERROR_NONE;
} /* end SetEventNotificationMode */

  //
  // Capability functions
  //

// capabilities_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetPotentialCapabilities(jvmtiCapabilities* capabilities_ptr) {
  JvmtiManageCapabilities::get_potential_capabilities(get_capabilities(),
                                                      get_prohibited_capabilities(),
                                                      capabilities_ptr);
  return JVMTI_ERROR_NONE;
} /* end GetPotentialCapabilities */


// capabilities_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::AddCapabilities(const jvmtiCapabilities* capabilities_ptr) {
  return JvmtiManageCapabilities::add_capabilities(get_capabilities(),
                                                   get_prohibited_capabilities(),
                                                   capabilities_ptr,
                                                   get_capabilities());
} /* end AddCapabilities */


// capabilities_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::RelinquishCapabilities(const jvmtiCapabilities* capabilities_ptr) {
  JvmtiManageCapabilities::relinquish_capabilities(get_capabilities(), capabilities_ptr, get_capabilities());
  return JVMTI_ERROR_NONE;
} /* end RelinquishCapabilities */


// capabilities_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetCapabilities(jvmtiCapabilities* capabilities_ptr) {
  JvmtiManageCapabilities::copy_capabilities(get_capabilities(), capabilities_ptr);
  return JVMTI_ERROR_NONE;
} /* end GetCapabilities */

  //
  // Class Loader Search functions
  //

// segment - pre-checked for NULL
jvmtiError
JvmtiEnv::AddToBootstrapClassLoaderSearch(const char* segment) {
  jvmtiPhase phase = get_phase();
  if (phase == JVMTI_PHASE_ONLOAD) {
    Arguments::append_sysclasspath(segment);
    return JVMTI_ERROR_NONE;
  } else if (use_version_1_0_semantics()) {
    // This JvmtiEnv requested version 1.0 semantics and this function
    // is only allowed in the ONLOAD phase in version 1.0 so we need to
    // return an error here.
    return JVMTI_ERROR_WRONG_PHASE;
  } else if (phase == JVMTI_PHASE_LIVE) {
    // The phase is checked by the wrapper that called this function,
    // but this thread could be racing with the thread that is
    // terminating the VM so we check one more time.

    // create the zip entry
    ClassPathZipEntry* zip_entry = ClassLoader::create_class_path_zip_entry(segment, true);
    if (zip_entry == NULL) {
      return JVMTI_ERROR_ILLEGAL_ARGUMENT;
    }

    // lock the loader
    Thread* thread = Thread::current();
    HandleMark hm;
    Handle loader_lock = Handle(thread, SystemDictionary::system_loader_lock());

    ObjectLocker ol(loader_lock, thread);

    // add the jar file to the bootclasspath
    log_info(classload)("opened: %s", zip_entry->name());
    ClassLoaderExt::append_boot_classpath(zip_entry);
    return JVMTI_ERROR_NONE;
  } else {
    return JVMTI_ERROR_WRONG_PHASE;
  }

} /* end AddToBootstrapClassLoaderSearch */


// segment - pre-checked for NULL
jvmtiError
JvmtiEnv::AddToSystemClassLoaderSearch(const char* segment) {
  jvmtiPhase phase = get_phase();

  if (phase == JVMTI_PHASE_ONLOAD) {
    for (SystemProperty* p = Arguments::system_properties(); p != NULL; p = p->next()) {
      if (strcmp("java.class.path", p->key()) == 0) {
        p->append_value(segment);
        break;
      }
    }
    return JVMTI_ERROR_NONE;
  } else if (phase == JVMTI_PHASE_LIVE) {
    // The phase is checked by the wrapper that called this function,
    // but this thread could be racing with the thread that is
    // terminating the VM so we check one more time.
    HandleMark hm;

    // create the zip entry (which will open the zip file and hence
    // check that the segment is indeed a zip file).
    ClassPathZipEntry* zip_entry = ClassLoader::create_class_path_zip_entry(segment, false);
    if (zip_entry == NULL) {
      return JVMTI_ERROR_ILLEGAL_ARGUMENT;
    }
    delete zip_entry;   // no longer needed

    // lock the loader
    Thread* THREAD = Thread::current();
    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());

    ObjectLocker ol(loader, THREAD);

    // need the path as java.lang.String
    Handle path = java_lang_String::create_from_platform_dependent_str(segment, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
      return JVMTI_ERROR_INTERNAL;
    }

    instanceKlassHandle loader_ik(THREAD, loader->klass());

    // Invoke the appendToClassPathForInstrumentation method - if the method
    // is not found it means the loader doesn't support adding to the class path
    // in the live phase.
    {
      JavaValue res(T_VOID);
      JavaCalls::call_special(&res,
                              loader,
                              loader_ik,
                              vmSymbols::appendToClassPathForInstrumentation_name(),
                              vmSymbols::appendToClassPathForInstrumentation_signature(),
                              path,
                              THREAD);
      if (HAS_PENDING_EXCEPTION) {
        Symbol* ex_name = PENDING_EXCEPTION->klass()->name();
        CLEAR_PENDING_EXCEPTION;

        if (ex_name == vmSymbols::java_lang_NoSuchMethodError()) {
          return JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED;
        } else {
          return JVMTI_ERROR_INTERNAL;
        }
      }
    }

    return JVMTI_ERROR_NONE;
  } else {
    return JVMTI_ERROR_WRONG_PHASE;
  }
} /* end AddToSystemClassLoaderSearch */

  //
  // General functions
  //

// phase_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetPhase(jvmtiPhase* phase_ptr) {
  *phase_ptr = phase();
  return JVMTI_ERROR_NONE;
} /* end GetPhase */


jvmtiError
JvmtiEnv::DisposeEnvironment() {
  dispose();
  return JVMTI_ERROR_NONE;
} /* end DisposeEnvironment */


// data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::SetEnvironmentLocalStorage(const void* data) {
  set_env_local_storage(data);
  return JVMTI_ERROR_NONE;
} /* end SetEnvironmentLocalStorage */


// data_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetEnvironmentLocalStorage(void** data_ptr) {
  *data_ptr = (void*)get_env_local_storage();
  return JVMTI_ERROR_NONE;
} /* end GetEnvironmentLocalStorage */

// version_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetVersionNumber(jint* version_ptr) {
  *version_ptr = JVMTI_VERSION;
  return JVMTI_ERROR_NONE;
} /* end GetVersionNumber */


// name_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetErrorName(jvmtiError error, char** name_ptr) {
  if (error < JVMTI_ERROR_NONE || error > JVMTI_ERROR_MAX) {
    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
  }
  const char *name = JvmtiUtil::error_name(error);
  if (name == NULL) {
    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
  }
  size_t len = strlen(name) + 1;
  jvmtiError err = allocate(len, (unsigned char**)name_ptr);
  if (err == JVMTI_ERROR_NONE) {
    memcpy(*name_ptr, name, len);
  }
  return err;
} /* end GetErrorName */


jvmtiError
JvmtiEnv::SetVerboseFlag(jvmtiVerboseFlag flag, jboolean value) {
  switch (flag) {
  case JVMTI_VERBOSE_OTHER:
    // ignore
    break;
  case JVMTI_VERBOSE_CLASS:
    if (value == 0) {
      LogConfiguration::parse_log_arguments("stdout", "classunload=off", NULL, NULL, NULL);
      LogConfiguration::parse_log_arguments("stdout", "classload=off", NULL, NULL, NULL);
    } else {
      LogConfiguration::parse_log_arguments("stdout", "classload=info", NULL, NULL, NULL);
      LogConfiguration::parse_log_arguments("stdout", "classunload=info", NULL, NULL, NULL);
    }
    break;
  case JVMTI_VERBOSE_GC:
    if (value == 0) {
      LogConfiguration::configure_stdout(LogLevel::Off, true, LOG_TAGS(gc));
    } else {
      LogConfiguration::configure_stdout(LogLevel::Info, true, LOG_TAGS(gc));
    }
    break;
  case JVMTI_VERBOSE_JNI:
    PrintJNIResolving = value != 0;
    break;
  default:
    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
  };
  return JVMTI_ERROR_NONE;
} /* end SetVerboseFlag */


// format_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetJLocationFormat(jvmtiJlocationFormat* format_ptr) {
  *format_ptr = JVMTI_JLOCATION_JVMBCI;
  return JVMTI_ERROR_NONE;
} /* end GetJLocationFormat */

  //
  // Thread functions
  //

// Threads_lock NOT held
// thread - NOT pre-checked
// thread_state_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadState(jthread thread, jint* thread_state_ptr) {
  jint state;
  oop thread_oop;
  JavaThread* thr;

  if (thread == NULL) {
    thread_oop = JavaThread::current()->threadObj();
  } else {
    thread_oop = JNIHandles::resolve_external_guard(thread);
  }

  if (thread_oop == NULL || !thread_oop->is_a(SystemDictionary::Thread_klass())) {
    return JVMTI_ERROR_INVALID_THREAD;
  }

  // get most state bits
  state = (jint)java_lang_Thread::get_thread_status(thread_oop);

  // add more state bits
  thr = java_lang_Thread::thread(thread_oop);
  if (thr != NULL) {
    JavaThreadState jts = thr->thread_state();

    if (thr->is_being_ext_suspended()) {
      state |= JVMTI_THREAD_STATE_SUSPENDED;
    }
    if (jts == _thread_in_native) {
      state |= JVMTI_THREAD_STATE_IN_NATIVE;
    }
    OSThread* osThread = thr->osthread();
    if (osThread != NULL && osThread->interrupted()) {
      state |= JVMTI_THREAD_STATE_INTERRUPTED;
    }
  }

  *thread_state_ptr = state;
  return JVMTI_ERROR_NONE;
} /* end GetThreadState */


// thread_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetCurrentThread(jthread* thread_ptr) {
  JavaThread* current_thread  = JavaThread::current();
  *thread_ptr = (jthread)JNIHandles::make_local(current_thread, current_thread->threadObj());
  return JVMTI_ERROR_NONE;
} /* end GetCurrentThread */


// threads_count_ptr - pre-checked for NULL
// threads_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetAllThreads(jint* threads_count_ptr, jthread** threads_ptr) {
  int nthreads        = 0;
  Handle *thread_objs = NULL;
  ResourceMark rm;
  HandleMark hm;

  // enumerate threads (including agent threads)
  ThreadsListEnumerator tle(Thread::current(), true);
  nthreads = tle.num_threads();
  *threads_count_ptr = nthreads;

  if (nthreads == 0) {
    *threads_ptr = NULL;
    return JVMTI_ERROR_NONE;
  }

  thread_objs = NEW_RESOURCE_ARRAY(Handle, nthreads);
  NULL_CHECK(thread_objs, JVMTI_ERROR_OUT_OF_MEMORY);

  for (int i=0; i < nthreads; i++) {
    thread_objs[i] = Handle(tle.get_threadObj(i));
  }

  // have to make global handles outside of Threads_lock
  jthread *jthreads  = new_jthreadArray(nthreads, thread_objs);
  NULL_CHECK(jthreads, JVMTI_ERROR_OUT_OF_MEMORY);

  *threads_ptr = jthreads;
  return JVMTI_ERROR_NONE;
} /* end GetAllThreads */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::SuspendThread(JavaThread* java_thread) {
  // don't allow hidden thread suspend request.
  if (java_thread->is_hidden_from_external_view()) {
    return (JVMTI_ERROR_NONE);
  }

  {
    MutexLockerEx ml(java_thread->SR_lock(), Mutex::_no_safepoint_check_flag);
    if (java_thread->is_external_suspend()) {
      // don't allow nested external suspend requests.
      return (JVMTI_ERROR_THREAD_SUSPENDED);
    }
    if (java_thread->is_exiting()) { // thread is in the process of exiting
      return (JVMTI_ERROR_THREAD_NOT_ALIVE);
    }
    java_thread->set_external_suspend();
  }

  if (!JvmtiSuspendControl::suspend(java_thread)) {
    // the thread was in the process of exiting
    return (JVMTI_ERROR_THREAD_NOT_ALIVE);
  }
  return JVMTI_ERROR_NONE;
} /* end SuspendThread */


// request_count - pre-checked to be greater than or equal to 0
// request_list - pre-checked for NULL
// results - pre-checked for NULL
jvmtiError
JvmtiEnv::SuspendThreadList(jint request_count, const jthread* request_list, jvmtiError* results) {
  int needSafepoint = 0;  // > 0 if we need a safepoint
  for (int i = 0; i < request_count; i++) {
    JavaThread *java_thread = get_JavaThread(request_list[i]);
    if (java_thread == NULL) {
      results[i] = JVMTI_ERROR_INVALID_THREAD;
      continue;
    }
    // the thread has not yet run or has exited (not on threads list)
    if (java_thread->threadObj() == NULL) {
      results[i] = JVMTI_ERROR_THREAD_NOT_ALIVE;
      continue;
    }
    if (java_lang_Thread::thread(java_thread->threadObj()) == NULL) {
      results[i] = JVMTI_ERROR_THREAD_NOT_ALIVE;
      continue;
    }
    // don't allow hidden thread suspend request.
    if (java_thread->is_hidden_from_external_view()) {
      results[i] = JVMTI_ERROR_NONE;  // indicate successful suspend
      continue;
    }

    {
      MutexLockerEx ml(java_thread->SR_lock(), Mutex::_no_safepoint_check_flag);
      if (java_thread->is_external_suspend()) {
        // don't allow nested external suspend requests.
        results[i] = JVMTI_ERROR_THREAD_SUSPENDED;
        continue;
      }
      if (java_thread->is_exiting()) { // thread is in the process of exiting
        results[i] = JVMTI_ERROR_THREAD_NOT_ALIVE;
        continue;
      }
      java_thread->set_external_suspend();
    }
    if (java_thread->thread_state() == _thread_in_native) {
      // We need to try and suspend native threads here. Threads in
      // other states will self-suspend on their next transition.
      if (!JvmtiSuspendControl::suspend(java_thread)) {
        // The thread was in the process of exiting. Force another
        // safepoint to make sure that this thread transitions.
        needSafepoint++;
        results[i] = JVMTI_ERROR_THREAD_NOT_ALIVE;
        continue;
      }
    } else {
      needSafepoint++;
    }
    results[i] = JVMTI_ERROR_NONE;  // indicate successful suspend
  }
  if (needSafepoint > 0) {
    VM_ForceSafepoint vfs;
    VMThread::execute(&vfs);
  }
  // per-thread suspend results returned via results parameter
  return JVMTI_ERROR_NONE;
} /* end SuspendThreadList */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::ResumeThread(JavaThread* java_thread) {
  // don't allow hidden thread resume request.
  if (java_thread->is_hidden_from_external_view()) {
    return JVMTI_ERROR_NONE;
  }

  if (!java_thread->is_being_ext_suspended()) {
    return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
  }

  if (!JvmtiSuspendControl::resume(java_thread)) {
    return JVMTI_ERROR_INTERNAL;
  }
  return JVMTI_ERROR_NONE;
} /* end ResumeThread */


// request_count - pre-checked to be greater than or equal to 0
// request_list - pre-checked for NULL
// results - pre-checked for NULL
jvmtiError
JvmtiEnv::ResumeThreadList(jint request_count, const jthread* request_list, jvmtiError* results) {
  for (int i = 0; i < request_count; i++) {
    JavaThread *java_thread = get_JavaThread(request_list[i]);
    if (java_thread == NULL) {
      results[i] = JVMTI_ERROR_INVALID_THREAD;
      continue;
    }
    // don't allow hidden thread resume request.
    if (java_thread->is_hidden_from_external_view()) {
      results[i] = JVMTI_ERROR_NONE;  // indicate successful resume
      continue;
    }
    if (!java_thread->is_being_ext_suspended()) {
      results[i] = JVMTI_ERROR_THREAD_NOT_SUSPENDED;
      continue;
    }

    if (!JvmtiSuspendControl::resume(java_thread)) {
      results[i] = JVMTI_ERROR_INTERNAL;
      continue;
    }

    results[i] = JVMTI_ERROR_NONE;  // indicate successful suspend
  }
  // per-thread resume results returned via results parameter
  return JVMTI_ERROR_NONE;
} /* end ResumeThreadList */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::StopThread(JavaThread* java_thread, jobject exception) {
  oop e = JNIHandles::resolve_external_guard(exception);
  NULL_CHECK(e, JVMTI_ERROR_NULL_POINTER);

  JavaThread::send_async_exception(java_thread->threadObj(), e);

  return JVMTI_ERROR_NONE;

} /* end StopThread */


// Threads_lock NOT held
// thread - NOT pre-checked
jvmtiError
JvmtiEnv::InterruptThread(jthread thread) {
  oop thread_oop = JNIHandles::resolve_external_guard(thread);
  if (thread_oop == NULL || !thread_oop->is_a(SystemDictionary::Thread_klass()))
    return JVMTI_ERROR_INVALID_THREAD;

  JavaThread* current_thread  = JavaThread::current();

  // Todo: this is a duplicate of JVM_Interrupt; share code in future
  // Ensure that the C++ Thread and OSThread structures aren't freed before we operate
  MutexLockerEx ml(current_thread->threadObj() == thread_oop ? NULL : Threads_lock);
  // We need to re-resolve the java_thread, since a GC might have happened during the
  // acquire of the lock

  JavaThread* java_thread = java_lang_Thread::thread(JNIHandles::resolve_external_guard(thread));
  NULL_CHECK(java_thread, JVMTI_ERROR_THREAD_NOT_ALIVE);

  Thread::interrupt(java_thread);

  return JVMTI_ERROR_NONE;
} /* end InterruptThread */


// Threads_lock NOT held
// thread - NOT pre-checked
// info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadInfo(jthread thread, jvmtiThreadInfo* info_ptr) {
  ResourceMark rm;
  HandleMark hm;

  JavaThread* current_thread = JavaThread::current();

  // if thread is NULL the current thread is used
  oop thread_oop;
  if (thread == NULL) {
    thread_oop = current_thread->threadObj();
  } else {
    thread_oop = JNIHandles::resolve_external_guard(thread);
  }
  if (thread_oop == NULL || !thread_oop->is_a(SystemDictionary::Thread_klass()))
    return JVMTI_ERROR_INVALID_THREAD;

  Handle thread_obj(current_thread, thread_oop);
  Handle name;
  ThreadPriority priority;
  Handle     thread_group;
  Handle context_class_loader;
  bool          is_daemon;

  { MutexLocker mu(Threads_lock);

    name = Handle(current_thread, java_lang_Thread::name(thread_obj()));
    priority = java_lang_Thread::priority(thread_obj());
    thread_group = Handle(current_thread, java_lang_Thread::threadGroup(thread_obj()));
    is_daemon = java_lang_Thread::is_daemon(thread_obj());

    oop loader = java_lang_Thread::context_class_loader(thread_obj());
    context_class_loader = Handle(current_thread, loader);
  }
  { const char *n;

    if (name() != NULL) {
      n = java_lang_String::as_utf8_string(name());
    } else {
      n = UNICODE::as_utf8((jchar*) NULL, 0);
    }

    info_ptr->name = (char *) jvmtiMalloc(strlen(n)+1);
    if (info_ptr->name == NULL)
      return JVMTI_ERROR_OUT_OF_MEMORY;

    strcpy(info_ptr->name, n);
  }
  info_ptr->is_daemon = is_daemon;
  info_ptr->priority  = priority;

  info_ptr->context_class_loader = (context_class_loader.is_null()) ? NULL :
                                     jni_reference(context_class_loader);
  info_ptr->thread_group = jni_reference(thread_group);

  return JVMTI_ERROR_NONE;
} /* end GetThreadInfo */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// owned_monitor_count_ptr - pre-checked for NULL
// owned_monitors_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetOwnedMonitorInfo(JavaThread* java_thread, jint* owned_monitor_count_ptr, jobject** owned_monitors_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;
  JavaThread* calling_thread = JavaThread::current();

  // growable array of jvmti monitors info on the C-heap
  GrowableArray<jvmtiMonitorStackDepthInfo*> *owned_monitors_list =
      new (ResourceObj::C_HEAP, mtInternal) GrowableArray<jvmtiMonitorStackDepthInfo*>(1, true);

  // It is only safe to perform the direct operation on the current
  // thread. All other usage needs to use a vm-safepoint-op for safety.
  if (java_thread == calling_thread) {
    err = get_owned_monitors(calling_thread, java_thread, owned_monitors_list);
  } else {
    // JVMTI get monitors info at safepoint. Do not require target thread to
    // be suspended.
    VM_GetOwnedMonitorInfo op(this, calling_thread, java_thread, owned_monitors_list);
    VMThread::execute(&op);
    err = op.result();
  }
  jint owned_monitor_count = owned_monitors_list->length();
  if (err == JVMTI_ERROR_NONE) {
    if ((err = allocate(owned_monitor_count * sizeof(jobject *),
                      (unsigned char**)owned_monitors_ptr)) == JVMTI_ERROR_NONE) {
      // copy into the returned array
      for (int i = 0; i < owned_monitor_count; i++) {
        (*owned_monitors_ptr)[i] =
          ((jvmtiMonitorStackDepthInfo*)owned_monitors_list->at(i))->monitor;
      }
      *owned_monitor_count_ptr = owned_monitor_count;
    }
  }
  // clean up.
  for (int i = 0; i < owned_monitor_count; i++) {
    deallocate((unsigned char*)owned_monitors_list->at(i));
  }
  delete owned_monitors_list;

  return err;
} /* end GetOwnedMonitorInfo */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// monitor_info_count_ptr - pre-checked for NULL
// monitor_info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetOwnedMonitorStackDepthInfo(JavaThread* java_thread, jint* monitor_info_count_ptr, jvmtiMonitorStackDepthInfo** monitor_info_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;
  JavaThread* calling_thread  = JavaThread::current();

  // growable array of jvmti monitors info on the C-heap
  GrowableArray<jvmtiMonitorStackDepthInfo*> *owned_monitors_list =
         new (ResourceObj::C_HEAP, mtInternal) GrowableArray<jvmtiMonitorStackDepthInfo*>(1, true);

  // It is only safe to perform the direct operation on the current
  // thread. All other usage needs to use a vm-safepoint-op for safety.
  if (java_thread == calling_thread) {
    err = get_owned_monitors(calling_thread, java_thread, owned_monitors_list);
  } else {
    // JVMTI get owned monitors info at safepoint. Do not require target thread to
    // be suspended.
    VM_GetOwnedMonitorInfo op(this, calling_thread, java_thread, owned_monitors_list);
    VMThread::execute(&op);
    err = op.result();
  }

  jint owned_monitor_count = owned_monitors_list->length();
  if (err == JVMTI_ERROR_NONE) {
    if ((err = allocate(owned_monitor_count * sizeof(jvmtiMonitorStackDepthInfo),
                      (unsigned char**)monitor_info_ptr)) == JVMTI_ERROR_NONE) {
      // copy to output array.
      for (int i = 0; i < owned_monitor_count; i++) {
        (*monitor_info_ptr)[i].monitor =
          ((jvmtiMonitorStackDepthInfo*)owned_monitors_list->at(i))->monitor;
        (*monitor_info_ptr)[i].stack_depth =
          ((jvmtiMonitorStackDepthInfo*)owned_monitors_list->at(i))->stack_depth;
      }
    }
    *monitor_info_count_ptr = owned_monitor_count;
  }

  // clean up.
  for (int i = 0; i < owned_monitor_count; i++) {
    deallocate((unsigned char*)owned_monitors_list->at(i));
  }
  delete owned_monitors_list;

  return err;
} /* end GetOwnedMonitorStackDepthInfo */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// monitor_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetCurrentContendedMonitor(JavaThread* java_thread, jobject* monitor_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;
  JavaThread* calling_thread  = JavaThread::current();

  // It is only safe to perform the direct operation on the current
  // thread. All other usage needs to use a vm-safepoint-op for safety.
  if (java_thread == calling_thread) {
    err = get_current_contended_monitor(calling_thread, java_thread, monitor_ptr);
  } else {
    // get contended monitor information at safepoint.
    VM_GetCurrentContendedMonitor op(this, calling_thread, java_thread, monitor_ptr);
    VMThread::execute(&op);
    err = op.result();
  }
  return err;
} /* end GetCurrentContendedMonitor */


// Threads_lock NOT held
// thread - NOT pre-checked
// proc - pre-checked for NULL
// arg - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::RunAgentThread(jthread thread, jvmtiStartFunction proc, const void* arg, jint priority) {
  oop thread_oop = JNIHandles::resolve_external_guard(thread);
  if (thread_oop == NULL || !thread_oop->is_a(SystemDictionary::Thread_klass())) {
    return JVMTI_ERROR_INVALID_THREAD;
  }
  if (priority < JVMTI_THREAD_MIN_PRIORITY || priority > JVMTI_THREAD_MAX_PRIORITY) {
    return JVMTI_ERROR_INVALID_PRIORITY;
  }

  //Thread-self
  JavaThread* current_thread = JavaThread::current();

  Handle thread_hndl(current_thread, thread_oop);
  {
    MutexLocker mu(Threads_lock); // grab Threads_lock

    JvmtiAgentThread *new_thread = new JvmtiAgentThread(this, proc, arg);

    // At this point it may be possible that no osthread was created for the
    // JavaThread due to lack of memory.
    if (new_thread == NULL || new_thread->osthread() == NULL) {
      if (new_thread) delete new_thread;
      return JVMTI_ERROR_OUT_OF_MEMORY;
    }

    java_lang_Thread::set_thread(thread_hndl(), new_thread);
    java_lang_Thread::set_priority(thread_hndl(), (ThreadPriority)priority);
    java_lang_Thread::set_daemon(thread_hndl());

    new_thread->set_threadObj(thread_hndl());
    Threads::add(new_thread);
    Thread::start(new_thread);
  } // unlock Threads_lock

  return JVMTI_ERROR_NONE;
} /* end RunAgentThread */

  //
  // Thread Group functions
  //

// group_count_ptr - pre-checked for NULL
// groups_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetTopThreadGroups(jint* group_count_ptr, jthreadGroup** groups_ptr) {
  JavaThread* current_thread = JavaThread::current();

  // Only one top level thread group now.
  *group_count_ptr = 1;

  // Allocate memory to store global-refs to the thread groups.
  // Assume this area is freed by caller.
  *groups_ptr = (jthreadGroup *) jvmtiMalloc((sizeof(jthreadGroup)) * (*group_count_ptr));

  NULL_CHECK(*groups_ptr, JVMTI_ERROR_OUT_OF_MEMORY);

  // Convert oop to Handle, then convert Handle to global-ref.
  {
    HandleMark hm(current_thread);
    Handle system_thread_group(current_thread, Universe::system_thread_group());
    *groups_ptr[0] = jni_reference(system_thread_group);
  }

  return JVMTI_ERROR_NONE;
} /* end GetTopThreadGroups */


// info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadGroupInfo(jthreadGroup group, jvmtiThreadGroupInfo* info_ptr) {
  ResourceMark rm;
  HandleMark hm;

  JavaThread* current_thread = JavaThread::current();

  Handle group_obj (current_thread, JNIHandles::resolve_external_guard(group));
  NULL_CHECK(group_obj(), JVMTI_ERROR_INVALID_THREAD_GROUP);

  const char* name;
  Handle parent_group;
  bool is_daemon;
  ThreadPriority max_priority;

  { MutexLocker mu(Threads_lock);

    name         = java_lang_ThreadGroup::name(group_obj());
    parent_group = Handle(current_thread, java_lang_ThreadGroup::parent(group_obj()));
    is_daemon    = java_lang_ThreadGroup::is_daemon(group_obj());
    max_priority = java_lang_ThreadGroup::maxPriority(group_obj());
  }

  info_ptr->is_daemon    = is_daemon;
  info_ptr->max_priority = max_priority;
  info_ptr->parent       = jni_reference(parent_group);

  if (name != NULL) {
    info_ptr->name = (char*)jvmtiMalloc(strlen(name)+1);
    NULL_CHECK(info_ptr->name, JVMTI_ERROR_OUT_OF_MEMORY);
    strcpy(info_ptr->name, name);
  } else {
    info_ptr->name = NULL;
  }

  return JVMTI_ERROR_NONE;
} /* end GetThreadGroupInfo */


// thread_count_ptr - pre-checked for NULL
// threads_ptr - pre-checked for NULL
// group_count_ptr - pre-checked for NULL
// groups_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadGroupChildren(jthreadGroup group, jint* thread_count_ptr, jthread** threads_ptr, jint* group_count_ptr, jthreadGroup** groups_ptr) {
  JavaThread* current_thread = JavaThread::current();
  oop group_obj = (oop) JNIHandles::resolve_external_guard(group);
  NULL_CHECK(group_obj, JVMTI_ERROR_INVALID_THREAD_GROUP);

  Handle *thread_objs = NULL;
  Handle *group_objs  = NULL;
  int nthreads = 0;
  int ngroups = 0;
  int hidden_threads = 0;

  ResourceMark rm;
  HandleMark hm;

  Handle group_hdl(current_thread, group_obj);

  { MutexLocker mu(Threads_lock);

    nthreads = java_lang_ThreadGroup::nthreads(group_hdl());
    ngroups  = java_lang_ThreadGroup::ngroups(group_hdl());

    if (nthreads > 0) {
      objArrayOop threads = java_lang_ThreadGroup::threads(group_hdl());
      assert(nthreads <= threads->length(), "too many threads");
      thread_objs = NEW_RESOURCE_ARRAY(Handle,nthreads);
      for (int i=0, j=0; i<nthreads; i++) {
        oop thread_obj = threads->obj_at(i);
        assert(thread_obj != NULL, "thread_obj is NULL");
        JavaThread *javathread = java_lang_Thread::thread(thread_obj);
        // Filter out hidden java threads.
        if (javathread != NULL && javathread->is_hidden_from_external_view()) {
          hidden_threads++;
          continue;
        }
        thread_objs[j++] = Handle(current_thread, thread_obj);
      }
      nthreads -= hidden_threads;
    }
    if (ngroups > 0) {
      objArrayOop groups = java_lang_ThreadGroup::groups(group_hdl());
      assert(ngroups <= groups->length(), "too many threads");
      group_objs = NEW_RESOURCE_ARRAY(Handle,ngroups);
      for (int i=0; i<ngroups; i++) {
        oop group_obj = groups->obj_at(i);
        assert(group_obj != NULL, "group_obj != NULL");
        group_objs[i] = Handle(current_thread, group_obj);
      }
    }
  }

  // have to make global handles outside of Threads_lock
  *group_count_ptr  = ngroups;
  *thread_count_ptr = nthreads;
  *threads_ptr     = new_jthreadArray(nthreads, thread_objs);
  *groups_ptr      = new_jthreadGroupArray(ngroups, group_objs);
  if ((nthreads > 0) && (*threads_ptr == NULL)) {
    return JVMTI_ERROR_OUT_OF_MEMORY;
  }
  if ((ngroups > 0) && (*groups_ptr == NULL)) {
    return JVMTI_ERROR_OUT_OF_MEMORY;
  }

  return JVMTI_ERROR_NONE;
} /* end GetThreadGroupChildren */


  //
  // Stack Frame functions
  //

// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// max_frame_count - pre-checked to be greater than or equal to 0
// frame_buffer - pre-checked for NULL
// count_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetStackTrace(JavaThread* java_thread, jint start_depth, jint max_frame_count, jvmtiFrameInfo* frame_buffer, jint* count_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;

  // It is only safe to perform the direct operation on the current
  // thread. All other usage needs to use a vm-safepoint-op for safety.
  if (java_thread == JavaThread::current()) {
    err = get_stack_trace(java_thread, start_depth, max_frame_count, frame_buffer, count_ptr);
  } else {
    // JVMTI get stack trace at safepoint. Do not require target thread to
    // be suspended.
    VM_GetStackTrace op(this, java_thread, start_depth, max_frame_count, frame_buffer, count_ptr);
    VMThread::execute(&op);
    err = op.result();
  }

  return err;
} /* end GetStackTrace */


// max_frame_count - pre-checked to be greater than or equal to 0
// stack_info_ptr - pre-checked for NULL
// thread_count_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetAllStackTraces(jint max_frame_count, jvmtiStackInfo** stack_info_ptr, jint* thread_count_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;
  JavaThread* calling_thread = JavaThread::current();

  // JVMTI get stack traces at safepoint.
  VM_GetAllStackTraces op(this, calling_thread, max_frame_count);
  VMThread::execute(&op);
  *thread_count_ptr = op.final_thread_count();
  *stack_info_ptr = op.stack_info();
  err = op.result();
  return err;
} /* end GetAllStackTraces */


// thread_count - pre-checked to be greater than or equal to 0
// thread_list - pre-checked for NULL
// max_frame_count - pre-checked to be greater than or equal to 0
// stack_info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadListStackTraces(jint thread_count, const jthread* thread_list, jint max_frame_count, jvmtiStackInfo** stack_info_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;
  // JVMTI get stack traces at safepoint.
  VM_GetThreadListStackTraces op(this, thread_count, thread_list, max_frame_count);
  VMThread::execute(&op);
  err = op.result();
  if (err == JVMTI_ERROR_NONE) {
    *stack_info_ptr = op.stack_info();
  }
  return err;
} /* end GetThreadListStackTraces */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// count_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetFrameCount(JavaThread* java_thread, jint* count_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;

  // retrieve or create JvmtiThreadState.
  JvmtiThreadState* state = JvmtiThreadState::state_for(java_thread);
  if (state == NULL) {
    return JVMTI_ERROR_THREAD_NOT_ALIVE;
  }

  // It is only safe to perform the direct operation on the current
  // thread. All other usage needs to use a vm-safepoint-op for safety.
  if (java_thread == JavaThread::current()) {
    err = get_frame_count(state, count_ptr);
  } else {
    // get java stack frame count at safepoint.
    VM_GetFrameCount op(this, state, count_ptr);
    VMThread::execute(&op);
    err = op.result();
  }
  return err;
} /* end GetFrameCount */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::PopFrame(JavaThread* java_thread) {
  JavaThread* current_thread  = JavaThread::current();
  HandleMark hm(current_thread);
  uint32_t debug_bits = 0;

  // retrieve or create the state
  JvmtiThreadState* state = JvmtiThreadState::state_for(java_thread);
  if (state == NULL) {
    return JVMTI_ERROR_THREAD_NOT_ALIVE;
  }

  // Check if java_thread is fully suspended
  if (!is_thread_fully_suspended(java_thread, true /* wait for suspend completion */, &debug_bits)) {
    return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
  }
  // Check to see if a PopFrame was already in progress
  if (java_thread->popframe_condition() != JavaThread::popframe_inactive) {
    // Probably possible for JVMTI clients to trigger this, but the
    // JPDA backend shouldn't allow this to happen
    return JVMTI_ERROR_INTERNAL;
  }

  {
    // Was workaround bug
    //    4812902: popFrame hangs if the method is waiting at a synchronize
    // Catch this condition and return an error to avoid hanging.
    // Now JVMTI spec allows an implementation to bail out with an opaque frame error.
    OSThread* osThread = java_thread->osthread();
    if (osThread->get_state() == MONITOR_WAIT) {
      return JVMTI_ERROR_OPAQUE_FRAME;
    }
  }

  {
    ResourceMark rm(current_thread);
    // Check if there are more than one Java frame in this thread, that the top two frames
    // are Java (not native) frames, and that there is no intervening VM frame
    int frame_count = 0;
    bool is_interpreted[2];
    intptr_t *frame_sp[2];
    // The 2-nd arg of constructor is needed to stop iterating at java entry frame.
    for (vframeStream vfs(java_thread, true); !vfs.at_end(); vfs.next()) {
      methodHandle mh(current_thread, vfs.method());
      if (mh->is_native()) return(JVMTI_ERROR_OPAQUE_FRAME);
      is_interpreted[frame_count] = vfs.is_interpreted_frame();
      frame_sp[frame_count] = vfs.frame_id();
      if (++frame_count > 1) break;
    }
    if (frame_count < 2)  {
      // We haven't found two adjacent non-native Java frames on the top.
      // There can be two situations here:
      //  1. There are no more java frames
      //  2. Two top java frames are separated by non-java native frames
      if(vframeFor(java_thread, 1) == NULL) {
        return JVMTI_ERROR_NO_MORE_FRAMES;
      } else {
        // Intervening non-java native or VM frames separate java frames.
        // Current implementation does not support this. See bug #5031735.
        // In theory it is possible to pop frames in such cases.
        return JVMTI_ERROR_OPAQUE_FRAME;
      }
    }

    // If any of the top 2 frames is a compiled one, need to deoptimize it
    for (int i = 0; i < 2; i++) {
      if (!is_interpreted[i]) {
        Deoptimization::deoptimize_frame(java_thread, frame_sp[i]);
      }
    }

    // Update the thread state to reflect that the top frame is popped
    // so that cur_stack_depth is maintained properly and all frameIDs
    // are invalidated.
    // The current frame will be popped later when the suspended thread
    // is resumed and right before returning from VM to Java.
    // (see call_VM_base() in assembler_<cpu>.cpp).

    // It's fine to update the thread state here because no JVMTI events
    // shall be posted for this PopFrame.

    // It is only safe to perform the direct operation on the current
    // thread. All other usage needs to use a vm-safepoint-op for safety.
    if (java_thread == JavaThread::current()) {
      state->update_for_pop_top_frame();
    } else {
      VM_UpdateForPopTopFrame op(state);
      VMThread::execute(&op);
      jvmtiError err = op.result();
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
    }

    java_thread->set_popframe_condition(JavaThread::popframe_pending_bit);
    // Set pending step flag for this popframe and it is cleared when next
    // step event is posted.
    state->set_pending_step_for_popframe();
  }

  return JVMTI_ERROR_NONE;
} /* end PopFrame */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
// method_ptr - pre-checked for NULL
// location_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetFrameLocation(JavaThread* java_thread, jint depth, jmethodID* method_ptr, jlocation* location_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;

  // It is only safe to perform the direct operation on the current
  // thread. All other usage needs to use a vm-safepoint-op for safety.
  if (java_thread == JavaThread::current()) {
    err = get_frame_location(java_thread, depth, method_ptr, location_ptr);
  } else {
    // JVMTI get java stack frame location at safepoint.
    VM_GetFrameLocation op(this, java_thread, depth, method_ptr, location_ptr);
    VMThread::execute(&op);
    err = op.result();
  }
  return err;
} /* end GetFrameLocation */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
jvmtiError
JvmtiEnv::NotifyFramePop(JavaThread* java_thread, jint depth) {
  jvmtiError err = JVMTI_ERROR_NONE;
  ResourceMark rm;
  uint32_t debug_bits = 0;

  JvmtiThreadState *state = JvmtiThreadState::state_for(java_thread);
  if (state == NULL) {
    return JVMTI_ERROR_THREAD_NOT_ALIVE;
  }

  if (!JvmtiEnv::is_thread_fully_suspended(java_thread, true, &debug_bits)) {
      return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
  }

  if (TraceJVMTICalls) {
    JvmtiSuspendControl::print();
  }

  vframe *vf = vframeFor(java_thread, depth);
  if (vf == NULL) {
    return JVMTI_ERROR_NO_MORE_FRAMES;
  }

  if (!vf->is_java_frame() || ((javaVFrame*) vf)->method()->is_native()) {
    return JVMTI_ERROR_OPAQUE_FRAME;
  }

  assert(vf->frame_pointer() != NULL, "frame pointer mustn't be NULL");

  // It is only safe to perform the direct operation on the current
  // thread. All other usage needs to use a vm-safepoint-op for safety.
  if (java_thread == JavaThread::current()) {
    int frame_number = state->count_frames() - depth;
    state->env_thread_state(this)->set_frame_pop(frame_number);
  } else {
    VM_SetFramePop op(this, state, depth);
    VMThread::execute(&op);
    err = op.result();
  }
  return err;
} /* end NotifyFramePop */


  //
  // Force Early Return functions
  //

// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::ForceEarlyReturnObject(JavaThread* java_thread, jobject value) {
  jvalue val;
  val.l = value;
  return force_early_return(java_thread, val, atos);
} /* end ForceEarlyReturnObject */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::ForceEarlyReturnInt(JavaThread* java_thread, jint value) {
  jvalue val;
  val.i = value;
  return force_early_return(java_thread, val, itos);
} /* end ForceEarlyReturnInt */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::ForceEarlyReturnLong(JavaThread* java_thread, jlong value) {
  jvalue val;
  val.j = value;
  return force_early_return(java_thread, val, ltos);
} /* end ForceEarlyReturnLong */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::ForceEarlyReturnFloat(JavaThread* java_thread, jfloat value) {
  jvalue val;
  val.f = value;
  return force_early_return(java_thread, val, ftos);
} /* end ForceEarlyReturnFloat */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::ForceEarlyReturnDouble(JavaThread* java_thread, jdouble value) {
  jvalue val;
  val.d = value;
  return force_early_return(java_thread, val, dtos);
} /* end ForceEarlyReturnDouble */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
jvmtiError
JvmtiEnv::ForceEarlyReturnVoid(JavaThread* java_thread) {
  jvalue val;
  val.j = 0L;
  return force_early_return(java_thread, val, vtos);
} /* end ForceEarlyReturnVoid */


  //
  // Heap functions
  //

// klass - NULL is a valid value, must be checked
// initial_object - NULL is a valid value, must be checked
// callbacks - pre-checked for NULL
// user_data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::FollowReferences(jint heap_filter, jclass klass, jobject initial_object, const jvmtiHeapCallbacks* callbacks, const void* user_data) {
  // check klass if provided
  Klass* k_oop = NULL;
  if (klass != NULL) {
    oop k_mirror = JNIHandles::resolve_external_guard(klass);
    if (k_mirror == NULL) {
      return JVMTI_ERROR_INVALID_CLASS;
    }
    if (java_lang_Class::is_primitive(k_mirror)) {
      return JVMTI_ERROR_NONE;
    }
    k_oop = java_lang_Class::as_Klass(k_mirror);
    if (k_oop == NULL) {
      return JVMTI_ERROR_INVALID_CLASS;
    }
  }

  Thread *thread = Thread::current();
  HandleMark hm(thread);
  KlassHandle kh (thread, k_oop);

  TraceTime t("FollowReferences", TRACETIME_LOG(Debug, jvmti, objecttagging));
  JvmtiTagMap::tag_map_for(this)->follow_references(heap_filter, kh, initial_object, callbacks, user_data);
  return JVMTI_ERROR_NONE;
} /* end FollowReferences */


// klass - NULL is a valid value, must be checked
// callbacks - pre-checked for NULL
// user_data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::IterateThroughHeap(jint heap_filter, jclass klass, const jvmtiHeapCallbacks* callbacks, const void* user_data) {
  // check klass if provided
  Klass* k_oop = NULL;
  if (klass != NULL) {
    oop k_mirror = JNIHandles::resolve_external_guard(klass);
    if (k_mirror == NULL) {
      return JVMTI_ERROR_INVALID_CLASS;
    }
    if (java_lang_Class::is_primitive(k_mirror)) {
      return JVMTI_ERROR_NONE;
    }
    k_oop = java_lang_Class::as_Klass(k_mirror);
    if (k_oop == NULL) {
      return JVMTI_ERROR_INVALID_CLASS;
    }
  }

  Thread *thread = Thread::current();
  HandleMark hm(thread);
  KlassHandle kh (thread, k_oop);

  TraceTime t("IterateThroughHeap", TRACETIME_LOG(Debug, jvmti, objecttagging));
  JvmtiTagMap::tag_map_for(this)->iterate_through_heap(heap_filter, kh, callbacks, user_data);
  return JVMTI_ERROR_NONE;
} /* end IterateThroughHeap */


// tag_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetTag(jobject object, jlong* tag_ptr) {
  oop o = JNIHandles::resolve_external_guard(object);
  NULL_CHECK(o, JVMTI_ERROR_INVALID_OBJECT);
  *tag_ptr = JvmtiTagMap::tag_map_for(this)->get_tag(object);
  return JVMTI_ERROR_NONE;
} /* end GetTag */


jvmtiError
JvmtiEnv::SetTag(jobject object, jlong tag) {
  oop o = JNIHandles::resolve_external_guard(object);
  NULL_CHECK(o, JVMTI_ERROR_INVALID_OBJECT);
  JvmtiTagMap::tag_map_for(this)->set_tag(object, tag);
  return JVMTI_ERROR_NONE;
} /* end SetTag */


// tag_count - pre-checked to be greater than or equal to 0
// tags - pre-checked for NULL
// count_ptr - pre-checked for NULL
// object_result_ptr - NULL is a valid value, must be checked
// tag_result_ptr - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::GetObjectsWithTags(jint tag_count, const jlong* tags, jint* count_ptr, jobject** object_result_ptr, jlong** tag_result_ptr) {
  TraceTime t("GetObjectsWithTags", TRACETIME_LOG(Debug, jvmti, objecttagging));
  return JvmtiTagMap::tag_map_for(this)->get_objects_with_tags((jlong*)tags, tag_count, count_ptr, object_result_ptr, tag_result_ptr);
} /* end GetObjectsWithTags */


jvmtiError
JvmtiEnv::ForceGarbageCollection() {
  Universe::heap()->collect(GCCause::_jvmti_force_gc);
  return JVMTI_ERROR_NONE;
} /* end ForceGarbageCollection */


  //
  // Heap (1.0) functions
  //

// object_reference_callback - pre-checked for NULL
// user_data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::IterateOverObjectsReachableFromObject(jobject object, jvmtiObjectReferenceCallback object_reference_callback, const void* user_data) {
  oop o = JNIHandles::resolve_external_guard(object);
  NULL_CHECK(o, JVMTI_ERROR_INVALID_OBJECT);
  JvmtiTagMap::tag_map_for(this)->iterate_over_objects_reachable_from_object(object, object_reference_callback, user_data);
  return JVMTI_ERROR_NONE;
} /* end IterateOverObjectsReachableFromObject */


// heap_root_callback - NULL is a valid value, must be checked
// stack_ref_callback - NULL is a valid value, must be checked
// object_ref_callback - NULL is a valid value, must be checked
// user_data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::IterateOverReachableObjects(jvmtiHeapRootCallback heap_root_callback, jvmtiStackReferenceCallback stack_ref_callback, jvmtiObjectReferenceCallback object_ref_callback, const void* user_data) {
  TraceTime t("IterateOverReachableObjects", TRACETIME_LOG(Debug, jvmti, objecttagging));
  JvmtiTagMap::tag_map_for(this)->iterate_over_reachable_objects(heap_root_callback, stack_ref_callback, object_ref_callback, user_data);
  return JVMTI_ERROR_NONE;
} /* end IterateOverReachableObjects */


// heap_object_callback - pre-checked for NULL
// user_data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::IterateOverHeap(jvmtiHeapObjectFilter object_filter, jvmtiHeapObjectCallback heap_object_callback, const void* user_data) {
  TraceTime t("IterateOverHeap", TRACETIME_LOG(Debug, jvmti, objecttagging));
  Thread *thread = Thread::current();
  HandleMark hm(thread);
  JvmtiTagMap::tag_map_for(this)->iterate_over_heap(object_filter, KlassHandle(), heap_object_callback, user_data);
  return JVMTI_ERROR_NONE;
} /* end IterateOverHeap */


// k_mirror - may be primitive, this must be checked
// heap_object_callback - pre-checked for NULL
// user_data - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::IterateOverInstancesOfClass(oop k_mirror, jvmtiHeapObjectFilter object_filter, jvmtiHeapObjectCallback heap_object_callback, const void* user_data) {
  if (java_lang_Class::is_primitive(k_mirror)) {
    // DO PRIMITIVE CLASS PROCESSING
    return JVMTI_ERROR_NONE;
  }
  Klass* k_oop = java_lang_Class::as_Klass(k_mirror);
  if (k_oop == NULL) {
    return JVMTI_ERROR_INVALID_CLASS;
  }
  Thread *thread = Thread::current();
  HandleMark hm(thread);
  KlassHandle klass (thread, k_oop);
  TraceTime t("IterateOverInstancesOfClass", TRACETIME_LOG(Debug, jvmti, objecttagging));
  JvmtiTagMap::tag_map_for(this)->iterate_over_heap(object_filter, klass, heap_object_callback, user_data);
  return JVMTI_ERROR_NONE;
} /* end IterateOverInstancesOfClass */


  //
  // Local Variable functions
  //

// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
// value_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLocalObject(JavaThread* java_thread, jint depth, jint slot, jobject* value_ptr) {
  JavaThread* current_thread = JavaThread::current();
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm(current_thread);

  VM_GetOrSetLocal op(java_thread, current_thread, depth, slot);
  VMThread::execute(&op);
  jvmtiError err = op.result();
  if (err != JVMTI_ERROR_NONE) {
    return err;
  } else {
    *value_ptr = op.value().l;
    return JVMTI_ERROR_NONE;
  }
} /* end GetLocalObject */

// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
// value - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLocalInstance(JavaThread* java_thread, jint depth, jobject* value_ptr){
  JavaThread* current_thread = JavaThread::current();
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm(current_thread);

  VM_GetReceiver op(java_thread, current_thread, depth);
  VMThread::execute(&op);
  jvmtiError err = op.result();
  if (err != JVMTI_ERROR_NONE) {
    return err;
  } else {
    *value_ptr = op.value().l;
    return JVMTI_ERROR_NONE;
  }
} /* end GetLocalInstance */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
// value_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLocalInt(JavaThread* java_thread, jint depth, jint slot, jint* value_ptr) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;

  VM_GetOrSetLocal op(java_thread, depth, slot, T_INT);
  VMThread::execute(&op);
  *value_ptr = op.value().i;
  return op.result();
} /* end GetLocalInt */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
// value_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLocalLong(JavaThread* java_thread, jint depth, jint slot, jlong* value_ptr) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;

  VM_GetOrSetLocal op(java_thread, depth, slot, T_LONG);
  VMThread::execute(&op);
  *value_ptr = op.value().j;
  return op.result();
} /* end GetLocalLong */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
// value_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLocalFloat(JavaThread* java_thread, jint depth, jint slot, jfloat* value_ptr) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;

  VM_GetOrSetLocal op(java_thread, depth, slot, T_FLOAT);
  VMThread::execute(&op);
  *value_ptr = op.value().f;
  return op.result();
} /* end GetLocalFloat */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
// value_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLocalDouble(JavaThread* java_thread, jint depth, jint slot, jdouble* value_ptr) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;

  VM_GetOrSetLocal op(java_thread, depth, slot, T_DOUBLE);
  VMThread::execute(&op);
  *value_ptr = op.value().d;
  return op.result();
} /* end GetLocalDouble */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
jvmtiError
JvmtiEnv::SetLocalObject(JavaThread* java_thread, jint depth, jint slot, jobject value) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;
  jvalue val;
  val.l = value;
  VM_GetOrSetLocal op(java_thread, depth, slot, T_OBJECT, val);
  VMThread::execute(&op);
  return op.result();
} /* end SetLocalObject */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
jvmtiError
JvmtiEnv::SetLocalInt(JavaThread* java_thread, jint depth, jint slot, jint value) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;
  jvalue val;
  val.i = value;
  VM_GetOrSetLocal op(java_thread, depth, slot, T_INT, val);
  VMThread::execute(&op);
  return op.result();
} /* end SetLocalInt */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
jvmtiError
JvmtiEnv::SetLocalLong(JavaThread* java_thread, jint depth, jint slot, jlong value) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;
  jvalue val;
  val.j = value;
  VM_GetOrSetLocal op(java_thread, depth, slot, T_LONG, val);
  VMThread::execute(&op);
  return op.result();
} /* end SetLocalLong */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
jvmtiError
JvmtiEnv::SetLocalFloat(JavaThread* java_thread, jint depth, jint slot, jfloat value) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;
  jvalue val;
  val.f = value;
  VM_GetOrSetLocal op(java_thread, depth, slot, T_FLOAT, val);
  VMThread::execute(&op);
  return op.result();
} /* end SetLocalFloat */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// java_thread - unchecked
// depth - pre-checked as non-negative
jvmtiError
JvmtiEnv::SetLocalDouble(JavaThread* java_thread, jint depth, jint slot, jdouble value) {
  // rm object is created to clean up the javaVFrame created in
  // doit_prologue(), but after doit() is finished with it.
  ResourceMark rm;
  jvalue val;
  val.d = value;
  VM_GetOrSetLocal op(java_thread, depth, slot, T_DOUBLE, val);
  VMThread::execute(&op);
  return op.result();
} /* end SetLocalDouble */


  //
  // Breakpoint functions
  //

// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
jvmtiError
JvmtiEnv::SetBreakpoint(Method* method_oop, jlocation location) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  if (location < 0) {   // simple invalid location check first
    return JVMTI_ERROR_INVALID_LOCATION;
  }
  // verify that the breakpoint is not past the end of the method
  if (location >= (jlocation) method_oop->code_size()) {
    return JVMTI_ERROR_INVALID_LOCATION;
  }

  ResourceMark rm;
  JvmtiBreakpoint bp(method_oop, location);
  JvmtiBreakpoints& jvmti_breakpoints = JvmtiCurrentBreakpoints::get_jvmti_breakpoints();
  if (jvmti_breakpoints.set(bp) == JVMTI_ERROR_DUPLICATE)
    return JVMTI_ERROR_DUPLICATE;

  if (TraceJVMTICalls) {
    jvmti_breakpoints.print();
  }

  return JVMTI_ERROR_NONE;
} /* end SetBreakpoint */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
jvmtiError
JvmtiEnv::ClearBreakpoint(Method* method_oop, jlocation location) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);

  if (location < 0) {   // simple invalid location check first
    return JVMTI_ERROR_INVALID_LOCATION;
  }

  // verify that the breakpoint is not past the end of the method
  if (location >= (jlocation) method_oop->code_size()) {
    return JVMTI_ERROR_INVALID_LOCATION;
  }

  JvmtiBreakpoint bp(method_oop, location);

  JvmtiBreakpoints& jvmti_breakpoints = JvmtiCurrentBreakpoints::get_jvmti_breakpoints();
  if (jvmti_breakpoints.clear(bp) == JVMTI_ERROR_NOT_FOUND)
    return JVMTI_ERROR_NOT_FOUND;

  if (TraceJVMTICalls) {
    jvmti_breakpoints.print();
  }

  return JVMTI_ERROR_NONE;
} /* end ClearBreakpoint */


  //
  // Watched Field functions
  //

jvmtiError
JvmtiEnv::SetFieldAccessWatch(fieldDescriptor* fdesc_ptr) {
  // make sure we haven't set this watch before
  if (fdesc_ptr->is_field_access_watched()) return JVMTI_ERROR_DUPLICATE;
  fdesc_ptr->set_is_field_access_watched(true);

  JvmtiEventController::change_field_watch(JVMTI_EVENT_FIELD_ACCESS, true);

  return JVMTI_ERROR_NONE;
} /* end SetFieldAccessWatch */


jvmtiError
JvmtiEnv::ClearFieldAccessWatch(fieldDescriptor* fdesc_ptr) {
  // make sure we have a watch to clear
  if (!fdesc_ptr->is_field_access_watched()) return JVMTI_ERROR_NOT_FOUND;
  fdesc_ptr->set_is_field_access_watched(false);

  JvmtiEventController::change_field_watch(JVMTI_EVENT_FIELD_ACCESS, false);

  return JVMTI_ERROR_NONE;
} /* end ClearFieldAccessWatch */


jvmtiError
JvmtiEnv::SetFieldModificationWatch(fieldDescriptor* fdesc_ptr) {
  // make sure we haven't set this watch before
  if (fdesc_ptr->is_field_modification_watched()) return JVMTI_ERROR_DUPLICATE;
  fdesc_ptr->set_is_field_modification_watched(true);

  JvmtiEventController::change_field_watch(JVMTI_EVENT_FIELD_MODIFICATION, true);

  return JVMTI_ERROR_NONE;
} /* end SetFieldModificationWatch */


jvmtiError
JvmtiEnv::ClearFieldModificationWatch(fieldDescriptor* fdesc_ptr) {
   // make sure we have a watch to clear
  if (!fdesc_ptr->is_field_modification_watched()) return JVMTI_ERROR_NOT_FOUND;
  fdesc_ptr->set_is_field_modification_watched(false);

  JvmtiEventController::change_field_watch(JVMTI_EVENT_FIELD_MODIFICATION, false);

  return JVMTI_ERROR_NONE;
} /* end ClearFieldModificationWatch */

  //
  // Class functions
  //


// k_mirror - may be primitive, this must be checked
// signature_ptr - NULL is a valid value, must be checked
// generic_ptr - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::GetClassSignature(oop k_mirror, char** signature_ptr, char** generic_ptr) {
  ResourceMark rm;
  bool isPrimitive = java_lang_Class::is_primitive(k_mirror);
  Klass* k = NULL;
  if (!isPrimitive) {
    k = java_lang_Class::as_Klass(k_mirror);
    NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);
  }
  if (signature_ptr != NULL) {
    char* result = NULL;
    if (isPrimitive) {
      char tchar = type2char(java_lang_Class::primitive_type(k_mirror));
      result = (char*) jvmtiMalloc(2);
      result[0] = tchar;
      result[1] = '\0';
    } else {
      const char* class_sig = k->signature_name();
      result = (char *) jvmtiMalloc(strlen(class_sig)+1);
      strcpy(result, class_sig);
    }
    *signature_ptr = result;
  }
  if (generic_ptr != NULL) {
    *generic_ptr = NULL;
    if (!isPrimitive && k->is_instance_klass()) {
      Symbol* soo = InstanceKlass::cast(k)->generic_signature();
      if (soo != NULL) {
        const char *gen_sig = soo->as_C_string();
        if (gen_sig != NULL) {
          char* gen_result;
          jvmtiError err = allocate(strlen(gen_sig) + 1,
                                    (unsigned char **)&gen_result);
          if (err != JVMTI_ERROR_NONE) {
            return err;
          }
          strcpy(gen_result, gen_sig);
          *generic_ptr = gen_result;
        }
      }
    }
  }
  return JVMTI_ERROR_NONE;
} /* end GetClassSignature */


// k_mirror - may be primitive, this must be checked
// status_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetClassStatus(oop k_mirror, jint* status_ptr) {
  jint result = 0;
  if (java_lang_Class::is_primitive(k_mirror)) {
    result |= JVMTI_CLASS_STATUS_PRIMITIVE;
  } else {
    Klass* k = java_lang_Class::as_Klass(k_mirror);
    NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);
    result = k->jvmti_class_status();
  }
  *status_ptr = result;

  return JVMTI_ERROR_NONE;
} /* end GetClassStatus */


// k_mirror - may be primitive, this must be checked
// source_name_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetSourceFileName(oop k_mirror, char** source_name_ptr) {
  if (java_lang_Class::is_primitive(k_mirror)) {
     return JVMTI_ERROR_ABSENT_INFORMATION;
  }
  Klass* k_klass = java_lang_Class::as_Klass(k_mirror);
  NULL_CHECK(k_klass, JVMTI_ERROR_INVALID_CLASS);

  if (!k_klass->is_instance_klass()) {
    return JVMTI_ERROR_ABSENT_INFORMATION;
  }

  Symbol* sfnOop = InstanceKlass::cast(k_klass)->source_file_name();
  NULL_CHECK(sfnOop, JVMTI_ERROR_ABSENT_INFORMATION);
  {
    JavaThread* current_thread  = JavaThread::current();
    ResourceMark rm(current_thread);
    const char* sfncp = (const char*) sfnOop->as_C_string();
    *source_name_ptr = (char *) jvmtiMalloc(strlen(sfncp)+1);
    strcpy(*source_name_ptr, sfncp);
  }

  return JVMTI_ERROR_NONE;
} /* end GetSourceFileName */


// k_mirror - may be primitive, this must be checked
// modifiers_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetClassModifiers(oop k_mirror, jint* modifiers_ptr) {
  JavaThread* current_thread  = JavaThread::current();
  jint result = 0;
  if (!java_lang_Class::is_primitive(k_mirror)) {
    Klass* k = java_lang_Class::as_Klass(k_mirror);
    NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);
    result = k->compute_modifier_flags(current_thread);
    JavaThread* THREAD = current_thread; // pass to macros
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
      return JVMTI_ERROR_INTERNAL;
    };

    // Reset the deleted  ACC_SUPER bit ( deleted in compute_modifier_flags()).
    if(k->is_super()) {
      result |= JVM_ACC_SUPER;
    }
  } else {
    result = (JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC);
  }
  *modifiers_ptr = result;

  return JVMTI_ERROR_NONE;
} /* end GetClassModifiers */


// k_mirror - may be primitive, this must be checked
// method_count_ptr - pre-checked for NULL
// methods_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetClassMethods(oop k_mirror, jint* method_count_ptr, jmethodID** methods_ptr) {
  JavaThread* current_thread  = JavaThread::current();
  HandleMark hm(current_thread);

  if (java_lang_Class::is_primitive(k_mirror)) {
    *method_count_ptr = 0;
    *methods_ptr = (jmethodID*) jvmtiMalloc(0 * sizeof(jmethodID));
    return JVMTI_ERROR_NONE;
  }
  Klass* k = java_lang_Class::as_Klass(k_mirror);
  NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);

  // Return CLASS_NOT_PREPARED error as per JVMTI spec.
  if (!(k->jvmti_class_status() & (JVMTI_CLASS_STATUS_PREPARED|JVMTI_CLASS_STATUS_ARRAY) )) {
    return JVMTI_ERROR_CLASS_NOT_PREPARED;
  }

  if (!k->is_instance_klass()) {
    *method_count_ptr = 0;
    *methods_ptr = (jmethodID*) jvmtiMalloc(0 * sizeof(jmethodID));
    return JVMTI_ERROR_NONE;
  }
  instanceKlassHandle instanceK_h(current_thread, k);
  // Allocate the result and fill it in
  int result_length = instanceK_h->methods()->length();
  jmethodID* result_list = (jmethodID*)jvmtiMalloc(result_length * sizeof(jmethodID));
  int index;
  bool jmethodids_found = true;

  if (JvmtiExport::can_maintain_original_method_order()) {
    // Use the original method ordering indices stored in the class, so we can emit
    // jmethodIDs in the order they appeared in the class file
    for (index = 0; index < result_length; index++) {
      Method* m = instanceK_h->methods()->at(index);
      int original_index = instanceK_h->method_ordering()->at(index);
      assert(original_index >= 0 && original_index < result_length, "invalid original method index");
      jmethodID id;
      if (jmethodids_found) {
        id = m->find_jmethod_id_or_null();
        if (id == NULL) {
          // If we find an uninitialized value, make sure there is
          // enough space for all the uninitialized values we might
          // find.
          instanceK_h->ensure_space_for_methodids(index);
          jmethodids_found = false;
          id = m->jmethod_id();
        }
      } else {
        id = m->jmethod_id();
      }
      result_list[original_index] = id;
    }
  } else {
    // otherwise just copy in any order
    for (index = 0; index < result_length; index++) {
      Method* m = instanceK_h->methods()->at(index);
      jmethodID id;
      if (jmethodids_found) {
        id = m->find_jmethod_id_or_null();
        if (id == NULL) {
          // If we find an uninitialized value, make sure there is
          // enough space for all the uninitialized values we might
          // find.
          instanceK_h->ensure_space_for_methodids(index);
          jmethodids_found = false;
          id = m->jmethod_id();
        }
      } else {
        id = m->jmethod_id();
      }
      result_list[index] = id;
    }
  }
  // Fill in return value.
  *method_count_ptr = result_length;
  *methods_ptr = result_list;

  return JVMTI_ERROR_NONE;
} /* end GetClassMethods */


// k_mirror - may be primitive, this must be checked
// field_count_ptr - pre-checked for NULL
// fields_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetClassFields(oop k_mirror, jint* field_count_ptr, jfieldID** fields_ptr) {
  if (java_lang_Class::is_primitive(k_mirror)) {
    *field_count_ptr = 0;
    *fields_ptr = (jfieldID*) jvmtiMalloc(0 * sizeof(jfieldID));
    return JVMTI_ERROR_NONE;
  }
  JavaThread* current_thread = JavaThread::current();
  HandleMark hm(current_thread);
  Klass* k = java_lang_Class::as_Klass(k_mirror);
  NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);

  // Return CLASS_NOT_PREPARED error as per JVMTI spec.
  if (!(k->jvmti_class_status() & (JVMTI_CLASS_STATUS_PREPARED|JVMTI_CLASS_STATUS_ARRAY) )) {
    return JVMTI_ERROR_CLASS_NOT_PREPARED;
  }

  if (!k->is_instance_klass()) {
    *field_count_ptr = 0;
    *fields_ptr = (jfieldID*) jvmtiMalloc(0 * sizeof(jfieldID));
    return JVMTI_ERROR_NONE;
  }


  instanceKlassHandle instanceK_h(current_thread, k);

  int result_count = 0;
  // First, count the fields.
  FilteredFieldStream flds(instanceK_h, true, true);
  result_count = flds.field_count();

  // Allocate the result and fill it in
  jfieldID* result_list = (jfieldID*) jvmtiMalloc(result_count * sizeof(jfieldID));
  // The JVMTI spec requires fields in the order they occur in the class file,
  // this is the reverse order of what FieldStream hands out.
  int id_index = (result_count - 1);

  for (FilteredFieldStream src_st(instanceK_h, true, true); !src_st.eos(); src_st.next()) {
    result_list[id_index--] = jfieldIDWorkaround::to_jfieldID(
                                            instanceK_h, src_st.offset(),
                                            src_st.access_flags().is_static());
  }
  assert(id_index == -1, "just checking");
  // Fill in the results
  *field_count_ptr = result_count;
  *fields_ptr = result_list;

  return JVMTI_ERROR_NONE;
} /* end GetClassFields */


// k_mirror - may be primitive, this must be checked
// interface_count_ptr - pre-checked for NULL
// interfaces_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetImplementedInterfaces(oop k_mirror, jint* interface_count_ptr, jclass** interfaces_ptr) {
  {
    if (java_lang_Class::is_primitive(k_mirror)) {
      *interface_count_ptr = 0;
      *interfaces_ptr = (jclass*) jvmtiMalloc(0 * sizeof(jclass));
      return JVMTI_ERROR_NONE;
    }
    JavaThread* current_thread = JavaThread::current();
    HandleMark hm(current_thread);
    Klass* k = java_lang_Class::as_Klass(k_mirror);
    NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);

    // Return CLASS_NOT_PREPARED error as per JVMTI spec.
    if (!(k->jvmti_class_status() & (JVMTI_CLASS_STATUS_PREPARED|JVMTI_CLASS_STATUS_ARRAY) ))
      return JVMTI_ERROR_CLASS_NOT_PREPARED;

    if (!k->is_instance_klass()) {
      *interface_count_ptr = 0;
      *interfaces_ptr = (jclass*) jvmtiMalloc(0 * sizeof(jclass));
      return JVMTI_ERROR_NONE;
    }

    Array<Klass*>* interface_list = InstanceKlass::cast(k)->local_interfaces();
    const int result_length = (interface_list == NULL ? 0 : interface_list->length());
    jclass* result_list = (jclass*) jvmtiMalloc(result_length * sizeof(jclass));
    for (int i_index = 0; i_index < result_length; i_index += 1) {
      Klass* klass_at = interface_list->at(i_index);
      assert(klass_at->is_klass(), "interfaces must be Klass*s");
      assert(klass_at->is_interface(), "interfaces must be interfaces");
      oop mirror_at = klass_at->java_mirror();
      Handle handle_at = Handle(current_thread, mirror_at);
      result_list[i_index] = (jclass) jni_reference(handle_at);
    }
    *interface_count_ptr = result_length;
    *interfaces_ptr = result_list;
  }

  return JVMTI_ERROR_NONE;
} /* end GetImplementedInterfaces */


// k_mirror - may be primitive, this must be checked
// minor_version_ptr - pre-checked for NULL
// major_version_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetClassVersionNumbers(oop k_mirror, jint* minor_version_ptr, jint* major_version_ptr) {
  if (java_lang_Class::is_primitive(k_mirror)) {
    return JVMTI_ERROR_ABSENT_INFORMATION;
  }
  Klass* k_oop = java_lang_Class::as_Klass(k_mirror);
  Thread *thread = Thread::current();
  HandleMark hm(thread);
  KlassHandle klass(thread, k_oop);

  jint status = klass->jvmti_class_status();
  if (status & (JVMTI_CLASS_STATUS_ERROR)) {
    return JVMTI_ERROR_INVALID_CLASS;
  }
  if (status & (JVMTI_CLASS_STATUS_ARRAY)) {
    return JVMTI_ERROR_ABSENT_INFORMATION;
  }

  instanceKlassHandle ik(thread, k_oop);
  *minor_version_ptr = ik->minor_version();
  *major_version_ptr = ik->major_version();

  return JVMTI_ERROR_NONE;
} /* end GetClassVersionNumbers */


// k_mirror - may be primitive, this must be checked
// constant_pool_count_ptr - pre-checked for NULL
// constant_pool_byte_count_ptr - pre-checked for NULL
// constant_pool_bytes_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetConstantPool(oop k_mirror, jint* constant_pool_count_ptr, jint* constant_pool_byte_count_ptr, unsigned char** constant_pool_bytes_ptr) {
  if (java_lang_Class::is_primitive(k_mirror)) {
    return JVMTI_ERROR_ABSENT_INFORMATION;
  }

  Klass* k_oop = java_lang_Class::as_Klass(k_mirror);
  Thread *thread = Thread::current();
  HandleMark hm(thread);
  ResourceMark rm(thread);
  KlassHandle klass(thread, k_oop);

  jint status = klass->jvmti_class_status();
  if (status & (JVMTI_CLASS_STATUS_ERROR)) {
    return JVMTI_ERROR_INVALID_CLASS;
  }
  if (status & (JVMTI_CLASS_STATUS_ARRAY)) {
    return JVMTI_ERROR_ABSENT_INFORMATION;
  }

  instanceKlassHandle ikh(thread, k_oop);
  JvmtiConstantPoolReconstituter reconstituter(ikh);
  if (reconstituter.get_error() != JVMTI_ERROR_NONE) {
    return reconstituter.get_error();
  }

  unsigned char *cpool_bytes;
  int cpool_size = reconstituter.cpool_size();
  if (reconstituter.get_error() != JVMTI_ERROR_NONE) {
    return reconstituter.get_error();
  }
  jvmtiError res = allocate(cpool_size, &cpool_bytes);
  if (res != JVMTI_ERROR_NONE) {
    return res;
  }
  reconstituter.copy_cpool_bytes(cpool_bytes);
  if (reconstituter.get_error() != JVMTI_ERROR_NONE) {
    return reconstituter.get_error();
  }

  constantPoolHandle  constants(thread, ikh->constants());
  *constant_pool_count_ptr      = constants->length();
  *constant_pool_byte_count_ptr = cpool_size;
  *constant_pool_bytes_ptr      = cpool_bytes;

  return JVMTI_ERROR_NONE;
} /* end GetConstantPool */


// k_mirror - may be primitive, this must be checked
// is_interface_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::IsInterface(oop k_mirror, jboolean* is_interface_ptr) {
  {
    bool result = false;
    if (!java_lang_Class::is_primitive(k_mirror)) {
      Klass* k = java_lang_Class::as_Klass(k_mirror);
      if (k != NULL && k->is_interface()) {
        result = true;
      }
    }
    *is_interface_ptr = result;
  }

  return JVMTI_ERROR_NONE;
} /* end IsInterface */


// k_mirror - may be primitive, this must be checked
// is_array_class_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::IsArrayClass(oop k_mirror, jboolean* is_array_class_ptr) {
  {
    bool result = false;
    if (!java_lang_Class::is_primitive(k_mirror)) {
      Klass* k = java_lang_Class::as_Klass(k_mirror);
      if (k != NULL && k->is_array_klass()) {
        result = true;
      }
    }
    *is_array_class_ptr = result;
  }

  return JVMTI_ERROR_NONE;
} /* end IsArrayClass */


// k_mirror - may be primitive, this must be checked
// classloader_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetClassLoader(oop k_mirror, jobject* classloader_ptr) {
  {
    if (java_lang_Class::is_primitive(k_mirror)) {
      *classloader_ptr = (jclass) jni_reference(Handle());
      return JVMTI_ERROR_NONE;
    }
    JavaThread* current_thread = JavaThread::current();
    HandleMark hm(current_thread);
    Klass* k = java_lang_Class::as_Klass(k_mirror);
    NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);

    oop result_oop = k->class_loader();
    if (result_oop == NULL) {
      *classloader_ptr = (jclass) jni_reference(Handle());
      return JVMTI_ERROR_NONE;
    }
    Handle result_handle = Handle(current_thread, result_oop);
    jclass result_jnihandle = (jclass) jni_reference(result_handle);
    *classloader_ptr = result_jnihandle;
  }
  return JVMTI_ERROR_NONE;
} /* end GetClassLoader */


// k_mirror - may be primitive, this must be checked
// source_debug_extension_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetSourceDebugExtension(oop k_mirror, char** source_debug_extension_ptr) {
  {
    if (java_lang_Class::is_primitive(k_mirror)) {
      return JVMTI_ERROR_ABSENT_INFORMATION;
    }
    Klass* k = java_lang_Class::as_Klass(k_mirror);
    NULL_CHECK(k, JVMTI_ERROR_INVALID_CLASS);
    if (!k->is_instance_klass()) {
      return JVMTI_ERROR_ABSENT_INFORMATION;
    }
    const char* sde = InstanceKlass::cast(k)->source_debug_extension();
    NULL_CHECK(sde, JVMTI_ERROR_ABSENT_INFORMATION);

    {
      *source_debug_extension_ptr = (char *) jvmtiMalloc(strlen(sde)+1);
      strcpy(*source_debug_extension_ptr, sde);
    }
  }

  return JVMTI_ERROR_NONE;
} /* end GetSourceDebugExtension */

  //
  // Object functions
  //

// hash_code_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetObjectHashCode(jobject object, jint* hash_code_ptr) {
  oop mirror = JNIHandles::resolve_external_guard(object);
  NULL_CHECK(mirror, JVMTI_ERROR_INVALID_OBJECT);
  NULL_CHECK(hash_code_ptr, JVMTI_ERROR_NULL_POINTER);

  {
    jint result = (jint) mirror->identity_hash();
    *hash_code_ptr = result;
  }
  return JVMTI_ERROR_NONE;
} /* end GetObjectHashCode */


// info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetObjectMonitorUsage(jobject object, jvmtiMonitorUsage* info_ptr) {
  JavaThread* calling_thread = JavaThread::current();
  jvmtiError err = get_object_monitor_usage(calling_thread, object, info_ptr);
  if (err == JVMTI_ERROR_THREAD_NOT_SUSPENDED) {
    // Some of the critical threads were not suspended. go to a safepoint and try again
    VM_GetObjectMonitorUsage op(this, calling_thread, object, info_ptr);
    VMThread::execute(&op);
    err = op.result();
  }
  return err;
} /* end GetObjectMonitorUsage */


  //
  // Field functions
  //

// name_ptr - NULL is a valid value, must be checked
// signature_ptr - NULL is a valid value, must be checked
// generic_ptr - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::GetFieldName(fieldDescriptor* fdesc_ptr, char** name_ptr, char** signature_ptr, char** generic_ptr) {
  JavaThread* current_thread  = JavaThread::current();
  ResourceMark rm(current_thread);
  if (name_ptr == NULL) {
    // just don't return the name
  } else {
    const char* fieldName = fdesc_ptr->name()->as_C_string();
    *name_ptr =  (char*) jvmtiMalloc(strlen(fieldName) + 1);
    if (*name_ptr == NULL)
      return JVMTI_ERROR_OUT_OF_MEMORY;
    strcpy(*name_ptr, fieldName);
  }
  if (signature_ptr== NULL) {
    // just don't return the signature
  } else {
    const char* fieldSignature = fdesc_ptr->signature()->as_C_string();
    *signature_ptr = (char*) jvmtiMalloc(strlen(fieldSignature) + 1);
    if (*signature_ptr == NULL)
      return JVMTI_ERROR_OUT_OF_MEMORY;
    strcpy(*signature_ptr, fieldSignature);
  }
  if (generic_ptr != NULL) {
    *generic_ptr = NULL;
    Symbol* soop = fdesc_ptr->generic_signature();
    if (soop != NULL) {
      const char* gen_sig = soop->as_C_string();
      if (gen_sig != NULL) {
        jvmtiError err = allocate(strlen(gen_sig) + 1, (unsigned char **)generic_ptr);
        if (err != JVMTI_ERROR_NONE) {
          return err;
        }
        strcpy(*generic_ptr, gen_sig);
      }
    }
  }
  return JVMTI_ERROR_NONE;
} /* end GetFieldName */


// declaring_class_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetFieldDeclaringClass(fieldDescriptor* fdesc_ptr, jclass* declaring_class_ptr) {

  *declaring_class_ptr = get_jni_class_non_null(fdesc_ptr->field_holder());
  return JVMTI_ERROR_NONE;
} /* end GetFieldDeclaringClass */


// modifiers_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetFieldModifiers(fieldDescriptor* fdesc_ptr, jint* modifiers_ptr) {

  AccessFlags resultFlags = fdesc_ptr->access_flags();
  jint result = resultFlags.as_int();
  *modifiers_ptr = result;

  return JVMTI_ERROR_NONE;
} /* end GetFieldModifiers */


// is_synthetic_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::IsFieldSynthetic(fieldDescriptor* fdesc_ptr, jboolean* is_synthetic_ptr) {
  *is_synthetic_ptr = fdesc_ptr->is_synthetic();
  return JVMTI_ERROR_NONE;
} /* end IsFieldSynthetic */


  //
  // Method functions
  //

// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// name_ptr - NULL is a valid value, must be checked
// signature_ptr - NULL is a valid value, must be checked
// generic_ptr - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::GetMethodName(Method* method_oop, char** name_ptr, char** signature_ptr, char** generic_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  JavaThread* current_thread  = JavaThread::current();

  ResourceMark rm(current_thread); // get the utf8 name and signature
  if (name_ptr == NULL) {
    // just don't return the name
  } else {
    const char* utf8_name = (const char *) method_oop->name()->as_utf8();
    *name_ptr = (char *) jvmtiMalloc(strlen(utf8_name)+1);
    strcpy(*name_ptr, utf8_name);
  }
  if (signature_ptr == NULL) {
    // just don't return the signature
  } else {
    const char* utf8_signature = (const char *) method_oop->signature()->as_utf8();
    *signature_ptr = (char *) jvmtiMalloc(strlen(utf8_signature) + 1);
    strcpy(*signature_ptr, utf8_signature);
  }

  if (generic_ptr != NULL) {
    *generic_ptr = NULL;
    Symbol* soop = method_oop->generic_signature();
    if (soop != NULL) {
      const char* gen_sig = soop->as_C_string();
      if (gen_sig != NULL) {
        jvmtiError err = allocate(strlen(gen_sig) + 1, (unsigned char **)generic_ptr);
        if (err != JVMTI_ERROR_NONE) {
          return err;
        }
        strcpy(*generic_ptr, gen_sig);
      }
    }
  }
  return JVMTI_ERROR_NONE;
} /* end GetMethodName */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// declaring_class_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetMethodDeclaringClass(Method* method_oop, jclass* declaring_class_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  (*declaring_class_ptr) = get_jni_class_non_null(method_oop->method_holder());
  return JVMTI_ERROR_NONE;
} /* end GetMethodDeclaringClass */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// modifiers_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetMethodModifiers(Method* method_oop, jint* modifiers_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  (*modifiers_ptr) = method_oop->access_flags().as_int() & JVM_RECOGNIZED_METHOD_MODIFIERS;
  return JVMTI_ERROR_NONE;
} /* end GetMethodModifiers */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// max_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetMaxLocals(Method* method_oop, jint* max_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  // get max stack
  (*max_ptr) = method_oop->max_locals();
  return JVMTI_ERROR_NONE;
} /* end GetMaxLocals */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// size_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetArgumentsSize(Method* method_oop, jint* size_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  // get size of arguments

  (*size_ptr) = method_oop->size_of_parameters();
  return JVMTI_ERROR_NONE;
} /* end GetArgumentsSize */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// entry_count_ptr - pre-checked for NULL
// table_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLineNumberTable(Method* method_oop, jint* entry_count_ptr, jvmtiLineNumberEntry** table_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  if (!method_oop->has_linenumber_table()) {
    return (JVMTI_ERROR_ABSENT_INFORMATION);
  }

  // The line number table is compressed so we don't know how big it is until decompressed.
  // Decompression is really fast so we just do it twice.

  // Compute size of table
  jint num_entries = 0;
  CompressedLineNumberReadStream stream(method_oop->compressed_linenumber_table());
  while (stream.read_pair()) {
    num_entries++;
  }
  jvmtiLineNumberEntry *jvmti_table =
            (jvmtiLineNumberEntry *)jvmtiMalloc(num_entries * (sizeof(jvmtiLineNumberEntry)));

  // Fill jvmti table
  if (num_entries > 0) {
    int index = 0;
    CompressedLineNumberReadStream stream(method_oop->compressed_linenumber_table());
    while (stream.read_pair()) {
      jvmti_table[index].start_location = (jlocation) stream.bci();
      jvmti_table[index].line_number = (jint) stream.line();
      index++;
    }
    assert(index == num_entries, "sanity check");
  }

  // Set up results
  (*entry_count_ptr) = num_entries;
  (*table_ptr) = jvmti_table;

  return JVMTI_ERROR_NONE;
} /* end GetLineNumberTable */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// start_location_ptr - pre-checked for NULL
// end_location_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetMethodLocation(Method* method_oop, jlocation* start_location_ptr, jlocation* end_location_ptr) {

  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  // get start and end location
  (*end_location_ptr) = (jlocation) (method_oop->code_size() - 1);
  if (method_oop->code_size() == 0) {
    // there is no code so there is no start location
    (*start_location_ptr) = (jlocation)(-1);
  } else {
    (*start_location_ptr) = (jlocation)(0);
  }

  return JVMTI_ERROR_NONE;
} /* end GetMethodLocation */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// entry_count_ptr - pre-checked for NULL
// table_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetLocalVariableTable(Method* method_oop, jint* entry_count_ptr, jvmtiLocalVariableEntry** table_ptr) {

  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  JavaThread* current_thread  = JavaThread::current();

  // does the klass have any local variable information?
  InstanceKlass* ik = method_oop->method_holder();
  if (!ik->access_flags().has_localvariable_table()) {
    return (JVMTI_ERROR_ABSENT_INFORMATION);
  }

  ConstantPool* constants = method_oop->constants();
  NULL_CHECK(constants, JVMTI_ERROR_ABSENT_INFORMATION);

  // in the vm localvariable table representation, 6 consecutive elements in the table
  // represent a 6-tuple of shorts
  // [start_pc, length, name_index, descriptor_index, signature_index, index]
  jint num_entries = method_oop->localvariable_table_length();
  jvmtiLocalVariableEntry *jvmti_table = (jvmtiLocalVariableEntry *)
                jvmtiMalloc(num_entries * (sizeof(jvmtiLocalVariableEntry)));

  if (num_entries > 0) {
    LocalVariableTableElement* table = method_oop->localvariable_table_start();
    for (int i = 0; i < num_entries; i++) {
      // get the 5 tuple information from the vm table
      jlocation start_location = (jlocation) table[i].start_bci;
      jint length = (jint) table[i].length;
      int name_index = (int) table[i].name_cp_index;
      int signature_index = (int) table[i].descriptor_cp_index;
      int generic_signature_index = (int) table[i].signature_cp_index;
      jint slot = (jint) table[i].slot;

      // get utf8 name and signature
      char *name_buf = NULL;
      char *sig_buf = NULL;
      char *gen_sig_buf = NULL;
      {
        ResourceMark rm(current_thread);

        const char *utf8_name = (const char *) constants->symbol_at(name_index)->as_utf8();
        name_buf = (char *) jvmtiMalloc(strlen(utf8_name)+1);
        strcpy(name_buf, utf8_name);

        const char *utf8_signature = (const char *) constants->symbol_at(signature_index)->as_utf8();
        sig_buf = (char *) jvmtiMalloc(strlen(utf8_signature)+1);
        strcpy(sig_buf, utf8_signature);

        if (generic_signature_index > 0) {
          const char *utf8_gen_sign = (const char *)
                                       constants->symbol_at(generic_signature_index)->as_utf8();
          gen_sig_buf = (char *) jvmtiMalloc(strlen(utf8_gen_sign)+1);
          strcpy(gen_sig_buf, utf8_gen_sign);
        }
      }

      // fill in the jvmti local variable table
      jvmti_table[i].start_location = start_location;
      jvmti_table[i].length = length;
      jvmti_table[i].name = name_buf;
      jvmti_table[i].signature = sig_buf;
      jvmti_table[i].generic_signature = gen_sig_buf;
      jvmti_table[i].slot = slot;
    }
  }

  // set results
  (*entry_count_ptr) = num_entries;
  (*table_ptr) = jvmti_table;

  return JVMTI_ERROR_NONE;
} /* end GetLocalVariableTable */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// bytecode_count_ptr - pre-checked for NULL
// bytecodes_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetBytecodes(Method* method_oop, jint* bytecode_count_ptr, unsigned char** bytecodes_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);

  HandleMark hm;
  methodHandle method(method_oop);
  jint size = (jint)method->code_size();
  jvmtiError err = allocate(size, bytecodes_ptr);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  (*bytecode_count_ptr) = size;
  // get byte codes
  JvmtiClassFileReconstituter::copy_bytecodes(method, *bytecodes_ptr);

  return JVMTI_ERROR_NONE;
} /* end GetBytecodes */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// is_native_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::IsMethodNative(Method* method_oop, jboolean* is_native_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  (*is_native_ptr) = method_oop->is_native();
  return JVMTI_ERROR_NONE;
} /* end IsMethodNative */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// is_synthetic_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::IsMethodSynthetic(Method* method_oop, jboolean* is_synthetic_ptr) {
  NULL_CHECK(method_oop, JVMTI_ERROR_INVALID_METHODID);
  (*is_synthetic_ptr) = method_oop->is_synthetic();
  return JVMTI_ERROR_NONE;
} /* end IsMethodSynthetic */


// method_oop - pre-checked for validity, but may be NULL meaning obsolete method
// is_obsolete_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::IsMethodObsolete(Method* method_oop, jboolean* is_obsolete_ptr) {
  if (use_version_1_0_semantics() &&
      get_capabilities()->can_redefine_classes == 0) {
    // This JvmtiEnv requested version 1.0 semantics and this function
    // requires the can_redefine_classes capability in version 1.0 so
    // we need to return an error here.
    return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
  }

  if (method_oop == NULL || method_oop->is_obsolete()) {
    *is_obsolete_ptr = true;
  } else {
    *is_obsolete_ptr = false;
  }
  return JVMTI_ERROR_NONE;
} /* end IsMethodObsolete */

  //
  // Raw Monitor functions
  //

// name - pre-checked for NULL
// monitor_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::CreateRawMonitor(const char* name, jrawMonitorID* monitor_ptr) {
  JvmtiRawMonitor* rmonitor = new JvmtiRawMonitor(name);
  NULL_CHECK(rmonitor, JVMTI_ERROR_OUT_OF_MEMORY);

  *monitor_ptr = (jrawMonitorID)rmonitor;

  return JVMTI_ERROR_NONE;
} /* end CreateRawMonitor */


// rmonitor - pre-checked for validity
jvmtiError
JvmtiEnv::DestroyRawMonitor(JvmtiRawMonitor * rmonitor) {
  if (Threads::number_of_threads() == 0) {
    // Remove this  monitor from pending raw monitors list
    // if it has entered in onload or start phase.
    JvmtiPendingMonitors::destroy(rmonitor);
  } else {
    Thread* thread  = Thread::current();
    if (rmonitor->is_entered(thread)) {
      // The caller owns this monitor which we are about to destroy.
      // We exit the underlying synchronization object so that the
      // "delete monitor" call below can work without an assertion
      // failure on systems that don't like destroying synchronization
      // objects that are locked.
      int r;
      intptr_t recursion = rmonitor->recursions();
      for (intptr_t i=0; i <= recursion; i++) {
        r = rmonitor->raw_exit(thread);
        assert(r == ObjectMonitor::OM_OK, "raw_exit should have worked");
        if (r != ObjectMonitor::OM_OK) {  // robustness
          return JVMTI_ERROR_INTERNAL;
        }
      }
    }
    if (rmonitor->owner() != NULL) {
      // The caller is trying to destroy a monitor that is locked by
      // someone else. While this is not forbidden by the JVMTI
      // spec, it will cause an assertion failure on systems that don't
      // like destroying synchronization objects that are locked.
      // We indicate a problem with the error return (and leak the
      // monitor's memory).
      return JVMTI_ERROR_NOT_MONITOR_OWNER;
    }
  }

  delete rmonitor;

  return JVMTI_ERROR_NONE;
} /* end DestroyRawMonitor */


// rmonitor - pre-checked for validity
jvmtiError
JvmtiEnv::RawMonitorEnter(JvmtiRawMonitor * rmonitor) {
  if (Threads::number_of_threads() == 0) {
    // No JavaThreads exist so ObjectMonitor enter cannot be
    // used, add this raw monitor to the pending list.
    // The pending monitors will be actually entered when
    // the VM is setup.
    // See transition_pending_raw_monitors in create_vm()
    // in thread.cpp.
    JvmtiPendingMonitors::enter(rmonitor);
  } else {
    int r = 0;
    Thread* thread = Thread::current();

    if (thread->is_Java_thread()) {
      JavaThread* current_thread = (JavaThread*)thread;

#ifdef PROPER_TRANSITIONS
      // Not really unknown but ThreadInVMfromNative does more than we want
      ThreadInVMfromUnknown __tiv;
      {
        ThreadBlockInVM __tbivm(current_thread);
        r = rmonitor->raw_enter(current_thread);
      }
#else
      /* Transition to thread_blocked without entering vm state          */
      /* This is really evil. Normally you can't undo _thread_blocked    */
      /* transitions like this because it would cause us to miss a       */
      /* safepoint but since the thread was already in _thread_in_native */
      /* the thread is not leaving a safepoint safe state and it will    */
      /* block when it tries to return from native. We can't safepoint   */
      /* block in here because we could deadlock the vmthread. Blech.    */

      JavaThreadState state = current_thread->thread_state();
      assert(state == _thread_in_native, "Must be _thread_in_native");
      // frame should already be walkable since we are in native
      assert(!current_thread->has_last_Java_frame() ||
             current_thread->frame_anchor()->walkable(), "Must be walkable");
      current_thread->set_thread_state(_thread_blocked);

      r = rmonitor->raw_enter(current_thread);
      // restore state, still at a safepoint safe state
      current_thread->set_thread_state(state);

#endif /* PROPER_TRANSITIONS */
      assert(r == ObjectMonitor::OM_OK, "raw_enter should have worked");
    } else {
      if (thread->is_VM_thread() || thread->is_ConcurrentGC_thread()) {
        r = rmonitor->raw_enter(thread);
      } else {
        ShouldNotReachHere();
      }
    }

    if (r != ObjectMonitor::OM_OK) {  // robustness
      return JVMTI_ERROR_INTERNAL;
    }
  }
  return JVMTI_ERROR_NONE;
} /* end RawMonitorEnter */


// rmonitor - pre-checked for validity
jvmtiError
JvmtiEnv::RawMonitorExit(JvmtiRawMonitor * rmonitor) {
  jvmtiError err = JVMTI_ERROR_NONE;

  if (Threads::number_of_threads() == 0) {
    // No JavaThreads exist so just remove this monitor from the pending list.
    // Bool value from exit is false if rmonitor is not in the list.
    if (!JvmtiPendingMonitors::exit(rmonitor)) {
      err = JVMTI_ERROR_NOT_MONITOR_OWNER;
    }
  } else {
    int r = 0;
    Thread* thread = Thread::current();

    if (thread->is_Java_thread()) {
      JavaThread* current_thread = (JavaThread*)thread;
#ifdef PROPER_TRANSITIONS
      // Not really unknown but ThreadInVMfromNative does more than we want
      ThreadInVMfromUnknown __tiv;
#endif /* PROPER_TRANSITIONS */
      r = rmonitor->raw_exit(current_thread);
    } else {
      if (thread->is_VM_thread() || thread->is_ConcurrentGC_thread()) {
        r = rmonitor->raw_exit(thread);
      } else {
        ShouldNotReachHere();
      }
    }

    if (r == ObjectMonitor::OM_ILLEGAL_MONITOR_STATE) {
      err = JVMTI_ERROR_NOT_MONITOR_OWNER;
    } else {
      assert(r == ObjectMonitor::OM_OK, "raw_exit should have worked");
      if (r != ObjectMonitor::OM_OK) {  // robustness
        err = JVMTI_ERROR_INTERNAL;
      }
    }
  }
  return err;
} /* end RawMonitorExit */


// rmonitor - pre-checked for validity
jvmtiError
JvmtiEnv::RawMonitorWait(JvmtiRawMonitor * rmonitor, jlong millis) {
  int r = 0;
  Thread* thread = Thread::current();

  if (thread->is_Java_thread()) {
    JavaThread* current_thread = (JavaThread*)thread;
#ifdef PROPER_TRANSITIONS
    // Not really unknown but ThreadInVMfromNative does more than we want
    ThreadInVMfromUnknown __tiv;
    {
      ThreadBlockInVM __tbivm(current_thread);
      r = rmonitor->raw_wait(millis, true, current_thread);
    }
#else
    /* Transition to thread_blocked without entering vm state          */
    /* This is really evil. Normally you can't undo _thread_blocked    */
    /* transitions like this because it would cause us to miss a       */
    /* safepoint but since the thread was already in _thread_in_native */
    /* the thread is not leaving a safepoint safe state and it will    */
    /* block when it tries to return from native. We can't safepoint   */
    /* block in here because we could deadlock the vmthread. Blech.    */

    JavaThreadState state = current_thread->thread_state();
    assert(state == _thread_in_native, "Must be _thread_in_native");
    // frame should already be walkable since we are in native
    assert(!current_thread->has_last_Java_frame() ||
           current_thread->frame_anchor()->walkable(), "Must be walkable");
    current_thread->set_thread_state(_thread_blocked);

    r = rmonitor->raw_wait(millis, true, current_thread);
    // restore state, still at a safepoint safe state
    current_thread->set_thread_state(state);

#endif /* PROPER_TRANSITIONS */
  } else {
    if (thread->is_VM_thread() || thread->is_ConcurrentGC_thread()) {
      r = rmonitor->raw_wait(millis, true, thread);
    } else {
      ShouldNotReachHere();
    }
  }

  switch (r) {
  case ObjectMonitor::OM_INTERRUPTED:
    return JVMTI_ERROR_INTERRUPT;
  case ObjectMonitor::OM_ILLEGAL_MONITOR_STATE:
    return JVMTI_ERROR_NOT_MONITOR_OWNER;
  }
  assert(r == ObjectMonitor::OM_OK, "raw_wait should have worked");
  if (r != ObjectMonitor::OM_OK) {  // robustness
    return JVMTI_ERROR_INTERNAL;
  }

  return JVMTI_ERROR_NONE;
} /* end RawMonitorWait */


// rmonitor - pre-checked for validity
jvmtiError
JvmtiEnv::RawMonitorNotify(JvmtiRawMonitor * rmonitor) {
  int r = 0;
  Thread* thread = Thread::current();

  if (thread->is_Java_thread()) {
    JavaThread* current_thread = (JavaThread*)thread;
    // Not really unknown but ThreadInVMfromNative does more than we want
    ThreadInVMfromUnknown __tiv;
    r = rmonitor->raw_notify(current_thread);
  } else {
    if (thread->is_VM_thread() || thread->is_ConcurrentGC_thread()) {
      r = rmonitor->raw_notify(thread);
    } else {
      ShouldNotReachHere();
    }
  }

  if (r == ObjectMonitor::OM_ILLEGAL_MONITOR_STATE) {
    return JVMTI_ERROR_NOT_MONITOR_OWNER;
  }
  assert(r == ObjectMonitor::OM_OK, "raw_notify should have worked");
  if (r != ObjectMonitor::OM_OK) {  // robustness
    return JVMTI_ERROR_INTERNAL;
  }

  return JVMTI_ERROR_NONE;
} /* end RawMonitorNotify */


// rmonitor - pre-checked for validity
jvmtiError
JvmtiEnv::RawMonitorNotifyAll(JvmtiRawMonitor * rmonitor) {
  int r = 0;
  Thread* thread = Thread::current();

  if (thread->is_Java_thread()) {
    JavaThread* current_thread = (JavaThread*)thread;
    ThreadInVMfromUnknown __tiv;
    r = rmonitor->raw_notifyAll(current_thread);
  } else {
    if (thread->is_VM_thread() || thread->is_ConcurrentGC_thread()) {
      r = rmonitor->raw_notifyAll(thread);
    } else {
      ShouldNotReachHere();
    }
  }

  if (r == ObjectMonitor::OM_ILLEGAL_MONITOR_STATE) {
    return JVMTI_ERROR_NOT_MONITOR_OWNER;
  }
  assert(r == ObjectMonitor::OM_OK, "raw_notifyAll should have worked");
  if (r != ObjectMonitor::OM_OK) {  // robustness
    return JVMTI_ERROR_INTERNAL;
  }

  return JVMTI_ERROR_NONE;
} /* end RawMonitorNotifyAll */


  //
  // JNI Function Interception functions
  //


// function_table - pre-checked for NULL
jvmtiError
JvmtiEnv::SetJNIFunctionTable(const jniNativeInterface* function_table) {
  // Copy jni function table at safepoint.
  VM_JNIFunctionTableCopier copier(function_table);
  VMThread::execute(&copier);

  return JVMTI_ERROR_NONE;
} /* end SetJNIFunctionTable */


// function_table - pre-checked for NULL
jvmtiError
JvmtiEnv::GetJNIFunctionTable(jniNativeInterface** function_table) {
  *function_table=(jniNativeInterface*)jvmtiMalloc(sizeof(jniNativeInterface));
  if (*function_table == NULL)
    return JVMTI_ERROR_OUT_OF_MEMORY;
  memcpy(*function_table,(JavaThread::current())->get_jni_functions(),sizeof(jniNativeInterface));
  return JVMTI_ERROR_NONE;
} /* end GetJNIFunctionTable */


  //
  // Event Management functions
  //

jvmtiError
JvmtiEnv::GenerateEvents(jvmtiEvent event_type) {
  // can only generate two event types
  if (event_type != JVMTI_EVENT_COMPILED_METHOD_LOAD &&
      event_type != JVMTI_EVENT_DYNAMIC_CODE_GENERATED) {
    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
  }

  // for compiled_method_load events we must check that the environment
  // has the can_generate_compiled_method_load_events capability.
  if (event_type == JVMTI_EVENT_COMPILED_METHOD_LOAD) {
    if (get_capabilities()->can_generate_compiled_method_load_events == 0) {
      return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
    }
    return JvmtiCodeBlobEvents::generate_compiled_method_load_events(this);
  } else {
    return JvmtiCodeBlobEvents::generate_dynamic_code_events(this);
  }

} /* end GenerateEvents */


  //
  // Extension Mechanism functions
  //

// extension_count_ptr - pre-checked for NULL
// extensions - pre-checked for NULL
jvmtiError
JvmtiEnv::GetExtensionFunctions(jint* extension_count_ptr, jvmtiExtensionFunctionInfo** extensions) {
  return JvmtiExtensions::get_functions(this, extension_count_ptr, extensions);
} /* end GetExtensionFunctions */


// extension_count_ptr - pre-checked for NULL
// extensions - pre-checked for NULL
jvmtiError
JvmtiEnv::GetExtensionEvents(jint* extension_count_ptr, jvmtiExtensionEventInfo** extensions) {
  return JvmtiExtensions::get_events(this, extension_count_ptr, extensions);
} /* end GetExtensionEvents */


// callback - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::SetExtensionEventCallback(jint extension_event_index, jvmtiExtensionEvent callback) {
  return JvmtiExtensions::set_event_callback(this, extension_event_index, callback);
} /* end SetExtensionEventCallback */

  //
  // Timers functions
  //

// info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetCurrentThreadCpuTimerInfo(jvmtiTimerInfo* info_ptr) {
  os::current_thread_cpu_time_info(info_ptr);
  return JVMTI_ERROR_NONE;
} /* end GetCurrentThreadCpuTimerInfo */


// nanos_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetCurrentThreadCpuTime(jlong* nanos_ptr) {
  *nanos_ptr = os::current_thread_cpu_time();
  return JVMTI_ERROR_NONE;
} /* end GetCurrentThreadCpuTime */


// info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadCpuTimerInfo(jvmtiTimerInfo* info_ptr) {
  os::thread_cpu_time_info(info_ptr);
  return JVMTI_ERROR_NONE;
} /* end GetThreadCpuTimerInfo */


// Threads_lock NOT held, java_thread not protected by lock
// java_thread - pre-checked
// nanos_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetThreadCpuTime(JavaThread* java_thread, jlong* nanos_ptr) {
  *nanos_ptr = os::thread_cpu_time(java_thread);
  return JVMTI_ERROR_NONE;
} /* end GetThreadCpuTime */


// info_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetTimerInfo(jvmtiTimerInfo* info_ptr) {
  os::javaTimeNanos_info(info_ptr);
  return JVMTI_ERROR_NONE;
} /* end GetTimerInfo */


// nanos_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetTime(jlong* nanos_ptr) {
  *nanos_ptr = os::javaTimeNanos();
  return JVMTI_ERROR_NONE;
} /* end GetTime */


// processor_count_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetAvailableProcessors(jint* processor_count_ptr) {
  *processor_count_ptr = os::active_processor_count();
  return JVMTI_ERROR_NONE;
} /* end GetAvailableProcessors */

  //
  // System Properties functions
  //

// count_ptr - pre-checked for NULL
// property_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetSystemProperties(jint* count_ptr, char*** property_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;

  *count_ptr = Arguments::PropertyList_count(Arguments::system_properties());

  err = allocate(*count_ptr * sizeof(char *), (unsigned char **)property_ptr);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }
  int i = 0 ;
  for (SystemProperty* p = Arguments::system_properties(); p != NULL && i < *count_ptr; p = p->next(), i++) {
    const char *key = p->key();
    char **tmp_value = *property_ptr+i;
    err = allocate((strlen(key)+1) * sizeof(char), (unsigned char**)tmp_value);
    if (err == JVMTI_ERROR_NONE) {
      strcpy(*tmp_value, key);
    } else {
      // clean up previously allocated memory.
      for (int j=0; j<i; j++) {
        Deallocate((unsigned char*)*property_ptr+j);
      }
      Deallocate((unsigned char*)property_ptr);
      break;
    }
  }
  return err;
} /* end GetSystemProperties */


// property - pre-checked for NULL
// value_ptr - pre-checked for NULL
jvmtiError
JvmtiEnv::GetSystemProperty(const char* property, char** value_ptr) {
  jvmtiError err = JVMTI_ERROR_NONE;
  const char *value;

  value = Arguments::PropertyList_get_value(Arguments::system_properties(), property);
  if (value == NULL) {
    err =  JVMTI_ERROR_NOT_AVAILABLE;
  } else {
    err = allocate((strlen(value)+1) * sizeof(char), (unsigned char **)value_ptr);
    if (err == JVMTI_ERROR_NONE) {
      strcpy(*value_ptr, value);
    }
  }
  return err;
} /* end GetSystemProperty */


// property - pre-checked for NULL
// value - NULL is a valid value, must be checked
jvmtiError
JvmtiEnv::SetSystemProperty(const char* property, const char* value_ptr) {
  jvmtiError err =JVMTI_ERROR_NOT_AVAILABLE;

  for (SystemProperty* p = Arguments::system_properties(); p != NULL; p = p->next()) {
    if (strcmp(property, p->key()) == 0) {
      if (p->set_writeable_value(value_ptr)) {
        err =  JVMTI_ERROR_NONE;
      }
    }
  }
  return err;
} /* end SetSystemProperty */
