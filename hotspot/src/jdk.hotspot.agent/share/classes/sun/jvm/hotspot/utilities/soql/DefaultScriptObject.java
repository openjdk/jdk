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

import java.util.*;

/**
 * Dummy implementation for ScriptObject interface. This class
 * supports empty set of named and indexed properties. Returns
 * false always for "has" calls. And ignores "delete" and "put"
 * calls.
 */
public class DefaultScriptObject implements ScriptObject {
  public Object[] getIds() {
    return EMPTY_ARRAY;
  }

  public Object get(String name) {
    return UNDEFINED;
  }

  public Object get(int index) {
    return UNDEFINED;
  }

  public void put(String name, Object value) {
  }

  public void put(int index, Object value) {
  }

  public boolean has(String name) {
    return false;
  }

  public boolean has(int index) {
    return false;
  }

  public boolean delete(String name) {
    return false;
  }

  public boolean delete(int index) {
    return false;
  }
}
