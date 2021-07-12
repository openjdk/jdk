/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include "jni.h"
#import "TableRowAccessibility.h"
#import "JavaAccessibilityAction.h"
#import "JavaAccessibilityUtilities.h"
#import "TableAccessibility.h"
#import "CellAccessibility.h"
#import "ColumnAccessibility.h"
#import "ThreadUtilities.h"
#import "JNIUtilities.h"

@implementation TableAccessibility

- (int)accessibleRowCount
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return 0;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_getAccessibleRowCount, clsInfo, "getAccessibleRowCount", "()I", 0);
    jint javaRowsCount = (*env)->CallIntMethod(env, axContext, jm_getAccessibleRowCount);
    (*env)->DeleteLocalRef(env, axContext);
    return (int)javaRowsCount;
}

- (int)accessibleColCount
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return 0;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_getAccessibleColumnCount, clsInfo, "getAccessibleColumnCount", "()I", 0);
    jint javaColsCount = (*env)->CallIntMethod(env, axContext, jm_getAccessibleColumnCount);
    (*env)->DeleteLocalRef(env, axContext);
    return (int)javaColsCount;
}

- (NSArray<NSNumber *> *)selectedAccessibleRows
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return nil;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_getSelectedAccessibleRows, clsInfo, "getSelectedAccessibleRows", "()[I", nil);
    jintArray selectedRowNumbers = (*env)->CallObjectMethod(env, axContext, jm_getSelectedAccessibleRows);
    (*env)->DeleteLocalRef(env, axContext);
    if (selectedRowNumbers == NULL) {
        return nil;
    }
    jsize arrayLen = (*env)->GetArrayLength(env, selectedRowNumbers);
    jint *indexsis = (*env)->GetIntArrayElements(env, selectedRowNumbers, 0);
    NSMutableArray<NSNumber *> *nsArraySelectedRowNumbers = [NSMutableArray<NSNumber *> arrayWithCapacity:arrayLen];
    for (int i = 0; i < arrayLen; i++) {
        [nsArraySelectedRowNumbers addObject:[NSNumber numberWithInt:indexsis[i]]];
    }
    (*env)->DeleteLocalRef(env, selectedRowNumbers);
    return [NSArray<NSNumber *> arrayWithArray:nsArraySelectedRowNumbers];
}

- (NSArray<NSNumber *> *)selectedAccessibleColumns
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return nil;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_getSelectedAccessibleColumns, clsInfo, "getSelectedAccessibleColumns", "()[I", nil);
    jintArray selectedColumnNumbers = (*env)->CallObjectMethod(env, axContext, jm_getSelectedAccessibleColumns);
    (*env)->DeleteLocalRef(env, axContext);
    if (selectedColumnNumbers == NULL) {
        return nil;
    }
    jsize arrayLen = (*env)->GetArrayLength(env, selectedColumnNumbers);
    jint *indexsis = (*env)->GetIntArrayElements(env, selectedColumnNumbers, 0);
    NSMutableArray<NSNumber *> *nsArraySelectedColumnNumbers = [NSMutableArray<NSNumber *> arrayWithCapacity:arrayLen];
    for (int i = 0; i < arrayLen; i++) {
        [nsArraySelectedColumnNumbers addObject:[NSNumber numberWithInt:indexsis[i]]];
    }
    (*env)->DeleteLocalRef(env, selectedColumnNumbers);
    return [NSArray<NSNumber *> arrayWithArray:nsArraySelectedColumnNumbers];
}

- (int)accessibleRowAtIndex:(int)index
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return 0;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_getAccessibleRowAtIndex, clsInfo, "getAccessibleRowAtIndex", "(I)I", -1);
    jint rowAtIndex = (*env)->CallIntMethod(env, axContext, jm_getAccessibleRowAtIndex, (jint)index);
    (*env)->DeleteLocalRef(env, axContext);
    return (int)rowAtIndex;
}

- (int)accessibleColumnAtIndex:(int)index
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return 0;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_getAccessibleColumnAtIndex, clsInfo, "getAccessibleColumnAtIndex", "(I)I", -1);
    jint columnAtIndex = (*env)->CallIntMethod(env, axContext, jm_getAccessibleColumnAtIndex, (jint)index);
    (*env)->DeleteLocalRef(env, axContext);
    return (int)columnAtIndex;
}

- (BOOL) isAccessibleChildSelectedFromIndex:(int)index
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return NO;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_isAccessibleChildSelected, clsInfo, "isAccessibleChildSelected", "(I)Z", NO);
    jboolean isAccessibleChildSelected = (*env)->CallIntMethod(env, axContext, jm_isAccessibleChildSelected, (jint)index);
    (*env)->DeleteLocalRef(env, axContext);
    return isAccessibleChildSelected;
}

// NSAccessibilityElement protocol methods

- (NSArray *)accessibilityChildren
{
    NSArray *children = [self accessibilityRows];
    NSArray *columns = [self accessibilityColumns];
    NSMutableArray *results = [NSMutableArray arrayWithCapacity:[children count] + [columns count]];
    [results addObjectsFromArray:children];
    [results addObjectsFromArray:columns];
    return [NSArray arrayWithArray:results];
}

- (NSArray *)accessibilitySelectedChildren
{
    return [self accessibilitySelectedRows];
}

- (NSArray *)accessibilityRows
{
    int rowCount = [self accessibleRowCount];
    NSMutableArray *children = [NSMutableArray arrayWithCapacity:rowCount];
    for (int i = 0; i < rowCount; i++) {
        [children addObject:[[TableRowAccessibility alloc] initWithParent:self
                                                                      withEnv:[ThreadUtilities getJNIEnv]
                                                               withAccessible:NULL
                                                                    withIndex:i
                                                                     withView:[self view]
                                                                 withJavaRole:JavaAccessibilityIgnore]];
    }
    return [NSArray arrayWithArray:children];
}

- (nullable NSArray<id<NSAccessibilityRow>> *)accessibilitySelectedRows
{
    NSArray<NSNumber *> *selectedRowIndexses = [self selectedAccessibleRows];
    NSMutableArray *children = [NSMutableArray arrayWithCapacity:[selectedRowIndexses count]];
    for (NSNumber *index in selectedRowIndexses) {
        [children addObject:[[TableRowAccessibility alloc] initWithParent:self
                                                                      withEnv:[ThreadUtilities getJNIEnv]
                                                               withAccessible:NULL
                                                                    withIndex:index.unsignedIntValue
                                                                     withView:[self view]
                                                                 withJavaRole:JavaAccessibilityIgnore]];
    }
    return [NSArray arrayWithArray:children];
}

- (NSString *)accessibilityLabel
{
    return [super accessibilityLabel] == NULL ? @"table" : [super accessibilityLabel];
}

- (NSRect)accessibilityFrame
{
    return [super accessibilityFrame];
}

- (id)accessibilityParent
{
    return [super accessibilityParent];
}

- (nullable NSArray *)accessibilityColumns
{
    int colCount = [self accessibleColCount];
    NSMutableArray *columns = [NSMutableArray arrayWithCapacity:colCount];
    for (int i = 0; i < colCount; i++) {
        [columns addObject:[[ColumnAccessibility alloc] initWithParent:self
                                                                   withEnv:[ThreadUtilities getJNIEnv]
                                                            withAccessible:NULL
                                                                 withIndex:i
                                                                  withView:self->fView
                                                              withJavaRole:JavaAccessibilityIgnore]];
    }
    return [NSArray arrayWithArray:columns];
}

- (nullable NSArray *)accessibilitySelectedColumns
{
    NSArray<NSNumber *> *indexes = [self selectedAccessibleColumns];
    NSMutableArray *columns = [NSMutableArray arrayWithCapacity:[indexes count]];
    for (NSNumber *i in indexes) {
        [columns addObject:[[ColumnAccessibility alloc] initWithParent:self
                                                                   withEnv:[ThreadUtilities getJNIEnv]
                                                            withAccessible:NULL
                                                                 withIndex:i.unsignedIntValue
                                                                  withView:self->fView
                                                              withJavaRole:JavaAccessibilityIgnore]];
    }
    return [NSArray arrayWithArray:columns];
}

/* Other optional NSAccessibilityTable Methods
- (nullable NSArray<id<NSAccessibilityRow>> *)accessibilityVisibleRows;
- (nullable NSArray *)accessibilityColumns;
- (nullable NSArray *)accessibilitySelectedColumns;
- (nullable NSArray *)accessibilityVisibleColumns;

 - (nullable NSArray *)accessibilitySelectedCells;
- (nullable NSArray *)accessibilityVisibleCells;
- (nullable NSArray *)accessibilityRowHeaderUIElements;
- (nullable NSArray *)accessibilityColumnHeaderUIElements;
 */

@end
