/*
 * @test
 * @bug     4614845
 * @summary Test drawImage(bgcolor) gets correct RGB from SystemColor objects.
 * @run     main SystemBgColorTest
 */

import java.awt.*;
import java.awt.image.*;

public class SystemBgColorTest {
    public static final int TESTW = 10;
    public static final int TESTH = 10;

    static SystemColor systemColorObjects [] = {
        SystemColor.desktop,
        SystemColor.activeCaption,
        SystemColor.activeCaptionText,
        SystemColor.activeCaptionBorder,
        SystemColor.inactiveCaption,
        SystemColor.inactiveCaptionText,
        SystemColor.inactiveCaptionBorder,
        SystemColor.window,
        SystemColor.windowBorder,
        SystemColor.windowText,
        SystemColor.menu,
        SystemColor.menuText,
        SystemColor.text,
        SystemColor.textText,
        SystemColor.textHighlight,
        SystemColor.textHighlightText,
        SystemColor.textInactiveText,
        SystemColor.control,
        SystemColor.controlText,
        SystemColor.controlHighlight,
        SystemColor.controlLtHighlight,
        SystemColor.controlShadow,
        SystemColor.controlDkShadow,
        SystemColor.scrollbar,
        SystemColor.info,
        SystemColor.infoText
    };

    static boolean counterrors;
    static int errcount;

    public static void error(String problem) {
        if (counterrors) {
            errcount++;
        } else {
            throw new RuntimeException(problem);
        }
    }

    public static void main(String argv[]) {
        counterrors = (argv.length > 0);
        test(BufferedImage.TYPE_INT_ARGB);
        test(BufferedImage.TYPE_INT_RGB);
        if (errcount > 0) {
            throw new RuntimeException(errcount+" errors");
        }
    }

    static int cmap[] = {
        0x00000000,
        0xffffffff,
    };

    public static void test(int dsttype) {
        BufferedImage src =
            new BufferedImage(TESTW, TESTH, BufferedImage.TYPE_INT_ARGB);
        test(src, dsttype);
        IndexColorModel icm = new IndexColorModel(8, 2, cmap, 0, true, 0,
                                                  DataBuffer.TYPE_BYTE);
        src = new BufferedImage(TESTW, TESTH,
                                BufferedImage.TYPE_BYTE_INDEXED, icm);
        test(src, dsttype);
    }

    public static void test(Image src, int dsttype) {
        BufferedImage dst =
            new BufferedImage(TESTW, TESTH, dsttype);
        for (int i = 0; i < systemColorObjects.length; i++) {
            test(src, dst, systemColorObjects[i]);
        }
    }

    public static void test(Image src, BufferedImage dst, Color bg) {
        Graphics g = dst.getGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, TESTW, TESTH);
        g.drawImage(src, 0, 0, bg, null);
        if (dst.getRGB(0, 0) != bg.getRGB()) {
            error("bad bg pixel for: "+bg);
        }
    }
}
