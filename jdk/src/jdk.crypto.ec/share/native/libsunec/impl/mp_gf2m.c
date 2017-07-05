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
 * The Original Code is the Multi-precision Binary Polynomial Arithmetic Library.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Sheueling Chang Shantz <sheueling.chang@sun.com> and
 *   Douglas Stebila <douglas@stebila.ca> of Sun Laboratories.
 *
 *********************************************************************** */

#include "mp_gf2m.h"
#include "mp_gf2m-priv.h"
#include "mplogic.h"
#include "mpi-priv.h"

const mp_digit mp_gf2m_sqr_tb[16] =
{
      0,     1,     4,     5,    16,    17,    20,    21,
     64,    65,    68,    69,    80,    81,    84,    85
};

/* Multiply two binary polynomials mp_digits a, b.
 * Result is a polynomial with degree < 2 * MP_DIGIT_BITS - 1.
 * Output in two mp_digits rh, rl.
 */
#if MP_DIGIT_BITS == 32
void
s_bmul_1x1(mp_digit *rh, mp_digit *rl, const mp_digit a, const mp_digit b)
{
    register mp_digit h, l, s;
    mp_digit tab[8], top2b = a >> 30;
    register mp_digit a1, a2, a4;

    a1 = a & (0x3FFFFFFF); a2 = a1 << 1; a4 = a2 << 1;

    tab[0] =  0; tab[1] = a1;    tab[2] = a2;    tab[3] = a1^a2;
    tab[4] = a4; tab[5] = a1^a4; tab[6] = a2^a4; tab[7] = a1^a2^a4;

    s = tab[b       & 0x7]; l  = s;
    s = tab[b >>  3 & 0x7]; l ^= s <<  3; h  = s >> 29;
    s = tab[b >>  6 & 0x7]; l ^= s <<  6; h ^= s >> 26;
    s = tab[b >>  9 & 0x7]; l ^= s <<  9; h ^= s >> 23;
    s = tab[b >> 12 & 0x7]; l ^= s << 12; h ^= s >> 20;
    s = tab[b >> 15 & 0x7]; l ^= s << 15; h ^= s >> 17;
    s = tab[b >> 18 & 0x7]; l ^= s << 18; h ^= s >> 14;
    s = tab[b >> 21 & 0x7]; l ^= s << 21; h ^= s >> 11;
    s = tab[b >> 24 & 0x7]; l ^= s << 24; h ^= s >>  8;
    s = tab[b >> 27 & 0x7]; l ^= s << 27; h ^= s >>  5;
    s = tab[b >> 30      ]; l ^= s << 30; h ^= s >>  2;

    /* compensate for the top two bits of a */

    if (top2b & 01) { l ^= b << 30; h ^= b >> 2; }
    if (top2b & 02) { l ^= b << 31; h ^= b >> 1; }

    *rh = h; *rl = l;
}
#else
void
s_bmul_1x1(mp_digit *rh, mp_digit *rl, const mp_digit a, const mp_digit b)
{
    register mp_digit h, l, s;
    mp_digit tab[16], top3b = a >> 61;
    register mp_digit a1, a2, a4, a8;

    a1 = a & (0x1FFFFFFFFFFFFFFFULL); a2 = a1 << 1;
    a4 = a2 << 1; a8 = a4 << 1;
    tab[ 0] = 0;     tab[ 1] = a1;       tab[ 2] = a2;       tab[ 3] = a1^a2;
    tab[ 4] = a4;    tab[ 5] = a1^a4;    tab[ 6] = a2^a4;    tab[ 7] = a1^a2^a4;
    tab[ 8] = a8;    tab[ 9] = a1^a8;    tab[10] = a2^a8;    tab[11] = a1^a2^a8;
    tab[12] = a4^a8; tab[13] = a1^a4^a8; tab[14] = a2^a4^a8; tab[15] = a1^a2^a4^a8;

    s = tab[b       & 0xF]; l  = s;
    s = tab[b >>  4 & 0xF]; l ^= s <<  4; h  = s >> 60;
    s = tab[b >>  8 & 0xF]; l ^= s <<  8; h ^= s >> 56;
    s = tab[b >> 12 & 0xF]; l ^= s << 12; h ^= s >> 52;
    s = tab[b >> 16 & 0xF]; l ^= s << 16; h ^= s >> 48;
    s = tab[b >> 20 & 0xF]; l ^= s << 20; h ^= s >> 44;
    s = tab[b >> 24 & 0xF]; l ^= s << 24; h ^= s >> 40;
    s = tab[b >> 28 & 0xF]; l ^= s << 28; h ^= s >> 36;
    s = tab[b >> 32 & 0xF]; l ^= s << 32; h ^= s >> 32;
    s = tab[b >> 36 & 0xF]; l ^= s << 36; h ^= s >> 28;
    s = tab[b >> 40 & 0xF]; l ^= s << 40; h ^= s >> 24;
    s = tab[b >> 44 & 0xF]; l ^= s << 44; h ^= s >> 20;
    s = tab[b >> 48 & 0xF]; l ^= s << 48; h ^= s >> 16;
    s = tab[b >> 52 & 0xF]; l ^= s << 52; h ^= s >> 12;
    s = tab[b >> 56 & 0xF]; l ^= s << 56; h ^= s >>  8;
    s = tab[b >> 60      ]; l ^= s << 60; h ^= s >>  4;

    /* compensate for the top three bits of a */

    if (top3b & 01) { l ^= b << 61; h ^= b >> 3; }
    if (top3b & 02) { l ^= b << 62; h ^= b >> 2; }
    if (top3b & 04) { l ^= b << 63; h ^= b >> 1; }

    *rh = h; *rl = l;
}
#endif

/* Compute xor-multiply of two binary polynomials  (a1, a0) x (b1, b0)
 * result is a binary polynomial in 4 mp_digits r[4].
 * The caller MUST ensure that r has the right amount of space allocated.
 */
void
s_bmul_2x2(mp_digit *r, const mp_digit a1, const mp_digit a0, const mp_digit b1,
           const mp_digit b0)
{
    mp_digit m1, m0;
    /* r[3] = h1, r[2] = h0; r[1] = l1; r[0] = l0 */
    s_bmul_1x1(r+3, r+2, a1, b1);
    s_bmul_1x1(r+1, r, a0, b0);
    s_bmul_1x1(&m1, &m0, a0 ^ a1, b0 ^ b1);
    /* Correction on m1 ^= l1 ^ h1; m0 ^= l0 ^ h0; */
    r[2] ^= m1 ^ r[1] ^ r[3];  /* h0 ^= m1 ^ l1 ^ h1; */
    r[1]  = r[3] ^ r[2] ^ r[0] ^ m1 ^ m0;  /* l1 ^= l0 ^ h0 ^ m0; */
}

/* Compute xor-multiply of two binary polynomials  (a2, a1, a0) x (b2, b1, b0)
 * result is a binary polynomial in 6 mp_digits r[6].
 * The caller MUST ensure that r has the right amount of space allocated.
 */
void
s_bmul_3x3(mp_digit *r, const mp_digit a2, const mp_digit a1, const mp_digit a0,
        const mp_digit b2, const mp_digit b1, const mp_digit b0)
{
        mp_digit zm[4];

        s_bmul_1x1(r+5, r+4, a2, b2);         /* fill top 2 words */
        s_bmul_2x2(zm, a1, a2^a0, b1, b2^b0); /* fill middle 4 words */
        s_bmul_2x2(r, a1, a0, b1, b0);        /* fill bottom 4 words */

        zm[3] ^= r[3];
        zm[2] ^= r[2];
        zm[1] ^= r[1] ^ r[5];
        zm[0] ^= r[0] ^ r[4];

        r[5]  ^= zm[3];
        r[4]  ^= zm[2];
        r[3]  ^= zm[1];
        r[2]  ^= zm[0];
}

/* Compute xor-multiply of two binary polynomials  (a3, a2, a1, a0) x (b3, b2, b1, b0)
 * result is a binary polynomial in 8 mp_digits r[8].
 * The caller MUST ensure that r has the right amount of space allocated.
 */
void s_bmul_4x4(mp_digit *r, const mp_digit a3, const mp_digit a2, const mp_digit a1,
        const mp_digit a0, const mp_digit b3, const mp_digit b2, const mp_digit b1,
        const mp_digit b0)
{
        mp_digit zm[4];

        s_bmul_2x2(r+4, a3, a2, b3, b2);            /* fill top 4 words */
        s_bmul_2x2(zm, a3^a1, a2^a0, b3^b1, b2^b0); /* fill middle 4 words */
        s_bmul_2x2(r, a1, a0, b1, b0);              /* fill bottom 4 words */

        zm[3] ^= r[3] ^ r[7];
        zm[2] ^= r[2] ^ r[6];
        zm[1] ^= r[1] ^ r[5];
        zm[0] ^= r[0] ^ r[4];

        r[5]  ^= zm[3];
        r[4]  ^= zm[2];
        r[3]  ^= zm[1];
        r[2]  ^= zm[0];
}

/* Compute addition of two binary polynomials a and b,
 * store result in c; c could be a or b, a and b could be equal;
 * c is the bitwise XOR of a and b.
 */
mp_err
mp_badd(const mp_int *a, const mp_int *b, mp_int *c)
{
    mp_digit *pa, *pb, *pc;
    mp_size ix;
    mp_size used_pa, used_pb;
    mp_err res = MP_OKAY;

    /* Add all digits up to the precision of b.  If b had more
     * precision than a initially, swap a, b first
     */
    if (MP_USED(a) >= MP_USED(b)) {
        pa = MP_DIGITS(a);
        pb = MP_DIGITS(b);
        used_pa = MP_USED(a);
        used_pb = MP_USED(b);
    } else {
        pa = MP_DIGITS(b);
        pb = MP_DIGITS(a);
        used_pa = MP_USED(b);
        used_pb = MP_USED(a);
    }

    /* Make sure c has enough precision for the output value */
    MP_CHECKOK( s_mp_pad(c, used_pa) );

    /* Do word-by-word xor */
    pc = MP_DIGITS(c);
    for (ix = 0; ix < used_pb; ix++) {
        (*pc++) = (*pa++) ^ (*pb++);
    }

    /* Finish the rest of digits until we're actually done */
    for (; ix < used_pa; ++ix) {
        *pc++ = *pa++;
    }

    MP_USED(c) = used_pa;
    MP_SIGN(c) = ZPOS;
    s_mp_clamp(c);

CLEANUP:
    return res;
}

#define s_mp_div2(a) MP_CHECKOK( mpl_rsh((a), (a), 1) );

/* Compute binary polynomial multiply d = a * b */
static void
s_bmul_d(const mp_digit *a, mp_size a_len, mp_digit b, mp_digit *d)
{
    mp_digit a_i, a0b0, a1b1, carry = 0;
    while (a_len--) {
        a_i = *a++;
        s_bmul_1x1(&a1b1, &a0b0, a_i, b);
        *d++ = a0b0 ^ carry;
        carry = a1b1;
    }
    *d = carry;
}

/* Compute binary polynomial xor multiply accumulate d ^= a * b */
static void
s_bmul_d_add(const mp_digit *a, mp_size a_len, mp_digit b, mp_digit *d)
{
    mp_digit a_i, a0b0, a1b1, carry = 0;
    while (a_len--) {
        a_i = *a++;
        s_bmul_1x1(&a1b1, &a0b0, a_i, b);
        *d++ ^= a0b0 ^ carry;
        carry = a1b1;
    }
    *d ^= carry;
}

/* Compute binary polynomial xor multiply c = a * b.
 * All parameters may be identical.
 */
mp_err
mp_bmul(const mp_int *a, const mp_int *b, mp_int *c)
{
    mp_digit *pb, b_i;
    mp_int tmp;
    mp_size ib, a_used, b_used;
    mp_err res = MP_OKAY;

    MP_DIGITS(&tmp) = 0;

    ARGCHK(a != NULL && b != NULL && c != NULL, MP_BADARG);

    if (a == c) {
        MP_CHECKOK( mp_init_copy(&tmp, a) );
        if (a == b)
            b = &tmp;
        a = &tmp;
    } else if (b == c) {
        MP_CHECKOK( mp_init_copy(&tmp, b) );
        b = &tmp;
    }

    if (MP_USED(a) < MP_USED(b)) {
        const mp_int *xch = b;      /* switch a and b if b longer */
        b = a;
        a = xch;
    }

    MP_USED(c) = 1; MP_DIGIT(c, 0) = 0;
    MP_CHECKOK( s_mp_pad(c, USED(a) + USED(b)) );

    pb = MP_DIGITS(b);
    s_bmul_d(MP_DIGITS(a), MP_USED(a), *pb++, MP_DIGITS(c));

    /* Outer loop:  Digits of b */
    a_used = MP_USED(a);
    b_used = MP_USED(b);
        MP_USED(c) = a_used + b_used;
    for (ib = 1; ib < b_used; ib++) {
        b_i = *pb++;

        /* Inner product:  Digits of a */
        if (b_i)
            s_bmul_d_add(MP_DIGITS(a), a_used, b_i, MP_DIGITS(c) + ib);
        else
            MP_DIGIT(c, ib + a_used) = b_i;
    }

    s_mp_clamp(c);

    SIGN(c) = ZPOS;

CLEANUP:
    mp_clear(&tmp);
    return res;
}


/* Compute modular reduction of a and store result in r.
 * r could be a.
 * For modular arithmetic, the irreducible polynomial f(t) is represented
 * as an array of int[], where f(t) is of the form:
 *     f(t) = t^p[0] + t^p[1] + ... + t^p[k]
 * where m = p[0] > p[1] > ... > p[k] = 0.
 */
mp_err
mp_bmod(const mp_int *a, const unsigned int p[], mp_int *r)
{
    int j, k;
    int n, dN, d0, d1;
    mp_digit zz, *z, tmp;
    mp_size used;
    mp_err res = MP_OKAY;

    /* The algorithm does the reduction in place in r,
     * if a != r, copy a into r first so reduction can be done in r
     */
    if (a != r) {
        MP_CHECKOK( mp_copy(a, r) );
    }
    z = MP_DIGITS(r);

    /* start reduction */
    dN = p[0] / MP_DIGIT_BITS;
    used = MP_USED(r);

    for (j = used - 1; j > dN;) {

        zz = z[j];
        if (zz == 0) {
            j--; continue;
        }
        z[j] = 0;

        for (k = 1; p[k] > 0; k++) {
            /* reducing component t^p[k] */
            n = p[0] - p[k];
            d0 = n % MP_DIGIT_BITS;
            d1 = MP_DIGIT_BITS - d0;
            n /= MP_DIGIT_BITS;
            z[j-n] ^= (zz>>d0);
            if (d0)
                z[j-n-1] ^= (zz<<d1);
        }

        /* reducing component t^0 */
        n = dN;
        d0 = p[0] % MP_DIGIT_BITS;
        d1 = MP_DIGIT_BITS - d0;
        z[j-n] ^= (zz >> d0);
        if (d0)
            z[j-n-1] ^= (zz << d1);

    }

    /* final round of reduction */
    while (j == dN) {

        d0 = p[0] % MP_DIGIT_BITS;
        zz = z[dN] >> d0;
        if (zz == 0) break;
        d1 = MP_DIGIT_BITS - d0;

        /* clear up the top d1 bits */
        if (d0) z[dN] = (z[dN] << d1) >> d1;
        *z ^= zz; /* reduction t^0 component */

        for (k = 1; p[k] > 0; k++) {
            /* reducing component t^p[k]*/
            n = p[k] / MP_DIGIT_BITS;
            d0 = p[k] % MP_DIGIT_BITS;
            d1 = MP_DIGIT_BITS - d0;
            z[n] ^= (zz << d0);
            tmp = zz >> d1;
            if (d0 && tmp)
                z[n+1] ^= tmp;
        }
    }

    s_mp_clamp(r);
CLEANUP:
    return res;
}

/* Compute the product of two polynomials a and b, reduce modulo p,
 * Store the result in r.  r could be a or b; a could be b.
 */
mp_err
mp_bmulmod(const mp_int *a, const mp_int *b, const unsigned int p[], mp_int *r)
{
    mp_err res;

    if (a == b) return mp_bsqrmod(a, p, r);
    if ((res = mp_bmul(a, b, r) ) != MP_OKAY)
        return res;
    return mp_bmod(r, p, r);
}

/* Compute binary polynomial squaring c = a*a mod p .
 * Parameter r and a can be identical.
 */

mp_err
mp_bsqrmod(const mp_int *a, const unsigned int p[], mp_int *r)
{
    mp_digit *pa, *pr, a_i;
    mp_int tmp;
    mp_size ia, a_used;
    mp_err res;

    ARGCHK(a != NULL && r != NULL, MP_BADARG);
    MP_DIGITS(&tmp) = 0;

    if (a == r) {
        MP_CHECKOK( mp_init_copy(&tmp, a) );
        a = &tmp;
    }

    MP_USED(r) = 1; MP_DIGIT(r, 0) = 0;
    MP_CHECKOK( s_mp_pad(r, 2*USED(a)) );

    pa = MP_DIGITS(a);
    pr = MP_DIGITS(r);
    a_used = MP_USED(a);
        MP_USED(r) = 2 * a_used;

    for (ia = 0; ia < a_used; ia++) {
        a_i = *pa++;
        *pr++ = gf2m_SQR0(a_i);
        *pr++ = gf2m_SQR1(a_i);
    }

    MP_CHECKOK( mp_bmod(r, p, r) );
    s_mp_clamp(r);
    SIGN(r) = ZPOS;

CLEANUP:
    mp_clear(&tmp);
    return res;
}

/* Compute binary polynomial y/x mod p, y divided by x, reduce modulo p.
 * Store the result in r. r could be x or y, and x could equal y.
 * Uses algorithm Modular_Division_GF(2^m) from
 *     Chang-Shantz, S.  "From Euclid's GCD to Montgomery Multiplication to
 *     the Great Divide".
 */
int
mp_bdivmod(const mp_int *y, const mp_int *x, const mp_int *pp,
    const unsigned int p[], mp_int *r)
{
    mp_int aa, bb, uu;
    mp_int *a, *b, *u, *v;
    mp_err res = MP_OKAY;

    MP_DIGITS(&aa) = 0;
    MP_DIGITS(&bb) = 0;
    MP_DIGITS(&uu) = 0;

    MP_CHECKOK( mp_init_copy(&aa, x) );
    MP_CHECKOK( mp_init_copy(&uu, y) );
    MP_CHECKOK( mp_init_copy(&bb, pp) );
    MP_CHECKOK( s_mp_pad(r, USED(pp)) );
    MP_USED(r) = 1; MP_DIGIT(r, 0) = 0;

    a = &aa; b= &bb; u=&uu; v=r;
    /* reduce x and y mod p */
    MP_CHECKOK( mp_bmod(a, p, a) );
    MP_CHECKOK( mp_bmod(u, p, u) );

    while (!mp_isodd(a)) {
        s_mp_div2(a);
        if (mp_isodd(u)) {
            MP_CHECKOK( mp_badd(u, pp, u) );
        }
        s_mp_div2(u);
    }

    do {
        if (mp_cmp_mag(b, a) > 0) {
            MP_CHECKOK( mp_badd(b, a, b) );
            MP_CHECKOK( mp_badd(v, u, v) );
            do {
                s_mp_div2(b);
                if (mp_isodd(v)) {
                    MP_CHECKOK( mp_badd(v, pp, v) );
                }
                s_mp_div2(v);
            } while (!mp_isodd(b));
        }
        else if ((MP_DIGIT(a,0) == 1) && (MP_USED(a) == 1))
            break;
        else {
            MP_CHECKOK( mp_badd(a, b, a) );
            MP_CHECKOK( mp_badd(u, v, u) );
            do {
                s_mp_div2(a);
                if (mp_isodd(u)) {
                    MP_CHECKOK( mp_badd(u, pp, u) );
                }
                s_mp_div2(u);
            } while (!mp_isodd(a));
        }
    } while (1);

    MP_CHECKOK( mp_copy(u, r) );

CLEANUP:
    /* XXX this appears to be a memory leak in the NSS code */
    mp_clear(&aa);
    mp_clear(&bb);
    mp_clear(&uu);
    return res;

}

/* Convert the bit-string representation of a polynomial a into an array
 * of integers corresponding to the bits with non-zero coefficient.
 * Up to max elements of the array will be filled.  Return value is total
 * number of coefficients that would be extracted if array was large enough.
 */
int
mp_bpoly2arr(const mp_int *a, unsigned int p[], int max)
{
    int i, j, k;
    mp_digit top_bit, mask;

    top_bit = 1;
    top_bit <<= MP_DIGIT_BIT - 1;

    for (k = 0; k < max; k++) p[k] = 0;
    k = 0;

    for (i = MP_USED(a) - 1; i >= 0; i--) {
        mask = top_bit;
        for (j = MP_DIGIT_BIT - 1; j >= 0; j--) {
            if (MP_DIGITS(a)[i] & mask) {
                if (k < max) p[k] = MP_DIGIT_BIT * i + j;
                k++;
            }
            mask >>= 1;
        }
    }

    return k;
}

/* Convert the coefficient array representation of a polynomial to a
 * bit-string.  The array must be terminated by 0.
 */
mp_err
mp_barr2poly(const unsigned int p[], mp_int *a)
{

    mp_err res = MP_OKAY;
    int i;

    mp_zero(a);
    for (i = 0; p[i] > 0; i++) {
        MP_CHECKOK( mpl_set_bit(a, p[i], 1) );
    }
    MP_CHECKOK( mpl_set_bit(a, 0, 1) );

CLEANUP:
    return res;
}
