/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.ui.*;

/** This panel contains a ListBox with all of the stack frames in a
    given thread. When a given entry is selected, an event is
    fired. */

public class StackTracePanel extends JPanel {
  public interface Listener {
    public void frameChanged(CFrame fr, JavaVFrame jfr);
  }

  class Model extends AbstractListModel implements ComboBoxModel {
    private Object selectedItem;
    public Object getElementAt(int index) {
      if (trace == null) return null;
      return trace.get(index);
    }
    public int getSize() {
      if (trace == null) return 0;
      return trace.size();
    }
    public Object getSelectedItem() {
      return selectedItem;
    }
    public void setSelectedItem(Object item) {
      selectedItem = item;
    }
    public void dataChanged() {
      fireContentsChanged(this, 0, trace.size());
    }
  }

  private java.util.List trace;
  private Model model;
  private JComboBox list;
  private java.util.List listeners;

  public StackTracePanel() {
    super();

    model = new Model();

    // Build user interface
    setLayout(new BorderLayout());
    setBorder(GraphicsUtilities.newBorder(5));
    list = new JComboBox(model);
    list.setPrototypeDisplayValue("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZ");
    add(list, BorderLayout.CENTER);

    // Add selection listener
    list.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            fireFrameChanged();
          }
        }
      });
  }

  /** Takes a List of StackTraceEntry objects */
  public void setTrace(java.util.List trace) {
    this.trace = trace;
    model.dataChanged();
    list.setSelectedIndex(0);
    fireFrameChanged();
  }

  public void addListener(Listener listener) {
    if (listeners == null) {
      listeners = new ArrayList();
    }
    listeners.add(listener);
  }

  protected void fireFrameChanged() {
    if (listeners != null) {
      StackTraceEntry entry = (StackTraceEntry) trace.get(list.getSelectedIndex());
      for (Iterator iter = listeners.iterator(); iter.hasNext(); ) {
        ((Listener) iter.next()).frameChanged(entry.getCFrame(), entry.getJavaFrame());
      }
    }
  }
}
