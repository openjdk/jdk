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

#import "ApplicationDelegate.h"

#import "com_apple_eawt_Application.h"
#import "com_apple_eawt__AppDockIconHandler.h"
#import "com_apple_eawt__AppEventHandler.h"
#import "com_apple_eawt__AppMenuBarHandler.h"
#import "com_apple_eawt__AppMenuBarHandler.h"
#import "com_apple_eawt__AppMiscHandlers.h"

#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "CPopupMenu.h"
#import "ThreadUtilities.h"
#import "NSApplicationAWT.h"


#pragma mark App Menu helpers

// The following is a AWT convention?
#define PREFERENCES_TAG  42

static void addMenuItem(NSMenuItem* menuItem, NSInteger index) {
AWT_ASSERT_APPKIT_THREAD;

    NSMenu *menuBar = [[NSApplication sharedApplication] mainMenu];
    NSMenu *appMenu = [[menuBar itemAtIndex:0] submenu];

    [appMenu insertItem:menuItem atIndex:index];
    [appMenu insertItem:[NSMenuItem separatorItem] atIndex:index + 1]; // Add the following separator
}

static void removeMenuItem(NSMenuItem* menuItem) {
AWT_ASSERT_APPKIT_THREAD;

    NSMenu *menuBar = [[NSApplication sharedApplication] mainMenu];
    NSMenu *appMenu = [[menuBar itemAtIndex:0] submenu];

    NSInteger index = [appMenu indexOfItem:menuItem];
    if (index < 0) return; // something went wrong

    [appMenu removeItemAtIndex:index + 1]; // Get the following separator
    [appMenu removeItem:menuItem];
}

@interface NSBundle (EAWTOverrides)
- (BOOL)_hasEAWTOverride:(NSString *)key;
@end


@implementation NSBundle (EAWTOverrides)

- (BOOL)_hasEAWTOverride:(NSString *)key {
    return [[[[self objectForInfoDictionaryKey:@"Java"] objectForKey:@"EAWTOverride"] objectForKey:key] boolValue];
}

@end


// used by JavaRuntimeSupport.framework's [JRSAppKitAWT awtAppDelegate]
// to expose our app delegate to the SWT or other apps that have knoledge
// of Java's AWT and want to install their own app delegate that will delegate
// to the AWT for some operations

@interface JavaAWTAppDelegateLoader : NSObject { }
@end

@implementation JavaAWTAppDelegateLoader
+ (ApplicationDelegate *) awtAppDelegate {
    return [ApplicationDelegate sharedDelegate];
}
@end


@implementation ApplicationDelegate

@synthesize fPreferencesMenu;
@synthesize fAboutMenu;

@synthesize fDockMenu;
@synthesize fDefaultMenuBar;


+ (ApplicationDelegate *)sharedDelegate {
    static ApplicationDelegate *sApplicationDelegate = nil;
    static BOOL checked = NO;

    if (sApplicationDelegate != nil) return sApplicationDelegate;
    if (checked) return nil;

AWT_ASSERT_APPKIT_THREAD;

    // don't install the EAWT delegate if another kind of NSApplication is installed, like say, Safari
    BOOL shouldInstall = NO;
    if (NSApp != nil) {
        if ([NSApp isMemberOfClass:[NSApplication class]]) shouldInstall = YES;
        if ([NSApp isKindOfClass:[NSApplicationAWT class]]) shouldInstall = YES;
    }
    checked = YES;
    if (!shouldInstall) return nil;

    sApplicationDelegate = [[ApplicationDelegate alloc] init];
    return sApplicationDelegate;
}

- (void)_updatePreferencesMenu:(BOOL)prefsAvailable enabled:(BOOL)prefsEnabled {
AWT_ASSERT_APPKIT_THREAD;

    if (prefsAvailable) {
        // Make sure Prefs is around
        if ([self.fPreferencesMenu menu] == nil) {
            // Position of Prefs depends upon About availability.
            NSInteger index = ([self.fAboutMenu menu] != nil) ? 2 : 0;

            addMenuItem(self.fPreferencesMenu, index);
        }

        if (prefsEnabled) {
            [self.fPreferencesMenu setEnabled:YES];
            [self.fPreferencesMenu setTarget:self];
            [self.fPreferencesMenu setAction:@selector(_preferencesMenuHandler)];
        } else {
            [self.fPreferencesMenu setEnabled:NO];
            [self.fPreferencesMenu setTarget:nil];
            [self.fPreferencesMenu setAction:nil];
        }
    } else {
        if ([self.fPreferencesMenu menu] == nil) return;

        // Remove the preferences item
        removeMenuItem(self.fPreferencesMenu);
    }
}

- (void)_updateAboutMenu:(BOOL)aboutAvailable enabled:(BOOL)aboutEnabled {
AWT_ASSERT_APPKIT_THREAD;

    if (aboutAvailable) {
        // Make sure About is around
        if ([self.fAboutMenu menu] == nil) {
            addMenuItem(self.fAboutMenu, 0);
        }

        if (aboutEnabled) {
            [self.fAboutMenu setEnabled:YES];
            [self.fAboutMenu setTarget:self];
            [self.fAboutMenu setAction:@selector(_aboutMenuHandler)];
        } else {
            [self.fAboutMenu setEnabled:NO];
            [self.fAboutMenu setTarget:nil];
            [self.fAboutMenu setAction:nil];
        }
    } else {
        if ([self.fAboutMenu menu] == nil) return;

        // Remove About item.
        removeMenuItem(self.fAboutMenu);
    }
}

- (id) init {
AWT_ASSERT_APPKIT_THREAD;

    self = [super init];
    if (!self) return self;

    // Prep for about and preferences menu
    BOOL usingDefaultNib = YES;
    if ([NSApp isKindOfClass:[NSApplicationAWT class]]) {
        usingDefaultNib = [NSApp usingDefaultNib];
    }
    if (!usingDefaultNib) return self;

    NSMenu *menuBar = [[NSApplication sharedApplication] mainMenu];
    NSMenu *appMenu = [[menuBar itemAtIndex:0] submenu];

    self.fPreferencesMenu = (NSMenuItem*)[appMenu itemWithTag:PREFERENCES_TAG];
    self.fAboutMenu = (NSMenuItem*)[appMenu itemAtIndex:0];

    // If the java application has a bundle with an Info.plist file with
    //  a CFBundleDocumentTypes entry, then it is set up to handle Open Doc
    //  and Print Doc commands for these files. Therefore java AWT will
    //  cache Open Doc and Print Doc events that are sent prior to a
    //  listener being installed by the client java application.
    NSBundle *bundle = [NSBundle mainBundle];
    fHandlesDocumentTypes = [bundle objectForInfoDictionaryKey:@"CFBundleDocumentTypes"] != nil || [bundle _hasEAWTOverride:@"DocumentHandler"];
    fHandlesURLTypes = [bundle objectForInfoDictionaryKey:@"CFBundleURLTypes"] != nil || [bundle _hasEAWTOverride:@"URLHandler"];
    if (fHandlesURLTypes) {
        [[NSAppleEventManager sharedAppleEventManager] setEventHandler:self
                                                           andSelector:@selector(_handleOpenURLEvent:withReplyEvent:)
                                                         forEventClass:kInternetEventClass
                                                            andEventID:kAEGetURL];
    }

    // By HIG, Preferences are not available unless there is a handler. By default in Mac OS X,
    //  there is not a handler, but it is in the nib file for convenience.
    removeMenuItem(self.fPreferencesMenu);

    [self _updateAboutMenu:YES enabled:YES];

    // Now that the AppKit objects are known and set up, initialize the model data
    BOOL aboutAvailable = ([self.fAboutMenu menu] != nil);
    BOOL aboutEnabled = (aboutAvailable && [self.fAboutMenu isEnabled] && ([self.fAboutMenu target] != nil));

    BOOL prefsAvailable = ([self.fPreferencesMenu menu] != nil);
    BOOL prefsEnabled = (prefsAvailable && [self.fPreferencesMenu isEnabled] && ([self.fPreferencesMenu target] != nil));

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_CLASS_CACHE(sjc_AppMenuBarHandler, "com/apple/eawt/_AppMenuBarHandler");
    static JNF_STATIC_MEMBER_CACHE(sjm_initMenuStates, sjc_AppMenuBarHandler, "initMenuStates", "(ZZZZ)V");
    JNFCallStaticVoidMethod(env, sjm_initMenuStates, aboutAvailable, aboutEnabled, prefsAvailable, prefsEnabled);

    // register for the finish launching and system power off notifications by default
    NSNotificationCenter *ctr = [NSNotificationCenter defaultCenter];
    Class clz = [ApplicationDelegate class];
    [ctr addObserver:clz selector:@selector(_willFinishLaunching) name:NSApplicationWillFinishLaunchingNotification object:nil];
    [ctr addObserver:clz selector:@selector(_systemWillPowerOff) name:NSWorkspaceWillPowerOffNotification object:nil];
    [ctr addObserver:clz selector:@selector(_appDidActivate) name:NSApplicationDidBecomeActiveNotification object:nil];
    [ctr addObserver:clz selector:@selector(_appDidDeactivate) name:NSApplicationDidResignActiveNotification object:nil];
    [ctr addObserver:clz selector:@selector(_appDidHide) name:NSApplicationDidHideNotification object:nil];
    [ctr addObserver:clz selector:@selector(_appDidUnhide) name:NSApplicationDidUnhideNotification object:nil];

    return self;
}

- (void)dealloc {
    self.fPreferencesMenu = nil;
    self.fAboutMenu = nil;
    self.fDockMenu = nil;
    self.fDefaultMenuBar = nil;

    [super dealloc];
}

#pragma mark Callbacks from AppKit

static JNF_CLASS_CACHE(sjc_AppEventHandler, "com/apple/eawt/_AppEventHandler");

- (void)_handleOpenURLEvent:(NSAppleEventDescriptor *)openURLEvent withReplyEvent:(NSAppleEventDescriptor *)replyEvent {
AWT_ASSERT_APPKIT_THREAD;
    if (!fHandlesURLTypes) return;

    NSString *url = [[openURLEvent paramDescriptorForKeyword:keyDirectObject] stringValue];

    //fprintf(stderr,"jm_handleOpenURL\n");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jstring jURL = JNFNSToJavaString(env, url);
    static JNF_STATIC_MEMBER_CACHE(jm_handleOpenURI, sjc_AppEventHandler, "handleOpenURI", "(Ljava/lang/String;)V");
    JNFCallStaticVoidMethod(env, jm_handleOpenURI, jURL); // AWT_THREADING Safe (event)
    (*env)->DeleteLocalRef(env, jURL);

    [replyEvent insertDescriptor:[NSAppleEventDescriptor nullDescriptor] atIndex:0];
}

// Helper for both open file and print file methods
// Creates a Java list of files from a native list of files
- (jobject)_createFilePathArrayFrom:(NSArray *)filenames withEnv:(JNIEnv *)env {
    static JNF_CLASS_CACHE(sjc_ArrayList, "java/util/ArrayList");
    static JNF_CTOR_CACHE(jm_ArrayList_ctor, sjc_ArrayList, "(I)V");
    static JNF_MEMBER_CACHE(jm_ArrayList_add, sjc_ArrayList, "add", "(Ljava/lang/Object;)Z");

    jobject jFileNamesArray = JNFNewObject(env, jm_ArrayList_ctor, (jint)[filenames count]); // AWT_THREADING Safe (known object)
    for (NSString *filename in filenames) {
        jstring jFileName = JNFNormalizedJavaStringForPath(env, filename);
        JNFCallVoidMethod(env, jFileNamesArray, jm_ArrayList_add, jFileName);
    }

    return jFileNamesArray;
}

// Open file handler
- (void)application:(NSApplication *)theApplication openFiles:(NSArray *)fileNames {
AWT_ASSERT_APPKIT_THREAD;
    if (!fHandlesDocumentTypes) {
        [theApplication replyToOpenOrPrint:NSApplicationDelegateReplyCancel];
        return;
    }

    //fprintf(stderr,"jm_handleOpenFile\n");
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    // if these files were opened from a Spotlight query, try to get the search text from the current AppleEvent
    NSAppleEventDescriptor *currentEvent = [[NSAppleEventManager sharedAppleEventManager] currentAppleEvent];
    NSString *searchString = [[currentEvent paramDescriptorForKeyword:keyAESearchText] stringValue];
    jstring jSearchString = JNFNSToJavaString(env, searchString);

    // convert the file names array
    jobject jFileNamesArray = [self _createFilePathArrayFrom:fileNames withEnv:env];

    static JNF_STATIC_MEMBER_CACHE(jm_handleOpenFiles, sjc_AppEventHandler, "handleOpenFiles", "(Ljava/util/List;Ljava/lang/String;)V");
    JNFCallStaticVoidMethod(env, jm_handleOpenFiles, jFileNamesArray, jSearchString);
    (*env)->DeleteLocalRef(env, jFileNamesArray);
    (*env)->DeleteLocalRef(env, jSearchString);

    [theApplication replyToOpenOrPrint:NSApplicationDelegateReplySuccess];
}

// Print handler
- (NSApplicationPrintReply)application:(NSApplication *)application printFiles:(NSArray *)fileNames withSettings:(NSDictionary *)printSettings showPrintPanels:(BOOL)showPrintPanels {
AWT_ASSERT_APPKIT_THREAD;
    if (!fHandlesDocumentTypes) return NSPrintingCancelled;

    //fprintf(stderr,"jm_handlePrintFile\n");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject jFileNamesArray = [self _createFilePathArrayFrom:fileNames withEnv:env];
    static JNF_STATIC_MEMBER_CACHE(jm_handlePrintFile, sjc_AppEventHandler, "handlePrintFiles", "(Ljava/util/List;)V");
    JNFCallStaticVoidMethod(env, jm_handlePrintFile, jFileNamesArray); // AWT_THREADING Safe (event)
    (*env)->DeleteLocalRef(env, jFileNamesArray);

    return NSPrintingSuccess;
}

// Open app handler, registered in -init
+ (void)_notifyJava:(jint)notificationType {
AWT_ASSERT_APPKIT_THREAD;

    //fprintf(stderr,"jm_handleOpenApplication\n");
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_handleNativeNotification, sjc_AppEventHandler, "handleNativeNotification", "(I)V");
    JNFCallStaticVoidMethod(env, jm_handleNativeNotification, notificationType); // AWT_THREADING Safe (event)
}

// About menu handler
- (void)_aboutMenuHandler {
    [ApplicationDelegate _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_ABOUT];
}

// Preferences handler
- (void)_preferencesMenuHandler {
    [ApplicationDelegate _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_PREFS];
}

// Open app handler, registered in -init
+ (void)_willFinishLaunching {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_OPEN_APP];
}

// ReOpen app handler
- (BOOL)applicationShouldHandleReopen:(NSApplication *)theApplication hasVisibleWindows:(BOOL)flag {
    [ApplicationDelegate _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_REOPEN_APP];
    return YES;
}

// Quit handler
- (NSApplicationTerminateReply)applicationShouldTerminate:(NSApplication *)app {
    [ApplicationDelegate _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_QUIT];
    return NSTerminateLater;
}

+ (void)_systemWillPowerOff {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_SHUTDOWN];
}

+ (void)_appDidActivate {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_ACTIVE_APP_GAINED];
}

+ (void)_appDidDeactivate {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_ACTIVE_APP_LOST];
}

+ (void)_appDidHide {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_APP_HIDDEN];
}

+ (void)_appDidUnhide {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_APP_SHOWN];
}

+ (void)_sessionDidActivate {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_USER_SESSION_ACTIVE];
}

+ (void)_sessionDidDeactivate {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_USER_SESSION_INACTIVE];
}

+ (void)_screenDidSleep {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_SCREEN_SLEEP];
}

+ (void)_screenDidWake {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_SCREEN_WAKE];
}

+ (void)_systemDidSleep {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_SYSTEM_SLEEP];
}

+ (void)_systemDidWake {
    [self _notifyJava:com_apple_eawt__AppEventHandler_NOTIFY_SYSTEM_WAKE];
}

+ (void)_registerForNotification:(NSNumber *)notificationTypeNum {
    NSNotificationCenter *ctr = [[NSWorkspace sharedWorkspace] notificationCenter];
    Class clz = [ApplicationDelegate class];

    jint notificationType = [notificationTypeNum intValue];
    switch (notificationType) {
        case com_apple_eawt__AppEventHandler_REGISTER_USER_SESSION:
            [ctr addObserver:clz selector:@selector(_sessionDidActivate) name:NSWorkspaceSessionDidBecomeActiveNotification object:nil];
            [ctr addObserver:clz selector:@selector(_sessionDidDeactivate) name:NSWorkspaceSessionDidResignActiveNotification object:nil];
            break;
        case com_apple_eawt__AppEventHandler_REGISTER_SCREEN_SLEEP:
            [ctr addObserver:clz selector:@selector(_screenDidSleep) name:NSWorkspaceScreensDidSleepNotification object:nil];
            [ctr addObserver:clz selector:@selector(_screenDidWake) name:NSWorkspaceScreensDidWakeNotification object:nil];
            break;
        case com_apple_eawt__AppEventHandler_REGISTER_SYSTEM_SLEEP:
            [ctr addObserver:clz selector:@selector(_systemDidSleep) name:NSWorkspaceWillSleepNotification object:nil];
            [ctr addObserver:clz selector:@selector(_systemDidWake) name:NSWorkspaceDidWakeNotification object:nil];
            break;
        default:
            NSLog(@"EAWT attempting to register for unknown notification: %d", (int)notificationType);
            break;
    }
}

// Retrieves the menu to be attached to the Dock icon (AppKit callback)
- (NSMenu *)applicationDockMenu:(NSApplication *)sender {
AWT_ASSERT_APPKIT_THREAD;
    return self.fDockMenu;
}

- (CMenuBar *)defaultMenuBar {
    return [[self.fDefaultMenuBar retain] autorelease];
}


#pragma mark Helpers called on the main thread from Java

// Sets a new NSImageView on the Dock tile
+ (void)_setDockIconImage:(NSImage *)image {
AWT_ASSERT_APPKIT_THREAD;

    NSDockTile *dockTile = [NSApp dockTile];
    if (image == nil) {
        [dockTile setContentView:nil];
        return;
    }

    // setup an image view for the dock tile
    NSRect frame = NSMakeRect(0, 0, dockTile.size.width, dockTile.size.height);
    NSImageView *dockImageView = [[NSImageView alloc] initWithFrame: frame];
    [dockImageView setImageScaling:NSImageScaleProportionallyUpOrDown];
    [dockImageView setImage:image];

    // add it to the NSDockTile
    [dockTile setContentView: dockImageView];
    [dockTile display];

    [dockImageView release];
}

// Obtains the image of the Dock icon, either manually set, a drawn copy, or the default NSApplicationIcon
+ (NSImage *)_dockIconImage {
AWT_ASSERT_APPKIT_THREAD;

    NSDockTile *dockTile = [NSApp dockTile];
    NSView *view = [dockTile contentView];

    if ([view isKindOfClass:[NSImageView class]]) {
        NSImage *img = [((NSImageView *)view) image];
        if (img) return img;
    }

    if (view == nil) {
        return [NSImage imageNamed:@"NSApplicationIcon"];
    }

    NSRect frame = [view frame];
    NSImage *image = [[NSImage alloc] initWithSize:frame.size];
    [image lockFocus];
    [view drawRect:frame];
    [image unlockFocus];
    [image autorelease];
    return image;
}

@end


#pragma mark Native JNI calls

/*
 * Class:     com_apple_eawt_Application
 * Method:    nativeInitializeApplicationDelegate
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt_Application_nativeInitializeApplicationDelegate
(JNIEnv *env, jclass clz)
{
JNF_COCOA_ENTER(env);
    // Force initialization to happen on AppKit thread!
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [ApplicationDelegate sharedDelegate];
    }];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppEventHandler
 * Method:    nativeOpenCocoaAboutWindow
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppEventHandler_nativeOpenCocoaAboutWindow
(JNIEnv *env, jclass clz)
{
JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [NSApp orderFrontStandardAboutPanel:nil];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppEventHandler
 * Method:    nativeReplyToAppShouldTerminate
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppEventHandler_nativeReplyToAppShouldTerminate
(JNIEnv *env, jclass clz, jboolean doTerminate)
{
JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [NSApp replyToApplicationShouldTerminate:doTerminate];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppEventHandler
 * Method:    nativeRegisterForNotification
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppEventHandler_nativeRegisterForNotification
(JNIEnv *env, jclass clz, jint notificationType)
{
JNF_COCOA_ENTER(env);
    [ThreadUtilities performOnMainThread:@selector(_registerForNotification:)
                                      on:[ApplicationDelegate class]
                              withObject:[NSNumber numberWithInt:notificationType]
                           waitUntilDone:NO]; // AWT_THREADING Safe (non-blocking)
JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppDockIconHandler
 * Method:    nativeSetDockMenu
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppDockIconHandler_nativeSetDockMenu
(JNIEnv *env, jclass clz, jlong nsMenuPtr)
{
JNF_COCOA_ENTER(env);

    NSMenu *menu = (NSMenu *)jlong_to_ptr(nsMenuPtr);
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        [ApplicationDelegate sharedDelegate].fDockMenu = menu;
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppDockIconHandler
 * Method:    nativeSetDockIconImage
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppDockIconHandler_nativeSetDockIconImage
(JNIEnv *env, jclass clz, jlong nsImagePtr)
{
JNF_COCOA_ENTER(env);

    NSImage *_image = (NSImage *)jlong_to_ptr(nsImagePtr);
    [ThreadUtilities performOnMainThread:@selector(_setDockIconImage:)
                                      on:[ApplicationDelegate class]
                              withObject:_image
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppDockIconHandler
 * Method:    nativeGetDockIconImage
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_apple_eawt__1AppDockIconHandler_nativeGetDockIconImage
(JNIEnv *env, jclass clz)
{
    __block NSImage *image = nil;

JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        image = [[ApplicationDelegate _dockIconImage] retain];
    }];

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(image);
}

/*
 * Class:     com_apple_eawt__AppDockIconHandler
 * Method:    nativeSetDockIconBadge
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppDockIconHandler_nativeSetDockIconBadge
(JNIEnv *env, jclass clz, jstring badge)
{
JNF_COCOA_ENTER(env);

    NSString *badgeString = JNFJavaToNSString(env, badge);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        NSDockTile *dockTile = [NSApp dockTile];
        [dockTile setBadgeLabel:badgeString];
        [dockTile display];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppMiscHandlers
 * Method:    nativeRequestActivation
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppMiscHandlers_nativeRequestActivation
(JNIEnv *env, jclass clz, jboolean allWindows)
{
JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        NSApplicationActivationOptions options = allWindows ? NSApplicationActivateAllWindows : 0;
        options |= NSApplicationActivateIgnoringOtherApps; // without this, nothing happens!
        [[NSRunningApplication currentApplication] activateWithOptions:options];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppMiscHandlers
 * Method:    nativeRequestUserAttention
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppMiscHandlers_nativeRequestUserAttention
(JNIEnv *env, jclass clz, jboolean critical)
{
JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [NSApp requestUserAttention:critical ? NSCriticalRequest : NSInformationalRequest];
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppMiscHandlers
 * Method:    nativeOpenHelpViewer
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppMiscHandlers_nativeOpenHelpViewer
(JNIEnv *env, jclass clz)
{
JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThread:@selector(showHelp:)
                                      on:NSApp
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppMiscHandlers
 * Method:    nativeEnableSuddenTermination
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppMiscHandlers_nativeEnableSuddenTermination
(JNIEnv *env, jclass clz)
{
JNF_COCOA_ENTER(env);

    [[NSProcessInfo processInfo] enableSuddenTermination]; // Foundation thread-safe

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppMiscHandlers
 * Method:    nativeDisableSuddenTermination
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppMiscHandlers_nativeDisableSuddenTermination
(JNIEnv *env, jclass clz)
{
JNF_COCOA_ENTER(env);

    [[NSProcessInfo processInfo] disableSuddenTermination]; // Foundation thread-safe

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppMenuBarHandler
 * Method:    nativeSetMenuState
 * Signature: (IZZ)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppMenuBarHandler_nativeSetMenuState
(JNIEnv *env, jclass clz, jint menuID, jboolean visible, jboolean enabled)
{
JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        ApplicationDelegate *delegate = [ApplicationDelegate sharedDelegate];
        switch (menuID) {
            case com_apple_eawt__AppMenuBarHandler_MENU_ABOUT:
                [delegate _updateAboutMenu:visible enabled:enabled];
                break;
            case com_apple_eawt__AppMenuBarHandler_MENU_PREFS:
                [delegate _updatePreferencesMenu:visible enabled:enabled];
                break;
        }
    }];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eawt__AppMenuBarHandler
 * Method:    nativeSetDefaultMenuBar
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_apple_eawt__1AppMenuBarHandler_nativeSetDefaultMenuBar
(JNIEnv *env, jclass clz, jlong cMenuBarPtr)
{
JNF_COCOA_ENTER(env);

    CMenuBar *menu = (CMenuBar *)jlong_to_ptr(cMenuBarPtr);
    [ThreadUtilities performOnMainThreadWaiting:NO block:^(){
        [ApplicationDelegate sharedDelegate].fDefaultMenuBar = menu;
    }];

JNF_COCOA_EXIT(env);
}
