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

#include "AnyInt.h"

/*
 * This file declares, registers, and defines the various graphics
 * primitive loops to manipulate surfaces of type "AnyInt".
 *
 * See also LoopMacros.h
 */

RegisterFunc RegisterAnyInt;

DECLARE_SOLID_FILLRECT(AnyInt);
DECLARE_SOLID_FILLSPANS(AnyInt);
DECLARE_SOLID_DRAWLINE(AnyInt);
DECLARE_XOR_FILLRECT(AnyInt);
DECLARE_XOR_FILLSPANS(AnyInt);
DECLARE_XOR_DRAWLINE(AnyInt);
DECLARE_SOLID_DRAWGLYPHLIST(AnyInt);
DECLARE_XOR_DRAWGLYPHLIST(AnyInt);

NativePrimitive AnyIntPrimitives[] = {
    REGISTER_SOLID_FILLRECT(AnyInt),
    REGISTER_SOLID_FILLSPANS(AnyInt),
    REGISTER_SOLID_LINE_PRIMITIVES(AnyInt),
    REGISTER_XOR_FILLRECT(AnyInt),
    REGISTER_XOR_FILLSPANS(AnyInt),
    REGISTER_XOR_LINE_PRIMITIVES(AnyInt),
    REGISTER_SOLID_DRAWGLYPHLIST(AnyInt),
    REGISTER_XOR_DRAWGLYPHLIST(AnyInt),
};

jboolean RegisterAnyInt(JNIEnv *env)
{
    return RegisterPrimitives(env, AnyIntPrimitives,
                              ArraySize(AnyIntPrimitives));
}

DEFINE_ISOCOPY_BLIT(AnyInt)

DEFINE_ISOXOR_BLIT(AnyInt)

DEFINE_ISOSCALE_BLIT(AnyInt)

DEFINE_SOLID_FILLRECT(AnyInt)

DEFINE_SOLID_FILLSPANS(AnyInt)

DEFINE_SOLID_DRAWLINE(AnyInt)

DEFINE_XOR_FILLRECT(AnyInt)

DEFINE_XOR_FILLSPANS(AnyInt)

DEFINE_XOR_DRAWLINE(AnyInt)

DEFINE_SOLID_DRAWGLYPHLIST(AnyInt)

DEFINE_XOR_DRAWGLYPHLIST(AnyInt)
