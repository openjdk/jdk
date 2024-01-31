/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @key headful
  @bug  6497426
  @summary Tests that pressing of Ctrl+ascii mostly fires KEY_TYPED with a Unicode control symbols
  @run main/timeout=600 CtrlASCII
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;

//
// In this test, a key listener for KEY_TYPED checks if a character typed has
// a correspondent keycode in an initially filled hashtable.
// If it does not, test fails. If character was produced by
// pressing a wrong key still listed in the hashtable, test cannot detect it.
// Under MS Windows, unlike X Window, some Ctrl+Ascii keystrokes don't
// produce a unicode character, so there will be no KEY_TYPED and no problem.
// Test doesn't try to verify Ctrl+deadkey behavior.
//
public class CtrlASCII extends Frame implements KeyListener {
    // Declare things used in the test, like buttons and labels here
    static final HashMap<Character, Integer> KEYCHAR_MAP = new HashMap<>();
    static volatile boolean testFailed = false;

    TextField tf;
    Robot robot;

    static void fillHash() {
        KEYCHAR_MAP.put((char) 0x20, KeyEvent.VK_SPACE);             /*32,x20*/ /*' ' */
        KEYCHAR_MAP.put((char) 0x21, KeyEvent.VK_EXCLAMATION_MARK);  /*33,x21*/ /*'!' fr*/
        KEYCHAR_MAP.put((char) 0x22, KeyEvent.VK_QUOTEDBL);          /*34,x22*/ /*'"' fr*/
        KEYCHAR_MAP.put((char) 0x23, KeyEvent.VK_NUMBER_SIGN);       /*35,x23*/ /*'#' de*/
        KEYCHAR_MAP.put((char) 0x24, KeyEvent.VK_DOLLAR);            /*36,x24*/ /*'$', de_CH*/
        //keycharHash.put('%', (char)0x25 );                         /*37,x25*/ /*no VK, cannot test*/
        KEYCHAR_MAP.put((char) 0x26, KeyEvent.VK_AMPERSAND);         /*38,x26*/ /*'&', fr*/
        KEYCHAR_MAP.put((char) 0x27, KeyEvent.VK_QUOTE);             /*39,x27*/ /*''', fr*/
        KEYCHAR_MAP.put((char) 0x28, KeyEvent.VK_LEFT_PARENTHESIS);  /*40,x28*/ /*'(', fr*/
        KEYCHAR_MAP.put((char) 0x29, KeyEvent.VK_RIGHT_PARENTHESIS); /*41,x29*/ /*')', fr*/
        KEYCHAR_MAP.put((char) 0x2a, KeyEvent.VK_ASTERISK);          /*42,x2a*/ /*'*', fr*/
        KEYCHAR_MAP.put((char) 0x2b, KeyEvent.VK_PLUS);              /*43,x2b*/ /*'+', de*/
        KEYCHAR_MAP.put((char) 0x2c, KeyEvent.VK_COMMA);             /*44,x2c*/  /*','*/
        KEYCHAR_MAP.put((char) 0x2d, KeyEvent.VK_MINUS);             /*45,x2d*/ /*'-'*/
        KEYCHAR_MAP.put((char) 0x2e, KeyEvent.VK_PERIOD);            /*46,x2e*/ /*'.'*/
        KEYCHAR_MAP.put((char) 0x2f, KeyEvent.VK_SLASH);             /*47,x2f*/ /*'/'*/
        KEYCHAR_MAP.put((char) 0x30, KeyEvent.VK_0);                 /*48,x30*/
        KEYCHAR_MAP.put((char) 0x31, KeyEvent.VK_1);                 /*49,x31*/
        KEYCHAR_MAP.put((char) 0x32, KeyEvent.VK_2);                 /*50,x32*/
        KEYCHAR_MAP.put((char) 0x33, KeyEvent.VK_3);                 /*51,x33*/
        KEYCHAR_MAP.put((char) 0x34, KeyEvent.VK_4);                 /*52,x34*/
        KEYCHAR_MAP.put((char) 0x35, KeyEvent.VK_5);                 /*53,x35*/
        KEYCHAR_MAP.put((char) 0x36, KeyEvent.VK_6);                 /*54,x36*/
        KEYCHAR_MAP.put((char) 0x37, KeyEvent.VK_7);                 /*55,x37*/
        KEYCHAR_MAP.put((char) 0x38, KeyEvent.VK_8);                 /*56,x38*/
        KEYCHAR_MAP.put((char) 0x39, KeyEvent.VK_9);                 /*57,x39*/
        KEYCHAR_MAP.put((char) 0x3a, KeyEvent.VK_COLON);             /*58,x3a*/ /*':', fr*/
        KEYCHAR_MAP.put((char) 0x3b, KeyEvent.VK_SEMICOLON);         /*59,x3b*/ /*';'*/
        KEYCHAR_MAP.put((char) 0x3c, KeyEvent.VK_LESS);              /*60,x3c*/ /*'<' us 102*/
        KEYCHAR_MAP.put((char) 0x3d, KeyEvent.VK_EQUALS);            /*61,x3d*/
        KEYCHAR_MAP.put((char) 0x3e, KeyEvent.VK_GREATER);           /*62,x3e*/ /*'>' ?????? where???*/
        // Javadoc says: "there is no keycode for the question mark because
        // there is no keyboard for which it appears on the primary layer."
        // Well, it's Lithuanian standard.
        //keycharHash.put('?', (char)0x3f);                          /*63,x3f*/ /*no VK, cannot test*/
        KEYCHAR_MAP.put((char) 0x40, KeyEvent.VK_AT);                /*64,x40*/ /*'@' ?????? where???*/
        KEYCHAR_MAP.put((char) 0x1, KeyEvent.VK_A);                  /*65,x41*/
        KEYCHAR_MAP.put((char) 0x2, KeyEvent.VK_B);                  /*66,x42*/
        KEYCHAR_MAP.put((char) 0x3, KeyEvent.VK_C);                  /*67,x43*/
        KEYCHAR_MAP.put((char) 0x4, KeyEvent.VK_D);                  /*68,x44*/
        KEYCHAR_MAP.put((char) 0x5, KeyEvent.VK_E);                  /*69,x45*/
        KEYCHAR_MAP.put((char) 0x6, KeyEvent.VK_F);                  /*70,x46*/
        KEYCHAR_MAP.put((char) 0x7, KeyEvent.VK_G);                  /*71,x47*/
        KEYCHAR_MAP.put((char) 0x8, KeyEvent.VK_H);                  /*72,x48*/
        KEYCHAR_MAP.put((char) 0x9, KeyEvent.VK_I);                  /*73,x49*/
        KEYCHAR_MAP.put((char) 0xa, KeyEvent.VK_J);                  /*74,x4a*/
        KEYCHAR_MAP.put((char) 0xb, KeyEvent.VK_K);                  /*75,x4b*/
        KEYCHAR_MAP.put((char) 0xc, KeyEvent.VK_L);                  /*76,x4c*/
        KEYCHAR_MAP.put((char) 0xd, KeyEvent.VK_M);                  /*77,x4d*/
        KEYCHAR_MAP.put((char) 0xe, KeyEvent.VK_N);                  /*78,x4e*/
        KEYCHAR_MAP.put((char) 0xf, KeyEvent.VK_O);                  /*79,x4f*/
        KEYCHAR_MAP.put((char) 0x10, KeyEvent.VK_P);                 /*80,x50*/
        KEYCHAR_MAP.put((char) 0x11, KeyEvent.VK_Q);                 /*81,x51*/
        KEYCHAR_MAP.put((char) 0x12, KeyEvent.VK_R);                 /*82,x52*/
        KEYCHAR_MAP.put((char) 0x13, KeyEvent.VK_S);                 /*83,x53*/
        KEYCHAR_MAP.put((char) 0x14, KeyEvent.VK_T);                 /*84,x54*/
        KEYCHAR_MAP.put((char) 0x15, KeyEvent.VK_U);                 /*85,x55*/
        KEYCHAR_MAP.put((char) 0x16, KeyEvent.VK_V);                 /*86,x56*/
        KEYCHAR_MAP.put((char) 0x17, KeyEvent.VK_W);                 /*87,x57*/
        KEYCHAR_MAP.put((char) 0x18, KeyEvent.VK_X);                 /*88,x58*/
        KEYCHAR_MAP.put((char) 0x19, KeyEvent.VK_Y);                 /*89,x59*/
        KEYCHAR_MAP.put((char) 0x1a, KeyEvent.VK_Z);                 /*90,x5a*/

        KEYCHAR_MAP.put((char) 0x1b, KeyEvent.VK_OPEN_BRACKET);      /*91,x5b*/ /*'['*/
        KEYCHAR_MAP.put((char) 0x1c, KeyEvent.VK_BACK_SLASH);        /*92,x5c*/ /*'\'*/
        KEYCHAR_MAP.put((char) 0x1d, KeyEvent.VK_CLOSE_BRACKET);     /*93,x5d*/ /*']'*/
        KEYCHAR_MAP.put((char) 0x5e, KeyEvent.VK_CIRCUMFLEX);        /*94,x5e*/  /*'^' ?? nodead fr, de??*/
        KEYCHAR_MAP.put((char) 0x1f, KeyEvent.VK_UNDERSCORE);        /*95,x5f*/  /*'_' fr*/
        KEYCHAR_MAP.put((char) 0x60, KeyEvent.VK_BACK_QUOTE);        /*96,x60*/

        /********* Same as uppercase*/
        //keycharHash.put((char)0x1, KeyEvent.VK_a);                 /*97,x61*/
        //keycharHash.put((char)0x2, KeyEvent.VK_b);                 /*98,x62*/
        //keycharHash.put((char)0x3, KeyEvent.VK_c);                 /*99,x63*/
        //keycharHash.put((char)0x4, KeyEvent.VK_d);                 /*100,x64*/
        //keycharHash.put((char)0x5, KeyEvent.VK_e);                 /*101,x65*/
        //keycharHash.put((char)0x6, KeyEvent.VK_f);                 /*102,x66*/
        //keycharHash.put((char)0x7, KeyEvent.VK_g);                 /*103,x67*/
        //keycharHash.put((char)0x8, KeyEvent.VK_h);                 /*104,x68*/
        //keycharHash.put((char)0x9, KeyEvent.VK_i);                 /*105,x69*/
        //keycharHash.put((char)0xa, KeyEvent.VK_j);                 /*106,x6a*/
        //keycharHash.put((char)0xb, KeyEvent.VK_k);                 /*107,x6b*/
        //keycharHash.put((char)0xc, KeyEvent.VK_l);                 /*108,x6c*/
        //keycharHash.put((char)0xd, KeyEvent.VK_m);                 /*109,x6d*/
        //keycharHash.put((char)0xe, KeyEvent.VK_n);                 /*110,x6e*/
        //keycharHash.put((char)0xf, KeyEvent.VK_o);                 /*111,x6f*/
        //keycharHash.put((char)0x10, KeyEvent.VK_p);                /*112,x70*/
        //keycharHash.put((char)0x11, KeyEvent.VK_q);                /*113,x71*/
        //keycharHash.put((char)0x12, KeyEvent.VK_r);                /*114,x72*/
        //keycharHash.put((char)0x13, KeyEvent.VK_s);                /*115,x73*/
        //keycharHash.put((char)0x14, KeyEvent.VK_t);                /*116,x74*/
        //keycharHash.put((char)0x15, KeyEvent.VK_u);                /*117,x75*/
        //keycharHash.put((char)0x16, KeyEvent.VK_v);                /*118,x76*/
        //keycharHash.put((char)0x17, KeyEvent.VK_w);                /*119,x77*/
        //keycharHash.put((char)0x18, KeyEvent.VK_x);                /*120,x78*/
        //keycharHash.put((char)0x19, KeyEvent.VK_y);                /*121,x79*/
        //keycharHash.put((char)0x1a, KeyEvent.VK_z);                /*122,x7a*/

        KEYCHAR_MAP.put((char) 0x7b, KeyEvent.VK_BRACELEFT);         /*123,x7b*/ /*'{' la (Latin American)*/
        //keycharHash.put(char) 0x1c, KeyEvent.VK_|);                /*124,x7c*/ /* no VK, cannot test*/
        KEYCHAR_MAP.put((char) 0x7d, KeyEvent.VK_BRACERIGHT);        /*125,x7d*/ /*'}' la */
        //keycharHash.put((char) 0x1e, KeyEvent.VK_~);               /*126,x7e*/ /* no VK, cannot test*/
    }

    public static void main(String[] args) throws AWTException {
        CtrlASCII test = new CtrlASCII();
        test.init();
        test.start();
    }

    public void init() throws AWTException {
        fillHash();

        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(100);

        setLayout(new BorderLayout());

        setSize(400, 300);
        setLocationRelativeTo(null);
        setVisible(true);

        String original = "0123456789";
        tf = new TextField(original, 20);
        this.add(tf);
        tf.addKeyListener(this);
        validate();

        robot.waitForIdle();
        robot.delay(1000);

        this.requestFocus();
        tf.requestFocusInWindow();

        robot.waitForIdle();
    }//End  init()

    public void start() {
        try {
            Point pt = getLocationOnScreen();
            robot.mouseMove(pt.x + 100, pt.y + 100);
            robot.delay(1000);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            KEYCHAR_MAP.forEach((k, v) -> punchCtrlKey(v));

            robot.delay(500);
        } catch (Exception e) {
            throw new RuntimeException("The test was not completed.\n\n" + e);
        } finally {
            dispose();
        }
        if (testFailed) {
            throw new RuntimeException("The test failed.\n\n");
        }
        System.out.println("Success\n");

    }// start()

    public void punchCtrlKey(int keyCode) {
        try {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(keyCode);
        } catch (IllegalArgumentException iae) {
            System.err.println("skip probably invalid keyCode " + keyCode);
        } finally {
            try {
                robot.keyRelease(keyCode);
            } catch (IllegalArgumentException iae) {
                System.err.println("skip probably invalid keyCode " + keyCode);
            }
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }
        robot.delay(200);
    }

    public void keyPressed(KeyEvent evt) {
        //printKey(evt);
    }

    public void keyTyped(KeyEvent evt) {
        printKey(evt);
        char keych = evt.getKeyChar();
        if (!KEYCHAR_MAP.containsKey(keych)) {
            System.out.println("Unexpected keychar: " + keych);
            testFailed = true;
        }
    }

    public void keyReleased(KeyEvent evt) {
        //printKey(evt);
    }

    protected void printKey(KeyEvent evt) {
        switch (evt.getID()) {
            case KeyEvent.KEY_TYPED:
            case KeyEvent.KEY_PRESSED:
            case KeyEvent.KEY_RELEASED:
                break;
            default:
                System.out.println("Other Event ");
                return;
        }
        System.out.println(" 0x" + Integer.toHexString(evt.getKeyChar()));
    }

}// class CtrlASCII
