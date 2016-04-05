/*
* Copyright (c) 2016, Intel Corporation.
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
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"

// ofs and limit are used for multi-block byte array.
// int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
void MacroAssembler::fast_sha1(XMMRegister abcd, XMMRegister e0, XMMRegister e1, XMMRegister msg0,
  XMMRegister msg1, XMMRegister msg2, XMMRegister msg3, XMMRegister shuf_mask,
  Register buf, Register state, Register ofs, Register limit, Register rsp, bool multi_block) {

  Label start, done_hash, loop0;

  address upper_word_mask = StubRoutines::x86::upper_word_mask_addr();
  address shuffle_byte_flip_mask = StubRoutines::x86::shuffle_byte_flip_mask_addr();

  bind(start);
  movdqu(abcd, Address(state, 0));
  pinsrd(e0, Address(state, 16), 3);
  movdqu(shuf_mask, ExternalAddress(upper_word_mask)); // 0xFFFFFFFF000000000000000000000000
  pand(e0, shuf_mask);
  pshufd(abcd, abcd, 0x1B);
  movdqu(shuf_mask, ExternalAddress(shuffle_byte_flip_mask)); //0x000102030405060708090a0b0c0d0e0f

  bind(loop0);
  // Save hash values for addition after rounds
  movdqu(Address(rsp, 0), e0);
  movdqu(Address(rsp, 16), abcd);


  // Rounds 0 - 3
  movdqu(msg0, Address(buf, 0));
  pshufb(msg0, shuf_mask);
  paddd(e0, msg0);
  movdqa(e1, abcd);
  sha1rnds4(abcd, e0, 0);

  // Rounds 4 - 7
  movdqu(msg1, Address(buf, 16));
  pshufb(msg1, shuf_mask);
  sha1nexte(e1, msg1);
  movdqa(e0, abcd);
  sha1rnds4(abcd, e1, 0);
  sha1msg1(msg0, msg1);

  // Rounds 8 - 11
  movdqu(msg2, Address(buf, 32));
  pshufb(msg2, shuf_mask);
  sha1nexte(e0, msg2);
  movdqa(e1, abcd);
  sha1rnds4(abcd, e0, 0);
  sha1msg1(msg1, msg2);
  pxor(msg0, msg2);

  // Rounds 12 - 15
  movdqu(msg3, Address(buf, 48));
  pshufb(msg3, shuf_mask);
  sha1nexte(e1, msg3);
  movdqa(e0, abcd);
  sha1msg2(msg0, msg3);
  sha1rnds4(abcd, e1, 0);
  sha1msg1(msg2, msg3);
  pxor(msg1, msg3);

  // Rounds 16 - 19
  sha1nexte(e0, msg0);
  movdqa(e1, abcd);
  sha1msg2(msg1, msg0);
  sha1rnds4(abcd, e0, 0);
  sha1msg1(msg3, msg0);
  pxor(msg2, msg0);

  // Rounds 20 - 23
  sha1nexte(e1, msg1);
  movdqa(e0, abcd);
  sha1msg2(msg2, msg1);
  sha1rnds4(abcd, e1, 1);
  sha1msg1(msg0, msg1);
  pxor(msg3, msg1);

  // Rounds 24 - 27
  sha1nexte(e0, msg2);
  movdqa(e1, abcd);
  sha1msg2(msg3, msg2);
  sha1rnds4(abcd, e0, 1);
  sha1msg1(msg1, msg2);
  pxor(msg0, msg2);

  // Rounds 28 - 31
  sha1nexte(e1, msg3);
  movdqa(e0, abcd);
  sha1msg2(msg0, msg3);
  sha1rnds4(abcd, e1, 1);
  sha1msg1(msg2, msg3);
  pxor(msg1, msg3);

  // Rounds 32 - 35
  sha1nexte(e0, msg0);
  movdqa(e1, abcd);
  sha1msg2(msg1, msg0);
  sha1rnds4(abcd, e0, 1);
  sha1msg1(msg3, msg0);
  pxor(msg2, msg0);

  // Rounds 36 - 39
  sha1nexte(e1, msg1);
  movdqa(e0, abcd);
  sha1msg2(msg2, msg1);
  sha1rnds4(abcd, e1, 1);
  sha1msg1(msg0, msg1);
  pxor(msg3, msg1);

  // Rounds 40 - 43
  sha1nexte(e0, msg2);
  movdqa(e1, abcd);
  sha1msg2(msg3, msg2);
  sha1rnds4(abcd, e0, 2);
  sha1msg1(msg1, msg2);
  pxor(msg0, msg2);

  // Rounds 44 - 47
  sha1nexte(e1, msg3);
  movdqa(e0, abcd);
  sha1msg2(msg0, msg3);
  sha1rnds4(abcd, e1, 2);
  sha1msg1(msg2, msg3);
  pxor(msg1, msg3);

  // Rounds 48 - 51
  sha1nexte(e0, msg0);
  movdqa(e1, abcd);
  sha1msg2(msg1, msg0);
  sha1rnds4(abcd, e0, 2);
  sha1msg1(msg3, msg0);
  pxor(msg2, msg0);

  // Rounds 52 - 55
  sha1nexte(e1, msg1);
  movdqa(e0, abcd);
  sha1msg2(msg2, msg1);
  sha1rnds4(abcd, e1, 2);
  sha1msg1(msg0, msg1);
  pxor(msg3, msg1);

  // Rounds 56 - 59
  sha1nexte(e0, msg2);
  movdqa(e1, abcd);
  sha1msg2(msg3, msg2);
  sha1rnds4(abcd, e0, 2);
  sha1msg1(msg1, msg2);
  pxor(msg0, msg2);

  // Rounds 60 - 63
  sha1nexte(e1, msg3);
  movdqa(e0, abcd);
  sha1msg2(msg0, msg3);
  sha1rnds4(abcd, e1, 3);
  sha1msg1(msg2, msg3);
  pxor(msg1, msg3);

  // Rounds 64 - 67
  sha1nexte(e0, msg0);
  movdqa(e1, abcd);
  sha1msg2(msg1, msg0);
  sha1rnds4(abcd, e0, 3);
  sha1msg1(msg3, msg0);
  pxor(msg2, msg0);

  // Rounds 68 - 71
  sha1nexte(e1, msg1);
  movdqa(e0, abcd);
  sha1msg2(msg2, msg1);
  sha1rnds4(abcd, e1, 3);
  pxor(msg3, msg1);

  // Rounds 72 - 75
  sha1nexte(e0, msg2);
  movdqa(e1, abcd);
  sha1msg2(msg3, msg2);
  sha1rnds4(abcd, e0, 3);

  // Rounds 76 - 79
  sha1nexte(e1, msg3);
  movdqa(e0, abcd);
  sha1rnds4(abcd, e1, 3);

  // add current hash values with previously saved
  movdqu(msg0, Address(rsp, 0));
  sha1nexte(e0, msg0);
  movdqu(msg0, Address(rsp, 16));
  paddd(abcd, msg0);

  if (multi_block) {
    // increment data pointer and loop if more to process
    addptr(buf, 64);
    addptr(ofs, 64);
    cmpptr(ofs, limit);
    jcc(Assembler::belowEqual, loop0);
    movptr(rax, ofs); //return ofs
  }
  // write hash values back in the correct order
  pshufd(abcd, abcd, 0x1b);
  movdqu(Address(state, 0), abcd);
  pextrd(Address(state, 16), e0, 3);

  bind(done_hash);

}

// xmm0 (msg) is used as an implicit argument to sh256rnds2
// and state0 and state1 can never use xmm0 register.
// ofs and limit are used for multi-block byte array.
// int com.sun.security.provider.DigestBase.implCompressMultiBlock(byte[] b, int ofs, int limit)
#ifdef _LP64
void MacroAssembler::fast_sha256(XMMRegister msg, XMMRegister state0, XMMRegister state1, XMMRegister msgtmp0,
  XMMRegister msgtmp1, XMMRegister msgtmp2, XMMRegister msgtmp3, XMMRegister msgtmp4,
  Register buf, Register state, Register ofs, Register limit, Register rsp,
  bool multi_block, XMMRegister shuf_mask) {
#else
void MacroAssembler::fast_sha256(XMMRegister msg, XMMRegister state0, XMMRegister state1, XMMRegister msgtmp0,
  XMMRegister msgtmp1, XMMRegister msgtmp2, XMMRegister msgtmp3, XMMRegister msgtmp4,
  Register buf, Register state, Register ofs, Register limit, Register rsp,
  bool multi_block) {
#endif
  Label start, done_hash, loop0;

  address K256 = StubRoutines::x86::k256_addr();
  address pshuffle_byte_flip_mask = StubRoutines::x86::pshuffle_byte_flip_mask_addr();

  bind(start);
  movdqu(state0, Address(state, 0));
  movdqu(state1, Address(state, 16));

  pshufd(state0, state0, 0xB1);
  pshufd(state1, state1, 0x1B);
  movdqa(msgtmp4, state0);
  palignr(state0, state1, 8);
  pblendw(state1, msgtmp4, 0xF0);

#ifdef _LP64
  movdqu(shuf_mask, ExternalAddress(pshuffle_byte_flip_mask));
#endif
  lea(rax, ExternalAddress(K256));

  bind(loop0);
  movdqu(Address(rsp, 0), state0);
  movdqu(Address(rsp, 16), state1);

  // Rounds 0-3
  movdqu(msg, Address(buf, 0));
#ifdef _LP64
  pshufb(msg, shuf_mask);
#else
  pshufb(msg, ExternalAddress(pshuffle_byte_flip_mask));
#endif
  movdqa(msgtmp0, msg);
  paddd(msg, Address(rax, 0));
  sha256rnds2(state1, state0);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);

  // Rounds 4-7
  movdqu(msg, Address(buf, 16));
#ifdef _LP64
  pshufb(msg, shuf_mask);
#else
  pshufb(msg, ExternalAddress(pshuffle_byte_flip_mask));
#endif
  movdqa(msgtmp1, msg);
  paddd(msg, Address(rax, 16));
  sha256rnds2(state1, state0);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp0, msgtmp1);

  // Rounds 8-11
  movdqu(msg, Address(buf, 32));
#ifdef _LP64
  pshufb(msg, shuf_mask);
#else
  pshufb(msg, ExternalAddress(pshuffle_byte_flip_mask));
#endif
  movdqa(msgtmp2, msg);
  paddd(msg, Address(rax, 32));
  sha256rnds2(state1, state0);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp1, msgtmp2);

  // Rounds 12-15
  movdqu(msg, Address(buf, 48));
#ifdef _LP64
  pshufb(msg, shuf_mask);
#else
  pshufb(msg, ExternalAddress(pshuffle_byte_flip_mask));
#endif
  movdqa(msgtmp3, msg);
  paddd(msg, Address(rax, 48));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp3);
  palignr(msgtmp4, msgtmp2, 4);
  paddd(msgtmp0, msgtmp4);
  sha256msg2(msgtmp0, msgtmp3);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp2, msgtmp3);

  // Rounds 16-19
  movdqa(msg, msgtmp0);
  paddd(msg, Address(rax, 64));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp0);
  palignr(msgtmp4, msgtmp3, 4);
  paddd(msgtmp1, msgtmp4);
  sha256msg2(msgtmp1, msgtmp0);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp3, msgtmp0);

  // Rounds 20-23
  movdqa(msg, msgtmp1);
  paddd(msg, Address(rax, 80));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp1);
  palignr(msgtmp4, msgtmp0, 4);
  paddd(msgtmp2, msgtmp4);
  sha256msg2(msgtmp2, msgtmp1);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp0, msgtmp1);

  // Rounds 24-27
  movdqa(msg, msgtmp2);
  paddd(msg, Address(rax, 96));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp2);
  palignr(msgtmp4, msgtmp1, 4);
  paddd(msgtmp3, msgtmp4);
  sha256msg2(msgtmp3, msgtmp2);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp1, msgtmp2);

  // Rounds 28-31
  movdqa(msg, msgtmp3);
  paddd(msg, Address(rax, 112));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp3);
  palignr(msgtmp4, msgtmp2, 4);
  paddd(msgtmp0, msgtmp4);
  sha256msg2(msgtmp0, msgtmp3);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp2, msgtmp3);

  // Rounds 32-35
  movdqa(msg, msgtmp0);
  paddd(msg, Address(rax, 128));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp0);
  palignr(msgtmp4, msgtmp3, 4);
  paddd(msgtmp1, msgtmp4);
  sha256msg2(msgtmp1, msgtmp0);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp3, msgtmp0);

  // Rounds 36-39
  movdqa(msg, msgtmp1);
  paddd(msg, Address(rax, 144));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp1);
  palignr(msgtmp4, msgtmp0, 4);
  paddd(msgtmp2, msgtmp4);
  sha256msg2(msgtmp2, msgtmp1);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp0, msgtmp1);

  // Rounds 40-43
  movdqa(msg, msgtmp2);
  paddd(msg, Address(rax, 160));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp2);
  palignr(msgtmp4, msgtmp1, 4);
  paddd(msgtmp3, msgtmp4);
  sha256msg2(msgtmp3, msgtmp2);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp1, msgtmp2);

  // Rounds 44-47
  movdqa(msg, msgtmp3);
  paddd(msg, Address(rax, 176));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp3);
  palignr(msgtmp4, msgtmp2, 4);
  paddd(msgtmp0, msgtmp4);
  sha256msg2(msgtmp0, msgtmp3);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp2, msgtmp3);

  // Rounds 48-51
  movdqa(msg, msgtmp0);
  paddd(msg, Address(rax, 192));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp0);
  palignr(msgtmp4, msgtmp3, 4);
  paddd(msgtmp1, msgtmp4);
  sha256msg2(msgtmp1, msgtmp0);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  sha256msg1(msgtmp3, msgtmp0);

  // Rounds 52-55
  movdqa(msg, msgtmp1);
  paddd(msg, Address(rax, 208));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp1);
  palignr(msgtmp4, msgtmp0, 4);
  paddd(msgtmp2, msgtmp4);
  sha256msg2(msgtmp2, msgtmp1);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);

  // Rounds 56-59
  movdqa(msg, msgtmp2);
  paddd(msg, Address(rax, 224));
  sha256rnds2(state1, state0);
  movdqa(msgtmp4, msgtmp2);
  palignr(msgtmp4, msgtmp1, 4);
  paddd(msgtmp3, msgtmp4);
  sha256msg2(msgtmp3, msgtmp2);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);

  // Rounds 60-63
  movdqa(msg, msgtmp3);
  paddd(msg, Address(rax, 240));
  sha256rnds2(state1, state0);
  pshufd(msg, msg, 0x0E);
  sha256rnds2(state0, state1);
  movdqu(msg, Address(rsp, 0));
  paddd(state0, msg);
  movdqu(msg, Address(rsp, 16));
  paddd(state1, msg);

  if (multi_block) {
    // increment data pointer and loop if more to process
    addptr(buf, 64);
    addptr(ofs, 64);
    cmpptr(ofs, limit);
    jcc(Assembler::belowEqual, loop0);
    movptr(rax, ofs); //return ofs
  }

  pshufd(state0, state0, 0x1B);
  pshufd(state1, state1, 0xB1);
  movdqa(msgtmp4, state0);
  pblendw(state0, state1, 0xF0);
  palignr(state1, msgtmp4, 8);

  movdqu(Address(state, 0), state0);
  movdqu(Address(state, 16), state1);

  bind(done_hash);

}
