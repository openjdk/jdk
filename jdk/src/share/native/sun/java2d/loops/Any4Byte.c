/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <string.h>

#include "Any4Byte.h"

#include "AlphaMath.h"
#include "IntDcm.h"

/*
 * This file declares, registers, and defines the various graphics
 * primitive loops to manipulate surfaces of type "Any4Byte".
 *
 * See also LoopMacros.h
 */

RegisterFunc RegisterAny4Byte;

DECLARE_SOLID_FILLRECT(Any4Byte);
DECLARE_SOLID_FILLSPANS(Any4Byte);
DECLARE_SOLID_DRAWLINE(Any4Byte);
DECLARE_XOR_FILLRECT(Any4Byte);
DECLARE_XOR_FILLSPANS(Any4Byte);
DECLARE_XOR_DRAWLINE(Any4Byte);
DECLARE_SOLID_DRAWGLYPHLIST(Any4Byte);
DECLARE_XOR_DRAWGLYPHLIST(Any4Byte);

NativePrimitive Any4BytePrimitives[] = {
    REGISTER_SOLID_FILLRECT(Any4Byte),
    REGISTER_SOLID_FILLSPANS(Any4Byte),
    REGISTER_SOLID_LINE_PRIMITIVES(Any4Byte),
    REGISTER_XOR_FILLRECT(Any4Byte),
    REGISTER_XOR_FILLSPANS(Any4Byte),
    REGISTER_XOR_LINE_PRIMITIVES(Any4Byte),
    REGISTER_SOLID_DRAWGLYPHLIST(Any4Byte),
    REGISTER_XOR_DRAWGLYPHLIST(Any4Byte),
};

jboolean RegisterAny4Byte(JNIEnv *env)
{
    return RegisterPrimitives(env, Any4BytePrimitives,
                              ArraySize(Any4BytePrimitives));
}

DEFINE_ISOCOPY_BLIT(Any4Byte)

DEFINE_ISOSCALE_BLIT(Any4Byte)

DEFINE_ISOXOR_BLIT(Any4Byte)

DEFINE_SOLID_FILLRECT(Any4Byte)

DEFINE_SOLID_FILLSPANS(Any4Byte)

DEFINE_SOLID_DRAWLINE(Any4Byte)

DEFINE_XOR_FILLRECT(Any4Byte)

DEFINE_XOR_FILLSPANS(Any4Byte)

DEFINE_XOR_DRAWLINE(Any4Byte)

DEFINE_SOLID_DRAWGLYPHLIST(Any4Byte)

DEFINE_XOR_DRAWGLYPHLIST(Any4Byte)
