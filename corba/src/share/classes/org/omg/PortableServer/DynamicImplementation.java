/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.omg.PortableServer;

/**
 * Allows dynamic handling of object invocations.  POA-based DSI
 * servants inherit from the
 * standard <code>DynamicImplementation</code> class, this class inherits
 * from the <code>Servant</code> class. Based on IDL to Java spec.
 * CORBA V 2.3.1 ptc/00-01-08.pdf.
 */
abstract public class DynamicImplementation extends Servant {

/**
 * Receives requests issued to any CORBA object
 * incarnated by the DSI servant and performs the processing
 * necessary to execute the request.
 * @param request the request issued to the CORBA object.
 */
    abstract public void invoke(org.omg.CORBA.ServerRequest request);
}
