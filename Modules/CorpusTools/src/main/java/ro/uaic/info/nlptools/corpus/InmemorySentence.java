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

package ro.uaic.info.nlptools.corpus;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

public class InmemorySentence implements INlpSentence {
    private List<SpanAnnotation> spanAnnotations = new ArrayList<>();
    private INlpCorpus parentCorpus;
    private int sentenceIndex;
    private int startTokenIndex;

    public Map<String, String> getFeatures() {
        return features;
    }

    protected Map<String, String> features;
    List<Token> tokens;

    @Override
    public boolean hasInputAnnotations() {
        return !spanAnnotations.isEmpty();
    }

    @Override
    public INlpCorpus getParentCorpus() {
        return parentCorpus;
    }

    @Override
    public int getSentenceIndexInCorpus() {
        return sentenceIndex;
    }

    @Override
    public int getTokenCount() {
        return tokens.size();
    }

    @Override
    public Token getToken(int indexInSentence) {
        return tokens.get(indexInSentence);
    }

    @Override
    public int getFirstTokenIndexInCorpus() {
        return startTokenIndex;
    }

    InmemorySentence(Node node, Map<String, Token> tokensToRefIds) {
        tokens = new ArrayList<>();
        features = new TreeMap<>();

        for (int i = 0; i < node.getAttributes().getLength(); i++) {
            features.put(node.getAttributes().item(i).getNodeName(), node.getAttributes().item(i).getNodeValue());
        }

        NodeList allChildNodes = ((Element) node).getElementsByTagName("*");

        Map<Node, SpanAnnotation> xmlNodesToAnnotations = new HashMap<>(); //used for inline spanAnnotations

        for (int i = 0; i < allChildNodes.getLength(); i++) {
            Node childNode = allChildNodes.item(i);
            if (childNode.getNodeType() == Node.TEXT_NODE) {
                continue;
            }
            NodeList grandChildrenNodes = childNode.getChildNodes();
            if (grandChildrenNodes.getLength() > 1 || (grandChildrenNodes.getLength() == 1 && grandChildrenNodes.item(0).getNodeType() != Node.TEXT_NODE))// if not leaf node
                continue;
            Token token = new Token(childNode);
            String refId = token.getFeatures().get("GGS:RefId");
            if (refId != null) {
                tokensToRefIds.put(refId, token);
                token.getFeatures().remove("GGS:RefId");
            }
            addToken(token);

            Node parent = childNode.getParentNode();
            int annotationsSoFar = spanAnnotations.size();
            while (parent != node) {
                SpanAnnotation annotation = xmlNodesToAnnotations.get(parent);
                if (annotation == null) {
                    //create new input annotation
                    annotation = new SpanAnnotation();
                    annotation.setPreferInlineRepresentation(true);
                    annotation.setStartTokenIndex(tokens.size() - 1);
                    annotation.setEndTokenIndex(annotation.getStartTokenIndex() - 1);
                    annotation.setSentence(this);
                    annotation.setName(parent.getNodeName());
                    xmlNodesToAnnotations.put(parent, annotation);
                    for (int j = 0; j < parent.getAttributes().getLength(); j++)
                        annotation.features.put(parent.getAttributes().item(j).getNodeName(), parent.getAttributes().item(j).getNodeValue());
                    this.spanAnnotations.add(annotationsSoFar, annotation);
                }
                annotation.setEndTokenIndex(annotation.getEndTokenIndex() + 1);
                token.parentAnnotations.add(0, annotation);
                parent = parent.getParentNode();
            }
        }
    }

    public InmemorySentence() {
        tokens = new ArrayList<>();
        features = new TreeMap<>();
    }

    public String toString() {
        return features.toString() + "\n" + tokens.toString();
    }

    public void addTokens(List<Token> tokens) {
        for (Token w : tokens) {
            addToken(w);
        }
    }

    public void addToken(Token token) {
        token.indexInSentence = tokens.size();
        token.parentSentence = this;
        if (getParentCorpus() != null)
            ((InmemoryCorpus)getParentCorpus()).allTokensDirty = true;
        tokens.add(token);
    }

    public List<SpanAnnotation> getSpanAnnotations() {
        return spanAnnotations;
    }

    public void setSentenceIndex(int sentenceIndex) {
        this.sentenceIndex = sentenceIndex;
    }

    public void setParentCorpus(InmemoryCorpus parentCorpus) {
        this.parentCorpus = parentCorpus;
    }

    public void setStartTokenIndex(int startTokenIndex) {
        this.startTokenIndex = startTokenIndex;
    }
}
