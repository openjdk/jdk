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

import sun.tools.java.ClassNotFound;
import sun.tools.java.CompilerError;
import sun.tools.java.Identifier;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.ClassDefinition;

/**
 * SpecialInterfaceType represents any one of the following types:
 * <pre>
 *    java.rmi.Remote
 *    java.io.Serializable
 *    java.io.Externalizable
 *    org.omg.CORBA.Object
 *    org.omg.CORBA.portable.IDLEntity
 * </pre>
 * all of which are treated as special cases. For all but CORBA.Object,
 * the type must match exactly. For CORBA.Object, the type must either be
 * CORBA.Object or inherit from it.
 * <p>
 * The static forSpecial(...) method must be used to obtain an instance, and
 * will return null if the type is non-conforming.
 *
 * @author  Bryan Atsatt
 */
public class SpecialInterfaceType extends InterfaceType {

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Create a SpecialInterfaceType object for the given class.
     *
     * If the class is not a properly formed or if some other error occurs, the
     * return value will be null, and errors will have been reported to the
     * supplied BatchEnvironment.
     */
    public static SpecialInterfaceType forSpecial ( ClassDefinition theClass,
                                                    ContextStack stack) {

        if (stack.anyErrors()) return null;

        // Do we already have it?

        sun.tools.java.Type type = theClass.getType();
        Type existing = getType(type,stack);

        if (existing != null) {

            if (!(existing instanceof SpecialInterfaceType)) return null; // False hit.

            // Yep, so return it...

            return (SpecialInterfaceType) existing;
        }

        // Is it special?

        if (isSpecial(type,theClass,stack)) {

            // Yes...

            SpecialInterfaceType result = new SpecialInterfaceType(stack,0,theClass);
            putType(type,result,stack);
            stack.push(result);

            if (result.initialize(type,stack)) {
                stack.pop(true);
                return result;
            } else {
                removeType(type,stack);
                stack.pop(false);
                return null;
            }
        }
        return null;
    }

    /**
     * Return a string describing this type.
     */
    public String getTypeDescription () {
        return "Special interface";
    }

    //_____________________________________________________________________
    // Subclass/Internal Interfaces
    //_____________________________________________________________________

    /**
     * Create an SpecialInterfaceType instance for the given class.
     */
    private SpecialInterfaceType(ContextStack stack, int typeCode,
                                 ClassDefinition theClass) {
        super(stack,typeCode | TM_SPECIAL_INTERFACE | TM_INTERFACE | TM_COMPOUND, theClass);
        setNames(theClass.getName(),null,null); // Fixed in initialize.
    }

    private static boolean isSpecial(sun.tools.java.Type type,
                                     ClassDefinition theClass,
                                     ContextStack stack) {
        if (type.isType(TC_CLASS)) {
            Identifier id = type.getClassName();

            if (id.equals(idRemote)) return true;
            if (id == idJavaIoSerializable) return true;
            if (id == idJavaIoExternalizable) return true;
            if (id == idCorbaObject) return true;
            if (id == idIDLEntity) return true;
            BatchEnvironment env = stack.getEnv();
            try {
                if (env.defCorbaObject.implementedBy(env,theClass.getClassDeclaration())) return true;
            } catch (ClassNotFound e) {
                classNotFound(stack,e);
            }
        }
        return false;
    }

    private boolean initialize(sun.tools.java.Type type, ContextStack stack) {

        int typeCode = TYPE_NONE;
        Identifier id = null;
        String idlName = null;
        String[] idlModuleName = null;
        boolean constant = stack.size() > 0 && stack.getContext().isConstant();

        if (type.isType(TC_CLASS)) {
            id = type.getClassName();

            if (id.equals(idRemote)) {
                typeCode = TYPE_JAVA_RMI_REMOTE;
                idlName = IDL_JAVA_RMI_REMOTE;
                idlModuleName = IDL_JAVA_RMI_MODULE;
            } else if (id == idJavaIoSerializable) {
                typeCode = TYPE_ANY;
                idlName = IDL_SERIALIZABLE;
                idlModuleName = IDL_JAVA_IO_MODULE;
            } else if (id == idJavaIoExternalizable) {
                typeCode = TYPE_ANY;
                idlName = IDL_EXTERNALIZABLE;
                idlModuleName = IDL_JAVA_IO_MODULE;
            } else if (id == idIDLEntity) {
                typeCode = TYPE_ANY;
                idlName = IDL_IDLENTITY;
                idlModuleName = IDL_ORG_OMG_CORBA_PORTABLE_MODULE;
            } else {

                typeCode = TYPE_CORBA_OBJECT;

                // Is it exactly org.omg.CORBA.Object?

                if (id == idCorbaObject) {

                    // Yes, so special case...

                    idlName = IDLNames.getTypeName(typeCode,constant);
                    idlModuleName = null;

                } else {

                    // No, so get the correct names...

                    try {

                        // These can fail if we get case-sensitive name matches...

                        idlName = IDLNames.getClassOrInterfaceName(id,env);
                        idlModuleName = IDLNames.getModuleNames(id,isBoxed(),env);

                    } catch (Exception e) {
                        failedConstraint(7,false,stack,id.toString(),e.getMessage());
                        throw new CompilerError("");
                    }
                }
            }
        }

        if (typeCode == TYPE_NONE) {
            return false;
        }

        // Reset type code...

        setTypeCode(typeCode | TM_SPECIAL_INTERFACE | TM_INTERFACE | TM_COMPOUND);

        // Set names

        if (idlName == null) {
            throw new CompilerError("Not a special type");
        }

        setNames(id,idlModuleName,idlName);

        // Initialize CompoundType...

        return initialize(null,null,null,stack,false);
    }
}
