/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.internal.ws.processor.model.java;

import com.sun.tools.internal.ws.processor.model.ModelException;

import java.util.*;

/**
 *
 * @author WS Development Team
 */
public class JavaStructureType extends JavaType {

    public JavaStructureType() {}

    public JavaStructureType(String name, boolean present, Object owner) {
        super(name, present, "null");
        this.owner = owner;
    }

    public void add(JavaStructureMember m) {
        if (membersByName.containsKey(m.getName())) {
            throw new ModelException("model.uniqueness.javastructuretype",
                new Object[] {m.getName(), getRealName()});
        }
        members.add(m);
        membersByName.put(m.getName(), m);
    }


    public JavaStructureMember getMemberByName(String name) {
        if (membersByName.size() != members.size()) {
            initializeMembersByName();
        }
        return membersByName.get(name);
    }

    public Iterator getMembers() {
        return members.iterator();
    }

    public int getMembersCount() {
        return members.size();
    }

    /* serialization */
    public List<JavaStructureMember> getMembersList() {
        return members;
    }

    /* serialization */
    public void setMembersList(List<JavaStructureMember> l) {
        members = l;
    }

    private void initializeMembersByName() {
        membersByName = new HashMap<String, JavaStructureMember>();
        if (members != null) {
            for (JavaStructureMember m : members) {
                if (m.getName() != null &&
                    membersByName.containsKey(m.getName())) {

                    throw new ModelException("model.uniqueness");
                }
                membersByName.put(m.getName(), m);
            }
        }
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    public JavaStructureType getSuperclass() {
        return superclass;
    }

    public void setSuperclass(JavaStructureType superclassType) {
        superclass = superclassType;
    }

    public void addSubclass(JavaStructureType subclassType) {
        subclasses.add(subclassType);
        subclassType.setSuperclass(this);
    }

    public Iterator getSubclasses() {
        if (subclasses == null || subclasses.size() == 0) {
            return null;
        }
        return subclasses.iterator();
    }

    public Set getSubclassesSet() {
        return subclasses;
    }

    /* serialization */
    public void setSubclassesSet(Set s) {
        subclasses = s;
        for (Iterator iter = s.iterator(); iter.hasNext();) {
            ((JavaStructureType) iter.next()).setSuperclass(this);
        }
    }

    public Iterator getAllSubclasses() {
        Set subs = getAllSubclassesSet();
        if (subs.size() == 0) {
            return null;
        }
        return subs.iterator();
    }

    public Set getAllSubclassesSet() {
        Set transitiveSet = new HashSet();
        Iterator subs = subclasses.iterator();
        while (subs.hasNext()) {
            transitiveSet.addAll(
                ((JavaStructureType)subs.next()).getAllSubclassesSet());
        }
        transitiveSet.addAll(subclasses);
        return transitiveSet;
    }

    public Object getOwner() {

        // usually a SOAPStructureType
        return owner;
    }

    public void setOwner(Object owner) {

        // usually a SOAPStructureType
        this.owner = owner;
    }

    private List<JavaStructureMember> members = new ArrayList();
    private Map<String, JavaStructureMember> membersByName = new HashMap();

    // known subclasses of this type
    private Set subclasses = new HashSet();
    private JavaStructureType superclass;

    // usually a SOAPStructureType
    private Object owner;
    private boolean isAbstract = false;
}
