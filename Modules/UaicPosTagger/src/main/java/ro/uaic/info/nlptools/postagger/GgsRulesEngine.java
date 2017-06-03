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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ro.uaic.info.nlptools.postagger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import ro.uaic.info.nlptools.corpus.*;
import ro.uaic.info.nlptools.ggs.engine.core.CompiledGrammar;
import ro.uaic.info.nlptools.ggs.engine.core.GGSException;
import ro.uaic.info.nlptools.ggs.engine.core.Match;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import ro.uaic.info.nlptools.ggs.engine.grammar.Graph;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;
import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;

/**
 *
 * @author Radu
 */
class GgsRulesEngine {

    private CompiledGrammar compiledGrammar;
    public Grammar grammar = null;
    public List<Graph> rulesGraphs;

    public boolean load(InputStream file) throws GGSException {
        if (file == null) {
            return false;
        }

        try {
            grammar = new Grammar(file);
        } catch (IOException ex) {
            Logger.getLogger(GgsRulesEngine.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (SAXException ex) {
            Logger.getLogger(GgsRulesEngine.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(GgsRulesEngine.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        compiledGrammar = new CompiledGrammar(grammar);
        rulesGraphs = new ArrayList<Graph>();
        for (GraphNode gn : grammar.getGraphs().get("Main").getGraphNodes().values()) {
            if (gn != null) {
                for (String clause : gn.getTokenMatchingCode().split("\n")) {
                    if (clause.startsWith(":")) {
                        rulesGraphs.add(grammar.getGraphs().get(clause.substring(1)));
                    }
                }
            }
        }
        return true;
    }

    public void process(SentenceStruct s) throws Exception {
        INlpCorpus t = new InmemoryCorpus();
        InmemorySentence ggsSent = new InmemorySentence();
        for (WordStruct ws : s) {
            ggsSent.addToken(new Token());
        }

        boolean isGgsSentDirty = true;
        for (Graph graph : rulesGraphs) {
            compiledGrammar.SetMainGraph(graph);
            for (int i = 0; i < s.size(); i++) {//for each index in the sentence, apply the curent rule, from left to right
                //first update the features of the words if they were modified by the last applied rule
                if (isGgsSentDirty) {
                    for (int k = 0; k < s.size(); k++) {
                        Token word = ggsSent.getToken(k);


                        WordStruct ws = s.get(k);
                        //check if a modification has been done
                        int newsize = ws.possibleAnnotations.size() + 2 + 1; // what will be the new size of word.getFeatures()
                        if (ws.inDict) {
                            newsize += ws.possibleAnnotations.size() - 1;
                        }
                        if (newsize == word.getFeatures().size())
                            continue;

                        word.getFeatures().clear();
                        word.getFeatures().put("WORD", ws.word);
                        word.getFeatures().put("in_dict", ws.inDict + "");
                        for (int j = 0; j < ws.possibleAnnotations.size(); j++) {
                            UaicMorphologicalAnnotation uaicMorphologicalAnnotation = ws.possibleAnnotations.get(j);
                            word.getFeatures().put("msd" + j, uaicMorphologicalAnnotation.getMsd());
                        }
                        if (ws.inDict) {
                            for (int j = 0; j < ws.possibleAnnotations.size(); j++) {
                                UaicMorphologicalAnnotation uaicMorphologicalAnnotation = ws.possibleAnnotations.get(j);
                                word.getFeatures().put("lemma" + j, uaicMorphologicalAnnotation.getLemma());
                            }
                        } else {
                            word.getFeatures().put("lemma0", ws.word);
                        }
                    }
                    isGgsSentDirty = false;
                }

                Match m = compiledGrammar.GetMatch(ggsSent.getToken(i));
                if (m != null) {
                    for (SpanAnnotation a : m.getSpanAnnotations()) {
                        String action = a.getName();
                        Pattern regex = Pattern.compile(a.getFeatures().get("regex"));
                        List<UaicMorphologicalAnnotation> newUaicMorphologicalAnnotations = new ArrayList<>();
                        for (UaicMorphologicalAnnotation uaicMorphologicalAnnotation : s.get(a.getStartTokenIndex()).possibleAnnotations) {
                            if ((action.equals("KEEP") && regex.matcher(uaicMorphologicalAnnotation.getMsd()).matches())
                                    || (action.equals("REMOVE") && !regex.matcher(uaicMorphologicalAnnotation.getMsd()).matches())) {
                                newUaicMorphologicalAnnotations.add(uaicMorphologicalAnnotation);
                            }
                        }
                        if (!newUaicMorphologicalAnnotations.isEmpty()) {
                            s.get(a.getStartTokenIndex()).possibleAnnotations = newUaicMorphologicalAnnotations;
                            isGgsSentDirty = true;
                        }
                    }
                }
            }
        }
    }
}
