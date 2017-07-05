/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

#include <windows.h>
#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "sun_net_spi_DefaultProxySelector.h"

/**
 * These functions are used by the sun.net.spi.DefaultProxySelector class
 * to access some platform specific settings.
 * This is the Windows code using the registry settings.
 */

static jclass proxy_class;
static jclass isaddr_class;
static jclass ptype_class;
static jmethodID isaddr_createUnresolvedID;
static jmethodID proxy_ctrID;
static jfieldID pr_no_proxyID;
static jfieldID ptype_httpID;
static jfieldID ptype_socksID;

#define CHECK_NULL(X) { if ((X) == NULL) fprintf (stderr,"JNI errror at line %d\n", __LINE__); }


/*
 * Class:     sun_net_spi_DefaultProxySelector
 * Method:    init
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_net_spi_DefaultProxySelector_init(JNIEnv *env, jclass clazz) {
  HKEY hKey;
  LONG ret;
  jclass cls;

  /**
   * Get all the method & field IDs for later use.
   */
  CHECK_NULL(cls = (*env)->FindClass(env,"java/net/Proxy"));
  proxy_class = (*env)->NewGlobalRef(env, cls);
  CHECK_NULL(cls = (*env)->FindClass(env,"java/net/Proxy$Type"));
  ptype_class = (*env)->NewGlobalRef(env, cls);
  CHECK_NULL(cls = (*env)->FindClass(env, "java/net/InetSocketAddress"));
  isaddr_class = (*env)->NewGlobalRef(env, cls);
  proxy_ctrID = (*env)->GetMethodID(env, proxy_class, "<init>", "(Ljava/net/Proxy$Type;Ljava/net/SocketAddress;)V");
  pr_no_proxyID = (*env)->GetStaticFieldID(env, proxy_class, "NO_PROXY", "Ljava/net/Proxy;");
  ptype_httpID = (*env)->GetStaticFieldID(env, ptype_class, "HTTP", "Ljava/net/Proxy$Type;");
  ptype_socksID = (*env)->GetStaticFieldID(env, ptype_class, "SOCKS", "Ljava/net/Proxy$Type;");
  isaddr_createUnresolvedID = (*env)->GetStaticMethodID(env, isaddr_class, "createUnresolved", "(Ljava/lang/String;I)Ljava/net/InetSocketAddress;");

  /**
   * Let's see if we can find the proper Registry entry.
   */
  ret = RegOpenKeyEx(HKEY_CURRENT_USER,
                     "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                     0, KEY_READ, (PHKEY)&hKey);
  if (ret == ERROR_SUCCESS) {
    RegCloseKey(hKey);
    /**
     * It worked, we can probably rely on it then.
     */
    return JNI_TRUE;
  }

  return JNI_FALSE;
}

#define MAX_STR_LEN 1024

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
  jobject isa = NULL;
  jobject proxy = NULL;
  jobject type_proxy = NULL;
  jobject no_proxy = NULL;
  jboolean isCopy;
  HKEY hKey;
  LONG ret;
  const char* cproto;
  const char* urlhost;
  char pproto[MAX_STR_LEN];
  char regserver[MAX_STR_LEN];
  char override[MAX_STR_LEN];
  char *s, *s2;
  int pport = 0;
  int defport = 0;
  char *phost;

  /**
   * Let's opem the Registry entry. We'll check a few values in it:
   *
   * - ProxyEnable: 0 means no proxy, 1 means use the proxy
   * - ProxyServer: a string that can take 2 forms:
   *    "server[:port]"
   *    or
   *    "protocol1=server[:port][;protocol2=server[:port]]..."
   * - ProxyOverride: a string containing a list of prefixes for hostnames.
   *   e.g.: hoth;localhost;<local>
   */
  ret = RegOpenKeyEx(HKEY_CURRENT_USER,
                     "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                     0, KEY_READ, (PHKEY)&hKey);
  if (ret == ERROR_SUCCESS) {
    DWORD dwLen;
    DWORD dwProxyEnabled;
    ULONG ulType;
    dwLen = sizeof(dwProxyEnabled);

    /**
     * Let's see if the proxy settings are to be used.
     */
    ret = RegQueryValueEx(hKey, "ProxyEnable",  NULL, &ulType,
                          (LPBYTE)&dwProxyEnabled, &dwLen);
    if ((ret == ERROR_SUCCESS) && (dwProxyEnabled > 0)) {
      /*
       * Yes, ProxyEnable == 1
       */
      dwLen = sizeof(override);
      override[0] = 0;
      ret = RegQueryValueEx(hKey, "ProxyOverride", NULL, &ulType,
                            (LPBYTE)&override, &dwLen);
      dwLen = sizeof(regserver);
      regserver[0] = 0;
      ret = RegQueryValueEx(hKey, "ProxyServer",  NULL, &ulType,
                            (LPBYTE)&regserver, &dwLen);
      RegCloseKey(hKey);
      if (ret == ERROR_SUCCESS) {
        if (strlen(override) > 0) {
          /**
           * we did get ProxyServer and may have an override.
           * So let's check the override list first, by walking down the list
           * The semicolons (;) separated entries have to be matched with the
           * the beginning of the hostname.
           */
          s = strtok(override, "; ");
          urlhost = (*env)->GetStringUTFChars(env, host, &isCopy);
          while (s != NULL) {
            if (strncmp(s, urlhost, strlen(s)) == 0) {
              /**
               * the URL host name matches with one of the prefixes,
               * therefore we have to use a direct connection.
               */
              if (isCopy == JNI_TRUE)
                (*env)->ReleaseStringUTFChars(env, host, urlhost);
              goto noproxy;
            }
            s = strtok(NULL, "; ");
          }
          if (isCopy == JNI_TRUE)
            (*env)->ReleaseStringUTFChars(env, host, urlhost);
        }

        cproto = (*env)->GetStringUTFChars(env, proto, &isCopy);
        if (cproto == NULL)
          goto noproxy;

        /*
         * Set default port value & proxy type from protocol.
         */
        if ((strcmp(cproto, "http") == 0) ||
            (strcmp(cproto, "ftp") == 0) ||
            (strcmp(cproto, "gopher") == 0))
          defport = 80;
        if (strcmp(cproto, "https") == 0)
          defport = 443;
        if (strcmp(cproto, "socks") == 0) {
          defport = 1080;
          type_proxy = (*env)->GetStaticObjectField(env, ptype_class, ptype_socksID);
        } else {
          type_proxy = (*env)->GetStaticObjectField(env, ptype_class, ptype_httpID);
        }

        sprintf(pproto,"%s=", cproto);
        if (isCopy == JNI_TRUE)
          (*env)->ReleaseStringUTFChars(env, proto, cproto);
        /**
         * Let's check the protocol specific form first.
         */
        if ((s = strstr(regserver, pproto)) != NULL) {
          s += strlen(pproto);
        } else {
          /**
           * If we couldn't find *this* protocol but the string is in the
           * protocol specific format, then don't use proxy
           */
          if (strchr(regserver, '=') != NULL)
            goto noproxy;
          s = regserver;
        }
        s2 = strchr(s, ';');
        if (s2 != NULL)
          *s2 = 0;

        /**
         * Is there a port specified?
         */
        s2 = strchr(s, ':');
        if (s2 != NULL) {
          *s2 = 0;
          s2++;
          sscanf(s2, "%d", &pport);
        }
        phost = s;

        if (phost != NULL) {
          /**
           * Let's create the appropriate Proxy object then.
           */
          jstring jhost;
          if (pport == 0)
            pport = defport;
          jhost = (*env)->NewStringUTF(env, phost);
          isa = (*env)->CallStaticObjectMethod(env, isaddr_class, isaddr_createUnresolvedID, jhost, pport);
          proxy = (*env)->NewObject(env, proxy_class, proxy_ctrID, type_proxy, isa);
          return proxy;
        }
      }
    } else {
      /* ProxyEnable == 0 or Query failed      */
      /* close the handle to the registry key  */
      RegCloseKey(hKey);
    }
  }

noproxy:
  no_proxy = (*env)->GetStaticObjectField(env, proxy_class, pr_no_proxyID);
  return no_proxy;
}
