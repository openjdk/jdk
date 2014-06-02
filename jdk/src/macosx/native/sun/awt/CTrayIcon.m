/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

#import <AppKit/AppKit.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import "jni_util.h"

#import "CTrayIcon.h"
#import "ThreadUtilities.h"
#include "GeomUtilities.h"
#import "LWCToolkit.h"

#define kImageInset 4.0

/**
 * If the image of the specified size won't fit into the status bar,
 * then scale it down proprtionally. Otherwise, leave it as is.
 */
static NSSize ScaledImageSizeForStatusBar(NSSize imageSize) {
    NSRect imageRect = NSMakeRect(0.0, 0.0, imageSize.width, imageSize.height);

    // There is a black line at the bottom of the status bar
    // that we don't want to cover with image pixels.
    CGFloat desiredHeight = [[NSStatusBar systemStatusBar] thickness] - 1.0;
    CGFloat scaleFactor = MIN(1.0, desiredHeight/imageSize.height);

    imageRect.size.width *= scaleFactor;
    imageRect.size.height *= scaleFactor;
    imageRect = NSIntegralRect(imageRect);

    return imageRect.size;
}

@implementation AWTTrayIcon

- (id) initWithPeer:(jobject)thePeer {
    if (!(self = [super init])) return nil;

    peer = thePeer;

    theItem = [[NSStatusBar systemStatusBar] statusItemWithLength:NSVariableStatusItemLength];
    [theItem retain];

    view = [[AWTTrayIconView alloc] initWithTrayIcon:self];
    [theItem setView:view];

    return self;
}

-(void) dealloc {
    JNIEnv *env = [ThreadUtilities getJNIEnvUncached];
    JNFDeleteGlobalRef(env, peer);

    [[NSStatusBar systemStatusBar] removeStatusItem: theItem];

    // Its a bad idea to force the item to release our view by setting
    // the item's view to nil: it can lead to a crash in some scenarios.
    // The item will release the view later on, so just set the view's image
    // and tray icon to nil since we are done with it.
    [view setImage: nil];
    [view setTrayIcon: nil];
    [view release];

    [theItem release];

    [super dealloc];
}

- (void) setTooltip:(NSString *) tooltip{
    [view setToolTip:tooltip];
}

-(NSStatusItem *) theItem{
    return theItem;
}

- (jobject) peer{
    return peer;
}

- (void) setImage:(NSImage *) imagePtr sizing:(BOOL)autosize{
    NSSize imageSize = [imagePtr size];
    NSSize scaledSize = ScaledImageSizeForStatusBar(imageSize);
    if (imageSize.width != scaledSize.width ||
        imageSize.height != scaledSize.height) {
        [imagePtr setSize: scaledSize];
    }

    CGFloat itemLength = scaledSize.width + 2.0*kImageInset;
    [theItem setLength:itemLength];

    [view setImage:imagePtr];
}

- (NSPoint) getLocationOnScreen {
    return [[view window] convertBaseToScreen: NSZeroPoint];
}

-(void) deliverJavaMouseEvent: (NSEvent *) event {
    [AWTToolkit eventCountPlusPlus];

    JNIEnv *env = [ThreadUtilities getJNIEnv];

    NSPoint eventLocation = [event locationInWindow];
    NSPoint localPoint = [view convertPoint: eventLocation fromView: nil];
    localPoint.y = [view bounds].size.height - localPoint.y;

    NSPoint absP = [NSEvent mouseLocation];
    NSEventType type = [event type];

    NSRect screenRect = [[NSScreen mainScreen] frame];
    absP.y = screenRect.size.height - absP.y;
    jint clickCount;

    clickCount = [event clickCount];

    static JNF_CLASS_CACHE(jc_NSEvent, "sun/lwawt/macosx/NSEvent");
    static JNF_CTOR_CACHE(jctor_NSEvent, jc_NSEvent, "(IIIIIIIIDD)V");
    jobject jEvent = JNFNewObject(env, jctor_NSEvent,
                                  [event type],
                                  [event modifierFlags],
                                  clickCount,
                                  [event buttonNumber],
                                  (jint)localPoint.x, (jint)localPoint.y,
                                  (jint)absP.x, (jint)absP.y,
                                  [event deltaY],
                                  [event deltaX]);
    CHECK_NULL(jEvent);

    static JNF_CLASS_CACHE(jc_TrayIcon, "sun/lwawt/macosx/CTrayIcon");
    static JNF_MEMBER_CACHE(jm_handleMouseEvent, jc_TrayIcon, "handleMouseEvent", "(Lsun/lwawt/macosx/NSEvent;)V");
    JNFCallVoidMethod(env, peer, jm_handleMouseEvent, jEvent);
    (*env)->DeleteLocalRef(env, jEvent);
}

@end //AWTTrayIcon
//================================================

@implementation AWTTrayIconView

-(id)initWithTrayIcon:(AWTTrayIcon *)theTrayIcon {
    self = [super initWithFrame:NSMakeRect(0, 0, 1, 1)];

    [self setTrayIcon: theTrayIcon];
    isHighlighted = NO;
    image = nil;

    return self;
}

-(void) dealloc {
    [image release];
    [super dealloc];
}

- (void)setHighlighted:(BOOL)aFlag
{
    if (isHighlighted != aFlag) {
        isHighlighted = aFlag;
        [self setNeedsDisplay:YES];
    }
}

- (void)setImage:(NSImage*)anImage {
    [anImage retain];
    [image release];
    image = anImage;

    if (image != nil) {
        [self setNeedsDisplay:YES];
    }
}

-(void)setTrayIcon:(AWTTrayIcon*)theTrayIcon {
    trayIcon = theTrayIcon;
}

- (void)menuWillOpen:(NSMenu *)menu
{
    [self setHighlighted:YES];
}

- (void)menuDidClose:(NSMenu *)menu
{
    [menu setDelegate:nil];
    [self setHighlighted:NO];
}

- (void)drawRect:(NSRect)dirtyRect
{
    if (image == nil) {
        return;
    }

    NSRect bounds = [self bounds];
    NSSize imageSize = [image size];

    NSRect drawRect = {{ (bounds.size.width - imageSize.width) / 2.0,
        (bounds.size.height - imageSize.height) / 2.0 }, imageSize};

    // don't cover bottom pixels of the status bar with the image
    if (drawRect.origin.y < 1.0) {
        drawRect.origin.y = 1.0;
    }
    drawRect = NSIntegralRect(drawRect);

    [trayIcon.theItem drawStatusBarBackgroundInRect:bounds
                                withHighlight:isHighlighted];
    [image drawInRect:drawRect
             fromRect:NSZeroRect
            operation:NSCompositeSourceOver
             fraction:1.0
     ];
}

- (void)mouseDown:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];

    // don't show the menu on ctrl+click: it triggers ACTION event, like right click
    if (([event modifierFlags] & NSControlKeyMask) == 0) {
        //find CTrayIcon.getPopupMenuModel method and call it to get popup menu ptr.
        JNIEnv *env = [ThreadUtilities getJNIEnv];
        static JNF_CLASS_CACHE(jc_CTrayIcon, "sun/lwawt/macosx/CTrayIcon");
        static JNF_MEMBER_CACHE(jm_getPopupMenuModel, jc_CTrayIcon, "getPopupMenuModel", "()J");
        jlong res = JNFCallLongMethod(env, trayIcon.peer, jm_getPopupMenuModel);

        if (res != 0) {
            CPopupMenu *cmenu = jlong_to_ptr(res);
            NSMenu* menu = [cmenu menu];
            [menu setDelegate:self];
            [trayIcon.theItem popUpStatusItemMenu:menu];
            [self setNeedsDisplay:YES];
        }
    }
}

- (void) mouseUp:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}

- (void) mouseDragged:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}

- (void) rightMouseDown:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}

- (void) rightMouseUp:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}

- (void) rightMouseDragged:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}

- (void) otherMouseDown:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}

- (void) otherMouseUp:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}

- (void) otherMouseDragged:(NSEvent *)event {
    [trayIcon deliverJavaMouseEvent: event];
}


@end //AWTTrayIconView
//================================================

/*
 * Class:     sun_lwawt_macosx_CTrayIcon
 * Method:    nativeCreate
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CTrayIcon_nativeCreate
(JNIEnv *env, jobject peer) {
    __block AWTTrayIcon *trayIcon = nil;

JNF_COCOA_ENTER(env);

    jobject thePeer = JNFNewGlobalRef(env, peer);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        trayIcon = [[AWTTrayIcon alloc] initWithPeer:thePeer];
    }];

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(trayIcon);
}


/*
 * Class: java_awt_TrayIcon
 * Method: initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_java_awt_TrayIcon_initIDs
(JNIEnv *env, jclass cls) {
    //Do nothing.
}

/*
 * Class:     sun_lwawt_macosx_CTrayIcon
 * Method:    nativeSetToolTip
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CTrayIcon_nativeSetToolTip
(JNIEnv *env, jobject self, jlong model, jstring jtooltip) {
JNF_COCOA_ENTER(env);

    AWTTrayIcon *icon = jlong_to_ptr(model);
    NSString *tooltip = JNFJavaToNSString(env, jtooltip);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [icon setTooltip:tooltip];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CTrayIcon
 * Method:    setNativeImage
 * Signature: (JJZ)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CTrayIcon_setNativeImage
(JNIEnv *env, jobject self, jlong model, jlong imagePtr, jboolean autosize) {
JNF_COCOA_ENTER(env);

    AWTTrayIcon *icon = jlong_to_ptr(model);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        [icon setImage:jlong_to_ptr(imagePtr) sizing:autosize];
    }];

JNF_COCOA_EXIT(env);
}

JNIEXPORT jobject JNICALL
Java_sun_lwawt_macosx_CTrayIcon_nativeGetIconLocation
(JNIEnv *env, jobject self, jlong model) {
    jobject jpt = NULL;

JNF_COCOA_ENTER(env);

    __block NSPoint pt = NSZeroPoint;
    AWTTrayIcon *icon = jlong_to_ptr(model);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        NSPoint loc = [icon getLocationOnScreen];
        pt = ConvertNSScreenPoint(env, loc);
    }];

    jpt = NSToJavaPoint(env, pt);

JNF_COCOA_EXIT(env);

    return jpt;
}
