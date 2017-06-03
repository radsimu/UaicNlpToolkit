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

package ro.uaic.info.nlptools.corpus;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import sun.misc.IOUtils;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

public class InmemoryCorpus implements INlpCorpus {
    protected ICorpusFormatConfig loadingConfig;
    private List<InmemorySentence> allSentences;
    private boolean hasInputAnnotations = false;
    String indexPath;
    IndexedLuceneCorpus indexedClone;
    private List<Token> allTokens = new ArrayList<>();
    boolean allTokensDirty = true;

    private void cleanIfDirty() {
        if (allTokensDirty) {
            allTokens.clear();
            for (int i = 0; i < getSentenceCount(); i++)
                for (int j = 0; j < getSentence(i).getTokenCount(); j++)
                    allTokens.add(getSentence(i).getToken(j));
            allTokensDirty = false;
        }
    }

    public IndexedLuceneCorpus getIndexedVersion() throws IOException {
        if (indexedClone == null) {
            File f = new File(indexPath);
            try {
                indexedClone = new IndexedLuceneCorpus(f);
            } catch (Exception ex) {
                indexedClone = IndexedLuceneCorpus.CreateIndexFromInmemoryCorpus(f, this);
            }
        }
        return indexedClone;
    }

    public String getIndexPath() {
        if (indexPath == null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < getSentenceCount(); i++)
                sb.append(getSentence(i).toString());
            indexPath = sb.toString().hashCode() + ".sentenceIndex";
        }
        return indexPath;
    }

    public InmemoryCorpus(File f) throws Exception {
        this(new FileInputStream(f));
        indexPath = Paths.get(f.toPath().getParent().toString(), f.getName().replaceAll("\\.[a-z]+$", ".sentenceIndex")).toString();
    }

    public InmemoryCorpus(InputStream is) throws Exception {
        byte[] bytes = IOUtils.readFully(is, -1, true);
        ByteArrayInputStream buffer = new ByteArrayInputStream(bytes);
        is.close();
        try {
            buffer.reset();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(buffer);
            loadFromXml(doc, inferXmlCorpusConfig(doc));
            return;
        } catch (SAXException e) {
        } catch (IOException e) {
        } catch (ParserConfigurationException e) {
        } try {
            buffer.reset();
            loadFromConll(buffer, inferConllCorpusConfig(buffer));
        }catch (Exception ex){//TODO use a more specific exception here

        }
    }

    public InmemoryCorpus(Document doc) throws Exception {
        this(doc, inferXmlCorpusConfig(doc));
    }

    public InmemoryCorpus(InputStream is, ICorpusFormatConfig loadingConfig) throws Exception {
        throw new NotImplementedException();
    }

    public InmemoryCorpus(File f, ICorpusFormatConfig loadingConfig) throws Exception {
        this(new FileInputStream(f), loadingConfig);
        indexPath = Paths.get(f.toPath().getParent().toString(), f.getName().replaceAll("\\.[a-z]+$", ".sentenceIndex")).toString();
    }

    public InmemoryCorpus(Document doc, XMLFormatConfig loadingConfig) throws Exception {
        loadFromXml(doc, loadingConfig);
    }

    private void loadFromConll(InputStream is, ConllFormatConfig loadingConfig) throws Exception {
        throw new NotImplementedException();//TODO implement this
    }

    private void loadFromXml(Document doc, XMLFormatConfig loadingConfig) throws Exception {
        this.loadingConfig = loadingConfig;
        allSentences = new ArrayList<>();

        Element root = doc.getDocumentElement();
        root.normalize();

        NodeList l = doc.getElementsByTagName(loadingConfig.sentenceNodeName);
        Map<String, Token> tokensToRefIds = new HashMap<>(); //used for startPositionInSentence spanAnnotations
        for (int i = 0; i < l.getLength(); i++) {
            addSentence(new InmemorySentence(l.item(i), tokensToRefIds));
        }

        List<Element> annotationsElems = new ArrayList<>();
        l = doc.getElementsByTagName("GGS:SpanAnnotation");
        for (int i = 0; i < l.getLength(); i++)
            annotationsElems.add((Element) l.item(i));
        l = doc.getElementsByTagName("GGS:Annotation");
        for (int i = 0; i < l.getLength(); i++)
            annotationsElems.add((Element) l.item(i));

        for (Element elem : annotationsElems) {
            SpanAnnotation spanAnnotation = new SpanAnnotation();
            Token startToken = tokensToRefIds.get(elem.getAttribute("GGS:StartTokenRefId"));
            Token endToken = tokensToRefIds.get(elem.getAttribute("GGS:EndTokenRefId"));
            spanAnnotation.setStartTokenIndex(startToken.getTokenIndexInSentence());
            spanAnnotation.setEndTokenIndex(endToken.getTokenIndexInSentence());
            spanAnnotation.setSentence(startToken.getParentSentence());
            spanAnnotation.setName(elem.getAttribute("GGS:Name"));
            for (int j = 0; j < elem.getAttributes().getLength(); j++) {
                Node attr = elem.getAttributes().item(j);
                if (attr.getNodeName().startsWith("GGS:"))
                    continue;
                spanAnnotation.features.put(attr.getNodeName(), attr.getNodeValue());
            }
            spanAnnotation.getSentence().getSpanAnnotations().add(spanAnnotation);
            for (int j = startToken.getTokenIndexInSentence(); j < endToken.getTokenIndexInSentence() + 1; j++) {
                startToken.getParentSentence().getToken(j).getParentSpanAnnotations().add(spanAnnotation);
            }
        }
    }

    private static ConllFormatConfig inferConllCorpusConfig(InputStream is) {
        throw new NotImplementedException();
    }

    private static XMLFormatConfig inferXmlCorpusConfig(Document doc) {
        try {
            XMLFormatConfig ret = new XMLFormatConfig();
            List<String> candidateSentenceNodeNames = new ArrayList<>();
            candidateSentenceNodeNames.add("S");
            candidateSentenceNodeNames.add("s");
            candidateSentenceNodeNames.add("SENTENCE");
            candidateSentenceNodeNames.add("Sentence");
            candidateSentenceNodeNames.add("sentence");
            candidateSentenceNodeNames.add("SENT");
            candidateSentenceNodeNames.add("Sent");
            candidateSentenceNodeNames.add("sent");

            List<String> candidateWordFormAttrNames = new ArrayList<>();
            candidateWordFormAttrNames.add("FORM");
            candidateWordFormAttrNames.add("Form");
            candidateWordFormAttrNames.add("form");
            candidateWordFormAttrNames.add("WORD");
            candidateWordFormAttrNames.add("Word");
            candidateWordFormAttrNames.add("word");
            candidateWordFormAttrNames.add("WORDFORM");
            candidateWordFormAttrNames.add("WordForm");
            candidateWordFormAttrNames.add("Wordform");
            candidateWordFormAttrNames.add("wordform");

            List<String> candidateLemmaAttrNames = new ArrayList<>();
            candidateLemmaAttrNames.add("LEMMA");
            candidateLemmaAttrNames.add("Lemma");
            candidateLemmaAttrNames.add("lemma");
            candidateLemmaAttrNames.add("LEMA");
            candidateLemmaAttrNames.add("Lema");
            candidateLemmaAttrNames.add("lema");
            candidateLemmaAttrNames.add("LEM");
            candidateLemmaAttrNames.add("Lem");
            candidateLemmaAttrNames.add("lem");

            List<String> candidatePostagAttrNames = new ArrayList<>();
            candidatePostagAttrNames.add("POS");
            candidatePostagAttrNames.add("Pos");
            candidatePostagAttrNames.add("pos");
            candidatePostagAttrNames.add("MSD");
            candidatePostagAttrNames.add("Msd");
            candidatePostagAttrNames.add("msd");
            candidatePostagAttrNames.add("POSTAG");
            candidatePostagAttrNames.add("PosTag");
            candidatePostagAttrNames.add("POStag");
            candidatePostagAttrNames.add("Postag");
            candidatePostagAttrNames.add("postag");

            List<String> candidateDeprelAttrNames = new ArrayList<>();
            candidateDeprelAttrNames.add("DEPREL");
            candidateDeprelAttrNames.add("Deprel");
            candidateDeprelAttrNames.add("deprel");

            List<String> candidateHeadAttrNames = new ArrayList<>();
            candidateHeadAttrNames.add("HEAD");
            candidateHeadAttrNames.add("Head");
            candidateHeadAttrNames.add("head");

            //guess sentence node name
            Map<String, Integer> sentNodeNameOccurrences = new HashMap<>();
            for (String candidateSentenceNodeName : candidateSentenceNodeNames) {
                sentNodeNameOccurrences.put(candidateSentenceNodeName, doc.getElementsByTagName(candidateSentenceNodeName).getLength());
            }

            Map<String, NodeList> sentNodeNameLeafsOccurrences = new HashMap<>();
            for (String candidateSentenceNodeName : candidateSentenceNodeNames) {
                if (sentNodeNameOccurrences.get(candidateSentenceNodeName) == 0)
                    continue;
                XPathExpression leafsXPath = XPathFactory.newInstance().newXPath().compile("//" + candidateSentenceNodeName + "//*[count(./*) = 0]");
                NodeList list = (NodeList) leafsXPath.evaluate(doc, XPathConstants.NODESET);
                if (list.getLength()< sentNodeNameOccurrences.get(candidateSentenceNodeName))
                    continue;
                sentNodeNameLeafsOccurrences.put(candidateSentenceNodeName, list);
            }

            if (sentNodeNameLeafsOccurrences.size() > 1){
                throw new UnknownFormatConversionException("Automatic xml format detection is ambiguous. Multiple possible sentence tag names found. Provide an XmlFormatConfig in the constructor");
            }

            if (sentNodeNameLeafsOccurrences.size() == 0){
                throw new UnknownFormatConversionException("Automatic xml format detection couldn't find a valid, common sentence tag name. Provide an XmlFormatConfig in the constructor");
            }

            ret.sentenceNodeName = sentNodeNameLeafsOccurrences.keySet().iterator().next();
            NodeList leafs = sentNodeNameLeafsOccurrences.values().iterator().next();
            //check leaf nodes
            int nodesCountToCheck = Math.min(leafs.getLength(), 50);
            Map<String, Set<String>> foundWordFormsCandidates = new LinkedHashMap<>();
            Map<String, Set<String>> foundLemmasCandidates = new LinkedHashMap<>();
            Map<String,Set<String>> foundPostagsCandidates = new LinkedHashMap<>();
            Map<String,Set<String>> foundDeprelCandidates = new LinkedHashMap<>();
            Map<String,Set<String>> foundHeadCandidates = new LinkedHashMap<>();
            for (int i = 0; i < nodesCountToCheck; i++){
                Element node = (Element) leafs.item(i * leafs.getLength() / nodesCountToCheck);
                if (node.getTextContent()!= null && !node.getTextContent().trim().isEmpty()) {
                    Set<String> vals = foundWordFormsCandidates.get("");
                    if (vals == null) {
                        vals = new HashSet<>();
                        foundWordFormsCandidates.put("", vals); //empty string for attribute name means that the value for the attribute is given as textual content of the node itself
                    }
                    vals.add(node.getTextContent());
                }

                //look for wordforms attribute names candidates
                for (String candidateWordFormNodeName : candidateWordFormAttrNames) {
                    if (node.hasAttribute(candidateWordFormNodeName)){
                        Set<String> vals = foundWordFormsCandidates.get(candidateWordFormNodeName);
                        if (vals == null) {
                            vals = new HashSet<>();
                            foundWordFormsCandidates.put(candidateWordFormNodeName, vals);
                        }
                        vals.add(node.getAttribute(candidateWordFormNodeName));
                    }
                }

                //look for lemma attribute names candidates
                for (String candidateLemmaAttrName : candidateLemmaAttrNames) {
                    if (node.hasAttribute(candidateLemmaAttrName)){
                        Set<String> vals = foundLemmasCandidates.get(candidateLemmaAttrName);
                        if (vals == null) {
                            vals = new HashSet<>();
                            foundLemmasCandidates.put(candidateLemmaAttrName, vals);
                        }
                        vals.add(node.getAttribute(candidateLemmaAttrName));
                    }
                }

                //look for postag attribute names candidates
                for (String candidatePostagAttrName : candidatePostagAttrNames) {
                    if (node.hasAttribute(candidatePostagAttrName)) {
                        Set<String> vals = foundPostagsCandidates.get(candidatePostagAttrName);
                        if (vals == null) {
                            vals = new HashSet<>();
                            foundPostagsCandidates.put(candidatePostagAttrName, vals);
                        }
                        vals.add(node.getAttribute(candidatePostagAttrName));
                    }
                }

                //look for deprel attribute names candidates
                for (String candidateDeprelAttrName: candidateDeprelAttrNames) {
                    if (node.hasAttribute(candidateDeprelAttrName)) {
                        Set<String> vals = foundDeprelCandidates.get(candidateDeprelAttrName);
                        if (vals == null) {
                            vals = new HashSet<>();
                            foundDeprelCandidates.put(candidateDeprelAttrName, vals);
                        }
                        vals.add(node.getAttribute(candidateDeprelAttrName));
                    }
                }

                //look for head attribute names candidates
                for (String candidateHeadAttrName: candidateHeadAttrNames) {
                    if (node.hasAttribute(candidateHeadAttrName) && node.getAttribute(candidateHeadAttrName).matches("[0-9]*")) {
                        Set<String> vals = foundHeadCandidates.get(candidateHeadAttrName);
                        if (vals == null) {
                            vals = new HashSet<>();
                            foundHeadCandidates.put(candidateHeadAttrName, vals);
                        }
                        vals.add(node.getAttribute(candidateHeadAttrName));
                    }
                }
            }


            int targetTagsetSize = 400;
            int targetDeprelsSize = 50;
            int targetToksPerSent = 10;


            if (!foundDeprelCandidates.isEmpty()){
                String best = foundDeprelCandidates.entrySet().stream().sorted(new Comparator<Map.Entry<String, Set<String>>>() {
                    @Override
                    public int compare(Map.Entry<String, Set<String>> string2SetEntry1, Map.Entry<String, Set<String>> string2SetEntry2) {
                        return Integer.compare(Math.abs(string2SetEntry1.getValue().size() - targetDeprelsSize), Math.abs(string2SetEntry2.getValue().size() - targetDeprelsSize));
                    }
                }).iterator().next().getKey();

                ret.dependencyLabelAttributeName = best;
            }

            if (!foundHeadCandidates.isEmpty()){
                String best = foundHeadCandidates.entrySet().stream().sorted(new Comparator<Map.Entry<String, Set<String>>>() {
                    @Override
                    public int compare(Map.Entry<String, Set<String>> string2SetEntry1, Map.Entry<String, Set<String>> string2SetEntry2) {
                        return Integer.compare(Math.abs(string2SetEntry1.getValue().size() - targetToksPerSent), Math.abs(string2SetEntry2.getValue().size() - targetToksPerSent));
                    }
                }).iterator().next().getKey();

                ret.dependencyHeadAttributeName = best;
            }

            if (!foundLemmasCandidates.isEmpty()){
                String best = foundLemmasCandidates.entrySet().stream().sorted(new Comparator<Map.Entry<String, Set<String>>>() {
                    @Override
                    public int compare(Map.Entry<String, Set<String>> string2SetEntry1, Map.Entry<String, Set<String>> string2SetEntry2) {
                        return Integer.compare(string2SetEntry2.getValue().size(), string2SetEntry1.getValue().size());
                    }
                }).iterator().next().getKey();

                ret.lemmaAttributeName = best;
            }

            if (!foundWordFormsCandidates.isEmpty()) {
                String best = foundWordFormsCandidates.entrySet().stream().sorted(new Comparator<Map.Entry<String, Set<String>>>() {
                    @Override
                    public int compare(Map.Entry<String, Set<String>> string2SetEntry1, Map.Entry<String, Set<String>> string2SetEntry2) {
                        return Integer.compare(string2SetEntry2.getValue().size(), string2SetEntry1.getValue().size());
                    }
                }).iterator().next().getKey();

                ret.wordFormAttributeName = best;
            }

            if (!foundPostagsCandidates.isEmpty()) {
                String best = foundPostagsCandidates.entrySet().stream().sorted(new Comparator<Map.Entry<String, Set<String>>>() {
                    @Override
                    public int compare(Map.Entry<String, Set<String>> string2SetEntry1, Map.Entry<String, Set<String>> string2SetEntry2) {
                        return Integer.compare(Math.abs(string2SetEntry1.getValue().size() - targetTagsetSize), Math.abs(string2SetEntry2.getValue().size() - targetTagsetSize));
                    }
                }).iterator().next().getKey();

                ret.posAttributeName = best;
            }

            return ret;
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        return null;
    }

    public InmemoryCorpus() {
        allSentences = new ArrayList<>();
    }

    public InmemoryCorpus(String file, ICorpusFormatConfig loadingConfig) throws Exception {
        this(new File(file), loadingConfig);
    }

    public void addSentences(List<InmemorySentence> sentences) {
        for (InmemorySentence sentence : sentences) {
            addSentence(sentence);
        }
    }

    public void addSentence(InmemorySentence sentence) {
        sentence.setSentenceIndex(allSentences.size());
        sentence.setParentCorpus(this);
        if (allSentences.size() == 0)
            sentence.setStartTokenIndex(0);
        else
            sentence.setStartTokenIndex(getTokenCount());
        allSentences.add(sentence);
        allTokens.addAll(sentence.tokens);
        hasInputAnnotations |= sentence.hasInputAnnotations();
    }

    /**
     * Creates a document which contains only the spanAnnotations from matches. Any input spanAnnotations will be discarded. The contained spanAnnotations will be saved in an inline manner
     *
     * @return returns the Document object with the contents
     */

    public enum DOMConversionSpanRepresentation{
        INLINE, STANDOFF, PRESERVE, OPTIMAL_INLINE, AUTO // AUTO first tries inline, then preserve if span annotations are marked as inline and if nestable, then optimal_inline
    }

    public Document convertToDOM() {
        return convertToDOM(DOMConversionSpanRepresentation.AUTO);
    }

    public Document convertToDOM(DOMConversionSpanRepresentation representationConfig) {
        Document doc;
        Element root;

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        doc = dBuilder.newDocument();
        root = doc.createElement("Document");
        root.setAttribute("xmlns:GGS", "http://nlptools.info.uaic.ro/GGS");
        doc.appendChild(root);

        List<SpanAnnotation> allSpanAnnotations = new ArrayList<>();
        for (InmemorySentence sentence : allSentences) {
            sentence.getSpanAnnotations().sort(annotationSorter);
            allSpanAnnotations.addAll(sentence.getSpanAnnotations());
        }
        allSpanAnnotations.sort(annotationSorter);

        boolean nestingAll = false;

        if (representationConfig == DOMConversionSpanRepresentation.INLINE || representationConfig == DOMConversionSpanRepresentation.AUTO) {
            //detect if nesting all annotations is possible
            nestingAll = isNestingPossible(allSpanAnnotations);
        }
        if (!nestingAll && representationConfig == DOMConversionSpanRepresentation.INLINE)
            throw new IllegalStateException("The current span annotation set is not all nestable");

        boolean nestOptimal = false;
        List<SpanAnnotation> inlineSpanAnnotations = allSpanAnnotations;
        List<SpanAnnotation> standOffSpanAnnotations = new ArrayList<>();

        if (!nestingAll) {
            if (representationConfig == DOMConversionSpanRepresentation.PRESERVE || representationConfig == DOMConversionSpanRepresentation.AUTO) {
                //detect if preserving the initial nesting is possible
                inlineSpanAnnotations = new ArrayList<>();

                for (SpanAnnotation annotation : allSpanAnnotations)
                    if (annotation.isPreferInlineRepresentation())
                        inlineSpanAnnotations.add(annotation);
                    else
                        standOffSpanAnnotations.add(annotation);
                if (!inlineSpanAnnotations.isEmpty()) {//some annotations prefer inline representation
                    //check if nesting is possible for these
                    if (!isNestingPossible(inlineSpanAnnotations))
                        nestOptimal = true;//the anotations that prefer inline cannot be nested - will determine which can be inline and which not
                    if (nestOptimal && representationConfig == DOMConversionSpanRepresentation.PRESERVE)
                        throw new IllegalStateException("Span annotations marked as inline cannot all be nested");
                } else {
                    if (representationConfig == DOMConversionSpanRepresentation.AUTO)
                        nestOptimal = true;
                }
            }

            if ((representationConfig == DOMConversionSpanRepresentation.OPTIMAL_INLINE || representationConfig == DOMConversionSpanRepresentation.AUTO) && nestOptimal) {
                Set<SpanAnnotation> optimalInlineAnnotationsSet = determineMaximalInlineSpanAnnotationsSet(allSpanAnnotations);
                inlineSpanAnnotations = new ArrayList<>(optimalInlineAnnotationsSet);
                standOffSpanAnnotations.clear();
                for (SpanAnnotation spanAnnotation : allSpanAnnotations) {
                    if (!optimalInlineAnnotationsSet.contains(spanAnnotation))
                        standOffSpanAnnotations.add(spanAnnotation);
                }
            } else if (representationConfig == DOMConversionSpanRepresentation.STANDOFF) {//STANDOFF representation
                inlineSpanAnnotations = new ArrayList<>();
                standOffSpanAnnotations = allSpanAnnotations;
            }
        }

        long lastId = 0;
        Map<Token, String> tokensToRefIds = new HashMap<>();
        Set<SpanAnnotation> inlineAnnotationsSet = new HashSet<>(inlineSpanAnnotations);

        for (InmemorySentence sentence : allSentences) {
            Element sentenceElem = sentenceToXml(sentence, doc);
            root.appendChild(sentenceElem);

            Stack<SpanAnnotation> annotationsStack = new Stack<>();
            int lastAnnotIndex = 0;
            while (lastAnnotIndex < sentence.getSpanAnnotations().size() && !inlineAnnotationsSet.contains(sentence.getSpanAnnotations().get(lastAnnotIndex)))//skip annotations that will be saved standoff
                lastAnnotIndex++;
            Element lastParentElem = sentenceElem;

            for (int i = 0; i < sentence.getTokenCount(); i++) {
                Token token = sentence.getToken(i);
                Element wordElem = wordToXml(token, doc);
                String refId = "GGS:TokenId" + lastId++;
                tokensToRefIds.put(sentence.getToken(i), refId);
                wordElem.setAttribute("GGS:RefId", refId);

                while (lastAnnotIndex < sentence.getSpanAnnotations().size() && sentence.getSpanAnnotations().get(lastAnnotIndex).getStartTokenIndex() == token.getTokenIndexInSentence()) {
                    annotationsStack.push(sentence.getSpanAnnotations().get(lastAnnotIndex));
                    Element spanAnnotationElem = doc.createElement(annotationsStack.peek().getName());
                    for (String key : annotationsStack.peek().getFeatures().keySet())
                        spanAnnotationElem.setAttribute(key, annotationsStack.peek().getFeatures().get(key));
                    lastParentElem.appendChild(spanAnnotationElem);
                    lastParentElem = spanAnnotationElem;
                    lastAnnotIndex++;
                    while (lastAnnotIndex < sentence.getSpanAnnotations().size() && !inlineAnnotationsSet.contains(sentence.getSpanAnnotations().get(lastAnnotIndex)))//skip annotations that will be saved standoff
                        lastAnnotIndex++;
                }

                lastParentElem.appendChild(wordElem);

                while (!annotationsStack.isEmpty() && annotationsStack.peek().getEndTokenIndex() == token.getTokenIndexInSentence()) {
                    annotationsStack.pop();
                    lastParentElem = (Element) lastParentElem.getParentNode();
                }
            }
            assert lastParentElem == sentenceElem;
        }

        //add standoff span annotations at the end of document
        for (SpanAnnotation spanAnnotation : standOffSpanAnnotations) {
            Element annotationElem = doc.createElement("GGS:SpanAnnotation");
            annotationElem.setAttribute("GGS:StartTokenRefId", tokensToRefIds.get(spanAnnotation.getSentence().getToken(spanAnnotation.getStartTokenIndex())));
            annotationElem.setAttribute("GGS:EndTokenRefId", tokensToRefIds.get(spanAnnotation.getSentence().getToken(spanAnnotation.getEndTokenIndex())));
            annotationElem.setAttribute("GGS:Name", spanAnnotation.getName());
            for (Map.Entry<String, String> entry : spanAnnotation.getFeatures().entrySet()) {
                annotationElem.setAttribute(entry.getKey(), entry.getValue());
            }
            root.appendChild(annotationElem);
        }

        doc.normalize();
        return doc;
    }

    private boolean isNestingPossible(List<SpanAnnotation> spanAnnotations) {
        spanAnnotations.sort(annotationSorter);
        for (int i = 0; i < spanAnnotations.size() - 1; i++) {
            int ii = i + 1;
            SpanAnnotation annotation_i = spanAnnotations.get(i);
            while (ii < spanAnnotations.size() && annotation_i.getSentence() == spanAnnotations.get(ii).getSentence()) {
                SpanAnnotation annotation_ii = spanAnnotations.get(ii);
                if (annotation_i.getEndTokenIndex() >= annotation_ii.getStartTokenIndex() &&
                        annotation_i.getEndTokenIndex() < annotation_ii.getEndTokenIndex()) {
                    return false;
                }
                ii++;
            }
        }
        return  true;
    }

    protected Element wordToXml(Token w, Document doc) {
        Element wElem = doc.createElement("W");
        wElem.appendChild(doc.createTextNode(w.getFeatures().get("WORD")));
        for (String key : w.getFeatures().keySet()) {
            if (key.equals("WORD")) continue;
            if (key.equals("GGS:RefId")) continue;
            wElem.setAttribute(key, w.getFeatures().get(key));
        }
        return wElem;
    }

    protected Element sentenceToXml(InmemorySentence s, Document doc) {
        Element sentenceElem = doc.createElement("S");
        for (Map.Entry<String, String> entry : s.features.entrySet()) {
            sentenceElem.setAttribute(entry.getKey(), entry.getValue());
        }
        return sentenceElem;
    }

//    public InmemoryCorpus mergeOutputAnnotations(List<SpanAnnotation> matches) throws Exception {
//        return new InmemoryCorpus(saveTextWithMatches(matches), sentenceNodeName);
//    }

    @Override
    public ICorpusFormatConfig getLoadingCorpusFormatConfiguration() { // if null, corpus was generated from code
        return loadingConfig;
    }

    @Override
    public int getSentenceCount() {
        return allSentences.size();
    }

    @Override
    public int getTokenCount() {
        cleanIfDirty();
        return allTokens.size();
    }

    @Override
    public Token getToken(int index) {
        cleanIfDirty();
        return allTokens.get(index);
    }

    @Override
    public INlpSentence getSentence(int index) {
        return allSentences.get(index);
    }

    public boolean hasIndexedVersion() {
        return indexedClone != null;
    }

    public void mergeSpanAnnotations(List<SpanAnnotation> annotations) {
        //sort annotations by start index
        List<SpanAnnotation> sortedAnnotations = new ArrayList<>(annotations);
        sortedAnnotations.sort(annotationSorter);

        //for each, identify covered sentences and tokens

        Set<INlpSentence> sentences = new HashSet<>();
        Set<Token> tokens = new HashSet<>();
        for (SpanAnnotation annotation : annotations) {
            INlpSentence sentence = getSentence(annotation.getSentence().getSentenceIndexInCorpus()); //the annotations might refer to a clone of this corpus - remap based on sentence indexes
            SpanAnnotation clonedAnnotation = new SpanAnnotation(annotation);
            clonedAnnotation.setSentence(sentence);
            sentence.getSpanAnnotations().add(annotation);
            sentences.add(sentence);
            for (int i = annotation.getStartTokenIndex(); i <= annotation.getEndTokenIndex(); i++) {
                Token t = sentence.getToken(i);
                t.getParentSpanAnnotations().add(annotation);
                tokens.add(t);
            }
        }

        //for each token and sentence covered, set child/parent annotations
        for (Token t : tokens) {
            t.getParentSpanAnnotations().sort(annotationSorter);
        }
        for (INlpSentence s : sentences) {
            s.getSpanAnnotations().sort(annotationSorter);
        }
    }

    private Comparator<SpanAnnotation> annotationSorter = new Comparator<SpanAnnotation>() {
        @Override
        public int compare(SpanAnnotation sa1, SpanAnnotation sa2) {
            //sort by starting token index in corpus
            //when starting index is equal, the longer annotation comes first
            int rez = Integer.compare(sa1.getSentence().getToken(sa1.getStartTokenIndex()).getTokenIndexInCorpus(), sa2.getSentence().getToken(sa2.getStartTokenIndex()).getTokenIndexInCorpus());
            if (rez == 0)
                rez = -Integer.compare(sa1.getSentence().getToken(sa1.getEndTokenIndex()).getTokenIndexInCorpus(), sa2.getSentence().getToken(sa2.getEndTokenIndex()).getTokenIndexInCorpus());
            return rez;
        }
    };

    public InmemoryCorpus clone(){
        try {
            return new InmemoryCorpus(convertToDOM(DOMConversionSpanRepresentation.PRESERVE), (XMLFormatConfig) loadingConfig == null ? XMLFormatConfig.UaicCommonFormat : (XMLFormatConfig) loadingConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Set<SpanAnnotation> determineMaximalInlineSpanAnnotationsSet(List<SpanAnnotation> spanAnnotations) {
        //determine the amount of over crossing span annotations for each span annotation
        //sort them by amount in a list of sets - each index representing the amount of over-crossing for the contained span annotations
        //then eliminate one by one all annotations from these sets, starting with the ones with the most over-crossings - eliminating them means adjusting their neighbours over-crossing ammount, so that means moving them to the corresponding container
        Map<SpanAnnotation, Set<SpanAnnotation>> annotationsToOvercrossingGroups = new HashMap<>();
        List<Set<SpanAnnotation>> annotationsPerOvercrossingNeighboursAmmount = new ArrayList<>();

        spanAnnotations.sort(annotationSorter);
        //populate annotationsToOvercrossingGroups
        for (int i = 0; i < spanAnnotations.size(); i++) {
            int ii = i + 1;
            SpanAnnotation annotation_i = spanAnnotations.get(i);
            Set<SpanAnnotation> crossing_i = null;

            while (ii < spanAnnotations.size() && annotation_i.getSentence() == spanAnnotations.get(ii).getSentence()) {
                SpanAnnotation annotation_ii = spanAnnotations.get(ii);

                if (annotation_i.getEndTokenIndex() >= annotation_ii.getStartTokenIndex() &&
                        annotation_i.getEndTokenIndex() < annotation_ii.getEndTokenIndex()) {
                    if (crossing_i == null) {
                        crossing_i = annotationsToOvercrossingGroups.get(annotation_i);
                        if (crossing_i == null) {
                            crossing_i = new HashSet<>();
                            annotationsToOvercrossingGroups.put(annotation_i, crossing_i);
                        }
                    }
                    crossing_i.add(annotation_ii);

                    Set<SpanAnnotation> crossing_ii = annotationsToOvercrossingGroups.get(annotation_ii);
                    if (crossing_ii == null) {
                        crossing_ii = new HashSet<>();
                        annotationsToOvercrossingGroups.put(annotation_ii, crossing_ii);
                    }
                    crossing_ii.add(annotation_i);
                }
                ii++;
            }
        }

        //populate annotationsPerOvercrossingNeighboursAmmount
        annotationsPerOvercrossingNeighboursAmmount.add(new HashSet<>(spanAnnotations));
        for (Map.Entry<SpanAnnotation, Set<SpanAnnotation>> spanAnnotationSetEntry : annotationsToOvercrossingGroups.entrySet()) {
            int overcrossingAmmount = spanAnnotationSetEntry.getValue().size();
            while (annotationsPerOvercrossingNeighboursAmmount.size() <= overcrossingAmmount) //increase list size to cover all crossing amounts
                annotationsPerOvercrossingNeighboursAmmount.add(new HashSet<>());
            annotationsPerOvercrossingNeighboursAmmount.get(overcrossingAmmount).add(spanAnnotationSetEntry.getKey());
            annotationsPerOvercrossingNeighboursAmmount.get(0).remove(spanAnnotationSetEntry.getKey());
        }

        //start removing annotations from
        for (int i = annotationsPerOvercrossingNeighboursAmmount.size() - 1 ; i > 0; i--){
            while(annotationsPerOvercrossingNeighboursAmmount.get(i).size()>0){
                SpanAnnotation annotationToRemove = annotationsPerOvercrossingNeighboursAmmount.get(i).stream().findAny().get();
                Set<SpanAnnotation> crossingSpans = annotationsToOvercrossingGroups.get(annotationToRemove);//these are the annotations that must be altered by the removal
                for (SpanAnnotation crossingSpan : crossingSpans) {
                    //move the crossing span to the lower container, because its over crossing spans amount has decreased by 1
                    annotationsPerOvercrossingNeighboursAmmount.get(annotationsToOvercrossingGroups.get(crossingSpan).size()).remove(crossingSpan);
                    annotationsPerOvercrossingNeighboursAmmount.get(annotationsToOvercrossingGroups.get(crossingSpan).size() - 1).add(crossingSpan);

                    //adjust the crossing groups of the annotations that are altered by the removal
                    annotationsToOvercrossingGroups.get(crossingSpan).remove(annotationToRemove);
                    //check if crossing span now has no other crossing spans and if so, remove empty sets
                    if (annotationsToOvercrossingGroups.get(crossingSpan).isEmpty())
                        annotationsToOvercrossingGroups.remove(crossingSpan);
                }
                annotationsToOvercrossingGroups.remove(annotationToRemove);
                annotationsPerOvercrossingNeighboursAmmount.get(i).remove(annotationToRemove);
            }
            annotationsPerOvercrossingNeighboursAmmount.remove(i);
        }

        return annotationsPerOvercrossingNeighboursAmmount.get(0);
    }
}
