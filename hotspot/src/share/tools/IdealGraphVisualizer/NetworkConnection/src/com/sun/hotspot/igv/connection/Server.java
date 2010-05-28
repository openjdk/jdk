/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.connection;

import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.services.GroupReceiver;
import com.sun.hotspot.igv.settings.Settings;
import java.awt.Component;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.SwingUtilities;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Server implements GroupCallback, GroupReceiver, PreferenceChangeListener {

    private javax.swing.JPanel jPanel1;
    private javax.swing.JCheckBox networkCheckBox;
    private javax.swing.JTextField networkTextField;
    private ServerSocket serverSocket;
    private GroupCallback callback;
    private int port;
    private Runnable serverRunnable;

    public Component init(GroupCallback callback) {

        this.callback = callback;

        jPanel1 = new javax.swing.JPanel();
        networkTextField = new javax.swing.JTextField();
        networkCheckBox = new javax.swing.JCheckBox();


        jPanel1.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        jPanel1.setLayout(new java.awt.BorderLayout(10, 10));
        jPanel1.add(networkTextField, java.awt.BorderLayout.CENTER);

        networkCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(networkCheckBox, "Receive when name contains");
        networkCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        networkCheckBox.setMargin(new java.awt.Insets(0, 0, 0, 0));
        networkCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {

            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                networkCheckBoxChanged(evt);
            }
        });
        jPanel1.add(networkCheckBox, java.awt.BorderLayout.WEST);
        networkCheckBox.getAccessibleContext().setAccessibleName("Read from network when name contains");

        initializeNetwork();
        Settings.get().addPreferenceChangeListener(this);
        return jPanel1;
    }

    private void networkCheckBoxChanged(javax.swing.event.ChangeEvent evt) {
        networkTextField.setEnabled(networkCheckBox.isSelected());
    }

    public void preferenceChange(PreferenceChangeEvent e) {

        int curPort = Integer.parseInt(Settings.get().get(Settings.PORT, Settings.PORT_DEFAULT));
        if (curPort != port) {
            initializeNetwork();
        }
    }

    private void initializeNetwork() {

        int curPort = Integer.parseInt(Settings.get().get(Settings.PORT, Settings.PORT_DEFAULT));
        this.port = curPort;
        try {
            serverSocket = new java.net.ServerSocket(curPort);
        } catch (IOException ex) {
            NotifyDescriptor message = new NotifyDescriptor.Message("Could not create server. Listening for incoming data is disabled.", NotifyDescriptor.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(message);
            return;
        }

        Runnable runnable = new Runnable() {

            public void run() {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        if (serverRunnable != this) {
                            clientSocket.close();
                            return;
                        }
                        RequestProcessor.getDefault().post(new Client(clientSocket, networkTextField, Server.this), 0, Thread.MAX_PRIORITY);
                    } catch (IOException ex) {
                        serverSocket = null;
                        NotifyDescriptor message = new NotifyDescriptor.Message("Error during listening for incoming connections. Listening for incoming data is disabled.", NotifyDescriptor.ERROR_MESSAGE);
                        DialogDisplayer.getDefault().notifyLater(message);
                        return;
                    }
                }
            }
        };

        serverRunnable = runnable;

        RequestProcessor.getDefault().post(runnable, 0, Thread.MAX_PRIORITY);
    }

    public void started(final Group g) {
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                callback.started(g);
            }
        });
    }
}
