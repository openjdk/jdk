/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"

#include "java_lang_Module.h"

/*
 * Gets the UTF-8 chars for the string and translates '.' to '/'.  Does no
 * further validation, assumption being that both calling code in
 * java.lang.Module and VM will do deeper validation.
 */
static char*
GetInternalPackageName(JNIEnv *env, jstring pkg, char* buf, jsize buf_size)
{
    jsize len;
    jsize unicode_len;
    char* p;
    char* utf_str;

    len = (*env)->GetStringUTFLength(env, pkg);
    unicode_len = (*env)->GetStringLength(env, pkg);
    if (len >= buf_size) {
        utf_str = malloc(len + 1);
        if (utf_str == NULL) {
            JNU_ThrowOutOfMemoryError(env, NULL);
            return NULL;
        }
    } else {
        utf_str = buf;
    }
    (*env)->GetStringUTFRegion(env, pkg, 0, unicode_len, utf_str);

    p = utf_str;
    while (*p != '\0') {
        if (*p == '.') {
            *p = '/';
        }
        p++;
    }
    return utf_str;
}

JNIEXPORT void JNICALL
Java_java_lang_Module_defineModule0(JNIEnv *env, jclass cls, jobject module,
                                            jboolean is_open, jstring version,
                                            jstring location, jobjectArray packages)
{
    char** pkgs = NULL;
    jsize num_packages = (*env)->GetArrayLength(env, packages);

    if (num_packages != 0 && (pkgs = calloc(num_packages, sizeof(char*))) == NULL) {
        JNU_ThrowOutOfMemoryError(env, NULL);
        return;
    } else if ((*env)->EnsureLocalCapacity(env, (jint)num_packages) == 0) {
        jboolean failed = JNI_FALSE;
        int idx;
        for (idx = 0; idx < num_packages; idx++) {
            jstring pkg = (*env)->GetObjectArrayElement(env, packages, idx);
            char* name = GetInternalPackageName(env, pkg, NULL, 0);
            if (name != NULL) {
                pkgs[idx] = name;
            } else {
                failed = JNI_TRUE;
                break;
            }
        }
        if (!failed) {
            JVM_DefineModule(env, module, is_open, version, location,
                             (const char* const*)pkgs, num_packages);
        }
    }

    if (num_packages > 0) {
        int idx;
        for (idx = 0; idx < num_packages; idx++) {
            if (pkgs[idx] != NULL) {
                free(pkgs[idx]);
            }
        }
        free(pkgs);
    }
}

JNIEXPORT void JNICALL
Java_java_lang_Module_addReads0(JNIEnv *env, jclass cls, jobject from, jobject to)
{
    JVM_AddReadsModule(env, from, to);
}

JNIEXPORT void JNICALL
Java_java_lang_Module_addExports0(JNIEnv *env, jclass cls, jobject from,
                                  jstring pkg, jobject to)
{
    char buf[128];
    char* pkg_name;

    if (pkg == NULL) {
        JNU_ThrowNullPointerException(env, "package is null");
        return;
    }

    pkg_name = GetInternalPackageName(env, pkg, buf, (jsize)sizeof(buf));
    if (pkg_name != NULL) {
        JVM_AddModuleExports(env, from, pkg_name, to);
        if (pkg_name != buf) {
            free(pkg_name);
        }
    }
}

JNIEXPORT void JNICALL
Java_java_lang_Module_addExportsToAll0(JNIEnv *env, jclass cls, jobject from,
                                       jstring pkg)
{
    char buf[128];
    char* pkg_name;

    if (pkg == NULL) {
        JNU_ThrowNullPointerException(env, "package is null");
        return;
    }

    pkg_name = GetInternalPackageName(env, pkg, buf, (jsize)sizeof(buf));
    if (pkg_name != NULL) {
        JVM_AddModuleExportsToAll(env, from, pkg_name);
        if (pkg_name != buf) {
            free(pkg_name);
        }
    }
}

JNIEXPORT void JNICALL
Java_java_lang_Module_addExportsToAllUnnamed0(JNIEnv *env, jclass cls,
                                              jobject from, jstring pkg)
{
    char buf[128];
    char* pkg_name;

    if (pkg == NULL) {
        JNU_ThrowNullPointerException(env, "package is null");
        return;
    }

    pkg_name = GetInternalPackageName(env, pkg, buf, (jsize)sizeof(buf));
    if (pkg_name != NULL) {
        JVM_AddModuleExportsToAllUnnamed(env, from, pkg_name);
        if (pkg_name != buf) {
            free(pkg_name);
        }
    }
}
