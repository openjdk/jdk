/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#import "CommonComponentAccessibility.h"
#import "JNIUtilities.h"
#import "ThreadUtilities.h"

static jclass sjc_CAccessibility = NULL;
static jmethodID sjm_getAccessibleComponent = NULL;

#define GET_ACCESSIBLECOMPONENT_STATIC_METHOD_RETURN(ret) \
    GET_CACCESSIBILITY_CLASS_RETURN(ret); \
    GET_STATIC_METHOD_RETURN(sjm_getAccessibleComponent, sjc_CAccessibility, "getAccessibleComponent", \
           "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/AccessibleComponent;", ret);

static NSMutableDictionary * _Nullable rolesMap;

/*
 * Common ancestor for all the accessibility peers that implements the new method-based accessibility API
 */
@implementation CommonComponentAccessibility

+ (void) initializeRolesMap {
    /*
     * Here we should keep all the mapping between the accessibility roles and implementing classes
     */
    rolesMap = [[NSMutableDictionary alloc] initWithCapacity:7];

    [rolesMap setObject:@"ButtonAccessibility" forKey:@"pushbutton"];
    [rolesMap setObject:@"ImageAccessibility" forKey:@"icon"];
    [rolesMap setObject:@"ImageAccessibility" forKey:@"desktopicon"];
    [rolesMap setObject:@"SpinboxAccessibility" forKey:@"spinbox"];
    [rolesMap setObject:@"StaticTextAccessibility" forKey:@"hyperlink"];
    [rolesMap setObject:@"StaticTextAccessibility" forKey:@"label"];
    [rolesMap setObject:@"RadiobuttonAccessibility" forKey:@"radiobutton"];
}

/*
 * If new implementation of the accessible component peer for the given role exists
 * return the allocated class otherwise return nil to let old implementation being initialized
 */
+ (JavaComponentAccessibility *) getComponentAccessibility:(NSString *)role
{
    AWT_ASSERT_APPKIT_THREAD;
    if (rolesMap == nil) {
        [self initializeRolesMap];
    }

    NSString *className = [rolesMap objectForKey:role];
    if (className != nil) {
        return [NSClassFromString(className) alloc];
    }
    return nil;
}

// NSAccessibilityElement protocol implementation
- (NSRect)accessibilityFrame
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    GET_ACCESSIBLECOMPONENT_STATIC_METHOD_RETURN(NSZeroRect);
    jobject axComponent = (*env)->CallStaticObjectMethod(env, sjc_CAccessibility,
                                                         sjm_getAccessibleComponent,
                                                         fAccessible, fComponent);
    CHECK_EXCEPTION();

    NSSize size = getAxComponentSize(env, axComponent, fComponent);
    NSPoint point = getAxComponentLocationOnScreen(env, axComponent, fComponent);
    (*env)->DeleteLocalRef(env, axComponent);
    point.y += size.height;

    point.y = [[[[self view] window] screen] frame].size.height - point.y;

    return NSMakeRect(point.x, point.y, size.width, size.height);
}

- (nullable id)accessibilityParent
{
    return [self accessibilityParentAttribute];
}

// AccessibleAction support
- (BOOL)performAccessibleAction:(int)index
{
    AWT_ASSERT_APPKIT_THREAD;
    JNIEnv* env = [ThreadUtilities getJNIEnv];

    GET_CACCESSIBILITY_CLASS_RETURN(FALSE);
    DECLARE_STATIC_METHOD_RETURN(jm_doAccessibleAction, sjc_CAccessibility, "doAccessibleAction",
                                 "(Ljavax/accessibility/AccessibleAction;ILjava/awt/Component;)V", FALSE);
    (*env)->CallStaticVoidMethod(env, sjc_CAccessibility, jm_doAccessibleAction,
                                 [self axContextWithEnv:(env)], index, fComponent);
    CHECK_EXCEPTION();

    return TRUE;
}

@end
