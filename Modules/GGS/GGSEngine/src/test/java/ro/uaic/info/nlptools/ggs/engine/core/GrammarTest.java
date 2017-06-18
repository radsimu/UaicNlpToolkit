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

package ro.uaic.info.nlptools.ggs.engine.core;

import org.testng.SkipException;
import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.IndexedLuceneCorpus;
import ro.uaic.info.nlptools.corpus.Utils;
import ro.uaic.info.nlptools.ggs.engine.SparseBitSet;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GrammarTest {

//    @Test
//    void testDoubleNestedLookbehindAssertion() throws Exception{
//        assert false;
//    }

    @Test
    void testConsumeInputAnnotationsBackwards() throws Exception{
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));

        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/npconsumerbackwardTest.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches1 = compiledGrammar.GetMatches(t, false);
        List<Match> matches2 = compiledGrammar.GetMatches(t);

        assert (matches1.size() == matches2.size());
        assert (matches1.size() == 8);
    }

    @Test
    void testConsumeInputAnnotations1() throws Exception{
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));

        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/npconsumertest.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches1 = compiledGrammar.GetMatches(t, false);
        List<Match> matches2 = compiledGrammar.GetMatches(t);

        assert (matches1.size() == matches2.size());
        assert (matches1.size() == 7);
    }

    @Test
    public void testNestedAssertions_1() throws  Exception{
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));

        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/nestedAssertions.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches1 = compiledGrammar.GetMatches(t);
        List<Match> matches2 = compiledGrammar.GetMatches(t,false);

        compiledGrammar.SetMainGraph(g.getGraphs().get("main1"));
        List<Match> matches3 = compiledGrammar.GetMatches(t);
        List<Match> matches4 = compiledGrammar.GetMatches(t,false);

        assert (matches1.size() == matches2.size());
        assert (matches2.size() == matches3.size());
        assert (matches3.size() == matches4.size());
    }

    @Test
    public void testVerySimpleGrammar_forIndexedSearch() throws Exception {
        SearchSpaceReducer.searchCache.clear();
        //InmemoryCorpus t = new InmemoryCorpus("TestData/inputCorpora/1984_bigger.xml");
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/1984_readyForChunking.xml"));

        Grammar g = new Grammar();
        //g.load(new FileInputStream("TestData/inputGrammars/npexample.ggf"));
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        //g.load(new FileInputStream("TestData/inputGrammars/verysimplegrammar.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        long time;

        time = System.currentTimeMillis();
        List<Match> matches1 = compiledGrammar.GetMatches(t,false);
        time = System.currentTimeMillis() - time;
        System.out.println("time for iterrative search:\n" + time);

//        SparseBitSet.uncompressed = false;
//        time = System.currentTimeMillis();
//        List<Match> matches2 = compiledGrammar.GetMatches(t,true);
//        time = System.currentTimeMillis() - time;
//        System.out.println("time for indexed search: \n" + time);
//        //assert (matches1.size() == matches2.size());


        SparseBitSet.compressionPolicy = SparseBitSet.SparseBitSetCompressionPolicy.none;
        SearchSpaceReducer.searchCache.clear();
        time = System.currentTimeMillis();
        List<Match> matches3 = compiledGrammar.GetMatches(t,true);
        time = System.currentTimeMillis() - time;
        System.out.println("time for indexed search uncompressed bitsets: \n" + time);

        assert (matches3.size() == matches1.size());

        System.out.println("\n\ntotal time for regex query:\n" + SearchSpaceReducer.regexQueryTime + " - " + SearchSpaceReducer.regexQueryCount);
        System.out.println("total time for term query:\n" + SearchSpaceReducer.termQueryTime + " - " + SearchSpaceReducer.termQueryCount);
    }

    @Test
    public void testLoad() throws Exception {
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        assert (g.getGraphs().size() == 122);
        assert (g.getGraphs().get("Main").getGraphNodes().size() == 4);
        assert (g.getGraphs().get("Main").getGraphNodes().get(0).getChildNodesIndexes().size() == 2);
        assert (g.getGraphs().get("Main").getGraphNodes().get(8).getChildNodesIndexes().size() == 1);
    }

    @Test
    public void testCompile() throws IOException, SAXException, ParserConfigurationException, GGSException {
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        new CompiledGrammar(g);
    }

    @Test
    public void testLoadInput() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        assert (t.getSentenceCount() == 1950);
        assert (t.getSentence(0).getTokenCount() == 64);
    }

    @Test
    public void testLoadInputWithAnnotations() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithAnnotations.xml"));
        assert (t.getSentence(0).getSpanAnnotations().size() == 12);
        assert (t.getSentence(0).getToken(5).getParentSpanAnnotations().size() == 3);
        assert (t.getSentence(0).getToken(5).getParentSpanAnnotations().get(0).getName().equals("NP") );
        assert (t.getSentence(0).getToken(5).getParentSpanAnnotations().get(1).getName().equals("NP") );
        assert (t.getSentence(0).getToken(5).getParentSpanAnnotations().get(2).getName().equals("HEAD") );

        assert (t.getSentence(0).getSpanAnnotations().get(0).getName().equals("NP"));
        assert (t.getSentence(0).getSpanAnnotations().get(1).getName().equals("HEAD"));
        assert (t.getSentence(0).getSpanAnnotations().get(2).getName().equals("NP"));
        assert (t.getSentence(0).getSpanAnnotations().get(3).getName().equals("HEAD"));
    }

    @Test
    public void testInfiniteLoop1() throws IOException, SAXException, ParserConfigurationException, GGSException {
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/infiniteLoop1.ggf"));
        boolean caught = false;

        try {
            new CompiledGrammar(g);
        } catch (GGSInfiniteLoopException ex) {
            caught = true;
        }
        assert (caught);
    }

    @Test
    public void testNPchunkerGrammarMatch1_VerySimpleMatch() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        Match match = compiledGrammar.GetMatch(t.getSentence(0).getToken(1));
        assert match != null;
        assert match.size() == 7;
        System.out.println(match.toString());
    }

    @Test
    public void testNPchunkerGrammarMatch1_SimpleMatch() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        Match match = compiledGrammar.GetMatch(t.getSentence(0).getToken(38));//"ușile de sticlă ale Blocului Victoria"
        System.out.println(match.toString());
        assert (match.size() == 6);
    }

    @Test
    public void testNPchunkerGrammarMatch1_FirstSentence() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches = compiledGrammar.GetMatches(t.getSentence(0));
        assert (matches.size() == 10);

        for (Match match : matches) {
            System.out.println(match.toString());
        }
    }

    @Test
    public void testNPchunkerGrammarMatch1_PronounMatch() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        Match match = compiledGrammar.GetMatch(t.getSentence(6).getToken(1));//"Apartamentul lui se găsea la etajul șapte ..."
        assert (match != null);
        System.out.println(match.toString());
    }

    @Test
    public void testNPchunkerGrammarMatch2_ExtremeCase() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        Match match = compiledGrammar.GetMatch(t.getSentence(38).getToken(4));//"Oare întotdeauna au existat străzile astea cu case din secolul nouăsprezece..."
        System.out.println(match.toString());

        t.mergeSpanAnnotations(match.getSpanAnnotations());
        Document doc = t.convertToDOM();

        Utils.writeDocument(doc, "TestData/generated/savedMatches/saveTextWithMatches_Extreme.xml");
    }

    @Test
    public void testMatchSave_1() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches = compiledGrammar.GetMatches(t.getSentence(0));
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches)
            annotations.addAll(m.getSpanAnnotations());

        t.mergeSpanAnnotations(annotations);
        Document doc = t.convertToDOM();

        Utils.writeDocument(doc, "TestData/generated/savedMatches/saveTextWithMatches1.xml");
    }

    @Test
    public void testMatchOffsetSave() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithAnnotations.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/simpleWithRegex.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches = compiledGrammar.GetMatches(t);
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches)
            annotations.addAll(m.getSpanAnnotations());
        t.mergeSpanAnnotations(annotations);
        Document doc = t.convertToDOM();

        Utils.writeDocument(doc, "TestData/generated/savedMatches/saveTextWithOffsetMatches.xml");
        assert(doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 4);
    }

    @Test
    public void testMatchInlineAndOffsetSave1() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        Document doc = t.convertToDOM();

        Utils.writeDocument(doc, "TestData/generated/savedMatches/saveTextWithMixedMatches.xml");
        assert(doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 3);
    }

    @Test
    public void testMatchInlineAndOffsetSave2() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/simpleWithRegex.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches = compiledGrammar.GetMatches(t);
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches)
            annotations.addAll(m.getSpanAnnotations());
        t.mergeSpanAnnotations(annotations);
        Document doc = t.convertToDOM();

        Utils.writeDocument(doc, "TestData/generated/savedMatches/saveTextWithMixedMatches.xml");
        assert(doc.getElementsByTagName("GGS:SpanAnnotation").getLength() == 7);
    }

    @Test
    public void testNPchunkerGrammarMatch3_NpChunkInputText() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/input.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches1 = compiledGrammar.GetMatches(t);
        List<Match> matches2 = compiledGrammar.GetMatches(t, false);
        assert matches1.size() == matches2.size();
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches1)
            annotations.addAll(m.getSpanAnnotations());
        InmemoryCorpus merged1 = t.clone();
        merged1.mergeSpanAnnotations(annotations);
        Document doc1 = merged1.convertToDOM();

        annotations.clear();
        for (Match m : matches2)
            annotations.addAll(m.getSpanAnnotations());
        InmemoryCorpus merged2 = t.clone();
        merged2.mergeSpanAnnotations(annotations);
        Document doc2 = merged2.convertToDOM();

        assert doc1.getElementsByTagName("NP").getLength() == doc2.getElementsByTagName("NP").getLength();
    }

    @Test
    public void testMatchSave_1984sample_readyForChunking() throws Exception {
        //spre deosebire de testul de mai sus, aici intrarea are un format mai alambicat
        //se doreste doar adaugarea de informatii pe structura documentului initial
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/1984sample_readyForChunking.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches;
        matches = compiledGrammar.GetMatches(t);
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches)
            annotations.addAll(m.getSpanAnnotations());
        t.mergeSpanAnnotations(annotations);
        Document doc = t.convertToDOM();
        Utils.writeDocument(doc, "TestData/generated/savedMatches/1984_readyForChunking_NpChunked.xml");
    }

    @Test
    public void testMatchSave_ChunkMilescu() throws Exception {
        //spre deosebire de testul de mai sus, aici intrarea are un format mai alambicat
        //se doreste doar adaugarea de informatii pe structura documentului initial
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/spataru_jurnal.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches;

        matches = compiledGrammar.GetMatches(t);
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches)
            annotations.addAll(m.getSpanAnnotations());
        t.mergeSpanAnnotations(annotations);
        Document doc = t.convertToDOM();
        Utils.writeDocument(doc, "TestData/generated/savedMatches/spataru_jurnal_chunked.xml");
    }


    @Test
    public void testMatchSave_matchPronouns() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/testPronouns.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches = compiledGrammar.GetMatches(t);
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches)
            annotations.addAll(m.getSpanAnnotations());
        t.mergeSpanAnnotations(annotations);
        Document doc = t.convertToDOM();

        Utils.writeDocument(doc, "TestData/generated/savedMatches/testPronouns.xml");
        //todo multe teste nu fac assert ci doar testeaza daca nu crapa ceva. de facut teste mai bune
    }

    @Test
    public void testNPchunkerGrammarMatch_usingRegex() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/1984_readyForChunking.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/simpleWithRegex.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches1 = compiledGrammar.GetMatches(t);
        List<Match> matches2 = compiledGrammar.GetMatches(t, false);
        assert (matches1.size() > 0 && matches1.size() == matches2.size());
        System.out.println(matches1.toString());
    }

    @Test
    public void testNPchunkerGrammarMatch_strangeCase() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/strangeCase.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches = compiledGrammar.GetMatches(t.getSentence(260));
        List<SpanAnnotation> annotations = new ArrayList<>();
        for (Match m : matches)
            annotations.addAll(m.getSpanAnnotations());
        t.mergeSpanAnnotations(annotations);
        Document doc = t.convertToDOM();
        Utils.writeDocument(doc, "TestData/generated/savedMatches/saveTextWithMatches_strangeCase.xml");
        assert doc.getElementsByTagName("NP").getLength() == 706;
    }

    @Test
    public void testAssertions_1() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/sampleSent.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/posreduction sample.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches = compiledGrammar.GetMatches(t);
        assert matches.size() == 2;
        assert matches.get(1).getTokens().get(0).getWordForm().equals(".");
        System.out.println(matches.toString());
    }

    @Test
    public void testAssertions_2() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/sampleSent.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/posreduction.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches = compiledGrammar.GetMatches(t,false);
        assert matches.size() == 1;
        System.out.println(matches.toString());
    }

    @Test
    public void testJS_1() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/1984sample_readyForChunking.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/test_js.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        List<Match> matches1 = compiledGrammar.GetMatches(t);
        List<Match> matches2 = compiledGrammar.GetMatches(t, false);
        assert matches1.size() == matches2.size();
    }

    @Test
    public void testCrossRefs_1() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/1984Orwel-dep.xml"));
        Grammar g = new Grammar();
        g.load(new FileInputStream("TestData/inputGrammars/test_crossRefs.ggf"));

        CompiledGrammar compiledGrammar = new CompiledGrammar(g);

        //TODO cross ref assertions can be optimised to make use of indexed attributes of the referenced token
        List<Match> matches = compiledGrammar.GetMatches(t, true);
        List<Match> matches1 = compiledGrammar.GetMatches(t, false);
        assert (matches1.size() == matches.size());
    }

    @Test
    public void testIndexedCorola() throws IOException, SAXException, ParserConfigurationException, GGSException {
        IndexedLuceneCorpus t;
        try {
            t = new IndexedLuceneCorpus(new File("TestData/inputCorpora/corola.index"));
        } catch (IOException e) {
            throw new SkipException("indexed corola not present");
        }
        Grammar g = new Grammar();
        //g.load(new FileInputStream("TestData/inputGrammars/roNPchunker_grammar.ggf"));
        //g.load(new FileInputStream("TestData/inputGrammars/npexample.ggf"));
        g.load(new FileInputStream("TestData/inputGrammars/verysimplegrammar.ggf"));

        SparseBitSet.compressionPolicy = SparseBitSet.SparseBitSetCompressionPolicy.none;
        CompiledGrammar compiledGrammar = new CompiledGrammar(g);
        List<Match> matches = compiledGrammar.GetMatches(t, true);

        assert (matches.size() == matches.size());
    }
}
