/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#import "CMenuComponent.h"
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "ThreadUtilities.h"

@class CMenuItem;

@implementation CMenuComponent

-(id) initWithPeer:(jobject)peer {
    self = [super init];
    if (self) {
        // the peer has been made clobal ref before
        fPeer = peer;
    }
    return self;
}

-(void) cleanup {
    // Used by subclasses
}

-(void) disposer {
    JNIEnv *env = [ThreadUtilities getJNIEnvUncached];
    JNFDeleteGlobalRef(env, fPeer);
    fPeer = NULL;

    [self cleanup];

    CFRelease(self); // GC
}

// The method is used by all subclasses, since the process of the creation
// is the same. The only exception is the CMenuItem class.
- (void) _create_OnAppKitThread: (NSMutableArray *)argValue {
    jobject cPeerObjGlobal = (jobject)[[argValue objectAtIndex: 0] pointerValue];
    CMenuItem *aCMenuItem = [self initWithPeer:cPeerObjGlobal];
    [argValue removeAllObjects];
    [argValue addObject: aCMenuItem];
}

//-(void) dealloc { [super dealloc]; }
//- (void)finalize { [super finalize]; }

@end

/*
 * Class:     sun_lwawt_macosx_CMenuComponent
 * Method:    nativeDispose
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_CMenuComponent_nativeDispose
(JNIEnv *env, jobject peer, jlong menuItemObj)
{
JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThread:@selector(disposer)
                                      on:((id)jlong_to_ptr(menuItemObj))
                              withObject:nil
                           waitUntilDone:NO];

JNF_COCOA_EXIT(env);
}
