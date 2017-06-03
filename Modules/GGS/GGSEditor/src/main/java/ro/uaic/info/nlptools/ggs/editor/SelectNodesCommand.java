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
import javax.swing.undo.UndoableEditSupport;
import java.util.List;

public class SelectNodesCommand extends AbstractUndoableEdit {
    public GrammarEditorComponent editor;
    public List<GraphNode> prevSelection;
    public List<GraphNode> nextSelection;
    public List<GraphNode> curentSelection;

    public void undo() throws CannotUndoException {
        if (!prevSelection.isEmpty())
            GrammarEditor.theEditor.selectGraph(prevSelection.get(0).getParentGraph());
        curentSelection.clear();
        curentSelection.addAll(prevSelection);
    }

    public void redo() throws CannotRedoException {
        if (!nextSelection.isEmpty())
            GrammarEditor.theEditor.selectGraph(nextSelection.get(0).getParentGraph());
        curentSelection.clear();
        curentSelection.addAll(nextSelection);
    }

    public boolean canUndo() {
        return true;
    }

    public boolean canRedo() {
        return true;
    }

    public String getPresentationName() {
        return "Select";
    }

    public void register(UndoableEditSupport undoSupport) {
        if (prevSelection.equals(nextSelection))
            return;
        undoSupport.postEdit(this);
    }
}
