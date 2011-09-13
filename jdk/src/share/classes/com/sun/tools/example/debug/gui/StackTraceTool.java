/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.debug.gui;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import com.sun.jdi.*;
import com.sun.tools.example.debug.bdi.*;

public class StackTraceTool extends JPanel {

    private static final long serialVersionUID = 9140041989427965718L;

    private Environment env;

    private ExecutionManager runtime;
    private ContextManager context;

    private ThreadInfo tinfo;

    private JList list;
    private ListModel stackModel;

    public StackTraceTool(Environment env) {

        super(new BorderLayout());

        this.env = env;
        this.runtime = env.getExecutionManager();
        this.context = env.getContextManager();

        stackModel = new DefaultListModel();  // empty

        list = new JList(stackModel);
        list.setCellRenderer(new StackFrameRenderer());

        JScrollPane listView = new JScrollPane(list);
        add(listView);

        // Create listener.
        StackTraceToolListener listener = new StackTraceToolListener();
        context.addContextListener(listener);
        list.addListSelectionListener(listener);

        //### remove listeners on exit!
    }

    private class StackTraceToolListener
        implements ContextListener, ListSelectionListener
    {

        // ContextListener

        // If the user selects a new current frame, display it in
        // this view.

        //### I suspect we handle the case badly that the VM is not interrupted.

        @Override
        public void currentFrameChanged(CurrentFrameChangedEvent e) {
            // If the current frame of the thread appearing in this
            // view is changed, move the selection to track it.
            int frameIndex = e.getIndex();
            ThreadInfo ti = e.getThreadInfo();
            if (e.getInvalidate() || tinfo != ti) {
                tinfo = ti;
                showStack(ti, frameIndex);
            } else {
                if (frameIndex < stackModel.getSize()) {
                    list.setSelectedIndex(frameIndex);
                    list.ensureIndexIsVisible(frameIndex);
                }
            }
        }

        // ListSelectionListener

        @Override
        public void valueChanged(ListSelectionEvent e) {
            int index = list.getSelectedIndex();
            if (index != -1) {
                //### should use listener?
                try {
                    context.setCurrentFrameIndex(index);
                } catch (VMNotInterruptedException exc) {
                }
            }
        }
    }

    private class StackFrameRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {

            //### We should indicate the current thread independently of the
            //### selection, e.g., with an icon, because the user may change
            //### the selection graphically without affecting the current
            //### thread.

            super.getListCellRendererComponent(list, value, index,
                                               isSelected, cellHasFocus);
            if (value == null) {
                this.setText("<unavailable>");
            } else {
                StackFrame frame = (StackFrame)value;
                Location loc = frame.location();
                Method meth = loc.method();
                String methName =
                    meth.declaringType().name() + '.' + meth.name();
                String position = "";
                if (meth.isNative()) {
                    position = " (native method)";
                } else if (loc.lineNumber() != -1) {
                    position = ":" + loc.lineNumber();
                } else {
                    long pc = loc.codeIndex();
                    if (pc != -1) {
                        position = ", pc = " + pc;
                    }
                }
                // Indices are presented to the user starting from 1, not 0.
                this.setText("[" + (index+1) +"] " + methName + position);
            }
            return this;
        }
    }

    // Point this view at the given thread and frame.

    private void showStack(ThreadInfo tinfo, int selectFrame) {
        StackTraceListModel model = new StackTraceListModel(tinfo);
        stackModel = model;
        list.setModel(stackModel);
        list.setSelectedIndex(selectFrame);
        list.ensureIndexIsVisible(selectFrame);
    }

    private static class StackTraceListModel extends AbstractListModel {

        private final ThreadInfo tinfo;

        public StackTraceListModel(ThreadInfo tinfo) {
            this.tinfo = tinfo;
        }

        @Override
        public Object getElementAt(int index) {
            try {
                return tinfo == null? null : tinfo.getFrame(index);
            } catch (VMNotInterruptedException e) {
                //### Is this the right way to handle this?
                //### Would happen if user scrolled stack trace
                //### while not interrupted -- should probably
                //### block user interaction in this case.
                return null;
            }
        }

        @Override
        public int getSize() {
            try {
                return tinfo == null? 1 : tinfo.getFrameCount();
            } catch (VMNotInterruptedException e) {
                //### Is this the right way to handle this?
                return 0;
            }
        }
    }
}
