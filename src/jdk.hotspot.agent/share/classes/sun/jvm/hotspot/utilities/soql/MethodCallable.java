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

import java.lang.reflect.Method;
import javax.script.ScriptException;

/**
 * An implementation of Callable interface that
 * invokes an instance or static Java method when
 * called.
 */
public class MethodCallable implements Callable {
  private Object target;
  private Method method;
  private boolean wrapArgs;

  // "wrapArgs" tells whether the underlying java Method
  // accepts one Object[] argument or it wants usual way of
  // passing arguments. The former method is used when you
  // want to implement a Callable that is variadic.
  public MethodCallable(Object target, Method method, boolean wrapArgs) {
    this.method = method;
    this.target = target;
    this.wrapArgs = wrapArgs;
  }

  public MethodCallable(Object target, Method method) {
    this(target, method, true);
  }

  public Object call(Object[] args) throws ScriptException {
    try {
      if (wrapArgs) {
        return method.invoke(target, new Object[] { args });
      } else {
        return method.invoke(target, args);
      }
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception exp) {
      throw new ScriptException(exp);
    }
  }
}
