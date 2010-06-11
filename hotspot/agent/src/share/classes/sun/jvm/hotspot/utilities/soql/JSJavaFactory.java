/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.utilities.soql;

import java.util.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

public interface JSJavaFactory {
   public JSJavaObject newJSJavaObject(Oop oop);
   public JSJavaKlass  newJSJavaKlass(Klass klass);
   public JSJavaField  newJSJavaField(Field f);
   public JSJavaThread newJSJavaThread(JavaThread jt);
   public JSJavaFrame  newJSJavaFrame(JavaVFrame vf);
   public JSList       newJSList(List l);
   public JSMap        newJSMap(Map m);
   public JSJavaHeap   newJSJavaHeap();
   public JSJavaVM     newJSJavaVM();
   // checks for one of the above special cases
   public Object       newJSJavaWrapper(Object o);
}
