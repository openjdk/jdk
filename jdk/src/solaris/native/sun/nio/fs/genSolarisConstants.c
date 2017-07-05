/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <errno.h>
#include <unistd.h>
#include <sys/acl.h>
#include <sys/fcntl.h>
#include <sys/stat.h>

/**
 * Generates sun.nio.fs.SolarisConstants
 */

static void out(char* s) {
    printf("%s\n", s);
}

static void emit(char* name, int value) {
    printf("    static final int %s = %d;\n", name, value);
}

static void emitX(char* name, int value) {
    printf("    static final int %s = 0x%x;\n", name, value);
}

#define DEF(X) emit(#X, X);
#define DEFX(X) emitX(#X, X);

int main(int argc, const char* argv[]) {
    out("// AUTOMATICALLY GENERATED FILE - DO NOT EDIT                                  ");
    out("package sun.nio.fs;                                                            ");
    out("class SolarisConstants {                                                       ");
    out("    private SolarisConstants() { }                                             ");

    // extended attributes
    DEFX(O_XATTR);
    DEF(_PC_XATTR_ENABLED);

    // ACL configuration
    DEF(_PC_ACL_ENABLED);
    DEFX(_ACL_ACE_ENABLED);

    // ACL commands
    DEFX(ACE_GETACL);
    DEFX(ACE_SETACL);

    // ACL mask/flags/types
    emitX("ACE_ACCESS_ALLOWED_ACE_TYPE",        0x0000);
    emitX("ACE_ACCESS_DENIED_ACE_TYPE",         0x0001);
    emitX("ACE_SYSTEM_AUDIT_ACE_TYPE",          0x0002);
    emitX("ACE_SYSTEM_ALARM_ACE_TYPE",          0x0003);
    emitX("ACE_READ_DATA",                      0x00000001);
    emitX("ACE_LIST_DIRECTORY",                 0x00000001);
    emitX("ACE_WRITE_DATA",                     0x00000002);
    emitX("ACE_ADD_FILE",                       0x00000002);
    emitX("ACE_APPEND_DATA",                    0x00000004);
    emitX("ACE_ADD_SUBDIRECTORY",               0x00000004);
    emitX("ACE_READ_NAMED_ATTRS",               0x00000008);
    emitX("ACE_WRITE_NAMED_ATTRS",              0x00000010);
    emitX("ACE_EXECUTE",                        0x00000020);
    emitX("ACE_DELETE_CHILD",                   0x00000040);
    emitX("ACE_READ_ATTRIBUTES",                0x00000080);
    emitX("ACE_WRITE_ATTRIBUTES",               0x00000100);
    emitX("ACE_DELETE",                         0x00010000);
    emitX("ACE_READ_ACL",                       0x00020000);
    emitX("ACE_WRITE_ACL",                      0x00040000);
    emitX("ACE_WRITE_OWNER",                    0x00080000);
    emitX("ACE_SYNCHRONIZE",                    0x00100000);
    emitX("ACE_FILE_INHERIT_ACE",               0x0001);
    emitX("ACE_DIRECTORY_INHERIT_ACE",          0x0002);
    emitX("ACE_NO_PROPAGATE_INHERIT_ACE",       0x0004);
    emitX("ACE_INHERIT_ONLY_ACE",               0x0008);
    emitX("ACE_SUCCESSFUL_ACCESS_ACE_FLAG",     0x0010);
    emitX("ACE_FAILED_ACCESS_ACE_FLAG",         0x0020);
    emitX("ACE_IDENTIFIER_GROUP",               0x0040);
    emitX("ACE_OWNER",                          0x1000);
    emitX("ACE_GROUP",                          0x2000);
    emitX("ACE_EVERYONE",                       0x4000);

    out("}                                                                              ");
    return 0;
}
