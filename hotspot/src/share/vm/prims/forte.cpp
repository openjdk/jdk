/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "code/debugInfoRec.hpp"
#include "code/pcDesc.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/space.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.inline2.hpp"
#include "prims/forte.hpp"
#include "runtime/thread.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vframeArray.hpp"

// These name match the names reported by the forte quality kit
enum {
  ticks_no_Java_frame         =  0,
  ticks_no_class_load         = -1,
  ticks_GC_active             = -2,
  ticks_unknown_not_Java      = -3,
  ticks_not_walkable_not_Java = -4,
  ticks_unknown_Java          = -5,
  ticks_not_walkable_Java     = -6,
  ticks_unknown_state         = -7,
  ticks_thread_exit           = -8,
  ticks_deopt                 = -9,
  ticks_safepoint             = -10
};

//-------------------------------------------------------

// Native interfaces for use by Forte tools.


#ifndef IA64

class vframeStreamForte : public vframeStreamCommon {
 public:
  // constructor that starts with sender of frame fr (top_frame)
  vframeStreamForte(JavaThread *jt, frame fr, bool stop_at_java_call_stub);
  void forte_next();
};


static bool is_decipherable_compiled_frame(JavaThread* thread, frame* fr, nmethod* nm);
static bool is_decipherable_interpreted_frame(JavaThread* thread,
                                              frame* fr,
                                              methodOop* method_p,
                                              int* bci_p);




vframeStreamForte::vframeStreamForte(JavaThread *jt,
                                     frame fr,
                                     bool stop_at_java_call_stub) : vframeStreamCommon(jt) {

  _stop_at_java_call_stub = stop_at_java_call_stub;
  _frame = fr;

  // We must always have a valid frame to start filling

  bool filled_in = fill_from_frame();

  assert(filled_in, "invariant");

}


// Solaris SPARC Compiler1 needs an additional check on the grandparent
// of the top_frame when the parent of the top_frame is interpreted and
// the grandparent is compiled. However, in this method we do not know
// the relationship of the current _frame relative to the top_frame so
// we implement a more broad sanity check. When the previous callee is
// interpreted and the current sender is compiled, we verify that the
// current sender is also walkable. If it is not walkable, then we mark
// the current vframeStream as at the end.
void vframeStreamForte::forte_next() {
  // handle frames with inlining
  if (_mode == compiled_mode &&
      vframeStreamCommon::fill_in_compiled_inlined_sender()) {
    return;
  }

  // handle general case

  int loop_count = 0;
  int loop_max = MaxJavaStackTraceDepth * 2;


  do {

    loop_count++;

    // By the time we get here we should never see unsafe but better
    // safe then segv'd

    if (loop_count > loop_max || !_frame.safe_for_sender(_thread)) {
      _mode = at_end_mode;
      return;
    }

    _frame = _frame.sender(&_reg_map);

  } while (!fill_from_frame());
}

// Determine if 'fr' is a decipherable compiled frame. We are already
// assured that fr is for a java nmethod.

static bool is_decipherable_compiled_frame(JavaThread* thread, frame* fr, nmethod* nm) {
  assert(nm->is_java_method(), "invariant");

  if (thread->has_last_Java_frame() && thread->last_Java_pc() == fr->pc()) {
    // We're stopped at a call into the JVM so look for a PcDesc with
    // the actual pc reported by the frame.
    PcDesc* pc_desc = nm->pc_desc_at(fr->pc());

    // Did we find a useful PcDesc?
    if (pc_desc != NULL &&
        pc_desc->scope_decode_offset() != DebugInformationRecorder::serialized_null) {
      return true;
    }
  }

  // We're at some random pc in the nmethod so search for the PcDesc
  // whose pc is greater than the current PC.  It's done this way
  // because the extra PcDescs that are recorded for improved debug
  // info record the end of the region covered by the ScopeDesc
  // instead of the beginning.
  PcDesc* pc_desc = nm->pc_desc_near(fr->pc() + 1);

  // Now do we have a useful PcDesc?
  if (pc_desc == NULL ||
      pc_desc->scope_decode_offset() == DebugInformationRecorder::serialized_null) {
    // No debug information available for this pc
    // vframeStream would explode if we try and walk the frames.
    return false;
  }

  // This PcDesc is useful however we must adjust the frame's pc
  // so that the vframeStream lookups will use this same pc
  fr->set_pc(pc_desc->real_pc(nm));
  return true;
}


// Determine if 'fr' is a walkable interpreted frame. Returns false
// if it is not. *method_p, and *bci_p are not set when false is
// returned. *method_p is non-NULL if frame was executing a Java
// method. *bci_p is != -1 if a valid BCI in the Java method could
// be found.
// Note: this method returns true when a valid Java method is found
// even if a valid BCI cannot be found.

static bool is_decipherable_interpreted_frame(JavaThread* thread,
                                              frame* fr,
                                              methodOop* method_p,
                                              int* bci_p) {
  assert(fr->is_interpreted_frame(), "just checking");

  // top frame is an interpreted frame
  // check if it is walkable (i.e. valid methodOop and valid bci)

  // Because we may be racing a gc thread the method and/or bci
  // of a valid interpreter frame may look bad causing us to
  // fail the is_interpreted_frame_valid test. If the thread
  // is in any of the following states we are assured that the
  // frame is in fact valid and we must have hit the race.

  JavaThreadState state = thread->thread_state();
  bool known_valid = (state == _thread_in_native ||
                      state == _thread_in_vm ||
                      state == _thread_blocked );

  if (known_valid || fr->is_interpreted_frame_valid(thread)) {

    // The frame code should completely validate the frame so that
    // references to methodOop and bci are completely safe to access
    // If they aren't the frame code should be fixed not this
    // code. However since gc isn't locked out the values could be
    // stale. This is a race we can never completely win since we can't
    // lock out gc so do one last check after retrieving their values
    // from the frame for additional safety

    methodOop method = fr->interpreter_frame_method();

    // We've at least found a method.
    // NOTE: there is something to be said for the approach that
    // if we don't find a valid bci then the method is not likely
    // a valid method. Then again we may have caught an interpreter
    // frame in the middle of construction and the bci field is
    // not yet valid.

    *method_p = method;

    // See if gc may have invalidated method since we validated frame

    if (!Universe::heap()->is_valid_method(method)) return false;

    intptr_t bcx = fr->interpreter_frame_bcx();

    int      bci = method->validate_bci_from_bcx(bcx);

    // note: bci is set to -1 if not a valid bci
    *bci_p = bci;
    return true;
  }

  return false;
}


// Determine if 'fr' can be used to find an initial Java frame.
// Return false if it can not find a fully decipherable Java frame
// (in other words a frame that isn't safe to use in a vframe stream).
// Obviously if it can't even find a Java frame false will also be returned.
//
// If we find a Java frame decipherable or not then by definition we have
// identified a method and that will be returned to the caller via method_p.
// If we can determine a bci that is returned also. (Hmm is it possible
// to return a method and bci and still return false? )
//
// The initial Java frame we find (if any) is return via initial_frame_p.
//

static bool find_initial_Java_frame(JavaThread* thread,
                                    frame* fr,
                                    frame* initial_frame_p,
                                    methodOop* method_p,
                                    int* bci_p) {

  // It is possible that for a frame containing an nmethod
  // we can capture the method but no bci. If we get no
  // bci the frame isn't walkable but the method is usable.
  // Therefore we init the returned methodOop to NULL so the
  // caller can make the distinction.

  *method_p = NULL;

  // On the initial call to this method the frame we get may not be
  // recognizable to us. This should only happen if we are in a JRT_LEAF
  // or something called by a JRT_LEAF method.



  frame candidate = *fr;

  // If the starting frame we were given has no codeBlob associated with
  // it see if we can find such a frame because only frames with codeBlobs
  // are possible Java frames.

  if (fr->cb() == NULL) {

    // See if we can find a useful frame
    int loop_count;
    int loop_max = MaxJavaStackTraceDepth * 2;
    RegisterMap map(thread, false);

    for (loop_count = 0; loop_count < loop_max; loop_count++) {
      if (!candidate.safe_for_sender(thread)) return false;
      candidate = candidate.sender(&map);
      if (candidate.cb() != NULL) break;
    }
    if (candidate.cb() == NULL) return false;
  }

  // We have a frame known to be in the codeCache
  // We will hopefully be able to figure out something to do with it.
  int loop_count;
  int loop_max = MaxJavaStackTraceDepth * 2;
  RegisterMap map(thread, false);

  for (loop_count = 0; loop_count < loop_max; loop_count++) {

    if (candidate.is_first_frame()) {
      // If initial frame is frame from StubGenerator and there is no
      // previous anchor, there are no java frames associated with a method
      return false;
    }

    if (candidate.is_interpreted_frame()) {
      if (is_decipherable_interpreted_frame(thread, &candidate, method_p, bci_p)) {
        *initial_frame_p = candidate;
        return true;
      }

      // Hopefully we got some data
      return false;
    }

    if (candidate.cb()->is_nmethod()) {

      nmethod* nm = (nmethod*) candidate.cb();
      *method_p = nm->method();

      // If the frame isn't fully decipherable then the default
      // value for the bci is a signal that we don't have a bci.
      // If we have a decipherable frame this bci value will
      // not be used.

      *bci_p = -1;

      *initial_frame_p = candidate;

      // Native wrapper code is trivial to decode by vframeStream

      if (nm->is_native_method()) return true;

      // If it isn't decipherable then we have found a pc that doesn't
      // have a PCDesc that can get us a bci however we did find
      // a method

      if (!is_decipherable_compiled_frame(thread, &candidate, nm)) {
        return false;
      }

      // is_decipherable_compiled_frame may modify candidate's pc
      *initial_frame_p = candidate;

      assert(nm->pc_desc_at(candidate.pc()) != NULL, "if it's decipherable then pc must be valid");

      return true;
    }

    // Must be some stub frame that we don't care about

    if (!candidate.safe_for_sender(thread)) return false;
    candidate = candidate.sender(&map);

    // If it isn't in the code cache something is wrong
    // since once we find a frame in the code cache they
    // all should be there.

    if (candidate.cb() == NULL) return false;

  }

  return false;

}


// call frame copied from old .h file and renamed
typedef struct {
    jint lineno;                      // line number in the source file
    jmethodID method_id;              // method executed in this frame
} ASGCT_CallFrame;

// call trace copied from old .h file and renamed
typedef struct {
    JNIEnv *env_id;                   // Env where trace was recorded
    jint num_frames;                  // number of frames in this trace
    ASGCT_CallFrame *frames;          // frames
} ASGCT_CallTrace;

static void forte_fill_call_trace_given_top(JavaThread* thd,
                                            ASGCT_CallTrace* trace,
                                            int depth,
                                            frame top_frame) {
  NoHandleMark nhm;

  frame initial_Java_frame;
  methodOop method;
  int bci;
  int count;

  count = 0;
  assert(trace->frames != NULL, "trace->frames must be non-NULL");

  bool fully_decipherable = find_initial_Java_frame(thd, &top_frame, &initial_Java_frame, &method, &bci);

  // The frame might not be walkable but still recovered a method
  // (e.g. an nmethod with no scope info for the pc

  if (method == NULL) return;

  CollectedHeap* ch = Universe::heap();

  // The method is not stored GC safe so see if GC became active
  // after we entered AsyncGetCallTrace() and before we try to
  // use the methodOop.
  // Yes, there is still a window after this check and before
  // we use methodOop below, but we can't lock out GC so that
  // has to be an acceptable risk.
  if (!ch->is_valid_method(method)) {
    trace->num_frames = ticks_GC_active; // -2
    return;
  }

  // We got a Java frame however it isn't fully decipherable
  // so it won't necessarily be safe to use it for the
  // initial frame in the vframe stream.

  if (!fully_decipherable) {
    // Take whatever method the top-frame decoder managed to scrape up.
    // We look further at the top frame only if non-safepoint
    // debugging information is available.
    count++;
    trace->num_frames = count;
    trace->frames[0].method_id = method->find_jmethod_id_or_null();
    if (!method->is_native()) {
      trace->frames[0].lineno = bci;
    } else {
      trace->frames[0].lineno = -3;
    }

    if (!initial_Java_frame.safe_for_sender(thd)) return;

    RegisterMap map(thd, false);
    initial_Java_frame = initial_Java_frame.sender(&map);
  }

  vframeStreamForte st(thd, initial_Java_frame, false);

  for (; !st.at_end() && count < depth; st.forte_next(), count++) {
    bci = st.bci();
    method = st.method();

    // The method is not stored GC safe so see if GC became active
    // after we entered AsyncGetCallTrace() and before we try to
    // use the methodOop.
    // Yes, there is still a window after this check and before
    // we use methodOop below, but we can't lock out GC so that
    // has to be an acceptable risk.
    if (!ch->is_valid_method(method)) {
      // we throw away everything we've gathered in this sample since
      // none of it is safe
      trace->num_frames = ticks_GC_active; // -2
      return;
    }

    trace->frames[count].method_id = method->find_jmethod_id_or_null();
    if (!method->is_native()) {
      trace->frames[count].lineno = bci;
    } else {
      trace->frames[count].lineno = -3;
    }
  }
  trace->num_frames = count;
  return;
}


// Forte Analyzer AsyncGetCallTrace() entry point. Currently supported
// on Linux X86, Solaris SPARC and Solaris X86.
//
// Async-safe version of GetCallTrace being called from a signal handler
// when a LWP gets interrupted by SIGPROF but the stack traces are filled
// with different content (see below).
//
// This function must only be called when JVM/TI
// CLASS_LOAD events have been enabled since agent startup. The enabled
// event will cause the jmethodIDs to be allocated at class load time.
// The jmethodIDs cannot be allocated in a signal handler because locks
// cannot be grabbed in a signal handler safely.
//
// void (*AsyncGetCallTrace)(ASGCT_CallTrace *trace, jint depth, void* ucontext)
//
// Called by the profiler to obtain the current method call stack trace for
// a given thread. The thread is identified by the env_id field in the
// ASGCT_CallTrace structure. The profiler agent should allocate a ASGCT_CallTrace
// structure with enough memory for the requested stack depth. The VM fills in
// the frames buffer and the num_frames field.
//
// Arguments:
//
//   trace    - trace data structure to be filled by the VM.
//   depth    - depth of the call stack trace.
//   ucontext - ucontext_t of the LWP
//
// ASGCT_CallTrace:
//   typedef struct {
//       JNIEnv *env_id;
//       jint num_frames;
//       ASGCT_CallFrame *frames;
//   } ASGCT_CallTrace;
//
// Fields:
//   env_id     - ID of thread which executed this trace.
//   num_frames - number of frames in the trace.
//                (< 0 indicates the frame is not walkable).
//   frames     - the ASGCT_CallFrames that make up this trace. Callee followed by callers.
//
//  ASGCT_CallFrame:
//    typedef struct {
//        jint lineno;
//        jmethodID method_id;
//    } ASGCT_CallFrame;
//
//  Fields:
//    1) For Java frame (interpreted and compiled),
//       lineno    - bci of the method being executed or -1 if bci is not available
//       method_id - jmethodID of the method being executed
//    2) For native method
//       lineno    - (-3)
//       method_id - jmethodID of the method being executed

extern "C" {
void AsyncGetCallTrace(ASGCT_CallTrace *trace, jint depth, void* ucontext) {

// This is if'd out because we no longer use thread suspension.
// However if someone wanted to backport this to a 5.0 jvm then this
// code would be important.
#if 0
  if (SafepointSynchronize::is_synchronizing()) {
    // The safepoint mechanism is trying to synchronize all the threads.
    // Since this can involve thread suspension, it is not safe for us
    // to be here. We can reduce the deadlock risk window by quickly
    // returning to the SIGPROF handler. However, it is still possible
    // for VMThread to catch us here or in the SIGPROF handler. If we
    // are suspended while holding a resource and another thread blocks
    // on that resource in the SIGPROF handler, then we will have a
    // three-thread deadlock (VMThread, this thread, the other thread).
    trace->num_frames = ticks_safepoint; // -10
    return;
  }
#endif

  JavaThread* thread;

  if (trace->env_id == NULL ||
    (thread = JavaThread::thread_from_jni_environment(trace->env_id)) == NULL ||
    thread->is_exiting()) {

    // bad env_id, thread has exited or thread is exiting
    trace->num_frames = ticks_thread_exit; // -8
    return;
  }

  if (thread->in_deopt_handler()) {
    // thread is in the deoptimization handler so return no frames
    trace->num_frames = ticks_deopt; // -9
    return;
  }

  assert(JavaThread::current() == thread,
         "AsyncGetCallTrace must be called by the current interrupted thread");

  if (!JvmtiExport::should_post_class_load()) {
    trace->num_frames = ticks_no_class_load; // -1
    return;
  }

  if (Universe::heap()->is_gc_active()) {
    trace->num_frames = ticks_GC_active; // -2
    return;
  }

  switch (thread->thread_state()) {
  case _thread_new:
  case _thread_uninitialized:
  case _thread_new_trans:
    // We found the thread on the threads list above, but it is too
    // young to be useful so return that there are no Java frames.
    trace->num_frames = 0;
    break;
  case _thread_in_native:
  case _thread_in_native_trans:
  case _thread_blocked:
  case _thread_blocked_trans:
  case _thread_in_vm:
  case _thread_in_vm_trans:
    {
      frame fr;

      // param isInJava == false - indicate we aren't in Java code
      if (!thread->pd_get_top_frame_for_signal_handler(&fr, ucontext, false)) {
        trace->num_frames = ticks_unknown_not_Java;  // -3 unknown frame
      } else {
        if (!thread->has_last_Java_frame()) {
          trace->num_frames = 0; // No Java frames
        } else {
          trace->num_frames = ticks_not_walkable_not_Java;    // -4 non walkable frame by default
          forte_fill_call_trace_given_top(thread, trace, depth, fr);

          // This assert would seem to be valid but it is not.
          // It would be valid if we weren't possibly racing a gc
          // thread. A gc thread can make a valid interpreted frame
          // look invalid. It's a small window but it does happen.
          // The assert is left here commented out as a reminder.
          // assert(trace->num_frames != ticks_not_walkable_not_Java, "should always be walkable");

        }
      }
    }
    break;
  case _thread_in_Java:
  case _thread_in_Java_trans:
    {
      frame fr;

      // param isInJava == true - indicate we are in Java code
      if (!thread->pd_get_top_frame_for_signal_handler(&fr, ucontext, true)) {
        trace->num_frames = ticks_unknown_Java;  // -5 unknown frame
      } else {
        trace->num_frames = ticks_not_walkable_Java;  // -6, non walkable frame by default
        forte_fill_call_trace_given_top(thread, trace, depth, fr);
      }
    }
    break;
  default:
    // Unknown thread state
    trace->num_frames = ticks_unknown_state; // -7
    break;
  }
}


#ifndef _WINDOWS
// Support for the Forte(TM) Peformance Tools collector.
//
// The method prototype is derived from libcollector.h. For more
// information, please see the libcollect man page.

// Method to let libcollector know about a dynamically loaded function.
// Because it is weakly bound, the calls become NOP's when the library
// isn't present.
void    collector_func_load(char* name,
                            void* null_argument_1,
                            void* null_argument_2,
                            void *vaddr,
                            int size,
                            int zero_argument,
                            void* null_argument_3);
#pragma weak collector_func_load
#define collector_func_load(x0,x1,x2,x3,x4,x5,x6) \
        ( collector_func_load ? collector_func_load(x0,x1,x2,x3,x4,x5,x6),0 : 0 )
#endif // !_WINDOWS

} // end extern "C"
#endif // !IA64

void Forte::register_stub(const char* name, address start, address end) {
#if !defined(_WINDOWS) && !defined(IA64)
  assert(pointer_delta(end, start, sizeof(jbyte)) < INT_MAX,
         "Code size exceeds maximum range");

  collector_func_load((char*)name, NULL, NULL, start,
    pointer_delta(end, start, sizeof(jbyte)), 0, NULL);
#endif // !_WINDOWS && !IA64
}
