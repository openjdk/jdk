/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.posix.*;
import sun.jvm.hotspot.debugger.win32.*;
import sun.jvm.hotspot.livejvm.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.ui.*;
import sun.jvm.hotspot.utilities.*;

/** The BugSpot component. This is embeddable in an application by
    virtue of its being a JComponent. It (currently) requires the use
    of a menu bar which can be fetched via getMenuBar(). This is
    intended ultimately to replace HSDB. */

public class BugSpot extends JPanel {
  public BugSpot() {
    super();
    Runtime.getRuntime().addShutdownHook(new java.lang.Thread() {
        public void run() {
          detachDebugger();
        }
      });
  }

  /** Turn on or off MDI (Multiple Document Interface) mode. When MDI
      is enabled, the BugSpot component contains a JDesktopPane and all
      windows are JInternalFrames. When disabled, only the menu bar is
      relevant. */
  public void setMDIMode(boolean onOrOff) {
    mdiMode = onOrOff;
  }

  /** Indicates whether MDI mode is enabled. */
  public boolean getMDIMode() {
    return mdiMode;
  }

  /** Build user interface widgets. This must be called before adding
      the BugSpot component to its parent. */
  public void build() {
    setLayout(new BorderLayout());

    menuBar = new JMenuBar();

    attachMenuItems = new java.util.ArrayList();
    detachMenuItems = new java.util.ArrayList();
    debugMenuItems  = new java.util.ArrayList();
    suspendDebugMenuItems = new java.util.ArrayList();
    resumeDebugMenuItems = new java.util.ArrayList();

    //
    // File menu
    //

    JMenu menu = createMenu("File", 'F', 0);
    JMenuItem item;
    item = createMenuItem("Open source file...",
                          new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                openSourceFile();
                              }
                            },
                          KeyEvent.VK_O, InputEvent.CTRL_MASK,
                          'O', 0);
    menu.add(item);
    detachMenuItems.add(item);

    menu.addSeparator();

    item = createMenuItem("Attach to process...",
                          new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                showAttachDialog();
                              }
                            },
                          'A', 0);
    menu.add(item);
    attachMenuItems.add(item);

    item = createMenuItem("Detach",
                          new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                detach();
                              }
                            },
                          'D', 0);
    menu.add(item);
    detachMenuItems.add(item);

    // Disable detach menu items at first
    setMenuItemsEnabled(detachMenuItems, false);

    menu.addSeparator();

    menu.add(createMenuItem("Exit",
                            new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                  detach();
                                  System.exit(0);
                                }
                              },
                            'x', 1));

    menuBar.add(menu);

    //
    // Debug menu
    //

    debugMenu = createMenu("Debug", 'D', 0);
    item = createMenuItem("Go",
                          new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                if (!attached) return;
                                if (!isSuspended()) return;
                                resume();
                              }
                            },
                          KeyEvent.VK_F5, 0,
                          'G', 0);
    debugMenu.add(item);
    resumeDebugMenuItems.add(item);

    item = createMenuItem("Break",
                          new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                if (!attached) {
                                  System.err.println("Not attached");
                                  return;
                                }
                                if (isSuspended()) {
                                  System.err.println("Already suspended");
                                  return;
                                }
                                suspend();
                              }
                            },
                          'B', 0);
    debugMenu.add(item);
    suspendDebugMenuItems.add(item);

    debugMenu.addSeparator();

    item = createMenuItem("Threads...",
                          new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                showThreadsDialog();
                              }
                            },
                          'T', 0);
    debugMenu.add(item);
    debugMenuItems.add(item);
    // FIXME: belongs under "View -> Debug Windows"
    item = createMenuItem("Memory",
                          new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                showMemoryDialog();
                              }
                            },
                          'M', 0);
    debugMenu.add(item);
    debugMenuItems.add(item);

    debugMenu.setEnabled(false);
    menuBar.add(debugMenu);

    if (mdiMode) {
      desktop = new JDesktopPane();
      add(desktop, BorderLayout.CENTER);
    }

    fixedWidthFont = GraphicsUtilities.lookupFont("Courier");

    debugEventTimer = new javax.swing.Timer(100, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          pollForDebugEvent();
        }
      });
  }

  public JMenuBar getMenuBar() {
    return menuBar;
  }

  public void showAttachDialog() {
    setMenuItemsEnabled(attachMenuItems, false);
    final FrameWrapper attachDialog = newFrame("Attach to process");
    attachDialog.getContentPane().setLayout(new BorderLayout());
    attachDialog.setClosable(true);
    attachDialog.setResizable(true);

    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.setBorder(GraphicsUtilities.newBorder(5));
    attachDialog.setBackground(panel.getBackground());

    JPanel listPanel = new JPanel();
    listPanel.setLayout(new BorderLayout());
    final ProcessListPanel plist = new ProcessListPanel(getLocalDebugger());
    panel.add(plist, BorderLayout.CENTER);
    JCheckBox check = new JCheckBox("Update list continuously");
    check.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            plist.start();
          } else {
            plist.stop();
          }
        }
      });
    listPanel.add(plist, BorderLayout.CENTER);
    listPanel.add(check, BorderLayout.SOUTH);
    panel.add(listPanel, BorderLayout.CENTER);
    attachDialog.getContentPane().add(panel, BorderLayout.CENTER);
    attachDialog.setClosingActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          plist.stop();
          setMenuItemsEnabled(attachMenuItems, true);
        }
      });

    ActionListener attacher = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          plist.stop();
          attachDialog.setVisible(false);
          removeFrame(attachDialog);
          ProcessInfo info = plist.getSelectedProcess();
          if (info != null) {
            attach(info.getPid());
          }
        }
      };

    Box hbox = Box.createHorizontalBox();
    hbox.add(Box.createGlue());
    JButton button = new JButton("OK");
    button.addActionListener(attacher);
    hbox.add(button);
    hbox.add(Box.createHorizontalStrut(20));
    button = new JButton("Cancel");
    button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          plist.stop();
          attachDialog.setVisible(false);
          removeFrame(attachDialog);
          setMenuItemsEnabled(attachMenuItems, true);
        }
      });
    hbox.add(button);
    hbox.add(Box.createGlue());
    panel = new JPanel();
    panel.setBorder(GraphicsUtilities.newBorder(5));
    panel.add(hbox);

    attachDialog.getContentPane().add(panel, BorderLayout.SOUTH);

    addFrame(attachDialog);
    attachDialog.pack();
    attachDialog.setSize(400, 300);
    GraphicsUtilities.centerInContainer(attachDialog.getComponent(),
                                        getParentDimension(attachDialog.getComponent()));
    attachDialog.setVisible(true);
  }

  public void showThreadsDialog() {
    final FrameWrapper threadsDialog = newFrame("Threads");
    threadsDialog.getContentPane().setLayout(new BorderLayout());
    threadsDialog.setClosable(true);
    threadsDialog.setResizable(true);

    ThreadListPanel threads = new ThreadListPanel(getCDebugger(), getAgent().isJavaMode());
    threads.addListener(new ThreadListPanel.Listener() {
        public void setFocus(ThreadProxy thread, JavaThread jthread) {
          setCurrentThread(thread);
          // FIXME: print this to GUI, bring some windows to foreground
          System.err.println("Focus changed to thread " + thread);
        }
      });
    threads.setBorder(GraphicsUtilities.newBorder(5));
    threadsDialog.getContentPane().add(threads);
    addFrame(threadsDialog);
    threadsDialog.pack();
    GraphicsUtilities.reshapeToAspectRatio(threadsDialog.getComponent(),
                                           3.0f,
                                           0.9f,
                                           getParentDimension(threadsDialog.getComponent()));
    GraphicsUtilities.centerInContainer(threadsDialog.getComponent(),
                                        getParentDimension(threadsDialog.getComponent()));
    threadsDialog.setVisible(true);
  }

  public void showMemoryDialog() {
    final FrameWrapper memoryDialog = newFrame("Memory");
    memoryDialog.getContentPane().setLayout(new BorderLayout());
    memoryDialog.setClosable(true);
    memoryDialog.setResizable(true);

    memoryDialog.getContentPane().add(new MemoryViewer(getDebugger(),
                                                       (getDebugger().getMachineDescription().getAddressSize() == 8)),
                                      BorderLayout.CENTER);
    addFrame(memoryDialog);
    memoryDialog.pack();
    GraphicsUtilities.reshapeToAspectRatio(memoryDialog.getComponent(),
                                           1.0f,
                                           0.7f,
                                           getParentDimension(memoryDialog.getComponent()));
    GraphicsUtilities.centerInContainer(memoryDialog.getComponent(),
                                        getParentDimension(memoryDialog.getComponent()));
    memoryDialog.setVisible(true);
  }

  /** Changes the editor factory this debugger uses to display source
      code. Specified factory may be null, in which case the default
      factory is used. */
  public void setEditorFactory(EditorFactory fact) {
    if (fact != null) {
      editorFact = fact;
    } else {
      editorFact = new DefaultEditorFactory();
    }
  }

  //----------------------------------------------------------------------
  // Internals only below this point
  //

  private WorkerThread    workerThread;
  private boolean         mdiMode;
  private JVMDebugger     localDebugger;
  private BugSpotAgent    agent = new BugSpotAgent();
  private JMenuBar        menuBar;
  /** List <JMenuItem> */
  private java.util.List  attachMenuItems;
  private java.util.List  detachMenuItems;
  private java.util.List  debugMenuItems;
  private java.util.List  suspendDebugMenuItems;
  private java.util.List  resumeDebugMenuItems;
  private FrameWrapper    stackFrame;
  private VariablePanel   localsPanel;
  private StackTracePanel stackTracePanel;
  private FrameWrapper    registerFrame;
  private RegisterPanel   registerPanel;
  // Used for mixed-language stack traces
  private Map             threadToJavaThreadMap;

  private JMenu debugMenu;

  // MDI mode only: desktop pane
  private JDesktopPane desktop;

  // Attach/detach state
  private boolean attached;

  // Suspension (combined Java/C++) state
  private boolean suspended;

  // Fixed-width font
  private Font fixedWidthFont;

  // Breakpoint setting
  // Maps Strings to List/*<LineNumberInfo>*/
  private Map sourceFileToLineNumberInfoMap;
  // Maps Strings (file names) to Sets of Integers (line numbers)
  private Map fileToBreakpointMap;

  // Debug events
  private javax.swing.Timer debugEventTimer;

  // Java debug events
  private boolean javaEventPending;

  static class BreakpointResult {
    private boolean success;
    private boolean set;
    private int lineNo;
    private String why;

    /** For positive results */
    BreakpointResult(boolean success, boolean set, int lineNo) {
      this(success, set, lineNo, null);
    }

    /** For negative results */
    BreakpointResult(boolean success, boolean set, int lineNo, String why) {
      this.success = success;
      this.set = set;
      this.lineNo = lineNo;
      this.why = why;
    }

    public boolean succeeded() {
      return success;
    }

    public boolean set() {
      return set;
    }

    /** Line at which the breakpoint was actually set; only valid if
        succeeded() returns true */
    public int getLine() {
      return lineNo;
    }

    public String getWhy() {
      return why;
    }
  }


  // Editors for source code. File name-to-Editor mapping.
  private Map editors;
  private EditorFactory editorFact = new DefaultEditorFactory();
  private EditorCommands editorComm = new EditorCommands() {
      public void windowClosed(Editor editor) {
        editors.remove(editor.getSourceFileName());
      }

      public void toggleBreakpointAtLine(Editor editor, int lineNumber) {
        // FIXME: handle "lazy" breakpoints where the source file has
        // been opened with some other mechanism (File -> Open) and we
        // don't have debug information pointing to that file yet
        // FIXME: NOT FINISHED

        BreakpointResult res =
          handleBreakpointToggle(editor, lineNumber);
        if (res.succeeded()) {
          if (res.set()) {
            editor.showBreakpointAtLine(res.getLine());
          } else {
            editor.clearBreakpointAtLine(res.getLine());
          }
        } else {
          String why = res.getWhy();
          if (why == null) {
            why = "";
          } else {
            why = ": " + why;
          }
          showMessageDialog("Unable to toggle breakpoint" + why,
                            "Unable to toggle breakpoint",
                            JOptionPane.WARNING_MESSAGE);
        }
      }
    };

  private void attach(final int pid) {
    try {
      getAgent().attach(pid);
      setMenuItemsEnabled(detachMenuItems, true);
      setMenuItemsEnabled(suspendDebugMenuItems, false);
      setMenuItemsEnabled(resumeDebugMenuItems, true);
      debugMenu.setEnabled(true);
      attached = true;
      suspended = true;

      if (getAgent().isJavaMode()) {
        System.err.println("Java HotSpot(TM) virtual machine detected.");
      } else {
        System.err.println("(No Java(TM) virtual machine detected)");
      }

      // Set up editor map
      editors = new HashMap();

      // Initialize breakpoints
      fileToBreakpointMap = new HashMap();

      // Create combined stack trace and local variable panel
      JPanel framePanel = new JPanel();
      framePanel.setLayout(new BorderLayout());
      framePanel.setBorder(GraphicsUtilities.newBorder(5));
      localsPanel = new VariablePanel();
      JTabbedPane tab = new JTabbedPane();
      tab.addTab("Locals", localsPanel);
      tab.setTabPlacement(JTabbedPane.BOTTOM);
      framePanel.add(tab, BorderLayout.CENTER);
      JPanel stackPanel = new JPanel();
      stackPanel.setLayout(new BoxLayout(stackPanel, BoxLayout.X_AXIS));
      stackPanel.add(new JLabel("Context:"));
      stackPanel.add(Box.createHorizontalStrut(5));
      stackTracePanel = new StackTracePanel();
      stackTracePanel.addListener(new StackTracePanel.Listener() {
          public void frameChanged(CFrame fr, JavaVFrame jfr) {
            setCurrentFrame(fr, jfr);
          }
        });
      stackPanel.add(stackTracePanel);
      framePanel.add(stackPanel, BorderLayout.NORTH);
      stackFrame = newFrame("Stack");
      stackFrame.getContentPane().setLayout(new BorderLayout());
      stackFrame.getContentPane().add(framePanel, BorderLayout.CENTER);
      stackFrame.setResizable(true);
      stackFrame.setClosable(false);
      addFrame(stackFrame);
      stackFrame.setSize(400, 200);
      GraphicsUtilities.moveToInContainer(stackFrame.getComponent(), 0.0f, 1.0f, 0, 20);
      stackFrame.setVisible(true);

      // Create register panel
      registerPanel = new RegisterPanel();
      registerPanel.setFont(fixedWidthFont);
      registerFrame = newFrame("Registers");
      registerFrame.getContentPane().setLayout(new BorderLayout());
      registerFrame.getContentPane().add(registerPanel, BorderLayout.CENTER);
      addFrame(registerFrame);
      registerFrame.setResizable(true);
      registerFrame.setClosable(false);
      registerFrame.setSize(225, 200);
      GraphicsUtilities.moveToInContainer(registerFrame.getComponent(),
                                          1.0f, 0.0f, 0, 0);
      registerFrame.setVisible(true);

      resetCurrentThread();
    } catch (DebuggerException e) {
      final String errMsg = formatMessage(e.getMessage(), 80);
      setMenuItemsEnabled(attachMenuItems, true);
      showMessageDialog("Unable to connect to process ID " + pid + ":\n\n" + errMsg,
                        "Unable to Connect",
                        JOptionPane.WARNING_MESSAGE);
      getAgent().detach();
    }
  }

  private synchronized void detachDebugger() {
    if (!attached) {
      return;
    }
    if (isSuspended()) {
      resume(); // Necessary for JVMDI resumption
    }
    getAgent().detach();
    // FIXME: clear out breakpoints (both Java and C/C++) from target
    // process
    sourceFileToLineNumberInfoMap = null;
    fileToBreakpointMap = null;
    threadToJavaThreadMap = null;
    editors = null;
    attached = false;
  }

  private synchronized void detach() {
    detachDebugger();
    setMenuItemsEnabled(attachMenuItems, true);
    setMenuItemsEnabled(detachMenuItems, false);
    debugMenu.setEnabled(false);
    if (mdiMode) {
      // FIXME: is this sufficient, or will I have to do anything else
      // to the components to kill them off? What about WorkerThreads?
      desktop.removeAll();
      desktop.invalidate();
      desktop.validate();
      desktop.repaint();
    }
    // FIXME: keep track of all windows and close them even in non-MDI
    // mode
    debugEventTimer.stop();
  }

  // Returns a Debugger for processes on the local machine. This is
  // only used to fetch the process list.
  private Debugger getLocalDebugger() {
    if (localDebugger == null) {
      String os  = PlatformInfo.getOS();
      String cpu = PlatformInfo.getCPU();

      if (os.equals("win32")) {
        if (!cpu.equals("x86")) {
          throw new DebuggerException("Unsupported CPU \"" + cpu + "\" for Windows");
        }

        localDebugger = new Win32DebuggerLocal(new MachineDescriptionIntelX86(), true);
      } else if (os.equals("linux")) {
        if (!cpu.equals("x86")) {
          throw new DebuggerException("Unsupported CPU \"" + cpu + "\" for Linux");
        }

        // FIXME: figure out how to specify path to debugger module
        throw new RuntimeException("FIXME: figure out how to specify path to debugger module");
        //        localDebugger = new PosixDebuggerLocal(new MachineDescriptionIntelX86(), true);
      } else {
        // FIXME: port to Solaris
        throw new DebuggerException("Unsupported OS \"" + os + "\"");
      }

      // FIXME: we require that the primitive type sizes be configured
      // in order to use basic functionality in class Address such as
      // the fetching of floating-point values. There are a lot of
      // assumptions in the current code that Java floats and doubles
      // are of equivalent size to C values. The configurability of the
      // primitive type sizes hasn't seemed necessary and in this kind
      // of debugging scenario (namely, debugging arbitrary C++
      // processes) it appears difficult to support that kind of
      // flexibility.
      localDebugger.configureJavaPrimitiveTypeSizes(1, 1, 2, 8, 4, 4, 8, 2);
    }

    return localDebugger;
  }

  private BugSpotAgent getAgent() {
    return agent;
  }

  private Debugger getDebugger() {
    return getAgent().getDebugger();
  }

  private CDebugger getCDebugger() {
    return getAgent().getCDebugger();
  }

  private void resetCurrentThread() {
    setCurrentThread((ThreadProxy) getCDebugger().getThreadList().get(0));
  }

  private void setCurrentThread(ThreadProxy t) {
    // Create stack trace
    // FIXME: add ability to intermix C/Java frames
    java.util.List trace = new ArrayList();
    CFrame fr = getCDebugger().topFrameForThread(t);
    while (fr != null) {
      trace.add(new StackTraceEntry(fr, getCDebugger()));
      try {
        fr = fr.sender();
      } catch (AddressException e) {
        e.printStackTrace();
        showMessageDialog("Error while walking stack; stack trace will be truncated\n(see console for details)",
                          "Error walking stack",
                          JOptionPane.WARNING_MESSAGE);
        fr = null;
      }
    }
    JavaThread jthread = javaThreadForProxy(t);
    if (jthread != null) {
      // Java mode, and we have a Java thread.
      // Find all Java frames on the stack. We currently do this in a
      // manner which involves minimal interaction between the Java
      // and C/C++ debugging systems: any C frame which has a PC in an
      // unknown location (i.e., not in any DSO) is assumed to be a
      // Java frame. We merge stack segments of unknown frames with
      // segments of Java frames beginning with native methods.
      java.util.List javaTrace = new ArrayList();
      VFrame vf = jthread.getLastJavaVFrameDbg();
      while (vf != null) {
        if (vf.isJavaFrame()) {
          javaTrace.add(new StackTraceEntry((JavaVFrame) vf));
          vf = vf.sender();
        }
      }
      // Merge stack traces
      java.util.List mergedTrace = new ArrayList();
      int c = 0;
      int j = 0;
      while (c < trace.size()) {
        StackTraceEntry entry = (StackTraceEntry) trace.get(c);
        if (entry.isUnknownCFrame()) {
          boolean gotJavaFrame = false;
          while (j < javaTrace.size()) {
            StackTraceEntry javaEntry = (StackTraceEntry) javaTrace.get(j);
            JavaVFrame jvf = javaEntry.getJavaFrame();
            Method m = jvf.getMethod();
            if (!m.isNative() || !gotJavaFrame) {
              gotJavaFrame = true;
              mergedTrace.add(javaEntry);
              ++j;
            } else {
              break; // Reached native method; have intervening C frames
            }
          }
          if (gotJavaFrame) {
            // Skip this sequence of unknown frames, as we've
            // successfully identified it as Java frames
            while (c < trace.size() && entry.isUnknownCFrame()) {
              ++c;
              if (c < trace.size()) {
                entry = (StackTraceEntry) trace.get(c);
              }
            }
            continue;
          }
        }
        // If we get here, we either have an unknown frame we didn't
        // know how to categorize or we have a known C frame. Add it
        // to the trace.
        mergedTrace.add(entry);
        ++c;
      }
      trace = mergedTrace;
    }
    stackTracePanel.setTrace(trace);

    registerPanel.update(t);
  }

  private void setCurrentFrame(CFrame fr, JavaVFrame jfr) {
    localsPanel.clear();

    if (fr != null) {
      localsPanel.update(fr);

      // FIXME: load source file if we can find it, otherwise display disassembly
      LoadObject lo = getCDebugger().loadObjectContainingPC(fr.pc());
      if (lo != null) {
        CDebugInfoDataBase db = lo.getDebugInfoDataBase();
        if (db != null) {
          LineNumberInfo info = db.lineNumberForPC(fr.pc());
          if (info != null) {
            System.err.println("PC " + fr.pc() + ": Source file \"" +
                               info.getSourceFileName() +
                               "\", line number " +
                               info.getLineNumber() +
                               ", PC range [" +
                               info.getStartPC() +
                               ", " +
                               info.getEndPC() +
                               ")");
            // OK, here we go...
            showLineNumber(null, info.getSourceFileName(), info.getLineNumber());
          } else {
            System.err.println("(No line number information for PC " + fr.pc() + ")");
            // Dump line number information for database
            db.iterate(new LineNumberVisitor() {
                public void doLineNumber(LineNumberInfo info) {
                  System.err.println("  Source file \"" +
                                     info.getSourceFileName() +
                                     "\", line number " +
                                     info.getLineNumber() +
                                     ", PC range [" +
                                     info.getStartPC() +
                                     ", " +
                                     info.getEndPC() +
                                     ")");
                }
              });
          }
        }
      }
    } else {
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(jfr != null, "Must have either C or Java frame");
      }
      localsPanel.update(jfr);
      // See whether we can locate source file and line number
      // FIXME: infer pathmap entries from user's locating of this
      // source file
      // FIXME: figure out what to do for native methods. Possible to
      // go to line number for the native method declaration?
      Method m = jfr.getMethod();
      Symbol sfn = ((InstanceKlass) m.getMethodHolder()).getSourceFileName();
      if (sfn != null) {
        int bci = jfr.getBCI();
        int lineNo = m.getLineNumberFromBCI(bci);
        if (lineNo >= 0) {
          // FIXME: show disassembly otherwise
          showLineNumber(packageName(m.getMethodHolder().getName().asString()),
                         sfn.asString(), lineNo);
        }
      }
    }
  }

  private String packageName(String str) {
    int idx = str.lastIndexOf('/');
    if (idx < 0) {
      return "";
    }
    return str.substring(0, idx).replace('/', '.');
  }

  private JavaThread javaThreadForProxy(ThreadProxy t) {
    if (!getAgent().isJavaMode()) {
      return null;
    }
    if (threadToJavaThreadMap == null) {
      threadToJavaThreadMap = new HashMap();
      Threads threads = VM.getVM().getThreads();
      for (JavaThread thr = threads.first(); thr != null; thr = thr.next()) {
        threadToJavaThreadMap.put(thr.getThreadProxy(), thr);
      }
    }
    return (JavaThread) threadToJavaThreadMap.get(t);
  }

  private static JMenu createMenu(String name, char mnemonic, int mnemonicPos) {
    JMenu menu = new JMenu(name);
    menu.setMnemonic(mnemonic);
    menu.setDisplayedMnemonicIndex(mnemonicPos);
    return menu;
  }

  private static JMenuItem createMenuItem(String name, ActionListener l) {
    JMenuItem item = new JMenuItem(name);
    item.addActionListener(l);
    return item;
  }

  private static JMenuItem createMenuItemInternal(String name, ActionListener l, int accelerator, int modifiers) {
    JMenuItem item = createMenuItem(name, l);
    item.setAccelerator(KeyStroke.getKeyStroke(accelerator, modifiers));
    return item;
  }

  private static JMenuItem createMenuItem(String name, ActionListener l, int accelerator) {
    return createMenuItemInternal(name, l, accelerator, 0);
  }

  private static JMenuItem createMenuItem(String name, ActionListener l, char mnemonic, int mnemonicPos) {
    JMenuItem item = createMenuItem(name, l);
    item.setMnemonic(mnemonic);
    item.setDisplayedMnemonicIndex(mnemonicPos);
    return item;
  }

  private static JMenuItem createMenuItem(String name,
                                          ActionListener l,
                                          int accelerator,
                                          int acceleratorMods,
                                          char mnemonic,
                                          int mnemonicPos) {
    JMenuItem item = createMenuItemInternal(name, l, accelerator, acceleratorMods);
    item.setMnemonic(mnemonic);
    item.setDisplayedMnemonicIndex(mnemonicPos);
    return item;
  }

  /** Punctuates the given string with \n's where necessary to not
      exceed the given number of characters per line. Strips
      extraneous whitespace. */
  private static String formatMessage(String message, int charsPerLine) {
    StringBuffer buf = new StringBuffer(message.length());
    StringTokenizer tokenizer = new StringTokenizer(message);
    int curLineLength = 0;
    while (tokenizer.hasMoreTokens()) {
      String tok = tokenizer.nextToken();
      if (curLineLength + tok.length() > charsPerLine) {
        buf.append('\n');
        curLineLength = 0;
      } else {
        if (curLineLength != 0) {
          buf.append(' ');
          ++curLineLength;
        }
      }
      buf.append(tok);
      curLineLength += tok.length();
    }
    return buf.toString();
  }

  private void setMenuItemsEnabled(java.util.List items, boolean enabled) {
    for (Iterator iter = items.iterator(); iter.hasNext(); ) {
      ((JMenuItem) iter.next()).setEnabled(enabled);
    }
  }

  private void showMessageDialog(final String message, final String title, final int jOptionPaneKind) {
    SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (mdiMode) {
            JOptionPane.showInternalMessageDialog(desktop, message, title, jOptionPaneKind);
          } else {
            JOptionPane.showMessageDialog(null, message, title, jOptionPaneKind);
          }
        }
      });
  }

  private FrameWrapper newFrame(String title) {
    if (mdiMode) {
      return new JInternalFrameWrapper(new JInternalFrame(title));
    } else {
      return new JFrameWrapper(new JFrame(title));
    }
  }

  private void addFrame(FrameWrapper frame) {
    if (mdiMode) {
      desktop.add(frame.getComponent());
    }
  }

  private void removeFrame(FrameWrapper frame) {
    if (mdiMode) {
      desktop.remove(frame.getComponent());
      desktop.invalidate();
      desktop.validate();
      desktop.repaint();
    }
    // FIXME: do something when not in MDI mode
  }

  private Dimension getParentDimension(Component c) {
    if (mdiMode) {
      return desktop.getSize();
    } else {
      return Toolkit.getDefaultToolkit().getScreenSize();
    }
  }

  // Default editor implementation
  class DefaultEditor implements Editor {
    private DefaultEditorFactory factory;
    private FrameWrapper    editorFrame;
    private String          filename;
    private SourceCodePanel code;
    private boolean         shown;
    private Object          userData;

    public DefaultEditor(DefaultEditorFactory fact, String filename, final EditorCommands comm) {
      this.filename = filename;
      this.factory = fact;
      editorFrame = newFrame(filename);
      code = new SourceCodePanel();
      // FIXME: when font changes, change font in editors as well
      code.setFont(fixedWidthFont);
      editorFrame.getContentPane().add(code);
      editorFrame.setClosable(true);
      editorFrame.setResizable(true);
      editorFrame.setClosingActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            comm.windowClosed(DefaultEditor.this);
            removeFrame(editorFrame);
            editorFrame.dispose();
            factory.editorClosed(DefaultEditor.this);
          }
        });
      editorFrame.setActivatedActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            factory.makeEditorCurrent(DefaultEditor.this);
            code.requestFocus();
          }
        });
      code.setEditorCommands(comm, this);
    }

    public boolean openFile()                        { return code.openFile(filename);     }
    public String  getSourceFileName()               { return filename;                    }
    public int     getCurrentLineNumber()            { return code.getCurrentLineNumber(); }
    public void showLineNumber(int lineNo) {
      if (!shown) {
        addFrame(editorFrame);
        GraphicsUtilities.reshapeToAspectRatio(editorFrame.getComponent(),
                                               1.0f,
                                               0.85f,
                                               getParentDimension(editorFrame.getComponent()));
        editorFrame.setVisible(true);
        shown = true;
      }
      code.showLineNumber(lineNo);
      editorFrame.toFront();
    }
    public void    highlightLineNumber(int lineNo)   { code.highlightLineNumber(lineNo);        }
    public void    showBreakpointAtLine(int lineNo)  { code.showBreakpointAtLine(lineNo);       }
    public boolean hasBreakpointAtLine(int lineNo)   { return code.hasBreakpointAtLine(lineNo); }
    public void    clearBreakpointAtLine(int lineNo) { code.clearBreakpointAtLine(lineNo);      }
    public void    clearBreakpoints()                { code.clearBreakpoints();                 }
    public void    setUserData(Object o)             { userData = o;                            }
    public Object  getUserData()                     { return userData;                         }
    public void    toFront()                         { editorFrame.toFront();
                                                       factory.makeEditorCurrent(this);         }
  }

  class DefaultEditorFactory implements EditorFactory {
    private LinkedList/*<Editor>*/ editors = new LinkedList();

    public Editor openFile(String filename, EditorCommands commands) {
      DefaultEditor editor = new DefaultEditor(this, filename, editorComm);
      if (!editor.openFile()) {
        return null;
      }
      return editor;
    }

    public Editor getCurrentEditor() {
      if (editors.isEmpty()) {
        return null;
      }
      return (Editor) editors.getFirst();
    }

    void editorClosed(Editor editor) {
      editors.remove(editor);
    }

    void makeEditorCurrent(Editor editor) {
      editors.remove(editor);
      editors.addFirst(editor);
    }
  }

  // Helper class for loading .java files; show only those with
  // correct file name which are also in the correct package
  static class JavaFileFilter extends javax.swing.filechooser.FileFilter {
    private String packageName;
    private String fileName;

    JavaFileFilter(String packageName, String fileName) {
      this.packageName = packageName;
      this.fileName = fileName;
    }

    public boolean accept(File f) {
      if (f.isDirectory()) {
        return true;
      }
      // This rejects most files
      if (!f.getName().equals(fileName)) {
        return false;
      }
      // Ensure selected file is in the correct package
      PackageScanner scanner = new PackageScanner();
      String pkg = scanner.scan(f);
      if (!pkg.equals(packageName)) {
        return false;
      }
      return true;
    }

    public String getDescription() { return "Java source files"; }
  }

  // Auxiliary information used only for Java source files
  static class JavaUserData {
    private String packageName; // External format
    private String sourceFileName;

    /** Source file name is equivalent to that found in the .java
        file; i.e., not a full path */
    JavaUserData(String packageName, String sourceFileName) {
      this.packageName = packageName;
      this.sourceFileName = sourceFileName;
    }

    String packageName()    { return packageName; }
    String sourceFileName() { return sourceFileName; }
  }

  // Opens a source file. This makes it available for the setting of
  // lazy breakpoints.
  private void openSourceFile() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Open source code file");
    chooser.setMultiSelectionEnabled(false);
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File chosen = chooser.getSelectedFile();
    if (chosen == null) {
      return;
    }

    // See whether we have a Java source file. If so, derive a package
    // name for it.
    String path = chosen.getPath();
    String name = null;
    JavaUserData data = null;
    if (path.endsWith(".java")) {
      PackageScanner scanner = new PackageScanner();
      String pkg = scanner.scan(chosen);
      // Now knowing both the package name and file name, we can put
      // this in the editor map and use it for setting breakpoints
      // later
      String fileName = chosen.getName();
      name = pkg + "." + fileName;
      data = new JavaUserData(pkg, fileName);
    } else {
      // FIXME: need pathmap mechanism
      name = path;
    }
    Editor editor = (Editor) editors.get(name);
    if (editor == null) {
      editor = editorFact.openFile(path, editorComm);
      if (editor == null) {
        showMessageDialog("Unable to open file \"" + path + "\" -- unexpected error.",
                          "Unable to open file",
                          JOptionPane.WARNING_MESSAGE);
        return;
      }
      editors.put(name, editor);
      if (data != null) {
        editor.setUserData(data);
      }
    } else {
      editor.toFront();
    }
    editor.showLineNumber(1);
    // Show breakpoints as well if we have any for this file
    Set set = (Set) fileToBreakpointMap.get(editor.getSourceFileName());
    if (set != null) {
      for (Iterator iter = set.iterator(); iter.hasNext(); ) {
        editor.showBreakpointAtLine(((Integer) iter.next()).intValue());
      }
    }
  }

  // Package name may be null, in which case the file is assumed to be
  // a C source file. Otherwise it is assumed to be a Java source file
  // and certain filtering rules will be applied.
  private void showLineNumber(String packageName, String fileName, int lineNumber) {
    String name;
    if (packageName == null) {
      name = fileName;
    } else {
      name = packageName + "." + fileName;
    }
    Editor editor = (Editor) editors.get(name);
    if (editor == null) {
      // See whether file exists
      File file = new File(fileName);
      String realFileName = fileName;
      if (!file.exists()) {
        // User must specify path to file
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Please locate " + fileName);
        chooser.setMultiSelectionEnabled(false);
        if (packageName != null) {
          chooser.setFileFilter(new JavaFileFilter(packageName, fileName));
        }
        int res = chooser.showOpenDialog(null);
        if (res != JFileChooser.APPROVE_OPTION) {
          // FIXME: show disassembly instead
          return;
        }
        // FIXME: would like to infer more from the selection; i.e.,
        // a pathmap leading up to this file
        File chosen = chooser.getSelectedFile();
        if (chosen == null) {
          return;
        }
        realFileName = chosen.getPath();
      }
      // Now instruct editor factory to open file
      editor = editorFact.openFile(realFileName, editorComm);
      if (editor == null) {
        showMessageDialog("Unable to open file \"" + realFileName + "\" -- unexpected error.",
                          "Unable to open file",
                          JOptionPane.WARNING_MESSAGE);
        return;
      }
      // Got an editor; put it in map
      editors.put(name, editor);
      // If Java source file, add additional information for later
      if (packageName != null) {
        editor.setUserData(new JavaUserData(packageName, fileName));
      }
    }
    // Got editor; show line
    editor.showLineNumber(lineNumber);
    editor.highlightLineNumber(lineNumber);
    // Show breakpoints as well if we have any for this file
    Set set = (Set) fileToBreakpointMap.get(editor.getSourceFileName());
    if (set != null) {
      for (Iterator iter = set.iterator(); iter.hasNext(); ) {
        editor.showBreakpointAtLine(((Integer) iter.next()).intValue());
      }
    }
  }

  //
  // Suspend/resume
  //

  private boolean isSuspended() {
    return suspended;
  }

  private synchronized void suspend() {
    setMenuItemsEnabled(resumeDebugMenuItems, true);
    setMenuItemsEnabled(suspendDebugMenuItems, false);
    BugSpotAgent agent = getAgent();
    if (agent.canInteractWithJava() && !agent.isJavaSuspended()) {
      agent.suspendJava();
    }
    agent.suspend();
    // FIXME: call VM.getVM().fireVMSuspended()
    resetCurrentThread();
    debugEventTimer.stop();
    suspended = true;
  }

  private synchronized void resume() {
    // Note: we don't wipe out the cached state like the
    // sourceFileToLineNumberInfoMap since it is too expensive to
    // recompute. Instead we recompute it if any DLLs are loaded or
    // unloaded.
    threadToJavaThreadMap = null;
    setMenuItemsEnabled(resumeDebugMenuItems, false);
    setMenuItemsEnabled(suspendDebugMenuItems, true);
    registerPanel.clear();
    // FIXME: call VM.getVM().fireVMResumed()
    BugSpotAgent agent = getAgent();
    agent.resume();
    if (agent.canInteractWithJava()) {
      if (agent.isJavaSuspended()) {
        agent.resumeJava();
      }
      if (javaEventPending) {
        javaEventPending = false;
        // Clear it out before resuming polling for events
        agent.javaEventContinue();
      }
    }
    agent.enableJavaInteraction();
    suspended = false;
    debugEventTimer.start();
  }

  //
  // Breakpoints
  //

  private synchronized BreakpointResult handleBreakpointToggle(Editor editor, int lineNumber) {
    // Currently we only use user data in editors to indicate Java
    // source files. If this changes then this code will need to
    // change.
    JavaUserData data = (JavaUserData) editor.getUserData();
    String filename = editor.getSourceFileName();
    if (data == null) {
      // C/C++ code
      // FIXME: as noted above in EditorCommands.toggleBreakpointAtLine,
      // this needs more work to handle "lazy" breakpoints in files
      // which we don't know about in the debug information yet
      CDebugger dbg = getCDebugger();
      ProcessControl prctl = dbg.getProcessControl();
      if (prctl == null) {
        return new BreakpointResult(false, false, 0, "Process control not enabled");
      }
      boolean mustSuspendAndResume = (!prctl.isSuspended());
      try {
        if (mustSuspendAndResume) {
          prctl.suspend();
        }
        // Search debug info for all DSOs
        LineNumberInfo info = getLineNumberInfo(filename, lineNumber);
        if (info != null) {
          Set bpset = (Set) fileToBreakpointMap.get(filename);
          if (bpset == null) {
            bpset = new HashSet();
            fileToBreakpointMap.put(filename, bpset);
          }
          Integer key = new Integer(info.getLineNumber());
          if (bpset.contains(key)) {
            // Clear breakpoint at this line's PC
            prctl.clearBreakpoint(info.getStartPC());
            bpset.remove(key);
            return new BreakpointResult(true, false, info.getLineNumber());
          } else {
            // Set breakpoint at this line's PC
            System.err.println("Setting breakpoint at PC " + info.getStartPC());
            prctl.setBreakpoint(info.getStartPC());
            bpset.add(key);
            return new BreakpointResult(true, true, info.getLineNumber());
          }
        } else {
          return new BreakpointResult(false, false, 0, "No debug information for this source file and line");
        }
      } finally {
        if (mustSuspendAndResume) {
          prctl.resume();
        }
      }
    } else {
      BugSpotAgent agent = getAgent();
      if (!agent.canInteractWithJava()) {
        String why;
        if (agent.isJavaInteractionDisabled()) {
          why = "Can not toggle Java breakpoints while stopped because\nof C/C++ debug events (breakpoints, single-stepping)";
        } else {
          why = "Could not talk to SA's JVMDI module to enable Java\nprogramming language breakpoints (run with -Xdebug -Xrunsa)";
        }
        return new BreakpointResult(false, false, 0, why);
      }
      Set bpset = (Set) fileToBreakpointMap.get(filename);
      if (bpset == null) {
        bpset = new HashSet();
        fileToBreakpointMap.put(filename, bpset);
      }
      boolean mustResumeAndSuspend = isSuspended();
      try {
        if (mustResumeAndSuspend) {
          agent.resume();
        }
        ServiceabilityAgentJVMDIModule.BreakpointToggleResult res =
          getAgent().toggleJavaBreakpoint(data.sourceFileName(),
                                          data.packageName(),
                                          lineNumber);
        if (res.getSuccess()) {
          Integer key = new Integer(res.getLineNumber());
          boolean addRemRes = false;
          if (res.getWasSet()) {
            addRemRes = bpset.add(key);
            System.err.println("Setting breakpoint at " + res.getMethodName() + res.getMethodSignature() +
                               ", bci " + res.getBCI() + ", line " + res.getLineNumber());
          } else {
            addRemRes = bpset.remove(key);
            System.err.println("Clearing breakpoint at " + res.getMethodName() + res.getMethodSignature() +
                               ", bci " + res.getBCI() + ", line " + res.getLineNumber());
          }
          if (Assert.ASSERTS_ENABLED) {
            Assert.that(addRemRes, "Inconsistent Java breakpoint state with respect to target process");
          }
          return new BreakpointResult(true, res.getWasSet(), res.getLineNumber());
        } else {
          return new BreakpointResult(false, false, 0, res.getErrMsg());
        }
      } finally {
        if (mustResumeAndSuspend) {
          agent.suspend();
          resetCurrentThread();
        }
      }
    }
  }

  // Must call only when suspended
  private LineNumberInfo getLineNumberInfo(String filename, int lineNumber) {
    Map map = getSourceFileToLineNumberInfoMap();
    java.util.List infos = (java.util.List) map.get(filename);
    if (infos == null) {
      return null;
    }
    // Binary search for line number
    return searchLineNumbers(infos, lineNumber, 0, infos.size());
  }

  // Must call only when suspended
  private Map getSourceFileToLineNumberInfoMap() {
    if (sourceFileToLineNumberInfoMap == null) {
      // Build from debug info
      java.util.List loadObjects = getCDebugger().getLoadObjectList();
      final Map map = new HashMap();
      for (Iterator iter = loadObjects.iterator(); iter.hasNext(); ) {
        LoadObject lo = (LoadObject) iter.next();
        CDebugInfoDataBase db = lo.getDebugInfoDataBase();
        if (db != null) {
          db.iterate(new LineNumberVisitor() {
              public void doLineNumber(LineNumberInfo info) {
                String name = info.getSourceFileName();
                if (name != null) {
                  java.util.List val = (java.util.List) map.get(name);
                  if (val == null) {
                    val = new ArrayList();
                    map.put(name, val);
                  }
                  val.add(info);
                }
              }
            });
        }
      }
      // Sort all lists
      for (Iterator iter = map.values().iterator(); iter.hasNext(); ) {
        java.util.List list = (java.util.List) iter.next();
        Collections.sort(list, new Comparator() {
            public int compare(Object o1, Object o2) {
              LineNumberInfo l1 = (LineNumberInfo) o1;
              LineNumberInfo l2 = (LineNumberInfo) o2;
              int n1 = l1.getLineNumber();
              int n2 = l2.getLineNumber();
              if (n1 < n2) return -1;
              if (n1 == n2) return 0;
              return 1;
            }
          });
      }
      sourceFileToLineNumberInfoMap = map;
    }
    return sourceFileToLineNumberInfoMap;
  }

  private LineNumberInfo searchLineNumbers(java.util.List infoList, int lineNo, int lowIdx, int highIdx) {
    if (highIdx < lowIdx) return null;
    if (lowIdx == highIdx) {
      // Base case: see whether start PC is less than or equal to addr
      if (checkLineNumber(infoList, lineNo, lowIdx)) {
        return (LineNumberInfo) infoList.get(lowIdx);
      } else {
        return null;
      }
    } else if (lowIdx == highIdx - 1) {
      if (checkLineNumber(infoList, lineNo, lowIdx)) {
        return (LineNumberInfo) infoList.get(lowIdx);
      } else if (checkLineNumber(infoList, lineNo, highIdx)) {
        return (LineNumberInfo) infoList.get(highIdx);
      } else {
        return null;
      }
    }
    int midIdx = (lowIdx + highIdx) >> 1;
    LineNumberInfo info = (LineNumberInfo) infoList.get(midIdx);
    if (lineNo < info.getLineNumber()) {
      // Always move search down
      return searchLineNumbers(infoList, lineNo, lowIdx, midIdx);
    } else if (lineNo == info.getLineNumber()) {
      return info;
    } else {
      // Move search up
      return searchLineNumbers(infoList, lineNo, midIdx, highIdx);
    }
  }

  private boolean checkLineNumber(java.util.List infoList, int lineNo, int idx) {
    LineNumberInfo info = (LineNumberInfo) infoList.get(idx);
    return (info.getLineNumber() >= lineNo);
  }

  //
  // Debug events
  //

  private synchronized void pollForDebugEvent() {
    ProcessControl prctl = getCDebugger().getProcessControl();
    if (prctl == null) {
      return;
    }
    DebugEvent ev = prctl.debugEventPoll();
    if (ev != null) {
      DebugEvent.Type t = ev.getType();
      if (t == DebugEvent.Type.LOADOBJECT_LOAD ||
          t == DebugEvent.Type.LOADOBJECT_UNLOAD) {
        // Conservatively clear cached debug info state
        sourceFileToLineNumberInfoMap = null;
        // FIXME: would be very useful to have "stop on load/unload"
        // events
        // FIXME: must do work at these events to implement lazy
        // breakpoints
        prctl.debugEventContinue();
      } else if (t == DebugEvent.Type.BREAKPOINT) {
        // Note: Visual C++ only notifies on breakpoints it doesn't
        // know about

        // FIXME: put back test
        //        if (!prctl.isBreakpointSet(ev.getPC())) {
          showMessageDialog("Breakpoint reached at PC " + ev.getPC(),
                            "Breakpoint reached",
                            JOptionPane.INFORMATION_MESSAGE);
          //        }
        agent.disableJavaInteraction();
        suspend();
        prctl.debugEventContinue();
      } else if (t == DebugEvent.Type.SINGLE_STEP) {
        agent.disableJavaInteraction();
        suspend();
        prctl.debugEventContinue();
      } else if (t == DebugEvent.Type.ACCESS_VIOLATION) {
        showMessageDialog("Access violation attempting to " +
                          (ev.getWasWrite() ? "write" : "read") +
                          " address " + ev.getAddress() +
                          " at PC " + ev.getPC(),
                          "Access Violation",
                          JOptionPane.WARNING_MESSAGE);
        agent.disableJavaInteraction();
        suspend();
        prctl.debugEventContinue();
      } else {
        String info = "Unknown debug event encountered";
        if (ev.getUnknownEventDetail() != null) {
          info = info + ": " + ev.getUnknownEventDetail();
        }
        showMessageDialog(info, "Unknown debug event", JOptionPane.INFORMATION_MESSAGE);
        suspend();
        prctl.debugEventContinue();
      }
      return;
    }

    // No C++ debug event; poll for Java debug event
    if (getAgent().canInteractWithJava()) {
      if (!javaEventPending) {
        if (getAgent().javaEventPending()) {
          suspend();
          // This does a lot of work and we want to have the page
          // cache available to us as it runs
          sun.jvm.hotspot.livejvm.Event jev = getAgent().javaEventPoll();
          if (jev != null) {
            javaEventPending = true;
            if (jev.getType() == sun.jvm.hotspot.livejvm.Event.Type.BREAKPOINT) {
              BreakpointEvent bpev = (BreakpointEvent) jev;
              showMessageDialog("Breakpoint reached in method\n" +
                                bpev.methodID().method().externalNameAndSignature() +
                                ",\nbci " + bpev.location(),
                                "Breakpoint reached",
                                JOptionPane.INFORMATION_MESSAGE);
            } else if (jev.getType() == sun.jvm.hotspot.livejvm.Event.Type.EXCEPTION) {
              ExceptionEvent exev = (ExceptionEvent) jev;
              showMessageDialog(exev.exception().getKlass().getName().asString() +
                                "\nthrown in method\n" +
                                exev.methodID().method().externalNameAndSignature() +
                                "\nat BCI " + exev.location(),
                                "Exception thrown",
                                JOptionPane.INFORMATION_MESSAGE);
            } else {
              Assert.that(false, "Should not reach here");
            }
          }
        }
      }
    }
  }
}
