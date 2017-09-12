! Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
! This file contains inline procedures for VIS instructions in 64-bit mode.
!
!--------------------------------------------------------------------
! Pure edge handling instructions
!
! int vis_edge8(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge8,16
	edge8	%o0,%o1,%o0
	.end
!
! int vis_edge8l(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge8l,16
	edge8l	%o0,%o1,%o0
	.end
!
! int vis_edge16(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge16,16
	edge16	%o0,%o1,%o0
	.end
!
! int vis_edge16l(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge16l,16
	edge16l	%o0,%o1,%o0
	.end
!
! int vis_edge32(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge32,16
	edge32	%o0,%o1,%o0
	.end
!
! int vis_edge32l(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge32l,16
	edge32l	%o0,%o1,%o0
	.end

!--------------------------------------------------------------------
! Edge handling instructions with negative return values if cc set
!
! int vis_edge8cc(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge8cc,16
	edge8	%o0,%o1,%o0
	mov     0,%o1
	movgu   %xcc,-1024,%o1
	or      %o1,%o0,%o0
	.end
!
! int vis_edge8lcc(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge8lcc,16
	edge8l	%o0,%o1,%o0
	mov     0,%o1
	movgu   %xcc,-1024,%o1
	or      %o1,%o0,%o0
	.end
!
! int vis_edge16cc(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge16cc,16
	edge16	%o0,%o1,%o0
	mov     0,%o1
	movgu   %xcc,-1024,%o1
	or      %o1,%o0,%o0
	.end
!
! int vis_edge16lcc(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge16lcc,16
	edge16l	%o0,%o1,%o0
	mov     0,%o1
	movgu   %xcc,-1024,%o1
	or      %o1,%o0,%o0
	.end
!
! int vis_edge32cc(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge32cc,16
	edge32	%o0,%o1,%o0
	mov     0,%o1
	movgu   %xcc,-1024,%o1
	or      %o1,%o0,%o0
	.end
!
! int vis_edge32lcc(void */*frs1*/, void */*frs2*/);
!
	.inline vis_edge32lcc,16
	edge32l	%o0,%o1,%o0
	mov     0,%o1
	movgu   %xcc,-1024,%o1
	or      %o1,%o0,%o0
	.end

!--------------------------------------------------------------------
! Alignment instructions
!
! void *vis_alignaddr(void */*rs1*/, int /*rs2*/);
!
	.inline vis_alignaddr,12
	alignaddr	%o0,%o1,%o0
	.end
!
! void *vis_alignaddrl(void */*rs1*/, int /*rs2*/);
!
	.inline vis_alignaddrl,12
	alignaddrl	%o0,%o1,%o0
	.end
!
! double vis_faligndata(double /*frs1*/, double /*frs2*/);
!
	.inline vis_faligndata,16
	faligndata	%f0,%f2,%f0
	.end

!--------------------------------------------------------------------
! Partitioned comparison instructions
!
! int vis_fcmple16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmple16,16
	fcmple16	%f0,%f2,%o0
	.end
!
! int vis_fcmpne16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmpne16,16
	fcmpne16	%f0,%f2,%o0
	.end
!
! int vis_fcmple32(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmple32,16
	fcmple32	%f0,%f2,%o0
	.end
!
! int vis_fcmpne32(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmpne32,16
	fcmpne32	%f0,%f2,%o0
	.end
!
! int vis_fcmpgt16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmpgt16,16
	fcmpgt16	%f0,%f2,%o0
	.end
!
! int vis_fcmpeq16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmpeq16,16
	fcmpeq16	%f0,%f2,%o0
	.end
!
! int vis_fcmpgt32(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmpgt32,16
	fcmpgt32	%f0,%f2,%o0
	.end
!
! int vis_fcmpeq32(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fcmpeq32,16
	fcmpeq32	%f0,%f2,%o0
	.end

!--------------------------------------------------------------------
! Partitioned arithmetic
!
! double vis_fmul8x16(float /*frs1*/, double /*frs2*/);
!
	.inline vis_fmul8x16,12
	fmul8x16	%f1,%f2,%f0
	.end
!
! double vis_fmul8x16_dummy(float /*frs1*/, int /*dummy*/, double /*frs2*/);
!
	.inline vis_fmul8x16_dummy,16
	fmul8x16	%f1,%f4,%f0
	.end
!
! double vis_fmul8x16au(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fmul8x16au,8
	fmul8x16au	%f1,%f3,%f0
	.end
!
! double vis_fmul8x16al(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fmul8x16al,8
	fmul8x16al	%f1,%f3,%f0
	.end
!
! double vis_fmul8sux16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fmul8sux16,16
	fmul8sux16	%f0,%f2,%f0
	.end
!
! double vis_fmul8ulx16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fmul8ulx16,16
	fmul8ulx16	%f0,%f2,%f0
	.end
!
! double vis_fmuld8sux16(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fmuld8sux16,8
	fmuld8sux16	%f1,%f3,%f0
	.end
!
! double vis_fmuld8ulx16(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fmuld8ulx16,8
	fmuld8ulx16	%f1,%f3,%f0
	.end
!
! double vis_fpadd16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fpadd16,16
	fpadd16	%f0,%f2,%f0
	.end
!
! float vis_fpadd16s(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fpadd16s,8
	fpadd16s	%f1,%f3,%f0
	.end
!
! double vis_fpadd32(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fpadd32,16
	fpadd32	%f0,%f2,%f0
	.end
!
! float vis_fpadd32s(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fpadd32s,8
	fpadd32s	%f1,%f3,%f0
	.end
!
! double vis_fpsub16(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fpsub16,16
	fpsub16	%f0,%f2,%f0
	.end
!
! float vis_fpsub16s(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fpsub16s,8
	fpsub16s	%f1,%f3,%f0
	.end
!
! double vis_fpsub32(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fpsub32,16
	fpsub32	%f0,%f2,%f0
	.end
!
! float vis_fpsub32s(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fpsub32s,8
	fpsub32s	%f1,%f3,%f0
	.end

!--------------------------------------------------------------------
! Pixel packing
!
! float vis_fpack16(double /*frs2*/);
!
	.inline vis_fpack16,8
	fpack16	%f0,%f0
	.end
!
! double vis_fpack32(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fpack32,16
	fpack32	%f0,%f2,%f0
	.end
!
! float vis_fpackfix(double /*frs2*/);
!
	.inline vis_fpackfix,8
	fpackfix	%f0,%f0
	.end
!
! double vis_fpack16_pair(double /*frs2*/, double /*frs2*/);
!
	.inline vis_fpack16_pair,16
	fpack16	%f0,%f0
	fpack16	%f2,%f1
	.end
!
! double vis_fpackfix_pair(double /*frs2*/, double /*frs2*/);
!
	.inline vis_fpackfix_pair,16
	fpackfix	%f0,%f0
	fpackfix	%f2,%f1
	.end
!
! void vis_st2_fpack16(double, double, double *);
!
	.inline vis_st2_fpack16,24
 	fpack16	%f0,%f0
 	fpack16	%f2,%f1
 	st	%f0,[%o2+0]
 	st	%f1,[%o2+4]
 	.end
!
! void vis_std_fpack16(double, double, double *);
!
	.inline vis_std_fpack16,24
	fpack16	%f0,%f0
	fpack16	%f2,%f1
	std	%f0,[%o2]
	.end
!
! void vis_st2_fpackfix(double, double, double *);
!
	.inline vis_st2_fpackfix,24
 	fpackfix %f0,%f0
 	fpackfix %f2,%f1
 	st	%f0,[%o2+0]
 	st	%f1,[%o2+4]
 	.end
!
! double vis_fpack16_to_hi(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fpack16_to_hi,16
	fpack16	%f2,%f0
	.end
!
! double vis_fpack16_to_lo(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fpack16_to_lo,16
	fpack16	%f2,%f1
	.end

!--------------------------------------------------------------------
! Motion estimation
!
! double vis_pxldist64(double accum /*frd*/, double pxls1 /*frs1*/,
!		       double pxls2 /*frs2*/);
!
	.inline vis_pxldist64,24
	pdist	%f2,%f4,%f0
	.end

!--------------------------------------------------------------------
! Channel merging
!
! double vis_fpmerge(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fpmerge,8
	fpmerge	%f1,%f3,%f0
	.end

!--------------------------------------------------------------------
! Pixel expansion
!
! double vis_fexpand(float /*frs2*/);
!
	.inline vis_fexpand,4
	fexpand	%f1,%f0
	.end
!
! double vis_fexpand_hi(double /*frs2*/);
!
	.inline vis_fexpand_hi,8
	fexpand	%f0,%f0
	.end
!
! double vis_fexpand_lo(double /*frs2*/);
!
	.inline vis_fexpand_lo,8
	fexpand	%f1,%f0
	.end

!--------------------------------------------------------------------
! Bitwise logical operations
!
! double vis_fnor(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fnor,16
	fnor	%f0,%f2,%f0
	.end
!
! float vis_fnors(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fnors,8
	fnors	%f1,%f3,%f0
	.end
!
! double vis_fandnot(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fandnot,16
	fandnot1 %f0,%f2,%f0
	.end
!
! float vis_fandnots(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fandnots,8
	fandnot1s %f1,%f3,%f0
	.end
!
! double vis_fnot(double /*frs1*/);
!
	.inline vis_fnot,8
	fnot1	%f0,%f0
	.end
!
! float vis_fnots(float /*frs1*/);
!
	.inline vis_fnots,4
	fnot1s	%f1,%f0
	.end
!
! double vis_fxor(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fxor,16
	fxor	%f0,%f2,%f0
	.end
!
! float vis_fxors(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fxors,8
	fxors	%f1,%f3,%f0
	.end
!
! double vis_fnand(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fnand,16
	fnand	%f0,%f2,%f0
	.end
!
! float vis_fnands(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fnands,8
	fnands	%f1,%f3,%f0
	.end
!
! double vis_fand(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fand,16
	fand	%f0,%f2,%f0
	.end
!
! float vis_fands(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fands,8
	fands	%f1,%f3,%f0
	.end
!
! double vis_fxnor(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fxnor,16
	fxnor	%f0,%f2,%f0
	.end
!
! float vis_fxnors(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fxnors,8
	fxnors	%f1,%f3,%f0
	.end
!
! double vis_fsrc(double /*frs1*/);
!
	.inline vis_fsrc,8
	fsrc1	%f0,%f0
	.end
!
! float vis_fsrcs(float /*frs1*/);
!
	.inline vis_fsrcs,4
	fsrc1s	%f1,%f0
	.end
!
! double vis_fornot(double /*frs1*/, double /*frs2*/);
!
	.inline vis_fornot,16
	fornot1	%f0,%f2,%f0
	.end
!
! float vis_fornots(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fornots,8
	fornot1s %f1,%f3,%f0
	.end
!
! double vis_for(double /*frs1*/, double /*frs2*/);
!
	.inline vis_for,16
	for	%f0,%f2,%f0
	.end
!
! float vis_fors(float /*frs1*/, float /*frs2*/);
!
	.inline vis_fors,8
	fors	%f1,%f3,%f0
	.end
!
! double vis_fzero(void);
!
	.inline	vis_fzero,0
	fzero	%f0
	.end
!
! float vis_fzeros(void);
!
	.inline	vis_fzeros,0
	fzeros	%f0
	.end
!
! double vis_fone(void);
!
	.inline	vis_fone,0
	fone	%f0
	.end
!
! float vis_fones(void);
!
	.inline	vis_fones,0
	fones	%f0
	.end

!--------------------------------------------------------------------
! Partial store instructions
!
! void vis_stdfa_ASI_PST8P(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST8P,20
	stda	%f0,[%o1]%o2,0xc0	! ASI_PST8_P
	.end
!
! void vis_stdfa_ASI_PST8PL(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST8PL,20
	stda	%f0,[%o1]%o2,0xc8	! ASI_PST8_PL
	.end
!
! void vis_stdfa_ASI_PST8S(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST8S,20
	stda	%f0,[%o1]%o2,0xc1	! ASI_PST8_S
	.end
!
! void vis_stdfa_ASI_PST8P_int_pair(void * /*rs1*/, void * /*rs2*/, void * /*rs3*/, int /*rmask*/);;
!
	.inline vis_stdfa_ASI_PST8P_int_pair,28
        ld	[%o0],%f4
        ld	[%o1],%f5
	stda	%f4,[%o2]%o3,0xc0	! ASI_PST8_P
	.end
!
! void vis_stdfa_ASI_PST16P(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST16P,20
	stda	%f0,[%o1]%o2,0xc2	! ASI_PST16_P
	.end
!
! void vis_stdfa_ASI_PST16PL(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST16PL,20
	stda	%f0,[%o1]%o2,0xca	! ASI_PST16_PL
	.end
!
! void vis_stdfa_ASI_PST16S(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST16S,20
	stda	%f0,[%o1]%o2,0xc3	! ASI_PST16_S
	.end
!
! void vis_stdfa_ASI_PST32P(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST32P,20
	stda	%f0,[%o1]%o2,0xc4	! ASI_PST32_P
	.end
!
! void vis_stdfa_ASI_PST32PL(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST32PL,20
	stda	%f0,[%o1]%o2,0xcc	! ASI_PST32_PL
	.end
!
! void vis_stdfa_ASI_PST32S(double /*frd*/, void * /*rs1*/, int /*rmask*/);
!
	.inline vis_stdfa_ASI_PST32S,20
	stda	%f0,[%o1]%o2,0xc5	! ASI_PST32_S
	.end

!--------------------------------------------------------------------
! Byte & short store instructions
!
! void vis_stdfa_ASI_FL8P(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL8P,16
	stda	%f0,[%o1]0xd0		! ASI_FL8_P
	.end
!
! void vis_stdfa_ASI_FL8P_index(double /*frd*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stdfa_ASI_FL8P_index,24
	stda	%f0,[%o1+%o2]0xd0	! ASI_FL8_P
	.end
!
! void vis_stdfa_ASI_FL8S(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL8S,16
	stda	%f0,[%o1]0xd1		! ASI_FL8_S
	.end
!
! void vis_stdfa_ASI_FL16P(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL16P,16
	stda	%f0,[%o1]0xd2		! ASI_FL16_P
	.end
!
! void vis_stdfa_ASI_FL16P_index(double /*frd*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stdfa_ASI_FL16P_index,24
	stda	%f0,[%o1+%o2]0xd2	! ASI_FL16_P
	.end
!
! void vis_stdfa_ASI_FL16S(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL16S,16
	stda	%f0,[%o1]0xd3		! ASI_FL16_S
	.end
!
! void vis_stdfa_ASI_FL8PL(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL8PL,16
	stda	%f0,[%o1]0xd8		! ASI_FL8_PL
	.end
!
! void vis_stdfa_ASI_FL8PL_index(double /*frd*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stdfa_ASI_FL8PL_index,24
	stda	%f0,[%o1+%o2]0xd8	! ASI_FL8_PL
	.end
!
! void vis_stdfa_ASI_FL8SL(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL8SL,16
	stda	%f0,[%o1]0xd9		! ASI_FL8_SL
	.end
!
! void vis_stdfa_ASI_FL16PL(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL16PL,16
	stda	%f0,[%o1]0xda		! ASI_FL16_PL
	.end
!
! void vis_stdfa_ASI_FL16PL_index(double /*frd*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stdfa_ASI_FL16PL_index,24
	stda	%f0,[%o1+%o2]0xda 	! ASI_FL16_PL
	.end
!
! void vis_stdfa_ASI_FL16SL(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_FL16SL,16
	stda	%f0,[%o1]0xdb		! ASI_FL16_SL
	.end

!--------------------------------------------------------------------
! Byte & short load instructions
!
! double vis_lddfa_ASI_FL8P(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL8P,8
	ldda	[%o0]0xd0,%f0		! ASI_FL8_P
	.end
!
! double vis_lddfa_ASI_FL8P_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_FL8P_index,16
	ldda	[%o0+%o1]0xd0,%f0	! ASI_FL8_P
	.end
!
! double vis_lddfa_ASI_FL8P_hi(void * /*rs1*/, unsigned int /*index*/);
!
	.inline vis_lddfa_ASI_FL8P_hi,12
	sra     %o1,16,%o1
	ldda	[%o0+%o1]0xd0,%f0	! ASI_FL8_P
	.end
!
! double vis_lddfa_ASI_FL8P_lo(void * /*rs1*/, unsigned int /*index*/);
!
	.inline vis_lddfa_ASI_FL8P_lo,12
	sll     %o1,16,%o1
	sra     %o1,16,%o1
	ldda	[%o0+%o1]0xd0,%f0	! ASI_FL8_P
	.end
!
! double vis_lddfa_ASI_FL8S(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL8S,8
	ldda	[%o0]0xd1,%f0		! ASI_FL8_S
	.end
!
! double vis_lddfa_ASI_FL16P(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL16P,8
	ldda	[%o0]0xd2,%f0		! ASI_FL16_P
	.end
!
! double vis_lddfa_ASI_FL16P_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_FL16P_index,16
	ldda	[%o0+%o1]0xd2,%f0	! ASI_FL16_P
	.end
!
! double vis_lddfa_ASI_FL16S(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL16S,8
	ldda	[%o0]0xd3,%f0		! ASI_FL16_S
	.end
!
! double vis_lddfa_ASI_FL8PL(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL8PL,8
	ldda	[%o0]0xd8,%f0		! ASI_FL8_PL
	.end
!
! double vis_lddfa_ASI_FL8PL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_FL8PL_index,16
	ldda	[%o0+%o1]0xd8,%f0	! ASI_FL8_PL
	.end
!
! double vis_lddfa_ASI_FL8SL(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL8SL,8
	ldda	[%o0]0xd9,%f0		! ASI_FL8_SL
	.end
!
! double vis_lddfa_ASI_FL16PL(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL16PL,8
	ldda	[%o0]0xda,%f0		! ASI_FL16_PL
	.end
!
! double vis_lddfa_ASI_FL16PL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_FL16PL_index,16
	ldda	[%o0+%o1]0xda,%f0	! ASI_FL16_PL
	.end
!
! double vis_lddfa_ASI_FL16SL(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_FL16SL,8
	ldda	[%o0]0xdb,%f0		! ASI_FL16_SL
	.end

!--------------------------------------------------------------------
! Graphics status register
!
! unsigned int vis_read_gsr32(void);
!
	.inline vis_read_gsr32,0
	rd	%gsr,%o0
	.end
!
! void vis_write_gsr32(unsigned int /* GSR */);
!
	.inline vis_write_gsr32,4
	wr	%g0,%o0,%gsr
	.end

!--------------------------------------------------------------------
! Voxel texture mapping
!
! unsigned long vis_array8(unsigned long /*rs1 */, int /*rs2*/);
!
	.inline	vis_array8,12
	array8	%o0,%o1,%o0
	.end
!
! unsigned long vis_array16(unsigned long /*rs1*/, int /*rs2*/);
!
	.inline	vis_array16,12
	array16	%o0,%o1,%o0
	.end
!
! unsigned long vis_array32(unsigned long /*rs1*/, int /*rs2*/);
!
	.inline	vis_array32,12
	array32	%o0,%o1,%o0
	.end

!--------------------------------------------------------------------
! Register aliasing and type casts
!
! float vis_read_hi(double /* frs1 */);
!
	.inline vis_read_hi,8
	fmovs	%f0,%f0
	.end
!
! float vis_read_lo(double /* frs1 */);
!
	.inline vis_read_lo,8
	fmovs	%f1,%f0		! %f0 = low word (frs1); return %f0;
	.end
!
! double vis_write_hi(double /* frs1 */, float /* frs2 */);
!
	.inline vis_write_hi,12
	fmovs	%f3,%f0		! %f3 = float frs2; return %f0:f1;
	.end
!
! double vis_write_lo(double /* frs1 */, float /* frs2 */);
!
	.inline vis_write_lo,12
	fmovs	%f3,%f1		! %f3 = float frs2; return %f0:f1;
	.end
!
! double vis_freg_pair(float /* frs1 */, float /* frs2 */);
!
	.inline vis_freg_pair,8
	fmovs	%f1,%f0
	fmovs	%f3,%f1
	.end
!
! float vis_to_float(unsigned int /*value*/);
!
	.inline vis_to_float,4
	st	%o0,[%sp+2183]
	ld	[%sp+2183],%f0
	.end
!
! double vis_to_double(unsigned int /*value1*/, unsigned int /*value2*/);
!
	.inline vis_to_double,8
	st	%o0,[%sp+2183]
	ld	[%sp+2183],%f0
	st	%o1,[%sp+2183]
	ld	[%sp+2183],%f1
	.end
!
! double vis_to_double_dup(unsigned int /*value*/);
!
	.inline vis_to_double_dup,4
	st	%o0,[%sp+2183]
	ld	[%sp+2183],%f1
	fmovs	%f1,%f0		! duplicate value
	.end
!
! double vis_ll_to_double(unsigned long /*value*/);
!
	.inline vis_ll_to_double,8
	stx     %o0,[%sp+2183]
	ldd     [%sp+2183],%f0
        .end

!--------------------------------------------------------------------
! Address space identifier (ASI) register
!
! unsigned int vis_read_asi(void);
!
	.inline vis_read_asi,0
	rd	%asi,%o0
	.end
!
! void vis_write_asi(unsigned int /* ASI */);
!
	.inline vis_write_asi,4
	wr	%g0,%o0,%asi
	.end

!--------------------------------------------------------------------
! Load/store from/into alternate space
!
! float vis_ldfa_ASI_REG(void * /*rs1*/);
!
	.inline vis_ldfa_ASI_REG,8
	lda	[%o0+0]%asi,%f0
	.end
!
! float vis_ldfa_ASI_P(void * /*rs1*/);
!
	.inline vis_ldfa_ASI_P,8
	lda	[%o0]0x80,%f0		! ASI_P
	.end
!
! float vis_ldfa_ASI_P_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldfa_ASI_P_index,16
	lda	[%o0+%o1]0x80,%f0	! ASI_P
	.end
!
! float vis_ldfa_ASI_PL(void * /*rs1*/);
!
	.inline vis_ldfa_ASI_PL,8
	lda	[%o0]0x88,%f0		! ASI_PL
	.end
!
! float vis_ldfa_ASI_PL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldfa_ASI_PL_index,16
	lda	[%o0+%o1]0x88,%f0	! ASI_PL
	.end
!
! double vis_lddfa_ASI_REG(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_REG,8
	ldda	[%o0+0]%asi,%f0
	.end
!
! double vis_lddfa_ASI_P(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_P,8
	ldda	[%o0]0x80,%f0		! ASI_P
	.end
!
! double vis_lddfa_ASI_P_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_P_index,16
	ldda	[%o0+%o1]0x80,%f0	! ASI_P
	.end
!
! double vis_lddfa_ASI_PL(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_PL,8
	ldda	[%o0]0x88,%f0		! ASI_PL
	.end
!
! double vis_lddfa_ASI_PL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_PL_index,16
	ldda	[%o0+%o1]0x88,%f0	! ASI_PL
	.end
!
! void vis_stfa_ASI_REG(float /*frs*/, void * /*rs1*/);
!
	.inline vis_stfa_ASI_REG,12
	sta	%f1,[%o1]%asi
	.end
!
! void vis_stfa_ASI_P(float /*frs*/, void * /*rs1*/);
!
	.inline vis_stfa_ASI_P,12
	sta	%f1,[%o1]0x80		! ASI_P
	.end
!
! void vis_stfa_ASI_P_index(float /*frs*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stfa_ASI_P_index,24
	sta	%f1,[%o1+%o2]0x80	! ASI_P
	.end
!
! void vis_stfa_ASI_PL(float /*frs*/, void * /*rs1*/);
!
	.inline vis_stfa_ASI_PL,12
	sta	%f1,[%o1]0x88		! ASI_PL
	.end
!
! void vis_stfa_ASI_PL_index(float /*frs*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stfa_ASI_PL_index,24
	sta	%f1,[%o1+%o2]0x88	! ASI_PL
	.end
!
! void vis_stdfa_ASI_REG(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_REG,16
	stda	%f0,[%o1]%asi
	.end
!
! void vis_stdfa_ASI_P(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_P,16
	stda	%f0,[%o1]0x80		! ASI_P
	.end
!
! void vis_stdfa_ASI_P_index(double /*frd*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stdfa_ASI_P_index,24
	stda	%f0,[%o1+%o2]0x80	! ASI_P
	.end
!
! void vis_stdfa_ASI_PL(double /*frd*/, void * /*rs1*/);
!
	.inline vis_stdfa_ASI_PL,16
	stda	%f0,[%o1]0x88		! ASI_PL
	.end
!
! void vis_stdfa_ASI_PL_index(double /*frd*/, void * /*rs1*/, long /*index*/);
!
	.inline vis_stdfa_ASI_PL_index,24
	stda	%f0,[%o1+%o2]0x88	! ASI_PL
	.end
!
! unsigned short vis_lduha_ASI_REG(void * /*rs1*/);
!
	.inline vis_lduha_ASI_REG,8
	lduha	[%o0+0]%asi,%o0
	.end
!
! unsigned short vis_lduha_ASI_P(void * /*rs1*/);
!
	.inline vis_lduha_ASI_P,8
	lduha	[%o0]0x80,%o0		! ASI_P
	.end
!
! unsigned short vis_lduha_ASI_PL(void * /*rs1*/);
!
	.inline vis_lduha_ASI_PL,8
	lduha	[%o0]0x88,%o0		! ASI_PL
	.end
!
! unsigned short vis_lduha_ASI_P_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduha_ASI_P_index,16
	lduha	[%o0+%o1]0x80,%o0	! ASI_P
	.end
!
! unsigned short vis_lduha_ASI_PL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduha_ASI_PL_index,16
	lduha	[%o0+%o1]0x88,%o0	! ASI_PL
	.end

!--------------------------------------------------------------------
! Prefetch
!
! void vis_prefetch_read(void * /*address*/);
!
	.inline vis_prefetch_read,8
	prefetch	[%o0],0
	.end
!
! void vis_prefetch_write(void * /*address*/);
!
	.inline vis_prefetch_write,8
	prefetch	[%o0],2
	.end

!--------------------------------------------------------------------
! Nonfaulting load instructions
!
! char vis_ldsba_ASI_PNF(void * /*rs1*/);
!
	.inline vis_ldsba_ASI_PNF,8
	ldsba	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! char vis_ldsba_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldsba_ASI_PNF_index,16
	ldsba	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! char vis_ldsba_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_ldsba_ASI_PNFL,8
	ldsba	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! char vis_ldsba_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldsba_ASI_PNFL_index,16
	ldsba	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! unsigned char vis_lduba_ASI_PNF(void * /*rs1*/);
!
	.inline vis_lduba_ASI_PNF,8
	lduba	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! unsigned char vis_lduba_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduba_ASI_PNF_index,16
	lduba	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! unsigned char vis_lduba_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_lduba_ASI_PNFL,8
	lduba	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! unsigned char vis_lduba_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduba_ASI_PNFL_index,16
	lduba	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! short vis_ldsha_ASI_PNF(void * /*rs1*/);
!
	.inline vis_ldsha_ASI_PNF,8
	ldsha	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! short vis_ldsha_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldsha_ASI_PNF_index,16
	ldsha	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! short vis_ldsha_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_ldsha_ASI_PNFL,8
	ldsha	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! short vis_ldsha_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldsha_ASI_PNFL_index,16
	ldsha	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! unsigned short vis_lduha_ASI_PNF(void * /*rs1*/);
!
	.inline vis_lduha_ASI_PNF,8
	lduha	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! unsigned short vis_lduha_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduha_ASI_PNF_index,16
	lduha	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! unsigned short vis_lduha_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_lduha_ASI_PNFL,8
	lduha	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! unsigned short vis_lduha_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduha_ASI_PNFL_index,16
	lduha	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! int vis_ldswa_ASI_PNF(void * /*rs1*/);
!
	.inline vis_ldswa_ASI_PNF,8
	ldswa	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! int vis_ldswa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldswa_ASI_PNF_index,16
	ldswa	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! int vis_ldswa_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_ldswa_ASI_PNFL,8
	ldswa	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! int vis_ldswa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldswa_ASI_PNFL_index,16
	ldswa	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! unsigned int vis_lduwa_ASI_PNF(void * /*rs1*/);
!
	.inline vis_lduwa_ASI_PNF,8
	lduwa	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! unsigned int vis_lduwa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduwa_ASI_PNF_index,16
	lduwa	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! unsigned int vis_lduwa_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_lduwa_ASI_PNFL,8
	lduwa	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! unsigned int vis_lduwa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lduwa_ASI_PNFL_index,16
	lduwa	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! long vis_ldxa_ASI_PNF(void * /*rs1*/);
!
	.inline vis_ldxa_ASI_PNF,8
	ldxa	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! long vis_ldxa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldxa_ASI_PNF_index,16
	ldxa	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! long vis_ldxa_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_ldxa_ASI_PNFL,8
	ldxa	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! long vis_ldxa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldxa_ASI_PNFL_index,16
	ldxa	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! long long vis_ldda_ASI_PNF(void * /*rs1*/);
!
	.inline vis_ldda_ASI_PNF,8
	ldda	[%o0]0x82,%o0		! ASI_PNF
	.end
!
! long long vis_ldda_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldda_ASI_PNF_index,16
	ldda	[%o0+%o1]0x82,%o0	! ASI_PNF
	.end
!
! long long vis_ldda_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_ldda_ASI_PNFL,8
	ldda	[%o0]0x8a,%o0		! ASI_PNFL
	.end
!
! long long vis_ldda_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldda_ASI_PNFL_index,16
	ldda	[%o0+%o1]0x8a,%o0	! ASI_PNFL
	.end
!
! float vis_ldfa_ASI_PNF(void * /*rs1*/);
!
	.inline vis_ldfa_ASI_PNF,8
	lda	[%o0]0x82,%f0		! ASI_PNF
	.end
!
! float vis_ldfa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldfa_ASI_PNF_index,16
	lda	[%o0+%o1]0x82,%f0	! ASI_PNF
	.end
!
! float vis_ldfa_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_ldfa_ASI_PNFL,8
	lda	[%o0]0x8a,%f0		! ASI_PNFL
	.end
!
! float vis_ldfa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_ldfa_ASI_PNFL_index,16
	lda	[%o0+%o1]0x8a,%f0	! ASI_PNFL
	.end
!
! double vis_lddfa_ASI_PNF(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_PNF,8
	ldda	[%o0]0x82,%f0		! ASI_PNF
	.end
!
! double vis_lddfa_ASI_PNF_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_PNF_index,16
	ldda	[%o0+%o1]0x82,%f0	! ASI_PNF
	.end
!
! double vis_lddfa_ASI_PNFL(void * /*rs1*/);
!
	.inline vis_lddfa_ASI_PNFL,8
	ldda	[%o0]0x8a,%f0		! ASI_PNFL
	.end
!
! double vis_lddfa_ASI_PNFL_index(void * /*rs1*/, long /*index*/);
!
	.inline vis_lddfa_ASI_PNFL_index,16
	ldda	[%o0+%o1]0x8a,%f0	! ASI_PNFL
	.end

!--------------------------------------------------------------------
!
! The following are the new VIS 2.0 instructions.
!

!
! Edge handling instructions which do not set the integer condition codes
!
! int vis_edge8n(void * /*rs1*/, void * /*rs2*/);
!
	.inline vis_edge8n,16
	edge8n	%o0,%o1,%o0
	.end
!
! int vis_edge8ln(void * /*rs1*/, void * /*rs2*/);
!
	.inline vis_edge8ln,16
	edge8ln	%o0,%o1,%o0
	.end
!
! int vis_edge16n(void * /*rs1*/, void * /*rs2*/);
!
	.inline vis_edge16n,16
	edge16n	%o0,%o1,%o0
	.end
!
! int vis_edge16ln(void * /*rs1*/, void * /*rs2*/);
!
	.inline vis_edge16ln,16
	edge16ln	%o0,%o1,%o0
	.end
!
! int vis_edge32n(void * /*rs1*/, void * /*rs2*/);
!
	.inline vis_edge32n,16
	edge32n	%o0,%o1,%o0
	.end
!
! int vis_edge32ln(void * /*rs1*/, void * /*rs2*/);
!
	.inline vis_edge32ln,16
	edge32ln	%o0,%o1,%o0
	.end

!--------------------------------------------------------------------
! Byte mask and shuffle instructions
!
! void vis_write_bmask(unsigned int /*rs1*/, unsigned int /*rs2*/);
!
	.inline vis_write_bmask,8
	bmask	%o0,%o1,%o0
	.end
!
! double vis_bshuffle(double /*frs1*/, double /*frs2*/);
!
	.inline vis_bshuffle,16
	bshuffle	%f0,%f2,%f0
	.end

!--------------------------------------------------------------------
! Graphics status register
!
! unsigned int vis_read_bmask(void);
!
	.inline vis_read_bmask,0
	rd	%gsr,%o0
	srlx	%o0,32,%o0
	.end
!
! unsigned long vis_read_gsr64(void);
!
	.inline vis_read_gsr64,0
	rd	%gsr,%o0
	.end
!
! void vis_write_gsr64(unsigned long /* GSR */);
!
	.inline vis_write_gsr64,8
	wr	%g0,%o0,%gsr
        .end
