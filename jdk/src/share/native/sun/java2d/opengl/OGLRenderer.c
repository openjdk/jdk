/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef HEADLESS

#include <jlong.h>
#include <jni_util.h>

#include "sun_java2d_opengl_OGLRenderer.h"

#include "OGLRenderer.h"
#include "OGLRenderQueue.h"
#include "OGLSurfaceData.h"

/**
 * Note: Some of the methods in this file apply a "magic number"
 * translation to line segments.  The OpenGL specification lays out the
 * "diamond exit rule" for line rasterization, but it is loose enough to
 * allow for a wide range of line rendering hardware.  (It appears that
 * some hardware, such as the Nvidia GeForce2 series, does not even meet
 * the spec in all cases.)  As such it is difficult to find a mapping
 * between the Java2D and OpenGL line specs that works consistently across
 * all hardware combinations.
 *
 * Therefore the "magic numbers" you see here have been empirically derived
 * after testing on a variety of graphics hardware in order to find some
 * reasonable middle ground between the two specifications.  The general
 * approach is to apply a fractional translation to vertices so that they
 * hit pixel centers and therefore touch the same pixels as in our other
 * pipelines.  Emphasis was placed on finding values so that OGL lines with
 * a slope of +/- 1 hit all the same pixels as our other (software) loops.
 * The stepping in other diagonal lines rendered with OGL may deviate
 * slightly from those rendered with our software loops, but the most
 * important thing is that these magic numbers ensure that all OGL lines
 * hit the same endpoints as our software loops.
 *
 * If you find it necessary to change any of these magic numbers in the
 * future, just be sure that you test the changes across a variety of
 * hardware to ensure consistent rendering everywhere.
 */

void
OGLRenderer_DrawLine(OGLContext *oglc, jint x1, jint y1, jint x2, jint y2)
{
    J2dTraceLn(J2D_TRACE_INFO, "OGLRenderer_DrawLine");

    RETURN_IF_NULL(oglc);

    CHECK_PREVIOUS_OP(GL_LINES);

    if (y1 == y2) {
        // horizontal
        GLfloat fx1 = (GLfloat)x1;
        GLfloat fx2 = (GLfloat)x2;
        GLfloat fy  = ((GLfloat)y1) + 0.2f;

        if (x1 > x2) {
            GLfloat t = fx1; fx1 = fx2; fx2 = t;
        }

        j2d_glVertex2f(fx1+0.2f, fy);
        j2d_glVertex2f(fx2+1.2f, fy);
    } else if (x1 == x2) {
        // vertical
        GLfloat fx  = ((GLfloat)x1) + 0.2f;
        GLfloat fy1 = (GLfloat)y1;
        GLfloat fy2 = (GLfloat)y2;

        if (y1 > y2) {
            GLfloat t = fy1; fy1 = fy2; fy2 = t;
        }

        j2d_glVertex2f(fx, fy1+0.2f);
        j2d_glVertex2f(fx, fy2+1.2f);
    } else {
        // diagonal
        GLfloat fx1 = (GLfloat)x1;
        GLfloat fy1 = (GLfloat)y1;
        GLfloat fx2 = (GLfloat)x2;
        GLfloat fy2 = (GLfloat)y2;

        if (x1 < x2) {
            fx1 += 0.2f;
            fx2 += 1.0f;
        } else {
            fx1 += 0.8f;
            fx2 -= 0.2f;
        }

        if (y1 < y2) {
            fy1 += 0.2f;
            fy2 += 1.0f;
        } else {
            fy1 += 0.8f;
            fy2 -= 0.2f;
        }

        j2d_glVertex2f(fx1, fy1);
        j2d_glVertex2f(fx2, fy2);
    }
}

void
OGLRenderer_DrawRect(OGLContext *oglc, jint x, jint y, jint w, jint h)
{
    J2dTraceLn(J2D_TRACE_INFO, "OGLRenderer_DrawRect");

    if (w < 0 || h < 0) {
        return;
    }

    RETURN_IF_NULL(oglc);

    if (w < 2 || h < 2) {
        // If one dimension is less than 2 then there is no
        // gap in the middle - draw a solid filled rectangle.
        CHECK_PREVIOUS_OP(GL_QUADS);
        GLRECT_BODY_XYWH(x, y, w+1, h+1);
    } else {
        GLfloat fx1 = ((GLfloat)x) + 0.2f;
        GLfloat fy1 = ((GLfloat)y) + 0.2f;
        GLfloat fx2 = fx1 + ((GLfloat)w);
        GLfloat fy2 = fy1 + ((GLfloat)h);

        // Avoid drawing the endpoints twice.
        // Also prefer including the endpoints in the
        // horizontal sections which draw pixels faster.

        CHECK_PREVIOUS_OP(GL_LINES);
        // top
        j2d_glVertex2f(fx1,      fy1);
        j2d_glVertex2f(fx2+1.0f, fy1);
        // right
        j2d_glVertex2f(fx2,      fy1+1.0f);
        j2d_glVertex2f(fx2,      fy2);
        // bottom
        j2d_glVertex2f(fx1,      fy2);
        j2d_glVertex2f(fx2+1.0f, fy2);
        // left
        j2d_glVertex2f(fx1,      fy1+1.0f);
        j2d_glVertex2f(fx1,      fy2);
    }
}

void
OGLRenderer_DrawPoly(OGLContext *oglc,
                     jint nPoints, jint isClosed,
                     jint transX, jint transY,
                     jint *xPoints, jint *yPoints)
{
    jboolean isEmpty = JNI_TRUE;
    jint mx, my;
    jint i;

    J2dTraceLn(J2D_TRACE_INFO, "OGLRenderer_DrawPoly");

    if (xPoints == NULL || yPoints == NULL) {
        J2dRlsTraceLn(J2D_TRACE_ERROR,
                      "OGLRenderer_DrawPoly: points array is null");
        return;
    }

    RETURN_IF_NULL(oglc);

    // Note that BufferedRenderPipe.drawPoly() has already rejected polys
    // with nPoints<2, so we can be certain here that we have nPoints>=2.

    mx = xPoints[0];
    my = yPoints[0];

    CHECK_PREVIOUS_OP(GL_LINE_STRIP);
    for (i = 0; i < nPoints; i++) {
        jint x = xPoints[i];
        jint y = yPoints[i];

        isEmpty = isEmpty && (x == mx && y == my);

        // Translate each vertex by a fraction so that we hit pixel centers.
        j2d_glVertex2f((GLfloat)(x + transX) + 0.5f,
                       (GLfloat)(y + transY) + 0.5f);
    }

    if (isClosed && !isEmpty &&
        (xPoints[nPoints-1] != mx ||
         yPoints[nPoints-1] != my))
    {
        // In this case, the polyline's start and end positions are
        // different and need to be closed manually; we do this by adding
        // one more segment back to the starting position.  Note that we
        // do not need to fill in the last pixel (as we do in the following
        // block) because we are returning to the starting pixel, which
        // has already been filled in.
        j2d_glVertex2f((GLfloat)(mx + transX) + 0.5f,
                       (GLfloat)(my + transY) + 0.5f);
        RESET_PREVIOUS_OP(); // so that we don't leave the line strip open
    } else if (!isClosed || isEmpty) {
        // OpenGL omits the last pixel in a polyline, so we fix this by
        // adding a one-pixel segment at the end.  Also, if the polyline
        // never went anywhere (isEmpty is true), we need to use this
        // workaround to ensure that a single pixel is touched.
        CHECK_PREVIOUS_OP(GL_LINES); // this closes the line strip first
        mx = xPoints[nPoints-1] + transX;
        my = yPoints[nPoints-1] + transY;
        j2d_glVertex2i(mx, my);
        j2d_glVertex2i(mx+1, my+1);
        // no need for RESET_PREVIOUS_OP, as the line strip is no longer open
    } else {
        RESET_PREVIOUS_OP(); // so that we don't leave the line strip open
    }
}

JNIEXPORT void JNICALL
Java_sun_java2d_opengl_OGLRenderer_drawPoly
    (JNIEnv *env, jobject oglr,
     jintArray xpointsArray, jintArray ypointsArray,
     jint nPoints, jboolean isClosed,
     jint transX, jint transY)
{
    jint *xPoints, *yPoints;

    J2dTraceLn(J2D_TRACE_INFO, "OGLRenderer_drawPoly");

    xPoints = (jint *)
        (*env)->GetPrimitiveArrayCritical(env, xpointsArray, NULL);
    if (xPoints != NULL) {
        yPoints = (jint *)
            (*env)->GetPrimitiveArrayCritical(env, ypointsArray, NULL);
        if (yPoints != NULL) {
            OGLContext *oglc = OGLRenderQueue_GetCurrentContext();

            OGLRenderer_DrawPoly(oglc,
                                 nPoints, isClosed,
                                 transX, transY,
                                 xPoints, yPoints);

            // 6358147: reset current state, and ensure rendering is
            // flushed to dest
            if (oglc != NULL) {
                RESET_PREVIOUS_OP();
                j2d_glFlush();
            }

            (*env)->ReleasePrimitiveArrayCritical(env, ypointsArray, yPoints,
                                                  JNI_ABORT);
        }
        (*env)->ReleasePrimitiveArrayCritical(env, xpointsArray, xPoints,
                                              JNI_ABORT);
    }
}

void
OGLRenderer_DrawScanlines(OGLContext *oglc,
                          jint scanlineCount, jint *scanlines)
{
    J2dTraceLn(J2D_TRACE_INFO, "OGLRenderer_DrawScanlines");

    RETURN_IF_NULL(oglc);
    RETURN_IF_NULL(scanlines);

    CHECK_PREVIOUS_OP(GL_LINES);
    while (scanlineCount > 0) {
        // Translate each vertex by a fraction so
        // that we hit pixel centers.
        GLfloat x1 = ((GLfloat)*(scanlines++)) + 0.2f;
        GLfloat x2 = ((GLfloat)*(scanlines++)) + 1.2f;
        GLfloat y  = ((GLfloat)*(scanlines++)) + 0.5f;
        j2d_glVertex2f(x1, y);
        j2d_glVertex2f(x2, y);
        scanlineCount--;
    }
}

void
OGLRenderer_FillRect(OGLContext *oglc, jint x, jint y, jint w, jint h)
{
    J2dTraceLn(J2D_TRACE_INFO, "OGLRenderer_FillRect");

    if (w <= 0 || h <= 0) {
        return;
    }

    RETURN_IF_NULL(oglc);

    CHECK_PREVIOUS_OP(GL_QUADS);
    GLRECT_BODY_XYWH(x, y, w, h);
}

void
OGLRenderer_FillSpans(OGLContext *oglc, jint spanCount, jint *spans)
{
    J2dTraceLn(J2D_TRACE_INFO, "OGLRenderer_FillSpans");

    RETURN_IF_NULL(oglc);
    RETURN_IF_NULL(spans);

    CHECK_PREVIOUS_OP(GL_QUADS);
    while (spanCount > 0) {
        jint x1 = *(spans++);
        jint y1 = *(spans++);
        jint x2 = *(spans++);
        jint y2 = *(spans++);
        GLRECT_BODY_XYXY(x1, y1, x2, y2);
        spanCount--;
    }
}

#endif /* !HEADLESS */
