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

inline UnloadableMethodHandle::UnloadableMethodHandle() : _spin_lock(0), _method(nullptr) {
  set_state(EMPTY);
}

inline UnloadableMethodHandle::UnloadableMethodHandle(Method* method) : _spin_lock(0), _method(method) {
  assert(method != nullptr, "Should be");
  oop obj = get_unload_blocker(method);
  if (obj != nullptr) {
    _weak_handle = WeakHandle(Universe::vm_weak(), obj);
    set_state(WEAK);
  } else {
    set_state(STRONG);
  }
  assert(is_safe(), "Should be");
}

inline UnloadableMethodHandle::State UnloadableMethodHandle::get_state() const {
  return Atomic::load_acquire(&_state);
}

inline void UnloadableMethodHandle::set_state(State s) {
  Atomic::release_store_fence(&_state, s);
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

void UnloadableMethodHandle::release() {
  switch (get_state()) {
    case RELEASED: {
      // Nothing to do.
      break;
    }
    case STRONG:
    case WEAK: {
      _strong_handle.release(Universe::vm_global());
      _weak_handle.release(Universe::vm_weak());
      _method = nullptr;
      set_state(RELEASED);
      break;
    }
    case EMPTY: {
      set_state(RELEASED);
      break;
    }
    default:
      ShouldNotReachHere();
  }

  assert(!is_safe(), "Should not be");
}

bool UnloadableMethodHandle::is_safe() const {
  switch (get_state()) {
    case EMPTY:
    case STRONG: {
      // Definitely safe.
      return true;
    }
    case RELEASED: {
      // Definitely unsafe.
      return false;
    }
    case WEAK: {
      // Safe only if weak handle was not cleared by GC.
      // This is only trustworthy if caller is a Java thread in proper state.
      // Otherwise, unloading can happen without coordinating with this thread.
      // (Access API would assert this too, but do not rely on it.)
      Thread* t = Thread::current();
      if (t->is_Java_thread() &&
          (JavaThread::cast(t)->thread_state() != _thread_in_native) &&
          (_weak_handle.peek() != nullptr)) {
        return true;
      }
      return false;
    }
    default:
      ShouldNotReachHere();
      return false;
  }
}

inline void UnloadableMethodHandle::make_always_safe() {
  assert(is_safe(), "Should be");

  switch (get_state()) {
    case EMPTY:
    case STRONG: {
      // Already safe.
      break;
    }
    case RELEASED: {
      assert(false, "Cannot be RELEASED: check lifecycle");
      break;
    }
    case WEAK: {
      // Need to capture holder strongly. Under concurrent calls, we need to make
      // sure we create the strong handle only once, otherwise we can leak some.
      // This path is normally uncontended, so a simple spin lock would do.
      Thread::SpinAcquire(&_spin_lock);
      if (get_state() == WEAK) {
        oop obj = get_unload_blocker(_method);
        assert(obj != nullptr, "Should have one");
        _strong_handle = OopHandle(Universe::vm_global(), obj);
        set_state(STRONG);
      } else {
        assert(get_state() == STRONG, "Should be otherwise");
      }
      Thread::SpinRelease(&_spin_lock);
      break;
    }
    default:
      ShouldNotReachHere();
  }

  assert(is_safe(), "Should be");
}

inline Method* UnloadableMethodHandle::method() const {
  assert(is_safe(), "Should be");
  return _method;
}

#endif // SHARE_OOPS_UNLOADABLE_METHOD_HANDLE_INLINE_HPP
