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
import sun.tools.java.ClassNotFound;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.ClassDefinition;
import sun.tools.java.MemberDefinition;
import java.util.Hashtable;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;


/**
 * ValueType represents any non-special class which does inherit from
 * java.io.Serializable and does not inherit from java.rmi.Remote.
 * <p>
 * The static forValue(...) method must be used to obtain an instance, and
 * will return null if the ClassDefinition is non-conforming.
 *
 * @author      Bryan Atsatt
 */
public class ValueType extends ClassType {

    private boolean isCustom;

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Create an ValueType object for the given class.
     *
     * If the class is not a properly formed or if some other error occurs, the
     * return value will be null, and errors will have been reported to the
     * supplied BatchEnvironment.
     */
    public static ValueType forValue(ClassDefinition classDef,
                                     ContextStack stack,
                                     boolean quiet) {

        if (stack.anyErrors()) return null;

        // Do we already have it?

        sun.tools.java.Type theType = classDef.getType();
        String typeKey = theType.toString();
        Type existing = getType(typeKey,stack);

        if (existing != null) {

            if (!(existing instanceof ValueType)) return null; // False hit.

            // Yep, so return it...

            return (ValueType) existing;
        }

        // Is this java.lang.Class?

        boolean javaLangClass = false;

        if (classDef.getClassDeclaration().getName() == idJavaLangClass) {

            // Yes, so replace classDef with one for
            // javax.rmi.CORBA.ClassDesc...

            javaLangClass = true;
            BatchEnvironment env = stack.getEnv();
            ClassDeclaration decl = env.getClassDeclaration(idClassDesc);
            ClassDefinition def = null;

            try {
                def = decl.getClassDefinition(env);
            } catch (ClassNotFound ex) {
                classNotFound(stack,ex);
                return null;
            }

            classDef = def;
        }

        // Could this be a value?

        if (couldBeValue(stack,classDef)) {

            // Yes, so check it...

            ValueType it = new ValueType(classDef,stack,javaLangClass);
            putType(typeKey,it,stack);
            stack.push(it);

            if (it.initialize(stack,quiet)) {
                stack.pop(true);
                return it;
            } else {
                removeType(typeKey,stack);
                stack.pop(false);
                return null;
            }
        } else {
            return null;
        }
    }


    /**
     * Return a string describing this type.
     */
    public String getTypeDescription () {
        String result = addExceptionDescription("Value");
        if (isCustom) {
            result = "Custom " + result;
        }
        if (isIDLEntity) {
            result = result + " [IDLEntity]";
        }
        return result;
    }

    /**
     * Return true if this type is a "custom" type (i.e.
     * it implements java.io.Externalizable or has a
     * method with the following signature:
     *
     *  private void writeObject(java.io.ObjectOutputStream out);
     *
     */
    public boolean isCustom () {
        return isCustom;
    }


    //_____________________________________________________________________
    // Subclass/Internal Interfaces
    //_____________________________________________________________________

    /**
     * Create a ValueType instance for the given class.  The resulting
     * object is not yet completely initialized.
     */
    private ValueType(ClassDefinition classDef,
                      ContextStack stack,
                      boolean isMappedJavaLangClass) {
        super(stack,classDef,TYPE_VALUE | TM_CLASS | TM_COMPOUND);
        isCustom = false;

        // If this is the mapped version of java.lang.Class,
        // set the non-IDL names back to java.lang.Class...

        if (isMappedJavaLangClass) {
            setNames(idJavaLangClass,IDL_CLASS_MODULE,IDL_CLASS);
        }
    }

    //_____________________________________________________________________
    // Internal Interfaces
    //_____________________________________________________________________

    /**
     * Initialize this instance.
     */

    private static boolean couldBeValue(ContextStack stack, ClassDefinition classDef) {

        boolean result = false;
        ClassDeclaration classDecl = classDef.getClassDeclaration();
        BatchEnvironment env = stack.getEnv();

        try {
            // Make sure it's not remote...

            if (env.defRemote.implementedBy(env, classDecl)) {
                failedConstraint(10,false,stack,classDef.getName());
            } else {

                // Make sure it's Serializable...

                if (!env.defSerializable.implementedBy(env, classDecl)) {
                    failedConstraint(11,false,stack,classDef.getName());
                } else {
                    result = true;
                }
            }
        } catch (ClassNotFound e) {
            classNotFound(stack,e);
        }

        return result;
    }

    /**
     * Initialize this instance.
     */
    private boolean initialize (ContextStack stack, boolean quiet) {

        ClassDefinition ourDef = getClassDefinition();
        ClassDeclaration ourDecl = getClassDeclaration();

        try {

            // Make sure our parentage is ok...

            if (!initParents(stack)) {
                failedConstraint(12,quiet,stack,getQualifiedName());
                return false;
            }


            // We're ok, so make up our collections...

            Vector directInterfaces = new Vector();
            Vector directMethods = new Vector();
            Vector directMembers = new Vector();

            // Get interfaces...

            if (addNonRemoteInterfaces(directInterfaces,stack) != null) {

                // Get methods...

                if (addAllMethods(ourDef,directMethods,false,false,stack) != null) {

                    // Update parent class methods
                    if (updateParentClassMethods(ourDef,directMethods,false,stack) != null) {

                    // Get constants and members...

                    if (addAllMembers(directMembers,false,false,stack)) {

                        // We're ok, so pass 'em up...

                        if (!initialize(directInterfaces,directMethods,directMembers,stack,quiet)) {
                            return false;
                        }

                        // Is this class Externalizable?

                        boolean externalizable = false;
                        if (!env.defExternalizable.implementedBy(env, ourDecl)) {

                            // No, so check to see if we have a serialPersistentField
                            // that will modify the members.

                            if (!checkPersistentFields(getClassInstance(),quiet)) {
                                return false;
                            }
                        } else {

                            // Yes.

                            externalizable = true;
                        }

                        // Should this class be considered "custom"? It is if
                        // it is Externalizable OR if it has a method with the
                        // following signature:
                        //
                        //  private void writeObject(java.io.ObjectOutputStream out);
                        //

                        if (externalizable) {
                            isCustom = true;
                        } else {
                            for (MemberDefinition member = ourDef.getFirstMember();
                                 member != null;
                                 member = member.getNextMember()) {

                                if (member.isMethod() &&
                                    !member.isInitializer() &&
                                    member.isPrivate() &&
                                    member.getName().toString().equals("writeObject")) {

                                    // Check return type, arguments and exceptions...

                                    sun.tools.java.Type methodType = member.getType();
                                    sun.tools.java.Type rtnType = methodType.getReturnType();

                                    if (rtnType == sun.tools.java.Type.tVoid) {

                                        // Return type is correct. How about arguments?

                                        sun.tools.java.Type[] args = methodType.getArgumentTypes();
                                        if (args.length == 1 &&
                                            args[0].getTypeSignature().equals("Ljava/io/ObjectOutputStream;")) {

                                            // Arguments are correct, so it is a custom
                                            // value type...

                                            isCustom = true;
                                        }
                                    }
                                }
                            }
                        }
                        }

                        return true;
                    }
                }
            }
        } catch (ClassNotFound e) {
            classNotFound(stack,e);
        }

        return false;
    }


    private boolean checkPersistentFields (Class clz, boolean quiet) {

        // Do we have a writeObject method?

        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals("writeObject") &&
                methods[i].getArguments().length == 1) {

                Type returnType = methods[i].getReturnType();
                Type arg = methods[i].getArguments()[0];
                String id = arg.getQualifiedName();

                if (returnType.isType(TYPE_VOID) &&
                    id.equals("java.io.ObjectOutputStream")) {

                    // Got one, so there's nothing to do...

                    return true;
                }
            }
        }

        // Do we have a valid serialPersistentField array?

        MemberDefinition spfDef = null;

        for (int i = 0; i < members.length; i++) {
            if (members[i].getName().equals("serialPersistentFields")) {

                Member member = members[i];
                Type type = member.getType();
                Type elementType = type.getElementType();

                // We have a member with the correct name. Make sure
                // we have the correct signature...

                if (elementType != null &&
                    elementType.getQualifiedName().equals(
                                                          "java.io.ObjectStreamField")
                    ) {

                    if (member.isStatic() &&
                        member.isFinal() &&
                        member.isPrivate()) {

                        // We have the correct signature

                        spfDef = member.getMemberDefinition();

                    } else {

                        // Bad signature...

                        failedConstraint(4,quiet,stack,getQualifiedName());
                        return false;
                    }
                }
            }
        }

        // If we do not have a serialPersistentField,
        // there's nothing to do, so return with no error...

        if (spfDef == null) {
            return true;
        }

        // Ok, now we must examine the contents of the array -
        // then validate them...

        Hashtable fields = getPersistentFields(clz);
        boolean result = true;

        for (int i = 0; i < members.length; i++) {
            String fieldName = members[i].getName();
            String fieldType = members[i].getType().getSignature();

            // Is this field present in the array?

            String type = (String) fields.get(fieldName);

            if (type == null) {

                // No, so mark it transient...

                members[i].setTransient();

            } else {

                // Yes, does the type match?

                if (type.equals(fieldType)) {

                    // Yes, so remove it from the fields table...

                    fields.remove(fieldName);

                } else {

                    // No, so error...

                    result = false;
                    failedConstraint(2,quiet,stack,fieldName,getQualifiedName());
                }
            }
        }

        // Ok, we've checked all of our fields. Are there any left in the "array"?
        // If so, it's an error...

        if (result && fields.size() > 0) {

            result = false;
            failedConstraint(9,quiet,stack,getQualifiedName());
        }

        // Return result...

        return result;
    }

    /**
     * Get the names and types of all the persistent fields of a Class.
     */
    private Hashtable getPersistentFields (Class clz) {
        Hashtable result = new Hashtable();
        ObjectStreamClass osc = ObjectStreamClass.lookup(clz);
        if (osc != null) {
            ObjectStreamField[] fields = osc.getFields();
            for (int i = 0; i < fields.length; i++) {
                String typeSig;
                String typePrefix = String.valueOf(fields[i].getTypeCode());
                if (fields[i].isPrimitive()) {
                    typeSig = typePrefix;
                } else {
                    if (fields[i].getTypeCode() == '[') {
                        typePrefix = "";
                    }
                    typeSig = typePrefix + fields[i].getType().getName().replace('.','/');
                    if (typeSig.endsWith(";")) {
                        typeSig = typeSig.substring(0,typeSig.length()-1);
                    }
                }
                result.put(fields[i].getName(),typeSig);
            }
        }
        return result;
    }
}
