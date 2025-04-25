/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jmx.mbeanserver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;

import static com.sun.jmx.mbeanserver.Util.*;

/**
 * Per-MBean-interface behavior.  A single instance of this class can be shared
 * by all MBeans of the same kind (Standard MBean or MXBean) that have the same
 * MBean interface.
 *
 * @since 1.6
 */
final class PerInterface<M> {
    PerInterface(Class<?> mbeanInterface, MBeanIntrospector<M> introspector,
                 MBeanAnalyzer<M> analyzer, MBeanInfo mbeanInfo) {
        this.mbeanInterface = mbeanInterface;
        this.introspector = introspector;
        this.mbeanInfo = mbeanInfo;
        analyzer.visit(new InitMaps());
    }

    Class<?> getMBeanInterface() {
        return mbeanInterface;
    }

    MBeanInfo getMBeanInfo() {
        return mbeanInfo;
    }

    boolean isMXBean() {
        return introspector.isMXBean();
    }

    Object getAttribute(Object resource, String attribute, Object cookie)
            throws AttributeNotFoundException,
                   MBeanException,
                   ReflectionException {

        final M cm = getters.get(attribute);
        if (cm == null) {
            final String msg;
            if (setters.containsKey(attribute))
                msg = "Write-only attribute: " + attribute;
            else
                msg = "No such attribute: " + attribute;
            throw new AttributeNotFoundException(msg);
        }
        return introspector.invokeM(cm, resource, (Object[]) null, cookie);
    }

    void setAttribute(Object resource, String attribute, Object value,
                      Object cookie)
            throws AttributeNotFoundException,
                   InvalidAttributeValueException,
                   MBeanException,
                   ReflectionException {

        final M cm = setters.get(attribute);
        if (cm == null) {
            final String msg;
            if (getters.containsKey(attribute))
                msg = "Read-only attribute: " + attribute;
            else
                msg = "No such attribute: " + attribute;
            throw new AttributeNotFoundException(msg);
        }
        introspector.invokeSetter(attribute, cm, resource, value, cookie);
    }

    Object invoke(Object resource, String operation, Object[] params,
                  String[] signature, Object cookie)
            throws MBeanException, ReflectionException {

        final List<MethodAndSig> list = ops.get(operation);
        if (list == null) {
            final String msg = "No such operation: " + operation;
            throw new ReflectionException(new NoSuchMethodException(operation + sigString(signature)), msg);
        }
        if (signature == null)
            signature = new String[0];
        MethodAndSig found = null;
        for (MethodAndSig mas : list) {
            if (Arrays.equals(mas.signature, signature)) {
                found = mas;
                break;
            }
        }
        if (found == null) {
            final String badSig = sigString(signature);
            final String msg;
            if (list.size() == 1) {  // helpful exception message
                msg = "Signature mismatch for operation " + operation +
                        ": " + badSig + " should be " +
                        sigString(list.get(0).signature);
            } else {
                msg = "Operation " + operation + " exists but not with " +
                        "this signature: " + badSig;
            }
            throw new ReflectionException(new NoSuchMethodException(operation + badSig), msg);
        }
        return introspector.invokeM(found.method, resource, params, cookie);
    }

    private String sigString(String[] signature) {
        StringBuilder b = new StringBuilder("(");
        if (signature != null) {
            for (String s : signature) {
                if (b.length() > 1)
                    b.append(", ");
                b.append(s);
            }
        }
        return b.append(")").toString();
    }

    /**
     * Visitor that sets up the method maps (operations, getters, setters).
     */
    private class InitMaps implements MBeanAnalyzer.MBeanVisitor<M> {
        public void visitAttribute(String attributeName,
                                   M getter,
                                   M setter) {
            if (getter != null) {
                introspector.checkMethod(getter);
                final Object old = getters.put(attributeName, getter);
                assert(old == null);
            }
            if (setter != null) {
                introspector.checkMethod(setter);
                final Object old = setters.put(attributeName, setter);
                assert(old == null);
            }
        }

        public void visitOperation(String operationName,
                                   M operation) {
            introspector.checkMethod(operation);
            final String[] sig = introspector.getSignature(operation);
            final MethodAndSig mas = new MethodAndSig();
            mas.method = operation;
            mas.signature = sig;
            List<MethodAndSig> list = ops.get(operationName);
            if (list == null)
                list = Collections.singletonList(mas);
            else {
                if (list.size() == 1)
                    list = newList(list);
                list.add(mas);
            }
            ops.put(operationName, list);
        }
    }

    private class MethodAndSig {
        M method;
        String[] signature;
    }

    private final Class<?> mbeanInterface;
    private final MBeanIntrospector<M> introspector;
    private final MBeanInfo mbeanInfo;
    private final Map<String, M> getters = newMap();
    private final Map<String, M> setters = newMap();
    private final Map<String, List<MethodAndSig>> ops = newMap();
}
