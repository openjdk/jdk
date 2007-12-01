/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_forte.cpp.incl"


//-------------------------------------------------------

// Native interfaces for use by Forte tools.


#ifndef IA64

class vframeStreamForte : public vframeStreamCommon {
 public:
  // constructor that starts with sender of frame fr (top_frame)
  vframeStreamForte(JavaThread *jt, frame fr, bool stop_at_java_call_stub);
  void forte_next();
};


static void forte_is_walkable_compiled_frame(frame* fr, RegisterMap* map,
  bool* is_compiled_p, bool* is_walkable_p);
static bool forte_is_walkable_interpreted_frame(frame* fr,
  methodOop* method_p, int* bci_p);


// A Forte specific version of frame:safe_for_sender().
static bool forte_safe_for_sender(frame* fr, JavaThread *thread) {
  bool ret_value = false;  // be pessimistic

#ifdef COMPILER2
#if defined(IA32) || defined(AMD64)
  {
    // This check is the same as the standard safe_for_sender()
    // on IA32 or AMD64 except that NULL FP values are tolerated
    // for C2.
    address   sp = (address)fr->sp();
    address   fp = (address)fr->fp();
    ret_value = sp != NULL && sp <= thread->stack_base() &&
      sp >= thread->stack_base() - thread->stack_size() &&
      (fp == NULL || (fp <= thread->stack_base() &&
      fp >= thread->stack_base() - thread->stack_size()));

    // We used to use standard safe_for_sender() when we are supposed
    // to be executing Java code. However, that prevents us from
    // walking some intrinsic stacks so now we have to be more refined.
    // If we passed the above check and we have a NULL frame pointer
    // and we are supposed to be executing Java code, then we have a
    // couple of more checks to make.
    if (ret_value && fp == NULL && (thread->thread_state() == _thread_in_Java
        || thread->thread_state() == _thread_in_Java_trans)) {

      if (fr->is_interpreted_frame()) {
        // interpreted frames don't really have a NULL frame pointer
        return false;
      } else if (CodeCache::find_blob(fr->pc()) == NULL) {
        // the NULL frame pointer should be associated with generated code
        return false;
      }
    }
  }

#else // !(IA32 || AMD64)
  ret_value = fr->safe_for_sender(thread);
#endif // IA32 || AMD64

#else // !COMPILER2
  ret_value = fr->safe_for_sender(thread);
#endif // COMPILER2

  if (!ret_value) {
    return ret_value;  // not safe, nothing more to do
  }

  address sp1;

#ifdef SPARC
  // On Solaris SPARC, when a compiler frame has an interpreted callee
  // the _interpreter_sp_adjustment field contains the adjustment to
  // this frame's SP made by that interpreted callee.
  // For AsyncGetCallTrace(), we need to verify that the resulting SP
  // is valid for the specified thread's stack.
  sp1 = (address)fr->sp();
  address sp2 = (address)fr->unextended_sp();

  // If the second SP is NULL, then the _interpreter_sp_adjustment
  // field simply adjusts this frame's SP to NULL and the frame is
  // not safe. This strange value can be set in the frame constructor
  // when our peek into the interpreted callee's adjusted value for
  // this frame's SP finds a NULL. This can happen when SIGPROF
  // catches us while we are creating the interpreter frame.
  //
  if (sp2 == NULL ||

      // If the two SPs are different, then _interpreter_sp_adjustment
      // is non-zero and we need to validate the second SP. We invert
      // the range check from frame::safe_for_sender() and bail out
      // if the second SP is not safe.
      (sp1 != sp2 && !(sp2 <= thread->stack_base()
      && sp2 >= (thread->stack_base() - thread->stack_size())))) {
    return false;
  }
#endif // SPARC

  if (fr->is_entry_frame()) {
    // This frame thinks it is an entry frame; we need to validate
    // the JavaCallWrapper pointer.
    // Note: frame::entry_frame_is_first() assumes that the
    // JavaCallWrapper has a non-NULL _anchor field. We don't
    // check that here (yet) since we've never seen a failure
    // due to a NULL _anchor field.
    // Update: Originally this check was done only for SPARC. However,
    // this failure has now been seen on C2 C86. I have no reason to
    // believe that this is not a general issue so I'm enabling the
    // check for all compilers on all supported platforms.
#ifdef COMPILER2
#if defined(IA32) || defined(AMD64)
    if (fr->fp() == NULL) {
      // C2 X86 allows NULL frame pointers, but if we have one then
      // we cannot call entry_frame_call_wrapper().
      return false;
    }
#endif // IA32 || AMD64
#endif // COMPILER2

    sp1 = (address)fr->entry_frame_call_wrapper();
    // We invert the range check from frame::safe_for_sender() and
    // bail out if the JavaCallWrapper * is not safe.
    if (!(sp1 <= thread->stack_base()
        && sp1 >= (thread->stack_base() - thread->stack_size()))) {
      return false;
    }
  }

  return ret_value;
}


// Unknown compiled frames have caused assertion failures on Solaris
// X86. This code also detects unknown compiled frames on Solaris
// SPARC, but no assertion failures have been observed. However, I'm
// paranoid so I'm enabling this code whenever we have a compiler.
//
// Returns true if the specified frame is an unknown compiled frame
// and false otherwise.
static bool is_unknown_compiled_frame(frame* fr, JavaThread *thread) {
  bool ret_value = false;  // be optimistic

  // This failure mode only occurs when the thread is in state
  // _thread_in_Java so we are okay for this check for any other
  // thread state.
  //
  // Note: _thread_in_Java does not always mean that the thread
  // is executing Java code. AsyncGetCallTrace() has caught
  // threads executing in JRT_LEAF() routines when the state
  // will also be _thread_in_Java.
  if (thread->thread_state() != _thread_in_Java) {
    return ret_value;
  }

  // This failure mode only occurs with compiled frames so we are
  // okay for this check for both entry and interpreted frames.
  if (fr->is_entry_frame() || fr->is_interpreted_frame()) {
    return ret_value;
  }

  // This failure mode only occurs when the compiled frame's PC
  // is in the code cache so we are okay for this check if the
  // PC is not in the code cache.
  CodeBlob* cb = CodeCache::find_blob(fr->pc());
  if (cb == NULL) {
    return ret_value;
  }

  // We have compiled code in the code cache so it is time for
  // the final check: let's see if any frame type is set
  ret_value = !(
    // is_entry_frame() is checked above
    // testers that are a subset of is_entry_frame():
    //   is_first_frame()
    fr->is_java_frame()
    // testers that are a subset of is_java_frame():
    //   is_interpreted_frame()
    //   is_compiled_frame()
    || fr->is_native_frame()
    || fr->is_runtime_frame()
    || fr->is_safepoint_blob_frame()
    );

  // If there is no frame type set, then we have an unknown compiled
  // frame and sender() should not be called on it.

  return ret_value;
}

#define DebugNonSafepoints_IS_CLEARED \
  (!FLAG_IS_DEFAULT(DebugNonSafepoints) && !DebugNonSafepoints)

// if -XX:-DebugNonSafepoints, then top-frame will be skipped
vframeStreamForte::vframeStreamForte(JavaThread *jt, frame fr,
  bool stop_at_java_call_stub) : vframeStreamCommon(jt) {
  _stop_at_java_call_stub = stop_at_java_call_stub;

  if (!DebugNonSafepoints_IS_CLEARED) {
    // decode the top frame fully
    // (usual case, if JVMTI is enabled)
    _frame = fr;
  } else {
    // skip top frame, as it may not be at safepoint
    // For AsyncGetCallTrace(), we extracted as much info from the top
    // frame as we could in forte_is_walkable_frame(). We also verified
    // forte_safe_for_sender() so this sender() call is safe.
    _frame  = fr.sender(&_reg_map);
  }

  if (jt->thread_state() == _thread_in_Java && !fr.is_first_frame()) {
    bool sender_check = false;  // assume sender is not safe

    if (forte_safe_for_sender(&_frame, jt)) {
      // If the initial sender frame is safe, then continue on with other
      // checks. The unsafe sender frame has been seen on Solaris X86
      // with both Compiler1 and Compiler2. It has not been seen on
      // Solaris SPARC, but seems like a good sanity check to have
      // anyway.

      // SIGPROF caught us in Java code and the current frame is not the
      // first frame so we should sanity check the sender frame. It is
      // possible for SIGPROF to catch us in the middle of making a call.
      // When that happens the current frame is actually a combination of
      // the real sender and some of the new call's info. We can't find
      // the real sender with such a current frame and things can get
      // confused.
      //
      // This sanity check has caught problems with the sender frame on
      // Solaris SPARC. So far Solaris X86 has not had a failure here.
      sender_check = _frame.is_entry_frame()
        // testers that are a subset of is_entry_frame():
        //   is_first_frame()
        || _frame.is_java_frame()
        // testers that are a subset of is_java_frame():
        //   is_interpreted_frame()
        //   is_compiled_frame()
        || _frame.is_native_frame()
        || _frame.is_runtime_frame()
        || _frame.is_safepoint_blob_frame()
        ;

      // We need an additional sanity check on an initial interpreted
      // sender frame. This interpreted frame needs to be both walkable
      // and have a valid BCI. This is yet another variant of SIGPROF
      // catching us in the middle of making a call.
      if (sender_check && _frame.is_interpreted_frame()) {
        methodOop method = NULL;
        int bci = -1;

        if (!forte_is_walkable_interpreted_frame(&_frame, &method, &bci)
            || bci == -1) {
          sender_check = false;
        }
      }

      // We need an additional sanity check on an initial compiled
      // sender frame. This compiled frame also needs to be walkable.
      // This is yet another variant of SIGPROF catching us in the
      // middle of making a call.
      if (sender_check && !_frame.is_interpreted_frame()) {
        bool is_compiled, is_walkable;

        forte_is_walkable_compiled_frame(&_frame, &_reg_map,
          &is_compiled, &is_walkable);
        if (is_compiled && !is_walkable) {
          sender_check = false;
        }
      }
    }

    if (!sender_check) {
      // nothing else to try if we can't recognize the sender
      _mode = at_end_mode;
      return;
    }
  }

  int loop_count = 0;
  int loop_max = MaxJavaStackTraceDepth * 2;

  while (!fill_from_frame()) {
    _frame = _frame.sender(&_reg_map);

#ifdef COMPILER2
#if defined(IA32) || defined(AMD64)
    // Stress testing on C2 X86 has shown a periodic problem with
    // the sender() call below. The initial _frame that we have on
    // entry to the loop has already passed forte_safe_for_sender()
    // so we only check frames after it.
    if (!forte_safe_for_sender(&_frame, _thread)) {
      _mode = at_end_mode;
      return;
    }
#endif // IA32 || AMD64
#endif // COMPILER2

    if (++loop_count >= loop_max) {
      // We have looped more than twice the number of possible
      // Java frames. This indicates that we are trying to walk
      // a stack that is in the middle of being constructed and
      // it is self referential.
      _mode = at_end_mode;
      return;
    }
  }
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

#if defined(COMPILER1) && defined(SPARC)
  bool prevIsInterpreted =  _frame.is_interpreted_frame();
#endif // COMPILER1 && SPARC

    _frame = _frame.sender(&_reg_map);

    if (!forte_safe_for_sender(&_frame, _thread)) {
      _mode = at_end_mode;
      return;
    }

#if defined(COMPILER1) && defined(SPARC)
    if (prevIsInterpreted) {
      // previous callee was interpreted and may require a special check
      if (_frame.is_compiled_frame() && _frame.cb()->is_compiled_by_c1()) {
        // compiled sender called interpreted callee so need one more check
        bool is_compiled, is_walkable;

        // sanity check the compiled sender frame
        forte_is_walkable_compiled_frame(&_frame, &_reg_map,
          &is_compiled, &is_walkable);
        assert(is_compiled, "sanity check");
        if (!is_walkable) {
          // compiled sender frame is not walkable so bail out
          _mode = at_end_mode;
          return;
        }
      }
    }
#endif // COMPILER1 && SPARC

    if (++loop_count >= loop_max) {
      // We have looped more than twice the number of possible
      // Java frames. This indicates that we are trying to walk
      // a stack that is in the middle of being constructed and
      // it is self referential.
      _mode = at_end_mode;
      return;
    }
  } while (!fill_from_frame());
}

// Determine if 'fr' is a walkable, compiled frame.
// *is_compiled_p is set to true if the frame is compiled and if it
// is, then *is_walkable_p is set to true if it is also walkable.
static void forte_is_walkable_compiled_frame(frame* fr, RegisterMap* map,
  bool* is_compiled_p, bool* is_walkable_p) {

  *is_compiled_p = false;
  *is_walkable_p = false;

  CodeBlob* cb = CodeCache::find_blob(fr->pc());
  if (cb != NULL &&
      cb->is_nmethod() &&
      ((nmethod*)cb)->is_java_method()) {
    // frame is compiled and executing a Java method
    *is_compiled_p = true;

    // Increment PC because the PcDesc we want is associated with
    // the *end* of the instruction, and pc_desc_near searches
    // forward to the first matching PC after the probe PC.
    PcDesc* pc_desc = NULL;
    if (!DebugNonSafepoints_IS_CLEARED) {
      // usual case:  look for any safepoint near the sampled PC
      address probe_pc = fr->pc() + 1;
      pc_desc = ((nmethod*) cb)->pc_desc_near(probe_pc);
    } else {
      // reduced functionality:  only recognize PCs immediately after calls
      pc_desc = ((nmethod*) cb)->pc_desc_at(fr->pc());
    }
    if (pc_desc != NULL && (pc_desc->scope_decode_offset()
                            == DebugInformationRecorder::serialized_null)) {
      pc_desc = NULL;
    }
    if (pc_desc != NULL) {
      // it has a PcDesc so the frame is also walkable
      *is_walkable_p = true;
      if (!DebugNonSafepoints_IS_CLEARED) {
        // Normalize the PC to the one associated exactly with
        // this PcDesc, so that subsequent stack-walking queries
        // need not be approximate:
        fr->set_pc(pc_desc->real_pc((nmethod*) cb));
      }
    }
    // Implied else: this compiled frame has no PcDesc, i.e., contains
    // a frameless stub such as C1 method exit, so it is not walkable.
  }
  // Implied else: this isn't a compiled frame so it isn't a
  // walkable, compiled frame.
}

// Determine if 'fr' is a walkable interpreted frame. Returns false
// if it is not. *method_p, and *bci_p are not set when false is
// returned. *method_p is non-NULL if frame was executing a Java
// method. *bci_p is != -1 if a valid BCI in the Java method could
// be found.
// Note: this method returns true when a valid Java method is found
// even if a valid BCI cannot be found.

static bool forte_is_walkable_interpreted_frame(frame* fr,
  methodOop* method_p, int* bci_p) {
  assert(fr->is_interpreted_frame(), "just checking");

  // top frame is an interpreted frame
  // check if it is walkable (i.e. valid methodOop and valid bci)
  if (fr->is_interpreted_frame_valid()) {
    if (fr->fp() != NULL) {
      // access address in order not to trigger asserts that
      // are built in interpreter_frame_method function
      methodOop method = *fr->interpreter_frame_method_addr();
      if (Universe::heap()->is_valid_method(method)) {
        intptr_t bcx = fr->interpreter_frame_bcx();
        int      bci = method->validate_bci_from_bcx(bcx);
        // note: bci is set to -1 if not a valid bci
        *method_p = method;
        *bci_p = bci;
        return true;
      }
    }
  }
  return false;
}


// Determine if 'fr' can be used to find a walkable frame. Returns
// false if a walkable frame cannot be found. *walkframe_p, *method_p,
// and *bci_p are not set when false is returned. Returns true if a
// walkable frame is returned via *walkframe_p. *method_p is non-NULL
// if the returned frame was executing a Java method. *bci_p is != -1
// if a valid BCI in the Java method could be found.
//
// *walkframe_p will be used by vframeStreamForte as the initial
// frame for walking the stack. Currently the initial frame is
// skipped by vframeStreamForte because we inherited the logic from
// the vframeStream class. This needs to be revisited in the future.
static bool forte_is_walkable_frame(JavaThread* thread, frame* fr,
  frame* walkframe_p, methodOop* method_p, int* bci_p) {

  if (!forte_safe_for_sender(fr, thread)
      || is_unknown_compiled_frame(fr, thread)
     ) {
    // If the initial frame is not safe, then bail out. So far this
    // has only been seen on Solaris X86 with Compiler2, but it seems
    // like a great initial sanity check.
    return false;
  }

  if (fr->is_first_frame()) {
    // If initial frame is frame from StubGenerator and there is no
    // previous anchor, there are no java frames yet
    return false;
  }

  if (fr->is_interpreted_frame()) {
    if (forte_is_walkable_interpreted_frame(fr, method_p, bci_p)) {
      *walkframe_p = *fr;
      return true;
    }
    return false;
  }

  // At this point we have something other than a first frame or an
  // interpreted frame.

  methodOop method = NULL;
  frame candidate = *fr;

  // If we loop more than twice the number of possible Java
  // frames, then this indicates that we are trying to walk
  // a stack that is in the middle of being constructed and
  // it is self referential. So far this problem has only
  // been seen on Solaris X86 Compiler2, but it seems like
  // a good robustness fix for all platforms.

  int loop_count;
  int loop_max = MaxJavaStackTraceDepth * 2;

  for (loop_count = 0; loop_count < loop_max; loop_count++) {
    // determine if the candidate frame is executing a Java method
    if (CodeCache::contains(candidate.pc())) {
      // candidate is a compiled frame or stub routine
      CodeBlob* cb = CodeCache::find_blob(candidate.pc());

      if (cb->is_nmethod()) {
        method = ((nmethod *)cb)->method();
      }
    } // end if CodeCache has our PC

    RegisterMap map(thread, false);

    // we have a Java frame that seems reasonable
    if (method != NULL && candidate.is_java_frame()
        && candidate.sp() != NULL && candidate.pc() != NULL) {
      // we need to sanity check the candidate further
      bool is_compiled, is_walkable;

      forte_is_walkable_compiled_frame(&candidate, &map, &is_compiled,
        &is_walkable);
      if (is_compiled) {
        // At this point, we know we have a compiled Java frame with
        // method information that we want to return. We don't check
        // the is_walkable flag here because that flag pertains to
        // vframeStreamForte work that is done after we are done here.
        break;
      }
    }

    // At this point, the candidate doesn't work so try the sender.

    // For AsyncGetCallTrace() we cannot assume there is a sender
    // for the initial frame. The initial forte_safe_for_sender() call
    // and check for is_first_frame() is done on entry to this method.
    candidate = candidate.sender(&map);
    if (!forte_safe_for_sender(&candidate, thread)) {

#ifdef COMPILER2
#if defined(IA32) || defined(AMD64)
      // C2 on X86 can use the ebp register as a general purpose register
      // which can cause the candidate to fail theforte_safe_for_sender()
      // above. We try one more time using a NULL frame pointer (fp).

      candidate = frame(candidate.sp(), NULL, candidate.pc());
      if (!forte_safe_for_sender(&candidate, thread)) {
#endif // IA32 || AMD64
#endif // COMPILER2

        return false;

#ifdef COMPILER2
#if defined(IA32) || defined(AMD64)
      } // end forte_safe_for_sender retry with NULL fp
#endif // IA32 || AMD64
#endif // COMPILER2

    } // end first forte_safe_for_sender check

    if (candidate.is_first_frame()
        || is_unknown_compiled_frame(&candidate, thread)) {
      return false;
    }
  } // end for loop_count

  if (method == NULL) {
    // If we didn't get any method info from the candidate, then
    // we have nothing to return so bail out.
    return false;
  }

  *walkframe_p = candidate;
  *method_p = method;
  *bci_p = -1;
  return true;
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
  ASGCT_CallTrace* trace, int depth, frame top_frame) {
  NoHandleMark nhm;

  frame walkframe;
  methodOop method;
  int bci;
  int count;

  count = 0;
  assert(trace->frames != NULL, "trace->frames must be non-NULL");

  if (!forte_is_walkable_frame(thd, &top_frame, &walkframe, &method, &bci)) {
    // return if no walkable frame is found
    return;
  }

  CollectedHeap* ch = Universe::heap();

  if (method != NULL) {
    // The method is not stored GC safe so see if GC became active
    // after we entered AsyncGetCallTrace() and before we try to
    // use the methodOop.
    // Yes, there is still a window after this check and before
    // we use methodOop below, but we can't lock out GC so that
    // has to be an acceptable risk.
    if (!ch->is_valid_method(method)) {
      trace->num_frames = -2;
      return;
    }

    if (DebugNonSafepoints_IS_CLEARED) {
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
    }
  }

  // check has_last_Java_frame() after looking at the top frame
  // which may be an interpreted Java frame.
  if (!thd->has_last_Java_frame() && method == NULL) {
    trace->num_frames = 0;
    return;
  }

  vframeStreamForte st(thd, walkframe, false);
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
      trace->num_frames = -2;
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
  if (SafepointSynchronize::is_synchronizing()) {
    // The safepoint mechanism is trying to synchronize all the threads.
    // Since this can involve thread suspension, it is not safe for us
    // to be here. We can reduce the deadlock risk window by quickly
    // returning to the SIGPROF handler. However, it is still possible
    // for VMThread to catch us here or in the SIGPROF handler. If we
    // are suspended while holding a resource and another thread blocks
    // on that resource in the SIGPROF handler, then we will have a
    // three-thread deadlock (VMThread, this thread, the other thread).
    trace->num_frames = -10;
    return;
  }

  JavaThread* thread;

  if (trace->env_id == NULL ||
    (thread = JavaThread::thread_from_jni_environment(trace->env_id)) == NULL ||
    thread->is_exiting()) {

    // bad env_id, thread has exited or thread is exiting
    trace->num_frames = -8;
    return;
  }

  if (thread->in_deopt_handler()) {
    // thread is in the deoptimization handler so return no frames
    trace->num_frames = -9;
    return;
  }

  assert(JavaThread::current() == thread,
         "AsyncGetCallTrace must be called by the current interrupted thread");

  if (!JvmtiExport::should_post_class_load()) {
    trace->num_frames = -1;
    return;
  }

  if (Universe::heap()->is_gc_active()) {
    trace->num_frames = -2;
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
        if (!thread->has_last_Java_frame()) {
          trace->num_frames = 0;   // no Java frames
        } else {
          trace->num_frames = -3;  // unknown frame
        }
      } else {
        trace->num_frames = -4;    // non walkable frame by default
        forte_fill_call_trace_given_top(thread, trace, depth, fr);
      }
    }
    break;
  case _thread_in_Java:
  case _thread_in_Java_trans:
    {
      frame fr;

      // param isInJava == true - indicate we are in Java code
      if (!thread->pd_get_top_frame_for_signal_handler(&fr, ucontext, true)) {
        trace->num_frames = -5;  // unknown frame
      } else {
        trace->num_frames = -6;  // non walkable frame by default
        forte_fill_call_trace_given_top(thread, trace, depth, fr);
      }
    }
    break;
  default:
    // Unknown thread state
    trace->num_frames = -7;
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
    "Code size exceeds maximum range")

  collector_func_load((char*)name, NULL, NULL, start,
    pointer_delta(end, start, sizeof(jbyte)), 0, NULL);
#endif // !_WINDOWS && !IA64
}
