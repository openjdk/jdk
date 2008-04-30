/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tracing.dtrace;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import sun.tracing.ProbeSkeleton;

class DTraceProbe extends ProbeSkeleton {
    private Object proxy;
    private Method declared_method;
    private Method implementing_method;

    DTraceProbe(Object proxy, Method m) {
        super(m.getParameterTypes());
        this.proxy = proxy;
        this.declared_method = m;
        try {
            // The JVM will override the proxy method's implementation with
            // a version that will invoke the probe.
            this.implementing_method =  proxy.getClass().getMethod(
                m.getName(), m.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Internal error, wrong proxy class");
        }
    }

    public boolean isEnabled() {
        return JVM.isEnabled(implementing_method);
    }

    public void uncheckedTrigger(Object[] args) {
        try {
            implementing_method.invoke(proxy, args);
        } catch (IllegalAccessException e) {
            assert false;
        } catch (InvocationTargetException e) {
            assert false;
        }
    }

    String getProbeName() {
        return DTraceProvider.getProbeName(declared_method);
    }

    String getFunctionName() {
        return DTraceProvider.getFunctionName(declared_method);
    }

    Method getMethod() {
        return implementing_method;
    }

    Class<?>[] getParameterTypes() {
        return this.parameters;
    }
}

