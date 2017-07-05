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

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import javax.script.Invocable;

/**
 * Simple implementation of ScriptObject interface
 * with property storage backed by a Map. This class
 * can be extended to override any of the methods, in
 * particular to add "function" valued properties.
 */
public class MapScriptObject implements ScriptObject {
  // map to store the properties
  private Map map;

  public MapScriptObject() {
    this(new HashMap());
  }

  public MapScriptObject(Map map) {
    // make it synchronized
    this.map = Collections.synchronizedMap(map);
  }

  public Object[] getIds() {
    return map.keySet().toArray();
  }

  public Object get(String name) {
    if (has(name)) {
      return map.get(name);
    } else {
      return UNDEFINED;
    }
  }

  public Object get(int index) {
    if (has(index)) {
      Object key = Integer.valueOf(index);
      return map.get(key);
    } else {
      return UNDEFINED;
    }
  }

  public void put(String name, Object value) {
    map.put(name, value);
  }

  public void put(int index, Object value) {
    map.put(Integer.valueOf(index), value);
  }

  public boolean has(String name) {
    return map.containsKey(name);
  }

  public boolean has(int index) {
    return map.containsKey(Integer.valueOf(index));
  }

  public boolean delete(String name) {
    if (map.containsKey(name)) {
      map.remove(name);
      return true;
    } else {
      return false;
    }
  }

  public boolean delete(int index) {
    Object key = Integer.valueOf(index);
    if (map.containsKey(key)) {
      map.remove(key);
      return true;
    } else {
      return false;
    }
  }

  // add a function valued property that invokes given Method
  protected void putFunction(Object target, Method method) {
    putFunction(target, method, true);
  }

  // add a function valued property that invokes given Method
  protected void putFunction(Object target, Method method, boolean wrapArgs) {
    map.put(method.getName(), new MethodCallable(target, method, wrapArgs));
  }

  // add a function valued property that invokes given script function
  protected void putFunction(Object target, String name, Invocable invocable) {
    map.put(name, new InvocableCallable(target, name, invocable));
  }
}
