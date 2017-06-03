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

import java.util.HashMap;
import java.util.List;

class TokenTrieNode {

    public HashMap<String, TokenTrieNode> nextToks = new HashMap<String, TokenTrieNode>();
    public String tok;
    public int depth = 0;
    public boolean isLeaf = false;

    public TokenTrieNode() {
    }

    public void addNext(List<String> toks) {

        TokenTrieNode next = nextToks.get(toks.get(depth + 1));
        if (next == null) {
            next = new TokenTrieNode();
            next.depth = depth + 1;
            next.tok = toks.get(next.depth);
            nextToks.put(next.tok, next);
        }

        if (next.depth + 1 == toks.size()) {
            next.isLeaf = true;
            return;
        }
        next.addNext(toks);
    }

    @Override
    public String toString() {
        if (nextToks.isEmpty()) {
            return "END";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : nextToks.keySet()) {
            sb.append(key).append(" ");
        }
        return sb.toString();
    }
}