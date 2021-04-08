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

#import "StaticTextAccessibility.h"

@implementation StaticTextAccessibility

- (nullable NSString *)accessibilityAttributedStringForRange:(NSRange)range
{
    //NSLog(@"in StaticTextAccessibility accessibilityAttributedStringForRange");
    return [self accessibilityStringForRangeAttribute:range];
}

- (nullable NSString *)accessibilityValue
{
    //NSLog(@"in StaticTextAccessibility accessibilityValue");
    return [self accessibilityValueAttribute];
}

- (NSRange)accessibilityVisibleCharacterRange
{
    //NSLog(@"in StaticTextAccessibility accessibilityVisibleCharacterRange");
    return [self accessibilityVisibleCharacterRangeAttribute];
}

- (NSRange) accessibilityRangeForPoint:(NSPoint)point
{
    NSLog(@"in StaticTextAccessibility accessibilityRangeForPoint");
    NSRange range = [self accessibilityRangeForPositionAttribute:point];
    NSLog(@"Range  for point %@ is %@", NSStringFromRange(range), NSStringFromPoint(point));
    return range;
}

- (NSRange)accessibilityRangeForIndex:(int)index
{
    NSLog(@"in StaticTextAccessibility accessibilityRangeForIndex");
    NSRange range = [self accessibilityRangeForIndexAttribute:index];
    NSLog(@"Range for index %@ is %d", NSStringFromRange(range), index);
    return range;
}

@end
