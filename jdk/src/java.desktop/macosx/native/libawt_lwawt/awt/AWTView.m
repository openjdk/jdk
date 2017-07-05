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

#import "CGLGraphicsConfig.h"

#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>
#import "jni_util.h"

#import "ThreadUtilities.h"
#import "AWTView.h"
#import "AWTEvent.h"
#import "AWTWindow.h"
#import "LWCToolkit.h"
#import "JavaComponentAccessibility.h"
#import "JavaTextAccessibility.h"
#import "GeomUtilities.h"
#import "OSVersion.h"
#import "CGLLayer.h"

@interface AWTView()
@property (retain) CDropTarget *_dropTarget;
@property (retain) CDragSource *_dragSource;

-(void) deliverResize: (NSRect) rect;
-(void) resetTrackingArea;
-(void) deliverJavaKeyEventHelper: (NSEvent*) event;
@end

// Uncomment this line to see fprintfs of each InputMethod API being called on this View
//#define IM_DEBUG TRUE
//#define EXTRA_DEBUG

static BOOL shouldUsePressAndHold() {
    static int shouldUsePressAndHold = -1;
    if (shouldUsePressAndHold != -1) return shouldUsePressAndHold;
    shouldUsePressAndHold = !isSnowLeopardOrLower();
    return shouldUsePressAndHold;
}

@implementation AWTView

@synthesize _dropTarget;
@synthesize _dragSource;
@synthesize cglLayer;
@synthesize mouseIsOver;

// Note: Must be called on main (AppKit) thread only
- (id) initWithRect: (NSRect) rect
       platformView: (jobject) cPlatformView
       windowLayer: (CALayer*) windowLayer
{
AWT_ASSERT_APPKIT_THREAD;
    // Initialize ourselves
    self = [super initWithFrame: rect];
    if (self == nil) return self;

    m_cPlatformView = cPlatformView;
    fInputMethodLOCKABLE = NULL;
    fKeyEventsNeeded = NO;
    fProcessingKeystroke = NO;

    fEnablePressAndHold = shouldUsePressAndHold();
    fInPressAndHold = NO;
    fPAHNeedsToSelect = NO;

    mouseIsOver = NO;
    [self resetTrackingArea];
    [self setAutoresizesSubviews:NO];

    if (windowLayer != nil) {
        self.cglLayer = windowLayer;
        //Layer hosting view
        [self setLayer: cglLayer];
        [self setWantsLayer: YES];
        //Layer backed view
        //[self.layer addSublayer: (CALayer *)cglLayer];
        //[self setLayerContentsRedrawPolicy: NSViewLayerContentsRedrawDuringViewResize];
        //[self setLayerContentsPlacement: NSViewLayerContentsPlacementTopLeft];
        //[self setAutoresizingMask: NSViewHeightSizable | NSViewWidthSizable];

#ifdef REMOTELAYER
        CGLLayer *parentLayer = (CGLLayer*)self.cglLayer;
        parentLayer.parentLayer = NULL;
        parentLayer.remoteLayer = NULL;
        if (JRSRemotePort != 0 && remoteSocketFD > 0) {
            CGLLayer *remoteLayer = [[CGLLayer alloc] initWithJavaLayer: parentLayer.javaLayer];
            remoteLayer.target = GL_TEXTURE_2D;
            NSLog(@"Creating Parent=%p, Remote=%p", parentLayer, remoteLayer);
            parentLayer.remoteLayer = remoteLayer;
            remoteLayer.parentLayer = parentLayer;
            remoteLayer.remoteLayer = NULL;
            remoteLayer.jrsRemoteLayer = [remoteLayer createRemoteLayerBoundTo:JRSRemotePort];
            [remoteLayer retain];  // REMIND
            remoteLayer.frame = CGRectMake(0, 0, 720, 500); // REMIND
            [remoteLayer.jrsRemoteLayer retain]; // REMIND
            int layerID = [remoteLayer.jrsRemoteLayer layerID];
            NSLog(@"layer id to send = %d", layerID);
            sendLayerID(layerID);
        }
#endif /* REMOTELAYER */
    }

    return self;
}

- (void) dealloc {
AWT_ASSERT_APPKIT_THREAD;

    self.cglLayer = nil;

    JNIEnv *env = [ThreadUtilities getJNIEnvUncached];
    (*env)->DeleteGlobalRef(env, m_cPlatformView);
    m_cPlatformView = NULL;

    if (fInputMethodLOCKABLE != NULL)
    {
        JNIEnv *env = [ThreadUtilities getJNIEnvUncached];

        JNFDeleteGlobalRef(env, fInputMethodLOCKABLE);
        fInputMethodLOCKABLE = NULL;
    }


    [super dealloc];
}

- (void) viewDidMoveToWindow {
AWT_ASSERT_APPKIT_THREAD;

    [AWTToolkit eventCountPlusPlus];

    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^() {
        [[self window] makeFirstResponder: self];
    }];
    if ([self window] != NULL) {
        [self resetTrackingArea];
    }
}

- (BOOL) acceptsFirstMouse: (NSEvent *)event {
    return YES;
}

- (BOOL) acceptsFirstResponder {
    return YES;
}

- (BOOL) becomeFirstResponder {
    return YES;
}

- (BOOL) preservesContentDuringLiveResize {
    return YES;
}

/*
 * Automatically triggered functions.
 */

- (void)resizeWithOldSuperviewSize:(NSSize)oldBoundsSize {
    [super resizeWithOldSuperviewSize: oldBoundsSize];
    [self deliverResize: [self frame]];
}

/*
 * MouseEvents support
 */

- (void) mouseDown: (NSEvent *)event {
    NSInputManager *inputManager = [NSInputManager currentInputManager];
    if ([inputManager wantsToHandleMouseEvents]) {
#if IM_DEBUG
        NSLog(@"-> IM wants to handle event");
#endif
        if (![inputManager handleMouseEvent:event]) {
            [self deliverJavaMouseEvent: event];
        } else {
#if IM_DEBUG
            NSLog(@"-> Event was handled.");
#endif
        }
    } else {
#if IM_DEBUG
        NSLog(@"-> IM does not want to handle event");
#endif
        [self deliverJavaMouseEvent: event];
    }
}

- (void) mouseUp: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) rightMouseDown: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) rightMouseUp: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) otherMouseDown: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) otherMouseUp: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) mouseMoved: (NSEvent *)event {
    // TODO: better way to redirect move events to the "under" view
    
    NSPoint eventLocation = [event locationInWindow];
    NSPoint localPoint = [self convertPoint: eventLocation fromView: nil];

    if  ([self mouse: localPoint inRect: [self bounds]]) {
        [self deliverJavaMouseEvent: event];
    } else {
        [[self nextResponder] mouseDown:event];
    }
}

- (void) mouseDragged: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) rightMouseDragged: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) otherMouseDragged: (NSEvent *)event {
    [self deliverJavaMouseEvent: event];
}

- (void) mouseEntered: (NSEvent *)event {
    [[self window] setAcceptsMouseMovedEvents:YES];
    //[[self window] makeFirstResponder:self];
    [self deliverJavaMouseEvent: event];
}

- (void) mouseExited: (NSEvent *)event {
    [[self window] setAcceptsMouseMovedEvents:NO];
    [self deliverJavaMouseEvent: event];
    //Restore the cursor back.
    //[CCursorManager _setCursor: [NSCursor arrowCursor]];
}

- (void) scrollWheel: (NSEvent*) event {
    [self deliverJavaMouseEvent: event];
}

/*
 * KeyEvents support
 */

- (void) keyDown: (NSEvent *)event {
    fProcessingKeystroke = YES;
    fKeyEventsNeeded = YES;

    // Allow TSM to look at the event and potentially send back NSTextInputClient messages.
    [self interpretKeyEvents:[NSArray arrayWithObject:event]];

    if (fEnablePressAndHold && [event willBeHandledByComplexInputMethod]) {
        fProcessingKeystroke = NO;
        if (!fInPressAndHold) {
            fInPressAndHold = YES;
            fPAHNeedsToSelect = YES;
        }
        return;
    }

    NSString *eventCharacters = [event characters];
    BOOL isDeadKey = (eventCharacters != nil && [eventCharacters length] == 0);

    if ((![self hasMarkedText] && fKeyEventsNeeded) || isDeadKey) {
        [self deliverJavaKeyEventHelper: event];
    }

    fProcessingKeystroke = NO;
}

- (void) keyUp: (NSEvent *)event {
    [self deliverJavaKeyEventHelper: event];
}

- (void) flagsChanged: (NSEvent *)event {
    [self deliverJavaKeyEventHelper: event];
}

- (BOOL) performKeyEquivalent: (NSEvent *) event {
    // if IM is active key events should be ignored 
    if (![self hasMarkedText] && !fInPressAndHold) {
        [self deliverJavaKeyEventHelper: event];
    }

    // Workaround for 8020209: special case for "Cmd =" and "Cmd ." 
    // because Cocoa calls performKeyEquivalent twice for these keystrokes  
    NSUInteger modFlags = [event modifierFlags] & 
        (NSCommandKeyMask | NSAlternateKeyMask | NSShiftKeyMask | NSControlKeyMask);
    if (modFlags == NSCommandKeyMask) {
        NSString *eventChars = [event charactersIgnoringModifiers];
        if ([eventChars length] == 1) {
            unichar ch = [eventChars characterAtIndex:0];
            if (ch == '=' || ch == '.') {
                [[NSApp mainMenu] performKeyEquivalent: event];
                return YES;
            }
        }

    }

    return NO;
}

/**
 * Utility methods and accessors
 */

-(void) deliverJavaMouseEvent: (NSEvent *) event {
    BOOL isEnabled = YES;
    NSWindow* window = [self window];
    if ([window isKindOfClass: [AWTWindow_Panel class]] || [window isKindOfClass: [AWTWindow_Normal class]]) {
        isEnabled = [(AWTWindow*)[window delegate] isEnabled];
    }

    if (!isEnabled) {
        return;
    }

    NSEventType type = [event type];

    // check synthesized mouse entered/exited events
    if ((type == NSMouseEntered && mouseIsOver) || (type == NSMouseExited && !mouseIsOver)) {
        return;
    }else if ((type == NSMouseEntered && !mouseIsOver) || (type == NSMouseExited && mouseIsOver)) {
        mouseIsOver = !mouseIsOver;
    }

    [AWTToolkit eventCountPlusPlus];

    JNIEnv *env = [ThreadUtilities getJNIEnv];

    NSPoint eventLocation = [event locationInWindow];
    NSPoint localPoint = [self convertPoint: eventLocation fromView: nil];
    NSPoint absP = [NSEvent mouseLocation];

    // Convert global numbers between Cocoa's coordinate system and Java.
    // TODO: need consitent way for doing that both with global as well as with local coordinates.
    // The reason to do it here is one more native method for getting screen dimension otherwise.

    NSRect screenRect = [[[NSScreen screens] objectAtIndex:0] frame];
    absP.y = screenRect.size.height - absP.y;
    jint clickCount;

    if (type == NSMouseEntered ||
        type == NSMouseExited ||
        type == NSScrollWheel ||
        type == NSMouseMoved) {
        clickCount = 0;
    } else {
        clickCount = [event clickCount];
    }

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

    static JNF_CLASS_CACHE(jc_PlatformView, "sun/lwawt/macosx/CPlatformView");
    static JNF_MEMBER_CACHE(jm_deliverMouseEvent, jc_PlatformView, "deliverMouseEvent", "(Lsun/lwawt/macosx/NSEvent;)V");
    JNFCallVoidMethod(env, m_cPlatformView, jm_deliverMouseEvent, jEvent);
    (*env)->DeleteLocalRef(env, jEvent);
}

- (void) resetTrackingArea {
    if (rolloverTrackingArea != nil) {
        [self removeTrackingArea:rolloverTrackingArea];
        [rolloverTrackingArea release];
    }

    int options = (NSTrackingActiveAlways | NSTrackingMouseEnteredAndExited |
                   NSTrackingMouseMoved | NSTrackingEnabledDuringMouseDrag);

    rolloverTrackingArea = [[NSTrackingArea alloc] initWithRect:[self visibleRect]
                                                        options: options
                                                          owner:self
                                                       userInfo:nil
                            ];
    [self addTrackingArea:rolloverTrackingArea];
}

- (void)updateTrackingAreas {
    [super updateTrackingAreas];
    [self resetTrackingArea];
}

- (void) resetCursorRects {
    [super resetCursorRects];
    [self resetTrackingArea];
}

-(void) deliverJavaKeyEventHelper: (NSEvent *) event {
    static NSEvent* sLastKeyEvent = nil;
    if (event == sLastKeyEvent) {
        // The event is repeatedly delivered by keyDown: after performKeyEquivalent:
        return;
    }
    [sLastKeyEvent release];
    sLastKeyEvent = [event retain];

    [AWTToolkit eventCountPlusPlus];
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    jstring characters = NULL;
    jstring charactersIgnoringModifiers = NULL;
    if ([event type] != NSFlagsChanged) {
        characters = JNFNSToJavaString(env, [event characters]);
        charactersIgnoringModifiers = JNFNSToJavaString(env, [event charactersIgnoringModifiers]);
    }

    static JNF_CLASS_CACHE(jc_NSEvent, "sun/lwawt/macosx/NSEvent");
    static JNF_CTOR_CACHE(jctor_NSEvent, jc_NSEvent, "(IISLjava/lang/String;Ljava/lang/String;)V");
    jobject jEvent = JNFNewObject(env, jctor_NSEvent,
                                  [event type],
                                  [event modifierFlags],
                                  [event keyCode],
                                  characters,
                                  charactersIgnoringModifiers);
    CHECK_NULL(jEvent);

    static JNF_CLASS_CACHE(jc_PlatformView, "sun/lwawt/macosx/CPlatformView");
    static JNF_MEMBER_CACHE(jm_deliverKeyEvent, jc_PlatformView,
                            "deliverKeyEvent", "(Lsun/lwawt/macosx/NSEvent;)V");
    JNFCallVoidMethod(env, m_cPlatformView, jm_deliverKeyEvent, jEvent);

    if (characters != NULL) {
        (*env)->DeleteLocalRef(env, characters);
    }
    (*env)->DeleteLocalRef(env, jEvent);
}

-(void) deliverResize: (NSRect) rect {
    jint x = (jint) rect.origin.x;
    jint y = (jint) rect.origin.y;
    jint w = (jint) rect.size.width;
    jint h = (jint) rect.size.height;
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_CLASS_CACHE(jc_PlatformView, "sun/lwawt/macosx/CPlatformView");
    static JNF_MEMBER_CACHE(jm_deliverResize, jc_PlatformView, "deliverResize", "(IIII)V");
    JNFCallVoidMethod(env, m_cPlatformView, jm_deliverResize, x,y,w,h);
}


- (void) drawRect:(NSRect)dirtyRect {
AWT_ASSERT_APPKIT_THREAD;

    [super drawRect:dirtyRect];
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    if (env != NULL) {
/*
        if ([self inLiveResize]) {
        NSRect rs[4];
        NSInteger count;
        [self getRectsExposedDuringLiveResize:rs count:&count];
        for (int i = 0; i < count; i++) {
            JNU_CallMethodByName(env, NULL, [m_awtWindow cPlatformView],
                 "deliverWindowDidExposeEvent", "(FFFF)V",
                 (jfloat)rs[i].origin.x, (jfloat)rs[i].origin.y,
                 (jfloat)rs[i].size.width, (jfloat)rs[i].size.height);
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        }
        } else {
*/
        static JNF_CLASS_CACHE(jc_CPlatformView, "sun/lwawt/macosx/CPlatformView");
        static JNF_MEMBER_CACHE(jm_deliverWindowDidExposeEvent, jc_CPlatformView, "deliverWindowDidExposeEvent", "()V");
        JNFCallVoidMethod(env, m_cPlatformView, jm_deliverWindowDidExposeEvent);
/*
        }
*/
    }
}

// NSAccessibility support
- (jobject)awtComponent:(JNIEnv*)env
{
    static JNF_CLASS_CACHE(jc_CPlatformView, "sun/lwawt/macosx/CPlatformView");
    static JNF_MEMBER_CACHE(jf_Peer, jc_CPlatformView, "peer", "Lsun/lwawt/LWWindowPeer;");
    if ((env == NULL) || (m_cPlatformView == NULL)) {
        NSLog(@"Apple AWT : Error AWTView:awtComponent given bad parameters.");
        if (env != NULL)
        {
            JNFDumpJavaStack(env);
        }
        return NULL;
    }
    jobject peer = JNFGetObjectField(env, m_cPlatformView, jf_Peer);
    static JNF_CLASS_CACHE(jc_LWWindowPeer, "sun/lwawt/LWWindowPeer");
    static JNF_MEMBER_CACHE(jf_Target, jc_LWWindowPeer, "target", "Ljava/awt/Component;");
    if (peer == NULL) {
        NSLog(@"Apple AWT : Error AWTView:awtComponent got null peer from CPlatformView");
        JNFDumpJavaStack(env);
        return NULL;
    }
    return JNFGetObjectField(env, peer, jf_Target);
}

- (id)getAxData:(JNIEnv*)env
{
    return [[[JavaComponentAccessibility alloc] initWithParent:self withEnv:env withAccessible:[self awtComponent:env] withIndex:-1 withView:self withJavaRole:nil] autorelease];
}

- (NSArray *)accessibilityAttributeNames
{
    return [[super accessibilityAttributeNames] arrayByAddingObject:NSAccessibilityChildrenAttribute];
}

// NSAccessibility messages
// attribute methods
- (id)accessibilityAttributeValue:(NSString *)attribute
{
    AWT_ASSERT_APPKIT_THREAD;

    if ([attribute isEqualToString:NSAccessibilityChildrenAttribute])
    {
        JNIEnv *env = [ThreadUtilities getJNIEnv];

        (*env)->PushLocalFrame(env, 4);

        id result = NSAccessibilityUnignoredChildrenForOnlyChild([self getAxData:env]);

        (*env)->PopLocalFrame(env, NULL);

        return result;
    }
    else
    {
        return [super accessibilityAttributeValue:attribute];
    }
}
- (BOOL)accessibilityIsIgnored
{
    return YES;
}

- (id)accessibilityHitTest:(NSPoint)point
{
    AWT_ASSERT_APPKIT_THREAD;
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    (*env)->PushLocalFrame(env, 4);

    id result = [[self getAxData:env] accessibilityHitTest:point withEnv:env];

    (*env)->PopLocalFrame(env, NULL);

    return result;
}

- (id)accessibilityFocusedUIElement
{
    AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnv];

    (*env)->PushLocalFrame(env, 4);

    id result = [[self getAxData:env] accessibilityFocusedUIElement];

    (*env)->PopLocalFrame(env, NULL);

    return result;
}

// --- Services menu support for lightweights ---

// finds the focused accessible element, and if it is a text element, obtains the text from it
- (NSString *)accessibleSelectedText
{
    id focused = [self accessibilityFocusedUIElement];
    if (![focused isKindOfClass:[JavaTextAccessibility class]]) return nil;
    return [(JavaTextAccessibility *)focused accessibilitySelectedTextAttribute];
}

// same as above, but converts to RTFD
- (NSData *)accessibleSelectedTextAsRTFD
{
    NSString *selectedText = [self accessibleSelectedText];
    NSAttributedString *styledText = [[NSAttributedString alloc] initWithString:selectedText];
    NSData *rtfdData = [styledText RTFDFromRange:NSMakeRange(0, [styledText length]) documentAttributes:nil];
    [styledText release];
    return rtfdData;
}

// finds the focused accessible element, and if it is a text element, sets the text in it
- (BOOL)replaceAccessibleTextSelection:(NSString *)text
{
    id focused = [self accessibilityFocusedUIElement];
    if (![focused isKindOfClass:[JavaTextAccessibility class]]) return NO;
    [(JavaTextAccessibility *)focused accessibilitySetSelectedTextAttribute:text];
    return YES;
}

// called for each service in the Services menu - only handle text for now
- (id)validRequestorForSendType:(NSString *)sendType returnType:(NSString *)returnType
{
    if ([[self window] firstResponder] != self) return nil; // let AWT components handle themselves

    if ([sendType isEqual:NSStringPboardType] || [returnType isEqual:NSStringPboardType]) {
        NSString *selectedText = [self accessibleSelectedText];
        if (selectedText) return self;
    }

    return nil;
}

// fetch text from Java and hand off to the service
- (BOOL)writeSelectionToPasteboard:(NSPasteboard *)pboard types:(NSArray *)types
{
    if ([types containsObject:NSStringPboardType])
    {
        [pboard declareTypes:[NSArray arrayWithObject:NSStringPboardType] owner:nil];
        return [pboard setString:[self accessibleSelectedText] forType:NSStringPboardType];
    }

    if ([types containsObject:NSRTFDPboardType])
    {
        [pboard declareTypes:[NSArray arrayWithObject:NSRTFDPboardType] owner:nil];
        return [pboard setData:[self accessibleSelectedTextAsRTFD] forType:NSRTFDPboardType];
    }

    return NO;
}

// write text back to Java from the service
- (BOOL)readSelectionFromPasteboard:(NSPasteboard *)pboard
{
    if ([[pboard types] containsObject:NSStringPboardType])
    {
        NSString *text = [pboard stringForType:NSStringPboardType];
        return [self replaceAccessibleTextSelection:text];
    }

    if ([[pboard types] containsObject:NSRTFDPboardType])
    {
        NSData *rtfdData = [pboard dataForType:NSRTFDPboardType];
        NSAttributedString *styledText = [[NSAttributedString alloc] initWithRTFD:rtfdData documentAttributes:nil];
        NSString *text = [styledText string];
        [styledText release];

        return [self replaceAccessibleTextSelection:text];
    }

    return NO;
}


-(void) setDragSource:(CDragSource *)source {
    self._dragSource = source;
}


- (void) setDropTarget:(CDropTarget *)target {
    self._dropTarget = target;
    [ThreadUtilities performOnMainThread:@selector(controlModelControlValid) on:self._dropTarget withObject:nil waitUntilDone:YES];
}

/********************************  BEGIN NSDraggingSource Interface  ********************************/

- (NSDragOperation)draggingSourceOperationMaskForLocal:(BOOL)flag
{
    // If draggingSource is nil route the message to the superclass (if responding to the selector):
    CDragSource *dragSource = self._dragSource;
    NSDragOperation dragOp = NSDragOperationNone;

    if (dragSource != nil)
        dragOp = [dragSource draggingSourceOperationMaskForLocal:flag];
    else if ([super respondsToSelector:@selector(draggingSourceOperationMaskForLocal:)])
        dragOp = [super draggingSourceOperationMaskForLocal:flag];

    return dragOp;
}

- (NSArray *)namesOfPromisedFilesDroppedAtDestination:(NSURL *)dropDestination
{
    // If draggingSource is nil route the message to the superclass (if responding to the selector):
    CDragSource *dragSource = self._dragSource;
    NSArray* array = nil;

    if (dragSource != nil)
        array = [dragSource namesOfPromisedFilesDroppedAtDestination:dropDestination];
    else if ([super respondsToSelector:@selector(namesOfPromisedFilesDroppedAtDestination:)])
        array = [super namesOfPromisedFilesDroppedAtDestination:dropDestination];

    return array;
}

- (void)draggedImage:(NSImage *)image beganAt:(NSPoint)screenPoint
{
    // If draggingSource is nil route the message to the superclass (if responding to the selector):
    CDragSource *dragSource = self._dragSource;

    if (dragSource != nil)
        [dragSource draggedImage:image beganAt:screenPoint];
    else if ([super respondsToSelector:@selector(draggedImage::)])
        [super draggedImage:image beganAt:screenPoint];
}

- (void)draggedImage:(NSImage *)image endedAt:(NSPoint)screenPoint operation:(NSDragOperation)operation
{
    // If draggingSource is nil route the message to the superclass (if responding to the selector):
    CDragSource *dragSource = self._dragSource;

    if (dragSource != nil)
        [dragSource draggedImage:image endedAt:screenPoint operation:operation];
    else if ([super respondsToSelector:@selector(draggedImage:::)])
        [super draggedImage:image endedAt:screenPoint operation:operation];
}

- (void)draggedImage:(NSImage *)image movedTo:(NSPoint)screenPoint
{
    // If draggingSource is nil route the message to the superclass (if responding to the selector):
    CDragSource *dragSource = self._dragSource;

    if (dragSource != nil)
        [dragSource draggedImage:image movedTo:screenPoint];
    else if ([super respondsToSelector:@selector(draggedImage::)])
        [super draggedImage:image movedTo:screenPoint];
}

- (BOOL)ignoreModifierKeysWhileDragging
{
    // If draggingSource is nil route the message to the superclass (if responding to the selector):
    CDragSource *dragSource = self._dragSource;
    BOOL result = FALSE;

    if (dragSource != nil)
        result = [dragSource ignoreModifierKeysWhileDragging];
    else if ([super respondsToSelector:@selector(ignoreModifierKeysWhileDragging)])
        result = [super ignoreModifierKeysWhileDragging];

    return result;
}

/********************************  END NSDraggingSource Interface  ********************************/

/********************************  BEGIN NSDraggingDestination Interface  ********************************/

- (NSDragOperation)draggingEntered:(id <NSDraggingInfo>)sender
{
    // If draggingDestination is nil route the message to the superclass:
    CDropTarget *dropTarget = self._dropTarget;
    NSDragOperation dragOp = NSDragOperationNone;

    if (dropTarget != nil)
        dragOp = [dropTarget draggingEntered:sender];
    else if ([super respondsToSelector:@selector(draggingEntered:)])
        dragOp = [super draggingEntered:sender];

    return dragOp;
}

- (NSDragOperation)draggingUpdated:(id <NSDraggingInfo>)sender
{
    // If draggingDestination is nil route the message to the superclass:
    CDropTarget *dropTarget = self._dropTarget;
    NSDragOperation dragOp = NSDragOperationNone;

    if (dropTarget != nil)
        dragOp = [dropTarget draggingUpdated:sender];
    else if ([super respondsToSelector:@selector(draggingUpdated:)])
        dragOp = [super draggingUpdated:sender];

    return dragOp;
}

- (void)draggingExited:(id <NSDraggingInfo>)sender
{
    // If draggingDestination is nil route the message to the superclass:
    CDropTarget *dropTarget = self._dropTarget;

    if (dropTarget != nil)
        [dropTarget draggingExited:sender];
    else if ([super respondsToSelector:@selector(draggingExited:)])
        [super draggingExited:sender];
}

- (BOOL)prepareForDragOperation:(id <NSDraggingInfo>)sender
{
    // If draggingDestination is nil route the message to the superclass:
    CDropTarget *dropTarget = self._dropTarget;
    BOOL result = FALSE;

    if (dropTarget != nil)
        result = [dropTarget prepareForDragOperation:sender];
    else if ([super respondsToSelector:@selector(prepareForDragOperation:)])
        result = [super prepareForDragOperation:sender];

    return result;
}

- (BOOL)performDragOperation:(id <NSDraggingInfo>)sender
{
    // If draggingDestination is nil route the message to the superclass:
    CDropTarget *dropTarget = self._dropTarget;
    BOOL result = FALSE;

    if (dropTarget != nil)
        result = [dropTarget performDragOperation:sender];
    else if ([super respondsToSelector:@selector(performDragOperation:)])
        result = [super performDragOperation:sender];

    return result;
}

- (void)concludeDragOperation:(id <NSDraggingInfo>)sender
{
    // If draggingDestination is nil route the message to the superclass:
    CDropTarget *dropTarget = self._dropTarget;

    if (dropTarget != nil)
        [dropTarget concludeDragOperation:sender];
    else if ([super respondsToSelector:@selector(concludeDragOperation:)])
        [super concludeDragOperation:sender];
}

- (void)draggingEnded:(id <NSDraggingInfo>)sender
{
    // If draggingDestination is nil route the message to the superclass:
    CDropTarget *dropTarget = self._dropTarget;

    if (dropTarget != nil)
        [dropTarget draggingEnded:sender];
    else if ([super respondsToSelector:@selector(draggingEnded:)])
        [super draggingEnded:sender];
}

/********************************  END NSDraggingDestination Interface  ********************************/

/********************************  BEGIN NSTextInputClient Protocol  ********************************/


JNF_CLASS_CACHE(jc_CInputMethod, "sun/lwawt/macosx/CInputMethod");

- (void) insertText:(id)aString replacementRange:(NSRange)replacementRange
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [insertText]: %s\n", [aString UTF8String]);
#endif // IM_DEBUG

    if (fInputMethodLOCKABLE == NULL) {
        return;
    }

    // Insert happens at the end of PAH
    fInPressAndHold = NO;

    // insertText gets called when the user commits text generated from an input method.  It also gets
    // called during ordinary input as well.  We only need to send an input method event when we have marked
    // text, or 'text in progress'.  We also need to send the event if we get an insert text out of the blue!
    // (i.e., when the user uses the Character palette or Inkwell), or when the string to insert is a complex
    // Unicode value.
    NSUInteger utf16Length = [aString lengthOfBytesUsingEncoding:NSUTF16StringEncoding];

    if ([self hasMarkedText] || !fProcessingKeystroke || (utf16Length > 2)) {
        JNIEnv *env = [ThreadUtilities getJNIEnv];

        static JNF_MEMBER_CACHE(jm_selectPreviousGlyph, jc_CInputMethod, "selectPreviousGlyph", "()V");
        // We need to select the previous glyph so that it is overwritten.
        if (fPAHNeedsToSelect) {
            JNFCallVoidMethod(env, fInputMethodLOCKABLE, jm_selectPreviousGlyph);
            fPAHNeedsToSelect = NO;
        }

        static JNF_MEMBER_CACHE(jm_insertText, jc_CInputMethod, "insertText", "(Ljava/lang/String;)V");
        jstring insertedText =  JNFNSToJavaString(env, aString);
        JNFCallVoidMethod(env, fInputMethodLOCKABLE, jm_insertText, insertedText); // AWT_THREADING Safe (AWTRunLoopMode)
        (*env)->DeleteLocalRef(env, insertedText);

        // The input method event will create psuedo-key events for each character in the committed string.
        // We also don't want to send the character that triggered the insertText, usually a return. [3337563]
        fKeyEventsNeeded = NO;
    }

    fPAHNeedsToSelect = NO;

}

- (void) doCommandBySelector:(SEL)aSelector
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [doCommandBySelector]\n");
    NSLog(@"%@", NSStringFromSelector(aSelector));
#endif // IM_DEBUG
    if (@selector(insertNewline:) == aSelector || @selector(insertTab:) == aSelector || @selector(deleteBackward:) == aSelector)
    {
        fKeyEventsNeeded = YES;
    }
}

// setMarkedText: cannot take a nil first argument. aString can be NSString or NSAttributedString
- (void) setMarkedText:(id)aString selectedRange:(NSRange)selectionRange replacementRange:(NSRange)replacementRange
{
    if (!fInputMethodLOCKABLE)
        return;

    BOOL isAttributedString = [aString isKindOfClass:[NSAttributedString class]];
    NSAttributedString *attrString = (isAttributedString ? (NSAttributedString *)aString : nil);
    NSString *incomingString = (isAttributedString ? [aString string] : aString);
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [setMarkedText] \"%s\", loc=%lu, length=%lu\n", [incomingString UTF8String], (unsigned long)selectionRange.location, (unsigned long)selectionRange.length);
#endif // IM_DEBUG
    static JNF_MEMBER_CACHE(jm_startIMUpdate, jc_CInputMethod, "startIMUpdate", "(Ljava/lang/String;)V");
    static JNF_MEMBER_CACHE(jm_addAttribute, jc_CInputMethod, "addAttribute", "(ZZII)V");
    static JNF_MEMBER_CACHE(jm_dispatchText, jc_CInputMethod, "dispatchText", "(IIZ)V");
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    // NSInputContext already did the analysis of the TSM event and created attributes indicating
    // the underlining and color that should be done to the string.  We need to look at the underline
    // style and color to determine what kind of Java hilighting needs to be done.
    jstring inProcessText = JNFNSToJavaString(env, incomingString);
    JNFCallVoidMethod(env, fInputMethodLOCKABLE, jm_startIMUpdate, inProcessText); // AWT_THREADING Safe (AWTRunLoopMode)
    (*env)->DeleteLocalRef(env, inProcessText);

    if (isAttributedString) {
        NSUInteger length;
        NSRange effectiveRange;
        NSDictionary *attributes;
        length = [attrString length];
        effectiveRange = NSMakeRange(0, 0);
        while (NSMaxRange(effectiveRange) < length) {
            attributes = [attrString attributesAtIndex:NSMaxRange(effectiveRange)
                                        effectiveRange:&effectiveRange];
            if (attributes) {
                BOOL isThickUnderline, isGray;
                NSNumber *underlineSizeObj =
                (NSNumber *)[attributes objectForKey:NSUnderlineStyleAttributeName];
                NSInteger underlineSize = [underlineSizeObj integerValue];
                isThickUnderline = (underlineSize > 1);

                NSColor *underlineColorObj =
                (NSColor *)[attributes objectForKey:NSUnderlineColorAttributeName];
                isGray = !([underlineColorObj isEqual:[NSColor blackColor]]);

                JNFCallVoidMethod(env, fInputMethodLOCKABLE, jm_addAttribute, isThickUnderline, isGray, effectiveRange.location, effectiveRange.length); // AWT_THREADING Safe (AWTRunLoopMode)
            }
        }
    }

    static JNF_MEMBER_CACHE(jm_selectPreviousGlyph, jc_CInputMethod, "selectPreviousGlyph", "()V");
    // We need to select the previous glyph so that it is overwritten.
    if (fPAHNeedsToSelect) {
        JNFCallVoidMethod(env, fInputMethodLOCKABLE, jm_selectPreviousGlyph);
        fPAHNeedsToSelect = NO;
    }

    JNFCallVoidMethod(env, fInputMethodLOCKABLE, jm_dispatchText, selectionRange.location, selectionRange.length, JNI_FALSE); // AWT_THREADING Safe (AWTRunLoopMode)

    // If the marked text is being cleared (zero-length string) don't handle the key event.
    if ([incomingString length] == 0) {
        fKeyEventsNeeded = NO;
    }
}

- (void) unmarkText
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [unmarkText]\n");
#endif // IM_DEBUG

    if (!fInputMethodLOCKABLE) {
        return;
    }

    // unmarkText cancels any input in progress and commits it to the text field.
    static JNF_MEMBER_CACHE(jm_unmarkText, jc_CInputMethod, "unmarkText", "()V");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    JNFCallVoidMethod(env, fInputMethodLOCKABLE, jm_unmarkText); // AWT_THREADING Safe (AWTRunLoopMode)

}

- (BOOL) hasMarkedText
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [hasMarkedText]\n");
#endif // IM_DEBUG

    if (!fInputMethodLOCKABLE) {
        return NO;
    }

    static JNF_MEMBER_CACHE(jf_fCurrentText, jc_CInputMethod, "fCurrentText", "Ljava/text/AttributedString;");
    static JNF_MEMBER_CACHE(jf_fCurrentTextLength, jc_CInputMethod, "fCurrentTextLength", "I");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject currentText = JNFGetObjectField(env, fInputMethodLOCKABLE, jf_fCurrentText);

    jint currentTextLength = JNFGetIntField(env, fInputMethodLOCKABLE, jf_fCurrentTextLength);

    BOOL hasMarkedText = (currentText != NULL && currentTextLength > 0);

    if (currentText != NULL) {
        (*env)->DeleteLocalRef(env, currentText);
    }

    return hasMarkedText;
}

- (NSInteger) conversationIdentifier
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [conversationIdentifier]\n");
#endif // IM_DEBUG

    return (NSInteger) self;
}

/* Returns attributed string at the range.  This allows input mangers to
 query any range in backing-store (Andy's request)
 */
- (NSAttributedString *) attributedSubstringForProposedRange:(NSRange)theRange actualRange:(NSRangePointer)actualRange
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [attributedSubstringFromRange] location=%lu, length=%lu\n", (unsigned long)theRange.location, (unsigned long)theRange.length);
#endif // IM_DEBUG

    static JNF_MEMBER_CACHE(jm_substringFromRange, jc_CInputMethod, "attributedSubstringFromRange", "(II)Ljava/lang/String;");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject theString = JNFCallObjectMethod(env, fInputMethodLOCKABLE, jm_substringFromRange, theRange.location, theRange.length); // AWT_THREADING Safe (AWTRunLoopMode)

    id result = [[[NSAttributedString alloc] initWithString:JNFJavaToNSString(env, theString)] autorelease];
#ifdef IM_DEBUG
    NSLog(@"attributedSubstringFromRange returning \"%@\"", result);
#endif // IM_DEBUG

    (*env)->DeleteLocalRef(env, theString);
    return result;
}

/* This method returns the range for marked region.  If hasMarkedText == false,
 it'll return NSNotFound location & 0 length range.
 */
- (NSRange) markedRange
{

#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [markedRange]\n");
#endif // IM_DEBUG

    if (!fInputMethodLOCKABLE) {
        return NSMakeRange(NSNotFound, 0);
    }

    static JNF_MEMBER_CACHE(jm_markedRange, jc_CInputMethod, "markedRange", "()[I");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jarray array;
    jboolean isCopy;
    jint *_array;
    NSRange range = NSMakeRange(NSNotFound, 0);

    array = JNFCallObjectMethod(env, fInputMethodLOCKABLE, jm_markedRange); // AWT_THREADING Safe (AWTRunLoopMode)

    if (array) {
        _array = (*env)->GetIntArrayElements(env, array, &isCopy);
        if (_array != NULL) {
            range.location = _array[0];
            range.length = _array[1];
#ifdef IM_DEBUG
            fprintf(stderr, "markedRange returning (%lu, %lu)\n",
                    (unsigned long)range.location, (unsigned long)range.length);
#endif // IM_DEBUG
            (*env)->ReleaseIntArrayElements(env, array, _array, 0);
        }
        (*env)->DeleteLocalRef(env, array);
    }

    return range;
}

/* This method returns the range for selected region.  Just like markedRange method,
 its location field contains char index from the text beginning.
 */
- (NSRange) selectedRange
{
    if (!fInputMethodLOCKABLE) {
        return NSMakeRange(NSNotFound, 0);
    }

    static JNF_MEMBER_CACHE(jm_selectedRange, jc_CInputMethod, "selectedRange", "()[I");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jarray array;
    jboolean isCopy;
    jint *_array;
    NSRange range = NSMakeRange(NSNotFound, 0);

#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [selectedRange]\n");
#endif // IM_DEBUG

    array = JNFCallObjectMethod(env, fInputMethodLOCKABLE, jm_selectedRange); // AWT_THREADING Safe (AWTRunLoopMode)
    if (array) {
        _array = (*env)->GetIntArrayElements(env, array, &isCopy);
        if (_array != NULL) {
            range.location = _array[0];
            range.length = _array[1];
            (*env)->ReleaseIntArrayElements(env, array, _array, 0);
        }
        (*env)->DeleteLocalRef(env, array);
    }

    return range;
}

/* This method returns the first frame of rects for theRange in screen coordindate system.
 */
- (NSRect) firstRectForCharacterRange:(NSRange)theRange actualRange:(NSRangePointer)actualRange
{
    if (!fInputMethodLOCKABLE) {
        return NSZeroRect;
    }

    static JNF_MEMBER_CACHE(jm_firstRectForCharacterRange, jc_CInputMethod,
                            "firstRectForCharacterRange", "(I)[I");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jarray array;
    jboolean isCopy;
    jint *_array;
    NSRect rect;

#ifdef IM_DEBUG
    fprintf(stderr,
            "AWTView InputMethod Selector Called : [firstRectForCharacterRange:] location=%lu, length=%lu\n",
            (unsigned long)theRange.location, (unsigned long)theRange.length);
#endif // IM_DEBUG

    array = JNFCallObjectMethod(env, fInputMethodLOCKABLE, jm_firstRectForCharacterRange,
                                theRange.location); // AWT_THREADING Safe (AWTRunLoopMode)

    _array = (*env)->GetIntArrayElements(env, array, &isCopy);
    if (_array) {
        rect = ConvertNSScreenRect(env, NSMakeRect(_array[0], _array[1], _array[2], _array[3]));
        (*env)->ReleaseIntArrayElements(env, array, _array, 0);
    } else {
        rect = NSZeroRect;
    }
    (*env)->DeleteLocalRef(env, array);

#ifdef IM_DEBUG
    fprintf(stderr,
            "firstRectForCharacterRange returning x=%f, y=%f, width=%f, height=%f\n",
            rect.origin.x, rect.origin.y, rect.size.width, rect.size.height);
#endif // IM_DEBUG
    return rect;
}

/* This method returns the index for character that is nearest to thePoint.  thPoint is in
 screen coordinate system.
 */
- (NSUInteger)characterIndexForPoint:(NSPoint)thePoint
{
    if (!fInputMethodLOCKABLE) {
        return NSNotFound;
    }

    static JNF_MEMBER_CACHE(jm_characterIndexForPoint, jc_CInputMethod,
                            "characterIndexForPoint", "(II)I");
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    NSPoint flippedLocation = ConvertNSScreenPoint(env, thePoint);

#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [characterIndexForPoint:(NSPoint)thePoint] x=%f, y=%f\n", flippedLocation.x, flippedLocation.y);
#endif // IM_DEBUG

    jint index = JNFCallIntMethod(env, fInputMethodLOCKABLE, jm_characterIndexForPoint, (jint)flippedLocation.x, (jint)flippedLocation.y); // AWT_THREADING Safe (AWTRunLoopMode)

#ifdef IM_DEBUG
    fprintf(stderr, "characterIndexForPoint returning %ld\n", index);
#endif // IM_DEBUG

    if (index == -1) {
        return NSNotFound;
    } else {
        return (NSUInteger)index;
    }
}

- (NSArray*) validAttributesForMarkedText
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [validAttributesForMarkedText]\n");
#endif // IM_DEBUG

    return [NSArray array];
}

- (void)setInputMethod:(jobject)inputMethod
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [setInputMethod]\n");
#endif // IM_DEBUG

    JNIEnv *env = [ThreadUtilities getJNIEnv];

    // Get rid of the old one
    if (fInputMethodLOCKABLE) {
        JNFDeleteGlobalRef(env, fInputMethodLOCKABLE);
    }

    // Save a global ref to the new input method.
    if (inputMethod != NULL)
        fInputMethodLOCKABLE = JNFNewGlobalRef(env, inputMethod);
    else
        fInputMethodLOCKABLE = NULL;
}

- (void)abandonInput
{
#ifdef IM_DEBUG
    fprintf(stderr, "AWTView InputMethod Selector Called : [abandonInput]\n");
#endif // IM_DEBUG

    [ThreadUtilities performOnMainThread:@selector(markedTextAbandoned:) on:[NSInputManager currentInputManager] withObject:self waitUntilDone:YES];
    [self unmarkText];
}

/********************************   END NSTextInputClient Protocol   ********************************/




@end // AWTView

/*
 * Class:     sun_lwawt_macosx_CPlatformView
 * Method:    nativeCreateView
 * Signature: (IIII)J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CPlatformView_nativeCreateView
(JNIEnv *env, jobject obj, jint originX, jint originY, jint width, jint height, jlong windowLayerPtr)
{
    __block AWTView *newView = nil;

JNF_COCOA_ENTER(env);

    NSRect rect = NSMakeRect(originX, originY, width, height);
    jobject cPlatformView = (*env)->NewGlobalRef(env, obj);

    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){

        CALayer *windowLayer = jlong_to_ptr(windowLayerPtr);
        newView = [[AWTView alloc] initWithRect:rect
                                   platformView:cPlatformView
                                    windowLayer:windowLayer];
    }];

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(newView);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformView
 * Method:    nativeSetAutoResizable
 * Signature: (JZ)V;
 */

JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CPlatformView_nativeSetAutoResizable
(JNIEnv *env, jclass cls, jlong viewPtr, jboolean toResize)
{
JNF_COCOA_ENTER(env);
    
    NSView *view = (NSView *)jlong_to_ptr(viewPtr);    

   [ThreadUtilities performOnMainThreadWaiting:NO block:^(){

       if (toResize) {
           [view setAutoresizingMask: NSViewHeightSizable | NSViewWidthSizable];
       } else {
           [view setAutoresizingMask: NSViewMinYMargin | NSViewMaxXMargin];
       }
       
       if ([view superview] != nil) {
           [[view superview] setAutoresizesSubviews:(BOOL)toResize];
       }
       
    }];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPlatformView
 * Method:    nativeGetNSViewDisplayID
 * Signature: (J)I;
 */

JNIEXPORT jint JNICALL
Java_sun_lwawt_macosx_CPlatformView_nativeGetNSViewDisplayID
(JNIEnv *env, jclass cls, jlong viewPtr)
{
    __block jint ret; //CGDirectDisplayID
    
JNF_COCOA_ENTER(env);
    
    NSView *view = (NSView *)jlong_to_ptr(viewPtr);    
    NSWindow *window = [view window];
    
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){

            ret = (jint)[[AWTWindow getNSWindowDisplayID_AppKitThread: window] intValue];
    }];
    
JNF_COCOA_EXIT(env);
    
    return ret;
}

/*
 * Class:     sun_lwawt_macosx_CPlatformView
 * Method:    nativeGetLocationOnScreen
 * Signature: (J)Ljava/awt/Rectangle;
 */

JNIEXPORT jobject JNICALL
Java_sun_lwawt_macosx_CPlatformView_nativeGetLocationOnScreen
(JNIEnv *env, jclass cls, jlong viewPtr)
{
    jobject jRect = NULL;
    
JNF_COCOA_ENTER(env);
    
    __block NSRect rect = NSZeroRect;
    
    NSView *view = (NSView *)jlong_to_ptr(viewPtr);    
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){

        NSRect viewBounds = [view bounds];
        NSRect frameInWindow = [view convertRect:viewBounds toView:nil];
        rect = [[view window] convertRectToScreen:frameInWindow];
        NSRect screenRect = [[NSScreen mainScreen] frame];
        //Convert coordinates to top-left corner origin
        rect.origin.y = screenRect.size.height - rect.origin.y - viewBounds.size.height;
    }];
    jRect = NSToJavaRect(env, rect);
    
JNF_COCOA_EXIT(env);
    
    return jRect;
}

/*
 * Class:     sun_lwawt_macosx_CPlatformView
 * Method:    nativeIsViewUnderMouse
 * Signature: (J)Z;
 */

JNIEXPORT jboolean JNICALL Java_sun_lwawt_macosx_CPlatformView_nativeIsViewUnderMouse
(JNIEnv *env, jclass clazz, jlong viewPtr)
{
    __block jboolean underMouse = JNI_FALSE;
    
JNF_COCOA_ENTER(env);
    
    NSView *nsView = OBJC(viewPtr);
   [ThreadUtilities performOnMainThreadWaiting:YES block:^(){       
       NSPoint ptWindowCoords = [[nsView window] mouseLocationOutsideOfEventStream];
       NSPoint ptViewCoords = [nsView convertPoint:ptWindowCoords fromView:nil];
       underMouse = [nsView hitTest:ptViewCoords] != nil;
    }];
    
JNF_COCOA_EXIT(env);
    
    return underMouse;
}
