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

class Block;
class Bundle;
class C2Compiler;
class CallGenerator;
class ConnectionGraph;
class InlineTree;
class Int_Array;
class Matcher;
class MachNode;
class MachSafePointNode;
class Node;
class Node_Array;
class Node_Notes;
class OptoReg;
class PhaseCFG;
class PhaseGVN;
class PhaseRegAlloc;
class PhaseCCP;
class PhaseCCP_DCE;
class RootNode;
class relocInfo;
class Scope;
class StartNode;
class SafePointNode;
class JVMState;
class TypeData;
class TypePtr;
class TypeFunc;
class Unique_Node_List;
class nmethod;
class WarmCallInfo;

//------------------------------Compile----------------------------------------
// This class defines a top-level Compiler invocation.

class Compile : public Phase {
 public:
  // Fixed alias indexes.  (See also MergeMemNode.)
  enum {
    AliasIdxTop = 1,  // pseudo-index, aliases to nothing (used as sentinel value)
    AliasIdxBot = 2,  // pseudo-index, aliases to everything
    AliasIdxRaw = 3   // hard-wired index for TypeRawPtr::BOTTOM
  };

  // Variant of TraceTime(NULL, &_t_accumulator, TimeCompiler);
  // Integrated with logging.  If logging is turned on, and dolog is true,
  // then brackets are put into the log, with time stamps and node counts.
  // (The time collection itself is always conditionalized on TimeCompiler.)
  class TracePhase : public TraceTime {
   private:
    Compile*    C;
    CompileLog* _log;
   public:
    TracePhase(const char* name, elapsedTimer* accumulator, bool dolog);
    ~TracePhase();
  };

  // Information per category of alias (memory slice)
  class AliasType {
   private:
    friend class Compile;

    int             _index;         // unique index, used with MergeMemNode
    const TypePtr*  _adr_type;      // normalized address type
    ciField*        _field;         // relevant instance field, or null if none
    bool            _is_rewritable; // false if the memory is write-once only
    int             _general_index; // if this is type is an instance, the general
                                    // type that this is an instance of

    void Init(int i, const TypePtr* at);

   public:
    int             index()         const { return _index; }
    const TypePtr*  adr_type()      const { return _adr_type; }
    ciField*        field()         const { return _field; }
    bool            is_rewritable() const { return _is_rewritable; }
    bool            is_volatile()   const { return (_field ? _field->is_volatile() : false); }
    int             general_index() const { return (_general_index != 0) ? _general_index : _index; }

    void set_rewritable(bool z) { _is_rewritable = z; }
    void set_field(ciField* f) {
      assert(!_field,"");
      _field = f;
      if (f->is_final())  _is_rewritable = false;
    }

    void print_on(outputStream* st) PRODUCT_RETURN;
  };

  enum {
    logAliasCacheSize = 6,
    AliasCacheSize = (1<<logAliasCacheSize)
  };
  struct AliasCacheEntry { const TypePtr* _adr_type; int _index; };  // simple duple type
  enum {
    trapHistLength = methodDataOopDesc::_trap_hist_limit
  };

 private:
  // Fixed parameters to this compilation.
  const int             _compile_id;
  const bool            _save_argument_registers; // save/restore arg regs for trampolines
  const bool            _subsume_loads;         // Load can be matched as part of a larger op.
  const bool            _do_escape_analysis;    // Do escape analysis.
  ciMethod*             _method;                // The method being compiled.
  int                   _entry_bci;             // entry bci for osr methods.
  const TypeFunc*       _tf;                    // My kind of signature
  InlineTree*           _ilt;                   // Ditto (temporary).
  address               _stub_function;         // VM entry for stub being compiled, or NULL
  const char*           _stub_name;             // Name of stub or adapter being compiled, or NULL
  address               _stub_entry_point;      // Compile code entry for generated stub, or NULL

  // Control of this compilation.
  int                   _num_loop_opts;         // Number of iterations for doing loop optimiztions
  int                   _max_inline_size;       // Max inline size for this compilation
  int                   _freq_inline_size;      // Max hot method inline size for this compilation
  int                   _fixed_slots;           // count of frame slots not allocated by the register
                                                // allocator i.e. locks, original deopt pc, etc.
  // For deopt
  int                   _orig_pc_slot;
  int                   _orig_pc_slot_offset_in_bytes;

  int                   _major_progress;        // Count of something big happening
  bool                  _deopt_happens;         // TRUE if de-optimization CAN happen
  bool                  _has_loops;             // True if the method _may_ have some loops
  bool                  _has_split_ifs;         // True if the method _may_ have some split-if
  bool                  _has_unsafe_access;     // True if the method _may_ produce faults in unsafe loads or stores.
  bool                  _has_stringbuilder;     // True StringBuffers or StringBuilders are allocated
  uint                  _trap_hist[trapHistLength];  // Cumulative traps
  bool                  _trap_can_recompile;    // Have we emitted a recompiling trap?
  uint                  _decompile_count;       // Cumulative decompilation counts.
  bool                  _do_inlining;           // True if we intend to do inlining
  bool                  _do_scheduling;         // True if we intend to do scheduling
  bool                  _do_freq_based_layout;  // True if we intend to do frequency based block layout
  bool                  _do_count_invocations;  // True if we generate code to count invocations
  bool                  _do_method_data_update; // True if we generate code to update methodDataOops
  int                   _AliasLevel;            // Locally-adjusted version of AliasLevel flag.
  bool                  _print_assembly;        // True if we should dump assembly code for this compilation
#ifndef PRODUCT
  bool                  _trace_opto_output;
  bool                  _parsed_irreducible_loop; // True if ciTypeFlow detected irreducible loops during parsing
#endif

  // Compilation environment.
  Arena                 _comp_arena;            // Arena with lifetime equivalent to Compile
  ciEnv*                _env;                   // CI interface
  CompileLog*           _log;                   // from CompilerThread
  const char*           _failure_reason;        // for record_failure/failing pattern
  GrowableArray<CallGenerator*>* _intrinsics;   // List of intrinsics.
  GrowableArray<Node*>* _macro_nodes;           // List of nodes which need to be expanded before matching.
  ConnectionGraph*      _congraph;
#ifndef PRODUCT
  IdealGraphPrinter*    _printer;
#endif

  // Node management
  uint                  _unique;                // Counter for unique Node indices
  debug_only(static int _debug_idx;)            // Monotonic counter (not reset), use -XX:BreakAtNode=<idx>
  Arena                 _node_arena;            // Arena for new-space Nodes
  Arena                 _old_arena;             // Arena for old-space Nodes, lifetime during xform
  RootNode*             _root;                  // Unique root of compilation, or NULL after bail-out.
  Node*                 _top;                   // Unique top node.  (Reset by various phases.)

  Node*                 _immutable_memory;      // Initial memory state

  Node*                 _recent_alloc_obj;
  Node*                 _recent_alloc_ctl;

  // Blocked array of debugging and profiling information,
  // tracked per node.
  enum { _log2_node_notes_block_size = 8,
         _node_notes_block_size = (1<<_log2_node_notes_block_size)
  };
  GrowableArray<Node_Notes*>* _node_note_array;
  Node_Notes*           _default_node_notes;  // default notes for new nodes

  // After parsing and every bulk phase we hang onto the Root instruction.
  // The RootNode instruction is where the whole program begins.  It produces
  // the initial Control and BOTTOM for everybody else.

  // Type management
  Arena                 _Compile_types;         // Arena for all types
  Arena*                _type_arena;            // Alias for _Compile_types except in Initialize_shared()
  Dict*                 _type_dict;             // Intern table
  void*                 _type_hwm;              // Last allocation (see Type::operator new/delete)
  size_t                _type_last_size;        // Last allocation size (see Type::operator new/delete)
  ciMethod*             _last_tf_m;             // Cache for
  const TypeFunc*       _last_tf;               //  TypeFunc::make
  AliasType**           _alias_types;           // List of alias types seen so far.
  int                   _num_alias_types;       // Logical length of _alias_types
  int                   _max_alias_types;       // Physical length of _alias_types
  AliasCacheEntry       _alias_cache[AliasCacheSize]; // Gets aliases w/o data structure walking

  // Parsing, optimization
  PhaseGVN*             _initial_gvn;           // Results of parse-time PhaseGVN
  Unique_Node_List*     _for_igvn;              // Initial work-list for next round of Iterative GVN
  WarmCallInfo*         _warm_calls;            // Sorted work-list for heat-based inlining.

  GrowableArray<CallGenerator*> _late_inlines;  // List of CallGenerators to be revisited after
                                                // main parsing has finished.

  // Matching, CFG layout, allocation, code generation
  PhaseCFG*             _cfg;                   // Results of CFG finding
  bool                  _select_24_bit_instr;   // We selected an instruction with a 24-bit result
  bool                  _in_24_bit_fp_mode;     // We are emitting instructions with 24-bit results
  int                   _java_calls;            // Number of java calls in the method
  int                   _inner_loops;           // Number of inner loops in the method
  Matcher*              _matcher;               // Engine to map ideal to machine instructions
  PhaseRegAlloc*        _regalloc;              // Results of register allocation.
  int                   _frame_slots;           // Size of total frame in stack slots
  CodeOffsets           _code_offsets;          // Offsets into the code for various interesting entries
  RegMask               _FIRST_STACK_mask;      // All stack slots usable for spills (depends on frame layout)
  Arena*                _indexSet_arena;        // control IndexSet allocation within PhaseChaitin
  void*                 _indexSet_free_block_list; // free list of IndexSet bit blocks

  uint                  _node_bundling_limit;
  Bundle*               _node_bundling_base;    // Information for instruction bundling

  // Instruction bits passed off to the VM
  int                   _method_size;           // Size of nmethod code segment in bytes
  CodeBuffer            _code_buffer;           // Where the code is assembled
  int                   _first_block_size;      // Size of unvalidated entry point code / OSR poison code
  ExceptionHandlerTable _handler_table;         // Table of native-code exception handlers
  ImplicitExceptionTable _inc_table;            // Table of implicit null checks in native code
  OopMapSet*            _oop_map_set;           // Table of oop maps (one for each safepoint location)
  static int            _CompiledZap_count;     // counter compared against CompileZap[First/Last]
  BufferBlob*           _scratch_buffer_blob;   // For temporary code buffers.
  relocInfo*            _scratch_locs_memory;   // For temporary code buffers.

 public:
  // Accessors

  // The Compile instance currently active in this (compiler) thread.
  static Compile* current() {
    return (Compile*) ciEnv::current()->compiler_data();
  }

  // ID for this compilation.  Useful for setting breakpoints in the debugger.
  int               compile_id() const          { return _compile_id; }

  // Does this compilation allow instructions to subsume loads?  User
  // instructions that subsume a load may result in an unschedulable
  // instruction sequence.
  bool              subsume_loads() const       { return _subsume_loads; }
  // Do escape analysis.
  bool              do_escape_analysis() const  { return _do_escape_analysis; }
  bool              save_argument_registers() const { return _save_argument_registers; }


  // Other fixed compilation parameters.
  ciMethod*         method() const              { return _method; }
  int               entry_bci() const           { return _entry_bci; }
  bool              is_osr_compilation() const  { return _entry_bci != InvocationEntryBci; }
  bool              is_method_compilation() const { return (_method != NULL && !_method->flags().is_native()); }
  const TypeFunc*   tf() const                  { assert(_tf!=NULL, ""); return _tf; }
  void         init_tf(const TypeFunc* tf)      { assert(_tf==NULL, ""); _tf = tf; }
  InlineTree*       ilt() const                 { return _ilt; }
  address           stub_function() const       { return _stub_function; }
  const char*       stub_name() const           { return _stub_name; }
  address           stub_entry_point() const    { return _stub_entry_point; }

  // Control of this compilation.
  int               fixed_slots() const         { assert(_fixed_slots >= 0, "");         return _fixed_slots; }
  void          set_fixed_slots(int n)          { _fixed_slots = n; }
  int               major_progress() const      { return _major_progress; }
  void          set_major_progress()            { _major_progress++; }
  void        clear_major_progress()            { _major_progress = 0; }
  int               num_loop_opts() const       { return _num_loop_opts; }
  void          set_num_loop_opts(int n)        { _num_loop_opts = n; }
  int               max_inline_size() const     { return _max_inline_size; }
  void          set_freq_inline_size(int n)     { _freq_inline_size = n; }
  int               freq_inline_size() const    { return _freq_inline_size; }
  void          set_max_inline_size(int n)      { _max_inline_size = n; }
  bool              deopt_happens() const       { return _deopt_happens; }
  bool              has_loops() const           { return _has_loops; }
  void          set_has_loops(bool z)           { _has_loops = z; }
  bool              has_split_ifs() const       { return _has_split_ifs; }
  void          set_has_split_ifs(bool z)       { _has_split_ifs = z; }
  bool              has_unsafe_access() const   { return _has_unsafe_access; }
  void          set_has_unsafe_access(bool z)   { _has_unsafe_access = z; }
  bool              has_stringbuilder() const   { return _has_stringbuilder; }
  void          set_has_stringbuilder(bool z)   { _has_stringbuilder = z; }
  void          set_trap_count(uint r, uint c)  { assert(r < trapHistLength, "oob");        _trap_hist[r] = c; }
  uint              trap_count(uint r) const    { assert(r < trapHistLength, "oob"); return _trap_hist[r]; }
  bool              trap_can_recompile() const  { return _trap_can_recompile; }
  void          set_trap_can_recompile(bool z)  { _trap_can_recompile = z; }
  uint              decompile_count() const     { return _decompile_count; }
  void          set_decompile_count(uint c)     { _decompile_count = c; }
  bool              allow_range_check_smearing() const;
  bool              do_inlining() const         { return _do_inlining; }
  void          set_do_inlining(bool z)         { _do_inlining = z; }
  bool              do_scheduling() const       { return _do_scheduling; }
  void          set_do_scheduling(bool z)       { _do_scheduling = z; }
  bool              do_freq_based_layout() const{ return _do_freq_based_layout; }
  void          set_do_freq_based_layout(bool z){ _do_freq_based_layout = z; }
  bool              do_count_invocations() const{ return _do_count_invocations; }
  void          set_do_count_invocations(bool z){ _do_count_invocations = z; }
  bool              do_method_data_update() const { return _do_method_data_update; }
  void          set_do_method_data_update(bool z) { _do_method_data_update = z; }
  int               AliasLevel() const          { return _AliasLevel; }
  bool              print_assembly() const       { return _print_assembly; }
  void          set_print_assembly(bool z)       { _print_assembly = z; }
  // check the CompilerOracle for special behaviours for this compile
  bool          method_has_option(const char * option) {
    return method() != NULL && method()->has_option(option);
  }
#ifndef PRODUCT
  bool          trace_opto_output() const       { return _trace_opto_output; }
  bool              parsed_irreducible_loop() const { return _parsed_irreducible_loop; }
  void          set_parsed_irreducible_loop(bool z) { _parsed_irreducible_loop = z; }
#endif

  void begin_method() {
#ifndef PRODUCT
    if (_printer) _printer->begin_method(this);
#endif
  }
  void print_method(const char * name, int level = 1) {
#ifndef PRODUCT
    if (_printer) _printer->print_method(this, name, level);
#endif
  }
  void end_method() {
#ifndef PRODUCT
    if (_printer) _printer->end_method();
#endif
  }

  int           macro_count()                   { return _macro_nodes->length(); }
  Node*         macro_node(int idx)             { return _macro_nodes->at(idx); }
  ConnectionGraph* congraph()                   { return _congraph;}
  void add_macro_node(Node * n) {
    //assert(n->is_macro(), "must be a macro node");
    assert(!_macro_nodes->contains(n), " duplicate entry in expand list");
    _macro_nodes->append(n);
  }
  void remove_macro_node(Node * n) {
    // this function may be called twice for a node so check
    // that the node is in the array before attempting to remove it
    if (_macro_nodes->contains(n))
      _macro_nodes->remove(n);
  }

  // Compilation environment.
  Arena*            comp_arena()                { return &_comp_arena; }
  ciEnv*            env() const                 { return _env; }
  CompileLog*       log() const                 { return _log; }
  bool              failing() const             { return _env->failing() || _failure_reason != NULL; }
  const char* failure_reason() { return _failure_reason; }
  bool              failure_reason_is(const char* r) { return (r==_failure_reason) || (r!=NULL && _failure_reason!=NULL && strcmp(r, _failure_reason)==0); }

  void record_failure(const char* reason);
  void record_method_not_compilable(const char* reason, bool all_tiers = false) {
    // All bailouts cover "all_tiers" when TieredCompilation is off.
    if (!TieredCompilation) all_tiers = true;
    env()->record_method_not_compilable(reason, all_tiers);
    // Record failure reason.
    record_failure(reason);
  }
  void record_method_not_compilable_all_tiers(const char* reason) {
    record_method_not_compilable(reason, true);
  }
  bool check_node_count(uint margin, const char* reason) {
    if (unique() + margin > (uint)MaxNodeLimit) {
      record_method_not_compilable(reason);
      return true;
    } else {
      return false;
    }
  }

  // Node management
  uint              unique() const              { return _unique; }
  uint         next_unique()                    { return _unique++; }
  void          set_unique(uint i)              { _unique = i; }
  static int        debug_idx()                 { return debug_only(_debug_idx)+0; }
  static void   set_debug_idx(int i)            { debug_only(_debug_idx = i); }
  Arena*            node_arena()                { return &_node_arena; }
  Arena*            old_arena()                 { return &_old_arena; }
  RootNode*         root() const                { return _root; }
  void          set_root(RootNode* r)           { _root = r; }
  StartNode*        start() const;              // (Derived from root.)
  void         init_start(StartNode* s);
  Node*             immutable_memory();

  Node*             recent_alloc_ctl() const    { return _recent_alloc_ctl; }
  Node*             recent_alloc_obj() const    { return _recent_alloc_obj; }
  void          set_recent_alloc(Node* ctl, Node* obj) {
                                                  _recent_alloc_ctl = ctl;
                                                  _recent_alloc_obj = obj;
                                                }

  // Handy undefined Node
  Node*             top() const                 { return _top; }

  // these are used by guys who need to know about creation and transformation of top:
  Node*             cached_top_node()           { return _top; }
  void          set_cached_top_node(Node* tn);

  GrowableArray<Node_Notes*>* node_note_array() const { return _node_note_array; }
  void set_node_note_array(GrowableArray<Node_Notes*>* arr) { _node_note_array = arr; }
  Node_Notes* default_node_notes() const        { return _default_node_notes; }
  void    set_default_node_notes(Node_Notes* n) { _default_node_notes = n; }

  Node_Notes*       node_notes_at(int idx) {
    return locate_node_notes(_node_note_array, idx, false);
  }
  inline bool   set_node_notes_at(int idx, Node_Notes* value);

  // Copy notes from source to dest, if they exist.
  // Overwrite dest only if source provides something.
  // Return true if information was moved.
  bool copy_node_notes_to(Node* dest, Node* source);

  // Workhorse function to sort out the blocked Node_Notes array:
  inline Node_Notes* locate_node_notes(GrowableArray<Node_Notes*>* arr,
                                       int idx, bool can_grow = false);

  void grow_node_notes(GrowableArray<Node_Notes*>* arr, int grow_by);

  // Type management
  Arena*            type_arena()                { return _type_arena; }
  Dict*             type_dict()                 { return _type_dict; }
  void*             type_hwm()                  { return _type_hwm; }
  size_t            type_last_size()            { return _type_last_size; }
  int               num_alias_types()           { return _num_alias_types; }

  void          init_type_arena()                       { _type_arena = &_Compile_types; }
  void          set_type_arena(Arena* a)                { _type_arena = a; }
  void          set_type_dict(Dict* d)                  { _type_dict = d; }
  void          set_type_hwm(void* p)                   { _type_hwm = p; }
  void          set_type_last_size(size_t sz)           { _type_last_size = sz; }

  const TypeFunc* last_tf(ciMethod* m) {
    return (m == _last_tf_m) ? _last_tf : NULL;
  }
  void set_last_tf(ciMethod* m, const TypeFunc* tf) {
    assert(m != NULL || tf == NULL, "");
    _last_tf_m = m;
    _last_tf = tf;
  }

  AliasType*        alias_type(int                idx)  { assert(idx < num_alias_types(), "oob"); return _alias_types[idx]; }
  AliasType*        alias_type(const TypePtr* adr_type) { return find_alias_type(adr_type, false); }
  bool         have_alias_type(const TypePtr* adr_type);
  AliasType*        alias_type(ciField*         field);

  int               get_alias_index(const TypePtr* at)  { return alias_type(at)->index(); }
  const TypePtr*    get_adr_type(uint aidx)             { return alias_type(aidx)->adr_type(); }
  int               get_general_index(uint aidx)        { return alias_type(aidx)->general_index(); }

  // Building nodes
  void              rethrow_exceptions(JVMState* jvms);
  void              return_values(JVMState* jvms);
  JVMState*         build_start_state(StartNode* start, const TypeFunc* tf);

  // Decide how to build a call.
  // The profile factor is a discount to apply to this site's interp. profile.
  CallGenerator*    call_generator(ciMethod* call_method, int vtable_index, bool call_is_virtual, JVMState* jvms, bool allow_inline, float profile_factor);
  bool should_delay_inlining(ciMethod* call_method, JVMState* jvms);

  // Report if there were too many traps at a current method and bci.
  // Report if a trap was recorded, and/or PerMethodTrapLimit was exceeded.
  // If there is no MDO at all, report no trap unless told to assume it.
  bool too_many_traps(ciMethod* method, int bci, Deoptimization::DeoptReason reason);
  // This version, unspecific to a particular bci, asks if
  // PerMethodTrapLimit was exceeded for all inlined methods seen so far.
  bool too_many_traps(Deoptimization::DeoptReason reason,
                      // Privately used parameter for logging:
                      ciMethodData* logmd = NULL);
  // Report if there were too many recompiles at a method and bci.
  bool too_many_recompiles(ciMethod* method, int bci, Deoptimization::DeoptReason reason);

  // Parsing, optimization
  PhaseGVN*         initial_gvn()               { return _initial_gvn; }
  Unique_Node_List* for_igvn()                  { return _for_igvn; }
  inline void       record_for_igvn(Node* n);   // Body is after class Unique_Node_List.
  void          set_initial_gvn(PhaseGVN *gvn)           { _initial_gvn = gvn; }
  void          set_for_igvn(Unique_Node_List *for_igvn) { _for_igvn = for_igvn; }

  // Replace n by nn using initial_gvn, calling hash_delete and
  // record_for_igvn as needed.
  void gvn_replace_by(Node* n, Node* nn);


  void              identify_useful_nodes(Unique_Node_List &useful);
  void              remove_useless_nodes  (Unique_Node_List &useful);

  WarmCallInfo*     warm_calls() const          { return _warm_calls; }
  void          set_warm_calls(WarmCallInfo* l) { _warm_calls = l; }
  WarmCallInfo* pop_warm_call();

  // Record this CallGenerator for inlining at the end of parsing.
  void              add_late_inline(CallGenerator* cg) { _late_inlines.push(cg); }

  // Matching, CFG layout, allocation, code generation
  PhaseCFG*         cfg()                       { return _cfg; }
  bool              select_24_bit_instr() const { return _select_24_bit_instr; }
  bool              in_24_bit_fp_mode() const   { return _in_24_bit_fp_mode; }
  bool              has_java_calls() const      { return _java_calls > 0; }
  int               java_calls() const          { return _java_calls; }
  int               inner_loops() const         { return _inner_loops; }
  Matcher*          matcher()                   { return _matcher; }
  PhaseRegAlloc*    regalloc()                  { return _regalloc; }
  int               frame_slots() const         { return _frame_slots; }
  int               frame_size_in_words() const; // frame_slots in units of the polymorphic 'words'
  RegMask&          FIRST_STACK_mask()          { return _FIRST_STACK_mask; }
  Arena*            indexSet_arena()            { return _indexSet_arena; }
  void*             indexSet_free_block_list()  { return _indexSet_free_block_list; }
  uint              node_bundling_limit()       { return _node_bundling_limit; }
  Bundle*           node_bundling_base()        { return _node_bundling_base; }
  void          set_node_bundling_limit(uint n) { _node_bundling_limit = n; }
  void          set_node_bundling_base(Bundle* b) { _node_bundling_base = b; }
  bool          starts_bundle(const Node *n) const;
  bool          need_stack_bang(int frame_size_in_bytes) const;
  bool          need_register_stack_bang() const;

  void          set_matcher(Matcher* m)                 { _matcher = m; }
//void          set_regalloc(PhaseRegAlloc* ra)           { _regalloc = ra; }
  void          set_indexSet_arena(Arena* a)            { _indexSet_arena = a; }
  void          set_indexSet_free_block_list(void* p)   { _indexSet_free_block_list = p; }

  // Remember if this compilation changes hardware mode to 24-bit precision
  void set_24_bit_selection_and_mode(bool selection, bool mode) {
    _select_24_bit_instr = selection;
    _in_24_bit_fp_mode   = mode;
  }

  void  set_java_calls(int z) { _java_calls  = z; }
  void set_inner_loops(int z) { _inner_loops = z; }

  // Instruction bits passed off to the VM
  int               code_size()                 { return _method_size; }
  CodeBuffer*       code_buffer()               { return &_code_buffer; }
  int               first_block_size()          { return _first_block_size; }
  void              set_frame_complete(int off) { _code_offsets.set_value(CodeOffsets::Frame_Complete, off); }
  ExceptionHandlerTable*  handler_table()       { return &_handler_table; }
  ImplicitExceptionTable* inc_table()           { return &_inc_table; }
  OopMapSet*        oop_map_set()               { return _oop_map_set; }
  DebugInformationRecorder* debug_info()        { return env()->debug_info(); }
  Dependencies*     dependencies()              { return env()->dependencies(); }
  static int        CompiledZap_count()         { return _CompiledZap_count; }
  BufferBlob*       scratch_buffer_blob()       { return _scratch_buffer_blob; }
  void         init_scratch_buffer_blob();
  void          set_scratch_buffer_blob(BufferBlob* b) { _scratch_buffer_blob = b; }
  relocInfo*        scratch_locs_memory()       { return _scratch_locs_memory; }
  void          set_scratch_locs_memory(relocInfo* b)  { _scratch_locs_memory = b; }

  // emit to scratch blob, report resulting size
  uint              scratch_emit_size(const Node* n);

  enum ScratchBufferBlob {
    MAX_inst_size       = 1024,
    MAX_locs_size       = 128, // number of relocInfo elements
    MAX_const_size      = 128,
    MAX_stubs_size      = 128
  };

  // Major entry point.  Given a Scope, compile the associated method.
  // For normal compilations, entry_bci is InvocationEntryBci.  For on stack
  // replacement, entry_bci indicates the bytecode for which to compile a
  // continuation.
  Compile(ciEnv* ci_env, C2Compiler* compiler, ciMethod* target,
          int entry_bci, bool subsume_loads, bool do_escape_analysis);

  // Second major entry point.  From the TypeFunc signature, generate code
  // to pass arguments from the Java calling convention to the C calling
  // convention.
  Compile(ciEnv* ci_env, const TypeFunc *(*gen)(),
          address stub_function, const char *stub_name,
          int is_fancy_jump, bool pass_tls,
          bool save_arg_registers, bool return_pc);

  // From the TypeFunc signature, generate code to pass arguments
  // from Compiled calling convention to Interpreter's calling convention
  void Generate_Compiled_To_Interpreter_Graph(const TypeFunc *tf, address interpreter_entry);

  // From the TypeFunc signature, generate code to pass arguments
  // from Interpreter's calling convention to Compiler's calling convention
  void Generate_Interpreter_To_Compiled_Graph(const TypeFunc *tf);

  // Are we compiling a method?
  bool has_method() { return method() != NULL; }

  // Maybe print some information about this compile.
  void print_compile_messages();

  // Final graph reshaping, a post-pass after the regular optimizer is done.
  bool final_graph_reshaping();

  // returns true if adr is completely contained in the given alias category
  bool must_alias(const TypePtr* adr, int alias_idx);

  // returns true if adr overlaps with the given alias category
  bool can_alias(const TypePtr* adr, int alias_idx);

  // Driver for converting compiler's IR into machine code bits
  void Output();

  // Accessors for node bundling info.
  Bundle* node_bundling(const Node *n);
  bool valid_bundle_info(const Node *n);

  // Schedule and Bundle the instructions
  void ScheduleAndBundle();

  // Build OopMaps for each GC point
  void BuildOopMaps();

  // Append debug info for the node "local" at safepoint node "sfpt" to the
  // "array",   May also consult and add to "objs", which describes the
  // scalar-replaced objects.
  void FillLocArray( int idx, MachSafePointNode* sfpt,
                     Node *local, GrowableArray<ScopeValue*> *array,
                     GrowableArray<ScopeValue*> *objs );

  // If "objs" contains an ObjectValue whose id is "id", returns it, else NULL.
  static ObjectValue* sv_for_node_id(GrowableArray<ScopeValue*> *objs, int id);
  // Requres that "objs" does not contains an ObjectValue whose id matches
  // that of "sv.  Appends "sv".
  static void set_sv_for_object_node(GrowableArray<ScopeValue*> *objs,
                                     ObjectValue* sv );

  // Process an OopMap Element while emitting nodes
  void Process_OopMap_Node(MachNode *mach, int code_offset);

  // Write out basic block data to code buffer
  void Fill_buffer();

  // Determine which variable sized branches can be shortened
  void Shorten_branches(Label *labels, int& code_size, int& reloc_size, int& stub_size, int& const_size);

  // Compute the size of first NumberOfLoopInstrToAlign instructions
  // at the head of a loop.
  void compute_loop_first_inst_sizes();

  // Compute the information for the exception tables
  void FillExceptionTables(uint cnt, uint *call_returns, uint *inct_starts, Label *blk_labels);

  // Stack slots that may be unused by the calling convention but must
  // otherwise be preserved.  On Intel this includes the return address.
  // On PowerPC it includes the 4 words holding the old TOC & LR glue.
  uint in_preserve_stack_slots();

  // "Top of Stack" slots that may be unused by the calling convention but must
  // otherwise be preserved.
  // On Intel these are not necessary and the value can be zero.
  // On Sparc this describes the words reserved for storing a register window
  // when an interrupt occurs.
  static uint out_preserve_stack_slots();

  // Number of outgoing stack slots killed above the out_preserve_stack_slots
  // for calls to C.  Supports the var-args backing area for register parms.
  uint varargs_C_out_slots_killed() const;

  // Number of Stack Slots consumed by a synchronization entry
  int sync_stack_slots() const;

  // Compute the name of old_SP.  See <arch>.ad for frame layout.
  OptoReg::Name compute_old_SP();

#ifdef ENABLE_ZAP_DEAD_LOCALS
  static bool is_node_getting_a_safepoint(Node*);
  void Insert_zap_nodes();
  Node* call_zap_node(MachSafePointNode* n, int block_no);
#endif

 private:
  // Phase control:
  void Init(int aliaslevel);                     // Prepare for a single compilation
  int  Inline_Warm();                            // Find more inlining work.
  void Finish_Warm();                            // Give up on further inlines.
  void Optimize();                               // Given a graph, optimize it
  void Code_Gen();                               // Generate code from a graph

  // Management of the AliasType table.
  void grow_alias_types();
  AliasCacheEntry* probe_alias_cache(const TypePtr* adr_type);
  const TypePtr *flatten_alias_type(const TypePtr* adr_type) const;
  AliasType* find_alias_type(const TypePtr* adr_type, bool no_create);

  void verify_top(Node*) const PRODUCT_RETURN;

  // Intrinsic setup.
  void           register_library_intrinsics();                            // initializer
  CallGenerator* make_vm_intrinsic(ciMethod* m, bool is_virtual);          // constructor
  int            intrinsic_insertion_index(ciMethod* m, bool is_virtual);  // helper
  CallGenerator* find_intrinsic(ciMethod* m, bool is_virtual);             // query fn
  void           register_intrinsic(CallGenerator* cg);                    // update fn

#ifndef PRODUCT
  static juint  _intrinsic_hist_count[vmIntrinsics::ID_LIMIT];
  static jubyte _intrinsic_hist_flags[vmIntrinsics::ID_LIMIT];
#endif

 public:

  // Note:  Histogram array size is about 1 Kb.
  enum {                        // flag bits:
    _intrinsic_worked = 1,      // succeeded at least once
    _intrinsic_failed = 2,      // tried it but it failed
    _intrinsic_disabled = 4,    // was requested but disabled (e.g., -XX:-InlineUnsafeOps)
    _intrinsic_virtual = 8,     // was seen in the virtual form (rare)
    _intrinsic_both = 16        // was seen in the non-virtual form (usual)
  };
  // Update histogram.  Return boolean if this is a first-time occurrence.
  static bool gather_intrinsic_statistics(vmIntrinsics::ID id,
                                          bool is_virtual, int flags) PRODUCT_RETURN0;
  static void print_intrinsic_statistics() PRODUCT_RETURN;

  // Graph verification code
  // Walk the node list, verifying that there is a one-to-one
  // correspondence between Use-Def edges and Def-Use edges
  // The option no_dead_code enables stronger checks that the
  // graph is strongly connected from root in both directions.
  void verify_graph_edges(bool no_dead_code = false) PRODUCT_RETURN;

  // Print bytecodes, including the scope inlining tree
  void print_codes();

  // End-of-run dumps.
  static void print_statistics() PRODUCT_RETURN;

  // Dump formatted assembly
  void dump_asm(int *pcs = NULL, uint pc_limit = 0) PRODUCT_RETURN;
  void dump_pc(int *pcs, int pc_limit, Node *n);

  // Verify ADLC assumptions during startup
  static void adlc_verification() PRODUCT_RETURN;

  // Definitions of pd methods
  static void pd_compiler2_init();
};
