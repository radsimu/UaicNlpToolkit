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

import ro.uaic.info.nlptools.ggs.engine.SparseBitSet;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SimpleCollector;

import java.io.IOException;
import java.util.BitSet;

public class CorpusPositionsCollector extends SimpleCollector {
    IndexSearcher searcher;
    BitSet result;
    SparseBitSet rez;
    int docBase = 0;

    public SparseBitSet result(int size) {
        if (rez != null)
            return  rez;

        rez = new SparseBitSet(result);
        return rez;
    }

    public CorpusPositionsCollector(IndexSearcher searcher) {
        this.searcher = searcher;
        result = new BitSet();
    }

    @Override
    public boolean needsScores() {
        return false;
    }

    @Override
    public void collect(int i) throws IOException {
        result.set(i + docBase);
    }

    @Override
    protected void doSetNextReader(LeafReaderContext context) throws IOException {
        docBase = context.docBase;
    }
}

