/*
 * Copyright (c) 1998, 2009, Oracle and/or its affiliates. All rights reserved.
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
#include "incls/_cpCacheOop.cpp.incl"


// Implememtation of ConstantPoolCacheEntry

void ConstantPoolCacheEntry::initialize_entry(int index) {
  assert(0 < index && index < 0x10000, "sanity check");
  _indices = index;
  assert(constant_pool_index() == index, "");
}

void ConstantPoolCacheEntry::initialize_secondary_entry(int main_index) {
  assert(0 <= main_index && main_index < 0x10000, "sanity check");
  _indices = (main_index << 16);
  assert(main_entry_index() == main_index, "");
}

int ConstantPoolCacheEntry::as_flags(TosState state, bool is_final,
                    bool is_vfinal, bool is_volatile,
                    bool is_method_interface, bool is_method) {
  int f = state;

  assert( state < number_of_states, "Invalid state in as_flags");

  f <<= 1;
  if (is_final) f |= 1;
  f <<= 1;
  if (is_vfinal) f |= 1;
  f <<= 1;
  if (is_volatile) f |= 1;
  f <<= 1;
  if (is_method_interface) f |= 1;
  f <<= 1;
  if (is_method) f |= 1;
  f <<= ConstantPoolCacheEntry::hotSwapBit;
  // Preserve existing flag bit values
#ifdef ASSERT
  int old_state = ((_flags >> tosBits) & 0x0F);
  assert(old_state == 0 || old_state == state,
         "inconsistent cpCache flags state");
#endif
  return (_flags | f) ;
}

void ConstantPoolCacheEntry::set_bytecode_1(Bytecodes::Code code) {
#ifdef ASSERT
  // Read once.
  volatile Bytecodes::Code c = bytecode_1();
  assert(c == 0 || c == code || code == 0, "update must be consistent");
#endif
  // Need to flush pending stores here before bytecode is written.
  OrderAccess::release_store_ptr(&_indices, _indices | ((u_char)code << 16));
}

void ConstantPoolCacheEntry::set_bytecode_2(Bytecodes::Code code) {
#ifdef ASSERT
  // Read once.
  volatile Bytecodes::Code c = bytecode_2();
  assert(c == 0 || c == code || code == 0, "update must be consistent");
#endif
  // Need to flush pending stores here before bytecode is written.
  OrderAccess::release_store_ptr(&_indices, _indices | ((u_char)code << 24));
}

#ifdef ASSERT
// It is possible to have two different dummy methodOops created
// when the resolve code for invoke interface executes concurrently
// Hence the assertion below is weakened a bit for the invokeinterface
// case.
bool ConstantPoolCacheEntry::same_methodOop(oop cur_f1, oop f1) {
  return (cur_f1 == f1 || ((methodOop)cur_f1)->name() ==
         ((methodOop)f1)->name() || ((methodOop)cur_f1)->signature() ==
         ((methodOop)f1)->signature());
}
#endif

// Note that concurrent update of both bytecodes can leave one of them
// reset to zero.  This is harmless; the interpreter will simply re-resolve
// the damaged entry.  More seriously, the memory synchronization is needed
// to flush other fields (f1, f2) completely to memory before the bytecodes
// are updated, lest other processors see a non-zero bytecode but zero f1/f2.
void ConstantPoolCacheEntry::set_field(Bytecodes::Code get_code,
                                       Bytecodes::Code put_code,
                                       KlassHandle field_holder,
                                       int orig_field_index,
                                       int field_offset,
                                       TosState field_type,
                                       bool is_final,
                                       bool is_volatile) {
  set_f1(field_holder());
  set_f2(field_offset);
  // The field index is used by jvm/ti and is the index into fields() array
  // in holder instanceKlass.  This is scaled by instanceKlass::next_offset.
  assert((orig_field_index % instanceKlass::next_offset) == 0, "wierd index");
  const int field_index = orig_field_index / instanceKlass::next_offset;
  assert(field_index <= field_index_mask,
         "field index does not fit in low flag bits");
  set_flags(as_flags(field_type, is_final, false, is_volatile, false, false) |
            (field_index & field_index_mask));
  set_bytecode_1(get_code);
  set_bytecode_2(put_code);
  NOT_PRODUCT(verify(tty));
}

int  ConstantPoolCacheEntry::field_index() const {
  return (_flags & field_index_mask) * instanceKlass::next_offset;
}

void ConstantPoolCacheEntry::set_method(Bytecodes::Code invoke_code,
                                        methodHandle method,
                                        int vtable_index) {
  assert(!is_secondary_entry(), "");
  assert(method->interpreter_entry() != NULL, "should have been set at this point");
  assert(!method->is_obsolete(),  "attempt to write obsolete method to cpCache");
  bool change_to_virtual = (invoke_code == Bytecodes::_invokeinterface);

  int byte_no = -1;
  bool needs_vfinal_flag = false;
  switch (invoke_code) {
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokeinterface: {
        if (method->can_be_statically_bound()) {
          set_f2((intptr_t)method());
          needs_vfinal_flag = true;
        } else {
          assert(vtable_index >= 0, "valid index");
          set_f2(vtable_index);
        }
        byte_no = 2;
        break;
    }

    case Bytecodes::_invokedynamic:  // similar to _invokevirtual
      if (TraceInvokeDynamic) {
        tty->print_cr("InvokeDynamic set_method%s method="PTR_FORMAT" index=%d",
                      (is_secondary_entry() ? " secondary" : ""),
                      (intptr_t)method(), vtable_index);
        method->print();
        this->print(tty, 0);
      }
      assert(method->can_be_statically_bound(), "must be a MH invoker method");
      assert(AllowTransitionalJSR292 || _f2 >= constantPoolOopDesc::CPCACHE_INDEX_TAG, "BSM index initialized");
      set_f1(method());
      needs_vfinal_flag = false;  // _f2 is not an oop
      assert(!is_vfinal(), "f2 not an oop");
      byte_no = 1;  // coordinate this with bytecode_number & is_resolved
      break;

    case Bytecodes::_invokespecial:
      // Preserve the value of the vfinal flag on invokevirtual bytecode
      // which may be shared with this constant pool cache entry.
      needs_vfinal_flag = is_resolved(Bytecodes::_invokevirtual) && is_vfinal();
      // fall through
    case Bytecodes::_invokestatic:
      set_f1(method());
      byte_no = 1;
      break;
    default:
      ShouldNotReachHere();
      break;
  }

  set_flags(as_flags(as_TosState(method->result_type()),
                     method->is_final_method(),
                     needs_vfinal_flag,
                     false,
                     change_to_virtual,
                     true)|
            method()->size_of_parameters());

  // Note:  byte_no also appears in TemplateTable::resolve.
  if (byte_no == 1) {
    set_bytecode_1(invoke_code);
  } else if (byte_no == 2)  {
    if (change_to_virtual) {
      // NOTE: THIS IS A HACK - BE VERY CAREFUL!!!
      //
      // Workaround for the case where we encounter an invokeinterface, but we
      // should really have an _invokevirtual since the resolved method is a
      // virtual method in java.lang.Object. This is a corner case in the spec
      // but is presumably legal. javac does not generate this code.
      //
      // We set bytecode_1() to _invokeinterface, because that is the
      // bytecode # used by the interpreter to see if it is resolved.
      // We set bytecode_2() to _invokevirtual.
      // See also interpreterRuntime.cpp. (8/25/2000)
      // Only set resolved for the invokeinterface case if method is public.
      // Otherwise, the method needs to be reresolved with caller for each
      // interface call.
      if (method->is_public()) set_bytecode_1(invoke_code);
      set_bytecode_2(Bytecodes::_invokevirtual);
    } else {
      set_bytecode_2(invoke_code);
    }
  } else {
    ShouldNotReachHere();
  }
  NOT_PRODUCT(verify(tty));
}


void ConstantPoolCacheEntry::set_interface_call(methodHandle method, int index) {
  assert(!is_secondary_entry(), "");
  klassOop interf = method->method_holder();
  assert(instanceKlass::cast(interf)->is_interface(), "must be an interface");
  set_f1(interf);
  set_f2(index);
  set_flags(as_flags(as_TosState(method->result_type()), method->is_final_method(), false, false, false, true) | method()->size_of_parameters());
  set_bytecode_1(Bytecodes::_invokeinterface);
}


void ConstantPoolCacheEntry::initialize_bootstrap_method_index_in_cache(int bsm_cache_index) {
  assert(!is_secondary_entry(), "only for JVM_CONSTANT_InvokeDynamic main entry");
  assert(_f2 == 0, "initialize once");
  assert(bsm_cache_index == (int)(u2)bsm_cache_index, "oob");
  set_f2(bsm_cache_index + constantPoolOopDesc::CPCACHE_INDEX_TAG);
}

int ConstantPoolCacheEntry::bootstrap_method_index_in_cache() {
  assert(!is_secondary_entry(), "only for JVM_CONSTANT_InvokeDynamic main entry");
  intptr_t bsm_cache_index = (intptr_t) _f2 - constantPoolOopDesc::CPCACHE_INDEX_TAG;
  assert(bsm_cache_index == (intptr_t)(u2)bsm_cache_index, "oob");
  return (int) bsm_cache_index;
}

void ConstantPoolCacheEntry::set_dynamic_call(Handle call_site,
                                              methodHandle signature_invoker) {
  assert(is_secondary_entry(), "");
  int param_size = signature_invoker->size_of_parameters();
  assert(param_size >= 1, "method argument size must include MH.this");
  param_size -= 1;              // do not count MH.this; it is not stacked for invokedynamic
  if (Atomic::cmpxchg_ptr(call_site(), &_f1, NULL) == NULL) {
    // racing threads might be trying to install their own favorites
    set_f1(call_site());
  }
  bool is_final = true;
  assert(signature_invoker->is_final_method(), "is_final");
  set_flags(as_flags(as_TosState(signature_invoker->result_type()), is_final, false, false, false, true) | param_size);
  // do not do set_bytecode on a secondary CP cache entry
  //set_bytecode_1(Bytecodes::_invokedynamic);
}


class LocalOopClosure: public OopClosure {
 private:
  void (*_f)(oop*);

 public:
  LocalOopClosure(void f(oop*))        { _f = f; }
  virtual void do_oop(oop* o)          { _f(o); }
  virtual void do_oop(narrowOop *o)    { ShouldNotReachHere(); }
};


void ConstantPoolCacheEntry::oops_do(void f(oop*)) {
  LocalOopClosure blk(f);
  oop_iterate(&blk);
}


void ConstantPoolCacheEntry::oop_iterate(OopClosure* blk) {
  assert(in_words(size()) == 4, "check code below - may need adjustment");
  // field[1] is always oop or NULL
  blk->do_oop((oop*)&_f1);
  if (is_vfinal()) {
    blk->do_oop((oop*)&_f2);
  }
}


void ConstantPoolCacheEntry::oop_iterate_m(OopClosure* blk, MemRegion mr) {
  assert(in_words(size()) == 4, "check code below - may need adjustment");
  // field[1] is always oop or NULL
  if (mr.contains((oop *)&_f1)) blk->do_oop((oop*)&_f1);
  if (is_vfinal()) {
    if (mr.contains((oop *)&_f2)) blk->do_oop((oop*)&_f2);
  }
}


void ConstantPoolCacheEntry::follow_contents() {
  assert(in_words(size()) == 4, "check code below - may need adjustment");
  // field[1] is always oop or NULL
  MarkSweep::mark_and_push((oop*)&_f1);
  if (is_vfinal()) {
    MarkSweep::mark_and_push((oop*)&_f2);
  }
}

#ifndef SERIALGC
void ConstantPoolCacheEntry::follow_contents(ParCompactionManager* cm) {
  assert(in_words(size()) == 4, "check code below - may need adjustment");
  // field[1] is always oop or NULL
  PSParallelCompact::mark_and_push(cm, (oop*)&_f1);
  if (is_vfinal()) {
    PSParallelCompact::mark_and_push(cm, (oop*)&_f2);
  }
}
#endif // SERIALGC

void ConstantPoolCacheEntry::adjust_pointers() {
  assert(in_words(size()) == 4, "check code below - may need adjustment");
  // field[1] is always oop or NULL
  MarkSweep::adjust_pointer((oop*)&_f1);
  if (is_vfinal()) {
    MarkSweep::adjust_pointer((oop*)&_f2);
  }
}

#ifndef SERIALGC
void ConstantPoolCacheEntry::update_pointers() {
  assert(in_words(size()) == 4, "check code below - may need adjustment");
  // field[1] is always oop or NULL
  PSParallelCompact::adjust_pointer((oop*)&_f1);
  if (is_vfinal()) {
    PSParallelCompact::adjust_pointer((oop*)&_f2);
  }
}

void ConstantPoolCacheEntry::update_pointers(HeapWord* beg_addr,
                                             HeapWord* end_addr) {
  assert(in_words(size()) == 4, "check code below - may need adjustment");
  // field[1] is always oop or NULL
  PSParallelCompact::adjust_pointer((oop*)&_f1, beg_addr, end_addr);
  if (is_vfinal()) {
    PSParallelCompact::adjust_pointer((oop*)&_f2, beg_addr, end_addr);
  }
}
#endif // SERIALGC

// RedefineClasses() API support:
// If this constantPoolCacheEntry refers to old_method then update it
// to refer to new_method.
bool ConstantPoolCacheEntry::adjust_method_entry(methodOop old_method,
       methodOop new_method, bool * trace_name_printed) {

  if (is_vfinal()) {
    // virtual and final so f2() contains method ptr instead of vtable index
    if (f2() == (intptr_t)old_method) {
      // match old_method so need an update
      _f2 = (intptr_t)new_method;
      if (RC_TRACE_IN_RANGE(0x00100000, 0x00400000)) {
        if (!(*trace_name_printed)) {
          // RC_TRACE_MESG macro has an embedded ResourceMark
          RC_TRACE_MESG(("adjust: name=%s",
            Klass::cast(old_method->method_holder())->external_name()));
          *trace_name_printed = true;
        }
        // RC_TRACE macro has an embedded ResourceMark
        RC_TRACE(0x00400000, ("cpc vf-entry update: %s(%s)",
          new_method->name()->as_C_string(),
          new_method->signature()->as_C_string()));
      }

      return true;
    }

    // f1() is not used with virtual entries so bail out
    return false;
  }

  if ((oop)_f1 == NULL) {
    // NULL f1() means this is a virtual entry so bail out
    // We are assuming that the vtable index does not need change.
    return false;
  }

  if ((oop)_f1 == old_method) {
    _f1 = new_method;
    if (RC_TRACE_IN_RANGE(0x00100000, 0x00400000)) {
      if (!(*trace_name_printed)) {
        // RC_TRACE_MESG macro has an embedded ResourceMark
        RC_TRACE_MESG(("adjust: name=%s",
          Klass::cast(old_method->method_holder())->external_name()));
        *trace_name_printed = true;
      }
      // RC_TRACE macro has an embedded ResourceMark
      RC_TRACE(0x00400000, ("cpc entry update: %s(%s)",
        new_method->name()->as_C_string(),
        new_method->signature()->as_C_string()));
    }

    return true;
  }

  return false;
}

bool ConstantPoolCacheEntry::is_interesting_method_entry(klassOop k) {
  if (!is_method_entry()) {
    // not a method entry so not interesting by default
    return false;
  }

  methodOop m = NULL;
  if (is_vfinal()) {
    // virtual and final so _f2 contains method ptr instead of vtable index
    m = (methodOop)_f2;
  } else if ((oop)_f1 == NULL) {
    // NULL _f1 means this is a virtual entry so also not interesting
    return false;
  } else {
    if (!((oop)_f1)->is_method()) {
      // _f1 can also contain a klassOop for an interface
      return false;
    }
    m = (methodOop)_f1;
  }

  assert(m != NULL && m->is_method(), "sanity check");
  if (m == NULL || !m->is_method() || m->method_holder() != k) {
    // robustness for above sanity checks or method is not in
    // the interesting class
    return false;
  }

  // the method is in the interesting class so the entry is interesting
  return true;
}

void ConstantPoolCacheEntry::print(outputStream* st, int index) const {
  // print separator
  if (index == 0) tty->print_cr("                 -------------");
  // print entry
  tty->print("%3d  ("PTR_FORMAT")  ", index, (intptr_t)this);
  if (is_secondary_entry())
    tty->print_cr("[%5d|secondary]", main_entry_index());
  else
    tty->print_cr("[%02x|%02x|%5d]", bytecode_2(), bytecode_1(), constant_pool_index());
  tty->print_cr("                 [   "PTR_FORMAT"]", (intptr_t)(oop)_f1);
  tty->print_cr("                 [   "PTR_FORMAT"]", (intptr_t)_f2);
  tty->print_cr("                 [   "PTR_FORMAT"]", (intptr_t)_flags);
  tty->print_cr("                 -------------");
}

void ConstantPoolCacheEntry::verify(outputStream* st) const {
  // not implemented yet
}

// Implementation of ConstantPoolCache

void constantPoolCacheOopDesc::initialize(intArray& inverse_index_map) {
  assert(inverse_index_map.length() == length(), "inverse index map must have same length as cache");
  for (int i = 0; i < length(); i++) {
    ConstantPoolCacheEntry* e = entry_at(i);
    int original_index = inverse_index_map[i];
    if ((original_index & Rewriter::_secondary_entry_tag) != 0) {
      int main_index = (original_index - Rewriter::_secondary_entry_tag);
      assert(!entry_at(main_index)->is_secondary_entry(), "valid main index");
      e->initialize_secondary_entry(main_index);
    } else {
      e->initialize_entry(original_index);
    }
    assert(entry_at(i) == e, "sanity");
  }
}

// RedefineClasses() API support:
// If any entry of this constantPoolCache points to any of
// old_methods, replace it with the corresponding new_method.
void constantPoolCacheOopDesc::adjust_method_entries(methodOop* old_methods, methodOop* new_methods,
                                                     int methods_length, bool * trace_name_printed) {

  if (methods_length == 0) {
    // nothing to do if there are no methods
    return;
  }

  // get shorthand for the interesting class
  klassOop old_holder = old_methods[0]->method_holder();

  for (int i = 0; i < length(); i++) {
    if (!entry_at(i)->is_interesting_method_entry(old_holder)) {
      // skip uninteresting methods
      continue;
    }

    // The constantPoolCache contains entries for several different
    // things, but we only care about methods. In fact, we only care
    // about methods in the same class as the one that contains the
    // old_methods. At this point, we have an interesting entry.

    for (int j = 0; j < methods_length; j++) {
      methodOop old_method = old_methods[j];
      methodOop new_method = new_methods[j];

      if (entry_at(i)->adjust_method_entry(old_method, new_method,
          trace_name_printed)) {
        // current old_method matched this entry and we updated it so
        // break out and get to the next interesting entry if there one
        break;
      }
    }
  }
}
