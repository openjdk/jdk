/*
 * Copyright 2004-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef OGLBlitLoops_h_Included
#define OGLBlitLoops_h_Included

#include "sun_java2d_opengl_OGLBlitLoops.h"
#include "OGLSurfaceData.h"
#include "OGLContext.h"

#define OFFSET_SRCTYPE sun_java2d_opengl_OGLBlitLoops_OFFSET_SRCTYPE
#define OFFSET_HINT    sun_java2d_opengl_OGLBlitLoops_OFFSET_HINT
#define OFFSET_TEXTURE sun_java2d_opengl_OGLBlitLoops_OFFSET_TEXTURE
#define OFFSET_RTT     sun_java2d_opengl_OGLBlitLoops_OFFSET_RTT
#define OFFSET_XFORM   sun_java2d_opengl_OGLBlitLoops_OFFSET_XFORM
#define OFFSET_ISOBLIT sun_java2d_opengl_OGLBlitLoops_OFFSET_ISOBLIT

void OGLBlitLoops_IsoBlit(JNIEnv *env,
                          OGLContext *oglc, jlong pSrcOps, jlong pDstOps,
                          jboolean xform, jint hint,
                          jboolean texture, jboolean rtt,
                          jint sx1, jint sy1,
                          jint sx2, jint sy2,
                          jdouble dx1, jdouble dy1,
                          jdouble dx2, jdouble dy2);

void OGLBlitLoops_Blit(JNIEnv *env,
                       OGLContext *oglc, jlong pSrcOps, jlong pDstOps,
                       jboolean xform, jint hint,
                       jint srctype, jboolean texture,
                       jint sx1, jint sy1,
                       jint sx2, jint sy2,
                       jdouble dx1, jdouble dy1,
                       jdouble dx2, jdouble dy2);

void OGLBlitLoops_SurfaceToSwBlit(JNIEnv *env, OGLContext *oglc,
                                  jlong pSrcOps, jlong pDstOps, jint dsttype,
                                  jint srcx, jint srcy,
                                  jint dstx, jint dsty,
                                  jint width, jint height);

void OGLBlitLoops_CopyArea(JNIEnv *env,
                           OGLContext *oglc, OGLSDOps *dstOps,
                           jint x, jint y,
                           jint width, jint height,
                           jint dx, jint dy);

#endif /* OGLBlitLoops_h_Included */
