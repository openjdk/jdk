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

#ifndef _MPLOGIC_H
#define _MPLOGIC_H

/* $Id: mplogic.h,v 1.7 2004/04/27 23:04:36 gerv%gerv.net Exp $ */

#include "mpi.h"

/*
  The logical operations treat an mp_int as if it were a bit vector,
  without regard to its sign (an mp_int is represented in a signed
  magnitude format).  Values are treated as if they had an infinite
  string of zeros left of the most-significant bit.
 */

/* Parity results                    */

#define MP_EVEN       MP_YES
#define MP_ODD        MP_NO

/* Bitwise functions                 */

mp_err mpl_not(mp_int *a, mp_int *b);            /* one's complement  */
mp_err mpl_and(mp_int *a, mp_int *b, mp_int *c); /* bitwise AND       */
mp_err mpl_or(mp_int *a, mp_int *b, mp_int *c);  /* bitwise OR        */
mp_err mpl_xor(mp_int *a, mp_int *b, mp_int *c); /* bitwise XOR       */

/* Shift functions                   */

mp_err mpl_rsh(const mp_int *a, mp_int *b, mp_digit d);   /* right shift    */
mp_err mpl_lsh(const mp_int *a, mp_int *b, mp_digit d);   /* left shift     */

/* Bit count and parity              */

mp_err mpl_num_set(mp_int *a, int *num);         /* count set bits    */
mp_err mpl_num_clear(mp_int *a, int *num);       /* count clear bits  */
mp_err mpl_parity(mp_int *a);                    /* determine parity  */

/* Get & Set the value of a bit */

mp_err mpl_set_bit(mp_int *a, mp_size bitNum, mp_size value);
mp_err mpl_get_bit(const mp_int *a, mp_size bitNum);
mp_err mpl_get_bits(const mp_int *a, mp_size lsbNum, mp_size numBits);
mp_err mpl_significant_bits(const mp_int *a);

#endif /* _MPLOGIC_H */
