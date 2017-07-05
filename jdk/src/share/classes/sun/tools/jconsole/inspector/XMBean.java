/*
 * Copyright 2004-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.tools.jconsole.inspector;

import java.io.IOException;
import javax.management.*;
import javax.swing.Icon;
import sun.tools.jconsole.MBeansTab;

public class XMBean extends Object {
    private ObjectName objectName;
    private Icon icon;
    private String text;
    private boolean broadcaster;
    private MBeanInfo mbeanInfo;
    private MBeansTab mbeansTab;

    public XMBean(ObjectName objectName, MBeansTab mbeansTab)
        throws InstanceNotFoundException, IntrospectionException,
            ReflectionException, IOException {
        this.mbeansTab = mbeansTab;
        setObjectName(objectName);
        if (MBeanServerDelegate.DELEGATE_NAME.equals(objectName)) {
            icon = IconManager.MBEANSERVERDELEGATE;
        } else {
            icon = IconManager.MBEAN;
        }
        this.broadcaster = isBroadcaster(objectName);
        this.mbeanInfo = getMBeanInfo(objectName);
    }

    MBeanServerConnection getMBeanServerConnection() {
        return mbeansTab.getMBeanServerConnection();
    }

    public boolean isBroadcaster() {
        return broadcaster;
    }

    private boolean isBroadcaster(ObjectName name) {
        try {
            return getMBeanServerConnection().isInstanceOf(
                    name, "javax.management.NotificationBroadcaster");
        } catch (Exception e) {
            System.out.println("Error calling isBroadcaster: " +
                    e.getMessage());
        }
        return false;
    }

    public Object invoke(String operationName) throws Exception {
        Object result = getMBeanServerConnection().invoke(
                getObjectName(), operationName, new Object[0], new String[0]);
        return result;
    }

    public Object invoke(String operationName, Object params[], String sig[])
        throws Exception {
        Object result = getMBeanServerConnection().invoke(
                getObjectName(), operationName, params, sig);
        return result;
    }

    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException, InstanceNotFoundException,
            InvalidAttributeValueException, MBeanException,
            ReflectionException, IOException {
        getMBeanServerConnection().setAttribute(getObjectName(), attribute);
    }

    public Object getAttribute(String attributeName)
        throws AttributeNotFoundException, InstanceNotFoundException,
            MBeanException, ReflectionException, IOException {
        return getMBeanServerConnection().getAttribute(
                getObjectName(), attributeName);
    }

    public AttributeList getAttributes(String attributeNames[])
        throws AttributeNotFoundException, InstanceNotFoundException,
            MBeanException, ReflectionException, IOException {
        return getMBeanServerConnection().getAttributes(
                getObjectName(), attributeNames);
    }

    public AttributeList getAttributes(MBeanAttributeInfo attributeNames[])
        throws AttributeNotFoundException, InstanceNotFoundException,
            MBeanException, ReflectionException, IOException {
        String attributeString[] = new String[attributeNames.length];
        for (int i = 0; i < attributeNames.length; i++) {
            attributeString[i] = attributeNames[i].getName();
        }
        return getAttributes(attributeString);
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    private void setObjectName(ObjectName objectName) {
        this.objectName = objectName;
        // generate a readable name now
        String name = getObjectName().getKeyProperty("name");
        if (name == null)
            setText(getObjectName().getDomain());
        else
            setText(name);
    }

    public MBeanInfo getMBeanInfo() {
        return mbeanInfo;
    }

    private MBeanInfo getMBeanInfo(ObjectName name)
        throws InstanceNotFoundException, IntrospectionException,
            ReflectionException, IOException {
        return getMBeanServerConnection().getMBeanInfo(name);
    }

    public boolean equals(Object o) {
        if (o instanceof XMBean) {
            XMBean mbean = (XMBean) o;
            return getObjectName().equals((mbean).getObjectName());
        }
        return false;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    public String toString() {
        return getText();
    }
}
