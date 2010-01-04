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

// Low-level parser for method handle chains.
class MethodHandleChain : StackObj {
public:
  typedef MethodHandles::EntryKind EntryKind;

private:
  Handle        _root;          // original target
  Handle        _method_handle; // current target
  bool          _is_last;       // final guy in chain
  bool          _is_bound;      // has a bound argument
  BasicType     _arg_type;      // if is_bound, the bound argument type
  int           _arg_slot;      // if is_bound or is_adapter, affected argument slot
  jint          _conversion;    // conversion field of AMH or -1
  methodHandle  _last_method;   // if is_last, which method we target
  Bytecodes::Code _last_invoke; // if is_last, type of invoke
  const char*   _lose_message;  // saved argument to lose()

  void set_method_handle(Handle target, TRAPS);
  void set_last_method(oop target, TRAPS);
  static BasicType compute_bound_arg_type(oop target, methodOop m, int arg_slot, TRAPS);

  oop MethodHandle_type_oop()     { return java_dyn_MethodHandle::type(method_handle_oop()); }
  oop MethodHandle_vmtarget_oop() { return java_dyn_MethodHandle::vmtarget(method_handle_oop()); }
  int MethodHandle_vmslots()      { return java_dyn_MethodHandle::vmslots(method_handle_oop()); }
  int DirectMethodHandle_vmindex()     { return sun_dyn_DirectMethodHandle::vmindex(method_handle_oop()); }
  oop BoundMethodHandle_argument_oop() { return sun_dyn_BoundMethodHandle::argument(method_handle_oop()); }
  int BoundMethodHandle_vmargslot()    { return sun_dyn_BoundMethodHandle::vmargslot(method_handle_oop()); }
  int AdapterMethodHandle_conversion() { return sun_dyn_AdapterMethodHandle::conversion(method_handle_oop()); }

public:
  MethodHandleChain(Handle root, TRAPS)
    : _root(root)
  { set_method_handle(root, THREAD); }

  bool is_adapter()             { return _conversion != -1; }
  bool is_bound()               { return _is_bound; }
  bool is_last()                { return _is_last; }

  void next(TRAPS) {
    assert(!is_last(), "");
    set_method_handle(MethodHandle_vmtarget_oop(), THREAD);
  }

  Handle method_handle()        { return _method_handle; }
  oop    method_handle_oop()    { return _method_handle(); }
  oop    method_type_oop()      { return MethodHandle_type_oop(); }

  jint adapter_conversion()     { assert(is_adapter(), ""); return _conversion; }
  int  adapter_conversion_op()  { return MethodHandles::adapter_conversion_op(adapter_conversion()); }
  BasicType adapter_conversion_src_type()
                                { return MethodHandles::adapter_conversion_src_type(adapter_conversion()); }
  BasicType adapter_conversion_dest_type()
                                { return MethodHandles::adapter_conversion_dest_type(adapter_conversion()); }
  int  adapter_conversion_stack_move()
                                { return MethodHandles::adapter_conversion_stack_move(adapter_conversion()); }
  int  adapter_conversion_stack_pushes()
                                { return adapter_conversion_stack_move() / MethodHandles::stack_move_unit(); }
  int  adapter_conversion_vminfo()
                                { return MethodHandles::adapter_conversion_vminfo(adapter_conversion()); }
  int adapter_arg_slot()        { assert(is_adapter(), ""); return _arg_slot; }
  oop adapter_arg_oop()         { assert(is_adapter(), ""); return BoundMethodHandle_argument_oop(); }

  BasicType bound_arg_type()    { assert(is_bound(), ""); return _arg_type; }
  int       bound_arg_slot()    { assert(is_bound(), ""); return _arg_slot; }
  oop       bound_arg_oop()     { assert(is_bound(), ""); return BoundMethodHandle_argument_oop(); }

  methodOop last_method_oop()   { assert(is_last(), ""); return _last_method(); }
  Bytecodes::Code last_invoke_code() { assert(is_last(), ""); return _last_invoke; }

  void lose(const char* msg, TRAPS);
  const char* lose_message()    { return _lose_message; }
};


// Structure walker for method handles.
// Does abstract interpretation on top of low-level parsing.
// You supply the tokens shuffled by the abstract interpretation.
class MethodHandleWalker : StackObj {
public:
  struct _ArgToken { };  // dummy struct
  typedef _ArgToken* ArgToken;

  // Abstract interpretation state:
  struct SlotState {
    BasicType _type;
    ArgToken  _arg;
    SlotState() : _type(), _arg() {}
  };
  static SlotState make_state(BasicType type, ArgToken arg) {
    SlotState ss;
    ss._type = type; ss._arg = arg;
    return ss;
  }

private:
  MethodHandleChain _chain;

  GrowableArray<SlotState> _outgoing;  // current outgoing parameter slots
  int                      _outgoing_argc;  // # non-empty outgoing slots

  // Replace a value of type old_type at slot (and maybe slot+1) with the new value.
  // If old_type != T_VOID, remove the old argument at that point.
  // If new_type != T_VOID, insert the new argument at that point.
  // Insert or delete a second empty slot as needed.
  void change_argument(BasicType old_type, int slot, BasicType new_type, ArgToken new_arg);

  SlotState* slot_state(int slot) {
    if (slot < 0 || slot >= _outgoing.length())
      return NULL;
    return _outgoing.adr_at(slot);
  }
  BasicType slot_type(int slot) {
    SlotState* ss = slot_state(slot);
    if (ss == NULL)
      return T_ILLEGAL;
    return ss->_type;
  }
  bool slot_has_argument(int slot) {
    return slot_type(slot) < T_VOID;
  }

#ifdef ASSERT
  int argument_count_slow();
#endif

  // Return a bytecode for converting src to dest, if one exists.
  Bytecodes::Code conversion_code(BasicType src, BasicType dest);

  void walk_incoming_state(TRAPS);

public:
  MethodHandleWalker(Handle root, TRAPS)
    : _chain(root, THREAD),
      _outgoing(THREAD, 10),
      _outgoing_argc(0)
  { }

  MethodHandleChain& chain() { return _chain; }

  // plug-in abstract interpretation steps:
  virtual ArgToken make_parameter( BasicType type, klassOop tk, int argnum, TRAPS ) = 0;
  virtual ArgToken make_prim_constant( BasicType type, jvalue* con, TRAPS ) = 0;
  virtual ArgToken make_oop_constant( oop con, TRAPS ) = 0;
  virtual ArgToken make_conversion( BasicType type, klassOop tk, Bytecodes::Code op, ArgToken src, TRAPS ) = 0;
  virtual ArgToken make_fetch( BasicType type, klassOop tk, Bytecodes::Code op, ArgToken base, ArgToken offset, TRAPS ) = 0;
  virtual ArgToken make_invoke( methodOop m, vmIntrinsics::ID iid, Bytecodes::Code op, bool tailcall, int argc, ArgToken* argv, TRAPS ) = 0;

  // For make_invoke, the methodOop can be NULL if the intrinsic ID
  // is something other than vmIntrinsics::_none.

  // and in case anyone cares to related the previous actions to the chain:
  virtual void set_method_handle(oop mh) { }

  void lose(const char* msg, TRAPS) { chain().lose(msg, THREAD); }
  const char* lose_message()        { return chain().lose_message(); }

  ArgToken walk(TRAPS);
};


// An abstract interpreter for method handle chains.
// Produces an account of the semantics of a chain, in terms of a static IR.
// The IR happens to be JVM bytecodes.
class MethodHandleCompiler : public MethodHandleWalker {
private:
  Thread* _thread;

  struct PrimCon {
    BasicType _type;
    jvalue    _value;
  };

  // Accumulated compiler state:
  stringStream _bytes;
  GrowableArray<Handle>   _constant_oops;
  GrowableArray<PrimCon*> _constant_prims;
  int _max_stack;
  int _num_params;
  int _max_locals;
  int _name_index;
  int _signature_index;

  // Stack values:
  enum TokenType {
    tt_void,
    tt_parameter,
    tt_temporary,
    tt_constant
  };

  ArgToken make_stack_value(TokenType tt, BasicType type, int id) {
    return ArgToken( ((intptr_t)id << 8) | ((intptr_t)type << 4) | (intptr_t)tt );
  }

public:
  virtual ArgToken make_parameter(BasicType type, klassOop tk, int argnum, TRAPS) {
    return make_stack_value(tt_parameter, type, argnum);
  }
  virtual ArgToken make_oop_constant(oop con, TRAPS) {
    return make_stack_value(tt_constant, T_OBJECT, find_oop_constant(con));
  }
  virtual ArgToken make_prim_constant(BasicType type, jvalue* con, TRAPS) {
    return make_stack_value(tt_constant, type, find_prim_constant(type, con));
  }
  virtual ArgToken make_conversion(BasicType type, klassOop tk, Bytecodes::Code op, ArgToken src, TRAPS);
  virtual ArgToken make_fetch(BasicType type, klassOop tk, Bytecodes::Code op, ArgToken base, ArgToken offset, TRAPS);
  virtual ArgToken make_invoke(methodOop m, vmIntrinsics::ID iid, Bytecodes::Code op, bool tailcall, int argc, ArgToken* argv, TRAPS);

  int find_oop_constant(oop con);
  int find_prim_constant(BasicType type, jvalue* con);

public:
  MethodHandleCompiler(Handle root, TRAPS)
    : MethodHandleWalker(root, THREAD),
      _thread(THREAD),
      _bytes(50),
      _constant_oops(THREAD, 10),
      _constant_prims(THREAD, 10),
      _max_stack(0), _max_locals(0),
      _name_index(0), _signature_index(0)
  { }
  const char* bytes()         { return _bytes.as_string(); }
  int constant_length()       { return _constant_oops.length(); }
  int max_stack()             { return _max_stack; }
  int max_locals()            { return _max_locals; }
  int name_index()            { return _name_index; }
  int signature_index()       { return _signature_index; }
  symbolHandle name()         { return symbolHandle(_thread, (symbolOop)constant_oop_at(_name_index)()); }
  symbolHandle signature()    { return symbolHandle(_thread, (symbolOop)constant_oop_at(_signature_index)()); }

  bool constant_is_oop_at(int i) {
    return (_constant_prims.at(i) == NULL);
  }
  Handle constant_oop_at(int i) {
    assert(constant_is_oop_at(i), "");
    return _constant_oops.at(i);
  }
  PrimCon* constant_prim_at(int i) {
    assert(!constant_is_oop_at(i), "");
    return _constant_prims.at(i);
  }

  // Compile the given MH chain into bytecode.
  void compile(TRAPS);
};
