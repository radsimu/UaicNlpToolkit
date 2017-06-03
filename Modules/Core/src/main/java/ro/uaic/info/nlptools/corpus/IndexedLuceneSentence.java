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

import org.apache.lucene.index.IndexableField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IndexedLuceneSentence implements INlpSentence {
    int sentenceIndex;
    int startTokenIndex;
    private int tokenCount;
    private List<SpanAnnotation> spanAnnotations;
    private INlpCorpus parentCorpus;


    public IndexedLuceneSentence(int sentenceIndex, IndexedLuceneCorpus parentCorpus) throws IOException {
        this.sentenceIndex = sentenceIndex;
        this.parentCorpus = parentCorpus;
        startTokenIndex = parentCorpus.sentenceSearcher.doc(sentenceIndex).getField("GGS:StartTokenIndex").numericValue().intValue();
        tokenCount = parentCorpus.sentenceSearcher.doc(sentenceIndex).getField("GGS:EndTokenIndex").numericValue().intValue() - startTokenIndex;
    }

    @Override
    public boolean hasInputAnnotations() {
        try {
            return ((IndexedLuceneCorpus) getParentCorpus()).sentenceSearcher.doc(sentenceIndex).getField("GGS:Annotations") != null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public INlpCorpus getParentCorpus() {
        return parentCorpus;
    }

    @Override
    public int getSentenceIndexInCorpus() {
        return sentenceIndex;
    }

    @Override
    public int getTokenCount() {
        return tokenCount;
    }

    @Override
    public Token getToken(int indexInSentence) {
        return getParentCorpus().getToken(indexInSentence + getFirstTokenIndexInCorpus());
    }

    @Override
    public int getFirstTokenIndexInCorpus() {
        return startTokenIndex;
    }

    public List<SpanAnnotation> getSpanAnnotations() {
        if (spanAnnotations == null) {
            spanAnnotations = new ArrayList<>();
            try {
                for (IndexableField field : ((IndexedLuceneCorpus) getParentCorpus()).sentenceSearcher.doc(sentenceIndex).getFields("GGS:SpanAnnotation")) {
                    spanAnnotations.add(((IndexedLuceneCorpus) getParentCorpus()).getAnnotation(field.numericValue().intValue()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return spanAnnotations;
    }
}
