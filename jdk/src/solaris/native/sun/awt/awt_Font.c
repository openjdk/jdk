/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef HEADLESS

#include "awt_p.h"
#include <string.h>
#include "java_awt_Component.h"
#include "java_awt_Font.h"
#include "java_awt_FontMetrics.h"
#include "sun_awt_X11GraphicsEnvironment.h"

#include "awt_Font.h"

#include "java_awt_Dimension.h"
#include "multi_font.h"
#include "Disposer.h"
#endif /* !HEADLESS */
#include <jni.h>
#ifndef HEADLESS
#include <jni_util.h>

#define defaultXLFD "-*-helvetica-*-*-*-*-12-*-*-*-*-*-iso8859-1"

struct FontIDs fontIDs;
struct PlatformFontIDs platformFontIDs;

static void pDataDisposeMethod(JNIEnv *env, jlong pData);

/* #define FONT_DEBUG 2 */
/* 1- print failures, 2- print all, 3- terminate on failure */
#if FONT_DEBUG
static XFontStruct *XLoadQueryFontX(Display *display, char *name)
{
    XFontStruct *result = NULL;
    result = XLoadQueryFont(display, name);
#if FONT_DEBUG < 2
    if (result == NULL)
#endif
        fprintf(stderr, "XLoadQueryFont(\"%s\") -> 0x%x.\n", name, result);
#if FONT_DEBUG >= 3
    if (result == NULL)
        exit(-1);
#endif
    return result;
}
#define XLoadQueryFont XLoadQueryFontX
#endif
#endif /* !HEADLESS */

/*
 * Class:     java_awt_Font
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for Font.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_Font_initIDs
  (JNIEnv *env, jclass cls)
{
#ifndef HEADLESS
    /** We call "NoClientCode" methods because they won't invoke client
        code on the privileged toolkit thread **/
    CHECK_NULL(fontIDs.pData = (*env)->GetFieldID(env, cls, "pData", "J"));
    CHECK_NULL(fontIDs.style = (*env)->GetFieldID(env, cls, "style", "I"));
    CHECK_NULL(fontIDs.size = (*env)->GetFieldID(env, cls, "size", "I"));
    CHECK_NULL(fontIDs.getPeer = (*env)->GetMethodID(env, cls, "getPeer_NoClientCode",
                                                     "()Ljava/awt/peer/FontPeer;"));
    CHECK_NULL(fontIDs.getFamily = (*env)->GetMethodID(env, cls, "getFamily_NoClientCode",
                                                       "()Ljava/lang/String;"));
#endif /* !HEADLESS */
}

#ifndef HEADLESS
/* fieldIDs for FontDescriptor fields that may be accessed from C */
static struct FontDescriptorIDs {
    jfieldID nativeName;
    jfieldID charsetName;
} fontDescriptorIDs;
#endif /* !HEADLESS */

/*
 * Class:     sun_awt_FontDescriptor
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   FontDescriptor.java to initialize the fieldIDs for fields
   that may be accessed from C */

JNIEXPORT void JNICALL
Java_sun_awt_FontDescriptor_initIDs
  (JNIEnv *env, jclass cls)
{
#ifndef HEADLESS
    CHECK_NULL(fontDescriptorIDs.nativeName =
               (*env)->GetFieldID(env, cls, "nativeName", "Ljava/lang/String;"));
    CHECK_NULL(fontDescriptorIDs.charsetName =
               (*env)->GetFieldID(env, cls, "charsetName", "Ljava/lang/String;"));
#endif /* !HEADLESS */
}

/*
 * Class:     sun_awt_PlatformFont
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   PlatformFont.java to initialize the fieldIDs for fields
   that may be accessed from C */

JNIEXPORT void JNICALL
Java_sun_awt_PlatformFont_initIDs
  (JNIEnv *env, jclass cls)
{
#ifndef HEADLESS
    CHECK_NULL(platformFontIDs.componentFonts =
               (*env)->GetFieldID(env, cls, "componentFonts",
                                  "[Lsun/awt/FontDescriptor;"));
    CHECK_NULL(platformFontIDs.fontConfig =
               (*env)->GetFieldID(env,cls, "fontConfig",
                                  "Lsun/awt/FontConfiguration;"));
    CHECK_NULL(platformFontIDs.makeConvertedMultiFontString =
               (*env)->GetMethodID(env, cls, "makeConvertedMultiFontString",
                                   "(Ljava/lang/String;)[Ljava/lang/Object;"));
    CHECK_NULL(platformFontIDs.makeConvertedMultiFontChars =
               (*env)->GetMethodID(env, cls, "makeConvertedMultiFontChars",
                                   "([CII)[Ljava/lang/Object;"));
#endif /* !HEADLESS */
}

#ifndef HEADLESS
XFontStruct *
loadFont(Display * display, char *name, int32_t pointSize)
{
    XFontStruct *f = NULL;

    /* try the exact xlfd name in font configuration file */
    f = XLoadQueryFont(display, name);
    if (f != NULL) {
        return f;
    }

    /*
     * try nearly font
     *
     *  1. specify FAMILY_NAME, WEIGHT_NAME, SLANT, POINT_SIZE,
     *     CHARSET_REGISTRY and CHARSET_ENCODING.
     *  2. change POINT_SIZE to PIXEL_SIZE
     *  3. change FAMILY_NAME to *
     *  4. specify only PIXEL_SIZE and CHARSET_REGISTRY/ENCODING
     *  5. change PIXEL_SIZE +1/-1/+2/-2...+4/-4
     *  6. default font pattern
     */
    {
        /*
         * This code assumes the name contains exactly 14 '-' delimiter.
         * If not use default pattern.
         */
        int32_t i, length, pixelSize;
        Boolean useDefault = FALSE;

        char buffer[BUFSIZ], buffer2[BUFSIZ];
        char *family = NULL, *style = NULL, *slant = NULL, *encoding = NULL;
        char *start = NULL, *end = NULL;

        if (strlen(name) > BUFSIZ - 1) {
            useDefault = TRUE;
        } else {
            strcpy(buffer, name);
        }

#define NEXT_HYPHEN\
        start = end + 1;\
        end = strchr(start, '-');\
        if (end == NULL) {\
                              useDefault = TRUE;\
        break;\
        }\
        *end = '\0'

             do {
                 end = buffer;

                 /* skip FOUNDRY */
                 NEXT_HYPHEN;

                 /* set FAMILY_NAME */
                 NEXT_HYPHEN;
                 family = start;

                 /* set STYLE_NAME */
                 NEXT_HYPHEN;
                 style = start;

                 /* set SLANT */
                 NEXT_HYPHEN;
                 slant = start;

                 /* skip SETWIDTH_NAME, ADD_STYLE_NAME, PIXEL_SIZE
                    POINT_SIZE, RESOLUTION_X, RESOLUTION_Y, SPACING
                    and AVERAGE_WIDTH */
                 NEXT_HYPHEN;
                 NEXT_HYPHEN;
                 NEXT_HYPHEN;
                 NEXT_HYPHEN;
                 NEXT_HYPHEN;
                 NEXT_HYPHEN;
                 NEXT_HYPHEN;
                 NEXT_HYPHEN;

                 /* set CHARSET_REGISTRY and CHARSET_ENCODING */
                 encoding = end + 1;
             }
             while (0);

#define TRY_LOAD\
        f = XLoadQueryFont(display, buffer2);\
        if (f != NULL) {\
                            strcpy(name, buffer2);\
        return f;\
        }

        if (!useDefault) {
            char *altstyle = NULL;

            /* Regular is the style for TrueType fonts -- Type1, F3 use roman */
            if (strcmp(style, "regular") == 0) {
                altstyle = "roman";
            }
#if defined(__linux__) || defined(MACOSX)
            if (!strcmp(family, "lucidasans")) {
                family = "lucida";
            }
#endif
            /* try 1. */
            jio_snprintf(buffer2, sizeof(buffer2),
                         "-*-%s-%s-%s-*-*-*-%d-*-*-*-*-%s",
                         family, style, slant, pointSize, encoding);
            TRY_LOAD;

            if (altstyle != NULL) {
                jio_snprintf(buffer2, sizeof(buffer2),
                             "-*-%s-%s-%s-*-*-*-%d-*-*-*-*-%s",
                             family, altstyle, slant, pointSize, encoding);
                TRY_LOAD;
            }

            /* search bitmap font */
            pixelSize = pointSize / 10;

            /* try 2. */
            jio_snprintf(buffer2, sizeof(buffer2),
                         "-*-%s-%s-%s-*-*-%d-*-*-*-*-*-%s",
                         family, style, slant, pixelSize, encoding);
            TRY_LOAD;

            if (altstyle != NULL) {
                jio_snprintf(buffer2, sizeof(buffer2),
                             "-*-%s-%s-%s-*-*-%d-*-*-*-*-*-%s",
                             family, altstyle, slant, pixelSize, encoding);
                TRY_LOAD;
            }

            /* try 3 */
            jio_snprintf(buffer2, sizeof(buffer2),
                         "-*-*-%s-%s-*-*-%d-*-*-*-*-*-%s",
                         style, slant, pixelSize, encoding);
            TRY_LOAD;
            if (altstyle != NULL) {
                jio_snprintf(buffer2, sizeof(buffer2),
                             "-*-*-%s-%s-*-*-%d-*-*-*-*-*-%s",
                             altstyle, slant, pixelSize, encoding);
                TRY_LOAD;
            }

            /* try 4 */
            jio_snprintf(buffer2, sizeof(buffer2),
                         "-*-*-*-%s-*-*-%d-*-*-*-*-*-%s",
                         slant, pixelSize, encoding);

            TRY_LOAD;

            /* try 5. */
            jio_snprintf(buffer2, sizeof(buffer2),
                         "-*-*-*-*-*-*-%d-*-*-*-*-*-%s",
                         pixelSize, encoding);
            TRY_LOAD;

            /* try 6. */
            for (i = 1; i < 4; i++) {
                if (pixelSize < i)
                    break;
                jio_snprintf(buffer2, sizeof(buffer2),
                             "-*-%s-%s-%s-*-*-%d-*-*-*-*-*-%s",
                             family, style, slant, pixelSize + i, encoding);
                TRY_LOAD;

                jio_snprintf(buffer2, sizeof(buffer2),
                             "-*-%s-%s-%s-*-*-%d-*-*-*-*-*-%s",
                             family, style, slant, pixelSize - i, encoding);
                TRY_LOAD;

                jio_snprintf(buffer2, sizeof(buffer2),
                             "-*-*-*-*-*-*-%d-*-*-*-*-*-%s",
                             pixelSize + i, encoding);
                TRY_LOAD;

                jio_snprintf(buffer2, sizeof(buffer2),
                             "-*-*-*-*-*-*-%d-*-*-*-*-*-%s",
                             pixelSize - i, encoding);
                TRY_LOAD;
            }
        }
    }

    strcpy(name, defaultXLFD);
    return XLoadQueryFont(display, defaultXLFD);
}

/*
 * Hardwired list of mappings for generic font names "Helvetica",
 * "TimesRoman", "Courier", "Dialog", and "DialogInput".
 */
static char *defaultfontname = "fixed";
static char *defaultfoundry = "misc";
static char *anyfoundry = "*";
static char *anystyle = "*-*";
static char *isolatin1 = "iso8859-1";

static char *
Style(int32_t s)
{
    switch (s) {
        case java_awt_Font_ITALIC:
            return "medium-i";
        case java_awt_Font_BOLD:
            return "bold-r";
        case java_awt_Font_BOLD + java_awt_Font_ITALIC:
            return "bold-i";
        case java_awt_Font_PLAIN:
        default:
            return "medium-r";
    }
}

static int32_t
awtJNI_FontName(JNIEnv * env, jstring name, char **foundry, char **facename, char **encoding)
{
    char *cname = NULL;

    if (JNU_IsNull(env, name)) {
        return 0;
    }
    cname = (char *) JNU_GetStringPlatformChars(env, name, NULL);
    if (cname == NULL) {
        (*env)->ExceptionClear(env);
        JNU_ThrowOutOfMemoryError(env, "Could not create font name");
        return 0;
    }

    /* additional default font names */
    if (strcmp(cname, "serif") == 0) {
        *foundry = "adobe";
        *facename = "times";
        *encoding = isolatin1;
    } else if (strcmp(cname, "sansserif") == 0) {
        *foundry = "adobe";
        *facename = "helvetica";
        *encoding = isolatin1;
    } else if (strcmp(cname, "monospaced") == 0) {
        *foundry = "adobe";
        *facename = "courier";
        *encoding = isolatin1;
    } else if (strcmp(cname, "helvetica") == 0) {
        *foundry = "adobe";
        *facename = "helvetica";
        *encoding = isolatin1;
    } else if (strcmp(cname, "timesroman") == 0) {
        *foundry = "adobe";
        *facename = "times";
        *encoding = isolatin1;
    } else if (strcmp(cname, "courier") == 0) {
        *foundry = "adobe";
        *facename = "courier";
        *encoding = isolatin1;
    } else if (strcmp(cname, "dialog") == 0) {
        *foundry = "b&h";
        *facename = "lucida";
        *encoding = isolatin1;
    } else if (strcmp(cname, "dialoginput") == 0) {
        *foundry = "b&h";
        *facename = "lucidatypewriter";
        *encoding = isolatin1;
    } else if (strcmp(cname, "zapfdingbats") == 0) {
        *foundry = "itc";
        *facename = "zapfdingbats";
        *encoding = "*-*";
    } else {
#ifdef DEBUG
        jio_fprintf(stderr, "Unknown font: %s\n", cname);
#endif
        *foundry = defaultfoundry;
        *facename = defaultfontname;
        *encoding = isolatin1;
    }

    if (cname != NULL)
        JNU_ReleaseStringPlatformChars(env, name, (const char *) cname);

    return 1;
}

struct FontData *
awtJNI_GetFontData(JNIEnv * env, jobject font, char **errmsg)
{
    /* We are going to create at most 4 outstanding local refs in this
     * function. */
    if ((*env)->EnsureLocalCapacity(env, 4) < 0) {
        return NULL;
    }

    if (!JNU_IsNull(env, font) && awtJNI_IsMultiFont(env, font)) {
        JNU_CHECK_EXCEPTION_RETURN(env, NULL);

        struct FontData *fdata = NULL;
        int32_t i, size;
        char *fontsetname = NULL;
        char *nativename = NULL;
        Boolean doFree = FALSE;
        jobjectArray componentFonts = NULL;
        jobject peer = NULL;
        jobject fontDescriptor = NULL;
        jstring fontDescriptorName = NULL;
        jstring charsetName = NULL;

        fdata = (struct FontData *) JNU_GetLongFieldAsPtr(env,font,
                                                         fontIDs.pData);

        if (fdata != NULL && fdata->flist != NULL) {
            return fdata;
        }
        size = (*env)->GetIntField(env, font, fontIDs.size);
        fdata = (struct FontData *) malloc(sizeof(struct FontData));

        peer = (*env)->CallObjectMethod(env, font, fontIDs.getPeer);

        componentFonts =
          (*env)->GetObjectField(env, peer, platformFontIDs.componentFonts);
        /* We no longer need peer */
        (*env)->DeleteLocalRef(env, peer);

        fdata->charset_num = (*env)->GetArrayLength(env, componentFonts);

        fdata->flist = (awtFontList *) malloc(sizeof(awtFontList)
                                              * fdata->charset_num);
        fdata->xfont = NULL;
        for (i = 0; i < fdata->charset_num; i++) {
            /*
             * set xlfd name
             */

            fontDescriptor = (*env)->GetObjectArrayElement(env, componentFonts, i);
            fontDescriptorName =
              (*env)->GetObjectField(env, fontDescriptor,
                                     fontDescriptorIDs.nativeName);

            if (!JNU_IsNull(env, fontDescriptorName)) {
                nativename = (char *) JNU_GetStringPlatformChars(env, fontDescriptorName, NULL);
                if (nativename == NULL) {
                    nativename = "";
                    doFree = FALSE;
                } else {
                    doFree = TRUE;
                }
            } else {
                nativename = "";
                doFree = FALSE;
            }

            fdata->flist[i].xlfd = malloc(strlen(nativename)
                                          + strlen(defaultXLFD));
            jio_snprintf(fdata->flist[i].xlfd, strlen(nativename) + 10,
                         nativename, size * 10);

            if (nativename != NULL && doFree)
                JNU_ReleaseStringPlatformChars(env, fontDescriptorName, (const char *) nativename);

            /*
             * set charset_name
             */

            charsetName =
              (*env)->GetObjectField(env, fontDescriptor,
                                     fontDescriptorIDs.charsetName);

            fdata->flist[i].charset_name = (char *)
                JNU_GetStringPlatformChars(env, charsetName, NULL);
            if (fdata->flist[i].charset_name == NULL) {
                (*env)->ExceptionClear(env);
                JNU_ThrowOutOfMemoryError(env, "Could not create charset name");
                return NULL;
            }

            /* We are done with the objects. */
            (*env)->DeleteLocalRef(env, fontDescriptor);
            (*env)->DeleteLocalRef(env, fontDescriptorName);
            (*env)->DeleteLocalRef(env, charsetName);

            /*
             * set load & XFontStruct
             */
            fdata->flist[i].load = 0;

            /*
             * This appears to be a bogus check.  The actual intent appears
             * to be to find out whether this is the "base" font in a set,
             * rather than iso8859_1 explicitly.  Note that iso8859_15 will
             * and must also pass this test.
             */

            if (fdata->xfont == NULL &&
                strstr(fdata->flist[i].charset_name, "8859_1")) {
                fdata->flist[i].xfont =
                    loadFont(awt_display, fdata->flist[i].xlfd, size * 10);
                if (fdata->flist[i].xfont != NULL) {
                    fdata->flist[i].load = 1;
                    fdata->xfont = fdata->flist[i].xfont;
                    fdata->flist[i].index_length = 1;
                } else {
                    if (errmsg != NULL) {
                        *errmsg = "java/lang" "NullPointerException";
                    }
                    (*env)->DeleteLocalRef(env, componentFonts);
                    return NULL;
                }
            }
        }
        (*env)->DeleteLocalRef(env, componentFonts);
        /*
         * XFontSet will create if the peer of TextField/TextArea
         * are used.
         */
        fdata->xfs = NULL;

        JNU_SetLongFieldFromPtr(env,font,fontIDs.pData,fdata);
        Disposer_AddRecord(env, font, pDataDisposeMethod, ptr_to_jlong(fdata));
        return fdata;
    } else {
        Display *display = NULL;
        struct FontData *fdata = NULL;
        char fontSpec[1024];
        int32_t height;
        int32_t oheight;
        int32_t above = 0;              /* tries above height */
        int32_t below = 0;              /* tries below height */
        char *foundry = NULL;
        char *name = NULL;
        char *encoding = NULL;
        char *style = NULL;
        XFontStruct *xfont = NULL;
        jstring family = NULL;

        if (JNU_IsNull(env, font)) {
            if (errmsg != NULL) {
                *errmsg = "java/lang" "NullPointerException";
            }
            return (struct FontData *) NULL;
        }
        display = XDISPLAY;

        fdata = (struct FontData *) JNU_GetLongFieldAsPtr(env,font,fontIDs.pData);
        if (fdata != NULL && fdata->xfont != NULL) {
            return fdata;
        }

        family = (*env)->CallObjectMethod(env, font, fontIDs.getFamily);

        if (!awtJNI_FontName(env, family, &foundry, &name, &encoding)) {
            if (errmsg != NULL) {
                *errmsg = "java/lang" "NullPointerException";
            }
            (*env)->DeleteLocalRef(env, family);
            return (struct FontData *) NULL;
        }
        style = Style((*env)->GetIntField(env, font, fontIDs.style));
        oheight = height = (*env)->GetIntField(env, font, fontIDs.size);

        while (1) {
            jio_snprintf(fontSpec, sizeof(fontSpec), "-%s-%s-%s-*-*-%d-*-*-*-*-*-%s",
                         foundry,
                         name,
                         style,
                         height,
                         encoding);

            /*fprintf(stderr,"LoadFont: %s\n", fontSpec); */
            xfont = XLoadQueryFont(display, fontSpec);

            /* XXX: sometimes XLoadQueryFont returns a bogus font structure */
            /* with negative ascent. */
            if (xfont == (Font) NULL || xfont->ascent < 0) {
                if (xfont != NULL) {
                    XFreeFont(display, xfont);
                }
                if (foundry != anyfoundry) {  /* Use ptr comparison here, not strcmp */
                    /* Try any other foundry before messing with the sizes */
                    foundry = anyfoundry;
                    continue;
                }
                /* We couldn't find the font. We'll try to find an */
                /* alternate by searching for heights above and below our */
                /* preferred height. We try for 4 heights above and below. */
                /* If we still can't find a font we repeat the algorithm */
                /* using misc-fixed as the font. If we then fail, then we */
                /* give up and signal an error. */
                if (above == below) {
                    above++;
                    height = oheight + above;
                } else {
                    below++;
                    if (below > 4) {
                        if (name != defaultfontname || style != anystyle) {
                            name = defaultfontname;
                            foundry = defaultfoundry;
                            height = oheight;
                            style = anystyle;
                            encoding = isolatin1;
                            above = below = 0;
                            continue;
                        } else {
                            if (errmsg != NULL) {
                                *errmsg = "java/io/" "FileNotFoundException";
                            }
                            (*env)->DeleteLocalRef(env, family);
                            return (struct FontData *) NULL;
                        }
                    }
                    height = oheight - below;
                }
                continue;
            } else {
                fdata = ZALLOC(FontData);

                if (fdata == NULL) {
                    if (errmsg != NULL) {
                        *errmsg = "java/lang" "OutOfMemoryError";
                    }
                } else {
                    fdata->xfont = xfont;
                    JNU_SetLongFieldFromPtr(env,font,fontIDs.pData,fdata);
                    Disposer_AddRecord(env, font, pDataDisposeMethod,
                                       ptr_to_jlong(fdata));
                }
                (*env)->DeleteLocalRef(env, family);
                return fdata;
            }
        }
        /* not reached */
    }
}

/*
 * Registered with the 2D disposer to be called after the Font is GC'd.
 */
static void pDataDisposeMethod(JNIEnv *env, jlong pData)
{
    struct FontData *fdata = NULL;
    int32_t i = 0;
    Display *display = XDISPLAY;

    AWT_LOCK();
    fdata = (struct FontData *)pData;

    if (fdata == NULL) {
        AWT_UNLOCK();
        return;
    }

    if (fdata->xfs != NULL) {
        XFreeFontSet(display, fdata->xfs);
    }

    /* AWT fonts are always "multifonts" and probably have been in
     * all post 1.0 releases, so this test test for multi fonts is
     * probably not needed, and the singleton xfont is probably never used.
     */
    if (fdata->charset_num > 0) {
        for (i = 0; i < fdata->charset_num; i++) {
            free((void *)fdata->flist[i].xlfd);
            JNU_ReleaseStringPlatformChars(env, NULL,
                                           fdata->flist[i].charset_name);
            if (fdata->flist[i].load) {
                XFreeFont(display, fdata->flist[i].xfont);
            }
        }

        free((void *)fdata->flist);

        /* Don't free fdata->xfont because it is equal to fdata->flist[i].xfont
           for some 'i' */
    } else {
        if (fdata->xfont != NULL) {
            XFreeFont(display, fdata->xfont);
        }
    }

    free((void *)fdata);

    AWT_UNLOCK();
}
#endif /* !HEADLESS */
