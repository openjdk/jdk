/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
#import <SystemConfiguration/SystemConfiguration.h>
#import "JNIUtilities.h"

#define KERBEROS_DEFAULT_REALMS @"Kerberos-Default-Realms"
#define KERBEROS_DEFAULT_REALM_MAPPINGS @"Kerberos-Domain-Realm-Mappings"
#define KERBEROS_REALM_INFO @"Kerberos:%@"

JavaVM *localVM;

void _SCDynamicStoreCallBack(SCDynamicStoreRef store, CFArrayRef changedKeys, void *info) {
    NSArray *keys = (NSArray *)changedKeys;
    if ([keys count] == 0) return;
    if (![keys containsObject:KERBEROS_DEFAULT_REALMS] && ![keys containsObject:KERBEROS_DEFAULT_REALM_MAPPINGS]) return;
    //    JNFPerformEnvBlock(JNFThreadDetachOnThreadDeath | JNFThreadSetSystemClassLoaderOnAttach | JNFThreadAttachAsDaemon, ^(JNIEnv *env) {
    JNIEnv *env;
    jint status = (*localVM)->GetEnv(localVM, (void**)&env, JNI_VERSION_1_2);
    if (status == JNI_EDETACHED) {
        status = (*localVM)->AttachCurrentThreadAsDaemon(localVM, (void**)&env, NULL);
    }
    if (status == 0) {
        DECLARE_CLASS(jc_Config, "sun/security/krb5/Config");
        DECLARE_STATIC_METHOD(jm_Config_refresh, jc_Config, "refresh", "()V");
        (*env)->CallStaticVoidMethod(env, jc_Config, jm_Config_refresh);
        CHECK_EXCEPTION();
    }
    (*localVM)->DetachCurrentThread(localVM);
}

/*
 * Class:     sun_security_krb5_SCDynamicStoreConfig
 * Method:    installNotificationCallback
 */
JNIEXPORT void JNICALL Java_sun_security_krb5_SCDynamicStoreConfig_installNotificationCallback(JNIEnv *env, jclass klass) {

JNI_COCOA_ENTER(env);
    (*env)->GetJavaVM(env, &localVM);
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

JNI_COCOA_EXIT(env);

}

static jobject CreateLocaleObjectFromNSString(JNIEnv *env, NSString *name)
{
    char * language = strdup([name UTF8String]);
    jobject localeObj = NULL;
    return (*env)->NewStringUTF(env, language);
}

#define ADD(s) { \
    jobject localeObj = CreateLocaleObjectFromNSString(env, s); \
    (*env)->CallBooleanMethod(env, returnValue, jm_listAdd, localeObj); \
    (*env)->DeleteLocalRef(env, localeObj); \
}

#define ADDNULL (*env)->CallBooleanMethod(env, returnValue, jm_listAdd, NULL)

/*
 * Class:     sun_security_krb5_SCDynamicStoreConfig
 * Method:    getKerberosConfig
 * Signature: ()Ljava/util/List;
 */
JNIEXPORT jobject JNICALL Java_sun_security_krb5_SCDynamicStoreConfig_getKerberosConfig(JNIEnv *env, jclass klass) {

    jobject returnValue = 0;

    SCDynamicStoreRef store = NULL;
    CFTypeRef realms = NULL;
    CFTypeRef realmMappings = NULL;
    CFTypeRef realmInfo = NULL;

    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init]; \
    @try {
        SCDynamicStoreRef store = SCDynamicStoreCreate(NULL, CFSTR("java-kerberos"), NULL, NULL);
        if (store == NULL) {
            return NULL;
        }

        CFTypeRef realms = SCDynamicStoreCopyValue(store, (CFStringRef) KERBEROS_DEFAULT_REALMS);
        if (realms == NULL || CFGetTypeID(realms) != CFArrayGetTypeID()) {
            return NULL;
        }

        // This methods returns a ArrayList<String>:
        // (realm kdc* null) null (mapping-domain mapping-realm)*
        DECLARE_CLASS_RETURN(jc_arrayListClass, "java/util/ArrayList", NULL);
        DECLARE_METHOD_RETURN(jm_arrayListCons, jc_arrayListClass, "<init>", "()V", NULL);
        DECLARE_METHOD_RETURN(jm_listAdd, jc_arrayListClass, "add", "(Ljava/lang/Object;)Z", NULL);
        returnValue = (*env)->NewObject(env, jc_arrayListClass, jm_arrayListCons);
        CHECK_EXCEPTION_NULL_RETURN(returnValue, NULL);

        for (NSString *realm in (NSArray*)realms) {
            if (realmInfo) CFRelease(realmInfo); // for the previous realm
            realmInfo = SCDynamicStoreCopyValue(store, (CFStringRef) [NSString stringWithFormat:KERBEROS_REALM_INFO, realm]);
            if (realmInfo == NULL || CFGetTypeID(realmInfo) != CFDictionaryGetTypeID()) {
                continue;
            }

            ADD(realm);
            NSDictionary* ri = (NSDictionary*)realmInfo;
            for (NSDictionary* k in (NSArray*)ri[@"kdc"]) {
                ADD(k[@"host"]);
            }

            ADDNULL;
        }
        ADDNULL;

        CFTypeRef realmMappings = SCDynamicStoreCopyValue(store, (CFStringRef) KERBEROS_DEFAULT_REALM_MAPPINGS);
        if (realmMappings != NULL && CFGetTypeID(realmMappings) == CFArrayGetTypeID()) {
            for (NSDictionary* d in (NSArray *)realmMappings) {
                for (NSString* s in d) {
                    ADD(s);
                    ADD(d[s]);
                }
            }
        }
    } @catch (NSException *e) {
        // TODO: stdout or stderr?
        NSLog(@"%@", [e callStackSymbols]);
    } @finally {
        [pool drain];
        if (realmInfo) CFRelease(realmInfo);
        if (realmMappings) CFRelease(realmMappings);
        if (realms) CFRelease(realms);
        if (store) CFRelease(store);
    }

    return returnValue;
}
