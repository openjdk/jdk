/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.util.*;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.toolkit.Configuration;

/**
 * For a given class method, build an array of interface methods which it
 * implements.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 */
public class ImplementedMethods {

    private final Map<ExecutableElement, TypeMirror> interfaces = new HashMap<>();
    private final List<ExecutableElement> methlist = new ArrayList<>();
    private final Utils utils;
    private final TypeElement typeElement;
    private final ExecutableElement method;

    public ImplementedMethods(ExecutableElement method, Configuration configuration) {
        this.method = method;
        this.utils = configuration.utils;
        typeElement = utils.getEnclosingTypeElement(method);
    }

    /**
     * Return the array of interface methods which the method passed in the
     * constructor is implementing. The search/build order is as follows:
     * <pre>
     * 1. Search in all the immediate interfaces which this method's class is
     *    implementing. Do it recursively for the superinterfaces as well.
     * 2. Traverse all the superclasses and search recursively in the
     *    interfaces which those superclasses implement.
     *</pre>
     *
     * @return SortedSet<ExecutableElement> of implemented methods.
     */
    public List<ExecutableElement> build() {
        buildImplementedMethodList();
        return methlist;
    }

    public TypeMirror getMethodHolder(ExecutableElement ee) {
        return interfaces.get(ee);
    }

    /**
     * Search for the method in the array of interfaces. If found check if it is
     * overridden by any other subinterface method which this class
     * implements. If it is not overidden, add it in the method list.
     * Do this recursively for all the extended interfaces for each interface
     * from the array passed.
     */
    private void buildImplementedMethodList() {
        Set<TypeMirror> intfacs = utils.getAllInterfaces(typeElement);
        for (TypeMirror interfaceType : intfacs) {
            ExecutableElement found = utils.findMethod(utils.asTypeElement(interfaceType), method);
            if (found != null) {
                removeOverriddenMethod(found);
                if (!overridingMethodFound(found)) {
                    methlist.add(found);
                    interfaces.put(found, interfaceType);
                }
            }
        }
    }

    /**
     * Search in the method list and check if it contains a method which
     * is overridden by the method as parameter.  If found, remove the
     * overridden method from the method list.
     *
     * @param method Is this method overriding a method in the method list.
     */
    private void removeOverriddenMethod(ExecutableElement method) {
        TypeElement overriddenClass = utils.overriddenClass(method);
        if (overriddenClass != null) {
            for (int i = 0; i < methlist.size(); i++) {
                TypeElement te = utils.getEnclosingTypeElement(methlist.get(i));
                if (te == overriddenClass || utils.isSubclassOf(overriddenClass, te)) {
                    methlist.remove(i);  // remove overridden method
                    return;
                }
            }
        }
    }

    /**
     * Search in the already found methods' list and check if it contains
     * a method which is overriding the method parameter or is the method
     * parameter itself.
     *
     * @param method MethodDoc Method to be searched in the Method List for
     * an overriding method.
     */
    private boolean overridingMethodFound(ExecutableElement method) {
        TypeElement containingClass = utils.getEnclosingTypeElement(method);
        for (ExecutableElement listmethod : methlist) {
            if (containingClass == utils.getEnclosingTypeElement(listmethod)) {
                // it's the same method.
                return true;
            }
            TypeElement te = utils.overriddenClass(listmethod);
            if (te == null) {
                continue;
            }
            if (te == containingClass || utils.isSubclassOf(te, containingClass)) {
                return true;
            }
        }
        return false;
    }
}
