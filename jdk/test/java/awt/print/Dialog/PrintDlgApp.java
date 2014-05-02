/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4865976 7158366
  @summary  Pass if it program exits.
  @run main/manual PrintDlgApp
*/
import java.awt.*;
import java.awt.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.Destination;
import java.util.Locale;

import javax.print.*;

class PrintDlgApp implements Printable {
        /**
         * Constructor
         */
        public PrintDlgApp() {
                super();
        }
        /**
         * Starts the application.
         */
        public static void main(java.lang.String[] args) {
                PrintDlgApp pd = new PrintDlgApp();
                PrinterJob pj = PrinterJob.getPrinterJob();
                System.out.println(pj);
                PrintRequestAttributeSet pSet = new HashPrintRequestAttributeSet();
                pSet.add(new Copies(1));
                //PageFormat pf = pj.pageDialog(pSet);
                PageFormat pf = new PageFormat();
                System.out.println("Setting Printable...pf = "+pf);
                if (pf == null) {
                    return;
                }
                pj.setPrintable(pd,pf);

                //try { pj.setPrintService(services[0]); } catch(Exception e) { e.printStackTrace(); }
                pSet.add(new Destination(new java.io.File("./out.prn").toURI()));
                System.out.println("open PrintDialog..");
                for (int i=0; i<2; i++) {
                if (pj.printDialog(pSet)) {
                        try {
                                System.out.println("About to print the data ...");
                                pj.print(pSet);
                                System.out.println("Printed");
                        }
                        catch (PrinterException pe) {
                                pe.printStackTrace();
                        }
                }
                }

        }

        //printable interface
        public int print(Graphics g, PageFormat pf, int pi) throws
PrinterException {

                if (pi > 0) {
                        System.out.println("pi is greater than 0");
                        return Printable.NO_SUCH_PAGE;
                }
                // Simply draw two rectangles
                Graphics2D g2 = (Graphics2D)g;
                g2.setColor(Color.black);
                g2.translate(pf.getImageableX(), pf.getImageableY());
                g2.drawRect(1,1,200,300);
                g2.drawRect(1,1,25,25);
                System.out.println("print method called "+pi);
                return Printable.PAGE_EXISTS;
        }
}
