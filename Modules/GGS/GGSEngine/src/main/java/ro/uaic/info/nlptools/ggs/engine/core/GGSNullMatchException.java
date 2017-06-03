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

import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import java.util.ArrayList;
import java.util.List;

public class GGSNullMatchException extends GGSException{
    List<GraphNode> path;
    public GGSNullMatchException(List<State> path) {
        super(buildMessage(path));
        this.path= new ArrayList<GraphNode>();
        for (State s : path) {
            if ((s.isGraphNodeEntry || s.isGraphNodeExit)) continue;
            this.path.add(s.parentGraphNode);
        }

    }

    private static String buildMessage(List<State> loop) {
        StringBuilder sb = new StringBuilder("GGS null match exception thrown. The path below can match 0 tokens:\n");
        for (State s : loop) {
            if ((s.isGraphNodeEntry || s.isGraphNodeExit)) continue;
            sb.append(s.parentGraphNode.toString()).append(" --> ");
        }
        sb.delete(sb.length() - 5, sb.length());

        return sb.toString();
    }
}
