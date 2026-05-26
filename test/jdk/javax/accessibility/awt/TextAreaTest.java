/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
 * @key headful
 * @summary TextArea Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main TextAreaTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextArea;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class TextAreaTest {

    private static TextArea textArea;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "TextArea Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, TextArea";
    private static final String TEST_DATA = """
            How to say "Oh my god! There's an axe in my head" in various languages:

             English:                Oh my god! There's an axe in my head.
             Bosnian:                Boze moj! sjekira mi je u glavi.
             French:                 Mon dieu! Il y a une hache dans ma tete.
             Visigothic:             Meina guth, Ikgastaldan aqizi-wunds meina haubida
             Swedish:                Ah, Herregud! Jag har en yxa i huvudet!
             Dutch:                  O, mijn God! Er zit een bijl in mijn hoofd.
             Latin:                  Deus Meus! Securis in capite meo est.
             German:                 Oh mein Gott! Ich habe eine Axt im Kopf!
             Japanese:               ahh, kamisama! watashi no atama ni ono ga arimasu.
             Norwegian:              Herre Gud! Jeg har en aks i hodet!
             Spanish:                Dios mio!  Hay una hacha en mi cabeza!
             Hungarian:              Jaj Istenem, de fejsze van a fejemben!!
             Egyptian:               in Amun! iw minb m tp-i!
             Greek:                  hristo mou!  eho ena maheri sto kefali mou!
             Tagalog:                Ay Dios ko! May palakol sa ulo ko!
             Danish:                 Oh min gud! Der er en oekse i mit hoved.
             Afrikaans:              O God!  Daar's 'n byl in my kop!
             Polish:                 O Moj Boze! Mam siekiere w glowie!
             Maori:                  Ave Te Ariki! He toki ki roto taku mahuna!
             Italian:                Dio mio!  C'e' un' ascia nella mia testa!
             Portuguese:             Meu Deus! Tenho um machado na cabeca!
             Klingon:                ghay'cha'! nachwIjDaq betleH tu'lu'!
             Bengali:                Oh Allah! Amar mathar upor bash poreche.
             Finnish:                Voi Luoja! Paassani on kirves!
             Icelandic:              Gud minn godur!  Thad er o:xi i ho:fdinu a mer.
             Ancient Greek:          O Theos mou! Echo ten labrida en te mou kephale!
             Babylonian:             iliya pashu ina reshiya bashu
             Assyrian:               iliya pashum ina reshimi bashu
             Welsh:                  A nuw!  Mae bywell yn fy mhen i!
             Alsatian:               Lever Gott! Es esch a Axe en miner Kopf!
             Swahili:                Siyo! (Huko) Shoka yangu kichwanil!
             Slovenian:              Moj Bog! Sekiro imam v glavi.
             Irish:                  Mo Dhia!  Ta' tua sa mo cheann.
             Esperanto:              Mia Dio!  Hakilo estas en mia kapo!
             Marathi:                Aray Devaa!  Majhyaa dokyaat kurhaad aahay.
             Hindi:                  Hay Bhagwaan!  Mere sar mein kulhaadi hain.
             Russian:                Bozhe moi!  Eto topor v moyei golove!
             Hebrew:                 Eloi!  Yesh'li ca-sheel ba-rosh sheh-li!
             Malayalam:              Entey Deiwame, entey thalayil oru kodali undei.
             Latvian:                Ak Dievs! Man ir cirvis galva!
            """;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        TextAreaTest textAreaTest = new TextAreaTest();
        EventQueue.invokeAndWait(textAreaTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(textAreaTest::test);
        } finally {
            textAreaTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("TextAreaTest");
        textArea = new TextArea(TEST_DATA, 24, 80);

        textArea.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        textArea.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.add(textArea);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void dispose() throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    private void test() {
        AccessibleTestUtils.verifyTextAreaAccessibility(
                textArea,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new TextStateTester(
                textArea,
                textArea.getAccessibleContext().getAccessibleStateSet()
        ).testAll();

        textArea.setEditable(false);

        AccessibleTestUtils.verifyTextAreaAccessibility(
                textArea,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new TextStateTester(
                textArea,
                textArea.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }

    private static final class TextStateTester
            extends AccessibleStateSetTester {
        private final AccessibleStateSet set;
        private final TextArea textArea;

        private TextStateTester(TextArea textArea, AccessibleStateSet set) {
            super(textArea, set);
            this.textArea = textArea;
            this.set = set;
        }

        @Override
        public void testEditable() {
            if (set.contains(AccessibleState.EDITABLE)) {
                if (!textArea.isEditable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains EDITABLE but " +
                                    "this component is not editable");
                }
            } else {
                if (textArea.isEditable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain EDITABLE " +
                                    "but this component is editable");
                }
            }
        }
    }
}
