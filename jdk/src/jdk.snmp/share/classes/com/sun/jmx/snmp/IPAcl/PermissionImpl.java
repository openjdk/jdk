/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp.IPAcl;



import java.io.Serializable;


/**
 * Permission is represented as a String.
 *
 * @see java.security.acl.Permission
 */

class PermissionImpl implements java.security.acl.Permission, Serializable {
  private static final long serialVersionUID = 4478110422746916589L;

  private String perm = null;

  /**
   * Constructs a permission.
   *
   * @param s the string representing the permission.
   */
  public PermissionImpl(String s) {
        perm = s;
  }

  public int hashCode() {
        return super.hashCode();
  }

  /**
   * Returns true if the object passed matches the permission represented in.
   *
   * @param p the Permission object to compare with.
   * @return true if the Permission objects are equal, false otherwise.
   */
  public boolean equals(Object p){
        if (p instanceof PermissionImpl){
          return perm.equals(((PermissionImpl)p).getString());
        } else {
          return false;
        }
  }

  /**
   * Prints a string representation of this permission.
   *
   * @return a string representation of this permission.
   */
  public String toString(){
        return perm;
  }

  /**
   * Prints the permission.
   *
   * @return a string representation of this permission.
   */
  public String getString(){
        return perm;
  }
}
