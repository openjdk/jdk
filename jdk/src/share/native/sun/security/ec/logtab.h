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
 * The Original Code is the Netscape security libraries.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1994-2000
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Dr Vipul Gupta <vipul.gupta@sun.com>, Sun Microsystems Laboratories
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

#ifndef _LOGTAB_H
#define _LOGTAB_H

#pragma ident   "%Z%%M% %I%     %E% SMI"

const float s_logv_2[] = {
   0.000000000f, 0.000000000f, 1.000000000f, 0.630929754f,  /*  0  1  2  3 */
   0.500000000f, 0.430676558f, 0.386852807f, 0.356207187f,  /*  4  5  6  7 */
   0.333333333f, 0.315464877f, 0.301029996f, 0.289064826f,  /*  8  9 10 11 */
   0.278942946f, 0.270238154f, 0.262649535f, 0.255958025f,  /* 12 13 14 15 */
   0.250000000f, 0.244650542f, 0.239812467f, 0.235408913f,  /* 16 17 18 19 */
   0.231378213f, 0.227670249f, 0.224243824f, 0.221064729f,  /* 20 21 22 23 */
   0.218104292f, 0.215338279f, 0.212746054f, 0.210309918f,  /* 24 25 26 27 */
   0.208014598f, 0.205846832f, 0.203795047f, 0.201849087f,  /* 28 29 30 31 */
   0.200000000f, 0.198239863f, 0.196561632f, 0.194959022f,  /* 32 33 34 35 */
   0.193426404f, 0.191958720f, 0.190551412f, 0.189200360f,  /* 36 37 38 39 */
   0.187901825f, 0.186652411f, 0.185449023f, 0.184288833f,  /* 40 41 42 43 */
   0.183169251f, 0.182087900f, 0.181042597f, 0.180031327f,  /* 44 45 46 47 */
   0.179052232f, 0.178103594f, 0.177183820f, 0.176291434f,  /* 48 49 50 51 */
   0.175425064f, 0.174583430f, 0.173765343f, 0.172969690f,  /* 52 53 54 55 */
   0.172195434f, 0.171441601f, 0.170707280f, 0.169991616f,  /* 56 57 58 59 */
   0.169293808f, 0.168613099f, 0.167948779f, 0.167300179f,  /* 60 61 62 63 */
   0.166666667f
};

#endif /* _LOGTAB_H */
