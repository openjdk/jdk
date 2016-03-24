/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import java.lang.reflect.Module;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jdk.dynalink.beans.StaticClass;
import jdk.nashorn.internal.codegen.DumpBytecode;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.internal.module.Modules;

/**
 * This class encapsulates the bytecode of the adapter class and can be used to load it into the JVM as an actual Class.
 * It can be invoked repeatedly to create multiple adapter classes from the same bytecode; adapter classes that have
 * class-level overrides must be re-created for every set of such overrides. Note that while this class is named
 * "class loader", it does not, in fact, extend {@code ClassLoader}, but rather uses them internally. Instances of this
 * class are normally created by {@code JavaAdapterBytecodeGenerator}.
 */
final class JavaAdapterClassLoader {
    private static final Module nashornModule = JavaAdapterClassLoader.class.getModule();
    private static final Set<String> adapterPkgs = new HashSet<>();
    static {
        adapterPkgs.add(JavaAdapterBytecodeGenerator.ADAPTER_PACKAGE);
    }

    private static final AccessControlContext CREATE_LOADER_ACC_CTXT = ClassAndLoader.createPermAccCtxt("createClassLoader");
    private static final AccessControlContext GET_CONTEXT_ACC_CTXT = ClassAndLoader.createPermAccCtxt(Context.NASHORN_GET_CONTEXT);
    private static final Collection<String> VISIBLE_INTERNAL_CLASS_NAMES = Collections.unmodifiableCollection(new HashSet<>(
            Arrays.asList(JavaAdapterServices.class.getName(), ScriptObject.class.getName(), ScriptFunction.class.getName(), JSType.class.getName())));

    private final String className;
    private final byte[] classBytes;
    private final Set<Module> accessedModules;

    JavaAdapterClassLoader(final String className, final byte[] classBytes, final Set<Module> accessedModules) {
        this.className = className.replace('/', '.');
        this.classBytes = classBytes;
        this.accessedModules = accessedModules;
    }

    /**
     * Loads the generated adapter class into the JVM.
     * @param parentLoader the parent class loader for the generated class loader
     * @param protectionDomain the protection domain for the generated class
     * @return the generated adapter class
     */
    StaticClass generateClass(final ClassLoader parentLoader, final ProtectionDomain protectionDomain) {
        assert protectionDomain != null;
        return AccessController.doPrivileged(new PrivilegedAction<StaticClass>() {
            @Override
            public StaticClass run() {
                try {
                    return StaticClass.forClass(Class.forName(className, true, createClassLoader(parentLoader, protectionDomain)));
                } catch (final ClassNotFoundException e) {
                    throw new AssertionError(e); // cannot happen
                }
            }
        }, CREATE_LOADER_ACC_CTXT);
    }

    private static void addExports(final Module from, final String pkg, final Module to) {
        if (to == null) {
            Modules.addExportsToAll(from, pkg);
        } else {
            Modules.addExports(from, pkg, to);
        }
    }

    // Note that the adapter class is created in the protection domain of the class/interface being
    // extended/implemented, and only the privileged global setter action class is generated in the protection domain
    // of Nashorn itself. Also note that the creation and loading of the global setter is deferred until it is
    // required by JVM linker, which will only happen on first invocation of any of the adapted method. We could defer
    // it even more by separating its invocation into a separate static method on the adapter class, but then someone
    // with ability to introspect on the class and use setAccessible(true) on it could invoke the method. It's a
    // security tradeoff...
    private ClassLoader createClassLoader(final ClassLoader parentLoader, final ProtectionDomain protectionDomain) {
        return new SecureClassLoader(parentLoader) {
            private final ClassLoader myLoader = getClass().getClassLoader();

            // new adapter module
            private final Module adapterModule = Modules.defineModule(this, "jdk.scripting.nashorn.javaadapters", adapterPkgs);

            {
                // new adapter module exports and read-edges
                addExports(adapterModule, JavaAdapterBytecodeGenerator.ADAPTER_PACKAGE, null);
                Modules.addReads(adapterModule, nashornModule);
                Modules.addReads(adapterModule, Object.class.getModule());
                for (Module mod : accessedModules) {
                    Modules.addReads(adapterModule, mod);
                }

                // specific exports from nashorn to the new adapter module
                nashornModule.addExports("jdk.nashorn.internal.runtime", adapterModule);
                nashornModule.addExports("jdk.nashorn.internal.runtime.linker", adapterModule);

                // nashorn should be be able to read methods of classes loaded in adapter module
                nashornModule.addReads(adapterModule);
            }

            @Override
            public Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                try {
                    Context.checkPackageAccess(name);
                    return super.loadClass(name, resolve);
                } catch (final SecurityException se) {
                    // we may be implementing an interface or extending a class that was
                    // loaded by a loader that prevents package.access. If so, it'd throw
                    // SecurityException for nashorn's classes!. For adapter's to work, we
                    // should be able to refer to the few classes it needs in its implementation.
                    if(VISIBLE_INTERNAL_CLASS_NAMES.contains(name)) {
                        return myLoader != null? myLoader.loadClass(name) : Class.forName(name, false, myLoader);
                    }
                    throw se;
                }
            }

            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                if(name.equals(className)) {
                    assert classBytes != null : "what? already cleared .class bytes!!";

                    final Context ctx = AccessController.doPrivileged(new PrivilegedAction<Context>() {
                        @Override
                        public Context run() {
                            return Context.getContext();
                        }
                    }, GET_CONTEXT_ACC_CTXT);
                    DumpBytecode.dumpBytecode(ctx.getEnv(), ctx.getLogger(jdk.nashorn.internal.codegen.Compiler.class), classBytes, name);
                    return defineClass(name, classBytes, 0, classBytes.length, protectionDomain);
                }
                throw new ClassNotFoundException(name);
            }
        };
    }
}
