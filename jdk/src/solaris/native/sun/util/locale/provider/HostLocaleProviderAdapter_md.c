/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_util_locale_provider_HostLocaleProviderAdapterImpl.h"
#include <gdefs.h>
#include <string.h>
#include <langinfo.h>
#include <locale.h>

#define BUFLEN 64

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getPattern
 * Signature: (IILjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getPattern
  (JNIEnv *env, jclass cls, jint dateStyle, jint timeStyle, jstring jlangtag) {

    // TEMPORARY!
    char locale[BUFLEN];
    char * pch;
    char * old;
    char * ret;
    const char *langtag = (*env)->GetStringUTFChars(env, jlangtag, JNI_FALSE);

    strcpy(locale, langtag);
    pch = strchr(locale, '-');
    if (pch != NULL) {
        *pch = '_';
    }
    pch = strchr(locale, '-');
    if (pch != NULL) {
        *pch = '\0';
    }
    strcat(locale, ".UTF-8");
    old = setlocale(LC_TIME, "");
    setlocale(LC_TIME, locale);

    if (dateStyle != (-1) && timeStyle != (-1)) {
        ret = nl_langinfo(D_T_FMT);
    } else if (dateStyle != (-1)) {
        ret = nl_langinfo(D_FMT);
    } else if (timeStyle != (-1)) {
        ret = nl_langinfo(T_FMT);
    } else {
        ret = "yyyy/MM/dd";
    }

    setlocale(LC_TIME, old);

    (*env)->ReleaseStringUTFChars(env, jlangtag, langtag);

    return (*env)->NewStringUTF(env, ret);
}
