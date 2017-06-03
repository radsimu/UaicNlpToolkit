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

package ro.uaic.info.nlptools.ggs.engine;

import ro.uaic.info.nlptools.corpus.InmemoryCorpus;
import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.corpus.Utils;
import ro.uaic.info.nlptools.corpus.XMLFormatConfig;
import ro.uaic.info.nlptools.ggs.engine.core.Match;
import ro.uaic.info.nlptools.ggs.engine.core.CompiledGrammar;
import ro.uaic.info.nlptools.ggs.engine.grammar.Grammar;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class GGSengine {
    public static void main(String[] args) throws Exception {//aguments in this order: input file or folder, grammar, output folder, sentence tag
        String inputsPath = args[0];
        String outputsPath = args[2];
        String grammarPath = args[1];
        String sentenceTag = args[3];

        File outputFolder = new File(outputsPath);
        if (!outputFolder.exists()) {
            System.out.println("Output folder doesn't exist!!!");
            return;
        } else if (outputFolder.isFile()) {
            System.out.println("The output path must be a folder, not a file!!!");
            return;
        }

        File inputsFile = new File(inputsPath);
        File[] files = new File[0];
        if (!inputsFile.exists()) {
            System.out.println("Invalid input path!!!");
            return;
        } else if (inputsFile.isDirectory()) {
            files = inputsFile.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isFile();
                }
            });

            if (files.length == 0) {
                System.out.println("Input folder is empty!!!");
                return;
            }
        } else if (inputsFile.isFile()) {
            files = new File[1];
            files[0] = inputsFile;
        }

        Grammar g = new Grammar(new FileInputStream(grammarPath));

        CompiledGrammar cg = new CompiledGrammar(g);
        for (File f : files) {
            System.out.println(f.getPath());
            XMLFormatConfig xmlConfig = new XMLFormatConfig();
            xmlConfig.sentenceNodeName = sentenceTag;

            InmemoryCorpus t = new InmemoryCorpus(new FileInputStream(f), xmlConfig);
            List<Match> matches = cg.GetMatches(t);
            List<SpanAnnotation> annotations = new ArrayList<>();
            for (Match match : matches){
                if (match.getSpanAnnotations() != null)
                annotations.addAll(match.getSpanAnnotations());
            }

            t.mergeSpanAnnotations(annotations);
            Document doc = t.convertToDOM();

            try {
                Utils.writeDocument(doc, outputFolder.getAbsolutePath() + "/" + f.getName());
            } catch (Exception e) {
                System.out.println(e.getMessage() + "!!!");
            }
        }
    }
}
