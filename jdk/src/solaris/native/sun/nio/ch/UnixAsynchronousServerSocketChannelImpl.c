/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_nio_ch_UnixAsynchronousServerSocketChannelImpl.h"

extern void Java_sun_nio_ch_ServerSocketChannelImpl_initIDs(JNIEnv* env,
    jclass c);

extern jint Java_sun_nio_ch_ServerSocketChannelImpl_accept0(JNIEnv* env,
    jobject this, jobject ssfdo, jobject newfdo, jobjectArray isaa);

JNIEXPORT void JNICALL
Java_sun_nio_ch_UnixAsynchronousServerSocketChannelImpl_initIDs(JNIEnv* env,
    jclass c)
{
    Java_sun_nio_ch_ServerSocketChannelImpl_initIDs(env, c);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_UnixAsynchronousServerSocketChannelImpl_accept0(JNIEnv* env,
    jobject this, jobject ssfdo, jobject newfdo, jobjectArray isaa)
{
    return Java_sun_nio_ch_ServerSocketChannelImpl_accept0(env, this,
        ssfdo, newfdo, isaa);
}
