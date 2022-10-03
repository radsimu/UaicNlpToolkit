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

package ro.uaic.info.nlptools.tools;

import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.corpus.InmemorySentence;
import ro.uaic.info.nlptools.corpus.Token;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UaicTokenizer {

    static Pattern basic_tokens = Pattern.compile("([\\p{L}\\p{Nd}]+)|([\\s\\p{Z}]+)|(.)");
    static Pattern WORD_REGEXP = Pattern.compile( // e folosit pentru stabilirea tokenilor din prima faza
            //tokenul normal
            //     prima litera                                               poate fi - doar daca urmeaza o cifra
            "(?:([\\p{Lu}\\p{Ll}\\p{Lo}\\p{Lm}\\p{Nd}\\p{Mn}\\p{Mc}_'’‘]   |   \\p{Pd}(?=\\p{Nd}))      "
            //  restul de caractere
            + "(?:[\\p{Lu}\\p{Ll}\\p{Lo}\\p{Lm}\\p{Nd}\\p{Mn}\\p{Mc}_'’‘]   |"
            //  daca se ia punctul sau nu in token
            + "(?:(?<!\\.)\\.(?![\\s\\p{Z}]+\\p{Lu}|\\.|\\p{Lu}\\p{Ll}))   |"
            //  daca se ia virgula si punct, la numere cu virgula
            + "(?:(?<=\\p{Nd})[,\\.](?=\\p{Nd}))   |"
            //  daca se ia - (nu se ia daca in fata e cifra : 50-5)
            + "(?:(?<!\\p{Nd})\\p{Pd})   )*"
            // ultimul caracter
            + "[\\p{Lu}\\p{Ll}\\p{Lo}\\p{Lm}\\p{Nd}\\p{Mn}\\p{Mc}_'’‘]   )"
            //orice alt simbol se ia separat
            + "|"
            + "[^\\p{Z}\\s]",
            Pattern.COMMENTS);

    private UaicMorphologicalDictionary morphologicalDictionary;

    public UaicTokenizer(UaicMorphologicalDictionary morphologicalDictionary){
        this.morphologicalDictionary = morphologicalDictionary;
    }

    private static InmemorySentence tokenize(UaicMorphologicalDictionary dic, SentenceHunk sentence) throws InstantiationException, IllegalAccessException {
        return tokenize(dic, sentence, null);
    }

    private static InmemorySentence tokenize(UaicMorphologicalDictionary dic, SentenceHunk sentenceHunk, List<Integer> nonbreakableBoundaries) throws InstantiationException, IllegalAccessException {
        if (nonbreakableBoundaries == null) {
            nonbreakableBoundaries = UaicSegmenter.findNonbreakable(sentenceHunk.text);
        }

        InmemorySentence sent = new InmemorySentence();
        List<Token> toksTemp = new LinkedList<Token>();
        List<Token> toks = new LinkedList<Token>();
        // mai intai fac o tokenizare rudimentara
        Matcher m = basic_tokens.matcher(sentenceHunk.text);
        while (m.find()) {
            Token token = new Token();
            token.setCharStartIndexInSentence(m.start());
            token.setCharEndIndexInSentence(m.end());
            token.setWordForm(m.group());
            token.setParentSentence(sent);
            if (token.getWordForm().matches("[\\p{Z}\\s]+")) {
                token.setWordForm("~");
            }
            toksTemp.add(token);
        }

        // stabilesc tokenii compusi
        if (dic != null) {
            for (int i = 0; i < toksTemp.size(); i++) {
                TokenTrieNode current = dic.compCuvsTrie;
                TokenTrieNode next = null;
                int k = 0;
                int bestk = 0;

                while (i + k < toksTemp.size() && (next = current.nextToks.get(UaicMorphologicalDictionary.getCanonicalWord(toksTemp.get(i + k).getWordForm()))) != null) {
                    current = next;
                    if (current.isLeaf) {
                        bestk = k;
                    }
                    k++;
                }
                if (bestk > 0) {
                    Token token = new Token();
                    token.setCharStartIndexInSentence(toksTemp.get(i).getCharStartIndexInSentence());
                    token.setCharEndIndexInSentence(toksTemp.get(i + bestk).getCharEndIndexInSentence());
                    token.setWordForm(sentenceHunk.text.substring(token.getCharStartIndexInSentence(), token.getCharEndIndexInSentence()));
                    token.setParentSentence(sent);
                    if (i + bestk + 1 == toksTemp.size() && toksTemp.get(i + bestk).getWordForm().equals(".") && dic.containsKey(sentenceHunk.text.substring(token.getCharStartIndexInSentence(), toksTemp.get(i + bestk - 1).getCharEndIndexInSentence()))) {
                        //nu lua in calcul acest token compus
                    } else {
                        toks.add(token);
                    }
                }
            }
        }

        //apoi stabilesc tokenii pe baza paternului simplu
        m = WORD_REGEXP.matcher(sentenceHunk.text);

        while (m.find()) {
            Token token = new Token();
            token.setCharStartIndexInSentence(m.start());
            token.setCharEndIndexInSentence(m.end());
            token.setWordForm(m.group());
            token.setParentSentence(sent);
            toks.add(token);
        }

        for (int i = 0; i < nonbreakableBoundaries.size(); i += 2) {
            if (nonbreakableBoundaries.get(i) < sentenceHunk.offset) {
                continue;
            }
            if (nonbreakableBoundaries.get(i + 1) > sentenceHunk.text.length() + sentenceHunk.offset) {
                break;
            }
            Token ws = new Token();
            ws.setCharStartIndexInSentence(nonbreakableBoundaries.get(i) - sentenceHunk.offset);
            ws.setCharEndIndexInSentence(nonbreakableBoundaries.get(i + 1) - sentenceHunk.offset);
            ws.setWordForm(sentenceHunk.text.substring(ws.getCharStartIndexInSentence(), ws.getCharEndIndexInSentence()).replaceAll("\\p{Pd}", "-"));
            ws.setParentSentence(sent);
            toks.add(ws);
        }

        //si sortez lista de tokeni, dupa pozitiile de start
        Collections.sort(toks, new Comparator<Token>() {

            public int compare(Token o1, Token o2) {
                if (o1.getCharStartIndexInSentence() < o2.getCharStartIndexInSentence()) {
                    return -1;
                }
                return 1;
            }
        });

        //acum unific tokenii, adica, daca exita 2 tokeni care se intersecteaza, fac din ei unul singur        
        UnifyToks(toks, sentenceHunk.text);


        //iar acum, ma mai uit o data peste tokenii care au cratima

        List<Token> newToks = (List<Token>) toks.getClass().newInstance();

        for (int i = 0; i < toks.size(); i++) {
            Token bigTok = toks.get(i);

            boolean doSplit = true;
            for (int k = 0; k < nonbreakableBoundaries.size(); k += 2) {
                if (nonbreakableBoundaries.get(k) > bigTok.getCharEndIndexInSentence() + sentenceHunk.offset) {
                    break;
                }
                if (bigTok.getCharStartIndexInSentence() + sentenceHunk.offset == nonbreakableBoundaries.get(k)) {
                    doSplit = false;
                    break;
                }
            }

            if (doSplit && toks.get(i).getWordForm().length() > 2 && toks.get(i).getWordForm().substring(1, toks.get(i).getWordForm().length() - 1).contains("-") && (dic == null || dic.get(toks.get(i)) == null)) {
                //trebuie sa maximizez numarul de tokeni care se gasesc in dictionar

                //in bucata de mai jos caut sa  vad daca nu cumva exista grupuri care se gasesc cu totul in dictionar, precum in cha-cha-cha -ul
                List<String> subtoks = Arrays.asList(bigTok.getWordForm().split("-"));
                ArrayList<String> subtoksNew = new ArrayList<String>();
                subtoksNew.addAll(subtoks);
                for (int numGroups = 4; numGroups >= 2; numGroups--) {
                    boolean found = false;
                    for (int j = 0; j < subtoks.size() - numGroups + 1; j++) {
                        String group = "";
                        for (int k = j; k < j + numGroups; k++) {
                            group += subtoks.get(k) + "-";
                        }
                        group = group.substring(0, group.length() - 1);
                        if (dic != null) {
                            if (dic.get(group) != null || dic.get("-" + group) != null || dic.get(group + "-") != null) {
                                for (int k = 0; k < numGroups; k++) {
                                    subtoksNew.remove(j);
                                }
                                subtoksNew.add(j, group);
                                found = true;
                                break;
                            }
                        }
                    }

                    if (found) {
                        break;
                    }
                }
                subtoks = subtoksNew;

                //compun toate combinatiile in care fiecare cratima se duce ba cu tokenul din stanga, ba cu tokenul din dreapta
                //pentru asta, fac o codare binara unde 0 inseamna ca se duce in stanga, 1 inseamna ca se duce in dreapta: exemplu 010 este codarea pt o combinatie de 3 cratime care se ataseaza stanga/dreapta/stanga

                //creez numarul care are numarul de biti = cu numarul de cratime (maska). Incrementand de la 0 pana la acest numar, trec prin toate combinatiile de cratime posibile
                byte maxCombCode = (byte) ((byte) Math.pow(2, subtoks.size() - 1) - 1);

                //caut combinatiile care au scor maxim legat de prezenta subtokenilor in dictionar. E posibil sa apara mai mult de o combinatie care sa aiba scor maxim.
                //Le pastrez pe toate in prima faza cu scor maxi, si le mai triez apoi dupa scorul frecventa a lemelor in dictionar - asta elimina unele situatii precum "v-am" in care "v" apare in dictionar ca numeral dar cu scor mic
                List<List<String>> bestSubToksCombinations = new ArrayList<>();
                int bestscore = -1;

                for (byte k = 0; k <= maxCombCode; k++) {
                    List<String> combtoks = generateCombinedToks(subtoks, k);
                    int score = evaluateCombinedTokens(combtoks, dic);
                    if (score > bestscore)
                        bestSubToksCombinations.clear();
                    if (score >= bestscore) {
                        bestSubToksCombinations.add(combtoks);
                        bestscore = score;
                    }
                }
                List<String> bestSubToks = new ArrayList<>();
                bestscore = -1;

                for (List<String> subToksComb : bestSubToksCombinations)
                {
                    int score = 0;
                    for (String subTok : subToksComb)
                    {
                        if (dic.containsKey(subTok)){
                            for (UaicMorphologicalAnnotation a : dic.get(subTok))
                                score += dic.lemmas.get(a.getLemma());
                        }else
                            score -= 100;
                    }

                    if (score == bestscore)
                    {//wdf!!!!
                        score=score;
                    }

                    if (score > bestscore) {
                        bestSubToks = subToksComb;
                        bestscore = score;
                    }
                }


                if (bestscore > 0) {
                    //il splituiesc din nou; daca nici un subtoken nu se gaseste in dictionar, nu il mai splituiesc, pt ca e posibil sa fie vreun cuvant strain
                    for (String goodTok : bestSubToks) {
                        Token token = new Token();
                        token.setWordForm(goodTok);
                        token.setCharStartIndexInSentence(bigTok.getCharStartIndexInSentence() + bigTok.getWordForm().indexOf(goodTok));
                        token.setCharEndIndexInSentence(token.getCharStartIndexInSentence() + token.getWordForm().length());
                        token.setParentSentence(bigTok.getParentSentence());
                        newToks.add(token);
                    }
                } else {
                    newToks.add(bigTok);
                }
            } else {
                newToks.add(bigTok);
            }
        }
        toks.clear();
        toks.addAll(newToks);

        sent.addTokens(toks);
        return sent;
    }

    public InmemoryCorpus splitAndTokenize(String text) throws InstantiationException, IllegalAccessException {

        UaicSegmenter segmenter = new UaicSegmenter(morphologicalDictionary);
        Map.Entry<List<SentenceHunk>, List<Integer>> sentencesAndNonBreakableBounds = segmenter.segment(text);
        InmemoryCorpus ret = new InmemoryCorpus();
        for (SentenceHunk s : sentencesAndNonBreakableBounds.getKey()){
            InmemorySentence sent = tokenize(morphologicalDictionary, s, sentencesAndNonBreakableBounds.getValue());
            ret.addSentence(sent);
        }

        return ret;
    }

    static boolean IntervalInters(int s1, int e1, int s2, int e2) {
        return (s2 < e1 && e1 <= e2) || (s1 < e2 && e2 <= e1);
    }

    static Token UnifyToks(Token ws1, Token ws2, String input) {
        Token rez = new Token();
        rez.setCharStartIndexInSentence(Math.min(ws1.getCharStartIndexInSentence(), ws2.getCharStartIndexInSentence()));
        rez.setCharEndIndexInSentence(ws1.getCharEndIndexInSentence() + ws2.getCharEndIndexInSentence() - Math.min(ws1.getCharEndIndexInSentence(), ws2.getCharEndIndexInSentence()));
        rez.setWordForm(input.substring(rez.getCharStartIndexInSentence(), rez.getCharEndIndexInSentence()));
        rez.setParentSentence(ws1.getParentSentence());
        return rez;
    }

    static void UnifyToks(List<Token> toks, String input) {

        for (int i = 0; i < toks.size() - 1; i++) {
            for (int j = i + 1; j < toks.size(); j++) {
                if (IntervalInters(toks.get(i).getCharStartIndexInSentence(), toks.get(i).getCharEndIndexInSentence(), toks.get(j).getCharStartIndexInSentence(), toks.get(j).getCharEndIndexInSentence())) {
                    if (toks.get(i).getCharStartIndexInSentence() == toks.get(j).getCharStartIndexInSentence() || toks.get(j).getCharEndIndexInSentence() == toks.get(i).getCharEndIndexInSentence()) {
                        toks.set(i, UnifyToks(toks.get(i), toks.get(j), input));
                    }
                    toks.remove(j);
                    i--;
                    break;
                }
                if (toks.get(j).getCharStartIndexInSentence() > toks.get(i).getCharEndIndexInSentence()) {
                    break;
                }
            }
        }
    }

    private static List<String> generateCombinedToks(List<String> tokens, byte combcode) {
        List<String> combinedTokens = new ArrayList<String>();

        // make sure the last bit is set to 1 so we can avoid the last - in the final token
        int bindPositions = (1 << tokens.size() - 1) | combcode;

        String leftTokenPart = "";
        for (String token : tokens) {

            boolean leftOrRightBinding = (bindPositions & 1) == 0;

            combinedTokens.add(leftTokenPart + token + (leftOrRightBinding ? "-" : ""));
            leftTokenPart = leftOrRightBinding ? "" : "-";
            bindPositions >>= 1;
        }

        return combinedTokens;
    }

    private static int evaluateCombinedTokens(List<String> combToks, UaicMorphologicalDictionary dic) {
        int score = 0;
        for (String subtok : combToks) {
            if (dic != null && dic.get(subtok) != null) {
                score++;
                if (subtok.startsWith("-") || subtok.endsWith("-")) {
                    score += 9;
                }
            }
        }
        return score;
    }
}
