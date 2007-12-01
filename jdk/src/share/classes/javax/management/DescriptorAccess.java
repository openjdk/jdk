/*
 * Portions Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * @author    IBM Corp.
 *
 * Copyright IBM Corp. 1999-2000.  All rights reserved.
 */

package javax.management;

/**
 * This interface is used to gain access to descriptors of the Descriptor class
 * which are associated with a JMX component, i.e. MBean, MBeanInfo,
 * MBeanAttributeInfo, MBeanNotificationInfo,
 * MBeanOperationInfo, MBeanParameterInfo.
 * <P>
 * ModelMBeans make extensive use of this interface in ModelMBeanInfo classes.
 *
 * @since 1.5
 */
public interface DescriptorAccess extends DescriptorRead
{
    /**
    * Sets Descriptor (full replace).
    *
    * @param inDescriptor replaces the Descriptor associated with the
    * component implementing this interface. If the inDescriptor is invalid for the
    * type of Info object it is being set for, an exception is thrown.  If the
    * inDescriptor is null, then the Descriptor will revert to its default value
    * which should contain, at a minimum, the descriptor name and descriptorType.
    *
    * @see #getDescriptor
    */
    public void setDescriptor(Descriptor inDescriptor);
}
