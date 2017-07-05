/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic.iiop;

import java.io.IOException;
import sun.tools.java.CompilerError;
import sun.tools.java.ClassDefinition;
import sun.rmi.rmic.IndentingWriter;

/**
 * InterfaceType is an abstract base representing any non-special
 * interface type.
 *
 * @author      Bryan Atsatt
 */
public abstract class InterfaceType extends CompoundType {

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Print this type.
     * @param writer The stream to print to.
     * @param useQualifiedNames If true, print qualified names; otherwise, print unqualified names.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     * @param globalIDLNames If true and useIDLNames true, prepends "::".
     */
    public void print ( IndentingWriter writer,
                        boolean useQualifiedNames,
                        boolean useIDLNames,
                        boolean globalIDLNames) throws IOException {

        if (isInner()) {
            writer.p("// " + getTypeDescription() + " (INNER)");
        } else {
            writer.p("// " + getTypeDescription() + "");
        }
        writer.pln(" (" + getRepositoryID() + ")\n");
        printPackageOpen(writer,useIDLNames);

        if (!useIDLNames) {
            writer.p("public ");
        }

        writer.p("interface " + getTypeName(false,useIDLNames,false));
        printImplements(writer,"",useQualifiedNames,useIDLNames,globalIDLNames);
        writer.plnI(" {");
        printMembers(writer,useQualifiedNames,useIDLNames,globalIDLNames);
        writer.pln();
        printMethods(writer,useQualifiedNames,useIDLNames,globalIDLNames);
        writer.pln();

        if (useIDLNames) {
            writer.pOln("};");
        } else {
            writer.pOln("}");
        }
        printPackageClose(writer,useIDLNames);
    }

    //_____________________________________________________________________
    // Subclass/Internal Interfaces
    //_____________________________________________________________________

    /**
     * Create a InterfaceType instance for the given class. NOTE: This constructor
     * is ONLY for SpecialInterfaceType.
     */
    protected InterfaceType(ContextStack stack, int typeCode, ClassDefinition classDef) {
        super(stack,typeCode,classDef); // Call special parent constructor.

        if ((typeCode & TM_INTERFACE) == 0 || ! classDef.isInterface()) {
            throw new CompilerError("Not an interface");
        }
    }

    /**
     * Create a InterfaceType instance for the given class.  The resulting
     * object is not yet completely initialized. Subclasses must call
     * initialize(directInterfaces,directInterfaces,directConstants);
     */
    protected InterfaceType(ContextStack stack,
                            ClassDefinition classDef,
                            int typeCode) {
        super(stack,classDef,typeCode);

        if ((typeCode & TM_INTERFACE) == 0 || ! classDef.isInterface()) {
            throw new CompilerError("Not an interface");
        }
    }
}
