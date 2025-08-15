/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaThreadStatus.hpp"
#include "code/codeCache.inline.hpp"
#include "code/debugInfoRec.hpp"
#include "code/nmethod.hpp"
#include "interpreter/interpreter.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/sampling/jfrCPUTimeThreadSampler.hpp"
#include "jfr/periodic/sampling/jfrSampleMonitor.hpp"
#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/periodic/sampling/jfrThreadSampling.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "runtime/continuation.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/stackFrameStream.inline.hpp"

template <typename EventType>
static inline void send_sample_event(const JfrTicks& start_time, const JfrTicks& end_time, traceid sid, traceid tid) {
  EventType event(UNTIMED);
  event.set_starttime(start_time);
  event.set_endtime(end_time);
  event.set_sampledThread(tid);
  event.set_state(static_cast<u8>(JavaThreadStatus::RUNNABLE));
  event.set_stackTrace(sid);
  event.commit();
}

static inline void send_safepoint_latency_event(const JfrSampleRequest& request, const JfrTicks& end_time, traceid sid, const JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  assert(!jt->jfr_thread_local()->has_cached_stack_trace(), "invariant");
  EventSafepointLatency event(UNTIMED);
  event.set_starttime(request._sample_ticks);
  event.set_endtime(end_time);
  if (event.should_commit()) {
    event.set_threadState(_thread_in_Java);
    jt->jfr_thread_local()->set_cached_stack_trace_id(sid);
    event.commit();
    jt->jfr_thread_local()->clear_cached_stack_trace();
  }
}

static inline bool is_interpreter(address pc) {
  return Interpreter::contains(pc);
}

static inline bool is_interpreter(const JfrSampleRequest& request) {
  return request._sample_bcp != nullptr;
}

static inline bool is_in_continuation(const frame& frame, JavaThread* jt) {
  return JfrThreadLocal::is_vthread(jt) &&
         (Continuation::is_frame_in_continuation(jt, frame) || Continuation::is_continuation_enterSpecial(frame));
}

// A sampled interpreter frame is handled differently from a sampled compiler frame.
//
// The JfrSampleRequest description partially describes a _potential_ interpreter Java frame.
// It's partial because the sampler thread only sets the fp and bcp fields.
//
// We want to ensure that what we discovered inside interpreter code _really_ is what we assume, a valid interpreter frame.
//
// Therefore, instead of letting the sampler thread read what it believes to be a Method*, we delay until we are at a safepoint to ensure the Method* is valid.
//
// If the JfrSampleRequest represents a valid interpreter frame, the Method* is retrieved and the sender frame is returned per the sender_frame.
//
// If it is not a valid interpreter frame, then the JfrSampleRequest is invalidated, and the current frame is returned per the sender frame.
//
static bool compute_sender_frame(JfrSampleRequest& request, frame& sender_frame, bool& in_continuation, JavaThread* jt) {
  assert(is_interpreter(request), "invariant");
  assert(jt != nullptr, "invariant");
  assert(jt->has_last_Java_frame(), "invariant");

  // For a request representing an interpreter frame, request._sample_sp is actually the frame pointer, fp.
  const void* const sampled_fp = request._sample_sp;

  StackFrameStream stream(jt, false, false);

  // Search for the sampled interpreter frame and get its Method*.

  while (!stream.is_done()) {
    const frame* const frame = stream.current();
    assert(frame != nullptr, "invariant");
    const intptr_t* const real_fp = frame->real_fp();
    assert(real_fp != nullptr, "invariant");
    if (real_fp == sampled_fp && frame->is_interpreted_frame()) {
      Method* const method = frame->interpreter_frame_method();
      assert(method != nullptr, "invariant");
      request._sample_pc = method;
      // Got the Method*. Validate bcp.
      if (!method->is_native() &&  !method->contains(static_cast<address>(request._sample_bcp))) {
        request._sample_bcp = frame->interpreter_frame_bcp();
      }
      in_continuation = is_in_continuation(*frame, jt);
      break;
    }
    if (real_fp >= sampled_fp) {
      // What we sampled is not an official interpreter frame.
      // Invalidate the sample request and use current.
      request._sample_bcp = nullptr;
      sender_frame = *stream.current();
      in_continuation = is_in_continuation(sender_frame, jt);
      return true;
    }
    stream.next();
  }

  assert(!stream.is_done(), "invariant");

  // Step to sender.
  stream.next();

  // If the top frame is in a continuation, check that the sender frame is too.
  if (in_continuation && !is_in_continuation(*stream.current(), jt)) {
    // Leave sender frame empty.
    return true;
  }

  sender_frame = *stream.current();

  assert(request._sample_pc != nullptr, "invariant");
  assert(request._sample_bcp != nullptr, "invariant");
  assert(Method::is_valid_method(static_cast<const Method*>(request._sample_pc)), "invariant");
  assert(static_cast<const Method*>(request._sample_pc)->is_native() ||
         static_cast<const Method*>(request._sample_pc)->contains(static_cast<address>(request._sample_bcp)), "invariant");
  return true;
}

static inline const PcDesc* get_pc_desc(nmethod* nm, void* pc) {
  assert(nm != nullptr, "invariant");
  assert(pc != nullptr, "invariant");
  return nm->pc_desc_near(static_cast<address>(pc));
}

static inline bool is_valid(const PcDesc* pc_desc) {
  return pc_desc != nullptr && pc_desc->scope_decode_offset() != DebugInformationRecorder::serialized_null;
}

static bool compute_top_frame(const JfrSampleRequest& request, frame& top_frame, bool& in_continuation, JavaThread* jt, bool& biased) {
  assert(jt != nullptr, "invariant");

  if (!jt->has_last_Java_frame()) {
    return false;
  }

  if (is_interpreter(request)) {
    return compute_sender_frame(const_cast<JfrSampleRequest&>(request), top_frame, in_continuation, jt);
  }

  void* const sampled_pc = request._sample_pc;
  CodeBlob* sampled_cb;
  if (sampled_pc == nullptr || (sampled_cb = CodeCache::find_blob(sampled_pc)) == nullptr) {
    // A biased sample is requested or no code blob.
    top_frame = jt->last_frame();
    in_continuation = is_in_continuation(top_frame, jt);
    biased = true;
    return true;
  }

  // We will never describe a sample request that represents an unparsable stub or blob.
  assert(sampled_cb->frame_complete_offset() != CodeOffsets::frame_never_safe, "invariant");

  const void* const sampled_sp = request._sample_sp;
  assert(sampled_sp != nullptr, "invariant");

  nmethod* const sampled_nm = sampled_cb->as_nmethod_or_null();

  StackFrameStream stream(jt, false /* update registers */, false /* process frames */);

  if (stream.current()->is_safepoint_blob_frame()) {
    if (sampled_nm != nullptr) {
      // Move to the physical sender frame of the SafepointBlob stub frame using the frame size, not the logical iterator.
      const int safepoint_blob_stub_frame_size = stream.current()->cb()->frame_size();
      intptr_t* const sender_sp = stream.current()->unextended_sp() + safepoint_blob_stub_frame_size;
      if (sender_sp > sampled_sp) {
        const address saved_exception_pc = jt->saved_exception_pc();
        assert(saved_exception_pc != nullptr, "invariant");
        const nmethod* const exception_nm = CodeCache::find_blob(saved_exception_pc)->as_nmethod();
        assert(exception_nm != nullptr, "invariant");
        if (exception_nm == sampled_nm && sampled_nm->is_at_poll_return(saved_exception_pc)) {
          // We sit at the poll return site in the sampled compiled nmethod with only the return address on the stack.
          // The sampled_nm compiled frame is no longer extant, but we might be able to reconstruct a synthetic
          // compiled frame at this location. We do this by overlaying a reconstructed frame on top of
          // the huge SafepointBlob stub frame. Of course, the synthetic frame only contains random stack memory,
          // but it is safe because stack walking cares only about the form of the frame (i.e., an sp and a pc).
          // We also do not have to worry about stackbanging because we currently have a huge SafepointBlob stub frame
          // on the stack. For extra assurance, we know that we can create this frame size at this
          // very location because we just popped such a frame before we hit the return poll site.
          //
          // Let's attempt to correct for the safepoint bias.
          const PcDesc* const pc_desc = get_pc_desc(sampled_nm, sampled_pc);
          if (is_valid(pc_desc)) {
            intptr_t* const synthetic_sp = sender_sp - sampled_nm->frame_size();
            top_frame = frame(synthetic_sp, synthetic_sp, sender_sp, pc_desc->real_pc(sampled_nm), sampled_nm);
            in_continuation = is_in_continuation(top_frame, jt);
            return true;
          }
        }
      }
    }
    stream.next(); // skip the SafepointBlob stub frame
  }

  assert(!stream.current()->is_safepoint_blob_frame(), "invariant");

  biased = true;

  // Search the first frame that is above the sampled sp.
  for (; !stream.is_done(); stream.next()) {
    frame* const current = stream.current();

    if (current->real_fp() <= sampled_sp) {
      // Continue searching for a matching frame.
      continue;
    }

    if (sampled_nm == nullptr) {
      // The sample didn't have an nmethod; we decide to trace from its sender.
      // Another instance of safepoint bias.
      top_frame = *current;
      break;
    }

    // Check for a matching compiled method.
    if (current->cb()->as_nmethod_or_null() == sampled_nm) {
      if (current->pc() != sampled_pc) {
        // Let's adjust for the safepoint bias if we can.
        const PcDesc* const pc_desc = get_pc_desc(sampled_nm, sampled_pc);
        if (is_valid(pc_desc)) {
          current->adjust_pc(pc_desc->real_pc(sampled_nm));
          biased = false;
        }
      }
    }
    // Either a hit or a mismatched sample in which case we trace from the sender.
    // Yet another instance of safepoint bias,to be addressed with
    // more exact and stricter versions when parsable blobs become available.
    top_frame = *current;
    break;
  }

  in_continuation = is_in_continuation(top_frame, jt);
  return true;
}

static void record_thread_in_java(const JfrSampleRequest& request, const JfrTicks& now, const JfrThreadLocal* tl, JavaThread* jt, Thread* current) {
  assert(jt != nullptr, "invariant");
  assert(tl != nullptr, "invariant");
  assert(current != nullptr, "invariant");

  frame top_frame;
  bool biased = false;
  bool in_continuation;
  if (!compute_top_frame(request, top_frame, in_continuation, jt, biased)) {
    return;
  }

  traceid sid;
  {
    ResourceMark rm(current);
    JfrStackTrace stacktrace;
    if (!stacktrace.record(jt, top_frame, in_continuation, request)) {
      // Unable to record stacktrace. Fail.
      return;
    }
    sid = JfrStackTraceRepository::add(stacktrace);
  }
  assert(sid != 0, "invariant");
  const traceid tid = in_continuation ? tl->vthread_id_with_epoch_update(jt) : JfrThreadLocal::jvm_thread_id(jt);
  send_sample_event<EventExecutionSample>(request._sample_ticks, now, sid, tid);
  if (current == jt) {
    send_safepoint_latency_event(request, now, sid, jt);
  }
}

#ifdef LINUX
static void record_cpu_time_thread(const JfrCPUTimeSampleRequest& request, const JfrTicks& now, const JfrThreadLocal* tl, JavaThread* jt, Thread* current) {
  assert(jt != nullptr, "invariant");
  assert(tl != nullptr, "invariant");
  assert(current != nullptr, "invariant");
  frame top_frame;
  bool biased = false;
  bool in_continuation = false;
  bool could_compute_top_frame = compute_top_frame(request._request, top_frame, in_continuation, jt, biased);
  const traceid tid = in_continuation ? tl->vthread_id_with_epoch_update(jt) : JfrThreadLocal::jvm_thread_id(jt);

  if (!could_compute_top_frame) {
    JfrCPUTimeThreadSampling::send_empty_event(request._request._sample_ticks, tid, request._cpu_time_period);
    return;
  }
  traceid sid;
  {
    ResourceMark rm(current);
    JfrStackTrace stacktrace;
    if (!stacktrace.record(jt, top_frame, in_continuation, request._request)) {
      // Unable to record stacktrace. Fail.
      JfrCPUTimeThreadSampling::send_empty_event(request._request._sample_ticks, tid, request._cpu_time_period);
      return;
    }
    sid = JfrStackTraceRepository::add(stacktrace);
  }
  assert(sid != 0, "invariant");


  JfrCPUTimeThreadSampling::send_event(request._request._sample_ticks, sid, tid, request._cpu_time_period, biased);
  if (current == jt) {
    send_safepoint_latency_event(request._request, now, sid, jt);
  }
}
#endif

static void drain_enqueued_requests(const JfrTicks& now, JfrThreadLocal* tl, JavaThread* jt, Thread* current) {
  assert(tl != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(current != nullptr, "invariant");
  assert(jt->jfr_thread_local() == tl, "invariant");
  assert_lock_strong(tl->sample_monitor());
  if (tl->has_enqueued_requests()) {
    for (const JfrSampleRequest& request : *tl->sample_requests()) {
      record_thread_in_java(request, now, tl, jt, current);
    }
    tl->clear_enqueued_requests();
  }
  assert(!tl->has_enqueued_requests(), "invariant");
}

static void drain_enqueued_cpu_time_requests(const JfrTicks& now, JfrThreadLocal* tl, JavaThread* jt, Thread* current, bool lock) {
  assert(tl != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(current != nullptr, "invariant");
#ifdef LINUX
  tl->set_do_async_processing_of_cpu_time_jfr_requests(false);
  if (lock) {
    tl->acquire_cpu_time_jfr_dequeue_lock();
  }
  JfrCPUTimeTraceQueue& queue = tl->cpu_time_jfr_queue();
  for (u4 i = 0; i < queue.size(); i++) {
    record_cpu_time_thread(queue.at(i), now, tl, jt, current);
  }
  queue.clear();
  assert(queue.is_empty(), "invariant");
  tl->set_has_cpu_time_jfr_requests(false);
  if (queue.lost_samples() > 0) {
    JfrCPUTimeThreadSampling::send_lost_event( now, JfrThreadLocal::thread_id(jt), queue.get_and_reset_lost_samples());
  }
  if (lock) {
    tl->release_cpu_time_jfr_queue_lock();
  }
#endif
}

// Entry point for a thread that has been sampled in native code and has a pending JFR CPU time request.
void JfrThreadSampling::process_cpu_time_request(JavaThread* jt, JfrThreadLocal* tl, Thread* current, bool lock) {
  assert(jt != nullptr, "invariant");

  const JfrTicks now = JfrTicks::now();
  drain_enqueued_cpu_time_requests(now, tl, jt, current, lock);
}

static void drain_all_enqueued_requests(const JfrTicks& now, JfrThreadLocal* tl, JavaThread* jt, Thread* current) {
  assert(tl != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(current != nullptr, "invariant");
  drain_enqueued_requests(now, tl, jt, current);
  if (tl->has_cpu_time_jfr_requests()) {
    drain_enqueued_cpu_time_requests(now, tl, jt, current, true);
  }
}

// Only entered by the JfrSampler thread.
bool JfrThreadSampling::process_native_sample_request(JfrThreadLocal* tl, JavaThread* jt, Thread* sampler_thread) {
  assert(tl != nullptr, "invairant");
  assert(jt != nullptr, "invariant");
  assert(sampler_thread != nullptr, "invariant");
  assert(sampler_thread->is_JfrSampler_thread(), "invariant");
  assert(tl == jt->jfr_thread_local(), "invariant");
  assert(jt != sampler_thread, "only asynchronous processing of native samples");
  assert(jt->has_last_Java_frame(), "invariant");
  assert(tl->sample_state() >= NATIVE_SAMPLE, "invariant");

  assert_lock_strong(Threads_lock);

  const JfrTicks start_time = JfrTicks::now();

  traceid tid;
  traceid sid;

  {
    JfrSampleMonitor sm(tl);

    // Because the thread was in native, it is in a walkable state, because
    // it will hit a safepoint poll on the way back from native. To ensure timely
    // progress, any requests in the queue can be safely processed now.
    drain_enqueued_requests(start_time, tl, jt, sampler_thread);
    // Process the current stacktrace using the ljf.
    {
      ResourceMark rm(sampler_thread);
      JfrStackTrace stacktrace;
      const frame top_frame = jt->last_frame();
      if (!stacktrace.record_inner(jt, top_frame, is_in_continuation(top_frame, jt), 0 /* skip level */)) {
        // Unable to record stacktrace. Fail.
        return false;
      }
      sid = JfrStackTraceRepository::add(stacktrace);
    }
    // Read the tid under the monitor to ensure that if its a virtual thread,
    // it is not unmounted until we are done with it.
    tid = JfrThreadLocal::thread_id(jt);
  }

  assert(tl->sample_state() == NO_SAMPLE, "invariant");
  send_sample_event<EventNativeMethodSample>(start_time, start_time, sid, tid);
  return true;
}

// Entry point for a sampled thread that discovered pending Jfr Sample Requests as part of a safepoint poll.
void JfrThreadSampling::process_sample_request(JavaThread* jt) {
  assert(JavaThread::current() == jt, "should be current thread");
  assert(jt->thread_state() == _thread_in_vm || jt->thread_state() == _thread_in_Java, "invariant");

  const JfrTicks now = JfrTicks::now();

  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");

  MonitorLocker ml(tl->sample_monitor(), Monitor::_no_safepoint_check_flag);

  for (;;) {
    const int sample_state = tl->sample_state();
    if (sample_state == NATIVE_SAMPLE) {
      tl->set_sample_state(WAITING_FOR_NATIVE_SAMPLE);
      // Wait until stack trace is processed.
      ml.wait();
    } else if (sample_state == JAVA_SAMPLE) {
      tl->enqueue_request();
    } else if (sample_state == WAITING_FOR_NATIVE_SAMPLE) {
      // Handle spurious wakeups. Again wait until stack trace is processed.
      ml.wait();
    } else {
      // State has been processed.
      break;
    }
  }
  drain_all_enqueued_requests(now, tl, jt, jt);
}

