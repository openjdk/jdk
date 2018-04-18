/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_awt_windows_ThemeReader.h"
#include <string.h>

#include "awt.h"
#include "awt_Toolkit.h"
#include "awt_Object.h"
#include "awt_Component.h"

#include "math.h"

// Important note about VC6 and VC7 (or XP Platform SDK)   !
//
// These type definitions have been imported from UxTheme.h
// They have been imported instead of including them, because
// currently we don't require Platform SDK for building J2SE and
// VC6 includes do not have UxTheme.h. When we move to VC7
// we should remove these imports and just include
//
//  Uncomment these when we start using VC 7 (or XP Platform SDK)
//
//  #include <uxtheme.h>
//  #incldue <tmschema.h>


// Remove everyting inside this ifdef when we start using VC 7 (or XP Platform SDK)
#ifndef  _UXTHEME_H_
typedef HANDLE HTHEME;          // handle to a section of theme data for class

typedef enum {
    TS_MIN,
    TS_TRUE,
    TS_DRAW
} THEME_SIZE;


// Remove these when we start using VC 7 (or XP Platform SDK)
typedef struct _MARGINS
{
    int cxLeftWidth;      // width of left border that retains its size
    int cxRightWidth;     // width of right border that retains its size
    int cyTopHeight;      // height of top border that retains its size
    int cyBottomHeight;   // height of bottom border that retains its size
} MARGINS, *PMARGINS;

#define TMT_TRANSPARENT 2201
#endif // _UXTHEME_H_

#if defined(_MSC_VER) && _MSC_VER >= 1800
#  define ROUND_TO_INT(num)    ((int) round(num))
#else
#  define ROUND_TO_INT(num)    ((int) floor((num) + 0.5))
#endif

#define ALPHA_MASK 0xff000000
#define RED_MASK 0xff0000
#define GREEN_MASK 0xff00
#define BLUE_MASK 0xff
#define ALPHA_SHIFT 24
#define RED_SHIFT 16
#define GREEN_SHIFT 8


typedef HRESULT(__stdcall *PFNCLOSETHEMEDATA)(HTHEME hTheme);

typedef HRESULT(__stdcall *PFNDRAWTHEMEBACKGROUND)(HTHEME hTheme, HDC hdc,
        int iPartId, int iStateId, const RECT *pRect,  const RECT *pClipRect);

typedef HTHEME(__stdcall *PFNOPENTHEMEDATA)(HWND hwnd, LPCWSTR pszClassList);

typedef HRESULT (__stdcall *PFNDRAWTHEMETEXT)(HTHEME hTheme, HDC hdc,
          int iPartId, int iStateId, LPCWSTR pszText, int iCharCount,
          DWORD dwTextFlags, DWORD dwTextFlags2, const RECT *pRect);

typedef HRESULT (__stdcall *PFNGETTHEMEBACKGROUNDCONTENTRECT)(HTHEME hTheme,
        HDC hdc, int iPartId, int iStateId,  const RECT *pBoundingRect,
        RECT *pContentRect);

typedef HRESULT (__stdcall *PFNGETTHEMEMARGINS)(HTHEME hTheme,
        OPTIONAL HDC hdc, int iPartId, int iStateId, int iPropId,
        OPTIONAL RECT *prc, OUT MARGINS *pMargins);

typedef BOOL (__stdcall *PFNISTHEMEPARTDEFINED)(HTHEME hTheme, int iPartId, int iStateId);

typedef HRESULT (__stdcall *PFNGETTHEMEBOOL)(HTHEME hTheme, int iPartId,
        int iStateId, int iPropId, BOOL *pfVal);

typedef BOOL (__stdcall *PFNGETTHEMESYSBOOL)(HTHEME hTheme, int iPropId);

typedef HRESULT (__stdcall *PFNGETTHEMECOLOR)(HTHEME hTheme, int iPartId,
        int iStateId, int iPropId, COLORREF *pColor);

typedef HRESULT (__stdcall *PFNGETTHEMEENUMVALUE)(HTHEME hTheme, int iPartId,
        int iStateId, int iPropId, int *val);
typedef HRESULT (__stdcall *PFNGETTHEMEINT)(HTHEME hTheme, int iPartId,
        int iStateId, int iPropId, int *val);
typedef HRESULT (__stdcall *PFNGETTHEMEPARTSIZE)(HTHEME hTheme, HDC hdc,
        int iPartId, int iStateId, RECT *prc, THEME_SIZE eSize, SIZE *size);

typedef HRESULT (__stdcall *PFNGETTHEMEPOSITION)(HTHEME hTheme, int iPartId,
        int iStateId, int propID, POINT *point);

typedef HRESULT(__stdcall *PFNSETWINDOWTHEME)(HWND hwnd, LPCWSTR pszSubAppName,
            LPCWSTR pszSubIdList);

typedef HRESULT (__stdcall *PFNISTHEMEBACKGROUNDPARTIALLYTRANSPARENT)
                (HTHEME hTheme, int iPartId, int iStateId);

typedef HRESULT (__stdcall *PFNGETTHEMETRANSITIONDURATION)
                (HTHEME hTheme, int iPartId, int iStateIdFrom, int iStateIdTo,
                 int iPropId, DWORD *pdwDuration);

static PFNOPENTHEMEDATA OpenThemeData = NULL;
static PFNDRAWTHEMEBACKGROUND DrawThemeBackground = NULL;
static PFNCLOSETHEMEDATA CloseThemeData = NULL;
static PFNDRAWTHEMETEXT DrawThemeText = NULL;
static PFNGETTHEMEBACKGROUNDCONTENTRECT GetThemeBackgroundContentRect = NULL;
static PFNGETTHEMEMARGINS GetThemeMargins = NULL;
static PFNISTHEMEPARTDEFINED IsThemePartDefined = NULL;
static PFNGETTHEMEBOOL GetThemeBool=NULL;
static PFNGETTHEMESYSBOOL GetThemeSysBool=NULL;
static PFNGETTHEMECOLOR GetThemeColor=NULL;
static PFNGETTHEMEENUMVALUE GetThemeEnumValue = NULL;
static PFNGETTHEMEINT GetThemeInt = NULL;
static PFNGETTHEMEPARTSIZE GetThemePartSize = NULL;
static PFNGETTHEMEPOSITION GetThemePosition = NULL;
static PFNSETWINDOWTHEME SetWindowTheme = NULL;
static PFNISTHEMEBACKGROUNDPARTIALLYTRANSPARENT
                                   IsThemeBackgroundPartiallyTransparent = NULL;
//this function might not exist on Windows XP
static PFNGETTHEMETRANSITIONDURATION GetThemeTransitionDuration = NULL;


BOOL InitThemes() {
    static HMODULE hModThemes = NULL;
    hModThemes = JDK_LoadSystemLibrary("UXTHEME.DLL");
    DTRACE_PRINTLN1("InitThemes hModThemes = %x\n", hModThemes);
    if(hModThemes) {
        DTRACE_PRINTLN("Loaded UxTheme.dll\n");
        OpenThemeData = (PFNOPENTHEMEDATA)GetProcAddress(hModThemes,
                                                        "OpenThemeData");
        DrawThemeBackground = (PFNDRAWTHEMEBACKGROUND)GetProcAddress(
                                        hModThemes, "DrawThemeBackground");
        CloseThemeData = (PFNCLOSETHEMEDATA)GetProcAddress(
                                                hModThemes, "CloseThemeData");
        DrawThemeText = (PFNDRAWTHEMETEXT)GetProcAddress(
                                        hModThemes, "DrawThemeText");
        GetThemeBackgroundContentRect = (PFNGETTHEMEBACKGROUNDCONTENTRECT)
                GetProcAddress(hModThemes, "GetThemeBackgroundContentRect");
        GetThemeMargins = (PFNGETTHEMEMARGINS)GetProcAddress(
                                        hModThemes, "GetThemeMargins");
        IsThemePartDefined = (PFNISTHEMEPARTDEFINED)GetProcAddress(
                                        hModThemes, "IsThemePartDefined");
        GetThemeBool = (PFNGETTHEMEBOOL)GetProcAddress(
                                        hModThemes, "GetThemeBool");
        GetThemeSysBool = (PFNGETTHEMESYSBOOL)GetProcAddress(hModThemes,
                                                        "GetThemeSysBool");
        GetThemeColor = (PFNGETTHEMECOLOR)GetProcAddress(hModThemes,
                                                        "GetThemeColor");
        GetThemeEnumValue = (PFNGETTHEMEENUMVALUE)GetProcAddress(hModThemes,
                                                "GetThemeEnumValue");
        GetThemeInt = (PFNGETTHEMEINT)GetProcAddress(hModThemes, "GetThemeInt");
        GetThemePosition = (PFNGETTHEMEPOSITION)GetProcAddress(hModThemes,
                                                        "GetThemePosition");
        GetThemePartSize = (PFNGETTHEMEPARTSIZE)GetProcAddress(hModThemes,
                                                         "GetThemePartSize");
        SetWindowTheme = (PFNSETWINDOWTHEME)GetProcAddress(hModThemes,
                                                        "SetWindowTheme");
        IsThemeBackgroundPartiallyTransparent =
            (PFNISTHEMEBACKGROUNDPARTIALLYTRANSPARENT)GetProcAddress(hModThemes,
                                       "IsThemeBackgroundPartiallyTransparent");
        //this function might not exist
        GetThemeTransitionDuration =
            (PFNGETTHEMETRANSITIONDURATION)GetProcAddress(hModThemes,
                                        "GetThemeTransitionDuration");

        if(OpenThemeData
           && DrawThemeBackground
           && CloseThemeData
           && DrawThemeText
           && GetThemeBackgroundContentRect
           && GetThemeMargins
           && IsThemePartDefined
           && GetThemeBool
           && GetThemeSysBool
           && GetThemeColor
           && GetThemeEnumValue
           && GetThemeInt
           && GetThemePartSize
           && GetThemePosition
           && SetWindowTheme
           && IsThemeBackgroundPartiallyTransparent
          ) {
              DTRACE_PRINTLN("Loaded function pointers.\n");
              // We need to make sure we can load the Theme. This may not be
              // the case on a WinXP machine with classic mode enabled.
              HTHEME hTheme = OpenThemeData(AwtToolkit::GetInstance().GetHWnd(), L"Button");
              if(hTheme) {
                  DTRACE_PRINTLN("Loaded Theme data.\n");
                  CloseThemeData(hTheme);
                  return TRUE;
              }
            } else {
               FreeLibrary(hModThemes);
               hModThemes = NULL;
            }
    }
    return FALSE;
}

JNIEXPORT jboolean JNICALL Java_sun_awt_windows_ThemeReader_initThemes
(JNIEnv *env, jclass klass) {
    static BOOL TryLoadingThemeLib = FALSE;
    static BOOL Themed = FALSE;
    if (!TryLoadingThemeLib) {
        Themed = InitThemes();
        TryLoadingThemeLib = TRUE;
    }
    return JNI_IS_TRUE(Themed);
}



static void assert_result(HRESULT hres, JNIEnv *env) {
#ifdef _DEBUG
    if (hres != 0) {
        DWORD lastError = GetLastError();
        if (lastError != 0) {
            LPSTR msgBuffer = NULL;
            FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER |
                    FORMAT_MESSAGE_FROM_SYSTEM |
                    FORMAT_MESSAGE_IGNORE_INSERTS,
                    NULL,
                    lastError,
                    MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                    (LPSTR)&msgBuffer,
                    // it's an output parameter when allocate buffer is used
                    0,
                    NULL);
            DTRACE_PRINTLN3("Error: hres=0x%x lastError=0x%x %s\n", hres,
                                                lastError, msgBuffer);
        }
    }
#endif
}


/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    openTheme
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_windows_ThemeReader_openTheme
(JNIEnv *env, jclass klass, jstring widget) {

    LPCTSTR str = (LPCTSTR) JNU_GetStringPlatformChars(env, widget, NULL);
    if (str == NULL) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    }
    // We need to open the Theme on a Window that will stick around.
    // The best one for that purpose is the Toolkit window.
    HTHEME htheme = OpenThemeData(AwtToolkit::GetInstance().GetHWnd(), str);
    JNU_ReleaseStringPlatformChars(env, widget, str);
    return (jlong) htheme;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    setWindowTheme
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_ThemeReader_setWindowTheme
(JNIEnv *env, jclass klass, jstring subAppName) {

    LPCTSTR str = NULL;
    if (subAppName != NULL) {
        str = (LPCTSTR) JNU_GetStringPlatformChars(env, subAppName, NULL);
    }
    // We need to set the Window theme on the same theme that we opened it with.
    HRESULT hres = SetWindowTheme(AwtToolkit::GetInstance().GetHWnd(), str, NULL);
    assert_result(hres, env);
    if (subAppName != NULL) {
        JNU_ReleaseStringPlatformChars(env, subAppName, str);
    }
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    closeTheme
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_ThemeReader_closeTheme
(JNIEnv *env, jclass klass, jlong theme) {

    HRESULT hres = CloseThemeData((HTHEME)theme);
    assert_result(hres, env);
}

static void copyDIBToBufferedImage(int *pDstBits, int *pSrcBits,
                BOOL transparent, int w, int h, int stride) {

    int offsetToNextLine = stride - w;
    int *dst = pDstBits;
    int *src = pSrcBits;
    double alphaScale;
    int r,g,b,a;
    int pixel;

    BOOL translucent = FALSE;

    for (int i=0;i<h;i++) {
        for (int j=0;j<w;j++) {
            pixel = *src++;
            a = (pixel & ALPHA_MASK)  >> ALPHA_SHIFT;
            if ((a != 0) && (a != 255)) {
                translucent = TRUE;
                break;
            }
        }
        if (translucent) break;
    }
    src = pSrcBits;

    if (translucent) {
        for (int i=0;i<h;i++) {
            for (int j=0;j<w;j++) {
                pixel = *src++;
                if (pixel != 0) {
                    // The UxTheme API seems to do the blending and
                    // premultiply the resulting values.
                    // so we have to divide by the alpha to get the
                    // original component values.
                    a = (pixel & ALPHA_MASK)  >> ALPHA_SHIFT;
                    if ((a != 255) && (a != 0)) {
                        r = (pixel & RED_MASK)  >> RED_SHIFT;
                        g = (pixel & GREEN_MASK)  >> GREEN_SHIFT;
                        b = (pixel & BLUE_MASK);
                        alphaScale = 255.0 / a;
                        r = (int) ((double) r * alphaScale);
                        if (r > 255) r = 255;
                        g = (int) ((double) g * alphaScale);
                        if (g > 255) g = 255;
                        b = (int) ((double) b * alphaScale);
                        if (b > 255) b = 255;
                        pixel = (a << ALPHA_SHIFT) | (r << RED_SHIFT) |
                                                   (g << GREEN_SHIFT) | b ;
                    }
                    else {
                        // Frame maximize and minimize buttons
                        // have transparent pixels with alpha
                        // set to FF and nontransparent pixels have zero alpha.
                        pixel |= 0xFF000000;
                    }
                }
                *dst++ = pixel;
            }
            dst += offsetToNextLine;
        }
    }
    else if (transparent) {
         for (int i=0;i<h;i++) {
             for (int j=0;j<w;j++) {
                 pixel = *src++;
                 if (pixel == 0) {
                     *dst++ = 0;
                 }
                 else {
                     *dst++ = 0xFF000000 | pixel;
                 }
             }
             dst += offsetToNextLine;
         }
     }
     else {
         for (int i=0;i<h;i++) {
             for (int j=0;j<w;j++) {
                 pixel = *src++;
                 *dst++ = 0xFF000000 | pixel;
             }
             dst += offsetToNextLine;
         }
     }

}



/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    paintBackground
 * Signature: ([IJIIIIIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_ThemeReader_paintBackground
  (JNIEnv *env, jclass klass, jintArray array, jlong theme, jint part, jint state,
    jint x, jint y, jint w, jint h, jint stride) {

    int *pDstBits=NULL;
    int *pSrcBits=NULL;
    HDC memDC,defaultDC;
    HBITMAP hDibSection = NULL;
    RECT rect;
    BITMAPINFO bmi;
    HTHEME hTheme = (HTHEME) theme;

    DTRACE_PRINTLN3("Java_sun_awt_windows_ThemeReader_paintButtonBackground w=%d h=%d\n stride=%d\n",w,h,stride);

    if (hTheme == NULL) {
        JNU_ThrowInternalError(env, "HTHEME is null");
        return;
    }

    defaultDC = GetDC(NULL);

    memDC = CreateCompatibleDC(defaultDC);

    static const int BITS_PER_PIXEL = 32;

    ZeroMemory(&bmi,sizeof(BITMAPINFO));
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = w;
    bmi.bmiHeader.biHeight = -h;
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = BITS_PER_PIXEL;
    bmi.bmiHeader.biCompression = BI_RGB;
    bmi.bmiHeader.biSizeImage = w * h * (BITS_PER_PIXEL>>3);


    hDibSection = ::CreateDIBSection(memDC, (BITMAPINFO*) &bmi,
            DIB_RGB_COLORS, (void **) &pSrcBits,
            NULL, 0);
    if (hDibSection == NULL) {
        DTRACE_PRINTLN("Error creating DIB section");
        ReleaseDC(NULL,defaultDC);
        return;
    }

    SelectObject(memDC,hDibSection);

    rect.left = 0;
    rect.top = 0;
    rect.bottom = h;
    rect.right = w;

    ZeroMemory(pSrcBits,(BITS_PER_PIXEL>>3)*w*h);

    HRESULT hres = DrawThemeBackground(hTheme, memDC, part, state, &rect, NULL);
    assert_result(hres, env);
    if (SUCCEEDED(hres)) {
        // Make sure GDI is done.
        GdiFlush();
        // Copy the resulting pixels to our Java BufferedImage.
        pDstBits = (int *)env->GetPrimitiveArrayCritical(array, 0);
        BOOL transparent = FALSE;
        transparent = IsThemeBackgroundPartiallyTransparent(hTheme,part,state);
        copyDIBToBufferedImage(pDstBits, pSrcBits, transparent, w, h, stride);
        env->ReleasePrimitiveArrayCritical(array, pDstBits, 0);
    }

    // Delete resources.
    DeleteObject(hDibSection);
    DeleteDC(memDC);
    ReleaseDC(NULL,defaultDC);
}

jobject newInsets(JNIEnv *env, jint top, jint left, jint bottom, jint right) {
    if (env->EnsureLocalCapacity(2) < 0) {
        return NULL;
    }

    static jclass insetsClassID = NULL;

    if (insetsClassID == NULL) {
        jclass insetsClassIDLocal = env->FindClass("java/awt/Insets");
        CHECK_NULL_RETURN(insetsClassIDLocal, NULL);
        insetsClassID = (jclass)env->NewGlobalRef(insetsClassIDLocal);
        env->DeleteLocalRef(insetsClassIDLocal);
    }

    jobject insets = env->NewObject(insetsClassID,
        AwtToolkit::insetsMID,
        top, left, bottom, right);

    if (safe_ExceptionOccurred(env)) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    return insets;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getThemeMargins
 * Signature: (JIII)Ljava/awt/Insets;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_windows_ThemeReader_getThemeMargins
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state, jint property) {
    MARGINS margins;
    HTHEME hTheme = (HTHEME) theme;

    if (hTheme != NULL) {
        HRESULT hres = GetThemeMargins(hTheme, NULL, part, state, property, NULL, &margins);
        assert_result(hres, env);
        if (FAILED(hres)) {
            return NULL;
        }

        return newInsets(env,
                margins.cyTopHeight,
                margins.cxLeftWidth, margins.cyBottomHeight, margins.cxRightWidth);
    }
    return NULL;
}

/*
 * Class: sun_awt_windows_ThemeReader
 * Method: isThemePartDefined
 * Signature: (JII)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_windows_ThemeReader_isThemePartDefined
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state) {
    HTHEME hTheme = (HTHEME) theme;
    return JNI_IS_TRUE(IsThemePartDefined(hTheme, part, state));
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getColor
 * Signature: (JIII)Ljava/awt/Color;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_windows_ThemeReader_getColor
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state, jint type) {

    HTHEME hTheme = (HTHEME) theme;

    if (hTheme != NULL) {
        COLORREF color=0;

        if (GetThemeColor(hTheme, part, state, type, &color) != S_OK) {
            return NULL;
        }

        if (env->EnsureLocalCapacity(1) < 0) {
            return NULL;
        }

        static jmethodID colorMID = NULL;
        static jclass colorClassID = NULL;

        if (colorClassID == NULL) {
            jclass colorClassIDLocal = env->FindClass("java/awt/Color");
            CHECK_NULL_RETURN(colorClassIDLocal, NULL);
            colorClassID = (jclass)env->NewGlobalRef(colorClassIDLocal);
            env->DeleteLocalRef(colorClassIDLocal);
        }

        if (colorMID == NULL) {
            colorMID = env->GetMethodID(colorClassID, "<init>", "(III)V");
            CHECK_NULL_RETURN(colorMID, NULL);
        }
        jobject colorObj = env->NewObject(colorClassID,
                colorMID, GetRValue(color), GetGValue(color),GetBValue(color));

        if (safe_ExceptionOccurred(env)) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        return colorObj;
    }
    return NULL;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getInt
 * Signature: (JIII)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_windows_ThemeReader_getInt
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state, jint prop) {

    HTHEME hTheme = (HTHEME) theme;
    int retVal = -1;
    if (hTheme != NULL) {
        HRESULT hres = GetThemeInt(hTheme, part, state, prop, &retVal);
        assert_result(hres, env);
    }
    return retVal;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getEnum
 * Signature: (JIII)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_windows_ThemeReader_getEnum
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state, jint prop) {
    HTHEME hTheme = (HTHEME) theme;
    int retVal = -1;
    if (hTheme != NULL) {
        HRESULT hres = GetThemeEnumValue(hTheme, part, state, prop, &retVal);
        assert_result(hres, env);
    }
    return retVal;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getBoolean
 * Signature: (JIII)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_windows_ThemeReader_getBoolean
(JNIEnv *env, jclass klass, jlong  theme, jint part, jint state, jint prop) {
    HTHEME hTheme = (HTHEME) theme;
    BOOL retVal = FALSE;
    if (hTheme != NULL) {
        HRESULT hres = GetThemeBool(hTheme, part, state, prop, &retVal);
        assert_result(hres, env);
    }
    return JNI_IS_TRUE(retVal);
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getSysBoolean
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_windows_ThemeReader_getSysBoolean
(JNIEnv *env, jclass klass, jlong  theme, jint prop) {
    HTHEME hTheme = (HTHEME)theme;
    if (hTheme != NULL) {
        return JNI_IS_TRUE(GetThemeSysBool(hTheme, prop));
    }
    return JNI_FALSE;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getPoint
 * Signature: (JIII)Ljava/awt/Point;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_windows_ThemeReader_getPoint
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state, jint prop) {
    HTHEME hTheme = (HTHEME) theme;
    POINT point;

    if (hTheme != NULL) {
        if (GetThemePosition(hTheme, part, state, prop, &point) != S_OK) {
            return NULL;
        }

        if (env->EnsureLocalCapacity(2) < 0) {
            return NULL;
        }

        static jmethodID pointMID = NULL;
        static jclass pointClassID = NULL;

        if (pointClassID == NULL) {
            jclass pointClassIDLocal = env->FindClass("java/awt/Point");
            CHECK_NULL_RETURN(pointClassIDLocal, NULL);
            pointClassID = (jclass)env->NewGlobalRef(pointClassIDLocal);
            env->DeleteLocalRef(pointClassIDLocal);
        }

        if (pointMID == NULL) {
            pointMID = env->GetMethodID(pointClassID, "<init>", "(II)V");
            CHECK_NULL_RETURN(pointMID, NULL);
        }
        jobject pointObj = env->NewObject(pointClassID, pointMID, point.x, point.y);

        if (safe_ExceptionOccurred(env)) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        return pointObj;
    }
    return NULL;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getPosition
 * Signature: (JIII)Ljava/awt/Dimension;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_windows_ThemeReader_getPosition
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state, jint prop) {

    HTHEME hTheme = (HTHEME) theme;
    if (hTheme != NULL) {

        POINT point;

        HRESULT hres = GetThemePosition(hTheme, part, state, prop, &point);
        assert_result(hres, env);
        if (FAILED(hres)) {
            return NULL;
        }


        if (env->EnsureLocalCapacity(2) < 0) {
            return NULL;
        }

        static jmethodID dimMID = NULL;
        static jclass dimClassID = NULL;
        if (dimClassID == NULL) {
            jclass dimClassIDLocal = env->FindClass("java/awt/Dimension");
            CHECK_NULL_RETURN(dimClassIDLocal, NULL);
            dimClassID = (jclass)env->NewGlobalRef(dimClassIDLocal);
            env->DeleteLocalRef(dimClassIDLocal);
        }
        if (dimMID == NULL) {
            dimMID = env->GetMethodID(dimClassID, "<init>", "(II)V");
            CHECK_NULL_RETURN(dimMID, NULL);
        }
        jobject dimObj = env->NewObject(dimClassID, dimMID, point.x, point.y);

        if (safe_ExceptionOccurred(env)) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        return dimObj;
    }
    return NULL;
}

void rescale(SIZE *size) {
    static int dpiX = -1;
    static int dpiY = -1;
    if (dpiX == -1 || dpiY == -1) {
        HWND hWnd = ::GetDesktopWindow();
        HDC hDC = ::GetDC(hWnd);
        dpiX = ::GetDeviceCaps(hDC, LOGPIXELSX);
        dpiY = ::GetDeviceCaps(hDC, LOGPIXELSY);
        ::ReleaseDC(hWnd, hDC);
    }

    if (dpiX !=0 && dpiX != 96) {
        float invScaleX = 96.0f / dpiX;
        size->cx = ROUND_TO_INT(size->cx * invScaleX);
    }
    if (dpiY != 0 && dpiY != 96) {
        float invScaleY = 96.0f / dpiY;
        size->cy = ROUND_TO_INT(size->cy * invScaleY);
    }
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getPartSize
 * Signature: (JII)Ljava/awt/Dimension;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_windows_ThemeReader_getPartSize
(JNIEnv *env, jclass klass, jlong theme, jint part, jint state) {
    if (theme != NULL) {
        SIZE size;

        if (SUCCEEDED(GetThemePartSize((HTHEME)theme, NULL, part, state,
           NULL, TS_TRUE, &size)) && (env->EnsureLocalCapacity(2) >= 0)) {

            static jmethodID dimMID = NULL;
            static jclass dimClassID = NULL;
            if (dimClassID == NULL) {
                jclass dimClassIDLocal = env->FindClass("java/awt/Dimension");
                CHECK_NULL_RETURN(dimClassIDLocal, NULL);
                dimClassID = (jclass)env->NewGlobalRef(dimClassIDLocal);
                env->DeleteLocalRef(dimClassIDLocal);
            }
            if (dimMID == NULL) {
                dimMID = env->GetMethodID(dimClassID, "<init>", "(II)V");
                CHECK_NULL_RETURN(dimMID, NULL);
            }

            rescale(&size);
            jobject dimObj = env->NewObject(dimClassID, dimMID, size.cx, size.cy);
            if (safe_ExceptionOccurred(env)) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            }

            return dimObj;
        }
    }
    return NULL;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getThemeBackgroundContentMargins
 * Signature: (JIIII)Ljava/awt/Insets;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_windows_ThemeReader_getThemeBackgroundContentMargins
(JNIEnv *env, jclass klass, jlong hTheme, jint part, jint state,
jint boundingWidth, jint boundingHeight) {
    if (hTheme != NULL) {
        RECT boundingRect;
        boundingRect.left = 0;
        boundingRect.top = 0;
        boundingRect.right = boundingWidth;
        boundingRect.bottom = boundingHeight;
        RECT contentRect;
        if (SUCCEEDED(GetThemeBackgroundContentRect((HTHEME) hTheme, NULL, part,
                                                    state, &boundingRect,
                                                    &contentRect))) {
            return newInsets(env,
                             contentRect.top, contentRect.left,
                             boundingHeight - contentRect.bottom,
                             boundingWidth - contentRect.right);
        }
    }
    return NULL;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    getThemeTransitionDuration
 * Signature: (JIIII)J
 */
JNIEXPORT jlong JNICALL
Java_sun_awt_windows_ThemeReader_getThemeTransitionDuration
(JNIEnv *env, jclass klass, jlong theme, jint part, jint stateFrom,
 jint stateTo, jint propId) {
    jlong rv = -1;
    if (GetThemeTransitionDuration != NULL) {
        DWORD duration = 0;
        if (SUCCEEDED(GetThemeTransitionDuration((HTHEME) theme, part,
                                      stateFrom, stateTo, propId, &duration))) {
            rv = duration;
        }
    }
    return rv;
}

/*
 * Class:     sun_awt_windows_ThemeReader
 * Method:    isGetThemeTransitionDurationDefined
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_windows_ThemeReader_isGetThemeTransitionDurationDefined
(JNIEnv *env, jclass klass) {
    return (GetThemeTransitionDuration != NULL) ? JNI_TRUE : JNI_FALSE;
}
