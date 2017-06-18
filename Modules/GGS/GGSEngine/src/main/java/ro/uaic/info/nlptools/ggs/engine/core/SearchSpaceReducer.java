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

import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.corpus.IndexedLuceneCorpus;
import ro.uaic.info.nlptools.ggs.engine.SparseBitSet;
import ro.uaic.info.nlptools.corpus.INlpCorpus;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;
import javafx.util.Pair;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SearchSpaceReducer {
    static WeakHashMap<INlpCorpus, SearchCache> searchCache = new WeakHashMap<>();

    //return a pair: key represents the starting tokens positions and value represent the ending tokens positions. for one position there might by more ending positions because there might be more spanAnnotations starting or ending in a particular position
    //this is some sort of one to many bidirectional map
    private static Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>> PositionsForAnnotationCondition(TransitionCondition condition, IndexedLuceneCorpus input) throws IOException {
        String key = condition.toString();
        if (searchCache.containsKey(input))
            if (searchCache.get(input).positionsForMatchingTokens.containsKey(key))
                return searchCache.get(input).positionsForMatchingAnnotations.get(key);

        SparseBitSet ANYforAnnotations = new SparseBitSet();
        ANYforAnnotations.set(0, input.getAnnotationsCount());
        ANYforAnnotations.locked = true;

        SparseBitSet data = PositionsForTokenCondition(condition, input, ANYforAnnotations);
        Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>> rez = new Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>>(new HashMap<Integer, List<Integer>>(), new HashMap<Integer, List<Integer>>());

        for (int i = data.nextSetBit(); i != -1; i = data.nextSetBit()) {
            Document doc = input.annotationSearcher.doc(i);
            int startOfAnnotation = doc.getField("GGS:StartTokenIndex").numericValue().intValue();
            int endOfAnnotation = doc.getField("GGS:EndTokenIndex").numericValue().intValue();

            List<Integer> allAnnotationsWhichStartHere = rez.getKey().get(startOfAnnotation);
            if (allAnnotationsWhichStartHere == null) {
                allAnnotationsWhichStartHere = new ArrayList<>();
                rez.getKey().put(startOfAnnotation, allAnnotationsWhichStartHere);
            }
            allAnnotationsWhichStartHere.add(i);

            List<Integer> allAnnotationsWhichEndHere = rez.getValue().get(endOfAnnotation);
            if (allAnnotationsWhichEndHere == null) {
                allAnnotationsWhichEndHere = new ArrayList<>();
                rez.getValue().put(endOfAnnotation, allAnnotationsWhichEndHere);
            }
            allAnnotationsWhichEndHere.add(i);
        }

        if (!searchCache.containsKey(input))
            searchCache.put(input, new SearchCache());
        searchCache.get(input).positionsForMatchingAnnotations.put(key, rez);

        return rez;
    }

    static SparseBitSet PositionsForTokenCondition(TransitionCondition condition, IndexedLuceneCorpus input, SparseBitSet ANY) throws IOException {
        if (condition.featuresConditions == null && condition.annotationMatcher != null)
            return ANY;

        String key = condition.toString();
        if (searchCache.containsKey(input))
            if (searchCache.get(input).positionsForMatchingTokens.containsKey(key))
                return searchCache.get(input).positionsForMatchingTokens.get(key);

        SparseBitSet rez = ANY;
        IndexSearcher searcher = input.tokenSearcher;
        if (condition.annotationMatcher != null) { //entries for input spanAnnotations are just like simple tokens but they also have an attribute for spanAnnotation name and one for indicating the length of the spanAnnotation
            searcher = input.annotationSearcher;
            rez = PositionsForAttributeValue(searcher, "GGS:Name", condition.annotationMatcher, false, input, ANY, true);
        }

        boolean initialRez = true;
        for (Map.Entry<Pattern, Pattern> entry : condition.featuresConditions.entrySet()) {
            if (rez.cardinality() == 0)
                break;

            SparseBitSet r;
            if (condition.featuresConditionNamesWhichAreRegex.contains(entry.getKey())) {//make a reunion of all the attributes matching the attr name pattern
                r = new SparseBitSet();
                for (String attr : input.getAllAttributeNames())
                    if (entry.getKey().matcher(attr).matches()) {
                        if (condition.featuresConditionsSigns.get(entry.getKey()))
                            r.or(PositionsForAttributeValue(searcher, attr, entry.getValue().toString(), condition.featuresConditionValuesWhichAreRegex.contains(entry.getValue()), input, ANY, condition.featuresConditionsSigns.get(entry.getKey())));
                        else {
                            r.and(PositionsForAttributeValue(searcher, attr, entry.getValue().toString(), condition.featuresConditionValuesWhichAreRegex.contains(entry.getValue()), input, ANY, condition.featuresConditionsSigns.get(entry.getKey())));
                            if (r.cardinality() == 0)
                                break;
                        }
                    }
            } else
                r = PositionsForAttributeValue(searcher, entry.getKey().toString(), entry.getValue().toString(), condition.featuresConditionValuesWhichAreRegex.contains(entry.getValue()), input, ANY, condition.featuresConditionsSigns.get(entry.getKey()));
            if (initialRez)
                rez = r;
            else {
                if (rez.locked)
                    rez = rez.clone();
                rez.and(r);
            }
            initialRez = false;
        }

        rez.locked = true;
        if (!searchCache.containsKey(input))
            searchCache.put(input, new SearchCache());
        searchCache.get(input).positionsForMatchingTokens.put(key, rez);

        return rez;
    }

    public static long regexQueryTime;
    public static long termQueryTime;
    public static int regexQueryCount;
    public static int termQueryCount;

    private static SparseBitSet PositionsForAttributeValue(IndexSearcher searcher, String attr, String value, boolean isRegex, IndexedLuceneCorpus input, SparseBitSet ANY, Boolean conditionSign) throws IOException {
        String key = (conditionSign ? "+__" : "-__") + attr + "__" + value + "__" + isRegex;
        if (searchCache.containsKey(input))
            if (searchCache.get(input).positionsForMatchingTokens.containsKey(key))
                return searchCache.get(input).positionsForMatchingTokens.get(key);

        Matcher matcher = GraphNode.jsVarsPattern.matcher(value);
        StringBuilder sb = new StringBuilder();
        int lastOffset = 0;
        boolean hasJS = false;
        while (matcher.find()) {
            hasJS = true;
            sb.append(value.substring(lastOffset, matcher.start()));
            if (lastOffset == 0 || lastOffset != matcher.start())
                sb.append(".*");
            lastOffset = matcher.end();
        }
        if (hasJS)
            sb.append(value.substring(lastOffset));
        String stripedJsKey = null;
        if (hasJS) {
            value = sb.toString();
            String _value = "";
            while (!_value.equals(value)) {
                _value = value;
                value = value.replaceAll("\\.\\*\\.\\*", ".*");
                value = value.replaceAll("\\.\\+\\.\\*", ".+");
                value = value.replaceAll("\\.\\*\\.\\+", ".+");
                value = value.replaceAll("\\.\\.[\\*]", ".+");
                value = value.replaceAll("\\.[\\*]\\.", ".+");
            }
            isRegex = true;
            stripedJsKey = (conditionSign ? "+__" : "-__") + attr + "__" + value + "__" + isRegex;
            if (searchCache.containsKey(input))
                if (searchCache.get(input).positionsForMatchingTokens.containsKey(stripedJsKey))
                    return searchCache.get(input).positionsForMatchingTokens.get(stripedJsKey);
        }

        if (!conditionSign) {
            //TODO - test what happens when complementing a bitset resulted from a regex condition. regexes in lucene are not as exact as in java. For instance capitalization is ignored.
            //the complement of a wider/inexact bitset does not contain all the tokens which don't match the exact regex.
            //it might just be easier to negate the regex itself, rather the using its complementary bitset
            SparseBitSet r = PositionsForAttributeValue(searcher, attr, value, isRegex, input, ANY, true).clone();
            r.xor(ANY);// not operator
            r.locked = true;
            searchCache.get(input).positionsForMatchingTokens.put(key, r);
            return r;
        }

        long time = System.currentTimeMillis();
        CorpusPositionsCollector collector = new CorpusPositionsCollector(searcher);
        Query query = null;
        if (isRegex)
            try {
                query = new RegexpQuery(new Term(attr, value.toString()));
            } catch (IllegalArgumentException ex) {
                return ANY;//regex expression not supported by lucene
            }
        else
            query = new TermQuery(new Term(attr, value.toString()));

        searcher.search(query, collector);
        time = System.currentTimeMillis() - time;
        if (isRegex) {
            regexQueryTime += time;
            regexQueryCount++;
        } else {
            termQueryTime += time;
            termQueryCount++;
        }

        SparseBitSet result = collector.result(input.getTokenCount());
        result.locked = true;
        if (!searchCache.containsKey(input))
            searchCache.put(input, new SearchCache());
        searchCache.get(input).positionsForMatchingTokens.put(key, result);
        if (hasJS)
            searchCache.get(input).positionsForMatchingTokens.put(stripedJsKey, result);

        return result;
    }

    public static SparseBitSet ReduceSearchSpaceForSentButCacheAll(StateMachine sm, int startState, INlpSentence sentence, PTAcache cache) throws IOException {
        SparseBitSet searchSpace = ReduceSearchSpace(sm, startState, sentence.getParentCorpus(), cache);
        SparseBitSet bitMask = new SparseBitSet();
        bitMask.set(sentence.getFirstTokenIndexInCorpus(), sentence.getFirstTokenIndexInCorpus() + sentence.getTokenCount());
        bitMask.and(searchSpace);
        return bitMask;
    }

    public static SparseBitSet ReduceSearchSpace(StateMachine sm, int startState, INlpSentence sentence, PTAcache cache) throws IOException {
        INlpCorpus input = sentence.getParentCorpus();
        if (input instanceof InmemoryCorpus) {
            input = ((InmemoryCorpus) input).getIndexedVersion();
        }
        SparseBitSet bitMask = new SparseBitSet();
        bitMask.set(sentence.getFirstTokenIndexInCorpus(), sentence.getFirstTokenIndexInCorpus() + sentence.getTokenCount());
        return ReduceSearchSpace(sm, startState, input, bitMask, cache);
    }

    public static SparseBitSet ReduceSearchSpace(StateMachine sm, int startState, INlpCorpus input, PTAcache cache) throws IOException {
        if (input instanceof InmemoryCorpus) {
            input = ((InmemoryCorpus) input).getIndexedVersion();
        }
        SparseBitSet ANY = new SparseBitSet();
        ANY.set(0, input.getTokenCount());
        ANY.locked = true;

        return ReduceSearchSpace(sm, startState, input, ANY, cache);
    }

    private static SparseBitSet ReduceSearchSpace(StateMachine sm, int startState, INlpCorpus input, SparseBitSet initBitMask, PTAcache ptaCache) throws IOException {
        System.out.println("Reducing search space for start state: " + startState);
        if (searchCache.containsKey(input))
            if (searchCache.get(input).reducedSearchSpaces.containsKey(sm))
                if (searchCache.get(input).reducedSearchSpaces.get(sm).containsKey(startState)) {
                    System.out.println("Found in cache");
                    return searchCache.get(input).reducedSearchSpaces.get(sm).get(startState);
                }

        if (input instanceof InmemoryCorpus) {
            input = ((InmemoryCorpus) input).getIndexedVersion();
        }

        long time = System.currentTimeMillis();

        SparseBitSet ANY = new SparseBitSet();
        ANY.set(0, input.getTokenCount());
        ANY.locked = true;
        initBitMask.locked = true;

        //start building the pta
        Stack<GraphNode> graphNodesStack = new Stack<>();
        if (ptaCache == null)
            ptaCache = new PTAcache((IndexedLuceneCorpus) input);
        SparseBitSet reducedSpace = BuildPTA(sm, (IndexedLuceneCorpus) input, sm.states.get(startState), initBitMask, 0, graphNodesStack, ANY, ptaCache);

        if (!searchCache.containsKey(input))
            searchCache.put(input, new SearchCache());
        if (!searchCache.get(input).reducedSearchSpaces.containsKey(sm))
            searchCache.get(input).reducedSearchSpaces.put(sm, new HashMap<Integer, SparseBitSet>());
        searchCache.get(input).reducedSearchSpaces.get(sm).put(startState, reducedSpace);
        reducedSpace.locked = true;
        time = System.currentTimeMillis() - time;
        System.out.println("Search space reduced. Elapsed time: " + time);
        System.out.println("Cached PTA bitsets: " + ptaCache.count());
        System.out.println("Cached indexes for input: " + searchCache.get(input).countPositionsForMatchingTokens());
        System.out.println("Cached shift masks for input: " + searchCache.get(input).countShiftMasks());
        return reducedSpace;
    }

    /*
    PTA cache:

    State, depth, graphNodeStack -> {
                                BitSet
                                previous start node indexes
                }

    o intrare din cache se poate folosi doar atunci cand se doreste un pta pt aceeasi stare, avand depth = cu cel din cache, graphNodeStack identice si previous start node indexes din cache tre sa il include pe previous start node indexes pt care s-a facut cererea
    -a face aceste verificari ar putea fi mai costisitor decat eficientizarea in sine. Merita incercat totusi. De observat ca graphNodeStack are nevoie de snapshot in cache
    -cand se face o insertie in cache, daca cheia este prezenta, se poate modifica valoarea existenta, facand OR intre valorile noi si cele din cache, cu BitSet si previous start node indexes
    */

    public static SparseBitSet BuildPTA(StateMachine sm, IndexedLuceneCorpus input, State forState, SparseBitSet bitMask, int depthInMatch, Stack<GraphNode> jumpNodesStack, SparseBitSet ANY, PTAcache ptaCache) throws IOException {
        if (bitMask.cardinality() == 0)
            return null;

        bitMask.locked = true;

        //use PTA cache for nodes which have more than one parent - the partial results for PTA nodes are likely to be reused.
        Pair<SparseBitSet, SparseBitSet> cachedRez = null;
        boolean cached = false;
        SparseBitSet bitMaskBackup = null;
        int depthInMatchBackup = depthInMatch;
        if (forState.parentStatesIndexes.size() > 1) {
            cached = true;
            cachedRez = ptaCache.Get(forState, depthInMatch, jumpNodesStack, bitMask);

            if (cachedRez != null) {
                if (cachedRez.getKey().cardinality() == bitMask.cardinality())
                    return cachedRez.getValue();
                bitMaskBackup = bitMask;
                bitMask = bitMask.clone();
                bitMask.andNot(cachedRez.getKey()); // filter out the corpus position for which a result is already cached (based on the current state and jump stack.
                //check only the unknown positions and then compose the final result with the partially cached results
            }
        }

        if (forState.isGraphNodeExit) {
            //cand se iese dintr-un graf(apelat dintr-un altul), din nodul de iesire trebuie sa se poata merge doar pe un singur
            //traseu, care duce inapoi in graful initial (din care a fost apelat). Pentru aceasta, la revenirea dintr-un jump, se verifica daca varful acestei pathului de noduri este egal cu nodul curent

            //when returning from a jump to the upper graph, jumpsStack will specify the node at which the graph parsing must return.
            //(when the state machine is build, if a graph is being jumped to from various nodes, the child states of its exit state will be multiple,
            //but when returning from a jump only one of the possible transitions is valid)

            //here we check if the graph node of the current state is equal to the one from the jumpsStack
            if (forState.parentGraphNode != jumpNodesStack.peek())
                return null;
            jumpNodesStack.pop();
        }

        if (forState.isGraphNodeEntry) {
            jumpNodesStack.push(forState.parentGraphNode);
        }

        if (jumpNodesStack.size() == 0) { //path might consume successfully
            boolean hasTransitionsToOtherNodes = false;
            for (int childIndex : forState.childStatesIndexes)
                if (!sm.states.get(childIndex).isGraphNodeExit) {
                    hasTransitionsToOtherNodes = true;
                    break;
                }
            if (!hasTransitionsToOtherNodes) {
                jumpNodesStack.push((forState.parentGraphNode));
                return bitMask;//final state reached
            }
        }
        SparseBitSet intersection = bitMask; //if intersection doesnțt get manipulated, just return bitMask. Otherwise, make a enw instance. bitMask must not be modified

        //check current node conditions and consume
        SparseBitSet additionalCacheInfo = null;
        if (forState.condition != null && !forState.condition.isEmpty) {
            if (forState.condition.annotationMatcher == null) {
                SparseBitSet positionsForConditionUnshifted = PositionsForTokenCondition(forState.condition, input, ANY);
                //at this point, we can update the cache of this state partial result. We know for a fact that some positions are not going to match from this point forward. This info can be reused

                SparseBitSet positionsForCondition = positionsForConditionUnshifted.clone();
                ShiftOnSentences(positionsForCondition, input, -depthInMatch * (forState.lookBehind ? -1 : 1));
                int c = intersection.cardinality();
                if (intersection == bitMask)
                    intersection = bitMask.clone();
                intersection.and(positionsForCondition);
                if (c != intersection.cardinality())
                    additionalCacheInfo = positionsForCondition;//intersection has changed based on the positions for condition, yet some info discarded in the process could be cached - we save it temporarily in additionalCacheInfo and handle it at the end of the method. We can cache all the positions for which this state yelds 0, even though we don't need these positions just yet
                depthInMatch++;
            } else {
                //check the input spanAnnotation conditions in a similar way to token
                //get start token positions or end token positions (if lookbehind) of all input spanAnnotations
                //check if the sequences match the rest of the grammar by starting from the other end of the input spanAnnotation. We don't shift for this, because each input spanAnnotation can have a different size.
                //instead we check in a similar way to lookahead or lookbehind assertions, by feeding the opposite ends of the input spanAnnotations as initial corpus positions intersection.
                Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>> annotationsPositions = PositionsForAnnotationCondition(forState.condition, input);
                SparseBitSet continueFrom = new SparseBitSet();
                HashMap<Integer, List<Integer>> aux = forState.lookBehind ? annotationsPositions.getKey() : annotationsPositions.getValue();
                for (int entry : aux.keySet())
                    continueFrom.set(entry + (forState.lookBehind ? -1 : 1));

                continueFrom = BuildPTA(sm, input, sm.states.get(forState.childStatesIndexes.get(0)), continueFrom, 0, jumpNodesStack, ANY, ptaCache);
                if (continueFrom.locked)
                    continueFrom = continueFrom.clone();
                ShiftOnSentences(continueFrom, input, forState.lookBehind ? 1 : -1);

                SparseBitSet filteredAnnotations = new SparseBitSet();
                //find the start of the filtered spanAnnotations endings
                for (int i = continueFrom.nextSetBit(); i != -1; i = continueFrom.nextSetBit())
                    for (int ii : aux.get(i)) {
                        Document doc = input.annotationSearcher.doc(ii);
                        int otherEnd = doc.getField(forState.lookBehind ? "GGS:EndTokenIndex" : "GGS:StartTokenIndex").numericValue().intValue();
                        filteredAnnotations.set(otherEnd);
                    }
                SparseBitSet filteredAnnotationsUnshifted = filteredAnnotations.clone();
                ShiftOnSentences(filteredAnnotations, input, -depthInMatch * (forState.lookBehind ? -1 : 1));
                int c = intersection.cardinality();
                if (intersection == bitMask)
                    intersection = bitMask.clone();
                intersection.and(filteredAnnotations);
                if (c != intersection.cardinality())
                    additionalCacheInfo = filteredAnnotationsUnshifted;
            }
        }

        //check assertions - only positive assertions. negative assertions cannot be handled. The complementary of a reduced search space does not necessarily yield all the non matching token positions
        if (forState.assertions != null && forState.assertions.size() > 0) {
            for (AssertionCondition assertionCondition : forState.assertions) {
                if (assertionCondition.assertionNode.nodeType != GraphNode.NodeType.CrossReference && !assertionCondition.assertionNode.isNegativeAssertion) {
                    SparseBitSet bs = ReduceSearchSpace(assertionCondition.stateMachine, assertionCondition.startStateIndex, input, ptaCache);
                    if (bs.locked)
                        bs = bs.clone();
                    if (assertionCondition.assertionNode.nodeType == GraphNode.NodeType.LookAhead)
                        ShiftOnSentences(bs, input, -depthInMatch * (forState.lookBehind ? -1 : 1));
                    else if (assertionCondition.assertionNode.nodeType == GraphNode.NodeType.LookBehind)
                        ShiftOnSentences(bs, input, -depthInMatch * (forState.lookBehind ? -1 : 1) + (forState.lookBehind ? -1 : 1));

                    if (intersection == bitMask)
                        intersection = bitMask.clone();
                    intersection.and(bs);
                }
            }
        }

        //check children recursively
        if (forState.condition.annotationMatcher == null && intersection.cardinality() != 0) {//don't check children for input spanAnnotation consumers - they have been handled as assertion in the condition checking area above
            SparseBitSet childReunion = new SparseBitSet();

            SparseBitSet tempBitMask = intersection;
            for (int k = forState.childStatesIndexes.size() - 1; k >= 0; k--) {//mergem mai intai prin pathul cel mai putin prioritar. In caz ca acesta este un "escape path" catre stare terminala, multe pozitii devin 1, si astfel vor fi eliminate din cautarile facute pe alte path-uri.
                //for (int k = 0; k < forState.childStatesIndexes.size(); k++) {
                int childIndex = forState.childStatesIndexes.get(k);
                tempBitMask.locked = true;
                SparseBitSet childRez = BuildPTA(sm, input, sm.states.get(childIndex), tempBitMask, depthInMatch, jumpNodesStack, ANY, ptaCache);

                if (childRez != null && !childRez.isEmpty()) {
                    if (childRez.cardinality() == intersection.cardinality()) {//nothing is filtered by this child, so there is no need to check the other children
                        childReunion = intersection;
                        break;
                    }

                    if (childReunion.isEmpty()) {
                        childReunion = childRez;
                    } else {
                        if (childReunion.locked)
                            childReunion = childReunion.clone();
                        childReunion.or(childRez);
                    }

                    if (!childRez.isEmpty() && k > 0) {//daca k e 0, inseamna ca nu o sa mai fie inca un buildPTA pt inca un copil si atunci nu are rost sa mai micsoram tempBitMask
                        //if (!childRez.isEmpty() && k < forState.childStatesIndexes.size() - 1) {
                        if (tempBitMask == intersection)
                            tempBitMask = tempBitMask.clone();
                        tempBitMask.locked = false;
                        tempBitMask.andNot(childRez); //for next child states, don't consider the positions for which we have already determined that are selected, because they cannot modify the result
                    }
                }
                if (tempBitMask.cardinality() == 0)//all positions from intersection match so there is no need to find positions for the rest of the children - they cannot modify the result
                    break;
            }
            intersection = childReunion;
        }

        if (forState.isGraphNodeEntry) //if it was a graph node entry state
            //remove it from the jumpsStack (because it was pushed before the recursive calls
            jumpNodesStack.pop();
        if (forState.isGraphNodeExit) //if it is a graph node exit state, push it back in the jumpsStack (because it was popped before the recursive calls)
            jumpNodesStack.push(forState.parentGraphNode);

        if (cached) {
            if (bitMaskBackup != null) {//partial cache was present - unify the results with the partial cache
                bitMask = bitMaskBackup;
                if (intersection.locked)
                    intersection = intersection.clone();
                intersection.or(cachedRez.getValue());
            }
            if (additionalCacheInfo != null) {
                SparseBitSet additionalCacheInfoNew = additionalCacheInfo.clone();
                additionalCacheInfoNew.xor(ANY);//not
                additionalCacheInfoNew.or(bitMask);
                ptaCache.Set(forState, depthInMatchBackup, jumpNodesStack, additionalCacheInfoNew, intersection);
            } else
                ptaCache.Set(forState, depthInMatchBackup, jumpNodesStack, bitMask, intersection);
        }
        return intersection;
    }

    static void ShiftOnSentences(SparseBitSet bi, IndexedLuceneCorpus input, int n) {//n < 0
        if (bi.getLength() <= Math.abs(n)) {
            bi.clear();
            return;
        }
        if (n == 0 || bi.cardinality() == 0)
            return;

        SparseBitSet shiftMask = getShiftMask(input, n);
        bi.Shift(n);
        bi.and(shiftMask);
    }

    static SparseBitSet getShiftMask(INlpCorpus input, int n) {
        if (searchCache.containsKey(input))
            if (searchCache.get(input).shiftMasks.containsKey(n))
                return searchCache.get(input).shiftMasks.get(n);

        SparseBitSet ret = new SparseBitSet();
        ret.set(0, input.getTokenCount(), true);
        if (n == 0)
            return ret;

        for (int i = input.getSentenceCount() - 1; i >= 0; i--) {
            INlpSentence s = input.getSentence(i);
            if (n < 0) {
                if (s.getFirstTokenIndexInCorpus() + s.getTokenCount() + n > 0)
                    ret.set(s.getFirstTokenIndexInCorpus() + s.getTokenCount() + n, false);
            } else {
                if (s.getTokenCount() >= n)
                    ret.set(s.getFirstTokenIndexInCorpus() + (n - 1), false);
            }
        }

        if (n < -1)
            ret.and(getShiftMask(input, n + 1));
        else if (n > 1)
            ret.and(getShiftMask(input, n - 1));

        ret.locked = true;
        if (!searchCache.containsKey(input))
            searchCache.put(input, new SearchCache());
        searchCache.get(input).shiftMasks.put(n, ret);
        return ret;
    }

    public static PTAcache newPTAcache(INlpCorpus input) {
        if (input instanceof IndexedLuceneCorpus)
            return new PTAcache((IndexedLuceneCorpus) input);
        else if (((InmemoryCorpus) input).hasIndexedVersion()) {
            try {
                return new PTAcache(((InmemoryCorpus) input).getIndexedVersion());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    static class SearchCache {
        HashMap<Integer, SparseBitSet> shiftMasks = new HashMap<>();
        HashMap<String, Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>>> positionsForMatchingAnnotations = new HashMap<>();
        HashMap<String, SparseBitSet> positionsForMatchingTokens = new HashMap<>();
        WeakHashMap<StateMachine, HashMap<Integer, SparseBitSet>> reducedSearchSpaces = new WeakHashMap<>();//for a state machine, cache computed search spaces based on the starting state in the state machine (start state is given by index - Integer)

        public int countShiftMasks() {
            return shiftMasks.size();
        }

        public int countReducedSearchSpaces() {
            HashSet<SparseBitSet> set = new HashSet<>();
            for (Map.Entry<StateMachine, HashMap<Integer, SparseBitSet>> stateMachineHashMapEntry : reducedSearchSpaces.entrySet()) {
                for (SparseBitSet sparseBitSet : stateMachineHashMapEntry.getValue().values()) {
                    set.add(sparseBitSet);
                }

            }
            return reducedSearchSpaces.size();
        }

        public int countPositionsForMatchingTokens() {
            HashSet<SparseBitSet> set = new HashSet<>();
            for (SparseBitSet bitSet : positionsForMatchingTokens.values()) {
                set.add(bitSet);
            }
            return set.size();
        }

    }

    static class PTAcache {
        public IndexedLuceneCorpus input;
        //Map <State, Map<Integer, Map<String, Pair<SparseBitSet, SparseBitSet>>>> cache = new HashMap<State, Map<Integer, Map<String, Pair<SparseBitSet, SparseBitSet>>>>();
        Map<State, Map<String, Pair<SparseBitSet, SparseBitSet>>> cache = new HashMap<State, Map<String, Pair<SparseBitSet, SparseBitSet>>>();

        public PTAcache(IndexedLuceneCorpus input) {
            this.input = input;
        }

        public int count() {
            int count = 0;
            for (Map.Entry<State, Map<String, Pair<SparseBitSet, SparseBitSet>>> entry : cache.entrySet()) {
                count += entry.getValue().size();
            }
            return count;
        }

        //the entries in cache are maintained in unshifted manner. This way they can be used for various offsets.
        //must specify the offset (depth) of the given shiftedBitMask to correctly retrieve the cached values of the bits covered by the given mask
        //returns the cached bitset as the value of the pair. The key represents the bits which were covered by the cache - a subset of shiftedBitMask.
        //if result.key.cardinality == shiftedBitMask.cardinality then it means that all bits were covered by the cache
        //why is the jump stack necessary? because an equal jump stack indicates the returning path to the ending node. the partial results on the same node but different jump paths are not overlapping. The paths to the end node are different, even though the node is the same, the jump paths differ, which dictate different path to the end. They are actually different paths in grammar.
        public Pair<SparseBitSet, SparseBitSet> Get(State forState, int depth, Stack<GraphNode> jumpStack, SparseBitSet shiftedBitMask) {
            Map<String, Pair<SparseBitSet, SparseBitSet>> forStateCache = cache.get(forState);
            if (forStateCache == null)
                return null;
            String key = keyForJumpStack(jumpStack);
            Pair<SparseBitSet, SparseBitSet> cachedUnshiftedMask2UnshiftedRez = forStateCache.get(key);
            if (cachedUnshiftedMask2UnshiftedRez == null)
                return null;
            //check if prevStartingPositions is contained in the prevPoses from cache
            SparseBitSet unshiftedBitMask = shiftedBitMask.clone();
            ShiftOnSentences(unshiftedBitMask, input, depth);

            //B contained by A =  |A and B|  == |B|
            unshiftedBitMask.and(cachedUnshiftedMask2UnshiftedRez.getKey());

            if (unshiftedBitMask.cardinality() == 0)
                return null;//the cached bitmask and the requested bitmask don't intersect at all - return null

            SparseBitSet shiftedBitMaskIntersection = unshiftedBitMask.clone();
            ShiftOnSentences(shiftedBitMaskIntersection, input, -depth);

            SparseBitSet shiftedRezIntersection = cachedUnshiftedMask2UnshiftedRez.getValue().clone();
            ShiftOnSentences(shiftedRezIntersection, input, -depth);
            shiftedRezIntersection.and(shiftedBitMaskIntersection);
            return new Pair<SparseBitSet, SparseBitSet>(shiftedBitMaskIntersection, shiftedRezIntersection);
        }

        public void Set(State forState, int depth, Stack<GraphNode> jumpStack, SparseBitSet shiftedBitmask, SparseBitSet shiftedRez) {
            Map<String, Pair<SparseBitSet, SparseBitSet>> forStateCache = cache.get(forState);
            if (forStateCache == null)
                cache.put(forState, forStateCache = new HashMap<String, Pair<SparseBitSet, SparseBitSet>>());

            String key = keyForJumpStack(jumpStack);
            Pair<SparseBitSet, SparseBitSet> prevToRez = forStateCache.get(key);
            if (depth != 0) {
                shiftedRez = shiftedRez.clone();
                shiftedBitmask = shiftedBitmask.clone();
            }
            ShiftOnSentences(shiftedBitmask, input, depth);
            ShiftOnSentences(shiftedRez, input, depth);
            if (prevToRez == null) {
                shiftedRez.locked = true;
                shiftedBitmask.locked = true;
                forStateCache.put(key, new Pair<SparseBitSet, SparseBitSet>(shiftedBitmask, shiftedRez));
            } else {
                prevToRez.getKey().locked = false;
                prevToRez.getKey().or(shiftedBitmask);
                prevToRez.getKey().locked = true;
                prevToRez.getValue().locked = false;
                prevToRez.getValue().or(shiftedRez);
                prevToRez.getValue().locked = true;
            }
        }

        public static String keyForJumpStack(Stack<GraphNode> jumpStack) {
            StringBuilder sb = new StringBuilder();
            for (GraphNode gn : jumpStack)
                sb.append(gn.getIndex()).append(" -> ");
            return sb.toString();
        }
    }
}
