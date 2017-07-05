/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_constMethodKlass.cpp.incl"


klassOop constMethodKlass::create_klass(TRAPS) {
  constMethodKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(),
                                    o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()),
         "wrong size for object");
  //java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror
  return k();
}


int constMethodKlass::oop_size(oop obj) const {
  assert(obj->is_constMethod(), "must be constMethod oop");
  return constMethodOop(obj)->object_size();
}

bool constMethodKlass::oop_is_parsable(oop obj) const {
  assert(obj->is_constMethod(), "must be constMethod oop");
  return constMethodOop(obj)->object_is_parsable();
}

bool constMethodKlass::oop_is_conc_safe(oop obj) const {
  assert(obj->is_constMethod(), "must be constMethod oop");
  return constMethodOop(obj)->is_conc_safe();
}

constMethodOop constMethodKlass::allocate(int byte_code_size,
                                          int compressed_line_number_size,
                                          int localvariable_table_length,
                                          int checked_exceptions_length,
                                          bool is_conc_safe,
                                          TRAPS) {

  int size = constMethodOopDesc::object_size(byte_code_size,
                                             compressed_line_number_size,
                                             localvariable_table_length,
                                             checked_exceptions_length);
  KlassHandle h_k(THREAD, as_klassOop());
  constMethodOop cm = (constMethodOop)
    CollectedHeap::permanent_obj_allocate(h_k, size, CHECK_NULL);
  assert(!cm->is_parsable(), "Not yet safely parsable");
  No_Safepoint_Verifier no_safepoint;
  cm->set_interpreter_kind(Interpreter::invalid);
  cm->init_fingerprint();
  cm->set_method(NULL);
  cm->set_stackmap_data(NULL);
  cm->set_exception_table(NULL);
  cm->set_code_size(byte_code_size);
  cm->set_constMethod_size(size);
  cm->set_inlined_tables_length(checked_exceptions_length,
                                compressed_line_number_size,
                                localvariable_table_length);
  assert(cm->size() == size, "wrong size for object");
  cm->set_is_conc_safe(is_conc_safe);
  cm->set_partially_loaded();
  assert(cm->is_parsable(), "Is safely parsable by gc");
  return cm;
}

void constMethodKlass::oop_follow_contents(oop obj) {
  assert (obj->is_constMethod(), "object must be constMethod");
  constMethodOop cm = constMethodOop(obj);
  MarkSweep::mark_and_push(cm->adr_method());
  MarkSweep::mark_and_push(cm->adr_stackmap_data());
  MarkSweep::mark_and_push(cm->adr_exception_table());
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constMethodKlassObj never moves.
}

#ifndef SERIALGC
void constMethodKlass::oop_follow_contents(ParCompactionManager* cm,
                                           oop obj) {
  assert (obj->is_constMethod(), "object must be constMethod");
  constMethodOop cm_oop = constMethodOop(obj);
  PSParallelCompact::mark_and_push(cm, cm_oop->adr_method());
  PSParallelCompact::mark_and_push(cm, cm_oop->adr_stackmap_data());
  PSParallelCompact::mark_and_push(cm, cm_oop->adr_exception_table());
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constMethodKlassObj never moves.
}
#endif // SERIALGC

int constMethodKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert (obj->is_constMethod(), "object must be constMethod");
  constMethodOop cm = constMethodOop(obj);
  blk->do_oop(cm->adr_method());
  blk->do_oop(cm->adr_stackmap_data());
  blk->do_oop(cm->adr_exception_table());
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cm->object_size();
  return size;
}


int constMethodKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  assert (obj->is_constMethod(), "object must be constMethod");
  constMethodOop cm = constMethodOop(obj);
  oop* adr;
  adr = cm->adr_method();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = cm->adr_stackmap_data();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = cm->adr_exception_table();
  if (mr.contains(adr)) blk->do_oop(adr);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cm->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constMethodKlassObj never moves.
  return size;
}


int constMethodKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_constMethod(), "should be constMethod");
  constMethodOop cm = constMethodOop(obj);
  MarkSweep::adjust_pointer(cm->adr_method());
  MarkSweep::adjust_pointer(cm->adr_stackmap_data());
  MarkSweep::adjust_pointer(cm->adr_exception_table());
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = cm->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::constMethodKlassObj never moves.
  return size;
}

#ifndef SERIALGC
void constMethodKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_constMethod(), "should be constMethod");
}

void constMethodKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_constMethod(), "should be constMethod");
}

int constMethodKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert(obj->is_constMethod(), "should be constMethod");
  constMethodOop cm_oop = constMethodOop(obj);
#if 0
  PSParallelCompact::adjust_pointer(cm_oop->adr_method());
  PSParallelCompact::adjust_pointer(cm_oop->adr_exception_table());
  PSParallelCompact::adjust_pointer(cm_oop->adr_stackmap_data());
#endif
  oop* const beg_oop = cm_oop->oop_block_beg();
  oop* const end_oop = cm_oop->oop_block_end();
  for (oop* cur_oop = beg_oop; cur_oop < end_oop; ++cur_oop) {
    PSParallelCompact::adjust_pointer(cur_oop);
  }
  return cm_oop->object_size();
}

int constMethodKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                          HeapWord* beg_addr,
                                          HeapWord* end_addr) {
  assert(obj->is_constMethod(), "should be constMethod");
  constMethodOop cm_oop = constMethodOop(obj);

  oop* const beg_oop = MAX2((oop*)beg_addr, cm_oop->oop_block_beg());
  oop* const end_oop = MIN2((oop*)end_addr, cm_oop->oop_block_end());
  for (oop* cur_oop = beg_oop; cur_oop < end_oop; ++cur_oop) {
    PSParallelCompact::adjust_pointer(cur_oop);
  }

  return cm_oop->object_size();
}
#endif // SERIALGC

// Printing

void constMethodKlass::oop_print_on(oop obj, outputStream* st) {
  ResourceMark rm;
  assert(obj->is_constMethod(), "must be constMethod");
  Klass::oop_print_on(obj, st);
  constMethodOop m = constMethodOop(obj);
  st->print(" - method:       " INTPTR_FORMAT " ", (address)m->method());
  m->method()->print_value_on(st); st->cr();
  st->print(" - exceptions:   " INTPTR_FORMAT "\n", (address)m->exception_table());
  if (m->has_stackmap_table()) {
    st->print(" - stackmap data:       ");
    m->stackmap_data()->print_value_on(st);
    st->cr();
  }
}

// Short version of printing constMethodOop - just print the name of the
// method it belongs to.
void constMethodKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_constMethod(), "must be constMethod");
  constMethodOop m = constMethodOop(obj);
  st->print(" const part of method " );
  m->method()->print_value_on(st);
}

const char* constMethodKlass::internal_name() const {
  return "{constMethod}";
}


// Verification

void constMethodKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  guarantee(obj->is_constMethod(), "object must be constMethod");
  constMethodOop m = constMethodOop(obj);
  guarantee(m->is_perm(),                            "should be in permspace");

  // Verification can occur during oop construction before the method or
  // other fields have been initialized.
  if (!obj->partially_loaded()) {
    guarantee(m->method()->is_perm(), "should be in permspace");
    guarantee(m->method()->is_method(), "should be method");
    typeArrayOop stackmap_data = m->stackmap_data();
    guarantee(stackmap_data == NULL ||
              stackmap_data->is_perm(),  "should be in permspace");
    guarantee(m->exception_table()->is_perm(), "should be in permspace");
    guarantee(m->exception_table()->is_typeArray(), "should be type array");

    address m_end = (address)((oop*) m + m->size());
    address compressed_table_start = m->code_end();
    guarantee(compressed_table_start <= m_end, "invalid method layout");
    address compressed_table_end = compressed_table_start;
    // Verify line number table
    if (m->has_linenumber_table()) {
      CompressedLineNumberReadStream stream(m->compressed_linenumber_table());
      while (stream.read_pair()) {
        guarantee(stream.bci() >= 0 && stream.bci() <= m->code_size(), "invalid bci in line number table");
      }
      compressed_table_end += stream.position();
    }
    guarantee(compressed_table_end <= m_end, "invalid method layout");
    // Verify checked exceptions and local variable tables
    if (m->has_checked_exceptions()) {
      u2* addr = m->checked_exceptions_length_addr();
      guarantee(*addr > 0 && (address) addr >= compressed_table_end && (address) addr < m_end, "invalid method layout");
    }
    if (m->has_localvariable_table()) {
      u2* addr = m->localvariable_table_length_addr();
      guarantee(*addr > 0 && (address) addr >= compressed_table_end && (address) addr < m_end, "invalid method layout");
    }
    // Check compressed_table_end relative to uncompressed_table_start
    u2* uncompressed_table_start;
    if (m->has_localvariable_table()) {
      uncompressed_table_start = (u2*) m->localvariable_table_start();
    } else {
      if (m->has_checked_exceptions()) {
        uncompressed_table_start = (u2*) m->checked_exceptions_start();
      } else {
        uncompressed_table_start = (u2*) m_end;
      }
    }
    int gap = (intptr_t) uncompressed_table_start - (intptr_t) compressed_table_end;
    int max_gap = align_object_size(1)*BytesPerWord;
    guarantee(gap >= 0 && gap < max_gap, "invalid method layout");
  }
}

bool constMethodKlass::oop_partially_loaded(oop obj) const {
  assert(obj->is_constMethod(), "object must be klass");
  constMethodOop m = constMethodOop(obj);
  // check whether exception_table points to self (flag for partially loaded)
  return m->exception_table() == (typeArrayOop)obj;
}


// The exception_table is the last field set when loading an object.
void constMethodKlass::oop_set_partially_loaded(oop obj) {
  assert(obj->is_constMethod(), "object must be klass");
  constMethodOop m = constMethodOop(obj);
  // Temporarily set exception_table to point to self
  m->set_exception_table((typeArrayOop)obj);
}
