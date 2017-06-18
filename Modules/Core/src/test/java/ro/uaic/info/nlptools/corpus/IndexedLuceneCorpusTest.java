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

import org.testng.SkipException;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static java.util.Map.*;

public class IndexedLuceneCorpusTest {
    public static void assertInputTextsAreEqual(INlpCorpus input1, INlpCorpus input2) {
        assert input1.getSentenceCount() == input2.getSentenceCount();
        assert input1.getTokenCount() == input2.getTokenCount();
        assert input1.getSentence(1).getTokenCount() + input1.getSentence(0).getTokenCount() == input1.getSentence(2).getFirstTokenIndexInCorpus();
        assert input1.getToken(input1.getSentence(1).getTokenCount() + input1.getSentence(0).getTokenCount() + 3) == input1.getSentence(2).getToken(3);

        for (int i = 0; i < input1.getSentenceCount(); i++) {
            assert input1.getSentence(i).getSpanAnnotations().size() == input2.getSentence(i).getSpanAnnotations().size();
            assert input1.getSentence(i).getFirstTokenIndexInCorpus() == input2.getSentence(i).getFirstTokenIndexInCorpus();
            assert input1.getSentence(i).getTokenCount() == input2.getSentence(i).getTokenCount();
            int t1 = 0;
            for (SpanAnnotation a : input1.getSentence(i).getSpanAnnotations())
                t1 += a.getFeatures().size();
            int t2 = 0;
            for (SpanAnnotation a : input2.getSentence(i).getSpanAnnotations())
                t2 += a.getFeatures().size();
            assert t1 == t2;
        }

        for (int i = 0; i < input1.getTokenCount(); i++) {
            assert input1.getToken(i).getParentSpanAnnotations().size() == input2.getToken(i).getParentSpanAnnotations().size();
            assert input1.getToken(i).getParentSentence().getSentenceIndexInCorpus() == input2.getToken(i).getParentSentence().getSentenceIndexInCorpus();
            assert input1.getToken(i).getFeatures().size() == input2.getToken(i).getFeatures().size();
            assert input1.getToken(i).getTokenIndexInSentence() == input2.getToken(i).getTokenIndexInSentence();

            for (Entry<String, String> entry : input1.getToken(i).getFeatures().entrySet()) {
                assert entry.getValue().equals(input2.getToken(i).getFeatures().get(entry.getKey()));
            }
        }
    }

    @Test
    public void testCreateIndexFromInmemoryInput1() throws Exception {
        InmemoryCorpus inmemoryCorpus = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/1984_bigger.xml"));
        IndexedLuceneCorpus indexedLuceneCorpus = IndexedLuceneCorpus.CreateIndexFromInmemoryCorpus(new File("TestData/generated/indexedCorpora/1984_bigger.index"), inmemoryCorpus);
        assertInputTextsAreEqual(inmemoryCorpus, indexedLuceneCorpus);
        IndexedLuceneCorpus reloadedIndexedLuceneCorpus = new IndexedLuceneCorpus(new File("TestData/generated/indexedCorpora/1984_bigger.index"));
        assertInputTextsAreEqual(reloadedIndexedLuceneCorpus, indexedLuceneCorpus);
    }

    @Test
    public void testCreateIndexFromInmemoryInput2() throws Exception {
        InmemoryCorpus inmemoryCorpus = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/inputWithAnnotations.xml"));
        IndexedLuceneCorpus indexedLuceneCorpus = IndexedLuceneCorpus.CreateIndexFromInmemoryCorpus(new File("TestData/generated/indexedCorpora/inputWithAnnotations.index"), inmemoryCorpus);
        assertInputTextsAreEqual(inmemoryCorpus, indexedLuceneCorpus);
        IndexedLuceneCorpus reloadedIndexedLuceneCorpus = new IndexedLuceneCorpus(new File("TestData/generated/indexedCorpora/inputWithAnnotations.index"));
        assertInputTextsAreEqual(reloadedIndexedLuceneCorpus, indexedLuceneCorpus);
    }

    @Test
    public void testCreateIndexFromInmemoryInput3() throws Exception {
        InmemoryCorpus inmemoryCorpus = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        IndexedLuceneCorpus indexedLuceneCorpus = IndexedLuceneCorpus.CreateIndexFromInmemoryCorpus(new File("TestData/generated/indexedCorpora/inputWithMixedAnnotations.index"), inmemoryCorpus);
        assertInputTextsAreEqual(inmemoryCorpus, indexedLuceneCorpus);
        IndexedLuceneCorpus reloadedIndexedLuceneCorpus = new IndexedLuceneCorpus(new File("TestData/generated/indexedCorpora/inputWithMixedAnnotations.index"));
        assertInputTextsAreEqual(reloadedIndexedLuceneCorpus, indexedLuceneCorpus);
    }


    @Test
    public void testCreateIndexFromXmlFiles1() throws Exception {
        IndexedLuceneCorpus indexedLuceneCorpus = IndexedLuceneCorpus.CreateIndexFromXmlFiles(new File("TestData/generated/indexedCorpora//1984_bigger.index"), new File("TestData/inputCorpora/1984_bigger.xml"), "s");
        InmemoryCorpus inmemoryCorpus = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/1984_bigger.xml"));
        assertInputTextsAreEqual(inmemoryCorpus, indexedLuceneCorpus);
        IndexedLuceneCorpus reloadedIndexedLuceneCorpus = new IndexedLuceneCorpus(new File("TestData/generated/indexedCorpora/1984_bigger.index"));
        assertInputTextsAreEqual(reloadedIndexedLuceneCorpus, indexedLuceneCorpus);
    }

    @Test
    public void testCreateIndexFromXmlFiles2() throws Exception {
        IndexedLuceneCorpus indexedLuceneCorpus = IndexedLuceneCorpus.CreateIndexFromXmlFiles(new File("TestData/generated/indexedCorpora/inputWithAnnotations.index"), new File("TestData/inputCorpora/inputWithAnnotations.xml"), "s");
        InmemoryCorpus inmemoryCorpus = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/inputWithAnnotations.xml"));
        assertInputTextsAreEqual(inmemoryCorpus, indexedLuceneCorpus);
        IndexedLuceneCorpus reloadedIndexedLuceneCorpus = new IndexedLuceneCorpus(new File("TestData/generated/indexedCorpora/inputWithAnnotations.index"));
        assertInputTextsAreEqual(reloadedIndexedLuceneCorpus, indexedLuceneCorpus);
    }

    @Test
    public void testCreateIndexFromXmlFiles3() throws Exception {
        IndexedLuceneCorpus indexedLuceneCorpus = IndexedLuceneCorpus.CreateIndexFromXmlFiles(new File("TestData/generated/indexedCorpora/inputWithMixedAnnotations.index"), new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"), "s");
        InmemoryCorpus inmemoryCorpus = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        assertInputTextsAreEqual(inmemoryCorpus, indexedLuceneCorpus);
        IndexedLuceneCorpus reloadedIndexedLuceneCorpus = new IndexedLuceneCorpus(new File("TestData/generated/indexedCorpora/inputWithMixedAnnotations.index"));
        assertInputTextsAreEqual(reloadedIndexedLuceneCorpus, indexedLuceneCorpus);
    }

    @Test
    public void testCreateIndexFromXmlFilesCorola() throws Exception {
        try {
            IndexedLuceneCorpus indexedLuceneCorpus = IndexedLuceneCorpus.CreateIndexFromXmlFiles(new File("TestData/generated/indexedCorpora/corola.index"), new File("TestData/inputCorpora/corola"), "S");
        } catch (IOException e) {
            throw new SkipException("indexed corola not present");
        }
    }
}