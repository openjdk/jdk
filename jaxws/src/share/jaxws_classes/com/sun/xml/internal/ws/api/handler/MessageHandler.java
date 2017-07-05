/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.handler;

import javax.xml.ws.handler.Handler;
import javax.xml.namespace.QName;
import java.util.Set;


/**
 * The <code>MessageHandler</code> class extends <code>Handler</code>
 * to provide typesafety for the message context parameter and add a method
 * to obtain access to the headers that may be processed by the handler.
 * Its provides similar functionality as a SOAPHandler but provides RI's
 * Message in the MessageContext.
 *
 * @author Rama Pulavarthi
 * @since JAX-WS 2.1.3
 */
public interface MessageHandler<C extends MessageHandlerContext> extends Handler<C> {


   /** Gets the header blocks that can be processed by this Handler
   *  instance.
   *
   *  @return Set of <code>QNames</code> of header blocks processed by this
   *           handler instance. <code>QName</code> is the qualified
   *           name of the outermost element of the Header block.
  **/
  Set<QName> getHeaders();

}
