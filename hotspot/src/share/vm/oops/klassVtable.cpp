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

#include "incls/_precompiled.incl"
#include "incls/_klassVtable.cpp.incl"

inline instanceKlass* klassVtable::ik() const {
  Klass* k = _klass()->klass_part();
  assert(k->oop_is_instance(), "not an instanceKlass");
  return (instanceKlass*)k;
}


// this function computes the vtable size (including the size needed for miranda
// methods) and the number of miranda methods in this class
// Note on Miranda methods: Let's say there is a class C that implements
// interface I.  Let's say there is a method m in I that neither C nor any
// of its super classes implement (i.e there is no method of any access, with
// the same name and signature as m), then m is a Miranda method which is
// entered as a public abstract method in C's vtable.  From then on it should
// treated as any other public method in C for method over-ride purposes.
void klassVtable::compute_vtable_size_and_num_mirandas(int &vtable_length,
                                                       int &num_miranda_methods,
                                                       klassOop super,
                                                       objArrayOop methods,
                                                       AccessFlags class_flags,
                                                       Handle classloader,
                                                       symbolHandle classname,
                                                       objArrayOop local_interfaces,
                                                       TRAPS
                                                       ) {

  No_Safepoint_Verifier nsv;

  // set up default result values
  vtable_length = 0;
  num_miranda_methods = 0;

  // start off with super's vtable length
  instanceKlass* sk = (instanceKlass*)super->klass_part();
  vtable_length = super == NULL ? 0 : sk->vtable_length();

  // go thru each method in the methods table to see if it needs a new entry
  int len = methods->length();
  for (int i = 0; i < len; i++) {
    assert(methods->obj_at(i)->is_method(), "must be a methodOop");
    methodHandle mh(THREAD, methodOop(methods->obj_at(i)));

    if (needs_new_vtable_entry(mh, super, classloader, classname, class_flags, THREAD)) {
      vtable_length += vtableEntry::size(); // we need a new entry
    }
  }

  // compute the number of mirandas methods that must be added to the end
  num_miranda_methods = get_num_mirandas(super, methods, local_interfaces);
  vtable_length += (num_miranda_methods * vtableEntry::size());

  if (Universe::is_bootstrapping() && vtable_length == 0) {
    // array classes don't have their superclass set correctly during
    // bootstrapping
    vtable_length = Universe::base_vtable_size();
  }

  if (super == NULL && !Universe::is_bootstrapping() &&
      vtable_length != Universe::base_vtable_size()) {
    // Someone is attempting to redefine java.lang.Object incorrectly.  The
    // only way this should happen is from
    // SystemDictionary::resolve_from_stream(), which will detect this later
    // and throw a security exception.  So don't assert here to let
    // the exception occur.
    vtable_length = Universe::base_vtable_size();
  }
  assert(super != NULL || vtable_length == Universe::base_vtable_size(),
         "bad vtable size for class Object");
  assert(vtable_length % vtableEntry::size() == 0, "bad vtable length");
  assert(vtable_length >= Universe::base_vtable_size(), "vtable too small");
}

int klassVtable::index_of(methodOop m, int len) const {
  assert(m->vtable_index() >= 0, "do not ask this of non-vtable methods");
  return m->vtable_index();
}

int klassVtable::initialize_from_super(KlassHandle super) {
  if (super.is_null()) {
    return 0;
  } else {
    // copy methods from superKlass
    // can't inherit from array class, so must be instanceKlass
    assert(super->oop_is_instance(), "must be instance klass");
    instanceKlass* sk = (instanceKlass*)super()->klass_part();
    klassVtable* superVtable = sk->vtable();
    assert(superVtable->length() <= _length, "vtable too short");
#ifdef ASSERT
    superVtable->verify(tty, true);
#endif
    superVtable->copy_vtable_to(table());
#ifndef PRODUCT
    if (PrintVtables && Verbose) {
      ResourceMark rm;
      tty->print_cr("copy vtable from %s to %s size %d", sk->internal_name(), klass()->internal_name(), _length);
    }
#endif
    return superVtable->length();
  }
}

// Revised lookup semantics   introduced 1.3 (Kestral beta)
void klassVtable::initialize_vtable(bool checkconstraints, TRAPS) {

  // Note:  Arrays can have intermediate array supers.  Use java_super to skip them.
  KlassHandle super (THREAD, klass()->java_super());
  int nofNewEntries = 0;


  if (PrintVtables && !klass()->oop_is_array()) {
    ResourceMark rm(THREAD);
    tty->print_cr("Initializing: %s", _klass->name()->as_C_string());
  }

#ifdef ASSERT
  oop* end_of_obj = (oop*)_klass() + _klass()->size();
  oop* end_of_vtable = (oop*)&table()[_length];
  assert(end_of_vtable <= end_of_obj, "vtable extends beyond end");
#endif

  if (Universe::is_bootstrapping()) {
    // just clear everything
    for (int i = 0; i < _length; i++) table()[i].clear();
    return;
  }

  int super_vtable_len = initialize_from_super(super);
  if (klass()->oop_is_array()) {
    assert(super_vtable_len == _length, "arrays shouldn't introduce new methods");
  } else {
    assert(_klass->oop_is_instance(), "must be instanceKlass");

    objArrayHandle methods(THREAD, ik()->methods());
    int len = methods()->length();
    int initialized = super_vtable_len;

    // update_inherited_vtable can stop for gc - ensure using handles
    for (int i = 0; i < len; i++) {
      HandleMark hm(THREAD);
      assert(methods()->obj_at(i)->is_method(), "must be a methodOop");
      methodHandle mh(THREAD, (methodOop)methods()->obj_at(i));

      bool needs_new_entry = update_inherited_vtable(ik(), mh, super_vtable_len, checkconstraints, CHECK);

      if (needs_new_entry) {
        put_method_at(mh(), initialized);
        mh()->set_vtable_index(initialized); // set primary vtable index
        initialized++;
      }
    }

    // add miranda methods; it will also update the value of initialized
    fill_in_mirandas(initialized);

    // In class hierarchies where the accessibility is not increasing (i.e., going from private ->
    // package_private -> publicprotected), the vtable might actually be smaller than our initial
    // calculation.
    assert(initialized <= _length, "vtable initialization failed");
    for(;initialized < _length; initialized++) {
      put_method_at(NULL, initialized);
    }
    NOT_PRODUCT(verify(tty, true));
  }
}

// Called for cases where a method does not override its superclass' vtable entry
// For bytecodes not produced by javac together it is possible that a method does not override
// the superclass's method, but might indirectly override a super-super class's vtable entry
// If none found, return a null superk, else return the superk of the method this does override
instanceKlass* klassVtable::find_transitive_override(instanceKlass* initialsuper, methodHandle target_method,
                            int vtable_index, Handle target_loader, symbolHandle target_classname, Thread * THREAD) {
  instanceKlass* superk = initialsuper;
  while (superk != NULL && superk->super() != NULL) {
    instanceKlass* supersuperklass = instanceKlass::cast(superk->super());
    klassVtable* ssVtable = supersuperklass->vtable();
    if (vtable_index < ssVtable->length()) {
      methodOop super_method = ssVtable->method_at(vtable_index);
#ifndef PRODUCT
      symbolHandle name(THREAD,target_method()->name());
      symbolHandle signature(THREAD,target_method()->signature());
      assert(super_method->name() == name() && super_method->signature() == signature(), "vtable entry name/sig mismatch");
#endif
      if (supersuperklass->is_override(super_method, target_loader, target_classname, THREAD)) {
#ifndef PRODUCT
        if (PrintVtables && Verbose) {
          ResourceMark rm(THREAD);
          tty->print("transitive overriding superclass %s with %s::%s index %d, original flags: ",
           supersuperklass->internal_name(),
           _klass->internal_name(), (target_method() != NULL) ?
           target_method()->name()->as_C_string() : "<NULL>", vtable_index);
           super_method->access_flags().print_on(tty);
           tty->print("overriders flags: ");
           target_method->access_flags().print_on(tty);
           tty->cr();
        }
#endif /*PRODUCT*/
        break; // return found superk
      }
    } else  {
      // super class has no vtable entry here, stop transitive search
      superk = (instanceKlass*)NULL;
      break;
    }
    // if no override found yet, continue to search up
    superk = instanceKlass::cast(superk->super());
  }

  return superk;
}


// Update child's copy of super vtable for overrides
// OR return true if a new vtable entry is required
// Only called for instanceKlass's, i.e. not for arrays
// If that changed, could not use _klass as handle for klass
bool klassVtable::update_inherited_vtable(instanceKlass* klass, methodHandle target_method, int super_vtable_len,
                  bool checkconstraints, TRAPS) {
  ResourceMark rm;
  bool allocate_new = true;
  assert(klass->oop_is_instance(), "must be instanceKlass");

  // Initialize the method's vtable index to "nonvirtual".
  // If we allocate a vtable entry, we will update it to a non-negative number.
  target_method()->set_vtable_index(methodOopDesc::nonvirtual_vtable_index);

  // Static and <init> methods are never in
  if (target_method()->is_static() || target_method()->name() ==  vmSymbols::object_initializer_name()) {
    return false;
  }

  if (klass->is_final() || target_method()->is_final()) {
    // a final method never needs a new entry; final methods can be statically
    // resolved and they have to be present in the vtable only if they override
    // a super's method, in which case they re-use its entry
    allocate_new = false;
  }

  // we need a new entry if there is no superclass
  if (klass->super() == NULL) {
    return allocate_new;
  }

  // private methods always have a new entry in the vtable
  // specification interpretation since classic has
  // private methods not overriding
  if (target_method()->is_private()) {
    return allocate_new;
  }

  // search through the vtable and update overridden entries
  // Since check_signature_loaders acquires SystemDictionary_lock
  // which can block for gc, once we are in this loop, use handles
  // For classfiles built with >= jdk7, we now look for transitive overrides

  symbolHandle name(THREAD,target_method()->name());
  symbolHandle signature(THREAD,target_method()->signature());
  Handle target_loader(THREAD, _klass->class_loader());
  symbolHandle target_classname(THREAD, _klass->name());
  for(int i = 0; i < super_vtable_len; i++) {
    methodOop super_method = method_at(i);
    // Check if method name matches
    if (super_method->name() == name() && super_method->signature() == signature()) {

      // get super_klass for method_holder for the found method
      instanceKlass* super_klass =  instanceKlass::cast(super_method->method_holder());

      if ((super_klass->is_override(super_method, target_loader, target_classname, THREAD)) ||
      ((klass->major_version() >= VTABLE_TRANSITIVE_OVERRIDE_VERSION)
        && ((super_klass = find_transitive_override(super_klass, target_method, i, target_loader,
             target_classname, THREAD)) != (instanceKlass*)NULL))) {
        // overriding, so no new entry
        allocate_new = false;

        if (checkconstraints) {
        // Override vtable entry if passes loader constraint check
        // if loader constraint checking requested
        // No need to visit his super, since he and his super
        // have already made any needed loader constraints.
        // Since loader constraints are transitive, it is enough
        // to link to the first super, and we get all the others.
          Handle super_loader(THREAD, super_klass->class_loader());

          if (target_loader() != super_loader()) {
            ResourceMark rm(THREAD);
            char* failed_type_name =
              SystemDictionary::check_signature_loaders(signature, target_loader,
                                                        super_loader, true,
                                                        CHECK_(false));
            if (failed_type_name != NULL) {
              const char* msg = "loader constraint violation: when resolving "
                "overridden method \"%s\" the class loader (instance"
                " of %s) of the current class, %s, and its superclass loader "
                "(instance of %s), have different Class objects for the type "
                "%s used in the signature";
              char* sig = target_method()->name_and_sig_as_C_string();
              const char* loader1 = SystemDictionary::loader_name(target_loader());
              char* current = _klass->name()->as_C_string();
              const char* loader2 = SystemDictionary::loader_name(super_loader());
              size_t buflen = strlen(msg) + strlen(sig) + strlen(loader1) +
                strlen(current) + strlen(loader2) + strlen(failed_type_name);
              char* buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, buflen);
              jio_snprintf(buf, buflen, msg, sig, loader1, current, loader2,
                           failed_type_name);
              THROW_MSG_(vmSymbols::java_lang_LinkageError(), buf, false);
            }
          }
       }

        put_method_at(target_method(), i);
        target_method()->set_vtable_index(i);
#ifndef PRODUCT
        if (PrintVtables && Verbose) {
          tty->print("overriding with %s::%s index %d, original flags: ",
           _klass->internal_name(), (target_method() != NULL) ?
           target_method()->name()->as_C_string() : "<NULL>", i);
           super_method->access_flags().print_on(tty);
           tty->print("overriders flags: ");
           target_method->access_flags().print_on(tty);
           tty->cr();
        }
#endif /*PRODUCT*/
      } else {
        // allocate_new = true; default. We might override one entry,
        // but not override another. Once we override one, not need new
#ifndef PRODUCT
        if (PrintVtables && Verbose) {
          tty->print("NOT overriding with %s::%s index %d, original flags: ",
           _klass->internal_name(), (target_method() != NULL) ?
           target_method()->name()->as_C_string() : "<NULL>", i);
           super_method->access_flags().print_on(tty);
           tty->print("overriders flags: ");
           target_method->access_flags().print_on(tty);
           tty->cr();
        }
#endif /*PRODUCT*/
      }
    }
  }
  return allocate_new;
}

void klassVtable::put_method_at(methodOop m, int index) {
  assert(m->is_oop_or_null(), "Not an oop or null");
#ifndef PRODUCT
  if (PrintVtables && Verbose) {
    ResourceMark rm;
    tty->print_cr("adding %s::%s at index %d", _klass->internal_name(),
      (m != NULL) ? m->name()->as_C_string() : "<NULL>", index);
  }
  assert(unchecked_method_at(index)->is_oop_or_null(), "Not an oop or null");
#endif
  table()[index].set(m);
}

// Find out if a method "m" with superclass "super", loader "classloader" and
// name "classname" needs a new vtable entry.  Let P be a class package defined
// by "classloader" and "classname".
// NOTE: The logic used here is very similar to the one used for computing
// the vtables indices for a method. We cannot directly use that function because,
// we allocate the instanceKlass at load time, and that requires that the
// superclass has been loaded.
// However, the vtable entries are filled in at link time, and therefore
// the superclass' vtable may not yet have been filled in.
bool klassVtable::needs_new_vtable_entry(methodHandle target_method,
                                         klassOop super,
                                         Handle classloader,
                                         symbolHandle classname,
                                         AccessFlags class_flags,
                                         TRAPS) {
  if ((class_flags.is_final() || target_method()->is_final()) ||
      // a final method never needs a new entry; final methods can be statically
      // resolved and they have to be present in the vtable only if they override
      // a super's method, in which case they re-use its entry
      (target_method()->is_static()) ||
      // static methods don't need to be in vtable
      (target_method()->name() ==  vmSymbols::object_initializer_name())
      // <init> is never called dynamically-bound
      ) {
    return false;
  }

  // we need a new entry if there is no superclass
  if (super == NULL) {
    return true;
  }

  // private methods always have a new entry in the vtable
  // specification interpretation since classic has
  // private methods not overriding
  if (target_method()->is_private()) {
    return true;
  }

  // search through the super class hierarchy to see if we need
  // a new entry
  ResourceMark rm;
  symbolOop name = target_method()->name();
  symbolOop signature = target_method()->signature();
  klassOop k = super;
  methodOop super_method = NULL;
  instanceKlass *holder = NULL;
  methodOop recheck_method =  NULL;
  while (k != NULL) {
    // lookup through the hierarchy for a method with matching name and sign.
    super_method = instanceKlass::cast(k)->lookup_method(name, signature);
    if (super_method == NULL) {
      break; // we still have to search for a matching miranda method
    }
    // get the class holding the matching method
    // make sure you use that class for is_override
    instanceKlass* superk = instanceKlass::cast(super_method->method_holder());
    // we want only instance method matches
    // pretend private methods are not in the super vtable
    // since we do override around them: e.g. a.m pub/b.m private/c.m pub,
    // ignore private, c.m pub does override a.m pub
    // For classes that were not javac'd together, we also do transitive overriding around
    // methods that have less accessibility
    if ((!super_method->is_static()) &&
       (!super_method->is_private())) {
      if (superk->is_override(super_method, classloader, classname, THREAD)) {
        return false;
      // else keep looking for transitive overrides
      }
    }

    // Start with lookup result and continue to search up
    k = superk->super(); // haven't found an override match yet; continue to look
  }

  // if the target method is public or protected it may have a matching
  // miranda method in the super, whose entry it should re-use.
  // Actually, to handle cases that javac would not generate, we need
  // this check for all access permissions.
  instanceKlass *sk = instanceKlass::cast(super);
  if (sk->has_miranda_methods()) {
    if (sk->lookup_method_in_all_interfaces(name, signature) != NULL) {
      return false;  // found a matching miranda; we do not need a new entry
    }
  }
  return true; // found no match; we need a new entry
}

// Support for miranda methods

// get the vtable index of a miranda method with matching "name" and "signature"
int klassVtable::index_of_miranda(symbolOop name, symbolOop signature) {
  // search from the bottom, might be faster
  for (int i = (length() - 1); i >= 0; i--) {
    methodOop m = table()[i].method();
    if (is_miranda_entry_at(i) &&
        m->name() == name && m->signature() == signature) {
      return i;
    }
  }
  return methodOopDesc::invalid_vtable_index;
}

// check if an entry is miranda
bool klassVtable::is_miranda_entry_at(int i) {
  methodOop m = method_at(i);
  klassOop method_holder = m->method_holder();
  instanceKlass *mhk = instanceKlass::cast(method_holder);

  // miranda methods are interface methods in a class's vtable
  if (mhk->is_interface()) {
    assert(m->is_public() && m->is_abstract(), "should be public and abstract");
    assert(ik()->implements_interface(method_holder) , "this class should implement the interface");
    assert(is_miranda(m, ik()->methods(), ik()->super()), "should be a miranda_method");
    return true;
  }
  return false;
}

// check if a method is a miranda method, given a class's methods table and it's super
// the caller must make sure that the method belongs to an interface implemented by the class
bool klassVtable::is_miranda(methodOop m, objArrayOop class_methods, klassOop super) {
  symbolOop name = m->name();
  symbolOop signature = m->signature();
  if (instanceKlass::find_method(class_methods, name, signature) == NULL) {
     // did not find it in the method table of the current class
    if (super == NULL) {
      // super doesn't exist
      return true;
    } else {
      if (instanceKlass::cast(super)->lookup_method(name, signature) == NULL) {
        // super class hierarchy does not implement it
        return true;
      }
    }
  }
  return false;
}

void klassVtable::add_new_mirandas_to_list(GrowableArray<methodOop>* list_of_current_mirandas,
                                           objArrayOop current_interface_methods,
                                           objArrayOop class_methods,
                                           klassOop super) {
  // iterate thru the current interface's method to see if it a miranda
  int num_methods = current_interface_methods->length();
  for (int i = 0; i < num_methods; i++) {
    methodOop im = methodOop(current_interface_methods->obj_at(i));
    bool is_duplicate = false;
    int num_of_current_mirandas = list_of_current_mirandas->length();
    // check for duplicate mirandas in different interfaces we implement
    for (int j = 0; j < num_of_current_mirandas; j++) {
      methodOop miranda = list_of_current_mirandas->at(j);
      if ((im->name() == miranda->name()) &&
          (im->signature() == miranda->signature())) {
        is_duplicate = true;
        break;
      }
    }

    if (!is_duplicate) { // we don't want duplicate miranda entries in the vtable
      if (is_miranda(im, class_methods, super)) { // is it a miranda at all?
        instanceKlass *sk = instanceKlass::cast(super);
        // check if it is a duplicate of a super's miranda
        if (sk->lookup_method_in_all_interfaces(im->name(), im->signature()) == NULL) {
          list_of_current_mirandas->append(im);
        }
      }
    }
  }
}

void klassVtable::get_mirandas(GrowableArray<methodOop>* mirandas,
                               klassOop super, objArrayOop class_methods,
                               objArrayOop local_interfaces) {
  assert((mirandas->length() == 0) , "current mirandas must be 0");

  // iterate thru the local interfaces looking for a miranda
  int num_local_ifs = local_interfaces->length();
  for (int i = 0; i < num_local_ifs; i++) {
    instanceKlass *ik = instanceKlass::cast(klassOop(local_interfaces->obj_at(i)));
    add_new_mirandas_to_list(mirandas, ik->methods(), class_methods, super);
    // iterate thru each local's super interfaces
    objArrayOop super_ifs = ik->transitive_interfaces();
    int num_super_ifs = super_ifs->length();
    for (int j = 0; j < num_super_ifs; j++) {
      instanceKlass *sik = instanceKlass::cast(klassOop(super_ifs->obj_at(j)));
      add_new_mirandas_to_list(mirandas, sik->methods(), class_methods, super);
    }
  }
}

// get number of mirandas
int klassVtable::get_num_mirandas(klassOop super, objArrayOop class_methods, objArrayOop local_interfaces) {
  ResourceMark rm;
  GrowableArray<methodOop>* mirandas = new GrowableArray<methodOop>(20);
  get_mirandas(mirandas, super, class_methods, local_interfaces);
  return mirandas->length();
}

// fill in mirandas
void klassVtable::fill_in_mirandas(int& initialized) {
  ResourceMark rm;
  GrowableArray<methodOop>* mirandas = new GrowableArray<methodOop>(20);
  instanceKlass *this_ik = ik();
  get_mirandas(mirandas, this_ik->super(), this_ik->methods(), this_ik->local_interfaces());
  int num_mirandas = mirandas->length();
  for (int i = 0; i < num_mirandas; i++) {
    put_method_at(mirandas->at(i), initialized);
    initialized++;
  }
}

void klassVtable::copy_vtable_to(vtableEntry* start) {
  Copy::disjoint_words((HeapWord*)table(), (HeapWord*)start, _length * vtableEntry::size());
}

void klassVtable::adjust_method_entries(methodOop* old_methods, methodOop* new_methods,
                                        int methods_length, bool * trace_name_printed) {
  // search the vtable for uses of either obsolete or EMCP methods
  for (int j = 0; j < methods_length; j++) {
    methodOop old_method = old_methods[j];
    methodOop new_method = new_methods[j];

    // In the vast majority of cases we could get the vtable index
    // by using:  old_method->vtable_index()
    // However, there are rare cases, eg. sun.awt.X11.XDecoratedPeer.getX()
    // in sun.awt.X11.XFramePeer where methods occur more than once in the
    // vtable, so, alas, we must do an exhaustive search.
    for (int index = 0; index < length(); index++) {
      if (unchecked_method_at(index) == old_method) {
        put_method_at(new_method, index);

        if (RC_TRACE_IN_RANGE(0x00100000, 0x00400000)) {
          if (!(*trace_name_printed)) {
            // RC_TRACE_MESG macro has an embedded ResourceMark
            RC_TRACE_MESG(("adjust: name=%s",
                           Klass::cast(old_method->method_holder())->external_name()));
            *trace_name_printed = true;
          }
          // RC_TRACE macro has an embedded ResourceMark
          RC_TRACE(0x00100000, ("vtable method update: %s(%s)",
                                new_method->name()->as_C_string(),
                                new_method->signature()->as_C_string()));
        }
      }
    }
  }
}


// Garbage collection
void klassVtable::oop_follow_contents() {
  int len = length();
  for (int i = 0; i < len; i++) {
    MarkSweep::mark_and_push(adr_method_at(i));
  }
}

#ifndef SERIALGC
void klassVtable::oop_follow_contents(ParCompactionManager* cm) {
  int len = length();
  for (int i = 0; i < len; i++) {
    PSParallelCompact::mark_and_push(cm, adr_method_at(i));
  }
}
#endif // SERIALGC

void klassVtable::oop_adjust_pointers() {
  int len = length();
  for (int i = 0; i < len; i++) {
    MarkSweep::adjust_pointer(adr_method_at(i));
  }
}

#ifndef SERIALGC
void klassVtable::oop_update_pointers(ParCompactionManager* cm) {
  const int n = length();
  for (int i = 0; i < n; i++) {
    PSParallelCompact::adjust_pointer(adr_method_at(i));
  }
}

void klassVtable::oop_update_pointers(ParCompactionManager* cm,
                                      HeapWord* beg_addr, HeapWord* end_addr) {
  const int n = length();
  const int entry_size = vtableEntry::size();

  int beg_idx = 0;
  HeapWord* const method_0 = (HeapWord*)adr_method_at(0);
  if (beg_addr > method_0) {
    // it's safe to use cast, as we have guarantees on vtable size to be sane
    beg_idx = int((pointer_delta(beg_addr, method_0) + entry_size - 1) / entry_size);
  }

  oop* const beg_oop = adr_method_at(beg_idx);
  oop* const end_oop = MIN2((oop*)end_addr, adr_method_at(n));
  for (oop* cur_oop = beg_oop; cur_oop < end_oop; cur_oop += entry_size) {
    PSParallelCompact::adjust_pointer(cur_oop);
  }
}
#endif // SERIALGC

// Iterators
void klassVtable::oop_oop_iterate(OopClosure* blk) {
  int len = length();
  for (int i = 0; i < len; i++) {
    blk->do_oop(adr_method_at(i));
  }
}

void klassVtable::oop_oop_iterate_m(OopClosure* blk, MemRegion mr) {
  int len = length();
  int i;
  for (i = 0; i < len; i++) {
    if ((HeapWord*)adr_method_at(i) >= mr.start()) break;
  }
  for (; i < len; i++) {
    oop* adr = adr_method_at(i);
    if ((HeapWord*)adr < mr.end()) blk->do_oop(adr);
  }
}

//-----------------------------------------------------------------------------------------
// Itable code

// Initialize a itableMethodEntry
void itableMethodEntry::initialize(methodOop m) {
  if (m == NULL) return;

  _method = m;
}

klassItable::klassItable(instanceKlassHandle klass) {
  _klass = klass;

  if (klass->itable_length() > 0) {
    itableOffsetEntry* offset_entry = (itableOffsetEntry*)klass->start_of_itable();
    if (offset_entry  != NULL && offset_entry->interface_klass() != NULL) { // Check that itable is initialized
      // First offset entry points to the first method_entry
      intptr_t* method_entry  = (intptr_t *)(((address)klass->as_klassOop()) + offset_entry->offset());
      intptr_t* end         = klass->end_of_itable();

      _table_offset      = (intptr_t*)offset_entry - (intptr_t*)klass->as_klassOop();
      _size_offset_table = (method_entry - ((intptr_t*)offset_entry)) / itableOffsetEntry::size();
      _size_method_table = (end - method_entry)                  / itableMethodEntry::size();
      assert(_table_offset >= 0 && _size_offset_table >= 0 && _size_method_table >= 0, "wrong computation");
      return;
    }
  }

  // The length of the itable was either zero, or it has not yet been initialized.
  _table_offset      = 0;
  _size_offset_table = 0;
  _size_method_table = 0;
}

// Garbage Collection

void klassItable::oop_follow_contents() {
  // offset table
  itableOffsetEntry* ioe = offset_entry(0);
  for(int i = 0; i < _size_offset_table; i++) {
    MarkSweep::mark_and_push((oop*)&ioe->_interface);
    ioe++;
  }

  // method table
  itableMethodEntry* ime = method_entry(0);
  for(int j = 0; j < _size_method_table; j++) {
    MarkSweep::mark_and_push((oop*)&ime->_method);
    ime++;
  }
}

#ifndef SERIALGC
void klassItable::oop_follow_contents(ParCompactionManager* cm) {
  // offset table
  itableOffsetEntry* ioe = offset_entry(0);
  for(int i = 0; i < _size_offset_table; i++) {
    PSParallelCompact::mark_and_push(cm, (oop*)&ioe->_interface);
    ioe++;
  }

  // method table
  itableMethodEntry* ime = method_entry(0);
  for(int j = 0; j < _size_method_table; j++) {
    PSParallelCompact::mark_and_push(cm, (oop*)&ime->_method);
    ime++;
  }
}
#endif // SERIALGC

void klassItable::oop_adjust_pointers() {
  // offset table
  itableOffsetEntry* ioe = offset_entry(0);
  for(int i = 0; i < _size_offset_table; i++) {
    MarkSweep::adjust_pointer((oop*)&ioe->_interface);
    ioe++;
  }

  // method table
  itableMethodEntry* ime = method_entry(0);
  for(int j = 0; j < _size_method_table; j++) {
    MarkSweep::adjust_pointer((oop*)&ime->_method);
    ime++;
  }
}

#ifndef SERIALGC
void klassItable::oop_update_pointers(ParCompactionManager* cm) {
  // offset table
  itableOffsetEntry* ioe = offset_entry(0);
  for(int i = 0; i < _size_offset_table; i++) {
    PSParallelCompact::adjust_pointer((oop*)&ioe->_interface);
    ioe++;
  }

  // method table
  itableMethodEntry* ime = method_entry(0);
  for(int j = 0; j < _size_method_table; j++) {
    PSParallelCompact::adjust_pointer((oop*)&ime->_method);
    ime++;
  }
}

void klassItable::oop_update_pointers(ParCompactionManager* cm,
                                      HeapWord* beg_addr, HeapWord* end_addr) {
  // offset table
  itableOffsetEntry* ioe = offset_entry(0);
  for(int i = 0; i < _size_offset_table; i++) {
    oop* p = (oop*)&ioe->_interface;
    PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
    ioe++;
  }

  // method table
  itableMethodEntry* ime = method_entry(0);
  for(int j = 0; j < _size_method_table; j++) {
    oop* p = (oop*)&ime->_method;
    PSParallelCompact::adjust_pointer(p, beg_addr, end_addr);
    ime++;
  }
}
#endif // SERIALGC

// Iterators
void klassItable::oop_oop_iterate(OopClosure* blk) {
  // offset table
  itableOffsetEntry* ioe = offset_entry(0);
  for(int i = 0; i < _size_offset_table; i++) {
    blk->do_oop((oop*)&ioe->_interface);
    ioe++;
  }

  // method table
  itableMethodEntry* ime = method_entry(0);
  for(int j = 0; j < _size_method_table; j++) {
    blk->do_oop((oop*)&ime->_method);
    ime++;
  }
}

void klassItable::oop_oop_iterate_m(OopClosure* blk, MemRegion mr) {
  // offset table
  itableOffsetEntry* ioe = offset_entry(0);
  for(int i = 0; i < _size_offset_table; i++) {
    oop* adr = (oop*)&ioe->_interface;
    if (mr.contains(adr)) blk->do_oop(adr);
    ioe++;
  }

  // method table
  itableMethodEntry* ime = method_entry(0);
  for(int j = 0; j < _size_method_table; j++) {
    oop* adr = (oop*)&ime->_method;
    if (mr.contains(adr)) blk->do_oop(adr);
    ime++;
  }
}


static int initialize_count = 0;

// Initialization
void klassItable::initialize_itable(bool checkconstraints, TRAPS) {
  // Cannot be setup doing bootstrapping, interfaces don't have
  // itables, and klass with only ones entry have empty itables
  if (Universe::is_bootstrapping() ||
      _klass->is_interface() ||
      _klass->itable_length() == itableOffsetEntry::size()) return;

  // There's alway an extra itable entry so we can null-terminate it.
  guarantee(size_offset_table() >= 1, "too small");
  int num_interfaces = size_offset_table() - 1;
  if (num_interfaces > 0) {
    if (TraceItables) tty->print_cr("%3d: Initializing itables for %s", ++initialize_count,
                                    _klass->name()->as_C_string());


    // Iterate through all interfaces
    int i;
    for(i = 0; i < num_interfaces; i++) {
      itableOffsetEntry* ioe = offset_entry(i);
      KlassHandle interf_h (THREAD, ioe->interface_klass());
      assert(interf_h() != NULL && ioe->offset() != 0, "bad offset entry in itable");
      initialize_itable_for_interface(ioe->offset(), interf_h, checkconstraints, CHECK);
    }

  }
  // Check that the last entry is empty
  itableOffsetEntry* ioe = offset_entry(size_offset_table() - 1);
  guarantee(ioe->interface_klass() == NULL && ioe->offset() == 0, "terminator entry missing");
}


void klassItable::initialize_itable_for_interface(int method_table_offset, KlassHandle interf_h, bool checkconstraints, TRAPS) {
  objArrayHandle methods(THREAD, instanceKlass::cast(interf_h())->methods());
  int nof_methods = methods()->length();
  HandleMark hm;
  KlassHandle klass = _klass;
  assert(nof_methods > 0, "at least one method must exist for interface to be in vtable")
  Handle interface_loader (THREAD, instanceKlass::cast(interf_h())->class_loader());
  int ime_num = 0;

  // Skip first methodOop if it is a class initializer
  int i = ((methodOop)methods()->obj_at(0))->name() != vmSymbols::class_initializer_name() ? 0 : 1;

  // m, method_name, method_signature, klass reset each loop so they
  // don't need preserving across check_signature_loaders call
  // methods needs a handle in case of gc from check_signature_loaders
  for(; i < nof_methods; i++) {
    methodOop m = (methodOop)methods()->obj_at(i);
    symbolOop method_name = m->name();
    symbolOop method_signature = m->signature();

    // This is same code as in Linkresolver::lookup_instance_method_in_klasses
    methodOop target = klass->uncached_lookup_method(method_name, method_signature);
    while (target != NULL && target->is_static()) {
      // continue with recursive lookup through the superclass
      klassOop super = Klass::cast(target->method_holder())->super();
      target = (super == NULL) ? methodOop(NULL) : Klass::cast(super)->uncached_lookup_method(method_name, method_signature);
    }
    if (target == NULL || !target->is_public() || target->is_abstract()) {
      // Entry do not resolve. Leave it empty
    } else {
      // Entry did resolve, check loader constraints before initializing
      // if checkconstraints requested
      methodHandle  target_h (THREAD, target); // preserve across gc
      if (checkconstraints) {
        Handle method_holder_loader (THREAD, instanceKlass::cast(target->method_holder())->class_loader());
        if (method_holder_loader() != interface_loader()) {
          ResourceMark rm(THREAD);
          char* failed_type_name =
            SystemDictionary::check_signature_loaders(method_signature,
                                                      method_holder_loader,
                                                      interface_loader,
                                                      true, CHECK);
          if (failed_type_name != NULL) {
            const char* msg = "loader constraint violation in interface "
              "itable initialization: when resolving method \"%s\" the class"
              " loader (instance of %s) of the current class, %s, "
              "and the class loader (instance of %s) for interface "
              "%s have different Class objects for the type %s "
              "used in the signature";
            char* sig = target_h()->name_and_sig_as_C_string();
            const char* loader1 = SystemDictionary::loader_name(method_holder_loader());
            char* current = klass->name()->as_C_string();
            const char* loader2 = SystemDictionary::loader_name(interface_loader());
            char* iface = instanceKlass::cast(interf_h())->name()->as_C_string();
            size_t buflen = strlen(msg) + strlen(sig) + strlen(loader1) +
              strlen(current) + strlen(loader2) + strlen(iface) +
              strlen(failed_type_name);
            char* buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, buflen);
            jio_snprintf(buf, buflen, msg, sig, loader1, current, loader2,
                         iface, failed_type_name);
            THROW_MSG(vmSymbols::java_lang_LinkageError(), buf);
          }
        }
      }

      // ime may have moved during GC so recalculate address
      itableOffsetEntry::method_entry(_klass(), method_table_offset)[ime_num].initialize(target_h());
    }
    // Progress to next entry
    ime_num++;
  }
}

// Update entry for specific methodOop
void klassItable::initialize_with_method(methodOop m) {
  itableMethodEntry* ime = method_entry(0);
  for(int i = 0; i < _size_method_table; i++) {
    if (ime->method() == m) {
      ime->initialize(m);
    }
    ime++;
  }
}

void klassItable::adjust_method_entries(methodOop* old_methods, methodOop* new_methods,
                                        int methods_length, bool * trace_name_printed) {
  // search the itable for uses of either obsolete or EMCP methods
  for (int j = 0; j < methods_length; j++) {
    methodOop old_method = old_methods[j];
    methodOop new_method = new_methods[j];
    itableMethodEntry* ime = method_entry(0);

    // The itable can describe more than one interface and the same
    // method signature can be specified by more than one interface.
    // This means we have to do an exhaustive search to find all the
    // old_method references.
    for (int i = 0; i < _size_method_table; i++) {
      if (ime->method() == old_method) {
        ime->initialize(new_method);

        if (RC_TRACE_IN_RANGE(0x00100000, 0x00400000)) {
          if (!(*trace_name_printed)) {
            // RC_TRACE_MESG macro has an embedded ResourceMark
            RC_TRACE_MESG(("adjust: name=%s",
              Klass::cast(old_method->method_holder())->external_name()));
            *trace_name_printed = true;
          }
          // RC_TRACE macro has an embedded ResourceMark
          RC_TRACE(0x00200000, ("itable method update: %s(%s)",
            new_method->name()->as_C_string(),
            new_method->signature()->as_C_string()));
        }
        break;
      }
      ime++;
    }
  }
}


// Setup
class InterfaceVisiterClosure : public StackObj {
 public:
  virtual void doit(klassOop intf, int method_count) = 0;
};

// Visit all interfaces with at-least one method (excluding <clinit>)
void visit_all_interfaces(objArrayOop transitive_intf, InterfaceVisiterClosure *blk) {
  // Handle array argument
  for(int i = 0; i < transitive_intf->length(); i++) {
    klassOop intf = (klassOop)transitive_intf->obj_at(i);
    assert(Klass::cast(intf)->is_interface(), "sanity check");

    // Find no. of methods excluding a <clinit>
    int method_count = instanceKlass::cast(intf)->methods()->length();
    if (method_count > 0) {
      methodOop m = (methodOop)instanceKlass::cast(intf)->methods()->obj_at(0);
      assert(m != NULL && m->is_method(), "sanity check");
      if (m->name() == vmSymbols::object_initializer_name()) {
        method_count--;
      }
    }

    // Only count interfaces with at least one method
    if (method_count > 0) {
      blk->doit(intf, method_count);
    }
  }
}

class CountInterfacesClosure : public InterfaceVisiterClosure {
 private:
  int _nof_methods;
  int _nof_interfaces;
 public:
   CountInterfacesClosure() { _nof_methods = 0; _nof_interfaces = 0; }

   int nof_methods() const    { return _nof_methods; }
   int nof_interfaces() const { return _nof_interfaces; }

   void doit(klassOop intf, int method_count) { _nof_methods += method_count; _nof_interfaces++; }
};

class SetupItableClosure : public InterfaceVisiterClosure  {
 private:
  itableOffsetEntry* _offset_entry;
  itableMethodEntry* _method_entry;
  address            _klass_begin;
 public:
  SetupItableClosure(address klass_begin, itableOffsetEntry* offset_entry, itableMethodEntry* method_entry) {
    _klass_begin  = klass_begin;
    _offset_entry = offset_entry;
    _method_entry = method_entry;
  }

  itableMethodEntry* method_entry() const { return _method_entry; }

  void doit(klassOop intf, int method_count) {
    int offset = ((address)_method_entry) - _klass_begin;
    _offset_entry->initialize(intf, offset);
    _offset_entry++;
    _method_entry += method_count;
  }
};

int klassItable::compute_itable_size(objArrayHandle transitive_interfaces) {
  // Count no of interfaces and total number of interface methods
  CountInterfacesClosure cic;
  visit_all_interfaces(transitive_interfaces(), &cic);

  // There's alway an extra itable entry so we can null-terminate it.
  int itable_size = calc_itable_size(cic.nof_interfaces() + 1, cic.nof_methods());

  // Statistics
  update_stats(itable_size * HeapWordSize);

  return itable_size;
}


// Fill out offset table and interface klasses into the itable space
void klassItable::setup_itable_offset_table(instanceKlassHandle klass) {
  if (klass->itable_length() == 0) return;
  assert(!klass->is_interface(), "Should have zero length itable");

  // Count no of interfaces and total number of interface methods
  CountInterfacesClosure cic;
  visit_all_interfaces(klass->transitive_interfaces(), &cic);
  int nof_methods    = cic.nof_methods();
  int nof_interfaces = cic.nof_interfaces();

  // Add one extra entry so we can null-terminate the table
  nof_interfaces++;

  assert(compute_itable_size(objArrayHandle(klass->transitive_interfaces())) ==
         calc_itable_size(nof_interfaces, nof_methods),
         "mismatch calculation of itable size");

  // Fill-out offset table
  itableOffsetEntry* ioe = (itableOffsetEntry*)klass->start_of_itable();
  itableMethodEntry* ime = (itableMethodEntry*)(ioe + nof_interfaces);
  intptr_t* end               = klass->end_of_itable();
  assert((oop*)(ime + nof_methods) <= (oop*)klass->start_of_static_fields(), "wrong offset calculation (1)");
  assert((oop*)(end) == (oop*)(ime + nof_methods),                      "wrong offset calculation (2)");

  // Visit all interfaces and initialize itable offset table
  SetupItableClosure sic((address)klass->as_klassOop(), ioe, ime);
  visit_all_interfaces(klass->transitive_interfaces(), &sic);

#ifdef ASSERT
  ime  = sic.method_entry();
  oop* v = (oop*) klass->end_of_itable();
  assert( (oop*)(ime) == v, "wrong offset calculation (2)");
#endif
}


// m must be a method in an interface
int klassItable::compute_itable_index(methodOop m) {
  klassOop intf = m->method_holder();
  assert(instanceKlass::cast(intf)->is_interface(), "sanity check");
  objArrayOop methods = instanceKlass::cast(intf)->methods();
  int index = 0;
  while(methods->obj_at(index) != m) {
    index++;
    assert(index < methods->length(), "should find index for resolve_invoke");
  }
  // Adjust for <clinit>, which is left out of table if first method
  if (methods->length() > 0 && ((methodOop)methods->obj_at(0))->name() == vmSymbols::class_initializer_name()) {
    index--;
  }
  return index;
}


// inverse to compute_itable_index
methodOop klassItable::method_for_itable_index(klassOop intf, int itable_index) {
  assert(instanceKlass::cast(intf)->is_interface(), "sanity check");
  objArrayOop methods = instanceKlass::cast(intf)->methods();

  int index = itable_index;
  // Adjust for <clinit>, which is left out of table if first method
  if (methods->length() > 0 && ((methodOop)methods->obj_at(0))->name() == vmSymbols::class_initializer_name()) {
    index++;
  }

  if (itable_index < 0 || index >= methods->length())
    return NULL;                // help caller defend against bad indexes

  methodOop m = (methodOop)methods->obj_at(index);
  assert(compute_itable_index(m) == itable_index, "correct inverse");

  return m;
}

void klassVtable::verify(outputStream* st, bool forced) {
  // make sure table is initialized
  if (!Universe::is_fully_initialized()) return;
#ifndef PRODUCT
  // avoid redundant verifies
  if (!forced && _verify_count == Universe::verify_count()) return;
  _verify_count = Universe::verify_count();
#endif
  oop* end_of_obj = (oop*)_klass() + _klass()->size();
  oop* end_of_vtable = (oop *)&table()[_length];
  if (end_of_vtable > end_of_obj) {
    fatal1("klass %s: klass object too short (vtable extends beyond end)",
          _klass->internal_name());
  }

  for (int i = 0; i < _length; i++) table()[i].verify(this, st);
  // verify consistency with superKlass vtable
  klassOop super = _klass->super();
  if (super != NULL) {
    instanceKlass* sk = instanceKlass::cast(super);
    klassVtable* vt = sk->vtable();
    for (int i = 0; i < vt->length(); i++) {
      verify_against(st, vt, i);
    }
  }
}

void klassVtable::verify_against(outputStream* st, klassVtable* vt, int index) {
  vtableEntry* vte = &vt->table()[index];
  if (vte->method()->name()      != table()[index].method()->name() ||
      vte->method()->signature() != table()[index].method()->signature()) {
    fatal("mismatched name/signature of vtable entries");
  }
}

#ifndef PRODUCT
void klassVtable::print() {
  ResourceMark rm;
  tty->print("klassVtable for klass %s (length %d):\n", _klass->internal_name(), length());
  for (int i = 0; i < length(); i++) {
    table()[i].print();
    tty->cr();
  }
}
#endif

void vtableEntry::verify(klassVtable* vt, outputStream* st) {
  NOT_PRODUCT(FlagSetting fs(IgnoreLockingAssertions, true));
  assert(method() != NULL, "must have set method");
  method()->verify();
  // we sub_type, because it could be a miranda method
  if (!vt->klass()->is_subtype_of(method()->method_holder())) {
#ifndef PRODUCT
    print();
#endif
    fatal1("vtableEntry %#lx: method is from subclass", this);
  }
}

#ifndef PRODUCT

void vtableEntry::print() {
  ResourceMark rm;
  tty->print("vtableEntry %s: ", method()->name()->as_C_string());
  if (Verbose) {
    tty->print("m %#lx ", (address)method());
  }
}

class VtableStats : AllStatic {
 public:
  static int no_klasses;                // # classes with vtables
  static int no_array_klasses;          // # array classes
  static int no_instance_klasses;       // # instanceKlasses
  static int sum_of_vtable_len;         // total # of vtable entries
  static int sum_of_array_vtable_len;   // total # of vtable entries in array klasses only
  static int fixed;                     // total fixed overhead in bytes
  static int filler;                    // overhead caused by filler bytes
  static int entries;                   // total bytes consumed by vtable entries
  static int array_entries;             // total bytes consumed by array vtable entries

  static void do_class(klassOop k) {
    Klass* kl = k->klass_part();
    klassVtable* vt = kl->vtable();
    if (vt == NULL) return;
    no_klasses++;
    if (kl->oop_is_instance()) {
      no_instance_klasses++;
      kl->array_klasses_do(do_class);
    }
    if (kl->oop_is_array()) {
      no_array_klasses++;
      sum_of_array_vtable_len += vt->length();
    }
    sum_of_vtable_len += vt->length();
  }

  static void compute() {
    SystemDictionary::classes_do(do_class);
    fixed  = no_klasses * oopSize;      // vtable length
    // filler size is a conservative approximation
    filler = oopSize * (no_klasses - no_instance_klasses) * (sizeof(instanceKlass) - sizeof(arrayKlass) - 1);
    entries = sizeof(vtableEntry) * sum_of_vtable_len;
    array_entries = sizeof(vtableEntry) * sum_of_array_vtable_len;
  }
};

int VtableStats::no_klasses = 0;
int VtableStats::no_array_klasses = 0;
int VtableStats::no_instance_klasses = 0;
int VtableStats::sum_of_vtable_len = 0;
int VtableStats::sum_of_array_vtable_len = 0;
int VtableStats::fixed = 0;
int VtableStats::filler = 0;
int VtableStats::entries = 0;
int VtableStats::array_entries = 0;

void klassVtable::print_statistics() {
  ResourceMark rm;
  HandleMark hm;
  VtableStats::compute();
  tty->print_cr("vtable statistics:");
  tty->print_cr("%6d classes (%d instance, %d array)", VtableStats::no_klasses, VtableStats::no_instance_klasses, VtableStats::no_array_klasses);
  int total = VtableStats::fixed + VtableStats::filler + VtableStats::entries;
  tty->print_cr("%6d bytes fixed overhead (refs + vtable object header)", VtableStats::fixed);
  tty->print_cr("%6d bytes filler overhead", VtableStats::filler);
  tty->print_cr("%6d bytes for vtable entries (%d for arrays)", VtableStats::entries, VtableStats::array_entries);
  tty->print_cr("%6d bytes total", total);
}

bool klassVtable::check_no_old_entries() {
  // Check that there really is no entry
  for (int i = 0; i < length(); i++) {
    methodOop m = unchecked_method_at(i);
    if (m != NULL) {
        if (m->is_old()) {
            return false;
        }
    }
  }
  return true;
}

void klassVtable::dump_vtable() {
  tty->print_cr("vtable dump --");
  for (int i = 0; i < length(); i++) {
    methodOop m = unchecked_method_at(i);
    if (m != NULL) {
      tty->print("      (%5d)  ", i);
      m->access_flags().print_on(tty);
      tty->print(" --  ");
      m->print_name(tty);
      tty->cr();
    }
  }
}

int  klassItable::_total_classes;   // Total no. of classes with itables
long klassItable::_total_size;      // Total no. of bytes used for itables

void klassItable::print_statistics() {
 tty->print_cr("itable statistics:");
 tty->print_cr("%6d classes with itables", _total_classes);
 tty->print_cr("%6d K uses for itables (average by class: %d bytes)", _total_size / K, _total_size / _total_classes);
}

#endif // PRODUCT
