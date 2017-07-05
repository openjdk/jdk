/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/rewriter.hpp"
#include "memory/universe.inline.hpp"
#include "oops/cpCacheOop.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/handles.inline.hpp"


// Implememtation of ConstantPoolCacheEntry

void ConstantPoolCacheEntry::initialize_entry(int index) {
  assert(0 < index && index < 0x10000, "sanity check");
  _indices = index;
  assert(constant_pool_index() == index, "");
}

void ConstantPoolCacheEntry::initialize_secondary_entry(int main_index) {
  assert(0 <= main_index && main_index < 0x10000, "sanity check");
  _indices = (main_index << main_cp_index_bits);
  assert(main_entry_index() == main_index, "");
}

int ConstantPoolCacheEntry::make_flags(TosState state,
                                       int option_bits,
                                       int field_index_or_method_params) {
  assert(state < number_of_states, "Invalid state in make_flags");
  int f = ((int)state << tos_state_shift) | option_bits | field_index_or_method_params;
  // Preserve existing flag bit values
  // The low bits are a field offset, or else the method parameter size.
#ifdef ASSERT
  TosState old_state = flag_state();
  assert(old_state == (TosState)0 || old_state == state,
         "inconsistent cpCache flags state");
#endif
  return (_flags | f) ;
}

void ConstantPoolCacheEntry::set_bytecode_1(Bytecodes::Code code) {
  assert(!is_secondary_entry(), "must not overwrite main_entry_index");
#ifdef ASSERT
  // Read once.
  volatile Bytecodes::Code c = bytecode_1();
  assert(c == 0 || c == code || code == 0, "update must be consistent");
#endif
  // Need to flush pending stores here before bytecode is written.
  OrderAccess::release_store_ptr(&_indices, _indices | ((u_char)code << bytecode_1_shift));
}

void ConstantPoolCacheEntry::set_bytecode_2(Bytecodes::Code code) {
  assert(!is_secondary_entry(), "must not overwrite main_entry_index");
#ifdef ASSERT
  // Read once.
  volatile Bytecodes::Code c = bytecode_2();
  assert(c == 0 || c == code || code == 0, "update must be consistent");
#endif
  // Need to flush pending stores here before bytecode is written.
  OrderAccess::release_store_ptr(&_indices, _indices | ((u_char)code << bytecode_2_shift));
}

// Sets f1, ordering with previous writes.
void ConstantPoolCacheEntry::release_set_f1(oop f1) {
  // Use barriers as in oop_store
  assert(f1 != NULL, "");
  oop* f1_addr = (oop*) &_f1;
  update_barrier_set_pre(f1_addr, f1);
  OrderAccess::release_store_ptr((intptr_t*)f1_addr, f1);
  update_barrier_set((void*) f1_addr, f1);
}

// Sets flags, but only if the value was previously zero.
bool ConstantPoolCacheEntry::init_flags_atomic(intptr_t flags) {
  intptr_t result = Atomic::cmpxchg_ptr(flags, &_flags, 0);
  return (result == 0);
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
                                       int field_index,
                                       int field_offset,
                                       TosState field_type,
                                       bool is_final,
                                       bool is_volatile) {
  set_f1(field_holder()->java_mirror());
  set_f2(field_offset);
  assert((field_index & field_index_mask) == field_index,
         "field index does not fit in low flag bits");
  set_field_flags(field_type,
                  ((is_volatile ? 1 : 0) << is_volatile_shift) |
                  ((is_final    ? 1 : 0) << is_final_shift),
                  field_index);
  set_bytecode_1(get_code);
  set_bytecode_2(put_code);
  NOT_PRODUCT(verify(tty));
}

void ConstantPoolCacheEntry::set_parameter_size(int value) {
  // This routine is called only in corner cases where the CPCE is not yet initialized.
  // See AbstractInterpreter::deopt_continue_after_entry.
  assert(_flags == 0 || parameter_size() == 0 || parameter_size() == value,
         err_msg("size must not change: parameter_size=%d, value=%d", parameter_size(), value));
  // Setting the parameter size by itself is only safe if the
  // current value of _flags is 0, otherwise another thread may have
  // updated it and we don't want to overwrite that value.  Don't
  // bother trying to update it once it's nonzero but always make
  // sure that the final parameter size agrees with what was passed.
  if (_flags == 0) {
    Atomic::cmpxchg_ptr((value & parameter_size_mask), &_flags, 0);
  }
  guarantee(parameter_size() == value,
            err_msg("size must not change: parameter_size=%d, value=%d", parameter_size(), value));
}

void ConstantPoolCacheEntry::set_method(Bytecodes::Code invoke_code,
                                        methodHandle method,
                                        int vtable_index) {
  assert(!is_secondary_entry(), "");
  assert(method->interpreter_entry() != NULL, "should have been set at this point");
  assert(!method->is_obsolete(),  "attempt to write obsolete method to cpCache");

  int byte_no = -1;
  bool change_to_virtual = false;

  switch (invoke_code) {
    case Bytecodes::_invokeinterface:
      // We get here from InterpreterRuntime::resolve_invoke when an invokeinterface
      // instruction somehow links to a non-interface method (in Object).
      // In that case, the method has no itable index and must be invoked as a virtual.
      // Set a flag to keep track of this corner case.
      change_to_virtual = true;

      // ...and fall through as if we were handling invokevirtual:
    case Bytecodes::_invokevirtual:
      {
        if (method->can_be_statically_bound()) {
          // set_f2_as_vfinal_method checks if is_vfinal flag is true.
          set_method_flags(as_TosState(method->result_type()),
                           (                             1      << is_vfinal_shift) |
                           ((method->is_final_method() ? 1 : 0) << is_final_shift)  |
                           ((change_to_virtual         ? 1 : 0) << is_forced_virtual_shift),
                           method()->size_of_parameters());
          set_f2_as_vfinal_method(method());
        } else {
          assert(vtable_index >= 0, "valid index");
          assert(!method->is_final_method(), "sanity");
          set_method_flags(as_TosState(method->result_type()),
                           ((change_to_virtual ? 1 : 0) << is_forced_virtual_shift),
                           method()->size_of_parameters());
          set_f2(vtable_index);
        }
        byte_no = 2;
        break;
      }

    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
      // Note:  Read and preserve the value of the is_vfinal flag on any
      // invokevirtual bytecode shared with this constant pool cache entry.
      // It is cheap and safe to consult is_vfinal() at all times.
      // Once is_vfinal is set, it must stay that way, lest we get a dangling oop.
      set_method_flags(as_TosState(method->result_type()),
                       ((is_vfinal()               ? 1 : 0) << is_vfinal_shift) |
                       ((method->is_final_method() ? 1 : 0) << is_final_shift),
                       method()->size_of_parameters());
      set_f1(method());
      byte_no = 1;
      break;
    default:
      ShouldNotReachHere();
      break;
  }

  // Note:  byte_no also appears in TemplateTable::resolve.
  if (byte_no == 1) {
    assert(invoke_code != Bytecodes::_invokevirtual &&
           invoke_code != Bytecodes::_invokeinterface, "");
    set_bytecode_1(invoke_code);
  } else if (byte_no == 2)  {
    if (change_to_virtual) {
      assert(invoke_code == Bytecodes::_invokeinterface, "");
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
    } else {
      assert(invoke_code == Bytecodes::_invokevirtual, "");
    }
    // set up for invokevirtual, even if linking for invokeinterface also:
    set_bytecode_2(Bytecodes::_invokevirtual);
  } else {
    ShouldNotReachHere();
  }
  NOT_PRODUCT(verify(tty));
}


void ConstantPoolCacheEntry::set_interface_call(methodHandle method, int index) {
  assert(!is_secondary_entry(), "");
  klassOop interf = method->method_holder();
  assert(instanceKlass::cast(interf)->is_interface(), "must be an interface");
  assert(!method->is_final_method(), "interfaces do not have final methods; cannot link to one here");
  set_f1(interf);
  set_f2(index);
  set_method_flags(as_TosState(method->result_type()),
                   0,  // no option bits
                   method()->size_of_parameters());
  set_bytecode_1(Bytecodes::_invokeinterface);
}


void ConstantPoolCacheEntry::set_method_handle(methodHandle adapter, Handle appendix) {
  assert(!is_secondary_entry(), "");
  set_method_handle_common(Bytecodes::_invokehandle, adapter, appendix);
}

void ConstantPoolCacheEntry::set_dynamic_call(methodHandle adapter, Handle appendix) {
  assert(is_secondary_entry(), "");
  set_method_handle_common(Bytecodes::_invokedynamic, adapter, appendix);
}

void ConstantPoolCacheEntry::set_method_handle_common(Bytecodes::Code invoke_code, methodHandle adapter, Handle appendix) {
  // NOTE: This CPCE can be the subject of data races.
  // There are three words to update: flags, f2, f1 (in that order).
  // Writers must store all other values before f1.
  // Readers must test f1 first for non-null before reading other fields.
  // Competing writers must acquire exclusive access on the first
  // write, to flags, using a compare/exchange.
  // A losing writer must spin until the winner writes f1,
  // so that when he returns, he can use the linked cache entry.

  bool has_appendix = appendix.not_null();
  if (!has_appendix) {
    // The extra argument is not used, but we need a non-null value to signify linkage state.
    // Set it to something benign that will never leak memory.
    appendix = Universe::void_mirror();
  }

  bool owner =
    init_method_flags_atomic(as_TosState(adapter->result_type()),
                   ((has_appendix ?  1 : 0) << has_appendix_shift) |
                   (                 1      << is_vfinal_shift)    |
                   (                 1      << is_final_shift),
                   adapter->size_of_parameters());
  if (!owner) {
    while (is_f1_null()) {
      // Pause momentarily on a low-level lock, to allow racing thread to win.
      MutexLockerEx mu(Patching_lock, Mutex::_no_safepoint_check_flag);
      os::yield();
    }
    return;
  }

  if (TraceInvokeDynamic) {
    tty->print_cr("set_method_handle bc=%d appendix="PTR_FORMAT"%s method="PTR_FORMAT" ",
                  invoke_code,
                  (intptr_t)appendix(), (has_appendix ? "" : " (unused)"),
                  (intptr_t)adapter());
    adapter->print();
    if (has_appendix)  appendix()->print();
  }

  // Method handle invokes and invokedynamic sites use both cp cache words.
  // f1, if not null, contains a value passed as a trailing argument to the adapter.
  // In the general case, this could be the call site's MethodType,
  // for use with java.lang.Invokers.checkExactType, or else a CallSite object.
  // f2 contains the adapter method which manages the actual call.
  // In the general case, this is a compiled LambdaForm.
  // (The Java code is free to optimize these calls by binding other
  // sorts of methods and appendices to call sites.)
  // JVM-level linking is via f2, as if for invokevfinal, and signatures are erased.
  // The appendix argument (if any) is added to the signature, and is counted in the parameter_size bits.
  // In principle this means that the method (with appendix) could take up to 256 parameter slots.
  //
  // This means that given a call site like (List)mh.invoke("foo"),
  // the f2 method has signature '(Ljl/Object;Ljl/invoke/MethodType;)Ljl/Object;',
  // not '(Ljava/lang/String;)Ljava/util/List;'.
  // The fact that String and List are involved is encoded in the MethodType in f1.
  // This allows us to create fewer method oops, while keeping type safety.
  //
  set_f2_as_vfinal_method(adapter());
  assert(appendix.not_null(), "needed for linkage state");
  release_set_f1(appendix());  // This must be the last one to set (see NOTE above)!
  if (!is_secondary_entry()) {
    // The interpreter assembly code does not check byte_2,
    // but it is used by is_resolved, method_if_resolved, etc.
    set_bytecode_2(invoke_code);
  }
  NOT_PRODUCT(verify(tty));
  if (TraceInvokeDynamic) {
    this->print(tty, 0);
  }
}

methodOop ConstantPoolCacheEntry::method_if_resolved(constantPoolHandle cpool) {
  if (is_secondary_entry()) {
    if (!is_f1_null())
      return f2_as_vfinal_method();
    return NULL;
  }
  // Decode the action of set_method and set_interface_call
  Bytecodes::Code invoke_code = bytecode_1();
  if (invoke_code != (Bytecodes::Code)0) {
    oop f1 = _f1;
    if (f1 != NULL) {
      switch (invoke_code) {
      case Bytecodes::_invokeinterface:
        assert(f1->is_klass(), "");
        return klassItable::method_for_itable_index(klassOop(f1), f2_as_index());
      case Bytecodes::_invokestatic:
      case Bytecodes::_invokespecial:
        assert(!has_appendix(), "");
        assert(f1->is_method(), "");
        return methodOop(f1);
      }
    }
  }
  invoke_code = bytecode_2();
  if (invoke_code != (Bytecodes::Code)0) {
    switch (invoke_code) {
    case Bytecodes::_invokevirtual:
      if (is_vfinal()) {
        // invokevirtual
        methodOop m = f2_as_vfinal_method();
        assert(m->is_method(), "");
        return m;
      } else {
        int holder_index = cpool->uncached_klass_ref_index_at(constant_pool_index());
        if (cpool->tag_at(holder_index).is_klass()) {
          klassOop klass = cpool->resolved_klass_at(holder_index);
          if (!Klass::cast(klass)->oop_is_instance())
            klass = SystemDictionary::Object_klass();
          return instanceKlass::cast(klass)->method_at_vtable(f2_as_index());
        }
      }
      break;
    case Bytecodes::_invokehandle:
    case Bytecodes::_invokedynamic:
      return f2_as_vfinal_method();
    }
  }
  return NULL;
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
#endif // SERIALGC

// RedefineClasses() API support:
// If this constantPoolCacheEntry refers to old_method then update it
// to refer to new_method.
bool ConstantPoolCacheEntry::adjust_method_entry(methodOop old_method,
       methodOop new_method, bool * trace_name_printed) {

  if (is_vfinal()) {
    // virtual and final so _f2 contains method ptr instead of vtable index
    if (f2_as_vfinal_method() == old_method) {
      // match old_method so need an update
      // NOTE: can't use set_f2_as_vfinal_method as it asserts on different values
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
    m = f2_as_vfinal_method();
  } else if (is_f1_null()) {
    // NULL _f1 means this is a virtual entry so also not interesting
    return false;
  } else {
    oop f1 = _f1;  // _f1 is volatile
    if (!f1->is_method()) {
      // _f1 can also contain a klassOop for an interface
      return false;
    }
    m = f1_as_method();
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
  if (index == 0) st->print_cr("                 -------------");
  // print entry
  st->print("%3d  ("PTR_FORMAT")  ", index, (intptr_t)this);
  if (is_secondary_entry())
    st->print_cr("[%5d|secondary]", main_entry_index());
  else
    st->print_cr("[%02x|%02x|%5d]", bytecode_2(), bytecode_1(), constant_pool_index());
  st->print_cr("                 [   "PTR_FORMAT"]", (intptr_t)(oop)_f1);
  st->print_cr("                 [   "PTR_FORMAT"]", (intptr_t)_f2);
  st->print_cr("                 [   "PTR_FORMAT"]", (intptr_t)_flags);
  st->print_cr("                 -------------");
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
