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
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.IOException;
import sun.tools.java.ClassDefinition;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.Identifier;
import sun.tools.java.ClassNotFound;
import sun.tools.java.CompilerError;
import sun.rmi.rmic.IndentingWriter;
import java.util.HashSet;
import com.sun.corba.se.impl.util.RepositoryId;
import sun.rmi.rmic.Names;

/**
 * Type is an abstract base class for a family of types which provide
 * conformance checking and name mapping as defined in the "Java to IDL
 * Mapping" OMG specification.  The family is composed of the following
 * fixed set of types:
 * <pre>{@literal
 *
 *                                              +- RemoteType <-- AbstractType
 *                                              |
 *                           +- InterfaceType <-+- SpecialInterfaceType
 *         +- PrimitiveType  |                  |
 *         |                 |                  +- NCInterfaceType
 *  Type <-+- CompoundType <-|
 *         |                 |                  +- ValueType
 *         +- ArrayType      |                  |
 *                           +- ClassType <-----+- ImplementationType
 *                                              |
 *                                              +- SpecialClassType
 *                                              |
 *                                              +- NCClassType
 *
 * }</pre>
 * PrimitiveType represents a primitive or a void type.
 * <p>
 * CompoundType is an abstract base representing any non-special class
 * or interface type.
 * <p>
 * InterfaceType is an abstract base representing any non-special
 * interface type.
 * <p>
 * RemoteType represents any non-special interface which inherits
 * from java.rmi.Remote.
 * <p>
 * AbstractType represents any non-special interface which does not
 * inherit from java.rmi.Remote, for which all methods throw RemoteException.
 * <p>
 * SpecialInterfaceType represents any one of the following types:
 * <pre>
 *    java.rmi.Remote
 *    java.io.Serializable
 *    java.io.Externalizable
 * </pre>
 * all of which are treated as special cases.
 * <p>
 * NCInterfaceType represents any non-special, non-conforming interface.
 * <p>
 * ClassType is an abstract base representing any non-special class
 * type.
 * <p>
 * ValueType represents any non-special class which does inherit from
 * java.io.Serializable and does not inherit from java.rmi.Remote.
 * <p>
 * ImplementationType represents any non-special class which implements
 * one or more interfaces which inherit from java.rmi.Remote.
 * <p>
 * SpecialClassType represents any one of the following types:
 * <pre>
 *    java.lang.Object
 *    java.lang.String
 *    org.omg.CORBA.Object
 * </pre>
 * all of which are treated as special cases. For all but CORBA.Object,
 * the type must match exactly. For CORBA.Object, the type must either be
 * CORBA.Object or inherit from it.
 * <p>
 * NCClassType represents any non-special, non-conforming class.
 * <p>
 * ArrayType is a wrapper for any of the other types. The getElementType()
 * method can be used to get the array element type.  The getArrayDimension()
 * method can be used to get the array dimension.
 * <p>
 * <i><strong>NOTE:</strong> None of these types is multi-thread-safe</i>
 * @author      Bryan Atsatt
 */
public abstract class Type implements sun.rmi.rmic.iiop.Constants, ContextElement, Cloneable {

    private int typeCode;
    private int fullTypeCode;
    private Identifier id;

    private String name;
    private String packageName;
    private String qualifiedName;

    private String idlName;
    private String[] idlModuleNames;
    private String qualifiedIDLName;

    private String repositoryID;
    private Class ourClass;

    private int status = STATUS_PENDING;

    protected BatchEnvironment env;     // Easy access for subclasses.
    protected ContextStack stack;       // Easy access for subclasses.

    protected boolean destroyed = false;

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Return the unqualified name for this type (e.g. com.acme.Dynamite would
     * return "Dynamite").
     */
    public String getName() {
        return name;
    }

    /**
     * Return the package of this type (e.g. com.acme.Dynamite would
     * return "com.acme"). Will return null if default package or
     * if this type is a primitive.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Return the fully qualified name of this type  (e.g. com.acme.Dynamite
     * would return "com.acme.Dynamite")
     */
    public String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * Return signature for this type  (e.g. com.acme.Dynamite
     * would return "com.acme.Dynamite", byte = "B")
     */
    public abstract String getSignature();

    /**
     * IDL_Naming
     * Return the unqualified IDL name of this type (e.g. com.acme.Dynamite would
     * return "Dynamite").
     */
    public String getIDLName() {
        return idlName;
    }

    /**
     * IDL_Naming
     * Return the IDL module name for this type (e.g. com.acme.Dynamite would return
     * a three element array of {"com","acme"). May be a zero length array if
     * there is no module name.
     */
    public String[] getIDLModuleNames() {
        return idlModuleNames;
    }

    /**
     * IDL_Naming
     * Return the fully qualified IDL name for this type (e.g. com.acme.Dynamite would
     * return "com::acme::Dynamite").
     * @param global If true, prepends "::".
     */
    public String getQualifiedIDLName(boolean global) {
        if (global && getIDLModuleNames().length > 0) {
            return IDL_NAME_SEPARATOR + qualifiedIDLName;
        } else {
            return qualifiedIDLName;
        }
    }

    /**
     * Return the identifier for this type. May be qualified.
     */
    public Identifier getIdentifier() {
        return id;
    }

    /**
     * Return the repository ID for this type.
     */
    public String getRepositoryID() {
        return repositoryID;
    }

    /**
     * Return the repository ID for this "boxed" type.
     */
    public String getBoxedRepositoryID() {
        return RepositoryId.createForJavaType(ourClass);
    }

    /**
     * Return the Class for this type.
     */
    public Class getClassInstance() {
        if (ourClass == null) {
            initClass();
        }
        return ourClass;
    }

    /**
     * Return the status of this type.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Set the status of this type.
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Return the compiler environment for this type.
     */
    public BatchEnvironment getEnv() {
        return env;
    }

    /**
     * Get type code, without modifiers. Type codes are defined in sun.rmi.rmic.iiop.Constants.
     */
    public int getTypeCode() {
        return typeCode;
    }

    /**
     * Get type code, with modifiers. Type codes are defined in sun.rmi.rmic.iiop.Constants.
     */
    public int getFullTypeCode() {
        return fullTypeCode;
    }

    /**
     * Get type code modifiers. Type codes are defined in sun.rmi.rmic.iiop.Constants.
     */
    public int getTypeCodeModifiers() {
        return fullTypeCode & TM_MASK;
    }

    /**
     * Check for a certain type. Type codes are defined in sun.rmi.rmic.iiop.Constants.
     * Returns true if all of the bits in typeCodeMask are present in the full type code
     * of this object.
     */
    public boolean isType(int typeCodeMask) {
        return (fullTypeCode & typeCodeMask) == typeCodeMask;
    }

    /**
     * Like isType(), but returns true if <em>any</em> of the bits in typeCodeMask are
     * present in the full type code of this object.
     */
    public boolean typeMatches(int typeCodeMask) {
        return (fullTypeCode & typeCodeMask) > 0;
    }


    /**
     * Return the fullTypeCode. If an array, returns the
     * type code from the element type.
     */
    public int getRootTypeCode() {
        if (isArray()) {
            return getElementType().getFullTypeCode();
        } else {
            return fullTypeCode;
        }
    }

    /**
     * Return true if this type is-a InterfaceType.
     */
    public boolean isInterface() {
        return (fullTypeCode & TM_INTERFACE) == TM_INTERFACE;
    }

    /**
     * Return true if this type is-a ClassType.
     */
    public boolean isClass() {
        return (fullTypeCode & TM_CLASS) == TM_CLASS;
    }

    /**
     * Return true if this type is-a inner class or interface.
     */
    public boolean isInner() {
        return (fullTypeCode & TM_INNER) == TM_INNER;
    }


    /**
     * Return true if this type is-a SpecialInterfaceType.
     */
    public boolean isSpecialInterface() {
        return (fullTypeCode & TM_SPECIAL_INTERFACE) == TM_SPECIAL_INTERFACE;
    }

    /**
     * Return true if this type is-a SpecialClassType.
     */
    public boolean isSpecialClass() {
        return (fullTypeCode & TM_SPECIAL_CLASS) == TM_SPECIAL_CLASS;
    }

    /**
     * Return true if this type is-a CompoundType.
     */
    public boolean isCompound() {
        return (fullTypeCode & TM_COMPOUND) == TM_COMPOUND;
    }

    /**
     * Return true if this type is-a PrimitiveType.
     */
    public boolean isPrimitive() {
        return (fullTypeCode & TM_PRIMITIVE) == TM_PRIMITIVE;
    }

    /**
     * Return true if this type is-a ArrayType.
     */
    public boolean isArray() {
        return (fullTypeCode & TYPE_ARRAY) == TYPE_ARRAY;
    }

    /**
     * Return true if this type is a conforming type.
     */
    public boolean isConforming() {
        return (fullTypeCode & TM_NON_CONFORMING) == TM_NON_CONFORMING;
    }

    /**
     * Return a string representation of this type.
     */
    public String toString () {
        return getQualifiedName();
    }

    /**
     * Get element type. Returns null if not an array.
     */
    public Type getElementType () {
        return null;
    }

    /**
     * Get array dimension. Returns zero if not an array.
     */
    public int getArrayDimension () {
        return 0;
    }

    /**
     * Get brackets string. Returns "" if not an array.
     */
    public String getArrayBrackets () {
        return "";
    }

    /**
     * Equality check based on the string representation.
     */
    public boolean equals(Object obj) {

        String us = toString();
        String them = ((Type)obj).toString();
        return us.equals(them);
    }

    /**
     * Collect all the matching types referenced directly or indirectly
     * by this type, including itself.
     * @param typeCodeFilter The typeCode to use as a filter.
     */
    public Type[] collectMatching (int typeCodeFilter) {
        return collectMatching(typeCodeFilter,new HashSet(env.allTypes.size()));
    }

    /**
     * Collect all the matching types referenced directly or indirectly
     * by this type, including itself.
     * @param typeCodeFilter The typeCode to use as a filter.
     * @param alreadyChecked Contains types which have previously been checked
     * and will be ignored. Updated during collection.
     */
    public Type[] collectMatching (int typeCodeFilter, HashSet alreadyChecked) {
        Vector matching = new Vector();

        // Fill up the list...

        addTypes(typeCodeFilter,alreadyChecked,matching);

        // Copy vector contents to array and return it...

        Type[] result = new Type[matching.size()];
        matching.copyInto(result);

        return result;
    }

    /**
     * Return a string describing this type.
     */
    public abstract String getTypeDescription ();

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
            if (useQualifiedNames) {
                return getQualifiedIDLName(globalIDLNames);
            } else {
                return getIDLName();
            }
        } else {
            if (useQualifiedNames) {
                return getQualifiedName();
            } else {
                return getName();
            }
        }
    }

    /**
     * Print all types referenced directly or indirectly by this type which
     * match the filter.
     * @param writer The stream to print to.
     * @param typeCodeFilter The type codes to print.
     * @param useQualifiedNames If true, print qualified names; otherwise, print unqualified names.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     * @param globalIDLNames If true and useIDLNames true, prepends "::".
     */
    public void print ( IndentingWriter writer,
                        int typeCodeFilter,
                        boolean useQualifiedNames,
                        boolean useIDLNames,
                        boolean globalIDLNames) throws IOException {

        Type[] theTypes = collectMatching(typeCodeFilter);
        print(writer,theTypes,useQualifiedNames,useIDLNames,globalIDLNames);
    }

    /**
     * Print an array of types.
     * @param writer The stream to print to.
     * @param theTypes The types to print.
     * @param useQualifiedNames If true, print qualified names; otherwise, print unqualified names.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     * @param globalIDLNames If true and useIDLNames true, prepends "::".
     */
    public static void print (  IndentingWriter writer,
                                Type[] theTypes,
                                boolean useQualifiedNames,
                                boolean useIDLNames,
                                boolean globalIDLNames) throws IOException {

        for (int i = 0; i < theTypes.length; i++) {
            theTypes[i].println(writer,useQualifiedNames,useIDLNames,globalIDLNames);
        }
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
        printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);
    }

    /**
     * Print this type, followed by a newline.
     * @param writer The stream to print to.
     * @param useQualifiedNames If true, print qualified names; otherwise, print unqualified names.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     * @param globalIDLNames If true and useIDLNames true, prepends "::".
     */
    public void println (       IndentingWriter writer,
                                boolean useQualifiedNames,
                                boolean useIDLNames,
                                boolean globalIDLNames) throws IOException  {

        print(writer,useQualifiedNames,useIDLNames,globalIDLNames);
        writer.pln();
    }



    /**
     * Print the name of this type.
     * @param writer The stream to print to.
     * @param useQualifiedNames If true, print qualified names; otherwise, print unqualified names.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     * @param globalIDLNames If true and useIDLNames true, prepends "::".
     */
    public void printTypeName ( IndentingWriter writer,
                                boolean useQualifiedNames,
                                boolean useIDLNames,
                                boolean globalIDLNames) throws IOException {

        writer.p(getTypeName(useQualifiedNames,useIDLNames,globalIDLNames));
    }

    /**
     * Return context element name.
     */
    public String getElementName() {
        return getQualifiedName();
    }

    //_____________________________________________________________________
    // Subclass Interfaces
    //_____________________________________________________________________

    /**
     * Print the "opening" of the package or module of this type.
     * @param writer The stream to print to.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     */
    protected void printPackageOpen (   IndentingWriter writer,
                                        boolean useIDLNames) throws IOException {

        if (useIDLNames) {
            String[] moduleNames = getIDLModuleNames();
            for (int i = 0; i < moduleNames.length; i++ ) {
                writer.plnI("module " + moduleNames[i] + " {");
            }
        } else {
            String packageName = getPackageName();
            if (packageName != null) {
                writer.pln("package " + packageName + ";");
            }
        }
    }

    /**
     * Get a type out of the table.
     */
    protected static Type getType (sun.tools.java.Type key, ContextStack stack) {
        return getType(key.toString(),stack);
    }

    /**
     * Get a type out of the table.
     */
    protected static Type getType (String key, ContextStack stack) {
        Type result = (Type) stack.getEnv().allTypes.get(key);

        if (result != null) {
            stack.traceExistingType(result);
        }

        return result;
    }

    /**
     * Remove a type from the table.
     */
    protected static void removeType (String key, ContextStack stack) {
        Type value = (Type) stack.getEnv().allTypes.remove(key);
        stack.getEnv().invalidTypes.put(value,key);
    }

    /**
     * Remove a type from the table.
     */
    protected static void removeType (sun.tools.java.Type key, ContextStack stack) {
        String theKey = key.toString();
        Type old = (Type) stack.getEnv().allTypes.remove(theKey);
        putInvalidType(old,theKey,stack);
    }

    /**
     * Put a type into the table.
     */
    protected static void putType (sun.tools.java.Type key, Type value, ContextStack stack) {
        stack.getEnv().allTypes.put(key.toString(),value);
    }

    /**
     * Put a type into the table.
     */
    protected static void putType (String key, Type value, ContextStack stack) {
        stack.getEnv().allTypes.put(key,value);
    }

    /**
     * Put an invalid type into the.
     */
    protected static void putInvalidType (Type key, String value, ContextStack stack) {
        stack.getEnv().invalidTypes.put(key,value);
    }


    /**
     * Remove all invalid types...
     */
    public void removeInvalidTypes () {
        if (env.invalidTypes.size() > 0) {
            env.invalidTypes.clear();
        }
    }

    /**
     * Walk all types and tell them to update invalid types...
     */
    protected static void updateAllInvalidTypes (ContextStack stack) {
        BatchEnvironment env = stack.getEnv();
        if (env.invalidTypes.size() > 0) {

            // Walk all types and swap invalid...

            for (Enumeration e = env.allTypes.elements() ; e.hasMoreElements() ;) {
                Type it = (Type) e.nextElement();
                it.swapInvalidTypes();
            }

            // Delete all invalidTypes...

            env.invalidTypes.clear();
        }
    }

    /**
     * Return count of previously parsed types.
     */
    protected int countTypes () {
        return env.allTypes.size();
    }

    /**
     * Reset types removes all previously parsed types.
     */
    void resetTypes () {
        env.reset();
    }

    /**
     * Release all resources.
     */
    protected void destroy () {
        if (!destroyed) {
            id = null;
            name = null;
            packageName = null;
            qualifiedName = null;
            idlName = null;
            idlModuleNames = null;
            qualifiedIDLName = null;
            repositoryID = null;
            ourClass = null;
            env = null;
            stack = null;
            destroyed = true;
        }
    }

    /**
     * Convert all invalid types to valid ones.
     */
    protected void swapInvalidTypes () {
    }

    /**
     * Convert an invalid type to a valid one.
     */
    protected Type getValidType (Type invalidType) {
        if (invalidType.getStatus() == STATUS_VALID) {
            return invalidType;
        }

        String key = (String)env.invalidTypes.get(invalidType);
        Type result = null;
        if (key != null) {
            result = (Type) env.allTypes.get(key);
        }

        if (result == null) {
            throw new Error("Failed to find valid type to swap for " + invalidType + " mis-identified as " + invalidType.getTypeDescription());
        }
        //System.out.println("Swapped " + result + " from " + invalidType.getTypeDescription()
        //    + " to " + result.getTypeDescription());
        //ContextStack.dumpCallStack();
        return result;
    }

    /**
     * Print the "closing" of the package or module of this type.
     * @param writer The stream to print to.
     * @param useIDLNames If true, print IDL names; otherwise, print java names.
     */
    protected void printPackageClose (  IndentingWriter writer,
                                        boolean useIDLNames) throws IOException {
        if (useIDLNames) {
            String[] moduleNames = getIDLModuleNames();
            for (int i = 0; i < moduleNames.length; i++ ) {
                writer.pOln("};");
            }
        }
    }

    /**
     * Create a Type instance for the given type. Requires that
     * setName(Identifier) be called afterward.
     */
    protected Type(ContextStack stack, int fullTypeCode) {
        this.env = stack.getEnv();
        this.stack = stack;
        this.fullTypeCode = fullTypeCode;
        typeCode = fullTypeCode & TYPE_MASK;
    }

    /**
     * Set type codes. May only be called during initialization.
     */
    protected void setTypeCode(int fullTypeCode) {
        this.fullTypeCode = fullTypeCode;
        typeCode = fullTypeCode & TYPE_MASK;
    }

    /**
     * Set name and package. May only be called during initialization.
     */
    protected void setNames(Identifier id, String[] idlModuleNames, String idlName) {

        this.id = id;
        name = Names.mangleClass(id).getName().toString();
        packageName = null;

        if (id.isQualified()) {
            packageName = id.getQualifier().toString();
            qualifiedName = packageName + NAME_SEPARATOR + name;
        } else {
            qualifiedName = name;
        }

        setIDLNames(idlModuleNames,idlName);
    }


    /**
     * Set IDL name. May only be called during initialization.
     */
    protected void setIDLNames(String[] idlModuleNames, String idlName) {
        this.idlName = idlName;

        if (idlModuleNames != null) {
            this.idlModuleNames = idlModuleNames;
        } else {
            this.idlModuleNames = new String[0];
        }
        qualifiedIDLName = IDLNames.getQualifiedName(idlModuleNames,idlName);
    }

    /**
     * Report a ClassNotFoundException thru the compiler environment.
     */
    protected static void classNotFound(ContextStack stack,
                                        ClassNotFound e) {
        classNotFound(false,stack,e);
    }

    /**
     * Report a ClassNotFoundException thru the compiler environment.
     */
    protected static void classNotFound(boolean quiet,
                                        ContextStack stack,
                                        ClassNotFound e) {
        if (!quiet) stack.getEnv().error(0, "rmic.class.not.found", e.name);
        stack.traceCallStack();
    }

    /**
     * Report a constraint failure thru the compiler environment.
     * @param constraintNum Used to generate a key of the form
     "rmic.iiop.constraint.N", which must identify a message
     in the "rmic.properties" file.
     * @param quiet True if should not cause failure or message.
     * @param stack The context stack.
     * @param arg0 An object to substitute for {0} in the message.
     * @param arg1 An object to substitute for {1} in the message.
     * @param arg2 An object to substitute for {2} in the message.
     * @return false.
     */
    protected static boolean failedConstraint(int constraintNum,
                                              boolean quiet,
                                              ContextStack stack,
                                              Object arg0, Object arg1, Object arg2) {
        String message = "rmic.iiop.constraint." + constraintNum;

        if (!quiet) {
            stack.getEnv().error(0,message,
                                 (arg0 != null ? arg0.toString() : null),
                                 (arg1 != null ? arg1.toString() : null),
                                 (arg2 != null ? arg2.toString() : null));
        } else {
            String error = stack.getEnv().errorString(message,arg0,arg1,arg2);
            stack.traceln(error);
        }

        return false;
    }

    /**
     * Report a constraint failure thru the compiler environment.
     * @param constraintNum Used to generate a key of the form
     "rmic.iiop.constraint.N", which must identify a message
     in the "rmic.properties" file.
     * @param quiet True if should not cause failure or message.
     * @param stack The context stack.
     * @param arg0 An object to substitute for {0} in the message.
     * @param arg1 An object to substitute for {1} in the message.
     * @return false.
     */
    protected static boolean failedConstraint(int constraintNum,
                                              boolean quiet,
                                              ContextStack stack,
                                              Object arg0, Object arg1) {
        return failedConstraint(constraintNum,quiet,stack,arg0,arg1,null);
    }


    /**
     * Report a constraint failure thru the compiler environment.
     * @param constraintNum Used to generate a key of the form
     "rmic.iiop.constraint.N", which must identify a message
     in the "rmic.properties" file.
     * @param quiet True if should not cause failure or message.
     * @param stack The context stack.
     * @param arg0 An object to substitute for {0} in the message.
     * @return false.
     */
    protected static boolean failedConstraint(int constraintNum,
                                              boolean quiet,
                                              ContextStack stack,
                                              Object arg0) {
        return failedConstraint(constraintNum,quiet,stack,arg0,null,null);
    }

    /**
     * Report a constraint failure thru the compiler environment.
     * @param quiet True if should not cause failure or message.
     * @param stack The context stack.
     * @param constraintNum Used to generate a key of the form
     "rmic.iiop.constraint.N", which must identify a message
     in the "rmic.properties" file.
     * @return false.
     */
    protected static boolean failedConstraint(int constraintNum,
                                              boolean quiet,
                                              ContextStack stack) {
        return failedConstraint(constraintNum,quiet,stack,null,null,null);
    }

    /**
     * Cloning is supported by returning a shallow copy of this object.
     */
    protected Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error("clone failed");
        }
    }

    /*
     * Add matching types to list. Return true if this type has not
     * been previously checked, false otherwise.
     */
    protected boolean addTypes (int typeCodeFilter,
                                HashSet checked,
                                Vector matching) {

        boolean result;

        // Have we already checked this type?

        if (checked.contains(this)) {

            // Yes, so return false.

            result = false;

        } else {

            // Nope, so add it...

            checked.add(this);

            // Do we match the filter?

            if (typeMatches(typeCodeFilter)) {

                // Yep. so add it and set result to true...

                matching.addElement(this);
            }

            // Return true.

            result = true;
        }

        return result;
    }

    /*
     * Load a Class instance. Return null if fail.
     */
    protected abstract Class loadClass();

    private boolean initClass() {
        if (ourClass == null) {
            ourClass = loadClass();
            if (ourClass == null) {
                failedConstraint(27,false,stack,getQualifiedName());
                return false;
            }
        }
        return true;
    }

    /*
     * Set the clz and repositoryID fields. Reports error
     * and returns false if fails, returns true if succeeds.
     */
    protected boolean setRepositoryID() {

        // First, load the class...

        if (!initClass()) {
            return false;
        }

        // Now make the repositoryID and return success...

        repositoryID = RepositoryId.createForAnyType(ourClass);
        return true;
    }


    //_____________________________________________________________________
    // Internal Interfaces
    //_____________________________________________________________________

    private Type () {} // Disallowed.
}
