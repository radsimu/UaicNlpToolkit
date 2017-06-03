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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class IndexedLuceneCorpus implements INlpCorpus {
    private IndexedFormatConfig loadingConfig;
    private FSDirectory tokensIndex;
    private FSDirectory sentencesIndex;
    private FSDirectory annotationsIndex;
    private WeakHashMap<Integer, Token> cachedTokens = new WeakHashMap<>();
    private WeakHashMap<Integer, SpanAnnotation> cachedAnnotations = new WeakHashMap<>();
    public IndexSearcher tokenSearcher;
    public IndexSearcher annotationSearcher;
    public IndexSearcher sentenceSearcher;
    private Set<String> allAttributeNames = new HashSet<>();

    IndexedLuceneCorpus() {
    }

    public IndexedLuceneCorpus(File indexedCorpusFolder) throws IOException {
        this.tokensIndex = FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "tokens"));
        this.sentencesIndex = FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "sentences"));
        this.annotationsIndex = FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "annotations"));
        DirectoryReader r = DirectoryReader.open(tokensIndex);
        tokenSearcher = new IndexSearcher(r);

        r = DirectoryReader.open(sentencesIndex);
        sentenceSearcher = new IndexSearcher(r);

        r = DirectoryReader.open(annotationsIndex);
        annotationSearcher = new IndexSearcher(r);
    }

    public static IndexedLuceneCorpus CreateIndexFromInmemoryCorpus(File indexedCorpusFolder, InmemoryCorpus inmemoryInput) throws IOException {
        long time = System.currentTimeMillis();
        IndexedLuceneCorpus inputText = new IndexedLuceneCorpus();

        Analyzer analyzer = new KeywordAnalyzer();

        rmdir(Paths.get(indexedCorpusFolder.toString(), "tokens").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "sentences").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "annotations").toFile());

        Paths.get(indexedCorpusFolder.toString(), "tokens").toFile().mkdirs();
        Paths.get(indexedCorpusFolder.toString(), "sentences").toFile().mkdirs();
        Paths.get(indexedCorpusFolder.toString(), "annotations").toFile().mkdirs();

        IndexWriter tokensWriter = new IndexWriter(FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "tokens")), new IndexWriterConfig(analyzer));
        IndexWriter sentencesWriter = new IndexWriter(FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "sentences")), new IndexWriterConfig(analyzer));
        IndexWriter annotationsWriter = new IndexWriter(FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "annotations")), new IndexWriterConfig(analyzer));

        Map<SpanAnnotation, Integer> annotationsIndexes = new HashMap<>();
        for (int i =0;i<inmemoryInput.getSentenceCount();i++){
            Document sentDoc = new Document();
            INlpSentence s = inmemoryInput.getSentence(i);
            sentDoc.add(new IntField("GGS:StartTokenIndex", s.getFirstTokenIndexInCorpus(), Field.Store.YES));
            sentDoc.add(new IntField("GGS:EndTokenIndex", s.getFirstTokenIndexInCorpus()+s.getTokenCount(), Field.Store.YES));

            for (SpanAnnotation spanAnnotation : inmemoryInput.getSentence(i).getSpanAnnotations()) {
                Document aDoc = new Document();
                aDoc.add(new IntField("GGS:StartTokenIndex", s.getFirstTokenIndexInCorpus() + spanAnnotation.getStartTokenIndex(), Field.Store.YES));
                aDoc.add(new IntField("GGS:EndTokenIndex", s.getFirstTokenIndexInCorpus() + spanAnnotation.getEndTokenIndex(), Field.Store.YES));
                aDoc.add(new StringField("GGS:Name", spanAnnotation.getName(), Field.Store.YES));
                for (Map.Entry<String, String> entry : spanAnnotation.getFeatures().entrySet()){
                    aDoc.add(new StringField(entry.getKey(),entry.getValue(), Field.Store.YES));
                }
                sentDoc.add(new IntField("GGS:SpanAnnotation", annotationsIndexes.size(), Field.Store.YES));
                annotationsIndexes.put(spanAnnotation, annotationsIndexes.size() );
                annotationsWriter.addDocument(aDoc);
            }
            sentencesWriter.addDocument(sentDoc);
        }

        for (int i =0; i<  inmemoryInput.getTokenCount(); i++) {
            Token t = inmemoryInput.getToken(i);
            Document doc = new Document();
            for (Map.Entry<String, String> entry : t.getFeatures().entrySet()) {
                doc.add(new StringField(entry.getKey(), entry.getValue(), Field.Store.YES));
            }
            doc.add(new IntField("GGS:Sentence", t.getParentSentence().getSentenceIndexInCorpus(), Field.Store.YES));
            for (SpanAnnotation a : t.parentAnnotations)
                doc.add(new IntField("GGS:SpanAnnotation", annotationsIndexes.get(a), Field.Store.YES));
            tokensWriter.addDocument(doc);
        }

        annotationsWriter.close();
        tokensWriter.close();
        sentencesWriter.close();

        //TODO: use a  logger for every system.out.print !!!
        System.out.println("Indexed input corpus:\n" + (System.currentTimeMillis() - time));
        return new IndexedLuceneCorpus(indexedCorpusFolder);
    }

    public static IndexedLuceneCorpus CreateIndexFromXmlFiles(File indexedCorpusFolder, File xmlData, String sentElem) throws Exception {
        if (!xmlData.exists())
            throw new FileNotFoundException();
        Analyzer analyzer = new KeywordAnalyzer();

        rmdir(Paths.get(indexedCorpusFolder.toString(), "tokensTemp").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "sentencesTemp").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "annotationsTemp").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "tokens").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "sentences").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "annotations").toFile());

        Paths.get(indexedCorpusFolder.toString(), "tokensTemp").toFile().mkdirs();
        Paths.get(indexedCorpusFolder.toString(), "sentencesTemp").toFile().mkdirs();
        Paths.get(indexedCorpusFolder.toString(), "annotationsTemp").toFile().mkdirs();
        Paths.get(indexedCorpusFolder.toString(), "tokens").toFile().mkdirs();
        Paths.get(indexedCorpusFolder.toString(), "sentences").toFile().mkdirs();
        Paths.get(indexedCorpusFolder.toString(), "annotations").toFile().mkdirs();

        FSDirectory tempTokensIndex = FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "tokensTemp"));
        IndexWriter tokensWriter = new IndexWriter(tempTokensIndex, new IndexWriterConfig(analyzer));
        FSDirectory tempSentencesIndex = FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "sentencesTemp"));
        IndexWriter sentencesWriter = new IndexWriter(tempSentencesIndex, new IndexWriterConfig(analyzer));
        FSDirectory tempAnnotationsIndex = FSDirectory.open(Paths.get(indexedCorpusFolder.toString(), "annotationsTemp"));
        IndexWriter annotationsWriter = new IndexWriter(tempAnnotationsIndex, new IndexWriterConfig(analyzer));

        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        SAXParser parser = saxParserFactory.newSAXParser();
        long time = System.currentTimeMillis();
        InputTextParserHandler handler = new InputTextParserHandler(tokensWriter, sentencesWriter, annotationsWriter, sentElem);
        if (xmlData.isFile())
            parser.parse(xmlData, handler);
        else
            for (File child : xmlData.listFiles())
                if (child.isFile())
                    parser.parse(child, handler);
        annotationsWriter.close();
        tokensWriter.close();
        sentencesWriter.close();
        UpdateInterIndexReferences(indexedCorpusFolder,
                new IndexSearcher(DirectoryReader.open(annotationsWriter.getDirectory())),
                new IndexSearcher(DirectoryReader.open(tokensWriter.getDirectory())),
                new IndexSearcher(DirectoryReader.open(sentencesWriter.getDirectory())),
                analyzer);

        System.out.println("Indexed xml files corpus:\n" + (System.currentTimeMillis() - time));

        rmdir(Paths.get(indexedCorpusFolder.toString(), "tokensTemp").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "sentencesTemp").toFile());
        rmdir(Paths.get(indexedCorpusFolder.toString(), "annotationsTemp").toFile());

        return new IndexedLuceneCorpus(indexedCorpusFolder);
    }

    private static void UpdateInterIndexReferences(File indexFolder, IndexSearcher tempAnnotationSearcher, IndexSearcher tempTokenSearcher, IndexSearcher tempSentenceSearcher, Analyzer analyzer) throws IOException {
        List<Integer> annotations;

        IndexWriter annotationWriter = new IndexWriter(FSDirectory.open(Paths.get(indexFolder.toString(), "annotations")), new IndexWriterConfig(analyzer));
        for (int i = 0; i < tempAnnotationSearcher.getIndexReader().numDocs(); i++) {
            Document doc = tempAnnotationSearcher.doc(i);
            Document newDoc = new Document();
            for (IndexableField f : doc.getFields()) {
                if (f.name().equals("GGS:StartTokenRefId"))
                    newDoc.add(new IntField("GGS:StartTokenIndex", tempTokenSearcher.search(new TermQuery(new Term("GGS:RefId", f.stringValue())), 1).scoreDocs[0].doc, Field.Store.YES));
                else if (f.name().equals("GGS:EndTokenRefId"))
                    newDoc.add(new IntField("GGS:EndTokenIndex", tempTokenSearcher.search(new TermQuery(new Term("GGS:RefId", f.stringValue())), 1).scoreDocs[0].doc, Field.Store.YES));
                else
                    newDoc.add(f);
            }
            annotationWriter.addDocument(newDoc);
        }
        annotationWriter.close();
        tempAnnotationSearcher = new IndexSearcher(DirectoryReader.open(annotationWriter.getDirectory()));

        Map<Integer, List<Integer>> toksAnnotations = new HashMap<>();
        Map<Integer, List<Integer>> sentsAnnotations = new HashMap<>();


        for (int i = 0; i < tempAnnotationSearcher.getIndexReader().numDocs(); i++) {
            Document doc = tempAnnotationSearcher.doc(i);
            int start = doc.getField("GGS:StartTokenIndex").numericValue().intValue();
            int end = doc.getField("GGS:EndTokenIndex").numericValue().intValue();
            for (int j = start; j <= end; j++) {
                annotations = toksAnnotations.get(j);
                if (annotations == null) {
                    annotations = new ArrayList<>();
                    toksAnnotations.put(j, annotations);
                }
                annotations.add(i);
            }

            int sentIndex = tempTokenSearcher.doc(start).getField("GGS:Sentence").numericValue().intValue();
            annotations = sentsAnnotations.get(sentIndex);
            if (annotations == null) {
                annotations = new ArrayList<>();
                sentsAnnotations.put(sentIndex, annotations);
            }
            annotations.add(i);
        }

        IndexWriter tokenWriter = new IndexWriter(FSDirectory.open(Paths.get(indexFolder.toString(), "tokens")), new IndexWriterConfig(analyzer));

        for (int i = 0; i < tempTokenSearcher.getIndexReader().numDocs(); i++) {
            Document doc = tempTokenSearcher.doc(i);
            Document newDoc = new Document();
            for (IndexableField f : doc.getFields()) {
                newDoc.add(f);
            }

            annotations = toksAnnotations.get(i);
            if (annotations != null) {
                for (int k : annotations)
                    newDoc.add(new IntField("GGS:SpanAnnotation", k, Field.Store.YES));
            }
            tokenWriter.addDocument(newDoc);
        }
        tokenWriter.close();

        IndexWriter sentenceWriter = new IndexWriter(FSDirectory.open(Paths.get(indexFolder.toString(), "sentences")), new IndexWriterConfig(analyzer));
        for (int i = 0; i < tempSentenceSearcher.getIndexReader().numDocs(); i++) {
            Document doc = tempSentenceSearcher.doc(i);
            Document newDoc = new Document();
            for (IndexableField f : doc.getFields()) {
                newDoc.add(f);
            }

            annotations = sentsAnnotations.get(i);
            if (annotations != null) {
                for (int k : annotations)
                    newDoc.add(new IntField("GGS:SpanAnnotation", k, Field.Store.YES));
            }
            sentenceWriter.addDocument(newDoc);
        }
        sentenceWriter.close();

        tempTokenSearcher.getIndexReader().close();
        tempAnnotationSearcher.getIndexReader().close();
        tempSentenceSearcher.getIndexReader().close();
    }

    private static void rmdir(File f) {
        if (f.isDirectory())
            for (File file : f.listFiles())
                rmdir(file);
        f.delete();
    }

    //use a cache of token objects here - not worth generating objects every time?
    //or use an indexed token class which grabs data from sentenceIndex on first request
    //o sa fie nevoie de indesci separati (as in coloane de tabel diferite), pt: tokens, sentences, input spanAnnotations
    //spanAnnotations momentan le tinem la urma in aceeasi "coloana" cu tokenii...

    //dar pe ce indexam la input spanAnnotations? pai... spanAnnotations or sa fie tinute ca intr-uo galeata comuna si vor fi indexate pe starttoken si pe endtoken, date ca corpus position
    //cu un lucene query indexat query are trebui sa putem obtine relativ usor lista de spanAnnotations care incepu


    @Override
    public ICorpusFormatConfig getLoadingCorpusFormatConfiguration() {
        return loadingConfig;
    }

    @Override
    public int getSentenceCount() {
        return sentenceSearcher.getIndexReader().numDocs();
    }

    @Override
    public int getTokenCount() {
        return tokenSearcher.getIndexReader().numDocs();
    }

    @Override
    public Token getToken(int index) {
        Token ret = cachedTokens.get(index);
        if (ret == null) {
            ret = new Token();
            try {
                Document doc = tokenSearcher.doc(index);
                for (IndexableField f : doc.getFields())
                    if (!f.name().startsWith("GGS:"))
                        ret.getFeatures().put(f.name(), f.stringValue());
                    else if (f.name().equals("GGS:SpanAnnotation"))
                        ret.parentAnnotations.add(getAnnotation(f.numericValue().intValue()));
                    else if (f.name().equals("GGS:Sentence"))
                        ret.parentSentence = getSentence(f.numericValue().intValue());
                ret.indexInSentence = index - ret.parentSentence.getFirstTokenIndexInCorpus();
            } catch (IOException e) {
                e.printStackTrace();
            }
            cachedTokens.put(index, ret);
        }
        return ret;
    }

    SpanAnnotation getAnnotation(int i) {
        SpanAnnotation ret = cachedAnnotations.get(i);
        if (ret == null) {
            ret = new SpanAnnotation();
            try {
                Document doc = annotationSearcher.doc(i);
                ret.setSentence(this.getSentence(tokenSearcher.doc(doc.getField("GGS:StartTokenIndex").numericValue().intValue()).getField("GGS:Sentence").numericValue().intValue()));
                ret.setName(doc.get("GGS:Name"));
                for (IndexableField f : doc.getFields())
                    if (!f.name().startsWith("GGS:"))
                        ret.getFeatures().put(f.name(), f.stringValue());
                    else if (f.name().equals("GGS:StartTokenIndex"))
                        ret.setStartTokenIndex(f.numericValue().intValue());
                    else if (f.name().equals("GGS:EndTokenIndex"))
                        ret.setEndTokenIndex(f.numericValue().intValue());
            } catch (IOException e) {
                e.printStackTrace();
            }
            cachedAnnotations.put(i, ret);
        }
        return ret;
    }


    @Override
    public INlpSentence getSentence(int index) {
        try {
            return new IndexedLuceneSentence(index, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Set<String> getAllAttributeNames() {
        if (allAttributeNames == null) {
            if (allAttributeNames == null) {
                allAttributeNames = new HashSet<>();
                try {
                    for (String f : SlowCompositeReaderWrapper.wrap(tokenSearcher.getIndexReader()).fields())
                        if (!f.startsWith("GGS:"))
                            getAllAttributeNames().add(f);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return allAttributeNames;
    }

    public int getAnnotationsCount() {
        return annotationSearcher.getIndexReader().numDocs();
    }

    static class InputTextParserHandler extends DefaultHandler {
        Stack<String> nodesStack = new Stack<>();
        Stack<Attributes> attributesStack = new Stack<>();
        Stack<Integer> segmentStartsStack = new Stack<>();

        StringBuilder sb;
        private IndexWriter tokensWriter;
        private IndexWriter sentencesWriter;
        private IndexWriter annotationsWriter;
        private String sent;
        int wordsInSentence;
        int totalWords;
        int totalSentences;

        public InputTextParserHandler(IndexWriter tokensWriter, IndexWriter sentencesWriter, IndexWriter annotationsWriter, String sent) {
            this.tokensWriter = tokensWriter;
            this.sentencesWriter = sentencesWriter;
            this.annotationsWriter = annotationsWriter;
            this.sent = sent;
        }

        @Override
        public void startElement(String u, String l, String qName, Attributes attributes) throws SAXException {
            if (qName.equals("GGS:SpanAnnotation") || qName.equals("GGS:Annotation")) {
                Document doc = new Document();
                for (int i = 0; i < attributes.getLength(); i++) {
                    doc.add(new StringField(attributes.getLocalName(i), attributes.getValue(i), Field.Store.YES));
                }
                try {
                    annotationsWriter.addDocument(doc);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (qName.equals(sent) || nodesStack.size() > 0) {
                nodesStack.push(qName);
                segmentStartsStack.push(totalWords);
                if (attributes != null && attributes.getLength() > 0) {
                    attributesStack.push(new AttributesImpl(attributes));
                } else {
                    attributesStack.push(new AttributesImpl());
                }
                sb = new StringBuilder();
            }
        }

        @Override
        public void endElement(String u, String l, String qName) throws SAXException {
            if (nodesStack.isEmpty())
                return;
            nodesStack.pop();
            Attributes attributes = attributesStack.pop();

            int segmentStart = segmentStartsStack.pop();
            org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
            boolean isLeaf = segmentStart == totalWords;
            boolean isSentence = qName.equals(sent);
            boolean isAnnotation = !isLeaf && !isSentence;

            for (int i = 0; i < attributes.getLength(); i++) {
                doc.add(new StringField(attributes.getLocalName(i), attributes.getValue(i), Field.Store.YES));
            }

            if (isLeaf) {//this is token
                doc.add(new StringField("WORD", sb.toString(), Field.Store.YES));
                doc.add(new IntField("GGS:Sentence", totalSentences, Field.Store.YES));
                totalWords++;
                wordsInSentence++;
                try {
                    tokensWriter.addDocument(doc);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (isSentence) {
                doc.add(new IntField("GGS:EndTokenIndex", totalWords, Field.Store.YES));
                doc.add(new IntField("GGS:StartTokenIndex", segmentStart, Field.Store.YES));
                wordsInSentence = 0;
                totalSentences++;
                try {
                    sentencesWriter.addDocument(doc);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (isAnnotation) {
                doc.add(new IntField("GGS:EndTokenIndex", totalWords - 1, Field.Store.YES));
                doc.add(new IntField("GGS:StartTokenIndex", segmentStart, Field.Store.YES));
                doc.add(new StringField("GGS:Name", qName, Field.Store.YES));
                try {
                    annotationsWriter.addDocument(doc);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            sb = null;
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (sb != null)
                sb.append(ch, start, length);
        }
    }
}
