/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package build.tools.fontchecker;

import java.io.*;
import java.util.*;
import java.awt.event.*;
import sun.font.FontManager;

/**
 * FontChecker.
 *
 * <PRE>
 * This is a FontChecker program. This class is a "parent" process
 * which invokes a "child" process. The child process will test
 * series of fonts and may crash as it encounters invalid fonts.
 * The "parent" process must then interpret error codes passed to it
 * by the "child" process and restart the "child" process if necessary.
 *
 * usage: java FontChecker [-v] -o outputfile
 *
 *        -o is the name of the file to contains canonical path names of
 *           bad fonts that are identified. This file is not created if
 *           no bad fonts are found.
 *        -v verbose: prints progress messages.
 *
 * </PRE>
 *
 * @author Ilya Bagrak
 */
public class FontChecker implements ActionListener, FontCheckerConstants {

    /**
     * Output stream to subprocess.
     * Corresponds to the subprocess's System.in".
     */
    private PrintWriter procPipeOut;

    /**
     * Input stream from subprocess.
     * Corresponds to the subprocess's System.out".
     */
    private BufferedInputStream procPipeIn;

    /**
     * Child process.
     */
    private Process childProc;

    /**
     * Name of output file to write file names of bad fonts
     */
    private String outputFile;

    /**
     * Reference to currently executing thread.
     */
    private Thread currThread;

    /**
     * Timeout timer for a single font check
     */
    private javax.swing.Timer timeOne;

    /**
     * Timeout timer for all font checks
     */
    private javax.swing.Timer timeAll;

    /**
     * max time (in milliseconds) allowed for checking a single font.
     */
    private static int timeoutOne = 10000;

    /**
     * max time (in milliseconds) allowed for checking all fonts.
     */
    private static int timeoutAll = 120000;

    /**
     * Boolean flag indicating whether FontChecker is required to
     * check non-TrueType fonts.
     */
    private boolean checkNonTTF = false;

    /**
     * List of bad fonts found in the system.
     */
    private Vector badFonts = new Vector();

    /**
     * whether to print warnings messges etc to stdout/err
     * default is false
     */
    private static boolean verbose = false;

    /* Command to use to exec sub-process. */
    private static String javaCmd = "java";

    static void printlnMessage(String s) {
        if (verbose) {
            System.out.println(s);
        }
    }

    /**
     * Event handler for timer event.
     * <BR>
     * Stops the timer and interrupts the current thread which is
     * still waiting on I/O from the child process.
     * <BR><BR>
     * @param evt timer event
     */
    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == timeOne) {
            timeOne.stop();
            printlnMessage("Child timed out: killing");
            childProc.destroy();
        } else {
            doExit(); // went on too long (ie timeAll timed out).
        }
    }

    /**
     * Initializes a FontChecker.
     * <BR>
     * This method is usually called after an unrecoverable error has
     * been detected and a child process has  either crashed or is in bad
     * state. The method creates a new child process from
     * scratch and initializes it's input/output streams.
     */
    public void initialize() {
        try {
            if (childProc != null) {
                childProc.destroy();
            }
            String fileSeparator = System.getProperty("file.separator");
            String javaHome = System.getProperty("java.home");
            String classPath =  System.getProperty("java.class.path");
            classPath = "\"" + classPath + "\"";
            String opt = "-cp " + classPath + " -Dsun.java2d.fontpath=\"" +
                javaHome + fileSeparator + "lib" + fileSeparator + "fonts\"";

            /* command to exec the child process with the same JRE */
            String cmd =
                new String(javaHome + fileSeparator + "bin" +
                           fileSeparator + javaCmd +
                           " -XXsuppressExitMessage " + opt +
                           " com.sun.java2d.fontchecker.FontCheckDummy");
            printlnMessage("cmd="+cmd);
            childProc = Runtime.getRuntime().exec(cmd);

        } catch (IOException e) {
            printlnMessage("can't execute child process");
            System.exit(0);
        } catch (SecurityException e) {
            printlnMessage("Error: access denied");
            System.exit(0);
        }

        /* initialize input/output streams to/from child process */
        procPipeOut = new PrintWriter(childProc.getOutputStream());
        procPipeIn = new BufferedInputStream(childProc.getInputStream());

        try {
            int code = procPipeIn.read();
            if (code != CHILD_STARTED_OK) {
                printlnMessage("bad child process start status="+code);
                doExit();
            }
        } catch (IOException e) {
            printlnMessage("can't read child process start status unknown");
            doExit();
        }
    }

    private void doExit() {
        try {
            if (procPipeOut != null) {
                /* Tell the child to exit */
                procPipeOut.write(EXITCOMMAND+System.getProperty("line.separator"));
                procPipeOut.flush();
                procPipeOut.close();
            }
        } catch (Throwable t) {
        }
        System.exit(0);
    }

    /**
     * Tries to verify integrity of a font specified by a path.
     * <BR>
     * This method is used to test whether a font specified by the given
     * path is valid and does not crash the system.
     * <BR><BR>
     * @param fontPath a string representation of font path
     * to standard out during while this font is tried
     * @return returns <code>true</code> if font is OK, and
     * <code>false</code> otherwise.
     */
    public boolean tryFont(File fontFile) {
        int bytesRead = 0;
        String fontPath = fontFile.getAbsolutePath();

        printlnMessage("Checking font "+fontPath);

        /* store reference to the current thread, so that when the timer
         * fires it can be interrupted
         */
        currThread = Thread.currentThread();
        timeOne.restart();

        /* write a string command out to child process
         * The command is formed by appending whether to test non-TT fonts
         * and font path to be tested
         */
        String command = Integer.toString(checkNonTTF ? 1 : 0) +
                         fontPath +
                         System.getProperty("line.separator");
        procPipeOut.write(command);
        procPipeOut.flush();

        /* check if underlying stream has encountered an error after
         * command has been issued
         */
        if (procPipeOut.checkError()){
            printlnMessage("Error: font crashed");
            initialize();
            return false;
        }

        /* trying reading error code back from child process */
        try {
            bytesRead = procPipeIn.read();
        } catch(InterruptedIOException e) {
            /* A timeout timer fired before the operation completed */
            printlnMessage("Error: timeout occured");
            initialize();
            return false;
        } catch(IOException e) {
            /* there was an error reading from the stream */
            timeOne.stop();
            printlnMessage("Error: font crashed");
            initialize();
            return false;
        } catch (Throwable t) {
            bytesRead = ERR_FONT_READ_EXCPT;
        } finally {
          timeOne.stop();
        }

        if (bytesRead == ERR_FONT_OK) {
            printlnMessage("Font integrity verified");
            return true;
        } else if (bytesRead > 0) {

            switch(bytesRead){
            case ERR_FONT_NOT_FOUND:
                printlnMessage("Error: font not found!");
                break;
            case ERR_FONT_BAD_FORMAT:
                printlnMessage("Error: incorrect font format");
                break;
            case ERR_FONT_READ_EXCPT:
                printlnMessage("Error: exception reading font");
                break;
            case ERR_FONT_DISPLAY:
                printlnMessage("Error: can't display characters");
                break;
            case ERR_FONT_CRASH:
                printlnMessage("Error: font crashed");
                break;
            default:
                printlnMessage("Error: invalid error code:"+bytesRead);
                break;

            }
        } else if (bytesRead == ERR_FONT_EOS) {
            printlnMessage("Error: end of stream marker encountered");
        } else {
            printlnMessage("Error: invalid error code:"+bytesRead);
        }

        /* if we still haven't returned from this method, some error
         * condition has occured and it is safer to re-initialize
         */
        initialize();
        return false;
    }

    /**
     * Checks the integrity of all system fonts.
     * <BR>
     * This method goes through every font in system's font path and verifies
     * its integrity via the tryFont method.
     * <BR><BR>
     * @param restart <code>true</code> if checking of fonts should continue
     * after the first  bad font is found, and <code>false</code> otherwise
     * @return returns <code>true</code> if all fonts are valid,
     * <code>false</code> otherwise
     * @see #tryFont(String, boolean, boolean)
     */
    public boolean checkFonts(boolean restart) {

        /* file filter to filter out none-truetype font files */
        FontFileFilter fff = new FontFileFilter(checkNonTTF);
        boolean checkOk = true;

        /* get platform-independent font path. Note that this bypasses
         * the normal GraphicsEnvironment initialisation. In conjunction with
         * the headless setting above, so we want to add
         * java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
         * to trigger a more normal initialisation.
         */
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        String fontPath = FontManager.getFontPath(true);
        StringTokenizer st =
            new StringTokenizer(fontPath,
                                System.getProperty("path.separator"));

        /* some systems may have multiple font paths separated by
         * platform-dependent characters, so fontPath string needs to be
         * parsed
         */
        timeOne = new javax.swing.Timer(timeoutOne, this);
        timeAll = new javax.swing.Timer(timeoutAll, this);
        timeAll.restart();
        while (st.hasMoreTokens()) {
            File fontRoot = new File(st.nextToken());
            File[] fontFiles = fontRoot.listFiles(fff);

            for (int i = 0; i < fontFiles.length; i++) {
                /* for each font file that is not a directory and passes
                 * through the font filter run the test
                 */
                if (!fontFiles[i].isDirectory() &&
                    !tryFont(fontFiles[i])) {

                    checkOk = false;
                    badFonts.add(fontFiles[i].getAbsolutePath());
                    if (!restart) {
                        break;
                    }
                }
            }
        }

        /* Tell the child to exit */
        procPipeOut.write(EXITCOMMAND+System.getProperty("line.separator"));
        procPipeOut.flush();
        procPipeOut.close();

        return checkOk;
    }

    public static void main(String args[]){
        try {
            /* Background app. */
            System.setProperty("java.awt.headless", "true");
            System.setProperty("sun.java2d.noddraw", "true");

            boolean restart = true;
            boolean errorFlag = false;

            FontChecker fc = new FontChecker();
            int arg = 0;

            while (arg < args.length && errorFlag == false) {
                if (args[arg].equals("-v")) {
                    verbose = true;
                }
                else if (args[arg].equals("-w") &&
                         System.getProperty("os.name", "unknown").
                         startsWith("Windows")) {
                    javaCmd = "javaw";
                }
                else if (args[arg].equals("-o")) {
                    /* set output file */
                    if (++arg < args.length)
                        fc.outputFile = args[arg];
                    else {
                        /* invalid argument format */
                        printlnMessage("Error: invalid argument format");
                        errorFlag = true;
                    }
                }
                else {
                    /* invalid command line argument */
                    printlnMessage("Error: invalid argument value");
                    errorFlag = true;
                }
                arg++;
            }

            if (errorFlag || fc.outputFile == null) {
                System.exit(0);
            }

            File outfile = new File(fc.outputFile);
            if (outfile.exists()) {
                outfile.delete();
            }

            fc.initialize();

            if (!fc.checkFonts(restart)) {
                String[] badFonts = (String[])fc.badFonts.toArray(new String[0]);
                if (badFonts.length > 0) {
                    printlnMessage("Bad Fonts:");
                    try {
                        FileOutputStream fos =
                            new FileOutputStream(fc.outputFile);
                        PrintStream ps = new  PrintStream(fos);
                        for (int i = 0; i < badFonts.length; i++) {
                            ps.println(badFonts[i]);
                            printlnMessage(badFonts[i]);
                        }
                        fos.close();
                    } catch (IOException e) {
                    }
                }
            } else {
                printlnMessage("No bad fonts found.");
        }
        } catch (Throwable t) {
        }
        System.exit(0);
    }
}
