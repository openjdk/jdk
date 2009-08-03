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

typedef class BytecodeInterpreter* interpreterState;

class CodeBlob;


// A frame represents a physical stack frame (an activation).  Frames
// can be C or Java frames, and the Java frames can be interpreted or
// compiled.  In contrast, vframes represent source-level activations,
// so that one physical frame can correspond to multiple source level
// frames because of inlining.

class frame VALUE_OBJ_CLASS_SPEC {
 private:
  // Instance variables:
  intptr_t* _sp; // stack pointer (from Thread::last_Java_sp)
  address   _pc; // program counter (the next instruction after the call)

  CodeBlob* _cb; // CodeBlob that "owns" pc
  enum deopt_state {
    not_deoptimized,
    is_deoptimized,
    unknown
  };

  deopt_state _deopt_state;

 public:
  // Constructors
  frame();

  // Accessors

  // pc: Returns the pc at which this frame will continue normally.
  // It must point at the beginning of the next instruction to execute.
  address pc() const             { return _pc; }

  // This returns the pc that if you were in the debugger you'd see. Not
  // the idealized value in the frame object. This undoes the magic conversion
  // that happens for deoptimized frames. In addition it makes the value the
  // hardware would want to see in the native frame. The only user (at this point)
  // is deoptimization. It likely no one else should ever use it.
  address raw_pc() const;

  void set_pc( address   newpc );

  intptr_t* sp() const           { return _sp; }
  void set_sp( intptr_t* newsp ) { _sp = newsp; }


  CodeBlob* cb() const           { return _cb; }

  // patching operations
  void   patch_pc(Thread* thread, address pc);

  // Every frame needs to return a unique id which distinguishes it from all other frames.
  // For sparc and ia32 use sp. ia64 can have memory frames that are empty so multiple frames
  // will have identical sp values. For ia64 the bsp (fp) value will serve. No real frame
  // should have an id() of NULL so it is a distinguishing value for an unmatchable frame.
  // We also have relationals which allow comparing a frame to anoth frame's id() allow
  // us to distinguish younger (more recent activation) from older (less recent activations)
  // A NULL id is only valid when comparing for equality.

  intptr_t* id(void) const;
  bool is_younger(intptr_t* id) const;
  bool is_older(intptr_t* id) const;

  // testers

  // Compares for strict equality. Rarely used or needed.
  // It can return a different result than f1.id() == f2.id()
  bool equal(frame other) const;

  // type testers
  bool is_interpreted_frame()    const;
  bool is_java_frame()           const;
  bool is_entry_frame()          const;             // Java frame called from C?
  bool is_native_frame()         const;
  bool is_runtime_frame()        const;
  bool is_compiled_frame()       const;
  bool is_safepoint_blob_frame() const;
  bool is_deoptimized_frame()    const;

  // testers
  bool is_first_frame() const; // oldest frame? (has no sender)
  bool is_first_java_frame() const;              // same for Java frame

  bool is_interpreted_frame_valid(JavaThread* thread) const;       // performs sanity checks on interpreted frames.

  // tells whether this frame is marked for deoptimization
  bool should_be_deoptimized() const;

  // tells whether this frame can be deoptimized
  bool can_be_deoptimized() const;

  // returns the frame size in stack slots
  int frame_size(RegisterMap* map) const;

  // returns the sending frame
  frame sender(RegisterMap* map) const;

  // for Profiling - acting on another frame. walks sender frames
  // if valid.
  frame profile_find_Java_sender_frame(JavaThread *thread);
  bool safe_for_sender(JavaThread *thread);

  // returns the sender, but skips conversion frames
  frame real_sender(RegisterMap* map) const;

  // returns the the sending Java frame, skipping any intermediate C frames
  // NB: receiver must not be first frame
  frame java_sender() const;

 private:
  // Helper methods for better factored code in frame::sender
  frame sender_for_compiled_frame(RegisterMap* map) const;
  frame sender_for_entry_frame(RegisterMap* map) const;
  frame sender_for_interpreter_frame(RegisterMap* map) const;
  frame sender_for_native_frame(RegisterMap* map) const;

  // All frames:

  // A low-level interface for vframes:

 public:

  intptr_t* addr_at(int index) const             { return &fp()[index];    }
  intptr_t  at(int index) const                  { return *addr_at(index); }

  // accessors for locals
  oop obj_at(int offset) const                   { return *obj_at_addr(offset);  }
  void obj_at_put(int offset, oop value)         { *obj_at_addr(offset) = value; }

  jint int_at(int offset) const                  { return *int_at_addr(offset);  }
  void int_at_put(int offset, jint value)        { *int_at_addr(offset) = value; }

  oop*      obj_at_addr(int offset) const        { return (oop*)     addr_at(offset); }

  oop*      adjusted_obj_at_addr(methodOop method, int index) { return obj_at_addr(adjust_offset(method, index)); }

 private:
  jint*    int_at_addr(int offset) const         { return (jint*)    addr_at(offset); }

 public:
  // Link (i.e., the pointer to the previous frame)
  intptr_t* link() const;
  void set_link(intptr_t* addr);

  // Return address
  address  sender_pc() const;

  // Support for deoptimization
  void deoptimize(JavaThread* thread, bool thread_is_known_safe = false);

  // The frame's original SP, before any extension by an interpreted callee;
  // used for packing debug info into vframeArray objects and vframeArray lookup.
  intptr_t* unextended_sp() const;

  // returns the stack pointer of the calling frame
  intptr_t* sender_sp() const;


  // Interpreter frames:

 private:
  intptr_t** interpreter_frame_locals_addr() const;
  intptr_t*  interpreter_frame_bcx_addr() const;
  intptr_t*  interpreter_frame_mdx_addr() const;

 public:
  // Tags for TaggedStackInterpreter
  enum Tag {
      TagValue = 0,          // Important: must be zero to use G0 on sparc.
      TagReference = 0x555,  // Reference type - is an oop that needs gc.
      TagCategory2 = 0x666   // Only used internally by interpreter
                             // and not written to the java stack.
      // The values above are chosen so that misuse causes a crash
      // with a recognizable value.
  };

  static Tag tag_for_basic_type(BasicType typ) {
    return (typ == T_OBJECT ? TagReference : TagValue);
  }

  // Locals

  // The _at version returns a pointer because the address is used for GC.
  intptr_t* interpreter_frame_local_at(int index) const;
  Tag       interpreter_frame_local_tag(int index) const;
  void      interpreter_frame_set_local_tag(int index, Tag tag) const;

  void interpreter_frame_set_locals(intptr_t* locs);

  // byte code index/pointer (use these functions for unchecked frame access only!)
  intptr_t interpreter_frame_bcx() const                  { return *interpreter_frame_bcx_addr(); }
  void interpreter_frame_set_bcx(intptr_t bcx);

  // byte code index
  jint interpreter_frame_bci() const;
  void interpreter_frame_set_bci(jint bci);

  // byte code pointer
  address interpreter_frame_bcp() const;
  void    interpreter_frame_set_bcp(address bcp);

  // Unchecked access to the method data index/pointer.
  // Only use this if you know what you are doing.
  intptr_t interpreter_frame_mdx() const                  { return *interpreter_frame_mdx_addr(); }
  void interpreter_frame_set_mdx(intptr_t mdx);

  // method data pointer
  address interpreter_frame_mdp() const;
  void    interpreter_frame_set_mdp(address dp);

  // Find receiver out of caller's (compiled) argument list
  oop retrieve_receiver(RegisterMap *reg_map);

  // Return the monitor owner and BasicLock for compiled synchronized
  // native methods so that biased locking can revoke the receiver's
  // bias if necessary. Takes optional nmethod for this frame as
  // argument to avoid performing repeated lookups in code cache.
  BasicLock* compiled_synchronized_native_monitor      (nmethod* nm = NULL);
  oop        compiled_synchronized_native_monitor_owner(nmethod* nm = NULL);

  // Find receiver for an invoke when arguments are just pushed on stack (i.e., callee stack-frame is
  // not setup)
  oop interpreter_callee_receiver(symbolHandle signature)     { return *interpreter_callee_receiver_addr(signature); }


  oop* interpreter_callee_receiver_addr(symbolHandle signature);


  // expression stack (may go up or down, direction == 1 or -1)
 public:
  intptr_t* interpreter_frame_expression_stack() const;
  static  jint  interpreter_frame_expression_stack_direction();

  // The _at version returns a pointer because the address is used for GC.
  intptr_t* interpreter_frame_expression_stack_at(jint offset) const;
  Tag       interpreter_frame_expression_stack_tag(jint offset) const;
  void      interpreter_frame_set_expression_stack_tag(jint offset, Tag tag) const;

  // top of expression stack
  intptr_t* interpreter_frame_tos_at(jint offset) const;
  intptr_t* interpreter_frame_tos_address() const;


  jint  interpreter_frame_expression_stack_size() const;

  intptr_t* interpreter_frame_sender_sp() const;

#ifndef CC_INTERP
  // template based interpreter deoptimization support
  void  set_interpreter_frame_sender_sp(intptr_t* sender_sp);
  void interpreter_frame_set_monitor_end(BasicObjectLock* value);
#endif // CC_INTERP

  // BasicObjectLocks:
  //
  // interpreter_frame_monitor_begin is higher in memory than interpreter_frame_monitor_end
  // Interpreter_frame_monitor_begin points to one element beyond the oldest one,
  // interpreter_frame_monitor_end   points to the youngest one, or if there are none,
  //                                 it points to one beyond where the first element will be.
  // interpreter_frame_monitor_size  reports the allocation size of a monitor in the interpreter stack.
  //                                 this value is >= BasicObjectLock::size(), and may be rounded up

  BasicObjectLock* interpreter_frame_monitor_begin() const;
  BasicObjectLock* interpreter_frame_monitor_end()   const;
  BasicObjectLock* next_monitor_in_interpreter_frame(BasicObjectLock* current) const;
  BasicObjectLock* previous_monitor_in_interpreter_frame(BasicObjectLock* current) const;
  static int interpreter_frame_monitor_size();

  void interpreter_frame_verify_monitor(BasicObjectLock* value) const;

  // Tells whether the current interpreter_frame frame pointer
  // corresponds to the old compiled/deoptimized fp
  // The receiver used to be a top level frame
  bool interpreter_frame_equals_unpacked_fp(intptr_t* fp);

  // Return/result value from this interpreter frame
  // If the method return type is T_OBJECT or T_ARRAY populates oop_result
  // For other (non-T_VOID) the appropriate field in the jvalue is populated
  // with the result value.
  // Should only be called when at method exit when the method is not
  // exiting due to an exception.
  BasicType interpreter_frame_result(oop* oop_result, jvalue* value_result);

 public:
  // Method & constant pool cache
  methodOop interpreter_frame_method() const;
  void interpreter_frame_set_method(methodOop method);
  methodOop* interpreter_frame_method_addr() const;
  constantPoolCacheOop* interpreter_frame_cache_addr() const;

 public:
  // Entry frames
  JavaCallWrapper* entry_frame_call_wrapper() const;
  intptr_t* entry_frame_argument_at(int offset) const;

  // tells whether there is another chunk of Delta stack above
  bool entry_frame_is_first() const;

  // Compiled frames:

 public:
  // Given the index of a local, and the number of argument words
  // in this stack frame, tell which word of the stack frame to find
  // the local in.  Arguments are stored above the ofp/rpc pair,
  // while other locals are stored below it.
  // Since monitors (BasicLock blocks) are also assigned indexes,
  // but may have different storage requirements, their presence
  // can also affect the calculation of offsets.
  static int local_offset_for_compiler(int local_index, int nof_args, int max_nof_locals, int max_nof_monitors);

  // Given the index of a monitor, etc., tell which word of the
  // stack frame contains the start of the BasicLock block.
  // Note that the local index by convention is the __higher__
  // of the two indexes allocated to the block.
  static int monitor_offset_for_compiler(int local_index, int nof_args, int max_nof_locals, int max_nof_monitors);

  // Tell the smallest value that local_offset_for_compiler will attain.
  // This is used to help determine how much stack frame to allocate.
  static int min_local_offset_for_compiler(int nof_args, int max_nof_locals, int max_nof_monitors);

  // Tells if this register must be spilled during a call.
  // On Intel, all registers are smashed by calls.
  static bool volatile_across_calls(Register reg);


  // Safepoints

 public:
  oop saved_oop_result(RegisterMap* map) const;
  void set_saved_oop_result(RegisterMap* map, oop obj);

  // For debugging
 private:
  const char* print_name() const;

 public:
  void print_value() const { print_value_on(tty,NULL); }
  void print_value_on(outputStream* st, JavaThread *thread) const;
  void print_on(outputStream* st) const;
  void interpreter_frame_print_on(outputStream* st) const;
  void print_on_error(outputStream* st, char* buf, int buflen, bool verbose = false) const;

  // Conversion from an VMReg to physical stack location
  oop* oopmapreg_to_location(VMReg reg, const RegisterMap* regmap) const;

  // Oops-do's
  void oops_compiled_arguments_do(symbolHandle signature, bool is_static, const RegisterMap* reg_map, OopClosure* f);
  void oops_interpreted_do(OopClosure* f, const RegisterMap* map, bool query_oop_map_cache = true);

 private:
  void oops_interpreted_locals_do(OopClosure *f,
                                 int max_locals,
                                 InterpreterOopMap *mask);
  void oops_interpreted_expressions_do(OopClosure *f, symbolHandle signature,
                                 bool is_static, int max_stack, int max_locals,
                                 InterpreterOopMap *mask);
  void oops_interpreted_arguments_do(symbolHandle signature, bool is_static, OopClosure* f);

  // Iteration of oops
  void oops_do_internal(OopClosure* f, RegisterMap* map, bool use_interpreter_oop_map_cache);
  void oops_entry_do(OopClosure* f, const RegisterMap* map);
  void oops_code_blob_do(OopClosure* f, const RegisterMap* map);
  int adjust_offset(methodOop method, int index); // helper for above fn
  // Iteration of nmethods
  void nmethods_code_blob_do();
 public:
  // Memory management
  void oops_do(OopClosure* f, RegisterMap* map) { oops_do_internal(f, map, true); }
  void nmethods_do();

  void gc_prologue();
  void gc_epilogue();
  void pd_gc_epilog();

# ifdef ENABLE_ZAP_DEAD_LOCALS
 private:
  class CheckValueClosure: public OopClosure {
   public:
    void do_oop(oop* p);
    void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  };
  static CheckValueClosure _check_value;

  class CheckOopClosure: public OopClosure {
   public:
    void do_oop(oop* p);
    void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  };
  static CheckOopClosure _check_oop;

  static void check_derived_oop(oop* base, oop* derived);

  class ZapDeadClosure: public OopClosure {
   public:
    void do_oop(oop* p);
    void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  };
  static ZapDeadClosure _zap_dead;

 public:
  // Zapping
  void zap_dead_locals            (JavaThread* thread, const RegisterMap* map);
  void zap_dead_interpreted_locals(JavaThread* thread, const RegisterMap* map);
  void zap_dead_compiled_locals   (JavaThread* thread, const RegisterMap* map);
  void zap_dead_entry_locals      (JavaThread* thread, const RegisterMap* map);
  void zap_dead_deoptimized_locals(JavaThread* thread, const RegisterMap* map);
# endif
  // Verification
  void verify(const RegisterMap* map);
  static bool verify_return_pc(address x);
  static bool is_bci(intptr_t bcx);
  // Usage:
  // assert(frame::verify_return_pc(return_address), "must be a return pc");

  int pd_oop_map_offset_adjustment() const;

# include "incls/_frame_pd.hpp.incl"
};


//
// StackFrameStream iterates through the frames of a thread starting from
// top most frame. It automatically takes care of updating the location of
// all (callee-saved) registers. Notice: If a thread is stopped at
// a safepoint, all registers are saved, not only the callee-saved ones.
//
// Use:
//
//   for(StackFrameStream fst(thread); !fst.is_done(); fst.next()) {
//     ...
//   }
//
class StackFrameStream : public StackObj {
 private:
  frame       _fr;
  RegisterMap _reg_map;
  bool        _is_done;
 public:
   StackFrameStream(JavaThread *thread, bool update = true);

  // Iteration
  bool is_done()                  { return (_is_done) ? true : (_is_done = _fr.is_first_frame(), false); }
  void next()                     { if (!_is_done) _fr = _fr.sender(&_reg_map); }

  // Query
  frame *current()                { return &_fr; }
  RegisterMap* register_map()     { return &_reg_map; }
};
