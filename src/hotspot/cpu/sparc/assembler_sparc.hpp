/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_ASSEMBLER_SPARC_HPP
#define CPU_SPARC_VM_ASSEMBLER_SPARC_HPP

#include "asm/register.hpp"

// The SPARC Assembler: Pure assembler doing NO optimizations on the instruction
// level; i.e., what you write is what you get. The Assembler is generating code
// into a CodeBuffer.

class Assembler : public AbstractAssembler {
  friend class AbstractAssembler;
  friend class AddressLiteral;

  // code patchers need various routines like inv_wdisp()
  friend class NativeInstruction;
  friend class NativeGeneralJump;
  friend class Relocation;
  friend class Label;

 public:
  // op carries format info; see page 62 & 267

  enum ops {
    call_op   = 1, // fmt 1
    branch_op = 0, // also sethi (fmt2)
    arith_op  = 2, // fmt 3, arith & misc
    ldst_op   = 3  // fmt 3, load/store
  };

  enum op2s {
    bpr_op2   = 3,
    fb_op2    = 6,
    fbp_op2   = 5,
    br_op2    = 2,
    bp_op2    = 1,
    sethi_op2 = 4
  };

  enum op3s {
    // selected op3s
    add_op3      = 0x00,
    and_op3      = 0x01,
    or_op3       = 0x02,
    xor_op3      = 0x03,
    sub_op3      = 0x04,
    andn_op3     = 0x05,
    orn_op3      = 0x06,
    xnor_op3     = 0x07,
    addc_op3     = 0x08,
    mulx_op3     = 0x09,
    umul_op3     = 0x0a,
    smul_op3     = 0x0b,
    subc_op3     = 0x0c,
    udivx_op3    = 0x0d,
    udiv_op3     = 0x0e,
    sdiv_op3     = 0x0f,

    addcc_op3    = 0x10,
    andcc_op3    = 0x11,
    orcc_op3     = 0x12,
    xorcc_op3    = 0x13,
    subcc_op3    = 0x14,
    andncc_op3   = 0x15,
    orncc_op3    = 0x16,
    xnorcc_op3   = 0x17,
    addccc_op3   = 0x18,
    aes4_op3     = 0x19,
    umulcc_op3   = 0x1a,
    smulcc_op3   = 0x1b,
    subccc_op3   = 0x1c,
    udivcc_op3   = 0x1e,
    sdivcc_op3   = 0x1f,

    taddcc_op3   = 0x20,
    tsubcc_op3   = 0x21,
    taddcctv_op3 = 0x22,
    tsubcctv_op3 = 0x23,
    mulscc_op3   = 0x24,
    sll_op3      = 0x25,
    sllx_op3     = 0x25,
    srl_op3      = 0x26,
    srlx_op3     = 0x26,
    sra_op3      = 0x27,
    srax_op3     = 0x27,
    rdreg_op3    = 0x28,
    membar_op3   = 0x28,

    flushw_op3   = 0x2b,
    movcc_op3    = 0x2c,
    sdivx_op3    = 0x2d,
    popc_op3     = 0x2e,
    movr_op3     = 0x2f,

    sir_op3      = 0x30,
    wrreg_op3    = 0x30,
    saved_op3    = 0x31,

    fpop1_op3    = 0x34,
    fpop2_op3    = 0x35,
    impdep1_op3  = 0x36,
    addx_op3     = 0x36,
    aes3_op3     = 0x36,
    sha_op3      = 0x36,
    bmask_op3    = 0x36,
    bshuffle_op3   = 0x36,
    alignaddr_op3  = 0x36,
    faligndata_op3 = 0x36,
    flog3_op3    = 0x36,
    edge_op3     = 0x36,
    fzero_op3    = 0x36,
    fsrc_op3     = 0x36,
    fnot_op3     = 0x36,
    mpmul_op3    = 0x36,
    umulx_op3    = 0x36,
    xmulx_op3    = 0x36,
    crc32c_op3   = 0x36,
    impdep2_op3  = 0x37,
    stpartialf_op3 = 0x37,
    jmpl_op3     = 0x38,
    rett_op3     = 0x39,
    trap_op3     = 0x3a,
    flush_op3    = 0x3b,
    save_op3     = 0x3c,
    restore_op3  = 0x3d,
    done_op3     = 0x3e,
    retry_op3    = 0x3e,

    lduw_op3     = 0x00,
    ldub_op3     = 0x01,
    lduh_op3     = 0x02,
    ldd_op3      = 0x03,
    stw_op3      = 0x04,
    stb_op3      = 0x05,
    sth_op3      = 0x06,
    std_op3      = 0x07,
    ldsw_op3     = 0x08,
    ldsb_op3     = 0x09,
    ldsh_op3     = 0x0a,
    ldx_op3      = 0x0b,

    stx_op3      = 0x0e,
    swap_op3     = 0x0f,

    stwa_op3     = 0x14,
    stxa_op3     = 0x1e,

    ldf_op3      = 0x20,
    ldfsr_op3    = 0x21,
    ldqf_op3     = 0x22,
    lddf_op3     = 0x23,
    stf_op3      = 0x24,
    stfsr_op3    = 0x25,
    stqf_op3     = 0x26,
    stdf_op3     = 0x27,

    prefetch_op3 = 0x2d,

    casa_op3     = 0x3c,
    casxa_op3    = 0x3e,

    mftoi_op3    = 0x36,

    alt_bit_op3  = 0x10,
     cc_bit_op3  = 0x10
  };

  enum opfs {
    // selected opfs
    edge8n_opf         = 0x01,

    fmovs_opf          = 0x01,
    fmovd_opf          = 0x02,

    fnegs_opf          = 0x05,
    fnegd_opf          = 0x06,

    addxc_opf          = 0x11,
    addxccc_opf        = 0x13,
    umulxhi_opf        = 0x16,
    alignaddr_opf      = 0x18,
    bmask_opf          = 0x19,

    fadds_opf          = 0x41,
    faddd_opf          = 0x42,
    fsubs_opf          = 0x45,
    fsubd_opf          = 0x46,

    faligndata_opf     = 0x48,

    fmuls_opf          = 0x49,
    fmuld_opf          = 0x4a,
    bshuffle_opf       = 0x4c,
    fdivs_opf          = 0x4d,
    fdivd_opf          = 0x4e,

    fcmps_opf          = 0x51,
    fcmpd_opf          = 0x52,

    fstox_opf          = 0x81,
    fdtox_opf          = 0x82,
    fxtos_opf          = 0x84,
    fxtod_opf          = 0x88,
    fitos_opf          = 0xc4,
    fdtos_opf          = 0xc6,
    fitod_opf          = 0xc8,
    fstod_opf          = 0xc9,
    fstoi_opf          = 0xd1,
    fdtoi_opf          = 0xd2,

    mdtox_opf          = 0x110,
    mstouw_opf         = 0x111,
    mstosw_opf         = 0x113,
    xmulx_opf          = 0x115,
    xmulxhi_opf        = 0x116,
    mxtod_opf          = 0x118,
    mwtos_opf          = 0x119,

    aes_kexpand0_opf   = 0x130,
    aes_kexpand2_opf   = 0x131,

    sha1_opf           = 0x141,
    sha256_opf         = 0x142,
    sha512_opf         = 0x143,

    crc32c_opf         = 0x147,
    mpmul_opf          = 0x148
  };

  enum op5s {
    aes_eround01_op5   = 0x00,
    aes_eround23_op5   = 0x01,
    aes_dround01_op5   = 0x02,
    aes_dround23_op5   = 0x03,
    aes_eround01_l_op5 = 0x04,
    aes_eround23_l_op5 = 0x05,
    aes_dround01_l_op5 = 0x06,
    aes_dround23_l_op5 = 0x07,
    aes_kexpand1_op5   = 0x08
  };

  enum RCondition { rc_z = 1, rc_lez = 2, rc_lz = 3, rc_nz = 5, rc_gz = 6, rc_gez = 7, rc_last = rc_gez };

  enum Condition {
     // for FBfcc & FBPfcc instruction
    f_never                     = 0,
    f_notEqual                  = 1,
    f_notZero                   = 1,
    f_lessOrGreater             = 2,
    f_unorderedOrLess           = 3,
    f_less                      = 4,
    f_unorderedOrGreater        = 5,
    f_greater                   = 6,
    f_unordered                 = 7,
    f_always                    = 8,
    f_equal                     = 9,
    f_zero                      = 9,
    f_unorderedOrEqual          = 10,
    f_greaterOrEqual            = 11,
    f_unorderedOrGreaterOrEqual = 12,
    f_lessOrEqual               = 13,
    f_unorderedOrLessOrEqual    = 14,
    f_ordered                   = 15,

    // for integers

    never                = 0,
    equal                = 1,
    zero                 = 1,
    lessEqual            = 2,
    less                 = 3,
    lessEqualUnsigned    = 4,
    lessUnsigned         = 5,
    carrySet             = 5,
    negative             = 6,
    overflowSet          = 7,
    always               = 8,
    notEqual             = 9,
    notZero              = 9,
    greater              = 10,
    greaterEqual         = 11,
    greaterUnsigned      = 12,
    greaterEqualUnsigned = 13,
    carryClear           = 13,
    positive             = 14,
    overflowClear        = 15
  };

  enum CC {
    // ptr_cc is the correct condition code for a pointer or intptr_t:
    icc  = 0, xcc  = 2, ptr_cc = xcc,
    fcc0 = 0, fcc1 = 1, fcc2 = 2, fcc3 = 3
  };

  enum PrefetchFcn {
    severalReads = 0, oneRead = 1, severalWritesAndPossiblyReads = 2, oneWrite = 3, page = 4
  };

 public:
  // Helper functions for groups of instructions

  enum Predict { pt = 1, pn = 0 }; // pt = predict taken

  enum Membar_mask_bits { // page 184, v9
    StoreStore = 1 << 3,
    LoadStore  = 1 << 2,
    StoreLoad  = 1 << 1,
    LoadLoad   = 1 << 0,

    Sync       = 1 << 6,
    MemIssue   = 1 << 5,
    Lookaside  = 1 << 4
  };

  static bool is_in_wdisp_range(address a, address b, int nbits) {
    intptr_t d = intptr_t(b) - intptr_t(a);
    return is_simm(d, nbits + 2);
  }

  address target_distance(Label &L) {
    // Assembler::target(L) should be called only when
    // a branch instruction is emitted since non-bound
    // labels record current pc() as a branch address.
    if (L.is_bound()) return target(L);
    // Return current address for non-bound labels.
    return pc();
  }

  // test if label is in simm16 range in words (wdisp16).
  bool is_in_wdisp16_range(Label &L) {
    return is_in_wdisp_range(target_distance(L), pc(), 16);
  }
  // test if the distance between two addresses fits in simm30 range in words
  static bool is_in_wdisp30_range(address a, address b) {
    return is_in_wdisp_range(a, b, 30);
  }

  enum ASIs { // page 72, v9
    ASI_PRIMARY            = 0x80,
    ASI_PRIMARY_NOFAULT    = 0x82,
    ASI_PRIMARY_LITTLE     = 0x88,
    // 8x8-bit partial store
    ASI_PST8_PRIMARY       = 0xC0,
    // Block initializing store
    ASI_ST_BLKINIT_PRIMARY = 0xE2,
    // Most-Recently-Used (MRU) BIS variant
    ASI_ST_BLKINIT_MRU_PRIMARY = 0xF2
    // add more from book as needed
  };

 protected:
  // helpers

  // x is supposed to fit in a field "nbits" wide
  // and be sign-extended. Check the range.

  static void assert_signed_range(intptr_t x, int nbits) {
    assert(nbits == 32 || (-(1 << nbits-1) <= x && x < (1 << nbits-1)),
           "value out of range: x=" INTPTR_FORMAT ", nbits=%d", x, nbits);
  }

  static void assert_signed_word_disp_range(intptr_t x, int nbits) {
    assert((x & 3) == 0, "not word aligned");
    assert_signed_range(x, nbits + 2);
  }

  static void assert_unsigned_range(int x, int nbits) {
    assert(juint(x) < juint(1 << nbits), "unsigned constant out of range");
  }

  // fields: note bits numbered from LSB = 0, fields known by inclusive bit range

  static int fmask(juint hi_bit, juint lo_bit) {
    assert(hi_bit >= lo_bit && 0 <= lo_bit && hi_bit < 32, "bad bits");
    return (1 << (hi_bit-lo_bit + 1)) - 1;
  }

  // inverse of u_field

  static int inv_u_field(int x, int hi_bit, int lo_bit) {
    juint r = juint(x) >> lo_bit;
    r &= fmask(hi_bit, lo_bit);
    return int(r);
  }

  // signed version: extract from field and sign-extend

  static int inv_s_field(int x, int hi_bit, int lo_bit) {
    int sign_shift = 31 - hi_bit;
    return inv_u_field(((x << sign_shift) >> sign_shift), hi_bit, lo_bit);
  }

  // given a field that ranges from hi_bit to lo_bit (inclusive,
  // LSB = 0), and an unsigned value for the field,
  // shift it into the field

#ifdef ASSERT
  static int u_field(int x, int hi_bit, int lo_bit) {
    assert((x & ~fmask(hi_bit, lo_bit)) == 0,
            "value out of range");
    int r = x << lo_bit;
    assert(inv_u_field(r, hi_bit, lo_bit) == x, "just checking");
    return r;
  }
#else
  // make sure this is inlined as it will reduce code size significantly
  #define u_field(x, hi_bit, lo_bit) ((x) << (lo_bit))
#endif

  static int inv_op(int x)   { return inv_u_field(x, 31, 30); }
  static int inv_op2(int x)  { return inv_u_field(x, 24, 22); }
  static int inv_op3(int x)  { return inv_u_field(x, 24, 19); }
  static int inv_cond(int x) { return inv_u_field(x, 28, 25); }

  static bool inv_immed(int x)   { return (x & Assembler::immed(true)) != 0; }

  static Register inv_rd(int x)  { return as_Register(inv_u_field(x, 29, 25)); }
  static Register inv_rs1(int x) { return as_Register(inv_u_field(x, 18, 14)); }
  static Register inv_rs2(int x) { return as_Register(inv_u_field(x,  4,  0)); }

  static int op(int x)           { return u_field(x,             31, 30); }
  static int rd(Register r)      { return u_field(r->encoding(), 29, 25); }
  static int fcn(int x)          { return u_field(x,             29, 25); }
  static int op3(int x)          { return u_field(x,             24, 19); }
  static int rs1(Register r)     { return u_field(r->encoding(), 18, 14); }
  static int rs2(Register r)     { return u_field(r->encoding(),  4,  0); }
  static int annul(bool a)       { return u_field(a ? 1 : 0,     29, 29); }
  static int cond(int x)         { return u_field(x,             28, 25); }
  static int cond_mov(int x)     { return u_field(x,             17, 14); }
  static int rcond(RCondition x) { return u_field(x,             12, 10); }
  static int op2(int x)          { return u_field(x,             24, 22); }
  static int predict(bool p)     { return u_field(p ? 1 : 0,     19, 19); }
  static int branchcc(CC fcca)   { return u_field(fcca,          21, 20); }
  static int cmpcc(CC fcca)      { return u_field(fcca,          26, 25); }
  static int imm_asi(int x)      { return u_field(x,             12,  5); }
  static int immed(bool i)       { return u_field(i ? 1 : 0,     13, 13); }
  static int opf_low6(int w)     { return u_field(w,             10,  5); }
  static int opf_low5(int w)     { return u_field(w,              9,  5); }
  static int op5(int x)          { return u_field(x,              8,  5); }
  static int trapcc(CC cc)       { return u_field(cc,            12, 11); }
  static int sx(int i)           { return u_field(i,             12, 12); } // shift x=1 means 64-bit
  static int opf(int x)          { return u_field(x,             13,  5); }

  static bool is_cbcond(int x) {
    return (VM_Version::has_cbcond() && (inv_cond(x) > rc_last) &&
            inv_op(x) == branch_op && inv_op2(x) == bpr_op2);
  }
  static bool is_cxb(int x) {
    assert(is_cbcond(x), "wrong instruction");
    return (x & (1 << 21)) != 0;
  }
  static bool is_branch(int x) {
    if (inv_op(x) != Assembler::branch_op) return false;

    bool is_bpr = inv_op2(x) == Assembler::bpr_op2;
    bool is_bp  = inv_op2(x) == Assembler::bp_op2;
    bool is_br  = inv_op2(x) == Assembler::br_op2;
    bool is_fp  = inv_op2(x) == Assembler::fb_op2;
    bool is_fbp = inv_op2(x) == Assembler::fbp_op2;

    return is_bpr || is_bp || is_br || is_fp || is_fbp;
  }
  static bool is_call(int x) {
    return inv_op(x) == Assembler::call_op;
  }
  static bool is_jump(int x) {
    if (inv_op(x) != Assembler::arith_op) return false;

    bool is_jmpl = inv_op3(x) == Assembler::jmpl_op3;
    bool is_rett = inv_op3(x) == Assembler::rett_op3;

    return is_jmpl || is_rett;
  }
  static bool is_rdpc(int x) {
    return (inv_op(x) == Assembler::arith_op && inv_op3(x) == Assembler::rdreg_op3 &&
            inv_u_field(x, 18, 14) == 5);
  }
  static bool is_cti(int x) {
      return is_branch(x) || is_call(x) || is_jump(x); // Ignoring done/retry
  }

  static int cond_cbcond(int x) { return  u_field((((x & 8) << 1) + 8 + (x & 7)), 29, 25); }
  static int inv_cond_cbcond(int x) {
    assert(is_cbcond(x), "wrong instruction");
    return inv_u_field(x, 27, 25) | (inv_u_field(x, 29, 29) << 3);
  }

  static int opf_cc(CC c, bool useFloat) { return u_field((useFloat ? 0 : 4) + c, 13, 11); }
  static int mov_cc(CC c, bool useFloat) { return u_field(useFloat ? 0 : 1, 18, 18) | u_field(c, 12, 11); }

  static int fd(FloatRegister r, FloatRegisterImpl::Width fwa)  { return u_field(r->encoding(fwa), 29, 25); };
  static int fs1(FloatRegister r, FloatRegisterImpl::Width fwa) { return u_field(r->encoding(fwa), 18, 14); };
  static int fs2(FloatRegister r, FloatRegisterImpl::Width fwa) { return u_field(r->encoding(fwa),  4,  0); };
  static int fs3(FloatRegister r, FloatRegisterImpl::Width fwa) { return u_field(r->encoding(fwa), 13,  9); };

  // some float instructions use this encoding on the op3 field
  static int alt_op3(int op, FloatRegisterImpl::Width w) {
    int r;
    switch(w) {
     case FloatRegisterImpl::S: r = op + 0;  break;
     case FloatRegisterImpl::D: r = op + 3;  break;
     case FloatRegisterImpl::Q: r = op + 2;  break;
     default: ShouldNotReachHere(); break;
    }
    return op3(r);
  }

  // compute inverse of simm
  static int inv_simm(int x, int nbits) {
    return (int)(x << (32 - nbits)) >> (32 - nbits);
  }

  static int inv_simm13(int x) { return inv_simm(x, 13); }

  // signed immediate, in low bits, nbits long
  static int simm(int x, int nbits) {
    assert_signed_range(x, nbits);
    return x & ((1 << nbits) - 1);
  }

  // unsigned immediate, in low bits, at most nbits long.
  static int uimm(int x, int nbits) {
    assert_unsigned_range(x, nbits);
    return x & ((1 << nbits) - 1);
  }

  // compute inverse of wdisp16
  static intptr_t inv_wdisp16(int x, intptr_t pos) {
    int lo = x & ((1 << 14) - 1);
    int hi = (x >> 20) & 3;
    if (hi >= 2) hi |= ~1;
    return (((hi << 14) | lo) << 2) + pos;
  }

  // word offset, 14 bits at LSend, 2 bits at B21, B20
  static int wdisp16(intptr_t x, intptr_t off) {
    intptr_t xx = x - off;
    assert_signed_word_disp_range(xx, 16);
    int r = (xx >> 2) & ((1 << 14) - 1) | (((xx >> (2+14)) & 3) << 20);
    assert(inv_wdisp16(r, off) == x, "inverse is not inverse");
    return r;
  }

  // compute inverse of wdisp10
  static intptr_t inv_wdisp10(int x, intptr_t pos) {
    assert(is_cbcond(x), "wrong instruction");
    int lo = inv_u_field(x, 12, 5);
    int hi = (x >> 19) & 3;
    if (hi >= 2) hi |= ~1;
    return (((hi << 8) | lo) << 2) + pos;
  }

  // word offset for cbcond, 8 bits at [B12,B5], 2 bits at [B20,B19]
  static int wdisp10(intptr_t x, intptr_t off) {
    assert(VM_Version::has_cbcond(), "This CPU does not have CBCOND instruction");
    intptr_t xx = x - off;
    assert_signed_word_disp_range(xx, 10);
    int r = (((xx >> 2) & ((1 << 8) - 1)) << 5) | (((xx >> (2+8)) & 3) << 19);
    // Have to fake cbcond instruction to pass assert in inv_wdisp10()
    assert(inv_wdisp10((r | op(branch_op) | cond_cbcond(rc_last+1) | op2(bpr_op2)), off) == x, "inverse is not inverse");
    return r;
  }

  // word displacement in low-order nbits bits

  static intptr_t inv_wdisp(int x, intptr_t pos, int nbits) {
    int pre_sign_extend = x & ((1 << nbits) - 1);
    int r = (pre_sign_extend >= (1 << (nbits - 1)) ?
             pre_sign_extend | ~((1 << nbits) - 1) : pre_sign_extend);
    return (r << 2) + pos;
  }

  static int wdisp(intptr_t x, intptr_t off, int nbits) {
    intptr_t xx = x - off;
    assert_signed_word_disp_range(xx, nbits);
    int r = (xx >> 2) & ((1 << nbits) - 1);
    assert(inv_wdisp(r, off, nbits) == x, "inverse not inverse");
    return r;
  }


  // Extract the top 32 bits in a 64 bit word
  static int32_t hi32(int64_t x) {
    int32_t r = int32_t((uint64_t)x >> 32);
    return r;
  }

  // given a sethi instruction, extract the constant, left-justified
  static int inv_hi22(int x) {
    return x << 10;
  }

  // create an imm22 field, given a 32-bit left-justified constant
  static int hi22(int x) {
    int r = int(juint(x) >> 10);
    assert((r & ~((1 << 22) - 1)) == 0, "just checkin'");
    return r;
  }

  // create a low10 __value__ (not a field) for a given a 32-bit constant
  static int low10(int x) {
    return x & ((1 << 10) - 1);
  }

  // create a low12 __value__ (not a field) for a given a 32-bit constant
  static int low12(int x) {
    return x & ((1 << 12) - 1);
  }

  // AES crypto instructions supported only on certain processors
  static void aes_only() { assert(VM_Version::has_aes(), "This instruction only works on SPARC with AES instructions support"); }

  // SHA crypto instructions supported only on certain processors
  static void sha1_only()   { assert(VM_Version::has_sha1(),   "This instruction only works on SPARC with SHA1"); }
  static void sha256_only() { assert(VM_Version::has_sha256(), "This instruction only works on SPARC with SHA256"); }
  static void sha512_only() { assert(VM_Version::has_sha512(), "This instruction only works on SPARC with SHA512"); }

  // CRC32C instruction supported only on certain processors
  static void crc32c_only() { assert(VM_Version::has_crc32c(), "This instruction only works on SPARC with CRC32C"); }

  // FMAf instructions supported only on certain processors
  static void fmaf_only() { assert(VM_Version::has_fmaf(), "This instruction only works on SPARC with FMAf"); }

  // MPMUL instruction supported only on certain processors
  static void mpmul_only() { assert(VM_Version::has_mpmul(), "This instruction only works on SPARC with MPMUL"); }

  // instruction only in VIS1
  static void vis1_only() { assert(VM_Version::has_vis1(), "This instruction only works on SPARC with VIS1"); }

  // instruction only in VIS2
  static void vis2_only() { assert(VM_Version::has_vis2(), "This instruction only works on SPARC with VIS2"); }

  // instruction only in VIS3
  static void vis3_only() { assert(VM_Version::has_vis3(), "This instruction only works on SPARC with VIS3"); }

  // instruction deprecated in v9
  static void v9_dep() { } // do nothing for now

 protected:
#ifdef ASSERT
#define VALIDATE_PIPELINE
#endif

#ifdef VALIDATE_PIPELINE
  // A simple delay-slot scheme:
  // In order to check the programmer, the assembler keeps track of delay-slots.
  // It forbids CTIs in delay-slots (conservative, but should be OK). Also, when
  // emitting an instruction into a delay-slot, you must do so using delayed(),
  // e.g. asm->delayed()->add(...), in order to check that you do not omit the
  // delay-slot instruction. To implement this, we use a simple FSA.
  enum { NoDelay, AtDelay, FillDelay } _delay_state;

  // A simple hazard scheme:
  // In order to avoid pipeline stalls, due to single cycle pipeline hazards, we
  // adopt a simplistic state tracking mechanism that will enforce an additional
  // 'nop' instruction to be inserted prior to emitting an instruction that can
  // expose a given hazard (currently, PC-related hazards only).
  enum { NoHazard, PcHazard } _hazard_state;
#endif

 public:
  // Tell the assembler that the next instruction must NOT be in delay-slot.
  // Use at start of multi-instruction macros.
  void assert_not_delayed() {
    // This is a separate entry to avoid the creation of string constants in
    // non-asserted code, with some compilers this pollutes the object code.
#ifdef VALIDATE_PIPELINE
    assert_no_delay("Next instruction should not be in a delay-slot.");
#endif
  }

 protected:
  void assert_no_delay(const char* msg) {
#ifdef VALIDATE_PIPELINE
    assert(_delay_state == NoDelay, msg);
#endif
  }

  void assert_no_hazard() {
#ifdef VALIDATE_PIPELINE
    assert(_hazard_state == NoHazard, "Unsolicited pipeline hazard.");
#endif
  }

 private:
  inline int32_t prev_insn() {
    assert(offset() > 0, "Interface violation.");
    int32_t* addr = (int32_t*)pc() - 1;
    return *addr;
  }

#ifdef VALIDATE_PIPELINE
  void validate_no_pipeline_hazards();
#endif

 protected:
  // Avoid possible pipeline stall by inserting an additional 'nop' instruction,
  // if the previous instruction is a 'cbcond' or a 'rdpc'.
  inline void avoid_pipeline_stall();

  // A call to cti() is made before emitting a control-transfer instruction (CTI)
  // in order to assert a CTI is not emitted right after a 'cbcond', nor in the
  // delay-slot of another CTI. Only effective when assertions are enabled.
  void cti() {
    // A 'cbcond' or 'rdpc' instruction immediately followed by a CTI introduces
    // a pipeline stall, which we make sure to prohibit.
    assert_no_cbcond_before();
    assert_no_rdpc_before();
#ifdef VALIDATE_PIPELINE
    assert_no_hazard();
    assert_no_delay("CTI in delay-slot.");
#endif
  }

  // Called when emitting CTI with a delay-slot, AFTER emitting.
  inline void induce_delay_slot() {
#ifdef VALIDATE_PIPELINE
    assert_no_delay("Already in delay-slot.");
    _delay_state = AtDelay;
#endif
  }

  inline void induce_pc_hazard() {
#ifdef VALIDATE_PIPELINE
    assert_no_hazard();
    _hazard_state = PcHazard;
#endif
  }

  bool is_cbcond_before() { return offset() > 0 ? is_cbcond(prev_insn()) : false; }

  bool is_rdpc_before() { return offset() > 0 ? is_rdpc(prev_insn()) : false; }

  void assert_no_cbcond_before() {
    assert(offset() == 0 || !is_cbcond_before(), "CBCOND should not be followed by CTI.");
  }

  void assert_no_rdpc_before() {
    assert(offset() == 0 || !is_rdpc_before(), "RDPC should not be followed by CTI.");
  }

 public:

  bool use_cbcond(Label &L) {
    if (!UseCBCond || is_cbcond_before()) return false;
    intptr_t x = intptr_t(target_distance(L)) - intptr_t(pc());
    assert((x & 3) == 0, "not word aligned");
    return is_simm12(x);
  }

  // Tells assembler you know that next instruction is delayed
  Assembler* delayed() {
#ifdef VALIDATE_PIPELINE
    assert(_delay_state == AtDelay, "Delayed instruction not in delay-slot.");
    _delay_state = FillDelay;
#endif
    return this;
  }

  void flush() {
#ifdef VALIDATE_PIPELINE
    assert(_delay_state == NoDelay, "Ending code with a delay-slot.");
#ifdef COMPILER2
    validate_no_pipeline_hazards();
#endif
#endif
    AbstractAssembler::flush();
  }

  inline void emit_int32(int32_t);  // shadows AbstractAssembler::emit_int32
  inline void emit_data(int32_t);
  inline void emit_data(int32_t, RelocationHolder const&);
  inline void emit_data(int32_t, relocInfo::relocType rtype);

  // Helper for the above functions.
  inline void check_delay();


 public:
  // instructions, refer to page numbers in the SPARC Architecture Manual, V9

  // pp 135

  inline void add(Register s1, Register s2, Register d);
  inline void add(Register s1, int simm13a, Register d);

  inline void addcc(Register s1, Register s2, Register d);
  inline void addcc(Register s1, int simm13a, Register d);
  inline void addc(Register s1, Register s2, Register d);
  inline void addc(Register s1, int simm13a, Register d);
  inline void addccc(Register s1, Register s2, Register d);
  inline void addccc(Register s1, int simm13a, Register d);


  // 4-operand AES instructions

  inline void aes_eround01(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_eround23(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_dround01(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_dround23(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_eround01_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_eround23_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_dround01_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_dround23_l(FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void aes_kexpand1(FloatRegister s1, FloatRegister s2, int imm5a, FloatRegister d);


  // 3-operand AES instructions

  inline void aes_kexpand0(FloatRegister s1, FloatRegister s2, FloatRegister d);
  inline void aes_kexpand2(FloatRegister s1, FloatRegister s2, FloatRegister d);

  // pp 136

  inline void bpr(RCondition c, bool a, Predict p, Register s1, address d, relocInfo::relocType rt = relocInfo::none);
  inline void bpr(RCondition c, bool a, Predict p, Register s1, Label &L);

  // compare and branch
  inline void cbcond(Condition c, CC cc, Register s1, Register s2, Label &L);
  inline void cbcond(Condition c, CC cc, Register s1, int simm5, Label &L);

 protected: // use MacroAssembler::br instead

  // pp 138

  inline void fb(Condition c, bool a, address d, relocInfo::relocType rt = relocInfo::none);
  inline void fb(Condition c, bool a, Label &L);

  // pp 141

  inline void fbp(Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt = relocInfo::none);
  inline void fbp(Condition c, bool a, CC cc, Predict p, Label &L);

  // pp 144

  inline void br(Condition c, bool a, address d, relocInfo::relocType rt = relocInfo::none);
  inline void br(Condition c, bool a, Label &L);

  // pp 146

  inline void bp(Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt = relocInfo::none);
  inline void bp(Condition c, bool a, CC cc, Predict p, Label &L);

  // pp 149

  inline void call(address d, relocInfo::relocType rt = relocInfo::runtime_call_type);
  inline void call(Label &L,  relocInfo::relocType rt = relocInfo::runtime_call_type);

  inline void call(address d, RelocationHolder const &rspec);

 public:

  // pp 150

  // These instructions compare the contents of s2 with the contents of
  // memory at address in s1. If the values are equal, the contents of memory
  // at address s1 is swapped with the data in d. If the values are not equal,
  // the the contents of memory at s1 is loaded into d, without the swap.

  inline void casa(Register s1, Register s2, Register d, int ia = -1);
  inline void casxa(Register s1, Register s2, Register d, int ia = -1);

  // pp 152

  inline void udiv(Register s1, Register s2, Register d);
  inline void udiv(Register s1, int simm13a, Register d);
  inline void sdiv(Register s1, Register s2, Register d);
  inline void sdiv(Register s1, int simm13a, Register d);
  inline void udivcc(Register s1, Register s2, Register d);
  inline void udivcc(Register s1, int simm13a, Register d);
  inline void sdivcc(Register s1, Register s2, Register d);
  inline void sdivcc(Register s1, int simm13a, Register d);

  // pp 155

  inline void done();
  inline void retry();

  // pp 156

  inline void fadd(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d);
  inline void fsub(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d);

  // pp 157

  inline void fcmp(FloatRegisterImpl::Width w, CC cc, FloatRegister s1, FloatRegister s2);
  inline void fcmpe(FloatRegisterImpl::Width w, CC cc, FloatRegister s1, FloatRegister s2);

  // pp 159

  inline void ftox(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);
  inline void ftoi(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);

  // pp 160

  inline void ftof(FloatRegisterImpl::Width sw, FloatRegisterImpl::Width dw, FloatRegister s, FloatRegister d);

  // pp 161

  inline void fxtof(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);
  inline void fitof(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);

  // pp 162

  inline void fmov(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);

  inline void fneg(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);

  inline void fabs(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);

  // pp 163

  inline void fmul(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d);
  inline void fmul(FloatRegisterImpl::Width sw, FloatRegisterImpl::Width dw, FloatRegister s1, FloatRegister s2, FloatRegister d);
  inline void fdiv(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d);

  // FXORs/FXORd instructions

  inline void fxor(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d);

  // pp 164

  inline void fsqrt(FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);

  // fmaf instructions.

  inline void fmadd(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void fmsub(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);

  inline void fnmadd(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);
  inline void fnmsub(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister s3, FloatRegister d);

  // pp 165

  inline void flush(Register s1, Register s2);
  inline void flush(Register s1, int simm13a);

  // pp 167

  void flushw();

  // pp 168

  void illtrap(int const22a);

  // pp 169

  void impdep1(int id1, int const19a);
  void impdep2(int id1, int const19a);

  // pp 170

  void jmpl(Register s1, Register s2, Register d);
  void jmpl(Register s1, int simm13a, Register d,
            RelocationHolder const &rspec = RelocationHolder());

  // 171

  inline void ldf(FloatRegisterImpl::Width w, Register s1, Register s2, FloatRegister d);
  inline void ldf(FloatRegisterImpl::Width w, Register s1, int simm13a, FloatRegister d,
                  RelocationHolder const &rspec = RelocationHolder());

  inline void ldd(Register s1, Register s2, FloatRegister d);
  inline void ldd(Register s1, int simm13a, FloatRegister d);

  inline void ldfsr(Register s1, Register s2);
  inline void ldfsr(Register s1, int simm13a);
  inline void ldxfsr(Register s1, Register s2);
  inline void ldxfsr(Register s1, int simm13a);

  // 173

  inline void ldfa(FloatRegisterImpl::Width w, Register s1, Register s2, int ia, FloatRegister d);
  inline void ldfa(FloatRegisterImpl::Width w, Register s1, int simm13a,         FloatRegister d);

  // pp 175

  inline void ldsb(Register s1, Register s2, Register d);
  inline void ldsb(Register s1, int simm13a, Register d);
  inline void ldsh(Register s1, Register s2, Register d);
  inline void ldsh(Register s1, int simm13a, Register d);
  inline void ldsw(Register s1, Register s2, Register d);
  inline void ldsw(Register s1, int simm13a, Register d);
  inline void ldub(Register s1, Register s2, Register d);
  inline void ldub(Register s1, int simm13a, Register d);
  inline void lduh(Register s1, Register s2, Register d);
  inline void lduh(Register s1, int simm13a, Register d);
  inline void lduw(Register s1, Register s2, Register d);
  inline void lduw(Register s1, int simm13a, Register d);
  inline void ldx(Register s1, Register s2, Register d);
  inline void ldx(Register s1, int simm13a, Register d);

  // pp 177

  inline void ldsba(Register s1, Register s2, int ia, Register d);
  inline void ldsba(Register s1, int simm13a,         Register d);
  inline void ldsha(Register s1, Register s2, int ia, Register d);
  inline void ldsha(Register s1, int simm13a,         Register d);
  inline void ldswa(Register s1, Register s2, int ia, Register d);
  inline void ldswa(Register s1, int simm13a,         Register d);
  inline void lduba(Register s1, Register s2, int ia, Register d);
  inline void lduba(Register s1, int simm13a,         Register d);
  inline void lduha(Register s1, Register s2, int ia, Register d);
  inline void lduha(Register s1, int simm13a,         Register d);
  inline void lduwa(Register s1, Register s2, int ia, Register d);
  inline void lduwa(Register s1, int simm13a,         Register d);
  inline void ldxa(Register s1, Register s2, int ia, Register d);
  inline void ldxa(Register s1, int simm13a,         Register d);

  // pp 181

  inline void and3(Register s1, Register s2, Register d);
  inline void and3(Register s1, int simm13a, Register d);
  inline void andcc(Register s1, Register s2, Register d);
  inline void andcc(Register s1, int simm13a, Register d);
  inline void andn(Register s1, Register s2, Register d);
  inline void andn(Register s1, int simm13a, Register d);
  inline void andncc(Register s1, Register s2, Register d);
  inline void andncc(Register s1, int simm13a, Register d);
  inline void or3(Register s1, Register s2, Register d);
  inline void or3(Register s1, int simm13a, Register d);
  inline void orcc(Register s1, Register s2, Register d);
  inline void orcc(Register s1, int simm13a, Register d);
  inline void orn(Register s1, Register s2, Register d);
  inline void orn(Register s1, int simm13a, Register d);
  inline void orncc(Register s1, Register s2, Register d);
  inline void orncc(Register s1, int simm13a, Register d);
  inline void xor3(Register s1, Register s2, Register d);
  inline void xor3(Register s1, int simm13a, Register d);
  inline void xorcc(Register s1, Register s2, Register d);
  inline void xorcc(Register s1, int simm13a, Register d);
  inline void xnor(Register s1, Register s2, Register d);
  inline void xnor(Register s1, int simm13a, Register d);
  inline void xnorcc(Register s1, Register s2, Register d);
  inline void xnorcc(Register s1, int simm13a, Register d);

  // pp 183

  inline void membar(Membar_mask_bits const7a);

  // pp 185

  inline void fmov(FloatRegisterImpl::Width w, Condition c,  bool floatCC, CC cca, FloatRegister s2, FloatRegister d);

  // pp 189

  inline void fmov(FloatRegisterImpl::Width w, RCondition c, Register s1,  FloatRegister s2, FloatRegister d);

  // pp 191

  inline void movcc(Condition c, bool floatCC, CC cca, Register s2, Register d);
  inline void movcc(Condition c, bool floatCC, CC cca, int simm11a, Register d);

  // pp 195

  inline void movr(RCondition c, Register s1, Register s2,  Register d);
  inline void movr(RCondition c, Register s1, int simm10a,  Register d);

  // pp 196

  inline void mulx(Register s1, Register s2, Register d);
  inline void mulx(Register s1, int simm13a, Register d);
  inline void sdivx(Register s1, Register s2, Register d);
  inline void sdivx(Register s1, int simm13a, Register d);
  inline void udivx(Register s1, Register s2, Register d);
  inline void udivx(Register s1, int simm13a, Register d);

  // pp 197

  inline void umul(Register s1, Register s2, Register d);
  inline void umul(Register s1, int simm13a, Register d);
  inline void smul(Register s1, Register s2, Register d);
  inline void smul(Register s1, int simm13a, Register d);
  inline void umulcc(Register s1, Register s2, Register d);
  inline void umulcc(Register s1, int simm13a, Register d);
  inline void smulcc(Register s1, Register s2, Register d);
  inline void smulcc(Register s1, int simm13a, Register d);

  // pp 201

  inline void nop();

  inline void sw_count();

  // pp 202

  inline void popc(Register s,  Register d);
  inline void popc(int simm13a, Register d);

  // pp 203

  inline void prefetch(Register s1, Register s2, PrefetchFcn f);
  inline void prefetch(Register s1, int simm13a, PrefetchFcn f);

  inline void prefetcha(Register s1, Register s2, int ia, PrefetchFcn f);
  inline void prefetcha(Register s1, int simm13a,         PrefetchFcn f);

  // pp 208

  // not implementing read privileged register

  inline void rdy(Register d);
  inline void rdccr(Register d);
  inline void rdasi(Register d);
  inline void rdtick(Register d);
  inline void rdpc(Register d);
  inline void rdfprs(Register d);

  // pp 213

  inline void rett(Register s1, Register s2);
  inline void rett(Register s1, int simm13a, relocInfo::relocType rt = relocInfo::none);

  // pp 214

  inline void save(Register s1, Register s2, Register d);
  inline void save(Register s1, int simm13a, Register d);

  inline void restore(Register s1 = G0, Register s2 = G0, Register d = G0);
  inline void restore(Register s1,      int simm13a,      Register d);

  // pp 216

  inline void saved();
  inline void restored();

  // pp 217

  inline void sethi(int imm22a, Register d, RelocationHolder const &rspec = RelocationHolder());

  // pp 218

  inline void sll(Register s1, Register s2, Register d);
  inline void sll(Register s1, int imm5a,   Register d);
  inline void srl(Register s1, Register s2, Register d);
  inline void srl(Register s1, int imm5a,   Register d);
  inline void sra(Register s1, Register s2, Register d);
  inline void sra(Register s1, int imm5a,   Register d);

  inline void sllx(Register s1, Register s2, Register d);
  inline void sllx(Register s1, int imm6a,   Register d);
  inline void srlx(Register s1, Register s2, Register d);
  inline void srlx(Register s1, int imm6a,   Register d);
  inline void srax(Register s1, Register s2, Register d);
  inline void srax(Register s1, int imm6a,   Register d);

  // pp 220

  inline void sir(int simm13a);

  // pp 221

  inline void stbar();

  // pp 222

  inline void stf(FloatRegisterImpl::Width w, FloatRegister d, Register s1, Register s2);
  inline void stf(FloatRegisterImpl::Width w, FloatRegister d, Register s1, int simm13a);

  inline void std(FloatRegister d, Register s1, Register s2);
  inline void std(FloatRegister d, Register s1, int simm13a);

  inline void stfsr(Register s1, Register s2);
  inline void stfsr(Register s1, int simm13a);
  inline void stxfsr(Register s1, Register s2);
  inline void stxfsr(Register s1, int simm13a);

  // pp 224

  inline void stfa(FloatRegisterImpl::Width w, FloatRegister d, Register s1, Register s2, int ia);
  inline void stfa(FloatRegisterImpl::Width w, FloatRegister d, Register s1, int simm13a);

  // pp 226

  inline void stb(Register d, Register s1, Register s2);
  inline void stb(Register d, Register s1, int simm13a);
  inline void sth(Register d, Register s1, Register s2);
  inline void sth(Register d, Register s1, int simm13a);
  inline void stw(Register d, Register s1, Register s2);
  inline void stw(Register d, Register s1, int simm13a);
  inline void stx(Register d, Register s1, Register s2);
  inline void stx(Register d, Register s1, int simm13a);

  // pp 177

  inline void stba(Register d, Register s1, Register s2, int ia);
  inline void stba(Register d, Register s1, int simm13a);
  inline void stha(Register d, Register s1, Register s2, int ia);
  inline void stha(Register d, Register s1, int simm13a);
  inline void stwa(Register d, Register s1, Register s2, int ia);
  inline void stwa(Register d, Register s1, int simm13a);
  inline void stxa(Register d, Register s1, Register s2, int ia);
  inline void stxa(Register d, Register s1, int simm13a);
  inline void stda(Register d, Register s1, Register s2, int ia);
  inline void stda(Register d, Register s1, int simm13a);

  // pp 230

  inline void sub(Register s1, Register s2, Register d);
  inline void sub(Register s1, int simm13a, Register d);

  inline void subcc(Register s1, Register s2, Register d);
  inline void subcc(Register s1, int simm13a, Register d);
  inline void subc(Register s1, Register s2, Register d);
  inline void subc(Register s1, int simm13a, Register d);
  inline void subccc(Register s1, Register s2, Register d);
  inline void subccc(Register s1, int simm13a, Register d);

  // pp 231

  inline void swap(Register s1, Register s2, Register d);
  inline void swap(Register s1, int simm13a, Register d);

  // pp 232

  inline void swapa(Register s1, Register s2, int ia, Register d);
  inline void swapa(Register s1, int simm13a,         Register d);

  // pp 234, note op in book is wrong, see pp 268

  inline void taddcc(Register s1, Register s2, Register d);
  inline void taddcc(Register s1, int simm13a, Register d);

  // pp 235

  inline void tsubcc(Register s1, Register s2, Register d);
  inline void tsubcc(Register s1, int simm13a, Register d);

  // pp 237

  inline void trap(Condition c, CC cc, Register s1, Register s2);
  inline void trap(Condition c, CC cc, Register s1, int trapa);
  // simple uncond. trap
  inline void trap(int trapa);

  // pp 239 omit write priv register for now

  inline void wry(Register d);
  inline void wrccr(Register s);
  inline void wrccr(Register s, int simm13a);
  inline void wrasi(Register d);
  // wrasi(d, imm) stores (d xor imm) to asi
  inline void wrasi(Register d, int simm13a);
  inline void wrfprs(Register d);

  // VIS1 instructions

  inline void alignaddr(Register s1, Register s2, Register d);

  inline void faligndata(FloatRegister s1, FloatRegister s2, FloatRegister d);

  inline void fzero(FloatRegisterImpl::Width w, FloatRegister d);

  inline void fsrc2(FloatRegisterImpl::Width w, FloatRegister s2, FloatRegister d);

  inline void fnot1(FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister d);

  inline void fpmerge(FloatRegister s1, FloatRegister s2, FloatRegister d);

  inline void stpartialf(Register s1, Register s2, FloatRegister d, int ia = -1);

  // VIS2 instructions

  inline void edge8n(Register s1, Register s2, Register d);

  inline void bmask(Register s1, Register s2, Register d);
  inline void bshuffle(FloatRegister s1, FloatRegister s2, FloatRegister d);

  // VIS3 instructions

  inline void addxc(Register s1, Register s2, Register d);
  inline void addxccc(Register s1, Register s2, Register d);

  inline void movstosw(FloatRegister s, Register d);
  inline void movstouw(FloatRegister s, Register d);
  inline void movdtox(FloatRegister s, Register d);

  inline void movwtos(Register s, FloatRegister d);
  inline void movxtod(Register s, FloatRegister d);

  inline void xmulx(Register s1, Register s2, Register d);
  inline void xmulxhi(Register s1, Register s2, Register d);
  inline void umulxhi(Register s1, Register s2, Register d);

  // Crypto SHA instructions

  inline void sha1();
  inline void sha256();
  inline void sha512();

  // CRC32C instruction

  inline void crc32c(FloatRegister s1, FloatRegister s2, FloatRegister d);

  // MPMUL instruction

  inline void mpmul(int uimm5);

  // Creation
  Assembler(CodeBuffer* code) : AbstractAssembler(code) {
#ifdef VALIDATE_PIPELINE
    _delay_state  = NoDelay;
    _hazard_state = NoHazard;
#endif
  }
};

#endif // CPU_SPARC_VM_ASSEMBLER_SPARC_HPP
