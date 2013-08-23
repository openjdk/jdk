/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include "jlong.h"
#include "awt_Cursor.h"
#include "awt_Component.h"
#include "awt_Container.h"
#include "awt_IconCursor.h"
#include "awt_Toolkit.h"
#include "awt_Window.h"
#include <java_awt_Cursor.h>
#include <sun_awt_windows_WCustomCursor.h>
#include <sun_awt_windows_WGlobalCursorManager.h>


/************************************************************************
 * AwtCursor fields
 */
jmethodID AwtCursor::mSetPDataID;
jfieldID AwtCursor::pDataID;
jfieldID AwtCursor::typeID;

jfieldID AwtCursor::pointXID;
jfieldID AwtCursor::pointYID;

jclass AwtCursor::globalCursorManagerClass;
jmethodID AwtCursor::updateCursorID;

AwtObjectList AwtCursor::customCursors;


AwtCursor::AwtCursor(JNIEnv *env, HCURSOR hCur, jobject jCur)
{
    hCursor = hCur;
    jCursor = env->NewWeakGlobalRef(jCur);

    xHotSpot = yHotSpot = nWidth = nHeight = nSS = 0;
    cols = NULL;
    mask = NULL;

    custom = dirty = FALSE;
}

AwtCursor::AwtCursor(JNIEnv *env, HCURSOR hCur, jobject jCur, int xH, int yH,
                     int nWid, int nHgt, int nS, int *col, BYTE *hM)
{
    hCursor = hCur;
    jCursor = env->NewWeakGlobalRef(jCur);

    xHotSpot = xH;
    yHotSpot = yH;
    nWidth = nWid;
    nHeight = nHgt;
    nSS = nS;
    cols = col;
    mask = hM;

    custom = TRUE;
    dirty = FALSE;
}

AwtCursor::~AwtCursor()
{
}

void AwtCursor::Dispose()
{
    delete[] mask;
    delete[] cols;

    if (custom) {
        ::DestroyIcon(hCursor);
    }

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject localObj = env->NewLocalRef(jCursor);
    if (localObj != NULL) {
        setPData(localObj, ptr_to_jlong(NULL));
        env->DeleteLocalRef(localObj);
    }
    env->DeleteWeakGlobalRef(jCursor);

    AwtObject::Dispose();
}

AwtCursor * AwtCursor::CreateSystemCursor(jobject jCursor)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jint type = env->GetIntField(jCursor, AwtCursor::typeID);
    DASSERT(type != java_awt_Cursor_CUSTOM_CURSOR);

    LPCTSTR winCursor;
    switch (type) {
      case java_awt_Cursor_DEFAULT_CURSOR:
      default:
        winCursor = IDC_ARROW;
        break;
      case java_awt_Cursor_CROSSHAIR_CURSOR:
        winCursor = IDC_CROSS;
        break;
      case java_awt_Cursor_TEXT_CURSOR:
        winCursor = IDC_IBEAM;
        break;
      case java_awt_Cursor_WAIT_CURSOR:
        winCursor = IDC_WAIT;
        break;
      case java_awt_Cursor_NE_RESIZE_CURSOR:
      case java_awt_Cursor_SW_RESIZE_CURSOR:
        winCursor = IDC_SIZENESW;
        break;
      case java_awt_Cursor_SE_RESIZE_CURSOR:
      case java_awt_Cursor_NW_RESIZE_CURSOR:
        winCursor = IDC_SIZENWSE;
        break;
      case java_awt_Cursor_N_RESIZE_CURSOR:
      case java_awt_Cursor_S_RESIZE_CURSOR:
        winCursor = IDC_SIZENS;
        break;
      case java_awt_Cursor_W_RESIZE_CURSOR:
      case java_awt_Cursor_E_RESIZE_CURSOR:
        winCursor = IDC_SIZEWE;
        break;
      case java_awt_Cursor_HAND_CURSOR:
        winCursor = TEXT("HAND_CURSOR");
        break;
      case java_awt_Cursor_MOVE_CURSOR:
        winCursor = IDC_SIZEALL;
        break;
    }
    HCURSOR hCursor = ::LoadCursor(NULL, winCursor);
    if (hCursor == NULL) {
        /* Not a system cursor, check for resource. */
        hCursor = ::LoadCursor(AwtToolkit::GetInstance().GetModuleHandle(),
                               winCursor);
    }
    if (hCursor == NULL) {
        hCursor = ::LoadCursor(NULL, IDC_ARROW);
        DASSERT(hCursor != NULL);
    }

    AwtCursor *awtCursor = new AwtCursor(env, hCursor, jCursor);
    setPData(jCursor, ptr_to_jlong(awtCursor));

    return awtCursor;
}

HCURSOR  AwtCursor::GetCursor(JNIEnv *env, AwtComponent *comp) {
    jlong  pData ;

    if (comp == NULL) {
        return NULL;
    }
    if (env->EnsureLocalCapacity(2) < 0) {
        return NULL;
    }
    jobject jcomp = comp->GetTarget(env);
    if (jcomp == NULL)
        return NULL;
    jobject jcurs = env->GetObjectField (jcomp, AwtComponent::cursorID);

    if (jcurs != NULL) {
        pData = env->GetLongField(jcurs, AwtCursor::pDataID);
        AwtCursor *awtCursor = (AwtCursor *)jlong_to_ptr(pData);

        env->DeleteLocalRef(jcomp);
        env->DeleteLocalRef(jcurs);

        if (awtCursor == NULL) {
            return NULL;
        }
        return awtCursor->GetHCursor();

    } else {
        env->DeleteLocalRef(jcomp);
    }

    //if component's cursor is null, get the parent's cursor
    AwtComponent *parent = comp->GetParent() ;

    return AwtCursor::GetCursor(env, parent);
}

void AwtCursor::UpdateCursor(AwtComponent *comp) {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    if (env->EnsureLocalCapacity(1) < 0) {
        return;
    }
    jobject jcomp = comp->GetTarget(env);

    //4372119:Disappearing of busy cursor on JDK 1.3
    HWND captureWnd = GetCapture();
    if ( !AwtComponent::isMenuLoopActive() &&
        (captureWnd==NULL || captureWnd==comp->GetHWnd()))
    {
        if (IsWindow(AwtWindow::GetModalBlocker(
                                AwtComponent::GetTopLevelParentForWindow(
                                comp->GetHWnd()))))
        {
            static HCURSOR hArrowCursor = LoadCursor(NULL, IDC_ARROW);
            SetCursor(hArrowCursor);
        } else {
            HCURSOR cur = comp->getCursorCache();
            if (cur == NULL) {
                cur = GetCursor(env , comp);
            }
            if (cur != NULL) {
                ::SetCursor(cur);
            }

            if (AwtCursor::updateCursorID == NULL) {
                jclass cls =
                    env->FindClass("sun/awt/windows/WGlobalCursorManager");
                AwtCursor::globalCursorManagerClass =
                    (jclass)env->NewGlobalRef(cls);
                AwtCursor::updateCursorID =
                    env->GetStaticMethodID(cls, "nativeUpdateCursor",
                    "(Ljava/awt/Component;)V");
                DASSERT(AwtCursor::globalCursorManagerClass != NULL);
                DASSERT(AwtCursor::updateCursorID != NULL);
            }

            env->CallStaticVoidMethod(AwtCursor::globalCursorManagerClass,
                AwtCursor::updateCursorID, jcomp);
        }
    }
    env->DeleteLocalRef(jcomp);
}

void AwtCursor::Rebuild() {
    if (!dirty) {
        return;
    }

    ::DestroyIcon(hCursor);
    hCursor = NULL;

    HBITMAP hMask = ::CreateBitmap(nWidth, nHeight, 1, 1, mask);
    HBITMAP hColor = create_BMP(NULL, cols, nSS, nWidth, nHeight);
    if (hMask && hColor) {
        ICONINFO icnInfo;
        memset(&icnInfo, 0, sizeof(ICONINFO));
        icnInfo.hbmMask = hMask;
        icnInfo.hbmColor = hColor;
        icnInfo.fIcon = FALSE;
        icnInfo.xHotspot = xHotSpot;
        icnInfo.yHotspot = yHotSpot;

        hCursor = ::CreateIconIndirect(&icnInfo);

        destroy_BMP(hColor);
        destroy_BMP(hMask);
    }
    DASSERT(hCursor);
    dirty = FALSE;
}

extern "C" {

/************************************************************************
 * AwtCursor methods
 */

/*
 * Class:     jave_awt_Cursor
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_awt_Cursor_initIDs(JNIEnv *env, jclass cls)
{
    TRY;

    AwtCursor::mSetPDataID = env->GetMethodID(cls, "setPData", "(J)V");
    AwtCursor::pDataID = env->GetFieldID(cls, "pData", "J");
    AwtCursor::typeID = env->GetFieldID(cls, "type", "I");
    DASSERT(AwtCursor::pDataID != NULL);
    DASSERT(AwtCursor::typeID != NULL);

    cls = env->FindClass("java/awt/Point");
    AwtCursor::pointXID = env->GetFieldID(cls, "x", "I");
    AwtCursor::pointYID = env->GetFieldID(cls, "y", "I");
    DASSERT(AwtCursor::pointXID != NULL);
    DASSERT(AwtCursor::pointYID != NULL);

    AwtCursor::updateCursorID = NULL;

    CATCH_BAD_ALLOC;
}

/*
 * Class:     java_awt_Cursor
 * Method:    finalizeImpl
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_awt_Cursor_finalizeImpl(JNIEnv *env, jclass clazz, jlong pData)
{
    TRY_NO_VERIFY;

    AwtObject::_Dispose((PDATA)pData);

    CATCH_BAD_ALLOC;
}

/************************************************************************
 * WCustomCursor native methods
 */

JNIEXPORT void JNICALL
Java_sun_awt_windows_WCustomCursor_createCursorIndirect(
    JNIEnv *env, jobject self, jintArray intRasterData, jbyteArray andMask,
    jint nSS, jint nW, jint nH, jint xHotSpot, jint yHotSpot)
{
    TRY;

    JNI_CHECK_NULL_RETURN(intRasterData, "intRasterData argument");

    if (nW != ::GetSystemMetrics(SM_CXCURSOR) ||
        nH != ::GetSystemMetrics(SM_CYCURSOR)) {
        JNU_ThrowArrayIndexOutOfBoundsException(env,
                                                "bad width and/or height");
        return;
    }

    jsize length = env->GetArrayLength(andMask);
    jbyte *andMaskPtr = new jbyte[length]; // safe because sizeof(jbyte)==1
    env->GetByteArrayRegion(andMask, 0, length, andMaskPtr);

    HBITMAP hMask = ::CreateBitmap(nW, nH, 1, 1, (BYTE *)andMaskPtr);
    ::GdiFlush();

    int *cols = SAFE_SIZE_NEW_ARRAY2(int, nW, nH);

    jint *intRasterDataPtr = NULL;
    HBITMAP hColor = NULL;
    try {
        intRasterDataPtr =
            (jint *)env->GetPrimitiveArrayCritical(intRasterData, 0);
        hColor = create_BMP(NULL, (int *)intRasterDataPtr, nSS, nW, nH);
        memcpy(cols, intRasterDataPtr, nW*nH*sizeof(int));
    } catch (...) {
        if (intRasterDataPtr != NULL) {
            env->ReleasePrimitiveArrayCritical(intRasterData,
                                               intRasterDataPtr, 0);
        }
        throw;
    }

    env->ReleasePrimitiveArrayCritical(intRasterData, intRasterDataPtr, 0);
    intRasterDataPtr = NULL;

    HCURSOR hCursor = NULL;

    if (hMask && hColor) {
        ICONINFO icnInfo;
        memset(&icnInfo, 0, sizeof(ICONINFO));
        icnInfo.hbmMask = hMask;
        icnInfo.hbmColor = hColor;
        icnInfo.fIcon = FALSE;
        icnInfo.xHotspot = xHotSpot;
        icnInfo.yHotspot = yHotSpot;

        hCursor = ::CreateIconIndirect(&icnInfo);

        destroy_BMP(hColor);
        destroy_BMP(hMask);
    }

    DASSERT(hCursor);

    try {
        AwtCursor::setPData(self, ptr_to_jlong(new AwtCursor(env, hCursor, self, xHotSpot,
                                                             yHotSpot, nW, nH, nSS, cols,
                                                             (BYTE *)andMaskPtr)));
    } catch (...) {
        if (cols) {
            delete[] cols;
        }
        throw;
    }
    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WCustomCursor
 * Method:    getCursorWidth
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_windows_WCustomCursor_getCursorWidth(JNIEnv *, jclass)
{
    TRY;

    DTRACE_PRINTLN("WCustomCursor.getCursorWidth()");
    return (jint)::GetSystemMetrics(SM_CXCURSOR);

    CATCH_BAD_ALLOC_RET(0);
}

/*
 * Class:     sun_awt_windows_WCustomCursor
 * Method:    getCursorHeight
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_windows_WCustomCursor_getCursorHeight(JNIEnv *, jclass)
{
    TRY;

    DTRACE_PRINTLN("WCustomCursor.getCursorHeight()");
    return (jint)::GetSystemMetrics(SM_CYCURSOR);

    CATCH_BAD_ALLOC_RET(0);
}

/************************************************************************
 * WGlobalCursorManager native methods
 */

/*
 * Class:     sun_awt_windows_WGlobalCursorManager
 * Method:    getCursorPos
 * Signature: (Ljava/awt/Point;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WGlobalCursorManager_getCursorPos(JNIEnv *env,
                                                       jobject,
                                                       jobject point)
{
    TRY;

    POINT p;
    ::GetCursorPos(&p);
    env->SetIntField(point, AwtCursor::pointXID, (jint)p.x);
    env->SetIntField(point, AwtCursor::pointYID, (jint)p.y);

    CATCH_BAD_ALLOC;
}

struct GlobalSetCursorStruct {
    jobject cursor;
    jboolean u;
};

static void GlobalSetCursor(void* pStruct) {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject cursor  = ((GlobalSetCursorStruct*)pStruct)->cursor;
    jboolean u      = ((GlobalSetCursorStruct*)pStruct)->u;
    jlong pData = env->GetLongField(cursor, AwtCursor::pDataID);
    AwtCursor *awtCursor = (AwtCursor *)jlong_to_ptr(pData);

    if (awtCursor == NULL) {
        awtCursor = AwtCursor::CreateSystemCursor(cursor);
    }

    HCURSOR hCursor = awtCursor->GetHCursor();

    BOOL blocked = false;
    if (jobject jcomp = AwtComponent::FindHeavyweightUnderCursor(u)) {
        if(jobject jpeer = AwtObject::GetPeerForTarget(env, jcomp))
        {
            if(AwtComponent *awtComponent = (AwtComponent*)JNI_GET_PDATA(jpeer)) {
                blocked = ::IsWindow(AwtWindow::GetModalBlocker(
                                    AwtComponent::GetTopLevelParentForWindow(
                                    awtComponent->GetHWnd())));
                if (!blocked) {
                    awtComponent->setCursorCache(hCursor);
                }
            }
            env->DeleteLocalRef(jpeer);
        }
        env->DeleteGlobalRef(jcomp);
    }

    if (!blocked) {
        ::SetCursor(hCursor); // don't need WM_AWT_SETCURSOR
    }

    env->DeleteGlobalRef(((GlobalSetCursorStruct*)pStruct)->cursor);
}

/*
 * Class:     sun_awt_windows_WGlobalCursorManager
 * Method:    setCursor
 * Signature: (Ljava/awt/Component;Ljava/awt/Cursor;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WGlobalCursorManager_setCursor(JNIEnv *env, jobject,
                            jobject, jobject cursor, jboolean u)
{
    TRY;

    if (cursor != NULL) {  // fix for 4430302 - getCursor() returns NULL
        GlobalSetCursorStruct data;
        data.cursor = env->NewGlobalRef(cursor);
        data.u = u;
        AwtToolkit::GetInstance().InvokeFunction(
               GlobalSetCursor,
               (void *)&data);
    } else {
        JNU_ThrowNullPointerException(env, "NullPointerException");
    }
    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WGlobalCursorManager
 * Method:    findHeavyweight
 * Signature: (II)Z
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_windows_WGlobalCursorManager_findHeavyweightUnderCursor(
    JNIEnv *env, jobject, jboolean useCache)
{
    TRY;

    if (env->EnsureLocalCapacity(1) < 0) {
        return NULL;
    }

    jobject globalRef = (jobject)AwtToolkit::GetInstance().
        InvokeFunction((void*(*)(void*))
                       AwtComponent::FindHeavyweightUnderCursor,
                       (void *)useCache);
    jobject localRef = env->NewLocalRef(globalRef);
    env->DeleteGlobalRef(globalRef);
    return localRef;

    CATCH_BAD_ALLOC_RET(NULL);
}

/*
 * Class:     sun_awt_windows_WGlobalCursorManager
 * Method:    findComponentAt
 * Signature: (L/java/awt/Container;II)L/java/awt/Component
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_windows_WGlobalCursorManager_findComponentAt(
    JNIEnv *env, jobject, jobject container, jint x, jint y)
{
    TRY;

    /*
     * Call private version of Container.findComponentAt with the following
     * flag set -- ignoreEnabled = false (i.e., don't return or recur into
     * disabled Components);
     * NOTE: it may return a JRootPane's glass pane as the target Component
     */
    JNI_CHECK_NULL_RETURN_NULL(container, "null container");
    jobject comp =
        env->CallObjectMethod(container, AwtContainer::findComponentAtMID,
                              x, y, JNI_FALSE);
    return comp;

    CATCH_BAD_ALLOC_RET(NULL);
}

/*
 * Class:     sun_awt_windows_WGlobalCursorManager
 * Method:    getLocationOnScreen
 * Signature: (L/java/awt/Component;)L/java/awt/Point
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_windows_WGlobalCursorManager_getLocationOnScreen(
    JNIEnv *env, jobject, jobject component)
{
    TRY;

    JNI_CHECK_NULL_RETURN_NULL(component, "null component");
    jobject point =
        env->CallObjectMethod(component, AwtComponent::getLocationOnScreenMID);
    return point;

    CATCH_BAD_ALLOC_RET(NULL);
}

} /* extern "C" */
