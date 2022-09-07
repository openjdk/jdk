/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jlong.h"

#include "nio.h"

#include <stdlib.h>
#include <strings.h>
#include <unistd.h>
#include <errno.h>
#include <sys/attr.h>

#include "sun_nio_fs_BsdFileStore.h"

#define CAPABILITY(vinfo, cap) \
        (((vinfo).valid[VOL_CAPABILITIES_INTERFACES]        & (cap)) && \
         ((vinfo).capabilities[VOL_CAPABILITIES_INTERFACES] & (cap)))

static void throwUnixException(JNIEnv* env, int errnum) {
    jobject x = JNU_NewObjectByName(env, "sun/nio/fs/UnixException",
        "(I)V", errnum);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}

JNIEXPORT jboolean JNICALL
Java_sun_nio_fs_BsdFileStore_supportsCloning0(JNIEnv* env, jclass this,
    jlong fileAddress)
{
    const char* file = (const char*)jlong_to_ptr(fileAddress);

    struct attrlist alist;
    bzero(&alist, sizeof(alist));
    alist.bitmapcount = ATTR_BIT_MAP_COUNT;
    alist.volattr     = ATTR_VOL_INFO | ATTR_VOL_CAPABILITIES;

    struct volAttrsBuf {
        u_int32_t length;
        vol_capabilities_attr_t capabilities;
    } __attribute__((aligned(4), packed));
    struct volAttrsBuf volAttrs;
    bzero(&volAttrs, sizeof(volAttrs));

    // ignore any error in getattrlist
    if (getattrlist(file, &alist, &volAttrs, sizeof(volAttrs), 0) == 0) {
        vol_capabilities_attr_t volCaps = volAttrs.capabilities;
        int supportsAttrList = CAPABILITY(volCaps, VOL_CAP_INT_ATTRLIST);
        if (supportsAttrList) {
            return CAPABILITY(volCaps, VOL_CAP_INT_CLONE) != 0 ?
                JNI_TRUE : JNI_FALSE;
        }
    }

    // return false if getattrlist fails
    return JNI_FALSE;
}
