/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth.module;

import com.sun.security.auth.module.nt._SID_AND_ATTRIBUTES;
import com.sun.security.auth.module.nt._TOKEN_GROUPS;
import jdk.internal.foreign.BufferStack;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.lang.foreign.Linker;


import static com.sun.security.auth.module.nt.my_win_h.*;
import static com.sun.security.auth.module.nt.my_win_h$shared.C_CHAR;
import static com.sun.security.auth.module.nt.my_win_h$shared.C_INT;
import static com.sun.security.auth.module.nt.my_win_h$shared.C_LONG;
import static com.sun.security.auth.module.nt.my_win_h$shared.C_POINTER;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemorySegment.NULL;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * This class implementation retrieves and makes available NT
 * security information for the current user.
 *
 * @since 1.4
 */
@SuppressWarnings("restricted")
public class NTSystem {

    private static final VarHandle VH_ERROR = Linker.Option.captureStateLayout()
            .varHandle(groupElement("GetLastError"));

    private static void DisplayErrorText(Arena scope, String label, MemorySegment cs) {
        int dwFormatFlags = FORMAT_MESSAGE_ALLOCATE_BUFFER() |
                FORMAT_MESSAGE_FROM_SYSTEM() |
                FORMAT_MESSAGE_IGNORE_INSERTS();
        MemorySegment buffer = scope.allocate(C_POINTER);
        MemorySegment hModule = NULL;
        int errno = (int) VH_ERROR.get(cs, 0);
        if (errno >= NERR_BASE() && errno <= MAX_NERR()) {
            MemorySegment dllName = scope.allocateFrom("netmsg.dll");
            hModule = LoadLibraryExA(
                    dllName,
                    NULL,
                    LOAD_LIBRARY_AS_DATAFILE()
            );
            if(hModule != NULL) {
                dwFormatFlags |= FORMAT_MESSAGE_FROM_HMODULE();
            }
        }
        // dwLanguageId = 0 uses the caller's language preferences
        FormatMessageA(dwFormatFlags, hModule, errno, 0, buffer, 0, NULL);
        MemorySegment msg = buffer.get(C_POINTER, 0);
        System.out.println(label + " error [" + errno + "]: "
                + msg.getString(0));
        LocalFree(msg);
        if (hModule != NULL) {
            FreeLibrary(hModule);
        }
    }

    // The following MethodHandles and methods are the GetLastError-enabled
    // versions of their original jextract-generated forms.

    private static final MemoryLayout CAPTURE_LAYOUT =
            Linker.Option.captureStateLayout();
    private static final BufferStack POOL =
            BufferStack.of(CAPTURE_LAYOUT);
    private static final Linker.Option CCS_GLE =
            Linker.Option.captureCallState("GetLastError");

    private static final MethodHandle MH_OpenThreadTokenGLE = Linker.nativeLinker()
            .downcallHandle(OpenThreadToken$address(), OpenThreadToken$descriptor(), CCS_GLE);

    private int OpenThreadTokenGLE(MemorySegment ThreadHandle, int DesiredAccess, int OpenAsSelf, MemorySegment TokenHandle) {
        try (var arena = POOL.pushFrame(CAPTURE_LAYOUT.byteSize() + 64,
                CAPTURE_LAYOUT.byteAlignment())) {
            MemorySegment cs = arena.allocate(CAPTURE_LAYOUT);
            int output = (int) MH_OpenThreadTokenGLE.invokeExact(
                    cs, ThreadHandle, DesiredAccess, OpenAsSelf, TokenHandle);
            if (output == 0) {
                if (debug) {
                    DisplayErrorText(arena, "OpenThreadToken", cs);
                }
            }
            return output;
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("should not reach here", t);
        }
    }

    private static final MethodHandle MH_OpenProcessTokenGLE = Linker.nativeLinker()
            .downcallHandle(OpenProcessToken$address(), OpenProcessToken$descriptor(), CCS_GLE);

    private int OpenProcessTokenGLE(MemorySegment ProcessHandle_, int DesiredAccess, MemorySegment TokenHandle) {
        try (var arena = POOL.pushFrame(CAPTURE_LAYOUT.byteSize() + 64,
                CAPTURE_LAYOUT.byteAlignment())) {
            MemorySegment cs = arena.allocate(CAPTURE_LAYOUT);
            int output = (int) MH_OpenProcessTokenGLE.invokeExact(
                    cs, ProcessHandle_, DesiredAccess, TokenHandle);
            if (output == 0) {
                if (debug) {
                    DisplayErrorText(arena, "OpenProcessToken", cs);
                }
            }
            return output;
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("should not reach here", t);
        }
    }

    private static final MethodHandle MH_GetTokenInformationGLE = Linker.nativeLinker()
            .downcallHandle(GetTokenInformation$address(), GetTokenInformation$descriptor(), CCS_GLE);

    private int GetTokenInformationGLE(MemorySegment TokenHandle, int TokenInformationClass, MemorySegment TokenInformation, int TokenInformationLength, MemorySegment ReturnLength) {
        try (var arena = POOL.pushFrame(CAPTURE_LAYOUT.byteSize() + 64,
                CAPTURE_LAYOUT.byteAlignment())) {
            MemorySegment cs = arena.allocate(CAPTURE_LAYOUT);
            int output = (int) MH_GetTokenInformationGLE.invokeExact(
                    cs, TokenHandle, TokenInformationClass, TokenInformation, TokenInformationLength, ReturnLength);
            if (output == 0) {
                if (debug) {
                    DisplayErrorText(arena, "GetTokenInformation", cs);
                }
            }
            return output;
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("should not reach here", t);
        }
    }

    private static final MethodHandle MH_LookupAccountSidAGLE = Linker.nativeLinker()
            .downcallHandle(LookupAccountSidA$address(), LookupAccountSidA$descriptor(), CCS_GLE);

    private int LookupAccountSidAGLE(MemorySegment lpSystemName, MemorySegment Sid, MemorySegment Name, MemorySegment cchName, MemorySegment ReferencedDomainName, MemorySegment cchReferencedDomainName, MemorySegment peUse) {
        try (var arena = POOL.pushFrame(CAPTURE_LAYOUT.byteSize() + 64,
                CAPTURE_LAYOUT.byteAlignment())) {
            MemorySegment cs = arena.allocate(CAPTURE_LAYOUT);
            int output = (int) MH_LookupAccountSidAGLE.invokeExact(
                    cs, lpSystemName, Sid, Name, cchName, ReferencedDomainName, cchReferencedDomainName, peUse);
            if (output == 0) {
                if (debug) {
                    DisplayErrorText(arena, "LookupAccountSidA", cs);
                }
            }
            return output;
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("should not reach here", t);
        }
    }

    private static final MethodHandle MH_LookupAccountNameAGLE = Linker.nativeLinker()
            .downcallHandle(LookupAccountNameA$address(), LookupAccountNameA$descriptor(), CCS_GLE);

    private int LookupAccountNameAGLE(MemorySegment lpSystemName, MemorySegment lpAccountName, MemorySegment Sid, MemorySegment cbSid, MemorySegment ReferencedDomainName, MemorySegment cchReferencedDomainName, MemorySegment peUse) {
        try (var arena = POOL.pushFrame(CAPTURE_LAYOUT.byteSize() + 64,
                CAPTURE_LAYOUT.byteAlignment())) {
            MemorySegment cs = arena.allocate(CAPTURE_LAYOUT);
            int output = (int) MH_LookupAccountNameAGLE.invokeExact(
                    cs, lpSystemName, lpAccountName, Sid, cbSid, ReferencedDomainName, cchReferencedDomainName, peUse);
            if (output == 0) {
                if (debug) {
                    DisplayErrorText(arena, "LookupAccountNameA", cs);
                }
            }
            return output;
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("should not reach here", t);
        }
    }

    private static final MethodHandle MH_DuplicateTokenGLE = Linker.nativeLinker()
            .downcallHandle(DuplicateToken$address(), DuplicateToken$descriptor(), CCS_GLE);

    private int DuplicateTokenGLE(MemorySegment ExistingTokenHandle, int ImpersonationLevel, MemorySegment DuplicateTokenHandle) {
        try (var arena = POOL.pushFrame(CAPTURE_LAYOUT.byteSize() + 64,
                CAPTURE_LAYOUT.byteAlignment())) {
            MemorySegment cs = arena.allocate(CAPTURE_LAYOUT);
            int output = (int) MH_DuplicateTokenGLE.invokeExact(
                    cs, ExistingTokenHandle, ImpersonationLevel, DuplicateTokenHandle);
            if (output == 0) {
                if (debug) {
                    DisplayErrorText(arena, "DuplicateToken", cs);
                }
            }
            return output;
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new AssertionError("should not reach here", t);
        }
    }

    private void getCurrent() {
        try (Arena scope = Arena.ofConfined()) {

            String tmpUserName = null;
            String tmpDomain = null;
            String tmpDomainSID = null;
            String tmpUserSID = null;
            String[] tmpGroupIDs = null;
            String tmpPrimaryGroupID = null;

            boolean succeed = false;

            MemorySegment tokenHandle = null;
            try {
                // getToken
                if (debug) {
                    System.out.println("getting access token");
                }
                MemorySegment pHandle = scope.allocate(HANDLE);
                // first try the thread token
                if (OpenThreadTokenGLE(GetCurrentThread(), TOKEN_READ(), 0, pHandle) == 0) {
                    // next try the process token
                    if (OpenProcessTokenGLE(GetCurrentProcess(), TOKEN_READ(), pHandle) == 0) {
                        return;
                    }
                }
                tokenHandle = pHandle.get(HANDLE, 0);
                if (debug) {
                    System.out.println("got user access token");
                }

                // getUser
                if (debug) {
                    System.out.println("getting user info");
                }

                MemorySegment bufSize = scope.allocate(C_LONG);
                MemorySegment buf2Size = scope.allocate(C_LONG);

                // enum SID_NAME_USE
                MemorySegment nameUse = scope.allocate(C_INT);

                GetTokenInformation(tokenHandle, TokenUser(), NULL, 0, bufSize);
                int size = bufSize.get(C_LONG, 0);
                MemorySegment tokenUserInfo = scope.allocate(size);
                if (GetTokenInformationGLE(
                        tokenHandle, TokenUser(), tokenUserInfo, size, bufSize) == 0) {
                    return;
                }
                if (debug) {
                    System.out.println("Got TokenUser info");
                }

                // User is first field of _TOKEN_USER and Sid is the first field
                // of _SID_AND_ATTRIBUTES, so tokenUserInfo->User.Sid is simply
                // the pointer at the beginning. No need to drill info.
                MemorySegment userSid = tokenUserInfo.get(C_POINTER, 0);
                bufSize.set(C_LONG, 0, 0);
                buf2Size.set(C_LONG, 0, 0);
                LookupAccountSidA(NULL,  // local host
                        userSid, NULL, bufSize, NULL, buf2Size, nameUse);

                MemorySegment bufName = scope.allocate(bufSize.get(C_LONG, 0));
                MemorySegment bufDomain = scope.allocate(buf2Size.get(C_LONG, 0));
                if (LookupAccountSidAGLE(NULL, // local host
                        userSid, bufName, bufSize, bufDomain, buf2Size, nameUse) == 0) {
                    return;
                }
                tmpUserName = bufName.getString(0);
                tmpDomain = bufDomain.getString(0);
                tmpUserSID = getTextSid(userSid);
                if (debug) {
                    System.out.println("userName = " + tmpUserName
                            + ", domainName = " + tmpDomain
                            + ", userSid = " + tmpUserSID);
                }

                bufSize.set(C_LONG, 0, 0);
                buf2Size.set(C_LONG, 0, 0);
                LookupAccountNameA(NULL, bufDomain, NULL, bufSize, NULL, buf2Size, nameUse);

                MemorySegment dSid = scope.allocate(bufSize.get(C_LONG, 0));
                MemorySegment domainSidName = scope.allocate(buf2Size.get(C_LONG, 0));
                if (LookupAccountNameAGLE(NULL, bufDomain,
                        dSid, bufSize, domainSidName, buf2Size, nameUse) == 0) {
                    // ok not to have a domain SID (no error)
                } else {
                    tmpDomainSID = getTextSid(dSid);
                    if (debug) {
                        System.out.println("domainSID = " + tmpDomainSID);
                    }
                }

                // getPrimaryGroup
                if (debug) {
                    System.out.println("getting primary groups");
                }

                bufSize.set(C_LONG, 0, 0);
                GetTokenInformation(tokenHandle, TokenPrimaryGroup(), NULL, 0, bufSize);
                size = bufSize.get(C_LONG, 0);
                MemorySegment primaryGroupInfo = scope.allocate(size);
                if (GetTokenInformationGLE(tokenHandle, TokenPrimaryGroup(),
                        primaryGroupInfo, size, bufSize) == 0) {
                    return;
                }
                tmpPrimaryGroupID = getTextSid(primaryGroupInfo.get(C_POINTER, 0));
                if (debug) {
                    System.out.println("primaryGroup = " + tmpPrimaryGroupID);
                }

                // getGroups
                if (debug) {
                    System.out.println("getting supplementary groups");
                }

                bufSize.set(C_LONG, 0, 0);
                GetTokenInformation(tokenHandle, TokenGroups(), NULL, 0, bufSize);
                size = bufSize.get(C_INT, 0);
                MemorySegment groupsInfo = scope.allocate(size);
                if (GetTokenInformationGLE(tokenHandle,
                        TokenGroups(), groupsInfo, size, bufSize) == 0) {
                    return;
                }
                int numGroups = _TOKEN_GROUPS.GroupCount(groupsInfo);

                String[] allGroups = new String[numGroups];
                int pos = 0;
                for (int i = 0; i < numGroups; i++) {
                    MemorySegment groups = groupsInfo.asSlice(
                            _TOKEN_GROUPS.Groups$offset() + i * _SID_AND_ATTRIBUTES.sizeof(),
                            _TOKEN_GROUPS.Groups$layout().byteSize());
                    String g = getTextSid(_SID_AND_ATTRIBUTES.Sid(groups));
                    if (debug) {
                        System.out.println("group " + i + " = " + g);
                    }
                    if (g.equals(tmpPrimaryGroupID)) {
                        if (debug) {
                            System.out.println("skip primary group");
                        }
                    } else {
                        allGroups[pos++] = g;
                    }
                }
                if (pos == 0) {
                    if (debug) {
                        System.out.println("no secondary groups");
                    }
                    tmpGroupIDs = null;
                } else if (pos != numGroups) {
                    tmpGroupIDs = Arrays.copyOf(allGroups, pos);
                } else {
                    tmpGroupIDs = allGroups;
                }
                succeed = true;
            } finally {
                if (tokenHandle != null) {
                    CloseHandle(tokenHandle);
                }
                // Only fill in the fields when all info (except for domain sid)
                // are available.
                if (succeed) {
                    userName = tmpUserName;
                    userSID = tmpUserSID;
                    domain = tmpDomain;
                    domainSID = tmpDomainSID;
                    primaryGroupID = tmpPrimaryGroupID;
                    groupIDs = tmpGroupIDs;
                }
            }
        }
    }

    private static String getTextSid(MemorySegment sid) {
        String textSid;
        if (IsValidSid(sid) == 0) {
            return null;
        }
        MemorySegment sia = GetSidIdentifierAuthority(sid);
        byte subCC = GetSidSubAuthorityCount(sid).get(C_CHAR, 0);
        StringBuilder sb = new StringBuilder("S-1-");
        sb.append(sia.get(C_CHAR, 5) & 0xff
                + ((sia.get(C_CHAR, 4) & 0xff) << 8)
                + ((sia.get(C_CHAR, 3) & 0xff) << 16)
                + ((sia.get(C_CHAR, 2) & 0xff) << 24));
        for (int i = 0; i < subCC; i++) {
            sb.append('-').append(Integer.toUnsignedLong(
                    (GetSidSubAuthority(sid, i)).get(C_INT, 0)));
        }
        textSid = sb.toString();
        return textSid;
    }

    private long getImpersonationToken0() {
        if (debug) {
            System.out.println("getting impersonation token");
        }
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment pDupToken = scope.allocate(HANDLE);
            if (OpenThreadTokenGLE(GetCurrentThread(),
                    TOKEN_DUPLICATE(), 0, pDupToken) == 0) {
                if (OpenProcessToken(GetCurrentProcess(), TOKEN_DUPLICATE(), pDupToken) == 0) {
                    return 0;
                }
            }
            MemorySegment impersonationToken = scope.allocate(HANDLE);
            MemorySegment dupToken = pDupToken.get(HANDLE, 0);
            if (DuplicateTokenGLE(dupToken, SecurityImpersonation(),
                    impersonationToken) == 0) {
                return 0;
            }
            CloseHandle(dupToken);
            return impersonationToken.get(JAVA_LONG, 0);
        }
    }

    private boolean debug;

    private String userName;
    private String domain;
    private String domainSID;
    private String userSID;
    private String[] groupIDs;
    private String primaryGroupID;

    private long   impersonationToken;

    /**
     * Instantiate an {@code NTSystem} and load
     * the native library to access the underlying system information.
     */
    public NTSystem() {
        this(false);
    }

    /**
     * Instantiate an {@code NTSystem} and load
     * the native library to access the underlying system information.
     */
    NTSystem(boolean debug) {
        this.debug = debug;
        getCurrent();
    }

    /**
     * Get the username for the current NT user.
     *
     * @return the username for the current NT user.
     */
    public String getName() {
        return userName;
    }

    /**
     * Get the domain for the current NT user.
     *
     * @return the domain for the current NT user.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Get a printable SID for the current NT user's domain.
     *
     * @return a printable SID for the current NT user's domain.
     */
    public String getDomainSID() {
        return domainSID;
    }

    /**
     * Get a printable SID for the current NT user.
     *
     * @return a printable SID for the current NT user.
     */
    public String getUserSID() {
        return userSID;
    }

    /**
     * Get a printable primary group SID for the current NT user.
     *
     * @return the primary group SID for the current NT user.
     */
    public String getPrimaryGroupID() {
        return primaryGroupID;
    }

    /**
     * Get the printable group SIDs for the current NT user.
     *
     * @return the group SIDs for the current NT user.
     */
    public String[] getGroupIDs() {
        return groupIDs == null ? null : groupIDs.clone();
    }

    /**
     * Get an impersonation token for the current NT user.
     *
     * @return an impersonation token for the current NT user.
     */
    public synchronized long getImpersonationToken() {
        if (impersonationToken == 0) {
            impersonationToken = getImpersonationToken0();
        }
        return impersonationToken;
    }
}
