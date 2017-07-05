/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.handler;

import javax.xml.namespace.QName;

/**
 *  The <code>PortInfo</code> interface is used by a
 *  <code>HandlerResolver</code> to query information about
 *  the port it is being asked to create a handler chain for.
 *  <p>
 *  This interface is never implemented by an application,
 *  only by a JAX-WS implementation.
 *
 *  @since JAX-WS 2.0
**/
public interface PortInfo {

  /**
   *  Gets the qualified name of the WSDL service name containing
   *  the port being accessed.
   *
   *  @return javax.xml.namespace.QName The qualified name of the WSDL service.
  **/
  public QName getServiceName();

  /**
   *  Gets the qualified name of the WSDL port being accessed.
   *
   *  @return javax.xml.namespace.QName The qualified name of the WSDL port.
  **/
  public QName getPortName();

  /**
   *  Gets the URI identifying the binding used by the port being accessed.
   *
   *  @return String The binding identifier for the port.
   *
   *  @see javax.xml.ws.Binding
  **/
  public String getBindingID();

}
