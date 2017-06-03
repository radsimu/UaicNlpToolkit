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

import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;

class OutputAnnotator {

    public int nestedLimit = 0; // pentru type=start, limitam numarul de adnotari cu acelasi nume, imbricate
    private boolean hasJsVars = false;
    private String code = null;

    private State state;
    List<String> featsToDelFromAnnotation;
    Map<String, String> featsToAddToAnnotation;
    boolean startsAnnotation = false;
    String newAnnotationName = null;
    boolean endsAnnotation = false;


    public OutputAnnotator(State state, String code) {
        this.state = state;
        this.code = code;
        if (code == null) return;
        Matcher m = GraphNode.jsVarsPattern.matcher(code);
        if (m.find()) {
            hasJsVars = true;
        }
    }

    public void Compile() throws GraphNodeOutputSyntaxException {
        if (!hasJsVars)
            buildPattern(code);
        //otherwise compile before matching
    }

    private void buildPattern(String code) throws GraphNodeOutputSyntaxException {
        if (!GraphNode.outputCodeValidatorPattern.matcher(code).matches()) {
            throw new GraphNodeOutputSyntaxException(state.parentGraphNode);
        }

        Matcher m = GraphNode.outputCodeParserPattern.matcher(code);
        featsToAddToAnnotation = new TreeMap<String, String>();
        featsToDelFromAnnotation = new ArrayList<String>();
        while (m.find()) {
            if (state.isGraphNodeExit) {
                if (m.group().equals(">"))
                    endsAnnotation = true;
            } else if (state.isGraphNodeEntry) {
                if (m.group().startsWith("<")) {
                    startsAnnotation = true;
                    newAnnotationName = m.group().substring(1);
                } else if (m.group().startsWith("-")) {
                    featsToDelFromAnnotation.add(m.group().substring(2));
                } else if (m.group().startsWith("+")) {
                    featsToAddToAnnotation.put(m.group(1), m.group(2));
                }
            }
        }
    }

    public void Apply(Match m) throws GGSException {
        if (hasJsVars) {
            buildPattern(state.stateMachine.evalJsFragments(code, state));
        }

        if (startsAnnotation) {
            m.insertStartAnnotation(m.startPositionInSentence + m.tokens.size(), newAnnotationName, state.parentGraphNode);
        }

        for (String key : featsToDelFromAnnotation) {
            m.removeFeature(key);
        }

        for (Map.Entry<String, String> entry : featsToAddToAnnotation.entrySet()) {
            m.insertFeature(entry.getKey(), entry.getValue());
        }

        if (endsAnnotation) {
            m.insertEndAnnotation(m.startPositionInSentence + m.tokens.size() - 1, state.parentGraphNode);
        }
    }
}
