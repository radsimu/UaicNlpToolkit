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
import java.util.Map;

public class InsertNodeCommand extends AbstractUndoableEdit {
    public GraphNode gn;
    public Map<GraphNode, GraphNodeRenderingInfo> renderingInfos;

    private GraphNodeRenderingInfo renderInfo;

    public void undo() throws CannotUndoException {
        GrammarEditor.theEditor.selectGraph(gn.getParentGraph());
        gn.getParentGraph().getGraphNodes().remove(gn.getIndex());
        renderInfo = renderingInfos.get(gn);
        renderingInfos.remove(gn);
    }

    public void redo() throws CannotRedoException {
        GrammarEditor.theEditor.selectGraph(gn.getParentGraph());
        gn.getParentGraph().getGraphNodes().put(gn.getIndex(), gn);
        renderingInfos.put(gn, renderInfo);
    }

    public boolean canUndo() {
        return true;
    }

    public boolean canRedo() {
        return true;
    }

    public String getPresentationName() {
        return "Insert Node";
    }
}
