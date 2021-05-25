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
NSString *const IgnoreClassName = @"IgnoreAccessibility";
static jobject sAccessibilityClass = NULL;

/*
 * Common ancestor for all the accessibility peers that implements the new method-based accessibility API
 */
@implementation CommonComponentAccessibility

+ (void) initializeRolesMap {
    /*
     * Here we should keep all the mapping between the accessibility roles and implementing classes
     */
    rolesMap = [[NSMutableDictionary alloc] initWithCapacity:35];

    [rolesMap setObject:@"ButtonAccessibility" forKey:@"pushbutton"];
    [rolesMap setObject:@"ImageAccessibility" forKey:@"icon"];
    [rolesMap setObject:@"ImageAccessibility" forKey:@"desktopicon"];
    [rolesMap setObject:@"SpinboxAccessibility" forKey:@"spinbox"];
    [rolesMap setObject:@"StaticTextAccessibility" forKey:@"hyperlink"];
    [rolesMap setObject:@"StaticTextAccessibility" forKey:@"label"];
    [rolesMap setObject:@"RadiobuttonAccessibility" forKey:@"radiobutton"];
    [rolesMap setObject:@"CheckboxAccessibility" forKey:@"checkbox"];
    [rolesMap setObject:@"SliderAccessibility" forKey:@"slider"];
    [rolesMap setObject:@"ScrollAreaAccessibility" forKey:@"scrollpane"];
    [rolesMap setObject:@"ScrollBarAccessibility" forKey:@"scrollbar"];
    [rolesMap setObject:@"GroupAccessibility" forKey:@"awtcomponent"];
    [rolesMap setObject:@"GroupAccessibility" forKey:@"canvas"];
    [rolesMap setObject:@"GroupAccessibility" forKey:@"groupbox"];
    [rolesMap setObject:@"GroupAccessibility" forKey:@"internalframe"];
    [rolesMap setObject:@"GroupAccessibility" forKey:@"swingcomponent"];
    [rolesMap setObject:@"ToolbarAccessibility" forKey:@"toolbar"];

    /*
     * All the components below should be ignored by the accessibility subsystem,
     * If any of the enclosed component asks for a parent the first ancestor
     * participating in accessibility exchange should be returned.
     */
    [rolesMap setObject:IgnoreClassName forKey:@"alert"];
    [rolesMap setObject:IgnoreClassName forKey:@"colorchooser"];
    [rolesMap setObject:IgnoreClassName forKey:@"desktoppane"];
    [rolesMap setObject:IgnoreClassName forKey:@"dialog"];
    [rolesMap setObject:IgnoreClassName forKey:@"directorypane"];
    [rolesMap setObject:IgnoreClassName forKey:@"filechooser"];
    [rolesMap setObject:IgnoreClassName forKey:@"filler"];
    [rolesMap setObject:IgnoreClassName forKey:@"fontchooser"];
    [rolesMap setObject:IgnoreClassName forKey:@"frame"];
    [rolesMap setObject:IgnoreClassName forKey:@"glasspane"];
    [rolesMap setObject:IgnoreClassName forKey:@"layeredpane"];
    [rolesMap setObject:IgnoreClassName forKey:@"optionpane"];
    [rolesMap setObject:IgnoreClassName forKey:@"panel"];
    [rolesMap setObject:IgnoreClassName forKey:@"rootpane"];
    [rolesMap setObject:IgnoreClassName forKey:@"separator"];
    [rolesMap setObject:IgnoreClassName forKey:@"tooltip"];
    [rolesMap setObject:IgnoreClassName forKey:@"viewport"];
    [rolesMap setObject:IgnoreClassName forKey:@"window"];

    /*
     * Initialize CAccessibility instance
     */
#ifdef JAVA_AX_NO_IGNORES
    NSArray *ignoredKeys = [NSArray array];
#else
    NSArray *ignoredKeys = [rolesMap allKeysForObject:IgnoreClassName];
#endif

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    GET_CACCESSIBILITY_CLASS();
    DECLARE_STATIC_METHOD(jm_getAccessibility, sjc_CAccessibility, "getAccessibility", "([Ljava/lang/String;)Lsun/lwawt/macosx/CAccessibility;");
    jobjectArray result = NULL;
    jsize count = [ignoredKeys count];

    DECLARE_CLASS(jc_String, "java/lang/String");
    result = (*env)->NewObjectArray(env, count, jc_String, NULL);
    CHECK_EXCEPTION();
    if (!result) {
        NSLog(@"In %s, can't create Java array of String objects", __FUNCTION__);
        return;
    }

    NSInteger i;
    for (i = 0; i < count; i++) {
        jstring jString = NSStringToJavaString(env, [ignoredKeys objectAtIndex:i]);
        (*env)->SetObjectArrayElement(env, result, i, jString);
        (*env)->DeleteLocalRef(env, jString);
    }

    sAccessibilityClass = (*env)->CallStaticObjectMethod(env, sjc_CAccessibility, jm_getAccessibility, result);
    CHECK_EXCEPTION();
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

- (BOOL)isAccessibilityElement {
    return YES;
}

@end
