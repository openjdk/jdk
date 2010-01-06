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

#include "incls/_precompiled.incl"
#include "incls/_jvm.cpp.incl"
#include <errno.h>

/*
  NOTE about use of any ctor or function call that can trigger a safepoint/GC:
  such ctors and calls MUST NOT come between an oop declaration/init and its
  usage because if objects are move this may cause various memory stomps, bus
  errors and segfaults. Here is a cookbook for causing so called "naked oop
  failures":

      JVM_ENTRY(jobjectArray, JVM_GetClassDeclaredFields<etc> {
          JVMWrapper("JVM_GetClassDeclaredFields");

          // Object address to be held directly in mirror & not visible to GC
          oop mirror = JNIHandles::resolve_non_null(ofClass);

          // If this ctor can hit a safepoint, moving objects around, then
          ComplexConstructor foo;

          // Boom! mirror may point to JUNK instead of the intended object
          (some dereference of mirror)

          // Here's another call that may block for GC, making mirror stale
          MutexLocker ml(some_lock);

          // And here's an initializer that can result in a stale oop
          // all in one step.
          oop o = call_that_can_throw_exception(TRAPS);


  The solution is to keep the oop declaration BELOW the ctor or function
  call that might cause a GC, do another resolve to reassign the oop, or
  consider use of a Handle instead of an oop so there is immunity from object
  motion. But note that the "QUICK" entries below do not have a handlemark
  and thus can only support use of handles passed in.
*/

static void trace_class_resolution_impl(klassOop to_class, TRAPS) {
  ResourceMark rm;
  int line_number = -1;
  const char * source_file = NULL;
  const char * trace = "explicit";
  klassOop caller = NULL;
  JavaThread* jthread = JavaThread::current();
  if (jthread->has_last_Java_frame()) {
    vframeStream vfst(jthread);

    // scan up the stack skipping ClassLoader, AccessController and PrivilegedAction frames
    symbolHandle access_controller = oopFactory::new_symbol_handle("java/security/AccessController", CHECK);
    klassOop access_controller_klass = SystemDictionary::resolve_or_fail(access_controller, false, CHECK);
    symbolHandle privileged_action = oopFactory::new_symbol_handle("java/security/PrivilegedAction", CHECK);
    klassOop privileged_action_klass = SystemDictionary::resolve_or_fail(privileged_action, false, CHECK);

    methodOop last_caller = NULL;

    while (!vfst.at_end()) {
      methodOop m = vfst.method();
      if (!vfst.method()->method_holder()->klass_part()->is_subclass_of(SystemDictionary::ClassLoader_klass())&&
          !vfst.method()->method_holder()->klass_part()->is_subclass_of(access_controller_klass) &&
          !vfst.method()->method_holder()->klass_part()->is_subclass_of(privileged_action_klass)) {
        break;
      }
      last_caller = m;
      vfst.next();
    }
    // if this is called from Class.forName0 and that is called from Class.forName,
    // then print the caller of Class.forName.  If this is Class.loadClass, then print
    // that caller, otherwise keep quiet since this should be picked up elsewhere.
    bool found_it = false;
    if (!vfst.at_end() &&
        instanceKlass::cast(vfst.method()->method_holder())->name() == vmSymbols::java_lang_Class() &&
        vfst.method()->name() == vmSymbols::forName0_name()) {
      vfst.next();
      if (!vfst.at_end() &&
          instanceKlass::cast(vfst.method()->method_holder())->name() == vmSymbols::java_lang_Class() &&
          vfst.method()->name() == vmSymbols::forName_name()) {
        vfst.next();
        found_it = true;
      }
    } else if (last_caller != NULL &&
               instanceKlass::cast(last_caller->method_holder())->name() ==
               vmSymbols::java_lang_ClassLoader() &&
               (last_caller->name() == vmSymbols::loadClassInternal_name() ||
                last_caller->name() == vmSymbols::loadClass_name())) {
      found_it = true;
    } else if (!vfst.at_end()) {
      if (vfst.method()->is_native()) {
        // JNI call
        found_it = true;
      }
    }
    if (found_it && !vfst.at_end()) {
      // found the caller
      caller = vfst.method()->method_holder();
      line_number = vfst.method()->line_number_from_bci(vfst.bci());
      if (line_number == -1) {
        // show method name if it's a native method
        trace = vfst.method()->name_and_sig_as_C_string();
      }
      symbolOop s = instanceKlass::cast(caller)->source_file_name();
      if (s != NULL) {
        source_file = s->as_C_string();
      }
    }
  }
  if (caller != NULL) {
    if (to_class != caller) {
      const char * from = Klass::cast(caller)->external_name();
      const char * to = Klass::cast(to_class)->external_name();
      // print in a single call to reduce interleaving between threads
      if (source_file != NULL) {
        tty->print("RESOLVE %s %s %s:%d (%s)\n", from, to, source_file, line_number, trace);
      } else {
        tty->print("RESOLVE %s %s (%s)\n", from, to, trace);
      }
    }
  }
}

void trace_class_resolution(klassOop to_class) {
  EXCEPTION_MARK;
  trace_class_resolution_impl(to_class, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
  }
}

// Wrapper to trace JVM functions

#ifdef ASSERT
  class JVMTraceWrapper : public StackObj {
   public:
    JVMTraceWrapper(const char* format, ...) {
      if (TraceJVMCalls) {
        va_list ap;
        va_start(ap, format);
        tty->print("JVM ");
        tty->vprint_cr(format, ap);
        va_end(ap);
      }
    }
  };

  Histogram* JVMHistogram;
  volatile jint JVMHistogram_lock = 0;

  class JVMHistogramElement : public HistogramElement {
    public:
     JVMHistogramElement(const char* name);
  };

  JVMHistogramElement::JVMHistogramElement(const char* elementName) {
    _name = elementName;
    uintx count = 0;

    while (Atomic::cmpxchg(1, &JVMHistogram_lock, 0) != 0) {
      while (OrderAccess::load_acquire(&JVMHistogram_lock) != 0) {
        count +=1;
        if ( (WarnOnStalledSpinLock > 0)
          && (count % WarnOnStalledSpinLock == 0)) {
          warning("JVMHistogram_lock seems to be stalled");
        }
      }
     }

    if(JVMHistogram == NULL)
      JVMHistogram = new Histogram("JVM Call Counts",100);

    JVMHistogram->add_element(this);
    Atomic::dec(&JVMHistogram_lock);
  }

  #define JVMCountWrapper(arg) \
      static JVMHistogramElement* e = new JVMHistogramElement(arg); \
      if (e != NULL) e->increment_count();  // Due to bug in VC++, we need a NULL check here eventhough it should never happen!

  #define JVMWrapper(arg1)                    JVMCountWrapper(arg1); JVMTraceWrapper(arg1)
  #define JVMWrapper2(arg1, arg2)             JVMCountWrapper(arg1); JVMTraceWrapper(arg1, arg2)
  #define JVMWrapper3(arg1, arg2, arg3)       JVMCountWrapper(arg1); JVMTraceWrapper(arg1, arg2, arg3)
  #define JVMWrapper4(arg1, arg2, arg3, arg4) JVMCountWrapper(arg1); JVMTraceWrapper(arg1, arg2, arg3, arg4)
#else
  #define JVMWrapper(arg1)
  #define JVMWrapper2(arg1, arg2)
  #define JVMWrapper3(arg1, arg2, arg3)
  #define JVMWrapper4(arg1, arg2, arg3, arg4)
#endif


// Interface version /////////////////////////////////////////////////////////////////////


JVM_LEAF(jint, JVM_GetInterfaceVersion())
  return JVM_INTERFACE_VERSION;
JVM_END


// java.lang.System //////////////////////////////////////////////////////////////////////


JVM_LEAF(jlong, JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored))
  JVMWrapper("JVM_CurrentTimeMillis");
  return os::javaTimeMillis();
JVM_END

JVM_LEAF(jlong, JVM_NanoTime(JNIEnv *env, jclass ignored))
  JVMWrapper("JVM_NanoTime");
  return os::javaTimeNanos();
JVM_END


JVM_ENTRY(void, JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos,
                               jobject dst, jint dst_pos, jint length))
  JVMWrapper("JVM_ArrayCopy");
  // Check if we have null pointers
  if (src == NULL || dst == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }
  arrayOop s = arrayOop(JNIHandles::resolve_non_null(src));
  arrayOop d = arrayOop(JNIHandles::resolve_non_null(dst));
  assert(s->is_oop(), "JVM_ArrayCopy: src not an oop");
  assert(d->is_oop(), "JVM_ArrayCopy: dst not an oop");
  // Do copy
  Klass::cast(s->klass())->copy_array(s, src_pos, d, dst_pos, length, thread);
JVM_END


static void set_property(Handle props, const char* key, const char* value, TRAPS) {
  JavaValue r(T_OBJECT);
  // public synchronized Object put(Object key, Object value);
  HandleMark hm(THREAD);
  Handle key_str    = java_lang_String::create_from_platform_dependent_str(key, CHECK);
  Handle value_str  = java_lang_String::create_from_platform_dependent_str((value != NULL ? value : ""), CHECK);
  JavaCalls::call_virtual(&r,
                          props,
                          KlassHandle(THREAD, SystemDictionary::Properties_klass()),
                          vmSymbolHandles::put_name(),
                          vmSymbolHandles::object_object_object_signature(),
                          key_str,
                          value_str,
                          THREAD);
}


#define PUTPROP(props, name, value) set_property((props), (name), (value), CHECK_(properties));


JVM_ENTRY(jobject, JVM_InitProperties(JNIEnv *env, jobject properties))
  JVMWrapper("JVM_InitProperties");
  ResourceMark rm;

  Handle props(THREAD, JNIHandles::resolve_non_null(properties));

  // System property list includes both user set via -D option and
  // jvm system specific properties.
  for (SystemProperty* p = Arguments::system_properties(); p != NULL; p = p->next()) {
    PUTPROP(props, p->key(), p->value());
  }

  // Convert the -XX:MaxDirectMemorySize= command line flag
  // to the sun.nio.MaxDirectMemorySize property.
  // Do this after setting user properties to prevent people
  // from setting the value with a -D option, as requested.
  {
    char as_chars[256];
    jio_snprintf(as_chars, sizeof(as_chars), INTX_FORMAT, MaxDirectMemorySize);
    PUTPROP(props, "sun.nio.MaxDirectMemorySize", as_chars);
  }

  // JVM monitoring and management support
  // Add the sun.management.compiler property for the compiler's name
  {
#undef CSIZE
#if defined(_LP64) || defined(_WIN64)
  #define CSIZE "64-Bit "
#else
  #define CSIZE
#endif // 64bit

#ifdef TIERED
    const char* compiler_name = "HotSpot " CSIZE "Tiered Compilers";
#else
#if defined(COMPILER1)
    const char* compiler_name = "HotSpot " CSIZE "Client Compiler";
#elif defined(COMPILER2)
    const char* compiler_name = "HotSpot " CSIZE "Server Compiler";
#else
    const char* compiler_name = "";
#endif // compilers
#endif // TIERED

    if (*compiler_name != '\0' &&
        (Arguments::mode() != Arguments::_int)) {
      PUTPROP(props, "sun.management.compiler", compiler_name);
    }
  }

  return properties;
JVM_END


// java.lang.Runtime /////////////////////////////////////////////////////////////////////////

extern volatile jint vm_created;

JVM_ENTRY_NO_ENV(void, JVM_Exit(jint code))
  if (vm_created != 0 && (code == 0)) {
    // The VM is about to exit. We call back into Java to check whether finalizers should be run
    Universe::run_finalizers_on_exit();
  }
  before_exit(thread);
  vm_exit(code);
JVM_END


JVM_ENTRY_NO_ENV(void, JVM_Halt(jint code))
  before_exit(thread);
  vm_exit(code);
JVM_END


JVM_LEAF(void, JVM_OnExit(void (*func)(void)))
  register_on_exit_function(func);
JVM_END


JVM_ENTRY_NO_ENV(void, JVM_GC(void))
  JVMWrapper("JVM_GC");
  if (!DisableExplicitGC) {
    Universe::heap()->collect(GCCause::_java_lang_system_gc);
  }
JVM_END


JVM_LEAF(jlong, JVM_MaxObjectInspectionAge(void))
  JVMWrapper("JVM_MaxObjectInspectionAge");
  return Universe::heap()->millis_since_last_gc();
JVM_END


JVM_LEAF(void, JVM_TraceInstructions(jboolean on))
  if (PrintJVMWarnings) warning("JVM_TraceInstructions not supported");
JVM_END


JVM_LEAF(void, JVM_TraceMethodCalls(jboolean on))
  if (PrintJVMWarnings) warning("JVM_TraceMethodCalls not supported");
JVM_END

static inline jlong convert_size_t_to_jlong(size_t val) {
  // In the 64-bit vm, a size_t can overflow a jlong (which is signed).
  NOT_LP64 (return (jlong)val;)
  LP64_ONLY(return (jlong)MIN2(val, (size_t)max_jlong);)
}

JVM_ENTRY_NO_ENV(jlong, JVM_TotalMemory(void))
  JVMWrapper("JVM_TotalMemory");
  size_t n = Universe::heap()->capacity();
  return convert_size_t_to_jlong(n);
JVM_END


JVM_ENTRY_NO_ENV(jlong, JVM_FreeMemory(void))
  JVMWrapper("JVM_FreeMemory");
  CollectedHeap* ch = Universe::heap();
  size_t n;
  {
     MutexLocker x(Heap_lock);
     n = ch->capacity() - ch->used();
  }
  return convert_size_t_to_jlong(n);
JVM_END


JVM_ENTRY_NO_ENV(jlong, JVM_MaxMemory(void))
  JVMWrapper("JVM_MaxMemory");
  size_t n = Universe::heap()->max_capacity();
  return convert_size_t_to_jlong(n);
JVM_END


JVM_ENTRY_NO_ENV(jint, JVM_ActiveProcessorCount(void))
  JVMWrapper("JVM_ActiveProcessorCount");
  return os::active_processor_count();
JVM_END



// java.lang.Throwable //////////////////////////////////////////////////////


JVM_ENTRY(void, JVM_FillInStackTrace(JNIEnv *env, jobject receiver))
  JVMWrapper("JVM_FillInStackTrace");
  Handle exception(thread, JNIHandles::resolve_non_null(receiver));
  java_lang_Throwable::fill_in_stack_trace(exception);
JVM_END


JVM_ENTRY(void, JVM_PrintStackTrace(JNIEnv *env, jobject receiver, jobject printable))
  JVMWrapper("JVM_PrintStackTrace");
  // Note: This is no longer used in Merlin, but we still support it for compatibility.
  oop exception = JNIHandles::resolve_non_null(receiver);
  oop stream    = JNIHandles::resolve_non_null(printable);
  java_lang_Throwable::print_stack_trace(exception, stream);
JVM_END


JVM_ENTRY(jint, JVM_GetStackTraceDepth(JNIEnv *env, jobject throwable))
  JVMWrapper("JVM_GetStackTraceDepth");
  oop exception = JNIHandles::resolve(throwable);
  return java_lang_Throwable::get_stack_trace_depth(exception, THREAD);
JVM_END


JVM_ENTRY(jobject, JVM_GetStackTraceElement(JNIEnv *env, jobject throwable, jint index))
  JVMWrapper("JVM_GetStackTraceElement");
  JvmtiVMObjectAllocEventCollector oam; // This ctor (throughout this module) may trigger a safepoint/GC
  oop exception = JNIHandles::resolve(throwable);
  oop element = java_lang_Throwable::get_stack_trace_element(exception, index, CHECK_NULL);
  return JNIHandles::make_local(env, element);
JVM_END


// java.lang.Object ///////////////////////////////////////////////


JVM_ENTRY(jint, JVM_IHashCode(JNIEnv* env, jobject handle))
  JVMWrapper("JVM_IHashCode");
  // as implemented in the classic virtual machine; return 0 if object is NULL
  return handle == NULL ? 0 : ObjectSynchronizer::FastHashCode (THREAD, JNIHandles::resolve_non_null(handle)) ;
JVM_END


JVM_ENTRY(void, JVM_MonitorWait(JNIEnv* env, jobject handle, jlong ms))
  JVMWrapper("JVM_MonitorWait");
  Handle obj(THREAD, JNIHandles::resolve_non_null(handle));
  assert(obj->is_instance() || obj->is_array(), "JVM_MonitorWait must apply to an object");
  JavaThreadInObjectWaitState jtiows(thread, ms != 0);
  if (JvmtiExport::should_post_monitor_wait()) {
    JvmtiExport::post_monitor_wait((JavaThread *)THREAD, (oop)obj(), ms);
  }
  ObjectSynchronizer::wait(obj, ms, CHECK);
JVM_END


JVM_ENTRY(void, JVM_MonitorNotify(JNIEnv* env, jobject handle))
  JVMWrapper("JVM_MonitorNotify");
  Handle obj(THREAD, JNIHandles::resolve_non_null(handle));
  assert(obj->is_instance() || obj->is_array(), "JVM_MonitorNotify must apply to an object");
  ObjectSynchronizer::notify(obj, CHECK);
JVM_END


JVM_ENTRY(void, JVM_MonitorNotifyAll(JNIEnv* env, jobject handle))
  JVMWrapper("JVM_MonitorNotifyAll");
  Handle obj(THREAD, JNIHandles::resolve_non_null(handle));
  assert(obj->is_instance() || obj->is_array(), "JVM_MonitorNotifyAll must apply to an object");
  ObjectSynchronizer::notifyall(obj, CHECK);
JVM_END


JVM_ENTRY(jobject, JVM_Clone(JNIEnv* env, jobject handle))
  JVMWrapper("JVM_Clone");
  Handle obj(THREAD, JNIHandles::resolve_non_null(handle));
  const KlassHandle klass (THREAD, obj->klass());
  JvmtiVMObjectAllocEventCollector oam;

#ifdef ASSERT
  // Just checking that the cloneable flag is set correct
  if (obj->is_javaArray()) {
    guarantee(klass->is_cloneable(), "all arrays are cloneable");
  } else {
    guarantee(obj->is_instance(), "should be instanceOop");
    bool cloneable = klass->is_subtype_of(SystemDictionary::Cloneable_klass());
    guarantee(cloneable == klass->is_cloneable(), "incorrect cloneable flag");
  }
#endif

  // Check if class of obj supports the Cloneable interface.
  // All arrays are considered to be cloneable (See JLS 20.1.5)
  if (!klass->is_cloneable()) {
    ResourceMark rm(THREAD);
    THROW_MSG_0(vmSymbols::java_lang_CloneNotSupportedException(), klass->external_name());
  }

  // Make shallow object copy
  const int size = obj->size();
  oop new_obj = NULL;
  if (obj->is_javaArray()) {
    const int length = ((arrayOop)obj())->length();
    new_obj = CollectedHeap::array_allocate(klass, size, length, CHECK_NULL);
  } else {
    new_obj = CollectedHeap::obj_allocate(klass, size, CHECK_NULL);
  }
  // 4839641 (4840070): We must do an oop-atomic copy, because if another thread
  // is modifying a reference field in the clonee, a non-oop-atomic copy might
  // be suspended in the middle of copying the pointer and end up with parts
  // of two different pointers in the field.  Subsequent dereferences will crash.
  // 4846409: an oop-copy of objects with long or double fields or arrays of same
  // won't copy the longs/doubles atomically in 32-bit vm's, so we copy jlongs instead
  // of oops.  We know objects are aligned on a minimum of an jlong boundary.
  // The same is true of StubRoutines::object_copy and the various oop_copy
  // variants, and of the code generated by the inline_native_clone intrinsic.
  assert(MinObjAlignmentInBytes >= BytesPerLong, "objects misaligned");
  Copy::conjoint_jlongs_atomic((jlong*)obj(), (jlong*)new_obj,
                               (size_t)align_object_size(size) / HeapWordsPerLong);
  // Clear the header
  new_obj->init_mark();

  // Store check (mark entire object and let gc sort it out)
  BarrierSet* bs = Universe::heap()->barrier_set();
  assert(bs->has_write_region_opt(), "Barrier set does not have write_region");
  bs->write_region(MemRegion((HeapWord*)new_obj, size));

  // Caution: this involves a java upcall, so the clone should be
  // "gc-robust" by this stage.
  if (klass->has_finalizer()) {
    assert(obj->is_instance(), "should be instanceOop");
    new_obj = instanceKlass::register_finalizer(instanceOop(new_obj), CHECK_NULL);
  }

  return JNIHandles::make_local(env, oop(new_obj));
JVM_END

// java.lang.Compiler ////////////////////////////////////////////////////

// The initial cuts of the HotSpot VM will not support JITs, and all existing
// JITs would need extensive changes to work with HotSpot.  The JIT-related JVM
// functions are all silently ignored unless JVM warnings are printed.

JVM_LEAF(void, JVM_InitializeCompiler (JNIEnv *env, jclass compCls))
  if (PrintJVMWarnings) warning("JVM_InitializeCompiler not supported");
JVM_END


JVM_LEAF(jboolean, JVM_IsSilentCompiler(JNIEnv *env, jclass compCls))
  if (PrintJVMWarnings) warning("JVM_IsSilentCompiler not supported");
  return JNI_FALSE;
JVM_END


JVM_LEAF(jboolean, JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls))
  if (PrintJVMWarnings) warning("JVM_CompileClass not supported");
  return JNI_FALSE;
JVM_END


JVM_LEAF(jboolean, JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname))
  if (PrintJVMWarnings) warning("JVM_CompileClasses not supported");
  return JNI_FALSE;
JVM_END


JVM_LEAF(jobject, JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg))
  if (PrintJVMWarnings) warning("JVM_CompilerCommand not supported");
  return NULL;
JVM_END


JVM_LEAF(void, JVM_EnableCompiler(JNIEnv *env, jclass compCls))
  if (PrintJVMWarnings) warning("JVM_EnableCompiler not supported");
JVM_END


JVM_LEAF(void, JVM_DisableCompiler(JNIEnv *env, jclass compCls))
  if (PrintJVMWarnings) warning("JVM_DisableCompiler not supported");
JVM_END



// Error message support //////////////////////////////////////////////////////

JVM_LEAF(jint, JVM_GetLastErrorString(char *buf, int len))
  JVMWrapper("JVM_GetLastErrorString");
  return hpi::lasterror(buf, len);
JVM_END


// java.io.File ///////////////////////////////////////////////////////////////

JVM_LEAF(char*, JVM_NativePath(char* path))
  JVMWrapper2("JVM_NativePath (%s)", path);
  return hpi::native_path(path);
JVM_END


// Misc. class handling ///////////////////////////////////////////////////////////


JVM_ENTRY(jclass, JVM_GetCallerClass(JNIEnv* env, int depth))
  JVMWrapper("JVM_GetCallerClass");
  klassOop k = thread->security_get_caller_class(depth);
  return (k == NULL) ? NULL : (jclass) JNIHandles::make_local(env, Klass::cast(k)->java_mirror());
JVM_END


JVM_ENTRY(jclass, JVM_FindPrimitiveClass(JNIEnv* env, const char* utf))
  JVMWrapper("JVM_FindPrimitiveClass");
  oop mirror = NULL;
  BasicType t = name2type(utf);
  if (t != T_ILLEGAL && t != T_OBJECT && t != T_ARRAY) {
    mirror = Universe::java_mirror(t);
  }
  if (mirror == NULL) {
    THROW_MSG_0(vmSymbols::java_lang_ClassNotFoundException(), (char*) utf);
  } else {
    return (jclass) JNIHandles::make_local(env, mirror);
  }
JVM_END


JVM_ENTRY(void, JVM_ResolveClass(JNIEnv* env, jclass cls))
  JVMWrapper("JVM_ResolveClass");
  if (PrintJVMWarnings) warning("JVM_ResolveClass not implemented");
JVM_END


// Returns a class loaded by the bootstrap class loader; or null
// if not found.  ClassNotFoundException is not thrown.
//
// Rationale behind JVM_FindClassFromBootLoader
// a> JVM_FindClassFromClassLoader was never exported in the export tables.
// b> because of (a) java.dll has a direct dependecy on the  unexported
//    private symbol "_JVM_FindClassFromClassLoader@20".
// c> the launcher cannot use the private symbol as it dynamically opens
//    the entry point, so if something changes, the launcher will fail
//    unexpectedly at runtime, it is safest for the launcher to dlopen a
//    stable exported interface.
// d> re-exporting JVM_FindClassFromClassLoader as public, will cause its
//    signature to change from _JVM_FindClassFromClassLoader@20 to
//    JVM_FindClassFromClassLoader and will not be backward compatible
//    with older JDKs.
// Thus a public/stable exported entry point is the right solution,
// public here means public in linker semantics, and is exported only
// to the JDK, and is not intended to be a public API.

JVM_ENTRY(jclass, JVM_FindClassFromBootLoader(JNIEnv* env,
                                              const char* name))
  JVMWrapper2("JVM_FindClassFromBootLoader %s", name);

  // Java libraries should ensure that name is never null...
  if (name == NULL || (int)strlen(name) > symbolOopDesc::max_length()) {
    // It's impossible to create this class;  the name cannot fit
    // into the constant pool.
    return NULL;
  }

  symbolHandle h_name = oopFactory::new_symbol_handle(name, CHECK_NULL);
  klassOop k = SystemDictionary::resolve_or_null(h_name, CHECK_NULL);
  if (k == NULL) {
    return NULL;
  }

  if (TraceClassResolution) {
    trace_class_resolution(k);
  }
  return (jclass) JNIHandles::make_local(env, Klass::cast(k)->java_mirror());
JVM_END

JVM_ENTRY(jclass, JVM_FindClassFromClassLoader(JNIEnv* env, const char* name,
                                               jboolean init, jobject loader,
                                               jboolean throwError))
  JVMWrapper3("JVM_FindClassFromClassLoader %s throw %s", name,
               throwError ? "error" : "exception");
  // Java libraries should ensure that name is never null...
  if (name == NULL || (int)strlen(name) > symbolOopDesc::max_length()) {
    // It's impossible to create this class;  the name cannot fit
    // into the constant pool.
    if (throwError) {
      THROW_MSG_0(vmSymbols::java_lang_NoClassDefFoundError(), name);
    } else {
      THROW_MSG_0(vmSymbols::java_lang_ClassNotFoundException(), name);
    }
  }
  symbolHandle h_name = oopFactory::new_symbol_handle(name, CHECK_NULL);
  Handle h_loader(THREAD, JNIHandles::resolve(loader));
  jclass result = find_class_from_class_loader(env, h_name, init, h_loader,
                                               Handle(), throwError, THREAD);

  if (TraceClassResolution && result != NULL) {
    trace_class_resolution(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(result)));
  }
  return result;
JVM_END


JVM_ENTRY(jclass, JVM_FindClassFromClass(JNIEnv *env, const char *name,
                                         jboolean init, jclass from))
  JVMWrapper2("JVM_FindClassFromClass %s", name);
  if (name == NULL || (int)strlen(name) > symbolOopDesc::max_length()) {
    // It's impossible to create this class;  the name cannot fit
    // into the constant pool.
    THROW_MSG_0(vmSymbols::java_lang_NoClassDefFoundError(), name);
  }
  symbolHandle h_name = oopFactory::new_symbol_handle(name, CHECK_NULL);
  oop from_class_oop = JNIHandles::resolve(from);
  klassOop from_class = (from_class_oop == NULL)
                           ? (klassOop)NULL
                           : java_lang_Class::as_klassOop(from_class_oop);
  oop class_loader = NULL;
  oop protection_domain = NULL;
  if (from_class != NULL) {
    class_loader = Klass::cast(from_class)->class_loader();
    protection_domain = Klass::cast(from_class)->protection_domain();
  }
  Handle h_loader(THREAD, class_loader);
  Handle h_prot  (THREAD, protection_domain);
  jclass result = find_class_from_class_loader(env, h_name, init, h_loader,
                                               h_prot, true, thread);

  if (TraceClassResolution && result != NULL) {
    // this function is generally only used for class loading during verification.
    ResourceMark rm;
    oop from_mirror = JNIHandles::resolve_non_null(from);
    klassOop from_class = java_lang_Class::as_klassOop(from_mirror);
    const char * from_name = Klass::cast(from_class)->external_name();

    oop mirror = JNIHandles::resolve_non_null(result);
    klassOop to_class = java_lang_Class::as_klassOop(mirror);
    const char * to = Klass::cast(to_class)->external_name();
    tty->print("RESOLVE %s %s (verification)\n", from_name, to);
  }

  return result;
JVM_END

static void is_lock_held_by_thread(Handle loader, PerfCounter* counter, TRAPS) {
  if (loader.is_null()) {
    return;
  }

  // check whether the current caller thread holds the lock or not.
  // If not, increment the corresponding counter
  if (ObjectSynchronizer::query_lock_ownership((JavaThread*)THREAD, loader) !=
      ObjectSynchronizer::owner_self) {
    counter->inc();
  }
}

// common code for JVM_DefineClass() and JVM_DefineClassWithSource()
// and JVM_DefineClassWithSourceCond()
static jclass jvm_define_class_common(JNIEnv *env, const char *name,
                                      jobject loader, const jbyte *buf,
                                      jsize len, jobject pd, const char *source,
                                      jboolean verify, TRAPS) {
  if (source == NULL)  source = "__JVM_DefineClass__";

  assert(THREAD->is_Java_thread(), "must be a JavaThread");
  JavaThread* jt = (JavaThread*) THREAD;

  PerfClassTraceTime vmtimer(ClassLoader::perf_define_appclass_time(),
                             ClassLoader::perf_define_appclass_selftime(),
                             ClassLoader::perf_define_appclasses(),
                             jt->get_thread_stat()->perf_recursion_counts_addr(),
                             jt->get_thread_stat()->perf_timers_addr(),
                             PerfClassTraceTime::DEFINE_CLASS);

  if (UsePerfData) {
    ClassLoader::perf_app_classfile_bytes_read()->inc(len);
  }

  // Since exceptions can be thrown, class initialization can take place
  // if name is NULL no check for class name in .class stream has to be made.
  symbolHandle class_name;
  if (name != NULL) {
    const int str_len = (int)strlen(name);
    if (str_len > symbolOopDesc::max_length()) {
      // It's impossible to create this class;  the name cannot fit
      // into the constant pool.
      THROW_MSG_0(vmSymbols::java_lang_NoClassDefFoundError(), name);
    }
    class_name = oopFactory::new_symbol_handle(name, str_len, CHECK_NULL);
  }

  ResourceMark rm(THREAD);
  ClassFileStream st((u1*) buf, len, (char *)source);
  Handle class_loader (THREAD, JNIHandles::resolve(loader));
  if (UsePerfData) {
    is_lock_held_by_thread(class_loader,
                           ClassLoader::sync_JVMDefineClassLockFreeCounter(),
                           THREAD);
  }
  Handle protection_domain (THREAD, JNIHandles::resolve(pd));
  klassOop k = SystemDictionary::resolve_from_stream(class_name, class_loader,
                                                     protection_domain, &st,
                                                     verify != 0,
                                                     CHECK_NULL);

  if (TraceClassResolution && k != NULL) {
    trace_class_resolution(k);
  }

  return (jclass) JNIHandles::make_local(env, Klass::cast(k)->java_mirror());
}


JVM_ENTRY(jclass, JVM_DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd))
  JVMWrapper2("JVM_DefineClass %s", name);

  return jvm_define_class_common(env, name, loader, buf, len, pd, NULL, true, THREAD);
JVM_END


JVM_ENTRY(jclass, JVM_DefineClassWithSource(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd, const char *source))
  JVMWrapper2("JVM_DefineClassWithSource %s", name);

  return jvm_define_class_common(env, name, loader, buf, len, pd, source, true, THREAD);
JVM_END

JVM_ENTRY(jclass, JVM_DefineClassWithSourceCond(JNIEnv *env, const char *name,
                                                jobject loader, const jbyte *buf,
                                                jsize len, jobject pd,
                                                const char *source, jboolean verify))
  JVMWrapper2("JVM_DefineClassWithSourceCond %s", name);

  return jvm_define_class_common(env, name, loader, buf, len, pd, source, verify, THREAD);
JVM_END

JVM_ENTRY(jclass, JVM_FindLoadedClass(JNIEnv *env, jobject loader, jstring name))
  JVMWrapper("JVM_FindLoadedClass");
  ResourceMark rm(THREAD);

  Handle h_name (THREAD, JNIHandles::resolve_non_null(name));
  Handle string = java_lang_String::internalize_classname(h_name, CHECK_NULL);

  const char* str   = java_lang_String::as_utf8_string(string());
  // Sanity check, don't expect null
  if (str == NULL) return NULL;

  const int str_len = (int)strlen(str);
  if (str_len > symbolOopDesc::max_length()) {
    // It's impossible to create this class;  the name cannot fit
    // into the constant pool.
    return NULL;
  }
  symbolHandle klass_name = oopFactory::new_symbol_handle(str, str_len,CHECK_NULL);

  // Security Note:
  //   The Java level wrapper will perform the necessary security check allowing
  //   us to pass the NULL as the initiating class loader.
  Handle h_loader(THREAD, JNIHandles::resolve(loader));
  if (UsePerfData) {
    is_lock_held_by_thread(h_loader,
                           ClassLoader::sync_JVMFindLoadedClassLockFreeCounter(),
                           THREAD);
  }

  klassOop k = SystemDictionary::find_instance_or_array_klass(klass_name,
                                                              h_loader,
                                                              Handle(),
                                                              CHECK_NULL);

  return (k == NULL) ? NULL :
            (jclass) JNIHandles::make_local(env, Klass::cast(k)->java_mirror());
JVM_END


// Reflection support //////////////////////////////////////////////////////////////////////////////

JVM_ENTRY(jstring, JVM_GetClassName(JNIEnv *env, jclass cls))
  assert (cls != NULL, "illegal class");
  JVMWrapper("JVM_GetClassName");
  JvmtiVMObjectAllocEventCollector oam;
  ResourceMark rm(THREAD);
  const char* name;
  if (java_lang_Class::is_primitive(JNIHandles::resolve(cls))) {
    name = type2name(java_lang_Class::primitive_type(JNIHandles::resolve(cls)));
  } else {
    // Consider caching interned string in Klass
    klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve(cls));
    assert(k->is_klass(), "just checking");
    name = Klass::cast(k)->external_name();
  }
  oop result = StringTable::intern((char*) name, CHECK_NULL);
  return (jstring) JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jobjectArray, JVM_GetClassInterfaces(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassInterfaces");
  JvmtiVMObjectAllocEventCollector oam;
  oop mirror = JNIHandles::resolve_non_null(cls);

  // Special handling for primitive objects
  if (java_lang_Class::is_primitive(mirror)) {
    // Primitive objects does not have any interfaces
    objArrayOop r = oopFactory::new_objArray(SystemDictionary::Class_klass(), 0, CHECK_NULL);
    return (jobjectArray) JNIHandles::make_local(env, r);
  }

  KlassHandle klass(thread, java_lang_Class::as_klassOop(mirror));
  // Figure size of result array
  int size;
  if (klass->oop_is_instance()) {
    size = instanceKlass::cast(klass())->local_interfaces()->length();
  } else {
    assert(klass->oop_is_objArray() || klass->oop_is_typeArray(), "Illegal mirror klass");
    size = 2;
  }

  // Allocate result array
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::Class_klass(), size, CHECK_NULL);
  objArrayHandle result (THREAD, r);
  // Fill in result
  if (klass->oop_is_instance()) {
    // Regular instance klass, fill in all local interfaces
    for (int index = 0; index < size; index++) {
      klassOop k = klassOop(instanceKlass::cast(klass())->local_interfaces()->obj_at(index));
      result->obj_at_put(index, Klass::cast(k)->java_mirror());
    }
  } else {
    // All arrays implement java.lang.Cloneable and java.io.Serializable
    result->obj_at_put(0, Klass::cast(SystemDictionary::Cloneable_klass())->java_mirror());
    result->obj_at_put(1, Klass::cast(SystemDictionary::Serializable_klass())->java_mirror());
  }
  return (jobjectArray) JNIHandles::make_local(env, result());
JVM_END


JVM_ENTRY(jobject, JVM_GetClassLoader(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassLoader");
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(cls))) {
    return NULL;
  }
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  oop loader = Klass::cast(k)->class_loader();
  return JNIHandles::make_local(env, loader);
JVM_END


JVM_QUICK_ENTRY(jboolean, JVM_IsInterface(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_IsInterface");
  oop mirror = JNIHandles::resolve_non_null(cls);
  if (java_lang_Class::is_primitive(mirror)) {
    return JNI_FALSE;
  }
  klassOop k = java_lang_Class::as_klassOop(mirror);
  jboolean result = Klass::cast(k)->is_interface();
  assert(!result || Klass::cast(k)->oop_is_instance(),
         "all interfaces are instance types");
  // The compiler intrinsic for isInterface tests the
  // Klass::_access_flags bits in the same way.
  return result;
JVM_END


JVM_ENTRY(jobjectArray, JVM_GetClassSigners(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassSigners");
  JvmtiVMObjectAllocEventCollector oam;
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(cls))) {
    // There are no signers for primitive types
    return NULL;
  }

  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  objArrayOop signers = NULL;
  if (Klass::cast(k)->oop_is_instance()) {
    signers = instanceKlass::cast(k)->signers();
  }

  // If there are no signers set in the class, or if the class
  // is an array, return NULL.
  if (signers == NULL) return NULL;

  // copy of the signers array
  klassOop element = objArrayKlass::cast(signers->klass())->element_klass();
  objArrayOop signers_copy = oopFactory::new_objArray(element, signers->length(), CHECK_NULL);
  for (int index = 0; index < signers->length(); index++) {
    signers_copy->obj_at_put(index, signers->obj_at(index));
  }

  // return the copy
  return (jobjectArray) JNIHandles::make_local(env, signers_copy);
JVM_END


JVM_ENTRY(void, JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers))
  JVMWrapper("JVM_SetClassSigners");
  if (!java_lang_Class::is_primitive(JNIHandles::resolve_non_null(cls))) {
    // This call is ignored for primitive types and arrays.
    // Signers are only set once, ClassLoader.java, and thus shouldn't
    // be called with an array.  Only the bootstrap loader creates arrays.
    klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
    if (Klass::cast(k)->oop_is_instance()) {
      instanceKlass::cast(k)->set_signers(objArrayOop(JNIHandles::resolve(signers)));
    }
  }
JVM_END


JVM_ENTRY(jobject, JVM_GetProtectionDomain(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetProtectionDomain");
  if (JNIHandles::resolve(cls) == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), NULL);
  }

  if (java_lang_Class::is_primitive(JNIHandles::resolve(cls))) {
    // Primitive types does not have a protection domain.
    return NULL;
  }

  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve(cls));
  return (jobject) JNIHandles::make_local(env, Klass::cast(k)->protection_domain());
JVM_END


// Obsolete since 1.2 (Class.setProtectionDomain removed), although
// still defined in core libraries as of 1.5.
JVM_ENTRY(void, JVM_SetProtectionDomain(JNIEnv *env, jclass cls, jobject protection_domain))
  JVMWrapper("JVM_SetProtectionDomain");
  if (JNIHandles::resolve(cls) == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }
  if (!java_lang_Class::is_primitive(JNIHandles::resolve(cls))) {
    // Call is ignored for primitive types
    klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve(cls));

    // cls won't be an array, as this called only from ClassLoader.defineClass
    if (Klass::cast(k)->oop_is_instance()) {
      oop pd = JNIHandles::resolve(protection_domain);
      assert(pd == NULL || pd->is_oop(), "just checking");
      instanceKlass::cast(k)->set_protection_domain(pd);
    }
  }
JVM_END


JVM_ENTRY(jobject, JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException))
  JVMWrapper("JVM_DoPrivileged");

  if (action == NULL) {
    THROW_MSG_0(vmSymbols::java_lang_NullPointerException(), "Null action");
  }

  // Stack allocated list of privileged stack elements
  PrivilegedElement pi;

  // Check that action object understands "Object run()"
  Handle object (THREAD, JNIHandles::resolve(action));

  // get run() method
  methodOop m_oop = Klass::cast(object->klass())->uncached_lookup_method(
                                           vmSymbols::run_method_name(),
                                           vmSymbols::void_object_signature());
  methodHandle m (THREAD, m_oop);
  if (m.is_null() || !m->is_method() || !methodOop(m())->is_public() || methodOop(m())->is_static()) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), "No run method");
  }

  // Compute the frame initiating the do privileged operation and setup the privileged stack
  vframeStream vfst(thread);
  vfst.security_get_caller_frame(1);

  if (!vfst.at_end()) {
    pi.initialize(&vfst, JNIHandles::resolve(context), thread->privileged_stack_top(), CHECK_NULL);
    thread->set_privileged_stack_top(&pi);
  }


  // invoke the Object run() in the action object. We cannot use call_interface here, since the static type
  // is not really known - it is either java.security.PrivilegedAction or java.security.PrivilegedExceptionAction
  Handle pending_exception;
  JavaValue result(T_OBJECT);
  JavaCallArguments args(object);
  JavaCalls::call(&result, m, &args, THREAD);

  // done with action, remove ourselves from the list
  if (!vfst.at_end()) {
    assert(thread->privileged_stack_top() != NULL && thread->privileged_stack_top() == &pi, "wrong top element");
    thread->set_privileged_stack_top(thread->privileged_stack_top()->next());
  }

  if (HAS_PENDING_EXCEPTION) {
    pending_exception = Handle(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;

    if ( pending_exception->is_a(SystemDictionary::Exception_klass()) &&
        !pending_exception->is_a(SystemDictionary::RuntimeException_klass())) {
      // Throw a java.security.PrivilegedActionException(Exception e) exception
      JavaCallArguments args(pending_exception);
      THROW_ARG_0(vmSymbolHandles::java_security_PrivilegedActionException(),
                  vmSymbolHandles::exception_void_signature(),
                  &args);
    }
  }

  if (pending_exception.not_null()) THROW_OOP_0(pending_exception());
  return JNIHandles::make_local(env, (oop) result.get_jobject());
JVM_END


// Returns the inherited_access_control_context field of the running thread.
JVM_ENTRY(jobject, JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetInheritedAccessControlContext");
  oop result = java_lang_Thread::inherited_access_control_context(thread->threadObj());
  return JNIHandles::make_local(env, result);
JVM_END

class RegisterArrayForGC {
 private:
  JavaThread *_thread;
 public:
  RegisterArrayForGC(JavaThread *thread, GrowableArray<oop>* array)  {
    _thread = thread;
    _thread->register_array_for_gc(array);
  }

  ~RegisterArrayForGC() {
    _thread->register_array_for_gc(NULL);
  }
};


JVM_ENTRY(jobject, JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetStackAccessControlContext");
  if (!UsePrivilegedStack) return NULL;

  ResourceMark rm(THREAD);
  GrowableArray<oop>* local_array = new GrowableArray<oop>(12);
  JvmtiVMObjectAllocEventCollector oam;

  // count the protection domains on the execution stack. We collapse
  // duplicate consecutive protection domains into a single one, as
  // well as stopping when we hit a privileged frame.

  // Use vframeStream to iterate through Java frames
  vframeStream vfst(thread);

  oop previous_protection_domain = NULL;
  Handle privileged_context(thread, NULL);
  bool is_privileged = false;
  oop protection_domain = NULL;

  for(; !vfst.at_end(); vfst.next()) {
    // get method of frame
    methodOop method = vfst.method();
    intptr_t* frame_id   = vfst.frame_id();

    // check the privileged frames to see if we have a match
    if (thread->privileged_stack_top() && thread->privileged_stack_top()->frame_id() == frame_id) {
      // this frame is privileged
      is_privileged = true;
      privileged_context = Handle(thread, thread->privileged_stack_top()->privileged_context());
      protection_domain  = thread->privileged_stack_top()->protection_domain();
    } else {
      protection_domain = instanceKlass::cast(method->method_holder())->protection_domain();
    }

    if ((previous_protection_domain != protection_domain) && (protection_domain != NULL)) {
      local_array->push(protection_domain);
      previous_protection_domain = protection_domain;
    }

    if (is_privileged) break;
  }


  // either all the domains on the stack were system domains, or
  // we had a privileged system domain
  if (local_array->is_empty()) {
    if (is_privileged && privileged_context.is_null()) return NULL;

    oop result = java_security_AccessControlContext::create(objArrayHandle(), is_privileged, privileged_context, CHECK_NULL);
    return JNIHandles::make_local(env, result);
  }

  // the resource area must be registered in case of a gc
  RegisterArrayForGC ragc(thread, local_array);
  objArrayOop context = oopFactory::new_objArray(SystemDictionary::ProtectionDomain_klass(),
                                                 local_array->length(), CHECK_NULL);
  objArrayHandle h_context(thread, context);
  for (int index = 0; index < local_array->length(); index++) {
    h_context->obj_at_put(index, local_array->at(index));
  }

  oop result = java_security_AccessControlContext::create(h_context, is_privileged, privileged_context, CHECK_NULL);

  return JNIHandles::make_local(env, result);
JVM_END


JVM_QUICK_ENTRY(jboolean, JVM_IsArrayClass(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_IsArrayClass");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  return (k != NULL) && Klass::cast(k)->oop_is_javaArray() ? true : false;
JVM_END


JVM_QUICK_ENTRY(jboolean, JVM_IsPrimitiveClass(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_IsPrimitiveClass");
  oop mirror = JNIHandles::resolve_non_null(cls);
  return (jboolean) java_lang_Class::is_primitive(mirror);
JVM_END


JVM_ENTRY(jclass, JVM_GetComponentType(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetComponentType");
  oop mirror = JNIHandles::resolve_non_null(cls);
  oop result = Reflection::array_component_type(mirror, CHECK_NULL);
  return (jclass) JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jint, JVM_GetClassModifiers(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassModifiers");
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(cls))) {
    // Primitive type
    return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
  }

  Klass* k = Klass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls)));
  debug_only(int computed_modifiers = k->compute_modifier_flags(CHECK_0));
  assert(k->modifier_flags() == computed_modifiers, "modifiers cache is OK");
  return k->modifier_flags();
JVM_END


// Inner class reflection ///////////////////////////////////////////////////////////////////////////////

JVM_ENTRY(jobjectArray, JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass))
  const int inner_class_info_index = 0;
  const int outer_class_info_index = 1;

  JvmtiVMObjectAllocEventCollector oam;
  // ofClass is a reference to a java_lang_Class object. The mirror object
  // of an instanceKlass

  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(ofClass)) ||
      ! Klass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)))->oop_is_instance()) {
    oop result = oopFactory::new_objArray(SystemDictionary::Class_klass(), 0, CHECK_NULL);
    return (jobjectArray)JNIHandles::make_local(env, result);
  }

  instanceKlassHandle k(thread, java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)));

  if (k->inner_classes()->length() == 0) {
    // Neither an inner nor outer class
    oop result = oopFactory::new_objArray(SystemDictionary::Class_klass(), 0, CHECK_NULL);
    return (jobjectArray)JNIHandles::make_local(env, result);
  }

  // find inner class info
  typeArrayHandle    icls(thread, k->inner_classes());
  constantPoolHandle cp(thread, k->constants());
  int length = icls->length();

  // Allocate temp. result array
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::Class_klass(), length/4, CHECK_NULL);
  objArrayHandle result (THREAD, r);
  int members = 0;

  for(int i = 0; i < length; i += 4) {
    int ioff = icls->ushort_at(i + inner_class_info_index);
    int ooff = icls->ushort_at(i + outer_class_info_index);

    if (ioff != 0 && ooff != 0) {
      // Check to see if the name matches the class we're looking for
      // before attempting to find the class.
      if (cp->klass_name_at_matches(k, ooff)) {
        klassOop outer_klass = cp->klass_at(ooff, CHECK_NULL);
        if (outer_klass == k()) {
           klassOop ik = cp->klass_at(ioff, CHECK_NULL);
           instanceKlassHandle inner_klass (THREAD, ik);

           // Throws an exception if outer klass has not declared k as
           // an inner klass
           Reflection::check_for_inner_class(k, inner_klass, true, CHECK_NULL);

           result->obj_at_put(members, inner_klass->java_mirror());
           members++;
        }
      }
    }
  }

  if (members != length) {
    // Return array of right length
    objArrayOop res = oopFactory::new_objArray(SystemDictionary::Class_klass(), members, CHECK_NULL);
    for(int i = 0; i < members; i++) {
      res->obj_at_put(i, result->obj_at(i));
    }
    return (jobjectArray)JNIHandles::make_local(env, res);
  }

  return (jobjectArray)JNIHandles::make_local(env, result());
JVM_END


JVM_ENTRY(jclass, JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass))
{
  // ofClass is a reference to a java_lang_Class object.
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(ofClass)) ||
      ! Klass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)))->oop_is_instance()) {
    return NULL;
  }

  symbolOop simple_name = NULL;
  klassOop outer_klass
    = instanceKlass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass))
                          )->compute_enclosing_class(simple_name, CHECK_NULL);
  if (outer_klass == NULL)  return NULL;  // already a top-level class
  if (simple_name == NULL)  return NULL;  // an anonymous class (inside a method)
  return (jclass) JNIHandles::make_local(env, Klass::cast(outer_klass)->java_mirror());
}
JVM_END

// should be in instanceKlass.cpp, but is here for historical reasons
klassOop instanceKlass::compute_enclosing_class_impl(instanceKlassHandle k,
                                                     symbolOop& simple_name_result, TRAPS) {
  Thread* thread = THREAD;
  const int inner_class_info_index = inner_class_inner_class_info_offset;
  const int outer_class_info_index = inner_class_outer_class_info_offset;

  if (k->inner_classes()->length() == 0) {
    // No inner class info => no declaring class
    return NULL;
  }

  typeArrayHandle i_icls(thread, k->inner_classes());
  constantPoolHandle i_cp(thread, k->constants());
  int i_length = i_icls->length();

  bool found = false;
  klassOop ok;
  instanceKlassHandle outer_klass;
  bool inner_is_member = false;
  int simple_name_index = 0;

  // Find inner_klass attribute
  for (int i = 0; i < i_length && !found; i += inner_class_next_offset) {
    int ioff = i_icls->ushort_at(i + inner_class_info_index);
    int ooff = i_icls->ushort_at(i + outer_class_info_index);
    int noff = i_icls->ushort_at(i + inner_class_inner_name_offset);
    if (ioff != 0) {
      // Check to see if the name matches the class we're looking for
      // before attempting to find the class.
      if (i_cp->klass_name_at_matches(k, ioff)) {
        klassOop inner_klass = i_cp->klass_at(ioff, CHECK_NULL);
        found = (k() == inner_klass);
        if (found && ooff != 0) {
          ok = i_cp->klass_at(ooff, CHECK_NULL);
          outer_klass = instanceKlassHandle(thread, ok);
          simple_name_index = noff;
          inner_is_member = true;
        }
      }
    }
  }

  if (found && outer_klass.is_null()) {
    // It may be anonymous; try for that.
    int encl_method_class_idx = k->enclosing_method_class_index();
    if (encl_method_class_idx != 0) {
      ok = i_cp->klass_at(encl_method_class_idx, CHECK_NULL);
      outer_klass = instanceKlassHandle(thread, ok);
      inner_is_member = false;
    }
  }

  // If no inner class attribute found for this class.
  if (outer_klass.is_null())  return NULL;

  // Throws an exception if outer klass has not declared k as an inner klass
  // We need evidence that each klass knows about the other, or else
  // the system could allow a spoof of an inner class to gain access rights.
  Reflection::check_for_inner_class(outer_klass, k, inner_is_member, CHECK_NULL);

  simple_name_result = (inner_is_member ? i_cp->symbol_at(simple_name_index) : symbolOop(NULL));
  return outer_klass();
}

JVM_ENTRY(jstring, JVM_GetClassSignature(JNIEnv *env, jclass cls))
  assert (cls != NULL, "illegal class");
  JVMWrapper("JVM_GetClassSignature");
  JvmtiVMObjectAllocEventCollector oam;
  ResourceMark rm(THREAD);
  // Return null for arrays and primatives
  if (!java_lang_Class::is_primitive(JNIHandles::resolve(cls))) {
    klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve(cls));
    if (Klass::cast(k)->oop_is_instance()) {
      symbolHandle sym = symbolHandle(THREAD, instanceKlass::cast(k)->generic_signature());
      if (sym.is_null()) return NULL;
      Handle str = java_lang_String::create_from_symbol(sym, CHECK_NULL);
      return (jstring) JNIHandles::make_local(env, str());
    }
  }
  return NULL;
JVM_END


JVM_ENTRY(jbyteArray, JVM_GetClassAnnotations(JNIEnv *env, jclass cls))
  assert (cls != NULL, "illegal class");
  JVMWrapper("JVM_GetClassAnnotations");
  ResourceMark rm(THREAD);
  // Return null for arrays and primitives
  if (!java_lang_Class::is_primitive(JNIHandles::resolve(cls))) {
    klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve(cls));
    if (Klass::cast(k)->oop_is_instance()) {
      return (jbyteArray) JNIHandles::make_local(env,
                                  instanceKlass::cast(k)->class_annotations());
    }
  }
  return NULL;
JVM_END


JVM_ENTRY(jbyteArray, JVM_GetFieldAnnotations(JNIEnv *env, jobject field))
  assert(field != NULL, "illegal field");
  JVMWrapper("JVM_GetFieldAnnotations");

  // some of this code was adapted from from jni_FromReflectedField

  // field is a handle to a java.lang.reflect.Field object
  oop reflected = JNIHandles::resolve_non_null(field);
  oop mirror    = java_lang_reflect_Field::clazz(reflected);
  klassOop k    = java_lang_Class::as_klassOop(mirror);
  int slot      = java_lang_reflect_Field::slot(reflected);
  int modifiers = java_lang_reflect_Field::modifiers(reflected);

  fieldDescriptor fd;
  KlassHandle kh(THREAD, k);
  intptr_t offset = instanceKlass::cast(kh())->offset_from_fields(slot);

  if (modifiers & JVM_ACC_STATIC) {
    // for static fields we only look in the current class
    if (!instanceKlass::cast(kh())->find_local_field_from_offset(offset,
                                                                 true, &fd)) {
      assert(false, "cannot find static field");
      return NULL;  // robustness
    }
  } else {
    // for instance fields we start with the current class and work
    // our way up through the superclass chain
    if (!instanceKlass::cast(kh())->find_field_from_offset(offset, false,
                                                           &fd)) {
      assert(false, "cannot find instance field");
      return NULL;  // robustness
    }
  }

  return (jbyteArray) JNIHandles::make_local(env, fd.annotations());
JVM_END


static methodOop jvm_get_method_common(jobject method, TRAPS) {
  // some of this code was adapted from from jni_FromReflectedMethod

  oop reflected = JNIHandles::resolve_non_null(method);
  oop mirror    = NULL;
  int slot      = 0;

  if (reflected->klass() == SystemDictionary::reflect_Constructor_klass()) {
    mirror = java_lang_reflect_Constructor::clazz(reflected);
    slot   = java_lang_reflect_Constructor::slot(reflected);
  } else {
    assert(reflected->klass() == SystemDictionary::reflect_Method_klass(),
           "wrong type");
    mirror = java_lang_reflect_Method::clazz(reflected);
    slot   = java_lang_reflect_Method::slot(reflected);
  }
  klassOop k = java_lang_Class::as_klassOop(mirror);

  KlassHandle kh(THREAD, k);
  methodOop m = instanceKlass::cast(kh())->method_with_idnum(slot);
  if (m == NULL) {
    assert(false, "cannot find method");
    return NULL;  // robustness
  }

  return m;
}


JVM_ENTRY(jbyteArray, JVM_GetMethodAnnotations(JNIEnv *env, jobject method))
  JVMWrapper("JVM_GetMethodAnnotations");

  // method is a handle to a java.lang.reflect.Method object
  methodOop m = jvm_get_method_common(method, CHECK_NULL);
  return (jbyteArray) JNIHandles::make_local(env, m->annotations());
JVM_END


JVM_ENTRY(jbyteArray, JVM_GetMethodDefaultAnnotationValue(JNIEnv *env, jobject method))
  JVMWrapper("JVM_GetMethodDefaultAnnotationValue");

  // method is a handle to a java.lang.reflect.Method object
  methodOop m = jvm_get_method_common(method, CHECK_NULL);
  return (jbyteArray) JNIHandles::make_local(env, m->annotation_default());
JVM_END


JVM_ENTRY(jbyteArray, JVM_GetMethodParameterAnnotations(JNIEnv *env, jobject method))
  JVMWrapper("JVM_GetMethodParameterAnnotations");

  // method is a handle to a java.lang.reflect.Method object
  methodOop m = jvm_get_method_common(method, CHECK_NULL);
  return (jbyteArray) JNIHandles::make_local(env, m->parameter_annotations());
JVM_END


// New (JDK 1.4) reflection implementation /////////////////////////////////////

JVM_ENTRY(jobjectArray, JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly))
{
  JVMWrapper("JVM_GetClassDeclaredFields");
  JvmtiVMObjectAllocEventCollector oam;

  // Exclude primitive types and array types
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(ofClass)) ||
      Klass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)))->oop_is_javaArray()) {
    // Return empty array
    oop res = oopFactory::new_objArray(SystemDictionary::reflect_Field_klass(), 0, CHECK_NULL);
    return (jobjectArray) JNIHandles::make_local(env, res);
  }

  instanceKlassHandle k(THREAD, java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)));
  constantPoolHandle cp(THREAD, k->constants());

  // Ensure class is linked
  k->link_class(CHECK_NULL);

  typeArrayHandle fields(THREAD, k->fields());
  int fields_len = fields->length();

  // 4496456 We need to filter out java.lang.Throwable.backtrace
  bool skip_backtrace = false;

  // Allocate result
  int num_fields;

  if (publicOnly) {
    num_fields = 0;
    for (int i = 0, j = 0; i < fields_len; i += instanceKlass::next_offset, j++) {
      int mods = fields->ushort_at(i + instanceKlass::access_flags_offset) & JVM_RECOGNIZED_FIELD_MODIFIERS;
      if (mods & JVM_ACC_PUBLIC) ++num_fields;
    }
  } else {
    num_fields = fields_len / instanceKlass::next_offset;

    if (k() == SystemDictionary::Throwable_klass()) {
      num_fields--;
      skip_backtrace = true;
    }
  }

  objArrayOop r = oopFactory::new_objArray(SystemDictionary::reflect_Field_klass(), num_fields, CHECK_NULL);
  objArrayHandle result (THREAD, r);

  int out_idx = 0;
  fieldDescriptor fd;
  for (int i = 0; i < fields_len; i += instanceKlass::next_offset) {
    if (skip_backtrace) {
      // 4496456 skip java.lang.Throwable.backtrace
      int offset = k->offset_from_fields(i);
      if (offset == java_lang_Throwable::get_backtrace_offset()) continue;
    }

    int mods = fields->ushort_at(i + instanceKlass::access_flags_offset) & JVM_RECOGNIZED_FIELD_MODIFIERS;
    if (!publicOnly || (mods & JVM_ACC_PUBLIC)) {
      fd.initialize(k(), i);
      oop field = Reflection::new_field(&fd, UseNewReflection, CHECK_NULL);
      result->obj_at_put(out_idx, field);
      ++out_idx;
    }
  }
  assert(out_idx == num_fields, "just checking");
  return (jobjectArray) JNIHandles::make_local(env, result());
}
JVM_END

JVM_ENTRY(jobjectArray, JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly))
{
  JVMWrapper("JVM_GetClassDeclaredMethods");
  JvmtiVMObjectAllocEventCollector oam;

  // Exclude primitive types and array types
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(ofClass))
      || Klass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)))->oop_is_javaArray()) {
    // Return empty array
    oop res = oopFactory::new_objArray(SystemDictionary::reflect_Method_klass(), 0, CHECK_NULL);
    return (jobjectArray) JNIHandles::make_local(env, res);
  }

  instanceKlassHandle k(THREAD, java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)));

  // Ensure class is linked
  k->link_class(CHECK_NULL);

  objArrayHandle methods (THREAD, k->methods());
  int methods_length = methods->length();
  int num_methods = 0;

  int i;
  for (i = 0; i < methods_length; i++) {
    methodHandle method(THREAD, (methodOop) methods->obj_at(i));
    if (!method->is_initializer()) {
      if (!publicOnly || method->is_public()) {
        ++num_methods;
      }
    }
  }

  // Allocate result
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::reflect_Method_klass(), num_methods, CHECK_NULL);
  objArrayHandle result (THREAD, r);

  int out_idx = 0;
  for (i = 0; i < methods_length; i++) {
    methodHandle method(THREAD, (methodOop) methods->obj_at(i));
    if (!method->is_initializer()) {
      if (!publicOnly || method->is_public()) {
        oop m = Reflection::new_method(method, UseNewReflection, false, CHECK_NULL);
        result->obj_at_put(out_idx, m);
        ++out_idx;
      }
    }
  }
  assert(out_idx == num_methods, "just checking");
  return (jobjectArray) JNIHandles::make_local(env, result());
}
JVM_END

JVM_ENTRY(jobjectArray, JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly))
{
  JVMWrapper("JVM_GetClassDeclaredConstructors");
  JvmtiVMObjectAllocEventCollector oam;

  // Exclude primitive types and array types
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(ofClass))
      || Klass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)))->oop_is_javaArray()) {
    // Return empty array
    oop res = oopFactory::new_objArray(SystemDictionary::reflect_Constructor_klass(), 0 , CHECK_NULL);
    return (jobjectArray) JNIHandles::make_local(env, res);
  }

  instanceKlassHandle k(THREAD, java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(ofClass)));

  // Ensure class is linked
  k->link_class(CHECK_NULL);

  objArrayHandle methods (THREAD, k->methods());
  int methods_length = methods->length();
  int num_constructors = 0;

  int i;
  for (i = 0; i < methods_length; i++) {
    methodHandle method(THREAD, (methodOop) methods->obj_at(i));
    if (method->is_initializer() && !method->is_static()) {
      if (!publicOnly || method->is_public()) {
        ++num_constructors;
      }
    }
  }

  // Allocate result
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::reflect_Constructor_klass(), num_constructors, CHECK_NULL);
  objArrayHandle result(THREAD, r);

  int out_idx = 0;
  for (i = 0; i < methods_length; i++) {
    methodHandle method(THREAD, (methodOop) methods->obj_at(i));
    if (method->is_initializer() && !method->is_static()) {
      if (!publicOnly || method->is_public()) {
        oop m = Reflection::new_constructor(method, CHECK_NULL);
        result->obj_at_put(out_idx, m);
        ++out_idx;
      }
    }
  }
  assert(out_idx == num_constructors, "just checking");
  return (jobjectArray) JNIHandles::make_local(env, result());
}
JVM_END

JVM_ENTRY(jint, JVM_GetClassAccessFlags(JNIEnv *env, jclass cls))
{
  JVMWrapper("JVM_GetClassAccessFlags");
  if (java_lang_Class::is_primitive(JNIHandles::resolve_non_null(cls))) {
    // Primitive type
    return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
  }

  Klass* k = Klass::cast(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls)));
  return k->access_flags().as_int() & JVM_ACC_WRITTEN_FLAGS;
}
JVM_END


// Constant pool access //////////////////////////////////////////////////////////

JVM_ENTRY(jobject, JVM_GetClassConstantPool(JNIEnv *env, jclass cls))
{
  JVMWrapper("JVM_GetClassConstantPool");
  JvmtiVMObjectAllocEventCollector oam;

  // Return null for primitives and arrays
  if (!java_lang_Class::is_primitive(JNIHandles::resolve_non_null(cls))) {
    klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
    if (Klass::cast(k)->oop_is_instance()) {
      instanceKlassHandle k_h(THREAD, k);
      Handle jcp = sun_reflect_ConstantPool::create(CHECK_NULL);
      sun_reflect_ConstantPool::set_cp_oop(jcp(), k_h->constants());
      return JNIHandles::make_local(jcp());
    }
  }
  return NULL;
}
JVM_END


JVM_ENTRY(jint, JVM_ConstantPoolGetSize(JNIEnv *env, jobject unused, jobject jcpool))
{
  JVMWrapper("JVM_ConstantPoolGetSize");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  return cp->length();
}
JVM_END


static void bounds_check(constantPoolHandle cp, jint index, TRAPS) {
  if (!cp->is_within_bounds(index)) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "Constant pool index out of bounds");
  }
}


JVM_ENTRY(jclass, JVM_ConstantPoolGetClassAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetClassAt");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  constantTag tag = cp->tag_at(index);
  if (!tag.is_klass() && !tag.is_unresolved_klass()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  klassOop k = cp->klass_at(index, CHECK_NULL);
  return (jclass) JNIHandles::make_local(k->klass_part()->java_mirror());
}
JVM_END


JVM_ENTRY(jclass, JVM_ConstantPoolGetClassAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetClassAtIfLoaded");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  constantTag tag = cp->tag_at(index);
  if (!tag.is_klass() && !tag.is_unresolved_klass()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  klassOop k = constantPoolOopDesc::klass_at_if_loaded(cp, index);
  if (k == NULL) return NULL;
  return (jclass) JNIHandles::make_local(k->klass_part()->java_mirror());
}
JVM_END

static jobject get_method_at_helper(constantPoolHandle cp, jint index, bool force_resolution, TRAPS) {
  constantTag tag = cp->tag_at(index);
  if (!tag.is_method() && !tag.is_interface_method()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  int klass_ref  = cp->uncached_klass_ref_index_at(index);
  klassOop k_o;
  if (force_resolution) {
    k_o = cp->klass_at(klass_ref, CHECK_NULL);
  } else {
    k_o = constantPoolOopDesc::klass_at_if_loaded(cp, klass_ref);
    if (k_o == NULL) return NULL;
  }
  instanceKlassHandle k(THREAD, k_o);
  symbolOop name = cp->uncached_name_ref_at(index);
  symbolOop sig  = cp->uncached_signature_ref_at(index);
  methodHandle m (THREAD, k->find_method(name, sig));
  if (m.is_null()) {
    THROW_MSG_0(vmSymbols::java_lang_RuntimeException(), "Unable to look up method in target class");
  }
  oop method;
  if (!m->is_initializer() || m->is_static()) {
    method = Reflection::new_method(m, true, true, CHECK_NULL);
  } else {
    method = Reflection::new_constructor(m, CHECK_NULL);
  }
  return JNIHandles::make_local(method);
}

JVM_ENTRY(jobject, JVM_ConstantPoolGetMethodAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetMethodAt");
  JvmtiVMObjectAllocEventCollector oam;
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  jobject res = get_method_at_helper(cp, index, true, CHECK_NULL);
  return res;
}
JVM_END

JVM_ENTRY(jobject, JVM_ConstantPoolGetMethodAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetMethodAtIfLoaded");
  JvmtiVMObjectAllocEventCollector oam;
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  jobject res = get_method_at_helper(cp, index, false, CHECK_NULL);
  return res;
}
JVM_END

static jobject get_field_at_helper(constantPoolHandle cp, jint index, bool force_resolution, TRAPS) {
  constantTag tag = cp->tag_at(index);
  if (!tag.is_field()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  int klass_ref  = cp->uncached_klass_ref_index_at(index);
  klassOop k_o;
  if (force_resolution) {
    k_o = cp->klass_at(klass_ref, CHECK_NULL);
  } else {
    k_o = constantPoolOopDesc::klass_at_if_loaded(cp, klass_ref);
    if (k_o == NULL) return NULL;
  }
  instanceKlassHandle k(THREAD, k_o);
  symbolOop name = cp->uncached_name_ref_at(index);
  symbolOop sig  = cp->uncached_signature_ref_at(index);
  fieldDescriptor fd;
  klassOop target_klass = k->find_field(name, sig, &fd);
  if (target_klass == NULL) {
    THROW_MSG_0(vmSymbols::java_lang_RuntimeException(), "Unable to look up field in target class");
  }
  oop field = Reflection::new_field(&fd, true, CHECK_NULL);
  return JNIHandles::make_local(field);
}

JVM_ENTRY(jobject, JVM_ConstantPoolGetFieldAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetFieldAt");
  JvmtiVMObjectAllocEventCollector oam;
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  jobject res = get_field_at_helper(cp, index, true, CHECK_NULL);
  return res;
}
JVM_END

JVM_ENTRY(jobject, JVM_ConstantPoolGetFieldAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetFieldAtIfLoaded");
  JvmtiVMObjectAllocEventCollector oam;
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  jobject res = get_field_at_helper(cp, index, false, CHECK_NULL);
  return res;
}
JVM_END

JVM_ENTRY(jobjectArray, JVM_ConstantPoolGetMemberRefInfoAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetMemberRefInfoAt");
  JvmtiVMObjectAllocEventCollector oam;
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  constantTag tag = cp->tag_at(index);
  if (!tag.is_field_or_method()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  int klass_ref = cp->uncached_klass_ref_index_at(index);
  symbolHandle klass_name (THREAD, cp->klass_name_at(klass_ref));
  symbolHandle member_name(THREAD, cp->uncached_name_ref_at(index));
  symbolHandle member_sig (THREAD, cp->uncached_signature_ref_at(index));
  objArrayOop  dest_o = oopFactory::new_objArray(SystemDictionary::String_klass(), 3, CHECK_NULL);
  objArrayHandle dest(THREAD, dest_o);
  Handle str = java_lang_String::create_from_symbol(klass_name, CHECK_NULL);
  dest->obj_at_put(0, str());
  str = java_lang_String::create_from_symbol(member_name, CHECK_NULL);
  dest->obj_at_put(1, str());
  str = java_lang_String::create_from_symbol(member_sig, CHECK_NULL);
  dest->obj_at_put(2, str());
  return (jobjectArray) JNIHandles::make_local(dest());
}
JVM_END

JVM_ENTRY(jint, JVM_ConstantPoolGetIntAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetIntAt");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_0);
  constantTag tag = cp->tag_at(index);
  if (!tag.is_int()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  return cp->int_at(index);
}
JVM_END

JVM_ENTRY(jlong, JVM_ConstantPoolGetLongAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetLongAt");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_(0L));
  constantTag tag = cp->tag_at(index);
  if (!tag.is_long()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  return cp->long_at(index);
}
JVM_END

JVM_ENTRY(jfloat, JVM_ConstantPoolGetFloatAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetFloatAt");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_(0.0f));
  constantTag tag = cp->tag_at(index);
  if (!tag.is_float()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  return cp->float_at(index);
}
JVM_END

JVM_ENTRY(jdouble, JVM_ConstantPoolGetDoubleAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetDoubleAt");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_(0.0));
  constantTag tag = cp->tag_at(index);
  if (!tag.is_double()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  return cp->double_at(index);
}
JVM_END

JVM_ENTRY(jstring, JVM_ConstantPoolGetStringAt(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetStringAt");
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  constantTag tag = cp->tag_at(index);
  if (!tag.is_string() && !tag.is_unresolved_string()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  oop str = cp->string_at(index, CHECK_NULL);
  return (jstring) JNIHandles::make_local(str);
}
JVM_END

JVM_ENTRY(jstring, JVM_ConstantPoolGetUTF8At(JNIEnv *env, jobject unused, jobject jcpool, jint index))
{
  JVMWrapper("JVM_ConstantPoolGetUTF8At");
  JvmtiVMObjectAllocEventCollector oam;
  constantPoolHandle cp = constantPoolHandle(THREAD, constantPoolOop(JNIHandles::resolve_non_null(jcpool)));
  bounds_check(cp, index, CHECK_NULL);
  constantTag tag = cp->tag_at(index);
  if (!tag.is_symbol()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Wrong type at constant pool index");
  }
  symbolOop sym_o = cp->symbol_at(index);
  symbolHandle sym(THREAD, sym_o);
  Handle str = java_lang_String::create_from_symbol(sym, CHECK_NULL);
  return (jstring) JNIHandles::make_local(str());
}
JVM_END


// Assertion support. //////////////////////////////////////////////////////////

JVM_ENTRY(jboolean, JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls))
  JVMWrapper("JVM_DesiredAssertionStatus");
  assert(cls != NULL, "bad class");

  oop r = JNIHandles::resolve(cls);
  assert(! java_lang_Class::is_primitive(r), "primitive classes not allowed");
  if (java_lang_Class::is_primitive(r)) return false;

  klassOop k = java_lang_Class::as_klassOop(r);
  assert(Klass::cast(k)->oop_is_instance(), "must be an instance klass");
  if (! Klass::cast(k)->oop_is_instance()) return false;

  ResourceMark rm(THREAD);
  const char* name = Klass::cast(k)->name()->as_C_string();
  bool system_class = Klass::cast(k)->class_loader() == NULL;
  return JavaAssertions::enabled(name, system_class);

JVM_END


// Return a new AssertionStatusDirectives object with the fields filled in with
// command-line assertion arguments (i.e., -ea, -da).
JVM_ENTRY(jobject, JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused))
  JVMWrapper("JVM_AssertionStatusDirectives");
  JvmtiVMObjectAllocEventCollector oam;
  oop asd = JavaAssertions::createAssertionStatusDirectives(CHECK_NULL);
  return JNIHandles::make_local(env, asd);
JVM_END

// Verification ////////////////////////////////////////////////////////////////////////////////

// Reflection for the verifier /////////////////////////////////////////////////////////////////

// RedefineClasses support: bug 6214132 caused verification to fail.
// All functions from this section should call the jvmtiThreadSate function:
//   klassOop class_to_verify_considering_redefinition(klassOop klass).
// The function returns a klassOop of the _scratch_class if the verifier
// was invoked in the middle of the class redefinition.
// Otherwise it returns its argument value which is the _the_class klassOop.
// Please, refer to the description in the jvmtiThreadSate.hpp.

JVM_ENTRY(const char*, JVM_GetClassNameUTF(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassNameUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  return Klass::cast(k)->name()->as_utf8();
JVM_END


JVM_QUICK_ENTRY(void, JVM_GetClassCPTypes(JNIEnv *env, jclass cls, unsigned char *types))
  JVMWrapper("JVM_GetClassCPTypes");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  // types will have length zero if this is not an instanceKlass
  // (length is determined by call to JVM_GetClassCPEntriesCount)
  if (Klass::cast(k)->oop_is_instance()) {
    constantPoolOop cp = instanceKlass::cast(k)->constants();
    for (int index = cp->length() - 1; index >= 0; index--) {
      constantTag tag = cp->tag_at(index);
      types[index] = (tag.is_unresolved_klass()) ? JVM_CONSTANT_Class :
                     (tag.is_unresolved_string()) ? JVM_CONSTANT_String : tag.value();
  }
  }
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetClassCPEntriesCount(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassCPEntriesCount");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  if (!Klass::cast(k)->oop_is_instance())
    return 0;
  return instanceKlass::cast(k)->constants()->length();
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetClassFieldsCount(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassFieldsCount");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  if (!Klass::cast(k)->oop_is_instance())
    return 0;
  return instanceKlass::cast(k)->fields()->length() / instanceKlass::next_offset;
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetClassMethodsCount(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_GetClassMethodsCount");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  if (!Klass::cast(k)->oop_is_instance())
    return 0;
  return instanceKlass::cast(k)->methods()->length();
JVM_END


// The following methods, used for the verifier, are never called with
// array klasses, so a direct cast to instanceKlass is safe.
// Typically, these methods are called in a loop with bounds determined
// by the results of JVM_GetClass{Fields,Methods}Count, which return
// zero for arrays.
JVM_QUICK_ENTRY(void, JVM_GetMethodIxExceptionIndexes(JNIEnv *env, jclass cls, jint method_index, unsigned short *exceptions))
  JVMWrapper("JVM_GetMethodIxExceptionIndexes");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  int length = methodOop(method)->checked_exceptions_length();
  if (length > 0) {
    CheckedExceptionElement* table= methodOop(method)->checked_exceptions_start();
    for (int i = 0; i < length; i++) {
      exceptions[i] = table[i].class_cp_index;
    }
  }
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetMethodIxExceptionsCount(JNIEnv *env, jclass cls, jint method_index))
  JVMWrapper("JVM_GetMethodIxExceptionsCount");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->checked_exceptions_length();
JVM_END


JVM_QUICK_ENTRY(void, JVM_GetMethodIxByteCode(JNIEnv *env, jclass cls, jint method_index, unsigned char *code))
  JVMWrapper("JVM_GetMethodIxByteCode");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  memcpy(code, methodOop(method)->code_base(), methodOop(method)->code_size());
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetMethodIxByteCodeLength(JNIEnv *env, jclass cls, jint method_index))
  JVMWrapper("JVM_GetMethodIxByteCodeLength");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->code_size();
JVM_END


JVM_QUICK_ENTRY(void, JVM_GetMethodIxExceptionTableEntry(JNIEnv *env, jclass cls, jint method_index, jint entry_index, JVM_ExceptionTableEntryType *entry))
  JVMWrapper("JVM_GetMethodIxExceptionTableEntry");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  typeArrayOop extable = methodOop(method)->exception_table();
  entry->start_pc   = extable->int_at(entry_index * 4);
  entry->end_pc     = extable->int_at(entry_index * 4 + 1);
  entry->handler_pc = extable->int_at(entry_index * 4 + 2);
  entry->catchType  = extable->int_at(entry_index * 4 + 3);
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetMethodIxExceptionTableLength(JNIEnv *env, jclass cls, int method_index))
  JVMWrapper("JVM_GetMethodIxExceptionTableLength");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->exception_table()->length() / 4;
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetMethodIxModifiers(JNIEnv *env, jclass cls, int method_index))
  JVMWrapper("JVM_GetMethodIxModifiers");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->access_flags().as_int() & JVM_RECOGNIZED_METHOD_MODIFIERS;
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetFieldIxModifiers(JNIEnv *env, jclass cls, int field_index))
  JVMWrapper("JVM_GetFieldIxModifiers");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  typeArrayOop fields = instanceKlass::cast(k)->fields();
  return fields->ushort_at(field_index * instanceKlass::next_offset + instanceKlass::access_flags_offset) & JVM_RECOGNIZED_FIELD_MODIFIERS;
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetMethodIxLocalsCount(JNIEnv *env, jclass cls, int method_index))
  JVMWrapper("JVM_GetMethodIxLocalsCount");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->max_locals();
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetMethodIxArgsSize(JNIEnv *env, jclass cls, int method_index))
  JVMWrapper("JVM_GetMethodIxArgsSize");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->size_of_parameters();
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetMethodIxMaxStack(JNIEnv *env, jclass cls, int method_index))
  JVMWrapper("JVM_GetMethodIxMaxStack");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->max_stack();
JVM_END


JVM_QUICK_ENTRY(jboolean, JVM_IsConstructorIx(JNIEnv *env, jclass cls, int method_index))
  JVMWrapper("JVM_IsConstructorIx");
  ResourceMark rm(THREAD);
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->name() == vmSymbols::object_initializer_name();
JVM_END


JVM_ENTRY(const char*, JVM_GetMethodIxNameUTF(JNIEnv *env, jclass cls, jint method_index))
  JVMWrapper("JVM_GetMethodIxIxUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->name()->as_utf8();
JVM_END


JVM_ENTRY(const char*, JVM_GetMethodIxSignatureUTF(JNIEnv *env, jclass cls, jint method_index))
  JVMWrapper("JVM_GetMethodIxSignatureUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  oop method = instanceKlass::cast(k)->methods()->obj_at(method_index);
  return methodOop(method)->signature()->as_utf8();
JVM_END

/**
 * All of these JVM_GetCP-xxx methods are used by the old verifier to
 * read entries in the constant pool.  Since the old verifier always
 * works on a copy of the code, it will not see any rewriting that
 * may possibly occur in the middle of verification.  So it is important
 * that nothing it calls tries to use the cpCache instead of the raw
 * constant pool, so we must use cp->uncached_x methods when appropriate.
 */
JVM_ENTRY(const char*, JVM_GetCPFieldNameUTF(JNIEnv *env, jclass cls, jint cp_index))
  JVMWrapper("JVM_GetCPFieldNameUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_Fieldref:
      return cp->uncached_name_ref_at(cp_index)->as_utf8();
    default:
      fatal("JVM_GetCPFieldNameUTF: illegal constant");
  }
  ShouldNotReachHere();
  return NULL;
JVM_END


JVM_ENTRY(const char*, JVM_GetCPMethodNameUTF(JNIEnv *env, jclass cls, jint cp_index))
  JVMWrapper("JVM_GetCPMethodNameUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_InterfaceMethodref:
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_NameAndType:  // for invokedynamic
      return cp->uncached_name_ref_at(cp_index)->as_utf8();
    default:
      fatal("JVM_GetCPMethodNameUTF: illegal constant");
  }
  ShouldNotReachHere();
  return NULL;
JVM_END


JVM_ENTRY(const char*, JVM_GetCPMethodSignatureUTF(JNIEnv *env, jclass cls, jint cp_index))
  JVMWrapper("JVM_GetCPMethodSignatureUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_InterfaceMethodref:
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_NameAndType:  // for invokedynamic
      return cp->uncached_signature_ref_at(cp_index)->as_utf8();
    default:
      fatal("JVM_GetCPMethodSignatureUTF: illegal constant");
  }
  ShouldNotReachHere();
  return NULL;
JVM_END


JVM_ENTRY(const char*, JVM_GetCPFieldSignatureUTF(JNIEnv *env, jclass cls, jint cp_index))
  JVMWrapper("JVM_GetCPFieldSignatureUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_Fieldref:
      return cp->uncached_signature_ref_at(cp_index)->as_utf8();
    default:
      fatal("JVM_GetCPFieldSignatureUTF: illegal constant");
  }
  ShouldNotReachHere();
  return NULL;
JVM_END


JVM_ENTRY(const char*, JVM_GetCPClassNameUTF(JNIEnv *env, jclass cls, jint cp_index))
  JVMWrapper("JVM_GetCPClassNameUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  symbolOop classname = cp->klass_name_at(cp_index);
  return classname->as_utf8();
JVM_END


JVM_ENTRY(const char*, JVM_GetCPFieldClassNameUTF(JNIEnv *env, jclass cls, jint cp_index))
  JVMWrapper("JVM_GetCPFieldClassNameUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_Fieldref: {
      int class_index = cp->uncached_klass_ref_index_at(cp_index);
      symbolOop classname = cp->klass_name_at(class_index);
      return classname->as_utf8();
    }
    default:
      fatal("JVM_GetCPFieldClassNameUTF: illegal constant");
  }
  ShouldNotReachHere();
  return NULL;
JVM_END


JVM_ENTRY(const char*, JVM_GetCPMethodClassNameUTF(JNIEnv *env, jclass cls, jint cp_index))
  JVMWrapper("JVM_GetCPMethodClassNameUTF");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  k = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_InterfaceMethodref: {
      int class_index = cp->uncached_klass_ref_index_at(cp_index);
      symbolOop classname = cp->klass_name_at(class_index);
      return classname->as_utf8();
    }
    default:
      fatal("JVM_GetCPMethodClassNameUTF: illegal constant");
  }
  ShouldNotReachHere();
  return NULL;
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetCPFieldModifiers(JNIEnv *env, jclass cls, int cp_index, jclass called_cls))
  JVMWrapper("JVM_GetCPFieldModifiers");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  klassOop k_called = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(called_cls));
  k        = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  k_called = JvmtiThreadState::class_to_verify_considering_redefinition(k_called, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  constantPoolOop cp_called = instanceKlass::cast(k_called)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_Fieldref: {
      symbolOop name      = cp->uncached_name_ref_at(cp_index);
      symbolOop signature = cp->uncached_signature_ref_at(cp_index);
      typeArrayOop fields = instanceKlass::cast(k_called)->fields();
      int fields_count = fields->length();
      for (int i = 0; i < fields_count; i += instanceKlass::next_offset) {
        if (cp_called->symbol_at(fields->ushort_at(i + instanceKlass::name_index_offset)) == name &&
            cp_called->symbol_at(fields->ushort_at(i + instanceKlass::signature_index_offset)) == signature) {
          return fields->ushort_at(i + instanceKlass::access_flags_offset) & JVM_RECOGNIZED_FIELD_MODIFIERS;
        }
      }
      return -1;
    }
    default:
      fatal("JVM_GetCPFieldModifiers: illegal constant");
  }
  ShouldNotReachHere();
  return 0;
JVM_END


JVM_QUICK_ENTRY(jint, JVM_GetCPMethodModifiers(JNIEnv *env, jclass cls, int cp_index, jclass called_cls))
  JVMWrapper("JVM_GetCPMethodModifiers");
  klassOop k = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls));
  klassOop k_called = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(called_cls));
  k        = JvmtiThreadState::class_to_verify_considering_redefinition(k, thread);
  k_called = JvmtiThreadState::class_to_verify_considering_redefinition(k_called, thread);
  constantPoolOop cp = instanceKlass::cast(k)->constants();
  switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_InterfaceMethodref: {
      symbolOop name      = cp->uncached_name_ref_at(cp_index);
      symbolOop signature = cp->uncached_signature_ref_at(cp_index);
      objArrayOop methods = instanceKlass::cast(k_called)->methods();
      int methods_count = methods->length();
      for (int i = 0; i < methods_count; i++) {
        methodOop method = methodOop(methods->obj_at(i));
        if (method->name() == name && method->signature() == signature) {
            return method->access_flags().as_int() & JVM_RECOGNIZED_METHOD_MODIFIERS;
        }
      }
      return -1;
    }
    default:
      fatal("JVM_GetCPMethodModifiers: illegal constant");
  }
  ShouldNotReachHere();
  return 0;
JVM_END


// Misc //////////////////////////////////////////////////////////////////////////////////////////////

JVM_LEAF(void, JVM_ReleaseUTF(const char *utf))
  // So long as UTF8::convert_to_utf8 returns resource strings, we don't have to do anything
JVM_END


JVM_ENTRY(jboolean, JVM_IsSameClassPackage(JNIEnv *env, jclass class1, jclass class2))
  JVMWrapper("JVM_IsSameClassPackage");
  oop class1_mirror = JNIHandles::resolve_non_null(class1);
  oop class2_mirror = JNIHandles::resolve_non_null(class2);
  klassOop klass1 = java_lang_Class::as_klassOop(class1_mirror);
  klassOop klass2 = java_lang_Class::as_klassOop(class2_mirror);
  return (jboolean) Reflection::is_same_class_package(klass1, klass2);
JVM_END


// IO functions ////////////////////////////////////////////////////////////////////////////////////////

JVM_LEAF(jint, JVM_Open(const char *fname, jint flags, jint mode))
  JVMWrapper2("JVM_Open (%s)", fname);

  //%note jvm_r6
  int result = hpi::open(fname, flags, mode);
  if (result >= 0) {
    return result;
  } else {
    switch(errno) {
      case EEXIST:
        return JVM_EEXIST;
      default:
        return -1;
    }
  }
JVM_END


JVM_LEAF(jint, JVM_Close(jint fd))
  JVMWrapper2("JVM_Close (0x%x)", fd);
  //%note jvm_r6
  return hpi::close(fd);
JVM_END


JVM_LEAF(jint, JVM_Read(jint fd, char *buf, jint nbytes))
  JVMWrapper2("JVM_Read (0x%x)", fd);

  //%note jvm_r6
  return (jint)hpi::read(fd, buf, nbytes);
JVM_END


JVM_LEAF(jint, JVM_Write(jint fd, char *buf, jint nbytes))
  JVMWrapper2("JVM_Write (0x%x)", fd);

  //%note jvm_r6
  return (jint)hpi::write(fd, buf, nbytes);
JVM_END


JVM_LEAF(jint, JVM_Available(jint fd, jlong *pbytes))
  JVMWrapper2("JVM_Available (0x%x)", fd);
  //%note jvm_r6
  return hpi::available(fd, pbytes);
JVM_END


JVM_LEAF(jlong, JVM_Lseek(jint fd, jlong offset, jint whence))
  JVMWrapper4("JVM_Lseek (0x%x, %Ld, %d)", fd, offset, whence);
  //%note jvm_r6
  return hpi::lseek(fd, offset, whence);
JVM_END


JVM_LEAF(jint, JVM_SetLength(jint fd, jlong length))
  JVMWrapper3("JVM_SetLength (0x%x, %Ld)", fd, length);
  return hpi::ftruncate(fd, length);
JVM_END


JVM_LEAF(jint, JVM_Sync(jint fd))
  JVMWrapper2("JVM_Sync (0x%x)", fd);
  //%note jvm_r6
  return hpi::fsync(fd);
JVM_END


// Printing support //////////////////////////////////////////////////
extern "C" {

int jio_vsnprintf(char *str, size_t count, const char *fmt, va_list args) {
  // see bug 4399518, 4417214
  if ((intptr_t)count <= 0) return -1;
  return vsnprintf(str, count, fmt, args);
}


int jio_snprintf(char *str, size_t count, const char *fmt, ...) {
  va_list args;
  int len;
  va_start(args, fmt);
  len = jio_vsnprintf(str, count, fmt, args);
  va_end(args);
  return len;
}


int jio_fprintf(FILE* f, const char *fmt, ...) {
  int len;
  va_list args;
  va_start(args, fmt);
  len = jio_vfprintf(f, fmt, args);
  va_end(args);
  return len;
}


int jio_vfprintf(FILE* f, const char *fmt, va_list args) {
  if (Arguments::vfprintf_hook() != NULL) {
     return Arguments::vfprintf_hook()(f, fmt, args);
  } else {
    return vfprintf(f, fmt, args);
  }
}


int jio_printf(const char *fmt, ...) {
  int len;
  va_list args;
  va_start(args, fmt);
  len = jio_vfprintf(defaultStream::output_stream(), fmt, args);
  va_end(args);
  return len;
}


// HotSpot specific jio method
void jio_print(const char* s) {
  // Try to make this function as atomic as possible.
  if (Arguments::vfprintf_hook() != NULL) {
    jio_fprintf(defaultStream::output_stream(), "%s", s);
  } else {
    // Make an unused local variable to avoid warning from gcc 4.x compiler.
    size_t count = ::write(defaultStream::output_fd(), s, (int)strlen(s));
  }
}

} // Extern C

// java.lang.Thread //////////////////////////////////////////////////////////////////////////////

// In most of the JVM Thread support functions we need to be sure to lock the Threads_lock
// to prevent the target thread from exiting after we have a pointer to the C++ Thread or
// OSThread objects.  The exception to this rule is when the target object is the thread
// doing the operation, in which case we know that the thread won't exit until the
// operation is done (all exits being voluntary).  There are a few cases where it is
// rather silly to do operations on yourself, like resuming yourself or asking whether
// you are alive.  While these can still happen, they are not subject to deadlocks if
// the lock is held while the operation occurs (this is not the case for suspend, for
// instance), and are very unlikely.  Because IsAlive needs to be fast and its
// implementation is local to this file, we always lock Threads_lock for that one.

static void thread_entry(JavaThread* thread, TRAPS) {
  HandleMark hm(THREAD);
  Handle obj(THREAD, thread->threadObj());
  JavaValue result(T_VOID);
  JavaCalls::call_virtual(&result,
                          obj,
                          KlassHandle(THREAD, SystemDictionary::Thread_klass()),
                          vmSymbolHandles::run_method_name(),
                          vmSymbolHandles::void_method_signature(),
                          THREAD);
}


JVM_ENTRY(void, JVM_StartThread(JNIEnv* env, jobject jthread))
  JVMWrapper("JVM_StartThread");
  JavaThread *native_thread = NULL;

  // We cannot hold the Threads_lock when we throw an exception,
  // due to rank ordering issues. Example:  we might need to grab the
  // Heap_lock while we construct the exception.
  bool throw_illegal_thread_state = false;

  // We must release the Threads_lock before we can post a jvmti event
  // in Thread::start.
  {
    // Ensure that the C++ Thread and OSThread structures aren't freed before
    // we operate.
    MutexLocker mu(Threads_lock);

    // Check to see if we're running a thread that's already exited or was
    // stopped (is_stillborn) or is still active (thread is not NULL).
    if (java_lang_Thread::is_stillborn(JNIHandles::resolve_non_null(jthread)) ||
        java_lang_Thread::thread(JNIHandles::resolve_non_null(jthread)) != NULL) {
        throw_illegal_thread_state = true;
    } else {
      jlong size =
             java_lang_Thread::stackSize(JNIHandles::resolve_non_null(jthread));
      // Allocate the C++ Thread structure and create the native thread.  The
      // stack size retrieved from java is signed, but the constructor takes
      // size_t (an unsigned type), so avoid passing negative values which would
      // result in really large stacks.
      size_t sz = size > 0 ? (size_t) size : 0;
      native_thread = new JavaThread(&thread_entry, sz);

      // At this point it may be possible that no osthread was created for the
      // JavaThread due to lack of memory. Check for this situation and throw
      // an exception if necessary. Eventually we may want to change this so
      // that we only grab the lock if the thread was created successfully -
      // then we can also do this check and throw the exception in the
      // JavaThread constructor.
      if (native_thread->osthread() != NULL) {
        // Note: the current thread is not being used within "prepare".
        native_thread->prepare(jthread);
      }
    }
  }

  if (throw_illegal_thread_state) {
    THROW(vmSymbols::java_lang_IllegalThreadStateException());
  }

  assert(native_thread != NULL, "Starting null thread?");

  if (native_thread->osthread() == NULL) {
    // No one should hold a reference to the 'native_thread'.
    delete native_thread;
    if (JvmtiExport::should_post_resource_exhausted()) {
      JvmtiExport::post_resource_exhausted(
        JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR | JVMTI_RESOURCE_EXHAUSTED_THREADS,
        "unable to create new native thread");
    }
    THROW_MSG(vmSymbols::java_lang_OutOfMemoryError(),
              "unable to create new native thread");
  }

  Thread::start(native_thread);

JVM_END

// JVM_Stop is implemented using a VM_Operation, so threads are forced to safepoints
// before the quasi-asynchronous exception is delivered.  This is a little obtrusive,
// but is thought to be reliable and simple. In the case, where the receiver is the
// save thread as the sender, no safepoint is needed.
JVM_ENTRY(void, JVM_StopThread(JNIEnv* env, jobject jthread, jobject throwable))
  JVMWrapper("JVM_StopThread");

  oop java_throwable = JNIHandles::resolve(throwable);
  if (java_throwable == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }
  oop java_thread = JNIHandles::resolve_non_null(jthread);
  JavaThread* receiver = java_lang_Thread::thread(java_thread);
  Events::log("JVM_StopThread thread JavaThread " INTPTR_FORMAT " as oop " INTPTR_FORMAT " [exception " INTPTR_FORMAT "]", receiver, (address)java_thread, throwable);
  // First check if thread already exited
  if (receiver != NULL) {
    // Check if exception is getting thrown at self (use oop equality, since the
    // target object might exit)
    if (java_thread == thread->threadObj()) {
      // This is a change from JDK 1.1, but JDK 1.2 will also do it:
      // NOTE (from JDK 1.2): this is done solely to prevent stopped
      // threads from being restarted.
      // Fix for 4314342, 4145910, perhaps others: it now doesn't have
      // any effect on the "liveness" of a thread; see
      // JVM_IsThreadAlive, below.
      if (java_throwable->is_a(SystemDictionary::ThreadDeath_klass())) {
        java_lang_Thread::set_stillborn(java_thread);
      }
      THROW_OOP(java_throwable);
    } else {
      // Enques a VM_Operation to stop all threads and then deliver the exception...
      Thread::send_async_exception(java_thread, JNIHandles::resolve(throwable));
    }
  }
JVM_END


JVM_ENTRY(jboolean, JVM_IsThreadAlive(JNIEnv* env, jobject jthread))
  JVMWrapper("JVM_IsThreadAlive");

  oop thread_oop = JNIHandles::resolve_non_null(jthread);
  return java_lang_Thread::is_alive(thread_oop);
JVM_END


JVM_ENTRY(void, JVM_SuspendThread(JNIEnv* env, jobject jthread))
  JVMWrapper("JVM_SuspendThread");
  oop java_thread = JNIHandles::resolve_non_null(jthread);
  JavaThread* receiver = java_lang_Thread::thread(java_thread);

  if (receiver != NULL) {
    // thread has run and has not exited (still on threads list)

    {
      MutexLockerEx ml(receiver->SR_lock(), Mutex::_no_safepoint_check_flag);
      if (receiver->is_external_suspend()) {
        // Don't allow nested external suspend requests. We can't return
        // an error from this interface so just ignore the problem.
        return;
      }
      if (receiver->is_exiting()) { // thread is in the process of exiting
        return;
      }
      receiver->set_external_suspend();
    }

    // java_suspend() will catch threads in the process of exiting
    // and will ignore them.
    receiver->java_suspend();

    // It would be nice to have the following assertion in all the
    // time, but it is possible for a racing resume request to have
    // resumed this thread right after we suspended it. Temporarily
    // enable this assertion if you are chasing a different kind of
    // bug.
    //
    // assert(java_lang_Thread::thread(receiver->threadObj()) == NULL ||
    //   receiver->is_being_ext_suspended(), "thread is not suspended");
  }
JVM_END


JVM_ENTRY(void, JVM_ResumeThread(JNIEnv* env, jobject jthread))
  JVMWrapper("JVM_ResumeThread");
  // Ensure that the C++ Thread and OSThread structures aren't freed before we operate.
  // We need to *always* get the threads lock here, since this operation cannot be allowed during
  // a safepoint. The safepoint code relies on suspending a thread to examine its state. If other
  // threads randomly resumes threads, then a thread might not be suspended when the safepoint code
  // looks at it.
  MutexLocker ml(Threads_lock);
  JavaThread* thr = java_lang_Thread::thread(JNIHandles::resolve_non_null(jthread));
  if (thr != NULL) {
    // the thread has run and is not in the process of exiting
    thr->java_resume();
  }
JVM_END


JVM_ENTRY(void, JVM_SetThreadPriority(JNIEnv* env, jobject jthread, jint prio))
  JVMWrapper("JVM_SetThreadPriority");
  // Ensure that the C++ Thread and OSThread structures aren't freed before we operate
  MutexLocker ml(Threads_lock);
  oop java_thread = JNIHandles::resolve_non_null(jthread);
  java_lang_Thread::set_priority(java_thread, (ThreadPriority)prio);
  JavaThread* thr = java_lang_Thread::thread(java_thread);
  if (thr != NULL) {                  // Thread not yet started; priority pushed down when it is
    Thread::set_priority(thr, (ThreadPriority)prio);
  }
JVM_END


JVM_ENTRY(void, JVM_Yield(JNIEnv *env, jclass threadClass))
  JVMWrapper("JVM_Yield");
  if (os::dont_yield()) return;
  // When ConvertYieldToSleep is off (default), this matches the classic VM use of yield.
  // Critical for similar threading behaviour
  if (ConvertYieldToSleep) {
    os::sleep(thread, MinSleepInterval, false);
  } else {
    os::yield();
  }
JVM_END


JVM_ENTRY(void, JVM_Sleep(JNIEnv* env, jclass threadClass, jlong millis))
  JVMWrapper("JVM_Sleep");

  if (millis < 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }

  if (Thread::is_interrupted (THREAD, true) && !HAS_PENDING_EXCEPTION) {
    THROW_MSG(vmSymbols::java_lang_InterruptedException(), "sleep interrupted");
  }

  // Save current thread state and restore it at the end of this block.
  // And set new thread state to SLEEPING.
  JavaThreadSleepState jtss(thread);

  if (millis == 0) {
    // When ConvertSleepToYield is on, this matches the classic VM implementation of
    // JVM_Sleep. Critical for similar threading behaviour (Win32)
    // It appears that in certain GUI contexts, it may be beneficial to do a short sleep
    // for SOLARIS
    if (ConvertSleepToYield) {
      os::yield();
    } else {
      ThreadState old_state = thread->osthread()->get_state();
      thread->osthread()->set_state(SLEEPING);
      os::sleep(thread, MinSleepInterval, false);
      thread->osthread()->set_state(old_state);
    }
  } else {
    ThreadState old_state = thread->osthread()->get_state();
    thread->osthread()->set_state(SLEEPING);
    if (os::sleep(thread, millis, true) == OS_INTRPT) {
      // An asynchronous exception (e.g., ThreadDeathException) could have been thrown on
      // us while we were sleeping. We do not overwrite those.
      if (!HAS_PENDING_EXCEPTION) {
        // TODO-FIXME: THROW_MSG returns which means we will not call set_state()
        // to properly restore the thread state.  That's likely wrong.
        THROW_MSG(vmSymbols::java_lang_InterruptedException(), "sleep interrupted");
      }
    }
    thread->osthread()->set_state(old_state);
  }
JVM_END

JVM_ENTRY(jobject, JVM_CurrentThread(JNIEnv* env, jclass threadClass))
  JVMWrapper("JVM_CurrentThread");
  oop jthread = thread->threadObj();
  assert (thread != NULL, "no current thread!");
  return JNIHandles::make_local(env, jthread);
JVM_END


JVM_ENTRY(jint, JVM_CountStackFrames(JNIEnv* env, jobject jthread))
  JVMWrapper("JVM_CountStackFrames");

  // Ensure that the C++ Thread and OSThread structures aren't freed before we operate
  oop java_thread = JNIHandles::resolve_non_null(jthread);
  bool throw_illegal_thread_state = false;
  int count = 0;

  {
    MutexLockerEx ml(thread->threadObj() == java_thread ? NULL : Threads_lock);
    // We need to re-resolve the java_thread, since a GC might have happened during the
    // acquire of the lock
    JavaThread* thr = java_lang_Thread::thread(JNIHandles::resolve_non_null(jthread));

    if (thr == NULL) {
      // do nothing
    } else if(! thr->is_external_suspend() || ! thr->frame_anchor()->walkable()) {
      // Check whether this java thread has been suspended already. If not, throws
      // IllegalThreadStateException. We defer to throw that exception until
      // Threads_lock is released since loading exception class has to leave VM.
      // The correct way to test a thread is actually suspended is
      // wait_for_ext_suspend_completion(), but we can't call that while holding
      // the Threads_lock. The above tests are sufficient for our purposes
      // provided the walkability of the stack is stable - which it isn't
      // 100% but close enough for most practical purposes.
      throw_illegal_thread_state = true;
    } else {
      // Count all java activation, i.e., number of vframes
      for(vframeStream vfst(thr); !vfst.at_end(); vfst.next()) {
        // Native frames are not counted
        if (!vfst.method()->is_native()) count++;
       }
    }
  }

  if (throw_illegal_thread_state) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalThreadStateException(),
                "this thread is not suspended");
  }
  return count;
JVM_END

// Consider: A better way to implement JVM_Interrupt() is to acquire
// Threads_lock to resolve the jthread into a Thread pointer, fetch
// Thread->platformevent, Thread->native_thr, Thread->parker, etc.,
// drop Threads_lock, and the perform the unpark() and thr_kill() operations
// outside the critical section.  Threads_lock is hot so we want to minimize
// the hold-time.  A cleaner interface would be to decompose interrupt into
// two steps.  The 1st phase, performed under Threads_lock, would return
// a closure that'd be invoked after Threads_lock was dropped.
// This tactic is safe as PlatformEvent and Parkers are type-stable (TSM) and
// admit spurious wakeups.

JVM_ENTRY(void, JVM_Interrupt(JNIEnv* env, jobject jthread))
  JVMWrapper("JVM_Interrupt");

  // Ensure that the C++ Thread and OSThread structures aren't freed before we operate
  oop java_thread = JNIHandles::resolve_non_null(jthread);
  MutexLockerEx ml(thread->threadObj() == java_thread ? NULL : Threads_lock);
  // We need to re-resolve the java_thread, since a GC might have happened during the
  // acquire of the lock
  JavaThread* thr = java_lang_Thread::thread(JNIHandles::resolve_non_null(jthread));
  if (thr != NULL) {
    Thread::interrupt(thr);
  }
JVM_END


JVM_QUICK_ENTRY(jboolean, JVM_IsInterrupted(JNIEnv* env, jobject jthread, jboolean clear_interrupted))
  JVMWrapper("JVM_IsInterrupted");

  // Ensure that the C++ Thread and OSThread structures aren't freed before we operate
  oop java_thread = JNIHandles::resolve_non_null(jthread);
  MutexLockerEx ml(thread->threadObj() == java_thread ? NULL : Threads_lock);
  // We need to re-resolve the java_thread, since a GC might have happened during the
  // acquire of the lock
  JavaThread* thr = java_lang_Thread::thread(JNIHandles::resolve_non_null(jthread));
  if (thr == NULL) {
    return JNI_FALSE;
  } else {
    return (jboolean) Thread::is_interrupted(thr, clear_interrupted != 0);
  }
JVM_END


// Return true iff the current thread has locked the object passed in

JVM_ENTRY(jboolean, JVM_HoldsLock(JNIEnv* env, jclass threadClass, jobject obj))
  JVMWrapper("JVM_HoldsLock");
  assert(THREAD->is_Java_thread(), "sanity check");
  if (obj == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), JNI_FALSE);
  }
  Handle h_obj(THREAD, JNIHandles::resolve(obj));
  return ObjectSynchronizer::current_thread_holds_lock((JavaThread*)THREAD, h_obj);
JVM_END


JVM_ENTRY(void, JVM_DumpAllStacks(JNIEnv* env, jclass))
  JVMWrapper("JVM_DumpAllStacks");
  VM_PrintThreads op;
  VMThread::execute(&op);
  if (JvmtiExport::should_post_data_dump()) {
    JvmtiExport::post_data_dump();
  }
JVM_END


// java.lang.SecurityManager ///////////////////////////////////////////////////////////////////////

static bool is_trusted_frame(JavaThread* jthread, vframeStream* vfst) {
  assert(jthread->is_Java_thread(), "must be a Java thread");
  if (jthread->privileged_stack_top() == NULL) return false;
  if (jthread->privileged_stack_top()->frame_id() == vfst->frame_id()) {
    oop loader = jthread->privileged_stack_top()->class_loader();
    if (loader == NULL) return true;
    bool trusted = java_lang_ClassLoader::is_trusted_loader(loader);
    if (trusted) return true;
  }
  return false;
}

JVM_ENTRY(jclass, JVM_CurrentLoadedClass(JNIEnv *env))
  JVMWrapper("JVM_CurrentLoadedClass");
  ResourceMark rm(THREAD);

  for (vframeStream vfst(thread); !vfst.at_end(); vfst.next()) {
    // if a method in a class in a trusted loader is in a doPrivileged, return NULL
    bool trusted = is_trusted_frame(thread, &vfst);
    if (trusted) return NULL;

    methodOop m = vfst.method();
    if (!m->is_native()) {
      klassOop holder = m->method_holder();
      oop      loader = instanceKlass::cast(holder)->class_loader();
      if (loader != NULL && !java_lang_ClassLoader::is_trusted_loader(loader)) {
        return (jclass) JNIHandles::make_local(env, Klass::cast(holder)->java_mirror());
      }
    }
  }
  return NULL;
JVM_END


JVM_ENTRY(jobject, JVM_CurrentClassLoader(JNIEnv *env))
  JVMWrapper("JVM_CurrentClassLoader");
  ResourceMark rm(THREAD);

  for (vframeStream vfst(thread); !vfst.at_end(); vfst.next()) {

    // if a method in a class in a trusted loader is in a doPrivileged, return NULL
    bool trusted = is_trusted_frame(thread, &vfst);
    if (trusted) return NULL;

    methodOop m = vfst.method();
    if (!m->is_native()) {
      klassOop holder = m->method_holder();
      assert(holder->is_klass(), "just checking");
      oop loader = instanceKlass::cast(holder)->class_loader();
      if (loader != NULL && !java_lang_ClassLoader::is_trusted_loader(loader)) {
        return JNIHandles::make_local(env, loader);
      }
    }
  }
  return NULL;
JVM_END


// Utility object for collecting method holders walking down the stack
class KlassLink: public ResourceObj {
 public:
  KlassHandle klass;
  KlassLink*  next;

  KlassLink(KlassHandle k) { klass = k; next = NULL; }
};


JVM_ENTRY(jobjectArray, JVM_GetClassContext(JNIEnv *env))
  JVMWrapper("JVM_GetClassContext");
  ResourceMark rm(THREAD);
  JvmtiVMObjectAllocEventCollector oam;
  // Collect linked list of (handles to) method holders
  KlassLink* first = NULL;
  KlassLink* last  = NULL;
  int depth = 0;

  for(vframeStream vfst(thread); !vfst.at_end(); vfst.security_get_caller_frame(1)) {
    // Native frames are not returned
    if (!vfst.method()->is_native()) {
      klassOop holder = vfst.method()->method_holder();
      assert(holder->is_klass(), "just checking");
      depth++;
      KlassLink* l = new KlassLink(KlassHandle(thread, holder));
      if (first == NULL) {
        first = last = l;
      } else {
        last->next = l;
        last = l;
      }
    }
  }

  // Create result array of type [Ljava/lang/Class;
  objArrayOop result = oopFactory::new_objArray(SystemDictionary::Class_klass(), depth, CHECK_NULL);
  // Fill in mirrors corresponding to method holders
  int index = 0;
  while (first != NULL) {
    result->obj_at_put(index++, Klass::cast(first->klass())->java_mirror());
    first = first->next;
  }
  assert(index == depth, "just checking");

  return (jobjectArray) JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jint, JVM_ClassDepth(JNIEnv *env, jstring name))
  JVMWrapper("JVM_ClassDepth");
  ResourceMark rm(THREAD);
  Handle h_name (THREAD, JNIHandles::resolve_non_null(name));
  Handle class_name_str = java_lang_String::internalize_classname(h_name, CHECK_0);

  const char* str = java_lang_String::as_utf8_string(class_name_str());
  symbolHandle class_name_sym =
                symbolHandle(THREAD, SymbolTable::probe(str, (int)strlen(str)));
  if (class_name_sym.is_null()) {
    return -1;
  }

  int depth = 0;

  for(vframeStream vfst(thread); !vfst.at_end(); vfst.next()) {
    if (!vfst.method()->is_native()) {
      klassOop holder = vfst.method()->method_holder();
      assert(holder->is_klass(), "just checking");
      if (instanceKlass::cast(holder)->name() == class_name_sym()) {
        return depth;
      }
      depth++;
    }
  }
  return -1;
JVM_END


JVM_ENTRY(jint, JVM_ClassLoaderDepth(JNIEnv *env))
  JVMWrapper("JVM_ClassLoaderDepth");
  ResourceMark rm(THREAD);
  int depth = 0;
  for (vframeStream vfst(thread); !vfst.at_end(); vfst.next()) {
    // if a method in a class in a trusted loader is in a doPrivileged, return -1
    bool trusted = is_trusted_frame(thread, &vfst);
    if (trusted) return -1;

    methodOop m = vfst.method();
    if (!m->is_native()) {
      klassOop holder = m->method_holder();
      assert(holder->is_klass(), "just checking");
      oop loader = instanceKlass::cast(holder)->class_loader();
      if (loader != NULL && !java_lang_ClassLoader::is_trusted_loader(loader)) {
        return depth;
      }
      depth++;
    }
  }
  return -1;
JVM_END


// java.lang.Package ////////////////////////////////////////////////////////////////


JVM_ENTRY(jstring, JVM_GetSystemPackage(JNIEnv *env, jstring name))
  JVMWrapper("JVM_GetSystemPackage");
  ResourceMark rm(THREAD);
  JvmtiVMObjectAllocEventCollector oam;
  char* str = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(name));
  oop result = ClassLoader::get_system_package(str, CHECK_NULL);
  return (jstring) JNIHandles::make_local(result);
JVM_END


JVM_ENTRY(jobjectArray, JVM_GetSystemPackages(JNIEnv *env))
  JVMWrapper("JVM_GetSystemPackages");
  JvmtiVMObjectAllocEventCollector oam;
  objArrayOop result = ClassLoader::get_system_packages(CHECK_NULL);
  return (jobjectArray) JNIHandles::make_local(result);
JVM_END


// ObjectInputStream ///////////////////////////////////////////////////////////////

bool force_verify_field_access(klassOop current_class, klassOop field_class, AccessFlags access, bool classloader_only) {
  if (current_class == NULL) {
    return true;
  }
  if ((current_class == field_class) || access.is_public()) {
    return true;
  }

  if (access.is_protected()) {
    // See if current_class is a subclass of field_class
    if (Klass::cast(current_class)->is_subclass_of(field_class)) {
      return true;
    }
  }

  return (!access.is_private() && instanceKlass::cast(current_class)->is_same_class_package(field_class));
}


// JVM_AllocateNewObject and JVM_AllocateNewArray are unused as of 1.4
JVM_ENTRY(jobject, JVM_AllocateNewObject(JNIEnv *env, jobject receiver, jclass currClass, jclass initClass))
  JVMWrapper("JVM_AllocateNewObject");
  JvmtiVMObjectAllocEventCollector oam;
  // Receiver is not used
  oop curr_mirror = JNIHandles::resolve_non_null(currClass);
  oop init_mirror = JNIHandles::resolve_non_null(initClass);

  // Cannot instantiate primitive types
  if (java_lang_Class::is_primitive(curr_mirror) || java_lang_Class::is_primitive(init_mirror)) {
    ResourceMark rm(THREAD);
    THROW_0(vmSymbols::java_lang_InvalidClassException());
  }

  // Arrays not allowed here, must use JVM_AllocateNewArray
  if (Klass::cast(java_lang_Class::as_klassOop(curr_mirror))->oop_is_javaArray() ||
      Klass::cast(java_lang_Class::as_klassOop(init_mirror))->oop_is_javaArray()) {
    ResourceMark rm(THREAD);
    THROW_0(vmSymbols::java_lang_InvalidClassException());
  }

  instanceKlassHandle curr_klass (THREAD, java_lang_Class::as_klassOop(curr_mirror));
  instanceKlassHandle init_klass (THREAD, java_lang_Class::as_klassOop(init_mirror));

  assert(curr_klass->is_subclass_of(init_klass()), "just checking");

  // Interfaces, abstract classes, and java.lang.Class classes cannot be instantiated directly.
  curr_klass->check_valid_for_instantiation(false, CHECK_NULL);

  // Make sure klass is initialized, since we are about to instantiate one of them.
  curr_klass->initialize(CHECK_NULL);

 methodHandle m (THREAD,
                 init_klass->find_method(vmSymbols::object_initializer_name(),
                                         vmSymbols::void_method_signature()));
  if (m.is_null()) {
    ResourceMark rm(THREAD);
    THROW_MSG_0(vmSymbols::java_lang_NoSuchMethodError(),
                methodOopDesc::name_and_sig_as_C_string(Klass::cast(init_klass()),
                                          vmSymbols::object_initializer_name(),
                                          vmSymbols::void_method_signature()));
  }

  if (curr_klass ==  init_klass && !m->is_public()) {
    // Calling the constructor for class 'curr_klass'.
    // Only allow calls to a public no-arg constructor.
    // This path corresponds to creating an Externalizable object.
    THROW_0(vmSymbols::java_lang_IllegalAccessException());
  }

  if (!force_verify_field_access(curr_klass(), init_klass(), m->access_flags(), false)) {
    // subclass 'curr_klass' does not have access to no-arg constructor of 'initcb'
    THROW_0(vmSymbols::java_lang_IllegalAccessException());
  }

  Handle obj = curr_klass->allocate_instance_handle(CHECK_NULL);
  // Call constructor m. This might call a constructor higher up in the hierachy
  JavaCalls::call_default_constructor(thread, m, obj, CHECK_NULL);

  return JNIHandles::make_local(obj());
JVM_END


JVM_ENTRY(jobject, JVM_AllocateNewArray(JNIEnv *env, jobject obj, jclass currClass, jint length))
  JVMWrapper("JVM_AllocateNewArray");
  JvmtiVMObjectAllocEventCollector oam;
  oop mirror = JNIHandles::resolve_non_null(currClass);

  if (java_lang_Class::is_primitive(mirror)) {
    THROW_0(vmSymbols::java_lang_InvalidClassException());
  }
  klassOop k = java_lang_Class::as_klassOop(mirror);
  oop result;

  if (k->klass_part()->oop_is_typeArray()) {
    // typeArray
    result = typeArrayKlass::cast(k)->allocate(length, CHECK_NULL);
  } else if (k->klass_part()->oop_is_objArray()) {
    // objArray
    objArrayKlassHandle oak(THREAD, k);
    oak->initialize(CHECK_NULL); // make sure class is initialized (matches Classic VM behavior)
    result = oak->allocate(length, CHECK_NULL);
  } else {
    THROW_0(vmSymbols::java_lang_InvalidClassException());
  }
  return JNIHandles::make_local(env, result);
JVM_END


// Return the first non-null class loader up the execution stack, or null
// if only code from the null class loader is on the stack.

JVM_ENTRY(jobject, JVM_LatestUserDefinedLoader(JNIEnv *env))
  for (vframeStream vfst(thread); !vfst.at_end(); vfst.next()) {
    // UseNewReflection
    vfst.skip_reflection_related_frames(); // Only needed for 1.4 reflection
    klassOop holder = vfst.method()->method_holder();
    oop loader = instanceKlass::cast(holder)->class_loader();
    if (loader != NULL) {
      return JNIHandles::make_local(env, loader);
    }
  }
  return NULL;
JVM_END


// Load a class relative to the most recent class on the stack  with a non-null
// classloader.
// This function has been deprecated and should not be considered part of the
// specified JVM interface.

JVM_ENTRY(jclass, JVM_LoadClass0(JNIEnv *env, jobject receiver,
                                 jclass currClass, jstring currClassName))
  JVMWrapper("JVM_LoadClass0");
  // Receiver is not used
  ResourceMark rm(THREAD);

  // Class name argument is not guaranteed to be in internal format
  Handle classname (THREAD, JNIHandles::resolve_non_null(currClassName));
  Handle string = java_lang_String::internalize_classname(classname, CHECK_NULL);

  const char* str = java_lang_String::as_utf8_string(string());

  if (str == NULL || (int)strlen(str) > symbolOopDesc::max_length()) {
    // It's impossible to create this class;  the name cannot fit
    // into the constant pool.
    THROW_MSG_0(vmSymbols::java_lang_NoClassDefFoundError(), str);
  }

  symbolHandle name = oopFactory::new_symbol_handle(str, CHECK_NULL);
  Handle curr_klass (THREAD, JNIHandles::resolve(currClass));
  // Find the most recent class on the stack with a non-null classloader
  oop loader = NULL;
  oop protection_domain = NULL;
  if (curr_klass.is_null()) {
    for (vframeStream vfst(thread);
         !vfst.at_end() && loader == NULL;
         vfst.next()) {
      if (!vfst.method()->is_native()) {
        klassOop holder = vfst.method()->method_holder();
        loader             = instanceKlass::cast(holder)->class_loader();
        protection_domain  = instanceKlass::cast(holder)->protection_domain();
      }
    }
  } else {
    klassOop curr_klass_oop = java_lang_Class::as_klassOop(curr_klass());
    loader            = instanceKlass::cast(curr_klass_oop)->class_loader();
    protection_domain = instanceKlass::cast(curr_klass_oop)->protection_domain();
  }
  Handle h_loader(THREAD, loader);
  Handle h_prot  (THREAD, protection_domain);
  jclass result =  find_class_from_class_loader(env, name, true, h_loader, h_prot,
                                                false, thread);
  if (TraceClassResolution && result != NULL) {
    trace_class_resolution(java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(result)));
  }
  return result;
JVM_END


// Array ///////////////////////////////////////////////////////////////////////////////////////////


// resolve array handle and check arguments
static inline arrayOop check_array(JNIEnv *env, jobject arr, bool type_array_only, TRAPS) {
  if (arr == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  oop a = JNIHandles::resolve_non_null(arr);
  if (!a->is_javaArray() || (type_array_only && !a->is_typeArray())) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Argument is not an array");
  }
  return arrayOop(a);
}


JVM_ENTRY(jint, JVM_GetArrayLength(JNIEnv *env, jobject arr))
  JVMWrapper("JVM_GetArrayLength");
  arrayOop a = check_array(env, arr, false, CHECK_0);
  return a->length();
JVM_END


JVM_ENTRY(jobject, JVM_GetArrayElement(JNIEnv *env, jobject arr, jint index))
  JVMWrapper("JVM_Array_Get");
  JvmtiVMObjectAllocEventCollector oam;
  arrayOop a = check_array(env, arr, false, CHECK_NULL);
  jvalue value;
  BasicType type = Reflection::array_get(&value, a, index, CHECK_NULL);
  oop box = Reflection::box(&value, type, CHECK_NULL);
  return JNIHandles::make_local(env, box);
JVM_END


JVM_ENTRY(jvalue, JVM_GetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jint wCode))
  JVMWrapper("JVM_GetPrimitiveArrayElement");
  jvalue value;
  value.i = 0; // to initialize value before getting used in CHECK
  arrayOop a = check_array(env, arr, true, CHECK_(value));
  assert(a->is_typeArray(), "just checking");
  BasicType type = Reflection::array_get(&value, a, index, CHECK_(value));
  BasicType wide_type = (BasicType) wCode;
  if (type != wide_type) {
    Reflection::widen(&value, type, wide_type, CHECK_(value));
  }
  return value;
JVM_END


JVM_ENTRY(void, JVM_SetArrayElement(JNIEnv *env, jobject arr, jint index, jobject val))
  JVMWrapper("JVM_SetArrayElement");
  arrayOop a = check_array(env, arr, false, CHECK);
  oop box = JNIHandles::resolve(val);
  jvalue value;
  value.i = 0; // to initialize value before getting used in CHECK
  BasicType value_type;
  if (a->is_objArray()) {
    // Make sure we do no unbox e.g. java/lang/Integer instances when storing into an object array
    value_type = Reflection::unbox_for_regular_object(box, &value);
  } else {
    value_type = Reflection::unbox_for_primitive(box, &value, CHECK);
  }
  Reflection::array_set(&value, a, index, value_type, CHECK);
JVM_END


JVM_ENTRY(void, JVM_SetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jvalue v, unsigned char vCode))
  JVMWrapper("JVM_SetPrimitiveArrayElement");
  arrayOop a = check_array(env, arr, true, CHECK);
  assert(a->is_typeArray(), "just checking");
  BasicType value_type = (BasicType) vCode;
  Reflection::array_set(&v, a, index, value_type, CHECK);
JVM_END


JVM_ENTRY(jobject, JVM_NewArray(JNIEnv *env, jclass eltClass, jint length))
  JVMWrapper("JVM_NewArray");
  JvmtiVMObjectAllocEventCollector oam;
  oop element_mirror = JNIHandles::resolve(eltClass);
  oop result = Reflection::reflect_new_array(element_mirror, length, CHECK_NULL);
  return JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jobject, JVM_NewMultiArray(JNIEnv *env, jclass eltClass, jintArray dim))
  JVMWrapper("JVM_NewMultiArray");
  JvmtiVMObjectAllocEventCollector oam;
  arrayOop dim_array = check_array(env, dim, true, CHECK_NULL);
  oop element_mirror = JNIHandles::resolve(eltClass);
  assert(dim_array->is_typeArray(), "just checking");
  oop result = Reflection::reflect_new_multi_array(element_mirror, typeArrayOop(dim_array), CHECK_NULL);
  return JNIHandles::make_local(env, result);
JVM_END


// Networking library support ////////////////////////////////////////////////////////////////////

JVM_LEAF(jint, JVM_InitializeSocketLibrary())
  JVMWrapper("JVM_InitializeSocketLibrary");
  return hpi::initialize_socket_library();
JVM_END


JVM_LEAF(jint, JVM_Socket(jint domain, jint type, jint protocol))
  JVMWrapper("JVM_Socket");
  return hpi::socket(domain, type, protocol);
JVM_END


JVM_LEAF(jint, JVM_SocketClose(jint fd))
  JVMWrapper2("JVM_SocketClose (0x%x)", fd);
  //%note jvm_r6
  return hpi::socket_close(fd);
JVM_END


JVM_LEAF(jint, JVM_SocketShutdown(jint fd, jint howto))
  JVMWrapper2("JVM_SocketShutdown (0x%x)", fd);
  //%note jvm_r6
  return hpi::socket_shutdown(fd, howto);
JVM_END


JVM_LEAF(jint, JVM_Recv(jint fd, char *buf, jint nBytes, jint flags))
  JVMWrapper2("JVM_Recv (0x%x)", fd);
  //%note jvm_r6
  return hpi::recv(fd, buf, nBytes, flags);
JVM_END


JVM_LEAF(jint, JVM_Send(jint fd, char *buf, jint nBytes, jint flags))
  JVMWrapper2("JVM_Send (0x%x)", fd);
  //%note jvm_r6
  return hpi::send(fd, buf, nBytes, flags);
JVM_END


JVM_LEAF(jint, JVM_Timeout(int fd, long timeout))
  JVMWrapper2("JVM_Timeout (0x%x)", fd);
  //%note jvm_r6
  return hpi::timeout(fd, timeout);
JVM_END


JVM_LEAF(jint, JVM_Listen(jint fd, jint count))
  JVMWrapper2("JVM_Listen (0x%x)", fd);
  //%note jvm_r6
  return hpi::listen(fd, count);
JVM_END


JVM_LEAF(jint, JVM_Connect(jint fd, struct sockaddr *him, jint len))
  JVMWrapper2("JVM_Connect (0x%x)", fd);
  //%note jvm_r6
  return hpi::connect(fd, him, len);
JVM_END


JVM_LEAF(jint, JVM_Bind(jint fd, struct sockaddr *him, jint len))
  JVMWrapper2("JVM_Bind (0x%x)", fd);
  //%note jvm_r6
  return hpi::bind(fd, him, len);
JVM_END


JVM_LEAF(jint, JVM_Accept(jint fd, struct sockaddr *him, jint *len))
  JVMWrapper2("JVM_Accept (0x%x)", fd);
  //%note jvm_r6
  return hpi::accept(fd, him, (int *)len);
JVM_END


JVM_LEAF(jint, JVM_RecvFrom(jint fd, char *buf, int nBytes, int flags, struct sockaddr *from, int *fromlen))
  JVMWrapper2("JVM_RecvFrom (0x%x)", fd);
  //%note jvm_r6
  return hpi::recvfrom(fd, buf, nBytes, flags, from, fromlen);
JVM_END


JVM_LEAF(jint, JVM_GetSockName(jint fd, struct sockaddr *him, int *len))
  JVMWrapper2("JVM_GetSockName (0x%x)", fd);
  //%note jvm_r6
  return hpi::get_sock_name(fd, him, len);
JVM_END


JVM_LEAF(jint, JVM_SendTo(jint fd, char *buf, int len, int flags, struct sockaddr *to, int tolen))
  JVMWrapper2("JVM_SendTo (0x%x)", fd);
  //%note jvm_r6
  return hpi::sendto(fd, buf, len, flags, to, tolen);
JVM_END


JVM_LEAF(jint, JVM_SocketAvailable(jint fd, jint *pbytes))
  JVMWrapper2("JVM_SocketAvailable (0x%x)", fd);
  //%note jvm_r6
  return hpi::socket_available(fd, pbytes);
JVM_END


JVM_LEAF(jint, JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen))
  JVMWrapper2("JVM_GetSockOpt (0x%x)", fd);
  //%note jvm_r6
  return hpi::get_sock_opt(fd, level, optname, optval, optlen);
JVM_END


JVM_LEAF(jint, JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen))
  JVMWrapper2("JVM_GetSockOpt (0x%x)", fd);
  //%note jvm_r6
  return hpi::set_sock_opt(fd, level, optname, optval, optlen);
JVM_END

JVM_LEAF(int, JVM_GetHostName(char* name, int namelen))
  JVMWrapper("JVM_GetHostName");
  return hpi::get_host_name(name, namelen);
JVM_END

#ifdef _WINDOWS

JVM_LEAF(struct hostent*, JVM_GetHostByAddr(const char* name, int len, int type))
  JVMWrapper("JVM_GetHostByAddr");
  return hpi::get_host_by_addr(name, len, type);
JVM_END


JVM_LEAF(struct hostent*, JVM_GetHostByName(char* name))
  JVMWrapper("JVM_GetHostByName");
  return hpi::get_host_by_name(name);
JVM_END


JVM_LEAF(struct protoent*, JVM_GetProtoByName(char* name))
  JVMWrapper("JVM_GetProtoByName");
  return hpi::get_proto_by_name(name);
JVM_END

#endif

// Library support ///////////////////////////////////////////////////////////////////////////

JVM_ENTRY_NO_ENV(void*, JVM_LoadLibrary(const char* name))
  //%note jvm_ct
  JVMWrapper2("JVM_LoadLibrary (%s)", name);
  char ebuf[1024];
  void *load_result;
  {
    ThreadToNativeFromVM ttnfvm(thread);
    load_result = hpi::dll_load(name, ebuf, sizeof ebuf);
  }
  if (load_result == NULL) {
    char msg[1024];
    jio_snprintf(msg, sizeof msg, "%s: %s", name, ebuf);
    // Since 'ebuf' may contain a string encoded using
    // platform encoding scheme, we need to pass
    // Exceptions::unsafe_to_utf8 to the new_exception method
    // as the last argument. See bug 6367357.
    Handle h_exception =
      Exceptions::new_exception(thread,
                                vmSymbols::java_lang_UnsatisfiedLinkError(),
                                msg, Exceptions::unsafe_to_utf8);

    THROW_HANDLE_0(h_exception);
  }
  return load_result;
JVM_END


JVM_LEAF(void, JVM_UnloadLibrary(void* handle))
  JVMWrapper("JVM_UnloadLibrary");
  hpi::dll_unload(handle);
JVM_END


JVM_LEAF(void*, JVM_FindLibraryEntry(void* handle, const char* name))
  JVMWrapper2("JVM_FindLibraryEntry (%s)", name);
  return hpi::dll_lookup(handle, name);
JVM_END

// Floating point support ////////////////////////////////////////////////////////////////////

JVM_LEAF(jboolean, JVM_IsNaN(jdouble a))
  JVMWrapper("JVM_IsNaN");
  return g_isnan(a);
JVM_END



// JNI version ///////////////////////////////////////////////////////////////////////////////

JVM_LEAF(jboolean, JVM_IsSupportedJNIVersion(jint version))
  JVMWrapper2("JVM_IsSupportedJNIVersion (%d)", version);
  return Threads::is_supported_jni_version_including_1_1(version);
JVM_END


// String support ///////////////////////////////////////////////////////////////////////////

JVM_ENTRY(jstring, JVM_InternString(JNIEnv *env, jstring str))
  JVMWrapper("JVM_InternString");
  JvmtiVMObjectAllocEventCollector oam;
  if (str == NULL) return NULL;
  oop string = JNIHandles::resolve_non_null(str);
  oop result = StringTable::intern(string, CHECK_NULL);
  return (jstring) JNIHandles::make_local(env, result);
JVM_END


// Raw monitor support //////////////////////////////////////////////////////////////////////

// The lock routine below calls lock_without_safepoint_check in order to get a raw lock
// without interfering with the safepoint mechanism. The routines are not JVM_LEAF because
// they might be called by non-java threads. The JVM_LEAF installs a NoHandleMark check
// that only works with java threads.


JNIEXPORT void* JNICALL JVM_RawMonitorCreate(void) {
  VM_Exit::block_if_vm_exited();
  JVMWrapper("JVM_RawMonitorCreate");
  return new Mutex(Mutex::native, "JVM_RawMonitorCreate");
}


JNIEXPORT void JNICALL  JVM_RawMonitorDestroy(void *mon) {
  VM_Exit::block_if_vm_exited();
  JVMWrapper("JVM_RawMonitorDestroy");
  delete ((Mutex*) mon);
}


JNIEXPORT jint JNICALL JVM_RawMonitorEnter(void *mon) {
  VM_Exit::block_if_vm_exited();
  JVMWrapper("JVM_RawMonitorEnter");
  ((Mutex*) mon)->jvm_raw_lock();
  return 0;
}


JNIEXPORT void JNICALL JVM_RawMonitorExit(void *mon) {
  VM_Exit::block_if_vm_exited();
  JVMWrapper("JVM_RawMonitorExit");
  ((Mutex*) mon)->jvm_raw_unlock();
}


// Support for Serialization

typedef jfloat  (JNICALL *IntBitsToFloatFn  )(JNIEnv* env, jclass cb, jint    value);
typedef jdouble (JNICALL *LongBitsToDoubleFn)(JNIEnv* env, jclass cb, jlong   value);
typedef jint    (JNICALL *FloatToIntBitsFn  )(JNIEnv* env, jclass cb, jfloat  value);
typedef jlong   (JNICALL *DoubleToLongBitsFn)(JNIEnv* env, jclass cb, jdouble value);

static IntBitsToFloatFn   int_bits_to_float_fn   = NULL;
static LongBitsToDoubleFn long_bits_to_double_fn = NULL;
static FloatToIntBitsFn   float_to_int_bits_fn   = NULL;
static DoubleToLongBitsFn double_to_long_bits_fn = NULL;


void initialize_converter_functions() {
  if (JDK_Version::is_gte_jdk14x_version()) {
    // These functions only exist for compatibility with 1.3.1 and earlier
    return;
  }

  // called from universe_post_init()
  assert(
    int_bits_to_float_fn   == NULL &&
    long_bits_to_double_fn == NULL &&
    float_to_int_bits_fn   == NULL &&
    double_to_long_bits_fn == NULL ,
    "initialization done twice"
  );
  // initialize
  int_bits_to_float_fn   = CAST_TO_FN_PTR(IntBitsToFloatFn  , NativeLookup::base_library_lookup("java/lang/Float" , "intBitsToFloat"  , "(I)F"));
  long_bits_to_double_fn = CAST_TO_FN_PTR(LongBitsToDoubleFn, NativeLookup::base_library_lookup("java/lang/Double", "longBitsToDouble", "(J)D"));
  float_to_int_bits_fn   = CAST_TO_FN_PTR(FloatToIntBitsFn  , NativeLookup::base_library_lookup("java/lang/Float" , "floatToIntBits"  , "(F)I"));
  double_to_long_bits_fn = CAST_TO_FN_PTR(DoubleToLongBitsFn, NativeLookup::base_library_lookup("java/lang/Double", "doubleToLongBits", "(D)J"));
  // verify
  assert(
    int_bits_to_float_fn   != NULL &&
    long_bits_to_double_fn != NULL &&
    float_to_int_bits_fn   != NULL &&
    double_to_long_bits_fn != NULL ,
    "initialization failed"
  );
}


// Serialization
JVM_ENTRY(void, JVM_SetPrimitiveFieldValues(JNIEnv *env, jclass cb, jobject obj,
                                            jlongArray fieldIDs, jcharArray typecodes, jbyteArray data))
  assert(!JDK_Version::is_gte_jdk14x_version(), "should only be used in 1.3.1 and earlier");

  typeArrayOop tcodes = typeArrayOop(JNIHandles::resolve(typecodes));
  typeArrayOop dbuf   = typeArrayOop(JNIHandles::resolve(data));
  typeArrayOop fids   = typeArrayOop(JNIHandles::resolve(fieldIDs));
  oop          o      = JNIHandles::resolve(obj);

  if (o == NULL || fids == NULL  || dbuf == NULL  || tcodes == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }

  jsize nfids = fids->length();
  if (nfids == 0) return;

  if (tcodes->length() < nfids) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }

  jsize off = 0;
  /* loop through fields, setting values */
  for (jsize i = 0; i < nfids; i++) {
    jfieldID fid = (jfieldID)(intptr_t) fids->long_at(i);
    int field_offset;
    if (fid != NULL) {
      // NULL is a legal value for fid, but retrieving the field offset
      // trigger assertion in that case
      field_offset = jfieldIDWorkaround::from_instance_jfieldID(o->klass(), fid);
    }

    switch (tcodes->char_at(i)) {
      case 'Z':
        if (fid != NULL) {
          jboolean val = (dbuf->byte_at(off) != 0) ? JNI_TRUE : JNI_FALSE;
          o->bool_field_put(field_offset, val);
        }
        off++;
        break;

      case 'B':
        if (fid != NULL) {
          o->byte_field_put(field_offset, dbuf->byte_at(off));
        }
        off++;
        break;

      case 'C':
        if (fid != NULL) {
          jchar val = ((dbuf->byte_at(off + 0) & 0xFF) << 8)
                    + ((dbuf->byte_at(off + 1) & 0xFF) << 0);
          o->char_field_put(field_offset, val);
        }
        off += 2;
        break;

      case 'S':
        if (fid != NULL) {
          jshort val = ((dbuf->byte_at(off + 0) & 0xFF) << 8)
                     + ((dbuf->byte_at(off + 1) & 0xFF) << 0);
          o->short_field_put(field_offset, val);
        }
        off += 2;
        break;

      case 'I':
        if (fid != NULL) {
          jint ival = ((dbuf->byte_at(off + 0) & 0xFF) << 24)
                    + ((dbuf->byte_at(off + 1) & 0xFF) << 16)
                    + ((dbuf->byte_at(off + 2) & 0xFF) << 8)
                    + ((dbuf->byte_at(off + 3) & 0xFF) << 0);
          o->int_field_put(field_offset, ival);
        }
        off += 4;
        break;

      case 'F':
        if (fid != NULL) {
          jint ival = ((dbuf->byte_at(off + 0) & 0xFF) << 24)
                    + ((dbuf->byte_at(off + 1) & 0xFF) << 16)
                    + ((dbuf->byte_at(off + 2) & 0xFF) << 8)
                    + ((dbuf->byte_at(off + 3) & 0xFF) << 0);
          jfloat fval = (*int_bits_to_float_fn)(env, NULL, ival);
          o->float_field_put(field_offset, fval);
        }
        off += 4;
        break;

      case 'J':
        if (fid != NULL) {
          jlong lval = (((jlong) dbuf->byte_at(off + 0) & 0xFF) << 56)
                     + (((jlong) dbuf->byte_at(off + 1) & 0xFF) << 48)
                     + (((jlong) dbuf->byte_at(off + 2) & 0xFF) << 40)
                     + (((jlong) dbuf->byte_at(off + 3) & 0xFF) << 32)
                     + (((jlong) dbuf->byte_at(off + 4) & 0xFF) << 24)
                     + (((jlong) dbuf->byte_at(off + 5) & 0xFF) << 16)
                     + (((jlong) dbuf->byte_at(off + 6) & 0xFF) << 8)
                     + (((jlong) dbuf->byte_at(off + 7) & 0xFF) << 0);
          o->long_field_put(field_offset, lval);
        }
        off += 8;
        break;

      case 'D':
        if (fid != NULL) {
          jlong lval = (((jlong) dbuf->byte_at(off + 0) & 0xFF) << 56)
                     + (((jlong) dbuf->byte_at(off + 1) & 0xFF) << 48)
                     + (((jlong) dbuf->byte_at(off + 2) & 0xFF) << 40)
                     + (((jlong) dbuf->byte_at(off + 3) & 0xFF) << 32)
                     + (((jlong) dbuf->byte_at(off + 4) & 0xFF) << 24)
                     + (((jlong) dbuf->byte_at(off + 5) & 0xFF) << 16)
                     + (((jlong) dbuf->byte_at(off + 6) & 0xFF) << 8)
                     + (((jlong) dbuf->byte_at(off + 7) & 0xFF) << 0);
          jdouble dval = (*long_bits_to_double_fn)(env, NULL, lval);
          o->double_field_put(field_offset, dval);
        }
        off += 8;
        break;

      default:
        // Illegal typecode
        THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "illegal typecode");
    }
  }
JVM_END


JVM_ENTRY(void, JVM_GetPrimitiveFieldValues(JNIEnv *env, jclass cb, jobject obj,
                            jlongArray fieldIDs, jcharArray typecodes, jbyteArray data))
  assert(!JDK_Version::is_gte_jdk14x_version(), "should only be used in 1.3.1 and earlier");

  typeArrayOop tcodes = typeArrayOop(JNIHandles::resolve(typecodes));
  typeArrayOop dbuf   = typeArrayOop(JNIHandles::resolve(data));
  typeArrayOop fids   = typeArrayOop(JNIHandles::resolve(fieldIDs));
  oop          o      = JNIHandles::resolve(obj);

  if (o == NULL || fids == NULL  || dbuf == NULL  || tcodes == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }

  jsize nfids = fids->length();
  if (nfids == 0) return;

  if (tcodes->length() < nfids) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }

  /* loop through fields, fetching values */
  jsize off = 0;
  for (jsize i = 0; i < nfids; i++) {
    jfieldID fid = (jfieldID)(intptr_t) fids->long_at(i);
    if (fid == NULL) {
      THROW(vmSymbols::java_lang_NullPointerException());
    }
    int field_offset = jfieldIDWorkaround::from_instance_jfieldID(o->klass(), fid);

     switch (tcodes->char_at(i)) {
       case 'Z':
         {
           jboolean val = o->bool_field(field_offset);
           dbuf->byte_at_put(off++, (val != 0) ? 1 : 0);
         }
         break;

       case 'B':
         dbuf->byte_at_put(off++, o->byte_field(field_offset));
         break;

       case 'C':
         {
           jchar val = o->char_field(field_offset);
           dbuf->byte_at_put(off++, (val >> 8) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 0) & 0xFF);
         }
         break;

       case 'S':
         {
           jshort val = o->short_field(field_offset);
           dbuf->byte_at_put(off++, (val >> 8) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 0) & 0xFF);
         }
         break;

       case 'I':
         {
           jint val = o->int_field(field_offset);
           dbuf->byte_at_put(off++, (val >> 24) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 16) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 8)  & 0xFF);
           dbuf->byte_at_put(off++, (val >> 0)  & 0xFF);
         }
         break;

       case 'F':
         {
           jfloat fval = o->float_field(field_offset);
           jint ival = (*float_to_int_bits_fn)(env, NULL, fval);
           dbuf->byte_at_put(off++, (ival >> 24) & 0xFF);
           dbuf->byte_at_put(off++, (ival >> 16) & 0xFF);
           dbuf->byte_at_put(off++, (ival >> 8)  & 0xFF);
           dbuf->byte_at_put(off++, (ival >> 0)  & 0xFF);
         }
         break;

       case 'J':
         {
           jlong val = o->long_field(field_offset);
           dbuf->byte_at_put(off++, (val >> 56) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 48) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 40) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 32) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 24) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 16) & 0xFF);
           dbuf->byte_at_put(off++, (val >> 8)  & 0xFF);
           dbuf->byte_at_put(off++, (val >> 0)  & 0xFF);
         }
         break;

       case 'D':
         {
           jdouble dval = o->double_field(field_offset);
           jlong lval = (*double_to_long_bits_fn)(env, NULL, dval);
           dbuf->byte_at_put(off++, (lval >> 56) & 0xFF);
           dbuf->byte_at_put(off++, (lval >> 48) & 0xFF);
           dbuf->byte_at_put(off++, (lval >> 40) & 0xFF);
           dbuf->byte_at_put(off++, (lval >> 32) & 0xFF);
           dbuf->byte_at_put(off++, (lval >> 24) & 0xFF);
           dbuf->byte_at_put(off++, (lval >> 16) & 0xFF);
           dbuf->byte_at_put(off++, (lval >> 8)  & 0xFF);
           dbuf->byte_at_put(off++, (lval >> 0)  & 0xFF);
         }
         break;

       default:
         // Illegal typecode
         THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "illegal typecode");
     }
  }
JVM_END


// Shared JNI/JVM entry points //////////////////////////////////////////////////////////////

jclass find_class_from_class_loader(JNIEnv* env, symbolHandle name, jboolean init, Handle loader, Handle protection_domain, jboolean throwError, TRAPS) {
  // Security Note:
  //   The Java level wrapper will perform the necessary security check allowing
  //   us to pass the NULL as the initiating class loader.
  klassOop klass = SystemDictionary::resolve_or_fail(name, loader, protection_domain, throwError != 0, CHECK_NULL);

  KlassHandle klass_handle(THREAD, klass);
  // Check if we should initialize the class
  if (init && klass_handle->oop_is_instance()) {
    klass_handle->initialize(CHECK_NULL);
  }
  return (jclass) JNIHandles::make_local(env, klass_handle->java_mirror());
}


// Internal SQE debugging support ///////////////////////////////////////////////////////////

#ifndef PRODUCT

extern "C" {
  JNIEXPORT jboolean JNICALL JVM_AccessVMBooleanFlag(const char* name, jboolean* value, jboolean is_get);
  JNIEXPORT jboolean JNICALL JVM_AccessVMIntFlag(const char* name, jint* value, jboolean is_get);
  JNIEXPORT void JNICALL JVM_VMBreakPoint(JNIEnv *env, jobject obj);
}

JVM_LEAF(jboolean, JVM_AccessVMBooleanFlag(const char* name, jboolean* value, jboolean is_get))
  JVMWrapper("JVM_AccessBoolVMFlag");
  return is_get ? CommandLineFlags::boolAt((char*) name, (bool*) value) : CommandLineFlags::boolAtPut((char*) name, (bool*) value, INTERNAL);
JVM_END

JVM_LEAF(jboolean, JVM_AccessVMIntFlag(const char* name, jint* value, jboolean is_get))
  JVMWrapper("JVM_AccessVMIntFlag");
  intx v;
  jboolean result = is_get ? CommandLineFlags::intxAt((char*) name, &v) : CommandLineFlags::intxAtPut((char*) name, &v, INTERNAL);
  *value = (jint)v;
  return result;
JVM_END


JVM_ENTRY(void, JVM_VMBreakPoint(JNIEnv *env, jobject obj))
  JVMWrapper("JVM_VMBreakPoint");
  oop the_obj = JNIHandles::resolve(obj);
  BREAKPOINT;
JVM_END


#endif


//---------------------------------------------------------------------------
//
// Support for old native code-based reflection (pre-JDK 1.4)
// Disabled by default in the product build.
//
// See reflection.hpp for information on SUPPORT_OLD_REFLECTION
//
//---------------------------------------------------------------------------

#ifdef SUPPORT_OLD_REFLECTION

JVM_ENTRY(jobjectArray, JVM_GetClassFields(JNIEnv *env, jclass cls, jint which))
  JVMWrapper("JVM_GetClassFields");
  JvmtiVMObjectAllocEventCollector oam;
  oop mirror = JNIHandles::resolve_non_null(cls);
  objArrayOop result = Reflection::reflect_fields(mirror, which, CHECK_NULL);
  return (jobjectArray) JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jobjectArray, JVM_GetClassMethods(JNIEnv *env, jclass cls, jint which))
  JVMWrapper("JVM_GetClassMethods");
  JvmtiVMObjectAllocEventCollector oam;
  oop mirror = JNIHandles::resolve_non_null(cls);
  objArrayOop result = Reflection::reflect_methods(mirror, which, CHECK_NULL);
  //%note jvm_r4
  return (jobjectArray) JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jobjectArray, JVM_GetClassConstructors(JNIEnv *env, jclass cls, jint which))
  JVMWrapper("JVM_GetClassConstructors");
  JvmtiVMObjectAllocEventCollector oam;
  oop mirror = JNIHandles::resolve_non_null(cls);
  objArrayOop result = Reflection::reflect_constructors(mirror, which, CHECK_NULL);
  //%note jvm_r4
  return (jobjectArray) JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jobject, JVM_GetClassField(JNIEnv *env, jclass cls, jstring name, jint which))
  JVMWrapper("JVM_GetClassField");
  JvmtiVMObjectAllocEventCollector oam;
  if (name == NULL) return NULL;
  Handle str (THREAD, JNIHandles::resolve_non_null(name));

  const char* cstr = java_lang_String::as_utf8_string(str());
  symbolHandle field_name =
           symbolHandle(THREAD, SymbolTable::probe(cstr, (int)strlen(cstr)));
  if (field_name.is_null()) {
    THROW_0(vmSymbols::java_lang_NoSuchFieldException());
  }

  oop mirror = JNIHandles::resolve_non_null(cls);
  oop result = Reflection::reflect_field(mirror, field_name(), which, CHECK_NULL);
  if (result == NULL) {
    THROW_0(vmSymbols::java_lang_NoSuchFieldException());
  }
  return JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jobject, JVM_GetClassMethod(JNIEnv *env, jclass cls, jstring name, jobjectArray types, jint which))
  JVMWrapper("JVM_GetClassMethod");
  JvmtiVMObjectAllocEventCollector oam;
  if (name == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  Handle str (THREAD, JNIHandles::resolve_non_null(name));

  const char* cstr = java_lang_String::as_utf8_string(str());
  symbolHandle method_name =
          symbolHandle(THREAD, SymbolTable::probe(cstr, (int)strlen(cstr)));
  if (method_name.is_null()) {
    THROW_0(vmSymbols::java_lang_NoSuchMethodException());
  }

  oop mirror = JNIHandles::resolve_non_null(cls);
  objArrayHandle tarray (THREAD, objArrayOop(JNIHandles::resolve(types)));
  oop result = Reflection::reflect_method(mirror, method_name, tarray,
                                          which, CHECK_NULL);
  if (result == NULL) {
    THROW_0(vmSymbols::java_lang_NoSuchMethodException());
  }
  return JNIHandles::make_local(env, result);
JVM_END


JVM_ENTRY(jobject, JVM_GetClassConstructor(JNIEnv *env, jclass cls, jobjectArray types, jint which))
  JVMWrapper("JVM_GetClassConstructor");
  JvmtiVMObjectAllocEventCollector oam;
  oop mirror = JNIHandles::resolve_non_null(cls);
  objArrayHandle tarray (THREAD, objArrayOop(JNIHandles::resolve(types)));
  oop result = Reflection::reflect_constructor(mirror, tarray, which, CHECK_NULL);
  if (result == NULL) {
    THROW_0(vmSymbols::java_lang_NoSuchMethodException());
  }
  return (jobject) JNIHandles::make_local(env, result);
JVM_END


// Instantiation ///////////////////////////////////////////////////////////////////////////////

JVM_ENTRY(jobject, JVM_NewInstance(JNIEnv *env, jclass cls))
  JVMWrapper("JVM_NewInstance");
  Handle mirror(THREAD, JNIHandles::resolve_non_null(cls));

  methodOop resolved_constructor = java_lang_Class::resolved_constructor(mirror());
  if (resolved_constructor == NULL) {
    klassOop k = java_lang_Class::as_klassOop(mirror());
    // The java.lang.Class object caches a resolved constructor if all the checks
    // below were done successfully and a constructor was found.

    // Do class based checks
    if (java_lang_Class::is_primitive(mirror())) {
      const char* msg = "";
      if      (mirror == Universe::bool_mirror())   msg = "java/lang/Boolean";
      else if (mirror == Universe::char_mirror())   msg = "java/lang/Character";
      else if (mirror == Universe::float_mirror())  msg = "java/lang/Float";
      else if (mirror == Universe::double_mirror()) msg = "java/lang/Double";
      else if (mirror == Universe::byte_mirror())   msg = "java/lang/Byte";
      else if (mirror == Universe::short_mirror())  msg = "java/lang/Short";
      else if (mirror == Universe::int_mirror())    msg = "java/lang/Integer";
      else if (mirror == Universe::long_mirror())   msg = "java/lang/Long";
      THROW_MSG_0(vmSymbols::java_lang_NullPointerException(), msg);
    }

    // Check whether we are allowed to instantiate this class
    Klass::cast(k)->check_valid_for_instantiation(false, CHECK_NULL); // Array classes get caught here
    instanceKlassHandle klass(THREAD, k);
    // Make sure class is initialized (also so all methods are rewritten)
    klass->initialize(CHECK_NULL);

    // Lookup default constructor
    resolved_constructor = klass->find_method(vmSymbols::object_initializer_name(), vmSymbols::void_method_signature());
    if (resolved_constructor == NULL) {
      ResourceMark rm(THREAD);
      THROW_MSG_0(vmSymbols::java_lang_InstantiationException(), klass->external_name());
    }

    // Cache result in java.lang.Class object. Does not have to be MT safe.
    java_lang_Class::set_resolved_constructor(mirror(), resolved_constructor);
  }

  assert(resolved_constructor != NULL, "sanity check");
  methodHandle constructor = methodHandle(THREAD, resolved_constructor);

  // We have an initialized instanceKlass with a default constructor
  instanceKlassHandle klass(THREAD, java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(cls)));
  assert(klass->is_initialized() || klass->is_being_initialized(), "sanity check");

  // Do security check
  klassOop caller_klass = NULL;
  if (UsePrivilegedStack) {
    caller_klass = thread->security_get_caller_class(2);

    if (!Reflection::verify_class_access(caller_klass, klass(), false) ||
        !Reflection::verify_field_access(caller_klass,
                                         klass(),
                                         klass(),
                                         constructor->access_flags(),
                                         false,
                                         true)) {
      ResourceMark rm(THREAD);
      THROW_MSG_0(vmSymbols::java_lang_IllegalAccessException(), klass->external_name());
    }
  }

  // Allocate object and call constructor
  Handle receiver = klass->allocate_instance_handle(CHECK_NULL);
  JavaCalls::call_default_constructor(thread, constructor, receiver, CHECK_NULL);

  jobject res = JNIHandles::make_local(env, receiver());
  if (JvmtiExport::should_post_vm_object_alloc()) {
    JvmtiExport::post_vm_object_alloc(JavaThread::current(), receiver());
  }
  return res;
JVM_END


// Field ////////////////////////////////////////////////////////////////////////////////////////////

JVM_ENTRY(jobject, JVM_GetField(JNIEnv *env, jobject field, jobject obj))
  JVMWrapper("JVM_GetField");
  JvmtiVMObjectAllocEventCollector oam;
  Handle field_mirror(thread, JNIHandles::resolve(field));
  Handle receiver    (thread, JNIHandles::resolve(obj));
  fieldDescriptor fd;
  Reflection::resolve_field(field_mirror, receiver, &fd, false, CHECK_NULL);
  jvalue value;
  BasicType type = Reflection::field_get(&value, &fd, receiver);
  oop box = Reflection::box(&value, type, CHECK_NULL);
  return JNIHandles::make_local(env, box);
JVM_END


JVM_ENTRY(jvalue, JVM_GetPrimitiveField(JNIEnv *env, jobject field, jobject obj, unsigned char wCode))
  JVMWrapper("JVM_GetPrimitiveField");
  Handle field_mirror(thread, JNIHandles::resolve(field));
  Handle receiver    (thread, JNIHandles::resolve(obj));
  fieldDescriptor fd;
  jvalue value;
  value.j = 0;
  Reflection::resolve_field(field_mirror, receiver, &fd, false, CHECK_(value));
  BasicType type = Reflection::field_get(&value, &fd, receiver);
  BasicType wide_type = (BasicType) wCode;
  if (type != wide_type) {
    Reflection::widen(&value, type, wide_type, CHECK_(value));
  }
  return value;
JVM_END // should really be JVM_END, but that doesn't work for union types!


JVM_ENTRY(void, JVM_SetField(JNIEnv *env, jobject field, jobject obj, jobject val))
  JVMWrapper("JVM_SetField");
  Handle field_mirror(thread, JNIHandles::resolve(field));
  Handle receiver    (thread, JNIHandles::resolve(obj));
  oop box = JNIHandles::resolve(val);
  fieldDescriptor fd;
  Reflection::resolve_field(field_mirror, receiver, &fd, true, CHECK);
  BasicType field_type = fd.field_type();
  jvalue value;
  BasicType value_type;
  if (field_type == T_OBJECT || field_type == T_ARRAY) {
    // Make sure we do no unbox e.g. java/lang/Integer instances when storing into an object array
    value_type = Reflection::unbox_for_regular_object(box, &value);
    Reflection::field_set(&value, &fd, receiver, field_type, CHECK);
  } else {
    value_type = Reflection::unbox_for_primitive(box, &value, CHECK);
    Reflection::field_set(&value, &fd, receiver, value_type, CHECK);
  }
JVM_END


JVM_ENTRY(void, JVM_SetPrimitiveField(JNIEnv *env, jobject field, jobject obj, jvalue v, unsigned char vCode))
  JVMWrapper("JVM_SetPrimitiveField");
  Handle field_mirror(thread, JNIHandles::resolve(field));
  Handle receiver    (thread, JNIHandles::resolve(obj));
  fieldDescriptor fd;
  Reflection::resolve_field(field_mirror, receiver, &fd, true, CHECK);
  BasicType value_type = (BasicType) vCode;
  Reflection::field_set(&v, &fd, receiver, value_type, CHECK);
JVM_END


// Method ///////////////////////////////////////////////////////////////////////////////////////////

JVM_ENTRY(jobject, JVM_InvokeMethod(JNIEnv *env, jobject method, jobject obj, jobjectArray args0))
  JVMWrapper("JVM_InvokeMethod");
  Handle method_handle;
  if (thread->stack_available((address) &method_handle) >= JVMInvokeMethodSlack) {
    method_handle = Handle(THREAD, JNIHandles::resolve(method));
    Handle receiver(THREAD, JNIHandles::resolve(obj));
    objArrayHandle args(THREAD, objArrayOop(JNIHandles::resolve(args0)));
    oop result = Reflection::invoke_method(method_handle(), receiver, args, CHECK_NULL);
    jobject res = JNIHandles::make_local(env, result);
    if (JvmtiExport::should_post_vm_object_alloc()) {
      oop ret_type = java_lang_reflect_Method::return_type(method_handle());
      assert(ret_type != NULL, "sanity check: ret_type oop must not be NULL!");
      if (java_lang_Class::is_primitive(ret_type)) {
        // Only for primitive type vm allocates memory for java object.
        // See box() method.
        JvmtiExport::post_vm_object_alloc(JavaThread::current(), result);
      }
    }
    return res;
  } else {
    THROW_0(vmSymbols::java_lang_StackOverflowError());
  }
JVM_END


JVM_ENTRY(jobject, JVM_NewInstanceFromConstructor(JNIEnv *env, jobject c, jobjectArray args0))
  JVMWrapper("JVM_NewInstanceFromConstructor");
  oop constructor_mirror = JNIHandles::resolve(c);
  objArrayHandle args(THREAD, objArrayOop(JNIHandles::resolve(args0)));
  oop result = Reflection::invoke_constructor(constructor_mirror, args, CHECK_NULL);
  jobject res = JNIHandles::make_local(env, result);
  if (JvmtiExport::should_post_vm_object_alloc()) {
    JvmtiExport::post_vm_object_alloc(JavaThread::current(), result);
  }
  return res;
JVM_END

#endif /* SUPPORT_OLD_REFLECTION */

// Atomic ///////////////////////////////////////////////////////////////////////////////////////////

JVM_LEAF(jboolean, JVM_SupportsCX8())
  JVMWrapper("JVM_SupportsCX8");
  return VM_Version::supports_cx8();
JVM_END


JVM_ENTRY(jboolean, JVM_CX8Field(JNIEnv *env, jobject obj, jfieldID fid, jlong oldVal, jlong newVal))
  JVMWrapper("JVM_CX8Field");
  jlong res;
  oop             o       = JNIHandles::resolve(obj);
  intptr_t        fldOffs = jfieldIDWorkaround::from_instance_jfieldID(o->klass(), fid);
  volatile jlong* addr    = (volatile jlong*)((address)o + fldOffs);

  assert(VM_Version::supports_cx8(), "cx8 not supported");
  res = Atomic::cmpxchg(newVal, addr, oldVal);

  return res == oldVal;
JVM_END

// DTrace ///////////////////////////////////////////////////////////////////

JVM_ENTRY(jint, JVM_DTraceGetVersion(JNIEnv* env))
  JVMWrapper("JVM_DTraceGetVersion");
  return (jint)JVM_TRACING_DTRACE_VERSION;
JVM_END

JVM_ENTRY(jlong,JVM_DTraceActivate(
    JNIEnv* env, jint version, jstring module_name, jint providers_count,
    JVM_DTraceProvider* providers))
  JVMWrapper("JVM_DTraceActivate");
  return DTraceJSDT::activate(
    version, module_name, providers_count, providers, CHECK_0);
JVM_END

JVM_ENTRY(jboolean,JVM_DTraceIsProbeEnabled(JNIEnv* env, jmethodID method))
  JVMWrapper("JVM_DTraceIsProbeEnabled");
  return DTraceJSDT::is_probe_enabled(method);
JVM_END

JVM_ENTRY(void,JVM_DTraceDispose(JNIEnv* env, jlong handle))
  JVMWrapper("JVM_DTraceDispose");
  DTraceJSDT::dispose(handle);
JVM_END

JVM_ENTRY(jboolean,JVM_DTraceIsSupported(JNIEnv* env))
  JVMWrapper("JVM_DTraceIsSupported");
  return DTraceJSDT::is_supported();
JVM_END

// Returns an array of all live Thread objects (VM internal JavaThreads,
// jvmti agent threads, and JNI attaching threads  are skipped)
// See CR 6404306 regarding JNI attaching threads
JVM_ENTRY(jobjectArray, JVM_GetAllThreads(JNIEnv *env, jclass dummy))
  ResourceMark rm(THREAD);
  ThreadsListEnumerator tle(THREAD, false, false);
  JvmtiVMObjectAllocEventCollector oam;

  int num_threads = tle.num_threads();
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::Thread_klass(), num_threads, CHECK_NULL);
  objArrayHandle threads_ah(THREAD, r);

  for (int i = 0; i < num_threads; i++) {
    Handle h = tle.get_threadObj(i);
    threads_ah->obj_at_put(i, h());
  }

  return (jobjectArray) JNIHandles::make_local(env, threads_ah());
JVM_END


// Support for java.lang.Thread.getStackTrace() and getAllStackTraces() methods
// Return StackTraceElement[][], each element is the stack trace of a thread in
// the corresponding entry in the given threads array
JVM_ENTRY(jobjectArray, JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads))
  JVMWrapper("JVM_DumpThreads");
  JvmtiVMObjectAllocEventCollector oam;

  // Check if threads is null
  if (threads == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), 0);
  }

  objArrayOop a = objArrayOop(JNIHandles::resolve_non_null(threads));
  objArrayHandle ah(THREAD, a);
  int num_threads = ah->length();
  // check if threads is non-empty array
  if (num_threads == 0) {
    THROW_(vmSymbols::java_lang_IllegalArgumentException(), 0);
  }

  // check if threads is not an array of objects of Thread class
  klassOop k = objArrayKlass::cast(ah->klass())->element_klass();
  if (k != SystemDictionary::Thread_klass()) {
    THROW_(vmSymbols::java_lang_IllegalArgumentException(), 0);
  }

  ResourceMark rm(THREAD);

  GrowableArray<instanceHandle>* thread_handle_array = new GrowableArray<instanceHandle>(num_threads);
  for (int i = 0; i < num_threads; i++) {
    oop thread_obj = ah->obj_at(i);
    instanceHandle h(THREAD, (instanceOop) thread_obj);
    thread_handle_array->append(h);
  }

  Handle stacktraces = ThreadService::dump_stack_traces(thread_handle_array, num_threads, CHECK_NULL);
  return (jobjectArray)JNIHandles::make_local(env, stacktraces());

JVM_END

// JVM monitoring and management support
JVM_ENTRY_NO_ENV(void*, JVM_GetManagement(jint version))
  return Management::get_jmm_interface(version);
JVM_END

// com.sun.tools.attach.VirtualMachine agent properties support
//
// Initialize the agent properties with the properties maintained in the VM
JVM_ENTRY(jobject, JVM_InitAgentProperties(JNIEnv *env, jobject properties))
  JVMWrapper("JVM_InitAgentProperties");
  ResourceMark rm;

  Handle props(THREAD, JNIHandles::resolve_non_null(properties));

  PUTPROP(props, "sun.java.command", Arguments::java_command());
  PUTPROP(props, "sun.jvm.flags", Arguments::jvm_flags());
  PUTPROP(props, "sun.jvm.args", Arguments::jvm_args());
  return properties;
JVM_END

JVM_ENTRY(jobjectArray, JVM_GetEnclosingMethodInfo(JNIEnv *env, jclass ofClass))
{
  JVMWrapper("JVM_GetEnclosingMethodInfo");
  JvmtiVMObjectAllocEventCollector oam;

  if (ofClass == NULL) {
    return NULL;
  }
  Handle mirror(THREAD, JNIHandles::resolve_non_null(ofClass));
  // Special handling for primitive objects
  if (java_lang_Class::is_primitive(mirror())) {
    return NULL;
  }
  klassOop k = java_lang_Class::as_klassOop(mirror());
  if (!Klass::cast(k)->oop_is_instance()) {
    return NULL;
  }
  instanceKlassHandle ik_h(THREAD, k);
  int encl_method_class_idx = ik_h->enclosing_method_class_index();
  if (encl_method_class_idx == 0) {
    return NULL;
  }
  objArrayOop dest_o = oopFactory::new_objArray(SystemDictionary::Object_klass(), 3, CHECK_NULL);
  objArrayHandle dest(THREAD, dest_o);
  klassOop enc_k = ik_h->constants()->klass_at(encl_method_class_idx, CHECK_NULL);
  dest->obj_at_put(0, Klass::cast(enc_k)->java_mirror());
  int encl_method_method_idx = ik_h->enclosing_method_method_index();
  if (encl_method_method_idx != 0) {
    symbolOop sym_o = ik_h->constants()->symbol_at(
                        extract_low_short_from_int(
                          ik_h->constants()->name_and_type_at(encl_method_method_idx)));
    symbolHandle sym(THREAD, sym_o);
    Handle str = java_lang_String::create_from_symbol(sym, CHECK_NULL);
    dest->obj_at_put(1, str());
    sym_o = ik_h->constants()->symbol_at(
              extract_high_short_from_int(
                ik_h->constants()->name_and_type_at(encl_method_method_idx)));
    sym = symbolHandle(THREAD, sym_o);
    str = java_lang_String::create_from_symbol(sym, CHECK_NULL);
    dest->obj_at_put(2, str());
  }
  return (jobjectArray) JNIHandles::make_local(dest());
}
JVM_END

JVM_ENTRY(jintArray, JVM_GetThreadStateValues(JNIEnv* env,
                                              jint javaThreadState))
{
  // If new thread states are added in future JDK and VM versions,
  // this should check if the JDK version is compatible with thread
  // states supported by the VM.  Return NULL if not compatible.
  //
  // This function must map the VM java_lang_Thread::ThreadStatus
  // to the Java thread state that the JDK supports.
  //

  typeArrayHandle values_h;
  switch (javaThreadState) {
    case JAVA_THREAD_STATE_NEW : {
      typeArrayOop r = oopFactory::new_typeArray(T_INT, 1, CHECK_NULL);
      values_h = typeArrayHandle(THREAD, r);
      values_h->int_at_put(0, java_lang_Thread::NEW);
      break;
    }
    case JAVA_THREAD_STATE_RUNNABLE : {
      typeArrayOop r = oopFactory::new_typeArray(T_INT, 1, CHECK_NULL);
      values_h = typeArrayHandle(THREAD, r);
      values_h->int_at_put(0, java_lang_Thread::RUNNABLE);
      break;
    }
    case JAVA_THREAD_STATE_BLOCKED : {
      typeArrayOop r = oopFactory::new_typeArray(T_INT, 1, CHECK_NULL);
      values_h = typeArrayHandle(THREAD, r);
      values_h->int_at_put(0, java_lang_Thread::BLOCKED_ON_MONITOR_ENTER);
      break;
    }
    case JAVA_THREAD_STATE_WAITING : {
      typeArrayOop r = oopFactory::new_typeArray(T_INT, 2, CHECK_NULL);
      values_h = typeArrayHandle(THREAD, r);
      values_h->int_at_put(0, java_lang_Thread::IN_OBJECT_WAIT);
      values_h->int_at_put(1, java_lang_Thread::PARKED);
      break;
    }
    case JAVA_THREAD_STATE_TIMED_WAITING : {
      typeArrayOop r = oopFactory::new_typeArray(T_INT, 3, CHECK_NULL);
      values_h = typeArrayHandle(THREAD, r);
      values_h->int_at_put(0, java_lang_Thread::SLEEPING);
      values_h->int_at_put(1, java_lang_Thread::IN_OBJECT_WAIT_TIMED);
      values_h->int_at_put(2, java_lang_Thread::PARKED_TIMED);
      break;
    }
    case JAVA_THREAD_STATE_TERMINATED : {
      typeArrayOop r = oopFactory::new_typeArray(T_INT, 1, CHECK_NULL);
      values_h = typeArrayHandle(THREAD, r);
      values_h->int_at_put(0, java_lang_Thread::TERMINATED);
      break;
    }
    default:
      // Unknown state - probably incompatible JDK version
      return NULL;
  }

  return (jintArray) JNIHandles::make_local(env, values_h());
}
JVM_END


JVM_ENTRY(jobjectArray, JVM_GetThreadStateNames(JNIEnv* env,
                                                jint javaThreadState,
                                                jintArray values))
{
  // If new thread states are added in future JDK and VM versions,
  // this should check if the JDK version is compatible with thread
  // states supported by the VM.  Return NULL if not compatible.
  //
  // This function must map the VM java_lang_Thread::ThreadStatus
  // to the Java thread state that the JDK supports.
  //

  ResourceMark rm;

  // Check if threads is null
  if (values == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), 0);
  }

  typeArrayOop v = typeArrayOop(JNIHandles::resolve_non_null(values));
  typeArrayHandle values_h(THREAD, v);

  objArrayHandle names_h;
  switch (javaThreadState) {
    case JAVA_THREAD_STATE_NEW : {
      assert(values_h->length() == 1 &&
               values_h->int_at(0) == java_lang_Thread::NEW,
             "Invalid threadStatus value");

      objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                               1, /* only 1 substate */
                                               CHECK_NULL);
      names_h = objArrayHandle(THREAD, r);
      Handle name = java_lang_String::create_from_str("NEW", CHECK_NULL);
      names_h->obj_at_put(0, name());
      break;
    }
    case JAVA_THREAD_STATE_RUNNABLE : {
      assert(values_h->length() == 1 &&
               values_h->int_at(0) == java_lang_Thread::RUNNABLE,
             "Invalid threadStatus value");

      objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                               1, /* only 1 substate */
                                               CHECK_NULL);
      names_h = objArrayHandle(THREAD, r);
      Handle name = java_lang_String::create_from_str("RUNNABLE", CHECK_NULL);
      names_h->obj_at_put(0, name());
      break;
    }
    case JAVA_THREAD_STATE_BLOCKED : {
      assert(values_h->length() == 1 &&
               values_h->int_at(0) == java_lang_Thread::BLOCKED_ON_MONITOR_ENTER,
             "Invalid threadStatus value");

      objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                               1, /* only 1 substate */
                                               CHECK_NULL);
      names_h = objArrayHandle(THREAD, r);
      Handle name = java_lang_String::create_from_str("BLOCKED", CHECK_NULL);
      names_h->obj_at_put(0, name());
      break;
    }
    case JAVA_THREAD_STATE_WAITING : {
      assert(values_h->length() == 2 &&
               values_h->int_at(0) == java_lang_Thread::IN_OBJECT_WAIT &&
               values_h->int_at(1) == java_lang_Thread::PARKED,
             "Invalid threadStatus value");
      objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                               2, /* number of substates */
                                               CHECK_NULL);
      names_h = objArrayHandle(THREAD, r);
      Handle name0 = java_lang_String::create_from_str("WAITING.OBJECT_WAIT",
                                                       CHECK_NULL);
      Handle name1 = java_lang_String::create_from_str("WAITING.PARKED",
                                                       CHECK_NULL);
      names_h->obj_at_put(0, name0());
      names_h->obj_at_put(1, name1());
      break;
    }
    case JAVA_THREAD_STATE_TIMED_WAITING : {
      assert(values_h->length() == 3 &&
               values_h->int_at(0) == java_lang_Thread::SLEEPING &&
               values_h->int_at(1) == java_lang_Thread::IN_OBJECT_WAIT_TIMED &&
               values_h->int_at(2) == java_lang_Thread::PARKED_TIMED,
             "Invalid threadStatus value");
      objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                               3, /* number of substates */
                                               CHECK_NULL);
      names_h = objArrayHandle(THREAD, r);
      Handle name0 = java_lang_String::create_from_str("TIMED_WAITING.SLEEPING",
                                                       CHECK_NULL);
      Handle name1 = java_lang_String::create_from_str("TIMED_WAITING.OBJECT_WAIT",
                                                       CHECK_NULL);
      Handle name2 = java_lang_String::create_from_str("TIMED_WAITING.PARKED",
                                                       CHECK_NULL);
      names_h->obj_at_put(0, name0());
      names_h->obj_at_put(1, name1());
      names_h->obj_at_put(2, name2());
      break;
    }
    case JAVA_THREAD_STATE_TERMINATED : {
      assert(values_h->length() == 1 &&
               values_h->int_at(0) == java_lang_Thread::TERMINATED,
             "Invalid threadStatus value");
      objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                               1, /* only 1 substate */
                                               CHECK_NULL);
      names_h = objArrayHandle(THREAD, r);
      Handle name = java_lang_String::create_from_str("TERMINATED", CHECK_NULL);
      names_h->obj_at_put(0, name());
      break;
    }
    default:
      // Unknown state - probably incompatible JDK version
      return NULL;
  }
  return (jobjectArray) JNIHandles::make_local(env, names_h());
}
JVM_END

JVM_ENTRY(void, JVM_GetVersionInfo(JNIEnv* env, jvm_version_info* info, size_t info_size))
{
  memset(info, 0, sizeof(info_size));

  info->jvm_version = Abstract_VM_Version::jvm_version();
  info->update_version = 0;          /* 0 in HotSpot Express VM */
  info->special_update_version = 0;  /* 0 in HotSpot Express VM */

  // when we add a new capability in the jvm_version_info struct, we should also
  // consider to expose this new capability in the sun.rt.jvmCapabilities jvmstat
  // counter defined in runtimeService.cpp.
  info->is_attachable = AttachListener::is_attach_supported();
#ifdef KERNEL
  info->is_kernel_jvm = 1; // true;
#else  // KERNEL
  info->is_kernel_jvm = 0; // false;
#endif // KERNEL
}
JVM_END
