/*
 * Copyright 2008 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#define _WIN32_WINNT 0x0500
#define WINVER 0x0500

#include "stdafx.h"
#include <shlobj.h>
#include <atlbase.h>
#include <locale.h>

CComModule _Module;

#include <atlwin.h>
#include <sys/types.h>
#include <sys/stat.h>
#include "Windows.h"
#include "WinNT.h"
#include <shellapi.h>
#include "DownloadDialog.h"
#include "DownloadHelper.h"
#include "kernel.h"
#include "sun_jkernel_DownloadManager.h"
#include "sun_jkernel_Bundle.h"
#include "sun_jkernel_Mutex.h"
#include "sun_jkernel_BackgroundDownloader.h"
#include <stdio.h>
#include <windows.h>
#include <conio.h>
#include <tchar.h>
#include <tchar.h>
#include <sddl.h>
#include <Aclapi.h>
#include <strsafe.h>

BOOL IsPlatformWindowsVista();

#define BUFSIZE 4096

#define JBROKERPIPE           "\\\\.\\pipe\\jbrokerpipe"
#define JREMAINKEY              "SOFTWARE\\JavaSoft\\Java Runtime Environment"
#define JRE_VERSION_REGISTRY_KEY    JREMAINKEY "\\" VERSION
#define ReleaseAndClose(mutex) \
        if (mutex != NULL) { \
            ReleaseMutex(mutex);  \
            CloseHandle(mutex); \
            mutex = NULL; \
        }

#define KERNEL_DEBUG false

// used to inform kernel that we believe it is running in high integrity
#define JBROKER_KEY "-Dkernel.spawned.from.jbroker=true -Dkernel.background.download=false"

// this is only available on Vista SDK, hard code it here for now
#define LABEL_SECURITY_INFORMATION (0x00000010L)

// The LABEL_SECURITY_INFORMATION SDDL SACL to be set for low integrity
LPCSTR LOW_INTEGRITY_SDDL_SACL = "S:(ML;;NW;;;LW)";

CDownloadDialog dlg;
BOOL createDialog = TRUE;

CComAutoCriticalSection m_csCreateDialog;

typedef BOOL (WINAPI *LPFNInitializeSecurityDescriptor)(
        PSECURITY_DESCRIPTOR pSecurityDescriptor, DWORD dwRevision);
typedef BOOL (WINAPI *LPFNSetSecurityDescriptorDacl)(
        PSECURITY_DESCRIPTOR pSecurityDescriptor, BOOL bDaclPresent, PACL pDacl,
        BOOL bDaclDefaulted);

typedef BOOL (WINAPI *LPFNConvertStringSecurityDescriptorToSecurityDescriptorA)(
        LPCSTR StringSecurityDescriptor, DWORD StringSDRevision,
        PSECURITY_DESCRIPTOR* SecurityDescriptor,
        PULONG SecurityDescriptorSize);

typedef BOOL (WINAPI *LPFNGetSecurityDescriptorSacl)(
        PSECURITY_DESCRIPTOR pSecurityDescriptor, LPBOOL lpbSaclPresent,
        PACL* pSacl, LPBOOL lpbSaclDefaulted);

typedef DWORD (WINAPI *LPFNSetSecurityInfo)(HANDLE handle,
        SE_OBJECT_TYPE ObjectType, SECURITY_INFORMATION SecurityInfo,
        PSID psidOwner, PSID psidGroup, PACL pDacl, PACL pSacl);

BOOL APIENTRY DllMain( HANDLE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
                     )
{
    return TRUE;
}

char* getStringPlatformChars(JNIEnv* env, jstring jstr) {
    char *result = NULL;
    size_t len;
    const jchar* utf16 = env->GetStringChars(jstr, NULL);
    len = wcstombs(NULL, utf16, env->GetStringLength(jstr) * 4) + 1;
    if (len == -1)
        return NULL;
    result = (char*) malloc(len);
    if (wcstombs(result, utf16, len) == -1)
        return NULL;
    env->ReleaseStringChars(jstr, utf16);
    return result;
}

bool SetObjectToLowIntegrity ( HANDLE hObject,
        SE_OBJECT_TYPE type = SE_KERNEL_OBJECT ) {

    bool bRet = false;
    DWORD dwErr = ERROR_SUCCESS;
    PSECURITY_DESCRIPTOR pSD = NULL;
    PACL pSacl = NULL;
    BOOL fSaclPresent = FALSE;
    BOOL fSaclDefaulted = FALSE;

    // initialize function pointers
    HMODULE hModule = LoadLibrary("Advapi32.dll");

    // ConvertStringSecurityDescriptorToSecurityDescriptorA
    LPFNConvertStringSecurityDescriptorToSecurityDescriptorA
            lpfnConvertStringSecurityDescriptorToSecurityDescriptorA =
            (LPFNConvertStringSecurityDescriptorToSecurityDescriptorA)GetProcAddress(
            hModule,
            "ConvertStringSecurityDescriptorToSecurityDescriptorA");

    // GetSecurityDescriptorSacl
    LPFNGetSecurityDescriptorSacl lpfnGetSecurityDescriptorSacl =
            (LPFNGetSecurityDescriptorSacl)GetProcAddress(hModule,
            "GetSecurityDescriptorSacl");

    // SetSecurityInfo
    LPFNSetSecurityInfo lpfnSetSecurityInfo =
            (LPFNSetSecurityInfo)GetProcAddress(hModule,
            "SetSecurityInfo");

    if (lpfnConvertStringSecurityDescriptorToSecurityDescriptorA == NULL ||
            lpfnGetSecurityDescriptorSacl == NULL ||
            lpfnSetSecurityInfo == NULL) {
        if (KERNEL_DEBUG) {
            printf("Fail to initialize function pointer\n");
        }
        FreeLibrary(hModule);
        return FALSE;
    }

    // Set object to lower integrity
    if ( lpfnConvertStringSecurityDescriptorToSecurityDescriptorA(
            LOW_INTEGRITY_SDDL_SACL, SDDL_REVISION_1, &pSD, NULL ) ) {
        if ( lpfnGetSecurityDescriptorSacl(
                pSD, &fSaclPresent, &pSacl, &fSaclDefaulted ) ) {
            dwErr = lpfnSetSecurityInfo(
                    hObject, type, LABEL_SECURITY_INFORMATION,
                    NULL, NULL, NULL, pSacl );

            bRet = (ERROR_SUCCESS == dwErr);
        }

        LocalFree( pSD );
    }

    FreeLibrary(hModule);
    return bRet;
}


JNIEXPORT jlong JNICALL Java_sun_jkernel_Mutex_createNativeMutex
                                (JNIEnv *env , jclass cls, jstring id) {
    SECURITY_ATTRIBUTES sa;
    PSECURITY_DESCRIPTOR pSD = NULL;
    BOOL saInitialized = FALSE;

    // initialize function pointers
    HMODULE hModule = LoadLibrary("Advapi32.dll");

    // InitializeSecurityDescriptor
    LPFNInitializeSecurityDescriptor lpfnInitializeSecurityDescriptor =
            (LPFNInitializeSecurityDescriptor)GetProcAddress(hModule,
            "InitializeSecurityDescriptor");

    // SetSecurityDescriptorDacl
    LPFNSetSecurityDescriptorDacl lpfnSetSecurityDescriptorDacl =
            (LPFNSetSecurityDescriptorDacl)GetProcAddress(hModule,
            "SetSecurityDescriptorDacl");

    if (lpfnInitializeSecurityDescriptor != NULL &&
            lpfnSetSecurityDescriptorDacl != NULL) {

        // Initialize a security descriptor.
        pSD = (PSECURITY_DESCRIPTOR) LocalAlloc(LPTR,
                SECURITY_DESCRIPTOR_MIN_LENGTH);
        if (NULL == pSD) {
            if (KERNEL_DEBUG) {
                printf("LocalAlloc Error %u\n", GetLastError());
            }
            FreeLibrary(hModule);
            return NULL;
        }

        if (!lpfnInitializeSecurityDescriptor(pSD,
                SECURITY_DESCRIPTOR_REVISION)) {
            if (KERNEL_DEBUG) {
                printf("InitializeSecurityDescriptor Error %u\n", GetLastError());
            }
            FreeLibrary(hModule);
            return NULL;

        }
        // Add the ACL to the security descriptor.
        if (!lpfnSetSecurityDescriptorDacl(pSD,
                TRUE,     // bDaclPresent flag
                NULL,     // NULL DACL is assigned to the security descriptor,
                // which allows all access to the object.
                // This is to allow the mutex to be accessbile by
                // all users;  The background downloader launched
                // by the installer will be running as SYSTEM user;
                // while other java process started by the current
                // user will be running as the current username.
                FALSE))   // not a default DACL
        {
            if (KERNEL_DEBUG) {
                printf("SetSecurityDescriptorDacl Error %u\n",
                        GetLastError());
            }
            FreeLibrary(hModule);
            return NULL;
        }

        // Initialize a security attributes structure.
        sa.nLength = sizeof (SECURITY_ATTRIBUTES);
        sa.lpSecurityDescriptor = pSD;
        sa.bInheritHandle = FALSE;

        saInitialized = TRUE;
        FreeLibrary(hModule);
    }

    HANDLE m = CreateMutex(saInitialized ? &sa : NULL, FALSE,
            (LPCSTR) getStringPlatformChars(env, id));
    if (m == NULL) {
        if (KERNEL_DEBUG) {
            printf("CreateMutex Error %u\n", GetLastError());
        }
    }

    // set the mutex object to low integrity on vista, so the mutex
    // can be accessed by different integrity level
    if (IsPlatformWindowsVista()) {
        if (!SetObjectToLowIntegrity(m)) {
            if (KERNEL_DEBUG) {
                printf("Fail to set Mutex object to low integrity\n");
            }
        }
    }
    return (jlong)m ;
}


HANDLE getMutexHandle(JNIEnv *env, jobject mutex) {
    jfieldID handle = env->GetFieldID(env->GetObjectClass(mutex), "handle", "J");
    return (HANDLE) env->GetLongField(mutex, handle);
}

JNIEXPORT jboolean JNICALL Java_sun_jkernel_Mutex_acquire__I
                                (JNIEnv *env, jobject mutex, jint timeout) {
    HANDLE hmutex = getMutexHandle(env, mutex);
    if (hmutex != NULL) {
        int result = WaitForSingleObject(hmutex, timeout);
        if (result == WAIT_ABANDONED)
            result = WaitForSingleObject(hmutex, timeout);
        return (result == WAIT_OBJECT_0);
    }
    else
        return false;
}

void ThrowByName(JNIEnv *env, const char *name, const char *msg) {
    jclass cls = env->FindClass(name);
    /* if cls is NULL, an exception has already been thrown */
    if (cls != NULL) {
        env->ThrowNew(cls, msg);
    }
    /* free the local ref */
    env->DeleteLocalRef(cls);
}

JNIEXPORT void JNICALL Java_sun_jkernel_Mutex_acquire__
        (JNIEnv *env, jobject mutex) {
    if (!Java_sun_jkernel_Mutex_acquire__I(env, mutex, INFINITE)) {
        // failed to acquire mutex, most likely because it was already disposed
        ThrowByName(env, "java/lang/IllegalStateException",
                "error acquiring mutex");
    }
}

JNIEXPORT void JNICALL Java_sun_jkernel_Mutex_release
                                (JNIEnv *env, jobject mutex) {
    HANDLE hmutex = getMutexHandle(env, mutex);
    if (hmutex != NULL)
        ReleaseMutex(hmutex);
    else
        ThrowByName(env, "java/lang/IllegalStateException",
                "releasing disposed mutex");
}

JNIEXPORT void JNICALL Java_sun_jkernel_Mutex_destroyNativeMutex
        (JNIEnv *env, jobject mutex) {
    HANDLE hmutex = getMutexHandle(env, mutex);
    if (hmutex != NULL) {
        Java_sun_jkernel_Mutex_release(env, mutex);
        CloseHandle(hmutex);
    }
}

void createDownloadWindowProc(LPVOID lpParameter) {
    CDownloadDialog* pDlg = (CDownloadDialog *) lpParameter;

    pDlg->delayedDoModal();

    // dialog destroyed, need to create a new one next time
    createDialog = TRUE;
}


void createDownloadWindow(LPVOID lpParameter) {
    // Create a new thread for download window
    DWORD dwThreadId = NULL;
    ::CreateThread(NULL, 0, (LPTHREAD_START_ROUTINE) createDownloadWindowProc, lpParameter, 0, &dwThreadId);
}

JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_bundleInstallComplete
    (JNIEnv *env, jclass dm) {
    dlg.bundleInstallComplete();
}

JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_bundleInstallStart
    (JNIEnv *env, jclass dm) {

    dlg.bundleInstallStart();
}

typedef HRESULT (WINAPI *LPFNIEIsProtectedModeProcess)(BOOL *pbResult);

BOOL isRunningIEProtectedMode() {

    HMODULE hModule = NULL;
    LPFNIEIsProtectedModeProcess lpfnIEIsProtectedModeProcess;

    __try {
        hModule = LoadLibrary("ieframe.dll");
        if (hModule != NULL) {

            lpfnIEIsProtectedModeProcess = (LPFNIEIsProtectedModeProcess)
                GetProcAddress(hModule, "IEIsProtectedModeProcess");

            if (lpfnIEIsProtectedModeProcess != NULL) {
                BOOL bProtectedMode = FALSE;
                HRESULT hr = lpfnIEIsProtectedModeProcess(&bProtectedMode);
                if ( SUCCEEDED(hr) && bProtectedMode ) {
                    // IE is running in protected mode
                    return TRUE;
                } else {
                    // IE isn't running in protected mode
                    return FALSE;
                }
            }
        }
    } __finally {
        if (hModule != NULL) {
            FreeLibrary(hModule);
        }
    }
    return FALSE;
}

/* Return TRUE if current running platform is Windows Vista, FALSE otherwise */
BOOL IsPlatformWindowsVista() {
    static BOOL initialized = FALSE;
    static BOOL isVista = FALSE;
    OSVERSIONINFO  osvi;

    if (initialized) {
        return isVista;
    }

    // Initialize the OSVERSIONINFO structure.
    ZeroMemory( &osvi, sizeof( osvi ) );
    osvi.dwOSVersionInfoSize = sizeof( osvi );

    GetVersionEx( &osvi );  // Assume this function succeeds.

    if ( osvi.dwPlatformId == VER_PLATFORM_WIN32_NT &&
        osvi.dwMajorVersion == 6 ) {
        isVista = TRUE;
    } else {
        isVista = FALSE;
    }

    initialized = TRUE;

    return isVista;
}

JNIEXPORT jboolean  JNICALL Java_sun_jkernel_DownloadManager_isIEProtectedMode
    (JNIEnv *env, jclass dm) {

    if (isRunningIEProtectedMode()) {
        return TRUE;
    }
    return FALSE;
}

JNIEXPORT jboolean JNICALL Java_sun_jkernel_DownloadManager_isWindowsVista
    (JNIEnv *env, jclass dm) {

    if (IsPlatformWindowsVista()) {
        return TRUE;
    }
    return FALSE;
}

int sendMessageToBroker(const char * message) {
        char ackString[1024];
        HANDLE hp = INVALID_HANDLE_VALUE;

        while (hp == INVALID_HANDLE_VALUE) {
            hp = CreateNamedPipe(_T(JBROKERPIPE),
                    PIPE_ACCESS_DUPLEX | FILE_FLAG_FIRST_PIPE_INSTANCE ,
                    PIPE_TYPE_MESSAGE | PIPE_READMODE_MESSAGE | PIPE_WAIT,
                    1, // number of pipes that can exist
                    1024, // output buffer
                    1024, // input buffer
                    0, // timeout
                    NULL); // security attributes

            if (hp == INVALID_HANDLE_VALUE) {
                DWORD err = GetLastError();
                // we only allow one instance of the pipe; if the instance
                // already exists, we will get ERROR_ACCESS_DENIED, which means
                // some other process is using the pipe, so let's try again
                if (err != ERROR_ACCESS_DENIED && err != ERROR_PIPE_BUSY) {
                    // create pipe failed
                    return 0;
                }
                // pipe instance might be in use, keep trying
            }
        }

        // Wait for the client to connect; if it succeeds,
        // the function returns a nonzero value. If the function
        // returns zero, GetLastError returns ERROR_PIPE_CONNECTED.
        BOOL fConnected = ConnectNamedPipe(hp, NULL) ?
                TRUE : (GetLastError() == ERROR_PIPE_CONNECTED);

        if (fConnected)
        {
                // Send message to the pipe server.
                DWORD cbWritten;

                BOOL fSuccess = WriteFile(
                        hp,                  // pipe handle
                        message,             // message
                        (strlen(message)+1)*sizeof(char), // message length
                        &cbWritten,             // bytes written
                        NULL);                  // not overlapped

                if (!fSuccess)
                {
                        // WriteFile failed
                        CloseHandle(hp);
                        return 0;
                }

                // wait for ack from server
                DWORD cbRead;
                TCHAR chBuf[BUFSIZE];

                do
                {
                        // Read from the pipe.
                        fSuccess = ReadFile(
                                hp,    // pipe handle
                                chBuf,    // buffer to receive reply
                                BUFSIZE*sizeof(TCHAR),  // size of buffer
                                &cbRead,  // number of bytes read
                                NULL);    // not overlapped

                        if (! fSuccess && GetLastError() != ERROR_MORE_DATA)
                                break;

                        sprintf(ackString, "%s", chBuf);


                } while (!fSuccess);  // repeat loop if ERROR_MORE_DATA
        }

        CloseHandle(hp);

        if (strcmp(ackString, "SUCCESS") == 0) {
                // server completed move command successfully
                return 1;
        }

        return 0;
}

int sendMoveMessageToBroker(const char * fromPath, const char * userHome) {
    // Send move message
    char * movecmd = "MOVEFILE";

    char * msg = (char*)malloc((strlen(fromPath) + strlen(movecmd) +
            strlen(userHome) + 3) * sizeof(char));

    sprintf(msg, "%s*%s*%s", movecmd, fromPath, userHome);

    return sendMessageToBroker(msg);
}

int sendMoveDirMessageToBroker(const char * fromPath, const char * userHome) {
        // Send move dir message
    char * movecmd = "MOVEDIR";

    char * msg = (char*)malloc((strlen(fromPath) + strlen(movecmd) +
            strlen(userHome) + 3) * sizeof(char));

    sprintf(msg, "%s*%s*%s", movecmd, fromPath, userHome);

    return sendMessageToBroker(msg);
}


int sendKillMessageToBroker() {
        // Send move message
        char * killcmd = "KILLBROKER";
        return sendMessageToBroker(killcmd);
}


int sendPerformCompletionMessageToBroker(const char *javaHome) {
    const char *cmd = "PERFORMCOMPLETION";

    int result = sendMessageToBroker(cmd);

    if (result)
        sendKillMessageToBroker();
    return result;
}

int getConstantInt(JNIEnv *env, jclass cls, const char *name) {
    jfieldID handle = env->GetStaticFieldID(cls, name, "I");
    return env->GetStaticIntField(cls, handle);
}

JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_displayError
        (JNIEnv *env, jclass dm, jint code, jstring arg) {
    int messageId = IDS_FATAL_ERROR;
    int titleId = IDS_ERROR_CAPTION;
    if (code == getConstantInt(env, dm, "ERROR_MALFORMED_BUNDLE_PROPERTIES"))
        messageId = IDS_ERROR_MALFORMED_BUNDLE_PROPERTIES;
    else if (code == getConstantInt(env, dm, "ERROR_DOWNLOADING_BUNDLE_PROPERTIES"))
        messageId = IDS_ERROR_DOWNLOADING_BUNDLE_PROPERTIES;
    else if (code == getConstantInt(env, dm, "ERROR_MALFORMED_URL"))
        messageId = IDS_ERROR_MALFORMED_URL;
    char message[BUFFER_SIZE];
    char rawMessage[BUFFER_SIZE];
    char title[BUFFER_SIZE];
    ::LoadString(_Module.GetModuleInstance(), titleId, title, BUFFER_SIZE);
    ::LoadString(_Module.GetModuleInstance(), messageId, rawMessage, BUFFER_SIZE);
    if (arg != NULL) {
        char *chars = getStringPlatformChars(env, arg);
        sprintf(message, rawMessage, chars);
    }
    else
        strcpy(message, rawMessage);

    MessageBox(NULL, message, title, MB_OK|MB_TASKMODAL);
}

JNIEXPORT jboolean JNICALL Java_sun_jkernel_DownloadManager_askUserToRetryDownloadOrQuit
        (JNIEnv *env, jclass dm, jint code) {

        int ret;
        if (code == getConstantInt(env, dm, "ERROR_DISK_FULL")) {
           ret = dlg.SafeMessageBox(IDS_DISK_FULL_ERROR,
                                    IDS_DISK_FULL_ERROR_CAPTION,
                                    IDS_ERROR_CAPTION,
                                    DIALOG_ERROR_RETRYCANCEL);
        } else {
           ret = dlg.SafeMessageBox(IDS_DOWNLOAD_RETRY_TEXT,
                                    IDS_DOWNLOAD_RETRY,
                                    IDS_ERROR_CAPTION,
                                    DIALOG_ERROR_RETRYCANCEL);
        }
        if (ret != IDRETRY) {
                // user choose to exit, return 0
                return JNI_FALSE;
        }

        // return 1 (retry the download)
        return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_startBackgroundDownloadWithBrokerImpl
(JNIEnv *env, jclass dm, jstring command) {

        char* szCommand = getStringPlatformChars(env, command);

        // Send createprocess message
        char * createproccmd = "STARTBACKGROUNDDOWNLOAD";

        char * msg = (char*)malloc((strlen(createproccmd) + strlen(szCommand) + 2) * sizeof(char));

        sprintf(msg, "%s*%s", createproccmd, szCommand);

        sendMessageToBroker(msg);

        free(szCommand);
}


void getParent(const TCHAR *path, TCHAR *dest) {
    char* lastSlash = max(strrchr(path, '\\'), strrchr(path, '/'));
    if (lastSlash == NULL) {
        *dest = NULL;
        return;
    }
    if (path != dest)
        strcpy(dest, path);
    *lastSlash = NULL;
}


bool createProcess(const TCHAR *path, const TCHAR *args) {
    SHELLEXECUTEINFOA shInfo;

    shInfo.cbSize = sizeof(SHELLEXECUTEINFOA);
    shInfo.fMask = 0;
    shInfo.hwnd = NULL;
    shInfo.lpVerb = "runas";
    shInfo.lpFile = path;
    shInfo.lpParameters = args;
    shInfo.lpDirectory = NULL;
    shInfo.nShow = SW_NORMAL;
    shInfo.hInstApp = NULL;

    int result = (int) ::ShellExecuteExA(&shInfo);
    // ShellExecute is documented to return >32 on success, but I'm consistently
    // getting a return of 1 despite obviously successful results.  1 is not a
    // documented return code from ShellExecute, and this may have something to
    // do with the fact that we're using an undocumented verb in the first place
    // ("runas").
    return result > 32 || result == 1;
}


bool launchJBroker(const char *szJavaHome) {
        char szPath[2048];
        wsprintf(szPath, "%s\\bin\\jbroker.exe", szJavaHome);
    return createProcess(szPath, NULL);
}


JNIEXPORT jboolean JNICALL Java_sun_jkernel_DownloadManager_launchJBroker
(JNIEnv *env, jclass dm, jstring javaHomePath) {
        char* szJavaHome = getStringPlatformChars(env, javaHomePath);
    bool result = launchJBroker(szJavaHome);
        free(szJavaHome);
    return result ? TRUE : FALSE;
}


bool isJBrokerRunning() {
        HANDLE hMutex = NULL;
        DWORD ret = 0;

        if (isRunningIEProtectedMode()) {

                // check if jbroker process is running
                // Use OpenMutex since we have limited access rights.
                // CreateMutex function will fail with ERROR_ACCESS_DENIED in protected mode
                hMutex = OpenMutex(SYNCHRONIZE, FALSE, "SunJavaBrokerMutex");

                ret = ::GetLastError();

                if (hMutex != NULL) {
                        CloseHandle(hMutex);
                }

                if (ret == ERROR_FILE_NOT_FOUND)
                {
                        // jbroker not running yet, launch it
                        return FALSE;
                }

                return TRUE;

        } else {
                hMutex = ::CreateMutex(NULL, TRUE, "SunJavaBrokerMutex");

                if ( (hMutex == NULL) || (::GetLastError() == ERROR_ALREADY_EXISTS)) {
                        // jbroker already running
                        if (hMutex != NULL) ::CloseHandle(hMutex);
                        return TRUE;
                }

                if (hMutex != NULL) ::CloseHandle(hMutex);

                return FALSE;
        }
}


JNIEXPORT jboolean JNICALL Java_sun_jkernel_DownloadManager_isJBrokerRunning
(JNIEnv *env, jclass dm) {
    return isJBrokerRunning() ? TRUE : FALSE;
}


JNIEXPORT jboolean JNICALL Java_sun_jkernel_DownloadManager_moveDirWithBrokerImpl
    (JNIEnv *env, jclass dm, jstring fromPath, jstring userHome) {

    char* fromPathChars = getStringPlatformChars(env, fromPath);

    char* userHomeChars = getStringPlatformChars(env, userHome);

    int ret = sendMoveDirMessageToBroker(fromPathChars, userHomeChars);

    free(fromPathChars);

    free(userHomeChars);

    if (ret == 0) {
        return FALSE;
    }
    return TRUE;
}

JNIEXPORT jboolean JNICALL Java_sun_jkernel_DownloadManager_moveFileWithBrokerImpl
    (JNIEnv *env, jclass dm, jstring fromPath, jstring userHome) {

    char* fromPathChars = getStringPlatformChars(env, fromPath);

    char* userHomeChars = getStringPlatformChars(env, userHome);

    int ret = sendMoveMessageToBroker(fromPathChars, userHomeChars);

    free(fromPathChars);

    free(userHomeChars);

    if (ret == 0) {
        return FALSE;
    }
    return TRUE;
}

/**
 * Throw an exception with the last Windows error code if available.
 */

void ThrowByNameWithLastError(JNIEnv *env, char *exception, char* msg) {
    char fullMsg[1024] = {0};
    if (StringCbPrintf(fullMsg, 1024, "%s. Windows error: %d\n",
        msg, GetLastError()) != S_OK) {

        // Formatting failed: fall back to msg w/o error code
        ThrowByName(env, exception, msg);
    } else {
        ThrowByName(env, exception, fullMsg);
    }
}

/**
 * Common code for "extra" compression or uncompression. If extra code
 * not available do nothing but return false. If available, return true
 * after locating the extra compression library at ".." and the defined
 * path relative to the native library containing this method's code.
 * If enabled, compress or uncompress the srcPath file into destpath,
 * throwing exceptions for errors (see JNI routine docs below for details).
 */

jboolean extraCommon(BOOL docompress,
        JNIEnv *env, jclass dm, jstring srcPath, jstring destPath) {
#ifdef EXTRA_COMP_INSTALL_PATH
    const char *operation = (docompress == true) ? "e" : "d";

    // This should be shared with the deploy tree and should be defined
    // in an implementation like LzmaAlone.h. However the deploy build
    // doesn't exit yet wrt to this function pointer type.

    typedef int (*EXTRACOMPTRTYPE) (int, const char**);

    // Function pointer for invoking the encoder/decoder (uncompressor)
    static volatile EXTRACOMPTRTYPE mptr = NULL;
    // Volatile boolean becomes true when mptr init is finished

// Stringifier macros to get the relative library path

#define K_STRING(x) #x
#define K_GETSTRING(x) K_STRING(x)

    char *srcPathChars = getStringPlatformChars(env, srcPath);

    if (srcPathChars == NULL) {
        // TODO (for all throw calls). If the class&method are *reliably*
        // reported to the user these message prefixes are silly.
        ThrowByName(env, "java/io/IOException",
            "Bundle.uncompress: GetStringPlatformChars failed");
        return true;
    }

    char *destPathChars = getStringPlatformChars(env, destPath);
    if (destPathChars == NULL) {
        free(srcPathChars);
        ThrowByName(env, "java/io/IOException",
            "Bundle.uncompress: GetStringPlatformChars failed");
        return true;
    }
    if (KERNEL_DEBUG) {
        printf("LZMA: %s %s to %s\n", operation, srcPathChars, destPathChars);
    }


    // This loop avoids a lot of repetitious code for exception handling.
    // If any loops are put inside this one be careful to properly
    // handle exceptions within the inner loops.

    do {

        if (mptr == NULL) {

            // Need to locate and link to the extra compression lib, which
            // has a pathname relative to the directory containing the library
            // containing this code, which is assumed to be one directory
            // "below" the JRE base path. That is, the JRE base path is
            // assumed to be ".." from the path of this library and then
            // EXTRA_COMP_INSTALL_PATH from the JRE base path is expected to
            // be the compression lib path.
            // But this code is defensive and tries not to fail if the
            // currently executing library is in ".". It will fail in a
            // case like this if the extra compression lib path isn't
            // "./EXTRA_CMP_INSTALL_PATH" (or just "EXTRA_CMP_INSTALL_PATH").
            // Use macro magic to get the path macro as a string value.

            const char *libRelativePath = K_GETSTRING(EXTRA_COMP_INSTALL_PATH);

            // The max length the base JRE path can be to safely concatenate
            // libRelativePath, a (possible) separator, and a null terminator.
            int jreMaxPathLength = MAX_PATH - sizeof(libRelativePath) - 2;

            TCHAR extraLibPath[MAX_PATH] = {0};
            HMODULE kernel = GetModuleHandle("jkernel");
            if (kernel != NULL) {
                DWORD result = GetModuleFileName(kernel, extraLibPath,
                    MAX_PATH-1);
                if (result > 0) {
                    // remove the name of this library (and maybe a
                    // separator)
                    getParent(extraLibPath, extraLibPath);
                    if (extraLibPath[0] != NULL) {
                        // There was a directory containing the library
                        // (probably "<something or nothing\\>bin"), so
                        // remove that to go up to the assumed JRE base path
                        getParent(extraLibPath, extraLibPath);
                    } else {
                        ThrowByName(env, "java/io/IOException",
                            "bundle uncompression: expected lib path component not found");
                        break;
                    }
                    // This is effectively an assertion that the concat
                    // below cannot overflow
                    if (extraLibPath[0] != NULL) {
                        // Current dir is not ".", so add a separator
                        strcat(extraLibPath, "\\");
                    }
                    if ((strlen(extraLibPath) + 1) > jreMaxPathLength) {
                        ThrowByName(env, "java/io/IOException",
                            "bundle uncompression: JRE base pathname too long");
                        break;
                    }
                    strcat(extraLibPath, libRelativePath);
                } else {
                    ThrowByName(env, "java/io/IOException",
                        "bundle uncompression: GetModuleFileName failed");
                    break;
                }
            } else {
                ThrowByNameWithLastError(env, "java/io/IOException",
                   "bundle uncompression: GetModuleHandle failed");
                break;
            }

            // Load the library and develop a pointer to the decoder routine

            if (KERNEL_DEBUG) {
                printf("bundle uncompression: extra library path %s\n",
                    extraLibPath);
            }

            HMODULE handle = LoadLibrary(extraLibPath);
            if (handle == NULL) {
                ThrowByNameWithLastError(env, "java/io/IOException",
                    "bundle uncompression: LoadLibrary failed");
                break;
            }

            // find the extra uncompression routine

            mptr = (EXTRACOMPTRTYPE) GetProcAddress(handle,
                "ExtraCompressionMain");

            if (mptr == NULL) {
                ThrowByNameWithLastError(env, "java/io/IOException",
                    "bundle uncompression: GetProcAddress failed");
                break;
            }
        }

        // Create the arguments for the decoder
        // Decoder options must go *between* the "d" argument and the
        // source path arguments and don't forget to keep the 1st arg to
        // (*mptr) the same as the number of elements of args.
        const char *args[] = {
            "", // the shared lib makes no attempt access it's "command name"
            operation,

            // Special decoder/encoder switch strings would go here

            // For example: "-d24", to set the dictionary size to 16MB

            "-q", // Suppress banner msg output

            // No special option switch strings after here

            srcPathChars,
            destPathChars
        };
        int argc = sizeof(args) / sizeof(const char *);
        if ((*mptr)(argc, args) != 0) {
            if (KERNEL_DEBUG) {
                printf("uncompress lib call failed with args: ");
                for (int i = 0; i < argc; i++) {
                    printf("%s", args[i]);
                }
                printf("\n");
            }
            ThrowByName(env, "java/io/IOException",
                "bundle uncompression: uncompression failed");
            break;
        }
    } while (false);

    free(srcPathChars);
    free(destPathChars);
    return TRUE;
#else
    if (KERNEL_DEBUG) {
        printf("LZMA not compiled in!\n");
    }

    return FALSE;
#endif // EXTRA_COMP_INSTALL_PATH
}

/**
 * Compress file sourcePath with "extra" algorithm (e.g. 7-Zip LZMA)
 * if available, put the compressed data into file destPath and
 * return true. If extra compression is not available do nothing
 * with destPath and return false;
 * @param srcPath the path of the uncompressed file
 * @param destPath the path of the compressed file, if used
 * @return true if the extra algorithm was used and destPath created
 *
 * @throws IOException if the extra compression code should be available
 *     but cannot be located or linked to, the destination file already
 *     exists or cannot be opened for writing, or the compression fails
 */
JNIEXPORT jboolean JNICALL Java_sun_jkernel_Bundle_extraCompress
        (JNIEnv *env, jclass dm, jstring srcPath, jstring destPath) {
    return extraCommon(true, env, dm, srcPath, destPath);
}

/**
 * Uncompress file sourcePath with "extra" algorithm (e.g. 7-Zip LZMA)
 * if available, put the uncompressed data into file destPath and
 * return true. If if the extra algorithm is not available, leave the
 * destination path unchanged and return false;
 * @param srcPath the path of the file having extra compression
 * @param destPath the path of the uncompressed file
 * @return true if the extra algorithm was used
 *
 * @throws IOException if the extra uncompression code should be available
 *     but cannot be located or linked to, the destination file already
 *     exists or cannot be opened for writing, or the uncompression fails
 */

JNIEXPORT jboolean JNICALL Java_sun_jkernel_Bundle_extraUncompress
        (JNIEnv *env, jclass dm, jstring srcPath, jstring destPath) {
    return extraCommon(false, env, dm, srcPath, destPath);
}


JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_addToTotalDownloadSize
    (JNIEnv *env, jclass dm, jint size) {
    dlg.addToTotalContentLength(size);
}

JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_downloadFromURLImpl
    (JNIEnv *env, jclass dm, jstring url, jobject file, jstring name,
        jboolean showProgress) {
    jclass object = env->FindClass("java/lang/Object");
    jmethodID toString = env->GetMethodID(object, "toString", "()Ljava/lang/String;");
    jstring urlString = (jstring) env->CallObjectMethod(url, toString);
    char* urlChars = getStringPlatformChars(env, urlString);
    if (KERNEL_DEBUG) {
        printf("Kernel downloadFromURL: %s\n", urlChars);
    }
    jstring fileString = (jstring) env->CallObjectMethod(file, toString);
    char* fileChars = getStringPlatformChars(env, fileString);
    char* nameChars = getStringPlatformChars(env, name);

    JavaVM *jvm;
    env->GetJavaVM(&jvm);

    __try
    {

        m_csCreateDialog.Lock();
        if (createDialog && showProgress) {
            // create download progress dialog in a new thread
            dlg.setJavaVM(jvm);
            createDownloadWindow(&dlg);
            createDialog = FALSE;
        }

    }
    __finally
    {
        m_csCreateDialog.Unlock();
    }

    DownloadHelper dh;

    dh.setJavaVM(jvm);
    dh.setURL(urlChars);
    dh.setFile(fileChars);
    dh.setNameText((char*) nameChars);
    dh.setShowProgressDialog(showProgress);
    dh.setDownloadDialog(&dlg);

    if (dh.doDownload() != S_OK) {
        // remove incomplete file
        int ret = DeleteFile(fileChars);
    }

    free(urlChars);
    free(fileChars);
    free(nameChars);
}


void error(char* msg) {
    MessageBox(NULL, msg, "Java Error", MB_OK);
}


// Replace the dest file with the src file.  Returns zero on success, Windows
// error code otherwise.
int replace(TCHAR* fullDest, TCHAR* fullSrc) {
    struct _stat stat;
    int result = _stat(fullSrc, &stat);
    if (result == 0) {
        DeleteFile(fullDest);
        if (MoveFile(fullSrc, fullDest))
            return 0;
        else
            return GetLastError();
    }
    else
        return ENOENT; // src file not found
}


// Replace the dest file with the src file, where both paths are relative to
// the specified root.  Returns zero on success, Windows error code otherwise.
int replaceRelative(TCHAR* root, TCHAR* dest, TCHAR* src) {
    TCHAR fullDest[MAX_PATH];
    TCHAR fullSrc[MAX_PATH];
    strcpy(fullDest, root);
    strcat(fullDest, dest);
    strcpy(fullSrc, root);
    strcat(fullSrc, src);
    return replace(fullDest, fullSrc);
}


// Atomically deletes a file tree.  Returns zero on success, Windows
// error code otherwise.
int deleteAll(TCHAR* root) {
    TCHAR tmp[MAX_PATH];
    if (strlen(root) + 5 > MAX_PATH)
        return ERROR_BUFFER_OVERFLOW;
    strcpy(tmp, root);
    strcat(tmp, ".tmp");
    struct _stat stat;
    int result = _stat(tmp, &stat);
    if (result == 0) {
        result = !deleteAll(tmp);
        if (result)
            return result;
    }
    if (!MoveFile(root, tmp))
        return GetLastError();
    struct _SHFILEOPSTRUCTA fileOp;
    memset(&fileOp, NULL, sizeof(fileOp));
    fileOp.wFunc = FO_DELETE;
    TCHAR pFrom[MAX_PATH + 1];
    strcpy(pFrom, tmp);
    pFrom[strlen(pFrom) + 1] = NULL; // extra null to signify that there is only one file in the list
    fileOp.pFrom = pFrom;
    fileOp.fFlags = FOF_NOCONFIRMATION | FOF_SILENT | FOF_NOERRORUI;
    return SHFileOperation(&fileOp);
}


// moves all file with "wait='true'" specified in bundles.xml into their final
// locations.  These files are stored under lib/bundles/tmp, e.g. lib/meta-index
// is stored at lib/bundles/tmp/lib/meta-index.
// relativePath is the current relative path we are searching (e.g. "lib" for the
// example above), which begins as the empty string.
int moveDelayedFiles(TCHAR* javaHome, TCHAR* relativePath) {
    TCHAR src[MAX_PATH];
    TCHAR* tmp = "lib\\bundles\\tmp";
    if (strlen(javaHome) + strlen(relativePath) + strlen(tmp) > MAX_PATH) {
        error("Path too long.");
        return ERROR_BUFFER_OVERFLOW;
    }
    strcpy(src, javaHome);
    strcat(src, tmp);
    if (relativePath[0] != NULL) {
        strcat(src, "\\");
        strcat(src, relativePath);
    }

    struct _stat stat;
    int result = _stat(src, &stat);
    if (result == 0) {
        if (stat.st_mode & _S_IFDIR) { // is a directory, loop through contents
            strcat(src, "\\*");
            struct _WIN32_FIND_DATAA file;
            HANDLE findHandle = FindFirstFile(src, &file);
            if (findHandle != INVALID_HANDLE_VALUE) {
                do {
                    if (file.cFileName[0] != '.') {
                        char child[MAX_PATH];
                        strcpy(child, relativePath);
                        strcat(child, "\\");
                        strcat(child, file.cFileName);
                        moveDelayedFiles(javaHome, child);
                    }
                }
                while (FindNextFile(findHandle, &file) != 0);
                FindClose(findHandle);
            }
        }
        else { // normal file, move into place
            if (strcmp(relativePath, "\\finished")) {
                TCHAR dest[MAX_PATH];
                strcpy(dest, javaHome);
                strcat(dest, relativePath);

                DeleteFile(dest); // just in case; ignore failures
                if (MoveFile(src, dest))
                    return 0;
                else
                    return GetLastError();
            }
        }
    }
    return result;
}


// activates Class Data Sharing
void activateCDS(const char *javaHome) {
    char java[MAX_PATH];
    strcpy(java, javaHome);
    strcat(java, "bin\\javaw.exe");

    STARTUPINFO si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));
    const char *args = " -Xshare:dump";
    const int argLength = 13;
    char commandLine[MAX_PATH + argLength + 2];
    strcpy(commandLine, "\"");
    strcat(commandLine, java);
    strcat(commandLine, "\"");
    strcat(commandLine, args);
    if (KERNEL_DEBUG)
        printf("Exec: %s\n", commandLine);
    if (CreateProcess(java, commandLine, NULL, NULL, FALSE, 0,
            NULL, NULL, &si, &pi)) {
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
    }
    else
        printf("Error initializing Class Data Sharing: %d", GetLastError());
}

typedef BOOL (*LPFNInstallJQS)();

// activates the Java Quickstart Service
void activateJQS(HMODULE hModule) {
    LPFNInstallJQS lpfnInstallJQS;

    if (hModule != NULL) {
        lpfnInstallJQS = (LPFNInstallJQS)GetProcAddress(hModule, "InstallJQS");
        if (lpfnInstallJQS != NULL) {
            if ((lpfnInstallJQS)() == false && KERNEL_DEBUG) {
                printf("InstallJQS returned FALSE\n");
            }
        }
    }
}

// determines JAVA_HOME and stores it in the specified buffer.  Returns true on success.
BOOL getJavaHome(char* buffer, int bufferSize) {
    HMODULE kernel = GetModuleHandle("jkernel");
    if (kernel != NULL) {
        DWORD result = GetModuleFileName(kernel, buffer, bufferSize);
        if (result > 0) {
            getParent(buffer, buffer); // remove "jkernel.dll"
            if (buffer[0] != NULL)
                getParent(buffer, buffer); // remove "bin"
            if (buffer[0] != NULL) {
                strcat(buffer, "\\");
                return TRUE;
            }
        }
    }
    return FALSE;
}

typedef unsigned int (WINAPI *LPFNPostPing)(LPVOID err);
HANDLE PostPing(HMODULE hModule, char* fname, DWORD err)
{
    LPFNPostPing lpfnPostPing;
    HANDLE hThread = NULL;
    lpfnPostPing = (LPFNPostPing)GetProcAddress(hModule, fname);
    if (lpfnPostPing != NULL) {
        printf("############# ERROR CODE: %d\n", err);
        hThread = (HANDLE)_beginthreadex(NULL, 0, lpfnPostPing,
                                             (LPVOID)err, 0, NULL);
        if (hThread == NULL)
            lpfnPostPing((LPVOID)err);
    }
    return hThread;
}

void postPingAndWait(char* fname, DWORD err) {
    TCHAR path[MAX_PATH];
    if (getJavaHome(path, MAX_PATH)) {
        strcat(path, "bin\\regutils.dll");
        HANDLE hThread = NULL;
        HMODULE hModule = LoadLibrary(path);
        if (hModule != NULL) {
            hThread = PostPing(hModule, fname, err);
            if (hThread != NULL) {
                DWORD dwRet = 0;
                WaitForSingleObject(hThread, 60*1000);
                GetExitCodeThread(hThread, &dwRet);
                CloseHandle(hThread);
            }
        }
    }
    else
        printf("error determining JAVA_HOME for ping\n");
}

JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_postDownloadError
        (JNIEnv *env, jclass dm, jint error) {
    postPingAndWait("PostKernelDLComp", error);
}

JNIEXPORT void JNICALL Java_sun_jkernel_DownloadManager_postDownloadComplete
        (JNIEnv *env, jclass dm) {
    Java_sun_jkernel_DownloadManager_postDownloadError(env, dm, ERROR_SUCCESS);
}

bool spawnedFromJBroker() {
    return strstr(GetCommandLine(), JBROKER_KEY) != NULL;
}


// Determines if we have sufficient access to go ahead and perform completion.
// This is true either if we are not on Vista (in which case we can't elevate
// privileges anyway and have to hope for the best) or if we are on Vista and
// running at High integrity level.
bool highIntegrity() {
    if (!IsPlatformWindowsVista())
        return TRUE;
    else {
        // directly determining this would require access to Vista-specific
        // APIs, which aren't supported by our current build configurations.
        // Instead we look for the presence of a flag on the command line to
        // indicate that we were launched by the jbroker process.  This is
        // actually safer, as it prevents us from re-launching another JRE in
        // the event that we somehow didn't end up with high integrity.
        return spawnedFromJBroker();
    }
}

JNIEXPORT jint JNICALL Java_sun_jkernel_DownloadManager_getCurrentProcessId
        (JNIEnv *env, jclass dm) {
    return (jint) GetCurrentProcessId();
}

JNIEXPORT jstring JNICALL Java_sun_jkernel_DownloadManager_getVisitorId0
        (JNIEnv *env, jclass dm) {
    CRegKey swKey, jsKey, juKey, pKey;
    if (swKey.Open(HKEY_LOCAL_MACHINE, "SOFTWARE", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    if (jsKey.Open(swKey, "JavaSoft", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    if (juKey.Open(jsKey, "Java Update", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    if (pKey.Open(juKey, "Policy", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    DWORD dwCount = BUFSIZE;
    char* keyValue = new char[BUFSIZE];
    if (pKey.QueryValue(keyValue, "VisitorId", &dwCount) != ERROR_SUCCESS){
        return NULL;
    }
    jstring visitorId = env->NewStringUTF(keyValue);

    return visitorId;
}


JNIEXPORT jstring JNICALL Java_sun_jkernel_DownloadManager_getUrlFromRegistry
        (JNIEnv *env, jclass dm) {

    CRegKey swKey, jsKey;
    if (swKey.Open(HKEY_LOCAL_MACHINE, "SOFTWARE", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    if (jsKey.Open(swKey, "JavaSoft", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    DWORD dwCount = BUFSIZE;
        char * keyValue = new char[BUFSIZE];
    if (jsKey.QueryValue(keyValue, "KernelDownloadUrl", &dwCount) != ERROR_SUCCESS){
        return NULL;
    }

    jstring downloadKeyValue = env->NewStringUTF(keyValue);

    return downloadKeyValue;
}



jboolean getBooleanRegistryKey(char *name, jboolean defaultValue) {
    // Check DWORD registry key
    // HKEY_LOCAL_MACHINE/Software/JavaSoft/<name>

    CRegKey swKey, jsKey;
    if (swKey.Open(HKEY_LOCAL_MACHINE, "SOFTWARE", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    if (jsKey.Open(swKey, "JavaSoft", KEY_READ) != ERROR_SUCCESS){
        return NULL;
    }

    DWORD dwValue = 0;
    if (jsKey.QueryValue(dwValue, name) != ERROR_SUCCESS){

        // Key does not exist, will return default value
        return defaultValue;
    }

    return dwValue != 0;
}


JNIEXPORT jboolean JNICALL Java_sun_jkernel_BackgroundDownloader_getBackgroundDownloadKey
        (JNIEnv *env, jclass dm) {
    return getBooleanRegistryKey("KernelBackgroundDownload", TRUE);
}


JNIEXPORT jboolean JNICALL Java_sun_jkernel_DownloadManager_getDebugKey
        (JNIEnv *env, jclass dm) {
    return getBooleanRegistryKey("KernelDebug", FALSE);
}


// Called by the launcher before the JVM starts.  If all kernel bundles have been
// downloaded, this function performs various post-download cleanups such as
// moving the merged rt.jar into place.  At the end of cleanup, the JRE should
// be indistinguishable from the non-kernel JRE.
void preJVMStart() {
    char rawMsg[BUFFER_SIZE];
    char msg[BUFFER_SIZE];
    HMODULE kernel = GetModuleHandle("jkernel");
    if (kernel != NULL) {
        TCHAR javaHome[MAX_PATH];
        DWORD result = GetModuleFileName(kernel, javaHome, MAX_PATH);
        if (result > 0) {
            getParent(javaHome, javaHome); // remove "jkernel.dll"
            if (javaHome[0] != NULL)
                getParent(javaHome, javaHome); // remove "bin"
            if (javaHome[0] != NULL) {
                // should now be pointing to correct java.home
                strcat(javaHome, "\\");
                bool jbroker = spawnedFromJBroker();
                HANDLE file;
                TCHAR rt[MAX_PATH];
                strcpy(rt, javaHome);
                strcat(rt, "lib\\rt.jar");
                HANDLE startMutex = CreateMutex(NULL, FALSE, "jvmStart");
                if (!jbroker) { // else mutex is already held by the pre-jbroker JVM
                    if (KERNEL_DEBUG)
                        printf("Locking startMutex\n");
                    WaitForSingleObject(startMutex, INFINITE);
                    if (KERNEL_DEBUG)
                        printf("Locked startMutex\n");
                    // open rt.jar for reading.  This prevents other JREs from being
                    // able to acquire a write lock on rt.jar, which is used as a test
                    // to ensure that no other JREs are running.
                    // The failure to close the file handle is intentional -- if we
                    // close it, there will be a brief window between the close and
                    // when the JRE reopens it during which another jre could get
                    // a write lock on it, hosing us.
                    file = CreateFile(rt, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, NULL, NULL);
                    if (file == INVALID_HANDLE_VALUE) {
                        ReleaseAndClose(startMutex);
                        return;
                    }
                    if (KERNEL_DEBUG)
                        printf("Opened rt.jar for reading\n");
                }
                TCHAR finished[MAX_PATH];
                TCHAR* finishedPath = "lib\\bundles\\tmp\\finished";
                if (strlen(javaHome) + strlen(finishedPath) < MAX_PATH) {
                    strcpy(finished, javaHome);
                    strcat(finished, finishedPath);
                    struct _stat finishedStat;
                    result = _stat(finished, &finishedStat);
                    if (result == 0) { // JRE has been fully downloaded but not yet cleaned up
                        if (KERNEL_DEBUG)
                            printf("Beginning completion.\n");
                        if (!jbroker)
                            CloseHandle(file);
                        if (highIntegrity()) {
                            // attempt to open rt.jar for exclusive write access -- if this succeeds,
                            // we know no other JREs are running
                            file = CreateFile(rt, GENERIC_WRITE, NULL, NULL, OPEN_EXISTING, NULL, NULL);
                            if (file == INVALID_HANDLE_VALUE) {
                                // must be another JRE running...
                                ReleaseAndClose(startMutex);
                                return;
                            }
                            if (KERNEL_DEBUG)
                                printf("Opened rt.jar for writing.\n");
                            CloseHandle(file);
                            if (KERNEL_DEBUG)
                                printf("Closed rt.jar.\n");
                            int result = replaceRelative(javaHome, "lib\\rt.jar",
                                    "lib\\bundles\\tmp\\merged-rt.jar");
                            if (result != 0 && result != ENOENT) {
                                ::LoadString(_Module.GetModuleInstance(), IDS_FILE_UPDATE_ERROR, rawMsg, BUFFER_SIZE);
                                wsprintf(msg, rawMsg, javaHome, "lib\\rt.jar");
                                error(msg);
                                ReleaseAndClose(startMutex);
                                return;
                            }
                            result = replaceRelative(javaHome, "lib\\resources.jar",
                                    "lib\\bundles\\tmp\\merged-resources.jar");
                            if (result != 0 && result != ENOENT) {
                                ::LoadString(_Module.GetModuleInstance(), IDS_FILE_UPDATE_ERROR, rawMsg, BUFFER_SIZE);
                                wsprintf(msg, rawMsg, javaHome, "lib\\resources.jar");
                                error(msg);
                                ReleaseAndClose(startMutex);
                                return;
                            }

                            TCHAR bundles[MAX_PATH];
                            strcpy(bundles, javaHome);
                            strcat(bundles, "lib\\bundles");
                            if (moveDelayedFiles(javaHome, "")) {
                                ::LoadString(_Module.GetModuleInstance(), IDS_FILE_UPDATE_ERROR, msg, BUFFER_SIZE);
                                error(msg);
                                ReleaseAndClose(startMutex);
                                return;
                            }

                            TCHAR kernel[MAX_PATH];
                            strcpy(kernel, javaHome);
                            strcat(kernel, "bin\\kernel");
                            result = deleteAll(kernel);
                            if (result != 0 && result != ENOENT) {
                                ::LoadString(_Module.GetModuleInstance(), IDS_FILE_DELETE_ERROR, rawMsg, BUFFER_SIZE);
                                wsprintf(msg, rawMsg, kernel);
                                error(msg);
                                ReleaseAndClose(startMutex);
                                return;
                            }

                            if (deleteAll(bundles)) {
                                // fail silently, CR #6643218
                                printf("deleteAll failed!\n");
                                ReleaseAndClose(startMutex);
                                return;
                            }

                            TCHAR kernelMap[MAX_PATH];
                            strcpy(kernelMap, javaHome);
                            strcat(kernelMap, "lib\\kernel.map");
                            result = deleteAll(kernelMap);
                            if (result != 0 && result != ENOENT) {
                                ::LoadString(_Module.GetModuleInstance(), IDS_FILE_DELETE_ERROR, rawMsg, BUFFER_SIZE);
                                wsprintf(msg, rawMsg, kernelMap);
                                error(msg);
                                ReleaseAndClose(startMutex);
                                return;
                            }

                            strcpy(rt, javaHome);
                            strcat(rt, "bin\\regutils.dll");
                            HANDLE hThread = NULL;
                            HMODULE hModule = LoadLibrary(rt);
                            if (hModule != NULL)
                                hThread = PostPing(hModule, "PostKernelComp", ERROR_SUCCESS);
                            if (KERNEL_DEBUG)
                                printf("Activating JQS.\n");
                            activateJQS(hModule);

                            if (KERNEL_DEBUG)
                                printf("Activating CDS.\n");
                            activateCDS(javaHome);

                            if (hThread != NULL) {
                                DWORD dwRet = 0;
                                WaitForSingleObject(hThread, 60*1000);
                                GetExitCodeThread(hThread, &dwRet);
                                CloseHandle(hThread);
                            }
                            if (hModule != NULL)
                                FreeLibrary(hModule);
                        } else {
                            bool jbroker = isJBrokerRunning();
                            if (!jbroker) {
                                // remove trailing slash
                                javaHome[strlen(javaHome) - 1] = 0;
                                jbroker = launchJBroker(javaHome);
                                if (!jbroker) {
                                    ::LoadString(_Module.GetModuleInstance(),
                                                IDS_JBROKER_ERROR,
                                                msg,
                                                BUFFER_SIZE);
                                    error(msg);
                                }
                            }
                            if (jbroker)
                                sendPerformCompletionMessageToBroker(javaHome);
                        }
                    }
                }
                if (KERNEL_DEBUG)
                    printf("Releasing startMutex.\n");
                ReleaseAndClose(startMutex);
            } else {
                ::LoadString(_Module.GetModuleInstance(), IDS_JAVA_HOME_ERROR, msg, BUFFER_SIZE);
                error(msg);
            }
        } else {
            ::LoadString(_Module.GetModuleInstance(), IDS_KERNEL_HOME_ERROR, msg, BUFFER_SIZE);
            error(msg);
        }
    } else {
        ::LoadString(_Module.GetModuleInstance(), IDS_KERNEL_HOME_ERROR, msg, BUFFER_SIZE);
        error(msg);
    }
}
