/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#import "MenuAccessibility.h"
#import "ThreadUtilities.h"
#import "sun_lwawt_macosx_CAccessibility.h"

static jclass sjc_CAccessibility = NULL;

/*
 * Implementing a protocol that represents menus both as submenu and as a
 * MenuBar components
 */
@implementation MenuAccessibility
- (NSAccessibilityRole _Nonnull)accessibilityRole
{
        if ([[[self parent] javaRole] isEqualToString:@"combobox"]) {
            return NSAccessibilityPopUpButtonRole;
        } else if ([[[self parent] javaRole] isEqualToString:@"menubar"]) {
            return NSAccessibilityMenuBarItemRole;
        } else {
            return NSAccessibilityMenuRole;
        }
}

- (BOOL)isAccessibilityElement
{
    return YES;
}

- (id _Nullable)accessibilityValue
{
    return NULL;
}

/*
 * Return all non-ignored children.
 */
- (NSArray *)accessibilityChildren {
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    GET_CACCESSIBILITY_CLASS_RETURN(nil);
    DECLARE_STATIC_METHOD_RETURN(sjm_getCurrentAccessiblePopupMenu, sjc_CAccessibility,
            "getCurrentAccessiblePopupMenu",
             "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/Accessible;", nil);
    jobject axComponent = (*env)->CallStaticObjectMethod(env, sjc_CAccessibility,
                                                             sjm_getCurrentAccessiblePopupMenu,
                                                             fAccessible, fComponent);

    CommonComponentAccessibility *currentElement = [CommonComponentAccessibility createWithAccessible:axComponent
                                                            withEnv:env withView:self->fView isCurrent:YES];

    NSArray *children = [CommonComponentAccessibility childrenOfParent:currentElement
                                withEnv:env
                                withChildrenCode:sun_lwawt_macosx_CAccessibility_JAVA_AX_ALL_CHILDREN
                                allowIgnored:NO];

    if ([children count] == 0) {
        return nil;
    } else {
        return children;
    }
}
@end
