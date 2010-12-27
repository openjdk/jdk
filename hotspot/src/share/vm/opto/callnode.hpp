/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_CALLNODE_HPP
#define SHARE_VM_OPTO_CALLNODE_HPP

#include "opto/connode.hpp"
#include "opto/mulnode.hpp"
#include "opto/multnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/type.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

class Chaitin;
class NamedCounter;
class MultiNode;
class  SafePointNode;
class   CallNode;
class     CallJavaNode;
class       CallStaticJavaNode;
class       CallDynamicJavaNode;
class     CallRuntimeNode;
class       CallLeafNode;
class         CallLeafNoFPNode;
class     AllocateNode;
class       AllocateArrayNode;
class     LockNode;
class     UnlockNode;
class JVMState;
class OopMap;
class State;
class StartNode;
class MachCallNode;
class FastLockNode;

//------------------------------StartNode--------------------------------------
// The method start node
class StartNode : public MultiNode {
  virtual uint cmp( const Node &n ) const;
  virtual uint size_of() const; // Size is bigger
public:
  const TypeTuple *_domain;
  StartNode( Node *root, const TypeTuple *domain ) : MultiNode(2), _domain(domain) {
    init_class_id(Class_Start);
    init_flags(Flag_is_block_start);
    init_req(0,this);
    init_req(1,root);
  }
  virtual int Opcode() const;
  virtual bool pinned() const { return true; };
  virtual const Type *bottom_type() const;
  virtual const TypePtr *adr_type() const { return TypePtr::BOTTOM; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual void  calling_convention( BasicType* sig_bt, VMRegPair *parm_reg, uint length ) const;
  virtual const RegMask &in_RegMask(uint) const;
  virtual Node *match( const ProjNode *proj, const Matcher *m );
  virtual uint ideal_reg() const { return 0; }
#ifndef PRODUCT
  virtual void  dump_spec(outputStream *st) const;
#endif
};

//------------------------------StartOSRNode-----------------------------------
// The method start node for on stack replacement code
class StartOSRNode : public StartNode {
public:
  StartOSRNode( Node *root, const TypeTuple *domain ) : StartNode(root, domain) {}
  virtual int   Opcode() const;
  static  const TypeTuple *osr_domain();
};


//------------------------------ParmNode---------------------------------------
// Incoming parameters
class ParmNode : public ProjNode {
  static const char * const names[TypeFunc::Parms+1];
public:
  ParmNode( StartNode *src, uint con ) : ProjNode(src,con) {
    init_class_id(Class_Parm);
  }
  virtual int Opcode() const;
  virtual bool  is_CFG() const { return (_con == TypeFunc::Control); }
  virtual uint ideal_reg() const;
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
};


//------------------------------ReturnNode-------------------------------------
// Return from subroutine node
class ReturnNode : public Node {
public:
  ReturnNode( uint edges, Node *cntrl, Node *i_o, Node *memory, Node *retadr, Node *frameptr );
  virtual int Opcode() const;
  virtual bool  is_CFG() const { return true; }
  virtual uint hash() const { return NO_HASH; }  // CFG nodes do not hash
  virtual bool depends_only_on_test() const { return false; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual uint ideal_reg() const { return NotAMachineReg; }
  virtual uint match_edge(uint idx) const;
#ifndef PRODUCT
  virtual void dump_req() const;
#endif
};


//------------------------------RethrowNode------------------------------------
// Rethrow of exception at call site.  Ends a procedure before rethrowing;
// ends the current basic block like a ReturnNode.  Restores registers and
// unwinds stack.  Rethrow happens in the caller's method.
class RethrowNode : public Node {
 public:
  RethrowNode( Node *cntrl, Node *i_o, Node *memory, Node *frameptr, Node *ret_adr, Node *exception );
  virtual int Opcode() const;
  virtual bool  is_CFG() const { return true; }
  virtual uint hash() const { return NO_HASH; }  // CFG nodes do not hash
  virtual bool depends_only_on_test() const { return false; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual uint match_edge(uint idx) const;
  virtual uint ideal_reg() const { return NotAMachineReg; }
#ifndef PRODUCT
  virtual void dump_req() const;
#endif
};


//------------------------------TailCallNode-----------------------------------
// Pop stack frame and jump indirect
class TailCallNode : public ReturnNode {
public:
  TailCallNode( Node *cntrl, Node *i_o, Node *memory, Node *frameptr, Node *retadr, Node *target, Node *moop )
    : ReturnNode( TypeFunc::Parms+2, cntrl, i_o, memory, frameptr, retadr ) {
    init_req(TypeFunc::Parms, target);
    init_req(TypeFunc::Parms+1, moop);
  }

  virtual int Opcode() const;
  virtual uint match_edge(uint idx) const;
};

//------------------------------TailJumpNode-----------------------------------
// Pop stack frame and jump indirect
class TailJumpNode : public ReturnNode {
public:
  TailJumpNode( Node *cntrl, Node *i_o, Node *memory, Node *frameptr, Node *target, Node *ex_oop)
    : ReturnNode(TypeFunc::Parms+2, cntrl, i_o, memory, frameptr, Compile::current()->top()) {
    init_req(TypeFunc::Parms, target);
    init_req(TypeFunc::Parms+1, ex_oop);
  }

  virtual int Opcode() const;
  virtual uint match_edge(uint idx) const;
};

//-------------------------------JVMState-------------------------------------
// A linked list of JVMState nodes captures the whole interpreter state,
// plus GC roots, for all active calls at some call site in this compilation
// unit.  (If there is no inlining, then the list has exactly one link.)
// This provides a way to map the optimized program back into the interpreter,
// or to let the GC mark the stack.
class JVMState : public ResourceObj {
public:
  typedef enum {
    Reexecute_Undefined = -1, // not defined -- will be translated into false later
    Reexecute_False     =  0, // false       -- do not reexecute
    Reexecute_True      =  1  // true        -- reexecute the bytecode
  } ReexecuteState; //Reexecute State

private:
  JVMState*         _caller;    // List pointer for forming scope chains
  uint              _depth;     // One mroe than caller depth, or one.
  uint              _locoff;    // Offset to locals in input edge mapping
  uint              _stkoff;    // Offset to stack in input edge mapping
  uint              _monoff;    // Offset to monitors in input edge mapping
  uint              _scloff;    // Offset to fields of scalar objs in input edge mapping
  uint              _endoff;    // Offset to end of input edge mapping
  uint              _sp;        // Jave Expression Stack Pointer for this state
  int               _bci;       // Byte Code Index of this JVM point
  ReexecuteState    _reexecute; // Whether this bytecode need to be re-executed
  ciMethod*         _method;    // Method Pointer
  SafePointNode*    _map;       // Map node associated with this scope
public:
  friend class Compile;
  friend class PreserveReexecuteState;

  // Because JVMState objects live over the entire lifetime of the
  // Compile object, they are allocated into the comp_arena, which
  // does not get resource marked or reset during the compile process
  void *operator new( size_t x, Compile* C ) { return C->comp_arena()->Amalloc(x); }
  void operator delete( void * ) { } // fast deallocation

  // Create a new JVMState, ready for abstract interpretation.
  JVMState(ciMethod* method, JVMState* caller);
  JVMState(int stack_size);  // root state; has a null method

  // Access functions for the JVM
  uint              locoff() const { return _locoff; }
  uint              stkoff() const { return _stkoff; }
  uint              argoff() const { return _stkoff + _sp; }
  uint              monoff() const { return _monoff; }
  uint              scloff() const { return _scloff; }
  uint              endoff() const { return _endoff; }
  uint              oopoff() const { return debug_end(); }

  int            loc_size() const { return _stkoff - _locoff; }
  int            stk_size() const { return _monoff - _stkoff; }
  int            mon_size() const { return _scloff - _monoff; }
  int            scl_size() const { return _endoff - _scloff; }

  bool        is_loc(uint i) const { return i >= _locoff && i < _stkoff; }
  bool        is_stk(uint i) const { return i >= _stkoff && i < _monoff; }
  bool        is_mon(uint i) const { return i >= _monoff && i < _scloff; }
  bool        is_scl(uint i) const { return i >= _scloff && i < _endoff; }

  uint                      sp() const { return _sp; }
  int                      bci() const { return _bci; }
  bool        should_reexecute() const { return _reexecute==Reexecute_True; }
  bool  is_reexecute_undefined() const { return _reexecute==Reexecute_Undefined; }
  bool              has_method() const { return _method != NULL; }
  ciMethod*             method() const { assert(has_method(), ""); return _method; }
  JVMState*             caller() const { return _caller; }
  SafePointNode*           map() const { return _map; }
  uint                   depth() const { return _depth; }
  uint             debug_start() const; // returns locoff of root caller
  uint               debug_end() const; // returns endoff of self
  uint              debug_size() const {
    return loc_size() + sp() + mon_size() + scl_size();
  }
  uint        debug_depth()  const; // returns sum of debug_size values at all depths

  // Returns the JVM state at the desired depth (1 == root).
  JVMState* of_depth(int d) const;

  // Tells if two JVM states have the same call chain (depth, methods, & bcis).
  bool same_calls_as(const JVMState* that) const;

  // Monitors (monitors are stored as (boxNode, objNode) pairs
  enum { logMonitorEdges = 1 };
  int  nof_monitors()              const { return mon_size() >> logMonitorEdges; }
  int  monitor_depth()             const { return nof_monitors() + (caller() ? caller()->monitor_depth() : 0); }
  int  monitor_box_offset(int idx) const { return monoff() + (idx << logMonitorEdges) + 0; }
  int  monitor_obj_offset(int idx) const { return monoff() + (idx << logMonitorEdges) + 1; }
  bool is_monitor_box(uint off)    const {
    assert(is_mon(off), "should be called only for monitor edge");
    return (0 == bitfield(off - monoff(), 0, logMonitorEdges));
  }
  bool is_monitor_use(uint off)    const { return (is_mon(off)
                                                   && is_monitor_box(off))
                                             || (caller() && caller()->is_monitor_use(off)); }

  // Initialization functions for the JVM
  void              set_locoff(uint off) { _locoff = off; }
  void              set_stkoff(uint off) { _stkoff = off; }
  void              set_monoff(uint off) { _monoff = off; }
  void              set_scloff(uint off) { _scloff = off; }
  void              set_endoff(uint off) { _endoff = off; }
  void              set_offsets(uint off) {
    _locoff = _stkoff = _monoff = _scloff = _endoff = off;
  }
  void              set_map(SafePointNode *map) { _map = map; }
  void              set_sp(uint sp) { _sp = sp; }
                    // _reexecute is initialized to "undefined" for a new bci
  void              set_bci(int bci) {if(_bci != bci)_reexecute=Reexecute_Undefined; _bci = bci; }
  void              set_should_reexecute(bool reexec) {_reexecute = reexec ? Reexecute_True : Reexecute_False;}

  // Miscellaneous utility functions
  JVMState* clone_deep(Compile* C) const;    // recursively clones caller chain
  JVMState* clone_shallow(Compile* C) const; // retains uncloned caller

#ifndef PRODUCT
  void      format(PhaseRegAlloc *regalloc, const Node *n, outputStream* st) const;
  void      dump_spec(outputStream *st) const;
  void      dump_on(outputStream* st) const;
  void      dump() const {
    dump_on(tty);
  }
#endif
};

//------------------------------SafePointNode----------------------------------
// A SafePointNode is a subclass of a MultiNode for convenience (and
// potential code sharing) only - conceptually it is independent of
// the Node semantics.
class SafePointNode : public MultiNode {
  virtual uint           cmp( const Node &n ) const;
  virtual uint           size_of() const;       // Size is bigger

public:
  SafePointNode(uint edges, JVMState* jvms,
                // A plain safepoint advertises no memory effects (NULL):
                const TypePtr* adr_type = NULL)
    : MultiNode( edges ),
      _jvms(jvms),
      _oop_map(NULL),
      _adr_type(adr_type)
  {
    init_class_id(Class_SafePoint);
  }

  OopMap*         _oop_map;   // Array of OopMap info (8-bit char) for GC
  JVMState* const _jvms;      // Pointer to list of JVM State objects
  const TypePtr*  _adr_type;  // What type of memory does this node produce?

  // Many calls take *all* of memory as input,
  // but some produce a limited subset of that memory as output.
  // The adr_type reports the call's behavior as a store, not a load.

  virtual JVMState* jvms() const { return _jvms; }
  void set_jvms(JVMState* s) {
    *(JVMState**)&_jvms = s;  // override const attribute in the accessor
  }
  OopMap *oop_map() const { return _oop_map; }
  void set_oop_map(OopMap *om) { _oop_map = om; }

  // Functionality from old debug nodes which has changed
  Node *local(JVMState* jvms, uint idx) const {
    assert(verify_jvms(jvms), "jvms must match");
    return in(jvms->locoff() + idx);
  }
  Node *stack(JVMState* jvms, uint idx) const {
    assert(verify_jvms(jvms), "jvms must match");
    return in(jvms->stkoff() + idx);
  }
  Node *argument(JVMState* jvms, uint idx) const {
    assert(verify_jvms(jvms), "jvms must match");
    return in(jvms->argoff() + idx);
  }
  Node *monitor_box(JVMState* jvms, uint idx) const {
    assert(verify_jvms(jvms), "jvms must match");
    return in(jvms->monitor_box_offset(idx));
  }
  Node *monitor_obj(JVMState* jvms, uint idx) const {
    assert(verify_jvms(jvms), "jvms must match");
    return in(jvms->monitor_obj_offset(idx));
  }

  void  set_local(JVMState* jvms, uint idx, Node *c);

  void  set_stack(JVMState* jvms, uint idx, Node *c) {
    assert(verify_jvms(jvms), "jvms must match");
    set_req(jvms->stkoff() + idx, c);
  }
  void  set_argument(JVMState* jvms, uint idx, Node *c) {
    assert(verify_jvms(jvms), "jvms must match");
    set_req(jvms->argoff() + idx, c);
  }
  void ensure_stack(JVMState* jvms, uint stk_size) {
    assert(verify_jvms(jvms), "jvms must match");
    int grow_by = (int)stk_size - (int)jvms->stk_size();
    if (grow_by > 0)  grow_stack(jvms, grow_by);
  }
  void grow_stack(JVMState* jvms, uint grow_by);
  // Handle monitor stack
  void push_monitor( const FastLockNode *lock );
  void pop_monitor ();
  Node *peek_monitor_box() const;
  Node *peek_monitor_obj() const;

  // Access functions for the JVM
  Node *control  () const { return in(TypeFunc::Control  ); }
  Node *i_o      () const { return in(TypeFunc::I_O      ); }
  Node *memory   () const { return in(TypeFunc::Memory   ); }
  Node *returnadr() const { return in(TypeFunc::ReturnAdr); }
  Node *frameptr () const { return in(TypeFunc::FramePtr ); }

  void set_control  ( Node *c ) { set_req(TypeFunc::Control,c); }
  void set_i_o      ( Node *c ) { set_req(TypeFunc::I_O    ,c); }
  void set_memory   ( Node *c ) { set_req(TypeFunc::Memory ,c); }

  MergeMemNode* merged_memory() const {
    return in(TypeFunc::Memory)->as_MergeMem();
  }

  // The parser marks useless maps as dead when it's done with them:
  bool is_killed() { return in(TypeFunc::Control) == NULL; }

  // Exception states bubbling out of subgraphs such as inlined calls
  // are recorded here.  (There might be more than one, hence the "next".)
  // This feature is used only for safepoints which serve as "maps"
  // for JVM states during parsing, intrinsic expansion, etc.
  SafePointNode*         next_exception() const;
  void               set_next_exception(SafePointNode* n);
  bool                   has_exceptions() const { return next_exception() != NULL; }

  // Standard Node stuff
  virtual int            Opcode() const;
  virtual bool           pinned() const { return true; }
  virtual const Type    *Value( PhaseTransform *phase ) const;
  virtual const Type    *bottom_type() const { return Type::CONTROL; }
  virtual const TypePtr *adr_type() const { return _adr_type; }
  virtual Node          *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node          *Identity( PhaseTransform *phase );
  virtual uint           ideal_reg() const { return 0; }
  virtual const RegMask &in_RegMask(uint) const;
  virtual const RegMask &out_RegMask() const;
  virtual uint           match_edge(uint idx) const;

  static  bool           needs_polling_address_input();

#ifndef PRODUCT
  virtual void              dump_spec(outputStream *st) const;
#endif
};

//------------------------------SafePointScalarObjectNode----------------------
// A SafePointScalarObjectNode represents the state of a scalarized object
// at a safepoint.

class SafePointScalarObjectNode: public TypeNode {
  uint _first_index; // First input edge index of a SafePoint node where
                     // states of the scalarized object fields are collected.
  uint _n_fields;    // Number of non-static fields of the scalarized object.
  DEBUG_ONLY(AllocateNode* _alloc;)
public:
  SafePointScalarObjectNode(const TypeOopPtr* tp,
#ifdef ASSERT
                            AllocateNode* alloc,
#endif
                            uint first_index, uint n_fields);
  virtual int Opcode() const;
  virtual uint           ideal_reg() const;
  virtual const RegMask &in_RegMask(uint) const;
  virtual const RegMask &out_RegMask() const;
  virtual uint           match_edge(uint idx) const;

  uint first_index() const { return _first_index; }
  uint n_fields()    const { return _n_fields; }
  DEBUG_ONLY(AllocateNode* alloc() const { return _alloc; })

  // SafePointScalarObject should be always pinned to the control edge
  // of the SafePoint node for which it was generated.
  virtual bool pinned() const; // { return true; }

  // SafePointScalarObject depends on the SafePoint node
  // for which it was generated.
  virtual bool depends_only_on_test() const; // { return false; }

  virtual uint size_of() const { return sizeof(*this); }

  // Assumes that "this" is an argument to a safepoint node "s", and that
  // "new_call" is being created to correspond to "s".  But the difference
  // between the start index of the jvmstates of "new_call" and "s" is
  // "jvms_adj".  Produce and return a SafePointScalarObjectNode that
  // corresponds appropriately to "this" in "new_call".  Assumes that
  // "sosn_map" is a map, specific to the translation of "s" to "new_call",
  // mapping old SafePointScalarObjectNodes to new, to avoid multiple copies.
  SafePointScalarObjectNode* clone(int jvms_adj, Dict* sosn_map) const;

#ifndef PRODUCT
  virtual void              dump_spec(outputStream *st) const;
#endif
};


// Simple container for the outgoing projections of a call.  Useful
// for serious surgery on calls.
class CallProjections : public StackObj {
public:
  Node* fallthrough_proj;
  Node* fallthrough_catchproj;
  Node* fallthrough_memproj;
  Node* fallthrough_ioproj;
  Node* catchall_catchproj;
  Node* catchall_memproj;
  Node* catchall_ioproj;
  Node* resproj;
  Node* exobj;
};


//------------------------------CallNode---------------------------------------
// Call nodes now subsume the function of debug nodes at callsites, so they
// contain the functionality of a full scope chain of debug nodes.
class CallNode : public SafePointNode {
public:
  const TypeFunc *_tf;        // Function type
  address      _entry_point;  // Address of method being called
  float        _cnt;          // Estimate of number of times called

  CallNode(const TypeFunc* tf, address addr, const TypePtr* adr_type)
    : SafePointNode(tf->domain()->cnt(), NULL, adr_type),
      _tf(tf),
      _entry_point(addr),
      _cnt(COUNT_UNKNOWN)
  {
    init_class_id(Class_Call);
    init_flags(Flag_is_Call);
  }

  const TypeFunc* tf()        const { return _tf; }
  const address entry_point() const { return _entry_point; }
  const float   cnt()         const { return _cnt; }

  void set_tf(const TypeFunc* tf) { _tf = tf; }
  void set_entry_point(address p) { _entry_point = p; }
  void set_cnt(float c)           { _cnt = c; }

  virtual const Type *bottom_type() const;
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase ) { return this; }
  virtual uint        cmp( const Node &n ) const;
  virtual uint        size_of() const = 0;
  virtual void        calling_convention( BasicType* sig_bt, VMRegPair *parm_regs, uint argcnt ) const;
  virtual Node       *match( const ProjNode *proj, const Matcher *m );
  virtual uint        ideal_reg() const { return NotAMachineReg; }
  // Are we guaranteed that this node is a safepoint?  Not true for leaf calls and
  // for some macro nodes whose expansion does not have a safepoint on the fast path.
  virtual bool        guaranteed_safepoint()  { return true; }
  // For macro nodes, the JVMState gets modified during expansion, so when cloning
  // the node the JVMState must be cloned.
  virtual void        clone_jvms() { }   // default is not to clone

  // Returns true if the call may modify n
  virtual bool        may_modify(const TypePtr *addr_t, PhaseTransform *phase);
  // Does this node have a use of n other than in debug information?
  bool                has_non_debug_use(Node *n);
  // Returns the unique CheckCastPP of a call
  // or result projection is there are several CheckCastPP
  // or returns NULL if there is no one.
  Node *result_cast();

  // Collect all the interesting edges from a call for use in
  // replacing the call by something else.  Used by macro expansion
  // and the late inlining support.
  void extract_projections(CallProjections* projs, bool separate_io_proj);

  virtual uint match_edge(uint idx) const;

#ifndef PRODUCT
  virtual void        dump_req()  const;
  virtual void        dump_spec(outputStream *st) const;
#endif
};


//------------------------------CallJavaNode-----------------------------------
// Make a static or dynamic subroutine call node using Java calling
// convention.  (The "Java" calling convention is the compiler's calling
// convention, as opposed to the interpreter's or that of native C.)
class CallJavaNode : public CallNode {
protected:
  virtual uint cmp( const Node &n ) const;
  virtual uint size_of() const; // Size is bigger

  bool    _optimized_virtual;
  bool    _method_handle_invoke;
  ciMethod* _method;            // Method being direct called
public:
  const int       _bci;         // Byte Code Index of call byte code
  CallJavaNode(const TypeFunc* tf , address addr, ciMethod* method, int bci)
    : CallNode(tf, addr, TypePtr::BOTTOM),
      _method(method), _bci(bci),
      _optimized_virtual(false),
      _method_handle_invoke(false)
  {
    init_class_id(Class_CallJava);
  }

  virtual int   Opcode() const;
  ciMethod* method() const                { return _method; }
  void  set_method(ciMethod *m)           { _method = m; }
  void  set_optimized_virtual(bool f)     { _optimized_virtual = f; }
  bool  is_optimized_virtual() const      { return _optimized_virtual; }
  void  set_method_handle_invoke(bool f)  { _method_handle_invoke = f; }
  bool  is_method_handle_invoke() const   { return _method_handle_invoke; }

#ifndef PRODUCT
  virtual void  dump_spec(outputStream *st) const;
#endif
};

//------------------------------CallStaticJavaNode-----------------------------
// Make a direct subroutine call using Java calling convention (for static
// calls and optimized virtual calls, plus calls to wrappers for run-time
// routines); generates static stub.
class CallStaticJavaNode : public CallJavaNode {
  virtual uint cmp( const Node &n ) const;
  virtual uint size_of() const; // Size is bigger
public:
  CallStaticJavaNode(const TypeFunc* tf, address addr, ciMethod* method, int bci)
    : CallJavaNode(tf, addr, method, bci), _name(NULL) {
    init_class_id(Class_CallStaticJava);
  }
  CallStaticJavaNode(const TypeFunc* tf, address addr, const char* name, int bci,
                     const TypePtr* adr_type)
    : CallJavaNode(tf, addr, NULL, bci), _name(name) {
    init_class_id(Class_CallStaticJava);
    // This node calls a runtime stub, which often has narrow memory effects.
    _adr_type = adr_type;
  }
  const char *_name;            // Runtime wrapper name

  // If this is an uncommon trap, return the request code, else zero.
  int uncommon_trap_request() const;
  static int extract_uncommon_trap_request(const Node* call);

  virtual int         Opcode() const;
#ifndef PRODUCT
  virtual void        dump_spec(outputStream *st) const;
#endif
};

//------------------------------CallDynamicJavaNode----------------------------
// Make a dispatched call using Java calling convention.
class CallDynamicJavaNode : public CallJavaNode {
  virtual uint cmp( const Node &n ) const;
  virtual uint size_of() const; // Size is bigger
public:
  CallDynamicJavaNode( const TypeFunc *tf , address addr, ciMethod* method, int vtable_index, int bci ) : CallJavaNode(tf,addr,method,bci), _vtable_index(vtable_index) {
    init_class_id(Class_CallDynamicJava);
  }

  int _vtable_index;
  virtual int   Opcode() const;
#ifndef PRODUCT
  virtual void  dump_spec(outputStream *st) const;
#endif
};

//------------------------------CallRuntimeNode--------------------------------
// Make a direct subroutine call node into compiled C++ code.
class CallRuntimeNode : public CallNode {
  virtual uint cmp( const Node &n ) const;
  virtual uint size_of() const; // Size is bigger
public:
  CallRuntimeNode(const TypeFunc* tf, address addr, const char* name,
                  const TypePtr* adr_type)
    : CallNode(tf, addr, adr_type),
      _name(name)
  {
    init_class_id(Class_CallRuntime);
  }

  const char *_name;            // Printable name, if _method is NULL
  virtual int   Opcode() const;
  virtual void  calling_convention( BasicType* sig_bt, VMRegPair *parm_regs, uint argcnt ) const;

#ifndef PRODUCT
  virtual void  dump_spec(outputStream *st) const;
#endif
};

//------------------------------CallLeafNode-----------------------------------
// Make a direct subroutine call node into compiled C++ code, without
// safepoints
class CallLeafNode : public CallRuntimeNode {
public:
  CallLeafNode(const TypeFunc* tf, address addr, const char* name,
               const TypePtr* adr_type)
    : CallRuntimeNode(tf, addr, name, adr_type)
  {
    init_class_id(Class_CallLeaf);
  }
  virtual int   Opcode() const;
  virtual bool        guaranteed_safepoint()  { return false; }
#ifndef PRODUCT
  virtual void  dump_spec(outputStream *st) const;
#endif
};

//------------------------------CallLeafNoFPNode-------------------------------
// CallLeafNode, not using floating point or using it in the same manner as
// the generated code
class CallLeafNoFPNode : public CallLeafNode {
public:
  CallLeafNoFPNode(const TypeFunc* tf, address addr, const char* name,
                   const TypePtr* adr_type)
    : CallLeafNode(tf, addr, name, adr_type)
  {
  }
  virtual int   Opcode() const;
};


//------------------------------Allocate---------------------------------------
// High-level memory allocation
//
//  AllocateNode and AllocateArrayNode are subclasses of CallNode because they will
//  get expanded into a code sequence containing a call.  Unlike other CallNodes,
//  they have 2 memory projections and 2 i_o projections (which are distinguished by
//  the _is_io_use flag in the projection.)  This is needed when expanding the node in
//  order to differentiate the uses of the projection on the normal control path from
//  those on the exception return path.
//
class AllocateNode : public CallNode {
public:
  enum {
    // Output:
    RawAddress  = TypeFunc::Parms,    // the newly-allocated raw address
    // Inputs:
    AllocSize   = TypeFunc::Parms,    // size (in bytes) of the new object
    KlassNode,                        // type (maybe dynamic) of the obj.
    InitialTest,                      // slow-path test (may be constant)
    ALength,                          // array length (or TOP if none)
    ParmLimit
  };

  static const TypeFunc* alloc_type() {
    const Type** fields = TypeTuple::fields(ParmLimit - TypeFunc::Parms);
    fields[AllocSize]   = TypeInt::POS;
    fields[KlassNode]   = TypeInstPtr::NOTNULL;
    fields[InitialTest] = TypeInt::BOOL;
    fields[ALength]     = TypeInt::INT;  // length (can be a bad length)

    const TypeTuple *domain = TypeTuple::make(ParmLimit, fields);

    // create result type (range)
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop

    const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

    return TypeFunc::make(domain, range);
  }

  bool _is_scalar_replaceable;  // Result of Escape Analysis

  virtual uint size_of() const; // Size is bigger
  AllocateNode(Compile* C, const TypeFunc *atype, Node *ctrl, Node *mem, Node *abio,
               Node *size, Node *klass_node, Node *initial_test);
  // Expansion modifies the JVMState, so we need to clone it
  virtual void  clone_jvms() {
    set_jvms(jvms()->clone_deep(Compile::current()));
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegP; }
  virtual bool        guaranteed_safepoint()  { return false; }

  // allocations do not modify their arguments
  virtual bool        may_modify(const TypePtr *addr_t, PhaseTransform *phase) { return false;}

  // Pattern-match a possible usage of AllocateNode.
  // Return null if no allocation is recognized.
  // The operand is the pointer produced by the (possible) allocation.
  // It must be a projection of the Allocate or its subsequent CastPP.
  // (Note:  This function is defined in file graphKit.cpp, near
  // GraphKit::new_instance/new_array, whose output it recognizes.)
  // The 'ptr' may not have an offset unless the 'offset' argument is given.
  static AllocateNode* Ideal_allocation(Node* ptr, PhaseTransform* phase);

  // Fancy version which uses AddPNode::Ideal_base_and_offset to strip
  // an offset, which is reported back to the caller.
  // (Note:  AllocateNode::Ideal_allocation is defined in graphKit.cpp.)
  static AllocateNode* Ideal_allocation(Node* ptr, PhaseTransform* phase,
                                        intptr_t& offset);

  // Dig the klass operand out of a (possible) allocation site.
  static Node* Ideal_klass(Node* ptr, PhaseTransform* phase) {
    AllocateNode* allo = Ideal_allocation(ptr, phase);
    return (allo == NULL) ? NULL : allo->in(KlassNode);
  }

  // Conservatively small estimate of offset of first non-header byte.
  int minimum_header_size() {
    return is_AllocateArray() ? arrayOopDesc::base_offset_in_bytes(T_BYTE) :
                                instanceOopDesc::base_offset_in_bytes();
  }

  // Return the corresponding initialization barrier (or null if none).
  // Walks out edges to find it...
  // (Note: Both InitializeNode::allocation and AllocateNode::initialization
  // are defined in graphKit.cpp, which sets up the bidirectional relation.)
  InitializeNode* initialization();

  // Convenience for initialization->maybe_set_complete(phase)
  bool maybe_set_complete(PhaseGVN* phase);
};

//------------------------------AllocateArray---------------------------------
//
// High-level array allocation
//
class AllocateArrayNode : public AllocateNode {
public:
  AllocateArrayNode(Compile* C, const TypeFunc *atype, Node *ctrl, Node *mem, Node *abio,
                    Node* size, Node* klass_node, Node* initial_test,
                    Node* count_val
                    )
    : AllocateNode(C, atype, ctrl, mem, abio, size, klass_node,
                   initial_test)
  {
    init_class_id(Class_AllocateArray);
    set_req(AllocateNode::ALength,        count_val);
  }
  virtual int Opcode() const;
  virtual uint size_of() const; // Size is bigger
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);

  // Dig the length operand out of a array allocation site.
  Node* Ideal_length() {
    return in(AllocateNode::ALength);
  }

  // Dig the length operand out of a array allocation site and narrow the
  // type with a CastII, if necesssary
  Node* make_ideal_length(const TypeOopPtr* ary_type, PhaseTransform *phase, bool can_create = true);

  // Pattern-match a possible usage of AllocateArrayNode.
  // Return null if no allocation is recognized.
  static AllocateArrayNode* Ideal_array_allocation(Node* ptr, PhaseTransform* phase) {
    AllocateNode* allo = Ideal_allocation(ptr, phase);
    return (allo == NULL || !allo->is_AllocateArray())
           ? NULL : allo->as_AllocateArray();
  }
};

//------------------------------AbstractLockNode-----------------------------------
class AbstractLockNode: public CallNode {
private:
  bool _eliminate;    // indicates this lock can be safely eliminated
  bool _coarsened;    // indicates this lock was coarsened
#ifndef PRODUCT
  NamedCounter* _counter;
#endif

protected:
  // helper functions for lock elimination
  //

  bool find_matching_unlock(const Node* ctrl, LockNode* lock,
                            GrowableArray<AbstractLockNode*> &lock_ops);
  bool find_lock_and_unlock_through_if(Node* node, LockNode* lock,
                                       GrowableArray<AbstractLockNode*> &lock_ops);
  bool find_unlocks_for_region(const RegionNode* region, LockNode* lock,
                               GrowableArray<AbstractLockNode*> &lock_ops);
  LockNode *find_matching_lock(UnlockNode* unlock);


public:
  AbstractLockNode(const TypeFunc *tf)
    : CallNode(tf, NULL, TypeRawPtr::BOTTOM),
      _coarsened(false),
      _eliminate(false)
  {
#ifndef PRODUCT
    _counter = NULL;
#endif
  }
  virtual int Opcode() const = 0;
  Node *   obj_node() const       {return in(TypeFunc::Parms + 0); }
  Node *   box_node() const       {return in(TypeFunc::Parms + 1); }
  Node *   fastlock_node() const  {return in(TypeFunc::Parms + 2); }
  const Type *sub(const Type *t1, const Type *t2) const { return TypeInt::CC;}

  virtual uint size_of() const { return sizeof(*this); }

  bool is_eliminated()         {return _eliminate; }
  // mark node as eliminated and update the counter if there is one
  void set_eliminated();

  bool is_coarsened()  { return _coarsened; }
  void set_coarsened() { _coarsened = true; }

  // locking does not modify its arguments
  virtual bool        may_modify(const TypePtr *addr_t, PhaseTransform *phase){ return false;}

#ifndef PRODUCT
  void create_lock_counter(JVMState* s);
  NamedCounter* counter() const { return _counter; }
#endif
};

//------------------------------Lock---------------------------------------
// High-level lock operation
//
// This is a subclass of CallNode because it is a macro node which gets expanded
// into a code sequence containing a call.  This node takes 3 "parameters":
//    0  -  object to lock
//    1 -   a BoxLockNode
//    2 -   a FastLockNode
//
class LockNode : public AbstractLockNode {
public:

  static const TypeFunc *lock_type() {
    // create input type (domain)
    const Type **fields = TypeTuple::fields(3);
    fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // Object to be Locked
    fields[TypeFunc::Parms+1] = TypeRawPtr::BOTTOM;    // Address of stack location for lock
    fields[TypeFunc::Parms+2] = TypeInt::BOOL;         // FastLock
    const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+3,fields);

    // create result type (range)
    fields = TypeTuple::fields(0);

    const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

    return TypeFunc::make(domain,range);
  }

  virtual int Opcode() const;
  virtual uint size_of() const; // Size is bigger
  LockNode(Compile* C, const TypeFunc *tf) : AbstractLockNode( tf ) {
    init_class_id(Class_Lock);
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  virtual bool        guaranteed_safepoint()  { return false; }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  // Expansion modifies the JVMState, so we need to clone it
  virtual void  clone_jvms() {
    set_jvms(jvms()->clone_deep(Compile::current()));
  }
};

//------------------------------Unlock---------------------------------------
// High-level unlock operation
class UnlockNode : public AbstractLockNode {
public:
  virtual int Opcode() const;
  virtual uint size_of() const; // Size is bigger
  UnlockNode(Compile* C, const TypeFunc *tf) : AbstractLockNode( tf ) {
    init_class_id(Class_Unlock);
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  // unlock is never a safepoint
  virtual bool        guaranteed_safepoint()  { return false; }
};

#endif // SHARE_VM_OPTO_CALLNODE_HPP
