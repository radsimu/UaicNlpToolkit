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

import java.util.HashMap;
import java.util.List;

public class EvalResult {

    public double precission;
    public HashMap<String, Integer> countExpectedTagsTotal;
    public HashMap<String, Integer> countOutputTagsTotal;
    public HashMap<String, Integer> countWordsTotal;
    public HashMap<String, List<FailCase>> worstExpectedTags_cases;
    public HashMap<String, List<FailCase>> worstOutputTags_cases;
    public HashMap<String, List<FailCase>> mostFrequentConfusions_cases;
    public HashMap<String, List<FailCase>> mostProblematicWords = new HashMap<String, List<FailCase>>();
    public List<FailCase> allFailCases;
    public long correctOutputsCount;
    public long totalWordsCount;
    public long totalUnambiguousWords;
}
