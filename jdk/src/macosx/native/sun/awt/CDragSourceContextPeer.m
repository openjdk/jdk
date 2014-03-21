/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#import "sun_lwawt_macosx_CDragSourceContextPeer.h"

#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "CDragSource.h"
#import "ThreadUtilities.h"


/*
 * Class:     sun_lwawt_macosx_CDragSourceContextPeer
 * Method:    createNativeDragSource
 * Signature: (Ljava/awt/Component;JLjava/awt/datatransfer/Transferable;
               Ljava/awt/event/InputEvent;IIIIJIJIII[JLjava/util/Map;)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CDragSourceContextPeer_createNativeDragSource
  (JNIEnv *env, jobject jthis, jobject jcomponent, jlong jnativepeer, jobject jtransferable,
   jobject jtrigger, jint jdragposx, jint jdragposy, jint jextmodifiers, jint jclickcount, jlong jtimestamp,
   jobject jnsdragimage, jint jdragimageoffsetx, jint jdragimageoffsety,
   jint jsourceactions, jlongArray jformats, jobject jformatmap)
{
    id controlObj = (id) jlong_to_ptr(jnativepeer);
    __block CDragSource* dragSource = nil;

JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        dragSource = [[CDragSource alloc] init:jthis
                                     component:jcomponent
                                       control:controlObj
                                  transferable:jtransferable
                                  triggerEvent:jtrigger
                                      dragPosX:jdragposx
                                      dragPosY:jdragposy
                                     modifiers:jextmodifiers
                                    clickCount:jclickcount
                                     timeStamp:jtimestamp
                                     dragImage:jnsdragimage
                              dragImageOffsetX:jdragimageoffsetx
                              dragImageOffsetY:jdragimageoffsety
                                 sourceActions:jsourceactions
                                       formats:jformats
                                     formatMap:jformatmap];
    }];
JNF_COCOA_EXIT(env);

    if (dragSource) {
        CFRetain(dragSource); // GC
        [dragSource release];
    }
    return ptr_to_jlong(dragSource);
}

/*
 * Class:     sun_lwawt_macosx_CDragSourceContextPeer
 * Method:    doDragging
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CDragSourceContextPeer_doDragging
  (JNIEnv *env, jobject jthis, jlong nativeDragSourceVal)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    CDragSource* dragSource = (CDragSource*) jlong_to_ptr(nativeDragSourceVal);

JNF_COCOA_ENTER(env);
    [dragSource drag];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CDragSourceContextPeer
 * Method:    releaseNativeDragSource
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CDragSourceContextPeer_releaseNativeDragSource
  (JNIEnv *env, jobject jthis, jlong nativeDragSourceVal)
{
      CDragSource* dragSource = (CDragSource*) jlong_to_ptr(nativeDragSourceVal);

JNF_COCOA_ENTER(env);
    [dragSource removeFromView:env];
JNF_COCOA_EXIT(env);
}
