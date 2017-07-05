/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.ws.ProtocolException;
import javax.xml.ws.handler.MessageContext;

/** The <code>Handler</code> interface
 *  is the base interface for JAX-WS handlers.
 *
 *  @since JAX-WS 2.0
**/
public interface Handler<C extends MessageContext> {

  /** The <code>handleMessage</code> method is invoked for normal processing
   *  of inbound and outbound messages. Refer to the description of the handler
   *  framework in the JAX-WS specification for full details.
   *
   *  @param context the message context.
   *  @return An indication of whether handler processing should continue for
   *  the current message
   *                 <ul>
   *                 <li>Return <code>true</code> to continue
   *                     processing.</li>
   *                 <li>Return <code>false</code> to block
   *                     processing.</li>
   *                  </ul>
   *  @throws RuntimeException Causes the JAX-WS runtime to cease
   *    handler processing and generate a fault.
   *  @throws ProtocolException Causes the JAX-WS runtime to switch to
   *    fault message processing.
  **/
  public boolean handleMessage(C context);

  /** The <code>handleFault</code> method is invoked for fault message
   *  processing.  Refer to the description of the handler
   *  framework in the JAX-WS specification for full details.
   *
   *  @param context the message context
   *  @return An indication of whether handler fault processing should continue
   *  for the current message
   *                 <ul>
   *                 <li>Return <code>true</code> to continue
   *                     processing.</li>
   *                 <li>Return <code>false</code> to block
   *                     processing.</li>
   *                  </ul>
   *  @throws RuntimeException Causes the JAX-WS runtime to cease
   *    handler fault processing and dispatch the fault.
   *  @throws ProtocolException Causes the JAX-WS runtime to cease
   *    handler fault processing and dispatch the fault.
  **/
  public boolean handleFault(C context);

  /**
   * Called at the conclusion of a message exchange pattern just prior to
   * the JAX-WS runtime dispatching a message, fault or exception.  Refer to
   * the description of the handler
   * framework in the JAX-WS specification for full details.
   *
   * @param context the message context
  **/
  public void close(MessageContext context);
}
