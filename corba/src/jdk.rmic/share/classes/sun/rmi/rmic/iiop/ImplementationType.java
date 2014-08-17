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
import sun.tools.java.CompilerError;
import sun.tools.java.ClassNotFound;
import sun.tools.java.ClassDefinition;
import sun.tools.java.MemberDefinition;

/**
 * ImplementationType represents any non-special class which implements
 * one or more interfaces which inherit from java.rmi.Remote.
 * <p>
 * The static forImplementation(...) method must be used to obtain an instance,
 * and will return null if the ClassDefinition is non-conforming.
 *
 * @author      Bryan Atsatt
 */
public class ImplementationType extends ClassType {

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Create an ImplementationType for the given class.
     *
     * If the class is not a properly formed or if some other error occurs, the
     * return value will be null, and errors will have been reported to the
     * supplied BatchEnvironment.
     */
    public static ImplementationType forImplementation(ClassDefinition classDef,
                                                       ContextStack stack,
                                                       boolean quiet) {
        if (stack.anyErrors()) return null;

        boolean doPop = false;
        ImplementationType result = null;

        try {
            // Do we already have it?

            sun.tools.java.Type theType = classDef.getType();
            Type existing = getType(theType,stack);

            if (existing != null) {

                if (!(existing instanceof ImplementationType)) return null; // False hit.

                                // Yep, so return it...

                return (ImplementationType) existing;

            }

            // Could this be an implementation?

            if (couldBeImplementation(quiet,stack,classDef)) {

                // Yes, so check it...

                ImplementationType it = new ImplementationType(stack, classDef);
                putType(theType,it,stack);
                stack.push(it);
                doPop = true;

                if (it.initialize(stack,quiet)) {
                    stack.pop(true);
                    result = it;
                } else {
                    removeType(theType,stack);
                    stack.pop(false);
                }
            }
        } catch (CompilerError e) {
            if (doPop) stack.pop(false);
        }

        return result;
    }

    /**
     * Return a string describing this type.
     */
    public String getTypeDescription () {
        return "Implementation";
    }


    //_____________________________________________________________________
    // Internal Interfaces
    //_____________________________________________________________________

    /**
     * Create a ImplementationType instance for the given class.  The resulting
     * object is not yet completely initialized.
     */
    private ImplementationType(ContextStack stack, ClassDefinition classDef) {
        super(TYPE_IMPLEMENTATION | TM_CLASS | TM_COMPOUND,classDef,stack); // Use special constructor.
    }


    private static boolean couldBeImplementation(boolean quiet, ContextStack stack,
                                                 ClassDefinition classDef) {
        boolean result = false;
        BatchEnvironment env = stack.getEnv();

        try {
            if (!classDef.isClass()) {
                failedConstraint(17,quiet,stack,classDef.getName());
            } else {
                result = env.defRemote.implementedBy(env, classDef.getClassDeclaration());
                if (!result) failedConstraint(8,quiet,stack,classDef.getName());
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

        boolean result = false;
        ClassDefinition theClass = getClassDefinition();

        if (initParents(stack)) {

            // Make up our collections...

            Vector directInterfaces = new Vector();
            Vector directMethods = new Vector();

            // Check interfaces...

            try {
                if (addRemoteInterfaces(directInterfaces,true,stack) != null) {

                    boolean haveRemote = false;

                    // Get methods from all interfaces...

                    for (int i = 0; i < directInterfaces.size(); i++) {
                        InterfaceType theInt = (InterfaceType) directInterfaces.elementAt(i);
                        if (theInt.isType(TYPE_REMOTE) ||
                            theInt.isType(TYPE_JAVA_RMI_REMOTE)) {
                            haveRemote = true;
                        }

                        copyRemoteMethods(theInt,directMethods);
                    }

                    // Make sure we have at least one remote interface...

                    if (!haveRemote) {
                        failedConstraint(8,quiet,stack,getQualifiedName());
                        return false;
                    }

                    // Now check the methods to ensure we have the
                    // correct throws clauses...

                    if (checkMethods(theClass,directMethods,stack,quiet)) {

                        // We're ok, so pass 'em up...

                        result = initialize(directInterfaces,directMethods,null,stack,quiet);
                    }
                }
            } catch (ClassNotFound e) {
                classNotFound(stack,e);
            }
        }

        return result;
    }

    private static void copyRemoteMethods(InterfaceType type, Vector list) {

        if (type.isType(TYPE_REMOTE)) {

            // Copy all the unique methods from type...

            Method[] allMethods = type.getMethods();

            for (int i = 0; i < allMethods.length; i++) {
                Method theMethod = allMethods[i];

                if (!list.contains(theMethod)) {
                    list.addElement(theMethod);
                }
            }

            // Now recurse thru all inherited interfaces...

            InterfaceType[] allInterfaces = type.getInterfaces();

            for (int i = 0; i < allInterfaces.length; i++) {
                copyRemoteMethods(allInterfaces[i],list);
            }
        }
    }

    // Walk all methods of the class, and for each that is already in
    // the list, call setImplExceptions()...

    private boolean checkMethods(ClassDefinition theClass, Vector list,
                                 ContextStack stack, boolean quiet) {

        // Convert vector to array...

        Method[] methods = new Method[list.size()];
        list.copyInto(methods);

        for (MemberDefinition member = theClass.getFirstMember();
             member != null;
             member = member.getNextMember()) {

            if (member.isMethod() && !member.isConstructor()
                && !member.isInitializer()) {

                // It's a method...

                if (!updateExceptions(member,methods,stack,quiet)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean updateExceptions (MemberDefinition implMethod, Method[] list,
                                      ContextStack stack, boolean quiet) {
        int length = list.length;
        String implMethodSig = implMethod.toString();

        for (int i = 0; i < length; i++) {
            Method existingMethod = list[i];
            MemberDefinition existing = existingMethod.getMemberDefinition();

            // Do we have a matching method?

            if (implMethodSig.equals(existing.toString())) {

                // Yes, so create exception list...

                try {
                    ValueType[] implExcept = getMethodExceptions(implMethod,quiet,stack);
                    existingMethod.setImplExceptions(implExcept);
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return true;
    }
}
