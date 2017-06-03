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

package ro.uaic.info.nlptools.postagger.evaluator;

public class SimpleEvalResult {

    public long correctOutputsCount;
    public long totalWordsCount;
    public long totalUnambiguousWords;
    public long correctGuessesCount;
    public long totalGuessesCount;
    public float sentsPerSecond;
    public float wordsPerSecond;

    @Override
    public String toString() {
        return ("\nsentences per second:" + sentsPerSecond
                +"\nwords per second:" + wordsPerSecond
                + "\n\nwords count:" + totalWordsCount
                + "\nunambiguous words:" + totalUnambiguousWords + " (" + (100 * (double) totalUnambiguousWords / totalWordsCount) + ")"
                + "\ncorrect outputs:" + correctOutputsCount
                + "\nunknown words count:" + totalGuessesCount + " (" + (100 * (double) totalGuessesCount/totalWordsCount) + ")"
                + "\ncorrect guesses:" + correctGuessesCount + " (" + (100 * (double) correctGuessesCount / totalGuessesCount) + ")"
                + "\nprecision: " + (100 * (double) correctOutputsCount / totalWordsCount));
    }
}
