# 
# Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
# 

        # TODO-AARCH64
        
        # NOTE WELL!  The _Copy functions are called directly
        # from server-compiler-generated code via CallLeafNoFP,
        # which means that they *must* either not use floating
        # point or use it in the same manner as does the server
        # compiler.
        
        .globl _Copy_conjoint_bytes
        .type _Copy_conjoint_bytes, %function
        .globl _Copy_arrayof_conjoint_bytes
        .type _Copy_arrayof_conjoint_bytes, %function
        .globl _Copy_disjoint_words
        .type _Copy_disjoint_words, %function
        .globl _Copy_conjoint_words
        .type _Copy_conjoint_words, %function
        .globl _Copy_conjoint_jshorts_atomic
        .type _Copy_conjoint_jshorts_atomic, %function
        .globl _Copy_arrayof_conjoint_jshorts
        .type _Copy_arrayof_conjoint_jshorts, %function
        .globl _Copy_conjoint_jints_atomic
        .type _Copy_conjoint_jints_atomic, %function
        .globl _Copy_arrayof_conjoint_jints
        .type _Copy_arrayof_conjoint_jints, %function
        .globl _Copy_conjoint_jlongs_atomic
        .type _Copy_conjoint_jlongs_atomic, %function
        .globl _Copy_arrayof_conjoint_jlongs
        .type _Copy_arrayof_conjoint_jlongs, %function

        .text
        .globl  SpinPause
        .type SpinPause, %function
SpinPause:
        yield
        ret

        # Support for void Copy::conjoint_bytes(void* from,
        #                                       void* to,
        #                                       size_t count)
_Copy_conjoint_bytes:
        hlt 1002

        # Support for void Copy::arrayof_conjoint_bytes(void* from,
        #                                               void* to,
        #                                               size_t count)
_Copy_arrayof_conjoint_bytes:
        hlt 1003


        # Support for void Copy::disjoint_words(void* from,
        #                                       void* to,
        #                                       size_t count)
_Copy_disjoint_words:
        # These and further memory prefetches may hit out of array ranges.
        # Experiments showed that prefetching of inaccessible memory doesn't result in exceptions.
        prfm    pldl1keep,  [x0, #0]
        prfm    pstl1keep,  [x1, #0]
        prfm    pldl1keep,  [x0, #64]
        prfm    pstl1keep,  [x1, #64]

        subs    x18, x2,  #128
        b.ge    dw_large

dw_lt_128:
        # Copy [x0, x0 + x2) to [x1, x1 + x2)
        
        adr     x15,  dw_tail_table_base
        and     x16,  x2,  #~8

        # Calculate address to jump and store it to x15:
        #   Each pair of instructions before dw_tail_table_base copies 16 bytes.
        #   x16 is count of bytes to copy aligned down by 16.
        #   So x16/16 pairs of instructions should be executed. 
        #   Each pair takes 8 bytes, so x15 = dw_tail_table_base - (x16/16)*8 = x15 - x16/2
        sub     x15,  x15, x16, lsr #1
        prfm    plil1keep, [x15]
    
        add     x17,  x0,  x2
        add     x18,  x1,  x2

        # If x2 = x16 + 8, then copy 8 bytes and x16 bytes after that.
        # Otherwise x2 = x16, so proceed to copy x16 bytes.
        tbz     x2, #3, dw_lt_128_even
        ldr     x3, [x0]
        str     x3, [x1]
dw_lt_128_even:
        # Copy [x17 - x16, x17) to [x18 - x16, x18)
        # x16 is aligned by 16 and less than 128

        # Execute (x16/16) ldp-stp pairs; each pair copies 16 bytes
        br      x15

        ldp     x3,  x4,  [x17, #-112]
        stp     x3,  x4,  [x18, #-112]
        ldp     x5,  x6,  [x17, #-96]
        stp     x5,  x6,  [x18, #-96]
        ldp     x7,  x8,  [x17, #-80]
        stp     x7,  x8,  [x18, #-80]
        ldp     x9,  x10, [x17, #-64]
        stp     x9,  x10, [x18, #-64]
        ldp     x11, x12, [x17, #-48]
        stp     x11, x12, [x18, #-48]
        ldp     x13, x14, [x17, #-32]
        stp     x13, x14, [x18, #-32]
        ldp     x15, x16, [x17, #-16]
        stp     x15, x16, [x18, #-16]
dw_tail_table_base:
        ret

.p2align  6
.rept   12
        nop
.endr
dw_large:
        # x18 >= 0;
        # Copy [x0, x0 + x18 + 128) to [x1, x1 + x18 + 128)

        ldp     x3,  x4,  [x0], #64
        ldp     x5,  x6,  [x0, #-48]
        ldp     x7,  x8,  [x0, #-32]
        ldp     x9,  x10, [x0, #-16]

        # Before and after each iteration of loop registers x3-x10 contain [x0 - 64, x0),
        # and x1 is a place to copy this data;
        # x18 contains number of bytes to be stored minus 128

        # Exactly 16 instructions from p2align, so dw_loop starts from cache line boundary
        # Checking it explictly by aligning with "hlt 1000" instructions 
.p2alignl  6, 0xd4407d00
dw_loop:
        prfm    pldl1keep,  [x0, #64]
        # Next line actually hurted memory copy performance (for interpreter) - JDK-8078120
        # prfm    pstl1keep,  [x1, #64]

        subs    x18, x18, #64

        stp     x3,  x4,  [x1, #0]
        ldp     x3,  x4,  [x0, #0]
        stp     x5,  x6,  [x1, #16]
        ldp     x5,  x6,  [x0, #16]
        stp     x7,  x8,  [x1, #32]
        ldp     x7,  x8,  [x0, #32]
        stp     x9,  x10, [x1, #48]
        ldp     x9,  x10, [x0, #48]
        
        add     x1,  x1,  #64
        add     x0,  x0,  #64

        b.ge    dw_loop

        # 13 instructions from dw_loop, so the loop body hits into one cache line

dw_loop_end:
        adds    x2,  x18, #64

        stp     x3,  x4,  [x1], #64
        stp     x5,  x6,  [x1, #-48]
        stp     x7,  x8,  [x1, #-32]
        stp     x9,  x10, [x1, #-16]

        # Increased x18 by 64, but stored 64 bytes, so x2 contains exact number of bytes to be stored

        # If this number is not zero, also copy remaining bytes
        b.ne    dw_lt_128
        ret


        # Support for void Copy::conjoint_words(void* from,
        #                                       void* to,
        #                                       size_t count)
_Copy_conjoint_words:
        subs    x3, x1, x0
        # hi condition is met <=> from < to
        ccmp    x2, x3, #0, hi
        # hi condition is met <=> (from < to) and (to - from < count)
        # otherwise _Copy_disjoint_words may be used, because it performs forward copying,
        # so it also works when ranges overlap but to <= from
        b.ls    _Copy_disjoint_words

        # Overlapping case should be the rare one, it does not worth optimizing

        ands    x3,  x2,  #~8
        # x3 is count aligned down by 2*wordSize
        add     x0,  x0,  x2
        add     x1,  x1,  x2
        sub     x3,  x3,  #16
        # Skip loop if 0 or 1 words
        b.eq    cw_backward_loop_end

        # x3 >= 0
        # Copy [x0 - x3 - 16, x0) to [x1 - x3 - 16, x1) backward
cw_backward_loop:
        subs    x3,  x3,  #16
        ldp     x4,  x5,  [x0, #-16]!
        stp     x4,  x5,  [x1, #-16]!
        b.ge    cw_backward_loop

cw_backward_loop_end:
        # Copy remaining 0 or 1 words
        tbz     x2,  #3,  cw_finish
        ldr     x3, [x0, #-8]
        str     x3, [x1, #-8]

cw_finish:
        ret


        # Support for void Copy::conjoint_jshorts_atomic(void* from,
        #                                                void* to,
        #                                                size_t count)
_Copy_conjoint_jshorts_atomic:
        add     x17, x0, x2
        add     x18, x1, x2

        subs    x3, x1, x0
        # hi is met <=> (from < to) and (to - from < count)
        ccmp    x2, x3, #0, hi
        b.hi    cs_backward
        
        subs    x3, x2, #14
        b.ge    cs_forward_loop

        # Copy x2 < 14 bytes from x0 to x1
cs_forward_lt14:
        ands    x7, x2, #7
        tbz     x2, #3, cs_forward_lt8
        ldrh    w3, [x0, #0]
        ldrh    w4, [x0, #2]
        ldrh    w5, [x0, #4]
        ldrh    w6, [x0, #6]

        strh    w3, [x1, #0]
        strh    w4, [x1, #2]
        strh    w5, [x1, #4]
        strh    w6, [x1, #6]

        # Copy x7 < 8 bytes from x17 - x7 to x18 - x7
cs_forward_lt8:
        b.eq    cs_forward_0
        cmp     x7, #4
        b.lt    cs_forward_2
        b.eq    cs_forward_4

cs_forward_6:
        ldrh    w3, [x17, #-6]
        strh    w3, [x18, #-6]
cs_forward_4:
        ldrh    w4, [x17, #-4]
        strh    w4, [x18, #-4]
cs_forward_2:
        ldrh    w5, [x17, #-2]
        strh    w5, [x18, #-2]
cs_forward_0:
        ret


        # Copy [x0, x0 + x3 + 14) to [x1, x1 + x3 + 14)
        # x3 >= 0
.p2align 6
cs_forward_loop:
        subs    x3, x3, #14
        
        ldrh    w4, [x0], #14
        ldrh    w5, [x0, #-12]
        ldrh    w6, [x0, #-10]
        ldrh    w7, [x0, #-8]
        ldrh    w8, [x0, #-6]
        ldrh    w9, [x0, #-4]
        ldrh    w10, [x0, #-2]

        strh    w4, [x1], #14
        strh    w5, [x1, #-12]
        strh    w6, [x1, #-10]
        strh    w7, [x1, #-8]
        strh    w8, [x1, #-6]
        strh    w9, [x1, #-4]
        strh    w10, [x1, #-2]

        b.ge    cs_forward_loop
        # Exactly 16 instruction from cs_forward_loop, so loop fits into one cache line

        adds    x2, x3, #14
        # x2 bytes should be copied from x0 to x1
        b.ne    cs_forward_lt14
        ret
        
        # Very similar to forward copying
cs_backward:
        subs    x3, x2, #14
        b.ge    cs_backward_loop

cs_backward_lt14:
        ands    x7, x2, #7
        tbz     x2, #3, cs_backward_lt8

        ldrh    w3, [x17, #-8]
        ldrh    w4, [x17, #-6]
        ldrh    w5, [x17, #-4]
        ldrh    w6, [x17, #-2]
        
        strh    w3, [x18, #-8]
        strh    w4, [x18, #-6]
        strh    w5, [x18, #-4]
        strh    w6, [x18, #-2]

cs_backward_lt8:
        b.eq    cs_backward_0
        cmp     x7, #4
        b.lt    cs_backward_2
        b.eq    cs_backward_4

cs_backward_6:
        ldrh    w3, [x0, #4]
        strh    w3, [x1, #4]

cs_backward_4:
        ldrh    w4, [x0, #2]
        strh    w4, [x1, #2]

cs_backward_2:
        ldrh    w5, [x0, #0]
        strh    w5, [x1, #0]

cs_backward_0:
        ret


.p2align 6
cs_backward_loop:
        subs    x3, x3, #14

        ldrh    w4, [x17, #-14]!
        ldrh    w5, [x17, #2]
        ldrh    w6, [x17, #4]
        ldrh    w7, [x17, #6]
        ldrh    w8, [x17, #8]
        ldrh    w9, [x17, #10]
        ldrh    w10, [x17, #12]

        strh    w4, [x18, #-14]!
        strh    w5, [x18, #2]
        strh    w6, [x18, #4]
        strh    w7, [x18, #6]
        strh    w8, [x18, #8]
        strh    w9, [x18, #10]
        strh    w10, [x18, #12]

        b.ge    cs_backward_loop
        adds    x2, x3, #14
        b.ne    cs_backward_lt14
        ret


        # Support for void Copy::arrayof_conjoint_jshorts(void* from,
        #                                                 void* to,
        #                                                 size_t count)
_Copy_arrayof_conjoint_jshorts:
        hlt 1007


        # Support for void Copy::conjoint_jlongs_atomic(jlong* from,
        #                                               jlong* to,
        #                                               size_t count)
_Copy_conjoint_jlongs_atomic:
_Copy_arrayof_conjoint_jlongs:
        hlt 1009


        # Support for void Copy::conjoint_jints_atomic(void* from,
        #                                              void* to,
        #                                              size_t count)
_Copy_conjoint_jints_atomic:
_Copy_arrayof_conjoint_jints:
        # These and further memory prefetches may hit out of array ranges.
        # Experiments showed that prefetching of inaccessible memory doesn't result in exceptions.
        prfm    pldl1keep,  [x0, #0]
        prfm    pstl1keep,  [x1, #0]
        prfm    pldl1keep,  [x0, #32]
        prfm    pstl1keep,  [x1, #32]

        subs    x3, x1, x0
        # hi condition is met <=> from < to
        ccmp    x2, x3, #0, hi
        # hi condition is met <=> (from < to) and (to - from < count)
        b.hi    ci_backward

        subs    x18, x2,  #64
        b.ge    ci_forward_large

ci_forward_lt_64:
        # Copy [x0, x0 + x2) to [x1, x1 + x2)
        
        adr     x15,  ci_forward_tail_table_base
        and     x16,  x2,  #~4

        # Calculate address to jump and store it to x15:
        #   Each pair of instructions before ci_forward_tail_table_base copies 8 bytes.
        #   x16 is count of bytes to copy aligned down by 8.
        #   So x16/8 pairs of instructions should be executed. 
        #   Each pair takes 8 bytes, so x15 = ci_forward_tail_table_base - (x16/8)*8 = x15 - x16
        sub     x15,  x15, x16
        prfm    plil1keep, [x15]
    
        add     x17,  x0,  x2
        add     x18,  x1,  x2

        # If x2 = x16 + 4, then copy 4 bytes and x16 bytes after that.
        # Otherwise x2 = x16, so proceed to copy x16 bytes.
        tbz     x2, #2, ci_forward_lt_64_even
        ldr     w3, [x0]
        str     w3, [x1]
ci_forward_lt_64_even:
        # Copy [x17 - x16, x17) to [x18 - x16, x18)
        # x16 is aligned by 8 and less than 64

        # Execute (x16/8) ldp-stp pairs; each pair copies 8 bytes
        br      x15

        ldp     w3,  w4,  [x17, #-56]
        stp     w3,  w4,  [x18, #-56]
        ldp     w5,  w6,  [x17, #-48]
        stp     w5,  w6,  [x18, #-48]
        ldp     w7,  w8,  [x17, #-40]
        stp     w7,  w8,  [x18, #-40]
        ldp     w9,  w10, [x17, #-32]
        stp     w9,  w10, [x18, #-32]
        ldp     w11, w12, [x17, #-24]
        stp     w11, w12, [x18, #-24]
        ldp     w13, w14, [x17, #-16]
        stp     w13, w14, [x18, #-16]
        ldp     w15, w16, [x17, #-8]
        stp     w15, w16, [x18, #-8]
ci_forward_tail_table_base:
        ret

.p2align  6
.rept   12
        nop
.endr
ci_forward_large:
        # x18 >= 0;
        # Copy [x0, x0 + x18 + 64) to [x1, x1 + x18 + 64)

        ldp     w3,  w4,  [x0], #32
        ldp     w5,  w6,  [x0, #-24]
        ldp     w7,  w8,  [x0, #-16]
        ldp     w9,  w10, [x0, #-8]

        # Before and after each iteration of loop registers w3-w10 contain [x0 - 32, x0),
        # and x1 is a place to copy this data;
        # x18 contains number of bytes to be stored minus 64

        # Exactly 16 instructions from p2align, so ci_forward_loop starts from cache line boundary
        # Checking it explictly by aligning with "hlt 1000" instructions 
.p2alignl  6, 0xd4407d00
ci_forward_loop:
        prfm    pldl1keep,  [x0, #32]
        prfm    pstl1keep,  [x1, #32]

        subs    x18, x18, #32

        stp     w3,  w4,  [x1, #0]
        ldp     w3,  w4,  [x0, #0]
        stp     w5,  w6,  [x1, #8]
        ldp     w5,  w6,  [x0, #8]
        stp     w7,  w8,  [x1, #16]
        ldp     w7,  w8,  [x0, #16]
        stp     w9,  w10, [x1, #24]
        ldp     w9,  w10, [x0, #24]
        
        add     x1,  x1,  #32
        add     x0,  x0,  #32

        b.ge    ci_forward_loop

        # 14 instructions from ci_forward_loop, so the loop body hits into one cache line

ci_forward_loop_end:
        adds    x2,  x18, #32

        stp     w3,  w4,  [x1], #32
        stp     w5,  w6,  [x1, #-24]
        stp     w7,  w8,  [x1, #-16]
        stp     w9,  w10, [x1, #-8]

        # Increased x18 by 32, but stored 32 bytes, so x2 contains exact number of bytes to be stored

        # If this number is not zero, also copy remaining bytes
        b.ne    ci_forward_lt_64
        ret

ci_backward:

        # Overlapping case should be the rare one, it does not worth optimizing

        ands    x3,  x2,  #~4
        # x3 is count aligned down by 2*jintSize
        add     x0,  x0,  x2
        add     x1,  x1,  x2
        sub     x3,  x3,  #8
        # Skip loop if 0 or 1 jints
        b.eq    ci_backward_loop_end

        # x3 >= 0
        # Copy [x0 - x3 - 8, x0) to [x1 - x3 - 8, x1) backward
ci_backward_loop:
        subs    x3,  x3,  #8
        ldp     w4,  w5,  [x0, #-8]!
        stp     w4,  w5,  [x1, #-8]!
        b.ge    ci_backward_loop

ci_backward_loop_end:
        # Copy remaining 0 or 1 jints
        tbz     x2,  #2,  ci_backward_finish
        ldr     w3, [x0, #-4]
        str     w3, [x1, #-4]

ci_backward_finish:
        ret
