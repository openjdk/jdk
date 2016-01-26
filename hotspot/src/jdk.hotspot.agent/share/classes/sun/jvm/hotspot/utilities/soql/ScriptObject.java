/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package sun.jvm.hotspot.utilities.soql;

/**
 * Any Java object supporting this interface can be
 * accessed from scripts with "simpler" access pattern.
 * For example, a script engine may support natural
 * property/field access syntax for the properties exposed
 * via this interface. We use this interface so that we
 * can dynamically add/delete/modify fields exposed to
 * scripts. If we stick to JavaBean pattern, then property
 * set is fixed.
 */
public interface ScriptObject {
  // special sentinel to denote no-result -- so that
  // null could be used as proper value
  public static final Object UNDEFINED = new Object();
  // empty object array
  public static final Object[] EMPTY_ARRAY = new Object[0];

  /*
   * Returns all property names supported by this object.
   * Property "name" is either a String or an Integer".
   */
  public Object[] getIds();

  /**
   * Get the value of the named property.
   */
  public Object get(String name);

  /**
   * Get the value of the "indexed" property.
   * Returns UNDEFINED if the property does not exist.
   */
  public Object get(int index);

  /**
   * Set the value of the named property.
   */
  public void put(String name, Object value);

  /**
   * Set the value of the indexed property.
   */
  public void put(int index, Object value);

  /**
   * Returns whether the named property exists or not.
   */
  public boolean has(String name);

  /**
   * Returns whether the indexed property exists or not.
   */
  public boolean has(int index);

  /**
   * Deletes the named property. Returns true on success.
   */
  public boolean delete(String name);

  /**
   * Deletes the indexed property. Returns true on success.
   */
  public boolean delete(int index);
}
