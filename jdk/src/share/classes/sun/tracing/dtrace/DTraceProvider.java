/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.tracing.dtrace;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.annotation.Annotation;

import sun.tracing.ProviderSkeleton;
import sun.tracing.ProbeSkeleton;
import com.sun.tracing.Provider;
import com.sun.tracing.ProbeName;
import com.sun.tracing.dtrace.Attributes;
import com.sun.tracing.dtrace.ModuleName;
import com.sun.tracing.dtrace.FunctionName;
import com.sun.tracing.dtrace.StabilityLevel;
import com.sun.tracing.dtrace.DependencyClass;

import sun.misc.ProxyGenerator;

class DTraceProvider extends ProviderSkeleton {

    private Activation activation;
    private Object proxy;

    // For proxy generation
    private final static Class[] constructorParams = { InvocationHandler.class };
    private final String proxyClassNamePrefix = "$DTraceTracingProxy";

    static final String DEFAULT_MODULE = "java_tracing";
    static final String DEFAULT_FUNCTION = "unspecified";

    private static long nextUniqueNumber = 0;
    private static synchronized long getUniqueNumber() {
        return nextUniqueNumber++;
    }

    protected ProbeSkeleton createProbe(Method m) {
        return new DTraceProbe(proxy, m);
    }

    DTraceProvider(Class<? extends Provider> type) {
        super(type);
    }

    void setProxy(Object p) {
        proxy = p;
    }

    void setActivation(Activation a) {
        this.activation = a;
    }

    public void dispose() {
        if (activation != null) {
            activation.disposeProvider(this);
            activation = null;
        }
        super.dispose();
    }

    /**
     * Magic routine which creates an implementation of the user's interface.
     *
     * This method uses the ProxyGenerator directly to bypass the
     * java.lang.reflect.proxy cache so that we get a unique class each
     * time it's called and can't accidently reuse a $Proxy class.
     *
     * @return an implementation of the user's interface
     */
    @SuppressWarnings("unchecked")
    public <T extends Provider> T newProxyInstance() {
        /*
         * Choose a name for the proxy class to generate.
         */
        long num = getUniqueNumber();

        String proxyPkg = "";
        if (!Modifier.isPublic(providerType.getModifiers())) {
            String name = providerType.getName();
            int n = name.lastIndexOf('.');
            proxyPkg = ((n == -1) ? "" : name.substring(0, n + 1));
        }

        String proxyName = proxyPkg + proxyClassNamePrefix + num;

        /*
         * Generate the specified proxy class.
         */
        Class<?> proxyClass = null;
        byte[] proxyClassFile = ProxyGenerator.generateProxyClass(
                proxyName, new Class<?>[] { providerType });
        try {
            proxyClass = JVM.defineClass(
                providerType.getClassLoader(), proxyName,
                proxyClassFile, 0, proxyClassFile.length);
        } catch (ClassFormatError e) {
            /*
             * A ClassFormatError here means that (barring bugs in the
             * proxy class generation code) there was some other
             * invalid aspect of the arguments supplied to the proxy
             * class creation (such as virtual machine limitations
             * exceeded).
             */
            throw new IllegalArgumentException(e.toString());
        }

        /*
         * Invoke its constructor with the designated invocation handler.
         */
        try {
            Constructor cons = proxyClass.getConstructor(constructorParams);
            return (T)cons.newInstance(new Object[] { this });
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e.toString(), e);
        }
    }

    // In the normal case, the proxy object's method implementations will call
    // this method (it usually calls the ProviderSkeleton's version).  That
    // method uses the passed 'method' object to lookup the associated
    // 'ProbeSkeleton' and calls uncheckedTrigger() on that probe to cause the
    // probe to fire.  DTrace probes are different in that the proxy class's
    // methods are immediately overridden with native code to fire the probe
    // directly.  So this method should never get invoked.  We also wire up the
    // DTraceProbe.uncheckedTrigger() method to call the proxy method instead
    // of doing the work itself.
    protected void triggerProbe(Method method, Object[] args) {
        assert false : "This method should have been overridden by the JVM";
    }

    public String getProviderName() {
        return super.getProviderName();
    }

    String getModuleName() {
        return getAnnotationString(
            providerType, ModuleName.class, DEFAULT_MODULE);
    }

    static String getProbeName(Method method) {
        return getAnnotationString(
            method, ProbeName.class, method.getName());
    }

    static String getFunctionName(Method method) {
        return getAnnotationString(
            method, FunctionName.class, DEFAULT_FUNCTION);
    }

    DTraceProbe[] getProbes() {
        return probes.values().toArray(new DTraceProbe[0]);
    }

    StabilityLevel getNameStabilityFor(Class<? extends Annotation> type) {
        Attributes attrs = (Attributes)getAnnotationValue(
            providerType, type, "value", null);
        if (attrs == null) {
            return StabilityLevel.PRIVATE;
        } else {
            return attrs.name();
        }
    }

    StabilityLevel getDataStabilityFor(Class<? extends Annotation> type) {
        Attributes attrs = (Attributes)getAnnotationValue(
            providerType, type, "value", null);
        if (attrs == null) {
            return StabilityLevel.PRIVATE;
        } else {
            return attrs.data();
        }
    }

    DependencyClass getDependencyClassFor(Class<? extends Annotation> type) {
        Attributes attrs = (Attributes)getAnnotationValue(
            providerType, type, "value", null);
        if (attrs == null) {
            return DependencyClass.UNKNOWN;
        } else {
            return attrs.dependency();
        }
    }
}
