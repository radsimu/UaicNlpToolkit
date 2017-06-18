/**************************************************************************
 * Copyright © 2017 Radu Simionescu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************************************************************/

package ro.uaic.info.nlptools.annotationsExplorer;

import ro.uaic.info.nlptools.corpus.InmemorySentence;
import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.Token;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.*;
import java.util.List;

public class SentenceRow extends JPanel {
    private InmemorySentence sentence;
    private AnnotationsExplorer main;

    public SentenceRow(InmemorySentence sentence, LinkedHashMap<String, Color> sortedAnnotations, final AnnotationsExplorer main) {
        super();
        this.main = main;
        setBackground(Color.white);
        setLayout(new GridBagLayout());
        this.sentence = sentence;
        GridBagConstraints c = new GridBagConstraints();
        setRequestFocusEnabled(true);
        c.insets = new Insets(0, 5, 0, 5);
        c.fill = GridBagConstraints.BOTH;
        for (int k = 0; k < sentence.getTokenCount(); k++) {
            final Token token = sentence.getToken(k);
            c.gridx = token.getTokenIndexInSentence();
            final JLabel tokenLabel = new JLabel(token.toString());
            Map<TextAttribute, Object> fontAttributes = (Map<TextAttribute, Object>) new Font("Arial", Font.BOLD, 16).getAttributes();
            fontAttributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_ONE_PIXEL);
            tokenLabel.setFont(new Font(fontAttributes));
            tokenLabel.setBorder(new EmptyBorder(2, 2, 2, 2));
            add(tokenLabel, c);
            tokenLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent mouseEvent) {
                    super.mousePressed(mouseEvent);
                    selectComponent(tokenLabel, token);
                }
            });
        }
        c.gridx++;
        c.weightx = 1;
        add(new JLabel(), c);

        int currentY = 0;
        List<SpanAnnotation> sortedSentenceAnnotations = new ArrayList<SpanAnnotation>(sentence.getSpanAnnotations());
        Collections.sort(sortedSentenceAnnotations, new Comparator<SpanAnnotation>() {
            @Override
            public int compare(SpanAnnotation spanAnnotation, SpanAnnotation spanAnnotation2) {
                return (int) Math.signum(spanAnnotation.getEndTokenIndex() - spanAnnotation.getStartTokenIndex() - spanAnnotation2.getEndTokenIndex() + spanAnnotation2.getStartTokenIndex());
            }
        });

        for (Map.Entry<String, Color> annotationWitColor : sortedAnnotations.entrySet()) {
            byte[] annotationLevelsForTokenAtIndex = new byte[sentence.getTokenCount()];
            int rowsForThisAnnotationType = 0;
            for (final SpanAnnotation annotation : sortedSentenceAnnotations)
                if (annotation.getName().equals(annotationWitColor.getKey())) {
                    byte rowForThisAnnotation = 0;
                    for (int i = annotation.getStartTokenIndex(); i <= annotation.getEndTokenIndex(); i++) {
                        annotationLevelsForTokenAtIndex[i]++;
                        if (annotationLevelsForTokenAtIndex[i] > rowForThisAnnotation)
                            rowForThisAnnotation = annotationLevelsForTokenAtIndex[i];
                        if (annotationLevelsForTokenAtIndex[i] > rowsForThisAnnotationType)
                            rowsForThisAnnotationType = annotationLevelsForTokenAtIndex[i];
                    }
                    for (int i = annotation.getStartTokenIndex(); i <= annotation.getEndTokenIndex(); i++)
                        annotationLevelsForTokenAtIndex[i] = rowForThisAnnotation;
                    c = new GridBagConstraints();
                    c.fill = GridBagConstraints.BOTH;
                    c.anchor = GridBagConstraints.CENTER;
                    c.insets = new Insets(0, 5, 3, 5);
                    c.gridx = annotation.getStartTokenIndex();
                    c.gridy = currentY + rowForThisAnnotation + 1;
                    c.gridwidth = annotation.getEndTokenIndex() - annotation.getStartTokenIndex() + 1;
                    final JPanel annotationPanel = new JPanel(new GridLayout());
                    annotationPanel.setPreferredSize(new Dimension(10, 16));
                    annotationPanel.setBackground(annotationWitColor.getValue());
                    add(annotationPanel, c);
                    final JLabel annotationLabel = new JLabel("<html><body style=\"white-space:nowrap\">" + annotation.getName() + "</body></html>");
                    annotationLabel.setFont(new Font("Arial", Font.BOLD, 10));
                    annotationLabel.setForeground(Color.black);
                    annotationPanel.add(annotationLabel);
                    annotationPanel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent mouseEvent) {
                            super.mousePressed(mouseEvent);
                            selectComponent(annotationPanel, annotation);
                        }
                    });
                }
            currentY += rowsForThisAnnotationType;
        }
        //sentenceViewer.setPreferredSize(new Dimension(width, this.getPreferredSize().height));
    }

    private void selectComponent(JComponent annotationPanel, Object obj) {
        if (main.selectedComponent != null) {
            main.selectedComponent.setBorder(new EmptyBorder(2, 2, 2, 2));
            main.table.repaint(main.table.getCellRect(main.selectedRow, 1, false));
        }

        Map<String, String> features = null;
        if (obj instanceof Token) {
            Token token = (Token) obj;
            main.selectedRow = token.getParentSentence().getSentenceIndexInCorpus();
            main.selectionLabel.setText(token.toString() + " (Token " + token.getTokenIndexInSentence() + ")");
            annotationPanel.setBorder(new LineBorder(Color.black, 2));
            features = token.getFeatures();
        } else if (obj instanceof SpanAnnotation) {
            SpanAnnotation annotation = (SpanAnnotation) obj;
            main.selectedRow = annotation.getSentence().getSentenceIndexInCorpus();
            main.selectionLabel.setText(annotation.getName() + " (SpanAnnotation)");
            annotationPanel.setBorder(new CompoundBorder(new LineBorder(Color.red, 2), new LineBorder(Color.white, 1)));
            features = annotation.getFeatures();
        }
        main.selectedComponent = annotationPanel;

        main.featuresList.removeAll();
        int i = 0;
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(0, 5, 0, 3);
        for (Map.Entry<String, String> entry : features.entrySet()) {
            c.gridx = 0;
            c.gridy = i;
            JLabel keyLabel = new JLabel(entry.getKey() + ":");
            main.featuresList.add(keyLabel, c);
            c.gridx = 1;
            main.featuresList.add(new JLabel(entry.getValue()), c);
            i++;
        }
        c.gridy = i - 1;
        c.gridx = 2;
        c.weightx = 1;
        main.featuresList.add(new JPanel(), c);

        c.gridx = 0;
        c.gridy = i;
        c.weighty = 1;
        main.featuresList.add(new JPanel(), c);
        main.featuresList.repaint();
    }
}