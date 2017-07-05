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

#import "NSApplicationAWT.h"

#import <objc/runtime.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>

#import "PropertiesUtilities.h"
#import "ThreadUtilities.h"
#import "QueuingApplicationDelegate.h"


static BOOL sUsingDefaultNIB = YES;
static NSString *SHARED_FRAMEWORK_BUNDLE = @"/System/Library/Frameworks/JavaVM.framework";
static id <NSApplicationDelegate> applicationDelegate = nil;
static QueuingApplicationDelegate * qad = nil;

// Flag used to indicate to the Plugin2 event synthesis code to do a postEvent instead of sendEvent
BOOL postEventDuringEventSynthesis = NO;

@implementation NSApplicationAWT

- (id) init
{
    // Headless: NO
    // Embedded: NO
    // Multiple Calls: NO
    //  Caller: +[NSApplication sharedApplication]

AWT_ASSERT_APPKIT_THREAD;
    fApplicationName = nil;
    fUseDefaultIcon = NO;

    // NSApplication will call _RegisterApplication with the application's bundle, but there may not be one.
    // So, we need to call it ourselves to ensure the app is set up properly.
    [self registerWithProcessManager];

    return [super init];
}

- (void)dealloc
{
    [fApplicationName release];
    fApplicationName = nil;

    [super dealloc];
}
//- (void)finalize { [super finalize]; }

- (void)finishLaunching
{
AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnv];

    // Get default nib file location
    // NOTE: This should learn about the current java.version. Probably best thru
    //  the Makefile system's -DFRAMEWORK_VERSION define. Need to be able to pass this
    //  thru to PB from the Makefile system and for local builds.
    NSString *defaultNibFile = [PropertiesUtilities javaSystemPropertyForKey:@"apple.awt.application.nib" withEnv:env];
    if (!defaultNibFile) {
        NSBundle *javaBundle = [NSBundle bundleWithPath:SHARED_FRAMEWORK_BUNDLE];
        defaultNibFile = [javaBundle pathForResource:@"DefaultApp" ofType:@"nib"];
    } else {
        sUsingDefaultNIB = NO;
    }

    [NSBundle loadNibFile:defaultNibFile externalNameTable: [NSDictionary dictionaryWithObject:self forKey:@"NSOwner"] withZone:nil];

    // Set user defaults to not try to parse application arguments.
    NSUserDefaults * defs = [NSUserDefaults standardUserDefaults];
    NSDictionary * noOpenDict = [NSDictionary dictionaryWithObject:@"NO" forKey:@"NSTreatUnknownArgumentsAsOpen"];
    [defs registerDefaults:noOpenDict];

    // Fix up the dock icon now that we are registered with CAS and the Dock.
    [self setDockIconWithEnv:env];

    // If we are using our nib (the default application NIB) we need to put the app name into
    // the application menu, which has placeholders for the name.
    if (sUsingDefaultNIB) {
        NSUInteger i, itemCount;
        NSMenu *theMainMenu = [NSApp mainMenu];

        // First submenu off the main menu is the application menu.
        NSMenuItem *appMenuItem = [theMainMenu itemAtIndex:0];
        NSMenu *appMenu = [appMenuItem submenu];
        itemCount = [appMenu numberOfItems];

        for (i = 0; i < itemCount; i++) {
            NSMenuItem *anItem = [appMenu itemAtIndex:i];
            NSString *oldTitle = [anItem title];
            [anItem setTitle:[NSString stringWithFormat:oldTitle, fApplicationName]];
        }
    }

    if (applicationDelegate) {
        [self setDelegate:applicationDelegate];
    } else {
        qad = [QueuingApplicationDelegate sharedDelegate];
        [self setDelegate:qad];
    }

    [super finishLaunching];

    // inform any interested parties that the AWT has arrived and is pumping
    [[NSNotificationCenter defaultCenter] postNotificationName:JNFRunLoopDidStartNotification object:self];
}

- (void) registerWithProcessManager
{
    // Headless: NO
    // Embedded: NO
    // Multiple Calls: NO
    //  Caller: -[NSApplicationAWT init]

AWT_ASSERT_APPKIT_THREAD;
    JNIEnv *env = [ThreadUtilities getJNIEnv];

    char envVar[80];

    // The following environment variable is set from the -Xdock:name param. It should be UTF8.
    snprintf(envVar, sizeof(envVar), "APP_NAME_%d", getpid());
    char *appName = getenv(envVar);
    if (appName != NULL) {
        fApplicationName = [NSString stringWithUTF8String:appName];
        unsetenv(envVar);

        // If this environment variable was set we were launched from the command line, so we
        // should use a generic app icon if one wasn't set.
        fUseDefaultIcon = YES;
    }

    // If it wasn't specified as an argument, see if it was specified as a system property.
    if (fApplicationName == nil) {
        fApplicationName = [PropertiesUtilities javaSystemPropertyForKey:@"apple.awt.application.name" withEnv:env];
    }

    // If we STILL don't have it, the app name is retrieved from an environment variable (set in java.c) It should be UTF8.
    if (fApplicationName == nil) {
        char mainClassEnvVar[80];
        snprintf(mainClassEnvVar, sizeof(mainClassEnvVar), "JAVA_MAIN_CLASS_%d", getpid());
        char *mainClass = getenv(mainClassEnvVar);
        if (mainClass != NULL) {
            fApplicationName = [NSString stringWithUTF8String:mainClass];
            unsetenv(mainClassEnvVar);

            NSRange lastPeriod = [fApplicationName rangeOfString:@"." options:NSBackwardsSearch];
            if (lastPeriod.location != NSNotFound) {
                fApplicationName = [fApplicationName substringFromIndex:lastPeriod.location + 1];
            }
            // If this environment variable was set we were launched from the command line, so we
            // should use a generic app icon if one wasn't set.
            fUseDefaultIcon = YES;
        }
    }

    // The dock name is nil for double-clickable Java apps (bundled and Web Start apps)
    // When that happens get the display name, and if that's not available fall back to
    // CFBundleName.
    NSBundle *mainBundle = [NSBundle mainBundle];
    if (fApplicationName == nil) {
        fApplicationName = (NSString *)[mainBundle objectForInfoDictionaryKey:@"CFBundleDisplayName"];

        if (fApplicationName == nil) {
            fApplicationName = (NSString *)[mainBundle objectForInfoDictionaryKey:(NSString *)kCFBundleNameKey];

            if (fApplicationName == nil) {
                fApplicationName = (NSString *)[mainBundle objectForInfoDictionaryKey: (NSString *)kCFBundleExecutableKey];

                if (fApplicationName == nil) {
                    // Name of last resort is the last part of the applicatoin name without the .app (consistent with CopyProcessName)
                    fApplicationName = [[mainBundle bundlePath] lastPathComponent];

                    if ([fApplicationName hasSuffix:@".app"]) {
                        fApplicationName = [fApplicationName stringByDeletingPathExtension];
                    }
                }
            }
        }
    }

    // We're all done trying to determine the app name.  Hold on to it.
    [fApplicationName retain];

    NSDictionary *registrationOptions = [NSMutableDictionary dictionaryWithObject:fApplicationName forKey:@"JRSAppNameKey"];

    NSString *launcherType = [PropertiesUtilities javaSystemPropertyForKey:@"sun.java.launcher" withEnv:env];
    if ([@"SUN_STANDARD" isEqualToString:launcherType]) {
        [registrationOptions setValue:[NSNumber numberWithBool:YES] forKey:@"JRSAppIsCommandLineKey"];
    }

    NSString *uiElementProp = [PropertiesUtilities javaSystemPropertyForKey:@"apple.awt.UIElement" withEnv:env];
    if ([@"true" isCaseInsensitiveLike:uiElementProp]) {
        [registrationOptions setValue:[NSNumber numberWithBool:YES] forKey:@"JRSAppIsUIElementKey"];
    }

    NSString *backgroundOnlyProp = [PropertiesUtilities javaSystemPropertyForKey:@"apple.awt.BackgroundOnly" withEnv:env];
    if ([@"true" isCaseInsensitiveLike:backgroundOnlyProp]) {
        [registrationOptions setValue:[NSNumber numberWithBool:YES] forKey:@"JRSAppIsBackgroundOnlyKey"];
    }

    // TODO replace with direct call
    // [JRSAppKitAWT registerAWTAppWithOptions:registrationOptions];
    // and remove below transform/activate/run hack

    id jrsAppKitAWTClass = objc_getClass("JRSAppKitAWT");
    SEL registerSel = @selector(registerAWTAppWithOptions:);
    if ([jrsAppKitAWTClass respondsToSelector:registerSel]) {
        [jrsAppKitAWTClass performSelector:registerSel withObject:registrationOptions];
        return;
    }

// HACK BEGIN
    // The following is necessary to make the java process behave like a
    // proper foreground application...
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        ProcessSerialNumber psn;
        GetCurrentProcess(&psn);
        TransformProcessType(&psn, kProcessTransformToForegroundApplication);

        [NSApp activateIgnoringOtherApps:YES];
        [NSApp run];
    }];
// HACK END
}

- (void) setDockIconWithEnv:(JNIEnv *)env {
    NSString *theIconPath = nil;

    // The following environment variable is set in java.c. It is probably UTF8.
    char envVar[80];
    snprintf(envVar, sizeof(envVar), "APP_ICON_%d", getpid());
    char *appIcon = getenv(envVar);
    if (appIcon != NULL) {
        theIconPath = [NSString stringWithUTF8String:appIcon];
        unsetenv(envVar);
    }

    if (theIconPath == nil) {
        theIconPath = [PropertiesUtilities javaSystemPropertyForKey:@"apple.awt.application.icon" withEnv:env];
    }

    // If the icon file wasn't specified as an argument and we need to get an icon
    // we'll use the generic java app icon.
    NSString *defaultIconPath = [NSString stringWithFormat:@"%@%@", SHARED_FRAMEWORK_BUNDLE, @"/Resources/GenericApp.icns"];
    if (fUseDefaultIcon && (theIconPath == nil)) {
        theIconPath = defaultIconPath;
    }

    // Set up the dock icon if we have an icon name.
    if (theIconPath != nil) {
        NSImage *iconImage = [[NSImage alloc] initWithContentsOfFile:theIconPath];

        // If we failed for some reason fall back to the default icon.
        if (iconImage == nil) {
            iconImage = [[NSImage alloc] initWithContentsOfFile:defaultIconPath];
        }

        [NSApp setApplicationIconImage:iconImage];
        [iconImage release];
    }
}

+ (void) runAWTLoopWithApp:(NSApplication*)app {
    NSAutoreleasePool *pool = [NSAutoreleasePool new];

    // Make sure that when we run in AWTRunLoopMode we don't exit randomly
    [[NSRunLoop currentRunLoop] addPort:[NSPort port] forMode:[JNFRunLoop javaRunLoopMode]];

    do {
        @try {
            [app run];
        } @catch (NSException* e) {
            NSLog(@"Apple AWT Startup Exception: %@", [e description]);
            NSLog(@"Apple AWT Restarting Native Event Thread");

            [app stop:app];
        }
    } while (YES);

    [pool drain];
}

- (BOOL)usingDefaultNib {
    return sUsingDefaultNIB;
}

- (void)orderFrontStandardAboutPanelWithOptions:(NSDictionary *)optionsDictionary {
    if (!optionsDictionary) {
        optionsDictionary = [NSMutableDictionary dictionaryWithCapacity:2];
        [optionsDictionary setValue:[[[[[NSApp mainMenu] itemAtIndex:0] submenu] itemAtIndex:0] title] forKey:@"ApplicationName"];
        if (![NSImage imageNamed:@"NSApplicationIcon"]) {
            [optionsDictionary setValue:[NSApp applicationIconImage] forKey:@"ApplicationIcon"];
        }
    }

    [super orderFrontStandardAboutPanelWithOptions:optionsDictionary];
}

#define DRAGMASK (NSMouseMovedMask | NSLeftMouseDraggedMask | NSRightMouseDownMask | NSRightMouseDraggedMask | NSLeftMouseUpMask | NSRightMouseUpMask | NSFlagsChangedMask | NSKeyDownMask)

- (NSEvent *)nextEventMatchingMask:(NSUInteger)mask untilDate:(NSDate *)expiration inMode:(NSString *)mode dequeue:(BOOL)deqFlag {
    if (mask == DRAGMASK && [((NSString *)kCFRunLoopDefaultMode) isEqual:mode]) {
        postEventDuringEventSynthesis = YES;
    }

    NSEvent *event = [super nextEventMatchingMask:mask untilDate:expiration inMode:mode dequeue: deqFlag];
    postEventDuringEventSynthesis = NO;

    return event;
}

@end


void OSXAPP_SetApplicationDelegate(id <NSApplicationDelegate> delegate)
{
AWT_ASSERT_APPKIT_THREAD;
    applicationDelegate = delegate;

    if (NSApp != nil) {
        [NSApp setDelegate: applicationDelegate];

        if (applicationDelegate && qad) {
            [qad processQueuedEventsWithTargetDelegate: applicationDelegate];
            qad = nil;
        }
    }
}

