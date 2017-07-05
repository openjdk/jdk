/*
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

/*
 * Copyright (c) 2003 by BEA Systems, Inc. All Rights Reserved.
 */

package javax.xml.stream;

/**
 * An error class for reporting factory configuration errors.
 *
 * @author Copyright (c) 2003 by BEA Systems. All Rights Reserved.
 * @since 1.6
 */
public class FactoryConfigurationError extends Error {

  Exception nested;

  /**
   * Default constructor
   */
  public FactoryConfigurationError(){}

  /**
   * Construct an exception with a nested inner exception
   *
   * @param e the exception to nest
   */
  public FactoryConfigurationError(java.lang.Exception e){
    nested = e;
  }

  /**
   * Construct an exception with a nested inner exception
   * and a message
   *
   * @param e the exception to nest
   * @param msg the message to report
   */
  public FactoryConfigurationError(java.lang.Exception e, java.lang.String msg){
    super(msg);
    nested = e;
  }

  /**
   * Construct an exception with a nested inner exception
   * and a message
   *
   * @param msg the message to report
   * @param e the exception to nest
   */
  public FactoryConfigurationError(java.lang.String msg, java.lang.Exception e){
    super(msg);
    nested = e;
  }

  /**
   * Construct an exception with associated message
   *
   * @param msg the message to report
   */
  public FactoryConfigurationError(java.lang.String msg) {
    super(msg);
  }

  /**
   * Return the nested exception (if any)
   *
   * @return the nested exception or null
   */
  public Exception getException() {
    return nested;
  }


  /**
   * Report the message associated with this error
   *
   * @return the string value of the message
   */
  public String getMessage() {
    String msg = super.getMessage();
    if(msg != null)
      return msg;
    if(nested != null){
      msg = nested.getMessage();
      if(msg == null)
        msg = nested.getClass().toString();
    }
    return msg;
  }



}
