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

import java.util.Vector;
import java.util.HashSet;
import sun.tools.java.CompilerError;
import sun.tools.java.Identifier;
import sun.tools.java.ClassDefinition;
import java.lang.reflect.Array;

/**
 * ArrayType is a wrapper for any of the other types. The getElementType()
 * method can be used to get the array element type.  The getArrayDimension()
 * method can be used to get the array dimension.
 *
 * @author      Bryan Atsatt
 */
public class ArrayType extends Type {

    private Type type;
    private int arrayDimension;
    private String brackets;
    private String bracketsSig;

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Create an ArrayType object for the given type.
     *
     * If the class is not a properly formed or if some other error occurs, the
     * return value will be null, and errors will have been reported to the
     * supplied BatchEnvironment.
     */
    public static ArrayType forArray(   sun.tools.java.Type theType,
                                        ContextStack stack) {


        ArrayType result = null;
        sun.tools.java.Type arrayType = theType;

        if (arrayType.getTypeCode() == TC_ARRAY) {

            // Find real type...

            while (arrayType.getTypeCode() == TC_ARRAY) {
                arrayType = arrayType.getElementType();
            }

            // Do we already have it?

            Type existing = getType(theType,stack);
            if (existing != null) {

                if (!(existing instanceof ArrayType)) return null; // False hit.

                                // Yep, so return it...

                return (ArrayType) existing;
            }

            // Now try to make a Type from it...

            Type temp = CompoundType.makeType(arrayType,null,stack);

            if (temp != null) {

                                // Got a valid one. Make an array type...

                result = new ArrayType(stack,temp,theType.getArrayDimension());

                                // Add it...

                putType(theType,result,stack);

                // Do the stack thing in case tracing on...

                stack.push(result);
                stack.pop(true);
            }
        }

        return result;
    }

    /**
     * Return signature for this type  (e.g. com.acme.Dynamite
     * would return "com.acme.Dynamite", byte = "B")
     */
    public String getSignature() {
        return bracketsSig + type.getSignature();
    }

    /**
     * Get element type. Returns null if not an array.
     */
    public Type getElementType () {
        return type;
    }

    /**
     * Get array dimension. Returns zero if not an array.
     */
    public int getArrayDimension () {
        return arrayDimension;
    }

    /**
     * Get brackets string. Returns "" if not an array.
     */
    public String getArrayBrackets () {
        return brackets;
    }

    /**
     * Return a string representation of this type.
     */
    public String toString () {
        return getQualifiedName() + brackets;
    }

    /**
     * Return a string describing this type.
     */
    public String getTypeDescription () {
        return "Array of " + type.getTypeDescription();
    }


    /**
     * Return the name of this type. For arrays, will include "[]" if useIDLNames == false.
     * @param useQualifiedNames If true, print qualified names; otherwise, print unqualified names.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     * @param globalIDLNames If true and useIDLNames true, prepends "::".
     */
    public String getTypeName ( boolean useQualifiedNames,
                                boolean useIDLNames,
                                boolean globalIDLNames) {
        if (useIDLNames) {
            return super.getTypeName(useQualifiedNames,useIDLNames,globalIDLNames);
        } else {
            return super.getTypeName(useQualifiedNames,useIDLNames,globalIDLNames) + brackets;
        }
    }

    //_____________________________________________________________________
    // Subclass/Internal Interfaces
    //_____________________________________________________________________


    /**
     * Convert all invalid types to valid ones.
     */
    protected void swapInvalidTypes () {
        if (type.getStatus() != STATUS_VALID) {
            type = getValidType(type);
        }
    }

    /*
     * Add matching types to list. Return true if this type has not
     * been previously checked, false otherwise.
     */
    protected boolean addTypes (int typeCodeFilter,
                                HashSet checked,
                                Vector matching) {

        // Check self.

        boolean result = super.addTypes(typeCodeFilter,checked,matching);

        // Have we been checked before?

        if (result) {

            // No, so add element type...

            getElementType().addTypes(typeCodeFilter,checked,matching);
        }

        return result;
    }

    /**
     * Create an ArrayType instance for the given type.  The resulting
     * object is not yet completely initialized.
     */
    private ArrayType(ContextStack stack, Type type, int arrayDimension) {
        super(stack,TYPE_ARRAY);
        this.type = type;
        this.arrayDimension = arrayDimension;

        // Create our brackets string...

        brackets = "";
        bracketsSig = "";
        for (int i = 0; i < arrayDimension; i ++) {
            brackets += "[]";
            bracketsSig += "[";
        }

        // Now set our names...

        String idlName = IDLNames.getArrayName(type,arrayDimension);
        String[] module = IDLNames.getArrayModuleNames(type);
        setNames(type.getIdentifier(),module,idlName);

        // Set our repositoryID...

        setRepositoryID();
    }


    /*
     * Load a Class instance. Return null if fail.
     */
    protected Class loadClass() {
        Class result = null;
        Class elementClass = type.getClassInstance();
        if (elementClass != null) {
            result = Array.newInstance(elementClass, new int[arrayDimension]).getClass();
        }
        return result;
    }

    /**
     * Release all resources
     */
    protected void destroy () {
        super.destroy();
        if (type != null) {
            type.destroy();
            type = null;
        }
        brackets = null;
        bracketsSig = null;
    }
}
