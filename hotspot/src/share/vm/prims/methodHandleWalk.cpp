/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/rewriter.hpp"
#include "memory/oopFactory.hpp"
#include "prims/methodHandleWalk.hpp"

/*
 * JSR 292 reference implementation: method handle structure analysis
 */

#ifdef PRODUCT
#define print_method_handle(mh) {}
#else //PRODUCT
extern "C" void print_method_handle(oop mh);
#endif //PRODUCT

// -----------------------------------------------------------------------------
// MethodHandleChain

void MethodHandleChain::set_method_handle(Handle mh, TRAPS) {
  if (!java_lang_invoke_MethodHandle::is_instance(mh()))  lose("bad method handle", CHECK);

  // set current method handle and unpack partially
  _method_handle = mh;
  _is_last       = false;
  _is_bound      = false;
  _arg_slot      = -1;
  _arg_type      = T_VOID;
  _conversion    = -1;
  _last_invoke   = Bytecodes::_nop;  //arbitrary non-garbage

  if (java_lang_invoke_DirectMethodHandle::is_instance(mh())) {
    set_last_method(mh(), THREAD);
    return;
  }
  if (java_lang_invoke_AdapterMethodHandle::is_instance(mh())) {
    _conversion = AdapterMethodHandle_conversion();
    assert(_conversion != -1, "bad conv value");
    assert(java_lang_invoke_BoundMethodHandle::is_instance(mh()), "also BMH");
  }
  if (java_lang_invoke_BoundMethodHandle::is_instance(mh())) {
    if (!is_adapter())          // keep AMH and BMH separate in this model
      _is_bound = true;
    _arg_slot = BoundMethodHandle_vmargslot();
    oop target = MethodHandle_vmtarget_oop();
    if (!is_bound() || java_lang_invoke_MethodHandle::is_instance(target)) {
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
  KlassHandle receiver_limit; int flags = 0;
  _last_method = MethodHandles::decode_method(target, receiver_limit, flags);
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
  BasicType arg_type = T_VOID;
  if (target != NULL) {
    oop mtype = java_lang_invoke_MethodHandle::type(target);
    int arg_num = MethodHandles::argument_slot_to_argnum(mtype, arg_slot);
    if (arg_num >= 0) {
      oop ptype = java_lang_invoke_MethodType::ptype(mtype, arg_num);
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
    ResourceMark rm(THREAD);
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
  _lose_message = msg;
#ifdef ASSERT
  if (Verbose) {
    tty->print_cr(INTPTR_FORMAT " lose: %s", _method_handle(), msg);
    print();
  }
#endif
  if (!THREAD->is_Java_thread() || ((JavaThread*)THREAD)->thread_state() != _thread_in_vm) {
    // throw a preallocated exception
    THROW_OOP(Universe::virtual_machine_error_instance());
  }
  THROW_MSG(vmSymbols::java_lang_InternalError(), msg);
}


#ifdef ASSERT
static const char* adapter_ops[] = {
  "retype_only"  ,
  "retype_raw"   ,
  "check_cast"   ,
  "prim_to_prim" ,
  "ref_to_prim"  ,
  "prim_to_ref"  ,
  "swap_args"    ,
  "rot_args"     ,
  "dup_args"     ,
  "drop_args"    ,
  "collect_args" ,
  "spread_args"  ,
  "fold_args"
};

static const char* adapter_op_to_string(int op) {
  if (op >= 0 && op < (int)ARRAY_SIZE(adapter_ops))
    return adapter_ops[op];
  return "unknown_op";
}

void MethodHandleChain::print(oopDesc* m) {
  HandleMark hm;
  ResourceMark rm;
  Handle mh(m);
  print(mh);
}

void MethodHandleChain::print(Handle mh) {
  EXCEPTION_MARK;
  MethodHandleChain mhc(mh, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    oop ex = THREAD->pending_exception();
    CLEAR_PENDING_EXCEPTION;
    ex->print();
    return;
  }
  mhc.print();
}


void MethodHandleChain::print() {
  EXCEPTION_MARK;
  print_impl(THREAD);
  if (HAS_PENDING_EXCEPTION) {
    oop ex = THREAD->pending_exception();
    CLEAR_PENDING_EXCEPTION;
    ex->print();
  }
}

void MethodHandleChain::print_impl(TRAPS) {
  ResourceMark rm;

  MethodHandleChain chain(_root, CHECK);
  for (;;) {
    tty->print(INTPTR_FORMAT ": ", chain.method_handle()());
    if (chain.is_bound()) {
      tty->print("bound: arg_type %s arg_slot %d",
                 type2name(chain.bound_arg_type()),
                 chain.bound_arg_slot());
      oop o = chain.bound_arg_oop();
      if (o != NULL) {
        if (o->is_instance()) {
          tty->print(" instance %s", o->klass()->klass_part()->internal_name());
        } else {
          o->print();
        }
      }
    } else if (chain.is_adapter()) {
      tty->print("adapter: arg_slot %d conversion op %s",
                 chain.adapter_arg_slot(),
                 adapter_op_to_string(chain.adapter_conversion_op()));
      switch (chain.adapter_conversion_op()) {
        case java_lang_invoke_AdapterMethodHandle::OP_RETYPE_ONLY:
        case java_lang_invoke_AdapterMethodHandle::OP_RETYPE_RAW:
        case java_lang_invoke_AdapterMethodHandle::OP_CHECK_CAST:
        case java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_PRIM:
        case java_lang_invoke_AdapterMethodHandle::OP_REF_TO_PRIM:
        case java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_REF:
          break;

        case java_lang_invoke_AdapterMethodHandle::OP_SWAP_ARGS:
        case java_lang_invoke_AdapterMethodHandle::OP_ROT_ARGS: {
          int dest_arg_slot = chain.adapter_conversion_vminfo();
          tty->print(" dest_arg_slot %d type %s", dest_arg_slot, type2name(chain.adapter_conversion_src_type()));
          break;
        }

        case java_lang_invoke_AdapterMethodHandle::OP_DUP_ARGS:
        case java_lang_invoke_AdapterMethodHandle::OP_DROP_ARGS: {
          int dup_slots = chain.adapter_conversion_stack_pushes();
          tty->print(" pushes %d", dup_slots);
          break;
        }

        case java_lang_invoke_AdapterMethodHandle::OP_FOLD_ARGS:
        case java_lang_invoke_AdapterMethodHandle::OP_COLLECT_ARGS: {
          int coll_slots = chain.MethodHandle_vmslots();
          tty->print(" coll_slots %d", coll_slots);
          break;
        }

        case java_lang_invoke_AdapterMethodHandle::OP_SPREAD_ARGS: {
          // Check the required length.
          int spread_slots = 1 + chain.adapter_conversion_stack_pushes();
          tty->print(" spread_slots %d", spread_slots);
          break;
        }

        default:
          tty->print_cr("bad adapter conversion");
          break;
      }
    } else {
      // DMH
      tty->print("direct: ");
      chain.last_method_oop()->print_short_name(tty);
    }

    tty->print(" (");
    objArrayOop ptypes = java_lang_invoke_MethodType::ptypes(chain.method_type_oop());
    for (int i = ptypes->length() - 1; i >= 0; i--) {
      BasicType t = java_lang_Class::as_BasicType(ptypes->obj_at(i));
      if (t == T_ARRAY) t = T_OBJECT;
      tty->print("%c", type2char(t));
      if (t == T_LONG || t == T_DOUBLE) tty->print("_");
    }
    tty->print(")");
    BasicType rtype = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::rtype(chain.method_type_oop()));
    if (rtype == T_ARRAY) rtype = T_OBJECT;
    tty->print("%c", type2char(rtype));
    tty->cr();
    if (!chain.is_last()) {
      chain.next(CHECK);
    } else {
      break;
    }
  }
}
#endif


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

      // Check that the arg_slot is valid.  In most cases it must be
      // within range of the current arguments but there are some
      // exceptions.  Those are sanity checked in their implemention
      // below.
      if ((arg_slot < 0 || arg_slot >= _outgoing.length()) &&
          conv_op > java_lang_invoke_AdapterMethodHandle::OP_RETYPE_RAW &&
          conv_op != java_lang_invoke_AdapterMethodHandle::OP_COLLECT_ARGS &&
          conv_op != java_lang_invoke_AdapterMethodHandle::OP_FOLD_ARGS) {
        lose(err_msg("bad argument index %d", arg_slot), CHECK_(empty));
      }

      bool retain_original_args = false;  // used by fold/collect logic

      // perform the adapter action
      switch (conv_op) {
      case java_lang_invoke_AdapterMethodHandle::OP_RETYPE_ONLY:
        // No changes to arguments; pass the bits through.
        break;

      case java_lang_invoke_AdapterMethodHandle::OP_RETYPE_RAW: {
        // To keep the verifier happy, emit bitwise ("raw") conversions as needed.
        // See MethodHandles::same_basic_type_for_arguments for allowed conversions.
        Handle incoming_mtype(THREAD, chain().method_type_oop());
        Handle outgoing_mtype;
        {
          oop outgoing_mh_oop = chain().vmtarget_oop();
          if (!java_lang_invoke_MethodHandle::is_instance(outgoing_mh_oop))
            lose("outgoing target not a MethodHandle", CHECK_(empty));
          outgoing_mtype = Handle(THREAD, java_lang_invoke_MethodHandle::type(outgoing_mh_oop));
        }

        int nptypes = java_lang_invoke_MethodType::ptype_count(outgoing_mtype());
        if (nptypes != java_lang_invoke_MethodType::ptype_count(incoming_mtype()))
          lose("incoming and outgoing parameter count do not agree", CHECK_(empty));

        // Argument types.
        for (int i = 0, slot = _outgoing.length() - 1; slot >= 0; slot--) {
          if (arg_type(slot) == T_VOID)  continue;

          klassOop  src_klass = NULL;
          klassOop  dst_klass = NULL;
          BasicType src = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::ptype(incoming_mtype(), i), &src_klass);
          BasicType dst = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::ptype(outgoing_mtype(), i), &dst_klass);
          retype_raw_argument_type(src, dst, slot, CHECK_(empty));
          i++;  // We need to skip void slots at the top of the loop.
        }

        // Return type.
        {
          BasicType src = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::rtype(incoming_mtype()));
          BasicType dst = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::rtype(outgoing_mtype()));
          retype_raw_return_type(src, dst, CHECK_(empty));
        }
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_CHECK_CAST: {
        // checkcast the Nth outgoing argument in place
        klassOop dest_klass = NULL;
        BasicType dest = java_lang_Class::as_BasicType(chain().adapter_arg_oop(), &dest_klass);
        assert(dest == T_OBJECT, "");
        ArgToken arg = _outgoing.at(arg_slot);
        assert(dest == arg.basic_type(), "");
        arg = make_conversion(T_OBJECT, dest_klass, Bytecodes::_checkcast, arg, CHECK_(empty));
        debug_only(dest_klass = (klassOop)badOop);
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_PRIM: {
        // i2l, etc., on the Nth outgoing argument in place
        BasicType src = chain().adapter_conversion_src_type(),
                  dest = chain().adapter_conversion_dest_type();
        ArgToken arg = _outgoing.at(arg_slot);
        Bytecodes::Code bc = conversion_code(src, dest);
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
          lose(err_msg("bad primitive conversion for %s -> %s", type2name(src), type2name(dest)), CHECK_(empty));
        }
        change_argument(src, arg_slot, dest, arg);
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_REF_TO_PRIM: {
        // checkcast to wrapper type & call intValue, etc.
        BasicType dest = chain().adapter_conversion_dest_type();
        ArgToken arg = _outgoing.at(arg_slot);
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

      case java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_REF: {
        // call wrapper type.valueOf
        BasicType src = chain().adapter_conversion_src_type();
        vmIntrinsics::ID boxer = vmIntrinsics::for_boxing(src);
        if (boxer == vmIntrinsics::_none) {
          lose("no boxing method", CHECK_(empty));
        }
        ArgToken arg = _outgoing.at(arg_slot);
        ArgToken arglist[2];
        arglist[0] = arg;         // outgoing value
        arglist[1] = ArgToken();  // sentinel
        arg = make_invoke(NULL, boxer, Bytecodes::_invokestatic, false, 1, &arglist[0], CHECK_(empty));
        change_argument(src, arg_slot, T_OBJECT, arg);
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_SWAP_ARGS: {
        int dest_arg_slot = chain().adapter_conversion_vminfo();
        if (!has_argument(dest_arg_slot)) {
          lose("bad swap index", CHECK_(empty));
        }
        // a simple swap between two arguments
        if (arg_slot > dest_arg_slot) {
          int tmp = arg_slot;
          arg_slot = dest_arg_slot;
          dest_arg_slot = tmp;
        }
        ArgToken a1 = _outgoing.at(arg_slot);
        ArgToken a2 = _outgoing.at(dest_arg_slot);
        change_argument(a2.basic_type(), dest_arg_slot, a1);
        change_argument(a1.basic_type(), arg_slot, a2);
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_ROT_ARGS: {
        int dest_arg_slot = chain().adapter_conversion_vminfo();
        if (!has_argument(dest_arg_slot) || arg_slot == dest_arg_slot) {
          lose("bad rotate index", CHECK_(empty));
        }
        // Rotate the source argument (plus following N slots) into the
        // position occupied by the dest argument (plus following N slots).
        int rotate_count = type2size[chain().adapter_conversion_src_type()];
        // (no other rotate counts are currently supported)
        if (arg_slot < dest_arg_slot) {
          for (int i = 0; i < rotate_count; i++) {
            ArgToken temp = _outgoing.at(arg_slot);
            _outgoing.remove_at(arg_slot);
            _outgoing.insert_before(dest_arg_slot + rotate_count - 1, temp);
          }
        } else { // arg_slot > dest_arg_slot
          for (int i = 0; i < rotate_count; i++) {
            ArgToken temp = _outgoing.at(arg_slot + rotate_count - 1);
            _outgoing.remove_at(arg_slot + rotate_count - 1);
            _outgoing.insert_before(dest_arg_slot, temp);
          }
        }
        assert(_outgoing_argc == argument_count_slow(), "empty slots under control");
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_DUP_ARGS: {
        int dup_slots = chain().adapter_conversion_stack_pushes();
        if (dup_slots <= 0) {
          lose("bad dup count", CHECK_(empty));
        }
        for (int i = 0; i < dup_slots; i++) {
          ArgToken dup = _outgoing.at(arg_slot + 2*i);
          if (dup.basic_type() != T_VOID)     _outgoing_argc += 1;
          _outgoing.insert_before(i, dup);
        }
        assert(_outgoing_argc == argument_count_slow(), "empty slots under control");
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_DROP_ARGS: {
        int drop_slots = -chain().adapter_conversion_stack_pushes();
        if (drop_slots <= 0) {
          lose("bad drop count", CHECK_(empty));
        }
        for (int i = 0; i < drop_slots; i++) {
          ArgToken drop = _outgoing.at(arg_slot);
          if (drop.basic_type() != T_VOID)    _outgoing_argc -= 1;
          _outgoing.remove_at(arg_slot);
        }
        assert(_outgoing_argc == argument_count_slow(), "empty slots under control");
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_FOLD_ARGS:
        retain_original_args = true;   // and fall through:
      case java_lang_invoke_AdapterMethodHandle::OP_COLLECT_ARGS: {
        // call argument MH recursively
        //{static int x; if (!x++) print_method_handle(chain().method_handle_oop()); --x;}
        Handle recursive_mh(THREAD, chain().adapter_arg_oop());
        if (!java_lang_invoke_MethodHandle::is_instance(recursive_mh())) {
          lose("recursive target not a MethodHandle", CHECK_(empty));
        }
        Handle recursive_mtype(THREAD, java_lang_invoke_MethodHandle::type(recursive_mh()));
        int argc = java_lang_invoke_MethodType::ptype_count(recursive_mtype());
        int coll_slots = java_lang_invoke_MethodHandle::vmslots(recursive_mh());
        BasicType rtype = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::rtype(recursive_mtype()));
        ArgToken* arglist = NEW_RESOURCE_ARRAY(ArgToken, 1 + argc + 1);  // 1+: mh, +1: sentinel
        arglist[0] = make_oop_constant(recursive_mh(), CHECK_(empty));
        if (arg_slot < 0 || coll_slots < 0 || arg_slot + coll_slots > _outgoing.length()) {
          lose("bad fold/collect arg slot", CHECK_(empty));
        }
        for (int i = 0, slot = arg_slot + coll_slots - 1; slot >= arg_slot; slot--) {
          ArgToken arg_state = _outgoing.at(slot);
          BasicType  arg_type  = arg_state.basic_type();
          if (arg_type == T_VOID)  continue;
          ArgToken arg = _outgoing.at(slot);
          if (i >= argc) { lose("bad fold/collect arg", CHECK_(empty)); }
          arglist[1+i] = arg;
          if (!retain_original_args)
            change_argument(arg_type, slot, T_VOID, ArgToken(tt_void));
          i++;
        }
        arglist[1+argc] = ArgToken();  // sentinel
        oop invoker = java_lang_invoke_MethodTypeForm::vmlayout(
                          java_lang_invoke_MethodType::form(recursive_mtype()) );
        if (invoker == NULL || !invoker->is_method()) {
          lose("bad vmlayout slot", CHECK_(empty));
        }
        // FIXME: consider inlining the invokee at the bytecode level
        ArgToken ret = make_invoke(methodOop(invoker), vmIntrinsics::_none,
                                   Bytecodes::_invokevirtual, false, 1+argc, &arglist[0], CHECK_(empty));
        DEBUG_ONLY(invoker = NULL);
        if (rtype == T_OBJECT) {
          klassOop rklass = java_lang_Class::as_klassOop( java_lang_invoke_MethodType::rtype(recursive_mtype()) );
          if (rklass != SystemDictionary::Object_klass() &&
              !Klass::cast(rklass)->is_interface()) {
            // preserve type safety
            ret = make_conversion(T_OBJECT, rklass, Bytecodes::_checkcast, ret, CHECK_(empty));
          }
        }
        if (rtype != T_VOID) {
          int ret_slot = arg_slot + (retain_original_args ? coll_slots : 0);
          change_argument(T_VOID, ret_slot, rtype, ret);
        }
        break;
      }

      case java_lang_invoke_AdapterMethodHandle::OP_SPREAD_ARGS: {
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
        ArgToken arg = _outgoing.at(arg_slot);
        assert(arg.basic_type() == T_OBJECT, "");
        ArgToken array_arg = arg;
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
                    Bytecodes::_invokestatic, false, 2, &arglist[0], CHECK_(empty));

        // Spread out the array elements.
        Bytecodes::Code aload_op = Bytecodes::_nop;
        switch (element_type) {
        case T_INT:       aload_op = Bytecodes::_iaload; break;
        case T_LONG:      aload_op = Bytecodes::_laload; break;
        case T_FLOAT:     aload_op = Bytecodes::_faload; break;
        case T_DOUBLE:    aload_op = Bytecodes::_daload; break;
        case T_OBJECT:    aload_op = Bytecodes::_aaload; break;
        case T_BOOLEAN:   // fall through:
        case T_BYTE:      aload_op = Bytecodes::_baload; break;
        case T_CHAR:      aload_op = Bytecodes::_caload; break;
        case T_SHORT:     aload_op = Bytecodes::_saload; break;
        default:          lose("primitive array NYI", CHECK_(empty));
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
        if (bt == arg_type || (bt == T_INT && is_subword_type(arg_type))) {
          arg = make_prim_constant(arg_type, &arg_value, CHECK_(empty));
        } else {
          lose(err_msg("bad bound value: arg_type %s boxing %s", type2name(arg_type), type2name(bt)), CHECK_(empty));
        }
      }
      DEBUG_ONLY(arg_oop = badOop);
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
    ArgToken arg_state = _outgoing.at(i);
    if (arg_state.basic_type() == T_VOID)  continue;
    arglist[ap++] = _outgoing.at(i);
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
  int nptypes = java_lang_invoke_MethodType::ptype_count(mtype());
  _outgoing_argc = nptypes;
  int argp = nptypes - 1;
  if (argp >= 0) {
    _outgoing.at_grow(argp, ArgToken(tt_void)); // presize
  }
  for (int i = 0; i < nptypes; i++) {
    klassOop  arg_type_klass = NULL;
    BasicType arg_type = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::ptype(mtype(), i), &arg_type_klass);
    int index = new_local_index(arg_type);
    ArgToken arg = make_parameter(arg_type, arg_type_klass, index, CHECK);
    DEBUG_ONLY(arg_type_klass = (klassOop) NULL);
    _outgoing.at_put(argp, arg);
    if (type2size[arg_type] == 2) {
      // add the extra slot, so we can model the JVM stack
      _outgoing.insert_before(argp+1, ArgToken(tt_void));
    }
    --argp;
  }
  // call make_parameter at the end of the list for the return type
  klassOop  ret_type_klass = NULL;
  BasicType ret_type = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::rtype(mtype()), &ret_type_klass);
  ArgToken  ret = make_parameter(ret_type, ret_type_klass, -1, CHECK);
  // ignore ret; client can catch it if needed

  assert(_outgoing_argc == argument_count_slow(), "empty slots under control");

  verify_args_and_signature(CHECK);
}


#ifdef ASSERT
void MethodHandleWalker::verify_args_and_signature(TRAPS) {
  int index = _outgoing.length() - 1;
  objArrayOop ptypes = java_lang_invoke_MethodType::ptypes(chain().method_type_oop());
  for (int i = 0, limit = ptypes->length(); i < limit; i++) {
    BasicType t = java_lang_Class::as_BasicType(ptypes->obj_at(i));
    if (t == T_ARRAY) t = T_OBJECT;
    if (t == T_LONG || t == T_DOUBLE) {
      assert(T_VOID == _outgoing.at(index).basic_type(), "types must match");
      index--;
    }
    assert(t == _outgoing.at(index).basic_type(), "types must match");
    index--;
  }
}
#endif


// -----------------------------------------------------------------------------
// MethodHandleWalker::change_argument
//
// This is messy because some kinds of arguments are paired with
// companion slots containing an empty value.
void MethodHandleWalker::change_argument(BasicType old_type, int slot, const ArgToken& new_arg) {
  BasicType new_type = new_arg.basic_type();
  int old_size = type2size[old_type];
  int new_size = type2size[new_type];
  if (old_size == new_size) {
    // simple case first
    _outgoing.at_put(slot, new_arg);
  } else if (old_size > new_size) {
    for (int i = old_size - 1; i >= new_size; i--) {
      assert((i != 0) == (_outgoing.at(slot + i).basic_type() == T_VOID), "");
      _outgoing.remove_at(slot + i);
    }
    if (new_size > 0)
      _outgoing.at_put(slot, new_arg);
    else
      _outgoing_argc -= 1;      // deleted a real argument
  } else {
    for (int i = old_size; i < new_size; i++) {
      _outgoing.insert_before(slot + i, ArgToken(tt_void));
    }
    _outgoing.at_put(slot, new_arg);
    if (old_size == 0)
      _outgoing_argc += 1;      // inserted a real argument
  }
  assert(_outgoing_argc == argument_count_slow(), "empty slots under control");
}


#ifdef ASSERT
int MethodHandleWalker::argument_count_slow() {
  int args_seen = 0;
  for (int i = _outgoing.length() - 1; i >= 0; i--) {
    if (_outgoing.at(i).basic_type() != T_VOID) {
      ++args_seen;
      if (_outgoing.at(i).basic_type() == T_LONG ||
          _outgoing.at(i).basic_type() == T_DOUBLE) {
        assert(_outgoing.at(i + 1).basic_type() == T_VOID, "should only follow two word");
      }
    } else {
      assert(_outgoing.at(i - 1).basic_type() == T_LONG ||
             _outgoing.at(i - 1).basic_type() == T_DOUBLE, "should only follow two word");
    }
  }
  return args_seen;
}
#endif


// -----------------------------------------------------------------------------
// MethodHandleWalker::retype_raw_conversion
//
// Do the raw retype conversions for OP_RETYPE_RAW.
void MethodHandleWalker::retype_raw_conversion(BasicType src, BasicType dst, bool for_return, int slot, TRAPS) {
  if (src != dst) {
    if (MethodHandles::same_basic_type_for_returns(src, dst, /*raw*/ true)) {
      if (MethodHandles::is_float_fixed_reinterpretation_cast(src, dst)) {
        if (for_return)  Untested("MHW return raw conversion");  // still untested
        vmIntrinsics::ID iid = vmIntrinsics::for_raw_conversion(src, dst);
        if (iid == vmIntrinsics::_none) {
          lose("no raw conversion method", CHECK);
        }
        ArgToken arglist[2];
        if (!for_return) {
          // argument type conversion
          ArgToken arg = _outgoing.at(slot);
          assert(arg.token_type() >= tt_symbolic || src == arg.basic_type(), "sanity");
          arglist[0] = arg;         // outgoing 'this'
          arglist[1] = ArgToken();  // sentinel
          arg = make_invoke(NULL, iid, Bytecodes::_invokestatic, false, 1, &arglist[0], CHECK);
          change_argument(src, slot, dst, arg);
        } else {
          // return type conversion
          klassOop arg_klass = NULL;
          arglist[0] = make_parameter(src, arg_klass, -1, CHECK);  // return value
          arglist[1] = ArgToken();                                 // sentinel
          (void) make_invoke(NULL, iid, Bytecodes::_invokestatic, false, 1, &arglist[0], CHECK);
        }
      } else {
        // Nothing to do.
      }
    } else if (src == T_OBJECT && is_java_primitive(dst)) {
      // ref-to-prim: discard ref, push zero
      lose("requested ref-to-prim conversion not expected", CHECK);
    } else {
      lose(err_msg("requested raw conversion not allowed: %s -> %s", type2name(src), type2name(dst)), CHECK);
    }
  }
}


// -----------------------------------------------------------------------------
// MethodHandleCompiler

MethodHandleCompiler::MethodHandleCompiler(Handle root, Symbol* name, Symbol* signature, int invoke_count, bool is_invokedynamic, TRAPS)
  : MethodHandleWalker(root, is_invokedynamic, THREAD),
    _invoke_count(invoke_count),
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
  _name_index      = cpool_symbol_put(name);
  _signature_index = cpool_symbol_put(signature);

  // Get return type klass.
  Handle first_mtype(THREAD, chain().method_type_oop());
  // _rklass is NULL for primitives.
  _rtype = java_lang_Class::as_BasicType(java_lang_invoke_MethodType::rtype(first_mtype()), &_rklass);
  if (_rtype == T_ARRAY)  _rtype = T_OBJECT;

  ArgumentSizeComputer args(signature);
  int params = args.size() + 1;  // Incoming arguments plus receiver.
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


void MethodHandleCompiler::emit_bc(Bytecodes::Code op, int index, int args_size) {
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
  case Bytecodes::_iand:
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
  case Bytecodes::_iaload:
  case Bytecodes::_laload:
  case Bytecodes::_faload:
  case Bytecodes::_daload:
  case Bytecodes::_aaload:
  case Bytecodes::_baload:
  case Bytecodes::_caload:
  case Bytecodes::_saload:
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
    if (index == (index & 0xff)) {
      _bytecode.push(op);
      _bytecode.push(index);
    } else {
      _bytecode.push(Bytecodes::_ldc_w);
      _bytecode.push(index >> 8);
      _bytecode.push(index);
    }
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
    if (index == (index & 0xff)) {
      _bytecode.push(op);
      _bytecode.push(index);
    } else {
      // doesn't fit in a u2
      _bytecode.push(Bytecodes::_wide);
      _bytecode.push(op);
      _bytecode.push(index >> 8);
      _bytecode.push(index);
    }
    break;

  // bkk
  case Bytecodes::_ldc_w:
  case Bytecodes::_ldc2_w:
  case Bytecodes::_checkcast:
    assert(Bytecodes::format_bits(op, false) == Bytecodes::_fmt_bkk, "wrong bytecode format");
    assert((unsigned short) index == index, "index does not fit in 16-bit");
    _bytecode.push(op);
    _bytecode.push(index >> 8);
    _bytecode.push(index);
    break;

  // bJJ
  case Bytecodes::_invokestatic:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokevirtual:
    assert(Bytecodes::format_bits(op, false) == Bytecodes::_fmt_bJJ, "wrong bytecode format");
    assert((unsigned short) index == index, "index does not fit in 16-bit");
    _bytecode.push(op);
    _bytecode.push(index >> 8);
    _bytecode.push(index);
    break;

  case Bytecodes::_invokeinterface:
    assert(Bytecodes::format_bits(op, false) == Bytecodes::_fmt_bJJ, "wrong bytecode format");
    assert((unsigned short) index == index, "index does not fit in 16-bit");
    assert(args_size > 0, "valid args_size");
    _bytecode.push(op);
    _bytecode.push(index >> 8);
    _bytecode.push(index);
    _bytecode.push(args_size);
    _bytecode.push(0);
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
  if (is_subword_type(bt)) bt = T_INT;
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
  TokenType tt = src.token_type();
  int index = -1;

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
    if (tt == tt_constant) {
      emit_load_constant(src);
    } else {
      emit_load(srctype, src.index());
    }
    stack_pop(srctype);  // pop the src type
    emit_bc(op);
    stack_push(type);    // push the dest value
    if (tt != tt_constant)
      index = src.index();
    if (srctype != type || index == -1)
      index = new_local_index(type);
    emit_store(type, index);
    break;

  case Bytecodes::_checkcast:
    if (tt == tt_constant) {
      emit_load_constant(src);
    } else {
      emit_load(srctype, src.index());
      index = src.index();
    }
    emit_bc(op, cpool_klass_put(tk));
    // Allocate a new local for the type so that we don't hide the
    // previous type from the verifier.
    index = new_local_index(type);
    emit_store(srctype, index);
    break;

  case Bytecodes::_nop:
    // nothing to do
    return src;

  default:
    if (op == Bytecodes::_illegal)
      lose(err_msg("no such primitive conversion: %s -> %s", type2name(src.basic_type()), type2name(type)), THREAD);
    else
      lose(err_msg("bad primitive conversion op: %s", Bytecodes::name(op)), THREAD);
    return make_prim_constant(type, &zero_jvalue, THREAD);
  }

  return make_parameter(type, tk, index, THREAD);
}


// -----------------------------------------------------------------------------
// MethodHandleCompiler
//

// Values used by the compiler.
jvalue MethodHandleCompiler::zero_jvalue = { 0 };
jvalue MethodHandleCompiler::one_jvalue  = { 1 };

// Emit bytecodes for the given invoke instruction.
MethodHandleWalker::ArgToken
MethodHandleCompiler::make_invoke(methodOop m, vmIntrinsics::ID iid,
                                  Bytecodes::Code op, bool tailcall,
                                  int argc, MethodHandleWalker::ArgToken* argv,
                                  TRAPS) {
  ArgToken zero;
  if (m == NULL) {
    // Get the intrinsic methodOop.
    m = vmIntrinsics::method_for(iid);
    if (m == NULL) {
      lose(vmIntrinsics::name_at(iid), CHECK_(zero));
    }
  }

  klassOop klass     = m->method_holder();
  Symbol*  name      = m->name();
  Symbol*  signature = m->signature();

  // Count the number of arguments, not the size
  ArgumentCount asc(signature);
  assert(argc == asc.size() + ((op == Bytecodes::_invokestatic || op == Bytecodes::_invokedynamic) ? 0 : 1),
         "argc mismatch");

  if (tailcall) {
    // Actually, in order to make these methods more recognizable,
    // let's put them in holder class MethodHandle.  That way stack
    // walkers and compiler heuristics can recognize them.
    _target_klass = SystemDictionary::MethodHandle_klass();
  }

  // Inline the method.
  InvocationCounter* ic = m->invocation_counter();
  ic->set_carry_flag();

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

  case Bytecodes::_invokeinterface: {
    ArgumentSizeComputer asc(signature);
    emit_bc(op, methodref_index, asc.size() + 1);
    break;
  }

  default:
    ShouldNotReachHere();
  }

  // If tailcall, we have walked all the way to a direct method handle.
  // Otherwise, make a recursive call to some helper routine.
  BasicType rbt = m->result_type();
  if (rbt == T_ARRAY)  rbt = T_OBJECT;
  stack_push(rbt);  // The return value is already pushed onto the stack.
  ArgToken ret;
  if (tailcall) {
    if (rbt != _rtype) {
      if (rbt == T_VOID) {
        // push a zero of the right sort
        if (_rtype == T_OBJECT) {
          zero = make_oop_constant(NULL, CHECK_(zero));
        } else {
          zero = make_prim_constant(_rtype, &zero_jvalue, CHECK_(zero));
        }
        emit_load_constant(zero);
      } else if (_rtype == T_VOID) {
        // We'll emit a _return with something on the stack.
        // It's OK to ignore what's on the stack.
      } else if (rbt == T_INT && is_subword_type(_rtype)) {
        // Convert value to match return type.
        switch (_rtype) {
        case T_BOOLEAN: {
          // boolean is treated as a one-bit unsigned integer.
          // Cf. API documentation: java/lang/invoke/MethodHandles.html#explicitCastArguments
          ArgToken one = make_prim_constant(T_INT, &one_jvalue, CHECK_(zero));
          emit_load_constant(one);
          emit_bc(Bytecodes::_iand);
          break;
        }
        case T_BYTE:    emit_bc(Bytecodes::_i2b); break;
        case T_CHAR:    emit_bc(Bytecodes::_i2c); break;
        case T_SHORT:   emit_bc(Bytecodes::_i2s); break;
        default: ShouldNotReachHere();
        }
      } else if (is_subword_type(rbt) && (is_subword_type(_rtype) || (_rtype == T_INT))) {
        // The subword type was returned as an int and will be passed
        // on as an int.
      } else {
        lose("unknown conversion", CHECK_(zero));
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
  switch (base.token_type()) {
    case tt_parameter:
    case tt_temporary:
      emit_load(base.basic_type(), base.index());
      break;
    case tt_constant:
      emit_load_constant(base);
      break;
    default:
      ShouldNotReachHere();
  }
  switch (offset.token_type()) {
    case tt_parameter:
    case tt_temporary:
      emit_load(offset.basic_type(), offset.index());
      break;
    case tt_constant:
      emit_load_constant(offset);
      break;
    default:
      ShouldNotReachHere();
  }
  emit_bc(op);
  int index = new_local_index(type);
  emit_store(type, index);
  return ArgToken(tt_temporary, type, index);
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
//     if (con != NULL && con->is_primitive() && con.basic_type() == bt) {
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
  constantPoolOop cpool_oop = oopFactory::new_constantPool(_constants.length(),
                                                           oopDesc::IsSafeConc,
                                                           CHECK_(nullHandle));
  constantPoolHandle cpool(THREAD, cpool_oop);

  // Fill the real constant pool skipping the zero element.
  for (int i = 1; i < _constants.length(); i++) {
    ConstantValue* cv = _constants.at(i);
    switch (cv->tag()) {
    case JVM_CONSTANT_Utf8:        cpool->symbol_at_put(       i, cv->symbol()                         ); break;
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
  methodHandle empty;
  // Create a method that holds the generated bytecode.  invokedynamic
  // has no receiver, normal MH calls do.
  int flags_bits;
  if (for_invokedynamic())
    flags_bits = (/*JVM_MH_INVOKE_BITS |*/ JVM_ACC_PUBLIC | JVM_ACC_FINAL | JVM_ACC_SYNTHETIC | JVM_ACC_STATIC);
  else
    flags_bits = (/*JVM_MH_INVOKE_BITS |*/ JVM_ACC_PUBLIC | JVM_ACC_FINAL | JVM_ACC_SYNTHETIC);

  // Create a new method
  methodHandle m;
  {
    methodOop m_oop = oopFactory::new_method(bytecode_length(),
                                             accessFlags_from(flags_bits),
                                             0, 0, 0, oopDesc::IsSafeConc, CHECK_(empty));
    m = methodHandle(THREAD, m_oop);
  }

  constantPoolHandle cpool = get_constant_pool(CHECK_(empty));
  m->set_constants(cpool());

  m->set_name_index(_name_index);
  m->set_signature_index(_signature_index);

  m->set_code((address) bytecode());

  m->set_max_stack(_max_stack);
  m->set_max_locals(max_locals());
  m->set_size_of_parameters(_num_params);

  typeArrayHandle exception_handlers(THREAD, Universe::the_empty_int_array());
  m->set_exception_table(exception_handlers());

  // Rewrite the method and set up the constant pool cache.
  objArrayOop m_array = oopFactory::new_system_objArray(1, CHECK_(empty));
  objArrayHandle methods(THREAD, m_array);
  methods->obj_at_put(0, m());
  Rewriter::rewrite(_target_klass(), cpool, methods, CHECK_(empty));  // Use fake class.

  // Set the invocation counter's count to the invoke count of the
  // original call site.
  InvocationCounter* ic = m->invocation_counter();
  ic->set(InvocationCounter::wait_for_compile, _invoke_count);

  // Create a new MDO
  {
    methodDataOop mdo = oopFactory::new_methodData(m, CHECK_(empty));
    assert(m->method_data() == NULL, "there should not be an MDO yet");
    m->set_method_data(mdo);

    // Iterate over all profile data and set the count of the counter
    // data entries to the original call site counter.
    for (ProfileData* profile_data = mdo->first_data();
         mdo->is_valid(profile_data);
         profile_data = mdo->next_data(profile_data)) {
      if (profile_data->is_CounterData()) {
        CounterData* counter_data = profile_data->as_CounterData();
        counter_data->set_count(_invoke_count);
      }
    }
  }

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

// MH printer for debugging.

class MethodHandlePrinter : public MethodHandleWalker {
private:
  outputStream* _out;
  bool          _verbose;
  int           _temp_num;
  int           _param_state;
  stringStream  _strbuf;
  const char* strbuf() {
    const char* s = _strbuf.as_string();
    _strbuf.reset();
    return s;
  }
  ArgToken token(const char* str, BasicType type) {
    return ArgToken(str, type);
  }
  const char* string(ArgToken token) {
    return token.str();
  }
  void start_params() {
    _param_state <<= 1;
    _out->print("(");
  }
  void end_params() {
    if (_verbose)  _out->print("\n");
    _out->print(") => {");
    _param_state >>= 1;
  }
  void put_type_name(BasicType type, klassOop tk, outputStream* s) {
    const char* kname = NULL;
    if (tk != NULL)
      kname = Klass::cast(tk)->external_name();
    s->print("%s", (kname != NULL) ? kname : type2name(type));
  }
  ArgToken maybe_make_temp(const char* statement_op, BasicType type, const char* temp_name) {
    const char* value = strbuf();
    if (!_verbose)  return token(value, type);
    // make an explicit binding for each separate value
    _strbuf.print("%s%d", temp_name, ++_temp_num);
    const char* temp = strbuf();
    _out->print("\n  %s %s %s = %s;", statement_op, type2name(type), temp, value);
    return token(temp, type);
  }

public:
  MethodHandlePrinter(Handle root, bool verbose, outputStream* out, TRAPS)
    : MethodHandleWalker(root, false, THREAD),
      _out(out),
      _verbose(verbose),
      _param_state(0),
      _temp_num(0)
  {
    start_params();
  }
  virtual ArgToken make_parameter(BasicType type, klassOop tk, int argnum, TRAPS) {
    if (argnum < 0) {
      end_params();
      return token("return", type);
    }
    if ((_param_state & 1) == 0) {
      _param_state |= 1;
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
    return token(arg, type);
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
  void print_bytecode_name(Bytecodes::Code op) {
    if (Bytecodes::is_defined(op))
      _strbuf.print("%s", Bytecodes::name(op));
    else
      _strbuf.print("bytecode_%d", (int) op);
  }
  virtual ArgToken make_conversion(BasicType type, klassOop tk, Bytecodes::Code op, const ArgToken& src, TRAPS) {
    print_bytecode_name(op);
    _strbuf.print("(%s", string(src));
    if (tk != NULL) {
      _strbuf.print(", ");
      put_type_name(type, tk, &_strbuf);
    }
    _strbuf.print(")");
    return maybe_make_temp("convert", type, "v");
  }
  virtual ArgToken make_fetch(BasicType type, klassOop tk, Bytecodes::Code op, const ArgToken& base, const ArgToken& offset, TRAPS) {
    _strbuf.print("%s(%s, %s", Bytecodes::name(op), string(base), string(offset));
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
    Symbol* name;
    Symbol* sig;
    if (m != NULL) {
      name = m->name();
      sig  = m->signature();
    } else {
      name = vmSymbols::symbol_at(vmIntrinsics::name_for(iid));
      sig  = vmSymbols::symbol_at(vmIntrinsics::signature_for(iid));
    }
    _strbuf.print("%s %s%s(", Bytecodes::name(op), name->as_C_string(), sig->as_C_string());
    for (int i = 0; i < argc; i++) {
      _strbuf.print("%s%s", (i > 0 ? ", " : ""), string(argv[i]));
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
    Thread* THREAD = Thread::current();
    ResourceMark rm;
    MethodHandlePrinter printer(root, verbose, out, THREAD);
    if (!HAS_PENDING_EXCEPTION)
      printer.walk(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      oop ex = PENDING_EXCEPTION;
      CLEAR_PENDING_EXCEPTION;
      out->print(" *** ");
      if (printer.lose_message() != NULL)  out->print("%s ", printer.lose_message());
      out->print("}");
    }
    out->print("\n");
  }
};

extern "C"
void print_method_handle(oop mh) {
  if (!mh->is_oop()) {
    tty->print_cr("*** not a method handle: "PTR_FORMAT, (intptr_t)mh);
  } else if (java_lang_invoke_MethodHandle::is_instance(mh)) {
    MethodHandlePrinter::print(mh);
  } else {
    tty->print("*** not a method handle: ");
    mh->print();
  }
}

#endif // PRODUCT
