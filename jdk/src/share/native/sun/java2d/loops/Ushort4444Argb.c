/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "AnyShort.h"
#include "Ushort4444Argb.h"
#include "AlphaMacros.h"

#include "IntArgb.h"
#include "IntArgbBm.h"
#include "IntRgb.h"
#include "ThreeByteBgr.h"
#include "ByteGray.h"
#include "ByteIndexed.h"

/*
 * This file declares, registers, and defines the various graphics
 * primitive loops to manipulate surfaces of type "Ushort4444Argb".
 *
 * See also LoopMacros.h
 */

RegisterFunc RegisterUshort4444Argb;

DECLARE_SRCOVER_MASKBLIT(IntArgb, Ushort4444Argb);

NativePrimitive Ushort4444ArgbPrimitives[] = {
    REGISTER_ANYSHORT_ISOCOPY_BLIT(Ushort4444Argb),
    REGISTER_SRCOVER_MASKBLIT(IntArgb, Ushort4444Argb),
};

jboolean RegisterUshort4444Argb(JNIEnv *env)
{
    return RegisterPrimitives(env, Ushort4444ArgbPrimitives,
                              ArraySize(Ushort4444ArgbPrimitives));
}

jint PixelForUshort4444Argb(SurfaceDataRasInfo *pRasInfo, jint rgb)
{
    return IntArgbToUshort4444Argb(rgb);
}

DEFINE_SRCOVER_MASKBLIT(IntArgb, Ushort4444Argb, 4ByteArgb)
