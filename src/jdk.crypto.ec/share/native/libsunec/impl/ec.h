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
 * The Original Code is the Elliptic Curve Cryptography library.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Dr Vipul Gupta <vipul.gupta@sun.com>, Sun Microsystems Laboratories
 *
 *********************************************************************** */

#ifndef __ec_h_
#define __ec_h_

#define EC_DEBUG                          0
#define EC_POINT_FORM_COMPRESSED_Y0    0x02
#define EC_POINT_FORM_COMPRESSED_Y1    0x03
#define EC_POINT_FORM_UNCOMPRESSED     0x04
#define EC_POINT_FORM_HYBRID_Y0        0x06
#define EC_POINT_FORM_HYBRID_Y1        0x07

#define ANSI_X962_CURVE_OID_TOTAL_LEN    10
#define SECG_CURVE_OID_TOTAL_LEN          7

#endif /* __ec_h_ */
