/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrThreadIterator.hpp"
#include "runtime/thread.inline.hpp"

static bool thread_inclusion_predicate(Thread* t) {
  assert(t != NULL, "invariant");
  return !t->jfr_thread_local()->is_dead();
}

static bool java_thread_inclusion_predicate(JavaThread* jt) {
  assert(jt != NULL, "invariant");
  return thread_inclusion_predicate(jt) && jt->thread_state() != _thread_new;
}

static JavaThread* next_java_thread(JavaThreadIteratorWithHandle& iter) {
  JavaThread* next = iter.next();
  while (next != NULL && !java_thread_inclusion_predicate(next)) {
    next = iter.next();
  }
  return next;
}

static NonJavaThread* next_non_java_thread(NonJavaThread::Iterator& iter) {
  NonJavaThread* next = NULL;
  while (!iter.end()) {
    next = iter.current();
    iter.step();
    assert(next != NULL, "invariant");
    if (!thread_inclusion_predicate(next)) {
      continue;
    }
  }
  return next;
}

JfrJavaThreadIteratorAdapter::JfrJavaThreadIteratorAdapter() : _iter(), _next(next_java_thread(_iter)) {}

JavaThread* JfrJavaThreadIteratorAdapter::next() {
  assert(has_next(), "invariant");
  Type* const temp = _next;
  _next = next_java_thread(_iter);
  assert(temp != _next, "invariant");
  return temp;
}

JfrNonJavaThreadIteratorAdapter::JfrNonJavaThreadIteratorAdapter() : _iter(), _next(next_non_java_thread(_iter)) {}

bool JfrNonJavaThreadIteratorAdapter::has_next() const {
  return _next != NULL;
}

NonJavaThread* JfrNonJavaThreadIteratorAdapter::next() {
  assert(has_next(), "invariant");
  Type* const temp = _next;
  _next = next_non_java_thread(_iter);
  assert(temp != _next, "invariant");
  return temp;
}

// explicit instantiations
template class JfrThreadIterator<JfrJavaThreadIteratorAdapter, StackObj>;
template class JfrThreadIterator<JfrNonJavaThreadIteratorAdapter, StackObj>;
