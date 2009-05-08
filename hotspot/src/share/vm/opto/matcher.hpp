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

class Compile;
class Node;
class MachNode;
class MachTypeNode;
class MachOper;

//---------------------------Matcher-------------------------------------------
class Matcher : public PhaseTransform {
  friend class VMStructs;
  // Private arena of State objects
  ResourceArea _states_arena;

  VectorSet   _visited;         // Visit bits

  // Used to control the Label pass
  VectorSet   _shared;          // Shared Ideal Node
  VectorSet   _dontcare;        // Nothing the matcher cares about

  // Private methods which perform the actual matching and reduction
  // Walks the label tree, generating machine nodes
  MachNode *ReduceInst( State *s, int rule, Node *&mem);
  void ReduceInst_Chain_Rule( State *s, int rule, Node *&mem, MachNode *mach);
  uint ReduceInst_Interior(State *s, int rule, Node *&mem, MachNode *mach, uint num_opnds);
  void ReduceOper( State *s, int newrule, Node *&mem, MachNode *mach );

  // If this node already matched using "rule", return the MachNode for it.
  MachNode* find_shared_node(Node* n, uint rule);

  // Convert a dense opcode number to an expanded rule number
  const int *_reduceOp;
  const int *_leftOp;
  const int *_rightOp;

  // Map dense opcode number to info on when rule is swallowed constant.
  const bool *_swallowed;

  // Map dense rule number to determine if this is an instruction chain rule
  const uint _begin_inst_chain_rule;
  const uint _end_inst_chain_rule;

  // We want to clone constants and possible CmpI-variants.
  // If we do not clone CmpI, then we can have many instances of
  // condition codes alive at once.  This is OK on some chips and
  // bad on others.  Hence the machine-dependent table lookup.
  const char *_must_clone;

  // Find shared Nodes, or Nodes that otherwise are Matcher roots
  void find_shared( Node *n );

  // Debug and profile information for nodes in old space:
  GrowableArray<Node_Notes*>* _old_node_note_array;

  // Node labeling iterator for instruction selection
  Node *Label_Root( const Node *n, State *svec, Node *control, const Node *mem );

  Node *transform( Node *dummy );

  Node_List &_proj_list;        // For Machine nodes killing many values

  Node_Array _shared_nodes;

  debug_only(Node_Array _old2new_map;)   // Map roots of ideal-trees to machine-roots
  debug_only(Node_Array _new2old_map;)   // Maps machine nodes back to ideal

  // Accessors for the inherited field PhaseTransform::_nodes:
  void   grow_new_node_array(uint idx_limit) {
    _nodes.map(idx_limit-1, NULL);
  }
  bool    has_new_node(const Node* n) const {
    return _nodes.at(n->_idx) != NULL;
  }
  Node*       new_node(const Node* n) const {
    assert(has_new_node(n), "set before get");
    return _nodes.at(n->_idx);
  }
  void    set_new_node(const Node* n, Node *nn) {
    assert(!has_new_node(n), "set only once");
    _nodes.map(n->_idx, nn);
  }

#ifdef ASSERT
  // Make sure only new nodes are reachable from this node
  void verify_new_nodes_only(Node* root);

  Node* _mem_node;   // Ideal memory node consumed by mach node
#endif

  // Mach node for ConP #NULL
  MachNode* _mach_null;

public:
  int LabelRootDepth;
  static const int base2reg[];        // Map Types to machine register types
  // Convert ideal machine register to a register mask for spill-loads
  static const RegMask *idealreg2regmask[];
  RegMask *idealreg2spillmask[_last_machine_leaf];
  RegMask *idealreg2debugmask[_last_machine_leaf];
  void init_spill_mask( Node *ret );
  // Convert machine register number to register mask
  static uint mreg2regmask_max;
  static RegMask mreg2regmask[];
  static RegMask STACK_ONLY_mask;

  MachNode* mach_null() const { return _mach_null; }

  bool    is_shared( Node *n ) { return _shared.test(n->_idx) != 0; }
  void   set_shared( Node *n ) {  _shared.set(n->_idx); }
  bool   is_visited( Node *n ) { return _visited.test(n->_idx) != 0; }
  void  set_visited( Node *n ) { _visited.set(n->_idx); }
  bool  is_dontcare( Node *n ) { return _dontcare.test(n->_idx) != 0; }
  void set_dontcare( Node *n ) {  _dontcare.set(n->_idx); }

  // Mode bit to tell DFA and expand rules whether we are running after
  // (or during) register selection.  Usually, the matcher runs before,
  // but it will also get called to generate post-allocation spill code.
  // In this situation, it is a deadly error to attempt to allocate more
  // temporary registers.
  bool _allocation_started;

  // Machine register names
  static const char *regName[];
  // Machine register encodings
  static const unsigned char _regEncode[];
  // Machine Node names
  const char **_ruleName;
  // Rules that are cheaper to rematerialize than to spill
  static const uint _begin_rematerialize;
  static const uint _end_rematerialize;

  // An array of chars, from 0 to _last_Mach_Reg.
  // No Save       = 'N' (for register windows)
  // Save on Entry = 'E'
  // Save on Call  = 'C'
  // Always Save   = 'A' (same as SOE + SOC)
  const char *_register_save_policy;
  const char *_c_reg_save_policy;
  // Convert a machine register to a machine register type, so-as to
  // properly match spill code.
  const int *_register_save_type;
  // Maps from machine register to boolean; true if machine register can
  // be holding a call argument in some signature.
  static bool can_be_java_arg( int reg );
  // Maps from machine register to boolean; true if machine register holds
  // a spillable argument.
  static bool is_spillable_arg( int reg );

  // List of IfFalse or IfTrue Nodes that indicate a taken null test.
  // List is valid in the post-matching space.
  Node_List _null_check_tests;
  void collect_null_checks( Node *proj, Node *orig_proj );
  void validate_null_checks( );

  Matcher( Node_List &proj_list );

  // Select instructions for entire method
  void  match( );
  // Helper for match
  OptoReg::Name warp_incoming_stk_arg( VMReg reg );

  // Transform, then walk.  Does implicit DCE while walking.
  // Name changed from "transform" to avoid it being virtual.
  Node *xform( Node *old_space_node, int Nodes );

  // Match a single Ideal Node - turn it into a 1-Node tree; Label & Reduce.
  MachNode *match_tree( const Node *n );
  MachNode *match_sfpt( SafePointNode *sfpt );
  // Helper for match_sfpt
  OptoReg::Name warp_outgoing_stk_arg( VMReg reg, OptoReg::Name begin_out_arg_area, OptoReg::Name &out_arg_limit_per_call );

  // Initialize first stack mask and related masks.
  void init_first_stack_mask();

  // If we should save-on-entry this register
  bool is_save_on_entry( int reg );

  // Fixup the save-on-entry registers
  void Fixup_Save_On_Entry( );

  // --- Frame handling ---

  // Register number of the stack slot corresponding to the incoming SP.
  // Per the Big Picture in the AD file, it is:
  //   SharedInfo::stack0 + locks + in_preserve_stack_slots + pad2.
  OptoReg::Name _old_SP;

  // Register number of the stack slot corresponding to the highest incoming
  // argument on the stack.  Per the Big Picture in the AD file, it is:
  //   _old_SP + out_preserve_stack_slots + incoming argument size.
  OptoReg::Name _in_arg_limit;

  // Register number of the stack slot corresponding to the new SP.
  // Per the Big Picture in the AD file, it is:
  //   _in_arg_limit + pad0
  OptoReg::Name _new_SP;

  // Register number of the stack slot corresponding to the highest outgoing
  // argument on the stack.  Per the Big Picture in the AD file, it is:
  //   _new_SP + max outgoing arguments of all calls
  OptoReg::Name _out_arg_limit;

  OptoRegPair *_parm_regs;        // Array of machine registers per argument
  RegMask *_calling_convention_mask; // Array of RegMasks per argument

  // Does matcher have a match rule for this ideal node?
  static const bool has_match_rule(int opcode);
  static const bool _hasMatchRule[_last_opcode];

  // Does matcher have a match rule for this ideal node and is the
  // predicate (if there is one) true?
  // NOTE: If this function is used more commonly in the future, ADLC
  // should generate this one.
  static const bool match_rule_supported(int opcode);

  // Used to determine if we have fast l2f conversion
  // USII has it, USIII doesn't
  static const bool convL2FSupported(void);

  // Vector width in bytes
  static const uint vector_width_in_bytes(void);

  // Vector ideal reg
  static const uint vector_ideal_reg(void);

  // Used to determine a "low complexity" 64-bit constant.  (Zero is simple.)
  // The standard of comparison is one (StoreL ConL) vs. two (StoreI ConI).
  // Depends on the details of 64-bit constant generation on the CPU.
  static const bool isSimpleConstant64(jlong con);

  // These calls are all generated by the ADLC

  // TRUE - grows up, FALSE - grows down (Intel)
  virtual bool stack_direction() const;

  // Java-Java calling convention
  // (what you use when Java calls Java)

  // Alignment of stack in bytes, standard Intel word alignment is 4.
  // Sparc probably wants at least double-word (8).
  static uint stack_alignment_in_bytes();
  // Alignment of stack, measured in stack slots.
  // The size of stack slots is defined by VMRegImpl::stack_slot_size.
  static uint stack_alignment_in_slots() {
    return stack_alignment_in_bytes() / (VMRegImpl::stack_slot_size);
  }

  // Array mapping arguments to registers.  Argument 0 is usually the 'this'
  // pointer.  Registers can include stack-slots and regular registers.
  static void calling_convention( BasicType *, VMRegPair *, uint len, bool is_outgoing );

  // Convert a sig into a calling convention register layout
  // and find interesting things about it.
  static OptoReg::Name  find_receiver( bool is_outgoing );
  // Return address register.  On Intel it is a stack-slot.  On PowerPC
  // it is the Link register.  On Sparc it is r31?
  virtual OptoReg::Name return_addr() const;
  RegMask              _return_addr_mask;
  // Return value register.  On Intel it is EAX.  On Sparc i0/o0.
  static OptoRegPair   return_value(int ideal_reg, bool is_outgoing);
  static OptoRegPair c_return_value(int ideal_reg, bool is_outgoing);
  RegMask                     _return_value_mask;
  // Inline Cache Register
  static OptoReg::Name  inline_cache_reg();
  static const RegMask &inline_cache_reg_mask();
  static int            inline_cache_reg_encode();

  // Register for DIVI projection of divmodI
  static RegMask divI_proj_mask();
  // Register for MODI projection of divmodI
  static RegMask modI_proj_mask();

  // Register for DIVL projection of divmodL
  static RegMask divL_proj_mask();
  // Register for MODL projection of divmodL
  static RegMask modL_proj_mask();

  // Java-Interpreter calling convention
  // (what you use when calling between compiled-Java and Interpreted-Java

  // Number of callee-save + always-save registers
  // Ignores frame pointer and "special" registers
  static int  number_of_saved_registers();

  // The Method-klass-holder may be passed in the inline_cache_reg
  // and then expanded into the inline_cache_reg and a method_oop register

  static OptoReg::Name  interpreter_method_oop_reg();
  static const RegMask &interpreter_method_oop_reg_mask();
  static int            interpreter_method_oop_reg_encode();

  static OptoReg::Name  compiler_method_oop_reg();
  static const RegMask &compiler_method_oop_reg_mask();
  static int            compiler_method_oop_reg_encode();

  // Interpreter's Frame Pointer Register
  static OptoReg::Name  interpreter_frame_pointer_reg();
  static const RegMask &interpreter_frame_pointer_reg_mask();

  // Java-Native calling convention
  // (what you use when intercalling between Java and C++ code)

  // Array mapping arguments to registers.  Argument 0 is usually the 'this'
  // pointer.  Registers can include stack-slots and regular registers.
  static void c_calling_convention( BasicType*, VMRegPair *, uint );
  // Frame pointer. The frame pointer is kept at the base of the stack
  // and so is probably the stack pointer for most machines.  On Intel
  // it is ESP.  On the PowerPC it is R1.  On Sparc it is SP.
  OptoReg::Name  c_frame_pointer() const;
  static RegMask c_frame_ptr_mask;

  // !!!!! Special stuff for building ScopeDescs
  virtual int      regnum_to_fpu_offset(int regnum);

  // Is this branch offset small enough to be addressed by a short branch?
  bool is_short_branch_offset(int rule, int offset);

  // Optional scaling for the parameter to the ClearArray/CopyArray node.
  static const bool init_array_count_is_in_bytes;

  // Threshold small size (in bytes) for a ClearArray/CopyArray node.
  // Anything this size or smaller may get converted to discrete scalar stores.
  static const int init_array_short_size;

  // Should the Matcher clone shifts on addressing modes, expecting them to
  // be subsumed into complex addressing expressions or compute them into
  // registers?  True for Intel but false for most RISCs
  static const bool clone_shift_expressions;

  // Is it better to copy float constants, or load them directly from memory?
  // Intel can load a float constant from a direct address, requiring no
  // extra registers.  Most RISCs will have to materialize an address into a
  // register first, so they may as well materialize the constant immediately.
  static const bool rematerialize_float_constants;

  // If CPU can load and store mis-aligned doubles directly then no fixup is
  // needed.  Else we split the double into 2 integer pieces and move it
  // piece-by-piece.  Only happens when passing doubles into C code or when
  // calling i2c adapters as the Java calling convention forces doubles to be
  // aligned.
  static const bool misaligned_doubles_ok;

  // Perform a platform dependent implicit null fixup.  This is needed
  // on windows95 to take care of some unusual register constraints.
  void pd_implicit_null_fixup(MachNode *load, uint idx);

  // Advertise here if the CPU requires explicit rounding operations
  // to implement the UseStrictFP mode.
  static const bool strict_fp_requires_explicit_rounding;

  // Do floats take an entire double register or just half?
  static const bool float_in_double;
  // Do ints take an entire long register or just half?
  static const bool int_in_long;

  // This routine is run whenever a graph fails to match.
  // If it returns, the compiler should bailout to interpreter without error.
  // In non-product mode, SoftMatchFailure is false to detect non-canonical
  // graphs.  Print a message and exit.
  static void soft_match_failure() {
    if( SoftMatchFailure ) return;
    else { fatal("SoftMatchFailure is not allowed except in product"); }
  }

  // Used by the DFA in dfa_sparc.cpp.  Check for a prior FastLock
  // acting as an Acquire and thus we don't need an Acquire here.  We
  // retain the Node to act as a compiler ordering barrier.
  static bool prior_fast_lock( const Node *acq );

  // Used by the DFA in dfa_sparc.cpp.  Check for a following
  // FastUnLock acting as a Release and thus we don't need a Release
  // here.  We retain the Node to act as a compiler ordering barrier.
  static bool post_fast_unlock( const Node *rel );

  // Check for a following volatile memory barrier without an
  // intervening load and thus we don't need a barrier here.  We
  // retain the Node to act as a compiler ordering barrier.
  static bool post_store_load_barrier(const Node* mb);


#ifdef ASSERT
  void dump_old2new_map();      // machine-independent to machine-dependent

  Node* find_old_node(Node* new_node) {
    return _new2old_map[new_node->_idx];
  }
#endif
};
