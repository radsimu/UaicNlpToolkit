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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class UaicMorphologicalDictionary {

    public static final Logger logger = Logger.getLogger(UaicMorphologicalDictionary.class.getPackage().getName());
    private Map<String, Set<UaicMorphologicalAnnotation>> strippedDiacrDic = new LinkedHashMap<>();
    private Map<String, Set<UaicMorphologicalAnnotation>> nonstrippedDiacrDic = new LinkedHashMap<>();
    private Map<String, Set<UaicMorphologicalAnnotation>> mixedDiacrDic = new LinkedHashMap<>();

    public UaicMorphologicalDictionary() {
        compCuvsTrie.tok = "ROOT";
        compCuvsTrie.depth = -1;
        extra_features = new HashSet<>();
        extra_features.add("NotInDict");
        tagset = new HashSet<>();
    }
    protected Set<String> extra_features;
    private Set<String> tagset;

    public Map<String, Integer> getLemmas() {
        return lemmas;
    }

    public Set<String> getTagset() {
        return tagset;
    }
    public Map<String, Integer> lemmas = new HashMap<String, Integer>();

    protected Set<String> abbreviations = new HashSet<String>(70);
    public TokenTrieNode compCuvsTrie = new TokenTrieNode();

    public void load(InputStream is) throws Exception {
        long start = System.currentTimeMillis();
        logger.log(Level.INFO, "POS dictionary loading...");

        String strLine;
        BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF8"));

        tagset = new HashSet<>(350);
        extra_features = new HashSet<>();
        extra_features.add("NotInDict");
        lemmas.clear();
        boolean priorities = false;
        int l = -1;
        while ((strLine = in.readLine()) != null) {
            l++;
            if (strLine.startsWith("\uFEFF")) {
                strLine = strLine.substring(1);
            }
            if (strLine.trim().isEmpty()) {
                continue;
            }
            if (strLine.equals("###LexemPriorityTable")) {
                priorities = true;
                continue;
            }
            String[] toks = strLine.split("\t");
            String word = toks[0];

            if (!priorities) {
                if (word.length() > 1 && (word.contains("~") || word.contains("."))) {
                    addToTrie(word);
                }

                if (word.endsWith(".")) {
                    abbreviations.add(word);
                }

                Set<UaicMorphologicalAnnotation> entries = new HashSet<UaicMorphologicalAnnotation>();
                Set<UaicMorphologicalAnnotation> strippedDiacrEntries = new HashSet<UaicMorphologicalAnnotation>();
                Set<UaicMorphologicalAnnotation> nonstrippedDiacrEntries = new HashSet<UaicMorphologicalAnnotation>();

                for (int i = 1; i < toks.length; i++) {
                    String[] feats = toks[i].split("_");

                    if (feats.length < 2) {
                        throw new Exception("Invalid format at line: " + l);
                    }
                    String extra = null;
                    if (feats.length > 2) {
                        extra = feats[2].trim();
                        extra_features.add(extra);
                    }

                    String tag = feats[0];
                    getTagset().add(tag);

                    String lemma = feats[1];
                    lemmas.put(lemma, 0);

                    UaicMorphologicalAnnotation entry = new UaicMorphologicalAnnotation(word, tag, lemma, extra);
                    if (entry.getMsd().equals("Y")) {
                        abbreviations.add(word);
                    }
                    entries.add(entry);
                    if (entry.getExtra() != null && entry.getExtra().contains("StrippedDiacritics")) {
                        strippedDiacrEntries.add(entry);
                    } else {
                        nonstrippedDiacrEntries.add(entry);
                    }
                }
                mixedDiacrDic.put(word, entries);
                if (!strippedDiacrEntries.isEmpty()) {
                    strippedDiacrDic.put(word, strippedDiacrEntries);
                }
                if (!nonstrippedDiacrEntries.isEmpty()) {
                    nonstrippedDiacrDic.put(word, nonstrippedDiacrEntries);
                }
            } else {//priorities table
                lemmas.put(word.replaceAll("~", " ").trim(), Integer.parseInt(toks[1]));
            }
        }
        in.close();
        logger.log(Level.INFO, "POS dictionary loaded in " + (System.currentTimeMillis() - start) / 1000f + " seconds");
    }

    public int size() {
        switch (diacriticsPolicy) {
            case OnlyStripped:
                return strippedDiacrDic.size();
            case NeverStripped:
                return nonstrippedDiacrDic.size();
            case Both:
                return mixedDiacrDic.size();
        }
        throw new NotImplementedException();
    }

    public boolean isEmpty() {
        switch (diacriticsPolicy) {
            case OnlyStripped:
                return strippedDiacrDic.isEmpty();
            case NeverStripped:
                return nonstrippedDiacrDic.isEmpty();
            case Both:
                return mixedDiacrDic.isEmpty();
        }
        throw new NotImplementedException();
    }

    public boolean containsValue(Object o) {
        switch (diacriticsPolicy) {
            case OnlyStripped:
                return strippedDiacrDic.containsValue(o);
            case NeverStripped:
                return nonstrippedDiacrDic.containsValue(o);
            case Both:
                return mixedDiacrDic.containsValue(o);
        }
        throw new NotImplementedException();
    }

    private void AddAnnotationToMap(UaicMorphologicalAnnotation a, Map<String, Set<UaicMorphologicalAnnotation>> map) {
        String key = getCanonicalWord(a.getWord());
        Set<UaicMorphologicalAnnotation> get = map.get(key);
        if (get == null) {
            get = new HashSet<UaicMorphologicalAnnotation>();
            map.put(key, get);
        }
        get.add(a);
    }

    public void Add(UaicMorphologicalAnnotation a) {
        assert a.getWord() != null;
        assert a.getLemma() != null;
        assert a.getMsd() != null;
        assert !a.getWord().trim().isEmpty();
        assert !a.getLemma().trim().isEmpty();
        assert !a.getMsd().trim().isEmpty();
        AddAnnotationToMap(a, mixedDiacrDic);
        AddAnnotationToMap(a, nonstrippedDiacrDic);
        UaicMorphologicalAnnotation stripped = stripDiacrFromAnnotation(a);
        AddAnnotationToMap(stripped, mixedDiacrDic);
        AddAnnotationToMap(stripped, strippedDiacrDic);
        if (!lemmas.containsKey(a.getLemma())) {
            lemmas.put(a.getLemma(), 0);
        }
        getTagset().add(a.getMsd());
    }

    private void RemoveAnnotationFromMap(UaicMorphologicalAnnotation a, Map<String, Set<UaicMorphologicalAnnotation>> map) {
        String key = getCanonicalWord(a.getWord());
        Set<UaicMorphologicalAnnotation> get = map.get(key);
        if (get != null) {
            get.remove(a);
        }
    }

    public void Remove(UaicMorphologicalAnnotation a) {
        UaicMorphologicalAnnotation s = stripDiacrFromAnnotation(a);
        String cA = getCanonicalWord(a.getWord());

        RemoveAnnotationFromMap(a, mixedDiacrDic);
        RemoveAnnotationFromMap(a, nonstrippedDiacrDic);
        if (mixedDiacrDic.get(cA) != null && mixedDiacrDic.get(cA).isEmpty()) {
            mixedDiacrDic.remove(cA);
        }
        if (nonstrippedDiacrDic.get(cA) != null && nonstrippedDiacrDic.get(cA).isEmpty()) {
            nonstrippedDiacrDic.remove(getCanonicalWord(cA));
        }
        if (a != s) {
            String cS = getCanonicalWord(s.getWord());
            RemoveAnnotationFromMap(s, mixedDiacrDic);
            RemoveAnnotationFromMap(s, strippedDiacrDic);
            if (mixedDiacrDic.get(cS) != null && mixedDiacrDic.get(cS).isEmpty()) {
                mixedDiacrDic.remove(cS);
            }
            if (strippedDiacrDic.get(cS) != null && strippedDiacrDic.get(cS).isEmpty()) {
                strippedDiacrDic.remove(getCanonicalWord(cS));
            }
        }
    }

    private UaicMorphologicalAnnotation stripDiacrFromAnnotation(UaicMorphologicalAnnotation a) {
        String w = getStrippedDiacritics(a.getWord(), a.getLemma());
        if (a.getWord().equals(w)) {
            return a;
        } else {
            StringBuilder extra = new StringBuilder();
            if (a.getExtra() != null) {
                extra.append(a.getExtra().trim());
            }
            if (a.getExtra() != null && !a.getExtra().contains("StrippedDiacritics")) {
                if (!a.getExtra().trim().isEmpty()) {
                    extra.append("|");
                }
                extra.append("StrippedDiacritics(").append(a.getWord()).append(")");
            }
            String e = extra.toString();
            if (e.trim().isEmpty())
                e = null;
            return new UaicMorphologicalAnnotation(w, a.getMsd(), a.getLemma(), e);
        }
    }

    public void clear() {
        mixedDiacrDic.clear();
        strippedDiacrDic.clear();
        nonstrippedDiacrDic.clear();
    }

    public Set<String> keySet() {
        switch (diacriticsPolicy) {
            case OnlyStripped:
                return strippedDiacrDic.keySet();
            case NeverStripped:
                return nonstrippedDiacrDic.keySet();
            case Both:
                return mixedDiacrDic.keySet();
        }
        throw new NotImplementedException();
    }

    public Collection<Set<UaicMorphologicalAnnotation>> values() {
        switch (diacriticsPolicy) {
            case OnlyStripped:
                return strippedDiacrDic.values();
            case NeverStripped:
                return nonstrippedDiacrDic.values();
            case Both:
                return mixedDiacrDic.values();
        }
        throw new NotImplementedException();
    }

    public Set<Entry<String, Set<UaicMorphologicalAnnotation>>> entrySet() {
        switch (diacriticsPolicy) {
            case OnlyStripped:
                return strippedDiacrDic.entrySet();
            case NeverStripped:
                return nonstrippedDiacrDic.entrySet();
            case Both:
                return mixedDiacrDic.entrySet();
        }
        throw new NotImplementedException();
    }

    public enum StrippedDiacriticsPolicy {
        OnlyStripped, //no diacritics
        NeverStripped, //only with diacritics
        Both //mixed
    }

    public StrippedDiacriticsPolicy diacriticsPolicy = StrippedDiacriticsPolicy.NeverStripped;

    public Set<UaicMorphologicalAnnotation> get(Object o) {
        return get(o.toString());
    }

    public Set<UaicMorphologicalAnnotation> get(String o) {
        String key = getCanonicalWord(o);
        switch (diacriticsPolicy) {
            case OnlyStripped:
                return strippedDiacrDic.get(key);
            case NeverStripped:
                return nonstrippedDiacrDic.get(key);
            case Both:
                return mixedDiacrDic.get(key);
        }
        throw new NotImplementedException();
    }

    public boolean containsKey(Object o) {
        String key = getCanonicalWord(o.toString());
        if (diacriticsPolicy == StrippedDiacriticsPolicy.OnlyStripped) {
            return strippedDiacrDic.containsKey(key);
        }
        if (diacriticsPolicy == StrippedDiacriticsPolicy.NeverStripped) {
            return nonstrippedDiacrDic.containsKey(key);
        }

        return mixedDiacrDic.containsKey(o);
    }

    public boolean isAbbreviation(String word) {
        boolean ret = abbreviations.contains(getCanonicalWord(word));
        if (!ret && word.charAt(word.length() - 1) == '.') {
            ret = abbreviations.contains(getCanonicalWord(word.substring(0, word.length() - 1)));
        }
        return ret;
    }

    private void addToTrie(String comp) {
        String[] subToks = comp.split("(?<=[~\\.-])|(?=[~\\.-])");
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < subToks.length; i++) {
            if (subToks[i].length() > 0) {
                list.add(subToks[i]);
            }
        }
        compCuvsTrie.addNext(list);
    }

    public void save(String file) throws IOException {
        Path pathToFile = Paths.get(file);
        Files.createDirectories(pathToFile.getParent());
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8"));

        for (java.util.Map.Entry<String, Set<UaicMorphologicalAnnotation>> entry : mixedDiacrDic.entrySet()) {
            bw.write(entry.getKey());

            for (UaicMorphologicalAnnotation uaicMorphologicalAnnotation : entry.getValue()) {
                bw.write("\t");
                bw.write(uaicMorphologicalAnnotation.getMsd());
                bw.write("_");
                assert !uaicMorphologicalAnnotation.getMsd().trim().isEmpty();

                bw.write(uaicMorphologicalAnnotation.getLemma());

                if (uaicMorphologicalAnnotation.getExtra() != null) {
                    bw.write("_");
                    bw.write(uaicMorphologicalAnnotation.getExtra());
                }
            }
            bw.write("\n");
        }

        bw.write("###LexemPriorityTable\n");
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            bw.write(getCanonicalWordKeepGrafie(entry.getKey()) + "\t" + entry.getValue() + "\n");
        }

        bw.close();
    }

    private Boolean _supportsParticipleDeclination = null;

    private boolean supportsParticipleDeclination() {
        if (_supportsParticipleDeclination == null) {
            _supportsParticipleDeclination = false;
            for (String tag : getTagset()) {
                if (tag.startsWith("Vmp") && tag.length() > 3) {
                    _supportsParticipleDeclination = true;
                }
            }
        }
        return _supportsParticipleDeclination;
    }

    public String correctMSDTag(String msd) {
        return correctMSDTag(null, msd);
    }

    public String correctMSDTag(String word, String racaiMsd) {
        Set<UaicMorphologicalAnnotation> entries = null;
        if (word != null) {
            word = getCanonicalWord(word);

            entries = get(word);
            if (entries != null) {
                for (UaicMorphologicalAnnotation a : entries) {
                    if (a.getMsd().toLowerCase().equals(racaiMsd.toLowerCase())) {
                        return a.getMsd();
                    }
                }
            }

            if (word.matches("^\\d+$")) {
                return "M";
            }
        }

        List<String> validTags = new ArrayList<String>();
        if (entries != null) {
            for (UaicMorphologicalAnnotation a : entries) {
                validTags.add(a.getMsd());
            }
        } else { //cuvantul nu a fost gasit in dictionar sau nu a fost dat ca parametru, atunci ma uit in tot tagsetul.
            validTags.addAll(getTagset());
        }

        float bestScore = -10000;
        String bestPos = "";
        for (String validTag : validTags) {
            float score = matchPosScore(racaiMsd, validTag);
            if (score > bestScore) {
                bestScore = score;
                bestPos = validTag;
            }
        }
        return bestPos;
    }

    private String transformPos(String msd) {//transform the tagset for better comparison
        String ret = msd;
        if (!supportsParticipleDeclination() && ret.matches("^Vmp--(.)(.).*")) {//transform participles to adjectives, only if this dictionary doesn't support conjugated participles
            ret = ret.replaceAll("^Vmp--(.)(.).*", "Afp$2$1rn");
        } else if (ret.matches("^(P....)[an](.*)")) {
            ret = ret.replaceAll("^(P....)[an](.*)", "$1r$2");
        } else if (ret.matches("^(P....)[dg](.*)")) {
            ret = ret.replaceAll("^(P....)[dg](.*)", "$1o$2");
        }

        if (msd.matches("^A.(.*)")) {
            ret = msd.replaceAll("^A.(.*)", "A$1"); //for 1-1 char comparison with Nc and others
        }
        return ret;
    }

    public static String getStrippedDiacritics(String word, String lemma) {
        return getStrippedDiacritics(restoreGrafie(word, lemma));
    }

    static String restoreGrafie(String word, String lemma) {
        //lema are niste â deci trebuie restaurata grafia in forma cuvantului
        //dar trebuie grija. spre exemplu intrarea "reîntorcînd" are lema "reîntoarce".
        //deci primul î nu trebuie transofrmat pt ca la aceeasi pozitie in lema, nu este un â
        //dar al doile î trebuie transformat
        int offset = 0;
        for (int aux = word.indexOf("î", offset); aux != -1; aux = word.indexOf("î", aux + 1)) {
            if (lemma == null || lemma.length() <= aux || lemma.charAt(aux) != 'î') {
                word = word.substring(0, aux) + 'â' + word.substring(aux + 1);
            }
            offset = aux;
        }
        return word;
    }

    public static String getStrippedDiacritics(String word) {
        String rez = getCorrectedDiacritics(word);
        rez = rez.replaceAll("ț", "t");
        rez = rez.replaceAll("Ț", "T");
        rez = rez.replaceAll("ș", "s");
        rez = rez.replaceAll("Ș", "S");
        rez = rez.replaceAll("ă", "a");
        rez = rez.replaceAll("Ă", "A");
        rez = rez.replaceAll("î", "i");
        rez = rez.replaceAll("Î", "I");
        rez = rez.replaceAll("â", "a");
        rez = rez.replaceAll("Â", "A");
        return rez;
    }

    static String getCanonicalWord(String word) {
        String rez = getCanonicalWordKeepGrafie(word);
        return rez.replaceAll("â", "î"); //ca sa ignoram grafia
    }

    private static String getCanonicalWordKeepGrafie(String word) {
        String rez = getCleanedUpWord(word);
        rez = rez.replaceAll("[-–]", "-");
        rez = rez.replaceAll("[\\p{Z}\\s\\n\\r\n\r]+", "~");
        return rez.toLowerCase();
    }

    public static String getCleanedUpWord(String word) {
        return getRemovedAccents(getCorrectedDiacritics(word));
    }

    public static String getRemovedAccents(String s) {
        s = s.trim();
        s = s.replaceAll("[èéêȇëěĕẻ]", "e");
        s = s.replaceAll("[ûùúũŭűǔȕȗ]", "u");
        s = s.replaceAll("[ïìíĩỉǐ]", "i");
        s = s.replaceAll("[àáẚảȁ]", "a");
        s = s.replaceAll("[ôòóõŏőǒȍȏỏ]", "o");

        s = s.replaceAll("[ÈÉÊËĔĚȆẺ]", "E");
        s = s.replaceAll("[ÛÙÚŨŬŰǓȔȖ]", "U");
        s = s.replaceAll("[ÏÌÍĨỈǏ]", "I");
        s = s.replaceAll("[ÀÁẢȀ]", "A");
        s = s.replaceAll("[ÔÒÓÕŎŐǑȌȎỎ]", "O");
        return s;
    }

    public static String getCorrectedDiacritics(String s) {
        s = s.trim();
        s = s.replaceAll("[èéêȇëěĕẻ]", "e");
        s = s.replaceAll("[ûùúũŭűǔȕȗ]", "u");
        s = s.replaceAll("[ïìíĩỉǐ]", "i");
        s = s.replaceAll("[àáẚảȁ]", "a");
        s = s.replaceAll("[ôòóõŏőǒȍȏỏ]", "o");

        s = s.replaceAll("[ÈÉÊËĔĚȆẺ]", "E");
        s = s.replaceAll("[ÛÙÚŨŬŰǓȔȖ]", "U");
        s = s.replaceAll("[ÏÌÍĨỈǏ]", "I");
        s = s.replaceAll("[ÀÁẢȀ]", "A");
        s = s.replaceAll("[ÔÒÓÕŎŐǑȌȎỎ]", "O");

        s = s.replaceAll("[ắằẳẵ]", "ă");
        s = s.replaceAll("[ấầẩẫ]", "â");
        s = s.replaceAll("[ȉ]", "î");

        s = s.replaceAll("[ẮẰẲẴ]", "Ă");
        s = s.replaceAll("[ẤẦẪẨ]", "Â");
        s = s.replaceAll("[Ȉ]", "Î");

        s = s.replaceAll("[ẤẦ]", "Â");
        s = s.replaceAll("[ẤẦ]", "Â");
        s = s.replaceAll("[ẤẦ]", "Â");
        s = s.replaceAll("[ẤẦ]", "Â");
        s = s.replaceAll("[ẤẦ]", "Â");

        s = s.replaceAll("[ŞṢ]", "Ș");
        s = s.replaceAll("[şṣ]", "ș");
        s = s.replaceAll("[ŢƮṬ]", "Ț");
        s = s.replaceAll("[ţƫṭ]", "ț");

        return s;
    }

    private static String transformPosForComparison(String racaiMSD) {

        try {
            racaiMSD = racaiMSD.trim();
            racaiMSD = racaiMSD.replaceAll("^A.(.*)", "A$1");
            racaiMSD = racaiMSD.replaceAll("^Cr(.*)", "Cr");
            racaiMSD = racaiMSD.replaceAll("^Mm(.*)", "Nc$1");
            racaiMSD = racaiMSD.replaceAll("^(P....)[dg](.*)", "$1o$2");
            racaiMSD = racaiMSD.replaceAll("^(P....)[an](.*)", "$1r$3");
            racaiMSD = racaiMSD.replaceAll("^Vmp(..p.*)", "$Af$1");
            racaiMSD = racaiMSD.replaceAll("^Vmp(...f*)", "$Af$1");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return racaiMSD;
    }

    private static float matchPosScore(String racai, String mypos) {

        racai = transformPosForComparison(racai);
        mypos = transformPosForComparison(mypos);

        StringBuilder sb = new StringBuilder();
        while (racai.length() > mypos.length() + sb.length() || racai.length() + sb.length() < mypos.length()) {
            sb.append("-");
        }
        if (racai.length() < mypos.length()) {
            racai = racai + sb.toString();
        } else if (racai.length() > mypos.length()) {
            mypos = mypos + sb.toString();
        }

        float score = 0.0f;
        long multip = 100000;

        for (int k = 0; k < racai.length(); k++) {
            if (racai.charAt(k) == mypos.charAt(k)) {
                score += 4 * multip;
            } else if (mypos.charAt(k) == '-' || racai.charAt(k) == '-') {
                if (mypos.charAt(k) == 'r') {//encourage matching a 'r' (for direct case) for a '-' instead of oblique case
                    score += multip;
                }
                score += multip;
            }
            multip /= 10;
        }
        return score;
    }
    //
    //
    //
    //
    //
    //

    public static Map<String, String> describeMSD_ro(String MSD) {
        TreeMap<String, String> rez = new TreeMap<String, String>();

        char[] msd = (MSD + "---------").toCharArray();
        rez.put("MSD", MSD);
        if ((MSD.length() > 1 && Character.isLowerCase(msd[1])) || MSD.length() == 1) {
            switch (msd[0]) {
                case 'V':
                    rez.put("POS", "Verb");
                    if (msd[1] == 'm') {
                        rez.put("Tip", "predicativ");
                    } else if (msd[1] == 'a') {
                        rez.put("Tip", "auxiliar");
                    }

                    if (msd[2] == 'i') {
                        rez.put("Mod", "indicativ");
                    } else if (msd[2] == 'n') {
                        rez.put("Mod", "infinitiv");
                    } else if (msd[2] == 's') {
                        rez.put("Mod", "conjunctiv");
                    } else if (msd[2] == 'm') {
                        rez.put("Mod", "imperativ");
                    } else if (msd[2] == 'p') {
                        rez.put("Mod", "participiu");
                    } else if (msd[2] == 'g') {
                        rez.put("Mod", "gerunziu");
                    }

                    if (msd[3] == 'p') {
                        rez.put("Timp", "prezent");
                    } else if (msd[3] == 'i') {
                        rez.put("Timp", "imperfect");
                    } else if (msd[3] == 's') {
                        rez.put("Timp", "perfect simplu");
                    } else if (msd[3] == 'l') {
                        rez.put("Timp", "mai mult ca perfectul");
                    }

                    if (msd[4] == '1') {
                        rez.put("Persoana", "întâi");
                    } else if (msd[4] == '2') {
                        rez.put("Persoana", "a doua");
                    } else if (msd[4] == '3') {
                        rez.put("Persoana", "a treia");
                    }

                    if (msd[5] == 's') {
                        rez.put("Număr", "singular");
                    } else if (msd[5] == 'p') {
                        rez.put("Număr", "plural");
                    }

                    if (msd[10] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[10] == 'n') {
                        rez.put("Clitic", "nu");
                    }
                    break;

                case 'A':
                    rez.put("POS", "Adjectiv");

                    if (msd[3] == 'f') {
                        rez.put("Gen", "feminin");
                    } else if (msd[3] == 'm') {
                        rez.put("Gen", "masculin");
                    }

                    if (msd[4] == 's') {
                        rez.put("Număr", "singular");
                    } else if (msd[4] == 'p') {
                        rez.put("Număr", "plural");
                    }

                    if (msd[5] == 'r') {
                        rez.put("Caz", "direct");
                    } else if (msd[5] == 'o') {
                        rez.put("Caz", "oblic");
                    } else if (msd[5] == 'v') {
                        rez.put("Caz", "vocativ");
                    }

                    if (msd[6] == 'y') {
                        rez.put("Articol", "hotărât");
                    } else if (msd[6] == 'n') {
                        rez.put("Articol", "nehotărât");
                    }

                    if (msd[7] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[7] == 'n') {
                        rez.put("Clitic", "nu");
                    }

                    break;

                case 'N':
                    rez.put("POS", "Substantiv");
                    if (msd[1] == 'c') {
                        rez.put("Tip", "comun");
                    } else if (msd[1] == 'p') {
                        rez.put("Tip", "propriu");
                    }

                    if (msd[2] == 'f') {
                        rez.put("Gen", "feminin");
                    } else if (msd[2] == 'm') {
                        rez.put("Gen", "masculin");
                    }

                    if (msd[3] == 's') {
                        rez.put("Număr", "singular");
                    } else if (msd[3] == 'p') {
                        rez.put("Număr", "plural");
                    }

                    if (msd[4] == 'r') {
                        rez.put("Caz", "direct");
                    } else if (msd[4] == 'o') {
                        rez.put("Caz", "oblic");
                    } else if (msd[4] == 'v') {
                        rez.put("Caz", "vocativ");
                    }

                    if (msd[5] == 'y') {
                        rez.put("Articol", "hotărât");
                    } else if (msd[5] == 'n') {
                        rez.put("Articol", "nehotărât");
                    }

                    if (msd[6] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[6] == 'n') {
                        rez.put("Clitic", "nu");
                    }
                    break;

                case 'P':
                    rez.put("POS", "Pronume");

                    if (msd[1] == 'p') {
                        rez.put("Tip", "personal");
                    } else if (msd[1] == 'd') {
                        rez.put("Tip", "demonstrativ");
                    } else if (msd[1] == 'i') {
                        rez.put("Tip", "nehotărât");
                    } else if (msd[1] == 's') {
                        rez.put("Tip", "posesiv");
                    } else if (msd[1] == 'x') {
                        rez.put("Tip", "reflexiv");
                    } else if (msd[1] == 'w') {
                        rez.put("Tip", "relativ");
                    } else if (msd[1] == 'z') {
                        rez.put("Tip", "negativ");
                    } else if (msd[1] == 'h') {
                        rez.put("Tip", "emfatic");
                    }

                    if (msd[2] == '1') {
                        rez.put("Persoana", "întâi");
                    } else if (msd[2] == '2') {
                        rez.put("Persoana", "a doua");
                    } else if (msd[2] == '3') {
                        rez.put("Persoana", "a treia");
                    }

                    if (msd[3] == 'f') {
                        rez.put("Gen", "feminin");
                    } else if (msd[3] == 'm') {
                        rez.put("Gen", "masculin");
                    }

                    if (msd[4] == 's') {
                        rez.put("Număr", "singular");
                    } else if (msd[4] == 'p') {
                        rez.put("Număr", "plural");
                    }

                    if (msd[5] == 'r') {
                        rez.put("Caz", "direct");
                    } else if (msd[5] == 'o') {
                        rez.put("Caz", "oblic");
                    } else if (msd[5] == 'v') {
                        rez.put("Caz", "vocativ");
                    }

                    if (msd[6] == 's') {
                        rez.put("Numarul_posesorului", "singular");
                    } else if (msd[6] == 'p') {
                        rez.put("Numarul_posesorului", "plural");
                    }

                    if (msd[7] == 'f') {
                        rez.put("Genul_posesorului", "feminin");
                    } else if (msd[7] == 'm') {
                        rez.put("Genul_posesorului", "masculin");
                    }
                    break;

                case 'D':
                    rez.put("POS", "Adjectiv pronominal");
                    if (msd[1] == 'd') {
                        rez.put("Tip", "demonstrativ");
                    } else if (msd[1] == 'i') {
                        rez.put("Tip", "nehotărât");
                    } else if (msd[1] == 's') {
                        rez.put("Tip", "posesiv");
                    } else if (msd[1] == 'w') {
                        rez.put("Tip", "relativ");
                    } else if (msd[1] == 'z') {
                        rez.put("Tip", "negativ");
                    } else if (msd[1] == 'h') {
                        rez.put("Tip", "emfatic");
                    }

                    if (msd[2] == '1') {
                        rez.put("Persoana", "întâi");
                    } else if (msd[2] == '2') {
                        rez.put("Persoana", "a doua");
                    } else if (msd[2] == '3') {
                        rez.put("Persoana", "a treia");
                    }

                    if (msd[3] == 'f') {
                        rez.put("Gen", "feminin");
                    } else if (msd[3] == 'm') {
                        rez.put("Gen", "masculin");
                    } else if (msd[3] == 'n') {
                        rez.put("Gen", "neutru");
                    }

                    if (msd[4] == 's') {
                        rez.put("Număr", "singular");
                    } else if (msd[4] == 'p') {
                        rez.put("Număr", "plural");
                    }

                    if (msd[5] == 'r') {
                        rez.put("Caz", "direct");
                    } else if (msd[5] == 'o') {
                        rez.put("Caz", "oblic");
                    }

                    if (msd[6] == 's') {
                        rez.put("Numărul_posesorului", "singular");
                    } else if (msd[6] == 'p') {
                        rez.put("Numărul_posesorului", "plural");
                    }

                    if (msd[7] == 'f') {
                        rez.put("Genul_posesorului", "feminin");
                    } else if (msd[7] == 'm') {
                        rez.put("Genul_posesorului", "masculin");
                    }

                    if (msd[9] == 'e') {
                        rez.put("Poziționare", "prenominală");
                    } else if (msd[9] == 'o') {
                        rez.put("Poziționare", "postnominală");
                    }
                    break;

                case 'T':
                    rez.put("POS", "Articol");

                    if (msd[1] == 'd') {
                        rez.put("Tip", "demonstrativ");
                    } else if (msd[1] == 'i') {
                        rez.put("Tip", "nehotărât");
                    } else if (msd[1] == 'f') {
                        rez.put("Tip", "hotărât");
                    } else if (msd[1] == 's') {
                        rez.put("Tip", "posesiv");
                    }

                    if (msd[2] == 'f') {
                        rez.put("Gen", "feminin");
                    } else if (msd[2] == 'm') {
                        rez.put("Gen", "masculin");
                    } else if (msd[2] == 'n') {
                        rez.put("Gen", "neutru");
                    }

                    if (msd[3] == 's') {
                        rez.put("Număr", "singular");
                    } else if (msd[3] == 'p') {
                        rez.put("Număr", "plural");
                    }

                    if (msd[4] == 'r') {
                        rez.put("Caz", "direct");
                    } else if (msd[4] == 'o') {
                        rez.put("Caz", "oblic");
                    } else if (msd[4] == 'v') {
                        rez.put("Caz", "vocativ");
                    }

                    if (msd[5] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[5] == 'n') {
                        rez.put("Clitic", "nu");
                    }

                    break;

                case 'R':
                    rez.put("POS", "Adverb");

                    if (msd[3] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[3] == 'n') {
                        rez.put("Clitic", "nu");
                    }

                    break;

                case 'S':
                    rez.put("POS", "Prepoziție");

                    if (msd[4] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[4] == 'n') {
                        rez.put("Clitic", "nu");
                    }

                    break;

                case 'C':
                    rez.put("POS", "Conjuncție");
                    if (msd[1] == 'c') {
                        rez.put("Tip", "coordonatoare");
                    } else if (msd[1] == 'p') {
                        rez.put("Tip", "subordonatoare");
                    }

                    if (msd[5] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[5] == 'n') {
                        rez.put("Clitic", "nu");
                    }
                    break;

                case 'M':
                    rez.put("POS", "Numeral");
                    if (msd[1] == 'c') {
                        rez.put("Tip", "cardinal");
                    } else if (msd[1] == 'o') {
                        rez.put("Tip", "ordinal");
                    } else if (msd[1] == 'f') {
                        rez.put("Tip", "fracțional");
                    } else if (msd[1] == 'c') {
                        rez.put("Tip", "colectiv");
                    } else if (msd[1] == 'm') {
                        rez.put("Tip", "multiplicativ");
                    }

                    if (msd[2] == 'f') {
                        rez.put("Gen", "feminin");
                    } else if (msd[2] == 'm') {
                        rez.put("Gen", "masculin");
                    } else if (msd[2] == 'n') {
                        rez.put("Gen", "neutru");
                    }

                    if (msd[3] == 's') {
                        rez.put("Număr", "singular");
                    } else if (msd[3] == 'p') {
                        rez.put("Număr", "plural");
                    }

                    if (msd[4] == 'r') {
                        rez.put("Caz", "direct");
                    } else if (msd[4] == 'o') {
                        rez.put("Caz", "oblic");
                    } else if (msd[4] == 'v') {
                        rez.put("Caz", "vocativ");
                    }

                    if (msd[5] == 'l') {
                        rez.put("Formă", "literală");
                    } else if (msd[5] == 'd') {
                        rez.put("Formă", "digitală");
                    } else if (msd[5] == 'r') {
                        rez.put("Formă", "romană");
                    }

                    if (msd[6] == 'y') {
                        rez.put("Articol", "hotărât");
                    } else if (msd[6] == 'n') {
                        rez.put("Articol", "nehotărât");
                    }

                    if (msd[7] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[7] == 'n') {
                        rez.put("Clitic", "nu");
                    }

                    break;

                case 'I':
                    rez.put("POS", "Interjecție");
                    break;

                case 'X':
                    rez.put("POS", "simbol/cuvânt străin");
                    break;

                case 'Y':
                    rez.put("POS", "Abreviere");
                    break;

                case 'Q':
                    rez.put("POS", "Particulă");
                    if (msd[1] == 'z') {
                        rez.put("Tip", "negativă");
                    } else if (msd[1] == 'n') {
                        rez.put("Tip", "de infinitiv");
                    } else if (msd[1] == 's') {
                        rez.put("Tip", "de conjunctiv");
                    } else if (msd[1] == 'f') {
                        rez.put("Tip", "de viitor");
                    }

                    if (msd[3] == 'y') {
                        rez.put("Clitic", "da");
                    } else if (msd[3] == 'n') {
                        rez.put("Clitic", "nu");
                    }
                    break;
            }
        }
        return rez;
    }

    public static Map<String, String> describeMSD_en(String MSD) {

        Map<String, String> rez = new TreeMap<String, String>();

        char[] msd = (MSD + "-----------").toCharArray();
        rez.put("MSD", MSD);
        if ((MSD.length() > 1 && Character.isLowerCase(msd[1])) || MSD.length() == 1) {
            switch (msd[0]) {
                case 'V':
                    rez.put("POS", "VERB");
                    if (msd[1] == 'm') {
                        rez.put("Type", "predicative");
                    } else if (msd[1] == 'a') {
                        rez.put("Type", "auxiliary");
                    }

                    if (msd[2] == 'i') {
                        rez.put("Mood", "indicative");
                    } else if (msd[2] == 'n') {
                        rez.put("Mood", "infinitive");
                    } else if (msd[2] == 's') {
                        rez.put("Mood", "conjunctive");
                    } else if (msd[2] == 'm') {
                        rez.put("Mood", "imperative");
                    } else if (msd[2] == 'p') {
                        rez.put("Mood", "participle");
                    } else if (msd[2] == 'g') {
                        rez.put("Mood", "gerund");
                    }

                    if (msd[3] == 'p') {
                        rez.put("Tense", "present");
                    } else if (msd[3] == 'i') {
                        rez.put("Tense", "imperfect");
                    } else if (msd[3] == 's') {
                        rez.put("Tense", "past");
                    } else if (msd[3] == 'l') {
                        rez.put("Tense", "long");
                    }

                    if (msd[4] == '1') {
                        rez.put("Person", "first");
                    } else if (msd[4] == '2') {
                        rez.put("Person", "second");
                    } else if (msd[4] == '3') {
                        rez.put("Person", "third");
                    }

                    if (msd[5] == 's') {
                        rez.put("Number", "singular");
                    } else if (msd[5] == 'p') {
                        rez.put("Number", "plural");
                    }

                    if (msd[10] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[10] == 'n') {
                        rez.put("Clitic", "no");
                    }
                    break;

                case 'A':
                    rez.put("POS", "ADJECTIVE");

                    if (msd[3] == 'f') {
                        rez.put("Gender", "feminine");
                    } else if (msd[3] == 'm') {
                        rez.put("Gender", "masculine");
                    }

                    if (msd[4] == 's') {
                        rez.put("Number", "singular");
                    } else if (msd[4] == 'p') {
                        rez.put("Number", "plural");
                    }

                    if (msd[5] == 'r') {
                        rez.put("Case", "direct");
                    } else if (msd[5] == 'o') {
                        rez.put("Case", "oblique");
                    } else if (msd[5] == 'v') {
                        rez.put("Caz", "vocative");
                    }

                    if (msd[6] == 'y') {
                        rez.put("Definiteness", "yes");
                    } else if (msd[6] == 'n') {
                        rez.put("Definiteness", "no");
                    }

                    if (msd[7] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[7] == 'n') {
                        rez.put("Clitic", "no");
                    }

                    break;

                case 'N':
                    rez.put("POS", "NOUN");
                    if (msd[1] == 'c') {
                        rez.put("Type", "common");
                    } else if (msd[1] == 'p') {
                        rez.put("Type", "proper");
                    }

                    if (msd[2] == 'f') {
                        rez.put("Gender", "feminine");
                    } else if (msd[2] == 'm') {
                        rez.put("Gender", "masculine");
                    }

                    if (msd[3] == 's') {
                        rez.put("Number", "singular");
                    } else if (msd[3] == 'p') {
                        rez.put("Number", "plural");
                    }

                    if (msd[4] == 'r') {
                        rez.put("Case", "direct");
                    } else if (msd[4] == 'o') {
                        rez.put("Case", "oblique");
                    } else if (msd[4] == 'v') {
                        rez.put("Case", "vocative");
                    }

                    if (msd[5] == 'y') {
                        rez.put("Definiteness", "yes");
                    } else if (msd[5] == 'n') {
                        rez.put("Definiteness", "no");
                    }

                    if (msd[6] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[6] == 'n') {
                        rez.put("Clitic", "no");
                    }
                    break;

                case 'P':
                    rez.put("POS", "PRONOUN");

                    if (msd[1] == 'p') {
                        rez.put("Type", "personal");
                    } else if (msd[1] == 'd') {
                        rez.put("Type", "demonstrative");
                    } else if (msd[1] == 'i') {
                        rez.put("Type", "indefinite");
                    } else if (msd[1] == 's') {
                        rez.put("Type", "possessive");
                    } else if (msd[1] == 'x') {
                        rez.put("Type", "reflexive");
                    } else if (msd[1] == 'w') {
                        rez.put("Type", "relative");
                    } else if (msd[1] == 'z') {
                        rez.put("Type", "negative");
                    } else if (msd[1] == 'h') {
                        rez.put("Type", "intensive");
                    }

                    if (msd[2] == '1') {
                        rez.put("Person", "first");
                    } else if (msd[2] == '2') {
                        rez.put("Person", "second");
                    } else if (msd[2] == '3') {
                        rez.put("Person", "third");
                    }

                    if (msd[3] == 'f') {
                        rez.put("Gender", "feminine");
                    } else if (msd[3] == 'm') {
                        rez.put("Gender", "masculine");
                    }

                    if (msd[4] == 's') {
                        rez.put("Number", "singular");
                    } else if (msd[4] == 'p') {
                        rez.put("Number", "plural");
                    }

                    if (msd[5] == 'r') {
                        rez.put("Case", "direct");
                    } else if (msd[5] == 'o') {
                        rez.put("Case", "oblique");
                    } else if (msd[5] == 'v') {
                        rez.put("Case", "vocative");
                    }

                    if (msd[6] == 's') {
                        rez.put("Possesor_Number", "singular");
                    } else if (msd[6] == 'p') {
                        rez.put("Possesor_Number", "plural");
                    }

                    if (msd[7] == 'f') {
                        rez.put("Possesor_Gender", "feminine");
                    } else if (msd[7] == 'm') {
                        rez.put("Possesor_Gender", "masculine");
                    }
                    break;

                case 'D':
                    rez.put("POS", "DETERMINER");
                    if (msd[1] == 'd') {
                        rez.put("Type", "demonstrative");
                    } else if (msd[1] == 'i') {
                        rez.put("Type", "indefinite");
                    } else if (msd[1] == 's') {
                        rez.put("Type", "possessive");
                    } else if (msd[1] == 'w') {
                        rez.put("Type", "relative");
                    } else if (msd[1] == 'z') {
                        rez.put("Type", "negative");
                    } else if (msd[1] == 'h') {
                        rez.put("Type", "emfatic");
                    }

                    if (msd[2] == '1') {
                        rez.put("Person", "first");
                    } else if (msd[2] == '2') {
                        rez.put("Person", "second");
                    } else if (msd[2] == '3') {
                        rez.put("Person", "third");
                    }

                    if (msd[3] == 'f') {
                        rez.put("Gender", "feminine");
                    } else if (msd[3] == 'm') {
                        rez.put("Gender", "masculine");
                    } else if (msd[3] == 'n') {
                        rez.put("Gender", "neuter");
                    }

                    if (msd[4] == 's') {
                        rez.put("Number", "singular");
                    } else if (msd[4] == 'p') {
                        rez.put("Number", "plural");
                    }

                    if (msd[5] == 'r') {
                        rez.put("Case", "direct");
                    } else if (msd[5] == 'o') {
                        rez.put("Case", "oblique");
                    }

                    if (msd[6] == 's') {
                        rez.put("Possessor_number", "singular");
                    } else if (msd[6] == 'p') {
                        rez.put("Possessor_number", "plural");
                    }

                    if (msd[7] == 'f') {
                        rez.put("Possessor_gender", "feminine");
                    } else if (msd[7] == 'm') {
                        rez.put("Possessor_gender", "masculine");
                    }

                    if (msd[9] == 'e') {
                        rez.put("Positioning", "prenominal");
                    } else if (msd[9] == 'o') {
                        rez.put("Positioning", "postnominal");
                    }
                    break;

                case 'T':
                    rez.put("POS", "ARTICLE");

                    if (msd[1] == 'd') {
                        rez.put("Type", "demonstrative");
                    } else if (msd[1] == 'i') {
                        rez.put("Type", "indefinite");
                    } else if (msd[1] == 'f') {
                        rez.put("Type", "definite");
                    } else if (msd[1] == 's') {
                        rez.put("Type", "possessive");
                    }

                    if (msd[2] == 'f') {
                        rez.put("Gender", "feminine");
                    } else if (msd[2] == 'm') {
                        rez.put("Gender", "masculine");
                    } else if (msd[2] == 'n') {
                        rez.put("Gender", "neuter");
                    }

                    if (msd[3] == 's') {
                        rez.put("Number", "singular");
                    } else if (msd[3] == 'p') {
                        rez.put("Number", "plural");
                    }

                    if (msd[4] == 'r') {
                        rez.put("Case", "direct");
                    } else if (msd[4] == 'o') {
                        rez.put("Case", "oblique");
                    } else if (msd[4] == 'v') {
                        rez.put("Case", "vocative");
                    }

                    if (msd[5] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[5] == 'n') {
                        rez.put("Clitic", "no");
                    }

                    break;

                case 'R':
                    rez.put("POS", "ADVERB");

                    if (msd[3] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[3] == 'n') {
                        rez.put("Clitic", "no");
                    }

                    break;

                case 'S':
                    rez.put("POS", "ADPOSITION");

                    if (msd[4] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[4] == 'n') {
                        rez.put("Clitic", "no");
                    }

                    break;

                case 'C':
                    rez.put("POS", "CONJUNCTION");
                    if (msd[1] == 'c') {
                        rez.put("Type", "coordinating");
                    } else if (msd[1] == 'p') {
                        rez.put("Type", "subordinating");
                    }

                    if (msd[5] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[5] == 'n') {
                        rez.put("Clitic", "no");
                    }
                    break;

                case 'M':
                    rez.put("POS", "NUMERAL");
                    if (msd[1] == 'c') {
                        rez.put("Type", "cardinal");
                    } else if (msd[1] == 'o') {
                        rez.put("Type", "ordinal");
                    } else if (msd[1] == 'f') {
                        rez.put("Type", "fractional");
                    } else if (msd[1] == 'c') {
                        rez.put("Type", "colective");
                    } else if (msd[1] == 'm') {
                        rez.put("Type", "multiplicative");
                    }

                    if (msd[2] == 'f') {
                        rez.put("Gender", "feminine");
                    } else if (msd[2] == 'm') {
                        rez.put("Gender", "masculine");
                    } else if (msd[2] == 'n') {
                        rez.put("Gender", "neuter");
                    }

                    if (msd[3] == 's') {
                        rez.put("Number", "singular");
                    } else if (msd[3] == 'p') {
                        rez.put("Number", "plural");
                    }

                    if (msd[4] == 'r') {
                        rez.put("Case", "direct");
                    } else if (msd[4] == 'o') {
                        rez.put("Case", "oblique");
                    } else if (msd[4] == 'v') {
                        rez.put("Case", "vocative");
                    }

                    if (msd[5] == 'l') {
                        rez.put("Form", "letter");
                    } else if (msd[5] == 'd') {
                        rez.put("Form", "digit");
                    } else if (msd[5] == 'r') {
                        rez.put("Form", "roman");
                    }

                    if (msd[6] == 'y') {
                        rez.put("Definiteness", "yes");
                    } else if (msd[6] == 'n') {
                        rez.put("Definiteness", "no");
                    }

                    if (msd[7] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[7] == 'n') {
                        rez.put("Clitic", "no");
                    }

                    break;

                case 'I':
                    rez.put("POS", "INTERJECTION");
                    break;

                case 'X':
                    rez.put("POS", "SYMBOL/FOREIGN");
                    break;

                case 'Y':
                    rez.put("POS", "ABBREVIATION");
                    break;

                case 'Q':
                    rez.put("POS", "PARTICLE");
                    if (msd[1] == 'z') {
                        rez.put("Type", "negative");
                    } else if (msd[1] == 'n') {
                        rez.put("Type", "infinitive");
                    } else if (msd[1] == 's') {
                        rez.put("Type", "conjunctive");
                    } else if (msd[1] == 'f') {
                        rez.put("Type", "future");
                    }

                    if (msd[3] == 'y') {
                        rez.put("Clitic", "yes");
                    } else if (msd[3] == 'n') {
                        rez.put("Clitic", "no");
                    }
                    break;
            }
        }
        return rez;
    }
}
