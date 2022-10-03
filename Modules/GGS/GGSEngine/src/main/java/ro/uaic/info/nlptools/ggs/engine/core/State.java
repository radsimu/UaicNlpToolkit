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
import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.ggs.engine.SparseBitSet;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import javax.script.ScriptException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class State {
    static String jumpParametersToLoad; //TODO: move this as instance field somewhere... check for other such erroneous static fields in the entire project!
    /**
     * start to match from this state and a given startPositionInSentence
     */
    private static INlpSentence lastS = null;
    public long index;
    public OutputAnnotator annotator;
    public List<Integer> childStatesIndexes;
    public List<Integer> parentStatesIndexes;
    public TransitionCondition condition;
    public List<AssertionCondition> assertions;
    public StateMachine stateMachine;
    public boolean searchForLongestMatch = true;
    public GraphNode parentGraphNode; // the graph node that generated this state on compilation
    public boolean isGraphNodeEntry = false; // flag for state that represent the entering in a graph node. To facilitate proper jumping and returning graph node entry states are added to a stack. When passing through graph node exit states, the identity of the parent graph node is confronted, ensuring that a jump has returned to the correct state.
    public boolean isGraphNodeExit;
    protected List<String> jumpParameters; //used for sending parameters when jumping to other graphs
    boolean lookBehind = false;

    public State(StateMachine stateMachine, GraphNode parent) {
        this.parentGraphNode = parent;
        this.stateMachine = stateMachine;
        childStatesIndexes = new ArrayList<Integer>();
        parentStatesIndexes = new ArrayList<Integer>();
        condition = new TransitionCondition("<<E>>", this);
        assertions = new ArrayList<AssertionCondition>();
    }

    public State(String cond, StateMachine stateMachine, GraphNode parent) {
        this.parentGraphNode = parent;
        this.stateMachine = stateMachine;
        childStatesIndexes = new ArrayList<Integer>();
        parentStatesIndexes = new ArrayList<Integer>();
        if (parent.isAssertion() || parent.nodeType == GraphNode.NodeType.AssertionEnd) {
            cond = "<<E>>";
        }
        condition = new TransitionCondition(cond, this);
        assertions = new ArrayList<AssertionCondition>();
    }

    protected Match Match(INlpSentence s, int offset, long terminalStateIndex, int consumeLimit, boolean assertionMatch, boolean indexedSearch, SearchSpaceReducer.PTAcache ptaCache) throws GGSException, IOException {
        //reset bindings for the jsEngine if this is not an assertion match
        if (!assertionMatch && stateMachine.usesJs()) {
            stateMachine.resetJsVariables();
            if (lastS != s) {
                stateMachine.setJsSentence(s);
                lastS = s;
            }
        }

        Match sofar = new Match();
        sofar.sentence = s;
        sofar.startPositionInSentence = offset;
        Stack<State> statesStack = new Stack<State>();


        sofar = Match(s, offset, sofar, new Stack<GraphNode>(), new Stack<GraphNode>(), statesStack, terminalStateIndex, consumeLimit, indexedSearch, ptaCache);

        if (sofar != null) {
            for (SpanAnnotation a : sofar.getSpanAnnotations()) {
                if (a.getEndTokenIndex() == -1 || a.getStartTokenIndex() == -1)
                    throw new GGSOutputMalformatException(a);
            }

            sofar.matchedGraphNodesPath = new ArrayList<>();
            for (int i = 0; i < statesStack.size(); i++) {
                if (statesStack.get(i).isGraphNodeEntry) {
                    sofar.matchedGraphNodesPath.add(statesStack.get(i).parentGraphNode);
                }
            }
        }

        return sofar;
    }

    /**
     * @param s                  the sentence
     * @param offset             the startPositionInSentence of the token to be matched
     * @param sofar              the matched sequence of tokens so far
     * @param jumpsStack         Stack of jump nodes till current moment. The top of the stack is the current GraphNode
     * @param graphNodesStack    Stack representing the path from the starting node to the current node (the parent graph nodes of the matchingStatesPath)
     * @param matchingStatesPath path of the states which matched sofar (when backtracking while having reached the final state, states won't be poped.
     * @param terminalStateIndex used to identify the final state
     * @param consumeLimit       a limit on the number of tokens which can be matched
     * @return the Match object or null
     * @throws javax.script.ScriptException
     */

    protected Match Match(INlpSentence s, int offset, Match sofar, Stack<GraphNode> jumpsStack, Stack<GraphNode> graphNodesStack, Stack<State> matchingStatesPath, long terminalStateIndex, int consumeLimit, boolean indexedSearch, SearchSpaceReducer.PTAcache ptaCache) throws GGSException, IOException {
        if (consumeLimit > 0 && sofar.size() > consumeLimit)
            return null;
        if (ptaCache != null && offset >= 0 && offset < s.getTokenCount()) {
            Map<String, Pair<SparseBitSet, SparseBitSet>> cache = ptaCache.cache.get(this);
            if (cache != null) {
                Pair<SparseBitSet, SparseBitSet> cachee = cache.get(SearchSpaceReducer.PTAcache.keyForJumpStack(jumpsStack));
                int pos = s.getToken(offset).getTokenIndexInCorpus();
                if (cachee != null && cachee.getKey().get(pos) && !cachee.getValue().get(pos)) {
                    return null; //the ptaCache suggests that it is useless to continue on this path
                }
            }
        }

        matchingStatesPath.push(this);
        if (isGraphNodeEntry)
            graphNodesStack.push(parentGraphNode);

        //executing the js code
        boolean backTrack = false;
        if (stateMachine.usesJs() && stateMachine.graphsContainingJsCode.contains(parentGraphNode.getParentGraph())) {
            if (isGraphNodeEntry && parentGraphNode.getParentGraph().getStartNode() == parentGraphNode) {//if it is the entry state in a graph, init some stuff like the graph and agruments variables, update grammarClones etc
                try {
                    //handle cloning of the graph states by adding them to a stack to be restored when exiting from this graph
                    stateMachine.jsEngine.eval(String.format("graphsJumpClones['%s'].push(clone(grammar.graph_%s))", parentGraphNode.getParentGraph().getId(), parentGraphNode.getParentGraph().getId()));
                    //also clear any variables for this graph
                    stateMachine.jsEngine.eval(String.format("clearGraph(grammar.graph_%s)", parentGraphNode.getParentGraph().getId()));
                } catch (ScriptException e) {
                    throw new CoreCriticalException(e);
                }
                if (jumpParametersToLoad != null)
                    try {
                        stateMachine.jsEngine.eval(String.format("grammar.graph_%s.arguments = %s;", parentGraphNode.getParentGraph().getId(), jumpParametersToLoad));
                    } catch (ScriptException e) {
                        throw new GraphNodeJumpArgsException(e, parentGraphNode, jumpParametersToLoad);
                    }
                else
                    try {
                        stateMachine.jsEngine.eval(String.format("grammar.graph_%s.arguments = [];", parentGraphNode.getParentGraph().getId()));
                    } catch (ScriptException e) {
                        throw new CoreCriticalException(e);
                    }
            }

            if (isGraphNodeExit && parentGraphNode.getJsCode() != null) {//run node js code
                try {
                    stateMachine.jsEngine.eval(String.format("graph = grammar.graph_%s", parentGraphNode.getParentGraph().getId()));
                    stateMachine.jsEngine.eval("grammarClones.push(clone(grammar))");
                } catch (ScriptException e) {
                    throw new CoreCriticalException(e);
                }

                boolean contin;
                try {
                    contin = (Boolean) stateMachine.jsEngine.eval(String.format("graph_%s_node_%d_jsCode(sentence[%d])",
                            parentGraphNode.getParentGraph().getId(),
                            parentGraphNode.getIndex(),
                            offset - 1));
                } catch (ScriptException e) {
                    throw new GraphNodeJsException(e, parentGraphNode);
                }

                if (!contin)
                    backTrack = true;
            }

            if (!backTrack && isGraphNodeExit && parentGraphNode.getParentGraph().getEndNode() == parentGraphNode) { //exit from a graph
                try {
                    stateMachine.jsEngine.eval(String.format("restoreGraph(grammar.graph_%s, graphsJumpClones['%s'].pop())", parentGraphNode.getParentGraph().getId(), parentGraphNode.getParentGraph().getId()));
                    stateMachine.jsEngine.eval(String.format("graph = grammar.graph_%s", parentGraphNode.getParentGraph().getId()));
                } catch (ScriptException e) {
                    throw new CoreCriticalException(e);
                }
            }
        }

        if (!backTrack && this.isGraphNodeExit) {
            //when the matching process leaves a graph (after entering it due to a jump), it must find the correct state to return to.
            //this is achieved by checking that the parent node of the graph node entry state, which is pushed in the jumpsStack, is equal to the parent graph node of this node exit state.
            if (parentGraphNode != jumpsStack.peek()) {
                matchingStatesPath.pop();
                return null;
            }
            jumpsStack.pop();
        }

        if (!backTrack && isGraphNodeEntry) {
            jumpsStack.push(parentGraphNode);
        }

        //here we check if the conditions of the state are met
        //note that only middle states don't have condition.isEmpty
        Match rez = new Match(sofar);

        int consume = 0;

        boolean conditionsPassed = true;
        if (!backTrack && !condition.isEmpty) {
            consume = lookBehind ? -1 : 1;
            if (!condition.isEmpty && ((!lookBehind && s.getTokenCount() > offset) || (lookBehind && 0 <= offset)) &&
                    (offset >= 0 && offset < s.getTokenCount() && condition.Matches(s.getToken(offset)))) {
                if (condition.matchedAnnotation != null) {//this has consumed an spanAnnotation
                    for (int i = condition.matchedAnnotation.getStartTokenIndex(); i <= condition.matchedAnnotation.getEndTokenIndex(); i++)
                        rez.add(s.getToken(i));
                    if (lookBehind) {
                        consume = condition.matchedAnnotation.getEndTokenIndex() - condition.matchedAnnotation.getStartTokenIndex() - 1;
                    } else
                        consume = condition.matchedAnnotation.getEndTokenIndex() - condition.matchedAnnotation.getStartTokenIndex() + 1;
                } else
                    rez.add(s.getToken(offset));
            } else {//condition not fulfilled
                conditionsPassed = false;
            }
        }
        if (!backTrack && conditionsPassed)//test assertions
            for (AssertionCondition assertion : assertions) {
                int offsetForAssertionCheck = offset;
                if (assertion.assertionNode.nodeType == GraphNode.NodeType.LookBehind)//if this is a lookbehind assertion, check by trying to consume backwards starting with the previous position
                    offsetForAssertionCheck += lookBehind ? 1 : -1; //if this is lookbehind assertion in a mirrored state machine, must start with the next position, actually

                if (!assertion.Matches(s, offsetForAssertionCheck, indexedSearch, ptaCache)) {
                    conditionsPassed = false;
                    break;
                }
            }

        //here we make recursive jumps to the child states
        Match nextRez = null;
        if (!backTrack && conditionsPassed) {
            //process output code, if present
            if (annotator != null) {
                annotator.Apply(rez);
            }

            if (!backTrack && jumpsStack.isEmpty()) {
                boolean hasTransitionsToOtherNodes = false;
                for (int childIndex : childStatesIndexes)
                    if (!stateMachine.states.get(childIndex).isGraphNodeExit) {
                        hasTransitionsToOtherNodes = true;
                        break;
                    }
                if (!hasTransitionsToOtherNodes) {
                    jumpsStack.push(parentGraphNode); //pushing back the current graph node before returning because it will be popped when the node entry state of the final node of main is reached while back tracking
                    return rez;
                }
            }

            int k = 0;

            //if a path priority is provided
            if (!searchForLongestMatch || childStatesIndexes.size() < 2) {
                for (int childStateIndex : childStatesIndexes) {
                    if (stateMachine.requestStop)
                        break;
                    if (jumpParameters != null && k < jumpParameters.size() && jumpParameters.get(k) != null) //it is about to make a jump. Must set graph parameters if it is the case
                        jumpParametersToLoad = jumpParameters.get(k);
                    else
                        jumpParametersToLoad = null;

                    nextRez = stateMachine.states.get(childStateIndex).Match(s, offset + consume, rez, jumpsStack, graphNodesStack, matchingStatesPath, terminalStateIndex, consumeLimit, indexedSearch, ptaCache);
                    if (nextRez != null) {
                        break;
                    }
                    k++;
                }
            }
        }

        //this point is reached when coming back from the recursive calls (above),
        //before returning back to the state from which this was called

        //we modify the stacks
        if (isGraphNodeEntry) {//if it was a graph node entry state
            //remove it from the jumpsStack (because it was pushed before the recursive calls
            jumpsStack.pop();
            graphNodesStack.pop();

            if (stateMachine.usesJs() && parentGraphNode.getParentGraph().getStartNode() == parentGraphNode && stateMachine.graphsContainingJsCode.contains(parentGraphNode.getParentGraph())) {//if it is the entry state in a graph revert graph and agruments variables
                try {
                    stateMachine.jsEngine.eval(String.format("restoreGraph(grammar.graph_%s, graphsJumpClones['%s'].pop())", parentGraphNode.getParentGraph().getId(), parentGraphNode.getParentGraph().getId()));
                    stateMachine.jsEngine.eval(String.format("graph = grammar.graph_%s", parentGraphNode.getParentGraph().getId()));
                } catch (ScriptException e) {
                    throw new CoreCriticalException(e);
                }
            }
        }
        if (isGraphNodeExit) {//if it is a graph node exit state, push it back in the jumpsStack (because it was poped before the recursive calls)
            jumpsStack.push(parentGraphNode);
            if (stateMachine.usesJs() && parentGraphNode.getParentGraph().getEndNode() == parentGraphNode)
                if (stateMachine.graphsContainingJsCode.contains(parentGraphNode.getParentGraph())) {//if it is the exit state in a graph bring back local variables
                    try {
                        stateMachine.jsEngine.eval(String.format("restoreGraph(grammar.graph_%s, grammarClones[grammarClones.lenght - 1]);", parentGraphNode.getParentGraph().getId(), parentGraphNode.getParentGraph().getId()));
                        stateMachine.jsEngine.eval(String.format("graph = grammar.graph_%s", parentGraphNode.getParentGraph().getId()));
                    } catch (ScriptException e) {
                        throw new CoreCriticalException(e);
                    }
                }

            if (stateMachine.usesJs() && stateMachine.graphsContainingJsCode.contains(parentGraphNode.getParentGraph())) {
                if (parentGraphNode.getJsCode() != null) //it means that some js code was processed, which now needs to be reverted, if no match was found
                    try {
                        if (nextRez == null) {
                            stateMachine.jsEngine.eval("grammar = grammarClones.pop()");
                        } else {
                            stateMachine.jsEngine.eval("grammarClones.pop()");
                        }
                    } catch (ScriptException e) {
                        throw new CoreCriticalException(e);
                    }
            }
        }
        if (nextRez == null)
            matchingStatesPath.pop();

        return nextRez;
    }

    public void Compile() throws GraphNodeMatchingSyntaxException, GraphNodeOutputSyntaxException {
        condition.Compile();
        if (parentGraphNode.getOutputCode() != null && (isGraphNodeEntry || isGraphNodeExit)) {
            annotator = new OutputAnnotator(this, parentGraphNode.getOutputCode());
            annotator.Compile();
        }
    }

    public String toString() {
        String position;
        if (isGraphNodeEntry) position = "Start of ";
        else if (isGraphNodeExit) position = "End of ";
        else position = "Middle of ";
        return position + parentGraphNode.toString();
    }
}