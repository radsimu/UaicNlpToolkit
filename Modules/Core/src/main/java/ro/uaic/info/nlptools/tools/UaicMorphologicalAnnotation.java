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

package ro.uaic.info.nlptools.tools;

public class UaicMorphologicalAnnotation {

    private String word;
    private String lemma;
    private String msd;
    private String extra;

    protected void setWord(String word){
        assert word!= null;
        assert !word.trim().isEmpty();
        this.word = UaicMorphologicalDictionary.getCanonicalWord(word);
    }
    
    public void setLemma(String lemma) {
        assert lemma!=null;
        assert ! lemma.trim().isEmpty();
        this.lemma = lemma;
    }

    public void setMsd(String msd) {
        assert msd != null;
        assert !msd.trim().isEmpty();
        this.msd = msd;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public UaicMorphologicalAnnotation() {
    }

    public UaicMorphologicalAnnotation(String word, String MSD, String LEMMA, String extra) {
        this.word = word;
        this.msd = MSD;
        this.lemma = LEMMA;
        this.extra = extra;
    }

    public String getWord() {
        return word;
    }

    public String getLemma() {
        if (lemma != null) {
            return lemma;
        }
        return word;
    }

    public String getMsd() {
        return msd;
    }

    public String getExtra() {
        return extra;
    }

    @Override
    public String toString() {
        return word + " " + getMsd() + " " + getLemma() + ((extra != null) ? " " + getExtra() : "");
    }

    @Override
    public int hashCode() {
        return (word + " " + getMsd() + " " + getLemma()).hashCode();
    }

    @Override
    public boolean equals(Object a) {
        return hashCode() == a.hashCode();
    }
}
