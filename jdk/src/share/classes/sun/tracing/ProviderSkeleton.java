/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.tracing;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.AnnotatedElement;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.security.AccessController;
import java.security.PrivilegedAction;

import com.sun.tracing.Provider;
import com.sun.tracing.Probe;
import com.sun.tracing.ProviderName;

/**
 * Provides a common code for implementation of {@code Provider} classes.
 *
 * Each tracing subsystem needs to provide three classes, a factory
 * (derived from {@code ProviderFactory}, a provider (a subclass of
 * {@code Provider}, and a probe type (subclass of {@code ProbeSkeleton}).
 *
 * The factory object takes a user-defined interface and provides an
 * implementation of it whose method calls will trigger probes in the
 * tracing framework.
 *
 * The framework's provider class, and its instances, are not seen by the
 * user at all -- they usually sit in the background and receive and dispatch
 * the calls to the user's provider interface.  The {@code ProviderSkeleton}
 * class provides almost all of the implementation needed by a framework
 * provider.  Framework providers must only provide a constructor and
 * disposal method, and implement the {@code createProbe} method to create
 * an appropriate {@code ProbeSkeleton} subclass.
 *
 * The framework's probe class provides the implementation of the two
 * probe methods, {@code isEnabled()} and {@code uncheckedTrigger()}.  Both are
 * framework-dependent implementations.
 *
 * @since 1.7
 */

public abstract class ProviderSkeleton implements InvocationHandler, Provider {

    protected boolean active; // set to false after dispose() is called
    protected Class<? extends Provider> providerType; // user's interface
    protected HashMap<Method, ProbeSkeleton> probes; // methods to probes


    /**
     * Creates a framework-specific probe subtype.
     *
     * This method is implemented by the framework's provider and returns
     * framework-specific probes for a method.
     *
     * @param method A method in the user's interface
     * @return a subclass of ProbeSkeleton for the particular framework.
     */
    protected abstract ProbeSkeleton createProbe(Method method);

    /**
     * Initializes the provider.
     *
     * @param type the user's interface
     */
    protected ProviderSkeleton(Class<? extends Provider> type) {
        this.active = false; // in case of some error during initialization
        this.providerType = type;
        this.probes = new HashMap<Method,ProbeSkeleton>();
    }

    /**
     * Post-constructor initialization routine.
     *
     * Subclass instances must be initialized before they can create probes.
     * It is up to the factory implementations to call this after construction.
     */
    public void init() {
        Method[] methods = AccessController.doPrivileged(new PrivilegedAction<Method[]>() {
            public Method[] run() {
                return providerType.getDeclaredMethods();
            }
        });

        for (Method m : methods) {
            if ( m.getReturnType() != Void.TYPE ) {
                throw new IllegalArgumentException(
                   "Return value of method is not void");
            } else {
                probes.put(m, createProbe(m));
            }
        }
        this.active = true;
    }

    /**
     * Magic routine which creates an implementation of the user's interface.
     *
     * This method creates the instance of the user's interface which is
     * passed back to the user.  Every call upon that interface will be
     * redirected to the {@code invoke()} method of this class (until
     * overridden by the VM).
     *
     * @return an implementation of the user's interface
     */
    @SuppressWarnings("unchecked")
    public <T extends Provider> T newProxyInstance() {
        return (T)Proxy.newProxyInstance(providerType.getClassLoader(),
               new Class<?>[] { providerType }, this);
    }

    /**
     * Triggers a framework probe when a user interface method is called.
     *
     * This method dispatches a user interface method call to the appropriate
     * probe associated with this framework.
     *
     * If the invoked method is not a user-defined member of the interface,
     * then it is a member of {@code Provider} or {@code Object} and we
     * invoke the method directly.
     *
     * @param proxy the instance whose method was invoked
     * @param method the method that was called
     * @param args the arguments passed in the call.
     * @return always null, if the method is a user-defined probe
     */
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() != providerType) {
            try {
                return method.invoke(this, args);
            } catch (IllegalAccessException e) {
                assert false;
            } catch (InvocationTargetException e) {
                assert false;
            }
        } else if (active) {
            ProbeSkeleton p = probes.get(method);
            if (p != null) {
                // Skips argument check -- already done by javac
                p.uncheckedTrigger(args);
            }
        }
        return null;
    }

    /**
     * Direct accessor for {@code Probe} objects.
     *
     * @param m the method corresponding to a probe
     * @return the method associated probe object, or null
     */
    public Probe getProbe(Method m) {
        return active ? probes.get(m) : null;
    }

    /**
     * Default provider disposal method.
     *
     * This is overridden in subclasses as needed.
     */
    public void dispose() {
        active = false;
        probes.clear();
    }

    /**
     * Gets the user-specified provider name for the user's interface.
     *
     * If the user's interface has a {@ProviderName} annotation, that value
     * is used.  Otherwise we use the simple name of the user interface's class.
     * @return the provider name
     */
    protected String getProviderName() {
        return getAnnotationString(
                providerType, ProviderName.class, providerType.getSimpleName());
    }

    /**
     * Utility method for getting a string value from an annotation.
     *
     * Used for getting a string value from an annotation with a 'value' method.
     *
     * @param element the element that was annotated, either a class or method
     * @param annotation the class of the annotation we're interested in
     * @param defaultValue the value to return if the annotation doesn't
     * exist, doesn't have a "value", or the value is empty.
     */
    protected static String getAnnotationString(
            AnnotatedElement element, Class<? extends Annotation> annotation,
            String defaultValue) {
        String ret = (String)getAnnotationValue(
                element, annotation, "value", defaultValue);
        return ret.isEmpty() ? defaultValue : ret;
    }

    /**
     * Utility method for calling an arbitrary method in an annotation.
     *
     * @param element the element that was annotated, either a class or method
     * @param annotation the class of the annotation we're interested in
     * @param methodName the name of the method in the annotation we wish
     * to call.
     * @param defaultValue the value to return if the annotation doesn't
     * exist, or we couldn't invoke the method for some reason.
     * @return the result of calling the annotation method, or the default.
     */
    protected static Object getAnnotationValue(
            AnnotatedElement element, Class<? extends Annotation> annotation,
            String methodName, Object defaultValue) {
        Object ret = defaultValue;
        try {
            Method m = annotation.getMethod(methodName);
            Annotation a = element.getAnnotation(annotation);
            ret = m.invoke(a);
        } catch (NoSuchMethodException e) {
            assert false;
        } catch (IllegalAccessException e) {
            assert false;
        } catch (InvocationTargetException e) {
            assert false;
        } catch (NullPointerException e) {
            assert false;
        }
        return ret;
    }
}
