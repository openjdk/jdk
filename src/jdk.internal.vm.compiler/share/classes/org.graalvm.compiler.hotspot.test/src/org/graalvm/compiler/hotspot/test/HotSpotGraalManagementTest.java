/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.test.HotSpotGraalCompilerTest.assumeGraalIsNotJIT;
import static org.graalvm.compiler.hotspot.test.HotSpotGraalManagementTest.JunitShield.findAttributeInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.NestedBooleanOptionKey;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionsParser;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class HotSpotGraalManagementTest {

    private static final boolean DEBUG = Boolean.getBoolean(HotSpotGraalManagementTest.class.getSimpleName() + ".debug");

    public HotSpotGraalManagementTest() {
        assumeGraalIsNotJIT("random flipping of Graal options can cause havoc if Graal is being used as a JIT");
        try {
            /* Trigger loading of the management library using the bootstrap class loader. */
            ManagementFactory.getThreadMXBean();
            MBeanServerFactory.findMBeanServer(null);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new AssumptionViolatedException("Management classes/module(s) not available: " + e);
        }
    }

    @Test
    public void registration() throws Exception {
        HotSpotGraalRuntime runtime = (HotSpotGraalRuntime) Graal.getRuntime();
        HotSpotGraalManagementRegistration management = runtime.getManagement();
        if (management == null) {
            return;
        }

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectName name;
        assertNotNull("Now the bean thinks it is registered", name = (ObjectName) management.poll(true));

        assertNotNull("And the bean is found", server.getObjectInstance(name));
    }

    @Test
    public void readBeanInfo() throws Exception {

        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalRuntime runtime = (HotSpotGraalRuntime) Graal.getRuntime();
        HotSpotGraalManagementRegistration management = runtime.getManagement();
        if (management == null) {
            return;
        }

        ObjectName mbeanName;
        assertNotNull("Bean is registered", mbeanName = (ObjectName) management.poll(true));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        ObjectInstance bean = server.getObjectInstance(mbeanName);
        assertNotNull("Bean is registered", bean);
        MBeanInfo info = server.getMBeanInfo(mbeanName);
        assertNotNull("Info is found", info);

        AttributeList originalValues = new AttributeList();
        AttributeList newValues = new AttributeList();
        for (OptionDescriptors set : OptionsParser.getOptionsLoader()) {
            for (OptionDescriptor option : set) {
                JunitShield.testOption(info, mbeanName, server, runtime, option, newValues, originalValues);
            }
        }

        String[] attributeNames = new String[originalValues.size()];
        for (int i = 0; i < attributeNames.length; i++) {
            attributeNames[i] = ((Attribute) originalValues.get(i)).getName();
        }
        AttributeList actualValues = server.getAttributes(mbeanName, attributeNames);
        assertEquals(originalValues.size(), actualValues.size());
        for (int i = 0; i < attributeNames.length; i++) {
            Object expect = String.valueOf(((Attribute) originalValues.get(i)).getValue());
            Object actual = String.valueOf(((Attribute) actualValues.get(i)).getValue());
            assertEquals(attributeNames[i], expect, actual);
        }

        try {
            server.setAttributes(mbeanName, newValues);
        } finally {
            server.setAttributes(mbeanName, originalValues);
        }
    }

    /**
     * Junit scans all methods of a test class and tries to resolve all method parameter and return
     * types. We hide such methods in an inner class to prevent errors such as:
     *
     * <pre>
     * java.lang.NoClassDefFoundError: javax/management/MBeanInfo
     *     at java.base/java.lang.Class.getDeclaredMethods0(Native Method)
     *     at java.base/java.lang.Class.privateGetDeclaredMethods(Class.java:3119)
     *     at java.base/java.lang.Class.getDeclaredMethods(Class.java:2268)
     *     at org.junit.internal.MethodSorter.getDeclaredMethods(MethodSorter.java:54)
     *     at org.junit.runners.model.TestClass.scanAnnotatedMembers(TestClass.java:65)
     *     at org.junit.runners.model.TestClass.<init>(TestClass.java:57)
     *
     * </pre>
     */
    static class JunitShield {

        /**
         * Tests changing the value of {@code option} via the management interface to a) a new legal
         * value and b) an illegal value.
         */
        static void testOption(MBeanInfo mbeanInfo,
                        ObjectName mbeanName,
                        MBeanServer server,
                        HotSpotGraalRuntime runtime,
                        OptionDescriptor option,
                        AttributeList newValues,
                        AttributeList originalValues) throws Exception {
            OptionKey<?> optionKey = option.getOptionKey();
            Object currentValue = optionKey.getValue(runtime.getOptions());
            Class<?> optionType = option.getOptionValueType();
            String name = option.getName();
            if (DEBUG) {
                System.out.println("Testing option " + name);
            }
            MBeanAttributeInfo attrInfo = findAttributeInfo(name, mbeanInfo);
            assertNotNull("Attribute not found for option " + name, attrInfo);

            String expectAttributeValue = stringValue(currentValue, option.getOptionValueType() == String.class);
            Object actualAttributeValue = server.getAttribute(mbeanName, name);
            assertEquals(expectAttributeValue, actualAttributeValue);

            Map<String, String> legalValues = new HashMap<>();
            List<String> illegalValues = new ArrayList<>();
            if (optionKey instanceof EnumOptionKey) {
                EnumOptionKey<?> enumOptionKey = (EnumOptionKey<?>) optionKey;
                for (Object obj : enumOptionKey.getAllValues()) {
                    if (obj != currentValue) {
                        legalValues.put(obj.toString(), obj.toString());
                    }
                }
                illegalValues.add(String.valueOf(42));
            } else if (optionType == Boolean.class) {
                Object defaultValue;
                if (optionKey instanceof NestedBooleanOptionKey) {
                    NestedBooleanOptionKey nbok = (NestedBooleanOptionKey) optionKey;
                    defaultValue = nbok.getMasterOption().getValue(runtime.getOptions());
                } else {
                    defaultValue = optionKey.getDefaultValue();
                }
                legalValues.put("", unquotedStringValue(defaultValue));
                illegalValues.add(String.valueOf(42));
                illegalValues.add("true");
                illegalValues.add("false");
            } else if (optionType == String.class) {
                legalValues.put("", quotedStringValue(optionKey.getDefaultValue()));
                legalValues.put("\"" + currentValue + "Prime\"", "\"" + currentValue + "Prime\"");
                legalValues.put("\"quoted string\"", "\"quoted string\"");
                illegalValues.add("\"unbalanced quotes");
                illegalValues.add("\"");
                illegalValues.add("non quoted string");
            } else if (optionType == Float.class) {
                legalValues.put("", unquotedStringValue(optionKey.getDefaultValue()));
                String value = unquotedStringValue(currentValue == null ? 33F : ((float) currentValue) + 11F);
                legalValues.put(value, value);
                illegalValues.add("string");
            } else if (optionType == Double.class) {
                legalValues.put("", unquotedStringValue(optionKey.getDefaultValue()));
                String value = unquotedStringValue(currentValue == null ? 33D : ((double) currentValue) + 11D);
                legalValues.put(value, value);
                illegalValues.add("string");
            } else if (optionType == Integer.class) {
                legalValues.put("", unquotedStringValue(optionKey.getDefaultValue()));
                String value = unquotedStringValue(currentValue == null ? 33 : ((int) currentValue) + 11);
                legalValues.put(value, value);
                illegalValues.add("42.42");
                illegalValues.add("string");
            } else if (optionType == Long.class) {
                legalValues.put("", unquotedStringValue(optionKey.getDefaultValue()));
                String value = unquotedStringValue(currentValue == null ? 33L : ((long) currentValue) + 11L);
                legalValues.put(value, value);
                illegalValues.add("42.42");
                illegalValues.add("string");
            }

            Attribute originalAttributeValue = new Attribute(name, expectAttributeValue);
            try {
                for (Map.Entry<String, String> e : legalValues.entrySet()) {
                    String legalValue = e.getKey();
                    if (DEBUG) {
                        System.out.printf("Changing %s from %s to %s%n", name, currentValue, legalValue);
                    }
                    Attribute newAttributeValue = new Attribute(name, legalValue);
                    newValues.add(newAttributeValue);
                    server.setAttribute(mbeanName, newAttributeValue);
                    Object actual = optionKey.getValue(runtime.getOptions());
                    actual = server.getAttribute(mbeanName, name);
                    String expectValue = e.getValue();
                    if (option.getOptionValueType() == String.class && expectValue == null) {
                        expectValue = "";
                    } else if (option.getOptionKey() instanceof NestedBooleanOptionKey && null == expectValue) {
                        NestedBooleanOptionKey nbok = (NestedBooleanOptionKey) option.getOptionKey();
                        expectValue = String.valueOf(nbok.getValue(runtime.getOptions()));
                        actual = server.getAttribute(mbeanName, name);
                    }
                    assertEquals(expectValue, actual);
                }
            } finally {
                if (DEBUG) {
                    System.out.printf("Resetting %s to %s%n", name, currentValue);
                }
                originalValues.add(originalAttributeValue);
                server.setAttribute(mbeanName, originalAttributeValue);
            }

            try {
                for (Object illegalValue : illegalValues) {
                    if (DEBUG) {
                        System.out.printf("Changing %s from %s to illegal value %s%n", name, currentValue, illegalValue);
                    }
                    server.setAttribute(mbeanName, new Attribute(name, illegalValue));
                    Assert.fail("Expected setting " + name + " to " + illegalValue + " to fail");
                }
            } catch (InvalidAttributeValueException e) {
                // Expected
            } finally {
                if (DEBUG) {
                    System.out.printf("Resetting %s to %s%n", name, currentValue);
                }
                server.setAttribute(mbeanName, originalAttributeValue);
            }

            try {

                String unknownOptionName = "definitely not an option name";
                server.setAttribute(mbeanName, new Attribute(unknownOptionName, ""));
                Assert.fail("Expected setting option with name \"" + unknownOptionName + "\" to fail");
            } catch (AttributeNotFoundException e) {
                // Expected
            }
        }

        static MBeanAttributeInfo findAttributeInfo(String attrName, MBeanInfo info) {
            for (MBeanAttributeInfo attr : info.getAttributes()) {
                if (attr.getName().equals(attrName)) {
                    assertTrue("Readable", attr.isReadable());
                    assertTrue("Writable", attr.isWritable());
                    return attr;
                }
            }
            return null;
        }
    }

    private static String quotedStringValue(Object optionValue) {
        return stringValue(optionValue, true);
    }

    private static String unquotedStringValue(Object optionValue) {
        return stringValue(optionValue, false);
    }

    private static String stringValue(Object optionValue, boolean withQuoting) {
        if (optionValue == null) {
            return "";
        }
        if (withQuoting) {
            return "\"" + optionValue + "\"";
        }
        return String.valueOf(optionValue);
    }

    private static String quoted(Object s) {
        return "\"" + s + "\"";
    }

    /**
     * Tests publicaly visible names and identifiers used by tools developed and distributed on an
     * independent schedule (like VisualVM). Consider keeping the test passing without any semantic
     * modifications. The cost of changes is higher than you estimate. Include all available
     * stakeholders as reviewers to give them a chance to stop you before causing too much damage.
     */
    @Test
    public void publicJmxApiOfGraalDumpOperation() throws Exception {
        assertNotNull("Server is started", ManagementFactory.getPlatformMBeanServer());

        HotSpotGraalRuntime runtime = (HotSpotGraalRuntime) Graal.getRuntime();
        HotSpotGraalManagementRegistration management = runtime.getManagement();
        if (management == null) {
            return;
        }

        ObjectName mbeanName;
        assertNotNull("Bean is registered", mbeanName = (ObjectName) management.poll(true));
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        assertEquals("Domain name is used to lookup the beans by VisualVM", "org.graalvm.compiler.hotspot", mbeanName.getDomain());
        assertEquals("type can be used to identify the Graal bean", "HotSpotGraalRuntime_VM", mbeanName.getKeyProperty("type"));

        ObjectInstance bean = server.getObjectInstance(mbeanName);
        assertNotNull("Bean is registered", bean);

        MBeanInfo info = server.getMBeanInfo(mbeanName);
        assertNotNull("Info is found", info);

        final MBeanOperationInfo[] arr = info.getOperations();
        MBeanOperationInfo dumpOp = null;
        int dumpMethodCount = 0;
        for (int i = 0; i < arr.length; i++) {
            if ("dumpMethod".equals(arr[i].getName())) {
                if (arr[i].getSignature().length == 3) {
                    dumpOp = arr[i];
                }
                dumpMethodCount++;
            }
        }
        assertEquals("Currently three overloads", 3, dumpMethodCount);
        assertNotNull("three args variant (as used by VisualVM) found", dumpOp);

        MBeanAttributeInfo dumpPath = findAttributeInfo("DumpPath", info);
        MBeanAttributeInfo printGraphFile = findAttributeInfo("PrintGraphFile", info);
        MBeanAttributeInfo showDumpFiles = findAttributeInfo("ShowDumpFiles", info);
        Object originalDumpPath = server.getAttribute(mbeanName, dumpPath.getName());
        Object originalPrintGraphFile = server.getAttribute(mbeanName, printGraphFile.getName());
        Object originalShowDumpFiles = server.getAttribute(mbeanName, showDumpFiles.getName());
        final File tmpDir = new File(HotSpotGraalManagementTest.class.getSimpleName() + "_" + System.currentTimeMillis()).getAbsoluteFile();

        server.setAttribute(mbeanName, new Attribute(dumpPath.getName(), quoted(tmpDir)));
        // Force output to a file even if there's a running IGV instance available.
        server.setAttribute(mbeanName, new Attribute(printGraphFile.getName(), true));
        server.setAttribute(mbeanName, new Attribute(showDumpFiles.getName(), false));
        Object[] params = {"java.util.Arrays", "asList", ":3"};
        try {
            server.invoke(mbeanName, "dumpMethod", params, null);
            boolean found = false;
            String expectedIgvDumpSuffix = "[Arrays.asList(Object[])List].bgv";
            List<String> dumpPathEntries = Arrays.asList(tmpDir.list());
            for (String entry : dumpPathEntries) {
                if (entry.endsWith(expectedIgvDumpSuffix)) {
                    found = true;
                }
            }
            if (!found) {
                Assert.fail(String.format("Expected file ending with \"%s\" in %s but only found:%n%s", expectedIgvDumpSuffix, tmpDir,
                                dumpPathEntries.stream().collect(Collectors.joining(System.lineSeparator()))));
            }
        } finally {
            deleteDirectory(tmpDir.toPath());
            server.setAttribute(mbeanName, new Attribute(dumpPath.getName(), originalDumpPath));
            server.setAttribute(mbeanName, new Attribute(printGraphFile.getName(), originalPrintGraphFile));
            server.setAttribute(mbeanName, new Attribute(showDumpFiles.getName(), originalShowDumpFiles));
        }
    }

    static void deleteDirectory(Path toDelete) throws IOException {
        Files.walk(toDelete).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
}
