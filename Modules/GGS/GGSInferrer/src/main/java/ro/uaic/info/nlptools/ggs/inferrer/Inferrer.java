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

package ro.uaic.info.nlptools.ggs.inferrer;

import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.ggs.engine.core.GenericGraphNode;
import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import ro.uaic.info.nlptools.ggs.engine.grammar.Graph;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import java.util.*;

public class Inferrer {

    //for now use only one xml argument
    public static String attrib = "ana";
    static Map<GraphNode, Set<GraphNode>> alldescendants = new HashMap<GraphNode, Set<GraphNode>>();
    int i = 0;
    Set<String> alreadyCompared = new HashSet<String>();
    Map<GenericGraphNode<Token>, GraphNode> alreadyBuiltGraphNodes = new HashMap<GenericGraphNode<Token>, GraphNode>();
    Map<GraphNode, Integer> alreadyPositioned = new HashMap<GraphNode, Integer>();
    int ySpace = 40;
    int xSpace = 120;
    int margin = 50;

    public static GenericGraphNode<Token> ClonePta(GenericGraphNode<Token> pta, Map<GenericGraphNode<Token>, GenericGraphNode<Token>> clones, Map<GenericGraphNode<Token>, GenericGraphNode<Token>> alreadyCloned) {
        if (alreadyCloned.containsKey(pta))
            return (alreadyCloned.get(pta));
        GenericGraphNode<Token> clone = clones.get(pta);
        alreadyCloned.put(pta, clone);
        for (GenericGraphNode<Token> child : pta.children) {
            clone.AddChild(ClonePta(child, clones, alreadyCloned));
        }
        return clone;
    }

    public static boolean PTAMatchesSample(GenericGraphNode<Token> pta, List<Token> sequence, int depth) {
        if (pta.userObject != null && !TokenMatchesGenericGraphNode(pta.userObject, sequence.get(depth)))
            return false;

        boolean rez = true;
        for (GenericGraphNode<Token> child : pta.children) {
            rez = rez && PTAMatchesSample(child, sequence, depth + 1);
            if (!rez)
                break;
        }
        rez = rez && pta.isLeaf && depth == sequence.size() - 1;
        return rez;
    }

    public static List<List<Token>> ExtractConflictingNegativeExamples(List<List<Token>> positiveExamples, List<List<Token>> negativeExamples) {
        Set<String> hashes = new HashSet<String>();
        for (List<Token> positiveExample : positiveExamples) {
            StringBuilder sb = new StringBuilder();
            for (Token t : positiveExample)
                sb.append(tokenHash(t)).append(" ");
            hashes.add(sb.toString());
        }

        List<List<Token>> conflictingNegativeExamples = new ArrayList<List<Token>>();

        for (List<Token> negativeExample : negativeExamples) {
            StringBuilder sb = new StringBuilder();
            for (Token t : negativeExample)
                sb.append(tokenHash(t)).append(" ");
            if (hashes.contains(sb.toString()))
                conflictingNegativeExamples.add(negativeExample);
        }
        return conflictingNegativeExamples;
    }

    private static boolean TokenMatchesGenericGraphNode(Token generic, Token actual) {
        for (Map.Entry<String, String> genericEntry : generic.getFeatures().entrySet()) {
            if (!genericEntry.getValue().equals(actual.getFeatures().get(genericEntry.getKey())))
                return false;
        }
        return true;
    }

    public List<List<Token>> ExtractExampleSequencesFromCorpus(InmemoryCorpus inputText) {
        List<List<Token>> exampleSequences = new ArrayList<List<Token>>();

        for (int k = 0; k < inputText.getSentenceCount(); k++) {
            INlpSentence s = inputText.getSentence(k);

            if (s.getSpanAnnotations().size() > 0) {
                SpanAnnotation annotation = null;
                List<Token> mySequence = null;
                boolean skipLast = false;
                for (int i = 0; i < s.getTokenCount(); i++) {
                    if (s.getToken(i).getParentSpanAnnotations().size() > 0) {
                        if (s.getToken(i).getFeatures().get(attrib).equals("V") ||
                                s.getToken(i).getFeatures().get(attrib).equals("PRONREL"))
                            skipLast = true;
                        if (annotation != s.getToken(i).getParentSpanAnnotations().get(0)) {
                            if (mySequence != null && !skipLast)
                                exampleSequences.add(mySequence);

                            skipLast = false;
                            mySequence = new ArrayList<Token>();
                            annotation = s.getToken(i).getParentSpanAnnotations().get(0);
                        }
                        mySequence.add(s.getToken(i));
                    } else if (annotation != null) {
                        if (!skipLast)
                            exampleSequences.add(mySequence);
                        mySequence = null;
                        annotation = null;
                    }
                }
            }
        }
        return exampleSequences;
    }

    public Grammar inferFromAnnotatedCorpus(InmemoryCorpus inputText) {
        return inferFromAnnotatedCorpus(inputText, -1);
    }

    public Grammar inferFromAnnotatedCorpus(InmemoryCorpus inputText, int itterations) {

        return inferFromExampleSet(ExtractExampleSequencesFromCorpus(inputText), null, itterations, true);
    }

    public Grammar inferFromExampleSet(List<List<Token>> exampleSequences) {
        return inferFromExampleSet(exampleSequences, null, -1, false);
    }

    public Grammar inferFromExampleSet(List<List<Token>> exampleSequences, List<List<Token>> negativeExamples) {
        return inferFromExampleSet(exampleSequences, negativeExamples, -1, false);
    }

    public Grammar inferFromExampleSet(List<List<Token>> exampleSequences, List<List<Token>> negativeExamples, int itterations, boolean foldAfterEachStep) {//if not fold after each step, it will minimize after each step
        Comparator<Token> comparator = new Comparator<Token>() {
            @Override
            public int compare(Token token1, Token token2) {
                if (token1 == token2) return 0;
                if (token1 == null || token2 == null) return -1;
                String attrib1 = token1.getFeatures().get(attrib);
                String attrib2 = token2.getFeatures().get(attrib);
                if (attrib1 == null || attrib2 == null) return -1;
                return attrib1.equals(attrib2) ? 0 : -1;
            }
        };

        //do something about conflicting negative examples
        //for now, just ignore them, in the future try to use additional attributes
        if (negativeExamples != null) {
            List<List<Token>> conflictingNegativeExamples = ExtractConflictingNegativeExamples(exampleSequences, negativeExamples);
            for (List<Token> conflict : conflictingNegativeExamples)
                negativeExamples.removeAll(conflictingNegativeExamples);
        }

        GenericGraphNode<Token> ptaTrie = buildPTAfromSamples(exampleSequences);
        Set<GenericGraphNode<Token>> allPtaNodes = new HashSet<GenericGraphNode<Token>>();
        grabNodes(ptaTrie, allPtaNodes);
        GenericGraphNode<GenericGraphNode<Token>> superTrie = buildSuperTrie(ptaTrie, comparator);
        Map<Float, List<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>>> simmilarGraphs = findSimmilarGraphs(superTrie, comparator);
        i = 0;
        int iters = itterations;
        Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedNodes = new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>(); //used by findActualNode for finding nodes after they have been merged
        for (Map.Entry<Float, List<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>>> pairs : simmilarGraphs.entrySet()) {
            for (AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>> pair : pairs.getValue()) {
                if (i == iters) break;
                GenericGraphNode<Token> node1 = findActualNode(pair.getKey().get(0), mergedNodes);
                GenericGraphNode<Token> node2 = findActualNode(pair.getValue().get(0), mergedNodes);
                if (node1 == node2)
                    continue;
                boolean ok = true;
                if (negativeExamples != null && negativeExamples.size() > 0) {
                    Map<GenericGraphNode<Token>, GenericGraphNode<Token>> clones = new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>();
                    for (GenericGraphNode<Token> graphNode : allPtaNodes)
                        clones.put(graphNode, graphNode.clone());

                    GenericGraphNode<Token> ptaClone = ClonePta(ptaTrie, clones, new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>());

                    Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedNodesClone = new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>();
                    for (Map.Entry<GenericGraphNode<Token>, GenericGraphNode<Token>> entry : mergedNodes.entrySet())
                        mergedNodesClone.put(clones.get(entry.getKey()), clones.get(entry.getValue()));

                    if (foldAfterEachStep)
                        mergeSubGraphs(clones.get(node1), clones.get(node2), comparator, mergedNodesClone);
                    else {
                        mergeGraphNodes(node1, node2, mergedNodesClone);
                        minimizeGraph(ptaTrie);
                    }

                    for (List<Token> negativeExample : negativeExamples)
                        if (!PTAMatchesSample(ptaClone, negativeExample, 0)) {
                            ok = false;
                            break;
                        }
                }

                if (ok) {
                    if (foldAfterEachStep)
                        mergeSubGraphs(node1, node2, comparator, mergedNodes);
                    else {
                        mergeGraphNodes(node1, node2, mergedNodes);
                        minimizeGraph(ptaTrie);
                    }
                }

                i++;
            }
            if (i == iters) break;
        }

        Grammar g = buildGraphStumpFromTokenTrie(ptaTrie);


        if (iters == -1) {
            Graph main = g.getGraphs().get("Main");
            int prevSize;
            do {
                prevSize = main.getGraphNodes().size();
                resolveCrossLinks(g);
            } while (prevSize != main.getGraphNodes().size());
        }
        formatGrammar(g);
        return g;
    }

    private GenericGraphNode<Token> findActualNode(GenericGraphNode<Token> node, Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedNodes) {
        if (mergedNodes == null)
            return node;
        GenericGraphNode<Token> mergedNode;
        while (true) {// a node might have been merged multiple times
            mergedNode = mergedNodes.get(node);
            if (mergedNode == null || mergedNode == node)
                return node;
            node = mergedNode;
        }
    }

    //build a supertrie - a trie on sequences from the PTA, used for finding similiar sequences in a trie - this is used for finding similar subsequences in the examples set
    private GenericGraphNode<GenericGraphNode<Token>> buildSuperTrie(GenericGraphNode<Token> samplesTrie, final Comparator<Token> comparator) {
        GenericGraphNode<GenericGraphNode<Token>> superTrie = new GenericGraphNode<GenericGraphNode<Token>>();

        buildSuperTrieRecursive(superTrie, samplesTrie, new ArrayList<GenericGraphNode<Token>>(), new Comparator<GenericGraphNode<Token>>() {
            @Override
            public int compare(GenericGraphNode<Token> tokenGenericGraphNode, GenericGraphNode<Token> tokenGenericGraphNode2) {
                return comparator.compare(tokenGenericGraphNode.userObject, tokenGenericGraphNode2.userObject);
            }
        });
        return superTrie;
    }

    private Map<Float, List<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>>> findSimmilarGraphs(GenericGraphNode<GenericGraphNode<Token>> superTrie, Comparator<Token> comparator) {
        Map<Float, List<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>>> sortedResultsBySize = new TreeMap<Float, List<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>>>(new Comparator<Float>() {
            @Override
            public int compare(Float aFloat, Float aFloat2) {
                return (int) Math.signum(aFloat2 - aFloat);
            }
        });
        findSimmilarGraphsRecursive(sortedResultsBySize, superTrie, 0, comparator);
        return sortedResultsBySize;
    }

    private void findSimmilarGraphsRecursive(Map<Float, List<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>>> sortedResultsBySimilarityScore, GenericGraphNode<GenericGraphNode<Token>> superGenericGraphNode, int depth, Comparator<Token> comparator) {
        int sum = 0;
        if (superGenericGraphNode.utterrances.size() > 0 || superGenericGraphNode.parents.size() == 0) {
//            for (GenericGraphNode<GenericGraphNode<Token>> superGenericGraphNodeChild : superGenericGraphNode.children) {
//                sum += superGenericGraphNodeChild.utterrances.size();
//            }
//            if (sum < superGenericGraphNode.utterrances.size()) //this means that at least one utterance ends here
            {
                for (int i = 0; i < superGenericGraphNode.utterrances.size() - 1; i++)
                    for (int j = i + 1; j < superGenericGraphNode.utterrances.size(); j++) {
                        if (superGenericGraphNode.utterrances.get(i).getKey().get(0) == superGenericGraphNode.utterrances.get(j).getKey().get(0))
                            continue; // if the two chains which are to be compared from the pta(found in the utterances of the branchSoFar) have a common pta node in the beggining,
                        // they must be ignored because they will yeld a very high similarity score, and they are already partially merged - we want to merge only the subgraphs which have distinct roots. We will actually merge the roots and then fold

                        List<GenericGraphNode<Token>> A = new ArrayList<GenericGraphNode<Token>>();
                        List<GenericGraphNode<Token>> B = new ArrayList<GenericGraphNode<Token>>();
                        StringBuilder pairUniqueId = new StringBuilder();//based on contents of A and B

                        for (int k = 0; k < depth; k++) {
                            A.add(superGenericGraphNode.utterrances.get(i).getKey().get(k));
                            B.add(superGenericGraphNode.utterrances.get(j).getKey().get(k));
                            pairUniqueId.append(superGenericGraphNode.utterrances.get(i).getKey().get(k).hashCode());
                            pairUniqueId.append(superGenericGraphNode.utterrances.get(j).getKey().get(k).hashCode());
                        }
                        //if this pair has already been handled, ignore and continue
                        if (alreadyCompared.contains(pairUniqueId.toString()))
                            continue;

                        alreadyCompared.add(pairUniqueId.toString());

                        float similarityScore = similarityMeasure(A, B, comparator);

                        List<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>> chainPairsWithThisLength = sortedResultsBySimilarityScore.get(similarityScore);
                        if (chainPairsWithThisLength == null) {
                            chainPairsWithThisLength = new ArrayList<AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>>();
                            sortedResultsBySimilarityScore.put(similarityScore, chainPairsWithThisLength);
                        }
                        chainPairsWithThisLength.add(new AbstractMap.SimpleEntry<List<GenericGraphNode<Token>>, List<GenericGraphNode<Token>>>(A, B));
                    }
            }

            for (GenericGraphNode<GenericGraphNode<Token>> superGenericGraphNodeChild : superGenericGraphNode.children) {
                findSimmilarGraphsRecursive(sortedResultsBySimilarityScore, superGenericGraphNodeChild, depth + 1, comparator);
            }
        }
    }

    private void buildSuperTrieRecursive(GenericGraphNode<GenericGraphNode<Token>> superTrie, GenericGraphNode<Token> samplesGenericGraphNode, List<GenericGraphNode<Token>> currentBranchToleaf, Comparator<GenericGraphNode<Token>> comparator) {
        if (samplesGenericGraphNode.parents.size() != 0)//not root
            currentBranchToleaf.add(samplesGenericGraphNode);
        if (samplesGenericGraphNode.children.size() == 0)
            while (currentBranchToleaf.size() > 0) {
                superTrie.AddSequence(currentBranchToleaf, comparator);
                currentBranchToleaf = new ArrayList<GenericGraphNode<Token>>(currentBranchToleaf);
                currentBranchToleaf.remove(0);
            }
        else
            for (GenericGraphNode<Token> samplesGenericGraphNodeChild : samplesGenericGraphNode.children) {
                List<GenericGraphNode<Token>> currentBranchToleafClone = currentBranchToleaf;
                if (samplesGenericGraphNode.children.size() > 1)
                    currentBranchToleafClone = new ArrayList<GenericGraphNode<Token>>(currentBranchToleaf);
                buildSuperTrieRecursive(superTrie, samplesGenericGraphNodeChild, currentBranchToleafClone, comparator);
            }
    }

    private GenericGraphNode<Token> buildPTAfromSamples(List<List<Token>> tokenSequences) {
        GenericGraphNode<Token> pta = new GenericGraphNode<Token>();
        for (List<Token> tokenSequence : tokenSequences) {
            //add this sequence to trie
            pta.AddSequence(tokenSequence, new Comparator<Token>() {
                @Override
                public int compare(Token token, Token token2) {
                    return token.getFeatures().get(attrib).equals(token2.getFeatures().get(attrib)) ? 0 : -1;
                }
            });
        }
        return pta;
    }

    private Grammar buildGraphStumpFromTokenTrie(GenericGraphNode<Token> inferredGrammarNode) {
        Grammar g = new Grammar();
        Graph main = new Graph(g, "Main");
        g.getGraphs().put("Main", main);

        GraphNode startNode = new GraphNode(g);
        main.addGraphNode(startNode);
        main.setStartNode(startNode);
        GraphNode endNode = new GraphNode(g);
        main.addGraphNode(endNode);
        main.setEndNode(endNode);

        buildGraphStumpFromTokenTrieRecursive(inferredGrammarNode, startNode, endNode);
        return g;
    }

    private void buildGraphStumpFromTokenTrieRecursive(GenericGraphNode<Token> genericGraphNode, GraphNode parentGn, GraphNode endGn) {
        for (GenericGraphNode<Token> genericGraphNodeChild : genericGraphNode.children) {
            GraphNode gn = alreadyBuiltGraphNodes.get(genericGraphNodeChild);
            boolean branchAlreadyBuiltFromHereOn = true;
            if (gn == null) {
                gn = genericToGraphNode(genericGraphNodeChild, parentGn.grammar);
                alreadyBuiltGraphNodes.put(genericGraphNodeChild, gn);
                parentGn.getParentGraph().addGraphNode(gn);
                branchAlreadyBuiltFromHereOn = false;
            }
            parentGn.getChildNodesIndexes().add(gn.getIndex());
            if (!branchAlreadyBuiltFromHereOn)
                buildGraphStumpFromTokenTrieRecursive(genericGraphNodeChild, gn, endGn);
        }

        if (genericGraphNode.isLeaf) {
            if (genericGraphNode.children.size() == 0)
                parentGn.getChildNodesIndexes().add(endGn.getIndex());
            else {
                GraphNode empty = new GraphNode(parentGn.grammar);
                parentGn.getParentGraph().addGraphNode(empty);
                parentGn.getChildNodesIndexes().add(empty.getIndex());
                empty.getChildNodesIndexes().add(endGn.getIndex());
            }
        }
    }

    private GraphNode genericToGraphNode(GenericGraphNode<Token> inferredGrammarNode, Grammar grammar) {
        GraphNode gn = new GraphNode(grammar);
        StringBuilder sb = new StringBuilder("<");
        for (Map.Entry<String, String> entry : inferredGrammarNode.userObject.getFeatures().entrySet()) {
            if (entry.getKey().equals(attrib)) {
                sb.append("+").append(entry.getKey()).append("=");
                if (entry.getValue().matches("[\\+\\-]")) {
                    sb.append("/").append(entry.getValue()).append("/");
                } else
                    sb.append(entry.getValue());
            }
        }
        sb.append(">");
        gn.setTokenMatchingCode(sb.toString());
        return gn;
    }

    private void formatGrammar(Grammar g) {
        Graph main = g.getGraphs().get("Main");

        List<Integer> allDepths = new ArrayList<Integer>();
        for (int i = 0; i < g.getGraphs().get("Main").getGraphNodes().size(); i++) {
            allDepths.add(-1);
        }
        HashSet<String> ignoreParentChildOnDepthCalc = new HashSet<String>();
        findAllCycles(g.getGraphs().get("Main").getStartNode(), new ArrayList<Integer>(), ignoreParentChildOnDepthCalc);
        computeAllDepthsRecursive(g.getGraphs().get("Main").getStartNode(), new ArrayList<Integer>(), allDepths, ignoreParentChildOnDepthCalc);


        autoPositionGGSNodes(main.getStartNode(), 0, allDepths, new HashSet<GraphNode>());
        main.getEndNode().setX(main.getEndNode().getX() + xSpace);
        List<GraphNode> parentsOfEndNode = new ArrayList<GraphNode>();
        for (GraphNode gn : main.getGraphNodes().values())
            if (gn.getChildNodesIndexes().contains(main.getEndNode().getIndex()))
                parentsOfEndNode.add(gn);
        if (parentsOfEndNode.size() > 1)
            for (GraphNode gn : parentsOfEndNode) {
                //add intemediary empty node for final, just to make the grammar look nice
                GraphNode empty = new GraphNode(g);
                main.addGraphNode(empty);
                gn.getChildNodesIndexes().remove((Integer) main.getEndNode().getIndex());
                gn.getChildNodesIndexes().add(empty.getIndex());
                empty.getChildNodesIndexes().add(main.getEndNode().getIndex());
                empty.setY(gn.getY());
                empty.setX(main.getEndNode().getX() - xSpace);
            }

        main.getEndNode().setY(margin + parentsOfEndNode.size() * ySpace / 2);
    }

    /**
     * remove crossed nodes relations by adding an extra empty node
     *
     * @param g
     */

    private void resolveCrossLinks(Grammar g) {
        //find nodes whose parent nodes sets intersection is greater than 2
        //in other words, find 2 or more nodes which all have a common number of parents (more than 2)

        Map<String, Set<Integer>> commonChildren2Parents = new HashMap<String, Set<Integer>>();
        Graph main = g.getGraphs().get("Main");

        for (GraphNode node1 : main.getGraphNodes().values())
            for (GraphNode node2 : main.getGraphNodes().values()) {
                if (node1.getIndex() >= node2.getIndex())
                    continue;
                List<Integer> intersection = new ArrayList<Integer>(node1.getChildNodesIndexes());
                intersection.retainAll(node2.getChildNodesIndexes());
                intersection.remove((Integer) node1.getIndex());
                intersection.remove((Integer) node2.getIndex());
                if (intersection.size() > 1) {
                    //build a string key for the parents list
                    Collections.sort(intersection);
                    StringBuilder sb = new StringBuilder();
                    for (int i : intersection)
                        sb.append(i).append(" ");
                    String key = sb.delete(sb.length() - 1, sb.length()).toString();
                    Set<Integer> commonParents = commonChildren2Parents.get(key);
                    if (commonParents == null) {
                        commonParents = new HashSet<Integer>();
                        commonChildren2Parents.put(key, commonParents);
                    }
                    commonParents.add(node1.getIndex());
                    commonParents.add(node2.getIndex());
                }
            }

        for (Map.Entry<String, Set<Integer>> entry : commonChildren2Parents.entrySet()) {
            Set<Integer> children = new HashSet<Integer>();
            String[] ints = entry.getKey().split(" ");
            for (String s : ints)
                children.add(Integer.parseInt(s));

            Set<Integer> parents = entry.getValue();

            GraphNode n = new GraphNode(g);
            main.addGraphNode(n);
            n.getChildNodesIndexes().addAll(children);

            for (int p : parents) {
                int minChildIndex = main.getGraphNodes().get(p).getChildNodesIndexes().size();
                for (int c : children) {
                    int ind = main.getGraphNodes().get(p).getChildNodesIndexes().indexOf(c);
                    if (ind < minChildIndex)
                        minChildIndex = ind;
                }
                main.getGraphNodes().get(p).getChildNodesIndexes().removeAll(children);
                main.getGraphNodes().get(p).getChildNodesIndexes().add(minChildIndex, n.getIndex());
            }
            break;
        }
    }

    private Set<GraphNode> findAllCycles(GraphNode gn, List<Integer> currentRoute, HashSet<String> ignoreParentChildOnDepthCalc) {
        currentRoute.add(gn.getIndex());

        Set<GraphNode> descendants = alldescendants.get(gn);
        if (descendants != null) {
            for (GraphNode desc : descendants)
                if (currentRoute.contains(desc))
                    ignoreParentChildOnDepthCalc.add(gn.getIndex() + " " + desc.getIndex());
            return descendants;
        } else {
            Set<GraphNode> ret = new HashSet<GraphNode>();
            for (int i : gn.getChildNodesIndexes()) {
                if (currentRoute.contains(i)) {
                    ignoreParentChildOnDepthCalc.add(gn.getIndex() + " " + gn.getParentGraph().getGraphNodes().get(i).getIndex());
                    continue;
                }

                ret.addAll(findAllCycles(gn.getParentGraph().getGraphNodes().get(i), currentRoute, ignoreParentChildOnDepthCalc));
                ret.add(gn.getParentGraph().getGraphNodes().get(i));
            }
            currentRoute.remove(currentRoute.size() - 1);
            alldescendants.put(gn, ret);
            return ret;
        }
    }

    private void computeAllDepthsRecursive(GraphNode gn, List<Integer> currentRoute, List<Integer> allDepths, HashSet<String> ignoreParentChildOnDepthCalc) {
//        Queue<Integer> breathe = new ArrayDeque<Integer>();
//        breathe.add(gn.getPositionInSentence());
//        while (!breathe.isEmpty()){
//            GraphNode g = gn.getParentGraph().getGraphNodes().get(breathe.remove());
//            for (int i: g.getChildNodesIndexes()){
//                if (ignoreParentChildOnDepthCalc.get(g) == gn.getParentGraph().getGraphNodes().get(i))
//                    continue;
//                if (allDepths)
//            }
//        }


        if (currentRoute.contains(gn.getIndex()))
            return;
        if (allDepths.get(gn.getIndex()) < currentRoute.size())
            allDepths.set(gn.getIndex(), currentRoute.size());
        else
            return;
        currentRoute = new ArrayList<Integer>(currentRoute);
        currentRoute.add(gn.getIndex());
        for (int i : gn.getChildNodesIndexes()) {
            if (ignoreParentChildOnDepthCalc.contains(gn.getIndex() + " " + gn.getParentGraph().getGraphNodes().get(i).getIndex()))
                continue;
            computeAllDepthsRecursive(gn.getParentGraph().getGraphNodes().get(i), currentRoute, allDepths, ignoreParentChildOnDepthCalc);
        }
    }

    private int autoPositionGGSNodes(GraphNode currentGn, int branchesAbove, List<Integer> allDepths, Set<GraphNode> alreadyPassed) {

        int newX = allDepths.get(currentGn.getIndex()) * xSpace + margin;
        currentGn.setX(newX);
        Integer branchesToRight = alreadyPositioned.get(currentGn);
        if (branchesToRight != null)
            return branchesToRight;

        int totalBranchesToRight = 0;
        alreadyPassed.add(currentGn);

        for (int childIndex : currentGn.getChildNodesIndexes()) {
            if (alreadyPassed.contains(currentGn.getParentGraph().getGraphNodes().get(childIndex)))
                continue;
            if (!alreadyPositioned.containsKey(currentGn.getParentGraph().getGraphNodes().get(childIndex)) && alreadyPassed.contains(currentGn.getParentGraph().getGraphNodes().get(childIndex)))
                continue; //prevent infinite loops
            GraphNode childNode = currentGn.getParentGraph().getGraphNodes().get(childIndex);
            branchesToRight = autoPositionGGSNodes(childNode, totalBranchesToRight + branchesAbove, allDepths, alreadyPassed);
            totalBranchesToRight += branchesToRight;
        }

        if (totalBranchesToRight == 0)
            totalBranchesToRight = 1;
        if (!alreadyPositioned.containsKey(currentGn)) {
            currentGn.setY(branchesAbove * ySpace + margin + totalBranchesToRight * ySpace / 2);
            alreadyPositioned.put(currentGn, totalBranchesToRight);
        }


        return totalBranchesToRight;
    }

    private float similarityMeasure(List<GenericGraphNode<Token>> subGraph1, List<GenericGraphNode<Token>> subGraph2, Comparator<Token> comparator) {
        float similaritySum = 0;
        for (int i = 0; i < subGraph1.size(); i++) {
            similaritySum += similarityMeasure(subGraph1.get(i), subGraph2.get(i), comparator);
        }
        //return (similaritySum - subGraph1.size() + 1) * subGraph1.size();
        //return similaritySum/subGraph1.size();
        return similaritySum;
    }

    private float similarityMeasure(GenericGraphNode<Token> node1, GenericGraphNode<Token> node2, Comparator<Token> comparator) {//return number of nodes which are "equal" - the number of the ones which are not
        float rez = 0;
        for (GenericGraphNode<Token> child1 : node1.children) {
            boolean found = false;
            for (GenericGraphNode<Token> child2 : node2.children)
                if (comparator.compare(child1.userObject, child2.userObject) == 0) {
                    found = true;
                    break;
                }
            if (found)
                rez += 2;
        }
        for (GenericGraphNode<Token> parent1 : node1.parents) {
            boolean found = false;
            for (GenericGraphNode<Token> parent2 : node2.parents)
                if (comparator.compare(parent1.userObject, parent2.userObject) == 0) {
                    found = true;
                    break;
                }
            if (found)
                rez += 2;
        }

        if (node1.isLeaf && node2.isLeaf) rez += 2;//if both nodes are leafs, increase score but don't affect otherwise

        int totalNeighbours = node1.children.size() + node2.children.size() + node1.parents.size() + node2.parents.size() + (node1.isLeaf && node2.isLeaf ? 2 : 0);
        //rez = 2 * rez - totalNeighbours; //nr de vecini similari - nr de vecini diferiti
        //rez = rez / totalNeighbours;
        rez = 2 * rez / totalNeighbours - 1;//intre -1 si 1
        return rez;
    }

    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///minimization/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///

    private void mergeSubGraphs(GenericGraphNode<Token> subGraph1root, GenericGraphNode<Token> subGraph2root, final Comparator<Token> comparator, Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedNodes) {
        mergeGraphNodesAndChildren(subGraph1root, subGraph2root, new Comparator<GenericGraphNode<Token>>() {
                    @Override
                    public int compare(GenericGraphNode<Token> tokenGenericGraphNode, GenericGraphNode<Token> tokenGenericGraphNode2) {
                        return comparator.compare(tokenGenericGraphNode.userObject, tokenGenericGraphNode2.userObject);
                    }
                },
                new HashSet<GenericGraphNode<Token>>(),
                new HashSet<GenericGraphNode<Token>>(),
                mergedNodes);
    }

    private void mergeGraphNodesAndChildren(GenericGraphNode<Token> node1, GenericGraphNode<Token> node2, Comparator<GenericGraphNode<Token>> comparator, Set<GenericGraphNode<Token>> alreadyPassedAncestorOfNode1, Set<GenericGraphNode<Token>> alreadyPassedAncestorOfNode2, Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedTo) {
        alreadyPassedAncestorOfNode1.add(node1);
        alreadyPassedAncestorOfNode2.add(node2);
        if (node1 == node2)
            return;
        mergedTo.put(node2, node1); // this is used to keep track of already merged nodes from previous iterations - some mergeable roots might get lost in a previous merge
        node1.isLeaf = node1.isLeaf || node2.isLeaf;
        List<GenericGraphNode> parentsClone = new ArrayList<GenericGraphNode>(node2.parents);
        for (GenericGraphNode<Token> parent : parentsClone) {
            parent.RemoveChild(node2);
            parent.AddChild(node1);
        }

        List<GenericGraphNode> childrenClone = new ArrayList<GenericGraphNode>(node2.children);
        for (GenericGraphNode<Token> child : childrenClone) {
            child.RemoveParent(node2);
            //find out if child has to be added to node1 or if an equivalent child exists for node1

            //check if must merge with an existing equal child
            boolean mustMerge = false;
            if (!alreadyPassedAncestorOfNode2.contains(child))//if it contains child it means that we are dealing with a loop on the node 2 ancestor line
                for (GenericGraphNode<Token> mergeChild : node1.children) {
                    if (alreadyPassedAncestorOfNode1.contains(mergeChild))//a loop somewhere on the node 1 ancestors line
                        continue;
                    if (comparator.compare(mergeChild, child) == 0) {
                        mustMerge = true;
                        mergeGraphNodesAndChildren(mergeChild, child, comparator, new HashSet<GenericGraphNode<Token>>(alreadyPassedAncestorOfNode1), new HashSet<GenericGraphNode<Token>>(alreadyPassedAncestorOfNode2), mergedTo);
                        break;
                    }
                }
            if (!mustMerge)
                node1.AddChild(child);
        }
    }

    private void mergeGraphNodes(GenericGraphNode<Token> node1, GenericGraphNode<Token> node2, Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedNodes) {
//        if (alreadyMerged.contains(node1))
//            return;
//        alreadyMerged.add(node1);
        if (node1 == node2)
            return;
        node1.isLeaf = node1.isLeaf || node2.isLeaf;
        List<GenericGraphNode> parentsClone = new ArrayList<GenericGraphNode>(node2.parents);
        for (GenericGraphNode<Token> parent : parentsClone) {
            parent.RemoveChild(node2);
            parent.AddChild(node1);
        }

        List<GenericGraphNode> childrenClone = new ArrayList<GenericGraphNode>(node2.children);
        for (GenericGraphNode<Token> child : childrenClone) {
            child.RemoveParent(node2);
            node1.AddChild(child);
        }
        if (mergedNodes != null)
            mergedNodes.put(node2, node1);
    }

    private void minimizeGraph(GenericGraphNode<Token> pta) {
        minimizeGraphLeftToRight(pta);
        minimizeGraphRightToLeft(pta);
    }

    private void minimizeGraphRightToLeft(GenericGraphNode<Token> pta) {
        Set<GenericGraphNode<Token>> allNodesSet = new LinkedHashSet<GenericGraphNode<Token>>();
        grabNodes(pta, allNodesSet);
        List<GenericGraphNode<Token>> allNodes = new ArrayList<GenericGraphNode<Token>>(allNodesSet);
        Map<GenericGraphNode<Token>, Integer> nodes2Indexes = new HashMap<GenericGraphNode<Token>, Integer>();
        for (int i = 0; i < allNodes.size(); i++)
            nodes2Indexes.put(allNodes.get(i), i);

        //array-ul folosit pt minizare. pt fiecare pereche de doua noduri trebuie sa retin
        // 1) daca sunt distinguishable
        // 2) daca ar fi distinguishable, ce alte perechi ar trebui sa fie distinguishable deasemenea
        //si deci am o matrice de liste de perechi (de fapt sunt maps) de noduri. Daca la i,j am null, cele doua nodur i si j sunt distinguishable. Daca am != null, atunci toate perechile din acel map ar fi distinghuishable daca i,j ar fi distinguishable
        Map<GenericGraphNode<Token>, GenericGraphNode<Token>>[][] minimizationMatrix = new Map[allNodes.size()][allNodes.size()];

        //initialize the minimization matrix
        for (int i = 0; i < allNodes.size(); i++)
            for (int j = 0; j < allNodes.size(); j++) {
                if (j < i)
                    minimizationMatrix[i][j] = minimizationMatrix[j][i];
                if (allNodes.get(i).isLeaf != allNodes.get(j).isLeaf || allNodes.get(i) == pta || allNodes.get(j) == pta || !tokenHash(allNodes.get(i).userObject).equals(tokenHash(allNodes.get(j).userObject)))
                    minimizationMatrix[i][j] = null;
                else
                    minimizationMatrix[i][j] = new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>();
            }
        for (int i = 0; i < allNodes.size() - 1; i++)
            for (int j = i + 1; j < allNodes.size(); j++) {
                if (minimizationMatrix[i][j] == null)
                    continue;
                //determine if any other of the nodes are distinguishable
                HashMap<String, List<GenericGraphNode<Token>>> nodeIparents = new HashMap<String, List<GenericGraphNode<Token>>>();
                for (GenericGraphNode<Token> parent : allNodes.get(i).parents) {
                    String hash = tokenHash(parent.userObject);
                    if (nodeIparents.get(hash) == null)
                        nodeIparents.put(hash, new ArrayList<GenericGraphNode<Token>>());

                    nodeIparents.get(hash).add(parent);
                }

                HashMap<String, List<GenericGraphNode<Token>>> nodeJparents = new HashMap<String, List<GenericGraphNode<Token>>>();
                for (GenericGraphNode<Token> parent : allNodes.get(j).parents) {
                    String hash = tokenHash(parent.userObject);
                    if (nodeJparents.get(hash) == null)
                        nodeJparents.put(hash, new ArrayList<GenericGraphNode<Token>>());

                    nodeJparents.get(hash).add(parent);
                }

                boolean ok = true;
                if (nodeIparents.size() != nodeJparents.size()) {//una din stari are parinti pe care cealalta nu ii are
                    setDistinguishable(i, j, minimizationMatrix, nodes2Indexes);
                    ok = false;
                }
                if (ok)
                    for (String s : nodeIparents.keySet()) {
                        if (!nodeJparents.containsKey(s)) {//una din stari are parinti pe care cealalta nu ii are
                            setDistinguishable(i, j, minimizationMatrix, nodes2Indexes);
                            ok = false;
                            break;
                        }
                    }

                if (ok) {
                    for (String s : nodeIparents.keySet()) {
                        for (GenericGraphNode<Token> tranzINode : nodeIparents.get(s)) {
                            for (GenericGraphNode<Token> tranzJNode : nodeJparents.get(s)) {
                                Map<GenericGraphNode<Token>, GenericGraphNode<Token>> pair = minimizationMatrix[nodes2Indexes.get(tranzINode)][nodes2Indexes.get(tranzJNode)];
                                if (pair == null) {//transition to distinguishable nodes, having the same condition
                                    setDistinguishable(i, j, minimizationMatrix, nodes2Indexes);
                                    ok = false;
                                    break;
                                } else
                                    pair.put(allNodes.get(i), allNodes.get(j));
                            }
                            if (!ok)
                                break;
                        }
                        if (!ok)
                            break;
                    }
                }
            }

        Map<GenericGraphNode<Token>, Set<GenericGraphNode<Token>>> nodes2distinguishableSets = new HashMap<GenericGraphNode<Token>, Set<GenericGraphNode<Token>>>();
        for (int i = 0; i < allNodes.size(); i++) {
            for (int j = 0; j < allNodes.size(); j++)
                if (j > i && minimizationMatrix[i][j] != null)
                    if (nodes2distinguishableSets.get(allNodes.get(i)) == null && nodes2distinguishableSets.get(allNodes.get(j)) == null) {
                        Set<GenericGraphNode<Token>> subset = new HashSet<GenericGraphNode<Token>>();
                        subset.add(allNodes.get(i));
                        subset.add(allNodes.get(j));
                        nodes2distinguishableSets.put(allNodes.get(i), subset);
                        nodes2distinguishableSets.put(allNodes.get(j), subset);
                    } else if (nodes2distinguishableSets.get(allNodes.get(j)) == null) {
                        Set<GenericGraphNode<Token>> subset = nodes2distinguishableSets.get(allNodes.get(i));
                        subset.add(allNodes.get(j));
                        nodes2distinguishableSets.put(allNodes.get(j), subset);
                    } else if (nodes2distinguishableSets.get(allNodes.get(i)) == null) {
                        Set<GenericGraphNode<Token>> subset = nodes2distinguishableSets.get(allNodes.get(j));
                        subset.add(allNodes.get(i));
                        nodes2distinguishableSets.put(allNodes.get(i), subset);
                    }
        }

        Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedNodes = new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>();
        Set<Set<GenericGraphNode<Token>>> distinguishableSets = new HashSet<Set<GenericGraphNode<Token>>>(nodes2distinguishableSets.values());
        for (Set<GenericGraphNode<Token>> distinguishableSet : distinguishableSets) {
            for (GenericGraphNode<Token> node : distinguishableSet)
                System.out.print(nodes2Indexes.get(node) + " ");
            System.out.println();
        }

        for (Set<GenericGraphNode<Token>> distinguishableSet : distinguishableSets) {
            GenericGraphNode<Token> firstNode = null;
            for (GenericGraphNode<Token> node : distinguishableSet) {
                if (firstNode == null)
                    firstNode = findActualNode(node, mergedNodes);
                else {
                    mergeGraphNodes(firstNode, findActualNode(node, mergedNodes), mergedNodes);
                }
            }
        }
    }

    private void minimizeGraphLeftToRight(GenericGraphNode<Token> pta) {
        Set<GenericGraphNode<Token>> allNodesSet = new LinkedHashSet<GenericGraphNode<Token>>();
        grabNodes(pta, allNodesSet);
        List<GenericGraphNode<Token>> allNodes = new ArrayList<GenericGraphNode<Token>>(allNodesSet);
        Map<GenericGraphNode<Token>, Integer> nodes2Indexes = new HashMap<GenericGraphNode<Token>, Integer>();
        for (int i = 0; i < allNodes.size(); i++)
            nodes2Indexes.put(allNodes.get(i), i);

        //array-ul folosit pt minizare. pt fiecare pereche de doua noduri trebuie sa retin
        // 1) daca sunt distinguishable
        // 2) daca ar fi distinguishable, ce alte perechi ar trebui sa fie distinguishable deasemenea
        //si deci am o matrice de liste de perechi (de fapt sunt maps) de noduri. Daca la i,j am null, cele doua nodur i si j sunt distinguishable. Daca am != null, atunci toate perechile din acel map ar fi distinghuishable daca i,j ar fi distinguishable
        Map<GenericGraphNode<Token>, GenericGraphNode<Token>>[][] minimizationMatrix = new Map[allNodes.size()][allNodes.size()];

        //initialize the minimization matrix
        for (int i = 0; i < allNodes.size(); i++)
            for (int j = 0; j < allNodes.size(); j++) {
                if (j < i)
                    minimizationMatrix[i][j] = minimizationMatrix[j][i];
                if (allNodes.get(i).isLeaf != allNodes.get(j).isLeaf || allNodes.get(i) == pta || allNodes.get(j) == pta || !tokenHash(allNodes.get(i).userObject).equals(tokenHash(allNodes.get(j).userObject)))
                    minimizationMatrix[i][j] = null;
                else
                    minimizationMatrix[i][j] = new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>();
            }
        for (int i = 0; i < allNodes.size() - 1; i++)
            for (int j = i + 1; j < allNodes.size(); j++) {
                if (minimizationMatrix[i][j] == null)
                    continue;
                //determine if any other of the nodes are distinguishable
                HashMap<String, List<GenericGraphNode<Token>>> nodeItranz = new HashMap<String, List<GenericGraphNode<Token>>>();
                for (GenericGraphNode<Token> child : allNodes.get(i).children) {
                    String hash = tokenHash(child.userObject);
                    if (nodeItranz.get(hash) == null)
                        nodeItranz.put(hash, new ArrayList<GenericGraphNode<Token>>());

                    nodeItranz.get(hash).add(child);
                }

                HashMap<String, List<GenericGraphNode<Token>>> nodeJtranz = new HashMap<String, List<GenericGraphNode<Token>>>();
                for (GenericGraphNode<Token> child : allNodes.get(j).children) {
                    String hash = tokenHash(child.userObject);
                    if (nodeJtranz.get(hash) == null)
                        nodeJtranz.put(hash, new ArrayList<GenericGraphNode<Token>>());

                    nodeJtranz.get(hash).add(child);
                }

                boolean ok = true;
                if (nodeItranz.size() != nodeJtranz.size()) {//din una din stari exista tranzitii care nu exista din cealalta
                    setDistinguishable(i, j, minimizationMatrix, nodes2Indexes);
                    ok = false;
                }
                if (ok)
                    for (String s : nodeItranz.keySet()) {
                        if (!nodeJtranz.containsKey(s)) {//din una din stari exista tranzitii care nu exista din cealalta
                            setDistinguishable(i, j, minimizationMatrix, nodes2Indexes);
                            ok = false;
                            break;
                        }
                    }

                if (ok) {
                    for (String s : nodeItranz.keySet()) {
                        for (GenericGraphNode<Token> tranzINode : nodeItranz.get(s)) {
                            for (GenericGraphNode<Token> tranzJNode : nodeJtranz.get(s)) {
                                Map<GenericGraphNode<Token>, GenericGraphNode<Token>> pair = minimizationMatrix[nodes2Indexes.get(tranzINode)][nodes2Indexes.get(tranzJNode)];
                                if (pair == null) {//transition to distinguishable nodes, having the same condition
                                    setDistinguishable(i, j, minimizationMatrix, nodes2Indexes);
                                    ok = false;
                                    break;
                                } else
                                    pair.put(allNodes.get(i), allNodes.get(j));
                            }
                            if (!ok)
                                break;
                        }
                        if (!ok)
                            break;
                    }
                }
            }

        Map<GenericGraphNode<Token>, Set<GenericGraphNode<Token>>> nodes2distinguishableSets = new HashMap<GenericGraphNode<Token>, Set<GenericGraphNode<Token>>>();
        for (int i = 0; i < allNodes.size(); i++) {
            for (int j = 0; j < allNodes.size(); j++)
                if (j > i && minimizationMatrix[i][j] != null)
                    if (nodes2distinguishableSets.get(allNodes.get(i)) == null && nodes2distinguishableSets.get(allNodes.get(j)) == null) {
                        Set<GenericGraphNode<Token>> subset = new HashSet<GenericGraphNode<Token>>();
                        subset.add(allNodes.get(i));
                        subset.add(allNodes.get(j));
                        nodes2distinguishableSets.put(allNodes.get(i), subset);
                        nodes2distinguishableSets.put(allNodes.get(j), subset);
                    } else if (nodes2distinguishableSets.get(allNodes.get(j)) == null) {
                        Set<GenericGraphNode<Token>> subset = nodes2distinguishableSets.get(allNodes.get(i));
                        subset.add(allNodes.get(j));
                        nodes2distinguishableSets.put(allNodes.get(j), subset);
                    } else if (nodes2distinguishableSets.get(allNodes.get(i)) == null) {
                        Set<GenericGraphNode<Token>> subset = nodes2distinguishableSets.get(allNodes.get(j));
                        subset.add(allNodes.get(i));
                        nodes2distinguishableSets.put(allNodes.get(i), subset);
                    }
        }

        Map<GenericGraphNode<Token>, GenericGraphNode<Token>> mergedNodes = new HashMap<GenericGraphNode<Token>, GenericGraphNode<Token>>();
        Set<Set<GenericGraphNode<Token>>> distinguishableSets = new HashSet<Set<GenericGraphNode<Token>>>(nodes2distinguishableSets.values());
        for (Set<GenericGraphNode<Token>> distinguishableSet : distinguishableSets) {
            for (GenericGraphNode<Token> node : distinguishableSet)
                System.out.print(nodes2Indexes.get(node) + " ");
            System.out.println();
        }

        for (Set<GenericGraphNode<Token>> distinguishableSet : distinguishableSets) {
            GenericGraphNode<Token> firstNode = null;
            for (GenericGraphNode<Token> node : distinguishableSet) {
                if (firstNode == null)
                    firstNode = findActualNode(node, mergedNodes);
                else {
                    mergeGraphNodes(firstNode, findActualNode(node, mergedNodes), mergedNodes);
                }
            }
        }
    }

    private void setDistinguishable(int i, int j, Map<GenericGraphNode<Token>, GenericGraphNode<Token>>[][] minimizationArray, Map<GenericGraphNode<Token>, Integer> nodes2Indexes) {
        Map<GenericGraphNode<Token>, GenericGraphNode<Token>> val = minimizationArray[i][j];
        minimizationArray[i][j] = null;
        minimizationArray[j][i] = null;
        if (val != null) {
            for (Map.Entry<GenericGraphNode<Token>, GenericGraphNode<Token>> entry : val.entrySet())
                setDistinguishable(nodes2Indexes.get(entry.getKey()), nodes2Indexes.get(entry.getValue()), minimizationArray, nodes2Indexes);
        }
    }

    private static String tokenHash(Token token) {
        if (token == null)
            return "";
        return attrib + "=" + token.getFeatures().get(attrib);
    }

    private void grabNodes(GenericGraphNode<Token> curent, Set<GenericGraphNode<Token>> allNodes) {
        if (allNodes.contains(curent))
            return;
        allNodes.add(curent);
        for (GenericGraphNode<Token> child : curent.children)
            grabNodes(child, allNodes);
    }
}