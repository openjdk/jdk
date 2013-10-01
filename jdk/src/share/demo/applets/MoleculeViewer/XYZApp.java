/*
 * Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */



import java.applet.Applet;
import java.awt.Image;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.net.URL;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/*
 * A set of classes to parse, represent and display Chemical compounds in
 * .xyz format (see http://chem.leeds.ac.uk/Project/MIME.html)
 */
/** The representation of a Chemical .xyz model */
final class XYZChemModel {

    float vert[];
    Atom atoms[];
    int tvert[];
    int ZsortMap[];
    int nvert, maxvert;
    static final Map<String, Atom> atomTable = new HashMap<String, Atom>();
    static Atom defaultAtom;

    static {
        atomTable.put("c", new Atom(0, 0, 0));
        atomTable.put("h", new Atom(210, 210, 210));
        atomTable.put("n", new Atom(0, 0, 255));
        atomTable.put("o", new Atom(255, 0, 0));
        atomTable.put("p", new Atom(255, 0, 255));
        atomTable.put("s", new Atom(255, 255, 0));
        atomTable.put("hn", new Atom(150, 255, 150)); /* !!*/
        defaultAtom = new Atom(255, 100, 200);
    }
    boolean transformed;
    Matrix3D mat;
    float xmin, xmax, ymin, ymax, zmin, zmax;

    XYZChemModel() {
        mat = new Matrix3D();
        mat.xrot(20);
        mat.yrot(30);
    }

    /** Create a Chemical model by parsing an input stream */
    XYZChemModel(InputStream is) throws Exception {
        this();
        StreamTokenizer st = new StreamTokenizer(
                new BufferedReader(new InputStreamReader(is, "UTF-8")));
        st.eolIsSignificant(true);
        st.commentChar('#');

        try {
            scan:
            while (true) {
                switch (st.nextToken()) {
                    case StreamTokenizer.TT_EOF:
                        break scan;
                    default:
                        break;
                    case StreamTokenizer.TT_WORD:
                        String name = st.sval;
                        double x = 0,
                         y = 0,
                         z = 0;
                        if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                            x = st.nval;
                            if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                                y = st.nval;
                                if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                                    z = st.nval;
                                }
                            }
                        }
                        addVert(name, (float) x, (float) y, (float) z);
                        while (st.ttype != StreamTokenizer.TT_EOL
                                && st.ttype != StreamTokenizer.TT_EOF) {
                            st.nextToken();
                        }

                }   // end Switch

            }  // end while

            is.close();

        } // end Try
        catch (IOException e) {
        }

        if (st.ttype != StreamTokenizer.TT_EOF) {
            throw new Exception(st.toString());
        }

    }  // end XYZChemModel()

    /** Add a vertex to this model */
    int addVert(String name, float x, float y, float z) {
        int i = nvert;
        if (i >= maxvert) {
            if (vert == null) {
                maxvert = 100;
                vert = new float[maxvert * 3];
                atoms = new Atom[maxvert];
            } else {
                maxvert *= 2;
                float nv[] = new float[maxvert * 3];
                System.arraycopy(vert, 0, nv, 0, vert.length);
                vert = nv;
                Atom na[] = new Atom[maxvert];
                System.arraycopy(atoms, 0, na, 0, atoms.length);
                atoms = na;
            }
        }
        Atom a = atomTable.get(name.toLowerCase());
        if (a == null) {
            a = defaultAtom;
        }
        atoms[i] = a;
        i *= 3;
        vert[i] = x;
        vert[i + 1] = y;
        vert[i + 2] = z;
        return nvert++;
    }

    /** Transform all the points in this model */
    void transform() {
        if (transformed || nvert <= 0) {
            return;
        }
        if (tvert == null || tvert.length < nvert * 3) {
            tvert = new int[nvert * 3];
        }
        mat.transform(vert, tvert, nvert);
        transformed = true;
    }

    /** Paint this model to a graphics context.  It uses the matrix associated
    with this model to map from model space to screen space.
    The next version of the browser should have double buffering,
    which will make this *much* nicer */
    void paint(Graphics g) {
        if (vert == null || nvert <= 0) {
            return;
        }
        transform();
        int v[] = tvert;
        int zs[] = ZsortMap;
        if (zs == null) {
            ZsortMap = zs = new int[nvert];
            for (int i = nvert; --i >= 0;) {
                zs[i] = i * 3;
            }
        }

        /*
         * I use a bubble sort since from one iteration to the next, the sort
         * order is pretty stable, so I just use what I had last time as a
         * "guess" of the sorted order.  With luck, this reduces O(N log N)
         * to O(N)
         */

        for (int i = nvert - 1; --i >= 0;) {
            boolean flipped = false;
            for (int j = 0; j <= i; j++) {
                int a = zs[j];
                int b = zs[j + 1];
                if (v[a + 2] > v[b + 2]) {
                    zs[j + 1] = a;
                    zs[j] = b;
                    flipped = true;
                }
            }
            if (!flipped) {
                break;
            }
        }

        int lim = nvert;
        if (lim <= 0 || nvert <= 0) {
            return;
        }
        for (int i = 0; i < lim; i++) {
            int j = zs[i];
            int grey = v[j + 2];
            if (grey < 0) {
                grey = 0;
            }
            if (grey > 15) {
                grey = 15;
            }
            // g.drawString(names[i], v[j], v[j+1]);
            atoms[j / 3].paint(g, v[j], v[j + 1], grey);
            // g.drawImage(iBall, v[j] - (iBall.width >> 1), v[j + 1] -
            // (iBall.height >> 1));
        }
    }

    /** Find the bounding box of this model */
    void findBB() {
        if (nvert <= 0) {
            return;
        }
        float v[] = vert;
        float _xmin = v[0], _xmax = _xmin;
        float _ymin = v[1], _ymax = _ymin;
        float _zmin = v[2], _zmax = _zmin;
        for (int i = nvert * 3; (i -= 3) > 0;) {
            float x = v[i];
            if (x < _xmin) {
                _xmin = x;
            }
            if (x > _xmax) {
                _xmax = x;
            }
            float y = v[i + 1];
            if (y < _ymin) {
                _ymin = y;
            }
            if (y > _ymax) {
                _ymax = y;
            }
            float z = v[i + 2];
            if (z < _zmin) {
                _zmin = z;
            }
            if (z > _zmax) {
                _zmax = z;
            }
        }
        this.xmax = _xmax;
        this.xmin = _xmin;
        this.ymax = _ymax;
        this.ymin = _ymin;
        this.zmax = _zmax;
        this.zmin = _zmin;
    }
}


/** An applet to put a Chemical model into a page */
@SuppressWarnings("serial")
public class XYZApp extends Applet implements Runnable, MouseListener,
        MouseMotionListener {

    XYZChemModel md;
    boolean painted = true;
    float xfac;
    int prevx, prevy;
    float scalefudge = 1;
    Matrix3D amat = new Matrix3D(), tmat = new Matrix3D();
    String mdname = null;
    String message = null;
    Image backBuffer;
    Graphics backGC;
    Dimension backSize;

    private synchronized void newBackBuffer() {
        backBuffer = createImage(getSize().width, getSize().height);
        if (backGC != null) {
            backGC.dispose();
        }
        backGC = backBuffer.getGraphics();
        backSize = getSize();
    }

    @Override
    public void init() {
        mdname = getParameter("model");
        try {
            scalefudge = Float.valueOf(getParameter("scale")).floatValue();
        } catch (Exception ignored) {
        }
        amat.yrot(20);
        amat.xrot(20);
        if (mdname == null) {
            mdname = "model.obj";
        }
        resize(getSize().width <= 20 ? 400 : getSize().width,
                getSize().height <= 20 ? 400 : getSize().height);
        newBackBuffer();
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    @Override
    public void destroy() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
    }

    @Override
    public void run() {
        InputStream is = null;
        try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            is = getClass().getResourceAsStream(mdname);
            XYZChemModel m = new XYZChemModel(is);
            Atom.setApplet(this);
            md = m;
            m.findBB();
            float xw = m.xmax - m.xmin;
            float yw = m.ymax - m.ymin;
            float zw = m.zmax - m.zmin;
            if (yw > xw) {
                xw = yw;
            }
            if (zw > xw) {
                xw = zw;
            }
            float f1 = getSize().width / xw;
            float f2 = getSize().height / xw;
            xfac = 0.7f * (f1 < f2 ? f1 : f2) * scalefudge;
        } catch (Exception e) {
            Logger.getLogger(XYZApp.class.getName()).log(Level.SEVERE, null, e);
            md = null;
            message = e.toString();
        }
        try {
            if (is != null) {
                is.close();
            }
        } catch (Exception ignored) {
        }
        repaint();
    }

    @Override
    public void start() {
        if (md == null && message == null) {
            new Thread(this).start();
        }
    }

    @Override
    public void stop() {
    }
    /* event handling */

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        prevx = e.getX();
        prevy = e.getY();
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        tmat.unit();
        float xtheta = (prevy - y) * (360.0f / getSize().width);
        float ytheta = (x - prevx) * (360.0f / getSize().height);
        tmat.xrot(xtheta);
        tmat.yrot(ytheta);
        amat.mult(tmat);
        if (painted) {
            painted = false;
            repaint();
        }
        prevx = x;
        prevy = y;
        e.consume();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void update(Graphics g) {
        if (backBuffer == null) {
            g.clearRect(0, 0, getSize().width, getSize().height);
        }
        paint(g);
    }

    @Override
    public void paint(Graphics g) {
        if (md != null) {
            md.mat.unit();
            md.mat.translate(-(md.xmin + md.xmax) / 2,
                    -(md.ymin + md.ymax) / 2,
                    -(md.zmin + md.zmax) / 2);
            md.mat.mult(amat);
            // md.mat.scale(xfac, -xfac, 8 * xfac / getSize().width);
            md.mat.scale(xfac, -xfac, 16 * xfac / getSize().width);
            md.mat.translate(getSize().width / 2, getSize().height / 2, 8);
            md.transformed = false;
            if (backBuffer != null) {
                if (!backSize.equals(getSize())) {
                    newBackBuffer();
                }
                backGC.setColor(getBackground());
                backGC.fillRect(0, 0, getSize().width, getSize().height);
                md.paint(backGC);
                g.drawImage(backBuffer, 0, 0, this);
            } else {
                md.paint(g);
            }
            setPainted();
        } else if (message != null) {
            g.drawString("Error in model:", 3, 20);
            g.drawString(message, 10, 40);
        }
    }

    private synchronized void setPainted() {
        painted = true;
        notifyAll();
    }

    @Override
    public String getAppletInfo() {
        return "Title: XYZApp \nAuthor: James Gosling \nAn applet to put"
                + " a Chemical model into a page.";
    }

    @Override
    public String[][] getParameterInfo() {
        String[][] info = {
            { "model", "path string", "The path to the model to be displayed"
                + " in .xyz format "
                + "(see http://chem.leeds.ac.uk/Project/MIME.html)."
                + "  Default is model.obj." },
            { "scale", "float", "Scale factor.  Default is 1 (i.e. no scale)." }
        };
        return info;
    }
}   // end class XYZApp


class Atom {

    private static Applet applet;
    private static byte[] data;
    private final static int R = 40;
    private final static int hx = 15;
    private final static int hy = 15;
    private final static int bgGrey = 192;
    private final static int nBalls = 16;
    private static int maxr;
    private int Rl;
    private int Gl;
    private int Bl;
    private Image balls[];

    static {
        data = new byte[R * 2 * R * 2];
        int mr = 0;
        for (int Y = 2 * R; --Y >= 0;) {
            int x0 = (int) (Math.sqrt(R * R - (Y - R) * (Y - R)) + 0.5);
            int p = Y * (R * 2) + R - x0;
            for (int X = -x0; X < x0; X++) {
                int x = X + hx;
                int y = Y - R + hy;
                int r = (int) (Math.sqrt(x * x + y * y) + 0.5);
                if (r > mr) {
                    mr = r;
                }
                data[p++] = r <= 0 ? 1 : (byte) r;
            }
        }
        maxr = mr;
    }

    static void setApplet(Applet app) {
        applet = app;
    }

    Atom(int Rl, int Gl, int Bl) {
        this.Rl = Rl;
        this.Gl = Gl;
        this.Bl = Bl;
    }

    private int blend(int fg, int bg, float fgfactor) {
        return (int) (bg + (fg - bg) * fgfactor);
    }

    private void Setup() {
        balls = new Image[nBalls];
        byte red[] = new byte[256];
        red[0] = (byte) bgGrey;
        byte green[] = new byte[256];
        green[0] = (byte) bgGrey;
        byte blue[] = new byte[256];
        blue[0] = (byte) bgGrey;
        for (int r = 0; r < nBalls; r++) {
            float b = (float) (r + 1) / nBalls;
            for (int i = maxr; i >= 1; --i) {
                float d = (float) i / maxr;
                red[i] = (byte) blend(blend(Rl, 255, d), bgGrey, b);
                green[i] = (byte) blend(blend(Gl, 255, d), bgGrey, b);
                blue[i] = (byte) blend(blend(Bl, 255, d), bgGrey, b);
            }
            IndexColorModel model = new IndexColorModel(8, maxr + 1,
                    red, green, blue, 0);
            balls[r] = applet.createImage(
                    new MemoryImageSource(R * 2, R * 2, model, data, 0, R * 2));
        }
    }

    void paint(Graphics gc, int x, int y, int r) {
        Image ba[] = balls;
        if (ba == null) {
            Setup();
            ba = balls;
        }
        Image i = ba[r];
        int size = 10 + r;
        gc.drawImage(i, x - (size >> 1), y - (size >> 1), size, size, applet);
    }
}
