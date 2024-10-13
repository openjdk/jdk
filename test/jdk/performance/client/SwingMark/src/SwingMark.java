/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.RepaintManager;
import javax.swing.UIManager;

/**
  * This class runs the SwingMark benchmarks as an application
  * Simply invoke this class' main() to run the test suite.
  * Optionally you can use the name of a subclass of LookAndFeel
  * as an arguement to main().  This will use that L&F for the test.
  */

public class SwingMark {

   static SwingMarkPanel mainPanel;
   static Date startTime;
   static Date startupCompleteTime;
   static Date endTime;
   static int numRepeats = 1;
   static boolean autoQuit = false;
   static boolean sleepBetweenRuns = false;
   static boolean useBlitScrolling = false;

   static long[][] timeReport;
   static long[][] memoryReport;

   static String reportFileName = null;
   static String memoryReportFileName = null;


   @SuppressWarnings("deprecation")
   public static void initFrame(JFrame frame) {
      mainPanel = new SwingMarkPanel();
      prepReports();
      frame.getContentPane().add(mainPanel);
      frame.pack();
      frame.show();
   }

   protected static void prepReports() {
      if (timeReport == null) {
         timeReport = new long[numRepeats][mainPanel.tests.length];
      }
      if (memoryReport == null) {
         memoryReport = new long[numRepeats][2];
      }
   }

   public static void main(String[] args) {

      System.out.println("Starting SwingMark");
      startTime = new Date();
      System.out.println("SwingMark Test started at " + startTime);

      parseArgs(args);

      JFrame f = new JFrame("SwingMarks");
      Thread.currentThread().setPriority(Thread.NORM_PRIORITY-1);
      f.addWindowListener( new Closer() );

      initFrame(f);
      Date startupCompleteTime = new Date();
      long elapsedTime = startupCompleteTime.getTime() - startTime.getTime();
      System.out.println("Startup Time: "+elapsedTime);


      //int repeat = 15;
      for (int i = 0; i < numRepeats; i++) {
         mainPanel.runTests(i);
         if (i < numRepeats - 1) {
            f.setVisible(false);
            f.dispose();
            AbstractSwingTest.rest();
            f = new JFrame("SwingMarks " + (i+2));
            initFrame(f);
            f.addWindowListener( new Closer() );
            System.out.println(" **** Starting run " + (i+2) + "****");
            maybeSleep();
         }
      }

      Date endTime = new Date();
      elapsedTime = endTime.getTime() - startTime.getTime();
      System.out.println("Score: "+elapsedTime);

      writeReport();
      writeMemoryReport();

      if (autoQuit) {
         System.exit(0);
      }
   }

   static void maybeSleep() {
      if (sleepBetweenRuns) {
         for (int i = 0; i < 10; i++) {
            Toolkit.getDefaultToolkit().beep();
            try {
               AbstractSwingTest.syncRam();
               Thread.sleep(900);
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }
   static class Closer extends WindowAdapter {
      public void windowClosing(WindowEvent e) {
         System.exit(0);
      }
   }

   @SuppressWarnings("deprecation")
   private static void parseArgs(String[] args) {
      String lafName = UIManager.getCrossPlatformLookAndFeelClassName();
      int lfOpts = 0;
      int nOpts = 0;

      for (int i = 0; i < args.length; i++) {

         if (args[i].indexOf("-lf") == 0) {
            lafName = args[i+1];//.substring(3);
            lfOpts = 1;
            i++;
         } else if (args[i].indexOf("-n") == 0) {
            // use native look and feel
            lafName = UIManager.getSystemLookAndFeelClassName();
            nOpts++;
         } else if (args[i].indexOf("-r") == 0) {
            String repeatString = args[i+1];//.substring(2);
            numRepeats = Integer.parseInt(repeatString);
            System.out.println("Will run test " + numRepeats + " times in the same VM");
            i++;
         } else if (args[i].equals("-q")) {
            autoQuit = true;
            System.out.println("Program will automatically terminate after last run");
         } else if (args[i].equals("-f")) {
            reportFileName = args[i+1];
            if (reportFileName.indexOf("-mmdd") != -1) {
               Date date = new Date();
               int startpos = reportFileName.indexOf("-mmdd");
               reportFileName =
               reportFileName.substring(0,startpos)+(date.getMonth()+1)+"-"+
                     date.getDate()+reportFileName.substring(startpos+5);
            }
            i++;
            System.out.println("Will write test report to file: "+ reportFileName);
         } else if (args[i].equals("-m")) {
            memoryReportFileName = args[i+1];
            i++;
            System.out.println("Will write memory report to file: "+ memoryReportFileName);
         } else if (args[i].equals("-db=off")) {
            RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
            System.out.println("Will run without double buffering");
         } else if (args[i].equals("-sleep")) {
            sleepBetweenRuns = true;
            System.out.println("Will sleep for 5 seconds between runs");
         } else if (args[i].equals("-blit")) {
            useBlitScrolling = true;
            System.out.println("Will use fast window blitting");
         } else if (args[i].equals("-version")) {
            System.out.println("SwingMark build Oct 28, 2005");
         } else {
            System.out.println("Unexpected Argument: " + args[i]);
            System.exit(1);
         }
      }
      if (lfOpts + nOpts > 1) {
        System.out.println("-lf and -n are mutually exclusive\n");
        System.exit(1);
      }
      switchLookAndFeel(lafName);
   }

   private static void switchLookAndFeel(String lafName) {
      try {
         System.out.println("Setting L&F to: "+ lafName);
         UIManager.setLookAndFeel(lafName);

      } catch (Exception e) {
         System.out.println(e);
         System.exit(1);
      }
   }

   protected static void writeReportHeader(PrintWriter writer) {
      writer.println("<REPORT>");

      writer.println("<NAME>SwingMark</NAME>");
      writer.println();
      writer.println("<DATE>" + startTime + "</DATE>");
      writer.println("<VERSION>" + System.getProperty("java.version") + "</VERSION>");
      writer.println("<VENDOR>" + System.getProperty("java.vendor") + "</VENDOR>");
      writer.println("<DIRECTORY>" + System.getProperty("java.home") + "</DIRECTORY>");

      String vmName = System.getProperty("java.vm.name");
      String vmVersion = System.getProperty("java.vm.info");

      String vmString = "Undefined";
      if (vmName != null && vmVersion != null) {
         vmString = vmName + " " + vmVersion;
      }
      writer.println("<VM_INFO>" + vmString + "</VM_INFO>");

      writer.print("<OS>" + System.getProperty("os.name") );
      writer.println(" version " + System.getProperty("os.version")+ "</OS>");

      int bits = java.awt.Toolkit.getDefaultToolkit().getColorModel().getPixelSize();
      writer.println("<BIT_DEPTH>" + bits + "</BIT_DEPTH>");

      writer.println();
   }

   protected static void writeReportFooter(PrintWriter writer) {
      writer.println("</REPORT>");
   }

   protected static void writeReport() {
      if (reportFileName != null) {
         try {
            System.out.println("Writing report to file: "+ reportFileName);
            FileWriter fileWriter = new FileWriter(reportFileName);
            PrintWriter writer = new PrintWriter(fileWriter);

            writeReportHeader(writer);

            writer.println("<DATA RUNS=\"" +numRepeats+
                           "\" TESTS=\"" + mainPanel.tests.length + "\" >");
            for (int testNumber =0; testNumber < mainPanel.tests.length; testNumber++) {
               writer.print(mainPanel.tests[testNumber].getTestName() + "\t");
               for (int runNumber = 0; runNumber < numRepeats; runNumber++) {
                  writer.print(timeReport[runNumber][testNumber]);
                  if (runNumber < numRepeats -1) {
                     writer.print("\t");
                  }
               }
               writer.println();
            }

            writer.println("</DATA>");

            writer.println();
            writeReportFooter(writer);

            writer.close();
            fileWriter.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   protected static void writeMemoryReport() {
      if (memoryReportFileName != null) {
         try {
            System.out.println("Writing memory report to file: "+ memoryReportFileName);
            FileWriter fileWriter = new FileWriter(memoryReportFileName);
            PrintWriter writer = new PrintWriter(fileWriter);
            writeReportHeader(writer);
            writer.println("Used Memory\tHeapSize");
            for (int runNumber = 0; runNumber < numRepeats; runNumber++) {
               writer.print(memoryReport[runNumber][0]);
               writer.print("\t");
               writer.println(memoryReport[runNumber][1]);
            }
            writer.println();
            writeReportFooter(writer);
            writer.close();
            fileWriter.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }
}
