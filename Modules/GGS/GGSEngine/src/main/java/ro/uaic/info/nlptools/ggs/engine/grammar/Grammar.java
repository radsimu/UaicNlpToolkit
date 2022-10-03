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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ro.uaic.info.nlptools.ggs.engine.core.Pair;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class Grammar {
    protected Map<String, Graph> graphs;
    public List<Pair<String, String>> macros;

    public String getJsCode() {
        return jsCode;
    }

    public void setJsCode(String jsCode) {
        this.jsCode = jsCode;
    }

    protected String jsCode;

    public Grammar() {
        graphs = new HashMap<String, Graph>();
        macros = new ArrayList<Pair<String, String>>();
    }

    public Grammar(InputStream is) throws IOException, SAXException, ParserConfigurationException {
        load(is);
    }

    public void load(InputStream is) throws ParserConfigurationException, IOException, SAXException {
        try {
            graphs = new HashMap<String, Graph>();
            macros = new ArrayList<Pair<String, String>>();
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            Document doc = null;
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(is);

            Element root = doc.getDocumentElement();
            root.normalize();

            NodeList l = root.getChildNodes();
            for (int i = 0; i < l.getLength(); i++) {
                if (l.item(i).getNodeName().equals("JScode")) {
                    jsCode = l.item(i).getTextContent().trim();
                    if (jsCode.isEmpty()) jsCode = null;
                    continue;
                }
                if (l.item(i).getNodeName().equals("Macros")) {
                    NodeList macroElems = ((Element) l.item(i)).getElementsByTagName("Macro");
                    for (int ii = 0; ii < macroElems.getLength(); ii++) {
                        Element macroElem = (Element) macroElems.item(ii);
                        macros.add(new Pair<String, String>(macroElem.getAttribute("key"), macroElem.getAttribute("value")));
                    }
                }
            }

            l = doc.getElementsByTagName("Graph");
            for (int i = 0; i < l.getLength(); i++) {
                Node graphElem = l.item(i);
                Graph g = new Graph(this, graphElem);
                graphs.put(g.id, g);
            }
        } catch (SAXException e) {
            is.close();
            throw e;
        } catch (IOException e) {
            is.close();
            throw e;
        } catch (ParserConfigurationException e) {
            is.close();
            throw e;
        }
    }


    public void save(OutputStream os) throws ParserConfigurationException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.newDocument();

        Element root = doc.createElement("Grammar");
        doc.appendChild(root);
        if (jsCode != null) {
            Element jsCodeElem = doc.createElement("JScode");
            jsCodeElem.setTextContent(jsCode);
            root.appendChild(jsCodeElem);
        }

        if (macros != null && macros.size() > 0) {
            Element macrosElem = doc.createElement("Macros");
            root.appendChild(macrosElem);
            for (Pair<String, String> entry : macros) {
                if (entry == null)
                    continue;;
                Element macroElem = doc.createElement(("Macro"));
                macrosElem.appendChild(macroElem);
                macroElem.setAttribute("key", entry.getKey());
                macroElem.setAttribute("value", entry.getValue());
                ;
            }
        }

        for (Graph gr : graphs.values()) {
            Element graphElem = doc.createElement("Graph");
            graphElem.setAttribute("ID", gr.id);
            root.appendChild(graphElem);

            for (GraphNode gn : gr.graphNodes.values()) {
                if (gn == null) continue;
                Element graphNodeElem = doc.createElement("Node");
                graphNodeElem.setAttribute("ID", Integer.toString(gn.index));
                graphNodeElem.setAttribute("X", Integer.toString(gn.X));
                graphNodeElem.setAttribute("Y", Integer.toString(gn.Y));
                if (gn.jsCode != null) {
                    Element jsCodeElem = doc.createElement("JScode");
                    jsCodeElem.setTextContent(gn.jsCode);
                    graphNodeElem.appendChild(jsCodeElem);
                    jsCodeElem.setAttribute("minimized", gn.isJsMinimized().toString());
                }

                if (!gn.isFindLongestMatch())
                    graphNodeElem.setAttribute("priorityPolicy", "custom");

                if (gn.isStart) {
                    graphNodeElem.setAttribute("isStartNode", "true");
                }
                if (gn.isEnd) {
                    graphNodeElem.setAttribute("isEndNode", "true");
                }

                Element MatchingPatternCodeElement = doc.createElement("MatchingPatternCode");
                MatchingPatternCodeElement.appendChild(doc.createTextNode(gn.getTokenMatchingCode()));
                graphNodeElem.appendChild(MatchingPatternCodeElement);

                if (gn.getOutputCode() != null && !gn.getOutputCode().isEmpty()) {
                    Element AnnotationCodeElement = doc.createElement("AnnotationCode");
                    AnnotationCodeElement.appendChild(doc.createTextNode(gn.getOutputCode()));
                    graphNodeElem.appendChild(AnnotationCodeElement);
                }

                graphElem.appendChild(graphNodeElem);

                for (int child : gn.childNodesIndexes) {
                    if (gn.parentGraph.graphNodes.get(child) == null) continue;
                    Element childElem = doc.createElement("Child");
                    childElem.appendChild(doc.createTextNode(Integer.toString(child)));
                    graphNodeElem.appendChild(childElem);
                }
            }
        }
        doc.normalize();

        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer serializer;
        try {
            serializer = tfactory.newTransformer();
            //Setup indenting to "pretty print"
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            serializer.transform(new DOMSource(doc), new StreamResult(os));
        } catch (TransformerException e) {
            // this is fatal, just dump the stack and throw a runtime exception
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

    public Map<String, Graph> getGraphs() {
        return graphs;
    }

    public void removeMacro(int macroIndex) {
        if (macros.get(macroIndex) != null) {
            for (Graph graph : graphs.values())
                for (GraphNode gn : graph.getGraphNodes().values())
                    gn.setTokenMatchingCode(gn.getTokenMatchingCode().replace("@" + macros.get(macroIndex).getKey() + "@", macros.get(macroIndex).getValue()));
        }
        macros.remove(macroIndex);
    }
}
