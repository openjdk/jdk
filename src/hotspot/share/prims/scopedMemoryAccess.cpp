/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/stackwalk.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vframe.inline.hpp"

static bool is_in_scoped_access(JavaThread* jt, oop session) {
  const int max_critical_stack_depth = 10;
  int depth = 0;
  for (vframeStream stream(jt); !stream.at_end(); stream.next()) {
    Method* m = stream.method();
    if (m->is_scoped()) {
      StackValueCollection* locals = stream.asJavaVFrame()->locals();
      for (int i = 0; i < locals->size(); i++) {
        StackValue* var = locals->at(i);
        if (var->type() == T_OBJECT) {
          if (var->get_obj() == session) {
            assert(depth < max_critical_stack_depth, "can't have more than %d critical frames", max_critical_stack_depth);
            return true;
          }
        }
      }
      break;
    }
    depth++;
#ifndef ASSERT
    if (depth >= max_critical_stack_depth) {
      break;
    }
#endif
  }

  return false;
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
    ResourceMark rm;
    if (is_in_scoped_access(jt, _session.resolve())) {
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

    frame last_frame = jt->last_frame();
    RegisterMap register_map(jt,
                             RegisterMap::UpdateMap::include,
                             RegisterMap::ProcessFrames::include,
                             RegisterMap::WalkContinuation::skip);

    if (last_frame.is_safepoint_blob_frame()) {
      last_frame = last_frame.sender(&register_map);
    }

    ResourceMark rm;
    if (last_frame.is_compiled_frame() && last_frame.can_be_deoptimized()) {
      // FIXME: we would like to conditionally deoptimize only if the corresponding
      // _session is reachable from the frame, but reachabilityFence doesn't currently
      // work the way it should. Therefore we deopt unconditionally for now.
      Deoptimization::deoptimize(jt, last_frame);
    }

    if (jt->has_async_exception_condition()) {
      // Target thread just about to throw an async exception using async handshakes,
      // we will then unwind out from the scoped memory access.
      return;
    }

    if (is_in_scoped_access(jt, JNIHandles::resolve(_session))) {
      // We have found that the target thread is inside of a scoped access.
      // An asynchronous handshake is sent to the target thread, telling it
      // to throw an exception, which will unwind the target thread out from
      // the scoped access.
      OopHandle session(Universe::vm_global(), JNIHandles::resolve(_session));
      OopHandle error(Universe::vm_global(), JNIHandles::resolve(_error));
      jt->install_async_exception(new ScopedAsyncExceptionHandshake(session, error));
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
