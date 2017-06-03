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


import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.undo.CompoundEdit;
import java.awt.*;
import java.awt.event.*;

class NodeEditorComponent extends JPanel {

    private boolean drag = false;
    private Point dragLocation = new Point();
    private Dimension initSize;

    private int borderWidth = 15;
    public JEditorPane jsEditor;
    public JTextArea mcEditor;
    public JTextArea ocEditor;
    public GraphNode node;
    public JRadioButton findLongestMatch;
    public JRadioButton customPriority;

    public NodeEditorComponent(final GraphNode node) {
        this.node = node;
        setFocusable(true);
        setMinimumSize(new Dimension(200, 170));
        setSize(getMinimumSize());
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, borderWidth, 0));

        jsEditor = new JEditorPane();
        final JScrollPane jsScrPane = new JScrollPane(jsEditor, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jsEditor.setContentType("text/javascript");

        mcEditor = new JTextArea();
        mcEditor.setRows(3);
        JScrollPane mcScrPane = new JScrollPane(mcEditor, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        ocEditor = new JTextArea();
        ocEditor.setRows(2);
        JScrollPane ocScrPane = new JScrollPane(ocEditor, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        final JTabbedPane tabs = new JTabbedPane(JTabbedPane.NORTH, JTabbedPane.SCROLL_TAB_LAYOUT);

        JPanel GGSpanel = new JPanel(new BorderLayout(0, 5));
        JLabel tokenMatchingCodeLabel = new JLabel("Token matching code");
        final JLabel outputCodeLabel = new JLabel("Output code");
        GGSpanel.add(tokenMatchingCodeLabel, BorderLayout.NORTH);
        GGSpanel.add(mcScrPane, BorderLayout.CENTER);
        final JPanel ocGroup = new JPanel(new BorderLayout());
        ocGroup.add(outputCodeLabel, BorderLayout.NORTH);
        ocGroup.add(ocScrPane, BorderLayout.CENTER);
        GGSpanel.add(ocGroup, BorderLayout.SOUTH);

//        JPanel prioritypPanel = new JPanel(new GridLayout(3, 1));
//        ButtonGroup grbtn = new ButtonGroup();
//        customPriority = new JRadioButton("Custom priority", true);
//        findLongestMatch = new JRadioButton("Find longest match");
//        grbtn.add(customPriority);
//        grbtn.add(findLongestMatch);
//        prioritypPanel.add(customPriority);
//        prioritypPanel.add(findLongestMatch);

        tabs.add("GGS", GGSpanel);
        tabs.add("JavaScript", jsScrPane);
        //tabs.add("Priority", prioritypPanel);

        add(tabs, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getX() > getWidth() - borderWidth && e.getY() > getHeight() - borderWidth) {
                    drag = true;
                    dragLocation = e.getPoint();
                    initSize = getSize();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                drag = false;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (drag) {
                    Dimension d = new Dimension((int) (initSize.width + (e.getPoint().getX() - dragLocation.getX())), (int) (initSize.height + (e.getPoint().getY() - dragLocation.getY())));
                    if (d.width < getMinimumSize().width) d.width = getMinimumSize().width;
                    if (d.height < getMinimumSize().height) d.height = getMinimumSize().height;
                    setSize(d);
                    validate();
                }
            }
        });

        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                GrammarEditor.theEditor.graphEditor.hideEditNodeCancel();
            }
        });

        if (node.isEndNode() || node.isStartNode()){
            tokenMatchingCodeLabel.setEnabled(false);
            mcEditor.setEnabled(false);
        }

        mcEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    if (e.getDocument().getLength() > 1 && e.getDocument().getText(0,2).equals("//")){
                        ocEditor.setEnabled(false);
                        jsEditor.setEnabled(false);
                        outputCodeLabel.setEnabled(false);
                        tabs.setEnabledAt(1,false);
                    }else{
                        ocEditor.setEnabled(true);
                        jsEditor.setEnabled(true);
                        outputCodeLabel.setEnabled(true);
                        tabs.setEnabledAt(1,true);
                    }
                } catch (BadLocationException e1) {
                    e1.printStackTrace();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                insertUpdate(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                insertUpdate(e);
            }
        });
    }

    public void updateNode() {

        CompoundEdit edits = new CompoundEdit();
        EditNodeCommand editNodeCommand = new EditNodeCommand(node);
        editNodeCommand.setPrev();
        if (!node.isStartNode() && !node.isEndNode()) {
            node.setTokenMatchingCode(mcEditor.getText().trim());
        }
        node.setJsCode(jsEditor.getText().trim());
        node.setOutputCode(ocEditor.getText().trim());
        //node.setFindLongestMatch(findLongestMatch.isSelected());
        editNodeCommand.setNext();

        if (editNodeCommand.isRelevant()) {
            edits.addEdit(editNodeCommand);
            if (node.nodeType == GraphNode.NodeType.Comment) {


                for (GraphNode graphNode : node.getParentGraph().getGraphNodes().values()) {//delete all path to this node

                    if (!graphNode.getChildNodesIndexes().contains(node.getIndex())) continue;

                    DeletePathCommand deletePathCommand = new DeletePathCommand();
                    deletePathCommand.start = graphNode;
                    deletePathCommand.end = node;
                    deletePathCommand.priorityIndex = graphNode.getChildNodesIndexes().indexOf(node.getIndex());

                    graphNode.getChildNodesIndexes().remove(deletePathCommand.priorityIndex);
                    edits.addEdit(deletePathCommand);
                }

                for (int index : node.getChildNodesIndexes()) {//delete all paths from this node

                    DeletePathCommand deletePathCommand = new DeletePathCommand();
                    deletePathCommand.start = node;
                    deletePathCommand.end = node.getParentGraph().getGraphNodes().get(index);
                    deletePathCommand.priorityIndex = 0;
                    edits.addEdit(deletePathCommand);
                }
                node.getChildNodesIndexes().clear();
            }
        }

        edits.end();
        GrammarEditor.theEditor.undoSupport.postEdit(edits);
        GrammarEditor.theEditor.graphEditor.renderingInfos.get(node).formatDirty = true;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        ((Graphics2D) g).setStroke(new BasicStroke(1.1F));

        g.setColor(Color.black);
        g.drawString("Node " + node.getIndex(), 2, this.getHeight() - 2);

        g.setColor(Color.LIGHT_GRAY);
        int b = borderWidth;

        while (b > 0) {
            g.drawLine(getWidth() - b, getHeight(), getWidth(), getHeight() - b);
            b -= 3;
        }
    }
}
