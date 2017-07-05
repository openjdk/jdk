/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "GraphicsPrimitiveMgr.h"

#include "sun_java2d_loops_MaskFill.h"

/*
 * Class:     sun_java2d_loops_MaskFill
 * Method:    MaskFill
 * Signature: (Lsun/java2d/SunGraphics2D;Lsun/java2d/SurfaceData;Ljava/awt/Composite;IIII[BII)V
 */
JNIEXPORT void JNICALL
Java_sun_java2d_loops_MaskFill_MaskFill
    (JNIEnv *env, jobject self,
     jobject sg2d, jobject sData, jobject comp,
     jint x, jint y, jint w, jint h,
     jbyteArray maskArray, jint maskoff, jint maskscan)
{
    SurfaceDataOps *sdOps;
    SurfaceDataRasInfo rasInfo;
    NativePrimitive *pPrim;
    CompositeInfo compInfo;

    pPrim = GetNativePrim(env, self);
    if (pPrim == NULL) {
        return;
    }
    if (pPrim->pCompType->getCompInfo != NULL) {
        (*pPrim->pCompType->getCompInfo)(env, &compInfo, comp);
    }

    sdOps = SurfaceData_GetOps(env, sData);
    if (sdOps == 0) {
        return;
    }

    rasInfo.bounds.x1 = x;
    rasInfo.bounds.y1 = y;
    rasInfo.bounds.x2 = x + w;
    rasInfo.bounds.y2 = y + h;
    if (sdOps->Lock(env, sdOps, &rasInfo, pPrim->dstflags) != SD_SUCCESS) {
        return;
    }

    if (rasInfo.bounds.x2 > rasInfo.bounds.x1 &&
        rasInfo.bounds.y2 > rasInfo.bounds.y1)
    {
        jint color = GrPrim_Sg2dGetEaRGB(env, sg2d);
        sdOps->GetRasInfo(env, sdOps, &rasInfo);
        if (rasInfo.rasBase) {
            jint width = rasInfo.bounds.x2 - rasInfo.bounds.x1;
            jint height = rasInfo.bounds.y2 - rasInfo.bounds.y1;
            void *pDst = PtrCoord(rasInfo.rasBase,
                                  rasInfo.bounds.x1, rasInfo.pixelStride,
                                  rasInfo.bounds.y1, rasInfo.scanStride);
            unsigned char *pMask =
                (maskArray
                 ? (*env)->GetPrimitiveArrayCritical(env, maskArray, 0)
                 : 0);
            maskoff += ((rasInfo.bounds.y1 - y) * maskscan +
                        (rasInfo.bounds.x1 - x));
            (*pPrim->funcs.maskfill)(pDst,
                                     pMask, maskoff, maskscan,
                                     width, height,
                                     color, &rasInfo,
                                     pPrim, &compInfo);
            if (pMask) {
                (*env)->ReleasePrimitiveArrayCritical(env, maskArray,
                                                      pMask, JNI_ABORT);
            }
        }
        SurfaceData_InvokeRelease(env, sdOps, &rasInfo);
    }
    SurfaceData_InvokeUnlock(env, sdOps, &rasInfo);
}
