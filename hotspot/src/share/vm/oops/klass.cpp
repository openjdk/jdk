/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_klass.cpp.incl"


bool Klass::is_subclass_of(klassOop k) const {
  // Run up the super chain and check
  klassOop t = as_klassOop();

  if (t == k) return true;
  t = Klass::cast(t)->super();

  while (t != NULL) {
    if (t == k) return true;
    t = Klass::cast(t)->super();
  }
  return false;
}

bool Klass::search_secondary_supers(klassOop k) const {
  // Put some extra logic here out-of-line, before the search proper.
  // This cuts down the size of the inline method.

  // This is necessary, since I am never in my own secondary_super list.
  if (this->as_klassOop() == k)
    return true;
  // Scan the array-of-objects for a match
  int cnt = secondary_supers()->length();
  for (int i = 0; i < cnt; i++) {
    if (secondary_supers()->obj_at(i) == k) {
      ((Klass*)this)->set_secondary_super_cache(k);
      return true;
    }
  }
  return false;
}

// Return self, except for abstract classes with exactly 1
// implementor.  Then return the 1 concrete implementation.
Klass *Klass::up_cast_abstract() {
  Klass *r = this;
  while( r->is_abstract() ) {   // Receiver is abstract?
    Klass *s = r->subklass();   // Check for exactly 1 subklass
    if( !s || s->next_sibling() ) // Oops; wrong count; give up
      return this;              // Return 'this' as a no-progress flag
    r = s;                    // Loop till find concrete class
  }
  return r;                   // Return the 1 concrete class
}

// Find LCA in class hierarchy
Klass *Klass::LCA( Klass *k2 ) {
  Klass *k1 = this;
  while( 1 ) {
    if( k1->is_subtype_of(k2->as_klassOop()) ) return k2;
    if( k2->is_subtype_of(k1->as_klassOop()) ) return k1;
    k1 = k1->super()->klass_part();
    k2 = k2->super()->klass_part();
  }
}


void Klass::check_valid_for_instantiation(bool throwError, TRAPS) {
  ResourceMark rm(THREAD);
  THROW_MSG(throwError ? vmSymbols::java_lang_InstantiationError()
            : vmSymbols::java_lang_InstantiationException(), external_name());
}


void Klass::copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS) {
  THROW(vmSymbols::java_lang_ArrayStoreException());
}


void Klass::initialize(TRAPS) {
  ShouldNotReachHere();
}

bool Klass::compute_is_subtype_of(klassOop k) {
  assert(k->is_klass(), "argument must be a class");
  return is_subclass_of(k);
}


methodOop Klass::uncached_lookup_method(symbolOop name, symbolOop signature) const {
#ifdef ASSERT
  tty->print_cr("Error: uncached_lookup_method called on a klass oop."
                " Likely error: reflection method does not correctly"
                " wrap return value in a mirror object.");
#endif
  ShouldNotReachHere();
  return NULL;
}

klassOop Klass::base_create_klass_oop(KlassHandle& klass, int size,
                                      const Klass_vtbl& vtbl, TRAPS) {
  size = align_object_size(size);
  // allocate and initialize vtable
  Klass*   kl = (Klass*) vtbl.allocate_permanent(klass, size, CHECK_NULL);
  klassOop k  = kl->as_klassOop();

  { // Preinitialize supertype information.
    // A later call to initialize_supers() may update these settings:
    kl->set_super(NULL);
    for (juint i = 0; i < Klass::primary_super_limit(); i++) {
      kl->_primary_supers[i] = NULL;
    }
    kl->set_secondary_supers(NULL);
    oop_store_without_check((oop*) &kl->_primary_supers[0], k);
    kl->set_super_check_offset(primary_supers_offset_in_bytes() + sizeof(oopDesc));
  }

  kl->set_java_mirror(NULL);
  kl->set_modifier_flags(0);
  kl->set_layout_helper(Klass::_lh_neutral_value);
  kl->set_name(NULL);
  AccessFlags af;
  af.set_flags(0);
  kl->set_access_flags(af);
  kl->set_subklass(NULL);
  kl->set_next_sibling(NULL);
  kl->set_alloc_count(0);
  kl->set_alloc_size(0);

  kl->set_prototype_header(markOopDesc::prototype());
  kl->set_biased_lock_revocation_count(0);
  kl->set_last_biased_lock_bulk_revocation_time(0);

  return k;
}

KlassHandle Klass::base_create_klass(KlassHandle& klass, int size,
                                     const Klass_vtbl& vtbl, TRAPS) {
  klassOop ek = base_create_klass_oop(klass, size, vtbl, THREAD);
  return KlassHandle(THREAD, ek);
}

void Klass_vtbl::post_new_init_klass(KlassHandle& klass,
                                     klassOop new_klass,
                                     int size) const {
  assert(!new_klass->klass_part()->null_vtbl(), "Not a complete klass");
  CollectedHeap::post_allocation_install_obj_klass(klass, new_klass, size);
}

void* Klass_vtbl::operator new(size_t ignored, KlassHandle& klass,
                               int size, TRAPS) {
  // The vtable pointer is installed during the execution of
  // constructors in the call to permanent_obj_allocate().  Delay
  // the installation of the klass pointer into the new klass "k"
  // until after the vtable pointer has been installed (i.e., until
  // after the return of permanent_obj_allocate().
  klassOop k =
    (klassOop) CollectedHeap::permanent_obj_allocate_no_klass_install(klass,
      size, CHECK_NULL);
  return k->klass_part();
}

jint Klass::array_layout_helper(BasicType etype) {
  assert(etype >= T_BOOLEAN && etype <= T_OBJECT, "valid etype");
  // Note that T_ARRAY is not allowed here.
  int  hsize = arrayOopDesc::base_offset_in_bytes(etype);
  int  esize = type2aelembytes(etype);
  bool isobj = (etype == T_OBJECT);
  int  tag   =  isobj ? _lh_array_tag_obj_value : _lh_array_tag_type_value;
  int lh = array_layout_helper(tag, hsize, etype, exact_log2(esize));

  assert(lh < (int)_lh_neutral_value, "must look like an array layout");
  assert(layout_helper_is_javaArray(lh), "correct kind");
  assert(layout_helper_is_objArray(lh) == isobj, "correct kind");
  assert(layout_helper_is_typeArray(lh) == !isobj, "correct kind");
  assert(layout_helper_header_size(lh) == hsize, "correct decode");
  assert(layout_helper_element_type(lh) == etype, "correct decode");
  assert(1 << layout_helper_log2_element_size(lh) == esize, "correct decode");

  return lh;
}

bool Klass::can_be_primary_super_slow() const {
  if (super() == NULL)
    return true;
  else if (super()->klass_part()->super_depth() >= primary_super_limit()-1)
    return false;
  else
    return true;
}

void Klass::initialize_supers(klassOop k, TRAPS) {
  if (FastSuperclassLimit == 0) {
    // None of the other machinery matters.
    set_super(k);
    return;
  }
  if (k == NULL) {
    set_super(NULL);
    oop_store_without_check((oop*) &_primary_supers[0], (oop) this->as_klassOop());
    assert(super_depth() == 0, "Object must already be initialized properly");
  } else if (k != super() || k == SystemDictionary::Object_klass()) {
    assert(super() == NULL || super() == SystemDictionary::Object_klass(),
           "initialize this only once to a non-trivial value");
    set_super(k);
    Klass* sup = k->klass_part();
    int sup_depth = sup->super_depth();
    juint my_depth  = MIN2(sup_depth + 1, (int)primary_super_limit());
    if (!can_be_primary_super_slow())
      my_depth = primary_super_limit();
    for (juint i = 0; i < my_depth; i++) {
      oop_store_without_check((oop*) &_primary_supers[i], (oop) sup->_primary_supers[i]);
    }
    klassOop *super_check_cell;
    if (my_depth < primary_super_limit()) {
      oop_store_without_check((oop*) &_primary_supers[my_depth], (oop) this->as_klassOop());
      super_check_cell = &_primary_supers[my_depth];
    } else {
      // Overflow of the primary_supers array forces me to be secondary.
      super_check_cell = &_secondary_super_cache;
    }
    set_super_check_offset((address)super_check_cell - (address) this->as_klassOop());

#ifdef ASSERT
    {
      juint j = super_depth();
      assert(j == my_depth, "computed accessor gets right answer");
      klassOop t = as_klassOop();
      while (!Klass::cast(t)->can_be_primary_super()) {
        t = Klass::cast(t)->super();
        j = Klass::cast(t)->super_depth();
      }
      for (juint j1 = j+1; j1 < primary_super_limit(); j1++) {
        assert(primary_super_of_depth(j1) == NULL, "super list padding");
      }
      while (t != NULL) {
        assert(primary_super_of_depth(j) == t, "super list initialization");
        t = Klass::cast(t)->super();
        --j;
      }
      assert(j == (juint)-1, "correct depth count");
    }
#endif
  }

  if (secondary_supers() == NULL) {
    KlassHandle this_kh (THREAD, this);

    // Now compute the list of secondary supertypes.
    // Secondaries can occasionally be on the super chain,
    // if the inline "_primary_supers" array overflows.
    int extras = 0;
    klassOop p;
    for (p = super(); !(p == NULL || p->klass_part()->can_be_primary_super()); p = p->klass_part()->super()) {
      ++extras;
    }

    // Compute the "real" non-extra secondaries.
    objArrayOop secondary_oops = compute_secondary_supers(extras, CHECK);
    objArrayHandle secondaries (THREAD, secondary_oops);

    // Store the extra secondaries in the first array positions:
    int fillp = extras;
    for (p = this_kh->super(); !(p == NULL || p->klass_part()->can_be_primary_super()); p = p->klass_part()->super()) {
      int i;                    // Scan for overflow primaries being duplicates of 2nd'arys

      // This happens frequently for very deeply nested arrays: the
      // primary superclass chain overflows into the secondary.  The
      // secondary list contains the element_klass's secondaries with
      // an extra array dimension added.  If the element_klass's
      // secondary list already contains some primary overflows, they
      // (with the extra level of array-ness) will collide with the
      // normal primary superclass overflows.
      for( i = extras; i < secondaries->length(); i++ )
        if( secondaries->obj_at(i) == p )
          break;
      if( i < secondaries->length() )
        continue;               // It's a dup, don't put it in
      secondaries->obj_at_put(--fillp, p);
    }
    // See if we had some dup's, so the array has holes in it.
    if( fillp > 0 ) {
      // Pack the array.  Drop the old secondaries array on the floor
      // and let GC reclaim it.
      objArrayOop s2 = oopFactory::new_system_objArray(secondaries->length() - fillp, CHECK);
      for( int i = 0; i < s2->length(); i++ )
        s2->obj_at_put( i, secondaries->obj_at(i+fillp) );
      secondaries = objArrayHandle(THREAD, s2);
    }

  #ifdef ASSERT
    if (secondaries() != Universe::the_array_interfaces_array()) {
      // We must not copy any NULL placeholders left over from bootstrap.
      for (int j = 0; j < secondaries->length(); j++) {
        assert(secondaries->obj_at(j) != NULL, "correct bootstrapping order");
      }
    }
  #endif

    this_kh->set_secondary_supers(secondaries());
  }
}

objArrayOop Klass::compute_secondary_supers(int num_extra_slots, TRAPS) {
  assert(num_extra_slots == 0, "override for complex klasses");
  return Universe::the_empty_system_obj_array();
}


Klass* Klass::subklass() const {
  return _subklass == NULL ? NULL : Klass::cast(_subklass);
}

instanceKlass* Klass::superklass() const {
  assert(super() == NULL || super()->klass_part()->oop_is_instance(), "must be instance klass");
  return _super == NULL ? NULL : instanceKlass::cast(_super);
}

Klass* Klass::next_sibling() const {
  return _next_sibling == NULL ? NULL : Klass::cast(_next_sibling);
}

void Klass::set_subklass(klassOop s) {
  assert(s != as_klassOop(), "sanity check");
  oop_store_without_check((oop*)&_subklass, s);
}

void Klass::set_next_sibling(klassOop s) {
  assert(s != as_klassOop(), "sanity check");
  oop_store_without_check((oop*)&_next_sibling, s);
}

void Klass::append_to_sibling_list() {
  debug_only(if (!SharedSkipVerify) as_klassOop()->verify();)
  // add ourselves to superklass' subklass list
  instanceKlass* super = superklass();
  if (super == NULL) return;        // special case: class Object
  assert(SharedSkipVerify ||
         (!super->is_interface()    // interfaces cannot be supers
          && (super->superklass() == NULL || !is_interface())),
         "an interface can only be a subklass of Object");
  klassOop prev_first_subklass = super->subklass_oop();
  if (prev_first_subklass != NULL) {
    // set our sibling to be the superklass' previous first subklass
    set_next_sibling(prev_first_subklass);
  }
  // make ourselves the superklass' first subklass
  super->set_subklass(as_klassOop());
  debug_only(if (!SharedSkipVerify) as_klassOop()->verify();)
}

void Klass::remove_from_sibling_list() {
  // remove receiver from sibling list
  instanceKlass* super = superklass();
  assert(super != NULL || as_klassOop() == SystemDictionary::Object_klass(), "should have super");
  if (super == NULL) return;        // special case: class Object
  if (super->subklass() == this) {
    // first subklass
    super->set_subklass(_next_sibling);
  } else {
    Klass* sib = super->subklass();
    while (sib->next_sibling() != this) {
      sib = sib->next_sibling();
    };
    sib->set_next_sibling(_next_sibling);
  }
}

void Klass::follow_weak_klass_links( BoolObjectClosure* is_alive, OopClosure* keep_alive) {
  // This klass is alive but the subklass and siblings are not followed/updated.
  // We update the subklass link and the subklass' sibling links here.
  // Our own sibling link will be updated by our superclass (which must be alive
  // since we are).
  assert(is_alive->do_object_b(as_klassOop()), "just checking, this should be live");
  if (ClassUnloading) {
    klassOop sub = subklass_oop();
    if (sub != NULL && !is_alive->do_object_b(sub)) {
      // first subklass not alive, find first one alive
      do {
#ifndef PRODUCT
        if (TraceClassUnloading && WizardMode) {
          ResourceMark rm;
          tty->print_cr("[Unlinking class (subclass) %s]", sub->klass_part()->external_name());
        }
#endif
        sub = sub->klass_part()->next_sibling_oop();
      } while (sub != NULL && !is_alive->do_object_b(sub));
      set_subklass(sub);
    }
    // now update the subklass' sibling list
    while (sub != NULL) {
      klassOop next = sub->klass_part()->next_sibling_oop();
      if (next != NULL && !is_alive->do_object_b(next)) {
        // first sibling not alive, find first one alive
        do {
#ifndef PRODUCT
          if (TraceClassUnloading && WizardMode) {
            ResourceMark rm;
            tty->print_cr("[Unlinking class (sibling) %s]", next->klass_part()->external_name());
          }
#endif
          next = next->klass_part()->next_sibling_oop();
        } while (next != NULL && !is_alive->do_object_b(next));
        sub->klass_part()->set_next_sibling(next);
      }
      sub = next;
    }
  } else {
    // Always follow subklass and sibling link. This will prevent any klasses from
    // being unloaded (all classes are transitively linked from java.lang.Object).
    keep_alive->do_oop(adr_subklass());
    keep_alive->do_oop(adr_next_sibling());
  }
}


void Klass::remove_unshareable_info() {
  if (oop_is_instance()) {
    instanceKlass* ik = (instanceKlass*)this;
    if (ik->is_linked()) {
      ik->unlink_class();
    }
  }
  set_subklass(NULL);
  set_next_sibling(NULL);
}


klassOop Klass::array_klass_or_null(int rank) {
  EXCEPTION_MARK;
  // No exception can be thrown by array_klass_impl when called with or_null == true.
  // (In anycase, the execption mark will fail if it do so)
  return array_klass_impl(true, rank, THREAD);
}


klassOop Klass::array_klass_or_null() {
  EXCEPTION_MARK;
  // No exception can be thrown by array_klass_impl when called with or_null == true.
  // (In anycase, the execption mark will fail if it do so)
  return array_klass_impl(true, THREAD);
}


klassOop Klass::array_klass_impl(bool or_null, int rank, TRAPS) {
  fatal("array_klass should be dispatched to instanceKlass, objArrayKlass or typeArrayKlass");
  return NULL;
}


klassOop Klass::array_klass_impl(bool or_null, TRAPS) {
  fatal("array_klass should be dispatched to instanceKlass, objArrayKlass or typeArrayKlass");
  return NULL;
}


void Klass::with_array_klasses_do(void f(klassOop k)) {
  f(as_klassOop());
}


const char* Klass::external_name() const {
  if (oop_is_instance()) {
    instanceKlass* ik = (instanceKlass*) this;
    if (ik->is_anonymous()) {
      assert(AnonymousClasses, "");
      intptr_t hash = ik->java_mirror()->identity_hash();
      char     hash_buf[40];
      sprintf(hash_buf, "/" UINTX_FORMAT, (uintx)hash);
      size_t   hash_len = strlen(hash_buf);

      size_t result_len = name()->utf8_length();
      char*  result     = NEW_RESOURCE_ARRAY(char, result_len + hash_len + 1);
      name()->as_klass_external_name(result, (int) result_len + 1);
      assert(strlen(result) == result_len, "");
      strcpy(result + result_len, hash_buf);
      assert(strlen(result) == result_len + hash_len, "");
      return result;
    }
  }
  if (name() == NULL)  return "<unknown>";
  return name()->as_klass_external_name();
}


const char* Klass::signature_name() const {
  if (name() == NULL)  return "<unknown>";
  return name()->as_C_string();
}

// Unless overridden, modifier_flags is 0.
jint Klass::compute_modifier_flags(TRAPS) const {
  return 0;
}

int Klass::atomic_incr_biased_lock_revocation_count() {
  return (int) Atomic::add(1, &_biased_lock_revocation_count);
}

// Unless overridden, jvmti_class_status has no flags set.
jint Klass::jvmti_class_status() const {
  return 0;
}

// Printing

void Klass::oop_print_on(oop obj, outputStream* st) {
  ResourceMark rm;
  // print title
  st->print_cr("%s ", internal_name());
  obj->print_address_on(st);

  if (WizardMode) {
     // print header
     obj->mark()->print_on(st);
  }

  // print class
  st->print(" - klass: ");
  obj->klass()->print_value_on(st);
  st->cr();
}

void Klass::oop_print_value_on(oop obj, outputStream* st) {
  // print title
  ResourceMark rm;              // Cannot print in debug mode without this
  st->print("%s", internal_name());
  obj->print_address_on(st);
}

// Verification

void Klass::oop_verify_on(oop obj, outputStream* st) {
  guarantee(obj->is_oop(),  "should be oop");
  guarantee(obj->klass()->is_perm(),  "should be in permspace");
  guarantee(obj->klass()->is_klass(), "klass field is not a klass");
}


void Klass::oop_verify_old_oop(oop obj, oop* p, bool allow_dirty) {
  /* $$$ I think this functionality should be handled by verification of
  RememberedSet::verify_old_oop(obj, p, allow_dirty, false);
  the card table. */
}
void Klass::oop_verify_old_oop(oop obj, narrowOop* p, bool allow_dirty) { }

#ifndef PRODUCT

void Klass::verify_vtable_index(int i) {
  assert(oop_is_instance() || oop_is_array(), "only instanceKlass and arrayKlass have vtables");
  if (oop_is_instance()) {
    assert(i>=0 && i<((instanceKlass*)this)->vtable_length()/vtableEntry::size(), "index out of bounds");
  } else {
    assert(i>=0 && i<((arrayKlass*)this)->vtable_length()/vtableEntry::size(), "index out of bounds");
  }
}

#endif
