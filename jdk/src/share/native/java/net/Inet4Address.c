/*
 * Copyright 2000-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <string.h>

#include "java_net_Inet4Address.h"
#include "net_util.h"

/************************************************************************
 * Inet4Address
 */
jclass ia4_class;
jmethodID ia4_ctrID;

/*
 * Class:     java_net_Inet4Address
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_Inet4Address_init(JNIEnv *env, jclass cls) {
    jclass c = (*env)->FindClass(env, "java/net/Inet4Address");
    CHECK_NULL(c);
    ia4_class = (*env)->NewGlobalRef(env, c);
    CHECK_NULL(ia4_class);
    ia4_ctrID = (*env)->GetMethodID(env, ia4_class, "<init>", "()V");
    CHECK_NULL(ia4_ctrID);
}
