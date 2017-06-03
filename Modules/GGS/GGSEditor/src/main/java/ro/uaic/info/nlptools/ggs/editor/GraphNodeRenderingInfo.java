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

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

class GraphNodeRenderingInfo {
    public int width;
    public int height;
    public NodeEditorComponent jsEditorComponent;

    public boolean isEmpty;

    private GraphNode graphNode;
    public List<Rectangle2D> clausesBoxes = new ArrayList<Rectangle2D>();
    public List<String> matchingClauses;
    public List<String> matchingClausesWithoutMacros;
    public Rectangle jsToggle = null;
    public boolean isCurve = false;

    public boolean formatDirty = true;

    private Pattern clauseSplitter = Pattern.compile("\\s*([\\n\\r]\\s*)+");
    public Point.Float startCtrlPoint;
    public Point.Float endCtrlPoint;

    public GraphNodeRenderingInfo(GraphNode g) {
        graphNode = g;
        jsEditorComponent = new NodeEditorComponent(g);

        if (g.isEndNode()) {
            isEmpty = true;
            width = 36;
            height = 36;
        } else if (g.isStartNode()) {
            width = 36;
            height = 36;
            isEmpty = true;
        } else {
            //sunt setate de catre GrammarEditorComponent
        }

        matchingClauses = new ArrayList<String>(Arrays.asList(clauseSplitter.split(graphNode.getTokenMatchingCode())));
    }

    public void reformatIfDirty(Graphics g) {
        if (!formatDirty) return;

        jsToggle = null;

        if (graphNode.isStartNode() || graphNode.isEndNode()) {
            if (graphNode.getJsCode() != null)
                jsToggle = new Rectangle(graphNode.getX() + width + 4, graphNode.getY() - 4, 8, 8);
            return;
        }


        if (graphNode.isEmpty()) {
            isEmpty = true;
            width = 18;
            height = 18;
        } else if (graphNode.nodeType == GraphNode.NodeType.AssertionEnd) {
            isEmpty = true;
            width = 18;
            height = 24;
        } else if (graphNode.isAssertion()) {
            isEmpty = true;
            width = height = 24;
        } else {
            isEmpty = false;

            List<String> prevMatchingClauses = new ArrayList<String>(matchingClauses);
            matchingClauses = new ArrayList<String>(Arrays.asList(clauseSplitter.split(graphNode.getTokenMatchingCode())));

            FontMetrics metrics = g.getFontMetrics();
            int hgt = metrics.getHeight();
            int marginsY = 1;
            int marginsX = 5;

            clausesBoxes.clear();
            width = 0;
            height = matchingClauses.size() * (hgt + marginsY * 2);
            int adv;
            for (int i = 0; i < matchingClauses.size(); i++) {
                String clause = matchingClauses.get(i);
                if (i == 0 && graphNode.nodeType == GraphNode.NodeType.Comment)
                    clause = clause.substring(2);
                adv = metrics.stringWidth(clause);
                if (adv > width) {
                    width = adv;
                }
                clausesBoxes.add(new Rectangle(graphNode.getX() + marginsX, graphNode.getY() - height / 2 + i * (hgt + marginsY * 2) + marginsY, adv, hgt));
            }
            width += marginsX * 2;

            if (!GrammarEditor.theEditor.mustRefreshGraphsTree) //if a jump node was edited, refresh the grammr tree
                if (matchingClauses.size() != prevMatchingClauses.size()) {
                    for (String clause : matchingClauses) {
                        if (clause.startsWith(":")) {
                            GrammarEditor.theEditor.mustRefreshGraphsTree = true;
                            break;
                        }
                    }
                    if (!GrammarEditor.theEditor.mustRefreshGraphsTree)
                        for (String clause : prevMatchingClauses) {
                            if (clause.startsWith(":")) {
                                GrammarEditor.theEditor.mustRefreshGraphsTree = true;
                                break;
                            }
                        }
                } else
                    for (int i = 0; i < matchingClauses.size(); i++) {
                        String clause = matchingClauses.get(i);
                        String prevClause = prevMatchingClauses.get(i);
                        if ((clause.startsWith(":") || prevClause.startsWith(":")) && !clause.equals(prevClause)) {
                            GrammarEditor.theEditor.mustRefreshGraphsTree = true;
                            break;
                        }
                    }
        }

        if (graphNode.getJsCode() != null)
            jsToggle = new Rectangle(graphNode.getX() + width + 4, graphNode.getY() - 4, 8, 8);

        formatDirty = false;
    }
}
