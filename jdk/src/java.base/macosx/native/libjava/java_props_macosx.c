/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include <dlfcn.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <Security/AuthSession.h>
#include <CoreFoundation/CoreFoundation.h>
#include <SystemConfiguration/SystemConfiguration.h>
#include <Foundation/Foundation.h>

#include "java_props_macosx.h"


// need dlopen/dlsym trick to avoid pulling in JavaRuntimeSupport before libjava.dylib is loaded
static void *getJRSFramework() {
    static void *jrsFwk = NULL;
    if (jrsFwk == NULL) {
       jrsFwk = dlopen("/System/Library/Frameworks/JavaVM.framework/Frameworks/JavaRuntimeSupport.framework/JavaRuntimeSupport", RTLD_LAZY | RTLD_LOCAL);
    }
    return jrsFwk;
}

char *getPosixLocale(int cat) {
    char *lc = setlocale(cat, NULL);
    if ((lc == NULL) || (strcmp(lc, "C") == 0)) {
        lc = getenv("LANG");
    }
    if (lc == NULL) return NULL;
    return strdup(lc);
}

#define LOCALEIDLENGTH  128
char *getMacOSXLocale(int cat) {
    switch (cat) {
    case LC_MESSAGES:
        {
            void *jrsFwk = getJRSFramework();
            if (jrsFwk == NULL) return NULL;

            char *(*JRSCopyPrimaryLanguage)() = dlsym(jrsFwk, "JRSCopyPrimaryLanguage");
            char *primaryLanguage = JRSCopyPrimaryLanguage ? JRSCopyPrimaryLanguage() : NULL;
            if (primaryLanguage == NULL) return NULL;

            char *(*JRSCopyCanonicalLanguageForPrimaryLanguage)(char *) = dlsym(jrsFwk, "JRSCopyCanonicalLanguageForPrimaryLanguage");
            char *canonicalLanguage = JRSCopyCanonicalLanguageForPrimaryLanguage ?  JRSCopyCanonicalLanguageForPrimaryLanguage(primaryLanguage) : NULL;
            free (primaryLanguage);

            return canonicalLanguage;
        }
        break;
    default:
        {
            char localeString[LOCALEIDLENGTH];
            if (CFStringGetCString(CFLocaleGetIdentifier(CFLocaleCopyCurrent()),
                                   localeString, LOCALEIDLENGTH, CFStringGetSystemEncoding())) {
                return strdup(localeString);
            }
        }
        break;
    }

    return NULL;
}

char *setupMacOSXLocale(int cat) {
    char * ret = getMacOSXLocale(cat);

    if (cat == LC_MESSAGES && ret != NULL) {
        void *jrsFwk = getJRSFramework();
        if (jrsFwk != NULL) {
            void (*JRSSetDefaultLocalization)(char *) = dlsym(jrsFwk, "JRSSetDefaultLocalization");
            if (JRSSetDefaultLocalization) JRSSetDefaultLocalization(ret);
        }
    }

    if (ret == NULL) {
        return getPosixLocale(cat);
    } else {
        return ret;
    }
}

int isInAquaSession() {
    // environment variable to bypass the aqua session check
    char *ev = getenv("AWT_FORCE_HEADFUL");
    if (ev && (strncasecmp(ev, "true", 4) == 0)) {
        // if "true" then tell the caller we're in an Aqua session without actually checking
        return 1;
    }
    // Is the WindowServer available?
    SecuritySessionId session_id;
    SessionAttributeBits session_info;
    OSStatus status = SessionGetInfo(callerSecuritySession, &session_id, &session_info);
    if (status == noErr) {
        if (session_info & sessionHasGraphicAccess) {
            return 1;
        }
    }
    return 0;
}

void setOSNameAndVersion(java_props_t *sprops) {
    /* Don't rely on JRSCopyOSName because there's no guarantee the value will
     * remain the same, or even if the JRS functions will continue to be part of
     * Mac OS X.  So hardcode os_name, and fill in os_version if we can.
     */
    sprops->os_name = strdup("Mac OS X");

    void *jrsFwk = getJRSFramework();
    if (jrsFwk != NULL) {
        char *(*copyOSVersion)() = dlsym(jrsFwk, "JRSCopyOSVersion");
        if (copyOSVersion != NULL) {
            sprops->os_version = copyOSVersion();
            return;
        }
    }
    sprops->os_version = strdup("Unknown");
}


static Boolean getProxyInfoForProtocol(CFDictionaryRef inDict, CFStringRef inEnabledKey, CFStringRef inHostKey, CFStringRef inPortKey, CFStringRef *outProxyHost, int *ioProxyPort) {
    /* See if the proxy is enabled. */
    CFNumberRef cf_enabled = CFDictionaryGetValue(inDict, inEnabledKey);
    if (cf_enabled == NULL) {
        return false;
    }

    int isEnabled = false;
    if (!CFNumberGetValue(cf_enabled, kCFNumberIntType, &isEnabled)) {
        return isEnabled;
    }

    if (!isEnabled) return false;
    *outProxyHost = CFDictionaryGetValue(inDict, inHostKey);

    // If cf_host is null, that means the checkbox is set,
    //   but no host was entered. We'll treat that as NOT ENABLED.
    // If cf_port is null or cf_port isn't a number, that means
    //   no port number was entered. Treat this as ENABLED with the
    //   protocol's default port.
    if (*outProxyHost == NULL) {
        return false;
    }

    if (CFStringGetLength(*outProxyHost) == 0) {
        return false;
    }

    int newPort = 0;
    CFNumberRef cf_port = NULL;
    if ((cf_port = CFDictionaryGetValue(inDict, inPortKey)) != NULL &&
        CFNumberGetValue(cf_port, kCFNumberIntType, &newPort) &&
        newPort > 0) {
        *ioProxyPort = newPort;
    } else {
        // bad port or no port - leave *ioProxyPort unchanged
    }

    return true;
}

static char *createUTF8CString(const CFStringRef theString) {
    if (theString == NULL) return NULL;

    const CFIndex stringLength = CFStringGetLength(theString);
    const CFIndex bufSize = CFStringGetMaximumSizeForEncoding(stringLength, kCFStringEncodingUTF8) + 1;
    char *returnVal = (char *)malloc(bufSize);

    if (CFStringGetCString(theString, returnVal, bufSize, kCFStringEncodingUTF8)) {
        return returnVal;
    }

    free(returnVal);
    return NULL;
}

// Return TRUE if str is a syntactically valid IP address.
// Using inet_pton() instead of inet_aton() for IPv6 support.
// len is only a hint; cstr must still be nul-terminated
static int looksLikeIPAddress(char *cstr, size_t len) {
    if (len == 0  ||  (len == 1 && cstr[0] == '.')) return FALSE;

    char dst[16]; // big enough for INET6
    return (1 == inet_pton(AF_INET, cstr, dst)  ||
            1 == inet_pton(AF_INET6, cstr, dst));
}



// Convert Mac OS X proxy exception entry to Java syntax.
// See Radar #3441134 for details.
// Returns NULL if this exception should be ignored by Java.
// May generate a string with multiple exceptions separated by '|'.
static char * createConvertedException(CFStringRef cf_original) {
    // This is done with char* instead of CFString because inet_pton()
    // needs a C string.
    char *c_exception = createUTF8CString(cf_original);
    if (!c_exception) return NULL;

    int c_len = strlen(c_exception);

    // 1. sanitize exception prefix
    if (c_len >= 1  &&  0 == strncmp(c_exception, ".", 1)) {
        memmove(c_exception, c_exception+1, c_len);
        c_len -= 1;
    } else if (c_len >= 2  &&  0 == strncmp(c_exception, "*.", 2)) {
        memmove(c_exception, c_exception+2, c_len-1);
        c_len -= 2;
    }

    // 2. pre-reject other exception wildcards
    if (strchr(c_exception, '*')) {
        free(c_exception);
        return NULL;
    }

    // 3. no IP wildcarding
    if (looksLikeIPAddress(c_exception, c_len)) {
        return c_exception;
    }

    // 4. allow domain suffixes
    // c_exception is now "str\0" - change to "str|*.str\0"
    c_exception = reallocf(c_exception, c_len+3+c_len+1);
    if (!c_exception) return NULL;

    strncpy(c_exception+c_len, "|*.", 3);
    strncpy(c_exception+c_len+3, c_exception, c_len);
    c_exception[c_len+3+c_len] = '\0';
    return c_exception;
}

/*
 * Method for fetching the user.home path and storing it in the property list.
 * For signed .apps running in the Mac App Sandbox, user.home is set to the
 * app's sandbox container.
 */
void setUserHome(java_props_t *sprops) {
    if (sprops == NULL) { return; }
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    sprops->user_home = createUTF8CString((CFStringRef)NSHomeDirectory());
    [pool drain];
}

/*
 * Method for fetching proxy info and storing it in the property list.
 */
void setProxyProperties(java_props_t *sProps) {
    if (sProps == NULL) return;

    char buf[16];    /* Used for %d of an int - 16 is plenty */
    CFStringRef
    cf_httpHost = NULL,
    cf_httpsHost = NULL,
    cf_ftpHost = NULL,
    cf_socksHost = NULL,
    cf_gopherHost = NULL;
    int
    httpPort = 80, // Default proxy port values
    httpsPort = 443,
    ftpPort = 21,
    socksPort = 1080,
    gopherPort = 70;

    CFDictionaryRef dict = SCDynamicStoreCopyProxies(NULL);
    if (dict == NULL) return;

    /* Read the proxy exceptions list */
    CFArrayRef cf_list = CFDictionaryGetValue(dict, kSCPropNetProxiesExceptionsList);

    CFMutableStringRef cf_exceptionList = NULL;
    if (cf_list != NULL) {
        CFIndex len = CFArrayGetCount(cf_list), idx;

        cf_exceptionList = CFStringCreateMutable(NULL, 0);
        for (idx = (CFIndex)0; idx < len; idx++) {
            CFStringRef cf_ehost;
            if ((cf_ehost = CFArrayGetValueAtIndex(cf_list, idx))) {
                /* Convert this exception from Mac OS X syntax to Java syntax.
                 See Radar #3441134 for details. This may generate a string
                 with multiple Java exceptions separated by '|'. */
                char *c_exception = createConvertedException(cf_ehost);
                if (c_exception) {
                    /* Append the host to the list of exclusions. */
                    if (CFStringGetLength(cf_exceptionList) > 0) {
                        CFStringAppendCString(cf_exceptionList, "|", kCFStringEncodingMacRoman);
                    }
                    CFStringAppendCString(cf_exceptionList, c_exception, kCFStringEncodingMacRoman);
                    free(c_exception);
                }
            }
        }
    }

    if (cf_exceptionList != NULL) {
        if (CFStringGetLength(cf_exceptionList) > 0) {
            sProps->exceptionList = createUTF8CString(cf_exceptionList);
        }
        CFRelease(cf_exceptionList);
    }

#define CHECK_PROXY(protocol, PROTOCOL)                                     \
    sProps->protocol##ProxyEnabled =                                        \
    getProxyInfoForProtocol(dict, kSCPropNetProxies##PROTOCOL##Enable,      \
    kSCPropNetProxies##PROTOCOL##Proxy,         \
    kSCPropNetProxies##PROTOCOL##Port,          \
    &cf_##protocol##Host, &protocol##Port);     \
    if (sProps->protocol##ProxyEnabled) {                                   \
        sProps->protocol##Host = createUTF8CString(cf_##protocol##Host);    \
        snprintf(buf, sizeof(buf), "%d", protocol##Port);                   \
        sProps->protocol##Port = malloc(strlen(buf) + 1);                   \
        strcpy(sProps->protocol##Port, buf);                                \
    }

    CHECK_PROXY(http, HTTP);
    CHECK_PROXY(https, HTTPS);
    CHECK_PROXY(ftp, FTP);
    CHECK_PROXY(socks, SOCKS);
    CHECK_PROXY(gopher, Gopher);

#undef CHECK_PROXY

    CFRelease(dict);
}
