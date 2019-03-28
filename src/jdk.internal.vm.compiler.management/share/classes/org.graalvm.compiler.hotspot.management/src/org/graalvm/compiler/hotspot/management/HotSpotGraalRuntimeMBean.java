/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.management;

import org.graalvm.compiler.phases.common.jmx.HotSpotMBeanOperationProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.services.Services;

/**
 * MBean used to access properties and operations of a {@link HotSpotGraalRuntime} instance.
 */
final class HotSpotGraalRuntimeMBean implements DynamicMBean {

    /**
     * The runtime instance to which this bean provides a management connection.
     */
    private final HotSpotGraalRuntime runtime;

    /**
     * The object name under which the bean is registered.
     */
    private final ObjectName objectName;

    HotSpotGraalRuntimeMBean(ObjectName objectName, HotSpotGraalRuntime runtime) {
        this.objectName = objectName;
        this.runtime = runtime;
    }

    ObjectName getObjectName() {
        return objectName;
    }

    HotSpotGraalRuntime getRuntime() {
        return runtime;
    }

    private static final boolean DEBUG = initDebug();

    private static boolean initDebug() {
        try {
            return Boolean.parseBoolean(Services.getSavedProperties().get(HotSpotGraalRuntimeMBean.class.getSimpleName() + ".debug"));
        } catch (SecurityException e) {
            // Swallow the exception
            return false;
        }
    }

    @Override
    public Object getAttribute(String name) throws AttributeNotFoundException {
        String[] result = runtime.getOptionValues(name);
        String value = result[0];
        if (value == null) {
            throw new AttributeNotFoundException(name);
        }
        if (DEBUG) {
            System.out.printf("getAttribute: %s = %s (type: %s)%n", name, value, value == null ? "null" : value.getClass().getName());
        }
        return result[0];
    }

    @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "reference equality on the receiver is what we want")
    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException {
        String name = attribute.getName();
        Object value = attribute.getValue();
        String svalue = String.valueOf(value);
        if (DEBUG) {
            System.out.printf("setAttribute: %s = %s (type: %s)%n", name, svalue, value == null ? "null" : value.getClass().getName());
        }
        String[] result = runtime.setOptionValues(new String[]{name}, new String[]{svalue});
        if (result[0] != name) {
            if (result[0] == null) {
                throw new AttributeNotFoundException(name);
            }
            throw new InvalidAttributeValueException(result[0]);
        }
    }

    @Override
    public AttributeList getAttributes(String[] names) {
        String[] values = runtime.getOptionValues(names);
        AttributeList list = new AttributeList();
        for (int i = 0; i < names.length; i++) {
            String value = values[i];
            String name = names[i];
            if (value == null) {
                TTY.printf("No such option named %s%n", name);
            } else {
                if (DEBUG) {
                    System.out.printf("getAttributes: %s = %s (type: %s)%n", name, value, value == null ? "null" : value.getClass().getName());
                }
                list.add(new Attribute(name, value));
            }
        }
        return list;
    }

    @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "reference equality on the receiver is what we want")
    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        String[] names = new String[attributes.size()];
        String[] values = new String[attributes.size()];

        int i = 0;
        for (Attribute attr : attributes.asList()) {
            String name = attr.getName();
            names[i] = name;
            Object value = attr.getValue();
            String svalue = String.valueOf(value);
            values[i] = svalue;
            if (DEBUG) {
                System.out.printf("setAttributes: %s = %s (type: %s)%n", name, svalue, value == null ? "null" : value.getClass().getName());
            }
            i++;
        }
        String[] result = runtime.setOptionValues(names, values);
        AttributeList setOk = new AttributeList();
        i = 0;
        for (Attribute attr : attributes.asList()) {
            if (names[i] == result[i]) {
                setOk.add(attr);
            } else if (result[i] == null) {
                TTY.printf("Error setting %s to %s: unknown option%n", attr.getName(), attr.getValue());
            } else {
                TTY.printf("Error setting %s to %s: %s%n", attr.getName(), attr.getValue(), result[i]);
            }
            i++;
        }
        return setOk;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        try {
            if (DEBUG) {
                System.out.printf("invoke: %s%s%n", actionName, Arrays.asList(params));
            }
            Object retvalue = null;
            if ("dumpMethod".equals(actionName)) {
                retvalue = runtime.invokeManagementAction(actionName, params);
            } else {
                boolean found = false;
                for (HotSpotMBeanOperationProvider p : GraalServices.load(HotSpotMBeanOperationProvider.class)) {
                    List<MBeanOperationInfo> info = new ArrayList<>();
                    p.registerOperations(MBeanOperationInfo.class, info);
                    for (MBeanOperationInfo op : info) {
                        if (actionName.equals(op.getName())) {
                            retvalue = p.invoke(actionName, params, signature);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    throw new MBeanException(new IllegalStateException("Cannot find operation " + actionName));
                }
            }
            if (DEBUG) {
                System.out.printf("invoke: %s%s = %s%n", actionName, Arrays.asList(params), retvalue);
            }
            return retvalue;
        } catch (MBeanException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ReflectionException(ex);
        }
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        List<MBeanAttributeInfo> attrs = new ArrayList<>();
        for (OptionDescriptor option : getOptionDescriptors().getValues()) {
            Class<?> optionValueType = option.getOptionValueType();
            if (Enum.class.isAssignableFrom(optionValueType)) {
                // Enum values are passed through
                // the management interface as Strings.
                optionValueType = String.class;
            }
            attrs.add(new MBeanAttributeInfo(option.getName(), optionValueType.getName(), option.getHelp(), true, true, false));
        }
        attrs.sort(new Comparator<MBeanAttributeInfo>() {
            @Override
            public int compare(MBeanAttributeInfo o1, MBeanAttributeInfo o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        List<MBeanOperationInfo> opts = new ArrayList<>();
        opts.add(new MBeanOperationInfo("dumpMethod", "Enable IGV dumps for provided method", new MBeanParameterInfo[]{
                        new MBeanParameterInfo("className", "java.lang.String", "Class to observe"),
                        new MBeanParameterInfo("methodName", "java.lang.String", "Method to observe"),
        }, "void", MBeanOperationInfo.ACTION));
        opts.add(new MBeanOperationInfo("dumpMethod", "Enable IGV dumps for provided method", new MBeanParameterInfo[]{
                        new MBeanParameterInfo("className", "java.lang.String", "Class to observe"),
                        new MBeanParameterInfo("methodName", "java.lang.String", "Method to observe"),
                        new MBeanParameterInfo("filter", "java.lang.String", "The parameter for Dump option"),
        }, "void", MBeanOperationInfo.ACTION));
        opts.add(new MBeanOperationInfo("dumpMethod", "Enable IGV dumps for provided method", new MBeanParameterInfo[]{
                        new MBeanParameterInfo("className", "java.lang.String", "Class to observe"),
                        new MBeanParameterInfo("methodName", "java.lang.String", "Method to observe"),
                        new MBeanParameterInfo("filter", "java.lang.String", "The parameter for Dump option"),
                        new MBeanParameterInfo("host", "java.lang.String", "The host where the IGV tool is running at"),
                        new MBeanParameterInfo("port", "int", "The port where the IGV tool is listening at"),
        }, "void", MBeanOperationInfo.ACTION));

        for (HotSpotMBeanOperationProvider p : GraalServices.load(HotSpotMBeanOperationProvider.class)) {
            p.registerOperations(MBeanOperationInfo.class, opts);
        }

        return new MBeanInfo(
                        HotSpotGraalRuntimeMBean.class.getName(),
                        "Graal",
                        attrs.toArray(new MBeanAttributeInfo[attrs.size()]),
                        null,
                        opts.toArray(new MBeanOperationInfo[opts.size()]),
                        null);
    }

    private static EconomicMap<String, OptionDescriptor> getOptionDescriptors() {
        EconomicMap<String, OptionDescriptor> result = EconomicMap.create();
        for (OptionDescriptors set : OptionsParser.getOptionsLoader()) {
            for (OptionDescriptor option : set) {
                result.put(option.getName(), option);
            }
        }
        return result;
    }
}
