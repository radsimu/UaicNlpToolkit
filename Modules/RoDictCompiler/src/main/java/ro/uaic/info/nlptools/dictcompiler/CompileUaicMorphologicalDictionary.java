package ro.uaic.info.nlptools.dictcompiler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

public class CompileUaicMorphologicalDictionary {

    static boolean preserveParticiples = false;
    static boolean detectNegativeVerbs = false;

    public static void main(String[] args) throws IOException {

        for (String arg : args) {
            switch (arg) {
                case "preserveParticiples":
                    preserveParticiples = true;
                    break;
                case "detectNegativeVerbs":
                    detectNegativeVerbs = true;
                    break;
            }
        }
        MyDictionary dic = new MyDictionary();
        dic.diacriticsPolicy = UaicMorphologicalDictionary.StrippedDiacriticsPolicy.Both;
        dic.loadFolder("ResourceData/RoDictCompiler/precompilation entries/NPs/");
        dic.loadFolder("ResourceData/RoDictCompiler/precompilation entries/");
        dic.loadLexemPriorities("ResourceData/RoDictCompiler/precompilation entries/lexemPriority.total");
        enrichDic(dic);

        Set<String> tagset = dic.saveAndReturnTagset("ResourceData/RoDictCompiler/result/posDictRo.txt");
        BufferedWriter tagsetOut = new BufferedWriter(new FileWriter("ResourceData/RoDictCompiler/result/tagsetFromDict.txt"));
        BufferedWriter erroneousTags = new BufferedWriter(new FileWriter("ResourceData/RoDictCompiler/result/invalidTagsReport.txt"));
        Set<String> manualTagset = loadManualTagset(new FileInputStream("ResourceData/RoDictCompiler/result/tagsetManual.txt"));
        Map<String, Integer> invalidCount = new TreeMap<>();

        for (String tag : tagset) {
            tagsetOut.write(tag);
            tagsetOut.write("\n");
        }

        for (Set<UaicMorphologicalAnnotation> as : dic.values()) {
            for (UaicMorphologicalAnnotation a : as) {
                if (!manualTagset.contains(a.getMsd())) {
                    Integer c = invalidCount.get(a.getMsd());
                    if (c == null) {
                        c = 0;
                    }
                    invalidCount.put(a.getMsd(), ++c);
                }
            }
        }

        erroneousTags.write("\n============Invalid tags with count==========\n");
        for (Entry<String, Integer> entry : invalidCount.entrySet()) {
            erroneousTags.write(entry.getKey() + "\t" + entry.getValue());
            erroneousTags.write("\n");
        }

        tagsetOut.close();
        erroneousTags.close();
    }

    public static Set<String> loadManualTagset(InputStream input) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(input, "UTF8"));
        Set<String> manualTagset = new TreeSet<>();
        String strLine;
        while ((strLine = in.readLine()) != null) {
            if (strLine.startsWith("\uFEFF")) {
                strLine = strLine.substring(1);
            }
            if (strLine.contains("#")) {
                strLine = strLine.substring(0, strLine.indexOf("#"));
            }
            if (strLine.trim().isEmpty()) {
                continue;
            }

            manualTagset.add(strLine.trim());
        }
        return manualTagset;
    }

    static void enrichDic(MyDictionary dic) {
        Set<String> keyset = new HashSet<>(dic.keySet());

        for (String word : keyset) {
            Set<UaicMorphologicalAnnotation> entries_orig = dic.get(word);
            ArrayList<UaicMorphologicalAnnotation> entries = new ArrayList<>(entries_orig);
            for (UaicMorphologicalAnnotation entry : entries) {
                UaicMorphologicalAnnotation newEntry;
                if (entry.getMsd().equals("Vmp") || entry.getMsd().equals("Vmg")) { //ascultatu-m-ați (Vmp) spalandu-se (Vmg)
                    newEntry = new UaicMorphologicalAnnotation(
                            entry.getWord() + "u",
                            entry.getMsd(),
                            entry.getLemma(),
                            entry.getExtra());
                    dic.Add(newEntry);
                } else if (entry.getMsd().equals("Ncfsrn")) { //generez vocativ feminin sg
                    String w = entry.getWord();
                    if (w.endsWith("ă") || w.endsWith("ie")) {
                        w = w.substring(0, w.length() - 1);
                    }
                    w += "o";
                    newEntry = new UaicMorphologicalAnnotation(
                            w,
                            "Ncfsvy",
                            entry.getLemma(),
                            entry.getExtra()
                    );
                    dic.Add(newEntry);
                } else if (entry.getMsd().equals("Ncmsry") && !entry.getLemma().equals(entry.getWord())) { //generez vocativ masculin sg (prostul-e)
                    newEntry = new UaicMorphologicalAnnotation(
                            entry.getWord() + "e",
                            "Ncmsvy",
                            entry.getLemma(),
                            entry.getExtra()
                    );
                    dic.Add(newEntry);
                } else if (entry.getMsd().equals("Ncfpoy") && !entry.getLemma().equals(entry.getWord())) { //generez vocativ feminin pl
                    newEntry = new UaicMorphologicalAnnotation(entry.getWord(), "Ncfpvy", entry.getLemma(), entry.getExtra());
                    dic.Add(newEntry);
                } else if (entry.getMsd().equals("Ncmpoy") && !entry.getLemma().equals(entry.getWord())) { //generez vocativ msculin pl
                    newEntry = new UaicMorphologicalAnnotation(entry.getWord(), "Ncmpvy", entry.getLemma(), entry.getExtra());
                    dic.Add(newEntry);
                }
            }
        }

        //toate adjectivele care apar ca verb la participiu cand sunt la masculin sg direct, pot fi participiu si in rest
        //marcam aceste adjective ca posibile participii
        //pt asta mai intai trebuie sa grupez intrarile pe leme
        Map<String, Set<UaicMorphologicalAnnotation>> lemma2words = new HashMap<>();
        for (String word : dic.keySet()) {
            Set<UaicMorphologicalAnnotation> entries = dic.get(word);
            for (UaicMorphologicalAnnotation entry : entries) {
                Set<UaicMorphologicalAnnotation> otherForms = lemma2words.get(entry.getLemma());
                if (otherForms == null) {
                    otherForms = new HashSet<>();
                    lemma2words.put(entry.getLemma(), otherForms);
                }
                otherForms.add(entry);
            }
        }

        //si apoi toate lemele care au formele de Afpmsrn egale cu cele de Vmp sunt participii
        //daca preserveParticiples e true, le introduc ca participii, altfel le marchez ca participii in campul extra
        for (Entry<String, Set<UaicMorphologicalAnnotation>> lemmaGroup : lemma2words.entrySet()) {
            UaicMorphologicalAnnotation participle = null;
            //determine if this is an adjective derived from participle
            for (UaicMorphologicalAnnotation entry : lemmaGroup.getValue()) {
                if (entry.getMsd().startsWith("Afpmsrn")) {
                    Set<UaicMorphologicalAnnotation> otherEntries = dic.get(entry.getWord());
                    for (UaicMorphologicalAnnotation otherEntry : otherEntries) {
                        if (otherEntry.getMsd().startsWith("Vmp")) {
                            participle = otherEntry;
                            break;
                        }
                    }
                }
                if (participle != null) {
                    break;
                }
            }

            if (participle != null) {
                for (UaicMorphologicalAnnotation entry : lemmaGroup.getValue()) {
                    if (entry.getMsd().startsWith("Afp")) {
                        if (!preserveParticiples) {
                            StringBuilder sb = new StringBuilder();
                            if (entry.getExtra() != null)
                                sb.append(entry.getExtra().trim());
                            if (!sb.toString().contains("ParticipleLemma")) {
                                if (sb.length() > 0)
                                    sb.append("|");
                                sb.append("ParticipleLemma:");
                                sb.append(participle.getLemma());
                                entry.setExtra(sb.toString());
                            }
                        } else {
                            switch (entry.getMsd()) {
                                case "Afpfsrn":
                                    entry.setMsd("Vmp--sf----r");
                                    entry.setLemma(participle.getLemma());
                                    break;
                                case "Afpfprn":
                                    entry.setMsd("Vmp--pf");
                                    entry.setLemma(participle.getLemma());
                                    break;
                                case "Afpmsrn":
                                    entry.setMsd("Vmp--sm");
                                    entry.setLemma(participle.getLemma());
                                    break;
                                case "Afpmprn":
                                    entry.setMsd("Vmp--pm");
                                    entry.setLemma(participle.getLemma());
                                    break;
                                case "Afpfson":
                                    entry.setMsd("Vmp--sf----o");
                                    entry.setLemma(participle.getLemma());
                                    break;
                                case "Afpfpon":
                                    entry.setMsd("Vmp--pf");
                                    entry.setLemma(participle.getLemma());
                                    break;
                                case "Afpmson":
                                    entry.setMsd("Vmp--sm");
                                    entry.setLemma(participle.getLemma());
                                    break;
                                case "Afpmpon":
                                    entry.setMsd("Vmp--pm");
                                    entry.setLemma(participle.getLemma());
                                    break;
                            }
                        }
                    }
                }
            }
        }

        if (detectNegativeVerbs) {
            for (Entry<String, Set<UaicMorphologicalAnnotation>> lemmaGroup : lemma2words.entrySet()) {
                for (UaicMorphologicalAnnotation entry : lemmaGroup.getValue()) {
                    if (entry.getMsd().startsWith("Vmp-") || entry.getMsd().startsWith("Vmg") || entry.getMsd().startsWith("Vag")) {
                        String isNegative = "n";
                        if (entry.getWord().startsWith("ne")) {
                            if (!entry.getLemma().startsWith("ne")) {
                                isNegative = "y";
                            } else {
                                String positiveForm = entry.getWord().substring(2);
                                Set<UaicMorphologicalAnnotation> annotations = dic.get(positiveForm);
                                if (annotations != null) {
                                    for (UaicMorphologicalAnnotation a : annotations) {
                                        if (a.getMsd().startsWith(entry.getMsd().substring(0, 3))) {
                                            isNegative = "y";
                                            entry.setLemma(a.getLemma());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        while (entry.getMsd().length() < 9) {
                            entry.setMsd(entry.getMsd() + "-");
                        }
                        entry.setMsd(entry.getMsd().substring(0, 8) + isNegative + entry.getMsd().substring(9));
                        if (isNegative.equals("y")) {
                            System.out.println("Negative inference: word=" + entry.getWord() + "    Lemma=" + entry.getLemma() + "    MSD=" + entry.getMsd());
                        }
                    }
                }
            }
        }

        keyset = new HashSet<>(dic.keySet());
        //toate cuvintele care incep cu î pot fi clitice
        for (String word : keyset) {
            Set<UaicMorphologicalAnnotation> entries = dic.get(word);
            if (word.length() > 2 && word.startsWith("î")) {
                String newWord = "-" + word.substring(1);
                for (UaicMorphologicalAnnotation entry : entries) {
                    UaicMorphologicalAnnotation newEntry = new UaicMorphologicalAnnotation(newWord, entry.getMsd(), entry.getLemma(), entry.getExtra());
                    dic.Add(newEntry);
                }
            }
        }
    }
}
