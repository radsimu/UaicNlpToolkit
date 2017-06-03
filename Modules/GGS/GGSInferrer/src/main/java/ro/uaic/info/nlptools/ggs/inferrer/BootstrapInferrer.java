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
import ro.uaic.info.nlptools.ggs.engine.core.CompiledGrammar;
import ro.uaic.info.nlptools.ggs.engine.core.GGSException;
import ro.uaic.info.nlptools.ggs.engine.core.Match;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;

import java.io.IOException;
import java.util.*;

public class BootstrapInferrer {

    public Grammar BootStrapBasedOnCorpus(InmemoryCorpus inputText) throws GGSException, IOException {
        Inferrer inferrer = new Inferrer();
        List<List<Token>> positiveExamples = inferrer.ExtractExampleSequencesFromCorpus(inputText);
        Grammar g = null;
        EvalResult eval = null;

        int lastIncorrectRecallSize = -1;
        int lastnotRecalledSize = -1;
        List<List<Token>> negativeExamples = new ArrayList<List<Token>>();
        while (eval == null || eval.incorrectlyRecalledTokens.size() != lastIncorrectRecallSize || eval.notRecalledTokens.size() != lastnotRecalledSize) {
            if (eval != null)
            {
                lastIncorrectRecallSize = eval.incorrectlyRecalledTokens.size();
                lastnotRecalledSize = eval.notRecalledTokens.size();
            }
            g = inferrer.inferFromExampleSet(positiveExamples, negativeExamples);

            CompiledGrammar compiledGrammar = new CompiledGrammar(g);
            List<Match> matches = compiledGrammar.GetMatches(inputText, false);
            List<List<Token>> matched = new ArrayList<List<Token>>();
            for (Match m : matches)
                matched.add(m.getTokens());

            eval = EvalAndCompare(positiveExamples, matched);
            if (eval.incorrectlyRecalledTokens.size() == 0)
                break;

            for (Map.Entry<List<Token>, List<Token>> entry : eval.incorrectMatchesToExampleSequences.entrySet())
                if (entry.getValue() != null) {
                    if (entry.getKey().get(0).getTokenIndexInSentence() < entry.getValue().get(0).getTokenIndexInSentence() ||
                            entry.getKey().get(entry.getKey().size() - 1).getTokenIndexInSentence() > entry.getValue().get(entry.getValue().size() - 1).getTokenIndexInSentence())
                        negativeExamples.add(entry.getKey());
                } else
                    negativeExamples.add(entry.getKey());
        }

        return g;
    }

    public EvalResult EvalAndCompare(List<List<Token>> gold, List<List<Token>> test) {
        EvalResult rez = new EvalResult();
        List<Token> goldTokens = new ArrayList<Token>();
        for (List<Token> l : gold)
            goldTokens.addAll(l);

        List<Token> testTokens = new ArrayList<Token>();
        for (List<Token> l : test)
            testTokens.addAll(l);

        List<Token> goldOnlyTokens = new ArrayList<Token>(goldTokens);
        goldOnlyTokens.removeAll(testTokens);

        List<Token> testOnlyTokens = new ArrayList<Token>(testTokens);
        testOnlyTokens.removeAll(goldTokens);

        rez.totalTokensCountInExamples = goldTokens.size();
        rez.notRecalledTokens = goldOnlyTokens;
        rez.incorrectlyRecalledTokens = testOnlyTokens;

        //obtain the incorrect matches in test
        for (Token t : testOnlyTokens)
            for (List<Token> l : test)
                if (l.contains(t) && rez.incorrectMatchesToExampleSequences.get(l) == null) {
                    rez.incorrectMatchesToExampleSequences.put(l, null);
                    for (List<Token> g : gold)
                        if (SequenceIntersects(l, g)) {
                            rez.incorrectMatchesToExampleSequences.put(l, g);
                            break;
                        }
                }

        return rez;
    }

    public boolean SequenceIntersects(List<Token> a, List<Token> b) {
        if (a.get(0).getParentSentence().getSentenceIndexInCorpus() != b.get(0).getParentSentence().getSentenceIndexInCorpus())
            return false;

        int as = a.get(0).getTokenIndexInSentence();
        int ae = a.get((a.size() - 1)).getTokenIndexInSentence();
        int bs = b.get(0).getTokenIndexInSentence();
        int be = b.get(b.size() - 1).getTokenIndexInSentence();
        return ((as <= be && as >= bs) || (ae <= be && ae >= bs));
    }
}
