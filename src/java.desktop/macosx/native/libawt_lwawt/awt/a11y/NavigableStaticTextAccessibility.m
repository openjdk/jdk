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

#import "NavigableStaticTextAccessibility.h"

@implementation NavigableStaticTextAccessibility
- (NSRect)accessibilityFrameForRange:(NSRange)range
{
    //NSLog(@"in NavigableStaticTextAccessibility accessibilityFrameForRange");
    NSRect rect = [self accessibilityBoundsForRangeAttribute:range];
    //NSLog(@"Frame for range %@ is %@", NSStringFromRange(range), NSStringFromRect(rect));
    return rect;
}
- (int)accessibilityLineForIndex:(int)index
{
    //NSLog(@"in NavigableStaticTextAccessibility accessibilityLineForIndex");
    int line = [self accessibilityLineForIndexAttribute:index];
    //NSLog(@"Line number for index %d is: %d", index, line);
    return line;
}
- (NSRange)accessibilityRangeForLine:(int)line
{
    //NSLog(@"in NavigableStaticTextAccessibility accessibilityRangeForLine");
    NSRange range = [self accessibilityRangeForLineAttribute:line];
    //NSLog(@"Range for line %d is %@", line, NSStringFromRange(range));
    return range;
}
- (nullable NSString *)accessibilityStringForRange:(NSRange)range
{
    //NSLog(@"in NavigableStaticTextAccessibility accessibilityStringForRange");
    NSString * str = [self accessibilityStringForRangeAttribute:range];
    //NSLog(@"String  for range %@ is %@", NSStringFromRange(range), str);
    return str;
}

- (NSRange) accessibilityRangeForPosition:(NSPoint)point
{
    NSLog(@"in StaticTextAccessibility accessibilityRangeForPoint");
    NSRange range = [self accessibilityRangeForPositionAttribute:point];
    //NSLog(@"Range  for point %@ is %@", NSStringFromRange(range), NSStringFromPoint(point));
    return range;
}

- (NSRange)accessibilityRangeForIndex:(int)index
{
    NSLog(@"in StaticTextAccessibility accessibilityRangeForIndex");
    NSRange range = [self accessibilityRangeForIndexAttribute:index];
    //NSLog(@"Range for index %@ is %d", NSStringFromRange(range), index);
    return range;
}

- (NSString *)accessibleSelectedText { 
    // JNIEnv* env = [ThreadUtilities getJNIEnv];
    // static JNF_STATIC_MEMBER_CACHE(jm_getSelectedText, sjc_CAccessibleText, "getSelectedText", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;");
    // jobject axText = JNFCallStaticObjectMethod(env, jm_getSelectedText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    // if (axText == NULL) return @"";
    // NSString* str = JNFJavaToNSString(env, axText);
    // (*env)->DeleteLocalRef(env, axText);
    // return str;
    NSLog(@"in accessibleSelectedText");
    return @"";
}

- (NSValue *)accessibleSelectedTextRange { 
    // JNIEnv *env = [ThreadUtilities getJNIEnv];
    // static JNF_STATIC_MEMBER_CACHE(jm_getSelectedTextRange, sjc_CAccessibleText, "getSelectedTextRange", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)[I");
    // jintArray axTextRange = JNFCallStaticObjectMethod(env, jm_getSelectedTextRange, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    // if (axTextRange == NULL) return nil;

    // return javaConvertIntArrayToNSRangeValue(env, axTextRange);
    NSLog(@"in accessibleSelectedTextRange");
    return [NSValue valueWithRange:NSMakeRange(0, 0)];
}

- (NSNumber *)accessibleNumberOfCharacters { 
    // cmcnote: should coalesce these two calls - radr://3951923
    // also, static text doesn't always have accessibleText. if axText is null, should get the charcount of the accessibleName instead
    // JNIEnv *env = [ThreadUtilities getJNIEnv];
    // jobject axText = JNFCallStaticObjectMethod(env, sjm_getAccessibleText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    // NSNumber* num = [NSNumber numberWithInt:getAxTextCharCount(env, axText, fComponent)];
    // (*env)->DeleteLocalRef(env, axText);
    // return num;
    NSLog(@"in accessibleNumberOfCharacters");
    return [NSNumber numberWithInt:0];
}

- (NSNumber *)accessibleInsertionPointLineNumber { 
    // JNIEnv *env = [ThreadUtilities getJNIEnv];
    // static JNF_STATIC_MEMBER_CACHE(jm_getLineNumberForInsertionPoint, sjc_CAccessibleText, "getLineNumberForInsertionPoint", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)I");
    // jint row = JNFCallStaticIntMethod(env, jm_getLineNumberForInsertionPoint, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    // if (row < 0) return nil;
    NSLog(@"in accessibleInsertionPointLineNumber");
    return [NSNumber numberWithInt:0];
}

- (BOOL)accessibleIsValueSettable {
    // if text is enabled and editable, it's settable (according to NSCellTextAttributesAccessibility)
    // BOOL isEnabled = [(NSNumber *)[self accessibilityEnabledAttribute] boolValue];
    // if (!isEnabled) return NO;

    // JNIEnv* env = [ThreadUtilities getJNIEnv];
    // jobject axEditableText = JNFCallStaticObjectMethod(env, sjm_getAccessibleEditableText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    // if (axEditableText == NULL) return NO;
    // (*env)->DeleteLocalRef(env, axEditableText);
    NSLog(@"in accessibleIsValueSettable");
    return YES;
}

- (BOOL)accessibleIsPasswordText {
    NSLog(@"in accessibleIsPasswordText");
    return [[self javaRole] isEqualToString:@"passwordtext"];
}

- (void)accessibleSetSelectedText:(NSString *)accessibilitySelectedText { 
    // JNIEnv *env = [ThreadUtilities getJNIEnv];
    // jstring jstringValue = JNFNSToJavaString(env, accessibilitySelectedText);
    // static JNF_STATIC_MEMBER_CACHE(jm_setSelectedText, sjc_CAccessibleText, "setSelectedText", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;Ljava/lang/String;)V");
    // JNFCallStaticVoidMethod(env, jm_setSelectedText, fAccessible, fComponent, jstringValue); // AWT_THREADING Safe (AWTRunLoop)
    NSLog(@"in accessibleSetSelectedText");
}

- (void)accessibleSetSelectedTextRange:(NSRange)accessibilitySelectedTextRange {
    // jint startIndex = accessibilitySelectedTextRange.location;
    // jint endIndex = startIndex + accessibilitySelectedTextRange.length;

    // JNIEnv *env = [ThreadUtilities getJNIEnv];
    // static JNF_STATIC_MEMBER_CACHE(jm_setSelectedTextRange, sjc_CAccessibleText, "setSelectedTextRange", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;II)V");
    // JNFCallStaticVoidMethod(env, jm_setSelectedTextRange, fAccessible, fComponent, startIndex, endIndex); // AWT_THREADING Safe (AWTRunLoop)
    NSLog(@"in accessibleSetSelectedTextRange");
}

@end
