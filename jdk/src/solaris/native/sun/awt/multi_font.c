/*
 * Copyright (c) 1996, 2005, Oracle and/or its affiliates. All rights reserved.
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
#ifndef XAWT
#include <Xm/Display.h>
#endif
#include "awt_Font.h"
#ifndef XAWT
#include "awt_Component.h"
#endif
#include "awt_MenuItem.h"
#include "awt_p.h"
#include "multi_font.h"

extern XFontStruct *loadFont(Display *, char *, int32_t);

extern struct FontIDs fontIDs;
//extern struct MComponentPeerIDs mComponentPeerIDs;
//extern struct MMenuItemPeerIDs mMenuItemPeerIDs;
extern struct PlatformFontIDs platformFontIDs;
extern struct MFontPeerIDs mFontPeerIDs;

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
#ifndef XAWT
jobject
awtJNI_CreateAndSetGlobalRef(JNIEnv * env, jobject this)
{
    jobject gRef;

    gRef = (*env)->NewGlobalRef(env, this);

    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.jniGlobalRef, gRef);

    return gRef;
}

struct gRefStruct
{
    jobject gRef;
    struct gRefStruct *next;
};

static struct gRefStruct *gRefHead = NULL;
static struct gRefStruct *gRefTail = NULL;

/*
 * This function is called by components that
 * are being disposed. It used to invalidate
 * the global ref immediately, but the awt is
 * rather full of thread race conditions involving
 * component disposal and outstanding events.
 * Now we queue up 'to be deleted' global refs
 * as they come in, and don't invalidate them
 * until the X event queue is empty. Callers of
 * either of these functions _must_ have AWT_LOCK'd
 * before using them!
 */
void
awtJNI_DeleteGlobalRef(JNIEnv * env, jobject this)
{
    jobject gRef;
    struct gRefStruct *newGRef;
    struct gRefStruct *temp;

    gRef = (jobject)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.jniGlobalRef);
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.jniGlobalRef, NULL);

    /*
     * Verra handy for tracking down race conditions. If you
     * have a peer getting called after its been disposed...
     */
    /* jio_fprintf(stderr,"%p\n",(void *)gRef); */

    newGRef = (struct gRefStruct *)malloc((size_t)sizeof(struct gRefStruct));

    if(newGRef == NULL)
        (*env)->DeleteGlobalRef(env, gRef);
    else
    {
        newGRef->gRef = gRef;
        newGRef->next = NULL;

        if(gRefHead == NULL)
        {
            gRefTail = newGRef;
            gRefHead = newGRef;
        }
        else
        {
            gRefTail->next = newGRef;
            gRefTail = newGRef;
        }
    }
}

void
awtJNI_DeleteGlobalMenuRef(JNIEnv * env, jobject this)
{
    jobject gRef;
    struct gRefStruct *newGRef;
    struct gRefStruct *temp;

    gRef = (jobject)
    //JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.jniGlobalRef);
    //JNU_SetLongFieldFromPtr(env, this, mMenuItemPeerIDs.jniGlobalRef, NULL);

    /*
     * Verra handy for tracking down race conditions. If you
     * have a peer getting called after its been disposed...
     */
    /* jio_fprintf(stderr,"%p\n",(void *)gRef); */

    newGRef = (struct gRefStruct *)malloc((size_t)sizeof(struct gRefStruct));

    if(newGRef == NULL)
        (*env)->DeleteGlobalRef(env, gRef);
    else
    {
        newGRef->gRef = gRef;
        newGRef->next = NULL;

        if(gRefHead == NULL)
        {
            gRefTail = newGRef;
            gRefHead = newGRef;
        }
        else
        {
            gRefTail->next = newGRef;
            gRefTail = newGRef;
        }
    }
}

void awtJNI_CleanupGlobalRefs()
{
    struct gRefStruct *working,*next;
    JNIEnv *env;
    int32_t count = 0;

    if(gRefHead == NULL) {
        return;
    }

    env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    working = gRefHead;
    gRefHead = gRefTail = NULL;

    while(working != NULL)
    {
        count++;
        next = working->next;
        (*env)->DeleteGlobalRef(env, working->gRef);

        free((void *)working);

        working = next;
    }
}
#endif
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
#ifndef XAWT
jobject
awtJNI_GetFont(JNIEnv * env, jobject this)
{
    jobject target = NULL;
    jobject font = NULL;

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    // SECURITY: Must call _NoClientCode() methods to ensure that we
    //           are not invoking client code on the privileged thread
    font = JNU_CallMethodByName(env,
                                NULL,
                                target,
                                "getFont_NoClientCode",
                                "()Ljava/awt/Font;").l;
    (*env)->DeleteLocalRef(env, target);
    return font;
}
#endif
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
#ifndef XAWT
#ifdef __linux__
XmString
unicodeXmStringCreate(char* text, char* tag, int len) {
    XmString ret_val;
    XtProcessLock();
    ret_val = _XmStringNCreate (text, tag, len);
    XtProcessUnlock();
    return ret_val;
}
#endif

/*
 * Unicode to Motif Multi Font Compound String converter
 *
 * ASSUMES: We are not running on a privileged thread
 */
XmString
awtJNI_MakeMultiFontString(JNIEnv * env, jstring s, jobject font)
{
    XmString xmstr = NULL, xmtmp1, xmtmp2;
    jobjectArray dataArray = NULL;
    char *err = NULL;
    int32_t stringCount,i;
    int32_t fdnumber;
    struct FontData *fdata = awtJNI_GetFontData(env, font, &err);
    jobject fontDescriptor = NULL;
    jbyteArray data = NULL;
    char *stringData = NULL;
    char tag[BUFSIZ];

    if ((*env)->PushLocalFrame(env, 16) < 0)
        return NULL;

    if (!JNU_IsNull(env, s) && !JNU_IsNull(env, font)) {
        jobject peer;

        peer = (*env)->CallObjectMethod(env,font,fontIDs.getPeer);

        DASSERT(!awt_currentThreadIsPrivileged(env));
        dataArray =
            (*env)->CallObjectMethod(
                             env,
                             peer,
                             platformFontIDs.makeConvertedMultiFontString,
                             s);

        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);

            (*env)->PopLocalFrame(env, NULL);
            return (XmString) NULL;
        }

        if(dataArray == NULL) {
            (*env)->PopLocalFrame(env, NULL);
            return (XmString) NULL;
        }
    } else {
        (*env)->PopLocalFrame(env, NULL);
        return (XmString) NULL;
    }

    stringCount = (*env)->GetArrayLength(env, dataArray);

    for (i = 0; i < stringCount; i+=2) {
        fontDescriptor = (*env)->GetObjectArrayElement(env, dataArray, i);
        data = (*env)->GetObjectArrayElement(env, dataArray, i + 1);

        /* Bail if we've finished */
        if(fontDescriptor == NULL || data == NULL)
            break;

        fdnumber = awtJNI_GetFontDescriptorNumber(env, font, fontDescriptor);
        fdata = awtJNI_GetFontData(env, font, &err);

        makeTag(fdata->flist[fdnumber].charset_name, fdnumber, tag);

        stringData = (char *)(*env)->GetPrimitiveArrayCritical(env, data, NULL);
        if(stringData != NULL) {
            unsigned char* buf = stringData;
            int len;
            char *offsetStringData;

            offsetStringData = stringData + (4 * sizeof(char));
#ifdef __linux__
            len = buf[0] << 24 | buf[1] << 16 | buf[2] << 8 | buf[3];
            /* Motif XmStringCreate() API requests "text must be a NULL-terminated
               string" and its implementation uses "strlen()" to calculate the length
               of the text string. Unfortunately when we deal with iso10646 font
               on linux, the "text" is requested to be encoded in UTF16, which has the
               posibility of including code points like "0xYY00" ("0xYY" + "0x00") that
               causes problem when XmStringCreate() calls _XmStringNCreate() without
               specifying a specific text lenth (see Motif XmString.c). The workaround is
               to call _XmStringNCreate() directly with specific text length at this
               cirsumstance.
            */
            if (strstr(fdata->flist[fdnumber].charset_name, "UnicodeBigUnmarked"))
                xmtmp1 = unicodeXmStringCreate(offsetStringData, tag, len);
            else
                xmtmp1 = XmStringCreate(offsetStringData, tag);
            if (xmstr == NULL)
                xmstr = xmtmp1;
            else {
                xmtmp2 = XmStringConcat(xmstr, xmtmp1);
                XmStringFree(xmtmp1);
                XmStringFree(xmstr);
                xmstr = xmtmp2;
            }
#else
            if(xmstr == NULL) {
                xmstr = XmStringCreate(offsetStringData, tag);
            }
            else {
                xmtmp1 = XmStringCreate(offsetStringData, tag);
                xmtmp2 = XmStringConcat(xmstr, xmtmp1);
                XmStringFree(xmtmp1);
                XmStringFree(xmstr);
                xmstr = xmtmp2;
            }
#endif
        }

        (*env)->ReleasePrimitiveArrayCritical(env, data, stringData, JNI_ABORT);
        (*env)->DeleteLocalRef(env, fontDescriptor);
        (*env)->DeleteLocalRef(env, data);
    }
    (*env)->PopLocalFrame(env, NULL);
    return xmstr;
}

/*
 * Find the character encoding for a given font and register that encoding
 * with the given tag.  The encoding is the last two fields of the XLFD of
 * the font (converted to uppercase).
 */
static void registerEncoding(char *xlfd, char *tag)
{
    char *e = xlfd + strlen(xlfd);
    char *ret = NULL;

    do { --e; } while (e != xlfd && *e != '-');
    do { --e; } while (e != xlfd && *e != '-');
    if (e != xlfd) {
        char *encoding = strdup(++e);
        char *u = NULL;

        for (u = encoding; *u != '\0'; ++u) {
            if (islower(*u)) {
                *u = toupper(*u);
            }
        }

        /*
         * Motif will core dump on or otherwise mishandle unknown (or
         * non-standard) character encodings (in conversion to compound
         * text, bug 4122785).  Register Sun private encodings for
         * Symbol or dingbat fonts as ISO8859-1, which is a lie,
         * but produces predictable results.
         */
        if (strncmp(encoding, "SUN-", 4) == 0) {
                free(encoding);
                encoding = strdup("ISO8859-1");
        }
        ret = XmRegisterSegmentEncoding(tag, encoding);
        if (ret != NULL)
                XtFree(ret);
        free(encoding);
    }
}


XmFontList
awtJNI_GetFontList(JNIEnv * env, jobject font)
{
    int32_t i;
    XmFontListEntry fle;
    XmFontList fontlist;
    XFontStruct *xf = NULL;
    int32_t size;
    struct FontData *fdata = NULL;
    char *err = NULL, tag[BUFSIZ];

    fdata = awtJNI_GetFontData(env, font, &err);

    makeTag(fdata->flist[0].charset_name, 0, tag);

    size = (int32_t) (*env)->GetIntField(env, font, fontIDs.size);

    if (fdata->flist[0].load == 0) {
        xf = loadFont(awt_display, fdata->flist[0].xlfd, size * 10);

        if (xf == NULL) {
            /* printf("Cannot load font: %s\n", fdata->list[0].xlfd); */
        } else {
            fdata->flist[0].xfont = xf;
            fdata->flist[0].load = 1;

            if (xf->min_byte1 == 0 && xf->max_byte1 == 0)
                fdata->flist[0].index_length = 1;
            else
                fdata->flist[0].index_length = 2;
        }
    }
    registerEncoding(fdata->flist[0].xlfd, tag);
    fle = XmFontListEntryCreate(tag, XmFONT_IS_FONT,
                                (XtPointer) fdata->flist[0].xfont);

    fontlist = XmFontListAppendEntry(NULL, fle);
    /*
     * Some versions of motif have a bug in
     * XmFontListEntryFree() which causes it to free more than it
     * should.  Use XtFree() is used instead.  See O'Reilly's
     * Motif Reference Manual for more information.
     */
    XmFontListEntryFree(&fle);

    for (i = 1; i < fdata->charset_num; i++) {
        makeTag(fdata->flist[i].charset_name, i, tag);

        if (fdata->flist[i].load == 0) {
            xf = loadFont(awt_display, fdata->flist[i].xlfd, size * 10);

            if (xf == NULL) {
                /* printf("Cannot load font: %s\n", fdata->flist[0].xlfd); */
                continue;
            }
            fdata->flist[i].xfont = xf;
            fdata->flist[i].load = 1;
            if (xf->min_byte1 == 0 && xf->max_byte1 == 0) {
                fdata->flist[i].index_length = 1;
            } else {
                fdata->flist[i].index_length = 2;
            }
        }
        registerEncoding(fdata->flist[i].xlfd, tag);
        fle = XmFontListEntryCreate(tag, XmFONT_IS_FONT,
                                    (XtPointer) fdata->flist[i].xfont);
        fontlist = XmFontListAppendEntry(fontlist, fle);
        /*
         * Some versions of motif have a bug in
         * XmFontListEntryFree() which causes it to free more than it
         * should.  Use XtFree() instead.  See O'Reilly's
         * Motif Reference Manual for more information.
         */
        XmFontListEntryFree(&fle);
    }

    return fontlist;
}
#endif
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
    xfsname = (*env)->GetObjectField(env, peer, mFontPeerIDs.xfsname);

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
#ifndef XAWT
    DASSERT(!awt_currentThreadIsPrivileged(env));
#endif
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
