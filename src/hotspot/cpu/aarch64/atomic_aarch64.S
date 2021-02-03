/*
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
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

  // aarch64_atomic_fetch_add_8_impl = (aarch64_atomic_fetch_add_8_type)__ pc();
  // {
  //   Register prev = r2, addr = c_rarg0, incr = c_rarg1;
  //   __ atomic_addal(prev, incr, addr);
  //   __ mov(r0, prev);
  //   __ ret(lr);
  // }
  // aarch64_atomic_fetch_add_4_impl = (aarch64_atomic_fetch_add_4_type)__ pc();
  // {
  //   Register prev = r2, addr = c_rarg0, incr = c_rarg1;
  //   __ atomic_addalw(prev, incr, addr);
  //   __ movw(r0, prev);
  //   __ ret(lr);
  // }
  // aarch64_atomic_xchg_4_impl = (aarch64_atomic_xchg_4_type)__ pc();
  // {
  //   Register prev = r2, addr = c_rarg0, newv = c_rarg1;
  //   __ atomic_xchgalw(prev, newv, addr);
  //   __ movw(r0, prev);
  //   __ ret(lr);
  // }
  // aarch64_atomic_xchg_8_impl = (aarch64_atomic_xchg_8_type)__ pc();
  // {
  //   Register prev = r2, addr = c_rarg0, newv = c_rarg1;
  //   __ atomic_xchgal(prev, newv, addr);
  //   __ mov(r0, prev);
  //   __ ret(lr);
  // }
  // aarch64_atomic_cmpxchg_1_impl = (aarch64_atomic_cmpxchg_1_type)__ pc();
  // {
  //   Register prev = r3, ptr = c_rarg0, compare_val = c_rarg1,
  //     exchange_val = c_rarg2;
  //   __ cmpxchg(ptr, compare_val, exchange_val,
  //              MacroAssembler::byte,
  //              /*acquire*/false, /*release*/false, /*weak*/false,
  //              prev);
  //   __ movw(r0, prev);
  //   __ ret(lr);
  // }
  // aarch64_atomic_cmpxchg_4_impl = (aarch64_atomic_cmpxchg_4_type)__ pc();
  // {
  //   Register prev = r3, ptr = c_rarg0, compare_val = c_rarg1,
  //     exchange_val = c_rarg2;
  //   __ cmpxchg(ptr, compare_val, exchange_val,
  //              MacroAssembler::word,
  //              /*acquire*/false, /*release*/false, /*weak*/false,
  //              prev);
  //   __ movw(r0, prev);
  //   __ ret(lr);
  // }
  // aarch64_atomic_cmpxchg_8_impl = (aarch64_atomic_cmpxchg_8_type)__ pc();
  // {
  //   Register prev = r3, ptr = c_rarg0, compare_val = c_rarg1,
  //     exchange_val = c_rarg2;
  //   __ cmpxchg(ptr, compare_val, exchange_val,
  //              MacroAssembler::xword,
  //              /*acquire*/false, /*release*/false, /*weak*/false,
  //              prev);
  //   __ mov(r0, prev);
  //   __ ret(lr);
  // }

        .text

        .globl aarch64_atomic_fetch_add_8_default_impl
aarch64_atomic_fetch_add_8_default_impl:
0:      ldaxr	x2, [x0]
	add	x8, x2, x1
	stlxr	w9, x8, [x0]
	cbnz	w9, 0b
	mov	x0, x2
	ret

        .globl aarch64_atomic_fetch_add_4_default_impl
aarch64_atomic_fetch_add_4_default_impl:
0:      ldaxr	w2, [x0]
	add	w8, w2, w1
	stlxr	w9, w8, [x0]
	cbnz	w9, 0b
	mov	w0, w2
	ret

        .globl aarch64_atomic_xchg_4_default_impl
aarch64_atomic_xchg_4_default_impl:
0:      ldaxr	w2, [x0]
	stlxr	w8, w1, [x0]
	cbnz	w8, 0b
	mov	w0, w2
	ret

        .globl aarch64_atomic_xchg_8_default_impl
aarch64_atomic_xchg_8_default_impl:
0:      ldaxr	x2, [x0]
	stlxr	w8, x1, [x0]
	cbnz	w8, 0b
	mov	x0, x2
	ret

        .globl aarch64_atomic_cmpxchg_1_default_impl
aarch64_atomic_cmpxchg_1_default_impl:
0:      ldxrb	w3, [x0]
	eor	w8, w3, w1
	tst	x8, #0xff
	b.ne	1f
        stxrb	w8, w2, [x0]
	cbnz	w8, 0b
1:      mov	w0, w3
	ret

        .globl aarch64_atomic_cmpxchg_4_default_impl
aarch64_atomic_cmpxchg_4_default_impl:
0:      ldxr	w3, [x0]
	cmp	w3, w1
	b.ne	1f
	stxr	w8, w2, [x0]
	cbnz	w8, 0b
1:      mov	w0, w3
	ret

        .globl aarch64_atomic_cmpxchg_8_default_impl
aarch64_atomic_cmpxchg_8_default_impl:
0:      ldxr	x3, [x0]
	cmp	x3, x1
	b.ne	1f
	stxr	w8, x2, [x0]
	cbnz	w8, 0b
1:      mov	x0, x3
	ret
