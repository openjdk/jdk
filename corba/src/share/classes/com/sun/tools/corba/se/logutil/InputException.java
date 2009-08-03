/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.LinkedList;
import java.util.Queue;

public class InputException {

  /**
   * The name of this exception.
   */
  private final String name;

  /**
   * The codes associated with this exception.
   */
  private final Queue<InputCode> codes;

  /**
   * Constructs a new {@link InputException} with the
   * specified name.
   *
   * @param name the name of the new exception;
   */
  public InputException(final String name) {
    this.name = name;
    codes = new LinkedList<InputCode>();
  }

  /**
   * Adds a new code to this exception.
   *
   * @param c the code to add.
   */
  public void add(InputCode c)
  {
    codes.offer(c);
  }

  /**
   * Returns the name of this exception.
   *
   * @return the exception's name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the codes associated with this exception.
   *
   * @return the exception's codes.
   */
  public Queue<InputCode> getCodes() {
    return codes;
  }

  /**
   * Returns a textual representation of this exception.
   *
   * @return a textual representation.
   */
  public String toString() {
    return getClass().getName()
      + "[name=" + name
      + ",codes=" + codes
      + "]";
  }

}
