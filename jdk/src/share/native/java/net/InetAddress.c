/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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

#include "java_net_InetAddress.h"
#include "net_util.h"

/************************************************************************
 * InetAddress
 */

jclass ia_class;
jfieldID ia_addressID;
jfieldID ia_familyID;
jfieldID ia_preferIPv6AddressID;

/*
 * Class:     java_net_InetAddress
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_InetAddress_init(JNIEnv *env, jclass cls) {
    jclass c = (*env)->FindClass(env,"java/net/InetAddress");
    CHECK_NULL(c);
    ia_class = (*env)->NewGlobalRef(env, c);
    CHECK_NULL(ia_class);
    ia_addressID = (*env)->GetFieldID(env, ia_class, "address", "I");
    CHECK_NULL(ia_addressID);
    ia_familyID = (*env)->GetFieldID(env, ia_class, "family", "I");
    CHECK_NULL(ia_familyID);
    ia_preferIPv6AddressID = (*env)->GetStaticFieldID(env, ia_class, "preferIPv6Address", "Z");
    CHECK_NULL(ia_preferIPv6AddressID);
}
