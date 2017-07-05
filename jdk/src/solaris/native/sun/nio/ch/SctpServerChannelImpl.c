/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "sun_nio_ch_SctpServerChannelImpl.h"

extern void Java_sun_nio_ch_ServerSocketChannelImpl_initIDs(JNIEnv* env,
    jclass c);

extern jint Java_sun_nio_ch_ServerSocketChannelImpl_accept0(JNIEnv* env,
    jobject this, jobject ssfdo, jobject newfdo, jobjectArray isaa);

/*
 * Class:     sun_nio_ch_SctpServerChannelImpl
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_nio_ch_SctpServerChannelImpl_initIDs
  (JNIEnv* env, jclass c) {
    Java_sun_nio_ch_ServerSocketChannelImpl_initIDs(env, c);
}

/*
 * Class:     sun_nio_ch_SctpServerChannelImpl
 * Method:    accept0
 * Signature: (Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;[Ljava/net/InetSocketAddress;)I
 */
JNIEXPORT jint JNICALL Java_sun_nio_ch_SctpServerChannelImpl_accept0
  (JNIEnv* env, jobject this, jobject ssfdo, jobject newfdo, jobjectArray isaa) {
    return Java_sun_nio_ch_ServerSocketChannelImpl_accept0(env, this,
                                                           ssfdo, newfdo, isaa);
}
