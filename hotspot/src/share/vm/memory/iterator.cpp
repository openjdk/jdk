/*
 * Copyright 1997-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_iterator.cpp.incl"

#ifdef ASSERT
bool OopClosure::_must_remember_klasses = false;
#endif

void ObjectToOopClosure::do_object(oop obj) {
  obj->oop_iterate(_cl);
}

void VoidClosure::do_void() {
  ShouldNotCallThis();
}

#ifdef ASSERT
bool OopClosure::must_remember_klasses() {
  return _must_remember_klasses;
}
void OopClosure::set_must_remember_klasses(bool v) {
  _must_remember_klasses = v;
}
#endif


MarkingCodeBlobClosure::MarkScope::MarkScope(bool activate)
  : _active(activate)
{
  if (_active)  nmethod::oops_do_marking_prologue();
}

MarkingCodeBlobClosure::MarkScope::~MarkScope() {
  if (_active)  nmethod::oops_do_marking_epilogue();
}

void MarkingCodeBlobClosure::do_code_blob(CodeBlob* cb) {
  if (!cb->is_nmethod())  return;
  nmethod* nm = (nmethod*) cb;
  if (!nm->test_set_oops_do_mark()) {
    NOT_PRODUCT(if (TraceScavenge)  nm->print_on(tty, "oops_do, 1st visit\n"));
    do_newly_marked_nmethod(nm);
  } else {
    NOT_PRODUCT(if (TraceScavenge)  nm->print_on(tty, "oops_do, skipped on 2nd visit\n"));
  }
}

void CodeBlobToOopClosure::do_newly_marked_nmethod(CodeBlob* cb) {
  cb->oops_do(_cl);
}

void CodeBlobToOopClosure::do_code_blob(CodeBlob* cb) {
  if (!_do_marking) {
    NOT_PRODUCT(if (TraceScavenge && Verbose && cb->is_nmethod())  ((nmethod*)cb)->print_on(tty, "oops_do, unmarked visit\n"));
    // This assert won't work, since there are lots of mini-passes
    // (mostly in debug mode) that co-exist with marking phases.
    //assert(!(cb->is_nmethod() && ((nmethod*)cb)->test_oops_do_mark()), "found marked nmethod during mark-free phase");
    cb->oops_do(_cl);
  } else {
    MarkingCodeBlobClosure::do_code_blob(cb);
  }
}


