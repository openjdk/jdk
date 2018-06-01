/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jit.graph;
import java.util.*;
import nsk.share.TestFailure;
import nsk.share.test.StressOptions;

class CGT extends Thread
{
  private static StressOptions stressOptions = new StressOptions();
  private final static String           version = "1.0";
  private       static String       ClistPath = "";

  private static long finishTime;

  public CGT( String[] args )
  {
    parse (args);
    Globals.initialize(ClistPath);
    outputStats (args);
  }


  public static void main( String[] args )
  {
    stressOptions.parseCommandLine(args);
    CGT jnimt = new CGT(args);
    jnimt.start();
  }

  public void outputStats( String[] args )
  {
    System.out.println("CGT command line options:");
    for (int i=0; i < args.length; ++i )
            System.out.println("# " + args[i] );

    System.out.println();

    System.out.println("CGT parameters");
    System.out.println("Seed: " +Globals.RANDOM_SEED);
    System.out.println("Number of Threads: " +Globals.NUM_THREADS);
    System.out.println("Number of Random Loop iterations: " + Globals.RANDOM_LOOP);
    System.out.println("Number of Static Loop iterations: " + Globals.STATIC_LOOP);
    System.out.println("Max number of Methods in the Graph: " +  Globals.NUM_TEST_CLASSES);
    System.out.println("Verbose function calls: " + Globals.VERBOSE);

    System.out.println();
  }

  public void run()
  {
    finishTime = System.currentTimeMillis() + stressOptions.getTime() * 1000;

    for (int i = 0; i< Globals.NUM_THREADS; i++)
      new CGTThread("CGT Thread " + i).start();
  }

  public static boolean shouldFinish()
  {
     return System.currentTimeMillis() >= finishTime;
  }

  public void parse (String args[])
  {
    for (int i = 0; i<args.length; i++)
      {
        if ((args[i].equalsIgnoreCase("-help")) || (args[i].equalsIgnoreCase("-h")) || (args[i].equalsIgnoreCase("-?")))
          {
            usage ();
          }
        else if (args[i].equalsIgnoreCase("-version"))
          {
            version();
          }
        else if (args[i].equalsIgnoreCase("-seed"))
          {
            int argIndex = i+1;
            if (argIndex < args.length)
              {
                try
                  {
                    Globals.RANDOM_SEED = Math.abs(Long.parseLong(args[argIndex]));
                  }
                catch (NumberFormatException e)
                  {
                    System.out.println("Improper Argument: " + args[i] + " " + args[argIndex]);
                    usage ();
                  }
                i++;
              }
            else
              {
                System.out.println("Improper Argument: " + args[i]);
                usage ();
              }

          }
        else if ((args[i].equalsIgnoreCase("-thread")) || (args[i].equalsIgnoreCase("-threads")))
          {
            int argIndex = i+1;
            if (argIndex < args.length)
              {
                try
                  {
                    Globals.NUM_THREADS = Math.abs(Integer.parseInt(args[argIndex])) * stressOptions.getThreadsFactor();
                  }
                catch (NumberFormatException e)
                  {
                    System.out.println("Improper Argument: " + args[i] + " " + args[argIndex]);
                    usage ();
                  }
                if(Globals.NUM_THREADS == 0)
                  Globals.NUM_THREADS = 1;
                i++;
              }
            else
              {
                System.out.println("Improper Argument: " + args[i]);
                usage ();
              }

          }
        else if (args[i].equalsIgnoreCase("-staticLoop"))
          {
            int argIndex = i+1;
            if (argIndex < args.length)
              {
                try
                  {
                    Globals.STATIC_LOOP = Math.abs(Integer.parseInt(args[argIndex])) * stressOptions.getIterationsFactor();
                  }
                catch (NumberFormatException e)
                  {
                    System.out.println("Improper Argument: " + args[i] + " " + args[argIndex]);
                    usage ();
                  }
                i++;
              }
            else
              {
                System.out.println("Improper Argument: " + args[i]);
                usage ();
              }

          }
        else if (args[i].equalsIgnoreCase("-randomLoop"))
          {
            int argIndex = i+1;
            if (argIndex < args.length)
              {
                try
                  {
                    Globals.RANDOM_LOOP = Math.abs(Long.parseLong(args[argIndex])) * stressOptions.getIterationsFactor();
                  }
                catch (NumberFormatException e)
                  {
                    System.out.println("Improper Argument: " + args[i] + " " + args[argIndex]);
                    usage ();
                  }
                i++;
              }
            else
              {
                System.out.println("Improper Argument: " + args[i]);
                usage ();
              }

          }
        else if (args[i].equalsIgnoreCase("-numTestClass"))
          {
            int argIndex = i+1;
            if (argIndex < args.length)
              {
                try
                  {
                    Globals.NUM_TEST_CLASSES = Math.abs(Integer.parseInt(args[argIndex]));
                  }
                catch (NumberFormatException e)
                  {
                    System.out.println("Improper Argument: " + args[i] + " " + args[argIndex]);
                    usage ();
                  }
                i++;
              }
            else
              {
                System.out.println("Improper Argument: " + args[i]);
                usage ();
              }

          }
        else if (args[i].equalsIgnoreCase("-v") || args[i].equalsIgnoreCase("-verbose"))
          {
            Globals.VERBOSE = true;
          }
        else if (args[i].equalsIgnoreCase("-path"))
          {
            int argIndex = i+1;
            if (argIndex < args.length)
              {
                ClistPath = args[argIndex];
                i++;
              }
            else
              {
                System.out.println("Improper Argument: " + args[i]);
                usage ();
              }
          }
        else if (args[i].startsWith("-stress"))
          {
              break;
          }
        else
          {
            System.out.println("Invalid Argument: " + args[i]);
            usage ();
          }
        }
        if (ClistPath.equals(""))
        {
                System.out.println("class list path not defined");
                usage();
        }
  }

  public void usage ()
  {
    System.out.println("usage: java CGT [options]");
    System.out.println("  -help                               prints out this message");
    System.out.println("  -numTestClass #                     limits the number of \"Test Methods\" to #");
    System.out.println("  -randomcLoop #                      # of random function calls");
    System.out.println("  -seed #                             uses the specified seed rather than the System Time");
    System.out.println("  -staticLoop #                       # of non-random static function calls");
    System.out.println("  -threads #                          # number of test threads, NOTE: no maximum value");
    System.out.println("  -version                            print out the tool version");
    System.out.println("  -v -verbose                         turn on verbose mode");
    throw new TestFailure("  -path <path to classlist>           required, argument so program can find classes");
  }

  public void version ()
  {
    throw new TestFailure("CGT version = " + version);
  }
}
