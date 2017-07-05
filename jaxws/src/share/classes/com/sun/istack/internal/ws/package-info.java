/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 *
 * <P>This document describes the {@link com.sun.mirror.apt.AnnotationProcessor AnnotationProcessor}
 * included with JAX-WS 2.0.1.
 *
 * <p>The {@link com.sun.istack.internal.ws.AnnotationProcessorFactoryImpl AnnoatationnProcessorFactoryImpl} class
 * tells the <a href="http://java.sun.com/j2se/1.5.0/docs/tooldocs/share/apt.html">APT</a>
 * framework that there exists an {@com.sun.mirror.apt.AnnotationProcessor AnnotationProcessor}
 * ({@link com.sun.istak.ws.WSAP WSAP}) for for processing javax.jws.*, javax.jws.soap.*,
 *  and javax.xml.ws.* annotations.
 *
 * @ArchitectureDocument
*/
package com.sun.istack.internal.ws;
