/*
* Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrNativeLibraryLoadEvent.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/thread.inline.hpp"

JfrNativeLibraryEventImpl::JfrNativeLibraryEventImpl(const char* name) : _name(name), _error_msg(nullptr), _start_time(nullptr) {}

JfrNativeLibraryEventImpl::~JfrNativeLibraryEventImpl() {
  delete _start_time;
}

const char* JfrNativeLibraryEventImpl::name() const {
  return _name;
}

JfrTicksWrapper* JfrNativeLibraryEventImpl::start_time() const {
  return _start_time;
}

bool JfrNativeLibraryEventImpl::has_start_time() const {
  return _start_time != nullptr;
}

const char* JfrNativeLibraryEventImpl::error_msg() const {
  return _error_msg;
}

void JfrNativeLibraryEventImpl::set_error_msg(const char* error_msg) {
  assert(_error_msg == nullptr, "invariant");
  _error_msg = error_msg;
}

template <typename EventType>
static inline JfrTicksWrapper* allocate_start_time() {
  return EventType::is_enabled() ? new JfrTicksWrapper() : nullptr;
}

NativeLibraryLoadEvent::NativeLibraryLoadEvent(const char* name, void** result) : JfrNativeLibraryEventImpl(name), _result(result) {
  assert(_result != nullptr, "invariant");
  _start_time = allocate_start_time<EventNativeLibraryLoad>();
}

bool NativeLibraryLoadEvent::success() const {
  return *_result != nullptr;
}

NativeLibraryUnloadEvent::NativeLibraryUnloadEvent(const char* name) : JfrNativeLibraryEventImpl(name), _result(false) {
  _start_time = allocate_start_time<EventNativeLibraryUnload>();
}

bool NativeLibraryUnloadEvent::success() const {
  return _result;
}

void NativeLibraryUnloadEvent::set_result(bool result) {
  _result = result;
}

template <typename EventType, typename Type>
static void commit(Type& type) {
  if (!type.has_start_time()) {
    return;
  }
  EventType event(UNTIMED);
  event.set_endtime(JfrTicks::now());
  event.set_starttime(*type.start_time());
  event.set_name(type.name());
  event.set_errorMessage(type.error_msg());
  event.set_success(type.success());
  Thread* thread = Thread::current();
  assert(thread != nullptr, "invariant");
  if (thread->is_Java_thread()) {
    JavaThread* jt = JavaThread::cast(thread);
    if (jt->thread_state() != _thread_in_vm) {
      assert(jt->thread_state() == _thread_in_native, "invariant");
      // For a JavaThread to take a JFR stacktrace, it must be in _thread_in_vm. Can safepoint here.
      ThreadInVMfromNative transition(jt);
      event.commit();
      return;
    }
  }
  event.commit();
}

NativeLibraryLoadEvent::~NativeLibraryLoadEvent() {
  commit<EventNativeLibraryLoad>(*this);
}

NativeLibraryUnloadEvent::~NativeLibraryUnloadEvent() {
  commit<EventNativeLibraryUnload>(*this);
}
