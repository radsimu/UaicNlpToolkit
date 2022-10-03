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

import ro.uaic.info.nlptools.ggs.engine.core.Pair;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import ro.uaic.info.nlptools.ggs.engine.grammar.Graph;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;
import jsyntaxpane.DefaultSyntaxKit;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.tree.*;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.UndoableEditSupport;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import static javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION;

public class GrammarEditor {
    protected static GrammarEditor theEditor;

    private JPanel panel1;
    public JTree graphTree;
    private JButton insertButton;
    public GrammarEditorComponent graphEditor;
    private JButton deleteButton;
    private JButton renameButton;
    private JSplitPane editorSplitPane;
    private JEditorPane grammarJsEditor;
    private JTable macrosTable;
    private JScrollPane editorScrollPane;
    public JSplitPane splitPane1;
    private JSplitPane splitPane2;
    public JButton grammarJSEditorButtonToggle;
    public JButton macrosButtonToggle;
    private JButton injectMacrosButton;
    private JButton importMacrosButton;
    private JFileChooser fileDialog;
    protected RunConfigurationWindow runConfigWindow;
    protected RunWindow runWindow;

    public Grammar grammar;
    private UndoManager undoManager;
    public UndoableEditSupport undoSupport;

    private JMenuItem undo;
    private JMenuItem redo;
    protected JMenuItem run;
    public JFrame frame;

    public File grammarFile;
    public boolean shouldSave = true;
    public boolean mustRefreshGraphsTree;

    public static void main(String[] args) {
        File f = null;
        if (args.length > 0) {
            f = new File(args[0]);
        }
        GrammarEditor grammarEditor = new GrammarEditor(f);
        grammarEditor.show();
    }

    public GrammarEditor() {
        this(null);
    }

    public GrammarEditor(final File file) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "WikiTeX");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        DefaultSyntaxKit.initKit();
        theEditor = this;
        runConfigWindow = new RunConfigurationWindow();
        runWindow = new RunWindow();
        frame = new JFrame("graphEditor");
        fileDialog = new JFileChooser() {
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
        fileDialog.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory() || f.getName().toLowerCase().endsWith(".ggf")) return true;
                return false;
            }

            @Override
            public String getDescription() {
                return null;
            }
        });
        //fra1me.setPreferredSize(new Dimension(1300, 786));
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {

                if (shouldSave) {
                    int answer = JOptionPane.showConfirmDialog(frame, "Save before exit?", null, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (answer == JOptionPane.YES_OPTION)
                        save.doClick();
                    else if (answer == JOptionPane.CANCEL_OPTION)
                        return;
                }
                frame.setVisible(false);
                frame.dispose();
                System.exit(0);
            }
        });

        frame.setJMenuBar(new JMenuBar());
        frame.setIconImage(new ImageIcon(getClass().getResource("images/logo.gif")).getImage());
        graphTree.getSelectionModel().setSelectionMode(SINGLE_TREE_SELECTION);
        graphTree.setCellRenderer(new MyTreeCellRenderer());
        graphTree.setCellEditor(new MyTreeCellEditor(graphTree, (DefaultTreeCellRenderer) graphTree.getCellRenderer()));
        TreeNode root = new DefaultMutableTreeNode();
        graphTree.setModel(new MyTreeModel(root, this));

        undoManager = new UndoManager();
        undoSupport = new UndoableEditSupport();
        undoSupport.addUndoableEditListener(new UndoableEditListener() {
            public void undoableEditHappened(UndoableEditEvent e) {
                UndoableEdit edit = e.getEdit();
                undoManager.addEdit(edit);
                refreshUndoRedo();
            }
        });
        graphEditor.undoSupport = undoSupport;

        panel1.setTransferHandler(new MyDragHandler());

        constructMenu();

        //set up the split panes
        JPanel layers = null;
        Component c0 = null;
        Component c1 = null;

        layers = (JPanel) editorScrollPane.getParent();
        layers.setLayout(new OverlayLayout(layers));
        c0 = layers.getComponent(1);
        c1 = layers.getComponent(0);
        layers.removeAll();
        layers.add(c0, 0);
        layers.add(c1, 1);

        layers = (JPanel) splitPane2.getParent();
        layers.setLayout(new OverlayLayout(layers));
        c0 = layers.getComponent(1);
        c1 = layers.getComponent(0);
        layers.removeAll();
        layers.add(c0, 0);
        layers.add(c1, 1);

        frame.pack();

        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) splitPane1.getUI()).getDivider();
        divider.getComponent(0).setVisible(false);
        divider.getComponent(1).setVisible(false);

        divider = ((BasicSplitPaneUI) splitPane2.getUI()).getDivider();
        divider.getComponent(0).setVisible(false);
        divider.getComponent(1).setVisible(false);
        final Dimension min1 = splitPane1.getRightComponent().getMinimumSize();
        final Dimension min2 = splitPane2.getBottomComponent().getMinimumSize();

        graphEditor.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                super.componentResized(e);
                GrammarEditor.theEditor.splitPane1.repaint();
            }
        });

        macrosButtonToggle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (splitPane1.getRightComponent().getWidth() > 0) {
                    splitPane1.getRightComponent().setMinimumSize(new Dimension());
                    splitPane1.setDividerLocation(1.0);
                } else {
                    splitPane1.setDividerLocation(splitPane1.getLastDividerLocation());
                    splitPane1.getRightComponent().setMinimumSize(min1);
                }
                splitPane1.repaint();
            }
        });

        grammarJSEditorButtonToggle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (splitPane2.getBottomComponent().getHeight() > 0) {
                    splitPane2.getBottomComponent().setMinimumSize(new Dimension());
                    splitPane2.setDividerLocation(1.0);
                } else {
                    splitPane2.setDividerLocation(splitPane2.getLastDividerLocation());
                    splitPane2.getBottomComponent().setMinimumSize(min2);
                }
                splitPane1.repaint();
            }
        });

        macrosButtonToggle.doClick();
        grammarJSEditorButtonToggle.doClick();

        injectMacrosButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                for (int i = 0; i < grammar.macros.size(); i++)
                    for (Graph graph : grammar.getGraphs().values())
                        for (GraphNode gn : graph.getGraphNodes().values()) {
                            gn.setTokenMatchingCode(gn.getTokenMatchingCode().replace(grammar.macros.get(i).getValue(), "@" + grammar.macros.get(i).getKey() + "@"));
                            graphEditor.renderingInfos.get(gn).formatDirty = true;
                            graphEditor.updateUI();
                        }
            }
        });

        grammarJsEditor.setContentType("text/javascript");
        grammarJsEditor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                try {
                    grammar.setJsCode(e.getDocument().getText(0, e.getDocument().getLength()));
                } catch (BadLocationException e1) {
                    e1.printStackTrace();
                }
            }

            public void removeUpdate(DocumentEvent e) {
                insertUpdate(e);
            }

            public void changedUpdate(DocumentEvent e) {
                insertUpdate(e);
            }
        });

        frame.addWindowListener(new WindowListener() {
            public void windowOpened(WindowEvent e) {
                if (file != null) {
                    try {
                        openGrammar(file);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, ex.getMessage(), "Can not open file", JOptionPane.ERROR_MESSAGE);
                    }
                } else newGrammar();
            }

            public void windowClosing(WindowEvent e) {
            }

            public void windowClosed(WindowEvent e) {
            }

            public void windowIconified(WindowEvent e) {
            }

            public void windowDeiconified(WindowEvent e) {
            }

            public void windowActivated(WindowEvent e) {
            }

            public void windowDeactivated(WindowEvent e) {
            }
        });

        graphTree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                if (e.getPath() != null && e.getPath().getLastPathComponent() != null) { //if there is anything selected
                    if (((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject() != null && ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject() instanceof MyTreeNodeObject && ((MyTreeNodeObject) ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject()).graph != null)
                        graphEditor.setCurrentGraph(((MyTreeNodeObject) ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject()).graph);
                    graphTree.expandPath(e.getPath());
                }
            }
        });

        graphTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteButton.doClick();
                }
            }
        });

        insertButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean ok = false;
                String initVal = "new graph";

                while (!ok) {
                    String val = JOptionPane.showInputDialog("Type the name of the new graph", initVal);
                    if (val == null) return;
                    val = val.trim();
                    ok = true;
                    if (val.isEmpty()) {
                        ok = false;
                        JOptionPane.showMessageDialog(frame, "The graph name cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
                    } else if (grammar.getGraphs().containsKey(val)) {
                        ok = false;
                        initVal = val;
                        JOptionPane.showMessageDialog(frame, "A graph with this name exists already", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    if (ok) {
                        CreateGraphCommand createGraphCommand = new CreateGraphCommand();
                        createGraphCommand.grammar = grammar;
                        createGraphCommand.graph = new Graph(grammar,val);
                        GrammarEditor.this.initNewGraph(createGraphCommand.graph);
                        grammar.getGraphs().put(createGraphCommand.graph.getId(), createGraphCommand.graph);

                        undoSupport.postEdit(createGraphCommand);
                        refreshTree();
                    }
                }
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                DeleteGraphCommand deleteGraphCommand = new DeleteGraphCommand();
                deleteGraphCommand.grammar = grammar;
                deleteGraphCommand.graph = ((MyTreeNodeObject) ((DefaultMutableTreeNode) graphTree.getSelectionPath().getLastPathComponent()).getUserObject()).graph;
                if (deleteGraphCommand.graph != null) { // if node points to existing graph (not red)
                    if (deleteGraphCommand.graph.getId().equals("Main")) {
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                    GrammarEditor.theEditor.graphTree.setSelectionPath(GrammarEditor.theEditor.graphTree.getSelectionPath().getParentPath());
                    grammar.getGraphs().remove(deleteGraphCommand.graph.getId());
                    undoSupport.postEdit(deleteGraphCommand);
                    refreshTree();
                }
            }
        });

        renameButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (graphTree.getSelectionPath() != null && graphTree.getSelectionPath().getLastPathComponent() != null && graphTree.getSelectionPath().getPathCount() > 1) {
                    graphTree.startEditingAtPath(graphTree.getSelectionPath());
                }
            }
        });
    }

    public void show() {
        frame.pack();
        //frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    JMenuItem save;

    private void constructMenu() {
        JMenuBar bar = frame.getJMenuBar();
        JMenu menuFile = new JMenu("File");
        JMenu menuEdit = new JMenu("Edit");
        JMenu menuRun = new JMenu("RunWindow");
        bar.add(menuFile);
        bar.add(menuEdit);
        bar.add(menuRun);

        //File
        JMenuItem newFile = new JMenuItem("New");
        newFile.setAccelerator(KeyStroke.getKeyStroke('N', InputEvent.CTRL_DOWN_MASK));
        newFile.setMnemonic('N');
        menuFile.add(newFile);

        JMenuItem open = new JMenuItem("Open");
        open.setAccelerator(KeyStroke.getKeyStroke('O', InputEvent.CTRL_DOWN_MASK));
        open.setMnemonic('O');
        menuFile.add(open);

        save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke('S', InputEvent.CTRL_DOWN_MASK));
        save.setMnemonic('S');
        menuFile.add(save);

        final JMenuItem saveAs = new JMenuItem("Save As");
        saveAs.setAccelerator(KeyStroke.getKeyStroke('S', InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAs.setMnemonic('A');
        menuFile.add(saveAs);

        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
        quit.setMnemonic('Q');
        menuFile.add(quit);

        //Edit
        undo = new JMenuItem("undo");
        undo.setAccelerator(KeyStroke.getKeyStroke('Z', InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(undo);

        redo = new JMenuItem("redo");
        redo.setAccelerator(KeyStroke.getKeyStroke('Y', InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(redo);

        //-------
        menuEdit.addSeparator();
        JMenuItem copy = new JMenuItem("copy");
        copy.setAccelerator(KeyStroke.getKeyStroke('C', InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(copy);

        JMenuItem cut = new JMenuItem("cut");
        cut.setAccelerator(KeyStroke.getKeyStroke('X', InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(cut);

        JMenuItem paste = new JMenuItem("paste");
        paste.setAccelerator(KeyStroke.getKeyStroke('V', InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(paste);

        //-------
        menuEdit.addSeparator();
        JMenuItem selectAll = new JMenuItem("select all");
        selectAll.setAccelerator(KeyStroke.getKeyStroke('A', InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(selectAll);

        JMenuItem deselect = new JMenuItem("deselect");
        deselect.setAccelerator(KeyStroke.getKeyStroke('D', InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(deselect);

        //-------
        menuEdit.addSeparator();
        JMenuItem insert = new JMenuItem("insert node");
        insert.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK));
        menuEdit.add(insert);

        JMenuItem delete = new JMenuItem("delete selected");
        delete.setAccelerator(KeyStroke.getKeyStroke("DELETE"));
        menuEdit.add(delete);

        //RunWindow
        JMenuItem runConfig = new JMenuItem("run configuration");
        runConfig.setAccelerator(KeyStroke.getKeyStroke("F4"));
        menuRun.add(runConfig);

        run = new JMenuItem("run");
        run.setAccelerator(KeyStroke.getKeyStroke("F9"));
        menuRun.add(run);


        //
        //
        //handlers

        runConfig.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                runConfigWindow.setVisible(true);
            }
        });

        run.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                runWindow.run();
            }
        });

        delete.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                graphEditor.delete();
            }
        });

        insert.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                graphEditor.insertNode(graphEditor.getWidth() / 2, graphEditor.getHeight() / 2);
            }
        });


        deselect.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                graphEditor.deselect();
            }
        });

        copy.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                graphEditor.copy();
            }
        });
        cut.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                graphEditor.cut();
            }
        });
        paste.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                graphEditor.paste();
            }
        });

        newFile.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (shouldSave) {
                    int answer = JOptionPane.showConfirmDialog(frame, "Save before starting a new grammar?", null, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (answer == JOptionPane.YES_OPTION)
                        save.doClick();
                    else if (answer == JOptionPane.CANCEL_OPTION)
                        return;

                }

                newGrammar();
            }
        });

        open.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (shouldSave) {
                    int answer = JOptionPane.showConfirmDialog(frame, "Save before opening a new grammar?", null, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (answer == JOptionPane.YES_OPTION)
                        save.doClick();
                    else if (answer == JOptionPane.CANCEL_OPTION)
                        return;
                }

                int ret = fileDialog.showOpenDialog(frame);
                if (ret == JFileChooser.APPROVE_OPTION) {
                    openGrammar(fileDialog.getSelectedFile());
                }
            }
        });

        save.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (grammarFile == null) {
                    saveAs.doClick();
                    return;
                }

                try {
                    grammar.save(new FileOutputStream(grammarFile));
                    if (runConfigWindow.checkConfig()) {
                        runConfigWindow.saveConfiguration(new FileOutputStream(grammarFile.getPath().replaceAll("....$", ".ggc")));
                    }
                    shouldSave = false;
                    refreshTitle();
                } catch (ParserConfigurationException e1) {
                    e1.printStackTrace();
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                }
            }
        });

        saveAs.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                int ret = fileDialog.showSaveDialog(frame);
                if (ret == JFileChooser.APPROVE_OPTION) {
                    try {
                        String filePath = fileDialog.getSelectedFile().getPath().toLowerCase();
                        if (!filePath.endsWith(".ggf")) {
                            filePath = filePath + ".ggf";
                        }

                        grammar.save(new FileOutputStream(filePath));
                        grammarFile = new File(filePath);
                        if (runConfigWindow.checkConfig()) {
                            runConfigWindow.saveConfiguration(new FileOutputStream(grammarFile.getPath().replaceAll("....$", ".ggc")));
                        }
                        shouldSave = false;
                        refreshTitle();
                    } catch (ParserConfigurationException e1) {
                        e1.printStackTrace();
                    } catch (FileNotFoundException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        undo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                undoManager.undo();
                refreshUndoRedo();
                graphEditor.updateUI();
            }
        });

        redo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                undoManager.redo();
                refreshUndoRedo();
                graphEditor.updateUI();
            }
        });

        selectAll.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                graphEditor.selectAll();
            }
        });
    }

    public void refreshUndoRedo() {

        // refresh undo
        undo.setText(undoManager.getUndoPresentationName());
        undo.setEnabled(undoManager.canUndo());

        // refresh redo
        redo.setText(undoManager.getRedoPresentationName());
        redo.setEnabled(undoManager.canRedo());
        shouldSave = true;
        refreshTitle();
    }

    public void initNewGraph(Graph graph) {
        GraphNode startNode = new GraphNode(grammar);
        graph.addGraphNode(startNode, -1);
        graph.setStartNode(startNode);
        startNode.setX(100);
        startNode.setY(100);
        graphEditor.renderingInfos.put(startNode, new GraphNodeRenderingInfo(startNode));

        GraphNode endNode = new GraphNode(grammar);
        graph.addGraphNode(endNode, -1);
        graph.setEndNode(endNode);
        endNode.setX(700);
        endNode.setY(100);
        graphEditor.renderingInfos.put(endNode, new GraphNodeRenderingInfo(endNode));
    }

    public void setUpMacrosTable() {
        final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Macro", "For string", ""}, 0);
        macrosTable.setModel(tableModel);
        macrosTable.getColumnModel().getColumn(2).setResizable(false);
        macrosTable.getColumnModel().getColumn(2).setPreferredWidth(20);
        macrosTable.getColumnModel().getColumn(2).setMaxWidth(20);
        macrosTable.getColumnModel().getColumn(2).setMinWidth(20);
        for (int i = 0; i < grammar.macros.size(); i++) {
            tableModel.addRow(new Object[]{grammar.macros.get(i).getKey(), grammar.macros.get(i).getValue(), "-"});
        }
        tableModel.addRow(new Object[]{"", "", "+"});

        ButtonColumn b = new ButtonColumn(macrosTable, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JTable table = (JTable) e.getSource();
                int modelRow = Integer.valueOf(e.getActionCommand());
                if (modelRow < grammar.macros.size()) {
                    grammar.removeMacro(modelRow);
                    ((DefaultTableModel) table.getModel()).removeRow(modelRow);
                    for (Graph graph : grammar.getGraphs().values())
                        for (GraphNode gn : graph.getGraphNodes().values())
                            graphEditor.renderingInfos.get(gn).formatDirty = true;
                    graphEditor.updateUI();

                } else {
                    table.getModel().setValueAt("-", modelRow, 2);
                    grammar.macros.add(null);
                    ((DefaultTableModel) table.getModel()).addRow(new Object[]{"", "", "+"});
                }
            }
        }, 2);

        tableModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent tableModelEvent) {
                if (tableModelEvent.getColumn() == 2 || tableModelEvent.getColumn() == -1)
                    return;
                int row = tableModelEvent.getLastRow();
                if (row == tableModel.getRowCount() - 1) {
                    grammar.macros.add(null);
                    tableModel.setValueAt("-", row, 2);
                    ((DefaultTableModel) tableModel).addRow(new Object[]{"", "", "+"});
                }

                if (tableModel.getValueAt(row, 0).toString().trim().isEmpty() || tableModel.getValueAt(row, 1).toString().trim().isEmpty())
                    grammar.macros.set(row, null);
                else
                    grammar.macros.set(row, new Pair<String, String>(tableModel.getValueAt(row, 0).toString(), tableModel.getValueAt(row, 1).toString()));
            }
        });
    }

    protected void openGrammar(File file) {
        shouldSave = false;
        grammarFile = file;
        grammar = new Grammar();
        try {
            grammar.load(new FileInputStream(grammarFile));

            graphEditor.renderingInfos = new HashMap<GraphNode, GraphNodeRenderingInfo>();
            for (Graph graph : grammar.getGraphs().values()) {
                for (GraphNode graphNode : graph.getGraphNodes().values()) {
                    graphEditor.renderingInfos.put(graphNode, new GraphNodeRenderingInfo(graphNode));
                }
            }
            refreshTree();
            graphTree.setSelectionPath(new TreePath(new Object[]{graphTree.getModel().getRoot(), ((DefaultMutableTreeNode) graphTree.getModel().getRoot()).getChildAt(0)}));
            File f = new File(file.getPath().replaceAll("....$", ".ggc"));
            if (f.exists()) {
                runConfigWindow.loadConfiguration(new FileInputStream(f));
            }
            refreshTitle();

            runConfigWindow.checkConfig();
            selectGraph(grammar.getGraphs().get("Main"));
            grammarJsEditor.setText(grammar.getJsCode());
            setUpMacrosTable();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Can't open file", JOptionPane.ERROR_MESSAGE);
        }
    }

    protected void newGrammar() {
        shouldSave = false;
        graphTree.setSelectionPath(new TreePath(graphTree.getModel().getRoot()));
        grammar = new Grammar();
        grammarFile = null;
        graphEditor.renderingInfos = new HashMap<GraphNode, GraphNodeRenderingInfo>();
        Graph mainGraph = new Graph(grammar, "Main");
        initNewGraph(mainGraph);
        grammar.getGraphs().put(mainGraph.getId(), mainGraph);
        refreshTree();
        graphTree.setSelectionPath(new TreePath(new Object[]{graphTree.getModel().getRoot(), ((DefaultMutableTreeNode) graphTree.getModel().getRoot()).getChildAt(0)}));
        refreshTitle();
        runConfigWindow.checkConfig();
        selectGraph(mainGraph);
        grammarJsEditor.setText("");
        setUpMacrosTable();
    }

    public void refreshTitle() {
        String t = "Graphical Grammar Studio";
        if (grammarFile != null) t += " - " + grammarFile.getName();
        if (shouldSave) t += "*";
        frame.setTitle(t);
    }

    private java.util.List<TreePath> saveGraphTreeExpandedState() {
        java.util.List<TreePath> rowsPaths = new ArrayList<TreePath>();
        for (int i = 0; i < graphTree.getRowCount(); i++) {
            TreePath treePath = graphTree.getPathForRow(i);
            if (graphTree.isExpanded(i))
                rowsPaths.add(treePath);
        }
        return rowsPaths;
    }

    private void recoverGraphTreeExpandedState(java.util.List<TreePath> rowsPaths) {
        for (TreePath oldTreePath : rowsPaths) {
            TreePath newTreePath = getTreePathAfterRefresh(oldTreePath);
            if (newTreePath.getPathCount() > 1)
                graphTree.expandPath(newTreePath);
        }
    }

    public void refreshTree() {
        //preserve selection path
        TreePath oldSelectionPath = graphTree.getSelectionPath();

        //preserve expanded paths
        java.util.List<TreePath> rowsPaths = saveGraphTreeExpandedState();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        TreeModel model = new MyTreeModel(root, this);
        Set<String> graphNames = new HashSet<String>(grammar.getGraphs().keySet());  //all graphs that are not linked to Main

        addGraph("Main", root, graphNames);

        while (!graphNames.isEmpty()) {
            addGraph(graphNames.iterator().next(), root, graphNames);
        }
        graphTree.setModel(model);

        //preserve expanded paths
        recoverGraphTreeExpandedState(rowsPaths);

        //try to do selection as before
        if (oldSelectionPath != null && oldSelectionPath.getPathCount() > 1) {
            TreePath newSel = getTreePathAfterRefresh(oldSelectionPath);
            graphTree.setSelectionPath(newSel);
        }
        mustRefreshGraphsTree = false;
    }

    private TreePath getTreePathAfterRefresh(TreePath oldTreePath) {
        TreePath newTreePath = new TreePath(graphTree.getModel().getRoot());

        for (int i = 1; i < oldTreePath.getPathCount(); i++) {
            DefaultMutableTreeNode lastPathNode = (DefaultMutableTreeNode) newTreePath.getLastPathComponent();

            //check if the next node from the old selection is a child in the new hierarchy

            DefaultMutableTreeNode found = null;
            for (int j = 0; j < lastPathNode.getChildCount(); j++) {
                if (lastPathNode.getChildAt(j).toString().equals(oldTreePath.getPathComponent(i).toString())) {
                    found = (DefaultMutableTreeNode) lastPathNode.getChildAt(j);
                    break;
                }
            }
            if (found != null) {
                newTreePath = newTreePath.pathByAddingChild(found);
            } else {
                break;
            }
        }

        return newTreePath;
    }

    private void addGraph(String childGraphId, DefaultMutableTreeNode parentNode, Set<String> graphNames) {
        Graph g = grammar.getGraphs().get(childGraphId);
        if (g == null) {
            MyTreeNodeObject nodeObject = new MyTreeNodeObject();
            nodeObject.text = childGraphId;

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(nodeObject);
            parentNode.add(newNode);

            return;
        }

        graphNames.remove(g.getId());

        MyTreeNodeObject nodeObject = new MyTreeNodeObject();
        nodeObject.graph = g;
        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(nodeObject);

        parentNode.add(newNode);

        if (isRecursive(g, parentNode)) {
            nodeObject.isRecursive = true; //this will get it to render in blue, and children won't be added
            return;
        }

        Set<String> childGraphs = new TreeSet<String>();
        for (GraphNode gn : g.getGraphNodes().values()) {
            for (String clause : graphEditor.renderingInfos.get(gn).matchingClauses) {
                if (clause.startsWith(":")) {
                    String str = clause.substring(1);
                    int aux = str.indexOf('(');
                    if (aux > 0) str = str.substring(0, aux);
                    childGraphs.add(str);
                }
            }
        }

        for (String newChildGraphId : childGraphs) {
            addGraph(newChildGraphId, newNode, graphNames);
        }
    }

    private boolean isRecursive(Graph toBeAdded, DefaultMutableTreeNode toBeParent) {
        while (toBeParent != null && toBeParent.getUserObject() != null) {
            if (toBeAdded == ((MyTreeNodeObject) toBeParent.getUserObject()).graph) {
                return true;
            }
            toBeParent = (DefaultMutableTreeNode) toBeParent.getParent();
        }
        return false;
    }

    public void selectChildGraph(String name) {
        DefaultMutableTreeNode current = ((DefaultMutableTreeNode) graphTree.getSelectionPath().getLastPathComponent());
        if (current.getChildCount() == 0) {
            for (TreeNode treeNode : current.getPath()) {
                if (treeNode.toString() == null) continue;
                if (treeNode.toString().equals(current.toString())) {
                    current = (DefaultMutableTreeNode) treeNode;
                    break;
                }
            }
        }

        for (int i = 0; i < current.getChildCount(); i++) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) current.getChildAt(i);
            if (treeNode.toString().equals(name)) {
                graphTree.setSelectionPath(new TreePath(current.getPath()).pathByAddingChild(treeNode));
                return;
            }
        }
    }

    public void selectGraph(Graph graph) {
        if (((MyTreeNodeObject) ((DefaultMutableTreeNode) graphTree.getSelectionPath().getLastPathComponent()).getUserObject()).graph == graph)
            return;
        selectGraph((DefaultMutableTreeNode) graphTree.getModel().getRoot(), graph);
    }

    private boolean selectGraph(DefaultMutableTreeNode current, Graph graph) {
        if (current.getUserObject() != null && ((MyTreeNodeObject) current.getUserObject()).graph == graph) {
            graphTree.setSelectionPath(new TreePath(current.getPath()));
            return true;
        }
        for (int i = 0; i < current.getChildCount(); i++) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) current.getChildAt(i);
            if (selectGraph(treeNode, graph))
                return true;
        }
        return false;
    }
}
