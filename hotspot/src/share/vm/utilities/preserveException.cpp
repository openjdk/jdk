/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_preserveException.cpp.incl"

// TODO: These three classes should be refactored

PreserveExceptionMark::PreserveExceptionMark(Thread*& thread) {
  thread     = Thread::current();
  _thread    = thread;
  _preserved_exception_oop = Handle(thread, _thread->pending_exception());
  _thread->clear_pending_exception(); // Needed to avoid infinite recursion
  _preserved_exception_line = _thread->exception_line();
  _preserved_exception_file = _thread->exception_file();
}


PreserveExceptionMark::~PreserveExceptionMark() {
  if (_thread->has_pending_exception()) {
    oop exception = _thread->pending_exception();
    _thread->clear_pending_exception(); // Needed to avoid infinite recursion
    exception->print();
    fatal("PreserveExceptionMark destructor expects no pending exceptions");
  }
  if (_preserved_exception_oop() != NULL) {
    _thread->set_pending_exception(_preserved_exception_oop(), _preserved_exception_file, _preserved_exception_line);
  }
}


// This code is cloned from PreserveExceptionMark, except that:
//   returned pending exceptions do not cause a crash.
//   thread is passed in, not set (not a reference parameter)
//   and bug 6431341 has been addressed.

CautiouslyPreserveExceptionMark::CautiouslyPreserveExceptionMark(Thread* thread) {
  _thread    = thread;
  _preserved_exception_oop = Handle(thread, _thread->pending_exception());
  _preserved_exception_line = _thread->exception_line();
  _preserved_exception_file = _thread->exception_file();
  _thread->clear_pending_exception(); // Pending exceptions are checked in the destructor
}


CautiouslyPreserveExceptionMark::~CautiouslyPreserveExceptionMark() {
  assert(!_thread->has_pending_exception(), "unexpected exception generated");
  if (_thread->has_pending_exception()) {
    _thread->clear_pending_exception();
  }
  if (_preserved_exception_oop() != NULL) {
    _thread->set_pending_exception(_preserved_exception_oop(), _preserved_exception_file, _preserved_exception_line);
  }
}


void WeakPreserveExceptionMark::preserve() {
  _preserved_exception_oop = Handle(_thread, _thread->pending_exception());
  _preserved_exception_line = _thread->exception_line();
  _preserved_exception_file = _thread->exception_file();
  _thread->clear_pending_exception();
}

void WeakPreserveExceptionMark::restore() {
  if (!_thread->has_pending_exception()) {
    _thread->set_pending_exception(_preserved_exception_oop(), _preserved_exception_file, _preserved_exception_line);
  }
}
