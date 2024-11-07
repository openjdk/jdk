/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmSymbols.hpp"
#include "jni.h"
#include "jvm.h"
#include "logging/logStream.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/stackwalk.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vframe.inline.hpp"

template<typename Func>
static bool for_scoped_method(JavaThread* jt, const Func& func) {
  ResourceMark rm;
#ifdef ASSERT
  LogMessage(foreign) msg;
  NonInterleavingLogStream ls{LogLevelType::Trace, msg};
  if (ls.is_enabled()) {
    ls.print_cr("Walking thread: %s", jt->name());
  }
#endif

  const int max_critical_stack_depth = 10;
  int depth = 0;
  for (vframeStream stream(jt); !stream.at_end(); stream.next()) {
    Method* m = stream.method();
    bool is_scoped = m->is_scoped();

#ifdef ASSERT
    if (ls.is_enabled()) {
      stream.asJavaVFrame()->print_value(&ls);
      ls.print_cr("    is_scoped=%s", is_scoped ? "true" : "false");
    }
#endif

    if (is_scoped) {
      assert(depth < max_critical_stack_depth, "can't have more than %d critical frames", max_critical_stack_depth);
      return func(stream);
    }
    depth++;

#ifndef ASSERT
    // On debug builds, just keep searching the stack
    // in case we missed an @Scoped method further up
    if (depth >= max_critical_stack_depth) {
      break;
    }
#endif
  }
  return false;
}

static bool is_accessing_session(JavaThread* jt, oop session, bool& in_scoped) {
  return for_scoped_method(jt, [&](vframeStream& stream){
    in_scoped = true;
    StackValueCollection* locals = stream.asJavaVFrame()->locals();
    for (int i = 0; i < locals->size(); i++) {
      StackValue* var = locals->at(i);
      if (var->type() == T_OBJECT) {
        if (var->get_obj() == session) {
          return true;
        }
      }
    }
    return false;
  });
}

static frame get_last_frame(JavaThread* jt) {
  frame last_frame = jt->last_frame();
  RegisterMap register_map(jt,
                            RegisterMap::UpdateMap::include,
                            RegisterMap::ProcessFrames::include,
                            RegisterMap::WalkContinuation::skip);

  if (last_frame.is_safepoint_blob_frame()) {
    last_frame = last_frame.sender(&register_map);
  }
  return last_frame;
}

class ScopedAsyncExceptionHandshake : public AsyncExceptionHandshake {
  OopHandle _session;

public:
  ScopedAsyncExceptionHandshake(OopHandle& session, OopHandle& error)
    : AsyncExceptionHandshake(error),
      _session(session) {}

  ~ScopedAsyncExceptionHandshake() {
    _session.release(Universe::vm_global());
  }

  virtual void do_thread(Thread* thread) {
    JavaThread* jt = JavaThread::cast(thread);
    bool ignored;
    if (is_accessing_session(jt, _session.resolve(), ignored)) {
      // Throw exception to unwind out from the scoped access
      AsyncExceptionHandshake::do_thread(thread);
    }
  }
};

class CloseScopedMemoryClosure : public HandshakeClosure {
  jobject _session;
  jobject _error;

public:
  CloseScopedMemoryClosure(jobject session, jobject error)
    : HandshakeClosure("CloseScopedMemory")
    , _session(session)
    , _error(error) {}

  void do_thread(Thread* thread) {
    JavaThread* jt = JavaThread::cast(thread);

    if (!jt->has_last_Java_frame()) {
      // No frames; not in a scoped memory access
      return;
    }

    if (jt->has_async_exception_condition()) {
      // Target thread just about to throw an async exception using async handshakes,
      // we will then unwind out from the scoped memory access.
      return;
    }

    bool in_scoped = false;
    if (is_accessing_session(jt, JNIHandles::resolve(_session), in_scoped)) {
      // We have found that the target thread is inside of a scoped access.
      // An asynchronous handshake is sent to the target thread, telling it
      // to throw an exception, which will unwind the target thread out from
      // the scoped access.
      OopHandle session(Universe::vm_global(), JNIHandles::resolve(_session));
      OopHandle error(Universe::vm_global(), JNIHandles::resolve(_error));
      jt->install_async_exception(new ScopedAsyncExceptionHandshake(session, error));
    } else if (!in_scoped) {
      frame last_frame = get_last_frame(jt);
      if (last_frame.is_compiled_frame() && last_frame.can_be_deoptimized()) {
        // We are not at a safepoint that is 'in' an @Scoped method, but due to the compiler
        // moving code around/hoisting checks, we may be in a situation like this:
        //
        // liveness check (from @Scoped method)
        // for (...) {
        //    for (...) { // strip-mining inner loop
        //        memory access (from @Scoped method)
        //    }
        //    safepoint <-- STOPPED HERE
        // }
        //
        // The safepoint at which we're stopped may be in between the liveness check
        // and actual memory access, but is itself 'outside' of @Scoped code
        //
        // However, we're not sure whether we are in this exact situation, and
        // we're also not sure whether a memory access will actually occur after
        // this safepoint. So, we can not just install an async exception here
        //
        // Instead, we mark the frame for deoptimization (which happens just before
        // execution in this frame continues) to get back to code like this:
        //
        // for (...) {
        //     call to ScopedMemoryAccess
        //     safepoint <-- STOPPED HERE
        // }
        //
        // This means that we will re-do the liveness check before attempting
        // another memory access. If the scope has been closed at that point,
        // the target thread will see it and throw an exception.

        nmethod* code = last_frame.cb()->as_nmethod();
        if (code->has_scoped_access()) {
          // We would like to deoptimize here only if last_frame::oops_do
          // reports the session oop being live at this safepoint, but this
          // currently isn't possible due to JDK-8290892
          Deoptimization::deoptimize(jt, last_frame);
        }
      }
    }
  }
};

/*
 * This function performs a thread-local handshake against all threads running at the time
 * the given session (deopt) was closed. If the handshake for a given thread is processed while
 * one or more threads is found inside a scoped method (that is, a method inside the ScopedMemoryAccess
 * class annotated with the '@Scoped' annotation), and whose local variables mention the session being
 * closed (deopt), this method returns false, signalling that the session cannot be closed safely.
 */
JVM_ENTRY(void, ScopedMemoryAccess_closeScope(JNIEnv *env, jobject receiver, jobject session, jobject error))
  CloseScopedMemoryClosure cl(session, error);
  Handshake::execute(&cl);
JVM_END

/// JVM_RegisterUnsafeMethods

#define PKG_MISC "Ljdk/internal/misc/"
#define PKG_FOREIGN "Ljdk/internal/foreign/"

#define SCOPED_SESSION PKG_FOREIGN "MemorySessionImpl;"
#define SCOPED_ERROR PKG_MISC "ScopedMemoryAccess$ScopedAccessError;"

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod jdk_internal_misc_ScopedMemoryAccess_methods[] = {
  {CC "closeScope0", CC "(" SCOPED_SESSION SCOPED_ERROR ")V", FN_PTR(ScopedMemoryAccess_closeScope)},
};

#undef CC
#undef FN_PTR

#undef PKG_MISC
#undef PKG_FOREIGN
#undef SCOPED_SESSION
#undef SCOPED_ERROR

// This function is exported, used by NativeLookup.

JVM_ENTRY(void, JVM_RegisterJDKInternalMiscScopedMemoryAccessMethods(JNIEnv *env, jclass scopedMemoryAccessClass))
  ThreadToNativeFromVM ttnfv(thread);

  int ok = env->RegisterNatives(scopedMemoryAccessClass, jdk_internal_misc_ScopedMemoryAccess_methods, sizeof(jdk_internal_misc_ScopedMemoryAccess_methods)/sizeof(JNINativeMethod));
  guarantee(ok == 0, "register jdk.internal.misc.ScopedMemoryAccess natives");
JVM_END
