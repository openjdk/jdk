/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "AWTSurfaceLayers.h"

JNIEXPORT JAWT_DrawingSurfaceInfo* JNICALL awt_DrawingSurface_GetDrawingSurfaceInfo
(JAWT_DrawingSurface* ds)
{
    JAWT_DrawingSurfaceInfo* dsi = (JAWT_DrawingSurfaceInfo*)malloc(sizeof(JAWT_DrawingSurfaceInfo));

    JNIEnv *env = ds->env;
    jobject target = ds->target;

    static JNF_CLASS_CACHE(jc_Component, "java/awt/Component");
    static JNF_MEMBER_CACHE(jf_peer, jc_Component, "peer", "Ljava/awt/peer/ComponentPeer;");
    jobject peer = JNFGetObjectField(env, target, jf_peer);

    static JNF_CLASS_CACHE(jc_ComponentPeer, "sun/lwawt/LWComponentPeer");
    static JNF_MEMBER_CACHE(jf_platformComponent, jc_ComponentPeer,
                            "platformComponent", "Lsun/lwawt/PlatformComponent;");
    jobject platformComponent = JNFGetObjectField(env, peer, jf_platformComponent);

    static JNF_CLASS_CACHE(jc_PlatformComponent, "sun/lwawt/macosx/CPlatformComponent");
    static JNF_MEMBER_CACHE(jm_getPointer, jc_PlatformComponent, "getPointer", "()J");
    AWTSurfaceLayers *surfaceLayers = jlong_to_ptr(JNFCallLongMethod(env, platformComponent, jm_getPointer));
    // REMIND: assert(surfaceLayers)

    dsi->platformInfo = surfaceLayers;
    dsi->ds = ds;

    static JNF_MEMBER_CACHE(jf_x, jc_Component, "x", "I");
    static JNF_MEMBER_CACHE(jf_y, jc_Component, "y", "I");
    static JNF_MEMBER_CACHE(jf_width, jc_Component, "width", "I");
    static JNF_MEMBER_CACHE(jf_height, jc_Component, "height", "I");

    dsi->bounds.x = JNFGetIntField(env, target, jf_x);
    dsi->bounds.y = JNFGetIntField(env, target, jf_y);
    dsi->bounds.width = JNFGetIntField(env, target, jf_width);
    dsi->bounds.height = JNFGetIntField(env, target, jf_height);

    dsi->clipSize = 1;
    dsi->clip = &(dsi->bounds);

    return dsi;
}

JNIEXPORT jint JNICALL awt_DrawingSurface_Lock
(JAWT_DrawingSurface* ds)
{
    // TODO: implement
    return 0;
}

JNIEXPORT void JNICALL awt_DrawingSurface_Unlock
(JAWT_DrawingSurface* ds)
{
    // TODO: implement
}

JNIEXPORT void JNICALL awt_DrawingSurface_FreeDrawingSurfaceInfo
(JAWT_DrawingSurfaceInfo* dsi)
{
    free(dsi);
}

JNIEXPORT JAWT_DrawingSurface* JNICALL awt_GetDrawingSurface
(JNIEnv* env, jobject target)
{
    JAWT_DrawingSurface* ds = (JAWT_DrawingSurface*)malloc(sizeof(JAWT_DrawingSurface));

    // TODO: "target instanceof" check

    ds->env = env;
    ds->target = (*env)->NewGlobalRef(env, target);
    ds->Lock = awt_DrawingSurface_Lock;
    ds->GetDrawingSurfaceInfo = awt_DrawingSurface_GetDrawingSurfaceInfo;
    ds->FreeDrawingSurfaceInfo = awt_DrawingSurface_FreeDrawingSurfaceInfo;
    ds->Unlock = awt_DrawingSurface_Unlock;

    return ds;
}

JNIEXPORT void JNICALL awt_FreeDrawingSurface
(JAWT_DrawingSurface* ds)
{
    JNIEnv *env = ds->env;
    (*env)->DeleteGlobalRef(env, ds->target);
    free(ds);
}

JNIEXPORT void JNICALL awt_Lock
(JNIEnv* env)
{
    // TODO: implement
}

JNIEXPORT void JNICALL awt_Unlock
(JNIEnv* env)
{
    // TODO: implement
}

JNIEXPORT jobject JNICALL awt_GetComponent
(JNIEnv* env, void* platformInfo)
{
    // TODO: implement
    return NULL;
}
