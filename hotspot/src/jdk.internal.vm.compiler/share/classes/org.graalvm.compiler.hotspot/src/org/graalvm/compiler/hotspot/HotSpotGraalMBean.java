/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.util.EconomicMap;

public final class HotSpotGraalMBean implements DynamicMBean {
    private static Object mBeanServerField;
    private final OptionValues options;
    private final EconomicMap<OptionKey<?>, Object> changes;
    private ObjectName registered;

    private HotSpotGraalMBean(OptionValues options) {
        this.options = options;
        this.changes = EconomicMap.create();
    }

    private static boolean isMXServerOn() {
        if (mBeanServerField == null) {
            try {
                final Field field = ManagementFactory.class.getDeclaredField("platformMBeanServer");
                field.setAccessible(true);
                mBeanServerField = field;
            } catch (Exception ex) {
                mBeanServerField = ManagementFactory.class;
            }
        }
        if (mBeanServerField instanceof Field) {
            try {
                return ((Field) mBeanServerField).get(null) != null;
            } catch (Exception ex) {
                return true;
            }
        } else {
            return false;
        }
    }

    public static HotSpotGraalMBean create() {
        OptionValues options = HotSpotGraalOptionValues.HOTSPOT_OPTIONS;
        HotSpotGraalMBean mbean = new HotSpotGraalMBean(options);
        return mbean;
    }

    public ObjectName ensureRegistered(boolean check) {
        for (int cnt = 0;; cnt++) {
            if (registered != null) {
                return registered;
            }
            if (check && !isMXServerOn()) {
                return null;
            }
            try {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                ObjectName name = new ObjectName("org.graalvm.compiler.hotspot:type=Options" + (cnt == 0 ? "" : cnt));
                mbs.registerMBean(this, name);
                registered = name;
                break;
            } catch (MalformedObjectNameException | MBeanRegistrationException | NotCompliantMBeanException ex) {
                throw new IllegalStateException(ex);
            } catch (InstanceAlreadyExistsException ex) {
                continue;
            }
        }
        return registered;
    }

    @SuppressWarnings("unused")
    OptionValues optionsFor(OptionValues values, ResolvedJavaMethod forMethod) {
        ensureRegistered(true);
        if (changes.isEmpty()) {
            return values;
        }
        return new OptionValues(values, changes);
    }

    @Override
    public Object getAttribute(String attribute) {
        for (OptionKey<?> k : options.getMap().getKeys()) {
            if (k.getName().equals(attribute)) {
                return options.getMap().get(k);
            }
        }
        return null;
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        throw new InvalidAttributeValueException();
    }

    @Override
    public AttributeList getAttributes(String[] names) {
        AttributeList list = new AttributeList();
        for (String name : names) {
            Object value = getAttribute(name);
            if (value != null) {
                list.add(new Attribute(name, value));
            }
        }
        return list;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        throw new IllegalStateException();
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        return null;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        List<MBeanAttributeInfo> attrs = new ArrayList<>();
        if (registered != null) {
            for (Iterator<OptionDescriptors> it = OptionsParser.getOptionsLoader().iterator(); it.hasNext();) {
                for (OptionDescriptor descr : it.next()) {
                    attrs.add(new MBeanAttributeInfo(descr.getName(), descr.getType().getName(), descr.getHelp(), true, false, false));
                }
            }
        }
        return new MBeanInfo(
                        HotSpotGraalMBean.class.getName(),
                        "Graal",
                        attrs.toArray(new MBeanAttributeInfo[attrs.size()]),
                        null, null, null);
    }

}
