/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "jvm.h"
#include "classfile/vmSymbols.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/deoptimization.hpp"
#include "prims/stackwalk.hpp"


class CloseScopedMemoryFindOopClosure : public OopClosure {
  oop _deopt;
  bool _found;

public:
  CloseScopedMemoryFindOopClosure(jobject deopt) :
      _deopt(JNIHandles::resolve(deopt)),
      _found(false) {}

  template <typename T>
  void do_oop_work(T* p) {
    if (_found) {
      return;
    }
    if (RawAccess<>::oop_load(p) == _deopt) {
      _found = true;
    }
  }

  virtual void do_oop(oop* p) {
    do_oop_work(p);
  }

  virtual void do_oop(narrowOop* p) {
    do_oop_work(p);
  }

  bool found() {
    return _found;
  }
};

class CloseScopedMemoryClosure : public HandshakeClosure {
  jobject _deopt;

public:
  jboolean _found;

  CloseScopedMemoryClosure(jobject deopt, jobject exception)
    : HandshakeClosure("CloseScopedMemory")
    , _deopt(deopt)
    , _found(false) {}

  void do_thread(Thread* thread) {

    JavaThread* jt = (JavaThread*)thread;

    if (!jt->has_last_Java_frame()) {
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
    if (_deopt != NULL && last_frame.is_compiled_frame() && last_frame.can_be_deoptimized()) {
      CloseScopedMemoryFindOopClosure cl(_deopt);
      CompiledMethod* cm = last_frame.cb()->as_compiled_method();

      /* FIXME: this doesn't work if reachability fences are violated by C2
      last_frame.oops_do(&cl, NULL, &register_map);
      if (cl.found()) {
           //Found the deopt oop in a compiled method; deoptimize.
           Deoptimization::deoptimize(jt, last_frame);
      }
      so... we unconditionally deoptimize, for now: */
      Deoptimization::deoptimize(jt, last_frame);
    }

    const int max_critical_stack_depth = 10;
    int depth = 0;
    for (vframeStream stream(jt); !stream.at_end(); stream.next()) {
      Method* m = stream.method();
      if (m->is_scoped()) {
        StackValueCollection* locals = stream.asJavaVFrame()->locals();
        for (int i = 0; i < locals->size(); i++) {
          StackValue* var = locals->at(i);
          if (var->type() == T_OBJECT) {
            if (var->get_obj() == JNIHandles::resolve(_deopt)) {
              assert(depth < max_critical_stack_depth, "can't have more than %d critical frames", max_critical_stack_depth);
              _found = true;
              return;
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
  }
};

/*
 * This function performs a thread-local handshake against all threads running at the time
 * the given session (deopt) was closed. If the handshake for a given thread is processed while
 * one or more threads is found inside a scoped method (that is, a method inside the ScopedMemoryAccess
 * class annotated with the '@Scoped' annotation), and whose local variables mention the session being
 * closed (deopt), this method returns false, signalling that the session cannot be closed safely.
 */
JVM_ENTRY(jboolean, ScopedMemoryAccess_closeScope(JNIEnv *env, jobject receiver, jobject deopt, jobject exception))
  CloseScopedMemoryClosure cl(deopt, exception);
  Handshake::execute(&cl);
  return !cl._found;
JVM_END

/// JVM_RegisterUnsafeMethods

#define PKG_MISC "Ljdk/internal/misc/"
#define PKG_FOREIGN "Ljdk/internal/foreign/"

#define MEMACCESS "ScopedMemoryAccess"
#define SCOPE PKG_FOREIGN "MemorySessionImpl;"

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod jdk_internal_misc_ScopedMemoryAccess_methods[] = {
    {CC "closeScope0",   CC "(" SCOPE ")Z",           FN_PTR(ScopedMemoryAccess_closeScope)},
};

#undef CC
#undef FN_PTR

#undef PKG_MISC
#undef PKG_FOREIGN
#undef MEMACCESS
#undef SCOPE

// This function is exported, used by NativeLookup.

JVM_ENTRY(void, JVM_RegisterJDKInternalMiscScopedMemoryAccessMethods(JNIEnv *env, jclass scopedMemoryAccessClass))
  ThreadToNativeFromVM ttnfv(thread);

  int ok = env->RegisterNatives(scopedMemoryAccessClass, jdk_internal_misc_ScopedMemoryAccess_methods, sizeof(jdk_internal_misc_ScopedMemoryAccess_methods)/sizeof(JNINativeMethod));
  guarantee(ok == 0, "register jdk.internal.misc.ScopedMemoryAccess natives");
JVM_END
