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

#ifndef SHARE_RUNTIME_UNLOADABLE_METHOD_HANDLE_INLINE_HPP
#define SHARE_RUNTIME_UNLOADABLE_METHOD_HANDLE_INLINE_HPP

#include "runtime/unloadableMethodHandle.hpp"

#include "memory/universe.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/weakHandle.inline.hpp"

inline UnloadableMethodHandle::UnloadableMethodHandle(Method* method) {
  assert(method != nullptr, "Should be");
  _method = method;
  oop obj = get_unload_blocker(method);
  if (obj != nullptr) {
    _weak_handle = WeakHandle(Universe::vm_weak(), obj);
  }
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
  if (_method != nullptr) {
    _method = nullptr;
    _weak_handle.release(Universe::vm_weak());
    _strong_handle.release(Universe::vm_global());
  }
}

bool UnloadableMethodHandle::is_unloaded() const {
  // Unloaded if weak handle was set, but now had been cleared by GC.
  return !_weak_handle.is_empty() && _weak_handle.peek() == nullptr;
}

inline void UnloadableMethodHandle::block_unloading() {
  assert(!is_unloaded(), "Pre-condition: should not be unloaded");

  if (!_weak_handle.is_empty()) {
    assert(_weak_handle.peek() != nullptr, "Should not be cleared");
    assert(_method->method_holder()->is_loader_alive(), "Should be alive");
    assert(_strong_handle.is_empty(), "Should be empty");
    oop obj = get_unload_blocker(_method);
    if (obj != nullptr) {
      _strong_handle = OopHandle(Universe::vm_global(), obj);
    }
    // Release the weak handle right away, so that is_unloaded() does not touch
    // peek() when thread is in the wrong state.
    _weak_handle.release(Universe::vm_weak());
  }

  assert(!is_unloaded(), "Post-condition: should not be unloaded");
}

inline Method* UnloadableMethodHandle::method() const {
  assert(!is_unloaded(), "Should not be unloaded");
  return _method;
}

#endif // SHARE_RUNTIME_UNLOADABLE_METHOD_HANDLE_INLINE_HPP
