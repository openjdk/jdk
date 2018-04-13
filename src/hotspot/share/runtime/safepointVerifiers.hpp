/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SAFEPOINTVERIFIERS_HPP
#define SHARE_VM_RUNTIME_SAFEPOINTVERIFIERS_HPP

#include "memory/allocation.hpp"
#include "runtime/thread.hpp"

// A NoGCVerifier object can be placed in methods where one assumes that
// no garbage collection will occur. The destructor will verify this property
// unless the constructor is called with argument false (not verifygc).
//
// The check will only be done in debug mode and if verifygc true.

class NoGCVerifier: public StackObj {
 friend class PauseNoGCVerifier;

 protected:
  bool _verifygc;
  unsigned int _old_invocations;

 public:
#ifdef ASSERT
  NoGCVerifier(bool verifygc = true);
  ~NoGCVerifier();
#else
  NoGCVerifier(bool verifygc = true) {}
  ~NoGCVerifier() {}
#endif
};

// A PauseNoGCVerifier is used to temporarily pause the behavior
// of a NoGCVerifier object. If we are not in debug mode or if the
// NoGCVerifier object has a _verifygc value of false, then there
// is nothing to do.

class PauseNoGCVerifier: public StackObj {
 private:
  NoGCVerifier * _ngcv;

 public:
#ifdef ASSERT
  PauseNoGCVerifier(NoGCVerifier * ngcv);
  ~PauseNoGCVerifier();
#else
  PauseNoGCVerifier(NoGCVerifier * ngcv) {}
  ~PauseNoGCVerifier() {}
#endif
};


// A NoSafepointVerifier object will throw an assertion failure if
// the current thread passes a possible safepoint while this object is
// instantiated. A safepoint, will either be: an oop allocation, blocking
// on a Mutex or JavaLock, or executing a VM operation.
//
// If StrictSafepointChecks is turned off, it degrades into a NoGCVerifier
//
class NoSafepointVerifier : public NoGCVerifier {
 friend class PauseNoSafepointVerifier;

 private:
  bool _activated;
  Thread *_thread;
 public:
#ifdef ASSERT
  NoSafepointVerifier(bool activated = true, bool verifygc = true ) :
    NoGCVerifier(verifygc),
    _activated(activated) {
    _thread = Thread::current();
    if (_activated) {
      _thread->_allow_allocation_count++;
      _thread->_allow_safepoint_count++;
    }
  }

  ~NoSafepointVerifier() {
    if (_activated) {
      _thread->_allow_allocation_count--;
      _thread->_allow_safepoint_count--;
    }
  }
#else
  NoSafepointVerifier(bool activated = true, bool verifygc = true) : NoGCVerifier(verifygc){}
  ~NoSafepointVerifier() {}
#endif
};

// A PauseNoSafepointVerifier is used to temporarily pause the
// behavior of a NoSafepointVerifier object. If we are not in debug
// mode then there is nothing to do. If the NoSafepointVerifier
// object has an _activated value of false, then there is nothing to
// do for safepoint and allocation checking, but there may still be
// something to do for the underlying NoGCVerifier object.

class PauseNoSafepointVerifier : public PauseNoGCVerifier {
 private:
  NoSafepointVerifier * _nsv;

 public:
#ifdef ASSERT
  PauseNoSafepointVerifier(NoSafepointVerifier * nsv)
    : PauseNoGCVerifier(nsv) {

    _nsv = nsv;
    if (_nsv->_activated) {
      _nsv->_thread->_allow_allocation_count--;
      _nsv->_thread->_allow_safepoint_count--;
    }
  }

  ~PauseNoSafepointVerifier() {
    if (_nsv->_activated) {
      _nsv->_thread->_allow_allocation_count++;
      _nsv->_thread->_allow_safepoint_count++;
    }
  }
#else
  PauseNoSafepointVerifier(NoSafepointVerifier * nsv)
    : PauseNoGCVerifier(nsv) {}
  ~PauseNoSafepointVerifier() {}
#endif
};

// A NoAllocVerifier object can be placed in methods where one assumes that
// no allocation will occur. The destructor will verify this property
// unless the constructor is called with argument false (not activated).
//
// The check will only be done in debug mode and if activated.
// Note: this only makes sense at safepoints (otherwise, other threads may
// allocate concurrently.)

class NoAllocVerifier : public StackObj {
 private:
  bool  _activated;

 public:
#ifdef ASSERT
  NoAllocVerifier(bool activated = true) {
    _activated = activated;
    if (_activated) Thread::current()->_allow_allocation_count++;
  }

  ~NoAllocVerifier() {
    if (_activated) Thread::current()->_allow_allocation_count--;
  }
#else
  NoAllocVerifier(bool activated = true) {}
  ~NoAllocVerifier() {}
#endif
};

#endif // SHARE_VM_RUNTIME_SAFEPOINTVERIFIERS_HPP
