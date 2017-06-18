/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ro.uaic.info.nlptools.dictcompiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

/**
 *
 * @author Planetaria
 */

//TODO this currently works only with RoDepTb xml format. Make it work with any corpus format...
public class PrecompileFromCorpus {

    public static void main(String[] args) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        UaicMorphologicalDictionary dic = new UaicMorphologicalDictionary();
        dic.diacriticsPolicy = UaicMorphologicalDictionary.StrippedDiacriticsPolicy.NeverStripped;
        dic.load(new FileInputStream(args[1]));
        PrintWriter pw = new PrintWriter(args[2], "UTF8");
        Set<String> lines = new HashSet<String>();

        Set<String> manualTagset = null;
        PrintWriter invalidReport = new PrintWriter("lexiconExtractionReport.txt");
        System.out.println("Extracting pos lexicon from tb files from " + args[0]);
        Map<String,Integer> invalidCount = new TreeMap<String, Integer>();
        
        if (args[3] != null) {
            manualTagset = CompileUaicMorphologicalDictionary.loadManualTagset(new FileInputStream(args[3]));
            System.out.println("Also validating msd tags and creating report in lexiconExtractionReport.txt");
        }
                
        for (File f : new File(args[0]).listFiles()) {
            Document doc = dBuilder.parse(new FileInputStream(f));
            Element root = doc.getDocumentElement();
            root.normalize();
            NodeList sentsElems = doc.getElementsByTagName("sentence");

            for (int i = 0; i < sentsElems.getLength(); i++) {
                NodeList words = ((Element) sentsElems.item(i)).getElementsByTagName("word");
                for (int k = 0; k < words.getLength(); k++) {
                    Element wordElem = (Element) words.item(k);
                    String form = wordElem.getAttribute("form").trim();
                    String lemma = wordElem.getAttribute("lemma").trim().replaceAll("[~_]", " ");
                    String msd = wordElem.getAttribute("postag").trim();

                    if (form == null || form.isEmpty() || form.trim().isEmpty() || lemma == null || lemma.isEmpty() || lemma.trim().isEmpty() || msd == null || msd.isEmpty() || msd.trim().isEmpty()) {
                        invalidReport.println(String.format("something wrong in entry: <word id=\"%s\" form=\"%s\" lemma=\"%s\" msd=\"%s\" ...  (%s)", wordElem.getAttribute("id"), form, lemma, msd, f.getName()));
                        continue;
                    }
                    if (manualTagset != null && !manualTagset.contains(msd)){
                        invalidReport.println(String.format("invalid msd: %s\tLEMMA=%s\tMSD=%s  ...  (%s)", form, lemma, msd, f.getName()));
                        Integer c = invalidCount.get(msd);
                        if (c == null)
                            c = 0;
                        invalidCount.put(msd, ++c);
                    }
                    
                    Set<UaicMorphologicalAnnotation> as = dic.get(form);
                    boolean found = false;
                    if (as != null) {
                        for (UaicMorphologicalAnnotation a : as) {
                            if (a.getMsd().equals(msd) && UaicMorphologicalDictionary.getCleanedUpWord(a.getLemma()).toLowerCase().equals(lemma.toLowerCase())) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        String line = String.format("%s\tLEMMA=%s\tMSD=%s", form, lemma, msd);
                        if (!lines.contains(line)) {
                            pw.println(line);
                            lines.add(line);
                        }
                    }
                }
            }
        }
        
        if (manualTagset != null){
            invalidReport.println("\n============Invalid tags with count==========");
            for (Entry<String, Integer> entry : invalidCount.entrySet()){
                invalidReport.println(entry.getKey() + "\t" + entry.getValue());
            }
        }
        
        invalidReport.close();
        pw.close();
    }
}
