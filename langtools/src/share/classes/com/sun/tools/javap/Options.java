/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javap;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.sun.tools.classfile.AccessFlags;

/*
 *  Provides access to javap's options, set via the command line
 *  or JSR 199 API.
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Options {
    public static Options instance(Context context) {
        Options instance = context.get(Options.class);
        if (instance == null)
            instance = new Options(context);
        return instance;
    }

    protected Options(Context context) {
        context.put(Options.class, this);
    }

    /**
     * Checks access of class, field or method.
     */
    public boolean checkAccess(AccessFlags flags){

        boolean isPublic = flags.is(AccessFlags.ACC_PUBLIC);
        boolean isProtected = flags.is(AccessFlags.ACC_PROTECTED);
        boolean isPrivate = flags.is(AccessFlags.ACC_PRIVATE);
        boolean isPackage = !(isPublic || isProtected || isPrivate);

        if ((showAccess == AccessFlags.ACC_PUBLIC) && (isProtected || isPrivate || isPackage))
            return false;
        else if ((showAccess == AccessFlags.ACC_PROTECTED) && (isPrivate || isPackage))
            return false;
        else if ((showAccess == 0) && (isPrivate))
            return false;
        else
            return true;
    }

    public boolean help;
    public boolean verbose;
    public boolean version;
    public boolean fullVersion;
    public boolean showFlags;
    public boolean showLineAndLocalVariableTables;
    public int showAccess;
    public Set<String> accessOptions = new HashSet<String>();
    public Set<InstructionDetailWriter.Kind> details = EnumSet.noneOf(InstructionDetailWriter.Kind.class);
    public boolean showDisassembled;
    public boolean showInternalSignatures;
    public boolean showAllAttrs;
    public boolean showConstants;
    public boolean sysInfo;

    public boolean compat;             // bug-for-bug compatibility mode with old javap
    public boolean jsr277;
}
