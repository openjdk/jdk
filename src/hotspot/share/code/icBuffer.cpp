/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "code/scopeDesc.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vmOperations.hpp"

DEF_STUB_INTERFACE(ICStub);

StubQueue* InlineCacheBuffer::_buffer    = nullptr;

CompiledICHolder* volatile InlineCacheBuffer::_pending_released = nullptr;
volatile int InlineCacheBuffer::_pending_count = 0;

#ifdef ASSERT
ICRefillVerifier::ICRefillVerifier()
  : _refill_requested(false),
    _refill_remembered(false)
{
  Thread* thread = Thread::current();
  assert(thread->missed_ic_stub_refill_verifier() == nullptr, "nesting not supported");
  thread->set_missed_ic_stub_refill_verifier(this);
}

ICRefillVerifier::~ICRefillVerifier() {
  assert(!_refill_requested || _refill_remembered,
         "Forgot to refill IC stubs after failed IC transition");
  Thread::current()->set_missed_ic_stub_refill_verifier(nullptr);
}

ICRefillVerifierMark::ICRefillVerifierMark(ICRefillVerifier* verifier) {
  Thread* thread = Thread::current();
  assert(thread->missed_ic_stub_refill_verifier() == nullptr, "nesting not supported");
  thread->set_missed_ic_stub_refill_verifier(verifier);
}

ICRefillVerifierMark::~ICRefillVerifierMark() {
  Thread::current()->set_missed_ic_stub_refill_verifier(nullptr);
}

static ICRefillVerifier* current_ic_refill_verifier() {
  Thread* current = Thread::current();
  ICRefillVerifier* verifier = current->missed_ic_stub_refill_verifier();
  assert(verifier != nullptr, "need a verifier for safety");
  return verifier;
}
#endif

void ICStub::finalize() {
  if (!is_empty()) {
    ResourceMark rm;
    CompiledIC *ic = CompiledIC_at(CodeCache::find_compiled(ic_site()), ic_site());
    assert(CodeCache::find_compiled(ic->instruction_address()) != nullptr, "inline cache in non-compiled?");

    assert(this == ICStub::from_destination_address(ic->stub_address()), "wrong owner of ic buffer");
    ic->set_ic_destination_and_value(destination(), cached_value());
  }
}


address ICStub::destination() const {
  return InlineCacheBuffer::ic_buffer_entry_point(code_begin());
}

void* ICStub::cached_value() const {
  return InlineCacheBuffer::ic_buffer_cached_value(code_begin());
}


void ICStub::set_stub(CompiledIC *ic, void* cached_val, address dest_addr) {
  // We cannot store a pointer to the 'ic' object, since it is resource allocated. Instead we
  // store the location of the inline cache. Then we have enough information recreate the CompiledIC
  // object when we need to remove the stub.
  _ic_site = ic->instruction_address();

  // Assemble new stub
  InlineCacheBuffer::assemble_ic_buffer_code(code_begin(), cached_val, dest_addr);
  assert(destination() == dest_addr,   "can recover destination");
  assert(cached_value() == cached_val, "can recover destination");
}


void ICStub::clear() {
  if (CompiledIC::is_icholder_entry(destination())) {
    InlineCacheBuffer::queue_for_release((CompiledICHolder*)cached_value());
  }
  _ic_site = nullptr;
}


#ifndef PRODUCT
// anybody calling to this stub will trap

void ICStub::verify() {
}

void ICStub::print() {
  tty->print_cr("ICStub: site: " INTPTR_FORMAT, p2i(_ic_site));
}
#endif

//-----------------------------------------------------------------------------------------------
// Implementation of InlineCacheBuffer


void InlineCacheBuffer::initialize() {
  if (_buffer != nullptr) return; // already initialized
  _buffer = new StubQueue(new ICStubInterface, checked_cast<int>(InlineCacheBufferSize), InlineCacheBuffer_lock, "InlineCacheBuffer");
  assert (_buffer != nullptr, "cannot allocate InlineCacheBuffer");
}


void InlineCacheBuffer::refill_ic_stubs() {
#ifdef ASSERT
  ICRefillVerifier* verifier = current_ic_refill_verifier();
  verifier->request_remembered();
#endif
  // we ran out of inline cache buffer space; must enter safepoint.
  // We do this by forcing a safepoint
  VM_ICBufferFull ibf;
  VMThread::execute(&ibf);
}

bool InlineCacheBuffer::needs_update_inline_caches() {
  // Stub removal
  if (buffer()->number_of_stubs() > 0) {
    return true;
  }

  // Release pending CompiledICHolder
  if (pending_icholder_count() > 0) {
    return true;
  }

  return false;
}

void InlineCacheBuffer::update_inline_caches() {
  if (buffer()->number_of_stubs() > 0) {
    if (TraceICBuffer) {
      tty->print_cr("[updating inline caches with %d stubs]", buffer()->number_of_stubs());
    }
    buffer()->remove_all();
  }
  release_pending_icholders();
}


bool InlineCacheBuffer::contains(address instruction_address) {
  return buffer()->contains(instruction_address);
}


bool InlineCacheBuffer::is_empty() {
  return buffer()->number_of_stubs() == 0;
}


void InlineCacheBuffer_init() {
  InlineCacheBuffer::initialize();
}

bool InlineCacheBuffer::create_transition_stub(CompiledIC *ic, void* cached_value, address entry) {
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be called during a safepoint");
  assert(CompiledICLocker::is_safe(ic->instruction_address()), "mt unsafe call");
  if (TraceICBuffer) {
    tty->print_cr("  create transition stub for " INTPTR_FORMAT " destination " INTPTR_FORMAT " cached value " INTPTR_FORMAT,
                  p2i(ic->instruction_address()), p2i(entry), p2i(cached_value));
  }

  // allocate and initialize new "out-of-line" inline-cache
  ICStub* ic_stub = (ICStub*) buffer()->request_committed(ic_stub_code_size());
  if (ic_stub == nullptr) {
#ifdef ASSERT
    ICRefillVerifier* verifier = current_ic_refill_verifier();
    verifier->request_refill();
#endif
    return false;
  }

#ifdef ASSERT
  {
    ICStub* rev_stub = ICStub::from_destination_address(ic_stub->code_begin());
    assert(ic_stub == rev_stub,
           "ICStub mapping is reversible: stub=" PTR_FORMAT ", code=" PTR_FORMAT ", rev_stub=" PTR_FORMAT,
           p2i(ic_stub), p2i(ic_stub->code_begin()), p2i(rev_stub));
  }
#endif

  // If an transition stub is already associate with the inline cache, then we remove the association.
  if (ic->is_in_transition_state()) {
    ICStub* old_stub = ICStub::from_destination_address(ic->stub_address());
    old_stub->clear();
  }

  ic_stub->set_stub(ic, cached_value, entry);

  // Update inline cache in nmethod to point to new "out-of-line" allocated inline cache
  ic->set_ic_destination(ic_stub);
  return true;
}


address InlineCacheBuffer::ic_destination_for(CompiledIC *ic) {
  ICStub* stub = ICStub::from_destination_address(ic->stub_address());
  return stub->destination();
}


void* InlineCacheBuffer::cached_value_for(CompiledIC *ic) {
  ICStub* stub = ICStub::from_destination_address(ic->stub_address());
  return stub->cached_value();
}


// Free CompiledICHolder*s that are no longer in use
void InlineCacheBuffer::release_pending_icholders() {
  assert(SafepointSynchronize::is_at_safepoint(), "should only be called during a safepoint");
  CompiledICHolder* holder = Atomic::load(&_pending_released);
  _pending_released = nullptr;
  int count = 0;
  while (holder != nullptr) {
    CompiledICHolder* next = holder->next();
    delete holder;
    holder = next;
    count++;
  }
  assert(pending_icholder_count() == count, "wrong count");
  Atomic::store(&_pending_count, 0);
}

// Enqueue this icholder for release during the next safepoint.  It's
// not safe to free them until then since they might be visible to
// another thread.
void InlineCacheBuffer::queue_for_release(CompiledICHolder* icholder) {
  assert(icholder->next() == nullptr, "multiple enqueue?");

  CompiledICHolder* old = Atomic::load(&_pending_released);
  for (;;) {
    icholder->set_next(old);
    // The only reader runs at a safepoint serially so there is no need for a more strict atomic.
    CompiledICHolder* cur = Atomic::cmpxchg(&_pending_released, old, icholder, memory_order_relaxed);
    if (cur == old) {
      break;
    }
    old = cur;
  }
  Atomic::inc(&_pending_count, memory_order_relaxed);

  if (TraceICBuffer) {
    tty->print_cr("enqueueing icholder " INTPTR_FORMAT " to be freed", p2i(icholder));
  }
}

int InlineCacheBuffer::pending_icholder_count() {
  return Atomic::load(&_pending_count);
}
