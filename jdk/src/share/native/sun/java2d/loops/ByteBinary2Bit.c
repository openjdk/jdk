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

#include "ByteBinary2Bit.h"

#include "IntArgb.h"

/*
 * This file declares, registers, and defines the various graphics
 * primitive loops to manipulate surfaces of type "ByteBinary2Bit".
 *
 * See also LoopMacros.h
 */

RegisterFunc RegisterByteBinary2Bit;

DECLARE_SOLID_FILLRECT(ByteBinary2Bit);
DECLARE_SOLID_FILLSPANS(ByteBinary2Bit);
DECLARE_SOLID_DRAWLINE(ByteBinary2Bit);
DECLARE_XOR_FILLRECT(ByteBinary2Bit);
DECLARE_XOR_FILLSPANS(ByteBinary2Bit);
DECLARE_XOR_DRAWLINE(ByteBinary2Bit);
DECLARE_SOLID_DRAWGLYPHLIST(ByteBinary2Bit);
DECLARE_SOLID_DRAWGLYPHLISTAA(ByteBinary2Bit);
DECLARE_XOR_DRAWGLYPHLIST(ByteBinary2Bit);

DECLARE_CONVERT_BLIT(ByteBinary2Bit, ByteBinary2Bit);
DECLARE_CONVERT_BLIT(ByteBinary2Bit, IntArgb);
DECLARE_CONVERT_BLIT(IntArgb, ByteBinary2Bit);
DECLARE_XOR_BLIT(IntArgb, ByteBinary2Bit);

DECLARE_ALPHA_MASKBLIT(ByteBinary2Bit, IntArgb);
DECLARE_ALPHA_MASKBLIT(IntArgb, ByteBinary2Bit);
DECLARE_ALPHA_MASKFILL(ByteBinary2Bit);

NativePrimitive ByteBinary2BitPrimitives[] = {
    REGISTER_SOLID_FILLRECT(ByteBinary2Bit),
    REGISTER_SOLID_FILLSPANS(ByteBinary2Bit),
    REGISTER_SOLID_LINE_PRIMITIVES(ByteBinary2Bit),
    REGISTER_XOR_FILLRECT(ByteBinary2Bit),
    REGISTER_XOR_FILLSPANS(ByteBinary2Bit),
    REGISTER_XOR_LINE_PRIMITIVES(ByteBinary2Bit),
    REGISTER_SOLID_DRAWGLYPHLIST(ByteBinary2Bit),
    REGISTER_SOLID_DRAWGLYPHLISTAA(ByteBinary2Bit),
    REGISTER_XOR_DRAWGLYPHLIST(ByteBinary2Bit),

    REGISTER_CONVERT_BLIT(ByteBinary2Bit, ByteBinary2Bit),
    REGISTER_CONVERT_BLIT(ByteBinary2Bit, IntArgb),
    REGISTER_CONVERT_BLIT(IntArgb, ByteBinary2Bit),
    REGISTER_XOR_BLIT(IntArgb, ByteBinary2Bit),

    REGISTER_ALPHA_MASKBLIT(ByteBinary2Bit, IntArgb),
    REGISTER_ALPHA_MASKBLIT(IntArgb, ByteBinary2Bit),
    REGISTER_ALPHA_MASKFILL(ByteBinary2Bit),
};

jboolean RegisterByteBinary2Bit(JNIEnv *env)
{
    return RegisterPrimitives(env, ByteBinary2BitPrimitives,
                              ArraySize(ByteBinary2BitPrimitives));
}

DEFINE_BYTE_BINARY_SOLID_FILLRECT(ByteBinary2Bit)

DEFINE_BYTE_BINARY_SOLID_FILLSPANS(ByteBinary2Bit)

DEFINE_BYTE_BINARY_SOLID_DRAWLINE(ByteBinary2Bit)

DEFINE_BYTE_BINARY_XOR_FILLRECT(ByteBinary2Bit)

DEFINE_BYTE_BINARY_XOR_FILLSPANS(ByteBinary2Bit)

DEFINE_BYTE_BINARY_XOR_DRAWLINE(ByteBinary2Bit)

DEFINE_BYTE_BINARY_SOLID_DRAWGLYPHLIST(ByteBinary2Bit)

DEFINE_BYTE_BINARY_SOLID_DRAWGLYPHLISTAA(ByteBinary2Bit, 3ByteRgb)

DEFINE_BYTE_BINARY_XOR_DRAWGLYPHLIST(ByteBinary2Bit)

DEFINE_BYTE_BINARY_CONVERT_BLIT(ByteBinary2Bit, ByteBinary2Bit, 1IntRgb)

DEFINE_BYTE_BINARY_CONVERT_BLIT(ByteBinary2Bit, IntArgb, 1IntArgb)

DEFINE_BYTE_BINARY_CONVERT_BLIT(IntArgb, ByteBinary2Bit, 1IntRgb)

DEFINE_BYTE_BINARY_XOR_BLIT(IntArgb, ByteBinary2Bit)

DEFINE_BYTE_BINARY_ALPHA_MASKBLIT(ByteBinary2Bit, IntArgb, 4ByteArgb)

DEFINE_BYTE_BINARY_ALPHA_MASKBLIT(IntArgb, ByteBinary2Bit, 4ByteArgb)

DEFINE_BYTE_BINARY_ALPHA_MASKFILL(ByteBinary2Bit, 4ByteArgb)
