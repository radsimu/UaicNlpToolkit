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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import opennlp.tools.postag.POSModel;
import ro.uaic.info.nlptools.corpus.INlpSentence;
import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.ggs.engine.core.GGSException;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

public class UaicHybridPOStagger {

    ExtendedPosTagger exTagger;
    public static final Logger logger = Logger.getLogger(UaicHybridPOStagger.class.getPackage().getName());
    public UaicMorphologicalDictionary uaicMorphologicalDictionary;
    public Set<String> guesserTagset;
    public GgsRulesEngine rulesEngine;
    public POSModel posModel;

//    public static void main(String[] args) throws Exception {//parametri: model; dictionar; guesserTagset; rulesgrammar;input folder; output folder
//        File dir = new File(args[4]);
//        InputStream rules = null;
//        if (!args[3].toLowerCase().equals("null"))
//            rules = new FileInputStream(args[3]);
//
//
//        UaicHybridPOStagger tagger = new UaicHybridPOStagger(new FileInputStream(args[0]), new FileInputStream(args[1]), new FileInputStream(args[2]), rules);
//
//        for (File f : dir.listFiles()) {
//            if (f.isDirectory() || f.getName().endsWith(".xml")) {
//                continue;
//            }
//            System.out.println(f.getPath());
//            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF8"));
//            String line;
//            StringBuilder sb = new StringBuilder();
//            while ((line = br.readLine()) != null) {
//                if (line.startsWith("\uFEFF")) {
//                    line = line.substring(1);
//                }
//                sb.append(line).append("\n");
//            }
//
//            OutputStream os = new FileOutputStream(args[5] + "/" + f.getName().substring(0, f.getName().length() - 3) + "xml");
//
//            Document doc = tagger.tagTextXmlDetailed_en(sb.toString());
//
//            TransformerFactory tfactory = TransformerFactory.newInstance();
//            Transformer serializer;
//            try {
//                serializer = tfactory.newTransformer();
//                serializer.setOutputProperty(OutputKeys.INDENT, "yes");
//                serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
//
//                serializer.transform(new DOMSource(doc), new StreamResult(os));
//            } catch (TransformerException e) {
//                throw new RuntimeException(e);
//            }
//
//            br.close();
//            os.close();
//        }
//    }

    public UaicHybridPOStagger(InputStream modelFile, UaicMorphologicalDictionary dic, InputStream guesserTagsetFile, InputStream rulesGrammarFile) throws IOException, GGSException {
        long start;
        start = System.currentTimeMillis();
        if (rulesGrammarFile != null) {
            logger.log(Level.INFO, "POS reduction rules loading...");
            rulesEngine = new GgsRulesEngine();
            rulesEngine.load(rulesGrammarFile);
            logger.log(Level.INFO, "POS reduction rules loaded in {0} seconds", (System.currentTimeMillis() - start) / 1000f);
        } else {
            logger.log(Level.INFO, "No POS reduction rules provided");
        }
        uaicMorphologicalDictionary = dic;

        start = System.currentTimeMillis();
        logger.log(Level.INFO, "POS model loading...");
        posModel = new POSModel(modelFile);

        HashSet<String> guesserTagsetPatterns = new HashSet<String>();

        String strLine = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(guesserTagsetFile));
        while ((strLine = in.readLine()) != null) {
            if (strLine.startsWith("\uFEFF")) {
                strLine = strLine.substring(1);
            }
            if (strLine.trim().isEmpty()) {
                continue;
            }
            guesserTagsetPatterns.add(strLine);
        }
        in.close();

        guesserTagset = new HashSet<>();
        for (String pattern : guesserTagsetPatterns) {
            Pattern p = Pattern.compile(pattern);
            for (String tag : uaicMorphologicalDictionary.getTagset()) {
                if (p.matcher(tag).matches()) {
                    guesserTagset.add(tag);
                }
            }
        }
        exTagger = new ExtendedPosTagger(posModel, uaicMorphologicalDictionary, guesserTagset, rulesEngine);
        logger.log(Level.INFO, "POS model loaded in {0} seconds", (System.currentTimeMillis() - start) / 1000f);
    }

//    public String tagTextRaw(String input) throws IOException, Exception {//folosita de serviciul/aplicatia web
//        StringBuilder sb = new StringBuilder();
//        UaicSegmenter seg = new UaicSegmenter(uaicMorphologicalDictionary);
//        List<String> sents = seg.segment(input);
//
//        for (SentenceHunk sentenceStruct : sents) {
//            SentenceHunk outcomes = tag(sentenceStruct);
//            sb.append(outcomes.toOutputString());
//            sb.append("\n");
//        }
//        return sb.toString();
//    }
//
//    public Document tagTextXml(String input) throws InstantiationException, IllegalAccessException, Exception {
//        UaicSegmenter seg = new UaicSegmenter(uaicMorphologicalDictionary);
//        List<SentenceHunk> sents = seg.segment(input);
//
//        ExtendedPosTagger exTagger = new ExtendedPosTagger(posModel, uaicMorphologicalDictionary, guesserTagset, rulesEngine);
//        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//        Document doc = dBuilder.newDocument();
//
//        Element root = doc.createElement("POS_Output");
//        doc.appendChild(root);
//
//        for (int i = 0; i < sents.size(); i++) {
//            SentenceHunk sentenceStruct = sents.get(i);
//            SentenceHunk outcomes = exTagger.tag(sentenceStruct);
//            if (outcomes == null) {
//                continue;
//            }
//            Element sentElem = outcomes.toXml(doc);
//            root.appendChild(sentElem);
//        }
//        doc.normalize();
//        return doc;
//    }
//
//    public Document tagTextXmlDetailed_ro(String input) throws InstantiationException, IllegalAccessException, Exception, InstantiationException, InstantiationException, InstantiationException, IllegalAccessException, InstantiationException, Exception, InstantiationException {
//        UaicSegmenter seg = new UaicSegmenter(uaicMorphologicalDictionary);
//        List<SentenceHunk> sents = seg.segment(input);
//
//        ExtendedPosTagger exTagger = new ExtendedPosTagger(posModel, uaicMorphologicalDictionary, guesserTagset, rulesEngine);
//        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//        Document doc = dBuilder.newDocument();
//
//        Element root = doc.createElement("POS_Output");
//        doc.appendChild(root);
//
//        for (int i = 0; i < sents.size(); i++) {
//            SentenceHunk sentenceStruct = sents.get(i);
//            SentenceHunk outcomes = exTagger.tag(sentenceStruct);
//            if (outcomes == null) {
//                continue;
//            }
//            Element sentElem = outcomes.toXmlDetailed_ro(doc);
//            root.appendChild(sentElem);
//        }
//        doc.normalize();
//        return doc;
//    }
//
//    public Document tagTextXmlDetailed_en(String input) throws InstantiationException, IllegalAccessException, ParserConfigurationException, FileNotFoundException, IOException, Exception {
//        UaicSegmenter seg = new UaicSegmenter(uaicMorphologicalDictionary);
//        List<SentenceHunk> sents = seg.segment(input);
//
//        ExtendedPosTagger exTagger = new ExtendedPosTagger(posModel, uaicMorphologicalDictionary, guesserTagset, rulesEngine);
//        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//        Document doc = dBuilder.newDocument();
//
//        Element root = doc.createElement("POS_Output");
//        doc.appendChild(root);
//
//        for (int i = 0; i < sents.size(); i++) {
//            SentenceHunk sentenceStruct = sents.get(i);
//            SentenceHunk outcomes = exTagger.tag(sentenceStruct);
//            if (outcomes == null) {
//                continue;
//            }
//            Element sentElem = outcomes.toXmlDetailed_en(doc);
//            root.appendChild(sentElem);
//        }
//        doc.normalize();
//        return doc;
//    }

    public void tag(INlpSentence sentence) {
        SentenceStruct sent = new SentenceStruct();
        for (int i = 0; i < sentence.getTokenCount(); i++) {
            WordStruct ws = new WordStruct();
            ws.word = sentence.getToken(i).getWordForm();
            sent.add(ws);
        }

        exTagger.tag(sent);

        for (int i = 0; i < sentence.getTokenCount(); i++) {
            sentence.getToken(i).setPostag(sent.get(i).possibleAnnotations.get(0).getMsd());
            sentence.getToken(i).setLemma(sent.get(i).possibleAnnotations.get(0).getLemma());
            sentence.getToken(i).getFeatures().put("posExtra", sent.get(i).possibleAnnotations.get(0).getExtra());
        }
    }

    public String[] tag(String[] strings) {
        return exTagger.tag(strings);
    }

    public void tag(InmemoryCorpus corpus) {
        for (int i = 0; i < corpus.getSentenceCount(); i++) {
            tag(corpus.getSentence(i));
        }
    }
}
