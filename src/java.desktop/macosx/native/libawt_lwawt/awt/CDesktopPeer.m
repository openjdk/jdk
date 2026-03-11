/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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


#import "JNIUtilities.h"
#import <CoreFoundation/CoreFoundation.h>
#import <ApplicationServices/ApplicationServices.h>
#import "sun_lwawt_macosx_CDesktopPeer.h"

/*
 * Class:     sun_lwawt_macosx_CDesktopPeer
 * Method:    _lsOpenURI
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_sun_lwawt_macosx_CDesktopPeer__1lsOpenURI
(JNIEnv *env, jclass clz, jstring uri, jint action)
{
    __block OSStatus status = noErr;
JNI_COCOA_ENTER(env);

    NSURL *urlToOpen = [NSURL URLWithString:JavaStringToNSString(env, uri)];
    NSURL *appURI = nil;

    if (action == sun_lwawt_macosx_CDesktopPeer_BROWSE) {
        // To get the defaultBrowser
        NSURL *httpsURL = [NSURL URLWithString:@"https://"];
        NSWorkspace *workspace = [NSWorkspace sharedWorkspace];
        appURI = [workspace URLForApplicationToOpenURL:httpsURL];
    } else if (action == sun_lwawt_macosx_CDesktopPeer_MAIL) {
        // To get the default mailer
        NSURL *mailtoURL = [NSURL URLWithString:@"mailto://"];
        NSWorkspace *workspace = [NSWorkspace sharedWorkspace];
        appURI = [workspace URLForApplicationToOpenURL:mailtoURL];
    }

    if (appURI == nil) {
        return -1;
    }

    // Prepare NSOpenConfig object
    NSArray<NSURL *> *urls = @[urlToOpen];
    NSWorkspaceOpenConfiguration *configuration = [NSWorkspaceOpenConfiguration configuration];
    configuration.activates = YES; // To bring app to foreground
    configuration.promptsUserIfNeeded = YES; // To allow macOS desktop prompts

    // dispatch semaphores used to wait for the completion handler to update and return status
    dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
    dispatch_time_t timeout = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(NSEC_PER_SEC)); // 1 second timeout

    // Asynchronous call to openURL
    [[NSWorkspace sharedWorkspace] openURLs:urls
                                    withApplicationAtURL:appURI
                                    configuration:configuration
                                    completionHandler:^(NSRunningApplication *app, NSError *error) {
        if (error) {
            status = (OSStatus) error.code;
        }
        dispatch_semaphore_signal(semaphore);
    }];

    dispatch_semaphore_wait(semaphore, timeout);

JNI_COCOA_EXIT(env);
    return status;
}

/*
 * Class:     sun_lwawt_macosx_CDesktopPeer
 * Method:    _lsOpenFile
 * Signature: (Ljava/lang/String;I;Ljava/lang/String;)I;
 */
JNIEXPORT jint JNICALL Java_sun_lwawt_macosx_CDesktopPeer__1lsOpenFile
(JNIEnv *env, jclass clz, jstring jpath, jint action, jstring jtmpTxtPath)
{
    __block OSStatus status = noErr;
JNI_COCOA_ENTER(env);

    NSString *path  = NormalizedPathNSStringFromJavaString(env, jpath);
    NSURL *urlToOpen = [NSURL fileURLWithPath:(NSString *)path];

    // This byzantine workaround is necessary, or else directories won't open in Finder
    urlToOpen = (NSURL *)CFURLCreateWithFileSystemPath(NULL, (CFStringRef)[urlToOpen path],
                                                        kCFURLPOSIXPathStyle, false);

    NSWorkspace *workspace = [NSWorkspace sharedWorkspace];
    NSURL *appURI = [workspace URLForApplicationToOpenURL:urlToOpen];
    NSURL *defaultTerminalApp = [workspace URLForApplicationToOpenURL:[NSURL URLWithString:@"file:///bin/sh"]];

    // Prepare NSOpenConfig object
    NSArray<NSURL *> *urls = @[urlToOpen];
    NSWorkspaceOpenConfiguration *configuration = [NSWorkspaceOpenConfiguration configuration];
    configuration.activates = YES; // To bring app to foreground
    configuration.promptsUserIfNeeded = YES;  // To allow macOS desktop prompts

    // pre-checks for open/print/edit before calling openURLs API
    if (action == sun_lwawt_macosx_CDesktopPeer_OPEN
            || action == sun_lwawt_macosx_CDesktopPeer_PRINT) {
        if (appURI == nil
            || [[urlToOpen absoluteString] containsString:[appURI absoluteString]]
            || [[defaultTerminalApp absoluteString] containsString:[appURI absoluteString]]) {
            [urlToOpen release];
            return -1;
        }
        // Additionally set forPrinting=TRUE for print
        if (action == sun_lwawt_macosx_CDesktopPeer_PRINT) {
            configuration.forPrinting = YES;
        }
    } else if (action == sun_lwawt_macosx_CDesktopPeer_EDIT) {
        if (appURI == nil
            || [[urlToOpen absoluteString] containsString:[appURI absoluteString]]) {
            [urlToOpen release];
            return -1;
        }
        // for EDIT: if (defaultApp = TerminalApp) then set appURI = DefaultTextEditor
        if ([[defaultTerminalApp absoluteString] containsString:[appURI absoluteString]]) {
            NSString *path  = NormalizedPathNSStringFromJavaString(env, jtmpTxtPath);
            NSURL *tempFilePath = [NSURL fileURLWithPath:(NSString *)path];
            appURI = [workspace URLForApplicationToOpenURL:tempFilePath];
        }
    }

    // dispatch semaphores used to wait for the completion handler to update and return status
    dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
    dispatch_time_t timeout = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(NSEC_PER_SEC)); // 1 second timeout

    // Asynchronous call - openURLs:withApplicationAtURL
    [[NSWorkspace sharedWorkspace] openURLs:urls
                                   withApplicationAtURL:appURI
                                   configuration:configuration
                                   completionHandler:^(NSRunningApplication *app, NSError *error) {
        if (error) {
            status = (OSStatus) error.code;
        }
        dispatch_semaphore_signal(semaphore);
    }];

    dispatch_semaphore_wait(semaphore, timeout);

    [urlToOpen release];
JNI_COCOA_EXIT(env);
    return status;
}
