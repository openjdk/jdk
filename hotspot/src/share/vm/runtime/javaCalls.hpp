/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

// A JavaCallWrapper is constructed before each JavaCall and destructed after the call.
// Its purpose is to allocate/deallocate a new handle block and to save/restore the last
// Java fp/sp. A pointer to the JavaCallWrapper is stored on the stack.

class JavaCallWrapper: StackObj {
  friend class VMStructs;
 private:
  JavaThread*      _thread;                 // the thread to which this call belongs
  JNIHandleBlock*  _handles;                // the saved handle block
  methodOop        _callee_method;          // to be able to collect arguments if entry frame is top frame
  oop              _receiver;               // the receiver of the call (if a non-static call)

  JavaFrameAnchor  _anchor;                 // last thread anchor state that we must restore

  JavaValue*       _result;                 // result value

 public:
  // Construction/destruction
   JavaCallWrapper(methodHandle callee_method, Handle receiver, JavaValue* result, TRAPS);
  ~JavaCallWrapper();

  // Accessors
  JavaThread*      thread() const           { return _thread; }
  JNIHandleBlock*  handles() const          { return _handles; }

  JavaFrameAnchor* anchor(void)             { return &_anchor; }

  JavaValue*       result() const           { return _result; }
  // GC support
  methodOop        callee_method()          { return _callee_method; }
  oop              receiver()               { return _receiver; }
  void             oops_do(OopClosure* f);

};


// Encapsulates arguments to a JavaCall (faster, safer, and more convenient than using var-args)
class JavaCallArguments : public StackObj {
 private:
  enum Constants {
   _default_size = 8    // Must be at least # of arguments in JavaCalls methods
  };

  intptr_t    _value_buffer [_default_size + 1];
  bool        _is_oop_buffer[_default_size + 1];

  intptr_t*   _value;
  bool*       _is_oop;
  int         _size;
  int         _max_size;
  bool        _start_at_zero;      // Support late setting of receiver

  void initialize() {
    // Starts at first element to support set_receiver.
    _value    = &_value_buffer[1];
    _is_oop   = &_is_oop_buffer[1];

    _max_size = _default_size;
    _size = 0;
    _start_at_zero = false;
  }

 public:
  JavaCallArguments() { initialize(); }

  JavaCallArguments(Handle receiver) {
    initialize();
    push_oop(receiver);
  }

  JavaCallArguments(int max_size) {
    if (max_size > _default_size) {
      _value  = NEW_RESOURCE_ARRAY(intptr_t, max_size + 1);
      _is_oop = NEW_RESOURCE_ARRAY(bool, max_size + 1);

      // Reserve room for potential receiver in value and is_oop
      _value++; _is_oop++;

      _max_size = max_size;
      _size = 0;
      _start_at_zero = false;
    } else {
      initialize();
    }
  }

  inline void push_oop(Handle h)    { _is_oop[_size] = true;
                               JNITypes::put_obj((oop)h.raw_value(), _value, _size); }

  inline void push_int(int i)       { _is_oop[_size] = false;
                               JNITypes::put_int(i, _value, _size); }

  inline void push_double(double d) { _is_oop[_size] = false; _is_oop[_size + 1] = false;
                               JNITypes::put_double(d, _value, _size); }

  inline void push_long(jlong l)    { _is_oop[_size] = false; _is_oop[_size + 1] = false;
                               JNITypes::put_long(l, _value, _size); }

  inline void push_float(float f)   { _is_oop[_size] = false;
                               JNITypes::put_float(f, _value, _size); }

  // receiver
  Handle receiver() {
    assert(_size > 0, "must at least be one argument");
    assert(_is_oop[0], "first argument must be an oop");
    assert(_value[0] != 0, "receiver must be not-null");
    return Handle((oop*)_value[0], false);
  }

  void set_receiver(Handle h) {
    assert(_start_at_zero == false, "can only be called once");
    _start_at_zero = true;
    _is_oop--;
    _value--;
    _size++;
    _is_oop[0] = true;
    _value[0] = (intptr_t)h.raw_value();
  }

  // Converts all Handles to oops, and returns a reference to parameter vector
  intptr_t* parameters() ;
  int   size_of_parameters() const { return _size; }

  // Verify that pushed arguments fits a given method
  void verify(methodHandle method, BasicType return_type, Thread *thread);
};

// All calls to Java have to go via JavaCalls. Sets up the stack frame
// and makes sure that the last_Java_frame pointers are chained correctly.
//

class JavaCalls: AllStatic {
  static void call_helper(JavaValue* result, methodHandle* method, JavaCallArguments* args, TRAPS);
 public:
  // Optimized Constuctor call
  static void call_default_constructor(JavaThread* thread, methodHandle method, Handle receiver, TRAPS);

  // call_special
  // ------------
  // The receiver must be first oop in argument list
  static void call_special(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, JavaCallArguments* args, TRAPS);

  static void call_special(JavaValue* result, Handle receiver, KlassHandle klass, symbolHandle name, symbolHandle signature, TRAPS); // No args
  static void call_special(JavaValue* result, Handle receiver, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, TRAPS);
  static void call_special(JavaValue* result, Handle receiver, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, Handle arg2, TRAPS);

  // virtual call
  // ------------

  // The receiver must be first oop in argument list
  static void call_virtual(JavaValue* result, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, JavaCallArguments* args, TRAPS);

  static void call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, TRAPS); // No args
  static void call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, Handle arg1, TRAPS);
  static void call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, Handle arg1, Handle arg2, TRAPS);

  // Static call
  // -----------
  static void call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, JavaCallArguments* args, TRAPS);

  static void call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, TRAPS);
  static void call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, TRAPS);
  static void call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, Handle arg2, TRAPS);

  // Low-level interface
  static void call(JavaValue* result, methodHandle method, JavaCallArguments* args, TRAPS);
};
