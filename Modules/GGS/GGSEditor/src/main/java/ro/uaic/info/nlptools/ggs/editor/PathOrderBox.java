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

package ro.uaic.info.nlptools.ggs.editor;

import ro.uaic.info.nlptools.ggs.engine.grammar.GraphNode;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

class PathOrderBox {
    public int index;
    public int displayIndex;
    public GraphNode parentGraphNode;
    public GraphNode childGraphNode;
    public RoundRectangle2D rect;

    public PathOrderBox(GraphNode parent, GraphNode child, int index, Point2D middle, Graphics2D g2d, int displayIndex) {
        parentGraphNode = parent;
        childGraphNode = child;
        this.index = index;
        this.displayIndex = displayIndex;
        Rectangle2D bounds = g2d.getFont().getStringBounds(Integer.toString(index), g2d.getFontRenderContext());
        rect = new RoundRectangle2D.Double(middle.getX() - bounds.getWidth() / 2 - 5, middle.getY() - bounds.getHeight() / 2, bounds.getWidth() + 10, bounds.getHeight(), bounds.getHeight() - 1, bounds.getHeight() - 1);
    }

    public PathOrderBox(PathOrderBox original) {
        index = original.index;
        displayIndex = original.displayIndex;
        parentGraphNode = original.parentGraphNode;
        childGraphNode = original.childGraphNode;
        rect = (RoundRectangle2D) original.rect.clone();
    }
}
