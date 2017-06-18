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

package ro.uaic.info.nlptools.apiTests;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.corpus.InmemorySentence;
import ro.uaic.info.nlptools.corpus.Token;
import ro.uaic.info.nlptools.corpus.XMLFormatConfig;
import ro.uaic.info.nlptools.tools.UaicTokenizer;
import ro.uaic.info.nlptools.postagger.UaicHybridPOStagger;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;

import static org.testng.Assert.*;

public class UserStoriesTest {
    UaicMorphologicalDictionary morphologicalDictionary;
    UaicHybridPOStagger tagger;

    @BeforeClass
    public void setUp() throws Exception {
        morphologicalDictionary = new UaicMorphologicalDictionary();
        morphologicalDictionary.load(new FileInputStream("TestData/posResources/posDictRoDiacr.txt"));
        tagger = new UaicHybridPOStagger(new FileInputStream("TestData/posResources/posRoDiacr.model"), morphologicalDictionary, new FileInputStream("TestData/posResources/guesserTagset.txt"), new FileInputStream("TestData/posResources/posreduction.ggf"));
    }

    @Test
    public void testLoadCorpusFormatAuto_XML() throws Exception {
        //test from single xml file
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"));
        assertTrue(t.getLoadingCorpusFormatConfiguration() instanceof XMLFormatConfig);
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).lemmaAttributeName, "lemma");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).posAttributeName, "MSD");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).sentenceNodeName, "s");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).wordFormAttributeName, "");
        assertNull(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).dependencyHeadAttributeName);
        assertNull(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).dependencyLabelAttributeName);

        assertEquals(t.getSentence(1).getToken(2).getParentSpanAnnotations().size(), 2);
        assertEquals(t.getSentence(1).getTokenCount(), 64);
        assertEquals(t.getSentence(2).getSpanAnnotations().get(4).getName(), "NP");
        assertEquals(t.getSentence(2).getSpanAnnotations().get(4).getEndTokenIndex() - t.getSentence(2).getSpanAnnotations().get(5).getStartTokenIndex(), 4);
    }

    @Test
    public void testLoadCorpusFormatAuto_RoDepTbXML() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/14_citate4-roDepTbFormat.xml"));
        assertTrue(t.getLoadingCorpusFormatConfiguration() instanceof XMLFormatConfig);
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).lemmaAttributeName, "lemma");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).posAttributeName, "postag");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).sentenceNodeName, "sentence");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).wordFormAttributeName, "form");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).dependencyHeadAttributeName, "head");
        assertEquals(((XMLFormatConfig) t.getLoadingCorpusFormatConfiguration()).dependencyLabelAttributeName, "deprel");
    }

    @Test
    public void testLoadCorpusFormatAuto_conll() throws Exception {
        //test from single conll file
        //validate guessing report
        throw new NotImplementedException();
    }

    @Test
    public void testLoadCorpusFormatAuto_XML_folder() throws Exception {
        //test from multiple xml files in folder
        //validate guessing report
        throw new NotImplementedException();
    }

    @Test
    public void testLoadCorpusFormatAuto_conll_folder() throws Exception {
        //test from multiple conll files in folder
        //validate guessing report
        throw new NotImplementedException();
    }

    @Test
    public void testLoadCorpusFormatAuto_RoDepTbXML_folder() throws Exception {
        //test from multiple treebank files in folder
        //validate guessing report
        throw new NotImplementedException();
    }

    @Test
    public void testLoadCorpusFormatAuto_mixed_folder() throws Exception {
        //test from multiple mixed files in folder
        //validate guessing report
        throw new NotImplementedException();
    }

    @Test
    public void testLoadCorpusFormatAuto_indexed() throws Exception {
        //test from indexed format in folder
        //validate guessing report
        throw new NotImplementedException();
    }

    @Test
    public void testLoadCorpusFormatAuto_mixed_list() throws Exception {
        //test from multiple files provided as list of files
        //validate guessing report
        throw new NotImplementedException();
    }

    @Test
    public void testLoadCorpusFromXmlWithFormatSpec() throws Exception {
        XMLFormatConfig xmlConfig = new XMLFormatConfig();
        xmlConfig.lemmaAttributeName = "lemma";
        xmlConfig.posAttributeName = "MSD";
        xmlConfig.sentenceNodeName = "s";
        xmlConfig.wordFormAttributeName = "";

        InmemoryCorpus t = new InmemoryCorpus(new File("TestData/inputCorpora/inputWithMixedAnnotations.xml"), xmlConfig);

        assertEquals(t.getSentence(1).getToken(2).getParentSpanAnnotations().size(), 2);
        assertEquals(t.getSentence(1).getTokenCount(), 64);
        assertEquals(t.getSentence(2).getSpanAnnotations().get(5).getName(), "NP");
        assertEquals(t.getSentence(2).getSpanAnnotations().get(5).getEndTokenIndex() - t.getSentence(2).getSpanAnnotations().get(5).getStartTokenIndex(), 4);
    }

    @Test
    public void testLoadCorpusFromConllWithLoadSpec() throws Exception {
        throw new NotImplementedException();
    }

    @Test
    public void testCreateCorpusFromPlainText() throws IOException, IllegalAccessException, InstantiationException {
        //split and tokenize
        UaicTokenizer tokenizer = new UaicTokenizer(morphologicalDictionary);
        InmemoryCorpus rez = tokenizer.splitAndTokenize(new BufferedReader(new FileReader("TestData/inputCorpora/plain.txt")).readLine());
        assertEquals(rez.getSentenceCount(), 5);
        assertEquals(rez.getSentence(4).getTokenCount(), 44);
    }

    @Test
    public void testCreateCorpusFromCode() {
        InmemoryCorpus corpus = new InmemoryCorpus();
        InmemorySentence s = new InmemorySentence();
        Token t = new Token();

        //sent 1
        t.setWordForm("Ana");
        t.setPostag("Np");
        t.setLemma("Ana");
        s.addToken(t);
        t = new Token();
        t.setWordForm("are");
        t.setPostag("Vmi");
        t.setLemma("avea");
        s.addToken(t);
        t = new Token();
        t.setWordForm("mere");
        t.setPostag("Ncfprn");
        t.setLemma("măr");
        s.addToken(t);
        corpus.addSentence(s);

        //sent 2
        s = new InmemorySentence();
        t = new Token();
        t.setWordForm("Dorel");
        t.setPostag("Np");
        t.setLemma("Dorel");
        s.addToken(t);
        t = new Token();
        t.setWordForm("are");
        t.setPostag("Vmi");
        t.setLemma("avea");
        s.addToken(t);
        t = new Token();
        t.setWordForm("bere");
        t.setPostag("Ncfprn");
        t.setLemma("bere");
        s.addToken(t);
        t = new Token();
        t.setWordForm(".");
        t.setPostag("PUNCT");
        t.setLemma(".");
        s.addToken(t);

        corpus.addSentence(s);
        assertEquals(corpus.getSentenceCount(), 2);
        assertEquals(corpus.getTokenCount(), 7);
        assertEquals(corpus.getToken(4).getFeatures().size(), 3);
        assertEquals(corpus.getToken(4).getTokenIndexInCorpus(), 4);
        assertEquals(corpus.getToken(4).getTokenIndexInSentence(), 1);
        assertEquals(corpus.getToken(4).getParentSentence().getSentenceIndexInCorpus(), 1);
        assertEquals(corpus.getToken(4).getParentSentence().getTokenCount(), 4);
    }

    @Test
    public void testSaveWithSpec() {
        throw new NotImplementedException();
    }

    @Test
    public void testSaveWithPreserve() {
        throw new NotImplementedException();
    }

    @Test
    public void testSaveWithPreserveWhenMultiple() {
        throw new NotImplementedException();
    }

    @Test
    public void testRemoveSentencesAndSave() {
        throw new NotImplementedException();
    }

    @Test
    public void testConvertIndexedCorpusToInMemory() {
        //extract some indexed sentences as in memory sentences and create an inmemory corpus with them for processing
        throw new NotImplementedException();
    }

    @Test
    public void testCloneCorpus() {
        throw new NotImplementedException();
    }

    @Test
    public void testMorphologicalDictDiacriticsPolicy() {
        morphologicalDictionary.diacriticsPolicy = UaicMorphologicalDictionary.StrippedDiacriticsPolicy.NeverStripped;
        assertNotNull(morphologicalDictionary.get("sfâșiați"));
        assertNull(morphologicalDictionary.get("sfasiati"));

        morphologicalDictionary.diacriticsPolicy = UaicMorphologicalDictionary.StrippedDiacriticsPolicy.Both;
        assertNotNull(morphologicalDictionary.get("sfâșiați"));
        assertNotNull(morphologicalDictionary.get("sfasiati"));

        morphologicalDictionary.diacriticsPolicy = UaicMorphologicalDictionary.StrippedDiacriticsPolicy.OnlyStripped;
        assertNull(morphologicalDictionary.get("sfâșiați"));
        assertNotNull(morphologicalDictionary.get("sfasiati"));

        morphologicalDictionary.diacriticsPolicy = UaicMorphologicalDictionary.StrippedDiacriticsPolicy.NeverStripped;
    }

    @Test
    public void testMorphologicalDictMSDUtilMethods() {
        assertEquals(morphologicalDictionary.correctMSDTag("Ncfsrny---a"), "Ncfsrn");
        assertEquals(UaicMorphologicalDictionary.describeMSD_en("Ncfsrn").get("Gender"), "feminine");
    }

    // Run some basic NLP chains
    @Test
    public void test_Plain_Tokenize_Postag_SaveXML() throws IOException, IllegalAccessException, InstantiationException {
        UaicTokenizer tokenizer = new UaicTokenizer(morphologicalDictionary);
        InmemoryCorpus rez = tokenizer.splitAndTokenize(new BufferedReader(new FileReader("TestData/inputCorpora/plain.txt")).readLine());
        tagger.tag(rez);

    }
}