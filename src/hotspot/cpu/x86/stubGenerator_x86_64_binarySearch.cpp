/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "macroAssembler_x86.hpp"
#include "runtime/globals.hpp"
#include "runtime/stubRoutines.hpp"
#include "stubGenerator_x86_64.hpp"
#include "utilities/globalDefinitions.hpp"

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

// Minimum element count to enter the N-ary loop. Below this, fall to scalar tail.
static const int DWORD_NARY_THRESHOLD = 128;
static const int QWORD_NARY_THRESHOLD = 128;

// Emit the dword N-ary loop body with per-type scalar fallback.
#define EMIT_DWORD_NARY_LOOP(loop_label, scalar_label, load_and_pack)          \
    __ bind(loop_label);                                                       \
    BLOCK_COMMENT("range check");                                              \
    __ movl(r_range, r_high);                                                  \
    __ subl(r_range, r_low);                                                   \
    __ incl(r_range);                                                          \
    __ cmpl(r_range, DWORD_NARY_THRESHOLD);                                    \
    __ jcc(Assembler::less, scalar_label);                                     \
                                                                               \
    __ movl(r_stride, r_range);                                                \
    __ shrl(r_stride, 3);                                                      \
                                                                               \
    BLOCK_COMMENT("load 7 pivots");                                            \
    load_and_pack                                                              \
                                                                               \
    BLOCK_COMMENT("eq check");                                                 \
    __ vpcmpeqd(ymm_eq, ymm_key, ymm_data, Assembler::AVX_256bit);             \
    __ vmovmskps(r_tmp1, ymm_eq, Assembler::AVX_256bit);                       \
    __ andl(r_tmp1, 0x7F);                                                     \
    __ jcc(Assembler::notZero, L_found_nary);                                  \
                                                                               \
    BLOCK_COMMENT("partition + bounds");                                        \
    __ vpcmpgtd(ymm_cmp, ymm_key, ymm_data, Assembler::AVX_256bit);            \
    __ vmovmskps(r_C, ymm_cmp, Assembler::AVX_256bit);                         \
    __ andl(r_C, 0x7F);                                                        \
    __ popcntl(r_C, r_C);                                                      \
                                                                               \
    __ movl(r_tmp1, r_C);                                                      \
    __ imull(r_tmp1, r_stride);                                                \
    __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));              \
    __ addl(r_tmp1, r_low);                                                    \
    __ incl(r_tmp1);                                                           \
    __ testl(r_C, r_C);                                                        \
    __ cmovl(Assembler::zero, r_tmp1, r_low);                                  \
    __ addl(r_tmp2, r_low);                                                    \
    __ decl(r_tmp2);                                                           \
    __ cmpl(r_C, 7);                                                           \
    __ cmovl(Assembler::equal, r_tmp2, r_high);                                \
                                                                               \
    __ movl(r_low, r_tmp1);                                                    \
    __ movl(r_high, r_tmp2);                                                   \
    __ jmp(loop_label);

// Emit a type-specific scalar binary search tail.
#define EMIT_SCALAR_TAIL(loop_label, found_label, less_label, load_mid_insn)   \
    __ bind(loop_label);                                                       \
    BLOCK_COMMENT("scalar: mid = (lo+hi)>>>1");                                \
    __ cmpl(r_low, r_high);                                                    \
    __ jcc(Assembler::greater, L_not_found);                                   \
    __ movl(r_tmp1, r_low);                                                    \
    __ addl(r_tmp1, r_high);                                                   \
    __ shrl(r_tmp1, 1);                                                        \
    load_mid_insn                                                              \
    __ cmpl(r_tmp2, r_key);                                                    \
    __ jcc(Assembler::equal, found_label);                                     \
    __ jcc(Assembler::less, less_label);                                       \
    __ leal(r_high, Address(r_tmp1, -1));                                      \
    __ jmp(loop_label);                                                        \
    __ bind(less_label);                                                       \
    __ leal(r_low, Address(r_tmp1, 1));                                        \
    __ jmp(loop_label);                                                        \
    __ bind(found_label);                                                      \
    __ movl(rax, r_tmp1);                                                      \
    __ jmp(L_done);

// AVX2 SIMD N-ary binary search for sorted primitive arrays.
//
// Arguments (shuffled to SysV register positions via setup_arg_regs):
//   rdi - array base address (element 0)
//   rsi - element type (BasicType)
//   rdx - fromIndex
//   rcx - toIndex
//   r8  - key (low 32 for int/short/char, full 64 for long)
//
// Returns: rax (index if found, or -(insertion_point + 1))

address StubGenerator::generate_arrayBinarySearch() {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "array_binary_search");
  address start = __ pc();

  const Register arr_base  = rdi;
  const Register elem_type = rsi;
  const Register r_low     = rdx;
  const Register r_toIndex = rcx;
  const Register r_key     = r8;

  const Register r_high   = rbx; // callee-saved
  const Register r_stride = r12; // callee-saved
  const Register r_tmp1   = r11;
  const Register r_tmp2   = r13; // callee-saved
  const Register r_range  = r14; // callee-saved
  const Register r_C      = rax;

  const XMMRegister ymm_data  = xmm0;
  const XMMRegister ymm_mask  = xmm1;  // also ymm_eq_hi in long path
  const XMMRegister ymm_key   = xmm2;
  const XMMRegister ymm_cmp   = xmm3;  // scratch, also used for packing high 128 bits
  const XMMRegister ymm_eq    = xmm4;
  const XMMRegister ymm_seq   = xmm5;  // also ymm_data_hi in long path

  BLOCK_COMMENT("Entry:");
  __ enter();
  __ push(rbx);
  __ push(r12);
  __ push(r13);
  __ push(r14);

#ifdef _WIN64
  setup_arg_regs(4); // arr_base => rdi, elem_type => rsi, fromIndex => rdx, toIndex => rcx
  // 5th argument (key) is on the stack on Win64
  __ movq(r8, Address(rsp, 10 * wordSize));
#endif

  // high = toIndex - 1
  __ movl(r_high, r_toIndex);
  __ decl(r_high);

  Label L_long;
  Label L_not_found, L_done;

  BLOCK_COMMENT("type dispatch");
  __ cmpl(elem_type, T_LONG);
  __ jcc(Assembler::equal, L_long);

  // =========================================================================
  // Dword path: int[], short[], char[] (8 lanes, 7 pivots)
  //
  // Three type-specific N-ary loops (no branches in hot loop),
  // shared found handler, per-type scalar tails.
  // =========================================================================
  {
    Label L_int_loop, L_short_loop, L_char_loop;
    Label L_found_nary;
    // Per-type scalar tails (no type dispatch per iteration)
    Label L_int_scalar, L_int_scalar_found, L_int_scalar_less;
    Label L_short_scalar, L_short_scalar_found, L_short_scalar_less;
    Label L_char_scalar, L_char_scalar_found, L_char_scalar_less;

    BLOCK_COMMENT("dword path: int/short/char");
    __ movdl(ymm_key, r_key);
    __ vpbroadcastd(ymm_key, ymm_key, Assembler::AVX_256bit);

    // Dispatch to type-specific loop
    __ cmpl(elem_type, T_INT);
    __ jcc(Assembler::equal, L_int_loop);
    __ cmpl(elem_type, T_SHORT);
    __ jcc(Assembler::equal, L_short_loop);
    // fall through to char loop

    // --- char N-ary loop (scalar movzwl loads, pack into ymm) ---
    BLOCK_COMMENT("char N-ary loop");
    EMIT_DWORD_NARY_LOOP(L_char_loop, L_char_scalar,
      __ leal(r_range, Address(r_low, r_stride, Address::times_1));
      __ leal(r_tmp1, Address(r_low, r_stride, Address::times_2));
      __ leal(r_C, Address(r_low, r_stride, Address::times_4));
      __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));
      __ movzwl(r_range, Address(arr_base, r_range, Address::times_2));
      __ movzwl(r_tmp1, Address(arr_base, r_tmp1, Address::times_2));
      __ movzwl(r_tmp2, Address(arr_base, r_tmp2, Address::times_2));
      __ movzwl(r_C, Address(arr_base, r_C, Address::times_2));
      __ movdl(ymm_data, r_range);
      __ vpinsrd(ymm_data, ymm_data, r_tmp1, 1);
      __ vpinsrd(ymm_data, ymm_data, r_tmp2, 2);
      __ vpinsrd(ymm_data, ymm_data, r_C, 3);
      __ leal(r_range, Address(r_low, r_stride, Address::times_4));
      __ leal(r_tmp1, Address(r_range, r_stride, Address::times_2));
      __ leal(r_range, Address(r_range, r_stride, Address::times_1));
      __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));
      __ movzwl(r_range, Address(arr_base, r_range, Address::times_2));
      __ movzwl(r_tmp1, Address(arr_base, r_tmp1, Address::times_2));
      __ movzwl(r_tmp2, Address(arr_base, r_tmp2, Address::times_2));
      __ movdl(ymm_cmp, r_range);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp1, 1);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp2, 2);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp2, 3);
      __ vinserti128(ymm_data, ymm_data, ymm_cmp, 1);
    )

    // --- int N-ary loop (scalar loads, pack into ymm) ---
    BLOCK_COMMENT("int N-ary loop");
    EMIT_DWORD_NARY_LOOP(L_int_loop, L_int_scalar,
      /* 7 scalar loads + pack into ymm_data for SIMD comparison.           */
      /* Pivot indices: p0,p1,p3 independent to maximize load parallelism. */
      __ leal(r_range, Address(r_low, r_stride, Address::times_1));         /* p0 */
      __ leal(r_tmp1, Address(r_low, r_stride, Address::times_2));          /* p1 (independent) */
      __ leal(r_C, Address(r_low, r_stride, Address::times_4));             /* p3 (independent) */
      __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));         /* p2 (depends on p1) */
      /* Load first 4 values */
      __ movl(r_range, Address(arr_base, r_range, Address::times_4));
      __ movl(r_tmp1, Address(arr_base, r_tmp1, Address::times_4));
      __ movl(r_tmp2, Address(arr_base, r_tmp2, Address::times_4));
      __ movl(r_C, Address(arr_base, r_C, Address::times_4));
      /* Pack into low 128 bits */
      __ movdl(ymm_data, r_range);
      __ vpinsrd(ymm_data, ymm_data, r_tmp1, 1);
      __ vpinsrd(ymm_data, ymm_data, r_tmp2, 2);
      __ vpinsrd(ymm_data, ymm_data, r_C, 3);
      /* p4..p6: recompute base=low+4*stride, then p4,p5 independent */
      __ leal(r_range, Address(r_low, r_stride, Address::times_4));         /* base = low+4*stride */
      __ leal(r_tmp1, Address(r_range, r_stride, Address::times_2));        /* p5 (independent) */
      __ leal(r_range, Address(r_range, r_stride, Address::times_1));       /* p4 */
      __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));         /* p6 (depends on p5) */
      /* Load next 3 values */
      __ movl(r_range, Address(arr_base, r_range, Address::times_4));
      __ movl(r_tmp1, Address(arr_base, r_tmp1, Address::times_4));
      __ movl(r_tmp2, Address(arr_base, r_tmp2, Address::times_4));
      /* Pack into high 128 bits: [p4, p5, p6, p6] */
      __ movdl(ymm_cmp, r_range);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp1, 1);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp2, 2);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp2, 3);                            /* p7 = p6 dup */
      __ vinserti128(ymm_data, ymm_data, ymm_cmp, 1);
    )

    // --- short N-ary loop (scalar movswl loads, pack into ymm) ---
    BLOCK_COMMENT("short N-ary loop");
    EMIT_DWORD_NARY_LOOP(L_short_loop, L_short_scalar,
      __ leal(r_range, Address(r_low, r_stride, Address::times_1));
      __ leal(r_tmp1, Address(r_low, r_stride, Address::times_2));
      __ leal(r_C, Address(r_low, r_stride, Address::times_4));
      __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));
      __ movswl(r_range, Address(arr_base, r_range, Address::times_2));
      __ movswl(r_tmp1, Address(arr_base, r_tmp1, Address::times_2));
      __ movswl(r_tmp2, Address(arr_base, r_tmp2, Address::times_2));
      __ movswl(r_C, Address(arr_base, r_C, Address::times_2));
      __ movdl(ymm_data, r_range);
      __ vpinsrd(ymm_data, ymm_data, r_tmp1, 1);
      __ vpinsrd(ymm_data, ymm_data, r_tmp2, 2);
      __ vpinsrd(ymm_data, ymm_data, r_C, 3);
      __ leal(r_range, Address(r_low, r_stride, Address::times_4));
      __ leal(r_tmp1, Address(r_range, r_stride, Address::times_2));
      __ leal(r_range, Address(r_range, r_stride, Address::times_1));
      __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));
      __ movswl(r_range, Address(arr_base, r_range, Address::times_2));
      __ movswl(r_tmp1, Address(arr_base, r_tmp1, Address::times_2));
      __ movswl(r_tmp2, Address(arr_base, r_tmp2, Address::times_2));
      __ movdl(ymm_cmp, r_range);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp1, 1);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp2, 2);
      __ vpinsrd(ymm_cmp, ymm_cmp, r_tmp2, 3);
      __ vinserti128(ymm_data, ymm_data, ymm_cmp, 1);
    )

    // === Shared paths (run at most once per call) ===

    BLOCK_COMMENT("dword found in N-ary");
    __ bind(L_found_nary);
    __ tzcntl(r_tmp1, r_tmp1);
    __ incl(r_tmp1);
    __ imull(r_tmp1, r_stride);
    __ addl(r_tmp1, r_low);
    __ movl(rax, r_tmp1);
    __ jmp(L_done);

    // Per-type scalar tails (no type dispatch per iteration)
    BLOCK_COMMENT("int scalar tail");
    EMIT_SCALAR_TAIL(L_int_scalar, L_int_scalar_found, L_int_scalar_less,
      __ movl(r_tmp2, Address(arr_base, r_tmp1, Address::times_4));
    )
    BLOCK_COMMENT("short scalar tail");
    EMIT_SCALAR_TAIL(L_short_scalar, L_short_scalar_found, L_short_scalar_less,
      __ movswl(r_tmp2, Address(arr_base, r_tmp1, Address::times_2));
    )
    BLOCK_COMMENT("char scalar tail");
    EMIT_SCALAR_TAIL(L_char_scalar, L_char_scalar_found, L_char_scalar_less,
      __ movzwl(r_tmp2, Address(arr_base, r_tmp1, Address::times_2));
    )
  }

  // =========================================================================
  // Qword path: long[] (8 lanes across 2 YMMs, 7 pivots, scalar loads)
  // =========================================================================
  __ bind(L_long);
  {
    const XMMRegister ymm_data_hi = xmm5;
    const XMMRegister ymm_eq_hi   = xmm1;

    Label L_nary_loop, L_found_nary;
    Label L_scalar_loop, L_scalar_found, L_scalar_mid_less;

    BLOCK_COMMENT("qword path: long 8-ary search");
    __ movq(r_tmp1, r_key);
    __ movdq(ymm_key, r_tmp1);
    __ vpbroadcastq(ymm_key, ymm_key, Assembler::AVX_256bit);

    BLOCK_COMMENT("qword N-ary loop");
    __ bind(L_nary_loop);
    __ movl(r_range, r_high);
    __ subl(r_range, r_low);
    __ incl(r_range);
    __ cmpl(r_range, QWORD_NARY_THRESHOLD);
    __ jcc(Assembler::less, L_scalar_loop);

    __ movl(r_stride, r_range);
    __ shrl(r_stride, 3);

    BLOCK_COMMENT("load 7 pivots");
    // p0..p3: indices and loads for low 4 lanes
    __ leal(r_range, Address(r_low, r_stride, Address::times_1));         // p0
    __ leal(r_tmp1, Address(r_low, r_stride, Address::times_2));          // p1
    __ leal(r_C, Address(r_low, r_stride, Address::times_4));             // p3
    __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));         // p2
    __ movq(r_range, Address(arr_base, r_range, Address::times_8));
    __ movq(r_tmp1, Address(arr_base, r_tmp1, Address::times_8));
    __ movq(r_tmp2, Address(arr_base, r_tmp2, Address::times_8));
    __ movq(r_C, Address(arr_base, r_C, Address::times_8));
    // Pack [p0, p1] into low 128, [p2, p3] into high 128
    __ movdq(ymm_data, r_range);
    __ vpinsrq(ymm_data, ymm_data, r_tmp1, 1);
    __ movdq(ymm_cmp, r_tmp2);
    __ vpinsrq(ymm_cmp, ymm_cmp, r_C, 1);
    __ vinserti128(ymm_data, ymm_data, ymm_cmp, 1);                      // [p0, p1, p2, p3]

    // p4..p6: indices and loads for high 4 lanes
    __ leal(r_range, Address(r_low, r_stride, Address::times_4));         // base = low+4*stride
    __ leal(r_tmp1, Address(r_range, r_stride, Address::times_2));        // p5
    __ leal(r_range, Address(r_range, r_stride, Address::times_1));       // p4
    __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));         // p6
    __ movq(r_range, Address(arr_base, r_range, Address::times_8));
    __ movq(r_tmp1, Address(arr_base, r_tmp1, Address::times_8));
    __ movq(r_tmp2, Address(arr_base, r_tmp2, Address::times_8));
    // Pack [p4, p5] into low 128, [p6, p6] into high 128
    __ movdq(ymm_data_hi, r_range);
    __ vpinsrq(ymm_data_hi, ymm_data_hi, r_tmp1, 1);
    __ movdq(ymm_cmp, r_tmp2);
    __ vpinsrq(ymm_cmp, ymm_cmp, r_tmp2, 1);                            // p7 = p6 dup
    __ vinserti128(ymm_data_hi, ymm_data_hi, ymm_cmp, 1);               // [p4, p5, p6, p6]

    BLOCK_COMMENT("eq check");
    __ vpcmpeqq(ymm_eq, ymm_key, ymm_data, Assembler::AVX_256bit);
    __ vpcmpeqq(ymm_eq_hi, ymm_key, ymm_data_hi, Assembler::AVX_256bit);
    __ vmovmskpd(r_tmp1, ymm_eq, Assembler::AVX_256bit);
    __ vmovmskpd(r_tmp2, ymm_eq_hi, Assembler::AVX_256bit);
    __ shll(r_tmp2, 4);
    __ orl(r_tmp1, r_tmp2);
    __ andl(r_tmp1, 0x7F);
    __ jcc(Assembler::notZero, L_found_nary);

    BLOCK_COMMENT("partition + bounds");
    __ vpcmpgtq(ymm_cmp, ymm_key, ymm_data, Assembler::AVX_256bit);
    __ vmovmskpd(r_C, ymm_cmp, Assembler::AVX_256bit);
    __ vpcmpgtq(ymm_cmp, ymm_key, ymm_data_hi, Assembler::AVX_256bit);
    __ vmovmskpd(r_tmp2, ymm_cmp, Assembler::AVX_256bit);
    __ shll(r_tmp2, 4);
    __ orl(r_C, r_tmp2);
    __ andl(r_C, 0x7F);
    __ popcntl(r_C, r_C);

    // Branchless bounds update (same as dword: 8 partitions, C in 0..7)
    __ movl(r_tmp1, r_C);
    __ imull(r_tmp1, r_stride);
    __ leal(r_tmp2, Address(r_tmp1, r_stride, Address::times_1));
    __ addl(r_tmp1, r_low);
    __ incl(r_tmp1);
    __ testl(r_C, r_C);
    __ cmovl(Assembler::zero, r_tmp1, r_low);
    __ addl(r_tmp2, r_low);
    __ decl(r_tmp2);
    __ cmpl(r_C, 7);
    __ cmovl(Assembler::equal, r_tmp2, r_high);

    __ movl(r_low, r_tmp1);
    __ movl(r_high, r_tmp2);
    __ jmp(L_nary_loop);

    BLOCK_COMMENT("qword found in N-ary");
    __ bind(L_found_nary);
    __ tzcntl(r_tmp1, r_tmp1);
    __ incl(r_tmp1);
    __ imull(r_tmp1, r_stride);
    __ addl(r_tmp1, r_low);
    __ movl(rax, r_tmp1);
    __ jmp(L_done);

    BLOCK_COMMENT("qword scalar tail");
    __ bind(L_scalar_loop);
    __ cmpl(r_low, r_high);
    __ jcc(Assembler::greater, L_not_found);
    __ movl(r_tmp1, r_low);
    __ addl(r_tmp1, r_high);
    __ shrl(r_tmp1, 1);
    __ movq(r_tmp2, Address(arr_base, r_tmp1, Address::times_8));
    __ cmpq(r_tmp2, r_key);
    __ jcc(Assembler::equal, L_scalar_found);
    __ jcc(Assembler::less, L_scalar_mid_less);
    __ leal(r_high, Address(r_tmp1, -1));
    __ jmp(L_scalar_loop);
    __ bind(L_scalar_mid_less);
    __ leal(r_low, Address(r_tmp1, 1));
    __ jmp(L_scalar_loop);
    __ bind(L_scalar_found);
    __ movl(rax, r_tmp1);
    __ jmp(L_done);
  }

  // =========================================================================
  // Common exit
  // =========================================================================
  __ bind(L_not_found);
  __ movl(rax, r_low);
  __ negl(rax);
  __ decl(rax);

  __ bind(L_done);
  __ vzeroupper();
#ifdef _WIN64
  restore_arg_regs();
#endif
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);
  __ pop(rbx);
  __ leave();
  __ ret(0);

  return start;
}
