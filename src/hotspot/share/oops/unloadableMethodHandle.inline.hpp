/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_INLINE_HPP
#define SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_INLINE_HPP

#include "unloadableMethodHandle.hpp"

#include "memory/universe.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "runtime/javaThread.inline.hpp"

inline UnloadableMethodHandle::UnloadableMethodHandle(Method* method) :
  _method(method) {
  assert(method != nullptr, "Should be");

  oop obj = get_unload_blocker(method);
  if (obj != nullptr) {
    _weak_handle = WeakHandle(Universe::vm_weak(), obj);
    set_state(State::WEAK);
  } else {
    set_state(State::PERMANENT);
  }

  assert(is_safe(), "Should be");
}

inline UnloadableMethodHandle::~UnloadableMethodHandle() {
  switch (get_state()) {
    case State::STRONG: {
      _strong_handle.release(Universe::vm_global());
    }
    case State::WEAK: {
      _weak_handle.release(Universe::vm_weak());
    }
    case State::PERMANENT: {
      _method = nullptr;
      set_state(State::RELEASED);
    }
    case State::RELEASED: {
      // Nothing to do.
      break;
    }
    default:
      assert(false, "Should not be here");
  }

  assert(_method == nullptr, "Should be");
  assert(_weak_handle.is_empty(), "Should be");
  assert(_strong_handle.is_empty(), "Should be");
  assert(!is_safe(), "Should not be");
}

inline UnloadableMethodHandle::State UnloadableMethodHandle::get_state() const {
  return Atomic::load_acquire(&_state);
}

inline void UnloadableMethodHandle::set_state(State to) {
  Atomic::release_store(&_state, to);
}

inline bool UnloadableMethodHandle::transit_state(State from, State to) {
  return Atomic::cmpxchg(&_state, from, to, memory_order_release) == from;
}

oop UnloadableMethodHandle::get_unload_blocker(Method* method) {
  assert(method != nullptr, "Should be");

  InstanceKlass* holder = method->method_holder();
  if (holder->class_loader_data()->is_permanent_class_loader_data()) {
    // Method holder class cannot be unloaded.
    return nullptr;
  }

  // Return the holder that would block unloading.
  // This would be either classloader oop for non-hidden classes,
  // or Java mirror oop for hidden classes.
  oop klass_holder = holder->klass_holder();
  assert(klass_holder != nullptr, "Should be");
  return klass_holder;
}

bool UnloadableMethodHandle::is_safe() const {
  switch (get_state()) {
    case State::PERMANENT:
    case State::STRONG: {
      // Definitely safe.
      return true;
    }
    case State::RELEASED: {
      // Definitely unsafe.
      return false;
    }
    case State::WEAK: {
      // Safety: Caller should be a Java thread in proper state.
      // Otherwise, unloading can happen without coordinating with this thread.
      // (Access API would assert this too, but do not rely on it.)
      Thread* t = Thread::current();
      if (!t->is_Java_thread() || JavaThread::cast(t)->thread_state() == _thread_in_native) {
        // Unable to figure out safety, give a pessimistic answer.
        assert(false, "Cannot figure out safety, check the thread lifecycle, or use method_unsafe()");
        return false;
      }

      // Finally, see if the handle was cleared by GC.
      return _weak_handle.peek() != nullptr;
    }
    default:
      assert(false, "Should not be here");
      return false;
  }
}

inline void UnloadableMethodHandle::make_always_safe() {
  assert(is_safe(), "Should be");

  switch (get_state()) {
    case State::PERMANENT:
    case State::STRONG:
    case State::RELEASED: {
      // No action is needed.
      break;
    }
    case State::WEAK: {
      if (transit_state(State::WEAK, State::STRONG)) {
        // Do this only once, otherwise it leaks handles.
        oop obj = get_unload_blocker(_method);
        assert(obj != nullptr, "Should have one");
        _strong_handle = OopHandle(Universe::vm_global(), obj);
      }
      break;
    }
    default:
      assert(false, "Should not be here");
  }

  assert(is_safe(), "Should be");
}

inline Method* UnloadableMethodHandle::method() const {
  assert(is_safe(), "Should be");
  return _method;
}

inline Method* UnloadableMethodHandle::method_unsafe() const {
  return _method;
}

#endif // SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_INLINE_HPP
