/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.InstanceKlass;

import java.util.*;
import java.lang.ref.SoftReference;

public class ClassTypeImpl extends ReferenceTypeImpl
    implements ClassType
{
    private SoftReference interfacesCache    = null;
    private SoftReference allInterfacesCache = null;
    private SoftReference subclassesCache    = null;

    protected ClassTypeImpl(VirtualMachine aVm, InstanceKlass aRef) {
        super(aVm, aRef);
    }

    public ClassType superclass() {
        InstanceKlass kk = (InstanceKlass)ref().getSuper();
        if (kk == null) {
            return null;
        }
        return (ClassType) vm.referenceType(kk);
    }

    public List interfaces()  {
        List interfaces = (interfacesCache != null)? (List) interfacesCache.get() : null;
        if (interfaces == null) {
            checkPrepared();
            interfaces = Collections.unmodifiableList(getInterfaces());
            interfacesCache = new SoftReference(interfaces);
        }
        return interfaces;
    }

    void addInterfaces(List list) {
        List immediate = interfaces();

        HashSet hashList = new HashSet(list);
        hashList.addAll(immediate);
        list.clear();
        list.addAll(hashList);

        Iterator iter = immediate.iterator();
        while (iter.hasNext()) {
            InterfaceTypeImpl interfaze = (InterfaceTypeImpl)iter.next();
            interfaze.addSuperinterfaces(list);
        }

        ClassTypeImpl superclass = (ClassTypeImpl)superclass();
        if (superclass != null) {
            superclass.addInterfaces(list);
        }
    }

    public List allInterfaces()  {
        List allinterfaces = (allInterfacesCache != null)? (List) allInterfacesCache.get() : null;
        if (allinterfaces == null) {
            checkPrepared();
            allinterfaces = new ArrayList();
            addInterfaces(allinterfaces);
            allinterfaces = Collections.unmodifiableList(allinterfaces);
            allInterfacesCache = new SoftReference(allinterfaces);
        }
        return allinterfaces;
    }

    public List subclasses() {
        List subclasses = (subclassesCache != null)? (List) subclassesCache.get() : null;
        if (subclasses == null) {
            List all = vm.allClasses();
            subclasses = new ArrayList(0);
            Iterator iter = all.iterator();
            while (iter.hasNext()) {
                ReferenceType refType = (ReferenceType)iter.next();
                if (refType instanceof ClassType) {
                    ClassType clazz = (ClassType)refType;
                    ClassType superclass = clazz.superclass();
                    if ((superclass != null) && superclass.equals(this)) {
                        subclasses.add(refType);
                    }
                }
            }
            subclasses = Collections.unmodifiableList(subclasses);
            subclassesCache = new SoftReference(subclasses);
        }
        return subclasses;
    }

    public Method concreteMethodByName(String name, String signature)  {
       checkPrepared();
       List methods = visibleMethods();
       Method method = null;
       Iterator iter = methods.iterator();
       while (iter.hasNext()) {
           Method candidate = (Method)iter.next();
           if (candidate.name().equals(name) &&
               candidate.signature().equals(signature) &&
               !candidate.isAbstract()) {

               method = candidate;
               break;
           }
       }
       return method;
   }

   List getAllMethods() {
        ArrayList list = new ArrayList(methods());
        ClassType clazz = superclass();
        while (clazz != null) {
            list.addAll(clazz.methods());
            clazz = clazz.superclass();
        }
        /*
         * Avoid duplicate checking on each method by iterating through
         * duplicate-free allInterfaces() rather than recursing
         */
        Iterator iter = allInterfaces().iterator();
        while (iter.hasNext()) {
            InterfaceType interfaze = (InterfaceType)iter.next();
            list.addAll(interfaze.methods());
        }
        return list;
    }

    List inheritedTypes() {
        List inherited = new ArrayList(interfaces());
        if (superclass() != null) {
            inherited.add(0, superclass()); /* insert at front */
        }
        return inherited;
    }

    public boolean isEnum() {
        ClassTypeImpl superclass = (ClassTypeImpl) superclass();
        if (superclass != null) {
            return superclass.typeNameAsSymbol().equals(vm.javaLangEnum());
        } else {
            return false;
        }
    }

    public void setValue(Field field, Value value)
        throws InvalidTypeException, ClassNotLoadedException {
        vm.throwNotReadOnlyException("ClassType.setValue(...)");
    }


    public Value invokeMethod(ThreadReference threadIntf, Method methodIntf,
                              List arguments, int options)
                                   throws InvalidTypeException,
                                          ClassNotLoadedException,
                                          IncompatibleThreadStateException,
                                          InvocationException {
        vm.throwNotReadOnlyException("ClassType.invokeMethod(...)");
        return null;
    }

    public ObjectReference newInstance(ThreadReference threadIntf,
                                       Method methodIntf,
                                       List arguments, int options)
                                   throws InvalidTypeException,
                                          ClassNotLoadedException,
                                          IncompatibleThreadStateException,
                                          InvocationException {
        vm.throwNotReadOnlyException("ClassType.newInstance(...)");
        return null;
    }

    void addVisibleMethods(Map methodMap) {
        /*
         * Add methods from
         * parent types first, so that the methods in this class will
         * overwrite them in the hash table
         */

        Iterator iter = interfaces().iterator();
        while (iter.hasNext()) {
            InterfaceTypeImpl interfaze = (InterfaceTypeImpl)iter.next();
            interfaze.addVisibleMethods(methodMap);
        }

        ClassTypeImpl clazz = (ClassTypeImpl)superclass();
        if (clazz != null) {
            clazz.addVisibleMethods(methodMap);
        }

        addToMethodMap(methodMap, methods());
    }

    boolean isAssignableTo(ReferenceType type) {
        ClassTypeImpl superclazz = (ClassTypeImpl)superclass();
        if (this.equals(type)) {
            return true;
        } else if ((superclazz != null) && superclazz.isAssignableTo(type)) {
            return true;
        } else {
            List interfaces = interfaces();
            Iterator iter = interfaces.iterator();
            while (iter.hasNext()) {
                InterfaceTypeImpl interfaze = (InterfaceTypeImpl)iter.next();
                if (interfaze.isAssignableTo(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    public String toString() {
       return "class " + name() + "(" + loaderString() + ")";
    }
}
