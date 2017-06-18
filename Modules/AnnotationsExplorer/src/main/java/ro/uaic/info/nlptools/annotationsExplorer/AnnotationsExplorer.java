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

import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.corpus.InmemorySentence;
import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.List;

public class AnnotationsExplorer {
    public JPanel contentPane;
    private JScrollPane sentencesList;
    public JLabel selectionLabel;
    public JPanel featuresList;
    public JLabel noDataMessage;
    public JComponent selectedComponent;
    public int selectedRow;
    InmemoryCorpus inputText;
    public JTable table;

    public AnnotationsExplorer() {
    }

    public void ShowInputText(final InmemoryCorpus inputText) {
        this.inputText = inputText;
        final Map<String, Float> sizeForAnnotationType = new HashMap<String, Float>();
        Map<String, List<SpanAnnotation>> namesToAnnotations = new TreeMap<String, List<SpanAnnotation>>();
        for (int k = 0; k < inputText.getSentenceCount(); k++) {
            INlpSentence s = inputText.getSentence(k);
            for (SpanAnnotation annotation : s.getSpanAnnotations()) {
                List<SpanAnnotation> annotations = namesToAnnotations.get(annotation.getName());
                if (annotations == null) {
                    annotations = new ArrayList<>();
                    namesToAnnotations.put(annotation.getName(), annotations);
                }
                annotations.add(annotation);
                Float f = sizeForAnnotationType.get(annotation.getName());
                if (f == null)
                    f = 0f;
                sizeForAnnotationType.put(annotation.getName(), f + annotation.getEndTokenIndex() - annotation.getStartTokenIndex() + 1);
            }
        }
        for (String annotationName : namesToAnnotations.keySet()) {
            sizeForAnnotationType.put(annotationName, sizeForAnnotationType.get(annotationName) / namesToAnnotations.get(annotationName).size());
        }

        List<String> sortedAnnotations = new ArrayList<String>(namesToAnnotations.keySet());

        Collections.sort(sortedAnnotations, new Comparator<String>() {
            @Override
            public int compare(String s, String s2) {
                if (sizeForAnnotationType.get(s) >= sizeForAnnotationType.get(s2)) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });

        List<Color> colors = BuildColorList(sortedAnnotations.size());

        final LinkedHashMap<String, Color> sortedAnnotationsWithColors = new LinkedHashMap<String, Color>();
        for (int i = 0; i < colors.size(); i++)
            sortedAnnotationsWithColors.put(sortedAnnotations.get(i), colors.get(i));
        final HashMap<InmemorySentence, SentenceRow> sentsToRows = new HashMap<InmemorySentence, SentenceRow>();

        table = new JTable(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return inputText.getSentenceCount();
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public Object getValueAt(int row, int col) {
                if (col == 0)
                    return row;
                else
                    return inputText.getSentence(row);
            }

            @Override
            public boolean isCellEditable(int i, int i2) {
                return i2 == 1;
            }

            @Override
            public Class getColumnClass(int c) {
                return getValueAt(0, c).getClass();
            }
        });

        table.setDefaultRenderer(InmemorySentence.class, new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable jTable, Object o, boolean b, boolean b2, int i, int i2) {
                SentenceRow ret = sentsToRows.get((InmemorySentence) o);
                if (ret == null) {
                    ret = new SentenceRow((InmemorySentence) o, sortedAnnotationsWithColors, AnnotationsExplorer.this);
                    sentsToRows.put((InmemorySentence) o, ret);
                }
                return ret;
            }
        });

        table.setDefaultEditor(InmemorySentence.class, new MyTableCellEditor(this, sortedAnnotationsWithColors, sentsToRows));

        table.setFont(new Font("Arial", Font.PLAIN, 14));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setTableHeader(null);
        table.setAutoscrolls(false);

        TableColumnAdjuster tca = new TableColumnAdjuster(table, 0);
        tca.adjustColumns();

        sentencesList.setViewportView(table);
    }

    private List<Color> BuildColorList(int size) {
        List<Color> ret = new ArrayList<Color>();
        if (size == 0)
            return ret;
        for (float i = 0; i < 1; i += 1f / size) {
            Color c = Color.getHSBColor(i, 0.4f, 1);
            ret.add(c);
        }

        return ret;
    }

    public static void main(String[] args) {
        final AnnotationsExplorer explorer = new AnnotationsExplorer();
        final JFrame frame = new JFrame("AnnotationsExplorer");
        frame.setIconImage(Toolkit.getDefaultToolkit().getImage("logo.gif"));

        frame.setContentPane(explorer.contentPane);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                frame.setVisible(false);
                frame.dispose();
                System.exit(0);
            }
        });


        explorer.contentPane.setTransferHandler(new TransferHandler() {
            public boolean canImport(JComponent arg0, DataFlavor[] arg1) {
                if (arg1.length != 1)
                    return false;
                DataFlavor flavor = arg1[0];
                if (flavor.equals(DataFlavor.javaFileListFlavor)) {
                    return true;
                }
                return false;
            }

            public boolean importData(JComponent comp, Transferable t) {
                File f = null;
                try {
                    f = ((List<File>) t.getTransferData(DataFlavor.javaFileListFlavor)).get(0);
                    final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new FileInputStream(f));
                    String guessS = "S";
                    try {
                        int i = 0;
                        Node n;
                        while ((n = doc.getDocumentElement().getChildNodes().item(i)).getNodeType() != Node.ELEMENT_NODE)
                            i++;
                        guessS = n.getNodeName();
                    } catch (Throwable th) {
                    }

                    final String sTag = (String) JOptionPane.showInputDialog(null, "InmemorySentence tag name", "The xml tag name used for sentences", JOptionPane.QUESTION_MESSAGE, null, null, guessS);
                    explorer.ShowInputText(new InmemoryCorpus(doc));
                    return true;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "Can't drag file", JOptionPane.ERROR_MESSAGE);
                }

                return false;
            }
        });

        frame.pack();
        frame.setVisible(true);
    }
}