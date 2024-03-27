import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.View;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;

/**
 * Test case for
 * <a href="https://bugs.openjdk.org/browse/JDK-8323801">JDK-8323801</a>:
 * {@code <s>} doesn't strikethrough the text.
 * <p>
 * If {@code <s>} is inside {@code <u>} or {@code <span>} with
 * {@code text-decoration: underline}, the text inside the {@code <s>} tag
 * is rendered without the strikethrough attribute.
 * Yet, if you replace {@code <s>} with {@code <strike>}, the text is rendered
 * with both underline and strikethrough attributes.
 */
public final class HTMLTextDecoration {
    private static final String HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>underline + line-through text</title>
            <!--h1><code>text-decoration</code></h1-->
                <style>
                .underline   { text-decoration: underline }
                .lineThrough { text-decoration: line-through }
                </style>
            </head>
            <body>
            <p><u><span style='text-decoration: line-through'>underline + line-through?</span></u></p>
            <p><s><span style='text-decoration: underline'>underline + line-through?</span></s></p>
            <p><strike><span style='text-decoration: underline'>underline + line-through?</span></strike></p>
            
            <p><span style='text-decoration: line-through'><span style='text-decoration: underline'>underline + line-through?</span></span></p>
            <p><span style='text-decoration: underline'><span style='text-decoration: line-through'>underline + line-through?</span></span></p>
            
            <p style='text-decoration: line-through'><u>underline + line-through?</u></p>
            <p style='text-decoration: underline'><s>underline + line-through?</s></p>
            <p style='text-decoration: underline'><strike>underline + line-through?</strike></p>

            <p style='text-decoration: line-through'><span style='text-decoration: underline'>underline + line-through?</span></p>
            <p style='text-decoration: underline'><span style='text-decoration: line-through'>underline + line-through?</span></p>
            
            <p class="underline"><span class="lineThrough">underline + line-through?</span></p>
            <p class="underline"><s>underline + line-through?</s></p>
            <p class="underline"><strike>underline + line-through?</strike></p>

            <p class="lineThrough"><span class="underline">underline + line-through?</span></p>
            <p class="lineThrough"><u>underline + line-through?</u></p>

            <div class="underline"><span class="lineThrough">underline + line-through?</span></div>
            <div class="underline"><s>underline + line-through?</s></div>
            <div class="underline"><strike>underline + line-through?</strike></div>

            <div class="lineThrough"><span class="underline">underline + line-through?</span></div>
            <div class="lineThrough"><u>underline + line-through?</u></div>

            <div class="underline"><p class="lineThrough">underline + line-through?</p></div>
            <div class="lineThrough"><p class="underline">underline + line-through?</p></div>

            <div class="underline"><div class="lineThrough">underline + line-through?</div></div>
            <div class="lineThrough"><div class="underline">underline + line-through?</div></div>
            </body>
            </html>
            """;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(HTMLTextDecoration::createUI);
    }

    private static void createUI() {
        JEditorPane html = new JEditorPane("text/html", HTML);
        html.setEditable(false);

        Dimension size = html.getPreferredSize();
        html.setSize(size);

        BufferedImage image = new BufferedImage(size.width, size.height,
                                                BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        html.paint(g);
        g.dispose();

        try {
            ImageIO.write(image, "png",
                          new File("html.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println();
        System.out.println("Result:");
//        StyledDocument document = (StyledDocument) html.getDocument();
//        StyleSheet styleSheet = ((HTMLDocument) document).getStyleSheet();
//        Element root = document.getDefaultRootElement();
//        Element body = root.getElement(1);
//        for (int i = 0; i < body.getElementCount(); i++) {
//            Element p = body.getElement(i);
//            Element content = getContent(p);
//            AttributeSet attr = content.getAttributes();
//            Object decoration = attr.getAttribute(CSS.Attribute.TEXT_DECORATION);
////            String strDecoration = decoration.toString();
////            Font font = document.getFont(attr);
//            System.out.println(i + ": " + decoration);
//        }

        System.out.println("\n\n------ Views ------");
        View rootView = html.getUI().getRootView(html);
        View bodyView = rootView.getView(1).getView(1);
        for (int i = 0; i < bodyView.getViewCount(); i++) {
            View pView = bodyView.getView(i);
            View contentView = getContentView(pView);
            AttributeSet attr = contentView.getAttributes();
            Object decoration = attr.getAttribute(CSS.Attribute.TEXT_DECORATION);
            System.out.println(i + ": " + decoration);
        }
        //((AbstractDocument) html.getDocument()).dump(System.out);

        JFrame frame = new JFrame("underline + line-through text");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.add(new JScrollPane(html));

        frame.pack();

        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static View getContentView(View parent) {
        View view = parent.getView(0);
        return view.getViewCount() > 0 ? getContentView(view) : view;
    }

    private static Element getContent(Element branch) {
        Element element = branch.getElement(0);
        return element.getElementCount() > 0
               ? getContent(element)
               : element;
    }
}
