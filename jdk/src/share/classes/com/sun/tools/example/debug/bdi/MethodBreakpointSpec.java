/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.debug.bdi;

import com.sun.jdi.*;
import java.util.ArrayList;
import java.util.List;

public class MethodBreakpointSpec extends BreakpointSpec {
    String methodId;
    List<String> methodArgs;

    MethodBreakpointSpec(EventRequestSpecList specs,
                         ReferenceTypeSpec refSpec,
                         String methodId, List<String> methodArgs) {
        super(specs, refSpec);
        this.methodId = methodId;
        this.methodArgs = methodArgs;
    }

    /**
     * The 'refType' is known to match.
     */
    @Override
    void resolve(ReferenceType refType) throws MalformedMemberNameException,
                                             AmbiguousMethodException,
                                             InvalidTypeException,
                                             NoSuchMethodException,
                                             NoSessionException {
        if (!isValidMethodName(methodId)) {
            throw new MalformedMemberNameException(methodId);
        }
        if (!(refType instanceof ClassType)) {
            throw new InvalidTypeException();
        }
        Location location = location((ClassType)refType);
        setRequest(refType.virtualMachine().eventRequestManager()
                   .createBreakpointRequest(location));
    }

    private Location location(ClassType clazz) throws
                                               AmbiguousMethodException,
                                               NoSuchMethodException,
                                               NoSessionException {
        Method method = findMatchingMethod(clazz);
        Location location = method.location();
        return location;
    }

    public String methodName() {
        return methodId;
    }

    public List<String> methodArgs() {
        return methodArgs;
    }

    @Override
    public int hashCode() {
        return refSpec.hashCode() +
            ((methodId != null) ? methodId.hashCode() : 0) +
            ((methodArgs != null) ? methodArgs.hashCode() : 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MethodBreakpointSpec) {
            MethodBreakpointSpec breakpoint = (MethodBreakpointSpec)obj;

            return methodId.equals(breakpoint.methodId) &&
                   methodArgs.equals(breakpoint.methodArgs) &&
                   refSpec.equals(breakpoint.refSpec);
        } else {
            return false;
        }
    }

    @Override
    public String errorMessageFor(Exception e) {
        if (e instanceof AmbiguousMethodException) {
            return ("Method " + methodName() + " is overloaded; specify arguments");
            /*
             * TO DO: list the methods here
             */
        } else if (e instanceof NoSuchMethodException) {
            return ("No method " + methodName() + " in " + refSpec);
        } else if (e instanceof InvalidTypeException) {
            return ("Breakpoints can be located only in classes. " +
                        refSpec + " is an interface or array");
        } else {
            return super.errorMessageFor( e);
        }
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("breakpoint ");
        buffer.append(refSpec.toString());
        buffer.append('.');
        buffer.append(methodId);
        if (methodArgs != null) {
            boolean first = true;
            buffer.append('(');
            for (String name : methodArgs) {
                if (!first) {
                    buffer.append(',');
                }
                buffer.append(name);
                first = false;
            }
            buffer.append(")");
        }
        buffer.append(" (");
        buffer.append(getStatusString());
        buffer.append(')');
        return buffer.toString();
    }

    private boolean isValidMethodName(String s) {
        return isJavaIdentifier(s) ||
               s.equals("<init>") ||
               s.equals("<clinit>");
    }

    /*
     * Compare a method's argument types with a Vector of type names.
     * Return true if each argument type has a name identical to the
     * corresponding string in the vector (allowing for varargs)
     * and if the number of arguments in the method matches the
     * number of names passed
     */
    private boolean compareArgTypes(Method method, List<String> nameList) {
        List<String> argTypeNames = method.argumentTypeNames();

        // If argument counts differ, we can stop here
        if (argTypeNames.size() != nameList.size()) {
            return false;
        }

        // Compare each argument type's name
        int nTypes = argTypeNames.size();
        for (int i = 0; i < nTypes; ++i) {
            String comp1 = argTypeNames.get(i);
            String comp2 = nameList.get(i);
            if (! comp1.equals(comp2)) {
                /*
                 * We have to handle varargs.  EG, the
                 * method's last arg type is xxx[]
                 * while the nameList contains xxx...
                 * Note that the nameList can also contain
                 * xxx[] in which case we don't get here.
                 */
                if (i != nTypes - 1 ||
                    !method.isVarArgs()  ||
                    !comp2.endsWith("...")) {
                    return false;
                }
                /*
                 * The last types differ, it is a varargs
                 * method and the nameList item is varargs.
                 * We just have to compare the type names, eg,
                 * make sure we don't have xxx[] for the method
                 * arg type and yyy... for the nameList item.
                 */
                int comp1Length = comp1.length();
                if (comp1Length + 1 != comp2.length()) {
                    // The type names are different lengths
                    return false;
                }
                // We know the two type names are the same length
                if (!comp1.regionMatches(0, comp2, 0, comp1Length - 2)) {
                    return false;
                }
                // We do have xxx[] and xxx... as the last param type
                return true;
            }
        }

        return true;
    }

  private VirtualMachine vm() {
    return request.virtualMachine();
  }

  /**
     * Remove unneeded spaces and expand class names to fully
     * qualified names, if necessary and possible.
     */
    private String normalizeArgTypeName(String name) throws NoSessionException {
        /*
         * Separate the type name from any array modifiers,
         * stripping whitespace after the name ends.
         */
        int i = 0;
        StringBuffer typePart = new StringBuffer();
        StringBuffer arrayPart = new StringBuffer();
        name = name.trim();
        int nameLength = name.length();
        /*
         * For varargs, there can be spaces before the ... but not
         * within the ...  So, we will just ignore the ...
         * while stripping blanks.
         */
        boolean isVarArgs = name.endsWith("...");
        if (isVarArgs) {
            nameLength -= 3;
        }

        while (i < nameLength) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c) || c == '[') {
                break;      // name is complete
            }
            typePart.append(c);
            i++;
        }
        while (i < nameLength) {
            char c = name.charAt(i);
            if ( (c == '[') || (c == ']')) {
                arrayPart.append(c);
            } else if (!Character.isWhitespace(c)) {
                throw new IllegalArgumentException(
                                                "Invalid argument type name");

            }
            i++;
        }

        name = typePart.toString();

        /*
         * When there's no sign of a package name already,
         * try to expand the
         * the name to a fully qualified class name
         */
        if ((name.indexOf('.') == -1) || name.startsWith("*.")) {
            try {
                List<?> refs = specs.runtime.findClassesMatchingPattern(name);
                if (refs.size() > 0) {  //### ambiguity???
                    name = ((ReferenceType)(refs.get(0))).name();
                }
            } catch (IllegalArgumentException e) {
                // We'll try the name as is
            }
        }
        name += arrayPart.toString();
        if (isVarArgs) {
            name += "...";
        }
        return name;
    }

    /*
     * Attempt an unambiguous match of the method name and
     * argument specification to a method. If no arguments
     * are specified, the method must not be overloaded.
     * Otherwise, the argument types much match exactly
     */
    private Method findMatchingMethod(ClassType clazz)
                                        throws AmbiguousMethodException,
                                               NoSuchMethodException,
                                               NoSessionException  {

        // Normalize the argument string once before looping below.
        List<String> argTypeNames = null;
        if (methodArgs() != null) {
            argTypeNames = new ArrayList<String>(methodArgs().size());
            for (String name : methodArgs()) {
                name = normalizeArgTypeName(name);
                argTypeNames.add(name);
            }
        }

        // Check each method in the class for matches
        Method firstMatch = null;  // first method with matching name
        Method exactMatch = null;  // (only) method with same name & sig
        int matchCount = 0;        // > 1 implies overload
        for (Method candidate : clazz.methods()) {
            if (candidate.name().equals(methodName())) {
                matchCount++;

                // Remember the first match in case it is the only one
                if (matchCount == 1) {
                    firstMatch = candidate;
                }

                // If argument types were specified, check against candidate
                if ((argTypeNames != null)
                        && compareArgTypes(candidate, argTypeNames) == true) {
                    exactMatch = candidate;
                    break;
                }
            }
        }

        // Determine method for breakpoint
        Method method = null;
        if (exactMatch != null) {
            // Name and signature match
            method = exactMatch;
        } else if ((argTypeNames == null) && (matchCount > 0)) {
            // At least one name matched and no arg types were specified
            if (matchCount == 1) {
                method = firstMatch;       // Only one match; safe to use it
            } else {
                throw new AmbiguousMethodException();
            }
        } else {
            throw new NoSuchMethodException(methodName());
        }
        return method;
    }
}
