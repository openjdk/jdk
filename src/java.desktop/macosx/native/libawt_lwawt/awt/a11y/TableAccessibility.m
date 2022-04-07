/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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

#include "jni.h"
#import "TableRowAccessibility.h"
#import "JavaAccessibilityAction.h"
#import "JavaAccessibilityUtilities.h"
#import "TableAccessibility.h"
#import "CellAccessibility.h"
#import "ColumnAccessibility.h"
#import "ThreadUtilities.h"
#import "JNIUtilities.h"
#import "CellAccessibility.h"
#import "sun_lwawt_macosx_CAccessibility.h"
#import "sun_lwawt_macosx_CAccessible.h"

static jclass sjc_CAccessibility = NULL;

static jmethodID sjm_getAccessibleName = NULL;
#define GET_ACCESSIBLENAME_METHOD_RETURN(ret) \
    GET_CACCESSIBILITY_CLASS_RETURN(ret); \
    GET_STATIC_METHOD_RETURN(sjm_getAccessibleName, sjc_CAccessibility, "getAccessibleName", \
                     "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;", ret);

@implementation TableAccessibility

- (id)getTableInfo:(jint)info
{
    if (fAccessible == NULL) return 0;

    JNIEnv* env = [ThreadUtilities getJNIEnv];
    GET_CACCESSIBILITY_CLASS_RETURN(nil);
    DECLARE_STATIC_METHOD_RETURN(jm_getTableInfo, sjc_CAccessibility, "getTableInfo",
                          "(Ljavax/accessibility/Accessible;Ljava/awt/Component;I)I", nil);
    jint count = (*env)->CallStaticIntMethod(env, sjc_CAccessibility, jm_getTableInfo, fAccessible,
                                        fComponent, info);
    CHECK_EXCEPTION();
    NSNumber *index = [NSNumber numberWithInt:count];
    return index;
}

- (NSArray<NSNumber *> *)getTableSelectedInfo:(jint)info
{
    if (fAccessible == NULL) return 0;

    JNIEnv* env = [ThreadUtilities getJNIEnv];
    GET_CACCESSIBILITY_CLASS_RETURN(nil);
    DECLARE_STATIC_METHOD_RETURN(jm_getTableSelectedInfo, sjc_CAccessibility, "getTableSelectedInfo",
                          "(Ljavax/accessibility/Accessible;Ljava/awt/Component;I)[I", nil);
    jintArray selected = (*env)->CallStaticObjectMethod(env, sjc_CAccessibility, jm_getTableSelectedInfo, fAccessible,
                                        fComponent, info);
    CHECK_EXCEPTION();
    if (selected == NULL) {
        return nil;
    }
    jsize arrayLen = (*env)->GetArrayLength(env, selected);
    jint *indexsis = (*env)->GetIntArrayElements(env, selected, 0);
    NSMutableArray<NSNumber *> *nsArraySelected = [NSMutableArray<NSNumber *> arrayWithCapacity:arrayLen];
    for (int i = 0; i < arrayLen; i++) {
        [nsArraySelected addObject:[NSNumber numberWithInt:indexsis[i]]];
    }
    (*env)->DeleteLocalRef(env, selected);
    return [NSArray<NSNumber *> arrayWithArray:nsArraySelected];
}

- (int)accessibleRowAtIndex:(int)index
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axContext = [self axContextWithEnv:env];
    if (axContext == NULL) return 0;
    jclass clsInfo = (*env)->GetObjectClass(env, axContext);
    DECLARE_METHOD_RETURN(jm_getAccessibleRowAtIndex, clsInfo, "getAccessibleRowAtIndex", "(I)I", -1);
    jint rowAtIndex = (*env)->CallIntMethod(env, axContext, jm_getAccessibleRowAtIndex, (jint)index);
    CHECK_EXCEPTION();
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
    CHECK_EXCEPTION();
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
    CHECK_EXCEPTION();
    (*env)->DeleteLocalRef(env, axContext);
    return isAccessibleChildSelected;
}

- (TableRowAccessibility *)createRowWithIndex:(NSUInteger)index
{
    if (rowCache == nil) {
        int rowCount = [self accessibilityRowCount];
        rowCache = [[NSMutableDictionary<NSNumber*, id> dictionaryWithCapacity:rowCount] retain];
    }

    id row = [rowCache objectForKey:[NSNumber numberWithUnsignedInteger:index]];
    if (row == nil) {
        row = [[TableRowAccessibility alloc] initWithParent:self
                                                        withEnv:[ThreadUtilities getJNIEnv]
                                                 withAccessible:NULL
                                                      withIndex:index
                                                       withView:[self view]
                                                   withJavaRole:JavaAccessibilityIgnore];
        [rowCache setObject:row forKey:[NSNumber numberWithUnsignedInteger:index]];
    }

    return row;
}

// NSAccessibilityElement protocol methods

- (NSArray *)accessibilityChildren
{
    return [self accessibilityRows];
}

- (NSArray *)accessibilitySelectedChildren
{
    return [self accessibilitySelectedRows];
}

- (NSArray *)accessibilityRows
{
    int rowCount = [self accessibilityRowCount];
    NSMutableArray *children = [NSMutableArray arrayWithCapacity:rowCount];
    for (int i = 0; i < rowCount; i++) {
        [children addObject:[self createRowWithIndex:i]];
    }
    return [NSArray arrayWithArray:children];
}

- (nullable NSArray<id<NSAccessibilityRow>> *)accessibilitySelectedRows
{
    NSArray<NSNumber *> *selectedRowIndexses = [self getTableSelectedInfo:sun_lwawt_macosx_CAccessibility_JAVA_AX_ROWS];
    NSMutableArray *children = [NSMutableArray arrayWithCapacity:[selectedRowIndexses count]];
    for (NSNumber *index in selectedRowIndexses) {
        [children addObject:[self createRowWithIndex:index.unsignedIntValue]];
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

- (NSInteger)accessibilityRowCount
{
    return [[self getTableInfo:sun_lwawt_macosx_CAccessibility_JAVA_AX_ROWS] integerValue];
}

- (NSInteger)accessibilityColumnCount
{
    return [[self getTableInfo:sun_lwawt_macosx_CAccessibility_JAVA_AX_COLS] integerValue];
}

- (id)accessibilityCellForColumn:(NSInteger)column row:(NSInteger)row
{
    return [[[self createRowWithIndex:row] accessibilityChildren] objectAtIndex:column];
}

- (NSArray *)accessibilitySelectedCells
{
    NSArray *selectedRows = [self getTableSelectedInfo:sun_lwawt_macosx_CAccessibility_JAVA_AX_ROWS];
    NSArray *selectedColumns = [self getTableSelectedInfo:sun_lwawt_macosx_CAccessibility_JAVA_AX_COLS];
    NSMutableArray *selectedCells = [NSMutableArray arrayWithCapacity:[selectedRows count] * [selectedColumns count]];
    for (NSNumber *row in selectedRows) {
        for (NSNumber *col in selectedColumns) {
            CellAccessibility *cell = [self accessibilityCellForColumn:[col integerValue] row:[row integerValue]];
            [selectedCells addObject:cell];
        }
    }
    return [NSArray arrayWithArray:selectedCells];
}

- (void)clearCache {
    for (NSNumber *key in [rowCache allKeys]) {
        [[rowCache objectForKey:key] release];
    }
    [rowCache release];
    rowCache = nil;
}

@end

/*
 * Class:     sun_lwawt_macosx_CAccessible
 * Method:    tableContentIndexDestroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CAccessible_tableContentCacheClear
        (JNIEnv *env, jclass class, jlong element)
{
    JNI_COCOA_ENTER(env);
        [ThreadUtilities performOnMainThread:@selector(clearCache)
                                          on:(CommonComponentAccessibility *)jlong_to_ptr(element)
                                  withObject:nil
                               waitUntilDone:NO];
    JNI_COCOA_EXIT(env);
}
