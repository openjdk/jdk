/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.ui.tree;

import java.io.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;

/** Simple wrapper for displaying bad oops in the Inspector */

public class BadOopTreeNodeAdapter extends FieldTreeNodeAdapter {
  private OopHandle oop;

  public BadOopTreeNodeAdapter(OopHandle oop, FieldIdentifier id) {
    this(oop, id, false);
  }

  /** The oop may be null (for oop fields of oops which are null); the
      FieldIdentifier may also be null (for the root node). */
  public BadOopTreeNodeAdapter(OopHandle oop, FieldIdentifier id, boolean treeTableMode) {
    super(id, treeTableMode);
    this.oop = oop;
  }

  public int getChildCount() {
    return 0;
  }

  public SimpleTreeNode getChild(int index) {
    throw new RuntimeException("Should not call this");
  }

  public boolean isLeaf() {
    return true;
  }

  public int getIndexOfChild(SimpleTreeNode child) {
    throw new RuntimeException("Should not call this");
  }

  public String getValue() {
    return "** BAD OOP " + oop + " **";
  }
}
