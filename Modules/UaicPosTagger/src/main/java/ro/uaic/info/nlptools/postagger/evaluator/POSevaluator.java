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
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

import java.io.FileInputStream;

public class POSevaluator {

    public static void main(String[] args) throws Exception {//model dicfile guesserTagsetFile rules testSamples
        FileInputStream grammar = null;
        try{
            grammar = new FileInputStream(args[3]);
        }catch(Exception ex){}

        UaicMorphologicalDictionary dic = new UaicMorphologicalDictionary();
        dic.load(new FileInputStream(args[1]));
        UaicHybridPOStagger tagger = new UaicHybridPOStagger(new FileInputStream(args[0]), dic, new FileInputStream(args[2]), grammar);
        System.out.println(EvalEngine.simpleEval(tagger, args[4]));
    }
}
