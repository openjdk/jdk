//
// Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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


  // Support for u8 os::setup_fpu()
      .inline _solaris_raw_setup_fpu,1
      movl     0(%esp), %eax
      fldcw    (%eax)
      .end

  // The argument size of each inline directive is ignored by the compiler
  // and is set to 0 for compatibility reason.

  // Get the raw thread ID from %gs:0
      .inline _raw_thread_id,0
      movl     %gs:0, %eax 
      .end

  // Get current fp
      .inline _get_current_fp,0
      .volatile
      movl     %ebp, %eax 
      .end

  // Support for jint Atomic::add(jint inc, volatile jint* dest)
  // An additional bool (os::is_MP()) is passed as the last argument.
      .inline _Atomic_add,3
      movl     0(%esp), %eax   // inc
      movl     4(%esp), %edx   // dest
      movl     %eax, %ecx
      cmpl     $0, 8(%esp)     // MP test
      je       1f
      lock
1:    xaddl    %eax, (%edx)
      addl     %ecx, %eax
      .end

  // Support for jint Atomic::xchg(jint exchange_value, volatile jint* dest).
      .inline _Atomic_xchg,2
      movl     0(%esp), %eax   // exchange_value
      movl     4(%esp), %ecx   // dest
      xchgl    (%ecx), %eax
      .end

  // Support for jint Atomic::cmpxchg(jint exchange_value, 
  //                                  volatile jint *dest, 
  //                                  jint compare_value)
  // An additional bool (os::is_MP()) is passed as the last argument.
      .inline _Atomic_cmpxchg,4
      movl     8(%esp), %eax   // compare_value
      movl     0(%esp), %ecx   // exchange_value
      movl     4(%esp), %edx   // dest
      cmp      $0, 12(%esp)    // MP test
      je       1f
      lock
1:    cmpxchgl %ecx, (%edx)
      .end

  // Support for jlong Atomic::cmpxchg(jlong exchange_value,
  //                                   volatile jlong* dest,
  //                                   jlong compare_value)
  // An additional bool (os::is_MP()) is passed as the last argument.
      .inline _Atomic_cmpxchg_long,6
      pushl    %ebx
      pushl    %edi
      movl     20(%esp), %eax  // compare_value (low)
      movl     24(%esp), %edx  // compare_value (high)
      movl     16(%esp), %edi  // dest
      movl     8(%esp), %ebx   // exchange_value (low)
      movl     12(%esp), %ecx  // exchange_high (high)
      cmp      $0, 28(%esp)    // MP test
      je       1f
      lock
1:    cmpxchg8b (%edi)
      popl     %edi
      popl     %ebx
      .end

  // Support for void Atomic::load(volatile jlong* src, volatile jlong* dest).
      .inline _Atomic_load_long,2
      movl     0(%esp), %eax   // src
      fildll    (%eax)
      movl     4(%esp), %eax   // dest
      fistpll   (%eax)
      .end


  // Support for OrderAccess::acquire()
      .inline _OrderAccess_acquire,0
      movl     0(%esp), %eax
      .end

  // Support for OrderAccess::fence()
      .inline _OrderAccess_fence,0
      lock
      addl     $0, (%esp)
      .end

  // Support for u2 Bytes::swap_u2(u2 x)
      .inline _raw_swap_u2,1
      movl     0(%esp), %eax
      xchgb    %al, %ah
      .end

  // Support for u4 Bytes::swap_u4(u4 x)
      .inline _raw_swap_u4,1
      movl     0(%esp), %eax
      bswap    %eax
      .end

  // Support for u8 Bytes::swap_u8_base(u4 x, u4 y)
      .inline _raw_swap_u8,2
      movl     4(%esp), %eax   // y
      movl     0(%esp), %edx   // x
      bswap    %eax
      bswap    %edx
      .end
