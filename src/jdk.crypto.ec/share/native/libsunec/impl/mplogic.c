/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* *********************************************************************
 *
 * The Original Code is the MPI Arbitrary Precision Integer Arithmetic library.
 *
 * The Initial Developer of the Original Code is
 * Michael J. Fromberger.
 * Portions created by the Initial Developer are Copyright (C) 1998
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 *********************************************************************** */

/*  Bitwise logical operations on MPI values */

#include "mpi-priv.h"
#include "mplogic.h"

/* {{{ Lookup table for population count */

static unsigned char bitc[] = {
   0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4,
   1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
   1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
   2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
   1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
   2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
   2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
   3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
   1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
   2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
   2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
   3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
   2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
   3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
   3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
   4, 5, 5, 6, 5, 6, 6, 7, 5, 6, 6, 7, 6, 7, 7, 8
};

/* }}} */

/*
  mpl_rsh(a, b, d)     - b = a >> d
  mpl_lsh(a, b, d)     - b = a << d
 */

/* {{{ mpl_rsh(a, b, d) */

mp_err mpl_rsh(const mp_int *a, mp_int *b, mp_digit d)
{
  mp_err   res;

  ARGCHK(a != NULL && b != NULL, MP_BADARG);

  if((res = mp_copy(a, b)) != MP_OKAY)
    return res;

  s_mp_div_2d(b, d);

  return MP_OKAY;

} /* end mpl_rsh() */

/* }}} */

/* {{{ mpl_lsh(a, b, d) */

mp_err mpl_lsh(const mp_int *a, mp_int *b, mp_digit d)
{
  mp_err   res;

  ARGCHK(a != NULL && b != NULL, MP_BADARG);

  if((res = mp_copy(a, b)) != MP_OKAY)
    return res;

  return s_mp_mul_2d(b, d);

} /* end mpl_lsh() */

/* }}} */

/*------------------------------------------------------------------------*/
/*
  mpl_set_bit

  Returns MP_OKAY or some error code.
  Grows a if needed to set a bit to 1.
 */
mp_err mpl_set_bit(mp_int *a, mp_size bitNum, mp_size value)
{
  mp_size      ix;
  mp_err       rv;
  mp_digit     mask;

  ARGCHK(a != NULL, MP_BADARG);

  ix = bitNum / MP_DIGIT_BIT;
  if (ix + 1 > MP_USED(a)) {
    rv = s_mp_pad(a, ix + 1);
    if (rv != MP_OKAY)
      return rv;
  }

  bitNum = bitNum % MP_DIGIT_BIT;
  mask = (mp_digit)1 << bitNum;
  if (value)
    MP_DIGIT(a,ix) |= mask;
  else
    MP_DIGIT(a,ix) &= ~mask;
  s_mp_clamp(a);
  return MP_OKAY;
}

/*
  mpl_get_bit

  returns 0 or 1 or some (negative) error code.
 */
mp_err mpl_get_bit(const mp_int *a, mp_size bitNum)
{
  mp_size      bit, ix;
  mp_err       rv;

  ARGCHK(a != NULL, MP_BADARG);

  ix = bitNum / MP_DIGIT_BIT;
  ARGCHK(ix <= MP_USED(a) - 1, MP_RANGE);

  bit   = bitNum % MP_DIGIT_BIT;
  rv = (mp_err)(MP_DIGIT(a, ix) >> bit) & 1;
  return rv;
}

/*
  mpl_get_bits
  - Extracts numBits bits from a, where the least significant extracted bit
  is bit lsbNum.  Returns a negative value if error occurs.
  - Because sign bit is used to indicate error, maximum number of bits to
  be returned is the lesser of (a) the number of bits in an mp_digit, or
  (b) one less than the number of bits in an mp_err.
  - lsbNum + numbits can be greater than the number of significant bits in
  integer a, as long as bit lsbNum is in the high order digit of a.
 */
mp_err mpl_get_bits(const mp_int *a, mp_size lsbNum, mp_size numBits)
{
  mp_size    rshift = (lsbNum % MP_DIGIT_BIT);
  mp_size    lsWndx = (lsbNum / MP_DIGIT_BIT);
  mp_digit * digit  = MP_DIGITS(a) + lsWndx;
  mp_digit   mask   = ((1 << numBits) - 1);

  ARGCHK(numBits < CHAR_BIT * sizeof mask, MP_BADARG);
  ARGCHK(MP_HOWMANY(lsbNum, MP_DIGIT_BIT) <= MP_USED(a), MP_RANGE);

  if ((numBits + lsbNum % MP_DIGIT_BIT <= MP_DIGIT_BIT) ||
      (lsWndx + 1 >= MP_USED(a))) {
    mask &= (digit[0] >> rshift);
  } else {
    mask &= ((digit[0] >> rshift) | (digit[1] << (MP_DIGIT_BIT - rshift)));
  }
  return (mp_err)mask;
}

/*
  mpl_significant_bits
  returns number of significnant bits in abs(a).
  returns 1 if value is zero.
 */
mp_err mpl_significant_bits(const mp_int *a)
{
  mp_err bits   = 0;
  int    ix;

  ARGCHK(a != NULL, MP_BADARG);

  ix = MP_USED(a);
  for (ix = MP_USED(a); ix > 0; ) {
    mp_digit d;
    d = MP_DIGIT(a, --ix);
    if (d) {
      while (d) {
        ++bits;
        d >>= 1;
      }
      break;
    }
  }
  bits += ix * MP_DIGIT_BIT;
  if (!bits)
    bits = 1;
  return bits;
}

/*------------------------------------------------------------------------*/
/* HERE THERE BE DRAGONS                                                  */
