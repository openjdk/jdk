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

#include <sys/types.h>

#ifndef _WIN32
#if !defined(__linux__) && !defined(_ALLBSD_SOURCE)
#include <sys/systm.h>
#endif /* __linux__ || _ALLBSD_SOURCE */
#include <sys/param.h>
#endif /* _WIN32 */

#ifdef _KERNEL
#include <sys/kmem.h>
#else
#include <string.h>
#endif
#include "ec.h"
#include "ecl-curve.h"
#include "ecc_impl.h"
#include "secoidt.h"

#define CERTICOM_OID            0x2b, 0x81, 0x04
#define SECG_OID                CERTICOM_OID, 0x00

#define ANSI_X962_OID           0x2a, 0x86, 0x48, 0xce, 0x3d
#define ANSI_X962_CURVE_OID     ANSI_X962_OID, 0x03
#define ANSI_X962_GF2m_OID      ANSI_X962_CURVE_OID, 0x00
#define ANSI_X962_GFp_OID       ANSI_X962_CURVE_OID, 0x01

#define CONST_OID static const unsigned char

/* ANSI X9.62 prime curve OIDs */
/* NOTE: prime192v1 is the same as secp192r1, prime256v1 is the
 * same as secp256r1
 */
CONST_OID ansiX962prime192v1[] = { ANSI_X962_GFp_OID, 0x01 };
CONST_OID ansiX962prime192v2[] = { ANSI_X962_GFp_OID, 0x02 };
CONST_OID ansiX962prime192v3[] = { ANSI_X962_GFp_OID, 0x03 };
CONST_OID ansiX962prime239v1[] = { ANSI_X962_GFp_OID, 0x04 };
CONST_OID ansiX962prime239v2[] = { ANSI_X962_GFp_OID, 0x05 };
CONST_OID ansiX962prime239v3[] = { ANSI_X962_GFp_OID, 0x06 };
CONST_OID ansiX962prime256v1[] = { ANSI_X962_GFp_OID, 0x07 };

/* SECG prime curve OIDs */
CONST_OID secgECsecp112r1[] = { SECG_OID, 0x06 };
CONST_OID secgECsecp112r2[] = { SECG_OID, 0x07 };
CONST_OID secgECsecp128r1[] = { SECG_OID, 0x1c };
CONST_OID secgECsecp128r2[] = { SECG_OID, 0x1d };
CONST_OID secgECsecp160k1[] = { SECG_OID, 0x09 };
CONST_OID secgECsecp160r1[] = { SECG_OID, 0x08 };
CONST_OID secgECsecp160r2[] = { SECG_OID, 0x1e };
CONST_OID secgECsecp192k1[] = { SECG_OID, 0x1f };
CONST_OID secgECsecp224k1[] = { SECG_OID, 0x20 };
CONST_OID secgECsecp224r1[] = { SECG_OID, 0x21 };
CONST_OID secgECsecp256k1[] = { SECG_OID, 0x0a };
CONST_OID secgECsecp384r1[] = { SECG_OID, 0x22 };
CONST_OID secgECsecp521r1[] = { SECG_OID, 0x23 };

/* SECG characterisitic two curve OIDs */
CONST_OID secgECsect113r1[] = {SECG_OID, 0x04 };
CONST_OID secgECsect113r2[] = {SECG_OID, 0x05 };
CONST_OID secgECsect131r1[] = {SECG_OID, 0x16 };
CONST_OID secgECsect131r2[] = {SECG_OID, 0x17 };
CONST_OID secgECsect163k1[] = {SECG_OID, 0x01 };
CONST_OID secgECsect163r1[] = {SECG_OID, 0x02 };
CONST_OID secgECsect163r2[] = {SECG_OID, 0x0f };
CONST_OID secgECsect193r1[] = {SECG_OID, 0x18 };
CONST_OID secgECsect193r2[] = {SECG_OID, 0x19 };
CONST_OID secgECsect233k1[] = {SECG_OID, 0x1a };
CONST_OID secgECsect233r1[] = {SECG_OID, 0x1b };
CONST_OID secgECsect239k1[] = {SECG_OID, 0x03 };
CONST_OID secgECsect283k1[] = {SECG_OID, 0x10 };
CONST_OID secgECsect283r1[] = {SECG_OID, 0x11 };
CONST_OID secgECsect409k1[] = {SECG_OID, 0x24 };
CONST_OID secgECsect409r1[] = {SECG_OID, 0x25 };
CONST_OID secgECsect571k1[] = {SECG_OID, 0x26 };
CONST_OID secgECsect571r1[] = {SECG_OID, 0x27 };

/* ANSI X9.62 characteristic two curve OIDs */
CONST_OID ansiX962c2pnb163v1[] = { ANSI_X962_GF2m_OID, 0x01 };
CONST_OID ansiX962c2pnb163v2[] = { ANSI_X962_GF2m_OID, 0x02 };
CONST_OID ansiX962c2pnb163v3[] = { ANSI_X962_GF2m_OID, 0x03 };
CONST_OID ansiX962c2pnb176v1[] = { ANSI_X962_GF2m_OID, 0x04 };
CONST_OID ansiX962c2tnb191v1[] = { ANSI_X962_GF2m_OID, 0x05 };
CONST_OID ansiX962c2tnb191v2[] = { ANSI_X962_GF2m_OID, 0x06 };
CONST_OID ansiX962c2tnb191v3[] = { ANSI_X962_GF2m_OID, 0x07 };
CONST_OID ansiX962c2onb191v4[] = { ANSI_X962_GF2m_OID, 0x08 };
CONST_OID ansiX962c2onb191v5[] = { ANSI_X962_GF2m_OID, 0x09 };
CONST_OID ansiX962c2pnb208w1[] = { ANSI_X962_GF2m_OID, 0x0a };
CONST_OID ansiX962c2tnb239v1[] = { ANSI_X962_GF2m_OID, 0x0b };
CONST_OID ansiX962c2tnb239v2[] = { ANSI_X962_GF2m_OID, 0x0c };
CONST_OID ansiX962c2tnb239v3[] = { ANSI_X962_GF2m_OID, 0x0d };
CONST_OID ansiX962c2onb239v4[] = { ANSI_X962_GF2m_OID, 0x0e };
CONST_OID ansiX962c2onb239v5[] = { ANSI_X962_GF2m_OID, 0x0f };
CONST_OID ansiX962c2pnb272w1[] = { ANSI_X962_GF2m_OID, 0x10 };
CONST_OID ansiX962c2pnb304w1[] = { ANSI_X962_GF2m_OID, 0x11 };
CONST_OID ansiX962c2tnb359v1[] = { ANSI_X962_GF2m_OID, 0x12 };
CONST_OID ansiX962c2pnb368w1[] = { ANSI_X962_GF2m_OID, 0x13 };
CONST_OID ansiX962c2tnb431r1[] = { ANSI_X962_GF2m_OID, 0x14 };

#define OI(x) { siDEROID, (unsigned char *)x, sizeof x }
#ifndef SECOID_NO_STRINGS
#define OD(oid,tag,desc,mech,ext) { OI(oid), tag, desc, mech, ext }
#else
#define OD(oid,tag,desc,mech,ext) { OI(oid), tag, 0, mech, ext }
#endif

#define CKM_INVALID_MECHANISM 0xffffffffUL

/* XXX this is incorrect */
#define INVALID_CERT_EXTENSION 1

#define CKM_ECDSA                      0x00001041
#define CKM_ECDSA_SHA1                 0x00001042
#define CKM_ECDH1_DERIVE               0x00001050

static SECOidData ANSI_prime_oids[] = {
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },

    OD( ansiX962prime192v1, ECCurve_NIST_P192,
        "ANSI X9.62 elliptic curve prime192v1 (aka secp192r1, NIST P-192)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962prime192v2, ECCurve_X9_62_PRIME_192V2,
        "ANSI X9.62 elliptic curve prime192v2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962prime192v3, ECCurve_X9_62_PRIME_192V3,
        "ANSI X9.62 elliptic curve prime192v3",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962prime239v1, ECCurve_X9_62_PRIME_239V1,
        "ANSI X9.62 elliptic curve prime239v1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962prime239v2, ECCurve_X9_62_PRIME_239V2,
        "ANSI X9.62 elliptic curve prime239v2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962prime239v3, ECCurve_X9_62_PRIME_239V3,
        "ANSI X9.62 elliptic curve prime239v3",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962prime256v1, ECCurve_NIST_P256,
        "ANSI X9.62 elliptic curve prime256v1 (aka secp256r1, NIST P-256)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION )
};

static SECOidData SECG_oids[] = {
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },

    OD( secgECsect163k1, ECCurve_NIST_K163,
        "SECG elliptic curve sect163k1 (aka NIST K-163)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect163r1, ECCurve_SECG_CHAR2_163R1,
        "SECG elliptic curve sect163r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect239k1, ECCurve_SECG_CHAR2_239K1,
        "SECG elliptic curve sect239k1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect113r1, ECCurve_SECG_CHAR2_113R1,
        "SECG elliptic curve sect113r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect113r2, ECCurve_SECG_CHAR2_113R2,
        "SECG elliptic curve sect113r2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp112r1, ECCurve_SECG_PRIME_112R1,
        "SECG elliptic curve secp112r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp112r2, ECCurve_SECG_PRIME_112R2,
        "SECG elliptic curve secp112r2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp160r1, ECCurve_SECG_PRIME_160R1,
        "SECG elliptic curve secp160r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp160k1, ECCurve_SECG_PRIME_160K1,
        "SECG elliptic curve secp160k1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp256k1, ECCurve_SECG_PRIME_256K1,
        "SECG elliptic curve secp256k1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    OD( secgECsect163r2, ECCurve_NIST_B163,
        "SECG elliptic curve sect163r2 (aka NIST B-163)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect283k1, ECCurve_NIST_K283,
        "SECG elliptic curve sect283k1 (aka NIST K-283)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect283r1, ECCurve_NIST_B283,
        "SECG elliptic curve sect283r1 (aka NIST B-283)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    OD( secgECsect131r1, ECCurve_SECG_CHAR2_131R1,
        "SECG elliptic curve sect131r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect131r2, ECCurve_SECG_CHAR2_131R2,
        "SECG elliptic curve sect131r2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect193r1, ECCurve_SECG_CHAR2_193R1,
        "SECG elliptic curve sect193r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect193r2, ECCurve_SECG_CHAR2_193R2,
        "SECG elliptic curve sect193r2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect233k1, ECCurve_NIST_K233,
        "SECG elliptic curve sect233k1 (aka NIST K-233)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect233r1, ECCurve_NIST_B233,
        "SECG elliptic curve sect233r1 (aka NIST B-233)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp128r1, ECCurve_SECG_PRIME_128R1,
        "SECG elliptic curve secp128r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp128r2, ECCurve_SECG_PRIME_128R2,
        "SECG elliptic curve secp128r2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp160r2, ECCurve_SECG_PRIME_160R2,
        "SECG elliptic curve secp160r2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp192k1, ECCurve_SECG_PRIME_192K1,
        "SECG elliptic curve secp192k1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp224k1, ECCurve_SECG_PRIME_224K1,
        "SECG elliptic curve secp224k1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp224r1, ECCurve_NIST_P224,
        "SECG elliptic curve secp224r1 (aka NIST P-224)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp384r1, ECCurve_NIST_P384,
        "SECG elliptic curve secp384r1 (aka NIST P-384)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsecp521r1, ECCurve_NIST_P521,
        "SECG elliptic curve secp521r1 (aka NIST P-521)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect409k1, ECCurve_NIST_K409,
        "SECG elliptic curve sect409k1 (aka NIST K-409)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect409r1, ECCurve_NIST_B409,
        "SECG elliptic curve sect409r1 (aka NIST B-409)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect571k1, ECCurve_NIST_K571,
        "SECG elliptic curve sect571k1 (aka NIST K-571)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( secgECsect571r1, ECCurve_NIST_B571,
        "SECG elliptic curve sect571r1 (aka NIST B-571)",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION )
};

static SECOidData ANSI_oids[] = {
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },

    /* ANSI X9.62 named elliptic curves (characteristic two field) */
    OD( ansiX962c2pnb163v1, ECCurve_X9_62_CHAR2_PNB163V1,
        "ANSI X9.62 elliptic curve c2pnb163v1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2pnb163v2, ECCurve_X9_62_CHAR2_PNB163V2,
        "ANSI X9.62 elliptic curve c2pnb163v2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2pnb163v3, ECCurve_X9_62_CHAR2_PNB163V3,
        "ANSI X9.62 elliptic curve c2pnb163v3",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2pnb176v1, ECCurve_X9_62_CHAR2_PNB176V1,
        "ANSI X9.62 elliptic curve c2pnb176v1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb191v1, ECCurve_X9_62_CHAR2_TNB191V1,
        "ANSI X9.62 elliptic curve c2tnb191v1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb191v2, ECCurve_X9_62_CHAR2_TNB191V2,
        "ANSI X9.62 elliptic curve c2tnb191v2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb191v3, ECCurve_X9_62_CHAR2_TNB191V3,
        "ANSI X9.62 elliptic curve c2tnb191v3",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    OD( ansiX962c2pnb208w1, ECCurve_X9_62_CHAR2_PNB208W1,
        "ANSI X9.62 elliptic curve c2pnb208w1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb239v1, ECCurve_X9_62_CHAR2_TNB239V1,
        "ANSI X9.62 elliptic curve c2tnb239v1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb239v2, ECCurve_X9_62_CHAR2_TNB239V2,
        "ANSI X9.62 elliptic curve c2tnb239v2",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb239v3, ECCurve_X9_62_CHAR2_TNB239V3,
        "ANSI X9.62 elliptic curve c2tnb239v3",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    { { siDEROID, NULL, 0 }, ECCurve_noName,
        "Unknown OID", CKM_INVALID_MECHANISM, INVALID_CERT_EXTENSION },
    OD( ansiX962c2pnb272w1, ECCurve_X9_62_CHAR2_PNB272W1,
        "ANSI X9.62 elliptic curve c2pnb272w1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2pnb304w1, ECCurve_X9_62_CHAR2_PNB304W1,
        "ANSI X9.62 elliptic curve c2pnb304w1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb359v1, ECCurve_X9_62_CHAR2_TNB359V1,
        "ANSI X9.62 elliptic curve c2tnb359v1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2pnb368w1, ECCurve_X9_62_CHAR2_PNB368W1,
        "ANSI X9.62 elliptic curve c2pnb368w1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION ),
    OD( ansiX962c2tnb431r1, ECCurve_X9_62_CHAR2_TNB431R1,
        "ANSI X9.62 elliptic curve c2tnb431r1",
        CKM_INVALID_MECHANISM,
        INVALID_CERT_EXTENSION )
};

SECOidData *
SECOID_FindOID(const SECItem *oid)
{
    SECOidData *po;
    SECOidData *ret = NULL;

    if (oid->len == 8) {
        if (oid->data[6] == 0x00) {
                /* XXX bounds check */
                po = &ANSI_oids[oid->data[7]];
                if (memcmp(oid->data, po->oid.data, 8) == 0)
                        ret = po;
        }
        if (oid->data[6] == 0x01) {
                /* XXX bounds check */
                po = &ANSI_prime_oids[oid->data[7]];
                if (memcmp(oid->data, po->oid.data, 8) == 0)
                        ret = po;
        }
    } else if (oid->len == 5) {
        /* XXX bounds check */
        po = &SECG_oids[oid->data[4]];
        if (memcmp(oid->data, po->oid.data, 5) == 0)
                ret = po;
    }
    return(ret);
}

ECCurveName
SECOID_FindOIDTag(const SECItem *oid)
{
    SECOidData *oiddata;

    oiddata = SECOID_FindOID (oid);
    if (oiddata == NULL)
        return ECCurve_noName;

    return oiddata->offset;
}
