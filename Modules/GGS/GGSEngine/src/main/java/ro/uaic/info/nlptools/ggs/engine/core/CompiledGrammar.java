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

import ro.uaic.info.nlptools.corpus.INlpCorpus;
import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import ro.uaic.info.nlptools.ggs.engine.grammar.Graph;

import java.io.IOException;
import java.util.List;

public class CompiledGrammar {
    public boolean createIndexForInputBeforeSearching = false;
    StateMachine stateMachine;
    Grammar grammar;
    int startStateIndex;

//TODO: compute paths priorities automatically
    public CompiledGrammar(Grammar g, String customMainGraph) throws GGSException {
        grammar = g;
        stateMachine = new StateMachine(this);
        stateMachine.compile(grammar);
        SetMainGraph(grammar.getGraphs().get(customMainGraph));
        List<State> loop = stateMachine.findInfiniteLoop(startStateIndex);
        if (loop != null) {
            throw new GGSInfiniteLoopException(loop);
        }

        List<State> emptyPath = stateMachine.findEmptyMatchingPath(startStateIndex);
        if (emptyPath != null) {
            throw new GGSNullMatchException(emptyPath);
        }
    }

    public CompiledGrammar(Grammar g) throws GGSException {
        this(g, "Main");
    }

    public void SetMainGraph(Graph customMainGraph) {
        if (customMainGraph == null)
            customMainGraph = grammar.getGraphs().get("Main");
        startStateIndex = stateMachine.compNodes.get(customMainGraph.getStartNode()).startStateIndex;
        stateMachine.terminalStateIndex = stateMachine.compNodes.get(customMainGraph.getEndNode()).endStateIndex;
    }


    public List<Match> GetMatches(INlpCorpus input) throws GGSException, IOException {
        return GetMatches(input, createIndexForInputBeforeSearching);
    }

    public List<Match> GetMatches(INlpCorpus input, boolean indexedSearch) throws GGSException, IOException {
        return GetMatches(input, 0, indexedSearch);
    }

    public List<Match> GetMatches(INlpCorpus input, int maxMatchSize, boolean indexedSearch) throws GGSException, IOException {

        long time = System.currentTimeMillis();
        if (indexedSearch && input instanceof InmemoryCorpus){
            ((InmemoryCorpus) input).getIndexedVersion();
        }
        List<Match> rez = stateMachine.run(startStateIndex, input, maxMatchSize, false, indexedSearch, SearchSpaceReducer.newPTAcache(input));
        System.out.println("Finished in " + (System.currentTimeMillis() - time));
        return rez;
    }


    public List<Match> GetMatches(INlpSentence sentence) throws GGSException, IOException {
        return GetMatches(sentence, 0, createIndexForInputBeforeSearching);
    }

    public List<Match> GetMatches(INlpSentence sentence, int maxMatchSize) throws GGSException, IOException {
        return GetMatches(sentence, maxMatchSize, createIndexForInputBeforeSearching);
    }

    public List<Match> GetMatches(INlpSentence sentence, boolean indexedSearch) throws GGSException, IOException {
        return GetMatches(sentence, 0, indexedSearch);
    }

    public List<Match> GetMatches(INlpSentence sentence, int maxMatchSize, boolean indexedSearch) throws GGSException, IOException {
        if (indexedSearch && sentence.getParentCorpus() instanceof InmemoryCorpus){
            ((InmemoryCorpus) sentence.getParentCorpus()).getIndexedVersion();
        }
        return stateMachine.run(startStateIndex, sentence, maxMatchSize, false, indexedSearch, SearchSpaceReducer.newPTAcache(sentence.getParentCorpus()));
    }

    public Match GetMatch(Token t) throws GGSException, IOException {
        return GetMatch(t, 0);
    }

    public Match GetMatch(Token t, int maxMatchSize) throws GGSException, IOException {
        return GetMatch(t, maxMatchSize, createIndexForInputBeforeSearching);
    }

    public Match GetMatch(Token t, boolean indexedSearch) throws GGSException, IOException {
        return GetMatch(t, 0, indexedSearch);
    }

    public Match GetMatch(Token t, int maxMatchSize, boolean indexedSearch) throws GGSException, IOException {
        return stateMachine.states.get(startStateIndex).Match(t.getParentSentence(), t.getTokenIndexInSentence(), stateMachine.terminalStateIndex, maxMatchSize, false, indexedSearch, null);
    }

    public void RequestStop() {
        stateMachine.requestStop = true;
    }
}
