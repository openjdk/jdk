#
# Copyright (c) 2012, Red Hat. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

# Routines used to enable x86 VM C++ code to invoke JIT-compiled ARM code
# -- either Java methods or generated stub -- and to allow JIT-compiled
# ARM code to invoke x86 VM C++ code
#
# the code for aarch64_stub_prolog below can be copied into the start
# of the ARM code buffer and patched with a link to the
# C++ routine which starts execution on the simulator. the ARM
# code can be generated immediately following the copied code.

#ifdef BUILTIN_SIM

	.data
        .globl setup_arm_sim,
	.type  setup_arm_sim,@function
        .globl get_alt_stack,
	.type  get_alt_stack,@function
        .globl aarch64_stub_prolog
        .p2align  4
aarch64_stub_prolog:
	// entry point
4:	lea 1f(%rip), %r11
	mov (%r11), %r10
	mov (%r10), %r10
	jmp *%r10
	.p2align 4
1:
	.set entry_offset, . - 1b
	.quad aarch64_prolog_ptr
	// 64 bit int used to idenitfy called fn arg/return types
	.set calltype_offset, . - 1b
	.quad 0
	// arm JIT code follows the stub
	.set arm_code_offset, . - 1b
	.size aarch64_stub_prolog, .-aarch64_stub_prolog
aarch64_stub_prolog_end:

	.text
aarch64_prolog_ptr:
	.quad aarch64_prolog

        .globl aarch64_prolog
aarch64_prolog:
	.cfi_startproc
	pushq	%rbp
	.cfi_def_cfa_offset 16
	.cfi_offset 6, -16
	movq	%rsp, %rbp
	.cfi_def_cfa_register 6
	// save all registers used to pass args
	sub $8, %rsp
	movd %xmm7, (%rsp)
	sub $8, %rsp
	movd %xmm6, (%rsp)
	sub $8, %rsp
	movd %xmm5, (%rsp)
	sub $8, %rsp
	movd %xmm4, (%rsp)
	sub $8, %rsp
	movd %xmm3, (%rsp)
	sub $8, %rsp
	movd %xmm2, (%rsp)
	sub $8, %rsp
	movd %xmm1, (%rsp)
	sub $8, %rsp
	movd %xmm0, (%rsp)
	push %r9
	push %r8
	push %rcx
	push %rdx
	push %rsi
	push %rdi
	// save rax -- this stack slot will be rewritten with a
	// return value if needed
	push %rax
	// temporarily save r11 while we find the other stack
	push %r11
	// retrieve alt stack
	call get_alt_stack@PLT
	pop %r11
	// push start of arm code
	lea (arm_code_offset)(%r11), %rsi
	push %rsi
	// load call type code in arg reg 1
	mov (calltype_offset)(%r11), %rsi
	// load current stack pointer in arg reg 0
	mov %rsp, %rdi
	// switch to alt stack
	mov %rax, %rsp
	// save previous stack pointer on new stack
	push %rdi
	// 16-align the new stack pointer
	push %rdi
	// call sim setup routine
	call setup_arm_sim@PLT
	// switch back to old stack
	pop %rsp
	// pop start of arm code
	pop %rdi
	// pop rax -- either restores old value or installs return value
	pop %rax
	// pop arg registers
	pop %rdi
	pop %rsi
	pop %rdx
	pop %rcx
	pop %r8
	pop %r9
	movd (%rsp), %xmm0
	add $8, %rsp
	movd (%rsp), %xmm1
	add $8, %rsp
	movd (%rsp), %xmm2
	add $8, %rsp
	movd (%rsp), %xmm3
	add $8, %rsp
	movd (%rsp), %xmm4
	add $8, %rsp
	movd (%rsp), %xmm5
	add $8, %rsp
	movd (%rsp), %xmm6
	add $8, %rsp
	movd (%rsp), %xmm7
	add $8, %rsp
	leave
	.cfi_def_cfa 7, 8
	ret
	.cfi_endproc


        .p2align  4
get_pc:
	// get return pc in rdi and then push it back
	pop %rdi
	push %rdi
	ret

	.p2align 4
	.long
	.globl aarch64_stub_prolog_size
	.type  aarch64_stub_prolog_size,@function
aarch64_stub_prolog_size:
	leaq  aarch64_stub_prolog_end - aarch64_stub_prolog, %rax
	ret

#endif
