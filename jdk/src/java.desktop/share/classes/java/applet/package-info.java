/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Provides the classes necessary to create an applet and the classes an applet
 * uses to communicate with its applet context.
 * <p>
 * The applet framework involves two entities: the <i>applet</i> and the
 * <i>applet context</i>. An applet is an embeddable window (see the Panel
 * class) with a few extra methods that the applet context can use to
 * initialize, start, and stop the applet.
 * <p>
 * The applet context is an application that is responsible for loading and
 * running applets. For example, the applet context could be a Web browser or an
 * applet development environment.
 * <p>
 * The APIs in this package are all deprecated. Alternative technologies such as
 * Java Web Start or installable applications should be used instead.
 * See <a href="http://openjdk.java.net/jeps/289">JEP 289</a> and
 * the Oracle White Paper
 * <a href="http://www.oracle.com/technetwork/java/javase/migratingfromapplets-2872444.pdf">
 * "Migrating from Java Applets to plugin-free Java technologies"</a> for more
 * information.
 *
 * @since 1.0
 */
package java.applet;
