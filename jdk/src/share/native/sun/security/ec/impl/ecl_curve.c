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
 * The Original Code is the elliptic curve math library.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
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

#include "ecl.h"
#include "ecl-curve.h"
#include "ecl-priv.h"
#ifndef _KERNEL
#include <stdlib.h>
#include <string.h>
#endif

#define CHECK(func) if ((func) == NULL) { res = 0; goto CLEANUP; }

/* Duplicates an ECCurveParams */
ECCurveParams *
ECCurveParams_dup(const ECCurveParams * params, int kmflag)
{
        int res = 1;
        ECCurveParams *ret = NULL;

#ifdef _KERNEL
        ret = (ECCurveParams *) kmem_zalloc(sizeof(ECCurveParams), kmflag);
#else
        CHECK(ret = (ECCurveParams *) calloc(1, sizeof(ECCurveParams)));
#endif
        if (params->text != NULL) {
#ifdef _KERNEL
                ret->text = kmem_alloc(strlen(params->text) + 1, kmflag);
                bcopy(params->text, ret->text, strlen(params->text) + 1);
#else
                CHECK(ret->text = strdup(params->text));
#endif
        }
        ret->field = params->field;
        ret->size = params->size;
        if (params->irr != NULL) {
#ifdef _KERNEL
                ret->irr = kmem_alloc(strlen(params->irr) + 1, kmflag);
                bcopy(params->irr, ret->irr, strlen(params->irr) + 1);
#else
                CHECK(ret->irr = strdup(params->irr));
#endif
        }
        if (params->curvea != NULL) {
#ifdef _KERNEL
                ret->curvea = kmem_alloc(strlen(params->curvea) + 1, kmflag);
                bcopy(params->curvea, ret->curvea, strlen(params->curvea) + 1);
#else
                CHECK(ret->curvea = strdup(params->curvea));
#endif
        }
        if (params->curveb != NULL) {
#ifdef _KERNEL
                ret->curveb = kmem_alloc(strlen(params->curveb) + 1, kmflag);
                bcopy(params->curveb, ret->curveb, strlen(params->curveb) + 1);
#else
                CHECK(ret->curveb = strdup(params->curveb));
#endif
        }
        if (params->genx != NULL) {
#ifdef _KERNEL
                ret->genx = kmem_alloc(strlen(params->genx) + 1, kmflag);
                bcopy(params->genx, ret->genx, strlen(params->genx) + 1);
#else
                CHECK(ret->genx = strdup(params->genx));
#endif
        }
        if (params->geny != NULL) {
#ifdef _KERNEL
                ret->geny = kmem_alloc(strlen(params->geny) + 1, kmflag);
                bcopy(params->geny, ret->geny, strlen(params->geny) + 1);
#else
                CHECK(ret->geny = strdup(params->geny));
#endif
        }
        if (params->order != NULL) {
#ifdef _KERNEL
                ret->order = kmem_alloc(strlen(params->order) + 1, kmflag);
                bcopy(params->order, ret->order, strlen(params->order) + 1);
#else
                CHECK(ret->order = strdup(params->order));
#endif
        }
        ret->cofactor = params->cofactor;

  CLEANUP:
        if (res != 1) {
                EC_FreeCurveParams(ret);
                return NULL;
        }
        return ret;
}

#undef CHECK

/* Construct ECCurveParams from an ECCurveName */
ECCurveParams *
EC_GetNamedCurveParams(const ECCurveName name, int kmflag)
{
        if ((name <= ECCurve_noName) || (ECCurve_pastLastCurve <= name) ||
                                        (ecCurve_map[name] == NULL)) {
                return NULL;
        } else {
                return ECCurveParams_dup(ecCurve_map[name], kmflag);
        }
}

/* Free the memory allocated (if any) to an ECCurveParams object. */
void
EC_FreeCurveParams(ECCurveParams * params)
{
        if (params == NULL)
                return;
        if (params->text != NULL)
#ifdef _KERNEL
                kmem_free(params->text, strlen(params->text) + 1);
#else
                free(params->text);
#endif
        if (params->irr != NULL)
#ifdef _KERNEL
                kmem_free(params->irr, strlen(params->irr) + 1);
#else
                free(params->irr);
#endif
        if (params->curvea != NULL)
#ifdef _KERNEL
                kmem_free(params->curvea, strlen(params->curvea) + 1);
#else
                free(params->curvea);
#endif
        if (params->curveb != NULL)
#ifdef _KERNEL
                kmem_free(params->curveb, strlen(params->curveb) + 1);
#else
                free(params->curveb);
#endif
        if (params->genx != NULL)
#ifdef _KERNEL
                kmem_free(params->genx, strlen(params->genx) + 1);
#else
                free(params->genx);
#endif
        if (params->geny != NULL)
#ifdef _KERNEL
                kmem_free(params->geny, strlen(params->geny) + 1);
#else
                free(params->geny);
#endif
        if (params->order != NULL)
#ifdef _KERNEL
                kmem_free(params->order, strlen(params->order) + 1);
#else
                free(params->order);
#endif
#ifdef _KERNEL
        kmem_free(params, sizeof(ECCurveParams));
#else
        free(params);
#endif
}
