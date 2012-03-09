/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import sun.misc.Unsafe;

public final class JObjCRuntime {
    static { System.loadLibrary("JObjC"); }

    public static enum Arch{ ppc, i386, x86_64 };
    public static enum Width{ W32, W64 };

    public static final Arch ARCH = getArch();
    public static final Width WIDTH = getWidth();

    private static Arch getArch(){
        String arch = System.getProperty("os.arch");
        if("ppc".equals(arch)) return Arch.ppc;
        if("i386".equals(arch)) return Arch.i386;
        if("x86_64".equals(arch)) return Arch.x86_64;
        if("amd64".equals(arch)) return Arch.x86_64;
        if("universal".equals(arch)) return Arch.x86_64;
        throw new RuntimeException("Did not recognize os.arch system property: '" + arch + "'");
    }

    private static Width getWidth(){
        String width = System.getProperty("sun.arch.data.model");
        if("32".equals(width)) return Width.W32;
        if("64".equals(width)) return Width.W64;
        throw new RuntimeException("Did not recognize sun.arch.data.model system property: '" + width + "'");
    }

    public static final boolean IS32 = System.getProperty("sun.arch.data.model").equals("32");
    public static final boolean IS64 = System.getProperty("sun.arch.data.model").equals("64");
    public static final int PTR_LEN = IS64 ? 8 : 4;
    public static final boolean IS_BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("JObjC.debug"));

    static void checkPermission(){
        final SecurityManager security = System.getSecurityManager();
        if (security != null) security.checkPermission(new RuntimePermission("canProcessApplicationEvents"));
    }

    public final void assertOK(){
        if(this != instance)
            throw new SecurityException("runtime");
    }

    private JObjCRuntime(){}

    private static JObjCRuntime instance;
    static JObjCRuntime inst() {
        if (instance == null) instance = new JObjCRuntime();
        return instance;
    }

    public static JObjCRuntime getInstance() {
        checkPermission();
        return inst();
    }

    public final NativeArgumentBuffer getThreadLocalState() {
        return NativeArgumentBuffer.getThreadLocalBuffer(this);
    }

    final Unsafe unsafe = getUnsafe();
    final Subclassing subclassing = new Subclassing(this);
    final List<String> registeredPackages = new ArrayList<String>();

    @SuppressWarnings("unchecked")
    Class<? extends ID> getClassForNativeClassName(final String className) {
        for (final String pkg : registeredPackages) {
            try {
                final Class<?> clazz = Class.forName(pkg + "." + className);
                if (clazz != null) return (Class<? extends ID>)clazz;
            } catch (final ClassNotFoundException e) { }
        }

        return null;
    }

    private final static Unsafe getUnsafe() {
        Unsafe inst = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            inst = (Unsafe) f.get(null);
            if(inst == null) throw new NullPointerException("Unsafe.theUnsafe == null");
        } catch (Exception e) {
            throw new RuntimeException("Unable to get instance of Unsafe.", e);
        }
        return inst;
    }

    public void registerPackage(final String pkg) {
        registeredPackages.add(pkg);
    }

    /**
     * Register a subclass of NSObject to allow the native side to send
     * messages which in turn call java methods declared on the class.
     * If a native class by the same name already exists, registerClass
     * will simply return without doing anything.
     *
     * For a usage example, see the SubclassingTest.
     */
    public boolean registerUserClass(Class<? extends ID> clazz, Class<? extends NSClass> clazzClazz) {
        return subclassing.registerUserClass(clazz, clazzClazz);
    }

}
