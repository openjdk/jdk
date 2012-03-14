/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#import <dlfcn.h>
#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#ifndef MAXPATHLEN
#define MAXPATHLEN PATH_MAX
#endif

static jboolean
GetPathFromCurrentBinary(char *buf, jint bufsize)
{
    Dl_info dlinfo;
    dladdr((void *)GetPathFromCurrentBinary, &dlinfo);
    if (realpath(dlinfo.dli_fname, buf) == NULL) {
//      fprintf(stderr, "Error: realpath(`%s') failed.\n", dlinfo.dli_fname);
        return JNI_FALSE;
    }

    const char *libawt = "lib/libawt.dylib";
    int strLen, libawtLen;

    strLen = strlen(buf);
    libawtLen = strlen(libawt);

    if (strLen < libawtLen ||
        strcmp(buf + strLen - libawtLen, libawt) != 0) {
        return JNI_FALSE;
    }

    buf[strLen - libawtLen] = '\0';

    return JNI_TRUE;
}

#define JAVA_DLL "libjava.dylib"

static jboolean
GetJREPath(char *buf, jint bufsize)
{
    /* try to get the path from the current binary, if not, bail to the framework */
    if (GetPathFromCurrentBinary(buf, bufsize) == JNI_TRUE) {
        /* does the rest of the JRE exist? */
        char libjava[MAXPATHLEN];
        snprintf(libjava, MAXPATHLEN, "%s/lib/" JAVA_DLL, buf);
        if (access(libjava, F_OK) == 0) {
            return JNI_TRUE;
        }
    }

    return JNI_FALSE;
}

static NSString *getRunningJavaBundle()
{
    char path[MAXPATHLEN];
    GetJREPath(path, MAXPATHLEN);
    return [[NSString alloc] initWithFormat:@"%@/bundle", [NSString stringWithUTF8String:path]];
}

/*
 * Class:     com_apple_resources_LoadNativeBundleAction
 * Method:    getPathToBundleFile
 * Signature: (Ljava/lang/String)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_apple_resources_LoadNativeBundleAction_getPathToBundleFile
    (JNIEnv *env, jclass klass, jstring filename)
{
    jstring returnVal = NULL;
    if (filename == NULL) {
        return NULL;
    }

JNF_COCOA_ENTER(env);
    NSBundle *javaBundle = [NSBundle bundleWithPath:getRunningJavaBundle()];
    NSString *baseFilename = JNFJavaToNSString(env, filename);
    NSString *propertyFilePath = [javaBundle pathForResource:baseFilename ofType:@"properties"];

    if (propertyFilePath != nil) {
        returnVal = JNFNSToJavaString(env, propertyFilePath);
    }
JNF_COCOA_EXIT(env);

    return returnVal;
}
