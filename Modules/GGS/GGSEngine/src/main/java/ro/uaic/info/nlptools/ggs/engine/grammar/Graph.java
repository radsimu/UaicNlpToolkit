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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Graph {
    protected String id;
    protected Map<Integer, GraphNode> graphNodes;
    protected GraphNode startNode;
    protected GraphNode endNode;
    public Grammar grammar;

    public Graph(Grammar grammar, String id) {
        this.grammar = grammar;
        graphNodes = new LinkedHashMap<Integer, GraphNode>();
        this.id = id;
    }

    protected Graph(Grammar grammar, org.w3c.dom.Node graphElem) {
        this.grammar = grammar;
        graphNodes = new HashMap<Integer, GraphNode>();
        id = graphElem.getAttributes().getNamedItem("ID").getNodeValue();
        NodeList l = ((Element) graphElem).getElementsByTagName("Node");

        for (int i = 0; i < l.getLength(); i++) {
            Node graphNodeElem = l.item(i);
            GraphNode newGraphNode = new GraphNode(graphNodeElem, this);
            graphNodes.put(newGraphNode.index, newGraphNode);
        }

        //remove any evental links to comment nodes or null nodes
        for (GraphNode gn : getGraphNodes().values()) {
            for (int i = 0; i < gn.getChildNodesIndexes().size(); i++) {
                if (getGraphNodes().get(gn.getChildNodesIndexes().get(i)).nodeType == GraphNode.NodeType.Comment) {
                    gn.getChildNodesIndexes().remove(i);
                    i--;
                }
            }
        }
    }

    public String toString() {
        return id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<Integer, GraphNode> getGraphNodes() {
        return graphNodes;
    }

    public GraphNode getStartNode() {
        return startNode;
    }

    public void setStartNode(GraphNode startNode) {
        if (this.startNode != null) {
            this.startNode.isStart = false;
        }
        this.startNode = startNode;
        startNode.isStart = true;
    }

    public GraphNode getEndNode() {
        return endNode;
    }

    public void setEndNode(GraphNode endNode) {
        if (this.endNode != null) {
            this.endNode.isEnd = false;
        }
        this.endNode = endNode;
        endNode.isEnd = true;
    }

    public void addGraphNode(GraphNode graphNode) {
        addGraphNode(graphNode, -1);
    }

    public void addGraphNode(GraphNode graphNode, int index) {
        if (graphNode.parentGraph != null) {
            graphNode.parentGraph.removeGraphNode(graphNode);
        }

        graphNode.parentGraph = this;
        graphNode.index = index;
        if (index == -1) {

            //get first unused node positionInSentence
            int i = 0;
            while (graphNodes.containsKey(i)) i++;
            graphNode.index = i;
        }

        graphNodes.put(graphNode.index, graphNode);
    }

    private void removeGraphNode(GraphNode graphNode) {
        graphNodes.remove(graphNode);
        graphNode.parentGraph = null;
    }
}
