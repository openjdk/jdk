/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
    Small utilities for AMXBooter and related.
 */
@com.sun.org.glassfish.external.arc.Taxonomy(stability = com.sun.org.glassfish.external.arc.Stability.UNCOMMITTED)
public final class AMXUtil
{
    private AMXUtil() {}

    /**
        Make a new ObjectName (unchecked exception).
     */
    public static ObjectName newObjectName(final String s)
    {
        try
        {
            return new ObjectName( s );
        }
        catch( final Exception e )
        {
            throw new RuntimeException("bad ObjectName", e);
        }
    }

    /**
        Make a new ObjectName (unchecked exception).
        @param domain
        @param props
     */
    public static ObjectName newObjectName(
            final String domain,
            final String props)
    {
        return newObjectName(domain + ":" + props);
    }

    /**
        Get the ObjectName of the MBeanServerDelegateObjectName.
     */
    public static ObjectName getMBeanServerDelegateObjectName()
    {
        return newObjectName( "JMImplementation:type=MBeanServerDelegate" );
    }

    public static String prop(final String key, final String value)
    {
        return key + "=" + value;
    }
}
