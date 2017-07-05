/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

class FastLockNode;
class FastUnlockNode;
class IdealKit;
class Parse;
class RootNode;

//-----------------------------------------------------------------------------
//----------------------------GraphKit-----------------------------------------
// Toolkit for building the common sorts of subgraphs.
// Does not know about bytecode parsing or type-flow results.
// It is able to create graphs implementing the semantics of most
// or all bytecodes, so that it can expand intrinsics and calls.
// It may depend on JVMState structure, but it must not depend
// on specific bytecode streams.
class GraphKit : public Phase {
  friend class PreserveJVMState;

 protected:
  ciEnv*            _env;       // Compilation environment
  PhaseGVN         &_gvn;       // Some optimizations while parsing
  SafePointNode*    _map;       // Parser map from JVM to Nodes
  SafePointNode*    _exceptions;// Parser map(s) for exception state(s)
  int               _sp;        // JVM Expression Stack Pointer
  int               _bci;       // JVM Bytecode Pointer
  ciMethod*         _method;    // JVM Current Method

 private:
  SafePointNode*     map_not_null() const {
    assert(_map != NULL, "must call stopped() to test for reset compiler map");
    return _map;
  }

 public:
  GraphKit();                   // empty constructor
  GraphKit(JVMState* jvms);     // the JVM state on which to operate

#ifdef ASSERT
  ~GraphKit() {
    assert(!has_exceptions(), "user must call transfer_exceptions_into_jvms");
  }
#endif

  virtual Parse* is_Parse() const { return NULL; }

  ciEnv*        env()           const { return _env; }
  PhaseGVN&     gvn()           const { return _gvn; }

  void record_for_igvn(Node* n) const { C->record_for_igvn(n); }  // delegate to Compile

  // Handy well-known nodes:
  Node*         null()          const { return zerocon(T_OBJECT); }
  Node*         top()           const { return C->top(); }
  RootNode*     root()          const { return C->root(); }

  // Create or find a constant node
  Node* intcon(jint con)        const { return _gvn.intcon(con); }
  Node* longcon(jlong con)      const { return _gvn.longcon(con); }
  Node* makecon(const Type *t)  const { return _gvn.makecon(t); }
  Node* zerocon(BasicType bt)   const { return _gvn.zerocon(bt); }
  // (See also macro MakeConX in type.hpp, which uses intcon or longcon.)

  // Helper for byte_map_base
  Node* byte_map_base_node() {
    // Get base of card map
    CardTableModRefBS* ct = (CardTableModRefBS*)(Universe::heap()->barrier_set());
    assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust users of this code");
    if (ct->byte_map_base != NULL) {
      return makecon(TypeRawPtr::make((address)ct->byte_map_base));
    } else {
      return null();
    }
  }

  jint  find_int_con(Node* n, jint value_if_unknown) {
    return _gvn.find_int_con(n, value_if_unknown);
  }
  jlong find_long_con(Node* n, jlong value_if_unknown) {
    return _gvn.find_long_con(n, value_if_unknown);
  }
  // (See also macro find_intptr_t_con in type.hpp, which uses one of these.)

  // JVM State accessors:
  // Parser mapping from JVM indices into Nodes.
  // Low slots are accessed by the StartNode::enum.
  // Then come the locals at StartNode::Parms to StartNode::Parms+max_locals();
  // Then come JVM stack slots.
  // Finally come the monitors, if any.
  // See layout accessors in class JVMState.

  SafePointNode*     map()      const { return _map; }
  bool               has_exceptions() const { return _exceptions != NULL; }
  JVMState*          jvms()     const { return map_not_null()->_jvms; }
  int                sp()       const { return _sp; }
  int                bci()      const { return _bci; }
  Bytecodes::Code    java_bc()  const;
  ciMethod*          method()   const { return _method; }

  void set_jvms(JVMState* jvms)       { set_map(jvms->map());
                                        assert(jvms == this->jvms(), "sanity");
                                        _sp = jvms->sp();
                                        _bci = jvms->bci();
                                        _method = jvms->has_method() ? jvms->method() : NULL; }
  void set_map(SafePointNode* m)      { _map = m; debug_only(verify_map()); }
  void set_sp(int i)                  { assert(i >= 0, "must be non-negative"); _sp = i; }
  void clean_stack(int from_sp); // clear garbage beyond from_sp to top

  void inc_sp(int i)                  { set_sp(sp() + i); }
  void set_bci(int bci)               { _bci = bci; }

  // Make sure jvms has current bci & sp.
  JVMState* sync_jvms()     const;
#ifdef ASSERT
  // Make sure JVMS has an updated copy of bci and sp.
  // Also sanity-check method, depth, and monitor depth.
  bool jvms_in_sync() const;

  // Make sure the map looks OK.
  void verify_map() const;

  // Make sure a proposed exception state looks OK.
  static void verify_exception_state(SafePointNode* ex_map);
#endif

  // Clone the existing map state.  (Implements PreserveJVMState.)
  SafePointNode* clone_map();

  // Set the map to a clone of the given one.
  void set_map_clone(SafePointNode* m);

  // Tell if the compilation is failing.
  bool failing() const { return C->failing(); }

  // Set _map to NULL, signalling a stop to further bytecode execution.
  // Preserve the map intact for future use, and return it back to the caller.
  SafePointNode* stop() { SafePointNode* m = map(); set_map(NULL); return m; }

  // Stop, but first smash the map's inputs to NULL, to mark it dead.
  void stop_and_kill_map();

  // Tell if _map is NULL, or control is top.
  bool stopped();

  // Tell if this method or any caller method has exception handlers.
  bool has_ex_handler();

  // Save an exception without blowing stack contents or other JVM state.
  // (The extra pointer is stuck with add_req on the map, beyond the JVMS.)
  static void set_saved_ex_oop(SafePointNode* ex_map, Node* ex_oop);

  // Recover a saved exception from its map.
  static Node* saved_ex_oop(SafePointNode* ex_map);

  // Recover a saved exception from its map, and remove it from the map.
  static Node* clear_saved_ex_oop(SafePointNode* ex_map);

#ifdef ASSERT
  // Recover a saved exception from its map, and remove it from the map.
  static bool has_saved_ex_oop(SafePointNode* ex_map);
#endif

  // Push an exception in the canonical position for handlers (stack(0)).
  void push_ex_oop(Node* ex_oop) {
    ensure_stack(1);  // ensure room to push the exception
    set_stack(0, ex_oop);
    set_sp(1);
    clean_stack(1);
  }

  // Detach and return an exception state.
  SafePointNode* pop_exception_state() {
    SafePointNode* ex_map = _exceptions;
    if (ex_map != NULL) {
      _exceptions = ex_map->next_exception();
      ex_map->set_next_exception(NULL);
      debug_only(verify_exception_state(ex_map));
    }
    return ex_map;
  }

  // Add an exception, using the given JVM state, without commoning.
  void push_exception_state(SafePointNode* ex_map) {
    debug_only(verify_exception_state(ex_map));
    ex_map->set_next_exception(_exceptions);
    _exceptions = ex_map;
  }

  // Turn the current JVM state into an exception state, appending the ex_oop.
  SafePointNode* make_exception_state(Node* ex_oop);

  // Add an exception, using the given JVM state.
  // Combine all exceptions with a common exception type into a single state.
  // (This is done via combine_exception_states.)
  void add_exception_state(SafePointNode* ex_map);

  // Combine all exceptions of any sort whatever into a single master state.
  SafePointNode* combine_and_pop_all_exception_states() {
    if (_exceptions == NULL)  return NULL;
    SafePointNode* phi_map = pop_exception_state();
    SafePointNode* ex_map;
    while ((ex_map = pop_exception_state()) != NULL) {
      combine_exception_states(ex_map, phi_map);
    }
    return phi_map;
  }

  // Combine the two exception states, building phis as necessary.
  // The second argument is updated to include contributions from the first.
  void combine_exception_states(SafePointNode* ex_map, SafePointNode* phi_map);

  // Reset the map to the given state.  If there are any half-finished phis
  // in it (created by combine_exception_states), transform them now.
  // Returns the exception oop.  (Caller must call push_ex_oop if required.)
  Node* use_exception_state(SafePointNode* ex_map);

  // Collect exceptions from a given JVM state into my exception list.
  void add_exception_states_from(JVMState* jvms);

  // Collect all raised exceptions into the current JVM state.
  // Clear the current exception list and map, returns the combined states.
  JVMState* transfer_exceptions_into_jvms();

  // Helper to throw a built-in exception.
  // Range checks take the offending index.
  // Cast and array store checks take the offending class.
  // Others do not take the optional argument.
  // The JVMS must allow the bytecode to be re-executed
  // via an uncommon trap.
  void builtin_throw(Deoptimization::DeoptReason reason, Node* arg = NULL);

  // Helper Functions for adding debug information
  void kill_dead_locals();
#ifdef ASSERT
  bool dead_locals_are_killed();
#endif
  // The call may deoptimize.  Supply required JVM state as debug info.
  // If must_throw is true, the call is guaranteed not to return normally.
  void add_safepoint_edges(SafePointNode* call,
                           bool must_throw = false);

  // How many stack inputs does the current BC consume?
  // And, how does the stack change after the bytecode?
  // Returns false if unknown.
  bool compute_stack_effects(int& inputs, int& depth);

  // Add a fixed offset to a pointer
  Node* basic_plus_adr(Node* base, Node* ptr, intptr_t offset) {
    return basic_plus_adr(base, ptr, MakeConX(offset));
  }
  Node* basic_plus_adr(Node* base, intptr_t offset) {
    return basic_plus_adr(base, base, MakeConX(offset));
  }
  // Add a variable offset to a pointer
  Node* basic_plus_adr(Node* base, Node* offset) {
    return basic_plus_adr(base, base, offset);
  }
  Node* basic_plus_adr(Node* base, Node* ptr, Node* offset);

  // Convert between int and long, and size_t.
  // (See macros ConvI2X, etc., in type.hpp for ConvI2X, etc.)
  Node* ConvI2L(Node* offset);
  Node* ConvL2I(Node* offset);
  // Find out the klass of an object.
  Node* load_object_klass(Node* object);
  // Find out the length of an array.
  Node* load_array_length(Node* array);
  // Helper function to do a NULL pointer check or ZERO check based on type.
  Node* null_check_common(Node* value, BasicType type,
                          bool assert_null, Node* *null_control);
  // Throw an exception if a given value is null.
  // Return the value cast to not-null.
  // Be clever about equivalent dominating null checks.
  Node* do_null_check(Node* value, BasicType type) {
    return null_check_common(value, type, false, NULL);
  }
  // Throw an uncommon trap if a given value is __not__ null.
  // Return the value cast to null, and be clever about dominating checks.
  Node* do_null_assert(Node* value, BasicType type) {
    return null_check_common(value, type, true, NULL);
  }
  // Null check oop.  Return null-path control into (*null_control).
  // Return a cast-not-null node which depends on the not-null control.
  // If never_see_null, use an uncommon trap (*null_control sees a top).
  // The cast is not valid along the null path; keep a copy of the original.
  Node* null_check_oop(Node* value, Node* *null_control,
                       bool never_see_null = false);

  // Cast obj to not-null on this path
  Node* cast_not_null(Node* obj, bool do_replace_in_map = true);
  // Replace all occurrences of one node by another.
  void replace_in_map(Node* old, Node* neww);

  void push(Node* n)    { map_not_null(); _map->set_stack(_map->_jvms,_sp++,n); }
  Node* pop()           { map_not_null(); return _map->stack(_map->_jvms,--_sp); }
  Node* peek(int off=0) { map_not_null(); return _map->stack(_map->_jvms, _sp - off - 1); }

  void push_pair(Node* ldval) {
    push(ldval);
    push(top());  // the halfword is merely a placeholder
  }
  void push_pair_local(int i) {
    // longs are stored in locals in "push" order
    push(  local(i+0) );  // the real value
    assert(local(i+1) == top(), "");
    push(top());  // halfword placeholder
  }
  Node* pop_pair() {
    // the second half is pushed last & popped first; it contains exactly nothing
    Node* halfword = pop();
    assert(halfword == top(), "");
    // the long bits are pushed first & popped last:
    return pop();
  }
  void set_pair_local(int i, Node* lval) {
    // longs are stored in locals as a value/half pair (like doubles)
    set_local(i+0, lval);
    set_local(i+1, top());
  }

  // Push the node, which may be zero, one, or two words.
  void push_node(BasicType n_type, Node* n) {
    int n_size = type2size[n_type];
    if      (n_size == 1)  push(      n );  // T_INT, ...
    else if (n_size == 2)  push_pair( n );  // T_DOUBLE, T_LONG
    else                   { assert(n_size == 0, "must be T_VOID"); }
  }

  Node* pop_node(BasicType n_type) {
    int n_size = type2size[n_type];
    if      (n_size == 1)  return pop();
    else if (n_size == 2)  return pop_pair();
    else                   return NULL;
  }

  Node* control()               const { return map_not_null()->control(); }
  Node* i_o()                   const { return map_not_null()->i_o(); }
  Node* returnadr()             const { return map_not_null()->returnadr(); }
  Node* frameptr()              const { return map_not_null()->frameptr(); }
  Node* local(uint idx)         const { map_not_null(); return _map->local(      _map->_jvms, idx); }
  Node* stack(uint idx)         const { map_not_null(); return _map->stack(      _map->_jvms, idx); }
  Node* argument(uint idx)      const { map_not_null(); return _map->argument(   _map->_jvms, idx); }
  Node* monitor_box(uint idx)   const { map_not_null(); return _map->monitor_box(_map->_jvms, idx); }
  Node* monitor_obj(uint idx)   const { map_not_null(); return _map->monitor_obj(_map->_jvms, idx); }

  void set_control  (Node* c)         { map_not_null()->set_control(c); }
  void set_i_o      (Node* c)         { map_not_null()->set_i_o(c); }
  void set_local(uint idx, Node* c)   { map_not_null(); _map->set_local(   _map->_jvms, idx, c); }
  void set_stack(uint idx, Node* c)   { map_not_null(); _map->set_stack(   _map->_jvms, idx, c); }
  void set_argument(uint idx, Node* c){ map_not_null(); _map->set_argument(_map->_jvms, idx, c); }
  void ensure_stack(uint stk_size)    { map_not_null(); _map->ensure_stack(_map->_jvms, stk_size); }

  // Access unaliased memory
  Node* memory(uint alias_idx);
  Node* memory(const TypePtr *tp) { return memory(C->get_alias_index(tp)); }
  Node* memory(Node* adr) { return memory(_gvn.type(adr)->is_ptr()); }

  // Access immutable memory
  Node* immutable_memory() { return C->immutable_memory(); }

  // Set unaliased memory
  void set_memory(Node* c, uint alias_idx) { merged_memory()->set_memory_at(alias_idx, c); }
  void set_memory(Node* c, const TypePtr *tp) { set_memory(c,C->get_alias_index(tp)); }
  void set_memory(Node* c, Node* adr) { set_memory(c,_gvn.type(adr)->is_ptr()); }

  // Get the entire memory state (probably a MergeMemNode), and reset it
  // (The resetting prevents somebody from using the dangling Node pointer.)
  Node* reset_memory();

  // Get the entire memory state, asserted to be a MergeMemNode.
  MergeMemNode* merged_memory() {
    Node* mem = map_not_null()->memory();
    assert(mem->is_MergeMem(), "parse memory is always pre-split");
    return mem->as_MergeMem();
  }

  // Set the entire memory state; produce a new MergeMemNode.
  void set_all_memory(Node* newmem);

  // Create a memory projection from the call, then set_all_memory.
  void set_all_memory_call(Node* call);

  // Create a LoadNode, reading from the parser's memory state.
  // (Note:  require_atomic_access is useful only with T_LONG.)
  Node* make_load(Node* ctl, Node* adr, const Type* t, BasicType bt,
                  bool require_atomic_access = false) {
    // This version computes alias_index from bottom_type
    return make_load(ctl, adr, t, bt, adr->bottom_type()->is_ptr(),
                     require_atomic_access);
  }
  Node* make_load(Node* ctl, Node* adr, const Type* t, BasicType bt, const TypePtr* adr_type, bool require_atomic_access = false) {
    // This version computes alias_index from an address type
    assert(adr_type != NULL, "use other make_load factory");
    return make_load(ctl, adr, t, bt, C->get_alias_index(adr_type),
                     require_atomic_access);
  }
  // This is the base version which is given an alias index.
  Node* make_load(Node* ctl, Node* adr, const Type* t, BasicType bt, int adr_idx, bool require_atomic_access = false);

  // Create & transform a StoreNode and store the effect into the
  // parser's memory state.
  Node* store_to_memory(Node* ctl, Node* adr, Node* val, BasicType bt,
                        const TypePtr* adr_type,
                        bool require_atomic_access = false) {
    // This version computes alias_index from an address type
    assert(adr_type != NULL, "use other store_to_memory factory");
    return store_to_memory(ctl, adr, val, bt,
                           C->get_alias_index(adr_type),
                           require_atomic_access);
  }
  // This is the base version which is given alias index
  // Return the new StoreXNode
  Node* store_to_memory(Node* ctl, Node* adr, Node* val, BasicType bt,
                        int adr_idx,
                        bool require_atomic_access = false);


  // All in one pre-barrier, store, post_barrier
  // Insert a write-barrier'd store.  This is to let generational GC
  // work; we have to flag all oop-stores before the next GC point.
  //
  // It comes in 3 flavors of store to an object, array, or unknown.
  // We use precise card marks for arrays to avoid scanning the entire
  // array. We use imprecise for object. We use precise for unknown
  // since we don't know if we have an array or and object or even
  // where the object starts.
  //
  // If val==NULL, it is taken to be a completely unknown value. QQQ

  Node* store_oop_to_object(Node* ctl,
                            Node* obj,   // containing obj
                            Node* adr,  // actual adress to store val at
                            const TypePtr* adr_type,
                            Node* val,
                            const TypeOopPtr* val_type,
                            BasicType bt);

  Node* store_oop_to_array(Node* ctl,
                           Node* obj,   // containing obj
                           Node* adr,  // actual adress to store val at
                           const TypePtr* adr_type,
                           Node* val,
                           const TypeOopPtr* val_type,
                           BasicType bt);

  // Could be an array or object we don't know at compile time (unsafe ref.)
  Node* store_oop_to_unknown(Node* ctl,
                             Node* obj,   // containing obj
                             Node* adr,  // actual adress to store val at
                             const TypePtr* adr_type,
                             Node* val,
                             BasicType bt);

  // For the few case where the barriers need special help
  void pre_barrier(Node* ctl, Node* obj, Node* adr, uint adr_idx,
                   Node* val, const TypeOopPtr* val_type, BasicType bt);

  void post_barrier(Node* ctl, Node* store, Node* obj, Node* adr, uint adr_idx,
                    Node* val, BasicType bt, bool use_precise);

  // Return addressing for an array element.
  Node* array_element_address(Node* ary, Node* idx, BasicType elembt,
                              // Optional constraint on the array size:
                              const TypeInt* sizetype = NULL);

  // Return a load of array element at idx.
  Node* load_array_element(Node* ctl, Node* ary, Node* idx, const TypeAryPtr* arytype);

  // CMS card-marks have an input from the corresponding oop_store
  void  cms_card_mark(Node* ctl, Node* adr, Node* val, Node* oop_store);

  //---------------- Dtrace support --------------------
  void make_dtrace_method_entry_exit(ciMethod* method, bool is_entry);
  void make_dtrace_method_entry(ciMethod* method) {
    make_dtrace_method_entry_exit(method, true);
  }
  void make_dtrace_method_exit(ciMethod* method) {
    make_dtrace_method_entry_exit(method, false);
  }

  //--------------- stub generation -------------------
 public:
  void gen_stub(address C_function,
                const char *name,
                int is_fancy_jump,
                bool pass_tls,
                bool return_pc);

  //---------- help for generating calls --------------

  // Do a null check on the receiver, which is in argument(0).
  Node* null_check_receiver(ciMethod* callee) {
    assert(!callee->is_static(), "must be a virtual method");
    int nargs = 1 + callee->signature()->size();
    // Null check on self without removing any arguments.  The argument
    // null check technically happens in the wrong place, which can lead to
    // invalid stack traces when the primitive is inlined into a method
    // which handles NullPointerExceptions.
    Node* receiver = argument(0);
    _sp += nargs;
    receiver = do_null_check(receiver, T_OBJECT);
    _sp -= nargs;
    return receiver;
  }

  // Fill in argument edges for the call from argument(0), argument(1), ...
  // (The next step is to call set_edges_for_java_call.)
  void  set_arguments_for_java_call(CallJavaNode* call);

  // Fill in non-argument edges for the call.
  // Transform the call, and update the basics: control, i_o, memory.
  // (The next step is usually to call set_results_for_java_call.)
  void set_edges_for_java_call(CallJavaNode* call,
                               bool must_throw = false);

  // Finish up a java call that was started by set_edges_for_java_call.
  // Call add_exception on any throw arising from the call.
  // Return the call result (transformed).
  Node* set_results_for_java_call(CallJavaNode* call);

  // Similar to set_edges_for_java_call, but simplified for runtime calls.
  void  set_predefined_output_for_runtime_call(Node* call) {
    set_predefined_output_for_runtime_call(call, NULL, NULL);
  }
  void  set_predefined_output_for_runtime_call(Node* call,
                                               Node* keep_mem,
                                               const TypePtr* hook_mem);
  Node* set_predefined_input_for_runtime_call(SafePointNode* call);

  // helper functions for statistics
  void increment_counter(address counter_addr);   // increment a debug counter
  void increment_counter(Node*   counter_addr);   // increment a debug counter

  // Bail out to the interpreter right now
  // The optional klass is the one causing the trap.
  // The optional reason is debug information written to the compile log.
  // Optional must_throw is the same as with add_safepoint_edges.
  void uncommon_trap(int trap_request,
                     ciKlass* klass = NULL, const char* reason_string = NULL,
                     bool must_throw = false, bool keep_exact_action = false);

  // Shorthand, to avoid saying "Deoptimization::" so many times.
  void uncommon_trap(Deoptimization::DeoptReason reason,
                     Deoptimization::DeoptAction action,
                     ciKlass* klass = NULL, const char* reason_string = NULL,
                     bool must_throw = false, bool keep_exact_action = false) {
    uncommon_trap(Deoptimization::make_trap_request(reason, action),
                  klass, reason_string, must_throw, keep_exact_action);
  }

  // Report if there were too many traps at the current method and bci.
  // Report if a trap was recorded, and/or PerMethodTrapLimit was exceeded.
  // If there is no MDO at all, report no trap unless told to assume it.
  bool too_many_traps(Deoptimization::DeoptReason reason) {
    return C->too_many_traps(method(), bci(), reason);
  }

  // Report if there were too many recompiles at the current method and bci.
  bool too_many_recompiles(Deoptimization::DeoptReason reason) {
    return C->too_many_recompiles(method(), bci(), reason);
  }

  // vanilla/CMS post barrier
  void write_barrier_post(Node *store, Node* obj, Node* adr, Node* val, bool use_precise);

  // Returns the object (if any) which was created the moment before.
  Node* just_allocated_object(Node* current_control);

  static bool use_ReduceInitialCardMarks() {
    return (ReduceInitialCardMarks
            && Universe::heap()->can_elide_tlab_store_barriers());
  }

  // G1 pre/post barriers
  void g1_write_barrier_pre(Node* obj,
                            Node* adr,
                            uint alias_idx,
                            Node* val,
                            const TypeOopPtr* val_type,
                            BasicType bt);

  void g1_write_barrier_post(Node* store,
                             Node* obj,
                             Node* adr,
                             uint alias_idx,
                             Node* val,
                             BasicType bt,
                             bool use_precise);
  // Helper function for g1
  private:
  void g1_mark_card(IdealKit* ideal, Node* card_adr, Node* store,  Node* index, Node* index_adr,
                    Node* buffer, const TypeFunc* tf);

  public:
  // Helper function to round double arguments before a call
  void round_double_arguments(ciMethod* dest_method);
  void round_double_result(ciMethod* dest_method);

  // rounding for strict float precision conformance
  Node* precision_rounding(Node* n);

  // rounding for strict double precision conformance
  Node* dprecision_rounding(Node* n);

  // rounding for non-strict double stores
  Node* dstore_rounding(Node* n);

  // Helper functions for fast/slow path codes
  Node* opt_iff(Node* region, Node* iff);
  Node* make_runtime_call(int flags,
                          const TypeFunc* call_type, address call_addr,
                          const char* call_name,
                          const TypePtr* adr_type, // NULL if no memory effects
                          Node* parm0 = NULL, Node* parm1 = NULL,
                          Node* parm2 = NULL, Node* parm3 = NULL,
                          Node* parm4 = NULL, Node* parm5 = NULL,
                          Node* parm6 = NULL, Node* parm7 = NULL);
  enum {  // flag values for make_runtime_call
    RC_NO_FP = 1,               // CallLeafNoFPNode
    RC_NO_IO = 2,               // do not hook IO edges
    RC_NO_LEAF = 4,             // CallStaticJavaNode
    RC_MUST_THROW = 8,          // flag passed to add_safepoint_edges
    RC_NARROW_MEM = 16,         // input memory is same as output
    RC_UNCOMMON = 32,           // freq. expected to be like uncommon trap
    RC_LEAF = 0                 // null value:  no flags set
  };

  // merge in all memory slices from new_mem, along the given path
  void merge_memory(Node* new_mem, Node* region, int new_path);
  void make_slow_call_ex(Node* call, ciInstanceKlass* ex_klass, bool separate_io_proj);

  // Helper functions to build synchronizations
  int next_monitor();
  Node* insert_mem_bar(int opcode, Node* precedent = NULL);
  Node* insert_mem_bar_volatile(int opcode, int alias_idx, Node* precedent = NULL);
  // Optional 'precedent' is appended as an extra edge, to force ordering.
  FastLockNode* shared_lock(Node* obj);
  void shared_unlock(Node* box, Node* obj);

  // helper functions for the fast path/slow path idioms
  Node* fast_and_slow(Node* in, const Type *result_type, Node* null_result, IfNode* fast_test, Node* fast_result, address slow_call, const TypeFunc *slow_call_type, Node* slow_arg, klassOop ex_klass, Node* slow_result);

  // Generate an instance-of idiom.  Used by both the instance-of bytecode
  // and the reflective instance-of call.
  Node* gen_instanceof( Node *subobj, Node* superkls );

  // Generate a check-cast idiom.  Used by both the check-cast bytecode
  // and the array-store bytecode
  Node* gen_checkcast( Node *subobj, Node* superkls,
                       Node* *failure_control = NULL );

  // Generate a subtyping check.  Takes as input the subtype and supertype.
  // Returns 2 values: sets the default control() to the true path and
  // returns the false path.  Only reads from constant memory taken from the
  // default memory; does not write anything.  It also doesn't take in an
  // Object; if you wish to check an Object you need to load the Object's
  // class prior to coming here.
  Node* gen_subtype_check(Node* subklass, Node* superklass);

  // Static parse-time type checking logic for gen_subtype_check:
  enum { SSC_always_false, SSC_always_true, SSC_easy_test, SSC_full_test };
  int static_subtype_check(ciKlass* superk, ciKlass* subk);

  // Exact type check used for predicted calls and casts.
  // Rewrites (*casted_receiver) to be casted to the stronger type.
  // (Caller is responsible for doing replace_in_map.)
  Node* type_check_receiver(Node* receiver, ciKlass* klass, float prob,
                            Node* *casted_receiver);

  // implementation of object creation
  Node* set_output_for_allocation(AllocateNode* alloc,
                                  const TypeOopPtr* oop_type,
                                  bool raw_mem_only);
  Node* get_layout_helper(Node* klass_node, jint& constant_value);
  Node* new_instance(Node* klass_node,
                     Node* slow_test = NULL,
                     bool raw_mem_only = false,
                     Node* *return_size_val = NULL);
  Node* new_array(Node* klass_node, Node* count_val, int nargs,
                  bool raw_mem_only = false, Node* *return_size_val = NULL);

  // Handy for making control flow
  IfNode* create_and_map_if(Node* ctrl, Node* tst, float prob, float cnt) {
    IfNode* iff = new (C, 2) IfNode(ctrl, tst, prob, cnt);// New IfNode's
    _gvn.set_type(iff, iff->Value(&_gvn)); // Value may be known at parse-time
    // Place 'if' on worklist if it will be in graph
    if (!tst->is_Con())  record_for_igvn(iff);     // Range-check and Null-check removal is later
    return iff;
  }

  IfNode* create_and_xform_if(Node* ctrl, Node* tst, float prob, float cnt) {
    IfNode* iff = new (C, 2) IfNode(ctrl, tst, prob, cnt);// New IfNode's
    _gvn.transform(iff);                           // Value may be known at parse-time
    // Place 'if' on worklist if it will be in graph
    if (!tst->is_Con())  record_for_igvn(iff);     // Range-check and Null-check removal is later
    return iff;
  }
};

// Helper class to support building of control flow branches. Upon
// creation the map and sp at bci are cloned and restored upon de-
// struction. Typical use:
//
// { PreserveJVMState pjvms(this);
//   // code of new branch
// }
// // here the JVM state at bci is established

class PreserveJVMState: public StackObj {
 protected:
  GraphKit*      _kit;
#ifdef ASSERT
  int            _block;  // PO of current block, if a Parse
  int            _bci;
#endif
  SafePointNode* _map;
  uint           _sp;

 public:
  PreserveJVMState(GraphKit* kit, bool clone_map = true);
  ~PreserveJVMState();
};

// Helper class to build cutouts of the form if (p) ; else {x...}.
// The code {x...} must not fall through.
// The kit's main flow of control is set to the "then" continuation of if(p).
class BuildCutout: public PreserveJVMState {
 public:
  BuildCutout(GraphKit* kit, Node* p, float prob, float cnt = COUNT_UNKNOWN);
  ~BuildCutout();
};
