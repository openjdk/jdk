/* *********************************************************************
 *
 * Sun elects to have this file available under and governed by the
 * Mozilla Public License Version 1.1 ("MPL") (see
 * http://www.mozilla.org/MPL/ for full license text). For the avoidance
 * of doubt and subject to the following, Sun also elects to allow
 * licensees to use this file under the MPL, the GNU General Public
 * License version 2 only or the Lesser General Public License version
 * 2.1 only. Any references to the "GNU General Public License version 2
 * or later" or "GPL" in the following shall be construed to mean the
 * GNU General Public License version 2 only. Any references to the "GNU
 * Lesser General Public License version 2.1 or later" or "LGPL" in the
 * following shall be construed to mean the GNU Lesser General Public
 * License version 2.1 only. However, the following notice accompanied
 * the original version of this file:
 *
 *
 *  Bitwise logical operations on MPI values
 *
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
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
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 *********************************************************************** */
/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

#ifndef _MPLOGIC_H
#define _MPLOGIC_H

#pragma ident   "%Z%%M% %I%     %E% SMI"

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
