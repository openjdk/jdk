/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#if !defined(_DASSERT_H)
#define _DASSERT_H

#if defined(__cplusplus)
extern "C" {
#endif

#include "debug_util.h"

#if defined(DEBUG)

#define DASSERT(_expr) \
        if ( !(_expr) ) { \
            DAssert_Impl( #_expr, __FILE__, __LINE__); \
        } else { \
        }

#define DASSERTMSG(_expr, _msg) \
        if ( !(_expr) ) { \
            DAssert_Impl( (_msg), __FILE__, __LINE__); \
        } else { \
        }

/* prototype for assert function */
typedef void (*DASSERT_CALLBACK)(const char * msg, const char * file, int line);

extern void DAssert_Impl(const char * msg, const char * file, int line);
extern void DAssert_SetCallback( DASSERT_CALLBACK pfn );

#else /* DEBUG not defined */

#define DASSERT(_expr)
#define DASSERTMSG(_expr, _msg)

#endif /* if defined(DEBUG) */

#if defined(__cplusplus)
} /* extern "C" */
#endif

#endif /* _DASSERT_H */
