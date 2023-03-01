/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.filter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.*;
import org.openide.windows.WindowManager;

/**
 *
 * @author Thomas Wuerthinger
 */
public class EditFilterDialog extends javax.swing.JDialog {

    private final CustomFilter customFilter;
    private boolean accepted;

    /** Creates new form EditFilterDialog */
    public EditFilterDialog(CustomFilter customFilter) {
        super(WindowManager.getDefault().getMainWindow(), true);
        this.customFilter = customFilter;
        initComponents();

        sourceTextArea.setFont(new Font("Courier", Font.PLAIN, 12));
        AbstractDocument doc = (AbstractDocument) sourceTextArea.getDocument();
        doc.setDocumentFilter(new DocumentFilter(){
            private final StyledDocument styledDocument = sourceTextArea.getStyledDocument();
            private final StyleContext styleContext = StyleContext.getDefaultStyleContext();
            private final AttributeSet blueAttributeSet = styleContext.addAttribute(styleContext.getEmptySet(), StyleConstants.Foreground, new Color(8,8,255));
            private final AttributeSet greenAttributeSet = styleContext.addAttribute(styleContext.getEmptySet(), StyleConstants.Foreground, new Color(80,160,80));
            private final AttributeSet greyAttributeSet = styleContext.addAttribute(styleContext.getEmptySet(), StyleConstants.Foreground, Color.GRAY);
            private final AttributeSet blackAttributeSet = styleContext.addAttribute(styleContext.getEmptySet(), StyleConstants.Foreground, Color.BLACK);
            private final Pattern comments_pattern = Pattern.compile("(?://.*)|(/\\*(?:.|[\\n\\r])*?\\*/)");
            private final Pattern quote_pattern = Pattern.compile("([\"'])((?:\\\\\\1|(?:(?!\\1)).)*)(\\1)");
            private final Pattern keywords_pattern = buildKeywordsPattern();
            private final String tabSpaces = "  ";


            private Pattern buildKeywordsPattern() {
                StringBuilder pattern = new StringBuilder();
                String[] keywords = new String[]{"await","break","case","catch","class","const","continue","debugger",
                        "default","delete","do","else","enum","export","extends","false","finally","for","function",
                        "if","implements","import","in","instanceof","interface","let","new","null","package","private",
                        "protected","public","return","super","switch","static","this","throw","try","true","typeof",
                        "var","void","while","with","yield"
                };
                for (String keyword : keywords) {
                    pattern.append("\\b").append(keyword).append("\\b|");
                }
                if (pattern.length()>0) {
                    pattern.deleteCharAt(pattern.length()-1);
                }
                return Pattern.compile(pattern.toString());
            }

            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs) throws BadLocationException {
                text = text.replace("\t", tabSpaces);
                super.insertString(fb, offset, text, attrs);
                SwingUtilities.invokeLater(this::updateSyntaxHighlighting);
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                super.remove(fb, offset, length);
                SwingUtilities.invokeLater(this::updateSyntaxHighlighting);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                text = text.replace("\t", tabSpaces);
                super.replace(fb, offset, length, text, attrs);
                SwingUtilities.invokeLater(this::updateSyntaxHighlighting);
            }

            private void updateSyntaxHighlighting() {
                styledDocument.setCharacterAttributes(0, sourceTextArea.getText().length(), blackAttributeSet, true);

                Matcher keyword_matcher = keywords_pattern.matcher(sourceTextArea.getText());
                while (keyword_matcher.find()) {
                    styledDocument.setCharacterAttributes(keyword_matcher.start(), keyword_matcher.end() - keyword_matcher.start(), blueAttributeSet, false);
                }

                Matcher quote_matcher = quote_pattern.matcher(sourceTextArea.getText());
                while (quote_matcher.find()) {
                    styledDocument.setCharacterAttributes(quote_matcher.start(), quote_matcher.end() - quote_matcher.start(), greenAttributeSet, false);
                }

                Matcher comments_matcher = comments_pattern.matcher(sourceTextArea.getText());
                while (comments_matcher.find()) {
                    styledDocument.setCharacterAttributes(comments_matcher.start(), comments_matcher.end() - comments_matcher.start(), greyAttributeSet, false);
                }
            }
        });

        sourceTextArea.setText(customFilter.getCode());
        nameTextField.setText(customFilter.getName());
    }

    public boolean wasAccepted() {
        return accepted;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        jScrollPane1 = new javax.swing.JScrollPane();
        sourceTextArea = new javax.swing.JTextPane();
        nameTextField = new javax.swing.JTextField();
        nameLabel = new javax.swing.JLabel();
        sourceLabel = new javax.swing.JLabel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "title")); // NOI18N
        setResizable(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(sourceTextArea);
        jScrollPane1.setViewportView(panel);

        nameTextField.setText("null");

        nameLabel.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jLabel1.text")); // NOI18N

        sourceLabel.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jLabel2.text")); // NOI18N

        okButton.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jButton1.text")); // NOI18N
        okButton.addActionListener(evt -> {
            cancelButtonClicked(evt);
            okButtonClicked(evt);
        });

        cancelButton.setText(org.openide.util.NbBundle.getMessage(EditFilterDialog.class, "jButton2.text")); // NOI18N
        cancelButton.addActionListener(this::cancelButtonClicked);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(sourceLabel)
                            .add(nameLabel))
                        .add(25, 25, 25)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 695, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE)
                            .add(nameTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 695, Short.MAX_VALUE)))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(okButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 76, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(nameLabel)
                    .add(nameTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(sourceLabel)
                    .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 337, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE))
                    .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(cancelButton)
                    .add(okButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

private void okButtonClicked(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonClicked
    this.customFilter.setName(this.nameTextField.getText());
    this.customFilter.setCode(this.sourceTextArea.getText());
    accepted = true;
    setVisible(false);
}//GEN-LAST:event_okButtonClicked

private void cancelButtonClicked(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonClicked
    setVisible(false);
}//GEN-LAST:event_cancelButtonClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JTextField nameTextField;
    private javax.swing.JButton okButton;
    private javax.swing.JLabel sourceLabel;
    private javax.swing.JTextPane sourceTextArea;
    // End of variables declaration//GEN-END:variables

}
