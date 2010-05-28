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
 * test
 * @bug 6497426
 * @summary Tests that pressing of Ctrl+ascii mostly fires KEY_TYPED with a Unicode control symbols
 * @author Yuri.Nesterenko@... area=awt.keyboard
 * @run applet CtrlASCII.html
 */

// Note there is no @ in front of test above.  This is so that the
//  harness will not mistake this file as a test file.  It should
//  only see the html file as a test file. (the harness runs all
//  valid test files, so it would run this test twice if this file
//  were valid as well as the html file.)
// Also, note the area= after Your Name in the author tag.  Here, you
//  should put which functional area the test falls in.  See the
//  AWT-core home page -> test areas and/or -> AWT team  for a list of
//  areas.
// Note also the 'RobotLWTest.html' in the run tag.  This should
//  be changed to the name of the test.


/**
 * CtrlASCII.java
 *
 * @summary Tests that pressing of Ctrl+ascii mostly fires KEY_TYPED with a Unicode control symbols
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.util.*;


//
// In this test, a key listener for KEY_TYPED checks if a character typed has
// a correspondent keycode in an initially filled hashtable.
// If it does not, test fails. If character was produced by
// pressing a wrong key still listed in the hashtable, test cannot detect it.
// Under MS Windows, unlike X Window, some Ctrl+Ascii keystrokes don't
// produce a unicode character, so there will be no KEY_TYPED and no problem.
// Test doesn't try to verify Ctrl+deadkey behavior.
//

public class CtrlASCII extends Applet implements KeyListener
{
    // Declare things used in the test, like buttons and labels here
    static Hashtable<Character, Integer> keycharHash = new Hashtable<Character, Integer>();
    static boolean testFailed = false;
    //Frame frame;
    TextField tf;
    Robot robot;

    static void fillHash( boolean isMSWindows ) {
        keycharHash.put(    (char)0x20         , KeyEvent.VK_SPACE        );                      /*32,x20*/ /*' ' */
        keycharHash.put(    (char)0x21         , KeyEvent.VK_EXCLAMATION_MARK        );           /*33,x21*/ /*'!' fr*/
        keycharHash.put(    (char)0x22         , KeyEvent.VK_QUOTEDBL        );                   /*34,x22*/ /*'"' fr*/
        keycharHash.put(    (char)0x23         , KeyEvent.VK_NUMBER_SIGN        );                /*35,x23*/ /*'#' de*/
        keycharHash.put(    (char)0x24         , KeyEvent.VK_DOLLAR        );                      /*36,x24*/ /*'$', de_CH*/
        //keycharHash.put('%',                                  (char)0x25        );                                  /*37,x25*/ /*no VK, cannot test*/
        keycharHash.put(    (char)0x26    , KeyEvent.VK_AMPERSAND        );                  /*38,x26*/ /*'&', fr*/
        keycharHash.put(    (char)0x27    , KeyEvent.VK_QUOTE        );                      /*39,x27*/ /*''', fr*/
        keycharHash.put(    (char)0x28    , KeyEvent.VK_LEFT_PARENTHESIS        );           /*40,x28*/ /*'(', fr*/
        keycharHash.put(    (char)0x29    , KeyEvent.VK_RIGHT_PARENTHESIS        );           /*41,x29*/ /*')', fr*/
        keycharHash.put(    (char)0x2a    , KeyEvent.VK_ASTERISK        );                    /*42,x2a*/ /*'*', fr*/
        keycharHash.put(    (char)0x2b    , KeyEvent.VK_PLUS        );                        /*43,x2b*/ /*'+', de*/
        keycharHash.put(    (char)0x2c    , KeyEvent.VK_COMMA        );                       /*44,x2c*/  /*','*/
        keycharHash.put(    (char)0x2d    , KeyEvent.VK_MINUS        );                       /*45,x2d*/ /*'-'*/
        keycharHash.put(    (char)0x2e    , KeyEvent.VK_PERIOD        );                      /*46,x2e*/ /*'.'*/
        keycharHash.put(    (char)0x2f    , KeyEvent.VK_SLASH        );                       /*47,x2f*/ /*'/'*/
        keycharHash.put(    (char)0x30    , KeyEvent.VK_0        );                           /*48,x30*/
        keycharHash.put(    (char)0x31    , KeyEvent.VK_1        );                           /*49,x31*/
        keycharHash.put(    (char)0x32    , KeyEvent.VK_2        );                           /*50,x32*/
        keycharHash.put(    (char)0x33    , KeyEvent.VK_3        );                           /*51,x33*/
        keycharHash.put(    (char)0x34    , KeyEvent.VK_4        );                           /*52,x34*/
        keycharHash.put(    (char)0x35    , KeyEvent.VK_5        );                           /*53,x35*/
        keycharHash.put(    (char)0x36    , KeyEvent.VK_6        );                           /*54,x36*/
        keycharHash.put(    (char)0x37    , KeyEvent.VK_7        );                           /*55,x37*/
        keycharHash.put(    (char)0x38    , KeyEvent.VK_8        );                           /*56,x38*/
        keycharHash.put(    (char)0x39    , KeyEvent.VK_9        );                           /*57,x39*/
        keycharHash.put(    (char)0x3a    , KeyEvent.VK_COLON        );                       /*58,x3a*/ /*':', fr*/
        keycharHash.put(    (char)0x3b    , KeyEvent.VK_SEMICOLON        );                   /*59,x3b*/ /*';'*/
        keycharHash.put(    (char)0x3c    , KeyEvent.VK_LESS        );                        /*60,x3c*/ /*'<' us 102*/
        keycharHash.put(    (char)0x3d    , KeyEvent.VK_EQUALS        );                      /*61,x3d*/
        keycharHash.put(    (char)0x3e    , KeyEvent.VK_GREATER        );                     /*62,x3e*/ /*'>' ?????? where???*/
            // Javadoc says: "there is no keycode for the question mark because
            // there is no keyboard for which it appears on the primary layer."
            // Well, it's Lithuanian standard.
        //keycharHash.put('?',                                 (char)0x3f        );                                   /*63,x3f*/ /*no VK, cannot test*/
        keycharHash.put(    (char)0x40   , KeyEvent.VK_AT        );                          /*64,x40*/ /*'@' ?????? where???*/
        keycharHash.put(    (char)0x1    , KeyEvent.VK_A        );                             /*65,x41*/
        keycharHash.put(    (char)0x2    , KeyEvent.VK_B        );                            /*66,x42*/
        keycharHash.put(    (char)0x3    , KeyEvent.VK_C        );                            /*67,x43*/
        keycharHash.put(    (char)0x4    , KeyEvent.VK_D        );                            /*68,x44*/
        keycharHash.put(    (char)0x5    , KeyEvent.VK_E        );                            /*69,x45*/
        keycharHash.put(    (char)0x6    , KeyEvent.VK_F        );                            /*70,x46*/
        keycharHash.put(    (char)0x7    , KeyEvent.VK_G        );                            /*71,x47*/
        keycharHash.put(    (char)0x8    , KeyEvent.VK_H        );                            /*72,x48*/
        keycharHash.put(    (char)0x9    , KeyEvent.VK_I        );                            /*73,x49*/
        keycharHash.put(    (char)0xa    , KeyEvent.VK_J        );                            /*74,x4a*/
        keycharHash.put(    (char)0xb    , KeyEvent.VK_K        );                            /*75,x4b*/
        keycharHash.put(    (char)0xc    , KeyEvent.VK_L        );                            /*76,x4c*/
        keycharHash.put(    (char)0xd    , KeyEvent.VK_M        );                            /*77,x4d*/
        keycharHash.put(    (char)0xe    , KeyEvent.VK_N        );                            /*78,x4e*/
        keycharHash.put(    (char)0xf    , KeyEvent.VK_O        );                            /*79,x4f*/
        keycharHash.put(    (char)0x10   , KeyEvent.VK_P        );                           /*80,x50*/
        keycharHash.put(    (char)0x11   , KeyEvent.VK_Q        );                           /*81,x51*/
        keycharHash.put(    (char)0x12   , KeyEvent.VK_R        );                           /*82,x52*/
        keycharHash.put(    (char)0x13   , KeyEvent.VK_S        );                           /*83,x53*/
        keycharHash.put(    (char)0x14   , KeyEvent.VK_T        );                           /*84,x54*/
        keycharHash.put(    (char)0x15   , KeyEvent.VK_U        );                           /*85,x55*/
        keycharHash.put(    (char)0x16   , KeyEvent.VK_V        );                           /*86,x56*/
        keycharHash.put(    (char)0x17   , KeyEvent.VK_W        );                           /*87,x57*/
        keycharHash.put(    (char)0x18   , KeyEvent.VK_X        );                           /*88,x58*/
        keycharHash.put(    (char)0x19   , KeyEvent.VK_Y        );                           /*89,x59*/
        keycharHash.put(    (char)0x1a   , KeyEvent.VK_Z        );                           /*90,x5a*/

        keycharHash.put(    (char)0x1b   , KeyEvent.VK_OPEN_BRACKET        );             /*91,x5b*/ /*'['*/
        keycharHash.put(    (char)0x1c   , KeyEvent.VK_BACK_SLASH        );               /*92,x5c*/ /*'\'*/
        keycharHash.put(    (char)0x1d   , KeyEvent.VK_CLOSE_BRACKET        );            /*93,x5d*/ /*']'*/
        keycharHash.put(    (char)0x5e   , KeyEvent.VK_CIRCUMFLEX        );               /*94,x5e*/  /*'^' ?? nodead fr, de??*/
        keycharHash.put(    (char)0x1f   , KeyEvent.VK_UNDERSCORE        );               /*95,x5f*/  /*'_' fr*/
        keycharHash.put(    (char)0x60   , KeyEvent.VK_BACK_QUOTE        );               /*96,x60*/
        /********* Same as uppercase*/
        //keycharHash.put(  (char)0x1         , KeyEvent.VK_a        );/*97,x61*/
        //keycharHash.put(  (char)0x2         , KeyEvent.VK_b        );/*98,x62*/
        //keycharHash.put(  (char)0x3         , KeyEvent.VK_c        );/*99,x63*/
        //keycharHash.put(  (char)0x4         , KeyEvent.VK_d        );/*100,x64*/
        //keycharHash.put(  (char)0x5         , KeyEvent.VK_e        );/*101,x65*/
        //keycharHash.put(  (char)0x6         , KeyEvent.VK_f        );/*102,x66*/
        //keycharHash.put(  (char)0x7         , KeyEvent.VK_g        );/*103,x67*/
        //keycharHash.put(  (char)0x8         , KeyEvent.VK_h        );/*104,x68*/
        //keycharHash.put(  (char)0x9         , KeyEvent.VK_i        );/*105,x69*/
        //keycharHash.put(  (char)0xa         , KeyEvent.VK_j        );/*106,x6a*/
        //keycharHash.put(  (char)0xb         , KeyEvent.VK_k        );/*107,x6b*/
        //keycharHash.put(  (char)0xc         , KeyEvent.VK_l        );/*108,x6c*/
        //keycharHash.put(  (char)0xd         , KeyEvent.VK_m        );/*109,x6d*/
        //keycharHash.put(  (char)0xe         , KeyEvent.VK_n        );/*110,x6e*/
        //keycharHash.put(  (char)0xf         , KeyEvent.VK_o        );/*111,x6f*/
        //keycharHash.put(  (char)0x10        , KeyEvent.VK_p        );/*112,x70*/
        //keycharHash.put(  (char)0x11        , KeyEvent.VK_q        );/*113,x71*/
        //keycharHash.put(  (char)0x12        , KeyEvent.VK_r        );/*114,x72*/
        //keycharHash.put(  (char)0x13        , KeyEvent.VK_s        );/*115,x73*/
        //keycharHash.put(  (char)0x14        , KeyEvent.VK_t        );/*116,x74*/
        //keycharHash.put(  (char)0x15        , KeyEvent.VK_u        );/*117,x75*/
        //keycharHash.put(  (char)0x16        , KeyEvent.VK_v        );/*118,x76*/
        //keycharHash.put(  (char)0x17        , KeyEvent.VK_w        );/*119,x77*/
        //keycharHash.put(  (char)0x18        , KeyEvent.VK_x        );/*120,x78*/
        //keycharHash.put(  (char)0x19        , KeyEvent.VK_y        );/*121,x79*/
        //keycharHash.put(  (char)0x1a        , KeyEvent.VK_z        );/*122,x7a*/

        keycharHash.put(    (char)0x7b      , KeyEvent.VK_BRACELEFT        );             /*123,x7b*/ /*'{' la (Latin American)*/
        //keycharHash.put(  (char)0x1c        , KeyEvent.VK_|        );                   /*124,x7c*/ /* no VK, cannot test*/
        keycharHash.put(    (char)0x7d      , KeyEvent.VK_BRACERIGHT        );            /*125,x7d*/ /*'}' la */
        //keycharHash.put(  (char)0x1e        , KeyEvent.VK_~        );                   /*126,x7e*/ /* no VK, cannot test*/


    }
    public static void main(String[] args) {
        CtrlASCII test = new CtrlASCII();
        test.init();
        test.start();
    }

    public void init()
    {
        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        // XXX test for MS Windows
        fillHash( false );
        this.setLayout (new BorderLayout ());

        String[] instructions =
        {
            "This is an AUTOMATIC test",
            "simply wait until it is done"
        };
        Sysout.createDialog( );
        Sysout.printInstructions( instructions );

    }//End  init()

    public void start ()
    {
        //Get things going.  Request focus, set size, et cetera

        setSize(400,300);
        setVisible(true);

        //What would normally go into main() will probably go here.
        //Use System.out.println for diagnostic messages that you want
        //to read after the test is done.
        //Use Sysout.println for messages you want the tester to read.

        String original = "0123456789";
        tf = new TextField(original, 20);
        this.add(tf);
        tf.addKeyListener(this);
        validate();

        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(100);

            robot.waitForIdle();

            // wait for focus, etc.  (Hack.)
            robot.delay(2000);
            this.requestFocus();
            tf.requestFocusInWindow();

            Point pt = getLocationOnScreen();
            robot.mouseMove( pt.x+100, pt.y+100 );
            robot.delay(2000);
            robot.mousePress( InputEvent.BUTTON1_MASK );
            robot.mouseRelease( InputEvent.BUTTON1_MASK );
            Enumeration<Integer> enuElem = keycharHash.elements();

            int kc;
            while( enuElem.hasMoreElements()) {
                kc = enuElem.nextElement();
                punchCtrlKey( robot, kc );
            }
            robot.delay(500);
        } catch (Exception e) {
            throw new RuntimeException("The test was not completed.\n\n" + e);
        }
        if( testFailed ) {
            throw new RuntimeException("The test failed.\n\n");
        }
        Sysout.println("Success\n");

    }// start()
    public void punchCtrlKey( Robot ro, int keyCode ) {
        ro.keyPress(KeyEvent.VK_CONTROL);
        ro.keyPress(keyCode);
        ro.keyRelease(keyCode);
        ro.keyRelease(KeyEvent.VK_CONTROL);
        ro.delay(200);
    }
    public void keyPressed(KeyEvent evt)
    {
        //printKey(evt);
    }

    public void keyTyped(KeyEvent evt)
    {
        printKey(evt);
        char keych = evt.getKeyChar();
        if( !keycharHash.containsKey( keych ) ) {
            System.out.println("Unexpected keychar: "+keych);
            Sysout.println("Unexpected keychar: "+keych);
            testFailed = true;
        }
    }

    public void keyReleased(KeyEvent evt)
    {
        //printKey(evt);
    }

    protected void printKey(KeyEvent evt)
    {
        switch(evt.getID())
        {
          case KeyEvent.KEY_TYPED:
          case KeyEvent.KEY_PRESSED:
          case KeyEvent.KEY_RELEASED:
            break;
          default:
            System.out.println("Other Event ");
            Sysout.println("Other Event ");
            return;
        }
        System.out.print(" 0x"+ Integer.toHexString(evt.getKeyChar()));
        Sysout.println    (" 0x"+ Integer.toHexString(evt.getKeyChar()));
    }

}// class CtrlASCII


/****************************************************
 Standard Test Machinery
 DO NOT modify anything below -- it's a standard
  chunk of code whose purpose is to make user
  interaction uniform, and thereby make it simpler
  to read and understand someone else's test.
 ****************************************************/

/**
 This is part of the standard test machinery.
 It creates a dialog (with the instructions), and is the interface
  for sending text messages to the user.
 To print the instructions, send an array of strings to Sysout.createDialog
  WithInstructions method.  Put one line of instructions per array entry.
 To display a message for the tester to see, simply call Sysout.println
  with the string to be displayed.
 This mimics System.out.println but works within the test harness as well
  as standalone.
 */

class Sysout
 {
   private static TestDialog dialog;

   public static void createDialogWithInstructions( String[] instructions )
    {
      dialog = new TestDialog( new Frame(), "Instructions" );
      dialog.printInstructions( instructions );
      dialog.show();
      println( "Any messages for the tester will display here." );
    }

   public static void createDialog( )
    {
      dialog = new TestDialog( new Frame(), "Instructions" );
      String[] defInstr = { "Instructions will appear here. ", "" } ;
      dialog.printInstructions( defInstr );
      dialog.show();
      println( "Any messages for the tester will display here." );
    }


   public static void printInstructions( String[] instructions )
    {
      dialog.printInstructions( instructions );
    }


   public static void println( String messageIn )
    {
      dialog.displayMessage( messageIn );
    }

 }// Sysout  class

/**
  This is part of the standard test machinery.  It provides a place for the
   test instructions to be displayed, and a place for interactive messages
   to the user to be displayed.
  To have the test instructions displayed, see Sysout.
  To have a message to the user be displayed, see Sysout.
  Do not call anything in this dialog directly.
  */
class TestDialog extends Dialog
 {

   TextArea instructionsText;
   TextArea messageText;
   int maxStringLength = 80;

   //DO NOT call this directly, go through Sysout
   public TestDialog( Frame frame, String name )
    {
      super( frame, name );
      int scrollBoth = TextArea.SCROLLBARS_BOTH;
      instructionsText = new TextArea( "", 15, maxStringLength, scrollBoth );
      add( "North", instructionsText );

      messageText = new TextArea( "", 5, maxStringLength, scrollBoth );
      add("South", messageText);

      pack();

      show();
    }// TestDialog()

   //DO NOT call this directly, go through Sysout
   public void printInstructions( String[] instructions )
    {
      //Clear out any current instructions
      instructionsText.setText( "" );

      //Go down array of instruction strings

      String printStr, remainingStr;
      for( int i=0; i < instructions.length; i++ )
       {
         //chop up each into pieces maxSringLength long
         remainingStr = instructions[ i ];
         while( remainingStr.length() > 0 )
          {
            //if longer than max then chop off first max chars to print
            if( remainingStr.length() >= maxStringLength )
             {
               //Try to chop on a word boundary
               int posOfSpace = remainingStr.
                  lastIndexOf( ' ', maxStringLength - 1 );

               if( posOfSpace <= 0 ) posOfSpace = maxStringLength - 1;

               printStr = remainingStr.substring( 0, posOfSpace + 1 );
               remainingStr = remainingStr.substring( posOfSpace + 1 );
             }
            //else just print
            else
             {
               printStr = remainingStr;
               remainingStr = "";
             }

            instructionsText.append( printStr + "\n" );

          }// while

       }// for

    }//printInstructions()

   //DO NOT call this directly, go through Sysout
   public void displayMessage( String messageIn )
    {
      messageText.append( messageIn + "\n" );
    }

 }// TestDialog  class
