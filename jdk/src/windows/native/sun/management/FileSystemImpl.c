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
#include <malloc.h>
#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "sun_management_FileSystemImpl.h"

/*
 * Access mask to represent any file access
 */
#define ANY_ACCESS (FILE_GENERIC_READ | FILE_GENERIC_WRITE | FILE_GENERIC_EXECUTE)

/*
 * Function prototypes for security functions - we can't statically
 * link because these functions aren't on Windows 9x.
 */
typedef BOOL (WINAPI *GetFileSecurityFunc)
    (LPCTSTR lpFileName, SECURITY_INFORMATION RequestedInformation,
     PSECURITY_DESCRIPTOR pSecurityDescriptor, DWORD nLength,
     LPDWORD lpnLengthNeeded);

typedef BOOL (WINAPI *GetSecurityDescriptorOwnerFunc)
    (PSECURITY_DESCRIPTOR pSecurityDescriptor, PSID *pOwner,
     LPBOOL lpbOwnerDefaulted);

typedef BOOL (WINAPI *GetSecurityDescriptorDaclFunc)
    (PSECURITY_DESCRIPTOR pSecurityDescriptor, LPBOOL lpbDaclPresent,
     PACL *pDacl, LPBOOL lpbDaclDefaulted);

typedef BOOL (WINAPI *GetAclInformationFunc)
    (PACL pAcl, LPVOID pAclInformation, DWORD nAclInformationLength,
     ACL_INFORMATION_CLASS dwAclInformationClass);

typedef BOOL (WINAPI *GetAceFunc)
    (PACL pAcl, DWORD dwAceIndex, LPVOID *pAce);

typedef BOOL (WINAPI *EqualSidFunc)(PSID pSid1, PSID pSid2);


/* Addresses of the security functions */
static GetFileSecurityFunc GetFileSecurity_func;
static GetSecurityDescriptorOwnerFunc GetSecurityDescriptorOwner_func;
static GetSecurityDescriptorDaclFunc GetSecurityDescriptorDacl_func;
static GetAclInformationFunc GetAclInformation_func;
static GetAceFunc GetAce_func;
static EqualSidFunc EqualSid_func;

/* True if this OS is NT kernel based (NT/2000/XP) */
static int isNT;


/*
 * Returns JNI_TRUE if the specified file is on a file system that supports
 * persistent ACLs (On NTFS file systems returns true, on FAT32 file systems
 * returns false).
 */
static jboolean isSecuritySupported(JNIEnv* env, const char* path) {
    char* root;
    char* p;
    BOOL res;
    DWORD dwMaxComponentLength;
    DWORD dwFlags;
    char fsName[128];
    DWORD fsNameLength;

    /*
     * Get root directory. Assume that files are absolute paths. For UNCs
     * the slash after the share name is required.
     */
    root = strdup(path);
    if (*root == '\\') {
        /*
         * \\server\share\file ==> \\server\share\
         */
        int slashskip = 3;
        p = root;
        while ((*p == '\\') && (slashskip > 0)) {
            char* p2;
            p++;
            p2 = strchr(p, '\\');
            if ((p2 == NULL) || (*p2 != '\\')) {
                free(root);
                JNU_ThrowIOException(env, "Malformed UNC");
                return JNI_FALSE;
            }
            p = p2;
            slashskip--;
        }
        if (slashskip != 0) {
            free(root);
            JNU_ThrowIOException(env, "Malformed UNC");
            return JNI_FALSE;
        }
        p++;
        *p = '\0';

    } else {
        p = strchr(root, '\\');
        if (p == NULL) {
            free(root);
            JNU_ThrowIOException(env, "Absolute filename not specified");
            return JNI_FALSE;
        }
        p++;
        *p = '\0';
    }


    /*
     * Get the volume information - this gives us the file system file and
     * also tells us if the file system supports persistent ACLs.
     */
    fsNameLength = sizeof(fsName)-1;
    res = GetVolumeInformation(root,
                               NULL,        // address of name of the volume, can be NULL
                               0,           // length of volume name
                               NULL,        // address of volume serial number, can be NULL
                               &dwMaxComponentLength,
                               &dwFlags,
                               fsName,
                               fsNameLength);
    if (res == 0) {
        free(root);
        JNU_ThrowIOExceptionWithLastError(env, "GetVolumeInformation failed");
        return JNI_FALSE;
    }

    free(root);
    return (dwFlags & FS_PERSISTENT_ACLS) ? JNI_TRUE : JNI_FALSE;
}


/*
 * Returns the security descriptor for a file.
 */
static SECURITY_DESCRIPTOR* getFileSecurityDescriptor(JNIEnv* env, const char* path) {
    SECURITY_DESCRIPTOR* sd;
    DWORD len = 0;
    SECURITY_INFORMATION info =
        OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION;

    (*GetFileSecurity_func)(path, info , 0, 0, &len);
    if (GetLastError() != ERROR_INSUFFICIENT_BUFFER) {
        JNU_ThrowIOExceptionWithLastError(env, "GetFileSecurity failed");
        return NULL;
    }
    sd = (SECURITY_DESCRIPTOR *)malloc(len);
    if (sd == NULL) {
        JNU_ThrowOutOfMemoryError(env, 0);
    } else {
        if (!(*GetFileSecurity_func)(path, info, sd, len, &len)) {
            JNU_ThrowIOExceptionWithLastError(env, "GetFileSecurity failed");
            free(sd);
            return NULL;
        }
    }
    return sd;
}

/*
 * Returns pointer to the SID identifying the owner of the specified
 * file.
 */
static SID* getFileOwner(JNIEnv* env, SECURITY_DESCRIPTOR* sd) {
    SID* owner;
    BOOL defaulted;

    if (!(*GetSecurityDescriptorOwner_func)(sd, &owner, &defaulted)) {
        JNU_ThrowIOExceptionWithLastError(env, "GetSecurityDescriptorOwner failed");
        return NULL;
    }
    return owner;
}

/*
 * Returns pointer discretionary access-control list (ACL) from the security
 * descriptor of the specified file.
 */
static ACL* getFileDACL(JNIEnv* env, SECURITY_DESCRIPTOR* sd) {
    ACL *acl;
    int defaulted, present;

    if (!(*GetSecurityDescriptorDacl_func)(sd, &present, &acl, &defaulted)) {
        JNU_ThrowIOExceptionWithLastError(env, "GetSecurityDescriptorDacl failed");
        return NULL;
    }
    if (!present) {
        JNU_ThrowInternalError(env, "Security descriptor does not contain a DACL");
        return NULL;
    }
    return acl;
}

/*
 * Returns JNI_TRUE if the specified owner is the only SID will access
 * to the file.
 */
static jboolean isAccessUserOnly(JNIEnv* env, SID* owner, ACL* acl) {
    ACL_SIZE_INFORMATION acl_size_info;
    DWORD i;

    /*
     * If there's no DACL then there's no access to the file
     */
    if (acl == NULL) {
        return JNI_TRUE;
    }

    /*
     * Get the ACE count
     */
    if (!(*GetAclInformation_func)(acl, (void *) &acl_size_info, sizeof(acl_size_info),
                                  AclSizeInformation)) {
        JNU_ThrowIOExceptionWithLastError(env, "GetAclInformation failed");
        return JNI_FALSE;
    }

    /*
     * Iterate over the ACEs. For each "allow" type check that the SID
     * matches the owner, and check that the access is read only.
     */
    for (i = 0; i < acl_size_info.AceCount; i++) {
        void* ace;
        ACCESS_ALLOWED_ACE *access;
        SID* sid;

        if (!(*GetAce_func)(acl, i, &ace)) {
            JNU_ThrowIOExceptionWithLastError(env, "GetAce failed");
            return -1;
        }
        if (((ACCESS_ALLOWED_ACE *)ace)->Header.AceType != ACCESS_ALLOWED_ACE_TYPE) {
            continue;
        }
        access = (ACCESS_ALLOWED_ACE *)ace;
        sid = (SID *) &access->SidStart;
        if (!EqualSid(owner, sid)) {
            /*
             * If the ACE allows any access then the file is not secure.
             */
            if (access->Mask & ANY_ACCESS) {
                return JNI_FALSE;
            }
        }
    }
    return JNI_TRUE;
}


/*
 * Class:     sun_management_FileSystemImpl
 * Method:    init0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_management_FileSystemImpl_init0
  (JNIEnv *env, jclass ignored)
{
    OSVERSIONINFO ver;
    HINSTANCE hInst;

    /*
     * Get the OS version. If dwPlatformId is VER_PLATFORM_WIN32_NT
     * it means we're running on a Windows NT, 2000, or XP machine.
     */
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);
    isNT = (ver.dwPlatformId == VER_PLATFORM_WIN32_NT);
    if (!isNT) {
        return;
    }

    /*
     * On NT/2000/XP we need the addresses of the security functions
     */
    hInst = LoadLibrary("ADVAPI32.DLL");
    if (hInst == NULL) {
        JNU_ThrowIOExceptionWithLastError(env, "Unable to load ADVAPI32.DLL");
        return;
    }


    GetFileSecurity_func = (GetFileSecurityFunc)GetProcAddress(hInst, "GetFileSecurityA");
    GetSecurityDescriptorOwner_func =
        (GetSecurityDescriptorOwnerFunc)GetProcAddress(hInst, "GetSecurityDescriptorOwner");
    GetSecurityDescriptorDacl_func =
        (GetSecurityDescriptorDaclFunc)GetProcAddress(hInst, "GetSecurityDescriptorDacl");
    GetAclInformation_func =
        (GetAclInformationFunc)GetProcAddress(hInst, "GetAclInformation");
    GetAce_func = (GetAceFunc)GetProcAddress(hInst, "GetAce");
    EqualSid_func = (EqualSidFunc)GetProcAddress(hInst, "EqualSid");

    if (GetFileSecurity_func == NULL ||
        GetSecurityDescriptorDacl_func == NULL ||
        GetSecurityDescriptorDacl_func == NULL ||
        GetAclInformation_func == NULL ||
        GetAce_func == NULL ||
        EqualSid_func == NULL)
    {
        JNU_ThrowIOExceptionWithLastError(env,
            "Unable to get address of security functions");
        return;
    }
}

/*
 * Class:     sun_management_FileSystemImpl
 * Method:    isSecuritySupported0
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_management_FileSystemImpl_isSecuritySupported0
  (JNIEnv *env, jclass ignored, jstring str)
{
    jboolean res;
    jboolean isCopy;
    const char* path;

    if (!isNT) {
        return JNI_FALSE;
    }

    path = JNU_GetStringPlatformChars(env, str, &isCopy);
    if (path != NULL) {
        res = isSecuritySupported(env, path);
        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, str, path);
        }
        return res;
    } else {
        /* exception thrown - doesn't matter what we return */
        return JNI_TRUE;
    }
}


/*
 * Class:     sun_management_FileSystemImpl
 * Method:    isAccessUserOnly0
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_management_FileSystemImpl_isAccessUserOnly0
  (JNIEnv *env, jclass ignored, jstring str)
{
    jboolean res = JNI_FALSE;
    jboolean isCopy;
    const char* path;

    path = JNU_GetStringPlatformChars(env, str, &isCopy);
    if (path != NULL) {
        /*
         * From the security descriptor get the file owner and
         * DACL. Then check if anybody but the owner has access
         * to the file.
         */
        SECURITY_DESCRIPTOR* sd = getFileSecurityDescriptor(env, path);
        if (sd != NULL) {
            SID *owner = getFileOwner(env, sd);
            if (owner != NULL) {
                ACL* acl = getFileDACL(env, sd);
                if (acl != NULL) {
                    res = isAccessUserOnly(env, owner, acl);
                } else {
                    /*
                     * If acl is NULL it means that an exception was thrown
                     * or there is "all acess" to the file.
                     */
                    res = JNI_FALSE;
                }
            }
            free(sd);
        }
        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, str, path);
        }
    }
    return res;
}
