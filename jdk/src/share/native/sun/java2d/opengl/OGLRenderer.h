/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef OGLRenderer_h_Included
#define OGLRenderer_h_Included

#include "sun_java2d_pipe_BufferedRenderPipe.h"
#include "OGLContext.h"

#define BYTES_PER_POLY_POINT \
    sun_java2d_pipe_BufferedRenderPipe_BYTES_PER_POLY_POINT
#define BYTES_PER_SCANLINE \
    sun_java2d_pipe_BufferedRenderPipe_BYTES_PER_SCANLINE
#define BYTES_PER_SPAN \
    sun_java2d_pipe_BufferedRenderPipe_BYTES_PER_SPAN

void OGLRenderer_DrawLine(OGLContext *oglc,
                          jint x1, jint y1, jint x2, jint y2);
void OGLRenderer_DrawRect(OGLContext *oglc,
                          jint x, jint y, jint w, jint h);
void OGLRenderer_DrawPoly(OGLContext *oglc,
                          jint nPoints, jint isClosed,
                          jint transX, jint transY,
                          jint *xPoints, jint *yPoints);
void OGLRenderer_DrawScanlines(OGLContext *oglc,
                               jint count, jint *scanlines);
void OGLRenderer_FillRect(OGLContext *oglc,
                          jint x, jint y, jint w, jint h);
void OGLRenderer_FillSpans(OGLContext *oglc,
                           jint count, jint *spans);

#endif /* OGLRenderer_h_Included */
