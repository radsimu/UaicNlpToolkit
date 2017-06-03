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

import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Token {
    int charStartIndexInSentence;

    public int getCharStartIndexInSentence() {
        return charStartIndexInSentence;
    }

    public void setCharStartIndexInSentence(int charStartIndexInSentence) {
        this.charStartIndexInSentence = charStartIndexInSentence;
    }

    public int getCharEndIndexInSentence() {
        return charEndIndexInSentence;
    }

    public void setCharEndIndexInSentence(int charEndIndexInSentence) {
        this.charEndIndexInSentence = charEndIndexInSentence;
    }

    int charEndIndexInSentence;
    int indexInSentence;

    public void setParentSentence(INlpSentence parentSentence) {
        this.parentSentence = parentSentence;
    }

    INlpSentence parentSentence;
    protected List<SpanAnnotation> parentAnnotations = new ArrayList<SpanAnnotation>();
    public int getTokenIndexInCorpus() {
        return parentSentence.getFirstTokenIndexInCorpus() + indexInSentence;
    }

    private Map<String, String> features;

    public Token() {
        features = new TreeMap<>();
    }

    public Token(Node node) {
        setFeatures(new TreeMap<String, String>());

        if (node.getFirstChild() != null)
            getFeatures().put("WORD", node.getFirstChild().getNodeValue());

        for (int i = 0; i < node.getAttributes().getLength(); i++) {
            getFeatures().put(node.getAttributes().item(i).getNodeName(), node.getAttributes().item(i).getNodeValue());
        }
    }

    @Override
    public String toString() {
        String ret = getFeatures().get(FeatureLabel_Form) + " " + FeatureLabel_POS + "=" + getPostag();
        return ret;
    }

    public int getTokenIndexInSentence() {
        return indexInSentence;
    }

    public INlpSentence getParentSentence() {
        return parentSentence;
    }

    public Map<String, String> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, String> features) {
        this.features = features;
    }


    public List<SpanAnnotation> getParentSpanAnnotations() {
        return parentAnnotations;
    }

    public INlpCorpus getParentCorpus() {
        return parentSentence.getParentCorpus();
    }

    public String FeatureLabel_Form = "WORD";
    public String FeatureLabel_Lemma = "lemma";
    public String FeatureLabel_POS = "postag";

    public String getWordForm(){return features.get(FeatureLabel_Form);}
    public String getLemma(){return features.get(FeatureLabel_Lemma);}
    public String getPostag(){return features.get(FeatureLabel_POS);}

    public void setWordForm(String value){features.put(FeatureLabel_Form, value);}
    public void setLemma(String value){features.put(FeatureLabel_Lemma, value);}
    public void setPostag(String value){features.put(FeatureLabel_POS, value);}
}