/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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


/*
 */

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.lang.management.*;
/**
 * Demo code which plots the memory usage by all memory pools.
 * The memory usage is sampled at some time interval using
 * java.lang.management API. This demo code is modified based
 * java2d MemoryMonitor demo.
 */
public class MemoryMonitor extends JPanel {

    private static final long serialVersionUID = -3463003810776195761L;
    static JCheckBox dateStampCB = new JCheckBox("Output Date Stamp");
    public Surface surf;
    JPanel controls;
    boolean doControls;
    JTextField tf;
    // Get memory pools.
    static java.util.List<MemoryPoolMXBean> mpools =
        ManagementFactory.getMemoryPoolMXBeans();
    // Total number of memory pools.
    static int numPools = mpools.size();

    public MemoryMonitor() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder(new EtchedBorder(), "Memory Monitor"));
        add(surf = new Surface());
        controls = new JPanel();
        controls.setPreferredSize(new Dimension(135,80));
        Font font = new Font("serif", Font.PLAIN, 10);
        JLabel label = new JLabel("Sample Rate");
        label.setFont(font);
        label.setForeground(Color.red);
        controls.add(label);
        tf = new JTextField("1000");
        tf.setPreferredSize(new Dimension(45,20));
        controls.add(tf);
        controls.add(label = new JLabel("ms"));
        label.setFont(font);
        label.setForeground(Color.red);
        controls.add(dateStampCB);
        dateStampCB.setFont(font);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
               removeAll();
               if ((doControls = !doControls)) {
                   surf.stop();
                   add(controls);
               } else {
                   try {
                       surf.sleepAmount = Long.parseLong(tf.getText().trim());
                   } catch (Exception ex) {}
                   surf.start();
                   add(surf);
               }
               validate();
               repaint();
            }
        });
    }


    public class Surface extends JPanel implements Runnable {

        public Thread thread;
        public long sleepAmount = 1000;
        public int  usageHistCount = 20000;
        private int w, h;
        private BufferedImage bimg;
        private Graphics2D big;
        private Font font = new Font("Times New Roman", Font.PLAIN, 11);
        private int columnInc;
        private float usedMem[][];
        private int ptNum[];
        private int ascent, descent;
        private Rectangle graphOutlineRect = new Rectangle();
        private Rectangle2D mfRect = new Rectangle2D.Float();
        private Rectangle2D muRect = new Rectangle2D.Float();
        private Line2D graphLine = new Line2D.Float();
        private Color graphColor = new Color(46, 139, 87);
        private Color mfColor = new Color(0, 100, 0);
        private String usedStr;


        public Surface() {
            setBackground(Color.black);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (thread == null) start(); else stop();
                }
            });
            usedMem = new float[numPools][];
            ptNum = new int[numPools];
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(135,80);
        }


        @Override
        public void paint(Graphics g) {

            if (big == null) {
                return;
            }

            big.setBackground(getBackground());
            big.clearRect(0,0,w,h);


            h = h / ((numPools + numPools%2) / 2);
            w = w / 2;

            int k=0; // index of memory pool.
            for (int i=0; i < 2;i++) {
               for (int j=0; j < (numPools + numPools%2)/ 2; j++) {
                 plotMemoryUsage(w*i,h*j,w,h,k);
                 if (++k >= numPools) {
                    i = 3;
                    j = (numPools + numPools%2)/ 2;
                    break;
                 }
               }
            }
            g.drawImage(bimg, 0, 0, this);
        }

        public void plotMemoryUsage(int x1, int y1, int x2, int y2, int npool) {

            MemoryPoolMXBean mp = mpools.get(npool);
            float usedMemory =  mp.getUsage().getUsed();
            float totalMemory =  mp.getUsage().getMax();

            // .. Draw allocated and used strings ..
            big.setColor(Color.green);

            // Print Max memory allocated for this memory pool.
            big.drawString(String.valueOf((int)totalMemory/1024) + "K Max ", x1+4.0f, (float) y1 + ascent+0.5f);
            big.setColor(Color.yellow);

            // Print the memory pool name.
            big.drawString(mp.getName(),  x1+x2/2, (float) y1 + ascent+0.5f);

            // Print the memory used by this memory pool.
            usedStr = String.valueOf((int)usedMemory/1024)
                + "K used";
            big.setColor(Color.green);
            big.drawString(usedStr, x1+4, y1+y2-descent);

            // Calculate remaining size
            float ssH = ascent + descent;
            float remainingHeight = (float) (y2 - (ssH*2) - 0.5f);
            float blockHeight = remainingHeight/10;
            float blockWidth = 20.0f;
            float remainingWidth = (float) (x2 - blockWidth - 10);

            // .. Memory Free ..
            big.setColor(mfColor);
            int MemUsage = (int) (((totalMemory - usedMemory) / totalMemory) * 10);
            int i = 0;
            for ( ; i < MemUsage ; i++) {
                mfRect.setRect(x1+5,(float) y1+ssH+i*blockHeight,
                                blockWidth,(float) blockHeight-1);
                big.fill(mfRect);
            }

            // .. Memory Used ..
            big.setColor(Color.green);
            for ( ; i < 10; i++)  {
                muRect.setRect(x1+5,(float) y1 + ssH+i*blockHeight,
                                blockWidth,(float) blockHeight-1);
                big.fill(muRect);
            }

            // .. Draw History Graph ..
            if (remainingWidth <= 30) remainingWidth = (float)30;
            if (remainingHeight <= ssH) remainingHeight = (float)ssH;
            big.setColor(graphColor);
            int graphX = x1+30;
            int graphY = y1 + (int) ssH;
            int graphW = (int) remainingWidth;
            int graphH = (int) remainingHeight;

            graphOutlineRect.setRect(graphX, graphY, graphW, graphH);
            big.draw(graphOutlineRect);

            int graphRow = graphH/10;

            // .. Draw row ..
            for (int j = graphY; j <= graphH+graphY; j += graphRow) {
                graphLine.setLine(graphX,j,graphX+graphW,j);
                big.draw(graphLine);
            }

            // .. Draw animated column movement ..
            int graphColumn = graphW/15;

            if (columnInc == 0) {
                columnInc = graphColumn;
            }

            for (int j = graphX+columnInc; j < graphW+graphX; j+=graphColumn) {
                graphLine.setLine(j,graphY,j,graphY+graphH);
                big.draw(graphLine);
            }

            --columnInc;

            // Plot memory usage by this memory pool.
            if (usedMem[npool] == null) {
                usedMem[npool] = new float[usageHistCount];
                ptNum[npool] = 0;
            }

            // save memory usage history.
            usedMem[npool][ptNum[npool]] = usedMemory;

            big.setColor(Color.yellow);

            int w1; // width of memory usage history.
            if (ptNum[npool] > graphW) {
                w1 = graphW;
            } else {
                w1 = ptNum[npool];
            }


            for (int j=graphX+graphW-w1, k=ptNum[npool]-w1; k < ptNum[npool];
                                                                k++, j++) {
                 if (k != 0) {
                     if (usedMem[npool][k] != usedMem[npool][k-1]) {
                         int h1 = (int)(graphY + graphH * ((totalMemory -usedMem[npool][k-1])/totalMemory));
                         int h2 = (int)(graphY + graphH * ((totalMemory -usedMem[npool][k])/totalMemory));
                         big.drawLine(j-1, h1, j, h2);
                     } else {
                         int h1 = (int)(graphY + graphH * ((totalMemory -usedMem[npool][k])/totalMemory));
                         big.fillRect(j, h1, 1, 1);
                     }
                 }
            }
            if (ptNum[npool]+2 == usedMem[npool].length) {
                // throw out oldest point
                for (int j = 1;j < ptNum[npool]; j++) {
                     usedMem[npool][j-1] = usedMem[npool][j];
                }
                --ptNum[npool];
            } else {
                ptNum[npool]++;
            }
        }


        public void start() {
            thread = new Thread(this);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setName("MemoryMonitor");
            thread.start();
        }


        public synchronized void stop() {
            thread = null;
            notify();
        }

        @Override
        public void run() {

            Thread me = Thread.currentThread();

            while (thread == me && !isShowing() || getSize().width == 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) { return; }
            }

            while (thread == me && isShowing()) {
                Dimension d = getSize();
                if (d.width != w || d.height != h) {
                    w = d.width;
                    h = d.height;
                    bimg = (BufferedImage) createImage(w, h);
                    big = bimg.createGraphics();
                    big.setFont(font);
                    FontMetrics fm = big.getFontMetrics(font);
                    ascent = (int) fm.getAscent();
                    descent = (int) fm.getDescent();
                }
                repaint();
                try {
                    Thread.sleep(sleepAmount);
                } catch (InterruptedException e) { break; }
                if (MemoryMonitor.dateStampCB.isSelected()) {
                     System.out.println(new Date().toString() + " " + usedStr);
                }
            }
            thread = null;
        }
    }


    // Test thread to consume memory
    static class Memeater extends ClassLoader implements Runnable {
        Object y[];
        public Memeater() {}
        @Override
        public void run() {
            y = new Object[10000000];
            int k =0;
            while(true) {
                 if (k == 5000000) k=0;
                 y[k++] = new Object();
                 try {
                     Thread.sleep(20);
                 } catch (Exception x){}

                 // to consume perm gen storage
                 try {
                     // the classes are small so we load 10 at a time
                     for (int i=0; i<10; i++) {
                        loadNext();
                     }
                 } catch (ClassNotFoundException x) {
                   // ignore exception
                 }

           }

        }

        Class<?> loadNext() throws ClassNotFoundException {

            // public class TestNNNNNN extends java.lang.Object{
            // public TestNNNNNN();
            //   Code:
            //    0:    aload_0
            //    1:    invokespecial   #1; //Method java/lang/Object."<init>":()V
            //    4:    return
            // }

            int begin[] = {
                0xca, 0xfe, 0xba, 0xbe, 0x00, 0x00, 0x00, 0x30,
                0x00, 0x0a, 0x0a, 0x00, 0x03, 0x00, 0x07, 0x07,
                0x00, 0x08, 0x07, 0x00, 0x09, 0x01, 0x00, 0x06,
                0x3c, 0x69, 0x6e, 0x69, 0x74, 0x3e, 0x01, 0x00,
                0x03, 0x28, 0x29, 0x56, 0x01, 0x00, 0x04, 0x43,
                0x6f, 0x64, 0x65, 0x0c, 0x00, 0x04, 0x00, 0x05,
                0x01, 0x00, 0x0a, 0x54, 0x65, 0x73, 0x74 };

            int end [] = {
                0x01, 0x00, 0x10,
                0x6a, 0x61, 0x76, 0x61, 0x2f, 0x6c, 0x61, 0x6e,
                0x67, 0x2f, 0x4f, 0x62, 0x6a, 0x65, 0x63, 0x74,
                0x00, 0x21, 0x00, 0x02, 0x00, 0x03, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x04,
                0x00, 0x05, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00,
                0x00, 0x11, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x05, 0x2a, 0xb7, 0x00, 0x01, 0xb1, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00 };


            // TestNNNNNN

            String name = "Test" + Integer.toString(count++);

            byte value[];
            try {
                value = name.substring(4).getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException x) {
                throw new Error();
            }

            // construct class file

            int len = begin.length + value.length + end.length;
            byte b[] = new byte[len];
            int pos=0;
            for (int i: begin) {
                b[pos++] = (byte) i;
            }
            for (byte v: value) {
                b[pos++] = v;
            }
            for (int e: end) {
                b[pos++] = (byte) e;
            }

            return defineClass(name, b, 0, b.length);

        }
        static int count = 100000;

    }

    public static void main(String s[]) {
        final MemoryMonitor demo = new MemoryMonitor();
        WindowListener l = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {System.exit(0);}
            @Override
            public void windowDeiconified(WindowEvent e) { demo.surf.start(); }
            @Override
            public void windowIconified(WindowEvent e) { demo.surf.stop(); }
        };
        JFrame f = new JFrame("MemoryMonitor");
        f.addWindowListener(l);
        f.getContentPane().add("Center", demo);
        f.pack();
        f.setSize(new Dimension(400,500));
        f.setVisible(true);
        demo.surf.start();
        Thread thr = new Thread(new Memeater());
        thr.start();
    }

}
