/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <jni.h>
#include <jni_util.h>
#include <jvm.h>
#include "gdefs.h"

#include <sys/param.h>
#include <sys/utsname.h>

#ifdef AIX
#include "porting_aix.h" /* For the 'dladdr' function. */
#endif

#ifdef DEBUG
#define VERBOSE_AWT_DEBUG
#endif

static void *awtHandle = NULL;

typedef jint JNICALL JNI_OnLoad_type(JavaVM *vm, void *reserved);

/* Initialize the Java VM instance variable when the library is
   first loaded */
JavaVM *jvm;

JNIEXPORT jboolean JNICALL AWTIsHeadless() {
    static JNIEnv *env = NULL;
    static jboolean isHeadless;
    jmethodID headlessFn;
    jclass graphicsEnvClass;

    if (env == NULL) {
        env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        graphicsEnvClass = (*env)->FindClass(env,
                                             "java/awt/GraphicsEnvironment");
        if (graphicsEnvClass == NULL) {
            return JNI_TRUE;
        }
        headlessFn = (*env)->GetStaticMethodID(env,
                                               graphicsEnvClass, "isHeadless", "()Z");
        if (headlessFn == NULL) {
            return JNI_TRUE;
        }
        isHeadless = (*env)->CallStaticBooleanMethod(env, graphicsEnvClass,
                                                     headlessFn);
    }
    return isHeadless;
}

#define CHECK_EXCEPTION_FATAL(env, message) \
    if ((*env)->ExceptionCheck(env)) { \
        (*env)->ExceptionClear(env); \
        (*env)->FatalError(env, message); \
    }

/*
 * Pathnames to the various awt toolkits
 */

#ifdef MACOSX
  #define LWAWT_PATH "/libawt_lwawt.dylib"
  #define DEFAULT_PATH LWAWT_PATH
#else
  #define XAWT_PATH "/libawt_xawt.so"
  #define DEFAULT_PATH XAWT_PATH
  #define HEADLESS_PATH "/libawt_headless.so"
#endif

jint
AWT_OnLoad(JavaVM *vm, void *reserved)
{
    Dl_info dlinfo;
    char buf[MAXPATHLEN];
    int32_t len;
    char *p, *tk;
    JNI_OnLoad_type *JNI_OnLoad_ptr;
    struct utsname name;
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(vm, JNI_VERSION_1_2);
    void *v;
    jstring fmanager = NULL;
    jstring fmProp = NULL;

    if (awtHandle != NULL) {
        /* Avoid several loading attempts */
        return JNI_VERSION_1_2;
    }

    jvm = vm;

    /* Get address of this library and the directory containing it. */
    dladdr((void *)AWT_OnLoad, &dlinfo);
    realpath((char *)dlinfo.dli_fname, buf);
    len = strlen(buf);
    p = strrchr(buf, '/');

    /*
     * The code below is responsible for:
     * 1. Loading appropriate awt library, i.e. libawt_xawt or libawt_headless
     * 2. Set the "sun.font.fontmanager" system property.
     */

    fmProp = (*env)->NewStringUTF(env, "sun.font.fontmanager");
    CHECK_EXCEPTION_FATAL(env, "Could not allocate font manager property");

#ifdef MACOSX
        fmanager = (*env)->NewStringUTF(env, "sun.font.CFontManager");
        tk = LWAWT_PATH;
#else
        fmanager = (*env)->NewStringUTF(env, "sun.awt.X11FontManager");
        tk = XAWT_PATH;
#endif
    CHECK_EXCEPTION_FATAL(env, "Could not allocate font manager name");

    if (fmanager && fmProp) {
        JNU_CallStaticMethodByName(env, NULL, "java/lang/System", "setProperty",
                                   "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                   fmProp, fmanager);
        CHECK_EXCEPTION_FATAL(env, "Could not allocate set properties");
    }

#ifndef MACOSX
    if (AWTIsHeadless()) {
        tk = HEADLESS_PATH;
    }
#endif

    /* Calculate library name to load */
    strncpy(p, tk, MAXPATHLEN-len-1);

    if (fmProp) {
        (*env)->DeleteLocalRef(env, fmProp);
    }
    if (fmanager) {
        (*env)->DeleteLocalRef(env, fmanager);
    }

    jstring jbuf = JNU_NewStringPlatform(env, buf);
    CHECK_EXCEPTION_FATAL(env, "Could not allocate library name");
    JNU_CallStaticMethodByName(env, NULL, "java/lang/System", "load",
                               "(Ljava/lang/String;)V",
                               jbuf);

    awtHandle = dlopen(buf, RTLD_LAZY | RTLD_GLOBAL);

    return JNI_VERSION_1_2;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
    return AWT_OnLoad(vm, reserved);
}

/*
 * This entry point must remain in libawt.so as part of a contract
 * with the CDE variant of Java Media Framework. (sdtjmplay)
 * Reflect this call over to the correct libawt_<toolkit>.so.
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_XsessionWMcommand(JNIEnv *env, jobject this,
                                     jobject frame, jstring jcommand)
{
    /* type of the old backdoor function */
    typedef void JNICALL
        XsessionWMcommand_type(JNIEnv *env, jobject this,
                               jobject frame, jstring jcommand);

    static XsessionWMcommand_type *XsessionWMcommand = NULL;

    if (XsessionWMcommand == NULL && awtHandle == NULL) {
        return;
    }

    XsessionWMcommand = (XsessionWMcommand_type *)
        dlsym(awtHandle, "Java_sun_awt_motif_XsessionWMcommand");

    if (XsessionWMcommand == NULL)
        return;

    (*XsessionWMcommand)(env, this, frame, jcommand);
}


/*
 * This entry point must remain in libawt.so as part of a contract
 * with the CDE variant of Java Media Framework. (sdtjmplay)
 * Reflect this call over to the correct libawt_<toolkit>.so.
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_XsessionWMcommand_New(JNIEnv *env, jobjectArray jargv)
{
    typedef void JNICALL
        XsessionWMcommand_New_type(JNIEnv *env, jobjectArray jargv);

    static XsessionWMcommand_New_type *XsessionWMcommand = NULL;

    if (XsessionWMcommand == NULL && awtHandle == NULL) {
        return;
    }

    XsessionWMcommand = (XsessionWMcommand_New_type *)
        dlsym(awtHandle, "Java_sun_awt_motif_XsessionWMcommand_New");

    if (XsessionWMcommand == NULL)
        return;

    (*XsessionWMcommand)(env, jargv);
}
