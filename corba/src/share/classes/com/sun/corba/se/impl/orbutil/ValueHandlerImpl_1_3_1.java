/*
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.impl.orbutil;

import org.omg.CORBA.TCKind;

/**
 * This class overrides behavior of our current ValueHandlerImpl to
 * provide backwards compatibility with JDK 1.3.1.
 */
public class ValueHandlerImpl_1_3_1
    extends com.sun.corba.se.impl.io.ValueHandlerImpl
{
    public ValueHandlerImpl_1_3_1() {}

    public ValueHandlerImpl_1_3_1(boolean isInputStream) {
        super(isInputStream);
    }

    /**
     * Our JDK 1.3 and JDK 1.3.1 behavior subclasses override this.
     * The correct behavior is for a Java char to map to a CORBA wchar,
     * but our older code mapped it to a CORBA char.
     */
    protected TCKind getJavaCharTCKind() {
        return TCKind.tk_char;
    }

    /**
     * RepositoryId_1_3_1 performs an incorrect repId calculation
     * when using serialPersistentFields and one of the fields no longer
     * exists on the class itself.
     */
    public boolean useFullValueDescription(Class clazz, String repositoryID)
        throws java.io.IOException
    {
        return RepositoryId_1_3_1.useFullValueDescription(clazz, repositoryID);
    }

    /**
     * Installs the legacy IIOPOutputStream_1_3_1 which does
     * PutFields/GetFields incorrectly.  Bug 4407244.
     */
    protected final String getOutputStreamClassName() {
        return "com.sun.corba.se.impl.orbutil.IIOPOutputStream_1_3_1";
    }

    /**
     * Installs the legacy IIOPInputStream_1_3_1 which does
     * PutFields/GetFields incorrectly.  Bug 4407244.
     */
    protected final String getInputStreamClassName() {
        return "com.sun.corba.se.impl.orbutil.IIOPInputStream_1_3_1";
    }
}
