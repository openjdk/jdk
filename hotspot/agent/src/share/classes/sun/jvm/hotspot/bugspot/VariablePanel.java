/*
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.bugspot;

import java.awt.*;
import javax.swing.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.bugspot.tree.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.ui.tree.*;
import sun.jvm.hotspot.ui.treetable.*;

/** Manages display of a set of local variables in a frame, or the
    contents of the "this" pointer */

public class VariablePanel extends JPanel {
  private JTreeTable treeTable;
  private SimpleTreeTableModel model;
  private SimpleTreeGroupNode root;

  public VariablePanel() {
    super();

    model = new SimpleTreeTableModel();
    model.setValuesEditable(false);
    root = new SimpleTreeGroupNode();
    model.setRoot(root);
    treeTable = new JTreeTable(model);
    treeTable.setRootVisible(false);
    treeTable.setShowsRootHandles(true);
    treeTable.setShowsIcons(false);
    treeTable.setTreeEditable(false);
    treeTable.getTableHeader().setReorderingAllowed(false);
    treeTable.setCellSelectionEnabled(true);
    treeTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    treeTable.setDragEnabled(true);
    JScrollPane sp = new JScrollPane(treeTable);
    sp.getViewport().setBackground(Color.white);

    setLayout(new BorderLayout());
    add(sp, BorderLayout.CENTER);
  }

  /** Clear the contents of this VariablePanel */
  public void clear() {
    root.removeAllChildren();
    model.fireTreeStructureChanged();
  }

  /** Update the contents of this VariablePanel from the given CFrame */
  public void update(CFrame fr) {
    // Collect locals
    CCollector coll = new CCollector();
    fr.iterateLocals(coll);
    update(coll);
  }

  /** Update the contents of this VariablePanel from the given JavaVFrame */
  public void update(JavaVFrame jfr) {
    Method m = jfr.getMethod();
    if (!m.hasLocalVariableTable()) {
      return;
    }
    int bci = jfr.getBCI();
    // Get local variable table
    LocalVariableTableElement[] locals = m.getLocalVariableTable();
    // Get locals as StackValueCollection
    StackValueCollection coll = jfr.getLocals();
    root.removeAllChildren();
    // See which locals are live
    for (int i = 0; i < locals.length; i++) {
      LocalVariableTableElement local = locals[i];
      if (local.getStartBCI() <= bci && bci < local.getStartBCI() + local.getLength()) {
        // Valid; add it
        SimpleTreeNode node = null;
        Symbol name = null;
        try {
          name = m.getConstants().getSymbolAt(local.getNameCPIndex());
          if (name == null) {
            System.err.println("Null name at slot " +
                               local.getNameCPIndex() +
                               " for local variable at slot " +
                               local.getSlot());
            continue;
          }
        } catch (Exception e) {
          System.err.println("Unable to fetch name at slot " +
                             local.getNameCPIndex() +
                             " for local variable at slot " +
                             local.getSlot());
          e.printStackTrace();
          continue;
        }
        sun.jvm.hotspot.oops.NamedFieldIdentifier f =
          new sun.jvm.hotspot.oops.NamedFieldIdentifier(name.asString());
        Symbol descriptor = null;
        try {
          descriptor = m.getConstants().getSymbolAt(local.getDescriptorCPIndex());
        } catch (Exception e) {
          System.err.println("Unable to fetch descriptor at slot " +
                             local.getDescriptorCPIndex() +
                             " for local variable " + f.getName() +
                             " at slot " + local.getSlot());
          e.printStackTrace();
          continue;
        }

        if (descriptor != null) {
          switch (descriptor.getByteAt(0)) {
          case 'F': {
            node = new sun.jvm.hotspot.ui.tree.FloatTreeNodeAdapter(coll.floatAt(local.getSlot()), f, true);
            break;
          }
          case 'D': {
            node = new sun.jvm.hotspot.ui.tree.DoubleTreeNodeAdapter(coll.doubleAt(local.getSlot()), f, true);
            break;
          }
          case 'C': {
            node = new sun.jvm.hotspot.ui.tree.CharTreeNodeAdapter((char) coll.intAt(local.getSlot()), f, true);
            break;
          }
          case 'B':
          case 'S':
          case 'I': {
            node = new sun.jvm.hotspot.ui.tree.LongTreeNodeAdapter(coll.intAt(local.getSlot()), f, true);
            break;
          }
          case 'Z': {
            node = new sun.jvm.hotspot.ui.tree.BooleanTreeNodeAdapter(
              ((coll.intAt(local.getSlot()) != 0) ? true : false), f, true
            );
            break;
          }
          case 'J': {
            node = new sun.jvm.hotspot.ui.tree.LongTreeNodeAdapter(coll.longAt(local.getSlot()), f, true);
            break;
          }
          default: {
            try {
              node = new sun.jvm.hotspot.ui.tree.OopTreeNodeAdapter(
                VM.getVM().getObjectHeap().newOop(coll.oopHandleAt(local.getSlot())), f, true
              );
            } catch (AddressException e) {
              node = new sun.jvm.hotspot.ui.tree.FieldTreeNodeAdapter(f, true) {
                  public int getChildCount()                       { return 0;     }
                  public SimpleTreeNode getChild(int i)            { return null;  }
                  public boolean isLeaf()                          { return false; }
                  public int getIndexOfChild(SimpleTreeNode child) { return 0;     }
                  public String getValue() {
                    return "<Bad oop>";
                  }
                };
            }
            break;
          }
          }
          if (node != null) {
            root.addChild(node);
          }
        }
      }
    }

    model.fireTreeStructureChanged();
  }

  /** Update the contents of this VariablePanel from the given "this"
      pointer of the given type */
  public void update(Address thisAddr, Type type) {
    // Collect fields
    CCollector coll = new CCollector();
    type.iterateObject(thisAddr, coll);
    update(coll);
  }

  private void update(CCollector coll) {
    root.removeAllChildren();
    for (int i = 0; i < coll.getNumChildren(); i++) {
      root.addChild(coll.getChild(i));
    }
    model.fireTreeStructureChanged();
  }

  static class CCollector extends DefaultObjectVisitor {
    private java.util.List children;

    public CCollector() {
      children = new ArrayList();
    }

    public int getNumChildren() {
      return children.size();
    }

    public SimpleTreeNode getChild(int i) {
      return (SimpleTreeNode) children.get(i);
    }

    public void doBit(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, long val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.LongTreeNodeAdapter(val, f, true));
    }
    public void doInt(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, long val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.LongTreeNodeAdapter(val, f, true));
    }
    public void doEnum(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, long val, String enumName) {
      children.add(new sun.jvm.hotspot.bugspot.tree.EnumTreeNodeAdapter(enumName, val, f, true));
    }
    public void doFloat(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, float val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.FloatTreeNodeAdapter(val, f, true));
    }
    public void doDouble(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, double val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.DoubleTreeNodeAdapter(val, f, true));
    }
    public void doPointer(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, Address val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.AddressTreeNodeAdapter(val, f, true));
    }
    public void doArray(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, Address val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.AddressTreeNodeAdapter(val, f, true));
    }
    public void doRef(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, Address val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.AddressTreeNodeAdapter(val, f, true));
    }
    public void doCompound(sun.jvm.hotspot.debugger.cdbg.FieldIdentifier f, Address val) {
      children.add(new sun.jvm.hotspot.bugspot.tree.ObjectTreeNodeAdapter(val, f, true));
    }
  }
}
