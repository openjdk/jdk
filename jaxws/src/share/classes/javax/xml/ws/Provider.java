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

package javax.xml.ws;

/**
 *  <p>Service endpoints may implement the <code>Provider</code>
 *  interface as a dynamic alternative to an SEI.
 *
 *  <p>Implementations are required to support <code>Provider&lt;Source&gt;</code>,
 *  <code>Provider&lt;SOAPMessage&gt;</code> and
 *  <code>Provider&lt;DataSource&gt;</code>, depending on the binding
 *  in use and the service mode.
 *
 *  <p>The <code>ServiceMode</code> annotation can be used to control whether
 *  the <code>Provider</code> instance will receive entire protocol messages
 *  or just message payloads.
 *
 *  @since JAX-WS 2.0
 *
 *  @see javax.xml.transform.Source
 *  @see javax.xml.soap.SOAPMessage
 *  @see javax.xml.ws.ServiceMode
**/
public interface Provider<T> {

  /** Invokes an operation occording to the contents of the request
   *  message.
   *
   *  @param  request The request message or message payload.
   *  @return The response message or message payload. May be <code>null</code> if
              there is no response.
   *  @throws WebServiceException If there is an error processing request.
   *          The cause of the <code>WebServiceException</code> may be set to a subclass
   *          of <code>ProtocolException</code> to control the protocol level
   *          representation of the exception.
   *  @see javax.xml.ws.handler.MessageContext
   *  @see javax.xml.ws.ProtocolException
  **/
  public T invoke(T request);
}
