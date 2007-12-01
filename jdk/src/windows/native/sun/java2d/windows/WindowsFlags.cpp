/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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


#include <jni.h>
#include <awt.h>
#include "Trace.h"
#include "WindowsFlags.h"
#include "dxInit.h"

BOOL      ddVramForced;       // disable punting of ddraw buffers
BOOL      accelReset;         // reset registry 2d acceleration settings
BOOL      useDD;              // ddraw enabled flag
BOOL      useD3D;             // d3d enabled flag
BOOL      forceD3DUsage;      // force d3d on or off
jboolean  g_offscreenSharing; // JAWT accelerated surface sharing
BOOL      useDDLock;          // Disabled for win2k/XP
BOOL      checkRegistry;      // Diagnostic tool: outputs 2d registry settings
BOOL      disableRegistry;    // Diagnostic tool: disables registry interaction
BOOL      setHighDPIAware;    // Whether to set the high-DPI awareness flag

extern WCHAR *j2dAccelKey;       // Name of java2d root key
extern WCHAR *j2dAccelDriverKey; // Name of j2d per-device key

static jfieldID ddEnabledID;
static jfieldID d3dEnabledID;
static jfieldID d3dSetID;
static jfieldID ddSetID;
static jclass   wFlagsClassID;

void SetIDs(JNIEnv *env, jclass wFlagsClass)
{
    wFlagsClassID = (jclass)env->NewGlobalRef(wFlagsClass);
    ddEnabledID = env->GetStaticFieldID(wFlagsClass, "ddEnabled", "Z");
    d3dEnabledID = env->GetStaticFieldID(wFlagsClass, "d3dEnabled", "Z");
    d3dSetID = env->GetStaticFieldID(wFlagsClass, "d3dSet", "Z");
    ddSetID = env->GetStaticFieldID(wFlagsClass, "ddSet", "Z");
}

BOOL GetStaticBoolean(JNIEnv *env, jclass wfClass, const char *fieldName)
{
    jfieldID fieldID = env->GetStaticFieldID(wfClass, fieldName, "Z");
    return env->GetStaticBooleanField(wfClass, fieldID);
}

jobject GetStaticObject(JNIEnv *env, jclass wfClass, const char *fieldName,
                        const char *signature)
{
    jfieldID fieldID = env->GetStaticFieldID(wfClass, fieldName, signature);
    return env->GetStaticObjectField(wfClass, fieldID);
}

void GetFlagValues(JNIEnv *env, jclass wFlagsClass)
{
    useDD = env->GetStaticBooleanField(wFlagsClass, ddEnabledID);
    jboolean ddSet = env->GetStaticBooleanField(wFlagsClass, ddSetID);
    jboolean d3dEnabled = env->GetStaticBooleanField(wFlagsClass, d3dEnabledID);
    jboolean d3dSet = env->GetStaticBooleanField(wFlagsClass, d3dSetID);
    if (!d3dSet) {
        // Only check environment variable if user did not set Java
        // command-line parameter; values of sun.java2d.d3d override
        // any setting of J2D_D3D environment variable.
        char *d3dEnv = getenv("J2D_D3D");
        if (d3dEnv) {
            if (strcmp(d3dEnv, "false") == 0) {
                // printf("Java2D Direct3D usage disabled by J2D_D3D env\n");
                d3dEnabled = FALSE;
                d3dSet = TRUE;
                SetD3DEnabledFlag(env, d3dEnabled, d3dSet);
            } else if (strcmp(d3dEnv, "true") == 0) {
                // printf("Java2D Direct3D usage forced on by J2D_D3D env\n");
                d3dEnabled = TRUE;
                d3dSet = TRUE;
                SetD3DEnabledFlag(env, d3dEnabled, d3dSet);
            }
        }
    }
    useD3D = d3dEnabled;
    forceD3DUsage = d3dSet;
    ddVramForced = GetStaticBoolean(env, wFlagsClass, "ddVramForced");
    g_offscreenSharing = GetStaticBoolean(env, wFlagsClass,
                                          "offscreenSharingEnabled");
    useDDLock = GetStaticBoolean(env, wFlagsClass, "ddLockEnabled");
    jboolean ddLockSet = GetStaticBoolean(env, wFlagsClass, "ddLockSet");
    accelReset = GetStaticBoolean(env, wFlagsClass, "accelReset");
    checkRegistry = GetStaticBoolean(env, wFlagsClass, "checkRegistry");
    disableRegistry = GetStaticBoolean(env, wFlagsClass, "disableRegistry");
    jstring javaVersionString = (jstring)GetStaticObject(env, wFlagsClass,
                                                         "javaVersion",
                                                         "Ljava/lang/String;");
    jboolean isCopy;
    const jchar *javaVersion = env->GetStringChars(javaVersionString,
                                             &isCopy);
    jsize versionLength = env->GetStringLength(javaVersionString);
    size_t j2dRootKeyLength = wcslen(J2D_ACCEL_KEY_ROOT);
    j2dAccelKey = (WCHAR *)safe_Calloc((j2dRootKeyLength + versionLength + 2),
                                       sizeof(WCHAR));
    wcscpy(j2dAccelKey, J2D_ACCEL_KEY_ROOT);
    wcscat(j2dAccelKey, javaVersion);
    wcscat(j2dAccelKey, L"\\");
    j2dAccelDriverKey =
        (WCHAR *)safe_Calloc((wcslen(j2dAccelKey) +
                              wcslen(J2D_ACCEL_DRIVER_SUBKEY) + 1),
                             sizeof(WCHAR));
    wcscpy(j2dAccelDriverKey, j2dAccelKey);
    wcscat(j2dAccelDriverKey, J2D_ACCEL_DRIVER_SUBKEY);
    env->ReleaseStringChars(javaVersionString, javaVersion);

    setHighDPIAware =
        (IS_WINVISTA && GetStaticBoolean(env, wFlagsClass, "setHighDPIAware"));

    // Change default value of some flags based on OS-specific requirements
    if (IS_WINVISTA && !(ddSet && useDD)) {
        // Disable ddraw on vista due to issues with mixing GDI and ddraw
        // unless ddraw is forced
        SetDDEnabledFlag(env, FALSE);
        J2dRlsTraceLn(J2D_TRACE_WARNING,
                      "GetFlagValues: DDraw/D3D is disabled on Windows Vista");
    }

    if (IS_NT && !(IS_WIN2000)) {
        // Do not enable d3d on NT4; d3d is only supported through
        // software on that platform
        SetD3DEnabledFlag(env, FALSE, FALSE);
        J2dRlsTraceLn(J2D_TRACE_WARNING,
                      "GetFlagValues: D3D is disabled on Win NT");
    }
    if (IS_WIN64 && !d3dSet) {
        // Only enable d3d on Itanium if user forces it on.
        // D3d was not functioning on initial XP Itanium releases
        // so we do not want it suddenly enabled in the field without
        // having tested that codepath first.
        SetD3DEnabledFlag(env, FALSE, FALSE);
        J2dRlsTraceLn(J2D_TRACE_WARNING,
                      "GetFlagValues: D3D is disabled on 64-bit OSs");
    }
    if (IS_WIN2000 && !ddLockSet) { // valid for win2k, XP, and future OSs
        // Fix for cursor flicker on win2k and XP (bug 4409306).  The
        // fix is to avoid using DDraw for locking the
        // screen.  Ideally, we will handle most operations to the
        // screen through new GDI Blt loops (GDIBlitLoops.cpp),
        // but failing there we will punt to GDI instead of DDraw for
        // locking the screen.
        useDDLock = FALSE;
        J2dRlsTraceLn(J2D_TRACE_WARNING,
                      "GetFlagValues: DDraw screen locking is "\
                      "disabled (W2K, XP+)");
    }
    J2dTraceLn(J2D_TRACE_INFO, "WindowsFlags (native):");
    J2dTraceLn1(J2D_TRACE_INFO, "  ddEnabled = %s",
                (useDD ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  ddSet = %s",
                (ddSet ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  ddVramForced = %s",
                (ddVramForced ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  d3dEnabled = %s",
                (useD3D ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  d3dSet = %s",
                (forceD3DUsage ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  ddLockEnabled = %s",
                (useDDLock ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  ddLockSet = %s",
                (ddLockSet ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  offscreenSharing = %s",
                (g_offscreenSharing ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  accelReset = %s",
                (accelReset ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  checkRegistry = %s",
                (checkRegistry ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  disableRegistry = %s",
                (disableRegistry ? "true" : "false"));
    J2dTraceLn1(J2D_TRACE_INFO, "  setHighDPIAware = %s",
                (setHighDPIAware ? "true" : "false"));
}

void SetD3DEnabledFlag(JNIEnv *env, BOOL d3dEnabled, BOOL d3dSet)
{
    useD3D = d3dEnabled;
    forceD3DUsage = d3dSet;
    if (env == NULL) {
        env = (JNIEnv * ) JNU_GetEnv(jvm, JNI_VERSION_1_2);
    }
    env->SetStaticBooleanField(wFlagsClassID, d3dEnabledID, d3dEnabled);
    if (d3dSet) {
        env->SetStaticBooleanField(wFlagsClassID, d3dSetID, d3dSet);
    }
}

void SetDDEnabledFlag(JNIEnv *env, BOOL ddEnabled)
{
    useDD = ddEnabled;
    if (env == NULL) {
        env = (JNIEnv * ) JNU_GetEnv(jvm, JNI_VERSION_1_2);
    }
    env->SetStaticBooleanField(wFlagsClassID, ddEnabledID, ddEnabled);
}

extern "C" {

/**
 * This function is called from WindowsFlags.initFlags() and initializes
 * the native side of our runtime flags.  There are a couple of important
 * things that happen at the native level after we set the Java flags:
 * - set native variables based on the java flag settings (such as useDD
 * based on whether ddraw was enabled by a runtime flag)
 * - override java level settings if there user has set an environment
 * variable but not a runtime flag.  For example, if the user runs
 * with sun.java2d.d3d=true but also uses the J2D_D3D=false environment
 * variable, then we use the java-level true value.  But if they do
 * not use the runtime flag, then the env variable will force d3d to
 * be disabled.  Any native env variable overriding must up-call to
 * Java to change the java level flag settings.
 * - A later error in initialization may result in disabling some
 * native property that must be propagated to the Java level.  For
 * example, d3d is enabled by default, but we may find later that
 * we must disable it do to some runtime configuration problem (such as
 * a bad video card).  This will happen through mechanisms in this native
 * file to change the value of the known Java flags (in this d3d example,
 * we would up-call to set the value of d3dEnabled to Boolean.FALSE).
 */
JNIEXPORT void JNICALL
Java_sun_java2d_windows_WindowsFlags_initNativeFlags(JNIEnv *env,
                                                     jclass wFlagsClass)
{
    SetIDs(env, wFlagsClass);
    GetFlagValues(env, wFlagsClass);
}

} // extern "C"
