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

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.awt.*;

public class MoveNodeCommand extends AbstractUndoableEdit {
    GraphNode gn;
    public Point initPos;
    public Point newPos;
    GraphNodeRenderingInfo renderInfo;

    public MoveNodeCommand(GraphNode gn, GraphNodeRenderingInfo renderInfo) {
        this.renderInfo = renderInfo;
        this.gn = gn;
    }

    public void undo() throws CannotUndoException {
        GrammarEditor.theEditor.selectGraph(gn.getParentGraph());
        gn.setX(initPos.x);
        gn.setY(initPos.y);
        renderInfo.formatDirty = true;
    }

    public void redo() throws CannotRedoException {
        GrammarEditor.theEditor.selectGraph(gn.getParentGraph());
        gn.setX(newPos.x);
        gn.setY(newPos.y);
        renderInfo.formatDirty = true;
    }

    public boolean canUndo() {
        return true;
    }

    public boolean canRedo() {
        return true;
    }

    public String getPresentationName() {
        return "Move Node";
    }
}
