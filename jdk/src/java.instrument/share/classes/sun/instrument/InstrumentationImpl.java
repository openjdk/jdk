/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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


package sun.instrument;

import java.lang.reflect.Method;
import java.lang.reflect.Module;
import java.lang.reflect.AccessibleObject;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

import java.util.Objects;
import java.util.jar.JarFile;

/*
 * Copyright 2003 Wily Technology, Inc.
 */

/**
 * The Java side of the JPLIS implementation. Works in concert with a native JVMTI agent
 * to implement the JPLIS API set. Provides both the Java API implementation of
 * the Instrumentation interface and utility Java routines to support the native code.
 * Keeps a pointer to the native data structure in a scalar field to allow native
 * processing behind native methods.
 */
public class InstrumentationImpl implements Instrumentation {
    private final     TransformerManager      mTransformerManager;
    private           TransformerManager      mRetransfomableTransformerManager;
    // needs to store a native pointer, so use 64 bits
    private final     long                    mNativeAgent;
    private final     boolean                 mEnvironmentSupportsRedefineClasses;
    private volatile  boolean                 mEnvironmentSupportsRetransformClassesKnown;
    private volatile  boolean                 mEnvironmentSupportsRetransformClasses;
    private final     boolean                 mEnvironmentSupportsNativeMethodPrefix;

    private
    InstrumentationImpl(long    nativeAgent,
                        boolean environmentSupportsRedefineClasses,
                        boolean environmentSupportsNativeMethodPrefix) {
        mTransformerManager                    = new TransformerManager(false);
        mRetransfomableTransformerManager      = null;
        mNativeAgent                           = nativeAgent;
        mEnvironmentSupportsRedefineClasses    = environmentSupportsRedefineClasses;
        mEnvironmentSupportsRetransformClassesKnown = false; // false = need to ask
        mEnvironmentSupportsRetransformClasses = false;      // don't know yet
        mEnvironmentSupportsNativeMethodPrefix = environmentSupportsNativeMethodPrefix;
    }

    public void
    addTransformer(ClassFileTransformer transformer) {
        addTransformer(transformer, false);
    }

    public synchronized void
    addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
        if (transformer == null) {
            throw new NullPointerException("null passed as 'transformer' in addTransformer");
        }
        if (canRetransform) {
            if (!isRetransformClassesSupported()) {
                throw new UnsupportedOperationException(
                  "adding retransformable transformers is not supported in this environment");
            }
            if (mRetransfomableTransformerManager == null) {
                mRetransfomableTransformerManager = new TransformerManager(true);
            }
            mRetransfomableTransformerManager.addTransformer(transformer);
            if (mRetransfomableTransformerManager.getTransformerCount() == 1) {
                setHasRetransformableTransformers(mNativeAgent, true);
            }
        } else {
            mTransformerManager.addTransformer(transformer);
        }
    }

    public synchronized boolean
    removeTransformer(ClassFileTransformer transformer) {
        if (transformer == null) {
            throw new NullPointerException("null passed as 'transformer' in removeTransformer");
        }
        TransformerManager mgr = findTransformerManager(transformer);
        if (mgr != null) {
            mgr.removeTransformer(transformer);
            if (mgr.isRetransformable() && mgr.getTransformerCount() == 0) {
                setHasRetransformableTransformers(mNativeAgent, false);
            }
            return true;
        }
        return false;
    }

    public boolean
    isModifiableClass(Class<?> theClass) {
        if (theClass == null) {
            throw new NullPointerException(
                         "null passed as 'theClass' in isModifiableClass");
        }
        return isModifiableClass0(mNativeAgent, theClass);
    }

    public boolean
    isRetransformClassesSupported() {
        // ask lazily since there is some overhead
        if (!mEnvironmentSupportsRetransformClassesKnown) {
            mEnvironmentSupportsRetransformClasses = isRetransformClassesSupported0(mNativeAgent);
            mEnvironmentSupportsRetransformClassesKnown = true;
        }
        return mEnvironmentSupportsRetransformClasses;
    }

    public void
    retransformClasses(Class<?>... classes) {
        if (!isRetransformClassesSupported()) {
            throw new UnsupportedOperationException(
              "retransformClasses is not supported in this environment");
        }
        retransformClasses0(mNativeAgent, classes);
    }

    public boolean
    isRedefineClassesSupported() {
        return mEnvironmentSupportsRedefineClasses;
    }

    public void
    redefineClasses(ClassDefinition...  definitions)
            throws  ClassNotFoundException {
        if (!isRedefineClassesSupported()) {
            throw new UnsupportedOperationException("redefineClasses is not supported in this environment");
        }
        if (definitions == null) {
            throw new NullPointerException("null passed as 'definitions' in redefineClasses");
        }
        for (int i = 0; i < definitions.length; ++i) {
            if (definitions[i] == null) {
                throw new NullPointerException("element of 'definitions' is null in redefineClasses");
            }
        }
        if (definitions.length == 0) {
            return; // short-circuit if there are no changes requested
        }

        redefineClasses0(mNativeAgent, definitions);
    }

    @SuppressWarnings("rawtypes")
    public Class[]
    getAllLoadedClasses() {
        return getAllLoadedClasses0(mNativeAgent);
    }

    @SuppressWarnings("rawtypes")
    public Class[]
    getInitiatedClasses(ClassLoader loader) {
        return getInitiatedClasses0(mNativeAgent, loader);
    }

    public long
    getObjectSize(Object objectToSize) {
        if (objectToSize == null) {
            throw new NullPointerException("null passed as 'objectToSize' in getObjectSize");
        }
        return getObjectSize0(mNativeAgent, objectToSize);
    }

    public void
    appendToBootstrapClassLoaderSearch(JarFile jarfile) {
        appendToClassLoaderSearch0(mNativeAgent, jarfile.getName(), true);
    }

    public void
    appendToSystemClassLoaderSearch(JarFile jarfile) {
        appendToClassLoaderSearch0(mNativeAgent, jarfile.getName(), false);
    }

    public boolean
    isNativeMethodPrefixSupported() {
        return mEnvironmentSupportsNativeMethodPrefix;
    }

    public synchronized void
    setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
        if (!isNativeMethodPrefixSupported()) {
            throw new UnsupportedOperationException(
                   "setNativeMethodPrefix is not supported in this environment");
        }
        if (transformer == null) {
            throw new NullPointerException(
                       "null passed as 'transformer' in setNativeMethodPrefix");
        }
        TransformerManager mgr = findTransformerManager(transformer);
        if (mgr == null) {
            throw new IllegalArgumentException(
                       "transformer not registered in setNativeMethodPrefix");
        }
        mgr.setNativeMethodPrefix(transformer, prefix);
        String[] prefixes = mgr.getNativeMethodPrefixes();
        setNativeMethodPrefixes(mNativeAgent, prefixes, mgr.isRetransformable());
    }

    @Override
    public void addModuleReads(Module module, Module other) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(other);
        jdk.internal.module.Modules.addReads(module, other);
    }


    private TransformerManager
    findTransformerManager(ClassFileTransformer transformer) {
        if (mTransformerManager.includesTransformer(transformer)) {
            return mTransformerManager;
        }
        if (mRetransfomableTransformerManager != null &&
                mRetransfomableTransformerManager.includesTransformer(transformer)) {
            return mRetransfomableTransformerManager;
        }
        return null;
    }


    /*
     *  Natives
     */
    private native boolean
    isModifiableClass0(long nativeAgent, Class<?> theClass);

    private native boolean
    isRetransformClassesSupported0(long nativeAgent);

    private native void
    setHasRetransformableTransformers(long nativeAgent, boolean has);

    private native void
    retransformClasses0(long nativeAgent, Class<?>[] classes);

    private native void
    redefineClasses0(long nativeAgent, ClassDefinition[]  definitions)
        throws  ClassNotFoundException;

    @SuppressWarnings("rawtypes")
    private native Class[]
    getAllLoadedClasses0(long nativeAgent);

    @SuppressWarnings("rawtypes")
    private native Class[]
    getInitiatedClasses0(long nativeAgent, ClassLoader loader);

    private native long
    getObjectSize0(long nativeAgent, Object objectToSize);

    private native void
    appendToClassLoaderSearch0(long nativeAgent, String jarfile, boolean bootLoader);

    private native void
    setNativeMethodPrefixes(long nativeAgent, String[] prefixes, boolean isRetransformable);

    static {
        System.loadLibrary("instrument");
    }

    /*
     *  Internals
     */


    // Enable or disable Java programming language access checks on a
    // reflected object (for example, a method)
    private static void setAccessible(final AccessibleObject ao, final boolean accessible) {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    ao.setAccessible(accessible);
                    return null;
                }});
    }

    // Attempt to load and start an agent
    private void
    loadClassAndStartAgent( String  classname,
                            String  methodname,
                            String  optionsString)
            throws Throwable {

        ClassLoader mainAppLoader   = ClassLoader.getSystemClassLoader();
        Class<?>    javaAgentClass  = mainAppLoader.loadClass(classname);

        Method m = null;
        NoSuchMethodException firstExc = null;
        boolean twoArgAgent = false;

        // The agent class must have a premain or agentmain method that
        // has 1 or 2 arguments. We check in the following order:
        //
        // 1) declared with a signature of (String, Instrumentation)
        // 2) declared with a signature of (String)
        // 3) inherited with a signature of (String, Instrumentation)
        // 4) inherited with a signature of (String)
        //
        // So the declared version of either 1-arg or 2-arg always takes
        // primary precedence over an inherited version. After that, the
        // 2-arg version takes precedence over the 1-arg version.
        //
        // If no method is found then we throw the NoSuchMethodException
        // from the first attempt so that the exception text indicates
        // the lookup failed for the 2-arg method (same as JDK5.0).

        try {
            m = javaAgentClass.getDeclaredMethod( methodname,
                                 new Class<?>[] {
                                     String.class,
                                     java.lang.instrument.Instrumentation.class
                                 }
                               );
            twoArgAgent = true;
        } catch (NoSuchMethodException x) {
            // remember the NoSuchMethodException
            firstExc = x;
        }

        if (m == null) {
            // now try the declared 1-arg method
            try {
                m = javaAgentClass.getDeclaredMethod(methodname,
                                                 new Class<?>[] { String.class });
            } catch (NoSuchMethodException x) {
                // ignore this exception because we'll try
                // two arg inheritance next
            }
        }

        if (m == null) {
            // now try the inherited 2-arg method
            try {
                m = javaAgentClass.getMethod( methodname,
                                 new Class<?>[] {
                                     String.class,
                                     java.lang.instrument.Instrumentation.class
                                 }
                               );
                twoArgAgent = true;
            } catch (NoSuchMethodException x) {
                // ignore this exception because we'll try
                // one arg inheritance next
            }
        }

        if (m == null) {
            // finally try the inherited 1-arg method
            try {
                m = javaAgentClass.getMethod(methodname,
                                             new Class<?>[] { String.class });
            } catch (NoSuchMethodException x) {
                // none of the methods exists so we throw the
                // first NoSuchMethodException as per 5.0
                throw firstExc;
            }
        }

        // the premain method should not be required to be public,
        // make it accessible so we can call it
        // Note: The spec says the following:
        //     The agent class must implement a public static premain method...
        setAccessible(m, true);

        // invoke the 1 or 2-arg method
        if (twoArgAgent) {
            m.invoke(null, new Object[] { optionsString, this });
        } else {
            m.invoke(null, new Object[] { optionsString });
        }
    }

    // WARNING: the native code knows the name & signature of this method
    private void
    loadClassAndCallPremain(    String  classname,
                                String  optionsString)
            throws Throwable {

        loadClassAndStartAgent( classname, "premain", optionsString );
    }


    // WARNING: the native code knows the name & signature of this method
    private void
    loadClassAndCallAgentmain(  String  classname,
                                String  optionsString)
            throws Throwable {

        loadClassAndStartAgent( classname, "agentmain", optionsString );
    }

    // WARNING: the native code knows the name & signature of this method
    private byte[]
    transform(  ClassLoader         loader,
                Module              module,
                String              classname,
                Class<?>            classBeingRedefined,
                ProtectionDomain    protectionDomain,
                byte[]              classfileBuffer,
                boolean             isRetransformer) {
        TransformerManager mgr = isRetransformer?
                                        mRetransfomableTransformerManager :
                                        mTransformerManager;
        // module is null when not a class load or when loading a class in an
        // unnamed module and this is the first type to be loaded in the package.
        if (module == null) {
            if (classBeingRedefined != null) {
                module = classBeingRedefined.getModule();
            } else {
                module = loader.getUnnamedModule();
            }
        }
        if (mgr == null) {
            return null; // no manager, no transform
        } else {
            return mgr.transform(   module,
                                    classname,
                                    classBeingRedefined,
                                    protectionDomain,
                                    classfileBuffer);
        }
    }
}
