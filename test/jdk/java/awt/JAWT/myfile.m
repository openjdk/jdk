/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#import "JAVAVM/jawt_md.h"
#import <Quartz/Quartz.h>

/*
 * Pass the block to a selector of a class that extends NSObject
 * There is no need to copy the block since this class always waits.
 */
@interface BlockRunner : NSObject { }

+ (void)invokeBlock:(void (^)())block;
@end

@implementation BlockRunner

+ (void)invokeBlock:(void (^)())block{
  block();
}

+ (void)performBlock:(void (^)())block {
  [self performSelectorOnMainThread:@selector(invokeBlock:) withObject:block waitUntilDone:YES];
}

@end


/*
 * Class:	MyMacCanvas
 * Method:	paint
 * SIgnature:	(Ljava/awt/Graphics;)V
 */
JNIEXPORT void JNICALL Java_MyMacCanvas_addNativeCoreAnimationLayer
(JNIEnv* env, jobject canvas, jobject graphics)
{
    printf("paint called\n");

    JAWT awt;
    awt.version = JAWT_VERSION_1_4 | JAWT_MACOSX_USE_CALAYER;
    if (JAWT_GetAWT(env, &awt) == JNI_FALSE) {
        printf("AWT Not found\n");
        return;
    }

    printf("JAWT found\n");

    /* Get the drawing surface */
    JAWT_DrawingSurface* ds = awt.GetDrawingSurface(env, canvas);
    if (ds == NULL) {
        printf("NULL drawing surface\n");
        return;
    }

    /* Lock the drawing surface */
    jint lock = ds->Lock(ds);
    printf("Lock value %d\n", (int)lock);
    if((lock & JAWT_LOCK_ERROR) != 0) {
        printf("Error locking surface");
        return;
    }

    /* Get the drawing surface info */
    JAWT_DrawingSurfaceInfo *dsi = ds->GetDrawingSurfaceInfo(ds);
    if (dsi == NULL) {
        printf("Error getting surface info\n");
        ds->Unlock(ds);
        return;
    }

    // create and attach the layer on the AppKit thread
    void (^block)() = ^(){
        // attach the "root layer" to the AWT Canvas surface layers
        CALayer *layer = [[CALayer new] autorelease];
        id <JAWT_SurfaceLayers> surfaceLayers = (id <JAWT_SurfaceLayers>)dsi->platformInfo;
        CGColorSpaceRef colorspace = CGColorSpaceCreateDeviceRGB ();
        CGFloat rgba[4] = {0.0, 0.0, 0.0, 1.0};
        CGColorRef color = CGColorCreate (colorspace, rgba);
        layer.backgroundColor = color;
        surfaceLayers.layer = layer;
    };

    if ([NSThread isMainThread]) {
        block();
    } else {
        [BlockRunner performBlock:block];
    }

    /* Free the drawing surface info */
    ds->FreeDrawingSurfaceInfo(dsi);

    /* Unlock the drawing surface */
    ds->Unlock(ds);

    /* Free the drawing surface */
    awt.FreeDrawingSurface(ds);
}
