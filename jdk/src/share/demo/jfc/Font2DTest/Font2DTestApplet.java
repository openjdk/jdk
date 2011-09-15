/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/*
 */

import java.awt.AWTPermission;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.*;

/**
 * Font2DTestApplet.java
 *
 * @author Shinsuke Fukuda
 * @author Ankit Patel [Conversion to Swing - 01/07/30]
 */

/// Applet version of Font2DTest that wraps the actual demo

public final class Font2DTestApplet extends JApplet {
    public void init() {
        /// Check if necessary permission is given...
        SecurityManager security = System.getSecurityManager();
        if ( security != null ) {
            try {
                security.checkPermission( new AWTPermission( "showWindowWithoutWarningBanner" ));
            }
            catch ( SecurityException e ) {
                System.out.println( "NOTE: showWindowWithoutWarningBanner AWTPermission not given.\n" +
                                    "Zoom window will contain warning banner at bottom when shown\n" );
            }
            try {
                security.checkPrintJobAccess();
            }
            catch ( SecurityException e ) {
                System.out.println( "NOTE: queuePrintJob RuntimePermission not given.\n" +
                                    "Printing feature will not be available\n" );
            }
        }

        final JFrame f = new JFrame( "Font2DTest" );
        final Font2DTest f2dt = new Font2DTest( f, true );
        f.addWindowListener( new WindowAdapter() {
            public void windowClosing( WindowEvent e ) { f.dispose(); }
        });

        f.getContentPane().add( f2dt );
        f.pack();
        f.show();
    }
}
