/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.jdi;

import com.sun.jdi.*;
import sun.jvm.hotspot.oops.InstanceKlass;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;
import java.util.Collections;
import java.lang.ref.SoftReference;

public class InterfaceTypeImpl extends ReferenceTypeImpl
                               implements InterfaceType {
    private SoftReference superInterfacesCache = null;
    private SoftReference subInterfacesCache = null;
    private SoftReference implementorsCache = null;

    protected InterfaceTypeImpl(VirtualMachine aVm, InstanceKlass aRef) {
        super(aVm, aRef);
    }

    public List superinterfaces() throws ClassNotPreparedException {
        List superinterfaces = (superInterfacesCache != null)? (List) superInterfacesCache.get() : null;
        if (superinterfaces == null) {
            checkPrepared();
            superinterfaces = Collections.unmodifiableList(getInterfaces());
            superInterfacesCache = new SoftReference(superinterfaces);
        }
        return superinterfaces;
    }

    public List subinterfaces() {
        List subinterfaces = (subInterfacesCache != null)? (List) subInterfacesCache.get() : null;
        if (subinterfaces == null) {
            List all = vm.allClasses();
            subinterfaces = new ArrayList();
            Iterator iter = all.iterator();
            while (iter.hasNext()) {
                ReferenceType refType = (ReferenceType)iter.next();
                if (refType instanceof InterfaceType) {
                    InterfaceType interfaze = (InterfaceType)refType;
                    if (interfaze.isPrepared() && interfaze.superinterfaces().contains(this)) {
                        subinterfaces.add(interfaze);
                    }
               }
            }
            subinterfaces = Collections.unmodifiableList(subinterfaces);
            subInterfacesCache = new SoftReference(subinterfaces);
        }
        return subinterfaces;
    }

    public List implementors() {
        List implementors = (implementorsCache != null)? (List) implementorsCache.get() : null;
        if (implementors == null) {
            List all = vm.allClasses();
            implementors = new ArrayList();
            Iterator iter = all.iterator();
            while (iter.hasNext()) {
                ReferenceType refType = (ReferenceType)iter.next();
                if (refType instanceof ClassType) {
                    ClassType clazz = (ClassType)refType;
                    if (clazz.isPrepared() && clazz.interfaces().contains(this)) {
                        implementors.add(clazz);
                    }
                }
            }
            implementors = Collections.unmodifiableList(implementors);
            implementorsCache = new SoftReference(implementors);
        }
        return implementors;
    }

    void addVisibleMethods(Map methodMap) {
        /*
         * Add methods from
         * parent types first, so that the methods in this class will
         * overwrite them in the hash table
         */
        Iterator iter = superinterfaces().iterator();
        while (iter.hasNext()) {
            InterfaceTypeImpl interfaze = (InterfaceTypeImpl)iter.next();
            interfaze.addVisibleMethods(methodMap);
        }

        addToMethodMap(methodMap, methods());
    }

    List getAllMethods() {
        ArrayList list = new ArrayList(methods());
        /*
         * It's more efficient if don't do this
         * recursively.
         */
        List interfaces = allSuperinterfaces();
        Iterator iter = interfaces.iterator();
        while (iter.hasNext()) {
            InterfaceType interfaze = (InterfaceType)iter.next();
            list.addAll(interfaze.methods());
        }

        return list;
    }

    List allSuperinterfaces() {
        ArrayList list = new ArrayList();
        addSuperinterfaces(list);
        return list;
    }

    void addSuperinterfaces(List list) {
        /*
         * This code is a little strange because it
         * builds the list with a more suitable order than the
         * depth-first approach a normal recursive solution would
         * take. Instead, all direct superinterfaces precede all
         * indirect ones.
         */

        /*
         * Get a list of direct superinterfaces that's not already in the
         * list being built.
         */
        List immediate = new ArrayList(superinterfaces());
        Iterator iter = immediate.iterator();
        while (iter.hasNext()) {
            InterfaceType interfaze = (InterfaceType)iter.next();
            if (list.contains(interfaze)) {
                iter.remove();
            }
        }

        /*
         * Add all new direct superinterfaces
         */
        list.addAll(immediate);

        /*
         * Recurse for all new direct superinterfaces.
         */
        iter = immediate.iterator();
        while (iter.hasNext()) {
            InterfaceTypeImpl interfaze = (InterfaceTypeImpl)iter.next();
            interfaze.addSuperinterfaces(list);
        }
    }

    boolean isAssignableTo(ReferenceType type) {

        // Exact match?
        if (this.equals(type)) {
            return true;
        } else {
            // Try superinterfaces.
            List supers = superinterfaces();
            Iterator iter = supers.iterator();
            while (iter.hasNext()) {
                InterfaceTypeImpl interfaze = (InterfaceTypeImpl)iter.next();
                if (interfaze.isAssignableTo(type)) {
                    return true;
                }
            }

            return false;
        }
    }

    List inheritedTypes() {
        return superinterfaces();
    }

    public boolean isInitialized() {
        return isPrepared();
    }

    public String toString() {
       return "interface " + name() + " (" + loaderString() + ")";
    }
}
