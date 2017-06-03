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

package ro.uaic.info.nlptools.ggs.engine.grammar;

import ro.uaic.info.nlptools.ggs.engine.core.GGSMacroNotFoundException;
import javafx.util.Pair;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphNode implements Cloneable {
    private String tokenMatchingCode = "<<E>>";
    private String outputCode = null;
    protected String jsCode = null;
    protected boolean jsMinimized = false;
    private boolean findLongestMatch;

    public boolean isEnd = false;
    public boolean isStart = false;
    public NodeType nodeType;
    public boolean isNegativeAssertion = false;
    int index = -1;
    protected List<Integer> childNodesIndexes;
    Graph parentGraph;

    protected int X;
    protected int Y;

    public Grammar grammar;

    public boolean isAssertion() {
        return nodeType == NodeType.CrossReference.CrossReference || nodeType == NodeType.CrossReference.LookAhead || nodeType == NodeType.CrossReference.LookBehind;
    }

    public boolean isComment() {
        return nodeType == NodeType.Comment;
    }

    public boolean isEmpty() {
        return tokenMatchingCode.equals("<E>") || tokenMatchingCode.equals("<<E>>");
    }
    //protected boolean findLongestMatch = false;

    public enum NodeType {LookAhead, LookBehind, CrossReference, Normal, AssertionEnd, Comment}

    ;

    public static Pattern jsVarsPattern = Pattern.compile("\\$[^@\\$]+\\$");
    static Pattern macroPattern = Pattern.compile("@[A-Za-z0-9_]+@");
    public static Pattern macroOrJsVarPattern = Pattern.compile("(" + jsVarsPattern + ")|(" + macroPattern + ")");
    public static Pattern clauseSplittingRegex = Pattern.compile("\\s*(([\\n\\r]+)|((?<=>)\\s*\\|\\s*(?=<)))\\s*");
    public static Pattern tokenMatchingCodeValidatorPattern = Pattern.compile(
            "(<\\s*([_\\p{L}][\\p{L}\\p{N}\\-_.]*)?(\\s*([+-]\\s*((?:[^+\\-=/]+)|(?:/[^/]+?/i?)))=(?:(?:[^+\\-=/]*)|(?:/[^/]+?/i?)))*>)" +
                    "|(:[\\p{L}\\p{N}_]+(\\(.*\\))?)" + //jump with parameters
                    "|(<E>)" +
                    "|(<X>)" +
                    "|(<<E>>)" +
                    "|(<<X>>)" +
                    "|(=>)" + //positive token search
                    "|(!=>)" + //negative token search
                    "|(\\?=)" + //positive lookahead
                    "|(\\?!)" + //negative lookahead
                    "|(\\?<=)" + //positive lookbehind
                    "|(\\?<!)", //negative lookbehind
            Pattern.COMMENTS);
    public static Pattern tokenMatchingCodeParserPattern = Pattern.compile("(?:[+-]\\s*((?:[^+\\-=/]+)|(?:/[^/]+?/i?)))=(?:(?:[^+\\-=/>]+)|(?:/[^/]+/i?))");

    public static Pattern outputCodeValidatorPattern = Pattern.compile("(<[\\p{L}\\p{N}_]+(\\s+\\+[\\p{L}\\p{N}_]+=\".*?\")*>?)   |" +
            "(>)   |" +
            "(((\\+[\\p{L}\\p{N}_]+=\".*?\")|(\\s+-[\\p{L}\\p{N}_]+))  ((\\s+\\+[\\p{L}\\p{N}_]+=\".*?\")|(\\s+-[\\p{L}\\p{N}_]+))*>?)", Pattern.COMMENTS);

    public static Pattern outputCodeParserPattern = Pattern.compile("(?:<[\\p{L}\\p{N}_]*) | (?:\\+([\\p{L}\\p{N}_]+)=\"(.*?)\") | (?:-[\\p{L}\\p{N}_]+) | (>)", Pattern.COMMENTS);

    public GraphNode(Grammar g) {
        childNodesIndexes = new ArrayList<Integer>();
        grammar = g;

    }

    public GraphNode(Node GraphNodeElem, Graph parent) {
        grammar = parent.grammar;
        parentGraph = parent;
        index = Integer.parseInt(GraphNodeElem.getAttributes().getNamedItem("ID").getNodeValue());

        if (GraphNodeElem.getAttributes().getNamedItem("isEndNode") != null && GraphNodeElem.getAttributes().getNamedItem("isEndNode").getNodeValue().toLowerCase().equals("true")) {
            isEnd = true;
            parentGraph.endNode = this;
        }

        if (GraphNodeElem.getAttributes().getNamedItem("isStartNode") != null && GraphNodeElem.getAttributes().getNamedItem("isStartNode").getNodeValue().toLowerCase().equals("true")) {
            isStart = true;
            parentGraph.startNode = this;
        }

        if (GraphNodeElem.getAttributes().getNamedItem("priorityPolicy") != null && GraphNodeElem.getAttributes().getNamedItem("priorityPolicy").getNodeValue().toLowerCase().equals("custom")) {
            findLongestMatch = false;
        }


        setTokenMatchingCode(((Element) GraphNodeElem).getElementsByTagName("MatchingPatternCode").item(0).getTextContent());

        childNodesIndexes = new ArrayList<Integer>();

        if (nodeType != NodeType.Comment) {
            NodeList nodeList = ((Element) GraphNodeElem).getElementsByTagName("Child");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node childNode = nodeList.item(i);
                childNodesIndexes.add(Integer.parseInt(childNode.getFirstChild().getNodeValue()));
            }


            nodeList = ((Element) GraphNodeElem).getElementsByTagName("AnnotationCode");
            if (nodeList.getLength() > 0) {
                outputCode = nodeList.item(0).getTextContent().trim();
                if (outputCode.isEmpty()) outputCode = null;
            }

            nodeList = ((Element) GraphNodeElem).getElementsByTagName("JScode");
            if (nodeList.getLength() > 0) {
                jsCode = nodeList.item(0).getTextContent().trim();
                if (jsCode.isEmpty()) jsCode = null;
                if (((Element) nodeList.item(0)).getAttribute("minimized").equals("true"))
                    jsMinimized = true;
            }
        }
        X = Integer.parseInt(GraphNodeElem.getAttributes().getNamedItem("X").getNodeValue());
        Y = Integer.parseInt(GraphNodeElem.getAttributes().getNamedItem("Y").getNodeValue());
    }

    public GraphNode clone() {

        GraphNode newGraphNode = new GraphNode(grammar);
        newGraphNode.setTokenMatchingCode(getTokenMatchingCode());
        newGraphNode.setOutputCode(getOutputCode());
        newGraphNode.setJsCode(getJsCode());
        //newGraphNode.setFindLongestMatch(isFindLongestMatch());
        newGraphNode.setJsMinimized(isJsMinimized());

        return newGraphNode;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (parentGraph != null)
            sb.append(parentGraph.getId()).append("#").append(index);
        sb.append(" [").append(tokenMatchingCode.replaceAll("[\r\n]+", " | ")).append("]");


        if (outputCode != null) {
            sb.append(" / ").append(outputCode);
        }
        return sb.toString();
    }

    public String getTokenMatchingCode() {
        return tokenMatchingCode;
    }

    public void setTokenMatchingCode(String tokenMatchingCode) {
        this.tokenMatchingCode = tokenMatchingCode;
        if (this.tokenMatchingCode.isEmpty()) {
            this.tokenMatchingCode = "<<E>>";
        }

        nodeType = NodeType.Normal;
        isNegativeAssertion = false;
        if (tokenMatchingCode.trim().startsWith("//"))
            nodeType = NodeType.Comment;
        else if (tokenMatchingCode.matches("(\\?<=)|(\\?<!)")) {
            nodeType = NodeType.LookBehind;
            if (tokenMatchingCode.endsWith("!"))
                isNegativeAssertion = true;
        } else if (tokenMatchingCode.matches("(\\?=)|(\\?!)")) {
            nodeType = NodeType.LookAhead;
            if (tokenMatchingCode.endsWith("!"))
                isNegativeAssertion = true;
        } else if (tokenMatchingCode.matches("!?=>")) {
            nodeType = NodeType.CrossReference;
            if (tokenMatchingCode.startsWith("!"))
                isNegativeAssertion = true;
        } else if (tokenMatchingCode.equals("<<X>>") || tokenMatchingCode.equals("<X>"))
            nodeType = NodeType.AssertionEnd;
    }

    public String getOutputCode() {
        return outputCode;
    }

    public void setOutputCode(String outputCode) {
        this.outputCode = outputCode;
        if (outputCode != null && outputCode.isEmpty()) this.outputCode = null;
    }

    public String getJsCode() {
        return jsCode;
    }

    public void setJsCode(String jsCode) {
        this.jsCode = jsCode;
        if (jsCode != null && jsCode.trim().isEmpty()) this.jsCode = null;
    }

    public Boolean isJsMinimized() {
        return jsMinimized;
    }

    public void setJsMinimized(Boolean jsMinimized) {
        this.jsMinimized = jsMinimized;
    }

    public boolean isEndNode() {
        return isEnd;
    }

    public boolean isStartNode() {
        return isStart;
    }

    public int getIndex() {
        return index;
    }

    public List<Integer> getChildNodesIndexes() {
        return childNodesIndexes;
    }

    public Graph getParentGraph() {
        return parentGraph;
    }

    public void setParentGraph(Graph parent) {
        parent.addGraphNode(this, this.getIndex());
    }

    public int getX() {
        return X;
    }

    public void setX(int x) {
        X = x;
    }

    public int getY() {
        return Y;
    }

    public void setY(int y) {
        Y = y;
    }

    public void setFindLongestMatch(boolean findLongestMatch) {
        this.findLongestMatch = findLongestMatch;
    }

    public boolean isFindLongestMatch() {
        //return findLongestMatch;
        return false;
    }

    public String translateMacros(String clause) throws GGSMacroNotFoundException {
        boolean found = false;
        Matcher m = macroPattern.matcher(clause);
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        while (m.find()) {
            found = true;
            sb.append(clause.substring(offset, m.start()));
            String macro = clause.substring(m.start() + 1, m.end() - 1);
            boolean macroOk = false;
            for (Pair<String, String> entry : grammar.macros) {
                if (entry.getKey().equals(macro)) {
                    sb.append(entry.getValue());
                    macroOk = true;
                    offset = m.end();
                    break;
                }
            }

            if (!macroOk)
                throw new GGSMacroNotFoundException(macro);
        }

        if (!found)
            return clause;
        else {
            sb.append(clause.substring(offset));
            return sb.toString();
        }
    }
}
