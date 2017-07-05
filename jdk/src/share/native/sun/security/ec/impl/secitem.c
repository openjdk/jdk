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
 *
 *********************************************************************** */

/*
 * Support routines for SECItem data structure.
 *
 * $Id: secitem.c,v 1.14 2006/05/22 22:24:34 wtchang%redhat.com Exp $
 */

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

#ifndef _WIN32
#include <strings.h>
#endif /* _WIN32 */

#include <assert.h>
#endif
#include "ec.h"
#include "ecl-curve.h"
#include "ecc_impl.h"

void SECITEM_FreeItem(SECItem *, PRBool);

SECItem *
SECITEM_AllocItem(PRArenaPool *arena, SECItem *item, unsigned int len,
    int kmflag)
{
    SECItem *result = NULL;
    void *mark = NULL;

    if (arena != NULL) {
        mark = PORT_ArenaMark(arena);
    }

    if (item == NULL) {
        if (arena != NULL) {
            result = PORT_ArenaZAlloc(arena, sizeof(SECItem), kmflag);
        } else {
            result = PORT_ZAlloc(sizeof(SECItem), kmflag);
        }
        if (result == NULL) {
            goto loser;
        }
    } else {
        PORT_Assert(item->data == NULL);
        result = item;
    }

    result->len = len;
    if (len) {
        if (arena != NULL) {
            result->data = PORT_ArenaAlloc(arena, len, kmflag);
        } else {
            result->data = PORT_Alloc(len, kmflag);
        }
        if (result->data == NULL) {
            goto loser;
        }
    } else {
        result->data = NULL;
    }

    if (mark) {
        PORT_ArenaUnmark(arena, mark);
    }
    return(result);

loser:
    if ( arena != NULL ) {
        if (mark) {
            PORT_ArenaRelease(arena, mark);
        }
        if (item != NULL) {
            item->data = NULL;
            item->len = 0;
        }
    } else {
        if (result != NULL) {
            SECITEM_FreeItem(result, (item == NULL) ? PR_TRUE : PR_FALSE);
        }
        /*
         * If item is not NULL, the above has set item->data and
         * item->len to 0.
         */
    }
    return(NULL);
}

SECStatus
SECITEM_CopyItem(PRArenaPool *arena, SECItem *to, const SECItem *from,
   int kmflag)
{
    to->type = from->type;
    if (from->data && from->len) {
        if ( arena ) {
            to->data = (unsigned char*) PORT_ArenaAlloc(arena, from->len,
                kmflag);
        } else {
            to->data = (unsigned char*) PORT_Alloc(from->len, kmflag);
        }

        if (!to->data) {
            return SECFailure;
        }
        PORT_Memcpy(to->data, from->data, from->len);
        to->len = from->len;
    } else {
        to->data = 0;
        to->len = 0;
    }
    return SECSuccess;
}

void
SECITEM_FreeItem(SECItem *zap, PRBool freeit)
{
    if (zap) {
#ifdef _KERNEL
        kmem_free(zap->data, zap->len);
#else
        free(zap->data);
#endif
        zap->data = 0;
        zap->len = 0;
        if (freeit) {
#ifdef _KERNEL
            kmem_free(zap, sizeof (SECItem));
#else
            free(zap);
#endif
        }
    }
}
