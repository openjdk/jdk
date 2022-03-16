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

/*
 * To store problematic threads during an handshake, we need an atomic data structure.
 * This is because the handshake closure can run concurrently either on the thread that
 * is the target of the handshake operation, or on the thread that is performing the
 * handshake (e.g. if the target thread is blocked, or in native state).
 */
class LockFreeStackThreadsElement : public CHeapObj<mtInternal> {
  typedef LockFreeStackThreadsElement Element;

  Element* volatile _next;
  static Element* volatile* next_ptr(Element& e) { return &e._next; }

public:
  JavaThread* _thread;
  LockFreeStackThreadsElement(JavaThread* thread) : _next(nullptr), _thread(thread) {}
  typedef LockFreeStack<Element, &next_ptr> ThreadStack;
};

typedef LockFreeStackThreadsElement::ThreadStack ThreadStack;
typedef LockFreeStackThreadsElement ThreadStackElement;

class CloseScopedMemoryClosure : public HandshakeClosure {
  jobject _deopt;
  ThreadStack *_threads;

public:
  CloseScopedMemoryClosure(jobject deopt, ThreadStack *threads)
    : HandshakeClosure("CloseScopedMemory")
    , _deopt(deopt)
    , _threads(threads) {}

  void do_thread(Thread* thread) {

    JavaThread* jt = (JavaThread*)thread;

    if (!jt->has_last_Java_frame()) {
      return;
    }

    frame last_frame = jt->last_frame();
    RegisterMap register_map(jt, true);

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
              ThreadStackElement *element = new ThreadStackElement(jt);
              _threads->push(*element);
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
 * the given scope (deopt) was closed. If the hanshake for a given thread is processed while
 * the thread is inside a scoped method (that is, a method inside the ScopedMemoryAccess
 * class annotated with the '@Scoped' annotation), whose local variables mention the scope being
 * closed (deopt), the thread is added to a problematic list. After the handshake, each thread in
 * the problematic list is handshaked again, individually, to check that it has exited
 * the scoped method. This should happen quickly, because once we find a problematic
 * thread, we also deoptimize it, meaning that when the thread resumes execution, the thread
 * should also see the updated scope state (and fail on access). This function returns when
 * the list of problematic threads is empty. To prevent premature thread termination we take
 * a snapshot of the live threads in the system using a ThreadsListHandle.
 */
JVM_ENTRY(void, ScopedMemoryAccess_closeScope(JNIEnv *env, jobject receiver, jobject deopt))
  ThreadStack threads;
  CloseScopedMemoryClosure cl(deopt, &threads);
  // do a first handshake and collect all problematic threads
  Handshake::execute(&cl);
  if (threads.empty()) {
    // fast-path: return if no problematic thread is found
    return;
  }
  // Now iterate over all problematic threads, until we converge. Note: from this point on,
  // we only need to focus on the problematic threads found in the previous step, as
  // any new thread created after the initial handshake will see the scope as CLOSED,
  // and will fail to access memory anyway.
  ThreadsListHandle tlh;
  ThreadStackElement *element = threads.pop();
  while (element != NULL) {
    JavaThread* thread = element->_thread;
    // If the thread is not in the list handle, it means that the thread has died,
    // so that we can safely skip further handshakes.
    if (tlh.list()->includes(thread)) {
      Handshake::execute(&cl, thread);
    }
    delete element;
    element = threads.pop();
  }
JVM_END

/// JVM_RegisterUnsafeMethods

#define PKG_MISC "Ljdk/internal/misc/"
#define PKG_FOREIGN "Ljdk/internal/foreign/"

#define MEMACCESS "ScopedMemoryAccess"
#define SCOPE PKG_FOREIGN "MemorySessionImpl;"

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod jdk_internal_misc_ScopedMemoryAccess_methods[] = {
    {CC "closeScope0",   CC "(" SCOPE ")V",           FN_PTR(ScopedMemoryAccess_closeScope)},
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
