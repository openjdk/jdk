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
import java.awt.Graphics;
import java.awt.Color;
import java.awt.event.*;
import java.io.*;
import java.net.URL;


/* A set of classes to parse, represent and display 3D wireframe models
represented in Wavefront .obj format. */
@SuppressWarnings("serial")
class FileFormatException extends Exception {

    public FileFormatException(String s) {
        super(s);
    }
}


/** The representation of a 3D model */
final class Model3D {

    float vert[];
    int tvert[];
    int nvert, maxvert;
    int con[];
    int ncon, maxcon;
    boolean transformed;
    Matrix3D mat;
    float xmin, xmax, ymin, ymax, zmin, zmax;

    Model3D() {
        mat = new Matrix3D();
        mat.xrot(20);
        mat.yrot(30);
    }

    /** Create a 3D model by parsing an input stream */
    Model3D(InputStream is) throws IOException, FileFormatException {
        this();
        StreamTokenizer st = new StreamTokenizer(
                new BufferedReader(new InputStreamReader(is, "UTF-8")));
        st.eolIsSignificant(true);
        st.commentChar('#');
        scan:
        while (true) {
            switch (st.nextToken()) {
                default:
                    break scan;
                case StreamTokenizer.TT_EOL:
                    break;
                case StreamTokenizer.TT_WORD:
                    if ("v".equals(st.sval)) {
                        double x = 0, y = 0, z = 0;
                        if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                            x = st.nval;
                            if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                                y = st.nval;
                                if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                                    z = st.nval;
                                }
                            }
                        }
                        addVert((float) x, (float) y, (float) z);
                        while (st.ttype != StreamTokenizer.TT_EOL && st.ttype
                                != StreamTokenizer.TT_EOF) {
                            st.nextToken();
                        }
                    } else if ("f".equals(st.sval) || "fo".equals(st.sval) || "l".
                            equals(st.sval)) {
                        int start = -1;
                        int prev = -1;
                        int n = -1;
                        while (true) {
                            if (st.nextToken() == StreamTokenizer.TT_NUMBER) {
                                n = (int) st.nval;
                                if (prev >= 0) {
                                    add(prev - 1, n - 1);
                                }
                                if (start < 0) {
                                    start = n;
                                }
                                prev = n;
                            } else if (st.ttype == '/') {
                                st.nextToken();
                            } else {
                                break;
                            }
                        }
                        if (start >= 0) {
                            add(start - 1, prev - 1);
                        }
                        if (st.ttype != StreamTokenizer.TT_EOL) {
                            break scan;
                        }
                    } else {
                        while (st.nextToken() != StreamTokenizer.TT_EOL
                                && st.ttype != StreamTokenizer.TT_EOF) {
                            // no-op
                        }
                    }
            }
        }
        is.close();
        if (st.ttype != StreamTokenizer.TT_EOF) {
            throw new FileFormatException(st.toString());
        }
    }

    /** Add a vertex to this model */
    int addVert(float x, float y, float z) {
        int i = nvert;
        if (i >= maxvert) {
            if (vert == null) {
                maxvert = 100;
                vert = new float[maxvert * 3];
            } else {
                maxvert *= 2;
                float nv[] = new float[maxvert * 3];
                System.arraycopy(vert, 0, nv, 0, vert.length);
                vert = nv;
            }
        }
        i *= 3;
        vert[i] = x;
        vert[i + 1] = y;
        vert[i + 2] = z;
        return nvert++;
    }

    /** Add a line from vertex p1 to vertex p2 */
    void add(int p1, int p2) {
        int i = ncon;
        if (p1 >= nvert || p2 >= nvert) {
            return;
        }
        if (i >= maxcon) {
            if (con == null) {
                maxcon = 100;
                con = new int[maxcon];
            } else {
                maxcon *= 2;
                int nv[] = new int[maxcon];
                System.arraycopy(con, 0, nv, 0, con.length);
                con = nv;
            }
        }
        if (p1 > p2) {
            int t = p1;
            p1 = p2;
            p2 = t;
        }
        con[i] = (p1 << 16) | p2;
        ncon = i + 1;
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

    /* Quick Sort implementation
     */
    private void quickSort(int a[], int left, int right) {
        int leftIndex = left;
        int rightIndex = right;
        int partionElement;
        if (right > left) {

            /* Arbitrarily establishing partition element as the midpoint of
             * the array.
             */
            partionElement = a[(left + right) / 2];

            // loop through the array until indices cross
            while (leftIndex <= rightIndex) {
                /* find the first element that is greater than or equal to
                 * the partionElement starting from the leftIndex.
                 */
                while ((leftIndex < right) && (a[leftIndex] < partionElement)) {
                    ++leftIndex;
                }

                /* find an element that is smaller than or equal to
                 * the partionElement starting from the rightIndex.
                 */
                while ((rightIndex > left) && (a[rightIndex] > partionElement)) {
                    --rightIndex;
                }

                // if the indexes have not crossed, swap
                if (leftIndex <= rightIndex) {
                    swap(a, leftIndex, rightIndex);
                    ++leftIndex;
                    --rightIndex;
                }
            }

            /* If the right index has not reached the left side of array
             * must now sort the left partition.
             */
            if (left < rightIndex) {
                quickSort(a, left, rightIndex);
            }

            /* If the left index has not reached the right side of array
             * must now sort the right partition.
             */
            if (leftIndex < right) {
                quickSort(a, leftIndex, right);
            }

        }
    }

    private void swap(int a[], int i, int j) {
        int T;
        T = a[i];
        a[i] = a[j];
        a[j] = T;
    }

    /** eliminate duplicate lines */
    void compress() {
        int limit = ncon;
        int c[] = con;
        quickSort(con, 0, ncon - 1);
        int d = 0;
        int pp1 = -1;
        for (int i = 0; i < limit; i++) {
            int p1 = c[i];
            if (pp1 != p1) {
                c[d] = p1;
                d++;
            }
            pp1 = p1;
        }
        ncon = d;
    }
    static Color gr[];

    /** Paint this model to a graphics context.  It uses the matrix associated
    with this model to map from model space to screen space.
    The next version of the browser should have double buffering,
    which will make this *much* nicer */
    void paint(Graphics g) {
        if (vert == null || nvert <= 0) {
            return;
        }
        transform();
        if (gr == null) {
            gr = new Color[16];
            for (int i = 0; i < 16; i++) {
                int grey = (int) (170 * (1 - Math.pow(i / 15.0, 2.3)));
                gr[i] = new Color(grey, grey, grey);
            }
        }
        int lg = 0;
        int lim = ncon;
        int c[] = con;
        int v[] = tvert;
        if (lim <= 0 || nvert <= 0) {
            return;
        }
        for (int i = 0; i < lim; i++) {
            int T = c[i];
            int p1 = ((T >> 16) & 0xFFFF) * 3;
            int p2 = (T & 0xFFFF) * 3;
            int grey = v[p1 + 2] + v[p2 + 2];
            if (grey < 0) {
                grey = 0;
            }
            if (grey > 15) {
                grey = 15;
            }
            if (grey != lg) {
                lg = grey;
                g.setColor(gr[grey]);
            }
            g.drawLine(v[p1], v[p1 + 1],
                    v[p2], v[p2 + 1]);
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


/** An applet to put a 3D model into a page */
@SuppressWarnings("serial")
public class ThreeD extends Applet
        implements Runnable, MouseListener, MouseMotionListener {

    Model3D md;
    boolean painted = true;
    float xfac;
    int prevx, prevy;
    float scalefudge = 1;
    Matrix3D amat = new Matrix3D(), tmat = new Matrix3D();
    String mdname = null;
    String message = null;

    @Override
    public void init() {
        mdname = getParameter("model");
        try {
            scalefudge = Float.valueOf(getParameter("scale")).floatValue();
        } catch (Exception ignored) {
            // fall back to default scalefudge = 1
        }
        amat.yrot(20);
        amat.xrot(20);
        if (mdname == null) {
            mdname = "model.obj";
        }
        resize(getSize().width <= 20 ? 400 : getSize().width,
                getSize().height <= 20 ? 400 : getSize().height);
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
            is = new URL(getDocumentBase(), mdname).openStream();
            Model3D m = new Model3D(is);
            md = m;
            m.findBB();
            m.compress();
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
            md = null;
            message = e.toString();
        }
        try {
            if (is != null) {
                is.close();
            }
        } catch (Exception e) {
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
        float xtheta = (prevy - y) * 360.0f / getSize().width;
        float ytheta = (x - prevx) * 360.0f / getSize().height;
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
    public void paint(Graphics g) {
        if (md != null) {
            md.mat.unit();
            md.mat.translate(-(md.xmin + md.xmax) / 2,
                    -(md.ymin + md.ymax) / 2,
                    -(md.zmin + md.zmax) / 2);
            md.mat.mult(amat);
            md.mat.scale(xfac, -xfac, 16 * xfac / getSize().width);
            md.mat.translate(getSize().width / 2, getSize().height / 2, 8);
            md.transformed = false;
            md.paint(g);
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
        return "Title: ThreeD \nAuthor: James Gosling? \n"
                + "An applet to put a 3D model into a page.";
    }

    @Override
    public String[][] getParameterInfo() {
        String[][] info = {
            { "model", "path string", "The path to the model to be displayed." },
            { "scale", "float", "The scale of the model.  Default is 1." }
        };
        return info;
    }
}
