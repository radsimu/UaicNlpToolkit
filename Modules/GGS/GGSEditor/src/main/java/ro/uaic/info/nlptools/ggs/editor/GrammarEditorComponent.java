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

import ro.uaic.info.nlptools.ggs.engine.core.GGSMacroNotFoundException;
import ro.uaic.info.nlptools.ggs.engine.grammar.Graph;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import javax.swing.*;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEditSupport;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;


public class GrammarEditorComponent extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    public UndoableEditSupport undoSupport;

    public Graph currentGraph;
    List<GraphNode> selectedNodes;
    List<GraphNode> clipboard;
    public Map<GraphNode, GraphNodeRenderingInfo> renderingInfos;
    private PathOrderBox draggedPathOrderBox;
    private PathOrderBox swapPathOrderBox;
    private NodeEditorComponent currentJsEditor;
    public AffineTransform g2dTransform;
    private Map<Graph, AffineTransform> g2dTransforms;
    private Color background = new Color(153, 153, 153);
    public List<PathOrderBox> pathOrderBoxes = new ArrayList<PathOrderBox>();
    public double zoomAmmount = 0.2d;

    public GrammarEditorComponent() {
        super();
        selectedNodes = new ArrayList<GraphNode>();
        clipboard = new ArrayList<GraphNode>();
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);
        KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keyboardFocusManager.addKeyEventDispatcher(new MyDispatcher());
        this.setLayout(null);
        g2dTransforms = new HashMap<Graph, AffineTransform>();
    }

    public void setCurrentGraph(Graph currentGraph) {
        if (this.currentGraph != null && this.currentGraph != currentGraph) {
            selectedNodes.clear();
        }
        this.currentGraph = currentGraph;
        g2dTransform = g2dTransforms.get(currentGraph);
        if (g2dTransform == null) {
            g2dTransform = new AffineTransform();
            g2dTransforms.put(currentGraph, g2dTransform);
        }
        hideEditNodeCancel();
        this.requestFocusInWindow();
        this.updateUI();
    }


    private int grammarRectangleWidth;
    private int grammarRectangleHeight;

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentGraph == null) return;

        g.setColor(this.getBackground());
        g.fillRect(0, 0, this.getWidth(), this.getHeight());


        BufferedImage bufferedImage = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setFont(getFont());

        BufferedImage jsBufferedImage = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D jsG = jsBufferedImage.createGraphics();

        jsG.setTransform(g2dTransform);
        g2d.setTransform(g2dTransform);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        grammarRectangleHeight = 0;
        grammarRectangleWidth = 0;


        //establish curve nodes
        for (GraphNode node : currentGraph.getGraphNodes().values()) {
            int count = 0;
            if (node.getJsCode() == null && node.getOutputCode() == null && node.isEmpty() && node.getChildNodesIndexes().size() == 1) {
                for (GraphNode gn : node.getParentGraph().getGraphNodes().values())
                    if (gn.getChildNodesIndexes().contains(node.getIndex())) {
                        count++;
                        if (count > 1) {
                            break;
                        }
                    }
            }
            renderingInfos.get(node).isCurve = count == 1;
        }

        for (GraphNode gn : currentGraph.getGraphNodes().values()) {
            renderingInfos.get(gn).reformatIfDirty(g2d);
        }

        if (GrammarEditor.theEditor.mustRefreshGraphsTree)
            GrammarEditor.theEditor.refreshTree();

        //desenez path-urile
        pathOrderBoxes.clear();
        g2d.setColor(Color.BLACK);

        for (GraphNode gn : currentGraph.getGraphNodes().values()) {
            int assertionsCount = 0;
            if (!gn.isFindLongestMatch())
                for (int i = 0; i < gn.getChildNodesIndexes().size(); i++) {
                    if (gn.getParentGraph().getGraphNodes().get(gn.getChildNodesIndexes().get(i)).getTokenMatchingCode().startsWith("?"))
                        assertionsCount++;
                }

            int k = 1;
            for (int i = 0; i < gn.getChildNodesIndexes().size(); i++) {
                int childIndex = gn.getChildNodesIndexes().get(i);
                if (gn.getParentGraph().getGraphNodes().get(childIndex) == null) continue;
                Point2D middle = drawPath(gn, gn.getParentGraph().getGraphNodes().get(childIndex), g2d);

                if (gn.getChildNodesIndexes().size() - assertionsCount > 1)
                    if (!gn.getParentGraph().getGraphNodes().get(gn.getChildNodesIndexes().get(i)).isAssertion() &&
                            !gn.getParentGraph().getGraphNodes().get(gn.getChildNodesIndexes().get(i)).isComment()) {
                        pathOrderBoxes.add(new PathOrderBox(gn, gn.getParentGraph().getGraphNodes().get(childIndex), i, middle, g2d, k));
                        k++;
                    }
            }
        }

        for (GraphNode gn : currentGraph.getGraphNodes().values()) {
            drawNode(gn, g2d, jsG);
        }

        //draw jsBoxes on top
        g2d.setTransform(new AffineTransform());
        g2d.drawImage(jsBufferedImage, 0, 0, null);
        jsG.dispose();
        g2d.setTransform(g2dTransform);
        boolean intersects = false;
        swapPathOrderBox = null;
        for (PathOrderBox pathOrderBox : pathOrderBoxes) {
            Color c = new Color(250, 250, 150);
            if (selectedPathOrderBox != null && pathOrderBox.parentGraphNode == selectedPathOrderBox.parentGraphNode) {
                c = new Color(170, 250, 170);
                if (swapPathOrderBox == null && draggedPathOrderBox.index != pathOrderBox.index && draggedPathOrderBox.rect.intersects(pathOrderBox.rect.getBounds2D())) {
                    c = Color.green;
                    intersects = true;
                    swapPathOrderBox = pathOrderBox;
                }
            }

            //drawPathOrderBox(pathOrderBox, c, g2d);
        }
        if (!intersects) swapPathOrderBox = null;

        if (selectedPathOrderBox != null) {
            Color c = new Color(250, 250, 150);
            drawPathOrderBox(draggedPathOrderBox, c, g2d);
        }
        drawSelection(g2d);
        //draw selection box
        if (selecting) {
            g2d.setColor(Color.gray);
            g2d.draw(selectRectangle);
        } else if (pathing) {
            drawPathing(pressPoint, g2d);
        }
        grammarRectangleWidth += 50;
        grammarRectangleHeight += 30;

        AffineTransform transformBackup = ((Graphics2D) g).getTransform();

        AffineTransform transformConcat = new AffineTransform(transformBackup);
        transformConcat.concatenate(g2dTransform);
        ((Graphics2D) g).setTransform(transformConcat);
        g.setColor(background);
        g.fillRect(0, 0, grammarRectangleWidth, grammarRectangleHeight);
        g.setColor(Color.black);
        g.drawRect(0, 0, grammarRectangleWidth, grammarRectangleHeight);
        g.drawString(currentGraph.getId(), 20, 20);

        ((Graphics2D) g).setTransform(transformBackup);
        g.drawImage(bufferedImage, 0, 0, null);
        //((Graphics2D) g).setTransform(g2dTransform);
        g2d.dispose();
        //GrammarEditor.theEditor.macrosButtonToggle.repaint();
        //GrammarEditor.theEditor.grammarJSEditorButtonToggle.repaint();
    }

    @Override
    public void updateUI() {
        if (GrammarEditor.theEditor != null)
            GrammarEditor.theEditor.splitPane1.updateUI();
        else
            super.updateUI();
    }

    GraphNode pathingTo = null;

    private void drawPathing(Point2D.Float toPoint, Graphics2D g) {
        GraphNode gn = select(toPoint);
        if (gn != null && !gn.isStartNode() && gn.nodeType != GraphNode.NodeType.Comment) {
            pathingTo = gn;

            for (GraphNode sel : selectedNodes) {
                if (sel.isEndNode()) continue;
                if (sel.nodeType == GraphNode.NodeType.Comment) continue;
                if (sel.getChildNodesIndexes().contains(gn.getIndex())) {
                    g.setColor(Color.red);
                } else {
                    g.setColor(Color.green);
                }
                Stroke str = g.getStroke();
                g.setStroke(new BasicStroke(2));
                drawPath(sel, gn, g);
                g.setStroke(str);
            }

        } else {
            g.setColor(Color.gray);
            pathingTo = null;
            for (GraphNode sel : selectedNodes) {
                if (sel.isEndNode()) continue;
                if (sel.nodeType == GraphNode.NodeType.Comment) continue;
                GraphNodeRenderingInfo renderInfo = renderingInfos.get(sel);
                drawPath(new Point2D.Float(sel.getX() + renderInfo.width, sel.getY()), renderInfo.height, toPoint, 24, g, null, null);
            }
        }

    }

    private void drawSelection(Graphics2D g2d) {
        int selectBoxPadding = 2;
        for (GraphNode gn : selectedNodes) {
            GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
            g2d.setColor(Color.red);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(gn.getX() - selectBoxPadding, gn.getY() + renderInfo.height / 2 + selectBoxPadding, gn.getX() - selectBoxPadding, gn.getY() + renderInfo.height / 2 - selectBoxPadding);
            g2d.drawLine(gn.getX() - selectBoxPadding, gn.getY() + renderInfo.height / 2 + selectBoxPadding, gn.getX() + selectBoxPadding, gn.getY() + renderInfo.height / 2 + selectBoxPadding);

            g2d.drawLine(gn.getX() - selectBoxPadding, gn.getY() - renderInfo.height / 2 - selectBoxPadding, gn.getX() + selectBoxPadding, gn.getY() - renderInfo.height / 2 - selectBoxPadding);
            g2d.drawLine(gn.getX() - selectBoxPadding, gn.getY() - renderInfo.height / 2 - selectBoxPadding, gn.getX() - selectBoxPadding, gn.getY() - renderInfo.height / 2 + selectBoxPadding);

            g2d.drawLine(gn.getX() + renderInfo.width + selectBoxPadding, gn.getY() + renderInfo.height / 2 + selectBoxPadding, gn.getX() + renderInfo.width + selectBoxPadding, gn.getY() + renderInfo.height / 2 - selectBoxPadding);
            g2d.drawLine(gn.getX() + renderInfo.width + selectBoxPadding, gn.getY() + renderInfo.height / 2 + selectBoxPadding, gn.getX() + renderInfo.width - selectBoxPadding, gn.getY() + renderInfo.height / 2 + selectBoxPadding);

            g2d.drawLine(gn.getX() + renderInfo.width + selectBoxPadding, gn.getY() - renderInfo.height / 2 - selectBoxPadding, gn.getX() + renderInfo.width - selectBoxPadding, gn.getY() - renderInfo.height / 2 - selectBoxPadding);
            g2d.drawLine(gn.getX() + renderInfo.width + selectBoxPadding, gn.getY() - renderInfo.height / 2 - selectBoxPadding, gn.getX() + renderInfo.width + selectBoxPadding, gn.getY() - renderInfo.height / 2 + selectBoxPadding);
            g2d.setStroke(new BasicStroke(1));
        }
    }


    Color jsVarsColor = new Color(0, 150, 0);
    Color backColor = new Color(153, 153, 153);

    @SuppressWarnings({"ConstantConditions"})
    private void drawNode(GraphNode node, Graphics2D g, Graphics2D jsG) {
        if (node == null) return;
        GraphNodeRenderingInfo renderInfo = renderingInfos.get(node);
        Rectangle boundingBox = new Rectangle();
        boundingBox.setBounds(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);

        //compute the size of the outline rectangle

        int w = node.getX() + renderInfo.width;
        int h = node.getY() + renderInfo.height;
        if (grammarRectangleWidth < w) grammarRectangleWidth = w;
        if (grammarRectangleHeight < h) grammarRectangleHeight = h;

        if (node.isStartNode()) {
            g.setColor(Color.WHITE);
            g.fillOval(node.getX(), node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX(), node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);

            g.setColor(backColor);
            g.fillOval(node.getX() + renderInfo.width / 6 + renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX() + renderInfo.width / 6 + renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);

            g.drawLine(node.getX() + renderInfo.width / 3, node.getY(), node.getX() + renderInfo.width, node.getY());
            g.drawLine(node.getX() + renderInfo.width, node.getY(), node.getX() + 5 * renderInfo.width / 6, node.getY() + renderInfo.height / 6);
            g.drawLine(node.getX() + renderInfo.width, node.getY(), node.getX() + 5 * renderInfo.width / 6, node.getY() - renderInfo.height / 6);
        } else if (node.isEndNode()) {
            g.setColor(Color.WHITE);
            g.fillOval(node.getX() + renderInfo.width / 3, node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX() + renderInfo.width / 3, node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);

            g.setColor(backColor);
            g.fillOval(node.getX() + renderInfo.width / 2 - renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX() + renderInfo.width / 2 - renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);

            g.drawLine(node.getX(), node.getY(), node.getX() + 2 * renderInfo.width / 3, node.getY());
            g.drawLine(node.getX() + 2 * renderInfo.width / 3, node.getY(), node.getX() + renderInfo.width / 2, node.getY() + renderInfo.height / 6);
            g.drawLine(node.getX() + 2 * renderInfo.width / 3, node.getY(), node.getX() + renderInfo.width / 2, node.getY() - renderInfo.height / 6);
        } else if (node.nodeType == GraphNode.NodeType.LookBehind) {//lookbehind assertion
            Color c = new Color(0, 200, 0);
            if (node.isNegativeAssertion) c = new Color(200, 0, 0);

            g.setColor(c);
            g.fillOval(node.getX(), node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX(), node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);

            g.setColor(backColor);
            g.fillOval(node.getX() + renderInfo.width / 6 + renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX() + renderInfo.width / 6 + renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);

            g.drawLine(node.getX(), node.getY(), node.getX() + renderInfo.width / 6 + renderInfo.width / 36 + renderInfo.width / 3, node.getY());
            g.drawLine(node.getX() + 2 * renderInfo.width / 3, node.getY(), node.getX() + renderInfo.width, node.getY());
            g.drawLine(node.getX() + renderInfo.width / 4, node.getY(), node.getX() + renderInfo.width / 4 + renderInfo.width / 6, node.getY() + renderInfo.height / 6);
            g.drawLine(node.getX() + renderInfo.width / 4, node.getY(), node.getX() + renderInfo.width / 4 + renderInfo.width / 6, node.getY() - renderInfo.height / 6);
        } else if (node.nodeType == GraphNode.NodeType.LookAhead) {//lookahead assertion
            Color c = new Color(0, 200, 0);
            if (node.isNegativeAssertion) c = new Color(200, 0, 0);

            g.setColor(c);
            g.fillOval(node.getX() + renderInfo.width / 3, node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX() + renderInfo.width / 3, node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);

            g.setColor(backColor);
            g.fillOval(node.getX() + renderInfo.width / 2 - renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX() + renderInfo.width / 2 - renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);

            g.drawLine(node.getX(), node.getY(), node.getX() + renderInfo.width / 3, node.getY());
            g.drawLine(node.getX() + renderInfo.width / 2 - renderInfo.width / 36, node.getY(), node.getX() + renderInfo.width, node.getY());

            g.drawLine(node.getX() + 3 * renderInfo.width / 4, node.getY(), node.getX() + 3 * renderInfo.width / 4 - renderInfo.width / 6, node.getY() + renderInfo.height / 6);
            g.drawLine(node.getX() + 3 * renderInfo.width / 4, node.getY(), node.getX() + 3 * renderInfo.width / 4 - renderInfo.width / 6, node.getY() - renderInfo.height / 6);
        } else if (node.nodeType == GraphNode.NodeType.CrossReference) {//cross reference
            Color c = new Color(0, 200, 0);
            if (node.isNegativeAssertion) c = new Color(200, 0, 0);

            g.setColor(c);
            g.fillOval(node.getX(), node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX(), node.getY() - renderInfo.height / 2, 2 * renderInfo.width / 3, renderInfo.height);

            g.setColor(backColor);
            g.fillOval(node.getX() + renderInfo.width / 6 + renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX() + renderInfo.width / 6 + renderInfo.width / 36, node.getY() - renderInfo.height / 3, renderInfo.width / 3, 2 * renderInfo.height / 3);

            g.drawLine(node.getX() + renderInfo.width / 3, node.getY(), node.getX() + renderInfo.width, node.getY());
            g.drawLine(node.getX() + renderInfo.width, node.getY(), node.getX() + 5 * renderInfo.width / 6, node.getY() + renderInfo.height / 6);
            g.drawLine(node.getX() + renderInfo.width, node.getY(), node.getX() + 5 * renderInfo.width / 6, node.getY() - renderInfo.height / 6);

        } else if (node.nodeType == GraphNode.NodeType.AssertionEnd) {
            g.setColor(Color.WHITE);
            g.fillOval(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);
            g.setColor(Color.BLACK);
            g.drawOval(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);

            g.drawLine(node.getX() + renderInfo.width / 3, node.getY() - renderInfo.height / 4, node.getX() + 2 * renderInfo.width / 3, node.getY() + renderInfo.height / 4);
            g.drawLine(node.getX() + renderInfo.width / 3, node.getY() + renderInfo.height / 4, node.getX() + 2 * renderInfo.width / 3, node.getY() - renderInfo.height / 4);
            g.drawLine(node.getX(), node.getY(), node.getX() + renderInfo.width / 2, node.getY());
        } else if (renderInfo.isEmpty) {
            if (!renderInfo.isCurve) {
                g.setColor(Color.WHITE);
                g.fillOval(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);
                g.setColor(Color.BLACK);
                g.drawOval(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);
                g.drawLine(node.getX(), node.getY(), node.getX() + renderInfo.width, node.getY());
                g.drawLine(node.getX() + 2 * renderInfo.width / 3, node.getY(), node.getX() + renderInfo.width / 3, node.getY() - 2 * renderInfo.height / 6);
                g.drawLine(node.getX() + 2 * renderInfo.width / 3, node.getY(), node.getX() + renderInfo.width / 3, node.getY() + 2 * renderInfo.height / 6);
            } else {//rotate empty arrow

                GraphNode cPoint = node.getParentGraph().getGraphNodes().get(node.getChildNodesIndexes().get(0));
                GraphNode aPoint = null;
                //find parent graph node
                for (GraphNode gn : node.getParentGraph().getGraphNodes().values())
                    if (gn.getChildNodesIndexes().contains(node.getIndex())) {
                        aPoint = gn;
                        break;
                    }

                Point.Float A = new Point2D.Float(aPoint.getX(), aPoint.getY());
                GraphNodeRenderingInfo aRenderinginfo = renderingInfos.get(aPoint);
                if (aRenderinginfo.isCurve) {
                    A.x += aRenderinginfo.width / 2;
                } else {
                    A.x += aRenderinginfo.width;
                }

                Point.Float C = new Point.Float(cPoint.getX(), cPoint.getY());
                GraphNodeRenderingInfo cRenderinginfo = renderingInfos.get(cPoint);
                if (cRenderinginfo.isCurve)
                    C.x += cRenderinginfo.width / 2;

                Point.Float B = new Point2D.Float(node.getX() + renderInfo.width / 2, node.getY());
                float c = (float) Math.sqrt((A.x - B.x) * (A.x - B.x) + (A.y - B.y) * (A.y - B.y));
                float a = (float) Math.sqrt((C.x - B.x) * (C.x - B.x) + (C.y - B.y) * (C.y - B.y));
                C.x = (B.x + (C.x - B.x) * c / a);
                C.y = (B.y + (C.y - B.y) * c / a);

                renderInfo.startCtrlPoint = new Point.Float(B.x, B.y);
                renderInfo.endCtrlPoint = new Point.Float(B.x, B.y);

                //compute the control points
                int startRadius = 30;

                Point.Float M = new Point2D.Float((A.x + C.x) / 2, (A.y + C.y) / 2);
                float d = (float) Math.sqrt((M.x - B.x) * (M.x - B.x) + (M.y - B.y) * (M.y - B.y));
                int sign = (int) (Math.signum((B.y - A.y) / (C.y - A.y) - (B.x - A.x) / (C.x - A.x)) * Math.signum((M.y - B.y) / (M.x - B.x)));
                if (sign == 0) sign = 1;

                if ((C.y == A.y)) {
                    renderInfo.startCtrlPoint.x += Math.signum(C.x - A.x) * startRadius;
                    renderInfo.endCtrlPoint.x -= Math.signum(C.x - A.x) * startRadius;
                } else if (d != 0) {
                    renderInfo.startCtrlPoint.x += sign * (M.y - B.y) * startRadius / d;
                    renderInfo.endCtrlPoint.x -= sign * (M.y - B.y) * startRadius / d;
                }

                if (C.x == A.x) {
                    renderInfo.startCtrlPoint.y -= Math.signum(C.y - A.y) * startRadius;
                    renderInfo.endCtrlPoint.y += Math.signum(C.y - A.y) * startRadius;
                } else if (d != 0) {
                    renderInfo.startCtrlPoint.y -= sign * (M.x - B.x) * startRadius / d;
                    renderInfo.endCtrlPoint.y += sign * (M.x - B.x) * startRadius / d;
                }

//                g.fillOval((int) M.x - 2, (int) M.y - 2, 4, 4);
//                g.fillOval((int) renderInfo.startCtrlPoint.x - 2, (int) renderInfo.startCtrlPoint.y - 2, 4, 4);
//                g.fillOval((int) renderInfo.endCtrlPoint.x - 2, (int) renderInfo.endCtrlPoint.y - 2, 4, 4);

                Double theta = Math.atan2(renderInfo.endCtrlPoint.y - renderInfo.startCtrlPoint.y, renderInfo.endCtrlPoint.x - renderInfo.startCtrlPoint.x);

                g.setColor(Color.WHITE);
                g.fillOval(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);
                g.setColor(Color.BLACK);
                g.drawOval(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);

                //draw the rotated arrow
                AffineTransform transform = g.getTransform();
                g.setColor(new Color(0, 0, 0, 255));
                g.translate(node.getX(), node.getY() - renderInfo.height / 2);
                g.rotate(theta + Math.PI, 9, 9);
                g.drawLine(2 * renderInfo.width / 3, renderInfo.height / 2, renderInfo.width / 3, renderInfo.height / 2 - 2 * renderInfo.height / 6);
                g.drawLine(2 * renderInfo.width / 3, renderInfo.height / 2, renderInfo.width / 3, renderInfo.height / 2 + 2 * renderInfo.height / 6);

                g.setTransform(transform);
            }
        } else {// the normal node
            if (node.nodeType != GraphNode.NodeType.Comment)
                g.setColor(Color.WHITE);
            else
                g.setColor(backColor);
            g.fillRect(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);
            g.setColor(Color.BLACK);
            g.drawRect(node.getX(), node.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height);

            FontMetrics metrics = g.getFontMetrics();

            for (int i = 0; i < renderInfo.matchingClauses.size(); i++) {
                boolean error = false;
                //replace macros if present

                String trans = renderInfo.matchingClauses.get(i);
                try {
                    trans = node.translateMacros(renderInfo.matchingClauses.get(i));
                } catch (GGSMacroNotFoundException e) {
                    g.setColor(Color.red);
                    error = true;
                }

                if (node.nodeType != GraphNode.NodeType.Comment && !GraphNode.tokenMatchingCodeValidatorPattern.matcher(trans).matches()) {
                    g.setColor(Color.red);
                    error = true;
                }

                if (node.nodeType != GraphNode.NodeType.Comment && !error && renderInfo.matchingClauses.get(i).startsWith(":")) {
                    if (isControlDown && boldClauseRectangle == renderInfo.clausesBoxes.get(i))
                        g.setColor(new Color(180, 180, 255));
                    else g.setColor(new Color(200, 200, 255));

                    g.fill(renderInfo.clausesBoxes.get(i));
                    g.setColor(Color.black);

                    String str = renderInfo.matchingClauses.get(i).substring(1);
                    int aux = str.indexOf('(');
                    if (aux > 0) str = str.substring(0, aux);

                    if (GrammarEditor.theEditor.grammar.getGraphs().get(str) == null) {
                        g.setColor(new Color(255, 150, 150));
                        error = true;
                    }
                }

                int offset = 0;
                int offsetPix = 0;
                if (!error) {
                    Matcher matcher = GraphNode.macroOrJsVarPattern.matcher(renderInfo.matchingClauses.get(i));
                    while (matcher.find()) {
                        String part = renderInfo.matchingClauses.get(i).substring(offset, matcher.start());
                        g.setColor(Color.black);
                        g.drawString(part, (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
                        offsetPix += metrics.stringWidth(part);

                        part = matcher.group();
                        if (matcher.group().startsWith("$")) {
                            g.setColor(Color.lightGray);
                            g.drawString("$", (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
                            offsetPix += metrics.stringWidth("$");

                            part = part.substring(1, part.length() - 1);
                            g.setColor(jsVarsColor);
                            g.drawString(part, (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
                            offsetPix += metrics.stringWidth(part);

                            g.setColor(Color.lightGray);
                            g.drawString("$", (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
                            offsetPix += metrics.stringWidth("$");

                        } else {
                            g.setColor(Color.lightGray);
                            g.drawString("@", (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
                            offsetPix += metrics.stringWidth("@");

                            String macro = part.substring(1, part.length() - 1);
                            g.setColor(Color.magenta);
                            g.drawString(macro, (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
                            offsetPix += metrics.stringWidth(macro);

                            g.setColor(Color.lightGray);
                            g.drawString("@", (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
                            offsetPix += metrics.stringWidth("@");
                        }
                        offset = matcher.end();
                    }
                    g.setColor(Color.black);
                }
                String clause = renderInfo.matchingClauses.get(i).substring(offset);
                if (node.nodeType == GraphNode.NodeType.Comment && i == 0)
                    clause = clause.substring(2);
                g.drawString(clause, (int) renderInfo.clausesBoxes.get(i).getX() + offsetPix, (int) (renderInfo.clausesBoxes.get(i).getY() + 3 * renderInfo.clausesBoxes.get(0).getHeight() / 4));
            }
        }

        if (node.getJsCode() != null) {
            if (!node.isJsMinimized()) {
                FontMetrics metrics = jsG.getFontMetrics();
                int marginsY = 1;
                int marginsX = 5;
                String[] lines = node.getJsCode().split("[\r\n]+");
                boundingBox.height += 20;
                if (node.getOutputCode() != null && !node.getOutputCode().isEmpty()) {
                    boundingBox.height += metrics.getHeight() + marginsY * 2;
                }

                int hgt = lines.length * (metrics.getHeight() + marginsY) + marginsY;
                int adv = 0;
                for (String line : lines) {
                    int aux = metrics.stringWidth(line) + marginsX * 2;
                    if (aux > adv)
                        adv = aux;
                }
                jsG.setColor(new Color(200, 250, 200, 220));
                jsG.fillRect(node.getX() + renderInfo.width / 2 - adv / 2, boundingBox.y + boundingBox.height - marginsY, adv, hgt);
                jsG.setColor(Color.BLACK);
                jsG.drawRect(node.getX() + renderInfo.width / 2 - adv / 2, boundingBox.y + boundingBox.height - marginsY, adv, hgt);

                w = node.getX() + renderInfo.width / 2 - adv / 2 + adv;
                h = boundingBox.y + boundingBox.height - marginsY + hgt;
                if (grammarRectangleWidth < w) grammarRectangleWidth = w;
                if (grammarRectangleHeight < h) grammarRectangleHeight = h;


                for (int i = 0; i < lines.length; i++) {
                    jsG.drawString(lines[i], node.getX() + renderInfo.width / 2 - adv / 2 + marginsX, boundingBox.y + boundingBox.height + i * (metrics.getHeight() + marginsY) + metrics.getHeight() - marginsY);
                }

                //draw js bullet
                g.setColor(new Color(10, 150, 10));
                g.fillOval(renderInfo.jsToggle.x, renderInfo.jsToggle.y, renderInfo.jsToggle.width, renderInfo.jsToggle.height);
                g.setColor(Color.black);
                g.drawOval(renderInfo.jsToggle.x, renderInfo.jsToggle.y, renderInfo.jsToggle.width, renderInfo.jsToggle.height);

                g.drawLine(boundingBox.x + boundingBox.width / 2, boundingBox.y + boundingBox.height - 1, boundingBox.x + boundingBox.width / 2, boundingBox.y + boundingBox.height - 10);
                g.drawLine(boundingBox.x + boundingBox.width / 2, boundingBox.y + boundingBox.height - 10, renderInfo.jsToggle.x + renderInfo.jsToggle.width / 2, boundingBox.y + boundingBox.height - 10);
                g.drawLine(renderInfo.jsToggle.x + renderInfo.jsToggle.width / 2, boundingBox.y + boundingBox.height - 10, renderInfo.jsToggle.x + renderInfo.jsToggle.width / 2, renderInfo.jsToggle.y + renderInfo.jsToggle.height / 2);
            } else {
                g.setColor(new Color(200, 0, 0));
                g.fillOval((int) renderInfo.jsToggle.getX(), (int) renderInfo.jsToggle.getY(), (int) renderInfo.jsToggle.getWidth(), (int) renderInfo.jsToggle.getHeight());
                g.setColor(Color.black);
                g.setStroke(new BasicStroke(2));
                g.drawOval((int) renderInfo.jsToggle.getX() - 1, (int) renderInfo.jsToggle.getY(), (int) renderInfo.jsToggle.getWidth(), (int) renderInfo.jsToggle.getHeight());
                g.setStroke(new BasicStroke(1));
            }
        }

        if (node.getOutputCode() != null) {
            FontMetrics metrics = g.getFontMetrics();
            int marginsY = 1;
            int marginsX = 5;
            int hgt = metrics.getHeight() + marginsY * 2;


            int adv = metrics.stringWidth(node.getOutputCode()) + marginsX * 2;

            boundingBox.setBounds(node.getX() + renderInfo.width / 2 - adv / 2, node.getY() + renderInfo.height / 2 - marginsY, adv, hgt);
            g.setColor(new Color(250, 210, 210));
            g.fillRect(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
            g.setColor(Color.BLACK);
            g.drawRect(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);

            w = boundingBox.x + boundingBox.width;
            h = boundingBox.y + boundingBox.height;
            if (grammarRectangleWidth < w) grammarRectangleWidth = w;
            if (grammarRectangleHeight < h) grammarRectangleHeight = h;

            boolean error = false;
            if (node.getOutputCode() != null && !GraphNode.outputCodeValidatorPattern.matcher(node.getOutputCode()).matches()) {
                g.setColor(Color.red);
                error = true;
            }

            int offset = 0;
            int offsetPix = 0;
            if (!error) {
                Matcher matcher = GraphNode.jsVarsPattern.matcher(node.getOutputCode());
                while (matcher.find()) {
                    String part = node.getOutputCode().substring(offset, matcher.start());
                    g.setColor(Color.black);
                    g.drawString(part, boundingBox.x + marginsX + offsetPix, boundingBox.y - marginsY + metrics.getHeight());
                    offsetPix += metrics.stringWidth(part);

                    part = node.getOutputCode().substring(matcher.start(), matcher.end());
                    g.setColor(jsVarsColor);
                    g.drawString(part, boundingBox.x + marginsX + offsetPix, boundingBox.y - marginsY + metrics.getHeight());
                    offset = matcher.end();
                    offsetPix += metrics.stringWidth(part);
                }

                g.setColor(Color.black);
            }
            g.drawString(node.getOutputCode().substring(offset), boundingBox.x + marginsX + offsetPix, boundingBox.y - marginsY + metrics.getHeight());
        }
    }

    private Point2D drawPath(Point2D.Float startPoint, int startHeight, Point2D.Float endPoint, int endHeight, Graphics2D g, Point.Float startCtrlPoint, Point.Float endCtrlPoint) {//returns middle of the line
        int rotatedRadius = 30; // radius for empty rotated nodes
        float dist1 = (float) Math.sqrt((startPoint.x - endPoint.x) * (startPoint.x - endPoint.x) + (startPoint.y - endPoint.y) * (startPoint.y - endPoint.y));

        float startRadius = startHeight;
        float endRadius = endHeight;
        if (startCtrlPoint != null)
            startRadius = rotatedRadius;
        if (endCtrlPoint != null)
            endRadius = rotatedRadius;

        if (dist1 < startRadius * 3 + endRadius * 3) {
            float k = dist1 / (startRadius * 3 + endRadius * 3);

            startRadius = startRadius * k;
            if (startCtrlPoint != null) {
                startCtrlPoint.x = startPoint.x - (startPoint.x - startCtrlPoint.x) * k;
                startCtrlPoint.y = startPoint.y - (startPoint.y - startCtrlPoint.y) * k;
            }
            endRadius = endRadius * k;
            if (endCtrlPoint != null) {
                endCtrlPoint.x = endPoint.x - (endPoint.x - endCtrlPoint.x) * k;
                endCtrlPoint.y = endPoint.y - (endPoint.y - endCtrlPoint.y) * k;
            }

        }

        Point.Float startCurvePoint;
        Point.Float endCurvePoint;

        if (endCtrlPoint != null && startCtrlPoint != null) {
            float dist = (float) Math.sqrt((startCtrlPoint.x - endCtrlPoint.x) * (startCtrlPoint.x - endCtrlPoint.x) + (startCtrlPoint.y - endCtrlPoint.y) * (startCtrlPoint.y - endCtrlPoint.y));
            Point.Float vec = new Point.Float((endCtrlPoint.x - startCtrlPoint.x), (endCtrlPoint.y - startCtrlPoint.y));

            startCurvePoint = new Point.Float(startCtrlPoint.x, startCtrlPoint.y);
            startCurvePoint.x += vec.x * startRadius / dist;
            startCurvePoint.y += vec.y * startRadius / dist;
            endCurvePoint = new Point.Float(endCtrlPoint.x, endCtrlPoint.y);
            endCurvePoint.x -= vec.x * endRadius / dist;
            endCurvePoint.y -= vec.y * endRadius / dist;

            CubicCurve2D shape1 = new CubicCurve2D.Float();
            CubicCurve2D shape2 = new CubicCurve2D.Float();

            shape1.setCurve(startPoint, new Point((int) (startCtrlPoint.x + startPoint.x) / 2, (int) (startCtrlPoint.y + startPoint.y) / 2), new Point((int) (startCtrlPoint.x + startCurvePoint.x) / 2, (int) (startCtrlPoint.y + startCurvePoint.y) / 2), startCurvePoint);
            shape2.setCurve(endCurvePoint, new Point((int) (endCtrlPoint.x + endCurvePoint.x) / 2, (int) (endCtrlPoint.y + endCurvePoint.y) / 2), new Point((int) (endCtrlPoint.x + endPoint.x) / 2, (int) (endCtrlPoint.y + endPoint.y) / 2), endPoint);

            g.draw(shape1);
            g.draw(shape2);

//            g.setColor(Color.RED);
//            g.fillOval((int) startPoint.x, (int) startPoint.y, 2, 2);
//            g.fillOval((int) startCurvePoint.x, (int) startCurvePoint.y, 2, 2);
//            g.fillOval((int) endCurvePoint.x, (int) endCurvePoint.y, 2, 2);
//            g.fillOval((int) endPoint.x, (int) endPoint.y, 2, 2);
//            g.setColor(Color.black);

            g.drawLine((int) startCurvePoint.x, (int) startCurvePoint.y, (int) endCurvePoint.x, (int) endCurvePoint.y);
        } else if (startCtrlPoint != null) {
            endCtrlPoint = new Point.Float(endPoint.x, endPoint.y);
            endCtrlPoint.x -= endRadius;

            if (startPoint.x > endPoint.x - startRadius - endRadius) {
                float sqrt2 = (float) Math.sqrt(2);

                if (Math.abs(startPoint.y - endPoint.y) < startRadius + endRadius) {
                    endRadius = endHeight;
                    endCtrlPoint.x = endPoint.x - endRadius;// / 2;

                    if (startPoint.y <= endPoint.y) {
                        endCtrlPoint.y -= endRadius;
                        endRadius *= sqrt2;
                    } else {
                        endCtrlPoint.y += endRadius;
                        endRadius *= sqrt2;
                    }
                } else {
                    if (startPoint.y < endPoint.y) {
                        float dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > endRadius) dif = endRadius;
                        endCtrlPoint.y -= dif;
                        endRadius *= sqrt2;
                    } else {
                        float dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > endRadius) dif = endRadius;
                        endCtrlPoint.y += dif;
                        endRadius *= sqrt2;
                    }
                }
            }

            float dist = (float) Math.sqrt((startCtrlPoint.x - endCtrlPoint.x) * (startCtrlPoint.x - endCtrlPoint.x) + (startCtrlPoint.y - endCtrlPoint.y) * (startCtrlPoint.y - endCtrlPoint.y));
            Point.Float vec = new Point.Float((endCtrlPoint.x - startCtrlPoint.x), (endCtrlPoint.y - startCtrlPoint.y));

            startCurvePoint = new Point.Float(startCtrlPoint.x, startCtrlPoint.y);
            startCurvePoint.x += vec.x * startRadius / dist;
            startCurvePoint.y += vec.y * startRadius / dist;
            endCurvePoint = new Point.Float(endCtrlPoint.x, endCtrlPoint.y);
            endCurvePoint.x -= vec.x * endRadius / dist;
            endCurvePoint.y -= vec.y * endRadius / dist;

            CubicCurve2D shape1 = new CubicCurve2D.Float();
            CubicCurve2D shape2 = new CubicCurve2D.Float();

            shape1.setCurve(startPoint, new Point((int) (startCtrlPoint.x + startPoint.x) / 2, (int) (startCtrlPoint.y + startPoint.y) / 2), new Point((int) (startCtrlPoint.x + startCurvePoint.x) / 2, (int) (startCtrlPoint.y + startCurvePoint.y) / 2), startCurvePoint);
            shape2.setCurve(endCurvePoint, endCtrlPoint, new Point.Float(endCtrlPoint.x, endPoint.y), endPoint);

            g.draw(shape1);
            g.draw(shape2);

//            //debugging
//            g.setColor(Color.green);
//            g.fillOval((int) endCtrlPoint.x, (int) endPoint.y, 2, 2);
//
//            g.setColor(Color.RED);
//            g.fillOval((int) startPoint.x, (int) startPoint.y, 2, 2);
//            g.fillOval((int) startCurvePoint.x, (int) startCurvePoint.y, 2, 2);
//            g.fillOval((int) endCurvePoint.x, (int) endCurvePoint.y, 2, 2);
//            g.fillOval((int) endPoint.x, (int) endPoint.y, 2, 2);
//            g.setColor(Color.black);

            g.drawLine((int) startCurvePoint.x, (int) startCurvePoint.y, (int) endCurvePoint.x, (int) endCurvePoint.y);
        } else if (endCtrlPoint != null) {
            startCtrlPoint = new Point.Float(startPoint.x, startPoint.y);
            startCtrlPoint.x += startRadius;

            if (startPoint.x > endPoint.x - startRadius - endRadius) {
                float sqrt2 = (float) Math.sqrt(2);

                if (Math.abs(startPoint.y - endPoint.y) < startRadius + endRadius) {
                    startRadius = startHeight;
                    startCtrlPoint.x = startPoint.x + startRadius;// / 2;

                    if (startPoint.y <= endPoint.y) startCtrlPoint.y -= startRadius;
                    else startCtrlPoint.y += startRadius;
                    startRadius *= sqrt2;
                } else {
                    if (startPoint.y < endPoint.y) {
                        float dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > startRadius) dif = startRadius;
                        startCtrlPoint.y += dif;

                        startRadius *= sqrt2;
                    } else {
                        float dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > startRadius) dif = startRadius;
                        startCtrlPoint.y -= dif;

                        startRadius *= sqrt2;
                    }
                }
            }

            float dist = (float) Math.sqrt((startCtrlPoint.x - endCtrlPoint.x) * (startCtrlPoint.x - endCtrlPoint.x) + (startCtrlPoint.y - endCtrlPoint.y) * (startCtrlPoint.y - endCtrlPoint.y));
            Point.Float vec = new Point.Float((endCtrlPoint.x - startCtrlPoint.x), (endCtrlPoint.y - startCtrlPoint.y));

            startCurvePoint = new Point.Float(startCtrlPoint.x, startCtrlPoint.y);
            startCurvePoint.x += vec.x * startRadius / dist;
            startCurvePoint.y += vec.y * startRadius / dist;
            endCurvePoint = new Point.Float(endCtrlPoint.x, endCtrlPoint.y);
            endCurvePoint.x -= vec.x * endRadius / dist;
            endCurvePoint.y -= vec.y * endRadius / dist;

            CubicCurve2D shape1 = new CubicCurve2D.Float();
            CubicCurve2D shape2 = new CubicCurve2D.Float();

            shape1.setCurve(startPoint, new Point.Float(startCtrlPoint.x, startPoint.y), startCtrlPoint, startCurvePoint);
            shape2.setCurve(endCurvePoint, new Point((int) (endCtrlPoint.x + endCurvePoint.x) / 2, (int) (endCtrlPoint.y + endCurvePoint.y) / 2), new Point((int) (endCtrlPoint.x + endPoint.x) / 2, (int) (endCtrlPoint.y + endPoint.y) / 2), endPoint);

            g.draw(shape1);
            g.draw(shape2);

//            //debugging
//            g.setColor(Color.green);
//            g.fillOval((int) startCtrlPoint.x, (int) startPoint.y, 2, 2);
//            g.fillOval((int) startCtrlPoint.x, (int) startCtrlPoint.y, 2, 2);
//            g.fillOval((int) endCtrlPoint.x, (int) endCtrlPoint.y, 2, 2);
//
//            g.setColor(Color.red);
//            g.fillOval((int) startPoint.x, (int) startPoint.y, 2, 2);
//            g.fillOval((int) startCurvePoint.x, (int) startCurvePoint.y, 2, 2);
//            g.fillOval((int) endCurvePoint.x, (int) endCurvePoint.y, 2, 2);
//            g.fillOval((int) endPoint.x, (int) endPoint.y, 2, 2);
//            g.setColor(Color.black);

            g.drawLine((int) startCurvePoint.x, (int) startCurvePoint.y, (int) endCurvePoint.x, (int) endCurvePoint.y);
        } else {
            startCtrlPoint = new Point.Float(startPoint.x, startPoint.y);
            startCtrlPoint.x += startRadius;
            endCtrlPoint = new Point.Float(endPoint.x, endPoint.y);
            endCtrlPoint.x -= endRadius;

            if (startPoint.x > endPoint.x - startRadius - endRadius) {
                float sqrt2 = (float) Math.sqrt(2);

                if (Math.abs(startPoint.y - endPoint.y) < startRadius + endRadius) {
                    startRadius = startHeight;
                    endRadius = endHeight;
                    startCtrlPoint.x = startPoint.x + startRadius;
                    endCtrlPoint.x = endPoint.x - endRadius;

                    if (startPoint.y <= endPoint.y) {
                        startCtrlPoint.y -= startRadius;
                        endCtrlPoint.y -= endRadius;
                        startRadius *= sqrt2;
                        endRadius *= sqrt2;
                    } else {
                        startCtrlPoint.y += startRadius;
                        endCtrlPoint.y += endRadius;
                        startRadius *= sqrt2;
                        endRadius *= sqrt2;
                    }
                } else {
                    if (startPoint.y < endPoint.y) {
                        float dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > startRadius) dif = startRadius;
                        startCtrlPoint.y += dif;

                        dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > endRadius) dif = endRadius;
                        endCtrlPoint.y -= dif;

                        startRadius *= sqrt2;
                        endRadius *= sqrt2;
                    } else {
                        float dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > startRadius) dif = startRadius;
                        startCtrlPoint.y -= dif;

                        dif = (startRadius + endRadius + startPoint.x - endPoint.x) / 4;
                        if (dif > endRadius) dif = endRadius;
                        endCtrlPoint.y += dif;

                        startRadius *= sqrt2;
                        endRadius *= sqrt2;
                    }
                }
            }

            float dist = (float) Math.sqrt((startCtrlPoint.x - endCtrlPoint.x) * (startCtrlPoint.x - endCtrlPoint.x) + (startCtrlPoint.y - endCtrlPoint.y) * (startCtrlPoint.y - endCtrlPoint.y));
            Point.Float vec = new Point.Float((endCtrlPoint.x - startCtrlPoint.x), (endCtrlPoint.y - startCtrlPoint.y));

            startCurvePoint = new Point.Float(startCtrlPoint.x, startCtrlPoint.y);
            startCurvePoint.x += vec.x * startRadius / dist;
            startCurvePoint.y += vec.y * startRadius / dist;
            endCurvePoint = new Point.Float(endCtrlPoint.x, endCtrlPoint.y);
            endCurvePoint.x -= vec.x * endRadius / dist;
            endCurvePoint.y -= vec.y * endRadius / dist;

            CubicCurve2D shape1 = new CubicCurve2D.Float();
            CubicCurve2D shape2 = new CubicCurve2D.Float();

            shape1.setCurve(startPoint, new Point.Float(startCtrlPoint.x, startPoint.y), startCtrlPoint, startCurvePoint);
            shape2.setCurve(endCurvePoint, endCtrlPoint, new Point.Float(endCtrlPoint.x, endPoint.y), endPoint);

            g.draw(shape1);
            g.draw(shape2);

//            //debugging
//            g.setColor(Color.green);
//            g.fillOval((int) startCtrlPoint.x, (int) startPoint.y, 2, 2);
//            g.fillOval((int) startCtrlPoint.x, (int) startCtrlPoint.y, 2, 2);
//            g.fillOval((int) endCtrlPoint.x, (int) endPoint.y, 2, 2);
//            g.fillOval((int) endCtrlPoint.x, (int) endCtrlPoint.y, 2, 2);
//
//            g.setColor(Color.RED);
//            g.fillOval((int) startPoint.x, (int) startPoint.y, 2, 2);
//            g.fillOval((int) startCurvePoint.x, (int) startCurvePoint.y, 2, 2);
//            g.fillOval((int) endCurvePoint.x, (int) endCurvePoint.y, 2, 2);
//            g.fillOval((int) endPoint.x, (int) endPoint.y, 2, 2);
//            g.setColor(Color.black);

            g.drawLine((int) startCurvePoint.x, (int) startCurvePoint.y, (int) endCurvePoint.x, (int) endCurvePoint.y);
        }
        return new Point.Float((2 * startCurvePoint.x + endCurvePoint.x) / 3, (2 * startCurvePoint.y + endCurvePoint.y) / 3);
    }

    private Point2D drawPath(GraphNode a, GraphNode b, Graphics2D g) {
        GraphNodeRenderingInfo renderInfoA = renderingInfos.get(a);
        GraphNodeRenderingInfo renderInfoB = renderingInfos.get(b);
        Point2D.Float startPoint = new Point2D.Float();
        Point2D.Float endPoint = new Point2D.Float();

        Point.Float startControl = null;
        Point.Float endControl = null;

        startPoint.setLocation(a.getX() + renderInfoA.width, a.getY());
        endPoint.setLocation(b.getX(), b.getY());

        if (renderInfoA.isCurve) {
            startPoint.x -= renderInfoA.width / 2;
            startControl = renderInfoA.startCtrlPoint;
        }
        if (renderInfoB.isCurve) {
            endPoint.x += +renderInfoB.width / 2;
            endControl = renderInfoB.endCtrlPoint;
        }
        Stroke str = g.getStroke();

        int radiusA = renderInfoA.height;
        int radiusB = renderInfoB.height;

        int clipRadiusForAssertion = 10;
        if (a.nodeType == GraphNode.NodeType.LookBehind || b.nodeType == GraphNode.NodeType.LookAhead || b.nodeType == GraphNode.NodeType.CrossReference) {
            //for assersions set dashed lines and the make the entry shape of the path look straight
            g.setStroke(new BasicStroke(((BasicStroke) str).getLineWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0));

            Area clip = new Area(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
            if (a.nodeType == GraphNode.NodeType.LookBehind) {
                endPoint = new Point2D.Float(b.getX(), b.getY());
            } else {
                if (b.nodeType == GraphNode.NodeType.CrossReference) {
                    startPoint = new Point2D.Float(a.getX() + renderInfoA.width / 2, a.getY());
                    endPoint = new Point2D.Float(b.getX() + renderInfoB.width / 2, b.getY());
                    radiusB = 0;
                    radiusA = 0;
                    clip.subtract(new Area(new Ellipse2D.Float(b.getX() - clipRadiusForAssertion, b.getY() - renderInfoB.height / 2 - clipRadiusForAssertion, renderInfoB.width + clipRadiusForAssertion * 2, renderInfoB.height + clipRadiusForAssertion * 2)));
                }
            }
            g.setClip(clip);
        } else
            g.setStroke(new BasicStroke(((BasicStroke) str).getLineWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));

        Point2D ret = drawPath(startPoint, radiusA, endPoint, radiusB, g, startControl, endControl);
        g.setClip(null);
        g.setStroke(str);
        //draw a small arrow if b is CrossRef
        if (b.nodeType == GraphNode.NodeType.CrossReference) {
            double d = Math.sqrt((startPoint.getX() - endPoint.getX()) * (startPoint.getX() - endPoint.getX()) + (startPoint.getY() - endPoint.getY()) * (startPoint.getY() - endPoint.getY()));
            Point2D p = new Point2D.Double(endPoint.getX() + (renderInfoB.width / 2 + clipRadiusForAssertion) * (startPoint.getX() - endPoint.getX()) / d, endPoint.getY() + (renderInfoB.width / 2 + clipRadiusForAssertion) * (startPoint.getY() - endPoint.getY()) / d);

            int arrowRadius = 5;
            Double theta = Math.atan2(p.getY() - startPoint.getY(), p.getX() - startPoint.getX()) + Math.PI;
            //draw the rotated arrow
            AffineTransform transform = g.getTransform();
            g.setColor(Color.black);
            g.translate(p.getX(), p.getY());
            g.rotate(theta + Math.PI);
            g.drawLine(0, 0, -arrowRadius, arrowRadius);
            g.drawLine(0, 0, -arrowRadius, -arrowRadius);

            g.setTransform(transform);
        }

        return ret;
    }

    private void drawPathOrderBox(PathOrderBox pathOrderBox, Color c, Graphics2D g2d) {
        g2d.setColor(c);
        g2d.fill(pathOrderBox.rect);
        g2d.setColor(Color.BLACK);
        g2d.draw(pathOrderBox.rect);
        g2d.drawString(Integer.toString(pathOrderBox.displayIndex), (int) pathOrderBox.rect.getX() + 5, (int) (pathOrderBox.rect.getY() + ((3 * pathOrderBox.rect.getHeight()) / 4)));
    }

    protected void copy() {
        if (selectedNodes.isEmpty())
            return;
        clipboard.clear();
        for (GraphNode gn : selectedNodes)
            clipboard.add(gn);
    }

    protected void cut() {
        copy();
        delete();
    }

    protected void paste() {
        CompoundEdit inserts = new CompoundEdit();

        SelectNodesCommand selectCommand = new SelectNodesCommand();
        selectCommand.editor = this;
        selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
        selectCommand.curentSelection = selectedNodes;
        selectedNodes.clear();
        Map<GraphNode, GraphNode> cloneOf = new HashMap<GraphNode, GraphNode>();

        for (GraphNode gn : clipboard) {
            Point loc = new Point(gn.getX(), gn.getY());
            boolean ok;
            do {
                ok = true;
                for (GraphNode node : currentGraph.getGraphNodes().values()) {
                    if (node.getX() == loc.x && node.getY() == loc.y) {
                        loc.x = loc.x - 10;
                        loc.y = loc.y - 10;
                        ok = false;
                    }
                }
            } while (!ok);

            InsertNodeCommand insertCommand = new InsertNodeCommand();
            GraphNode newGraphNode = gn.clone();
            for (String clause : renderingInfos.get(gn).matchingClauses)
                if (clause.startsWith(":")) {
                    GrammarEditor.theEditor.mustRefreshGraphsTree = true;
                    break;
                }

            cloneOf.put(gn, newGraphNode);
            newGraphNode.setX(loc.x);
            newGraphNode.setY(loc.y);
            GraphNodeRenderingInfo renderingInfo = new GraphNodeRenderingInfo(newGraphNode);
            renderingInfos.put(newGraphNode, renderingInfo);
            newGraphNode.setParentGraph(currentGraph);
            selectedNodes.add(newGraphNode);

            insertCommand.gn = newGraphNode;
            insertCommand.renderingInfos = renderingInfos;
            inserts.addEdit(insertCommand);
        }

        for (GraphNode gn : clipboard) {//create the path between the clones
            for (int index : gn.getChildNodesIndexes()) {
                if (clipboard.contains(gn.getParentGraph().getGraphNodes().get(index))) {
                    cloneOf.get(gn).getChildNodesIndexes().add(cloneOf.get(gn.getParentGraph().getGraphNodes().get(index)).getIndex());
                    CreatePathCommand pathEdit = new CreatePathCommand();
                    pathEdit.start = cloneOf.get(gn);
                    pathEdit.end = cloneOf.get(gn.getParentGraph().getGraphNodes().get(index));
                    inserts.addEdit(pathEdit);
                }
            }
        }

        selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
        inserts.addEdit(selectCommand);

        inserts.end();
        undoSupport.postEdit(inserts);
        updateUI();
    }

    protected void selectAll() {
        SelectNodesCommand selectCommand = new SelectNodesCommand();
        selectCommand.editor = this;
        selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
        selectCommand.curentSelection = selectedNodes;
        selectedNodes.clear();
        for (GraphNode gn : currentGraph.getGraphNodes().values()) {
            selectedNodes.add(gn);
        }
        selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
        undoSupport.postEdit(selectCommand);
    }

    public void deselect() {
        SelectNodesCommand selectCommand = new SelectNodesCommand();
        selectCommand.editor = this;
        selectCommand.curentSelection = selectedNodes;
        selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
        selectedNodes.clear();
        selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
        selectCommand.register(undoSupport);
        updateUI();
    }

    List<MoveNodeCommand> nudgingEdits;
    BitSet nudging;

    @Override
    public void mouseWheelMoved(MouseWheelEvent mouseWheelEvent) {
        double scale = g2dTransform.getScaleX() - mouseWheelEvent.getWheelRotation() / 3d * zoomAmmount;
        if (scale <= zoomAmmount || scale >= 8)
            return;
        Point2D point1 = null;
        Point2D point2 = null;
        try {
            point1 = g2dTransform.inverseTransform(new Point2D.Double(mouseWheelEvent.getX(), mouseWheelEvent.getY()), null);
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }

        //g2dTransform.translate(point0.getX() - point1.getX(), point0.getY() - point1.getY());
        g2dTransform.setTransform(new AffineTransform(1, 0, 0, 1, g2dTransform.getTranslateX(), g2dTransform.getTranslateY()));
        g2dTransform.scale(scale, scale);
        try {
            point2 = g2dTransform.inverseTransform(new Point2D.Double(mouseWheelEvent.getX(), mouseWheelEvent.getY()), null);
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }
        g2dTransform.translate((point2.getX() - point1.getX()), (point2.getY() - point1.getY()));
        updateUI();
    }

    private class MyDispatcher implements KeyEventDispatcher {
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_CONTROL) {
                isControlDown = true;
                Point2D p = getMousePosition();
                if (p != null) {
                    try {
                        g2dTransform.inverseTransform(p, p);
                    } catch (NoninvertibleTransformException e1) {
                        e1.printStackTrace();
                    }

                    boolean changeCursor = false;
                    for (GraphNode gn : currentGraph.getGraphNodes().values()) {
                        GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
                        if (renderInfo == null || renderInfo.isEmpty) continue;
                        for (int i = 0; i < renderInfo.clausesBoxes.size(); i++) {
                            if (!renderInfo.matchingClauses.get(i).startsWith(":")) continue;
                            Rectangle2D r = renderInfo.clausesBoxes.get(i);
                            if (r.contains(p)) {
                                boldClauseRectangle = r;
                                changeCursor = true;
                                boldClauseName = renderInfo.matchingClauses.get(i).substring(1);
                                setCursor(handCursor);
                                updateUI();
                                break;
                            }
                        }
                        if (changeCursor) break;
                    }
                    if (!changeCursor) {
                        boldClauseRectangle = null;
                        boldClauseName = null;
                        setCursor(defaultCursor);
                        updateUI();
                    }

                    updateUI();
                }
            } else if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                hideEditNodeCancel();
                updateUI();
            } else if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_CONTROL) {

                isControlDown = false;
                setCursor(defaultCursor);
                updateUI();
            } else if (currentJsEditor == null && !pathing && !moving && selectedPathOrderBox == null && e.getID() == KeyEvent.KEY_PRESSED && (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT)) {
                if (nudging == null) nudging = new BitSet();
                if (nudging.isEmpty()) {

                    if (nudgingEdits == null)
                        nudgingEdits = new ArrayList<MoveNodeCommand>();
                    for (GraphNode gn : selectedNodes) {
                        MoveNodeCommand moveCommand = new MoveNodeCommand(gn, renderingInfos.get(gn));
                        moveCommand.initPos = new Point(gn.getX(), gn.getY());
                        nudgingEdits.add(moveCommand);
                    }
                }

                nudging.set(e.getKeyCode());
                Point vec = new Point();
                if (nudging.get(KeyEvent.VK_LEFT))
                    vec.x += -1;
                if (nudging.get(KeyEvent.VK_RIGHT))
                    vec.x += 1;
                if (nudging.get(KeyEvent.VK_UP))
                    vec.y += -1;
                if (nudging.get(KeyEvent.VK_DOWN))
                    vec.y += 1;

                for (GraphNode gn : selectedNodes) {
                    gn.setX(gn.getX() + vec.x);
                    gn.setY(gn.getY() + vec.y);
                    renderingInfos.get(gn).formatDirty = true;
                }
                updateUI();
            } else if (currentJsEditor == null && !pathing && !moving && selectedPathOrderBox == null && e.getID() == KeyEvent.KEY_RELEASED && (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT)) {
                //nudging
                nudging.clear(e.getKeyCode());
                if (nudging.isEmpty()) {
                    CompoundEdit edit = new CompoundEdit();
                    for (int i = 0; i < selectedNodes.size(); i++) {
                        nudgingEdits.get(i).newPos = new Point(selectedNodes.get(i).getX(), selectedNodes.get(i).getY());
                        edit.addEdit(nudgingEdits.get(i));
                    }
                    edit.end();
                    undoSupport.postEdit(edit);
                    nudgingEdits.clear();
                }
            } else if (currentJsEditor == null && !pathing && !moving && selectedPathOrderBox == null && e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_BACK_SPACE){
                //delete also on backspace - important on mac
                delete();
            }

            return false;
        }
    }

    protected void delete() {
        CompoundEdit deletes = new CompoundEdit();

        for (GraphNode selNode : selectedNodes) {
            if (!selNode.isStartNode() && !selNode.isEndNode()) {
                //sterg nodul
                for (GraphNode graphNode : currentGraph.getGraphNodes().values()) {//sterg toate pathurile spre el

                    if (!graphNode.getChildNodesIndexes().contains(selNode.getIndex())) continue;

                    DeletePathCommand deletePathCommand = new DeletePathCommand();
                    deletePathCommand.start = graphNode;
                    deletePathCommand.end = selNode;
                    deletePathCommand.priorityIndex = graphNode.getChildNodesIndexes().indexOf(selNode.getIndex());

                    graphNode.getChildNodesIndexes().remove(deletePathCommand.priorityIndex);
                    deletes.addEdit(deletePathCommand);
                }

                for (String clause : renderingInfos.get(selNode).matchingClauses)
                    if (clause.startsWith(":")) {
                        GrammarEditor.theEditor.mustRefreshGraphsTree = true;
                        break;
                    }

                selNode.getParentGraph().getGraphNodes().remove(selNode.getIndex());

                DeleteNodeCommand deleteCommand = new DeleteNodeCommand();
                deleteCommand.gn = selNode;
                deletes.addEdit(deleteCommand);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        SelectNodesCommand selectCommand = new SelectNodesCommand();
        selectCommand.editor = this;
        selectCommand.curentSelection = selectedNodes;
        selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
        selectedNodes.clear();
        selectCommand.nextSelection = new ArrayList<GraphNode>();
        deletes.addEdit(selectCommand);

        deletes.end();
        undoSupport.postEdit(deletes);

        updateUI();
    }

    private boolean isControlDown;

    private boolean moving = false;
    private boolean selecting = false;
    private boolean pathing = false;
    private Point2D.Float pressPoint;
    private int pressButton;
    private Rectangle selectRectangle = new Rectangle();

    public void mouseClicked(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
        if (g2dTransform == null)
            return; //means things have not ben init yet
        Point2D.Float p = null;
        try {
            p = (Point2D.Float) g2dTransform.inverseTransform(e.getPoint(), null);
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }
        grabFocus();
        wasDragged = false;
        pressPoint = p;
        lastDragPoint = e.getPoint();
        pressButton = e.getButton();
        if (e.getButton() != MouseEvent.BUTTON2) hideEditNode();
        else
            setCursor(pannCursor);
    }


    List<Point> initMovePositions = new ArrayList<Point>();

    protected GraphNode insertNode(int x, int y) {

        CompoundEdit compCommand = new CompoundEdit();

        InsertNodeCommand insertCommand = new InsertNodeCommand();
        GraphNode newGraphNode = new GraphNode(currentGraph.grammar);
        newGraphNode.setTokenMatchingCode("<<E>>");
        x = x - 9;
        boolean ok;
        do {
            ok = true;
            for (GraphNode node : currentGraph.getGraphNodes().values()) {
                if (node.getX() == x && node.getY() == y) {
                    x = x - 10;
                    y = y - 10;
                    ok = false;
                }
            }
        } while (!ok);

        newGraphNode.setX(x);
        newGraphNode.setY(y);
        newGraphNode.setParentGraph(currentGraph);
        renderingInfos.put(newGraphNode, new GraphNodeRenderingInfo(newGraphNode));
        insertCommand.gn = newGraphNode;
        insertCommand.renderingInfos = renderingInfos;
        compCommand.addEdit(insertCommand);

        SelectNodesCommand selectCommand = new SelectNodesCommand();
        selectCommand.editor = this;
        selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
        selectCommand.curentSelection = selectedNodes;
        selectedNodes.clear();
        selectedNodes.add(newGraphNode);
        selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
        compCommand.addEdit(selectCommand);

        compCommand.end();
        undoSupport.postEdit(compCommand);
        return newGraphNode;
    }

    public void mouseReleased(MouseEvent e) {
        Point2D.Float p = null;
        try {
            p = (Point2D.Float) g2dTransform.inverseTransform(e.getPoint(), null);
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }
        if (!wasDragged) {
            if (e.getClickCount() == 1 && !e.isConsumed() && pressButton == MouseEvent.BUTTON1) {
                e.consume();
                //s-a facut un click obisnuit

                for (GraphNode gn : currentGraph.getGraphNodes().values()) {
                    GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
                    if (renderInfo.jsToggle != null && renderInfo.jsToggle.contains(p)) {
                        gn.setJsMinimized(!gn.isJsMinimized());
                        updateUI();
                        return;
                    }
                }
                if (e.isControlDown()) {
                    if (boldClauseRectangle != null) {
                        int aux = boldClauseName.indexOf('(');
                        if (aux != -1)
                            GrammarEditor.theEditor.selectChildGraph(boldClauseName.substring(0, aux));
                        else
                            GrammarEditor.theEditor.selectChildGraph(boldClauseName);
                    }
                    return;
                }


                SelectNodesCommand selectCommand = new SelectNodesCommand();
                selectCommand.editor = this;
                selectCommand.curentSelection = selectedNodes;
                selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);

                if (!e.isAltDown() && !e.isShiftDown()) {
                    selectedNodes.clear();
                }
                GraphNode gn = select(p);
                if (gn != null) {
                    if (e.isAltDown() || selectedNodes.contains(gn)) {
                        selectedNodes.remove(gn);
                    } else {
                        if (!selectedNodes.contains(gn))
                            selectedNodes.add(gn);
                    }
                }

                selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
                selectCommand.register(undoSupport);

            } else if (e.getClickCount() == 2 && !e.isConsumed() && e.getButton() == MouseEvent.BUTTON1) {
                e.consume();
                //s-a facut un dublu click
                //daca e pe un jsBullet sa nu faca nimic
                for (GraphNode gn : currentGraph.getGraphNodes().values()) {
                    GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
                    if (renderInfo.jsToggle != null && renderInfo.jsToggle.contains(p)) {
                        return;
                    }
                }

                GraphNode auxNode = select(p);
                if (auxNode == null) {
                    insertNode((int) p.getX(), (int) p.getY());
                } else {
                    //select only the double clicked node
                    SelectNodesCommand selectCommand = new SelectNodesCommand();
                    selectCommand.editor = this;
                    selectCommand.curentSelection = selectedNodes;
                    selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
                    selectedNodes.clear();
                    selectedNodes.add(auxNode);
                    selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
                    selectCommand.register(undoSupport);

                    //show inline editing
                    showEditNode(selectedNodes.get(0));
                }
            }
            updateUI();
        } else if (selectedPathOrderBox != null) {
            if (swapPathOrderBox != null) {
                SwapPathPriorityCommand swapPathPriorityCommand = new SwapPathPriorityCommand();
                swapPathPriorityCommand.gn = selectedPathOrderBox.parentGraphNode;
                swapPathPriorityCommand.index1 = selectedPathOrderBox.index;
                swapPathPriorityCommand.index2 = swapPathOrderBox.index;

                Collections.swap(selectedPathOrderBox.parentGraphNode.getChildNodesIndexes(), swapPathPriorityCommand.index1, swapPathPriorityCommand.index2);
                undoSupport.postEdit(swapPathPriorityCommand);
            }
            selectedPathOrderBox = null;
            updateUI();
        } else if (selecting) {
            SelectNodesCommand selectCommand = new SelectNodesCommand();
            selectCommand.editor = this;
            selectCommand.curentSelection = selectedNodes;
            selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
            List<GraphNode> sel = select(selectRectangle);
            if (!e.isShiftDown()) {
                if (e.isAltDown()) {
                    selectedNodes.removeAll(sel);
                } else {
                    selectedNodes.clear();
                    selectedNodes.addAll(sel);
                }
            } else {
                for (GraphNode gn : sel) {
                    if (!selectedNodes.contains(gn)) {
                        selectedNodes.add(gn);
                    }
                }
            }
            selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
            selectCommand.register(undoSupport);
            selecting = false;
            updateUI();
        } else if (pathing) {
            pathing = false;
            if (pathingTo != null) {
                CompoundEdit pathings = new CompoundEdit();
                for (GraphNode sel : selectedNodes) {
                    if (sel.isEndNode()) continue;
                    if (sel.nodeType == GraphNode.NodeType.Comment) continue;
                    if (sel.getChildNodesIndexes().contains(pathingTo.getIndex())) {
                        DeletePathCommand deletePathCommand = new DeletePathCommand();
                        deletePathCommand.start = sel;
                        deletePathCommand.end = pathingTo;
                        deletePathCommand.priorityIndex = sel.getChildNodesIndexes().indexOf(pathingTo.getIndex());
                        sel.getChildNodesIndexes().remove(deletePathCommand.priorityIndex);
                        pathings.addEdit(deletePathCommand);
                    } else {
                        sel.getChildNodesIndexes().add(pathingTo.getIndex());
                        CreatePathCommand createPathCommand = new CreatePathCommand();
                        createPathCommand.start = sel;
                        createPathCommand.end = pathingTo;
                        pathings.addEdit(createPathCommand);
                    }
                }
                pathings.end();
                undoSupport.postEdit(pathings);
                pathingTo = null;
            }
            updateUI();
        } else if (moving) {
            moving = false;
            CompoundEdit moves = new CompoundEdit();
            for (int i = 0; i < selectedNodes.size(); i++) {
                MoveNodeCommand mc = new MoveNodeCommand(selectedNodes.get(i), renderingInfos.get(selectedNodes.get(i)));
                mc.initPos = initMovePositions.get(i);
                mc.newPos = new Point(selectedNodes.get(i).getX(), selectedNodes.get(i).getY());
                moves.addEdit(mc);
            }
            moves.end();
            undoSupport.postEdit(moves);
        } else if (e.getButton() == MouseEvent.BUTTON2) {
            setCursor(defaultCursor);
        }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public boolean wasDragged = false;
    public PathOrderBox selectedPathOrderBox = null;
    private Point lastDragPoint;

    public void mouseDragged(MouseEvent e) {
        Point2D.Float p = null;
        try {
            p = (Point2D.Float) g2dTransform.inverseTransform(e.getPoint(), null);
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }
        if (pressButton != MouseEvent.BUTTON2) {
            if (!wasDragged) {//dragging starts
                wasDragged = true;

                for (PathOrderBox pathOrderBox : pathOrderBoxes) {
                    if (pathOrderBox.rect.contains(pressPoint)) {
                        selectedPathOrderBox = pathOrderBox;
                        draggedPathOrderBox = new PathOrderBox(pathOrderBox);
                        break;
                    }
                }

                if (selectedPathOrderBox == null) {
                    GraphNode gn = select(pressPoint);

                    if (gn != null && !e.isShiftDown() && !e.isAltDown()) {
                        //if gn is overlaped with an already selected node, don't modify the selection
                        for (GraphNode sel : selectedNodes) {
                            GraphNodeRenderingInfo renderInfo = renderingInfos.get(sel);
                            if (new Rectangle(sel.getX(), sel.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height).contains(pressPoint)) {
                                gn = sel;
                                break;
                            }
                        }

                        if (selectedNodes.contains(gn)) {
                            if (pressButton == MouseEvent.BUTTON3) {
                                pathing = true;
                            } else if (pressButton == MouseEvent.BUTTON1) {
                                moving = true;
                            }
                        } else {
                            SelectNodesCommand selectCommand = new SelectNodesCommand();
                            selectCommand.editor = this;
                            selectCommand.curentSelection = selectedNodes;
                            selectCommand.prevSelection = new ArrayList<GraphNode>(selectedNodes);
                            if (pressButton == MouseEvent.BUTTON3) {
                                selectedNodes.clear();
                                selectedNodes.add(gn);
                                pathing = true;
                            } else if (pressButton == MouseEvent.BUTTON1) {
                                selectedNodes.clear();
                                selectedNodes.add(gn);
                                moving = true;
                            }
                            selectCommand.nextSelection = new ArrayList<GraphNode>(selectedNodes);
                            selectCommand.register(undoSupport);
                        }
                    } else {
                        selecting = true;
                    }
                }
                if (moving) {
                    initMovePositions.clear();
                    for (GraphNode sel : selectedNodes) {
                        initMovePositions.add(new Point(sel.getX(), sel.getY()));
                    }
                }
            }

            if (selectedPathOrderBox != null) {
                draggedPathOrderBox.rect.setFrame(p.getX() - draggedPathOrderBox.rect.getWidth(), p.getY() - draggedPathOrderBox.rect.getHeight(), selectedPathOrderBox.rect.getWidth(), selectedPathOrderBox.rect.getHeight());
                updateUI();
            } else if (selecting) {
                int x = (int) pressPoint.getX();
                if (x > p.getX()) {
                    x = (int) p.getX();
                }
                int y = (int) pressPoint.getY();
                if (y > p.getY()) {
                    y = (int) p.getY();
                }

                selectRectangle.setBounds(x, y, (int) Math.abs(p.getX() - pressPoint.x), (int) Math.abs(p.getY() - pressPoint.y));
                updateUI();
            } else if (moving) {

                for (int i = 0; i < selectedNodes.size(); i++) {
                    GraphNode gn = selectedNodes.get(i);
                    gn.setX((int) (initMovePositions.get(i).getX() + p.getX() - pressPoint.getX()));
                    gn.setY((int) (initMovePositions.get(i).getY() + p.getY() - pressPoint.getY()));
                    renderingInfos.get(gn).formatDirty = true;
                }
                //pressPoint = p;
                updateUI();
            } else if (pathing) {
                pressPoint = p;
                updateUI();
            }
        } else {//panning
            //g2dTransform.translate(g2dTransform.getTranslateX() - (e.getX() - pressPoint.getX()), g2dTransform.getTranslateY() - (e.getY() - pressPoint.getY()));
            g2dTransform.translate((e.getX() - lastDragPoint.getX()) / g2dTransform.getScaleX(), (e.getY() - lastDragPoint.getY()) / g2dTransform.getScaleY());
            lastDragPoint.setLocation(e.getX(), e.getY());
            if (currentJsEditor != null) {
                Point2D transform = g2dTransform.transform(new Point(selectedNodes.get(0).getX(), selectedNodes.get(0).getY() - renderingInfos.get(currentJsEditor.node).height / 2), null);
                currentJsEditor.setLocation(new Point((int) transform.getX(), (int) transform.getY()));
            }
            updateUI();
        }
    }

    Rectangle2D boldClauseRectangle = null;
    String boldClauseName = null;
    Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
    Cursor pannCursor = new Cursor(Cursor.MOVE_CURSOR);
    Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);

    public void mouseMoved(MouseEvent e) {
        if (currentGraph == null)
            return;
        Point2D p = null;
        try {
            p = g2dTransform.inverseTransform(e.getPoint(), null);
        } catch (NoninvertibleTransformException e1) {
            e1.printStackTrace();
        }

        boolean overJsToggle = false;
        for (GraphNode gn : currentGraph.getGraphNodes().values()) {
            GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
            if (renderInfo.jsToggle == null) continue;
            if (renderInfo.jsToggle.contains(p)) {
                setCursor(handCursor);
                overJsToggle = true;
            }
        }

        if (e.isControlDown()) {
            for (GraphNode gn : currentGraph.getGraphNodes().values()) {

                GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
                if (renderInfo == null || renderInfo.isEmpty) continue;
                for (int i = 0; i < renderInfo.clausesBoxes.size(); i++) {
                    if (!renderInfo.matchingClauses.get(i).startsWith(":")) continue;
                    Rectangle2D r = renderInfo.clausesBoxes.get(i);
                    if (r.contains(p)) {
                        boldClauseRectangle = r;
                        boldClauseName = renderInfo.matchingClauses.get(i).substring(1);
                        setCursor(handCursor);
                        updateUI();
                        return;
                    }
                }
            }
            if (boldClauseRectangle != null) {
                boldClauseRectangle = null;
                boldClauseName = null;
                setCursor(defaultCursor);
                updateUI();
            }
        }

        if (!overJsToggle)
            setCursor(defaultCursor);
    }

    private void showEditNode(GraphNode graphNode) {
        GraphNodeRenderingInfo rInfo = renderingInfos.get(selectedNodes.get(0));
        currentJsEditor = rInfo.jsEditorComponent;
        Point2D transform = g2dTransform.transform(new Point(selectedNodes.get(0).getX(), selectedNodes.get(0).getY() - rInfo.height / 2), null);
        currentJsEditor.setLocation(new Point((int) transform.getX(), (int) transform.getY()));
        currentJsEditor.jsEditor.setText(graphNode.getJsCode());
        currentJsEditor.mcEditor.setText(graphNode.getTokenMatchingCode());
        currentJsEditor.ocEditor.setText(graphNode.getOutputCode());
        this.add(currentJsEditor);
        this.invalidate();
    }

    public void hideEditNode() {
        if (currentJsEditor != null) {
            remove(currentJsEditor);
            currentJsEditor.updateNode();
            currentJsEditor = null;
        }
    }

    public void hideEditNodeCancel() {
        if (currentJsEditor != null) {
            remove(currentJsEditor);
            currentJsEditor = null;
        }
    }

    private GraphNode select(Point2D click) {
        GraphNode rez = null;
        for (GraphNode gn : currentGraph.getGraphNodes().values()) {
            GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
            if (new Rectangle(gn.getX(), gn.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height).contains(click)) {
                rez = gn;
            }
        }
        return rez;
    }

    private List<GraphNode> select(Rectangle rec) {
        List<GraphNode> rez = new ArrayList<GraphNode>();

        for (GraphNode gn : currentGraph.getGraphNodes().values()) {
            GraphNodeRenderingInfo renderInfo = renderingInfos.get(gn);
            if (new Rectangle(gn.getX(), gn.getY() - renderInfo.height / 2, renderInfo.width, renderInfo.height).intersects(rec)) {
                rez.add(gn);
            }
        }
        return rez;
    }
}
