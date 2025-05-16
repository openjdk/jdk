/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
   @test
   @summary verify JNI and FFM harfbuzz OpenType layout implementations are equivalent.
*/

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;

public class LayoutCompatTest {

   static String jni = "jni.txt";
   static String ffm = "ffm.txt";
   static final AffineTransform tx = new AffineTransform();
   static final FontRenderContext frc = new FontRenderContext(tx, false, false);

   static final String englishText =
       "OpenType font layout is a critical technology for proper rendering of many of the world's natural languages.";


    static final String arabicText =
       // " يعد تخطيط خطوط OpenType تقنية مهمة للعرض الصحيح للعديد من اللغات الطبيعية في العالم.יות";
    "يعد تخطيط خطوط OpenType تقنية مهمة للعرض الصحيح للعديد من اللغات الطبيعية في العالم.יות";

    static final String hebrewText =
    //  פריסת גופן OpenType היא טכנולוגיה קריטית לעיבוד נכון של רבות מהשפות הטבעיות בעולם.
    "פריסת גופן OpenType היא טכנולוגיה קריטית לעיבוד נכון של רבות מהשפות הטבעיות בעולם.";

    static final String thaiText =
    // เค้าโครงแบบอักษร OpenType เป็นเทคโนโลยีที่สำคัญสำหรับการแสดงผลภาษาธรรมชาติจำนวนมากของโลกอย่างเหมาะสม
    "เค้าโครงแบบอักษร OpenType เป็นเทคโนโลยีที่สำคัญสำหรับการแสดงผลภาษาธรรมชาติจำนวนมากของโลกอย่างเหมาะสม";

    static final String khmerText =
    // ប្លង់ពុម្ពអក្សរ OpenType គឺជាបច្ចេកវិជ្ជាសំខាន់មួយសម្រាប់ការបង្ហាញត្រឹមត្រូវនៃភាសាធម្មជាតិជាច្រើនរបស់ពិភពលោក។
    "ប្លង់ពុម្ពអក្សរ OpenType គឺជាបច្ចេកវិជ្ជាសំខាន់មួយសម្រាប់ការបង្ហាញត្រឹមត្រូវនៃភាសាធម្មជាតិជាច្រើនរបស់ពិភពលោក។";

    static final String laoText =
    // ຮູບແບບຕົວອັກສອນ OpenType ເປັນເທັກໂນໂລຍີສຳຄັນສຳລັບການສະແດງຜົນຂອງພາສາທຳມະຊາດຫຼາຍພາສາຂອງໂລກ.
    "ຮູບແບບຕົວອັກສອນ OpenType ເປັນເທັກໂນໂລຍີສຳຄັນສຳລັບການສະແດງຜົນຂອງພາສາທຳມະຊາດຫຼາຍພາສາຂອງໂລກ.";

    static final String hindiText =
    // ओपनटाइप फ़ॉन्ट लेआउट दुनिया की कई प्राकृतिक भाषाओं के उचित प्रतिपादन के लिए एक महत्वपूर्ण तकनीक है।
    "ओपनटाइप फ़ॉन्ट लेआउट दुनिया की कई प्राकृतिक भाषाओं के उचित प्रतिपादन के लिए एक महत्वपूर्ण तकनीक है।";

    static final String kannadaText =
    // ಓಪನ್‌ಟೈಪ್ ಫಾಂಟ್ ವಿನ್ಯಾಸವು ಪ್ರಪಂಚದ ಅನೇಕ ನೈಸರ್ಗಿಕ ಭಾಷೆಗಳ ಸರಿಯಾದ ರೆಂಡರಿಂಗ್‌ಗೆ ನಿರ್ಣಾಯಕ ತಂತ್ರಜ್ಞಾನವಾಗಿದೆ.
    "ಓಪನ್‌ಟೈಪ್ ಫಾಂಟ್ ವಿನ್ಯಾಸವು ಪ್ರಪಂಚದ ಅನೇಕ ನೈಸರ್ಗಿಕ ಭಾಷೆಗಳ ಸರಿಯಾದ ರೆಂಡರಿಂಗ್‌ಗೆ ನಿರ್ಣಾಯಕ ತಂತ್ರಜ್ಞಾನವಾಗಿದೆ.";

    static final String tamilText =
    // ஓபன் டைப் எழுத்துரு அமைப்பு என்பது உலகின் பல இயற்கை மொழிகளைச் சரியாக வழங்குவதற்கான ஒரு முக்கியமான தொழில்நுட்பமாகும்.
    "ஓபன் டைப் எழுத்துரு அமைப்பு என்பது உலகின் பல இயற்கை மொழிகளைச் சரியாக வழங்குவதற்கான ஒரு முக்கியமான தொழில்நுட்பமாகும்.";

    static final String malayalamText =
    // ഓപ്പൺടൈപ്പ് ഫോണ്ട് ലേഔട്ട് ലോകത്തിലെ പല സ്വാഭാവിക ഭാഷകളുടെയും ശരിയായ റെൻഡറിംഗിനുള്ള ഒരു നിർണായക സാങ്കേതികവിദ്യയാണ്.
    "ഓപ്പൺടൈപ്പ് ഫോണ്ട് ലേഔട്ട് ലോകത്തിലെ പല സ്വാഭാവിക ഭാഷകളുടെയും ശരിയായ റെൻഡറിംഗിനുള്ള ഒരു നിർണായക സാങ്കേതികവിദ്യയാണ്.";

    static final String gujaratiText =
    // ຮູບແບບຕົວອັກສອນ OpenType ເປັນເທັກໂນໂລຍີສຳຄັນສຳລັບການສະແດງຜົນຂອງພາສາທຳມະຊາດຫຼາຍພາສາຂອງໂລກ.
    "ຮູບແບບຕົວອັກສອນ OpenType ເປັນເທັກໂນໂລຍີສຳຄັນສຳລັບການສະແດງຜົນຂອງພາສາທຳມະຊາດຫຼາຍພາສາຂອງໂລກ.";

    static final String teluguText =
    // ఓపెన్‌టైప్ ఫాంట్ లేఅవుట్ అనేది ప్రపంచంలోని అనేక సహజ భాషలను సరిగ్గా రెండరింగ్ చేయడానికి కీలకమైన సాంకేతికత.
    "ఓపెన్‌టైప్ ఫాంట్ లేఅవుట్ అనేది ప్రపంచంలోని అనేక సహజ భాషలను సరిగ్గా రెండరింగ్ చేయడానికి కీలకమైన సాంకేతికత.";


   static Font[] allFonts;

   public static void main(String args[]) throws Exception {
       if (args.length > 0) {
           writeLayouts(args[0]);
           return;
       }
       String classesDir = System.getProperty("test.classes");
       if (classesDir != null) {
           String sep = System.getProperty("file.separator");
           String fileDir = classesDir + sep;
           jni = fileDir + jni;
           ffm = fileDir + ffm;
       }
       forkAndWait(jni, false);
       forkAndWait(ffm, true);
       compareLayouts(jni, ffm);
    }

    static void compareLayouts(String file1, String file2) throws Exception {
         FileInputStream i1 = new FileInputStream(file1);
         FileInputStream i2 = new FileInputStream(file2);
         byte[] ba1 = i1.readAllBytes();
         byte[] ba2 = i2.readAllBytes();
         for (int i = 0; i < ba1.length; i++) {
            if (ba1[i] != ba2[i]) {
                throw new RuntimeException("files differ byte offset=" + i);
            }
         }
    }

    static boolean isLogicalFont(Font f) {
        String s = f.getFamily().toLowerCase();
        if (s.startsWith(".") || // skip Apple System fonts - not supposed to be used
            s.equals("serif") ||
            s.equals("sansserif") ||
            s.equals("dialog") ||
            s.equals("dialoginput") ||
            s.equals("monospaced")) {
           return true;
       }
       return false;
    }

    static Font findFont(char c) {
        for (Font f : allFonts) {
           if (isLogicalFont(f)) continue;
           if (f.canDisplay(c)) { // not for supplementary chars
               return f.deriveFont(24.0f);
           }
        }
        return new Font(Font.DIALOG, 24, Font.PLAIN);
    }

    static void writeGV(PrintStream out, String title, String text) {
        char[] chars = text.toCharArray();
        Font font = findFont(chars[0]);
        GlyphVector gv = font.layoutGlyphVector(frc, chars, 0, chars.length, 0);
        int ng = gv.getNumGlyphs();
        int[] codes = gv.getGlyphCodes(0, ng, null);
        float[] positions = gv.getGlyphPositions(0, ng, null);
        out.println(title);
        out.println(font);
        out.println("num glyphs = " + ng);
        out.print("Codes=");
        for (int code : codes) out.print(" "+code); out.println();
        out.print("Positions=");
        for (float pos : positions) out.print(" "+pos); out.println();
        out.println();
    }

    static void writeLayouts(String fileName) throws Exception {
         allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
         PrintStream out = new PrintStream(fileName);
         out.println("java.home="+javaHome);
         out.println("javaExe="+javaExe);
         out.println("classpath="+classpath);
         writeGV(out,"English:", englishText);
         writeGV(out,"Arabic:", arabicText);
         writeGV(out,"Hebrew:", hebrewText);
         writeGV(out,"Thai:", thaiText);
         writeGV(out,"Khmer:", khmerText);
         writeGV(out,"Lao:", laoText);
         writeGV(out,"Hindi:", hindiText);
         writeGV(out,"Kannada:", kannadaText);
         writeGV(out,"Tamil:", tamilText);
         writeGV(out,"Malayalam:", malayalamText);
         writeGV(out,"Gujarati:", gujaratiText);
         writeGV(out,"Telugu:", teluguText);
         out.close();
    }

    static final String javaHome = (System.getProperty("test.jdk") != null)
            ? System.getProperty("test.jdk")
            : System.getProperty("java.home");

    static final String javaExe =
            javaHome + File.separator + "bin" + File.separator + "java";

    static final String classpath =
            System.getProperty("java.class.path");

    static void forkAndWait(String fileName, boolean val) throws Exception {
        List<String> args =
            Arrays.asList(javaExe,
               "-cp", classpath,
               "-Dsun.font.layout.ffm="+Boolean.toString(val),
               "-Dsun.font.layout.logtime=true",
               "LayoutCompatTest",
               fileName);
        ProcessBuilder pb = new ProcessBuilder(args);
        Process p = pb.start();
        p.waitFor();
        p.destroy();
    }
}
