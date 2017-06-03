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

package ro.uaic.info.nlptools.postagger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

class WordStruct {

    public String word;
    public List<UaicMorphologicalAnnotation> possibleAnnotations;
    public int start = -1;
    public int end;
    public SentenceStruct parentSentence;
    public boolean inDict = false;
    public String id;

    public WordStruct() {
        possibleAnnotations = new ArrayList<>();
    }

    @Override
    public String toString() {
        return word;
    }

    public String toOutputString() {
        String extra = possibleAnnotations.get(0).getExtra();
        return word.replaceAll("\\s+", "~") + "_" + possibleAnnotations.get(0).getLemma() + "_" + possibleAnnotations.get(0).getMsd() + ((extra != null) ? "_" + extra : "");
    }

    public Element toXml(Document doc) {
        Element wordElem = doc.createElement("W");
        wordElem.appendChild(doc.createTextNode(word));

        if (id != null) {
            wordElem.setAttribute("id", id);
        }
        wordElem.setAttribute("LEMMA", possibleAnnotations.get(0).getLemma());
        wordElem.setAttribute("MSD", possibleAnnotations.get(0).getMsd());

        if (possibleAnnotations.get(0).getExtra() != null) {
            wordElem.setAttribute("EXTRA", possibleAnnotations.get(0).getExtra());
        }
        if (start >= 0) {
            wordElem.setAttribute("offset", "" + start);
        }

        return wordElem;
    }

    Element toXmlDetailed_en(Document doc) {
        Element wordElem = toXml(doc);

        Map<String, String> describeMSD = UaicMorphologicalDictionary.describeMSD_en(possibleAnnotations.get(0).getMsd());
        for (String key : describeMSD.keySet()) {
            wordElem.setAttribute(key, describeMSD.get(key));
        }

        return wordElem;
    }

    Element toXmlDetailed_ro(Document doc) {
        Element wordElem = toXml(doc);

        Map<String, String> describeMSD = UaicMorphologicalDictionary.describeMSD_ro(possibleAnnotations.get(0).getMsd());
        for (String key : describeMSD.keySet()) {
            wordElem.setAttribute(key, describeMSD.get(key));
        }

        return wordElem;
    }

    public WordStruct clone() {
        WordStruct ws = new WordStruct();
        ws.end = end;
        ws.id = id;
        ws.inDict = inDict;
        ws.parentSentence = parentSentence;
        ws.possibleAnnotations = possibleAnnotations;
        ws.start = start;
        ws.word = word;
        return ws;
    }
}
