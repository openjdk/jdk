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
 */

import javax.print.*;
import java.applet.*;
import java.awt.*;

public class Applet3 extends Applet {

    String name = "Applet 3 ";
    PrintService defServ = null;
    PrintService[] allServices = null;
    StreamPrintServiceFactory []fact = null;

    public void init() {
        PrintServiceLookup.registerService(new Applet3PrintService());
        defServ = PrintServiceLookup.lookupDefaultPrintService();

        allServices = PrintServiceLookup.lookupPrintServices(null,null);

        fact = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(
              DocFlavor.SERVICE_FORMATTED.PRINTABLE,
              DocFlavor.BYTE_ARRAY.POSTSCRIPT.getMimeType());

    }

    public void paint(Graphics g) {
      g.drawString("This is " + name, 10, 20);
      g.drawString("Default print service="+defServ,10,40);
      g.drawString("Number of print services="+allServices.length,10,60);
      g.drawString("Number of PS stream services="+fact.length,10,80);
      int y=100;
      for (int i=0;i<allServices.length;i++) {
          if (allServices[i].getName().startsWith("Applet ")) {
               g.drawString("Found service : "+allServices[i].getName(),10,y);
               y+=20;
           }
      }
    }

    public static void main(String[] args) {
        Applet3 applet = new Applet3();
        applet.init();
        Frame f = new Frame("Print Lookup Test");
        f.add(applet);
        f.setSize(300,200);
        f.show();
        }


}
