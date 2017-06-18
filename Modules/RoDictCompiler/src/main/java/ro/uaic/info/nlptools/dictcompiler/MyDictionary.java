package ro.uaic.info.nlptools.dictcompiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

public class MyDictionary extends UaicMorphologicalDictionary { // loads entries from the dictionary folder and saves them in the format needed by the hybrid pos tagger

    public static String folder = "dictionary/";

    public MyDictionary() {
        super();
    }

    public static String getCanonicalWord(String word){
        return UaicMorphologicalDictionary.getCanonicalWord(word);
    }

    public static String getCanonicalWordKeepGrafie(String word){
        return UaicMorphologicalDictionary.getCanonicalWordKeepGrafie(word);
    }

    public boolean contains(UaicMorphologicalAnnotation entry) {
        Set<UaicMorphologicalAnnotation> entries = get(entry.getWord());
        if (entries == null) {
            return false;
        }
        for (UaicMorphologicalAnnotation anEntry : entries) {
            if (anEntry.getMsd().equals(entry.getMsd()) && anEntry.getLemma().equals(entry.getLemma())) {
                return true;
            }
        }
        return false;
    }

    public void loadFolder(String folder) throws IOException {
        File dicFolder = new File(folder);
        if (!dicFolder.exists()) {
            System.out.println("folder " + dicFolder + " not found");
        }
        for (File f : dicFolder.listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
            if (f.getName().endsWith(".txt")) {
                load(folder + f.getName());
            }
        }
    }

    public void load(String file) throws IOException {
        if (!new File(file).exists()) {
            System.out.println(file + " not found");
        } else {
            String strLine = null;
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));
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

                MyDictionaryEntry entry = new MyDictionaryEntry(strLine);
                if (entry.getLemma() != null && entry.getMsd() != null) {
                    Add(entry);
                }
            }
            System.out.println(file + " loaded");
            in.close();
        }
    }

    public Set<String> saveAndReturnTagset(String name) throws IOException {
        save(name);
        Set<String> tags = new TreeSet<String>();

        for (Set<UaicMorphologicalAnnotation> entries : values()) {
            for (UaicMorphologicalAnnotation entry : entries) {
                tags.add(entry.getMsd());
            }
        }

        return tags;
    }

    public void loadLexemPriorities(String file) throws IOException {
        if (!new File(file).exists()) {
            System.out.println("lexem priority file " + file + " not found");
        } else {
            String strLine;
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));

            while ((strLine = in.readLine()) != null) {
                if (strLine.trim().isEmpty()) {
                    continue;
                }
                if (strLine.startsWith("\uFEFF")) {
                    strLine = strLine.substring(1);
                }
                String[] splits = strLine.split("\\s");

                lemmas.put(splits[0].replaceAll("~", " "), Integer.parseInt(splits[1]));
            }
            System.out.println(file + " loaded lexem priorities");
            in.close();
        }
    }
}
