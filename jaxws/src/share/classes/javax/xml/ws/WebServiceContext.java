/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.xml.ws;

import java.security.Principal;
import javax.xml.ws.handler.MessageContext;

/**
 *  A <code>WebServiceContext</code> makes it possible for
 *  a web service endpoint implementation class to access
 *  message context and security information relative to
 *  a request being served.
 *
 *  Typically a <code>WebServiceContext</code> is injected
 *  into an endpoint implementation class using the
 *  <code>Resource</code> annotation.
 *
 *  @since JAX-WS 2.0
 *
 *  @see javax.annotation.Resource
**/
public interface WebServiceContext {

  /**
   *  Returns the MessageContext for the request being served
   *  at the time this method is called. Only properties with
   *  APPLICATION scope will be visible to the application.
   *
   *  @return MessageContext The message context.
   *
   *  @throws IllegalStateException This exception is thrown
   *          if the method is called while no request is
   *          being serviced.
   *
   *  @see javax.xml.ws.handler.MessageContext
   *  @see javax.xml.ws.handler.MessageContext.Scope
   *  @see java.lang.IllegalStateException
  **/
  public MessageContext getMessageContext();

  /**
   *  Returns the Principal that identifies the sender
   *  of the request currently being serviced. If the
   *  sender has not been authenticated, the method
   *  returns <code>null</code>.
   *
   *  @return Principal The principal object.
   *
   *  @throws IllegalStateException This exception is thrown
   *          if the method is called while no request is
   *          being serviced.
   *
   *  @see java.security.Principal
   *  @see java.lang.IllegalStateException
  **/
  public Principal getUserPrincipal();

  /**
   *  Returns a boolean indicating whether the
   *  authenticated user is included in the specified
   *  logical role. If the user has not been
   *  authenticated, the method returns </code>false</code>.
   *
   *  @param role  A <code>String</code> specifying the name of the role
   *
   *  @return a <code>boolean</code> indicating whether
   *  the sender of the request belongs to a given role
   *
   *  @throws IllegalStateException This exception is thrown
   *          if the method is called while no request is
   *          being serviced.
  **/
  public boolean isUserInRole(String role);
}
