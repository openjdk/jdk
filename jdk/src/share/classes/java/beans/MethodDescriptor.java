/*
 * Copyright 1996-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.beans;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

/**
 * A MethodDescriptor describes a particular method that a Java Bean
 * supports for external access from other components.
 */

public class MethodDescriptor extends FeatureDescriptor {

    private Reference<Method> methodRef;

    private String[] paramNames;

    private List params;

    private ParameterDescriptor parameterDescriptors[];

    /**
     * Constructs a <code>MethodDescriptor</code> from a
     * <code>Method</code>.
     *
     * @param method    The low-level method information.
     */
    public MethodDescriptor(Method method) {
        this(method, null);
    }


    /**
     * Constructs a <code>MethodDescriptor</code> from a
     * <code>Method</code> providing descriptive information for each
     * of the method's parameters.
     *
     * @param method    The low-level method information.
     * @param parameterDescriptors  Descriptive information for each of the
     *                          method's parameters.
     */
    public MethodDescriptor(Method method,
                ParameterDescriptor parameterDescriptors[]) {
        setName(method.getName());
        setMethod(method);
        this.parameterDescriptors = parameterDescriptors;
    }

    /**
     * Gets the method that this MethodDescriptor encapsualtes.
     *
     * @return The low-level description of the method
     */
    public synchronized Method getMethod() {
        Method method = getMethod0();
        if (method == null) {
            Class cls = getClass0();
            if (cls != null) {
                Class[] params = getParams();
                if (params == null) {
                    for (int i = 0; i < 3; i++) {
                        // Find methods for up to 2 params. We are guessing here.
                        // This block should never execute unless the classloader
                        // that loaded the argument classes disappears.
                        method = Introspector.findMethod(cls, getName(), i, null);
                        if (method != null) {
                            break;
                        }
                    }
                } else {
                    method = Introspector.findMethod(cls, getName(),
                                                     params.length, params);
                }
                setMethod(method);
            }
        }
        return method;
    }

    private synchronized void setMethod(Method method) {
        if (method == null) {
            return;
        }
        if (getClass0() == null) {
            setClass0(method.getDeclaringClass());
        }
        setParams(getParameterTypes(getClass0(), method));
        this.methodRef = getSoftReference(method);
    }

    private Method getMethod0() {
        return (this.methodRef != null)
                ? this.methodRef.get()
                : null;
    }

    private synchronized void setParams(Class[] param) {
        if (param == null) {
            return;
        }
        paramNames = new String[param.length];
        params = new ArrayList(param.length);
        for (int i = 0; i < param.length; i++) {
            paramNames[i] = param[i].getName();
            params.add(new WeakReference(param[i]));
        }
    }

    // pp getParamNames used as an optimization to avoid method.getParameterTypes.
    String[] getParamNames() {
        return paramNames;
    }

    private synchronized Class[] getParams() {
        Class[] clss = new Class[params.size()];

        for (int i = 0; i < params.size(); i++) {
            Reference ref = (Reference)params.get(i);
            Class cls = (Class)ref.get();
            if (cls == null) {
                return null;
            } else {
                clss[i] = cls;
            }
        }
        return clss;
    }

    /**
     * Gets the ParameterDescriptor for each of this MethodDescriptor's
     * method's parameters.
     *
     * @return The locale-independent names of the parameters.  May return
     *          a null array if the parameter names aren't known.
     */
    public ParameterDescriptor[] getParameterDescriptors() {
        return parameterDescriptors;
    }

    /*
     * Package-private constructor
     * Merge two method descriptors.  Where they conflict, give the
     * second argument (y) priority over the first argument (x).
     * @param x  The first (lower priority) MethodDescriptor
     * @param y  The second (higher priority) MethodDescriptor
     */

    MethodDescriptor(MethodDescriptor x, MethodDescriptor y) {
        super(x,y);

        methodRef = x.methodRef;
        if (y.methodRef != null) {
            methodRef = y.methodRef;
        }
        params = x.params;
        if (y.params != null) {
            params = y.params;
        }
        paramNames = x.paramNames;
        if (y.paramNames != null) {
            paramNames = y.paramNames;
        }

        parameterDescriptors = x.parameterDescriptors;
        if (y.parameterDescriptors != null) {
            parameterDescriptors = y.parameterDescriptors;
        }
    }

    /*
     * Package-private dup constructor
     * This must isolate the new object from any changes to the old object.
     */
    MethodDescriptor(MethodDescriptor old) {
        super(old);

        methodRef = old.methodRef;
        params = old.params;
        paramNames = old.paramNames;

        if (old.parameterDescriptors != null) {
            int len = old.parameterDescriptors.length;
            parameterDescriptors = new ParameterDescriptor[len];
            for (int i = 0; i < len ; i++) {
                parameterDescriptors[i] = new ParameterDescriptor(old.parameterDescriptors[i]);
            }
        }
    }

    void appendTo(StringBuilder sb) {
        appendTo(sb, "method", this.methodRef);
        if (this.parameterDescriptors != null) {
            sb.append("; parameterDescriptors={");
            for (ParameterDescriptor pd : this.parameterDescriptors) {
                sb.append(pd).append(", ");
            }
            sb.setLength(sb.length() - 2);
            sb.append("}");
        }
    }
}
