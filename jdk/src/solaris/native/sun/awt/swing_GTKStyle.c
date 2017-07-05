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
#include "com_sun_java_swing_plaf_gtk_GTKStyle.h"

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKStyle
 * Method:    nativeGetXThickness
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKStyle_nativeGetXThickness(
    JNIEnv *env, jclass klass, jint widget_type)
{
    jint ret;
    fp_gdk_threads_enter();
    ret = gtk2_get_xthickness(env, widget_type);
    fp_gdk_threads_leave();
    return ret;
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKStyle
 * Method:    nativeGetYThickness
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKStyle_nativeGetYThickness(
    JNIEnv *env, jclass klass, jint widget_type)
{
    jint ret;
    fp_gdk_threads_enter();
    ret = gtk2_get_ythickness(env, widget_type);
    fp_gdk_threads_leave();
    return ret;
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKStyle
 * Method:    nativeGetColorForState
 * Signature: (III)I
 */
JNIEXPORT jint JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKStyle_nativeGetColorForState(
    JNIEnv *env, jclass klass, jint widget_type,
    jint state_type, jint type_id)
{
    jint ret;
    fp_gdk_threads_enter();
    ret = gtk2_get_color_for_state(env, widget_type, state_type, type_id);
    fp_gdk_threads_leave();
    return ret;
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKStyle
 * Method:    nativeGetClassValue
 * Signature: (ILjava/lang/String;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKStyle_nativeGetClassValue(
    JNIEnv *env, jclass klass, jint widget_type, jstring key)
{
    jobject ret;
    fp_gdk_threads_enter();
    ret = gtk2_get_class_value(env, widget_type, key);
    fp_gdk_threads_leave();
    return ret;
}

/*
 * Class:     com_sun_java_swing_plaf_gtk_GTKStyle
 * Method:    nativeGetPangoFontName
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_sun_java_swing_plaf_gtk_GTKStyle_nativeGetPangoFontName(
    JNIEnv *env, jclass klass, jint widget_type)
{
    jstring ret;
    fp_gdk_threads_enter();
    ret = gtk2_get_pango_font_name(env, widget_type);
    fp_gdk_threads_leave();
    return ret;
}
