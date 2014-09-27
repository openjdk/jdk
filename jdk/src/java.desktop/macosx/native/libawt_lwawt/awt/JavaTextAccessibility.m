/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#import "JavaTextAccessibility.h"
#import "JavaAccessibilityAction.h"
#import "JavaAccessibilityUtilities.h"
#import "ThreadUtilities.h"


static JNF_CLASS_CACHE(sjc_CAccessibleText, "sun/lwawt/macosx/CAccessibleText");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleText, sjc_CAccessibility, "getAccessibleText", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/AccessibleText;");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleEditableText, sjc_CAccessibleText, "getAccessibleEditableText", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljavax/accessibility/AccessibleEditableText;");
static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleName, sjc_CAccessibility, "getAccessibleName", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;");

/*
 * Converts an int array to an NSRange wrapped inside an NSValue
 * takes [start, end] values and returns [start, end - start]
 */
NSValue *javaIntArrayToNSRangeValue(JNIEnv* env, jintArray array) {
    jint *values = (*env)->GetIntArrayElements(env, array, 0);
    if (values == NULL) {
        // Note: Java will not be on the stack here so a java exception can't happen and no need to call ExceptionCheck.
        NSLog(@"%s failed calling GetIntArrayElements", __FUNCTION__);
        return nil;
    };
    NSValue *value = [NSValue valueWithRange:NSMakeRange(values[0], values[1] - values[0])];
    (*env)->ReleaseIntArrayElements(env, array, values, 0);
    return value;
}

@implementation JavaTextAccessibility

// based strongly upon NSTextViewAccessibility:accessibilityAttributeNames
- (NSArray *)initializeAttributeNamesWithEnv:(JNIEnv *)env
{
    static NSArray *attributes = nil;

    if (attributes == nil) {
        //APPKIT_LOCK;
        if (attributes == nil) {
            NSMutableArray *temp = [[super initializeAttributeNamesWithEnv:env] mutableCopy];
            //[temp removeObject:NSAccessibilityTitleAttribute]; // title may have been set in the superclass implementation - some static text reports from java that it has a name
            [temp addObjectsFromArray:[NSArray arrayWithObjects:
                NSAccessibilityValueAttribute,
                NSAccessibilitySelectedTextAttribute,
                NSAccessibilitySelectedTextRangeAttribute,
                NSAccessibilityNumberOfCharactersAttribute,
                NSAccessibilityVisibleCharacterRangeAttribute,
                NSAccessibilityInsertionPointLineNumberAttribute,
                //    NSAccessibilitySharedTextUIElementsAttribute, // cmcnote: investigate what these two are for. currently unimplemented
                //    NSAccessibilitySharedCharacterRangeAttribute,
                nil]];
            attributes = [[NSArray alloc] initWithArray:temp];
            [temp release];
        }
        //APPKIT_UNLOCK;
    }
    return attributes;
}

// copied from NSTextViewAccessibility.
- (NSArray *)accessibilityParameterizedAttributeNames
{
    static NSArray *attributes = nil;

    if (attributes == nil) {
        //APPKIT_LOCK;
        if (attributes == nil) {
            attributes = [[NSArray alloc] initWithObjects:
                NSAccessibilityLineForIndexParameterizedAttribute,
                NSAccessibilityRangeForLineParameterizedAttribute,
                NSAccessibilityStringForRangeParameterizedAttribute,
                NSAccessibilityRangeForPositionParameterizedAttribute,
                NSAccessibilityRangeForIndexParameterizedAttribute,
                NSAccessibilityBoundsForRangeParameterizedAttribute,
                //NSAccessibilityRTFForRangeParameterizedAttribute, // cmcnote: not sure when/how these three are used. Investigate. radr://3960026
                //NSAccessibilityStyleRangeForIndexParameterizedAttribute,
                //NSAccessibilityAttributedStringForRangeParameterizedAttribute,
                nil];
        }
        //APPKIT_UNLOCK;
    }
    return attributes;
}

- (NSString *)accessibilityValueAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    if ([[self accessibilityRoleAttribute] isEqualToString:NSAccessibilityStaticTextRole]) {
        // if it's static text, the AppKit AXValue is the java accessibleName
        jobject axName = JNFCallStaticObjectMethod(env, sjm_getAccessibleName, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
        if (axName != NULL) {
            return JNFJavaToNSString(env, axName);
        }
        // value is still nil if no accessibleName for static text. Below, try to get the accessibleText.
    }

    // cmcnote: inefficient to make three distinct JNI calls. Coalesce. radr://3951923
    jobject axText = JNFCallStaticObjectMethod(env, sjm_getAccessibleText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axText == NULL) return nil;

    jobject axEditableText = JNFCallStaticObjectMethod(env, sjm_getAccessibleEditableText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axEditableText == NULL) return nil;

    static JNF_STATIC_MEMBER_CACHE(jm_getTextRange, sjc_CAccessibleText, "getTextRange", "(Ljavax/accessibility/AccessibleEditableText;IILjava/awt/Component;)Ljava/lang/String;");
    NSString *string = JNFJavaToNSString(env, JNFCallStaticObjectMethod(env, jm_getTextRange, axEditableText, 0, getAxTextCharCount(env, axEditableText, fComponent), fComponent)); // AWT_THREADING Safe (AWTRunLoop)
    if (string == nil) string = @"";
    return string;
}

- (BOOL)accessibilityIsValueAttributeSettable
{
    // if text is enabled and editable, it's settable (according to NSCellTextAttributesAccessibility)
    BOOL isEnabled = [(NSNumber *)[self accessibilityEnabledAttribute] boolValue];
    if (!isEnabled) return NO;

    JNIEnv* env = [ThreadUtilities getJNIEnv];
    jobject axEditableText = JNFCallStaticObjectMethod(env, sjm_getAccessibleEditableText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axEditableText == NULL) return NO;
    return YES;
}

- (void)accessibilitySetValueAttribute:(id)value
{
// cmcnote: should set the accessibleEditableText to the stringValue of value - AccessibleEditableText.setTextContents(String s)
#ifdef JAVA_AX_DEBUG
    NSLog(@"Not yet implemented: %s\n", __FUNCTION__); // radr://3954018
#endif
}

// Currently selected text (NSString)
- (NSString *)accessibilitySelectedTextAttribute
{
    JNIEnv* env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getSelectedText, sjc_CAccessibleText, "getSelectedText", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;");
    jobject axText = JNFCallStaticObjectMethod(env, jm_getSelectedText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axText == NULL) return @"";
    return JNFJavaToNSString(env, axText);
}

- (BOOL)accessibilityIsSelectedTextAttributeSettable
{
    return YES; //cmcnote: for AXTextField that's selectable, it's settable. Investigate further.
}

- (void)accessibilitySetSelectedTextAttribute:(id)value
{
#ifdef JAVA_AX_DEBUG_PARMS
    if (![value isKindOfClass:[NSString class]]) {
        JavaAccessibilityRaiseSetAttributeToIllegalTypeException(__FUNCTION__, self, NSAccessibilitySelectedTextAttribute, value);
        return;
    }
#endif

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jstring jstringValue = JNFNSToJavaString(env, (NSString *)value);
    static JNF_STATIC_MEMBER_CACHE(jm_setSelectedText, sjc_CAccessibleText, "setSelectedText", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;Ljava/lang/String;)V");
    JNFCallStaticVoidMethod(env, jm_setSelectedText, fAccessible, fComponent, jstringValue); // AWT_THREADING Safe (AWTRunLoop)
}

// Range of selected text (NSValue)
- (NSValue *)accessibilitySelectedTextRangeAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getSelectedTextRange, sjc_CAccessibleText, "getSelectedTextRange", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)[I");
    jintArray axTextRange = JNFCallStaticObjectMethod(env, jm_getSelectedTextRange, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axTextRange == NULL) return nil;

    return javaIntArrayToNSRangeValue(env, axTextRange);
}

- (BOOL)accessibilityIsSelectedTextRangeAttributeSettable
{
    return [(NSNumber *)[self accessibilityEnabledAttribute] boolValue]; // cmcnote: also may want to find out if isSelectable. Investigate.
}

- (void)accessibilitySetSelectedTextRangeAttribute:(id)value
{
#ifdef JAVA_AX_DEBUG_PARMS
    if (!([value isKindOfClass:[NSValue class]] && strcmp([(NSValue *)value objCType], @encode(NSRange)) == 0)) {
        JavaAccessibilityRaiseSetAttributeToIllegalTypeException(__FUNCTION__, self, NSAccessibilitySelectedTextRangeAttribute, value);
        return;
    }
#endif

    NSRange range = [(NSValue *)value rangeValue];
    jint startIndex = range.location;
    jint endIndex = startIndex + range.length;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_setSelectedTextRange, sjc_CAccessibleText, "setSelectedTextRange", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;II)V");
    JNFCallStaticVoidMethod(env, jm_setSelectedTextRange, fAccessible, fComponent, startIndex, endIndex); // AWT_THREADING Safe (AWTRunLoop)
}

- (NSNumber *)accessibilityNumberOfCharactersAttribute
{
    // cmcnote: should coalesce these two calls - radr://3951923
    // also, static text doesn't always have accessibleText. if axText is null, should get the charcount of the accessibleName instead
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    jobject axText = JNFCallStaticObjectMethod(env, sjm_getAccessibleText, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    return [NSNumber numberWithInt:getAxTextCharCount(env, axText, fComponent)];
}

- (BOOL)accessibilityIsNumberOfCharactersAttributeSettable
{
    return NO; // according to NSTextViewAccessibility.m and NSCellTextAttributesAccessibility.m
}

- (NSValue *)accessibilityVisibleCharacterRangeAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getVisibleCharacterRange, sjc_CAccessibleText, "getVisibleCharacterRange", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)[I");
    jintArray axTextRange = JNFCallStaticObjectMethod(env, jm_getVisibleCharacterRange, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (axTextRange == NULL) return nil;

    return javaIntArrayToNSRangeValue(env, axTextRange);
}

- (BOOL)accessibilityIsVisibleCharacterRangeAttributeSettable
{
#ifdef JAVA_AX_DEBUG
    NSLog(@"Not yet implemented: %s\n", __FUNCTION__);
#endif
    return NO;
}

- (NSValue *)accessibilityInsertionPointLineNumberAttribute
{
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getLineNumberForInsertionPoint, sjc_CAccessibleText, "getLineNumberForInsertionPoint", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)I");
    jint row = JNFCallStaticIntMethod(env, jm_getLineNumberForInsertionPoint, fAccessible, fComponent); // AWT_THREADING Safe (AWTRunLoop)
    if (row < 0) return nil;
    return [NSNumber numberWithInt:row];
}

- (BOOL)accessibilityIsInsertionPointLineNumberAttributeSettable
{
#ifdef JAVA_AX_DEBUG
    NSLog(@"Not yet implemented: %s\n", __FUNCTION__);
#endif
    return NO;
}

// parameterized attributes

//
// Usage of accessibilityBoundsForRangeAttributeForParameter:
// ---
// called by VoiceOver when interacting with text via ctrl-option-shift-downArrow.
// Need to know bounding box for the character / word / line of interest in
// order to draw VoiceOver cursor
//
- (NSValue *)accessibilityBoundsForRangeAttributeForParameter:(id)parameter
{
#ifdef JAVA_AX_DEBUG_PARMS
    if (!([parameter isKindOfClass:[NSValue class]] && strcmp([(NSValue *)parameter objCType], @encode(NSRange)) == 0)) {
        JavaAccessibilityRaiseIllegalParameterTypeException(__FUNCTION__, self, NSAccessibilityBoundsForRangeParameterizedAttribute, parameter);
        return nil;
    }
#endif

    NSRange range = [(NSValue *)parameter rangeValue];

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getBoundsForRange, sjc_CAccessibleText, "getBoundsForRange", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;II)[D");
    jdoubleArray axBounds = JNFCallStaticObjectMethod(env, jm_getBoundsForRange, fAccessible, fComponent, range.location, range.length); // AWT_THREADING Safe (AWTRunLoop)
    if (axBounds == NULL) return nil;

    // We cheat because we know that the array is 4 elements long (x, y, width, height)
    jdouble *values = (*env)->GetDoubleArrayElements(env, axBounds, 0);
    if (values == NULL) {
        // Note: Java will not be on the stack here so a java exception can't happen and no need to call ExceptionCheck.
        NSLog(@"%s failed calling GetDoubleArrayElements", __FUNCTION__); 
        return nil;
    };
    NSRect bounds;
    bounds.origin.x = values[0];
    bounds.origin.y = [[[[self view] window] screen] frame].size.height - values[1] - values[3]; //values[1] is y-coord from top-left of screen. Flip. Account for the height (values[3]) when flipping
    bounds.size.width = values[2];
    bounds.size.height = values[3];
    NSValue *result = [NSValue valueWithRect:bounds];
    (*env)->ReleaseDoubleArrayElements(env, axBounds, values, 0);
    return result;
}

- (NSNumber *)accessibilityLineForIndexAttributeForParameter:(id)parameter
{
    NSNumber *line = (NSNumber *) parameter;
    if (line == nil) return nil;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getLineNumberForIndex, sjc_CAccessibleText, "getLineNumberForIndex", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;I)I");
    jint row = JNFCallStaticIntMethod(env, jm_getLineNumberForIndex, fAccessible, fComponent, [line intValue]); // AWT_THREADING Safe (AWTRunLoop)
    if (row < 0) return nil;
    return [NSNumber numberWithInt:row];
}

- (NSValue *)accessibilityRangeForLineAttributeForParameter:(id)parameter
{
    NSNumber *line = (NSNumber *) parameter;
    if (line == nil) return nil;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getRangeForLine, sjc_CAccessibleText, "getRangeForLine", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;I)[I");
    jintArray axTextRange = JNFCallStaticObjectMethod(env, jm_getRangeForLine, fAccessible, fComponent, [line intValue]); // AWT_THREADING Safe (AWTRunLoop)
    if (axTextRange == NULL) return nil;

    return javaIntArrayToNSRangeValue(env,axTextRange);
}

//
// Usage of accessibilityStringForRangeAttributeForParameter:
// ---
// called by VoiceOver when interacting with text via ctrl-option-shift-downArrow.
// VO needs to know the particular string its currently dealing with so it can
// speak the string
//
- (NSString *)accessibilityStringForRangeAttributeForParameter:(id)parameter
{
#ifdef JAVA_AX_DEBUG_PARMS
    if (!([parameter isKindOfClass:[NSValue class]] && strcmp([(NSValue *)parameter objCType], @encode(NSRange)) == 0)) {
        JavaAccessibilityRaiseIllegalParameterTypeException(__FUNCTION__, self, NSAccessibilityBoundsForRangeParameterizedAttribute, parameter);
        return nil;
    }
#endif

    NSRange range = [(NSValue *)parameter rangeValue];

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getStringForRange, sjc_CAccessibleText, "getStringForRange", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;II)Ljava/lang/String;");
    jstring jstringForRange = JNFCallStaticObjectMethod(env, jm_getStringForRange, fAccessible, fComponent, range.location, range.length); // AWT_THREADING Safe (AWTRunLoop)

    if (jstringForRange == NULL) return @"";
    return JNFJavaToNSString(env, jstringForRange);
}

//
// Usage of accessibilityRangeForPositionAttributeForParameter:
// ---
// cmcnote: I'm not sure when this is called / how it's used. Investigate.
// probably could be used in a special text-only accessibilityHitTest to
// find the index of the string under the mouse?
//
- (NSValue *)accessibilityRangeForPositionAttributeForParameter:(id)parameter
{
#ifdef JAVA_AX_DEBUG_PARMS
    if (!([parameter isKindOfClass:[NSValue class]] && strcmp([(NSValue *)parameter objCType], @encode(NSPoint)) == 0)) {
        JavaAccessibilityRaiseIllegalParameterTypeException(__FUNCTION__, self, NSAccessibilityRangeForPositionParameterizedAttribute, parameter);
        return nil;
    }
#endif

    NSPoint point = [(NSValue *)parameter pointValue]; // point is in screen coords
    point.y = [[[[self view] window] screen] frame].size.height - point.y; // flip into java screen coords (0 is at upper-left corner of screen)

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getCharacterIndexAtPosition, sjc_CAccessibleText, "getCharacterIndexAtPosition", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;II)I");
    jint charIndex = JNFCallStaticIntMethod(env, jm_getCharacterIndexAtPosition, fAccessible, fComponent, point.x, point.y); // AWT_THREADING Safe (AWTRunLoop)
    if (charIndex == -1) return nil;

    // AccessibleText.getIndexAtPoint returns -1 for an invalid point
    NSRange range = NSMakeRange(charIndex, 1); //range's length is 1 - one-character range
    return [NSValue valueWithRange:range];
}

//
// Usage of accessibilityRangeForIndexAttributeForParameter:
// ---
// cmcnote: I'm not sure when this is called / how it's used. Investigate.
// AppKit version calls: [string rangeOfComposedCharacterSequenceAtIndex:index]
// We call: CAccessibility.getRangeForIndex, which calls AccessibleText.getAtIndex(AccessibleText.WORD, index)
// to determine the word closest to the given index. Then we find the length/location of this string.
//
- (NSValue *)accessibilityRangeForIndexAttributeForParameter:(id)parameter
{
#ifdef JAVA_AX_DEBUG_PARMS
    if (![parameter isKindOfClass:[NSNumber class]]) {
        JavaAccessibilityRaiseIllegalParameterTypeException(__FUNCTION__, self, NSAccessibilityRangeForIndexParameterizedAttribute, parameter);
        return nil;
    }
#endif

    NSUInteger index = [(NSNumber *)parameter unsignedIntegerValue];

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_STATIC_MEMBER_CACHE(jm_getRangeForIndex, sjc_CAccessibleText, "getRangeForIndex", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;I)[I");
    jintArray axTextRange = JNFCallStaticObjectMethod(env, jm_getRangeForIndex, fAccessible, fComponent, index); // AWT_THREADING Safe (AWTRunLoop)
    if (axTextRange == NULL) return nil;

    return javaIntArrayToNSRangeValue(env, axTextRange);
}

- (NSDictionary *)getActions:(JNIEnv *)env {
    // cmcnote: this isn't correct; text can have actions. Not yet implemented. radr://3941691
    // Editable text has AXShowMenu. Textfields have AXConfirm. Static text has no actions.
#ifdef JAVA_AX_DEBUG
    NSLog(@"Not yet implemented: %s\n", __FUNCTION__);
#endif
    return nil;
}

@end
