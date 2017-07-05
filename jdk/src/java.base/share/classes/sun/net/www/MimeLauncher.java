/*
 * Copyright (c) 1994, 1998, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www;
import java.net.URL;
import java.io.*;
import java.util.StringTokenizer;
import sun.security.action.GetPropertyAction;

class MimeLauncher extends Thread {
    java.net.URLConnection uc;
    MimeEntry m;
    String genericTempFileTemplate;
    InputStream is;
    String execPath;

    MimeLauncher (MimeEntry M, java.net.URLConnection uc,
                  InputStream is, String tempFileTemplate, String threadName) throws ApplicationLaunchException {
        super(null, null, threadName, 0, false);
        m = M;
        this.uc = uc;
        this.is = is;
        genericTempFileTemplate = tempFileTemplate;

        /* get the application to launch */
        String launchString = m.getLaunchString();

        /* get a valid path to launch application - sets
           the execPath instance variable with the correct path.
         */
        if (!findExecutablePath(launchString)) {
            /* strip off parameters i.e %s */
            String appName;
            int index = launchString.indexOf(' ');
            if (index != -1) {
                appName = launchString.substring(0, index);
            }
            else {
                appName = launchString;
            }
            throw new ApplicationLaunchException(appName);
        }
    }

    protected String getTempFileName(URL url, String template) {
        String tempFilename = template;

        // Replace all but last occurrance of "%s" with timestamp to insure
        // uniqueness.  There's a subtle behavior here: if there is anything
        // _after_ the last "%s" we need to append it so that unusual launch
        // strings that have the datafile in the middle can still be used.
        int wildcard = tempFilename.lastIndexOf("%s");
        String prefix = tempFilename.substring(0, wildcard);

        String suffix = "";
        if (wildcard < tempFilename.length() - 2) {
            suffix = tempFilename.substring(wildcard + 2);
        }

        long timestamp = System.currentTimeMillis()/1000;
        int argIndex = 0;
        while ((argIndex = prefix.indexOf("%s")) >= 0) {
            prefix = prefix.substring(0, argIndex)
                + timestamp
                + prefix.substring(argIndex + 2);
        }

        // Add a file name and file-extension if known
        String filename = url.getFile();

        String extension = "";
        int dot = filename.lastIndexOf('.');

        // BugId 4084826:  Temp MIME file names not always valid.
        // Fix:  don't allow slashes in the file name or extension.
        if (dot >= 0 && dot > filename.lastIndexOf('/')) {
            extension = filename.substring(dot);
        }

        filename = "HJ" + url.hashCode();

        tempFilename = prefix + filename + timestamp + extension + suffix;

        return tempFilename;
    }

    public void run() {
        try {
            String ofn = m.getTempFileTemplate();
            if (ofn == null) {
                ofn = genericTempFileTemplate;
            }

            ofn = getTempFileName(uc.getURL(), ofn);
            try {
                OutputStream os = new FileOutputStream(ofn);
                byte buf[] = new byte[2048];
                int i = 0;
                try {
                    while ((i = is.read(buf)) >= 0) {
                        os.write(buf, 0, i);
                    }
                } catch(IOException e) {
                  //System.err.println("Exception in write loop " + i);
                  //e.printStackTrace();
                } finally {
                    os.close();
                    is.close();
                }
            } catch(IOException e) {
              //System.err.println("Exception in input or output stream");
              //e.printStackTrace();
            }

            int inx = 0;
            String c = execPath;
            while ((inx = c.indexOf("%t")) >= 0) {
                c = c.substring(0, inx) + uc.getContentType()
                    + c.substring(inx + 2);
            }

            boolean substituted = false;
            while ((inx = c.indexOf("%s")) >= 0) {
                c = c.substring(0, inx) + ofn + c.substring(inx + 2);
                substituted = true;
            }
            if (!substituted)
                c = c + " <" + ofn;

            // System.out.println("Execing " +c);

            Runtime.getRuntime().exec(c);
        } catch(IOException e) {
        }
    }

    /* This method determines the path for the launcher application
       and sets the execPath instance variable.  It uses the exec.path
       property to obtain a list of paths that is in turn used to
       location the application.  If a valid path is not found, it
       returns false else true.  */
    private boolean findExecutablePath(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }

        String command;
        int index = str.indexOf(' ');
        if (index != -1) {
            command = str.substring(0, index);
        }
        else {
            command = str;
        }

        File f = new File(command);
        if (f.isFile()) {
            // Already executable as it is
            execPath = str;
            return true;
        }

        String execPathList;
        execPathList = GetPropertyAction.getProperty("exec.path");
        if (execPathList == null) {
            // exec.path property not set
            return false;
        }

        StringTokenizer iter = new StringTokenizer(execPathList, "|");
        while (iter.hasMoreElements()) {
            String prefix = (String)iter.nextElement();
            String fullCmd = prefix + File.separator + command;
            f = new File(fullCmd);
            if (f.isFile()) {
                execPath = prefix + File.separator + str;
                return true;
            }
        }

        return false; // application not found in exec.path
    }
}
