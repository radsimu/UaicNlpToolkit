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

import ro.uaic.info.nlptools.ggs.engine.SparseBitSet;
import ro.uaic.info.nlptools.corpus.INlpCorpus;
import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import ro.uaic.info.nlptools.ggs.engine.grammar.Graph;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;
import org.apache.commons.lang3.StringEscapeUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class StateMachine {
    //public int startStateIndex = -1;
    public int terminalStateIndex = -1;
    public StateMachine mirroredMachine = null;
    public List<State> states;
    public boolean requestStop;
    protected Map<GraphNode, CompiledNode> compNodes;
    protected ScriptEngine jsEngine;
    Grammar grammar;
    CompiledGrammar compiledGrammar;
    Set<AssertionCondition> assertions;
    Map<GraphNode, GraphNode> mapToMirrored;
    Set<Graph> graphsContainingJsCode;
    Set verifiedStates;

    public StateMachine(CompiledGrammar compiledGrammar) {
        states = new ArrayList<State>();
        this.compiledGrammar = compiledGrammar;
    }

    public static String tokenToJson(Token token) {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("index : ").append(token.getTokenIndexInSentence()).append(",\n");
        sb.append("parentAnnotations : [],\n");
        {
            sb.append("features:{");
            for (Map.Entry<String, String> entry : token.getFeatures().entrySet()) {
                sb.append("'").append(StringEscapeUtils.escapeEcmaScript(entry.getKey())).append("' : \"").append(StringEscapeUtils.escapeEcmaScript(entry.getValue())).append("\",\n");
            }
            sb.delete(sb.length() - 2, sb.length());
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    public static String annotationToJson(SpanAnnotation annotation) {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("name : '").append(annotation.getName()).append("',\n");
        sb.append("startToken : sentence[").append(annotation.getStartTokenIndex()).append("],\n");
        sb.append("endToken : sentence[").append(annotation.getEndTokenIndex()).append("]\n");
        if (annotation.getFeatures().size() > 0) {
            sb.append(",features:{");
            for (Map.Entry<String, String> entry : annotation.getFeatures().entrySet()) {
                sb.append("\"").append(StringEscapeUtils.escapeEcmaScript(entry.getKey())).append("\" : \"").append(StringEscapeUtils.escapeEcmaScript(entry.getValue())).append("\",\n");
            }
            sb.delete(sb.length() - 2, sb.length());
            sb.append("}");
        }
        sb.append("}");

        return sb.toString();
    }

    public Boolean usesJs() {
        //return false;
        return !graphsContainingJsCode.isEmpty();
    }

    public void compile(Grammar grammar) throws GGSException {
        compile(grammar, false);
    }

    public void compile(Grammar grammar, boolean isMirrored) throws GGSException {
        compileNoAssertions(grammar, isMirrored);
        if (usesJs())
            initJsEngine();
        for (AssertionCondition assertionCondition : assertions) {
            assertionCondition.compile(this);
        }
    }

    void compileNoAssertions(Grammar grammar, boolean isMirror) throws GGSException {
        graphsContainingJsCode = new HashSet<Graph>();
        this.grammar = grammar;
        compNodes = new HashMap<GraphNode, CompiledNode>();
        assertions = new HashSet<AssertionCondition>();

        for (Graph graph : grammar.getGraphs().values()) {
            //generate states for each GraphNode
            for (GraphNode graphNode : graph.getGraphNodes().values()) {
                if (graphNode == null) continue;
                if (graphNode.nodeType == GraphNode.NodeType.Comment) continue;
                if (graphNode.getJsCode() != null) graphsContainingJsCode.add(graph);
                genStates(graphNode);
            }

            //create transition links
            for (GraphNode graphNode : graph.getGraphNodes().values()) {
                if (graphNode == null) continue;
                if (graphNode.nodeType == GraphNode.NodeType.Comment) continue;

                if (graphNode.nodeType == GraphNode.NodeType.LookBehind) {//lookbehind assertion
                    AssertionCondition assertionCondition = new AssertionCondition(graphNode);
                    assertions.add(assertionCondition);
                    for (int childNodeIndex : graphNode.getChildNodesIndexes()) {
                        states.get(compNodes.get(graph.getGraphNodes().get(childNodeIndex)).startStateIndex).assertions.add(assertionCondition);
                    }
                } else
                    for (int childNodeIndex : graphNode.getChildNodesIndexes()) {
                        if (graph.getGraphNodes().get(childNodeIndex).nodeType == GraphNode.NodeType.LookAhead || graph.getGraphNodes().get(childNodeIndex).nodeType == GraphNode.NodeType.CrossReference) {//assertions linked as children
                            AssertionCondition assertionCondition;
                            if (isMirror && graph.getGraphNodes().get(childNodeIndex).nodeType == GraphNode.NodeType.CrossReference)
                                assertionCondition = new AssertionCondition(mapToMirrored.get(graph.getGraphNodes().get(childNodeIndex)));
                            else
                                assertionCondition = new AssertionCondition(graph.getGraphNodes().get(childNodeIndex));
                            assertions.add(assertionCondition);
                            states.get(compNodes.get(graphNode).endStateIndex).assertions.add(assertionCondition);
                        } else {
                            states.get(compNodes.get(graphNode).endStateIndex).childStatesIndexes.add(compNodes.get(graph.getGraphNodes().get(childNodeIndex)).startStateIndex);
                            states.get(compNodes.get(graph.getGraphNodes().get(childNodeIndex)).startStateIndex).parentStatesIndexes.add(compNodes.get(graphNode).endStateIndex);
                        }
                    }
            }
        }

        //link jump nodes
        for (Graph graph : grammar.getGraphs().values()) {
            for (GraphNode graphNode : graph.getGraphNodes().values()) {
                if (graphNode == null) continue;
                if (graphNode.nodeType == GraphNode.NodeType.Comment) continue;
                for (String clausee : GraphNode.clauseSplittingRegex.split(graphNode.getTokenMatchingCode())) {
                    String clause = graphNode.translateMacros(clausee);
                    if (clause.startsWith(":")) {
                        String str = clause.substring(1);
                        int aux = str.indexOf('(');
                        if (aux > 0) str = str.substring(0, aux);
                        if (!grammar.getGraphs().containsKey(str)) {
                            throw new GraphNodeJumpException(graphNode, str);
                        }
                        states.get(compNodes.get(graphNode).startStateIndex).childStatesIndexes.add(compNodes.get(grammar.getGraphs().get(str).getStartNode()).startStateIndex);
                        states.get(compNodes.get(grammar.getGraphs().get(str).getEndNode()).endStateIndex).childStatesIndexes.add(compNodes.get(graphNode).endStateIndex);
                    }
                }
            }
        }

        //compile states
        for (State s : states) {
            s.Compile();
            s.lookBehind = isMirror;
        }
    }

    protected void compileMirrored() throws GGSException {
        Grammar mirroredGrammar = new Grammar();
        mapToMirrored = new HashMap<GraphNode, GraphNode>();

        for (Map.Entry<String, Graph> entry : grammar.getGraphs().entrySet()) {
            Graph mirroredGraph = new Graph(grammar, entry.getKey());
            mirroredGrammar.getGraphs().put(mirroredGraph.getId(), mirroredGraph);

            for (GraphNode gn : entry.getValue().getGraphNodes().values()) {
                if (gn.nodeType == GraphNode.NodeType.Comment) continue;

                GraphNode mirroredNode = gn.clone();
                mapToMirrored.put(gn, mirroredNode);
                mirroredGraph.addGraphNode(mirroredNode, gn.getIndex());
                if (gn.isEnd) {
                    mirroredNode.isStart = true;
                    mirroredGraph.setStartNode(mirroredNode);

                } else if (gn.isStart) {
                    mirroredNode.isEnd = true;
                    mirroredGraph.setEndNode(mirroredNode);
                }

                if (mirroredNode.getTokenMatchingCode().startsWith("?<")) {
                    mirroredNode.setTokenMatchingCode("?" + mirroredNode.getTokenMatchingCode().substring(2));
                } else if (mirroredNode.getTokenMatchingCode().startsWith("?")) {
                    mirroredNode.setTokenMatchingCode("?<" + mirroredNode.getTokenMatchingCode().substring(1));
                }
            }
            for (GraphNode gn : entry.getValue().getGraphNodes().values()) {
                if (gn == null) continue;
                if (gn.nodeType == GraphNode.NodeType.Comment) continue;
                if (gn.nodeType == GraphNode.NodeType.CrossReference)
                    for (int childNodeIndex : gn.getChildNodesIndexes()) {
                        mirroredGraph.getGraphNodes().get(gn.getIndex()).getChildNodesIndexes().add(childNodeIndex);
                    }
                else
                    for (int childNodeIndex : gn.getChildNodesIndexes()) {
                        if (entry.getValue().getGraphNodes().get(childNodeIndex).nodeType == GraphNode.NodeType.CrossReference)
                            mirroredGraph.getGraphNodes().get(gn.getIndex()).getChildNodesIndexes().add(childNodeIndex);
                        else
                            mirroredGraph.getGraphNodes().get(childNodeIndex).getChildNodesIndexes().add(gn.getIndex());
                    }
            }
        }

        mirroredMachine = new StateMachine(compiledGrammar);
        ;
        mirroredMachine.mirroredMachine = this;
        mirroredMachine.mapToMirrored = new HashMap<GraphNode, GraphNode>();
        for (Map.Entry<GraphNode, GraphNode> entry : mapToMirrored.entrySet())
            mirroredMachine.mapToMirrored.put(entry.getValue(), entry.getKey());

        mirroredMachine.compile(mirroredGrammar, true);
        mirroredMachine.jsEngine = jsEngine;

    }

    public void genStates(GraphNode graphNode) throws GGSMacroNotFoundException {
        CompiledNode compNode = new CompiledNode();

        compNode.startStateIndex = states.size();
        states.add(new State(this, graphNode));
        states.get(states.size() - 1).index = states.size() - 1;
        states.get(states.size() - 1).isGraphNodeEntry = true;
        states.get(states.size() - 1).searchForLongestMatch = false;
        states.get(states.size() - 1).jumpParameters = new ArrayList<String>();

        compNode.endStateIndex = states.size();
        states.add(new State(this, graphNode));
        states.get(states.size() - 1).index = states.size() - 1;
        states.get(states.size() - 1).searchForLongestMatch = graphNode.isFindLongestMatch();
        states.get(states.size() - 1).isGraphNodeExit = true;

        for (String clausee : GraphNode.clauseSplittingRegex.split(graphNode.getTokenMatchingCode())) {
            String clause = graphNode.translateMacros(clausee);
            if (!clause.startsWith(":")) {
                State s = new State(clause, this, graphNode);
                s.childStatesIndexes.add(compNode.endStateIndex);
                states.get(compNode.startStateIndex).childStatesIndexes.add(states.size());
                states.add(s);
                s.index = states.size() - 1;
            } else {
                int aux = clause.indexOf("(");
                if (aux > 0)
                    states.get(compNode.startStateIndex).jumpParameters.add(String.format("[%s]", clause.substring(aux + 1, clause.lastIndexOf(")"))));
                else
                    states.get(compNode.startStateIndex).jumpParameters.add(null);
            }
        }

        compNodes.put(graphNode, compNode);
    }

    public List<Match> run(int startState, INlpCorpus input, int consumeLimit, boolean assertionMatch, boolean indexedSearch, SearchSpaceReducer.PTAcache cache) throws GGSException, IOException {
        if (!indexedSearch) {
            List<Match> rez = new ArrayList<>();

            for (int i =0;i<input.getSentenceCount();i++){
                rez.addAll(run(startState, input.getSentence(i), consumeLimit, assertionMatch, indexedSearch, cache));
            }

            return rez;
        } else {
            SparseBitSet searchSpace = SearchSpaceReducer.ReduceSearchSpace(this, startState, input, cache);
            int lastMatchEndPos = 0;
            List<Match> rez = new ArrayList<>();

            for (int i = searchSpace.nextSetBit(); i != -1; i = searchSpace.nextSetBit()) {
                if (i >= lastMatchEndPos) {
                    if (requestStop)
                        break;
                    Match match = run(startState, input.getToken(i), consumeLimit, false, true, cache);

                    if (match != null) {
                        lastMatchEndPos = i + match.size();
                        rez.add(match);
                    }
                }
            }
            return rez;
        }
    }

    public List<Match> run(int startState, INlpSentence s, int consumeLimit, boolean assertionMatch, boolean indexedSearch, SearchSpaceReducer.PTAcache cache) throws GGSException, IOException {
        if (!indexedSearch) {
            List<Match> rez = new ArrayList<Match>();

            for (int i = 0; i < s.getTokenCount(); i++) {
                if (requestStop)
                    break;
                Match match = run(startState, s.getToken(i), consumeLimit, assertionMatch, false, cache);
                if (match != null) {
                    rez.add(match);
                    i += match.size() - 1;
                }
            }

            return rez;
        } else {
            INlpCorpus input = s.getParentCorpus();
            SparseBitSet searchSpace;
            if (assertionMatch)
                searchSpace = SearchSpaceReducer.ReduceSearchSpaceForSentButCacheAll(this, startState, s, cache);
            else
                searchSpace = SearchSpaceReducer.ReduceSearchSpace(this, startState, s, cache);
            int lastMatchEndPos = 0;
            List<Match> rez = new ArrayList<Match>();

            boolean sign = false;
            int offset = 0;

            for (int i = searchSpace.nextSetBit(); i != -1; i = searchSpace.nextSetBit()) {
                if (i >= lastMatchEndPos) {
                    if (requestStop)
                        break;
                    Match match = run(startState, input.getToken(i), consumeLimit, false, true, cache);

                    if (match != null) {
                        lastMatchEndPos = i + match.size();
                        rez.add(match);
                    }
                }
            }
            return rez;
        }
    }

    public Match run(int startState, Token token, int consumeLimit, boolean assertionMatch, boolean indexedSearch, SearchSpaceReducer.PTAcache ptaCache) throws GGSException, IOException {
        if (indexedSearch) {
            boolean searchCachePresent = false;
            SearchSpaceReducer.SearchCache searchChache = SearchSpaceReducer.searchCache.get(token.getParentCorpus());
            if (searchChache != null) {
                if (searchChache.reducedSearchSpaces.containsKey(this))
                    if (searchChache.reducedSearchSpaces.get(this).containsKey(startState)) {
                        searchCachePresent = true;
                        if (!searchChache.reducedSearchSpaces.get(this).get(startState).get(token.getTokenIndexInCorpus()))
                            return null;
                    }
            }
            if (!searchCachePresent)//build cache
                if (!SearchSpaceReducer.ReduceSearchSpaceForSentButCacheAll(this, startState, token.getParentSentence(), ptaCache).get(token.getTokenIndexInCorpus()))
                    return null;
        }
        return states.get(startState).Match(token.getParentSentence(), token.getTokenIndexInSentence(), terminalStateIndex, consumeLimit, assertionMatch, indexedSearch, ptaCache);
    }

    public List<State> findEmptyMatchingPath(int startState) throws GGSNullMatchException {
        Stack<GraphNode> callStack = new Stack<GraphNode>();
        verifiedStates = new HashSet();
        return findEmptyMatchingPath(states.get(startState), new ArrayList<State>(), callStack);
    }

    private List<State> findEmptyMatchingPath(State curentState, List<State> curentPath, Stack<GraphNode> callStack) throws GGSNullMatchException {
        if (curentState.index == terminalStateIndex)
            return curentPath;

        if (verifiedStates.contains(curentState)) {
            //this path has been travelled before and it was ok
            return null;
        }

        //do not continue on non empty path
        if (!curentState.condition.isEmpty) {
            return null;
        }

        //ussing a callStack to correctly follow paths for jump transitions
        if (curentState.isGraphNodeEntry) {
            callStack.push(curentState.parentGraphNode);
        }
        if (curentState.isGraphNodeExit) {
            if (curentState.parentGraphNode != callStack.peek()) {
                return null; // this path is not valid according to the jumps/call stack
            }
            callStack.pop();
        }


        List<State> newPath = new ArrayList<State>(curentPath);
        newPath.add(curentState);

        for (int childStateIndex : curentState.childStatesIndexes) {
            List<State> foundPath = findEmptyMatchingPath(states.get(childStateIndex), newPath, callStack);
            if (foundPath != null)
                return foundPath;
        }

        if (curentState.isGraphNodeEntry) {
            verifiedStates.add(curentState);
        }
        return null;
    }

    public List<State> findInfiniteLoop(int startState) throws GGSNullMatchException {
        //search for loops of states that consume nothing
        //start from the start node and compose all possible roads while checking if the trailing empty nodes from the end of the current path have any node occuring twice.
        //maintain a set of already checked states

        Stack<GraphNode> callStack = new Stack<GraphNode>();
        verifiedStates = new HashSet();

        List<State> path = findInfiniteLoop(states.get(startState), new ArrayList<State>(), callStack, new Stack<Integer>(), new HashMap<State, State>());
        if (path != null) {
            List<State> loop = new ArrayList<State>();
            int st = path.size() - 2;
            while (path.get(st) != path.get(path.size() - 1))
                st--;

            for (int k = st; k < path.size(); k++) {
                loop.add(path.get(k));
            }
            return loop;
        }
        return null;
    }

    private List<State> findInfiniteLoop(State currentState, List<State> currentPath, Stack<GraphNode> callStack, Stack<Integer> pathJumpsBeginIndexes, Map<State, State> jumpProxies) {//functie recursiva pentru gasit bucle infinite
        if (currentState.index == terminalStateIndex)
            return null;

        if (verifiedStates.contains(currentState)) {
            //this state has been passed before successfully
            return null;
        }

        List<State> newPath = new ArrayList<State>(currentPath);

        State currentProxy = null;
        //handle correct jump transitions
        if (currentState.isGraphNodeEntry) {
            callStack.push(currentState.parentGraphNode);
            if (currentState.parentGraphNode == currentState.parentGraphNode.getParentGraph().getStartNode())
                pathJumpsBeginIndexes.push(newPath.size());
        } else if (currentState.isGraphNodeExit) {
            if (currentState.parentGraphNode != callStack.peek()) {
                return null; // this is not a valit transition according to jumps stack
            }
            callStack.pop();


            if (currentState.parentGraphNode == currentState.parentGraphNode.getParentGraph().getEndNode()) {//if exit from a jump, the nodes inside the jump must be treated as one empty or non empty node, to avoid false detection when recursivity is used
                int pathJumpStart = pathJumpsBeginIndexes.pop();
                boolean consumed = false;
                for (int i = pathJumpStart; i < currentPath.size(); i++) {
                    if (!newPath.get(newPath.size() - 1).condition.isEmpty)
                        consumed = true;
                    newPath.remove(newPath.size() - 1);
                }
                if (consumed) {
                    currentProxy = jumpProxies.get(newPath.get(newPath.size() - 1));
                    if (currentProxy == null) {
                        GraphNode gn = new GraphNode(grammar); // this is a proxy node for the entire sub network - since we know there is no loop inside it, we can treat it as a normal node for the search
                        if (!currentPath.isEmpty())
                            gn.setTokenMatchingCode("jump to " + currentPath.get(currentPath.size() - 1).parentGraphNode.getParentGraph().getId());
                        currentProxy = new State(this, gn);
                        currentProxy.condition.isEmpty = false;
                        jumpProxies.put(newPath.get(newPath.size() - 1), currentProxy);
                    }
                    newPath.add(currentProxy);
                }
            }
        }

        //avoid exploring non empty loops - such loops are ok
        if ((currentProxy == null || !currentPath.contains(currentProxy)) && (currentState.condition.isEmpty || !currentPath.contains(currentState))) {

            if (currentState.parentGraphNode != currentState.parentGraphNode.getParentGraph().getEndNode()) //if exited from a jump do not retain this node in the loop, to avoid false detection when recursivity is used
                newPath.add(currentState);

            if (currentState.condition.isEmpty) {// search for this empty state in the tail of the current traveled path from root
                boolean found = false;
                for (int i = currentPath.size() - 1; i >= 0; i--) {
                    if (!currentPath.get(i).condition.isEmpty) {
                        break;
                    }
                    if (currentPath.get(i) == currentState) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    return newPath;
                }
            }

            for (int childStateIndex : currentState.childStatesIndexes) {
                List<State> foundPath = findInfiniteLoop(states.get(childStateIndex), newPath, callStack, pathJumpsBeginIndexes, jumpProxies);
                if (foundPath != null)
                    return foundPath;
            }

            //this node doesn't lead to an infinite loop
            if (currentState.isGraphNodeEntry) {
                verifiedStates.add(currentState);
            }
        }
        if (currentState.isGraphNodeEntry && currentState.parentGraphNode == currentState.parentGraphNode.getParentGraph().getStartNode())
            pathJumpsBeginIndexes.pop();
        else if (currentState.isGraphNodeExit && currentState.parentGraphNode == currentState.parentGraphNode.getParentGraph().getEndNode()) //if exited from a jump, the nodes inside the jump must be ignored, to avoit false detection when recursivity is used
            pathJumpsBeginIndexes.push(currentPath.lastIndexOf(states.get(compNodes.get(currentPath.get(currentPath.size() - 1).parentGraphNode.getParentGraph().getStartNode()).startStateIndex)));

        return null;
    }

    protected void initJsEngine() throws GGSException {
        jsEngine = new ScriptEngineManager().getEngineByName("JavaScript");
        try {
            jsEngine.eval(new InputStreamReader(getClass().getResourceAsStream("jsInitCode.js")));
        } catch (ScriptException e) {
            throw new CoreCriticalException(e);
        }

        StringBuilder sb = new StringBuilder();

        sb.append("var global = {};\n\n");
        sb.append("var grammarClones = [];\n");
        sb.append("\n\n//////////////grammar code\nvar grammar = {}; var network = grammar;");
        try {
            jsEngine.eval(sb.toString());
        } catch (ScriptException e) {
            throw new CoreCriticalException(e);
        }

        sb.delete(0, sb.length());
        sb.append("\ngrammar.jsCode = function(){\n");
        if (grammar.getJsCode() != null && !grammar.getJsCode().trim().isEmpty())
            sb.append(grammar.getJsCode()).append("\n");
        sb.append("};");
        try {
            jsEngine.eval(sb.toString());
        } catch (ScriptException e) {
            throw new GrammarJsException(e.getMessage());
        }

        try {
            jsEngine.eval("grammar.jsCode()");
        } catch (ScriptException e) {
            throw new GrammarJsException(e.getMessage());
        }

        for (Graph graph : grammar.getGraphs().values()) {
            for (GraphNode gn : graph.getGraphNodes().values()) {
                if (gn == null || gn.getJsCode() == null) continue;
                if (gn.nodeType == GraphNode.NodeType.Comment) continue;

                sb.delete(0, sb.length());
                sb.append("var graph_").append(graph.getId()).append("_node_").append(gn.getIndex()).append("_jsCode = function(token){\n\t").append(gn.getJsCode()).append("\n\treturn true;\n};\n\n");
                try {
                    jsEngine.eval(sb.toString());
                } catch (ScriptException e) {
                    throw new GraphNodeJsException(e, gn);
                }
            }
        }

        resetJsVariables();
    }

    protected void setJsSentence(INlpSentence s) throws CoreCriticalException {
        try {
            jsEngine.eval("var sentence = [];");
            if ((Boolean) jsEngine.eval(String.format("sentence.positionInSentence == %d", s.getSentenceIndexInCorpus())))
                return;
            jsEngine.eval(String.format("sentence.positionInSentence = %d", s.getSentenceIndexInCorpus()));
            for (int i = 0; i< s.getTokenCount(); i++) {
                Token token = s.getToken(i);
                jsEngine.eval(String.format("sentence[%d] = %s;", token.getTokenIndexInSentence(), tokenToJson(token)));
            }

            jsEngine.eval("sentence.spanAnnotations = []");
            for (SpanAnnotation annotation : s.getSpanAnnotations()) {
                jsEngine.eval(String.format("sentence.spanAnnotations.push(%s);", annotationToJson(annotation)));

                for (int i = annotation.getStartTokenIndex(); i <= annotation.getEndTokenIndex(); i++) {
                    jsEngine.eval(String.format("sentence[%s].parentAnnotations.push(sentence.spanAnnotations[sentence.spanAnnotations.length - 1]);", i));
                }
            }

        } catch (ScriptException e) {
            throw new CoreCriticalException(e);
        }
    }

    protected void resetJsVariables() throws CoreCriticalException {
        StringBuilder sb = new StringBuilder();

        for (Graph graph : grammar.getGraphs().values()) {
            sb.append("//////////////// Graph ").append(graph.getId()).append("\n");
            sb.append("grammar.graph_").append(graph.getId()).append(" = {};\n");
            sb.append("\n");
        }

        sb.append("\n\n\n");
        sb.append("var graphsJumpClones = {}; //this is used as an array of stacks of clones of graph. When jumping from a graph, its state is maintained in this structure, and it is restored when coming back to it\n");
        for (Graph graph : grammar.getGraphs().values()) {
            sb.append("graphsJumpClones['").append(graph.getId()).append("']").append(" = [];\n");
            sb.append("graphsJumpClones['").append(graph.getId()).append("']").append(".push(clone(grammar.graph_").append(graph.getId()).append(",true));\n");
        }


        try {
            jsEngine.eval(sb.toString());
        } catch (ScriptException e) {
            throw new CoreCriticalException(e);
        }
    }

    static String evalJsFragments(String code, State state) throws GraphNodeVarException, CoreCriticalException {
        Matcher matcher = GraphNode.jsVarsPattern.matcher(code);
        StringBuilder sb = new StringBuilder();
        int lastOffset = 0;
        while (matcher.find()) {
            String var = matcher.group().substring(1, matcher.group().length() - 1);
            try {
                state.stateMachine.jsEngine.eval(String.format("grammar.graph_%s.__eval = function (){return %s;}",
                        state.parentGraphNode.getParentGraph().getId(),
                        var));
            } catch (ScriptException e) {
                throw new GraphNodeVarException(e, var, state.parentGraphNode);
            }

            try {
                sb.append(code.substring(lastOffset, matcher.start())).append(state.stateMachine.jsEngine.eval(String.format("grammar.graph_%s.__eval()",
                        state.parentGraphNode.getParentGraph().getId())));
            } catch (ScriptException e) {
                throw new GraphNodeVarException(e, var, state.parentGraphNode);
            }
            lastOffset = matcher.end();
        }

        try {
            state.stateMachine.jsEngine.eval(String.format("delete grammar.graph_%s.__eval;", state.parentGraphNode.getParentGraph().getId()));
        } catch (ScriptException e) {
            throw new CoreCriticalException(e);
        }

        sb.append(code.substring(lastOffset));
        return sb.toString();
    }
}