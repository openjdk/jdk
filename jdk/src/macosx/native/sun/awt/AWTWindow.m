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

#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>

#import "sun_lwawt_macosx_CPlatformWindow.h"
#import "com_apple_eawt_event_GestureHandler.h"
#import "com_apple_eawt_FullScreenHandler.h"

#import "AWTWindow.h"
#import "AWTView.h"
#import "CMenu.h"
#import "CMenuBar.h"
#import "LWCToolkit.h"
#import "GeomUtilities.h"
#import "ThreadUtilities.h"
#import "OSVersion.h"


#define MASK(KEY) \
    (sun_lwawt_macosx_CPlatformWindow_ ## KEY)

#define IS(BITS, KEY) \
    ((BITS & MASK(KEY)) != 0)

#define SET(BITS, KEY, VALUE) \
    BITS = VALUE ? BITS | MASK(KEY) : BITS & ~MASK(KEY)


static JNF_CLASS_CACHE(jc_CPlatformWindow, "sun/lwawt/macosx/CPlatformWindow");

@interface JavaResizeGrowBoxOverlayWindow : NSWindow { }

@end

@implementation JavaResizeGrowBoxOverlayWindow

- (BOOL) accessibilityIsIgnored
{
    return YES;
}

- (NSArray *)accessibilityChildrenAttribute
{
    return nil;
}
@end

@implementation AWTWindow

@synthesize javaPlatformWindow;
@synthesize javaMenuBar;
@synthesize growBoxWindow;
@synthesize javaMinSize;
@synthesize javaMaxSize;
@synthesize styleBits;

- (void) updateMinMaxSize:(BOOL)resizable {
    if (resizable) {
        [self setMinSize:self.javaMinSize];
        [self setMaxSize:self.javaMaxSize];
    } else {
        NSRect currentFrame = [self frame];
        [self setMinSize:currentFrame.size];
        [self setMaxSize:currentFrame.size];
    }
}

// creates a new NSWindow style mask based on the _STYLE_PROP_BITMASK bits
+ (NSUInteger) styleMaskForStyleBits:(jint)styleBits {
    NSUInteger type = 0;
    if (IS(styleBits, DECORATED)) {
        type |= NSTitledWindowMask;
        if (IS(styleBits, CLOSEABLE))   type |= NSClosableWindowMask;
        if (IS(styleBits, MINIMIZABLE)) type |= NSMiniaturizableWindowMask;
        if (IS(styleBits, RESIZABLE))   type |= NSResizableWindowMask;
    } else {
        type |= NSBorderlessWindowMask;
    }

    if (IS(styleBits, TEXTURED))    type |= NSTexturedBackgroundWindowMask;
    if (IS(styleBits, UNIFIED))     type |= NSUnifiedTitleAndToolbarWindowMask;
    if (IS(styleBits, UTILITY))     type |= NSUtilityWindowMask;
    if (IS(styleBits, HUD))         type |= NSHUDWindowMask;
    if (IS(styleBits, SHEET))       type |= NSDocModalWindowMask;

    return type;
}

// updates _METHOD_PROP_BITMASK based properties on the window
- (void) setPropertiesForStyleBits:(jint)bits mask:(jint)mask {
    if (IS(mask, RESIZABLE)) {
        BOOL resizable = IS(bits, RESIZABLE);
        [self updateMinMaxSize:resizable];
        [self setShowsResizeIndicator:resizable];
    }

    if (IS(mask, HAS_SHADOW)) {
        [self setHasShadow:IS(bits, HAS_SHADOW)];
    }

    if (IS(mask, ZOOMABLE)) {
        [[self standardWindowButton:NSWindowZoomButton] setEnabled:IS(bits, ZOOMABLE)];
    }

    if (IS(mask, ALWAYS_ON_TOP)) {
        [self setLevel:IS(bits, ALWAYS_ON_TOP) ? NSFloatingWindowLevel : NSNormalWindowLevel];
    }

    if (IS(mask, HIDES_ON_DEACTIVATE)) {
        [self setHidesOnDeactivate:IS(bits, HIDES_ON_DEACTIVATE)];
    }

    if (IS(mask, DRAGGABLE_BACKGROUND)) {
        [self setMovableByWindowBackground:IS(bits, DRAGGABLE_BACKGROUND)];
    }

    if (IS(mask, DOCUMENT_MODIFIED)) {
        [self setDocumentEdited:IS(bits, DOCUMENT_MODIFIED)];
    }

    if ([self respondsToSelector:@selector(toggleFullScreen:)]) {
        if (IS(mask, FULLSCREENABLE)) {
            [self setCollectionBehavior:(1 << 7) /*NSWindowCollectionBehaviorFullScreenPrimary*/];
        } else {
            [self setCollectionBehavior:NSWindowCollectionBehaviorDefault];
        }
    }

}

- (BOOL) shouldShowGrowBox {
    return isSnowLeopardOrLower() && IS(self.styleBits, RESIZABLE);
}

- (NSImage *) createGrowBoxImage {
    NSImage *image = [[NSImage alloc] initWithSize:NSMakeSize(12, 12)];
    JRSUIControlRef growBoxWidget = JRSUIControlCreate(FALSE);
    JRSUIControlSetWidget(growBoxWidget, kJRSUI_Widget_growBoxTextured);
    JRSUIControlSetWindowType(growBoxWidget, kJRSUI_WindowType_utility);
    JRSUIRendererRef renderer = JRSUIRendererCreate();
    [image lockFocus]; // sets current graphics context to that of the image
    JRSUIControlDraw(renderer, growBoxWidget, [[NSGraphicsContext currentContext] graphicsPort], CGRectMake(0, 1, 11, 11));
    [image unlockFocus];
    JRSUIRendererRelease(renderer);
    JRSUIControlRelease(growBoxWidget);
    return image;
}

- (id) initWithPlatformWindow:(JNFWeakJObjectWrapper *)platformWindow
                    styleBits:(jint)bits
                    frameRect:(NSRect)rect
                  contentView:(NSView *)view
{
AWT_ASSERT_APPKIT_THREAD;

    NSUInteger styleMask = [AWTWindow styleMaskForStyleBits:bits];
    NSRect contentRect = rect; //[NSWindow contentRectForFrameRect:rect styleMask:styleMask];
    if (contentRect.size.width <= 0.0) {
        contentRect.size.width = 1.0;
    }
    if (contentRect.size.height <= 0.0) {
        contentRect.size.height = 1.0;
    }

    self = [super initWithContentRect:contentRect
                            styleMask:styleMask
                              backing:NSBackingStoreBuffered
                                defer:NO];

    if (self == nil) return nil; // no hope

    self.javaPlatformWindow = platformWindow;
    self.styleBits = bits;
    [self setPropertiesForStyleBits:styleBits mask:MASK(_METHOD_PROP_BITMASK)];

    [self setDelegate:self];
    [self setContentView:view];
    [self setInitialFirstResponder:view];
    [self setReleasedWhenClosed:NO];
    [self setPreservesContentDuringLiveResize:YES];

    if ([self shouldShowGrowBox]) {
        NSImage *growBoxImage = [self createGrowBoxImage];
        growBoxWindow = [[JavaResizeGrowBoxOverlayWindow alloc] initWithContentRect:NSMakeRect(0, 0, [growBoxImage size].width, [growBoxImage size].height) styleMask:NSBorderlessWindowMask backing:NSBackingStoreBuffered defer:NO];
        [self.growBoxWindow setIgnoresMouseEvents:YES];
        [self.growBoxWindow setOpaque:NO];
        [self.growBoxWindow setBackgroundColor:[NSColor clearColor]];
        [self.growBoxWindow setHasShadow:NO];
        [self.growBoxWindow setReleasedWhenClosed:NO];

        NSImageView *imageView = [[NSImageView alloc] initWithFrame:[self.growBoxWindow frame]];
        [imageView setEditable:NO];
        [imageView setAnimates:NO];
        [imageView setAllowsCutCopyPaste:NO];
        [self.growBoxWindow setContentView:imageView];
        [imageView setImage:growBoxImage];
        [growBoxImage release];
        [imageView release];

        [self addChildWindow:self.growBoxWindow ordered:NSWindowAbove];
        [self adjustGrowBoxWindow];
    } else growBoxWindow = nil;

    return self;
}

- (void) dealloc {
AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    [self.javaPlatformWindow setJObject:nil withEnv:env];
    self.growBoxWindow = nil;

    [super dealloc];
}


// NSWindow overrides
- (BOOL) canBecomeKeyWindow {
AWT_ASSERT_APPKIT_THREAD;
    return IS(self.styleBits, SHOULD_BECOME_KEY);
}

- (BOOL) canBecomeMainWindow {
AWT_ASSERT_APPKIT_THREAD;
    return IS(self.styleBits, SHOULD_BECOME_MAIN);
}

- (BOOL) worksWhenModal {
AWT_ASSERT_APPKIT_THREAD;
    return IS(self.styleBits, MODAL_EXCLUDED);
}


// Gesture support
- (void)postGesture:(NSEvent *)event as:(jint)type a:(jdouble)a b:(jdouble)b {
AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        // extract the target AWT Window object out of the CPlatformWindow
        static JNF_MEMBER_CACHE(jf_target, jc_CPlatformWindow, "target", "Ljava/awt/Window;");
        jobject awtWindow = JNFGetObjectField(env, platformWindow, jf_target);
        if (awtWindow != NULL) {
            // translate the point into Java coordinates
            NSPoint loc = [event locationInWindow];
            loc.y = [self frame].size.height - loc.y;

            // send up to the GestureHandler to recursively dispatch on the AWT event thread
            static JNF_CLASS_CACHE(jc_GestureHandler, "com/apple/eawt/event/GestureHandler");
            static JNF_STATIC_MEMBER_CACHE(sjm_handleGestureFromNative, jc_GestureHandler, "handleGestureFromNative", "(Ljava/awt/Window;IDDDD)V");
            JNFCallStaticVoidMethod(env, sjm_handleGestureFromNative, awtWindow, type, (jdouble)loc.x, (jdouble)loc.y, (jdouble)a, (jdouble)b);
            (*env)->DeleteLocalRef(env, awtWindow);
        }
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}

- (void)beginGestureWithEvent:(NSEvent *)event {
    [self postGesture:event
                   as:com_apple_eawt_event_GestureHandler_PHASE
                    a:-1.0
                    b:0.0];
}

- (void)endGestureWithEvent:(NSEvent *)event {
    [self postGesture:event
                   as:com_apple_eawt_event_GestureHandler_PHASE
                    a:1.0
                    b:0.0];
}

- (void)magnifyWithEvent:(NSEvent *)event {
    [self postGesture:event
                   as:com_apple_eawt_event_GestureHandler_MAGNIFY
                    a:[event magnification]
                    b:0.0];
}

- (void)rotateWithEvent:(NSEvent *)event {
    [self postGesture:event
                   as:com_apple_eawt_event_GestureHandler_ROTATE
                    a:[event rotation]
                    b:0.0];
}

- (void)swipeWithEvent:(NSEvent *)event {
    [self postGesture:event
                   as:com_apple_eawt_event_GestureHandler_SWIPE
                    a:[event deltaX]
                    b:[event deltaY]];
}


// NSWindowDelegate methods

- (void) adjustGrowBoxWindow {
    if (self.growBoxWindow != nil) {
        NSRect parentRect = [self frame];
        parentRect.origin.x += (parentRect.size.width - [self.growBoxWindow frame].size.width);
        [self.growBoxWindow setFrameOrigin:parentRect.origin];
    }
}

- (void) _deliverMoveResizeEvent {
AWT_ASSERT_APPKIT_THREAD;

    // deliver the event if this is a user-initiated live resize or as a side-effect
    // of a Java initiated resize, because AppKit can override the bounds and force
    // the bounds of the window to avoid the Dock or remain on screen.
    [AWTToolkit eventCountPlusPlus];
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow == NULL) {
        // TODO: create generic AWT assert
    }

    [self adjustGrowBoxWindow];

    NSRect frame = ConvertNSScreenRect(env, [self frame]);

    static JNF_MEMBER_CACHE(jm_deliverMoveResizeEvent, jc_CPlatformWindow, "deliverMoveResizeEvent", "(IIII)V");
    JNFCallVoidMethod(env, platformWindow, jm_deliverMoveResizeEvent,
                      (jint)frame.origin.x,
                      (jint)frame.origin.y,
                      (jint)frame.size.width,
                      (jint)frame.size.height);
    (*env)->DeleteLocalRef(env, platformWindow);
}

- (void)windowDidMove:(NSNotification *)notification {
AWT_ASSERT_APPKIT_THREAD;

    [self _deliverMoveResizeEvent];
}

- (void)windowDidResize:(NSNotification *)notification {
AWT_ASSERT_APPKIT_THREAD;

    [self _deliverMoveResizeEvent];
}

- (void)windowDidExpose:(NSNotification *)notification {
AWT_ASSERT_APPKIT_THREAD;

    [AWTToolkit eventCountPlusPlus];
    // TODO: don't see this callback invoked anytime so we track
    // window exposing in _setVisible:(BOOL)
}

- (BOOL)windowShouldZoom:(NSWindow *)window toFrame:(NSRect)proposedFrame {
AWT_ASSERT_APPKIT_THREAD;

    [AWTToolkit eventCountPlusPlus];
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        static JNF_MEMBER_CACHE(jm_deliverZoom, jc_CPlatformWindow, "deliverZoom", "(Z)V");
        JNFCallVoidMethod(env, platformWindow, jm_deliverZoom, ![window isZoomed]);
        (*env)->DeleteLocalRef(env, platformWindow);
    }
    return YES;
}

- (void) _deliverIconify:(BOOL)iconify {
AWT_ASSERT_APPKIT_THREAD;

    [AWTToolkit eventCountPlusPlus];
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        static JNF_MEMBER_CACHE(jm_deliverIconify, jc_CPlatformWindow, "deliverIconify", "(Z)V");
        JNFCallVoidMethod(env, platformWindow, jm_deliverIconify, iconify);
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}

- (void)windowDidMiniaturize:(NSNotification *)notification {
AWT_ASSERT_APPKIT_THREAD;

    [self _deliverIconify:JNI_TRUE];
}

- (void)windowDidDeminiaturize:(NSNotification *)notification {
AWT_ASSERT_APPKIT_THREAD;

    [self _deliverIconify:JNI_FALSE];
}

- (void) _deliverWindowFocusEvent:(BOOL)focused {
//AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnvUncached];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        static JNF_MEMBER_CACHE(jm_deliverWindowFocusEvent, jc_CPlatformWindow, "deliverWindowFocusEvent", "(Z)V");
        JNFCallVoidMethod(env, platformWindow, jm_deliverWindowFocusEvent, (jboolean)focused);
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}


- (void) windowDidBecomeKey: (NSNotification *) notification {
AWT_ASSERT_APPKIT_THREAD;
    [AWTToolkit eventCountPlusPlus];
    [CMenuBar activate:self.javaMenuBar modallyDisabled:NO];
    [self _deliverWindowFocusEvent:YES];
}

- (void) windowDidResignKey: (NSNotification *) notification {
    // TODO: check why sometimes at start is invoked *not* on AppKit main thread.
AWT_ASSERT_APPKIT_THREAD;
    [AWTToolkit eventCountPlusPlus];
    [self.javaMenuBar deactivate];
    [self _deliverWindowFocusEvent:NO];
}

- (void) windowDidBecomeMain: (NSNotification *) notification {
AWT_ASSERT_APPKIT_THREAD;
    [AWTToolkit eventCountPlusPlus];

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        static JNF_MEMBER_CACHE(jm_windowDidBecomeMain, jc_CPlatformWindow, "windowDidBecomeMain", "()V");
        JNFCallVoidMethod(env, platformWindow, jm_windowDidBecomeMain);
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}

- (BOOL)windowShouldClose:(id)sender {
AWT_ASSERT_APPKIT_THREAD;
    [AWTToolkit eventCountPlusPlus];
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        static JNF_MEMBER_CACHE(jm_deliverWindowClosingEvent, jc_CPlatformWindow, "deliverWindowClosingEvent", "()V");
        JNFCallVoidMethod(env, platformWindow, jm_deliverWindowClosingEvent);
        (*env)->DeleteLocalRef(env, platformWindow);
    }
    // The window will be closed (if allowed) as result of sending Java event
    return NO;
}


- (void)_notifyFullScreenOp:(jint)op withEnv:(JNIEnv *)env {
    static JNF_CLASS_CACHE(jc_FullScreenHandler, "com/apple/eawt/FullScreenHandler");
    static JNF_STATIC_MEMBER_CACHE(jm_notifyFullScreenOperation, jc_FullScreenHandler, "handleFullScreenEventFromNative", "(Ljava/awt/Window;I)V");
    static JNF_MEMBER_CACHE(jf_target, jc_CPlatformWindow, "target", "Ljava/awt/Window;");
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        jobject awtWindow = JNFGetObjectField(env, platformWindow, jf_target);
        if (awtWindow != NULL) {
            JNFCallStaticVoidMethod(env, jm_notifyFullScreenOperation, awtWindow, op);
            (*env)->DeleteLocalRef(env, awtWindow);
        }
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}


- (void)windowWillEnterFullScreen:(NSNotification *)notification {
    static JNF_MEMBER_CACHE(jm_windowWillEnterFullScreen, jc_CPlatformWindow, "windowWillEnterFullScreen", "()V");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        JNFCallVoidMethod(env, platformWindow, jm_windowWillEnterFullScreen);
        [self _notifyFullScreenOp:com_apple_eawt_FullScreenHandler_FULLSCREEN_WILL_ENTER withEnv:env];
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}

- (void)windowDidEnterFullScreen:(NSNotification *)notification {
    static JNF_MEMBER_CACHE(jm_windowDidEnterFullScreen, jc_CPlatformWindow, "windowDidEnterFullScreen", "()V");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        JNFCallVoidMethod(env, platformWindow, jm_windowDidEnterFullScreen);
        [self _notifyFullScreenOp:com_apple_eawt_FullScreenHandler_FULLSCREEN_DID_ENTER withEnv:env];
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}

- (void)windowWillExitFullScreen:(NSNotification *)notification {
    static JNF_MEMBER_CACHE(jm_windowWillExitFullScreen, jc_CPlatformWindow, "windowWillExitFullScreen", "()V");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        JNFCallVoidMethod(env, platformWindow, jm_windowWillExitFullScreen);
        [self _notifyFullScreenOp:com_apple_eawt_FullScreenHandler_FULLSCREEN_WILL_EXIT withEnv:env];
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}

- (void)windowDidExitFullScreen:(NSNotification *)notification {
    static JNF_MEMBER_CACHE(jm_windowDidExitFullScreen, jc_CPlatformWindow, "windowDidExitFullScreen", "()V");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
    if (platformWindow != NULL) {
        JNFCallVoidMethod(env, platformWindow, jm_windowDidExitFullScreen);
        [self _notifyFullScreenOp:com_apple_eawt_FullScreenHandler_FULLSCREEN_DID_EXIT withEnv:env];
        (*env)->DeleteLocalRef(env, platformWindow);
    }
}

- (void)sendEvent:(NSEvent *)event {
        if ([event type] == NSLeftMouseDown || [event type] == NSRightMouseDown || [event type] == NSOtherMouseDown) {

            NSPoint p = [NSEvent mouseLocation];
            NSRect frame = [self frame];
            NSRect contentRect = [self contentRectForFrameRect:frame];

            // Check if the click happened in the non-client area (title bar)
            if (p.y >= (frame.origin.y + contentRect.size.height)) {
                JNIEnv *env = [ThreadUtilities getJNIEnvUncached];
                jobject platformWindow = [self.javaPlatformWindow jObjectWithEnv:env];
                // Currently, no need to deliver the whole NSEvent.
                static JNF_MEMBER_CACHE(jm_deliverNCMouseDown, jc_CPlatformWindow, "deliverNCMouseDown", "()V");
                JNFCallVoidMethod(env, platformWindow, jm_deliverNCMouseDown);
            }
        }
        [super sendEvent:event];
}
@end // AWTWindow


/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeCreateNSWindow
 * Signature: (JJIIII)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeCreateNSWindow
(JNIEnv *env, jobject obj, jlong contentViewPtr, jlong styleBits, jdouble x, jdouble y, jdouble w, jdouble h)
{
    __block AWTWindow *window = nil;

JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    JNFWeakJObjectWrapper *platformWindow = [JNFWeakJObjectWrapper wrapperWithJObject:obj withEnv:env];
    NSView *contentView = OBJC(contentViewPtr);
    NSRect frameRect = NSMakeRect(x, y, w, h);

    [JNFRunLoop performOnMainThreadWaiting:YES withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        window = [[AWTWindow alloc] initWithPlatformWindow:platformWindow
                                                  styleBits:styleBits
                                                  frameRect:frameRect
                                                contentView:contentView];

        if (window) CFRetain(window);
        [window release]; // GC
    }];

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(window);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowStyleBits
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowStyleBits
(JNIEnv *env, jclass clazz, jlong windowPtr, jint mask, jint bits)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        // scans the bit field, and only updates the values requested by the mask
        // (this implicity handles the _CALLBACK_PROP_BITMASK case, since those are passive reads)
        jint newBits = window.styleBits & ~mask | bits & mask;

        // resets the NSWindow's style mask if the mask intersects any of those bits
        if (mask & MASK(_STYLE_PROP_BITMASK)) {
            [window setStyleMask:[AWTWindow styleMaskForStyleBits:newBits]];
        }

        // calls methods on NSWindow to change other properties, based on the mask
        if (mask & MASK(_METHOD_PROP_BITMASK)) {
            [window setPropertiesForStyleBits:bits mask:mask];
        }

        window.styleBits = newBits;
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowMenuBar
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowMenuBar
(JNIEnv *env, jclass clazz, jlong windowPtr, jlong menuBarPtr)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    CMenuBar *menuBar = OBJC(menuBarPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        if ([window isKeyWindow]) [window.javaMenuBar deactivate];
        window.javaMenuBar = menuBar;

        // if ([self isKeyWindow]) {
        [CMenuBar activate:window.javaMenuBar modallyDisabled:NO];
        // }
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeGetNSWindowInsets
 * Signature: (J)Ljava/awt/Insets;
 */
JNIEXPORT jobject JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeGetNSWindowInsets
(JNIEnv *env, jclass clazz, jlong windowPtr)
{
    jobject ret = NULL;

JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    __block NSRect contentRect = NSZeroRect;
    __block NSRect frame = NSZeroRect;

    [JNFRunLoop performOnMainThreadWaiting:YES withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        frame = [window frame];
        contentRect = [NSWindow contentRectForFrameRect:frame styleMask:[window styleMask]];
    }];

    jint top = (jint)(frame.size.height - contentRect.size.height);
    jint left = (jint)(contentRect.origin.x - frame.origin.x);
    jint bottom = (jint)(contentRect.origin.y - frame.origin.y);
    jint right = (jint)(frame.size.width - (contentRect.size.width + left));

    static JNF_CLASS_CACHE(jc_Insets, "java/awt/Insets");
    static JNF_CTOR_CACHE(jc_Insets_ctor, jc_Insets, "(IIII)V");
    ret = JNFNewObject(env, jc_Insets_ctor, top, left, bottom, right);

JNF_COCOA_EXIT(env);
    return ret;
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowBounds
 * Signature: (JDDDD)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowBounds
(JNIEnv *env, jclass clazz, jlong windowPtr, jdouble originX, jdouble originY, jdouble width, jdouble height)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    NSRect jrect = NSMakeRect(originX, originY, width, height);

    // TODO: not sure we need displayIfNeeded message in our view
    AWTWindow *window = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        NSRect rect = ConvertNSScreenRect(NULL, jrect);
        [window setFrame:rect display:YES];

        // only start tracking events if pointer is above the toplevel
        // TODO: should post an Entered event if YES.
        NSPoint mLocation = [NSEvent mouseLocation];
        [window setAcceptsMouseMovedEvents:NSPointInRect(mLocation, rect)];

        // ensure we repaint the whole window after the resize operation
        // (this will also re-enable screen updates, which were disabled above)
        // TODO: send PaintEvent
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowMinMax
 * Signature: (JDDDD)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowMinMax
(JNIEnv *env, jclass clazz, jlong windowPtr, jdouble minW, jdouble minH, jdouble maxW, jdouble maxH)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    if (minW < 1) minW = 1;
    if (minH < 1) minH = 1;
    if (maxW < 1) maxW = 1;
    if (maxH < 1) maxH = 1;

    NSSize min = { minW, minH };
    NSSize max = { maxW, maxH };

    AWTWindow *window = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        window.javaMinSize = min;
        window.javaMaxSize = max;
        [window updateMinMaxSize:IS(window.styleBits, RESIZABLE)];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativePushNSWindowToBack
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativePushNSWindowToBack
(JNIEnv *env, jclass clazz, jlong windowPtr)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        [window orderBack:nil];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativePushNSWindowToFront
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativePushNSWindowToFront
(JNIEnv *env, jclass clazz, jlong windowPtr)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        if (![window isKeyWindow]) {
            [window makeKeyAndOrderFront:window];
        } else {
            [window orderFront:window];
        }
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowTitle
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowTitle
(JNIEnv *env, jclass clazz, jlong windowPtr, jstring jtitle)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    [window performSelectorOnMainThread:@selector(setTitle:)
                              withObject:JNFJavaToNSString(env, jtitle)
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowAlpha
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowAlpha
(JNIEnv *env, jclass clazz, jlong windowPtr, jfloat alpha)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        [window setAlphaValue:alpha];
        [window.growBoxWindow setAlphaValue:alpha];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeRevalidateNSWindowShadow
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeRevalidateNSWindowShadow
(JNIEnv *env, jclass clazz, jlong windowPtr)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        [window invalidateShadow];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeScreenOn_AppKitThread
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeScreenOn_1AppKitThread
(JNIEnv *env, jclass clazz, jlong windowPtr)
{
    jint ret = 0;

JNF_COCOA_ENTER(env);
AWT_ASSERT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    NSDictionary *props = [[window screen] deviceDescription];
    ret = [[props objectForKey:@"NSScreenNumber"] intValue];

JNF_COCOA_EXIT(env);

    return ret;
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowMinimizedIcon
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowMinimizedIcon
(JNIEnv *env, jclass clazz, jlong windowPtr, jlong nsImagePtr)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    NSImage *image = OBJC(nsImagePtr);
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        [window setMiniwindowImage:image];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowRepresentedFilename
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowRepresentedFilename
(JNIEnv *env, jclass clazz, jlong windowPtr, jstring filename)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    NSURL *url = (filename == NULL) ? nil : [NSURL fileURLWithPath:JNFNormalizedNSStringForPath(env, filename)];
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        AWT_ASSERT_APPKIT_THREAD;

        [window setRepresentedURL:url];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeSetNSWindowSecurityWarningPositioning
 * Signature: (JDDFF)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeSetNSWindowSecurityWarningPositioning
(JNIEnv *env, jclass clazz, jlong windowPtr, jdouble x, jdouble y, jfloat biasX, jfloat biasY)
{
JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    [JNFException raise:env as:kRuntimeException reason:"unimplemented"];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    nativeGetScreenNSWindowIsOn_AppKitThread
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_lwawt_macosx_CPlatformWindow_nativeGetScreenNSWindowIsOn_1AppKitThread
(JNIEnv *env, jclass clazz, jlong windowPtr)
{
    jint index = -1;

JNF_COCOA_ENTER(env);
AWT_ASSERT_APPKIT_THREAD;

    AWTWindow *window = OBJC(windowPtr);
    NSScreen* screen = [window screen];

    //+++gdb NOTE: This is using a linear search of the screens. If it should
    //  prove to be a bottleneck, this can definitely be improved. However,
    //  many screens should prove to be the exception, rather than the rule.
    NSArray* screens = [NSScreen screens];
    NSUInteger i;
    for (i = 0; i < [screens count]; i++)
    {
        if ([[screens objectAtIndex:i] isEqualTo:screen])
        {
            index = i;
            break;
        }
    }

JNF_COCOA_EXIT(env);
    return 1;
}


/*
 * Class:     sun_lwawt_macosx_CPlatformWindow
 * Method:    _toggleFullScreenMode
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPlatformWindow__1toggleFullScreenMode
(JNIEnv *env, jobject peer, jlong windowPtr)
{
JNF_COCOA_ENTER(env);

    AWTWindow *window = OBJC(windowPtr);
    SEL toggleFullScreenSelector = @selector(toggleFullScreen:);
    if (![window respondsToSelector:toggleFullScreenSelector]) return;

    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        [window performSelector:toggleFullScreenSelector withObject:nil];
    }];

JNF_COCOA_EXIT(env);
}

JNIEXPORT jboolean JNICALL Java_sun_lwawt_macosx_CMouseInfoPeer_nativeIsWindowUnderMouse
(JNIEnv *env, jclass clazz, jlong windowPtr)
{
    __block jboolean underMouse = JNI_FALSE;

JNF_COCOA_ENTER(env);
AWT_ASSERT_NOT_APPKIT_THREAD;

    AWTWindow *aWindow = OBJC(windowPtr);
    [JNFRunLoop performOnMainThreadWaiting:YES withBlock:^() {
        AWT_ASSERT_APPKIT_THREAD;

        NSPoint pt = [aWindow mouseLocationOutsideOfEventStream];
        underMouse = [[aWindow contentView] hitTest:pt] != nil;
    }];

JNF_COCOA_EXIT(env);

    return underMouse;
}
