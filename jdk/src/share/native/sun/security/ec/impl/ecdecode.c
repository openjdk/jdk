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
 * The Original Code is the Elliptic Curve Cryptography library.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Dr Vipul Gupta <vipul.gupta@sun.com> and
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories
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

#pragma ident   "%Z%%M% %I%     %E% SMI"

#include <sys/types.h>

#ifndef _WIN32
#ifndef __linux__
#include <sys/systm.h>
#endif /* __linux__ */
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

#define MAX_ECKEY_LEN           72
#define SEC_ASN1_OBJECT_ID      0x06

/*
 * Initializes a SECItem from a hexadecimal string
 *
 * Warning: This function ignores leading 00's, so any leading 00's
 * in the hexadecimal string must be optional.
 */
static SECItem *
hexString2SECItem(PRArenaPool *arena, SECItem *item, const char *str,
    int kmflag)
{
    int i = 0;
    int byteval = 0;
    int tmp = strlen(str);

    if ((tmp % 2) != 0) return NULL;

    /* skip leading 00's unless the hex string is "00" */
    while ((tmp > 2) && (str[0] == '0') && (str[1] == '0')) {
        str += 2;
        tmp -= 2;
    }

    item->data = (unsigned char *) PORT_ArenaAlloc(arena, tmp/2, kmflag);
    if (item->data == NULL) return NULL;
    item->len = tmp/2;

    while (str[i]) {
        if ((str[i] >= '0') && (str[i] <= '9'))
            tmp = str[i] - '0';
        else if ((str[i] >= 'a') && (str[i] <= 'f'))
            tmp = str[i] - 'a' + 10;
        else if ((str[i] >= 'A') && (str[i] <= 'F'))
            tmp = str[i] - 'A' + 10;
        else
            return NULL;

        byteval = byteval * 16 + tmp;
        if ((i % 2) != 0) {
            item->data[i/2] = byteval;
            byteval = 0;
        }
        i++;
    }

    return item;
}

static SECStatus
gf_populate_params(ECCurveName name, ECFieldType field_type, ECParams *params,
    int kmflag)
{
    SECStatus rv = SECFailure;
    const ECCurveParams *curveParams;
    /* 2 ['0'+'4'] + MAX_ECKEY_LEN * 2 [x,y] * 2 [hex string] + 1 ['\0'] */
    char genenc[3 + 2 * 2 * MAX_ECKEY_LEN];

    if ((name < ECCurve_noName) || (name > ECCurve_pastLastCurve)) goto cleanup;
    params->name = name;
    curveParams = ecCurve_map[params->name];
    CHECK_OK(curveParams);
    params->fieldID.size = curveParams->size;
    params->fieldID.type = field_type;
    if (field_type == ec_field_GFp) {
        CHECK_OK(hexString2SECItem(NULL, &params->fieldID.u.prime,
            curveParams->irr, kmflag));
    } else {
        CHECK_OK(hexString2SECItem(NULL, &params->fieldID.u.poly,
            curveParams->irr, kmflag));
    }
    CHECK_OK(hexString2SECItem(NULL, &params->curve.a,
        curveParams->curvea, kmflag));
    CHECK_OK(hexString2SECItem(NULL, &params->curve.b,
        curveParams->curveb, kmflag));
    genenc[0] = '0';
    genenc[1] = '4';
    genenc[2] = '\0';
    strcat(genenc, curveParams->genx);
    strcat(genenc, curveParams->geny);
    CHECK_OK(hexString2SECItem(NULL, &params->base, genenc, kmflag));
    CHECK_OK(hexString2SECItem(NULL, &params->order,
        curveParams->order, kmflag));
    params->cofactor = curveParams->cofactor;

    rv = SECSuccess;

cleanup:
    return rv;
}

ECCurveName SECOID_FindOIDTag(const SECItem *);

SECStatus
EC_FillParams(PRArenaPool *arena, const SECItem *encodedParams,
    ECParams *params, int kmflag)
{
    SECStatus rv = SECFailure;
    ECCurveName tag;
    SECItem oid = { siBuffer, NULL, 0};

#if EC_DEBUG
    int i;

    printf("Encoded params in EC_DecodeParams: ");
    for (i = 0; i < encodedParams->len; i++) {
            printf("%02x:", encodedParams->data[i]);
    }
    printf("\n");
#endif

    if ((encodedParams->len != ANSI_X962_CURVE_OID_TOTAL_LEN) &&
        (encodedParams->len != SECG_CURVE_OID_TOTAL_LEN)) {
            PORT_SetError(SEC_ERROR_UNSUPPORTED_ELLIPTIC_CURVE);
            return SECFailure;
    };

    oid.len = encodedParams->len - 2;
    oid.data = encodedParams->data + 2;
    if ((encodedParams->data[0] != SEC_ASN1_OBJECT_ID) ||
        ((tag = SECOID_FindOIDTag(&oid)) == ECCurve_noName)) {
            PORT_SetError(SEC_ERROR_UNSUPPORTED_ELLIPTIC_CURVE);
            return SECFailure;
    }

    params->arena = arena;
    params->cofactor = 0;
    params->type = ec_params_named;
    params->name = ECCurve_noName;

    /* For named curves, fill out curveOID */
    params->curveOID.len = oid.len;
    params->curveOID.data = (unsigned char *) PORT_ArenaAlloc(NULL, oid.len,
        kmflag);
    if (params->curveOID.data == NULL) goto cleanup;
    memcpy(params->curveOID.data, oid.data, oid.len);

#if EC_DEBUG
#ifndef SECOID_FindOIDTagDescription
    printf("Curve: %s\n", ecCurve_map[tag]->text);
#else
    printf("Curve: %s\n", SECOID_FindOIDTagDescription(tag));
#endif
#endif

    switch (tag) {

    /* Binary curves */

    case ECCurve_X9_62_CHAR2_PNB163V1:
        /* Populate params for c2pnb163v1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB163V1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_PNB163V2:
        /* Populate params for c2pnb163v2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB163V2, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_PNB163V3:
        /* Populate params for c2pnb163v3 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB163V3, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_PNB176V1:
        /* Populate params for c2pnb176v1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB176V1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB191V1:
        /* Populate params for c2tnb191v1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB191V1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB191V2:
        /* Populate params for c2tnb191v2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB191V2, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB191V3:
        /* Populate params for c2tnb191v3 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB191V3, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_PNB208W1:
        /* Populate params for c2pnb208w1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB208W1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB239V1:
        /* Populate params for c2tnb239v1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB239V1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB239V2:
        /* Populate params for c2tnb239v2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB239V2, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB239V3:
        /* Populate params for c2tnb239v3 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB239V3, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_PNB272W1:
        /* Populate params for c2pnb272w1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB272W1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_PNB304W1:
        /* Populate params for c2pnb304w1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB304W1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB359V1:
        /* Populate params for c2tnb359v1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB359V1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_PNB368W1:
        /* Populate params for c2pnb368w1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_PNB368W1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_X9_62_CHAR2_TNB431R1:
        /* Populate params for c2tnb431r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_CHAR2_TNB431R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_113R1:
        /* Populate params for sect113r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_113R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_113R2:
        /* Populate params for sect113r2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_113R2, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_131R1:
        /* Populate params for sect131r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_131R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_131R2:
        /* Populate params for sect131r2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_131R2, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_163K1:
        /* Populate params for sect163k1
         * (the NIST K-163 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_163K1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_163R1:
        /* Populate params for sect163r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_163R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_163R2:
        /* Populate params for sect163r2
         * (the NIST B-163 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_163R2, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_193R1:
        /* Populate params for sect193r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_193R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_193R2:
        /* Populate params for sect193r2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_193R2, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_233K1:
        /* Populate params for sect233k1
         * (the NIST K-233 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_233K1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_233R1:
        /* Populate params for sect233r1
         * (the NIST B-233 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_233R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_239K1:
        /* Populate params for sect239k1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_239K1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_283K1:
        /* Populate params for sect283k1
         * (the NIST K-283 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_283K1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_283R1:
        /* Populate params for sect283r1
         * (the NIST B-283 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_283R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_409K1:
        /* Populate params for sect409k1
         * (the NIST K-409 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_409K1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_409R1:
        /* Populate params for sect409r1
         * (the NIST B-409 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_409R1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_571K1:
        /* Populate params for sect571k1
         * (the NIST K-571 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_571K1, ec_field_GF2m,
            params, kmflag) );
        break;

    case ECCurve_SECG_CHAR2_571R1:
        /* Populate params for sect571r1
         * (the NIST B-571 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_CHAR2_571R1, ec_field_GF2m,
            params, kmflag) );
        break;

    /* Prime curves */

    case ECCurve_X9_62_PRIME_192V1:
        /* Populate params for prime192v1 aka secp192r1
         * (the NIST P-192 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_PRIME_192V1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_X9_62_PRIME_192V2:
        /* Populate params for prime192v2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_PRIME_192V2, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_X9_62_PRIME_192V3:
        /* Populate params for prime192v3 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_PRIME_192V3, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_X9_62_PRIME_239V1:
        /* Populate params for prime239v1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_PRIME_239V1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_X9_62_PRIME_239V2:
        /* Populate params for prime239v2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_PRIME_239V2, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_X9_62_PRIME_239V3:
        /* Populate params for prime239v3 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_PRIME_239V3, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_X9_62_PRIME_256V1:
        /* Populate params for prime256v1 aka secp256r1
         * (the NIST P-256 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_X9_62_PRIME_256V1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_112R1:
        /* Populate params for secp112r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_112R1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_112R2:
        /* Populate params for secp112r2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_112R2, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_128R1:
        /* Populate params for secp128r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_128R1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_128R2:
        /* Populate params for secp128r2 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_128R2, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_160K1:
        /* Populate params for secp160k1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_160K1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_160R1:
        /* Populate params for secp160r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_160R1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_160R2:
        /* Populate params for secp160r1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_160R2, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_192K1:
        /* Populate params for secp192k1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_192K1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_224K1:
        /* Populate params for secp224k1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_224K1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_224R1:
        /* Populate params for secp224r1
         * (the NIST P-224 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_224R1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_256K1:
        /* Populate params for secp256k1 */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_256K1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_384R1:
        /* Populate params for secp384r1
         * (the NIST P-384 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_384R1, ec_field_GFp,
            params, kmflag) );
        break;

    case ECCurve_SECG_PRIME_521R1:
        /* Populate params for secp521r1
         * (the NIST P-521 curve)
         */
        CHECK_SEC_OK( gf_populate_params(ECCurve_SECG_PRIME_521R1, ec_field_GFp,
            params, kmflag) );
        break;

    default:
        break;
    };

cleanup:
    if (!params->cofactor) {
        PORT_SetError(SEC_ERROR_UNSUPPORTED_ELLIPTIC_CURVE);
#if EC_DEBUG
        printf("Unrecognized curve, returning NULL params\n");
#endif
    }

    return rv;
}

SECStatus
EC_DecodeParams(const SECItem *encodedParams, ECParams **ecparams, int kmflag)
{
    PRArenaPool *arena;
    ECParams *params;
    SECStatus rv = SECFailure;

    /* Initialize an arena for the ECParams structure */
    if (!(arena = PORT_NewArena(NSS_FREEBL_DEFAULT_CHUNKSIZE)))
        return SECFailure;

    params = (ECParams *)PORT_ArenaZAlloc(NULL, sizeof(ECParams), kmflag);
    if (!params) {
        PORT_FreeArena(NULL, B_TRUE);
        return SECFailure;
    }

    /* Copy the encoded params */
    SECITEM_AllocItem(arena, &(params->DEREncoding), encodedParams->len,
        kmflag);
    memcpy(params->DEREncoding.data, encodedParams->data, encodedParams->len);

    /* Fill out the rest of the ECParams structure based on
     * the encoded params
     */
    rv = EC_FillParams(NULL, encodedParams, params, kmflag);
    if (rv == SECFailure) {
        PORT_FreeArena(NULL, B_TRUE);
        return SECFailure;
    } else {
        *ecparams = params;;
        return SECSuccess;
    }
}
