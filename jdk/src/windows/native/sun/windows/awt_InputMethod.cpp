/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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
#include "awtmsg.h"
#include "awt_AWTEvent.h"
#include "awt_Component.h"
#include "awt_Toolkit.h"
#include "locale_str.h"
#include <sun_awt_windows_WInputMethod.h>
#include <sun_awt_windows_WInputMethodDescriptor.h>
#include <java_awt_event_InputMethodEvent.h>

const UINT SYSCOMMAND_IMM = 0xF000 - 100;

/************************************************************************
 * WInputMethod native methods
 */

extern "C" {

jobject CreateLocaleObject(JNIEnv *env, const char * name);
HKL getDefaultKeyboardLayout();

extern BOOL g_bUserHasChangedInputLang;

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    createNativeContext
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_windows_WInputMethod_createNativeContext(JNIEnv *env, jobject self)
{
    TRY;

    // use special message to call ImmCreateContext() in main thread.
    return (jint)AwtToolkit::GetInstance().SendMessage(WM_AWT_CREATECONTEXT);

    CATCH_BAD_ALLOC_RET(0);
}


/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    destroyNativeContext
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WInputMethod_destroyNativeContext(JNIEnv *env, jobject self, jint context)
{
    TRY_NO_VERIFY;

    // use special message to call ImmDestroyContext() in main thread.
    AwtToolkit::GetInstance().SendMessage(WM_AWT_DESTROYCONTEXT, context, 0);

    CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    enableNativeIME
 * Signature: (Lsun/awt/windows/WComponentPeer;I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WInputMethod_enableNativeIME(JNIEnv *env, jobject self, jobject peer,
                                                  jint context, jboolean useNativeCompWindow)
{
    TRY;

    jobject selfGlobalRef = env->NewGlobalRef(self);
    jobject peerGlobalRef = env->NewGlobalRef(peer);

    EnableNativeIMEStruct *enis = new EnableNativeIMEStruct;

    enis->self = selfGlobalRef;
    enis->peer = peerGlobalRef;
    enis->context = context;
    enis->useNativeCompWindow = useNativeCompWindow;

    AwtToolkit::GetInstance().SendMessage(WM_AWT_ASSOCIATECONTEXT,
                                          reinterpret_cast<WPARAM>(enis), (LPARAM)0);
    // global refs are deleted in message handler

    CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    disableNativeIME
 * Signature: (Lsun/awt/windows/WComponentPeer;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WInputMethod_disableNativeIME(JNIEnv *env, jobject self, jobject peer)
{
    TRY_NO_VERIFY;

    jobject peerGlobalRef = env->NewGlobalRef(peer);
    // self reference is not used

    EnableNativeIMEStruct *enis = new EnableNativeIMEStruct;
    enis->self = NULL;
    enis->peer = peerGlobalRef;
    enis->context = NULL;
    enis->useNativeCompWindow = JNI_TRUE;

    AwtToolkit::GetInstance().SendMessage(WM_AWT_ASSOCIATECONTEXT,
                                          reinterpret_cast<WPARAM>(enis), (LPARAM)0);
    // global refs are deleted in message handler

    CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_windows_WComponentPeer
 * Method:    handleEvent
 * Signature: (Lsun/awt/windows/WComponentPeer;Ljava/awt/AWTEvent;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WInputMethod_handleNativeIMEEvent(JNIEnv *env, jobject self,
                                                       jobject peer, jobject event)
{
    TRY;

    PDATA pData;
    JNI_CHECK_PEER_RETURN(peer);
    AwtComponent* p = (AwtComponent *)pData;

    JNI_CHECK_NULL_RETURN(event, "null AWTEvent");
    if (env->EnsureLocalCapacity(1) < 0) {
        return;
    }
    jbyteArray bdata = (jbyteArray)(env)->GetObjectField(event, AwtAWTEvent::bdataID);
    if (bdata == 0) {
        return;
    }
    MSG msg;
    (env)->GetByteArrayRegion(bdata, 0, sizeof(MSG), (jbyte *)&msg);
    (env)->DeleteLocalRef(bdata);
    BOOL isConsumed =
      (BOOL)(env)->GetBooleanField(event, AwtAWTEvent::consumedID);
    int id = (env)->GetIntField(event, AwtAWTEvent::idID);
    DASSERT(!safe_ExceptionOccurred(env));

    if (isConsumed || p==NULL)  return;

    if (id >= java_awt_event_InputMethodEvent_INPUT_METHOD_FIRST &&
        id <= java_awt_event_InputMethodEvent_INPUT_METHOD_LAST)
    {
        jobject peerGlobalRef = env->NewGlobalRef(peer);

        // use special message to access pData on the toolkit thread
        AwtToolkit::GetInstance().SendMessage(WM_AWT_HANDLE_NATIVE_IME_EVENT,
                                              reinterpret_cast<WPARAM>(peerGlobalRef),
                                              reinterpret_cast<LPARAM>(&msg));
        // global ref is deleted in message handler

        (env)->SetBooleanField(event, AwtAWTEvent::consumedID, JNI_TRUE);
    }

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    notifyNativeIME
 * Signature: (Lsun/awt/windows/WComponentPeer;I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WInputMethod_endCompositionNative(JNIEnv *env, jobject self,
                                                       jint context, jboolean flag)
{
    TRY;

    // TODO: currently the flag parameter is ignored and the outstanding input is
    //       always discarded.
    //       If the flag value is Java_sun_awt_windows_WInputMethod_COMMIT_INPUT,
    //       then input text should be committed. Otherwise, should be discarded.
    //
    // 10/29/98 - Changed to commit it according to the flag.

    // use special message to call ImmNotifyIME() in main thread.
    AwtToolkit::GetInstance().SendMessage(WM_AWT_ENDCOMPOSITION, context,
        (LPARAM)(flag != sun_awt_windows_WInputMethod_DISCARD_INPUT));

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    setConversionStatus
 * Signature: (II)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WInputMethod_setConversionStatus(JNIEnv *env, jobject self, jint context, jint request)
{
    TRY;

    // use special message to call ImmSetConversionStatus() in main thread.
    AwtToolkit::GetInstance().SendMessage(WM_AWT_SETCONVERSIONSTATUS,
                                          context,
                                          MAKELPARAM((WORD)request, (WORD)0));

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    getConversionStatus
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_windows_WInputMethod_getConversionStatus(JNIEnv *env, jobject self, jint context)
{
    TRY;

    // use special message to call ImmSetConversionStatus() in main thread.
    return (jint) AwtToolkit::GetInstance().SendMessage(
        WM_AWT_GETCONVERSIONSTATUS, context, 0);

    CATCH_BAD_ALLOC_RET(0);
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    setOpenStatus
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WInputMethod_setOpenStatus(JNIEnv *env, jobject self, jint context, jboolean flag)
{
    TRY;

    // use special message to call ImmSetConversionStatus() in main thread.
    AwtToolkit::GetInstance().SendMessage(WM_AWT_SETOPENSTATUS,
                                          context, flag);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    getConversionStatus
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_windows_WInputMethod_getOpenStatus(JNIEnv *env, jobject self, jint context)
{
    TRY;

    // use special message to call ImmSetConversionStatus() in main thread.
    return (jboolean)(AwtToolkit::GetInstance().SendMessage(
                                                       WM_AWT_GETOPENSTATUS,
                                                       context, 0));
    CATCH_BAD_ALLOC_RET(0);
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    getNativeLocale
 * Signature: ()Ljava/util/Locale;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_windows_WInputMethod_getNativeLocale
  (JNIEnv *env, jclass cls)
{
    TRY;

    const char * javaLocaleName = getJavaIDFromLangID(AwtComponent::GetInputLanguage());
    if (javaLocaleName != NULL) {
        // Now WInputMethod.currentLocale and AwtComponent::m_idLang are get sync'ed,
        // so we can reset this flag.
        g_bUserHasChangedInputLang = FALSE;

        return CreateLocaleObject(env, javaLocaleName);
    } else {
        return NULL;
    }

    CATCH_BAD_ALLOC_RET(NULL);
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    setNativeLocale
 * Signature: (Ljava/lang/String;Z)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_windows_WInputMethod_setNativeLocale
  (JNIEnv *env, jclass cls, jstring localeString, jboolean onActivate)
{
    TRY;

    // check if current language ID is the requested one.  Note that the
    // current language ID (returned from 'getJavaIDFromLangID') is in
    // ASCII encoding, so we use 'GetStringUTFChars' to retrieve requested
    // language ID from the 'localeString' object.
    const char * current = getJavaIDFromLangID(AwtComponent::GetInputLanguage());
    jboolean isCopy;
    const char * requested = env->GetStringUTFChars(localeString, &isCopy);
    if ((current != NULL) && (strcmp(current, requested) == 0)) {
        env->ReleaseStringUTFChars(localeString, requested);
        return JNI_TRUE;
    }

    // get list of available HKLs.  Adding the user's preferred layout on top of the layout
    // list which is returned by GetKeyboardLayoutList ensures to match first when
    // looking up suitable layout.
    int layoutCount = ::GetKeyboardLayoutList(0, NULL) + 1;  // +1 for user's preferred HKL
    HKL FAR * hKLList = (HKL FAR *)safe_Malloc(sizeof(HKL)*layoutCount);
    DASSERT(!safe_ExceptionOccurred(env));
    ::GetKeyboardLayoutList(layoutCount - 1, &(hKLList[1]));
    hKLList[0] = getDefaultKeyboardLayout(); // put user's preferred layout on top of the list

    // lookup matching LangID
    jboolean retValue = JNI_FALSE;
    for (int i = 0; i < layoutCount; i++) {
        const char * supported = getJavaIDFromLangID(LOWORD(hKLList[i]));
        if ((supported != NULL) && (strcmp(supported, requested) == 0)) {
            // use special message to call ActivateKeyboardLayout() in main thread.
            if (AwtToolkit::GetInstance().SendMessage(WM_AWT_ACTIVATEKEYBOARDLAYOUT, (WPARAM)onActivate, (LPARAM)hKLList[i])) {
                //also need to change the same keyboard layout for the Java AWT-EventQueue thread
                AwtToolkit::activateKeyboardLayout(hKLList[i]);
                retValue = JNI_TRUE;
            }
            break;
        }
    }

    env->ReleaseStringUTFChars(localeString, requested);
    free(hKLList);
    return retValue;

    CATCH_BAD_ALLOC_RET(JNI_FALSE);
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    hideWindowsNative
 * Signature: (Lsun/awt/windows/WComponentPeer;Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WInputMethod_setStatusWindowVisible
  (JNIEnv *env, jobject self, jobject peer, jboolean visible)
{
    /* Retrieve the default input method Window handler from AwtToolkit.
       Windows system creates a default input method window for the
       toolkit thread.
    */

    HWND defaultIMEHandler = AwtToolkit::GetInstance().GetInputMethodWindow();

    if (defaultIMEHandler == NULL)
    {
        jobject peerGlobalRef = env->NewGlobalRef(peer);

        // use special message to access pData on the toolkit thread
        LRESULT res = AwtToolkit::GetInstance().SendMessage(WM_AWT_GET_DEFAULT_IME_HANDLER,
                                          reinterpret_cast<WPARAM>(peerGlobalRef), 0);
        // global ref is deleted in message handler

        if (res == TRUE) {
            defaultIMEHandler = AwtToolkit::GetInstance().GetInputMethodWindow();
        }
    }

    if (defaultIMEHandler != NULL) {
        ::SendMessage(defaultIMEHandler, WM_IME_CONTROL,
                      visible ? IMC_OPENSTATUSWINDOW : IMC_CLOSESTATUSWINDOW, 0);
    }
}

/*
 * Class:     sun_awt_windows_WInputMethod
 * Method:    openCandidateWindow
 * Signature: (Lsun/awt/windows/WComponentPeer;II)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WInputMethod_openCandidateWindow
  (JNIEnv *env, jobject self, jobject peer, jint x, jint y)
{
    TRY;

    PDATA pData;
    JNI_CHECK_PEER_RETURN(peer);

    jobject peerGlobalRef = env->NewGlobalRef(peer);

    // WARNING! MAKELONG macro treats the given values as unsigned.
    //   This may lead to some bugs in multiscreen configurations, as
    //   coordinates can be negative numbers. So, while handling
    //   WM_AWT_OPENCANDIDATEWINDOW message in AwtToolkit, we should
    //   carefully extract right x and y values using GET_X_LPARAM and
    //   GET_Y_LPARAM, not LOWORD and HIWORD
    // See CR 4805862, AwtToolkit::WndProc

    // use special message to open candidate window in main thread.
    AwtToolkit::GetInstance().SendMessage(WM_AWT_OPENCANDIDATEWINDOW,
                                          (WPARAM)peerGlobalRef, MAKELONG(x, y));
    // global ref is deleted in message handler

    CATCH_BAD_ALLOC;
}


/************************************************************************
 * WInputMethodDescriptor native methods
 */

/*
 * Class:     sun_awt_windows_WInputMethodDescriptor
 * Method:    getNativeAvailableLocales
 * Signature: ()[Ljava/util/Locale;
 */
JNIEXPORT jobjectArray JNICALL Java_sun_awt_windows_WInputMethodDescriptor_getNativeAvailableLocales
  (JNIEnv *env, jclass self)
{
    TRY;

    // get list of available HKLs
    int layoutCount = ::GetKeyboardLayoutList(0, NULL);
    HKL FAR * hKLList = (HKL FAR *)safe_Malloc(sizeof(HKL)*layoutCount);
    DASSERT(!safe_ExceptionOccurred(env));
    ::GetKeyboardLayoutList(layoutCount, hKLList);

    // get list of Java locale names while getting rid of duplicates
    int srcIndex = 0;
    int destIndex = 0;
    int javaLocaleNameCount = 0;
    int current = 0;
    const char ** javaLocaleNames = (const char **)safe_Malloc(sizeof(char *)*layoutCount);
    DASSERT(!safe_ExceptionOccurred(env));
    for (; srcIndex < layoutCount; srcIndex++) {
        const char * srcLocaleName = getJavaIDFromLangID(LOWORD(hKLList[srcIndex]));

        if (srcLocaleName == NULL) {
            // could not find corresponding Java locale name for this HKL.
            continue;
        }

        for (current = 0; current < destIndex; current++) {
            if (strcmp(javaLocaleNames[current], srcLocaleName) == 0) {
                // duplicated. ignore this HKL
                break;
            }
        }

        if (current == destIndex) {
            javaLocaleNameCount++;
            destIndex++;
            javaLocaleNames[current] = srcLocaleName;
        }
    }

    // convert it to an array of Java locale objects
    jclass localeClass = env->FindClass("java/util/Locale");
    jobjectArray locales = env->NewObjectArray(javaLocaleNameCount, localeClass, NULL);
    for (current = 0; current < javaLocaleNameCount; current++) {
        env->SetObjectArrayElement(locales,
                                   current,
                                   CreateLocaleObject(env, javaLocaleNames[current]));
    }
    DASSERT(!safe_ExceptionOccurred(env));

    env->DeleteLocalRef(localeClass);
    free(hKLList);
    free(javaLocaleNames);
    return locales;

    CATCH_BAD_ALLOC_RET(NULL);
}

/**
 * Class:     sun_awt_windows_WInputMethod
 * Method:    getNativeIMMDescription
 * Signature: ()Ljava/lang/String;
 *
 * This method tries to get the information about the input method associated with
 * the current active thread.
 *
 */
JNIEXPORT jstring JNICALL Java_sun_awt_windows_WInputMethod_getNativeIMMDescription
  (JNIEnv *env, jobject self) {

    TRY;

    // Get the keyboard layout of the active thread.
    HKL hkl = AwtComponent::GetKeyboardLayout();
    LPTSTR szImmDescription = NULL;
    UINT buffSize = 0;
    jstring infojStr = NULL;

    if ((buffSize = ::ImmGetDescription(hkl, szImmDescription, 0)) > 0) {
        szImmDescription = (LPTSTR) safe_Malloc(buffSize * sizeof(TCHAR));

        if (szImmDescription != NULL) {
            ImmGetDescription(hkl, szImmDescription, buffSize);

            infojStr = JNU_NewStringPlatform(env, szImmDescription);

            free(szImmDescription);
        }
    }

    return infojStr;

    CATCH_BAD_ALLOC_RET(NULL);
}

/*
 * Create a Java locale object from its name string
 */
jobject CreateLocaleObject(JNIEnv *env, const char * name)
{
    TRY;

    // get language, country, variant information
    char * language = (char *)safe_Malloc(strlen(name) + 1);
    char * country;
    char * variant;
    DASSERT(!safe_ExceptionOccurred(env));
    strcpy(language, name);
    for (country = language; *country != '_' && *country != '\0'; country++);
    if (*country == '_') {
        *country++ = '\0';
        for (variant = country; *variant != '_' && *variant != '\0'; variant++);
        if (*variant == '_') {
            *variant++ = '\0';
        }
    } else {
        variant = country;
    }

    // create Locale object
    jobject langObj = env->NewStringUTF(language);
    jobject ctryObj = env->NewStringUTF(country);
    jobject vrntObj = env->NewStringUTF(variant);
    jobject localeObj = JNU_NewObjectByName(env, "java/util/Locale",
                                            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                                            langObj, ctryObj, vrntObj);
    free(language);
    env->DeleteLocalRef(langObj);
    env->DeleteLocalRef(ctryObj);
    env->DeleteLocalRef(vrntObj);

    return localeObj;

    CATCH_BAD_ALLOC_RET(NULL);
}


/*
 * Gets user's preferred keyboard layout
 * Warning: This is version dependent code
 */
HKL getDefaultKeyboardLayout() {
    LONG ret;
    HKL hkl = 0;
    HKEY hKey;
    BYTE szHKL[16];
    DWORD cbHKL = 16;
    LPTSTR end;

    ret = ::RegOpenKeyEx(HKEY_CURRENT_USER, TEXT("Keyboard Layout\\Preload"), NULL, KEY_READ, &hKey);

    if (ret == ERROR_SUCCESS) {
        ret = ::RegQueryValueEx(hKey, TEXT("1"), 0, 0, szHKL, &cbHKL);

        if (ret == ERROR_SUCCESS) {
            hkl = reinterpret_cast<HKL>(static_cast<INT_PTR>(
                _tcstoul((LPCTSTR)szHKL, &end, 16)));
        }

        ::RegCloseKey(hKey);
    }

    return hkl;
}
} /* extern "C" */
