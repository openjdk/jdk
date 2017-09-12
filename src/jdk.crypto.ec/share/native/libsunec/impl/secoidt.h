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
 *********************************************************************** */

#ifndef _SECOIDT_H_
#define _SECOIDT_H_

/*
 * secoidt.h - public data structures for ASN.1 OID functions
 *
 * $Id: secoidt.h,v 1.23 2007/05/05 22:45:16 nelson%bolyard.com Exp $
 */

typedef struct SECOidDataStr SECOidData;
typedef struct SECAlgorithmIDStr SECAlgorithmID;

/*
** An X.500 algorithm identifier
*/
struct SECAlgorithmIDStr {
    SECItem algorithm;
    SECItem parameters;
};

#define SEC_OID_SECG_EC_SECP192R1 SEC_OID_ANSIX962_EC_PRIME192V1
#define SEC_OID_SECG_EC_SECP256R1 SEC_OID_ANSIX962_EC_PRIME256V1
#define SEC_OID_PKCS12_KEY_USAGE  SEC_OID_X509_KEY_USAGE

/* fake OID for DSS sign/verify */
#define SEC_OID_SHA SEC_OID_MISS_DSS

typedef enum {
    INVALID_CERT_EXTENSION = 0,
    UNSUPPORTED_CERT_EXTENSION = 1,
    SUPPORTED_CERT_EXTENSION = 2
} SECSupportExtenTag;

struct SECOidDataStr {
    SECItem            oid;
    ECCurveName        offset;
    const char *       desc;
    unsigned long      mechanism;
    SECSupportExtenTag supportedExtension;
                                /* only used for x.509 v3 extensions, so
                                   that we can print the names of those
                                   extensions that we don't even support */
};

#endif /* _SECOIDT_H_ */
