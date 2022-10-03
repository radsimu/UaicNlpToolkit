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

package ro.uaic.info.nlptools.ggs.engine.core;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;

import java.io.IOException;
import java.util.BitSet;

public class CorpusPositionsAnnotationsCollector extends SimpleCollector {
    IndexSearcher searcher;
    public Pair<BitSet, BitSet> result;

    public CorpusPositionsAnnotationsCollector(IndexSearcher searcher, int resultSize) {
        this.searcher = searcher;
        result = new Pair<BitSet, BitSet> (new BitSet(), new BitSet());
    }

    @Override
    public void collect(int i) throws IOException {
        result.getKey().set(Integer.parseInt(searcher.doc(i).get("GGS:StartTokenIndex")));
        result.getValue().set(Integer.parseInt(searcher.doc(i).get("GGS:EndTokenIndex")));
    }

    @Override
    public ScoreMode scoreMode() {
        return null;
    }
}

