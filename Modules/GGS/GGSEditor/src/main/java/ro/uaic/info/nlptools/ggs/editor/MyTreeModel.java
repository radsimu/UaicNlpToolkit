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

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

class MyTreeModel extends DefaultTreeModel {

    GrammarEditor grammarEditor;

    public MyTreeModel(TreeNode node, GrammarEditor grammarEditor) {
        super(node);
        this.grammarEditor = grammarEditor;
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        String name = ((String) newValue).trim();
        if (node.toString().equals(name))//s-a introdus acelasi text
            return;

        boolean ok = true;
        if (name == null || name.isEmpty()) {
            ok = false;
            JOptionPane.showMessageDialog(grammarEditor.frame, "The node name cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
        } else if (grammarEditor.grammar.getGraphs().containsKey(name)) {
            ok = false;
            JOptionPane.showMessageDialog(grammarEditor.frame, "A graph with this name exists already", "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (!ok) {
            grammarEditor.graphTree.startEditingAtPath(path);
        } else {
            RenameGraphCommand renameGraphCommand = new RenameGraphCommand();
            renameGraphCommand.graph = ((MyTreeNodeObject) node.getUserObject()).graph;
            renameGraphCommand.grammarEditor = grammarEditor;
            renameGraphCommand.prevName = renameGraphCommand.graph.getId();
            renameGraphCommand.nextName = name;
            grammarEditor.grammar.getGraphs().put(name, renameGraphCommand.graph);
            grammarEditor.grammar.getGraphs().remove(renameGraphCommand.graph.getId());
            renameGraphCommand.graph.setId(name);


            grammarEditor.undoSupport.postEdit(renameGraphCommand);
            grammarEditor.refreshTree();
        }

        nodeChanged(node);
    }
}