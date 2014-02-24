/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#import "GeomUtilities.h"

static JNF_CLASS_CACHE(sjc_Point2D, "java/awt/geom/Point2D");
static JNF_MEMBER_CACHE(jm_pt_getX, sjc_Point2D, "getX", "()D");
static JNF_MEMBER_CACHE(jm_pt_getY, sjc_Point2D, "getY", "()D");

static JNF_CLASS_CACHE(sjc_Dimension2D, "java/awt/geom/Dimension2D");
static JNF_MEMBER_CACHE(jm_sz_getWidth, sjc_Dimension2D, "getWidth", "()D");
static JNF_MEMBER_CACHE(jm_sz_getHeight, sjc_Dimension2D, "getHeight", "()D");

static JNF_CLASS_CACHE(sjc_Rectangle2D, "java/awt/geom/Rectangle2D");
static JNF_MEMBER_CACHE(jm_rect_getX, sjc_Rectangle2D, "getX", "()D");
static JNF_MEMBER_CACHE(jm_rect_getY, sjc_Rectangle2D, "getY", "()D");
static JNF_MEMBER_CACHE(jm_rect_getWidth, sjc_Rectangle2D, "getWidth", "()D");
static JNF_MEMBER_CACHE(jm_rect_getHeight, sjc_Rectangle2D, "getHeight", "()D");


static jobject NewJavaRect(JNIEnv *env, jdouble x, jdouble y, jdouble w, jdouble h) {
    static JNF_CLASS_CACHE(sjc_Rectangle2DDouble, "java/awt/geom/Rectangle2D$Double");
    static JNF_CTOR_CACHE(ctor_Rectangle2DDouble, sjc_Rectangle2DDouble, "(DDDD)V");
    return JNFNewObject(env, ctor_Rectangle2DDouble, x, y, w, h);
}

jobject CGToJavaRect(JNIEnv *env, CGRect rect) {
   return NewJavaRect(env,
                      rect.origin.x,
                      rect.origin.y,
                      rect.size.width,
                      rect.size.height);
}

jobject NSToJavaRect(JNIEnv *env, NSRect rect) {
    return NewJavaRect(env,
                       rect.origin.x,
                       rect.origin.y,
                       rect.size.width,
                       rect.size.height);
}

CGRect JavaToCGRect(JNIEnv *env, jobject rect) {
    return CGRectMake(JNFCallDoubleMethod(env, rect, jm_rect_getX),
                      JNFCallDoubleMethod(env, rect, jm_rect_getY),
                      JNFCallDoubleMethod(env, rect, jm_rect_getWidth),
                      JNFCallDoubleMethod(env, rect, jm_rect_getHeight));
}

NSRect JavaToNSRect(JNIEnv *env, jobject rect) {
    return NSMakeRect(JNFCallDoubleMethod(env, rect, jm_rect_getX),
                      JNFCallDoubleMethod(env, rect, jm_rect_getY),
                      JNFCallDoubleMethod(env, rect, jm_rect_getWidth),
                      JNFCallDoubleMethod(env, rect, jm_rect_getHeight));
}

jobject NSToJavaPoint(JNIEnv *env, NSPoint point) {
    static JNF_CLASS_CACHE(sjc_Point2DDouble, "java/awt/geom/Point2D$Double");
    static JNF_CTOR_CACHE(ctor_Point2DDouble, sjc_Point2DDouble, "(DD)V");
    return JNFNewObject(env, ctor_Point2DDouble, (jdouble)point.x, (jdouble)point.y);
}

NSPoint JavaToNSPoint(JNIEnv *env, jobject point) {
    return NSMakePoint(JNFCallDoubleMethod(env, point, jm_pt_getX),
                       JNFCallDoubleMethod(env, point, jm_pt_getY));
}

jobject NSToJavaSize(JNIEnv *env, NSSize size) {
    static JNF_CLASS_CACHE(sjc_Dimension2DDouble, "java/awt/Dimension"); // No Dimension2D$Double :-(
    static JNF_CTOR_CACHE(ctor_Dimension2DDouble, sjc_Dimension2DDouble, "(II)V");
    return JNFNewObject(env, ctor_Dimension2DDouble, (jint)size.width, (jint)size.height);
}

NSSize JavaToNSSize(JNIEnv *env, jobject dimension) {
    return NSMakeSize(JNFCallDoubleMethod(env, dimension, jm_sz_getWidth),
                      JNFCallDoubleMethod(env, dimension, jm_sz_getHeight));
}

static NSScreen *primaryScreen(JNIEnv *env) {
    NSScreen *primaryScreen = [[NSScreen screens] objectAtIndex:0];
    if (primaryScreen != nil) return primaryScreen;
    if (env != NULL) [JNFException raise:env as:kRuntimeException reason:"Failed to convert, no screen."];
    return nil;
}

NSPoint ConvertNSScreenPoint(JNIEnv *env, NSPoint point) {
    point.y = [primaryScreen(env) frame].size.height - point.y;
    return point;
}

NSRect ConvertNSScreenRect(JNIEnv *env, NSRect rect) {
    rect.origin.y = [primaryScreen(env) frame].size.height - rect.origin.y - rect.size.height;
    return rect;
}
