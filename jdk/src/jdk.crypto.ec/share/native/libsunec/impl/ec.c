/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
 *   Dr Vipul Gupta <vipul.gupta@sun.com> and
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories
 *
 *********************************************************************** */

#include "mplogic.h"
#include "ec.h"
#include "ecl.h"

#include <sys/types.h>
#ifndef _KERNEL
#include <stdlib.h>
#include <string.h>

#ifndef _WIN32
#include <stdio.h>
#include <strings.h>
#endif /* _WIN32 */

#endif
#include "ecl-exp.h"
#include "mpi.h"
#include "ecc_impl.h"

#ifdef _KERNEL
#define PORT_ZFree(p, l)                bzero((p), (l)); kmem_free((p), (l))
#else
#ifndef _WIN32
#define PORT_ZFree(p, l)                bzero((p), (l)); free((p))
#else
#define PORT_ZFree(p, l)                memset((p), 0, (l)); free((p))
#endif /* _WIN32 */
#endif

/*
 * Returns true if pointP is the point at infinity, false otherwise
 */
PRBool
ec_point_at_infinity(SECItem *pointP)
{
    unsigned int i;

    for (i = 1; i < pointP->len; i++) {
        if (pointP->data[i] != 0x00) return PR_FALSE;
    }

    return PR_TRUE;
}

/*
 * Computes scalar point multiplication pointQ = k1 * G + k2 * pointP for
 * the curve whose parameters are encoded in params with base point G.
 */
SECStatus
ec_points_mul(const ECParams *params, const mp_int *k1, const mp_int *k2,
             const SECItem *pointP, SECItem *pointQ, int kmflag)
{
    mp_int Px, Py, Qx, Qy;
    mp_int Gx, Gy, order, irreducible, a, b;
#if 0 /* currently don't support non-named curves */
    unsigned int irr_arr[5];
#endif
    ECGroup *group = NULL;
    SECStatus rv = SECFailure;
    mp_err err = MP_OKAY;
    unsigned int len;

#if EC_DEBUG
    int i;
    char mpstr[256];

    printf("ec_points_mul: params [len=%d]:", params->DEREncoding.len);
    for (i = 0; i < params->DEREncoding.len; i++)
            printf("%02x:", params->DEREncoding.data[i]);
    printf("\n");

        if (k1 != NULL) {
                mp_tohex(k1, mpstr);
                printf("ec_points_mul: scalar k1: %s\n", mpstr);
                mp_todecimal(k1, mpstr);
                printf("ec_points_mul: scalar k1: %s (dec)\n", mpstr);
        }

        if (k2 != NULL) {
                mp_tohex(k2, mpstr);
                printf("ec_points_mul: scalar k2: %s\n", mpstr);
                mp_todecimal(k2, mpstr);
                printf("ec_points_mul: scalar k2: %s (dec)\n", mpstr);
        }

        if (pointP != NULL) {
                printf("ec_points_mul: pointP [len=%d]:", pointP->len);
                for (i = 0; i < pointP->len; i++)
                        printf("%02x:", pointP->data[i]);
                printf("\n");
        }
#endif

        /* NOTE: We only support uncompressed points for now */
        len = (params->fieldID.size + 7) >> 3;
        if (pointP != NULL) {
                if ((pointP->data[0] != EC_POINT_FORM_UNCOMPRESSED) ||
                        (pointP->len != (2 * len + 1))) {
                        return SECFailure;
                };
        }

        MP_DIGITS(&Px) = 0;
        MP_DIGITS(&Py) = 0;
        MP_DIGITS(&Qx) = 0;
        MP_DIGITS(&Qy) = 0;
        MP_DIGITS(&Gx) = 0;
        MP_DIGITS(&Gy) = 0;
        MP_DIGITS(&order) = 0;
        MP_DIGITS(&irreducible) = 0;
        MP_DIGITS(&a) = 0;
        MP_DIGITS(&b) = 0;
        CHECK_MPI_OK( mp_init(&Px, kmflag) );
        CHECK_MPI_OK( mp_init(&Py, kmflag) );
        CHECK_MPI_OK( mp_init(&Qx, kmflag) );
        CHECK_MPI_OK( mp_init(&Qy, kmflag) );
        CHECK_MPI_OK( mp_init(&Gx, kmflag) );
        CHECK_MPI_OK( mp_init(&Gy, kmflag) );
        CHECK_MPI_OK( mp_init(&order, kmflag) );
        CHECK_MPI_OK( mp_init(&irreducible, kmflag) );
        CHECK_MPI_OK( mp_init(&a, kmflag) );
        CHECK_MPI_OK( mp_init(&b, kmflag) );

        if ((k2 != NULL) && (pointP != NULL)) {
                /* Initialize Px and Py */
                CHECK_MPI_OK( mp_read_unsigned_octets(&Px, pointP->data + 1, (mp_size) len) );
                CHECK_MPI_OK( mp_read_unsigned_octets(&Py, pointP->data + 1 + len, (mp_size) len) );
        }

        /* construct from named params, if possible */
        if (params->name != ECCurve_noName) {
                group = ECGroup_fromName(params->name, kmflag);
        }

#if 0 /* currently don't support non-named curves */
        if (group == NULL) {
                /* Set up mp_ints containing the curve coefficients */
                CHECK_MPI_OK( mp_read_unsigned_octets(&Gx, params->base.data + 1,
                                                                                  (mp_size) len) );
                CHECK_MPI_OK( mp_read_unsigned_octets(&Gy, params->base.data + 1 + len,
                                                                                  (mp_size) len) );
                SECITEM_TO_MPINT( params->order, &order );
                SECITEM_TO_MPINT( params->curve.a, &a );
                SECITEM_TO_MPINT( params->curve.b, &b );
                if (params->fieldID.type == ec_field_GFp) {
                        SECITEM_TO_MPINT( params->fieldID.u.prime, &irreducible );
                        group = ECGroup_consGFp(&irreducible, &a, &b, &Gx, &Gy, &order, params->cofactor);
                } else {
                        SECITEM_TO_MPINT( params->fieldID.u.poly, &irreducible );
                        irr_arr[0] = params->fieldID.size;
                        irr_arr[1] = params->fieldID.k1;
                        irr_arr[2] = params->fieldID.k2;
                        irr_arr[3] = params->fieldID.k3;
                        irr_arr[4] = 0;
                        group = ECGroup_consGF2m(&irreducible, irr_arr, &a, &b, &Gx, &Gy, &order, params->cofactor);
                }
        }
#endif
        if (group == NULL)
                goto cleanup;

        if ((k2 != NULL) && (pointP != NULL)) {
                CHECK_MPI_OK( ECPoints_mul(group, k1, k2, &Px, &Py, &Qx, &Qy) );
        } else {
                CHECK_MPI_OK( ECPoints_mul(group, k1, NULL, NULL, NULL, &Qx, &Qy) );
    }

    /* Construct the SECItem representation of point Q */
    pointQ->data[0] = EC_POINT_FORM_UNCOMPRESSED;
    CHECK_MPI_OK( mp_to_fixlen_octets(&Qx, pointQ->data + 1,
                                      (mp_size) len) );
    CHECK_MPI_OK( mp_to_fixlen_octets(&Qy, pointQ->data + 1 + len,
                                      (mp_size) len) );

    rv = SECSuccess;

#if EC_DEBUG
    printf("ec_points_mul: pointQ [len=%d]:", pointQ->len);
    for (i = 0; i < pointQ->len; i++)
            printf("%02x:", pointQ->data[i]);
    printf("\n");
#endif

cleanup:
    ECGroup_free(group);
    mp_clear(&Px);
    mp_clear(&Py);
    mp_clear(&Qx);
    mp_clear(&Qy);
    mp_clear(&Gx);
    mp_clear(&Gy);
    mp_clear(&order);
    mp_clear(&irreducible);
    mp_clear(&a);
    mp_clear(&b);
    if (err) {
        MP_TO_SEC_ERROR(err);
        rv = SECFailure;
    }

    return rv;
}

/* Generates a new EC key pair. The private key is a supplied
 * value and the public key is the result of performing a scalar
 * point multiplication of that value with the curve's base point.
 */
SECStatus
ec_NewKey(ECParams *ecParams, ECPrivateKey **privKey,
    const unsigned char *privKeyBytes, int privKeyLen, int kmflag)
{
    SECStatus rv = SECFailure;
    PRArenaPool *arena;
    ECPrivateKey *key;
    mp_int k;
    mp_err err = MP_OKAY;
    int len;

#if EC_DEBUG
    printf("ec_NewKey called\n");
#endif

    if (!ecParams || !privKey || !privKeyBytes || (privKeyLen < 0)) {
        PORT_SetError(SEC_ERROR_INVALID_ARGS);
        return SECFailure;
    }

    /* Initialize an arena for the EC key. */
    if (!(arena = PORT_NewArena(NSS_FREEBL_DEFAULT_CHUNKSIZE)))
        return SECFailure;

    key = (ECPrivateKey *)PORT_ArenaZAlloc(arena, sizeof(ECPrivateKey),
        kmflag);
    if (!key) {
        PORT_FreeArena(arena, PR_TRUE);
        return SECFailure;
    }

    /* Set the version number (SEC 1 section C.4 says it should be 1) */
    SECITEM_AllocItem(arena, &key->version, 1, kmflag);
    key->version.data[0] = 1;

    /* Copy all of the fields from the ECParams argument to the
     * ECParams structure within the private key.
     */
    key->ecParams.arena = arena;
    key->ecParams.type = ecParams->type;
    key->ecParams.fieldID.size = ecParams->fieldID.size;
    key->ecParams.fieldID.type = ecParams->fieldID.type;
    if (ecParams->fieldID.type == ec_field_GFp) {
        CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.fieldID.u.prime,
            &ecParams->fieldID.u.prime, kmflag));
    } else {
        CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.fieldID.u.poly,
            &ecParams->fieldID.u.poly, kmflag));
    }
    key->ecParams.fieldID.k1 = ecParams->fieldID.k1;
    key->ecParams.fieldID.k2 = ecParams->fieldID.k2;
    key->ecParams.fieldID.k3 = ecParams->fieldID.k3;
    CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.curve.a,
        &ecParams->curve.a, kmflag));
    CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.curve.b,
        &ecParams->curve.b, kmflag));
    CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.curve.seed,
        &ecParams->curve.seed, kmflag));
    CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.base,
        &ecParams->base, kmflag));
    CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.order,
        &ecParams->order, kmflag));
    key->ecParams.cofactor = ecParams->cofactor;
    CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.DEREncoding,
        &ecParams->DEREncoding, kmflag));
    key->ecParams.name = ecParams->name;
    CHECK_SEC_OK(SECITEM_CopyItem(arena, &key->ecParams.curveOID,
        &ecParams->curveOID, kmflag));

    len = (ecParams->fieldID.size + 7) >> 3;
    SECITEM_AllocItem(arena, &key->publicValue, 2*len + 1, kmflag);
    len = ecParams->order.len;
    SECITEM_AllocItem(arena, &key->privateValue, len, kmflag);

    /* Copy private key */
    if (privKeyLen >= len) {
        memcpy(key->privateValue.data, privKeyBytes, len);
    } else {
        memset(key->privateValue.data, 0, (len - privKeyLen));
        memcpy(key->privateValue.data + (len - privKeyLen), privKeyBytes, privKeyLen);
    }

    /* Compute corresponding public key */
    MP_DIGITS(&k) = 0;
    CHECK_MPI_OK( mp_init(&k, kmflag) );
    CHECK_MPI_OK( mp_read_unsigned_octets(&k, key->privateValue.data,
        (mp_size) len) );

    rv = ec_points_mul(ecParams, &k, NULL, NULL, &(key->publicValue), kmflag);
    if (rv != SECSuccess) goto cleanup;
    *privKey = key;

cleanup:
    mp_clear(&k);
    if (rv) {
        PORT_FreeArena(arena, PR_TRUE);
    }

#if EC_DEBUG
    printf("ec_NewKey returning %s\n",
        (rv == SECSuccess) ? "success" : "failure");
#endif

    return rv;

}

/* Generates a new EC key pair. The private key is a supplied
 * random value (in seed) and the public key is the result of
 * performing a scalar point multiplication of that value with
 * the curve's base point.
 */
SECStatus
EC_NewKeyFromSeed(ECParams *ecParams, ECPrivateKey **privKey,
    const unsigned char *seed, int seedlen, int kmflag)
{
    SECStatus rv = SECFailure;
    rv = ec_NewKey(ecParams, privKey, seed, seedlen, kmflag);
    return rv;
}

/* Generate a random private key using the algorithm A.4.1 of ANSI X9.62,
 * modified a la FIPS 186-2 Change Notice 1 to eliminate the bias in the
 * random number generator.
 *
 * Parameters
 * - order: a buffer that holds the curve's group order
 * - len: the length in octets of the order buffer
 * - random: a buffer of 2 * len random bytes
 * - randomlen: the length in octets of the random buffer
 *
 * Return Value
 * Returns a buffer of len octets that holds the private key. The caller
 * is responsible for freeing the buffer with PORT_ZFree.
 */
static unsigned char *
ec_GenerateRandomPrivateKey(const unsigned char *order, int len,
    const unsigned char *random, int randomlen, int kmflag)
{
    SECStatus rv = SECSuccess;
    mp_err err;
    unsigned char *privKeyBytes = NULL;
    mp_int privKeyVal, order_1, one;

    MP_DIGITS(&privKeyVal) = 0;
    MP_DIGITS(&order_1) = 0;
    MP_DIGITS(&one) = 0;
    CHECK_MPI_OK( mp_init(&privKeyVal, kmflag) );
    CHECK_MPI_OK( mp_init(&order_1, kmflag) );
    CHECK_MPI_OK( mp_init(&one, kmflag) );

    /*
     * Reduces the 2*len buffer of random bytes modulo the group order.
     */
    if ((privKeyBytes = PORT_Alloc(2*len, kmflag)) == NULL) goto cleanup;
    if (randomlen != 2 * len) {
        randomlen = 2 * len;
    }
    /* No need to generate - random bytes are now supplied */
    /* CHECK_SEC_OK( RNG_GenerateGlobalRandomBytes(privKeyBytes, 2*len) );*/
    memcpy(privKeyBytes, random, randomlen);

    CHECK_MPI_OK( mp_read_unsigned_octets(&privKeyVal, privKeyBytes, 2*len) );
    CHECK_MPI_OK( mp_read_unsigned_octets(&order_1, order, len) );
    CHECK_MPI_OK( mp_set_int(&one, 1) );
    CHECK_MPI_OK( mp_sub(&order_1, &one, &order_1) );
    CHECK_MPI_OK( mp_mod(&privKeyVal, &order_1, &privKeyVal) );
    CHECK_MPI_OK( mp_add(&privKeyVal, &one, &privKeyVal) );
    CHECK_MPI_OK( mp_to_fixlen_octets(&privKeyVal, privKeyBytes, len) );
    memset(privKeyBytes+len, 0, len);
cleanup:
    mp_clear(&privKeyVal);
    mp_clear(&order_1);
    mp_clear(&one);
    if (err < MP_OKAY) {
        MP_TO_SEC_ERROR(err);
        rv = SECFailure;
    }
    if (rv != SECSuccess && privKeyBytes) {
#ifdef _KERNEL
        kmem_free(privKeyBytes, 2*len);
#else
        free(privKeyBytes);
#endif
        privKeyBytes = NULL;
    }
    return privKeyBytes;
}

/* Generates a new EC key pair. The private key is a random value and
 * the public key is the result of performing a scalar point multiplication
 * of that value with the curve's base point.
 */
SECStatus
EC_NewKey(ECParams *ecParams, ECPrivateKey **privKey,
    const unsigned char* random, int randomlen, int kmflag)
{
    SECStatus rv = SECFailure;
    int len;
    unsigned char *privKeyBytes = NULL;

    if (!ecParams) {
        PORT_SetError(SEC_ERROR_INVALID_ARGS);
        return SECFailure;
    }

    len = ecParams->order.len;
    privKeyBytes = ec_GenerateRandomPrivateKey(ecParams->order.data, len,
        random, randomlen, kmflag);
    if (privKeyBytes == NULL) goto cleanup;
    /* generate public key */
    CHECK_SEC_OK( ec_NewKey(ecParams, privKey, privKeyBytes, len, kmflag) );

cleanup:
    if (privKeyBytes) {
        PORT_ZFree(privKeyBytes, len * 2);
    }
#if EC_DEBUG
    printf("EC_NewKey returning %s\n",
        (rv == SECSuccess) ? "success" : "failure");
#endif

    return rv;
}

/* Validates an EC public key as described in Section 5.2.2 of
 * X9.62. The ECDH primitive when used without the cofactor does
 * not address small subgroup attacks, which may occur when the
 * public key is not valid. These attacks can be prevented by
 * validating the public key before using ECDH.
 */
SECStatus
EC_ValidatePublicKey(ECParams *ecParams, SECItem *publicValue, int kmflag)
{
    mp_int Px, Py;
    ECGroup *group = NULL;
    SECStatus rv = SECFailure;
    mp_err err = MP_OKAY;
    unsigned int len;

    if (!ecParams || !publicValue) {
        PORT_SetError(SEC_ERROR_INVALID_ARGS);
        return SECFailure;
    }

    /* NOTE: We only support uncompressed points for now */
    len = (ecParams->fieldID.size + 7) >> 3;
    if (publicValue->data[0] != EC_POINT_FORM_UNCOMPRESSED) {
        PORT_SetError(SEC_ERROR_UNSUPPORTED_EC_POINT_FORM);
        return SECFailure;
    } else if (publicValue->len != (2 * len + 1)) {
        PORT_SetError(SEC_ERROR_BAD_KEY);
        return SECFailure;
    }

    MP_DIGITS(&Px) = 0;
    MP_DIGITS(&Py) = 0;
    CHECK_MPI_OK( mp_init(&Px, kmflag) );
    CHECK_MPI_OK( mp_init(&Py, kmflag) );

    /* Initialize Px and Py */
    CHECK_MPI_OK( mp_read_unsigned_octets(&Px, publicValue->data + 1, (mp_size) len) );
    CHECK_MPI_OK( mp_read_unsigned_octets(&Py, publicValue->data + 1 + len, (mp_size) len) );

    /* construct from named params */
    group = ECGroup_fromName(ecParams->name, kmflag);
    if (group == NULL) {
        /*
         * ECGroup_fromName fails if ecParams->name is not a valid
         * ECCurveName value, or if we run out of memory, or perhaps
         * for other reasons.  Unfortunately if ecParams->name is a
         * valid ECCurveName value, we don't know what the right error
         * code should be because ECGroup_fromName doesn't return an
         * error code to the caller.  Set err to MP_UNDEF because
         * that's what ECGroup_fromName uses internally.
         */
        if ((ecParams->name <= ECCurve_noName) ||
            (ecParams->name >= ECCurve_pastLastCurve)) {
            err = MP_BADARG;
        } else {
            err = MP_UNDEF;
        }
        goto cleanup;
    }

    /* validate public point */
    if ((err = ECPoint_validate(group, &Px, &Py)) < MP_YES) {
        if (err == MP_NO) {
            PORT_SetError(SEC_ERROR_BAD_KEY);
            rv = SECFailure;
            err = MP_OKAY;  /* don't change the error code */
        }
        goto cleanup;
    }

    rv = SECSuccess;

cleanup:
    ECGroup_free(group);
    mp_clear(&Px);
    mp_clear(&Py);
    if (err) {
        MP_TO_SEC_ERROR(err);
        rv = SECFailure;
    }
    return rv;
}

/*
** Performs an ECDH key derivation by computing the scalar point
** multiplication of privateValue and publicValue (with or without the
** cofactor) and returns the x-coordinate of the resulting elliptic
** curve point in derived secret.  If successful, derivedSecret->data
** is set to the address of the newly allocated buffer containing the
** derived secret, and derivedSecret->len is the size of the secret
** produced. It is the caller's responsibility to free the allocated
** buffer containing the derived secret.
*/
SECStatus
ECDH_Derive(SECItem  *publicValue,
            ECParams *ecParams,
            SECItem  *privateValue,
            PRBool    withCofactor,
            SECItem  *derivedSecret,
            int kmflag)
{
    SECStatus rv = SECFailure;
    unsigned int len = 0;
    SECItem pointQ = {siBuffer, NULL, 0};
    mp_int k; /* to hold the private value */
    mp_int cofactor;
    mp_err err = MP_OKAY;
#if EC_DEBUG
    int i;
#endif

    if (!publicValue || !ecParams || !privateValue ||
        !derivedSecret) {
        PORT_SetError(SEC_ERROR_INVALID_ARGS);
        return SECFailure;
    }

    if (EC_ValidatePublicKey(ecParams, publicValue, kmflag) != SECSuccess) {
        return SECFailure;
    }

    memset(derivedSecret, 0, sizeof *derivedSecret);
    len = (ecParams->fieldID.size + 7) >> 3;
    pointQ.len = 2*len + 1;
    if ((pointQ.data = PORT_Alloc(2*len + 1, kmflag)) == NULL) goto cleanup;

    MP_DIGITS(&k) = 0;
    CHECK_MPI_OK( mp_init(&k, kmflag) );
    CHECK_MPI_OK( mp_read_unsigned_octets(&k, privateValue->data,
                                          (mp_size) privateValue->len) );

    if (withCofactor && (ecParams->cofactor != 1)) {
            /* multiply k with the cofactor */
            MP_DIGITS(&cofactor) = 0;
            CHECK_MPI_OK( mp_init(&cofactor, kmflag) );
            mp_set(&cofactor, ecParams->cofactor);
            CHECK_MPI_OK( mp_mul(&k, &cofactor, &k) );
    }

    /* Multiply our private key and peer's public point */
    if ((ec_points_mul(ecParams, NULL, &k, publicValue, &pointQ, kmflag) != SECSuccess) ||
        ec_point_at_infinity(&pointQ))
        goto cleanup;

    /* Allocate memory for the derived secret and copy
     * the x co-ordinate of pointQ into it.
     */
    SECITEM_AllocItem(NULL, derivedSecret, len, kmflag);
    memcpy(derivedSecret->data, pointQ.data + 1, len);

    rv = SECSuccess;

#if EC_DEBUG
    printf("derived_secret:\n");
    for (i = 0; i < derivedSecret->len; i++)
        printf("%02x:", derivedSecret->data[i]);
    printf("\n");
#endif

cleanup:
    mp_clear(&k);

    if (pointQ.data) {
        PORT_ZFree(pointQ.data, 2*len + 1);
    }

    return rv;
}

/* Computes the ECDSA signature (a concatenation of two values r and s)
 * on the digest using the given key and the random value kb (used in
 * computing s).
 */
SECStatus
ECDSA_SignDigestWithSeed(ECPrivateKey *key, SECItem *signature,
    const SECItem *digest, const unsigned char *kb, const int kblen, int kmflag)
{
    SECStatus rv = SECFailure;
    mp_int x1;
    mp_int d, k;     /* private key, random integer */
    mp_int r, s;     /* tuple (r, s) is the signature */
    mp_int n;
    mp_err err = MP_OKAY;
    ECParams *ecParams = NULL;
    SECItem kGpoint = { siBuffer, NULL, 0};
    int flen = 0;    /* length in bytes of the field size */
    unsigned olen;   /* length in bytes of the base point order */

#if EC_DEBUG
    char mpstr[256];
#endif

    /* Initialize MPI integers. */
    /* must happen before the first potential call to cleanup */
    MP_DIGITS(&x1) = 0;
    MP_DIGITS(&d) = 0;
    MP_DIGITS(&k) = 0;
    MP_DIGITS(&r) = 0;
    MP_DIGITS(&s) = 0;
    MP_DIGITS(&n) = 0;

    /* Check args */
    if (!key || !signature || !digest || !kb || (kblen < 0)) {
        PORT_SetError(SEC_ERROR_INVALID_ARGS);
        goto cleanup;
    }

    ecParams = &(key->ecParams);
    flen = (ecParams->fieldID.size + 7) >> 3;
    olen = ecParams->order.len;
    if (signature->data == NULL) {
        /* a call to get the signature length only */
        goto finish;
    }
    if (signature->len < 2*olen) {
        PORT_SetError(SEC_ERROR_OUTPUT_LEN);
        rv = SECBufferTooSmall;
        goto cleanup;
    }


    CHECK_MPI_OK( mp_init(&x1, kmflag) );
    CHECK_MPI_OK( mp_init(&d, kmflag) );
    CHECK_MPI_OK( mp_init(&k, kmflag) );
    CHECK_MPI_OK( mp_init(&r, kmflag) );
    CHECK_MPI_OK( mp_init(&s, kmflag) );
    CHECK_MPI_OK( mp_init(&n, kmflag) );

    SECITEM_TO_MPINT( ecParams->order, &n );
    SECITEM_TO_MPINT( key->privateValue, &d );
    CHECK_MPI_OK( mp_read_unsigned_octets(&k, kb, kblen) );
    /* Make sure k is in the interval [1, n-1] */
    if ((mp_cmp_z(&k) <= 0) || (mp_cmp(&k, &n) >= 0)) {
#if EC_DEBUG
        printf("k is outside [1, n-1]\n");
        mp_tohex(&k, mpstr);
        printf("k : %s \n", mpstr);
        mp_tohex(&n, mpstr);
        printf("n : %s \n", mpstr);
#endif
        PORT_SetError(SEC_ERROR_NEED_RANDOM);
        goto cleanup;
    }

    /*
    ** ANSI X9.62, Section 5.3.2, Step 2
    **
    ** Compute kG
    */
    kGpoint.len = 2*flen + 1;
    kGpoint.data = PORT_Alloc(2*flen + 1, kmflag);
    if ((kGpoint.data == NULL) ||
        (ec_points_mul(ecParams, &k, NULL, NULL, &kGpoint, kmflag)
            != SECSuccess))
        goto cleanup;

    /*
    ** ANSI X9.62, Section 5.3.3, Step 1
    **
    ** Extract the x co-ordinate of kG into x1
    */
    CHECK_MPI_OK( mp_read_unsigned_octets(&x1, kGpoint.data + 1,
                                          (mp_size) flen) );

    /*
    ** ANSI X9.62, Section 5.3.3, Step 2
    **
    ** r = x1 mod n  NOTE: n is the order of the curve
    */
    CHECK_MPI_OK( mp_mod(&x1, &n, &r) );

    /*
    ** ANSI X9.62, Section 5.3.3, Step 3
    **
    ** verify r != 0
    */
    if (mp_cmp_z(&r) == 0) {
        PORT_SetError(SEC_ERROR_NEED_RANDOM);
        goto cleanup;
    }

    /*
    ** ANSI X9.62, Section 5.3.3, Step 4
    **
    ** s = (k**-1 * (HASH(M) + d*r)) mod n
    */
    SECITEM_TO_MPINT(*digest, &s);        /* s = HASH(M)     */

    /* In the definition of EC signing, digests are truncated
     * to the length of n in bits.
     * (see SEC 1 "Elliptic Curve Digit Signature Algorithm" section 4.1.*/
    if (digest->len*8 > (unsigned int)ecParams->fieldID.size) {
        mpl_rsh(&s,&s,digest->len*8 - ecParams->fieldID.size);
    }

#if EC_DEBUG
    mp_todecimal(&n, mpstr);
    printf("n : %s (dec)\n", mpstr);
    mp_todecimal(&d, mpstr);
    printf("d : %s (dec)\n", mpstr);
    mp_tohex(&x1, mpstr);
    printf("x1: %s\n", mpstr);
    mp_todecimal(&s, mpstr);
    printf("digest: %s (decimal)\n", mpstr);
    mp_todecimal(&r, mpstr);
    printf("r : %s (dec)\n", mpstr);
    mp_tohex(&r, mpstr);
    printf("r : %s\n", mpstr);
#endif

    CHECK_MPI_OK( mp_invmod(&k, &n, &k) );      /* k = k**-1 mod n */
    CHECK_MPI_OK( mp_mulmod(&d, &r, &n, &d) );  /* d = d * r mod n */
    CHECK_MPI_OK( mp_addmod(&s, &d, &n, &s) );  /* s = s + d mod n */
    CHECK_MPI_OK( mp_mulmod(&s, &k, &n, &s) );  /* s = s * k mod n */

#if EC_DEBUG
    mp_todecimal(&s, mpstr);
    printf("s : %s (dec)\n", mpstr);
    mp_tohex(&s, mpstr);
    printf("s : %s\n", mpstr);
#endif

    /*
    ** ANSI X9.62, Section 5.3.3, Step 5
    **
    ** verify s != 0
    */
    if (mp_cmp_z(&s) == 0) {
        PORT_SetError(SEC_ERROR_NEED_RANDOM);
        goto cleanup;
    }

   /*
    **
    ** Signature is tuple (r, s)
    */
    CHECK_MPI_OK( mp_to_fixlen_octets(&r, signature->data, olen) );
    CHECK_MPI_OK( mp_to_fixlen_octets(&s, signature->data + olen, olen) );
finish:
    signature->len = 2*olen;

    rv = SECSuccess;
    err = MP_OKAY;
cleanup:
    mp_clear(&x1);
    mp_clear(&d);
    mp_clear(&k);
    mp_clear(&r);
    mp_clear(&s);
    mp_clear(&n);

    if (kGpoint.data) {
        PORT_ZFree(kGpoint.data, 2*flen + 1);
    }

    if (err) {
        MP_TO_SEC_ERROR(err);
        rv = SECFailure;
    }

#if EC_DEBUG
    printf("ECDSA signing with seed %s\n",
        (rv == SECSuccess) ? "succeeded" : "failed");
#endif

   return rv;
}

/*
** Computes the ECDSA signature on the digest using the given key
** and a random seed.
*/
SECStatus
ECDSA_SignDigest(ECPrivateKey *key, SECItem *signature, const SECItem *digest,
    const unsigned char* random, int randomLen, int kmflag)
{
    SECStatus rv = SECFailure;
    int len;
    unsigned char *kBytes= NULL;

    if (!key) {
        PORT_SetError(SEC_ERROR_INVALID_ARGS);
        return SECFailure;
    }

    /* Generate random value k */
    len = key->ecParams.order.len;
    kBytes = ec_GenerateRandomPrivateKey(key->ecParams.order.data, len,
        random, randomLen, kmflag);
    if (kBytes == NULL) goto cleanup;

    /* Generate ECDSA signature with the specified k value */
    rv = ECDSA_SignDigestWithSeed(key, signature, digest, kBytes, len, kmflag);

cleanup:
    if (kBytes) {
        PORT_ZFree(kBytes, len * 2);
    }

#if EC_DEBUG
    printf("ECDSA signing %s\n",
        (rv == SECSuccess) ? "succeeded" : "failed");
#endif

    return rv;
}

/*
** Checks the signature on the given digest using the key provided.
*/
SECStatus
ECDSA_VerifyDigest(ECPublicKey *key, const SECItem *signature,
                 const SECItem *digest, int kmflag)
{
    SECStatus rv = SECFailure;
    mp_int r_, s_;           /* tuple (r', s') is received signature) */
    mp_int c, u1, u2, v;     /* intermediate values used in verification */
    mp_int x1;
    mp_int n;
    mp_err err = MP_OKAY;
    ECParams *ecParams = NULL;
    SECItem pointC = { siBuffer, NULL, 0 };
    int slen;       /* length in bytes of a half signature (r or s) */
    int flen;       /* length in bytes of the field size */
    unsigned olen;  /* length in bytes of the base point order */

#if EC_DEBUG
    char mpstr[256];
    printf("ECDSA verification called\n");
#endif

    /* Initialize MPI integers. */
    /* must happen before the first potential call to cleanup */
    MP_DIGITS(&r_) = 0;
    MP_DIGITS(&s_) = 0;
    MP_DIGITS(&c) = 0;
    MP_DIGITS(&u1) = 0;
    MP_DIGITS(&u2) = 0;
    MP_DIGITS(&x1) = 0;
    MP_DIGITS(&v)  = 0;
    MP_DIGITS(&n)  = 0;

    /* Check args */
    if (!key || !signature || !digest) {
        PORT_SetError(SEC_ERROR_INVALID_ARGS);
        goto cleanup;
    }

    ecParams = &(key->ecParams);
    flen = (ecParams->fieldID.size + 7) >> 3;
    olen = ecParams->order.len;
    if (signature->len == 0 || signature->len%2 != 0 ||
        signature->len > 2*olen) {
        PORT_SetError(SEC_ERROR_INPUT_LEN);
        goto cleanup;
    }
    slen = signature->len/2;

    SECITEM_AllocItem(NULL, &pointC, 2*flen + 1, kmflag);
    if (pointC.data == NULL)
        goto cleanup;

    CHECK_MPI_OK( mp_init(&r_, kmflag) );
    CHECK_MPI_OK( mp_init(&s_, kmflag) );
    CHECK_MPI_OK( mp_init(&c, kmflag)  );
    CHECK_MPI_OK( mp_init(&u1, kmflag) );
    CHECK_MPI_OK( mp_init(&u2, kmflag) );
    CHECK_MPI_OK( mp_init(&x1, kmflag)  );
    CHECK_MPI_OK( mp_init(&v, kmflag)  );
    CHECK_MPI_OK( mp_init(&n, kmflag)  );

    /*
    ** Convert received signature (r', s') into MPI integers.
    */
    CHECK_MPI_OK( mp_read_unsigned_octets(&r_, signature->data, slen) );
    CHECK_MPI_OK( mp_read_unsigned_octets(&s_, signature->data + slen, slen) );

    /*
    ** ANSI X9.62, Section 5.4.2, Steps 1 and 2
    **
    ** Verify that 0 < r' < n and 0 < s' < n
    */
    SECITEM_TO_MPINT(ecParams->order, &n);
    if (mp_cmp_z(&r_) <= 0 || mp_cmp_z(&s_) <= 0 ||
        mp_cmp(&r_, &n) >= 0 || mp_cmp(&s_, &n) >= 0) {
        PORT_SetError(SEC_ERROR_BAD_SIGNATURE);
        goto cleanup; /* will return rv == SECFailure */
    }

    /*
    ** ANSI X9.62, Section 5.4.2, Step 3
    **
    ** c = (s')**-1 mod n
    */
    CHECK_MPI_OK( mp_invmod(&s_, &n, &c) );      /* c = (s')**-1 mod n */

    /*
    ** ANSI X9.62, Section 5.4.2, Step 4
    **
    ** u1 = ((HASH(M')) * c) mod n
    */
    SECITEM_TO_MPINT(*digest, &u1);                  /* u1 = HASH(M)     */

    /* In the definition of EC signing, digests are truncated
     * to the length of n in bits.
     * (see SEC 1 "Elliptic Curve Digit Signature Algorithm" section 4.1.*/
    /* u1 = HASH(M')     */
    if (digest->len*8 > (unsigned int)ecParams->fieldID.size) {
        mpl_rsh(&u1,&u1,digest->len*8- ecParams->fieldID.size);
    }

#if EC_DEBUG
    mp_todecimal(&r_, mpstr);
    printf("r_: %s (dec)\n", mpstr);
    mp_todecimal(&s_, mpstr);
    printf("s_: %s (dec)\n", mpstr);
    mp_todecimal(&c, mpstr);
    printf("c : %s (dec)\n", mpstr);
    mp_todecimal(&u1, mpstr);
    printf("digest: %s (dec)\n", mpstr);
#endif

    CHECK_MPI_OK( mp_mulmod(&u1, &c, &n, &u1) );  /* u1 = u1 * c mod n */

    /*
    ** ANSI X9.62, Section 5.4.2, Step 4
    **
    ** u2 = ((r') * c) mod n
    */
    CHECK_MPI_OK( mp_mulmod(&r_, &c, &n, &u2) );

    /*
    ** ANSI X9.62, Section 5.4.3, Step 1
    **
    ** Compute u1*G + u2*Q
    ** Here, A = u1.G     B = u2.Q    and   C = A + B
    ** If the result, C, is the point at infinity, reject the signature
    */
    if (ec_points_mul(ecParams, &u1, &u2, &key->publicValue, &pointC, kmflag)
        != SECSuccess) {
        rv = SECFailure;
        goto cleanup;
    }
    if (ec_point_at_infinity(&pointC)) {
        PORT_SetError(SEC_ERROR_BAD_SIGNATURE);
        rv = SECFailure;
        goto cleanup;
    }

    CHECK_MPI_OK( mp_read_unsigned_octets(&x1, pointC.data + 1, flen) );

    /*
    ** ANSI X9.62, Section 5.4.4, Step 2
    **
    ** v = x1 mod n
    */
    CHECK_MPI_OK( mp_mod(&x1, &n, &v) );

#if EC_DEBUG
    mp_todecimal(&r_, mpstr);
    printf("r_: %s (dec)\n", mpstr);
    mp_todecimal(&v, mpstr);
    printf("v : %s (dec)\n", mpstr);
#endif

    /*
    ** ANSI X9.62, Section 5.4.4, Step 3
    **
    ** Verification:  v == r'
    */
    if (mp_cmp(&v, &r_)) {
        PORT_SetError(SEC_ERROR_BAD_SIGNATURE);
        rv = SECFailure; /* Signature failed to verify. */
    } else {
        rv = SECSuccess; /* Signature verified. */
    }

#if EC_DEBUG
    mp_todecimal(&u1, mpstr);
    printf("u1: %s (dec)\n", mpstr);
    mp_todecimal(&u2, mpstr);
    printf("u2: %s (dec)\n", mpstr);
    mp_tohex(&x1, mpstr);
    printf("x1: %s\n", mpstr);
    mp_todecimal(&v, mpstr);
    printf("v : %s (dec)\n", mpstr);
#endif

cleanup:
    mp_clear(&r_);
    mp_clear(&s_);
    mp_clear(&c);
    mp_clear(&u1);
    mp_clear(&u2);
    mp_clear(&x1);
    mp_clear(&v);
    mp_clear(&n);

    if (pointC.data) SECITEM_FreeItem(&pointC, PR_FALSE);
    if (err) {
        MP_TO_SEC_ERROR(err);
        rv = SECFailure;
    }

#if EC_DEBUG
    printf("ECDSA verification %s\n",
        (rv == SECSuccess) ? "succeeded" : "failed");
#endif

    return rv;
}
