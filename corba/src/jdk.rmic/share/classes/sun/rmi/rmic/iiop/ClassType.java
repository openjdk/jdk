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

import sun.tools.java.CompilerError;
import sun.tools.java.ClassNotFound;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.ClassDefinition;
import sun.rmi.rmic.IndentingWriter;
import java.io.IOException;

/**
 * ClassType is an abstract base representing any non-special class
 * type.
 *
 * @author      Bryan Atsatt
 */
public abstract class ClassType extends CompoundType {

    private ClassType parent;

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Return the parent class of this type. Returns null if this
     * type is an interface or if there is no parent.
     */
    public ClassType getSuperclass() {
        return parent;
    }


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
            writer.p("// " + getTypeDescription());
        }
        writer.pln(" (" + getRepositoryID() + ")\n");

        printPackageOpen(writer,useIDLNames);

        if (!useIDLNames) {
            writer.p("public ");
        }

        String prefix = "";
        writer.p("class " + getTypeName(false,useIDLNames,false));
        if (printExtends(writer,useQualifiedNames,useIDLNames,globalIDLNames)) {
            prefix = ",";
        }
        printImplements(writer,prefix,useQualifiedNames,useIDLNames,globalIDLNames);
        writer.plnI(" {");
        printMembers(writer,useQualifiedNames,useIDLNames,globalIDLNames);
        writer.pln();
        printMethods(writer,useQualifiedNames,useIDLNames,globalIDLNames);

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

    protected void destroy () {
        if (!destroyed) {
            super.destroy();
            if (parent != null) {
                parent.destroy();
                parent = null;
    }
    }
        }

    /**
     * Create a ClassType instance for the given class. NOTE: This constructor
     * is ONLY for SpecialClassType.
     */
    protected ClassType(ContextStack stack, int typeCode, ClassDefinition classDef) {
        super(stack,typeCode,classDef); // Call special parent constructor.
        if ((typeCode & TM_CLASS) == 0 && classDef.isInterface()) {
            throw new CompilerError("Not a class");
        }
        parent = null;
    }

    /**
     * Create a ClassType instance for the given class. NOTE: This constructor
     * is ONLY for ImplementationType. It does not walk the parent chain.
     */
    protected ClassType(int typeCode, ClassDefinition classDef,ContextStack stack) {
        super(stack,classDef,typeCode);

        if ((typeCode & TM_CLASS) == 0 && classDef.isInterface()) {
            throw new CompilerError("Not a class");
        }
        parent = null;
    }

    /**
     * Create an ClassType instance for the given class.  The resulting
     * object is not yet completely initialized. Subclasses must call
     * initialize(directInterfaces,directInterfaces,directConstants);
     */
    protected ClassType(ContextStack stack,
                        ClassDefinition classDef,
                        int typeCode) {
        super(stack,classDef,typeCode);
        if ((typeCode & TM_CLASS) == 0 && classDef.isInterface()) {
            throw new CompilerError("Not a class");
        }
        parent = null;
    }

    /**
     * Convert all invalid types to valid ones.
     */
    protected void swapInvalidTypes () {
        super.swapInvalidTypes();
        if (parent != null && parent.getStatus() != STATUS_VALID) {
            parent = (ClassType) getValidType(parent);
        }
    }

    /**
     * Modify the type description with exception info.
     */
    public String addExceptionDescription (String typeDesc) {
        if (isException) {
            if (isCheckedException) {
                typeDesc = typeDesc + " - Checked Exception";
            } else {
                typeDesc = typeDesc + " - Unchecked Exception";
            }
        }
        return typeDesc;
    }


    protected boolean initParents(ContextStack stack) {

        stack.setNewContextCode(ContextStack.EXTENDS);
        BatchEnvironment env = stack.getEnv();

        // Init parent...

        boolean result = true;

        try {
            ClassDeclaration parentDecl = getClassDefinition().getSuperClass(env);
            if (parentDecl != null) {
                ClassDefinition parentDef = parentDecl.getClassDefinition(env);
                parent = (ClassType) makeType(parentDef.getType(),parentDef,stack);
                if (parent == null) {
                    result = false;
                }
            }
        } catch (ClassNotFound e) {
            classNotFound(stack,e);
            throw new CompilerError("ClassType constructor");
        }

        return result;
    }
}
