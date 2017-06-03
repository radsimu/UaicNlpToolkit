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

import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TransitionCondition {
    public String pattern;
    public Map<Pattern, Pattern> featuresConditions;
    Map<Pattern, Boolean> featuresConditionsSigns;
    HashSet<Pattern> featuresConditionNamesWhichAreRegex;
    HashSet<Pattern> featuresConditionValuesWhichAreRegex;
    HashSet<Pattern> featuresConditionsWhichUseJsVars;
    public boolean isEmpty;
    public String annotationMatcher;
    public SpanAnnotation matchedAnnotation;
    public boolean hasJsVars = false;
    private State state;

    public TransitionCondition(String pattern, State state) {
        this.state = state;
        this.pattern = pattern;

        Matcher m = GraphNode.jsVarsPattern.matcher(pattern);
        if (m.find()) {
            hasJsVars = true;
            state.stateMachine.graphsContainingJsCode.add(state.parentGraphNode.getParentGraph());
        }
    }

    public void Compile() throws GraphNodeMatchingSyntaxException {
        try {
            buildPattern(pattern);
        }catch (GraphNodeMatchingSyntaxException ex){
            if (!hasJsVars)
                throw ex;
        }
    }

    private void buildPattern(String pattern) throws GraphNodeMatchingSyntaxException {
        if (pattern.equals("<E>") || pattern.equals("<<E>>")) {
            isEmpty = true;
            return;
        }

        featuresConditions = new HashMap<Pattern, Pattern>();
        featuresConditionsSigns = new HashMap<Pattern, Boolean>();
        featuresConditionNamesWhichAreRegex = new HashSet<Pattern>();
        featuresConditionValuesWhichAreRegex = new HashSet<Pattern>();
        featuresConditionsWhichUseJsVars = new HashSet<Pattern>();

        if (!GraphNode.tokenMatchingCodeValidatorPattern.matcher(pattern).matches()) {
            throw new GraphNodeMatchingSyntaxException(state.parentGraphNode, pattern);
        }

        pattern = pattern.substring(1).trim();

        boolean firstMatch = true;
        Matcher matcher = GraphNode.tokenMatchingCodeParserPattern.matcher(pattern);
        while (matcher.find()) {
            String feat = matcher.group();
            if (firstMatch) {
                if (matcher.start() > 0) {
                    annotationMatcher = pattern.substring(0, matcher.start());
                }
                firstMatch = false;
            }
            String[] toks = feat.split("(?<=[^\\\\])=");
            boolean sign;
            if (toks[0].charAt(0) == '+')
                sign = true;
            else
                sign = false;
            toks[0] = toks[0].substring(1);
            String tok0 = toks[0];

            boolean isRegex = false;
            if (toks[0].startsWith("/")) {
                toks[0] = toks[0].substring(1);
                isRegex = true;
            }
            int flags = 0;
            if (toks[0].endsWith("/")) {
                toks[0] = toks[0].substring(0, toks[0].length() - 1);
            } else if (toks[0].endsWith("/i")) {
                toks[0] = toks[0].substring(0, toks[0].length() - 2);
                flags = Pattern.CASE_INSENSITIVE;
            }

            Pattern featNamePattern = Pattern.compile(toks[0], flags);
            featuresConditionsSigns.put(featNamePattern, sign);
            if (isRegex)
                featuresConditionNamesWhichAreRegex.add(featNamePattern);

            isRegex = false;
            if (toks[1].startsWith("/")) {
                toks[1] = toks[1].substring(1);
                isRegex = true;
            }
            flags = 0;
            if (toks[1].endsWith("/")) {
                toks[1] = toks[1].substring(0, toks[1].length() - 1);
            } else if (toks[1].endsWith("/i")) {
                toks[1] = toks[1].substring(0, toks[1].length() - 2);
                flags = Pattern.CASE_INSENSITIVE;
            }
            Pattern featValuePattern = Pattern.compile(toks[1], flags);
            featuresConditions.put(featNamePattern, featValuePattern);
            if (isRegex)
                featuresConditionValuesWhichAreRegex.add(featValuePattern);
            if (GraphNode.jsVarsPattern.matcher(featNamePattern.toString()).find() ||
                GraphNode.jsVarsPattern.matcher(featValuePattern.toString()).find())
                featuresConditionsWhichUseJsVars.add(featNamePattern);
        }
        if (firstMatch && pattern.matches("[^\\s=\"/\\\\]+>"))
            annotationMatcher = pattern.substring(0, pattern.length() - 1);
    }

    public boolean Matches(Token w) throws GraphNodeVarException, CoreCriticalException, GraphNodeMatchingSyntaxException {
        matchedAnnotation = null;
        if (hasJsVars) {
            try {
                state.stateMachine.jsEngine.eval("var token = sentence[" + w.getTokenIndexInSentence() + " - 1]");
                buildPattern(state.stateMachine.evalJsFragments(pattern, state));
                state.stateMachine.jsEngine.eval("delete token");
            } catch (ScriptException e) {
                throw new CoreCriticalException(e);
            }
        }
        if (annotationMatcher != null) {//if this transition tests an spanAnnotation and not a token
            for (SpanAnnotation a : w.getParentSpanAnnotations())
                if (((!state.lookBehind && a.getStartTokenIndex() == w.getTokenIndexInSentence()) || (state.lookBehind && a.getEndTokenIndex() == w.getTokenIndexInSentence())) && a.getName().equals(annotationMatcher))
                    if (Matches(a.getFeatures()))
                        if (matchedAnnotation == null || (matchedAnnotation.getEndTokenIndex() - matchedAnnotation.getEndTokenIndex() < matchedAnnotation.getEndTokenIndex() - matchedAnnotation.getStartTokenIndex()))
                            matchedAnnotation = a;
            if (matchedAnnotation != null)
                return true;
            return false;
        }

        return Matches(w.getFeatures());
    }

    private boolean Matches(Map<String, String> features) {
        for (Map.Entry<Pattern, Pattern> entry : featuresConditions.entrySet()) {
            if (!checkFeature(entry.getKey(), entry.getValue(), features)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkFeature(Pattern featNamePattern, Pattern featValPattern, Map<String, String> features) {
        boolean rez = false;

        if (featuresConditionNamesWhichAreRegex.contains(featNamePattern)) {
            for (Map.Entry<String, String> e : features.entrySet())
                if (featNamePattern.matcher(e.getKey()).matches())
                    if ((featuresConditionValuesWhichAreRegex.contains(featValPattern) && featValPattern.matcher(e.getValue()).matches()) ||
                            (!featuresConditionValuesWhichAreRegex.contains(featValPattern) && featValPattern.toString().equals(e.getValue()))) {
                        rez = true;
                        break;
                    }
        } else {
            String val = features.get(featNamePattern.toString());
            if (val != null)
                rez = (featuresConditionValuesWhichAreRegex.contains(featValPattern) && featValPattern.matcher(val).matches()) ||
                        (!featuresConditionValuesWhichAreRegex.contains(featValPattern) && featValPattern.toString().equals(val));
        }

        if (!featuresConditionsSigns.get(featNamePattern)) {
            rez = !rez;
        }
        return rez;
    }

    @Override
    public String toString() {
        return pattern;
    }
}
