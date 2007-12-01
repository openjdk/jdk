/*
 * Copyright 1996 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * This file contains macro definitions for the Storing category of
 * the macros used by the generic scaleloop function.
 *
 * This implementation can store 24-bit pixels into an array of bytes
 * as three consecutive bytes such that the pixel for (srcX, srcY) is
 * stored at indices (srcOff + srcY * srcScan + srcX * 3 + C) in the
 * array, where C == 0 for the blue component, 1 for the green component,
 * and 2 for the red component.
 */

#define DeclareOutputVars                               \
    pixptr dstP;

#define InitOutput(cvdata, clrdata, dstX, dstY)                 \
    do {                                                        \
        img_check(clrdata->bitsperpixel == 24);                 \
        dstP.vp = cvdata->outbuf;                               \
        dstP.bp += dstY * ScanBytes(cvdata) + dstX * 3;         \
    } while (0)

#define PutPixelInc(pixel, red, green, blue)                    \
    do {                                                        \
        *dstP.bp++ = blue;                                      \
        *dstP.bp++ = green;                                     \
        *dstP.bp++ = red;                                       \
    } while (0)

#define EndOutputRow(cvdata, dstY, dstX1, dstX2)                \
    do {                                                        \
        SendRow(cvdata, dstY, dstX1, dstX2);                    \
        dstP.bp += ScanBytes(cvdata) - (dstX2 - dstX1) * 3;     \
    } while (0)

#define EndOutputRect(cvdata, dstX1, dstY1, dstX2, dstY2)       \
    SendBuffer(cvdata, dstX1, dstY1, dstX2, dstY2)
