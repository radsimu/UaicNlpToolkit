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

import java.util.ArrayList;
import java.util.List;

public class ConllFormatConfig implements ICorpusFormatConfig {
    public List<String> columnFeatures = new ArrayList<>();
    public int posColumnIndex;
    public int lemmaColumnIndex;
    public int dependencyHeadColumnIndex;
    public int dependencyLabelColumnIndex;
    public int wordFormColumnIndex;
    public int cposColumnIndex;

    public static ConllFormatConfig conlluFormat;

    static{
        conlluFormat = new ConllFormatConfig();
        conlluFormat.columnFeatures.add("ID");
        conlluFormat.columnFeatures.add("FORM");
        conlluFormat.columnFeatures.add("LEMMA");
        conlluFormat.columnFeatures.add("UPOSTAG");
        conlluFormat.columnFeatures.add("XPOSTAG");
        conlluFormat.columnFeatures.add("FEATS");
        conlluFormat.columnFeatures.add("HEAD");
        conlluFormat.columnFeatures.add("DEPREL");
        conlluFormat.columnFeatures.add("DEPS");
        conlluFormat.columnFeatures.add("MISC");
        conlluFormat.posColumnIndex = 3;
        conlluFormat.cposColumnIndex = 4;
        conlluFormat.lemmaColumnIndex = 2;
        conlluFormat.dependencyHeadColumnIndex = 6;
        conlluFormat.dependencyLabelColumnIndex = 7;
        conlluFormat.wordFormColumnIndex = 1;
    }
}
