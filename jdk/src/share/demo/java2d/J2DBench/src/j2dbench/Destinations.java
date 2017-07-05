/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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

package j2dbench;

import java.awt.Image;
import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;

import j2dbench.tests.GraphicsTests;
import j2dbench.tests.ImageTests;

public abstract class Destinations extends Option.Enable {
    public static Group.EnableSet destroot;
    public static Group bufimgdestroot;
    public static Group compatimgdestroot;

    public static void init() {
        destroot = new Group.EnableSet(TestEnvironment.globaloptroot,
                                       "dest", "Output Destination Options");

        new Screen();
        new OffScreen();

        if (GraphicsTests.hasGraphics2D) {
            if (ImageTests.hasCompatImage) {
                compatimgdestroot =
                    new Group.EnableSet(destroot, "compatimg",
                                        "Compatible Image Destinations");
                compatimgdestroot.setHorizontal();

                new CompatImg();
                new CompatImg(Transparency.OPAQUE);
                new CompatImg(Transparency.BITMASK);
                new CompatImg(Transparency.TRANSLUCENT);
            }

            if (ImageTests.hasVolatileImage) {
                new VolatileImg();
            }

            bufimgdestroot = new Group.EnableSet(destroot, "bufimg",
                                                 "BufferedImage Destinations");

            new BufImg(BufferedImage.TYPE_INT_RGB);
            new BufImg(BufferedImage.TYPE_INT_ARGB);
            new BufImg(BufferedImage.TYPE_INT_ARGB_PRE);
            new BufImg(BufferedImage.TYPE_3BYTE_BGR);
            new BufImg(BufferedImage.TYPE_BYTE_INDEXED);
            new BufImg(BufferedImage.TYPE_BYTE_GRAY);
            new CustomImg();
        }
    }

    public Destinations(Group parent,
                        String nodename, String description,
                        boolean defenabled)
    {
        super(parent, nodename, description, defenabled);
    }

    public void modifyTest(TestEnvironment env) {
        setDestination(env);
    }

    public void restoreTest(TestEnvironment env) {
        env.setTestImage(null);
    }

    public String getAbbreviatedModifierDescription(Object val) {
        return "to "+getModifierValueName(val);
    }

    public abstract void setDestination(TestEnvironment env);

    public static class Screen extends Destinations {
        public Screen() {
            super(destroot, "screen", "Output to Screen", false);
        }

        public String getModifierValueName(Object val) {
            return "Screen";
        }

        public void setDestination(TestEnvironment env) {
            env.setTestImage(null);
        }
    }

    public static class OffScreen extends Destinations {
        public OffScreen() {
            super(destroot, "offscreen", "Output to OffScreen Image", false);
        }

        public String getModifierValueName(Object val) {
            return "OffScreen";
        }

        public void setDestination(TestEnvironment env) {
            Component c = env.getCanvas();
            env.setTestImage(c.createImage(env.getWidth(), env.getHeight()));
        }
    }

    public static class CompatImg extends Destinations {
        int transparency;

        public static String ShortNames[] = {
            "compatimg",
            "opqcompatimg",
            "bmcompatimg",
            "transcompatimg",
        };

        public static String ShortDescriptions[] = {
            "Default",
            "Opaque",
            "Bitmask",
            "Translucent",
        };

        public static String LongDescriptions[] = {
            "Default Compatible Image",
            "Opaque Compatible Image",
            "Bitmask Compatible Image",
            "Translucent Compatible Image",
        };

        public static String ModifierNames[] = {
            "CompatImage()",
            "CompatImage(Opaque)",
            "CompatImage(Bitmask)",
            "CompatImage(Translucent)",
        };

        public CompatImg() {
            this(0);
        }

        public CompatImg(int transparency) {
            super(compatimgdestroot,
                  ShortNames[transparency],
                  ShortDescriptions[transparency],
                  false);
            this.transparency = transparency;
        }

        public String getModifierValueName(Object val) {
            return ModifierNames[transparency];
        }

        public void setDestination(TestEnvironment env) {
            Component c = env.getCanvas();
            GraphicsConfiguration gc = c.getGraphicsConfiguration();
            int w = env.getWidth();
            int h = env.getHeight();
            if (transparency == 0) {
                env.setTestImage(gc.createCompatibleImage(w, h));
            } else {
                env.setTestImage(gc.createCompatibleImage(w, h, transparency));
            }
        }
    }

    public static class VolatileImg extends Destinations {
        public VolatileImg() {
            super(destroot, "volimg", "Output to Volatile Image", false);
        }

        public String getModifierValueName(Object val) {
            return "VolatileImg";
        }

        public void setDestination(TestEnvironment env) {
            Component c = env.getCanvas();
            env.setTestImage(c.createVolatileImage(env.getWidth(),
                                                   env.getHeight()));
        }
    }


    public static class BufImg extends Destinations {
        int type;
        Image img;

        public static String ShortNames[] = {
            "custom",
            "IntXrgb",
            "IntArgb",
            "IntArgbPre",
            "IntXbgr",
            "3ByteBgr",
            "4ByteAbgr",
            "4ByteAbgrPre",
            "Short565",
            "Short555",
            "ByteGray",
            "ShortGray",
            "ByteBinary",
            "ByteIndexed",
        };

        public static String Descriptions[] = {
            "Custom Image",
            "32-bit XRGB Packed Image",
            "32-bit ARGB Packed Image",
            "32-bit ARGB Alpha Premultiplied Packed Image",
            "32-bit XBGR Packed Image",
            "3-byte BGR Component Image",
            "4-byte ABGR Component Image",
            "4-byte ABGR Alpha Premultiplied Component Image",
            "16-bit 565 RGB Packed Image",
            "15-bit 555 RGB Packed Image",
            "8-bit Grayscale Image",
            "16-bit Grayscale Image",
            "1-bit Binary Image",
            "8-bit Indexed Image",
        };

        public BufImg(int type) {
            super(bufimgdestroot, ShortNames[type], Descriptions[type], false);
            this.type = type;
        }

        public String getModifierValueName(Object val) {
            return "BufImg("+getNodeName()+")";
        }

        public void setDestination(TestEnvironment env) {
            if (img == null) {
                img = new BufferedImage(env.getWidth(), env.getHeight(), type);
            }
            env.setTestImage(img);
        }
    }

    public static class CustomImg extends Destinations {
        private Image img;

        public CustomImg() {
            super(bufimgdestroot,
                  "custom",
                  "Custom (3-float RGB) Image",
                  false);
        }

        public String getModifierValueName(Object val) {
            return "CustomImg";
        }

        public void setDestination(TestEnvironment env) {
            if (img == null) {
                ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                ComponentColorModel cm =
                    new ComponentColorModel(cs, false, false,
                                            Transparency.OPAQUE,
                                            DataBuffer.TYPE_FLOAT);
                WritableRaster raster =
                    cm.createCompatibleWritableRaster(env.getWidth(),
                                                      env.getHeight());
                img = new BufferedImage(cm, raster, false, null);
            }
            env.setTestImage(img);
        }
    }
}
