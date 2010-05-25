/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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

/**
 *  Regression test for:
 *  Javadoc puts abstract keyword in the modifier string for the interfaces.
 *  Keyword `abstract' is not necessary since interfaces are implicitly
 *  abstract.
 *
 *  @bug 4210388
 *  @summary Javadoc declares interfaces to be "abstract".
 *  @build ModifierAbstract.java
 *  @run shell ModifierAbstractWrapper.sh
 *  @author Atul M Dambalkar
 */

import com.sun.javadoc.*;
import java.lang.*;

public class ModifierAbstract {

  public static boolean start(RootDoc root) throws Exception {

    ClassDoc[] classarr = root.classes();
    for (int i = 0; i < classarr.length; i++) {
        if (classarr[i].isInterface()) {
            String modifier = classarr[i].modifiers();
            if (modifier.indexOf("abstract") > 0) {
                throw new Exception("Keyword `abstract' found in the " +
                                    "modifier string for class " +
                                    classarr[i].qualifiedName());
            }
        }
    }
    return true;
  }
}
