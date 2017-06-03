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

import ro.uaic.info.nlptools.corpus.SpanAnnotation;
import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

public class GGSOutputMalformatException extends GGSException {
    SpanAnnotation spanAnnotation;

    public GGSOutputMalformatException(SpanAnnotation a) {
        super(buildMessage(a));
        spanAnnotation = a;
    }

    public GGSOutputMalformatException(GraphNode s) {
        super(String.format("GGS output malformat exception. \nCan't close spanAnnotation at node %s\n All spanAnnotations are already closed", s));
    }

    private static String buildMessage(SpanAnnotation a) {
        if (a.getEndTokenIndex() == -1)
            return String.format("GGS output malformat exception. \nSpanAnnotation %s is never closed", a.getName());
        else if (a.getStartTokenIndex() == -1)
            return String.format("GGS output malformat exception. \nAn spanAnnotation closing bracket has no starting bracket");
        else
            return String.format("GGS output malformat exception. \nSpanAnnotation %s does not enclose any tokens\nEmpty spanAnnotations are not allowed in output", a.getName()); //TODO: spanAnnotations with no tokens inside
    }
}
