/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_PROTECTEDMETHODS_HPP
#define SHARE_CLASSFILE_PROTECTEDMETHODS_HPP

#include "memory/allocation.hpp"
#include "oops/method.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

// Helper class to ensure that any raw Method* we collect are
// protected from being reclaimed if class redefinition occurs.
// Note this only guards against reclamation, if code cares
// about the version of the Method then it needs to handle that
// itself. Logically this acts similar to making each Method* a
// methodHandle, but without the stack-allocation limits imposed by
// methodHandle.
// This helper was inspired by the JfrMethodProcessor where it is
// now used. It is also used across classfile parsing to protect
// _all_mirandas.
class ProtectedMethods : public StackObj {
  Thread* const _current;
  GrowableArray<Method*> _methods;

  NONCOPYABLE(ProtectedMethods);

 public:
  ProtectedMethods(Thread* current) : _current(current), _methods(20) {
    precond(current == Thread::current());
  }

  void append(Method* m) {
    NoSafepointVerifier nsv;
    JVMTI_ONLY(_current->metadata_handles()->push(m);)
    _methods.append(m);
  }

  const GrowableArray<Method*>* methods() {
    return &_methods;
  }

  ~ProtectedMethods() {
#if INCLUDE_JVMTI
    // Release all methods. Count backwards to improve
    // efficiency.
    for (int i = _methods.length() - 1; i >= 0; i--) {
      Method* m = _methods.at(i);
      int index = _current->metadata_handles()->find_from_end(m);
      assert(index >= 0, "missing registration for method");
      _current->metadata_handles()->remove_at(index);
    }
#endif
  }
};

#endif // SHARE_CLASSFILE_PROTECTEDMETHODS_HPP
