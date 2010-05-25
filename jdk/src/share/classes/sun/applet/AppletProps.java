/*
 * Copyright (c) 1995, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.applet;

import java.awt.*;
import java.io.*;
import java.util.Properties;
import sun.net.www.http.HttpClient;
import sun.net.ftp.FtpClient;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;

import sun.security.action.*;

class AppletProps extends Frame {

    TextField proxyHost;
    TextField proxyPort;
    Choice accessMode;

    AppletProps() {
        setTitle(amh.getMessage("title"));
        Panel p = new Panel();
        p.setLayout(new GridLayout(0, 2));

        p.add(new Label(amh.getMessage("label.http.server", "Http proxy server:")));
        p.add(proxyHost = new TextField());

        p.add(new Label(amh.getMessage("label.http.proxy")));
        p.add(proxyPort = new TextField());

        p.add(new Label(amh.getMessage("label.class")));
        p.add(accessMode = new Choice());
        accessMode.addItem(amh.getMessage("choice.class.item.restricted"));
        accessMode.addItem(amh.getMessage("choice.class.item.unrestricted"));

        add("Center", p);
        p = new Panel();
        p.add(new Button(amh.getMessage("button.apply")));
        p.add(new Button(amh.getMessage("button.reset")));
        p.add(new Button(amh.getMessage("button.cancel")));
        add("South", p);
        move(200, 150);
        pack();
        reset();
    }

    void reset() {
        AppletSecurity security = (AppletSecurity) System.getSecurityManager();
        if (security != null)
            security.reset();

        String proxyhost = (String) AccessController.doPrivileged(
                new GetPropertyAction("http.proxyHost"));
        String proxyport = (String)  AccessController.doPrivileged(
                new GetPropertyAction("http.proxyPort"));

        Boolean tmp = (Boolean) AccessController.doPrivileged(
                new GetBooleanAction("package.restrict.access.sun"));

        boolean packageRestrict = tmp.booleanValue();
        if (packageRestrict) {
           accessMode.select(amh.getMessage("choice.class.item.restricted"));
        } else {
           accessMode.select(amh.getMessage("choice.class.item.unrestricted"));
        }

        if (proxyhost != null) {
            proxyHost.setText(proxyhost);
            proxyPort.setText(proxyport);
        } else {
            proxyHost.setText("");
            proxyPort.setText("");
        }
    }

    void apply() {
        String proxyHostValue = proxyHost.getText().trim();
        String proxyPortValue = proxyPort.getText().trim();

        // Get properties
        final Properties props = (Properties) AccessController.doPrivileged(
             new PrivilegedAction() {
                 public Object run() {
                     return System.getProperties();
                 }
        });

        if (proxyHostValue.length() != 0) {
            /* 4066402 */
            /* Check for parsable value in proxy port number field before */
            /* applying. Display warning to user until parsable value is  */
            /* entered. */
            int proxyPortNumber = 0;
            try {
                proxyPortNumber = Integer.parseInt(proxyPortValue);
            } catch (NumberFormatException e) {}

            if (proxyPortNumber <= 0) {
                proxyPort.selectAll();
                proxyPort.requestFocus();
                (new AppletPropsErrorDialog(this,
                                            amh.getMessage("title.invalidproxy"),
                                            amh.getMessage("label.invalidproxy"),
                                            amh.getMessage("button.ok"))).show();
                return;
            }
            /* end 4066402 */

            props.put("http.proxyHost", proxyHostValue);
            props.put("http.proxyPort", proxyPortValue);
        } else {
            props.put("http.proxyHost", "");
        }

        if (amh.getMessage("choice.class.item.restricted").equals(accessMode.getSelectedItem())) {
            props.put("package.restrict.access.sun", "true");
        } else {
            props.put("package.restrict.access.sun", "false");
        }

        // Save properties
        try {
            reset();
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws IOException {
                    File dotAV = Main.theUserPropertiesFile;
                    FileOutputStream out = new FileOutputStream(dotAV);
                    Properties avProps = new Properties();
                    for (int i = 0; i < Main.avDefaultUserProps.length; i++) {
                        String avKey = Main.avDefaultUserProps[i][0];
                        avProps.setProperty(avKey, props.getProperty(avKey));
                    }
                    avProps.store(out, amh.getMessage("prop.store"));
                    out.close();
                    return null;
                }
            });
            hide();
        } catch (java.security.PrivilegedActionException e) {
            System.out.println(amh.getMessage("apply.exception",
                                              e.getException()));
            // XXX what's the general feeling on stack traces to System.out?
            e.printStackTrace();
            reset();
        }
    }

    public boolean action(Event evt, Object obj) {
        if (amh.getMessage("button.apply").equals(obj)) {
            apply();
            return true;
        }
        if (amh.getMessage("button.reset").equals(obj)) {
            reset();
            return true;
        }
        if (amh.getMessage("button.cancel").equals(obj)) {
            reset();
            hide();
            return true;
        }
        return false;
    }

    private static AppletMessageHandler amh = new AppletMessageHandler("appletprops");

}

/* 4066432 */
/* Dialog class to display property-related errors to user */

class AppletPropsErrorDialog extends Dialog {
    public AppletPropsErrorDialog(Frame parent, String title, String message,
                String buttonText) {
        super(parent, title, true);
        Panel p = new Panel();
        add("Center", new Label(message));
        p.add(new Button(buttonText));
        add("South", p);
        pack();

        Dimension dDim = size();
        Rectangle fRect = parent.bounds();
        move(fRect.x + ((fRect.width - dDim.width) / 2),
             fRect.y + ((fRect.height - dDim.height) / 2));
    }

    public boolean action(Event event, Object object) {
        hide();
        dispose();
        return true;
    }
}

/* end 4066432 */
