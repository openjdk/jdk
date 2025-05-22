/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2024, JetBrains s.r.o.. All rights reserved.
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

#import "OutlineAccessibility.h"
#import "JavaAccessibilityUtilities.h"
#import "ThreadUtilities.h"
#import "JNIUtilities.h"

static jclass sjc_CAccessibility = NULL;

static jmethodID sjm_isTreeRootVisible = NULL;
#define GET_ISTREEROOTVISIBLE_METHOD_RETURN(ret) \
    GET_CACCESSIBILITY_CLASS_RETURN(ret); \
    GET_STATIC_METHOD_RETURN(sjm_isTreeRootVisible, sjc_CAccessibility, "isTreeRootVisible", \
                     "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Z", ret);

@implementation OutlineAccessibility

- (BOOL)isTreeRootVisible
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    GET_ISTREEROOTVISIBLE_METHOD_RETURN(NO);
    bool isTreeRootVisible = (*env)->CallStaticBooleanMethod(env, sjc_CAccessibility, sjm_isTreeRootVisible, fAccessible, fComponent);
    CHECK_EXCEPTION();
    return isTreeRootVisible;
}

// NSAccessibilityElement protocol methods

- (NSString *)accessibilityLabel
{
    return [[super accessibilityLabel] isEqualToString:@"list"] ? @"tree" : [super accessibilityLabel];
}

- (nullable NSArray<id<NSAccessibilityRow>> *)accessibilityRows
{
    return [self accessibilityChildren];
}

- (nullable NSArray<id<NSAccessibilityRow>> *)accessibilitySelectedRows
{
    return [self accessibilitySelectedChildren];
}

- (nullable  NSArray<id<NSAccessibilityRow>> *)accessibilityChildren
{
    if (![self isCacheValid]) {
        NSArray *t = [super accessibilityChildren];
        if (t != nil) {
            rowCache = [[NSMutableArray arrayWithArray:t] retain];
        } else {
            rowCache = nil;
        }
        rowCacheValid = YES;
    }
    return rowCache;
}

- (nullable NSArray<id<NSAccessibilityRow>> *)accessibilitySelectedChildren
{
    if (!selectedRowCacheValid) {
        NSArray *t = [super accessibilitySelectedChildren];
        if (t != nil) {
            selectedRowCache = [[NSMutableArray arrayWithArray:t] retain];
        } else {
            selectedRowCache = nil;
        }
        selectedRowCacheValid = YES;
    }
    return selectedRowCache;
}

- (BOOL)isCacheValid
{
    if (rowCacheValid && [[self parent] respondsToSelector:NSSelectorFromString(@"isCacheValid")]) {
        return [[self parent] isCacheValid];
    }
    return rowCacheValid;
}

- (void)invalidateCache
{
    rowCacheValid = NO;
}

- (void)invalidateSelectionCache
{
    selectedRowCacheValid = NO;
}

- (void)postSelectionChanged
{
    AWT_ASSERT_APPKIT_THREAD;
    [self invalidateSelectionCache];
    [super postSelectionChanged];
}

- (void)postTreeNodeCollapsed
{
    AWT_ASSERT_APPKIT_THREAD;
    [self invalidateCache];
    [super postTreeNodeCollapsed];
}

- (void)postTreeNodeExpanded
{
    AWT_ASSERT_APPKIT_THREAD;
    [self invalidateCache];
    [super postTreeNodeExpanded];
}

- (void)postSelectedCellsChanged
{
    AWT_ASSERT_APPKIT_THREAD;
    [self invalidateSelectionCache];
    [super postSelectedCellsChanged];
}

@end
