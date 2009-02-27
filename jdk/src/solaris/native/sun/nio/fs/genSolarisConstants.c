/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
    DEFX(ACE_ACCESS_ALLOWED_ACE_TYPE);
    DEFX(ACE_ACCESS_DENIED_ACE_TYPE);
    DEFX(ACE_SYSTEM_AUDIT_ACE_TYPE);
    DEFX(ACE_SYSTEM_ALARM_ACE_TYPE);
    DEFX(ACE_READ_DATA);
    DEFX(ACE_LIST_DIRECTORY);
    DEFX(ACE_WRITE_DATA);
    DEFX(ACE_ADD_FILE);
    DEFX(ACE_APPEND_DATA);
    DEFX(ACE_ADD_SUBDIRECTORY);
    DEFX(ACE_READ_NAMED_ATTRS);
    DEFX(ACE_WRITE_NAMED_ATTRS);
    DEFX(ACE_EXECUTE);
    DEFX(ACE_DELETE_CHILD);
    DEFX(ACE_READ_ATTRIBUTES);
    DEFX(ACE_WRITE_ATTRIBUTES);
    DEFX(ACE_DELETE);
    DEFX(ACE_READ_ACL);
    DEFX(ACE_WRITE_ACL);
    DEFX(ACE_WRITE_OWNER);
    DEFX(ACE_SYNCHRONIZE);
    DEFX(ACE_FILE_INHERIT_ACE);
    DEFX(ACE_DIRECTORY_INHERIT_ACE);
    DEFX(ACE_NO_PROPAGATE_INHERIT_ACE);
    DEFX(ACE_INHERIT_ONLY_ACE);
    DEFX(ACE_SUCCESSFUL_ACCESS_ACE_FLAG);
    DEFX(ACE_FAILED_ACCESS_ACE_FLAG);
    DEFX(ACE_IDENTIFIER_GROUP);
    DEFX(ACE_OWNER);
    DEFX(ACE_GROUP);
    DEFX(ACE_EVERYONE);

    out("}                                                                              ");
    return 0;
}
