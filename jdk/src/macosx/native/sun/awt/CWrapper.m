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

#import "CWrapper.h"

#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "AWTWindow.h"
#import "LWCToolkit.h"
#import "GeomUtilities.h"
#import "ThreadUtilities.h"

#import "sun_lwawt_macosx_CWrapper_NSWindow.h"

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSObject
 * Method:    release
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSObject_release
(JNIEnv *env, jclass cls, jlong objectPtr)
{
JNF_COCOA_ENTER(env);

    id obj = (id)jlong_to_ptr(objectPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        CFRelease(obj);
    }];

JNF_COCOA_EXIT(env);
}


/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    makeKeyAndOrderFront
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_makeKeyAndOrderFront
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(makeKeyAndOrderFront:)
                                      on:window
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    makeKeyWindow
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_makeKeyWindow
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(makeKeyWindow)
                                      on:window
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    makeMainWindow
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_makeMainWindow
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(makeMainWindow)
                                      on:window
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    canBecomeMainWindow
 * Signature: (J)V
 */
JNIEXPORT jboolean JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_canBecomeMainWindow
(JNIEnv *env, jclass cls, jlong windowPtr)
{
    __block jboolean canBecomeMainWindow = JNI_FALSE;

JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        canBecomeMainWindow = [window canBecomeMainWindow];
    }];

JNF_COCOA_EXIT(env);

    return canBecomeMainWindow;
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    isKeyWindow
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_isKeyWindow
(JNIEnv *env, jclass cls, jlong windowPtr)
{
    __block jboolean isKeyWindow = JNI_FALSE;

JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        isKeyWindow = [window isKeyWindow];
    }];

JNF_COCOA_EXIT(env);

    return isKeyWindow;
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    orderFront
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_orderFront
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(orderFront:)
                                      on:window
                              withObject:window
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    orderOut
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_orderOut
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(orderOut:)
                                      on:window
                              withObject:window
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    orderFrontRegardless
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_orderFrontRegardless
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(orderFrontRegardless)
                                      on:window
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    orderWindow
 * Signature: (JIJ)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_orderWindow
(JNIEnv *env, jclass cls, jlong windowPtr, jint order, jlong relativeToPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    NSWindow *relativeTo = (NSWindow *)jlong_to_ptr(relativeToPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [window orderWindow:(NSWindowOrderingMode)order relativeTo:[relativeTo windowNumber]];
    }];

JNF_COCOA_EXIT(env);
}

// Used for CWrapper.NSWindow.setLevel() (and level() which isn't implemented yet)
static NSInteger LEVELS[sun_lwawt_macosx_CWrapper_NSWindow_MAX_WINDOW_LEVELS];
static void initLevels()
{
    static dispatch_once_t pred;

    dispatch_once(&pred, ^{
        LEVELS[sun_lwawt_macosx_CWrapper_NSWindow_NSNormalWindowLevel] = NSNormalWindowLevel;
        LEVELS[sun_lwawt_macosx_CWrapper_NSWindow_NSFloatingWindowLevel] = NSFloatingWindowLevel;
    });
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    setLevel
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_setLevel
(JNIEnv *env, jclass cls, jlong windowPtr, jint level)
{
JNF_COCOA_ENTER(env);

    if (level >= 0 && level < sun_lwawt_macosx_CWrapper_NSWindow_MAX_WINDOW_LEVELS) {
        initLevels();

        NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
        [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
            [window setLevel: LEVELS[level]];
        }];
    } else {
        [JNFException raise:env as:kIllegalArgumentException reason:"unknown level"];
    }

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    addChildWindow
 * Signature: (JJI)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_addChildWindow
(JNIEnv *env, jclass cls, jlong parentPtr, jlong childPtr, jint order)
{
JNF_COCOA_ENTER(env);

    NSWindow *parent = (NSWindow *)jlong_to_ptr(parentPtr);
    NSWindow *child = (NSWindow *)jlong_to_ptr(childPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [parent addChildWindow:child ordered:order];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    removeChildWindow
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_removeChildWindow
(JNIEnv *env, jclass cls, jlong parentPtr, jlong childPtr)
{
JNF_COCOA_ENTER(env);

    AWTWindow *parent = (AWTWindow *)jlong_to_ptr(parentPtr);
    AWTWindow *child = (AWTWindow *)jlong_to_ptr(childPtr);
    [ThreadUtilities performOnMainThread:@selector(removeChildWindow:)
                                      on:parent
                              withObject:child
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    setFrame
 * Signature: (JIIIIZ)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_setFrame
(JNIEnv *env, jclass cls, jlong windowPtr, jint x, jint y, jint w, jint h, jboolean display)
{
JNF_COCOA_ENTER(env);

    AWTWindow *window = (AWTWindow *)jlong_to_ptr(windowPtr);
    NSRect frame = NSMakeRect(x, y, w, h);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [window setFrame:frame display:display];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    setAlphaValue
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_setAlphaValue
(JNIEnv *env, jclass cls, jlong windowPtr, jfloat alpha)
{
JNF_COCOA_ENTER(env);

    AWTWindow *window = (AWTWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [window setAlphaValue:(CGFloat)alpha];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    setOpaque
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_setOpaque
(JNIEnv *env, jclass cls, jlong windowPtr, jboolean opaque)
{
JNF_COCOA_ENTER(env);

    AWTWindow *window = (AWTWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [window setOpaque:(BOOL)opaque];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    setBackgroundColor
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_setBackgroundColor
(JNIEnv *env, jclass cls, jlong windowPtr, jlong colorPtr)
{
JNF_COCOA_ENTER(env);

    AWTWindow *window = (AWTWindow *)jlong_to_ptr(windowPtr);
    NSColor *color = (NSColor *)jlong_to_ptr(colorPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [window setBackgroundColor:color];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    screen
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_screen
(JNIEnv *env, jclass cls, jlong windowPtr)
{
    __block jlong screenPtr = 0L;

JNF_COCOA_ENTER(env);

    AWTWindow *window = (AWTWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        const NSScreen *screen = [window screen];
        CFRetain(screen); // GC
        screenPtr = ptr_to_jlong(screen);
    }];

JNF_COCOA_EXIT(env);

    return screenPtr;
}

/*
 * Method:    miniaturize
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_miniaturize
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(miniaturize:)
                                      on:window
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    deminiaturize
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_deminiaturize
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(deminiaturize:)
                                      on:window
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    zoom
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_zoom
(JNIEnv *env, jclass cls, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    [ThreadUtilities performOnMainThread:@selector(zoom:)
                                      on:window
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSWindow
 * Method:    makeFirstResponder
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSWindow_makeFirstResponder
(JNIEnv *env, jclass cls, jlong windowPtr, jlong responderPtr)
{
JNF_COCOA_ENTER(env);

    NSWindow *window = (NSWindow *)jlong_to_ptr(windowPtr);
    NSResponder *responder = (NSResponder *)jlong_to_ptr(responderPtr);
    [ThreadUtilities performOnMainThread:@selector(makeFirstResponder:)
                                      on:window
                              withObject:responder
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    addSubview
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_addSubview
(JNIEnv *env, jclass cls, jlong viewPtr, jlong subviewPtr)
{
JNF_COCOA_ENTER(env);

    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    NSView *subview = (NSView *)jlong_to_ptr(subviewPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        [view addSubview:subview];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    removeFromSuperview
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_removeFromSuperview
(JNIEnv *env, jclass cls, jlong viewPtr)
{
JNF_COCOA_ENTER(env);

    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    [ThreadUtilities performOnMainThread:@selector(removeFromSuperview)
                                      on:view
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    setFrame
 * Signature: (JIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_setFrame
(JNIEnv *env, jclass cls, jlong viewPtr, jint x, jint y, jint w, jint h)
{
JNF_COCOA_ENTER(env);

    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [view setFrame:NSMakeRect(x, y, w, h)];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    frame
 * Signature: (J)Ljava/awt/Rectangle;
 */
JNIEXPORT jobject JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_frame
(JNIEnv *env, jclass cls, jlong viewPtr)
{
    jobject jRect = NULL;

JNF_COCOA_ENTER(env);

    __block NSRect rect = NSZeroRect;

    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        rect = [view frame];
    }];

    jRect = NSToJavaRect(env, rect);

JNF_COCOA_EXIT(env);

    return jRect;
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    enterFullScreenMode
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_enterFullScreenMode
(JNIEnv *env, jclass cls, jlong viewPtr)
{
JNF_COCOA_ENTER(env);

    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        NSScreen *screen = [[view window] screen];
        NSDictionary *opts = [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithBool:NO], NSFullScreenModeAllScreens, nil];
        [view enterFullScreenMode:screen withOptions:opts];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    exitFullScreenMode
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_exitFullScreenMode
(JNIEnv *env, jclass cls, jlong viewPtr)
{
JNF_COCOA_ENTER(env);

    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [view exitFullScreenModeWithOptions:nil];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    window
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_window
(JNIEnv *env, jclass cls, jlong viewPtr)
{
    __block jlong windowPtr = 0L;

JNF_COCOA_ENTER(env);

    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        windowPtr = ptr_to_jlong([view window]);
    }];

JNF_COCOA_EXIT(env);

    return windowPtr;
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSView
 * Method:    setHidden
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSView_setHidden
(JNIEnv *env, jclass cls, jlong viewPtr, jboolean toHide)
{    
    JNF_COCOA_ENTER(env);
    
    NSView *view = (NSView *)jlong_to_ptr(viewPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [view setHidden:(BOOL)toHide];
    }];
    
    JNF_COCOA_EXIT(env);
}


/*
 * Class:     sun_lwawt_macosx_CWrapper$NSScreen
 * Method:    frame
 * Signature: (J)Ljava/awt/Rectangle;
 */
JNIEXPORT jobject JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSScreen_frame
(JNIEnv *env, jclass cls, jlong screenPtr)
{
    jobject jRect = NULL;

JNF_COCOA_ENTER(env);

    __block NSRect rect = NSZeroRect;

    NSScreen *screen = (NSScreen *)jlong_to_ptr(screenPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        rect = [screen frame];
    }];

    jRect = NSToJavaRect(env, rect);

JNF_COCOA_EXIT(env);

    return jRect;
}

/*
 * Class:     sun_lwawt_macosx_CWrapper_NSScreen
 * Method:    visibleFrame
 * Signature: (J)Ljava/awt/geom/Rectangle2D;
 */
JNIEXPORT jobject JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSScreen_visibleFrame
(JNIEnv *env, jclass cls, jlong screenPtr)
{
    jobject jRect = NULL;

JNF_COCOA_ENTER(env);

    __block NSRect rect = NSZeroRect;

    NSScreen *screen = (NSScreen *)jlong_to_ptr(screenPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        rect = [screen visibleFrame];
    }];

    jRect = NSToJavaRect(env, rect);

JNF_COCOA_EXIT(env);

    return jRect;
}

/*
 * Class:     sun_lwawt_macosx_CWrapper_NSScreen
 * Method:    screenByDisplayId
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSScreen_screenByDisplayId
(JNIEnv *env, jclass cls, jint displayID)
{
    __block jlong screenPtr = 0L;

JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        NSArray *screens = [NSScreen screens];
        for (NSScreen *screen in screens) {
            NSDictionary *screenInfo = [screen deviceDescription];
            NSNumber *screenID = [screenInfo objectForKey:@"NSScreenNumber"];
            if ([screenID intValue] == displayID){
                CFRetain(screen); // GC
                screenPtr = ptr_to_jlong(screen);
                break;
            }
        }
    }];

JNF_COCOA_EXIT(env);

    return screenPtr;
}

/*
 * Class:     sun_lwawt_macosx_CWrapper$NSColor
 * Method:    clearColor
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CWrapper_00024NSColor_clearColor
(JNIEnv *env, jclass cls)
{
    __block jlong clearColorPtr = 0L;

JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        clearColorPtr = ptr_to_jlong([NSColor clearColor]);
    }];

JNF_COCOA_EXIT(env);

    return clearColorPtr;
}

