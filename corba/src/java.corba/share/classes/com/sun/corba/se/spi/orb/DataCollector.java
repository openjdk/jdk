/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.spi.orb ;

import java.applet.Applet ;
import java.util.Properties ;
import java.util.Vector ;

/** Interface for collecting all sources of ORB configuration properties
 * into a single properties object.   A PropertyParser is needed so that
 * the set of property names of interest is known.
 */
public interface DataCollector {
    /** Return true iff this DataCollector was created from
     * applet data.
     */
    boolean isApplet() ;

    /** Return true iff the local host and ORB initial host are the same.
    * This is provided to avoid exposing the local host in insecure
    * contexts.
    */
    boolean initialHostIsLocal() ;

    /** Set the parser which is used to obtain property names.
     * This must be called before getProperties
     * may be called.  It may be called multiple times if different
     * sets of properties are needed for the same data sources.
     */
    void setParser( PropertyParser parser ) ;

    /** Return the consolidated property information to be used
     * for ORB configuration.  Note that -ORBInitRef arguments are
     * handled specially: all -ORBInitRef name=value arguments are
     * converted into ( org.omg.CORBA.ORBInitRef.name, value )
     * mappings in the resulting properties.  Also, -ORBInitialServices
     * is handled specially in applet mode: they are converted from
     * relative to absolute URLs.
     * @raises IllegalStateException if setPropertyNames has not
     * been called.
     */
    Properties getProperties() ;
}
