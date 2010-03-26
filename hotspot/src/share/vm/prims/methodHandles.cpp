/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * JSR 292 reference implementation: method handles
 */

#include "incls/_precompiled.incl"
#include "incls/_methodHandles.cpp.incl"

bool MethodHandles::_enabled = false; // set true after successful native linkage

MethodHandleEntry* MethodHandles::_entries[MethodHandles::_EK_LIMIT] = {NULL};
const char*        MethodHandles::_entry_names[_EK_LIMIT+1] = {
  "raise_exception",
  "invokestatic",               // how a MH emulates invokestatic
  "invokespecial",              // ditto for the other invokes...
  "invokevirtual",
  "invokeinterface",
  "bound_ref",                  // these are for BMH...
  "bound_int",
  "bound_long",
  "bound_ref_direct",           // (direct versions have a direct methodOop)
  "bound_int_direct",
  "bound_long_direct",

  // starting at _adapter_mh_first:
  "adapter_retype_only",       // these are for AMH...
  "adapter_retype_raw",
  "adapter_check_cast",
  "adapter_prim_to_prim",
  "adapter_ref_to_prim",
  "adapter_prim_to_ref",
  "adapter_swap_args",
  "adapter_rot_args",
  "adapter_dup_args",
  "adapter_drop_args",
  "adapter_collect_args",
  "adapter_spread_args",
  "adapter_flyby",
  "adapter_ricochet",

  // optimized adapter types:
  "adapter_swap_args/1",
  "adapter_swap_args/2",
  "adapter_rot_args/1,up",
  "adapter_rot_args/1,down",
  "adapter_rot_args/2,up",
  "adapter_rot_args/2,down",
  "adapter_prim_to_prim/i2i",
  "adapter_prim_to_prim/l2i",
  "adapter_prim_to_prim/d2f",
  "adapter_prim_to_prim/i2l",
  "adapter_prim_to_prim/f2d",
  "adapter_ref_to_prim/unboxi",
  "adapter_ref_to_prim/unboxl",
  "adapter_spread_args/0",
  "adapter_spread_args/1",
  "adapter_spread_args/more",

  NULL
};

// Adapters.
MethodHandlesAdapterBlob* MethodHandles::_adapter_code      = NULL;
int                       MethodHandles::_adapter_code_size = StubRoutines::method_handles_adapters_code_size;

jobject MethodHandles::_raise_exception_method;

#ifdef ASSERT
bool MethodHandles::spot_check_entry_names() {
  assert(!strcmp(entry_name(_invokestatic_mh), "invokestatic"), "");
  assert(!strcmp(entry_name(_bound_ref_mh), "bound_ref"), "");
  assert(!strcmp(entry_name(_adapter_retype_only), "adapter_retype_only"), "");
  assert(!strcmp(entry_name(_adapter_ricochet), "adapter_ricochet"), "");
  assert(!strcmp(entry_name(_adapter_opt_unboxi), "adapter_ref_to_prim/unboxi"), "");
  return true;
}
#endif


//------------------------------------------------------------------------------
// MethodHandles::generate_adapters
//
void MethodHandles::generate_adapters() {
  if (!EnableMethodHandles || SystemDictionary::MethodHandle_klass() == NULL)  return;

  assert(_adapter_code == NULL, "generate only once");

  ResourceMark rm;
  TraceTime timer("MethodHandles adapters generation", TraceStartupTime);
  _adapter_code = MethodHandlesAdapterBlob::create(_adapter_code_size);
  if (_adapter_code == NULL)
    vm_exit_out_of_memory(_adapter_code_size, "CodeCache: no room for MethodHandles adapters");
  CodeBuffer code(_adapter_code->instructions_begin(), _adapter_code->instructions_size());

  MethodHandlesAdapterGenerator g(&code);
  g.generate();
}


//------------------------------------------------------------------------------
// MethodHandlesAdapterGenerator::generate
//
void MethodHandlesAdapterGenerator::generate() {
  // Generate generic method handle adapters.
  for (MethodHandles::EntryKind ek = MethodHandles::_EK_FIRST;
       ek < MethodHandles::_EK_LIMIT;
       ek = MethodHandles::EntryKind(1 + (int)ek)) {
    StubCodeMark mark(this, "MethodHandle", MethodHandles::entry_name(ek));
    MethodHandles::generate_method_handle_stub(_masm, ek);
  }
}


void MethodHandles::set_enabled(bool z) {
  if (_enabled != z) {
    guarantee(z && EnableMethodHandles, "can only enable once, and only if -XX:+EnableMethodHandles");
    _enabled = z;
  }
}

// Note: A method which does not have a TRAPS argument cannot block in the GC
// or throw exceptions.  Such methods are used in this file to do something quick
// and local, like parse a data structure.  For speed, such methods work on plain
// oops, not handles.  Trapping methods uniformly operate on handles.

methodOop MethodHandles::decode_vmtarget(oop vmtarget, int vmindex, oop mtype,
                                         klassOop& receiver_limit_result, int& decode_flags_result) {
  if (vmtarget == NULL)  return NULL;
  assert(methodOopDesc::nonvirtual_vtable_index < 0, "encoding");
  if (vmindex < 0) {
    // this DMH performs no dispatch; it is directly bound to a methodOop
    // A MemberName may either be directly bound to a methodOop,
    // or it may use the klass/index form; both forms mean the same thing.
    methodOop m = decode_methodOop(methodOop(vmtarget), decode_flags_result);
    if ((decode_flags_result & _dmf_has_receiver) != 0
        && java_dyn_MethodType::is_instance(mtype)) {
      // Extract receiver type restriction from mtype.ptypes[0].
      objArrayOop ptypes = java_dyn_MethodType::ptypes(mtype);
      oop ptype0 = (ptypes == NULL || ptypes->length() < 1) ? oop(NULL) : ptypes->obj_at(0);
      if (java_lang_Class::is_instance(ptype0))
        receiver_limit_result = java_lang_Class::as_klassOop(ptype0);
    }
    if (vmindex == methodOopDesc::nonvirtual_vtable_index) {
      // this DMH can be an "invokespecial" version
      decode_flags_result &= ~_dmf_does_dispatch;
    } else {
      assert(vmindex == methodOopDesc::invalid_vtable_index, "random vmindex?");
    }
    return m;
  } else {
    assert(vmtarget->is_klass(), "must be class or interface");
    decode_flags_result |= MethodHandles::_dmf_does_dispatch;
    decode_flags_result |= MethodHandles::_dmf_has_receiver;
    receiver_limit_result = (klassOop)vmtarget;
    Klass* tk = Klass::cast((klassOop)vmtarget);
    if (tk->is_interface()) {
      // an itable linkage is <interface, itable index>
      decode_flags_result |= MethodHandles::_dmf_from_interface;
      return klassItable::method_for_itable_index((klassOop)vmtarget, vmindex);
    } else {
      if (!tk->oop_is_instance())
        tk = instanceKlass::cast(SystemDictionary::Object_klass());
      return ((instanceKlass*)tk)->method_at_vtable(vmindex);
    }
  }
}

// MemberName and DirectMethodHandle have the same linkage to the JVM internals.
// (MemberName is the non-operational name used for queries and setup.)

methodOop MethodHandles::decode_DirectMethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result) {
  oop vmtarget = sun_dyn_DirectMethodHandle::vmtarget(mh);
  int vmindex  = sun_dyn_DirectMethodHandle::vmindex(mh);
  oop mtype    = sun_dyn_DirectMethodHandle::type(mh);
  return decode_vmtarget(vmtarget, vmindex, mtype, receiver_limit_result, decode_flags_result);
}

methodOop MethodHandles::decode_BoundMethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result) {
  assert(sun_dyn_BoundMethodHandle::is_instance(mh), "");
  assert(mh->klass() != SystemDictionary::AdapterMethodHandle_klass(), "");
  for (oop bmh = mh;;) {
    // Bound MHs can be stacked to bind several arguments.
    oop target = java_dyn_MethodHandle::vmtarget(bmh);
    if (target == NULL)  return NULL;
    decode_flags_result |= MethodHandles::_dmf_binds_argument;
    klassOop tk = target->klass();
    if (tk == SystemDictionary::BoundMethodHandle_klass()) {
      bmh = target;
      continue;
    } else {
      if (java_dyn_MethodHandle::is_subclass(tk)) {
        //assert(tk == SystemDictionary::DirectMethodHandle_klass(), "end of BMH chain must be DMH");
        return decode_MethodHandle(target, receiver_limit_result, decode_flags_result);
      } else {
        // Optimized case:  binding a receiver to a non-dispatched DMH
        // short-circuits directly to the methodOop.
        // (It might be another argument besides a receiver also.)
        assert(target->is_method(), "must be a simple method");
        decode_flags_result |= MethodHandles::_dmf_binds_method;
        methodOop m = (methodOop) target;
        if (!m->is_static())
          decode_flags_result |= MethodHandles::_dmf_has_receiver;
        return m;
      }
    }
  }
}

methodOop MethodHandles::decode_AdapterMethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result) {
  assert(mh->klass() == SystemDictionary::AdapterMethodHandle_klass(), "");
  for (oop amh = mh;;) {
    // Adapter MHs can be stacked to convert several arguments.
    int conv_op = adapter_conversion_op(sun_dyn_AdapterMethodHandle::conversion(amh));
    decode_flags_result |= (_dmf_adapter_lsb << conv_op) & _DMF_ADAPTER_MASK;
    oop target = java_dyn_MethodHandle::vmtarget(amh);
    if (target == NULL)  return NULL;
    klassOop tk = target->klass();
    if (tk == SystemDictionary::AdapterMethodHandle_klass()) {
      amh = target;
      continue;
    } else {
      // must be a BMH (which will bind some more arguments) or a DMH (for the final call)
      return MethodHandles::decode_MethodHandle(target, receiver_limit_result, decode_flags_result);
    }
  }
}

methodOop MethodHandles::decode_MethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result) {
  if (mh == NULL)  return NULL;
  klassOop mhk = mh->klass();
  assert(java_dyn_MethodHandle::is_subclass(mhk), "must be a MethodHandle");
  if (mhk == SystemDictionary::DirectMethodHandle_klass()) {
    return decode_DirectMethodHandle(mh, receiver_limit_result, decode_flags_result);
  } else if (mhk == SystemDictionary::BoundMethodHandle_klass()) {
    return decode_BoundMethodHandle(mh, receiver_limit_result, decode_flags_result);
  } else if (mhk == SystemDictionary::AdapterMethodHandle_klass()) {
    return decode_AdapterMethodHandle(mh, receiver_limit_result, decode_flags_result);
  } else if (sun_dyn_BoundMethodHandle::is_subclass(mhk)) {
    // could be a JavaMethodHandle (but not an adapter MH)
    return decode_BoundMethodHandle(mh, receiver_limit_result, decode_flags_result);
  } else {
    assert(false, "cannot parse this MH");
    return NULL;              // random MH?
  }
}

methodOop MethodHandles::decode_methodOop(methodOop m, int& decode_flags_result) {
  assert(m->is_method(), "");
  if (m->is_static()) {
    // check that signature begins '(L' or '([' (not '(I', '()', etc.)
    symbolOop sig = m->signature();
    BasicType recv_bt = char2type(sig->byte_at(1));
    // Note: recv_bt might be T_ILLEGAL if byte_at(2) is ')'
    assert(sig->byte_at(0) == '(', "must be method sig");
//     if (recv_bt == T_OBJECT || recv_bt == T_ARRAY)
//       decode_flags_result |= _dmf_has_receiver;
  } else {
    // non-static method
    decode_flags_result |= _dmf_has_receiver;
    if (!m->can_be_statically_bound() && !m->is_initializer()) {
      decode_flags_result |= _dmf_does_dispatch;
      if (Klass::cast(m->method_holder())->is_interface())
        decode_flags_result |= _dmf_from_interface;
    }
  }
  return m;
}


// A trusted party is handing us a cookie to determine a method.
// Let's boil it down to the method oop they really want.
methodOop MethodHandles::decode_method(oop x, klassOop& receiver_limit_result, int& decode_flags_result) {
  decode_flags_result = 0;
  receiver_limit_result = NULL;
  klassOop xk = x->klass();
  if (xk == Universe::methodKlassObj()) {
    return decode_methodOop((methodOop) x, decode_flags_result);
  } else if (xk == SystemDictionary::MemberName_klass()) {
    // Note: This only works if the MemberName has already been resolved.
    return decode_MemberName(x, receiver_limit_result, decode_flags_result);
  } else if (java_dyn_MethodHandle::is_subclass(xk)) {
    return decode_MethodHandle(x, receiver_limit_result, decode_flags_result);
  } else if (xk == SystemDictionary::reflect_Method_klass()) {
    oop clazz  = java_lang_reflect_Method::clazz(x);
    int slot   = java_lang_reflect_Method::slot(x);
    klassOop k = java_lang_Class::as_klassOop(clazz);
    if (k != NULL && Klass::cast(k)->oop_is_instance())
      return decode_methodOop(instanceKlass::cast(k)->method_with_idnum(slot),
                              decode_flags_result);
  } else if (xk == SystemDictionary::reflect_Constructor_klass()) {
    oop clazz  = java_lang_reflect_Constructor::clazz(x);
    int slot   = java_lang_reflect_Constructor::slot(x);
    klassOop k = java_lang_Class::as_klassOop(clazz);
    if (k != NULL && Klass::cast(k)->oop_is_instance())
      return decode_methodOop(instanceKlass::cast(k)->method_with_idnum(slot),
                              decode_flags_result);
  } else {
    // unrecognized object
    assert(!x->is_method(), "already checked");
    assert(!sun_dyn_MemberName::is_instance(x), "already checked");
  }
  return NULL;
}


int MethodHandles::decode_MethodHandle_stack_pushes(oop mh) {
  if (mh->klass() == SystemDictionary::DirectMethodHandle_klass())
    return 0;                   // no push/pop
  int this_vmslots = java_dyn_MethodHandle::vmslots(mh);
  int last_vmslots = 0;
  oop last_mh = mh;
  for (;;) {
    oop target = java_dyn_MethodHandle::vmtarget(last_mh);
    if (target->klass() == SystemDictionary::DirectMethodHandle_klass()) {
      last_vmslots = java_dyn_MethodHandle::vmslots(target);
      break;
    } else if (!java_dyn_MethodHandle::is_instance(target)) {
      // might be klass or method
      assert(target->is_method(), "must get here with a direct ref to method");
      last_vmslots = methodOop(target)->size_of_parameters();
      break;
    }
    last_mh = target;
  }
  // If I am called with fewer VM slots than my ultimate callee,
  // it must be that I push the additionally needed slots.
  // Likewise if am called with more VM slots, I will pop them.
  return (last_vmslots - this_vmslots);
}


// MemberName support

// import sun_dyn_MemberName.*
enum {
  IS_METHOD      = sun_dyn_MemberName::MN_IS_METHOD,
  IS_CONSTRUCTOR = sun_dyn_MemberName::MN_IS_CONSTRUCTOR,
  IS_FIELD       = sun_dyn_MemberName::MN_IS_FIELD,
  IS_TYPE        = sun_dyn_MemberName::MN_IS_TYPE,
  SEARCH_SUPERCLASSES = sun_dyn_MemberName::MN_SEARCH_SUPERCLASSES,
  SEARCH_INTERFACES   = sun_dyn_MemberName::MN_SEARCH_INTERFACES,
  ALL_KINDS      = IS_METHOD | IS_CONSTRUCTOR | IS_FIELD | IS_TYPE,
  VM_INDEX_UNINITIALIZED = sun_dyn_MemberName::VM_INDEX_UNINITIALIZED
};

void MethodHandles::init_MemberName(oop mname_oop, oop target_oop) {
  if (target_oop->klass() == SystemDictionary::reflect_Field_klass()) {
    oop clazz = java_lang_reflect_Field::clazz(target_oop); // fd.field_holder()
    int slot  = java_lang_reflect_Field::slot(target_oop);  // fd.index()
    int mods  = java_lang_reflect_Field::modifiers(target_oop);
    klassOop k = java_lang_Class::as_klassOop(clazz);
    int offset = instanceKlass::cast(k)->offset_from_fields(slot);
    init_MemberName(mname_oop, k, accessFlags_from(mods), offset);
  } else {
    int decode_flags = 0; klassOop receiver_limit = NULL;
    methodOop m = MethodHandles::decode_method(target_oop,
                                               receiver_limit, decode_flags);
    bool do_dispatch = ((decode_flags & MethodHandles::_dmf_does_dispatch) != 0);
    init_MemberName(mname_oop, m, do_dispatch);
  }
}

void MethodHandles::init_MemberName(oop mname_oop, methodOop m, bool do_dispatch) {
  int flags = ((m->is_initializer() ? IS_CONSTRUCTOR : IS_METHOD)
               | (jushort)( m->access_flags().as_short() & JVM_RECOGNIZED_METHOD_MODIFIERS ));
  oop vmtarget = m;
  int vmindex  = methodOopDesc::invalid_vtable_index;  // implies no info yet
  if (!do_dispatch || (flags & IS_CONSTRUCTOR) || m->can_be_statically_bound())
    vmindex = methodOopDesc::nonvirtual_vtable_index; // implies never any dispatch
  assert(vmindex != VM_INDEX_UNINITIALIZED, "Java sentinel value");
  sun_dyn_MemberName::set_vmtarget(mname_oop, vmtarget);
  sun_dyn_MemberName::set_vmindex(mname_oop,  vmindex);
  sun_dyn_MemberName::set_flags(mname_oop,    flags);
}

void MethodHandles::init_MemberName(oop mname_oop, klassOop field_holder, AccessFlags mods, int offset) {
  int flags = (IS_FIELD | (jushort)( mods.as_short() & JVM_RECOGNIZED_FIELD_MODIFIERS ));
  oop vmtarget = field_holder;
  int vmindex  = offset;  // implies no info yet
  assert(vmindex != VM_INDEX_UNINITIALIZED, "bad alias on vmindex");
  sun_dyn_MemberName::set_vmtarget(mname_oop, vmtarget);
  sun_dyn_MemberName::set_vmindex(mname_oop,  vmindex);
  sun_dyn_MemberName::set_flags(mname_oop,    flags);
}


methodOop MethodHandles::decode_MemberName(oop mname, klassOop& receiver_limit_result, int& decode_flags_result) {
  int flags  = sun_dyn_MemberName::flags(mname);
  if ((flags & (IS_METHOD | IS_CONSTRUCTOR)) == 0)  return NULL;  // not invocable
  oop vmtarget = sun_dyn_MemberName::vmtarget(mname);
  int vmindex  = sun_dyn_MemberName::vmindex(mname);
  if (vmindex == VM_INDEX_UNINITIALIZED)  return NULL; // not resolved
  methodOop m = decode_vmtarget(vmtarget, vmindex, NULL, receiver_limit_result, decode_flags_result);
  oop clazz = sun_dyn_MemberName::clazz(mname);
  if (clazz != NULL && java_lang_Class::is_instance(clazz)) {
    klassOop klass = java_lang_Class::as_klassOop(clazz);
    if (klass != NULL)  receiver_limit_result = klass;
  }
  return m;
}

// An unresolved member name is a mere symbolic reference.
// Resolving it plants a vmtarget/vmindex in it,
// which refers dirctly to JVM internals.
void MethodHandles::resolve_MemberName(Handle mname, TRAPS) {
  assert(sun_dyn_MemberName::is_instance(mname()), "");
#ifdef ASSERT
  // If this assert throws, renegotiate the sentinel value used by the Java code,
  // so that it is distinct from any valid vtable index value, and any special
  // values defined in methodOopDesc::VtableIndexFlag.
  // The point of the slop is to give the Java code and the JVM some room
  // to independently specify sentinel values.
  const int sentinel_slop  = 10;
  const int sentinel_limit = methodOopDesc::highest_unused_vtable_index_value - sentinel_slop;
  assert(VM_INDEX_UNINITIALIZED < sentinel_limit, "Java sentinel != JVM sentinels");
#endif
  if (sun_dyn_MemberName::vmindex(mname()) != VM_INDEX_UNINITIALIZED)
    return;  // already resolved
  oop defc_oop = sun_dyn_MemberName::clazz(mname());
  oop name_str = sun_dyn_MemberName::name(mname());
  oop type_str = sun_dyn_MemberName::type(mname());
  int flags    = sun_dyn_MemberName::flags(mname());

  if (defc_oop == NULL || name_str == NULL || type_str == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "nothing to resolve");
  }
  klassOop defc_klassOop = java_lang_Class::as_klassOop(defc_oop);
  defc_oop = NULL;  // safety
  if (defc_klassOop == NULL)  return;  // a primitive; no resolution possible
  if (!Klass::cast(defc_klassOop)->oop_is_instance()) {
    if (!Klass::cast(defc_klassOop)->oop_is_array())  return;
    defc_klassOop = SystemDictionary::Object_klass();
  }
  instanceKlassHandle defc(THREAD, defc_klassOop);
  defc_klassOop = NULL;  // safety
  if (defc.is_null()) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "primitive class");
  }
  defc->link_class(CHECK);

  // convert the external string name to an internal symbol
  symbolHandle name(THREAD, java_lang_String::as_symbol_or_null(name_str));
  if (name.is_null())  return;  // no such name
  name_str = NULL;  // safety

  // convert the external string or reflective type to an internal signature
  bool force_signature = (name() == vmSymbols::invoke_name());
  symbolHandle type; {
    symbolOop type_sym = NULL;
    if (java_dyn_MethodType::is_instance(type_str)) {
      type_sym = java_dyn_MethodType::as_signature(type_str, force_signature, CHECK);
    } else if (java_lang_Class::is_instance(type_str)) {
      type_sym = java_lang_Class::as_signature(type_str, force_signature, CHECK);
    } else if (java_lang_String::is_instance(type_str)) {
      if (force_signature) {
        type     = java_lang_String::as_symbol(type_str, CHECK);
      } else {
        type_sym = java_lang_String::as_symbol_or_null(type_str);
      }
    } else {
      THROW_MSG(vmSymbols::java_lang_InternalError(), "unrecognized type");
    }
    if (type_sym != NULL)
      type = symbolHandle(THREAD, type_sym);
  }
  if (type.is_null())  return;  // no such signature exists in the VM
  type_str = NULL; // safety

  // Time to do the lookup.
  switch (flags & ALL_KINDS) {
  case IS_METHOD:
    {
      CallInfo result;
      {
        EXCEPTION_MARK;
        if ((flags & JVM_ACC_STATIC) != 0) {
          LinkResolver::resolve_static_call(result,
                        defc, name, type, KlassHandle(), false, false, THREAD);
        } else if (defc->is_interface()) {
          LinkResolver::resolve_interface_call(result, Handle(), defc,
                        defc, name, type, KlassHandle(), false, false, THREAD);
        } else {
          LinkResolver::resolve_virtual_call(result, Handle(), defc,
                        defc, name, type, KlassHandle(), false, false, THREAD);
        }
        if (HAS_PENDING_EXCEPTION) {
          CLEAR_PENDING_EXCEPTION;
          return;
        }
      }
      methodHandle m = result.resolved_method();
      oop vmtarget = NULL;
      int vmindex = methodOopDesc::nonvirtual_vtable_index;
      if (defc->is_interface()) {
        vmindex = klassItable::compute_itable_index(m());
        assert(vmindex >= 0, "");
      } else if (result.has_vtable_index()) {
        vmindex = result.vtable_index();
        assert(vmindex >= 0, "");
      }
      assert(vmindex != VM_INDEX_UNINITIALIZED, "");
      if (vmindex < 0) {
        assert(result.is_statically_bound(), "");
        vmtarget = m();
      } else {
        vmtarget = result.resolved_klass()->as_klassOop();
      }
      int mods = (m->access_flags().as_short() & JVM_RECOGNIZED_METHOD_MODIFIERS);
      sun_dyn_MemberName::set_vmtarget(mname(), vmtarget);
      sun_dyn_MemberName::set_vmindex(mname(),  vmindex);
      sun_dyn_MemberName::set_modifiers(mname(), mods);
      DEBUG_ONLY(int junk; klassOop junk2);
      assert(decode_MemberName(mname(), junk2, junk) == result.resolved_method()(),
             "properly stored for later decoding");
      return;
    }
  case IS_CONSTRUCTOR:
    {
      CallInfo result;
      {
        EXCEPTION_MARK;
        if (name() == vmSymbols::object_initializer_name()) {
          LinkResolver::resolve_special_call(result,
                        defc, name, type, KlassHandle(), false, THREAD);
        } else {
          break;                // will throw after end of switch
        }
        if (HAS_PENDING_EXCEPTION) {
          CLEAR_PENDING_EXCEPTION;
          return;
        }
      }
      assert(result.is_statically_bound(), "");
      methodHandle m = result.resolved_method();
      oop vmtarget = m();
      int vmindex  = methodOopDesc::nonvirtual_vtable_index;
      int mods     = (m->access_flags().as_short() & JVM_RECOGNIZED_METHOD_MODIFIERS);
      sun_dyn_MemberName::set_vmtarget(mname(), vmtarget);
      sun_dyn_MemberName::set_vmindex(mname(),  vmindex);
      sun_dyn_MemberName::set_modifiers(mname(), mods);
      DEBUG_ONLY(int junk; klassOop junk2);
      assert(decode_MemberName(mname(), junk2, junk) == result.resolved_method()(),
             "properly stored for later decoding");
      return;
    }
  case IS_FIELD:
    {
      // This is taken from LinkResolver::resolve_field, sans access checks.
      fieldDescriptor fd; // find_field initializes fd if found
      KlassHandle sel_klass(THREAD, instanceKlass::cast(defc())->find_field(name(), type(), &fd));
      // check if field exists; i.e., if a klass containing the field def has been selected
      if (sel_klass.is_null())  return;
      oop vmtarget = sel_klass->as_klassOop();
      int vmindex  = fd.offset();
      int mods     = (fd.access_flags().as_short() & JVM_RECOGNIZED_FIELD_MODIFIERS);
      if (vmindex == VM_INDEX_UNINITIALIZED)  break;  // should not happen
      sun_dyn_MemberName::set_vmtarget(mname(),  vmtarget);
      sun_dyn_MemberName::set_vmindex(mname(),   vmindex);
      sun_dyn_MemberName::set_modifiers(mname(), mods);
      return;
    }
  }
  THROW_MSG(vmSymbols::java_lang_InternalError(), "unrecognized MemberName format");
}

// Conversely, a member name which is only initialized from JVM internals
// may have null defc, name, and type fields.
// Resolving it plants a vmtarget/vmindex in it,
// which refers directly to JVM internals.
void MethodHandles::expand_MemberName(Handle mname, int suppress, TRAPS) {
  assert(sun_dyn_MemberName::is_instance(mname()), "");
  oop vmtarget = sun_dyn_MemberName::vmtarget(mname());
  int vmindex  = sun_dyn_MemberName::vmindex(mname());
  if (vmtarget == NULL || vmindex == VM_INDEX_UNINITIALIZED) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "nothing to expand");
  }

  bool have_defc = (sun_dyn_MemberName::clazz(mname()) != NULL);
  bool have_name = (sun_dyn_MemberName::name(mname()) != NULL);
  bool have_type = (sun_dyn_MemberName::type(mname()) != NULL);
  int flags      = sun_dyn_MemberName::flags(mname());

  if (suppress != 0) {
    if (suppress & _suppress_defc)  have_defc = true;
    if (suppress & _suppress_name)  have_name = true;
    if (suppress & _suppress_type)  have_type = true;
  }

  if (have_defc && have_name && have_type)  return;  // nothing needed

  switch (flags & ALL_KINDS) {
  case IS_METHOD:
  case IS_CONSTRUCTOR:
    {
      klassOop receiver_limit = NULL;
      int      decode_flags   = 0;
      methodHandle m(THREAD, decode_vmtarget(vmtarget, vmindex, NULL,
                                             receiver_limit, decode_flags));
      if (m.is_null())  break;
      if (!have_defc) {
        klassOop defc = m->method_holder();
        if (receiver_limit != NULL && receiver_limit != defc
            && Klass::cast(receiver_limit)->is_subtype_of(defc))
          defc = receiver_limit;
        sun_dyn_MemberName::set_clazz(mname(), Klass::cast(defc)->java_mirror());
      }
      if (!have_name) {
        //not java_lang_String::create_from_symbol; let's intern member names
        Handle name = StringTable::intern(m->name(), CHECK);
        sun_dyn_MemberName::set_name(mname(), name());
      }
      if (!have_type) {
        Handle type = java_lang_String::create_from_symbol(m->signature(), CHECK);
        sun_dyn_MemberName::set_type(mname(), type());
      }
      return;
    }
  case IS_FIELD:
    {
      // This is taken from LinkResolver::resolve_field, sans access checks.
      if (!vmtarget->is_klass())  break;
      if (!Klass::cast((klassOop) vmtarget)->oop_is_instance())  break;
      instanceKlassHandle defc(THREAD, (klassOop) vmtarget);
      bool is_static = ((flags & JVM_ACC_STATIC) != 0);
      fieldDescriptor fd; // find_field initializes fd if found
      if (!defc->find_field_from_offset(vmindex, is_static, &fd))
        break;                  // cannot expand
      if (!have_defc) {
        sun_dyn_MemberName::set_clazz(mname(), defc->java_mirror());
      }
      if (!have_name) {
        //not java_lang_String::create_from_symbol; let's intern member names
        Handle name = StringTable::intern(fd.name(), CHECK);
        sun_dyn_MemberName::set_name(mname(), name());
      }
      if (!have_type) {
        Handle type = java_lang_String::create_from_symbol(fd.signature(), CHECK);
        sun_dyn_MemberName::set_type(mname(), type());
      }
      return;
    }
  }
  THROW_MSG(vmSymbols::java_lang_InternalError(), "unrecognized MemberName format");
}

int MethodHandles::find_MemberNames(klassOop k,
                                    symbolOop name, symbolOop sig,
                                    int mflags, klassOop caller,
                                    int skip, objArrayOop results) {
  DEBUG_ONLY(No_Safepoint_Verifier nsv);
  // this code contains no safepoints!

  // %%% take caller into account!

  if (k == NULL || !Klass::cast(k)->oop_is_instance())  return -1;

  int rfill = 0, rlimit = results->length(), rskip = skip;
  // overflow measurement:
  int overflow = 0, overflow_limit = MAX2(1000, rlimit);

  int match_flags = mflags;
  bool search_superc = ((match_flags & SEARCH_SUPERCLASSES) != 0);
  bool search_intfc  = ((match_flags & SEARCH_INTERFACES)   != 0);
  bool local_only = !(search_superc | search_intfc);
  bool classes_only = false;

  if (name != NULL) {
    if (name->utf8_length() == 0)  return 0; // a match is not possible
  }
  if (sig != NULL) {
    if (sig->utf8_length() == 0)  return 0; // a match is not possible
    if (sig->byte_at(0) == '(')
      match_flags &= ~(IS_FIELD | IS_TYPE);
    else
      match_flags &= ~(IS_CONSTRUCTOR | IS_METHOD);
  }

  if ((match_flags & IS_TYPE) != 0) {
    // NYI, and Core Reflection works quite well for this query
  }

  if ((match_flags & IS_FIELD) != 0) {
    for (FieldStream st(k, local_only, !search_intfc); !st.eos(); st.next()) {
      if (name != NULL && st.name() != name)
          continue;
      if (sig != NULL && st.signature() != sig)
        continue;
      // passed the filters
      if (rskip > 0) {
        --rskip;
      } else if (rfill < rlimit) {
        oop result = results->obj_at(rfill++);
        if (!sun_dyn_MemberName::is_instance(result))
          return -99;  // caller bug!
        MethodHandles::init_MemberName(result, st.klass()->as_klassOop(), st.access_flags(), st.offset());
      } else if (++overflow >= overflow_limit) {
        match_flags = 0; break; // got tired of looking at overflow
      }
    }
  }

  if ((match_flags & (IS_METHOD | IS_CONSTRUCTOR)) != 0) {
    // watch out for these guys:
    symbolOop init_name   = vmSymbols::object_initializer_name();
    symbolOop clinit_name = vmSymbols::class_initializer_name();
    if (name == clinit_name)  clinit_name = NULL; // hack for exposing <clinit>
    bool negate_name_test = false;
    // fix name so that it captures the intention of IS_CONSTRUCTOR
    if (!(match_flags & IS_METHOD)) {
      // constructors only
      if (name == NULL) {
        name = init_name;
      } else if (name != init_name) {
        return 0;               // no constructors of this method name
      }
    } else if (!(match_flags & IS_CONSTRUCTOR)) {
      // methods only
      if (name == NULL) {
        name = init_name;
        negate_name_test = true; // if we see the name, we *omit* the entry
      } else if (name == init_name) {
        return 0;               // no methods of this constructor name
      }
    } else {
      // caller will accept either sort; no need to adjust name
    }
    for (MethodStream st(k, local_only, !search_intfc); !st.eos(); st.next()) {
      methodOop m = st.method();
      symbolOop m_name = m->name();
      if (m_name == clinit_name)
        continue;
      if (name != NULL && ((m_name != name) ^ negate_name_test))
          continue;
      if (sig != NULL && m->signature() != sig)
        continue;
      // passed the filters
      if (rskip > 0) {
        --rskip;
      } else if (rfill < rlimit) {
        oop result = results->obj_at(rfill++);
        if (!sun_dyn_MemberName::is_instance(result))
          return -99;  // caller bug!
        MethodHandles::init_MemberName(result, m, true);
      } else if (++overflow >= overflow_limit) {
        match_flags = 0; break; // got tired of looking at overflow
      }
    }
  }

  // return number of elements we at leasted wanted to initialize
  return rfill + overflow;
}




// Decode the vmtarget field of a method handle.
// Sanitize out methodOops, klassOops, and any other non-Java data.
// This is for debugging and reflection.
oop MethodHandles::encode_target(Handle mh, int format, TRAPS) {
  assert(java_dyn_MethodHandle::is_instance(mh()), "must be a MH");
  if (format == ETF_HANDLE_OR_METHOD_NAME) {
    oop target = java_dyn_MethodHandle::vmtarget(mh());
    if (target == NULL) {
      return NULL;                // unformed MH
    }
    klassOop tklass = target->klass();
    if (Klass::cast(tklass)->is_subclass_of(SystemDictionary::Object_klass())) {
      return target;              // target is another MH (or something else?)
    }
  }
  if (format == ETF_DIRECT_HANDLE) {
    oop target = mh();
    for (;;) {
      if (target->klass() == SystemDictionary::DirectMethodHandle_klass()) {
        return target;
      }
      if (!java_dyn_MethodHandle::is_instance(target)){
        return NULL;                // unformed MH
      }
      target = java_dyn_MethodHandle::vmtarget(target);
    }
  }
  // cases of metadata in MH.vmtarget:
  // - AMH can have methodOop for static invoke with bound receiver
  // - DMH can have methodOop for static invoke (on variable receiver)
  // - DMH can have klassOop for dispatched (non-static) invoke
  klassOop receiver_limit = NULL;
  int decode_flags = 0;
  methodOop m = decode_MethodHandle(mh(), receiver_limit, decode_flags);
  if (m == NULL)  return NULL;
  switch (format) {
  case ETF_REFLECT_METHOD:
    // same as jni_ToReflectedMethod:
    if (m->is_initializer()) {
      return Reflection::new_constructor(m, THREAD);
    } else {
      return Reflection::new_method(m, UseNewReflection, false, THREAD);
    }

  case ETF_HANDLE_OR_METHOD_NAME:   // method, not handle
  case ETF_METHOD_NAME:
    {
      if (SystemDictionary::MemberName_klass() == NULL)  break;
      instanceKlassHandle mname_klass(THREAD, SystemDictionary::MemberName_klass());
      mname_klass->initialize(CHECK_NULL);
      Handle mname = mname_klass->allocate_instance_handle(CHECK_NULL);
      sun_dyn_MemberName::set_vmindex(mname(), VM_INDEX_UNINITIALIZED);
      bool do_dispatch = ((decode_flags & MethodHandles::_dmf_does_dispatch) != 0);
      init_MemberName(mname(), m, do_dispatch);
      expand_MemberName(mname, 0, CHECK_NULL);
      return mname();
    }
  }

  // Unknown format code.
  char msg[50];
  jio_snprintf(msg, sizeof(msg), "unknown getTarget format=%d", format);
  THROW_MSG_NULL(vmSymbols::java_lang_IllegalArgumentException(), msg);
}

static const char* always_null_names[] = {
  "java/lang/Void",
  "java/lang/Null",
  //"java/lang/Nothing",
  "sun/dyn/empty/Empty",
  NULL
};

static bool is_always_null_type(klassOop klass) {
  if (!Klass::cast(klass)->oop_is_instance())  return false;
  instanceKlass* ik = instanceKlass::cast(klass);
  // Must be on the boot class path:
  if (ik->class_loader() != NULL)  return false;
  // Check the name.
  symbolOop name = ik->name();
  for (int i = 0; ; i++) {
    const char* test_name = always_null_names[i];
    if (test_name == NULL)  break;
    if (name->equals(test_name))
      return true;
  }
  return false;
}

bool MethodHandles::class_cast_needed(klassOop src, klassOop dst) {
  if (src == dst || dst == SystemDictionary::Object_klass())
    return false;                               // quickest checks
  Klass* srck = Klass::cast(src);
  Klass* dstk = Klass::cast(dst);
  if (dstk->is_interface()) {
    // interface receivers can safely be viewed as untyped,
    // because interface calls always include a dynamic check
    //dstk = Klass::cast(SystemDictionary::Object_klass());
    return false;
  }
  if (srck->is_interface()) {
    // interface arguments must be viewed as untyped
    //srck = Klass::cast(SystemDictionary::Object_klass());
    return true;
  }
  if (is_always_null_type(src)) {
    // some source types are known to be never instantiated;
    // they represent references which are always null
    // such null references never fail to convert safely
    return false;
  }
  return !srck->is_subclass_of(dstk->as_klassOop());
}

static oop object_java_mirror() {
  return Klass::cast(SystemDictionary::Object_klass())->java_mirror();
}

bool MethodHandles::same_basic_type_for_arguments(BasicType src,
                                                  BasicType dst,
                                                  bool raw,
                                                  bool for_return) {
  if (for_return) {
    // return values can always be forgotten:
    if (dst == T_VOID)  return true;
    if (src == T_VOID)  return raw && (dst == T_INT);
    // We allow caller to receive a garbage int, which is harmless.
    // This trick is pulled by trusted code (see VerifyType.canPassRaw).
  }
  assert(src != T_VOID && dst != T_VOID, "should not be here");
  if (src == dst)  return true;
  if (type2size[src] != type2size[dst])  return false;
  // allow reinterpretation casts for integral widening
  if (is_subword_type(src)) { // subwords can fit in int or other subwords
    if (dst == T_INT)         // any subword fits in an int
      return true;
    if (src == T_BOOLEAN)     // boolean fits in any subword
      return is_subword_type(dst);
    if (src == T_BYTE && dst == T_SHORT)
      return true;            // remaining case: byte fits in short
  }
  // allow float/fixed reinterpretation casts
  if (src == T_FLOAT)   return dst == T_INT;
  if (src == T_INT)     return dst == T_FLOAT;
  if (src == T_DOUBLE)  return dst == T_LONG;
  if (src == T_LONG)    return dst == T_DOUBLE;
  return false;
}

const char* MethodHandles::check_method_receiver(methodOop m,
                                                 klassOop passed_recv_type) {
  assert(!m->is_static(), "caller resp.");
  if (passed_recv_type == NULL)
    return "receiver type is primitive";
  if (class_cast_needed(passed_recv_type, m->method_holder())) {
    Klass* formal = Klass::cast(m->method_holder());
    return SharedRuntime::generate_class_cast_message("receiver type",
                                                      formal->external_name());
  }
  return NULL;                  // checks passed
}

// Verify that m's signature can be called type-safely by a method handle
// of the given method type 'mtype'.
// It takes a TRAPS argument because it must perform symbol lookups.
void MethodHandles::verify_method_signature(methodHandle m,
                                            Handle mtype,
                                            int first_ptype_pos,
                                            KlassHandle insert_ptype,
                                            TRAPS) {
  objArrayHandle ptypes(THREAD, java_dyn_MethodType::ptypes(mtype()));
  int pnum = first_ptype_pos;
  int pmax = ptypes->length();
  int mnum = 0;                 // method argument
  const char* err = NULL;
  for (SignatureStream ss(m->signature()); !ss.is_done(); ss.next()) {
    oop ptype_oop = NULL;
    if (ss.at_return_type()) {
      if (pnum != pmax)
        { err = "too many arguments"; break; }
      ptype_oop = java_dyn_MethodType::rtype(mtype());
    } else {
      if (pnum >= pmax)
        { err = "not enough arguments"; break; }
      if (pnum >= 0)
        ptype_oop = ptypes->obj_at(pnum);
      else if (insert_ptype.is_null())
        ptype_oop = NULL;
      else
        ptype_oop = insert_ptype->java_mirror();
      pnum += 1;
      mnum += 1;
    }
    klassOop  mklass = NULL;
    BasicType mtype  = ss.type();
    if (mtype == T_ARRAY)  mtype = T_OBJECT; // fold all refs to T_OBJECT
    if (mtype == T_OBJECT) {
      if (ptype_oop == NULL) {
        // null matches any reference
        continue;
      }
      // If we fail to resolve types at this point, we will throw an error.
      symbolOop    name_oop = ss.as_symbol(CHECK);
      symbolHandle name(THREAD, name_oop);
      instanceKlass* mk = instanceKlass::cast(m->method_holder());
      Handle loader(THREAD, mk->class_loader());
      Handle domain(THREAD, mk->protection_domain());
      mklass = SystemDictionary::resolve_or_fail(name, loader, domain,
                                                 true, CHECK);
    }
    if (ptype_oop == NULL) {
      // null does not match any non-reference; use Object to report the error
      ptype_oop = object_java_mirror();
    }
    klassOop  pklass = NULL;
    BasicType ptype  = java_lang_Class::as_BasicType(ptype_oop, &pklass);
    if (!ss.at_return_type()) {
      err = check_argument_type_change(ptype, pklass, mtype, mklass, mnum);
    } else {
      err = check_return_type_change(mtype, mklass, ptype, pklass); // note reversal!
    }
    if (err != NULL)  break;
  }

  if (err != NULL) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), err);
  }
}

// Main routine for verifying the MethodHandle.type of a proposed
// direct or bound-direct method handle.
void MethodHandles::verify_method_type(methodHandle m,
                                       Handle mtype,
                                       bool has_bound_recv,
                                       KlassHandle bound_recv_type,
                                       TRAPS) {
  bool m_needs_receiver = !m->is_static();

  const char* err = NULL;

  int first_ptype_pos = m_needs_receiver ? 1 : 0;
  if (has_bound_recv) {
    first_ptype_pos -= 1;  // ptypes do not include the bound argument; start earlier in them
    if (m_needs_receiver && bound_recv_type.is_null())
      { err = "bound receiver is not an object"; goto die; }
  }

  if (m_needs_receiver && err == NULL) {
    objArrayOop ptypes = java_dyn_MethodType::ptypes(mtype());
    if (ptypes->length() < first_ptype_pos)
      { err = "receiver argument is missing"; goto die; }
    if (has_bound_recv)
      err = check_method_receiver(m(), bound_recv_type->as_klassOop());
    else
      err = check_method_receiver(m(), java_lang_Class::as_klassOop(ptypes->obj_at(first_ptype_pos-1)));
    if (err != NULL)  goto die;
  }

  // Check the other arguments for mistypes.
  verify_method_signature(m, mtype, first_ptype_pos, bound_recv_type, CHECK);
  return;

 die:
  THROW_MSG(vmSymbols::java_lang_InternalError(), err);
}

void MethodHandles::verify_vmslots(Handle mh, TRAPS) {
  // Verify vmslots.
  int check_slots = argument_slot_count(java_dyn_MethodHandle::type(mh()));
  if (java_dyn_MethodHandle::vmslots(mh()) != check_slots) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "bad vmslots in BMH");
  }
}

void MethodHandles::verify_vmargslot(Handle mh, int argnum, int argslot, TRAPS) {
  // Verify that argslot points at the given argnum.
  int check_slot = argument_slot(java_dyn_MethodHandle::type(mh()), argnum);
  if (argslot != check_slot || argslot < 0) {
    const char* fmt = "for argnum of %d, vmargslot is %d, should be %d";
    size_t msglen = strlen(fmt) + 3*11 + 1;
    char* msg = NEW_RESOURCE_ARRAY(char, msglen);
    jio_snprintf(msg, msglen, fmt, argnum, argslot, check_slot);
    THROW_MSG(vmSymbols::java_lang_InternalError(), msg);
  }
}

// Verify the correspondence between two method types.
// Apart from the advertised changes, caller method type X must
// be able to invoke the callee method Y type with no violations
// of type integrity.
// Return NULL if all is well, else a short error message.
const char* MethodHandles::check_method_type_change(oop src_mtype, int src_beg, int src_end,
                                                    int insert_argnum, oop insert_type,
                                                    int change_argnum, oop change_type,
                                                    int delete_argnum,
                                                    oop dst_mtype, int dst_beg, int dst_end,
                                                    bool raw) {
  objArrayOop src_ptypes = java_dyn_MethodType::ptypes(src_mtype);
  objArrayOop dst_ptypes = java_dyn_MethodType::ptypes(dst_mtype);

  int src_max = src_ptypes->length();
  int dst_max = dst_ptypes->length();

  if (src_end == -1)  src_end = src_max;
  if (dst_end == -1)  dst_end = dst_max;

  assert(0 <= src_beg && src_beg <= src_end && src_end <= src_max, "oob");
  assert(0 <= dst_beg && dst_beg <= dst_end && dst_end <= dst_max, "oob");

  // pending actions; set to -1 when done:
  int ins_idx = insert_argnum, chg_idx = change_argnum, del_idx = delete_argnum;

  const char* err = NULL;

  // Walk along each array of parameter types, including a virtual
  // NULL end marker at the end of each.
  for (int src_idx = src_beg, dst_idx = dst_beg;
       (src_idx <= src_end && dst_idx <= dst_end);
       src_idx++, dst_idx++) {
    oop src_type = (src_idx == src_end) ? oop(NULL) : src_ptypes->obj_at(src_idx);
    oop dst_type = (dst_idx == dst_end) ? oop(NULL) : dst_ptypes->obj_at(dst_idx);
    bool fix_null_src_type = false;

    // Perform requested edits.
    if (ins_idx == src_idx) {
      // note that the inserted guy is never affected by a change or deletion
      ins_idx = -1;
      src_type = insert_type;
      fix_null_src_type = true;
      --src_idx;                // back up to process src type on next loop
      src_idx = src_end;
    } else {
      // note that the changed guy can be immediately deleted
      if (chg_idx == src_idx) {
        chg_idx = -1;
        assert(src_idx < src_end, "oob");
        src_type = change_type;
        fix_null_src_type = true;
      }
      if (del_idx == src_idx) {
        del_idx = -1;
        assert(src_idx < src_end, "oob");
        --dst_idx;
        continue;               // rerun loop after skipping this position
      }
    }

    if (src_type == NULL && fix_null_src_type)
      // explicit null in this case matches any dest reference
      src_type = (java_lang_Class::is_primitive(dst_type) ? object_java_mirror() : dst_type);

    // Compare the two argument types.
    if (src_type != dst_type) {
      if (src_type == NULL)  return "not enough arguments";
      if (dst_type == NULL)  return "too many arguments";
      err = check_argument_type_change(src_type, dst_type, dst_idx, raw);
      if (err != NULL)  return err;
    }
  }

  // Now compare return types also.
  oop src_rtype = java_dyn_MethodType::rtype(src_mtype);
  oop dst_rtype = java_dyn_MethodType::rtype(dst_mtype);
  if (src_rtype != dst_rtype) {
    err = check_return_type_change(dst_rtype, src_rtype, raw); // note reversal!
    if (err != NULL)  return err;
  }

  assert(err == NULL, "");
  return NULL;  // all is well
}


const char* MethodHandles::check_argument_type_change(BasicType src_type,
                                                      klassOop src_klass,
                                                      BasicType dst_type,
                                                      klassOop dst_klass,
                                                      int argnum,
                                                      bool raw) {
  const char* err = NULL;
  bool for_return = (argnum < 0);

  // just in case:
  if (src_type == T_ARRAY)  src_type = T_OBJECT;
  if (dst_type == T_ARRAY)  dst_type = T_OBJECT;

  // Produce some nice messages if VerifyMethodHandles is turned on:
  if (!same_basic_type_for_arguments(src_type, dst_type, raw, for_return)) {
    if (src_type == T_OBJECT) {
      if (raw && dst_type == T_INT && is_always_null_type(src_klass))
        return NULL;    // OK to convert a null pointer to a garbage int
      err = ((argnum >= 0)
             ? "type mismatch: passing a %s for method argument #%d, which expects primitive %s"
             : "type mismatch: returning a %s, but caller expects primitive %s");
    } else if (dst_type == T_OBJECT) {
      err = ((argnum >= 0)
             ? "type mismatch: passing a primitive %s for method argument #%d, which expects %s"
             : "type mismatch: returning a primitive %s, but caller expects %s");
    } else {
      err = ((argnum >= 0)
             ? "type mismatch: passing a %s for method argument #%d, which expects %s"
             : "type mismatch: returning a %s, but caller expects %s");
    }
  } else if (src_type == T_OBJECT && dst_type == T_OBJECT &&
             class_cast_needed(src_klass, dst_klass)) {
    if (!class_cast_needed(dst_klass, src_klass)) {
      if (raw)
        return NULL;    // reverse cast is OK; the MH target is trusted to enforce it
      err = ((argnum >= 0)
             ? "cast required: passing a %s for method argument #%d, which expects %s"
             : "cast required: returning a %s, but caller expects %s");
    } else {
      err = ((argnum >= 0)
             ? "reference mismatch: passing a %s for method argument #%d, which expects %s"
             : "reference mismatch: returning a %s, but caller expects %s");
    }
  } else {
    // passed the obstacle course
    return NULL;
  }

  // format, format, format
  const char* src_name = type2name(src_type);
  const char* dst_name = type2name(dst_type);
  if (src_type == T_OBJECT)  src_name = Klass::cast(src_klass)->external_name();
  if (dst_type == T_OBJECT)  dst_name = Klass::cast(dst_klass)->external_name();
  if (src_name == NULL)  src_name = "unknown type";
  if (dst_name == NULL)  dst_name = "unknown type";

  size_t msglen = strlen(err) + strlen(src_name) + strlen(dst_name) + (argnum < 10 ? 1 : 11);
  char* msg = NEW_RESOURCE_ARRAY(char, msglen + 1);
  if (argnum >= 0) {
    assert(strstr(err, "%d") != NULL, "");
    jio_snprintf(msg, msglen, err, src_name, argnum, dst_name);
  } else {
    assert(strstr(err, "%d") == NULL, "");
    jio_snprintf(msg, msglen, err, src_name,         dst_name);
  }
  return msg;
}

// Compute the depth within the stack of the given argument, i.e.,
// the combined size of arguments to the right of the given argument.
// For the last argument (ptypes.length-1) this will be zero.
// For the first argument (0) this will be the size of all
// arguments but that one.  For the special number -1, this
// will be the size of all arguments, including the first.
// If the argument is neither -1 nor a valid argument index,
// then return a negative number.  Otherwise, the result
// is in the range [0..vmslots] inclusive.
int MethodHandles::argument_slot(oop method_type, int arg) {
  objArrayOop ptypes = java_dyn_MethodType::ptypes(method_type);
  int argslot = 0;
  int len = ptypes->length();
  if (arg < -1 || arg >= len)  return -99;
  for (int i = len-1; i > arg; i--) {
    BasicType bt = java_lang_Class::as_BasicType(ptypes->obj_at(i));
    argslot += type2size[bt];
  }
  assert(argument_slot_to_argnum(method_type, argslot) == arg, "inverse works");
  return argslot;
}

// Given a slot number, return the argument number.
int MethodHandles::argument_slot_to_argnum(oop method_type, int query_argslot) {
  objArrayOop ptypes = java_dyn_MethodType::ptypes(method_type);
  int argslot = 0;
  int len = ptypes->length();
  for (int i = len-1; i >= 0; i--) {
    if (query_argslot == argslot)  return i;
    BasicType bt = java_lang_Class::as_BasicType(ptypes->obj_at(i));
    argslot += type2size[bt];
  }
  // return pseudo-arg deepest in stack:
  if (query_argslot == argslot)  return -1;
  return -99;                   // oob slot, or splitting a double-slot arg
}

methodHandle MethodHandles::dispatch_decoded_method(methodHandle m,
                                                    KlassHandle receiver_limit,
                                                    int decode_flags,
                                                    KlassHandle receiver_klass,
                                                    TRAPS) {
  assert((decode_flags & ~_DMF_DIRECT_MASK) == 0, "must be direct method reference");
  assert((decode_flags & _dmf_has_receiver) != 0, "must have a receiver or first reference argument");

  if (!m->is_static() &&
      (receiver_klass.is_null() || !receiver_klass->is_subtype_of(m->method_holder())))
    // given type does not match class of method, or receiver is null!
    // caller should have checked this, but let's be extra careful...
    return methodHandle();

  if (receiver_limit.not_null() &&
      (receiver_klass.not_null() && !receiver_klass->is_subtype_of(receiver_limit())))
    // given type is not limited to the receiver type
    // note that a null receiver can match any reference value, for a static method
    return methodHandle();

  if (!(decode_flags & MethodHandles::_dmf_does_dispatch)) {
    // pre-dispatched or static method (null receiver is OK for static)
    return m;

  } else if (receiver_klass.is_null()) {
    // null receiver value; cannot dispatch
    return methodHandle();

  } else if (!(decode_flags & MethodHandles::_dmf_from_interface)) {
    // perform virtual dispatch
    int vtable_index = m->vtable_index();
    guarantee(vtable_index >= 0, "valid vtable index");

    // receiver_klass might be an arrayKlassOop but all vtables start at
    // the same place. The cast is to avoid virtual call and assertion.
    // See also LinkResolver::runtime_resolve_virtual_method.
    instanceKlass* inst = (instanceKlass*)Klass::cast(receiver_klass());
    DEBUG_ONLY(inst->verify_vtable_index(vtable_index));
    methodOop m_oop = inst->method_at_vtable(vtable_index);
    return methodHandle(THREAD, m_oop);

  } else {
    // perform interface dispatch
    int itable_index = klassItable::compute_itable_index(m());
    guarantee(itable_index >= 0, "valid itable index");
    instanceKlass* inst = instanceKlass::cast(receiver_klass());
    methodOop m_oop = inst->method_at_itable(m->method_holder(), itable_index, THREAD);
    return methodHandle(THREAD, m_oop);
  }
}

void MethodHandles::verify_DirectMethodHandle(Handle mh, methodHandle m, TRAPS) {
  // Verify type.
  Handle mtype(THREAD, java_dyn_MethodHandle::type(mh()));
  verify_method_type(m, mtype, false, KlassHandle(), CHECK);

  // Verify vmslots.
  if (java_dyn_MethodHandle::vmslots(mh()) != m->size_of_parameters()) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "bad vmslots in DMH");
  }
}

void MethodHandles::init_DirectMethodHandle(Handle mh, methodHandle m, bool do_dispatch, TRAPS) {
  // Check arguments.
  if (mh.is_null() || m.is_null() ||
      (!do_dispatch && m->is_abstract())) {
    THROW(vmSymbols::java_lang_InternalError());
  }

  java_dyn_MethodHandle::init_vmslots(mh());

  if (VerifyMethodHandles) {
    // The privileged code which invokes this routine should not make
    // a mistake about types, but it's better to verify.
    verify_DirectMethodHandle(mh, m, CHECK);
  }

  // Finally, after safety checks are done, link to the target method.
  // We will follow the same path as the latter part of
  // InterpreterRuntime::resolve_invoke(), which first finds the method
  // and then decides how to populate the constant pool cache entry
  // that links the interpreter calls to the method.  We need the same
  // bits, and will use the same calling sequence code.

  int vmindex = methodOopDesc::garbage_vtable_index;
  oop vmtarget = NULL;

  instanceKlass::cast(m->method_holder())->link_class(CHECK);

  MethodHandleEntry* me = NULL;
  if (do_dispatch && Klass::cast(m->method_holder())->is_interface()) {
    // We are simulating an invokeinterface instruction.
    // (We might also be simulating an invokevirtual on a miranda method,
    // but it is safe to treat it as an invokeinterface.)
    assert(!m->can_be_statically_bound(), "no final methods on interfaces");
    vmindex = klassItable::compute_itable_index(m());
    assert(vmindex >= 0, "(>=0) == do_dispatch");
    // Set up same bits as ConstantPoolCacheEntry::set_interface_call().
    vmtarget = m->method_holder(); // the interface
    me = MethodHandles::entry(MethodHandles::_invokeinterface_mh);
  } else if (!do_dispatch || m->can_be_statically_bound()) {
    // We are simulating an invokestatic or invokespecial instruction.
    // Set up the method pointer, just like ConstantPoolCacheEntry::set_method().
    vmtarget = m();
    // this does not help dispatch, but it will make it possible to parse this MH:
    vmindex  = methodOopDesc::nonvirtual_vtable_index;
    assert(vmindex < 0, "(>=0) == do_dispatch");
    if (!m->is_static()) {
      me = MethodHandles::entry(MethodHandles::_invokespecial_mh);
    } else {
      me = MethodHandles::entry(MethodHandles::_invokestatic_mh);
      // Part of the semantics of a static call is an initialization barrier.
      // For a DMH, it is done now, when the handle is created.
      Klass* k = Klass::cast(m->method_holder());
      if (k->should_be_initialized()) {
        k->initialize(CHECK);
      }
    }
  } else {
    // We are simulating an invokevirtual instruction.
    // Set up the vtable index, just like ConstantPoolCacheEntry::set_method().
    // The key logic is LinkResolver::runtime_resolve_virtual_method.
    vmindex  = m->vtable_index();
    vmtarget = m->method_holder();
    me = MethodHandles::entry(MethodHandles::_invokevirtual_mh);
  }

  if (me == NULL) { THROW(vmSymbols::java_lang_InternalError()); }

  sun_dyn_DirectMethodHandle::set_vmtarget(mh(), vmtarget);
  sun_dyn_DirectMethodHandle::set_vmindex(mh(),  vmindex);
  DEBUG_ONLY(int flags; klassOop rlimit);
  assert(MethodHandles::decode_method(mh(), rlimit, flags) == m(),
         "properly stored for later decoding");
  DEBUG_ONLY(bool actual_do_dispatch = ((flags & _dmf_does_dispatch) != 0));
  assert(!(actual_do_dispatch && !do_dispatch),
         "do not perform dispatch if !do_dispatch specified");
  assert(actual_do_dispatch == (vmindex >= 0), "proper later decoding of do_dispatch");
  assert(decode_MethodHandle_stack_pushes(mh()) == 0, "DMH does not move stack");

  // Done!
  java_dyn_MethodHandle::set_vmentry(mh(), me);
}

void MethodHandles::verify_BoundMethodHandle_with_receiver(Handle mh,
                                                           methodHandle m,
                                                           TRAPS) {
  // Verify type.
  oop receiver = sun_dyn_BoundMethodHandle::argument(mh());
  Handle mtype(THREAD, java_dyn_MethodHandle::type(mh()));
  KlassHandle bound_recv_type;
  if (receiver != NULL)  bound_recv_type = KlassHandle(THREAD, receiver->klass());
  verify_method_type(m, mtype, true, bound_recv_type, CHECK);

  int receiver_pos = m->size_of_parameters() - 1;

  // Verify MH.vmargslot, which should point at the bound receiver.
  verify_vmargslot(mh, -1, sun_dyn_BoundMethodHandle::vmargslot(mh()), CHECK);
  //verify_vmslots(mh, CHECK);

  // Verify vmslots.
  if (java_dyn_MethodHandle::vmslots(mh()) != receiver_pos) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "bad vmslots in BMH (receiver)");
  }
}

// Initialize a BMH with a receiver bound directly to a methodOop.
void MethodHandles::init_BoundMethodHandle_with_receiver(Handle mh,
                                                         methodHandle original_m,
                                                         KlassHandle receiver_limit,
                                                         int decode_flags,
                                                         TRAPS) {
  // Check arguments.
  if (mh.is_null() || original_m.is_null()) {
    THROW(vmSymbols::java_lang_InternalError());
  }

  KlassHandle receiver_klass;
  {
    oop receiver_oop = sun_dyn_BoundMethodHandle::argument(mh());
    if (receiver_oop != NULL)
      receiver_klass = KlassHandle(THREAD, receiver_oop->klass());
  }
  methodHandle m = dispatch_decoded_method(original_m,
                                           receiver_limit, decode_flags,
                                           receiver_klass,
                                           CHECK);
  if (m.is_null())      { THROW(vmSymbols::java_lang_InternalError()); }
  if (m->is_abstract()) { THROW(vmSymbols::java_lang_AbstractMethodError()); }

  java_dyn_MethodHandle::init_vmslots(mh());

  if (VerifyMethodHandles) {
    verify_BoundMethodHandle_with_receiver(mh, m, CHECK);
  }

  sun_dyn_BoundMethodHandle::set_vmtarget(mh(), m());

  DEBUG_ONLY(int junk; klassOop junk2);
  assert(MethodHandles::decode_method(mh(), junk2, junk) == m(), "properly stored for later decoding");
  assert(decode_MethodHandle_stack_pushes(mh()) == 1, "BMH pushes one stack slot");

  // Done!
  java_dyn_MethodHandle::set_vmentry(mh(), MethodHandles::entry(MethodHandles::_bound_ref_direct_mh));
}

void MethodHandles::verify_BoundMethodHandle(Handle mh, Handle target, int argnum,
                                             bool direct_to_method, TRAPS) {
  Handle ptype_handle(THREAD,
                           java_dyn_MethodType::ptype(java_dyn_MethodHandle::type(target()), argnum));
  KlassHandle ptype_klass;
  BasicType ptype = java_lang_Class::as_BasicType(ptype_handle(), &ptype_klass);
  int slots_pushed = type2size[ptype];

  oop argument = sun_dyn_BoundMethodHandle::argument(mh());

  const char* err = NULL;

  switch (ptype) {
  case T_OBJECT:
    if (argument != NULL)
      // we must implicitly convert from the arg type to the outgoing ptype
      err = check_argument_type_change(T_OBJECT, argument->klass(), ptype, ptype_klass(), argnum);
    break;

  case T_ARRAY: case T_VOID:
    assert(false, "array, void do not appear here");
  default:
    if (ptype != T_INT && !is_subword_type(ptype)) {
      err = "unexpected parameter type";
      break;
    }
    // check subrange of Integer.value, if necessary
    if (argument == NULL || argument->klass() != SystemDictionary::Integer_klass()) {
      err = "bound integer argument must be of type java.lang.Integer";
      break;
    }
    if (ptype != T_INT) {
      int value_offset = java_lang_boxing_object::value_offset_in_bytes(T_INT);
      jint value = argument->int_field(value_offset);
      int vminfo = adapter_subword_vminfo(ptype);
      jint subword = truncate_subword_from_vminfo(value, vminfo);
      if (value != subword) {
        err = "bound subword value does not fit into the subword type";
        break;
      }
    }
    break;
  case T_FLOAT:
  case T_DOUBLE:
  case T_LONG:
    {
      // we must implicitly convert from the unboxed arg type to the outgoing ptype
      BasicType argbox = java_lang_boxing_object::basic_type(argument);
      if (argbox != ptype) {
        err = check_argument_type_change(T_OBJECT, (argument == NULL
                                                    ? SystemDictionary::Object_klass()
                                                    : argument->klass()),
                                         ptype, ptype_klass(), argnum);
        assert(err != NULL, "this must be an error");
      }
      break;
    }
  }

  if (err == NULL) {
    DEBUG_ONLY(int this_pushes = decode_MethodHandle_stack_pushes(mh()));
    if (direct_to_method) {
      assert(this_pushes == slots_pushed, "BMH pushes one or two stack slots");
      assert(slots_pushed <= MethodHandlePushLimit, "");
    } else {
      int target_pushes = decode_MethodHandle_stack_pushes(target());
      assert(this_pushes == slots_pushed + target_pushes, "BMH stack motion must be correct");
      // do not blow the stack; use a Java-based adapter if this limit is exceeded
      // FIXME
      // if (slots_pushed + target_pushes > MethodHandlePushLimit)
      //   err = "too many bound parameters";
    }
  }

  if (err == NULL) {
    // Verify the rest of the method type.
    err = check_method_type_insertion(java_dyn_MethodHandle::type(mh()),
                                      argnum, ptype_handle(),
                                      java_dyn_MethodHandle::type(target()));
  }

  if (err != NULL) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), err);
  }
}

void MethodHandles::init_BoundMethodHandle(Handle mh, Handle target, int argnum, TRAPS) {
  // Check arguments.
  if (mh.is_null() || target.is_null() || !java_dyn_MethodHandle::is_instance(target())) {
    THROW(vmSymbols::java_lang_InternalError());
  }

  java_dyn_MethodHandle::init_vmslots(mh());

  if (VerifyMethodHandles) {
    int insert_after = argnum - 1;
    verify_vmargslot(mh, insert_after, sun_dyn_BoundMethodHandle::vmargslot(mh()), CHECK);
    verify_vmslots(mh, CHECK);
  }

  // Get bound type and required slots.
  oop ptype_oop = java_dyn_MethodType::ptype(java_dyn_MethodHandle::type(target()), argnum);
  BasicType ptype = java_lang_Class::as_BasicType(ptype_oop);
  int slots_pushed = type2size[ptype];

  // If (a) the target is a direct non-dispatched method handle,
  // or (b) the target is a dispatched direct method handle and we
  // are binding the receiver, cut out the middle-man.
  // Do this by decoding the DMH and using its methodOop directly as vmtarget.
  bool direct_to_method = false;
  if (OptimizeMethodHandles &&
      target->klass() == SystemDictionary::DirectMethodHandle_klass() &&
      (argnum == 0 || sun_dyn_DirectMethodHandle::vmindex(target()) < 0)) {
    int decode_flags = 0; klassOop receiver_limit_oop = NULL;
    methodHandle m(THREAD, decode_method(target(), receiver_limit_oop, decode_flags));
    if (m.is_null()) { THROW_MSG(vmSymbols::java_lang_InternalError(), "DMH failed to decode"); }
    DEBUG_ONLY(int m_vmslots = m->size_of_parameters() - slots_pushed); // pos. of 1st arg.
    assert(sun_dyn_BoundMethodHandle::vmslots(mh()) == m_vmslots, "type w/ m sig");
    if (argnum == 0 && (decode_flags & _dmf_has_receiver) != 0) {
      KlassHandle receiver_limit(THREAD, receiver_limit_oop);
      init_BoundMethodHandle_with_receiver(mh, m,
                                           receiver_limit, decode_flags,
                                           CHECK);
      return;
    }

    // Even if it is not a bound receiver, we still might be able
    // to bind another argument and still invoke the methodOop directly.
    if (!(decode_flags & _dmf_does_dispatch)) {
      direct_to_method = true;
      sun_dyn_BoundMethodHandle::set_vmtarget(mh(), m());
    }
  }
  if (!direct_to_method)
    sun_dyn_BoundMethodHandle::set_vmtarget(mh(), target());

  if (VerifyMethodHandles) {
    verify_BoundMethodHandle(mh, target, argnum, direct_to_method, CHECK);
  }

  // Next question:  Is this a ref, int, or long bound value?
  MethodHandleEntry* me = NULL;
  if (ptype == T_OBJECT) {
    if (direct_to_method)  me = MethodHandles::entry(_bound_ref_direct_mh);
    else                   me = MethodHandles::entry(_bound_ref_mh);
  } else if (slots_pushed == 2) {
    if (direct_to_method)  me = MethodHandles::entry(_bound_long_direct_mh);
    else                   me = MethodHandles::entry(_bound_long_mh);
  } else if (slots_pushed == 1) {
    if (direct_to_method)  me = MethodHandles::entry(_bound_int_direct_mh);
    else                   me = MethodHandles::entry(_bound_int_mh);
  } else {
    assert(false, "");
  }

  // Done!
  java_dyn_MethodHandle::set_vmentry(mh(), me);
}

static void throw_InternalError_for_bad_conversion(int conversion, const char* err, TRAPS) {
  char msg[200];
  jio_snprintf(msg, sizeof(msg), "bad adapter (conversion=0x%08x): %s", conversion, err);
  THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), msg);
}

void MethodHandles::verify_AdapterMethodHandle(Handle mh, int argnum, TRAPS) {
  jint conversion = sun_dyn_AdapterMethodHandle::conversion(mh());
  int  argslot    = sun_dyn_AdapterMethodHandle::vmargslot(mh());

  verify_vmargslot(mh, argnum, argslot, CHECK);
  verify_vmslots(mh, CHECK);

  jint conv_op    = adapter_conversion_op(conversion);
  if (!conv_op_valid(conv_op)) {
    throw_InternalError_for_bad_conversion(conversion, "unknown conversion op", THREAD);
    return;
  }
  EntryKind ek = adapter_entry_kind(conv_op);

  int stack_move = adapter_conversion_stack_move(conversion);
  BasicType src  = adapter_conversion_src_type(conversion);
  BasicType dest = adapter_conversion_dest_type(conversion);
  int vminfo     = adapter_conversion_vminfo(conversion); // should be zero

  Handle argument(THREAD,  sun_dyn_AdapterMethodHandle::argument(mh()));
  Handle target(THREAD,    sun_dyn_AdapterMethodHandle::vmtarget(mh()));
  Handle src_mtype(THREAD, java_dyn_MethodHandle::type(mh()));
  Handle dst_mtype(THREAD, java_dyn_MethodHandle::type(target()));

  const char* err = NULL;

  if (err == NULL) {
    // Check that the correct argument is supplied, but only if it is required.
    switch (ek) {
    case _adapter_check_cast:     // target type of cast
    case _adapter_ref_to_prim:    // wrapper type from which to unbox
    case _adapter_prim_to_ref:    // wrapper type to box into
    case _adapter_collect_args:   // array type to collect into
    case _adapter_spread_args:    // array type to spread from
      if (!java_lang_Class::is_instance(argument())
          || java_lang_Class::is_primitive(argument()))
        { err = "adapter requires argument of type java.lang.Class"; break; }
      if (ek == _adapter_collect_args ||
          ek == _adapter_spread_args) {
        // Make sure it is a suitable collection type.  (Array, for now.)
        Klass* ak = Klass::cast(java_lang_Class::as_klassOop(argument()));
        if (!ak->oop_is_objArray()) {
          { err = "adapter requires argument of type java.lang.Class<Object[]>"; break; }
        }
      }
      break;
    case _adapter_flyby:
    case _adapter_ricochet:
      if (!java_dyn_MethodHandle::is_instance(argument()))
        { err = "MethodHandle adapter argument required"; break; }
      break;
    default:
      if (argument.not_null())
        { err = "adapter has spurious argument"; break; }
      break;
    }
  }

  if (err == NULL) {
    // Check that the src/dest types are supplied if needed.
    switch (ek) {
    case _adapter_check_cast:
      if (src != T_OBJECT || dest != T_OBJECT) {
        err = "adapter requires object src/dest conversion subfields";
      }
      break;
    case _adapter_prim_to_prim:
      if (!is_java_primitive(src) || !is_java_primitive(dest) || src == dest) {
        err = "adapter requires primitive src/dest conversion subfields"; break;
      }
      if ( (src == T_FLOAT || src == T_DOUBLE) && !(dest == T_FLOAT || dest == T_DOUBLE) ||
          !(src == T_FLOAT || src == T_DOUBLE) &&  (dest == T_FLOAT || dest == T_DOUBLE)) {
        err = "adapter cannot convert beween floating and fixed-point"; break;
      }
      break;
    case _adapter_ref_to_prim:
      if (src != T_OBJECT || !is_java_primitive(dest)
          || argument() != Klass::cast(SystemDictionary::box_klass(dest))->java_mirror()) {
        err = "adapter requires primitive dest conversion subfield"; break;
      }
      break;
    case _adapter_prim_to_ref:
      if (!is_java_primitive(src) || dest != T_OBJECT
          || argument() != Klass::cast(SystemDictionary::box_klass(src))->java_mirror()) {
        err = "adapter requires primitive src conversion subfield"; break;
      }
      break;
    case _adapter_swap_args:
    case _adapter_rot_args:
      {
        if (!src || src != dest) {
          err = "adapter requires src/dest conversion subfields for swap"; break;
        }
        int swap_size = type2size[src];
        oop src_mtype  = sun_dyn_AdapterMethodHandle::type(mh());
        oop dest_mtype = sun_dyn_AdapterMethodHandle::type(target());
        int slot_limit = sun_dyn_AdapterMethodHandle::vmslots(target());
        int src_slot   = argslot;
        int dest_slot  = vminfo;
        bool rotate_up = (src_slot > dest_slot); // upward rotation
        int src_arg    = argnum;
        int dest_arg   = argument_slot_to_argnum(dest_mtype, dest_slot);
        verify_vmargslot(mh, dest_arg, dest_slot, CHECK);
        if (!(dest_slot >= src_slot + swap_size) &&
            !(src_slot >= dest_slot + swap_size)) {
          err = "source, destination slots must be distinct";
        } else if (ek == _adapter_swap_args && !(src_slot > dest_slot)) {
          err = "source of swap must be deeper in stack";
        } else if (ek == _adapter_swap_args) {
          err = check_argument_type_change(java_dyn_MethodType::ptype(src_mtype, dest_arg),
                                           java_dyn_MethodType::ptype(dest_mtype, src_arg),
                                           dest_arg);
        } else if (ek == _adapter_rot_args) {
          if (rotate_up) {
            assert((src_slot > dest_slot) && (src_arg < dest_arg), "");
            // rotate up: [dest_slot..src_slot-ss] --> [dest_slot+ss..src_slot]
            // that is:   [src_arg+1..dest_arg] --> [src_arg..dest_arg-1]
            for (int i = src_arg+1; i <= dest_arg && err == NULL; i++) {
              err = check_argument_type_change(java_dyn_MethodType::ptype(src_mtype, i),
                                               java_dyn_MethodType::ptype(dest_mtype, i-1),
                                               i);
            }
          } else { // rotate down
            assert((src_slot < dest_slot) && (src_arg > dest_arg), "");
            // rotate down: [src_slot+ss..dest_slot] --> [src_slot..dest_slot-ss]
            // that is:     [dest_arg..src_arg-1] --> [dst_arg+1..src_arg]
            for (int i = dest_arg; i <= src_arg-1 && err == NULL; i++) {
              err = check_argument_type_change(java_dyn_MethodType::ptype(src_mtype, i),
                                               java_dyn_MethodType::ptype(dest_mtype, i+1),
                                               i);
            }
          }
        }
        if (err == NULL)
          err = check_argument_type_change(java_dyn_MethodType::ptype(src_mtype, src_arg),
                                           java_dyn_MethodType::ptype(dest_mtype, dest_arg),
                                           src_arg);
      }
      break;
    case _adapter_collect_args:
    case _adapter_spread_args:
      {
        BasicType coll_type = (ek == _adapter_collect_args) ? dest : src;
        BasicType elem_type = (ek == _adapter_collect_args) ? src : dest;
        if (coll_type != T_OBJECT || elem_type != T_OBJECT) {
          err = "adapter requires src/dest subfields"; break;
          // later:
          // - consider making coll be a primitive array
          // - consider making coll be a heterogeneous collection
        }
      }
      break;
    default:
      if (src != 0 || dest != 0) {
        err = "adapter has spurious src/dest conversion subfields"; break;
      }
      break;
    }
  }

  if (err == NULL) {
    // Check the stack_move subfield.
    // It must always report the net change in stack size, positive or negative.
    int slots_pushed = stack_move / stack_move_unit();
    switch (ek) {
    case _adapter_prim_to_prim:
    case _adapter_ref_to_prim:
    case _adapter_prim_to_ref:
      if (slots_pushed != type2size[dest] - type2size[src]) {
        err = "wrong stack motion for primitive conversion";
      }
      break;
    case _adapter_dup_args:
      if (slots_pushed <= 0) {
        err = "adapter requires conversion subfield slots_pushed > 0";
      }
      break;
    case _adapter_drop_args:
      if (slots_pushed >= 0) {
        err = "adapter requires conversion subfield slots_pushed < 0";
      }
      break;
    case _adapter_collect_args:
      if (slots_pushed > 1) {
        err = "adapter requires conversion subfield slots_pushed <= 1";
      }
      break;
    case _adapter_spread_args:
      if (slots_pushed < -1) {
        err = "adapter requires conversion subfield slots_pushed >= -1";
      }
      break;
    default:
      if (stack_move != 0) {
        err = "adapter has spurious stack_move conversion subfield";
      }
      break;
    }
    if (err == NULL && stack_move != slots_pushed * stack_move_unit()) {
      err = "stack_move conversion subfield must be multiple of stack_move_unit";
    }
  }

  if (err == NULL) {
    // Make sure this adapter does not push too deeply.
    int slots_pushed = stack_move / stack_move_unit();
    int this_vmslots = java_dyn_MethodHandle::vmslots(mh());
    int target_vmslots = java_dyn_MethodHandle::vmslots(target());
    if (slots_pushed != (target_vmslots - this_vmslots)) {
      err = "stack_move inconsistent with previous and current MethodType vmslots";
    } else if (slots_pushed > 0)  {
      // verify stack_move against MethodHandlePushLimit
      int target_pushes = decode_MethodHandle_stack_pushes(target());
      // do not blow the stack; use a Java-based adapter if this limit is exceeded
      if (slots_pushed + target_pushes > MethodHandlePushLimit) {
        err = "adapter pushes too many parameters";
      }
    }

    // While we're at it, check that the stack motion decoder works:
    DEBUG_ONLY(int target_pushes = decode_MethodHandle_stack_pushes(target()));
    DEBUG_ONLY(int this_pushes = decode_MethodHandle_stack_pushes(mh()));
    assert(this_pushes == slots_pushed + target_pushes, "AMH stack motion must be correct");
  }

  if (err == NULL && vminfo != 0) {
    switch (ek) {
      case _adapter_swap_args:
      case _adapter_rot_args:
        break;                // OK
    default:
      err = "vminfo subfield is reserved to the JVM";
    }
  }

  // Do additional ad hoc checks.
  if (err == NULL) {
    switch (ek) {
    case _adapter_retype_only:
      err = check_method_type_passthrough(src_mtype(), dst_mtype(), false);
      break;

    case _adapter_retype_raw:
      err = check_method_type_passthrough(src_mtype(), dst_mtype(), true);
      break;

    case _adapter_check_cast:
      {
        // The actual value being checked must be a reference:
        err = check_argument_type_change(java_dyn_MethodType::ptype(src_mtype(), argnum),
                                         object_java_mirror(), argnum);
        if (err != NULL)  break;

        // The output of the cast must fit with the destination argument:
        Handle cast_class = argument;
        err = check_method_type_conversion(src_mtype(),
                                           argnum, cast_class(),
                                           dst_mtype());
      }
      break;

      // %%% TO DO: continue in remaining cases to verify src/dst_mtype if VerifyMethodHandles
    }
  }

  if (err != NULL) {
    throw_InternalError_for_bad_conversion(conversion, err, THREAD);
    return;
  }

}

void MethodHandles::init_AdapterMethodHandle(Handle mh, Handle target, int argnum, TRAPS) {
  oop  argument   = sun_dyn_AdapterMethodHandle::argument(mh());
  int  argslot    = sun_dyn_AdapterMethodHandle::vmargslot(mh());
  jint conversion = sun_dyn_AdapterMethodHandle::conversion(mh());
  jint conv_op    = adapter_conversion_op(conversion);

  // adjust the adapter code to the internal EntryKind enumeration:
  EntryKind ek_orig = adapter_entry_kind(conv_op);
  EntryKind ek_opt  = ek_orig;  // may be optimized

  // Finalize the vmtarget field (Java initialized it to null).
  if (!java_dyn_MethodHandle::is_instance(target())) {
    throw_InternalError_for_bad_conversion(conversion, "bad target", THREAD);
    return;
  }
  sun_dyn_AdapterMethodHandle::set_vmtarget(mh(), target());

  if (VerifyMethodHandles) {
    verify_AdapterMethodHandle(mh, argnum, CHECK);
  }

  int stack_move = adapter_conversion_stack_move(conversion);
  BasicType src  = adapter_conversion_src_type(conversion);
  BasicType dest = adapter_conversion_dest_type(conversion);
  int vminfo     = adapter_conversion_vminfo(conversion); // should be zero

  const char* err = NULL;

  // Now it's time to finish the case analysis and pick a MethodHandleEntry.
  switch (ek_orig) {
  case _adapter_retype_only:
  case _adapter_retype_raw:
  case _adapter_check_cast:
  case _adapter_dup_args:
  case _adapter_drop_args:
    // these work fine via general case code
    break;

  case _adapter_prim_to_prim:
    {
      // Non-subword cases are {int,float,long,double} -> {int,float,long,double}.
      // And, the {float,double} -> {int,long} cases must be handled by Java.
      switch (type2size[src] *4+ type2size[dest]) {
      case 1 *4+ 1:
        assert(src == T_INT || is_subword_type(src), "source is not float");
        // Subword-related cases are int -> {boolean,byte,char,short}.
        ek_opt = _adapter_opt_i2i;
        vminfo = adapter_subword_vminfo(dest);
        break;
      case 2 *4+ 1:
        if (src == T_LONG && (dest == T_INT || is_subword_type(dest))) {
          ek_opt = _adapter_opt_l2i;
          vminfo = adapter_subword_vminfo(dest);
        } else if (src == T_DOUBLE && dest == T_FLOAT) {
          ek_opt = _adapter_opt_d2f;
        } else {
          assert(false, "");
        }
        break;
      case 1 *4+ 2:
        if (src == T_INT && dest == T_LONG) {
          ek_opt = _adapter_opt_i2l;
        } else if (src == T_FLOAT && dest == T_DOUBLE) {
          ek_opt = _adapter_opt_f2d;
        } else {
          assert(false, "");
        }
        break;
      default:
        assert(false, "");
        break;
      }
    }
    break;

  case _adapter_ref_to_prim:
    {
      switch (type2size[dest]) {
      case 1:
        ek_opt = _adapter_opt_unboxi;
        vminfo = adapter_subword_vminfo(dest);
        break;
      case 2:
        ek_opt = _adapter_opt_unboxl;
        break;
      default:
        assert(false, "");
        break;
      }
    }
    break;

  case _adapter_prim_to_ref:
    goto throw_not_impl;        // allocates, hence could block

  case _adapter_swap_args:
  case _adapter_rot_args:
    {
      int swap_slots = type2size[src];
      int slot_limit = sun_dyn_AdapterMethodHandle::vmslots(mh());
      int src_slot   = argslot;
      int dest_slot  = vminfo;
      int rotate     = (ek_orig == _adapter_swap_args) ? 0 : (src_slot > dest_slot) ? 1 : -1;
      switch (swap_slots) {
      case 1:
        ek_opt = (!rotate    ? _adapter_opt_swap_1 :
                  rotate > 0 ? _adapter_opt_rot_1_up : _adapter_opt_rot_1_down);
        break;
      case 2:
        ek_opt = (!rotate    ? _adapter_opt_swap_2 :
                  rotate > 0 ? _adapter_opt_rot_2_up : _adapter_opt_rot_2_down);
        break;
      default:
        assert(false, "");
        break;
      }
    }
    break;

  case _adapter_collect_args:
    goto throw_not_impl;        // allocates, hence could block

  case _adapter_spread_args:
    {
      // vminfo will be the required length of the array
      int slots_pushed = stack_move / stack_move_unit();
      int array_size   = slots_pushed + 1;
      assert(array_size >= 0, "");
      vminfo = array_size;
      switch (array_size) {
      case 0:   ek_opt = _adapter_opt_spread_0;       break;
      case 1:   ek_opt = _adapter_opt_spread_1;       break;
      default:  ek_opt = _adapter_opt_spread_more;    break;
      }
      if ((vminfo & CONV_VMINFO_MASK) != vminfo)
        goto throw_not_impl;    // overflow
    }
    break;

  case _adapter_flyby:
  case _adapter_ricochet:
    goto throw_not_impl;        // runs Java code, hence could block

  default:
    // should have failed much earlier; must be a missing case here
    assert(false, "incomplete switch");
    // and fall through:

  throw_not_impl:
    // FIXME: these adapters are NYI
    err = "adapter not yet implemented in the JVM";
    break;
  }

  if (err != NULL) {
    throw_InternalError_for_bad_conversion(conversion, err, THREAD);
    return;
  }

  // Rebuild the conversion value; maybe parts of it were changed.
  jint new_conversion = adapter_conversion(conv_op, src, dest, stack_move, vminfo);

  // Finalize the conversion field.  (Note that it is final to Java code.)
  sun_dyn_AdapterMethodHandle::set_conversion(mh(), new_conversion);

  // Done!
  java_dyn_MethodHandle::set_vmentry(mh(), entry(ek_opt));

  // There should be enough memory barriers on exit from native methods
  // to ensure that the MH is fully initialized to all threads before
  // Java code can publish it in global data structures.
}

//
// Here are the native methods on sun.dyn.MethodHandleImpl.
// They are the private interface between this JVM and the HotSpot-specific
// Java code that implements JSR 292 method handles.
//
// Note:  We use a JVM_ENTRY macro to define each of these, for this is the way
// that intrinsic (non-JNI) native methods are defined in HotSpot.
//

// direct method handles for invokestatic or invokespecial
// void init(DirectMethodHandle self, MemberName ref, boolean doDispatch, Class<?> caller);
JVM_ENTRY(void, MHI_init_DMH(JNIEnv *env, jobject igcls, jobject mh_jh,
                             jobject target_jh, jboolean do_dispatch, jobject caller_jh)) {
  ResourceMark rm;              // for error messages

  // This is the guy we are initializing:
  if (mh_jh == NULL) { THROW(vmSymbols::java_lang_InternalError()); }
  Handle mh(THREAD, JNIHandles::resolve_non_null(mh_jh));

  // Early returns out of this method leave the DMH in an unfinished state.
  assert(java_dyn_MethodHandle::vmentry(mh()) == NULL, "must be safely null");

  // which method are we really talking about?
  if (target_jh == NULL) { THROW(vmSymbols::java_lang_InternalError()); }
  oop target_oop = JNIHandles::resolve_non_null(target_jh);
  if (sun_dyn_MemberName::is_instance(target_oop) &&
      sun_dyn_MemberName::vmindex(target_oop) == VM_INDEX_UNINITIALIZED) {
    Handle mname(THREAD, target_oop);
    MethodHandles::resolve_MemberName(mname, CHECK);
    target_oop = mname(); // in case of GC
  }

  int decode_flags = 0; klassOop receiver_limit = NULL;
  methodHandle m(THREAD,
                 MethodHandles::decode_method(target_oop,
                                              receiver_limit, decode_flags));
  if (m.is_null()) { THROW_MSG(vmSymbols::java_lang_InternalError(), "no such method"); }

  // The trusted Java code that calls this method should already have performed
  // access checks on behalf of the given caller.  But, we can verify this.
  if (VerifyMethodHandles && caller_jh != NULL) {
    KlassHandle caller(THREAD, java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(caller_jh)));
    // If this were a bytecode, the first access check would be against
    // the "reference class" mentioned in the CONSTANT_Methodref.
    // For that class, we use the defining class of m,
    // or a more specific receiver limit if available.
    klassOop reference_klass = m->method_holder();  // OK approximation
    if (receiver_limit != NULL && receiver_limit != reference_klass) {
      if (!Klass::cast(receiver_limit)->is_subtype_of(reference_klass))
        THROW_MSG(vmSymbols::java_lang_InternalError(), "receiver limit out of bounds");  // Java code bug
      reference_klass = receiver_limit;
    }
    // Emulate LinkResolver::check_klass_accessability.
    if (!Reflection::verify_class_access(caller->as_klassOop(),
                                         reference_klass,
                                         true)) {
      THROW_MSG(vmSymbols::java_lang_InternalError(), Klass::cast(m->method_holder())->external_name());
    }
    // If there were a bytecode, the next step would be to lookup the method
    // in the reference class, then then check the method's access bits.
    // Emulate LinkResolver::check_method_accessability.
    klassOop resolved_klass = m->method_holder();
    if (!Reflection::verify_field_access(caller->as_klassOop(),
                                         resolved_klass, reference_klass,
                                         m->access_flags(),
                                         true)) {
      // %%% following cutout belongs in Reflection::verify_field_access?
      bool same_pm = Reflection::is_same_package_member(caller->as_klassOop(),
                                                        reference_klass, THREAD);
      if (!same_pm) {
        THROW_MSG(vmSymbols::java_lang_InternalError(), m->name_and_sig_as_C_string());
      }
    }
  }

  MethodHandles::init_DirectMethodHandle(mh, m, (do_dispatch != JNI_FALSE), CHECK);
}
JVM_END

// bound method handles
JVM_ENTRY(void, MHI_init_BMH(JNIEnv *env, jobject igcls, jobject mh_jh,
                             jobject target_jh, int argnum)) {
  ResourceMark rm;              // for error messages

  // This is the guy we are initializing:
  if (mh_jh == NULL) { THROW(vmSymbols::java_lang_InternalError()); }
  Handle mh(THREAD, JNIHandles::resolve_non_null(mh_jh));

  // Early returns out of this method leave the BMH in an unfinished state.
  assert(java_dyn_MethodHandle::vmentry(mh()) == NULL, "must be safely null");

  if (target_jh == NULL) { THROW(vmSymbols::java_lang_InternalError()); }
  Handle target(THREAD, JNIHandles::resolve_non_null(target_jh));

  if (!java_dyn_MethodHandle::is_instance(target())) {
    // Target object is a reflective method.  (%%% Do we need this alternate path?)
    Untested("init_BMH of non-MH");
    if (argnum != 0) { THROW(vmSymbols::java_lang_InternalError()); }
    int decode_flags = 0; klassOop receiver_limit_oop = NULL;
    methodHandle m(THREAD,
                   MethodHandles::decode_method(target(),
                                                receiver_limit_oop,
                                                decode_flags));
    KlassHandle receiver_limit(THREAD, receiver_limit_oop);
    MethodHandles::init_BoundMethodHandle_with_receiver(mh, m,
                                                       receiver_limit,
                                                       decode_flags,
                                                       CHECK);
    return;
  }

  // Build a BMH on top of a DMH or another BMH:
  MethodHandles::init_BoundMethodHandle(mh, target, argnum, CHECK);
}
JVM_END

// adapter method handles
JVM_ENTRY(void, MHI_init_AMH(JNIEnv *env, jobject igcls, jobject mh_jh,
                             jobject target_jh, int argnum)) {
  // This is the guy we are initializing:
  if (mh_jh == NULL || target_jh == NULL) {
    THROW(vmSymbols::java_lang_InternalError());
  }
  Handle mh(THREAD, JNIHandles::resolve_non_null(mh_jh));
  Handle target(THREAD, JNIHandles::resolve_non_null(target_jh));

  // Early returns out of this method leave the AMH in an unfinished state.
  assert(java_dyn_MethodHandle::vmentry(mh()) == NULL, "must be safely null");

  MethodHandles::init_AdapterMethodHandle(mh, target, argnum, CHECK);
}
JVM_END

// method type forms
JVM_ENTRY(void, MHI_init_MT(JNIEnv *env, jobject igcls, jobject erased_jh)) {
  if (erased_jh == NULL)  return;
  if (TraceMethodHandles) {
    tty->print("creating MethodType form ");
    if (WizardMode || Verbose) {   // Warning: this calls Java code on the MH!
      // call Object.toString()
      symbolOop name = vmSymbols::toString_name(), sig = vmSymbols::void_string_signature();
      JavaCallArguments args(Handle(THREAD, JNIHandles::resolve_non_null(erased_jh)));
      JavaValue result(T_OBJECT);
      JavaCalls::call_virtual(&result, SystemDictionary::Object_klass(), name, sig,
                              &args, CHECK);
      Handle str(THREAD, (oop)result.get_jobject());
      java_lang_String::print(str, tty);
    }
    tty->cr();
  }
}
JVM_END

// debugging and reflection
JVM_ENTRY(jobject, MHI_getTarget(JNIEnv *env, jobject igcls, jobject mh_jh, jint format)) {
  Handle mh(THREAD, JNIHandles::resolve(mh_jh));
  if (!java_dyn_MethodHandle::is_instance(mh())) {
    THROW_NULL(vmSymbols::java_lang_IllegalArgumentException());
  }
  oop target = MethodHandles::encode_target(mh, format, CHECK_NULL);
  return JNIHandles::make_local(THREAD, target);
}
JVM_END

JVM_ENTRY(jint, MHI_getConstant(JNIEnv *env, jobject igcls, jint which)) {
  switch (which) {
  case MethodHandles::GC_JVM_PUSH_LIMIT:
    guarantee(MethodHandlePushLimit >= 2 && MethodHandlePushLimit <= 0xFF,
              "MethodHandlePushLimit parameter must be in valid range");
    return MethodHandlePushLimit;
  case MethodHandles::GC_JVM_STACK_MOVE_UNIT:
    // return number of words per slot, signed according to stack direction
    return MethodHandles::stack_move_unit();
  }
  return 0;
}
JVM_END

#ifndef PRODUCT
#define EACH_NAMED_CON(template) \
    template(MethodHandles,GC_JVM_PUSH_LIMIT) \
    template(MethodHandles,GC_JVM_STACK_MOVE_UNIT) \
    template(MethodHandles,ETF_HANDLE_OR_METHOD_NAME) \
    template(MethodHandles,ETF_DIRECT_HANDLE) \
    template(MethodHandles,ETF_METHOD_NAME) \
    template(MethodHandles,ETF_REFLECT_METHOD) \
    template(sun_dyn_MemberName,MN_IS_METHOD) \
    template(sun_dyn_MemberName,MN_IS_CONSTRUCTOR) \
    template(sun_dyn_MemberName,MN_IS_FIELD) \
    template(sun_dyn_MemberName,MN_IS_TYPE) \
    template(sun_dyn_MemberName,MN_SEARCH_SUPERCLASSES) \
    template(sun_dyn_MemberName,MN_SEARCH_INTERFACES) \
    template(sun_dyn_MemberName,VM_INDEX_UNINITIALIZED) \
    template(sun_dyn_AdapterMethodHandle,OP_RETYPE_ONLY) \
    template(sun_dyn_AdapterMethodHandle,OP_RETYPE_RAW) \
    template(sun_dyn_AdapterMethodHandle,OP_CHECK_CAST) \
    template(sun_dyn_AdapterMethodHandle,OP_PRIM_TO_PRIM) \
    template(sun_dyn_AdapterMethodHandle,OP_REF_TO_PRIM) \
    template(sun_dyn_AdapterMethodHandle,OP_PRIM_TO_REF) \
    template(sun_dyn_AdapterMethodHandle,OP_SWAP_ARGS) \
    template(sun_dyn_AdapterMethodHandle,OP_ROT_ARGS) \
    template(sun_dyn_AdapterMethodHandle,OP_DUP_ARGS) \
    template(sun_dyn_AdapterMethodHandle,OP_DROP_ARGS) \
    template(sun_dyn_AdapterMethodHandle,OP_COLLECT_ARGS) \
    template(sun_dyn_AdapterMethodHandle,OP_SPREAD_ARGS) \
    template(sun_dyn_AdapterMethodHandle,OP_FLYBY) \
    template(sun_dyn_AdapterMethodHandle,OP_RICOCHET) \
    template(sun_dyn_AdapterMethodHandle,CONV_OP_LIMIT) \
    template(sun_dyn_AdapterMethodHandle,CONV_OP_MASK) \
    template(sun_dyn_AdapterMethodHandle,CONV_VMINFO_MASK) \
    template(sun_dyn_AdapterMethodHandle,CONV_VMINFO_SHIFT) \
    template(sun_dyn_AdapterMethodHandle,CONV_OP_SHIFT) \
    template(sun_dyn_AdapterMethodHandle,CONV_DEST_TYPE_SHIFT) \
    template(sun_dyn_AdapterMethodHandle,CONV_SRC_TYPE_SHIFT) \
    template(sun_dyn_AdapterMethodHandle,CONV_STACK_MOVE_SHIFT) \
    template(sun_dyn_AdapterMethodHandle,CONV_STACK_MOVE_MASK) \
    /*end*/

#define ONE_PLUS(scope,value) 1+
static const int con_value_count = EACH_NAMED_CON(ONE_PLUS) 0;
#define VALUE_COMMA(scope,value) scope::value,
static const int con_values[con_value_count+1] = { EACH_NAMED_CON(VALUE_COMMA) 0 };
#define STRING_NULL(scope,value) #value "\0"
static const char con_names[] = { EACH_NAMED_CON(STRING_NULL) };

#undef ONE_PLUS
#undef VALUE_COMMA
#undef STRING_NULL
#undef EACH_NAMED_CON
#endif

JVM_ENTRY(jint, MHI_getNamedCon(JNIEnv *env, jobject igcls, jint which, jobjectArray box_jh)) {
#ifndef PRODUCT
  if (which >= 0 && which < con_value_count) {
    int con = con_values[which];
    objArrayOop box = (objArrayOop) JNIHandles::resolve(box_jh);
    if (box != NULL && box->klass() == Universe::objectArrayKlassObj() && box->length() > 0) {
      const char* str = &con_names[0];
      for (int i = 0; i < which; i++)
        str += strlen(str) + 1;   // skip name and null
      oop name = java_lang_String::create_oop_from_str(str, CHECK_0);
      box->obj_at_put(0, name);
    }
    return con;
  }
#endif
  return 0;
}
JVM_END

// void init(MemberName self, AccessibleObject ref)
JVM_ENTRY(void, MHI_init_Mem(JNIEnv *env, jobject igcls, jobject mname_jh, jobject target_jh)) {
  if (mname_jh == NULL || target_jh == NULL) { THROW(vmSymbols::java_lang_InternalError()); }
  Handle mname(THREAD, JNIHandles::resolve_non_null(mname_jh));
  oop target_oop = JNIHandles::resolve_non_null(target_jh);
  MethodHandles::init_MemberName(mname(), target_oop);
}
JVM_END

// void expand(MemberName self)
JVM_ENTRY(void, MHI_expand_Mem(JNIEnv *env, jobject igcls, jobject mname_jh)) {
  if (mname_jh == NULL) { THROW(vmSymbols::java_lang_InternalError()); }
  Handle mname(THREAD, JNIHandles::resolve_non_null(mname_jh));
  MethodHandles::expand_MemberName(mname, 0, CHECK);
}
JVM_END

// void resolve(MemberName self, Class<?> caller)
JVM_ENTRY(void, MHI_resolve_Mem(JNIEnv *env, jobject igcls, jobject mname_jh, jclass caller_jh)) {
  if (mname_jh == NULL) { THROW(vmSymbols::java_lang_InternalError()); }
  Handle mname(THREAD, JNIHandles::resolve_non_null(mname_jh));
  // %%% take caller into account!
  MethodHandles::resolve_MemberName(mname, CHECK);
}
JVM_END

//  static native int getMembers(Class<?> defc, String matchName, String matchSig,
//          int matchFlags, Class<?> caller, int skip, MemberName[] results);
JVM_ENTRY(jint, MHI_getMembers(JNIEnv *env, jobject igcls,
                               jclass clazz_jh, jstring name_jh, jstring sig_jh,
                               int mflags, jclass caller_jh, jint skip, jobjectArray results_jh)) {
  if (clazz_jh == NULL || results_jh == NULL)  return -1;
  klassOop k_oop = java_lang_Class::as_klassOop(JNIHandles::resolve_non_null(clazz_jh));

  objArrayOop results = (objArrayOop) JNIHandles::resolve(results_jh);
  if (results == NULL || !results->is_objArray())       return -1;

  symbolOop name = NULL, sig = NULL;
  if (name_jh != NULL) {
    name = java_lang_String::as_symbol_or_null(JNIHandles::resolve_non_null(name_jh));
    if (name == NULL)  return 0; // a match is not possible
  }
  if (sig_jh != NULL) {
    sig = java_lang_String::as_symbol_or_null(JNIHandles::resolve_non_null(sig_jh));
    if (sig == NULL)  return 0; // a match is not possible
  }

  klassOop caller = NULL;
  if (caller_jh != NULL) {
    oop caller_oop = JNIHandles::resolve_non_null(caller_jh);
    if (!java_lang_Class::is_instance(caller_oop))  return -1;
    caller = java_lang_Class::as_klassOop(caller_oop);
  }

  if (name != NULL && sig != NULL && results != NULL) {
    // try a direct resolve
    // %%% TO DO
  }

  int res = MethodHandles::find_MemberNames(k_oop, name, sig, mflags,
                                            caller, skip, results);
  // TO DO: expand at least some of the MemberNames, to avoid massive callbacks
  return res;
}
JVM_END


JVM_ENTRY(void, MH_linkCallSite(JNIEnv *env, jobject igcls, jobject site_jh, jobject target_jh)) {
  // No special action required, yet.
  oop site_oop = JNIHandles::resolve(site_jh);
  if (site_oop == NULL || site_oop->klass() != SystemDictionary::CallSite_klass())
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "call site");
  java_dyn_CallSite::set_target(site_oop, JNIHandles::resolve(target_jh));
}
JVM_END


/// JVM_RegisterMethodHandleMethods

#define ADR "J"

#define LANG "Ljava/lang/"
#define JDYN "Ljava/dyn/"
#define IDYN "Lsun/dyn/"

#define OBJ   LANG"Object;"
#define CLS   LANG"Class;"
#define STRG  LANG"String;"
#define CST   JDYN"CallSite;"
#define MT    JDYN"MethodType;"
#define MH    JDYN"MethodHandle;"
#define MHI   IDYN"MethodHandleImpl;"
#define MEM   IDYN"MemberName;"
#define AMH   IDYN"AdapterMethodHandle;"
#define BMH   IDYN"BoundMethodHandle;"
#define DMH   IDYN"DirectMethodHandle;"

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

// These are the native methods on sun.dyn.MethodHandleNatives.
static JNINativeMethod methods[] = {
  // void init(MemberName self, AccessibleObject ref)
  {CC"init",                    CC"("AMH""MH"I)V",              FN_PTR(MHI_init_AMH)},
  {CC"init",                    CC"("BMH""OBJ"I)V",             FN_PTR(MHI_init_BMH)},
  {CC"init",                    CC"("DMH""OBJ"Z"CLS")V",        FN_PTR(MHI_init_DMH)},
  {CC"init",                    CC"("MT")V",                    FN_PTR(MHI_init_MT)},
  {CC"init",                    CC"("MEM""OBJ")V",              FN_PTR(MHI_init_Mem)},
  {CC"expand",                  CC"("MEM")V",                   FN_PTR(MHI_expand_Mem)},
  {CC"resolve",                 CC"("MEM""CLS")V",              FN_PTR(MHI_resolve_Mem)},
  {CC"getTarget",               CC"("MH"I)"OBJ,                 FN_PTR(MHI_getTarget)},
  {CC"getConstant",             CC"(I)I",                       FN_PTR(MHI_getConstant)},
  //  static native int getNamedCon(int which, Object[] name)
  {CC"getNamedCon",             CC"(I["OBJ")I",                 FN_PTR(MHI_getNamedCon)},
  //  static native int getMembers(Class<?> defc, String matchName, String matchSig,
  //          int matchFlags, Class<?> caller, int skip, MemberName[] results);
  {CC"getMembers",              CC"("CLS""STRG""STRG"I"CLS"I["MEM")I",  FN_PTR(MHI_getMembers)}
};

// More entry points specifically for EnableInvokeDynamic.
static JNINativeMethod methods2[] = {
  {CC"linkCallSite",            CC"("CST MH")V",                FN_PTR(MH_linkCallSite)}
};


// This one function is exported, used by NativeLookup.

JVM_ENTRY(void, JVM_RegisterMethodHandleMethods(JNIEnv *env, jclass MHN_class)) {
  assert(MethodHandles::spot_check_entry_names(), "entry enum is OK");

  // note: this explicit warning-producing stuff will be replaced by auto-detection of the JSR 292 classes

  if (!EnableMethodHandles) {
    warning("JSR 292 method handles are disabled in this JVM.  Use -XX:+UnlockExperimentalVMOptions -XX:+EnableMethodHandles to enable.");
    return;  // bind nothing
  }

  bool enable_MH = true;

  {
    ThreadToNativeFromVM ttnfv(thread);

    int status = env->RegisterNatives(MHN_class, methods, sizeof(methods)/sizeof(JNINativeMethod));
    if (env->ExceptionOccurred()) {
      MethodHandles::set_enabled(false);
      warning("JSR 292 method handle code is mismatched to this JVM.  Disabling support.");
      enable_MH = false;
      env->ExceptionClear();
    }
  }

  if (enable_MH) {
    KlassHandle MHI_klass = SystemDictionaryHandles::MethodHandleImpl_klass();
    if (MHI_klass.not_null()) {
      symbolHandle raiseException_name = oopFactory::new_symbol_handle("raiseException", CHECK);
      symbolHandle raiseException_sig  = oopFactory::new_symbol_handle("(ILjava/lang/Object;Ljava/lang/Object;)V", CHECK);
      methodOop raiseException_method  = instanceKlass::cast(MHI_klass->as_klassOop())
                    ->find_method(raiseException_name(), raiseException_sig());
      if (raiseException_method != NULL && raiseException_method->is_static()) {
        MethodHandles::set_raise_exception_method(raiseException_method);
      } else {
        warning("JSR 292 method handle code is mismatched to this JVM.  Disabling support.");
        enable_MH = false;
      }
    }
  }

  if (enable_MH) {
    MethodHandles::set_enabled(true);
  }

  if (!EnableInvokeDynamic) {
    warning("JSR 292 invokedynamic is disabled in this JVM.  Use -XX:+UnlockExperimentalVMOptions -XX:+EnableInvokeDynamic to enable.");
    return;  // bind nothing
  }

  {
    ThreadToNativeFromVM ttnfv(thread);

    int status = env->RegisterNatives(MHN_class, methods2, sizeof(methods2)/sizeof(JNINativeMethod));
    if (env->ExceptionOccurred()) {
      MethodHandles::set_enabled(false);
      warning("JSR 292 method handle code is mismatched to this JVM.  Disabling support.");
      env->ExceptionClear();
    } else {
      MethodHandles::set_enabled(true);
    }
  }
}
JVM_END
