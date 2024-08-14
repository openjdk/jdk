/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/moduleEntry.hpp"
#include "code/codeCache.hpp"
#include "code/scopeDesc.hpp"
#include "code/vmreg.inline.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/disassembler.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/oopMapCache.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/oop.inline.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/monitorChunk.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/signature.hpp"
#include "runtime/stackValue.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/debug.hpp"
#include "utilities/decoder.hpp"
#include "utilities/formatBuffer.hpp"

RegisterMap::RegisterMap(JavaThread *thread, UpdateMap update_map, ProcessFrames process_frames, WalkContinuation walk_cont) {
  _thread         = thread;
  _update_map     = update_map == UpdateMap::include;
  _process_frames = process_frames == ProcessFrames::include;
  _walk_cont      = walk_cont == WalkContinuation::include;
  clear();
  DEBUG_ONLY (_update_for_id = nullptr;)
  NOT_PRODUCT(_skip_missing = false;)
  NOT_PRODUCT(_async = false;)

  if (walk_cont == WalkContinuation::include && thread != nullptr && thread->last_continuation() != nullptr) {
    _chunk = stackChunkHandle(Thread::current()->handle_area()->allocate_null_handle(), true /* dummy */);
  }
  _chunk_index = -1;

#ifndef PRODUCT
  for (int i = 0; i < reg_count ; i++ ) _location[i] = nullptr;
#endif /* PRODUCT */
}

RegisterMap::RegisterMap(oop continuation, UpdateMap update_map) {
  _thread         = nullptr;
  _update_map     = update_map == UpdateMap::include;
  _process_frames = false;
  _walk_cont      = true;
  clear();
  DEBUG_ONLY (_update_for_id = nullptr;)
  NOT_PRODUCT(_skip_missing = false;)
  NOT_PRODUCT(_async = false;)

  _chunk = stackChunkHandle(Thread::current()->handle_area()->allocate_null_handle(), true /* dummy */);
  _chunk_index = -1;

#ifndef PRODUCT
  for (int i = 0; i < reg_count ; i++ ) _location[i] = nullptr;
#endif /* PRODUCT */
}

RegisterMap::RegisterMap(const RegisterMap* map) {
  assert(map != this, "bad initialization parameter");
  assert(map != nullptr, "RegisterMap must be present");
  _thread                = map->thread();
  _update_map            = map->update_map();
  _process_frames        = map->process_frames();
  _walk_cont             = map->_walk_cont;
  _include_argument_oops = map->include_argument_oops();
  DEBUG_ONLY (_update_for_id = map->_update_for_id;)
  NOT_PRODUCT(_skip_missing = map->_skip_missing;)
  NOT_PRODUCT(_async = map->_async;)

  // only the original RegisterMap's handle lives long enough for StackWalker; this is bound to cause trouble with nested continuations.
  _chunk = map->_chunk;
  _chunk_index = map->_chunk_index;

  pd_initialize_from(map);
  if (update_map()) {
    for(int i = 0; i < location_valid_size; i++) {
      LocationValidType bits = map->_location_valid[i];
      _location_valid[i] = bits;
      // for whichever bits are set, pull in the corresponding map->_location
      int j = i*location_valid_type_size;
      while (bits != 0) {
        if ((bits & 1) != 0) {
          assert(0 <= j && j < reg_count, "range check");
          _location[j] = map->_location[j];
        }
        bits >>= 1;
        j += 1;
      }
    }
  }
}

oop RegisterMap::cont() const {
  return _chunk() != nullptr ? _chunk()->cont() : (oop)nullptr;
}

void RegisterMap::set_stack_chunk(stackChunkOop chunk) {
  assert(chunk == nullptr || _walk_cont, "");
  assert(chunk == nullptr || _chunk.not_null(), "");
  if (_chunk.is_null()) return;
  log_trace(continuations)("set_stack_chunk: " INTPTR_FORMAT " this: " INTPTR_FORMAT, p2i((oopDesc*)chunk), p2i(this));
  _chunk.replace(chunk); // reuse handle. see comment above in the constructor
  if (chunk == nullptr) {
    _chunk_index = -1;
  } else {
    _chunk_index++;
  }
}

void RegisterMap::clear() {
  set_include_argument_oops(true);
  if (update_map()) {
    for(int i = 0; i < location_valid_size; i++) {
      _location_valid[i] = 0;
    }
    pd_clear();
  } else {
    pd_initialize();
  }
}

#ifndef PRODUCT

VMReg RegisterMap::find_register_spilled_here(void* p, intptr_t* sp) {
  for(int i = 0; i < RegisterMap::reg_count; i++) {
    VMReg r = VMRegImpl::as_VMReg(i);
    if (p == location(r, sp)) return r;
  }
  return nullptr;
}

void RegisterMap::print_on(outputStream* st) const {
  st->print_cr("Register map");
  for(int i = 0; i < reg_count; i++) {

    VMReg r = VMRegImpl::as_VMReg(i);
    intptr_t* src = (intptr_t*) location(r, nullptr);
    if (src != nullptr) {

      r->print_on(st);
      st->print(" [" INTPTR_FORMAT "] = ", p2i(src));
      if (((uintptr_t)src & (sizeof(*src)-1)) != 0) {
        st->print_cr("<misaligned>");
      } else {
        st->print_cr(INTPTR_FORMAT, *src);
      }
    }
  }
}

void RegisterMap::print() const {
  print_on(tty);
}

#endif
// This returns the pc that if you were in the debugger you'd see. Not
// the idealized value in the frame object. This undoes the magic conversion
// that happens for deoptimized frames. In addition it makes the value the
// hardware would want to see in the native frame. The only user (at this point)
// is deoptimization. It likely no one else should ever use it.

address frame::raw_pc() const {
  if (is_deoptimized_frame()) {
    nmethod* nm = cb()->as_nmethod_or_null();
    assert(nm != nullptr, "only nmethod is expected here");
    if (nm->is_method_handle_return(pc()))
      return nm->deopt_mh_handler_begin() - pc_return_offset;
    else
      return nm->deopt_handler_begin() - pc_return_offset;
  } else {
    return (pc() - pc_return_offset);
  }
}

// Change the pc in a frame object. This does not change the actual pc in
// actual frame. To do that use patch_pc.
//
void frame::set_pc(address newpc) {
#ifdef ASSERT
  if (_cb != nullptr && _cb->is_nmethod()) {
    assert(!((nmethod*)_cb)->is_deopt_pc(_pc), "invariant violation");
  }
#endif // ASSERT

  // Unsafe to use the is_deoptimized tester after changing pc
  _deopt_state = unknown;
  _pc = newpc;
  _cb = CodeCache::find_blob(_pc);

}

// type testers
bool frame::is_ignored_frame() const {
  return false;  // FIXME: some LambdaForm frames should be ignored
}

bool frame::is_native_frame() const {
  return (_cb != nullptr &&
          _cb->is_nmethod() &&
          ((nmethod*)_cb)->is_native_method());
}

bool frame::is_java_frame() const {
  if (is_interpreted_frame()) return true;
  if (is_compiled_frame())    return true;
  return false;
}

bool frame::is_runtime_frame() const {
  return (_cb != nullptr && _cb->is_runtime_stub());
}

bool frame::is_safepoint_blob_frame() const {
  return (_cb != nullptr && _cb->is_safepoint_stub());
}

// testers

bool frame::is_first_java_frame() const {
  RegisterMap map(JavaThread::current(),
                  RegisterMap::UpdateMap::skip,
                  RegisterMap::ProcessFrames::include,
                  RegisterMap::WalkContinuation::skip); // No update
  frame s;
  for (s = sender(&map); !(s.is_java_frame() || s.is_first_frame()); s = s.sender(&map));
  return s.is_first_frame();
}

bool frame::is_first_vthread_frame(JavaThread* thread) const {
  return Continuation::is_continuation_enterSpecial(*this)
    && Continuation::get_continuation_entry_for_entry_frame(thread, *this)->is_virtual_thread();
}

bool frame::entry_frame_is_first() const {
  return entry_frame_call_wrapper()->is_first_frame();
}

JavaCallWrapper* frame::entry_frame_call_wrapper_if_safe(JavaThread* thread) const {
  JavaCallWrapper** jcw = entry_frame_call_wrapper_addr();
  address addr = (address) jcw;

  // addr must be within the usable part of the stack
  if (thread->is_in_usable_stack(addr)) {
    return *jcw;
  }

  return nullptr;
}

bool frame::is_entry_frame_valid(JavaThread* thread) const {
  // Validate the JavaCallWrapper an entry frame must have
  address jcw = (address)entry_frame_call_wrapper();
  if (!thread->is_in_stack_range_excl(jcw, (address)fp())) {
    return false;
  }

  // Validate sp saved in the java frame anchor
  JavaFrameAnchor* jfa = entry_frame_call_wrapper()->anchor();
  return (jfa->last_Java_sp() > sp());
}

Method* frame::safe_interpreter_frame_method() const {
  Method** m_addr = interpreter_frame_method_addr();
  if (m_addr == nullptr) {
    return nullptr;
  }
  return (Method*) SafeFetchN((intptr_t*) m_addr, 0);
}

bool frame::should_be_deoptimized() const {
  if (_deopt_state == is_deoptimized ||
      !is_compiled_frame() ) return false;
  assert(_cb != nullptr && _cb->is_nmethod(), "must be an nmethod");
  nmethod* nm = _cb->as_nmethod();
  LogTarget(Debug, dependencies) lt;
  if (lt.is_enabled()) {
    LogStream ls(&lt);
    ls.print("checking (%s) ", nm->is_marked_for_deoptimization() ? "true" : "false");
    nm->print_value_on(&ls);
    ls.cr();
  }

  if( !nm->is_marked_for_deoptimization() )
    return false;

  // If at the return point, then the frame has already been popped, and
  // only the return needs to be executed. Don't deoptimize here.
  return !nm->is_at_poll_return(pc());
}

bool frame::can_be_deoptimized() const {
  if (!is_compiled_frame()) return false;
  nmethod* nm = _cb->as_nmethod();

  if(!nm->can_be_deoptimized())
    return false;

  return !nm->is_at_poll_return(pc());
}

void frame::deoptimize(JavaThread* thread) {
  assert(thread == nullptr
         || (thread->frame_anchor()->has_last_Java_frame() &&
             thread->frame_anchor()->walkable()), "must be");
  // Schedule deoptimization of an nmethod activation with this frame.
  assert(_cb != nullptr && _cb->is_nmethod(), "must be");

  // If the call site is a MethodHandle call site use the MH deopt handler.
  nmethod* nm = _cb->as_nmethod();
  address deopt = nm->is_method_handle_return(pc()) ?
                        nm->deopt_mh_handler_begin() :
                        nm->deopt_handler_begin();

  NativePostCallNop* inst = nativePostCallNop_at(pc());

  // Save the original pc before we patch in the new one
  nm->set_original_pc(this, pc());
  patch_pc(thread, deopt);
  assert(is_deoptimized_frame(), "must be");

#ifdef ASSERT
  if (thread != nullptr) {
    frame check = thread->last_frame();
    if (is_older(check.id())) {
      RegisterMap map(thread,
                      RegisterMap::UpdateMap::skip,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
      while (id() != check.id()) {
        check = check.sender(&map);
      }
      assert(check.is_deoptimized_frame(), "missed deopt");
    }
  }
#endif // ASSERT
}

frame frame::java_sender() const {
  RegisterMap map(JavaThread::current(),
                  RegisterMap::UpdateMap::skip,
                  RegisterMap::ProcessFrames::include,
                  RegisterMap::WalkContinuation::skip);
  frame s;
  for (s = sender(&map); !(s.is_java_frame() || s.is_first_frame()); s = s.sender(&map)) ;
  guarantee(s.is_java_frame(), "tried to get caller of first java frame");
  return s;
}

frame frame::real_sender(RegisterMap* map) const {
  frame result = sender(map);
  while (result.is_runtime_frame() ||
         result.is_ignored_frame()) {
    result = result.sender(map);
  }
  return result;
}

// Interpreter frames


Method* frame::interpreter_frame_method() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  Method* m = *interpreter_frame_method_addr();
  assert(m->is_method(), "not a Method*");
  return m;
}

void frame::interpreter_frame_set_method(Method* method) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  *interpreter_frame_method_addr() = method;
}

void frame::interpreter_frame_set_mirror(oop mirror) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  *interpreter_frame_mirror_addr() = mirror;
}

jint frame::interpreter_frame_bci() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  address bcp = interpreter_frame_bcp();
  return interpreter_frame_method()->bci_from(bcp);
}

address frame::interpreter_frame_bcp() const {
  assert(is_interpreted_frame(), "interpreted frame expected");
  address bcp = (address)*interpreter_frame_bcp_addr();
  return interpreter_frame_method()->bcp_from(bcp);
}

void frame::interpreter_frame_set_bcp(address bcp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  *interpreter_frame_bcp_addr() = (intptr_t)bcp;
}

address frame::interpreter_frame_mdp() const {
  assert(ProfileInterpreter, "must be profiling interpreter");
  assert(is_interpreted_frame(), "interpreted frame expected");
  return (address)*interpreter_frame_mdp_addr();
}

void frame::interpreter_frame_set_mdp(address mdp) {
  assert(is_interpreted_frame(), "interpreted frame expected");
  assert(ProfileInterpreter, "must be profiling interpreter");
  *interpreter_frame_mdp_addr() = (intptr_t)mdp;
}

BasicObjectLock* frame::next_monitor_in_interpreter_frame(BasicObjectLock* current) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
#ifdef ASSERT
  interpreter_frame_verify_monitor(current);
#endif
  BasicObjectLock* next = (BasicObjectLock*) (((intptr_t*) current) + interpreter_frame_monitor_size());
  return next;
}

BasicObjectLock* frame::previous_monitor_in_interpreter_frame(BasicObjectLock* current) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
#ifdef ASSERT
//   // This verification needs to be checked before being enabled
//   interpreter_frame_verify_monitor(current);
#endif
  BasicObjectLock* previous = (BasicObjectLock*) (((intptr_t*) current) - interpreter_frame_monitor_size());
  return previous;
}

// Interpreter locals and expression stack locations.

intptr_t* frame::interpreter_frame_local_at(int index) const {
  const int n = Interpreter::local_offset_in_bytes(index)/wordSize;
  intptr_t* first = interpreter_frame_locals();
  return &(first[n]);
}

intptr_t* frame::interpreter_frame_expression_stack_at(jint offset) const {
  const int i = offset * interpreter_frame_expression_stack_direction();
  const int n = i * Interpreter::stackElementWords;
  return &(interpreter_frame_expression_stack()[n]);
}

jint frame::interpreter_frame_expression_stack_size() const {
  // Number of elements on the interpreter expression stack
  // Callers should span by stackElementWords
  int element_size = Interpreter::stackElementWords;
  size_t stack_size = 0;
  if (frame::interpreter_frame_expression_stack_direction() < 0) {
    stack_size = (interpreter_frame_expression_stack() -
                  interpreter_frame_tos_address() + 1)/element_size;
  } else {
    stack_size = (interpreter_frame_tos_address() -
                  interpreter_frame_expression_stack() + 1)/element_size;
  }
  assert(stack_size <= (size_t)max_jint, "stack size too big");
  return (jint)stack_size;
}


// (frame::interpreter_frame_sender_sp accessor is in frame_<arch>.cpp)

const char* frame::print_name() const {
  if (is_native_frame())      return "Native";
  if (is_interpreted_frame()) return "Interpreted";
  if (is_compiled_frame()) {
    if (is_deoptimized_frame()) return "Deoptimized";
    return "Compiled";
  }
  if (sp() == nullptr)            return "Empty";
  return "C";
}

void frame::print_value_on(outputStream* st, JavaThread *thread) const {
  NOT_PRODUCT(address begin = pc()-40;)
  NOT_PRODUCT(address end   = nullptr;)

  st->print("%s frame (sp=" INTPTR_FORMAT " unextended sp=" INTPTR_FORMAT, print_name(), p2i(sp()), p2i(unextended_sp()));
  if (sp() != nullptr)
    st->print(", fp=" INTPTR_FORMAT ", real_fp=" INTPTR_FORMAT ", pc=" INTPTR_FORMAT,
              p2i(fp()), p2i(real_fp()), p2i(pc()));
  st->print_cr(")");

  if (StubRoutines::contains(pc())) {
    StubCodeDesc* desc = StubCodeDesc::desc_for(pc());
    st->print("~Stub::%s", desc->name());
    NOT_PRODUCT(begin = desc->begin(); end = desc->end();)
  } else if (Interpreter::contains(pc())) {
    InterpreterCodelet* desc = Interpreter::codelet_containing(pc());
    if (desc != nullptr) {
      st->print("~");
      desc->print_on(st);
      NOT_PRODUCT(begin = desc->code_begin(); end = desc->code_end();)
    } else {
      st->print("~interpreter");
    }
  }

#ifndef PRODUCT
  if (_cb != nullptr) {
    st->print("     ");
    _cb->print_value_on(st);
    if (end == nullptr) {
      begin = _cb->code_begin();
      end   = _cb->code_end();
    }
  }
  if (WizardMode && Verbose) Disassembler::decode(begin, end);
#endif
}

void frame::print_on(outputStream* st) const {
  print_value_on(st,nullptr);
  if (is_interpreted_frame()) {
    interpreter_frame_print_on(st);
  }
}

void frame::interpreter_frame_print_on(outputStream* st) const {
#ifndef PRODUCT
  assert(is_interpreted_frame(), "Not an interpreted frame");
  jint i;
  for (i = 0; i < interpreter_frame_method()->max_locals(); i++ ) {
    intptr_t x = *interpreter_frame_local_at(i);
    st->print(" - local  [" INTPTR_FORMAT "]", x);
    st->fill_to(23);
    st->print_cr("; #%d", i);
  }
  for (i = interpreter_frame_expression_stack_size() - 1; i >= 0; --i ) {
    intptr_t x = *interpreter_frame_expression_stack_at(i);
    st->print(" - stack  [" INTPTR_FORMAT "]", x);
    st->fill_to(23);
    st->print_cr("; #%d", i);
  }
  // locks for synchronization
  for (BasicObjectLock* current = interpreter_frame_monitor_end();
       current < interpreter_frame_monitor_begin();
       current = next_monitor_in_interpreter_frame(current)) {
    st->print(" - obj    [%s", current->obj() == nullptr ? "null" : "");
    if (current->obj() != nullptr) current->obj()->print_value_on(st);
    st->print_cr("]");
    st->print(" - lock   [");
    current->lock()->print_on(st, current->obj());
    st->print_cr("]");
  }
  // monitor
  st->print_cr(" - monitor[" INTPTR_FORMAT "]", p2i(interpreter_frame_monitor_begin()));
  // bcp
  st->print(" - bcp    [" INTPTR_FORMAT "]", p2i(interpreter_frame_bcp()));
  st->fill_to(23);
  st->print_cr("; @%d", interpreter_frame_bci());
  // locals
  st->print_cr(" - locals [" INTPTR_FORMAT "]", p2i(interpreter_frame_local_at(0)));
  // method
  st->print(" - method [" INTPTR_FORMAT "]", p2i(interpreter_frame_method()));
  st->fill_to(23);
  st->print("; ");
  interpreter_frame_method()->print_name(st);
  st->cr();
#endif
}

// Print whether the frame is in the VM or OS indicating a HotSpot problem.
// Otherwise, it's likely a bug in the native library that the Java code calls,
// hopefully indicating where to submit bugs.
void frame::print_C_frame(outputStream* st, char* buf, int buflen, address pc) {
  // C/C++ frame
  bool in_vm = os::address_is_in_vm(pc);
  st->print(in_vm ? "V" : "C");

  int offset;
  bool found;

  if (buf == nullptr || buflen < 1) return;
  // libname
  buf[0] = '\0';
  found = os::dll_address_to_library_name(pc, buf, buflen, &offset);
  if (found && buf[0] != '\0') {
    // skip directory names
    const char *p1, *p2;
    p1 = buf;
    int len = (int)strlen(os::file_separator());
    while ((p2 = strstr(p1, os::file_separator())) != nullptr) p1 = p2 + len;
    st->print("  [%s+0x%x]", p1, offset);
  } else {
    st->print("  " PTR_FORMAT, p2i(pc));
  }

  found = os::dll_address_to_function_name(pc, buf, buflen, &offset);
  if (found) {
    st->print("  %s+0x%x", buf, offset);
  }
}

// frame::print_on_error() is called by fatal error handler. Notice that we may
// crash inside this function if stack frame is corrupted. The fatal error
// handler can catch and handle the crash. Here we assume the frame is valid.
//
// First letter indicates type of the frame:
//    J: Java frame (compiled)
//    j: Java frame (interpreted)
//    V: VM frame (C/C++)
//    v: Other frames running VM generated code (e.g. stubs, adapters, etc.)
//    C: C/C++ frame
//
// We don't need detailed frame type as that in frame::print_name(). "C"
// suggests the problem is in user lib; everything else is likely a VM bug.

void frame::print_on_error(outputStream* st, char* buf, int buflen, bool verbose) const {
  if (_cb != nullptr) {
    if (Interpreter::contains(pc())) {
      Method* m = this->interpreter_frame_method();
      if (m != nullptr) {
        m->name_and_sig_as_C_string(buf, buflen);
        st->print("j  %s", buf);
        st->print("+%d", this->interpreter_frame_bci());
        ModuleEntry* module = m->method_holder()->module();
        if (module->is_named()) {
          module->name()->as_C_string(buf, buflen);
          st->print(" %s", buf);
          if (module->version() != nullptr) {
            module->version()->as_C_string(buf, buflen);
            st->print("@%s", buf);
          }
        }
      } else {
        st->print("j  " PTR_FORMAT, p2i(pc()));
      }
    } else if (StubRoutines::contains(pc())) {
      StubCodeDesc* desc = StubCodeDesc::desc_for(pc());
      if (desc != nullptr) {
        st->print("v  ~StubRoutines::%s " PTR_FORMAT, desc->name(), p2i(pc()));
      } else {
        st->print("v  ~StubRoutines::" PTR_FORMAT, p2i(pc()));
      }
    } else if (_cb->is_buffer_blob()) {
      st->print("v  ~BufferBlob::%s " PTR_FORMAT, ((BufferBlob *)_cb)->name(), p2i(pc()));
    } else if (_cb->is_nmethod()) {
      nmethod* nm = _cb->as_nmethod();
      Method* m = nm->method();
      if (m != nullptr) {
        st->print("J %d%s", nm->compile_id(), (nm->is_osr_method() ? "%" : ""));
        st->print(" %s", nm->compiler_name());
        m->name_and_sig_as_C_string(buf, buflen);
        st->print(" %s", buf);
        ModuleEntry* module = m->method_holder()->module();
        if (module->is_named()) {
          module->name()->as_C_string(buf, buflen);
          st->print(" %s", buf);
          if (module->version() != nullptr) {
            module->version()->as_C_string(buf, buflen);
            st->print("@%s", buf);
          }
        }
        st->print(" (%d bytes) @ " PTR_FORMAT " [" PTR_FORMAT "+" INTPTR_FORMAT "]",
                  m->code_size(), p2i(_pc), p2i(_cb->code_begin()), _pc - _cb->code_begin());
#if INCLUDE_JVMCI
        const char* jvmciName = nm->jvmci_name();
        if (jvmciName != nullptr) {
          st->print(" (%s)", jvmciName);
        }
#endif
      } else {
        st->print("J  " PTR_FORMAT, p2i(pc()));
      }
    } else if (_cb->is_runtime_stub()) {
      st->print("v  ~RuntimeStub::%s " PTR_FORMAT, ((RuntimeStub *)_cb)->name(), p2i(pc()));
    } else if (_cb->is_deoptimization_stub()) {
      st->print("v  ~DeoptimizationBlob " PTR_FORMAT, p2i(pc()));
    } else if (_cb->is_exception_stub()) {
      st->print("v  ~ExceptionBlob " PTR_FORMAT, p2i(pc()));
    } else if (_cb->is_safepoint_stub()) {
      st->print("v  ~SafepointBlob " PTR_FORMAT, p2i(pc()));
    } else if (_cb->is_adapter_blob()) {
      st->print("v  ~AdapterBlob " PTR_FORMAT, p2i(pc()));
    } else if (_cb->is_vtable_blob()) {
      st->print("v  ~VtableBlob " PTR_FORMAT, p2i(pc()));
    } else if (_cb->is_method_handles_adapter_blob()) {
      st->print("v  ~MethodHandlesAdapterBlob " PTR_FORMAT, p2i(pc()));
    } else if (_cb->is_uncommon_trap_stub()) {
      st->print("v  ~UncommonTrapBlob " PTR_FORMAT, p2i(pc()));
    } else {
      st->print("v  blob " PTR_FORMAT, p2i(pc()));
    }
  } else {
    print_C_frame(st, buf, buflen, pc());
  }
}


/*
  The interpreter_frame_expression_stack_at method in the case of SPARC needs the
  max_stack value of the method in order to compute the expression stack address.
  It uses the Method* in order to get the max_stack value but during GC this
  Method* value saved on the frame is changed by reverse_and_push and hence cannot
  be used. So we save the max_stack value in the FrameClosure object and pass it
  down to the interpreter_frame_expression_stack_at method
*/
class InterpreterFrameClosure : public OffsetClosure {
 private:
  const frame* _fr;
  OopClosure*  _f;
  int          _max_locals;
  int          _max_stack;

 public:
  InterpreterFrameClosure(const frame* fr, int max_locals, int max_stack,
                          OopClosure* f) {
    _fr         = fr;
    _max_locals = max_locals;
    _max_stack  = max_stack;
    _f          = f;
  }

  void offset_do(int offset) {
    oop* addr;
    if (offset < _max_locals) {
      addr = (oop*) _fr->interpreter_frame_local_at(offset);
      assert((intptr_t*)addr >= _fr->sp(), "must be inside the frame");
      _f->do_oop(addr);
    } else {
      addr = (oop*) _fr->interpreter_frame_expression_stack_at((offset - _max_locals));
      // In case of exceptions, the expression stack is invalid and the esp will be reset to express
      // this condition. Therefore, we call f only if addr is 'inside' the stack (i.e., addr >= esp for Intel).
      bool in_stack;
      if (frame::interpreter_frame_expression_stack_direction() > 0) {
        in_stack = (intptr_t*)addr <= _fr->interpreter_frame_tos_address();
      } else {
        in_stack = (intptr_t*)addr >= _fr->interpreter_frame_tos_address();
      }
      if (in_stack) {
        _f->do_oop(addr);
      }
    }
  }
};


class InterpretedArgumentOopFinder: public SignatureIterator {
 private:
  OopClosure*  _f;             // Closure to invoke
  int          _offset;        // TOS-relative offset, decremented with each argument
  bool         _has_receiver;  // true if the callee has a receiver
  const frame* _fr;

  friend class SignatureIterator;  // so do_parameters_on can call do_type
  void do_type(BasicType type) {
    _offset -= parameter_type_word_count(type);
    if (is_reference_type(type)) oop_offset_do();
   }

  void oop_offset_do() {
    oop* addr;
    addr = (oop*)_fr->interpreter_frame_tos_at(_offset);
    _f->do_oop(addr);
  }

 public:
  InterpretedArgumentOopFinder(Symbol* signature, bool has_receiver, const frame* fr, OopClosure* f) : SignatureIterator(signature), _has_receiver(has_receiver) {
    // compute size of arguments
    int args_size = ArgumentSizeComputer(signature).size() + (has_receiver ? 1 : 0);
    assert(!fr->is_interpreted_frame() ||
           args_size <= fr->interpreter_frame_expression_stack_size(),
            "args cannot be on stack anymore");
    // initialize InterpretedArgumentOopFinder
    _f         = f;
    _fr        = fr;
    _offset    = args_size;
  }

  void oops_do() {
    if (_has_receiver) {
      --_offset;
      oop_offset_do();
    }
    do_parameters_on(this);
  }
};


// Entry frame has following form (n arguments)
//         +-----------+
//   sp -> |  last arg |
//         +-----------+
//         :    :::    :
//         +-----------+
// (sp+n)->|  first arg|
//         +-----------+



// visits and GC's all the arguments in entry frame
class EntryFrameOopFinder: public SignatureIterator {
 private:
  bool         _is_static;
  int          _offset;
  const frame* _fr;
  OopClosure*  _f;

  friend class SignatureIterator;  // so do_parameters_on can call do_type
  void do_type(BasicType type) {
    // decrement offset before processing the type
    _offset -= parameter_type_word_count(type);
    assert (_offset >= 0, "illegal offset");
    if (is_reference_type(type))  oop_at_offset_do(_offset);
 }

  void oop_at_offset_do(int offset) {
    assert (offset >= 0, "illegal offset");
    oop* addr = (oop*) _fr->entry_frame_argument_at(offset);
    _f->do_oop(addr);
  }

 public:
  EntryFrameOopFinder(const frame* frame, Symbol* signature, bool is_static) : SignatureIterator(signature) {
    _f = nullptr; // will be set later
    _fr = frame;
    _is_static = is_static;
    _offset = ArgumentSizeComputer(signature).size();  // pre-decremented down to zero
  }

  void arguments_do(OopClosure* f) {
    _f = f;
    if (!_is_static)  oop_at_offset_do(_offset); // do the receiver
    do_parameters_on(this);
  }

};

oop* frame::interpreter_callee_receiver_addr(Symbol* signature) {
  ArgumentSizeComputer asc(signature);
  int size = asc.size();
  return (oop *)interpreter_frame_tos_at(size);
}

oop frame::interpreter_callee_receiver(Symbol* signature) {
  return *interpreter_callee_receiver_addr(signature);
}

void frame::oops_interpreted_do(OopClosure* f, const RegisterMap* map, bool query_oop_map_cache) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  Thread *thread = Thread::current();
  methodHandle m (thread, interpreter_frame_method());
  jint      bci = interpreter_frame_bci();

  assert(!Universe::heap()->is_in(m()),
          "must be valid oop");
  assert(m->is_method(), "checking frame value");
  assert((m->is_native() && bci == 0)  ||
         (!m->is_native() && bci >= 0 && bci < m->code_size()),
         "invalid bci value");

  // Handle the monitor elements in the activation
  for (
    BasicObjectLock* current = interpreter_frame_monitor_end();
    current < interpreter_frame_monitor_begin();
    current = next_monitor_in_interpreter_frame(current)
  ) {
#ifdef ASSERT
    interpreter_frame_verify_monitor(current);
#endif
    current->oops_do(f);
  }

  if (m->is_native()) {
    f->do_oop(interpreter_frame_temp_oop_addr());
  }

  // The method pointer in the frame might be the only path to the method's
  // klass, and the klass needs to be kept alive while executing. The GCs
  // don't trace through method pointers, so the mirror of the method's klass
  // is installed as a GC root.
  f->do_oop(interpreter_frame_mirror_addr());

  int max_locals = m->is_native() ? m->size_of_parameters() : m->max_locals();

  Symbol* signature = nullptr;
  bool has_receiver = false;

  // Process a callee's arguments if we are at a call site
  // (i.e., if we are at an invoke bytecode)
  // This is used sometimes for calling into the VM, not for another
  // interpreted or compiled frame.
  if (!m->is_native()) {
    Bytecode_invoke call = Bytecode_invoke_check(m, bci);
    if (map != nullptr && call.is_valid()) {
      signature = call.signature();
      has_receiver = call.has_receiver();
      if (map->include_argument_oops() &&
          interpreter_frame_expression_stack_size() > 0) {
        ResourceMark rm(thread);  // is this right ???
        // we are at a call site & the expression stack is not empty
        // => process callee's arguments
        //
        // Note: The expression stack can be empty if an exception
        //       occurred during method resolution/execution. In all
        //       cases we empty the expression stack completely be-
        //       fore handling the exception (the exception handling
        //       code in the interpreter calls a blocking runtime
        //       routine which can cause this code to be executed).
        //       (was bug gri 7/27/98)
        oops_interpreted_arguments_do(signature, has_receiver, f);
      }
    }
  }

  InterpreterFrameClosure blk(this, max_locals, m->max_stack(), f);

  // process locals & expression stack
  InterpreterOopMap mask;
  if (query_oop_map_cache) {
    m->mask_for(m, bci, &mask);
  } else {
    OopMapCache::compute_one_oop_map(m, bci, &mask);
  }
  mask.iterate_oop(&blk);
}


void frame::oops_interpreted_arguments_do(Symbol* signature, bool has_receiver, OopClosure* f) const {
  InterpretedArgumentOopFinder finder(signature, has_receiver, this, f);
  finder.oops_do();
}

void frame::oops_nmethod_do(OopClosure* f, NMethodClosure* cf, DerivedOopClosure* df, DerivedPointerIterationMode derived_mode, const RegisterMap* reg_map) const {
  assert(_cb != nullptr, "sanity check");
  assert((oop_map() == nullptr) == (_cb->oop_maps() == nullptr), "frame and _cb must agree that oopmap is set or not");
  if (oop_map() != nullptr) {
    if (df != nullptr) {
      _oop_map->oops_do(this, reg_map, f, df);
    } else {
      _oop_map->oops_do(this, reg_map, f, derived_mode);
    }

    // Preserve potential arguments for a callee. We handle this by dispatching
    // on the codeblob. For c2i, we do
    if (reg_map->include_argument_oops() && _cb->is_nmethod()) {
      // Only nmethod preserves outgoing arguments at call.
      _cb->as_nmethod()->preserve_callee_argument_oops(*this, reg_map, f);
    }
  }
  // In cases where perm gen is collected, GC will want to mark
  // oops referenced from nmethods active on thread stacks so as to
  // prevent them from being collected. However, this visit should be
  // restricted to certain phases of the collection only. The
  // closure decides how it wants nmethods to be traced.
  if (cf != nullptr && _cb->is_nmethod())
    cf->do_nmethod(_cb->as_nmethod());
}

class CompiledArgumentOopFinder: public SignatureIterator {
 protected:
  OopClosure*     _f;
  int             _offset;        // the current offset, incremented with each argument
  bool            _has_receiver;  // true if the callee has a receiver
  bool            _has_appendix;  // true if the call has an appendix
  frame           _fr;
  RegisterMap*    _reg_map;
  int             _arg_size;
  VMRegPair*      _regs;        // VMReg list of arguments

  friend class SignatureIterator;  // so do_parameters_on can call do_type
  void do_type(BasicType type) {
    if (is_reference_type(type))  handle_oop_offset();
    _offset += parameter_type_word_count(type);
  }

  virtual void handle_oop_offset() {
    // Extract low order register number from register array.
    // In LP64-land, the high-order bits are valid but unhelpful.
    VMReg reg = _regs[_offset].first();
    oop *loc = _fr.oopmapreg_to_oop_location(reg, _reg_map);
  #ifdef ASSERT
    if (loc == nullptr) {
      if (_reg_map->should_skip_missing()) {
        return;
      }
      tty->print_cr("Error walking frame oops:");
      _fr.print_on(tty);
      assert(loc != nullptr, "missing register map entry reg: %d %s loc: " INTPTR_FORMAT, reg->value(), reg->name(), p2i(loc));
    }
  #endif
    _f->do_oop(loc);
  }

 public:
  CompiledArgumentOopFinder(Symbol* signature, bool has_receiver, bool has_appendix, OopClosure* f, frame fr, const RegisterMap* reg_map)
    : SignatureIterator(signature) {

    // initialize CompiledArgumentOopFinder
    _f         = f;
    _offset    = 0;
    _has_receiver = has_receiver;
    _has_appendix = has_appendix;
    _fr        = fr;
    _reg_map   = (RegisterMap*)reg_map;
    _arg_size  = ArgumentSizeComputer(signature).size() + (has_receiver ? 1 : 0) + (has_appendix ? 1 : 0);

    int arg_size;
    _regs = SharedRuntime::find_callee_arguments(signature, has_receiver, has_appendix, &arg_size);
    assert(arg_size == _arg_size, "wrong arg size");
  }

  void oops_do() {
    if (_has_receiver) {
      handle_oop_offset();
      _offset++;
    }
    do_parameters_on(this);
    if (_has_appendix) {
      handle_oop_offset();
      _offset++;
    }
  }
};

void frame::oops_compiled_arguments_do(Symbol* signature, bool has_receiver, bool has_appendix,
                                       const RegisterMap* reg_map, OopClosure* f) const {
  // ResourceMark rm;
  CompiledArgumentOopFinder finder(signature, has_receiver, has_appendix, f, *this, reg_map);
  finder.oops_do();
}

// Get receiver out of callers frame, i.e. find parameter 0 in callers
// frame.  Consult ADLC for where parameter 0 is to be found.  Then
// check local reg_map for it being a callee-save register or argument
// register, both of which are saved in the local frame.  If not found
// there, it must be an in-stack argument of the caller.
// Note: caller.sp() points to callee-arguments
oop frame::retrieve_receiver(RegisterMap* reg_map) {
  frame caller = *this;

  // First consult the ADLC on where it puts parameter 0 for this signature.
  VMReg reg = SharedRuntime::name_for_receiver();
  oop* oop_adr = caller.oopmapreg_to_oop_location(reg, reg_map);
  if (oop_adr == nullptr) {
    guarantee(oop_adr != nullptr, "bad register save location");
    return nullptr;
  }
  oop r = *oop_adr;
  assert(Universe::heap()->is_in_or_null(r), "bad receiver: " INTPTR_FORMAT " (" INTX_FORMAT ")", p2i(r), p2i(r));
  return r;
}


BasicLock* frame::get_native_monitor() {
  nmethod* nm = (nmethod*)_cb;
  assert(_cb != nullptr && _cb->is_nmethod() && nm->method()->is_native(),
         "Should not call this unless it's a native nmethod");
  int byte_offset = in_bytes(nm->native_basic_lock_sp_offset());
  assert(byte_offset >= 0, "should not see invalid offset");
  return (BasicLock*) &sp()[byte_offset / wordSize];
}

oop frame::get_native_receiver() {
  nmethod* nm = (nmethod*)_cb;
  assert(_cb != nullptr && _cb->is_nmethod() && nm->method()->is_native(),
         "Should not call this unless it's a native nmethod");
  int byte_offset = in_bytes(nm->native_receiver_sp_offset());
  assert(byte_offset >= 0, "should not see invalid offset");
  oop owner = ((oop*) sp())[byte_offset / wordSize];
  assert( Universe::heap()->is_in(owner), "bad receiver" );
  return owner;
}

void frame::oops_entry_do(OopClosure* f, const RegisterMap* map) const {
  assert(map != nullptr, "map must be set");
  if (map->include_argument_oops()) {
    // must collect argument oops, as nobody else is doing it
    Thread *thread = Thread::current();
    methodHandle m (thread, entry_frame_call_wrapper()->callee_method());
    EntryFrameOopFinder finder(this, m->signature(), m->is_static());
    finder.arguments_do(f);
  }
  // Traverse the Handle Block saved in the entry frame
  entry_frame_call_wrapper()->oops_do(f);
}

bool frame::is_deoptimized_frame() const {
  assert(_deopt_state != unknown, "not answerable");
  if (_deopt_state == is_deoptimized) {
    return true;
  }

  /* This method only checks if the frame is deoptimized
   * as in return address being patched.
   * It doesn't care if the OP that we return to is a
   * deopt instruction */
  /*if (_cb != nullptr && _cb->is_nmethod()) {
    return NativeDeoptInstruction::is_deopt_at(_pc);
  }*/
  return false;
}

void frame::oops_do_internal(OopClosure* f, NMethodClosure* cf,
                             DerivedOopClosure* df, DerivedPointerIterationMode derived_mode,
                             const RegisterMap* map, bool use_interpreter_oop_map_cache) const {
#ifndef PRODUCT
  // simulate GC crash here to dump java thread in error report
  if (CrashGCForDumpingJavaThread) {
    char *t = nullptr;
    *t = 'c';
  }
#endif
  if (is_interpreted_frame()) {
    oops_interpreted_do(f, map, use_interpreter_oop_map_cache);
  } else if (is_entry_frame()) {
    oops_entry_do(f, map);
  } else if (is_upcall_stub_frame()) {
    _cb->as_upcall_stub()->oops_do(f, *this);
  } else if (CodeCache::contains(pc())) {
    oops_nmethod_do(f, cf, df, derived_mode, map);
  } else {
    ShouldNotReachHere();
  }
}

void frame::nmethod_do(NMethodClosure* cf) const {
  if (_cb != nullptr && _cb->is_nmethod()) {
    cf->do_nmethod(_cb->as_nmethod());
  }
}


// Call f closure on the interpreted Method*s in the stack.
void frame::metadata_do(MetadataClosure* f) const {
  ResourceMark rm;
  if (is_interpreted_frame()) {
    Method* m = this->interpreter_frame_method();
    assert(m != nullptr, "expecting a method in this frame");
    f->do_metadata(m);
  }
}

void frame::verify(const RegisterMap* map) const {
#ifndef PRODUCT
  if (TraceCodeBlobStacks) {
    tty->print_cr("*** verify");
    print_on(tty);
  }
#endif

  // for now make sure receiver type is correct
  if (is_interpreted_frame()) {
    Method* method = interpreter_frame_method();
    guarantee(method->is_method(), "method is wrong in frame::verify");
    if (!method->is_static()) {
      // fetch the receiver
      oop* p = (oop*) interpreter_frame_local_at(0);
      // make sure we have the right receiver type
    }
  }
#if COMPILER2_OR_JVMCI
  assert(DerivedPointerTable::is_empty(), "must be empty before verify");
#endif

  if (map->update_map()) { // The map has to be up-to-date for the current frame
    oops_do_internal(&VerifyOopClosure::verify_oop, nullptr, nullptr, DerivedPointerIterationMode::_ignore, map, false);
  }
}


#ifdef ASSERT
bool frame::verify_return_pc(address x) {
#ifdef TARGET_ARCH_aarch64
  if (!pauth_ptr_is_raw(x)) {
    return false;
  }
#endif
  if (StubRoutines::returns_to_call_stub(x)) {
    return true;
  }
  if (CodeCache::contains(x)) {
    return true;
  }
  if (Interpreter::contains(x)) {
    return true;
  }
  return false;
}
#endif

#ifdef ASSERT
void frame::interpreter_frame_verify_monitor(BasicObjectLock* value) const {
  assert(is_interpreted_frame(), "Not an interpreted frame");
  // verify that the value is in the right part of the frame
  address low_mark  = (address) interpreter_frame_monitor_end();
  address high_mark = (address) interpreter_frame_monitor_begin();
  address current   = (address) value;

  const int monitor_size = frame::interpreter_frame_monitor_size();
  guarantee((high_mark - current) % monitor_size  ==  0         , "Misaligned top of BasicObjectLock*");
  guarantee( high_mark > current                                , "Current BasicObjectLock* higher than high_mark");

  guarantee((current - low_mark) % monitor_size  ==  0         , "Misaligned bottom of BasicObjectLock*");
  guarantee( current >= low_mark                               , "Current BasicObjectLock* below than low_mark");
}
#endif

#ifndef PRODUCT

// Returns true iff the address p is readable and *(intptr_t*)p != errvalue
extern "C" bool dbg_is_safe(const void* p, intptr_t errvalue);

class FrameValuesOopClosure: public OopClosure, public DerivedOopClosure {
private:
  GrowableArray<oop*>* _oops;
  GrowableArray<narrowOop*>* _narrow_oops;
  GrowableArray<derived_base*>* _base;
  GrowableArray<derived_pointer*>* _derived;
  NoSafepointVerifier nsv;

public:
  FrameValuesOopClosure() {
    _oops = new (mtThread) GrowableArray<oop*>(100, mtThread);
    _narrow_oops = new (mtThread) GrowableArray<narrowOop*>(100, mtThread);
    _base = new (mtThread) GrowableArray<derived_base*>(100, mtThread);
    _derived = new (mtThread) GrowableArray<derived_pointer*>(100, mtThread);
  }
  ~FrameValuesOopClosure() {
    delete _oops;
    delete _narrow_oops;
    delete _base;
    delete _derived;
  }

  virtual void do_oop(oop* p) override { _oops->push(p); }
  virtual void do_oop(narrowOop* p) override { _narrow_oops->push(p); }
  virtual void do_derived_oop(derived_base* base_loc, derived_pointer* derived_loc) override {
    _base->push(base_loc);
    _derived->push(derived_loc);
  }

  bool is_good(oop* p) {
    return *p == nullptr || (dbg_is_safe(*p, -1) && dbg_is_safe((*p)->klass(), -1) && oopDesc::is_oop_or_null(*p));
  }
  void describe(FrameValues& values, int frame_no) {
    for (int i = 0; i < _oops->length(); i++) {
      oop* p = _oops->at(i);
      values.describe(frame_no, (intptr_t*)p, err_msg("oop%s for #%d", is_good(p) ? "" : " (BAD)", frame_no));
    }
    for (int i = 0; i < _narrow_oops->length(); i++) {
      narrowOop* p = _narrow_oops->at(i);
      // we can't check for bad compressed oops, as decoding them might crash
      values.describe(frame_no, (intptr_t*)p, err_msg("narrow oop for #%d", frame_no));
    }
    assert(_base->length() == _derived->length(), "should be the same");
    for (int i = 0; i < _base->length(); i++) {
      derived_base* base = _base->at(i);
      derived_pointer* derived = _derived->at(i);
      values.describe(frame_no, (intptr_t*)derived, err_msg("derived pointer (base: " INTPTR_FORMAT ") for #%d", p2i(base), frame_no));
    }
  }
};

class FrameValuesOopMapClosure: public OopMapClosure {
private:
  const frame* _fr;
  const RegisterMap* _reg_map;
  FrameValues& _values;
  int _frame_no;

public:
  FrameValuesOopMapClosure(const frame* fr, const RegisterMap* reg_map, FrameValues& values, int frame_no)
   : _fr(fr), _reg_map(reg_map), _values(values), _frame_no(frame_no) {}

  virtual void do_value(VMReg reg, OopMapValue::oop_types type) override {
    intptr_t* p = (intptr_t*)_fr->oopmapreg_to_location(reg, _reg_map);
    if (p != nullptr && (((intptr_t)p & WordAlignmentMask) == 0)) {
      const char* type_name = nullptr;
      switch(type) {
        case OopMapValue::oop_value:          type_name = "oop";          break;
        case OopMapValue::narrowoop_value:    type_name = "narrow oop";   break;
        case OopMapValue::callee_saved_value: type_name = "callee-saved"; break;
        case OopMapValue::derived_oop_value:  type_name = "derived";      break;
        // case OopMapValue::live_value:         type_name = "live";         break;
        default: break;
      }
      if (type_name != nullptr) {
        _values.describe(_frame_no, p, err_msg("%s for #%d", type_name, _frame_no));
      }
    }
  }
};

// callers need a ResourceMark because of name_and_sig_as_C_string() usage,
// RA allocated string is returned to the caller
void frame::describe(FrameValues& values, int frame_no, const RegisterMap* reg_map) {
  // boundaries: sp and the 'real' frame pointer
  values.describe(-1, sp(), err_msg("sp for #%d", frame_no), 0);
  intptr_t* frame_pointer = real_fp(); // Note: may differ from fp()

  // print frame info at the highest boundary
  intptr_t* info_address = MAX2(sp(), frame_pointer);

  if (info_address != frame_pointer) {
    // print frame_pointer explicitly if not marked by the frame info
    values.describe(-1, frame_pointer, err_msg("frame pointer for #%d", frame_no), 1);
  }

  if (is_entry_frame() || is_compiled_frame() || is_interpreted_frame() || is_native_frame()) {
    // Label values common to most frames
    values.describe(-1, unextended_sp(), err_msg("unextended_sp for #%d", frame_no), 0);
  }

  if (is_interpreted_frame()) {
    Method* m = interpreter_frame_method();
    int bci = interpreter_frame_bci();
    InterpreterCodelet* desc = Interpreter::codelet_containing(pc());

    // Label the method and current bci
    values.describe(-1, info_address,
                    FormatBuffer<1024>("#%d method %s @ %d", frame_no, m->name_and_sig_as_C_string(), bci), 3);
    if (desc != nullptr) {
      values.describe(-1, info_address, err_msg("- %s codelet: %s",
        desc->bytecode()    >= 0    ? Bytecodes::name(desc->bytecode()) : "",
        desc->description() != nullptr ? desc->description()               : "?"), 2);
    }
    values.describe(-1, info_address,
                    err_msg("- %d locals %d max stack", m->max_locals(), m->max_stack()), 2);
    // return address will be emitted by caller in describe_pd
    // values.describe(frame_no, (intptr_t*)sender_pc_addr(), Continuation::is_return_barrier_entry(*sender_pc_addr()) ? "return address (return barrier)" : "return address");

    if (m->max_locals() > 0) {
      intptr_t* l0 = interpreter_frame_local_at(0);
      intptr_t* ln = interpreter_frame_local_at(m->max_locals() - 1);
      values.describe(-1, MAX2(l0, ln), err_msg("locals for #%d", frame_no), 2);
      // Report each local and mark as owned by this frame
      for (int l = 0; l < m->max_locals(); l++) {
        intptr_t* l0 = interpreter_frame_local_at(l);
        values.describe(frame_no, l0, err_msg("local %d", l), 1);
      }
    }

    if (interpreter_frame_monitor_begin() != interpreter_frame_monitor_end()) {
      values.describe(frame_no, (intptr_t*)interpreter_frame_monitor_begin(), "monitors begin");
      values.describe(frame_no, (intptr_t*)interpreter_frame_monitor_end(), "monitors end");
    }

    // Compute the actual expression stack size
    InterpreterOopMap mask;
    OopMapCache::compute_one_oop_map(methodHandle(Thread::current(), m), bci, &mask);
    intptr_t* tos = nullptr;
    // Report each stack element and mark as owned by this frame
    for (int e = 0; e < mask.expression_stack_size(); e++) {
      tos = MAX2(tos, interpreter_frame_expression_stack_at(e));
      values.describe(frame_no, interpreter_frame_expression_stack_at(e),
                      err_msg("stack %d", e), 1);
    }
    if (tos != nullptr) {
      values.describe(-1, tos, err_msg("expression stack for #%d", frame_no), 2);
    }

    if (reg_map != nullptr) {
      FrameValuesOopClosure oopsFn;
      oops_do(&oopsFn, nullptr, &oopsFn, reg_map);
      oopsFn.describe(values, frame_no);
    }
  } else if (is_entry_frame()) {
    // For now just label the frame
    values.describe(-1, info_address, err_msg("#%d entry frame", frame_no), 2);
  } else if (cb()->is_nmethod()) {
    // For now just label the frame
    nmethod* nm = cb()->as_nmethod();
    values.describe(-1, info_address,
                    FormatBuffer<1024>("#%d nmethod " INTPTR_FORMAT " for method J %s%s", frame_no,
                                       p2i(nm),
                                       nm->method()->name_and_sig_as_C_string(),
                                       (_deopt_state == is_deoptimized) ?
                                       " (deoptimized)" :
                                       ((_deopt_state == unknown) ? " (state unknown)" : "")),
                    3);

    { // mark arguments (see nmethod::print_nmethod_labels)
      Method* m = nm->method();

      int stack_slot_offset = nm->frame_size() * wordSize; // offset, in bytes, to caller sp
      int sizeargs = m->size_of_parameters();

      BasicType* sig_bt = NEW_RESOURCE_ARRAY(BasicType, sizeargs);
      VMRegPair* regs   = NEW_RESOURCE_ARRAY(VMRegPair, sizeargs);
      {
        int sig_index = 0;
        if (!m->is_static()) {
          sig_bt[sig_index++] = T_OBJECT; // 'this'
        }
        for (SignatureStream ss(m->signature()); !ss.at_return_type(); ss.next()) {
          BasicType t = ss.type();
          assert(type2size[t] == 1 || type2size[t] == 2, "size is 1 or 2");
          sig_bt[sig_index++] = t;
          if (type2size[t] == 2) {
            sig_bt[sig_index++] = T_VOID;
          }
        }
        assert(sig_index == sizeargs, "");
      }
      int stack_arg_slots = SharedRuntime::java_calling_convention(sig_bt, regs, sizeargs);
      assert(stack_arg_slots ==  nm->as_nmethod()->num_stack_arg_slots(false /* rounded */) || nm->is_osr_method(), "");
      int out_preserve = SharedRuntime::out_preserve_stack_slots();
      int sig_index = 0;
      int arg_index = (m->is_static() ? 0 : -1);
      for (SignatureStream ss(m->signature()); !ss.at_return_type(); ) {
        bool at_this = (arg_index == -1);
        bool at_old_sp = false;
        BasicType t = (at_this ? T_OBJECT : ss.type());
        assert(t == sig_bt[sig_index], "sigs in sync");
        VMReg fst = regs[sig_index].first();
        if (fst->is_stack()) {
          assert(((int)fst->reg2stack()) >= 0, "reg2stack: %d", fst->reg2stack());
          int offset = (fst->reg2stack() + out_preserve) * VMRegImpl::stack_slot_size + stack_slot_offset;
          intptr_t* stack_address = (intptr_t*)((address)unextended_sp() + offset);
          if (at_this) {
            values.describe(frame_no, stack_address, err_msg("this for #%d", frame_no), 1);
          } else {
            values.describe(frame_no, stack_address, err_msg("param %d %s for #%d", arg_index, type2name(t), frame_no), 1);
          }
        }
        sig_index += type2size[t];
        arg_index += 1;
        if (!at_this) {
          ss.next();
        }
      }
    }

    if (reg_map != nullptr && is_java_frame()) {
      int scope_no = 0;
      for (ScopeDesc* scope = nm->scope_desc_at(pc()); scope != nullptr; scope = scope->sender(), scope_no++) {
        Method* m = scope->method();
        int  bci = scope->bci();
        values.describe(-1, info_address, err_msg("- #%d scope %s @ %d", scope_no, m->name_and_sig_as_C_string(), bci), 2);

        { // mark locals
          GrowableArray<ScopeValue*>* scvs = scope->locals();
          int scvs_length = scvs != nullptr ? scvs->length() : 0;
          for (int i = 0; i < scvs_length; i++) {
            intptr_t* stack_address = (intptr_t*)StackValue::stack_value_address(this, reg_map, scvs->at(i));
            if (stack_address != nullptr) {
              values.describe(frame_no, stack_address, err_msg("local %d for #%d (scope %d)", i, frame_no, scope_no), 1);
            }
          }
        }
        { // mark expression stack
          GrowableArray<ScopeValue*>* scvs = scope->expressions();
          int scvs_length = scvs != nullptr ? scvs->length() : 0;
          for (int i = 0; i < scvs_length; i++) {
            intptr_t* stack_address = (intptr_t*)StackValue::stack_value_address(this, reg_map, scvs->at(i));
            if (stack_address != nullptr) {
              values.describe(frame_no, stack_address, err_msg("stack %d for #%d (scope %d)", i, frame_no, scope_no), 1);
            }
          }
        }
      }

      FrameValuesOopClosure oopsFn;
      oops_do(&oopsFn, nullptr, &oopsFn, reg_map);
      oopsFn.describe(values, frame_no);

      if (oop_map() != nullptr) {
        FrameValuesOopMapClosure valuesFn(this, reg_map, values, frame_no);
        // also OopMapValue::live_value ??
        oop_map()->all_type_do(this, OopMapValue::callee_saved_value, &valuesFn);
      }
    }

    if (nm->method()->is_continuation_enter_intrinsic()) {
      ContinuationEntry* ce = Continuation::get_continuation_entry_for_entry_frame(reg_map->thread(), *this); // (ContinuationEntry*)unextended_sp();
      ce->describe(values, frame_no);
    }
  } else if (is_native_frame()) {
    // For now just label the frame
    nmethod* nm = cb()->as_nmethod_or_null();
    values.describe(-1, info_address,
                    FormatBuffer<1024>("#%d nmethod " INTPTR_FORMAT " for native method %s", frame_no,
                                       p2i(nm), nm->method()->name_and_sig_as_C_string()), 2);
  } else {
    // provide default info if not handled before
    char *info = (char *) "special frame";
    if ((_cb != nullptr) &&
        (_cb->name() != nullptr)) {
      info = (char *)_cb->name();
    }
    values.describe(-1, info_address, err_msg("#%d <%s>", frame_no, info), 2);
  }

  // platform dependent additional data
  describe_pd(values, frame_no);
}

#endif

#ifndef PRODUCT

void FrameValues::describe(int owner, intptr_t* location, const char* description, int priority) {
  FrameValue fv;
  fv.location = location;
  fv.owner = owner;
  fv.priority = priority;
  fv.description = NEW_RESOURCE_ARRAY(char, strlen(description) + 1);
  strcpy(fv.description, description);
  _values.append(fv);
}


#ifdef ASSERT
void FrameValues::validate() {
  _values.sort(compare);
  bool error = false;
  FrameValue prev;
  prev.owner = -1;
  for (int i = _values.length() - 1; i >= 0; i--) {
    FrameValue fv = _values.at(i);
    if (fv.owner == -1) continue;
    if (prev.owner == -1) {
      prev = fv;
      continue;
    }
    if (prev.location == fv.location) {
      if (fv.owner != prev.owner) {
        tty->print_cr("overlapping storage");
        tty->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT " %s", p2i(prev.location), *prev.location, prev.description);
        tty->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT " %s", p2i(fv.location), *fv.location, fv.description);
        error = true;
      }
    } else {
      prev = fv;
    }
  }
  // if (error) { tty->cr(); print_on(static_cast<JavaThread*>(nullptr), tty); }
  assert(!error, "invalid layout");
}
#endif // ASSERT

void FrameValues::print_on(JavaThread* thread, outputStream* st) {
  _values.sort(compare);

  // Sometimes values like the fp can be invalid values if the
  // register map wasn't updated during the walk.  Trim out values
  // that aren't actually in the stack of the thread.
  int min_index = 0;
  int max_index = _values.length() - 1;
  intptr_t* v0 = _values.at(min_index).location;
  intptr_t* v1 = _values.at(max_index).location;

  if (thread != nullptr) {
    if (thread == Thread::current()) {
      while (!thread->is_in_live_stack((address)v0)) v0 = _values.at(++min_index).location;
      while (!thread->is_in_live_stack((address)v1)) v1 = _values.at(--max_index).location;
    } else {
      while (!thread->is_in_full_stack((address)v0)) v0 = _values.at(++min_index).location;
      while (!thread->is_in_full_stack((address)v1)) v1 = _values.at(--max_index).location;
    }
  }

  print_on(st, min_index, max_index, v0, v1);
}

void FrameValues::print_on(stackChunkOop chunk, outputStream* st) {
  _values.sort(compare);

  intptr_t* start = chunk->start_address();
  intptr_t* end = chunk->end_address() + 1;

  int min_index = 0;
  int max_index = _values.length() - 1;
  intptr_t* v0 = _values.at(min_index).location;
  intptr_t* v1 = _values.at(max_index).location;
  while (!(start <= v0 && v0 <= end)) v0 = _values.at(++min_index).location;
  while (!(start <= v1 && v1 <= end)) v1 = _values.at(--max_index).location;

  print_on(st, min_index, max_index, v0, v1);
}

void FrameValues::print_on(outputStream* st, int min_index, int max_index, intptr_t* v0, intptr_t* v1) {
  intptr_t* min = MIN2(v0, v1);
  intptr_t* max = MAX2(v0, v1);
  intptr_t* cur = max;
  intptr_t* last = nullptr;
  intptr_t* fp = nullptr;
  for (int i = max_index; i >= min_index; i--) {
    FrameValue fv = _values.at(i);
    while (cur > fv.location) {
      st->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT, p2i(cur), *cur);
      cur--;
    }
    if (last == fv.location) {
      const char* spacer = "          " LP64_ONLY("        ");
      st->print_cr(" %s  %s %s", spacer, spacer, fv.description);
    } else {
      if (*fv.description == '#' && isdigit(fv.description[1])) {
        // The fv.description string starting with a '#' is the line for the
        // saved frame pointer eg. "#10 method java.lang.invoke.LambdaForm..."
        // basicaly means frame 10.
        fp = fv.location;
      }
      // To print a fp-relative value:
      //   1. The content of *fv.location must be such that we think it's a
      //      fp-relative number, i.e [-100..100].
      //   2. We must have found the frame pointer.
      //   3. The line can not be the line for the saved frame pointer.
      //   4. Recognize it as being part of the "fixed frame".
      if (*fv.location != 0 && *fv.location > -100 && *fv.location < 100
          && fp != nullptr && *fv.description != '#'
#if !defined(PPC64)
          && (strncmp(fv.description, "interpreter_frame_", 18) == 0 || strstr(fv.description, " method "))
#else  // !defined(PPC64)
          && (strcmp(fv.description, "sender_sp") == 0 || strcmp(fv.description, "top_frame_sp") == 0 ||
              strcmp(fv.description, "esp") == 0 || strcmp(fv.description, "monitors") == 0 ||
              strcmp(fv.description, "locals") == 0 || strstr(fv.description, " method "))
#endif //!defined(PPC64)
          ) {
        st->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT " %-32s (relativized: fp%+d)",
                     p2i(fv.location), p2i(&fp[*fv.location]), fv.description, (int)*fv.location);
      } else {
        st->print_cr(" " INTPTR_FORMAT ": " INTPTR_FORMAT " %s", p2i(fv.location), *fv.location, fv.description);
      }
      last = fv.location;
      cur--;
    }
  }
}

#endif // ndef PRODUCT
