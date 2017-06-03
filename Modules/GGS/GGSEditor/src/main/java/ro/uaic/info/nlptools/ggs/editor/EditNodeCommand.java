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

public class EditNodeCommand extends AbstractUndoableEdit {
    GraphNode gn;

    String pattern_prev;
    String output_prev = null;
    String jsCode_prev = null;
    boolean findLongestMatch_prev;

    String pattern_next;
    String output_next = null;
    String jsCode_next = null;
    boolean findLongestMatch_next;


    public EditNodeCommand(GraphNode gn) {
        this.gn = gn;
    }

    public void undo() throws CannotUndoException {
        GrammarEditor.theEditor.selectGraph(gn.getParentGraph());
        gn.setOutputCode(null);
        if (output_prev != null) gn.setOutputCode(output_prev);
        gn.setTokenMatchingCode(pattern_prev);
        gn.setFindLongestMatch(findLongestMatch_prev);
        gn.setJsCode(jsCode_prev);
        GrammarEditor.theEditor.graphEditor.renderingInfos.get(gn).formatDirty = true;
    }

    public void redo() throws CannotRedoException {
        GrammarEditor.theEditor.selectGraph(gn.getParentGraph());
        gn.setOutputCode(null);
        if (output_next != null) gn.setOutputCode(output_next);
        gn.setTokenMatchingCode(pattern_next);
        gn.setFindLongestMatch(findLongestMatch_next);
        gn.setJsCode(jsCode_next);
        GrammarEditor.theEditor.graphEditor.renderingInfos.get(gn).formatDirty = true;
    }

    public void setPrev() {
        output_prev = null;
        if (gn.getOutputCode() != null)
            output_prev = gn.getOutputCode();
        pattern_prev = gn.getTokenMatchingCode();
        jsCode_prev = gn.getJsCode();
        findLongestMatch_prev = gn.isFindLongestMatch();
    }

    public void setNext() {
        output_next = null;
        if (gn.getOutputCode() != null)
            output_next = gn.getOutputCode();
        pattern_next = gn.getTokenMatchingCode();
        jsCode_next = gn.getJsCode();
        findLongestMatch_next = gn.isFindLongestMatch();
    }

    public boolean isRelevant() {
        return !pattern_prev.equals(pattern_next)
                || findLongestMatch_next != findLongestMatch_prev
                || (output_next != null && !output_next.equals(output_prev))
                || (output_next == null && output_prev != null)
                || (jsCode_next != null && !jsCode_next.equals(jsCode_prev))
                || (jsCode_next == null && jsCode_prev != null);
    }

    public boolean canUndo() {
        return true;
    }

    public boolean canRedo() {
        return true;
    }

    public String getPresentationName() {
        return "Edit Node";
    }
}