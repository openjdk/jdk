/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp;


/**
 * Is used internally to signal that the size of a PDU exceeds the packet size limitation.
 * <p>
 * You will not usually need to use this class, except if you
 * decide to implement your own
 * {@link com.sun.jmx.snmp.SnmpPduFactory SnmPduFactory} object.
 * <p>
 * The <CODE>varBindCount</CODE> property contains the
 * number of <CODE>SnmpVarBind</CODE> successfully encoded
 * before the exception was thrown. Its value is 0
 * when this number is unknown.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpTooBigException extends Exception {
  private static final long serialVersionUID = 4754796246674803969L;

  /**
   * Builds an <CODE>SnmpTooBigException</CODE> with
   * <CODE>varBindCount</CODE> set to 0.
   */
  public SnmpTooBigException() {
    varBindCount = 0 ;
  }

  /**
   * Builds an <CODE>SnmpTooBigException</CODE> with
   * <CODE>varBindCount</CODE> set to the specified value.
   * @param n The <CODE>varBindCount</CODE> value.
   */
  public SnmpTooBigException(int n) {
    varBindCount = n ;
  }


  /**
   * Returns the number of <CODE>SnmpVarBind</CODE> successfully
   * encoded before the exception was thrown.
   *
   * @return A positive integer (0 means the number is unknown).
   */
  public int getVarBindCount() {
    return varBindCount ;
  }

  /**
   * The <CODE>varBindCount</CODE>.
   * @serial
   */
  private int varBindCount ;
}
