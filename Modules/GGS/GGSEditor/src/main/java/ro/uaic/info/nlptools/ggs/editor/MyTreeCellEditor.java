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
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

class MyTreeCellEditor extends DefaultTreeCellEditor {

    private final DefaultTreeCellRenderer renderer;

    public MyTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
        super(tree, renderer);
        this.renderer = renderer;
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
        MyTreeNodeObject myTreeNodeObject = (MyTreeNodeObject) ((DefaultMutableTreeNode) value).getUserObject();
        if (myTreeNodeObject.graph == null || myTreeNodeObject.graph.getId().equals("Main")) {
            Toolkit.getDefaultToolkit().beep();
            return renderer.getTreeCellRendererComponent(tree, value, isSelected, expanded, leaf, row, true); // hasFocus == true
        }
        return super.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row);
    }
}
