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

import java.util.Map;
import java.util.TreeMap;

public class SpanAnnotation {
    private INlpSentence sentence;

    private int startTokenIndex = -1; //the indexInSentence in the sentence of the first token
    private int endTokenIndex = -1; //the indexInSentence in the sentence of the last token
    protected Map<String, String> features = new TreeMap<String, String>();
    private String name;
    private boolean preferInlineRepresentation = false; //in case the corpus was loaded from an xml file, this value indicated if this span annotation was represented as an inline xml tag (as an ancestor of its tokens)

    public SpanAnnotation() {
    }

    public SpanAnnotation(SpanAnnotation a) {
        setSentence(a.getSentence());
        setStartTokenIndex(a.getStartTokenIndex());
        setEndTokenIndex(a.getEndTokenIndex());
        features = new TreeMap<>(a.getFeatures());
        setName(a.getName());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(getName());
        if (features != null)
            for (String key : features.keySet()) {
                sb.append(" ").append(key).append("=\"").append(features.get(key)).append("\"");
            }
        sb.append(">");

        for (int i = getStartTokenIndex(); i <= getEndTokenIndex(); i++) {
            sb.append(getSentence().getParentCorpus().getToken(i)).append(" ");
        }
        sb.setLength(sb.length() - 1);
        sb.append("</").append(getName()).append(">");
        return sb.toString();
    }

    public INlpSentence getSentence() {
        return sentence;
    }

    public Map<String, String> getFeatures() {
        return features;
    }

    public String getName() {
        return name;
    }

    public int getStartTokenIndex() {
        return startTokenIndex;
    }

    public int getEndTokenIndex() {
        return endTokenIndex;
    }

    public void setSentence(INlpSentence sentence) {
        this.sentence = sentence;
    }

    public void setStartTokenIndex(int startTokenIndex) {
        this.startTokenIndex = startTokenIndex;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEndTokenIndex(int endTokenIndex) {
        this.endTokenIndex = endTokenIndex;
    }

    public boolean isPreferInlineRepresentation() {
        return preferInlineRepresentation;
    }

    public void setPreferInlineRepresentation(boolean preferInlineRepresentation) {
        this.preferInlineRepresentation = preferInlineRepresentation;
    }
}
