/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifdef __linux__
#include <stdio.h>
#include <ctype.h>
#endif
#include <pwd.h>
#include <locale.h>
#ifndef ARCHPROPNAME
#error "The macro ARCHPROPNAME has not been defined"
#endif
#include <sys/utsname.h>        /* For os_name and os_version */
#include <langinfo.h>           /* For nl_langinfo */
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/param.h>
#include <time.h>
#include <errno.h>

#include "locale_str.h"
#include "java_props.h"

#ifdef __linux__
  #ifndef CODESET
  #define CODESET _NL_CTYPE_CODESET_NAME
  #endif
#else
#ifdef ALT_CODESET_KEY
#define CODESET ALT_CODESET_KEY
#endif
#endif

#ifdef JAVASE_EMBEDDED
#include <dlfcn.h>
#include <sys/stat.h>
#endif

/* Take an array of string pairs (map of key->value) and a string (key).
 * Examine each pair in the map to see if the first string (key) matches the
 * string.  If so, store the second string of the pair (value) in the value and
 * return 1.  Otherwise do nothing and return 0.  The end of the map is
 * indicated by an empty string at the start of a pair (key of "").
 */
static int
mapLookup(char* map[], const char* key, char** value) {
    int i;
    for (i = 0; strcmp(map[i], ""); i += 2){
        if (!strcmp(key, map[i])){
            *value = map[i + 1];
            return 1;
        }
    }
    return 0;
}

/* This function sets an environment variable using envstring.
 * The format of envstring is "name=value".
 * If the name has already existed, it will append value to the name.
 */
static void
setPathEnvironment(char *envstring)
{
    char name[20], *value, *current;

    value = strchr(envstring, '='); /* locate name and value separator */

    if (! value)
        return; /* not a valid environment setting */

    /* copy first part as environment name */
    strncpy(name, envstring, value - envstring);
    name[value-envstring] = '\0';

    value++; /* set value point to value of the envstring */

    current = getenv(name);
    if (current) {
        if (! strstr(current, value)) {
            /* value is not found in current environment, append it */
            char *temp = malloc(strlen(envstring) + strlen(current) + 2);
        strcpy(temp, name);
        strcat(temp, "=");
        strcat(temp, current);
        strcat(temp, ":");
        strcat(temp, value);
        putenv(temp);
        }
        /* else the value has already been set, do nothing */
    }
    else {
        /* environment variable is not found */
        putenv(envstring);
    }
}

#ifndef P_tmpdir
#define P_tmpdir "/var/tmp"
#endif

static int ParseLocale(int cat, char ** std_language, char ** std_script,
                       char ** std_country, char ** std_variant, char ** std_encoding) {
    char temp[64];
    char *language = NULL, *country = NULL, *variant = NULL,
         *encoding = NULL;
    char *p, encoding_variant[64];
    char *lc;

    /* Query the locale set for the category */
    lc = setlocale(cat, NULL);

#ifndef __linux__
    if (lc == NULL) {
        return 0;
    }

    if (cat == LC_CTYPE) {
        /*
         * Workaround for Solaris bug 4201684: Xlib doesn't like @euro
         * locales. Since we don't depend on the libc @euro behavior,
         * we just remove the qualifier.
         * On Linux, the bug doesn't occur; on the other hand, @euro
         * is needed there because it's a shortcut that also determines
         * the encoding - without it, we wouldn't get ISO-8859-15.
         * Therefore, this code section is Solaris-specific.
         */
        lc = strdup(lc);    /* keep a copy, setlocale trashes original. */
        strcpy(temp, lc);
        p = strstr(temp, "@euro");
        if (p != NULL) {
            *p = '\0';
            setlocale(LC_ALL, temp);
        }
    }
#else
    if (lc == NULL || !strcmp(lc, "C") || !strcmp(lc, "POSIX")) {
        lc = "en_US";
    }
#endif

    /*
     * locale string format in Solaris is
     * <language name>_<country name>.<encoding name>@<variant name>
     * <country name>, <encoding name>, and <variant name> are optional.
     */

    strcpy(temp, lc);

    /* Parse the language, country, encoding, and variant from the
     * locale.  Any of the elements may be missing, but they must occur
     * in the order language_country.encoding@variant, and must be
     * preceded by their delimiter (except for language).
     *
     * If the locale name (without .encoding@variant, if any) matches
     * any of the names in the locale_aliases list, map it to the
     * corresponding full locale name.  Most of the entries in the
     * locale_aliases list are locales that include a language name but
     * no country name, and this facility is used to map each language
     * to a default country if that's possible.  It's also used to map
     * the Solaris locale aliases to their proper Java locale IDs.
     */
    if ((p = strchr(temp, '.')) != NULL) {
        strcpy(encoding_variant, p); /* Copy the leading '.' */
        *p = '\0';
    } else if ((p = strchr(temp, '@')) != NULL) {
        strcpy(encoding_variant, p); /* Copy the leading '@' */
        *p = '\0';
    } else {
        *encoding_variant = '\0';
    }

    if (mapLookup(locale_aliases, temp, &p)) {
        strcpy(temp, p);
        // check the "encoding_variant" again, if any.
        if ((p = strchr(temp, '.')) != NULL) {
            strcpy(encoding_variant, p); /* Copy the leading '.' */
            *p = '\0';
        } else if ((p = strchr(temp, '@')) != NULL) {
            strcpy(encoding_variant, p); /* Copy the leading '@' */
            *p = '\0';
        }
    }

    language = temp;
    if ((country = strchr(temp, '_')) != NULL) {
        *country++ = '\0';
    }

    p = encoding_variant;
    if ((encoding = strchr(p, '.')) != NULL) {
        p[encoding++ - p] = '\0';
        p = encoding;
    }
    if ((variant = strchr(p, '@')) != NULL) {
        p[variant++ - p] = '\0';
    }

    /* Normalize the language name */
    if (std_language != NULL) {
        *std_language = "en";
        if (language != NULL && mapLookup(language_names, language, std_language) == 0) {
            *std_language = malloc(strlen(language)+1);
            strcpy(*std_language, language);
        }
    }

    /* Normalize the country name */
    if (std_country != NULL && country != NULL) {
        if (mapLookup(country_names, country, std_country) == 0) {
            *std_country = malloc(strlen(country)+1);
            strcpy(*std_country, country);
        }
    }

    /* Normalize the script and variant name.  Note that we only use
     * variants listed in the mapping array; others are ignored.
     */
    if (variant != NULL) {
        if (std_script != NULL) {
            mapLookup(script_names, variant, std_script);
        }

        if (std_variant != NULL) {
            mapLookup(variant_names, variant, std_variant);
        }
    }

    /* Normalize the encoding name.  Note that we IGNORE the string
     * 'encoding' extracted from the locale name above.  Instead, we use the
     * more reliable method of calling nl_langinfo(CODESET).  This function
     * returns an empty string if no encoding is set for the given locale
     * (e.g., the C or POSIX locales); we use the default ISO 8859-1
     * converter for such locales.
     */
    if (std_encoding != NULL) {
        /* OK, not so reliable - nl_langinfo() gives wrong answers on
         * Euro locales, in particular. */
        if (strcmp(p, "ISO8859-15") == 0)
            p = "ISO8859-15";
        else
            p = nl_langinfo(CODESET);

        /* Convert the bare "646" used on Solaris to a proper IANA name */
        if (strcmp(p, "646") == 0)
            p = "ISO646-US";

        /* return same result nl_langinfo would return for en_UK,
         * in order to use optimizations. */
        *std_encoding = (*p != '\0') ? p : "ISO8859-1";

#ifdef __linux__
        /*
         * Remap the encoding string to a different value for japanese
         * locales on linux so that customized converters are used instead
         * of the default converter for "EUC-JP". The customized converters
         * omit support for the JIS0212 encoding which is not supported by
         * the variant of "EUC-JP" encoding used on linux
         */
        if (strcmp(p, "EUC-JP") == 0) {
            *std_encoding = "EUC-JP-LINUX";
        }
#else
        if (strcmp(p,"eucJP") == 0) {
            /* For Solaris use customized vendor defined character
             * customized EUC-JP converter
             */
            *std_encoding = "eucJP-open";
        } else if (strcmp(p, "Big5") == 0 || strcmp(p, "BIG5") == 0) {
            /*
             * Remap the encoding string to Big5_Solaris which augments
             * the default converter for Solaris Big5 locales to include
             * seven additional ideographic characters beyond those included
             * in the Java "Big5" converter.
             */
            *std_encoding = "Big5_Solaris";
        } else if (strcmp(p, "Big5-HKSCS") == 0) {
            /*
             * Solaris uses HKSCS2001
             */
            *std_encoding = "Big5-HKSCS-2001";
        }
#endif
    }

    return 1;
}

#ifdef JAVASE_EMBEDDED
/* Determine the default embedded toolkit based on whether lib/xawt/
 * exists in the JRE. This can still be overridden by -Dawt.toolkit=XXX
 */
static char* getEmbeddedToolkit() {
    Dl_info dlinfo;
    char buf[MAXPATHLEN];
    int32_t len;
    char *p;
    struct stat statbuf;

    /* Get address of this library and the directory containing it. */
    dladdr((void *)getEmbeddedToolkit, &dlinfo);
    realpath((char *)dlinfo.dli_fname, buf);
    len = strlen(buf);
    p = strrchr(buf, '/');
    /* Default AWT Toolkit on Linux and Solaris is XAWT. */
    strncpy(p, "/xawt/", MAXPATHLEN-len-1);
    /* Check if it exists */
    if (stat(buf, &statbuf) == -1 && errno == ENOENT) {
        /* No - this is a reduced-headless-jre so use special HToolkit */
        return "sun.awt.HToolkit";
    }
    else {
        /* Yes - this is a headful JRE so fallback to SE defaults */
        return NULL;
    }
}
#endif

/* This function gets called very early, before VM_CALLS are setup.
 * Do not use any of the VM_CALLS entries!!!
 */
java_props_t *
GetJavaProperties(JNIEnv *env)
{
    static java_props_t sprops;
    char *v; /* tmp var */

    if (sprops.user_dir) {
        return &sprops;
    }

    /* tmp dir */
    sprops.tmp_dir = P_tmpdir;

    /* Printing properties */
    sprops.printerJob = "sun.print.PSPrinterJob";

    /* patches/service packs installed */
    sprops.patch_level = "unknown";

    /* Java 2D properties */
    sprops.graphics_env = "sun.awt.X11GraphicsEnvironment";

#ifdef JAVASE_EMBEDDED
    sprops.awt_toolkit = getEmbeddedToolkit();
    if (sprops.awt_toolkit == NULL) // default as below
#endif
    sprops.awt_toolkit = "sun.awt.X11.XToolkit";

    /* This is used only for debugging of font problems. */
    v = getenv("JAVA2D_FONTPATH");
    sprops.font_dir = v ? v : NULL;

#ifdef SI_ISALIST
    /* supported instruction sets */
    {
        char list[258];
        sysinfo(SI_ISALIST, list, sizeof(list));
        sprops.cpu_isalist = strdup(list);
    }
#else
    sprops.cpu_isalist = NULL;
#endif

    /* endianness of platform */
    {
        unsigned int endianTest = 0xff000000;
        if (((char*)(&endianTest))[0] != 0)
            sprops.cpu_endian = "big";
        else
            sprops.cpu_endian = "little";
    }

    /* os properties */
    {
        struct utsname name;
        uname(&name);
        sprops.os_name = strdup(name.sysname);
        sprops.os_version = strdup(name.release);

        sprops.os_arch = ARCHPROPNAME;

        if (getenv("GNOME_DESKTOP_SESSION_ID") != NULL) {
            sprops.desktop = "gnome";
        }
        else {
            sprops.desktop = NULL;
        }
    }

    /* Determine the language, country, variant, and encoding from the host,
     * and store these in the user.language, user.country, user.variant and
     * file.encoding system properties. */
    setlocale(LC_ALL, "");
    if (ParseLocale(LC_CTYPE,
                    &(sprops.format_language),
                    &(sprops.format_script),
                    &(sprops.format_country),
                    &(sprops.format_variant),
                    &(sprops.encoding))) {
        ParseLocale(LC_MESSAGES,
                    &(sprops.language),
                    &(sprops.script),
                    &(sprops.country),
                    &(sprops.variant),
                    NULL);
    } else {
        sprops.language = "en";
        sprops.encoding = "ISO8859-1";
    }
    sprops.display_language = sprops.language;
    sprops.display_script = sprops.script;
    sprops.display_country = sprops.country;
    sprops.display_variant = sprops.variant;
    sprops.sun_jnu_encoding = sprops.encoding;

#ifdef __linux__
#if __BYTE_ORDER == __LITTLE_ENDIAN
    sprops.unicode_encoding = "UnicodeLittle";
#else
    sprops.unicode_encoding = "UnicodeBig";
#endif
#else
    sprops.unicode_encoding = "UnicodeBig";
#endif

    /* user properties */
    {
        struct passwd *pwent = getpwuid(getuid());
        sprops.user_name = pwent ? strdup(pwent->pw_name) : "?";
        sprops.user_home = pwent ? strdup(pwent->pw_dir) : "?";
    }

    /* User TIMEZONE */
    {
        /*
         * We defer setting up timezone until it's actually necessary.
         * Refer to TimeZone.getDefault(). However, the system
         * property is necessary to be able to be set by the command
         * line interface -D. Here temporarily set a null string to
         * timezone.
         */
        tzset();        /* for compatibility */
        sprops.timezone = "";
    }

    /* Current directory */
    {
        char buf[MAXPATHLEN];
        errno = 0;
        if (getcwd(buf, sizeof(buf))  == NULL)
            JNU_ThrowByName(env, "java/lang/Error",
             "Properties init: Could not determine current working directory.");
        else
            sprops.user_dir = strdup(buf);
    }

    sprops.file_separator = "/";
    sprops.path_separator = ":";
    sprops.line_separator = "\n";

    /* Append CDE message and resource search path to NLSPATH and
     * XFILESEARCHPATH, in order to pick localized message for
     * FileSelectionDialog window (Bug 4173641).
     */
    setPathEnvironment("NLSPATH=/usr/dt/lib/nls/msg/%L/%N.cat");
    setPathEnvironment("XFILESEARCHPATH=/usr/dt/app-defaults/%L/Dt");

    return &sprops;
}

jstring
GetStringPlatform(JNIEnv *env, nchar* cstr)
{
    return JNU_NewStringPlatform(env, cstr);
}
