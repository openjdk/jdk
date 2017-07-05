/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_oop.cpp.incl"

bool always_do_update_barrier = false;

BarrierSet* oopDesc::_bs = NULL;

#ifdef PRODUCT
void oopDesc::print_on(outputStream* st) const {}
void oopDesc::print_address_on(outputStream* st) const {}
char* oopDesc::print_string() { return NULL; }
void oopDesc::print()         {}
void oopDesc::print_address() {}

#else //PRODUCT

void oopDesc::print_on(outputStream* st) const {
  if (this == NULL) {
    st->print_cr("NULL");
  } else {
    blueprint()->oop_print_on(oop(this), st);
  }
}

void oopDesc::print_address_on(outputStream* st) const {
  if (PrintOopAddress) {
    st->print("{"INTPTR_FORMAT"}", this);
  }
}

void oopDesc::print()         { print_on(tty);         }

void oopDesc::print_address() { print_address_on(tty); }

char* oopDesc::print_string() {
  stringStream st;
  print_on(&st);
  return st.as_string();
}

#endif // PRODUCT

// The print_value functions are present in all builds, to support the disassembler.

void oopDesc::print_value() {
  print_value_on(tty);
}

char* oopDesc::print_value_string() {
  char buf[100];
  stringStream st(buf, sizeof(buf));
  print_value_on(&st);
  return st.as_string();
}

void oopDesc::print_value_on(outputStream* st) const {
  oop obj = oop(this);
  if (this == NULL) {
    st->print("NULL");
  } else if (java_lang_String::is_instance(obj)) {
    java_lang_String::print(obj, st);
#ifndef PRODUCT
    if (PrintOopAddress) print_address_on(st);
#endif //PRODUCT
#ifdef ASSERT
  } else if (!Universe::heap()->is_in(obj) || !Universe::heap()->is_in(klass())) {
    st->print("### BAD OOP %p ###", (address)obj);
#endif //ASSERT
  } else {
    blueprint()->oop_print_value_on(obj, st);
  }
}


void oopDesc::verify_on(outputStream* st) {
  if (this != NULL) {
    blueprint()->oop_verify_on(this, st);
  }
}


void oopDesc::verify() {
  verify_on(tty);
}


// XXX verify_old_oop doesn't do anything (should we remove?)
void oopDesc::verify_old_oop(oop* p, bool allow_dirty) {
  blueprint()->oop_verify_old_oop(this, p, allow_dirty);
}

void oopDesc::verify_old_oop(narrowOop* p, bool allow_dirty) {
  blueprint()->oop_verify_old_oop(this, p, allow_dirty);
}

bool oopDesc::partially_loaded() {
  return blueprint()->oop_partially_loaded(this);
}


void oopDesc::set_partially_loaded() {
  blueprint()->oop_set_partially_loaded(this);
}


intptr_t oopDesc::slow_identity_hash() {
  // slow case; we have to acquire the micro lock in order to locate the header
  ResetNoHandleMark rnm; // Might be called from LEAF/QUICK ENTRY
  HandleMark hm;
  Handle object((oop)this);
  assert(!is_shared_readonly(), "using identity hash on readonly object?");
  return ObjectSynchronizer::identity_hash_value_for(object);
}

VerifyOopClosure VerifyOopClosure::verify_oop;

void VerifyOopClosure::do_oop(oop* p)       { VerifyOopClosure::do_oop_work(p); }
void VerifyOopClosure::do_oop(narrowOop* p) { VerifyOopClosure::do_oop_work(p); }
