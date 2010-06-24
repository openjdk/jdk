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
import javax.swing.table.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.ui.*;

// NOTE: this class was not placed in sun.jvm.hotspot.ui to prevent
// mixing components designed for C and C++ debugging with the ones
// that work with the core serviceability agent functionality (which
// does not require that the CDebugger interface be implemented).

/** The ThreadListPanel is used for C and C++ debugging and can
    visualize all threads in the target process. The caller passes in
    a CDebugger attached to the target process and can request that
    JavaThreads' associations with these underlying threads be
    displayed; this option is only valid when attached to a HotSpot
    JVM and when the {@link sun.jvm.hotspot.runtime.VM} has been
    initialized. */

public class ThreadListPanel extends JPanel {
  /** Listener which can be added to receive "Set Focus" events */
  public static interface Listener {
    /** ThreadProxy will always be provided; JavaThread will only be
        present if displayJavaThreads was specified in the constructor
        for the panel and the thread was a JavaThread. */
    public void setFocus(ThreadProxy thread, JavaThread jthread);
  }

  static class ThreadInfo {
    private ThreadProxy thread;
    // Distinguish between PC == null and no top frame
    private boolean     gotPC;
    private Address     pc;
    private String      location;
    private JavaThread  javaThread;
    private String      javaThreadName;

    public ThreadInfo(ThreadProxy thread, CDebugger dbg, JavaThread jthread) {
      this.thread = thread;
      this.location = "<unknown>";
      CFrame fr = dbg.topFrameForThread(thread);
      if (fr != null) {
        gotPC = true;
        pc = fr.pc();
        PCFinder.Info info = PCFinder.findPC(pc, fr.loadObjectForPC(), dbg);
        if (info.getName() != null) {
          location = info.getName();
          if (info.getConfidence() == PCFinder.LOW_CONFIDENCE) {
            location = location + " (?)";
          }
          if (info.getOffset() < 0) {
            location = location + " + 0x" + Long.toHexString(info.getOffset());
          }
        }
      }
      if (jthread != null) {
        javaThread = jthread;
        javaThreadName = jthread.getThreadName();
      }
    }

    public ThreadProxy getThread()    { return thread;       }
    public boolean     hasPC()        { return gotPC;        }
    public Address     getPC()        { return pc;           }
    public String      getLocation()  { return location;     }
    public boolean     isJavaThread() { return (javaThread != null); }
    public JavaThread  getJavaThread() { return javaThread; }
    public String      getJavaThreadName() { return javaThreadName; }
  }

  // List<ThreadInfo>
  private java.util.List threadList;
  private JTable table;
  private AbstractTableModel dataModel;
  // List<Listener>
  private java.util.List listeners;

  /** Takes a CDebugger from which the thread list is queried.
      displayJavaThreads must only be set to true if the debugger is
      attached to a HotSpot JVM and if the VM has already been
      initialized. */
  public ThreadListPanel(CDebugger dbg, final boolean displayJavaThreads) {
    super();

    Map threadToJavaThreadMap = null;
    if (displayJavaThreads) {
      // Collect Java threads from virtual machine and insert them in
      // table for later querying
      threadToJavaThreadMap = new HashMap();
      Threads threads = VM.getVM().getThreads();
      for (JavaThread thr = threads.first(); thr != null; thr = thr.next()) {
        threadToJavaThreadMap.put(thr.getThreadProxy(), thr);
      }
    }

    java.util.List/*<ThreadProxy>*/ threads = dbg.getThreadList();
    threadList = new ArrayList(threads.size());
    for (Iterator iter = threads.iterator(); iter.hasNext(); ) {
      ThreadProxy thr = (ThreadProxy) iter.next();
      JavaThread jthr = null;
      if (displayJavaThreads) {
        jthr = (JavaThread) threadToJavaThreadMap.get(thr);
      }
      threadList.add(new ThreadInfo(thr, dbg, jthr));
    }

    // Thread ID, current PC, current symbol, Java Thread, [Java thread name]
    dataModel = new AbstractTableModel() {
        public int getColumnCount() { return (displayJavaThreads ? 5 : 3); }
        public int getRowCount()    { return threadList.size(); }
        public String getColumnName(int col) {
          switch (col) {
          case 0:
            return "Thread ID";
          case 1:
            return "PC";
          case 2:
            return "Location";
          case 3:
            return "Java?";
          case 4:
            return "Java Thread Name";
          default:
            throw new RuntimeException("Index " + col + " out of bounds");
          }
        }
        public Object getValueAt(int row, int col) {
          ThreadInfo info = (ThreadInfo) threadList.get(row);

          switch (col) {
          case 0:
            return info.getThread();
          case 1:
            {
              if (info.hasPC()) {
                return info.getPC();
              }
              return "<no frames on stack>";
            }
          case 2:
            return info.getLocation();
          case 3:
            if (info.isJavaThread()) {
              return "Yes";
            } else {
              return "";
            }
          case 4:
            if (info.isJavaThread()) {
              return info.getJavaThreadName();
            } else {
              return "";
            }
          default:
            throw new RuntimeException("Index (" + col + ", " + row + ") out of bounds");
          }
        }
      };

    // Build user interface
    setLayout(new BorderLayout());
    table = new JTable(dataModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JTableHeader header = table.getTableHeader();
    header.setReorderingAllowed(false);
    table.setRowSelectionAllowed(true);
    table.setColumnSelectionAllowed(false);
    JScrollPane scrollPane = new JScrollPane(table);
    add(scrollPane, BorderLayout.CENTER);
    if (threadList.size() > 0) {
      table.setRowSelectionInterval(0, 0);
    }

    JButton button = new JButton("Set Focus");
    button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int i = table.getSelectedRow();
          if (i < 0) {
            return;
          }
          ThreadInfo info = (ThreadInfo) threadList.get(i);
          for (Iterator iter = listeners.iterator(); iter.hasNext(); ) {
            ((Listener) iter.next()).setFocus(info.getThread(), info.getJavaThread());
          }
        }
      });
    JPanel focusPanel = new JPanel();
    focusPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    focusPanel.setLayout(new BoxLayout(focusPanel, BoxLayout.Y_AXIS));
    focusPanel.add(Box.createGlue());
    focusPanel.add(button);
    focusPanel.add(Box.createGlue());
    add(focusPanel, BorderLayout.EAST);

    // FIXME: make listener model for the debugger so if the user
    // specifies a mapfile for or path to a given DSO later we can
    // update our state
  }

  public void addListener(Listener l) {
    if (listeners == null) {
      listeners = new ArrayList();
    }
    listeners.add(l);
  }
}
