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

package ro.uaic.info.nlptools.ggs.inferrer;

import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import org.testng.annotations.Test;

import java.io.*;

public class InferrerTest {
    private void saveConfig(String configName, String inputFile, String sTagName) throws FileNotFoundException {
        OutputStream os = new FileOutputStream("TestData/generated/inferredGrammars/" + configName);
        PrintWriter pw = new PrintWriter(os);
        String p = "../../inputCorpus/" + inputFile;

        pw.write(p);
        pw.write("\n");
        pw.write(sTagName);
        pw.write("\n");
        pw.write("Main");
        pw.close();
    }

    @Test
    public void inferFromExampleSet_SimpleShallowNps() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/shallowNps.xml"));
        Inferrer inferrer = new Inferrer();
        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/shallowNps.ggf"));
        saveConfig("shallowNps.ggc", "shallowNps.xml", "samp");
    }

    @Test
    public void inferFromExampleSet_SimpleShallowNpsSaveEveryStep() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/shallowNpsVerySmallSet.xml"));
        Inferrer inferrer = new Inferrer();
        for (int i = 0; i < 20; i++) {
            Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, i);
            grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/shallowNpsVerySmallSet_" + i + ".ggf"));
            //saveConfig("simpleDeepNps_" + i + ".ggc", "simpleDeepNps.xml", "samp");
        }

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -1);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/shallowNpsVerySmallSet_converged.ggf"));
    }

    @Test
    public void inferFromExampleSet_SimpleDeepNps() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/simpleDeepNps.xml"));
        Inferrer inferrer = new Inferrer();
        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/simpleDeepNps.ggf"));
        //saveConfig("simpleDeepNps.ggc", "simpleDeepNps.xml", "samp");
    }

    @Test
    public void inferFromExampleSet_SimpleDeepNpsSaveEveryStep() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/simpleDeepNps.xml"));
        Inferrer inferrer = new Inferrer();

        //save first 20 itterations for observation purposes
        for (int i = 0; i < 20; i++) {
            Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, i);
            grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/simpleDeepNps_" + i + ".ggf"));
            //saveConfig("simpleDeepNps_" + i + ".ggc", "simpleDeepNps.xml", "samp");
        }

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -1);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/simpleDeepNps_converged.ggf"));
    }

    @Test
    public void inferFromExampleSet_SimpleDeepNpsSmall() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/simpleDeepNps_small.xml"));
        Inferrer inferrer = new Inferrer();

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -1);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/simpleDeepNps_small.ggf"));
    }

    @Test
    public void inferFromExampleSet_SimpleDeepNpsSmallSaveEveryStep() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/simpleDeepNps_small.xml"));
        Inferrer inferrer = new Inferrer();

        //save first 20 itterations for observation purposes
        for (int i = 0; i < 20; i++) {
            Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, i);
            grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/simpleDeepNps_small_" + i + ".ggf"));
            //saveConfig("simpleDeepNps_" + i + ".ggc", "simpleDeepNps.xml", "samp");
        }

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -2);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/simpleDeepNps_small_converged.ggf"));
    }

    @Test
    public void inferFromExampleSet_DeepNps() throws Exception {//spre deosebire de celelalte aici in np-uri exista np-uri precum "old rag mats" (secventa de subst alaturate)
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/deepNps.xml"));
        Inferrer inferrer = new Inferrer();

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -1);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/deepNps.ggf"));
    }

    @Test
    public void inferFromCorpus_DeepNps_EnigmaGold() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/Enigma-Otiliei-NPchunkedCorrected.xml"));
        Inferrer inferrer = new Inferrer();

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -1);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/EnigmaGold.ggf"));
    }

    @Test
    public void inferFromCorpus_DeepNps_EnigmaGold_SaveEveryStep() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/Enigma-Otiliei-NPchunkedCorrected.xml"));
        Inferrer inferrer = new Inferrer();

        //save first 20 itterations for observation purposes
        for (int i = 0; i < 20; i++) {
            Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, i);
            grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/EnigmaGold_" + i + ".ggf"));
            //saveConfig("simpleDeepNps_" + i + ".ggc", "simpleDeepNps.xml", "samp");
        }

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -1);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/EnigmaGold_converged.ggf"));
    }

    @Test
    public void inferFromCorpus_DeepNps_Semeval() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/semeval-test.xml"));
        Inferrer inferrer = new Inferrer();

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, -1);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/semeval.ggf"));
    }

    @Test
    public void inferFromCorpus_DeepNps_Semeval_SaveEveryStep() throws Exception {
        InmemoryCorpus t = new InmemoryCorpus(new FileInputStream("TestData/inputCorpora/semeval-test.xml"));
        Inferrer inferrer = new Inferrer();

        //save first 20 itterations for observation purposes
        for (int i = 0; i < 20; i++) {
            Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, i);
            grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/Semeval_" + i + ".ggf"));
            //saveConfig("simpleDeepNps_" + i + ".ggc", "simpleDeepNps.xml", "samp");
        }

        Grammar grammar = inferrer.inferFromAnnotatedCorpus(t, 0);
        grammar.save(new FileOutputStream("TestData/generated/inferredGrammars/Semeval_converged.ggf"));
    }
}
