/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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


import java.io.*;
import java.util.Hashtable;
import java.util.*;



/** This class is used for implementing enumerated values.
 *
 * An enumeration is represented by a class derived from Enumerated.
 * The derived class defines what are the permitted values in the enumeration.
 *
 * An enumerated value is represented by an instance of the derived class.
 * It can be represented :
 *  - as an integer
 *  - as a string
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

abstract public class Enumerated  implements Serializable {

  /**
   * Construct an enumerated with a default value.
   * The default value is the first available in getIntTable().
    * @exception IllegalArgumentException One of the arguments passed to the method is illegal or inappropriate.
   */
  public Enumerated() throws IllegalArgumentException {
    Enumeration e =getIntTable().keys() ;
    if (e.hasMoreElements()) {
      value = ((Integer)e.nextElement()).intValue() ;
    }
    else {
      throw new IllegalArgumentException() ;
    }
  }

  /**
   * Construct an enumerated from its integer form.
   *
   * @param valueIndex The integer form.
   * @exception IllegalArgumentException One of the arguments passed to
   *            the method is illegal or inappropriate.
   */
  public Enumerated(int valueIndex) throws IllegalArgumentException {
    if (getIntTable().get(new Integer(valueIndex)) == null) {
      throw new IllegalArgumentException() ;
    }
    value = valueIndex ;
  }

  /**
   * Construct an enumerated from its Integer form.
   *
   * @param valueIndex The Integer form.
   * @exception IllegalArgumentException One of the arguments passed to
   *            the method is illegal or inappropriate.
   */
  public Enumerated(Integer valueIndex) throws IllegalArgumentException {
    if (getIntTable().get(valueIndex) == null) {
      throw new IllegalArgumentException() ;
    }
    value = valueIndex.intValue() ;
  }


  /**
   * Construct an enumerated from its string form.
   *
   * @param valueString The string form.
   * @exception IllegalArgumentException One of the arguments passed
   *  to the method is illegal or inappropriate.
   */
  public Enumerated(String valueString) throws IllegalArgumentException {
    Integer index = (Integer)getStringTable().get(valueString) ;
    if (index == null) {
      throw new IllegalArgumentException() ;
    }
    else {
      value = index.intValue() ;
    }
  }


  /**
   * Return the integer form of the enumerated.
   *
   * @return The integer form
   */

  public int intValue() {
    return value ;
  }


  /**
   * Returns an Java enumeration of the permitted integers.
   *
   * @return An enumeration of Integer instances
   */

  public Enumeration valueIndexes() {
    return getIntTable().keys() ;
  }


  /**
   * Returns an Java enumeration of the permitted strings.
   *
   * @return An enumeration of String instances
   */

  public Enumeration valueStrings() {
    return getStringTable().keys() ;
  }


  /**
   * Compares this enumerated to the specified enumerated.
   *
   * The result is true if and only if the argument is not null
   * and is of the same class.
   *
   * @param obj The object to compare with.
   *
   * @return True if this and obj are the same; false otherwise
   */
  public boolean equals(Object obj) {

    return ((obj != null) &&
            (getClass() == obj.getClass()) &&
            (value == ((Enumerated)obj).value)) ;
  }


  /**
   * Returns the hash code for this enumerated.
   *
   * @return A hash code value for this object.
   */
  public int hashCode() {
    String hashString = getClass().getName() + String.valueOf(value) ;
    return hashString.hashCode() ;
  }


  /**
   * Returns the string form of this enumerated.
   *
   * @return The string for for this object.
   */

  public String toString() {
    return (String)getIntTable().get(new Integer(value)) ;
  }


  /**
   * Returns the hashtable of the integer forms.
   * getIntTable().get(x) returns the string form associated
   * to the integer x.
   *
   * This method must be implemented by the derived class.
   *
   * @return An hashtable for read-only purpose
   */

  protected abstract Hashtable getIntTable() ;



  /**
   * Returns the hashtable of the string forms.
   * getStringTable().get(s) returns the integer form associated
   * to the string s.
   *
   * This method must be implemented by the derived class.
   *
   * @return An hashtable for read-only purpose
   */

  protected abstract Hashtable getStringTable() ;


  /**
   * This variable keeps the integer form of the enumerated.
   * The string form is retreived using getIntTable().
   */
  protected int value ;

}
