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

#include "java_net_DatagramPacket.h"
#include "net_util.h"

/************************************************************************
 * DatagramPacket
 */

jfieldID dp_addressID;
jfieldID dp_portID;
jfieldID dp_bufID;
jfieldID dp_offsetID;
jfieldID dp_lengthID;
jfieldID dp_bufLengthID;

/*
 * Class:     java_net_DatagramPacket
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_DatagramPacket_init (JNIEnv *env, jclass cls) {
    dp_addressID = (*env)->GetFieldID(env, cls, "address",
                                      "Ljava/net/InetAddress;");
    CHECK_NULL(dp_addressID);
    dp_portID = (*env)->GetFieldID(env, cls, "port", "I");
    CHECK_NULL(dp_portID);
    dp_bufID = (*env)->GetFieldID(env, cls, "buf", "[B");
    CHECK_NULL(dp_bufID);
    dp_offsetID = (*env)->GetFieldID(env, cls, "offset", "I");
    CHECK_NULL(dp_offsetID);
    dp_lengthID = (*env)->GetFieldID(env, cls, "length", "I");
    CHECK_NULL(dp_lengthID);
    dp_bufLengthID = (*env)->GetFieldID(env, cls, "bufLength", "I");
    CHECK_NULL(dp_bufLengthID);
}
