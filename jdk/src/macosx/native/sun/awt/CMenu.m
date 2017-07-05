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

#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>


#import "CMenu.h"
#import "CMenuBar.h"
#import "ThreadUtilities.h"

#import "sun_lwawt_macosx_CMenu.h"

@implementation CMenu

- (id)initWithPeer:(jobject)peer {
AWT_ASSERT_APPKIT_THREAD;
    // Create the new NSMenu
    self = [super initWithPeer:peer asSeparator:[NSNumber numberWithBool:NO]];
    if (self) {
        fMenu = [NSMenu javaMenuWithTitle:@""];
        [fMenu retain];
        [fMenu setAutoenablesItems:NO];
    }
    return self;
}

- (void)dealloc {
    [fMenu release];
    fMenu = nil;
    [super dealloc];
}
//- (void)finalize { [super finalize]; }

- (void)addJavaSubmenu:(CMenu *)submenu {
AWT_ASSERT_NOT_APPKIT_THREAD;
    [ThreadUtilities performOnMainThread:@selector(addNativeItem_OnAppKitThread:) onObject:self withObject:submenu waitUntilDone:YES awtMode:YES];
}

- (void)addJavaMenuItem:(CMenuItem *)theMenuItem {
AWT_ASSERT_NOT_APPKIT_THREAD;
    [ThreadUtilities performOnMainThread:@selector(addNativeItem_OnAppKitThread:) onObject:self withObject:theMenuItem waitUntilDone:YES awtMode:YES];
}

- (void)addNativeItem_OnAppKitThread:(CMenuItem *)itemModified {
AWT_ASSERT_APPKIT_THREAD;
    [itemModified addNSMenuItemToMenu:[self menu]];
}

- (void)setJavaMenuTitle:(NSString *)title {
AWT_ASSERT_NOT_APPKIT_THREAD;

    if (title) {
        [ThreadUtilities performOnMainThread:@selector(setNativeMenuTitle_OnAppKitThread:) onObject:self withObject:title waitUntilDone:YES awtMode:YES];
    }
}

- (void)setNativeMenuTitle_OnAppKitThread:(NSString *)title {
AWT_ASSERT_APPKIT_THREAD;

    [fMenu setTitle:title];
    // If we are a submenu we need to set our name in the parent menu's menu item.
    NSMenu *parent = [fMenu supermenu];
    if (parent) {
        NSInteger index = [parent indexOfItemWithSubmenu:fMenu];
        NSMenuItem *menuItem = [parent itemAtIndex:index];
        [menuItem setTitle:title];
    }
}

- (void)addSeparator {
    // Nothing calls this, which is good because we need a CMenuItem here.
}

- (void)deleteJavaItem:(jint)index {
AWT_ASSERT_NOT_APPKIT_THREAD;

    [ThreadUtilities performOnMainThread:@selector(deleteNativeJavaItem_OnAppKitThread:) onObject:self withObject:[NSNumber numberWithInt:index] waitUntilDone:YES awtMode:YES];
}

- (void)deleteNativeJavaItem_OnAppKitThread:(NSNumber *)number {
AWT_ASSERT_APPKIT_THREAD;

    int n = [number intValue];
    if (n < [[self menu] numberOfItems]) {
        [[self menu] removeItemAtIndex:n];
    }
}

- (void)addNSMenuItemToMenu:(NSMenu *)inMenu {
    if (fMenuItem == nil) return;
    [fMenuItem setSubmenu:fMenu];
    [inMenu addItem:fMenuItem];
}

- (NSMenu *)menu {
    return [[fMenu retain] autorelease];
}

- (void)setNativeEnabled_OnAppKitThread:(NSNumber *)boolNumber {
AWT_ASSERT_APPKIT_THREAD;

    @synchronized(self) {
        fIsEnabled = [boolNumber boolValue];

        NSMenu* supermenu = [fMenu supermenu];
        [[supermenu itemAtIndex:[supermenu indexOfItemWithSubmenu:fMenu]] setEnabled:fIsEnabled];
    }
}

- (NSString *)description {
    return [NSString stringWithFormat:@"CMenu[ %@ ]", fMenu];
}

@end

CMenu * createCMenu (jobject cPeerObjGlobal) {

    CMenu *aCMenu = nil;

    // We use an array here only to be able to get a return value
    NSMutableArray *args = [[NSMutableArray alloc] initWithObjects:[NSValue valueWithBytes:&cPeerObjGlobal objCType:@encode(jobject)], nil];

    [ThreadUtilities performOnMainThread:@selector(_create_OnAppKitThread:) onObject:[CMenu alloc] withObject:args waitUntilDone:YES awtMode:YES];

    aCMenu = (CMenu *)[args objectAtIndex: 0];

    if (aCMenu == nil) {
        return 0L;
    }

    return aCMenu;

}

/*
 * Class:     sun_lwawt_macosx_CMenu
 * Method:    nativeCreateSubMenu
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CMenu_nativeCreateSubMenu
(JNIEnv *env, jobject peer, jlong parentMenu)
{
    CMenu *aCMenu = nil;
JNF_COCOA_ENTER(env);

    jobject cPeerObjGlobal = (*env)->NewGlobalRef(env, peer);

    aCMenu = createCMenu (cPeerObjGlobal);

    // Add it to the parent menu
    [((CMenu *)jlong_to_ptr(parentMenu)) addJavaSubmenu: aCMenu];
    if (aCMenu) {
        CFRetain(aCMenu); // GC
        [aCMenu release];
    }

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(aCMenu);
}



/*
 * Class:     sun_lwawt_macosx_CMenu
 * Method:    nativeCreateMenu
 * Signature: (JZ)J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CMenu_nativeCreateMenu
(JNIEnv *env, jobject peer,
        jlong parentMenuBar, jboolean isHelpMenu, jint insertLocation)
{
    CMenu *aCMenu = nil;
    CMenuBar *parent = (CMenuBar *)jlong_to_ptr(parentMenuBar);
JNF_COCOA_ENTER(env);

    jobject cPeerObjGlobal = (*env)->NewGlobalRef(env, peer);

    aCMenu = createCMenu (cPeerObjGlobal);

    // Add it to the menu bar.
    [parent javaAddMenu:aCMenu atIndex:insertLocation];

    // If the menu is already the help menu (because we are creating an entire
    // menu bar) we need to note that now, because we can't rely on
    // setHelpMenu() being called again.
    if (isHelpMenu == JNI_TRUE) {
        [parent javaSetHelpMenu: aCMenu];
    }

    if (aCMenu) {
        CFRetain(aCMenu); // GC
        [aCMenu release];
    }
JNF_COCOA_EXIT(env);
    return ptr_to_jlong(aCMenu);
}


/*
 * Class:     sun_lwawt_macosx_CMenu
 * Method:    nativeSetMenuTitle
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CMenu_nativeSetMenuTitle
(JNIEnv *env, jobject peer, jlong menuObject, jstring label)
{
JNF_COCOA_ENTER(env);
    // Set the menu's title.
    [((CMenu *)jlong_to_ptr(menuObject)) setJavaMenuTitle:JNFJavaToNSString(env, label)];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CMenu
 * Method:    nativeAddSeparator
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CMenu_nativeAddSeparator
(JNIEnv *env, jobject peer, jlong menuObject)
{
JNF_COCOA_ENTER(env);
    // Add a separator item.
    [((CMenu *)jlong_to_ptr(menuObject))addSeparator];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CMenu
 * Method:    nativeDeleteItem
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CMenu_nativeDeleteItem
(JNIEnv *env, jobject peer, jlong menuObject, jint index)
{
JNF_COCOA_ENTER(env);
    // Remove the specified item.
    [((CMenu *)jlong_to_ptr(menuObject)) deleteJavaItem: index];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CMenu
 * Method:    nativeGetNSMenu
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_lwawt_macosx_CMenu_nativeGetNSMenu
(JNIEnv *env, jobject peer, jlong menuObject)
{
    NSMenu* nsMenu = NULL;

JNF_COCOA_ENTER(env);
    nsMenu = [((CMenu *)jlong_to_ptr(menuObject)) menu];
JNF_COCOA_EXIT(env);

    // Strong retain this menu; it'll get released in Java_apple_laf_ScreenMenu_addMenuListeners
    if (nsMenu) {
        CFRetain(nsMenu); // GC
    }

    return ptr_to_jlong(nsMenu);
}
