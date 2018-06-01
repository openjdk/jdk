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
import java.io.*;
import java.util.*;
import java.lang.*;
import java.lang.reflect.*;
import nsk.share.TestFailure;


public final class Globals
{
    // Minimum and Maximum number of threads
    public static int     NUM_THREADS      = 1;
    public static long    RANDOM_SEED      = System.currentTimeMillis();
    public static int     STATIC_LOOP      = 0;
    public static int     NUM_TEST_CLASSES = 7;
    public static long    RANDOM_LOOP      = 100;
    public static boolean VERBOSE          = false;
    private static Random indexGenerator   = null;

  //private static TestLoader CGTTestLoader = null;
    private static String [] ClassArray = null;
    private static Class [] ClassInstanceArray = null;
    private static int       maxClassIndex    = 0;

    private static String [] MethodName_Array = null;
    private static Method [] MethodInstance_Array = null;

    //Should be prime, so that odds of an incorrect verification reduced
    public static  int    [] MethodID_Array   = null;


    //Number of threads will be reduced as threads finish
    public static synchronized void decNumThreads(){NUM_THREADS--;};

    public static synchronized void initialize(String testListPath)
    {

        File td = new File (testListPath);
        if (!td.exists())
            {
                System.err.println("File " + testListPath + " Not found");
                System.exit(1);
            }
        if (!td.isFile())
            {
                System.err.println(testListPath + " Must be a File");
                System.exit(1);
            }

        BufferedReader classList = null;

        try
          {
            classList = new BufferedReader(new FileReader(td));
          }
        catch (FileNotFoundException  fnfx)
          {
            System.err.println("Error finding Classlist");
            System.exit(1);
          }

        String line = null;
        try
            {
                line = classList.readLine();
            }
        catch (IOException iox)
            {
                System.err.println("Error reading Classlist");
                System.exit(1);
            }

        try
            {
                maxClassIndex = Math.abs(Integer.parseInt(line));//ClassArray.length;
            }
        catch (NumberFormatException nfx)
            {
                System.err.println("Error reading Classlist - first number must be number of methods defined");
                System.exit(1);
            }

        ClassArray = new String [maxClassIndex];
ClassInstanceArray = new Class [maxClassIndex];
        MethodName_Array = new String [maxClassIndex];
        MethodInstance_Array = new Method [maxClassIndex];
        MethodID_Array = new int [maxClassIndex];

        int i;
        for (i = 0; (i<maxClassIndex) && (line != null); i++)
            {
                try
                    {
                        line = classList.readLine();
                    }
                catch (IOException iox)
                    {
                        System.err.println("Error reading ClasslistFile: testListPath");
                        System.exit(1);
                    }
                StringTokenizer lineTokens = new StringTokenizer(line, "\t ");
                if (lineTokens.countTokens() <3)
                  {
                    System.out.println("Error reading ClasslistFile: Errored line");
                    i--;
                  }
                else
                  {
                    ClassArray[i] = lineTokens.nextToken();
                    MethodName_Array[i] =lineTokens.nextToken();
                    MethodID_Array[i] = Integer.parseInt(lineTokens.nextToken());
                  }
            }
        maxClassIndex = i;

        indexGenerator = new Random(RANDOM_SEED);
        if ((NUM_TEST_CLASSES < ClassArray.length) && (NUM_TEST_CLASSES > 0))
          maxClassIndex = NUM_TEST_CLASSES;
        else
          NUM_TEST_CLASSES = maxClassIndex;
    }

    //does a binary serach to find the index for the ID of a method
    private static int ID_BinSearch(int begin, int end, int ID)
    {
        if (end < begin)
            return(-1);

        int mid = (begin + end)/2;
        int midvalue = MethodID_Array[mid];

        if (ID == midvalue)
            return (mid);
        else if (ID < midvalue)
            return(ID_BinSearch(begin, mid-1, ID));
        else
            return(ID_BinSearch(mid+1, end, ID));
    }


    //based off a static index, this function selects the method to be called
    public static MethodData returnNextStaticMethod(int Method_ID)
    {
      //int i = ID_BinSearch(0, MethodID_Array.length - 1, Method_ID);
      int i = ID_BinSearch(0, maxClassIndex - 1, Method_ID);

      return(nextStaticMethod((i==-1)?0:i));
    }

    //this function randomly selects the next method to be called by the test class
    public static MethodData nextRandomMethod()
    {

        int i = indexGenerator.nextInt(maxClassIndex);
        return(nextStaticMethod(i));
    }

    private static MethodData nextStaticMethod(int i)
    {
        Class methodsClass = null;
        Method nextMethod = null;

        try
            {
              //methodsClass = CGTTestLoader.findClass(ClassArray[i]);
              methodsClass = ClassInstanceArray[i];
              if (methodsClass == null)
              {
                  methodsClass = Class.forName(ClassArray[i]);
                  ClassInstanceArray[i] = methodsClass;
              }
              nextMethod = MethodInstance_Array[i];
              if (nextMethod == null )
              {
              nextMethod =
                methodsClass.getMethod(MethodName_Array[i],
                                       new Class[]{java.util.Vector.class, java.util.Vector.class,
                                                     java.lang.Long.class, java.lang.Integer.class});
              //sum vector, ID vector, function depth, static function call depth
              MethodInstance_Array[i] = nextMethod;
              }
            }
        catch (ClassNotFoundException cnfx)
            {
                System.out.println("Class: " +ClassArray[i]+ " Not Found");
                System.exit(-1);
            }
        catch (NoSuchMethodException nsmx)
            {
                System.out.println("Class: " +ClassArray[i]);
                System.out.println("Method: " +MethodName_Array[i]+" Not Found");
                System.exit(-1);
            }
        catch (SecurityException sx)
            {
                System.out.println("Class: " +ClassArray[i]);
                System.out.println("Method: " +MethodName_Array[i]);
                System.out.println("Security Exception Generated, by above method call");
                System.exit(-1);
            }
        return(new MethodData(ClassArray[i], MethodName_Array[i], methodsClass, nextMethod, MethodID_Array[i]));
    }


    /*These two functions are used to verify that all function were called in the proper order*/

    //called by "parent" function to add childs ID to vector
    public static void addFunctionIDToVector(int FunctionIndex, Vector IDVector)
    {
        IDVector.addElement(new Integer(FunctionIndex));
    }

    //called by "child" to add Function Index to Vector
    public static void appendSumToSumationVector(int FunctionIndex, Vector SummationVector)
    {
        if (SummationVector.isEmpty())
            SummationVector.addElement(new Long(FunctionIndex));
        else
            SummationVector.addElement(new Long(((Long)SummationVector.lastElement()).longValue() + FunctionIndex));
    }

    //This function calls a method based off of MethodData
    public static void callMethod(MethodData methodCallStr,
                                  Vector summation, Vector ID,
                                  Long numFcalls, Integer staticFcalls)
                                  throws InvocationTargetException

    {
                if(NUM_THREADS >1)
                    {
                        if ((staticFcalls.intValue() + numFcalls.longValue()) %23 == 0)
                            {
                                try
                                    {
                                        Thread.sleep(225);
                                    }
                                catch (InterruptedException ie)
                                    {}
                                if (VERBOSE)
                                    System.out.println("\t\tCurrentThread:" + Thread.currentThread().getName());
                            }
                    }

                try
            {
                        methodCallStr.nextMethod.invoke(methodCallStr.instance,
                                new Object []{summation, ID, numFcalls, staticFcalls});
            }
                catch (IllegalAccessException iax)  //should never happen with a valid testfile
            {
                        throw new TestFailure("Illegal Access Exception");
            }
                    /*
                catch (InvocationTargetException itx)
                    {
                        itx.printStackTrace();
                        System.out.println("Invocation Target Exception");
                        System.exit(1);
                    }*/
    }
}
