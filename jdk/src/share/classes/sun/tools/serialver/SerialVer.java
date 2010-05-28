/*
 * Copyright (c) 1996, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.serialver;

import java.io.*;
import java.awt.*;
import java.applet.*;
import java.io.ObjectStreamClass;
import java.util.Properties;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.net.URLClassLoader;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.StringTokenizer;
import sun.net.www.ParseUtil;

public class SerialVer extends Applet {
    GridBagLayout gb;
    TextField classname_t;
    Button show_b;
    TextField serialversion_t;
    Label footer_l;

    private static final long serialVersionUID = 7666909783837760853L;

    public synchronized void init() {
        gb = new GridBagLayout();
        setLayout(gb);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;

        Label l1 = new Label(Res.getText("FullClassName"));
        l1.setAlignment(Label.RIGHT);
        gb.setConstraints(l1, c);
        add(l1);

        classname_t = new TextField(20);
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.weightx = 1.0;
        gb.setConstraints(classname_t, c);
        add(classname_t);

        show_b = new Button(Res.getText("Show"));
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 0.0;        /* Don't grow the button */
        gb.setConstraints(show_b, c);
        add(show_b);

        Label l2 = new Label(Res.getText("SerialVersion"));
        l2.setAlignment(Label.RIGHT);
        c.gridwidth = 1;
        gb.setConstraints(l2, c);
        add(l2);

        serialversion_t = new TextField(50);
        serialversion_t.setEditable(false);
        c.gridwidth = GridBagConstraints.REMAINDER;
        gb.setConstraints(serialversion_t, c);
        add(serialversion_t);

        footer_l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gb.setConstraints(footer_l, c);
        add(footer_l);

        /* Give the focus to the type-in area */
        classname_t.requestFocus();
    }

    public void start() {
        /* Give the focus to the type-in area */
        classname_t.requestFocus();
    }

    public boolean action(Event ev, Object obj) {
        if (ev.target == classname_t) {
            show((String)ev.arg);
            return true;
        } else if (ev.target == show_b) {
            show(classname_t.getText());
            return true;
        }
        return false;
    }


    public boolean handleEvent(Event ev) {
        boolean rc = super.handleEvent(ev);
        return rc;
    }

    /**
     * Lookup the specified classname and display it.
     */
    void show(String classname) {
        try {
            footer_l.setText(""); // Clear the message
            serialversion_t.setText(""); // clear the last value

            if (classname.equals("")) {
                return;
            }

            String s = serialSyntax(classname);
            if (s != null) {
                serialversion_t.setText(s);
            } else {
                footer_l.setText(Res.getText("NotSerializable", classname));
            }
        } catch (ClassNotFoundException cnf) {
            footer_l.setText(Res.getText("ClassNotFound", classname));
        }
    }

    /*
     * A class loader that will load from the CLASSPATH environment
     * variable set by the user.
     */
    static URLClassLoader loader = null;

    /*
     * Create a URL class loader that will load classes from the
     * specified classpath.
     */
    static void initializeLoader(String cp)
                                throws MalformedURLException, IOException {
        URL[] urls;
        StringTokenizer st = new StringTokenizer(cp, File.pathSeparator);
        int count = st.countTokens();
        urls = new URL[count];
        for (int i = 0; i < count; i++) {
            urls[i] = ParseUtil.fileToEncodedURL(
                new File(new File(st.nextToken()).getCanonicalPath()));
        }
        loader = new URLClassLoader(urls);
    }

    /*
     * From the classname find the serialVersionUID string formatted
     * for to be copied to a java class.
     */
    static String serialSyntax(String classname) throws ClassNotFoundException {
        String ret = null;
        boolean classFound = false;

        // If using old style of qualifyling inner classes with '$'s.
        if (classname.indexOf('$') != -1) {
            ret = resolveClass(classname);
        } else {
            /* Try to resolve the fully qualified name and if that fails, start
             * replacing the '.'s with '$'s starting from the last '.', until
             * the class is resolved.
             */
            try {
                ret = resolveClass(classname);
                classFound = true;
            } catch (ClassNotFoundException e) {
                /* Class not found so far */
            }
            if (!classFound) {
                StringBuffer workBuffer = new StringBuffer(classname);
                String workName = workBuffer.toString();
                int i;
                while ((i = workName.lastIndexOf('.')) != -1 && !classFound) {
                    workBuffer.setCharAt(i, '$');
                    try {
                        workName = workBuffer.toString();
                        ret = resolveClass(workName);
                        classFound = true;
                    } catch (ClassNotFoundException e) {
                        /* Continue searching */
                    }
                }
            }
            if (!classFound) {
                throw new ClassNotFoundException();
            }
        }
        return ret;
    }

    static String resolveClass(String classname) throws ClassNotFoundException {
        Class cl = Class.forName(classname, false, loader);
        ObjectStreamClass desc = ObjectStreamClass.lookup(cl);
        if (desc != null) {
            return "    static final long serialVersionUID = " +
                desc.getSerialVersionUID() + "L;";
        } else {
            return null;
        }
    }


    public static void main(String[] args) {
        boolean show = false;
        String envcp = null;
        int i = 0;

        if (args.length == 0) {
            usage();
            System.exit(1);
        }

        for (i = 0; i < args.length; i++) {
            if (args[i].equals("-show")) {
                show = true;
            } else if (args[i].equals("-classpath")) {
                if ((i+1 == args.length) || args[i+1].startsWith("-")) {
                    System.err.println(Res.getText("error.missing.classpath"));
                    usage();
                    System.exit(1);
                }
                envcp = new String(args[i+1]);
                i++;
            }  else if (args[i].startsWith("-")) {
                System.err.println(Res.getText("invalid.flag", args[i]));
                usage();
                System.exit(1);
            } else {
                break;          // drop into processing class names
            }
        }


        /*
         * Get user's CLASSPATH environment variable, if the -classpath option
         * is not defined, and make a loader that can read from that path.
         */
        if (envcp == null) {
            envcp = System.getProperty("env.class.path");
            /*
             * If environment variable not set, add current directory to path.
             */
            if (envcp == null) {
                envcp = ".";
            }
        }

        try {
            initializeLoader(envcp);
        } catch (MalformedURLException mue) {
            System.err.println(Res.getText("error.parsing.classpath", envcp));
            System.exit(2);
        } catch (IOException ioe) {
            System.err.println(Res.getText("error.parsing.classpath", envcp));
            System.exit(3);
        }

        if (!show) {
            /*
             * Check if there are any class names specified, if it is not a
             * invocation with the -show option.
             */
            if (i == args.length) {
                usage();
                System.exit(1);
            }

            /*
             * The rest of the parameters are classnames.
             */
            boolean exitFlag = false;
            for (i = i; i < args.length; i++ ) {
                try {
                    String syntax = serialSyntax(args[i]);
                    if (syntax != null)
                        System.out.println(args[i] + ":" + syntax);
                    else {
                        System.err.println(Res.getText("NotSerializable",
                            args[i]));
                        exitFlag = true;
                    }
                } catch (ClassNotFoundException cnf) {
                    System.err.println(Res.getText("ClassNotFound", args[i]));
                    exitFlag = true;
                }
            }
            if (exitFlag) {
                System.exit(1);
            }
        } else {
            if (i < args.length) {
                System.err.println(Res.getText("ignoring.classes"));
                System.exit(1);
            }
            Frame f =  new SerialVerFrame();
            //  f.setLayout(new FlowLayout());
            SerialVer sv = new SerialVer();
            sv.init();

            f.add("Center", sv);
            f.pack();
            f.show();
        }
    }


    /**
     * Usage
     */
    public static void usage() {
        System.err.println(Res.getText("usage"));
    }

}

/**
 * Top level frame so serialVer can be run as an main program
 * and have an exit menu item.
 */
class SerialVerFrame extends Frame {
    MenuBar menu_mb;
    Menu file_m;
    MenuItem exit_i;

    private static final long serialVersionUID = -7248105987187532533L;

    /*
     * Construct a new Frame with title and menu.
     */
    SerialVerFrame() {
        super(Res.getText("SerialVersionInspector"));

        /* Create the file menu */
        file_m = new Menu(Res.getText("File"));
        file_m.add(exit_i = new MenuItem(Res.getText("Exit")));

        /* Now add the file menu to the menu bar  */
        menu_mb = new MenuBar();
        menu_mb.add(file_m);

        /* Add the menubar to the frame */
        // Bug in JDK1.1        setMenuBar(menu_mb);
    }

    /*
     * Handle a window destroy event by exiting.
     */
    public boolean handleEvent(Event e) {
        if (e.id == Event.WINDOW_DESTROY) {
            exit(0);
        }
        return super.handleEvent(e);
    }
    /*
     * Handle an Exit event by exiting.
     */
    public boolean action(Event ev, Object obj) {
        if (ev.target == exit_i) {
            exit(0);
        }
        return false;
    }

    /*
     * Cleanup and exit.
     */
    void exit(int ret) {
        System.exit(ret);
    }

}

/**
 * Utility for integrating with serialver and for localization.
 * Handle Resources. Access to error and warning counts.
 * Message formatting.
 *
 * @see java.util.ResourceBundle
 * @see java.text.MessageFormat
 */
class Res {

    private static ResourceBundle messageRB;

    /**
     * Initialize ResourceBundle
     */
    static void initResource() {
        try {
            messageRB =
                ResourceBundle.getBundle("sun.tools.serialver.resources.serialver");
        } catch (MissingResourceException e) {
            throw new Error("Fatal: Resource for serialver is missing");
        }
    }

    /**
     * get and format message string from resource
     *
     * @param key selects message from resource
     */
    static String getText(String key) {
        return getText(key, (String)null);
    }

    /**
     * get and format message string from resource
     *
     * @param key selects message from resource
     * @param a1 first argument
     */
    static String getText(String key, String a1) {
        return getText(key, a1, null);
    }

    /**
     * get and format message string from resource
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     */
    static String getText(String key, String a1, String a2) {
        return getText(key, a1, a2, null);
    }

    /**
     * get and format message string from resource
     *
     * @param key selects message from resource
     * @param a1 first argument
     * @param a2 second argument
     * @param a3 third argument
     */
    static String getText(String key, String a1, String a2, String a3) {
        if (messageRB == null) {
            initResource();
        }
        try {
            String message = messageRB.getString(key);
            String[] args = new String[3];
            args[0] = a1;
            args[1] = a2;
            args[2] = a3;
            return MessageFormat.format(message, args);
        } catch (MissingResourceException e) {
            throw new Error("Fatal: Resource for serialver is broken. There is no " + key + " key in resource.");
        }
    }
}
