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

package ro.uaic.info.nlptools.ggs.editor;

import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.XMLFormatConfig;
import ro.uaic.info.nlptools.ggs.engine.core.GGSException;
import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.ggs.engine.core.Match;
import ro.uaic.info.nlptools.ggs.annotationsExplorer.AnnotationsExplorer;
import ro.uaic.info.nlptools.ggs.engine.core.CompiledGrammar;
import org.w3c.dom.Document;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class RunWindow extends JDialog {
    private JPanel contentPane;
    private JLabel message;
    private JList matchesList;
    private JButton buttonClose;
    private JButton saveAnnotationsButton;
    private AnnotationsExplorer annotationsExplorer;
    private JFileChooser fileChooser;
    private InmemoryCorpus lastInpuText;
    private java.util.List<Match> lastMatches;
    private CompiledGrammar lastCompiledGrammar;

    public RunWindow() {
        fileChooser = new JFileChooser() {
            @Override
            public void approveSelection() {
                File f = getSelectedFile();
                if (f.exists() && getDialogType() == SAVE_DIALOG) {
                    int result = JOptionPane.showConfirmDialog(this, "The file exists, overwrite?", "Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
                    switch (result) {
                        case JOptionPane.YES_OPTION:
                            super.approveSelection();
                            return;
                        case JOptionPane.NO_OPTION:
                            return;
                        case JOptionPane.CLOSED_OPTION:
                            return;
                        case JOptionPane.CANCEL_OPTION:
                            cancelSelection();
                            return;
                    }
                }
                super.approveSelection();
            }
        };

        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return (f.isDirectory() || (f.isFile() && f.getName().endsWith(".xml")));
            }

            @Override
            public String getDescription() {
                return null;
            }
        });
        setContentPane(contentPane);
        setModal(false);
        setIconImage(Toolkit.getDefaultToolkit().getImage("logo.gif"));

        getRootPane().setDefaultButton(buttonClose);
        setTitle("RunWindow");

        ActionListener ok = e -> {
            if (buttonClose.getText().equals("Close")) setVisible(false);
            else if (buttonClose.getText().equals("Stop")) lastCompiledGrammar.RequestStop();
        };
        buttonClose.addActionListener(ok);
        contentPane.registerKeyboardAction(ok, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        pack();
        saveAnnotationsButton.addActionListener(ev -> {
        if (fileChooser.showSaveDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
            List<SpanAnnotation> annotations = new ArrayList<>();
            for (Match match : lastMatches){
                if (match.getSpanAnnotations() != null)
                    annotations.addAll(match.getSpanAnnotations());
            }

            InmemoryCorpus merged = lastInpuText.clone();
            merged.mergeSpanAnnotations(annotations);
            Document doc = merged.convertToDOM();
            OutputStream os = null;
            try {
                String file = fileChooser.getSelectedFile().getPath();
                if (!file.toLowerCase().endsWith(".xml"))
                    file += ".xml";
                os = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer serializer;
            try {
                serializer = tfactory.newTransformer();
                serializer.setOutputProperty(OutputKeys.INDENT, "yes");
                serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                serializer.transform(new DOMSource(doc), new StreamResult(os));
            } catch (TransformerException e) {
                e.printStackTrace();

                throw new RuntimeException(e);
            } finally {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        });
        annotationsExplorer.noDataMessage.setText("Parsing...");
    }

    protected void run() {
        if (!GrammarEditor.theEditor.runConfigWindow.checkConfig()) {
            JOptionPane.showMessageDialog(contentPane, "Cannot apply the grammar.\nThe run configuration is incomplete (press F4 to open the RunWindow Configuration window)");
            return;
        }
        GrammarEditor.theEditor.runConfigWindow.setVisible(false);
        setVisible(true);
        saveAnnotationsButton.setEnabled(false);
        matchesList.setModel(new DefaultListModel());
        matchesList.setCellRenderer(new ListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Match m = (Match) value;
                JEditorPane component = new JEditorPane();
                component.setContentType("text/html");
                StringBuilder sb = new StringBuilder();
                int s = m.getTokens().get(0).getTokenIndexInSentence() - 3;
                if (s < 0) s = 0;
                int f = m.getTokens().get(m.getTokens().size() - 1).getTokenIndexInSentence() + 7;
                if (f > m.getSentence().getTokenCount() - 1) f = m.getSentence().getTokenCount() - 1;
                sb.append("<html><span style='color:gray'>");
                for (int i = s; i < m.getTokens().get(0).getTokenIndexInSentence(); i++) {
                    sb.append(m.getSentence().getToken(i).toString()).append(" ");
                }
                sb.append("</span>");

                sb.append(m.toString());

                sb.append("<span style='color:gray'>");
                for (int i = m.getTokens().get(m.getTokens().size() - 1).getTokenIndexInSentence() + 1; i <= f; i++) {
                    sb.append(m.getSentence().getToken(i).toString()).append(" ");
                }
                sb.append("</span></html>");

                component.setText(sb.toString());
                return component;
            }
        });
        message.setForeground(Color.black);
        buttonClose.setText("Stop");

        message.setText("Compiling grammar...");
        try {
            lastCompiledGrammar = new CompiledGrammar(GrammarEditor.theEditor.grammar, GrammarEditor.theEditor.runConfigWindow.main);
        } catch (GGSException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            message.setForeground(Color.red);
            message.setText("Grammar compilation error caused by " + e.getMessage());
            buttonClose.setText("Close");
            return;
        }

        message.setText("Loading input file...");

        lastInpuText = null;
        try {
            XMLFormatConfig xmlConfig = new XMLFormatConfig();
            xmlConfig.sentenceNodeName = GrammarEditor.theEditor.runConfigWindow.sTagName.getText();
            lastInpuText = new InmemoryCorpus(new File(GrammarEditor.theEditor.runConfigWindow.inputFile.getText()), xmlConfig);
        } catch (Exception e) {
            message.setForeground(Color.red);
            message.setText("Error: " + e.getMessage());
            buttonClose.setText("Close");
            e.printStackTrace();
            return;
        }

        message.setText("Finding matches...");

        new BackgroundMatcher().execute();
    }

    public class BackgroundMatcher extends SwingWorker<java.util.List<Match>, Void> {
        long elapsedMilliseconds;
        boolean errored = false;

        @Override
        protected java.util.List<Match> doInBackground() {
            java.util.List<Match> matches = new ArrayList<Match>();
            long start = System.nanoTime();
            errored = false;
            try {
                matches = lastCompiledGrammar.GetMatches(lastInpuText);
            } catch (GGSException e) {
                JOptionPane.showMessageDialog(RunWindow.this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                message.setForeground(Color.red);
                message.setText("Error: " + e.getMessage());
                errored = true;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(RunWindow.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                message.setForeground(Color.red);
                message.setText("Error: " + ex.getMessage());
                ex.printStackTrace();
                errored = true;
            }
            elapsedMilliseconds = System.nanoTime() - start;
            return matches;
        }

        @Override
        protected void done() {
            try {
                lastMatches = get();
                DefaultListModel model = new DefaultListModel();
                for (Match m : lastMatches) {
                    model.addElement(m);
                }
                matchesList.setModel(model);
                if (!errored)
                    message.setText(String.format("Finished (in %s seconds)", elapsedMilliseconds / 1000000000f));
                saveAnnotationsButton.setEnabled(true);
                buttonClose.setText("Close");
                List<SpanAnnotation> annotations = new ArrayList<>();
                for (Match match : lastMatches){
                    if (match.getSpanAnnotations() != null)
                        annotations.addAll(match.getSpanAnnotations());
                }
                InmemoryCorpus merged = lastInpuText.clone();
                merged.mergeSpanAnnotations(annotations);
                annotationsExplorer.ShowInputText(merged);
                pack();
                revalidate();
                repaint();
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (ExecutionException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
