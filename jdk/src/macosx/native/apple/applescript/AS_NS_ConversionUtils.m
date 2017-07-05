/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

//
//    Most of this is adapted from Ken Ferry's KFAppleScript Additions, contributed with permission
//    http://homepage.mac.com/kenferry/software.html
//

#import "AS_NS_ConversionUtils.h"

#import <Cocoa/Cocoa.h>
#import <Carbon/Carbon.h>


@interface NSAppleEventDescriptor (JavaAppleScriptEngineAdditionsPrivate)

// just returns self.  This means that you can pass custom descriptors
// to -[NSAppleScript executeHandler:error:withParameters:].
- (NSAppleEventDescriptor *)aeDescriptorValue;

// working with primitive descriptor types
+ (id)descriptorWithInt16:(SInt16)val;
- (SInt16)int16Value;
+ (id)descriptorWithUnsignedInt32:(UInt32)val;
- (UInt32)unsignedInt32Value;
+ (id)descriptorWithFloat32:(Float32)val;
- (Float32)float32Value;
+ (id)descriptorWithFloat64:(Float64)val;
- (Float64)float64Value;
+ (id)descriptorWithLongDateTime:(LongDateTime)val;
- (LongDateTime)longDateTimeValue;


// These are the methods for converting AS objects to objective-C objects.
// -[NSAppleEventDescriptor objCObjectValue] is the general method for converting
// AS objects to ObjC objects, and is called by -[NSAppleScript executeHandler:error:withParameters:].
// It does no work itself.  It finds a handler based on the type of the descriptor and lets that
// handler object do the work.  If there is no handler type registered for a the type of a descriptor,
// the raw descriptor is returned.
//
// You can designate a handlers for descriptor types with
// +[NSAppleEventDescriptor registerConversionHandler:selector:forDescriptorTypes:].  Please note
// that this method does _not_ retain the handler object (for now anyway).  The selector should
// take a single argument, a descriptor to translate, and should return an object.  An example such
// selector is @selector(dictionaryWithAEDesc:), for which the handler object would be [NSDictionary class].
//
// A number of handlers are designated by default.  The methods and objects can be easily inferred (or check
// the implementation), but the automatically handled types are
//    typeUnicodeText,
//    typeText,
//    typeUTF8Text,
//    typeCString,
//    typeChar,
//    typeBoolean,
//    typeTrue,
//    typeFalse,
//    typeSInt16,
//    typeSInt32,
//    typeUInt32,
//    typeSInt64,
//    typeIEEE32BitFloatingPoint,
//    typeIEEE64BitFloatingPoint,
//    type128BitFloatingPoint,
//    typeAEList,
//    typeAERecord,
//    typeLongDateTime,
//    typeNull.
+ (void)registerConversionHandler:(id)anObject selector:(SEL)aSelector forDescriptorTypes:(DescType)firstType, ...;
+ (void) jaseSetUpHandlerDict;
@end

// wrap the NSAppleEventDescriptor string methods
@interface NSString (JavaAppleScriptEngineAdditions)
- (NSAppleEventDescriptor *)aeDescriptorValue;
+ (NSString *)stringWithAEDesc:(NSAppleEventDescriptor *)desc;
@end

// wrap the NSAppleEventDescriptor longDateTime methods
@interface NSDate (JavaAppleScriptEngineAdditions)
- (NSAppleEventDescriptor *)aeDescriptorValue;
+ (NSDate *)dateWithAEDesc:(NSAppleEventDescriptor *)desc;
@end

// these are fairly complicated methods, due to having to try to match up the various
// AS number types (see NSAppleEventDescriptor for the primitive number methods)
// with NSNumber variants.  For complete behavior it's best to look at the implementation.
// Some notes:
//    NSNumbers created with numberWithBool should be correctly translated to AS booleans and vice versa.
//    NSNumbers created with large integer types may have to be translated to AS doubles,
//      so be careful if checking equality (you may have to check equality within epsilon).
//    Since NSNumbers can't remember if they were created with an unsigned value,
//      [[NSNumber numberWithUnsignedChar:255] aeDescriptorValue] is going to get you an AS integer
//      with value -1.  If you really need a descriptor with an unsigned value, you'll need to do it
//      manually using the primitive methods on NSAppleEventDescriptor.  The resulting descriptor
//      can still be passed to AS with -[NSAppleScript executeHandler:error:withParameters:].
@interface NSNumber (JavaAppleScriptEngineAdditions)
- (NSAppleEventDescriptor *)aeDescriptorValue;
+ (id)numberWithAEDesc:(NSAppleEventDescriptor *)desc;
@end

// Here we're following the behavior described in the CocoaScripting release note.
//
// NSPoint -> list of two numbers: {x, y}
// NSRange -> list of two numbers: {begin offset, end offset}
// NSRect  -> list of four numbers: {left, bottom, right, top}
// NSSize  -> list of two numbers: {width, height}
@interface NSValue (JavaAppleScriptEngineAdditions)
- (NSAppleEventDescriptor *)aeDescriptorValue;
@end

// No need for ObjC -> AS conversion here, we fall through to NSObject as a collection.
// For AS -> ObjC conversion, we build an array using the primitive list methods on
// NSAppleEventDescriptor.
@interface NSArray (JavaAppleScriptEngineAdditions)
+ (NSArray *)arrayWithAEDesc:(NSAppleEventDescriptor *)desc;
@end


// Please see the CocoaScripting release note for behavior.  It's kind of complicated.
//
// methods wrap the primitive record methods on NSAppleEventDescriptor.
@interface NSDictionary (JavaAppleScriptEngineAdditions)
- (NSAppleEventDescriptor *)aeDescriptorValue;
+ (NSDictionary *)dictionaryWithAEDesc:(NSAppleEventDescriptor *)desc;
@end

// be aware that a null descriptor does not correspond to the 'null' keyword in
// AppleScript - it's more like nothing at all.  For example, the return
// from an empty handler.
@interface NSNull (JavaAppleScriptEngineAdditions)
- (NSAppleEventDescriptor *)aeDescriptorValue;
+ (NSNull *)nullWithAEDesc:(NSAppleEventDescriptor *)desc;
@end


@interface NSNumber (JavaAppleScriptEngineAdditionsPrivate)
+ (id) jaseNumberWithSignedIntP:(void *)int_p byteCount:(int)bytes;
+ (id) jaseNumberWithUnsignedIntP:(void *)int_p byteCount:(int)bytes;
+ (id) jaseNumberWithFloatP:(void *)float_p byteCount:(int)bytes;
@end


@implementation NSObject (JavaAppleScriptEngineAdditions)

- (NSAppleEventDescriptor *)aeDescriptorValue {
    // collections go to lists
    if (![self respondsToSelector:@selector(objectEnumerator)]) {
        // encode the description as a fallback - this is pretty useless, only helpful for debugging
        return [[self description] aeDescriptorValue];
    }

    NSAppleEventDescriptor *resultDesc = [NSAppleEventDescriptor listDescriptor];
    NSEnumerator *objectEnumerator = [(id)self objectEnumerator];

    unsigned int i = 1; // apple event descriptors are 1-indexed
    id currentObject;
    while((currentObject = [objectEnumerator nextObject]) != nil) {
        [resultDesc insertDescriptor:[currentObject aeDescriptorValue] atIndex:i++];
    }

    return resultDesc;
}

@end


@implementation NSArray (JavaAppleScriptEngineAdditions)

// don't need to override aeDescriptorValue, the NSObject will treat the array as a collection
+ (NSArray *)arrayWithAEDesc:(NSAppleEventDescriptor *)desc {
    NSAppleEventDescriptor *listDesc = [desc coerceToDescriptorType:typeAEList];
    NSMutableArray *resultArray = [NSMutableArray array];

    // apple event descriptors are 1-indexed
    unsigned int listCount = [listDesc numberOfItems];
    unsigned int i;
    for (i = 1; i <= listCount; i++) {
        [resultArray addObject:[[listDesc descriptorAtIndex:i] objCObjectValue]];
    }

    return resultArray;
}

@end


@implementation NSDictionary (JavaAppleScriptEngineAdditions)

- (NSAppleEventDescriptor *)aeDescriptorValue {
    NSAppleEventDescriptor *resultDesc = [NSAppleEventDescriptor recordDescriptor];
    NSMutableArray *userFields = [NSMutableArray array];
    NSArray *keys = [self allKeys];

    unsigned int keyCount = [keys count];
    unsigned int i;
    for (i = 0; i < keyCount; i++) {
        id key = [keys objectAtIndex:i];

        if ([key isKindOfClass:[NSNumber class]]) {
            [resultDesc setDescriptor:[[self objectForKey:key] aeDescriptorValue] forKeyword:[(NSNumber *)key intValue]];
        } else if ([key isKindOfClass:[NSString class]]) {
            [userFields addObject:key];
            [userFields addObject:[self objectForKey:key]];
        }
    }

    if ([userFields count] > 0) {
        [resultDesc setDescriptor:[userFields aeDescriptorValue] forKeyword:keyASUserRecordFields];
    }

    return resultDesc;
}

+ (NSDictionary *)dictionaryWithAEDesc:(NSAppleEventDescriptor *)desc {
    NSAppleEventDescriptor *recDescriptor = [desc coerceToDescriptorType:typeAERecord];
    NSMutableDictionary *resultDict = [NSMutableDictionary dictionary];

    // NSAppleEventDescriptor uses 1 indexing
    unsigned int recordCount = [recDescriptor numberOfItems];
    unsigned int recordIndex;
    for (recordIndex = 1; recordIndex <= recordCount; recordIndex++) {
        AEKeyword keyword = [recDescriptor keywordForDescriptorAtIndex:recordIndex];

        if(keyword == keyASUserRecordFields) {
            NSAppleEventDescriptor *listDescriptor = [recDescriptor descriptorAtIndex:recordIndex];

            // NSAppleEventDescriptor uses 1 indexing
            unsigned int listCount = [listDescriptor numberOfItems];
            unsigned int listIndex;
            for (listIndex = 1; listIndex <= listCount; listIndex += 2) {
                id keyObj = [[listDescriptor descriptorAtIndex:listIndex] objCObjectValue];
                id valObj = [[listDescriptor descriptorAtIndex:listIndex+1] objCObjectValue];

                [resultDict setObject:valObj forKey:keyObj];
            }
        } else {
            id keyObj = [NSNumber numberWithInt:keyword];
            id valObj = [[recDescriptor descriptorAtIndex:recordIndex] objCObjectValue];

            [resultDict setObject:valObj forKey:keyObj];
        }
    }

    return resultDict;
}

@end


@implementation NSString (JavaAppleScriptEngineAdditions)

- (NSAppleEventDescriptor *)aeDescriptorValue {
    return [NSAppleEventDescriptor descriptorWithString:self];
}

+ (NSString *)stringWithAEDesc:(NSAppleEventDescriptor *)desc {
    return [desc stringValue];
}

+ (NSString *)versionWithAEDesc:(NSAppleEventDescriptor *)desc {
    const AEDesc *aeDesc = [desc aeDesc];
    VersRec v;
    AEGetDescData(aeDesc, &v, sizeof(v));
    return [[[NSString alloc] initWithBytes:&v.shortVersion[1] length:StrLength(v.shortVersion) encoding:NSUTF8StringEncoding] autorelease];
}

@end


@implementation NSNull (JavaAppleScriptEngineAdditions)

- (NSAppleEventDescriptor *)aeDescriptorValue {
    return [NSAppleEventDescriptor nullDescriptor];
}

+ (NSNull *)nullWithAEDesc:(NSAppleEventDescriptor *)desc {
    return [NSNull null];
}

@end


@implementation NSDate (JavaAppleScriptEngineAdditions)

- (NSAppleEventDescriptor *)aeDescriptorValue {
    LongDateTime ldt;
    UCConvertCFAbsoluteTimeToLongDateTime(CFDateGetAbsoluteTime((CFDateRef)self), &ldt);
    return [NSAppleEventDescriptor descriptorWithLongDateTime:ldt];
}

+ (NSDate *)dateWithAEDesc:(NSAppleEventDescriptor *)desc {
    CFAbsoluteTime absTime;
    UCConvertLongDateTimeToCFAbsoluteTime([desc longDateTimeValue], &absTime);
    NSDate *resultDate = (NSDate *)CFDateCreate(NULL, absTime);
    return [resultDate autorelease];
}

@end



static inline int areEqualEncodings(const char *enc1, const char *enc2) {
    return (strcmp(enc1, enc2) == 0);
}

@implementation NSNumber (JavaAppleScriptEngineAdditions)

-(id)jaseDescriptorValueWithFloatP:(void *)float_p byteCount:(int)bytes {
    float floatVal;
    if (bytes < sizeof(Float32)) {
        floatVal = [self floatValue];
        float_p = &floatVal;
        bytes = sizeof(floatVal);
    }

    double doubleVal;
    if (bytes > sizeof(Float64)) {
        doubleVal = [self doubleValue];
        float_p = &doubleVal;
        bytes = sizeof(doubleVal);
    }

    if (bytes == sizeof(Float32)) {
        return [NSAppleEventDescriptor descriptorWithFloat32:*(Float32 *)float_p];
    }

    if (bytes == sizeof(Float64)) {
        return [NSAppleEventDescriptor descriptorWithFloat64:*(Float64 *)float_p];
    }

    [NSException raise:NSInvalidArgumentException
                format:@"Cannot create an NSAppleEventDescriptor for float with %d bytes of data.",  bytes];

    return nil;
}

-(id)jaseDescriptorValueWithSignedIntP:(void *)int_p byteCount:(int)bytes {
    int intVal;

    if (bytes < sizeof(SInt16)) {
        intVal = [self intValue];
        int_p = &intVal;
        bytes = sizeof(intVal);
    }

    if (bytes == sizeof(SInt16)) {
        return [NSAppleEventDescriptor descriptorWithInt16:*(SInt16 *)int_p];
    }

    if (bytes == sizeof(SInt32)) {
        return [NSAppleEventDescriptor descriptorWithInt32:*(SInt32 *)int_p];
    }

    double val = [self doubleValue];
    return [self jaseDescriptorValueWithFloatP:&val byteCount:sizeof(val)];
}

-(id)jaseDescriptorValueWithUnsignedIntP:(void *)int_p byteCount:(int)bytes {
    unsigned int uIntVal;

    if (bytes < sizeof(UInt32)) {
        uIntVal = [self unsignedIntValue];
        int_p = &uIntVal;
        bytes = sizeof(uIntVal);
    }

    if (bytes == sizeof(UInt32)) {
        return [NSAppleEventDescriptor descriptorWithUnsignedInt32:*(UInt32 *)int_p];
    }

    double val = (double)[self unsignedLongLongValue];
    return [self jaseDescriptorValueWithFloatP:&val byteCount:sizeof(val)];
}

- (NSAppleEventDescriptor *)aeDescriptorValue {
    // NSNumber is unfortunately complicated, because the applescript
    // type we should use depends on the c type that our NSNumber corresponds to

    const char *type = [self objCType];

    // convert
    if (areEqualEncodings(type, @encode(BOOL))) {
        return [NSAppleEventDescriptor descriptorWithBoolean:[self boolValue]];
    }

    if (areEqualEncodings(type, @encode(char))) {
        char val = [self charValue];
        return [self jaseDescriptorValueWithSignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(short))) {
        short val = [self shortValue];
        return [self jaseDescriptorValueWithSignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(int))) {
        int val = [self intValue];
        return [self jaseDescriptorValueWithSignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(long))) {
        long val = [self longValue];
        return [self jaseDescriptorValueWithSignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(long long))) {
        long long val = [self longLongValue];
        return [self jaseDescriptorValueWithSignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(unsigned char))) {
        unsigned char val = [self unsignedCharValue];
        return [self jaseDescriptorValueWithUnsignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(unsigned short))) {
        unsigned short val = [self unsignedShortValue];
        return [self jaseDescriptorValueWithUnsignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(unsigned int))) {
        unsigned int val = [self unsignedIntValue];
        return [self jaseDescriptorValueWithUnsignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(unsigned long))) {
        unsigned long val = [self unsignedLongValue];
        return [self jaseDescriptorValueWithUnsignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(unsigned long long))) {
        unsigned long long val = [self unsignedLongLongValue];
        return [self jaseDescriptorValueWithUnsignedIntP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(float))) {
        float val = [self floatValue];
        return [self jaseDescriptorValueWithFloatP:&val byteCount:sizeof(val)];
    }

    if (areEqualEncodings(type, @encode(double))) {
        double val = [self doubleValue];
        return [self jaseDescriptorValueWithFloatP:&val byteCount:sizeof(val)];
    }

    [NSException raise:@"jaseUnsupportedAEDescriptorConversion"
                format:@"JavaAppleScriptEngineAdditions: conversion of an NSNumber with objCType '%s' to an aeDescriptor is not supported.", type];

    return nil;
}

+ (id)numberWithAEDesc:(NSAppleEventDescriptor *)desc {
    DescType type = [desc descriptorType];

    if ((type == typeTrue) || (type == typeFalse) || (type == typeBoolean)) {
        return [NSNumber numberWithBool:[desc booleanValue]];
    }

    if (type == typeSInt16) {
        SInt16 val = [desc int16Value];
        return [NSNumber jaseNumberWithSignedIntP:&val byteCount:sizeof(val)];
    }

    if (type == typeSInt32) {
        SInt32 val = [desc int32Value];
        return [NSNumber jaseNumberWithSignedIntP:&val byteCount:sizeof(val)];
    }

    if (type == typeUInt32) {
        UInt32 val = [desc unsignedInt32Value];
        return [NSNumber jaseNumberWithUnsignedIntP:&val byteCount:sizeof(val)];
    }

    if (type == typeIEEE32BitFloatingPoint) {
        Float32 val = [desc float32Value];
        return [NSNumber jaseNumberWithFloatP:&val byteCount:sizeof(val)];
    }

    if (type == typeIEEE64BitFloatingPoint) {
        Float64 val = [desc float64Value];
        return [NSNumber jaseNumberWithFloatP:&val byteCount:sizeof(val)];
    }

    // try to coerce to 64bit floating point
    desc = [desc coerceToDescriptorType:typeIEEE64BitFloatingPoint];
    if (desc != nil) {
        Float64 val = [desc float64Value];
        return [NSNumber jaseNumberWithFloatP:&val byteCount:sizeof(val)];
    }

    [NSException raise:@"jaseUnsupportedAEDescriptorConversion"
                format:@"JavaAppleScriptEngineAdditions: conversion of an NSAppleEventDescriptor with objCType '%s' to an aeDescriptor is not supported.", type];

    return nil;
}

+ (id) jaseNumberWithSignedIntP:(void *)int_p byteCount:(int)bytes {
    if (bytes == sizeof(char)) {
        return [NSNumber numberWithChar:*(char *)int_p];
    }

    if (bytes == sizeof(short)) {
        return [NSNumber numberWithShort:*(short *)int_p];
    }

    if (bytes == sizeof(int)) {
        return [NSNumber numberWithInt:*(int *)int_p];
    }

    if (bytes == sizeof(long)) {
        return [NSNumber numberWithLong:*(long *)int_p];
    }

    if (bytes == sizeof(long long)) {
        return [NSNumber numberWithLongLong:*(long long *)int_p];
    }

    [NSException raise:NSInvalidArgumentException
                format:@"NSNumber jaseNumberWithSignedIntP:byteCount: number with %i bytes not supported.", bytes];

    return nil;
}

+ (id) jaseNumberWithUnsignedIntP:(void *)int_p byteCount:(int)bytes {
    if (bytes == sizeof(unsigned char)) {
        return [NSNumber numberWithUnsignedChar:*(unsigned char *)int_p];
    }

    if (bytes == sizeof(unsigned short)) {
        return [NSNumber numberWithUnsignedShort:*(unsigned short *)int_p];
    }

    if (bytes == sizeof(unsigned int)) {
        return [NSNumber numberWithUnsignedInt:*(unsigned int *)int_p];
    }

    if (bytes == sizeof(unsigned long)) {
        return [NSNumber numberWithUnsignedLong:*(unsigned long *)int_p];
    }

    if (bytes == sizeof(unsigned long long)) {
        return [NSNumber numberWithUnsignedLongLong:*(unsigned long long *)int_p];
    }

    [NSException raise:NSInvalidArgumentException
                format:@"NSNumber numberWithUnsignedInt:byteCount: number with %i bytes not supported.", bytes];

    return nil;
}

+ (id) jaseNumberWithFloatP:(void *)float_p byteCount:(int)bytes {
    if (bytes == sizeof(float)) {
        return [NSNumber numberWithFloat:*(float *)float_p];
    }

    if (bytes == sizeof(double)) {
        return [NSNumber numberWithFloat:*(double *)float_p];
    }

    [NSException raise:NSInvalidArgumentException
                format:@"NSNumber numberWithFloat:byteCount: floating point number with %i bytes not supported.", bytes];

    return nil;
}

@end

@implementation NSValue (JavaAppleScriptEngineAdditions)

- (NSAppleEventDescriptor *)aeDescriptorValue {
    const char *type = [self objCType];

    if (areEqualEncodings(type, @encode(NSSize))) {
        NSSize size = [self sizeValue];
        return [[NSArray arrayWithObjects:
                 [NSNumber numberWithFloat:size.width],
                 [NSNumber numberWithFloat:size.height], nil] aeDescriptorValue];
    }

    if (areEqualEncodings(type, @encode(NSPoint))) {
        NSPoint point = [self pointValue];
        return [[NSArray arrayWithObjects:
                 [NSNumber numberWithFloat:point.x],
                 [NSNumber numberWithFloat:point.y], nil] aeDescriptorValue];
    }

    if (areEqualEncodings(type, @encode(NSRange))) {
        NSRange range = [self rangeValue];
        return [[NSArray arrayWithObjects:
                 [NSNumber numberWithUnsignedInt:range.location],
                 [NSNumber numberWithUnsignedInt:range.location + range.length], nil] aeDescriptorValue];
    }

    if (areEqualEncodings(type, @encode(NSRect))) {
        NSRect rect = [self rectValue];
        return [[NSArray arrayWithObjects:
                 [NSNumber numberWithFloat:rect.origin.x],
                 [NSNumber numberWithFloat:rect.origin.y],
                 [NSNumber numberWithFloat:rect.origin.x + rect.size.width],
                 [NSNumber numberWithFloat:rect.origin.y + rect.size.height], nil] aeDescriptorValue];
    }

    [NSException raise:@"jaseUnsupportedAEDescriptorConversion"
                format:@"JavaAppleScriptEngineAdditions: conversion of an NSNumber with objCType '%s' to an aeDescriptor is not supported.", type];

    return nil;
}

@end


@implementation NSImage (JavaAppleScriptEngineAdditions)

- (NSAppleEventDescriptor *)aeDescriptorValue {
    NSData *data = [self TIFFRepresentation];
    return [NSAppleEventDescriptor descriptorWithDescriptorType:typeTIFF data:data];
}

+ (NSImage *)imageWithAEDesc:(NSAppleEventDescriptor *)desc {
    const AEDesc *d = [desc aeDesc];
    NSMutableData *data = [NSMutableData dataWithLength:AEGetDescDataSize(d)];
    AEGetDescData(d, [data mutableBytes], [data length]);
    return [[[NSImage alloc] initWithData:data] autorelease];
}

@end



@implementation NSAppleEventDescriptor (JavaAppleScriptEngineAdditions)

// we're going to leak this.  It doesn't matter much for running apps, but
// for developers it might be nice to try to dispose of it (so it would not clutter the
// output when testing for leaks)
static NSMutableDictionary *handlerDict = nil;

- (id)objCObjectValue {
    if (handlerDict == nil) [NSAppleEventDescriptor jaseSetUpHandlerDict];

    id returnObj;
    DescType type = [self descriptorType];
    NSInvocation *handlerInvocation = [handlerDict objectForKey:[NSValue valueWithBytes:&type objCType:@encode(DescType)]];
    if (handlerInvocation == nil) {
        if (type == typeType) {
            DescType subType;
            AEGetDescData([self aeDesc], &subType, sizeof(subType));
            if (subType == typeNull) return [NSNull null];
        }
        // return raw apple event descriptor if no handler is registered
        returnObj = self;
    } else {
        [handlerInvocation setArgument:&self atIndex:2];
        [handlerInvocation invoke];
        [handlerInvocation getReturnValue:&returnObj];
    }

    return returnObj;
}

// FIXME - error checking, non nil handler
+ (void)registerConversionHandler:(id)anObject selector:(SEL)aSelector forDescriptorTypes:(DescType)firstType, ... {
    if (handlerDict == nil) [NSAppleEventDescriptor jaseSetUpHandlerDict];

    NSInvocation *handlerInvocation = [NSInvocation invocationWithMethodSignature:[anObject methodSignatureForSelector:aSelector]];
    [handlerInvocation setTarget:anObject];
    [handlerInvocation setSelector:aSelector];

    DescType aType = firstType;
    va_list typesList;
    va_start(typesList, firstType);
    do {
        NSValue *type = [NSValue valueWithBytes:&aType objCType:@encode(DescType)];
        [handlerDict setObject:handlerInvocation forKey:type];
    } while((aType = va_arg(typesList, DescType)) != 0);
    va_end(typesList);
}


- (NSAppleEventDescriptor *)aeDescriptorValue {
    return self;
}

+ (id)descriptorWithInt16:(SInt16)val {
    return [NSAppleEventDescriptor descriptorWithDescriptorType:typeSInt16 bytes:&val length:sizeof(val)];
}

- (SInt16)int16Value {
    SInt16 retValue;
    [[[self coerceToDescriptorType:typeSInt16] data] getBytes:&retValue];
    return retValue;
}

+ (id)descriptorWithUnsignedInt32:(UInt32)val {
    return [NSAppleEventDescriptor descriptorWithDescriptorType:typeUInt32 bytes:&val length:sizeof(val)];
}

- (UInt32)unsignedInt32Value {
    UInt32 retValue;
    [[[self coerceToDescriptorType:typeUInt32] data] getBytes:&retValue];
    return retValue;
}


+ (id)descriptorWithFloat32:(Float32)val {
    return [NSAppleEventDescriptor descriptorWithDescriptorType:typeIEEE32BitFloatingPoint bytes:&val length:sizeof(val)];
}

- (Float32)float32Value {
    Float32 retValue;
    [[[self coerceToDescriptorType:typeIEEE32BitFloatingPoint] data] getBytes:&retValue];
    return retValue;
}


+ (id)descriptorWithFloat64:(Float64)val {
    return [NSAppleEventDescriptor descriptorWithDescriptorType:typeIEEE64BitFloatingPoint bytes:&val length:sizeof(val)];
}

- (Float64)float64Value {
    Float64 retValue;
    [[[self coerceToDescriptorType:typeIEEE64BitFloatingPoint] data] getBytes:&retValue];
    return retValue;
}

+ (id)descriptorWithLongDateTime:(LongDateTime)val {
    return [NSAppleEventDescriptor descriptorWithDescriptorType:typeLongDateTime bytes:&val length:sizeof(val)];
}

- (LongDateTime)longDateTimeValue {
    LongDateTime retValue;
    [[[self coerceToDescriptorType:typeLongDateTime] data] getBytes:&retValue];
    return retValue;
}

+ (void)jaseSetUpHandlerDict {
    handlerDict = [[NSMutableDictionary alloc] init];

    // register default handlers
    // types are culled from AEDataModel.h and AERegistry.h

    // string -> NSStrings
    [NSAppleEventDescriptor registerConversionHandler:[NSString class] selector:@selector(stringWithAEDesc:) forDescriptorTypes:
     typeUnicodeText, typeText, typeUTF8Text, typeCString, typeChar, nil];

    // number/bool -> NSNumber
    [NSAppleEventDescriptor registerConversionHandler:[NSNumber class] selector:@selector(numberWithAEDesc:) forDescriptorTypes:
     typeBoolean, typeTrue, typeFalse,
     typeSInt16, typeSInt32, typeUInt32, typeSInt64,
     typeIEEE32BitFloatingPoint, typeIEEE64BitFloatingPoint, type128BitFloatingPoint, nil];

    // list -> NSArray
    [NSAppleEventDescriptor registerConversionHandler:[NSArray class] selector:@selector(arrayWithAEDesc:) forDescriptorTypes:typeAEList, nil];

    // record -> NSDictionary
    [NSAppleEventDescriptor registerConversionHandler:[NSDictionary class] selector:@selector(dictionaryWithAEDesc:) forDescriptorTypes:typeAERecord, nil];

    // date -> NSDate
    [NSAppleEventDescriptor registerConversionHandler:[NSDate class] selector:@selector(dateWithAEDesc:) forDescriptorTypes:typeLongDateTime, nil];

    // images -> NSImage
    [NSAppleEventDescriptor registerConversionHandler:[NSImage class] selector:@selector(imageWithAEDesc:) forDescriptorTypes:
     typeTIFF, typeJPEG, typeGIF, typePict, typeIconFamily, typeIconAndMask, nil];

    // vers -> NSString
    [NSAppleEventDescriptor registerConversionHandler:[NSString class] selector:@selector(versionWithAEDesc:) forDescriptorTypes:typeVersion, nil];

    // null -> NSNull
    [NSAppleEventDescriptor registerConversionHandler:[NSNull class] selector:@selector(nullWithAEDesc:) forDescriptorTypes:typeNull, nil];
}

@end
