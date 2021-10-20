/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2021, Red Hat Inc. All rights reserved.
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
#include "macroAssembler_aarch64.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/stubRoutines.hpp"

void MacroAssembler::aesecb_decrypt(Register from, Register to, Register key, Register keylen) {
  Label L_doLast;

  ld1(v0, T16B, from); // get 16 bytes of input

  ld1(v5, T16B, post(key, 16));
  rev32(v5, T16B, v5);

  ld1(v1, v2, v3, v4, T16B, post(key, 64));
  rev32(v1, T16B, v1);
  rev32(v2, T16B, v2);
  rev32(v3, T16B, v3);
  rev32(v4, T16B, v4);
  aesd(v0, v1);
  aesimc(v0, v0);
  aesd(v0, v2);
  aesimc(v0, v0);
  aesd(v0, v3);
  aesimc(v0, v0);
  aesd(v0, v4);
  aesimc(v0, v0);

  ld1(v1, v2, v3, v4, T16B, post(key, 64));
  rev32(v1, T16B, v1);
  rev32(v2, T16B, v2);
  rev32(v3, T16B, v3);
  rev32(v4, T16B, v4);
  aesd(v0, v1);
  aesimc(v0, v0);
  aesd(v0, v2);
  aesimc(v0, v0);
  aesd(v0, v3);
  aesimc(v0, v0);
  aesd(v0, v4);
  aesimc(v0, v0);

  ld1(v1, v2, T16B, post(key, 32));
  rev32(v1, T16B, v1);
  rev32(v2, T16B, v2);

  cmpw(keylen, 44);
  br(Assembler::EQ, L_doLast);

  aesd(v0, v1);
  aesimc(v0, v0);
  aesd(v0, v2);
  aesimc(v0, v0);

  ld1(v1, v2, T16B, post(key, 32));
  rev32(v1, T16B, v1);
  rev32(v2, T16B, v2);

  cmpw(keylen, 52);
  br(Assembler::EQ, L_doLast);

  aesd(v0, v1);
  aesimc(v0, v0);
  aesd(v0, v2);
  aesimc(v0, v0);

  ld1(v1, v2, T16B, post(key, 32));
  rev32(v1, T16B, v1);
  rev32(v2, T16B, v2);

  bind(L_doLast);

  aesd(v0, v1);
  aesimc(v0, v0);
  aesd(v0, v2);

  eor(v0, T16B, v0, v5);

  st1(v0, T16B, to);

  // Preserve the address of the start of the key
  sub(key, key, keylen, LSL, exact_log2(sizeof (jint)));
}

// Load expanded key into v17..v31
void MacroAssembler::aesenc_loadkeys(Register key, Register keylen) {
  Label L_loadkeys_44, L_loadkeys_52;
  cmpw(keylen, 52);
  br(Assembler::LO, L_loadkeys_44);
  br(Assembler::EQ, L_loadkeys_52);

  ld1(v17, v18,  T16B,  post(key, 32));
  rev32(v17,  T16B, v17);
  rev32(v18,  T16B, v18);
  bind(L_loadkeys_52);
  ld1(v19, v20,  T16B,  post(key, 32));
  rev32(v19,  T16B, v19);
  rev32(v20,  T16B, v20);
  bind(L_loadkeys_44);
  ld1(v21, v22, v23, v24,  T16B,  post(key, 64));
  rev32(v21,  T16B, v21);
  rev32(v22,  T16B, v22);
  rev32(v23,  T16B, v23);
  rev32(v24,  T16B, v24);
  ld1(v25, v26, v27, v28,  T16B,  post(key, 64));
  rev32(v25,  T16B, v25);
  rev32(v26,  T16B, v26);
  rev32(v27,  T16B, v27);
  rev32(v28,  T16B, v28);
  ld1(v29, v30, v31,  T16B, post(key, 48));
  rev32(v29,  T16B, v29);
  rev32(v30,  T16B, v30);
  rev32(v31,  T16B, v31);

  // Preserve the address of the start of the key
  sub(key, key, keylen, LSL, exact_log2(sizeof (jint)));
}

// NeoverseTM N1Software Optimization Guide:
// Adjacent AESE/AESMC instruction pairs and adjacent AESD/AESIMC
// instruction pairs will exhibit the performance characteristics
// described in Section 4.6.
void MacroAssembler::aes_round(FloatRegister input, FloatRegister subkey) {
  aese(input, subkey); aesmc(input, input);
}

// KernelGenerator
//
// The abstract base class of an unrolled function generator.
// Subclasses override generate(), length(), and next() to generate
// unrolled and interleaved functions.
//
// The core idea is that a subclass defines a method which generates
// the base case of a function and a method to generate a clone of it,
// shifted to a different set of registers. KernelGenerator will then
// generate several interleaved copies of the function, with each one
// using a different set of registers.

// The subclass must implement three methods: length(), which is the
// number of instruction bundles in the intrinsic, generate(int n)
// which emits the nth instruction bundle in the intrinsic, and next()
// which takes an instance of the generator and returns a version of it,
// shifted to a new set of registers.

class KernelGenerator: public MacroAssembler {
protected:
  const int _unrolls;
public:
  KernelGenerator(Assembler *as, int unrolls)
    : MacroAssembler(as->code()), _unrolls(unrolls) { }
  virtual void generate(int index) = 0;
  virtual int length() = 0;
  virtual KernelGenerator *next() = 0;
  int unrolls() { return _unrolls; }
  void unroll();
};

void KernelGenerator::unroll() {
  ResourceMark rm;
  KernelGenerator **generators
    = NEW_RESOURCE_ARRAY(KernelGenerator *, unrolls());

  generators[0] = this;
  for (int i = 1; i < unrolls(); i++) {
    generators[i] = generators[i-1]->next();
  }

  for (int j = 0; j < length(); j++) {
    for (int i = 0; i < unrolls(); i++) {
      generators[i]->generate(j);
    }
  }
}

// An unrolled and interleaved generator for AES encryption.
class AESKernelGenerator: public KernelGenerator {
  Register _from, _to;
  const Register _keylen;
  FloatRegister _data;
  const FloatRegister _subkeys;
  bool _once;
  Label _rounds_44, _rounds_52;

public:
  AESKernelGenerator(Assembler *as, int unrolls,
                     Register from, Register to, Register keylen, FloatRegister data,
                     FloatRegister subkeys, bool once = true)
    : KernelGenerator(as, unrolls),
      _from(from), _to(to), _keylen(keylen), _data(data),
      _subkeys(subkeys), _once(once) {
  }

  virtual void generate(int index) {
    switch (index) {
    case  0:
      if (_from != noreg) {
        ld1(_data, T16B, _from); // get 16 bytes of input
      }
      break;
    case  1:
      if (_once) {
        cmpw(_keylen, 52);
        br(Assembler::LO, _rounds_44);
        br(Assembler::EQ, _rounds_52);
      }
      break;
    case  2:  aes_round(_data, _subkeys +  0);  break;
    case  3:  aes_round(_data, _subkeys +  1);  break;
    case  4:
      if (_once)  bind(_rounds_52);
      break;
    case  5:  aes_round(_data, _subkeys +  2);  break;
    case  6:  aes_round(_data, _subkeys +  3);  break;
    case  7:
      if (_once)  bind(_rounds_44);
      break;
    case  8:  aes_round(_data, _subkeys +  4);  break;
    case  9:  aes_round(_data, _subkeys +  5);  break;
    case 10:  aes_round(_data, _subkeys +  6);  break;
    case 11:  aes_round(_data, _subkeys +  7);  break;
    case 12:  aes_round(_data, _subkeys +  8);  break;
    case 13:  aes_round(_data, _subkeys +  9);  break;
    case 14:  aes_round(_data, _subkeys + 10);  break;
    case 15:  aes_round(_data, _subkeys + 11);  break;
    case 16:  aes_round(_data, _subkeys + 12);  break;
    case 17:  aese(_data, _subkeys + 13);  break;
    case 18:  eor(_data, T16B, _data, _subkeys + 14);  break;
    case 19:
      if (_to != noreg) {
        st1(_data, T16B, _to);
      }
      break;
    default: ShouldNotReachHere();
    }
  }

  virtual KernelGenerator *next() {
    return new AESKernelGenerator(this, _unrolls,
                                  _from, _to, _keylen,
                                  _data + 1, _subkeys, /*once*/false);
  }

  virtual int length() { return 20; }
};

// Uses expanded key in v17..v31
// Returns encrypted values in inputs.
// If to != noreg, store value at to; likewise from
// Preserves key, keylen
// Increments from, to
// Input data in v0, v1, ...
// unrolls controls the number of times to unroll the generated function
void MacroAssembler::aesecb_encrypt(Register from, Register to, Register keylen,
                                    FloatRegister data, int unrolls) {
  AESKernelGenerator(this, unrolls, from, to, keylen, data, v17) .unroll();
}

// ghash_multiply and ghash_reduce are the non-unrolled versions of
// the GHASH function generators.
void MacroAssembler::ghash_multiply(FloatRegister result_lo, FloatRegister result_hi,
                                     FloatRegister a, FloatRegister b, FloatRegister a1_xor_a0,
                                     FloatRegister tmp1, FloatRegister tmp2, FloatRegister tmp3) {
  // Karatsuba multiplication performs a 128*128 -> 256-bit
  // multiplication in three 128-bit multiplications and a few
  // additions.
  //
  // (C1:C0) = A1*B1, (D1:D0) = A0*B0, (E1:E0) = (A0+A1)(B0+B1)
  // (A1:A0)(B1:B0) = C1:(C0+C1+D1+E1):(D1+C0+D0+E0):D0
  //
  // Inputs:
  //
  // A0 in a.d[0]     (subkey)
  // A1 in a.d[1]
  // (A1+A0) in a1_xor_a0.d[0]
  //
  // B0 in b.d[0]     (state)
  // B1 in b.d[1]

  ext(tmp1, T16B, b, b, 0x08);
  pmull2(result_hi, T1Q, b, a, T2D);  // A1*B1
  eor(tmp1, T16B, tmp1, b);           // (B1+B0)
  pmull(result_lo,  T1Q, b, a, T1D);  // A0*B0
  pmull(tmp2, T1Q, tmp1, a1_xor_a0, T1D); // (A1+A0)(B1+B0)

  ext(tmp1, T16B, result_lo, result_hi, 0x08);
  eor(tmp3, T16B, result_hi, result_lo); // A1*B1+A0*B0
  eor(tmp2, T16B, tmp2, tmp1);
  eor(tmp2, T16B, tmp2, tmp3);

  // Register pair <result_hi:result_lo> holds the result of carry-less multiplication
  ins(result_hi, D, tmp2, 0, 1);
  ins(result_lo, D, tmp2, 1, 0);
}

void MacroAssembler::ghash_reduce(FloatRegister result, FloatRegister lo, FloatRegister hi,
                  FloatRegister p, FloatRegister vzr, FloatRegister t1) {
  const FloatRegister t0 = result;

  // The GCM field polynomial f is z^128 + p(z), where p =
  // z^7+z^2+z+1.
  //
  //    z^128 === -p(z)  (mod (z^128 + p(z)))
  //
  // so, given that the product we're reducing is
  //    a == lo + hi * z^128
  // substituting,
  //      === lo - hi * p(z)  (mod (z^128 + p(z)))
  //
  // we reduce by multiplying hi by p(z) and subtracting the result
  // from (i.e. XORing it with) lo.  Because p has no nonzero high
  // bits we can do this with two 64-bit multiplications, lo*p and
  // hi*p.

  pmull2(t0, T1Q, hi, p, T2D);
  ext(t1, T16B, t0, vzr, 8);
  eor(hi, T16B, hi, t1);
  ext(t1, T16B, vzr, t0, 8);
  eor(lo, T16B, lo, t1);
  pmull(t0, T1Q, hi, p, T1D);
  eor(result, T16B, lo, t0);
}

class GHASHMultiplyGenerator: public KernelGenerator {
  FloatRegister _result_lo, _result_hi, _b,
    _a, _vzr, _a1_xor_a0, _p,
    _tmp1, _tmp2, _tmp3;

public:
  GHASHMultiplyGenerator(Assembler *as, int unrolls,
                         /* offsetted registers */
                         FloatRegister result_lo, FloatRegister result_hi,
                         FloatRegister b,
                         /* non-offsetted (shared) registers */
                         FloatRegister a, FloatRegister a1_xor_a0, FloatRegister p, FloatRegister vzr,
                         /* offseted (temp) registers */
                         FloatRegister tmp1, FloatRegister tmp2, FloatRegister tmp3)
    : KernelGenerator(as, unrolls),
      _result_lo(result_lo), _result_hi(result_hi), _b(b),
      _a(a), _vzr(vzr), _a1_xor_a0(a1_xor_a0), _p(p),
      _tmp1(tmp1), _tmp2(tmp2), _tmp3(tmp3) { }

  int register_stride = 7;

  virtual void generate(int index) {
    // Karatsuba multiplication performs a 128*128 -> 256-bit
    // multiplication in three 128-bit multiplications and a few
    // additions.
    //
    // (C1:C0) = A1*B1, (D1:D0) = A0*B0, (E1:E0) = (A0+A1)(B0+B1)
    // (A1:A0)(B1:B0) = C1:(C0+C1+D1+E1):(D1+C0+D0+E0):D0
    //
    // Inputs:
    //
    // A0 in a.d[0]     (subkey)
    // A1 in a.d[1]
    // (A1+A0) in a1_xor_a0.d[0]
    //
    // B0 in b.d[0]     (state)
    // B1 in b.d[1]

    switch (index) {
      case  0:  ext(_tmp1, T16B, _b, _b, 0x08);  break;
      case  1:  pmull2(_result_hi, T1Q, _b, _a, T2D);  // A1*B1
        break;
      case  2:  eor(_tmp1, T16B, _tmp1, _b);           // (B1+B0)
        break;
      case  3:  pmull(_result_lo,  T1Q, _b, _a, T1D);  // A0*B0
        break;
      case  4:  pmull(_tmp2, T1Q, _tmp1, _a1_xor_a0, T1D); // (A1+A0)(B1+B0)
        break;

      case  5:  ext(_tmp1, T16B, _result_lo, _result_hi, 0x08);  break;
      case  6:  eor(_tmp3, T16B, _result_hi, _result_lo); // A1*B1+A0*B0
        break;
      case  7:  eor(_tmp2, T16B, _tmp2, _tmp1);  break;
      case  8:  eor(_tmp2, T16B, _tmp2, _tmp3);  break;

        // Register pair <_result_hi:_result_lo> holds the _result of carry-less multiplication
      case  9:  ins(_result_hi, D, _tmp2, 0, 1);  break;
      case 10:  ins(_result_lo, D, _tmp2, 1, 0);  break;
      default: ShouldNotReachHere();
    }
  }

  virtual KernelGenerator *next() {
    GHASHMultiplyGenerator *result = new GHASHMultiplyGenerator(*this);
    result->_result_lo += register_stride;
    result->_result_hi += register_stride;
    result->_b += register_stride;
    result->_tmp1 += register_stride;
    result->_tmp2 += register_stride;
    result->_tmp3 += register_stride;
    return result;
  }

  virtual int length() { return 11; }
};

// Reduce the 128-bit product in hi:lo by the GCM field polynomial.
// The FloatRegister argument called data is optional: if it is a
// valid register, we interleave LD1 instructions with the
// reduction. This is to reduce latency next time around the loop.
class GHASHReduceGenerator: public KernelGenerator {
  FloatRegister _result, _lo, _hi, _p, _vzr, _data, _t1;
  int _once;
public:
  GHASHReduceGenerator(Assembler *as, int unrolls,
                       /* offsetted registers */
                       FloatRegister result, FloatRegister lo, FloatRegister hi,
                       /* non-offsetted (shared) registers */
                       FloatRegister p, FloatRegister vzr, FloatRegister data,
                       /* offseted (temp) registers */
                       FloatRegister t1)
    : KernelGenerator(as, unrolls),
      _result(result), _lo(lo), _hi(hi),
      _p(p), _vzr(vzr), _data(data), _t1(t1), _once(true) { }

  int register_stride = 7;

  virtual void generate(int index) {
    const FloatRegister t0 = _result;

    switch (index) {
      // The GCM field polynomial f is z^128 + p(z), where p =
      // z^7+z^2+z+1.
      //
      //    z^128 === -p(z)  (mod (z^128 + p(z)))
      //
      // so, given that the product we're reducing is
      //    a == lo + hi * z^128
      // substituting,
      //      === lo - hi * p(z)  (mod (z^128 + p(z)))
      //
      // we reduce by multiplying hi by p(z) and subtracting the _result
      // from (i.e. XORing it with) lo.  Because p has no nonzero high
      // bits we can do this with two 64-bit multiplications, lo*p and
      // hi*p.

      case  0:  pmull2(t0, T1Q, _hi, _p, T2D);  break;
      case  1:  ext(_t1, T16B, t0, _vzr, 8);  break;
      case  2:  eor(_hi, T16B, _hi, _t1);  break;
      case  3:  ext(_t1, T16B, _vzr, t0, 8);  break;
      case  4:  eor(_lo, T16B, _lo, _t1);  break;
      case  5:  pmull(t0, T1Q, _hi, _p, T1D);  break;
      case  6:  eor(_result, T16B, _lo, t0);  break;
      default: ShouldNotReachHere();
    }

    // Sprinkle load instructions into the generated instructions
    if (_data->is_valid() && _once) {
      assert(length() >= unrolls(), "not enough room for inteleaved loads");
      if (index < unrolls()) {
        ld1((_data + index*register_stride), T16B, post(r2, 0x10));
      }
    }
  }

  virtual KernelGenerator *next() {
    GHASHReduceGenerator *result = new GHASHReduceGenerator(*this);
    result->_result += register_stride;
    result->_hi += register_stride;
    result->_lo += register_stride;
    result->_t1 += register_stride;
    result->_once = false;
    return result;
  }

 int length() { return 7; }
};

// Perform a GHASH multiply/reduce on a single FloatRegister.
void MacroAssembler::ghash_modmul(FloatRegister result,
                                  FloatRegister result_lo, FloatRegister result_hi, FloatRegister b,
                                  FloatRegister a, FloatRegister vzr, FloatRegister a1_xor_a0, FloatRegister p,
                                  FloatRegister t1, FloatRegister t2, FloatRegister t3) {
  ghash_multiply(result_lo, result_hi, a, b, a1_xor_a0, t1, t2, t3);
  ghash_reduce(result, result_lo, result_hi, p, vzr, t1);
}

// Interleaved GHASH processing.
//
// Clobbers all vector registers.
//
void MacroAssembler::ghash_processBlocks_wide(address field_polynomial, Register state,
                                              Register subkeyH,
                                              Register data, Register blocks, int unrolls) {
  int register_stride = 7;

  // Bafflingly, GCM uses little-endian for the byte order, but
  // big-endian for the bit order.  For example, the polynomial 1 is
  // represented as the 16-byte string 80 00 00 00 | 12 bytes of 00.
  //
  // So, we must either reverse the bytes in each word and do
  // everything big-endian or reverse the bits in each byte and do
  // it little-endian.  On AArch64 it's more idiomatic to reverse
  // the bits in each byte (we have an instruction, RBIT, to do
  // that) and keep the data in little-endian bit order throught the
  // calculation, bit-reversing the inputs and outputs.

  assert(unrolls * register_stride < 32, "out of registers");

  FloatRegister a1_xor_a0 = v28;
  FloatRegister Hprime = v29;
  FloatRegister vzr = v30;
  FloatRegister p = v31;
  eor(vzr, T16B, vzr, vzr); // zero register

  ldrq(p, field_polynomial);    // The field polynomial

  ldrq(v0, Address(state));
  ldrq(Hprime, Address(subkeyH));

  rev64(v0, T16B, v0);          // Bit-reverse words in state and subkeyH
  rbit(v0, T16B, v0);
  rev64(Hprime, T16B, Hprime);
  rbit(Hprime, T16B, Hprime);

  // Powers of H -> Hprime

  Label already_calculated, done;
  {
    // The first time around we'll have to calculate H**2, H**3, etc.
    // Look at the largest power of H in the subkeyH array to see if
    // it's already been calculated.
    ldp(rscratch1, rscratch2, Address(subkeyH, 16 * (unrolls - 1)));
    orr(rscratch1, rscratch1, rscratch2);
    cbnz(rscratch1, already_calculated);

    orr(v6, T16B, Hprime, Hprime);  // Start with H in v6 and Hprime
    for (int i = 1; i < unrolls; i++) {
      ext(a1_xor_a0, T16B, Hprime, Hprime, 0x08); // long-swap subkeyH into a1_xor_a0
      eor(a1_xor_a0, T16B, a1_xor_a0, Hprime);    // xor subkeyH into subkeyL (Karatsuba: (A1+A0))
      ghash_modmul(/*result*/v6, /*result_lo*/v5, /*result_hi*/v4, /*b*/v6,
                   Hprime, vzr, a1_xor_a0, p,
                   /*temps*/v1, v3, v2);
      rev64(v1, T16B, v6);
      rbit(v1, T16B, v1);
      strq(v1, Address(subkeyH, 16 * i));
    }
    b(done);
  }
  {
    bind(already_calculated);

    // Load the largest power of H we need into v6.
    ldrq(v6, Address(subkeyH, 16 * (unrolls - 1)));
    rev64(v6, T16B, v6);
    rbit(v6, T16B, v6);
  }
  bind(done);

  orr(Hprime, T16B, v6, v6);     // Move H ** unrolls into Hprime

  // Hprime contains (H ** 1, H ** 2, ... H ** unrolls)
  // v0 contains the initial state. Clear the others.
  for (int i = 1; i < unrolls; i++) {
    int ofs = register_stride * i;
    eor(ofs+v0, T16B, ofs+v0, ofs+v0); // zero each state register
  }

  ext(a1_xor_a0, T16B, Hprime, Hprime, 0x08); // long-swap subkeyH into a1_xor_a0
  eor(a1_xor_a0, T16B, a1_xor_a0, Hprime);    // xor subkeyH into subkeyL (Karatsuba: (A1+A0))

  // Load #unrolls blocks of data
  for (int ofs = 0; ofs < unrolls * register_stride; ofs += register_stride) {
    ld1(v2+ofs, T16B, post(data, 0x10));
  }

  // Register assignments, replicated across 4 clones, v0 ... v23
  //
  // v0: input / output: current state, result of multiply/reduce
  // v1: temp
  // v2: input: one block of data (the ciphertext)
  //     also used as a temp once the data has been consumed
  // v3: temp
  // v4: output: high part of product
  // v5: output: low part ...
  // v6: unused
  //
  // Not replicated:
  //
  // v28: High part of H xor low part of H'
  // v29: H' (hash subkey)
  // v30: zero
  // v31: Reduction polynomial of the Galois field

  // Inner loop.
  // Do the whole load/add/multiply/reduce over all our data except
  // the last few rows.
  {
    Label L_ghash_loop;
    bind(L_ghash_loop);

    // Prefetching doesn't help here. In fact, on Neoverse N1 it's worse.
    // prfm(Address(data, 128), PLDL1KEEP);

    // Xor data into current state
    for (int ofs = 0; ofs < unrolls * register_stride; ofs += register_stride) {
      rbit((v2+ofs), T16B, (v2+ofs));
      eor((v2+ofs), T16B, v0+ofs, (v2+ofs));   // bit-swapped data ^ bit-swapped state
    }

    // Generate fully-unrolled multiply-reduce in two stages.

    GHASHMultiplyGenerator(this, unrolls,
                           /*result_lo*/v5, /*result_hi*/v4, /*data*/v2,
                           Hprime, a1_xor_a0, p, vzr,
                           /*temps*/v1, v3, /* reuse b*/v2) .unroll();

    // NB: GHASHReduceGenerator also loads the next #unrolls blocks of
    // data into v0, v0+ofs, the current state.
    GHASHReduceGenerator (this, unrolls,
                          /*result*/v0, /*lo*/v5, /*hi*/v4, p, vzr,
                          /*data*/v2, /*temp*/v3) .unroll();

    sub(blocks, blocks, unrolls);
    cmp(blocks, (unsigned char)(unrolls * 2));
    br(GE, L_ghash_loop);
  }

  // Merge the #unrolls states.  Note that the data for the next
  // iteration has already been loaded into v4, v4+ofs, etc...

  // First, we multiply/reduce each clone by the appropriate power of H.
  for (int i = 0; i < unrolls; i++) {
    int ofs = register_stride * i;
    ldrq(Hprime, Address(subkeyH, 16 * (unrolls - i - 1)));

    rbit(v2+ofs, T16B, v2+ofs);
    eor(v2+ofs, T16B, ofs+v0, v2+ofs);   // bit-swapped data ^ bit-swapped state

    rev64(Hprime, T16B, Hprime);
    rbit(Hprime, T16B, Hprime);
    ext(a1_xor_a0, T16B, Hprime, Hprime, 0x08); // long-swap subkeyH into a1_xor_a0
    eor(a1_xor_a0, T16B, a1_xor_a0, Hprime);    // xor subkeyH into subkeyL (Karatsuba: (A1+A0))
    ghash_modmul(/*result*/v0+ofs, /*result_lo*/v5+ofs, /*result_hi*/v4+ofs, /*b*/v2+ofs,
                 Hprime, vzr, a1_xor_a0, p,
                 /*temps*/v1+ofs, v3+ofs, /* reuse b*/v2+ofs);
  }

  // Then we sum the results.
  for (int i = 0; i < unrolls - 1; i++) {
    int ofs = register_stride * i;
    eor(v0, T16B, v0, v0 + register_stride + ofs);
  }

  sub(blocks, blocks, (unsigned char)unrolls);

  // And finally bit-reverse the state back to big endian.
  rev64(v0, T16B, v0);
  rbit(v0, T16B, v0);
  st1(v0, T16B, state);
}
