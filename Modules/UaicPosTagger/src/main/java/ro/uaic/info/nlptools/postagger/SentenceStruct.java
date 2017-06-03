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
import org.w3c.dom.NodeList;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

public class SentenceStruct extends ArrayList<WordStruct> {

    public String text;
    public String id;
    public int offset = -1;

    public SentenceStruct() {
        super();
    }

    public SentenceStruct(String line) {
        super();
        if (line.startsWith("\uFEFF")) {
            line = line.substring(1);
        }
        line = line.trim();

        String[] words = line.split("\\s");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            WordStruct wordStruct = new WordStruct();
            wordStruct.word = word.trim();
            wordStruct.id = "" + (i + 1);
            add(wordStruct);
        }
    }

    public SentenceStruct(Element S) {
        NodeList Ws = S.getElementsByTagName("W");
        for (int j = 0; j < Ws.getLength(); j++) {
            WordStruct word = new WordStruct();
            word.word = Ws.item(j).getFirstChild().getNodeValue();
            add(word);
        }
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (WordStruct w : this) {
            sb.append("[").append(w.toString()).append("] ");
        }
        return sb.toString();
    }

    public String toOutputString() {
        StringBuilder sb = new StringBuilder();
        for (WordStruct w : this) {
            sb.append(w.toOutputString()).append(" ");
        }
        return sb.toString();
    }

    public List<Map<String, String>> toList() {
        ArrayList<Map<String, String>> ret = new ArrayList<Map<String, String>>();
        for (int i = 0; i < size(); i++) {
            Map<String, String> descr = UaicMorphologicalDictionary.describeMSD_en(get(i).possibleAnnotations.get(0).getMsd());
            descr.put("WORD", get(i).word);
            descr.put("Lemma", get(i).possibleAnnotations.get(0).getLemma());
            descr.put("MSD", get(i).possibleAnnotations.get(0).getMsd());
            String extra = get(i).possibleAnnotations.get(0).getExtra();
            if (extra != null) {
                descr.put("Extra", extra);
            }
            ret.add(descr);
        }
        return ret;
    }

    public Element toXml(Document doc) throws Exception {

        Element sentElem = doc.createElement("S");
        if (id != null) {
            sentElem.setAttribute("id", "" + id);
        }
        if (offset >= 0) {
            sentElem.setAttribute("offset", "" + offset);
        }


        for (int j = 0; j < size(); j++) {
            WordStruct wordStruct = get(j);
            Element wordElem = wordStruct.toXml(doc);
            sentElem.appendChild(wordElem);
        }
        return sentElem;
    }

    public Element toXmlDetailed_en(Document doc) {
        Element sentElem = doc.createElement("S");
        if (id != null) {
            sentElem.setAttribute("id", "" + id);
        }
        if (offset >= 0) {
            sentElem.setAttribute("offset", "" + offset);
        }

        for (int j = 0; j < size(); j++) {
            WordStruct wordStruct = get(j);
            Element wordElem = wordStruct.toXmlDetailed_en(doc);
            sentElem.appendChild(wordElem);
        }

        return sentElem;
    }

    public Element toXmlDetailed_ro(Document doc) {
        Element sentElem = doc.createElement("S");
        if (id != null) {
            sentElem.setAttribute("id", "" + id);
        }
        if (offset >= 0) {
            sentElem.setAttribute("offset", "" + offset);
        }

        for (int j = 0; j < size(); j++) {
            WordStruct wordStruct = get(j);
            Element wordElem = wordStruct.toXmlDetailed_ro(doc);
            sentElem.appendChild(wordElem);
        }

        return sentElem;
    }
}
