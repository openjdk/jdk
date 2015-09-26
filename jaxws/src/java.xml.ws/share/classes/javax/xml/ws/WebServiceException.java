/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws;

/** The {@code WebServiceException} class is the base
 *  exception class for all JAX-WS API runtime exceptions.
 *
 *  @since 1.6, JAX-WS 2.0
**/

public class WebServiceException extends java.lang.RuntimeException {

  /** Constructs a new exception with {@code null} as its
   *  detail message. The cause is not initialized.
  **/
  public WebServiceException() {
    super();
  }

  /** Constructs a new exception with the specified detail
   *  message.  The cause is not initialized.
   *  @param message The detail message which is later
   *                 retrieved using the getMessage method
  **/
  public WebServiceException(String message) {
    super(message);
  }

  /** Constructs a new exception with the specified detail
   *  message and cause.
   *
   *  @param message The detail message which is later retrieved
   *                 using the getMessage method
   *  @param cause   The cause which is saved for the later
   *                 retrieval throw by the getCause method
  **/
  public WebServiceException(String message, Throwable cause) {
    super(message,cause);
  }

  /** Constructs a new WebServiceException with the specified cause
   *  and a detail message of
   *  {@code (cause==null ? null : cause.toString())}
   *  (which typically contains the
   *  class and detail message of {@code cause}).
   *
   *  @param cause   The cause which is saved for the later
   *                 retrieval throw by the getCause method.
   *                 (A {@code null} value is permitted, and
   *                 indicates that the cause is nonexistent or
     *               unknown.)
  **/
  public WebServiceException(Throwable cause) {
    super(cause);
  }

}
