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
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import javax.script.ScriptException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

class AssertionCondition {
    public StateMachine stateMachine;
    public int startStateIndex;
    GraphNode assertionNode;

    public AssertionCondition(GraphNode gn) {
        assertionNode = gn;
    }

    protected void compile(StateMachine mainMachine) throws GGSException {
        GraphNode actualAssertionNode = assertionNode;
        if (assertionNode.nodeType == GraphNode.NodeType.LookBehind) {
            if (mainMachine.mirroredMachine == null)
                mainMachine.compileMirrored();
            actualAssertionNode = mainMachine.mapToMirrored.get(assertionNode);
            mainMachine = mainMachine.mirroredMachine;
        }

        stateMachine = mainMachine;
        if (!stateMachine.compNodes.containsKey(actualAssertionNode))//this covers the case of cross ref assertions which are called from a mirrored state machine - they use the original state machine
            stateMachine = mainMachine.mirroredMachine;
        startStateIndex = stateMachine.compNodes.get(actualAssertionNode).startStateIndex;
//        stateMachine = new StateMachine(mainMachine.compiledGrammar);
//        stateMachine.states = mainMachine.states;
//        stateMachine.graphsContainingJsCode = mainMachine.graphsContainingJsCode;
//        stateMachine.startStateIndex = mainMachine.compNodes.get(actualAssertionNode).startStateIndex;
//        stateMachine.jsEngine = mainMachine.jsEngine;
        //discover the terminal node

        GraphNode assertionEndNode = findAssertionTerminalNode(actualAssertionNode, new HashSet<GraphNode>());
        if (assertionEndNode == null) {
            throw new GGSTerminalNodeNotFoundException(assertionNode);
        }
    }

    public boolean Matches(INlpSentence s, int offset, boolean indexedSearch, SearchSpaceReducer.PTAcache cache) throws GGSException, IOException {
        if (offset < 0)
            return assertionNode.isNegativeAssertion;
        if (offset >= s.getTokenCount())
            return assertionNode.isNegativeAssertion;
        boolean rez = false;
        int prevTokIndex = -1; //preserve token js variable for nested assertions
        if (stateMachine.jsEngine != null) {
            try {
                prevTokIndex = (Integer) stateMachine.jsEngine.eval("(typeof assertionTokenIndex === 'undefined') ? -1 :assertionTokenIndex");
                stateMachine.jsEngine.eval(String.format("var assertionTokenIndex = %d", offset - 1));
                stateMachine.jsEngine.eval("var assertionToken = sentence[assertionTokenIndex]");
            } catch (ScriptException e) {
                throw new CoreCriticalException(e);
            }
        }

        try {
            if (assertionNode.nodeType == GraphNode.NodeType.CrossReference)
                rez = stateMachine.run(startStateIndex, s, 0, true, indexedSearch, cache).size() > 0;
            else
                rez = stateMachine.run(startStateIndex, s.getToken(offset), 0, true, indexedSearch, cache) != null;
        } catch (GGSOutputMalformatException e) {
            e.printStackTrace();  //assertions should not output anyway...
        }
        if (assertionNode.isNegativeAssertion) rez = !rez;

        if (stateMachine.jsEngine != null) {
            try {
                if (prevTokIndex == -1)
                    stateMachine.jsEngine.eval("delete assertionToken; delete assertionTokenIndex");
                else
                    stateMachine.jsEngine.eval("assertionTokenIndex = " + prevTokIndex + "; assertionToken = sentence[assertionTokenIndex]");
            } catch (ScriptException e) {
                throw new CoreCriticalException(e);
            }
        }
        return rez;
    }

    private GraphNode findAssertionTerminalNode(GraphNode assertionNode, Set<GraphNode> alreadyChecked) {
        if (assertionNode.nodeType == GraphNode.NodeType.Comment) return null;
        if (alreadyChecked.contains(assertionNode))
            return null;
        alreadyChecked.add(assertionNode);

        if (assertionNode.nodeType == GraphNode.NodeType.AssertionEnd) {
            return assertionNode;
        } else {
            //assertion endnodes are optional
            int count = 0;
            for (int i : assertionNode.getChildNodesIndexes()) {
                if (!assertionNode.getParentGraph().getGraphNodes().get(i).isAssertion() && !assertionNode.getParentGraph().getGraphNodes().get(i).isComment())
                    count++;
            }
            if (count == 0)
                return assertionNode;

            for (int i : assertionNode.getChildNodesIndexes()) {

                GraphNode gn = findAssertionTerminalNode(assertionNode.getParentGraph().getGraphNodes().get(i), alreadyChecked);
                if (gn != null)
                    return gn;
            }
        }
        return null;
    }
}
