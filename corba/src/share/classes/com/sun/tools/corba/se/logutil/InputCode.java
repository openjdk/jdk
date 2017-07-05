/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.corba.se.logutil;

public class InputCode {

  /**
   * The name of this code.
   */
  private final String name;

  /**
   * The code.
   */
  private final int code;

  /**
   * The log level for this code.
   */
  private final String logLevel;

  /**
   * The error message for this code.
   */
  private final String message;

  /**
   * Creates a new error code with the specified name, code,
   * log level and error message.
   *
   * @param name the name of the new code.
   * @param code the code itself.
   * @param logLevel the level of severity of this error.
   * @param message the error message for this code.
   */
  public InputCode(final String name, final int code,
                   final String logLevel, final String message) {
    this.name = name;
    this.code = code;
    this.logLevel = logLevel;
    this.message = message;
  }

  /**
   * Returns the name of this code.
   *
   * @return the name of the code.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the code.
   *
   * @return the code.
   */
  public int getCode() {
    return code;
  }

  /**
   * Returns the severity of this code.
   *
   * @return the log level severity of the code.
   */
  public String getLogLevel() {
    return logLevel;
  }

  /**
   * Returns the error message for this code.
   *
   * @return the error message for this code.
   */
  public String getMessage() {
    return message;
  }

  /**
   * Returns a textual representation of this code.
   *
   * @return a textual representation.
   */
  public String toString() {
    return getClass().getName() +
      "[name=" + name +
      ",code=" + code +
      ",logLevel=" + logLevel +
      ",message=" + message +
      "]";
  }

}
