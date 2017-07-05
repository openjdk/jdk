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

#include "AnyShort.h"

/*
 * This file declares, registers, and defines the various graphics
 * primitive loops to manipulate surfaces of type "AnyShort".
 *
 * See also LoopMacros.h
 */

RegisterFunc RegisterAnyShort;

DECLARE_SOLID_FILLRECT(AnyShort);
DECLARE_SOLID_FILLSPANS(AnyShort);
DECLARE_SOLID_DRAWLINE(AnyShort);
DECLARE_XOR_FILLRECT(AnyShort);
DECLARE_XOR_FILLSPANS(AnyShort);
DECLARE_XOR_DRAWLINE(AnyShort);
DECLARE_SOLID_DRAWGLYPHLIST(AnyShort);
DECLARE_XOR_DRAWGLYPHLIST(AnyShort);

NativePrimitive AnyShortPrimitives[] = {
    REGISTER_SOLID_FILLRECT(AnyShort),
    REGISTER_SOLID_FILLSPANS(AnyShort),
    REGISTER_SOLID_LINE_PRIMITIVES(AnyShort),
    REGISTER_XOR_FILLRECT(AnyShort),
    REGISTER_XOR_FILLSPANS(AnyShort),
    REGISTER_XOR_LINE_PRIMITIVES(AnyShort),
    REGISTER_SOLID_DRAWGLYPHLIST(AnyShort),
    REGISTER_XOR_DRAWGLYPHLIST(AnyShort),
};

jboolean RegisterAnyShort(JNIEnv *env)
{
    return RegisterPrimitives(env, AnyShortPrimitives,
                              ArraySize(AnyShortPrimitives));
}

DEFINE_ISOCOPY_BLIT(AnyShort)

DEFINE_ISOSCALE_BLIT(AnyShort)

DEFINE_ISOXOR_BLIT(AnyShort)

DEFINE_SOLID_FILLRECT(AnyShort)

DEFINE_SOLID_FILLSPANS(AnyShort)

DEFINE_SOLID_DRAWLINE(AnyShort)

DEFINE_XOR_FILLRECT(AnyShort)

DEFINE_XOR_FILLSPANS(AnyShort)

DEFINE_XOR_DRAWLINE(AnyShort)

DEFINE_SOLID_DRAWGLYPHLIST(AnyShort)

DEFINE_XOR_DRAWGLYPHLIST(AnyShort)
