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

#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <SystemConfiguration/SystemConfiguration.h>


@interface JNFVectorCoercion : NSObject <JNFTypeCoercion> { }
@end

@implementation JNFVectorCoercion

- (jobject) coerceNSObject:(id)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    static JNF_CLASS_CACHE(jc_Vector, "java/util/Vector");
    static JNF_CTOR_CACHE(jm_Vector_ctor, jc_Vector, "(I)V");
    static JNF_MEMBER_CACHE(jm_Vector_add, jc_Vector, "add", "(Ljava/lang/Object;)Z");

    NSArray *nsArray = (NSArray *)obj;
    jobject javaArray = JNFNewObject(env, jm_Vector_ctor, (jint)[nsArray count]);

    for (id obj in nsArray) {
        jobject jobj = [coercer coerceNSObject:obj withEnv:env usingCoercer:coercer];
        JNFCallBooleanMethod(env, javaArray, jm_Vector_add, jobj);
        if (jobj != NULL) (*env)->DeleteLocalRef(env, jobj);
    }

    return javaArray;
}

- (id) coerceJavaObject:(jobject)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    return nil;
}

@end


@interface JNFHashtableCoercion : NSObject <JNFTypeCoercion> { }
@end

@implementation JNFHashtableCoercion

- (jobject) coerceNSObject:(id)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    static JNF_CLASS_CACHE(jc_Hashtable, "java/util/Hashtable");
    static JNF_CTOR_CACHE(jm_Hashtable_ctor, jc_Hashtable, "()V");
    static JNF_MEMBER_CACHE(jm_Hashtable_put, jc_Hashtable, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    NSDictionary *nsDict = (NSDictionary *)obj;
    NSEnumerator *keyEnum = [nsDict keyEnumerator];

    jobject jHashTable = JNFNewObject(env, jm_Hashtable_ctor);

    id key = nil;
    while ((key = [keyEnum nextObject]) != nil) {
        jobject jkey = [coercer coerceNSObject:key withEnv:env usingCoercer:coercer];

        id value = [nsDict objectForKey:key];
        jobject jvalue = [coercer coerceNSObject:value withEnv:env usingCoercer:coercer];

        JNFCallObjectMethod(env, jHashTable, jm_Hashtable_put, jkey, jvalue);

        if (jkey != NULL) (*env)->DeleteLocalRef(env, jkey);
        if (jvalue != NULL) (*env)->DeleteLocalRef(env, jvalue);
    }

    return jHashTable;
}

- (id) coerceJavaObject:(jobject)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    return nil;
}

@end



NSDictionary *realmConfigsForRealms(SCDynamicStoreRef store, NSArray *realms) {
    NSMutableDictionary *dict = [NSMutableDictionary dictionary];

    for (NSString *realm in realms) {
        CFTypeRef realmInfo = SCDynamicStoreCopyValue(store, (CFStringRef) [NSString stringWithFormat:@"Kerberos:%@", realm]);

        if (CFGetTypeID(realmInfo) != CFDictionaryGetTypeID()) {
            return nil;
        }

        [dict setObject:(NSArray *)realmInfo forKey:realm];
        CFRelease(realmInfo);
    }

    return dict;
}


#define KERBEROS_DEFAULT_REALMS @"Kerberos-Default-Realms"
#define KERBEROS_DEFAULT_REALM_MAPPINGS @"Kerberos-Domain-Realm-Mappings"

void _SCDynamicStoreCallBack(SCDynamicStoreRef store, CFArrayRef changedKeys, void *info) {
   NSArray *keys = (NSArray *)changedKeys;
    if ([keys count] == 0) return;
    if (![keys containsObject:KERBEROS_DEFAULT_REALMS] && ![keys containsObject:KERBEROS_DEFAULT_REALM_MAPPINGS]) return;

    JNFPerformEnvBlock(JNFThreadDetachOnThreadDeath | JNFThreadSetSystemClassLoaderOnAttach | JNFThreadAttachAsDaemon, ^(JNIEnv *env) {
        static JNF_CLASS_CACHE(jc_Config, "sun/security/krb5/Config");
        static JNF_STATIC_MEMBER_CACHE(jm_Config_refresh, jc_Config, "refresh", "()V");
        JNFCallStaticVoidMethod(env, jm_Config_refresh);
    });
}

/*
 * Class:     sun_security_krb5_SCDynamicStoreConfig
 * Method:    installNotificationCallback
 */
JNIEXPORT void JNICALL Java_sun_security_krb5_SCDynamicStoreConfig_installNotificationCallback(JNIEnv *env, jclass klass) {

JNF_COCOA_ENTER(env);

    SCDynamicStoreRef store = SCDynamicStoreCreate(NULL, CFSTR("java"), _SCDynamicStoreCallBack, NULL);
    if (store == NULL) {
        return;
    }

    NSArray *keys = [NSArray arrayWithObjects:KERBEROS_DEFAULT_REALMS, KERBEROS_DEFAULT_REALM_MAPPINGS, nil];
    SCDynamicStoreSetNotificationKeys(store, (CFArrayRef) keys, NULL);

    CFRunLoopSourceRef rls = SCDynamicStoreCreateRunLoopSource(NULL, store, 0);
    if (rls != NULL) {
        CFRunLoopAddSource(CFRunLoopGetMain(), rls, kCFRunLoopDefaultMode);
        CFRelease(rls);
    }

    CFRelease(store);

JNF_COCOA_EXIT(env);

}

/*
 * Class:     sun_security_krb5_SCDynamicStoreConfig
 * Method:    getKerberosConfig
 * Signature: ()Ljava/util/Hashtable;
 */
JNIEXPORT jobject JNICALL Java_sun_security_krb5_SCDynamicStoreConfig_getKerberosConfig(JNIEnv *env, jclass klass) {
    jobject jHashTable = NULL;

JNF_COCOA_ENTER(env);

    SCDynamicStoreRef store = SCDynamicStoreCreate(NULL, CFSTR("java-kerberos"), NULL, NULL);
    if (store == NULL) {
        return NULL;
    }

    CFTypeRef realms = SCDynamicStoreCopyValue(store, (CFStringRef) KERBEROS_DEFAULT_REALMS);
    if (realms == NULL || CFGetTypeID(realms) != CFArrayGetTypeID()) {
        if (realms) CFRelease(realms);
        CFRelease(store);
        return NULL;
    }

    CFTypeRef realmMappings = SCDynamicStoreCopyValue(store, (CFStringRef) KERBEROS_DEFAULT_REALM_MAPPINGS);

    if (realmMappings == NULL || CFGetTypeID(realmMappings) != CFArrayGetTypeID()) {
        if (realmMappings) CFRelease(realmMappings);
        CFRelease(realms);
        CFRelease(store);
        return NULL;
    }

    NSMutableDictionary *dict = [NSMutableDictionary dictionary];

    if (CFArrayGetCount(realms) > 0) {
        NSDictionary *defaultRealmsDict = [NSDictionary dictionaryWithObject:[(NSArray *)realms objectAtIndex:0] forKey:@"default_realm"];
        [dict setObject:defaultRealmsDict forKey:@"libdefaults"];

        NSDictionary *realmConfigs = realmConfigsForRealms(store, (NSArray *)realms);
        [dict setObject:realmConfigs forKey:@"realms"];
    }
    CFRelease(realms);
    CFRelease(store);

    if (CFArrayGetCount(realmMappings) > 0) {
        [dict setObject:[(NSArray *)realmMappings objectAtIndex:0] forKey:@"domain_realm"];
    }
    CFRelease(realmMappings);


    // create and load a coercer with all of the different coercions to convert each type of object
    JNFTypeCoercer *coercer = [[[JNFTypeCoercer alloc] init] autorelease];
    [JNFDefaultCoercions addStringCoercionTo:coercer];
    [JNFDefaultCoercions addNumberCoercionTo:coercer];
    [coercer addCoercion:[[[JNFHashtableCoercion alloc] init] autorelease] forNSClass:[NSDictionary class] javaClass:@"java/util/Map"];
    [coercer addCoercion:[[[JNFVectorCoercion alloc] init] autorelease] forNSClass:[NSArray class] javaClass:@"java/util/List"];

    // convert Cocoa graph to Java graph
    jHashTable = [coercer coerceNSObject:dict withEnv:env];

JNF_COCOA_EXIT(env);

    return jHashTable;
}
