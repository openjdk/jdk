/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines the full API of the Java SE Platform.
 * <P>
 * This module requires the <a href="java.se-summary.html">{@code java.se}</a>
 * module and supplements it with modules that define the CORBA and Java EE
 * APIs. These modules are upgradeable.
 *
 * @moduleGraph
 * @since 9
 */
// suppress warning for java.corba and other modules
@SuppressWarnings({"deprecation", "removal"})
@Deprecated(since="9", forRemoval=true)
module java.se.ee {

    requires transitive java.se;

    // Upgradeable modules for Java EE technologies
    requires transitive java.activation;
    requires transitive java.corba;
    requires transitive java.transaction;
    requires transitive java.xml.bind;
    requires transitive java.xml.ws;
    requires transitive java.xml.ws.annotation;

}
