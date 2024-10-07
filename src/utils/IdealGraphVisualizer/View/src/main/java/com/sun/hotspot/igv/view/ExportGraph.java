/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

package com.sun.hotspot.igv.view;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGraphics2D;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;


public class ExportGraph implements ExportCookie {

    @Override
    public void export(File f) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            String lcFileName = f.getName().toLowerCase();
            if (lcFileName.endsWith(".pdf")) {
                exportToPDF(editor, f);
            } else if (lcFileName.endsWith(".svg")) {
                exportToSVG(editor, f);
            } else {
                NotifyDescriptor message = new NotifyDescriptor.Message("Unknown image file extension: expected either '.pdf' or '.svg'", NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notifyLater(message);
            }
        }
    }

    private static void exportToPDF(EditorTopComponent editor, File f) {
        int width = editor.getSceneBounds().width;
        int height = editor.getSceneBounds().height;
        com.lowagie.text.Document document = new Document(new Rectangle(width, height));
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, Files.newOutputStream(f.toPath()));
            writer.setCloseStream(true);
            document.open();
            PdfContentByte contentByte = writer.getDirectContent();
            PdfTemplate template = contentByte.createTemplate(width, height);
            PdfGraphics2D pdfGenerator = new PdfGraphics2D(contentByte, width, height);
            editor.paintScene(pdfGenerator);
            pdfGenerator.dispose();
            contentByte.addTemplate(template, 0, 0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (document.isOpen()) {
                document.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static void exportToSVG(EditorTopComponent editor, File f) {
        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(GenericDOMImplementation.getDOMImplementation()
                .createDocument("http://www.w3.org/2000/svg", "svg", null));
        ctx.setEmbeddedFontsOn(true);
        SVGGraphics2D svgGenerator = new SVGGraphics2D(ctx, true);
        editor.paintScene(svgGenerator);
        try (FileOutputStream os = new FileOutputStream(f)) {
            Writer out = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            svgGenerator.stream(out, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
