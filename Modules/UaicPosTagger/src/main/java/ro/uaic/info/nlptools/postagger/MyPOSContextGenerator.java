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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import opennlp.tools.postag.POSContextGenerator;
import opennlp.tools.util.Cache;
import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

/**
 *
 * @author User
 */
class MyPOSContextGenerator implements POSContextGenerator {

    protected final String SE = "*SE*";
    protected final String SB = "*SB*";
    private static final int PREFIX_LENGTH = 0;
    private static final int SUFFIX_LENGTH = 4;
    private static Pattern hasCap = Pattern.compile(".\\p{Lu}");
    private static Pattern startsCap = Pattern.compile("^\\p{Lu}");
    private static Pattern hasNum = Pattern.compile("[0-9]");
    private Cache contextsCache;
    private Object wordsKey;
    private UaicMorphologicalDictionary posDict;

    public MyPOSContextGenerator(UaicMorphologicalDictionary dict) {
        this.posDict = dict;
    }

    protected static String[] getPrefixes(String lex) {
        String[] prefs = new String[PREFIX_LENGTH];
        for (int li = 0, ll = PREFIX_LENGTH; li < ll; li++) {
            prefs[li] = lex.substring(0, Math.min(li + 1, lex.length()));
        }
        return prefs;
    }

    protected static String[] getSuffixes(String lex) {
        String[] suffs = new String[SUFFIX_LENGTH];
        for (int li = 0, ll = SUFFIX_LENGTH; li < ll; li++) {
            suffs[li] = lex.substring(Math.max(lex.length() - li - 1, 0));
        }
        return suffs;
    }

    public String[] getContext(int index, String[] sequence, String[] priorDecisions, Object[] additionalContext) {
        return getContext(index, sequence, priorDecisions);
    }

    /**
     * Returns the context for making a pos tag decision at the specified token index given the specified tokens and previous tags.
     * @param index The index of the token for which the context is provided.
     * @param tokens The tokens in the sentence.
     * @param tags The tags assigned to the previous words in the sentence.
     * @return The context for making a pos tag decision at the specified token index given the specified tokens and previous tags.
     */
    public String[] getContext(int index, String[] tokens, String[] tags) {
        String next, nextnext, lex, prev, prevprev;
        String tagprev, tagprevprev, tagnext;
        tagprev = tagprevprev = tagnext = null;
        next = nextnext = lex = prev = prevprev = null;

        lex = tokens[index].toString();
        if (tokens.length > index + 1) {
            next = tokens[index + 1].toString();
            if (tokens.length > index + 2) {
                nextnext = tokens[index + 2].toString();
            } else {
                nextnext = SE; // Sentence End
            }
        } else {
            next = SE; // Sentence End
        }

        if (index - 1 >= 0) {
            prev = tokens[index - 1];
            tagprev = tags[index - 1];

            if (index - 2 >= 0) {
                prevprev = tokens[index - 2];
                tagprevprev = tags[index - 2];
            } else {
                prevprev = SB; // Sentence Beginning
            }
        } else {
            prev = SB; // Sentence Beginning
        }
        String cacheKey = index + tagprev + tagprevprev;
        if (contextsCache != null) {
            if (wordsKey == tokens) {
                String[] cachedContexts = (String[]) contextsCache.get(cacheKey);
                if (cachedContexts != null) {
                    return cachedContexts;
                }
            } else {
                contextsCache.clear();
                wordsKey = tokens;
            }
        }
        List<String> e = new ArrayList<String>();
        // add the word itself
        e.add("w=" + lex.toLowerCase());
        //if (posDict == null || !posDict.keySet().contains(tokens[index].toString())) {
        // do some basic suffix analysis

        // see if the word has any special characters

        if (hasCap.matcher(lex).find()) {
            e.add("hasCap");
        }

        if (startsCap.matcher(lex).find()) {
            e.add("startsCap");
        }

        if (hasNum.matcher(lex).find()) {
            e.add("hasDigit");
        }
        //}
        // add the words and pos's of the surrounding context
        if (prev != null) {
            e.add("p=" + prev);
            if (tagprev != null) {
                e.add("t=" + tagprev);
            }
            if (prevprev != null) {
                e.add("pp=" + prevprev);
                if (tagprevprev != null) {
                    e.add("t2=" + tagprevprev + "," + tagprev);
                }
            }
        }

        if (next != null) {
            e.add("n=" + next);
        }



        if (index < tokens.length - 1) {
            if (tags.length > index + 1) {
                tagnext = tags[index + 1];
                if (tagnext.length() > 1) {
                    tagnext = tagnext.substring(0, 2);
                }
            } else {
                if (posDict != null) {
                    Set<UaicMorphologicalAnnotation> get = posDict.get(tokens[index + 1]);
                    if (get != null) {
                        Set<String> posss = new HashSet<String>();
                        for (UaicMorphologicalAnnotation a : get) {
                            String str = a.getMsd();
                            if (str.length() > 2) {
                                str = str.substring(0, 2);
                            }
                            posss.add(str);
                        }
                        if (posss.size() == 1) {
                            tagnext = (String) posss.toArray()[0];
                        }
                    }
                }
            }
        }
        if (tagnext != null) {
            e.add("tagn=" + tagnext);
        }

        String tagnextnext = null;
        if (index < tokens.length - 2) {
            if (tags.length > index + 2) {
                tagnextnext = tags[index + 2];
                if (tagnextnext.length() > 1) {
                    tagnextnext = tagnextnext.substring(0, 2);
                }
            } else {
                if (posDict != null) {
                    Set<UaicMorphologicalAnnotation> get = posDict.get(tokens[index + 2]);
                    if (get != null) {
                        Set<String> posss = new HashSet<String>();
                        for (UaicMorphologicalAnnotation a : get) {
                            String str = a.getMsd();
                            if (str.length() > 2) {
                                str = str.substring(0, 2);
                            }
                            posss.add(str);
                        }
                        if (posss.size() == 1) {
                            tagnextnext = (String) posss.toArray()[0];
                        }
                    }
                }
            }
        }
        if (tagnextnext != null) {
            e.add("tagnn=" + tagnextnext);
        }

        String hyphen = null;
        String len = null;
        String[] suffs = getSuffixes(lex);
        for (int i = 0; i < suffs.length; i++) {
            e.add("suf=" + suffs[i]);
        }
        String lastmark = null;
        if (tags.length == tokens.length) {//e la antrenare
            len = Integer.toString(tokens[index].length());

            if (tokens[tokens.length - 1].matches("\\p{P}+")) {
                lastmark = tokens[tokens.length - 1];
            }

            if (lex.indexOf('-') != -1) {
                hyphen = "m";
                if (lex.startsWith("-")) {
                    hyphen = "s";
                } else if (lex.endsWith("-")) {
                    hyphen = "e";
                }
            }
            if (nextnext != null) {
                e.add("nn=" + nextnext);
            }
        } else {//la testare
            if (posDict != null) {
                Set<UaicMorphologicalAnnotation> get = posDict.get(tokens[index]);
                if (get == null) {
                    len = Integer.toString(tokens[index].length());
                    if (tokens[tokens.length - 1].matches("\\p{P}+")) {
                        lastmark = tokens[tokens.length - 1];
                    }

                    if (lex.indexOf('-') != -1) {
                        hyphen = "m";
                        if (lex.startsWith("-")) {
                            hyphen = "s";
                        } else if (lex.endsWith("-")) {
                            hyphen = "e";
                        }
                    }
                    if (nextnext != null) {
                        e.add("nn=" + nextnext);
                    }
                }
            }
        }
      
        if (len != null) {
            e.add("len=" + len);
        }

        if (hyphen != null) {
            e.add("h=" + hyphen);
        }

        if (lastmark != null) {
            e.add("lmark=" + lastmark);
        }

        String[] contexts = (String[]) e.toArray(new String[e.size()]);
        if (contextsCache != null) {
            contextsCache.put(cacheKey, contexts);
        }
        return contexts;
    }
}
