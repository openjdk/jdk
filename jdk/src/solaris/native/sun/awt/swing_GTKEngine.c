/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <stdlib.h>
#include "gtk2_interface.h"
#include "com_sun_java_swing_plaf_gtk_GTKEngine.h"

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_arrow
 * Signature: (IIILjava/lang/String;IIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1arrow(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h, jint arrow_type)
{
    fp_gdk_threads_enter();
    gtk2_paint_arrow(widget_type, state, shadow_type, getStrFor(env, detail),
            x, y, w, h, arrow_type, TRUE);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_box
 * Signature: (IIILjava/lang/String;IIIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1box(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h,
        jint synth_state, jint dir)
{
    fp_gdk_threads_enter();
    gtk2_paint_box(widget_type, state, shadow_type, getStrFor(env, detail),
                   x, y, w, h, synth_state, dir);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_box_gap
 * Signature: (IIILjava/lang/String;IIIIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1box_1gap(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h,
        jint gap_side, jint gap_x, jint gap_w)
{
    fp_gdk_threads_enter();
    gtk2_paint_box_gap(widget_type, state, shadow_type, getStrFor(env, detail),
            x, y, w, h, gap_side, gap_x, gap_w);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_check
 * Signature: (IILjava/lang/String;IIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1check(
        JNIEnv *env, jobject this,
        jint widget_type, jint synth_state, jstring detail,
        jint x, jint y, jint w, jint h)
{
    fp_gdk_threads_enter();
    gtk2_paint_check(widget_type, synth_state, getStrFor(env, detail),
                     x, y, w, h);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_expander
 * Signature: (IILjava/lang/String;IIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1expander(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jstring detail,
        jint x, jint y, jint w, jint h, jint expander_style)
{
    fp_gdk_threads_enter();
    gtk2_paint_expander(widget_type, state, getStrFor(env, detail),
            x, y, w, h, expander_style);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_extension
 * Signature: (IIILjava/lang/String;IIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1extension(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h, jint placement)
{
    fp_gdk_threads_enter();
    gtk2_paint_extension(widget_type, state, shadow_type,
            getStrFor(env, detail), x, y, w, h, placement);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_flat_box
 * Signature: (IIILjava/lang/String;IIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1flat_1box(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h, jboolean has_focus)
{
    fp_gdk_threads_enter();
    gtk2_paint_flat_box(widget_type, state, shadow_type,
            getStrFor(env, detail), x, y, w, h, has_focus);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_focus
 * Signature: (IILjava/lang/String;IIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1focus(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jstring detail,
        jint x, jint y, jint w, jint h)
{
    fp_gdk_threads_enter();
    gtk2_paint_focus(widget_type, state, getStrFor(env, detail),
            x, y, w, h);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_handle
 * Signature: (IIILjava/lang/String;IIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1handle(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h, jint orientation)
{
    fp_gdk_threads_enter();
    gtk2_paint_handle(widget_type, state, shadow_type, getStrFor(env, detail),
            x, y, w, h, orientation);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_hline
 * Signature: (IILjava/lang/String;IIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1hline(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jstring detail,
        jint x, jint y, jint w, jint h)
{
    fp_gdk_threads_enter();
    gtk2_paint_hline(widget_type, state, getStrFor(env, detail),
            x, y, w, h);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_option
 * Signature: (IILjava/lang/String;IIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1option(
        JNIEnv *env, jobject this,
        jint widget_type, jint synth_state, jstring detail,
        jint x, jint y, jint w, jint h)
{
    fp_gdk_threads_enter();
    gtk2_paint_option(widget_type, synth_state, getStrFor(env, detail),
                      x, y, w, h);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_shadow
 * Signature: (IIILjava/lang/String;IIIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1shadow(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h,
        jint synth_state, jint dir)
{
    fp_gdk_threads_enter();
    gtk2_paint_shadow(widget_type, state, shadow_type, getStrFor(env, detail),
                      x, y, w, h, synth_state, dir);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_slider
 * Signature: (IIILjava/lang/String;IIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1slider(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jint shadow_type, jstring detail,
        jint x, jint y, jint w, jint h, jint orientation)
{
    fp_gdk_threads_enter();
    gtk2_paint_slider(widget_type, state, shadow_type, getStrFor(env, detail),
            x, y, w, h, orientation);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_vline
 * Signature: (IILjava/lang/String;IIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1vline(
        JNIEnv *env, jobject this,
        jint widget_type, jint state, jstring detail,
        jint x, jint y, jint w, jint h)
{
    fp_gdk_threads_enter();
    gtk2_paint_vline(widget_type, state, getStrFor(env, detail),
            x, y, w, h);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_paint_background
 * Signature: (IIIIII)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1paint_1background(
        JNIEnv *env, jobject this, jint widget_type, jint state,
        jint x, jint y, jint w, jint h)
{
    fp_gdk_threads_enter();
    gtk_paint_background(widget_type, state, x, y, w, h);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    nativeStartPainting
 * Signature: (II)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_nativeStartPainting(
        JNIEnv *env, jobject this, jint w, jint h)
{
    fp_gdk_threads_enter();
    gtk2_init_painting(w, h);
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    nativeFinishPainting
 * Signature: ([III)I
 */
JNIEXPORT jint JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_nativeFinishPainting(
        JNIEnv *env, jobject this, jintArray dest, jint width, jint height)
{
    jint transparency;
    gint *buffer = (gint*) (*env)->GetPrimitiveArrayCritical(env, dest, 0);
    fp_gdk_threads_enter();
    transparency = gtk2_copy_image(buffer, width, height);
    fp_gdk_threads_leave();
    (*env)->ReleasePrimitiveArrayCritical(env, dest, buffer, 0);
    return transparency;
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_switch_theme
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1switch_1theme(
        JNIEnv *env, jobject this)
{
    fp_gdk_threads_enter();
    flush_gtk_event_loop();
    fp_gdk_threads_leave();
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    native_get_gtk_setting
 * Signature: (I)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_com_sun_java_swing_plaf_gtk_GTKEngine_native_1get_1gtk_1setting(
        JNIEnv *env, jobject this, jint property)
{
    jobject obj;
    fp_gdk_threads_enter();
    obj = gtk2_get_setting(env, property);
    fp_gdk_threads_leave();
    return obj;
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKEngine
 * Method:    nativeSetRangeValue
 * Signature: (IDDDD)V
 */
JNIEXPORT void JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKEngine_nativeSetRangeValue(
        JNIEnv *env, jobject this, jint widget_type,
        jdouble value, jdouble min, jdouble max, jdouble visible)
{
    fp_gdk_threads_enter();
    gtk2_set_range_value(widget_type, value, min, max, visible);
    fp_gdk_threads_leave();
}
