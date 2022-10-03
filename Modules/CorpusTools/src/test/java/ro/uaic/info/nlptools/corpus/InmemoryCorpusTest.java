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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InmemoryCorpusTest {
    @BeforeMethod
    public void setUp() throws Exception {

    }

    @Test
    public void testConvertToDOM_throwsWhenInlineNotPossible() throws Exception {

        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        boolean thrown = false;
        try {
            Document doc = t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.INLINE);
        }catch (IllegalStateException ex){
            thrown = true;
        }
        assert thrown;
    }

    @Test
    public void testConvertToDOM_Preserve() throws Exception {

        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        Document doc = t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.PRESERVE);

        assert doc.getElementsByTagName("NP").getLength() == 88;
        assert doc.getElementsByTagName("HEAD").getLength() == 88;
        assert doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 3;
    }

    @Test
    public void testConvertToDOM_PreserveWorksAfterMerge() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        SpanAnnotation a = new SpanAnnotation();
        a.setSentence(t.getSentence(0));
        a.setStartTokenIndex(9);
        a.setName("test");
        a.setEndTokenIndex(10);
        a.setPreferInlineRepresentation(true);
        List<SpanAnnotation> annotations = new ArrayList<>();
        annotations.add(a);
        t.mergeSpanAnnotations(annotations);

        Document doc = t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.PRESERVE);
        assert doc.getElementsByTagName("test").getLength() == 1;
    }

    @Test
    public void testConvertToDOM_throwsWhenPreserveNotPossible() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        SpanAnnotation a = new SpanAnnotation();
        a.setSentence(t.getSentence(0));
        a.setStartTokenIndex(1);
        a.setName("test");
        a.setEndTokenIndex(5);
        a.setPreferInlineRepresentation(true);
        List<SpanAnnotation> annotations = new ArrayList<>();
        annotations.add(a);
        t.mergeSpanAnnotations(annotations);

        boolean thrown = false;
        try {
            t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.PRESERVE);
        }catch (IllegalStateException ex){
            thrown = true;
        }
        assert thrown;
    }

    @Test
    public void testConvertToDOM_StandoffWorksAfterMerge() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        Document doc = t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.STANDOFF);
        assert doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 179;

        SpanAnnotation a = new SpanAnnotation();
        a.setSentence(t.getSentence(0));
        a.setStartTokenIndex(1);
        a.setName("test");
        a.setEndTokenIndex(5);
        a.setPreferInlineRepresentation(true);
        List<SpanAnnotation> annotations = new ArrayList<>();
        annotations.add(a);
        t.mergeSpanAnnotations(annotations);

        assert t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.STANDOFF).getElementsByTagName("GGS:SpanAnnotation").getLength() == 180;
    }

    @Test
    public void testConvertToDOM_AutoResultsPreserve() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        SpanAnnotation a = new SpanAnnotation();
        a.setSentence(t.getSentence(0));
        a.setStartTokenIndex(9);
        a.setName("test");
        a.setEndTokenIndex(10);
        a.setPreferInlineRepresentation(true);
        List<SpanAnnotation> annotations = new ArrayList<>();
        annotations.add(a);
        t.mergeSpanAnnotations(annotations);

        Document doc = t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.AUTO);
        assert doc.getElementsByTagName("test").getLength() == 1;
        assert doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 3;
    }

    @Test
    public void testConvertToDOM_AutoResultsInline() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithAnnotations.xml"));
        SpanAnnotation a1 = new SpanAnnotation();
        a1.setSentence(t.getSentence(0));
        a1.setStartTokenIndex(9);
        a1.setEndTokenIndex(10);
        a1.setName("test");
        a1.setPreferInlineRepresentation(true);

        SpanAnnotation a2 = new SpanAnnotation();
        a2.setSentence(t.getSentence(0));
        a2.setStartTokenIndex(7);
        a2.setEndTokenIndex(11);
        a2.setName("test");
        a2.setPreferInlineRepresentation(true);

        List<SpanAnnotation> annotations = new ArrayList<>();
        annotations.add(a1);
        annotations.add(a2);
        t.mergeSpanAnnotations(annotations);

        Document doc = t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.AUTO);
        assert doc.getElementsByTagName("test").getLength() == 2;
        assert doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 0;
    }

    @Test
    public void testConvertToDOM_AutoResultsOptimalInline() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithAnnotations.xml"));
        SpanAnnotation a1 = new SpanAnnotation();
        a1.setSentence(t.getSentence(0));
        a1.setStartTokenIndex(11);
        a1.setEndTokenIndex(13);
        a1.setName("test");
        a1.setPreferInlineRepresentation(true);

        List<SpanAnnotation> annotations = new ArrayList<>();
        annotations.add(a1);
        t.mergeSpanAnnotations(annotations);

        Document doc = t.convertToDOM(InmemoryCorpus.DOMConversionSpanRepresentation.AUTO);
        assert doc.getElementsByTagName("test").getLength() == 0;
        assert doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 1;
    }

    @Test
    public void testMergeSpanAnnotations() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));

        SpanAnnotation a = new SpanAnnotation();
        a.setSentence(t.getSentence(1));
        int beforeSize = t.getSentence(1).getSpanAnnotations().size();
        a.setStartTokenIndex(3);
        a.setEndTokenIndex(6);
        int before2 = t.getSentence(1).getToken(2).getParentSpanAnnotations().size();
        int before3 = t.getSentence(1).getToken(3).getParentSpanAnnotations().size();
        int before4 = t.getSentence(1).getToken(4).getParentSpanAnnotations().size();
        int before5 = t.getSentence(1).getToken(5).getParentSpanAnnotations().size();
        int before6 = t.getSentence(1).getToken(6).getParentSpanAnnotations().size();
        int before7 = t.getSentence(1).getToken(7).getParentSpanAnnotations().size();


        List<SpanAnnotation> annotations = new ArrayList<>();
        annotations.add(a);

        t.mergeSpanAnnotations(annotations);
        assert t.getSentence(1).getSpanAnnotations().size() == beforeSize + 1;

        assert  t.getSentence(1).getToken(2).getParentSpanAnnotations().size() == before2;
        assert  t.getSentence(1).getToken(3).getParentSpanAnnotations().size() == before3 + 1;
        assert  t.getSentence(1).getToken(4).getParentSpanAnnotations().size() == before4 + 1;
        assert  t.getSentence(1).getToken(5).getParentSpanAnnotations().size() == before5 + 1;
        assert  t.getSentence(1).getToken(6).getParentSpanAnnotations().size() == before6 + 1;
        assert  t.getSentence(1).getToken(7).getParentSpanAnnotations().size() == before7;
    }

    @Test
    public void testClone() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        InmemoryCorpus tClone = t.clone();
        IndexedLuceneCorpusTest.assertInputTextsAreEqual(t, tClone);
    }
}