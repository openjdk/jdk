/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_COMPILE_HPP
#define SHARE_OPTO_COMPILE_HPP

#include "asm/codeBuffer.hpp"
#include "ci/compilerInterface.hpp"
#include "code/debugInfoRec.hpp"
#include "compiler/cHeapStringHolder.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compiler_globals.hpp"
#include "compiler/compilerEvent.hpp"
#include "libadt/dict.hpp"
#include "libadt/vectset.hpp"
#include "memory/resourceArea.hpp"
#include "oops/methodData.hpp"
#include "opto/idealGraphPrinter.hpp"
#include "opto/phase.hpp"
#include "opto/phasetype.hpp"
#include "opto/printinlining.hpp"
#include "opto/regmask.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/ticks.hpp"
#include "utilities/vmEnums.hpp"

class AbstractLockNode;
class AddPNode;
class Block;
class Bundle;
class CallGenerator;
class CallStaticJavaNode;
class CloneMap;
class CompilationFailureInfo;
class ConnectionGraph;
class IdealGraphPrinter;
class InlineTree;
class Matcher;
class MachConstantNode;
class MachConstantBaseNode;
class MachNode;
class MachOper;
class MachSafePointNode;
class Node;
class Node_Array;
class Node_List;
class Node_Notes;
class NodeHash;
class NodeCloneInfo;
class OpaqueTemplateAssertionPredicateNode;
class OptoReg;
class ParsePredicateNode;
class PhaseCFG;
class PhaseGVN;
class PhaseIterGVN;
class PhaseRegAlloc;
class PhaseCCP;
class PhaseOutput;
class RootNode;
class relocInfo;
class StartNode;
class SafePointNode;
class JVMState;
class Type;
class TypeInt;
class TypeInteger;
class TypeKlassPtr;
class TypePtr;
class TypeOopPtr;
class TypeFunc;
class TypeVect;
class Type_Array;
class Unique_Node_List;
class UnstableIfTrap;
class nmethod;
class Node_Stack;
struct Final_Reshape_Counts;
class VerifyMeetResult;

enum LoopOptsMode {
  LoopOptsDefault,
  LoopOptsNone,
  LoopOptsMaxUnroll,
  LoopOptsShenandoahExpand,
  LoopOptsSkipSplitIf,
  LoopOptsVerify
};

// The type of all node counts and indexes.
// It must hold at least 16 bits, but must also be fast to load and store.
// This type, if less than 32 bits, could limit the number of possible nodes.
// (To make this type platform-specific, move to globalDefinitions_xxx.hpp.)
typedef unsigned int node_idx_t;

class NodeCloneInfo {
 private:
  uint64_t _idx_clone_orig;
 public:

  void set_idx(node_idx_t idx) {
    _idx_clone_orig = (_idx_clone_orig & CONST64(0xFFFFFFFF00000000)) | idx;
  }
  node_idx_t idx() const { return (node_idx_t)(_idx_clone_orig & 0xFFFFFFFF); }

  void set_gen(int generation) {
    uint64_t g = (uint64_t)generation << 32;
    _idx_clone_orig = (_idx_clone_orig & 0xFFFFFFFF) | g;
  }
  int gen() const { return (int)(_idx_clone_orig >> 32); }

  void set(uint64_t x) { _idx_clone_orig = x; }
  void set(node_idx_t x, int g) { set_idx(x); set_gen(g); }
  uint64_t get() const { return _idx_clone_orig; }

  NodeCloneInfo(uint64_t idx_clone_orig) : _idx_clone_orig(idx_clone_orig) {}
  NodeCloneInfo(node_idx_t x, int g) : _idx_clone_orig(0) { set(x, g); }

  void dump_on(outputStream* st) const;
};

class CloneMap {
  friend class Compile;
 private:
  bool      _debug;
  Dict*     _dict;
  int       _clone_idx;   // current cloning iteration/generation in loop unroll
 public:
  void*     _2p(node_idx_t key)   const          { return (void*)(intptr_t)key; } // 2 conversion functions to make gcc happy
  node_idx_t _2_node_idx_t(const void* k) const  { return (node_idx_t)(intptr_t)k; }
  Dict*     dict()                const          { return _dict; }
  void insert(node_idx_t key, uint64_t val)      { assert(_dict->operator[](_2p(key)) == nullptr, "key existed"); _dict->Insert(_2p(key), (void*)val); }
  void insert(node_idx_t key, NodeCloneInfo& ci) { insert(key, ci.get()); }
  void remove(node_idx_t key)                    { _dict->Delete(_2p(key)); }
  uint64_t value(node_idx_t key)  const          { return (uint64_t)_dict->operator[](_2p(key)); }
  node_idx_t idx(node_idx_t key)  const          { return NodeCloneInfo(value(key)).idx(); }
  int gen(node_idx_t key)         const          { return NodeCloneInfo(value(key)).gen(); }
  int gen(const void* k)          const          { return gen(_2_node_idx_t(k)); }
  int max_gen()                   const;
  void clone(Node* old, Node* nnn, int gen);
  void verify_insert_and_clone(Node* old, Node* nnn, int gen);
  void dump(node_idx_t key, outputStream* st) const;

  int  clone_idx() const                         { return _clone_idx; }
  void set_clone_idx(int x)                      { _clone_idx = x; }
  bool is_debug()                 const          { return _debug; }
  void set_debug(bool debug)                     { _debug = debug; }

  bool same_idx(node_idx_t k1, node_idx_t k2)  const { return idx(k1) == idx(k2); }
  bool same_gen(node_idx_t k1, node_idx_t k2)  const { return gen(k1) == gen(k2); }
};

class Options {
  friend class Compile;
 private:
  const bool _subsume_loads;         // Load can be matched as part of a larger op.
  const bool _do_escape_analysis;    // Do escape analysis.
  const bool _do_iterative_escape_analysis;  // Do iterative escape analysis.
  const bool _do_reduce_allocation_merges;  // Do try to reduce allocation merges.
  const bool _eliminate_boxing;      // Do boxing elimination.
  const bool _do_locks_coarsening;   // Do locks coarsening
  const bool _do_superword;          // Do SuperWord
  const bool _install_code;          // Install the code that was compiled
 public:
  Options(bool subsume_loads,
          bool do_escape_analysis,
          bool do_iterative_escape_analysis,
          bool do_reduce_allocation_merges,
          bool eliminate_boxing,
          bool do_locks_coarsening,
          bool do_superword,
          bool install_code) :
          _subsume_loads(subsume_loads),
          _do_escape_analysis(do_escape_analysis),
          _do_iterative_escape_analysis(do_iterative_escape_analysis),
          _do_reduce_allocation_merges(do_reduce_allocation_merges),
          _eliminate_boxing(eliminate_boxing),
          _do_locks_coarsening(do_locks_coarsening),
          _do_superword(do_superword),
          _install_code(install_code) {
  }

  static Options for_runtime_stub() {
    return Options(
       /* subsume_loads = */ true,
       /* do_escape_analysis = */ false,
       /* do_iterative_escape_analysis = */ false,
       /* do_reduce_allocation_merges = */ false,
       /* eliminate_boxing = */ false,
       /* do_lock_coarsening = */ false,
       /* do_superword = */ true,
       /* install_code = */ true
    );
  }
};

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

  // Variant of TraceTime(nullptr, &_t_accumulator, CITime);
  // Integrated with logging.  If logging is turned on, and CITimeVerbose is true,
  // then brackets are put into the log, with time stamps and node counts.
  // (The time collection itself is always conditionalized on CITime.)
  class TracePhase : public TraceTime {
   private:
    Compile* const _compile;
    CompileLog* _log;
    const bool _dolog;
   public:
    TracePhase(PhaseTraceId phaseTraceId);
    TracePhase(const char* name, PhaseTraceId phaseTraceId);
    ~TracePhase();
    const char* phase_name() const { return title(); }
  };

  // Information per category of alias (memory slice)
  class AliasType {
   private:
    friend class Compile;

    int             _index;         // unique index, used with MergeMemNode
    const TypePtr*  _adr_type;      // normalized address type
    ciField*        _field;         // relevant instance field, or null if none
    const Type*     _element;       // relevant array element type, or null if none
    bool            _is_rewritable; // false if the memory is write-once only
    int             _general_index; // if this is type is an instance, the general
                                    // type that this is an instance of

    void Init(int i, const TypePtr* at);

   public:
    int             index()         const { return _index; }
    const TypePtr*  adr_type()      const { return _adr_type; }
    ciField*        field()         const { return _field; }
    const Type*     element()       const { return _element; }
    bool            is_rewritable() const { return _is_rewritable; }
    bool            is_volatile()   const { return (_field ? _field->is_volatile() : false); }
    int             general_index() const { return (_general_index != 0) ? _general_index : _index; }

    void set_rewritable(bool z) { _is_rewritable = z; }
    void set_field(ciField* f) {
      assert(!_field,"");
      _field = f;
      if (f->is_final() || f->is_stable()) {
        // In the case of @Stable, multiple writes are possible but may be assumed to be no-ops.
        _is_rewritable = false;
      }
    }
    void set_element(const Type* e) {
      assert(_element == nullptr, "");
      _element = e;
    }

    BasicType basic_type() const;

    void print_on(outputStream* st) PRODUCT_RETURN;
  };

  enum {
    logAliasCacheSize = 6,
    AliasCacheSize = (1<<logAliasCacheSize)
  };
  struct AliasCacheEntry { const TypePtr* _adr_type; int _index; };  // simple duple type
  enum {
    trapHistLength = MethodData::_trap_hist_limit
  };

 private:
  // Fixed parameters to this compilation.
  const int             _compile_id;
  const Options         _options;               // Compilation options
  ciMethod*             _method;                // The method being compiled.
  int                   _entry_bci;             // entry bci for osr methods.
  const TypeFunc*       _tf;                    // My kind of signature
  InlineTree*           _ilt;                   // Ditto (temporary).
  address               _stub_function;         // VM entry for stub being compiled, or null
  const char*           _stub_name;             // Name of stub or adapter being compiled, or null
  StubId                   _stub_id;               // unique id for stub or NO_STUBID
  address               _stub_entry_point;      // Compile code entry for generated stub, or null

  // Control of this compilation.
  int                   _max_inline_size;       // Max inline size for this compilation
  int                   _freq_inline_size;      // Max hot method inline size for this compilation
  int                   _fixed_slots;           // count of frame slots not allocated by the register
                                                // allocator i.e. locks, original deopt pc, etc.
  uintx                 _max_node_limit;        // Max unique node count during a single compilation.

  bool                  _post_loop_opts_phase;  // Loop opts are finished.
  bool                  _merge_stores_phase;    // Phase for merging stores, after post loop opts phase.
  bool                  _allow_macro_nodes;     // True if we allow creation of macro nodes.

  /* If major progress is set:
   *   Marks that the loop tree information (get_ctrl, idom, get_loop, etc.) could be invalid, and we need to rebuild the loop tree.
   *   It also indicates that the graph was changed in a way that is promising to be able to apply more loop optimization.
   * If major progress is not set:
   *   Loop tree information is valid.
   *   If major progress is not set at the end of a loop opts phase, then we can stop loop opts, because we do not expect any further progress if we did more loop opts phases.
   *
   * This is not 100% accurate, the semantics of major progress has become less clear over time, but this is the general idea.
   */
  bool                  _major_progress;
  bool                  _inlining_progress;     // progress doing incremental inlining?
  bool                  _inlining_incrementally;// Are we doing incremental inlining (post parse)
  bool                  _do_cleanup;            // Cleanup is needed before proceeding with incremental inlining
  bool                  _has_loops;             // True if the method _may_ have some loops
  bool                  _has_split_ifs;         // True if the method _may_ have some split-if
  bool                  _has_unsafe_access;     // True if the method _may_ produce faults in unsafe loads or stores.
  bool                  _has_stringbuilder;     // True StringBuffers or StringBuilders are allocated
  bool                  _has_boxed_value;       // True if a boxed object is allocated
  bool                  _has_reserved_stack_access; // True if the method or an inlined method is annotated with ReservedStackAccess
  uint                  _max_vector_size;       // Maximum size of generated vectors
  bool                  _clear_upper_avx;       // Clear upper bits of ymm registers using vzeroupper
  uint                  _trap_hist[trapHistLength];  // Cumulative traps
  bool                  _trap_can_recompile;    // Have we emitted a recompiling trap?
  uint                  _decompile_count;       // Cumulative decompilation counts.
  bool                  _do_inlining;           // True if we intend to do inlining
  bool                  _do_scheduling;         // True if we intend to do scheduling
  bool                  _do_freq_based_layout;  // True if we intend to do frequency based block layout
  bool                  _do_vector_loop;        // True if allowed to execute loop in parallel iterations
  bool                  _use_cmove;             // True if CMove should be used without profitability analysis
  bool                  _do_aliasing;           // True if we intend to do aliasing
  bool                  _print_assembly;        // True if we should dump assembly code for this compilation
  bool                  _print_inlining;        // True if we should print inlining for this compilation
  bool                  _print_intrinsics;      // True if we should print intrinsics for this compilation
  bool                  _print_phase_loop_opts; // True if we should print before and after loop opts phase
#ifndef PRODUCT
  uint                  _phase_counter;         // Counter for the number of already printed phases
  uint                  _igv_idx;               // Counter for IGV node identifiers
  uint                  _igv_phase_iter[PHASE_NUM_TYPES]; // Counters for IGV phase iterations
  bool                  _trace_opto_output;
  bool                  _parsed_irreducible_loop; // True if ciTypeFlow detected irreducible loops during parsing
#endif
  bool                  _has_irreducible_loop;  // Found irreducible loops
  bool                  _has_monitors;          // Metadata transfered to nmethod to enable Continuations lock-detection fastpath
  bool                  _has_scoped_access;     // For shared scope closure
  bool                  _clinit_barrier_on_entry; // True if clinit barrier is needed on nmethod entry
  int                   _loop_opts_cnt;         // loop opts round
  uint                  _stress_seed;           // Seed for stress testing

  // Compilation environment.
  Arena                 _comp_arena;            // Arena with lifetime equivalent to Compile
  void*                 _barrier_set_state;     // Potential GC barrier state for Compile
  ciEnv*                _env;                   // CI interface
  DirectiveSet*         _directive;             // Compiler directive
  CompileLog*           _log;                   // from CompilerThread
  CHeapStringHolder     _failure_reason;        // for record_failure/failing pattern
  CompilationFailureInfo* _first_failure_details; // Details for the first failure happening during compilation
  GrowableArray<CallGenerator*> _intrinsics;    // List of intrinsics.
  GrowableArray<Node*>  _macro_nodes;           // List of nodes which need to be expanded before matching.
  GrowableArray<ParsePredicateNode*> _parse_predicates; // List of Parse Predicates.
  // List of OpaqueTemplateAssertionPredicateNode nodes for Template Assertion Predicates which can be seen as list
  // of Template Assertion Predicates themselves.
  GrowableArray<OpaqueTemplateAssertionPredicateNode*>  _template_assertion_predicate_opaques;
  GrowableArray<Node*>  _expensive_nodes;       // List of nodes that are expensive to compute and that we'd better not let the GVN freely common
  GrowableArray<Node*>  _for_post_loop_igvn;    // List of nodes for IGVN after loop opts are over
  GrowableArray<Node*>  _for_merge_stores_igvn; // List of nodes for IGVN merge stores
  GrowableArray<UnstableIfTrap*> _unstable_if_traps;        // List of ifnodes after IGVN
  GrowableArray<Node_List*> _coarsened_locks;   // List of coarsened Lock and Unlock nodes
  ConnectionGraph*      _congraph;
#ifndef PRODUCT
  IdealGraphPrinter*    _igv_printer;
  static IdealGraphPrinter* _debug_file_printer;
  static IdealGraphPrinter* _debug_network_printer;
#endif


  // Node management
  uint                  _unique;                // Counter for unique Node indices
  uint                  _dead_node_count;       // Number of dead nodes; VectorSet::Size() is O(N).
                                                // So use this to keep count and make the call O(1).
  VectorSet             _dead_node_list;        // Set of dead nodes
  DEBUG_ONLY(Unique_Node_List* _modified_nodes;)   // List of nodes which inputs were modified
  DEBUG_ONLY(bool       _phase_optimize_finished;) // Used for live node verification while creating new nodes

  DEBUG_ONLY(bool       _phase_verify_ideal_loop;) // Are we in PhaseIdealLoop verification?

  // Arenas for new-space and old-space nodes.
  // Swapped between using _node_arena.
  // The lifetime of the old-space nodes is during xform.
  Arena                 _node_arena_one;
  Arena                 _node_arena_two;
  Arena*                _node_arena;
public:
  Arena* swap_old_and_new() {
    Arena* filled_arena_ptr = _node_arena;
    Arena* old_arena_ptr = old_arena();
    old_arena_ptr->destruct_contents();
    _node_arena = old_arena_ptr;
    return filled_arena_ptr;
  }
private:
  RootNode*             _root;                  // Unique root of compilation, or null after bail-out.
  Node*                 _top;                   // Unique top node.  (Reset by various phases.)

  Node*                 _immutable_memory;      // Initial memory state

  Node*                 _recent_alloc_obj;
  Node*                 _recent_alloc_ctl;

  // Constant table
  MachConstantBaseNode* _mach_constant_base_node;  // Constant table base node singleton.


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
  CloneMap              _clone_map;             // used for recording history of cloned nodes
  size_t                _type_last_size;        // Last allocation size (see Type::operator new/delete)
  ciMethod*             _last_tf_m;             // Cache for
  const TypeFunc*       _last_tf;               //  TypeFunc::make
  AliasType**           _alias_types;           // List of alias types seen so far.
  int                   _num_alias_types;       // Logical length of _alias_types
  int                   _max_alias_types;       // Physical length of _alias_types
  AliasCacheEntry       _alias_cache[AliasCacheSize]; // Gets aliases w/o data structure walking

  // Parsing, optimization
  PhaseGVN*             _initial_gvn;           // Results of parse-time PhaseGVN

  // Shared worklist for all IGVN rounds. Nodes can be pushed to it at any time.
  // If pushed outside IGVN, the Node is processed in the next IGVN round.
  Unique_Node_List*     _igvn_worklist;

  // Shared type array for GVN, IGVN and CCP. It maps node idx -> Type*.
  Type_Array*           _types;

  // Shared node hash table for GVN, IGVN and CCP.
  NodeHash*             _node_hash;

  GrowableArray<CallGenerator*> _late_inlines;        // List of CallGenerators to be revisited after main parsing has finished.
  GrowableArray<CallGenerator*> _string_late_inlines; // same but for string operations
  GrowableArray<CallGenerator*> _boxing_late_inlines; // same but for boxing operations

  GrowableArray<CallGenerator*> _vector_reboxing_late_inlines; // same but for vector reboxing operations

  int                           _late_inlines_pos;    // Where in the queue should the next late inlining candidate go (emulate depth first inlining)
  bool                          _has_mh_late_inlines; // Can there still be a method handle late inlining pending?
                                                      // false: there can't be one
                                                      // true: we've enqueued one at some point so there may still be one

  // "MemLimit" directive was specified and the memory limit was hit during compilation
  bool                          _oom;

  // Only keep nodes in the expensive node list that need to be optimized
  void cleanup_expensive_nodes(PhaseIterGVN &igvn);
  // Use for sorting expensive nodes to bring similar nodes together
  static int cmp_expensive_nodes(Node** n1, Node** n2);
  // Expensive nodes list already sorted?
  bool expensive_nodes_sorted() const;
  // Remove the speculative part of types and clean up the graph
  void remove_speculative_types(PhaseIterGVN &igvn);

  void* _replay_inline_data; // Pointer to data loaded from file

  void log_late_inline_failure(CallGenerator* cg, const char* msg);
  DEBUG_ONLY(bool _exception_backedge;)

  void record_method_not_compilable_oom();

  InlinePrinter _inline_printer;

public:
  void* barrier_set_state() const { return _barrier_set_state; }

  InlinePrinter* inline_printer() { return &_inline_printer; }

#ifndef PRODUCT
  IdealGraphPrinter* igv_printer() { return _igv_printer; }
  void reset_igv_phase_iter(CompilerPhaseType cpt) { _igv_phase_iter[cpt] = 0; }
#endif

  void log_late_inline(CallGenerator* cg);
  void log_inline_id(CallGenerator* cg);
  void log_inline_failure(const char* msg);

  void* replay_inline_data() const { return _replay_inline_data; }

  // Dump inlining replay data to the stream.
  void dump_inline_data(outputStream* out);
  void dump_inline_data_reduced(outputStream* out);

 private:
  // Matching, CFG layout, allocation, code generation
  PhaseCFG*             _cfg;                   // Results of CFG finding
  int                   _java_calls;            // Number of java calls in the method
  int                   _inner_loops;           // Number of inner loops in the method
  Matcher*              _matcher;               // Engine to map ideal to machine instructions
  PhaseRegAlloc*        _regalloc;              // Results of register allocation.
  RegMask               _FIRST_STACK_mask;      // All stack slots usable for spills (depends on frame layout)
  Arena*                _indexSet_arena;        // control IndexSet allocation within PhaseChaitin
  void*                 _indexSet_free_block_list; // free list of IndexSet bit blocks
  int                   _interpreter_frame_size;

  // Holds dynamically allocated extensions of short-lived register masks. Such
  // extensions are potentially quite large and need tight resource marks which
  // may conflict with other allocations in the default resource area.
  // Therefore, we use a dedicated resource area for register masks.
  ResourceArea          _regmask_arena;

  PhaseOutput*          _output;

 public:
  // Accessors

  // The Compile instance currently active in this (compiler) thread.
  static Compile* current() {
    return (Compile*) ciEnv::current()->compiler_data();
  }

  int interpreter_frame_size() const            { return _interpreter_frame_size; }

  PhaseOutput*      output() const              { return _output; }
  void              set_output(PhaseOutput* o)  { _output = o; }

  // ID for this compilation.  Useful for setting breakpoints in the debugger.
  int               compile_id() const          { return _compile_id; }
  DirectiveSet*     directive() const           { return _directive; }

  // Does this compilation allow instructions to subsume loads?  User
  // instructions that subsume a load may result in an unschedulable
  // instruction sequence.
  bool              subsume_loads() const       { return _options._subsume_loads; }
  /** Do escape analysis. */
  bool              do_escape_analysis() const  { return _options._do_escape_analysis; }
  bool              do_iterative_escape_analysis() const  { return _options._do_iterative_escape_analysis; }
  bool              do_reduce_allocation_merges() const  { return _options._do_reduce_allocation_merges; }
  /** Do boxing elimination. */
  bool              eliminate_boxing() const    { return _options._eliminate_boxing; }
  /** Do aggressive boxing elimination. */
  bool              aggressive_unboxing() const { return _options._eliminate_boxing && AggressiveUnboxing; }
  bool              should_install_code() const { return _options._install_code; }
  /** Do locks coarsening. */
  bool              do_locks_coarsening() const { return _options._do_locks_coarsening; }
  bool              do_superword() const        { return _options._do_superword; }

  // Other fixed compilation parameters.
  ciMethod*         method() const              { return _method; }
  int               entry_bci() const           { return _entry_bci; }
  bool              is_osr_compilation() const  { return _entry_bci != InvocationEntryBci; }
  bool              is_method_compilation() const { return (_method != nullptr && !_method->flags().is_native()); }
  const TypeFunc*   tf() const                  { assert(_tf!=nullptr, ""); return _tf; }
  void         init_tf(const TypeFunc* tf)      { assert(_tf==nullptr, ""); _tf = tf; }
  InlineTree*       ilt() const                 { return _ilt; }
  address           stub_function() const       { return _stub_function; }
  const char*       stub_name() const           { return _stub_name; }
  StubId            stub_id() const             { return _stub_id; }
  address           stub_entry_point() const    { return _stub_entry_point; }
  void          set_stub_entry_point(address z) { _stub_entry_point = z; }

  // Control of this compilation.
  int               fixed_slots() const         { assert(_fixed_slots >= 0, "");         return _fixed_slots; }
  void          set_fixed_slots(int n)          { _fixed_slots = n; }
  void          set_inlining_progress(bool z)   { _inlining_progress = z; }
  int               inlining_progress() const   { return _inlining_progress; }
  void          set_inlining_incrementally(bool z) { _inlining_incrementally = z; }
  int               inlining_incrementally() const { return _inlining_incrementally; }
  void          set_do_cleanup(bool z)          { _do_cleanup = z; }
  int               do_cleanup() const          { return _do_cleanup; }
  bool              major_progress() const      { return _major_progress; }
  void          set_major_progress()            { _major_progress = true; }
  void          restore_major_progress(bool progress) { _major_progress = _major_progress || progress; }
  void        clear_major_progress()            { _major_progress = false; }
  int               max_inline_size() const     { return _max_inline_size; }
  void          set_freq_inline_size(int n)     { _freq_inline_size = n; }
  int               freq_inline_size() const    { return _freq_inline_size; }
  void          set_max_inline_size(int n)      { _max_inline_size = n; }
  bool              has_loops() const           { return _has_loops; }
  void          set_has_loops(bool z)           { _has_loops = z; }
  bool              has_split_ifs() const       { return _has_split_ifs; }
  void          set_has_split_ifs(bool z)       { _has_split_ifs = z; }
  bool              has_unsafe_access() const   { return _has_unsafe_access; }
  void          set_has_unsafe_access(bool z)   { _has_unsafe_access = z; }
  bool              has_stringbuilder() const   { return _has_stringbuilder; }
  void          set_has_stringbuilder(bool z)   { _has_stringbuilder = z; }
  bool              has_boxed_value() const     { return _has_boxed_value; }
  void          set_has_boxed_value(bool z)     { _has_boxed_value = z; }
  bool              has_reserved_stack_access() const { return _has_reserved_stack_access; }
  void          set_has_reserved_stack_access(bool z) { _has_reserved_stack_access = z; }
  uint              max_vector_size() const     { return _max_vector_size; }
  void          set_max_vector_size(uint s)     { _max_vector_size = s; }
  bool              clear_upper_avx() const     { return _clear_upper_avx; }
  void          set_clear_upper_avx(bool s)     { _clear_upper_avx = s; }
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
  bool              do_vector_loop() const      { return _do_vector_loop; }
  void          set_do_vector_loop(bool z)      { _do_vector_loop = z; }
  bool              use_cmove() const           { return _use_cmove; }
  void          set_use_cmove(bool z)           { _use_cmove = z; }
  bool              do_aliasing() const          { return _do_aliasing; }
  bool              print_assembly() const       { return _print_assembly; }
  void          set_print_assembly(bool z)       { _print_assembly = z; }
  bool              print_inlining() const       { return _print_inlining; }
  void          set_print_inlining(bool z)       { _print_inlining = z; }
  bool              print_intrinsics() const     { return _print_intrinsics; }
  void          set_print_intrinsics(bool z)     { _print_intrinsics = z; }
  uint              max_node_limit() const       { return (uint)_max_node_limit; }
  void          set_max_node_limit(uint n)       { _max_node_limit = n; }
  bool              clinit_barrier_on_entry()       { return _clinit_barrier_on_entry; }
  void          set_clinit_barrier_on_entry(bool z) { _clinit_barrier_on_entry = z; }
  bool              has_monitors() const         { return _has_monitors; }
  void          set_has_monitors(bool v)         { _has_monitors = v; }
  bool              has_scoped_access() const    { return _has_scoped_access; }
  void          set_has_scoped_access(bool v)    { _has_scoped_access = v; }

  // check the CompilerOracle for special behaviours for this compile
  bool          method_has_option(CompileCommandEnum option) const {
    return method() != nullptr && method()->has_option(option);
  }

#ifndef PRODUCT
  uint          next_igv_idx()                  { return _igv_idx++; }
  bool          trace_opto_output() const       { return _trace_opto_output; }
  void          print_phase(const char* phase_name);
  void          print_ideal_ir(const char* compile_phase_name) const;
  bool          should_print_ideal() const      { return _directive->PrintIdealOption; }
  bool              parsed_irreducible_loop() const { return _parsed_irreducible_loop; }
  void          set_parsed_irreducible_loop(bool z) { _parsed_irreducible_loop = z; }
  int _in_dump_cnt;  // Required for dumping ir nodes.
#endif
  bool              has_irreducible_loop() const { return _has_irreducible_loop; }
  void          set_has_irreducible_loop(bool z) { _has_irreducible_loop = z; }

  Ticks _latest_stage_start_counter;

  void begin_method();
  void end_method();

  void print_method(CompilerPhaseType compile_phase, int level, Node* n = nullptr);

#ifndef PRODUCT
  bool should_print_igv(int level);
  bool should_print_phase(int level) const;
  bool should_print_ideal_phase(CompilerPhaseType cpt) const;
  void init_igv();
  void dump_igv(const char* graph_name, int level = 3) {
    if (should_print_igv(level)) {
      _igv_printer->print_graph(graph_name, nullptr);
    }
  }

  void igv_print_method_to_file(const char* phase_name = nullptr, bool append = false, const frame* fr = nullptr);
  void igv_print_method_to_network(const char* phase_name = nullptr, const frame* fr = nullptr);
  void igv_print_graph_to_network(const char* name, GrowableArray<const Node*>& visible_nodes, const frame* fr);
  static IdealGraphPrinter* debug_file_printer() { return _debug_file_printer; }
  static IdealGraphPrinter* debug_network_printer() { return _debug_network_printer; }
#endif

  const GrowableArray<ParsePredicateNode*>& parse_predicates() const {
    return _parse_predicates;
  }

  const GrowableArray<OpaqueTemplateAssertionPredicateNode*>& template_assertion_predicate_opaques() const {
    return _template_assertion_predicate_opaques;
  }

  int           macro_count()             const { return _macro_nodes.length(); }
  int           parse_predicate_count()   const { return _parse_predicates.length(); }
  int           template_assertion_predicate_count() const { return _template_assertion_predicate_opaques.length(); }
  int           expensive_count()         const { return _expensive_nodes.length(); }
  int           coarsened_count()         const { return _coarsened_locks.length(); }

  Node*         macro_node(int idx)       const { return _macro_nodes.at(idx); }

  Node*         expensive_node(int idx)   const { return _expensive_nodes.at(idx); }

  ConnectionGraph* congraph()                   { return _congraph;}
  void set_congraph(ConnectionGraph* congraph)  { _congraph = congraph;}
  void add_macro_node(Node * n) {
    //assert(n->is_macro(), "must be a macro node");
    assert(!_macro_nodes.contains(n), "duplicate entry in expand list");
    _macro_nodes.append(n);
  }
  void remove_macro_node(Node* n) {
    // this function may be called twice for a node so we can only remove it
    // if it's still existing.
    _macro_nodes.remove_if_existing(n);
    // Remove from coarsened locks list if present
    if (coarsened_count() > 0) {
      remove_coarsened_lock(n);
    }
  }
  void add_expensive_node(Node* n);
  void remove_expensive_node(Node* n) {
    _expensive_nodes.remove_if_existing(n);
  }

  void add_parse_predicate(ParsePredicateNode* n) {
    assert(!_parse_predicates.contains(n), "duplicate entry in Parse Predicate list");
    _parse_predicates.append(n);
  }

  void remove_parse_predicate(ParsePredicateNode* n) {
    if (parse_predicate_count() > 0) {
      _parse_predicates.remove_if_existing(n);
    }
  }

  void add_template_assertion_predicate_opaque(OpaqueTemplateAssertionPredicateNode* n) {
    assert(!_template_assertion_predicate_opaques.contains(n),
           "Duplicate entry in Template Assertion Predicate OpaqueTemplateAssertionPredicate list");
    _template_assertion_predicate_opaques.append(n);
  }

  void remove_template_assertion_predicate_opaque(OpaqueTemplateAssertionPredicateNode* n) {
    if (template_assertion_predicate_count() > 0) {
      _template_assertion_predicate_opaques.remove_if_existing(n);
    }
  }
  void add_coarsened_locks(GrowableArray<AbstractLockNode*>& locks);
  void remove_coarsened_lock(Node* n);
  bool coarsened_locks_consistent();
  void mark_unbalanced_boxes() const;

  bool       post_loop_opts_phase() { return _post_loop_opts_phase;  }
  void   set_post_loop_opts_phase() { _post_loop_opts_phase = true;  }
  void reset_post_loop_opts_phase() { _post_loop_opts_phase = false; }

#ifdef ASSERT
  bool       phase_verify_ideal_loop() const { return _phase_verify_ideal_loop; }
  void   set_phase_verify_ideal_loop() { _phase_verify_ideal_loop = true; }
  void reset_phase_verify_ideal_loop() { _phase_verify_ideal_loop = false; }
#endif

  bool       allow_macro_nodes() { return _allow_macro_nodes;  }
  void reset_allow_macro_nodes() { _allow_macro_nodes = false;  }

  void record_for_post_loop_opts_igvn(Node* n);
  void remove_from_post_loop_opts_igvn(Node* n);
  void process_for_post_loop_opts_igvn(PhaseIterGVN& igvn);

  void record_unstable_if_trap(UnstableIfTrap* trap);
  bool remove_unstable_if_trap(CallStaticJavaNode* unc, bool yield);
  void remove_useless_unstable_if_traps(Unique_Node_List &useful);
  void process_for_unstable_if_traps(PhaseIterGVN& igvn);

  bool     merge_stores_phase() { return _merge_stores_phase;  }
  void set_merge_stores_phase() { _merge_stores_phase = true;  }
  void record_for_merge_stores_igvn(Node* n);
  void remove_from_merge_stores_igvn(Node* n);
  void process_for_merge_stores_igvn(PhaseIterGVN& igvn);

  void shuffle_macro_nodes();
  void sort_macro_nodes();

  void mark_parse_predicate_nodes_useless(PhaseIterGVN& igvn);

  // Are there candidate expensive nodes for optimization?
  bool should_optimize_expensive_nodes(PhaseIterGVN &igvn);
  // Check whether n1 and n2 are similar
  static int cmp_expensive_nodes(Node* n1, Node* n2);
  // Sort expensive nodes to locate similar expensive nodes
  void sort_expensive_nodes();

  // Compilation environment.
  Arena*      comp_arena()           { return &_comp_arena; }
  ciEnv*      env() const            { return _env; }
  CompileLog* log() const            { return _log; }

  bool        failing_internal() const {
    return _env->failing() ||
           _failure_reason.get() != nullptr;
  }

  const char* failure_reason() const {
    return _env->failing() ? _env->failure_reason()
                           : _failure_reason.get();
  }

  const CompilationFailureInfo* first_failure_details() const { return _first_failure_details; }

  bool failing() {
    if (failing_internal()) {
      return true;
    }
#ifdef ASSERT
    // Disable stress code for PhaseIdealLoop verification (would have cascading effects).
    if (phase_verify_ideal_loop()) {
      return false;
    }
    if (StressBailout) {
      return fail_randomly();
    }
#endif
    return false;
  }

#ifdef ASSERT
  bool fail_randomly();
  bool failure_is_artificial();
#endif

  bool failure_reason_is(const char* r) const {
    return (r == _failure_reason.get()) ||
           (r != nullptr &&
            _failure_reason.get() != nullptr &&
            strcmp(r, _failure_reason.get()) == 0);
  }

  void record_failure(const char* reason DEBUG_ONLY(COMMA bool allow_multiple_failures = false));
  void record_method_not_compilable(const char* reason DEBUG_ONLY(COMMA bool allow_multiple_failures = false)) {
    env()->record_method_not_compilable(reason);
    // Record failure reason.
    record_failure(reason DEBUG_ONLY(COMMA allow_multiple_failures));
  }
  bool check_node_count(uint margin, const char* reason) {
    if (oom()) {
      record_method_not_compilable_oom();
      return true;
    }
    if (live_nodes() + margin > max_node_limit()) {
      record_method_not_compilable(reason);
      return true;
    } else {
      return false;
    }
  }
  bool oom() const { return _oom; }
  void set_oom()   { _oom = true; }

  // Node management
  uint         unique() const              { return _unique; }
  uint         next_unique()               { return _unique++; }
  void         set_unique(uint i)          { _unique = i; }
  Arena*       node_arena()                { return _node_arena; }
  Arena*       old_arena()                 { return (&_node_arena_one == _node_arena) ? &_node_arena_two : &_node_arena_one; }
  RootNode*    root() const                { return _root; }
  void         set_root(RootNode* r)       { _root = r; }
  StartNode*   start() const;              // (Derived from root.)
  void         verify_start(StartNode* s) const NOT_DEBUG_RETURN;
  Node*        immutable_memory();

  Node*        recent_alloc_ctl() const    { return _recent_alloc_ctl; }
  Node*        recent_alloc_obj() const    { return _recent_alloc_obj; }
  void         set_recent_alloc(Node* ctl, Node* obj) {
                                                  _recent_alloc_ctl = ctl;
                                                  _recent_alloc_obj = obj;
                                           }
  void         record_dead_node(uint idx)  { if (_dead_node_list.test_set(idx)) return;
                                             _dead_node_count++;
                                           }
  void         reset_dead_node_list()      { _dead_node_list.reset();
                                             _dead_node_count = 0;
                                           }
  uint          live_nodes() const         {
    int  val = _unique - _dead_node_count;
    assert (val >= 0, "number of tracked dead nodes %d more than created nodes %d", _unique, _dead_node_count);
            return (uint) val;
                                           }
#ifdef ASSERT
  void         set_phase_optimize_finished() { _phase_optimize_finished = true; }
  bool         phase_optimize_finished() const { return _phase_optimize_finished; }
  uint         count_live_nodes_by_graph_walk();
  void         print_missing_nodes();
#endif

  // Record modified nodes to check that they are put on IGVN worklist
  void         record_modified_node(Node* n) NOT_DEBUG_RETURN;
  void         remove_modified_node(Node* n) NOT_DEBUG_RETURN;
  DEBUG_ONLY( Unique_Node_List*   modified_nodes() const { return _modified_nodes; } )

  MachConstantBaseNode*     mach_constant_base_node();
  bool                  has_mach_constant_base_node() const { return _mach_constant_base_node != nullptr; }
  // Generated by adlc, true if CallNode requires MachConstantBase.
  bool                      needs_deep_clone_jvms();

  // Handy undefined Node
  Node*             top() const                 { return _top; }

  // these are used by guys who need to know about creation and transformation of top:
  Node*             cached_top_node()           { return _top; }
  void          set_cached_top_node(Node* tn);

  GrowableArray<Node_Notes*>* node_note_array() const { return _node_note_array; }
  void set_node_note_array(GrowableArray<Node_Notes*>* arr) { _node_note_array = arr; }
  Node_Notes* default_node_notes() const        { return _default_node_notes; }
  void    set_default_node_notes(Node_Notes* n) { _default_node_notes = n; }

  Node_Notes*       node_notes_at(int idx);

  inline bool   set_node_notes_at(int idx, Node_Notes* value);
  // Copy notes from source to dest, if they exist.
  // Overwrite dest only if source provides something.
  // Return true if information was moved.
  bool copy_node_notes_to(Node* dest, Node* source);

  // Workhorse function to sort out the blocked Node_Notes array:
  Node_Notes* locate_node_notes(GrowableArray<Node_Notes*>* arr,
                                int idx, bool can_grow = false);

  void grow_node_notes(GrowableArray<Node_Notes*>* arr, int grow_by);

  // Type management
  Arena*            type_arena()                { return _type_arena; }
  Dict*             type_dict()                 { return _type_dict; }
  size_t            type_last_size()            { return _type_last_size; }
  int               num_alias_types()           { return _num_alias_types; }

  void          init_type_arena()                       { _type_arena = &_Compile_types; }
  void          set_type_arena(Arena* a)                { _type_arena = a; }
  void          set_type_dict(Dict* d)                  { _type_dict = d; }
  void          set_type_last_size(size_t sz)           { _type_last_size = sz; }

  const TypeFunc* last_tf(ciMethod* m) {
    return (m == _last_tf_m) ? _last_tf : nullptr;
  }
  void set_last_tf(ciMethod* m, const TypeFunc* tf) {
    assert(m != nullptr || tf == nullptr, "");
    _last_tf_m = m;
    _last_tf = tf;
  }

  AliasType*        alias_type(int                idx)  { assert(idx < num_alias_types(), "oob"); return _alias_types[idx]; }
  AliasType*        alias_type(const TypePtr* adr_type, ciField* field = nullptr) { return find_alias_type(adr_type, false, field); }
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
  CallGenerator*    call_generator(ciMethod* call_method, int vtable_index, bool call_does_dispatch,
                                   JVMState* jvms, bool allow_inline, float profile_factor, ciKlass* speculative_receiver_type = nullptr,
                                   bool allow_intrinsics = true);
  bool should_delay_inlining(ciMethod* call_method, JVMState* jvms) {
    return C->directive()->should_delay_inline(call_method) ||
           should_delay_string_inlining(call_method, jvms) ||
           should_delay_boxing_inlining(call_method, jvms) ||
           should_delay_vector_inlining(call_method, jvms);
  }
  bool should_delay_string_inlining(ciMethod* call_method, JVMState* jvms);
  bool should_delay_boxing_inlining(ciMethod* call_method, JVMState* jvms);
  bool should_delay_vector_inlining(ciMethod* call_method, JVMState* jvms);
  bool should_delay_vector_reboxing_inlining(ciMethod* call_method, JVMState* jvms);

  // Helper functions to identify inlining potential at call-site
  ciMethod* optimize_virtual_call(ciMethod* caller, ciInstanceKlass* klass,
                                  ciKlass* holder, ciMethod* callee,
                                  const TypeOopPtr* receiver_type, bool is_virtual,
                                  bool &call_does_dispatch, int &vtable_index,
                                  bool check_access = true);
  ciMethod* optimize_inlining(ciMethod* caller, ciInstanceKlass* klass, ciKlass* holder,
                              ciMethod* callee, const TypeOopPtr* receiver_type,
                              bool check_access = true);

  // Report if there were too many traps at a current method and bci.
  // Report if a trap was recorded, and/or PerMethodTrapLimit was exceeded.
  // If there is no MDO at all, report no trap unless told to assume it.
  bool too_many_traps(ciMethod* method, int bci, Deoptimization::DeoptReason reason);
  // This version, unspecific to a particular bci, asks if
  // PerMethodTrapLimit was exceeded for all inlined methods seen so far.
  bool too_many_traps(Deoptimization::DeoptReason reason,
                      // Privately used parameter for logging:
                      ciMethodData* logmd = nullptr);
  // Report if there were too many recompiles at a method and bci.
  bool too_many_recompiles(ciMethod* method, int bci, Deoptimization::DeoptReason reason);
  // Report if there were too many traps or recompiles at a method and bci.
  bool too_many_traps_or_recompiles(ciMethod* method, int bci, Deoptimization::DeoptReason reason) {
    return too_many_traps(method, bci, reason) ||
           too_many_recompiles(method, bci, reason);
  }
  // Return a bitset with the reasons where deoptimization is allowed,
  // i.e., where there were not too many uncommon traps.
  int _allowed_reasons;
  int      allowed_deopt_reasons() { return _allowed_reasons; }
  void set_allowed_deopt_reasons();

  // Parsing, optimization
  PhaseGVN*         initial_gvn()               { return _initial_gvn; }
  Unique_Node_List* igvn_worklist() {
    assert(_igvn_worklist != nullptr, "must be created in Compile::Compile");
    return _igvn_worklist;
  }
  Type_Array* types() {
    assert(_types != nullptr, "must be created in Compile::Compile");
    return _types;
  }
  NodeHash* node_hash() {
    assert(_node_hash != nullptr, "must be created in Compile::Compile");
    return _node_hash;
  }
  inline void       record_for_igvn(Node* n);   // Body is after class Unique_Node_List in node.hpp.
  inline void       remove_for_igvn(Node* n);   // Body is after class Unique_Node_List in node.hpp.
  void          set_initial_gvn(PhaseGVN *gvn)           { _initial_gvn = gvn; }

  // Replace n by nn using initial_gvn, calling hash_delete and
  // record_for_igvn as needed.
  void gvn_replace_by(Node* n, Node* nn);


  void              identify_useful_nodes(Unique_Node_List &useful);
  void              update_dead_node_list(Unique_Node_List &useful);
  void disconnect_useless_nodes(Unique_Node_List& useful, Unique_Node_List& worklist, const Unique_Node_List* root_and_safepoints = nullptr);

  void              remove_useless_node(Node* dead);

  // Record this CallGenerator for inlining at the end of parsing.
  void              add_late_inline(CallGenerator* cg)        {
    _late_inlines.insert_before(_late_inlines_pos, cg);
    _late_inlines_pos++;
  }

  void              prepend_late_inline(CallGenerator* cg)    {
    _late_inlines.insert_before(0, cg);
  }

  void              add_string_late_inline(CallGenerator* cg) {
    _string_late_inlines.push(cg);
  }

  void              add_boxing_late_inline(CallGenerator* cg) {
    _boxing_late_inlines.push(cg);
  }

  void              add_vector_reboxing_late_inline(CallGenerator* cg) {
    _vector_reboxing_late_inlines.push(cg);
  }

  template<typename N, ENABLE_IF(std::is_base_of<Node, N>::value)>
  void remove_useless_nodes(GrowableArray<N*>& node_list, Unique_Node_List& useful);

  void remove_useless_late_inlines(GrowableArray<CallGenerator*>* inlines, Unique_Node_List &useful);
  void remove_useless_late_inlines(GrowableArray<CallGenerator*>* inlines, Node* dead);

  void remove_useless_coarsened_locks(Unique_Node_List& useful);

  void dump_print_inlining();

  bool over_inlining_cutoff() const {
    if (!inlining_incrementally()) {
      return unique() > (uint)NodeCountInliningCutoff;
    } else {
      // Give some room for incremental inlining algorithm to "breathe"
      // and avoid thrashing when live node count is close to the limit.
      // Keep in mind that live_nodes() isn't accurate during inlining until
      // dead node elimination step happens (see Compile::inline_incrementally).
      return live_nodes() > (uint)LiveNodeCountInliningCutoff * 11 / 10;
    }
  }

  void mark_has_mh_late_inlines() { _has_mh_late_inlines = true; }
  bool has_mh_late_inlines() const { return _has_mh_late_inlines; }

  bool inline_incrementally_one();
  void inline_incrementally_cleanup(PhaseIterGVN& igvn);
  void inline_incrementally(PhaseIterGVN& igvn);
  bool should_stress_inlining() { return StressIncrementalInlining && (random() % 2) == 0; }
  bool should_delay_inlining() { return AlwaysIncrementalInline || should_stress_inlining(); }
  void inline_string_calls(bool parse_time);
  void inline_boxing_calls(PhaseIterGVN& igvn);
  bool optimize_loops(PhaseIterGVN& igvn, LoopOptsMode mode);
  void remove_root_to_sfpts_edges(PhaseIterGVN& igvn);

  void inline_vector_reboxing_calls();
  bool has_vbox_nodes();

  void process_late_inline_calls_no_inline(PhaseIterGVN& igvn);

  // Matching, CFG layout, allocation, code generation
  PhaseCFG*         cfg()                       { return _cfg; }
  bool              has_java_calls() const      { return _java_calls > 0; }
  int               java_calls() const          { return _java_calls; }
  int               inner_loops() const         { return _inner_loops; }
  Matcher*          matcher()                   { return _matcher; }
  PhaseRegAlloc*    regalloc()                  { return _regalloc; }
  RegMask&          FIRST_STACK_mask()          { return _FIRST_STACK_mask; }
  ResourceArea*     regmask_arena()             { return &_regmask_arena; }
  Arena*            indexSet_arena()            { return _indexSet_arena; }
  void*             indexSet_free_block_list()  { return _indexSet_free_block_list; }
  DebugInformationRecorder* debug_info()        { return env()->debug_info(); }

  void  update_interpreter_frame_size(int size) {
    if (_interpreter_frame_size < size) {
      _interpreter_frame_size = size;
    }
  }

  void          set_matcher(Matcher* m)                 { _matcher = m; }
//void          set_regalloc(PhaseRegAlloc* ra)           { _regalloc = ra; }
  void          set_indexSet_arena(Arena* a)            { _indexSet_arena = a; }
  void          set_indexSet_free_block_list(void* p)   { _indexSet_free_block_list = p; }

  void  set_java_calls(int z) { _java_calls  = z; }
  void set_inner_loops(int z) { _inner_loops = z; }

  Dependencies* dependencies() { return env()->dependencies(); }

  // Major entry point.  Given a Scope, compile the associated method.
  // For normal compilations, entry_bci is InvocationEntryBci.  For on stack
  // replacement, entry_bci indicates the bytecode for which to compile a
  // continuation.
  Compile(ciEnv* ci_env, ciMethod* target,
          int entry_bci, Options options, DirectiveSet* directive);

  // Second major entry point.  From the TypeFunc signature, generate code
  // to pass arguments from the Java calling convention to the C calling
  // convention.
  Compile(ciEnv* ci_env, const TypeFunc *(*gen)(),
          address stub_function, const char *stub_name,
          StubId stub_id, int is_fancy_jump, bool pass_tls,
          bool return_pc, DirectiveSet* directive);

  ~Compile();

  // Are we compiling a method?
  bool has_method() { return method() != nullptr; }

  // Maybe print some information about this compile.
  void print_compile_messages();

  // Final graph reshaping, a post-pass after the regular optimizer is done.
  bool final_graph_reshaping();

  // returns true if adr is completely contained in the given alias category
  bool must_alias(const TypePtr* adr, int alias_idx);

  // returns true if adr overlaps with the given alias category
  bool can_alias(const TypePtr* adr, int alias_idx);

  // Stack slots that may be unused by the calling convention but must
  // otherwise be preserved.  On Intel this includes the return address.
  // On PowerPC it includes the 4 words holding the old TOC & LR glue.
  uint in_preserve_stack_slots() {
    return SharedRuntime::in_preserve_stack_slots();
  }

  // "Top of Stack" slots that may be unused by the calling convention but must
  // otherwise be preserved.
  // On Intel these are not necessary and the value can be zero.
  static uint out_preserve_stack_slots() {
    return SharedRuntime::out_preserve_stack_slots();
  }

  // Number of outgoing stack slots killed above the out_preserve_stack_slots
  // for calls to C.  Supports the var-args backing area for register parms.
  uint varargs_C_out_slots_killed() const;

  // Number of Stack Slots consumed by a synchronization entry
  int sync_stack_slots() const;

  // Compute the name of old_SP.  See <arch>.ad for frame layout.
  OptoReg::Name compute_old_SP();

 private:
  // Phase control:
  void Init(bool aliasing);                      // Prepare for a single compilation
  void Optimize();                               // Given a graph, optimize it
  void Code_Gen();                               // Generate code from a graph

  // Management of the AliasType table.
  void grow_alias_types();
  AliasCacheEntry* probe_alias_cache(const TypePtr* adr_type);
  const TypePtr *flatten_alias_type(const TypePtr* adr_type) const;
  AliasType* find_alias_type(const TypePtr* adr_type, bool no_create, ciField* field);

  void verify_top(Node*) const PRODUCT_RETURN;

  // Intrinsic setup.
  CallGenerator* make_vm_intrinsic(ciMethod* m, bool is_virtual);          // constructor
  int            intrinsic_insertion_index(ciMethod* m, bool is_virtual, bool& found);  // helper
  CallGenerator* find_intrinsic(ciMethod* m, bool is_virtual);             // query fn
  void           register_intrinsic(CallGenerator* cg);                    // update fn

#ifndef PRODUCT
  static juint  _intrinsic_hist_count[];
  static jubyte _intrinsic_hist_flags[];
#endif
  // Function calls made by the public function final_graph_reshaping.
  // No need to be made public as they are not called elsewhere.
  void final_graph_reshaping_impl(Node *n, Final_Reshape_Counts& frc, Unique_Node_List& dead_nodes);
  void final_graph_reshaping_main_switch(Node* n, Final_Reshape_Counts& frc, uint nop, Unique_Node_List& dead_nodes);
  void final_graph_reshaping_walk(Node_Stack& nstack, Node* root, Final_Reshape_Counts& frc, Unique_Node_List& dead_nodes);
  void handle_div_mod_op(Node* n, BasicType bt, bool is_unsigned);

  // Logic cone optimization.
  void optimize_logic_cones(PhaseIterGVN &igvn);
  void collect_logic_cone_roots(Unique_Node_List& list);
  void process_logic_cone_root(PhaseIterGVN &igvn, Node* n, VectorSet& visited);
  bool compute_logic_cone(Node* n, Unique_Node_List& partition, Unique_Node_List& inputs);
  uint compute_truth_table(Unique_Node_List& partition, Unique_Node_List& inputs);
  uint eval_macro_logic_op(uint func, uint op1, uint op2, uint op3);
  Node* xform_to_MacroLogicV(PhaseIterGVN &igvn, const TypeVect* vt, Unique_Node_List& partitions, Unique_Node_List& inputs);
  void check_no_dead_use() const NOT_DEBUG_RETURN;

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
  // Walk the node list, verifying that there is a one-to-one correspondence
  // between Use-Def edges and Def-Use edges. The option no_dead_code enables
  // stronger checks that the graph is strongly connected from starting points
  // in both directions.
  // root_and_safepoints is used to give the starting points for the traversal.
  // If not supplied, only root is used. When this check is called after CCP,
  // we need to start traversal from Root and safepoints, just like CCP does its
  // own traversal (see PhaseCCP::transform for reasons).
  //
  // To call this function, there are 2 ways to go:
  // - give root_and_safepoints to start traversal everywhere needed (like after CCP)
  // - if the whole graph is assumed to be reachable from Root's input,
  //   root_and_safepoints is not needed (like in PhaseRemoveUseless).
  //
  // Failure to specify root_and_safepoints in case the graph is not fully
  // reachable from Root's input make this check unsound (can miss inconsistencies)
  // and even incomplete (can make up non-existing problems) if no_dead_code is
  // true.
  void verify_graph_edges(bool no_dead_code = false, const Unique_Node_List* root_and_safepoints = nullptr) const PRODUCT_RETURN;

  // Verify bi-directional correspondence of edges
  void verify_bidirectional_edges(Unique_Node_List& visited, const Unique_Node_List* root_and_safepoints = nullptr) const;

  // End-of-run dumps.
  static void print_statistics() PRODUCT_RETURN;

  // Verify ADLC assumptions during startup
  static void adlc_verification() PRODUCT_RETURN;

  // Definitions of pd methods
  static void pd_compiler2_init();

  // Static parse-time type checking logic for gen_subtype_check:
  enum SubTypeCheckResult { SSC_always_false, SSC_always_true, SSC_easy_test, SSC_full_test };
  SubTypeCheckResult static_subtype_check(const TypeKlassPtr* superk, const TypeKlassPtr* subk, bool skip = StressReflectiveCode);

  static Node* conv_I2X_index(PhaseGVN* phase, Node* offset, const TypeInt* sizetype,
                              // Optional control dependency (for example, on range check)
                              Node* ctrl = nullptr);

  // Convert integer value to a narrowed long type dependent on ctrl (for example, a range check)
  static Node* constrained_convI2L(PhaseGVN* phase, Node* value, const TypeInt* itype, Node* ctrl, bool carry_dependency = false);

  // Auxiliary methods for randomized fuzzing/stressing
  int random();
  bool randomized_select(int count);

  // seed random number generation and log the seed for repeatability.
  void initialize_stress_seed(const DirectiveSet* directive);

  // supporting clone_map
  CloneMap&     clone_map();
  void          set_clone_map(Dict* d);

  bool needs_clinit_barrier(ciField* ik,         ciMethod* accessing_method);
  bool needs_clinit_barrier(ciMethod* ik,        ciMethod* accessing_method);
  bool needs_clinit_barrier(ciInstanceKlass* ik, ciMethod* accessing_method);

#ifdef ASSERT
  VerifyMeetResult* _type_verify;
  void set_exception_backedge() { _exception_backedge = true; }
  bool has_exception_backedge() const { return _exception_backedge; }
#endif

  static bool push_thru_add(PhaseGVN* phase, Node* z, const TypeInteger* tz, const TypeInteger*& rx, const TypeInteger*& ry,
                            BasicType out_bt, BasicType in_bt);

  static Node* narrow_value(BasicType bt, Node* value, const Type* type, PhaseGVN* phase, bool transform_res);

#ifndef PRODUCT
private:
  // getting rid of the template makes things easier
  Node* make_debug_print_call(const char* str, address call_addr, PhaseGVN* gvn,
                              Node* parm0 = nullptr, Node* parm1 = nullptr,
                              Node* parm2 = nullptr, Node* parm3 = nullptr,
                              Node* parm4 = nullptr, Node* parm5 = nullptr,
                              Node* parm6 = nullptr) const;

public:
  // Creates a CallLeafNode for a runtime call that prints a static string and the values of the
  // nodes passed as arguments.
  // This function also takes care of doing the necessary wiring, including finding a suitable control
  // based on the nodes that need to be printed. Note that passing nodes that have incompatible controls
  // is undefined behavior.
  template <typename... TT, typename... NN>
  Node* make_debug_print(const char* str, PhaseGVN* gvn, NN... in) {
    address call_addr = CAST_FROM_FN_PTR(address, SharedRuntime::debug_print<TT...>);
    return make_debug_print_call(str, call_addr, gvn, in...);
  }
#endif
};

#endif // SHARE_OPTO_COMPILE_HPP
