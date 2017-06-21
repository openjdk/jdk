/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalMBean;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HotSpotGraalMBeanTest {
    @Test
    public void registration() throws Exception {
        ObjectName name;

        Field field = null;
        try {
            field = stopMBeanServer();
        } catch (Exception ex) {
            if (ex.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                // skip on JDK9
                return;
            }
        }
        assertNull("The platformMBeanServer isn't initialized now", field.get(null));

        HotSpotGraalMBean bean = HotSpotGraalMBean.create(null);
        assertNotNull("Bean created", bean);

        assertNull("It is not registered yet", bean.ensureRegistered(true));

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        assertNotNull("Now the bean thinks it is registered", name = bean.ensureRegistered(true));

        assertNotNull("And the bean is found", server.getObjectInstance(name));
    }

    private static Field stopMBeanServer() throws NoSuchFieldException, SecurityException, IllegalAccessException, IllegalArgumentException {
        final Field field = ManagementFactory.class.getDeclaredField("platformMBeanServer");
        field.setAccessible(true);
        field.set(null, null);
        return field;
    }

    @Test
    public void readBeanInfo() throws Exception {
        ObjectName name;

        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalMBean realBean = HotSpotGraalMBean.create(null);
        assertNotNull("Bean is registered", name = realBean.ensureRegistered(false));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectInstance bean = server.getObjectInstance(name);
        assertNotNull("Bean is registered", bean);
        MBeanInfo info = server.getMBeanInfo(name);
        assertNotNull("Info is found", info);

        MBeanAttributeInfo printCompilation = findAttributeInfo("PrintCompilation", info);
        assertNotNull("PrintCompilation found", printCompilation);
        assertEquals("true/false", Boolean.class.getName(), printCompilation.getType());

        Attribute printOn = new Attribute(printCompilation.getName(), Boolean.TRUE);

        Object before = server.getAttribute(name, printCompilation.getName());
        server.setAttribute(name, printOn);
        Object after = server.getAttribute(name, printCompilation.getName());

        assertNull("Default value was not set", before);
        assertEquals("Changed to on", Boolean.TRUE, after);
    }

    private static MBeanAttributeInfo findAttributeInfo(String attrName, MBeanInfo info) {
        MBeanAttributeInfo printCompilation = null;
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            if (attr.getName().equals(attrName)) {
                assertTrue("Readable", attr.isReadable());
                assertTrue("Writable", attr.isWritable());
                printCompilation = attr;
                break;
            }
        }
        return printCompilation;
    }

    @Test
    public void optionsAreCached() throws Exception {
        ObjectName name;

        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalMBean realBean = HotSpotGraalMBean.create(null);

        OptionValues original = new OptionValues(EconomicMap.create());

        assertSame(original, realBean.optionsFor(original, null));

        assertNotNull("Bean is registered", name = realBean.ensureRegistered(false));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectInstance bean = server.getObjectInstance(name);
        assertNotNull("Bean is registered", bean);
        MBeanInfo info = server.getMBeanInfo(name);
        assertNotNull("Info is found", info);

        MBeanAttributeInfo dump = findAttributeInfo("Dump", info);

        Attribute dumpTo1 = new Attribute(dump.getName(), 1);

        server.setAttribute(name, dumpTo1);
        Object after = server.getAttribute(name, dump.getName());
        assertEquals(1, after);

        final OptionValues modified1 = realBean.optionsFor(original, null);
        assertNotSame(original, modified1);
        final OptionValues modified2 = realBean.optionsFor(original, null);
        assertSame("Options are cached", modified1, modified2);

    }

    @Test
    public void dumpOperation() throws Exception {
        Field field = null;
        try {
            field = stopMBeanServer();
        } catch (Exception ex) {
            if (ex.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                // skip on JDK9
                return;
            }
        }
        assertNull("The platformMBeanServer isn't initialized now", field.get(null));

        ObjectName name;

        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalMBean realBean = HotSpotGraalMBean.create(null);

        assertNotNull("Bean is registered", name = realBean.ensureRegistered(false));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectInstance bean = server.getObjectInstance(name);
        assertNotNull("Bean is registered", bean);

        MBeanInfo info = server.getMBeanInfo(name);
        assertNotNull("Info is found", info);

        final MBeanOperationInfo[] arr = info.getOperations();
        assertEquals("Currently three overloads", 3, arr.length);
        MBeanOperationInfo dumpOp = null;
        for (int i = 0; i < arr.length; i++) {
            assertEquals("dumpMethod", arr[i].getName());
            if (arr[i].getSignature().length == 3) {
                dumpOp = arr[i];
            }
        }
        assertNotNull("three args variant found", dumpOp);

        server.invoke(name, "dumpMethod", new Object[]{
                        "java.util.Arrays", "asList", ":3"
        }, null);

        MBeanAttributeInfo dump = findAttributeInfo("Dump", info);
        Attribute dumpTo1 = new Attribute(dump.getName(), "");
        server.setAttribute(name, dumpTo1);
        Object after = server.getAttribute(name, dump.getName());
        assertEquals("", after);

        OptionValues empty = new OptionValues(EconomicMap.create());
        OptionValues unsetDump = realBean.optionsFor(empty, null);

        final OptionValues forMethod = realBean.optionsFor(unsetDump, new MockResolvedJavaMethod());
        assertNotSame(unsetDump, forMethod);
        Object nothing = unsetDump.getMap().get(GraalDebugConfig.Options.Dump);
        assertEquals("Empty string", "", nothing);

        Object specialValue = forMethod.getMap().get(GraalDebugConfig.Options.Dump);
        assertEquals(":3", specialValue);

        OptionValues normalMethod = realBean.optionsFor(unsetDump, null);
        Object noSpecialValue = normalMethod.getMap().get(GraalDebugConfig.Options.Dump);
        assertEquals("Empty string", "", noSpecialValue);
    }

    private static class MockResolvedJavaMethod implements HotSpotResolvedJavaMethod {
        MockResolvedJavaMethod() {
        }

        @Override
        public boolean isCallerSensitive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HotSpotResolvedObjectType getDeclaringClass() {
            return new MockResolvedObjectType();
        }

        @Override
        public boolean isForceInline() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasReservedStackAccess() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setNotInlineable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean ignoredBySecurityStackWalk() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResolvedJavaMethod uniqueConcreteMethod(HotSpotResolvedObjectType receiver) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasCompiledCode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasCompiledCodeAtLevel(int level) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int vtableEntryOffset(ResolvedJavaType resolved) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int intrinsicId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int allocateCompileId(int entryBCI) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasCodeAtLevel(int entryBCI, int level) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getCode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getCodeSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMaxLocals() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMaxStackSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSynthetic() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isVarArgs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isBridge() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isClassInitializer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isConstructor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canBeStaticallyBound() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExceptionHandler[] getExceptionHandlers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public StackTraceElement asStackTraceElement(int bci) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reprofile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConstantPool getConstantPool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Annotation[][] getParameterAnnotations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Type[] getGenericParameterTypes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canBeInlined() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasNeverInlineDirective() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean shouldBeInlined() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LineNumberTable getLineNumberTable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalVariableTable getLocalVariableTable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Constant getEncoding() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SpeculationLog getSpeculationLog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return "asList";
        }

        @Override
        public Signature getSignature() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getModifiers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Annotation[] getAnnotations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntrinsicCandidate() {
            return true;
        }

        private static class MockResolvedObjectType implements HotSpotResolvedObjectType {
            MockResolvedObjectType() {
            }

            @Override
            public long getFingerprint() {
                return 0L;
            }

            @Override
            public HotSpotResolvedObjectType getArrayClass() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaType getComponentType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HotSpotResolvedObjectType getSuperclass() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HotSpotResolvedObjectType[] getInterfaces() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HotSpotResolvedObjectType getSupertype() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HotSpotResolvedObjectType findLeastCommonAncestor(ResolvedJavaType otherType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ConstantPool getConstantPool() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int instanceSize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getVtableLength() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Constant klass() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isPrimaryType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int superCheckOffset() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long prototypeMarkWord() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int layoutHelper() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HotSpotResolvedObjectType getEnclosingType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaMethod getClassInitializer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasFinalizer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isInterface() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isInstanceClass() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isInitialized() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void initialize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isLinked() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isAssignableFrom(ResolvedJavaType other) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isInstance(JavaConstant obj) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaType getSingleImplementor() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaField[] getStaticFields() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getSourceFileName() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isLocal() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isMember() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaMethod[] getDeclaredConstructors() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedJavaMethod[] getDeclaredMethods() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isCloneableWithAllocation() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() {
                return "Ljava/util/Arrays;";
            }

            @Override
            public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getModifiers() {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Annotation[] getAnnotations() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Annotation[] getDeclaredAnnotations() {
                throw new UnsupportedOperationException();
            }
        }
    }
}
