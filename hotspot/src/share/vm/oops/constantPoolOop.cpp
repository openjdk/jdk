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
# include "incls/_constantPoolOop.cpp.incl"

void constantPoolOopDesc::set_flag_at(FlagBit fb) {
  const int MAX_STATE_CHANGES = 2;
  for (int i = MAX_STATE_CHANGES + 10; i > 0; i--) {
    int oflags = _flags;
    int nflags = oflags | (1 << (int)fb);
    if (Atomic::cmpxchg(nflags, &_flags, oflags) == oflags)
      return;
  }
  assert(false, "failed to cmpxchg flags");
  _flags |= (1 << (int)fb);     // better than nothing
}

klassOop constantPoolOopDesc::klass_at_impl(constantPoolHandle this_oop, int which, TRAPS) {
  // A resolved constantPool entry will contain a klassOop, otherwise a symbolOop.
  // It is not safe to rely on the tag bit's here, since we don't have a lock, and the entry and
  // tag is not updated atomicly.
  oop entry = *(this_oop->obj_at_addr(which));
  if (entry->is_klass()) {
    // Already resolved - return entry.
    return (klassOop)entry;
  }

  // Acquire lock on constant oop while doing update. After we get the lock, we check if another object
  // already has updated the object
  assert(THREAD->is_Java_thread(), "must be a Java thread");
  bool do_resolve = false;
  bool in_error = false;

  symbolHandle name;
  Handle       loader;
  { ObjectLocker ol(this_oop, THREAD);

    if (this_oop->tag_at(which).is_unresolved_klass()) {
      if (this_oop->tag_at(which).is_unresolved_klass_in_error()) {
        in_error = true;
      } else {
        do_resolve = true;
        name   = symbolHandle(THREAD, this_oop->unresolved_klass_at(which));
        loader = Handle(THREAD, instanceKlass::cast(this_oop->pool_holder())->class_loader());
      }
    }
  } // unlocking constantPool


  // The original attempt to resolve this constant pool entry failed so find the
  // original error and throw it again (JVMS 5.4.3).
  if (in_error) {
    symbolOop error = SystemDictionary::find_resolution_error(this_oop, which);
    guarantee(error != (symbolOop)NULL, "tag mismatch with resolution error table");
    ResourceMark rm;
    // exception text will be the class name
    const char* className = this_oop->unresolved_klass_at(which)->as_C_string();
    THROW_MSG_0(error, className);
  }

  if (do_resolve) {
    // this_oop must be unlocked during resolve_or_fail
    oop protection_domain = Klass::cast(this_oop->pool_holder())->protection_domain();
    Handle h_prot (THREAD, protection_domain);
    klassOop k_oop = SystemDictionary::resolve_or_fail(name, loader, h_prot, true, THREAD);
    KlassHandle k;
    if (!HAS_PENDING_EXCEPTION) {
      k = KlassHandle(THREAD, k_oop);
      // Do access check for klasses
      verify_constant_pool_resolve(this_oop, k, THREAD);
    }

    // Failed to resolve class. We must record the errors so that subsequent attempts
    // to resolve this constant pool entry fail with the same error (JVMS 5.4.3).
    if (HAS_PENDING_EXCEPTION) {
      ResourceMark rm;
      symbolHandle error(PENDING_EXCEPTION->klass()->klass_part()->name());

      bool throw_orig_error = false;
      {
        ObjectLocker ol (this_oop, THREAD);

        // some other thread has beaten us and has resolved the class.
        if (this_oop->tag_at(which).is_klass()) {
          CLEAR_PENDING_EXCEPTION;
          entry = this_oop->resolved_klass_at(which);
          return (klassOop)entry;
        }

        if (!PENDING_EXCEPTION->
              is_a(SystemDictionary::linkageError_klass())) {
          // Just throw the exception and don't prevent these classes from
          // being loaded due to virtual machine errors like StackOverflow
          // and OutOfMemoryError, etc, or if the thread was hit by stop()
          // Needs clarification to section 5.4.3 of the VM spec (see 6308271)
        }
        else if (!this_oop->tag_at(which).is_unresolved_klass_in_error()) {
          SystemDictionary::add_resolution_error(this_oop, which, error);
          this_oop->tag_at_put(which, JVM_CONSTANT_UnresolvedClassInError);
        } else {
          // some other thread has put the class in error state.
          error = symbolHandle(SystemDictionary::find_resolution_error(this_oop, which));
          assert(!error.is_null(), "checking");
          throw_orig_error = true;
        }
      } // unlocked

      if (throw_orig_error) {
        CLEAR_PENDING_EXCEPTION;
        ResourceMark rm;
        const char* className = this_oop->unresolved_klass_at(which)->as_C_string();
        THROW_MSG_0(error, className);
      }

      return 0;
    }

    if (TraceClassResolution && !k()->klass_part()->oop_is_array()) {
      // skip resolving the constant pool so that this code get's
      // called the next time some bytecodes refer to this class.
      ResourceMark rm;
      int line_number = -1;
      const char * source_file = NULL;
      if (JavaThread::current()->has_last_Java_frame()) {
        // try to identify the method which called this function.
        vframeStream vfst(JavaThread::current());
        if (!vfst.at_end()) {
          line_number = vfst.method()->line_number_from_bci(vfst.bci());
          symbolOop s = instanceKlass::cast(vfst.method()->method_holder())->source_file_name();
          if (s != NULL) {
            source_file = s->as_C_string();
          }
        }
      }
      if (k() != this_oop->pool_holder()) {
        // only print something if the classes are different
        if (source_file != NULL) {
          tty->print("RESOLVE %s %s %s:%d\n",
                     instanceKlass::cast(this_oop->pool_holder())->external_name(),
                     instanceKlass::cast(k())->external_name(), source_file, line_number);
        } else {
          tty->print("RESOLVE %s %s\n",
                     instanceKlass::cast(this_oop->pool_holder())->external_name(),
                     instanceKlass::cast(k())->external_name());
        }
      }
      return k();
    } else {
      ObjectLocker ol (this_oop, THREAD);
      // Only updated constant pool - if it is resolved.
      do_resolve = this_oop->tag_at(which).is_unresolved_klass();
      if (do_resolve) {
        this_oop->klass_at_put(which, k());
      }
    }
  }

  entry = this_oop->resolved_klass_at(which);
  assert(entry->is_klass(), "must be resolved at this point");
  return (klassOop)entry;
}


// Does not update constantPoolOop - to avoid any exception throwing. Used
// by compiler and exception handling.  Also used to avoid classloads for
// instanceof operations. Returns NULL if the class has not been loaded or
// if the verification of constant pool failed
klassOop constantPoolOopDesc::klass_at_if_loaded(constantPoolHandle this_oop, int which) {
  oop entry = *this_oop->obj_at_addr(which);
  if (entry->is_klass()) {
    return (klassOop)entry;
  } else {
    assert(entry->is_symbol(), "must be either symbol or klass");
    Thread *thread = Thread::current();
    symbolHandle name (thread, (symbolOop)entry);
    oop loader = instanceKlass::cast(this_oop->pool_holder())->class_loader();
    oop protection_domain = Klass::cast(this_oop->pool_holder())->protection_domain();
    Handle h_prot (thread, protection_domain);
    Handle h_loader (thread, loader);
    klassOop k = SystemDictionary::find(name, h_loader, h_prot, thread);

    if (k != NULL) {
      // Make sure that resolving is legal
      EXCEPTION_MARK;
      KlassHandle klass(THREAD, k);
      // return NULL if verification fails
      verify_constant_pool_resolve(this_oop, klass, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        return NULL;
      }
      return klass();
    } else {
      return k;
    }
  }
}


klassOop constantPoolOopDesc::klass_ref_at_if_loaded(constantPoolHandle this_oop, int which) {
  return klass_at_if_loaded(this_oop, this_oop->klass_ref_index_at(which));
}


// This is an interface for the compiler that allows accessing non-resolved entries
// in the constant pool - but still performs the validations tests. Must be used
// in a pre-parse of the compiler - to determine what it can do and not do.
// Note: We cannot update the ConstantPool from the vm_thread.
klassOop constantPoolOopDesc::klass_ref_at_if_loaded_check(constantPoolHandle this_oop, int index, TRAPS) {
  int which = this_oop->klass_ref_index_at(index);
  oop entry = *this_oop->obj_at_addr(which);
  if (entry->is_klass()) {
    return (klassOop)entry;
  } else {
    assert(entry->is_symbol(), "must be either symbol or klass");
    symbolHandle name (THREAD, (symbolOop)entry);
    oop loader = instanceKlass::cast(this_oop->pool_holder())->class_loader();
    oop protection_domain = Klass::cast(this_oop->pool_holder())->protection_domain();
    Handle h_loader(THREAD, loader);
    Handle h_prot  (THREAD, protection_domain);
    KlassHandle k(THREAD, SystemDictionary::find(name, h_loader, h_prot, THREAD));

    // Do access check for klasses
    if( k.not_null() ) verify_constant_pool_resolve(this_oop, k, CHECK_NULL);
    return k();
  }
}


symbolOop constantPoolOopDesc::uncached_name_ref_at(int which) {
  jint ref_index = name_and_type_at(uncached_name_and_type_ref_index_at(which));
  int name_index = extract_low_short_from_int(ref_index);
  return symbol_at(name_index);
}


symbolOop constantPoolOopDesc::uncached_signature_ref_at(int which) {
  jint ref_index = name_and_type_at(uncached_name_and_type_ref_index_at(which));
  int signature_index = extract_high_short_from_int(ref_index);
  return symbol_at(signature_index);
}


int constantPoolOopDesc::uncached_name_and_type_ref_index_at(int which) {
  jint ref_index = field_or_method_at(which, true);
  return extract_high_short_from_int(ref_index);
}


int constantPoolOopDesc::uncached_klass_ref_index_at(int which) {
  jint ref_index = field_or_method_at(which, true);
  return extract_low_short_from_int(ref_index);
}


void constantPoolOopDesc::verify_constant_pool_resolve(constantPoolHandle this_oop, KlassHandle k, TRAPS) {
 if (k->oop_is_instance() || k->oop_is_objArray()) {
    instanceKlassHandle holder (THREAD, this_oop->pool_holder());
    klassOop elem_oop = k->oop_is_instance() ? k() : objArrayKlass::cast(k())->bottom_klass();
    KlassHandle element (THREAD, elem_oop);

    // The element type could be a typeArray - we only need the access check if it is
    // an reference to another class
    if (element->oop_is_instance()) {
      LinkResolver::check_klass_accessability(holder, element, CHECK);
    }
  }
}


int constantPoolOopDesc::klass_ref_index_at(int which) {
  jint ref_index = field_or_method_at(which, false);
  return extract_low_short_from_int(ref_index);
}


int constantPoolOopDesc::name_and_type_ref_index_at(int which) {
  jint ref_index = field_or_method_at(which, false);
  return extract_high_short_from_int(ref_index);
}


int constantPoolOopDesc::name_ref_index_at(int which) {
  jint ref_index = name_and_type_at(which);
  return extract_low_short_from_int(ref_index);
}


int constantPoolOopDesc::signature_ref_index_at(int which) {
  jint ref_index = name_and_type_at(which);
  return extract_high_short_from_int(ref_index);
}


klassOop constantPoolOopDesc::klass_ref_at(int which, TRAPS) {
  return klass_at(klass_ref_index_at(which), CHECK_NULL);
}


symbolOop constantPoolOopDesc::klass_name_at(int which) {
  assert(tag_at(which).is_unresolved_klass() || tag_at(which).is_klass(),
         "Corrupted constant pool");
  // A resolved constantPool entry will contain a klassOop, otherwise a symbolOop.
  // It is not safe to rely on the tag bit's here, since we don't have a lock, and the entry and
  // tag is not updated atomicly.
  oop entry = *(obj_at_addr(which));
  if (entry->is_klass()) {
    // Already resolved - return entry's name.
    return klassOop(entry)->klass_part()->name();
  } else {
    assert(entry->is_symbol(), "must be either symbol or klass");
    return (symbolOop)entry;
  }
}

symbolOop constantPoolOopDesc::klass_ref_at_noresolve(int which) {
  jint ref_index = klass_ref_index_at(which);
  return klass_at_noresolve(ref_index);
}

char* constantPoolOopDesc::string_at_noresolve(int which) {
  // Test entry type in case string is resolved while in here.
  oop entry = *(obj_at_addr(which));
  if (entry->is_symbol()) {
    return ((symbolOop)entry)->as_C_string();
  } else if (java_lang_String::is_instance(entry)) {
    return java_lang_String::as_utf8_string(entry);
  } else {
    return (char*)"<pseudo-string>";
  }
}


symbolOop constantPoolOopDesc::name_ref_at(int which) {
  jint ref_index = name_and_type_at(name_and_type_ref_index_at(which));
  int name_index = extract_low_short_from_int(ref_index);
  return symbol_at(name_index);
}


symbolOop constantPoolOopDesc::signature_ref_at(int which) {
  jint ref_index = name_and_type_at(name_and_type_ref_index_at(which));
  int signature_index = extract_high_short_from_int(ref_index);
  return symbol_at(signature_index);
}


BasicType constantPoolOopDesc::basic_type_for_signature_at(int which) {
  return FieldType::basic_type(symbol_at(which));
}


void constantPoolOopDesc::resolve_string_constants_impl(constantPoolHandle this_oop, TRAPS) {
  for (int index = 1; index < this_oop->length(); index++) { // Index 0 is unused
    if (this_oop->tag_at(index).is_unresolved_string()) {
      this_oop->string_at(index, CHECK);
    }
  }
}

oop constantPoolOopDesc::string_at_impl(constantPoolHandle this_oop, int which, TRAPS) {
  oop entry = *(this_oop->obj_at_addr(which));
  if (entry->is_symbol()) {
    ObjectLocker ol(this_oop, THREAD);
    if (this_oop->tag_at(which).is_unresolved_string()) {
      // Intern string
      symbolOop sym = this_oop->unresolved_string_at(which);
      entry = StringTable::intern(sym, CHECK_(constantPoolOop(NULL)));
      this_oop->string_at_put(which, entry);
    } else {
      // Another thread beat us and interned string, read string from constant pool
      entry = this_oop->resolved_string_at(which);
    }
  }
  assert(java_lang_String::is_instance(entry), "must be string");
  return entry;
}


bool constantPoolOopDesc::is_pseudo_string_at(int which) {
  oop entry = *(obj_at_addr(which));
  if (entry->is_symbol())
    // Not yet resolved, but it will resolve to a string.
    return false;
  else if (java_lang_String::is_instance(entry))
    return false; // actually, it might be a non-interned or non-perm string
  else
    // truly pseudo
    return true;
}


bool constantPoolOopDesc::klass_name_at_matches(instanceKlassHandle k,
                                                int which) {
  // Names are interned, so we can compare symbolOops directly
  symbolOop cp_name = klass_name_at(which);
  return (cp_name == k->name());
}


int constantPoolOopDesc::pre_resolve_shared_klasses(TRAPS) {
  ResourceMark rm;
  int count = 0;
  for (int index = 1; index < tags()->length(); index++) { // Index 0 is unused
    if (tag_at(index).is_unresolved_string()) {
      // Intern string
      symbolOop sym = unresolved_string_at(index);
      oop entry = StringTable::intern(sym, CHECK_(-1));
      string_at_put(index, entry);
    }
  }
  return count;
}


// Iterate over symbols which are used as class, field, method names and
// signatures (in preparation for writing to the shared archive).

void constantPoolOopDesc::shared_symbols_iterate(OopClosure* closure) {
  for (int index = 1; index < length(); index++) { // Index 0 is unused
    switch (tag_at(index).value()) {

    case JVM_CONSTANT_UnresolvedClass:
      closure->do_oop(obj_at_addr(index));
      break;

    case JVM_CONSTANT_NameAndType:
      {
        int i = *int_at_addr(index);
        closure->do_oop(obj_at_addr((unsigned)i >> 16));
        closure->do_oop(obj_at_addr((unsigned)i & 0xffff));
      }
      break;

    case JVM_CONSTANT_Class:
    case JVM_CONSTANT_InterfaceMethodref:
    case JVM_CONSTANT_Fieldref:
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_Integer:
    case JVM_CONSTANT_Float:
      // Do nothing!  Not an oop.
      // These constant types do not reference symbols at this point.
      break;

    case JVM_CONSTANT_String:
      // Do nothing!  Not a symbol.
      break;

    case JVM_CONSTANT_UnresolvedString:
    case JVM_CONSTANT_Utf8:
      // These constants are symbols, but unless these symbols are
      // actually to be used for something, we don't want to mark them.
      break;

    case JVM_CONSTANT_Long:
    case JVM_CONSTANT_Double:
      // Do nothing!  Not an oop. (But takes two pool entries.)
      ++index;
      break;

    default:
      ShouldNotReachHere();
      break;
    }
  }
}


// Iterate over the [one] tags array (in preparation for writing to the
// shared archive).

void constantPoolOopDesc::shared_tags_iterate(OopClosure* closure) {
  closure->do_oop(tags_addr());
}


// Iterate over String objects (in preparation for writing to the shared
// archive).

void constantPoolOopDesc::shared_strings_iterate(OopClosure* closure) {
  for (int index = 1; index < length(); index++) { // Index 0 is unused
    switch (tag_at(index).value()) {

    case JVM_CONSTANT_UnresolvedClass:
    case JVM_CONSTANT_NameAndType:
      // Do nothing!  Not a String.
      break;

    case JVM_CONSTANT_Class:
    case JVM_CONSTANT_InterfaceMethodref:
    case JVM_CONSTANT_Fieldref:
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_Integer:
    case JVM_CONSTANT_Float:
      // Do nothing!  Not an oop.
      // These constant types do not reference symbols at this point.
      break;

    case JVM_CONSTANT_String:
      closure->do_oop(obj_at_addr(index));
      break;

    case JVM_CONSTANT_UnresolvedString:
    case JVM_CONSTANT_Utf8:
      // These constants are symbols, but unless these symbols are
      // actually to be used for something, we don't want to mark them.
      break;

    case JVM_CONSTANT_Long:
    case JVM_CONSTANT_Double:
      // Do nothing!  Not an oop. (But takes two pool entries.)
      ++index;
      break;

    default:
      ShouldNotReachHere();
      break;
    }
  }
}


// Compare this constant pool's entry at index1 to the constant pool
// cp2's entry at index2.
bool constantPoolOopDesc::compare_entry_to(int index1, constantPoolHandle cp2,
       int index2, TRAPS) {

  jbyte t1 = tag_at(index1).value();
  jbyte t2 = cp2->tag_at(index2).value();


  // JVM_CONSTANT_UnresolvedClassInError is equal to JVM_CONSTANT_UnresolvedClass
  // when comparing
  if (t1 == JVM_CONSTANT_UnresolvedClassInError) {
    t1 = JVM_CONSTANT_UnresolvedClass;
  }
  if (t2 == JVM_CONSTANT_UnresolvedClassInError) {
    t2 = JVM_CONSTANT_UnresolvedClass;
  }

  if (t1 != t2) {
    // Not the same entry type so there is nothing else to check. Note
    // that this style of checking will consider resolved/unresolved
    // class pairs and resolved/unresolved string pairs as different.
    // From the constantPoolOop API point of view, this is correct
    // behavior. See constantPoolKlass::merge() to see how this plays
    // out in the context of constantPoolOop merging.
    return false;
  }

  switch (t1) {
  case JVM_CONSTANT_Class:
  {
    klassOop k1 = klass_at(index1, CHECK_false);
    klassOop k2 = cp2->klass_at(index2, CHECK_false);
    if (k1 == k2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_ClassIndex:
  {
    int recur1 = klass_index_at(index1);
    int recur2 = cp2->klass_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Double:
  {
    jdouble d1 = double_at(index1);
    jdouble d2 = cp2->double_at(index2);
    if (d1 == d2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Fieldref:
  case JVM_CONSTANT_InterfaceMethodref:
  case JVM_CONSTANT_Methodref:
  {
    int recur1 = uncached_klass_ref_index_at(index1);
    int recur2 = cp2->uncached_klass_ref_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      recur1 = uncached_name_and_type_ref_index_at(index1);
      recur2 = cp2->uncached_name_and_type_ref_index_at(index2);
      match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
      if (match) {
        return true;
      }
    }
  } break;

  case JVM_CONSTANT_Float:
  {
    jfloat f1 = float_at(index1);
    jfloat f2 = cp2->float_at(index2);
    if (f1 == f2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Integer:
  {
    jint i1 = int_at(index1);
    jint i2 = cp2->int_at(index2);
    if (i1 == i2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Long:
  {
    jlong l1 = long_at(index1);
    jlong l2 = cp2->long_at(index2);
    if (l1 == l2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_NameAndType:
  {
    int recur1 = name_ref_index_at(index1);
    int recur2 = cp2->name_ref_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      recur1 = signature_ref_index_at(index1);
      recur2 = cp2->signature_ref_index_at(index2);
      match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
      if (match) {
        return true;
      }
    }
  } break;

  case JVM_CONSTANT_String:
  {
    oop s1 = string_at(index1, CHECK_false);
    oop s2 = cp2->string_at(index2, CHECK_false);
    if (s1 == s2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_StringIndex:
  {
    int recur1 = string_index_at(index1);
    int recur2 = cp2->string_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      return true;
    }
  } break;

  case JVM_CONSTANT_UnresolvedClass:
  {
    symbolOop k1 = unresolved_klass_at(index1);
    symbolOop k2 = cp2->unresolved_klass_at(index2);
    if (k1 == k2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_UnresolvedString:
  {
    symbolOop s1 = unresolved_string_at(index1);
    symbolOop s2 = cp2->unresolved_string_at(index2);
    if (s1 == s2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Utf8:
  {
    symbolOop s1 = symbol_at(index1);
    symbolOop s2 = cp2->symbol_at(index2);
    if (s1 == s2) {
      return true;
    }
  } break;

  // Invalid is used as the tag for the second constant pool entry
  // occupied by JVM_CONSTANT_Double or JVM_CONSTANT_Long. It should
  // not be seen by itself.
  case JVM_CONSTANT_Invalid: // fall through

  default:
    ShouldNotReachHere();
    break;
  }

  return false;
} // end compare_entry_to()


// Copy this constant pool's entries at start_i to end_i (inclusive)
// to the constant pool to_cp's entries starting at to_i. A total of
// (end_i - start_i) + 1 entries are copied.
void constantPoolOopDesc::copy_cp_to(int start_i, int end_i,
       constantPoolHandle to_cp, int to_i, TRAPS) {

  int dest_i = to_i;  // leave original alone for debug purposes

  for (int src_i = start_i; src_i <= end_i; /* see loop bottom */ ) {
    copy_entry_to(src_i, to_cp, dest_i, CHECK);

    switch (tag_at(src_i).value()) {
    case JVM_CONSTANT_Double:
    case JVM_CONSTANT_Long:
      // double and long take two constant pool entries
      src_i += 2;
      dest_i += 2;
      break;

    default:
      // all others take one constant pool entry
      src_i++;
      dest_i++;
      break;
    }
  }
} // end copy_cp_to()


// Copy this constant pool's entry at from_i to the constant pool
// to_cp's entry at to_i.
void constantPoolOopDesc::copy_entry_to(int from_i, constantPoolHandle to_cp,
       int to_i, TRAPS) {

  switch (tag_at(from_i).value()) {
  case JVM_CONSTANT_Class:
  {
    klassOop k = klass_at(from_i, CHECK);
    to_cp->klass_at_put(to_i, k);
  } break;

  case JVM_CONSTANT_ClassIndex:
  {
    jint ki = klass_index_at(from_i);
    to_cp->klass_index_at_put(to_i, ki);
  } break;

  case JVM_CONSTANT_Double:
  {
    jdouble d = double_at(from_i);
    to_cp->double_at_put(to_i, d);
    // double takes two constant pool entries so init second entry's tag
    to_cp->tag_at_put(to_i + 1, JVM_CONSTANT_Invalid);
  } break;

  case JVM_CONSTANT_Fieldref:
  {
    int class_index = uncached_klass_ref_index_at(from_i);
    int name_and_type_index = uncached_name_and_type_ref_index_at(from_i);
    to_cp->field_at_put(to_i, class_index, name_and_type_index);
  } break;

  case JVM_CONSTANT_Float:
  {
    jfloat f = float_at(from_i);
    to_cp->float_at_put(to_i, f);
  } break;

  case JVM_CONSTANT_Integer:
  {
    jint i = int_at(from_i);
    to_cp->int_at_put(to_i, i);
  } break;

  case JVM_CONSTANT_InterfaceMethodref:
  {
    int class_index = uncached_klass_ref_index_at(from_i);
    int name_and_type_index = uncached_name_and_type_ref_index_at(from_i);
    to_cp->interface_method_at_put(to_i, class_index, name_and_type_index);
  } break;

  case JVM_CONSTANT_Long:
  {
    jlong l = long_at(from_i);
    to_cp->long_at_put(to_i, l);
    // long takes two constant pool entries so init second entry's tag
    to_cp->tag_at_put(to_i + 1, JVM_CONSTANT_Invalid);
  } break;

  case JVM_CONSTANT_Methodref:
  {
    int class_index = uncached_klass_ref_index_at(from_i);
    int name_and_type_index = uncached_name_and_type_ref_index_at(from_i);
    to_cp->method_at_put(to_i, class_index, name_and_type_index);
  } break;

  case JVM_CONSTANT_NameAndType:
  {
    int name_ref_index = name_ref_index_at(from_i);
    int signature_ref_index = signature_ref_index_at(from_i);
    to_cp->name_and_type_at_put(to_i, name_ref_index, signature_ref_index);
  } break;

  case JVM_CONSTANT_String:
  {
    oop s = string_at(from_i, CHECK);
    to_cp->string_at_put(to_i, s);
  } break;

  case JVM_CONSTANT_StringIndex:
  {
    jint si = string_index_at(from_i);
    to_cp->string_index_at_put(to_i, si);
  } break;

  case JVM_CONSTANT_UnresolvedClass:
  {
    symbolOop k = unresolved_klass_at(from_i);
    to_cp->unresolved_klass_at_put(to_i, k);
  } break;

  case JVM_CONSTANT_UnresolvedClassInError:
  {
    symbolOop k = unresolved_klass_at(from_i);
    to_cp->unresolved_klass_at_put(to_i, k);
    to_cp->tag_at_put(to_i, JVM_CONSTANT_UnresolvedClassInError);
  } break;


  case JVM_CONSTANT_UnresolvedString:
  {
    symbolOop s = unresolved_string_at(from_i);
    to_cp->unresolved_string_at_put(to_i, s);
  } break;

  case JVM_CONSTANT_Utf8:
  {
    symbolOop s = symbol_at(from_i);
    to_cp->symbol_at_put(to_i, s);
  } break;

  // Invalid is used as the tag for the second constant pool entry
  // occupied by JVM_CONSTANT_Double or JVM_CONSTANT_Long. It should
  // not be seen by itself.
  case JVM_CONSTANT_Invalid: // fall through

  default:
  {
    jbyte bad_value = tag_at(from_i).value(); // leave a breadcrumb
    ShouldNotReachHere();
  } break;
  }
} // end copy_entry_to()


// Search constant pool search_cp for an entry that matches this
// constant pool's entry at pattern_i. Returns the index of a
// matching entry or zero (0) if there is no matching entry.
int constantPoolOopDesc::find_matching_entry(int pattern_i,
      constantPoolHandle search_cp, TRAPS) {

  // index zero (0) is not used
  for (int i = 1; i < search_cp->length(); i++) {
    bool found = compare_entry_to(pattern_i, search_cp, i, CHECK_0);
    if (found) {
      return i;
    }
  }

  return 0;  // entry not found; return unused index zero (0)
} // end find_matching_entry()


#ifndef PRODUCT

const char* constantPoolOopDesc::printable_name_at(int which) {

  constantTag tag = tag_at(which);

  if (tag.is_unresolved_string() || tag.is_string()) {
    return string_at_noresolve(which);
  } else if (tag.is_klass() || tag.is_unresolved_klass()) {
    return klass_name_at(which)->as_C_string();
  } else if (tag.is_symbol()) {
    return symbol_at(which)->as_C_string();
  }
  return "";
}

#endif // PRODUCT


// JVMTI GetConstantPool support

// For temporary use until code is stable.
#define DBG(code)

static const char* WARN_MSG = "Must not be such entry!";

static void print_cpool_bytes(jint cnt, u1 *bytes) {
  jint size = 0;
  u2   idx1, idx2;

  for (jint idx = 1; idx < cnt; idx++) {
    jint ent_size = 0;
    u1   tag  = *bytes++;
    size++;                       // count tag

    printf("const #%03d, tag: %02d ", idx, tag);
    switch(tag) {
      case JVM_CONSTANT_Invalid: {
        printf("Invalid");
        break;
      }
      case JVM_CONSTANT_Unicode: {
        printf("Unicode      %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_Utf8: {
        u2 len = Bytes::get_Java_u2(bytes);
        char str[128];
        if (len > 127) {
           len = 127;
        }
        strncpy(str, (char *) (bytes+2), len);
        str[len] = '\0';
        printf("Utf8          \"%s\"", str);
        ent_size = 2 + len;
        break;
      }
      case JVM_CONSTANT_Integer: {
        u4 val = Bytes::get_Java_u4(bytes);
        printf("int          %d", *(int *) &val);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Float: {
        u4 val = Bytes::get_Java_u4(bytes);
        printf("float        %5.3ff", *(float *) &val);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Long: {
        u8 val = Bytes::get_Java_u8(bytes);
        printf("long         "INT64_FORMAT, *(jlong *) &val);
        ent_size = 8;
        idx++; // Long takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Double: {
        u8 val = Bytes::get_Java_u8(bytes);
        printf("double       %5.3fd", *(jdouble *)&val);
        ent_size = 8;
        idx++; // Double takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Class: {
        idx1 = Bytes::get_Java_u2(bytes);
        printf("class        #%03d", idx1);
        ent_size = 2;
        break;
      }
      case JVM_CONSTANT_String: {
        idx1 = Bytes::get_Java_u2(bytes);
        printf("String       #%03d", idx1);
        ent_size = 2;
        break;
      }
      case JVM_CONSTANT_Fieldref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("Field        #%03d, #%03d", (int) idx1, (int) idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Methodref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("Method       #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_InterfaceMethodref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("InterfMethod #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("NameAndType  #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_ClassIndex: {
        printf("ClassIndex  %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_UnresolvedClass: {
        printf("UnresolvedClass: %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_UnresolvedClassInError: {
        printf("UnresolvedClassInErr: %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_StringIndex: {
        printf("StringIndex: %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_UnresolvedString: {
        printf("UnresolvedString: %s", WARN_MSG);
        break;
      }
    }
    printf(";\n");
    bytes += ent_size;
    size  += ent_size;
  }
  printf("Cpool size: %d\n", size);
  fflush(0);
  return;
} /* end print_cpool_bytes */


// Returns size of constant pool entry.
jint constantPoolOopDesc::cpool_entry_size(jint idx) {
  switch(tag_at(idx).value()) {
    case JVM_CONSTANT_Invalid:
    case JVM_CONSTANT_Unicode:
      return 1;

    case JVM_CONSTANT_Utf8:
      return 3 + symbol_at(idx)->utf8_length();

    case JVM_CONSTANT_Class:
    case JVM_CONSTANT_String:
    case JVM_CONSTANT_ClassIndex:
    case JVM_CONSTANT_UnresolvedClass:
    case JVM_CONSTANT_UnresolvedClassInError:
    case JVM_CONSTANT_StringIndex:
    case JVM_CONSTANT_UnresolvedString:
      return 3;

    case JVM_CONSTANT_Integer:
    case JVM_CONSTANT_Float:
    case JVM_CONSTANT_Fieldref:
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_InterfaceMethodref:
    case JVM_CONSTANT_NameAndType:
      return 5;

    case JVM_CONSTANT_Long:
    case JVM_CONSTANT_Double:
      return 9;
  }
  assert(false, "cpool_entry_size: Invalid constant pool entry tag");
  return 1;
} /* end cpool_entry_size */


// SymbolHashMap is used to find a constant pool index from a string.
// This function fills in SymbolHashMaps, one for utf8s and one for
// class names, returns size of the cpool raw bytes.
jint constantPoolOopDesc::hash_entries_to(SymbolHashMap *symmap,
                                          SymbolHashMap *classmap) {
  jint size = 0;

  for (u2 idx = 1; idx < length(); idx++) {
    u2 tag = tag_at(idx).value();
    size += cpool_entry_size(idx);

    switch(tag) {
      case JVM_CONSTANT_Utf8: {
        symbolOop sym = symbol_at(idx);
        symmap->add_entry(sym, idx);
        DBG(printf("adding symbol entry %s = %d\n", sym->as_utf8(), idx));
        break;
      }
      case JVM_CONSTANT_Class:
      case JVM_CONSTANT_UnresolvedClass:
      case JVM_CONSTANT_UnresolvedClassInError: {
        symbolOop sym = klass_name_at(idx);
        classmap->add_entry(sym, idx);
        DBG(printf("adding class entry %s = %d\n", sym->as_utf8(), idx));
        break;
      }
      case JVM_CONSTANT_Long:
      case JVM_CONSTANT_Double: {
        idx++; // Both Long and Double take two cpool slots
        break;
      }
    }
  }
  return size;
} /* end hash_utf8_entries_to */


// Copy cpool bytes.
// Returns:
//    0, in case of OutOfMemoryError
//   -1, in case of internal error
//  > 0, count of the raw cpool bytes that have been copied
int constantPoolOopDesc::copy_cpool_bytes(int cpool_size,
                                          SymbolHashMap* tbl,
                                          unsigned char *bytes) {
  u2   idx1, idx2;
  jint size  = 0;
  jint cnt   = length();
  unsigned char *start_bytes = bytes;

  for (jint idx = 1; idx < cnt; idx++) {
    u1   tag      = tag_at(idx).value();
    jint ent_size = cpool_entry_size(idx);

    assert(size + ent_size <= cpool_size, "Size mismatch");

    *bytes = tag;
    DBG(printf("#%03hd tag=%03hd, ", idx, tag));
    switch(tag) {
      case JVM_CONSTANT_Invalid: {
        DBG(printf("JVM_CONSTANT_Invalid"));
        break;
      }
      case JVM_CONSTANT_Unicode: {
        assert(false, "Wrong constant pool tag: JVM_CONSTANT_Unicode");
        DBG(printf("JVM_CONSTANT_Unicode"));
        break;
      }
      case JVM_CONSTANT_Utf8: {
        symbolOop sym = symbol_at(idx);
        char*     str = sym->as_utf8();
        // Warning! It's crashing on x86 with len = sym->utf8_length()
        int       len = (int) strlen(str);
        Bytes::put_Java_u2((address) (bytes+1), (u2) len);
        for (int i = 0; i < len; i++) {
            bytes[3+i] = (u1) str[i];
        }
        DBG(printf("JVM_CONSTANT_Utf8: %s ", str));
        break;
      }
      case JVM_CONSTANT_Integer: {
        jint val = int_at(idx);
        Bytes::put_Java_u4((address) (bytes+1), *(u4*)&val);
        break;
      }
      case JVM_CONSTANT_Float: {
        jfloat val = float_at(idx);
        Bytes::put_Java_u4((address) (bytes+1), *(u4*)&val);
        break;
      }
      case JVM_CONSTANT_Long: {
        jlong val = long_at(idx);
        Bytes::put_Java_u8((address) (bytes+1), *(u8*)&val);
        idx++;             // Long takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Double: {
        jdouble val = double_at(idx);
        Bytes::put_Java_u8((address) (bytes+1), *(u8*)&val);
        idx++;             // Double takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Class:
      case JVM_CONSTANT_UnresolvedClass:
      case JVM_CONSTANT_UnresolvedClassInError: {
        *bytes = JVM_CONSTANT_Class;
        symbolOop sym = klass_name_at(idx);
        idx1 = tbl->symbol_to_value(sym);
        assert(idx1 != 0, "Have not found a hashtable entry");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_Class: idx=#%03hd, %s", idx1, sym->as_utf8()));
        break;
      }
      case JVM_CONSTANT_String: {
        unsigned int hash;
        char *str = string_at_noresolve(idx);
        symbolOop sym = SymbolTable::lookup_only(str, (int) strlen(str), hash);
        idx1 = tbl->symbol_to_value(sym);
        assert(idx1 != 0, "Have not found a hashtable entry");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_String: idx=#%03hd, %s", idx1, str));
        break;
      }
      case JVM_CONSTANT_UnresolvedString: {
        *bytes = JVM_CONSTANT_String;
        symbolOop sym = unresolved_string_at(idx);
        idx1 = tbl->symbol_to_value(sym);
        assert(idx1 != 0, "Have not found a hashtable entry");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(char *str = sym->as_utf8());
        DBG(printf("JVM_CONSTANT_UnresolvedString: idx=#%03hd, %s", idx1, str));
        break;
      }
      case JVM_CONSTANT_Fieldref:
      case JVM_CONSTANT_Methodref:
      case JVM_CONSTANT_InterfaceMethodref: {
        idx1 = uncached_klass_ref_index_at(idx);
        idx2 = uncached_name_and_type_ref_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_Methodref: %hd %hd", idx1, idx2));
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        idx1 = name_ref_index_at(idx);
        idx2 = signature_ref_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_NameAndType: %hd %hd", idx1, idx2));
        break;
      }
      case JVM_CONSTANT_ClassIndex: {
        *bytes = JVM_CONSTANT_Class;
        idx1 = klass_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_ClassIndex: %hd", idx1));
        break;
      }
      case JVM_CONSTANT_StringIndex: {
        *bytes = JVM_CONSTANT_String;
        idx1 = string_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_StringIndex: %hd", idx1));
        break;
      }
    }
    DBG(printf("\n"));
    bytes += ent_size;
    size  += ent_size;
  }
  assert(size == cpool_size, "Size mismatch");

  // Keep temorarily for debugging until it's stable.
  DBG(print_cpool_bytes(cnt, start_bytes));
  return (int)(bytes - start_bytes);
} /* end copy_cpool_bytes */


void SymbolHashMap::add_entry(symbolOop sym, u2 value) {
  char *str = sym->as_utf8();
  unsigned int hash = compute_hash(str, sym->utf8_length());
  unsigned int index = hash % table_size();

  // check if already in map
  // we prefer the first entry since it is more likely to be what was used in
  // the class file
  for (SymbolHashMapEntry *en = bucket(index); en != NULL; en = en->next()) {
    assert(en->symbol() != NULL, "SymbolHashMapEntry symbol is NULL");
    if (en->hash() == hash && en->symbol() == sym) {
        return;  // already there
    }
  }

  SymbolHashMapEntry* entry = new SymbolHashMapEntry(hash, sym, value);
  entry->set_next(bucket(index));
  _buckets[index].set_entry(entry);
  assert(entry->symbol() != NULL, "SymbolHashMapEntry symbol is NULL");
}

SymbolHashMapEntry* SymbolHashMap::find_entry(symbolOop sym) {
  assert(sym != NULL, "SymbolHashMap::find_entry - symbol is NULL");
  char *str = sym->as_utf8();
  int   len = sym->utf8_length();
  unsigned int hash = SymbolHashMap::compute_hash(str, len);
  unsigned int index = hash % table_size();
  for (SymbolHashMapEntry *en = bucket(index); en != NULL; en = en->next()) {
    assert(en->symbol() != NULL, "SymbolHashMapEntry symbol is NULL");
    if (en->hash() == hash && en->symbol() == sym) {
      return en;
    }
  }
  return NULL;
}
