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

public class GraphNodeOutputSyntaxException extends GGSException {
    GraphNode node;

    public GraphNode getNode() {
        return node;
    }

    public GraphNodeOutputSyntaxException(GraphNode node) {
        super(String.format("GGS output code syntax exception thrown at %s : \n %s", node.toString(), node.getOutputCode()));
        this.node = node;
    }
}
