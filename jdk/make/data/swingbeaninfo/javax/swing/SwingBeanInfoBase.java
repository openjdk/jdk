/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing;

import java.beans.*;
import java.lang.reflect.*;
import java.awt.Image;

/**
 * The superclass for all Swing BeanInfo classes.  It provides
 * default implementations of <code>getIcon</code> and
 * <code>getDefaultPropertyIndex</code> as well as utility
 * methods, like createPropertyDescriptor, for writing BeanInfo
 * implementations.  This classes is intended to be used along
 * with <code>GenSwingBeanInfo</code> a BeanInfo class code generator.
 *
 * @see GenSwingBeanInfo
 * @author Hans Muller
 */
public class SwingBeanInfoBase extends SimpleBeanInfo
{
    /**
     * The default index is always 0.  In other words the first property
     * listed in the getPropertyDescriptors() method is the one
     * to show a (JFC builder) user in a situation where just a single
     * property will be shown.
     */
    public int getDefaultPropertyIndex() {
        return 0;
    }

    /**
     * Returns a generic Swing icon, all icon "kinds" are supported.
     * Subclasses should defer to this method when they don't have
     * a particular beans icon kind.
     */
    public Image getIcon(int kind) {
        // PENDING(hmuller) need generic swing icon images.
        return null;
    }

    /**
     * Returns the BeanInfo for the superclass of our bean, so that
     * its PropertyDescriptors will be included.
     */
    public BeanInfo[] getAdditionalBeanInfo() {
        Class superClass = getBeanDescriptor().getBeanClass().getSuperclass();
        BeanInfo superBeanInfo = null;
        try {
            superBeanInfo = Introspector.getBeanInfo(superClass);
        } catch (IntrospectionException ie) {}
        if (superBeanInfo != null) {
            BeanInfo[] ret = new BeanInfo[1];
            ret[0] = superBeanInfo;
            return ret;
        }
        return null;
    }
}
