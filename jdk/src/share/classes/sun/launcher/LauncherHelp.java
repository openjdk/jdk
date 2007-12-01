
/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.launcher;

/*
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.
 *  If you write code that depends on this, you do so at your own
 *  risk.  This code and its internal interfaces are subject to change
 *  or deletion without notice.</b>
 *
 */

/**
 * A utility package for the java(1), javaw(1) launchers.
 */
import java.io.File;
import java.io.PrintStream;
import java.util.ResourceBundle;
import java.text.MessageFormat;

public class LauncherHelp {

    private static final String defaultBundleName = "sun.launcher.resources.launcher";
    private static ResourceBundle javarb = ResourceBundle.getBundle(defaultBundleName);

    private static StringBuilder outBuf = new StringBuilder();

    /** Creates a new instance of LauncherHelp, keep it a singleton */
    private LauncherHelp(){}


    /**
     * A private helper method to get a localized message and also
     * apply any arguments that we might pass.
     */
    private static String getLocalizedMessage(String key, Object... args) {
        String msg = javarb.getString(key);
        return (args != null) ? MessageFormat.format(msg, args) : msg;
    }

    /**
     * The java -help message is split into 3 parts, an invariant, followed
     * by a set of platform dependent variant messages, finally an invariant
     * set of lines.
     * This method initializes the help message for the first time, and also
     * assembles the invariant header part of the message.
     */
    static void initHelpMessage(String progname) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.header", (progname == null) ? "java" : progname ));
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.datamodel", 32));
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.datamodel", 64));
    }

    /**
     * Appends the vm selection messages to the header, already created.
     * initHelpSystem must already be called.
     */
    static void appendVmSelectMessage(String vm1, String vm2) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.vmselect", vm1, vm2));
    }

    /**
     * Appends the vm synoym message to the header, already created.
     * initHelpSystem must be called before using this method.
     */
    static void appendVmSynonymMessage(String vm1, String vm2) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.hotspot", vm1, vm2));
    }

    /**
     * Appends the vm Ergo message to the header, already created.
     * initHelpSystem must be called before using this method.
     */
    static void appendVmErgoMessage(boolean isServerClass, String vm) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.ergo.message1", vm));
        outBuf = (isServerClass)
             ? outBuf.append(",\n" + getLocalizedMessage("java.launcher.ergo.message2") + "\n\n")
             : outBuf.append(".\n\n");
    }

    /**
     * Appends the last invariant part to the previously created messages,
     * and finishes up the printing to the desired output stream.
     * initHelpSystem must be called before using this method.
     */
    static void printHelpMessage(boolean printToStderr) {
        PrintStream ostream = (printToStderr) ? System.err : System.out;
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.footer",  File.pathSeparator));
        ostream.println(outBuf.toString());
    }

    /**
     * Prints the Xusage text to the desired output stream.
     */
    static void printXUsageMessage(boolean printToStderr) {
        PrintStream ostream =  (printToStderr) ? System.err : System.out;
        ostream.println(getLocalizedMessage("java.launcher.X.usage",  File.pathSeparator));
    }

    /* Test code */
    public static void main(String[] args) {
        initHelpMessage("java");
        appendVmSelectMessage("-client", "client");
        appendVmSelectMessage("-server", "server");
        appendVmSynonymMessage("-hotspot", "client");
        appendVmErgoMessage(true, "server");
        printHelpMessage(true);

        System.err.println("------------------------------------");

        printXUsageMessage(true);
    }
}
