/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_COMPILE_HPP
#define SHARE_VM_OPTO_COMPILE_HPP

#include "asm/codeBuffer.hpp"
#include "ci/compilerInterface.hpp"
#include "code/debugInfoRec.hpp"
#include "code/exceptionHandlerTable.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/compileBroker.hpp"
#include "libadt/dict.hpp"
#include "libadt/vectset.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/resourceArea.hpp"
#include "oops/methodData.hpp"
#include "opto/idealGraphPrinter.hpp"
#include "opto/phasetype.hpp"
#include "opto/phase.hpp"
#include "opto/regmask.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/ticks.hpp"

class AddPNode;
class Block;
class Bundle;
class C2Compiler;
class CallGenerator;
class CloneMap;
class ConnectionGraph;
class InlineTree;
class Int_Array;
class LoadBarrierNode;
class Matcher;
class MachConstantNode;
class MachConstantBaseNode;
class MachNode;
class MachOper;
class MachSafePointNode;
class Node;
class Node_Array;
class Node_Notes;
class NodeCloneInfo;
class OptoReg;
class PhaseCFG;
class PhaseGVN;
class PhaseIterGVN;
class PhaseRegAlloc;
class PhaseCCP;
class PhaseCCP_DCE;
class RootNode;
class relocInfo;
class Scope;
class StartNode;
class SafePointNode;
class JVMState;
class Type;
class TypeData;
class TypeInt;
class TypePtr;
class TypeOopPtr;
class TypeFunc;
class Unique_Node_List;
class nmethod;
class WarmCallInfo;
class Node_Stack;
struct Final_Reshape_Counts;

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

  void dump() const;
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
  void insert(node_idx_t key, uint64_t val)      { assert(_dict->operator[](_2p(key)) == NULL, "key existed"); _dict->Insert(_2p(key), (void*)val); }
  void insert(node_idx_t key, NodeCloneInfo& ci) { insert(key, ci.get()); }
  void remove(node_idx_t key)                    { _dict->Delete(_2p(key)); }
  uint64_t value(node_idx_t key)  const          { return (uint64_t)_dict->operator[](_2p(key)); }
  node_idx_t idx(node_idx_t key)  const          { return NodeCloneInfo(value(key)).idx(); }
  int gen(node_idx_t key)         const          { return NodeCloneInfo(value(key)).gen(); }
  int gen(const void* k)          const          { return gen(_2_node_idx_t(k)); }
  int max_gen()                   const;
  void clone(Node* old, Node* nnn, int gen);
  void verify_insert_and_clone(Node* old, Node* nnn, int gen);
  void dump(node_idx_t key)       const;

  int  clone_idx() const                         { return _clone_idx; }
  void set_clone_idx(int x)                      { _clone_idx = x; }
  bool is_debug()                 const          { return _debug; }
  void set_debug(bool debug)                     { _debug = debug; }
  static const char* debug_option_name;

  bool same_idx(node_idx_t k1, node_idx_t k2)  const { return idx(k1) == idx(k2); }
  bool same_gen(node_idx_t k1, node_idx_t k2)  const { return gen(k1) == gen(k2); }
};

//------------------------------Compile----------------------------------------
// This class defines a top-level Compiler invocation.

class Compile : public Phase {
  friend class VMStructs;

 public:
  // Fixed alias indexes.  (See also MergeMemNode.)
  enum {
    AliasIdxTop = 1,  // pseudo-index, aliases to nothing (used as sentinel value)
    AliasIdxBot = 2,  // pseudo-index, aliases to everything
    AliasIdxRaw = 3   // hard-wired index for TypeRawPtr::BOTTOM
  };

  // Variant of TraceTime(NULL, &_t_accumulator, CITime);
  // Integrated with logging.  If logging is turned on, and CITimeVerbose is true,
  // then brackets are put into the log, with time stamps and node counts.
  // (The time collection itself is always conditionalized on CITime.)
  class TracePhase : public TraceTime {
   private:
    Compile*    C;
    CompileLog* _log;
    const char* _phase_name;
    bool _dolog;
   public:
    TracePhase(const char* name, elapsedTimer* accumulator);
    ~TracePhase();
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
      assert(_element == NULL, "");
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

  // Constant entry of the constant table.
  class Constant {
  private:
    BasicType _type;
    union {
      jvalue    _value;
      Metadata* _metadata;
    } _v;
    int       _offset;         // offset of this constant (in bytes) relative to the constant table base.
    float     _freq;
    bool      _can_be_reused;  // true (default) if the value can be shared with other users.

  public:
    Constant() : _type(T_ILLEGAL), _offset(-1), _freq(0.0f), _can_be_reused(true) { _v._value.l = 0; }
    Constant(BasicType type, jvalue value, float freq = 0.0f, bool can_be_reused = true) :
      _type(type),
      _offset(-1),
      _freq(freq),
      _can_be_reused(can_be_reused)
    {
      assert(type != T_METADATA, "wrong constructor");
      _v._value = value;
    }
    Constant(Metadata* metadata, bool can_be_reused = true) :
      _type(T_METADATA),
      _offset(-1),
      _freq(0.0f),
      _can_be_reused(can_be_reused)
    {
      _v._metadata = metadata;
    }

    bool operator==(const Constant& other);

    BasicType type()      const    { return _type; }

    jint    get_jint()    const    { return _v._value.i; }
    jlong   get_jlong()   const    { return _v._value.j; }
    jfloat  get_jfloat()  const    { return _v._value.f; }
    jdouble get_jdouble() const    { return _v._value.d; }
    jobject get_jobject() const    { return _v._value.l; }

    Metadata* get_metadata() const { return _v._metadata; }

    int         offset()  const    { return _offset; }
    void    set_offset(int offset) {        _offset = offset; }

    float       freq()    const    { return _freq;         }
    void    inc_freq(float freq)   {        _freq += freq; }

    bool    can_be_reused() const  { return _can_be_reused; }
  };

  // Constant table.
  class ConstantTable {
  private:
    GrowableArray<Constant> _constants;          // Constants of this table.
    int                     _size;               // Size in bytes the emitted constant table takes (including padding).
    int                     _table_base_offset;  // Offset of the table base that gets added to the constant offsets.
    int                     _nof_jump_tables;    // Number of jump-tables in this constant table.

    static int qsort_comparator(Constant* a, Constant* b);

    // We use negative frequencies to keep the order of the
    // jump-tables in which they were added.  Otherwise we get into
    // trouble with relocation.
    float next_jump_table_freq() { return -1.0f * (++_nof_jump_tables); }

  public:
    ConstantTable() :
      _size(-1),
      _table_base_offset(-1),  // We can use -1 here since the constant table is always bigger than 2 bytes (-(size / 2), see MachConstantBaseNode::emit).
      _nof_jump_tables(0)
    {}

    int size() const { assert(_size != -1, "not calculated yet"); return _size; }

    int calculate_table_base_offset() const;  // AD specific
    void set_table_base_offset(int x)  { assert(_table_base_offset == -1 || x == _table_base_offset, "can't change"); _table_base_offset = x; }
    int      table_base_offset() const { assert(_table_base_offset != -1, "not set yet");                      return _table_base_offset; }

    void emit(CodeBuffer& cb);

    // Returns the offset of the last entry (the top) of the constant table.
    int  top_offset() const { assert(_constants.top().offset() != -1, "not bound yet"); return _constants.top().offset(); }

    void calculate_offsets_and_size();
    int  find_offset(Constant& con) const;

    void     add(Constant& con);
    Constant add(MachConstantNode* n, BasicType type, jvalue value);
    Constant add(Metadata* metadata);
    Constant add(MachConstantNode* n, MachOper* oper);
    Constant add(MachConstantNode* n, jint i) {
      jvalue value; value.i = i;
      return add(n, T_INT, value);
    }
    Constant add(MachConstantNode* n, jlong j) {
      jvalue value; value.j = j;
      return add(n, T_LONG, value);
    }
    Constant add(MachConstantNode* n, jfloat f) {
      jvalue value; value.f = f;
      return add(n, T_FLOAT, value);
    }
    Constant add(MachConstantNode* n, jdouble d) {
      jvalue value; value.d = d;
      return add(n, T_DOUBLE, value);
    }

    // Jump-table
    Constant  add_jump_table(MachConstantNode* n);
    void     fill_jump_table(CodeBuffer& cb, MachConstantNode* n, GrowableArray<Label*> labels) const;
  };

 private:
  // Fixed parameters to this compilation.
  const int             _compile_id;
  const bool            _save_argument_registers; // save/restore arg regs for trampolines
  const bool            _subsume_loads;         // Load can be matched as part of a larger op.
  const bool            _do_escape_analysis;    // Do escape analysis.
  const bool            _eliminate_boxing;      // Do boxing elimination.
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
  uintx                 _max_node_limit;        // Max unique node count during a single compilation.
  // For deopt
  int                   _orig_pc_slot;
  int                   _orig_pc_slot_offset_in_bytes;

  int                   _major_progress;        // Count of something big happening
  bool                  _inlining_progress;     // progress doing incremental inlining?
  bool                  _inlining_incrementally;// Are we doing incremental inlining (post parse)
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
  bool                  _do_count_invocations;  // True if we generate code to count invocations
  bool                  _do_method_data_update; // True if we generate code to update MethodData*s
  bool                  _do_vector_loop;        // True if allowed to execute loop in parallel iterations
  bool                  _use_cmove;             // True if CMove should be used without profitability analysis
  bool                  _age_code;              // True if we need to profile code age (decrement the aging counter)
  int                   _AliasLevel;            // Locally-adjusted version of AliasLevel flag.
  bool                  _print_assembly;        // True if we should dump assembly code for this compilation
  bool                  _print_inlining;        // True if we should print inlining for this compilation
  bool                  _print_intrinsics;      // True if we should print intrinsics for this compilation
#ifndef PRODUCT
  bool                  _trace_opto_output;
  bool                  _parsed_irreducible_loop; // True if ciTypeFlow detected irreducible loops during parsing
#endif
  bool                  _has_irreducible_loop;  // Found irreducible loops
  // JSR 292
  bool                  _has_method_handle_invokes; // True if this method has MethodHandle invokes.
  RTMState              _rtm_state;             // State of Restricted Transactional Memory usage

  // Compilation environment.
  Arena                 _comp_arena;            // Arena with lifetime equivalent to Compile
  void*                 _barrier_set_state;     // Potential GC barrier state for Compile
  ciEnv*                _env;                   // CI interface
  DirectiveSet*         _directive;             // Compiler directive
  CompileLog*           _log;                   // from CompilerThread
  const char*           _failure_reason;        // for record_failure/failing pattern
  GrowableArray<CallGenerator*>* _intrinsics;   // List of intrinsics.
  GrowableArray<Node*>* _macro_nodes;           // List of nodes which need to be expanded before matching.
  GrowableArray<Node*>* _predicate_opaqs;       // List of Opaque1 nodes for the loop predicates.
  GrowableArray<Node*>* _expensive_nodes;       // List of nodes that are expensive to compute and that we'd better not let the GVN freely common
  GrowableArray<Node*>* _range_check_casts;     // List of CastII nodes with a range check dependency
  GrowableArray<Node*>* _opaque4_nodes;         // List of Opaque4 nodes that have a default value
  ConnectionGraph*      _congraph;
#ifndef PRODUCT
  IdealGraphPrinter*    _printer;
#endif


  // Node management
  uint                  _unique;                // Counter for unique Node indices
  VectorSet             _dead_node_list;        // Set of dead nodes
  uint                  _dead_node_count;       // Number of dead nodes; VectorSet::Size() is O(N).
                                                // So use this to keep count and make the call O(1).
  DEBUG_ONLY( Unique_Node_List* _modified_nodes; )  // List of nodes which inputs were modified

  debug_only(static int _debug_idx;)            // Monotonic counter (not reset), use -XX:BreakAtNode=<idx>
  Arena                 _node_arena;            // Arena for new-space Nodes
  Arena                 _old_arena;             // Arena for old-space Nodes, lifetime during xform
  RootNode*             _root;                  // Unique root of compilation, or NULL after bail-out.
  Node*                 _top;                   // Unique top node.  (Reset by various phases.)

  Node*                 _immutable_memory;      // Initial memory state

  Node*                 _recent_alloc_obj;
  Node*                 _recent_alloc_ctl;

  // Constant table
  ConstantTable         _constant_table;        // The constant table for this compile.
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

  GrowableArray<CallGenerator*> _late_inlines;        // List of CallGenerators to be revisited after
                                                      // main parsing has finished.
  GrowableArray<CallGenerator*> _string_late_inlines; // same but for string operations

  GrowableArray<CallGenerator*> _boxing_late_inlines; // same but for boxing operations

  int                           _late_inlines_pos;    // Where in the queue should the next late inlining candidate go (emulate depth first inlining)
  uint                          _number_of_mh_late_inlines; // number of method handle late inlining still pending


  // Inlining may not happen in parse order which would make
  // PrintInlining output confusing. Keep track of PrintInlining
  // pieces in order.
  class PrintInliningBuffer : public ResourceObj {
   private:
    CallGenerator* _cg;
    stringStream* _ss;

   public:
    PrintInliningBuffer()
      : _cg(NULL) { _ss = new stringStream(); }

    stringStream* ss() const { return _ss; }
    CallGenerator* cg() const { return _cg; }
    void set_cg(CallGenerator* cg) { _cg = cg; }
  };

  stringStream* _print_inlining_stream;
  GrowableArray<PrintInliningBuffer>* _print_inlining_list;
  int _print_inlining_idx;
  char* _print_inlining_output;

  // Only keep nodes in the expensive node list that need to be optimized
  void cleanup_expensive_nodes(PhaseIterGVN &igvn);
  // Use for sorting expensive nodes to bring similar nodes together
  static int cmp_expensive_nodes(Node** n1, Node** n2);
  // Expensive nodes list already sorted?
  bool expensive_nodes_sorted() const;
  // Remove the speculative part of types and clean up the graph
  void remove_speculative_types(PhaseIterGVN &igvn);

  void* _replay_inline_data; // Pointer to data loaded from file

  void print_inlining_init();
  void print_inlining_reinit();
  void print_inlining_commit();
  void print_inlining_push();
  PrintInliningBuffer& print_inlining_current();

  void log_late_inline_failure(CallGenerator* cg, const char* msg);

 public:

  void* barrier_set_state() const { return _barrier_set_state; }

  outputStream* print_inlining_stream() const {
    assert(print_inlining() || print_intrinsics(), "PrintInlining off?");
    return _print_inlining_stream;
  }

  void print_inlining_update(CallGenerator* cg);
  void print_inlining_update_delayed(CallGenerator* cg);
  void print_inlining_move_to(CallGenerator* cg);
  void print_inlining_assert_ready();
  void print_inlining_reset();

  void print_inlining(ciMethod* method, int inline_level, int bci, const char* msg = NULL) {
    stringStream ss;
    CompileTask::print_inlining_inner(&ss, method, inline_level, bci, msg);
    print_inlining_stream()->print("%s", ss.as_string());
  }

#ifndef PRODUCT
  IdealGraphPrinter* printer() { return _printer; }
#endif

  void log_late_inline(CallGenerator* cg);
  void log_inline_id(CallGenerator* cg);
  void log_inline_failure(const char* msg);

  void* replay_inline_data() const { return _replay_inline_data; }

  // Dump inlining replay data to the stream.
  void dump_inline_data(outputStream* out);

 private:
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
  int                   _interpreter_frame_size;

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
  int                   _scratch_const_size;    // For temporary code buffers.
  bool                  _in_scratch_emit_size;  // true when in scratch_emit_size.

  void reshape_address(AddPNode* n);

 public:
  // Accessors

  // The Compile instance currently active in this (compiler) thread.
  static Compile* current() {
    return (Compile*) ciEnv::current()->compiler_data();
  }

  // ID for this compilation.  Useful for setting breakpoints in the debugger.
  int               compile_id() const          { return _compile_id; }
  DirectiveSet*     directive() const           { return _directive; }

  // Does this compilation allow instructions to subsume loads?  User
  // instructions that subsume a load may result in an unschedulable
  // instruction sequence.
  bool              subsume_loads() const       { return _subsume_loads; }
  /** Do escape analysis. */
  bool              do_escape_analysis() const  { return _do_escape_analysis; }
  /** Do boxing elimination. */
  bool              eliminate_boxing() const    { return _eliminate_boxing; }
  /** Do aggressive boxing elimination. */
  bool              aggressive_unboxing() const { return _eliminate_boxing && AggressiveUnboxing; }
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
  void          set_inlining_progress(bool z)   { _inlining_progress = z; }
  int               inlining_progress() const   { return _inlining_progress; }
  void          set_inlining_incrementally(bool z) { _inlining_incrementally = z; }
  int               inlining_incrementally() const { return _inlining_incrementally; }
  void          set_major_progress()            { _major_progress++; }
  void        clear_major_progress()            { _major_progress = 0; }
  int               num_loop_opts() const       { return _num_loop_opts; }
  void          set_num_loop_opts(int n)        { _num_loop_opts = n; }
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
  bool              do_count_invocations() const{ return _do_count_invocations; }
  void          set_do_count_invocations(bool z){ _do_count_invocations = z; }
  bool              do_method_data_update() const { return _do_method_data_update; }
  void          set_do_method_data_update(bool z) { _do_method_data_update = z; }
  bool              do_vector_loop() const      { return _do_vector_loop; }
  void          set_do_vector_loop(bool z)      { _do_vector_loop = z; }
  bool              use_cmove() const           { return _use_cmove; }
  void          set_use_cmove(bool z)           { _use_cmove = z; }
  bool              age_code() const             { return _age_code; }
  void          set_age_code(bool z)             { _age_code = z; }
  int               AliasLevel() const           { return _AliasLevel; }
  bool              print_assembly() const       { return _print_assembly; }
  void          set_print_assembly(bool z)       { _print_assembly = z; }
  bool              print_inlining() const       { return _print_inlining; }
  void          set_print_inlining(bool z)       { _print_inlining = z; }
  bool              print_intrinsics() const     { return _print_intrinsics; }
  void          set_print_intrinsics(bool z)     { _print_intrinsics = z; }
  RTMState          rtm_state()  const           { return _rtm_state; }
  void          set_rtm_state(RTMState s)        { _rtm_state = s; }
  bool              use_rtm() const              { return (_rtm_state & NoRTM) == 0; }
  bool          profile_rtm() const              { return _rtm_state == ProfileRTM; }
  uint              max_node_limit() const       { return (uint)_max_node_limit; }
  void          set_max_node_limit(uint n)       { _max_node_limit = n; }

  // check the CompilerOracle for special behaviours for this compile
  bool          method_has_option(const char * option) {
    return method() != NULL && method()->has_option(option);
  }

#ifndef PRODUCT
  bool          trace_opto_output() const       { return _trace_opto_output; }
  bool              parsed_irreducible_loop() const { return _parsed_irreducible_loop; }
  void          set_parsed_irreducible_loop(bool z) { _parsed_irreducible_loop = z; }
  int _in_dump_cnt;  // Required for dumping ir nodes.
#endif
  bool              has_irreducible_loop() const { return _has_irreducible_loop; }
  void          set_has_irreducible_loop(bool z) { _has_irreducible_loop = z; }

  // JSR 292
  bool              has_method_handle_invokes() const { return _has_method_handle_invokes;     }
  void          set_has_method_handle_invokes(bool z) {        _has_method_handle_invokes = z; }

  Ticks _latest_stage_start_counter;

  void begin_method() {
#ifndef PRODUCT
    if (_printer && _printer->should_print(1)) {
      _printer->begin_method();
    }
#endif
    C->_latest_stage_start_counter.stamp();
  }

  void print_method(CompilerPhaseType cpt, int level = 1) {
    EventCompilerPhase event;
    if (event.should_commit()) {
      event.set_starttime(C->_latest_stage_start_counter);
      event.set_phase((u1) cpt);
      event.set_compileId(C->_compile_id);
      event.set_phaseLevel(level);
      event.commit();
    }


#ifndef PRODUCT
    if (_printer && _printer->should_print(level)) {
      _printer->print_method(CompilerPhaseTypeHelper::to_string(cpt), level);
    }
#endif
    C->_latest_stage_start_counter.stamp();
  }

  void end_method(int level = 1) {
    EventCompilerPhase event;
    if (event.should_commit()) {
      event.set_starttime(C->_latest_stage_start_counter);
      event.set_phase((u1) PHASE_END);
      event.set_compileId(C->_compile_id);
      event.set_phaseLevel(level);
      event.commit();
    }
#ifndef PRODUCT
    if (_printer && _printer->should_print(level)) {
      _printer->end_method();
    }
#endif
  }

  int           macro_count()             const { return _macro_nodes->length(); }
  int           predicate_count()         const { return _predicate_opaqs->length();}
  int           expensive_count()         const { return _expensive_nodes->length(); }
  Node*         macro_node(int idx)       const { return _macro_nodes->at(idx); }
  Node*         predicate_opaque1_node(int idx) const { return _predicate_opaqs->at(idx);}
  Node*         expensive_node(int idx)   const { return _expensive_nodes->at(idx); }
  ConnectionGraph* congraph()                   { return _congraph;}
  void set_congraph(ConnectionGraph* congraph)  { _congraph = congraph;}
  void add_macro_node(Node * n) {
    //assert(n->is_macro(), "must be a macro node");
    assert(!_macro_nodes->contains(n), "duplicate entry in expand list");
    _macro_nodes->append(n);
  }
  void remove_macro_node(Node * n) {
    // this function may be called twice for a node so check
    // that the node is in the array before attempting to remove it
    if (_macro_nodes->contains(n))
      _macro_nodes->remove(n);
    // remove from _predicate_opaqs list also if it is there
    if (predicate_count() > 0 && _predicate_opaqs->contains(n)){
      _predicate_opaqs->remove(n);
    }
  }
  void add_expensive_node(Node * n);
  void remove_expensive_node(Node * n) {
    if (_expensive_nodes->contains(n)) {
      _expensive_nodes->remove(n);
    }
  }
  void add_predicate_opaq(Node * n) {
    assert(!_predicate_opaqs->contains(n), "duplicate entry in predicate opaque1");
    assert(_macro_nodes->contains(n), "should have already been in macro list");
    _predicate_opaqs->append(n);
  }

  // Range check dependent CastII nodes that can be removed after loop optimizations
  void add_range_check_cast(Node* n);
  void remove_range_check_cast(Node* n) {
    if (_range_check_casts->contains(n)) {
      _range_check_casts->remove(n);
    }
  }
  Node* range_check_cast_node(int idx) const { return _range_check_casts->at(idx);  }
  int   range_check_cast_count()       const { return _range_check_casts->length(); }
  // Remove all range check dependent CastIINodes.
  void  remove_range_check_casts(PhaseIterGVN &igvn);

  void add_opaque4_node(Node* n);
  void remove_opaque4_node(Node* n) {
    if (_opaque4_nodes->contains(n)) {
      _opaque4_nodes->remove(n);
    }
  }
  Node* opaque4_node(int idx) const { return _opaque4_nodes->at(idx);  }
  int   opaque4_count()       const { return _opaque4_nodes->length(); }
  void  remove_opaque4_nodes(PhaseIterGVN &igvn);

  // remove the opaque nodes that protect the predicates so that the unused checks and
  // uncommon traps will be eliminated from the graph.
  void cleanup_loop_predicates(PhaseIterGVN &igvn);
  bool is_predicate_opaq(Node * n) {
    return _predicate_opaqs->contains(n);
  }

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
  bool        failing() const        { return _env->failing() || _failure_reason != NULL; }
  const char* failure_reason() const { return (_env->failing()) ? _env->failure_reason() : _failure_reason; }

  bool failure_reason_is(const char* r) const {
    return (r == _failure_reason) || (r != NULL && _failure_reason != NULL && strcmp(r, _failure_reason) == 0);
  }

  void record_failure(const char* reason);
  void record_method_not_compilable(const char* reason) {
    // Bailouts cover "all_tiers" when TieredCompilation is off.
    env()->record_method_not_compilable(reason, !TieredCompilation);
    // Record failure reason.
    record_failure(reason);
  }
  bool check_node_count(uint margin, const char* reason) {
    if (live_nodes() + margin > max_node_limit()) {
      record_method_not_compilable(reason);
      return true;
    } else {
      return false;
    }
  }

  // Node management
  uint         unique() const              { return _unique; }
  uint         next_unique()               { return _unique++; }
  void         set_unique(uint i)          { _unique = i; }
  static int   debug_idx()                 { return debug_only(_debug_idx)+0; }
  static void  set_debug_idx(int i)        { debug_only(_debug_idx = i); }
  Arena*       node_arena()                { return &_node_arena; }
  Arena*       old_arena()                 { return &_old_arena; }
  RootNode*    root() const                { return _root; }
  void         set_root(RootNode* r)       { _root = r; }
  StartNode*   start() const;              // (Derived from root.)
  void         init_start(StartNode* s);
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
  bool         is_dead_node(uint idx)      { return _dead_node_list.test(idx) != 0; }
  uint         dead_node_count()           { return _dead_node_count; }
  void         reset_dead_node_list()      { _dead_node_list.Reset();
                                             _dead_node_count = 0;
                                           }
  uint          live_nodes() const         {
    int  val = _unique - _dead_node_count;
    assert (val >= 0, "number of tracked dead nodes %d more than created nodes %d", _unique, _dead_node_count);
            return (uint) val;
                                           }
#ifdef ASSERT
  uint         count_live_nodes_by_graph_walk();
  void         print_missing_nodes();
#endif

  // Record modified nodes to check that they are put on IGVN worklist
  void         record_modified_node(Node* n) NOT_DEBUG_RETURN;
  void         remove_modified_node(Node* n) NOT_DEBUG_RETURN;
  DEBUG_ONLY( Unique_Node_List*   modified_nodes() const { return _modified_nodes; } )

  // Constant table
  ConstantTable&   constant_table() { return _constant_table; }

  MachConstantBaseNode*     mach_constant_base_node();
  bool                  has_mach_constant_base_node() const { return _mach_constant_base_node != NULL; }
  // Generated by adlc, true if CallNode requires MachConstantBase.
  bool                      needs_clone_jvms();

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
  AliasType*        alias_type(const TypePtr* adr_type, ciField* field = NULL) { return find_alias_type(adr_type, false, field); }
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
                                   JVMState* jvms, bool allow_inline, float profile_factor, ciKlass* speculative_receiver_type = NULL,
                                   bool allow_intrinsics = true, bool delayed_forbidden = false);
  bool should_delay_inlining(ciMethod* call_method, JVMState* jvms) {
    return should_delay_string_inlining(call_method, jvms) ||
           should_delay_boxing_inlining(call_method, jvms);
  }
  bool should_delay_string_inlining(ciMethod* call_method, JVMState* jvms);
  bool should_delay_boxing_inlining(ciMethod* call_method, JVMState* jvms);

  // Helper functions to identify inlining potential at call-site
  ciMethod* optimize_virtual_call(ciMethod* caller, int bci, ciInstanceKlass* klass,
                                  ciKlass* holder, ciMethod* callee,
                                  const TypeOopPtr* receiver_type, bool is_virtual,
                                  bool &call_does_dispatch, int &vtable_index,
                                  bool check_access = true);
  ciMethod* optimize_inlining(ciMethod* caller, int bci, ciInstanceKlass* klass,
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
                      ciMethodData* logmd = NULL);
  // Report if there were too many recompiles at a method and bci.
  bool too_many_recompiles(ciMethod* method, int bci, Deoptimization::DeoptReason reason);
  // Return a bitset with the reasons where deoptimization is allowed,
  // i.e., where there were not too many uncommon traps.
  int _allowed_reasons;
  int      allowed_deopt_reasons() { return _allowed_reasons; }
  void set_allowed_deopt_reasons();

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
  void              update_dead_node_list(Unique_Node_List &useful);
  void              remove_useless_nodes (Unique_Node_List &useful);

  WarmCallInfo*     warm_calls() const          { return _warm_calls; }
  void          set_warm_calls(WarmCallInfo* l) { _warm_calls = l; }
  WarmCallInfo* pop_warm_call();

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

  void remove_useless_late_inlines(GrowableArray<CallGenerator*>* inlines, Unique_Node_List &useful);

  void process_print_inlining();
  void dump_print_inlining();

  bool over_inlining_cutoff() const {
    if (!inlining_incrementally()) {
      return unique() > (uint)NodeCountInliningCutoff;
    } else {
      return live_nodes() > (uint)LiveNodeCountInliningCutoff;
    }
  }

  void inc_number_of_mh_late_inlines() { _number_of_mh_late_inlines++; }
  void dec_number_of_mh_late_inlines() { assert(_number_of_mh_late_inlines > 0, "_number_of_mh_late_inlines < 0 !"); _number_of_mh_late_inlines--; }
  bool has_mh_late_inlines() const     { return _number_of_mh_late_inlines > 0; }

  void inline_incrementally_one(PhaseIterGVN& igvn);
  void inline_incrementally(PhaseIterGVN& igvn);
  void inline_string_calls(bool parse_time);
  void inline_boxing_calls(PhaseIterGVN& igvn);

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
  int               frame_size_in_bytes() const { return _frame_slots << LogBytesPerInt; }
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

  void  update_interpreter_frame_size(int size) {
    if (_interpreter_frame_size < size) {
      _interpreter_frame_size = size;
    }
  }
  int           bang_size_in_bytes() const;

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
  void              set_frame_complete(int off) { if (!in_scratch_emit_size()) { _code_offsets.set_value(CodeOffsets::Frame_Complete, off); } }
  ExceptionHandlerTable*  handler_table()       { return &_handler_table; }
  ImplicitExceptionTable* inc_table()           { return &_inc_table; }
  OopMapSet*        oop_map_set()               { return _oop_map_set; }
  DebugInformationRecorder* debug_info()        { return env()->debug_info(); }
  Dependencies*     dependencies()              { return env()->dependencies(); }
  static int        CompiledZap_count()         { return _CompiledZap_count; }
  BufferBlob*       scratch_buffer_blob()       { return _scratch_buffer_blob; }
  void         init_scratch_buffer_blob(int const_size);
  void        clear_scratch_buffer_blob();
  void          set_scratch_buffer_blob(BufferBlob* b) { _scratch_buffer_blob = b; }
  relocInfo*        scratch_locs_memory()       { return _scratch_locs_memory; }
  void          set_scratch_locs_memory(relocInfo* b)  { _scratch_locs_memory = b; }

  // emit to scratch blob, report resulting size
  uint              scratch_emit_size(const Node* n);
  void       set_in_scratch_emit_size(bool x)   {        _in_scratch_emit_size = x; }
  bool           in_scratch_emit_size() const   { return _in_scratch_emit_size;     }

  enum ScratchBufferBlob {
#if defined(PPC64)
    MAX_inst_size       = 2048,
#else
    MAX_inst_size       = 1024,
#endif
    MAX_locs_size       = 128, // number of relocInfo elements
    MAX_const_size      = 128,
    MAX_stubs_size      = 128
  };

  // Major entry point.  Given a Scope, compile the associated method.
  // For normal compilations, entry_bci is InvocationEntryBci.  For on stack
  // replacement, entry_bci indicates the bytecode for which to compile a
  // continuation.
  Compile(ciEnv* ci_env, C2Compiler* compiler, ciMethod* target,
          int entry_bci, bool subsume_loads, bool do_escape_analysis,
          bool eliminate_boxing, DirectiveSet* directive);

  // Second major entry point.  From the TypeFunc signature, generate code
  // to pass arguments from the Java calling convention to the C calling
  // convention.
  Compile(ciEnv* ci_env, const TypeFunc *(*gen)(),
          address stub_function, const char *stub_name,
          int is_fancy_jump, bool pass_tls,
          bool save_arg_registers, bool return_pc, DirectiveSet* directive);

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

  // Initialize code buffer
  CodeBuffer* init_buffer(uint* blk_starts);

  // Write out basic block data to code buffer
  void fill_buffer(CodeBuffer* cb, uint* blk_starts);

  // Determine which variable sized branches can be shortened
  void shorten_branches(uint* blk_starts, int& code_size, int& reloc_size, int& stub_size);

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
  AliasType* find_alias_type(const TypePtr* adr_type, bool no_create, ciField* field);

  void verify_top(Node*) const PRODUCT_RETURN;

  // Intrinsic setup.
  void           register_library_intrinsics();                            // initializer
  CallGenerator* make_vm_intrinsic(ciMethod* m, bool is_virtual);          // constructor
  int            intrinsic_insertion_index(ciMethod* m, bool is_virtual, bool& found);  // helper
  CallGenerator* find_intrinsic(ciMethod* m, bool is_virtual);             // query fn
  void           register_intrinsic(CallGenerator* cg);                    // update fn

#ifndef PRODUCT
  static juint  _intrinsic_hist_count[vmIntrinsics::ID_LIMIT];
  static jubyte _intrinsic_hist_flags[vmIntrinsics::ID_LIMIT];
#endif
  // Function calls made by the public function final_graph_reshaping.
  // No need to be made public as they are not called elsewhere.
  void final_graph_reshaping_impl( Node *n, Final_Reshape_Counts &frc);
  void final_graph_reshaping_walk( Node_Stack &nstack, Node *root, Final_Reshape_Counts &frc );
  void eliminate_redundant_card_marks(Node* n);

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

  // Verify GC barrier patterns
  void verify_barriers() PRODUCT_RETURN;

  // End-of-run dumps.
  static void print_statistics() PRODUCT_RETURN;

  // Dump formatted assembly
  void dump_asm(int *pcs = NULL, uint pc_limit = 0) PRODUCT_RETURN;
  void dump_pc(int *pcs, int pc_limit, Node *n);

  // Verify ADLC assumptions during startup
  static void adlc_verification() PRODUCT_RETURN;

  // Definitions of pd methods
  static void pd_compiler2_init();

  // Static parse-time type checking logic for gen_subtype_check:
  enum { SSC_always_false, SSC_always_true, SSC_easy_test, SSC_full_test };
  int static_subtype_check(ciKlass* superk, ciKlass* subk);

  static Node* conv_I2X_index(PhaseGVN* phase, Node* offset, const TypeInt* sizetype,
                              // Optional control dependency (for example, on range check)
                              Node* ctrl = NULL);

  // Convert integer value to a narrowed long type dependent on ctrl (for example, a range check)
  static Node* constrained_convI2L(PhaseGVN* phase, Node* value, const TypeInt* itype, Node* ctrl);

  // Auxiliary method for randomized fuzzing/stressing
  static bool randomized_select(int count);

  // supporting clone_map
  CloneMap&     clone_map();
  void          set_clone_map(Dict* d);
};

#endif // SHARE_VM_OPTO_COMPILE_HPP
