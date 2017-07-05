/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

// ciObject
//
// This class represents an oop in the HotSpot virtual machine.
// Its subclasses are structured in a hierarchy which mirrors
// an aggregate of the VM's oop and klass hierarchies (see
// oopHierarchy.hpp).  Each instance of ciObject holds a handle
// to a corresponding oop on the VM side and provides routines
// for accessing the information in its oop.  By using the ciObject
// hierarchy for accessing oops in the VM, the compiler ensures
// that it is safe with respect to garbage collection; that is,
// GC and compilation can proceed independently without
// interference.
//
// Within the VM, the oop and klass hierarchies are separate.
// The compiler interface does not preserve this separation --
// the distinction between `klassOop' and `Klass' are not
// reflected in the interface and instead the Klass hierarchy
// is directly modeled as the subclasses of ciKlass.
class ciObject : public ResourceObj {
  CI_PACKAGE_ACCESS
  friend class ciEnv;

private:
  // A JNI handle referring to an oop in the VM.  This
  // handle may, in a small set of cases, correctly be NULL.
  jobject  _handle;
  ciKlass* _klass;
  uint     _ident;

  enum { FLAG_BITS   = 2 };
  enum {
         PERM_FLAG        = 1,
         SCAVENGABLE_FLAG = 2
       };
protected:
  ciObject();
  ciObject(oop o);
  ciObject(Handle h);
  ciObject(ciKlass* klass);

  jobject      handle()  const { return _handle; }
  // Get the VM oop that this object holds.
  oop get_oop() const {
    assert(_handle != NULL, "null oop");
    return JNIHandles::resolve_non_null(_handle);
  }

  void init_flags_from(oop x) {
    int flags = 0;
    if (x != NULL) {
      if (x->is_perm())
        flags |= PERM_FLAG;
      if (x->is_scavengable())
        flags |= SCAVENGABLE_FLAG;
    }
    _ident |= flags;
  }

  // Virtual behavior of the print() method.
  virtual void print_impl(outputStream* st) {}

  virtual const char* type_string() { return "ciObject"; }

  void set_ident(uint id);
public:
  // The klass of this ciObject.
  ciKlass* klass();

  // A number unique to this object.
  uint ident();

  // Are two ciObjects equal?
  bool equals(ciObject* obj);

  // A hash value for the convenience of compilers.
  int hash();

  // Tells if this oop has an encoding as a constant.
  // True if is_scavengable is false.
  // Also true if ScavengeRootsInCode is non-zero.
  // If it does not have an encoding, the compiler is responsible for
  // making other arrangements for dealing with the oop.
  // See ciEnv::make_array
  bool can_be_constant();

  // Tells if this oop should be made a constant.
  // True if is_scavengable is false or ScavengeRootsInCode > 1.
  bool should_be_constant();

  // Is this object guaranteed to be in the permanent part of the heap?
  // If so, CollectedHeap::can_elide_permanent_oop_store_barriers is relevant.
  // If the answer is false, no guarantees are made.
  bool is_perm() { return (_ident & PERM_FLAG) != 0; }

  // Might this object possibly move during a scavenge operation?
  // If the answer is true and ScavengeRootsInCode==0, the oop cannot be embedded in code.
  bool is_scavengable() { return (_ident & SCAVENGABLE_FLAG) != 0; }

  // The address which the compiler should embed into the
  // generated code to represent this oop.  This address
  // is not the true address of the oop -- it will get patched
  // during nmethod creation.
  //
  // Usage note: no address arithmetic allowed.  Oop must
  // be registered with the oopRecorder.
  jobject constant_encoding();

  // What kind of ciObject is this?
  virtual bool is_null_object() const       { return false; }
  virtual bool is_call_site() const         { return false; }
  virtual bool is_cpcache() const           { return false; }
  virtual bool is_instance()                { return false; }
  virtual bool is_method()                  { return false; }
  virtual bool is_method_data()             { return false; }
  virtual bool is_method_handle() const     { return false; }
  virtual bool is_array()                   { return false; }
  virtual bool is_obj_array()               { return false; }
  virtual bool is_type_array()              { return false; }
  virtual bool is_symbol()                  { return false; }
  virtual bool is_type()                    { return false; }
  virtual bool is_return_address()          { return false; }
  virtual bool is_klass()                   { return false; }
  virtual bool is_instance_klass()          { return false; }
  virtual bool is_method_klass()            { return false; }
  virtual bool is_array_klass()             { return false; }
  virtual bool is_obj_array_klass()         { return false; }
  virtual bool is_type_array_klass()        { return false; }
  virtual bool is_symbol_klass()            { return false; }
  virtual bool is_klass_klass()             { return false; }
  virtual bool is_instance_klass_klass()    { return false; }
  virtual bool is_array_klass_klass()       { return false; }
  virtual bool is_obj_array_klass_klass()   { return false; }
  virtual bool is_type_array_klass_klass()  { return false; }

  // Is this a type or value which has no associated class?
  // It is true of primitive types and null objects.
  virtual bool is_classless() const         { return false; }

  // Is this ciObject a Java Language Object?  That is,
  // is the ciObject an instance or an array
  virtual bool is_java_object()             { return false; }

  // Does this ciObject represent a Java Language class?
  // That is, is the ciObject an instanceKlass or arrayKlass?
  virtual bool is_java_klass()              { return false; }

  // Is this ciObject the ciInstanceKlass representing
  // java.lang.Object()?
  virtual bool is_java_lang_Object()        { return false; }

  // Does this ciObject refer to a real oop in the VM?
  //
  // Note: some ciObjects refer to oops which have yet to be
  // created.  We refer to these as "unloaded".  Specifically,
  // there are unloaded ciMethods, ciObjArrayKlasses, and
  // ciInstanceKlasses.  By convention the ciNullObject is
  // considered loaded, and primitive types are considered loaded.
  bool is_loaded() const {
    return handle() != NULL || is_classless();
  }

  // Subclass casting with assertions.
  ciNullObject*            as_null_object() {
    assert(is_null_object(), "bad cast");
    return (ciNullObject*)this;
  }
  ciCallSite*              as_call_site() {
    assert(is_call_site(), "bad cast");
    return (ciCallSite*) this;
  }
  ciCPCache*               as_cpcache() {
    assert(is_cpcache(), "bad cast");
    return (ciCPCache*) this;
  }
  ciInstance*              as_instance() {
    assert(is_instance(), "bad cast");
    return (ciInstance*)this;
  }
  ciMethod*                as_method() {
    assert(is_method(), "bad cast");
    return (ciMethod*)this;
  }
  ciMethodData*            as_method_data() {
    assert(is_method_data(), "bad cast");
    return (ciMethodData*)this;
  }
  ciMethodHandle*          as_method_handle() {
    assert(is_method_handle(), "bad cast");
    return (ciMethodHandle*) this;
  }
  ciArray*                 as_array() {
    assert(is_array(), "bad cast");
    return (ciArray*)this;
  }
  ciObjArray*              as_obj_array() {
    assert(is_obj_array(), "bad cast");
    return (ciObjArray*)this;
  }
  ciTypeArray*             as_type_array() {
    assert(is_type_array(), "bad cast");
    return (ciTypeArray*)this;
  }
  ciSymbol*                as_symbol() {
    assert(is_symbol(), "bad cast");
    return (ciSymbol*)this;
  }
  ciType*                  as_type() {
    assert(is_type(), "bad cast");
    return (ciType*)this;
  }
  ciReturnAddress*         as_return_address() {
    assert(is_return_address(), "bad cast");
    return (ciReturnAddress*)this;
  }
  ciKlass*                 as_klass() {
    assert(is_klass(), "bad cast");
    return (ciKlass*)this;
  }
  ciInstanceKlass*         as_instance_klass() {
    assert(is_instance_klass(), "bad cast");
    return (ciInstanceKlass*)this;
  }
  ciMethodKlass*           as_method_klass() {
    assert(is_method_klass(), "bad cast");
    return (ciMethodKlass*)this;
  }
  ciArrayKlass*            as_array_klass() {
    assert(is_array_klass(), "bad cast");
    return (ciArrayKlass*)this;
  }
  ciObjArrayKlass*         as_obj_array_klass() {
    assert(is_obj_array_klass(), "bad cast");
    return (ciObjArrayKlass*)this;
  }
  ciTypeArrayKlass*        as_type_array_klass() {
    assert(is_type_array_klass(), "bad cast");
    return (ciTypeArrayKlass*)this;
  }
  ciSymbolKlass*           as_symbol_klass() {
    assert(is_symbol_klass(), "bad cast");
    return (ciSymbolKlass*)this;
  }
  ciKlassKlass*            as_klass_klass() {
    assert(is_klass_klass(), "bad cast");
    return (ciKlassKlass*)this;
  }
  ciInstanceKlassKlass*    as_instance_klass_klass() {
    assert(is_instance_klass_klass(), "bad cast");
    return (ciInstanceKlassKlass*)this;
  }
  ciArrayKlassKlass*       as_array_klass_klass() {
    assert(is_array_klass_klass(), "bad cast");
    return (ciArrayKlassKlass*)this;
  }
  ciObjArrayKlassKlass*    as_obj_array_klass_klass() {
    assert(is_obj_array_klass_klass(), "bad cast");
    return (ciObjArrayKlassKlass*)this;
  }
  ciTypeArrayKlassKlass*   as_type_array_klass_klass() {
    assert(is_type_array_klass_klass(), "bad cast");
    return (ciTypeArrayKlassKlass*)this;
  }

  // Print debugging output about this ciObject.
  void print(outputStream* st = tty);

  // Print debugging output about the oop this ciObject represents.
  void print_oop(outputStream* st = tty);
};
