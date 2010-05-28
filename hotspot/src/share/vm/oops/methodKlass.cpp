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
# include "incls/_methodKlass.cpp.incl"

klassOop methodKlass::create_klass(TRAPS) {
  methodKlass o;
  KlassHandle h_this_klass(THREAD, Universe::klassKlassObj());
  KlassHandle k = base_create_klass(h_this_klass, header_size(), o.vtbl_value(), CHECK_NULL);
  // Make sure size calculation is right
  assert(k()->size() == align_object_size(header_size()), "wrong size for object");
  java_lang_Class::create_mirror(k, CHECK_NULL); // Allocate mirror
  return k();
}


int methodKlass::oop_size(oop obj) const {
  assert(obj->is_method(), "must be method oop");
  return methodOop(obj)->object_size();
}


bool methodKlass::oop_is_parsable(oop obj) const {
  assert(obj->is_method(), "must be method oop");
  return methodOop(obj)->object_is_parsable();
}


methodOop methodKlass::allocate(constMethodHandle xconst,
                                AccessFlags access_flags, TRAPS) {
  int size = methodOopDesc::object_size(access_flags.is_native());
  KlassHandle h_k(THREAD, as_klassOop());
  assert(xconst()->is_parsable(), "possible publication protocol violation");
  methodOop m = (methodOop)CollectedHeap::permanent_obj_allocate(h_k, size, CHECK_NULL);
  assert(!m->is_parsable(), "not expecting parsability yet.");

  No_Safepoint_Verifier no_safepoint;  // until m becomes parsable below
  m->set_constMethod(xconst());
  m->set_access_flags(access_flags);
  m->set_method_size(size);
  m->set_name_index(0);
  m->set_signature_index(0);
#ifdef CC_INTERP
  m->set_result_index(T_VOID);
#endif
  m->set_constants(NULL);
  m->set_max_stack(0);
  m->set_max_locals(0);
  m->set_intrinsic_id(vmIntrinsics::_none);
  m->set_method_data(NULL);
  m->set_interpreter_throwout_count(0);
  m->set_vtable_index(methodOopDesc::garbage_vtable_index);

  // Fix and bury in methodOop
  m->set_interpreter_entry(NULL); // sets i2i entry and from_int
  m->set_highest_tier_compile(CompLevel_none);
  m->set_adapter_entry(NULL);
  m->clear_code(); // from_c/from_i get set to c2i/i2i

  if (access_flags.is_native()) {
    m->clear_native_function();
    m->set_signature_handler(NULL);
  }

  NOT_PRODUCT(m->set_compiled_invocation_count(0);)
  m->set_interpreter_invocation_count(0);
  m->invocation_counter()->init();
  m->backedge_counter()->init();
  m->clear_number_of_breakpoints();
  assert(m->is_parsable(), "must be parsable here.");
  assert(m->size() == size, "wrong size for object");
  // We should not publish an uprasable object's reference
  // into one that is parsable, since that presents problems
  // for the concurrent parallel marking and precleaning phases
  // of concurrent gc (CMS).
  xconst->set_method(m);
  return m;
}


void methodKlass::oop_follow_contents(oop obj) {
  assert (obj->is_method(), "object must be method");
  methodOop m = methodOop(obj);
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::methodKlassObj never moves.
  MarkSweep::mark_and_push(m->adr_constMethod());
  MarkSweep::mark_and_push(m->adr_constants());
  if (m->method_data() != NULL) {
    MarkSweep::mark_and_push(m->adr_method_data());
  }
}

#ifndef SERIALGC
void methodKlass::oop_follow_contents(ParCompactionManager* cm,
                                      oop obj) {
  assert (obj->is_method(), "object must be method");
  methodOop m = methodOop(obj);
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::methodKlassObj never moves.
  PSParallelCompact::mark_and_push(cm, m->adr_constMethod());
  PSParallelCompact::mark_and_push(cm, m->adr_constants());
#ifdef COMPILER2
  if (m->method_data() != NULL) {
    PSParallelCompact::mark_and_push(cm, m->adr_method_data());
  }
#endif // COMPILER2
}
#endif // SERIALGC

int methodKlass::oop_oop_iterate(oop obj, OopClosure* blk) {
  assert (obj->is_method(), "object must be method");
  methodOop m = methodOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = m->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::methodKlassObj never moves
  blk->do_oop(m->adr_constMethod());
  blk->do_oop(m->adr_constants());
  if (m->method_data() != NULL) {
    blk->do_oop(m->adr_method_data());
  }
  return size;
}


int methodKlass::oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
  assert (obj->is_method(), "object must be method");
  methodOop m = methodOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = m->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::methodKlassObj never moves.
  oop* adr;
  adr = m->adr_constMethod();
  if (mr.contains(adr)) blk->do_oop(adr);
  adr = m->adr_constants();
  if (mr.contains(adr)) blk->do_oop(adr);
  if (m->method_data() != NULL) {
    adr = m->adr_method_data();
    if (mr.contains(adr)) blk->do_oop(adr);
  }
  return size;
}


int methodKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_method(), "should be method");
  methodOop m = methodOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = m->object_size();
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::methodKlassObj never moves.
  MarkSweep::adjust_pointer(m->adr_constMethod());
  MarkSweep::adjust_pointer(m->adr_constants());
  if (m->method_data() != NULL) {
    MarkSweep::adjust_pointer(m->adr_method_data());
  }
  return size;
}

#ifndef SERIALGC
void methodKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_method(), "should be method");
}

void methodKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(obj->is_method(), "should be method");
}

int methodKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert(obj->is_method(), "should be method");
  methodOop m = methodOop(obj);
  PSParallelCompact::adjust_pointer(m->adr_constMethod());
  PSParallelCompact::adjust_pointer(m->adr_constants());
#ifdef COMPILER2
  if (m->method_data() != NULL) {
    PSParallelCompact::adjust_pointer(m->adr_method_data());
  }
#endif // COMPILER2
  return m->object_size();
}

int methodKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                     HeapWord* beg_addr, HeapWord* end_addr) {
  assert(obj->is_method(), "should be method");

  oop* p;
  methodOop m = methodOop(obj);

  p = m->adr_constMethod();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  p = m->adr_constants();
  PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);

#ifdef COMPILER2
  if (m->method_data() != NULL) {
    p = m->adr_method_data();
    PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
  }
#endif // COMPILER2
  return m->object_size();
}
#endif // SERIALGC

#ifndef PRODUCT

// Printing

void methodKlass::oop_print_on(oop obj, outputStream* st) {
  ResourceMark rm;
  assert(obj->is_method(), "must be method");
  Klass::oop_print_on(obj, st);
  methodOop m = methodOop(obj);
  // get the effect of PrintOopAddress, always, for methods:
  st->print_cr(" - this oop:          "INTPTR_FORMAT, (intptr_t)m);
  st->print   (" - method holder:     ");    m->method_holder()->print_value_on(st); st->cr();
  st->print   (" - constants:         "INTPTR_FORMAT" ", (address)m->constants());
  m->constants()->print_value_on(st); st->cr();
  st->print   (" - access:            0x%x  ", m->access_flags().as_int()); m->access_flags().print_on(st); st->cr();
  st->print   (" - name:              ");    m->name()->print_value_on(st); st->cr();
  st->print   (" - signature:         ");    m->signature()->print_value_on(st); st->cr();
  st->print_cr(" - max stack:         %d",   m->max_stack());
  st->print_cr(" - max locals:        %d",   m->max_locals());
  st->print_cr(" - size of params:    %d",   m->size_of_parameters());
  st->print_cr(" - method size:       %d",   m->method_size());
  if (m->intrinsic_id() != vmIntrinsics::_none)
    st->print_cr(" - intrinsic id:      %d %s", m->intrinsic_id(), vmIntrinsics::name_at(m->intrinsic_id()));
  if (m->highest_tier_compile() != CompLevel_none)
    st->print_cr(" - highest tier:      %d", m->highest_tier_compile());
  st->print_cr(" - vtable index:      %d",   m->_vtable_index);
  st->print_cr(" - i2i entry:         " INTPTR_FORMAT, m->interpreter_entry());
  st->print_cr(" - adapter:           " INTPTR_FORMAT, m->adapter());
  st->print_cr(" - compiled entry     " INTPTR_FORMAT, m->from_compiled_entry());
  st->print_cr(" - code size:         %d",   m->code_size());
  if (m->code_size() != 0) {
    st->print_cr(" - code start:        " INTPTR_FORMAT, m->code_base());
    st->print_cr(" - code end (excl):   " INTPTR_FORMAT, m->code_base() + m->code_size());
  }
  if (m->method_data() != NULL) {
    st->print_cr(" - method data:       " INTPTR_FORMAT, (address)m->method_data());
  }
  st->print_cr(" - checked ex length: %d",   m->checked_exceptions_length());
  if (m->checked_exceptions_length() > 0) {
    CheckedExceptionElement* table = m->checked_exceptions_start();
    st->print_cr(" - checked ex start:  " INTPTR_FORMAT, table);
    if (Verbose) {
      for (int i = 0; i < m->checked_exceptions_length(); i++) {
        st->print_cr("   - throws %s", m->constants()->printable_name_at(table[i].class_cp_index));
      }
    }
  }
  if (m->has_linenumber_table()) {
    u_char* table = m->compressed_linenumber_table();
    st->print_cr(" - linenumber start:  " INTPTR_FORMAT, table);
    if (Verbose) {
      CompressedLineNumberReadStream stream(table);
      while (stream.read_pair()) {
        st->print_cr("   - line %d: %d", stream.line(), stream.bci());
      }
    }
  }
  st->print_cr(" - localvar length:   %d",   m->localvariable_table_length());
  if (m->localvariable_table_length() > 0) {
    LocalVariableTableElement* table = m->localvariable_table_start();
    st->print_cr(" - localvar start:    " INTPTR_FORMAT, table);
    if (Verbose) {
      for (int i = 0; i < m->localvariable_table_length(); i++) {
        int bci = table[i].start_bci;
        int len = table[i].length;
        const char* name = m->constants()->printable_name_at(table[i].name_cp_index);
        const char* desc = m->constants()->printable_name_at(table[i].descriptor_cp_index);
        int slot = table[i].slot;
        st->print_cr("   - %s %s bci=%d len=%d slot=%d", desc, name, bci, len, slot);
      }
    }
  }
  if (m->code() != NULL) {
    st->print   (" - compiled code: ");
    m->code()->print_value_on(st);
    st->cr();
  }
  if (m->is_method_handle_invoke()) {
    st->print_cr(" - invoke method type: " INTPTR_FORMAT, (address) m->method_handle_type());
    // m is classified as native, but it does not have an interesting
    // native_function or signature handler
  } else if (m->is_native()) {
    st->print_cr(" - native function:   " INTPTR_FORMAT, m->native_function());
    st->print_cr(" - signature handler: " INTPTR_FORMAT, m->signature_handler());
  }
}

#endif //PRODUCT

void methodKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_method(), "must be method");
  Klass::oop_print_value_on(obj, st);
  methodOop m = methodOop(obj);
  st->print(" ");
  m->name()->print_value_on(st);
  st->print(" ");
  m->signature()->print_value_on(st);
  st->print(" in ");
  m->method_holder()->print_value_on(st);
  if (WizardMode) st->print("[%d,%d]", m->size_of_parameters(), m->max_locals());
  if (WizardMode && m->code() != NULL) st->print(" ((nmethod*)%p)", m->code());
}

const char* methodKlass::internal_name() const {
  return "{method}";
}


// Verification

void methodKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  guarantee(obj->is_method(), "object must be method");
  if (!obj->partially_loaded()) {
    methodOop m = methodOop(obj);
    guarantee(m->is_perm(),  "should be in permspace");
    guarantee(m->name()->is_perm(), "should be in permspace");
    guarantee(m->name()->is_symbol(), "should be symbol");
    guarantee(m->signature()->is_perm(), "should be in permspace");
    guarantee(m->signature()->is_symbol(), "should be symbol");
    guarantee(m->constants()->is_perm(), "should be in permspace");
    guarantee(m->constants()->is_constantPool(), "should be constant pool");
    guarantee(m->constMethod()->is_constMethod(), "should be constMethodOop");
    guarantee(m->constMethod()->is_perm(), "should be in permspace");
    methodDataOop method_data = m->method_data();
    guarantee(method_data == NULL ||
              method_data->is_perm(), "should be in permspace");
    guarantee(method_data == NULL ||
              method_data->is_methodData(), "should be method data");
  }
}

bool methodKlass::oop_partially_loaded(oop obj) const {
  assert(obj->is_method(), "object must be method");
  methodOop m = methodOop(obj);
  constMethodOop xconst = m->constMethod();
  assert(xconst != NULL, "const method must be set");
  constMethodKlass* ck = constMethodKlass::cast(xconst->klass());
  return ck->oop_partially_loaded(xconst);
}


void methodKlass::oop_set_partially_loaded(oop obj) {
  assert(obj->is_method(), "object must be method");
  methodOop m = methodOop(obj);
  constMethodOop xconst = m->constMethod();
  assert(xconst != NULL, "const method must be set");
  constMethodKlass* ck = constMethodKlass::cast(xconst->klass());
  ck->oop_set_partially_loaded(xconst);
}
