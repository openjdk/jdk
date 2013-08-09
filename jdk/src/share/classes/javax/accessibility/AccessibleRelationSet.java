/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.accessibility;

import java.util.Vector;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Class AccessibleRelationSet determines a component's relation set.  The
 * relation set of a component is a set of AccessibleRelation objects that
 * describe the component's relationships with other components.
 *
 * @see AccessibleRelation
 *
 * @author      Lynn Monsanto
 * @since 1.3
 */
public class AccessibleRelationSet {

    /**
     * Each entry in the Vector represents an AccessibleRelation.
     * @see #add
     * @see #addAll
     * @see #remove
     * @see #contains
     * @see #get
     * @see #size
     * @see #toArray
     * @see #clear
     */
    protected Vector<AccessibleRelation> relations = null;

    /**
     * Creates a new empty relation set.
     */
    public AccessibleRelationSet() {
        relations = null;
    }

    /**
     * Creates a new relation with the initial set of relations contained in
     * the array of relations passed in.  Duplicate entries are ignored.
     *
     * @param relations an array of AccessibleRelation describing the
     * relation set.
     */
    public AccessibleRelationSet(AccessibleRelation[] relations) {
        if (relations.length != 0) {
            this.relations = new Vector(relations.length);
            for (int i = 0; i < relations.length; i++) {
                add(relations[i]);
            }
        }
    }

    /**
     * Adds a new relation to the current relation set.  If the relation
     * is already in the relation set, the target(s) of the specified
     * relation is merged with the target(s) of the existing relation.
     * Otherwise,  the new relation is added to the relation set.
     *
     * @param relation the relation to add to the relation set
     * @return true if relation is added to the relation set; false if the
     * relation set is unchanged
     */
    public boolean add(AccessibleRelation relation) {
        if (relations == null) {
            relations = new Vector();
        }

        // Merge the relation targets if the key exists
        AccessibleRelation existingRelation = get(relation.getKey());
        if (existingRelation == null) {
            relations.addElement(relation);
            return true;
        } else {
            Object [] existingTarget = existingRelation.getTarget();
            Object [] newTarget = relation.getTarget();
            int mergedLength = existingTarget.length + newTarget.length;
            Object [] mergedTarget = new Object[mergedLength];
            for (int i = 0; i < existingTarget.length; i++) {
                mergedTarget[i] = existingTarget[i];
            }
            for (int i = existingTarget.length, j = 0;
                 i < mergedLength;
                 i++, j++) {
                mergedTarget[i] = newTarget[j];
            }
            existingRelation.setTarget(mergedTarget);
        }
        return true;
    }

    /**
     * Adds all of the relations to the existing relation set.  Duplicate
     * entries are ignored.
     *
     * @param relations  AccessibleRelation array describing the relation set.
     */
    public void addAll(AccessibleRelation[] relations) {
        if (relations.length != 0) {
            if (this.relations == null) {
                this.relations = new Vector(relations.length);
            }
            for (int i = 0; i < relations.length; i++) {
                add(relations[i]);
            }
        }
    }

    /**
     * Removes a relation from the current relation set.  If the relation
     * is not in the set, the relation set will be unchanged and the
     * return value will be false.  If the relation is in the relation
     * set, it will be removed from the set and the return value will be
     * true.
     *
     * @param relation the relation to remove from the relation set
     * @return true if the relation is in the relation set; false if the
     * relation set is unchanged
     */
    public boolean remove(AccessibleRelation relation) {
        if (relations == null) {
            return false;
        } else {
            return relations.removeElement(relation);
        }
    }

    /**
     * Removes all the relations from the current relation set.
     */
    public void clear() {
        if (relations != null) {
            relations.removeAllElements();
        }
    }

    /**
     * Returns the number of relations in the relation set.
     * @return the number of relations in the relation set
     */
    public int size() {
        if (relations == null) {
            return 0;
        } else {
            return relations.size();
        }
    }

    /**
     * Returns whether the relation set contains a relation
     * that matches the specified key.
     * @param key the AccessibleRelation key
     * @return true if the relation is in the relation set; otherwise false
     */
    public boolean contains(String key) {
        return get(key) != null;
    }

    /**
     * Returns the relation that matches the specified key.
     * @param key the AccessibleRelation key
     * @return the relation, if one exists, that matches the specified key.
     * Otherwise, null is returned.
     */
    public AccessibleRelation get(String key) {
        if (relations == null) {
            return null;
        } else {
            int len = relations.size();
            for (int i = 0; i < len; i++) {
                AccessibleRelation relation =
                    (AccessibleRelation)relations.elementAt(i);
                if (relation != null && relation.getKey().equals(key)) {
                    return relation;
                }
            }
            return null;
        }
    }

    /**
     * Returns the current relation set as an array of AccessibleRelation
     * @return AccessibleRelation array contacting the current relation.
     */
    public AccessibleRelation[] toArray() {
        if (relations == null) {
            return new AccessibleRelation[0];
        } else {
            AccessibleRelation[] relationArray
                = new AccessibleRelation[relations.size()];
            for (int i = 0; i < relationArray.length; i++) {
                relationArray[i] = (AccessibleRelation) relations.elementAt(i);
            }
            return relationArray;
        }
    }

    /**
     * Creates a localized String representing all the relations in the set
     * using the default locale.
     *
     * @return comma separated localized String
     * @see AccessibleBundle#toDisplayString
     */
    public String toString() {
        String ret = "";
        if ((relations != null) && (relations.size() > 0)) {
            ret = ((AccessibleRelation) (relations.elementAt(0))).toDisplayString();
            for (int i = 1; i < relations.size(); i++) {
                ret = ret + ","
                        + ((AccessibleRelation) (relations.elementAt(i))).
                                              toDisplayString();
            }
        }
        return ret;
    }
}
