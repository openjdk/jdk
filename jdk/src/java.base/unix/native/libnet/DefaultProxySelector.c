/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "jni_util.h"
#include "jvm.h"
#include "jvm_md.h"
#include "jlong.h"
#include "sun_net_spi_DefaultProxySelector.h"
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#if defined(__linux__) || defined(_ALLBSD_SOURCE)
#include <string.h>
#else
#include <strings.h>
#endif

#ifndef CHECK_NULL_RETURN
#define CHECK_NULL_RETURN(x, y) if ((x) == NULL) return y;
#endif

/**
 * These functions are used by the sun.net.spi.DefaultProxySelector class
 * to access some platform specific settings.
 * This is the Solaris/Linux Gnome 2.x code using the GConf-2 library.
 * Everything is loaded dynamically so no hard link with any library exists.
 * The GConf-2 settings used are:
 * - /system/http_proxy/use_http_proxy          boolean
 * - /system/http_proxy/use_authentcation       boolean
 * - /system/http_proxy/use_same_proxy          boolean
 * - /system/http_proxy/host                    string
 * - /system/http_proxy/authentication_user     string
 * - /system/http_proxy/authentication_password string
 * - /system/http_proxy/port                    int
 * - /system/proxy/socks_host                   string
 * - /system/proxy/mode                         string
 * - /system/proxy/ftp_host                     string
 * - /system/proxy/secure_host                  string
 * - /system/proxy/socks_port                   int
 * - /system/proxy/ftp_port                     int
 * - /system/proxy/secure_port                  int
 * - /system/proxy/no_proxy_for                 list
 * - /system/proxy/gopher_host                  string
 * - /system/proxy/gopher_port                  int
 *
 * The following keys are not used in the new gnome 3
 * - /system/http_proxy/use_http_proxy
 * - /system/http_proxy/use_same_proxy
 */
typedef void* gconf_client_get_default_func();
typedef char* gconf_client_get_string_func(void *, char *, void**);
typedef int   gconf_client_get_int_func(void*, char *, void**);
typedef int   gconf_client_get_bool_func(void*, char *, void**);
typedef int   gconf_init_func(int, char**, void**);
typedef void  g_type_init_func ();
gconf_client_get_default_func* my_get_default_func = NULL;
gconf_client_get_string_func* my_get_string_func = NULL;
gconf_client_get_int_func* my_get_int_func = NULL;
gconf_client_get_bool_func* my_get_bool_func = NULL;
gconf_init_func* my_gconf_init_func = NULL;
g_type_init_func* my_g_type_init_func = NULL;


/*
 * GProxyResolver provides synchronous and asynchronous network
 * proxy resolution. It is based on GSettings, which is the standard
 * of Gnome 3, to get system settings.
 *
 * In the current implementation, GProxyResolver has a higher priority
 * than the old GConf. And we only resolve the proxy synchronously. In
 * the future, we can also do the asynchronous network proxy resolution
 * if necessary.
 *
 */
typedef struct _GProxyResolver GProxyResolver;
typedef struct _GSocketConnectable GSocketConnectable;
typedef struct GError GError;
typedef GProxyResolver* g_proxy_resolver_get_default_func();
typedef char** g_proxy_resolver_lookup_func();
typedef GSocketConnectable* g_network_address_parse_uri_func();
typedef const char* g_network_address_get_hostname_func();
typedef unsigned short g_network_address_get_port_func();
typedef void g_strfreev_func();

static g_proxy_resolver_get_default_func* g_proxy_resolver_get_default = NULL;
static g_proxy_resolver_lookup_func* g_proxy_resolver_lookup = NULL;
static g_network_address_parse_uri_func* g_network_address_parse_uri = NULL;
static g_network_address_get_hostname_func* g_network_address_get_hostname = NULL;
static g_network_address_get_port_func* g_network_address_get_port = NULL;
static g_strfreev_func* g_strfreev = NULL;


static jclass proxy_class;
static jclass isaddr_class;
static jclass ptype_class;
static jmethodID isaddr_createUnresolvedID;
static jmethodID proxy_ctrID;
static jfieldID ptype_httpID;
static jfieldID ptype_socksID;


static void* gconf_client = NULL;
static int use_gproxyResolver = 0;
static int use_gconf = 0;


static jobject createProxy(JNIEnv *env, jfieldID ptype_ID,
                           const char* phost, unsigned short pport)
{
    jobject jProxy = NULL;
    jobject type_proxy = NULL;
    type_proxy = (*env)->GetStaticObjectField(env, ptype_class, ptype_ID);
    if (type_proxy) {
        jstring jhost = NULL;
        jhost = (*env)->NewStringUTF(env, phost);
        if (jhost) {
            jobject isa = NULL;
            isa = (*env)->CallStaticObjectMethod(env, isaddr_class,
                    isaddr_createUnresolvedID, jhost, pport);
            if (isa) {
                jProxy = (*env)->NewObject(env, proxy_class, proxy_ctrID,
                                          type_proxy, isa);
            }
        }
    }
    return jProxy;
}

static int initGConf() {
    /**
     * Let's try to load GConf-2 library
     */
    if (dlopen(JNI_LIB_NAME("gconf-2"), RTLD_GLOBAL | RTLD_LAZY) != NULL ||
        dlopen(VERSIONED_JNI_LIB_NAME("gconf-2", "4"),
               RTLD_GLOBAL | RTLD_LAZY) != NULL)
    {
        /*
         * Now let's get pointer to the functions we need.
         */
        my_g_type_init_func =
                (g_type_init_func*)dlsym(RTLD_DEFAULT, "g_type_init");
        my_get_default_func =
                (gconf_client_get_default_func*)dlsym(RTLD_DEFAULT,
                        "gconf_client_get_default");

        if (my_g_type_init_func != NULL && my_get_default_func != NULL) {
            /**
             * Try to connect to GConf.
             */
            (*my_g_type_init_func)();
            gconf_client = (*my_get_default_func)();
            if (gconf_client != NULL) {
                my_get_string_func =
                        (gconf_client_get_string_func*)dlsym(RTLD_DEFAULT,
                                "gconf_client_get_string");
                my_get_int_func =
                        (gconf_client_get_int_func*)dlsym(RTLD_DEFAULT,
                                "gconf_client_get_int");
                my_get_bool_func =
                        (gconf_client_get_bool_func*)dlsym(RTLD_DEFAULT,
                                "gconf_client_get_bool");
                if (my_get_int_func != NULL && my_get_string_func != NULL &&
                        my_get_bool_func != NULL)
                {
                    /**
                     * We did get all we need. Let's enable the System Proxy Settings.
                     */
                    return 1;
                }
            }
        }
    }
    return 0;
}

static jobject getProxyByGConf(JNIEnv *env, const char* cproto,
                               const char* chost)
{
    char *phost = NULL;
    char *mode = NULL;
    int pport = 0;
    int use_proxy = 0;
    int use_same_proxy = 0;
    jobject proxy = NULL;
    jfieldID ptype_ID = ptype_httpID;

    // We only check manual proxy configurations
    mode =  (*my_get_string_func)(gconf_client, "/system/proxy/mode", NULL);
    if (mode && !strcasecmp(mode, "manual")) {
        /*
         * Even though /system/http_proxy/use_same_proxy is no longer used,
         * its value is set to false in gnome 3. So it is not harmful to check
         * it first in case jdk is used with an old gnome.
         */
        use_same_proxy = (*my_get_bool_func)(gconf_client, "/system/http_proxy/use_same_proxy", NULL);
        if (use_same_proxy) {
            phost = (*my_get_string_func)(gconf_client, "/system/http_proxy/host", NULL);
            pport = (*my_get_int_func)(gconf_client, "/system/http_proxy/port", NULL);
            use_proxy = (phost != NULL && pport != 0);
        }

        if (!use_proxy) {
            /**
             * HTTP:
             * /system/http_proxy/use_http_proxy (boolean) - it's no longer used
             * /system/http_proxy/host (string)
             * /system/http_proxy/port (integer)
             */
            if (strcasecmp(cproto, "http") == 0) {
                phost = (*my_get_string_func)(gconf_client, "/system/http_proxy/host", NULL);
                pport = (*my_get_int_func)(gconf_client, "/system/http_proxy/port", NULL);
                use_proxy = (phost != NULL && pport != 0);
            }

            /**
             * HTTPS:
             * /system/proxy/mode (string) [ "manual" means use proxy settings ]
             * /system/proxy/secure_host (string)
             * /system/proxy/secure_port (integer)
             */
            if (strcasecmp(cproto, "https") == 0) {
                phost = (*my_get_string_func)(gconf_client, "/system/proxy/secure_host", NULL);
                pport = (*my_get_int_func)(gconf_client, "/system/proxy/secure_port", NULL);
                use_proxy = (phost != NULL && pport != 0);
            }

            /**
             * FTP:
             * /system/proxy/mode (string) [ "manual" means use proxy settings ]
             * /system/proxy/ftp_host (string)
             * /system/proxy/ftp_port (integer)
             */
            if (strcasecmp(cproto, "ftp") == 0) {
                phost = (*my_get_string_func)(gconf_client, "/system/proxy/ftp_host", NULL);
                pport = (*my_get_int_func)(gconf_client, "/system/proxy/ftp_port", NULL);
                use_proxy = (phost != NULL && pport != 0);
            }

            /**
             * GOPHER:
             * /system/proxy/mode (string) [ "manual" means use proxy settings ]
             * /system/proxy/gopher_host (string)
             * /system/proxy/gopher_port (integer)
             */
            if (strcasecmp(cproto, "gopher") == 0) {
                phost = (*my_get_string_func)(gconf_client, "/system/proxy/gopher_host", NULL);
                pport = (*my_get_int_func)(gconf_client, "/system/proxy/gopher_port", NULL);
                use_proxy = (phost != NULL && pport != 0);
            }

            /**
             * SOCKS:
             * /system/proxy/mode (string) [ "manual" means use proxy settings ]
             * /system/proxy/socks_host (string)
             * /system/proxy/socks_port (integer)
             */
            if (strcasecmp(cproto, "socks") == 0) {
                phost = (*my_get_string_func)(gconf_client, "/system/proxy/socks_host", NULL);
                pport = (*my_get_int_func)(gconf_client, "/system/proxy/socks_port", NULL);
                use_proxy = (phost != NULL && pport != 0);
                if (use_proxy)
                    ptype_ID = ptype_socksID;
            }
        }
    }

    if (use_proxy) {
        jstring jhost;
        char *noproxyfor;
        char *s;

        /**
         * check for the exclude list (aka "No Proxy For" list).
         * It's a list of comma separated suffixes (e.g. domain name).
         */
        noproxyfor = (*my_get_string_func)(gconf_client, "/system/proxy/no_proxy_for", NULL);
        if (noproxyfor != NULL) {
            char *tmpbuf[512];
            s = strtok_r(noproxyfor, ", ", tmpbuf);

            while (s != NULL && strlen(s) <= strlen(chost)) {
                if (strcasecmp(chost+(strlen(chost) - strlen(s)), s) == 0) {
                    /**
                     * the URL host name matches with one of the sufixes,
                     * therefore we have to use a direct connection.
                     */
                    use_proxy = 0;
                    break;
                }
                s = strtok_r(NULL, ", ", tmpbuf);
            }
        }
        if (use_proxy)
            proxy = createProxy(env, ptype_ID, phost, pport);
    }

    return proxy;
}

static int initGProxyResolver() {
    void *gio_handle;

    gio_handle = dlopen("libgio-2.0.so", RTLD_LAZY);
    if (!gio_handle) {
        gio_handle = dlopen("libgio-2.0.so.0", RTLD_LAZY);
        if (!gio_handle) {
            return 0;
        }
    }

    my_g_type_init_func = (g_type_init_func*)dlsym(gio_handle, "g_type_init");

    g_proxy_resolver_get_default =
            (g_proxy_resolver_get_default_func*)dlsym(gio_handle,
                    "g_proxy_resolver_get_default");

    g_proxy_resolver_lookup =
            (g_proxy_resolver_lookup_func*)dlsym(gio_handle,
                    "g_proxy_resolver_lookup");

    g_network_address_parse_uri =
            (g_network_address_parse_uri_func*)dlsym(gio_handle,
                    "g_network_address_parse_uri");

    g_network_address_get_hostname =
            (g_network_address_get_hostname_func*)dlsym(gio_handle,
                    "g_network_address_get_hostname");

    g_network_address_get_port =
            (g_network_address_get_port_func*)dlsym(gio_handle,
                    "g_network_address_get_port");

    g_strfreev = (g_strfreev_func*)dlsym(gio_handle, "g_strfreev");

    if (!my_g_type_init_func ||
        !g_proxy_resolver_get_default ||
        !g_proxy_resolver_lookup ||
        !g_network_address_parse_uri ||
        !g_network_address_get_hostname ||
        !g_network_address_get_port ||
        !g_strfreev)
    {
        dlclose(gio_handle);
        return 0;
    }

    (*my_g_type_init_func)();
    return 1;
}

static jobject getProxyByGProxyResolver(JNIEnv *env, const char* cproto,
                                        const char* chost)
{
    GProxyResolver* resolver = NULL;
    char** proxies = NULL;
    GError *error = NULL;

    size_t protoLen = 0;
    size_t hostLen = 0;
    char* uri = NULL;

    jobject jProxy = NULL;

    resolver = (*g_proxy_resolver_get_default)();
    if (resolver == NULL) {
        return NULL;
    }

    // Construct the uri, cproto + "://" + chost
    protoLen = strlen(cproto);
    hostLen = strlen(chost);
    uri = malloc(protoLen + hostLen + 4);
    if (!uri) {
        // Out of memory
        return NULL;
    }
    memcpy(uri, cproto, protoLen);
    memcpy(uri + protoLen, "://", 3);
    memcpy(uri + protoLen + 3, chost, hostLen + 1);

    /*
     * Looks into the system proxy configuration to determine what proxy,
     * if any, to use to connect to uri. The returned proxy URIs are of
     * the form <protocol>://[user[:password]@]host:port or direct://,
     * where <protocol> could be http, rtsp, socks or other proxying protocol.
     * direct:// is used when no proxy is needed.
     */
    proxies = (*g_proxy_resolver_lookup)(resolver, uri, NULL, &error);
    free(uri);

    if (proxies) {
        if (!error) {
            int i;
            for(i = 0; proxies[i] && !jProxy; i++) {
                if (strcmp(proxies[i], "direct://")) {
                    GSocketConnectable* conn =
                            (*g_network_address_parse_uri)(proxies[i], 0,
                                                           &error);
                    if (conn && !error) {
                        const char* phost = NULL;
                        unsigned short pport = 0;
                        phost = (*g_network_address_get_hostname)(conn);
                        pport = (*g_network_address_get_port)(conn);
                        if (phost && pport > 0) {
                            jfieldID ptype_ID = ptype_httpID;
                            if (!strncmp(proxies[i], "socks", 5))
                                ptype_ID = ptype_socksID;

                            jProxy = createProxy(env, ptype_ID, phost, pport);
                        }
                    }
                }
            }
        }
        (*g_strfreev)(proxies);
    }

    return jProxy;
}

static int initJavaClass(JNIEnv *env) {
    jclass proxy_cls = NULL;
    jclass ptype_cls = NULL;
    jclass isaddr_cls = NULL;

    // Proxy initialization
    proxy_cls = (*env)->FindClass(env,"java/net/Proxy");
    CHECK_NULL_RETURN(proxy_cls, 0);
    proxy_class = (*env)->NewGlobalRef(env, proxy_cls);
    CHECK_NULL_RETURN(proxy_class, 0);
    proxy_ctrID = (*env)->GetMethodID(env, proxy_class, "<init>",
            "(Ljava/net/Proxy$Type;Ljava/net/SocketAddress;)V");
    CHECK_NULL_RETURN(proxy_ctrID, 0);

    // Proxy$Type initialization
    ptype_cls = (*env)->FindClass(env,"java/net/Proxy$Type");
    CHECK_NULL_RETURN(ptype_cls, 0);
    ptype_class = (*env)->NewGlobalRef(env, ptype_cls);
    CHECK_NULL_RETURN(ptype_class, 0);
    ptype_httpID = (*env)->GetStaticFieldID(env, ptype_class, "HTTP",
                                            "Ljava/net/Proxy$Type;");
    CHECK_NULL_RETURN(ptype_httpID, 0);
    ptype_socksID = (*env)->GetStaticFieldID(env, ptype_class, "SOCKS",
                                             "Ljava/net/Proxy$Type;");
    CHECK_NULL_RETURN(ptype_socksID, 0);

    // InetSocketAddress initialization
    isaddr_cls = (*env)->FindClass(env, "java/net/InetSocketAddress");
    CHECK_NULL_RETURN(isaddr_cls, 0);
    isaddr_class = (*env)->NewGlobalRef(env, isaddr_cls);
    CHECK_NULL_RETURN(isaddr_class, 0);
    isaddr_createUnresolvedID = (*env)->GetStaticMethodID(env, isaddr_class,
            "createUnresolved",
            "(Ljava/lang/String;I)Ljava/net/InetSocketAddress;");

    return isaddr_createUnresolvedID != NULL ? 1 : 0;
}


/*
 * Class:     sun_net_spi_DefaultProxySelector
 * Method:    init
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_net_spi_DefaultProxySelector_init(JNIEnv *env, jclass clazz) {
    use_gproxyResolver = initGProxyResolver();
    if (!use_gproxyResolver)
        use_gconf = initGConf();

    if (use_gproxyResolver || use_gconf) {
        if (initJavaClass(env))
            return JNI_TRUE;
    }
    return JNI_FALSE;
}

/*
 * Class:     sun_net_spi_DefaultProxySelector
 * Method:    getSystemProxy
 * Signature: ([Ljava/lang/String;Ljava/lang/String;)Ljava/net/Proxy;
 */
JNIEXPORT jobject JNICALL
Java_sun_net_spi_DefaultProxySelector_getSystemProxy(JNIEnv *env,
                                                     jobject this,
                                                     jstring proto,
                                                     jstring host)
{
    const char* cproto;
    const char* chost;

    jboolean isProtoCopy;
    jboolean isHostCopy;

    jobject proxy = NULL;

    cproto = (*env)->GetStringUTFChars(env, proto, &isProtoCopy);

    if (cproto != NULL && (use_gproxyResolver || use_gconf)) {
        chost = (*env)->GetStringUTFChars(env, host, &isHostCopy);
        if (chost != NULL) {
            if (use_gproxyResolver)
                proxy = getProxyByGProxyResolver(env, cproto, chost);
            else if (use_gconf)
                proxy = getProxyByGConf(env, cproto, chost);

            if (isHostCopy == JNI_TRUE)
                (*env)->ReleaseStringUTFChars(env, host, chost);
        }
        if (isProtoCopy == JNI_TRUE)
            (*env)->ReleaseStringUTFChars(env, proto, cproto);
    }
    return proxy;
}

