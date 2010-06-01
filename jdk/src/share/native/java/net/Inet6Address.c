/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>

#include "java_net_Inet6Address.h"
#include "net_util.h"

/************************************************************************
 * Inet6Address
 */

jclass ia6_class;
jfieldID ia6_ipaddressID;
jfieldID ia6_scopeidID;
jfieldID ia6_cachedscopeidID;
jfieldID ia6_scopeidsetID;
jfieldID ia6_scopeifnameID;
jfieldID ia6_scopeifnamesetID;
jmethodID ia6_ctrID;

/*
 * Class:     java_net_Inet6Address
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_Inet6Address_init(JNIEnv *env, jclass cls) {
    jclass c = (*env)->FindClass(env, "java/net/Inet6Address");
    CHECK_NULL(c);
    ia6_class = (*env)->NewGlobalRef(env, c);
    CHECK_NULL(ia6_class);
    ia6_ipaddressID = (*env)->GetFieldID(env, ia6_class, "ipaddress", "[B");
    CHECK_NULL(ia6_ipaddressID);
    ia6_scopeidID = (*env)->GetFieldID(env, ia6_class, "scope_id", "I");
    CHECK_NULL(ia6_scopeidID);
    ia6_cachedscopeidID = (*env)->GetFieldID(env, ia6_class, "cached_scope_id", "I");
    CHECK_NULL(ia6_cachedscopeidID);
    ia6_scopeidsetID = (*env)->GetFieldID(env, ia6_class, "scope_id_set", "Z");
    CHECK_NULL(ia6_scopeidID);
    ia6_scopeifnameID = (*env)->GetFieldID(env, ia6_class, "scope_ifname", "Ljava/net/NetworkInterface;");
    CHECK_NULL(ia6_scopeifnameID);
    ia6_scopeifnamesetID = (*env)->GetFieldID(env, ia6_class, "scope_ifname_set", "Z");
    CHECK_NULL(ia6_scopeifnamesetID);
    ia6_ctrID = (*env)->GetMethodID(env, ia6_class, "<init>", "()V");
    CHECK_NULL(ia6_ctrID);
}
