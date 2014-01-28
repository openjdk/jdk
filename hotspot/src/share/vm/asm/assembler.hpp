/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_ASM_ASSEMBLER_HPP
#define SHARE_VM_ASM_ASSEMBLER_HPP

#include "asm/codeBuffer.hpp"
#include "code/oopRecorder.hpp"
#include "code/relocInfo.hpp"
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/top.hpp"

#ifdef TARGET_ARCH_x86
# include "register_x86.hpp"
# include "vm_version_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "register_sparc.hpp"
# include "vm_version_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "register_zero.hpp"
# include "vm_version_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "register_arm.hpp"
# include "vm_version_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "register_ppc.hpp"
# include "vm_version_ppc.hpp"
#endif

// This file contains platform-independent assembler declarations.

class MacroAssembler;
class AbstractAssembler;
class Label;

/**
 * Labels represent destinations for control transfer instructions.  Such
 * instructions can accept a Label as their target argument.  A Label is
 * bound to the current location in the code stream by calling the
 * MacroAssembler's 'bind' method, which in turn calls the Label's 'bind'
 * method.  A Label may be referenced by an instruction before it's bound
 * (i.e., 'forward referenced').  'bind' stores the current code offset
 * in the Label object.
 *
 * If an instruction references a bound Label, the offset field(s) within
 * the instruction are immediately filled in based on the Label's code
 * offset.  If an instruction references an unbound label, that
 * instruction is put on a list of instructions that must be patched
 * (i.e., 'resolved') when the Label is bound.
 *
 * 'bind' will call the platform-specific 'patch_instruction' method to
 * fill in the offset field(s) for each unresolved instruction (if there
 * are any).  'patch_instruction' lives in one of the
 * cpu/<arch>/vm/assembler_<arch>* files.
 *
 * Instead of using a linked list of unresolved instructions, a Label has
 * an array of unresolved instruction code offsets.  _patch_index
 * contains the total number of forward references.  If the Label's array
 * overflows (i.e., _patch_index grows larger than the array size), a
 * GrowableArray is allocated to hold the remaining offsets.  (The cache
 * size is 4 for now, which handles over 99.5% of the cases)
 *
 * Labels may only be used within a single CodeSection.  If you need
 * to create references between code sections, use explicit relocations.
 */
class Label VALUE_OBJ_CLASS_SPEC {
 private:
  enum { PatchCacheSize = 4 };

  // _loc encodes both the binding state (via its sign)
  // and the binding locator (via its value) of a label.
  //
  // _loc >= 0   bound label, loc() encodes the target (jump) position
  // _loc == -1  unbound label
  int _loc;

  // References to instructions that jump to this unresolved label.
  // These instructions need to be patched when the label is bound
  // using the platform-specific patchInstruction() method.
  //
  // To avoid having to allocate from the C-heap each time, we provide
  // a local cache and use the overflow only if we exceed the local cache
  int _patches[PatchCacheSize];
  int _patch_index;
  GrowableArray<int>* _patch_overflow;

  Label(const Label&) { ShouldNotReachHere(); }

 public:

  /**
   * After binding, be sure 'patch_instructions' is called later to link
   */
  void bind_loc(int loc) {
    assert(loc >= 0, "illegal locator");
    assert(_loc == -1, "already bound");
    _loc = loc;
  }
  void bind_loc(int pos, int sect) { bind_loc(CodeBuffer::locator(pos, sect)); }

#ifndef PRODUCT
  // Iterates over all unresolved instructions for printing
  void print_instructions(MacroAssembler* masm) const;
#endif // PRODUCT

  /**
   * Returns the position of the the Label in the code buffer
   * The position is a 'locator', which encodes both offset and section.
   */
  int loc() const {
    assert(_loc >= 0, "unbound label");
    return _loc;
  }
  int loc_pos()  const { return CodeBuffer::locator_pos(loc()); }
  int loc_sect() const { return CodeBuffer::locator_sect(loc()); }

  bool is_bound() const    { return _loc >=  0; }
  bool is_unbound() const  { return _loc == -1 && _patch_index > 0; }
  bool is_unused() const   { return _loc == -1 && _patch_index == 0; }

  /**
   * Adds a reference to an unresolved displacement instruction to
   * this unbound label
   *
   * @param cb         the code buffer being patched
   * @param branch_loc the locator of the branch instruction in the code buffer
   */
  void add_patch_at(CodeBuffer* cb, int branch_loc);

  /**
   * Iterate over the list of patches, resolving the instructions
   * Call patch_instruction on each 'branch_loc' value
   */
  void patch_instructions(MacroAssembler* masm);

  void init() {
    _loc = -1;
    _patch_index = 0;
    _patch_overflow = NULL;
  }

  Label() {
    init();
  }
};

// A union type for code which has to assemble both constant and
// non-constant operands, when the distinction cannot be made
// statically.
class RegisterOrConstant VALUE_OBJ_CLASS_SPEC {
 private:
  Register _r;
  intptr_t _c;

 public:
  RegisterOrConstant(): _r(noreg), _c(0) {}
  RegisterOrConstant(Register r): _r(r), _c(0) {}
  RegisterOrConstant(intptr_t c): _r(noreg), _c(c) {}

  Register as_register() const { assert(is_register(),""); return _r; }
  intptr_t as_constant() const { assert(is_constant(),""); return _c; }

  Register register_or_noreg() const { return _r; }
  intptr_t constant_or_zero() const  { return _c; }

  bool is_register() const { return _r != noreg; }
  bool is_constant() const { return _r == noreg; }
};

// The Abstract Assembler: Pure assembler doing NO optimizations on the
// instruction level; i.e., what you write is what you get.
// The Assembler is generating code into a CodeBuffer.
class AbstractAssembler : public ResourceObj  {
  friend class Label;

 protected:
  CodeSection* _code_section;          // section within the code buffer
  OopRecorder* _oop_recorder;          // support for relocInfo::oop_type

 public:
  // Code emission & accessing
  address addr_at(int pos) const { return code_section()->start() + pos; }

 protected:
  // This routine is called with a label is used for an address.
  // Labels and displacements truck in offsets, but target must return a PC.
  address target(Label& L)             { return code_section()->target(L, pc()); }

  bool is8bit(int x) const             { return -0x80 <= x && x < 0x80; }
  bool isByte(int x) const             { return 0 <= x && x < 0x100; }
  bool isShiftCount(int x) const       { return 0 <= x && x < 32; }

  // Instruction boundaries (required when emitting relocatable values).
  class InstructionMark: public StackObj {
   private:
    AbstractAssembler* _assm;

   public:
    InstructionMark(AbstractAssembler* assm) : _assm(assm) {
      assert(assm->inst_mark() == NULL, "overlapping instructions");
      _assm->set_inst_mark();
    }
    ~InstructionMark() {
      _assm->clear_inst_mark();
    }
  };
  friend class InstructionMark;
#ifdef ASSERT
  // Make it return true on platforms which need to verify
  // instruction boundaries for some operations.
  static bool pd_check_instruction_mark();

  // Add delta to short branch distance to verify that it still fit into imm8.
  int _short_branch_delta;

  int  short_branch_delta() const { return _short_branch_delta; }
  void set_short_branch_delta()   { _short_branch_delta = 32; }
  void clear_short_branch_delta() { _short_branch_delta = 0; }

  class ShortBranchVerifier: public StackObj {
   private:
    AbstractAssembler* _assm;

   public:
    ShortBranchVerifier(AbstractAssembler* assm) : _assm(assm) {
      assert(assm->short_branch_delta() == 0, "overlapping instructions");
      _assm->set_short_branch_delta();
    }
    ~ShortBranchVerifier() {
      _assm->clear_short_branch_delta();
    }
  };
#else
  // Dummy in product.
  class ShortBranchVerifier: public StackObj {
   public:
    ShortBranchVerifier(AbstractAssembler* assm) {}
  };
#endif

 public:

  // Creation
  AbstractAssembler(CodeBuffer* code);

  // ensure buf contains all code (call this before using/copying the code)
  void flush();

  void emit_int8(   int8_t  x) { code_section()->emit_int8(   x); }
  void emit_int16(  int16_t x) { code_section()->emit_int16(  x); }
  void emit_int32(  int32_t x) { code_section()->emit_int32(  x); }
  void emit_int64(  int64_t x) { code_section()->emit_int64(  x); }

  void emit_float(  jfloat  x) { code_section()->emit_float(  x); }
  void emit_double( jdouble x) { code_section()->emit_double( x); }
  void emit_address(address x) { code_section()->emit_address(x); }

  // min and max values for signed immediate ranges
  static int min_simm(int nbits) { return -(intptr_t(1) << (nbits - 1))    ; }
  static int max_simm(int nbits) { return  (intptr_t(1) << (nbits - 1)) - 1; }

  // Define some:
  static int min_simm10() { return min_simm(10); }
  static int min_simm13() { return min_simm(13); }
  static int min_simm16() { return min_simm(16); }

  // Test if x is within signed immediate range for nbits
  static bool is_simm(intptr_t x, int nbits) { return min_simm(nbits) <= x && x <= max_simm(nbits); }

  // Define some:
  static bool is_simm5( intptr_t x) { return is_simm(x, 5 ); }
  static bool is_simm8( intptr_t x) { return is_simm(x, 8 ); }
  static bool is_simm10(intptr_t x) { return is_simm(x, 10); }
  static bool is_simm11(intptr_t x) { return is_simm(x, 11); }
  static bool is_simm12(intptr_t x) { return is_simm(x, 12); }
  static bool is_simm13(intptr_t x) { return is_simm(x, 13); }
  static bool is_simm16(intptr_t x) { return is_simm(x, 16); }
  static bool is_simm26(intptr_t x) { return is_simm(x, 26); }
  static bool is_simm32(intptr_t x) { return is_simm(x, 32); }

  // Accessors
  CodeSection*  code_section() const   { return _code_section; }
  CodeBuffer*   code()         const   { return code_section()->outer(); }
  int           sect()         const   { return code_section()->index(); }
  address       pc()           const   { return code_section()->end();   }
  int           offset()       const   { return code_section()->size();  }
  int           locator()      const   { return CodeBuffer::locator(offset(), sect()); }

  OopRecorder*  oop_recorder() const   { return _oop_recorder; }
  void      set_oop_recorder(OopRecorder* r) { _oop_recorder = r; }

  address       inst_mark() const { return code_section()->mark();       }
  void      set_inst_mark()       {        code_section()->set_mark();   }
  void    clear_inst_mark()       {        code_section()->clear_mark(); }

  // Constants in code
  void relocate(RelocationHolder const& rspec, int format = 0) {
    assert(!pd_check_instruction_mark()
        || inst_mark() == NULL || inst_mark() == code_section()->end(),
        "call relocate() between instructions");
    code_section()->relocate(code_section()->end(), rspec, format);
  }
  void relocate(   relocInfo::relocType rtype, int format = 0) {
    code_section()->relocate(code_section()->end(), rtype, format);
  }

  static int code_fill_byte();         // used to pad out odd-sized code buffers

  // Associate a comment with the current offset.  It will be printed
  // along with the disassembly when printing nmethods.  Currently
  // only supported in the instruction section of the code buffer.
  void block_comment(const char* comment);
  // Copy str to a buffer that has the same lifetime as the CodeBuffer
  const char* code_string(const char* str);

  // Label functions
  void bind(Label& L); // binds an unbound label L to the current code position

  // Move to a different section in the same code buffer.
  void set_code_section(CodeSection* cs);

  // Inform assembler when generating stub code and relocation info
  address    start_a_stub(int required_space);
  void       end_a_stub();
  // Ditto for constants.
  address    start_a_const(int required_space, int required_align = sizeof(double));
  void       end_a_const(CodeSection* cs);  // Pass the codesection to continue in (insts or stubs?).

  // constants support
  //
  // We must remember the code section (insts or stubs) in c1
  // so we can reset to the proper section in end_a_const().
  address long_constant(jlong c) {
    CodeSection* c1 = _code_section;
    address ptr = start_a_const(sizeof(c), sizeof(c));
    if (ptr != NULL) {
      emit_int64(c);
      end_a_const(c1);
    }
    return ptr;
  }
  address double_constant(jdouble c) {
    CodeSection* c1 = _code_section;
    address ptr = start_a_const(sizeof(c), sizeof(c));
    if (ptr != NULL) {
      emit_double(c);
      end_a_const(c1);
    }
    return ptr;
  }
  address float_constant(jfloat c) {
    CodeSection* c1 = _code_section;
    address ptr = start_a_const(sizeof(c), sizeof(c));
    if (ptr != NULL) {
      emit_float(c);
      end_a_const(c1);
    }
    return ptr;
  }
  address address_constant(address c) {
    CodeSection* c1 = _code_section;
    address ptr = start_a_const(sizeof(c), sizeof(c));
    if (ptr != NULL) {
      emit_address(c);
      end_a_const(c1);
    }
    return ptr;
  }
  address address_constant(address c, RelocationHolder const& rspec) {
    CodeSection* c1 = _code_section;
    address ptr = start_a_const(sizeof(c), sizeof(c));
    if (ptr != NULL) {
      relocate(rspec);
      emit_address(c);
      end_a_const(c1);
    }
    return ptr;
  }

  // Bootstrapping aid to cope with delayed determination of constants.
  // Returns a static address which will eventually contain the constant.
  // The value zero (NULL) stands instead of a constant which is still uncomputed.
  // Thus, the eventual value of the constant must not be zero.
  // This is fine, since this is designed for embedding object field
  // offsets in code which must be generated before the object class is loaded.
  // Field offsets are never zero, since an object's header (mark word)
  // is located at offset zero.
  RegisterOrConstant delayed_value(int(*value_fn)(), Register tmp, int offset = 0);
  RegisterOrConstant delayed_value(address(*value_fn)(), Register tmp, int offset = 0);
  virtual RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr, Register tmp, int offset) = 0;
  // Last overloading is platform-dependent; look in assembler_<arch>.cpp.
  static intptr_t* delayed_value_addr(int(*constant_fn)());
  static intptr_t* delayed_value_addr(address(*constant_fn)());
  static void update_delayed_values();

  // Bang stack to trigger StackOverflowError at a safe location
  // implementation delegates to machine-specific bang_stack_with_offset
  void generate_stack_overflow_check( int frame_size_in_bytes );
  virtual void bang_stack_with_offset(int offset) = 0;


  /**
   * A platform-dependent method to patch a jump instruction that refers
   * to this label.
   *
   * @param branch the location of the instruction to patch
   * @param masm the assembler which generated the branch
   */
  void pd_patch_instruction(address branch, address target);

};

#ifdef TARGET_ARCH_x86
# include "assembler_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "assembler_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "assembler_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "assembler_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "assembler_ppc.hpp"
#endif


#endif // SHARE_VM_ASM_ASSEMBLER_HPP
