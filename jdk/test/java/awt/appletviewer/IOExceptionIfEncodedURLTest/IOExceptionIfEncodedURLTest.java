/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
  test
  @bug 6193279
  @summary REGRESSION: AppletViewer throws IOException when path is encoded URL
  @author Dmitry Cherepanov: area=appletviewer
  @run compile IOExceptionIfEncodedURLTest.java
  @run main IOExceptionIfEncodedURLTest
  @run shell IOExceptionIfEncodedURLTest.sh
*/

import java.applet.Applet;
import sun.net.www.ParseUtil;
import java.io.File;
import java.net.MalformedURLException;

public class IOExceptionIfEncodedURLTest extends Applet{
    public void init(){
    }

    public void start(){
        // We check that appletviewer writes this message to log file
        System.err.println("the appletviewer started");
    }

    // We expect that sun.net.www.ParseUtil.fileToEncodedURL works like following
    // if relative file URL, like this "file:index.html" is processed
    static String url = "file:IOExceptionIfEncodedURLTest.java";
    public static final void main(String args[])
      throws MalformedURLException{
        System.err.println("prior checking...");
        String prefix = "file:";
        String path = ParseUtil.fileToEncodedURL(new File(System.getProperty("user.dir"))).getPath();
        String filename = url.substring(prefix.length());
        System.err.println("url="+url+" -> path="+path+",filename="+filename);

        if (!path.endsWith("/") && !filename.startsWith("/")) {
            throw new RuntimeException("Incorrect '/' processing");
        }
    }

}
