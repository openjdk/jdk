/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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

/*
 * JSR 292 reference implementation: method handle structure analysis
 */

#include "incls/_precompiled.incl"
#include "incls/_methodHandleWalk.cpp.incl"


// -----------------------------------------------------------------------------
// MethodHandleChain

void MethodHandleChain::set_method_handle(Handle mh, TRAPS) {
  if (!java_dyn_MethodHandle::is_instance(mh()))  lose("bad method handle", CHECK);

  // set current method handle and unpack partially
  _method_handle = mh;
  _is_last       = false;
  _is_bound      = false;
  _arg_slot      = -1;
  _arg_type      = T_VOID;
  _conversion    = -1;
  _last_invoke   = Bytecodes::_nop;  //arbitrary non-garbage

  if (sun_dyn_DirectMethodHandle::is_instance(mh())) {
    set_last_method(mh(), THREAD);
    return;
  }
  if (sun_dyn_AdapterMethodHandle::is_instance(mh())) {
    _conversion = AdapterMethodHandle_conversion();
    assert(_conversion != -1, "bad conv value");
    assert(sun_dyn_BoundMethodHandle::is_instance(mh()), "also BMH");
  }
  if (sun_dyn_BoundMethodHandle::is_instance(mh())) {
    if (!is_adapter())          // keep AMH and BMH separate in this model
      _is_bound = true;
    _arg_slot = BoundMethodHandle_vmargslot();
    oop target = MethodHandle_vmtarget_oop();
    if (!is_bound() || java_dyn_MethodHandle::is_instance(target)) {
      _arg_type = compute_bound_arg_type(target, NULL, _arg_slot, CHECK);
    } else if (target != NULL && target->is_method()) {
      methodOop m = (methodOop) target;
      _arg_type = compute_bound_arg_type(NULL, m, _arg_slot, CHECK);
      set_last_method(mh(), CHECK);
    } else {
      _is_bound = false;  // lose!
    }
  }
  if (is_bound() && _arg_type == T_VOID) {
    lose("bad vmargslot", CHECK);
  }
  if (!is_bound() && !is_adapter()) {
    lose("unrecognized MH type", CHECK);
  }
}


void MethodHandleChain::set_last_method(oop target, TRAPS) {
  _is_last = true;
  klassOop receiver_limit_oop = NULL;
  int flags = 0;
  methodOop m = MethodHandles::decode_method(target, receiver_limit_oop, flags);
  _last_method = methodHandle(THREAD, m);
  if ((flags & MethodHandles::_dmf_has_receiver) == 0)
    _last_invoke = Bytecodes::_invokestatic;
  else if ((flags & MethodHandles::_dmf_does_dispatch) == 0)
    _last_invoke = Bytecodes::_invokespecial;
  else if ((flags & MethodHandles::_dmf_from_interface) != 0)
    _last_invoke = Bytecodes::_invokeinterface;
  else
    _last_invoke = Bytecodes::_invokevirtual;
}


BasicType MethodHandleChain::compute_bound_arg_type(oop target, methodOop m, int arg_slot, TRAPS) {
  // There is no direct indication of whether the argument is primitive or not.
  // It is implied by the _vmentry code, and by the MethodType of the target.
  // FIXME: Make it explicit MethodHandleImpl refactors out from MethodHandle
  BasicType arg_type = T_VOID;
  if (target != NULL) {
    oop mtype = java_dyn_MethodHandle::type(target);
    int arg_num = MethodHandles::argument_slot_to_argnum(mtype, arg_slot);
    if (arg_num >= 0) {
      oop ptype = java_dyn_MethodType::ptype(mtype, arg_num);
      arg_type = java_lang_Class::as_BasicType(ptype);
    }
  } else if (m != NULL) {
    // figure out the argument type from the slot
    // FIXME: make this explicit in the MH
    int cur_slot = m->size_of_parameters();
    if (arg_slot >= cur_slot)
      return T_VOID;
    if (!m->is_static()) {
      cur_slot -= type2size[T_OBJECT];
      if (cur_slot == arg_slot)
        return T_OBJECT;
    }
    for (SignatureStream ss(m->signature()); !ss.is_done(); ss.next()) {
      BasicType bt = ss.type();
      cur_slot -= type2size[bt];
      if (cur_slot <= arg_slot) {
        if (cur_slot == arg_slot)
          arg_type = bt;
        break;
      }
    }
  }
  if (arg_type == T_ARRAY)
    arg_type = T_OBJECT;
  return arg_type;
}


void MethodHandleChain::lose(const char* msg, TRAPS) {
  assert(false, "lose");
  _lose_message = msg;
  if (!THREAD->is_Java_thread() || ((JavaThread*)THREAD)->thread_state() != _thread_in_vm) {
    // throw a preallocated exception
    THROW_OOP(Universe::virtual_machine_error_instance());
  }
  THROW_MSG(vmSymbols::java_lang_InternalError(), msg);
}


// -----------------------------------------------------------------------------
// MethodHandleWalker

Bytecodes::Code MethodHandleWalker::conversion_code(BasicType src, BasicType dest) {
  if (is_subword_type(src)) {
    src = T_INT;          // all subword src types act like int
  }
  if (src == dest) {
    return Bytecodes::_nop;
  }

#define SRC_DEST(s,d) (((int)(s) << 4) + (int)(d))
  switch (SRC_DEST(src, dest)) {
  case SRC_DEST(T_INT, T_LONG):           return Bytecodes::_i2l;
  case SRC_DEST(T_INT, T_FLOAT):          return Bytecodes::_i2f;
  case SRC_DEST(T_INT, T_DOUBLE):         return Bytecodes::_i2d;
  case SRC_DEST(T_INT, T_BYTE):           return Bytecodes::_i2b;
  case SRC_DEST(T_INT, T_CHAR):           return Bytecodes::_i2c;
  case SRC_DEST(T_INT, T_SHORT):          return Bytecodes::_i2s;

  case SRC_DEST(T_LONG, T_INT):           return Bytecodes::_l2i;
  case SRC_DEST(T_LONG, T_FLOAT):         return Bytecodes::_l2f;
  case SRC_DEST(T_LONG, T_DOUBLE):        return Bytecodes::_l2d;

  case SRC_DEST(T_FLOAT, T_INT):          return Bytecodes::_f2i;
  case SRC_DEST(T_FLOAT, T_LONG):         return Bytecodes::_f2l;
  case SRC_DEST(T_FLOAT, T_DOUBLE):       return Bytecodes::_f2d;

  case SRC_DEST(T_DOUBLE, T_INT):         return Bytecodes::_d2i;
  case SRC_DEST(T_DOUBLE, T_LONG):        return Bytecodes::_d2l;
  case SRC_DEST(T_DOUBLE, T_FLOAT):       return Bytecodes::_d2f;
  }
#undef SRC_DEST

  // cannot do it in one step, or at all
  return Bytecodes::_illegal;
}


// -----------------------------------------------------------------------------
// MethodHandleWalker::walk
//
MethodHandleWalker::ArgToken
MethodHandleWalker::walk(TRAPS) {
  ArgToken empty = ArgToken();  // Empty return value.

  walk_incoming_state(CHECK_(empty));

  for (;;) {
    set_method_handle(chain().method_handle_oop());

    assert(_outgoing_argc == argument_count_slow(), "empty slots under control");

    if (chain().is_adapter()) {
      int conv_op = chain().adapter_conversion_op();
      int arg_slot = chain().adapter_arg_slot();
      SlotState* arg_state = slot_state(arg_slot);
      if (arg_state == NULL
          && conv_op > sun_dyn_AdapterMethodHandle::OP_RETYPE_RAW) {
        lose("bad argument index", CHECK_(empty));
      }

      // perform the adapter action
      switch (chain().adapter_conversion_op()) {
      case sun_dyn_AdapterMethodHandle::OP_RETYPE_ONLY:
        // No changes to arguments; pass the bits through.
        break;

      case sun_dyn_AdapterMethodHandle::OP_RETYPE_RAW: {
        // To keep the verifier happy, emit bitwise ("raw") conversions as needed.
        // See MethodHandles::same_basic_type_for_arguments for allowed conversions.
        Handle incoming_mtype(THREAD, chain().method_type_oop());
        oop outgoing_mh_oop = chain().vmtarget_oop();
        if (!java_dyn_MethodHandle::is_instance(outgoing_mh_oop))
          lose("outgoing target not a MethodHandle", CHECK_(empty));
        Handle outgoing_mtype(THREAD, java_dyn_MethodHandle::type(outgoing_mh_oop));
        outgoing_mh_oop = NULL;  // GC safety

        int nptypes = java_dyn_MethodType::ptype_count(outgoing_mtype());
        if (nptypes != java_dyn_MethodType::ptype_count(incoming_mtype()))
          lose("incoming and outgoing parameter count do not agree", CHECK_(empty));

        for (int i = 0, slot = _outgoing.length() - 1; slot >= 0; slot--) {
          SlotState* arg_state = slot_state(slot);
          if (arg_state->_type == T_VOID)  continue;
          ArgToken arg = _outgoing.at(slot)._arg;

          klassOop  in_klass  = NULL;
          klassOop  out_klass = NULL;
          BasicType inpbt  = java_lang_Class::as_BasicType(java_dyn_MethodType::ptype(incoming_mtype(), i), &in_klass);
          BasicType outpbt = java_lang_Class::as_BasicType(java_dyn_MethodType::ptype(outgoing_mtype(), i), &out_klass);
          assert(inpbt == arg.basic_type(), "sanity");

          if (inpbt != outpbt) {
            vmIntrinsics::ID iid = vmIntrinsics::for_raw_conversion(inpbt, outpbt);
            if (iid == vmIntrinsics::_none) {
              lose("no raw conversion method", CHECK_(empty));
            }
            ArgToken arglist[2];
            arglist[0] = arg;         // outgoing 'this'
            arglist[1] = ArgToken();  // sentinel
            arg = make_invoke(NULL, iid, Bytecodes::_invokestatic, false, 1, &arglist[0], CHECK_(empty));
            change_argument(inpbt, slot, outpbt, arg);
          }

          i++;  // We need to skip void slots at the top of the loop.
        }

        BasicType inrbt  = java_lang_Class::as_BasicType(java_dyn_MethodType::rtype(incoming_mtype()));
        BasicType outrbt = java_lang_Class::as_BasicType(java_dyn_MethodType::rtype(outgoing_mtype()));
        if (inrbt != outrbt) {
          if (inrbt == T_INT && outrbt == T_VOID) {
            // See comments in MethodHandles::same_basic_type_for_arguments.
          } else {
            assert(false, "IMPLEMENT ME");
            lose("no raw conversion method", CHECK_(empty));
          }
        }
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_CHECK_CAST: {
        // checkcast the Nth outgoing argument in place
        klassOop dest_klass = NULL;
        BasicType dest = java_lang_Class::as_BasicType(chain().adapter_arg_oop(), &dest_klass);
        assert(dest == T_OBJECT, "");
        assert(dest == arg_state->_type, "");
        ArgToken arg = arg_state->_arg;
        ArgToken new_arg = make_conversion(T_OBJECT, dest_klass, Bytecodes::_checkcast, arg, CHECK_(empty));
        assert(arg.index() == new_arg.index(), "should be the same index");
        debug_only(dest_klass = (klassOop)badOop);
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_PRIM_TO_PRIM: {
        // i2l, etc., on the Nth outgoing argument in place
        BasicType src = chain().adapter_conversion_src_type(),
                  dest = chain().adapter_conversion_dest_type();
        Bytecodes::Code bc = conversion_code(src, dest);
        ArgToken arg = arg_state->_arg;
        if (bc == Bytecodes::_nop) {
          break;
        } else if (bc != Bytecodes::_illegal) {
          arg = make_conversion(dest, NULL, bc, arg, CHECK_(empty));
        } else if (is_subword_type(dest)) {
          bc = conversion_code(src, T_INT);
          if (bc != Bytecodes::_illegal) {
            arg = make_conversion(dest, NULL, bc, arg, CHECK_(empty));
            bc = conversion_code(T_INT, dest);
            arg = make_conversion(dest, NULL, bc, arg, CHECK_(empty));
          }
        }
        if (bc == Bytecodes::_illegal) {
          lose("bad primitive conversion", CHECK_(empty));
        }
        change_argument(src, arg_slot, dest, arg);
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_REF_TO_PRIM: {
        // checkcast to wrapper type & call intValue, etc.
        BasicType dest = chain().adapter_conversion_dest_type();
        ArgToken arg = arg_state->_arg;
        arg = make_conversion(T_OBJECT, SystemDictionary::box_klass(dest),
                              Bytecodes::_checkcast, arg, CHECK_(empty));
        vmIntrinsics::ID unboxer = vmIntrinsics::for_unboxing(dest);
        if (unboxer == vmIntrinsics::_none) {
          lose("no unboxing method", CHECK_(empty));
        }
        ArgToken arglist[2];
        arglist[0] = arg;         // outgoing 'this'
        arglist[1] = ArgToken();  // sentinel
        arg = make_invoke(NULL, unboxer, Bytecodes::_invokevirtual, false, 1, &arglist[0], CHECK_(empty));
        change_argument(T_OBJECT, arg_slot, dest, arg);
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_PRIM_TO_REF: {
        // call wrapper type.valueOf
        BasicType src = chain().adapter_conversion_src_type();
        ArgToken arg = arg_state->_arg;
        vmIntrinsics::ID boxer = vmIntrinsics::for_boxing(src);
        if (boxer == vmIntrinsics::_none) {
          lose("no boxing method", CHECK_(empty));
        }
        ArgToken arglist[2];
        arglist[0] = arg;         // outgoing value
        arglist[1] = ArgToken();  // sentinel
        assert(false, "I think the argument count must be 1 instead of 0");
        arg = make_invoke(NULL, boxer, Bytecodes::_invokevirtual, false, 0, &arglist[0], CHECK_(empty));
        change_argument(src, arg_slot, T_OBJECT, arg);
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_SWAP_ARGS: {
        int dest_arg_slot = chain().adapter_conversion_vminfo();
        if (!slot_has_argument(dest_arg_slot)) {
          lose("bad swap index", CHECK_(empty));
        }
        // a simple swap between two arguments
        SlotState* dest_arg_state = slot_state(dest_arg_slot);
        SlotState temp = (*dest_arg_state);
        (*dest_arg_state) = (*arg_state);
        (*arg_state) = temp;
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_ROT_ARGS: {
        int dest_arg_slot = chain().adapter_conversion_vminfo();
        if (!slot_has_argument(dest_arg_slot) || arg_slot == dest_arg_slot) {
          lose("bad rotate index", CHECK_(empty));
        }
        SlotState* dest_arg_state = slot_state(dest_arg_slot);
        // Rotate the source argument (plus following N slots) into the
        // position occupied by the dest argument (plus following N slots).
        int rotate_count = type2size[dest_arg_state->_type];
        // (no other rotate counts are currently supported)
        if (arg_slot < dest_arg_slot) {
          for (int i = 0; i < rotate_count; i++) {
            SlotState temp = _outgoing.at(arg_slot);
            _outgoing.remove_at(arg_slot);
            _outgoing.insert_before(dest_arg_slot + rotate_count - 1, temp);
          }
        } else { // arg_slot > dest_arg_slot
          for (int i = 0; i < rotate_count; i++) {
            SlotState temp = _outgoing.at(arg_slot + rotate_count - 1);
            _outgoing.remove_at(arg_slot + rotate_count - 1);
            _outgoing.insert_before(dest_arg_slot, temp);
          }
        }
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_DUP_ARGS: {
        int dup_slots = chain().adapter_conversion_stack_pushes();
        if (dup_slots <= 0) {
          lose("bad dup count", CHECK_(empty));
        }
        for (int i = 0; i < dup_slots; i++) {
          SlotState* dup = slot_state(arg_slot + 2*i);
          if (dup == NULL)              break;  // safety net
          if (dup->_type != T_VOID)     _outgoing_argc += 1;
          _outgoing.insert_before(i, (*dup));
        }
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_DROP_ARGS: {
        int drop_slots = -chain().adapter_conversion_stack_pushes();
        if (drop_slots <= 0) {
          lose("bad drop count", CHECK_(empty));
        }
        for (int i = 0; i < drop_slots; i++) {
          SlotState* drop = slot_state(arg_slot);
          if (drop == NULL)             break;  // safety net
          if (drop->_type != T_VOID)    _outgoing_argc -= 1;
          _outgoing.remove_at(arg_slot);
        }
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_COLLECT_ARGS: { //NYI, may GC
        lose("unimplemented", CHECK_(empty));
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_SPREAD_ARGS: {
        klassOop array_klass_oop = NULL;
        BasicType array_type = java_lang_Class::as_BasicType(chain().adapter_arg_oop(),
                                                             &array_klass_oop);
        assert(array_type == T_OBJECT, "");
        assert(Klass::cast(array_klass_oop)->oop_is_array(), "");
        arrayKlassHandle array_klass(THREAD, array_klass_oop);
        debug_only(array_klass_oop = (klassOop)badOop);

        klassOop element_klass_oop = NULL;
        BasicType element_type = java_lang_Class::as_BasicType(array_klass->component_mirror(),
                                                               &element_klass_oop);
        KlassHandle element_klass(THREAD, element_klass_oop);
        debug_only(element_klass_oop = (klassOop)badOop);

        // Fetch the argument, which we will cast to the required array type.
        assert(arg_state->_type == T_OBJECT, "");
        ArgToken array_arg = arg_state->_arg;
        array_arg = make_conversion(T_OBJECT, array_klass(), Bytecodes::_checkcast, array_arg, CHECK_(empty));
        change_argument(T_OBJECT, arg_slot, T_VOID, ArgToken(tt_void));

        // Check the required length.
        int spread_slots = 1 + chain().adapter_conversion_stack_pushes();
        int spread_length = spread_slots;
        if (type2size[element_type] == 2) {
          if (spread_slots % 2 != 0)  spread_slots = -1;  // force error
          spread_length = spread_slots / 2;
        }
        if (spread_slots < 0) {
          lose("bad spread length", CHECK_(empty));
        }

        jvalue   length_jvalue;  length_jvalue.i = spread_length;
        ArgToken length_arg = make_prim_constant(T_INT, &length_jvalue, CHECK_(empty));
        // Call a built-in method known to the JVM to validate the length.
        ArgToken arglist[3];
        arglist[0] = array_arg;   // value to check
        arglist[1] = length_arg;  // length to check
        arglist[2] = ArgToken();  // sentinel
        make_invoke(NULL, vmIntrinsics::_checkSpreadArgument,
                    Bytecodes::_invokestatic, false, 3, &arglist[0], CHECK_(empty));

        // Spread out the array elements.
        Bytecodes::Code aload_op = Bytecodes::_aaload;
        if (element_type != T_OBJECT) {
          lose("primitive array NYI", CHECK_(empty));
        }
        int ap = arg_slot;
        for (int i = 0; i < spread_length; i++) {
          jvalue   offset_jvalue;  offset_jvalue.i = i;
          ArgToken offset_arg = make_prim_constant(T_INT, &offset_jvalue, CHECK_(empty));
          ArgToken element_arg = make_fetch(element_type, element_klass(), aload_op, array_arg, offset_arg, CHECK_(empty));
          change_argument(T_VOID, ap, element_type, element_arg);
          ap += type2size[element_type];
        }
        break;
      }

      case sun_dyn_AdapterMethodHandle::OP_FLYBY: //NYI, runs Java code
      case sun_dyn_AdapterMethodHandle::OP_RICOCHET: //NYI, runs Java code
        lose("unimplemented", CHECK_(empty));
        break;

      default:
        lose("bad adapter conversion", CHECK_(empty));
        break;
      }
    }

    if (chain().is_bound()) {
      // push a new argument
      BasicType arg_type  = chain().bound_arg_type();
      jint      arg_slot  = chain().bound_arg_slot();
      oop       arg_oop   = chain().bound_arg_oop();
      ArgToken  arg;
      if (arg_type == T_OBJECT) {
        arg = make_oop_constant(arg_oop, CHECK_(empty));
      } else {
        jvalue arg_value;
        BasicType bt = java_lang_boxing_object::get_value(arg_oop, &arg_value);
        if (bt == arg_type) {
          arg = make_prim_constant(arg_type, &arg_value, CHECK_(empty));
        } else {
          lose("bad bound value", CHECK_(empty));
        }
      }
      debug_only(arg_oop = badOop);
      change_argument(T_VOID, arg_slot, arg_type, arg);
    }

    // this test must come after the body of the loop
    if (!chain().is_last()) {
      chain().next(CHECK_(empty));
    } else {
      break;
    }
  }

  // finish the sequence with a tail-call to the ultimate target
  // parameters are passed in logical order (recv 1st), not slot order
  ArgToken* arglist = NEW_RESOURCE_ARRAY(ArgToken, _outgoing.length() + 1);
  int ap = 0;
  for (int i = _outgoing.length() - 1; i >= 0; i--) {
    SlotState* arg_state = slot_state(i);
    if (arg_state->_type == T_VOID)  continue;
    arglist[ap++] = _outgoing.at(i)._arg;
  }
  assert(ap == _outgoing_argc, "");
  arglist[ap] = ArgToken();  // add a sentinel, for the sake of asserts
  return make_invoke(chain().last_method_oop(),
                     vmIntrinsics::_none,
                     chain().last_invoke_code(), true,
                     ap, arglist, THREAD);
}


// -----------------------------------------------------------------------------
// MethodHandleWalker::walk_incoming_state
//
void MethodHandleWalker::walk_incoming_state(TRAPS) {
  Handle mtype(THREAD, chain().method_type_oop());
  int nptypes = java_dyn_MethodType::ptype_count(mtype());
  _outgoing_argc = nptypes;
  int argp = nptypes - 1;
  if (argp >= 0) {
    _outgoing.at_grow(argp, make_state(T_VOID, ArgToken(tt_void))); // presize
  }
  for (int i = 0; i < nptypes; i++) {
    klassOop  arg_type_klass = NULL;
    BasicType arg_type = java_lang_Class::as_BasicType(
                java_dyn_MethodType::ptype(mtype(), i), &arg_type_klass);
    int index = new_local_index(arg_type);
    ArgToken arg = make_parameter(arg_type, arg_type_klass, index, CHECK);
    debug_only(arg_type_klass = (klassOop) NULL);
    _outgoing.at_put(argp, make_state(arg_type, arg));
    if (type2size[arg_type] == 2) {
      // add the extra slot, so we can model the JVM stack
      _outgoing.insert_before(argp+1, make_state(T_VOID, ArgToken(tt_void)));
    }
    --argp;
  }
  // call make_parameter at the end of the list for the return type
  klassOop  ret_type_klass = NULL;
  BasicType ret_type = java_lang_Class::as_BasicType(
              java_dyn_MethodType::rtype(mtype()), &ret_type_klass);
  ArgToken  ret = make_parameter(ret_type, ret_type_klass, -1, CHECK);
  // ignore ret; client can catch it if needed
}


// -----------------------------------------------------------------------------
// MethodHandleWalker::change_argument
//
// This is messy because some kinds of arguments are paired with
// companion slots containing an empty value.
void MethodHandleWalker::change_argument(BasicType old_type, int slot, BasicType new_type,
                                         const ArgToken& new_arg) {
  int old_size = type2size[old_type];
  int new_size = type2size[new_type];
  if (old_size == new_size) {
    // simple case first
    _outgoing.at_put(slot, make_state(new_type, new_arg));
  } else if (old_size > new_size) {
    for (int i = old_size - 1; i >= new_size; i--) {
      assert((i != 0) == (_outgoing.at(slot + i)._type == T_VOID), "");
      _outgoing.remove_at(slot + i);
    }
    if (new_size > 0)
      _outgoing.at_put(slot, make_state(new_type, new_arg));
    else
      _outgoing_argc -= 1;      // deleted a real argument
  } else {
    for (int i = old_size; i < new_size; i++) {
      _outgoing.insert_before(slot + i, make_state(T_VOID, ArgToken(tt_void)));
    }
    _outgoing.at_put(slot, make_state(new_type, new_arg));
    if (old_size == 0)
      _outgoing_argc += 1;      // inserted a real argument
  }
}


#ifdef ASSERT
int MethodHandleWalker::argument_count_slow() {
  int args_seen = 0;
  for (int i = _outgoing.length() - 1; i >= 0; i--) {
    if (_outgoing.at(i)._type != T_VOID) {
      ++args_seen;
    }
  }
  return args_seen;
}
#endif


// -----------------------------------------------------------------------------
// MethodHandleCompiler

MethodHandleCompiler::MethodHandleCompiler(Handle root, methodHandle callee, bool is_invokedynamic, TRAPS)
  : MethodHandleWalker(root, is_invokedynamic, THREAD),
    _callee(callee),
    _thread(THREAD),
    _bytecode(THREAD, 50),
    _constants(THREAD, 10),
    _cur_stack(0),
    _max_stack(0),
    _rtype(T_ILLEGAL)
{

  // Element zero is always the null constant.
  (void) _constants.append(NULL);

  // Set name and signature index.
  _name_index      = cpool_symbol_put(_callee->name());
  _signature_index = cpool_symbol_put(_callee->signature());

  // Get return type klass.
  Handle first_mtype(THREAD, chain().method_type_oop());
  // _rklass is NULL for primitives.
  _rtype = java_lang_Class::as_BasicType(java_dyn_MethodType::rtype(first_mtype()), &_rklass);
  if (_rtype == T_ARRAY)  _rtype = T_OBJECT;

  int params = _callee->size_of_parameters();  // Incoming arguments plus receiver.
  _num_params = for_invokedynamic() ? params - 1 : params;  // XXX Check if callee is static?
}


// -----------------------------------------------------------------------------
// MethodHandleCompiler::compile
//
// Compile this MethodHandle into a bytecode adapter and return a
// methodOop.
methodHandle MethodHandleCompiler::compile(TRAPS) {
  assert(_thread == THREAD, "must be same thread");
  methodHandle nullHandle;
  (void) walk(CHECK_(nullHandle));
  return get_method_oop(CHECK_(nullHandle));
}


void MethodHandleCompiler::emit_bc(Bytecodes::Code op, int index) {
  Bytecodes::check(op);  // Are we legal?

  switch (op) {
  // b
  case Bytecodes::_aconst_null:
  case Bytecodes::_iconst_m1:
  case Bytecodes::_iconst_0:
  case Bytecodes::_iconst_1:
  case Bytecodes::_iconst_2:
  case Bytecodes::_iconst_3:
  case Bytecodes::_iconst_4:
  case Bytecodes::_iconst_5:
  case Bytecodes::_lconst_0:
  case Bytecodes::_lconst_1:
  case Bytecodes::_fconst_0:
  case Bytecodes::_fconst_1:
  case Bytecodes::_fconst_2:
  case Bytecodes::_dconst_0:
  case Bytecodes::_dconst_1:
  case Bytecodes::_iload_0:
  case Bytecodes::_iload_1:
  case Bytecodes::_iload_2:
  case Bytecodes::_iload_3:
  case Bytecodes::_lload_0:
  case Bytecodes::_lload_1:
  case Bytecodes::_lload_2:
  case Bytecodes::_lload_3:
  case Bytecodes::_fload_0:
  case Bytecodes::_fload_1:
  case Bytecodes::_fload_2:
  case Bytecodes::_fload_3:
  case Bytecodes::_dload_0:
  case Bytecodes::_dload_1:
  case Bytecodes::_dload_2:
  case Bytecodes::_dload_3:
  case Bytecodes::_aload_0:
  case Bytecodes::_aload_1:
  case Bytecodes::_aload_2:
  case Bytecodes::_aload_3:
  case Bytecodes::_istore_0:
  case Bytecodes::_istore_1:
  case Bytecodes::_istore_2:
  case Bytecodes::_istore_3:
  case Bytecodes::_lstore_0:
  case Bytecodes::_lstore_1:
  case Bytecodes::_lstore_2:
  case Bytecodes::_lstore_3:
  case Bytecodes::_fstore_0:
  case Bytecodes::_fstore_1:
  case Bytecodes::_fstore_2:
  case Bytecodes::_fstore_3:
  case Bytecodes::_dstore_0:
  case Bytecodes::_dstore_1:
  case Bytecodes::_dstore_2:
  case Bytecodes::_dstore_3:
  case Bytecodes::_astore_0:
  case Bytecodes::_astore_1:
  case Bytecodes::_astore_2:
  case Bytecodes::_astore_3:
  case Bytecodes::_i2l:
  case Bytecodes::_i2f:
  case Bytecodes::_i2d:
  case Bytecodes::_i2b:
  case Bytecodes::_i2c:
  case Bytecodes::_i2s:
  case Bytecodes::_l2i:
  case Bytecodes::_l2f:
  case Bytecodes::_l2d:
  case Bytecodes::_f2i:
  case Bytecodes::_f2l:
  case Bytecodes::_f2d:
  case Bytecodes::_d2i:
  case Bytecodes::_d2l:
  case Bytecodes::_d2f:
  case Bytecodes::_ireturn:
  case Bytecodes::_lreturn:
  case Bytecodes::_freturn:
  case Bytecodes::_dreturn:
  case Bytecodes::_areturn:
  case Bytecodes::_return:
    assert(Bytecodes::format_bits(op, false) == Bytecodes::_fmt_b, "wrong bytecode format");
    _bytecode.push(op);
    break;

  // bi
  case Bytecodes::_ldc:
    assert(Bytecodes::format_bits(op, false) == (Bytecodes::_fmt_b|Bytecodes::_fmt_has_k), "wrong bytecode format");
    assert((char) index == index, "index does not fit in 8-bit");
    _bytecode.push(op);
    _bytecode.push(index);
    break;

  case Bytecodes::_iload:
  case Bytecodes::_lload:
  case Bytecodes::_fload:
  case Bytecodes::_dload:
  case Bytecodes::_aload:
  case Bytecodes::_istore:
  case Bytecodes::_lstore:
  case Bytecodes::_fstore:
  case Bytecodes::_dstore:
  case Bytecodes::_astore:
    assert(Bytecodes::format_bits(op, false) == Bytecodes::_fmt_bi, "wrong bytecode format");
    assert((char) index == index, "index does not fit in 8-bit");
    _bytecode.push(op);
    _bytecode.push(index);
    break;

  // bkk
  case Bytecodes::_ldc_w:
  case Bytecodes::_ldc2_w:
  case Bytecodes::_checkcast:
    assert(Bytecodes::format_bits(op, false) == Bytecodes::_fmt_bkk, "wrong bytecode format");
    assert((short) index == index, "index does not fit in 16-bit");
    _bytecode.push(op);
    _bytecode.push(index >> 8);
    _bytecode.push(index);
    break;

  // bJJ
  case Bytecodes::_invokestatic:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokevirtual:
    assert(Bytecodes::format_bits(op, false) == Bytecodes::_fmt_bJJ, "wrong bytecode format");
    assert((short) index == index, "index does not fit in 16-bit");
    _bytecode.push(op);
    _bytecode.push(index >> 8);
    _bytecode.push(index);
    break;

  default:
    ShouldNotReachHere();
  }
}


void MethodHandleCompiler::emit_load(BasicType bt, int index) {
  if (index <= 3) {
    switch (bt) {
    case T_BOOLEAN: case T_BYTE: case T_CHAR: case T_SHORT:
    case T_INT:    emit_bc(Bytecodes::cast(Bytecodes::_iload_0 + index)); break;
    case T_LONG:   emit_bc(Bytecodes::cast(Bytecodes::_lload_0 + index)); break;
    case T_FLOAT:  emit_bc(Bytecodes::cast(Bytecodes::_fload_0 + index)); break;
    case T_DOUBLE: emit_bc(Bytecodes::cast(Bytecodes::_dload_0 + index)); break;
    case T_OBJECT: emit_bc(Bytecodes::cast(Bytecodes::_aload_0 + index)); break;
    default:
      ShouldNotReachHere();
    }
  }
  else {
    switch (bt) {
    case T_BOOLEAN: case T_BYTE: case T_CHAR: case T_SHORT:
    case T_INT:    emit_bc(Bytecodes::_iload, index); break;
    case T_LONG:   emit_bc(Bytecodes::_lload, index); break;
    case T_FLOAT:  emit_bc(Bytecodes::_fload, index); break;
    case T_DOUBLE: emit_bc(Bytecodes::_dload, index); break;
    case T_OBJECT: emit_bc(Bytecodes::_aload, index); break;
    default:
      ShouldNotReachHere();
    }
  }
  stack_push(bt);
}

void MethodHandleCompiler::emit_store(BasicType bt, int index) {
  if (index <= 3) {
    switch (bt) {
    case T_BOOLEAN: case T_BYTE: case T_CHAR: case T_SHORT:
    case T_INT:    emit_bc(Bytecodes::cast(Bytecodes::_istore_0 + index)); break;
    case T_LONG:   emit_bc(Bytecodes::cast(Bytecodes::_lstore_0 + index)); break;
    case T_FLOAT:  emit_bc(Bytecodes::cast(Bytecodes::_fstore_0 + index)); break;
    case T_DOUBLE: emit_bc(Bytecodes::cast(Bytecodes::_dstore_0 + index)); break;
    case T_OBJECT: emit_bc(Bytecodes::cast(Bytecodes::_astore_0 + index)); break;
    default:
      ShouldNotReachHere();
    }
  }
  else {
    switch (bt) {
    case T_BOOLEAN: case T_BYTE: case T_CHAR: case T_SHORT:
    case T_INT:    emit_bc(Bytecodes::_istore, index); break;
    case T_LONG:   emit_bc(Bytecodes::_lstore, index); break;
    case T_FLOAT:  emit_bc(Bytecodes::_fstore, index); break;
    case T_DOUBLE: emit_bc(Bytecodes::_dstore, index); break;
    case T_OBJECT: emit_bc(Bytecodes::_astore, index); break;
    default:
      ShouldNotReachHere();
    }
  }
  stack_pop(bt);
}


void MethodHandleCompiler::emit_load_constant(ArgToken arg) {
  BasicType bt = arg.basic_type();
  switch (bt) {
  case T_INT: {
    jint value = arg.get_jint();
    if (-1 <= value && value <= 5)
      emit_bc(Bytecodes::cast(Bytecodes::_iconst_0 + value));
    else
      emit_bc(Bytecodes::_ldc, cpool_int_put(value));
    break;
  }
  case T_LONG: {
    jlong value = arg.get_jlong();
    if (0 <= value && value <= 1)
      emit_bc(Bytecodes::cast(Bytecodes::_lconst_0 + (int) value));
    else
      emit_bc(Bytecodes::_ldc2_w, cpool_long_put(value));
    break;
  }
  case T_FLOAT: {
    jfloat value  = arg.get_jfloat();
    if (value == 0.0 || value == 1.0 || value == 2.0)
      emit_bc(Bytecodes::cast(Bytecodes::_fconst_0 + (int) value));
    else
      emit_bc(Bytecodes::_ldc, cpool_float_put(value));
    break;
  }
  case T_DOUBLE: {
    jdouble value = arg.get_jdouble();
    if (value == 0.0 || value == 1.0)
      emit_bc(Bytecodes::cast(Bytecodes::_dconst_0 + (int) value));
    else
      emit_bc(Bytecodes::_ldc2_w, cpool_double_put(value));
    break;
  }
  case T_OBJECT: {
    Handle value = arg.object();
    if (value.is_null())
      emit_bc(Bytecodes::_aconst_null);
    else
      emit_bc(Bytecodes::_ldc, cpool_object_put(value));
    break;
  }
  default:
    ShouldNotReachHere();
  }
  stack_push(bt);
}


MethodHandleWalker::ArgToken
MethodHandleCompiler::make_conversion(BasicType type, klassOop tk, Bytecodes::Code op,
                                      const ArgToken& src, TRAPS) {

  BasicType srctype = src.basic_type();
  int index = src.index();

  switch (op) {
  case Bytecodes::_i2l:
  case Bytecodes::_i2f:
  case Bytecodes::_i2d:
  case Bytecodes::_i2b:
  case Bytecodes::_i2c:
  case Bytecodes::_i2s:

  case Bytecodes::_l2i:
  case Bytecodes::_l2f:
  case Bytecodes::_l2d:

  case Bytecodes::_f2i:
  case Bytecodes::_f2l:
  case Bytecodes::_f2d:

  case Bytecodes::_d2i:
  case Bytecodes::_d2l:
  case Bytecodes::_d2f:
    emit_load(srctype, index);
    stack_pop(srctype);  // pop the src type
    emit_bc(op);
    stack_push(type);    // push the dest value
    if (srctype != type)
      index = new_local_index(type);
    emit_store(type, index);
    break;

  case Bytecodes::_checkcast:
    emit_load(srctype, index);
    emit_bc(op, cpool_klass_put(tk));
    emit_store(srctype, index);
    break;

  default:
    ShouldNotReachHere();
  }

  return make_parameter(type, tk, index, THREAD);
}


// -----------------------------------------------------------------------------
// MethodHandleCompiler
//

static jvalue zero_jvalue;

// Emit bytecodes for the given invoke instruction.
MethodHandleWalker::ArgToken
MethodHandleCompiler::make_invoke(methodOop m, vmIntrinsics::ID iid,
                                  Bytecodes::Code op, bool tailcall,
                                  int argc, MethodHandleWalker::ArgToken* argv,
                                  TRAPS) {
  if (m == NULL) {
    // Get the intrinsic methodOop.
    m = vmIntrinsics::method_for(iid);
  }

  klassOop  klass     = m->method_holder();
  symbolOop name      = m->name();
  symbolOop signature = m->signature();

  if (tailcall) {
    // Actually, in order to make these methods more recognizable,
    // let's put them in holder classes MethodHandle and InvokeDynamic.
    // That way stack walkers and compiler heuristics can recognize them.
    _target_klass = (for_invokedynamic()
                     ? SystemDictionary::InvokeDynamic_klass()
                     : SystemDictionary::MethodHandle_klass());
  }

  // instanceKlass* ik = instanceKlass::cast(klass);
  // tty->print_cr("MethodHandleCompiler::make_invoke: %s %s.%s%s", Bytecodes::name(op), ik->external_name(), name->as_C_string(), signature->as_C_string());

  // Inline the method.
  InvocationCounter* ic = m->invocation_counter();
  ic->set_carry();

  for (int i = 0; i < argc; i++) {
    ArgToken arg = argv[i];
    TokenType tt = arg.token_type();
    BasicType bt = arg.basic_type();

    switch (tt) {
    case tt_parameter:
    case tt_temporary:
      emit_load(bt, arg.index());
      break;
    case tt_constant:
      emit_load_constant(arg);
      break;
    case tt_illegal:
      // Sentinel.
      assert(i == (argc - 1), "sentinel must be last entry");
      break;
    case tt_void:
    default:
      ShouldNotReachHere();
    }
  }

  // Populate constant pool.
  int name_index          = cpool_symbol_put(name);
  int signature_index     = cpool_symbol_put(signature);
  int name_and_type_index = cpool_name_and_type_put(name_index, signature_index);
  int klass_index         = cpool_klass_put(klass);
  int methodref_index     = cpool_methodref_put(klass_index, name_and_type_index);

  // Generate invoke.
  switch (op) {
  case Bytecodes::_invokestatic:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokevirtual:
    emit_bc(op, methodref_index);
    break;
  case Bytecodes::_invokeinterface:
    Unimplemented();
    break;
  default:
    ShouldNotReachHere();
  }

  // If tailcall, we have walked all the way to a direct method handle.
  // Otherwise, make a recursive call to some helper routine.
  BasicType rbt = m->result_type();
  if (rbt == T_ARRAY)  rbt = T_OBJECT;
  ArgToken ret;
  if (tailcall) {
    if (rbt != _rtype) {
      if (rbt == T_VOID) {
        // push a zero of the right sort
        ArgToken zero;
        if (_rtype == T_OBJECT) {
          zero = make_oop_constant(NULL, CHECK_(zero));
        } else {
          zero = make_prim_constant(_rtype, &zero_jvalue, CHECK_(zero));
        }
        emit_load_constant(zero);
      } else if (_rtype == T_VOID) {
        // We'll emit a _return with something on the stack.
        // It's OK to ignore what's on the stack.
      } else {
        tty->print_cr("*** rbt=%d != rtype=%d", rbt, _rtype);
        assert(false, "IMPLEMENT ME");
      }
    }
    switch (_rtype) {
    case T_BOOLEAN: case T_BYTE: case T_CHAR: case T_SHORT:
    case T_INT:    emit_bc(Bytecodes::_ireturn); break;
    case T_LONG:   emit_bc(Bytecodes::_lreturn); break;
    case T_FLOAT:  emit_bc(Bytecodes::_freturn); break;
    case T_DOUBLE: emit_bc(Bytecodes::_dreturn); break;
    case T_VOID:   emit_bc(Bytecodes::_return);  break;
    case T_OBJECT:
      if (_rklass.not_null() && _rklass() != SystemDictionary::Object_klass())
        emit_bc(Bytecodes::_checkcast, cpool_klass_put(_rklass()));
      emit_bc(Bytecodes::_areturn);
      break;
    default: ShouldNotReachHere();
    }
    ret = ArgToken();  // Dummy return value.
  }
  else {
    stack_push(rbt);  // The return value is already pushed onto the stack.
    int index = new_local_index(rbt);
    switch (rbt) {
    case T_BOOLEAN: case T_BYTE: case T_CHAR:  case T_SHORT:
    case T_INT:     case T_LONG: case T_FLOAT: case T_DOUBLE:
    case T_OBJECT:
      emit_store(rbt, index);
      ret = ArgToken(tt_temporary, rbt, index);
      break;
    case T_VOID:
      ret = ArgToken(tt_void);
      break;
    default:
      ShouldNotReachHere();
    }
  }

  return ret;
}

MethodHandleWalker::ArgToken
MethodHandleCompiler::make_fetch(BasicType type, klassOop tk, Bytecodes::Code op,
                                 const MethodHandleWalker::ArgToken& base,
                                 const MethodHandleWalker::ArgToken& offset,
                                 TRAPS) {
  Unimplemented();
  return ArgToken();
}


int MethodHandleCompiler::cpool_primitive_put(BasicType bt, jvalue* con) {
  jvalue con_copy;
  assert(bt < T_OBJECT, "");
  if (type2aelembytes(bt) < jintSize) {
    // widen to int
    con_copy = (*con);
    con = &con_copy;
    switch (bt) {
    case T_BOOLEAN: con->i = (con->z ? 1 : 0); break;
    case T_BYTE:    con->i = con->b;           break;
    case T_CHAR:    con->i = con->c;           break;
    case T_SHORT:   con->i = con->s;           break;
    default: ShouldNotReachHere();
    }
    bt = T_INT;
  }

//   for (int i = 1, imax = _constants.length(); i < imax; i++) {
//     ConstantValue* con = _constants.at(i);
//     if (con != NULL && con->is_primitive() && con->_type == bt) {
//       bool match = false;
//       switch (type2size[bt]) {
//       case 1:  if (pcon->_value.i == con->i)  match = true;  break;
//       case 2:  if (pcon->_value.j == con->j)  match = true;  break;
//       }
//       if (match)
//         return i;
//     }
//   }
  ConstantValue* cv = new ConstantValue(bt, *con);
  int index = _constants.append(cv);

  // long and double entries take 2 slots, we add another empty entry.
  if (type2size[bt] == 2)
    (void) _constants.append(NULL);

  return index;
}


constantPoolHandle MethodHandleCompiler::get_constant_pool(TRAPS) const {
  constantPoolHandle nullHandle;
  bool is_conc_safe = true;
  constantPoolOop cpool_oop = oopFactory::new_constantPool(_constants.length(), is_conc_safe, CHECK_(nullHandle));
  constantPoolHandle cpool(THREAD, cpool_oop);

  // Fill the real constant pool skipping the zero element.
  for (int i = 1; i < _constants.length(); i++) {
    ConstantValue* cv = _constants.at(i);
    switch (cv->tag()) {
    case JVM_CONSTANT_Utf8:        cpool->symbol_at_put(       i, cv->symbol_oop()                     ); break;
    case JVM_CONSTANT_Integer:     cpool->int_at_put(          i, cv->get_jint()                       ); break;
    case JVM_CONSTANT_Float:       cpool->float_at_put(        i, cv->get_jfloat()                     ); break;
    case JVM_CONSTANT_Long:        cpool->long_at_put(         i, cv->get_jlong()                      ); break;
    case JVM_CONSTANT_Double:      cpool->double_at_put(       i, cv->get_jdouble()                    ); break;
    case JVM_CONSTANT_Class:       cpool->klass_at_put(        i, cv->klass_oop()                      ); break;
    case JVM_CONSTANT_Methodref:   cpool->method_at_put(       i, cv->first_index(), cv->second_index()); break;
    case JVM_CONSTANT_NameAndType: cpool->name_and_type_at_put(i, cv->first_index(), cv->second_index()); break;
    case JVM_CONSTANT_Object:      cpool->object_at_put(       i, cv->object_oop()                     ); break;
    default: ShouldNotReachHere();
    }

    switch (cv->tag()) {
    case JVM_CONSTANT_Long:
    case JVM_CONSTANT_Double:
      i++;  // Skip empty entry.
      assert(_constants.at(i) == NULL, "empty entry");
      break;
    }
  }

  // Set the constant pool holder to the target method's class.
  cpool->set_pool_holder(_target_klass());

  return cpool;
}


methodHandle MethodHandleCompiler::get_method_oop(TRAPS) const {
  methodHandle nullHandle;
  // Create a method that holds the generated bytecode.  invokedynamic
  // has no receiver, normal MH calls do.
  int flags_bits;
  if (for_invokedynamic())
    flags_bits = (/*JVM_MH_INVOKE_BITS |*/ JVM_ACC_PUBLIC | JVM_ACC_FINAL | JVM_ACC_SYNTHETIC | JVM_ACC_STATIC);
  else
    flags_bits = (/*JVM_MH_INVOKE_BITS |*/ JVM_ACC_PUBLIC | JVM_ACC_FINAL | JVM_ACC_SYNTHETIC);

  bool is_conc_safe = true;
  methodOop m_oop = oopFactory::new_method(bytecode_length(),
                                           accessFlags_from(flags_bits),
                                           0, 0, 0, is_conc_safe, CHECK_(nullHandle));
  methodHandle m(THREAD, m_oop);
  m_oop = NULL;  // oop not GC safe

  constantPoolHandle cpool = get_constant_pool(CHECK_(nullHandle));
  m->set_constants(cpool());

  m->set_name_index(_name_index);
  m->set_signature_index(_signature_index);

  m->set_code((address) bytecode());

  m->set_max_stack(_max_stack);
  m->set_max_locals(max_locals());
  m->set_size_of_parameters(_num_params);

  typeArrayHandle exception_handlers(THREAD, Universe::the_empty_int_array());
  m->set_exception_table(exception_handlers());

  // Set the carry bit of the invocation counter to force inlining of
  // the adapter.
  InvocationCounter* ic = m->invocation_counter();
  ic->set_carry();

  // Rewrite the method and set up the constant pool cache.
  objArrayOop m_array = oopFactory::new_system_objArray(1, CHECK_(nullHandle));
  objArrayHandle methods(THREAD, m_array);
  methods->obj_at_put(0, m());
  Rewriter::rewrite(_target_klass(), cpool, methods, CHECK_(nullHandle));  // Use fake class.

#ifndef PRODUCT
  if (TraceMethodHandles) {
    m->print();
    m->print_codes();
  }
#endif //PRODUCT

  assert(m->is_method_handle_adapter(), "must be recognized as an adapter");
  return m;
}


#ifndef PRODUCT

#if 0
// MH printer for debugging.

class MethodHandlePrinter : public MethodHandleWalker {
private:
  outputStream* _out;
  bool          _verbose;
  int           _temp_num;
  stringStream  _strbuf;
  const char* strbuf() {
    const char* s = _strbuf.as_string();
    _strbuf.reset();
    return s;
  }
  ArgToken token(const char* str) {
    return (ArgToken) str;
  }
  void start_params() {
    _out->print("(");
  }
  void end_params() {
    if (_verbose)  _out->print("\n");
    _out->print(") => {");
  }
  void put_type_name(BasicType type, klassOop tk, outputStream* s) {
    const char* kname = NULL;
    if (tk != NULL)
      kname = Klass::cast(tk)->external_name();
    s->print("%s", (kname != NULL) ? kname : type2name(type));
  }
  ArgToken maybe_make_temp(const char* statement_op, BasicType type, const char* temp_name) {
    const char* value = strbuf();
    if (!_verbose)  return token(value);
    // make an explicit binding for each separate value
    _strbuf.print("%s%d", temp_name, ++_temp_num);
    const char* temp = strbuf();
    _out->print("\n  %s %s %s = %s;", statement_op, type2name(type), temp, value);
    return token(temp);
  }

public:
  MethodHandlePrinter(Handle root, bool verbose, outputStream* out, TRAPS)
    : MethodHandleWalker(root, THREAD),
      _out(out),
      _verbose(verbose),
      _temp_num(0)
  {
    start_params();
  }
  virtual ArgToken make_parameter(BasicType type, klassOop tk, int argnum, TRAPS) {
    if (argnum < 0) {
      end_params();
      return NULL;
    }
    if (argnum == 0) {
      _out->print(_verbose ? "\n  " : "");
    } else {
      _out->print(_verbose ? ",\n  " : ", ");
    }
    if (argnum >= _temp_num)
      _temp_num = argnum;
    // generate an argument name
    _strbuf.print("a%d", argnum);
    const char* arg = strbuf();
    put_type_name(type, tk, _out);
    _out->print(" %s", arg);
    return token(arg);
  }
  virtual ArgToken make_oop_constant(oop con, TRAPS) {
    if (con == NULL)
      _strbuf.print("null");
    else
      con->print_value_on(&_strbuf);
    if (_strbuf.size() == 0) {  // yuck
      _strbuf.print("(a ");
      put_type_name(T_OBJECT, con->klass(), &_strbuf);
      _strbuf.print(")");
    }
    return maybe_make_temp("constant", T_OBJECT, "k");
  }
  virtual ArgToken make_prim_constant(BasicType type, jvalue* con, TRAPS) {
    java_lang_boxing_object::print(type, con, &_strbuf);
    return maybe_make_temp("constant", type, "k");
  }
  virtual ArgToken make_conversion(BasicType type, klassOop tk, Bytecodes::Code op, ArgToken src, TRAPS) {
    _strbuf.print("%s(%s", Bytecodes::name(op), (const char*)src);
    if (tk != NULL) {
      _strbuf.print(", ");
      put_type_name(type, tk, &_strbuf);
    }
    _strbuf.print(")");
    return maybe_make_temp("convert", type, "v");
  }
  virtual ArgToken make_fetch(BasicType type, klassOop tk, Bytecodes::Code op, ArgToken base, ArgToken offset, TRAPS) {
    _strbuf.print("%s(%s, %s", Bytecodes::name(op), (const char*)base, (const char*)offset);
    if (tk != NULL) {
      _strbuf.print(", ");
      put_type_name(type, tk, &_strbuf);
    }
    _strbuf.print(")");
    return maybe_make_temp("fetch", type, "x");
  }
  virtual ArgToken make_invoke(methodOop m, vmIntrinsics::ID iid,
                               Bytecodes::Code op, bool tailcall,
                               int argc, ArgToken* argv, TRAPS) {
    symbolOop name, sig;
    if (m != NULL) {
      name = m->name();
      sig  = m->signature();
    } else {
      name = vmSymbols::symbol_at(vmIntrinsics::name_for(iid));
      sig  = vmSymbols::symbol_at(vmIntrinsics::signature_for(iid));
    }
    _strbuf.print("%s %s%s(", Bytecodes::name(op), name->as_C_string(), sig->as_C_string());
    for (int i = 0; i < argc; i++) {
      _strbuf.print("%s%s", (i > 0 ? ", " : ""), (const char*)argv[i]);
    }
    _strbuf.print(")");
    if (!tailcall) {
      BasicType rt = char2type(sig->byte_at(sig->utf8_length()-1));
      if (rt == T_ILLEGAL)  rt = T_OBJECT;  // ';' at the end of '(...)L...;'
      return maybe_make_temp("invoke", rt, "x");
    } else {
      const char* ret = strbuf();
      _out->print(_verbose ? "\n  return " : " ");
      _out->print("%s", ret);
      _out->print(_verbose ? "\n}\n" : " }");
    }
    return ArgToken();
  }

  virtual void set_method_handle(oop mh) {
    if (WizardMode && Verbose) {
      tty->print("\n--- next target: ");
      mh->print();
    }
  }

  static void print(Handle root, bool verbose, outputStream* out, TRAPS) {
    ResourceMark rm;
    MethodHandlePrinter printer(root, verbose, out, CHECK);
    printer.walk(CHECK);
    out->print("\n");
  }
  static void print(Handle root, bool verbose = Verbose, outputStream* out = tty) {
    EXCEPTION_MARK;
    ResourceMark rm;
    MethodHandlePrinter printer(root, verbose, out, THREAD);
    if (!HAS_PENDING_EXCEPTION)
      printer.walk(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      oop ex = PENDING_EXCEPTION;
      CLEAR_PENDING_EXCEPTION;
      out->print("\n*** ");
      if (ex != Universe::virtual_machine_error_instance())
        ex->print_on(out);
      else
        out->print("lose: %s", printer.lose_message());
      out->print("\n}\n");
    }
    out->print("\n");
  }
};
#endif // 0

extern "C"
void print_method_handle(oop mh) {
  if (java_dyn_MethodHandle::is_instance(mh)) {
    //MethodHandlePrinter::print(mh);
  } else {
    tty->print("*** not a method handle: ");
    mh->print();
  }
}

#endif // PRODUCT
