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

package ro.uaic.info.nlptools.postagger.evaluator;

import ro.uaic.info.nlptools.postagger.UaicHybridPOStagger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class EvalEngine {

    public static SimpleEvalResult simpleEval(UaicHybridPOStagger tagger, String testCorpus) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(testCorpus), "UTF8"));
        long totalWords = 0;
        long correctOutputs = 0;
        long totalUnambiguousWords = 0;
        long totalGuesses = 0;
        long correctGuesses = 0;
        String line;
        long start = System.currentTimeMillis();
        long sentCount = 0;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] toks = line.split("\\s");
            String[] words = new String[toks.length];
            String[] expMSDs = new String[toks.length];
            for (int i = 0; i < toks.length; i++) {
                String[] subsplit = toks[i].split("_");
                words[i] = subsplit[0];
                expMSDs[i] = subsplit[1];
            }

            String[] outMSDs = tagger.tag(words);

            for (int i = 0; i < outMSDs.length; i++) {
                totalWords++;
                Set poss = tagger.uaicMorphologicalDictionary.get(words[i]);
                if (poss != null && poss.size() == 1) {
                    totalUnambiguousWords++;
                }
                if (poss == null) {
                    totalGuesses++;
                }

                if (outMSDs[i].equals(expMSDs[i]) || (expMSDs[i].startsWith("Np") && outMSDs[i].startsWith("Np"))) {
                    correctOutputs++;
                    if (poss == null) {
                        correctGuesses++;
                    }
                }
            }
            sentCount++;
        }
        SimpleEvalResult rez = new SimpleEvalResult();
        long dif = System.currentTimeMillis() - start;
        rez.sentsPerSecond = 1000 * (float) sentCount / dif;
        rez.wordsPerSecond = 1000 * (float) totalWords / dif;
        rez.correctOutputsCount = correctOutputs;
        rez.totalWordsCount = totalWords;
        rez.totalUnambiguousWords = totalUnambiguousWords;
        rez.correctGuessesCount = correctGuesses;
        rez.totalGuessesCount = totalGuesses;
        return rez;
    }

    public static EvalResult fullEval(UaicHybridPOStagger tagger, List<String> lines) throws UnsupportedEncodingException, FileNotFoundException, IOException {
        EvalResult r = new EvalResult();

        long totalWords = 0;
        long correctOutputs = 0;
        long totalUnambiguousWords = 0;
        HashMap<String, Integer> countExpMSDtotal = new HashMap<String, Integer>(); //total count of each expected MSD
        HashMap<String, Integer> countOutMSDtotal = new HashMap<String, Integer>(); //total count of each output MSD  
        HashMap<String, Integer> countWordstotal = new HashMap<String, Integer>(); //total count of each output MSD  
        List<FailCase> allFailCases = new ArrayList<FailCase>();

        int sentIndex = 0;
        for (int k = 0; k < lines.size(); k++) {
            String line = lines.get(k);
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] toks = line.split("\\s");
            String[] words = new String[toks.length];
            String[] expMSDs = new String[toks.length];
            for (int i = 0; i < toks.length; i++) {
                String[] subsplit = toks[i].split("_");
                words[i] = subsplit[0];
                expMSDs[i] = subsplit[1];
            }

            String[] outMSDs = tagger.tag(words);

            int start = 0;
            for (int i = 0; i < outMSDs.length; i++) {
                Set poss = tagger.uaicMorphologicalDictionary.get(words[i]);
                if (poss != null && poss.size() == 1) {
                    totalUnambiguousWords++;
                }
                totalWords++;
                if (outMSDs[i].equals(expMSDs[i]) || (expMSDs[i].startsWith("Np") && outMSDs[i].startsWith("Np"))) {
                    correctOutputs++;
                } else {
                    // pt generat statistici
                    FailCase sample = new FailCase();
                    sample.start = start;
                    sample.word = words[i].toLowerCase();
                    sample.length = words[i].length();
                    sample.sentIndex = sentIndex;
                    sample.expectedTag = expMSDs[i];
                    sample.outputTag = outMSDs[i];
                    allFailCases.add(sample);
                }
                Integer d;
                d = countExpMSDtotal.get(expMSDs[i]);
                if (d == null) {
                    d = 0;
                }
                countExpMSDtotal.put(expMSDs[i], d + 1);

                d = countOutMSDtotal.get(outMSDs[i]);
                if (d == null) {
                    d = 0;
                }
                countOutMSDtotal.put(outMSDs[i], d + 1);

                String canW = words[i].toLowerCase();
                d = countWordstotal.get(canW);
                if (d == null) {
                    d = 0;
                }
                countWordstotal.put(canW, d + 1);

                start += words[i].length() + 1;
            }
            sentIndex++;
        }

        r.allFailCases = allFailCases;
        r.countExpectedTagsTotal = countExpMSDtotal;
        r.countOutputTagsTotal = countOutMSDtotal;
        r.countWordsTotal = countWordstotal;
        r.precission = (double) correctOutputs / totalWords;
        r.correctOutputsCount = correctOutputs;
        r.totalWordsCount = totalWords;
        r.totalUnambiguousWords = totalUnambiguousWords;
        return r;
    }
}
