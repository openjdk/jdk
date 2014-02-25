/*
 * Copyright (c) 1996, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * These routines are used for display string with multi font.
 */

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <ctype.h>
#include <jni.h>
#include <jni_util.h>
#include <jvm.h>
#include "awt_Font.h"
#include "awt_p.h"
#include "multi_font.h"

extern XFontStruct *loadFont(Display *, char *, int32_t);

extern struct FontIDs fontIDs;
extern struct PlatformFontIDs platformFontIDs;
extern struct XFontPeerIDs xFontPeerIDs;

/*
 * make string with str + string representation of num
 * This string is used as tag string of Motif Compound String and FontList.
 */
static void
makeTag(char *str, int32_t num, char *buf)
{
    int32_t len = strlen(str);

    strcpy(buf, str);
    buf[len] = '0' + num % 100;
    buf[len + 1] = '\0';
}

static int32_t
awtJNI_GetFontDescriptorNumber(JNIEnv * env
                               ,jobject font
                               ,jobject fd)
{
    int32_t i = 0, num;
    /* initialize to NULL so that DeleteLocalRef will work. */
    jobjectArray componentFonts = NULL;
    jobject peer = NULL;
    jobject temp = NULL;
    jboolean validRet = JNI_FALSE;

    if ((*env)->EnsureLocalCapacity(env, 2) < 0)
        goto done;

    peer = (*env)->CallObjectMethod(env,font,fontIDs.getPeer);
    if (peer == NULL)
        goto done;

    componentFonts = (jobjectArray)
        (*env)->GetObjectField(env,peer,platformFontIDs.componentFonts);

    if (componentFonts == NULL)
        goto done;

    num = (*env)->GetArrayLength(env, componentFonts);

    for (i = 0; i < num; i++) {
        temp = (*env)->GetObjectArrayElement(env, componentFonts, i);

        if ((*env)->IsSameObject(env, fd, temp)) {
            validRet = JNI_TRUE;
            break;
        }
        (*env)->DeleteLocalRef(env, temp);
    }

 done:
    (*env)->DeleteLocalRef(env, peer);
    (*env)->DeleteLocalRef(env, componentFonts);

    if (validRet)
        return i;

    return 0;
}

jobject
awtJNI_GetFMFont(JNIEnv * env, jobject this)
{
    return JNU_CallMethodByName(env, NULL, this, "getFont_NoClientCode",
                                "()Ljava/awt/Font;").l;
}

jboolean
awtJNI_IsMultiFont(JNIEnv * env, jobject this)
{
    jobject peer = NULL;
    jobject fontConfig = NULL;

    if (this == NULL) {
        return JNI_FALSE;
    }

    if ((*env)->EnsureLocalCapacity(env, 2) < 0) {
        return JNI_FALSE;
    }

    peer = (*env)->CallObjectMethod(env,this,fontIDs.getPeer);
    if (peer == NULL) {
        return JNI_FALSE;
    }

    fontConfig = (*env)->GetObjectField(env,peer,platformFontIDs.fontConfig);
    (*env)->DeleteLocalRef(env, peer);

    if (fontConfig == NULL) {
        return JNI_FALSE;
    }
    (*env)->DeleteLocalRef(env, fontConfig);

    return JNI_TRUE;
}

jboolean
awtJNI_IsMultiFontMetrics(JNIEnv * env, jobject this)
{
    jobject peer = NULL;
    jobject fontConfig = NULL;
    jobject font = NULL;

    if (JNU_IsNull(env, this)) {
        return JNI_FALSE;
    }
    if ((*env)->EnsureLocalCapacity(env, 3) < 0) {
        return JNI_FALSE;
    }

    font = JNU_CallMethodByName(env, NULL, this, "getFont_NoClientCode",
                                "()Ljava/awt/Font;").l;
    if (JNU_IsNull(env, font)) {
        return JNI_FALSE;
    }

    peer = (*env)->CallObjectMethod(env,font,fontIDs.getPeer);
    (*env)->DeleteLocalRef(env, font);

    if (peer == NULL) {
        return JNI_FALSE;
    }

    fontConfig = (*env)->GetObjectField(env,peer,platformFontIDs.fontConfig);
    (*env)->DeleteLocalRef(env, peer);
    if (fontConfig == NULL) {
        return JNI_FALSE;
    }
    (*env)->DeleteLocalRef(env, fontConfig);

    return JNI_TRUE;
}

/* #define FONT_DEBUG 2 */

XFontSet
awtJNI_MakeFontSet(JNIEnv * env, jobject font)
{
    jstring xlfd = NULL;
    char *xfontset = NULL;
    int32_t size;
    int32_t length = 0;
    char *realxlfd = NULL, *ptr = NULL, *prev = NULL;
    char **missing_list = NULL;
    int32_t missing_count;
    char *def_string = NULL;
    XFontSet xfs;
    jobject peer = NULL;
    jstring xfsname = NULL;
#ifdef FONT_DEBUG
    char xx[1024];
#endif

    if ((*env)->EnsureLocalCapacity(env, 2) < 0)
        return 0;

    size = (*env)->GetIntField(env, font, fontIDs.size) * 10;

    peer = (*env)->CallObjectMethod(env,font,fontIDs.getPeer);
    xfsname = (*env)->GetObjectField(env, peer, xFontPeerIDs.xfsname);

    if (JNU_IsNull(env, xfsname))
        xfontset = "";
    else
        xfontset = (char *)JNU_GetStringPlatformChars(env, xfsname, NULL);

    realxlfd = malloc(strlen(xfontset) + 50);

    prev = ptr = xfontset;
    while ((ptr = strstr(ptr, "%d"))) {
        char save = *(ptr + 2);

        *(ptr + 2) = '\0';
        jio_snprintf(realxlfd + length, strlen(xfontset) + 50 - length,
                     prev, size);
        length = strlen(realxlfd);
        *(ptr + 2) = save;

        prev = ptr + 2;
        ptr += 2;
    }
    strcpy(realxlfd + length, prev);

#ifdef FONT_DEBUG
    strcpy(xx, realxlfd);
#endif
    xfs = XCreateFontSet(awt_display, realxlfd, &missing_list,
                         &missing_count, &def_string);
#if FONT_DEBUG >= 2
    fprintf(stderr, "XCreateFontSet(%s)->0x%x\n", xx, xfs);
#endif

#if FONT_DEBUG
    if (missing_count != 0) {
        int32_t i;
        fprintf(stderr, "XCreateFontSet missing %d fonts:\n", missing_count);
        for (i = 0; i < missing_count; ++i) {
            fprintf(stderr, "\t\"%s\"\n", missing_list[i]);
        }
        fprintf(stderr, "  requested \"%s\"\n", xx);
#if FONT_DEBUG >= 3
        exit(-1);
#endif
    }
#endif

    free((void *)realxlfd);

    if (xfontset && !JNU_IsNull(env, xfsname))
        JNU_ReleaseStringPlatformChars(env, xfsname, (const char *) xfontset);

    (*env)->DeleteLocalRef(env, peer);
    (*env)->DeleteLocalRef(env, xfsname);
    return xfs;
}

/*
 * get multi font string width with multiple X11 font
 *
 * ASSUMES: We are not running on a privileged thread
 */
int32_t
awtJNI_GetMFStringWidth(JNIEnv * env, jcharArray s, int offset, int sLength, jobject font)
{
    char *err = NULL;
    unsigned char *stringData = NULL;
    char *offsetStringData = NULL;
    int32_t stringCount, i;
    int32_t size;
    struct FontData *fdata = NULL;
    jobject fontDescriptor = NULL;
    jbyteArray data = NULL;
    int32_t j;
    int32_t width = 0;
    int32_t length;
    XFontStruct *xf = NULL;
    jobjectArray dataArray = NULL;
    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        return 0;

    if (!JNU_IsNull(env, s) && !JNU_IsNull(env, font))
    {
        jobject peer;
        peer = (*env)->CallObjectMethod(env,font,fontIDs.getPeer);

        dataArray = (*env)->CallObjectMethod(
                                 env,
                                 peer,
                                 platformFontIDs.makeConvertedMultiFontChars,
                                 s, offset, sLength);

        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        (*env)->DeleteLocalRef(env, peer);

        if(dataArray == NULL)
        {
            return 0;
        }
    } else {
        return 0;
    }

    fdata = awtJNI_GetFontData(env, font, &err);

    stringCount = (*env)->GetArrayLength(env, dataArray);

    size = (*env)->GetIntField(env, font, fontIDs.size);

    for (i = 0; i < stringCount; i+=2)
    {
        fontDescriptor = (*env)->GetObjectArrayElement(env, dataArray, i);
        data = (*env)->GetObjectArrayElement(env, dataArray, i + 1);

        /* Bail if we've finished */
        if (fontDescriptor == NULL || data == NULL) {
            (*env)->DeleteLocalRef(env, fontDescriptor);
            (*env)->DeleteLocalRef(env, data);
            break;
        }

        j = awtJNI_GetFontDescriptorNumber(env, font, fontDescriptor);

        if (fdata->flist[j].load == 0) {
            xf = loadFont(awt_display,
                          fdata->flist[j].xlfd, size * 10);
            if (xf == NULL) {
                (*env)->DeleteLocalRef(env, fontDescriptor);
                (*env)->DeleteLocalRef(env, data);
                continue;
            }
            fdata->flist[j].load = 1;
            fdata->flist[j].xfont = xf;
            if (xf->min_byte1 == 0 && xf->max_byte1 == 0)
                fdata->flist[j].index_length = 1;
            else
                fdata->flist[j].index_length = 2;
        }
        xf = fdata->flist[j].xfont;

        stringData =
            (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, data,NULL);
        length = (stringData[0] << 24) | (stringData[1] << 16) |
            (stringData[2] << 8) | stringData[3];
        offsetStringData = (char *)(stringData + (4 * sizeof(char)));

        if (fdata->flist[j].index_length == 2) {
            width += XTextWidth16(xf, (XChar2b *)offsetStringData, length/2);
        } else {
            width += XTextWidth(xf, offsetStringData, length);
        }

        (*env)->ReleasePrimitiveArrayCritical(env, data, stringData, JNI_ABORT);
        (*env)->DeleteLocalRef(env, fontDescriptor);
        (*env)->DeleteLocalRef(env, data);
    }
    (*env)->DeleteLocalRef(env, dataArray);

    return width;
}
