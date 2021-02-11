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

#import "ButtonAccessibility.h"
#import "JNIUtilities.h"
#import "ThreadUtilities.h"

static jclass sjc_CAccessibility = NULL;

/*
 * Implementation of the accessibility peer for the pushbutton role
 */
@implementation ButtonAccessibility
- (nullable NSString *)accessibilityLabel
{
    return [self accessibilityTitleAttribute];
}

- (BOOL)accessibilityPerformPress
{
    AWT_ASSERT_APPKIT_THREAD;
    JNIEnv* env = [ThreadUtilities getJNIEnv];

    GET_CACCESSIBILITY_CLASS_RETURN(FALSE);
    DECLARE_STATIC_METHOD_RETURN(jm_doAccessibleAction, sjc_CAccessibility, "doAccessibleAction",
                     "(Ljavax/accessibility/AccessibleAction;ILjava/awt/Component;)V", FALSE);
    (*env)->CallStaticVoidMethod(env, sjc_CAccessibility, jm_doAccessibleAction,
                                 [self axContextWithEnv:(env)], 0, fComponent);
    CHECK_EXCEPTION();

    return TRUE;
}

@end
