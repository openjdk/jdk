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

import java.util.Hashtable;

/**
 * A NameContext enables detection of strings which differ only
 * in case.
 *
 * @author      Bryan Atsatt
 */
class NameContext {

    private Hashtable table;
    private boolean allowCollisions;

    /**
     * Get a context for the given name. Name may be null, in
     * which case this method will return the default context.
     */
    public static synchronized NameContext forName (String name,
                                                    boolean allowCollisions,
                                                    BatchEnvironment env) {

        NameContext result = null;

        // Do we need to use the default context?

        if (name == null) {

            // Yes.

            name = "null";
        }

        // Have we initialized our hashtable?

        if (env.nameContexts == null) {

            // Nope, so do it...

            env.nameContexts = new Hashtable();

        } else {

            // Yes, see if we already have the requested
            // context...

            result = (NameContext) env.nameContexts.get(name);
        }

        // Do we have the requested context?

        if (result == null) {

            // Nope, so create and add it...

            result = new NameContext(allowCollisions);

            env.nameContexts.put(name,result);
        }

        return result;
    }

    /**
     * Construct a context.
     * @param allowCollisions true if case-sensitive name collisions
     * are allowed, false if not.
     */
    public NameContext (boolean allowCollisions) {
        this.allowCollisions = allowCollisions;
        table = new Hashtable();
    }

    /**
     * Add a name to this context. If constructed with allowCollisions
     * false and a collision occurs, this method will throw an exception
     * in which the message contains the string: "name" and "collision".
     */
    public void assertPut (String name) throws Exception {

        String message = add(name);

        if (message != null) {
            throw new Exception(message);
        }
    }

    /**
     * Add a name to this context..
     */
    public void put (String name) {

        if (allowCollisions == false) {
            throw new Error("Must use assertPut(name)");
        }

        add(name);
    }

    /**
     * Add a name to this context. If constructed with allowCollisions
     * false and a collision occurs, this method will return a message
     * string, otherwise returns null.
     */
    private String add (String name) {

        // First, create a key by converting name to lowercase...

        String key = name.toLowerCase();

        // Does this key exist in the context?

        Name value = (Name) table.get(key);

        if (value != null) {

            // Yes, so they match if we ignore case. Do they match if
            // we don't ignore case?

            if (!name.equals(value.name)) {

                // No, so this is a case-sensitive match. Are we
                // supposed to allow this?

                if (allowCollisions) {

                    // Yes, make sure it knows that it collides...

                    value.collisions = true;

                } else {

                    // No, so return a message string...

                    return new String("\"" + name + "\" and \"" + value.name + "\"");
                }
            }
        } else {

            // No, so add it...

            table.put(key,new Name(name,false));
        }

        return null;
    }

    /**
     * Get a name from the context. If it has collisions, the name
     * will be converted as specified in section 5.2.7.
     */
    public String get (String name) {

        Name it = (Name) table.get(name.toLowerCase());
        String result = name;

        // Do we need to mangle it?

        if (it.collisions) {

            // Yep, so do it...

            int length = name.length();
            boolean allLower = true;

            for (int i = 0; i < length; i++) {

                if (Character.isUpperCase(name.charAt(i))) {
                    result += "_";
                    result += i;
                    allLower = false;
                }
            }

            if (allLower) {
                result += "_";
            }
        }

        return result;
    }

    /**
     * Remove all entries.
     */
    public void clear () {
        table.clear();
    }

    public class Name {
        public String name;
        public boolean collisions;

        public Name (String name, boolean collisions) {
            this.name = name;
            this.collisions = collisions;
        }
    }
}
