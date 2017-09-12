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

package javax.xml.ws.soap;


import java.util.Set;
import javax.xml.ws.Binding;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.MessageFactory;

/** The {@code SOAPBinding} interface is an abstraction for
 *  the SOAP binding.
 *
 *  @since 1.6, JAX-WS 2.0
**/
public interface SOAPBinding extends Binding {

  /**
   * A constant representing the identity of the SOAP 1.1 over HTTP binding.
   */
  public static final String SOAP11HTTP_BINDING = "http://schemas.xmlsoap.org/wsdl/soap/http";

  /**
   * A constant representing the identity of the SOAP 1.2 over HTTP binding.
   */
  public static final String SOAP12HTTP_BINDING = "http://www.w3.org/2003/05/soap/bindings/HTTP/";

  /**
   * A constant representing the identity of the SOAP 1.1 over HTTP binding
   * with MTOM enabled by default.
   */
  public static final String SOAP11HTTP_MTOM_BINDING = "http://schemas.xmlsoap.org/wsdl/soap/http?mtom=true";

  /**
   * A constant representing the identity of the SOAP 1.2 over HTTP binding
   * with MTOM enabled by default.
   */
  public static final String SOAP12HTTP_MTOM_BINDING = "http://www.w3.org/2003/05/soap/bindings/HTTP/?mtom=true";


  /** Gets the roles played by the SOAP binding instance.
   *
   *  @return {@code Set<String>} The set of roles played by the binding instance.
  **/
  public Set<String> getRoles();

  /** Sets the roles played by the SOAP binding instance.
   *
   *  @param roles    The set of roles played by the binding instance.
   *  @throws javax.xml.ws.WebServiceException On an error in the configuration of
   *                  the list of roles.
  **/
  public void setRoles(Set<String> roles);

  /**
   * Returns {@code true} if the use of MTOM is enabled.
   *
   * @return {@code true} if and only if the use of MTOM is enabled.
  **/

  public boolean isMTOMEnabled();

  /**
   * Enables or disables use of MTOM.
   *
   * @param flag   A {@code boolean} specifying whether the use of MTOM should
   *               be enabled or disabled.
   * @throws javax.xml.ws.WebServiceException If the specified setting is not supported
   *                  by this binding instance.
   *
   **/
  public void setMTOMEnabled(boolean flag);

  /**
   * Gets the SAAJ {@code SOAPFactory} instance used by this SOAP binding.
   *
   * @return SOAPFactory instance used by this SOAP binding.
  **/
  public SOAPFactory getSOAPFactory();

  /**
   * Gets the SAAJ {@code MessageFactory} instance used by this SOAP binding.
   *
   * @return MessageFactory instance used by this SOAP binding.
  **/
  public MessageFactory getMessageFactory();
}
