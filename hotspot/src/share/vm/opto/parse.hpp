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

class BytecodeParseHistogram;
class InlineTree;
class Parse;
class SwitchRange;


//------------------------------InlineTree-------------------------------------
class InlineTree : public ResourceObj {
  Compile*    C;                  // cache
  JVMState*   _caller_jvms;       // state of caller
  ciMethod*   _method;            // method being called by the caller_jvms
  InlineTree* _caller_tree;
  uint        _count_inline_bcs;  // Accumulated count of inlined bytecodes
  // Call-site count / interpreter invocation count, scaled recursively.
  // Always between 0.0 and 1.0.  Represents the percentage of the method's
  // total execution time used at this call site.
  const float _site_invoke_ratio;
  float compute_callee_frequency( int caller_bci ) const;

  GrowableArray<InlineTree*> _subtrees;
  friend class Compile;

protected:
  InlineTree(Compile* C,
             const InlineTree* caller_tree,
             ciMethod* callee_method,
             JVMState* caller_jvms,
             int caller_bci,
             float site_invoke_ratio);
  InlineTree *build_inline_tree_for_callee(ciMethod* callee_method,
                                           JVMState* caller_jvms,
                                           int caller_bci);
  const char* try_to_inline(ciMethod* callee_method, ciMethod* caller_method, int caller_bci, ciCallProfile& profile, WarmCallInfo* wci_result);
  const char* shouldInline(ciMethod* callee_method, ciMethod* caller_method, int caller_bci, ciCallProfile& profile, WarmCallInfo* wci_result) const;
  const char* shouldNotInline(ciMethod* callee_method, ciMethod* caller_method, WarmCallInfo* wci_result) const;
  void        print_inlining(ciMethod *callee_method, int caller_bci, const char *failure_msg) const PRODUCT_RETURN;

  InlineTree *caller_tree()       const { return _caller_tree;  }
  InlineTree* callee_at(int bci, ciMethod* m) const;
  int         inline_depth()      const { return _caller_jvms ? _caller_jvms->depth() : 0; }

public:
  static InlineTree* build_inline_tree_root();
  static InlineTree* find_subtree_from_root(InlineTree* root, JVMState* jvms, ciMethod* callee, bool create_if_not_found = false);

  // For temporary (stack-allocated, stateless) ilts:
  InlineTree(Compile* c, ciMethod* callee_method, JVMState* caller_jvms, float site_invoke_ratio);

  // InlineTree enum
  enum InlineStyle {
    Inline_do_not_inline             =   0, //
    Inline_cha_is_monomorphic        =   1, //
    Inline_type_profile_monomorphic  =   2  //
  };

  // See if it is OK to inline.
  // The receiver is the inline tree for the caller.
  //
  // The result is a temperature indication.  If it is hot or cold,
  // inlining is immediate or undesirable.  Otherwise, the info block
  // returned is newly allocated and may be enqueued.
  //
  // If the method is inlinable, a new inline subtree is created on the fly,
  // and may be accessed by find_subtree_from_root.
  // The call_method is the dest_method for a special or static invocation.
  // The call_method is an optimized virtual method candidate otherwise.
  WarmCallInfo* ok_to_inline(ciMethod *call_method, JVMState* caller_jvms, ciCallProfile& profile, WarmCallInfo* wci);

  // Information about inlined method
  JVMState*   caller_jvms()       const { return _caller_jvms; }
  ciMethod   *method()            const { return _method; }
  int         caller_bci()        const { return _caller_jvms ? _caller_jvms->bci() : InvocationEntryBci; }
  uint        count_inline_bcs()  const { return _count_inline_bcs; }
  float       site_invoke_ratio() const { return _site_invoke_ratio; };

#ifndef PRODUCT
private:
  uint        _count_inlines;     // Count of inlined methods
public:
  // Debug information collected during parse
  uint        count_inlines()     const { return _count_inlines; };
#endif
  GrowableArray<InlineTree*> subtrees() { return _subtrees; }
};


//-----------------------------------------------------------------------------
//------------------------------Parse------------------------------------------
// Parse bytecodes, build a Graph
class Parse : public GraphKit {
 public:
  // Per-block information needed by the parser:
  class Block {
   private:
    ciTypeFlow::Block* _flow;
    int                _pred_count;     // how many predecessors in CFG?
    int                _preds_parsed;   // how many of these have been parsed?
    uint               _count;          // how many times executed?  Currently only set by _goto's
    bool               _is_parsed;      // has this block been parsed yet?
    bool               _is_handler;     // is this block an exception handler?
    SafePointNode*     _start_map;      // all values flowing into this block
    MethodLivenessResult _live_locals;  // lazily initialized liveness bitmap

    int                _num_successors; // Includes only normal control flow.
    int                _all_successors; // Include exception paths also.
    Block**            _successors;

    // Use init_node/init_graph to initialize Blocks.
    // Block() : _live_locals((uintptr_t*)NULL,0) { ShouldNotReachHere(); }
    Block() : _live_locals(NULL,0) { ShouldNotReachHere(); }

   public:

    // Set up the block data structure itself.
    void init_node(Parse* outer, int po);
    // Set up the block's relations to other blocks.
    void init_graph(Parse* outer);

    ciTypeFlow::Block* flow() const        { return _flow; }
    int pred_count() const                 { return _pred_count; }
    int preds_parsed() const               { return _preds_parsed; }
    bool is_parsed() const                 { return _is_parsed; }
    bool is_handler() const                { return _is_handler; }
    void set_count( uint x )               { _count = x; }
    uint count() const                     { return _count; }

    SafePointNode* start_map() const       { assert(is_merged(),"");   return _start_map; }
    void set_start_map(SafePointNode* m)   { assert(!is_merged(), ""); _start_map = m; }

    // True after any predecessor flows control into this block
    bool is_merged() const                 { return _start_map != NULL; }

    // True when all non-exception predecessors have been parsed.
    bool is_ready() const                  { return preds_parsed() == pred_count(); }

    int num_successors() const             { return _num_successors; }
    int all_successors() const             { return _all_successors; }
    Block* successor_at(int i) const {
      assert((uint)i < (uint)all_successors(), "");
      return _successors[i];
    }
    Block* successor_for_bci(int bci);

    int start() const                      { return flow()->start(); }
    int limit() const                      { return flow()->limit(); }
    int rpo() const                        { return flow()->rpo(); }
    int start_sp() const                   { return flow()->stack_size(); }

    bool is_loop_head() const              { return flow()->is_loop_head(); }
    bool is_SEL_head() const               { return flow()->is_single_entry_loop_head(); }
    bool is_SEL_backedge(Block* pred) const{ return is_SEL_head() && pred->rpo() >= rpo(); }
    bool is_invariant_local(uint i) const  {
      const JVMState* jvms = start_map()->jvms();
      if (!jvms->is_loc(i) || flow()->outer()->has_irreducible_entry()) return false;
      return flow()->is_invariant_local(i - jvms->locoff());
    }
    bool can_elide_SEL_phi(uint i) const  { assert(is_SEL_head(),""); return is_invariant_local(i); }

    const Type* peek(int off=0) const      { return stack_type_at(start_sp() - (off+1)); }

    const Type* stack_type_at(int i) const;
    const Type* local_type_at(int i) const;
    static const Type* get_type(ciType* t) { return Type::get_typeflow_type(t); }

    bool has_trap_at(int bci) const        { return flow()->has_trap() && flow()->trap_bci() == bci; }

    // Call this just before parsing a block.
    void mark_parsed() {
      assert(!_is_parsed, "must parse each block exactly once");
      _is_parsed = true;
    }

    // Return the phi/region input index for the "current" pred,
    // and bump the pred number.  For historical reasons these index
    // numbers are handed out in descending order.  The last index is
    // always PhiNode::Input (i.e., 1).  The value returned is known
    // as a "path number" because it distinguishes by which path we are
    // entering the block.
    int next_path_num() {
      assert(preds_parsed() < pred_count(), "too many preds?");
      return pred_count() - _preds_parsed++;
    }

    // Add a previously unaccounted predecessor to this block.
    // This operates by increasing the size of the block's region
    // and all its phi nodes (if any).  The value returned is a
    // path number ("pnum").
    int add_new_path();

    // Initialize me by recording the parser's map.  My own map must be NULL.
    void record_state(Parse* outer);
  };

#ifndef PRODUCT
  // BytecodeParseHistogram collects number of bytecodes parsed, nodes constructed, and transformations.
  class BytecodeParseHistogram : public ResourceObj {
   private:
    enum BPHType {
      BPH_transforms,
      BPH_values
    };
    static bool _initialized;
    static uint _bytecodes_parsed [Bytecodes::number_of_codes];
    static uint _nodes_constructed[Bytecodes::number_of_codes];
    static uint _nodes_transformed[Bytecodes::number_of_codes];
    static uint _new_values       [Bytecodes::number_of_codes];

    Bytecodes::Code _initial_bytecode;
    int             _initial_node_count;
    int             _initial_transforms;
    int             _initial_values;

    Parse     *_parser;
    Compile   *_compiler;

    // Initialization
    static void reset();

    // Return info being collected, select with global flag 'BytecodeParseInfo'
    int current_count(BPHType info_selector);

   public:
    BytecodeParseHistogram(Parse *p, Compile *c);
    static bool initialized();

    // Record info when starting to parse one bytecode
    void set_initial_state( Bytecodes::Code bc );
    // Record results of parsing one bytecode
    void record_change();

    // Profile printing
    static void print(float cutoff = 0.01F); // cutoff in percent
  };

  public:
    // Record work done during parsing
    BytecodeParseHistogram* _parse_histogram;
    void set_parse_histogram(BytecodeParseHistogram *bph) { _parse_histogram = bph; }
    BytecodeParseHistogram* parse_histogram()      { return _parse_histogram; }
#endif

 private:
  friend class Block;

  // Variables which characterize this compilation as a whole:

  JVMState*     _caller;        // JVMS which carries incoming args & state.
  float         _expected_uses; // expected number of calls to this code
  float         _prof_factor;   // discount applied to my profile counts
  int           _depth;         // Inline tree depth, for debug printouts
  const TypeFunc*_tf;           // My kind of function type
  int           _entry_bci;     // the osr bci or InvocationEntryBci

  ciTypeFlow*   _flow;          // Results of previous flow pass.
  Block*        _blocks;        // Array of basic-block structs.
  int           _block_count;   // Number of elements in _blocks.

  GraphKit      _exits;         // Record all normal returns and throws here.
  bool          _wrote_final;   // Did we write a final field?
  bool          _count_invocations; // update and test invocation counter
  bool          _method_data_update; // update method data oop

  // Variables which track Java semantics during bytecode parsing:

  Block*            _block;     // block currently getting parsed
  ciBytecodeStream  _iter;      // stream of this method's bytecodes

  int           _blocks_merged; // Progress meter: state merges from BB preds
  int           _blocks_parsed; // Progress meter: BBs actually parsed

  const FastLockNode* _synch_lock; // FastLockNode for synchronized method

#ifndef PRODUCT
  int _max_switch_depth;        // Debugging SwitchRanges.
  int _est_switch_depth;        // Debugging SwitchRanges.
#endif

 public:
  // Constructor
  Parse(JVMState* caller, ciMethod* parse_method, float expected_uses);

  virtual Parse* is_Parse() const { return (Parse*)this; }

 public:
  // Accessors.
  JVMState*     caller()        const { return _caller; }
  float         expected_uses() const { return _expected_uses; }
  float         prof_factor()   const { return _prof_factor; }
  int           depth()         const { return _depth; }
  const TypeFunc* tf()          const { return _tf; }
  //            entry_bci()     -- see osr_bci, etc.

  ciTypeFlow*   flow()          const { return _flow; }
  //            blocks()        -- see rpo_at, start_block, etc.
  int           block_count()   const { return _block_count; }

  GraphKit&     exits()               { return _exits; }
  bool          wrote_final() const   { return _wrote_final; }
  void      set_wrote_final(bool z)   { _wrote_final = z; }
  bool          count_invocations() const  { return _count_invocations; }
  bool          method_data_update() const { return _method_data_update; }

  Block*             block()    const { return _block; }
  ciBytecodeStream&  iter()           { return _iter; }
  Bytecodes::Code    bc()       const { return _iter.cur_bc(); }

  void set_block(Block* b)            { _block = b; }

  // Derived accessors:
  bool is_normal_parse() const  { return _entry_bci == InvocationEntryBci; }
  bool is_osr_parse() const     { return _entry_bci != InvocationEntryBci; }
  int osr_bci() const           { assert(is_osr_parse(),""); return _entry_bci; }

  void set_parse_bci(int bci);

  // Must this parse be aborted?
  bool failing()                { return C->failing(); }

  Block* rpo_at(int rpo) {
    assert(0 <= rpo && rpo < _block_count, "oob");
    return &_blocks[rpo];
  }
  Block* start_block() {
    return rpo_at(flow()->start_block()->rpo());
  }
  // Can return NULL if the flow pass did not complete a block.
  Block* successor_for_bci(int bci) {
    return block()->successor_for_bci(bci);
  }

 private:
  // Create a JVMS & map for the initial state of this method.
  SafePointNode* create_entry_map();

  // OSR helpers
  Node *fetch_interpreter_state(int index, BasicType bt, Node *local_addrs, Node *local_addrs_base);
  Node* check_interpreter_type(Node* l, const Type* type, SafePointNode* &bad_type_exit);
  void  load_interpreter_state(Node* osr_buf);

  // Functions for managing basic blocks:
  void init_blocks();
  void load_state_from(Block* b);
  void store_state_to(Block* b) { b->record_state(this); }

  // Parse all the basic blocks.
  void do_all_blocks();

  // Parse the current basic block
  void do_one_block();

  // Raise an error if we get a bad ciTypeFlow CFG.
  void handle_missing_successor(int bci);

  // first actions (before BCI 0)
  void do_method_entry();

  // implementation of monitorenter/monitorexit
  void do_monitor_enter();
  void do_monitor_exit();

  // Eagerly create phie throughout the state, to cope with back edges.
  void ensure_phis_everywhere();

  // Merge the current mapping into the basic block starting at bci
  void merge(          int target_bci);
  // Same as plain merge, except that it allocates a new path number.
  void merge_new_path( int target_bci);
  // Merge the current mapping into an exception handler.
  void merge_exception(int target_bci);
  // Helper: Merge the current mapping into the given basic block
  void merge_common(Block* target, int pnum);
  // Helper functions for merging individual cells.
  PhiNode *ensure_phi(       int idx, bool nocreate = false);
  PhiNode *ensure_memory_phi(int idx, bool nocreate = false);
  // Helper to merge the current memory state into the given basic block
  void merge_memory_edges(MergeMemNode* n, int pnum, bool nophi);

  // Parse this bytecode, and alter the Parsers JVM->Node mapping
  void do_one_bytecode();

  // helper function to generate array store check
  void array_store_check();
  // Helper function to generate array load
  void array_load(BasicType etype);
  // Helper function to generate array store
  void array_store(BasicType etype);
  // Helper function to compute array addressing
  Node* array_addressing(BasicType type, int vals, const Type* *result2=NULL);

  // Pass current map to exits
  void return_current(Node* value);

  // Register finalizers on return from Object.<init>
  void call_register_finalizer();

  // Insert a compiler safepoint into the graph
  void add_safepoint();

  // Insert a compiler safepoint into the graph, if there is a back-branch.
  void maybe_add_safepoint(int target_bci) {
    if (UseLoopSafepoints && target_bci <= bci()) {
      add_safepoint();
    }
  }

  // Note:  Intrinsic generation routines may be found in library_call.cpp.

  // Helper function to setup Ideal Call nodes
  void do_call();

  // Helper function to uncommon-trap or bailout for non-compilable call-sites
  bool can_not_compile_call_site(ciMethod *dest_method, ciInstanceKlass *klass);

  // Helper function to identify inlining potential at call-site
  ciMethod* optimize_inlining(ciMethod* caller, int bci, ciInstanceKlass* klass,
                              ciMethod *dest_method, const TypeOopPtr* receiver_type);

  // Helper function to setup for type-profile based inlining
  bool prepare_type_profile_inline(ciInstanceKlass* prof_klass, ciMethod* prof_method);

  // Helper functions for type checking bytecodes:
  void  do_checkcast();
  void  do_instanceof();

  // Helper functions for shifting & arithmetic
  void modf();
  void modd();
  void l2f();

  void do_irem();

  // implementation of _get* and _put* bytecodes
  void do_getstatic() { do_field_access(true,  false); }
  void do_getfield () { do_field_access(true,  true); }
  void do_putstatic() { do_field_access(false, false); }
  void do_putfield () { do_field_access(false, true); }

  // common code for making initial checks and forming addresses
  void do_field_access(bool is_get, bool is_field);
  bool static_field_ok_in_clinit(ciField *field, ciMethod *method);

  // common code for actually performing the load or store
  void do_get_xxx(const TypePtr* obj_type, Node* obj, ciField* field, bool is_field);
  void do_put_xxx(const TypePtr* obj_type, Node* obj, ciField* field, bool is_field);

  // loading from a constant field or the constant pool
  // returns false if push failed (non-perm field constants only, not ldcs)
  bool push_constant(ciConstant con);

  // implementation of object creation bytecodes
  void do_new();
  void do_newarray(BasicType elemtype);
  void do_anewarray();
  void do_multianewarray();
  Node* expand_multianewarray(ciArrayKlass* array_klass, Node* *lengths, int ndimensions);

  // implementation of jsr/ret
  void do_jsr();
  void do_ret();

  float   dynamic_branch_prediction(float &cnt);
  float   branch_prediction(float &cnt, BoolTest::mask btest, int target_bci);
  bool    seems_never_taken(float prob);

  void    do_ifnull(BoolTest::mask btest, Node* c);
  void    do_if(BoolTest::mask btest, Node* c);
  void    repush_if_args();
  void    adjust_map_after_if(BoolTest::mask btest, Node* c, float prob,
                              Block* path, Block* other_path);
  IfNode* jump_if_fork_int(Node* a, Node* b, BoolTest::mask mask);
  Node*   jump_if_join(Node* iffalse, Node* iftrue);
  void    jump_if_true_fork(IfNode *ifNode, int dest_bci_if_true, int prof_table_index);
  void    jump_if_false_fork(IfNode *ifNode, int dest_bci_if_false, int prof_table_index);
  void    jump_if_always_fork(int dest_bci_if_true, int prof_table_index);

  friend class SwitchRange;
  void    do_tableswitch();
  void    do_lookupswitch();
  void    jump_switch_ranges(Node* a, SwitchRange* lo, SwitchRange* hi, int depth = 0);
  bool    create_jump_tables(Node* a, SwitchRange* lo, SwitchRange* hi);

  // helper functions for methodData style profiling
  void test_counter_against_threshold(Node* cnt, int limit);
  void increment_and_test_invocation_counter(int limit);
  void test_for_osr_md_counter_at(ciMethodData* md, ciProfileData* data, ByteSize offset, int limit);
  Node* method_data_addressing(ciMethodData* md, ciProfileData* data, ByteSize offset, Node* idx = NULL, uint stride = 0);
  void increment_md_counter_at(ciMethodData* md, ciProfileData* data, ByteSize offset, Node* idx = NULL, uint stride = 0);
  void set_md_flag_at(ciMethodData* md, ciProfileData* data, int flag_constant);

  void profile_method_entry();
  void profile_taken_branch(int target_bci, bool force_update = false);
  void profile_not_taken_branch(bool force_update = false);
  void profile_call(Node* receiver);
  void profile_generic_call();
  void profile_receiver_type(Node* receiver);
  void profile_ret(int target_bci);
  void profile_null_checkcast();
  void profile_switch_case(int table_index);

  // helper function for call statistics
  void count_compiled_calls(bool at_method_entry, bool is_inline) PRODUCT_RETURN;

  Node_Notes* make_node_notes(Node_Notes* caller_nn);

  // Helper functions for handling normal and abnormal exits.
  void build_exits();

  // Fix up all exceptional control flow exiting a single bytecode.
  void do_exceptions();

  // Fix up all exiting control flow at the end of the parse.
  void do_exits();

  // Add Catch/CatchProjs
  // The call is either a Java call or the VM's rethrow stub
  void catch_call_exceptions(ciExceptionHandlerStream&);

  // Handle all exceptions thrown by the inlined method.
  // Also handles exceptions for individual bytecodes.
  void catch_inline_exceptions(SafePointNode* ex_map);

  // Bytecode classifier, helps decide to use uncommon_trap vs. rethrow_C.
  bool can_rerun_bytecode();

  // Merge the given map into correct exceptional exit state.
  // Assumes that there is no applicable local handler.
  void throw_to_exit(SafePointNode* ex_map);

 public:
#ifndef PRODUCT
  // Handle PrintOpto, etc.
  void show_parse_info();
  void dump_map_adr_mem() const;
  static void print_statistics(); // Print some performance counters
  void dump();
  void dump_bci(int bci);
#endif
};
