/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

import java.lang.classfile.ClassFile;
import jdk.test.lib.util.ForceGC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.Comparator;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleProxies.*;
import static java.lang.invoke.MethodType.methodType;
import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 6983726
 * @library /test/lib
 * @enablePreview
 * @summary Tests on implementation hidden classes spinned by MethodHandleProxies
 * @build WrapperHiddenClassTest Client jdk.test.lib.util.ForceGC
 * @run junit WrapperHiddenClassTest
 */
public class WrapperHiddenClassTest {

    /**
     * Tests an adversary "implementation" class will not be
     * "recovered" by the wrapperInstance* APIs
     */
    @Test
    public void testWrapperInstance() throws Throwable {
        Comparator<Integer> hostile = createHostileInstance();
        var mh = MethodHandles.publicLookup()
                .findVirtual(Integer.class, "compareTo", methodType(int.class, Integer.class));
        @SuppressWarnings("unchecked")
        Comparator<Integer> proxy = (Comparator<Integer>) asInterfaceInstance(Comparator.class, mh);

        assertTrue(isWrapperInstance(proxy));
        assertFalse(isWrapperInstance(hostile));
        assertSame(mh, wrapperInstanceTarget(proxy));
        assertThrows(IllegalArgumentException.class, () -> wrapperInstanceTarget(hostile));
        assertSame(Comparator.class, wrapperInstanceType(proxy));
        assertThrows(IllegalArgumentException.class, () -> wrapperInstanceType(hostile));
    }

    private static final String TYPE = "interfaceType";
    private static final String TARGET = "target";
    private static final ClassDesc CD_HostileWrapper = ClassDesc.of("HostileWrapper");
    private static final ClassDesc CD_Comparator = ClassDesc.of("java.util.Comparator");
    private static final MethodTypeDesc MTD_int_Object_Object = MethodTypeDesc.of(CD_int, CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_int_Integer = MethodTypeDesc.of(CD_int, CD_Integer);

    // Update this template when the MHP template is updated
    @SuppressWarnings("unchecked")
    private Comparator<Integer> createHostileInstance() throws Throwable {
        var cf = ClassFile.of();
        var bytes = cf.build(CD_HostileWrapper, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(ACC_FINAL | ACC_SYNTHETIC);
            clb.withInterfaceSymbols(CD_Comparator);

            // static and instance fields
            clb.withField(TYPE, CD_Class, ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
            clb.withField(TARGET, CD_MethodHandle, ACC_PRIVATE | ACC_FINAL);

            // <clinit>
            clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, cob -> {
                cob.constantInstruction(CD_Comparator);
                cob.putstatic(CD_HostileWrapper, TYPE, CD_Class);
                cob.return_();
            });

            // <init>
            clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });

            // implementation
            clb.withMethodBody("compare", MTD_int_Object_Object, ACC_PUBLIC, cob -> {
                cob.aload(1);
                cob.checkcast(CD_Integer);
                cob.aload(2);
                cob.checkcast(CD_Integer);
                cob.invokestatic(CD_Integer, "compareTo", MTD_int_Integer);
                cob.ireturn();
            });
        });
        var l = MethodHandles.lookup().defineHiddenClass(bytes, true);
        return (Comparator<Integer>) l.findConstructor(l.lookupClass(), MethodType.methodType(void.class)).invoke();
    }

    /**
     * Ensures a user interface cannot access a Proxy implementing it.
     */
    @Test
    public void testNoAccess() {
        var instance = asInterfaceInstance(Client.class, MethodHandles.zero(void.class));
        var instanceClass = instance.getClass();
        var interfaceLookup = Client.lookup();
        assertEquals(MethodHandles.Lookup.ORIGINAL, interfaceLookup.lookupModes() & MethodHandles.Lookup.ORIGINAL,
                "Missing original flag on interface's lookup");
        assertThrows(IllegalAccessException.class, () -> MethodHandles.privateLookupIn(instanceClass,
                interfaceLookup));
    }

    /**
     * Tests the Proxy module properties for Proxies implementing system and
     * user interfaces.
     */
    @ParameterizedTest
    @ValueSource(classes = {Client.class, Runnable.class})
    public void testModule(Class<?> ifaceClass) {
        var mh = MethodHandles.zero(void.class);

        var inst = asInterfaceInstance(ifaceClass, mh);
        Module ifaceModule = ifaceClass.getModule();
        Class<?> implClass = inst.getClass();
        Module implModule = implClass.getModule();

        String implPackage = implClass.getPackageName();
        assertFalse(implModule.isExported(implPackage),
                "implementation should not be exported");
        assertTrue(ifaceModule.isExported(ifaceClass.getPackageName(), implModule),
                "interface package should be exported to implementation");
        assertTrue(implModule.isOpen(implPackage, MethodHandleProxies.class.getModule()),
                "implementation class is not reflectively open to MHP class");
        assertTrue(implModule.isNamed(), "dynamic module must be named");
        assertTrue(implModule.getName().startsWith("jdk.MHProxy"),
                () -> "incorrect dynamic module name: " + implModule.getName());

        assertSame(ifaceClass.getClassLoader(), implClass.getClassLoader(),
                "wrapper class should use the interface's loader ");
        assertSame(implClass.getClassLoader(), implModule.getClassLoader(),
                "module class loader should be wrapper class's loader");
    }

    /**
     * Tests the access control of Proxies implementing system and user
     * interfaces.
     */
    @ParameterizedTest
    @ValueSource(classes = {Client.class, Runnable.class})
    public void testNoInstantiation(Class<?> ifaceClass) throws ReflectiveOperationException {
        var mh = MethodHandles.zero(void.class);
        var instanceClass = asInterfaceInstance(ifaceClass, mh).getClass();
        var ctor = instanceClass.getDeclaredConstructor(MethodHandles.Lookup.class, MethodHandle.class, MethodHandle.class);

        assertThrows(IllegalAccessException.class, () -> ctor.newInstance(Client.lookup(), mh, mh));
        assertThrows(IllegalAccessException.class, () -> ctor.newInstance(MethodHandles.lookup(), mh, mh));
        assertThrows(IllegalAccessException.class, () -> ctor.newInstance(MethodHandles.publicLookup(), mh, mh));
    }

    /**
     * Tests the caching and weak reference of implementation classes for
     * system and user interfaces.
     */
    @ParameterizedTest
    @ValueSource(classes = {Runnable.class, Client.class})
    public void testWeakImplClass(Class<?> ifaceClass) {
        var mh = MethodHandles.zero(void.class);

        var wrapper1 = asInterfaceInstance(ifaceClass, mh);
        var implClass = wrapper1.getClass();

        System.gc(); // helps debug if incorrect items are weakly referenced
        var wrapper2 = asInterfaceInstance(ifaceClass, mh);
        assertSame(implClass, wrapper2.getClass(),
                "MHP should reuse old implementation class when available");

        var implClassRef = new WeakReference<>(implClass);
        // clear strong references
        implClass = null;
        wrapper1 = null;
        wrapper2 = null;

        if (!ForceGC.wait(() -> implClassRef.refersTo(null))) {
            fail("MHP impl class cannot be cleared by GC");
        }
    }
}
