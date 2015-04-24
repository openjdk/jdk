/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 *  {@code HandlerResolver} is an interface implemented
 *  by an application to get control over the handler chain
 *  set on proxy/dispatch objects at the time of their creation.
 *  <p>
 *  A {@code HandlerResolver} may be set on a {@code Service}
 *  using the {@code setHandlerResolver} method.
 *  <p>
 *  When the runtime invokes a {@code HandlerResolver}, it will
 *  pass it a {@code PortInfo} object containing information
 *  about the port that the proxy/dispatch object will be accessing.
 *
 *  @see javax.xml.ws.Service#setHandlerResolver
 *
 *  @since 1.6, JAX-WS 2.0
**/
public interface HandlerResolver {

  /**
   *  Gets the handler chain for the specified port.
   *
   *  @param portInfo Contains information about the port being accessed.
   *  @return {@code java.util.List<Handler>} chain
  **/
  public java.util.List<Handler> getHandlerChain(PortInfo portInfo);
}
