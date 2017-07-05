/*
 * Copyright (c) 1995, 2006, Oracle and/or its affiliates. All rights reserved.
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
#include "sun_awt_motif_MToolkit.h"
#include "sun_awt_motif_X11FontMetrics.h"
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
    fontIDs.pData = (*env)->GetFieldID(env, cls, "pData", "J");
    fontIDs.style = (*env)->GetFieldID(env, cls, "style", "I");
    fontIDs.size = (*env)->GetFieldID(env, cls, "size", "I");
    fontIDs.getPeer = (*env)->GetMethodID(env, cls, "getPeer_NoClientCode",
                                           "()Ljava/awt/peer/FontPeer;");
    fontIDs.getFamily =
      (*env)->GetMethodID(env, cls, "getFamily_NoClientCode",
                                            "()Ljava/lang/String;");
#endif /* !HEADLESS */
}

#ifndef HEADLESS

/* fieldIDs for X11FontMetrics fields that may be accessed from C */
static struct X11FontMetricsIDs {
    jfieldID widths;
    jfieldID font;
    jfieldID ascent;
    jfieldID descent;
    jfieldID leading;
    jfieldID height;
    jfieldID maxAscent;
    jfieldID maxDescent;
    jfieldID maxHeight;
    jfieldID maxAdvance;
} x11FontMetricsIDs;

/*
 * Class:     sun_awt_motif_X11FontMetrics
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   X11FontMetrics.java to initialize the fieldIDs for fields
   that may be accessed from C */

JNIEXPORT void JNICALL
Java_sun_awt_motif_X11FontMetrics_initIDs
  (JNIEnv *env, jclass cls)
{
    x11FontMetricsIDs.widths = (*env)->GetFieldID(env, cls, "widths", "[I");
    x11FontMetricsIDs.font =
      (*env)->GetFieldID(env, cls, "font", "Ljava/awt/Font;");
    x11FontMetricsIDs.ascent =
      (*env)->GetFieldID(env, cls, "ascent", "I");
    x11FontMetricsIDs.descent =
      (*env)->GetFieldID(env, cls, "descent", "I");
    x11FontMetricsIDs.leading =
      (*env)->GetFieldID(env, cls, "leading", "I");
    x11FontMetricsIDs.height =
      (*env)->GetFieldID(env, cls, "height", "I");
    x11FontMetricsIDs.maxAscent =
      (*env)->GetFieldID(env, cls, "maxAscent", "I");
    x11FontMetricsIDs.maxDescent =
      (*env)->GetFieldID(env, cls, "maxDescent", "I");
    x11FontMetricsIDs.maxHeight =
      (*env)->GetFieldID(env, cls, "maxHeight", "I");
    x11FontMetricsIDs.maxAdvance =
      (*env)->GetFieldID(env, cls, "maxAdvance", "I");
}


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
    fontDescriptorIDs.nativeName =
      (*env)->GetFieldID(env, cls, "nativeName",
                         "Ljava/lang/String;");
    fontDescriptorIDs.charsetName =
      (*env)->GetFieldID(env, cls, "charsetName",
                         "Ljava/lang/String;");
#endif /* !HEADLESS */
}

#ifndef HEADLESS
struct MFontPeerIDs mFontPeerIDs;
/*
 * Class:     sun_awt_motif_MFontPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MFontPeer.java to initialize the fieldIDs for fields
   that may be accessed from C */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MFontPeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mFontPeerIDs.xfsname =
      (*env)->GetFieldID(env, cls, "xfsname", "Ljava/lang/String;");
}
#endif /* !HEADLESS */

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
    platformFontIDs.componentFonts =
      (*env)->GetFieldID(env, cls, "componentFonts",
                         "[Lsun/awt/FontDescriptor;");
    platformFontIDs.fontConfig =
      (*env)->GetFieldID(env,cls, "fontConfig",
                         "Lsun/awt/FontConfiguration;");

    platformFontIDs.makeConvertedMultiFontString =
      (*env)->GetMethodID(env, cls, "makeConvertedMultiFontString",
                          "(Ljava/lang/String;)[Ljava/lang/Object;");

    platformFontIDs.makeConvertedMultiFontChars =
      (*env)->GetMethodID(env, cls, "makeConvertedMultiFontChars",
                          "([CII)[Ljava/lang/Object;");
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
#ifdef __linux__
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
        struct FontData *fdata = NULL;
        int32_t i, size;
        char *fontsetname = NULL;
        char *nativename = NULL;
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
            } else {
                nativename = "";
            }

            fdata->flist[i].xlfd = malloc(strlen(nativename)
                                          + strlen(defaultXLFD));
            jio_snprintf(fdata->flist[i].xlfd, strlen(nativename) + 10,
                         nativename, size * 10);

            if (nativename != NULL && nativename != "")
                JNU_ReleaseStringPlatformChars(env, fontDescriptorName, (const char *) nativename);

            /*
             * set charset_name
             */

            charsetName =
              (*env)->GetObjectField(env, fontDescriptor,
                                     fontDescriptorIDs.charsetName);

            fdata->flist[i].charset_name = (char *)
                JNU_GetStringPlatformChars(env, charsetName, NULL);

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
 * Class:     sun_awt_motif_X11FontMetrics
 * Method:    getMFCharsWidth
 * Signature: ([CIILjava/awt/Font;)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_X11FontMetrics_getMFCharsWidth
  (JNIEnv *env, jobject this, jcharArray data, jint offset, jint length, jobject font)
{
    jint retVal = 0;

    AWT_LOCK();

    retVal = awtJNI_GetMFStringWidth(env, data, offset, length, font);

    AWT_UNLOCK();
    return retVal;
}

/*
 * Class:     sun_awt_motif_X11FontMetrics
 * Method:    bytesWidth
 * Signature: ([BII)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_X11FontMetrics_bytesWidth
  (JNIEnv *env, jobject this, jbyteArray str, jint off, jint len)
{
    jint w = 0;
    unsigned char *s = NULL, *tmpPointer = NULL;
    int32_t ch = 0;
    int32_t cnt = 0;
    jobject widths = NULL;
    jint tempWidths[256];
    jint maxAdvance = 0;
    int32_t widlen = 0;

    if (JNU_IsNull(env, str)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return 0;
    }

    cnt = (*env)->GetArrayLength(env, str);
    if (cnt == 0) {
        return 0;
    }

    widths = (*env)->GetObjectField(env, this, x11FontMetricsIDs.widths);
    maxAdvance = (*env)->GetIntField(env, this, x11FontMetricsIDs.maxAdvance);
    if (!JNU_IsNull(env, widths)) {
        w = 0;
        widlen = (*env)->GetArrayLength(env, widths);
        (*env)->GetIntArrayRegion(env, widths, 0, widlen, (jint *) tempWidths);

        s = tmpPointer = (unsigned char *) (*env)->GetPrimitiveArrayCritical(env, str, NULL);
        if (s == NULL) {
            return 0;
        }

        while (--cnt >= 0) {
            ch = *tmpPointer++;
            if (ch < widlen) {
                w += tempWidths[ch];
            } else {
                w += maxAdvance;
            }
        }

        (*env)->ReleasePrimitiveArrayCritical(env, str, (jchar *) s, JNI_ABORT);
    } else {
        w = maxAdvance * cnt;
    }
    return w;
}

/*
 * Class:     sun_awt_motif_X11FontMetrics
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_X11FontMetrics_init
  (JNIEnv *env, jobject this)
{
    jobject font = NULL;
    struct FontData *fdata = NULL;
    jint tempWidths[256];
    jintArray widths = NULL;
    int32_t ccount = 0;
    int32_t i = 0;
    int32_t tempWidthsIndex = 0;
    char *err = NULL;

    if (JNU_IsNull(env, this)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();

    font = (*env)->GetObjectField(env, this, x11FontMetricsIDs.font);
    if (JNU_IsNull(env, this)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    fdata = awtJNI_GetFontData(env, font, &err);
    if (fdata == NULL) {
        JNU_ThrowInternalError(env, err);
        AWT_UNLOCK();
        return;
    }

    /*
     * Bug 4103248, 4120310. We must take all of the fonts into
     * consideration in providing the metrics, not just the 8859-1 font,
     * because the underlying Motif widgets are.
     */
    if (awtJNI_IsMultiFont(env, font) && fdata->xfs == NULL) {
        fdata->xfs = awtJNI_MakeFontSet(env, font);
    }
    if (fdata->xfs != NULL) {
        XFontSetExtents *fs_extents = NULL;
        fs_extents = XExtentsOfFontSet(fdata->xfs);

        (*env)->SetIntField(env, this, x11FontMetricsIDs.maxAscent,
                        (jint)(-fs_extents->max_logical_extent.y));
        (*env)->SetIntField(env, this, x11FontMetricsIDs.maxDescent,
                        (jint)(fs_extents->max_logical_extent.height +
                               fs_extents->max_logical_extent.y));
        (*env)->SetIntField(env, this, x11FontMetricsIDs.maxAdvance,
                        (jint)(fs_extents->max_logical_extent.width));
        (*env)->SetIntField(env, this, x11FontMetricsIDs.ascent,
                        (jint)(-fs_extents->max_ink_extent.y));
        (*env)->SetIntField(env, this, x11FontMetricsIDs.descent,
                        (jint)(fs_extents->max_ink_extent.height +
                         fs_extents->max_ink_extent.y));
    } else {
        (*env)->SetIntField(env, this, x11FontMetricsIDs.maxAscent,
                        (jint) fdata->xfont->max_bounds.ascent);
        (*env)->SetIntField(env, this, x11FontMetricsIDs.maxDescent,
                        (jint) fdata->xfont->max_bounds.descent);
        (*env)->SetIntField(env, this, x11FontMetricsIDs.maxAdvance,
                        (jint) fdata->xfont->max_bounds.width);
        (*env)->SetIntField(env, this, x11FontMetricsIDs.ascent,
                        (jint) fdata->xfont->ascent);
        (*env)->SetIntField(env, this, x11FontMetricsIDs.descent,
                        (jint) fdata->xfont->descent);
    }

    (*env)->SetIntField(env, this, x11FontMetricsIDs.leading, (jint) 1);
    (*env)->SetIntField(env, this, x11FontMetricsIDs.height,
                        (jint) fdata->xfont->ascent + fdata->xfont->descent + 1);
    (*env)->SetIntField(env, this, x11FontMetricsIDs.maxHeight,
                        (jint) fdata->xfont->max_bounds.ascent
                        + fdata->xfont->max_bounds.descent + 1);


    widths = (*env)->NewIntArray(env, 256);
    (*env)->SetObjectField(env, this, x11FontMetricsIDs.widths, widths);
    if (JNU_IsNull(env, widths)) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    /*
     * We could pin the array and then release it, but I believe this method
     * is faster and perturbs the VM less
     *
     */
    memset(tempWidths, 0, 256 * sizeof(jint));

    tempWidthsIndex = fdata->xfont->min_char_or_byte2;

    ccount = fdata->xfont->max_char_or_byte2 - fdata->xfont->min_char_or_byte2;

    if (fdata->xfont->per_char) {
        for (i = 0; i <= ccount; i++) {
            tempWidths[tempWidthsIndex++] = (jint) fdata->xfont->per_char[i].width;
        }
    } else {
        for (i = 0; i <= ccount; i++) {
            tempWidths[tempWidthsIndex++] = (jint) fdata->xfont->max_bounds.width;
        }
    }

    (*env)->SetIntArrayRegion(env, widths, 0, 256, (jint *) tempWidths);

    AWT_UNLOCK();
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
