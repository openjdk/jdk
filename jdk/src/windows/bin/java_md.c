/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#include <windows.h>
#include <io.h>
#include <process.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <wtypes.h>
#include <commctrl.h>

#include <jni.h>
#include "java.h"
#include "version_comp.h"

#define JVM_DLL "jvm.dll"
#define JAVA_DLL "java.dll"

/*
 * Prototypes.
 */
static jboolean GetPublicJREHome(char *path, jint pathsize);
static jboolean GetJVMPath(const char *jrepath, const char *jvmtype,
                           char *jvmpath, jint jvmpathsize);
static jboolean GetJREPath(char *path, jint pathsize);
static void EnsureJreInstallation(const char *jrepath);

static jboolean _isjavaw = JNI_FALSE;


jboolean
IsJavaw()
{
    return _isjavaw;
}

/*
 * Returns the arch path, to get the current arch use the
 * macro GetArch, nbits here is ignored for now.
 */
const char *
GetArchPath(int nbits)
{
#ifdef _M_AMD64
    return "amd64";
#elif defined(_M_IA64)
    return "ia64";
#else
    return "i386";
#endif
}

/*
 *
 */
void
CreateExecutionEnvironment(int *_argc,
                           char ***_argv,
                           char jrepath[],
                           jint so_jrepath,
                           char jvmpath[],
                           jint so_jvmpath,
                           char **original_argv) {
    char * jvmtype;
    int i = 0;
    char** pargv = *_argv;
    int running = CURRENT_DATA_MODEL;

    int wanted = running;

    for (i = 0; i < *_argc ; i++) {
        if (JLI_StrCmp(pargv[i], "-J-d64") == 0 || JLI_StrCmp(pargv[i], "-d64") == 0) {
            wanted = 64;
            continue;
        }
        if (JLI_StrCmp(pargv[i], "-J-d32") == 0 || JLI_StrCmp(pargv[i], "-d32") == 0) {
            wanted = 32;
            continue;
        }
    }
    if (running != wanted) {
        JLI_ReportErrorMessage(JRE_ERROR2, wanted);
        exit(1);
    }

    /* Do this before we read jvm.cfg */
    EnsureJreInstallation(jrepath);

    /* Find out where the JRE is that we will be using. */
    if (!GetJREPath(jrepath, so_jrepath)) {
        JLI_ReportErrorMessage(JRE_ERROR1);
        exit(2);
    }

    /* Find the specified JVM type */
    if (ReadKnownVMs(jrepath, (char*)GetArch(), JNI_FALSE) < 1) {
        JLI_ReportErrorMessage(CFG_ERROR7);
        exit(1);
    }
    jvmtype = CheckJvmType(_argc, _argv, JNI_FALSE);

    jvmpath[0] = '\0';
    if (!GetJVMPath(jrepath, jvmtype, jvmpath, so_jvmpath)) {
        JLI_ReportErrorMessage(CFG_ERROR8, jvmtype, jvmpath);
        exit(4);
    }
    /* If we got here, jvmpath has been correctly initialized. */

}


static jboolean
LoadMSVCRT()
{
    // Only do this once
    static int loaded = 0;
    char crtpath[MAXPATHLEN];

    if (!loaded) {
        /*
         * The Microsoft C Runtime Library needs to be loaded first.  A copy is
         * assumed to be present in the "JRE path" directory.  If it is not found
         * there (or "JRE path" fails to resolve), skip the explicit load and let
         * nature take its course, which is likely to be a failure to execute.
         */
#ifdef _MSC_VER
#if _MSC_VER < 1400
#define CRT_DLL "msvcr71.dll"
#endif
#ifdef CRT_DLL
        if (GetJREPath(crtpath, MAXPATHLEN)) {
            (void)JLI_StrCat(crtpath, "\\bin\\" CRT_DLL);   /* Add crt dll */
            JLI_TraceLauncher("CRT path is %s\n", crtpath);
            if (_access(crtpath, 0) == 0) {
                if (LoadLibrary(crtpath) == 0) {
                    JLI_ReportErrorMessage(DLL_ERROR4, crtpath);
                    return JNI_FALSE;
                }
            }
        }
#endif /* CRT_DLL */
#endif /* _MSC_VER */
        loaded = 1;
    }
    return JNI_TRUE;
}

/*
 * The preJVMStart is a function in the jkernel.dll, which
 * performs the final step of synthesizing back the decomposed
 * modules  (partial install) to the full JRE. Any tool which
 * uses the  JRE must peform this step to ensure the complete synthesis.
 * The EnsureJreInstallation function calls preJVMStart based on
 * the conditions outlined below, noting that the operation
 * will fail silently if any of conditions are not met.
 * NOTE: this call must be made before jvm.dll is loaded, or jvm.cfg
 * is read, since jvm.cfg will be modified by the preJVMStart.
 * 1. Are we on a supported platform.
 * 2. Find the location of the JRE or the Kernel JRE.
 * 3. check existence of JREHOME/lib/bundles
 * 4. check jkernel.dll and invoke the entry-point
 */
typedef VOID (WINAPI *PREJVMSTART)();

static void
EnsureJreInstallation(const char* jrepath)
{
    HINSTANCE handle;
    char tmpbuf[MAXPATHLEN];
    PREJVMSTART PreJVMStart;
    struct stat s;

    /* 32 bit windows only please */
    if (strcmp(GetArch(), "i386") != 0 ) {
        return;
    }
    /* Does our bundle directory exist ? */
    strcpy(tmpbuf, jrepath);
    strcat(tmpbuf, "\\lib\\bundles");
    if (stat(tmpbuf, &s) != 0) {
        return;
    }
    /* Does our jkernel dll exist ? */
    strcpy(tmpbuf, jrepath);
    strcat(tmpbuf, "\\bin\\jkernel.dll");
    if (stat(tmpbuf, &s) != 0) {
        return;
    }
    /* The Microsoft C Runtime Library needs to be loaded first. */
    if (!LoadMSVCRT()) {
        return;
    }
    /* Load the jkernel.dll */
    if ((handle = LoadLibrary(tmpbuf)) == 0) {
        return;
    }
    /* Get the function address */
    PreJVMStart = (PREJVMSTART)GetProcAddress(handle, "preJVMStart");
    if (PreJVMStart == NULL) {
        FreeLibrary(handle);
        return;
    }
    PreJVMStart();
    FreeLibrary(handle);
    return;
}

/*
 * Find path to JRE based on .exe's location or registry settings.
 */
jboolean
GetJREPath(char *path, jint pathsize)
{
    char javadll[MAXPATHLEN];
    struct stat s;

    if (GetApplicationHome(path, pathsize)) {
        /* Is JRE co-located with the application? */
        sprintf(javadll, "%s\\bin\\" JAVA_DLL, path);
        if (stat(javadll, &s) == 0) {
            goto found;
        }

        /* Does this app ship a private JRE in <apphome>\jre directory? */
        sprintf(javadll, "%s\\jre\\bin\\" JAVA_DLL, path);
        if (stat(javadll, &s) == 0) {
            JLI_StrCat(path, "\\jre");
            goto found;
        }
    }

    /* Look for a public JRE on this machine. */
    if (GetPublicJREHome(path, pathsize)) {
        goto found;
    }

    JLI_ReportErrorMessage(JRE_ERROR8 JAVA_DLL);
    return JNI_FALSE;

 found:
    JLI_TraceLauncher("JRE path is %s\n", path);
    return JNI_TRUE;
}

/*
 * Given a JRE location and a JVM type, construct what the name the
 * JVM shared library will be.  Return true, if such a library
 * exists, false otherwise.
 */
static jboolean
GetJVMPath(const char *jrepath, const char *jvmtype,
           char *jvmpath, jint jvmpathsize)
{
    struct stat s;
    if (JLI_StrChr(jvmtype, '/') || JLI_StrChr(jvmtype, '\\')) {
        sprintf(jvmpath, "%s\\" JVM_DLL, jvmtype);
    } else {
        sprintf(jvmpath, "%s\\bin\\%s\\" JVM_DLL, jrepath, jvmtype);
    }
    if (stat(jvmpath, &s) == 0) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

/*
 * Load a jvm from "jvmpath" and initialize the invocation functions.
 */
jboolean
LoadJavaVM(const char *jvmpath, InvocationFunctions *ifn)
{
    HINSTANCE handle;

    JLI_TraceLauncher("JVM path is %s\n", jvmpath);

    /*
     * The Microsoft C Runtime Library needs to be loaded first.  A copy is
     * assumed to be present in the "JRE path" directory.  If it is not found
     * there (or "JRE path" fails to resolve), skip the explicit load and let
     * nature take its course, which is likely to be a failure to execute.
     *
     */
    LoadMSVCRT();

    /* Load the Java VM DLL */
    if ((handle = LoadLibrary(jvmpath)) == 0) {
        JLI_ReportErrorMessage(DLL_ERROR4, (char *)jvmpath);
        return JNI_FALSE;
    }

    /* Now get the function addresses */
    ifn->CreateJavaVM =
        (void *)GetProcAddress(handle, "JNI_CreateJavaVM");
    ifn->GetDefaultJavaVMInitArgs =
        (void *)GetProcAddress(handle, "JNI_GetDefaultJavaVMInitArgs");
    if (ifn->CreateJavaVM == 0 || ifn->GetDefaultJavaVMInitArgs == 0) {
        JLI_ReportErrorMessage(JNI_ERROR1, (char *)jvmpath);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * If app is "c:\foo\bin\javac", then put "c:\foo" into buf.
 */
jboolean
GetApplicationHome(char *buf, jint bufsize)
{
    char *cp;
    GetModuleFileName(0, buf, bufsize);
    *JLI_StrRChr(buf, '\\') = '\0'; /* remove .exe file name */
    if ((cp = JLI_StrRChr(buf, '\\')) == 0) {
        /* This happens if the application is in a drive root, and
         * there is no bin directory. */
        buf[0] = '\0';
        return JNI_FALSE;
    }
    *cp = '\0';  /* remove the bin\ part */
    return JNI_TRUE;
}

/*
 * Helpers to look in the registry for a public JRE.
 */
                    /* Same for 1.5.0, 1.5.1, 1.5.2 etc. */
#define JRE_KEY     "Software\\JavaSoft\\Java Runtime Environment"

static jboolean
GetStringFromRegistry(HKEY key, const char *name, char *buf, jint bufsize)
{
    DWORD type, size;

    if (RegQueryValueEx(key, name, 0, &type, 0, &size) == 0
        && type == REG_SZ
        && (size < (unsigned int)bufsize)) {
        if (RegQueryValueEx(key, name, 0, 0, buf, &size) == 0) {
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

static jboolean
GetPublicJREHome(char *buf, jint bufsize)
{
    HKEY key, subkey;
    char version[MAXPATHLEN];

    /*
     * Note: There is a very similar implementation of the following
     * registry reading code in the Windows java control panel (javacp.cpl).
     * If there are bugs here, a similar bug probably exists there.  Hence,
     * changes here require inspection there.
     */

    /* Find the current version of the JRE */
    if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, JRE_KEY, 0, KEY_READ, &key) != 0) {
        JLI_ReportErrorMessage(REG_ERROR1, JRE_KEY);
        return JNI_FALSE;
    }

    if (!GetStringFromRegistry(key, "CurrentVersion",
                               version, sizeof(version))) {
        JLI_ReportErrorMessage(REG_ERROR2, JRE_KEY);
        RegCloseKey(key);
        return JNI_FALSE;
    }

    if (JLI_StrCmp(version, GetDotVersion()) != 0) {
        JLI_ReportErrorMessage(REG_ERROR3, JRE_KEY, version, GetDotVersion()
        );
        RegCloseKey(key);
        return JNI_FALSE;
    }

    /* Find directory where the current version is installed. */
    if (RegOpenKeyEx(key, version, 0, KEY_READ, &subkey) != 0) {
        JLI_ReportErrorMessage(REG_ERROR1, JRE_KEY, version);
        RegCloseKey(key);
        return JNI_FALSE;
    }

    if (!GetStringFromRegistry(subkey, "JavaHome", buf, bufsize)) {
        JLI_ReportErrorMessage(REG_ERROR4, JRE_KEY, version);
        RegCloseKey(key);
        RegCloseKey(subkey);
        return JNI_FALSE;
    }

    if (JLI_IsTraceLauncher()) {
        char micro[MAXPATHLEN];
        if (!GetStringFromRegistry(subkey, "MicroVersion", micro,
                                   sizeof(micro))) {
            printf("Warning: Can't read MicroVersion\n");
            micro[0] = '\0';
        }
        printf("Version major.minor.micro = %s.%s\n", version, micro);
    }

    RegCloseKey(key);
    RegCloseKey(subkey);
    return JNI_TRUE;
}

/*
 * Support for doing cheap, accurate interval timing.
 */
static jboolean counterAvailable = JNI_FALSE;
static jboolean counterInitialized = JNI_FALSE;
static LARGE_INTEGER counterFrequency;

jlong CounterGet()
{
    LARGE_INTEGER count;

    if (!counterInitialized) {
        counterAvailable = QueryPerformanceFrequency(&counterFrequency);
        counterInitialized = JNI_TRUE;
    }
    if (!counterAvailable) {
        return 0;
    }
    QueryPerformanceCounter(&count);
    return (jlong)(count.QuadPart);
}

jlong Counter2Micros(jlong counts)
{
    if (!counterAvailable || !counterInitialized) {
        return 0;
    }
    return (counts * 1000 * 1000)/counterFrequency.QuadPart;
}

void
JLI_ReportErrorMessage(const char* fmt, ...) {
    va_list vl;
    va_start(vl,fmt);

    if (IsJavaw()) {
        char *message;

        /* get the length of the string we need */
        int n = _vscprintf(fmt, vl);

        message = (char *)JLI_MemAlloc(n + 1);
        _vsnprintf(message, n, fmt, vl);
        message[n]='\0';
        MessageBox(NULL, message, "Java Virtual Machine Launcher",
            (MB_OK|MB_ICONSTOP|MB_APPLMODAL));
        JLI_MemFree(message);
    } else {
        vfprintf(stderr, fmt, vl);
        fprintf(stderr, "\n");
    }
    va_end(vl);
}

/*
 * Just like JLI_ReportErrorMessage, except that it concatenates the system
 * error message if any, its upto the calling routine to correctly
 * format the separation of the messages.
 */
void
JLI_ReportErrorMessageSys(const char *fmt, ...)
{
    va_list vl;

    int save_errno = errno;
    DWORD       errval;
    jboolean freeit = JNI_FALSE;
    char  *errtext = NULL;

    va_start(vl, fmt);

    if ((errval = GetLastError()) != 0) {               /* Platform SDK / DOS Error */
        int n = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM|
            FORMAT_MESSAGE_IGNORE_INSERTS|FORMAT_MESSAGE_ALLOCATE_BUFFER,
            NULL, errval, 0, (LPTSTR)&errtext, 0, NULL);
        if (errtext == NULL || n == 0) {                /* Paranoia check */
            errtext = "";
            n = 0;
        } else {
            freeit = JNI_TRUE;
            if (n > 2) {                                /* Drop final CR, LF */
                if (errtext[n - 1] == '\n') n--;
                if (errtext[n - 1] == '\r') n--;
                errtext[n] = '\0';
            }
        }
    } else {   /* C runtime error that has no corresponding DOS error code */
        errtext = strerror(save_errno);
    }

    if (IsJavaw()) {
        char *message;
        int mlen;
        /* get the length of the string we need */
        int len = mlen =  _vscprintf(fmt, vl) + 1;
        if (freeit) {
           mlen += JLI_StrLen(errtext);
        }

        message = (char *)JLI_MemAlloc(mlen);
        _vsnprintf(message, len, fmt, vl);
        message[len]='\0';

        if (freeit) {
           JLI_StrCat(message, errtext);
        }

        MessageBox(NULL, message, "Java Virtual Machine Launcher",
            (MB_OK|MB_ICONSTOP|MB_APPLMODAL));

        JLI_MemFree(message);
    } else {
        vfprintf(stderr, fmt, vl);
        if (freeit) {
           fprintf(stderr, "%s", errtext);
        }
    }
    if (freeit) {
        (void)LocalFree((HLOCAL)errtext);
    }
    va_end(vl);
}

void  JLI_ReportExceptionDescription(JNIEnv * env) {
    if (IsJavaw()) {
       /*
        * This code should be replaced by code which opens a window with
        * the exception detail message, for now atleast put a dialog up.
        */
        MessageBox(NULL, "A Java Exception has occurred.", "Java Virtual Machine Launcher",
               (MB_OK|MB_ICONSTOP|MB_APPLMODAL));
    } else {
        (*env)->ExceptionDescribe(env);
    }
}

jboolean
ServerClassMachine() {
    return (GetErgoPolicy() == ALWAYS_SERVER_CLASS) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Determine if there is an acceptable JRE in the registry directory top_key.
 * Upon locating the "best" one, return a fully qualified path to it.
 * "Best" is defined as the most advanced JRE meeting the constraints
 * contained in the manifest_info. If no JRE in this directory meets the
 * constraints, return NULL.
 *
 * It doesn't matter if we get an error reading the registry, or we just
 * don't find anything interesting in the directory.  We just return NULL
 * in either case.
 */
static char *
ProcessDir(manifest_info* info, HKEY top_key) {
    DWORD   index = 0;
    HKEY    ver_key;
    char    name[MAXNAMELEN];
    int     len;
    char    *best = NULL;

    /*
     * Enumerate "<top_key>/SOFTWARE/JavaSoft/Java Runtime Environment"
     * searching for the best available version.
     */
    while (RegEnumKey(top_key, index, name, MAXNAMELEN) == ERROR_SUCCESS) {
        index++;
        if (JLI_AcceptableRelease(name, info->jre_version))
            if ((best == NULL) || (JLI_ExactVersionId(name, best) > 0)) {
                if (best != NULL)
                    JLI_MemFree(best);
                best = JLI_StringDup(name);
            }
    }

    /*
     * Extract "JavaHome" from the "best" registry directory and return
     * that path.  If no appropriate version was located, or there is an
     * error in extracting the "JavaHome" string, return null.
     */
    if (best == NULL)
        return (NULL);
    else {
        if (RegOpenKeyEx(top_key, best, 0, KEY_READ, &ver_key)
          != ERROR_SUCCESS) {
            JLI_MemFree(best);
            if (ver_key != NULL)
                RegCloseKey(ver_key);
            return (NULL);
        }
        JLI_MemFree(best);
        len = MAXNAMELEN;
        if (RegQueryValueEx(ver_key, "JavaHome", NULL, NULL, (LPBYTE)name, &len)
          != ERROR_SUCCESS) {
            if (ver_key != NULL)
                RegCloseKey(ver_key);
            return (NULL);
        }
        if (ver_key != NULL)
            RegCloseKey(ver_key);
        return (JLI_StringDup(name));
    }
}

/*
 * This is the global entry point. It examines the host for the optimal
 * JRE to be used by scanning a set of registry entries.  This set of entries
 * is hardwired on Windows as "Software\JavaSoft\Java Runtime Environment"
 * under the set of roots "{ HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE }".
 *
 * This routine simply opens each of these registry directories before passing
 * control onto ProcessDir().
 */
char *
LocateJRE(manifest_info* info) {
    HKEY    key = NULL;
    char    *path;
    int     key_index;
    HKEY    root_keys[2] = { HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE };

    for (key_index = 0; key_index <= 1; key_index++) {
        if (RegOpenKeyEx(root_keys[key_index], JRE_KEY, 0, KEY_READ, &key)
          == ERROR_SUCCESS)
            if ((path = ProcessDir(info, key)) != NULL) {
                if (key != NULL)
                    RegCloseKey(key);
                return (path);
            }
        if (key != NULL)
            RegCloseKey(key);
    }
    return NULL;
}

/*
 * Local helper routine to isolate a single token (option or argument)
 * from the command line.
 *
 * This routine accepts a pointer to a character pointer.  The first
 * token (as defined by MSDN command-line argument syntax) is isolated
 * from that string.
 *
 * Upon return, the input character pointer pointed to by the parameter s
 * is updated to point to the remainding, unscanned, portion of the string,
 * or to a null character if the entire string has been consummed.
 *
 * This function returns a pointer to a null-terminated string which
 * contains the isolated first token, or to the null character if no
 * token could be isolated.
 *
 * Note the side effect of modifying the input string s by the insertion
 * of a null character, making it two strings.
 *
 * See "Parsing C Command-Line Arguments" in the MSDN Library for the
 * parsing rule details.  The rule summary from that specification is:
 *
 *  * Arguments are delimited by white space, which is either a space or a tab.
 *
 *  * A string surrounded by double quotation marks is interpreted as a single
 *    argument, regardless of white space contained within. A quoted string can
 *    be embedded in an argument. Note that the caret (^) is not recognized as
 *    an escape character or delimiter.
 *
 *  * A double quotation mark preceded by a backslash, \", is interpreted as a
 *    literal double quotation mark (").
 *
 *  * Backslashes are interpreted literally, unless they immediately precede a
 *    double quotation mark.
 *
 *  * If an even number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\), and the double quotation mark (") is interpreted as a
 *    string delimiter.
 *
 *  * If an odd number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\) and the double quotation mark is interpreted as an
 *    escape sequence by the remaining backslash, causing a literal double
 *    quotation mark (") to be placed in argv.
 */
static char*
nextarg(char** s) {
    char    *p = *s;
    char    *head;
    int     slashes = 0;
    int     inquote = 0;

    /*
     * Strip leading whitespace, which MSDN defines as only space or tab.
     * (Hence, no locale specific "isspace" here.)
     */
    while (*p != (char)0 && (*p == ' ' || *p == '\t'))
        p++;
    head = p;                   /* Save the start of the token to return */

    /*
     * Isolate a token from the command line.
     */
    while (*p != (char)0 && (inquote || !(*p == ' ' || *p == '\t'))) {
        if (*p == '\\' && *(p+1) == '"' && slashes % 2 == 0)
            p++;
        else if (*p == '"')
            inquote = !inquote;
        slashes = (*p++ == '\\') ? slashes + 1 : 0;
    }

    /*
     * If the token isolated isn't already terminated in a "char zero",
     * then replace the whitespace character with one and move to the
     * next character.
     */
    if (*p != (char)0)
        *p++ = (char)0;

    /*
     * Update the parameter to point to the head of the remaining string
     * reflecting the command line and return a pointer to the leading
     * token which was isolated from the command line.
     */
    *s = p;
    return (head);
}

/*
 * Local helper routine to return a string equivalent to the input string
 * s, but with quotes removed so the result is a string as would be found
 * in argv[].  The returned string should be freed by a call to JLI_MemFree().
 *
 * The rules for quoting (and escaped quotes) are:
 *
 *  1 A double quotation mark preceded by a backslash, \", is interpreted as a
 *    literal double quotation mark (").
 *
 *  2 Backslashes are interpreted literally, unless they immediately precede a
 *    double quotation mark.
 *
 *  3 If an even number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\), and the double quotation mark (") is interpreted as a
 *    string delimiter.
 *
 *  4 If an odd number of backslashes is followed by a double quotation mark,
 *    then one backslash (\) is placed in the argv array for every pair of
 *    backslashes (\\) and the double quotation mark is interpreted as an
 *    escape sequence by the remaining backslash, causing a literal double
 *    quotation mark (") to be placed in argv.
 */
static char*
unquote(const char *s) {
    const char *p = s;          /* Pointer to the tail of the original string */
    char *un = (char*)JLI_MemAlloc(JLI_StrLen(s) + 1);  /* Ptr to unquoted string */
    char *pun = un;             /* Pointer to the tail of the unquoted string */

    while (*p != '\0') {
        if (*p == '"') {
            p++;
        } else if (*p == '\\') {
            const char *q = p + JLI_StrSpn(p,"\\");
            if (*q == '"')
                do {
                    *pun++ = '\\';
                    p += 2;
                 } while (*p == '\\' && p < q);
            else
                while (p < q)
                    *pun++ = *p++;
        } else {
            *pun++ = *p++;
        }
    }
    *pun = '\0';
    return un;
}

/*
 * Given a path to a jre to execute, this routine checks if this process
 * is indeed that jre.  If not, it exec's that jre.
 *
 * We want to actually check the paths rather than just the version string
 * built into the executable, so that given version specification will yield
 * the exact same Java environment, regardless of the version of the arbitrary
 * launcher we start with.
 */
void
ExecJRE(char *jre, char **argv) {
    int     len;
    char    path[MAXPATHLEN + 1];

    const char *progname = GetProgramName();

    /*
     * Resolve the real path to the currently running launcher.
     */
    len = GetModuleFileName(NULL, path, MAXPATHLEN + 1);
    if (len == 0 || len > MAXPATHLEN) {
        JLI_ReportErrorMessageSys(JRE_ERROR9, progname);
        exit(1);
    }

    JLI_TraceLauncher("ExecJRE: old: %s\n", path);
    JLI_TraceLauncher("ExecJRE: new: %s\n", jre);

    /*
     * If the path to the selected JRE directory is a match to the initial
     * portion of the path to the currently executing JRE, we have a winner!
     * If so, just return.
     */
    if (JLI_StrNCaseCmp(jre, path, JLI_StrLen(jre)) == 0)
        return;                 /* I am the droid you were looking for */

    /*
     * If this isn't the selected version, exec the selected version.
     */
    (void)JLI_StrCat(JLI_StrCat(JLI_StrCpy(path, jre), "\\bin\\"), progname);
    (void)JLI_StrCat(path, ".exe");

    /*
     * Although Windows has an execv() entrypoint, it doesn't actually
     * overlay a process: it can only create a new process and terminate
     * the old process.  Therefore, any processes waiting on the initial
     * process wake up and they shouldn't.  Hence, a chain of pseudo-zombie
     * processes must be retained to maintain the proper wait semantics.
     * Fortunately the image size of the launcher isn't too large at this
     * time.
     *
     * If it weren't for this semantic flaw, the code below would be ...
     *
     *     execv(path, argv);
     *     JLI_ReportErrorMessage("Error: Exec of %s failed\n", path);
     *     exit(1);
     *
     * The incorrect exec semantics could be addressed by:
     *
     *     exit((int)spawnv(_P_WAIT, path, argv));
     *
     * Unfortunately, a bug in Windows spawn/exec impementation prevents
     * this from completely working.  All the Windows POSIX process creation
     * interfaces are implemented as wrappers around the native Windows
     * function CreateProcess().  CreateProcess() takes a single string
     * to specify command line options and arguments, so the POSIX routine
     * wrappers build a single string from the argv[] array and in the
     * process, any quoting information is lost.
     *
     * The solution to this to get the original command line, to process it
     * to remove the new multiple JRE options (if any) as was done for argv
     * in the common SelectVersion() routine and finally to pass it directly
     * to the native CreateProcess() Windows process control interface.
     */
    {
        char    *cmdline;
        char    *p;
        char    *np;
        char    *ocl;
        char    *ccl;
        char    *unquoted;
        DWORD   exitCode;
        STARTUPINFO si;
        PROCESS_INFORMATION pi;

        /*
         * The following code block gets and processes the original command
         * line, replacing the argv[0] equivalent in the command line with
         * the path to the new executable and removing the appropriate
         * Multiple JRE support options. Note that similar logic exists
         * in the platform independent SelectVersion routine, but is
         * replicated here due to the syntax of CreateProcess().
         *
         * The magic "+ 4" characters added to the command line length are
         * 2 possible quotes around the path (argv[0]), a space after the
         * path and a terminating null character.
         */
        ocl = GetCommandLine();
        np = ccl = JLI_StringDup(ocl);
        p = nextarg(&np);               /* Discard argv[0] */
        cmdline = (char *)JLI_MemAlloc(JLI_StrLen(path) + JLI_StrLen(np) + 4);
        if (JLI_StrChr(path, (int)' ') == NULL && JLI_StrChr(path, (int)'\t') == NULL)
            cmdline = JLI_StrCpy(cmdline, path);
        else
            cmdline = JLI_StrCat(JLI_StrCat(JLI_StrCpy(cmdline, "\""), path), "\"");

        while (*np != (char)0) {                /* While more command-line */
            p = nextarg(&np);
            if (*p != (char)0) {                /* If a token was isolated */
                unquoted = unquote(p);
                if (*unquoted == '-') {         /* Looks like an option */
                    if (JLI_StrCmp(unquoted, "-classpath") == 0 ||
                      JLI_StrCmp(unquoted, "-cp") == 0) {       /* Unique cp syntax */
                        cmdline = JLI_StrCat(JLI_StrCat(cmdline, " "), p);
                        p = nextarg(&np);
                        if (*p != (char)0)      /* If a token was isolated */
                            cmdline = JLI_StrCat(JLI_StrCat(cmdline, " "), p);
                    } else if (JLI_StrNCmp(unquoted, "-version:", 9) != 0 &&
                      JLI_StrCmp(unquoted, "-jre-restrict-search") != 0 &&
                      JLI_StrCmp(unquoted, "-no-jre-restrict-search") != 0) {
                        cmdline = JLI_StrCat(JLI_StrCat(cmdline, " "), p);
                    }
                } else {                        /* End of options */
                    cmdline = JLI_StrCat(JLI_StrCat(cmdline, " "), p);
                    cmdline = JLI_StrCat(JLI_StrCat(cmdline, " "), np);
                    JLI_MemFree((void *)unquoted);
                    break;
                }
                JLI_MemFree((void *)unquoted);
            }
        }
        JLI_MemFree((void *)ccl);

        if (JLI_IsTraceLauncher()) {
            np = ccl = JLI_StringDup(cmdline);
            p = nextarg(&np);
            printf("ReExec Command: %s (%s)\n", path, p);
            printf("ReExec Args: %s\n", np);
            JLI_MemFree((void *)ccl);
        }
        (void)fflush(stdout);
        (void)fflush(stderr);

        /*
         * The following code is modeled after a model presented in the
         * Microsoft Technical Article "Moving Unix Applications to
         * Windows NT" (March 6, 1994) and "Creating Processes" on MSDN
         * (Februrary 2005).  It approximates UNIX spawn semantics with
         * the parent waiting for termination of the child.
         */
        memset(&si, 0, sizeof(si));
        si.cb =sizeof(STARTUPINFO);
        memset(&pi, 0, sizeof(pi));

        if (!CreateProcess((LPCTSTR)path,       /* executable name */
          (LPTSTR)cmdline,                      /* command line */
          (LPSECURITY_ATTRIBUTES)NULL,          /* process security attr. */
          (LPSECURITY_ATTRIBUTES)NULL,          /* thread security attr. */
          (BOOL)TRUE,                           /* inherits system handles */
          (DWORD)0,                             /* creation flags */
          (LPVOID)NULL,                         /* environment block */
          (LPCTSTR)NULL,                        /* current directory */
          (LPSTARTUPINFO)&si,                   /* (in) startup information */
          (LPPROCESS_INFORMATION)&pi)) {        /* (out) process information */
            JLI_ReportErrorMessageSys(SYS_ERROR1, path);
            exit(1);
        }

        if (WaitForSingleObject(pi.hProcess, INFINITE) != WAIT_FAILED) {
            if (GetExitCodeProcess(pi.hProcess, &exitCode) == FALSE)
                exitCode = 1;
        } else {
            JLI_ReportErrorMessage(SYS_ERROR2);
            exitCode = 1;
        }

        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);

        exit(exitCode);
    }

}

/*
 * Wrapper for platform dependent unsetenv function.
 */
int
UnsetEnv(char *name)
{
    int ret;
    char *buf = JLI_MemAlloc(JLI_StrLen(name) + 2);
    buf = JLI_StrCat(JLI_StrCpy(buf, name), "=");
    ret = _putenv(buf);
    JLI_MemFree(buf);
    return (ret);
}

/* --- Splash Screen shared library support --- */

static const char* SPLASHSCREEN_SO = "\\bin\\splashscreen.dll";

static HMODULE hSplashLib = NULL;

void* SplashProcAddress(const char* name) {
    char libraryPath[MAXPATHLEN]; /* some extra space for JLI_StrCat'ing SPLASHSCREEN_SO */

    if (!GetJREPath(libraryPath, MAXPATHLEN)) {
        return NULL;
    }
    if (JLI_StrLen(libraryPath)+JLI_StrLen(SPLASHSCREEN_SO) >= MAXPATHLEN) {
        return NULL;
    }
    JLI_StrCat(libraryPath, SPLASHSCREEN_SO);

    if (!hSplashLib) {
        hSplashLib = LoadLibrary(libraryPath);
    }
    if (hSplashLib) {
        return GetProcAddress(hSplashLib, name);
    } else {
        return NULL;
    }
}

void SplashFreeLibrary() {
    if (hSplashLib) {
        FreeLibrary(hSplashLib);
        hSplashLib = NULL;
    }
}

const char *
jlong_format_specifier() {
    return "%I64d";
}

/*
 * Block current thread and continue execution in a new thread
 */
int
ContinueInNewThread0(int (JNICALL *continuation)(void *), jlong stack_size, void * args) {
    int rslt = 0;
    unsigned thread_id;

#ifndef STACK_SIZE_PARAM_IS_A_RESERVATION
#define STACK_SIZE_PARAM_IS_A_RESERVATION  (0x10000)
#endif

    /*
     * STACK_SIZE_PARAM_IS_A_RESERVATION is what we want, but it's not
     * supported on older version of Windows. Try first with the flag; and
     * if that fails try again without the flag. See MSDN document or HotSpot
     * source (os_win32.cpp) for details.
     */
    HANDLE thread_handle =
      (HANDLE)_beginthreadex(NULL,
                             (unsigned)stack_size,
                             continuation,
                             args,
                             STACK_SIZE_PARAM_IS_A_RESERVATION,
                             &thread_id);
    if (thread_handle == NULL) {
      thread_handle =
      (HANDLE)_beginthreadex(NULL,
                             (unsigned)stack_size,
                             continuation,
                             args,
                             0,
                             &thread_id);
    }
    if (thread_handle) {
      WaitForSingleObject(thread_handle, INFINITE);
      GetExitCodeThread(thread_handle, &rslt);
      CloseHandle(thread_handle);
    } else {
      rslt = continuation(args);
    }
    return rslt;
}

/* Unix only, empty on windows. */
void SetJavaLauncherPlatformProps() {}

/*
 * The implementation for finding classes from the bootstrap
 * class loader, refer to java.h
 */
static FindClassFromBootLoader_t *findBootClass = NULL;

#ifdef _M_AMD64
#define JVM_BCLOADER "JVM_FindClassFromClassLoader"
#else
#define JVM_BCLOADER "_JVM_FindClassFromClassLoader@20"
#endif /* _M_AMD64 */

jclass FindBootStrapClass(JNIEnv *env, const char *classname)
{
   HMODULE hJvm;

   if (findBootClass == NULL) {
       hJvm = GetModuleHandle(JVM_DLL);
       if (hJvm == NULL) return NULL;
       /* need to use the demangled entry point */
       findBootClass = (FindClassFromBootLoader_t *)GetProcAddress(hJvm,
            JVM_BCLOADER);
       if (findBootClass == NULL) {
          JLI_ReportErrorMessage(DLL_ERROR4, JVM_BCLOADER);
          return NULL;
       }
   }
   return findBootClass(env, classname, JNI_FALSE, (jobject)NULL, JNI_FALSE);
}

void
InitLauncher(boolean javaw)
{
    INITCOMMONCONTROLSEX icx;

    /*
     * Required for javaw mode MessageBox output as well as for
     * HotSpot -XX:+ShowMessageBoxOnError in java mode, an empty
     * flag field is sufficient to perform the basic UI initialization.
     */
    memset(&icx, 0, sizeof(INITCOMMONCONTROLSEX));
    icx.dwSize = sizeof(INITCOMMONCONTROLSEX);
    InitCommonControlsEx(&icx);
    _isjavaw = javaw;
    JLI_SetTraceLauncher();
}
