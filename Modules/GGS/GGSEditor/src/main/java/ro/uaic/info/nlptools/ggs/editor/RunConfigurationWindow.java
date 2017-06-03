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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Vector;

public class RunConfigurationWindow extends JDialog {
    private JPanel contentPane;
    private JButton buttonRun;
    private JButton browseButton;
    protected JTextField inputFile;
    protected JTextField sTagName;
    protected JComboBox mainGraph;
    private JLabel message;
    private JButton buttonClose;
    private JPanel contentPane2;
    JFileChooser jfc = new JFileChooser();
    protected String main = "Main";

    public RunConfigurationWindow() {
        super();
        setContentPane(contentPane);
        setModal(true);
        setTitle("RunWindow configuration");
        setIconImage(new ImageIcon(getClass().getResource("images/logo.gif")).getImage());
        jfc.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory() || f.getName().toLowerCase().endsWith(".xml")) return true;
                return false;
            }

            @Override
            public String getDescription() {
                return null;
            }
        });

        ActionListener ok = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                main = mainGraph.getSelectedItem().toString();
                setVisible(false);
            }
        };

        ActionListener run = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                main = mainGraph.getSelectedItem().toString();
                GrammarEditor.theEditor.runWindow.run();
            }
        };

        contentPane.registerKeyboardAction(ok, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        contentPane.registerKeyboardAction(run, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        contentPane.registerKeyboardAction(run, KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        this.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                checkConfig();
                Vector<String> vec = new Vector<String>();
                vec.add("Main");
                for (String g : GrammarEditor.theEditor.grammar.getGraphs().keySet()) {
                    if (!g.equals("Main")) {
                        vec.add(g);
                    }
                }
                mainGraph.setModel(new DefaultComboBoxModel(vec));
                for (int i = 0; i < mainGraph.getItemCount(); i++)
                    if (mainGraph.getItemAt(i).toString().equals(main))
                        mainGraph.setSelectedItem(mainGraph.getItemAt(i));
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
            }
        });

        buttonRun.addActionListener(run);
        buttonClose.addActionListener(ok);
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jfc.setCurrentDirectory(GrammarEditor.theEditor.grammarFile);
                int returnVal = jfc.showOpenDialog(contentPane);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    inputFile.setText(jfc.getSelectedFile().getPath());
                }
            }
        });

        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                checkConfig();
            }

            public void removeUpdate(DocumentEvent e) {
                checkConfig();
            }

            public void changedUpdate(DocumentEvent e) {
            }
        };

        inputFile.getDocument().addDocumentListener(docListener);
        sTagName.getDocument().addDocumentListener(docListener);

        pack();

        DocumentListener myDocListener = new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        GrammarEditor.theEditor.shouldSave = true;
                        GrammarEditor.theEditor.refreshTitle();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        GrammarEditor.theEditor.shouldSave = true;
                        GrammarEditor.theEditor.refreshTitle();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        GrammarEditor.theEditor.shouldSave = true;
                        GrammarEditor.theEditor.refreshTitle();
                    }
                };

        inputFile.getDocument().addDocumentListener(myDocListener);
        sTagName.getDocument().addDocumentListener(myDocListener);
        mainGraph.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                GrammarEditor.theEditor.shouldSave = true;
                GrammarEditor.theEditor.refreshTitle();
            }
        } );
    }

    protected boolean checkConfig() {
        if (new File(inputFile.getText()).exists()) {
            if (!sTagName.getText().isEmpty()) {
                buttonRun.setEnabled(true);
                message.setText("");
                return true;
            } else {
                buttonRun.setEnabled(false);
                message.setText("The sentence tag name must be specified!!!");
                return false;
            }
        } else {
            buttonRun.setEnabled(false);
            message.setText("Input file not found!!!");
            return false;
        }
    }

    protected void loadConfiguration(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line = br.readLine();
        if (line.startsWith("\uFEFF")) line = line.substring(1);

        Path p = Paths.get(GrammarEditor.theEditor.grammarFile.getAbsolutePath()).resolve(line).normalize();

        inputFile.setText(p.toString());
        line = br.readLine();
        sTagName.setText(line);
        main = br.readLine().trim();
        br.close();
        GrammarEditor.theEditor.shouldSave = false;
    }

    protected void saveConfiguration(OutputStream os) {
        PrintWriter pw = new PrintWriter(os);
        Path p;

        try{
            p = Paths.get(GrammarEditor.theEditor.grammarFile.getAbsolutePath()).relativize(Paths.get(inputFile.getText()));
        }catch (IllegalArgumentException ex){//probably different root issue - save as absolute path
            p = Paths.get(inputFile.getText());
        }

        pw.write(p.toString());
        pw.write("\n");
        pw.write(sTagName.getText());
        pw.write("\n");
        pw.write(mainGraph.getSelectedItem().toString());
        pw.close();
    }
}
