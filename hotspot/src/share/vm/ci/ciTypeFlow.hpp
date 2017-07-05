/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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


class ciTypeFlow : public ResourceObj {
private:
  ciEnv*    _env;
  ciMethod* _method;
  ciMethodBlocks* _methodBlocks;
  int       _osr_bci;

  // information cached from the method:
  int _max_locals;
  int _max_stack;
  int _code_size;

  const char* _failure_reason;

public:
  class StateVector;
  class Block;

  // Build a type flow analyzer
  // Do an OSR analysis if osr_bci >= 0.
  ciTypeFlow(ciEnv* env, ciMethod* method, int osr_bci = InvocationEntryBci);

  // Accessors
  ciMethod* method() const     { return _method; }
  ciEnv*    env()              { return _env; }
  Arena*    arena()            { return _env->arena(); }
  bool      is_osr_flow() const{ return _osr_bci != InvocationEntryBci; }
  int       start_bci() const  { return is_osr_flow()? _osr_bci: 0; }
  int       max_locals() const { return _max_locals; }
  int       max_stack() const  { return _max_stack; }
  int       max_cells() const  { return _max_locals + _max_stack; }
  int       code_size() const  { return _code_size; }

  // Represents information about an "active" jsr call.  This
  // class represents a call to the routine at some entry address
  // with some distinct return address.
  class JsrRecord : public ResourceObj {
  private:
    int _entry_address;
    int _return_address;
  public:
    JsrRecord(int entry_address, int return_address) {
      _entry_address = entry_address;
      _return_address = return_address;
    }

    int entry_address() const  { return _entry_address; }
    int return_address() const { return _return_address; }

    void print_on(outputStream* st) const {
#ifndef PRODUCT
      st->print("%d->%d", entry_address(), return_address());
#endif
    }
  };

  // A JsrSet represents some set of JsrRecords.  This class
  // is used to record a set of all jsr routines which we permit
  // execution to return (ret) from.
  //
  // During abstract interpretation, JsrSets are used to determine
  // whether two paths which reach a given block are unique, and
  // should be cloned apart, or are compatible, and should merge
  // together.
  //
  // Note that different amounts of effort can be expended determining
  // if paths are compatible.  <DISCUSSION>
  class JsrSet : public ResourceObj {
  private:
    GrowableArray<JsrRecord*>* _set;

    JsrRecord* record_at(int i) {
      return _set->at(i);
    }

    // Insert the given JsrRecord into the JsrSet, maintaining the order
    // of the set and replacing any element with the same entry address.
    void insert_jsr_record(JsrRecord* record);

    // Remove the JsrRecord with the given return address from the JsrSet.
    void remove_jsr_record(int return_address);

  public:
    JsrSet(Arena* arena, int default_len = 4);

    // Copy this JsrSet.
    void copy_into(JsrSet* jsrs);

    // Is this JsrSet compatible with some other JsrSet?
    bool is_compatible_with(JsrSet* other);

    // Apply the effect of a single bytecode to the JsrSet.
    void apply_control(ciTypeFlow* analyzer,
                       ciBytecodeStream* str,
                       StateVector* state);

    // What is the cardinality of this set?
    int size() const { return _set->length(); }

    void print_on(outputStream* st) const PRODUCT_RETURN;
  };

  // Used as a combined index for locals and temps
  enum Cell {
    Cell_0, Cell_max = INT_MAX
  };

  // A StateVector summarizes the type information at some
  // point in the program
  class StateVector : public ResourceObj {
  private:
    ciType**    _types;
    int         _stack_size;
    int         _monitor_count;
    ciTypeFlow* _outer;

    int         _trap_bci;
    int         _trap_index;

    static ciType* type_meet_internal(ciType* t1, ciType* t2, ciTypeFlow* analyzer);

  public:
    // Special elements in our type lattice.
    enum {
      T_TOP     = T_VOID,      // why not?
      T_BOTTOM  = T_CONFLICT,
      T_LONG2   = T_SHORT,     // 2nd word of T_LONG
      T_DOUBLE2 = T_CHAR,      // 2nd word of T_DOUBLE
      T_NULL    = T_BYTE       // for now.
    };
    static ciType* top_type()    { return ciType::make((BasicType)T_TOP); }
    static ciType* bottom_type() { return ciType::make((BasicType)T_BOTTOM); }
    static ciType* long2_type()  { return ciType::make((BasicType)T_LONG2); }
    static ciType* double2_type(){ return ciType::make((BasicType)T_DOUBLE2); }
    static ciType* null_type()   { return ciType::make((BasicType)T_NULL); }

    static ciType* half_type(ciType* t) {
      switch (t->basic_type()) {
      case T_LONG:    return long2_type();
      case T_DOUBLE:  return double2_type();
      default:        ShouldNotReachHere(); return NULL;
      }
    }

    // The meet operation for our type lattice.
    ciType* type_meet(ciType* t1, ciType* t2) {
      return type_meet_internal(t1, t2, outer());
    }

    // Accessors
    ciTypeFlow* outer() const          { return _outer; }

    int         stack_size() const     { return _stack_size; }
    void    set_stack_size(int ss)     { _stack_size = ss; }

    int         monitor_count() const  { return _monitor_count; }
    void    set_monitor_count(int mc)  { _monitor_count = mc; }

    static Cell start_cell()           { return (Cell)0; }
    static Cell next_cell(Cell c)      { return (Cell)(((int)c) + 1); }
    Cell        limit_cell() const {
      return (Cell)(outer()->max_locals() + stack_size());
    }

    // Cell creation
    Cell      local(int lnum) const {
      assert(lnum < outer()->max_locals(), "index check");
      return (Cell)(lnum);
    }

    Cell      stack(int snum) const {
      assert(snum < stack_size(), "index check");
      return (Cell)(outer()->max_locals() + snum);
    }

    Cell      tos() const { return stack(stack_size()-1); }

    // For external use only:
    ciType* local_type_at(int i) const { return type_at(local(i)); }
    ciType* stack_type_at(int i) const { return type_at(stack(i)); }

    // Accessors for the type of some Cell c
    ciType*   type_at(Cell c) const {
      assert(start_cell() <= c && c < limit_cell(), "out of bounds");
      return _types[c];
    }

    void      set_type_at(Cell c, ciType* type) {
      assert(start_cell() <= c && c < limit_cell(), "out of bounds");
      _types[c] = type;
    }

    // Top-of-stack operations.
    void      set_type_at_tos(ciType* type) { set_type_at(tos(), type); }
    ciType*   type_at_tos() const           { return type_at(tos()); }

    void      push(ciType* type) {
      _stack_size++;
      set_type_at_tos(type);
    }
    void      pop() {
      debug_only(set_type_at_tos(bottom_type()));
      _stack_size--;
    }
    ciType*   pop_value() {
      ciType* t = type_at_tos();
      pop();
      return t;
    }

    // Convenience operations.
    bool      is_reference(ciType* type) const {
      return type == null_type() || !type->is_primitive_type();
    }
    bool      is_int(ciType* type) const {
      return type->basic_type() == T_INT;
    }
    bool      is_long(ciType* type) const {
      return type->basic_type() == T_LONG;
    }
    bool      is_float(ciType* type) const {
      return type->basic_type() == T_FLOAT;
    }
    bool      is_double(ciType* type) const {
      return type->basic_type() == T_DOUBLE;
    }

    void      push_translate(ciType* type);

    void      push_int() {
      push(ciType::make(T_INT));
    }
    void      pop_int() {
      assert(is_int(type_at_tos()), "must be integer");
      pop();
    }
    void      check_int(Cell c) {
      assert(is_int(type_at(c)), "must be integer");
    }
    void      push_double() {
      push(ciType::make(T_DOUBLE));
      push(double2_type());
    }
    void      pop_double() {
      assert(type_at_tos() == double2_type(), "must be 2nd half");
      pop();
      assert(is_double(type_at_tos()), "must be double");
      pop();
    }
    void      push_float() {
      push(ciType::make(T_FLOAT));
    }
    void      pop_float() {
      assert(is_float(type_at_tos()), "must be float");
      pop();
    }
    void      push_long() {
      push(ciType::make(T_LONG));
      push(long2_type());
    }
    void      pop_long() {
      assert(type_at_tos() == long2_type(), "must be 2nd half");
      pop();
      assert(is_long(type_at_tos()), "must be long");
      pop();
    }
    void      push_object(ciKlass* klass) {
      push(klass);
    }
    void      pop_object() {
      assert(is_reference(type_at_tos()), "must be reference type");
      pop();
    }
    void      pop_array() {
      assert(type_at_tos() == null_type() ||
             type_at_tos()->is_array_klass(), "must be array type");
      pop();
    }
    // pop_objArray and pop_typeArray narrow the tos to ciObjArrayKlass
    // or ciTypeArrayKlass (resp.).  In the rare case that an explicit
    // null is popped from the stack, we return NULL.  Caller beware.
    ciObjArrayKlass* pop_objArray() {
      ciType* array = pop_value();
      if (array == null_type())  return NULL;
      assert(array->is_obj_array_klass(), "must be object array type");
      return array->as_obj_array_klass();
    }
    ciTypeArrayKlass* pop_typeArray() {
      ciType* array = pop_value();
      if (array == null_type())  return NULL;
      assert(array->is_type_array_klass(), "must be prim array type");
      return array->as_type_array_klass();
    }
    void      push_null() {
      push(null_type());
    }
    void      do_null_assert(ciKlass* unloaded_klass);

    // Helper convenience routines.
    void do_aaload(ciBytecodeStream* str);
    void do_checkcast(ciBytecodeStream* str);
    void do_getfield(ciBytecodeStream* str);
    void do_getstatic(ciBytecodeStream* str);
    void do_invoke(ciBytecodeStream* str, bool has_receiver);
    void do_jsr(ciBytecodeStream* str);
    void do_ldc(ciBytecodeStream* str);
    void do_multianewarray(ciBytecodeStream* str);
    void do_new(ciBytecodeStream* str);
    void do_newarray(ciBytecodeStream* str);
    void do_putfield(ciBytecodeStream* str);
    void do_putstatic(ciBytecodeStream* str);
    void do_ret(ciBytecodeStream* str);

    void overwrite_local_double_long(int index) {
      // Invalidate the previous local if it contains first half of
      // a double or long value since it's seconf half is being overwritten.
      int prev_index = index - 1;
      if (prev_index >= 0 &&
          (is_double(type_at(local(prev_index))) ||
           is_long(type_at(local(prev_index))))) {
        set_type_at(local(prev_index), bottom_type());
      }
    }

    void load_local_object(int index) {
      ciType* type = type_at(local(index));
      assert(is_reference(type), "must be reference type");
      push(type);
    }
    void store_local_object(int index) {
      ciType* type = pop_value();
      assert(is_reference(type) || type->is_return_address(),
             "must be reference type or return address");
      overwrite_local_double_long(index);
      set_type_at(local(index), type);
    }

    void load_local_double(int index) {
      ciType* type = type_at(local(index));
      ciType* type2 = type_at(local(index+1));
      assert(is_double(type), "must be double type");
      assert(type2 == double2_type(), "must be 2nd half");
      push(type);
      push(double2_type());
    }
    void store_local_double(int index) {
      ciType* type2 = pop_value();
      ciType* type = pop_value();
      assert(is_double(type), "must be double");
      assert(type2 == double2_type(), "must be 2nd half");
      overwrite_local_double_long(index);
      set_type_at(local(index), type);
      set_type_at(local(index+1), type2);
    }

    void load_local_float(int index) {
      ciType* type = type_at(local(index));
      assert(is_float(type), "must be float type");
      push(type);
    }
    void store_local_float(int index) {
      ciType* type = pop_value();
      assert(is_float(type), "must be float type");
      overwrite_local_double_long(index);
      set_type_at(local(index), type);
    }

    void load_local_int(int index) {
      ciType* type = type_at(local(index));
      assert(is_int(type), "must be int type");
      push(type);
    }
    void store_local_int(int index) {
      ciType* type = pop_value();
      assert(is_int(type), "must be int type");
      overwrite_local_double_long(index);
      set_type_at(local(index), type);
    }

    void load_local_long(int index) {
      ciType* type = type_at(local(index));
      ciType* type2 = type_at(local(index+1));
      assert(is_long(type), "must be long type");
      assert(type2 == long2_type(), "must be 2nd half");
      push(type);
      push(long2_type());
    }
    void store_local_long(int index) {
      ciType* type2 = pop_value();
      ciType* type = pop_value();
      assert(is_long(type), "must be long");
      assert(type2 == long2_type(), "must be 2nd half");
      overwrite_local_double_long(index);
      set_type_at(local(index), type);
      set_type_at(local(index+1), type2);
    }

    // Stop interpretation of this path with a trap.
    void trap(ciBytecodeStream* str, ciKlass* klass, int index);

  public:
    StateVector(ciTypeFlow* outer);

    // Copy our value into some other StateVector
    void copy_into(StateVector* copy) const;

    // Meets this StateVector with another, destructively modifying this
    // one.  Returns true if any modification takes place.
    bool meet(const StateVector* incoming);

    // Ditto, except that the incoming state is coming from an exception.
    bool meet_exception(ciInstanceKlass* exc, const StateVector* incoming);

    // Apply the effect of one bytecode to this StateVector
    bool apply_one_bytecode(ciBytecodeStream* stream);

    // What is the bci of the trap?
    int  trap_bci() { return _trap_bci; }

    // What is the index associated with the trap?
    int  trap_index() { return _trap_index; }

    void print_cell_on(outputStream* st, Cell c) const PRODUCT_RETURN;
    void print_on(outputStream* st) const              PRODUCT_RETURN;
  };

  // Parameter for "find_block" calls:
  // Describes the difference between a public and private copy.
  enum CreateOption {
    create_public_copy,
    create_private_copy,
    no_create
  };

  // A basic block
  class Block : public ResourceObj {
  private:
    ciBlock*                          _ciblock;
    GrowableArray<Block*>*           _exceptions;
    GrowableArray<ciInstanceKlass*>* _exc_klasses;
    GrowableArray<Block*>*           _successors;
    StateVector*                     _state;
    JsrSet*                          _jsrs;

    int                              _trap_bci;
    int                              _trap_index;

    // A reasonable approximation to pre-order, provided.to the client.
    int                              _pre_order;

    // Has this block been cloned for some special purpose?
    bool                             _private_copy;

    // A pointer used for our internal work list
    Block*                 _next;
    bool                   _on_work_list;

    ciBlock*     ciblock() const     { return _ciblock; }
    StateVector* state() const     { return _state; }

    // Compute the exceptional successors and types for this Block.
    void compute_exceptions();

  public:
    // constructors
    Block(ciTypeFlow* outer, ciBlock* ciblk, JsrSet* jsrs);

    void set_trap(int trap_bci, int trap_index) {
      _trap_bci = trap_bci;
      _trap_index = trap_index;
      assert(has_trap(), "");
    }
    bool has_trap()   const  { return _trap_bci != -1; }
    int  trap_bci()   const  { assert(has_trap(), ""); return _trap_bci; }
    int  trap_index() const  { assert(has_trap(), ""); return _trap_index; }

    // accessors
    ciTypeFlow* outer() const { return state()->outer(); }
    int start() const         { return _ciblock->start_bci(); }
    int limit() const         { return _ciblock->limit_bci(); }
    int control() const       { return _ciblock->control_bci(); }

    bool    is_private_copy() const       { return _private_copy; }
    void   set_private_copy(bool z);
    int        private_copy_count() const { return outer()->private_copy_count(ciblock()->index(), _jsrs); }

    // access to entry state
    int     stack_size() const         { return _state->stack_size(); }
    int     monitor_count() const      { return _state->monitor_count(); }
    ciType* local_type_at(int i) const { return _state->local_type_at(i); }
    ciType* stack_type_at(int i) const { return _state->stack_type_at(i); }

    // Get the successors for this Block.
    GrowableArray<Block*>* successors(ciBytecodeStream* str,
                                      StateVector* state,
                                      JsrSet* jsrs);
    GrowableArray<Block*>* successors() {
      assert(_successors != NULL, "must be filled in");
      return _successors;
    }

    // Helper function for "successors" when making private copies of
    // loop heads for C2.
    Block * clone_loop_head(ciTypeFlow* analyzer,
                            int branch_bci,
                            Block* target,
                            JsrSet* jsrs);

    // Get the exceptional successors for this Block.
    GrowableArray<Block*>* exceptions() {
      if (_exceptions == NULL) {
        compute_exceptions();
      }
      return _exceptions;
    }

    // Get the exception klasses corresponding to the
    // exceptional successors for this Block.
    GrowableArray<ciInstanceKlass*>* exc_klasses() {
      if (_exc_klasses == NULL) {
        compute_exceptions();
      }
      return _exc_klasses;
    }

    // Is this Block compatible with a given JsrSet?
    bool is_compatible_with(JsrSet* other) {
      return _jsrs->is_compatible_with(other);
    }

    // Copy the value of our state vector into another.
    void copy_state_into(StateVector* copy) const {
      _state->copy_into(copy);
    }

    // Copy the value of our JsrSet into another
    void copy_jsrs_into(JsrSet* copy) const {
      _jsrs->copy_into(copy);
    }

    // Meets the start state of this block with another state, destructively
    // modifying this one.  Returns true if any modification takes place.
    bool meet(const StateVector* incoming) {
      return state()->meet(incoming);
    }

    // Ditto, except that the incoming state is coming from an
    // exception path.  This means the stack is replaced by the
    // appropriate exception type.
    bool meet_exception(ciInstanceKlass* exc, const StateVector* incoming) {
      return state()->meet_exception(exc, incoming);
    }

    // Work list manipulation
    void   set_next(Block* block) { _next = block; }
    Block* next() const           { return _next; }

    void   set_on_work_list(bool c) { _on_work_list = c; }
    bool   is_on_work_list() const  { return _on_work_list; }

    bool   has_pre_order() const  { return _pre_order >= 0; }
    void   set_pre_order(int po)  { assert(!has_pre_order() && po >= 0, ""); _pre_order = po; }
    int    pre_order() const      { assert(has_pre_order(), ""); return _pre_order; }
    bool   is_start() const       { return _pre_order == outer()->start_block_num(); }

    // A ranking used in determining order within the work list.
    bool   is_simpler_than(Block* other);

    void   print_value_on(outputStream* st) const PRODUCT_RETURN;
    void   print_on(outputStream* st) const       PRODUCT_RETURN;
  };

  // Standard indexes of successors, for various bytecodes.
  enum {
    FALL_THROUGH   = 0,  // normal control
    IF_NOT_TAKEN   = 0,  // the not-taken branch of an if (i.e., fall-through)
    IF_TAKEN       = 1,  // the taken branch of an if
    GOTO_TARGET    = 0,  // unique successor for goto, jsr, or ret
    SWITCH_DEFAULT = 0,  // default branch of a switch
    SWITCH_CASES   = 1   // first index for any non-default switch branches
    // Unlike in other blocks, the successors of a switch are listed uniquely.
  };

private:
  // A mapping from pre_order to Blocks.  This array is created
  // only at the end of the flow.
  Block** _block_map;

  // For each ciBlock index, a list of Blocks which share this ciBlock.
  GrowableArray<Block*>** _idx_to_blocklist;
  // count of ciBlocks
  int _ciblock_count;

  // Tells if a given instruction is able to generate an exception edge.
  bool can_trap(ciBytecodeStream& str);

public:
  // Return the block beginning at bci which has a JsrSet compatible
  // with jsrs.
  Block* block_at(int bci, JsrSet* set, CreateOption option = create_public_copy);

  // block factory
  Block* get_block_for(int ciBlockIndex, JsrSet* jsrs, CreateOption option = create_public_copy);

  // How many of the blocks have the private_copy bit set?
  int private_copy_count(int ciBlockIndex, JsrSet* jsrs) const;

  // Return an existing block containing bci which has a JsrSet compatible
  // with jsrs, or NULL if there is none.
  Block* existing_block_at(int bci, JsrSet* set) { return block_at(bci, set, no_create); }

  // Tell whether the flow analysis has encountered an error of some sort.
  bool failing() { return env()->failing() || _failure_reason != NULL; }

  // Reason this compilation is failing, such as "too many basic blocks".
  const char* failure_reason() { return _failure_reason; }

  // Note a failure.
  void record_failure(const char* reason);

  // Return the block of a given pre-order number.
  int have_block_count() const      { return _block_map != NULL; }
  int block_count() const           { assert(have_block_count(), "");
                                      return _next_pre_order; }
  Block* pre_order_at(int po) const { assert(0 <= po && po < block_count(), "out of bounds");
                                      return _block_map[po]; }
  Block* start_block() const        { return pre_order_at(start_block_num()); }
  int start_block_num() const       { return 0; }

private:
  // A work list used during flow analysis.
  Block* _work_list;

  // Next Block::_pre_order.  After mapping, doubles as block_count.
  int _next_pre_order;

  // Are there more blocks on the work list?
  bool work_list_empty() { return _work_list == NULL; }

  // Get the next basic block from our work list.
  Block* work_list_next();

  // Add a basic block to our work list.
  void add_to_work_list(Block* block);

  // State used for make_jsr_record
  int _jsr_count;
  GrowableArray<JsrRecord*>* _jsr_records;

public:
  // Make a JsrRecord for a given (entry, return) pair, if such a record
  // does not already exist.
  JsrRecord* make_jsr_record(int entry_address, int return_address);

private:
  // Get the initial state for start_bci:
  const StateVector* get_start_state();

  // Merge the current state into all exceptional successors at the
  // current point in the code.
  void flow_exceptions(GrowableArray<Block*>* exceptions,
                       GrowableArray<ciInstanceKlass*>* exc_klasses,
                       StateVector* state);

  // Merge the current state into all successors at the current point
  // in the code.
  void flow_successors(GrowableArray<Block*>* successors,
                       StateVector* state);

  // Interpret the effects of the bytecodes on the incoming state
  // vector of a basic block.  Push the changed state to succeeding
  // basic blocks.
  void flow_block(Block* block,
                  StateVector* scratch_state,
                  JsrSet* scratch_jsrs);

  // Perform the type flow analysis, creating and cloning Blocks as
  // necessary.
  void flow_types();

  // Create the block map, which indexes blocks in pre_order.
  void map_blocks();

public:
  // Perform type inference flow analysis.
  void do_flow();

  void print_on(outputStream* st) const PRODUCT_RETURN;
};
