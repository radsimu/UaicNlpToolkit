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

package ro.uaic.info.nlptools.ggs.inferrer;

import ro.uaic.info.nlptools.corpus.Token;

import java.util.*;

public class EvalResult {
    public Map<List<Token>,List<Token>> incorrectMatchesToExampleSequences = new HashMap<List<Token>, List<Token>>();//incorrect matches mapped to their corresponding correct version if available
    public Set<List<Token>> notRecalledExamples = new HashSet<List<Token>>(); //examples sequences which were not recalled at all - this should never happen, but if it does, do investigate

    public int nonIntersectingErrosCount;
    public int totalTokensCountInExamples;
    public Set<Token> correctlyRecalledTokens = new HashSet<Token>();
    public List<Token> incorrectlyRecalledTokens = new ArrayList<Token>();
    public List<Token> notRecalledTokens = new ArrayList<Token>();
}
