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

package ro.uaic.info.nlptools.ggs.engine.core;

import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import java.util.*;

public class Match {
    protected List<Token> tokens;
    protected INlpSentence sentence;

    protected Stack<SpanAnnotation> spanAnnotationStack; // used for establishing what span annotation must be closed when a closing marker is reached
    protected Stack<SpanAnnotation> spanAnnotations;
    protected List<GraphNode> matchedGraphNodesPath; //list of nodes that the matching process has passed through
    protected int startPositionInSentence = -1;

    public List<GraphNode> getMatchedGraphNodesPath() {
        return matchedGraphNodesPath;
    }


    protected void insertStartAnnotation(int index, String name, GraphNode annotatorNode) {
        SpanAnnotation spanAnnotation = new SpanAnnotation();
        spanAnnotation.setSentence(sentence);
        spanAnnotation.setStartTokenIndex(index);
        spanAnnotation.setName(name);
        spanAnnotationStack.push(spanAnnotation);
        spanAnnotations.push(spanAnnotation);
    }

    protected void insertEndAnnotation(int index, GraphNode annotatorNode) throws GGSOutputMalformatException {
        if (spanAnnotationStack.isEmpty())
            throw new GGSOutputMalformatException(annotatorNode);
        SpanAnnotation spanAnnotation = spanAnnotationStack.pop();
        spanAnnotation.setEndTokenIndex(index);
        if (spanAnnotation.getEndTokenIndex() < spanAnnotation.getStartTokenIndex()) {
            throw new GGSOutputMalformatException(spanAnnotation);
        }
    }

    protected void insertFeature(String key, String val) {
        spanAnnotationStack.peek().getFeatures().put(key, val);
    }

    protected void removeFeature(String key) {
        spanAnnotationStack.peek().getFeatures().remove(key);
    }

    protected Match() {
        tokens = new ArrayList<Token>();

        spanAnnotations = new Stack<SpanAnnotation>();
        spanAnnotationStack = new Stack<SpanAnnotation>();
    }

    protected Match(Match m) {
        sentence = m.sentence;
        startPositionInSentence = m.startPositionInSentence;
        tokens = new ArrayList<Token>(m.tokens);
        spanAnnotations = new Stack<SpanAnnotation>();
        spanAnnotationStack = new Stack<SpanAnnotation>();

        for (SpanAnnotation spanAnnotationOld : m.spanAnnotations) {
            SpanAnnotation spanAnnotationClone = new SpanAnnotation(spanAnnotationOld);
            spanAnnotations.push(spanAnnotationClone);
            if (m.spanAnnotationStack.contains(spanAnnotationOld)) {
                spanAnnotationStack.push(spanAnnotationClone);
            }
        }
    }

    protected void add(Token w) {
        tokens.add(w);
    }

    public int size() {
        return tokens.size();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        List<Integer> tokensAtOffset = new ArrayList<Integer>();

        for (Token token : tokens) {
            tokensAtOffset.add(sb.length());
            String word = token.toString();
            sb.append(word).append(" ");
        }

        int extraChars = 0;
        for (SpanAnnotation a : spanAnnotations) {
            sb.insert(tokensAtOffset.get(a.getStartTokenIndex() - tokens.get(0).getTokenIndexInSentence()), "[");
            for (int j = a.getStartTokenIndex(); j <= tokens.get(tokens.size() - 1).getTokenIndexInSentence(); j++)
                tokensAtOffset.set(j - tokens.get(0).getTokenIndexInSentence(), tokensAtOffset.get(j - tokens.get(0).getTokenIndexInSentence()) + 1);
        }

        for (SpanAnnotation a : spanAnnotations) {
            sb.insert(tokensAtOffset.get(a.getEndTokenIndex() - tokens.get(0).getTokenIndexInSentence()) + a.getSentence().getToken(a.getEndTokenIndex()).toString().length(), "]");
            for (int j = a.getEndTokenIndex() + 1; j <= tokens.get(tokens.size() - 1).getTokenIndexInSentence(); j++)
                tokensAtOffset.set(j - tokens.get(0).getTokenIndexInSentence(), tokensAtOffset.get(j - tokens.get(0).getTokenIndexInSentence()) + 1);
        }

        return sb.toString();
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public INlpSentence getSentence() {
        return sentence;
    }

    public Stack<SpanAnnotation> getSpanAnnotations() {
        return spanAnnotations;
    }
}
