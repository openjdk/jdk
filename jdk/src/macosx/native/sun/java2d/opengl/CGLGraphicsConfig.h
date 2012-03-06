/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CGLGraphicsConfig_h_Included
#define CGLGraphicsConfig_h_Included

#import "jni.h"
#import "J2D_GL/gl.h"
#import "OGLSurfaceData.h"
#import "OGLContext.h"
#import <Cocoa/Cocoa.h>

@interface GraphicsConfigUtil : NSObject {}
+ (void) _getCGLConfigInfo: (NSMutableArray *)argValue;
@end

// REMIND: Using an NSOpenGLPixelBuffer as the scratch surface has been
// problematic thus far (seeing garbage and flickering when switching
// between an NSView and the scratch surface), so the following enables
// an alternate codepath that uses a hidden NSWindow/NSView as the scratch
// surface, for the purposes of making a context current in certain
// situations.  It appears that calling [NSOpenGLContext setView] too
// frequently contributes to the bad behavior, so we should try to avoid
// switching to the scratch surface whenever possible.

/* Do we need this if we are using all off-screen drawing ? */
#define USE_NSVIEW_FOR_SCRATCH 1

/* Uncomment to have an additional CAOGLLayer instance tied to
 * each instance, which can be used to test remoting the layer
 * to an out of process window. The additional layer is needed
 * because a layer can only be attached to one context (view/window).
 * This is only for testing purposes and can be removed if/when no
 * longer needed.
 */
//#define REMOTELAYER 1

#ifdef REMOTELAYER
#import <JavaRuntimeSupport/JRSRemoteLayer.h>
#import <pthread.h>
#include <unistd.h>
#include <stdio.h>
#import <sys/socket.h>
#import <sys/un.h>

extern mach_port_t JRSRemotePort;
extern int remoteSocketFD;
extern void sendLayerID(int layerID);

#endif /* REMOTELAYER */


/**
 * The CGLGraphicsConfigInfo structure contains information specific to a
 * given CGLGraphicsConfig (pixel format).
 *
 *     jint screen;
 * The screen and PixelFormat for the associated CGLGraphicsConfig.
 *
 *     NSOpenGLPixelFormat *pixfmt;
 * The pixel format of the native NSOpenGL context.
 *
 *     OGLContext *context;
 * The context associated with this CGLGraphicsConfig.
 */
typedef struct _CGLGraphicsConfigInfo {
    jint                screen;
    NSOpenGLPixelFormat *pixfmt;
    OGLContext          *context;
} CGLGraphicsConfigInfo;

/**
 * The CGLCtxInfo structure contains the native CGLContext information
 * required by and is encapsulated by the platform-independent OGLContext
 * structure.
 *
 *     NSOpenGLContext *context;
 * The core native NSOpenGL context.  Rendering commands have no effect until
 * a context is made current (active).
 *
 *     NSOpenGLPixelBuffer *scratchSurface;
 * The scratch surface id used to make a context current when we do
 * not otherwise have a reference to an OpenGL surface for the purposes of
 * making a context current.
 */
typedef struct _CGLCtxInfo {
    NSOpenGLContext     *context;
#if USE_NSVIEW_FOR_SCRATCH
    NSView              *scratchSurface;
#else
    NSOpenGLPixelBuffer *scratchSurface;
#endif
} CGLCtxInfo;

#endif /* CGLGraphicsConfig_h_Included */
