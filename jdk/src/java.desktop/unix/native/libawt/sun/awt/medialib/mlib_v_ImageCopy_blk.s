!
!
! 
! Copyright 2000 Sun Microsystems, Inc.  All Rights Reserved.
! DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
!
! This code is free software; you can redistribute it and/or modify it
! under the terms of the GNU General Public License version 2 only, as
! published by the Free Software Foundation.  Oracle designates this
! particular file as subject to the "Classpath" exception as provided
! by Oracle in the LICENSE file that accompanied this code.
!
! This code is distributed in the hope that it will be useful, but WITHOUT
! ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
! FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
! version 2 for more details (a copy is included in the LICENSE file that
! accompanied this code).
!
! You should have received a copy of the GNU General Public License version
! 2 along with this work; if not, write to the Free Software Foundation,
! Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
!
! Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
! or visit www.oracle.com if you need additional information or have any
! questions.
!


! FUNCTION
!      mlib_v_ImageCopy_blk   - Copy an image into another 
!				(with Block Load/Store)
!
! SYNOPSIS
!      void mlib_v_ImageCopy_blk(void *src,
!                                void *dst, 
!                                int size);
!
! ARGUMENT
!      src     source image data
!      dst     destination image data
!      size    image size in bytes
!
! NOTES
!      src and dst must point to 64-byte aligned addresses
!      size must be multiple of 64
!
! DESCRIPTION
!      dst = src
!

#include "vis_asi.h"

! Minimum size of stack frame according to SPARC ABI
#define MINFRAME        96

! ENTRY provides the standard procedure entry code
#define ENTRY(x) \
	.align  4; \
	.global x; \
x:

! SET_SIZE trails a function and sets the size for the ELF symbol table
#define SET_SIZE(x) \
	.size   x, (.-x)

! SPARC have four integer register groups. i-registers %i0 to %i7
! hold input data. o-registers %o0 to %o7 hold output data. l-registers
! %l0 to %l7 hold local data. g-registers %g0 to %g7 hold global data.
! Note that %g0 is alway zero, write to it has no program-visible effect.

! When calling an assembly function, the first 6 arguments are stored
! in i-registers from %i0 to %i5. The rest arguments are stored in stack.
! Note that %i6 is reserved for stack pointer and %i7 for return address.

! Only the first 32 f-registers can be used as 32-bit registers.
! The last 32 f-registers can only be used as 16 64-bit registers.

#define src     %i0
#define dst     %i1
#define sz      %i2

!frame pointer  %i6
!return addr    %i7

!stack pointer  %o6
!call link      %o7

#define sa      %l0
#define da      %l1
#define se      %l2
#define ns      %l3

#define O0      %f16
#define O1      %f18
#define O2      %f20
#define O3      %f22
#define O4      %f24
#define O5      %f26
#define O6      %f28
#define O7      %f30

#define A0      %f32
#define A1      %f34
#define A2      %f36
#define A3      %f38
#define A4      %f40
#define A5      %f42
#define A6      %f44
#define A7      %f46

#define B0      %f48
#define B1      %f50
#define B2      %f52
#define B3      %f54
#define B4      %f56
#define B5      %f58
#define B6      %f60
#define B7      %f62

#define USE_BLD
#define USE_BST

#define MEMBAR_BEFORE_BLD        membar  #StoreLoad
#define MEMBAR_AFTER_BLD         membar  #StoreLoad

#ifdef USE_BLD
#define BLD_A0                                  \
        ldda    [sa]ASI_BLK_P,A0;               \
        cmp     sa,se;                          \
        blu,pt  %icc,1f;                        \
        inc     64,sa;                          \
        dec     64,sa;                          \
1:
#else
#define BLD_A0                                  \
        ldd     [sa +  0],A0;                   \
        ldd     [sa +  8],A1;                   \
        ldd     [sa + 16],A2;                   \
        ldd     [sa + 24],A3;                   \
        ldd     [sa + 32],A4;                   \
        ldd     [sa + 40],A5;                   \
        ldd     [sa + 48],A6;                   \
        ldd     [sa + 56],A7;                   \
        cmp     sa,se;                          \
        blu,pt  %icc,1f;                        \
        inc     64,sa;                          \
        dec     64,sa;                          \
1:
#endif

#ifdef USE_BLD
#define BLD_B0                                  \
        ldda    [sa]ASI_BLK_P,B0;               \
        cmp     sa,se;                          \
        blu,pt  %icc,1f;                        \
        inc     64,sa;                          \
        dec     64,sa;                          \
1:
#else
#define BLD_B0                                  \
        ldd     [sa +  0],B0;                   \
        ldd     [sa +  8],B1;                   \
        ldd     [sa + 16],B2;                   \
        ldd     [sa + 24],B3;                   \
        ldd     [sa + 32],B4;                   \
        ldd     [sa + 40],B5;                   \
        ldd     [sa + 48],B6;                   \
        ldd     [sa + 56],B7;                   \
        cmp     sa,se;                          \
        blu,pt  %icc,1f;                        \
        inc     64,sa;                          \
        dec     64,sa;                          \
1:
#endif

#ifdef USE_BST
#define BST                                     \
        stda    O0,[da]ASI_BLK_P;               \
        inc     64,da;                          \
        deccc   ns;                             \
        ble,pn  %icc,mlib_v_ImageCopy_end;	\
        nop
#else
#define BST                                     \
        std     O0,[da +  0];                   \
        std     O1,[da +  8];                   \
        std     O2,[da + 16];                   \
        std     O3,[da + 24];                   \
        std     O4,[da + 32];                   \
        std     O5,[da + 40];                   \
        std     O6,[da + 48];                   \
        std     O7,[da + 56];                   \
        inc     64,da;                          \
        deccc   ns;                             \
        ble,pn  %icc,mlib_v_ImageCopy_end;	\
        nop
#endif

#define COPY_A0					\
        fmovd A0, O0;                           \
        fmovd A1, O1;                           \
        fmovd A2, O2;                           \
        fmovd A3, O3;                           \
        fmovd A4, O4;                           \
        fmovd A5, O5;                           \
        fmovd A6, O6;                           \
        fmovd A7, O7;

#define COPY_B0					\
        fmovd B0, O0;                           \
        fmovd B1, O1;                           \
        fmovd B2, O2;                           \
        fmovd B3, O3;                           \
        fmovd B4, O4;                           \
        fmovd B5, O5;                           \
        fmovd B6, O6;                           \
        fmovd B7, O7;

        .section        ".text",#alloc,#execinstr

        ENTRY(mlib_v_ImageCopy_blk)	! function name

        save    %sp,-MINFRAME,%sp	! reserve space for stack
                                        ! and adjust register window
! do some error checking
        tst     sz                      ! size > 0
        ble,pn  %icc,mlib_v_ImageCopy_ret

! calculate loop count
        sra     sz,6,ns                 ! 64 bytes per loop

        add     src,sz,se               ! end address of source
        mov     src,sa
        mov     dst,da
                                        ! issue memory barrier instruction
        MEMBAR_BEFORE_BLD               ! to ensure all previous memory load
                                        ! and store has completed

        BLD_A0
        BLD_B0                          ! issue the 2nd block load instruction
                                        ! to synchronize with returning data
mlib_v_ImageCopy_bgn:

        COPY_A0				! process data returned by BLD_A0
        BLD_A0                          ! block load and sync data from BLD_B0
        BST                             ! block store data from BLD_A0

        COPY_B0				! process data returned by BLD_B0
        BLD_B0                          ! block load and sync data from BLD_A0
        BST                             ! block store data from BLD_B0

        bg,pt   %icc,mlib_v_ImageCopy_bgn

mlib_v_ImageCopy_end:
                                        ! issue memory barrier instruction
        MEMBAR_AFTER_BLD                ! to ensure all previous memory load
                                        ! and store has completed.
mlib_v_ImageCopy_ret:
        ret                             ! return
        restore                         ! restore register window

        SET_SIZE(mlib_v_ImageCopy_blk)
