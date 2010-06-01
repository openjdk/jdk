/*
 * Copyright (c) 2002, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

// -*- C++ -*-
// Small program for unpacking specially compressed Java packages.
// John R. Rose

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdarg.h>

#include "defines.h"
#include "bytes.h"
#include "utils.h"
#include "coding.h"

#include "constants.h"
#include "unpack.h"

extern coding basic_codings[];

#define CODING_PRIVATE(spec) \
  int spec_ = spec; \
  int B = CODING_B(spec_); \
  int H = CODING_H(spec_); \
  int L = 256 - H; \
  int S = CODING_S(spec_); \
  int D = CODING_D(spec_)

#define IS_NEG_CODE(S, codeVal) \
  ( (((int)(codeVal)+1) & ((1<<S)-1)) == 0 )

#define DECODE_SIGN_S1(ux) \
  ( ((uint)(ux) >> 1) ^ -((int)(ux) & 1) )

static maybe_inline
int decode_sign(int S, uint ux) {  // == Coding.decodeSign32
  assert(S > 0);
  uint sigbits = (ux >> S);
  if (IS_NEG_CODE(S, ux))
    return (int)(    ~sigbits);
  else
    return (int)(ux - sigbits);
  // Note that (int)(ux-sigbits) can be negative, if ux is large enough.
}

coding* coding::init() {
  if (umax > 0)  return this;  // already done
  assert(spec != 0);  // sanity

  // fill in derived fields
  CODING_PRIVATE(spec);

  // Return null if 'arb(BHSD)' parameter constraints are not met:
  if (B < 1 || B > B_MAX)  return null;
  if (H < 1 || H > 256)    return null;
  if (S < 0 || S > 2)      return null;
  if (D < 0 || D > 1)      return null;
  if (B == 1 && H != 256)  return null;  // 1-byte coding must be fixed-size
  if (B >= 5 && H == 256)  return null;  // no 5-byte fixed-size coding

  // first compute the range of the coding, in 64 bits
  jlong range = 0;
  {
    jlong H_i = 1;
    for (int i = 0; i < B; i++) {
      range += H_i;
      H_i *= H;
    }
    range *= L;
    range += H_i;
  }
  assert(range > 0);  // no useless codings, please

  int this_umax;

  // now, compute min and max
  if (range >= ((jlong)1 << 32)) {
    this_umax  = INT_MAX_VALUE;
    this->umin = INT_MIN_VALUE;
    this->max  = INT_MAX_VALUE;
    this->min  = INT_MIN_VALUE;
  } else {
    this_umax = (range > INT_MAX_VALUE) ? INT_MAX_VALUE : (int)range-1;
    this->max = this_umax;
    this->min = this->umin = 0;
    if (S != 0 && range != 0) {
      int Smask = (1<<S)-1;
      jlong maxPosCode = range-1;
      jlong maxNegCode = range-1;
      while (IS_NEG_CODE(S,  maxPosCode))  --maxPosCode;
      while (!IS_NEG_CODE(S, maxNegCode))  --maxNegCode;
      int maxPos = decode_sign(S, (uint)maxPosCode);
      if (maxPos < 0)
        this->max = INT_MAX_VALUE;  // 32-bit wraparound
      else
        this->max = maxPos;
      if (maxNegCode < 0)
        this->min = 0;  // No negative codings at all.
      else
        this->min = decode_sign(S, (uint)maxNegCode);
    }
  }

  assert(!(isFullRange | isSigned | isSubrange)); // init
  if (min < 0)
    this->isSigned = true;
  if (max < INT_MAX_VALUE && range <= INT_MAX_VALUE)
    this->isSubrange = true;
  if (max == INT_MAX_VALUE && min == INT_MIN_VALUE)
    this->isFullRange = true;

  // do this last, to reduce MT exposure (should have a membar too)
  this->umax = this_umax;

  return this;
}

coding* coding::findBySpec(int spec) {
  for (coding* scan = &basic_codings[0]; ; scan++) {
    if (scan->spec == spec)
      return scan->init();
    if (scan->spec == 0)
      break;
  }
  coding* ptr = NEW(coding, 1);
  CHECK_NULL_0(ptr);
  coding* c = ptr->initFrom(spec);
  if (c == null) {
    mtrace('f', ptr, 0);
    ::free(ptr);
  } else
    // else caller should free it...
    c->isMalloc = true;
  return c;
}

coding* coding::findBySpec(int B, int H, int S, int D) {
  if (B < 1 || B > B_MAX)  return null;
  if (H < 1 || H > 256)    return null;
  if (S < 0 || S > 2)      return null;
  if (D < 0 || D > 1)      return null;
  return findBySpec(CODING_SPEC(B, H, S, D));
}

void coding::free() {
  if (isMalloc) {
    mtrace('f', this, 0);
    ::free(this);
  }
}

void coding_method::reset(value_stream* state) {
  assert(state->rp == state->rplimit);  // not in mid-stream, please
  //assert(this == vs0.cm);
  state[0] = vs0;
  if (uValues != null) {
    uValues->reset(state->helper());
  }
}

maybe_inline
uint coding::parse(byte* &rp, int B, int H) {
  int L = 256-H;
  byte* ptr = rp;
  // hand peel the i==0 part of the loop:
  uint b_i = *ptr++ & 0xFF;
  if (B == 1 || b_i < (uint)L)
    { rp = ptr; return b_i; }
  uint sum = b_i;
  uint H_i = H;
  assert(B <= B_MAX);
  for (int i = 2; i <= B_MAX; i++) { // easy for compilers to unroll if desired
    b_i = *ptr++ & 0xFF;
    sum += b_i * H_i;
    if (i == B || b_i < (uint)L)
      { rp = ptr; return sum; }
    H_i *= H;
  }
  assert(false);
  return 0;
}

maybe_inline
uint coding::parse_lgH(byte* &rp, int B, int H, int lgH) {
  assert(H == (1<<lgH));
  int L = 256-(1<<lgH);
  byte* ptr = rp;
  // hand peel the i==0 part of the loop:
  uint b_i = *ptr++ & 0xFF;
  if (B == 1 || b_i < (uint)L)
    { rp = ptr; return b_i; }
  uint sum = b_i;
  uint lg_H_i = lgH;
  assert(B <= B_MAX);
  for (int i = 2; i <= B_MAX; i++) { // easy for compilers to unroll if desired
    b_i = *ptr++ & 0xFF;
    sum += b_i << lg_H_i;
    if (i == B || b_i < (uint)L)
      { rp = ptr; return sum; }
    lg_H_i += lgH;
  }
  assert(false);
  return 0;
}

static const char ERB[] = "EOF reading band";

maybe_inline
void coding::parseMultiple(byte* &rp, int N, byte* limit, int B, int H) {
  if (N < 0) {
    abort("bad value count");
    return;
  }
  byte* ptr = rp;
  if (B == 1 || H == 256) {
    size_t len = (size_t)N*B;
    if (len / B != (size_t)N || ptr+len > limit) {
      abort(ERB);
      return;
    }
    rp = ptr+len;
    return;
  }
  // Note:  We assume rp has enough zero-padding.
  int L = 256-H;
  int n = B;
  while (N > 0) {
    ptr += 1;
    if (--n == 0) {
      // end of encoding at B bytes, regardless of byte value
    } else {
      int b = (ptr[-1] & 0xFF);
      if (b >= L) {
        // keep going, unless we find a byte < L
        continue;
      }
    }
    // found the last byte
    N -= 1;
    n = B;   // reset length counter
    // do an error check here
    if (ptr > limit) {
      abort(ERB);
      return;
    }
  }
  rp = ptr;
  return;
}

bool value_stream::hasHelper() {
  // If my coding method is a pop-style method,
  // then I need a second value stream to transmit
  // unfavored values.
  // This can be determined by examining fValues.
  return cm->fValues != null;
}

void value_stream::init(byte* rp_, byte* rplimit_, coding* defc) {
  rp = rp_;
  rplimit = rplimit_;
  sum = 0;
  cm = null;  // no need in the simple case
  setCoding(defc);
}

void value_stream::setCoding(coding* defc) {
  if (defc == null) {
    unpack_abort("bad coding");
    defc = coding::findByIndex(_meta_canon_min);  // random pick for recovery
  }

  c = (*defc);

  // choose cmk
  cmk = cmk_ERROR;
  switch (c.spec) {
  case BYTE1_spec:      cmk = cmk_BYTE1;        break;
  case CHAR3_spec:      cmk = cmk_CHAR3;        break;
  case UNSIGNED5_spec:  cmk = cmk_UNSIGNED5;    break;
  case DELTA5_spec:     cmk = cmk_DELTA5;       break;
  case BCI5_spec:       cmk = cmk_BCI5;         break;
  case BRANCH5_spec:    cmk = cmk_BRANCH5;      break;
  default:
    if (c.D() == 0) {
      switch (c.S()) {
      case 0:  cmk = cmk_BHS0;  break;
      case 1:  cmk = cmk_BHS1;  break;
      default: cmk = cmk_BHS;   break;
      }
    } else {
      if (c.S() == 1) {
        if (c.isFullRange)   cmk = cmk_BHS1D1full;
        if (c.isSubrange)    cmk = cmk_BHS1D1sub;
      }
      if (cmk == cmk_ERROR)  cmk = cmk_BHSD1;
    }
  }
}

static maybe_inline
int getPopValue(value_stream* self, uint uval) {
  if (uval > 0) {
    // note that the initial parse performed a range check
    assert(uval <= (uint)self->cm->fVlength);
    return self->cm->fValues[uval-1];
  } else {
    // take an unfavored value
    return self->helper()->getInt();
  }
}

maybe_inline
int coding::sumInUnsignedRange(int x, int y) {
  assert(isSubrange);
  int range = (int)(umax+1);
  assert(range > 0);
  x += y;
  if (x != (int)((jlong)(x-y) + (jlong)y)) {
    // 32-bit overflow interferes with range reduction.
    // Back off from the overflow by adding a multiple of range:
    if (x < 0) {
      x -= range;
      assert(x >= 0);
    } else {
      x += range;
      assert(x < 0);
    }
  }
  if (x < 0) {
    x += range;
    if (x >= 0)  return x;
  } else if (x >= range) {
    x -= range;
    if (x < range)  return x;
  } else {
    // in range
    return x;
  }
  // do it the hard way
  x %= range;
  if (x < 0)  x += range;
  return x;
}

static maybe_inline
int getDeltaValue(value_stream* self, uint uval, bool isSubrange) {
  assert((uint)(self->c.isSubrange) == (uint)isSubrange);
  assert(self->c.isSubrange | self->c.isFullRange);
  if (isSubrange)
    return self->sum = self->c.sumInUnsignedRange(self->sum, (int)uval);
  else
    return self->sum += (int) uval;
}

bool value_stream::hasValue() {
  if (rp < rplimit)      return true;
  if (cm == null)        return false;
  if (cm->next == null)  return false;
  cm->next->reset(this);
  return hasValue();
}

int value_stream::getInt() {
  if (rp >= rplimit) {
    // Advance to next coding segment.
    if (rp > rplimit || cm == null || cm->next == null) {
      // Must perform this check and throw an exception on bad input.
      unpack_abort(ERB);
      return 0;
    }
    cm->next->reset(this);
    return getInt();
  }

  CODING_PRIVATE(c.spec);
  uint uval;
  enum {
    B5 = 5,
    B3 = 3,
    H128 = 128,
    H64 = 64,
    H4 = 4
  };
  switch (cmk) {
  case cmk_BHS:
    assert(D == 0);
    uval = coding::parse(rp, B, H);
    if (S == 0)
      return (int) uval;
    return decode_sign(S, uval);

  case cmk_BHS0:
    assert(S == 0 && D == 0);
    uval = coding::parse(rp, B, H);
    return (int) uval;

  case cmk_BHS1:
    assert(S == 1 && D == 0);
    uval = coding::parse(rp, B, H);
    return DECODE_SIGN_S1(uval);

  case cmk_BYTE1:
    assert(c.spec == BYTE1_spec);
    assert(B == 1 && H == 256 && S == 0 && D == 0);
    return *rp++ & 0xFF;

  case cmk_CHAR3:
    assert(c.spec == CHAR3_spec);
    assert(B == B3 && H == H128 && S == 0 && D == 0);
    return coding::parse_lgH(rp, B3, H128, 7);

  case cmk_UNSIGNED5:
    assert(c.spec == UNSIGNED5_spec);
    assert(B == B5 && H == H64 && S == 0 && D == 0);
    return coding::parse_lgH(rp, B5, H64, 6);

  case cmk_BHSD1:
    assert(D == 1);
    uval = coding::parse(rp, B, H);
    if (S != 0)
      uval = (uint) decode_sign(S, uval);
    return getDeltaValue(this, uval, (bool)c.isSubrange);

  case cmk_BHS1D1full:
    assert(S == 1 && D == 1 && c.isFullRange);
    uval = coding::parse(rp, B, H);
    uval = (uint) DECODE_SIGN_S1(uval);
    return getDeltaValue(this, uval, false);

  case cmk_BHS1D1sub:
    assert(S == 1 && D == 1 && c.isSubrange);
    uval = coding::parse(rp, B, H);
    uval = (uint) DECODE_SIGN_S1(uval);
    return getDeltaValue(this, uval, true);

  case cmk_DELTA5:
    assert(c.spec == DELTA5_spec);
    assert(B == B5 && H == H64 && S == 1 && D == 1 && c.isFullRange);
    uval = coding::parse_lgH(rp, B5, H64, 6);
    sum += DECODE_SIGN_S1(uval);
    return sum;

  case cmk_BCI5:
    assert(c.spec == BCI5_spec);
    assert(B == B5 && H == H4 && S == 0 && D == 0);
    return coding::parse_lgH(rp, B5, H4, 2);

  case cmk_BRANCH5:
    assert(c.spec == BRANCH5_spec);
    assert(B == B5 && H == H4 && S == 2 && D == 0);
    uval = coding::parse_lgH(rp, B5, H4, 2);
    return decode_sign(S, uval);

  case cmk_pop:
    uval = coding::parse(rp, B, H);
    if (S != 0) {
      uval = (uint) decode_sign(S, uval);
    }
    if (D != 0) {
      assert(c.isSubrange | c.isFullRange);
      if (c.isSubrange)
        sum = c.sumInUnsignedRange(sum, (int) uval);
      else
        sum += (int) uval;
      uval = (uint) sum;
    }
    return getPopValue(this, uval);

  case cmk_pop_BHS0:
    assert(S == 0 && D == 0);
    uval = coding::parse(rp, B, H);
    return getPopValue(this, uval);

  case cmk_pop_BYTE1:
    assert(c.spec == BYTE1_spec);
    assert(B == 1 && H == 256 && S == 0 && D == 0);
    return getPopValue(this, *rp++ & 0xFF);

  default:
    break;
  }
  assert(false);
  return 0;
}

static maybe_inline
int moreCentral(int x, int y) {  // used to find end of Pop.{F}
  // Suggested implementation from the Pack200 specification:
  uint kx = (x >> 31) ^ (x << 1);
  uint ky = (y >> 31) ^ (y << 1);
  return (kx < ky? x: y);
}
//static maybe_inline
//int moreCentral2(int x, int y, int min) {
//  // Strict implementation of buggy 150.7 specification.
//  // The bug is that the spec. says absolute-value ties are broken
//  // in favor of positive numbers, but the suggested implementation
//  // (also mentioned in the spec.) breaks ties in favor of negative numbers.
//  if ((x + y) != 0)
//    return min;
//  else
//    // return the other value, which breaks a tie in the positive direction
//    return (x > y)? x: y;
//}

static const byte* no_meta[] = {null};
#define NO_META (*(byte**)no_meta)
enum { POP_FAVORED_N = -2 };

// mode bits
#define DISABLE_RUN  1  // used immediately inside ACodee
#define DISABLE_POP  2  // used recursively in all pop sub-bands

// This function knows all about meta-coding.
void coding_method::init(byte* &band_rp, byte* band_limit,
                         byte* &meta_rp, int mode,
                         coding* defc, int N,
                         intlist* valueSink) {
  assert(N != 0);

  assert(u != null);  // must be pre-initialized
  //if (u == null)  u = unpacker::current();  // expensive

  int op = (meta_rp == null) ? _meta_default :  (*meta_rp++ & 0xFF);
  coding* foundc = null;
  coding* to_free = null;

  if (op == _meta_default) {
    foundc = defc;
    // and fall through

  } else if (op >= _meta_canon_min && op <= _meta_canon_max) {
    foundc = coding::findByIndex(op);
    // and fall through

  } else if (op == _meta_arb) {
    int args = (*meta_rp++ & 0xFF);
    // args = (D:[0..1] + 2*S[0..2] + 8*(B:[1..5]-1))
    int D = ((args >> 0) & 1);
    int S = ((args >> 1) & 3);
    int B = ((args >> 3) & -1) + 1;
    // & (H[1..256]-1)
    int H = (*meta_rp++ & 0xFF) + 1;
    foundc = coding::findBySpec(B, H, S, D);
    to_free = foundc;  // findBySpec may dynamically allocate
    if (foundc == null) {
      abort("illegal arb. coding");
      return;
    }
    // and fall through

  } else if (op >= _meta_run && op < _meta_pop) {
    int args = (op - _meta_run);
    // args: KX:[0..3] + 4*(KBFlag:[0..1]) + 8*(ABDef:[0..2])
    int KX     = ((args >> 0) & 3);
    int KBFlag = ((args >> 2) & 1);
    int ABDef  = ((args >> 3) & -1);
    assert(ABDef <= 2);
    // & KB: one of [0..255] if KBFlag=1
    int KB     = (!KBFlag? 3: (*meta_rp++ & 0xFF));
    int K      = (KB+1) << (KX * 4);
    int N2 = (N >= 0) ? N-K : N;
    if (N == 0 || (N2 <= 0 && N2 != N)) {
      abort("illegal run encoding");
      return;
    }
    if ((mode & DISABLE_RUN) != 0) {
      abort("illegal nested run encoding");
      return;
    }

    // & Enc{ ACode } if ADef=0  (ABDef != 1)
    // No direct nesting of 'run' in ACode, but in BCode it's OK.
    int disRun = mode | DISABLE_RUN;
    if (ABDef == 1) {
      this->init(band_rp, band_limit, NO_META, disRun, defc, K, valueSink);
    } else {
      this->init(band_rp, band_limit, meta_rp, disRun, defc, K, valueSink);
    }
    CHECK;

    // & Enc{ BCode } if BDef=0  (ABDef != 2)
    coding_method* tail = U_NEW(coding_method, 1);
    CHECK_NULL(tail);
    tail->u = u;

    // The 'run' codings may be nested indirectly via 'pop' codings.
    // This means that this->next may already be filled in, if
    // ACode was of type 'pop' with a 'run' token coding.
    // No problem:  Just chain the upcoming BCode onto the end.
    for (coding_method* self = this; ; self = self->next) {
      if (self->next == null) {
        self->next = tail;
        break;
      }
    }

    if (ABDef == 2) {
      tail->init(band_rp, band_limit, NO_META, mode, defc, N2, valueSink);
    } else {
      tail->init(band_rp, band_limit, meta_rp, mode, defc, N2, valueSink);
    }
    // Note:  The preceding calls to init should be tail-recursive.

    return;  // done; no falling through

  } else if (op >= _meta_pop && op < _meta_limit) {
    int args = (op - _meta_pop);
    // args: (FDef:[0..1]) + 2*UDef:[0..1] + 4*(TDefL:[0..11])
    int FDef  = ((args >> 0) & 1);
    int UDef  = ((args >> 1) & 1);
    int TDefL = ((args >> 2) & -1);
    assert(TDefL <= 11);
    int TDef  = (TDefL > 0);
    int TL    = (TDefL <= 6) ? (2 << TDefL) : (256 - (4 << (11-TDefL)));
    int TH    = (256-TL);
    if (N <= 0) {
      abort("illegal pop encoding");
      return;
    }
    if ((mode & DISABLE_POP) != 0) {
      abort("illegal nested pop encoding");
      return;
    }

    // No indirect nesting of 'pop', but 'run' is OK.
    int disPop = DISABLE_POP;

    // & Enc{ FCode } if FDef=0
    int FN = POP_FAVORED_N;
    assert(valueSink == null);
    intlist fValueSink; fValueSink.init();
    coding_method fval;
    BYTES_OF(fval).clear(); fval.u = u;
    if (FDef != 0) {
      fval.init(band_rp, band_limit, NO_META, disPop, defc, FN, &fValueSink);
    } else {
      fval.init(band_rp, band_limit, meta_rp, disPop, defc, FN, &fValueSink);
    }
    bytes fvbuf;
    fValues  = (u->saveTo(fvbuf, fValueSink.b), (int*) fvbuf.ptr);
    fVlength = fValueSink.length();  // i.e., the parameter K
    fValueSink.free();
    CHECK;

    // Skip the first {F} run in all subsequent passes.
    // The next call to this->init(...) will set vs0.rp to point after the {F}.

    // & Enc{ TCode } if TDef=0  (TDefL==0)
    if (TDef != 0) {
      coding* tcode = coding::findBySpec(1, 256);  // BYTE1
      // find the most narrowly sufficient code:
      for (int B = 2; B <= B_MAX; B++) {
        if (fVlength <= tcode->umax)  break;  // found it
        tcode->free();
        tcode = coding::findBySpec(B, TH);
        CHECK_NULL(tcode);
      }
      if (!(fVlength <= tcode->umax)) {
        abort("pop.L value too small");
        return;
      }
      this->init(band_rp, band_limit, NO_META, disPop, tcode, N, null);
      tcode->free();
    } else {
      this->init(band_rp, band_limit, meta_rp, disPop,  defc, N, null);
    }
    CHECK;

    // Count the number of zero tokens right now.
    // Also verify that they are in bounds.
    int UN = 0;   // one {U} for each zero in {T}
    value_stream vs = vs0;
    for (int i = 0; i < N; i++) {
      uint val = vs.getInt();
      if (val == 0)  UN += 1;
      if (!(val <= (uint)fVlength)) {
        abort("pop token out of range");
        return;
      }
    }
    vs.done();

    // & Enc{ UCode } if UDef=0
    if (UN != 0) {
      uValues = U_NEW(coding_method, 1);
      CHECK_NULL(uValues);
      uValues->u = u;
      if (UDef != 0) {
        uValues->init(band_rp, band_limit, NO_META, disPop, defc, UN, null);
      } else {
        uValues->init(band_rp, band_limit, meta_rp, disPop, defc, UN, null);
      }
    } else {
      if (UDef == 0) {
        int uop = (*meta_rp++ & 0xFF);
        if (uop > _meta_canon_max)
          // %%% Spec. requires the more strict (uop != _meta_default).
          abort("bad meta-coding for empty pop/U");
      }
    }

    // Bug fix for 6259542
    // Last of all, adjust vs0.cmk to the 'pop' flavor
    for (coding_method* self = this; self != null; self = self->next) {
        coding_method_kind cmk2 = cmk_pop;
        switch (self->vs0.cmk) {
        case cmk_BHS0:   cmk2 = cmk_pop_BHS0;   break;
        case cmk_BYTE1:  cmk2 = cmk_pop_BYTE1;  break;
        default: break;
        }
        self->vs0.cmk = cmk2;
        if (self != this) {
          assert(self->fValues == null); // no double init
          self->fValues  = this->fValues;
          self->fVlength = this->fVlength;
          assert(self->uValues == null); // must stay null
        }
    }

    return;  // done; no falling through

  } else {
    abort("bad meta-coding");
    return;
  }

  // Common code here skips a series of values with one coding.
  assert(foundc != null);

  assert(vs0.cmk == cmk_ERROR);  // no garbage, please
  assert(vs0.rp == null);  // no garbage, please
  assert(vs0.rplimit == null);  // no garbage, please
  assert(vs0.sum == 0);  // no garbage, please

  vs0.init(band_rp, band_limit, foundc);

  // Done with foundc.  Free if necessary.
  if (to_free != null) {
    to_free->free();
    to_free = null;
  }
  foundc = null;

  coding& c = vs0.c;
  CODING_PRIVATE(c.spec);
  // assert sane N
  assert((uint)N < INT_MAX_VALUE || N == POP_FAVORED_N);

  // Look at the values, or at least skip over them quickly.
  if (valueSink == null) {
    // Skip and ignore values in the first pass.
    c.parseMultiple(band_rp, N, band_limit, B, H);
  } else if (N >= 0) {
    // Pop coding, {F} sequence, initial run of values...
    assert((mode & DISABLE_POP) != 0);
    value_stream vs = vs0;
    for (int n = 0; n < N; n++) {
      int val = vs.getInt();
      valueSink->add(val);
    }
    band_rp = vs.rp;
  } else {
    // Pop coding, {F} sequence, final run of values...
    assert((mode & DISABLE_POP) != 0);
    assert(N == POP_FAVORED_N);
    int min = INT_MIN_VALUE;  // farthest from the center
    // min2 is based on the buggy specification of centrality in version 150.7
    // no known implementations transmit this value, but just in case...
    //int min2 = INT_MIN_VALUE;
    int last = 0;
    // if there were initial runs, find the potential sentinels in them:
    for (int i = 0; i < valueSink->length(); i++) {
      last = valueSink->get(i);
      min = moreCentral(min, last);
      //min2 = moreCentral2(min2, last, min);
    }
    value_stream vs = vs0;
    for (;;) {
      int val = vs.getInt();
      if (valueSink->length() > 0 &&
          (val == last || val == min)) //|| val == min2
        break;
      valueSink->add(val);
      CHECK;
      last = val;
      min = moreCentral(min, last);
      //min2 = moreCentral2(min2, last, min);
    }
    band_rp = vs.rp;
  }
  CHECK;

  // Get an accurate upper limit now.
  vs0.rplimit = band_rp;
  vs0.cm = this;

  return; // success
}

coding basic_codings[] = {
  // This one is not a usable irregular coding, but is used by cp_Utf8_chars.
  CODING_INIT(3,128,0,0),

  // Fixed-length codings:
  CODING_INIT(1,256,0,0),
  CODING_INIT(1,256,1,0),
  CODING_INIT(1,256,0,1),
  CODING_INIT(1,256,1,1),
  CODING_INIT(2,256,0,0),
  CODING_INIT(2,256,1,0),
  CODING_INIT(2,256,0,1),
  CODING_INIT(2,256,1,1),
  CODING_INIT(3,256,0,0),
  CODING_INIT(3,256,1,0),
  CODING_INIT(3,256,0,1),
  CODING_INIT(3,256,1,1),
  CODING_INIT(4,256,0,0),
  CODING_INIT(4,256,1,0),
  CODING_INIT(4,256,0,1),
  CODING_INIT(4,256,1,1),

  // Full-range variable-length codings:
  CODING_INIT(5,  4,0,0),
  CODING_INIT(5,  4,1,0),
  CODING_INIT(5,  4,2,0),
  CODING_INIT(5, 16,0,0),
  CODING_INIT(5, 16,1,0),
  CODING_INIT(5, 16,2,0),
  CODING_INIT(5, 32,0,0),
  CODING_INIT(5, 32,1,0),
  CODING_INIT(5, 32,2,0),
  CODING_INIT(5, 64,0,0),
  CODING_INIT(5, 64,1,0),
  CODING_INIT(5, 64,2,0),
  CODING_INIT(5,128,0,0),
  CODING_INIT(5,128,1,0),
  CODING_INIT(5,128,2,0),

  CODING_INIT(5,  4,0,1),
  CODING_INIT(5,  4,1,1),
  CODING_INIT(5,  4,2,1),
  CODING_INIT(5, 16,0,1),
  CODING_INIT(5, 16,1,1),
  CODING_INIT(5, 16,2,1),
  CODING_INIT(5, 32,0,1),
  CODING_INIT(5, 32,1,1),
  CODING_INIT(5, 32,2,1),
  CODING_INIT(5, 64,0,1),
  CODING_INIT(5, 64,1,1),
  CODING_INIT(5, 64,2,1),
  CODING_INIT(5,128,0,1),
  CODING_INIT(5,128,1,1),
  CODING_INIT(5,128,2,1),

  // Variable length subrange codings:
  CODING_INIT(2,192,0,0),
  CODING_INIT(2,224,0,0),
  CODING_INIT(2,240,0,0),
  CODING_INIT(2,248,0,0),
  CODING_INIT(2,252,0,0),

  CODING_INIT(2,  8,0,1),
  CODING_INIT(2,  8,1,1),
  CODING_INIT(2, 16,0,1),
  CODING_INIT(2, 16,1,1),
  CODING_INIT(2, 32,0,1),
  CODING_INIT(2, 32,1,1),
  CODING_INIT(2, 64,0,1),
  CODING_INIT(2, 64,1,1),
  CODING_INIT(2,128,0,1),
  CODING_INIT(2,128,1,1),
  CODING_INIT(2,192,0,1),
  CODING_INIT(2,192,1,1),
  CODING_INIT(2,224,0,1),
  CODING_INIT(2,224,1,1),
  CODING_INIT(2,240,0,1),
  CODING_INIT(2,240,1,1),
  CODING_INIT(2,248,0,1),
  CODING_INIT(2,248,1,1),

  CODING_INIT(3,192,0,0),
  CODING_INIT(3,224,0,0),
  CODING_INIT(3,240,0,0),
  CODING_INIT(3,248,0,0),
  CODING_INIT(3,252,0,0),

  CODING_INIT(3,  8,0,1),
  CODING_INIT(3,  8,1,1),
  CODING_INIT(3, 16,0,1),
  CODING_INIT(3, 16,1,1),
  CODING_INIT(3, 32,0,1),
  CODING_INIT(3, 32,1,1),
  CODING_INIT(3, 64,0,1),
  CODING_INIT(3, 64,1,1),
  CODING_INIT(3,128,0,1),
  CODING_INIT(3,128,1,1),
  CODING_INIT(3,192,0,1),
  CODING_INIT(3,192,1,1),
  CODING_INIT(3,224,0,1),
  CODING_INIT(3,224,1,1),
  CODING_INIT(3,240,0,1),
  CODING_INIT(3,240,1,1),
  CODING_INIT(3,248,0,1),
  CODING_INIT(3,248,1,1),

  CODING_INIT(4,192,0,0),
  CODING_INIT(4,224,0,0),
  CODING_INIT(4,240,0,0),
  CODING_INIT(4,248,0,0),
  CODING_INIT(4,252,0,0),

  CODING_INIT(4,  8,0,1),
  CODING_INIT(4,  8,1,1),
  CODING_INIT(4, 16,0,1),
  CODING_INIT(4, 16,1,1),
  CODING_INIT(4, 32,0,1),
  CODING_INIT(4, 32,1,1),
  CODING_INIT(4, 64,0,1),
  CODING_INIT(4, 64,1,1),
  CODING_INIT(4,128,0,1),
  CODING_INIT(4,128,1,1),
  CODING_INIT(4,192,0,1),
  CODING_INIT(4,192,1,1),
  CODING_INIT(4,224,0,1),
  CODING_INIT(4,224,1,1),
  CODING_INIT(4,240,0,1),
  CODING_INIT(4,240,1,1),
  CODING_INIT(4,248,0,1),
  CODING_INIT(4,248,1,1),
  CODING_INIT(0,0,0,0)
};
#define BASIC_INDEX_LIMIT \
        (int)(sizeof(basic_codings)/sizeof(basic_codings[0])-1)

coding* coding::findByIndex(int idx) {
#ifndef PRODUCT
  /* Tricky assert here, constants and gcc complains about it without local. */
  int index_limit = BASIC_INDEX_LIMIT;
  assert(_meta_canon_min == 1 && _meta_canon_max+1 == index_limit);
#endif
  if (idx >= _meta_canon_min && idx <= _meta_canon_max)
    return basic_codings[idx].init();
  else
    return null;
}

#ifndef PRODUCT
const char* coding::string() {
  CODING_PRIVATE(spec);
  bytes buf;
  buf.malloc(100);
  char maxS[20], minS[20];
  sprintf(maxS, "%d", max);
  sprintf(minS, "%d", min);
  if (max == INT_MAX_VALUE)  strcpy(maxS, "max");
  if (min == INT_MIN_VALUE)  strcpy(minS, "min");
  sprintf((char*)buf.ptr, "(%d,%d,%d,%d) L=%d r=[%s,%s]",
          B,H,S,D,L,minS,maxS);
  return (const char*) buf.ptr;
}
#endif
