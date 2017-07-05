/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.util.*;

/**
 * The MethodSet structure is used to store methods for a class.
 * It maintains the invariant that it never stores two methods
 * with the same signature.  MethodSets are able to lookup
 * all methods with a given name and the unique method with a given
 * signature (name, args).
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */

public
class MethodSet {

    /**
     * A Map containing Lists of MemberDefinitions.  The Lists
     * contain methods which share the same name.
     */
    private final Map lookupMap;

    /**
     * The number of methods stored in the MethodSet.
     */
    private int count;

    /**
     * Is this MethodSet currently frozen?  See freeze() for more details.
     */
    private boolean frozen;

    /**
     * Creates a brand new MethodSet
     */
    public MethodSet() {
        frozen = false;
        lookupMap = new HashMap();
        count = 0;
    }

    /**
     * Returns the number of distinct methods stored in the MethodSet.
     */
    public int size() {
        return count;
    }

    /**
     * Adds `method' to the MethodSet.  No method of the same signature
     * should be already defined.
     */
    public void add(MemberDefinition method) {
            // Check for late additions.
            if (frozen) {
                throw new CompilerError("add()");
            }

            // todo: Check for method??

            Identifier name = method.getName();

            // Get a List containing all methods of this name.
            List methodList = (List) lookupMap.get(name);

            if (methodList == null) {
                // There is no method with this name already.
                // Create a List, and insert it into the hash.
                methodList = new ArrayList();
                lookupMap.put(name, methodList);
            }

            // Make sure that no method with the same signature has already
            // been added to the MethodSet.
            int size = methodList.size();
            for (int i = 0; i < size; i++) {
                if (((MemberDefinition) methodList.get(i))
                    .getType().equalArguments(method.getType())) {
                    throw new CompilerError("duplicate addition");
                }
            }

            // We add the method to the appropriate list.
            methodList.add(method);
            count++;
    }

    /**
     * Adds `method' to the MethodSet, replacing any previous definition
     * with the same signature.
     */
    public void replace(MemberDefinition method) {
            // Check for late additions.
            if (frozen) {
                throw new CompilerError("replace()");
            }

            // todo: Check for method??

            Identifier name = method.getName();

            // Get a List containing all methods of this name.
            List methodList = (List) lookupMap.get(name);

            if (methodList == null) {
                // There is no method with this name already.
                // Create a List, and insert it into the hash.
                methodList = new ArrayList();
                lookupMap.put(name, methodList);
            }

            // Replace the element which has the same signature as
            // `method'.
            int size = methodList.size();
            for (int i = 0; i < size; i++) {
                if (((MemberDefinition) methodList.get(i))
                    .getType().equalArguments(method.getType())) {
                    methodList.set(i, method);
                    return;
                }
            }

            // We add the method to the appropriate list.
            methodList.add(method);
            count++;
    }

    /**
     * If the MethodSet contains a method with the same signature
     * then lookup() returns it.  Otherwise, this method returns null.
     */
    public MemberDefinition lookupSig(Identifier name, Type type) {
        // Go through all methods of the same name and see if any
        // have the right signature.
        Iterator matches = lookupName(name);
        MemberDefinition candidate;

        while (matches.hasNext()) {
            candidate = (MemberDefinition) matches.next();
            if (candidate.getType().equalArguments(type)) {
                return candidate;
            }
        }

        // No match.
        return null;
    }

    /**
     * Returns an Iterator of all methods contained in the
     * MethodSet which have a given name.
     */
    public Iterator lookupName(Identifier name) {
        // Find the List containing all methods of this name, and
        // return that List's Iterator.
        List methodList = (List) lookupMap.get(name);
        if (methodList == null) {
            // If there is no method of this name, return a bogus, empty
            // Iterator.
            return Collections.emptyIterator();
        }
        return methodList.iterator();
    }

    /**
     * Returns an Iterator of all methods in the MethodSet
     */
    public Iterator iterator() {

        //----------------------------------------------------------
        // The inner class MethodIterator is used to create our
        // Iterator of all methods in the MethodSet.
        class MethodIterator implements Iterator {
            Iterator hashIter = lookupMap.values().iterator();
            Iterator listIter = Collections.emptyIterator();

            public boolean hasNext() {
                if (listIter.hasNext()) {
                    return true;
                } else {
                    if (hashIter.hasNext()) {
                        listIter = ((List) hashIter.next())
                            .iterator();

                        // The following should be always true.
                        if (listIter.hasNext()) {
                            return true;
                        } else {
                            throw new
                                CompilerError("iterator() in MethodSet");
                        }
                    }
                }

                // We've run out of Lists.
                return false;
            }

            public Object next() {
                return listIter.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        }
        // end MethodIterator
        //----------------------------------------------------------

        // A one-liner.
        return new MethodIterator();
    }

    /**
     * After freeze() is called, the MethodSet becomes (mostly)
     * immutable.  Any calls to add() or addMeet() lead to
     * CompilerErrors.  Note that the entries themselves are still
     * (unfortunately) open for mischievous and wanton modification.
     */
    public void freeze() {
        frozen = true;
    }

    /**
     * Tells whether freeze() has been called on this MethodSet.
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Returns a (big) string representation of this MethodSet
     */
    public String toString() {
        int len = size();
        StringBuffer buf = new StringBuffer();
        Iterator all = iterator();
        buf.append("{");

        while (all.hasNext()) {
            buf.append(all.next().toString());
            len--;
            if (len > 0) {
                buf.append(", ");
            }
        }
        buf.append("}");
        return buf.toString();
    }
}
