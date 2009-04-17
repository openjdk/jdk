/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.applet.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class BigFont extends Applet {

   static private class SizedInputStream extends InputStream {

       int size;
       int cnt = 0;

       SizedInputStream(int size) {
           this.size = size;
       }

       public int read() {
           if (cnt < size) {
              cnt++;
              return 0;
           } else {
              return -1;
           }
       }

       public int getCurrentSize() {
           return cnt;
       }
   }

    String id;
    String fileName;

    public void init() {
        id = getParameter("number");
        fileName = getParameter("font");

        System.out.println("Applet " + id + " "+
                           Thread.currentThread().getThreadGroup());
        // Larger than size for a single font.
        int fontSize = 64 * 1000 * 1000;
        SizedInputStream sis = new SizedInputStream(fontSize);
        try {
             Font font = Font.createFont(Font.TRUETYPE_FONT, sis);
        } catch (Throwable t) {
            if (t instanceof FontFormatException ||
                fontSize <= sis.getCurrentSize())
            {
                System.out.println(sis.getCurrentSize());
                System.out.println(t);
                throw new RuntimeException("Allowed file to be too large.");
            }
        }
        // The following part of the test was verified manually but
        // is impractical to enable  because it requires a fairly large
        // valid font to be part of the test, and we can't easily include
        // that, nor dependably reference one from the applet environment.
        /*
        if (fileName == null) {
            return;
        }
        int size = getFileSize(fileName);
        if (size == 0) {
            return;
        }
        int fontCnt = 1000 * 1000 * 1000 / size;
        loadMany(size, fontCnt, fileName);
        System.gc(); System.gc();
        fontCnt = fontCnt / 2;
        System.out.println("Applet " + id + " load more.");
        loadMany(size, fontCnt, fileName);
        */
        System.out.println("Applet " + id + " finished.");
    }

    int getFileSize(String fileName) {
        try {
            URL url = new URL(getCodeBase(), fileName);
            InputStream inStream = url.openStream();
            BufferedInputStream fontStream = new BufferedInputStream(inStream);
            int size = 0;
            while (fontStream.read() != -1) {
                size++;
            }
            fontStream.close();
            return size;
        } catch (IOException e) {
            return 0;
        }

    }
    void loadMany(int oneFont, int fontCnt, String fileName) {
        System.out.println("fontcnt= " + fontCnt);
        Font[] fonts = new Font[fontCnt];
        int totalSize = 0;
        boolean gotException = false;
        for (int i=0; i<fontCnt; i++) {
            try {
                URL url = new URL(getCodeBase(), fileName);
                InputStream inStream = url.openStream();
                BufferedInputStream fontStream =
                    new BufferedInputStream(inStream);
                fonts[i] = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                totalSize += oneFont;
                fontStream.close();
            } catch (Throwable t) {
                gotException = true;
                System.out.println("Applet " + id + " " + t);
            }
        }
        if (!gotException) {
          throw new RuntimeException("No expected exception");
        }
    }
}

