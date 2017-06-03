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

import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

public class XMLFormatConfig implements ICorpusFormatConfig {
    public String sentenceNodeName;
    public String posAttributeName;
    public String lemmaAttributeName;
    public String dependencyHeadAttributeName;
    public String dependencyLabelAttributeName;
    public String wordFormAttributeName;

    public static XMLFormatConfig UaicCommonFormat;
    public static XMLFormatConfig RoDepTbFormat;
    static {
        UaicCommonFormat = new XMLFormatConfig();
        UaicCommonFormat.sentenceNodeName="S";
        UaicCommonFormat.dependencyHeadAttributeName="head";
        UaicCommonFormat.dependencyLabelAttributeName="deprel";
        UaicCommonFormat.posAttributeName="MSD";
        UaicCommonFormat.lemmaAttributeName="LEMMA";
        UaicCommonFormat.wordFormAttributeName="";

        RoDepTbFormat = new XMLFormatConfig();
        RoDepTbFormat.sentenceNodeName="sentence";
        RoDepTbFormat.dependencyHeadAttributeName="head";
        RoDepTbFormat.dependencyLabelAttributeName="deprel";
        RoDepTbFormat.posAttributeName="postag";
        RoDepTbFormat.lemmaAttributeName="lemma";
        RoDepTbFormat.wordFormAttributeName="form";
    }
}
