/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
    "\u064a\u0639\u062f\u0020\u062a\u062e\u0637\u064a\u0637\u0020\u062e\u0637\u0648\u0637\u0020\u004f\u0070\u0065\u006e\u0054\u0079\u0070\u0065\u0020\u062a\u0642\u0646\u064a\u0629\u0020\u0645\u0647\u0645\u0629\u0020\u0644\u0644\u0639\u0631\u0636\u0020\u0627\u0644\u0635\u062d\u064a\u062d\u0020\u0644\u0644\u0639\u062f\u064a\u062f\u0020\u0645\u0646\u0020\u0627\u0644\u0644\u063a\u0627\u062a\u0020\u0627\u0644\u0637\u0628\u064a\u0639\u064a\u0629\u0020\u0641\u064a\u0020\u0627\u0644\u0639\u0627\u0644\u0645\u002e\u05d9\u05d5\u05ea";

    static final String hebrewText =
    //  פריסת גופן OpenType היא טכנולוגיה קריטית לעיבוד נכון של רבות מהשפות הטבעיות בעולם.
    "\u05e4\u05e8\u05d9\u05e1\u05ea\u0020\u05d2\u05d5\u05e4\u05df\u0020\u004f\u0070\u0065\u006e\u0054\u0079\u0070\u0065\u0020\u05d4\u05d9\u05d0\u0020\u05d8\u05db\u05e0\u05d5\u05dc\u05d5\u05d2\u05d9\u05d4\u0020\u05e7\u05e8\u05d9\u05d8\u05d9\u05ea\u0020\u05dc\u05e2\u05d9\u05d1\u05d5\u05d3\u0020\u05e0\u05db\u05d5\u05df\u0020\u05e9\u05dc\u0020\u05e8\u05d1\u05d5\u05ea\u0020\u05de\u05d4\u05e9\u05e4\u05d5\u05ea\u0020\u05d4\u05d8\u05d1\u05e2\u05d9\u05d5\u05ea\u0020\u05d1\u05e2\u05d5\u05dc\u05dd\u002e";

    static final String thaiText =
    // เค้าโครงแบบอักษร OpenType เป็นเทคโนโลยีที่สำคัญสำหรับการแสดงผลภาษาธรรมชาติจำนวนมากของโลกอย่างเหมาะสม
    "\u0e40\u0e04\u0e49\u0e32\u0e42\u0e04\u0e23\u0e07\u0e41\u0e1a\u0e1a\u0e2d\u0e31\u0e01\u0e29\u0e23\u0020\u004f\u0070\u0065\u006e\u0054\u0079\u0070\u0065\u0020\u0e40\u0e1b\u0e47\u0e19\u0e40\u0e17\u0e04\u0e42\u0e19\u0e42\u0e25\u0e22\u0e35\u0e17\u0e35\u0e48\u0e2a\u0e33\u0e04\u0e31\u0e0d\u0e2a\u0e33\u0e2b\u0e23\u0e31\u0e1a\u0e01\u0e32\u0e23\u0e41\u0e2a\u0e14\u0e07\u0e1c\u0e25\u0e20\u0e32\u0e29\u0e32\u0e18\u0e23\u0e23\u0e21\u0e0a\u0e32\u0e15\u0e34\u0e08\u0e33\u0e19\u0e27\u0e19\u0e21\u0e32\u0e01\u0e02\u0e2d\u0e07\u0e42\u0e25\u0e01\u0e2d\u0e22\u0e48\u0e32\u0e07\u0e40\u0e2b\u0e21\u0e32\u0e30\u0e2a\u0e21";

    static final String khmerText =
    // ប្លង់ពុម្ពអក្សរ OpenType គឺជាបច្ចេកវិជ្ជាសំខាន់មួយសម្រាប់ការបង្ហាញត្រឹមត្រូវនៃភាសាធម្មជាតិជាច្រើនរបស់ពិភពលោក។
    "\u1794\u17d2\u179b\u1784\u17cb\u1796\u17bb\u1798\u17d2\u1796\u17a2\u1780\u17d2\u179f\u179a\u0020\u004f\u0070\u0065\u006e\u0054\u0079\u0070\u0065\u0020\u1782\u17ba\u1787\u17b6\u1794\u1785\u17d2\u1785\u17c1\u1780\u179c\u17b7\u1787\u17d2\u1787\u17b6\u179f\u17c6\u1781\u17b6\u1793\u17cb\u1798\u17bd\u1799\u179f\u1798\u17d2\u179a\u17b6\u1794\u17cb\u1780\u17b6\u179a\u1794\u1784\u17d2\u17a0\u17b6\u1789\u178f\u17d2\u179a\u17b9\u1798\u178f\u17d2\u179a\u17bc\u179c\u1793\u17c3\u1797\u17b6\u179f\u17b6\u1792\u1798\u17d2\u1798\u1787\u17b6\u178f\u17b7\u1787\u17b6\u1785\u17d2\u179a\u17be\u1793\u179a\u1794\u179f\u17cb\u1796\u17b7\u1797\u1796\u179b\u17c4\u1780\u17d4";

    static final String laoText =
    // ຮູບແບບຕົວອັກສອນ OpenType ເປັນເທັກໂນໂລຍີສຳຄັນສຳລັບການສະແດງຜົນຂອງພາສາທຳມະຊາດຫຼາຍພາສາຂອງໂລກ.
    "\u0eae\u0eb9\u0e9a\u0ec1\u0e9a\u0e9a\u0e95\u0ebb\u0ea7\u0ead\u0eb1\u0e81\u0eaa\u0ead\u0e99\u0020\u004f\u0070\u0065\u006e\u0054\u0079\u0070\u0065\u0020\u0ec0\u0e9b\u0eb1\u0e99\u0ec0\u0e97\u0eb1\u0e81\u0ec2\u0e99\u0ec2\u0ea5\u0e8d\u0eb5\u0eaa\u0eb3\u0e84\u0eb1\u0e99\u0eaa\u0eb3\u0ea5\u0eb1\u0e9a\u0e81\u0eb2\u0e99\u0eaa\u0eb0\u0ec1\u0e94\u0e87\u0e9c\u0ebb\u0e99\u0e82\u0ead\u0e87\u0e9e\u0eb2\u0eaa\u0eb2\u0e97\u0eb3\u0ea1\u0eb0\u0e8a\u0eb2\u0e94\u0eab\u0ebc\u0eb2\u0e8d\u0e9e\u0eb2\u0eaa\u0eb2\u0e82\u0ead\u0e87\u0ec2\u0ea5\u0e81\u002e";

    static final String hindiText =
    // ओपनटाइप फ़ॉन्ट लेआउट दुनिया की कई प्राकृतिक भाषाओं के उचित प्रतिपादन के लिए एक महत्वपूर्ण तकनीक है।
    "\u0913\u092a\u0928\u091f\u093e\u0907\u092a\u0020\u092b\u093c\u0949\u0928\u094d\u091f\u0020\u0932\u0947\u0906\u0909\u091f\u0020\u0926\u0941\u0928\u093f\u092f\u093e\u0020\u0915\u0940\u0020\u0915\u0908\u0020\u092a\u094d\u0930\u093e\u0915\u0943\u0924\u093f\u0915\u0020\u092d\u093e\u0937\u093e\u0913\u0902\u0020\u0915\u0947\u0020\u0909\u091a\u093f\u0924\u0020\u092a\u094d\u0930\u0924\u093f\u092a\u093e\u0926\u0928\u0020\u0915\u0947\u0020\u0932\u093f\u090f\u0020\u090f\u0915\u0020\u092e\u0939\u0924\u094d\u0935\u092a\u0942\u0930\u094d\u0923\u0020\u0924\u0915\u0928\u0940\u0915\u0020\u0939\u0948\u0964";

    static final String kannadaText =
    // ಓಪನ್‌ಟೈಪ್ ಫಾಂಟ್ ವಿನ್ಯಾಸವು ಪ್ರಪಂಚದ ಅನೇಕ ನೈಸರ್ಗಿಕ ಭಾಷೆಗಳ ಸರಿಯಾದ ರೆಂಡರಿಂಗ್‌ಗೆ ನಿರ್ಣಾಯಕ ತಂತ್ರಜ್ಞಾನವಾಗಿದೆ.
    "\u0c93\u0caa\u0ca8\u0ccd\u200c\u0c9f\u0cc8\u0caa\u0ccd\u0020\u0cab\u0cbe\u0c82\u0c9f\u0ccd\u0020\u0cb5\u0cbf\u0ca8\u0ccd\u0caf\u0cbe\u0cb8\u0cb5\u0cc1\u0020\u0caa\u0ccd\u0cb0\u0caa\u0c82\u0c9a\u0ca6\u0020\u0c85\u0ca8\u0cc7\u0c95\u0020\u0ca8\u0cc8\u0cb8\u0cb0\u0ccd\u0c97\u0cbf\u0c95\u0020\u0cad\u0cbe\u0cb7\u0cc6\u0c97\u0cb3\u0020\u0cb8\u0cb0\u0cbf\u0caf\u0cbe\u0ca6\u0020\u0cb0\u0cc6\u0c82\u0ca1\u0cb0\u0cbf\u0c82\u0c97\u0ccd\u200c\u0c97\u0cc6\u0020\u0ca8\u0cbf\u0cb0\u0ccd\u0ca3\u0cbe\u0caf\u0c95\u0020\u0ca4\u0c82\u0ca4\u0ccd\u0cb0\u0c9c\u0ccd\u0c9e\u0cbe\u0ca8\u0cb5\u0cbe\u0c97\u0cbf\u0ca6\u0cc6\u002e";

    static final String tamilText =
    // ஓபன் டைப் எழுத்துரு அமைப்பு என்பது உலகின் பல இயற்கை மொழிகளைச் சரியாக வழங்குவதற்கான ஒரு முக்கியமான தொழில்நுட்பமாகும்.
    "\u0b93\u0baa\u0ba9\u0bcd\u0020\u0b9f\u0bc8\u0baa\u0bcd\u0020\u0b8e\u0bb4\u0bc1\u0ba4\u0bcd\u0ba4\u0bc1\u0bb0\u0bc1\u0020\u0b85\u0bae\u0bc8\u0baa\u0bcd\u0baa\u0bc1\u0020\u0b8e\u0ba9\u0bcd\u0baa\u0ba4\u0bc1\u0020\u0b89\u0bb2\u0b95\u0bbf\u0ba9\u0bcd\u0020\u0baa\u0bb2\u0020\u0b87\u0baf\u0bb1\u0bcd\u0b95\u0bc8\u0020\u0bae\u0bca\u0bb4\u0bbf\u0b95\u0bb3\u0bc8\u0b9a\u0bcd\u0020\u0b9a\u0bb0\u0bbf\u0baf\u0bbe\u0b95\u0020\u0bb5\u0bb4\u0b99\u0bcd\u0b95\u0bc1\u0bb5\u0ba4\u0bb1\u0bcd\u0b95\u0bbe\u0ba9\u0020\u0b92\u0bb0\u0bc1\u0020\u0bae\u0bc1\u0b95\u0bcd\u0b95\u0bbf\u0baf\u0bae\u0bbe\u0ba9\u0020\u0ba4\u0bca\u0bb4\u0bbf\u0bb2\u0bcd\u0ba8\u0bc1\u0b9f\u0bcd\u0baa\u0bae\u0bbe\u0b95\u0bc1\u0bae\u0bcd\u002e";

    static final String malayalamText =
    // ഓപ്പൺടൈപ്പ് ഫോണ്ട് ലേഔട്ട് ലോകത്തിലെ പല സ്വാഭാവിക ഭാഷകളുടെയും ശരിയായ റെൻഡറിംഗിനുള്ള ഒരു നിർണായക സാങ്കേതികവിദ്യയാണ്.
    "\u0d13\u0d2a\u0d4d\u0d2a\u0d7a\u0d1f\u0d48\u0d2a\u0d4d\u0d2a\u0d4d\u0020\u0d2b\u0d4b\u0d23\u0d4d\u0d1f\u0d4d\u0020\u0d32\u0d47\u0d14\u0d1f\u0d4d\u0d1f\u0d4d\u0020\u0d32\u0d4b\u0d15\u0d24\u0d4d\u0d24\u0d3f\u0d32\u0d46\u0020\u0d2a\u0d32\u0020\u0d38\u0d4d\u0d35\u0d3e\u0d2d\u0d3e\u0d35\u0d3f\u0d15\u0020\u0d2d\u0d3e\u0d37\u0d15\u0d33\u0d41\u0d1f\u0d46\u0d2f\u0d41\u0d02\u0020\u0d36\u0d30\u0d3f\u0d2f\u0d3e\u0d2f\u0020\u0d31\u0d46\u0d7b\u0d21\u0d31\u0d3f\u0d02\u0d17\u0d3f\u0d28\u0d41\u0d33\u0d4d\u0d33\u0020\u0d12\u0d30\u0d41\u0020\u0d28\u0d3f\u0d7c\u0d23\u0d3e\u0d2f\u0d15\u0020\u0d38\u0d3e\u0d19\u0d4d\u0d15\u0d47\u0d24\u0d3f\u0d15\u0d35\u0d3f\u0d26\u0d4d\u0d2f\u0d2f\u0d3e\u0d23\u0d4d\u002e";

    static final String gujaratiText =
    // ຮູບແບບຕົວອັກສອນ OpenType ເປັນເທັກໂນໂລຍີສຳຄັນສຳລັບການສະແດງຜົນຂອງພາສາທຳມະຊາດຫຼາຍພາສາຂອງໂລກ.
    "\u0eae\u0eb9\u0e9a\u0ec1\u0e9a\u0e9a\u0e95\u0ebb\u0ea7\u0ead\u0eb1\u0e81\u0eaa\u0ead\u0e99\u0020\u004f\u0070\u0065\u006e\u0054\u0079\u0070\u0065\u0020\u0ec0\u0e9b\u0eb1\u0e99\u0ec0\u0e97\u0eb1\u0e81\u0ec2\u0e99\u0ec2\u0ea5\u0e8d\u0eb5\u0eaa\u0eb3\u0e84\u0eb1\u0e99\u0eaa\u0eb3\u0ea5\u0eb1\u0e9a\u0e81\u0eb2\u0e99\u0eaa\u0eb0\u0ec1\u0e94\u0e87\u0e9c\u0ebb\u0e99\u0e82\u0ead\u0e87\u0e9e\u0eb2\u0eaa\u0eb2\u0e97\u0eb3\u0ea1\u0eb0\u0e8a\u0eb2\u0e94\u0eab\u0ebc\u0eb2\u0e8d\u0e9e\u0eb2\u0eaa\u0eb2\u0e82\u0ead\u0e87\u0ec2\u0ea5\u0e81\u002e";

    static final String teluguText =
    // ఓపెన్‌టైప్ ఫాంట్ లేఅవుట్ అనేది ప్రపంచంలోని అనేక సహజ భాషలను సరిగ్గా రెండరింగ్ చేయడానికి కీలకమైన సాంకేతికత.
    "\u0c13\u0c2a\u0c46\u0c28\u0c4d\u200c\u0c1f\u0c48\u0c2a\u0c4d\u0020\u0c2b\u0c3e\u0c02\u0c1f\u0c4d\u0020\u0c32\u0c47\u0c05\u0c35\u0c41\u0c1f\u0c4d\u0020\u0c05\u0c28\u0c47\u0c26\u0c3f\u0020\u0c2a\u0c4d\u0c30\u0c2a\u0c02\u0c1a\u0c02\u0c32\u0c4b\u0c28\u0c3f\u0020\u0c05\u0c28\u0c47\u0c15\u0020\u0c38\u0c39\u0c1c\u0020\u0c2d\u0c3e\u0c37\u0c32\u0c28\u0c41\u0020\u0c38\u0c30\u0c3f\u0c17\u0c4d\u0c17\u0c3e\u0020\u0c30\u0c46\u0c02\u0c21\u0c30\u0c3f\u0c02\u0c17\u0c4d\u0020\u0c1a\u0c47\u0c2f\u0c21\u0c3e\u0c28\u0c3f\u0c15\u0c3f\u0020\u0c15\u0c40\u0c32\u0c15\u0c2e\u0c48\u0c28\u0020\u0c38\u0c3e\u0c02\u0c15\u0c47\u0c24\u0c3f\u0c15\u0c24\u002e";


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
