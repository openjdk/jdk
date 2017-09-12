/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.glassfish.external.amx;

/**
    Constants reflecting the AMX specification.
    See https://glassfish.dev.java.net/nonav/v3/admin/planning/V3Changes/V3_AMX_SPI.html
 */
public final class AMX
{
    private AMX()
    {
        // do not instantiate
    }

    /** Attribute yielding the ObjectName of the parent MBean */
    public static final String ATTR_PARENT = "Parent";

    /** Attribute yielding the children as an ObjectName[] */
    public static final String ATTR_CHILDREN = "Children";

    /** Attribute yielding the name of the MBean,
        possibly differing from the name as found in the ObjectName via the
        property {@link #NAME_KEY} */
    public static final String ATTR_NAME = "Name";

    /** ObjectName property for the type */
    public static final String TYPE_KEY = "type";

    /** ObjectName property for the name */
    public static final String NAME_KEY = "name";

    /** Implied name for singletons when the name property is not present */
    public static final String NO_NAME = "";

    /**
    The ObjectName property key denoting the path of the parent MBean.
    Serves to disambiguitate the ObjectName from others
    that might have the same type and/or name elsewhere in the hierarchy.
     */
    public static final String PARENT_PATH_KEY = "pp";

    /** Prefix for AMX descriptor fields */
    public static final String DESC_PREFIX = "amx.";

    /** Prefix for AMX notification types */
    public static final String NOTIFICATION_PREFIX = DESC_PREFIX;

    /**
        Descriptor value defined by JMX standard: whether the MBeanInfo is *invariant* (immutable is a misnomer).
     */
    public static final String DESC_STD_IMMUTABLE_INFO = "immutableInfo";

    /**
    Descriptor value defined by JMX standard, the classname of the interface for the MBean.
    Mainly advisory, since client code might not have access to the class.
     */
    public static final String DESC_STD_INTERFACE_NAME = "interfaceName";

    /**
    Descriptor value: The generic AMX interface to be used if the class found in {@link #DESC_STD_INTERFACE_NAME}
        cannot be loaded.  The class specified here must reside in the amx-core
    module eg com.sun.org.glassfish.admin.amx.core eg AMXProxy or AMXConfigProxy.
     */
    public static final String DESC_GENERIC_INTERFACE_NAME = DESC_PREFIX + "genericInterfaceName";

    /**
    Descriptor value: whether the MBean is a singleton, in spite of having a name property in its ObjectName.
    This is mainly for compatibility; named singletons are strongly discouraged.
     */
    public static final String DESC_IS_SINGLETON = DESC_PREFIX + "isSingleton";

    /**
    Descriptor value: whether the MBean is a global singleton eg whether in the AMX domain
    it can be looked up by its type and is the only MBean of that type.
     */
    public static final String DESC_IS_GLOBAL_SINGLETON = DESC_PREFIX + "isGlobalSingleton";

    /**
    Descriptor value: Arbitrary string denoting the general classification of MBean.
    Predefined values include "configuration", "monitoring", "jsr77", "utility", "other".
     */
    public static final String DESC_GROUP = DESC_PREFIX + "group";

    /**
    Descriptor value: whether new children may be added by code other than the implementation responsible for the MBean;
    this allows extension points within the hierarchy.
    Adding a new child means registering an MBean with an ObjectName that implies parentage via the ancestry type=name pairs.
     */
    public static final String DESC_SUPPORTS_ADOPTION = DESC_PREFIX + "supportsAdoption";

    /**
    Descriptor value: denotes the possible types of MBeans that children might be. If present, SHOULD include all possible and pre-known types.
    An empty array indicates that child MBeans might exist, but their types cannot be predicted.
     */
    public static final String DESC_SUB_TYPES = DESC_PREFIX + "subTypes";

    /**
    Group value indicating that the AMX is a configuration MBean.
     */
    public static final String GROUP_CONFIGURATION = "configuration";
    /**
    Group value indicating that the AMX represents a monitoring MBean.
     */
    public static final String GROUP_MONITORING = "monitoring";
    /**
    Group value indicating that the AMX is a utility MBean.
     */
    public static final String GROUP_UTILITY = "utility";
    /**
    Group value indicating that the AMX is a JSR 77 MBean
    (J2EE Management) .
     */
    public static final String GROUP_JSR77 = "jsr77";
    /**
    Group value indicating that the AMX is not one
    of the other types.
     */
    public static final String GROUP_OTHER = "other";
}
