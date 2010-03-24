/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_ciExceptionHandler.cpp.incl"

// ciExceptionHandler
//
// This class represents an exception handler for a method.

// ------------------------------------------------------------------
// ciExceptionHandler::catch_klass
//
// Get the exception klass that this handler catches.
ciInstanceKlass* ciExceptionHandler::catch_klass() {
  VM_ENTRY_MARK;
  assert(!is_catch_all(), "bad index");
  if (_catch_klass == NULL) {
    bool will_link;
    assert(_loading_klass->get_instanceKlass()->is_linked(), "must be linked before accessing constant pool");
    constantPoolHandle cpool(_loading_klass->get_instanceKlass()->constants());
    ciKlass* k = CURRENT_ENV->get_klass_by_index(cpool,
                                                 _catch_klass_index,
                                                 will_link,
                                                 _loading_klass);
    if (!will_link && k->is_loaded()) {
      GUARDED_VM_ENTRY(
        k = CURRENT_ENV->get_unloaded_klass(_loading_klass, k->name());
      )
    }
    _catch_klass = k->as_instance_klass();
  }
  return _catch_klass;
}

// ------------------------------------------------------------------
// ciExceptionHandler::print()
void ciExceptionHandler::print() {
  tty->print("<ciExceptionHandler start=%d limit=%d"
             " handler_bci=%d ex_klass_index=%d",
             start(), limit(), handler_bci(), catch_klass_index());
  if (_catch_klass != NULL) {
    tty->print(" ex_klass=");
    _catch_klass->print();
  }
  tty->print(">");
}
