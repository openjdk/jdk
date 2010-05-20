//
// Copyright 2004-2009 Sun Microsystems, Inc.  All Rights Reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
// CA 95054 USA or visit www.sun.com if you need additional information or
// have any questions.
//  
//

  // The argument size of each inline directive is ignored by the compiler
  // and is set to the number of arguments as documentation.

  // Get the raw thread ID from %gs:0
      .inline _raw_thread_id,0
      movq     %fs:0, %rax 
      .end

  // Get the frame pointer from current frame.
      .inline _get_current_fp,0
      .volatile
      movq     %rbp, %rax 
      .end

  // Support for jint Atomic::add(jint add_value, volatile jint* dest)
      .inline _Atomic_add,2
      movl     %edi, %eax      // save add_value for return
      lock
      xaddl    %edi, (%rsi)
      addl     %edi, %eax
      .end

  // Support for jlong Atomic::add(jlong add_value, volatile jlong* dest)
      .inline _Atomic_add_long,2
      movq     %rdi, %rax      // save add_value for return
      lock
      xaddq    %rdi, (%rsi)
      addq     %rdi, %rax
      .end

  // Support for jint Atomic::xchg(jint exchange_value, volatile jint* dest).
      .inline _Atomic_xchg,2
      xchgl    (%rsi), %edi
      movl     %edi, %eax
      .end

  // Support for jlong Atomic::xchg(jlong exchange_value, volatile jlong* dest).
      .inline _Atomic_xchg_long,2
      xchgq    (%rsi), %rdi
      movq     %rdi, %rax
      .end

  // Support for jint Atomic::cmpxchg(jint exchange_value, 
  //                                  volatile jint *dest, 
  //                                  jint compare_value)
      .inline _Atomic_cmpxchg,3
      movl     %edx, %eax      // compare_value
      lock
      cmpxchgl %edi, (%rsi)
      .end

  // Support for jlong Atomic::cmpxchg(jlong exchange_value,
  //                                   volatile jlong* dest,
  //                                   jlong compare_value)
      .inline _Atomic_cmpxchg_long,3
      movq     %rdx, %rax      // compare_value
      lock
      cmpxchgq %rdi, (%rsi)
      .end

  // Support for OrderAccess::acquire()
      .inline _OrderAccess_acquire,0
      movl     0(%rsp), %eax
      .end

  // Support for OrderAccess::fence()
      .inline _OrderAccess_fence,0
      lock
      addl     $0, (%rsp)
      .end

  // Support for u2 Bytes::swap_u2(u2 x)
      .inline _raw_swap_u2,1
      movw     %di, %ax
      rorw     $8, %ax
      .end

  // Support for u4 Bytes::swap_u4(u4 x)
      .inline _raw_swap_u4,1
      movl     %edi, %eax
      bswapl   %eax
      .end

  // Support for u8 Bytes::swap_u8(u8 x)
      .inline _raw_swap_u8,1
      movq     %rdi, %rax
      bswapq   %rax
      .end

  // Support for void Prefetch::read
      .inline _Prefetch_read,2
      prefetcht0 (%rdi, %rsi, 1)
      .end

  // Support for void Prefetch::write
  // We use prefetcht0 because em64t doesn't support prefetchw.
  // prefetchw is a 3dnow instruction.
      .inline _Prefetch_write,2
      prefetcht0 (%rdi, %rsi, 1)
      .end
