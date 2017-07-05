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

package j2dbench.tests;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.Polygon;
import java.awt.Color;
import java.awt.Dimension;
import java.lang.reflect.Field;

import j2dbench.Destinations;
import j2dbench.Group;
import j2dbench.Option;
import j2dbench.Result;
import j2dbench.Test;
import j2dbench.TestEnvironment;

public abstract class GraphicsTests extends Test {
    public static boolean hasGraphics2D;

    static {
        try {
            hasGraphics2D = (Graphics2D.class != null);
        } catch (NoClassDefFoundError e) {
        }
    }

    static Color makeAlphaColor(Color opaque, int alpha) {
        try {
            opaque = new Color(opaque.getRed(),
                               opaque.getGreen(),
                               opaque.getBlue(),
                               alpha);
        } catch (NoSuchMethodError e) {
        }
        return opaque;
    }

    static Group graphicsroot;
    static Group groptroot;

    static Option animList;
    static Option sizeList;
    static Option compRules;
    static Option doExtraAlpha;
    static Option doXor;
    static Option doClipping;
    static Option renderHint;
    // REMIND: transform, etc.

    public static void init() {
        graphicsroot = new Group("graphics", "Graphics Benchmarks");
        graphicsroot.setTabbed();

        groptroot = new Group(graphicsroot, "opts", "General Graphics Options");

        animList = new Option.IntList(groptroot, "anim",
                                      "Movement of rendering position",
                                      new int[] {0, 1, 2},
                                      new String[] {
                                          "static", "slide", "bounce",
                                      },
                                      new String[] {
                                          "No movement",
                                          "Shift horizontal alignment",
                                          "Bounce around window",
                                      }, 0x4);

        sizeList = new Option.IntList(groptroot, "sizes",
                                      "Size of Operations to perform",
                                      new int[] {1, 20, 100, 250, 1000},
                                      new String[] {
                                          "1x1", "20x20", "100x100", "250x250",
                                          "1000x1000",
                                      },
                                      new String[] {
                                          "Tiny Shapes (1x1)",
                                          "Small Shapes (20x20)",
                                          "Medium Shapes (100x100)",
                                          "Large Shapes (250x250)",
                                          "X-Large Shapes (1000x1000)",
                                      }, 0xa);
        if (hasGraphics2D) {
            String rulenames[] = {
                "Clear",
                "Src",
                "Dst",
                "SrcOver",
                "DstOver",
                "SrcIn",
                "DstIn",
                "SrcOut",
                "DstOut",
                "SrcAtop",
                "DstAtop",
                "Xor",
            };
            String ruledescs[] = new String[rulenames.length];
            Object rules[] = new Object[rulenames.length];
            int j = 0;
            int defrule = 0;
            for (int i = 0; i < rulenames.length; i++) {
                String rulename = rulenames[i];
                try {
                    Field f = AlphaComposite.class.getField(rulename);
                    rules[j] = f.get(null);
                } catch (NoSuchFieldException nsfe) {
                    continue;
                } catch (IllegalAccessException iae) {
                    continue;
                }
                if (rules[j] == AlphaComposite.SrcOver) {
                    defrule = j;
                }
                rulenames[j] = rulename;
                String suffix;
                if (rulename.startsWith("Src")) {
                    suffix = rulename.substring(3);
                    rulename = "Source";
                } else if (rulename.startsWith("Dst")) {
                    suffix = rulename.substring(3);
                    rulename = "Dest";
                } else {
                    suffix = "";
                }
                if (suffix.length() > 0) {
                    suffix = " "+suffix;
                }
                ruledescs[j] = rulename+suffix;
                j++;
            }
            compRules =
                new Option.ObjectList(groptroot, "alpharule",
                                      "AlphaComposite Rule",
                                      j, rulenames, rules, rulenames,
                                      ruledescs, (1 << defrule));
            ((Option.ObjectList) compRules).setNumRows(4);
            doExtraAlpha =
                new Option.Toggle(groptroot, "extraalpha",
                                  "Render with an \"extra alpha\" of 0.125",
                                  Option.Toggle.Off);
            doXor =
                new Option.Toggle(groptroot, "xormode",
                                  "Render in XOR mode", Option.Toggle.Off);
            doClipping =
                new Option.Toggle(groptroot, "clip",
                                  "Render through a complex clip shape",
                                  Option.Toggle.Off);
            String rhintnames[] = {
                "Default", "Speed", "Quality",
            };
            renderHint =
                new Option.ObjectList(groptroot, "renderhint",
                                      "Rendering Hint",
                                      rhintnames, new Object[] {
                                          RenderingHints.VALUE_RENDER_DEFAULT,
                                          RenderingHints.VALUE_RENDER_SPEED,
                                          RenderingHints.VALUE_RENDER_QUALITY,
                                      }, rhintnames, rhintnames, 1);
        }
    }

    public static class Context {
        Graphics graphics;
        Dimension outdim;
        boolean animate;
        int size;
        int orgX, orgY;
        int initX, initY;
        int maxX, maxY;
    }

    public GraphicsTests(Group parent, String nodeName, String description) {
        super(parent, nodeName, description);
        addDependency(Destinations.destroot);
        addDependencies(groptroot, false);
    }

    public Object initTest(TestEnvironment env, Result result) {
        Context ctx = createContext();
        initContext(env, ctx);
        result.setUnits(pixelsTouched(ctx));
        result.setUnitName("pixel");
        return ctx;
    }

    public int pixelsTouched(Context ctx) {
        return ctx.outdim.width * ctx.outdim.height;
    }

    public Context createContext() {
        return new Context();
    }

    public Dimension getOutputSize(int w, int h) {
        return new Dimension(w, h);
    }

    public void initContext(TestEnvironment env, Context ctx) {
        ctx.graphics = env.getGraphics();
        int w = env.getWidth();
        int h = env.getHeight();
        if (hasGraphics2D) {
            Graphics2D g2d = (Graphics2D) ctx.graphics;
            AlphaComposite ac = (AlphaComposite) env.getModifier(compRules);
            if (env.isEnabled(doExtraAlpha)) {
                ac = AlphaComposite.getInstance(ac.getRule(), 0.125f);
            }
            g2d.setComposite(ac);
            if (env.isEnabled(doXor)) {
                g2d.setXORMode(Color.white);
            }
            if (env.isEnabled(doClipping)) {
                Polygon p = new Polygon();
                p.addPoint(0, 0);
                p.addPoint(w, 0);
                p.addPoint(0, h);
                p.addPoint(w, h);
                p.addPoint(0, 0);
                g2d.clip(p);
            }
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                                 env.getModifier(renderHint));
        }
        ctx.size = env.getIntValue(sizeList);
        ctx.outdim = getOutputSize(ctx.size, ctx.size);
        switch (env.getIntValue(animList)) {
        case 0:
            ctx.animate = false;
            ctx.maxX = 3;
            ctx.maxY = 1;
            ctx.orgX = (w - ctx.outdim.width) / 2;
            ctx.orgY = (h - ctx.outdim.height) / 2;
            break;
        case 1:
            ctx.animate = true;
            ctx.maxX = Math.max(Math.min(32, w - ctx.outdim.width), 3);
            ctx.maxY = 1;
            ctx.orgX = (w - ctx.outdim.width - ctx.maxX) / 2;
            ctx.orgY = (h - ctx.outdim.height) / 2;
            break;
        case 2:
            ctx.animate = true;
            ctx.maxX = (w - ctx.outdim.width) + 1;
            ctx.maxY = (h - ctx.outdim.height) + 1;
            ctx.maxX = adjustWidth(ctx.maxX, ctx.maxY);
            ctx.maxX = Math.max(ctx.maxX, 3);
            ctx.maxY = Math.max(ctx.maxY, 1);
            // ctx.orgX = ctx.orgY = 0;
            break;
        }
        ctx.initX = ctx.maxX / 2;
        ctx.initY = ctx.maxY / 2;
    }

    public void cleanupTest(TestEnvironment env, Object ctx) {
        Graphics graphics = ((Context) ctx).graphics;
        graphics.dispose();
        ((Context) ctx).graphics = null;
    }
}
