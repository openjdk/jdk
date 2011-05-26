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

#ifndef SHARE_VM_PRIMS_METHODHANDLEWALK_HPP
#define SHARE_VM_PRIMS_METHODHANDLEWALK_HPP

#include "prims/methodHandles.hpp"

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

  oop MethodHandle_type_oop()          { return java_lang_invoke_MethodHandle::type(method_handle_oop()); }
  oop MethodHandle_vmtarget_oop()      { return java_lang_invoke_MethodHandle::vmtarget(method_handle_oop()); }
  int MethodHandle_vmslots()           { return java_lang_invoke_MethodHandle::vmslots(method_handle_oop()); }
  int DirectMethodHandle_vmindex()     { return java_lang_invoke_DirectMethodHandle::vmindex(method_handle_oop()); }
  oop BoundMethodHandle_argument_oop() { return java_lang_invoke_BoundMethodHandle::argument(method_handle_oop()); }
  int BoundMethodHandle_vmargslot()    { return java_lang_invoke_BoundMethodHandle::vmargslot(method_handle_oop()); }
  int AdapterMethodHandle_conversion() { return java_lang_invoke_AdapterMethodHandle::conversion(method_handle_oop()); }

#ifdef ASSERT
  void print_impl(TRAPS);
#endif

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
  oop    vmtarget_oop()         { return MethodHandle_vmtarget_oop(); }

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

#ifdef ASSERT
  // Print a symbolic description of a method handle chain, including
  // the signature for each method.  The signatures are printed in
  // slot order to make it easier to understand.
  void print();
  static void print(Handle mh);
#endif
};


// Structure walker for method handles.
// Does abstract interpretation on top of low-level parsing.
// You supply the tokens shuffled by the abstract interpretation.
class MethodHandleWalker : StackObj {
public:
  // Stack values:
  enum TokenType {
    tt_void,
    tt_parameter,
    tt_temporary,
    tt_constant,
    tt_symbolic,
    tt_illegal
  };

  // Argument token:
  class ArgToken {
  private:
    TokenType _tt;
    BasicType _bt;
    jvalue    _value;
    Handle    _handle;

  public:
    ArgToken(TokenType tt = tt_illegal) : _tt(tt), _bt(tt == tt_void ? T_VOID : T_ILLEGAL) {
      assert(tt == tt_illegal || tt == tt_void, "invalid token type");
    }

    ArgToken(TokenType tt, BasicType bt, int index) : _tt(tt), _bt(bt) {
      assert(_tt == tt_parameter || _tt == tt_temporary, "must have index");
      _value.i = index;
    }

    ArgToken(BasicType bt, jvalue value) : _tt(tt_constant), _bt(bt), _value(value) { assert(_bt != T_OBJECT, "wrong constructor"); }
    ArgToken(Handle handle) : _tt(tt_constant), _bt(T_OBJECT), _handle(handle) {}


    ArgToken(const char* str, BasicType type) : _tt(tt_symbolic), _bt(type) {
      _value.j = (intptr_t)str;
    }

    TokenType token_type()  const { return _tt; }
    BasicType basic_type()  const { return _bt; }
    bool      has_index()   const { return _tt == tt_parameter || _tt == tt_temporary; }
    int       index()       const { assert(has_index(), "must have index");; return _value.i; }
    Handle    object()      const { assert(_bt == T_OBJECT, "wrong accessor"); assert(_tt == tt_constant, "value type"); return _handle; }
    const char* str()       const { assert(_tt == tt_symbolic, "string type"); return (const char*)(intptr_t)_value.j; }

    jint      get_jint()    const { assert(_bt == T_INT || is_subword_type(_bt), "wrong accessor"); assert(_tt == tt_constant, "value types"); return _value.i; }
    jlong     get_jlong()   const { assert(_bt == T_LONG, "wrong accessor");   assert(_tt == tt_constant, "value types"); return _value.j; }
    jfloat    get_jfloat()  const { assert(_bt == T_FLOAT, "wrong accessor");  assert(_tt == tt_constant, "value types"); return _value.f; }
    jdouble   get_jdouble() const { assert(_bt == T_DOUBLE, "wrong accessor"); assert(_tt == tt_constant, "value types"); return _value.d; }
  };

private:
  MethodHandleChain _chain;
  bool              _for_invokedynamic;
  int               _local_index;

  // This array is kept in an unusual order, indexed by low-level "slot number".
  // TOS is always _outgoing.at(0), so simple pushes and pops shift the whole _outgoing array.
  // If there is a receiver in the current argument list, it is at _outgoing.at(_outgoing.length()-1).
  // If a value at _outgoing.at(n) is T_LONG or T_DOUBLE, the value at _outgoing.at(n+1) is T_VOID.
  GrowableArray<ArgToken>  _outgoing;       // current outgoing parameter slots
  int                      _outgoing_argc;  // # non-empty outgoing slots

  // Replace a value of type old_type at slot (and maybe slot+1) with the new value.
  // If old_type != T_VOID, remove the old argument at that point.
  // If new_type != T_VOID, insert the new argument at that point.
  // Insert or delete a second empty slot as needed.
  void change_argument(BasicType old_type, int slot, const ArgToken& new_arg);
  void change_argument(BasicType old_type, int slot, BasicType type, const ArgToken& new_arg) {
    assert(type == new_arg.basic_type(), "must agree");
    change_argument(old_type, slot, new_arg);
  }

  // Raw retype conversions for OP_RAW_RETYPE.
  void retype_raw_conversion(BasicType src, BasicType dst, bool for_return, int slot, TRAPS);
  void retype_raw_argument_type(BasicType src, BasicType dst, int slot, TRAPS) { retype_raw_conversion(src, dst, false, slot, CHECK); }
  void retype_raw_return_type(  BasicType src, BasicType dst,           TRAPS) { retype_raw_conversion(src, dst, true,  -1,   CHECK); }

  BasicType arg_type(int slot) {
    return _outgoing.at(slot).basic_type();
  }
  bool has_argument(int slot) {
    return arg_type(slot) < T_VOID;
  }

#ifdef ASSERT
  int argument_count_slow();
#endif

  // Return a bytecode for converting src to dest, if one exists.
  Bytecodes::Code conversion_code(BasicType src, BasicType dest);

  void walk_incoming_state(TRAPS);

  void verify_args_and_signature(TRAPS) NOT_DEBUG_RETURN;

public:
  MethodHandleWalker(Handle root, bool for_invokedynamic, TRAPS)
    : _chain(root, THREAD),
      _for_invokedynamic(for_invokedynamic),
      _outgoing(THREAD, 10),
      _outgoing_argc(0)
  {
    _local_index = for_invokedynamic ? 0 : 1;
  }

  MethodHandleChain& chain() { return _chain; }

  bool for_invokedynamic() const { return _for_invokedynamic; }

  int new_local_index(BasicType bt) {
    //int index = _for_invokedynamic ? _local_index : _local_index - 1;
    int index = _local_index;
    _local_index += type2size[bt];
    return index;
  }

  int max_locals() const { return _local_index; }

  // plug-in abstract interpretation steps:
  virtual ArgToken make_parameter(BasicType type, klassOop tk, int argnum, TRAPS) = 0;
  virtual ArgToken make_prim_constant(BasicType type, jvalue* con, TRAPS) = 0;
  virtual ArgToken make_oop_constant(oop con, TRAPS) = 0;
  virtual ArgToken make_conversion(BasicType type, klassOop tk, Bytecodes::Code op, const ArgToken& src, TRAPS) = 0;
  virtual ArgToken make_fetch(BasicType type, klassOop tk, Bytecodes::Code op, const ArgToken& base, const ArgToken& offset, TRAPS) = 0;
  virtual ArgToken make_invoke(methodOop m, vmIntrinsics::ID iid, Bytecodes::Code op, bool tailcall, int argc, ArgToken* argv, TRAPS) = 0;

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
  int          _invoke_count;  // count the original call site has been executed
  KlassHandle  _rklass;        // Return type for casting.
  BasicType    _rtype;
  KlassHandle  _target_klass;
  Thread*      _thread;

  // Values used by the compiler.
  static jvalue zero_jvalue;
  static jvalue one_jvalue;

  // Fake constant pool entry.
  class ConstantValue {
  private:
    int       _tag;   // Constant pool tag type.
    JavaValue _value;
    Handle    _handle;
    Symbol*   _sym;

  public:
    // Constructor for oop types.
    ConstantValue(int tag, Handle con) : _tag(tag), _handle(con) {
      assert(tag == JVM_CONSTANT_Class  ||
             tag == JVM_CONSTANT_String ||
             tag == JVM_CONSTANT_Object, "must be oop type");
    }

    ConstantValue(int tag, Symbol* con) : _tag(tag), _sym(con) {
      assert(tag == JVM_CONSTANT_Utf8, "must be symbol type");
    }

    // Constructor for oop reference types.
    ConstantValue(int tag, int index) : _tag(tag) {
      assert(JVM_CONSTANT_Fieldref <= tag && tag <= JVM_CONSTANT_NameAndType, "must be ref type");
      _value.set_jint(index);
    }
    ConstantValue(int tag, int first_index, int second_index) : _tag(tag) {
      assert(JVM_CONSTANT_Fieldref <= tag && tag <= JVM_CONSTANT_NameAndType, "must be ref type");
      _value.set_jint(first_index << 16 | second_index);
    }

    // Constructor for primitive types.
    ConstantValue(BasicType bt, jvalue con) {
      _value.set_type(bt);
      switch (bt) {
      case T_INT:    _tag = JVM_CONSTANT_Integer; _value.set_jint(   con.i); break;
      case T_LONG:   _tag = JVM_CONSTANT_Long;    _value.set_jlong(  con.j); break;
      case T_FLOAT:  _tag = JVM_CONSTANT_Float;   _value.set_jfloat( con.f); break;
      case T_DOUBLE: _tag = JVM_CONSTANT_Double;  _value.set_jdouble(con.d); break;
      default: ShouldNotReachHere();
      }
    }

    int       tag()          const { return _tag; }
    Symbol*   symbol()       const { return _sym; }
    klassOop  klass_oop()    const { return (klassOop)  _handle(); }
    oop       object_oop()   const { return _handle(); }
    int       index()        const { return _value.get_jint(); }
    int       first_index()  const { return _value.get_jint() >> 16; }
    int       second_index() const { return _value.get_jint() & 0x0000FFFF; }

    bool      is_primitive() const { return is_java_primitive(_value.get_type()); }
    jint      get_jint()     const { return _value.get_jint();    }
    jlong     get_jlong()    const { return _value.get_jlong();   }
    jfloat    get_jfloat()   const { return _value.get_jfloat();  }
    jdouble   get_jdouble()  const { return _value.get_jdouble(); }
  };

  // Fake constant pool.
  GrowableArray<ConstantValue*> _constants;

  // Accumulated compiler state:
  GrowableArray<unsigned char> _bytecode;

  int _cur_stack;
  int _max_stack;
  int _num_params;
  int _name_index;
  int _signature_index;

  void stack_push(BasicType bt) {
    _cur_stack += type2size[bt];
    if (_cur_stack > _max_stack) _max_stack = _cur_stack;
  }
  void stack_pop(BasicType bt) {
    _cur_stack -= type2size[bt];
    assert(_cur_stack >= 0, "sanity");
  }

  unsigned char* bytecode()        const { return _bytecode.adr_at(0); }
  int            bytecode_length() const { return _bytecode.length(); }

  // Fake constant pool.
  int cpool_oop_put(int tag, Handle con) {
    if (con.is_null())  return 0;
    ConstantValue* cv = new ConstantValue(tag, con);
    return _constants.append(cv);
  }

  int cpool_symbol_put(int tag, Symbol* con) {
    if (con == NULL)  return 0;
    ConstantValue* cv = new ConstantValue(tag, con);
    con->increment_refcount();
    return _constants.append(cv);
  }

  int cpool_oop_reference_put(int tag, int first_index, int second_index) {
    if (first_index == 0 && second_index == 0)  return 0;
    assert(first_index != 0 && second_index != 0, "no zero indexes");
    ConstantValue* cv = new ConstantValue(tag, first_index, second_index);
    return _constants.append(cv);
  }

  int cpool_primitive_put(BasicType type, jvalue* con);

  int cpool_int_put(jint value) {
    jvalue con; con.i = value;
    return cpool_primitive_put(T_INT, &con);
  }
  int cpool_long_put(jlong value) {
    jvalue con; con.j = value;
    return cpool_primitive_put(T_LONG, &con);
  }
  int cpool_float_put(jfloat value) {
    jvalue con; con.f = value;
    return cpool_primitive_put(T_FLOAT, &con);
  }
  int cpool_double_put(jdouble value) {
    jvalue con; con.d = value;
    return cpool_primitive_put(T_DOUBLE, &con);
  }

  int cpool_object_put(Handle obj) {
    return cpool_oop_put(JVM_CONSTANT_Object, obj);
  }
  int cpool_symbol_put(Symbol* sym) {
    return cpool_symbol_put(JVM_CONSTANT_Utf8, sym);
  }
  int cpool_klass_put(klassOop klass) {
    return cpool_oop_put(JVM_CONSTANT_Class, klass);
  }
  int cpool_methodref_put(int class_index, int name_and_type_index) {
    return cpool_oop_reference_put(JVM_CONSTANT_Methodref, class_index, name_and_type_index);
  }
  int cpool_name_and_type_put(int name_index, int signature_index) {
    return cpool_oop_reference_put(JVM_CONSTANT_NameAndType, name_index, signature_index);
  }

  void emit_bc(Bytecodes::Code op, int index = 0, int args_size = -1);
  void emit_load(BasicType bt, int index);
  void emit_store(BasicType bt, int index);
  void emit_load_constant(ArgToken arg);

  virtual ArgToken make_parameter(BasicType type, klassOop tk, int argnum, TRAPS) {
    return ArgToken(tt_parameter, type, argnum);
  }
  virtual ArgToken make_oop_constant(oop con, TRAPS) {
    Handle h(THREAD, con);
    return ArgToken(h);
  }
  virtual ArgToken make_prim_constant(BasicType type, jvalue* con, TRAPS) {
    return ArgToken(type, *con);
  }

  virtual ArgToken make_conversion(BasicType type, klassOop tk, Bytecodes::Code op, const ArgToken& src, TRAPS);
  virtual ArgToken make_fetch(BasicType type, klassOop tk, Bytecodes::Code op, const ArgToken& base, const ArgToken& offset, TRAPS);
  virtual ArgToken make_invoke(methodOop m, vmIntrinsics::ID iid, Bytecodes::Code op, bool tailcall, int argc, ArgToken* argv, TRAPS);

  // Get a real constant pool.
  constantPoolHandle get_constant_pool(TRAPS) const;

  // Get a real methodOop.
  methodHandle get_method_oop(TRAPS) const;

public:
  MethodHandleCompiler(Handle root, Symbol* name, Symbol* signature, int invoke_count, bool for_invokedynamic, TRAPS);

  // Compile the given MH chain into bytecode.
  methodHandle compile(TRAPS);

  // Tests if the given class is a MH adapter holder.
  static bool klass_is_method_handle_adapter_holder(klassOop klass) {
    return (klass == SystemDictionary::MethodHandle_klass());
  }
};

#endif // SHARE_VM_PRIMS_METHODHANDLEWALK_HPP
