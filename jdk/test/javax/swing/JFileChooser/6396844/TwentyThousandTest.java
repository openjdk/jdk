/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6396844
 * @summary Tests memory leak for 20000 files
 * @author Sergey Malenkov
 * @run main/othervm/timeout=1000 -mx256m TwentyThousandTest
 */

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;

public class TwentyThousandTest implements ActionListener, Runnable {

    private static final int FILES = 20000;
    private static final int ATTEMPTS = 100;
    private static final int INTERVAL = 100;

    private static final boolean ALWAYS_NEW_INSTANCE = false;
    private static final boolean UPDATE_UI_EACH_INTERVAL = true;
    private static final boolean AUTO_CLOSE_DIALOG = true;

    private static JFileChooser CHOOSER;

    private static String tmpDir;

    public static void main(String[] args) throws Exception {
        tmpDir = System.getProperty("java.io.tmpdir");

        if (tmpDir.length() == 0) { //'java.io.tmpdir' isn't guaranteed to be defined
            tmpDir = System.getProperty("user.home");
        }

        System.out.println("Temp directory: " + tmpDir);

        System.out.println("Creating " + FILES + " files");

        for (int i = 0; i < FILES; i++) {
            File file = getTempFile(i);

            FileWriter writer = new FileWriter(file);
            writer.write("File " + i);
            writer.close();
        }

        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            if (laf.getClassName().contains("Motif")) {
                continue;
            }

            UIManager.setLookAndFeel(laf.getClassName());

            System.out.println("Do " + ATTEMPTS + " attempts for " + laf.getClassName());

            for ( int i = 0; i < ATTEMPTS; i++ ) {
                System.out.print(i + " ");

                doAttempt();
            }

            System.out.println();

            CHOOSER = null;
        }

        System.out.println("Removing " + FILES + " files");

        for (int i = 0; i < FILES; i++) {
            getTempFile(i).delete();
        }

        System.out.println( "Test passed successfully" );
    }

    private static File getTempFile(int i) {
        return new File(tmpDir, "temp" + i + ".txt");
    }

    private static void doAttempt() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                if ( ALWAYS_NEW_INSTANCE || ( CHOOSER == null ) )
                    CHOOSER = new JFileChooser(tmpDir);

                if ( UPDATE_UI_EACH_INTERVAL )
                    CHOOSER.updateUI();

                if ( AUTO_CLOSE_DIALOG ) {
                    Thread t = new Thread( new TwentyThousandTest( CHOOSER ) );
                    t.start();
                    CHOOSER.showOpenDialog( null );
                } else {
                    CHOOSER.showOpenDialog( null );
                }
            }
        });

        // Allow to collect garbage by GC
        Thread.sleep(1000);

        System.gc();
    }

    private final JFileChooser chooser;

    TwentyThousandTest( JFileChooser chooser ) {
        this.chooser = chooser;
    }

    public void run() {
        while ( !this.chooser.isShowing() ) {
            try {
                Thread.sleep( 30 );
            } catch ( InterruptedException exception ) {
                exception.printStackTrace();
            }
        }
        Timer timer = new Timer( INTERVAL, this );
        timer.setRepeats( false );
        timer.start();
    }

    public void actionPerformed( ActionEvent event ) {
        this.chooser.cancelSelection();
    }
}
