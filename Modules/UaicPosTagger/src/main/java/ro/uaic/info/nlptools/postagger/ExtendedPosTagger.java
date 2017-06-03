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

package ro.uaic.info.nlptools.postagger;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.BeamSearch;
import opennlp.tools.util.SequenceValidator;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

class ExtendedPosTagger extends POSTaggerME {

    public UaicMorphologicalDictionary dict;
    public Set<String> guesserTagset;
    public GgsRulesEngine GgsRulesEngine;
    //desters
    public POSModel thePosModel;

    private class HybridPosSequenceValidator implements SequenceValidator<String> {

        public SentenceStruct curent;

        public boolean validSequence(int i, String[] inputSequence, String[] outcomesSequence, String outcome) {
            if (curent.get(i).possibleAnnotations == null || curent.get(i).possibleAnnotations.isEmpty()) {//nu a fost gasit in dictionar deci se merge la ghici
                return true;
            } else {
                for (UaicMorphologicalAnnotation ann : curent.get(i).possibleAnnotations) {
                    if (ann.getMsd().equals(outcome)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    private HybridPosSequenceValidator seqValidator;

    public ExtendedPosTagger(POSModel pm, UaicMorphologicalDictionary dic, Set<String> guesserTagset, GgsRulesEngine GGSre) {
        super(pm);
        this.thePosModel = pm;
        MyPOSContextGenerator myContextGen = new MyPOSContextGenerator(dic);
        super.contextGen = myContextGen;

        seqValidator = new HybridPosSequenceValidator();
        beam = new BeamSearch<String>(size, contextGen, posModel, seqValidator, 0);
        dict = dic;
        GgsRulesEngine = GGSre;
        this.guesserTagset = guesserTagset;
    }

    @Override
    public String[] tag(String[] sent) {

        SentenceStruct inS = new SentenceStruct();

        for (String w : sent) {
            WordStruct inW = new WordStruct();
            inW.word = w;
            inS.add(inW);
        }
        preprocessSentence(inS);
        seqValidator.curent = inS;
        return super.tag(sent);
    }

    @Override
    public List<String> tag(List<String> sent) {

        SentenceStruct inS = new SentenceStruct();

        for (String w : sent) {
            WordStruct inW = new WordStruct();
            inW.word = w;
            inS.add(inW);
        }
        preprocessSentence(inS);
        seqValidator.curent = inS;
        return super.tag(sent);
    }

    public SentenceStruct tag(SentenceStruct sentence) {
        preprocessSentence(sentence);
        seqValidator.curent = sentence;
        String[] sent = new String[sentence.size()];
        for (int i = 0; i < sentence.size(); i++) {
            sent[i] =  sentence.get(i).word;
        }
        String[] rez;
        try {
            rez = super.tag(sent);
        } catch (Exception ex) {
            System.err.println("OpennNLP couldn't tag sentence " + sentence.id);
            return null;
        }
        SentenceStruct ret = new SentenceStruct();
        for (int i = 0; i < rez.length; i++) {
            WordStruct w = new WordStruct();
            w.word = sentence.get(i).word;
            w.end = sentence.get(i).end;
            w.start = sentence.get(i).start;
            w.parentSentence = ret;
            w.id = sentence.get(i).id;
            w.inDict = sentence.get(i).inDict;
            for (UaicMorphologicalAnnotation a : sentence.get(i).possibleAnnotations) {
                if (a.getMsd().equals(rez[i])) {
                    w.possibleAnnotations.add(a);
                }
            }
            if (w.possibleAnnotations.size() > 1) {//resolve lemma ambiguity
                //case still ambiguous, just pick the most frequent lemma in rombac
                Collections.sort(w.possibleAnnotations, new Comparator<UaicMorphologicalAnnotation>() {
                    public int compare(UaicMorphologicalAnnotation t, UaicMorphologicalAnnotation t1) {
                        return -Integer.compare(dict.lemmas.get(t.getLemma()), dict.lemmas.get(t1.getLemma()));
                    }
                });

                if (dict.lemmas.get(w.possibleAnnotations.get(0).getLemma()) == dict.lemmas.get(w.possibleAnnotations.get(1).getLemma()))// still ambiguous, lemmas priorities didn't help :(
                {
                    //still freaking ambiguous :(
                    //Logger.getLogger(ExtendedPosTagger.class.getName()).log(Level.INFO, "word \"" + w.word + "\" ambiguous lemmas: " + w.possibleAnnotations.get(0).getLemma() + "(chosen), " + w.possibleAnnotations.get(1).getLemma());
                }
            }

            if (w.possibleAnnotations.isEmpty()) {//something went reeeealy wrong
                Logger.getLogger(ExtendedPosTagger.class.getName()).log(Level.SEVERE, null, new Exception("Severe error on word \"" + w.word + "\"!!!"));
            }

            ret.add(w);
        }
        ret.id = sentence.id;
        ret.offset = sentence.offset;
        ret.text = sentence.text;
        return ret;
    }

    public void preprocessSentence(SentenceStruct sentence) {
        for (WordStruct word : sentence) {
            if (!word.possibleAnnotations.isEmpty()) {
                continue;
            }
            word.possibleAnnotations = new ArrayList<>();
            Set<UaicMorphologicalAnnotation> annotations = dict.get(word.word);
            if (annotations == null) {
                annotations = dict.get(word.word.replaceAll("-", ""));
            }
            if (annotations != null) {
                word.possibleAnnotations = new ArrayList<>(annotations);
                word.inDict = true;
            } else {
                //cuvantul nu exista in dict                
                word.possibleAnnotations = new ArrayList<>();
                for (String msd : guesserTagset) {
                    UaicMorphologicalAnnotation an = new UaicMorphologicalAnnotation(word.word, msd, null, "NotInDict");
                    word.possibleAnnotations.add(an);
                }
            }
        }


////Title Case text, special behavior. If all text is Title Case, annotate as normal. Otherwise, annotate Title Case words as proper names, ignoring their possible spanAnnotations
//        boolean titleCase = true;
//
//        for (WordStruct word : sentence) {
//            //find a non functional word starting with lower case. if found , then this is not a title case sentence
//            if (titleCase && Character.isLowerCase(word.word.charAt(0))) {
//                for (UaicMorphologicalAnnotation an : word.possibleAnnotations) {
//                    if (an.getMsd().matches("[NVA]\\p{Ll}.*")) {
//                        titleCase = false;
//                        break;
//                    }
//                }
//            }
//        }
//        if (!titleCase) {
//            for (int i = 1; i < sentence.size(); i++) {
//                WordStruct word = sentence.get(i);
//                if (word.word.matches("\\p{Lu}.*")) {
//                    Set<SpanAnnotation> newAnnotations = new HashSet<SpanAnnotation>();
//                    boolean skip = false;
//                    for (SpanAnnotation ann : word.possibleAnnotations) {
//                        if (ann.getMsd().startsWith("Np")) {
//                            newAnnotations.add(ann);
//                        }
//                        if (ann.getMsd().startsWith("P") || ann.getMsd().startsWith("D"))//if it is a possible pronoun, it is just better to leave it for cases such as "cuvantul Lui Dumnezeu"
//                        {
//                            skip = true;
//                            break;
//                        }
//                    }
//                    if (skip) {
//                        continue;
//                    }
//                    if (newAnnotations.size() == 0) {
//                        for (SpanAnnotation ann : word.possibleAnnotations) {
//                            if (ann.getMsd().startsWith("Nc")) {
//                                SpanAnnotation a = new SpanAnnotation(word.word, "Np" + ann.msd.substring(2), ann.getLemma(), "PropperNameCaseOverride");
//                                a.msd = dict.correctMSDTag(a.msd);
//                                newAnnotations.add(a);
//                            } else if (ann.getMsd().startsWith("Af")) {
//                                SpanAnnotation a = new SpanAnnotation(word.word, "Np" + ann.msd.substring(3), ann.getLemma(), "PropperNameCaseOverride");
//                                a.msd = dict.correctMSDTag(a.msd);
//                                newAnnotations.add(a);
//                            }
//                        }
//                    }
//                    if (newAnnotations.size() == 0) {
//                        for (String str : guesserTagset) {
//                            if (str.startsWith("Np")) {
//                                newAnnotations.add(new SpanAnnotation(word.word, str, word.word, "PropperNameCaseOverride"));
//                            }
//                        }
//                    }
//                    word.possibleAnnotations = new ArrayList<SpanAnnotation>();
//                    word.possibleAnnotations.addAll(newAnnotations);
//                }
//            }
//        }
        if (GgsRulesEngine != null) {
            try {
                GgsRulesEngine.process(sentence);
            } catch (Exception ex) {
                Logger.getLogger(ExtendedPosTagger.class.getName()).log(Level.SEVERE, "error apply-ing ggsRules - proceed without rules for sentence " + sentence.toString(), ex);
            }
        }
    }
}
