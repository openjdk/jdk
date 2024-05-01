/*
 * Copyright (c) 2022, 2024, Intel Corporation. All rights reserved.
 *
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

#include "precompiled.hpp"
#include "macroAssembler_x86.hpp"
#include "stubGenerator_x86_64.hpp"
#include <functional>

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif // PRODUCT

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

void fixlencmp(Label& Found, Register haystack_off, Register haystack, Register needle, int known_needle_len, Register tmp, StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
    int elem_size, nelem_size, nscale2;
  Address::ScaleFactor scale, nscale;

  if (known_needle_len == 1 || known_needle_len == 2) {
    __ jmp (Found);
    return;
  }
  
  if (ae == StrIntrinsicNode::LL) {
    elem_size = nelem_size = 1;
    scale = nscale = Address::times_1;
    nscale2 = 1;
  } else if (ae == StrIntrinsicNode::UU) {
    elem_size = nelem_size = 2;
    nscale2 = 1;
    scale = nscale = Address::times_2;
  } else { // UL
    elem_size = 2;
    nelem_size = 1;
    nscale2 = 2;
    scale = Address::times_2;
    nscale = Address::times_1;
  }
  
  if (ae != StrIntrinsicNode::UL) {
    switch(known_needle_len*nelem_size) {
      default: assert(false, "unreachable"); break;
      case 3:
      case 4:
          __ movzwl(tmp, Address(needle, 1*nelem_size));
          __ cmpw(Address(haystack, haystack_off, scale, 1*elem_size), tmp);
          __ jcc(Assembler::equal, Found);
          break;
      case 5:
      case 6:
          __ movl(tmp, Address(needle, 1*nelem_size));
          __ cmpl(tmp, Address(haystack, haystack_off, scale, 1*elem_size));
          __ jcc(Assembler::equal, Found);
          break;
      case 7:
          //Read one byte before start of haystack, then mask it off
          __ movq(tmp, Address(needle, -1));
          __ xorq(tmp, Address(haystack, haystack_off, scale, -1));
          __ shrq(tmp, 0x8);
          __ jcc(Assembler::zero, Found);
          break;
      case 8:
          __ movq(tmp, Address(needle, 0));
          __ cmpq(tmp, Address(haystack, haystack_off, scale, 0));
          __ jcc(Assembler::equal, Found);
          break;
      case 9:
      case 10:
          __ movq(tmp, Address(needle, 1*nelem_size));
          __ cmpq(tmp, Address(haystack, haystack_off, scale, 1*elem_size));
          __ jcc(Assembler::equal, Found);
          break;
    }
  } else { // UL
    switch(known_needle_len*elem_size) { // needle 1 then haystack 2
      default: assert(false, "unreachable"); break;
      // case 3:
      // case 4:
      //     __ movzwl(tmp, Address(needle, 1));
      //     __ cmpw(Address(haystack, haystack_off, scale, 1), tmp);
      //     __ jcc(Assembler::equal, Found);
      //     break;
      // case 5:
      case 6:
          __ movl(tmp, Address(needle, 1));
          __ cmpl(tmp, Address(haystack, haystack_off, scale, 1));
          __ jcc(Assembler::equal, Found);
          break;
      // case 7:
      //     //Read one byte before start of haystack, then mask it off
      //     __ movq(tmp, Address(needle, -2));
      //     __ xorq(tmp, Address(haystack, haystack_off, scale, -2));
      //     __ shrq(tmp, 0x8);
      //     __ jcc(Assembler::zero, Found);
      //     break;
      case 8:
          __ movq(tmp, Address(needle, 0));
          __ cmpq(tmp, Address(haystack, haystack_off, scale, 0));
          __ jcc(Assembler::equal, Found);
          break;
      // case 9:
      case 10:
          __ movq(tmp, Address(needle, 1));
          __ cmpq(tmp, Address(haystack, haystack_off, scale, 1));
          __ jcc(Assembler::equal, Found);
          break;
    }
  }
}

void varlencmp(Label& Found, Register haystack_off, Register haystack, Register needle, Register needle_len, Register tmp, Register tmp2, Register tmp3, StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {
  BLOCK_COMMENT("varlencmp: ");

  XMMRegister xmp1 = xmm5;
  Register index = tmp; //index to _after_ a loop iteration (for easier cmpq; fixed up with addressing mode constant)
  Register haystack_start = tmp2;
  Register mask = tmp3;

  Label NotFound;
  Label Loop32, DoneLoop32, DoneFull16;
  int elem_size, nelem_size, nscale2;
  Address::ScaleFactor scale, nscale;
  std::function<void(XMMRegister, XMMRegister, Address, int)> vpcmpeq;
  std::function<void(XMMRegister dst, Address src, int vector_len)> vpmovq = [_masm](XMMRegister dst, Address src, int vector_len) {
      if (vector_len == Assembler::AVX_256bit) {
        __ vmovdqu(dst, src);
      } else {
        __ movdqu(dst, src);
      }
    };

  if (ae == StrIntrinsicNode::LL) {
    elem_size = nelem_size = 1;
    scale = nscale = Address::times_1;
    nscale2 = 1;
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqb(dst, nds, src, vector_len);};
  } else if (ae == StrIntrinsicNode::UU) {
    elem_size = nelem_size = 2;
    nscale2 = 1;
    scale = nscale = Address::times_2;
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqw(dst, nds, src, vector_len);};
  } else { // UL
    elem_size = 2;
    nelem_size = 1;
    nscale2 = 2;
    scale = Address::times_2;
    nscale = Address::times_1;
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqw(dst, nds, src, vector_len);};
    vpmovq = [_masm](XMMRegister dst, Address src, int vector_len) {__ vpmovzxbw(dst, src, vector_len);};
  }

  __ leaq(haystack_start, Address(haystack, haystack_off, scale, 0));

  BLOCK_COMMENT("if ( i+32 <= needle_len  ) { do {...} while (i+32 <= needle_len)}");
  __ movq(index, 32/elem_size+1);
  __ cmpq(index, needle_len);
  __ jcc(Assembler::greaterEqual, DoneLoop32);
  __ BIND(Loop32);
 
         vpmovq(xmp1, Address(needle,   index, nscale, -32/nscale2), Assembler::AVX_256bit);
      __ vpxor (xmp1, xmp1, Address(haystack_start, index, scale, -32), Assembler::AVX_256bit);
      __ vptest(xmp1, xmp1, Assembler::AVX_256bit);
      __ jcc(Assembler::notZero, NotFound);

  __ leaq(index, Address(index, 32/elem_size));
  __ cmpq(index, needle_len);
  __ jcc(Assembler::less, Loop32);
  __ BIND(DoneLoop32);

  BLOCK_COMMENT("if ( i+16 <= needle_len ) {...}");
  __ leaq(index, Address(index, (-32+16)/elem_size));
  __ cmpq(index, needle_len);
  __ jcc(Assembler::greaterEqual, DoneFull16);

         vpmovq(xmp1, Address(needle,   index, nscale, -16/nscale2),Assembler::AVX_128bit);
      __ vpxor (xmp1, xmp1, Address(haystack_start, index, scale, -16),  Assembler::AVX_128bit);
      __ vptest (xmp1, xmp1,                                                  Assembler::AVX_128bit);
      __ jcc(Assembler::notZero, NotFound);
      __ leaq(index, Address(index, 16/elem_size));
  __ BIND(DoneFull16);

  assert(HeapWordSize*TypeArrayKlass::header_size() >= 15,"cannot read 15 bytes before array-start");
  BLOCK_COMMENT("if (i+1<needle_len) {...}");
  __ subq(index, needle_len);
  __ cmpq(index, 16/elem_size - 1); // last element already compared
  __ jcc(Assembler::greaterEqual, Found);
      __ movq(mask, -1);
      if (elem_size == 2) {
        __ shlq(index, 1);
      }
      __ bzhiq(mask, mask, index);
         vpmovq (xmp1,       Address(needle,   needle_len, nscale, -16/nscale2), Assembler::AVX_128bit);
         vpcmpeq(xmp1, xmp1, Address(haystack_start, needle_len, scale, -16),  Assembler::AVX_128bit);
      __ vpmovmskb(index, xmp1,                                                      Assembler::AVX_128bit);
      __ orq(index, mask); // set all low bits to ignore the undeflow compares
      __ cmpq(index, 0xffff);
      __ jcc(Assembler::notEqual, NotFound);

  __ jmp (Found);
  __ BIND(NotFound);
}

void string_indexof_avx2_eq(Register haystack, Register needle,
                      Register haystack_off, Register haystack_len, Register needle_len,
                      int haystack_len2, int needle_len2, Register result,
                      XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3, 
                      Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register tmp6, StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {

  Register first_index = tmp1; // offset from last_index (redundant?)
  Register last_index = tmp2;
  Register eq_mask = tmp3;
  Register bitpos = tmp4;
  XMMRegister mask = xtmp1;
  XMMRegister accumulator = xtmp2;
  XMMRegister first_needle = xtmp3;
  Label OuterLoop, OuterLoopDone;
  Label Full16Done, Partial16Done;
  Label Found1, Found2, Found3, Done;
  Label InnerLoop2, InnerLoopDone2, InnerLoop3, InnerLoopDone3;
  Label InnerLoop, InnerLoopDone;
  int elem_size, nelem_size;
  Address::ScaleFactor scale, nscale;
  std::function<void(XMMRegister, Address, int)> vpbroadcast;
  std::function<void(XMMRegister, XMMRegister, Address, int)> vpcmpeq;

  if (ae == StrIntrinsicNode::LL) {
    elem_size = nelem_size = 1;
    scale = nscale = Address::times_1;
    vpbroadcast = [_masm](XMMRegister dst, Address src, int vector_len) {__ vpbroadcastb(dst, src, vector_len);};
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqb(dst, nds, src, vector_len);};
  } else if (ae == StrIntrinsicNode::UU) {
    elem_size = nelem_size =  2;
    scale = nscale = Address::times_2;
    vpbroadcast = [_masm](XMMRegister dst, Address src, int vector_len) {__ vpbroadcastw(dst, src, vector_len);};
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqw(dst, nds, src, vector_len);};
  } else { // UL
    elem_size = 2;
    nelem_size = 1;
    scale = Address::times_2;
    nscale = Address::times_1;
    vpbroadcast = [tmp3, _masm](XMMRegister dst, Address src, int vector_len) {
      __ movzbl(tmp3, src);
      __ movdl(dst, tmp3);
      __ vpbroadcastw(dst, dst, vector_len);
    };
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqw(dst, nds, src, vector_len);};
  }

  __ leaq(haystack, Address(haystack, haystack_off, scale, 0));
  int npos = 0;
  for (XMMRegister rxmm = first_needle; npos<needle_len2; npos++, rxmm = rxmm->successor()){
    assert(rxmm->is_valid(), "Insufficient vector registers asigned to routine.");
    vpbroadcast(rxmm, Address(needle, npos*nelem_size), Assembler::AVX_256bit);
  }

  __ movq(last_index, needle_len2-1+32/elem_size);
  BLOCK_COMMENT("if (last_index+16 < haystack_len) { do {...} while(last_index+16 < haystack_len)}");
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::greater, OuterLoopDone);
    __ BIND(OuterLoop);

    vpcmpeq(accumulator, first_needle, Address(haystack, last_index,  scale, -32-needle_len2*elem_size), Assembler::AVX_256bit);
    npos = 1;
    for (XMMRegister rxmm = first_needle->successor(); npos<needle_len2; npos++, rxmm = rxmm->successor()){
      vpcmpeq(mask, rxmm, Address(haystack, last_index, scale, -32-npos*elem_size), Assembler::AVX_256bit);
      __ vpand(accumulator, accumulator, mask, Assembler::AVX_256bit);
    }
    __ vpmovmskb(eq_mask, accumulator, Assembler::AVX_256bit);
    __ testl(eq_mask, eq_mask);
    __ jcc(Assembler::notZero, Found1);
    __ leaq(last_index,  Address(last_index,  32/elem_size));
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::lessEqual, OuterLoop);
  __ BIND(OuterLoopDone);

  // tail processing

  BLOCK_COMMENT("if (last_index+8 < haystack_len) {...}");
    __ leaq(last_index,  Address(last_index,  (-32+16)/elem_size));
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::greater, Full16Done);
    
    vpcmpeq(accumulator, first_needle, Address(haystack, last_index,  scale, -16-needle_len2*elem_size), Assembler::AVX_128bit);
    npos = 1;
    for (XMMRegister rxmm = first_needle->successor(); npos<needle_len2; npos++, rxmm = rxmm->successor()){
      vpcmpeq(mask, rxmm, Address(haystack, last_index, scale, -16-npos*elem_size), Assembler::AVX_128bit);
      __ vpand(accumulator, accumulator, mask, Assembler::AVX_128bit);
    }
    __ vpmovmskb(eq_mask, accumulator, Assembler::AVX_128bit);
    __ testl(eq_mask, eq_mask);
    __ jcc(Assembler::notZero, Found2);
    __ leaq(last_index,  Address(last_index,  16/elem_size));

  __ BIND(Full16Done);

  // This is more strict then necessary, given that it is 16 - 1 - (#needle_special_cases)
  assert(HeapWordSize*TypeArrayKlass::header_size() >= 15,"cannot read 15 bytes before array-start");
  BLOCK_COMMENT("if (last_index < haystack_len) {...}");
    __ subq(last_index, haystack_len); // last_index - haystack_len
    __ cmpq(last_index, 16/elem_size);
    __ jcc(Assembler::greater, Partial16Done);

    __ movq(first_index, haystack_len);
    __ subq(first_index, needle_len);
    if (elem_size==2) {
        __ shlq(last_index, 1);
    }

    int offset = needle_len2*elem_size;
    vpcmpeq(accumulator, first_needle, Address(haystack, haystack_len,  scale, -16-offset), Assembler::AVX_128bit);
    offset-=elem_size;
    for (XMMRegister rxmm = first_needle->successor(); offset>0; offset-=elem_size, rxmm = rxmm->successor()){
      vpcmpeq(mask, rxmm, Address(haystack, haystack_len, scale, -16-offset), Assembler::AVX_128bit);
      __ vpand(accumulator, accumulator, mask, Assembler::AVX_128bit);
    }
    __ vpmovmskb(eq_mask, accumulator, Assembler::AVX_128bit);
    //__ bzhiq(eq_mask, eq_mask, last_index); // wrong end!
    __ shrxq(eq_mask, eq_mask, last_index);
    __ shlxq(eq_mask, eq_mask, last_index);
    __ testl(eq_mask, eq_mask);
    __ jcc(Assembler::notZero, Found3);
    __ leaq(last_index,  Address(last_index,  16/elem_size));

  __ BIND(Partial16Done);
  __ movq(result, -1);
  __ jmp(Done);

  __ BIND(Found1);
  __ tzcntq(bitpos, eq_mask);
  if (elem_size == 2) {
    __ shrq(bitpos, 1); //keep in elements
  }
  __ leaq(result, Address(last_index, bitpos, Address::times_1, -32-needle_len2*elem_size));
  __ leaq(result, Address(result, haystack_off, Address::times_1, 0));
  __ jmp(Done);

  __ BIND(Found2);
  __ tzcntq(bitpos, eq_mask);
  if (elem_size == 2) {
    __ shrq(bitpos, 1); //keep in elements
  }
  __ leaq(result, Address(last_index, bitpos, Address::times_1, -16-needle_len2*elem_size));
  __ leaq(result, Address(result, haystack_off, Address::times_1, 0));
  __ jmp(Done);

  __ BIND(Found3);
  __ tzcntq(bitpos, eq_mask);
  if (elem_size == 2) {
    __ shrq(bitpos, 1); //keep in elements
  }
  __ leaq(result, Address(last_index, bitpos, Address::times_1, -16-needle_len2*elem_size));
  __ leaq(result, Address(result, haystack_off, Address::times_1, 0));
  __ BIND(Done);
}


/**
 * Algorithm adopted from http://0x80.pl/articles/simd-strfind.html
 * Modified to avoid reading past end of string, as follows:
 *  - process as many full 32-byte chunks as possible
 *  - process one full 16-byte chunk (if possible)
 *  - process one full 16-byte chunk indexed off the end of the haystack, masking off the bytes from the byte-array Klass header
 * 
 * Algorithm further parametrized to also deal with UU and UL cases (not just LL).
 * 
*/
void string_indexof_avx2(Register haystack, Register needle,
                      Register haystack_off, Register haystack_len, Register needle_len,
                      int haystack_len2, int needle_len2, Register result,
                      XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4, 
                      Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register tmp6, StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {

  Register first_index = tmp1; // offset from last_index (redundant?)
  Register last_index = tmp2;
  Register eq_mask = tmp3;
  XMMRegister first = xtmp1;
  XMMRegister last = xtmp2;
  XMMRegister first_block = xtmp3;
  XMMRegister last_block = xtmp4;
  Label OuterLoop, OuterLoopDone;
  Label Full16Done, Partial16Done;
  Label Found, Done;
  Label InnerLoop2, InnerLoopDone2, InnerLoop3, InnerLoopDone3;
  Label InnerLoop, InnerLoopDone;
  int elem_size, nelem_size;
  Address::ScaleFactor scale, nscale;
  std::function<void(XMMRegister, Address, int)> vpbroadcast;
  std::function<void(XMMRegister, XMMRegister, Address, int)> vpcmpeq;

  if (ae == StrIntrinsicNode::LL) {
    elem_size = nelem_size = 1;
    scale = nscale = Address::times_1;
    vpbroadcast = [_masm](XMMRegister dst, Address src, int vector_len) {__ vpbroadcastb(dst, src, vector_len);};
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqb(dst, nds, src, vector_len);};
  } else if (ae == StrIntrinsicNode::UU) {
    elem_size = nelem_size =  2;
    scale = nscale = Address::times_2;
    vpbroadcast = [_masm](XMMRegister dst, Address src, int vector_len) {__ vpbroadcastw(dst, src, vector_len);};
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqw(dst, nds, src, vector_len);};
  } else { // UL
    elem_size = 2;
    nelem_size = 1;
    scale = Address::times_2;
    nscale = Address::times_1;
    vpbroadcast = [tmp3, _masm](XMMRegister dst, Address src, int vector_len) {
      __ movzbl(tmp3, src);
      __ movdl(dst, tmp3);
      __ vpbroadcastw(dst, dst, vector_len);
    };
    vpcmpeq = [_masm](XMMRegister dst, XMMRegister nds, Address src, int vector_len) {__ vpcmpeqw(dst, nds, src, vector_len);};
  }

  auto loop_bitmask = [tmp4, tmp5, tmp6, &Found, elem_size, needle_len2, ae, _masm](Register bitmask, Register haystack, Register haystack_index, int haystack_index_bias, Register needle, Register needle_len, Register result) {
      Label InnerLoop, InnerLoopDone;
      Register bitpos = tmp4;
      __ testl(bitmask, bitmask);
      __ jcc(Assembler::zero, InnerLoopDone);
      __ BIND(InnerLoop);
        __ tzcntq(bitpos, bitmask);
        if (elem_size == 2) {
            __ shrq(bitpos, 1); //keep in elements
        }
        __ leaq(result, Address(haystack_index, bitpos, Address::times_1, haystack_index_bias));
        if (needle_len2 != -1) {
          fixlencmp(Found, result, haystack, needle, needle_len2, bitpos, ae, _masm);
        } else {
          varlencmp(Found, result, haystack, needle, needle_len, tmp5, tmp6, bitpos, ae, _masm);
        }
        __ blsrl(bitmask, bitmask);
        if (elem_size == 2) {
            __ blsrl(bitmask, bitmask);
        }
        __ jcc(Assembler::notZero, InnerLoop);
    __ BIND(InnerLoopDone);
  };

  __ leaq(haystack, Address(haystack, haystack_off, scale, 0));
     vpbroadcast(first, Address(needle, 0),                             Assembler::AVX_256bit);
     vpbroadcast(last,  Address(needle, needle_len, nscale, -nelem_size), Assembler::AVX_256bit);

  __ movq(first_index, 32/elem_size);
  __ leaq(last_index, Address(needle_len, -1+32/elem_size));
  BLOCK_COMMENT("if (last_index+16 < haystack_len) { do {...} while(last_index+16 < haystack_len)}");
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::greater, OuterLoopDone);
    __ BIND(OuterLoop);

       vpcmpeq(first_block, first, Address(haystack, first_index, scale, -32), Assembler::AVX_256bit);
       vpcmpeq(last_block,  last,  Address(haystack, last_index,  scale, -32), Assembler::AVX_256bit);
    __ vpand(first_block, first_block, last_block, Assembler::AVX_256bit);
    __ vpmovmskb(eq_mask, first_block, Assembler::AVX_256bit);

    BLOCK_COMMENT("while (eq_mask != 0)");
    loop_bitmask(eq_mask, haystack, first_index, -32/elem_size, needle, needle_len, result);
    __ leaq(first_index, Address(first_index, 32/elem_size));
    __ leaq(last_index,  Address(last_index,  32/elem_size));
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::lessEqual, OuterLoop); // turn loop upside down to remove jmp? harder to read
  __ BIND(OuterLoopDone);

  // tail processing

  BLOCK_COMMENT("if (last_index+8 < haystack_len) {...}");
    __ leaq(first_index, Address(first_index, (-32+16)/elem_size));
    __ leaq(last_index,  Address(last_index,  (-32+16)/elem_size));
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::greater, Full16Done);

       vpcmpeq(first_block, first, Address(haystack, first_index, scale, -16), Assembler::AVX_128bit);
       vpcmpeq(last_block,  last,  Address(haystack, last_index,  scale, -16), Assembler::AVX_128bit);
    __ vpand(first_block, first_block, last_block, Assembler::AVX_128bit);
    __ vpmovmskb(eq_mask, first_block, Assembler::AVX_128bit);

    BLOCK_COMMENT("while (eq_mask != 0)");
       loop_bitmask(eq_mask, haystack, first_index, -16/elem_size, needle, needle_len, result);
    __ leaq(first_index, Address(first_index, 16/elem_size));
    __ leaq(last_index,  Address(last_index,  16/elem_size));
  __ BIND(Full16Done);

  // This is more strict then necessary, given that it is 16 - 1 - (#needle_special_cases)
  assert(HeapWordSize*TypeArrayKlass::header_size() >= 15,"cannot read 15 bytes before array-start");
  BLOCK_COMMENT("if (last_index < haystack_len) {...}");
    __ subq(last_index, haystack_len); // last_index - haystack_len
    __ cmpq(last_index, 16/elem_size);
    __ jcc(Assembler::greater, Partial16Done);

    __ movq(first_index, haystack_len);
    __ subq(first_index, needle_len);
    if (elem_size==2) {
        __ shlq(last_index, 1);
    }
       vpcmpeq(first_block, first, Address(haystack, first_index,  scale, -16+elem_size), Assembler::AVX_128bit);
       vpcmpeq(last_block,  last,  Address(haystack, haystack_len, scale, -16), Assembler::AVX_128bit);
    __ vpand(first_block, first_block, last_block, Assembler::AVX_128bit);
    __ vpmovmskb(eq_mask, first_block, Assembler::AVX_128bit);
    //__ bzhiq(eq_mask, eq_mask, last_index); // wrong end!
    __ shrxq(eq_mask, eq_mask, last_index);
    __ shlxq(eq_mask, eq_mask, last_index);

    BLOCK_COMMENT("while (eq_mask != 0)");
    loop_bitmask(eq_mask, haystack, first_index, -(16/elem_size)+1, needle, needle_len, result);
  __ BIND(Partial16Done);

  __ movq(result, -1);
  __ jmp(Done);

  // Fixup result to include the haystack offset
  __ BIND(Found);
  __ leaq(result, Address(result, haystack_off, Address::times_1, 0));
  __ BIND(Done);
}

void string_indexof_avx5(Register haystack, Register needle,
                      Register haystack_off, Register haystack_len, Register needle_len,
                      int haystack_len2, int needle_len2, Register result,
                      XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4, 
                      Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register tmp6, StrIntrinsicNode::ArgEncoding ae, MacroAssembler *_masm) {

  Register first_index = tmp1; // offset from last_index (redundant?)
  Register last_index = tmp2;
  Register tail_mask_reg = tmp3;
  Register maskpos = tmp4;
  XMMRegister first = xtmp1;
  XMMRegister last = xtmp2;
  KRegister tail_mask = k5;
  KRegister first_block = k6;
  KRegister last_block = k7;
  Label OuterLoop, OuterLoopDone;
  Label Full16Done, Partial16Done;
  Label Found, Done;
  Label InnerLoop2, InnerLoopDone2, InnerLoop3, InnerLoopDone3;
  Label InnerLoop, InnerLoopDone;
  int elem_size, nelem_size;
  Address::ScaleFactor scale, nscale;
  std::function<void(XMMRegister, Address, int)> vpbroadcast;
  std::function<void(KRegister, XMMRegister, Address, int)> vpcmpeq;
  std::function<void(KRegister, KRegister, XMMRegister, Address, int)> kvpcmpeq;

  if (ae == StrIntrinsicNode::LL) {
    elem_size = nelem_size = 1;
    scale = nscale = Address::times_1;
    vpbroadcast = [_masm](XMMRegister dst, Address src, int vector_len) {__ vpbroadcastb(dst, src, vector_len);};
    vpcmpeq = [_masm](KRegister dst, XMMRegister nds, Address src, int vector_len) {__ evpcmpeqb(dst, nds, src, vector_len);};
    kvpcmpeq = [_masm](KRegister dst, KRegister mask, XMMRegister nds, Address src, int vector_len) {__ evpcmpeqb(dst, mask, nds, src, vector_len);};
  } else if (ae == StrIntrinsicNode::UU) {
    elem_size = nelem_size =  2;
    scale = nscale = Address::times_2;
    vpbroadcast = [_masm](XMMRegister dst, Address src, int vector_len) {__ vpbroadcastw(dst, src, vector_len);};
    vpcmpeq = [_masm](KRegister dst, XMMRegister nds, Address src, int vector_len) {__ evpcmpeqw(dst, nds, src, vector_len);};
    kvpcmpeq = [_masm](KRegister dst, KRegister mask, XMMRegister nds, Address src, int vector_len) {__ evpcmpeqw(dst, mask, nds, src, vector_len);};
  } else { // UL
    elem_size = 2;
    nelem_size = 1;
    scale = Address::times_2;
    nscale = Address::times_1;
    vpbroadcast = [tmp3, _masm](XMMRegister dst, Address src, int vector_len) {
      __ movzbl(tmp3, src);
      __ movdl(dst, tmp3);
      __ vpbroadcastw(dst, dst, vector_len);
    };
    vpcmpeq = [_masm](KRegister dst, XMMRegister nds, Address src, int vector_len) {__ evpcmpeqw(dst, nds, src, vector_len);};
    kvpcmpeq = [_masm](KRegister dst, KRegister mask, XMMRegister nds, Address src, int vector_len) {__ evpcmpeqw(dst, mask, nds, src, vector_len);
    };
  }

  auto loop_bitmask = [tmp3, tmp4, tmp5, tmp6, &Found, needle_len2, elem_size, ae, _masm](KRegister eq_mask, Register haystack, Register haystack_index, int haystack_index_bias, Register needle, Register needle_len, Register result) {
      Label InnerLoop, InnerLoopDone;
      Register bitmask = tmp3;
      Register bitpos = tmp4;
      __ ktestql(eq_mask, eq_mask);
      __ jcc(Assembler::zero, InnerLoopDone);
      __ kmovql(bitmask, eq_mask);
      __ BIND(InnerLoop);
        __ tzcntq(bitpos, bitmask);
        __ leaq(result, Address(haystack_index, bitpos, Address::times_1, haystack_index_bias));
        if (needle_len2 != -1) {
          fixlencmp(Found, result, haystack, needle, needle_len2, bitpos, ae, _masm);
        } else {
          varlencmp(Found, result, haystack, needle, needle_len, tmp5, tmp6, bitpos, ae, _masm);
        }
        __ blsrl(bitmask, bitmask);
        __ jcc(Assembler::notZero, InnerLoop);
    __ BIND(InnerLoopDone);
  };

  __ leaq(haystack, Address(haystack, haystack_off, scale, 0));
     vpbroadcast(first, Address(needle, 0),                     Assembler::AVX_512bit);
     vpbroadcast(last,  Address(needle, needle_len, nscale, -nelem_size), Assembler::AVX_512bit);

  __ movq(first_index, 64/elem_size);
  __ leaq(last_index, Address(needle_len, -1+64/elem_size));
  BLOCK_COMMENT("if (last_index+64 < haystack_len) { do {...} while(last_index+64 < haystack_len)}");
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::greater, OuterLoopDone);
    __ BIND(OuterLoop);

       vpcmpeq(first_block, first, Address(haystack, first_index, scale, -64), Assembler::AVX_512bit);
       vpcmpeq(last_block,  last, Address(haystack, last_index,  scale, -64), Assembler::AVX_512bit);
    __ kandql(first_block, first_block, last_block);

    BLOCK_COMMENT("while (eq_mask != 0)");
    loop_bitmask(first_block, haystack, first_index, -64/elem_size, needle, needle_len, result);
    __ leaq(first_index, Address(first_index, 64/elem_size));
    __ leaq(last_index,  Address(last_index, 64/elem_size));
    __ cmpl(last_index, haystack_len);
    __ jcc(Assembler::lessEqual, OuterLoop); // turn loop upside down to remove jmp? harder to read
  __ BIND(OuterLoopDone);

  // tail processing

  BLOCK_COMMENT("if (last_index < haystack_len) {...}");
    __ leaq(first_index, Address(first_index, (-64+1)/elem_size));
    __ leaq(last_index, Address(last_index, (-64+1)/elem_size));
    __ movq(maskpos, haystack_len);
    __ subq(maskpos, last_index);
    __ jcc(Assembler::lessEqual, Partial16Done);

    __ movq(tail_mask_reg, -1); // is this the best way to produce this mask??
    __ bzhiq(tail_mask_reg, tail_mask_reg, maskpos);
    __ kmovql(tail_mask, tail_mask_reg);
       kvpcmpeq(first_block, tail_mask, first, Address(haystack, first_index, scale, -elem_size), Assembler::AVX_512bit);
       kvpcmpeq(last_block,  tail_mask, last,  Address(haystack, last_index,  scale, -elem_size), Assembler::AVX_512bit);
    __ kandql(first_block, first_block, last_block);

    BLOCK_COMMENT("while (eq_mask != 0)");
    loop_bitmask(first_block, haystack, first_index, -1, needle, needle_len, result);
  __ BIND(Partial16Done);

  __ movq(result, -1);
  __ jmp(Done);

  // Fixup result to include the haystack offset
  __ BIND(Found);
  __ leaq(result, Address(result, haystack_off, Address::times_1, 0));
  __ BIND(Done);
}

address StubGenerator::generate_string_indexOf2I(StrIntrinsicNode::ArgEnc ae) {
  __ align(CodeEntryAlignment);
  StubCodeMark mark(this, "StubRoutines", "string_indexOfI");
  address start = __ pc();
  __ enter();

  // Linkage:
  // haystack_off = c_arg0
  // haystack_len = c_arg1
  // needle_len   = c_arg2
  // haystack     = c_arg3
  // needle       = c_arg4

  // Register Map
  #ifdef _WIN64
  #error "Not implemented yet"
  #else
  const Register haystack_off  = c_rarg0; // rdi
  const Register haystack_len  = c_rarg1; // rsi
  const Register needle_len    = c_rarg2; // rdx
  const Register haystack      = c_rarg3; // rcx
  const Register needle        = c_rarg4; // r8
  const Register result        = rax;
 
  const Register tmp1 = r9;
  const Register tmp2 = r10;
  const Register tmp3 = r11;
  const Register tmp4 = r12;
  const Register tmp5 = r13;
  const Register tmp6 = r14;
  #endif

  Label haystackCheck, haystackCheckFailed, checksPassed, defaultNeedleSize;
  // Check for trivial cases, no need to spill registers just yet
  // These checks already exist in String.java::indexOf() so perhaps not needed

  // if (0==needle_len) return haystack_off;
  __ cmpl(needle_len, 0);
  __ jcc(Assembler::notZero, haystackCheck);
  __ movl(result, haystack_off);
  __ leave();
  __ ret(0);

  // if (0==haystack_len || needle_len>haystack_len) return -1;
  __ BIND(haystackCheck);
  __ cmpl(haystack_len, 0);
  __ jcc(Assembler::zero, haystackCheckFailed);
  __ cmpl(haystack_len, needle_len);
  __ jcc(Assembler::greater, checksPassed);

  __ BIND(haystackCheckFailed);
  __ movl(result, -1);
  __ leave();
  __ ret(0);

  // Emit specialized code for SMALL needle sizes
  const int elem_size = ae == StrIntrinsicNode::LL ? 1 : 2;
  const int cases = 10/elem_size;
  address handlers[cases+1];
  for (int needle_size = 1; needle_size<=cases; needle_size++) {
    __ align(CodeEntryAlignment);
    handlers[needle_size] = __ pc();
      // Save all 'SOE' registers
    #ifdef _WIN64
    #error "Not implemented yet"
    #else
    __ push(r12);
    __ push(r13);
    // __ push(r14);
    // __ push(r15);

    // Java signature calls for 32-bit ints, convert inputs to 64-bit for consistency
    __ movzwq(haystack_len,haystack_len);
    __ movzwq(haystack_off, haystack_off);
    __ movzwq(needle_len, needle_len);

    #endif
    if (UseAVX>2) {
      string_indexof_avx5(haystack, needle, haystack_off, haystack_len, needle_len,
                        needle_size, -1, result,
                        xmm0, xmm1, xmm2, xmm3, 
                        tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, ae, _masm);
    } else {
      string_indexof_avx2(haystack, needle, haystack_off, haystack_len, needle_len,
                        -1, needle_size, result,
                        xmm0, xmm1, xmm2, xmm3, 
                        tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, ae, _masm);
    }

    // string_indexof_avx2_eq(haystack, needle, haystack_off, haystack_len, needle_len,
    //               -1, needle_size, result,
    //               xmm0, xmm1, xmm2, 
    //               tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, ae, _masm);
    #ifdef _WIN64
    #error "Not implemented yet"
    #else
    // __ pop(r15);
    // __ pop(r14);
    __ pop(r13);
    __ pop(r12);
    #endif

    __ leave();
    __ ret(0);
  }

  address needle_switch_table = __ pc();
  for (int needle_size = 1; needle_size<=cases; needle_size++) {
    __ emit_address(handlers[needle_size]);
  }

  __ BIND(checksPassed);
  if (ae != StrIntrinsicNode::UL) {
    __ cmpl(needle_len, cases);
    __ jcc(Assembler::greater, defaultNeedleSize);
    __ mov64(tmp1, (int64_t)needle_switch_table);
    __ jmp(Address(tmp1, needle_len, Address::times_8, -8));
  }
  __ BIND(defaultNeedleSize);

  // Save all 'SOE' registers
  #ifdef _WIN64
  #error "Not implemented yet"
  #else
  __ push(r12);
  __ push(r13);
  __ push(r14);
  __ push(r15);
  #endif

  // Java signature calls for 32-bit ints, convert inputs to 64-bit for consistency
  __ movzwq(haystack_len,haystack_len);
  __ movzwq(haystack_off, haystack_off);
  __ movzwq(needle_len, needle_len);

  if (UseAVX>2) {
    string_indexof_avx5(haystack, needle, haystack_off, haystack_len, needle_len,
                      -1, -1, result,
                      xmm0, xmm1, xmm2, xmm3, 
                      tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, ae, _masm);
  } else {
    string_indexof_avx2(haystack, needle, haystack_off, haystack_len, needle_len,
                      -1, -1, result,
                      xmm0, xmm1, xmm2, xmm3, 
                      tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, ae, _masm);
  }
  

  #ifdef _WIN64
  #error "Not implemented yet"
  #else
  __ pop(r15);
  __ pop(r14);
  __ pop(r13);
  __ pop(r12);
  #endif

  __ leave();
  __ ret(0);
  return start;
}

